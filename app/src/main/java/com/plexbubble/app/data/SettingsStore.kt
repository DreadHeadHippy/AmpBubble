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
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "plex_bubble_settings")

/** Non-secret app settings (transparency, bubble position, manual server override toggle). */
class SettingsStore(private val context: Context) {

    private object Keys {
        val TRANSPARENCY_PERCENT = intPreferencesKey("transparency_percent")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
        val USE_MANUAL_SERVER = booleanPreferencesKey("use_manual_server")
        val MANUAL_BASE_URL = stringPreferencesKey("manual_base_url")
        val RESOLVED_BASE_URL = stringPreferencesKey("resolved_base_url")
        val CLIENT_IDENTIFIER = stringPreferencesKey("client_identifier")
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val TRANSPARENCY_DEFAULT = 45f
    }

    val transparencyPercent: Flow<Int> =
        context.dataStore.data.map { it[Keys.TRANSPARENCY_PERCENT] ?: Keys.TRANSPARENCY_DEFAULT.toInt() }

    suspend fun setTransparencyPercent(value: Int) {
        context.dataStore.edit { it[Keys.TRANSPARENCY_PERCENT] = value.coerceIn(5, 100) }
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
