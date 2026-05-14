# Budget Phase 5 — Over-Budget Actions (Reallocate / Increase)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Roadmap: [budget-roadmap.md](2026-05-13-budget-roadmap.md). Prereq: Phases 1–4 merged.

**Goal:** When a category goes over budget, the user sees Reallocate and Increase action buttons inside the `BudgetCard` (Phase 4 omitted them). Tapping either opens `BudgetOverComponent` — a bottom sheet that handles three sub-modes:
1. **Choice** (lines 574–615): two options "Reallocate budget" and "Increase budget" — entered when user taps the over-budget card body, *not* a specific action button.
2. **Reallocate** (lines 432–505): list of categories with remaining budget, "Covers it" / "Partial" tags, tap to select, primary CTA to move money.
3. **Increase** (lines 508–571): suggestion chips (overage, rounded, next-50), NumPad for custom amount, primary CTA "Confirm Increase".

Selecting Reallocate from `BudgetCard` button skips Choice and lands straight on Reallocate. Same for Increase.

**Architecture:** One component (`BudgetOverComponent`) with internal `Mode` state (Choice / Reallocate / Increase). External callers can pass an `initialMode` arg via the destination. Mutation actions on success call `BudgetRepository.insert(...)` twice (reallocate: source + target updates) or once (increase: target only).

---

## Files

### New — zero-core
- `zero-core/.../budget/over/BudgetOverComponent.kt`
- `zero-core/.../budget/over/BudgetOverViewModel.kt`, `DefaultBudgetOverViewModel.kt`
- `zero-core/.../budget/over/BudgetOverViewProvider.kt`
- `zero-core/.../budget/over/OnReallocateCompletedHandler.kt` — `fun interface OnReallocateCompletedHandler { fun onComplete(sourceName: String, targetName: String, amount: Amount) }`
- `zero-core/.../budget/over/OnIncreaseCompletedHandler.kt` — `fun interface OnIncreaseCompletedHandler { fun onComplete(targetName: String, newAmount: Amount) }`

### Modified — zero-core
- `BudgetCard.kt` — add `onReallocate` and `onIncrease` lambda parameters, render the two over-budget action buttons when `isOver`
- `BudgetViewModel.kt` — add `Action.TapReallocate(categoryId)` / `Action.TapIncrease(categoryId)` dispatching to handlers
- `BudgetComponent.kt` — `@BindsInstance OnOverActionTappedHandler`

### Modified — app
- `Destinations.kt` — add `Destinations.Budget.Over` bottom-sheet destination with args `CategoryId`, `PeriodStart`, `PeriodEnd`, `InitialMode`
- `MainActivityScreenComponent.kt` — `budgetOverNavigationEntry`

---

## Task 1: `BudgetCard` over-budget action buttons

**Files:**
- Modify: `BudgetCard.kt`

Phase 4 omitted these. Add now (design lines 674–697):

```
if (isOver):
    Row (gap 8, marginTop 12):
        OutlinedActionButton(
            label = "Reallocate",
            icon = SyncAlt (or similar),
            colorBorder = Error,
            colorText = Error,
            onClick = onReallocate,
            modifier = Modifier.weight(1f),
        )
        FilledActionButton(
            label = "Increase",
            icon = ArrowUpward,
            backgroundColor = Error,
            textColor = White,
            onClick = onIncrease,
            modifier = Modifier.weight(1f),
        )
```

- [ ] **Step 1:** Add the two action button composables (inline in `BudgetCard.kt` — no need to extract).
- [ ] **Step 2: Wire `onTap` (card body) → dispatch with `initialMode=null` (Choice)** so the card-body tap opens the choice sheet on over-budget cards. Below: rendering goes to the existing `onTap` for in-progress/under-limit cards.

Actually — re-read the design: on the over-budget card, tapping the *top area* (icon + name + amount + bar) opens the edit/numpad (design line 643 `onClick={onPress}`). Tapping the *action buttons* opens reallocate / increase directly (lines 677, 687). There's no Choice entry point from the BudgetCard itself.

**Decision:** keep it simple — `BudgetCard` taps:
- Top area → existing `onTap` (inline numpad / `BudgetEditComponent`).
- Reallocate button → `onReallocate` → opens `BudgetOverComponent` with `initialMode=Reallocate`.
- Increase button → `onIncrease` → opens `BudgetOverComponent` with `initialMode=Increase`.

