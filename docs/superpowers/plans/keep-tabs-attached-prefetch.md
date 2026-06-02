# Keep Bottom-Bar Tabs Attached + Prefetched Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the data-load "jump" when switching between the four bottom-bar tabs (Home, Budget, Account, Category) by keeping their components attached + prefetched at `MainActivityScreenScope`, with an idempotent (reference-counted) `attach()` so the per-display `AttachWithView()` and the parent keep-warm `attach()` coexist without double-collection or premature teardown.

**Architecture:** Today each tab is destroyed on switch (`DefaultBottomBarViewModel` navigates with `clearBackStack = true`) and rebuilt + re-attached on return, re-running its async Room query — so it renders the empty default `State()` for a frame, then jumps to real data. Fix: build the four tab components once at `MainActivityScreenScope` and `attach()` them from `MainActivityScreenComponent.attach()` so their `StateFlow`s stay warm for the whole session. Idempotent-attach is provided at the ViewModel layer: a `RefCountedAttachable` primitive used inside `BaseViewModel`. To make this uniform, the four tab ViewModels that don't yet extend `BaseViewModel` are migrated onto it.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Room, kotlinx.coroutines. Modules: `zero-api` (`RefCountedAttachable`), `zero-core` (`BaseViewModel` + VM migrations + DI), `app` (tab wiring).

**Scope:** Part A only — no `StateFlow`/`collectAsStateWithLifecycle` change; non-tab destinations and Settings stay lazy. `DefaultBottomBarViewModel`'s `clearBackStack = true` is unchanged (it no longer affects freshness — the tab components live above the nav layer).

---

## ZERO-2 Safety (read before Task 7)

`MainActivityScreenComponent` is built **per composition** via `remember(navController, ...)` and attached with the **non-retaining** `T.AttachWithView()` overload (`MainActivityViewProvider.kt:49-59`), in lockstep with `rememberNavController`. Eager-attaching the tab components inside its `attach()` inherits that exact lifetime — their captured `navigator` is rebuilt with the component on configuration change and never goes stale. **Do NOT** introduce any Android-`ViewModel` retention of tab components (no retaining `(() -> Component).AttachWithView()` path for them). Tab lifetime == `MainActivityScreenComponent` lifetime == `navigator` lifetime.

## Threading note for migrations (read before Tasks 3–6)

`BaseViewModel`'s `scope` runs on `dispatchers.main()` (`ViewModelCoroutineScope.kt:20`). The VMs being migrated currently run their whole `attach` body on a private `CoroutineScope(Dispatchers.IO)`. To **preserve behavior and avoid main-thread Room (ANR/jank)**, each migrated subscription launches on IO explicitly: `scope.launch(dispatchers.io()) { …existing body… }`. `perform` branches keep their current dispatcher intent: navigation/handler calls → `scope.launch(dispatchers.main())`; repository writes → `scope.launch(dispatchers.io())`; pure `mutableState.update` stays inline. **Migration is behavior-preserving** — only scope ownership moves from the private IO scope to `BaseViewModel`; the flow logic is copied verbatim.

---

## Task 1: `RefCountedAttachable` primitive

