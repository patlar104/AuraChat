package com.personal.aurachat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuraChatDao {
    @Query(
        """
        SELECT c.id,
               c.title,
               COALESCE((
                   SELECT m.content
                   FROM messages m
                   WHERE m.conversationId = c.id
                   ORDER BY m.createdAtEpochMs DESC, m.id DESC
                   LIMIT 1
               ), '') AS latestMessagePreview,
               c.updatedAtEpochMs
        FROM conversations c
        ORDER BY c.updatedAtEpochMs DESC
        """
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummaryRow>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMs ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT title FROM conversations WHERE id = :conversationId LIMIT 1")
    fun observeConversationTitle(conversationId: Long): Flow<String?>

    @Insert
    suspend fun insertConversation(entity: ConversationEntity): Long

    @Insert
    suspend fun insertMessage(entity: MessageEntity): Long

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun updateConversationTitle(conversationId: Long, title: String)

    @Query("UPDATE conversations SET updatedAtEpochMs = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationTimestamp(conversationId: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = 'USER'")
    suspend fun countUserMessages(conversationId: Long): Int

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
          AND NOT (role = 'ASSISTANT' AND deliveryState = 'FAILED')
        ORDER BY createdAtEpochMs ASC, id ASC
        """
    )
    suspend fun getMessagesForRequest(conversationId: Long): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
          AND role = 'ASSISTANT'
          AND deliveryState = 'FAILED'
        ORDER BY createdAtEpochMs DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLastFailedAssistantMessage(conversationId: Long): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)
}
