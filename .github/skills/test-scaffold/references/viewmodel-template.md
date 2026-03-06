# ViewModel Test Template

## HomeViewModel

```kotlin
package com.personal.aurachat.presentation.home

import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.model.SendMessageResult
import com.personal.aurachat.domain.repository.ConversationRepository
import com.personal.aurachat.domain.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `uiState reflects conversations from repository`() = runTest {
        val summaries = listOf(
            ConversationSummary(id = 1L, title = "Chat A", latestMessagePreview = "Hi", updatedAtEpochMs = 0L)
        )
        val repository = FakeConversationRepository(summaries = summaries)
        val viewModel = HomeViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        assertEquals(summaries, viewModel.uiState.value.conversations)
        job.cancel()
    }

    @Test
    fun `renameConversation delegates to repository`() = runTest {
        val repository = FakeConversationRepository()
        val viewModel = HomeViewModel(repository)

        viewModel.renameConversation(1L, "New Title")

        assertEquals(1L, repository.lastRenamedId)
        assertEquals("New Title", repository.lastRenamedTitle)
    }
}

// ── Fakes ──────────────────────────────────────────────────────────────────

private class FakeConversationRepository(
    private val summaries: List<ConversationSummary> = emptyList()
) : ConversationRepository {
    var lastRenamedId: Long? = null
    var lastRenamedTitle: String? = null

    override fun observeConversationSummaries(): Flow<List<ConversationSummary>> = flowOf(summaries)
    override fun observeMessages(conversationId: Long, limit: Int): Flow<List<ChatMessage>> = flowOf(emptyList())
    override fun observeConversationTitle(conversationId: Long): Flow<String?> = flowOf("Test chat")
    override suspend fun createConversationIfNeeded(existingId: Long?): Long = existingId ?: 1L
    override suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult =
        SendMessageResult.Success(conversationId)
    override suspend fun retryLastFailedAssistantReply(conversationId: Long): SendMessageResult =
        SendMessageResult.Success(conversationId)
    override suspend fun renameConversation(conversationId: Long, title: String) {
        lastRenamedId = conversationId
        lastRenamedTitle = title
    }
}
```

---

## SettingsViewModel

```kotlin
package com.personal.aurachat.presentation.settings

import com.personal.aurachat.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `saveSettings with blank key sets error message`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("")
        viewModel.saveSettings()

        val job = launch { viewModel.uiState.collect {} }
        assertEquals("API key is required.", viewModel.uiState.value.saveMessage)
        job.cancel()
    }

    @Test
    fun `saveSettings persists trimmed key`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("  my-api-key  ")
        viewModel.saveSettings()

        assertEquals("my-api-key", repository.savedApiKey)
    }

    @Test
    fun `clearSaveMessage nulls the message`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)
        viewModel.onApiKeyChanged("")
        viewModel.saveSettings()   // triggers error message

        viewModel.clearSaveMessage()

        val job = launch { viewModel.uiState.collect {} }
        assertNull(viewModel.uiState.value.saveMessage)
        job.cancel()
    }

    @Test
    fun `onTimeoutSelected updates repository`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTimeoutSelected(60_000L)

        assertEquals(60_000L, repository.timeoutFlow.value)
    }
}

// ── Fake ───────────────────────────────────────────────────────────────────

private class FakeSettingsRepository(
    private var apiKey: String? = null,
    initialTimeout: Long = 30_000L
) : SettingsRepository {
    val timeoutFlow = MutableStateFlow(initialTimeout)
    var savedApiKey: String? = null

    override fun observeTimeoutMillis(): Flow<Long> = timeoutFlow
    override suspend fun setTimeoutMillis(value: Long) { timeoutFlow.value = value }
    override suspend fun setApiKey(value: String) { savedApiKey = value; apiKey = value }
    override suspend fun getApiKey(): String? = apiKey
}
```
