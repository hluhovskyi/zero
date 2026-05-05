# Category Detail Screen Improvements

## Branch
`feature/category-detail-improvements`

## Background
Three UX gaps on the Category Detail screen:
1. Tapping a transaction does nothing (handler was hard-coded to Noop).
2. No way to add a transaction from the screen; users must leave to create one.
3. The Edit icon in the top bar is ambiguous — a three-dot menu is the standard pattern for secondary actions.

---

## Task 1 — Wire transaction tap navigation

**Commit message:** `fix: wire transaction tap navigation on category detail screen`

### 1.1 `zero-core/…/categories/detail/CategoryDetailComponent.kt`
- Add `@BindsInstance fun onTransactionSelectedHandler(handler: OnTransactionSelectedHandler): Builder` to the `Builder` interface.
- In `companion object { fun builder(…) }`, chain `.onTransactionSelectedHandler(OnTransactionSelectedHandler.Noop)`.
- In `Module.transactionComponent(…)`, add `onTransactionSelectedHandler: OnTransactionSelectedHandler` parameter and replace `.onTransactionSelectHandler(OnTransactionSelectedHandler.Noop)` with `.onTransactionSelectHandler(onTransactionSelectedHandler)`.

### 1.2 `app/…/activity/screens/MainActivityScreenComponent.kt`
- In `categoryDetailNavigationEntry`, add:
  ```kotlin
  .onTransactionSelectedHandler { transactionId ->
      navigator.navigateTo(
          Destinations.Transaction.Item.Edit,
          Destinations.Transaction.Item.TransactionId.withValue(transactionId),
      )
  }
  ```

---

## Task 2 — Add "Create transaction" FAB with pre-selected category

### Task 2a — `preSelectedCategoryId` in `TransactionEditComponent` and `DefaultTransactionEditUseCase`

**Commit message:** `feat: support pre-selected category when creating a transaction`

#### 2a.1 `zero-core/…/transactions/edit/TransactionEditComponent.kt`
- Add `@BindsInstance fun preSelectedCategoryId(id: Id): Builder` to the `Builder` interface.
- In `companion object { fun builder(…) }`, chain `.preSelectedCategoryId(Id.Unknown)`.
- In `Module.useCase(…)`, add `preSelectedCategoryId: Id` parameter and pass it to `DefaultTransactionEditUseCase(preSelectedCategoryId = preSelectedCategoryId, …)`.

#### 2a.2 `zero-core/…/transactions/edit/DefaultTransactionEditUseCase.kt`
- Add `private val preSelectedCategoryId: Id = Id.Unknown` constructor parameter.
- In the `categoriesQueryUseCase.queryRanked(…).collectLatest` block, change the fallback category selection from:
  ```kotlin
  state.selectedCategory ?: categories.firstOrNull()
  ```
  to:
  ```kotlin
  (preSelectedCategoryId as? Id.Known)
      ?.let { id -> categories.find { it.id == id } }
      ?: categories.firstOrNull()
  ```
  (Only apply when `state.selectedCategory == null`, i.e. new transaction.)

### Task 2b — Optional `SelectedCategoryId` argument on `Destinations.Transaction.Edit`

**Commit message:** `feat: add optional selectedCategoryId nav argument to Transaction.Edit`

#### 2b.1 `app/…/activity/navigation/Destinations.kt`
- Change:
  ```kotlin
  object Edit : Transaction, Destination by destinationOf("transactions/edit")
  ```
  to:
  ```kotlin
  object Edit : Transaction, Destination by destinationOf("transactions/edit", SelectedCategoryId) {
      object SelectedCategoryId : Argument<Id> by idOptionalValueOf("selectedCategoryId")
  }
  ```

#### 2b.2 `app/…/activity/screens/MainActivityScreenComponent.kt`
- In `transactionEditNavigationEntry`, add:
  ```kotlin
  .preSelectedCategoryId(arguments.getValue(Destinations.Transaction.Edit.SelectedCategoryId))
  ```
  to the `componentBuilder` chain.

### Task 2c — `CreateTransaction` action + handler

**Commit message:** `feat: add CreateTransaction action and handler to CategoryDetailComponent`

#### 2c.1 `zero-core/…/categories/detail/CategoryDetailViewModel.kt`
- Add `object CreateTransaction : Action` to the `Action` sealed interface.

