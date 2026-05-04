# Architecture Patterns

## Component Pattern

Every feature follows: `FeatureComponent → FeatureViewModel → FeatureViewProvider` (+ optional `FeatureUseCase`). Read any existing component (e.g., `AccountComponent`, `TransactionComponent`) for the canonical structure.

### Why it works this way

- **Component owns lifecycle** — `attach()` delegates to ViewModel, returns `Closeable` that cancels coroutines. `AttachWithView()` in Compose ties this to `DisposableEffect`.
- **Embedding a sub-component** — if a ViewProvider needs to render another `AttachableViewComponent` (e.g. a `TransactionComponent` inside a detail screen), call `subComponent.AttachWithView()` directly in the composable. Do **not** call `subComponent.attach()` manually from the parent's `attach()` — Compose manages the sub-component's lifecycle via `DisposableEffect`.
- **`Buildable<T>` on Builder** — `NavigatorScope.buildable()` rebuilds the component on every navigation, so each screen visit gets fresh state.
- **Handler callbacks, not shared state** — screens communicate through `fun interface OnXxxHandler` passed via `@BindsInstance`. Handlers that trigger navigation must dispatch on `Dispatchers.Main`.
- **UseCase is optional** — only extract one when business logic is complex enough to warrant separation from the ViewModel (e.g., `TransactionEditUseCase` handles 3 transaction types).

## UseCase State Ownership

**When a UseCase exists, it owns all state — ViewModels are thin projections.** A ViewModel backed by a UseCase should only `filterIsInstance` and `.map` the relevant slice; it must not introduce its own `MutableStateFlow`. State that lives in the ViewModel instead of `UseCase.InternalState` won't survive back-navigation and won't be visible to other steps in the same flow.

## Flow Composition

ViewModels `combine()` multiple repository flows into a single `Flow<State>`. Key extensions:
- `onStartWithEmptyList()` — emit `[]` before first real emission so `combine` doesn't block
- `onEmptyReturnEmptyList()` — emit `[]` if flow completes empty
- `associateById()` — convert `List<Identifiable>` to `Map<Id.Known, T>` for O(1) lookups

Without `onStartWithEmptyList()`, `combine` waits for ALL flows to emit before producing anything — this causes blank screens.

## Reactive + Paginated Data

Transaction list uses a hybrid approach: a reactive Room `Flow` (for new/updated items via `updatedDateTime`) combined with a one-shot paginated flow (for historical data). Merge logic explicitly compares `updatedDateTime` to pick the fresher version of duplicates.
