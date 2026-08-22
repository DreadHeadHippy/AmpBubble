package com.plexbubble.app.plex

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared constants and HTTP client for talking to plex.tv and Plex Media Server APIs. */
object PlexApiClient {

    const val PRODUCT_NAME = "PlexBubble"
    const val PRODUCT_VERSION = "1.0"
    const val PLEXTV_BASE_URL = "https://plex.tv"

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    fun requestHeaders(clientIdentifier: String): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "X-Plex-Product" to PRODUCT_NAME,
        "X-Plex-Version" to PRODUCT_VERSION,
        "X-Plex-Client-Identifier" to clientIdentifier,
        "X-Plex-Platform" to "Android",
        "X-Plex-Platform-Version" to android.os.Build.VERSION.RELEASE,
        "X-Plex-Device" to android.os.Build.MODEL,
        "X-Plex-Device-Name" to android.os.Build.MODEL
    )
}
