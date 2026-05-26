# Budget Phase 7 — Over-Budget Notification Dot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a red dot on the Budget tab in the bottom navigation whenever at least one expense category is over its set budget for the current month.

**Architecture:** A new `BudgetOverAnyUseCase` (interface in `zero-api`, impl in `zero-core`) observes `BudgetQueryUseCase` for the current month and emits `Boolean`. It's provided at `@ActivityScope` so `BottomBarComponent` can consume it. `BottomBarViewModel.Item` gains `hasAlert: Boolean`, set only on the Budget tab; the `BottomBarViewProvider` renders the dot per the design's `NavBadge`.

**Tech Stack:** Kotlin, Dagger, Jetpack Compose, kotlinx.coroutines Flow, JUnit, Compose UI test.

**Design spec (from `Components.jsx` `NavBadge`):** 9.dp circle, color = `ZeroTheme.colors.error`, anchored top-right of the 24.dp icon at offset (x = +3.dp, y = −3.dp), with a 2.dp halo ring in the nav surface color (`surfaceContainerLowest`) so it reads cleanly over the active pill. Over-budget condition: a category with a set budget whose `spent > budgeted`.

---

### Task 1: `BudgetOverAnyUseCase` interface + impl + unit test

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/budget/BudgetOverAnyUseCase.kt` (model after `BudgetQueryUseCase.kt` — interface + `Noop` object, same package)
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/budget/DefaultBudgetOverAnyUseCase.kt` (model after `DefaultBudgetQueryUseCase.kt` — internal class, constructor injection)
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/budget/DefaultBudgetOverAnyUseCaseTest.kt` (model after `DefaultBudgetQueryUseCaseTest.kt`)

- [ ] **Step 1: Write the failing test** in `DefaultBudgetOverAnyUseCaseTest.kt`

Use a fake `BudgetQueryUseCase` returning a fixed list, and a `PeriodResolver` test double (or fake `Clock`/`ZoneProvider` like the existing query test uses). Build `Budgeted` rows with the same helper shape as `DefaultBudgetQueryUseCaseTest`. Cases:

```kotlin
@Test
fun `emits true when a set budget is over spent`() = runTest {
    val useCase = DefaultBudgetOverAnyUseCase(
        budgetQueryUseCase = fakeQuery(listOf(budgeted(budgetId = Id.Known("b"), budgeted = "100", spent = "150"))),
        periodResolver = fixedPeriodResolver(),
    )
    assertThat(useCase.observe().first()).isTrue()
}

@Test
fun `emits false when set budgets are within limit`() = runTest {
    val useCase = DefaultBudgetOverAnyUseCase(
        budgetQueryUseCase = fakeQuery(listOf(budgeted(budgetId = Id.Known("b"), budgeted = "100", spent = "80"))),
        periodResolver = fixedPeriodResolver(),
    )
    assertThat(useCase.observe().first()).isFalse()
}

@Test
fun `ignores unset categories even when spent is positive`() = runTest {
    val useCase = DefaultBudgetOverAnyUseCase(
        budgetQueryUseCase = fakeQuery(listOf(budgeted(budgetId = null, budgeted = "0", spent = "999"))),
        periodResolver = fixedPeriodResolver(),
    )
    assertThat(useCase.observe().first()).isFalse()
}
```

Match the assertion library, `runTest`, and fake/helper style already used in `DefaultBudgetQueryUseCaseTest.kt` — read it first and mirror it (do not introduce a new test idiom). `fixedPeriodResolver()` returns a `PeriodResolver` whose `currentMonth()` yields any fixed `(start, end)` pair; the fake query ignores the dates.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultBudgetOverAnyUseCaseTest*"`
Expected: FAIL — `DefaultBudgetOverAnyUseCase` / `BudgetOverAnyUseCase` unresolved.

- [ ] **Step 3: Write the interface** `BudgetOverAnyUseCase.kt`

```kotlin
package com.hluhovskyi.zero.budget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface BudgetOverAnyUseCase {

    /**
     * Emits true when at least one expense category with a set budget has spent more than its
     * budgeted amount for the current month. Drives the over-budget dot on the Budget tab.
     */
    fun observe(): Flow<Boolean>

    object Noop : BudgetOverAnyUseCase {
        override fun observe(): Flow<Boolean> = flowOf(false)
    }
}
```

- [ ] **Step 4: Write the impl** `DefaultBudgetOverAnyUseCase.kt`

```kotlin
package com.hluhovskyi.zero.budget

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultBudgetOverAnyUseCase(
    private val budgetQueryUseCase: BudgetQueryUseCase,
    private val periodResolver: PeriodResolver,
) : BudgetOverAnyUseCase {

    override fun observe(): Flow<Boolean> {
        val (start, end) = periodResolver.currentMonth()
        return budgetQueryUseCase.query(start, end).map { rows ->
            rows.any { it.budgetId != null && it.spent > it.budgeted }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "*DefaultBudgetOverAnyUseCaseTest*"`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/budget/BudgetOverAnyUseCase.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/budget/DefaultBudgetOverAnyUseCase.kt \
        zero-core/src/test/java/com/hluhovskyi/zero/budget/DefaultBudgetOverAnyUseCaseTest.kt
