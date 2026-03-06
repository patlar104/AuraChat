# AuraChat – GitHub Copilot Workspace Instructions

AuraChat is an Android AI chat app (Kotlin 2.0.21, Jetpack Compose, Google Gemini). No Hilt/Dagger — uses a manual service locator.

## Build & Test Commands

```bash
./gradlew assembleDebug                                        # Build debug APK (primary verify command)
./gradlew assembleRelease                                      # Build release APK
./gradlew test                                                 # All unit tests
./gradlew testDebugUnitTest --tests "com.personal.aurachat.X" # Single test class
./gradlew connectedAndroidTest                                 # Instrumented tests (device/emulator required)
./gradlew lint                                                 # Android lint — run before committing manifest/security changes
./gradlew clean                                                # Clean build outputs
```

## Architecture

Clean MVVM with four strict layers (no cross-layer skipping):

```
domain/        # Interfaces + data models — zero Android dependencies
data/          # Room DB, Google AI SDK, repository implementations
presentation/  # ViewModels (StateFlow + coroutines)
ui/            # Jetpack Compose screens and components
```

**Key files:**
- `AppContainer.kt` — manual service locator; created in `AuraChatApp`, accessed from `MainActivity` via Application reference
- `data/remote/GoogleAiService.kt` — `streamReply()` returns `Flow<AiResult<String>>` (Gemini `gemini-1.5-flash`)
- `presentation/chat/ChatViewModel.kt` — uses `Mutex` to prevent concurrent sends; exposes `StateFlow<UiState>`
- `core/network/NetworkMonitor.kt` — wraps `ConnectivityManager`, exposes `isOnline: Flow<Boolean>`

## State & Data Flow

1. Compose screens collect `StateFlow<UiState>` from ViewModels
2. ViewModels combine flows with `combine { }.stateIn(...)`
3. `DefaultConversationRepository` collects the AI stream and persists each chunk to Room in a transaction
4. `NetworkMonitor.isOnline` is combined into ViewModel state for offline UI

## Database

Two Room entities with cascade-delete:
- `ConversationEntity` — key index on `(conversationId, createdAtEpochMs)`
- `MessageEntity` — key index on `(conversationId, role, deliveryState)`

## Secure Storage

- **API key**: `EncryptedSharedPreferences` (AES-256-GCM); on corruption it clears and recreates
- **Timeout preference**: `DataStore`

## Error Handling

`AiErrorType` enum: `OFFLINE`, `TIMEOUT`, `NETWORK`, `EMPTY_RESPONSE`, `MALFORMED_RESPONSE`, `UNAUTHORIZED`, `UNKNOWN`

Messages carry `deliveryState`: `SENT` or `FAILED`. Retry = delete the failed message and re-request.

## Dependency Management

All versions are in `gradle/libs.versions.toml`. **Never add inline versions in build files** — always use the version catalog.

Key versions: Room 2.6.1, Google AI Client SDK 0.9.0, Min SDK 24, Target SDK 35.

## Testing

Unit tests live in `app/src/test/`. Reference tests:
- `presentation/chat/ChatViewModelTest.kt` — ViewModel layer
- `data/repository/DefaultConversationRepositoryTest.kt` — repository layer
- `ui/chat/ChatScreenLogicTest.kt` — screen logic

Instrumented tests: `app/src/androidTest/` (require emulator).

## Common Pitfalls

- **No DI framework**: wiring is manual in `AppContainer.kt`; new dependencies must be added there
- **KSP required for Room**: annotation processing uses KSP, not KAPT
- **Type-safe Navigation**: routes use Navigation Compose type-safe APIs, not string routes
- **Concurrent send guard**: always check for the `Mutex` in `ChatViewModel` when modifying send logic
- **Lint before security/manifest commits**: `./gradlew lint` to catch issues pre-merge
