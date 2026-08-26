package com.ampbubble.app.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONException
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
    suspend fun createPin(): Result<Pair<PlexPin, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("strong", "true")
                .add("X-Plex-Product", PlexApiClient.PRODUCT_NAME)
                .add("X-Plex-Client-Identifier", clientIdentifier)
                .build()

            val request = Request.Builder()
                .url("${PlexApiClient.PLEXTV_BASE_URL}/api/v2/pins")
                .header("Accept", "application/json")
                .header("X-Plex-Product", PlexApiClient.PRODUCT_NAME)
                .header("X-Plex-Client-Identifier", clientIdentifier)
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
    }

    private fun buildAuthUrl(pinCode: String): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val product = enc(PlexApiClient.PRODUCT_NAME)
        val clientId = enc(clientIdentifier)
        return "https://app.plex.tv/auth#?clientID=$clientId&code=$pinCode" +
            "&context%5Bdevice%5D%5Bproduct%5D=$product"
    }

    /** Polls the PIN once per second until claimed, expired (~15 min), or [maxAttempts] reached. */
    suspend fun pollForToken(pin: PlexPin, maxAttempts: Int = 900): Result<String> {
        var lastError: String? = null
        repeat(maxAttempts) {
            val v2Url = "${PlexApiClient.PLEXTV_BASE_URL}/api/v2/pins/${pin.id}"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("code", pin.code)
                .addQueryParameter("X-Plex-Client-Identifier", clientIdentifier)
                .build()

            val v2Request = Request.Builder()
                .url(v2Url)
                .header("Accept", "application/json")
                .header("X-Plex-Client-Identifier", clientIdentifier)
                .get()
                .build()

            val v2Result = withContext(Dispatchers.IO) { runCatching { executeForJson(v2Request) } }
            v2Result.exceptionOrNull()?.let { error ->
                lastError = error.message
                if (error is IOException && error.message?.contains("HTTP 4") == true) {
                    return Result.failure(error)
                }
            }
            val v2Token = v2Result.getOrNull()?.let { extractAuthToken(it) }
            if (!v2Token.isNullOrBlank()) {
                return Result.success(v2Token)
            }

            // Legacy fallback for environments where /api/v2 polling does not surface authToken.
            val legacyUrl = "${PlexApiClient.PLEXTV_BASE_URL}/pins/${pin.id}.json"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("code", pin.code)
                .addQueryParameter("X-Plex-Client-Identifier", clientIdentifier)
                .build()

            val legacyRequest = Request.Builder()
                .url(legacyUrl)
                .header("Accept", "application/json")
                .header("X-Plex-Client-Identifier", clientIdentifier)
                .get()
                .build()

            val legacyResult = withContext(Dispatchers.IO) { runCatching { executeForJson(legacyRequest) } }
            legacyResult.exceptionOrNull()?.let { error ->
                lastError = error.message
                if (error is IOException && error.message?.contains("HTTP 4") == true) {
                    return Result.failure(error)
                }
            }
            val token = legacyResult.getOrNull()?.let { extractAuthToken(it) }
            if (!token.isNullOrBlank()) {
                return Result.success(token)
            }
            delay(1000)
        }
        val detail = lastError?.let { " Last error: $it" }.orEmpty()
        return Result.failure(IOException("Plex sign-in timed out; please try again.$detail"))
    }

    private fun executeForJson(request: Request): JSONObject {
        PlexApiClient.httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = if (bodyString.isBlank()) "Plex request failed: HTTP ${response.code}" else "Plex request failed: HTTP ${response.code} - $bodyString"
                throw IOException(message)
            }
            try {
                return JSONObject(bodyString)
            } catch (e: JSONException) {
                val payload = if (bodyString.isBlank()) "<empty body>" else bodyString
                throw IOException("Plex returned unexpected JSON from ${request.url}: $payload", e)
            }
        }
    }

    /** Verifies a stored token is still valid by calling the plex.tv user endpoint. */
    suspend fun verifyToken(authToken: String): Boolean {
        val request = Request.Builder()
            .url("${PlexApiClient.PLEXTV_BASE_URL}/api/v2/user")
            .apply {
                PlexApiClient.requestHeaders(clientIdentifier).forEach { (key, value) ->
                    header(key, value)
                }
            }
            .header("X-Plex-Token", authToken)
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                PlexApiClient.httpClient.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun extractAuthToken(json: JSONObject): String? =
    json.optStringOrNull("authToken")
        ?: json.optStringOrNull("auth_token")
