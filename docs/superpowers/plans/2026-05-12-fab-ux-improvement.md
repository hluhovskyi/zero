# FAB UX improvement

## Goal

Across the three main bottom-nav screens (Transactions, Accounts, Categories), display the add-action as:

- **Extended FAB** with "+ Add Transaction / Account / Category" when the list is empty
  (i.e. user has *never* added one).
- **Compact "+" FAB** once at least one item exists.

Also:

- Restyle all FABs to use the design-system navy (`PrimaryContainer` `#0A2351`) on white "+"
  (current default is the green secondary).
- Add a FAB to the Accounts screen (currently it has none — uses an inline green pill in the
  section header). Remove the inline pill.
- Detail screens (AccountDetail, CategoryDetail) and Edit screens (TransactionEdit,
  AccountEdit, CategoryEdit) keep their full-text FABs, but get the new color.

Design reference: `/tmp/design-IXlINGZgIQ6WYD8Q18gvJA/zero-design-system/project/ui_kits/zero/`.
The design FAB is 56dp circular, `primaryContainer` background, white "+", shadow
`0 4px 12px rgba(0,14,47,0.25)`.

## Empty-state rule (Transactions only — the tricky one)

Transactions has filters + search, so "list is empty" can mean two things:

- DB has zero transactions → extended FAB.
- Filter / search returned zero → user has data, just filtered it out → compact FAB.

Implementation: `extended = state.transactions.isEmpty() && state.searchQuery.isBlank()
&& !state.activeFilter.isActive`. No new state field needed — if both filter and search are
inactive and the list is empty, the DB is empty.

Categories and Accounts have no filter/search at the list level, so `isEmpty()` on the
underlying list is sufficient.

## Architecture choice — FAB lives inside ViewProviders

CategoryViewProvider and AccountViewProvider already render their own scaffolding; the
Transactions FAB currently lives in `app/.../TransactionsScreen.kt` because the state isn't
visible from the screen wrapper.

Moving the Transactions FAB into `TransactionViewProvider` gives uniform access to viewmodel
state for empty detection. But `TransactionComponent` is also embedded inside
`CategoryDetailViewProvider` and `AccountDetailViewProvider` — those screens have their *own*
FAB and must not get a second one.

Solution: add `showFab: Boolean = false` to `DisplayConfig`. Only the main Transactions
screen sets it to `true`.

## Steps

### 1. Shared `ZeroFab` composable

Create `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ZeroFab.kt` with signature:

```kotlin
@Composable
fun ZeroFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    text: String,
    modifier: Modifier = Modifier,
)
```

Behavior:
- When `expanded`: render `ExtendedFloatingActionButton` with icon + text.
- When not: render `FloatingActionButton` with icon only.
- Both: `backgroundColor = PrimaryContainer`, `contentColor = Color.White`,
  `elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)`.
- No animation between states (the toggle is rare and M2 doesn't make this cheap; revisit
  later if needed).

### 2. Add `OnAddTransactionHandler` + wire it

- New file `zero-core/src/main/java/com/hluhovskyi/zero/transactions/OnAddTransactionHandler.kt`
  (fun interface, mirrors `OnAddAccountHandler`).
- Add `@BindsInstance fun onAddTransactionHandler(handler: OnAddTransactionHandler): Builder`
  to `TransactionComponent.Builder`. Default to `Noop` in `builder()` companion.
- Inject into `TransactionViewProvider` constructor.
- Update `DisplayConfig` to add `val showFab: Boolean = false`.

### 3. Update `TransactionViewProvider`

- When `displayConfig.showFab`, render `ZeroFab` aligned to bottom-end.
- `expanded = state.transactions.isEmpty() && state.searchQuery.isBlank()
  && !state.activeFilter.isActive`.
- Wrap existing `Column` in the existing `Box` (already a `Box`) — the FAB just goes inside.

### 4. Simplify `TransactionsScreen.kt`

- Remove the FAB. Screen becomes the same thin wrapper as `AccountsScreen` and
  `CategoriesScreen` (just `component.AttachWithView()`).
- Drop the `onTransactionEdit` param.

### 5. Update `MainActivityScreenComponent.transactionNavigationEntry`

- Replace `onTransactionEdit = { navigator.navigateTo(...) }` with
  `.onAddTransactionHandler { navigator.navigateTo(Destinations.Transaction.Edit) }`.
- Append `.displayConfig(DisplayConfig(showFab = true))`.
- Update the `TransactionScreen(...)` call to drop `onTransactionEdit`.

### 6. Update `CategoryViewProvider`

- Replace `ExtendedFloatingActionButton(...)` with `ZeroFab(...)`.
- `expanded = state.categories.isEmpty()`.

### 7. Update `AccountViewProvider`

- Remove the inline green "+ Add" pill from `MyAccountsSectionHeader`. The section header
  becomes just the title, with no trailing action. Update its signature to no longer take
  `onAddAccount`.
- Wrap the `LazyColumn` in a `Box` (currently it's the top-level composable).
- Add a `ZeroFab` aligned to bottom-end with
  `expanded = state.activeAccounts.isEmpty() && state.archivedAccounts.isEmpty()`,
  calling `onAddAccount.onAddAccount()`.

### 8. Update detail-screen FABs (color only)

In both `AccountDetailViewProvider.kt` and `CategoryDetailViewProvider.kt`: replace the
`ExtendedFloatingActionButton` block with `ZeroFab(expanded = true, ...)`. Same onClick,
same text/icon.

### 9. Update edit-screen FABs (color only)

In `TransactionEditViewProvider.kt`, `AccountEditViewProvider.kt`, and
`CategoriesEditViewProvider.kt`: replace `ExtendedFloatingActionButton` with
`ZeroFab(expanded = true, ...)`. Use the existing save string and `Icons.Filled.Check` icon.

### 10. Verification

- `./gradlew testDebugUnitTest` — must pass.
- `./gradlew lintDebug` — must show no new errors.
- UI inspection (the user said emulator acquisition is a separate PR — proceed without it; if
  emulator-based checks aren't possible, note this in PR description).

## Out of scope

- No animation between extended and compact states.
- No changes to the bottom-nav bar.
- No changes to `transactions_add`, `account_add`, `category_add` string resources — current
  capitalization is fine.
- "Budget" tab (also in the bottom nav per the design) is not mentioned in the task — leave
  alone.

## Files touched

New:
- `zero-ui/src/main/java/com/hluhovskyi/zero/ui/ZeroFab.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/OnAddTransactionHandler.kt`

Modified:
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionComponent.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/DisplayConfig.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/accounts/AccountViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/accounts/detail/AccountDetailViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/detail/CategoryDetailViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`
- `app/src/main/java/com/hluhovskyi/zero/activity/screens/TransactionsScreen.kt`
- `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`