The "Choice" mode is therefore *unreachable from BudgetCard*. Keep it in `BudgetOverComponent` anyway — it's a clean fallback if a future entry point (e.g. a notification deep link) lands without an explicit mode.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): add Reallocate/Increase action buttons on over-budget BudgetCard"
```

---

## Task 2: Scaffold `BudgetOverComponent`

Run `superpowers/scaffold-feature` for name `BudgetOver`, package `budget/over`, handlers `back`. No use case stub.

- [ ] **Step 1: Dependencies**

```kotlin
interface Dependencies {
    val imageLoader: ImageLoader
    val amountFormatter: AmountFormatter
    val budgetRepository: BudgetRepository
    val budgetQueryUseCase: BudgetQueryUseCase
}
```

- [ ] **Step 2: `@BindsInstance` inputs**

```kotlin
@BindsInstance fun categoryId(id: Id.Known): Builder
@BindsInstance fun periodStart(start: LocalDate): Builder
@BindsInstance fun periodEnd(end: LocalDate): Builder
@BindsInstance fun initialMode(mode: BudgetOverViewModel.Mode?): Builder   // null = Choice
@BindsInstance fun onReallocateCompletedHandler(h: OnReallocateCompletedHandler): Builder
@BindsInstance fun onIncreaseCompletedHandler(h: OnIncreaseCompletedHandler): Builder
@BindsInstance fun onBackHandler(h: OnBackHandler): Builder
```

- [ ] **Step 3: ViewModel**

```kotlin
interface BudgetOverViewModel : AttachableActionStateModel<Action, State> {

    enum class Mode { CHOICE, REALLOCATE, INCREASE }

    data class State(
        val mode: Mode = Mode.CHOICE,
        val target: TargetCategory? = null,    // the over-budget category
        val reallocationSources: List<ReallocationSource> = emptyList(),
        val selectedSourceId: Id.Known? = null,
        val increaseAmountText: String = "0",
        val increaseSuggestions: List<Amount> = emptyList(),
    )

    data class TargetCategory(
        val categoryId: Id.Known,
        val name: String,
        val iconUri: String?,
        val colorScheme: ColorScheme,
        val budgeted: Amount,
        val spent: Amount,
        val overage: Amount,    // spent - budgeted
    )

    data class ReallocationSource(
        val categoryId: Id.Known,
        val name: String,
        val iconUri: String?,
        val colorScheme: ColorScheme,
        val budgeted: Amount,
        val spent: Amount,
        val remaining: Amount,   // budgeted - spent
        val covers: Boolean,     // remaining >= target.overage
    )

