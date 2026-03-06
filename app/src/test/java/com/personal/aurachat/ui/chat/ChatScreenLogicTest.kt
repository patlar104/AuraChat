package com.personal.aurachat.ui.chat

import com.personal.aurachat.domain.model.AiErrorType
import com.personal.aurachat.domain.model.AiRole
import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.MessageDeliveryState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatScreenLogicTest {

    @Test
    fun `retry is offered only when latest message is a failed assistant reply`() {
        val messages = listOf(
            userMessage(id = 1L, content = "Hi"),
            failedAssistantMessage(id = 2L, content = "Partial reply")
        )

        assertEquals(1, retryableFailedMessageIndex(messages))
    }

    @Test
    fun `retry is not offered for stale failed replies once newer messages exist`() {
        val messages = listOf(
            userMessage(id = 1L, content = "Hi"),
            failedAssistantMessage(id = 2L, content = "Older failed reply"),
            userMessage(id = 3L, content = "Follow-up question"),
            assistantMessage(id = 4L, content = "Newer successful reply")
        )

        assertEquals(-1, retryableFailedMessageIndex(messages))
    }

    private fun userMessage(id: Long, content: String) = ChatMessage(
        id = id,
        conversationId = 9L,
        role = AiRole.USER,
        content = content,
        createdAtEpochMs = id,
        deliveryState = MessageDeliveryState.SENT,
        errorType = null
    )

    private fun assistantMessage(id: Long, content: String) = ChatMessage(
        id = id,
        conversationId = 9L,
        role = AiRole.ASSISTANT,
        content = content,
        createdAtEpochMs = id,
        deliveryState = MessageDeliveryState.SENT,
        errorType = null
    )

    private fun failedAssistantMessage(id: Long, content: String) = ChatMessage(
        id = id,
        conversationId = 9L,
        role = AiRole.ASSISTANT,
        content = content,
        createdAtEpochMs = id,
        deliveryState = MessageDeliveryState.FAILED,
        errorType = AiErrorType.NETWORK
    )
}
