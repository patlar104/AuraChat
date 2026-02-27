package com.personal.aurachat.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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

    private val securePrefs: SharedPreferences? by lazy {
        createEncryptedPrefsWithRecovery()
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
        securePrefs?.edit()?.putString(API_KEY_PREF, value.trim())?.apply()
    }

    override suspend fun getApiKey(): String? =
        securePrefs?.getString(API_KEY_PREF, null)?.takeIf { it.isNotBlank() }

    private fun createEncryptedPrefsWithRecovery(): SharedPreferences? {
        return runCatching { createEncryptedPrefs() }
            .recoverCatching { firstError ->
                Log.w(TAG, "Encrypted prefs read failed; clearing secure prefs and recreating.", firstError)
                context.deleteSharedPreferences(SECURE_PREFS_NAME)
                createEncryptedPrefs()
            }
            .getOrElse { fatalError ->
                Log.e(TAG, "Unable to initialize encrypted prefs; API key storage disabled.", fatalError)
                null
            }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MIN_TIMEOUT_MS = 5_000L
        const val MAX_TIMEOUT_MS = 120_000L
        private const val TAG = "DefaultSettingsRepo"
        private const val SECURE_PREFS_NAME = "secure_settings"
        private const val API_KEY_PREF = "gemini_api_key"
    }
}
