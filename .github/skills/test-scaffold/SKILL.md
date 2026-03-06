---
name: test-scaffold
description: 'Generate unit test scaffolds for untested AuraChat layers. Use when adding a new ViewModel, Repository method, or screen logic function, or when asked to increase test coverage. Produces ready-to-run test classes following the project''s coroutine, fake-object, and assertion patterns.'
argument-hint: 'Class or layer to test (e.g., "HomeViewModel", "DefaultSettingsRepository", "SettingsViewModel")'
---

# Test Scaffold — AuraChat

## When to Use
- New ViewModel or repository method needs tests
- Asked to add test coverage for an existing class
- Adding a new screen logic function (pure functions in `ui/` files)

---

## Step 1 — Identify the Layer

| Target | Test location | Base pattern |
|---|---|---|
| `presentation/*ViewModel` | `src/test/.../presentation/<feature>/` | [viewmodel-template.md](./references/viewmodel-template.md) |
| `data/repository/Default*` | `src/test/.../data/repository/` | [repository-template.md](./references/repository-template.md) |
| Pure functions in `ui/` | `src/test/.../ui/<feature>/` | Plain `@Test` — no coroutines needed |

---

## Step 2 — Set Up the Test Class

### Coroutine boilerplate (required for ViewModels and Repository suspend functions)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FooViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

**Key imports:**
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
```

---

## Step 3 — Create Fakes (not Mocks)

AuraChat uses **hand-written fake classes** — no Mockito or MockK. Pattern from `ChatViewModelTest`:

### FakeConversationRepository

```kotlin
private class FakeConversationRepository(
    private val sendDelayMs: Long = 0L,
    private val summaries: List<ConversationSummary> = emptyList()
) : ConversationRepository {
    var sendCalls: Int = 0
    var lastSentText: String? = null

    override fun observeConversationSummaries(): Flow<List<ConversationSummary>> = flowOf(summaries)
    override fun observeMessages(conversationId: Long, limit: Int): Flow<List<ChatMessage>> = flowOf(emptyList())
    override fun observeConversationTitle(conversationId: Long): Flow<String?> = flowOf("Test chat")
    override suspend fun createConversationIfNeeded(existingId: Long?): Long = existingId ?: 1L
    override suspend fun sendUserMessage(conversationId: Long, text: String): SendMessageResult {
        sendCalls++
        lastSentText = text
        if (sendDelayMs > 0) delay(sendDelayMs)
        return SendMessageResult.Success(conversationId)
    }
    override suspend fun retryLastFailedAssistantReply(conversationId: Long): SendMessageResult =
        SendMessageResult.Success(conversationId)
    override suspend fun renameConversation(conversationId: Long, title: String) = Unit
}
```

### FakeNetworkMonitor

```kotlin
private class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    val onlineFlow = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = onlineFlow
    override fun isCurrentlyOnline(): Boolean = onlineFlow.value
}
```

### FakeSettingsRepository

```kotlin
private class FakeSettingsRepository(
    private var apiKey: String? = null,
    initialTimeout: Long = 30_000L
) : SettingsRepository {
    private val timeoutFlow = MutableStateFlow(initialTimeout)
    override fun observeTimeoutMillis(): Flow<Long> = timeoutFlow
    override suspend fun setTimeoutMillis(value: Long) { timeoutFlow.value = value }
    override suspend fun setApiKey(value: String) { apiKey = value }
    override suspend fun getApiKey(): String? = apiKey
}
```

---

## Step 4 — Write Tests

Follow the backtick naming convention used throughout the project:

```kotlin
@Test
fun `descriptive behavior being tested`() = runTest { ... }
```

### Collecting StateFlow in tests

```kotlin
// Collect to trigger stateIn(...) and allow upstream flows to emit
val job = launch { viewModel.uiState.collect {} }
// ... assertions ...
job.cancel()
```

Or use `turbine` if added to dependencies (not currently present — use `launch/collect` pattern instead).

---

## Step 5 — Run and Verify

```bash
./gradlew testDebugUnitTest --tests "com.personal.aurachat.presentation.home.HomeViewModelTest"
./gradlew test    # all unit tests
```

---

## Coverage Gaps (as of v1)

These classes have **zero unit tests** — prioritize in order:

1. `HomeViewModel` — `renameConversation`, `uiState` flow from repository
2. `SettingsViewModel` — `saveSettings` (blank key guard), `onTimeoutSelected`, `clearSaveMessage`
3. `DefaultSettingsRepository` — `setApiKey`/`getApiKey` round-trip, timeout persistence
4. `DefaultConversationRepository` — streaming chunks persisted to Room, error paths, retry logic

See [viewmodel-template.md](./references/viewmodel-template.md) and [repository-template.md](./references/repository-template.md) for ready-to-expand stubs.
