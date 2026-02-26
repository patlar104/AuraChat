package com.personal.aurachat.presentation.chat

import com.personal.aurachat.core.network.NetworkMonitor
import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.model.SendMessageResult
import com.personal.aurachat.domain.repository.ConversationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

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
    fun `send blocks when offline`() = runTest {
        val repository = FakeConversationRepository()
        val networkMonitor = FakeNetworkMonitor(initialOnline = false)
        val viewModel = ChatViewModel(repository, networkMonitor, initialConversationId = null)

        val accepted = viewModel.sendMessage("hello")

        assertFalse(accepted)
        assertEquals(0, repository.sendCalls)
    }

    @Test
    fun `duplicate rapid sends only dispatch once`() = runTest {
        val repository = FakeConversationRepository(sendDelayMs = 1_000)
        val networkMonitor = FakeNetworkMonitor(initialOnline = true)
        val viewModel = ChatViewModel(repository, networkMonitor, initialConversationId = null)

        val first = async { viewModel.sendMessage("first") }
        val second = async { viewModel.sendMessage("second") }

        assertTrue(first.await())
        assertFalse(second.await())
        assertEquals(1, repository.sendCalls)
    }

    @Test
    fun `isSending is true during message send`() = runTest {
        val repository = FakeConversationRepository(sendDelayMs = 1_000)
        val networkMonitor = FakeNetworkMonitor(initialOnline = true)
        val viewModel = ChatViewModel(repository, networkMonitor, initialConversationId = null)

        // Start collecting to ensure StateFlow updates
        val job = launch { viewModel.uiState.collect {} }

        val sendTask = async { viewModel.sendMessage("hello") }

        // Give it a moment to start
        delay(100)
        assertTrue("ViewModel should be in isSending=true state", viewModel.uiState.value.isSending)

        sendTask.await()
        assertFalse("ViewModel should be in isSending=false state after completion", viewModel.uiState.value.isSending)
        job.cancel()
    }
}

private class FakeNetworkMonitor(initialOnline: Boolean) : NetworkMonitor {
    private val onlineFlow = MutableStateFlow(initialOnline)

    override val isOnline: Flow<Boolean> = onlineFlow

    override fun isCurrentlyOnline(): Boolean = onlineFlow.value
}

private class FakeConversationRepository(
    private val sendDelayMs: Long = 0L
) : ConversationRepository {
    var sendCalls: Int = 0

    override fun observeConversationSummaries(): Flow<List<ConversationSummary>> = flowOf(emptyList())

    override fun observeMessages(conversationId: Long, limit: Int): Flow<List<ChatMessage>> = flowOf(emptyList())

    override fun observeConversationTitle(conversationId: Long): Flow<String?> = flowOf("New chat")

    override suspend fun createConversationIfNeeded(existingId: Long?): Long = existingId ?: 1L

    override suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult {
        sendCalls += 1
        if (sendDelayMs > 0) delay(sendDelayMs)
        return SendMessageResult.Success(conversationId)
    }

    override suspend fun retryLastFailedAssistantReply(conversationId: Long): SendMessageResult =
        SendMessageResult.Success(conversationId)

    override suspend fun renameConversation(conversationId: Long, title: String) = Unit
}
