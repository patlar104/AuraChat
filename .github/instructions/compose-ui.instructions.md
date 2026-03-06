---
applyTo: "app/src/main/java/**/ui/**"
---

# Compose / Material3 Conventions — AuraChat `ui/` Layer

## Screen Contract

Every screen composable must:

- Accept a `UiState` data class + typed callbacks as parameters — never collect `StateFlow` directly
- Have `modifier: Modifier = Modifier` as the **last** parameter
- Be created in `AuraChatNavGraph`; ViewModels are **never** instantiated inside screen composables

```kotlin
@Composable
fun FooScreen(
    uiState: FooUiState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) { ... }
```

## State Hoisting

- **Local UI state** (drafts, expanded items, visibility toggles) → `remember` / `rememberSaveable` inside the screen
- **Scrollable list position** → `rememberSaveable(saver = LazyListState.Saver)`
- Never push ephemeral UI state into ViewModels

## Scaffold & Edge-to-Edge

All screens use `Scaffold`. Always consume `innerPadding`:

```kotlin
Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
    Column(modifier = Modifier.padding(innerPadding)) { ... }
}
```

For screens with a keyboard input field (e.g., chat), add **both** after `padding(innerPadding)`:

```kotlin
.imePadding()
.navigationBarsPadding()
```

## Navigation

Routes are **string-based constants** defined at the top of `AuraChatNavGraph.kt`. Add new routes there:

```kotlin
private const val FOO_ROUTE = "foo"
```

ViewModels are created inside `composable(ROUTE) { }` blocks using `viewModel(factory = ...)`. For screens that need a conversation ID, use the `key = "chat_$id"` pattern to scope the ViewModel.

State is collected with `collectAsStateWithLifecycle()` — **never** `collectAsState()`.

## Theming

Use `MaterialTheme.colorScheme.*` — never hardcode colors in UI files. The palette:

| Token                                         | Usage in AuraChat                      |
| --------------------------------------------- | -------------------------------------- |
| `primaryContainer` / `onPrimaryContainer`     | User message bubbles                   |
| `secondaryContainer` / `onSecondaryContainer` | AI message bubbles                     |
| `errorContainer` / `onErrorContainer`         | Failed message bubbles, offline banner |
| `surfaceContainerLow`                         | Conversation list cards                |
| `surfaceContainerHigh`                        | Typing indicator card                  |
| `surfaceContainerHighest`                     | Empty-state card                       |
| `onSurfaceVariant`                            | Secondary / timestamp text             |

Dynamic color (Android 12+) is enabled by default in `AuraChatTheme`. Static fallback uses the blue-green/mist/leaf palette from `Color.kt`.

Typography uses `MaterialTheme.typography.*` tokens. Never hardcode `fontSize` or `fontWeight` except for emphasis (`FontWeight.SemiBold`, `FontWeight.Bold`) on titles.

## Icons

Always use `Icons.Rounded.*` (or `Icons.AutoMirrored.Rounded.*` for directional icons like arrows):

```kotlin
// ✅
Icons.Rounded.Settings
Icons.AutoMirrored.Rounded.ArrowBack

// ❌ — never use
Icons.Default.*
```

## Components

- Private sub-composables within a screen file use `private` visibility
- Reusable cross-screen components go in `ui/components/`
- Naming: `*Screen` for top-level screens, `*Card` / `*Header` / `*Row` for sub-sections

### MessageBubble colors (reference)

```kotlin
val bubbleColor = when {
    isUser -> MaterialTheme.colorScheme.primaryContainer
    message.deliveryState == FAILED -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.secondaryContainer
}
```

AI messages render markdown via `MarkdownText(markdown = ..., style = ...)`. User messages use plain `Text`.

## LazyColumn

Always provide stable `key` lambdas using entity IDs:

```kotlin
items(list, key = { it.id }) { item -> ... }
```

## Opt-ins Required

```kotlin
@OptIn(ExperimentalMaterial3Api::class)           // TopAppBar
@OptIn(ExperimentalLayoutApi::class)              // FlowRow
```

Declare `@OptIn` on the narrowest scope (function, not file) unless multiple annotated APIs appear throughout.

## What NOT to Do

- Don't skip `innerPadding` — causes content hidden under system bars
- Don't use `Icons.Default.*` — breaks visual consistency
- Don't hardcode `Color(0xFF...)` in screens — add to `Color.kt` and reference via `MaterialTheme`
- Don't add ViewModel creation logic inside screen composables — belongs in `AuraChatNavGraph`
