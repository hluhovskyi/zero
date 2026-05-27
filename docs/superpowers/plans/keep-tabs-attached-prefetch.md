# Keep Bottom-Bar Tabs Attached + Prefetched Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the data-load "jump" when switching between the four bottom-bar tabs (Home, Budget, Account, Category) by keeping their components attached + prefetched at `MainActivityScreenScope`, with a refcounted `attach()`.

**Architecture:** Today each tab is destroyed on switch (`DefaultBottomBarViewModel` navigates with `clearBackStack = true`) and rebuilt + re-attached on return, re-running its async Room query — so it renders the empty default `State()` for a frame, then jumps to real data. Fix: build the four tab components once at `MainActivityScreenScope` and `attach()` them from `MainActivityScreenComponent.attach()` so their `StateFlow`s stay warm for the whole session. A refcounted-attach decorator lets the per-display `AttachWithView()` and the parent keep-warm `attach()` coexist without double-collection or premature teardown. The tab nav entries render the shared instances via the non-retaining `AttachWithView()` overload; `clearBackStack` no longer affects data freshness because the components live above the nav layer.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Room. Modules: `zero-core` (decorator), `app` (DI + nav wiring).

**Scope:** Part A only — no `StateFlow`/`collectAsStateWithLifecycle` change. Tabs only. ALL parameterized destinations (edit/detail/picker/bottom-sheet) and Settings stay lazy and untouched.

---

## ZERO-2 Safety (read before Task 2)

`MainActivityScreenComponent` is built **per composition** via `remember(navController, ...)` and attached with the **non-retaining** `T.AttachWithView()` overload (`MainActivityViewProvider.kt:49-59`), in lockstep with `rememberNavController`. Eager-attaching the tab components inside its `attach()` inherits that exact lifetime — their captured `navigator` is rebuilt with the component on configuration change and never goes stale. This is the structural reason the change is safe.

**Do NOT** introduce any Android-`ViewModel` retention of tab components (no `ComponentHolderViewModel`/retaining `(() -> Component).AttachWithView()` path for them). That is exactly what crashed in ZERO-2 (a retained component outliving its `NavController` → `NavHost.setGraph` crash). The fix here deliberately keeps tab lifetime == `MainActivityScreenComponent` lifetime == `navigator` lifetime.

---

## Task 1: Refcounted-attach decorator

Add a decorator so a single shared component's `attach()` is reference-counted: the underlying work starts once on the first attach and is torn down only when the last holder closes.

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/common/AttachableViewComponent.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/common/RefCountedComponentTest.kt` (create)

**Analog:** Model `RefCountedComponent` structurally on the existing `LoggingComponent` in the same file (same `AttachableViewComponent` delegation shape: passes through `tag` + `viewProvider`, wraps `attach()`). Model the test on `zero-core/.../feedback/InMemoryBreadcrumbsTest.kt` (plain JUnit, includes a concurrency case).

- [ ] **Step 1: Write the failing test**

Create `RefCountedComponentTest.kt`:

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

class RefCountedComponentTest {

    private class FakeComponent : AttachableViewComponent {
        override val tag: String = "fake"
        override val viewProvider: ViewProvider = ViewProvider { }
        val attachCount = AtomicInteger(0)
        val closeCount = AtomicInteger(0)
        var liveAttachments = 0
            private set

        override fun attach(): Closeable {
            attachCount.incrementAndGet()
            liveAttachments++
            return Closeable {
                closeCount.incrementAndGet()
                liveAttachments--
            }
        }
    }

    @Test
    fun `delegate attaches once on first attach and stays attached while count positive`() {
        val fake = FakeComponent()
        val refCounted = fake.refCounted()

        val first = refCounted.attach()
        val second = refCounted.attach()

        assertEquals(1, fake.attachCount.get())
        assertEquals(1, fake.liveAttachments)

        first.close()
        assertEquals(0, fake.closeCount.get())
        assertEquals(1, fake.liveAttachments)

        second.close()
        assertEquals(1, fake.closeCount.get())
        assertEquals(0, fake.liveAttachments)
    }

    @Test
    fun `re-attach after full release starts delegate again`() {
        val fake = FakeComponent()
        val refCounted = fake.refCounted()

        refCounted.attach().close()
        refCounted.attach()

        assertEquals(2, fake.attachCount.get())
        assertEquals(1, fake.liveAttachments)
    }

    @Test
    fun `double-close of one handle does not over-decrement`() {
        val fake = FakeComponent()
        val refCounted = fake.refCounted()

        val a = refCounted.attach()
        val b = refCounted.attach()

        a.close()
        a.close() // idempotent — must not drop b's reference

        assertEquals(1, fake.liveAttachments)
        b.close()
        assertEquals(0, fake.liveAttachments)
    }

    @Test
    fun `concurrent attach then close leaves delegate fully released`() = runBlocking {
        val fake = FakeComponent()
        val refCounted = fake.refCounted()

        val handles = (0 until 16).map {
            async(Dispatchers.Default) { refCounted.attach() }
        }.awaitAll()
        handles.map { async(Dispatchers.Default) { it.close() } }.awaitAll()

        assertEquals(0, fake.liveAttachments)
        assertEquals(1, fake.closeCount.get())
    }

    @Test
    fun `tag and viewProvider are delegated`() {
        val fake = FakeComponent()
        val refCounted = fake.refCounted()
        assertEquals(fake.tag, refCounted.tag)
        assertEquals(fake.viewProvider, refCounted.viewProvider)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.common.RefCountedComponentTest"`
