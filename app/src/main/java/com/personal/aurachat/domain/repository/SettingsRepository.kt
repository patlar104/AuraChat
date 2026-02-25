package com.personal.aurachat.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTimeoutMillis(): Flow<Long>
    suspend fun setTimeoutMillis(value: Long)

    suspend fun setApiKey(value: String)
    suspend fun getApiKey(): String?
}
