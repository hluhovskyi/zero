# Account Icon Tint on Transaction Screen

**Issue:** #52  
**Branch:** `worktree-fix+account-icon-tint`

## Problem

Account icons on the transaction screen are rendered without tint. They should be tinted with the account's color scheme (primary color), falling back to the category's color scheme if the account has no custom color (i.e., `ColorScheme.Grey`).

Additionally, Income transactions silently drop the `accountIcon` composable in `TransactionViewProvider` — it is never passed to `TransactionIncomeView` despite the parameter existing.

## Root Cause

1. `TransactionViewModel.Item.Transaction.Expense/Income` carries `accountIcon: Image` but no `accountColorScheme` — the color is not available to the view layer.
2. `TransactionViewProvider` renders the account icon via `toComposable` (no tint parameter), while the category icon correctly uses `toTintedComposable`.
3. The Income case in `TransactionViewProvider` doesn't pass `accountIcon` to `TransactionIncomeView` at all.

## Solution

Follow the pattern from `DefaultAccountsQueryUseCase`: resolve the account color scheme inline using `ColorRepository.schemeFor()`. Add it to the state and use it in the ViewProvider for tinting.

## Files Changed

### 1. `zero-core/.../transactions/TransactionViewModel.kt`
Add `accountColorScheme: ColorScheme` to `Item.Transaction.Expense` and `Item.Transaction.Income`.

### 2. `zero-core/.../transactions/DefaultTransactionViewModel.kt`
- Add `colorRepository: ColorRepository` constructor parameter.
- In `resolve()` for Expense and Income, compute:
  ```kotlin
  val accountColorScheme = (account.colorId as? Id.Known)
      ?.let { colorRepository.schemeFor(it) } ?: ColorScheme.Grey
  ```
- Populate `accountColorScheme` in the state items.

### 3. `zero-core/.../transactions/TransactionComponent.kt`
- Add `colorRepository: ColorRepository` to `Dependencies`.
- Pass it to `DefaultTransactionViewModel` in `@Provides fun viewModel(...)`.

### 4. `zero-core/.../transactions/TransactionViewProvider.kt`
For both Expense and Income:
- Compute effective tint: `transaction.accountColorScheme` if not `ColorScheme.Grey`, else `transaction.categoryColorScheme`.
- Render account icon via `imageLoader.View(image, modifier, tint = effectiveTint.primary.toUi())`.
- Pass `accountIcon` for Income (it was missing).

### 5. `zero-core/.../transactions/DefaultTransactionViewModelTest.kt`
- Add `@Mock colorRepository: ColorRepository`.
- Pass it in `createViewModel()`.

## Verification

```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```

Then `zero-project:android-ui-inspector` to confirm tinting on device.
