# Account Detail Screen Design

**Issue**: #68 — Clicking on account should open account screen with transactions + balance at top
**Date**: 2026-05-06

## Overview

Add an Account Detail screen that opens when a user taps any account in the Accounts list. The screen shows a collapsible hero card (account balance + monthly stats) above a full transaction list filtered to that account — following the same pattern as Category Detail.

## UI Design (from design file)

### TopBar
- Back arrow (left) + account name (centered) + empty 48dp spacer (right, no 3-dot menu)
- Matches existing CategoryDetail TopBar layout

### Hero Card (collapsible via NestedScrollConnection)
- Background: `SurfaceContainerLow` (positive balance) or `#FFF0F0` light red (negative balance)
- Account icon watermark: top-right, 8% opacity (design: 0.08, CategoryDetail uses 0.15)
- Period label: account's `details` field (e.g. "Checking") or "MAY 2026" — 11sp bold, accent color 75% alpha
- Balance: 34sp ExtraBold, `Primary` color (positive) or `Error` color (negative), -0.02em letter spacing
- Stats row (three columns, gap 24dp):
  - `IN THIS MONTH`: value in `Secondary` (green), 17sp bold, with "+" prefix
  - `OUT THIS MONTH`: value in `OnSurface`, 17sp bold, with "–" prefix
  - `TRANSACTIONS`: value in `OnSurface`, 17sp bold

### Transaction List
- Section header: "Transactions this month" — 11sp bold, OnSurfaceVariant, uppercase, 0.09em letter spacing
- Empty state: "No transactions yet" centered
- Reuses `TransactionComponent` with `TransactionFilter.ForAccount(accountId)` (shows all transaction types for that account)
- Income amounts shown in green with "+" prefix (handled by existing TransactionViewProvider)

### No FAB
The design has no floating action button on Account Detail (unlike Category Detail).

## Architecture

Follows the established **Component → ViewModel → ViewProvider** pattern, mirroring `CategoryDetailComponent`.

### New files in `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/`
```
AccountDetailViewModel.kt               — interface (State + Action)
DefaultAccountDetailViewModel.kt        — implementation
AccountDetailViewProvider.kt            — Composable UI
AccountDetailComponent.kt               — Dagger component
AccountDetailSpendingUseCase.kt         — interface for stats
DefaultAccountDetailSpendingUseCase.kt  — implementation
OnAccountDetailEditHandler.kt           — fun interface (optional, for future edit nav)
```

### AccountDetailViewModel.State
```kotlin
data class State(
    val accountName: String = "",
    val accountIcon: Image = Image.empty(),
    val accountDetails: String? = null,
    val balance: Amount = Amount.zero(),
    val currencySymbol: String = "",
    val isNegativeBalance: Boolean = false,
    val periodDate: LocalDate? = null,
    val totalIn: Amount = Amount.zero(),
    val totalOut: Amount = Amount.zero(),
    val transactionCount: Int = 0,
)
```

### AccountDetailSpendingUseCase
Queries `TransactionRepository.Criteria.ForAccountBetween(accountId, from, to)` and aggregates:
- `totalIn`: sum of Income transaction amounts (currency-converted to primary)
- `totalOut`: sum of Expense transaction amounts (currency-converted to primary)
- `transactionCount`: count of all non-Transfer transactions

## Data Layer Changes

### `zero-core/transactions/TransactionFilter.kt`
Add: `data class ForAccount(val accountId: Id.Known) : TransactionFilter`

### `zero-api/transactions/TransactionRepository.kt`
Add two new Criteria:
```kotlin
data class ForAccount(val accountId: Id.Known) : Criteria<List<Transaction>>
data class ForAccountBetween(
    val accountId: Id.Known,
    val from: LocalDate,
    val to: LocalDate,
) : Criteria<List<Transaction>>
```

### `zero-database/transactions/TransactionRoom.kt`
Add two Room DAO queries:
```sql
-- ForAccount: all non-deleted transactions for account, newest first
SELECT * FROM TransactionEntity
WHERE userId = :userId AND accountId = :accountId AND deletedAt IS NULL
ORDER BY datetime(enteredDateTime) DESC

-- ForAccountBetween: same but bounded by date range
SELECT * FROM TransactionEntity
WHERE userId = :userId AND accountId = :accountId AND deletedAt IS NULL
  AND date(enteredDateTime) >= date(:from) AND date(enteredDateTime) <= date(:to)
ORDER BY datetime(enteredDateTime) DESC
```

### `zero-database/transactions/RoomTransactionRepository.kt`
Handle the two new Criteria in the `when` block.

### `zero-core/transactions/DefaultTransactionViewModel.kt`
Add `forAccountTransactionsFlow(filter: TransactionFilter.ForAccount)` case in the `when(filter)` block, mirroring `forCategoryTransactionsFlow`.

## Shared UI Extraction (unification with CategoryDetail)

Extract from `CategoryDetailViewProvider` into `zero-ui`:
1. `DetailStatColumn` composable (label + value, currently private in CategoryDetailViewProvider — identical pattern needed in AccountDetail)
2. `DetailTopBar` composable (back + title + optional trailing slot)

CategoryDetailViewProvider will be updated to use these shared composables.

## Navigation Changes

### `app/navigation/Destinations.kt`
```kotlin
sealed interface Account : Destination {
    object All : Account, Destination by destinationOf("accounts")
    object Edit : Account, Destination by destinationOf("accounts/edit")
    sealed interface Item : Account {
        object AccountId : Argument<Id.Known> by idKnownValueOf("accountId")
        object Detail : Item, Destination by destinationOf("accounts/{accountId}", AccountId)
    }
}
```

### `AccountViewModel`
Add `Action.Select(accountId: Id.Known)` and `OnAccountSelectedHandler` (fun interface).

### `AccountComponent`
Add `@BindsInstance fun onAccountSelectedHandler(handler: OnAccountSelectedHandler): Builder`

### `AccountViewProvider`
Make `AccountRow` clickable: dispatches `AccountViewModel.Action.Select(account.id)`

### `MainActivityScreenComponent`
- Add `accountDetailComponentBuilder: AccountDetailComponent.Builder` to Dependencies
- Add navigation entry for `Destinations.Account.Item.Detail`
- Update `accountNavigationEntry` to pass `onAccountSelectedHandler` that navigates to Detail

## Income Page Note
Income transactions in the account detail transaction list are displayed in green with a "+" prefix by the existing `TransactionViewProvider` (already handles `TransactionViewModel.Item.Transaction.Income`). No separate "income page" feature needed.

## Verification
- Build passes: `./gradlew :zero-core:assembleDebug :zero-database:assembleDebug :app:assembleDebug`
- Lint: `./gradlew lintDebug`
- UI inspection: `android-ui-inspector` confirms hero card bounds, account name in top bar, transaction list items visible
