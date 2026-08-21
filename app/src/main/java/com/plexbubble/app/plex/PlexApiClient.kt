package com.plexbubble.app.plex

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared constants and HTTP client for talking to plex.tv and Plex Media Server APIs. */
object PlexApiClient {

    const val PRODUCT_NAME = "PlexBubble"
    const val PLEXTV_BASE_URL = "https://plex.tv"

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
}
