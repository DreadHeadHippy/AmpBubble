package com.plexbubble.app.plex

/** A pending Plex.tv OAuth PIN, used to poll for the user's access token. */
data class PlexPin(
    val id: Long,
    val code: String,
    val authToken: String?
)

/** A reachable connection URI for a Plex Media Server resource. */
data class PlexConnection(
    val uri: String,
    val local: Boolean,
    val relay: Boolean
)

/** A Plex Media Server the signed-in account has access to. */
data class PlexResource(
    val name: String,
    val clientIdentifier: String,
    val owned: Boolean,
    val provides: String,
    val connections: List<PlexConnection>
)

/** A currently active playback session reported by a Plex Media Server. */
data class PlexSession(
    val sessionId: String?,
    val ratingKey: String,
    val title: String,
    val grandparentTitle: String?,
    val parentTitle: String?,
    val thumbPath: String?,
    val year: Int?,
    val durationMs: Long?,
    val userRating: Float?
)

/** Track metadata as reported by Android's MediaController for the Plexamp notification. */
data class NowPlayingMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val durationMs: Long?,
    val isPlaying: Boolean,
    val playbackState: Int? = null,
    val accentColorArgb: Int? = null
)

/** A now-playing track resolved to a Plex ratingKey, ready to be rated. */
data class ResolvedTrack(
    val ratingKey: String,
    val title: String,
    val artist: String?,
    val currentUserRating: Float?
)
