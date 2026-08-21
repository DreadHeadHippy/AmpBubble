package com.plexbubble.app.notification

import android.app.Notification
import android.media.session.MediaController
import android.media.session.MediaSession
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.plexbubble.app.plex.NowPlayingMetadata
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
            publishMetadata(metadata, controller?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            publishMetadata(controller?.metadata, state?.state == android.media.session.PlaybackState.STATE_PLAYING)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != PLEXAMP_PACKAGE) return
        val token = extractMediaSessionToken(sbn.notification) ?: return

        if (controller?.sessionToken != token) {
            controller?.unregisterCallback(controllerCallback)
            controller = MediaController(applicationContext, token).also {
                it.registerCallback(controllerCallback)
            }
        }
        publishMetadata(
            controller?.metadata,
            controller?.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != PLEXAMP_PACKAGE) return
        controller?.unregisterCallback(controllerCallback)
        controller = null
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
        _nowPlaying.value = null
        super.onDestroy()
    }

    private fun publishMetadata(metadata: android.media.MediaMetadata?, isPlaying: Boolean) {
        if (metadata == null) {
            _nowPlaying.value = null
            return
        }
        _nowPlaying.value = NowPlayingMetadata(
            title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
            isPlaying = isPlaying
        )
    }

    companion object {
        // TODO verify this is Plexamp's actual applicationId on your Z Fold 7 (adb shell dumpsys notification)
        const val PLEXAMP_PACKAGE = "com.plexapp.android"

        private val _nowPlaying = MutableStateFlow<NowPlayingMetadata?>(null)
        val nowPlaying: StateFlow<NowPlayingMetadata?> = _nowPlaying.asStateFlow()
    }
}
