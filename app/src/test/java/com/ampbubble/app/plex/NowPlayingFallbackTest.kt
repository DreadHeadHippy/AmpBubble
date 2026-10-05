package com.ampbubble.app.plex

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingFallbackTest {

    @Test
    fun missingOrStoppedPlaybackStateUsesPlexSessionFallback() {
        assertTrue((null as NowPlayingMetadata?).needsPlexSessionFallback())
        assertTrue(metadata(playbackState = null).needsPlexSessionFallback())
        assertTrue(metadata(PlaybackState.STATE_STOPPED).needsPlexSessionFallback())
        assertTrue(metadata(PlaybackState.STATE_NONE).needsPlexSessionFallback())
    }

    @Test
    fun activeOrPausedPlaybackStateDoesNotUseFallback() {
        assertFalse(metadata(PlaybackState.STATE_PLAYING).needsPlexSessionFallback())
        assertFalse(metadata(PlaybackState.STATE_PAUSED).needsPlexSessionFallback())
    }

    private fun metadata(playbackState: Int?) = NowPlayingMetadata(
        title = "Track",
        artist = "Artist",
        album = null,
        year = null,
        durationMs = null,
        isPlaying = playbackState == PlaybackState.STATE_PLAYING,
        playbackState = playbackState
    )
}
