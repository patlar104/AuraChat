package com.personal.aurachat.domain.model

enum class MessageDeliveryState {
    SENT,
    FAILED
}

data class ConversationSummary(
    val id: Long,
    val title: String,
    val latestMessagePreview: String,
    val updatedAtEpochMs: Long
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: AiRole,
    val content: String,
    val createdAtEpochMs: Long,
    val deliveryState: MessageDeliveryState,
    val errorType: AiErrorType?
)

sealed interface SendMessageResult {
    data class Success(val conversationId: Long) : SendMessageResult

    data class Failure(
        val conversationId: Long,
        val errorType: AiErrorType,
        val message: String?
    ) : SendMessageResult
}
