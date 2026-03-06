package com.personal.aurachat.data.repository

import android.util.Log
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
import kotlinx.coroutines.CancellationException
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
                    latestMessagePreview = row.latestMessagePreview.toDisplayedMessageContent(),
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
                    content = entity.content.toDisplayedMessageContent(),
                    createdAtEpochMs = entity.createdAtEpochMs,
                    deliveryState = entity.deliveryState.toDeliveryState(),
                    errorType = entity.errorType?.toAiErrorType()
                )
            }
        }

    override fun observeConversationTitle(conversationId: Long): Flow<String?> =
        dao.observeConversationTitle(conversationId)

    override suspend fun createConversationIfNeeded(existingId: Long?): Long {
        if (existingId != null && existingId > 0) {
            Log.d(TAG, "Using existing conversation id=$existingId")
            return existingId
        }
        val now = timeProvider.nowEpochMillis()
        val id = dao.insertConversation(
            ConversationEntity(
                title = DEFAULT_CONVERSATION_TITLE,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        Log.i(TAG, "Created conversation id=$id")
        return id
    }

    override suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            Log.w(TAG, "sendUserMessage: rejected empty message conversationId=$conversationId")
            return SendMessageResult.Failure(
                conversationId = conversationId,
                errorType = AiErrorType.UNKNOWN,
                message = "Cannot send an empty message."
            )
        }

        Log.d(TAG, "sendUserMessage: conversationId=$conversationId chars=${normalized.length}")
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
            ?: run {
                Log.w(TAG, "retryLastFailed: no failed message found conversationId=$conversationId")
                return SendMessageResult.Failure(
                    conversationId = conversationId,
                    errorType = AiErrorType.UNKNOWN,
                    message = "No failed response available to retry."
                )
            }
        val latestMessage = dao.getLatestMessage(conversationId)
        if (latestMessage?.id != failedMessage.id) {
            Log.w(TAG, "retryLastFailed: stale failed message messageId=${failedMessage.id} latest=${latestMessage?.id}")
            return SendMessageResult.Failure(
                conversationId = conversationId,
                errorType = AiErrorType.UNKNOWN,
                message = "Only the latest failed response can be retried."
            )
        }

        Log.i(TAG, "retryLastFailed: deleting messageId=${failedMessage.id} conversationId=$conversationId")
        database.withTransaction {
            dao.deleteMessageById(failedMessage.id)
            dao.updateConversationTimestamp(conversationId, timeProvider.nowEpochMillis())
        }

        return requestAssistantReply(conversationId)
    }

    override suspend fun renameConversation(conversationId: Long, title: String) {
        val normalized = title.trim()
        if (normalized.isBlank()) return
        Log.d(TAG, "renameConversation: conversationId=$conversationId")
        dao.updateConversationTitle(conversationId, normalized)
        dao.updateConversationTimestamp(conversationId, timeProvider.nowEpochMillis())
    }

    private suspend fun requestAssistantReply(conversationId: Long): SendMessageResult {
        val timeoutMillis = settingsRepository.observeTimeoutMillis().first()
        val requestMessages = dao.getMessagesForConversation(conversationId)
            .filter(::shouldIncludeMessageInRequest)
            .map { entity ->
                AiMessage(role = entity.role.toAiRole(), text = entity.content.toDisplayedMessageContent())
            }
        Log.d(TAG, "requestAssistantReply: conversationId=$conversationId historySize=${requestMessages.size} timeout=${timeoutMillis}ms")

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
                            Log.d(TAG, "requestAssistantReply: created assistant message id=$messageId")
                        }
                        fullContent += result.value
                        dao.updateMessageContent(messageId!!, fullContent)
                    }
                    is AiResult.Error -> {
                        Log.w(TAG, "requestAssistantReply: stream error type=${result.type}")
                        lastError = result
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "requestAssistantReply: exception ${e::class.simpleName}")
            lastError = AiResult.Error(AiErrorType.UNKNOWN, e.message)
        }

        if (lastError != null) {
            // Any stream error (with or without partial content) is a failure.
            Log.w(TAG, "requestAssistantReply: failed conversationId=$conversationId error=${lastError!!.type} partialContent=${fullContent.isNotBlank()}")
            val finalMessageId = messageId ?: persistAssistantMessage(
                conversationId = conversationId,
                content = "",
                state = MessageDeliveryState.FAILED,
                errorType = lastError!!.type
            )
            val persistedContent = if (fullContent.isBlank()) {
                buildStoredFailureContent(lastError!!.message ?: lastError!!.type.toFriendlyMessage())
            } else {
                fullContent
            }
            dao.updateMessage(
                messageId = finalMessageId,
                content = persistedContent,
                state = MessageDeliveryState.FAILED.name,
                errorType = lastError!!.type.name
            )
            return SendMessageResult.Failure(conversationId, lastError!!.type, lastError!!.message)
        } else if (fullContent.isBlank()) {
            Log.w(TAG, "requestAssistantReply: empty response conversationId=$conversationId")
            val emptyError = AiErrorType.EMPTY_RESPONSE
            val persistedContent = buildStoredFailureContent(emptyError.toFriendlyMessage())
            val finalMessageId = messageId ?: persistAssistantMessage(
                conversationId = conversationId,
                content = persistedContent,
                state = MessageDeliveryState.FAILED,
                errorType = emptyError
            )
            dao.updateMessage(
                messageId = finalMessageId,
                content = persistedContent,
                state = MessageDeliveryState.FAILED.name,
                errorType = emptyError.name
            )
            return SendMessageResult.Failure(conversationId, emptyError)
        }

        Log.i(TAG, "requestAssistantReply: success conversationId=$conversationId messageId=$messageId")
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
        private const val TAG = "ConversationRepository"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_CONVERSATION_TITLE = "New chat"
        private const val MAX_TITLE_LENGTH = 48
    }
}

internal const val SYSTEM_FAILURE_PREFIX = "[[system-error]] "

internal fun buildStoredFailureContent(displayMessage: String): String =
    "$SYSTEM_FAILURE_PREFIX$displayMessage"

internal fun String.toDisplayedMessageContent(): String =
    removePrefix(SYSTEM_FAILURE_PREFIX)

internal fun shouldIncludeMessageInRequest(message: MessageEntity): Boolean =
    !(message.role.equals(AiRole.ASSISTANT.name, ignoreCase = true) &&
        message.deliveryState.equals(MessageDeliveryState.FAILED.name, ignoreCase = true) &&
        message.content.startsWith(SYSTEM_FAILURE_PREFIX))
