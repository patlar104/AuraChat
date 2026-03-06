package com.personal.aurachat.presentation.home

import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.model.SendMessageResult
import com.personal.aurachat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits empty conversations initially`() = runTest {
        val repository = FakeConversationRepository()
        val viewModel = HomeViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<ConversationSummary>(), viewModel.uiState.value.conversations)
        job.cancel()
    }

    @Test
    fun `uiState reflects conversations emitted by repository`() = runTest {
        val summaries = listOf(
            ConversationSummary(id = 1L, title = "Chat A", latestMessagePreview = "Hello", updatedAtEpochMs = 1000L),
            ConversationSummary(id = 2L, title = "Chat B", latestMessagePreview = "World", updatedAtEpochMs = 2000L)
        )
        val repository = FakeConversationRepository(summaries = summaries)
        val viewModel = HomeViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(summaries, viewModel.uiState.value.conversations)
        job.cancel()
    }

    @Test
    fun `uiState updates when repository emits new conversations`() = runTest {
        val summariesFlow = MutableStateFlow<List<ConversationSummary>>(emptyList())
        val repository = FakeConversationRepository(summariesFlow = summariesFlow)
        val viewModel = HomeViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<ConversationSummary>(), viewModel.uiState.value.conversations)

        summariesFlow.value = listOf(
            ConversationSummary(id = 3L, title = "New Chat", latestMessagePreview = "", updatedAtEpochMs = 3000L)
        )
        advanceUntilIdle()
        assertEquals(summariesFlow.value, viewModel.uiState.value.conversations)
        job.cancel()
    }

    @Test
    fun `renameConversation delegates to repository with correct arguments`() = runTest {
        val repository = FakeConversationRepository()
        val viewModel = HomeViewModel(repository)

        viewModel.renameConversation(42L, "Renamed Title")

        assertEquals(42L, repository.lastRenamedId)
        assertEquals("Renamed Title", repository.lastRenamedTitle)
    }

    @Test
    fun `renameConversation can be called multiple times`() = runTest {
        val repository = FakeConversationRepository()
        val viewModel = HomeViewModel(repository)

        viewModel.renameConversation(1L, "First")
        viewModel.renameConversation(2L, "Second")

        assertEquals(2L, repository.lastRenamedId)
        assertEquals("Second", repository.lastRenamedTitle)
        assertEquals(2, repository.renameCalls)
    }
}

// ── Fakes ─────────────────────────────────────────────────────────────────

private class FakeConversationRepository(
        summaries: List<ConversationSummary> = emptyList(),
        private val summariesFlow: MutableStateFlow<List<ConversationSummary>> =
                MutableStateFlow(summaries)
) : ConversationRepository {

    var lastRenamedId: Long? = null
    var lastRenamedTitle: String? = null
    var renameCalls: Int = 0

    override fun observeConversationSummaries(): Flow<List<ConversationSummary>> = summariesFlow
    override fun observeMessages(conversationId: Long, limit: Int): Flow<List<ChatMessage>> =
            flowOf(emptyList())
    override fun observeConversationTitle(conversationId: Long): Flow<String?> = flowOf("Test chat")
    override suspend fun createConversationIfNeeded(existingId: Long?): Long = existingId ?: 1L
    override suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult =
            SendMessageResult.Success(conversationId)
    override suspend fun retryLastFailedAssistantReply(conversationId: Long): SendMessageResult =
            SendMessageResult.Success(conversationId)
    override suspend fun renameConversation(conversationId: Long, title: String) {
        renameCalls++
        lastRenamedId = conversationId
        lastRenamedTitle = title
    }
}
