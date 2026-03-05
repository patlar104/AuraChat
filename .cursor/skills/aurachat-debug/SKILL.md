---
name: aurachat-debug
description: Debug AuraChat. Use when debugging Room, AI streaming, connectivity, or when the user mentions GoogleAiService, NetworkMonitor, ChatViewModel, or deliveryState issues.
---

# AuraChat Debugging

## Architecture Overview

- **domain/**: Interfaces, data models (no Android deps)
- **data/**: Room DB, Google AI SDK, repositories
- **presentation/**: ViewModels (StateFlow, coroutines)
- **ui/**: Jetpack Compose screens

## Key Components to Check

### Room Database

- Entities: `ConversationEntity`, `MessageEntity` (cascade-delete)
- Indices: `(conversationId, createdAtEpochMs)`, `(conversationId, role, deliveryState)`
- Repository persists AI chunks inside a transaction

### AI Streaming

- `GoogleAiService.streamReply()` returns `Flow<AiResult<String>>`
- Uses Google Generative AI SDK (`gemini-1.5-flash`)
- Repository collects the flow and persists each chunk to Room

### ChatViewModel

- Uses a `Mutex` to prevent concurrent sends
- Combines flows with `combine { }.stateIn(...)`

### NetworkMonitor

- Wraps `ConnectivityManager`
- Exposes `isOnline: Flow<Boolean>` into UI state

### Error Handling

- `AiErrorType`: OFFLINE, TIMEOUT, NETWORK, EMPTY_RESPONSE, MALFORMED_RESPONSE, UNAUTHORIZED, UNKNOWN
- `deliveryState`: SENT or FAILED
- Retry: delete failed message and re-request