A reusable `Attachable` whose `attach()` is reference-counted: the underlying work runs once on the first ref (0→1) and is closed only when the last ref is released (1→0).

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/common/RefCountedAttachable.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/common/RefCountedAttachableTest.kt`

**Analogs:** lives next to `Attachable.kt`/`Closeables.kt` in `zero-api` `common`. Test modeled on `zero-core/.../feedback/InMemoryBreadcrumbsTest.kt` (plain JUnit + a concurrency case).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.hluhovskyi.zero.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

class RefCountedAttachableTest {

    private class FakeAttach {
        val starts = AtomicInteger(0)
        val closes = AtomicInteger(0)
        var live = 0
            private set
        fun onAttach(): Closeable {
            starts.incrementAndGet(); live++
            return Closeable { closes.incrementAndGet(); live-- }
        }
    }

    @Test
    fun `starts once on first attach and stays started while count positive`() {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)
        val a = attachable.attach()
        val b = attachable.attach()
        assertEquals(1, fake.starts.get()); assertEquals(1, fake.live)
        a.close(); assertEquals(0, fake.closes.get()); assertEquals(1, fake.live)
        b.close(); assertEquals(1, fake.closes.get()); assertEquals(0, fake.live)
    }

    @Test
    fun `re-attach after full release starts again`() {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)
        attachable.attach().close()
        attachable.attach()
        assertEquals(2, fake.starts.get()); assertEquals(1, fake.live)
    }

    @Test
    fun `double-close of one handle does not over-decrement`() {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)
        val a = attachable.attach(); val b = attachable.attach()
        a.close(); a.close()
        assertEquals(1, fake.live)
        b.close(); assertEquals(0, fake.live)
    }

    @Test
    fun `concurrent attach then close fully releases`() = runBlocking {
        val fake = FakeAttach()
        val attachable = RefCountedAttachable(fake::onAttach)
        val handles = (0 until 16).map { async(Dispatchers.Default) { attachable.attach() } }.awaitAll()
        handles.map { async(Dispatchers.Default) { it.close() } }.awaitAll()
        assertEquals(0, fake.live); assertEquals(1, fake.closes.get())
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`RefCountedAttachable` unresolved):
`./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.common.RefCountedAttachableTest"`

- [ ] **Step 3: Implement**

```kotlin
package com.hluhovskyi.zero.common

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An [Attachable] that reference-counts [attach]. [onAttach] runs once on the first ref (0→1)
 * and the [Closeable] it produced is closed only when the last ref is released (1→0). Each
 * handle returned by [attach] is idempotent. Lets a single instance be held by multiple callers
 * (e.g. a session-long keep-warm ref plus a per-display `AttachWithView`).
 */
class RefCountedAttachable(
    private val onAttach: () -> Closeable,
) : Attachable {

    private val lock = Any()
    private var count = 0
    private var closeable: Closeable = Closeables.empty()

    override fun attach(): Closeable {
        synchronized(lock) {
            if (count++ == 0) {
                closeable = onAttach()
            }
        }
        val released = AtomicBoolean(false)
        return Closeables.from {
            if (released.compareAndSet(false, true)) {
                synchronized(lock) {
                    if (--count == 0) {
                        closeable.close()
                        closeable = Closeables.empty()
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS** (4 tests).
- [ ] **Step 5: Commit** — `feat(core): add RefCountedAttachable primitive`

---

## Task 2: Reference-count `BaseViewModel.attach()`

Make every `BaseViewModel` subclass idempotent under repeated `attach()`: `attachOnMain()` runs once; the scope is cancelled only when the last holder closes. This is what makes the keep-warm + per-display double-attach safe.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/common/BaseViewModel.kt`
- Modify (doc-with-code): `docs/agents/concurrency.md` — note that `attach()` is now reference-counted/idempotent (work starts on first ref, stops on last release).
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/common/BaseViewModelRefCountTest.kt`

- [ ] **Step 1: Write the failing test** — a tiny subclass counting `attachOnMain` calls and scope liveness.

```kotlin
package com.hluhovskyi.zero.common

import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseViewModelRefCountTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override fun io(): CoroutineDispatcher = dispatcher
        override fun cpu(): CoroutineDispatcher = dispatcher
        override fun main(): CoroutineDispatcher = dispatcher
    }

    private inner class Subject : BaseViewModel(dispatchers) {
        var attachOnMainCalls = 0
        val liveScope get() = scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive
        override fun attachOnMain() { attachOnMainCalls++ }
    }

    @Test
    fun `attachOnMain runs once across multiple attach and scope closes only on last release`() {
        val subject = Subject()
        val a = subject.attach()
        val b = subject.attach()
        assertEquals(1, subject.attachOnMainCalls)
        assertTrue(subject.liveScope)
        a.close()
        assertTrue(subject.liveScope)
        b.close()
        assertFalse(subject.liveScope)
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`attachOnMain` runs twice / scope cancels on first close):
`./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.common.BaseViewModelRefCountTest"`

