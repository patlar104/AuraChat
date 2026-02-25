package com.personal.aurachat.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.personal.aurachat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userSettingsDataStore by preferencesDataStore(name = "user_settings")

class DefaultSettingsRepository(
    private val context: Context
) : SettingsRepository {

    private val timeoutKey = longPreferencesKey("timeout_millis")

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun observeTimeoutMillis(): Flow<Long> =
        context.userSettingsDataStore.data.map { prefs ->
            prefs[timeoutKey] ?: DEFAULT_TIMEOUT_MS
        }

    override suspend fun setTimeoutMillis(value: Long) {
        val bounded = value.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        context.userSettingsDataStore.edit { prefs ->
            prefs[timeoutKey] = bounded
        }
    }

    override suspend fun setApiKey(value: String) {
        securePrefs.edit().putString(API_KEY_PREF, value.trim()).apply()
    }

    override suspend fun getApiKey(): String? =
        securePrefs.getString(API_KEY_PREF, null)?.takeIf { it.isNotBlank() }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MIN_TIMEOUT_MS = 5_000L
        const val MAX_TIMEOUT_MS = 120_000L
        private const val API_KEY_PREF = "gemini_api_key"
    }
}
