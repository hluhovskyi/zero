# Budget Phase 6 — Category Ordering + Long-Press Remove

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Roadmap: [budget-roadmap.md](2026-05-13-budget-roadmap.md). Prereq: Phases 1–5 merged.

**Goal:** Two finishing-touch features from the design:
1. **Sort unset categories by trailing 3-month average spend.** Set rows already sort by `spent/budgeted` desc (Phase 4); unset rows currently render in repo order. After this phase, the unset section starts with the categories the user spends most on — matches user goal #10.
2. **Long-press a `BudgetCard` → remove from budget for the current period.** Confirmation sheet matches design lines 1122–1147; deletes (soft) the `BudgetEntity` for that `(categoryId, period, type)` so the row drops to the "unset" section.

**Architecture:** A new `BudgetCategoryOrderingUseCase` (zero-api + zero-core) returns ordering metadata. `BudgetCard` gains an `onLongPress` callback. ViewModel adds a confirmation-modal slice of state.

---

## Files

### New — zero-api
- `zero-api/.../budget/BudgetCategoryOrderingUseCase.kt`

### New — zero-core
- `zero-core/.../budget/DefaultBudgetCategoryOrderingUseCase.kt`

### Modified — zero-core
- `BudgetViewModel.kt` — depend on `BudgetCategoryOrderingUseCase`, add `Action.LongPressCategory`, `Action.ConfirmRemove`, `Action.CancelRemove`
- `BudgetViewProvider.kt` — long-press detector on cards, confirmation bottom sheet
- `BudgetComponent.kt` — provide the new use case
- `BudgetCard.kt` — accept `onLongPress` callback

---

## Task 1: `BudgetCategoryOrderingUseCase`

**Files:**
- Create: `zero-api/.../budget/BudgetCategoryOrderingUseCase.kt`
- Create: `zero-core/.../budget/DefaultBudgetCategoryOrderingUseCase.kt`

- [ ] **Step 1: Interface**

```kotlin
interface BudgetCategoryOrderingUseCase {
    // Returns ordered category ids (highest avg-spend first). New categories sort last.
    fun query(referencePeriodStart: LocalDate): Flow<List<Id.Known>>

    object Noop : BudgetCategoryOrderingUseCase {
        override fun query(referencePeriodStart: LocalDate): Flow<List<Id.Known>> = emptyFlow()
    }
}
```

- [ ] **Step 2: Default impl**

```kotlin
internal class DefaultBudgetCategoryOrderingUseCase(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
) : BudgetCategoryOrderingUseCase {

    override fun query(referencePeriodStart: LocalDate): Flow<List<Id.Known>> {
        // Trailing 3 calendar months: [start-3, start) inclusive
        val from = referencePeriodStart.minus(3, DateTimeUnit.MONTH)
        val to = referencePeriodStart.minus(1, DateTimeUnit.DAY)

        return combine(
            categoriesQueryUseCase.query().onStartWithEmptyList(),
            categorySpendingUseCase.query(CategorySpendingUseCase.Period.Between(from, to)).onStartWithEmptyList(),
        ) { categories, spendingRows ->
            val expense = categories.filter { it.type == CategoryType.EXPENSE }
            val avgByCategory: Map<Id.Known, Amount> = spendingRows.associate { row ->
                row.categoryId to row.totalAmount.div(3)  // 3-month avg
            }
            val (withHistory, withoutHistory) = expense.partition { it.id in avgByCategory.keys }
            withHistory.sortedByDescending { avgByCategory[it.id] ?: Amount.zero() }.map { it.id } +
                withoutHistory.sortedBy { it.name }.map { it.id }   // new categories: alphabetical
        }
    }
}
```

- [ ] **Step 3: Tests** — `DefaultBudgetCategoryOrderingUseCaseTest.kt`:
  - No spending history → categories sorted alphabetically.
  - Mixed history → categories with spending come first, sorted by avg desc; categories without history sorted alphabetically after.
  - INCOME categories filtered out.

- [ ] **Step 4: Provide at `@ApplicationScope`** and add to `BudgetComponent.Dependencies`.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): BudgetCategoryOrderingUseCase — trailing 3-month avg"
```

---

## Task 2: Wire ordering into `BudgetViewModel`

**Files:**
- Modify: `DefaultBudgetViewModel.kt`

- [ ] **Step 1:** Inject the new use case.

- [ ] **Step 2: Merge ordering into existing combine** — the `attach()` block currently combines `categoriesQueryUseCase`, `budgetRepository.query(ForPeriod(current))`, `categorySpendingUseCase.query(Period.Between(current))`. Add a 4th source: `budgetCategoryOrderingUseCase.query(currentPeriodStart)`. After computing `sorted` (Phase 4 logic), reorder the **unset** segment using the ordering map:

```kotlin
val unsetOrdered = orderingIds
    .mapNotNull { id -> unset.firstOrNull { it.categoryId == id } }
    .let { ordered ->
        // any unset row not in orderingIds (shouldn't happen, but safe) goes last
        ordered + unset.filter { row -> orderingIds.none { it == row.categoryId } }
    }
