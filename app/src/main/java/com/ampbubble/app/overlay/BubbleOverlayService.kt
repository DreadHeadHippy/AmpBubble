package com.ampbubble.app.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.media.session.PlaybackState
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.ampbubble.app.MainActivity
import com.ampbubble.app.R
import com.ampbubble.app.data.PendingRatingRecord
import com.ampbubble.app.data.RatedTrackRecord
import com.ampbubble.app.data.SecureTokenStore
import com.ampbubble.app.data.SettingsStore
import com.ampbubble.app.notification.PlexampNotificationListener
import com.ampbubble.app.plex.NowPlayingMetadata
import com.ampbubble.app.plex.NowPlayingRepository
import com.ampbubble.app.plex.PlexRatingRepository
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Foreground service that hosts the always-on-top bubble and its expandable rating panel. */
class BubbleOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams
    private var panelLayoutParams: WindowManager.LayoutParams? = null

    private lateinit var bubbleView: ComposeView
    private var panelView: ComposeView? = null
    private val overlayLifecycleOwner = ComposeOverlayLifecycleOwner()

    private lateinit var settingsStore: SettingsStore
    private lateinit var tokenStore: SecureTokenStore
    private val nowPlayingRepository = NowPlayingRepository()
    private val ratingRepository = PlexRatingRepository()

    private var bubbleAlphaState by mutableStateOf(1f)
    private var panelVisibleState by mutableStateOf(false)
    private var trackTitleState by mutableStateOf<String?>(null)
    private var trackArtistState by mutableStateOf<String?>(null)
    private var trackAlbumState by mutableStateOf<String?>(null)
    private var trackYearState by mutableStateOf<Int?>(null)
    private var isPlayingState by mutableStateOf(false)
    private var playbackPositionMsState by mutableStateOf<Long?>(null)
    private var playbackPositionUpdatedAtMsState by mutableStateOf<Long?>(null)
    private var trackDurationMsState by mutableStateOf<Long?>(null)
    private var ratingState by mutableStateOf(0f)
    private var statusMessageState by mutableStateOf<String?>(null)
    private var panelAccentColorState by mutableStateOf(Color(0xFFE5A00D))
    private var currentTrackArtUrlState by mutableStateOf<String?>(null)
    private var currentTrackArtBitmapState by mutableStateOf<ImageBitmap?>(null)
    private var recentRatingsState by mutableStateOf<List<RecentRatingItemUi>>(emptyList())
    private var showRecentRatingsState by mutableStateOf(false)
    private var ratingPresetsState by mutableStateOf<List<Float>>(emptyList())

    private var resolvedRatingKey: String? = null
    private val cachedRatingByKey = mutableMapOf<String, Float>()
    private var ratingCacheLoaded = false
    private var currentTrackFingerprint: String? = null
    private var currentTrackThumbPath: String? = null
    private var pendingRatingSubmitJob: Job? = null
    private var trackResolutionJob: Job? = null
    private var trackResolutionVersion = 0L
    private var baseUrl: String = ""
    private var authToken: String? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsStore = SettingsStore(applicationContext)
        tokenStore = SecureTokenStore(applicationContext)
        overlayLifecycleOwner.onCreate()
        overlayLifecycleOwner.onStart()
        overlayLifecycleOwner.onResume()

        if (!Settings.canDrawOverlays(this)) {
            lifecycleScope.launch { settingsStore.addDiagnosticEvent("overlay:permission_missing") }
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        addBubbleWindow()
        observeSettings()
        observeNowPlaying()
        lifecycleScope.launch {
            hydratePersistentState()
            processPendingRatingsQueue()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        removePanelWindow()
        if (::bubbleView.isInitialized) runCatchingSilently { windowManager.removeView(bubbleView) }
        overlayLifecycleOwner.onDestroy()
        super.onDestroy()
    }

    // --- Window setup -----------------------------------------------------

    private fun overlayWindowType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun addBubbleWindow() {
        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                BubbleContent(
                    bubbleAlpha = bubbleAlphaState,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { togglePanel() })
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { snapBubbleToNearestEdge() }
                            ) { change, dragAmount ->
                                change.consume()
                                bubbleLayoutParams.x += dragAmount.x.roundToInt()
                                bubbleLayoutParams.y += dragAmount.y.roundToInt()
                                windowManager.updateViewLayout(bubbleView, bubbleLayoutParams)
                            }
                        }
                )
            }
        }

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        lifecycleScope.launch {
            val (savedX, savedY) = settingsStoreBubblePositionOrDefault()
            bubbleLayoutParams.x = savedX
            bubbleLayoutParams.y = savedY
            runCatching { windowManager.addView(bubbleView, bubbleLayoutParams) }
                .onFailure {
                    lifecycleScope.launch { settingsStore.addDiagnosticEvent("overlay:add_failed") }
                    stopSelf()
                }
        }
    }

    private suspend fun settingsStoreBubblePositionOrDefault(): Pair<Int, Int> =
        runCatching { settingsStore.bubblePosition.first() }.getOrDefault(0 to 300)

    private fun snapBubbleToNearestEdge() {
        val metrics = windowManager.currentWindowMetrics
        val screenWidth = metrics.bounds.width()
        val bubbleWidth = bubbleView.width.takeIf { it > 0 } ?: 150
        val targetX = if (bubbleLayoutParams.x + bubbleWidth / 2 < screenWidth / 2) 0 else screenWidth - bubbleWidth

        ValueAnimator.ofInt(bubbleLayoutParams.x, targetX).apply {
            duration = 200
            addUpdateListener { animator ->
                bubbleLayoutParams.x = animator.animatedValue as Int
                runCatchingSilently { windowManager.updateViewLayout(bubbleView, bubbleLayoutParams) }
            }
            start()
        }
        lifecycleScope.launch {
            settingsStore.setBubblePosition(targetX, bubbleLayoutParams.y)
        }
    }

    private fun togglePanel() {
        if (panelVisibleState) {
            removePanelWindow()
        } else {
            addPanelWindow()
        }
        panelVisibleState = !panelVisibleState
    }

    private fun addPanelWindow() {
        if (panelView != null) return
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                RatingPanelContent(
                    panelAlpha = 1f,
                    accentColor = panelAccentColorState,
                    trackTitle = trackTitleState,
                    trackArtist = trackArtistState,
                    trackAlbum = trackAlbumState,
                    trackYear = trackYearState,
                    trackArtUrl = currentTrackArtUrlState,
                    trackArtBitmap = currentTrackArtBitmapState,
                    isPlaying = isPlayingState,
                    playbackPositionMs = playbackPositionMsState,
                    playbackPositionUpdatedAtMs = playbackPositionUpdatedAtMsState,
                    durationMs = trackDurationMsState,
                    rating = ratingState,
                    onRatingPreview = ::previewRating,
                    onRatingCommit = ::submitRating,
                    onPlayPause = ::togglePlayPause,
                    onNext = ::skipToNext,
                    onPrevious = ::skipToPrevious,
                    onSeekForward = { seekBy(10_000L) },
                    onSeekBack = { seekBy(-10_000L) },
                    ratingPresets = ratingPresetsState,
                    onPresetRating = ::submitRating,
                    showRecentRatings = showRecentRatingsState,
                    recentRatings = recentRatingsState,
                    statusMessage = statusMessageState
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleLayoutParams.x
            y = bubbleLayoutParams.y + 140
            alpha = 0f
        }
        windowManager.addView(view, params)
        view.post {
            params.alpha = 1f
            positionPanelWithinScreen(view, params)
        }
        panelView = view
        panelLayoutParams = params
        refreshNowPlayingOnDemand()
    }

    private fun positionPanelWithinScreen(view: ComposeView, params: WindowManager.LayoutParams) {
        val metrics = windowManager.currentWindowMetrics
        val screenWidth = metrics.bounds.width()
        val screenHeight = metrics.bounds.height()
        val panelWidth = view.width.takeIf { it > 0 } ?: 300
        val panelHeight = view.height.takeIf { it > 0 } ?: 320
        val bubbleWidth = bubbleView.width.takeIf { it > 0 } ?: 150
        val bubbleHeight = bubbleView.height.takeIf { it > 0 } ?: 150

        // When on the right half, open inward to keep the panel usable.
        val preferredX = if (bubbleLayoutParams.x + bubbleWidth / 2 > screenWidth / 2) {
            bubbleLayoutParams.x - panelWidth + bubbleWidth
        } else {
            bubbleLayoutParams.x
        }

        val belowY = bubbleLayoutParams.y + bubbleHeight - 10
        val aboveY = bubbleLayoutParams.y - panelHeight - 10
        val preferredY = when {
            belowY + panelHeight <= screenHeight -> belowY
            aboveY >= 0 -> aboveY
            else -> belowY
        }

        val maxX = (screenWidth - panelWidth).coerceAtLeast(0)
        val maxY = (screenHeight - panelHeight).coerceAtLeast(0)

        params.x = preferredX.coerceIn(0, maxX)
        params.y = preferredY.coerceIn(0, maxY)
        if (view.isAttachedToWindow) {
            runCatchingSilently { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun removePanelWindow() {
        panelView?.let { runCatchingSilently { windowManager.removeView(it) } }
        panelView = null
        panelLayoutParams = null
    }

    // --- Settings -----------------------------------------------------------

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsStore.ratingPresets.distinctUntilChanged().collectLatest { presets ->
                ratingPresetsState = presets
            }
        }
        lifecycleScope.launch {
            settingsStore.showRecentInBubble.distinctUntilChanged().collectLatest { enabled ->
                showRecentRatingsState = enabled
            }
        }
    }

    // --- Now playing / rating ------------------------------------------------

    private fun observeNowPlaying() {
        lifecycleScope.launch {
            PlexampNotificationListener.nowPlaying.collectLatest { metadata ->
                if (metadata == null) {
                    clearNowPlayingState()
                    return@collectLatest
                }

                val state = metadata.playbackState
                val stopped = state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE
                if (stopped) {
                    clearNowPlayingState()
                    return@collectLatest
                }

                trackTitleState = metadata.title ?: "Nothing playing"
                trackArtistState = metadata.artist
                trackAlbumState = metadata.album
                trackYearState = metadata.year
                isPlayingState = metadata.isPlaying
                playbackPositionMsState = metadata.playbackPositionMs
                playbackPositionUpdatedAtMsState = metadata.playbackPositionUpdatedAtMs
                trackDurationMsState = metadata.durationMs
                panelAccentColorState = metadata.accentColorArgb?.let { Color(it) } ?: Color(0xFFE5A00D)
                currentTrackArtBitmapState = metadata.albumArtBitmap?.asImageBitmap()
                val fingerprint = buildTrackFingerprint(metadata.title, metadata.artist, metadata.durationMs)
                if (fingerprint != currentTrackFingerprint) {
                    currentTrackFingerprint = fingerprint
                    resolvedRatingKey = null
                    ratingState = 0f
                    currentTrackThumbPath = null
                    currentTrackArtUrlState = null
                    resolveRatingKeyAndPrefillRating(metadata.title, metadata.artist, metadata.album, metadata.year, metadata.durationMs)
                }
            }
        }
    }

    private fun refreshNowPlayingOnDemand() {
        val metadata = PlexampNotificationListener.nowPlaying.value
        val state = metadata?.playbackState
        val stopped = metadata == null || state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE
        if (!stopped) {
            val fingerprint = buildTrackFingerprint(metadata.title, metadata.artist, metadata.durationMs)
            if (resolvedRatingKey == null && currentTrackFingerprint == fingerprint) {
                resolveRatingKeyAndPrefillRating(metadata.title, metadata.artist, metadata.album, metadata.year, metadata.durationMs)
            }
        } else {
            clearNowPlayingState()
            statusMessageState = "Waiting for Plexamp playback..."
        }
    }

    private fun resolveRatingKeyAndPrefillRating(title: String?, artist: String?, album: String?, year: Int?, durationMs: Long?) {
        val fingerprint = buildTrackFingerprint(title, artist, durationMs)
        val resolutionVersion = ++trackResolutionVersion
        trackResolutionJob?.cancel()
        trackResolutionJob = lifecycleScope.launch {
            ensureRatingCacheLoaded()
            val resolvedBase = currentBaseUrl()
            val token = currentAuthToken()
            if (resolvedBase.isNullOrBlank() || token.isNullOrBlank()) {
                if (isCurrentResolution(fingerprint, resolutionVersion)) {
                    statusMessageState = "Sign in to Plex from the app first"
                    settingsStore.addDiagnosticEvent("resolve:missing_auth_or_base")
                }
                return@launch
            }
            val metadata = NowPlayingMetadata(title, artist, album, year, durationMs, true)
            delay(TRACK_CHANGE_SETTLE_DELAY_MS)
            repeat(MAX_SESSION_MATCH_ATTEMPTS) { attempt ->
                val sessions = nowPlayingRepository.fetchActiveSessions(resolvedBase, token).getOrNull().orEmpty()
                val match = nowPlayingRepository.matchSession(metadata, sessions)
                if (match != null) {
                    var resolvedAlbum = album ?: match.parentTitle
                    var resolvedYear = year ?: match.year
                    if (resolvedAlbum.isNullOrBlank() || resolvedYear == null) {
                        val context = nowPlayingRepository.fetchTrackContext(resolvedBase, token, match.ratingKey).getOrNull()
                        if (resolvedAlbum.isNullOrBlank()) resolvedAlbum = context?.album
                        if (resolvedYear == null) resolvedYear = context?.year
                    }
                    if (!isCurrentResolution(fingerprint, resolutionVersion)) return@launch

                    resolvedRatingKey = match.ratingKey
                    trackAlbumState = resolvedAlbum
                    trackYearState = resolvedYear
                    currentTrackThumbPath = match.thumbPath
                    currentTrackArtUrlState = buildMediaUrl(resolvedBase, token, match.thumbPath)
                    ratingState = cachedRatingByKey[match.ratingKey] ?: (match.userRating ?: 0f) / 2f
                    statusMessageState = null
                    settingsStore.addDiagnosticEvent("resolve:matched")
                    return@launch
                }

                if (attempt < MAX_SESSION_MATCH_ATTEMPTS - 1) delay(TRACK_MATCH_RETRY_DELAY_MS)
            }

            if (isCurrentResolution(fingerprint, resolutionVersion)) {
                statusMessageState = "Couldn't match this track in your Plex library yet"
                settingsStore.addDiagnosticEvent("resolve:no_match")
            }
        }
    }

    private fun isCurrentResolution(fingerprint: String, resolutionVersion: Long): Boolean =
        currentTrackFingerprint == fingerprint && trackResolutionVersion == resolutionVersion

    private fun submitRating(newStarRating: Float) {
        ratingState = newStarRating
        pendingRatingSubmitJob?.cancel()
        pendingRatingSubmitJob = lifecycleScope.launch {
            delay(300)
            val nowMetadata = PlexampNotificationListener.nowPlaying.value
            val nowFingerprint = buildTrackFingerprint(nowMetadata?.title, nowMetadata?.artist, nowMetadata?.durationMs)
            if (currentTrackFingerprint != null && nowFingerprint != currentTrackFingerprint) {
                statusMessageState = "Track changed - wait a moment, then retry"
                settingsStore.addDiagnosticEvent("rate:blocked_track_changed")
                return@launch
            }

            val ratingKey = resolvedRatingKey ?: run {
                statusMessageState = "No matched track to rate"
                settingsStore.addDiagnosticEvent("rate:no_rating_key")
                return@launch
            }

            ensureRatingCacheLoaded()
            val resolvedBase = currentBaseUrl() ?: return@launch
            val token = currentAuthToken() ?: return@launch
            val result = ratingRepository.rateTrack(resolvedBase, token, ratingKey, newStarRating * 2f)
            if (result.isSuccess) {
                cachedRatingByKey[ratingKey] = newStarRating
                settingsStore.setCachedRating(ratingKey, newStarRating)
                settingsStore.addRecentRatedTrack(
                    RatedTrackRecord(
                        ratingKey = ratingKey,
                        title = trackTitleState ?: "Unknown track",
                        artist = trackArtistState,
                        thumbPath = currentTrackThumbPath,
                        stars0to5 = newStarRating,
                        timestampMs = System.currentTimeMillis()
                    )
                )
                refreshRecentRatingsState()
                statusMessageState = "Rating saved"
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(3000)
                    if (statusMessageState == "Rating saved") {
                        statusMessageState = null
                    }
                }
                settingsStore.addDiagnosticEvent("rate:saved")
            } else {
                queuePendingRating(ratingKey, newStarRating)
                statusMessageState = "Failed to save rating, will retry on next tap"
                settingsStore.addDiagnosticEvent("rate:queued_for_retry")
            }
        }
    }

    private fun previewRating(newStarRating: Float) {
        ratingState = newStarRating
        pendingRatingSubmitJob?.cancel()
    }

    private suspend fun ensureRatingCacheLoaded() {
        if (ratingCacheLoaded) return
        cachedRatingByKey.clear()
        cachedRatingByKey.putAll(settingsStore.getCachedRatings())
        ratingCacheLoaded = true
    }

    private suspend fun hydratePersistentState() {
        ensureRatingCacheLoaded()
        showRecentRatingsState = settingsStore.showRecentInBubble.first()
        refreshRecentRatingsState()
        settingsStore.addDiagnosticEvent("service:hydrated_state")
    }

    private suspend fun refreshRecentRatingsState() {
        val resolvedBase = currentBaseUrl()?.trimEnd('/')
        val token = currentAuthToken()
        recentRatingsState = settingsStore.getRecentRatedTracks()
            .sortedByDescending { it.timestampMs }
            .take(3)
            .map {
                RecentRatingItemUi(
                    title = it.title,
                    artist = it.artist,
                    stars0to5 = it.stars0to5,
                    artUrl = buildMediaUrl(resolvedBase, token, it.thumbPath)
                )
            }
    }

    private fun buildMediaUrl(baseUrl: String?, token: String?, mediaPath: String?): String? {
        if (baseUrl.isNullOrBlank() || token.isNullOrBlank() || mediaPath.isNullOrBlank()) return null
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        val separator = if (mediaPath.contains("?")) "&" else "?"
        return "$baseUrl$mediaPath${separator}X-Plex-Token=$encodedToken"
    }

    private suspend fun queuePendingRating(ratingKey: String, stars0to5: Float) {
        settingsStore.addPendingRating(
            PendingRatingRecord(
                ratingKey = ratingKey,
                stars0to5 = stars0to5,
                title = trackTitleState,
                artist = trackArtistState,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun processPendingRatingsQueue() {
        val resolvedBase = currentBaseUrl() ?: return
        val token = currentAuthToken() ?: return
        val pending = settingsStore.getPendingRatings()
        if (pending.isEmpty()) return

        val remaining = mutableListOf<PendingRatingRecord>()
        var synced = 0
        for (item in pending) {
            val result = ratingRepository.rateTrack(resolvedBase, token, item.ratingKey, item.stars0to5 * 2f)
            if (result.isSuccess) {
                cachedRatingByKey[item.ratingKey] = item.stars0to5
                settingsStore.setCachedRating(item.ratingKey, item.stars0to5)
                settingsStore.addRecentRatedTrack(
                    RatedTrackRecord(
                        ratingKey = item.ratingKey,
                        title = item.title ?: "Unknown track",
                        artist = item.artist,
                        thumbPath = null,
                        stars0to5 = item.stars0to5,
                        timestampMs = item.timestampMs
                    )
                )
                synced++
            } else {
                remaining.add(item)
            }
        }
        settingsStore.setPendingRatings(remaining)
        refreshRecentRatingsState()
        if (synced > 0) {
            statusMessageState = "Synced $synced queued rating(s)"
            settingsStore.addDiagnosticEvent("queue:synced=$synced")
        }
    }

    private fun buildTrackFingerprint(title: String?, artist: String?, durationMs: Long?): String {
        return "${title.orEmpty().trim().lowercase()}|${artist.orEmpty().trim().lowercase()}|${durationMs ?: -1L}"
    }

    private fun togglePlayPause() {
        if (!PlexampNotificationListener.togglePlayPause()) {
            statusMessageState = "Playback control unavailable"
        }
    }

    private fun skipToNext() {
        if (!PlexampNotificationListener.skipNext()) {
            statusMessageState = "Playback control unavailable"
        }
    }

    private fun skipToPrevious() {
        if (!PlexampNotificationListener.skipPrevious()) {
            statusMessageState = "Playback control unavailable"
        }
    }

    private fun seekBy(deltaMs: Long) {
        if (!PlexampNotificationListener.seekBy(deltaMs)) {
            statusMessageState = "Playback control unavailable"
        }
    }

    private fun clearNowPlayingState() {
        trackTitleState = "Nothing playing"
        trackArtistState = null
        trackAlbumState = null
        trackYearState = null
        isPlayingState = false
        playbackPositionMsState = null
        playbackPositionUpdatedAtMsState = null
        trackDurationMsState = null
        ratingState = 0f
        resolvedRatingKey = null
        currentTrackFingerprint = null
        currentTrackThumbPath = null
        currentTrackArtUrlState = null
        currentTrackArtBitmapState = null
        panelAccentColorState = Color(0xFFE5A00D)
        trackResolutionJob?.cancel()
        trackResolutionJob = null
        trackResolutionVersion++
    }

    private suspend fun currentBaseUrl(): String? {
        if (baseUrl.isNotBlank()) return baseUrl
        val useManual = settingsStore.useManualServer.first()
        baseUrl = if (useManual) {
            settingsStore.manualBaseUrl.first()
        } else {
            settingsStore.resolvedBaseUrl.first()
        }
        return baseUrl.ifBlank { null }
    }

    private fun currentAuthToken(): String? {
        if (authToken.isNullOrBlank()) authToken = tokenStore.authToken
        return authToken
    }

    // --- Foreground notification ---------------------------------------------

    private fun buildForegroundNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_bubble),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, BubbleOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bubble)
            .setContentTitle(getString(R.string.notification_bubble_active_title))
            .setContentText(getString(R.string.notification_bubble_active_text))
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.action_stop_bubble), stopIntent)
            .setOngoing(true)
            .build()
    }

    private inline fun runCatchingSilently(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "bubble_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TRACK_CHANGE_SETTLE_DELAY_MS = 350L
        private const val TRACK_MATCH_RETRY_DELAY_MS = 250L
        private const val MAX_SESSION_MATCH_ATTEMPTS = 3
        const val ACTION_STOP = "com.ampbubble.app.action.STOP_BUBBLE"
    }
}
