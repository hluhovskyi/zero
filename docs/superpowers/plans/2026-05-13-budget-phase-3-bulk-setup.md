# Budget Phase 3 — Bulk Setup + Copy-from-Previous-Period

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Roadmap: [budget-roadmap.md](2026-05-13-budget-roadmap.md). Prereq: Phases 1–2 merged.

**Goal:** Three new flows from the design that share data:
1. **`BudgetBulkSetupComponent`** — full-screen modal (`BulkSetupFlow` in `BudgetScreen.jsx` lines 80–271). Tap "Create budget" from the empty-state callout → all expense categories appear at once with inline-edit chips; the inline numpad slides up over the list when editing.
2. **"Copy from {prevPeriod}" cards** — both the screen-level surface (lines 1014–1031) and the inline banner inside bulk setup (lines 132–150). Replace existing limits if any (confirm sheet).
3. **Inline numpad auto-advance** on the main Budget screen — tap a category, set amount, "Next →" jumps to the next unset category (lines 1178–1238). User goal #2 ("when entered navigate to next category").

**Architecture:** New `BudgetBulkSetupComponent` full-screen destination. New `BulkBudgetSaveUseCase` (zero-api) that takes a `List<BudgetInsert>` and a period, performs atomic upsert (delete-then-insert for the period+type) for "copy from previous" so leftover budgets from prior bulk setup don't survive. Inline numpad reuses the `BudgetEditComponent` from Phase 2 — adapted into a new `BudgetInlineEdit` overlay or kept as a separate, much-simpler composable inside the Budget screen ViewProvider.

**Toast surface** — every successful save (Phase 2 onwards) shows a `BudgetToast` composable inside `BudgetViewProvider`. Phase 2 stubbed this with a no-op; Phase 3 turns it on by relaying `OnBudgetSavedHandler` events into a `Channel<String>` consumed by `BudgetViewModel.State.toastMessage`.

---

## Files

### New — zero-api
- `zero-api/.../budget/BulkBudgetSaveUseCase.kt` — interface; takes `period: (LocalDate, LocalDate)`, `type: BudgetType`, `entries: List<Pair<categoryId, amount>>`. Deletes any existing alive budgets matching `(period, type)` not in the new list, upserts the rest.

### New — zero-core
- `zero-core/.../budget/bulksetup/BudgetBulkSetupComponent.kt`
- `zero-core/.../budget/bulksetup/BudgetBulkSetupViewModel.kt`, `DefaultBudgetBulkSetupViewModel.kt`
- `zero-core/.../budget/bulksetup/BudgetBulkSetupViewProvider.kt`
- `zero-core/.../budget/bulksetup/DefaultBulkBudgetSaveUseCase.kt`
- `zero-core/.../budget/bulksetup/OnBulkBudgetSavedHandler.kt`

### Modified — zero-core
- `BudgetViewModel.kt` — add `Action.TapCreateBudget`, `Action.TapCopyFromPrevious`, `Action.ConfirmCopyOverwrite`, `Action.CancelCopyOverwrite`, plus inline-edit actions
- `BudgetViewProvider.kt` — show inline numpad overlay, copy-from-prev card on screen, toast surface
- `BudgetComponent.kt` — depends on `BulkBudgetSaveUseCase`

### Modified — app
- `Destinations.kt` — add `Destinations.Budget.BulkSetup` full-screen destination
- `MainActivityScreenComponent.kt` — bulksetup navigation entry
- `ApplicationComponent.kt` — provide `BulkBudgetSaveUseCase`

---

## Task 1: `BulkBudgetSaveUseCase`

**Files:**
- Create: `zero-api/.../budget/BulkBudgetSaveUseCase.kt`
- Create: `zero-core/.../budget/bulksetup/DefaultBulkBudgetSaveUseCase.kt`

- [ ] **Step 1: Interface**

```kotlin
interface BulkBudgetSaveUseCase {

    suspend fun save(
        from: LocalDate,
        to: LocalDate,
        type: BudgetType,
        entries: List<Entry>,
    )

    data class Entry(val categoryId: Id.Known, val amount: Amount)

    object Noop : BulkBudgetSaveUseCase {
        override suspend fun save(from: LocalDate, to: LocalDate, type: BudgetType, entries: List<Entry>) = Unit
    }
}
```

- [ ] **Step 2: Default impl**

