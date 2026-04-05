# Implementation Plan: Replace LocalShowAllCategories + Move CategoryPicker Builder to App Scope

**Branch:** `feature/category-bottom-sheet` (continue on existing branch)

## Goal

Two cleanups:
1. Replace the `LocalShowAllCategories` CompositionLocal with a proper `OnShowAllCategoriesHandler` fun interface, following the existing handler convention
2. Move `CategoryPickerComponent.Builder` creation from `TransactionEditComponent` scope up to `ActivityComponent` scope, making it an externally-provided dependency

## Reference Convention

Look at how `OnEditCategoriesHandler`, `OnDiscardHandler`, `OnTransactionSavedHandler` work:
- Defined as `fun interface` in `zero-core`
- Passed via `@BindsInstance` on the Component Builder
- Default is `Noop` in the companion `builder()` function
- Wired at the app scope in `MainActivityScreenComponent`

Also look at how other component builders like `CategoryComponent.Builder` are created in `ActivityComponent.Module` and passed through `MainActivityScreenComponent.Dependencies`.

---

## Task 1: Create `OnShowAllCategoriesHandler` fun interface

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnShowAllCategoriesHandler.kt` (NEW)

```kotlin
package com.hluhovskyi.zero.transactions.edit

fun interface OnShowAllCategoriesHandler {
    fun onShowAll()

    object Noop : OnShowAllCategoriesHandler {
        override fun onShowAll() = Unit
    }
}
```

**Commit:** `feat: add OnShowAllCategoriesHandler fun interface`

---

## Task 2: Add `OnShowAllCategoriesHandler` to expense and income components

### 2a. `TransactionEditExpenseComponent.kt`

Add `@BindsInstance fun onShowAllCategoriesHandler(handler: OnShowAllCategoriesHandler): Builder` to the Builder interface.

In the companion `builder()` function (if it has one setting defaults), add `.onShowAllCategoriesHandler(OnShowAllCategoriesHandler.Noop)`.

**Wait** — `TransactionEditExpenseComponent` doesn't have a companion with defaults. Its builder is created in `TransactionEditComponent.Module`. So just add the `@BindsInstance` method to the Builder. The default will be set where the builder is used.

Update `TransactionEditComponent.Module.transactionEditExpenseComponentBuilder`:
```kotlin
fun transactionEditExpenseComponentBuilder(
    component: TransactionEditComponent,
    useCase: TransactionEditUseCase,
    onShowAllCategoriesHandler: OnShowAllCategoriesHandler,
): TransactionEditExpenseComponent.Builder =
    TransactionEditExpenseComponent.builder(component)
        .transactionEditUseCase(useCase)
        .onShowAllCategoriesHandler(onShowAllCategoriesHandler)
