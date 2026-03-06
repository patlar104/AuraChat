# Repository Test Template

## DefaultConversationRepository — unit-testable surface

The repository has internal helper functions that are already being tested directly
(`shouldIncludeMessageInRequest`, `buildStoredFailureContent`, `toDisplayedMessageContent`
in `DefaultConversationRepositoryTest`). Follow that same pattern for new pure functions.

For integration-style tests that need Room + `GoogleAiService`, use a fake `AiService`
(see below) and an in-memory Room database.

---

## Fake AiService

```kotlin
import com.personal.aurachat.domain.model.AiRequest
import com.personal.aurachat.domain.model.AiReply
import com.personal.aurachat.domain.model.AiResult
import com.personal.aurachat.domain.model.AiErrorType
import com.personal.aurachat.domain.model.AiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Emits a sequence of string chunks, then completes. */
class FakeAiService(
    private val chunks: List<String> = listOf("Hello", " world"),
    private val error: AiResult.Error? = null
) : AiService {

    var requestCount: Int = 0
    var lastRequest: AiRequest? = null

    override suspend fun generateReply(request: AiRequest): AiResult<AiReply> {
        requestCount++
        lastRequest = request
        return error ?: AiResult.Success(AiReply(chunks.joinToString("")))
    }

    override fun streamReply(request: AiRequest): Flow<AiResult<String>> {
        requestCount++
        lastRequest = request
        return flow {
            error?.let { emit(it); return@flow }
            chunks.forEach { emit(AiResult.Success(it)) }
        }
    }
}
```

---

## In-Memory Room Database (for integration tests)

```kotlin
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personal.aurachat.data.local.AuraChatDatabase

// Use in @Before; close in @After
fun buildInMemoryDatabase(): AuraChatDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AuraChatDatabase::class.java
    )
    .allowMainThreadQueries()   // OK only in tests
    .build()
```

> ⚠️ In-memory Room tests require the **androidTest** source set (instrumented), not `test`.
> For pure unit tests, extract logic into testable functions (see `DefaultConversationRepositoryTest` pattern).

---

## Pure-Function Test Pattern

When a repository method has extractable pure logic, test it directly — no Room or coroutines needed:

```kotlin
class MyRepositoryLogicTest {

    @Test
    fun `some pure function behaves correctly`() {
        val result = somePureFunction(input)
        assertEquals(expected, result)
    }
}
```

---

## Error Path Test Pattern

Test each `AiErrorType` by configuring `FakeAiService`:

```kotlin
@Test
fun `offline error maps to FAILED delivery state`() = runTest {
    val aiService = FakeAiService(error = AiResult.Error(AiErrorType.OFFLINE))
    // ... build repository with fake service and in-memory DB
    // ... call sendUserMessage(...)
    // ... assert the last message in DB has deliveryState == FAILED and errorType == OFFLINE
}
```

---

## Checklist for Repository Tests

- [ ] Success path: message persisted with `SENT` deliveryState
- [ ] Error path: message persisted with `FAILED` deliveryState + correct `errorType`
- [ ] Retry path: failed message deleted and re-requested
- [ ] Request context: failed assistant _partial replies_ included; error placeholders excluded
- [ ] Streaming: each chunk updates the same message row (no duplicate inserts)