git commit -m "budget(phase 7): add BudgetOverAnyUseCase"
```

---

### Task 2: Wire `BudgetOverAnyUseCase` through DI

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/budget/BudgetComponent.kt` — add a `overAnyUseCase(...)` factory in the `companion object`, mirroring the existing `queryUseCase(...)` factory.
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt` — add `@Provides @ActivityScope fun budgetOverAnyUseCase(...)`.
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/bottombar/BottomBarComponent.kt` — add `budgetOverAnyUseCase` to `Dependencies` and pass it into the `viewModel(...)` provider.

- [ ] **Step 1: Add factory to `BudgetComponent.companion`** (next to `queryUseCase`):

```kotlin
fun overAnyUseCase(
    budgetQueryUseCase: BudgetQueryUseCase,
    clock: Clock,
    zoneProvider: ZoneProvider,
): BudgetOverAnyUseCase = DefaultBudgetOverAnyUseCase(
    budgetQueryUseCase = budgetQueryUseCase,
    periodResolver = DefaultPeriodResolver(clock = clock, zoneProvider = zoneProvider),
)
```

(`Clock` and `ZoneProvider` are already imported in `BudgetComponent.kt`.)

- [ ] **Step 2: Provide at `@ActivityScope`** in `ActivityComponent.Module` (add `import com.hluhovskyi.zero.budget.BudgetOverAnyUseCase`):

```kotlin
@Provides
@ActivityScope
fun budgetOverAnyUseCase(
    budgetQueryUseCase: BudgetQueryUseCase,
    clock: Clock,
    zoneProvider: ZoneProvider,
): BudgetOverAnyUseCase = BudgetComponent.overAnyUseCase(
    budgetQueryUseCase = budgetQueryUseCase,
    clock = clock,
    zoneProvider = zoneProvider,
)
```

(`budgetQueryUseCase`, `clock`, `zoneProvider` are all already declared in `ActivityComponent.Dependencies`.)

- [ ] **Step 3: Extend `BottomBarComponent`** — add to `Dependencies`:

```kotlin
val budgetOverAnyUseCase: BudgetOverAnyUseCase
```

and add the param to the `viewModel(...)` `@Provides`, passing it into `DefaultBottomBarViewModel(...)`. Add `import com.hluhovskyi.zero.budget.BudgetOverAnyUseCase`.

- [ ] **Step 4: Build to verify Dagger graph compiles** (full impl of the VM param comes in Task 3; for now add the constructor param in Task 3 — do Step 4 build at end of Task 3). Skip build here.

- [ ] **Step 5: Commit** (combined with Task 3 — DI + VM compile together).

---

### Task 3: Plumb `hasAlert` into `BottomBarViewModel`

**Files:**
- Modify: `app/.../bottombar/BottomBarViewModel.kt` — add `hasAlert: Boolean = false` to `Item`.
- Modify: `app/.../bottombar/DefaultBottomBarViewModel.kt` — accept `budgetOverAnyUseCase`, combine it with navigator state, set `hasAlert` on the Budget item only.

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

- [ ] **Step 2: Update `DefaultBottomBarViewModel`** — add constructor param `private val budgetOverAnyUseCase: BudgetOverAnyUseCase` and rewrite `attach()` to combine the over-budget flow with navigator state:

```kotlin
override fun attach(): Closeable = Closeables.of {
    coroutineScope.launch {
        combine(
            navigator.state,
            budgetOverAnyUseCase.observe(),
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

Add imports: `kotlinx.coroutines.flow.combine`, `com.hluhovskyi.zero.budget.BudgetOverAnyUseCase`.

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

- [ ] **Step 1: Add the dot** inside the inner icon `Box` (the one already wrapping `imageLoader.View`). The dot anchors to the icon, top-right, per the design:

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

Wrap the existing `imageLoader.View(...)` in an inner `Box {}` so the badge can `align(Alignment.TopEnd)` relative to the icon (the outer `Box` is the 56×32 pill area — anchoring there would misplace the dot). Add imports: `androidx.compose.foundation.layout.offset`, `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`. The 2.dp `padding` between the two `background` layers produces the surface-colored halo ring from the design.

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

- [ ] **Step 2: Add the E2E test** to `ZeroE2eTest`. `seedBudgetOverScenario()` seeds Food (budget 50 / spent 100 → over). Raising the budget via the existing Increase flow (`+50.00` → budget 100 = spend) clears the over state:

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

- [ ] **Step 1:** Flip every row in the "Phase Index & Status Tracker" to `✅` (Phases 1–6 to `✅ Merged (PR #N)` using their existing PR numbers; Phase 7 to `✅ Merged (PR #<this PR>)`). Leave Phase 8 (Income budgets) as `☐ Pending` / out-of-scope. Add a one-line note at the top of the file: `**Feature complete on 2026-05-25.**` (Phase 8 income budgets remain future work.)

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-05-13-budget-roadmap.md
git commit -m "docs(budget): mark Budget feature complete (phase 7 shipped)"
```

---

## Self-Review Notes

- **Spec coverage:** use case (T1) + unit test (T1) + `@ActivityScope` provision (T2) + `hasAlert` only on Budget tab (T3) + dot render matching design tokens (T4) + single E2E asserting visible→cleared (T5) + roadmap flip (T6). Phase 4 order untouched; no tap nav change. ✅
- **Out of scope:** Income budgets (Phase 8) — not started.
- **No new hex:** dot uses `ZeroTheme.colors.error` and `surfaceContainerLowest`; no literals.
- **Type consistency:** `observe(): Flow<Boolean>`, `Item.hasAlert`, factory `overAnyUseCase(budgetQueryUseCase, clock, zoneProvider)`, contentDescription `"Over budget"` used identically across tasks.