#### 2c.2 `zero-core/…/categories/detail/OnCategoryDetailCreateTransactionHandler.kt` (new file)
```kotlin
package com.hluhovskyi.zero.categories.detail

fun interface OnCategoryDetailCreateTransactionHandler {
    fun onCreate()

    object Noop : OnCategoryDetailCreateTransactionHandler {
        override fun onCreate() = Unit
    }
}
```

#### 2c.3 `zero-core/…/categories/detail/DefaultCategoryDetailViewModel.kt`
- Add `private val onCreateTransactionHandler: OnCategoryDetailCreateTransactionHandler` constructor parameter.
- Handle `CategoryDetailViewModel.Action.CreateTransaction` similarly to `Edit`:
  ```kotlin
  CategoryDetailViewModel.Action.CreateTransaction -> coroutineScope.launch(Dispatchers.Main) {
      onCreateTransactionHandler.onCreate()
  }
  ```

#### 2c.4 `zero-core/…/categories/detail/CategoryDetailComponent.kt`
- Add `@BindsInstance fun onCreateTransactionHandler(handler: OnCategoryDetailCreateTransactionHandler): Builder`.
- Default to `OnCategoryDetailCreateTransactionHandler.Noop` in `companion object { fun builder(…) }`.
- In `Module.viewModel(…)`, add `onCreateTransactionHandler: OnCategoryDetailCreateTransactionHandler` parameter and pass it to `DefaultCategoryDetailViewModel`.

### Task 2d — Add FAB to `CategoryDetailViewProvider`

**Commit message:** `feat: add Create Transaction FAB to category detail screen`

#### 2d.1 `zero-core/…/categories/detail/CategoryDetailViewProvider.kt`
- Wrap the existing `Column` in a `Box(Modifier.fillMaxSize())`.
- Add inside the `Box` (after the `Column`):
  ```kotlin
  ExtendedFloatingActionButton(
      modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = 32.dp),
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      text = { Text("Add transaction") },
      onClick = { viewModel.perform(CategoryDetailViewModel.Action.CreateTransaction) },
      elevation = FloatingActionButtonDefaults.elevation(8.dp),
  )
  ```
- Add necessary imports: `ExtendedFloatingActionButton`, `FloatingActionButtonDefaults`, `Icons.Filled.Add`.

### Task 2e — Wire `onCreateTransactionHandler` in navigation

**Commit message:** `feat: wire create-transaction navigation from category detail`

#### 2e.1 `app/…/activity/screens/MainActivityScreenComponent.kt`
- In `categoryDetailNavigationEntry`, add:
  ```kotlin
  .onCreateTransactionHandler {
      navigator.navigateTo(
          Destinations.Transaction.Edit,
          Destinations.Transaction.Edit.SelectedCategoryId.withValue(categoryId),
      )
  }
  ```

---

## Task 3 — Replace Edit button with three-dot overflow menu

**Commit message:** `feat: replace edit button with three-dot overflow menu on category detail`

### 3.1 `zero-core/…/categories/detail/CategoryDetailViewProvider.kt`
In the `TopBar` composable:
- Remove `import androidx.compose.material.icons.filled.Edit`.
- Add imports for `Icons.Filled.MoreVert`, `DropdownMenu`, `DropdownMenuItem`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.setValue`.
- Add `var menuExpanded by remember { mutableStateOf(false) }` at the top of `TopBar`.
- Replace the `IconButton(onClick = { viewModel.perform(Edit) }) { Icon(Icons.Filled.Edit, …) }` with:
  ```kotlin
  Box {
      IconButton(onClick = { menuExpanded = true }) {
          Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = "More options",
              tint = PrimaryContainer,
          )
      }
      DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
      ) {
          DropdownMenuItem(
              onClick = {
                  menuExpanded = false
                  viewModel.perform(CategoryDetailViewModel.Action.Edit)
              },
          ) {
              Text("Edit category")
          }
      }
  }
  ```

---

## Verification

After all tasks, run:

```bash
./gradlew installDebug
./scripts/open-screen.sh categories
```

Navigate to a category detail screen and verify:
1. Tapping a transaction opens the transaction edit screen.
2. The FAB "Add transaction" is visible at the bottom right; tapping it opens the transaction edit screen with the category pre-selected.
3. The top-right icon is three dots; tapping it shows a dropdown with "Edit category"; tapping that navigates to category edit.

Then run:
```bash
./gradlew lintDebug
./gradlew testDebugUnitTest
```

Fix any lint errors or test failures before pushing.