```

### 2b. `TransactionEditIncomeComponent.kt`

Same as expense — add `@BindsInstance fun onShowAllCategoriesHandler(handler: OnShowAllCategoriesHandler): Builder`.

Update `TransactionEditComponent.Module.transactionEditIncomeComponentBuilder` similarly.

### 2c. `TransactionEditExpenseViewProvider.kt`

- Add `onShowAllCategoriesHandler: OnShowAllCategoriesHandler` constructor param
- Replace `val onShowAll = LocalShowAllCategories.current` with using the handler
- Pass `onShowAll = { onShowAllCategoriesHandler.onShowAll() }` to `CategoryScrollRow`
- Remove import of `LocalShowAllCategories`

Update the ViewProvider provides in `TransactionEditExpenseComponent.Module`:
```kotlin
fun viewProvider(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader,
    onShowAllCategoriesHandler: OnShowAllCategoriesHandler,
): ViewProvider = TransactionEditExpenseViewProvider(
    viewModel = viewModel,
    imageLoader = imageLoader,
    onShowAllCategoriesHandler = onShowAllCategoriesHandler,
)
```

### 2d. `TransactionEditIncomeViewProvider.kt`

Same pattern as expense.

Update the ViewProvider provides in `TransactionEditIncomeComponent.Module`:
```kotlin
fun viewProvider(
    viewModel: TransactionEditIncomeViewModel,
    imageLoader: ImageLoader,
    onShowAllCategoriesHandler: OnShowAllCategoriesHandler,
): ViewProvider = TransactionEditIncomeViewProvider(
    viewModel = viewModel,
    imageLoader = imageLoader,
    onShowAllCategoriesHandler = onShowAllCategoriesHandler,
)
```

**Commit:** `refactor: replace LocalShowAllCategories with OnShowAllCategoriesHandler`

---

## Task 3: Wire `OnShowAllCategoriesHandler` in TransactionEditComponent

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

Add `@BindsInstance fun onShowAllCategoriesHandler(handler: OnShowAllCategoriesHandler): Builder` to the `TransactionEditComponent.Builder` interface.

In the companion `builder()`, add `.onShowAllCategoriesHandler(OnShowAllCategoriesHandler.Noop)` as default.

The `Module.transactionEditExpenseComponentBuilder` and `Module.transactionEditIncomeComponentBuilder` methods now need `onShowAllCategoriesHandler` — Dagger will provide it since it's a `@BindsInstance`.

**Commit:** `feat: add OnShowAllCategoriesHandler to TransactionEditComponent.Builder`

---

## Task 4: Update TransactionEditViewProvider — remove LocalShowAllCategories, wire handler to sheet

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

1. Remove `CompositionLocalProvider(LocalShowAllCategories provides ...)` wrapping entirely
2. Remove imports of `LocalShowAllCategories` and `CompositionLocalProvider`
3. The bottom sheet show/hide is now triggered by the handler, which is wired externally

**But wait** — the `onShowAllCategoriesHandler` is set at component build time (before Compose), and we need it to call `sheetState.show()` which only exists in the Compose scope. We need a bridge.

**Solution:** Add `onShowAllCategoriesHandler: OnShowAllCategoriesHandler` to `TransactionEditViewProvider` constructor. In the composable, use a `LaunchedEffect` or `rememberUpdatedState` pattern to register the sheet-showing behavior. Actually simpler: make the `TransactionEditViewProvider` accept the handler, but inside Compose, wire it so the handler calls `sheetState.show()`.

Actually, the cleanest approach following existing conventions: the `OnShowAllCategoriesHandler` passed to expense/income components is what they call when user taps "All". The `TransactionEditComponent` creates this handler and makes it trigger the bottom sheet.

But the bottom sheet state is in Compose... The handler is created at DI time...

**Better approach:** The handler callback is still created in the `TransactionEditComponent.Module.viewProvider()` method. But we can't create the `ModalBottomSheetState` there since it's DI time, not Compose time.

**Cleanest approach:** Keep the current pattern where `TransactionEditViewProvider` owns the bottom sheet state. The `OnShowAllCategoriesHandler` passed down to expense/income components invokes a lambda that `TransactionEditViewProvider` sets up in Compose.

Implementation:
1. `TransactionEditViewProvider` creates a `var onShowAllCallback: (() -> Unit)? = null` mutable field
2. In the Compose function, set `onShowAllCallback = { focusManager.clearFocus(); coroutineScope.launch { sheetState.show() } }`
3. The `OnShowAllCategoriesHandler` passed to expense/income just calls `onShowAllCallback?.invoke()`

Wait, this is essentially what LocalShowAllCategories does but worse. Let me rethink.

**Alternative: keep the handler flow pure.** The expense/income ViewProviders call the handler. The handler triggers an action on the use case or a shared state. The TransactionEditViewProvider observes that state and opens the sheet.

Add to `TransactionEditViewModel`:
- `Action.ShowAllCategories`  
- `State.showCategoryPicker: Boolean`

When `ShowAllCategories` action fires, set `showCategoryPicker = true`. When sheet hides (or category selected), set back to `false`.

In `TransactionEditViewProvider`:
```kotlin
LaunchedEffect(state.showCategoryPicker) {
    if (state.showCategoryPicker && !sheetState.isVisible) {
        focusManager.clearFocus()
        sheetState.show()
    }
}
```

And when sheet is dismissed, reset:
```kotlin
LaunchedEffect(sheetState.currentValue) {
    if (sheetState.currentValue == ModalBottomSheetValue.Hidden) {
        viewModel.perform(TransactionEditViewModel.Action.DismissCategoryPicker)
    }
}
```

The `OnShowAllCategoriesHandler` wired in the component module:
```kotlin
.onShowAllCategoriesHandler { useCase.perform(TransactionEditUseCase.Action.ShowAllCategories) }
```

This keeps the entire flow reactive and follows the existing action/state pattern.

### Changes to TransactionEditUseCase

Add actions:
```kotlin
object ShowAllCategories : Action
object DismissCategoryPicker : Action
```

Add to all State variants:
NO — better to track it in the CompositeState and expose it via TransactionEditViewModel only. The use case doesn't need to know about UI state. Instead:

### Changes to TransactionEditViewModel

Add actions and state:
```kotlin
sealed interface Action {
    // ... existing
    object ShowAllCategories : Action
    object DismissCategoryPicker : Action
}

data class State(
    // ... existing
    val showCategoryPicker: Boolean = false,
)
```

In `DefaultTransactionEditViewModel`, handle these locally (don't forward to use case):
```kotlin
private val _showCategoryPicker = MutableStateFlow(false)

override val state = combine(useCase.state, _showCategoryPicker) { useCaseState, showPicker ->
    // ... existing mapping
    .copy(showCategoryPicker = showPicker)
}

override fun perform(action) {
    when (action) {
        is Action.ShowAllCategories -> _showCategoryPicker.value = true
        is Action.DismissCategoryPicker -> _showCategoryPicker.value = false
        else -> // forward to useCase
    }
}
```

### Wire OnShowAllCategoriesHandler

In `TransactionEditComponent.Module.viewProvider()`:
```kotlin
val categoryPickerBuildable = categoryPickerComponentBuilder
    .onCategorySelectedHandler { categoryId ->
        useCase.perform(TransactionEditUseCase.Action.SelectCategoryById(categoryId))
        viewModel.perform(TransactionEditViewModel.Action.DismissCategoryPicker)  
    }