Expected: FAIL — `refCounted` unresolved reference.

- [ ] **Step 3: Implement the decorator**

In `AttachableViewComponent.kt`, add the extension next to the existing `logging(...)` extensions, and the class next to `LoggingComponent`. Add imports `java.util.concurrent.atomic.AtomicBoolean` (already imports `AtomicReference`, `Closeable`).

```kotlin
fun AttachableViewComponent.refCounted(): AttachableViewComponent = RefCountedComponent(delegate = this)

/**
 * Reference-counts [attach] so a single shared component can be held by multiple callers
 * (e.g. a session-long keep-warm ref plus a per-display `AttachWithView`). The delegate is
 * attached on the first ref (0→1) and closed only when the last ref is released (1→0). Each
 * returned [Closeable] is idempotent.
 */
private class RefCountedComponent(
    private val delegate: AttachableViewComponent,
) : AttachableViewComponent {

    override val tag: String = delegate.tag
    override val viewProvider: ViewProvider = delegate.viewProvider

    private val lock = Any()
    private var count = 0
    private var closeable: Closeable = Closeables.empty()

    override fun attach(): Closeable {
        synchronized(lock) {
            if (count++ == 0) {
                closeable = delegate.attach()
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

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.common.RefCountedComponentTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/common/AttachableViewComponent.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/common/RefCountedComponentTest.kt
git commit -m "feat(core): add refcounted-attach decorator for shared components"
```

---

## Task 2: Provide the four tab components as scoped, refcounted singletons

Build each tab component once at `@MainActivityScreenScope` with its handler wiring applied, then `.logging(logger).refCounted()`. `refCounted()` is outermost so `logging` logs only real start/stop. Distinguish the four (all typed `AttachableViewComponent`) with four private `@Qualifier` annotations.

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

**Analogs:** Qualifier → the existing private `@ForMainActivity` in this same file. Handler wiring → copy verbatim from the current `homeNavigationEntry`/`budgetNavigationEntry`/`accountNavigationEntry`/`categoryNavigationEntry` lambdas (do not redesign the handlers). `@Named` is banned (NoNamedAnnotationDetector lint) — use custom qualifiers only.

- [ ] **Step 1: Add four private qualifiers**

Next to `@ForMainActivity` (around line 89), add:

```kotlin
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
private annotation class ForHomeTab

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
private annotation class ForBudgetTab

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
private annotation class ForAccountTab

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
private annotation class ForCategoryTab
```

- [ ] **Step 2: Add a scoped provider per tab in `Module`**

Each provider builds the component with the SAME handler chain the corresponding nav entry currently applies, then `.logging(logger).refCounted()`. Use the injected scope `navigator` (it is the identical instance the entry lambdas reference via `NavigatorScope.Context.navigator`). Add `import com.hluhovskyi.zero.common.refCounted`.

Home (mirror the wiring in `homeNavigationEntry`, lines ~296-328):

```kotlin
@Provides
@MainActivityScreenScope
@ForHomeTab
fun homeTabComponent(
    homeComponentBuilder: HomeComponent.Builder,
    welcomeComponentBuilder: WelcomeComponent.Builder,
    @ForMainActivity transactionComponentBuilder: TransactionComponent.Builder,
    transactionFilterUseCase: TransactionFilterUseCase,
    navigator: Navigator,
    logger: Logger,
): AttachableViewComponent = homeComponentBuilder
    .welcomeComponentBuilder(
        welcomeComponentBuilder
            .onImportSelectedHandler { navigator.navigateTo(Destinations.Import) }
            .onAddTransactionHandler { navigator.navigateTo(Destinations.Transaction.Edit) },
    )
    .transactionComponentBuilder(
        transactionComponentBuilder
            .onTransactionSelectHandler { transactionId ->
                navigator.navigateTo(
                    Destinations.Transaction.Item.Edit,
                    Destinations.Transaction.Item.TransactionId.withValue(transactionId),
                )
            }
            .onAddTransactionHandler { navigator.navigateTo(Destinations.Transaction.Edit) }
            .transactionFilterUseCase(transactionFilterUseCase)
            .displayConfig(DisplayConfig(showFab = true)),
    )
    .logging(logger)
    .build()
    .refCounted()
```

