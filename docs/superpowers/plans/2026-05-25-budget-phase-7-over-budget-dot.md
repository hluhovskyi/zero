# Budget Phase 7 — Over-Budget Notification Dot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a red dot on the Budget tab in the bottom navigation whenever at least one expense category is over its set budget for the current month.

**Architecture:** Reuse the existing `BudgetUseCase` (no new use case). Add a focused `observeAnyOver(type): Flow<Boolean>` method that resolves the current month and runs a single `BudgetQueryUseCase.query` + over-check — keeping current-month resolution and the over-budget check in the domain layer (the heavy `observe()` with its previous/trailing queries is not reused). `BudgetUseCase` is provided at `@ActivityScope` via a `BudgetComponent.useCase(...)` companion factory (mirroring the existing `BudgetComponent.queryUseCase(...)`) so `BottomBarComponent` can consume it. `BottomBarViewModel.Item` gains `hasAlert: Boolean`, set only on the Budget tab; `BottomBarViewProvider` renders the dot per the design's `NavBadge`.

**Tech Stack:** Kotlin, Dagger, Jetpack Compose, kotlinx.coroutines Flow, JUnit, Mockito, Compose UI test.

**Design spec (from `Components.jsx` `NavBadge`):** 9.dp circle, color = `ZeroTheme.colors.error`, anchored top-right of the 24.dp icon at offset (x = +3.dp, y = −3.dp), with a 2.dp halo ring in the nav surface color (`surfaceContainerLowest`) so it reads cleanly over the active pill. Over-budget condition: a category with a set budget whose `spent > budgeted` (unset categories, `budgetId == null`, never count).

---

### Task 1: Add `observeAnyOver` to `BudgetUseCase` + unit tests

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/budget/BudgetUseCase.kt` — add method to interface + `Noop`.
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/budget/DefaultBudgetUseCase.kt` — implement.
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/budget/DefaultBudgetUseCaseTest.kt` — add cases (mirror existing Mockito + `row(...)` helper idiom in that file).

- [ ] **Step 1: Write failing tests** in `DefaultBudgetUseCaseTest.kt` (use the existing `useCase()`, `periodResolver`, and `row(...)` helpers; `observeAnyOver` only queries the current period, so only the `currentStart, currentEnd` query needs stubbing):

```kotlin
@Test
fun `observeAnyOver emits true when a set budget is over spent`() = runTest {
    whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
        flowOf(listOf(row("c1", budgetId = "b1", budgeted = "100", spent = "150"))),
    )
    assertTrue(useCase().observeAnyOver(type).first())
}

@Test
fun `observeAnyOver emits false when set budgets are within limit`() = runTest {
    whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
        flowOf(listOf(row("c1", budgetId = "b1", budgeted = "100", spent = "80"))),
    )
    assertFalse(useCase().observeAnyOver(type).first())
}

