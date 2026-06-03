# Merge transaction-edit child forms into the parent — Implementation Plan

> Addresses PR #298 review: focus is a ViewModel concern, not the UseCase. Chosen approach: collapse
> the expense-income + transfer child VMs/components into the parent `TransactionEditViewModel`, so
> one ViewModel owns the keypad, the fields, and focus. The UseCase becomes pure domain.

**Goal:** One `TransactionEditViewModel` + one `TransactionEditViewProvider` render the whole sheet
(header, amount, keypad, type-specific form). Focus (`keypadTarget`) lives in the VM. The UseCase
exposes domain state + domain actions only.

## Target structure

- **Keep:** `TransactionEditUseCase` (+ Default), `TransactionEditComponent`, `TransactionEditViewModel`
  (+ Default), `TransactionEditViewProvider`, form composables (`CategoryScrollRow`,
  `TransactionEditRateField`, `AmountField`).
- **Delete:** `TransactionEditExpenseIncomeComponent`, `TransactionEditTransferComponent`,
  `TransactionEditExpenseIncomeViewModel` (+Default), `TransactionEditTransferViewModel` (+Default),
  `TransactionEditExpenseIncomeViewProvider`, `TransactionEditTransferViewProvider`.

## Steps

### 1. Parent VM owns focus + both forms
- `TransactionEditViewModel.State` gains a sealed `form: Form` (`ExpenseIncome` | `Transfer`) carrying
  the per-type fields, plus shared `keypadTarget: TransactionEditFocusTarget`, `rate`, `targetAmount`.
- Actions absorb the children's: `SelectCategory`, `ShowAllCategories`, `SelectAccount`,
  `SelectTargetAccount`, `SwapAccounts`, `ResetRate`, `FocusAmount/FocusRate/FocusReceived`.
- `Default` maps the UseCase sealed `State` → `Form`; holds `keypadTarget` as VM-local state
  (combine the UseCase flow with a `MutableStateFlow<FocusTarget>`); the keypad routes a digit to
  `ChangeAmount`/`ChangeRate`/`ChangeTargetAmount` by `keypadTarget`. Currency-symbol/name + the
  converted display string are formatted here (inject `AmountFormatter`).

### 2. Parent ViewProvider renders forms inline
- Replace the `AttachWithView()` of the child components with a `when (form)` that renders the
  expense/income form (CategoryScrollRow + date/account + rate tile) or the transfer form (From/To
  AmountFields + rate tile + From/swap/To). Hero amount hidden for transfer. Keypad routes by
  `keypadTarget`.

### 3. DI: drop the sub-components
- `TransactionEditComponent` no longer provides the two builders; `viewProvider` provides just the
  VM. Delete the two component files + their builder `@Provides`.

### 4. UseCase becomes pure domain (PR comments 1, 3, 5)
- Remove `editTarget` from `State.*`/`CompositeState`; remove `FocusAmount/FocusRate/FocusReceived`.
- Remove `convertedAmountText` (UI string) — expose the converted `Amount` (or drop; VM computes
  `amount × rate`). Drop the `AmountFormatter` dependency from the UseCase.
- Keep the live-link math but return `BigDecimal`/`Amount`, not pre-rounded `stripTrailingZeros`
  strings, where the value is domain; the VM does display rounding. `ChangeAmount`/`ChangeRate` no
  longer touch focus.

### 5. UseCase readability (PR comments 2, 4)
- `currenciesDiffer` + symbol lookups → computed members on `CompositeState`.
- Extract the auto-derive collector to `autoDeriveRateOnPairChange()` with real names
  (`sourceCurrencyId`/`targetCurrencyId`, a `RateKey` data class).

### 6. Test bridge fixtures (PR comment 6)
- Extract `insertWallet()`, `insertFoodCategory()`, `insertBootstrapExpense()` shared by the seed
  methods.

### 7. Verify
- `:zero-core:compileDebugKotlin`, then `testDebugUnitTest lintDebug`.
- Re-run the two FX e2e tests; spot-check expense + transfer FX on device.