```kotlin
internal class DefaultBulkBudgetSaveUseCase(
    private val budgetRepository: BudgetRepository,
) : BulkBudgetSaveUseCase {

    override suspend fun save(from: LocalDate, to: LocalDate, type: BudgetType, entries: List<BulkBudgetSaveUseCase.Entry>) {
        val existing = budgetRepository.query(BudgetRepository.Criteria.ForPeriod(from, to, type))
            .firstOrNull().orEmpty()
        val newCategoryIds = entries.map { it.categoryId }.toSet()
        // Delete budgets that exist for the period but are no longer in the new set
        existing.filter { it.categoryId !in newCategoryIds }.forEach { budgetRepository.delete(it.id) }
        // Upsert entries — repository will replace by primary key if id exists, else generate
        budgetRepository.insert(
            entries.map { entry ->
                val existingRow = existing.firstOrNull { it.categoryId == entry.categoryId }
                BudgetRepository.BudgetInsert(
                    id = existingRow?.id ?: Id.Unknown,
                    categoryId = entry.categoryId,
                    type = type,
                    amount = entry.amount,
                    periodStart = from,
                    periodEnd = to,
                )
            },
        )
    }
}
```

- [ ] **Step 3: Tests** in `DefaultBulkBudgetSaveUseCaseTest.kt` — assert delete called for removed categories, assert insert called with merged ids for surviving ones, assert empty entries deletes all.

- [ ] **Step 4: Provide at `@ApplicationScope`** in `ApplicationComponent.kt`. Add to `ActivityComponent.Dependencies` and `BudgetComponent.Dependencies`.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): BulkBudgetSaveUseCase for atomic period upsert"
```

---

## Task 2: Wire toast surface (Phase 2 follow-up)

**Files:**
- Modify: `BudgetViewModel.kt`, `BudgetViewProvider.kt`, `BudgetComponent.kt`

- [ ] **Step 1: Add `toastMessage: String? = null` to `BudgetViewModel.State`**

- [ ] **Step 2: Add `Action.ToastShown` and `Action.ShowToast(String)`** — `ShowToast` dispatched by `OnBudgetSavedHandler` callback wired through the Builder; `ToastShown` clears it after the auto-dismiss timer.

- [ ] **Step 3: Wire `OnBudgetSavedHandler` into `BudgetComponent.Builder`** — `@BindsInstance` with default `Noop`. `MainActivityScreenComponent.budgetNavigationEntry` provides:

```kotlin
.onBudgetSavedHandler { name, amount ->
    componentBuilder.viewModel.perform(BudgetViewModel.Action.ShowToast("Budget set for $name"))
}
```

Note: handler crosses component boundaries. Simplest implementation = expose a top-level `MutableSharedFlow<String>` from `BudgetComponent.Module` that both `BudgetViewModel.attach()` collects and `MainActivityScreenComponent.Module` produces a `OnBudgetSavedHandler` impl that emits into. Pattern is identical to how `TransactionFilterUseCase` round-trips through navigation — see [Navigation §Returning a Result from a Screen](../agents/navigation.md#returning-a-result-from-a-screen).

- [ ] **Step 4: `BudgetToast(message, modifier)` composable** in `BudgetViewProvider.kt`. Style from `BudgetScreen.jsx` lines 1083–1096:
  - Position: above bottom nav, padding 16dp horizontal, bottom 88dp from bottom of column
  - Background: `PrimaryContainer`, radius 14dp, padding 14/18
  - Icon: `Icons.Filled.CheckCircle`, tint `#5DDBA8`, size 18dp
  - Text: 14sp/SemiBold, `#fff`
  - Animation: `AnimatedVisibility` with `fadeIn + slideInVertically` (offset +12dp). Auto-dismiss after 2800ms (matches design).

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): toast surface on Budget screen after save"
```

---

## Task 3: "Copy from {prevPeriod}" card on Budget screen

**Files:**
- Modify: `BudgetViewProvider.kt`
- Modify: `BudgetViewModel.kt`

- [ ] **Step 1: ViewModel state**

```kotlin
data class State(
    // ...existing fields...
    val copyConfirmVisible: Boolean = false,
)

