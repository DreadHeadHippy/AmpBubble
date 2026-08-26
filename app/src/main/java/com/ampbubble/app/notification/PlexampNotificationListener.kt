package com.ampbubble.app.notification

import android.app.Notification
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSession
import android.view.KeyEvent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ampbubble.app.plex.NowPlayingMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads Plexamp's media notification to get low-latency now-playing metadata via MediaController.
 * Package name below is our best-known value; verify against an actual device if Plexamp updates it.
 */
class PlexampNotificationListener : NotificationListenerService() {

    private var controller: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            publishMetadata(metadata, controller?.playbackState)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            publishMetadata(controller?.metadata, state)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isSupportedPlexPackage(sbn.packageName)) return
        val token = extractMediaSessionToken(sbn.notification) ?: return

        if (controller?.sessionToken != token) {
            controller?.unregisterCallback(controllerCallback)
            controller = MediaController(applicationContext, token).also {
                it.registerCallback(controllerCallback)
            }
            activeController = controller
        }
        if (activeController == null) activeController = controller
        publishMetadata(
            controller?.metadata,
            controller?.playbackState
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isSupportedPlexPackage(sbn.packageName)) return
        // A single notification removal can happen while playback continues; re-scan active notifications first.
        val replacement = (activeNotifications ?: emptyArray()).firstOrNull { isSupportedPlexPackage(it.packageName) }
        if (replacement != null) {
            onNotificationPosted(replacement)
            return
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        activeController = null
        _nowPlaying.value = null
    }

    override fun onListenerConnected() {
        for (sbn in activeNotifications ?: emptyArray()) {
            onNotificationPosted(sbn)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractMediaSessionToken(notification: Notification): MediaSession.Token? {
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
        }
    }

    override fun onDestroy() {
        controller?.unregisterCallback(controllerCallback)
        controller = null
        activeController = null
        _nowPlaying.value = null
        super.onDestroy()
    }

    private fun publishMetadata(metadata: android.media.MediaMetadata?, playbackState: android.media.session.PlaybackState?) {
        if (metadata == null) {
            _nowPlaying.value = null
            return
        }
        val art = extractAlbumArt(metadata)
        val accentColor = art?.let { dominantColor(it) }
        val playing = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        val playbackPosition = playbackState?.position?.takeIf { it >= 0L }
        _nowPlaying.value = NowPlayingMetadata(
            title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM),
            year = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_YEAR).toInt().takeIf { it > 0 },
            durationMs = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
            isPlaying = playing,
            playbackState = playbackState?.state,
            playbackPositionMs = playbackPosition,
            playbackPositionUpdatedAtMs = playbackState?.lastPositionUpdateTime?.takeIf { it > 0L },
            accentColorArgb = accentColor,
            albumArtBitmap = art
        )
    }

    /** Notification-embedded art loads instantly, unlike the Plex thumb URL which depends on network session matching. */
    private fun extractAlbumArt(metadata: android.media.MediaMetadata): Bitmap? {
        val art = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null

        val maxDimension = 320
        if (art.width <= maxDimension && art.height <= maxDimension) return art

        val scale = maxDimension.toFloat() / maxOf(art.width, art.height)
        val targetWidth = (art.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (art.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(art, targetWidth, targetHeight, true)
    }

    private fun dominantColor(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val stepX = (bitmap.width / 24).coerceAtLeast(1)
        val stepY = (bitmap.height / 24).coerceAtLeast(1)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha > 96) {
                    sumR += android.graphics.Color.red(pixel)
                    sumG += android.graphics.Color.green(pixel)
                    sumB += android.graphics.Color.blue(pixel)
                    count++
                }
                x += stepX
            }
            y += stepY
        }

        if (count == 0L) return null

        val avg = android.graphics.Color.rgb(
            (sumR / count).toInt().coerceIn(0, 255),
            (sumG / count).toInt().coerceIn(0, 255),
            (sumB / count).toInt().coerceIn(0, 255)
        )

        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(avg, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.45f)
        hsv[2] = hsv[2].coerceIn(0.42f, 0.88f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    companion object {
        // Known package ids seen for Plex/Plexamp builds across channels/devices.
        private val SUPPORTED_PLEX_PACKAGES = setOf(
            "tv.plex.labs.plexamp",
            "com.plexamp.android",
            "com.plexapp.android"
        )

        private val _nowPlaying = MutableStateFlow<NowPlayingMetadata?>(null)
        val nowPlaying: StateFlow<NowPlayingMetadata?> = _nowPlaying.asStateFlow()

        @Volatile
        private var activeController: MediaController? = null

        fun togglePlayPause(): Boolean = withTransportControls { controls, controller ->
            val state = controller.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                controls.pause()
            } else {
                controls.play()
            }
            true
        }

        fun skipNext(): Boolean = withTransportControls { controls, _ ->
            controls.skipToNext()
            true
        }

        fun skipPrevious(): Boolean = withTransportControls { controls, _ ->
            controls.skipToPrevious()
            true
        }

        fun seekBy(deltaMs: Long): Boolean = withTransportControls { controls, controller ->
            val current = controller.playbackState?.position ?: 0L
            val target = (current + deltaMs).coerceAtLeast(0L)
            controls.seekTo(target)
            true
        }

        fun stopPlayback(): Boolean = withTransportControls { controls, controller ->
            val actions = controller.playbackState?.actions ?: 0L
            val canStop = (actions and android.media.session.PlaybackState.ACTION_STOP) != 0L
            val canPause = (actions and android.media.session.PlaybackState.ACTION_PAUSE) != 0L
            var dispatched = false
            if (canStop) {
                controls.stop()
                dispatched = true
            }
            if (canPause) {
                controls.pause()
                dispatched = true
            }

            controller.playbackState?.customActions
                ?.filter { action ->
                    val key = action.action?.lowercase().orEmpty()
                    key.contains("stop") || key.contains("end") || key.contains("clear")
                }
                ?.forEach { action ->
                    controls.sendCustomAction(action.action, null)
                    dispatched = true
                }

            if (dispatchMediaButton(controller, KeyEvent.KEYCODE_MEDIA_STOP)) {
                dispatched = true
            }

            dispatched
        }

        private fun dispatchMediaButton(controller: MediaController, keyCode: Int): Boolean {
            val down = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val up = controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            return down || up
        }

        private inline fun withTransportControls(action: (MediaController.TransportControls, MediaController) -> Boolean): Boolean {
            val controller = activeController ?: return false
            val controls = controller.transportControls ?: return false
            return runCatching {
                action(controls, controller)
            }.getOrDefault(false)
        }

        private fun isSupportedPlexPackage(packageName: String?): Boolean =
            packageName != null && packageName in SUPPORTED_PLEX_PACKAGES
    }
}