- [ ] **Step 3: Implement** — wrap the existing attach logic in a `RefCountedAttachable`.

```kotlin
internal abstract class BaseViewModel(
    dispatchers: DispatcherProvider,
    logger: Logger = Logger.Noop,
) : Attachable {

    private val closeableCoroutineScope: CloseableCoroutineScope = viewModelScope(
        dispatchers = dispatchers,
        rootExceptionHandler = CoroutineExceptionHandler { _, exception -> handleException(exception) },
    )
    protected val scope: CoroutineScope
        get() = closeableCoroutineScope

    private val refCounted = RefCountedAttachable {
        attachOnMain()
        closeableCoroutineScope
    }

    override fun attach(): Closeable = refCounted.attach()

    protected open fun attachOnMain() {
    }

    protected open fun handleException(throwable: Throwable): Unit = throw throwable
}
```

- [ ] **Step 4: Run — expect PASS.** Also run the existing `BaseViewModel` users to confirm no regression: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultAccountViewModelTest" --tests "*DefaultBudgetViewModel*"`
- [ ] **Step 5: Commit** — `feat(core): reference-count BaseViewModel.attach (idempotent)` (include the concurrency.md edit in this commit).

---

## Tasks 3–6: Migrate the four tab ViewModels onto `BaseViewModel`

**Template for all four:** `DefaultAccountViewModel` (`zero-core/.../accounts/DefaultAccountViewModel.kt`) + its provider in `AccountComponent.kt` (lines 40, 114-123 — `Dependencies` exposes `val dispatchers: DispatcherProvider`; the `viewModel` provider passes `dispatchers = dispatcherProvider`). For each component: add `val dispatchers: DispatcherProvider` to its `Dependencies` interface and thread it into the `viewModel` provider. `DispatcherProvider` is already supplied upstream (AccountComponent consumes it from the same parent), so the new `Dependencies` entries resolve from the same source — confirmed by the `:app:assembleDebug` at Task 10.

Mechanical migration recipe (apply per VM):
1. Constructor: drop `coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)`; add `private val dispatchers: DispatcherProvider`. Class header `: BaseViewModel(dispatchers), <TheViewModel>`.
2. Move the `attach()` body into `override fun attachOnMain()`, replacing the outer `coroutineScope.launch { … }` with `scope.launch(dispatchers.io()) { … }`. Delete the `Closeables.of { … }` wrapper and the `override fun attach()`.
3. `perform`: `coroutineScope.launch(Dispatchers.Main)` → `scope.launch(dispatchers.main())`; `coroutineScope.launch { <io work> }` → `scope.launch(dispatchers.io())`.
4. Remove now-unused imports (`CoroutineScope`, `Dispatchers`, `Closeables`, `launch` if unused, `java.io.Closeable`).

### Task 3: `DefaultWelcomeViewModel` (trivial — empty attach)

**Files:** `zero-core/.../welcome/DefaultWelcomeViewModel.kt`, `zero-core/.../welcome/WelcomeComponent.kt`

- [ ] **Step 1: Migrate** — full file:

```kotlin
package com.hluhovskyi.zero.welcome

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.settings.OnImportSelectedHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class DefaultWelcomeViewModel(
    private val onImportSelected: OnImportSelectedHandler,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    WelcomeViewModel {

    private val mutableState = MutableStateFlow(WelcomeViewModel.State())
    override val state: Flow<WelcomeViewModel.State> = mutableState

    override fun perform(action: WelcomeViewModel.Action) {
        when (action) {
            is WelcomeViewModel.Action.ImportSelected -> scope.launch(dispatchers.main()) {
                onImportSelected.onSelected()
            }
        }
    }
}
```