> **NOTE on build vs logging order:** the current entries call `.logging(logger)` on the **Builder** then `AttachWithView()` (which builds). `logging(Buildable)` returns a `Buildable`, so `.logging(logger).build()` yields the built `AttachableViewComponent`; then `.refCounted()` wraps it. Keep that order: `builder…​.logging(logger).build().refCounted()`. (`logging` has both a `Buildable` and an `AttachableViewComponent` overload — either reaches the same result; match the existing entry, which logs at the Builder layer.)

Budget (mirror `budgetNavigationEntry`, lines ~529-558):

```kotlin
@Provides
@MainActivityScreenScope
@ForBudgetTab
fun budgetTabComponent(
    componentBuilder: BudgetComponent.Builder,
    navigator: Navigator,
    logger: Logger,
): AttachableViewComponent = componentBuilder
    .onCategoryTappedHandler { categoryId, start, end ->
        navigator.navigateTo(
            Destinations.Budget.Edit,
            Destinations.Budget.Edit.CategoryId.withValue(categoryId),
            Destinations.Budget.Edit.PeriodStart.withValue(start.toString()),
            Destinations.Budget.Edit.PeriodEnd.withValue(end.toString()),
        )
    }
    .onOverActionTappedHandler { categoryId, start, end, mode ->
        navigator.navigateTo(
            Destinations.Budget.Over,
            Destinations.Budget.Over.CategoryId.withValue(categoryId),
            Destinations.Budget.Over.PeriodStart.withValue(start.toString()),
            Destinations.Budget.Over.PeriodEnd.withValue(end.toString()),
            Destinations.Budget.Over.InitialMode.withValue(mode?.name.orEmpty()),
        )
    }
    .logging(logger)
    .build()
    .refCounted()
```

Account (mirror `accountNavigationEntry`, lines ~615-637):

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
    .refCounted()
```

Category (mirror `categoryNavigationEntry`, lines ~428-449):

```kotlin
@Provides
@MainActivityScreenScope
@ForCategoryTab
fun categoryTabComponent(
    componentBuilder: CategoryComponent.Builder,
    navigator: Navigator,
    logger: Logger,
): AttachableViewComponent = componentBuilder
    .onCategorySelectedHandler { categoryId ->
        navigator.navigateTo(
            Destinations.Category.Item.Detail,
            Destinations.Category.Item.CategoryId.withValue(categoryId),
        )
    }
    .onAddCategoryHandler { type ->
        navigator.navigateTo(
            Destinations.Category.Edit,
            Destinations.Category.Edit.InitialType.withValue(type.name),
        )
    }
    .logging(logger)
    .build()
    .refCounted()
```

Add imports as needed: `com.hluhovskyi.zero.common.AttachableViewComponent` (already imported), `com.hluhovskyi.zero.common.refCounted`.

- [ ] **Step 2 verify: compile**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: compiles (entries still build their own components for now; that's removed in Task 4). If Dagger complains about duplicate bindings, that's expected until Task 4 removes the per-entry builds — proceed; full graph correctness is verified at Task 4's build.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat(app): provide tab components as scoped refcounted singletons"
```

---

## Task 3: Keep all four tabs warm from `MainActivityScreenComponent.attach()`

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Add qualified abstract vals + merge in `attach()`**

Replace the existing `feedbackComponent` field block + `attach()` (lines ~108-110):

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

Add import `com.hluhovskyi.zero.common.Closeables` (verify; `Closeable` is already imported).

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: compiles.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat(app): keep four bottom-bar tabs attached for the session"
```

---

## Task 4: Render shared instances in the tab nav entries; delete dead wrappers

Point the four tab entries at their shared singleton and render via the **non-retaining** `AttachWithView()` overload (`fun <T : AttachableViewComponent> T.AttachWithView(...)` in `AttachableViewComponent.kt` — DisposableEffect-based). Remove the per-navigation builds. This is what removes the duplicate Dagger bindings introduced in Task 2.

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`
- Delete: `app/src/main/java/com/hluhovskyi/zero/activity/screens/TransactionsScreen.kt`, `AccountsScreen.kt`, `CategoriesScreen.kt` (confirmed only referenced by these four entries)

