# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AuraChat is an Android AI chat app using Google Gemini. Key build command: `./gradlew assembleDebug`. Architecture follows Clean MVVM with Jetpack Compose. See sections below for full details.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew testDebugUnitTest      # Run a single test class: add --tests "com.personal.aurachat.MyTest"
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew lint                   # Run Android lint checks
./gradlew clean                  # Clean build outputs
```

## Architecture

Clean MVVM with repository pattern, organized in four layers:

```
domain/          # Interfaces + data models — no Android deps
data/            # Room DB, Google AI SDK, repository implementations
presentation/    # ViewModels (StateFlow, coroutines)
ui/              # Jetpack Compose screens and components
```

**Entry point**: `AppContainer.kt` is a manual service locator (no Hilt/Dagger). It instantiates all dependencies and is created in `AuraChatApp`. `MainActivity` accesses `AppContainer` via the Application reference.

**State flow**: Compose screens collect `StateFlow<UiState>` from ViewModels. ViewModels combine multiple flows (Room DAOs, NetworkMonitor, etc.) using `combine { }.stateIn(...)`. `ChatViewModel` uses a `Mutex` to prevent concurrent sends.

**AI streaming**: `GoogleAiService.streamReply()` returns `Flow<AiResult<String>>` using the Google Generative AI SDK (`gemini-1.5-flash`). The repository collects the flow and persists each chunk to Room inside a transaction.

**Database**: Two Room entities — `ConversationEntity` and `MessageEntity` (cascade-delete). Key indices on `(conversationId, createdAtEpochMs)` and `(conversationId, role, deliveryState)` for efficient message queries.

**Secure storage**: API key is stored in `EncryptedSharedPreferences` (AES-256-GCM). On corruption it clears and recreates. Timeout preference uses `DataStore`.

## Key Technology

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose + Material3 + Navigation Compose (type-safe routes)
- **Database**: Room 2.6.1 with KSP annotation processing
- **AI**: Google AI Client SDK 0.9.0 (`generativeai`)
- **Async**: Kotlin Coroutines + Flow + StateFlow
- **Min SDK**: 24 | Target SDK: 35

## Dependency Versions

All dependency versions are centralized in `gradle/libs.versions.toml`. Always update versions there, not inline in build files.

## Error Handling Patterns

`AiErrorType` covers: `OFFLINE`, `TIMEOUT`, `NETWORK`, `EMPTY_RESPONSE`, `MALFORMED_RESPONSE`, `UNAUTHORIZED`, `UNKNOWN`. Messages have a `deliveryState` of `SENT` or `FAILED`; failed messages can be retried (deleted and re-requested).

The `NetworkMonitor` wraps `ConnectivityManager` and exposes an `isOnline: Flow<Boolean>` that ViewModels combine into UI state.

## Code Reviews

When asked to do a security review, always perform a **comprehensive review of the entire codebase** — not just pending or staged changes. Use file exploration (Read, Glob, Grep) and `git log` / `git diff HEAD~N` to inspect history. Check: manifest hardening, logging safety, API key exposure, exported components, intent filters, and network/storage security.

## Workflow

- **Before committing security or manifest changes**: run `./gradlew lint` to catch issues before they land.
- **After making fixes**: verify the build still compiles with `./gradlew assembleDebug`.