sealed interface Action {
    object TapCopyFromPrevious : Action
    object ConfirmCopy : Action
    object CancelCopy : Action
}
```

- [ ] **Step 2: `perform(TapCopyFromPrevious)`** —
  - If `state.budgeted.none { it.budgetId != null }` (current period has no budgets), call `BulkBudgetSaveUseCase.save(...)` directly with the previous period's amounts (the ViewModel already has `previousPeriodBudgets`).
  - Else set `copyConfirmVisible = true`.

- [ ] **Step 3: `perform(ConfirmCopy)`** — same call as above, then `state.copy(copyConfirmVisible = false)` plus a toast "Copied {N} categories from {prevLabel}".

- [ ] **Step 4: Compose** — `CopyFromPreviousCard` (lines 1014–1031 of design):

```
Row (background = SurfaceContainerLow, radius 14dp, padding 12/16, gap 12, clickable):
  Box (34dp, radius 10, background SurfaceContainer, center):
    Icon(Icons.Filled.SyncAlt or SwapHoriz, 18dp, PrimaryContainer)
  Column (weight 1):
    Text("Copy from {prevPeriodLabel}", 14sp/Bold, Primary)
    Text("{N} categories · {total}", 12sp/Normal, OnSurfaceVariant)
  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, 18dp, OnSurfaceVariant)
```

Renders when `state.previousPeriodBudgets.isNotEmpty()`. Always visible (both empty-state and populated-state).

- [ ] **Step 5: Confirm overwrite bottom sheet** — design lines 1149–1176. Reuse Material `ModalBottomSheet` or a Compose `Dialog`. Drag handle + warning row (icon, "Replace existing budget?", subtitle) + two CTA buttons (Cancel / Replace).

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(budget): copy-from-previous-period flow on Budget screen"
```

---

## Task 4: Scaffold `BudgetBulkSetupComponent`

Run `superpowers/scaffold-feature` for name `BudgetBulkSetup`, package `budget/bulksetup`, handlers `back`, `saved`.

- [ ] **Step 1: Dependencies**

```kotlin
interface Dependencies {
    val imageLoader: ImageLoader
    val amountFormatter: AmountFormatter
    val categoriesQueryUseCase: CategoriesQueryUseCase  // for icon/color hydration
    val budgetQueryUseCase: BudgetQueryUseCase          // for current + previous period
    val budgetRepository: BudgetRepository
    val bulkBudgetSaveUseCase: BulkBudgetSaveUseCase
}
```

- [ ] **Step 2: `@BindsInstance`**

```kotlin
@BindsInstance fun periodStart(...): Builder
@BindsInstance fun periodEnd(...): Builder
@BindsInstance fun onBulkSavedHandler(handler: OnBulkBudgetSavedHandler): Builder
@BindsInstance fun onBackHandler(handler: OnBackHandler): Builder
```

- [ ] **Step 3: `State`**

```kotlin
data class State(
    val periodLabel: String = "",
    val categories: List<CategoryRow> = emptyList(),
    val previousPeriodTotal: Amount = Amount.zero(),
    val previousPeriodCount: Int = 0,
    val editingCategoryId: Id.Known? = null,  // null = not editing
    val editingAmountText: String = "0",      // raw NumPad input
    val toastMessage: String? = null,
)

data class CategoryRow(
    val categoryId: Id.Known,
    val name: String,
    val iconUri: String?,
    val colorScheme: ColorScheme,
    val amount: Amount = Amount.zero(),         // user-selected amount
    val previousAmount: Amount? = null,         // previous-period amount (chip)
)
```

`isSet` derived: `amount > Amount.zero()`. `setCount`, `totalBudgeted` derived for the CTA label.

- [ ] **Step 4: `Action`s**

```kotlin
object TapCopyAll : Action
data class StartEdit(val categoryId: Id.Known) : Action
data class ChangeEditAmount(val text: String) : Action
object TapPreviousChip : Action
object CommitEdit : Action
object DismissEdit : Action
object TapCreate : Action      // bottom CTA
object TapClose : Action
```

- [ ] **Step 5: `perform()` logic**

- `TapCopyAll` → set each `CategoryRow.amount = previousAmount ?: zero()`.
- `StartEdit(id)` → seed `editingAmountText` from current `amount` (or `"0"`), set `editingCategoryId = id`.
- `ChangeEditAmount(t)` → update text only.
- `TapPreviousChip` → set text to previous-period amount as string.
- `CommitEdit` → parse `editingAmountText`. Update the row's `amount` field. Close edit.
- `DismissEdit` → close edit without saving.
- `TapCreate` → call `bulkBudgetSaveUseCase.save(...)` with entries where `amount > zero`, then `onBulkSavedHandler.onSaved(setCount, totalBudgeted)` (which triggers a toast on the parent Budget screen) and `onBackHandler.onBack()`.
- `TapClose` → straight `onBackHandler.onBack()`.

