package com.ampbubble.app.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Resolves the current Plexamp now-playing track to a Plex ratingKey via /status/sessions. */
class NowPlayingRepository {

    data class TrackContext(
        val album: String?,
        val year: Int?
    )

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
                        val sessionObject = item.optJSONObject("Session")
                        val sessionId = sessionObject?.optString("id")?.ifBlank { null }
                        PlexSession(
                            sessionId = sessionId,
                            ratingKey = item.optString("ratingKey"),
                            title = item.optString("title"),
                            grandparentTitle = item.optString("grandparentTitle", null),
                            parentTitle = item.optString("parentTitle", null),
                            thumbPath = item.optString("thumb").ifBlank { null },
                            year = item.optInt("year", 0).takeIf { it > 0 },
                            durationMs = if (item.has("duration")) item.optLong("duration") else null,
                            userRating = if (item.has("userRating")) item.optDouble("userRating").toFloat() else null
                        )
                    }
                }
            }
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
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
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
                        year = parentYear ?: year ?: releaseYear
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
