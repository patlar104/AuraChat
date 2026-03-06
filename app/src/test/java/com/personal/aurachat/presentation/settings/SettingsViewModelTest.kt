package com.personal.aurachat.presentation.settings

import com.personal.aurachat.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState loads saved api key on init`() = runTest {
        val repository = FakeSettingsRepository(apiKey = "saved-key-123")
        val viewModel = SettingsViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("saved-key-123", viewModel.uiState.value.apiKey)
        job.cancel()
    }

    @Test
    fun `uiState loads default timeout when none saved`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(30_000L, viewModel.uiState.value.timeoutMillis)
        job.cancel()
    }

    @Test
    fun `onApiKeyChanged updates apiKey in uiState`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("new-key")

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("new-key", viewModel.uiState.value.apiKey)
        job.cancel()
    }

    @Test
    fun `saveSettings with blank key sets error message without saving`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("   ")
        viewModel.saveSettings()

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("API key is required.", viewModel.uiState.value.saveMessage)
        assertNull(repository.savedApiKey)
        job.cancel()
    }

    @Test
    fun `saveSettings persists trimmed api key`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("  my-api-key  ")
        viewModel.saveSettings()
        advanceUntilIdle()

        assertEquals("my-api-key", repository.savedApiKey)
    }

    @Test
    fun `saveSettings sets success message`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("valid-key")
        viewModel.saveSettings()

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("Settings saved", viewModel.uiState.value.saveMessage)
        job.cancel()
    }

    @Test
    fun `saveSettings does not show isSaving after completing`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("valid-key")
        viewModel.saveSettings()
        advanceUntilIdle()

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)
        job.cancel()
    }

    @Test
    fun `clearSaveMessage nulls the message`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onApiKeyChanged("")
        viewModel.saveSettings() // triggers "API key is required."

        viewModel.clearSaveMessage()
        advanceUntilIdle()

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.saveMessage)
        job.cancel()
    }

    @Test
    fun `onTimeoutSelected updates repository timeout`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.onTimeoutSelected(60_000L)
        advanceUntilIdle()

        assertEquals(60_000L, repository.timeoutFlow.value)
    }

    @Test
    fun `uiState reflects updated timeout from repository`() = runTest {
        val repository = FakeSettingsRepository(initialTimeout = 15_000L)
        val viewModel = SettingsViewModel(repository)

        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(15_000L, viewModel.uiState.value.timeoutMillis)

        repository.timeoutFlow.value = 60_000L
        advanceUntilIdle()
        assertEquals(60_000L, viewModel.uiState.value.timeoutMillis)
        job.cancel()
    }
}

// ── Fake ──────────────────────────────────────────────────────────────────

private class FakeSettingsRepository(
        private var apiKey: String? = null,
        initialTimeout: Long = 30_000L
) : SettingsRepository {

    val timeoutFlow = MutableStateFlow(initialTimeout)
    var savedApiKey: String? = null

    override fun observeTimeoutMillis(): Flow<Long> = timeoutFlow
    override suspend fun setTimeoutMillis(value: Long) {
        timeoutFlow.value = value
    }
    override suspend fun setApiKey(value: String) {
        savedApiKey = value
        apiKey = value
    }
    override suspend fun getApiKey(): String? = apiKey
}
