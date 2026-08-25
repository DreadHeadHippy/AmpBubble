package com.plexbubble.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "plex_bubble_settings")

data class PendingRatingRecord(
    val ratingKey: String,
    val stars0to5: Float,
    val title: String?,
    val artist: String?,
    val timestampMs: Long
)

data class RatedTrackRecord(
    val ratingKey: String,
    val title: String,
    val artist: String?,
    val thumbPath: String?,
    val stars0to5: Float,
    val timestampMs: Long
)

/** Non-secret app settings (bubble position and manual server override toggle). */
class SettingsStore(private val context: Context) {

    private object Keys {
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
        val USE_MANUAL_SERVER = booleanPreferencesKey("use_manual_server")
        val MANUAL_BASE_URL = stringPreferencesKey("manual_base_url")
        val RESOLVED_BASE_URL = stringPreferencesKey("resolved_base_url")
        val CLIENT_IDENTIFIER = stringPreferencesKey("client_identifier")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val RATING_CACHE_JSON = stringPreferencesKey("rating_cache_json")
        val PENDING_RATINGS_JSON = stringPreferencesKey("pending_ratings_json")
        val RECENT_HISTORY_JSON = stringPreferencesKey("recent_history_json")
        val DIAGNOSTIC_LOG_JSON = stringPreferencesKey("diagnostic_log_json")
        val RATING_PRESETS_CSV = stringPreferencesKey("rating_presets_csv")
        val SHOW_RECENT_IN_BUBBLE = booleanPreferencesKey("show_recent_in_bubble")
    }

    val bubblePosition: Flow<Pair<Int, Int>> =
        context.dataStore.data.map { (it[Keys.BUBBLE_X] ?: 0) to (it[Keys.BUBBLE_Y] ?: 200) }