- [ ] **Step 6: `OnBulkBudgetSavedHandler`**

```kotlin
fun interface OnBulkBudgetSavedHandler {
    fun onSaved(count: Int, total: Amount)
    companion object { val Noop = OnBulkBudgetSavedHandler { _, _ -> } }
}
```

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(budget): BudgetBulkSetupComponent scaffold + ViewModel"
```

---

## Task 5: `BudgetBulkSetupViewProvider`

Maps from `BudgetScreen.jsx` lines 117–271:

```
Column (background = Surface, fillMaxSize):
  // Header
  Row (padding top 52dp, horizontal 12dp):
    IconButton(close, 44dp) -> TapClose
    Column (weight 1, centerHorizontally):
      Text("New Budget", 17sp/ExtraBold, Primary)
      Text(periodLabel, 12sp/Normal, OnSurfaceVariant)
    Spacer(width 44dp)

  // Copy-from-prev banner (only if previousPeriodCount > 0)
  if (previousPeriodCount > 0) {
    Row (background = PrimaryContainer, radius 14, padding 14/16, gap 12, margin 16dp horizontal, clickable -> TapCopyAll):
      Box(36dp, radius 10, bg rgba(white, 0.12)): Icon(SyncAlt, 20dp, #5DDBA8)
      Column (weight 1):
        Text("Copy from previous month", 14sp/Bold, #fff)
        Text("{N} categories · {fmtShort(total)} total", 12sp/Normal, rgba(white, 0.55))
      Icon(KeyboardArrowRight, 18dp, rgba(white, 0.4))
  }

  // Category list
  LazyColumn (weight 1, padding bottom 140dp):
    item { Text("Categories — tap amount to edit", 11sp/Bold uppercase, OnSurfaceVariant, padding 8) }
    items(state.categories) { row ->
      CategoryAmountRow(row, onClick = { dispatch(StartEdit(row.categoryId)) })
    }

  // Sticky bottom CTA
  Box (align Bottom, background Surface, padding 12/16 36):
    PrimaryButton(
      enabled = setCount > 0,
      label = if (setCount == 0) "Set at least one category"
              else "Create budget — $setCount ${ if (setCount==1) "category" else "categories" } · ${fmtShort(totalBudgeted)}",
      onClick = TapCreate,
    )

  // Numpad overlay (when editingCategoryId != null)
  AnimatedVisibility(editingCategoryId != null) {
    Box (fillMaxSize):
      Box (weight 1 above, background = Color.Black 0.25, clickable -> CommitEdit)
      // Numpad sheet
      Column (background = Surface, top radius 20dp):
        DragHandle()
        Row: CategoryIcon + Name + PreviousChip + Done text-button
        AmountDisplay (big "$X" centered)
        NumPad(...)
        PrimaryButton(label = if hasAmount then "Set ${fmtAmt}" else "Skip", onClick = CommitEdit)
  }
```

`CategoryAmountRow` matches design lines 161–192: white card, radius 14dp, padding 12/14, 8dp bottom margin. Right side: amount chip — if `amount > 0` chip background = `colorScheme.background`, text in `colorScheme.primary`; else chip background = `SurfaceContainerLow`, "+ Set" text.

- [ ] **Step 1: Implement layout**
- [ ] **Step 2: Commit**

```bash
git commit -m "feat(budget): BudgetBulkSetupViewProvider matching design"
```

---

## Task 6: Wire `Destinations.Budget.BulkSetup` + entry

**Files:**
- Modify: `Destinations.kt`
- Modify: `MainActivityScreenComponent.kt`

- [ ] **Step 1: Destination**

```kotlin
object BulkSetup : Budget, Destination by destinationOf("budget/bulksetup", PeriodStart, PeriodEnd) {
    object PeriodStart : Argument<String> by stringValueOf("periodStart")
    object PeriodEnd : Argument<String> by stringValueOf("periodEnd")
}
```

- [ ] **Step 2: `budgetBulkSetupNavigationEntry`** — full-screen `composable` (not bottom sheet):

```kotlin
navigatorScope.buildable(Destinations.Budget.BulkSetup) {
    componentBuilder
        .periodStart(LocalDate.parse(arguments.getValue(...)))
        .periodEnd(LocalDate.parse(arguments.getValue(...)))
        .onBulkSavedHandler { count, total ->
            // bubble toast to parent Budget screen via shared SharedFlow (see Task 2 of this phase)
        }
        .onBackHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 3: Wire entry point from Budget screen** — `BudgetViewModel.Action.TapCreateBudget`:

```kotlin
coroutineScope.launch(Dispatchers.Main) {
    onCreateBudgetHandler.onCreate(currentPeriodStart, currentPeriodEnd)
}
```

`OnCreateBudgetHandler` is a new handler on `BudgetComponent.Builder`; the `budgetNavigationEntry` in `MainActivityScreenComponent` wires it to `navigator.navigateTo(Destinations.Budget.BulkSetup, ...)`. Dark callout card "Create budget" CTA from Phase 1 was a no-op stub — repoint its `onClick` to dispatch `TapCreateBudget`.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(budget): wire BulkSetup destination + create-budget entry from empty state"
```

---

## Task 7: Inline numpad auto-advance on main Budget screen

**Files:**
- Modify: `BudgetViewModel.kt`, `BudgetViewProvider.kt`

Behaves identically to bulk-setup's numpad overlay but lives on the main `BudgetComponent`. When user taps an unset row, the inline numpad slides up (over the scroll). On commit, jumps to the next unset category in the sorted order. On "Skip" or commit with no value, dismisses.

The current Phase 2 single-edit sheet (`BudgetEditComponent`) opens via navigation. **Decision:** keep Phase 2's sheet for edits of *already-set* categories (long-press in Phase 6 will also route to it), and use the inline numpad on the Budget screen for *unset* categories specifically. So the flow:

- Tap unset row → inline numpad overlay (this task).
- Tap set row → opens `BudgetEditComponent` bottom sheet via navigation (Phase 2).

Or, simpler: route everything through inline numpad on the Budget screen, and keep `BudgetEditComponent` reachable only via long-press (Phase 6). Choose one based on what feels right — the design uses inline-everywhere on the main Budget screen. **Recommended: inline-everywhere; route `BudgetEditComponent` only via long-press.** The Phase 2 navigation entry stays (so it's reachable from elsewhere later, e.g. category detail screen), but the Phase 1/2 tap handler now dispatches to inline edit instead of navigation.

- [ ] **Step 1: Add inline-edit state** to `BudgetViewModel.State` (mirrors bulk setup's `editingCategoryId` + `editingAmountText`).

- [ ] **Step 2: Repoint `Action.TapCategory`** to set inline-edit state instead of dispatching the navigation handler.

- [ ] **Step 3: On `CommitEdit`**, call `budgetRepository.insert(BudgetInsert(...))`, then compute next unset category from sorted list, set `editingCategoryId` to it (or `null` if none).

- [ ] **Step 4: ViewProvider overlay** — copy the bulk-setup overlay structure verbatim (the only difference is the CTA label):
  - If `hasNextUnset && hasVal`: "Set $X — Next →"
  - If `hasNextUnset && !hasVal`: "Skip →"
  - If `!hasNextUnset && hasVal`: "Set $X"
  - If `!hasNextUnset && !hasVal`: "Close"

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): inline numpad with auto-advance to next unset category"
```

---

## Task 8: Manual verification + PR

- [ ] **Step 1:** Tap "Create budget" CTA from empty state → full-screen bulk setup appears.
- [ ] **Step 2:** Tap a category amount chip → numpad slides up. Enter value, tap "Set" → chip updates with amount in category color.
- [ ] **Step 3:** Tap "Copy from previous month" banner → all rows that had previous-period values get filled.
- [ ] **Step 4:** Tap bottom CTA "Create budget — N categories · $X" → returns to Budget screen, toast appears, rows now have budgets persisted.
- [ ] **Step 5:** Back on Budget screen, tap an unset category → inline numpad. Set, tap "Next →" → numpad stays open with next unset category. Repeat until all set → final CTA reads "Set $X" then "Close".
- [ ] **Step 6:** Tap "Copy from {prevPeriod}" card → if current has data, confirm-overwrite sheet appears. Confirm → toast, rows updated.
- [ ] **Step 7: UI inspector**, tests, lint.
- [ ] **Step 8: PR**

Update Phase 3 in [roadmap status tracker](2026-05-13-budget-roadmap.md#phase-index--status-tracker).

---

## Self-Review

- [ ] User goals covered: #1 (quick enter), #2 (auto-advance), #3 (per-category copy from prev — via "Last month" chip in inline numpad), #4 (whole-budget copy).
- [ ] Cadence-neutral: every entry point passes `periodStart`/`periodEnd`.
- [ ] No reliance on Phase 4 progress UI — bulk setup and inline numpad work even before progress bars exist.