    sealed interface Action {
        object TapReallocateOption : Action            // Choice → Reallocate
        object TapIncreaseOption : Action              // Choice → Increase
        object TapBack : Action                        // back arrow
        data class SelectSource(val id: Id.Known) : Action
        object ConfirmReallocate : Action              // primary CTA in Reallocate
        data class ChangeIncreaseAmount(val text: String) : Action
        data class TapIncreaseSuggestion(val amount: Amount) : Action
        object ConfirmIncrease : Action                // primary CTA in Increase
        object TapClose : Action
    }
}
```

- [ ] **Step 4: `Default…ViewModel.attach()`**

`combine(budgetQueryUseCase.query(from, to)) { rows ->`:
- `target` = `rows.first { it.categoryId == categoryId }` projected into `TargetCategory`.
- `reallocationSources` = `rows.filter { it.categoryId != categoryId && it.budgetId != null && it.budgeted > it.spent }.sortedByDescending { it.remaining }.map { ... }`.
- `increaseSuggestions` (per design lines 509–513):
  ```kotlin
  val overage = target.overage
  listOf(
      overage.ceil(),
      overage.div(10).ceil().times(10).plus(10),                      // round-up to nearest 10 + buffer
      target.budgeted.plus(overage).div(50).ceil().times(50).minus(target.budgeted),  // round target+overage up to next 50
  ).filter { it > Amount.zero() }.distinct().take(3)
  ```
- `increaseAmountText = overage.ceil().toString()` on first load.

`perform()` dispatches: mode transitions for `TapReallocateOption` / `TapIncreaseOption` / `TapBack` (back goes Choice ← Reallocate, Choice ← Increase, or closes if `initialMode != null`); selection via `SelectSource`; amount edits.

`ConfirmReallocate` — within `coroutineScope.launch`:
```kotlin
val source = state.reallocationSources.firstOrNull { it.categoryId == state.selectedSourceId } ?: return@launch
val amountToMove = minOf(source.remaining, state.target.overage)
val sourceBudget = budgetRepository.query(Criteria.ForCategoryAndPeriod(source.categoryId, from, to)).firstOrNull() ?: return@launch
val targetBudget = budgetRepository.query(Criteria.ForCategoryAndPeriod(target.categoryId, from, to)).firstOrNull() ?: return@launch
budgetRepository.insert(listOf(
    sourceBudget.toInsert().copy(amount = (sourceBudget.amount - amountToMove).coerceAtLeast(Amount.zero())),
    targetBudget.toInsert().copy(amount = targetBudget.amount + amountToMove),
))
withContext(Dispatchers.Main) {
    onReallocateCompletedHandler.onComplete(source.name, target.name, amountToMove)
    onBackHandler.onBack()
}
```

`ConfirmIncrease` — analogous, but only the target budget changes:
```kotlin
val delta = parseFloat(state.increaseAmountText).asAmount() ?: return@launch
budgetRepository.insert(targetBudget.toInsert().copy(amount = targetBudget.amount + delta))
withContext(Dispatchers.Main) {
    onIncreaseCompletedHandler.onComplete(target.name, targetBudget.amount + delta)
    onBackHandler.onBack()
}
```

`toInsert()` — extension converting `Budget` to `BudgetInsert` preserving `id`. Place in `zero-api/.../budget/BudgetRepository.kt`:
```kotlin
fun BudgetRepository.Budget.toInsert(): BudgetRepository.BudgetInsert = BudgetRepository.BudgetInsert(
    id = id, categoryId = categoryId, type = type, amount = amount,
    periodStart = periodStart, periodEnd = periodEnd,
)
```

- [ ] **Step 5: Tests** in `DefaultBudgetOverViewModelTest.kt`:
  - Initial state when `initialMode = REALLOCATE` → `mode = REALLOCATE`.
  - `reallocationSources` excludes target category.
  - `reallocationSources` excludes categories without remaining budget.
  - `SelectSource → ConfirmReallocate` invokes `budgetRepository.insert(listOf(...))` with the two adjusted rows.
  - `ConfirmIncrease` invokes `budgetRepository.insert(...)` with target.amount + delta.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(budget): BudgetOverViewModel with reallocate + increase modes"
```

---

## Task 3: `BudgetOverViewProvider`

Render three sub-layouts switched by `state.mode`. Sheet shell:

```
Box (fillMaxSize):
    Box (weight scrim, background = Color.Black.copy(alpha=0.32), clickable -> TapClose)
    Surface (radius top 20dp, background = Surface, fillMaxWidth):
        DragHandle()
        Header(state.mode)
        when (state.mode) {
            CHOICE -> ChoiceContent(state, ...)
            REALLOCATE -> ReallocateContent(state, ...)
            INCREASE -> IncreaseContent(state, ...)
        }
```

**Choice** (lines 574–615) — 2 option cards:
```
Column (padding 16):
    Row (gap 14): Box(44dp, radius 12, bg #FFF3E8) { Icon(Warning, 24dp, #E65100) }
                   Column { Text("{target.name} is over budget", 17sp/ExtraBold, OnSurface)
                            Text("Spent {fmt(spent)} of {fmt(budgeted)} — {fmt(overage)} over", 13sp/Normal, OnSurfaceVariant) }
    Divider(thickness 1, color SurfaceContainer, padding 8/24)
    OptionCard("Reallocate budget", "Move money from another category", icon=SyncAlt, iconColor=Secondary, iconBg=#E8F5E9, onClick=TapReallocateOption)
    OptionCard("Increase budget", "Add more to this category's limit", icon=ArrowUpward, iconColor=#1565C0, iconBg=#E3F2FD, onClick=TapIncreaseOption)
```

**Reallocate** (lines 432–505):
```
Column:
    // Header with back arrow
    Row { IconButton(Back, onClick=TapBack); Spacer(weight 1); Text("Reallocate From", centered) }
    // Context pill — category needs ${overage} more
    Row (background = target.colorScheme.background, radius 14, padding 12/16, gap 12, margin 8/16 4):
        CategoryIcon(target, size 36)
        Column { Text("${target.name} needs", 13sp/Bold, target.colorScheme.primary)
                 Text("${fmtAmt(overage)} more", 18sp/ExtraBold, target.colorScheme.primary) }
    // Source list
    LazyColumn (padding 8/16 32):
        item { Text("AVAILABLE TO MOVE", 11sp/Bold uppercase, OnSurfaceVariant) }
        items(reallocationSources) { source ->
            SourceCard(source, selected = source.categoryId == selectedSourceId, onClick = { dispatch(SelectSource(source.categoryId)) })
        }
        if (reallocationSources.isEmpty()):
            item { Text("No categories have remaining budget", centered, OnSurfaceVariant) }
        if (selectedSourceId != null):
            item { PrimaryButton(label = "Move ${fmtAmt(amountToMove)} from ${selectedSource.name}", onClick = ConfirmReallocate) }
```

`SourceCard` (lines 469–489):
- Background = `#fff` (white) when unselected, `colorScheme.background` when selected, border 2dp `colorScheme.primary` when selected
- Row: `CategoryIcon` + `Column { Text(name, 15sp/Bold), Text("{fmt(remaining)} remaining", 12sp/Normal, OnSurfaceVariant) }` + `Column (end) { Text(if covers "✓ Covers it" else "Partial", 12sp/Bold, if covers Secondary else OnSurfaceVariant), Text("of {fmt(budgeted)}", 11sp/Normal, OnSurfaceVariant) }`
- Below: thin progress bar showing `spent / budgeted` for the source

**Increase** (lines 508–571):
```
Column (padding 8/24 0):
    Row (back, header "Increase Budget")
    Row (target context pill — over by ${overage}; current budget ${budgeted})
    Text("SUGGESTIONS", 11sp/Bold uppercase, OnSurfaceVariant)
    Row (gap 8):
        suggestions.forEach { s ->
            SuggestionChip(
                label = "+${fmtShort(s)}",
                subLabel = "→ ${fmtShort(budgeted + s)}",
                selected = increaseAmountText == s.toString(),
                onClick = { dispatch(TapIncreaseSuggestion(s)) },
            )
        }
    NewBudgetDisplay(budgeted + parsed(increaseAmountText))
    NumPad(value = increaseAmountText, onChange = { dispatch(ChangeIncreaseAmount(it)) })
    PrimaryButton(label = "Confirm Increase", enabled = parsed > 0, onClick = ConfirmIncrease)
```

- [ ] **Step 1: Implement three content composables**
- [ ] **Step 2: Implement shared header with back-arrow behavior** — back arrow returns to Choice unless `initialMode != null`, in which case it closes the sheet
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): BudgetOverViewProvider — Choice/Reallocate/Increase sub-views"
```

---

## Task 4: Destination + navigation wiring

**Files:**
- Modify: `Destinations.kt`, `MainActivityScreenComponent.kt`, `BudgetComponent.kt`

- [ ] **Step 1: Destination**

```kotlin
object Over : Budget, Destination by destinationOf(
    "budget/over",
    CategoryId, PeriodStart, PeriodEnd, InitialMode,
) {
    object CategoryId : Argument<Id.Known> by idKnownValueOf("categoryId")
    object PeriodStart : Argument<String> by stringValueOf("periodStart")
    object PeriodEnd : Argument<String> by stringValueOf("periodEnd")
    object InitialMode : Argument<String> by stringOptionalValueOf("initialMode")
}
```

- [ ] **Step 2: `budgetOverNavigationEntry`** — bottom sheet via `DisplayOption.PartiallyVisible.BottomSheet`. Wire `onReallocateCompletedHandler` and `onIncreaseCompletedHandler` to relay toast messages to the parent Budget screen (same mechanism as Phase 3's `OnBulkBudgetSavedHandler`).

- [ ] **Step 3: Wire from `BudgetCard`** —
- `onReallocate` in the card → `dispatch(BudgetViewModel.Action.TapReallocate(categoryId))`
- `onIncrease` in the card → `dispatch(BudgetViewModel.Action.TapIncrease(categoryId))`

Both ViewModel actions invoke a new `OnOverActionTappedHandler` with the category id, period, and mode. The handler implementation in `MainActivityScreenComponent.budgetNavigationEntry` calls `navigator.navigateTo(Destinations.Budget.Over, ...)`.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(budget): wire BudgetOver destination + over-action navigation"
```

