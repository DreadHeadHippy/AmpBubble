package com.plexbubble.app.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Discovers the user's owned Plex Media Server and picks a reachable connection URI. */
class PlexServerRepository(private val clientIdentifier: String) {

    private val quickClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun listOwnedServers(authToken: String): Result<List<PlexResource>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${PlexApiClient.PLEXTV_BASE_URL}/api/v2/resources?includeHttps=1")
                .header("Accept", "application/json")
                .header("X-Plex-Product", PlexApiClient.PRODUCT_NAME)
                .header("X-Plex-Client-Identifier", clientIdentifier)
                .header("X-Plex-Token", authToken)
                .get()
                .build()

            PlexApiClient.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Failed to list Plex servers: HTTP ${response.code}")
                val array = JSONArray(response.body?.string().orEmpty())
                (0 until array.length()).mapNotNull { i ->
                    val obj = array.getJSONObject(i)
                    if (obj.optString("provides") != "server") return@mapNotNull null
                    PlexResource(
                        name = obj.optString("name"),
                        clientIdentifier = obj.optString("clientIdentifier"),
                        owned = obj.optBoolean("owned", false),
                        provides = obj.optString("provides"),
                        connections = obj.optJSONArray("connections")?.let { conns ->
                            (0 until conns.length()).map { j ->
                                val c = conns.getJSONObject(j)
                                PlexConnection(
                                    uri = c.optString("uri"),
                                    local = c.optBoolean("local", false),
                                    relay = c.optBoolean("relay", false)
                                )
                            }
                        }.orEmpty()
                    )
                }
            }
        }
    }

    /** Tries each connection (local first) and returns the first one that responds. */
    suspend fun resolveReachableBaseUrl(resource: PlexResource, authToken: String): String? {
        val ordered = resource.connections.sortedByDescending { it.local }
        for (connection in ordered) {
            val reachable = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url("${connection.uri}/identity")
                        .header("X-Plex-Token", authToken)
                        .get()
                        .build()
                    quickClient.newCall(request).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            }
            if (reachable) return connection.uri
        }
        return null
    }
}
