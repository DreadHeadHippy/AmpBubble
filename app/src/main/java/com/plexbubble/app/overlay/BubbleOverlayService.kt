package com.plexbubble.app.overlay

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
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.plexbubble.app.MainActivity
import com.plexbubble.app.R
import com.plexbubble.app.data.SecureTokenStore
import com.plexbubble.app.data.SettingsStore
import com.plexbubble.app.notification.PlexampNotificationListener
import com.plexbubble.app.plex.NowPlayingMetadata
import com.plexbubble.app.plex.NowPlayingRepository
import com.plexbubble.app.plex.PlexRatingRepository
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private var bubbleAlphaState by mutableStateOf(0.45f)
    private var transparencyPercentState by mutableStateOf(45)
    private var panelVisibleState by mutableStateOf(false)
    private var trackTitleState by mutableStateOf<String?>(null)
    private var trackArtistState by mutableStateOf<String?>(null)
    private var ratingState by mutableStateOf(0f)
    private var statusMessageState by mutableStateOf<String?>(null)

    private var resolvedRatingKey: String? = null
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

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        addBubbleWindow()
        observeSettings()
        observeNowPlaying()
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
            windowManager.addView(bubbleView, bubbleLayoutParams)
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
                    panelAlpha = bubbleAlphaState,
                    trackTitle = trackTitleState,
                    trackArtist = trackArtistState,
                    rating = ratingState,
                    onRatingChange = ::submitRating,
                    transparencyPercent = transparencyPercentState,
                    onTransparencyChange = ::onTransparencyChanged,
                    onClose = { togglePanel() },
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
        }
        windowManager.addView(view, params)
        panelView = view
        panelLayoutParams = params
        refreshNowPlayingOnDemand()
    }

    private fun removePanelWindow() {
        panelView?.let { runCatchingSilently { windowManager.removeView(it) } }
        panelView = null
        panelLayoutParams = null
    }

    // --- Settings / transparency -------------------------------------------

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsStore.transparencyPercent.distinctUntilChanged().collectLatest { percent ->
                transparencyPercentState = percent
                bubbleAlphaState = (percent / 100f).coerceIn(0.05f, 1f)
            }
        }
    }

    private fun onTransparencyChanged(percent: Int) {
        transparencyPercentState = percent
        bubbleAlphaState = (percent / 100f).coerceIn(0.05f, 1f)
        lifecycleScope.launch { settingsStore.setTransparencyPercent(percent) }
    }

    // --- Now playing / rating ------------------------------------------------

    private fun observeNowPlaying() {
        lifecycleScope.launch {
            PlexampNotificationListener.nowPlaying.collectLatest { metadata ->
                trackTitleState = metadata?.title ?: "Nothing playing"
                trackArtistState = metadata?.artist
                if (metadata != null && panelVisibleState) {
                    resolveRatingKeyAndPrefillRating(metadata.title, metadata.artist, metadata.durationMs)
                }
            }
        }
    }

    private fun refreshNowPlayingOnDemand() {
        val metadata = PlexampNotificationListener.nowPlaying.value
        if (metadata != null) {
            resolveRatingKeyAndPrefillRating(metadata.title, metadata.artist, metadata.durationMs)
        } else {
            statusMessageState = "Waiting for Plexamp playback..."
        }
    }

    private fun resolveRatingKeyAndPrefillRating(title: String?, artist: String?, durationMs: Long?) {
        lifecycleScope.launch {
            val resolvedBase = currentBaseUrl()
            val token = currentAuthToken()
            if (resolvedBase.isNullOrBlank() || token.isNullOrBlank()) {
                statusMessageState = "Sign in to Plex from the app first"
                return@launch
            }
            val sessions = nowPlayingRepository.fetchActiveSessions(resolvedBase, token).getOrNull().orEmpty()
            val metadata = NowPlayingMetadata(title, artist, null, durationMs, true)
            val match = nowPlayingRepository.matchSession(metadata, sessions)
            if (match != null) {
                resolvedRatingKey = match.ratingKey
                ratingState = (match.userRating ?: 0f) / 2f
                statusMessageState = null
            } else {
                resolvedRatingKey = null
                statusMessageState = "Couldn't match this track in your Plex library yet"
            }
        }
    }

    private fun submitRating(newStarRating: Float) {
        ratingState = newStarRating
        val ratingKey = resolvedRatingKey ?: run {
            statusMessageState = "No matched track to rate"
            return
        }
        lifecycleScope.launch {
            val resolvedBase = currentBaseUrl() ?: return@launch
            val token = currentAuthToken() ?: return@launch
            val result = ratingRepository.rateTrack(resolvedBase, token, ratingKey, newStarRating * 2f)
            statusMessageState = if (result.isSuccess) null else "Failed to save rating, will retry on next tap"
        }
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
            .setSmallIcon(R.drawable.ic_plex_bubble)
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
        const val ACTION_STOP = "com.plexbubble.app.action.STOP_BUBBLE"
    }
}
