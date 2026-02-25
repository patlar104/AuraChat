package com.personal.aurachat.presentation.chat

import com.personal.aurachat.core.network.NetworkMonitor
import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.model.SendMessageResult
import com.personal.aurachat.domain.repository.ConversationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {

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

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> = flowOf(emptyList())

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
