package com.ampbubble.app.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Reads and writes track ratings on a Plex Media Server (0-10 scale, i.e. 0-5 stars). */
class PlexRatingRepository {

    suspend fun rateTrack(baseUrl: String, authToken: String, ratingKey: String, rating0to10: Float): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/:/rate" +
                    "?key=$ratingKey&identifier=com.plexapp.plugins.library&rating=$rating0to10"
                val request = Request.Builder()
                    .url(url)
                    .header("X-Plex-Token", authToken)
                    .put("".toRequestBody(null))
                    .build()

                PlexApiClient.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Rating failed: HTTP ${response.code}")
                    }
                }
            }
        }
}
