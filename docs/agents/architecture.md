# Architecture Patterns

## Component Pattern

Every feature follows: `FeatureComponent → FeatureViewModel → FeatureViewProvider` (+ optional `FeatureUseCase`). Read any existing component (e.g., `AccountComponent`, `TransactionComponent`) for the canonical structure.

### Why it works this way

- **Component owns lifecycle** — `attach()` delegates to ViewModel, returns `Closeable` that cancels coroutines. `AttachWithView()` in Compose ties this to `DisposableEffect`.
- **Embedding a sub-component** — if a ViewProvider needs to render another `AttachableViewComponent` (e.g. a `TransactionComponent` inside a detail screen), call `subComponent.AttachWithView()` directly in the composable. Do **not** call `subComponent.attach()` manually from the parent's `attach()` — Compose manages the sub-component's lifecycle via `DisposableEffect`.
- **Host-side scroll coordination** — if the host screen needs to react to a sub-component's internal scroll (e.g. collapsing a hero card as the sub-component's list scrolls), use `NestedScrollConnection` on the host's wrapping `Box`. Never add a header slot, content lambda, or layout parameter to the sub-component's `Builder` or `ViewProvider` to satisfy the host's layout needs — sub-components are black boxes; scroll events propagate up through the nested scroll system automatically.
- **`Buildable<T>` on Builder** — `NavigatorScope.buildable()` rebuilds the component on every navigation, so each screen visit gets fresh state.
- **Handler callbacks, not shared state** — screens communicate through `fun interface OnXxxHandler` passed via `@BindsInstance`. Handlers that trigger navigation must dispatch on `Dispatchers.Main`.
- **UseCase is optional** — only extract one when business logic is complex enough to warrant separation from the ViewModel (e.g., `TransactionEditUseCase` handles 3 transaction types). See [When to introduce a UseCase](#when-to-introduce-a-usecase).

## When to introduce a UseCase

**Default to no UseCase.** The VM calls the repository directly; build the interface only when one of these triggers fires today:

- **2+ callers** — the same operation is invoked from two ViewModels, components, or call sites. Anticipated future callers don't count.
- **Crosses a Dagger scope** — the operation is shared between, say, an `@ActivityScope` consumer and a `@<Feature>Scope` consumer, so they must see the same instance.
- **Non-trivial logic > ~300–400 LOC of mixed UI/state and business logic in one VM** — once a single VM file mixes a few hundred lines of state plumbing with branching domain logic, extract the domain logic to a UseCase and let the VM become a thin projection over its state. `DefaultBudgetUseCase` (period math + query observation + save + period replace) is the canonical example.
- **Documented module contract** — the interface is a published seam other modules consume; mark it explicitly (e.g. file-level comment) so the lint rule can be suppressed.

**Don't pre-declare UseCases in plans.** Plans describe data flow + acceptance criteria; the implementer decides extractions from observed concrete need. The `SpeculativeUseCase` lint rule (warning) flags single-method `*UseCase` interfaces that don't meet any of the triggers above. Suppress with `@Suppress("SpeculativeUseCase")` on the interface declaration when one of the triggers does apply but the rule can't see it (e.g. consumers in another module).

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