@Test
fun `observeAnyOver ignores unset categories even when spent is positive`() = runTest {
    whenever(budgetQueryUseCase.query(currentStart, currentEnd)).thenReturn(
        flowOf(listOf(row("u1", budgetId = null, budgeted = "0", spent = "999"))),
    )
    assertFalse(useCase().observeAnyOver(type).first())
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultBudgetUseCaseTest*"`
Expected: FAIL — `observeAnyOver` unresolved.

- [ ] **Step 3: Add to the `BudgetUseCase` interface** (after `observe(...)`):

```kotlin
/**
 * Emits true when at least one expense category with a set budget is over budget for the
 * current month. Lightweight relative to [observe] — a single current-period query, no
 * previous/trailing windows. Drives the over-budget dot on the Budget tab.
 */
fun observeAnyOver(type: BudgetType = BudgetType.EXPENSE): Flow<Boolean>
```

and to `Noop`:

```kotlin
override fun observeAnyOver(type: BudgetType): Flow<Boolean> = emptyFlow()
```

- [ ] **Step 4: Implement in `DefaultBudgetUseCase`** (the `type` param is accepted for parity with `observe`; the current `BudgetQueryUseCase.query` is EXPENSE-only, so it is not yet threaded further — matching how `observe` treats `type`):

```kotlin
override fun observeAnyOver(type: BudgetType): Flow<Boolean> {
    val current = periodFor(0)
    return budgetQueryUseCase.query(current.start, current.end).map { rows ->
        rows.any { it.budgetId != null && it.spent > it.budgeted }
    }
}
```

Add imports as needed: `kotlinx.coroutines.flow.map`. (`periodFor(0)` already exists and returns the current month via `PeriodResolver`.)

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultBudgetUseCaseTest*"`
Expected: PASS (existing + 3 new).

- [ ] **Step 6: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/budget/BudgetUseCase.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/budget/DefaultBudgetUseCase.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/budget/DefaultBudgetUseCaseTest.kt
git commit -m "budget(phase 7): add BudgetUseCase.observeAnyOver for current month"
```

---

### Task 2: Provide `BudgetUseCase` at `@ActivityScope`

**Files:**
- Modify: `zero-core/.../budget/BudgetComponent.kt` — add a `useCase(...)` factory to the `companion object`, mirroring the existing `queryUseCase(...)`.
- Modify: `app/.../activity/ActivityComponent.kt` — add `@Provides @ActivityScope fun budgetUseCase(...)`.
- Modify: `app/.../activity/screens/bottombar/BottomBarComponent.kt` — add `budgetUseCase` to `Dependencies` + pass into the `viewModel(...)` provider.

- [ ] **Step 1: Add factory to `BudgetComponent.companion`** (next to `queryUseCase`; `Clock`/`ZoneProvider` already imported):

```kotlin
fun useCase(
    budgetRepository: BudgetRepository,
    budgetQueryUseCase: BudgetQueryUseCase,
    clock: Clock,
    zoneProvider: ZoneProvider,
): BudgetUseCase = DefaultBudgetUseCase(
    budgetRepository = budgetRepository,
    budgetQueryUseCase = budgetQueryUseCase,
    periodResolver = DefaultPeriodResolver(clock = clock, zoneProvider = zoneProvider),
)
```

- [ ] **Step 2: Provide at `@ActivityScope`** in `ActivityComponent.Module` (add `import com.hluhovskyi.zero.budget.BudgetUseCase`):

```kotlin
@Provides
@ActivityScope
fun budgetUseCase(
    budgetRepository: BudgetRepository,
    budgetQueryUseCase: BudgetQueryUseCase,
    clock: Clock,
    zoneProvider: ZoneProvider,
): BudgetUseCase = BudgetComponent.useCase(
    budgetRepository = budgetRepository,
    budgetQueryUseCase = budgetQueryUseCase,
    clock = clock,
    zoneProvider = zoneProvider,
)
```

(`budgetRepository`, `budgetQueryUseCase`, `clock`, `zoneProvider` are all already in `ActivityComponent.Dependencies`.)

- [ ] **Step 3: Extend `BottomBarComponent`** — add to `Dependencies`:

```kotlin
val budgetUseCase: BudgetUseCase
```

add the param to the `viewModel(...)` `@Provides`, pass it into `DefaultBottomBarViewModel(...)`, and `import com.hluhovskyi.zero.budget.BudgetUseCase`.

- [ ] **Step 4:** Build deferred to end of Task 3 (VM constructor param lands there). Commit combined with Task 3.

---

### Task 3: Plumb `hasAlert` into `BottomBarViewModel`

**Files:**
- Modify: `app/.../bottombar/BottomBarViewModel.kt` — add `hasAlert: Boolean = false` to `Item`.
- Modify: `app/.../bottombar/DefaultBottomBarViewModel.kt` — accept `budgetUseCase`, combine `observeAnyOver()` with navigator state, set `hasAlert` on the Budget item only.

- [ ] **Step 1: Add field to `Item`**

```kotlin
data class Item(
    val id: Id.Known,
    val name: String,
    val icon: Image,
    val selected: Boolean,
    val hasAlert: Boolean = false,
)
```

- [ ] **Step 2: Update `DefaultBottomBarViewModel`** — add constructor param `private val budgetUseCase: BudgetUseCase` and rewrite `attach()` to combine the over-budget flow with navigator state:

```kotlin
override fun attach(): Closeable = Closeables.of {
    coroutineScope.launch {
        combine(
            navigator.state,
            budgetUseCase.observeAnyOver(),
        ) { navigatorState, isOver -> navigatorState to isOver }
            .collectLatest { (navigatorState, isOver) ->
                val bottomBarId = navigatorState.destination.toBottomBarId()
                mutableState.update { state ->
                    state.copy(
                        items = if (bottomBarId is Id.Known) {
                            bottomNavigationItems.map { item ->
                                item.copy(
                                    selected = item.id == bottomBarId,
                                    hasAlert = item.id == budgetId && isOver,
                                )
                            }
                        } else {
                            emptyList()
                        },
                    )
                }
            }
    }
}
```

Add imports: `kotlinx.coroutines.flow.combine`, `com.hluhovskyi.zero.budget.BudgetUseCase`.

- [ ] **Step 3: Build the app module**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (Dagger graph + VM compile).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/bottombar/ \
        app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/budget/BudgetComponent.kt
git commit -m "budget(phase 7): plumb hasAlert through bottom bar DI + VM"
```

---

### Task 4: Render the dot in `BottomBarViewProvider`

**Files:**
- Modify: `app/.../bottombar/BottomBarViewProvider.kt` — render the badge over the icon when `item.hasAlert`.

- [ ] **Step 1: Add the dot.** Wrap the existing `imageLoader.View(...)` in an inner `Box {}` so the badge anchors to the 24.dp icon (the outer `Box` is the 56×32 pill — anchoring there would misplace the dot):

```kotlin
Box {
    imageLoader.View(
        image = item.icon,
        modifier = Modifier.sizeIn(maxHeight = 24.dp),
        tint = iconTint,
    )
    if (item.hasAlert) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-3).dp)
                .size(9.dp)
                .background(ZeroTheme.colors.surfaceContainerLowest, CircleShape)
                .padding(2.dp)
                .background(ZeroTheme.colors.error, CircleShape)
                .semantics { contentDescription = "Over budget" },
        )
    }
}
```

Add imports: `androidx.compose.foundation.layout.offset`, `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`. The 2.dp `padding` between the two `background` layers produces the surface-colored halo ring from the design.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/bottombar/BottomBarViewProvider.kt
git commit -m "budget(phase 7): render over-budget dot on Budget tab icon"
```

---

### Task 5: E2E test + Budget tab dot robot helpers

**Files:**
- Modify: `app/src/androidTest/.../robots/BudgetRobot.kt` — add `assertBudgetTabAlertVisible()` / `assertBudgetTabAlertHidden()`.
- Modify: `app/src/androidTest/.../ZeroE2eTest.kt` — add one test.

- [ ] **Step 1: Add robot helpers** to `BudgetRobot` (mirror the `waitUntil` + `onAllNodesWithContentDescription` style already in the file):

```kotlin
fun assertBudgetTabAlertVisible(): BudgetRobot {
    composeRule.apply {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithContentDescription("Over budget").fetchSemanticsNodes().isNotEmpty()
        }
    }
    return this
}

