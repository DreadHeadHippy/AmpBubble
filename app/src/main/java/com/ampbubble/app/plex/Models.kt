package com.ampbubble.app.plex

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
    val userRating: Float?,
    val originalCodec: String? = null,
    val transcodedCodec: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val sourcePath: String? = null
)

fun formatCodecLabel(
    originalCodec: String?,
    transcodedCodec: String?,
    bitrateKbps: Int? = null,
    sampleRateHz: Int? = null,
    bitDepth: Int? = null
): String? {
    val original = originalCodec?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    val transcoded = transcodedCodec?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
    val sourceDetails = when {
        sampleRateHz != null && sampleRateHz > 0 && bitDepth != null && bitDepth > 0 ->
            "${sampleRateHz / 1000}/${bitDepth}"
        sampleRateHz != null && sampleRateHz > 0 -> "${sampleRateHz / 1000}"
        bitDepth != null && bitDepth > 0 -> bitDepth.toString()
        else -> null
    }
    val source = original?.let { codec -> sourceDetails?.let { "$codec $it" } ?: codec }
    val codec = when {
        source == null -> transcoded
        transcoded == null || transcoded == original -> source
        else -> "$source -> $transcoded"
    }
    return codec?.let { label ->
        val bitrate = bitrateKbps?.takeIf { it > 0 }?.let { " · ${it} kbps" }.orEmpty()
        "$label$bitrate"
    }
}

fun formatBitrateLabel(bitrateKbps: Int?): String? =
    bitrateKbps?.takeIf { it > 0 }?.toString()

fun formatSourceQualityLabel(sampleRateHz: Int?, bitDepth: Int?): String? {
    val sampleRate = sampleRateHz?.takeIf { it > 0 }?.let { it / 1000 }
    val depth = bitDepth?.takeIf { it > 0 }
    return when {
        sampleRate != null && depth != null -> "$sampleRate/$depth"
        sampleRate != null -> sampleRate.toString()
        depth != null -> depth.toString()
        else -> null
    }
}

/** Track metadata as reported by Android's MediaController for the Plexamp notification. */
data class NowPlayingMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val durationMs: Long?,
    val isPlaying: Boolean,
    val playbackState: Int? = null,
    val playbackPositionMs: Long? = null,
    val playbackPositionUpdatedAtMs: Long? = null,
    val accentColorArgb: Int? = null,
    val albumArtBitmap: android.graphics.Bitmap? = null
)

/** A now-playing track resolved to a Plex ratingKey, ready to be rated. */
data class ResolvedTrack(
    val ratingKey: String,
    val title: String,
    val artist: String?,
    val currentUserRating: Float?
)
