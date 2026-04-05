# Implementation Plan: Route ShowAllCategories through the action/state chain

**Branch:** `feature/category-bottom-sheet` (continue on existing branch)

## Goal

Fix two architectural problems:
1. `_showCategoryPicker = MutableStateFlow(false)` in `DefaultTransactionEditViewModel` — UI state leaking into the ViewModel implementation. Sheet visibility is a Compose concern and should be driven by state from the UseCase.
2. `OnShowAllCategoriesHandler` passed directly to expense/income ViewProviders — bypasses the ViewModel. Callbacks should never go directly to views; they route through `viewModel.perform()`.

## Correct approach

Add `ShowAllCategories` and `DismissCategoryPicker` to the action chain:
- Expense/Income **ViewProvider** calls `viewModel.perform(Action.ShowAllCategories)` directly
- Expense/Income **ViewModel** maps it to `TransactionEditUseCase.Action.ShowAllCategories`
- **UseCase** sets `showCategoryPicker = true` in `CompositeState`
- `TransactionEditUseCase.State` exposes `showCategoryPicker: Boolean`
- **`TransactionEditViewModel`** maps it from use case state — no `_showCategoryPicker` MutableStateFlow
- **`TransactionEditViewProvider`** observes `state.showCategoryPicker` and drives the sheet

Remove `OnShowAllCategoriesHandler` from the view layer entirely.

---

## Task 1: Add `ShowAllCategories` and `DismissCategoryPicker` to `TransactionEditUseCase`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`

Add to `Action`:
```kotlin
object ShowAllCategories : Action
object DismissCategoryPicker : Action
```

Add `showCategoryPicker: Boolean = false` to the `Expense` and `Income` state variants (not `Transfer` — no category picker there):
```kotlin
data class Expense(
    // ... existing fields ...
    val showCategoryPicker: Boolean = false,
) : State

data class Income(
    // ... existing fields ...
    val showCategoryPicker: Boolean = false,
) : State
```

**Commit:** `feat: add ShowAllCategories/DismissCategoryPicker to TransactionEditUseCase`

---

## Task 2: Handle new actions in `DefaultTransactionEditUseCase`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

In `perform()`, add:
```kotlin
is TransactionEditUseCase.Action.ShowAllCategories -> {
    mutableState.update { state -> state.copy(showCategoryPicker = true) }
}

is TransactionEditUseCase.Action.DismissCategoryPicker -> {
    mutableState.update { state -> state.copy(showCategoryPicker = false) }
}
```

Also update the state mapping (in `mutableState.map { ... }`) to include `showCategoryPicker` in `Expense` and `Income` state:
```kotlin
TransactionEditType.EXPENSE -> TransactionEditUseCase.State.Expense(
    // ... existing fields ...
    showCategoryPicker = state.showCategoryPicker,
)

TransactionEditType.INCOME -> TransactionEditUseCase.State.Income(
    // ... existing fields ...
    showCategoryPicker = state.showCategoryPicker,
)
```

Add `showCategoryPicker: Boolean = false` to `CompositeState`:
```kotlin
private data class CompositeState(
    // ... existing fields ...
    val showCategoryPicker: Boolean = false,
)
```

When `SelectCategoryById` is handled (category selected from picker), also reset `showCategoryPicker`:
```kotlin
is TransactionEditUseCase.Action.SelectCategoryById -> {
    mutableState.update { state ->
        val category = state.categories.firstOrNull { it.id == action.categoryId }
        if (category != null) state.copy(selectedCategory = category, showCategoryPicker = false)
        else state
    }
}
```

**Commit:** `feat: handle ShowAllCategories/DismissCategoryPicker in DefaultTransactionEditUseCase`

---

## Task 3: Add `ShowAllCategories` action to expense/income ViewModels

### 3a. `TransactionEditExpenseViewModel.kt`

Add `object ShowAllCategories : Action` to the `Action` sealed interface.

### 3b. `DefaultTransactionEditExpenseViewModel.kt`

Map it:
```kotlin
is TransactionEditExpenseViewModel.Action.ShowAllCategories ->
    TransactionEditUseCase.Action.ShowAllCategories
```

### 3c. `TransactionEditIncomeViewModel.kt`

Add `object ShowAllCategories : Action`.

### 3d. `DefaultTransactionEditIncomeViewModel.kt`

Map it:
```kotlin
is TransactionEditIncomeViewModel.Action.ShowAllCategories ->
    TransactionEditUseCase.Action.ShowAllCategories
```