fun assertBudgetTabAlertHidden(): BudgetRobot {
    composeRule.apply {
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithContentDescription("Over budget").fetchSemanticsNodes().isEmpty()
        }
    }
    return this
}
```

- [ ] **Step 2: Add the E2E test** to `ZeroE2eTest`. `seedBudgetOverScenario()` seeds Food (budget 50 / spent 100 → over). Raising the budget via the existing Increase flow (`+50.00` → budget 100 = spend → not over) clears the over state:

```kotlin
@Test
fun overBudgetShowsDotOnBudgetTabAndClearsWhenRaised() {
    seedBudgetOverScenario()
    onBudget()
        .assertBudgetTabAlertVisible()
        .tapIncrease()
        .pickSuggestion("+50.00")
        .confirm()
        .assertBudgetTabAlertHidden()
}
```

- [ ] **Step 3: Run the E2E test** (emulator acquired at verification time)

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hluhovskyi.zero.ZeroE2eTest#overBudgetShowsDotOnBudgetTabAndClearsWhenRaised 2>&1 | tail -30`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/
git commit -m "budget(phase 7): e2e — over-budget dot appears and clears"
```

---

### Task 6: Roadmap tracker — mark Budget feature complete

**Files:**
- Modify: `docs/superpowers/plans/2026-05-13-budget-roadmap.md`

- [ ] **Step 1:** Flip every shippable row in the "Phase Index & Status Tracker" to `✅` (Phases 1–6 to `✅ Merged (PR #N)` using their existing PR numbers; Phase 7 to `✅ Merged (PR #<this PR>)`). Leave Phase 8 (Income budgets) as out-of-scope. Add a one-line note at the top: `**Feature complete on <merge date>.** (Phase 8 income budgets remain future work — no design yet.)`

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-05-13-budget-roadmap.md
git commit -m "docs(budget): mark Budget feature complete (phase 7 shipped)"
```

---

## Self-Review Notes

- **Spec coverage:** reuse `BudgetUseCase` via new `observeAnyOver` (T1) + unit tests (T1) + `@ActivityScope` provision (T2) + `hasAlert` only on Budget tab (T3) + dot render matching design tokens (T4) + single E2E asserting visible→cleared (T5) + roadmap flip (T6). Phase 4 order untouched; no tap-nav change. ✅
- **No new use case:** reuses `BudgetUseCase`; no derivation in the VM (it consumes a `Flow<Boolean>`). ✅
- **Out of scope:** Income budgets (Phase 8) — not started.
- **No new hex:** dot uses `ZeroTheme.colors.error` and `surfaceContainerLowest`; no literals.
- **Type consistency:** `observeAnyOver(type): Flow<Boolean>`, `Item.hasAlert`, factory `useCase(budgetRepository, budgetQueryUseCase, clock, zoneProvider)`, contentDescription `"Over budget"` used identically across tasks.
