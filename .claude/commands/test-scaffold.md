Generate unit test scaffolds for the untested layers of AuraChat.

## Context

The only real tests currently live in ChatViewModelTest.kt. These layers have zero coverage:
- DefaultConversationRepository (streaming AI responses, error paths, retry logic)
- DefaultSettingsRepository (API key get/set, EncryptedSharedPreferences corruption recovery)
- AuraChatDao (Room queries, cascade delete, index correctness)

## Steps

1. **Audit current test coverage**
   ```bash
   tree app/src/test/ app/src/androidTest/ 2>/dev/null
   wc -l app/src/test/java/com/personal/aurachat/presentation/chat/ChatViewModelTest.kt
   rg "fun test|@Test" app/src/test/ --type kt -n    # list all existing test functions
   rg "import" app/src/test/java/com/personal/aurachat/presentation/chat/ChatViewModelTest.kt
   ```

2. **Read source files to scaffold tests for**
   ```bash
   cat app/src/main/java/com/personal/aurachat/data/repository/DefaultConversationRepository.kt
   cat app/src/main/java/com/personal/aurachat/data/repository/DefaultSettingsRepository.kt
   cat app/src/main/java/com/personal/aurachat/data/local/AuraChatDao.kt
   cat app/src/main/java/com/personal/aurachat/domain/model/AiModels.kt
   ```

3. **Check what test dependencies are already declared**
   ```bash
   rg "testImplementation|androidTestImplementation" app/build.gradle.kts
   cat gradle/libs.versions.toml | grep -A2 "test\|mock\|coroutine"
   ```

4. **Generate test files** following the existing test conventions:
   - Match imports and coroutine test patterns from ChatViewModelTest.kt exactly
   - For DAO tests: use `Room.inMemoryDatabaseBuilder` — place in `androidTest` (requires Android runtime)
   - For repository tests: fake/stub the DAO and AiService — no mocking frameworks unless already present
   - Cover per class:
     - `DefaultConversationRepository`: happy path send, each `AiErrorType` (OFFLINE, TIMEOUT, UNAUTHORIZED, NETWORK, EMPTY_RESPONSE), streaming chunks persisted, retry deletes failed message
     - `DefaultSettingsRepository`: API key set/get, blank key returns null, corruption recovery path
     - `AuraChatDao`: insert+query conversation, cascade delete, message ordering by `createdAtEpochMs`

5. **Write the files**:
   - `app/src/test/java/com/personal/aurachat/data/repository/DefaultConversationRepositoryTest.kt`
   - `app/src/test/java/com/personal/aurachat/data/repository/DefaultSettingsRepositoryTest.kt`
   - `app/src/androidTest/java/com/personal/aurachat/data/local/AuraChatDaoTest.kt`

6. **Verify files were created and have real content**
   ```bash
   tree app/src/test/ app/src/androidTest/
   wc -l app/src/test/java/com/personal/aurachat/data/repository/DefaultConversationRepositoryTest.kt
   wc -l app/src/test/java/com/personal/aurachat/data/repository/DefaultSettingsRepositoryTest.kt
   ```

7. **Run unit tests and fix until green**
   ```bash
   ./gradlew testDebugUnitTest 2>&1 | tail -30
   ```
