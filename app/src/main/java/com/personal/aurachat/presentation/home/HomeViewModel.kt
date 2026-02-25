package com.personal.aurachat.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val conversations: List<ConversationSummary> = emptyList()
)

class HomeViewModel(
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        conversationRepository.observeConversationSummaries()
            .map { conversations -> HomeUiState(conversations = conversations) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun renameConversation(conversationId: Long, title: String) {
        viewModelScope.launch {
            conversationRepository.renameConversation(conversationId, title)
        }
    }

    companion object {
        fun factory(conversationRepository: ConversationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(conversationRepository) as T
                }
            }
    }
}
