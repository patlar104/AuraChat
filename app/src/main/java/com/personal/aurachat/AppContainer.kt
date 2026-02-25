package com.personal.aurachat

import android.content.Context
import androidx.room.Room
import com.personal.aurachat.core.network.ConnectivityNetworkMonitor
import com.personal.aurachat.core.network.NetworkMonitor
import com.personal.aurachat.core.time.SystemTimeProvider
import com.personal.aurachat.core.time.TimeProvider
import com.personal.aurachat.data.local.AuraChatDatabase
import com.personal.aurachat.data.remote.GeminiAiService
import com.personal.aurachat.data.repository.DefaultConversationRepository
import com.personal.aurachat.data.repository.DefaultSettingsRepository
import com.personal.aurachat.domain.model.AiService
import com.personal.aurachat.domain.repository.ConversationRepository
import com.personal.aurachat.domain.repository.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

interface AppContainer {
    val conversationRepository: ConversationRepository
    val settingsRepository: SettingsRepository
    val networkMonitor: NetworkMonitor
}

class DefaultAppContainer(
    context: Context
) : AppContainer {

    private val appContext = context.applicationContext

    private val database: AuraChatDatabase by lazy {
        Room.databaseBuilder(appContext, AuraChatDatabase::class.java, "aurachat.db").build()
    }

    private val timeProvider: TimeProvider = SystemTimeProvider()

    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(appContext)
    }

    override val networkMonitor: NetworkMonitor by lazy {
        ConnectivityNetworkMonitor(appContext)
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val aiService: AiService by lazy {
        GeminiAiService(
            httpClient = okHttpClient,
            apiKeyProvider = { settingsRepository.getApiKey() }
        )
    }

    override val conversationRepository: ConversationRepository by lazy {
        DefaultConversationRepository(
            database = database,
            dao = database.auraChatDao(),
            aiService = aiService,
            settingsRepository = settingsRepository,
            timeProvider = timeProvider
        )
    }
}
