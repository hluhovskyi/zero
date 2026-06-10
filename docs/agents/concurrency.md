# Concurrency Patterns

## Core Principle

Design everything async-first. No blocking code, no `runBlocking`, no `Thread.sleep`. The codebase has zero blocking calls — keep it that way.

## When to Use What

| Pattern | When | Example |
|---------|------|---------|
| `Flow<T>` | Continuous observation (queries, state) | `repository.query()`, `viewModel.state` |
| `suspend fun` | One-shot operations (writes, lookups) | `repository.insert()`, DAO `selectById()` |
| `MutableStateFlow` | Local mutable state exposed to UI | ViewModel state holder |
| `MutableSharedFlow` | Event broadcasting (triggers, signals) | Pagination `loadMoreTrigger` |

**Never** use a suspend function where a Flow is needed (reactive updates), or a Flow where a suspend function suffices (single result).

## Dispatcher Rules

- **`Dispatchers.IO`** — default for all CoroutineScopes in ViewModels/UseCases. All data loading, repository calls, and business logic run here.
- **`Dispatchers.Main`** — only for handler callbacks that trigger navigation. Always wrap with `launch(context = Dispatchers.Main) { handler.onSaved() }`.
- **Never hardcode `Dispatchers.Default`** — CPU-bound work goes through the injectable `DispatcherProvider.cpu()` seam; everything else runs on IO. Enforced by the `NoDispatchersDefault` lint (only the provider impl under `common/coroutines/` may reference it).
- **`BaseViewModel.scope` is Main-dispatched** — collecting a Room-backed flow whose `.onStart { dao.get() }` touches the DB (e.g. `ConfigurationRepository.observe`) crashes with `IllegalStateException("Cannot access database on the main thread")`. Apply `.flowOn(dispatchers.io())` to such flows before they reach the collector.

## Lifecycle: attach() → Closeable

Every ViewModel/UseCase starts work in `attach()` and returns a `Closeable` that cancels it:

```kotlin
override fun attach(): Closeable = Closeables.of {
    coroutineScope.launch {
        // all reactive subscriptions go here
    }
}
```

`Closeables.of { }` wraps the `Job` — calling `close()` cancels the job and all child coroutines. Compose's `AttachWithView()` handles this automatically via `DisposableEffect`.

`BaseViewModel.attach()` is **reference-counted** (via `RefCountedAttachable`): `attachOnMain()` runs once on the first `attach()` and the scope is cancelled only when the last returned `Closeable` is closed. This makes `attach()` idempotent, so a component can be held by more than one caller at once — e.g. a session-long keep-warm `attach()` plus a per-display `AttachWithView()` — without double-subscribing or tearing down early while another holder is still attached.

## State Updates

Always use `MutableStateFlow.update { }` with immutable `copy()`:

```kotlin
mutableState.update { state -> state.copy(items = newItems) }
```

This is thread-safe. Never mutate state directly.

## collectLatest vs collect

- **`collectLatest`** — use for reactive UI updates. Cancels in-flight processing when a new value arrives. This is the default choice.
- **`collect`** — use only when you need to process every emission sequentially (rare) or when waiting for a condition (e.g., `filter { ... }.take(1).collect()`).

## combine() and Flow Extensions

`combine()` waits for ALL upstream flows to emit before producing anything. Without mitigation, this causes blank screens.

**Always use these extensions on flows passed to `combine()`:**
- `onStartWithEmptyList()` — emits `[]` immediately so combine doesn't block
- `onEmptyReturnEmptyList()` — emits `[]` if the flow completes empty

## Pagination Pattern

Trigger-driven pagination via `MutableSharedFlow<Unit>`:

1. ViewModel holds a `MutableSharedFlow<Unit>` trigger
2. `LoadMore` action emits to the trigger
3. Repository's `query(criteria, trigger = loadMoreTrigger)` collects the trigger and loads next page
4. Pages accumulate in the repository flow, re-emitted as a growing list
