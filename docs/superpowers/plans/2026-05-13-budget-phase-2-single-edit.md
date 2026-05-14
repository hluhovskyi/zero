# Budget Phase 2 — Single-Category Edit Sheet

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Roadmap: [budget-roadmap.md](2026-05-13-budget-roadmap.md). Prereq: Phase 1 merged.

**Goal:** Tap on any category row in the Budget screen → bottom sheet opens with the NumPad UI from `BudgetScreen.jsx` lines 341–420 (`SetBudgetSheet`). User sets / edits a budget; tap "Set Budget" persists via `BudgetRepository.insert(...)`. "Last month" chip appears when the previous period had a value for this category and pre-fills the amount on tap.

**Architecture:** New `BudgetEditComponent` (bottom-sheet destination, opens via `navigator.navigateTo(Destinations.Budget.Edit, ...)`). Standard Component / ViewModel / ViewProvider triple. The Budget tab dispatches taps via a handler that calls `navigator.navigateTo(...)`.

---

## Files

### New — zero-core
- `zero-core/.../budget/edit/BudgetEditComponent.kt`
- `zero-core/.../budget/edit/BudgetEditViewModel.kt`, `DefaultBudgetEditViewModel.kt`
- `zero-core/.../budget/edit/BudgetEditViewProvider.kt`
- `zero-core/.../budget/edit/OnBudgetSavedHandler.kt`
- `zero-ui/.../budget/NumPad.kt` — shared composable (placed in `zero-ui` so Phase 3's bulk-setup reuses it)

### Modified — app
- `activity/navigation/Destinations.kt` — add `Destinations.Budget.Edit` bottom-sheet destination with args `CategoryId`, `PeriodStart`, `PeriodEnd`
- `MainActivityScreenComponent.kt` — add `budgetEditComponentBuilder: BudgetEditComponent.Builder` + `budgetEditNavigationEntry(...)` with `DisplayOption.PartiallyVisible.BottomSheet`
- `ApplicationComponent.kt` / `ActivityComponent.kt` — wire dependencies

### Modified — zero-core
- `BudgetComponent.kt` — add `@BindsInstance onCategoryTappedHandler: OnCategoryTappedHandler`
- `BudgetViewModel.kt` — replace Phase 1's no-op `TapUnsetCategory` / `TapSetCategory` with a single `TapCategory(categoryId: Id.Known)` action that dispatches to the handler

---

## Task 1: Extract `NumPad` to `zero-ui`

**Files:**
- Create: `zero-ui/.../budget/NumPad.kt`

The keypad design from `BudgetScreen.jsx` lines 44–78: 3-column grid, keys `1–9 . 0 ⌫`, each cell 50dp tall, `OutlineVariant` color for backspace icon, `PrimaryContainer` color for digits, 22sp/Medium font.

- [ ] **Step 1:** Implement as `@Composable fun NumPad(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier)`. Internal `handleKey(k)` mirrors the JSX:
  - `"⌫"` → trim last char; if empty, emit `"0"`.
  - `"."` → ignore if value already contains `.`.
  - Digit → if `value == "0"`, replace; else append. Reject if `.` is present and right side already has 2+ chars.
- [ ] **Step 2:** Tests in `NumPadTest.kt` covering the four edge cases above.
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(budget): shared NumPad composable in zero-ui"
```

---

## Task 2: `OnCategoryTappedHandler` + `OnBudgetSavedHandler`

**Files:**
- Create: `zero-core/.../budget/OnCategoryTappedHandler.kt`
- Create: `zero-core/.../budget/edit/OnBudgetSavedHandler.kt`

```kotlin
// OnCategoryTappedHandler.kt
fun interface OnCategoryTappedHandler {
    fun onTap(categoryId: Id.Known, periodStart: LocalDate, periodEnd: LocalDate)
    companion object { val Noop = OnCategoryTappedHandler { _, _, _ -> } }
}

// OnBudgetSavedHandler.kt
fun interface OnBudgetSavedHandler {
    fun onSaved(categoryName: String, amount: Amount)
    companion object { val Noop = OnBudgetSavedHandler { _, _ -> } }
}
```

- [ ] **Step 1: Add both files**
- [ ] **Step 2: Commit**

```bash
git commit -m "feat(budget): handlers for category tap and budget saved"
```

---

## Task 3: Scaffold `BudgetEditComponent`

Run `superpowers/scaffold-feature` for name `BudgetEdit`, package `budget/edit`, handlers `back`, `saved`.

- [ ] **Step 1: Wire `Dependencies`**

```kotlin
interface Dependencies {
    val imageLoader: ImageLoader
    val amountFormatter: AmountFormatter
    val categoryRepository: CategoryRepository
    val budgetRepository: BudgetRepository
    val iconRepository: IconRepository
    val colorRepository: ColorRepository
}
```

- [ ] **Step 2: `@BindsInstance` inputs on Builder**

```kotlin
@BindsInstance fun categoryId(@CategoryId id: Id.Known): Builder
@BindsInstance fun periodStart(@PeriodStart start: LocalDate): Builder
@BindsInstance fun periodEnd(@PeriodEnd end: LocalDate): Builder
@BindsInstance fun onBudgetSavedHandler(handler: OnBudgetSavedHandler): Builder
@BindsInstance fun onBackHandler(handler: OnBackHandler): Builder
```

Use `@Qualifier` annotations for the three primitive types (mirror `transactions/edit/TransactionEditComponent` which uses similar pattern). If using `Id` is simpler, store `Id.Known` directly with `@BindsInstance` — match existing pattern in `CategoryEditComponent`.

- [ ] **Step 3: `BudgetEditViewModel.State`**

```kotlin
data class State(
    val categoryName: String = "",
    val iconUri: String? = null,
    val colorScheme: ColorScheme = ColorScheme.fallback(),
    val amountText: String = "0",      // raw NumPad input
    val isEditing: Boolean = false,    // true when an existing budget exists
    val previousPeriodAmount: Amount? = null,  // for the "Last month" chip
    val isPreviousSelected: Boolean = false,   // true when user tapped chip — controls chip styling
)

sealed interface Action {
    data class ChangeAmount(val text: String) : Action
    object TapPreviousChip : Action
    object TapSave : Action
    object TapClose : Action
}
```

- [ ] **Step 4: `DefaultBudgetEditViewModel.attach()`**

On attach:
1. `combine(categoryRepository.query(Criteria.ById(categoryId)), iconRepository.allFlow(), colorRepository.allFlow(), budgetRepository.query(Criteria.ForCategoryAndPeriod(categoryId, periodStart, periodEnd)), budgetRepository.query(Criteria.ForCategoryAndPeriod(categoryId, prevPeriodStart, prevPeriodEnd)))`.
2. Compute prev period using `start.minus(1, DateTimeUnit.MONTH)` (Phase 2 stays monthly — cadence flexibility lands in Phase 3 if needed; otherwise this hardcodes "previous month" by date math from `periodStart`).
3. Seed `state.amountText` from existing budget if present, else `"0"`. Set `isEditing` accordingly.

`perform()`:
- `ChangeAmount(text)` → `state.copy(amountText = text, isPreviousSelected = text == previousAmountString)`.
- `TapPreviousChip` → `state.copy(amountText = prevAmountString, isPreviousSelected = true)`.
- `TapSave` → `coroutineScope.launch { budgetRepository.insert(BudgetInsert(...)); withMain { onBudgetSavedHandler.onSaved(name, amount); onBackHandler.onBack() } }`.
- `TapClose` → `withMain { onBackHandler.onBack() }`.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(budget): BudgetEditComponent + ViewModel"
```

---

## Task 4: `BudgetEditViewProvider`

Layout matches `BudgetScreen.jsx` lines 341–420 (`SetBudgetSheet`):

```
Column (background = Surface, top corners 20dp):
  // Drag handle is provided by MainActivityScreenViewProvider — don't draw it here
  Row (header, padding 8/8 0):
    IconButton(close, 24dp icon, primaryContainer tint) -> dispatch TapClose
    Text("Set Budget" or "Edit Budget", 17sp/Bold, primaryContainer, weight 1, centered)
    Spacer(width 48dp)

  // Category identity row
  Row (padding 4/24 0):
    CategoryIcon(size=36)
    Text(categoryName, 16sp/Bold)
    if (previousPeriodAmount != null):
      PreviousChip(amount = previousPeriodAmount, selected = isPreviousSelected, onClick = TapPreviousChip)

  // Big amount display
  Column (centered, padding top 10dp):
    Text("MONTHLY BUDGET", 11sp/Bold, OnSurfaceVariant, letterSpacing 0.1em, uppercase)
    Row (baseline align):
      Text("$", 28sp/Bold, OnSurfaceVariant when hasAmount else OutlineVariant)
      Text(amountText if not "0" else "0" w/ alpha 0.25, 52sp/ExtraBold, PrimaryContainer when hasAmount)

  NumPad(value = state.amountText, onChange = { perform(ChangeAmount(it)) })

  // CTA
  Box (padding bottom 32dp):
    PrimaryButton(
      enabled = parsedAmount > 0,
      label = if (isEditing) "Update Budget" else "Set Budget",
      onClick = TapSave,
    )
```

Where `PreviousChip` is an inline `@Composable` that renders the "Last month: $42.00" pill (radius 20dp, padding 6/12, background `PrimaryContainer` if selected else `SurfaceContainerLow`, fg `#fff` / `PrimaryContainer`).

- [ ] **Step 1: Implement the layout above**
- [ ] **Step 2: Commit**

```bash
git commit -m "feat(budget): BudgetEditViewProvider matching SetBudgetSheet design"
```

---

## Task 5: Wire navigation

**Files:**
- Modify: `Destinations.kt`
- Modify: `MainActivityScreenComponent.kt`
- Modify: `BudgetComponent.kt` — accept `OnCategoryTappedHandler`

- [ ] **Step 1: Add destination** — see [Navigation](../agents/navigation.md):

```kotlin
sealed interface Budget : Destination {
    object Edit : Budget, Destination by destinationOf(
        "budget/edit",
        CategoryId, PeriodStart, PeriodEnd,
    ) {
        object CategoryId : Argument<Id.Known> by idKnownValueOf("categoryId")
        object PeriodStart : Argument<String> by stringValueOf("periodStart")  // ISO date
        object PeriodEnd : Argument<String> by stringValueOf("periodEnd")
    }
}
```

Update existing `object Budget : Destination by destinationOf("budget")` from Phase 1 to be the sealed-interface parent + `object All : Budget, Destination by destinationOf("budget")`. Update Phase 1's `budgetNavigationEntry` to reference `Destinations.Budget.All`. Also update `DefaultBottomBarViewModel` line 88/130 to reference `Destinations.Budget.All`.

- [ ] **Step 2: `budgetEditNavigationEntry(...)` @IntoSet**

```kotlin
navigatorScope.buildable(Destinations.Budget.Edit) {
    componentBuilder
        .categoryId(arguments.getValue(Destinations.Budget.Edit.CategoryId))
        .periodStart(LocalDate.parse(arguments.getValue(Destinations.Budget.Edit.PeriodStart)))
        .periodEnd(LocalDate.parse(arguments.getValue(Destinations.Budget.Edit.PeriodEnd)))
        .onBudgetSavedHandler { name, _ -> /* Phase 2: just navigate back via OnBackHandler; toast lands in Phase 3 */ navigator.back() }
        .onBackHandler { navigator.back() }
        .logging(logger)
}
```

Wrap with `DisplayOption.PartiallyVisible.BottomSheet` — see [Navigation §Bottom Sheet Destinations](../agents/navigation.md#bottom-sheet-destinations). The existing `transactionFilterSheetNavigationEntry` is the structural reference.

- [ ] **Step 3: Wire `OnCategoryTappedHandler` into `BudgetComponent.Builder`** as a `@BindsInstance`. In `MainActivityScreenComponent.budgetNavigationEntry` (from Phase 1), set:

```kotlin
.onCategoryTappedHandler { categoryId, start, end ->
    navigator.navigateTo(
        Destinations.Budget.Edit,
        Destinations.Budget.Edit.CategoryId.withValue(categoryId),
        Destinations.Budget.Edit.PeriodStart.withValue(start.toString()),
        Destinations.Budget.Edit.PeriodEnd.withValue(end.toString()),
    )
}
```

- [ ] **Step 4: `BudgetViewModel.perform(TapCategory)` dispatches via the handler** — `coroutineScope.launch(Dispatchers.Main) { onCategoryTappedHandler.onTap(categoryId, currentPeriodStart, currentPeriodEnd) }`.

- [ ] **Step 5: Build + commit**

```bash
./gradlew :app:assembleDebug
git commit -m "feat(budget): wire BudgetEdit bottom-sheet destination + category-tap handler"
```

---

## Task 6: Tests

**Files:**
- Create: `zero-core/src/test/.../budget/edit/DefaultBudgetEditViewModelTest.kt`

Mirror `DefaultTransactionEditViewModelTest.kt`. Cover:
- Initial state when no existing budget → `amountText="0"`, `isEditing=false`.
- Initial state when existing budget present → `amountText=existingAmount.toString()`, `isEditing=true`.
- `ChangeAmount("125")` → state's amount text updates; `isPreviousSelected=false`.
- `TapPreviousChip` → amount = previous-period amount; `isPreviousSelected=true`.
- `TapSave` invokes `budgetRepository.insert(...)` with the correct `BudgetInsert`, then triggers `onBudgetSavedHandler` and `onBackHandler`.

- [ ] **Step 1: Add 5 tests**
- [ ] **Step 2: Run** `./gradlew :zero-core:testDebugUnitTest`
- [ ] **Step 3: Commit**

---

## Task 7: Manual verification + PR

- [ ] **Step 1: Install + open** Budget tab.
- [ ] **Step 2:** Tap any "Set limit" row → bottom sheet appears with NumPad.
- [ ] **Step 3:** Enter `125` via the numpad. Tap "Set Budget". Sheet dismisses. The row updates (still shows dashed border in Phase 2 because the progress UI lands in Phase 4; verify via DB query that the row persisted: `adb shell run-as ... sqlite3 ...`).
- [ ] **Step 4:** Tap the same row → sheet now shows "Edit Budget" with amount pre-filled.
- [ ] **Step 5:** Set up a budget for the previous month (via the same flow after tapping the older-month chevron) → switch back to the current month, tap an unset category that had a budget last month → "Last month: $..." chip appears. Tap it → amount field pre-fills with last month's value.
- [ ] **Step 6:** UI inspector confirms layout bounds.
- [ ] **Step 7: PR**

```bash
gh pr create --title "feat: budget Phase 2 — single-category edit sheet" --body ...
```

Update Phase 2 row in the [roadmap status tracker](2026-05-13-budget-roadmap.md#phase-index--status-tracker) to `▶ In progress (PR #N)` when opening the PR; flip to `✅ Merged (PR #N)` after merge.

---

## Self-Review

- [ ] Single-edit flow covers user goals #1 (enter limit) and #3 (last-month copy when entering).
- [ ] Cadence-neutral: receives `periodStart`/`periodEnd` as args, never assumes "month".
- [ ] Naming: `BudgetEditComponent`, `OnBudgetSavedHandler`, `OnCategoryTappedHandler`.