- [ ] **Step 1: Rewrite the four entries**

`homeNavigationEntry` becomes:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun homeNavigationEntry(
    @ForHomeTab component: AttachableViewComponent,
    navigatorScope: NavigatorScope,
): NavigatorEntry = navigatorScope.composable(
    destination = Destinations.Home,
    displayOption = NavigatorEntry.DisplayOption.FullyVisible,
) {
    component.AttachWithView()
}
```

`budgetNavigationEntry`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun budgetNavigationEntry(
    @ForBudgetTab component: AttachableViewComponent,
    navigatorScope: NavigatorScope,
): NavigatorEntry = navigatorScope.composable(
    destination = Destinations.Budget.All,
    displayOption = NavigatorEntry.DisplayOption.FullyVisible,
) {
    component.AttachWithView()
}
```

`accountNavigationEntry`:

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

`categoryNavigationEntry`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun categoryNavigationEntry(
    @ForCategoryTab component: AttachableViewComponent,
    navigatorScope: NavigatorScope,
): NavigatorEntry = navigatorScope.composable(Destinations.Category.All) {
    component.AttachWithView()
}
```

Ensure `com.hluhovskyi.zero.common.AttachWithView` is imported (the non-retaining overload). Remove now-unused imports left by deleting the inline builds (e.g. `TransactionScreen`/`AccountsScreen`/`CategoriesScreen` imports, and any builder/usecase imports that are no longer referenced anywhere in the file — let the compiler flag unused ones; do not remove imports still used by Task 2 providers).

- [ ] **Step 2: Delete the dead wrappers**

```bash
git rm app/src/main/java/com/hluhovskyi/zero/activity/screens/TransactionsScreen.kt \
       app/src/main/java/com/hluhovskyi/zero/activity/screens/AccountsScreen.kt \
       app/src/main/java/com/hluhovskyi/zero/activity/screens/CategoriesScreen.kt
```

- [ ] **Step 3: Build the whole app (Dagger graph must resolve cleanly now)**

Run: `./gradlew :app:assembleDebug 2>&1 | tail -30`
Expected: BUILD SUCCESSFUL. No duplicate-binding errors (each tab component now bound exactly once, consumed by its entry + its abstract val).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(app): render shared warm tab instances in nav entries"
```

---

## Task 5: Verification

- [ ] **Step 1: Unit tests** — existing ViewModel tests must still pass (no behavior change), plus the new decorator test.

Run: `./gradlew testDebugUnitTest 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. Confirm `DefaultHomeViewModelTest`-equivalents, `DefaultBudgetViewModel*Test`, `DefaultAccountViewModelTest`, `DefaultTransactionViewModelTest`, and `RefCountedComponentTest` all pass.

- [ ] **Step 2: Lint** — no new errors.

Run: `./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20`
Expected: no new errors. In particular confirm no `NoNamedAnnotationDetector` hit (we used custom qualifiers, not `@Named`).

- [ ] **Step 3: On-device UI verification** — REQUIRED (runtime behavior change).

Acquire an emulator (`./scripts/emulator/acquire`), install, and invoke `zero-project:android-ui-inspector`. Steps:
1. Launch the app; let Home settle.
2. Switch Home → Budget → Account → Category, then back through them, several times.
3. After each switch, confirm via the inspector / screen capture that the destination shows **populated** content on the first observed frame — no empty-state callout flash (e.g. Budget's `EmptyBudgetCallout`), no list-height jump.
4. Sanity: open a parameterized destination (e.g. tap a transaction → edit) and back out — confirm those still build per-navigation and behave normally.
5. Rotate the device on each tab once — confirm no crash (guards the ZERO-2 lifetime invariant).

Expected: no flash/jump on tab switches; no crash on rotation.

- [ ] **Step 4: Commit any verification fixups** (only if needed).

---

## Self-Review Notes

- **Spec coverage:** Task 1 = refcount decorator (+test); Task 2 = scoped refcounted tab providers + qualifiers; Task 3 = keep-warm `attach()`; Task 4 = entries render shared instances + delete dead wrappers; Task 5 = build/test/lint/UI/rotation. All design points covered.
- **Type consistency:** providers return `AttachableViewComponent`; abstract vals + entry params consume `@ForXTab AttachableViewComponent`; decorator returns `AttachableViewComponent`. `refCounted()` / `logging()` / `AttachWithView()` signatures match `AttachableViewComponent.kt`.
- **Out of scope (unchanged):** `DefaultBottomBarViewModel` `clearBackStack = true` stays; no `StateFlow`/`collectAsStateWithLifecycle`; all non-tab destinations and Settings remain lazy.
