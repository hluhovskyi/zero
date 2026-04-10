# Plan: Unify Expense and Income Transaction Edit Views

Unify `TransactionEditExpenseViewProvider` and `TransactionEditIncomeViewProvider` into a single `TransactionEditCategoryViewProvider` to reduce duplication and eliminate flickering when switching between Expense and Income tabs.

## User Review Required

> [!IMPORTANT]
> The user has requested to proceed without approval. This plan is for documentation and tracking purposes.

## Proposed Changes

### 1. Unified ViewModel and State (`zero-core`)

- Create `TransactionEditCategoryViewModel` as a common interface for both Expense and Income.
- Create `DefaultTransactionEditCategoryViewModel` that maps `TransactionEditUseCase.State` (either Expense or Income) to a unified state.
- Both Expense and Income sub-components will now use this unified ViewModel.

### 2. Unified View (`zero-core`)

- Move the duplicated UI logic from `TransactionEditExpenseViewProvider` and `TransactionEditIncomeViewProvider` to a shared `TransactionEditCategoryView` in `com.hluhovskyi.zero.transactions.edit.common`.
- Update `TransactionEditCategoryViewProvider` to use this shared view.

### 3. Component Refactoring (`zero-core`)

- Update `TransactionEditComponent` to provide a single `categoryComponent` instead of separate `expenseComponent` and `incomeComponent` if they are identical in structure.
- In `TransactionEditViewProvider`, use the same component instance for both `EXPENSE` and `INCOME` types to prevent `AttachWithView` from re-triggering and causing flickering.

## Detailed Tasks

### Task 1: Create Shared ViewModel and State
- **File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategoryViewModel.kt`
- Define `TransactionEditCategoryViewModel` interface and `State` / `Action` that covers both types.

### Task 2: Implement Unified ViewModel
- **File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/DefaultTransactionEditCategoryViewModel.kt`
- Implement mapping logic from `TransactionEditUseCase`.

### Task 3: Create Unified View Provider
- **File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategoryViewProvider.kt`
- Implement the unified UI.

### Task 4: Refactor `TransactionEditComponent`
- **File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`
- Clean up sub-components and provide the unified one.

### Task 5: Update `TransactionEditViewProvider`
- **File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`
- Update the `when` block to reuse the same component for Expense and Income.

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :zero-core:test`
- Run lint: `./gradlew :zero-core:lint`
- Run ktlint: `./gradlew ktlintCheck`

### Manual Verification
- Use `./scripts/dump-ui.sh` to verify the UI is present and correct after switching tabs.
- Check for flickering manually (if possible via logs/UI dumps).