- [ ] **Step 2: DI** — `WelcomeComponent.kt`: add `val dispatchers: DispatcherProvider` to `Dependencies`; in the `viewModel` provider pass `dispatchers = dispatchers` to `DefaultWelcomeViewModel(...)`. (Follow `AccountComponent` lines 40, 114-123.)
- [ ] **Step 3: Compile** — `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -10`
- [ ] **Step 4: Commit** — `refactor(core): migrate WelcomeViewModel to BaseViewModel`

### Task 4: `DefaultHomeViewModel`

**Files:** `zero-core/.../home/DefaultHomeViewModel.kt`, `zero-core/.../home/HomeComponent.kt`

- [ ] **Step 1: Migrate** — full file:

```kotlin
package com.hluhovskyi.zero.home

import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.user.NewUserUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultHomeViewModel(
    private val newUserUseCase: NewUserUseCase,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    HomeViewModel {

    private val mutableState = MutableStateFlow(HomeViewModel.State())
    override val state: Flow<HomeViewModel.State> = mutableState

    override fun perform(action: HomeViewModel.Action) = Unit

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            newUserUseCase.isNewUser().collect { isNew ->
                mutableState.update { it.copy(isNewUser = isNew) }
            }
        }
    }
}
```

- [ ] **Step 2: DI** — `HomeComponent.kt`: add `val dispatchers: DispatcherProvider` to `Dependencies`; pass `dispatchers = dispatchers` in the `viewModel` provider (lines 86-90).
- [ ] **Step 3: Compile.**
- [ ] **Step 4: Commit** — `refactor(core): migrate HomeViewModel to BaseViewModel`

### Task 5: `DefaultCategoryViewModel`

**Files:** `zero-core/.../categories/DefaultCategoryViewModel.kt`, `zero-core/.../categories/CategoryComponent.kt`

- [ ] **Step 1: Migrate** — apply the recipe. Keep the entire `combine(...).collectLatest { … }` body verbatim; the only changes:
  - header `: BaseViewModel(dispatchers), CategoryViewModel`; constructor swaps `coroutineScope` for `private val dispatchers: DispatcherProvider`.
  - `override fun attach(): Closeable = Closeables.of { coroutineScope.launch { … } }` → `override fun attachOnMain() { scope.launch(dispatchers.io()) { … } }` (same inner body, including the `currencyPrimaryUseCase.getPrimaryCurrency()` call and the `combine`).
  - `perform`: `SelectCategory` → `scope.launch(dispatchers.main()) { onCategorySelectedHandler.onSelected(...) }`; `SelectTab` stays an inline `mutableState.update`.
  - Drop imports `CoroutineScope`, `Dispatchers`, `Closeables`, `java.io.Closeable`.
- [ ] **Step 2: DI** — `CategoryComponent.kt`: add `val dispatchers: DispatcherProvider` to `Dependencies`; pass it into `DefaultCategoryViewModel(...)` (provider at lines 118-124).
- [ ] **Step 3: Compile.**
- [ ] **Step 4: Commit** — `refactor(core): migrate CategoryViewModel to BaseViewModel`

### Task 6: `DefaultTransactionViewModel` (highest risk)

**Files:** `zero-core/.../transactions/DefaultTransactionViewModel.kt`, `zero-core/.../transactions/TransactionComponent.kt`, `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt`