```

Wait, `viewModel` isn't the right thing to call from DI — the handler fires on Main. Actually we can call `perform()` from anywhere since it's just updating a StateFlow.

But cleaner: when category is selected, the `LaunchedEffect(state.selectedCategory)` already hides the sheet, and the `LaunchedEffect(sheetState.currentValue)` already resets `showCategoryPicker`. So the category selection flow is already handled.

For `OnShowAllCategoriesHandler`:
```kotlin
val onShowAllHandler = OnShowAllCategoriesHandler {
    viewModel.perform(TransactionEditViewModel.Action.ShowAllCategories)
}
```

Pass this to the expense/income component builders.

### Updated TransactionEditViewProvider

Remove `CompositionLocalProvider`, remove `LocalShowAllCategories`. Add:
```kotlin
LaunchedEffect(state.showCategoryPicker) {
    if (state.showCategoryPicker && !sheetState.isVisible) {
        focusManager.clearFocus()
        sheetState.show()
    }
}

// Reset showCategoryPicker when sheet is dismissed (by swipe or category selection)
LaunchedEffect(sheetState.currentValue) {
    if (sheetState.currentValue == ModalBottomSheetValue.Hidden && state.showCategoryPicker) {
        viewModel.perform(TransactionEditViewModel.Action.DismissCategoryPicker)
    }
}
```

**Commit:** `refactor: replace LocalShowAllCategories with reactive ViewModel state`

---

## Task 5: Move CategoryPickerComponent.Builder creation to ActivityComponent

### 5a. Make ActivityComponent implement CategoryPickerComponent.Dependencies

**File:** `app/src/main/java/com/hluhovskyi/zero/activity/ActivityComponent.kt`

Add `CategoryPickerComponent.Dependencies` to the `implements` list. `ActivityComponent` already has `imageLoader` and `categoriesQueryUseCase` from its own Dependencies, so it satisfies `CategoryPickerComponent.Dependencies` automatically.

Add to `ActivityComponent.Module`:
```kotlin
@Provides
@ActivityScope
fun categoryPickerComponentBuilder(
    component: ActivityComponent,
): CategoryPickerComponent.Builder = CategoryPickerComponent.builder(component)
```

### 5b. Add `categoryPickerComponentBuilder` to `MainActivityScreenComponent.Dependencies`

**File:** `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

Add to Dependencies:
```kotlin
val categoryPickerComponentBuilder: CategoryPickerComponent.Builder
```

### 5c. Add `categoryPickerComponentBuilder` to `TransactionEditComponent.Dependencies`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

Add to Dependencies interface:
```kotlin
val categoryPickerComponentBuilder: CategoryPickerComponent.Builder
```

Remove `CategoryPickerComponent.Dependencies` from the `implements` list of `TransactionEditComponent` (it no longer creates the builder internally).

Remove the `categoryPickerComponentBuilder` `@Provides` method from `TransactionEditComponent.Module` (it's now provided externally via Dependencies).

Update `viewProvider` provides to get `categoryPickerComponentBuilder` from component's dependencies rather than Dagger graph — actually Dagger will provide it from the Dependencies interface automatically since it's declared there. But we need to make sure it's accessible. Since `TransactionEditComponent.Dependencies` now has `categoryPickerComponentBuilder`, and `ActivityComponent` implements `TransactionEditComponent.Dependencies`, the builder flows through.

Wait — `TransactionEditComponent.Dependencies` is an interface. Adding `categoryPickerComponentBuilder` there means every implementer of this interface must provide it. `ActivityComponent` implements `TransactionEditComponent.Dependencies`, so it needs to provide it. Since `ActivityComponent.Module` already has a `@Provides` for it, Dagger will handle it.

But hold on — the `categoryPickerComponentBuilder` in `TransactionEditComponent.Module` currently creates it from `component` (the `TransactionEditComponent` itself, which implements `CategoryPickerComponent.Dependencies`). Moving it to Dependencies means it comes from outside. We should:

1. Remove `CategoryPickerComponent.Dependencies` from `TransactionEditComponent`'s implements list
2. Add `val categoryPickerComponentBuilder: CategoryPickerComponent.Builder` to `TransactionEditComponent.Dependencies`
3. Remove the `@Provides fun categoryPickerComponentBuilder` from `TransactionEditComponent.Module`
4. The `viewProvider` method still takes `categoryPickerComponentBuilder: CategoryPickerComponent.Builder` — Dagger will resolve it from the dependencies

**Commit:** `refactor: move CategoryPickerComponent.Builder creation to ActivityComponent`

---

## Task 6: Delete LocalShowAllCategories

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/LocalShowAllCategories.kt` — DELETE

**Commit:** `refactor: delete unused LocalShowAllCategories`

---

## Task 7: Build verification

```bash
./gradlew clean assembleDebug
./gradlew testDebugUnitTest
```

Fix any issues. Watch for:
- Dagger errors about missing bindings (make sure all dependencies flow correctly)
- Import cleanup
- The `combine()` in `DefaultTransactionEditViewModel` needs `kotlinx.coroutines.flow.combine` import

**Commit (if fixes needed):** `fix: resolve build issues`

---

## Task 8: Push

```bash
git push
```
