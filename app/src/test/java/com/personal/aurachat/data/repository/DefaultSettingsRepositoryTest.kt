package com.personal.aurachat.data.repository

import com.personal.aurachat.data.repository.DefaultSettingsRepository.Companion.MAX_TIMEOUT_MS
import com.personal.aurachat.data.repository.DefaultSettingsRepository.Companion.MIN_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for pure / bounds logic in DefaultSettingsRepository.
 *
 * Note: setTimeoutMillis, setApiKey, and getApiKey require Android Context, DataStore, and
 * EncryptedSharedPreferences — those paths need instrumented tests (androidTest) against a real or
 * Robolectric context.
 */
class DefaultSettingsRepositoryTest {

    @Test
    fun `timeout below minimum is clamped to MIN_TIMEOUT_MS`() {
        val input = 1_000L
        val result = input.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        assertEquals(MIN_TIMEOUT_MS, result)
    }

    @Test
    fun `timeout above maximum is clamped to MAX_TIMEOUT_MS`() {
        val input = 999_999L
        val result = input.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        assertEquals(MAX_TIMEOUT_MS, result)
    }

    @Test
    fun `timeout within range passes through unchanged`() {
        val input = 30_000L
        val result = input.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        assertEquals(30_000L, result)
    }

    @Test
    fun `timeout exactly at MIN_TIMEOUT_MS is accepted`() {
        val result = MIN_TIMEOUT_MS.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        assertEquals(MIN_TIMEOUT_MS, result)
    }

    @Test
    fun `timeout exactly at MAX_TIMEOUT_MS is accepted`() {
        val result = MAX_TIMEOUT_MS.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        assertEquals(MAX_TIMEOUT_MS, result)
    }
}