    suspend fun setBubblePosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[Keys.BUBBLE_X] = x
            it[Keys.BUBBLE_Y] = y
        }
    }

    val useManualServer: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.USE_MANUAL_SERVER] ?: false }

    val manualBaseUrl: Flow<String> =
        context.dataStore.data.map { it[Keys.MANUAL_BASE_URL] ?: "" }

    suspend fun setManualServer(enabled: Boolean, baseUrl: String) {
        context.dataStore.edit {
            it[Keys.USE_MANUAL_SERVER] = enabled
            it[Keys.MANUAL_BASE_URL] = baseUrl.trimEnd('/')
        }
    }

    val resolvedBaseUrl: Flow<String> =
        context.dataStore.data.map { it[Keys.RESOLVED_BASE_URL] ?: "" }

    suspend fun setResolvedBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[Keys.RESOLVED_BASE_URL] = baseUrl.trimEnd('/') }
    }

    val bubbleEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.BUBBLE_ENABLED] ?: false }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BUBBLE_ENABLED] = enabled }
    }

    /** Persisted cache of per-track star ratings keyed by Plex ratingKey. */
    suspend fun getCachedRatings(): Map<String, Float> {
        val raw = context.dataStore.data.map { it[Keys.RATING_CACHE_JSON] ?: "{}" }.first()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optDouble(key, Double.NaN)
                    if (!value.isNaN()) put(key, value.toFloat())
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun setCachedRating(ratingKey: String, stars0to5: Float) {
        val normalized = stars0to5.coerceIn(0f, 5f)
        context.dataStore.edit { prefs ->
            val currentRaw = prefs[Keys.RATING_CACHE_JSON] ?: "{}"
            val current = runCatching { JSONObject(currentRaw) }.getOrElse { JSONObject() }
            current.put(ratingKey, normalized.toDouble())
            prefs[Keys.RATING_CACHE_JSON] = current.toString()
        }
    }

    suspend fun getPendingRatings(): List<PendingRatingRecord> {
        val raw = context.dataStore.data.map { it[Keys.PENDING_RATINGS_JSON] ?: "[]" }.first()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = obj.optString("ratingKey")
                if (key.isBlank()) return@mapNotNull null
                PendingRatingRecord(
                    ratingKey = key,
                    stars0to5 = obj.optDouble("stars0to5", 0.0).toFloat().coerceIn(0f, 5f),
                    title = obj.optString("title").ifBlank { null },
                    artist = obj.optString("artist").ifBlank { null },
                    timestampMs = obj.optLong("timestampMs", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun setPendingRatings(items: List<PendingRatingRecord>) {
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject()
                        .put("ratingKey", item.ratingKey)
                        .put("stars0to5", item.stars0to5.toDouble())
                        .put("title", item.title ?: "")
                        .put("artist", item.artist ?: "")
                        .put("timestampMs", item.timestampMs)
                )
            }
            prefs[Keys.PENDING_RATINGS_JSON] = arr.toString()
        }
    }

    suspend fun addPendingRating(item: PendingRatingRecord) {
        val current = getPendingRatings().toMutableList()
        current.removeAll { it.ratingKey == item.ratingKey }
        current.add(item)
        setPendingRatings(current)
    }

    suspend fun getRecentRatedTracks(): List<RatedTrackRecord> {
        val raw = context.dataStore.data.map { it[Keys.RECENT_HISTORY_JSON] ?: "[]" }.first()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = obj.optString("ratingKey")
                val title = obj.optString("title")
                if (key.isBlank() || title.isBlank()) return@mapNotNull null
                RatedTrackRecord(
                    ratingKey = key,
                    title = title,
                    artist = obj.optString("artist").ifBlank { null },
                    thumbPath = obj.optString("thumbPath").ifBlank { null },
                    stars0to5 = obj.optDouble("stars0to5", 0.0).toFloat().coerceIn(0f, 5f),
                    timestampMs = obj.optLong("timestampMs", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun addRecentRatedTrack(item: RatedTrackRecord, maxItems: Int = 20) {
        val list = getRecentRatedTracks().toMutableList()
        list.removeAll { it.ratingKey == item.ratingKey }
        list.add(0, item)
        val trimmed = if (list.size > maxItems) list.take(maxItems) else list
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            trimmed.forEach { track ->
                arr.put(
                    JSONObject()
                        .put("ratingKey", track.ratingKey)
                        .put("title", track.title)
                        .put("artist", track.artist ?: "")
                        .put("thumbPath", track.thumbPath ?: "")
                        .put("stars0to5", track.stars0to5.toDouble())
                        .put("timestampMs", track.timestampMs)
                )
            }
            prefs[Keys.RECENT_HISTORY_JSON] = arr.toString()
        }
    }

    suspend fun getDiagnosticEvents(): List<String> {
        val raw = context.dataStore.data.map { it[Keys.DIAGNOSTIC_LOG_JSON] ?: "[]" }.first()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).ifBlank { null } }
        }.getOrDefault(emptyList())
    }

    suspend fun addDiagnosticEvent(event: String, maxItems: Int = 120) {
        val prefix = System.currentTimeMillis()
        val list = getDiagnosticEvents().toMutableList()
        list.add("$prefix|$event")
        val trimmed = if (list.size > maxItems) list.takeLast(maxItems) else list
        context.dataStore.edit { prefs ->
            val arr = JSONArray()
            trimmed.forEach { arr.put(it) }
            prefs[Keys.DIAGNOSTIC_LOG_JSON] = arr.toString()
        }
    }

    suspend fun getRatingPresets(): List<Float> {
        val raw = context.dataStore.data.map { it[Keys.RATING_PRESETS_CSV] ?: "" }.first()
        return parseRatingPresets(raw)
    }

    suspend fun setRatingPresetsFromText(csv: String) {
        val normalized = parseRatingPresets(csv)
            .joinToString(",") { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
        context.dataStore.edit { it[Keys.RATING_PRESETS_CSV] = normalized }
    }

    val ratingPresets: Flow<List<Float>> =
        context.dataStore.data.map { prefs -> parseRatingPresets(prefs[Keys.RATING_PRESETS_CSV] ?: "") }

    val showRecentInBubble: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SHOW_RECENT_IN_BUBBLE] ?: false }

    suspend fun setShowRecentInBubble(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_RECENT_IN_BUBBLE] = enabled }
    }

    suspend fun exportSettingsSnapshot(): String {
        val prefs = context.dataStore.data.first()
        return JSONObject()
            .put("bubble_x", prefs[Keys.BUBBLE_X] ?: 0)
            .put("bubble_y", prefs[Keys.BUBBLE_Y] ?: 200)
            .put("use_manual_server", prefs[Keys.USE_MANUAL_SERVER] ?: false)
            .put("manual_base_url", prefs[Keys.MANUAL_BASE_URL] ?: "")
            .put("resolved_base_url", prefs[Keys.RESOLVED_BASE_URL] ?: "")
            .put("bubble_enabled", prefs[Keys.BUBBLE_ENABLED] ?: false)
            .put("rating_cache_json", prefs[Keys.RATING_CACHE_JSON] ?: "{}")
            .put("pending_ratings_json", prefs[Keys.PENDING_RATINGS_JSON] ?: "[]")
            .put("recent_history_json", prefs[Keys.RECENT_HISTORY_JSON] ?: "[]")
            .put("diagnostic_log_json", prefs[Keys.DIAGNOSTIC_LOG_JSON] ?: "[]")
            .put("rating_presets_csv", prefs[Keys.RATING_PRESETS_CSV] ?: "")
                .put("show_recent_in_bubble", prefs[Keys.SHOW_RECENT_IN_BUBBLE] ?: false)
            .toString(2)
    }

    suspend fun importSettingsSnapshot(snapshotJson: String): Result<Unit> = runCatching {
        val json = JSONObject(snapshotJson)
        context.dataStore.edit { prefs ->
            if (json.has("bubble_x")) prefs[Keys.BUBBLE_X] = json.optInt("bubble_x", 0)
            if (json.has("bubble_y")) prefs[Keys.BUBBLE_Y] = json.optInt("bubble_y", 200)
            if (json.has("use_manual_server")) prefs[Keys.USE_MANUAL_SERVER] = json.optBoolean("use_manual_server", false)
            if (json.has("manual_base_url")) prefs[Keys.MANUAL_BASE_URL] = json.optString("manual_base_url", "")
            if (json.has("resolved_base_url")) prefs[Keys.RESOLVED_BASE_URL] = json.optString("resolved_base_url", "")
            if (json.has("bubble_enabled")) prefs[Keys.BUBBLE_ENABLED] = json.optBoolean("bubble_enabled", false)
            if (json.has("rating_cache_json")) prefs[Keys.RATING_CACHE_JSON] = json.optString("rating_cache_json", "{}")
            if (json.has("pending_ratings_json")) prefs[Keys.PENDING_RATINGS_JSON] = json.optString("pending_ratings_json", "[]")
            if (json.has("recent_history_json")) prefs[Keys.RECENT_HISTORY_JSON] = json.optString("recent_history_json", "[]")
            if (json.has("diagnostic_log_json")) prefs[Keys.DIAGNOSTIC_LOG_JSON] = json.optString("diagnostic_log_json", "[]")
            if (json.has("rating_presets_csv")) {
                val raw = json.optString("rating_presets_csv", "")
                val normalized = parseRatingPresets(raw)
                    .joinToString(",") { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
                prefs[Keys.RATING_PRESETS_CSV] = normalized
            }
            if (json.has("show_recent_in_bubble")) prefs[Keys.SHOW_RECENT_IN_BUBBLE] = json.optBoolean("show_recent_in_bubble", false)
        }
    }

    private fun parseRatingPresets(raw: String): List<Float> {
        if (raw.isBlank()) return emptyList()
        return raw
            .split(',', ';', ' ', '\n', '\t')
            .mapNotNull { token -> token.trim().toFloatOrNull()?.coerceIn(0f, 5f) }
            .distinct()
    }

    /** Stable per-install identifier required by the Plex API for auth and session calls. */
    suspend fun getOrCreateClientIdentifier(): String {
        val existing = context.dataStore.data.map { it[Keys.CLIENT_IDENTIFIER] }.first()
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.CLIENT_IDENTIFIER] = newId }
        return newId
    }
}

/** Encrypted storage for the Plex access token (X-Plex-Token). */
class SecureTokenStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "plex_bubble_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
    }
}
