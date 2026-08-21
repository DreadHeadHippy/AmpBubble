package com.plexbubble.app.plex

import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

sealed class PlexAuthState {
    object Idle : PlexAuthState()
    data class AwaitingBrowser(val authUrl: String) : PlexAuthState()
    object Polling : PlexAuthState()
    data class Success(val authToken: String) : PlexAuthState()
    data class Failed(val message: String) : PlexAuthState()
    object Expired : PlexAuthState()
}

/** Implements the Plex.tv PIN-based OAuth sign-in flow (see developer.plex.tv auth docs). */
class PlexAuthRepository(private val clientIdentifier: String) {

    /** Creates a PIN and returns it along with the browser URL the user must visit. */
    suspend fun createPin(): Result<Pair<PlexPin, String>> = runCatching {
        val body = FormBody.Builder()
            .add("strong", "true")
            .add("X-Plex-Product", PlexApiClient.PRODUCT_NAME)
            .add("X-Plex-Client-Identifier", clientIdentifier)
            .build()

        val request = Request.Builder()
            .url("${PlexApiClient.PLEXTV_BASE_URL}/api/v2/pins")
            .header("Accept", "application/json")
            .post(body)
            .build()

        val json = executeForJson(request)
        val pin = PlexPin(
            id = json.getLong("id"),
            code = json.getString("code"),
            authToken = json.optStringOrNull("authToken")
        )
        val authUrl = buildAuthUrl(pin.code)
        pin to authUrl
    }

    private fun buildAuthUrl(pinCode: String): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val forwardUrl = enc("plexbubble://auth")
        val product = enc(PlexApiClient.PRODUCT_NAME)
        val clientId = enc(clientIdentifier)
        return "https://app.plex.tv/auth#?clientID=$clientId&code=$pinCode" +
            "&context%5Bdevice%5D%5Bproduct%5D=$product&forwardUrl=$forwardUrl"
    }

    /** Polls the PIN once per second until claimed, expired (~15 min), or [maxAttempts] reached. */
    suspend fun pollForToken(pin: PlexPin, maxAttempts: Int = 900): Result<String> {
        repeat(maxAttempts) {
            val request = Request.Builder()
                .url("${PlexApiClient.PLEXTV_BASE_URL}/api/v2/pins/${pin.id}?code=${pin.code}")
                .header("Accept", "application/json")
                .header("X-Plex-Client-Identifier", clientIdentifier)
                .get()
                .build()

            val result = runCatching { executeForJson(request) }
            val json = result.getOrNull()
            val token = json?.optStringOrNull("authToken")
            if (!token.isNullOrBlank()) {
                return Result.success(token)
            }
            delay(1000)
        }
        return Result.failure(IOException("Plex sign-in timed out; please try again"))
    }

    private fun executeForJson(request: Request): JSONObject {
        PlexApiClient.httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Plex request failed: HTTP ${response.code}")
            }
            return JSONObject(bodyString)
        }
    }

    /** Verifies a stored token is still valid by calling the plex.tv user endpoint. */
    suspend fun verifyToken(authToken: String): Boolean {
        val request = Request.Builder()
            .url("${PlexApiClient.PLEXTV_BASE_URL}/api/v2/user")
            .header("Accept", "application/json")
            .header("X-Plex-Product", PlexApiClient.PRODUCT_NAME)
            .header("X-Plex-Client-Identifier", clientIdentifier)
            .header("X-Plex-Token", authToken)
            .get()
            .build()
        return runCatching {
            PlexApiClient.httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
