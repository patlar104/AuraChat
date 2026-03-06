package com.personal.aurachat.data.repository

import com.personal.aurachat.data.local.MessageEntity
import com.personal.aurachat.domain.model.AiRole
import com.personal.aurachat.domain.model.MessageDeliveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultConversationRepositoryTest {

    @Test
    fun `request context keeps partial failed assistant replies`() {
        val message = MessageEntity(
            id = 1L,
            conversationId = 7L,
            role = AiRole.ASSISTANT.name,
            content = "Partial reply that the user already saw",
            createdAtEpochMs = 123L,
            deliveryState = MessageDeliveryState.FAILED.name,
            errorType = "NETWORK"
        )

        assertTrue(shouldIncludeMessageInRequest(message))
    }

    @Test
    fun `request context excludes stored failure placeholders`() {
        val message = MessageEntity(
            id = 1L,
            conversationId = 7L,
            role = AiRole.ASSISTANT.name,
            content = buildStoredFailureContent("Network error while contacting AI service."),
            createdAtEpochMs = 123L,
            deliveryState = MessageDeliveryState.FAILED.name,
            errorType = "NETWORK"
        )

        assertFalse(shouldIncludeMessageInRequest(message))
    }

    @Test
    fun `display content strips internal failure prefix`() {
        val stored = buildStoredFailureContent("Invalid or missing API key in Settings.")

        assertEquals(
            "Invalid or missing API key in Settings.",
            stored.toDisplayedMessageContent()
        )
    }
}
