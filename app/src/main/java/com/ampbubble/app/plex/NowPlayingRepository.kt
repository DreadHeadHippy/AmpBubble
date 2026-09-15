package com.ampbubble.app.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Resolves the current Plexamp now-playing track to a Plex ratingKey via /status/sessions. */
class NowPlayingRepository {

    data class TrackContext(
        val album: String?,
        val year: Int?,
        val sampleRateHz: Int? = null,
        val bitDepth: Int? = null,
        val sourcePath: String? = null
    )

    data class AudioQuality(val sampleRateHz: Int, val bitDepth: Int)

    suspend fun fetchActiveSessions(baseUrl: String, authToken: String): Result<List<PlexSession>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/status/sessions")
                    .header("Accept", "application/json")
                    .header("X-Plex-Token", authToken)
                    .get()
                    .build()

                PlexApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Session lookup failed: HTTP ${response.code}")
                    val root = org.json.JSONObject(response.body?.string().orEmpty())
                    val container = root.optJSONObject("MediaContainer")
                    val metadata: JSONArray = container?.optJSONArray("Metadata") ?: JSONArray()
                    (0 until metadata.length()).map { i ->
                        val item = metadata.getJSONObject(i)
                        parsePlexSession(item)
                    }
                }
            }
        }

    internal fun parsePlexSession(item: JSONObject): PlexSession {
        val sessionId = item.optJSONObject("Session")?.optString("id")?.ifBlank { null }
        val media = item.optJSONArray("Media")?.optJSONObject(0)
        val part = media?.optJSONArray("Part")?.optJSONObject(0)
        val transcode = item.optJSONObject("TranscodeSession")
        val audioDecision = transcode?.optString("audioDecision")?.lowercase()
        val transcodedCodec = transcode
            ?.optString("audioCodec")
            ?.takeIf { !it.isNullOrBlank() && (audioDecision == "transcode" || audioDecision == null) }
        val bitrateKbps = audioBitrateKbps(media, part, transcode)
        val (sampleRateHz, bitDepth) = audioQuality(media, part)

        return PlexSession(
                            sessionId = sessionId,
                            ratingKey = item.optString("ratingKey"),
                            title = item.optString("title"),
                            grandparentTitle = item.optString("grandparentTitle").ifBlank { null },
                            parentTitle = item.optString("parentTitle").ifBlank { null },
                            thumbPath = item.optString("thumb").ifBlank { null },
                            year = item.optInt("year", 0).takeIf { it > 0 },
                            durationMs = if (item.has("duration")) item.optLong("duration") else null,
                            userRating = if (item.has("userRating")) item.optDouble("userRating").toFloat() else null,
                            originalCodec = part?.optString("codec")?.ifBlank {
                                part.optString("container").ifBlank {
                                    media?.optString("codec")?.ifBlank { media.optString("container") }
                                }
                            },
                            transcodedCodec = transcodedCodec,
                            bitrateKbps = bitrateKbps,
                            sampleRateHz = sampleRateHz,
                            bitDepth = bitDepth,
                            sourcePath = part?.optString("key")?.ifBlank { null }
                        )
    }

    private fun JSONObject.numericValue(key: String): Int? =
        optString(key).toDoubleOrNull()?.toInt()

    private fun audioBitrateKbps(media: JSONObject?, part: JSONObject?, transcode: JSONObject?): Int? =
        sequenceOf(
            transcode?.numericValue("audioBitrate"),
            part?.numericValue("bitrate"),
            media?.numericValue("bitrate")
        ).firstOrNull { it != null && it > 0 }

    private fun audioQuality(media: JSONObject?, part: JSONObject?): Pair<Int?, Int?> {
        val sampleRateHz = sequenceOf(
            part?.numericValue("samplingRate"),
            part?.numericValue("sampleRate"),
            media?.numericValue("samplingRate"),
            media?.numericValue("sampleRate")
        ).firstOrNull { it != null && it > 0 }?.let { rate ->
            if (rate < 1000) rate * 1000 else rate
        }
        val bitDepth = sequenceOf(
            part?.numericValue("bitDepth"),
            part?.numericValue("bitsPerSample"),
            media?.numericValue("bitDepth"),
            media?.numericValue("bitsPerSample")
        ).firstOrNull { it != null && it > 0 }
        return sampleRateHz to bitDepth
    }

    suspend fun fetchFlacQuality(baseUrl: String, authToken: String, sourcePath: String): Result<AudioQuality?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = sourcePath.toHttpUrlOrNull()?.newBuilder()
                    ?: baseUrl.trimEnd('/').plus(sourcePath).toHttpUrlOrNull()?.newBuilder()
                    ?: return@runCatching null
                endpoint.addQueryParameter("X-Plex-Token", authToken)
                val request = Request.Builder()
                    .url(endpoint.build())
                    .header("Range", "bytes=0-65535")
                    .get()
                    .build()
                PlexApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parseFlacStreamInfo(response.body?.bytes() ?: return@use null)
                }
            }
        }

    internal fun parseFlacStreamInfo(bytes: ByteArray): AudioQuality? {
        if (bytes.size < 4 || bytes[0] != 'f'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'a'.code.toByte() || bytes[3] != 'C'.code.toByte()
        ) return null

        var offset = 4
        while (offset + 4 <= bytes.size) {
            val header = bytes[offset].toInt() and 0xFF
            val blockType = header and 0x7F
            val blockLength = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
            offset += 4
            if (blockType == 0 && blockLength >= 18 && offset + blockLength <= bytes.size) {
                val audioInfoOffset = offset + 10
                val sampleRate = ((bytes[audioInfoOffset].toInt() and 0xFF) shl 12) or
                    ((bytes[audioInfoOffset + 1].toInt() and 0xFF) shl 4) or
                    ((bytes[audioInfoOffset + 2].toInt() and 0xF0) ushr 4)
                val bitDepth = ((((bytes[audioInfoOffset + 2].toInt() and 0x01) shl 4) or
                    ((bytes[audioInfoOffset + 3].toInt() and 0xF0) ushr 4)) + 1)
                return AudioQuality(sampleRate, bitDepth)
            }
            offset += blockLength
        }
        return null
    }

    suspend fun terminateSession(baseUrl: String, authToken: String, sessionId: String, reason: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = "${baseUrl.trimEnd('/')}/status/sessions/terminate"
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addQueryParameter("sessionId", sessionId)
                    ?.addQueryParameter("reason", reason)
                    ?.build()
                    ?: throw IOException("Invalid base URL")

                val request = Request.Builder()
                    .url(endpoint)
                    .header("X-Plex-Token", authToken)
                    .post(ByteArray(0).toRequestBody(null))
                    .build()

                PlexApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Session terminate failed: HTTP ${response.code}")
                    }
                }
            }
        }

    suspend fun fetchTrackContext(baseUrl: String, authToken: String, ratingKey: String): Result<TrackContext> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/library/metadata/$ratingKey")
                    .header("Accept", "application/json")
                    .header("X-Plex-Token", authToken)
                    .get()
                    .build()

                PlexApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Metadata lookup failed: HTTP ${response.code}")
                    val root = JSONObject(response.body?.string().orEmpty())
                    val container = root.optJSONObject("MediaContainer")
                    val metadata = container?.optJSONArray("Metadata")
                    val item = metadata?.optJSONObject(0) ?: JSONObject()
                    val album = item.optString("parentTitle").ifBlank {
                        item.optString("album").ifBlank { null }
                    }
                    val parentYear = item.optInt("parentYear", 0).takeIf { it > 0 }
                    val year = item.optInt("year", 0).takeIf { it > 0 }
                    val releaseYear = item.optString("originallyAvailableAt")
                        .takeIf { it.length >= 4 }
                        ?.substring(0, 4)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }

                    TrackContext(
                        album = album,
                        year = parentYear ?: year ?: releaseYear,
                        sampleRateHz = audioQuality(
                            item.optJSONArray("Media")?.optJSONObject(0),
                            item.optJSONArray("Media")?.optJSONObject(0)?.optJSONArray("Part")?.optJSONObject(0)
                        ).first,
                        bitDepth = audioQuality(
                            item.optJSONArray("Media")?.optJSONObject(0),
                            item.optJSONArray("Media")?.optJSONObject(0)?.optJSONArray("Part")?.optJSONObject(0)
                        ).second,
                        sourcePath = item.optJSONArray("Media")?.optJSONObject(0)
                            ?.optJSONArray("Part")?.optJSONObject(0)?.optString("key")?.ifBlank { null }
                    )
                }
            }
        }

    /** Matches notification-derived metadata against active sessions by title/artist (+ duration tolerance). */
    fun matchSession(metadata: NowPlayingMetadata, sessions: List<PlexSession>): PlexSession? {
        val title = metadata.title?.trim()?.lowercase() ?: return null
        val exact = sessions.firstOrNull { session ->
            val titleMatches = session.title.trim().lowercase() == title
            val artistMatches = metadata.artist != null &&
                session.grandparentTitle?.trim()?.lowercase() == metadata.artist.trim().lowercase()
            val durationMatches = metadata.durationMs == null || session.durationMs == null ||
                kotlin.math.abs(session.durationMs - metadata.durationMs) < 2000
            titleMatches && artistMatches && durationMatches
        }
        if (exact != null) return exact

        val likely = sessions.firstOrNull { session ->
            val titleMatches = session.title.trim().lowercase() == title
            val artistMatches = metadata.artist != null &&
                session.grandparentTitle?.trim()?.lowercase() == metadata.artist.trim().lowercase()
            titleMatches && artistMatches
        }
        if (likely != null) return likely

        val fallback = sessions.firstOrNull { it.title.trim().lowercase() == title }
        if (fallback != null) return fallback

        return null
    }
}