**Commit:** `feat: add ShowAllCategories action to expense and income ViewModels`

---

## Task 4: Update expense/income ViewProviders to call `viewModel.perform()` instead of handler

### 4a. `TransactionEditExpenseViewProvider.kt`

- Remove `onShowAllCategoriesHandler: OnShowAllCategoriesHandler` constructor param
- Replace `onShowAll = { onShowAllCategoriesHandler.onShowAll() }` with `onShowAll = { viewModel.perform(TransactionEditExpenseViewModel.Action.ShowAllCategories) }` (inline in the composable — no constructor param needed)
- Remove import of `OnShowAllCategoriesHandler`

The composable function signature simplifies back to just `viewModel` and `imageLoader`:
```kotlin
internal class TransactionEditExpenseViewProvider(
    private val viewModel: TransactionEditExpenseViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider
```

### 4b. `TransactionEditIncomeViewProvider.kt`

Same as expense.

### 4c. `TransactionEditExpenseComponent.kt`

Remove `@BindsInstance fun onShowAllCategoriesHandler(handler: OnShowAllCategoriesHandler): Builder`.

Update the `viewProvider` `@Provides` in `TransactionEditExpenseComponent.Module` — remove `onShowAllCategoriesHandler` param:
```kotlin
fun viewProvider(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader,
): ViewProvider = TransactionEditExpenseViewProvider(
    viewModel = viewModel,
    imageLoader = imageLoader,
)
```

### 4d. `TransactionEditIncomeComponent.kt`

Same — remove `@BindsInstance fun onShowAllCategoriesHandler(...)`.

### 4e. `TransactionEditComponent.kt`

Remove `onShowAllCategoriesHandler: OnShowAllCategoriesHandler` from `Builder`.
Remove it from `companion object { fun builder(...) }` defaults.
Update `Module.transactionEditExpenseComponentBuilder` and `Module.transactionEditIncomeComponentBuilder` to no longer pass `onShowAllCategoriesHandler`.

**Commit:** `refactor: route ShowAllCategories through viewModel.perform instead of handler`

---

## Task 5: Fix `DefaultTransactionEditViewModel` — remove `_showCategoryPicker`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt`

Remove:
- `private val _showCategoryPicker = MutableStateFlow(false)` (if present)
- Any `combine()` that merges it with use case state
- `Action.ShowAllCategories` and `Action.DismissCategoryPicker` handling (if present)

Simplify back to a single `.map { }` on `useCase.state`:
```kotlin
override val state: Flow<TransactionEditViewModel.State> = useCase.state
    .map { state ->
        val (selectedCategory, showCategoryPicker) = when (state) {
            is TransactionEditUseCase.State.Expense -> state.selectedCategory to state.showCategoryPicker
            is TransactionEditUseCase.State.Income -> state.selectedCategory to state.showCategoryPicker
            is TransactionEditUseCase.State.Transfer -> null to false
        }
        TransactionEditViewModel.State(
            transactionTypes = TransactionEditType.values().toList(),
            selectedTransactionType = when (state) {
                is TransactionEditUseCase.State.Expense -> TransactionEditType.EXPENSE
                is TransactionEditUseCase.State.Income -> TransactionEditType.INCOME
                is TransactionEditUseCase.State.Transfer -> TransactionEditType.TRANSFER
            },
            date = state.date,
            selectedCategory = selectedCategory,
            showCategoryPicker = showCategoryPicker,
        )
    }
```

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`

Update `State` to include `showCategoryPicker: Boolean = false` (replace any existing `showCategoryPicker` if it was already there).

Remove `Action.ShowAllCategories` and `Action.DismissCategoryPicker` from `TransactionEditViewModel.Action` (they are no longer needed here — the use case handles them directly). The `TransactionEditViewModel` only exposes state; `DismissCategoryPicker` is now performed on the use case from the ViewProvider.

Update `perform()` in `DefaultTransactionEditViewModel` — remove `ShowAllCategories` and `DismissCategoryPicker` cases (no longer in Action).

**Commit:** `refactor: simplify DefaultTransactionEditViewModel, remove _showCategoryPicker`

---

## Task 6: Update TransactionEditViewProvider to use DismissCategoryPicker action

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

The ViewProvider now observes `state.showCategoryPicker` from use case (via TransactionEditViewModel).

For dismissal when user swipes down the sheet, call the use case action via the viewModel. But `TransactionEditViewModel` no longer has `DismissCategoryPicker` in its action. Instead, wire it directly: the ViewProvider needs access to call `dismiss` on the use case.

**Simplest approach:** Add `DismissCategoryPicker` action back to `TransactionEditViewModel.Action` purely as a passthrough to `TransactionEditUseCase.Action.DismissCategoryPicker`. This is the cleanest: the ViewProvider calls `viewModel.perform(TransactionEditViewModel.Action.DismissCategoryPicker)` and the ViewModel forwards it to the use case.

So in `TransactionEditViewModel`:
```kotlin
object DismissCategoryPicker : Action
```

In `DefaultTransactionEditViewModel.perform()`:
```kotlin
is TransactionEditViewModel.Action.DismissCategoryPicker ->
    TransactionEditUseCase.Action.DismissCategoryPicker
