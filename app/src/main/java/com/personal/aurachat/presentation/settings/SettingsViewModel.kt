package com.personal.aurachat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.aurachat.data.repository.DefaultSettingsRepository
import com.personal.aurachat.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val timeoutMillis: Long = DefaultSettingsRepository.DEFAULT_TIMEOUT_MS,
    val isSaving: Boolean = false,
    val saveMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val apiKey = MutableStateFlow("")
    private val isSaving = MutableStateFlow(false)
    private val saveMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        apiKey,
        settingsRepository.observeTimeoutMillis(),
        isSaving,
        saveMessage
    ) { currentApiKey, timeoutMillis, saving, message ->
        SettingsUiState(
            apiKey = currentApiKey,
            timeoutMillis = timeoutMillis,
            isSaving = saving,
            saveMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    init {
        viewModelScope.launch {
            apiKey.value = settingsRepository.getApiKey().orEmpty()
        }
    }

    fun onApiKeyChanged(value: String) {
        apiKey.value = value
    }

    fun onTimeoutSelected(timeoutMillis: Long) {
        viewModelScope.launch {
            settingsRepository.setTimeoutMillis(timeoutMillis)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val key = apiKey.value.trim()
            if (key.isBlank()) {
                saveMessage.value = "API key is required."
                return@launch
            }

            isSaving.value = true
            settingsRepository.setApiKey(key)
            isSaving.value = false
            saveMessage.value = "Settings saved"
        }
    }

    fun clearSaveMessage() {
        saveMessage.update { null }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(settingsRepository) as T
                }
            }
    }
}
