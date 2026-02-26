package com.personal.aurachat.data.repository

import androidx.room.withTransaction
import com.personal.aurachat.core.time.TimeProvider
import com.personal.aurachat.data.local.AuraChatDao
import com.personal.aurachat.data.local.AuraChatDatabase
import com.personal.aurachat.data.local.ConversationEntity
import com.personal.aurachat.data.local.MessageEntity
import com.personal.aurachat.domain.model.AiErrorType
import com.personal.aurachat.domain.model.AiMessage
import com.personal.aurachat.domain.model.AiRequest
import com.personal.aurachat.domain.model.AiResult
import com.personal.aurachat.domain.model.AiRole
import com.personal.aurachat.domain.model.AiService
import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.model.MessageDeliveryState
import com.personal.aurachat.domain.model.SendMessageResult
import com.personal.aurachat.domain.repository.ConversationRepository
import com.personal.aurachat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DefaultConversationRepository(
    private val database: AuraChatDatabase,
    private val dao: AuraChatDao,
    private val aiService: AiService,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider
) : ConversationRepository {

    override fun observeConversationSummaries(): Flow<List<ConversationSummary>> =
        dao.observeConversationSummaries().map { rows ->
            rows.map { row ->
                ConversationSummary(
                    id = row.id,
                    title = row.title,
                    latestMessagePreview = row.latestMessagePreview,
                    updatedAtEpochMs = row.updatedAtEpochMs
                )
            }
        }

    override fun observeMessages(conversationId: Long, limit: Int): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId, limit).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    role = entity.role.toAiRole(),
                    content = entity.content,
                    createdAtEpochMs = entity.createdAtEpochMs,
                    deliveryState = entity.deliveryState.toDeliveryState(),
                    errorType = entity.errorType?.toAiErrorType()
                )
            }
        }

    override fun observeConversationTitle(conversationId: Long): Flow<String?> =
        dao.observeConversationTitle(conversationId)

    override suspend fun createConversationIfNeeded(existingId: Long?): Long {
        if (existingId != null && existingId > 0) return existingId
        val now = timeProvider.nowEpochMillis()
        return dao.insertConversation(
            ConversationEntity(
                title = DEFAULT_CONVERSATION_TITLE,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
    }

    override suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return SendMessageResult.Failure(
                conversationId = conversationId,
                errorType = AiErrorType.UNKNOWN,
                message = "Cannot send an empty message."
            )
        }

        val now = timeProvider.nowEpochMillis()
        database.withTransaction {
            dao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = AiRole.USER.name,
                    content = normalized,
                    createdAtEpochMs = now,
                    deliveryState = MessageDeliveryState.SENT.name,
                    errorType = null
                )
            )

            if (dao.countUserMessages(conversationId) == 1) {
                dao.updateConversationTitle(conversationId, generateTitleFromFirstMessage(normalized))
            }

            dao.updateConversationTimestamp(conversationId, now)
        }

        return requestAssistantReply(conversationId)
    }

    override suspend fun retryLastFailedAssistantReply(conversationId: Long): SendMessageResult {
        val failedMessage = dao.getLastFailedAssistantMessage(conversationId)
            ?: return SendMessageResult.Failure(
                conversationId = conversationId,
                errorType = AiErrorType.UNKNOWN,
                message = "No failed response available to retry."
            )

        database.withTransaction {
            dao.deleteMessageById(failedMessage.id)
            dao.updateConversationTimestamp(conversationId, timeProvider.nowEpochMillis())
        }

        return requestAssistantReply(conversationId)
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        val normalized = title.trim()
        if (normalized.isBlank()) return
        dao.updateConversationTitle(conversationId, normalized)
        dao.updateConversationTimestamp(conversationId, timeProvider.nowEpochMillis())
    }

    private suspend fun requestAssistantReply(conversationId: Long): SendMessageResult {
        val timeoutMillis = settingsRepository.observeTimeoutMillis().first()
        val requestMessages = dao.getMessagesForRequest(conversationId).map { entity ->
            AiMessage(role = entity.role.toAiRole(), text = entity.content)
        }

        var messageId: Long? = null
        var fullContent = ""
        var lastError: AiResult.Error? = null

        try {
            aiService.streamReply(
                AiRequest(
                    messages = requestMessages,
                    model = DEFAULT_MODEL,
                    timeoutMillis = timeoutMillis
                )
            ).collect { result ->
                when (result) {
                    is AiResult.Success -> {
                        if (messageId == null) {
                            messageId = persistAssistantMessage(
                                conversationId = conversationId,
                                content = "",
                                state = MessageDeliveryState.SENT,
                                errorType = null
                            )
                        }
                        fullContent += result.value
                        dao.updateMessageContent(messageId!!, fullContent)
                    }
                    is AiResult.Error -> {
                        lastError = result
                    }
                }
            }
        } catch (e: Exception) {
            lastError = AiResult.Error(AiErrorType.UNKNOWN, e.message)
        }

        if (lastError != null && fullContent.isBlank()) {
            val finalMessageId = messageId ?: persistAssistantMessage(
                conversationId = conversationId,
                content = "",
                state = MessageDeliveryState.FAILED,
                errorType = lastError!!.type
            )
            dao.updateMessage(
                messageId = finalMessageId,
                content = lastError!!.message ?: lastError!!.type.toFriendlyMessage(),
                state = MessageDeliveryState.FAILED.name,
                errorType = lastError!!.type.name
            )
            return SendMessageResult.Failure(conversationId, lastError!!.type, lastError!!.message)
        } else if (fullContent.isBlank() && lastError == null) {
            val emptyError = AiErrorType.EMPTY_RESPONSE
            val finalMessageId = messageId ?: persistAssistantMessage(
                conversationId = conversationId,
                content = emptyError.toFriendlyMessage(),
                state = MessageDeliveryState.FAILED,
                errorType = emptyError
            )
            return SendMessageResult.Failure(conversationId, emptyError)
        }

        return SendMessageResult.Success(conversationId)
    }

    private suspend fun persistAssistantMessage(
        conversationId: Long,
        content: String,
        state: MessageDeliveryState,
        errorType: AiErrorType?
    ): Long {
        val now = timeProvider.nowEpochMillis()
        return database.withTransaction {
            val id = dao.insertMessage(
                MessageEntity(
                    conversationId = conversationId,
                    role = AiRole.ASSISTANT.name,
                    content = content,
                    createdAtEpochMs = now,
                    deliveryState = state.name,
                    errorType = errorType?.name
                )
            )
            dao.updateConversationTimestamp(conversationId, now)
            id
        }
    }

    private fun generateTitleFromFirstMessage(message: String): String {
        val oneLine = message.lineSequence().firstOrNull().orEmpty().trim().replace("\\s+".toRegex(), " ")
        return oneLine.take(MAX_TITLE_LENGTH).ifBlank { DEFAULT_CONVERSATION_TITLE }
    }

    private fun String.toAiRole(): AiRole =
        when (uppercase()) {
            AiRole.USER.name -> AiRole.USER
            else -> AiRole.ASSISTANT
        }

    private fun String.toDeliveryState(): MessageDeliveryState =
        when (uppercase()) {
            MessageDeliveryState.FAILED.name -> MessageDeliveryState.FAILED
            else -> MessageDeliveryState.SENT
        }

    private fun String.toAiErrorType(): AiErrorType =
        runCatching { AiErrorType.valueOf(this) }.getOrElse { AiErrorType.UNKNOWN }

    private fun AiErrorType.toFriendlyMessage(): String = when (this) {
        AiErrorType.OFFLINE -> "No internet connection. Connect and retry."
        AiErrorType.TIMEOUT -> "The request timed out. Retry when ready."
        AiErrorType.NETWORK -> "Network error while contacting AI service."
        AiErrorType.EMPTY_RESPONSE -> "AI returned no text. Please retry."
        AiErrorType.MALFORMED_RESPONSE -> "AI returned an unreadable response."
        AiErrorType.UNAUTHORIZED -> "Invalid or missing API key in Settings."
        AiErrorType.UNKNOWN -> "Unexpected error. Please retry."
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-1.5-flash"
        const val DEFAULT_CONVERSATION_TITLE = "New chat"
        private const val MAX_TITLE_LENGTH = 48
    }
}
