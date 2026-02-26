package com.personal.aurachat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.aurachat.core.network.NetworkMonitor
import com.personal.aurachat.domain.model.ChatMessage
import com.personal.aurachat.domain.model.SendMessageResult
import com.personal.aurachat.domain.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

data class ChatUiState(
    val conversationId: Long? = null,
    val title: String = DEFAULT_CHAT_TITLE,
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val isOnline: Boolean = true,
    val offlineNotice: String? = null
)

private const val DEFAULT_CHAT_TITLE = "New chat"

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    networkMonitor: NetworkMonitor,
    initialConversationId: Long?
) : ViewModel() {

    private val sendMutex = Mutex()

    private val conversationId = MutableStateFlow(initialConversationId?.takeIf { it > 0 })
    private val isSending = MutableStateFlow(false)
    private val offlineNotice = MutableStateFlow<String?>(null)

    private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = networkMonitor.isCurrentlyOnline()
    )

    private val titleFlow = conversationId.flatMapLatest { id ->
        if (id == null) {
            flowOf(DEFAULT_CHAT_TITLE)
        } else {
            conversationRepository.observeConversationTitle(id)
                .map { title -> title?.takeIf { it.isNotBlank() } ?: DEFAULT_CHAT_TITLE }
        }
    }

    private val messagesFlow = conversationId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            conversationRepository.observeMessages(id)
        }
    }

    private val combinedConversationData = combine(
        conversationId,
        titleFlow,
        messagesFlow
    ) { id, title, messages -> Triple(id, title, messages) }

    val uiState: StateFlow<ChatUiState> = combine(
        combinedConversationData,
        isSending,
        isOnline,
        offlineNotice
    ) { (id, title, messages), sending, online, offlineText ->
        ChatUiState(
            conversationId = id,
            title = title,
            messages = messages,
            isSending = sending,
            isOnline = online,
            offlineNotice = offlineText
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(
            conversationId = initialConversationId?.takeIf { it > 0 },
            title = if (initialConversationId != null && initialConversationId > 0) "..." else DEFAULT_CHAT_TITLE
        )
    )

    fun requestSendMessage(
        text: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            onResult(sendMessage(text))
        }
    }

    suspend fun sendMessage(text: String): Boolean {
        if (!sendMutex.tryLock()) return false
        return try {
            val normalized = text.trim()
            if (normalized.isBlank() || isSending.value) return false

            if (!isOnline.value) {
                offlineNotice.value = "Internet connection is required for AI responses."
                return false
            }

            isSending.value = true
            offlineNotice.value = null

            try {
                val id = conversationRepository.createConversationIfNeeded(conversationId.value)
                conversationId.value = id

                val result = conversationRepository.sendUserMessage(id, normalized)
                result is SendMessageResult.Success
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                offlineNotice.value = "Unable to send message right now. Please retry."
                false
            } finally {
                isSending.value = false
            }
        } finally {
            sendMutex.unlock()
        }
    }

    fun requestRetryLastFailed() {
        viewModelScope.launch {
            retryLastFailed()
        }
    }

    suspend fun retryLastFailed(): Boolean {
        if (!sendMutex.tryLock()) return false
        return try {
            val id = conversationId.value ?: return false
            if (isSending.value) return false
            if (!isOnline.value) {
                offlineNotice.value = "Internet connection is required for retry."
                return false
            }

            isSending.value = true
            try {
                val result = conversationRepository.retryLastFailedAssistantReply(id)
                result is SendMessageResult.Success
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                offlineNotice.value = "Unable to retry right now. Please try again."
                false
            } finally {
                isSending.value = false
            }
        } finally {
            sendMutex.unlock()
        }
    }

    fun clearOfflineNotice() {
        offlineNotice.update { null }
    }

    companion object {
        fun factory(
            conversationRepository: ConversationRepository,
            networkMonitor: NetworkMonitor,
            initialConversationId: Long?
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(
                        conversationRepository = conversationRepository,
                        networkMonitor = networkMonitor,
                        initialConversationId = initialConversationId
                    ) as T
                }
            }
    }
}