---

## Task 5: Manual verification + PR

- [ ] **Step 1:** Deliberately put a category over budget (set a $50 budget, add a $100 expense). The card on Budget screen should now display in the red over-budget style with two action buttons.
- [ ] **Step 2:** Tap "Reallocate" → bottom sheet with source list. Pick a category that has remaining → CTA shows "Move $X from {name}". Confirm → both budget rows update, toast "Moved $X from {source} to {target}".
- [ ] **Step 3:** Cause another over-budget condition. Tap "Increase" → suggestion chips populate. Tap one → New Budget value updates. Confirm → target budget grows by selected amount, toast "{name} budget increased to $X".
- [ ] **Step 4:** Verify Choice mode by deep-linking (no `initialMode`) — pop the choice card, both options route to the right sub-mode.
- [ ] **Step 5: UI inspector + tests + lint.**
- [ ] **Step 6: PR**

Update Phase 5 in [roadmap status tracker](2026-05-13-budget-roadmap.md#phase-index--status-tracker).

---

## Self-Review

- [ ] User goal #5 (flagging + Reallocate/Increase) fully covered.
- [ ] Reallocate moves money atomically (single `insert(list)` call → single DB transaction via Room).
- [ ] Increase preserves existing source rows; only target changes.
- [ ] Mutation behavior matches design — reallocate amount is capped at `min(source.remaining, target.overage)`.