val final = active.filter { it.isOver } +
    active.filter { !it.isOver }.sortedByDescending { it.pct } +
    unsetOrdered
```

- [ ] **Step 3: Tests** — add cases to `DefaultBudgetViewModelSortTest.kt` for ordering being applied to the unset segment.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(budget): order unset categories by 3-month avg spend"
```

---

## Task 3: Long-press detector + confirm-remove modal

**Files:**
- Modify: `BudgetCard.kt`, `BudgetViewModel.kt`, `BudgetViewProvider.kt`

- [ ] **Step 1: `BudgetCard` long-press**

Add parameter `onLongPress: () -> Unit = {}`. Use Compose `combinedClickable`:

```kotlin
.combinedClickable(
    interactionSource = ...,
    indication = ripple(),
    onClick = onTap,
    onLongClick = onLongPress,
)
```

(`combinedClickable` is `@ExperimentalFoundationApi` — annotate the function or opt in at file level. Pattern already used in `TransactionRowView` if exists; otherwise this is the first use.)

- [ ] **Step 2: ViewModel actions + state**

```kotlin
data class State(
    // ...existing...
    val removeConfirm: Id.Known? = null,   // category pending confirm
)

sealed interface Action {
    // ...existing...
    data class LongPressCategory(val categoryId: Id.Known) : Action
    object ConfirmRemove : Action
    object CancelRemove : Action
}
```

- [ ] **Step 3: `perform()`**

- `LongPressCategory(id)` → `state.copy(removeConfirm = id)`. Only valid for rows with `budgetId != null` — guard.
- `ConfirmRemove` → look up the row's `budgetId`, call `budgetRepository.delete(budgetId)`, clear `removeConfirm`, dispatch toast "Removed {name} from budget".
- `CancelRemove` → clear `removeConfirm`.

- [ ] **Step 4: ViewProvider confirmation modal**

Design lines 1122–1147:

```
if (state.removeConfirm != null):
    val row = state.budgeted.first { it.categoryId == state.removeConfirm }
    BottomSheet(onDismiss = { dispatch(CancelRemove) }):
        DragHandle()
        Row (gap 14, marginBottom 20):
            CategoryIcon(row.colorScheme, size 44)
            Column { Text("Remove ${row.categoryName}?", 17sp/ExtraBold, OnSurface)
                     Text("This removes the budget limit for this month only.", 13sp/Normal, OnSurfaceVariant) }
        Row (gap 10):
            SecondaryButton("Cancel", weight 1f, onClick = CancelRemove)
            DangerButton("Remove", weight 1f, background = Error, onClick = ConfirmRemove)
```

- [ ] **Step 5: Wire `BudgetCard.onLongPress`** in `BudgetViewProvider`:

```kotlin
BudgetCard(
    row = row,
    onTap = { dispatch(TapCategory(row.categoryId)) },
    onLongPress = { dispatch(LongPressCategory(row.categoryId)) },
    onReallocate = { dispatch(TapReallocate(row.categoryId)) },
    onIncrease = { dispatch(TapIncrease(row.categoryId)) },
)
```

- [ ] **Step 6: Tests**

`DefaultBudgetViewModelRemoveTest.kt`:
- `LongPressCategory` for set category → `removeConfirm` set.
- `LongPressCategory` for unset category → no change.
- `ConfirmRemove` calls `budgetRepository.delete(budgetId)` and clears `removeConfirm`.
- `CancelRemove` clears `removeConfirm` without calling delete.

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(budget): long-press to remove budget for current period"
```

---

## Task 4: Manual verification + PR

- [ ] **Step 1:** Open Budget for a period with no budgets. Confirm the "Set spending limits" list orders categories by 3-month average (e.g. Groceries, Food, Transport before Entertainment, Health for a typical user).
- [ ] **Step 2:** Tap a categorized budget card. Hold for ~500ms → remove confirmation sheet appears.
- [ ] **Step 3:** Tap "Remove" → toast "Removed {name} from budget", row drops to the unset section, position determined by ordering use case.
- [ ] **Step 4:** Confirm switching periods (Phase 1's MonthSelector) shows the budget still exists for other periods (only the current-period row was soft-deleted).
- [ ] **Step 5: UI inspector + tests + lint.**
- [ ] **Step 6: PR**

Update Phase 6 in [roadmap status tracker](2026-05-13-budget-roadmap.md#phase-index--status-tracker).

---

## Self-Review

- [ ] User goal #10 (correct category ordering) covered with 3-month-avg.
- [ ] Remove is period-scoped (other periods' budgets intact).
- [ ] No regression of set-row sorting (over → in-progress by pct).
- [ ] All `BudgetCard` callers from Phase 5 still compile (`onLongPress` has a default no-op so adding the param is backward-compatible).
