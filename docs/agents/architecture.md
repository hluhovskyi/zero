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

**Default to no UseCase** — the ViewModel calls the repository directly. Build the interface only when a trigger fires *today* (anticipated future callers don't count):

- **2+ callers** — the same operation runs from two ViewModels / components / call sites.
- **Crosses a Dagger scope** — shared between, say, an `@ActivityScope` and a `@<Feature>Scope` consumer, so they must see the same instance.
- **Non-trivial logic** — a single VM mixing a few hundred LOC of state plumbing with branching domain logic; extract the domain logic so the VM stays a thin projection. `DefaultBudgetUseCase` (period math + query observation + save + period replace) is the canonical example.
- **Documented module contract** — the interface is a published seam other modules consume.

A single-method `*UseCase` with one impl and one caller is the [speculative-UseCase smell](architecture-review.md) — inline it. **Don't pre-declare UseCases in plans**: `add a FooUseCase` is a candidate, not a directive (see [superpowers-workflow.md](superpowers-workflow.md)); extract from observed need.

## UseCase State Ownership

**When a UseCase exists, it owns all state — ViewModels are thin projections.** A ViewModel backed by a UseCase should only `filterIsInstance` and `.map` the relevant slice; it must not introduce its own `MutableStateFlow`. State that lives in the ViewModel instead of `UseCase.InternalState` won't survive back-navigation and won't be visible to other steps in the same flow.

**Derive the store; don't let multiple writers scribble on it.** A UseCase whose internal `MutableStateFlow` is written by several `attach()` collectors *and* `perform()` is a latent race: a reference-list re-emission overwrites the user's selection, and load races the reference data (which tempts a `filter { … }.take(1)` load gate that hangs forever when a list is legitimately empty). Derive it instead, the same way ViewModels `combine()` repository flows:
- Hold user + loaded **intent** in one draft (ids + scalars), written *only* by `perform()`, the loader, and pickers.
- Keep each reference list (accounts, categories, …) as its own flow.
- Produce the read model with a single `combine(draft, refs…) → pure resolve()` — one writer, selections resolved `id → object` against whatever lists exist *now* (empty lists yield null selections, not a block, so no load gate is needed).
- **Fold a loaded row into the draft by id, not by resolving it against the lists at load time** — resolving-at-load is exactly what races the collectors; let `resolve()` map id→object as each list arrives.

Reference impl: `DefaultTransactionEditUseCase` (`TransactionEditDraft` → `resolve()` → `TransactionEditState`).

## ViewModel UI Shape

Layer split — enforced by `ViewProviderDerivation` lint:
- **UseCase** — domain truth: sort, aggregation, business thresholds. Emits domain types.
- **ViewModel** — screen shape: sealed `Item` per visual variant, pre-joined fields, pre-computed semantic enums (e.g. `Status.{Healthy,Watch,AlmostThere,Over}`). No formatters, no Compose `Color`.
- **View** — `when (item) { is X -> ...; is Y -> ... }`, `AmountFormatter`, `ZeroTheme.colors`, `stringResource`. No `.filter`/`.any`/`.sortedBy`/`sumOf`, no `if (raw.field != null)`.

Reference: `TransactionViewModel.Item`. For derived `State` fields prefer body `val foo = expr` over `get()` — same correctness, cached per instance, re-evaluated by `copy()`.

## Flow Composition

ViewModels `combine()` multiple repository flows into a single `Flow<State>`. Key extensions:
- `onStartWithEmptyList()` — emit `[]` before first real emission so `combine` doesn't block
- `onEmptyReturnEmptyList()` — emit `[]` if flow completes empty
- `associateById()` — convert `List<Identifiable>` to `Map<Id.Known, T>` for O(1) lookups

Without `onStartWithEmptyList()`, `combine` waits for ALL flows to emit before producing anything — this causes blank screens.

## Reactive + Paginated Data

Transaction list uses a hybrid approach: a reactive Room `Flow` (for new/updated items via `updatedDateTime`) combined with a one-shot paginated flow (for historical data). Merge logic explicitly compares `updatedDateTime` to pick the fresher version of duplicates.

## Lint Enforcement

Much of the above is machine-enforced by custom detectors in `lint-rules/src/main/kotlin/com/hluhovskyi/zero/lint/` — CI gates them via `./gradlew lint`, so they fail the build on their own (don't re-flag them in review). The list below is pinned to `ZeroIssueRegistry` by `DocsConsistencyTest` — a new detector must be documented here in the same change. Rules by issue ID:

- **`ViewProviderDerivation`, `ViewProviderMustNotInjectRepository`** — no derivation or domain deps in a ViewProvider (the ViewModel UI Shape split).
- **`DefaultImplMustBeInternal`, `ViewProviderMustBeInternal`, `NoImplSuffix`** — impls stay `internal` and are named `Default*`, never `*Impl` (the `Default*` prefix is what the visibility rules key on).
- **`ScopedComponentBuilder`** — a feature `@Component` carries its `@Scope` + `Buildable` builder; Builder `@Provides` stay unscoped.
- **`HandlerMustBeFunInterface`** — screen-to-screen callbacks are `fun interface OnXxxHandler`.
- **`NoNamedAnnotation`** — duplicate bindings of one type get dedicated `@Qualifier` annotations, never `@Named("string")`.
- **`UnhandledCloseable`, `UnhandledJob`** — `attach()` Closeables/Jobs are retained, not dropped.
- **`DirectClockUsage`** — inject `common.time.Clock`; `Clock.System` is allowed only in the Clock impls (`common/time/`) and the test bridge.
- **`ZonedClockPreferred`** — a constructor taking both `Clock` and `ZoneProvider` should inject `ZonedClock` instead; it combines them and exposes `localDateTime()`.
- **`NoDispatchersDefault`** — `Dispatchers.IO`, or `DispatcherProvider.cpu()` for CPU-bound work; raw `Dispatchers.Default` lives only in the provider impl.
- **`ZeroThemeBypass`** — Compose colors come from `ZeroTheme.colors`, never a hardcoded hex.
- **`MaterialTwoImport`** — `androidx.compose.material3` only; M2 imports are allowed only in the bottom-sheet navigator island and the `material.icons` packs.
- **`HardcodedComposableString`, `UppercaseStringResource`** — user-facing text is a string resource.
- **`FullyQualifiedReference`** — import types; no fully-qualified names in code bodies.
- **`RemoteComponentEncapsulation`, `DatabaseComponentEncapsulation`, `KmpReadiness`** — module boundaries: KMP-bound modules stay free of Android/OkHttp/java.time; component internals stay encapsulated.
- **`SyncEntityFieldMustHaveSerialName`** — every `@Serializable` sync field pins its JSON key.
- **`TestBridgeBoundary`, `TestBridgeProductionPurity`** — the e2e test seam can't leak into production.
- **`SealedSubtypeDuplicateProperty`** — shared properties belong on the sealed parent.
- **`BreadcrumbsLiteralOnly`** — `Breadcrumbs.log()` takes string literals only (breadcrumbs end up in public GitHub issues; interpolation risks PII).

**A new module must wire `lintChecks(project(":lint-rules"))` — the registry does not propagate from other modules.** Pure-JVM modules additionally apply `id("com.android.lint")` (the standalone lint plugin, which registers their `lint` task). An unwired module silently skips every rule above; six modules sat unwired (and their boundary rules dead) until 2026-06.

What lint **can't** see — the structural, judgment-call smells (a sentinel param, a boolean that's really a subtype, a special-case branch, conflated responsibilities) — is what the [Architecture Review](architecture-review.md) pass is for.
