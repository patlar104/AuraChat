package com.personal.aurachat.domain.repository

import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.model.SendMessageResult
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversationSummaries(): Flow<List<ConversationSummary>>
    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>
    fun observeConversationTitle(conversationId: Long): Flow<String?>

    suspend fun createConversationIfNeeded(existingId: Long?): Long
    suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult
    suspend fun retryLastFailedAssistantReply(conversationId: Long): SendMessageResult
    suspend fun renameConversation(conversationId: Long, title: String)
}
