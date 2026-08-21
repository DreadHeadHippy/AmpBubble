package com.plexbubble.app.plex

import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

/** Resolves the current Plexamp now-playing track to a Plex ratingKey via /status/sessions. */
class NowPlayingRepository {

    suspend fun fetchActiveSessions(baseUrl: String, authToken: String): Result<List<PlexSession>> =
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
                    PlexSession(
                        ratingKey = item.optString("ratingKey"),
                        title = item.optString("title"),
                        grandparentTitle = item.optString("grandparentTitle", null),
                        parentTitle = item.optString("parentTitle", null),
                        durationMs = if (item.has("duration")) item.optLong("duration") else null,
                        userRating = if (item.has("userRating")) item.optDouble("userRating").toFloat() else null
                    )
                }
            }
        }

    /** Matches notification-derived metadata against active sessions by title/artist (+ duration tolerance). */
    fun matchSession(metadata: NowPlayingMetadata, sessions: List<PlexSession>): PlexSession? {
        val title = metadata.title?.trim()?.lowercase() ?: return null
        return sessions.firstOrNull { session ->
            val titleMatches = session.title.trim().lowercase() == title
            val artistMatches = metadata.artist == null ||
                session.grandparentTitle?.trim()?.lowercase() == metadata.artist.trim().lowercase()
            val durationMatches = metadata.durationMs == null || session.durationMs == null ||
                kotlin.math.abs(session.durationMs - metadata.durationMs) < 2000
            titleMatches && artistMatches && durationMatches
        } ?: sessions.firstOrNull { it.title.trim().lowercase() == title }
    }
}