```

In `TransactionEditViewProvider`, the `LaunchedEffect` for sheet state:
```kotlin
LaunchedEffect(sheetState.currentValue) {
    if (sheetState.currentValue == ModalBottomSheetValue.Hidden && state.showCategoryPicker) {
        viewModel.perform(TransactionEditViewModel.Action.DismissCategoryPicker)
    }
}
```

And remove the old `LaunchedEffect(state.showCategoryPicker)` + `LaunchedEffect(state.selectedCategory)` sheet management. Replace with:
```kotlin
// Show sheet when UseCase says so
LaunchedEffect(state.showCategoryPicker) {
    if (state.showCategoryPicker) {
        focusManager.clearFocus()
        sheetState.show()
    } else {
        sheetState.hide()
    }
}
```

Also wire the `onCategorySelectedHandler` in the `categoryPickerComponentBuilder` — when category is selected, the use case already resets `showCategoryPicker = false` (done in Task 2), so the `LaunchedEffect(state.showCategoryPicker)` will hide the sheet automatically. No explicit sheet hide needed in the category selection callback.

Remove the `OnShowAllCategoriesHandler` wiring from `TransactionEditComponent.Module.viewProvider()` since it's gone.

**Commit:** `refactor: drive bottom sheet visibility from UseCase state`

---

## Task 7: Delete `OnShowAllCategoriesHandler.kt`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnShowAllCategoriesHandler.kt` — DELETE

**Commit:** `refactor: delete OnShowAllCategoriesHandler`

---

## Task 8: Build verification

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
```

Watch for:
- Dagger errors — make sure all `@BindsInstance` removals are reflected in `companion object { fun builder(...) }` defaults
- `filterIsInstance` on expense/income state still works (no changes needed there)
- Import cleanup in all modified files

**Commit (if fixes needed):** `fix: resolve build issues`

---

## Task 9: Push

```bash
git push
```

---

## Summary of changes

| File | Change |
|------|--------|
| `TransactionEditUseCase.kt` | Add `ShowAllCategories`, `DismissCategoryPicker` actions; add `showCategoryPicker` to Expense/Income state |
| `DefaultTransactionEditUseCase.kt` | Handle new actions; add `showCategoryPicker` to CompositeState; reset on `SelectCategoryById` |
| `TransactionEditExpenseViewModel.kt` | Add `ShowAllCategories` action |
| `DefaultTransactionEditExpenseViewModel.kt` | Map `ShowAllCategories` to use case |
| `TransactionEditIncomeViewModel.kt` | Add `ShowAllCategories` action |
| `DefaultTransactionEditIncomeViewModel.kt` | Map `ShowAllCategories` to use case |
| `TransactionEditExpenseViewProvider.kt` | Remove handler param; call `viewModel.perform(ShowAllCategories)` |
| `TransactionEditIncomeViewProvider.kt` | Same |
| `TransactionEditExpenseComponent.kt` | Remove `onShowAllCategoriesHandler` from Builder and Module |
| `TransactionEditIncomeComponent.kt` | Same |
| `TransactionEditComponent.kt` | Remove `onShowAllCategoriesHandler` from Builder; clean up Module wiring |
| `TransactionEditViewModel.kt` | Add `showCategoryPicker` to State; add `DismissCategoryPicker` action |
| `DefaultTransactionEditViewModel.kt` | Remove `_showCategoryPicker`; simple `.map {}` on use case state |
| `TransactionEditViewProvider.kt` | Drive sheet from `state.showCategoryPicker`; call `DismissCategoryPicker` on swipe dismiss |
| `OnShowAllCategoriesHandler.kt` | DELETE |