- [ ] **Step 1: Migrate the VM — surgical, behavior-preserving.** Do **not** rewrite the flow logic. Changes only:
  - Imports: add `com.hluhovskyi.zero.common.BaseViewModel`, `com.hluhovskyi.zero.common.coroutines.DispatcherProvider`. Remove `CoroutineScope`, `Dispatchers`, `Closeables`, `java.io.Closeable` (keep `launch`).
  - Constructor: delete `coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO)`; add `private val dispatchers: DispatcherProvider` (place it last, before the removed scope param's position). Header: `) : BaseViewModel(dispatchers),\n    TransactionViewModel {`.
  - `attach()` (lines 154-297): rename to `override fun attachOnMain()`, drop the `Closeables.of { … }` wrapper, change the outer `coroutineScope.launch { … }` to `scope.launch(dispatchers.io()) { … }`. **Everything inside stays identical** (the nested `launch { transactionFilterUseCase.state.collect … }`, the `pagedTransactions`/`searchTransactions`/`combine`/`collectLatest`). No `return` — `attachOnMain` returns `Unit`.
  - `perform`: `LoadMore` and `DeleteSelected` use `coroutineScope.launch { … }` (IO work — emit to trigger / repository.delete) → `scope.launch(dispatchers.io()) { … }`. `SelectTransaction`/`DuplicateSelected` call handlers directly (no launch) — leave as-is. State-only branches unchanged.
- [ ] **Step 2: DI** — `TransactionComponent.kt`: add `val dispatchers: DispatcherProvider` to `Dependencies`; pass `dispatchers = dispatchers` into `DefaultTransactionViewModel(...)` (provider at lines 104-120).
- [ ] **Step 3: Fix the test's VM construction.** In `DefaultTransactionViewModelTest.kt`, the `createViewModel(coroutineScope: CoroutineScope, …)` helper (line ~417) injected `backgroundScope` into the VM. Replace that with a test `DispatcherProvider` whose dispatchers **share the `runTest` scheduler** so `advanceTimeBy`/`advanceUntilIdle`/`runCurrent` still drive the VM's internal scope (the 300ms search-debounce tests depend on this). Pattern (mirror `DefaultAccountViewModelTest.kt:26-27`, but bind the scheduler):

```kotlin
// inside each @Test using runTest { ... }
val dispatcher = UnconfinedTestDispatcher(testScheduler)
val dispatchers = object : DispatcherProvider {
    override fun io() = dispatcher
    override fun cpu() = dispatcher
    override fun main() = dispatcher
}
val viewModel = createViewModel(dispatchers, filter = ...)
```

Change `createViewModel(...)` to take `dispatchers: DispatcherProvider` instead of `coroutineScope: CoroutineScope` and pass `dispatchers = dispatchers` to `DefaultTransactionViewModel(...)`. Replace each `createViewModel(backgroundScope)` call accordingly. Keep `viewModel.attach()` usage; `advanceUntilIdle()` already drives the shared scheduler. (`UnconfinedTestDispatcher(testScheduler)` import: `kotlinx.coroutines.test.UnconfinedTestDispatcher`.)
- [ ] **Step 4: Run the Transaction test — expect PASS** (all existing cases, incl. debounce/pagination):
`./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.transactions.DefaultTransactionViewModelTest" 2>&1 | tail -25`
If a timing test hangs/fails, the dispatcher is not sharing `testScheduler` — fix that before proceeding (do not weaken assertions).
- [ ] **Step 5: Commit** — `refactor(core): migrate TransactionViewModel to BaseViewModel`

---

## Task 7: Provide the four tab components as scoped singletons

Build each tab component once at `@MainActivityScreenScope` with its existing handler wiring, distinguished by four private `@Qualifier`s. No `.refCounted()` decorator — idempotency now lives in `BaseViewModel`.

**Files:** `app/.../activity/screens/MainActivityScreenComponent.kt`

**Analogs:** qualifier → existing private `@ForMainActivity` (this file, ~line 89); handler wiring → copy verbatim from the current `homeNavigationEntry`/`budgetNavigationEntry`/`accountNavigationEntry`/`categoryNavigationEntry` lambdas. `@Named` is banned (NoNamedAnnotationDetector) — custom qualifiers only.

- [ ] **Step 1: Add four private qualifiers** next to `@ForMainActivity`: `@ForHomeTab`, `@ForBudgetTab`, `@ForAccountTab`, `@ForCategoryTab` (each `@Qualifier @Retention(AnnotationRetention.RUNTIME)`).
- [ ] **Step 2: Add one scoped provider per tab** returning `AttachableViewComponent`, each `@Provides @MainActivityScreenScope @ForXTab`. Build with the SAME handler chain the matching nav entry applies today (Home wires `welcomeComponentBuilder` + `@ForMainActivity transactionComponentBuilder` + `transactionFilterUseCase` + `DisplayConfig(showFab = true)`; Budget wires `onCategoryTapped`/`onOverActionTapped`; Account wires `onAddAccount`/`onAccountSelected`/`onEditAccount`; Category wires `onCategorySelected`/`onAddCategory`), then `.logging(logger).build()`. Use the injected scope `navigator`. Example (Account):

```kotlin
@Provides
@MainActivityScreenScope
@ForAccountTab
fun accountTabComponent(
    componentBuilder: AccountComponent.Builder,
    navigator: Navigator,
    logger: Logger,
): AttachableViewComponent = componentBuilder
    .onAddAccountHandler { navigator.navigateTo(Destinations.Account.Edit) }
    .onAccountSelectedHandler { accountId ->
        navigator.navigateTo(
            Destinations.Account.Item.Detail,
            Destinations.Account.Item.AccountId.withValue(accountId),
        )
    }
    .onEditAccountHandler { accountId ->
        navigator.navigateTo(
            Destinations.Account.Item.Edit,
            Destinations.Account.Item.AccountId.withValue(accountId),
        )
    }
    .logging(logger)
    .build()
```

(`.logging(logger)` on the Builder returns a `Buildable`; `.build()` realizes the shared `AttachableViewComponent`. Home/Budget/Category mirror their current entry wiring identically.)
- [ ] **Step 3: Commit** — `feat(app): provide tab components as scoped singletons`

---

## Task 8: Keep all four tabs warm from `MainActivityScreenComponent.attach()`

**Files:** `app/.../activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1** — add qualified abstract vals and merge in `attach()` (replace lines ~108-110):

```kotlin
protected abstract val feedbackComponent: FeedbackComponent

@get:ForHomeTab
protected abstract val homeTab: AttachableViewComponent

@get:ForBudgetTab
protected abstract val budgetTab: AttachableViewComponent

@get:ForAccountTab
protected abstract val accountTab: AttachableViewComponent

@get:ForCategoryTab
protected abstract val categoryTab: AttachableViewComponent

override fun attach(): Closeable = Closeables.merge(
    feedbackComponent.attach(),
    homeTab.attach(),
    budgetTab.attach(),
    accountTab.attach(),
    categoryTab.attach(),
)
```

Add import `com.hluhovskyi.zero.common.Closeables`.
- [ ] **Step 2: Compile** — `./gradlew :app:compileDebugKotlin 2>&1 | tail -15`
- [ ] **Step 3: Commit** — `feat(app): keep four bottom-bar tabs attached for the session`

---

## Task 9: Render shared instances in the tab nav entries; delete dead wrappers

**Files:** `app/.../activity/screens/MainActivityScreenComponent.kt`; delete `TransactionsScreen.kt`, `AccountsScreen.kt`, `CategoriesScreen.kt` (confirmed referenced only by these four entries).

- [ ] **Step 1: Rewrite the four entries** to inject the qualified shared instance and render via the non-retaining `AttachWithView()` overload (`fun <T : AttachableViewComponent> T.AttachWithView(...)`). Each becomes, e.g.:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun accountNavigationEntry(
    @ForAccountTab component: AttachableViewComponent,
    navigatorScope: NavigatorScope,
): NavigatorEntry = navigatorScope.composable(Destinations.Account.All) {
    component.AttachWithView()
}
```

Home/Budget/Category mirror this (Home + Budget keep `displayOption = NavigatorEntry.DisplayOption.FullyVisible`). Remove the per-navigation builds and now-unused imports the compiler flags (`TransactionScreen`/`AccountsScreen`/`CategoriesScreen`, plus builder/use-case imports no longer referenced — but keep any still used by Task 7 providers, e.g. `WelcomeComponent.Builder`, `TransactionFilterUseCase`, `DisplayConfig`). Ensure `com.hluhovskyi.zero.common.AttachWithView` is imported.
- [ ] **Step 2: Delete dead wrappers** — `git rm app/.../activity/screens/{TransactionsScreen,AccountsScreen,CategoriesScreen}.kt`
- [ ] **Step 3: Build the whole app** — `./gradlew :app:assembleDebug 2>&1 | tail -30` → BUILD SUCCESSFUL, no duplicate-binding errors (each tab component bound once, consumed by its entry + its abstract val; `DispatcherProvider` resolves for all migrated components).
- [ ] **Step 4: Commit** — `feat(app): render shared warm tab instances in nav entries`

---

## Task 10: Verification

- [ ] **Unit tests** — `./gradlew testDebugUnitTest 2>&1 | tail -25` → BUILD SUCCESSFUL. Confirm `RefCountedAttachableTest`, `BaseViewModelRefCountTest`, `DefaultTransactionViewModelTest`, `DefaultAccountViewModelTest`, `DefaultBudgetViewModel*Test` all pass.
- [ ] **Lint** — `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20` → no new errors (no `NoNamedAnnotationDetector` hit — we use custom qualifiers).
- [ ] **Format** — `./gradlew spotlessApply` (AGENTS.md rule #2; CI gates on `spotlessCheck`). Commit any reformatting.
- [ ] **On-device UI** (REQUIRED — runtime behavior change): `./scripts/emulator/acquire`, install, then `zero-project:android-ui-inspector`:
  1. Launch; let Home settle.
  2. Switch Home → Budget → Account → Category and back, several times. After each switch confirm via inspector/screen capture that the destination shows **populated** content on the first observed frame — no empty-state flash (e.g. Budget's `EmptyBudgetCallout`), no list-height jump.
  3. Sanity: open a parameterized destination (tap a transaction → edit; open a category/account detail) and back out — confirm those still build per-navigation and behave normally.
  4. Rotate on each tab once — no crash (guards the ZERO-2 lifetime invariant).
  5. Smoke the migrated VMs: add a transaction, run a search (debounce), scroll to trigger LoadMore, toggle a category tab — confirm correct behavior (guards the threading migration).
- [ ] **Commit** any verification fixups.

---

## Self-Review Notes

- **Spec coverage:** T1 `RefCountedAttachable` (+test); T2 `BaseViewModel` refcount (+test, +doc); T3-6 migrate Welcome/Home/Category/Transaction onto `BaseViewModel` (+DI, + Transaction test fix); T7 scoped singleton tab providers + qualifiers; T8 keep-warm `attach()`; T9 entries render shared instances + delete dead wrappers; T10 build/test/lint/spotless/UI/rotation. All decisions covered.
- **Type consistency:** providers return `AttachableViewComponent`; abstract vals + entry params consume `@ForXTab AttachableViewComponent`. Migrated VMs: `: BaseViewModel(dispatchers), <ViewModel>`; constructors take `dispatchers: DispatcherProvider`; their components' `Dependencies` gain `val dispatchers: DispatcherProvider`. `RefCountedAttachable(onAttach: () -> Closeable)` matches its use in `BaseViewModel`.
- **Behavior preservation:** migrations launch subscriptions on `dispatchers.io()` (same threading as the old private IO scopes); flow logic copied verbatim; `perform` dispatcher intent preserved.
- **Out of scope (unchanged):** `DefaultBottomBarViewModel` `clearBackStack = true`; no `StateFlow`/`collectAsStateWithLifecycle`; non-tab destinations + Settings stay lazy.
```
