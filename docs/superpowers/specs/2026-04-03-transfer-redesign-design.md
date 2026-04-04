# Transfer Screen Redesign

## Overview

Redesign the transfer transaction screen to match the Stitch "Manual Transfer - Harmonized Style" design, with a prominent amount display, smart rate mode cycling, account selectors with swap, and date picker. Reuses existing shared components from the Quick Add redesign (PR #6).

Reference design: Stitch project `9891740845259743556`, screen `63b0c4b0c4cd4359b4a05211b6f5ea89` ("Manual Transfer - Harmonized Style")

## Scope

**In scope:**
- Large centered amount display (reuse `AmountDisplay` without currency dropdown)
- Rate mode pill — clickable button cycling through 3 modes
- From/To account selectors (reuse `SelectorCard`) with swap button
- Date picker (reuse `DatePickerCard`)
- FAB save button (already in parent `TransactionEditViewProvider`)
- UseCase extensions: target amount, rate mode, swap accounts, rate fetching via `CurrencyConvertUseCase`

**Out of scope:**
- Currency labels near amounts
- Tags, notes, recurring, receipt
- Any UI elements not listed above

## Layout

```
AMOUNT                    (AmountDisplay, no currency dropdown)
  0.00

[ Rate pill — clickable ]
  DEFAULT:       "Receives 1,000.00" (same currency) / "Receives ₴43,000.00 · 1 USD = 43 UAH" (different)
  CUSTOM_RATE:   Rate input field + computed destination shown read-only
  CUSTOM_AMOUNT: Destination amount input field

[ From account  ▾ ]      SelectorCard
      [🔄 swap]          circular button overlapping between cards
[ To account    ▾ ]      SelectorCard

[ Date          ]        DatePickerCard
```

## Rate Mode State Machine

```kotlin
sealed interface TransferRateMode {
    // Collapsed. No separate destination field.
    // Same currency: pill shows "Receives 1,000.00"
    // Different currency: pill shows "Receives ₴43,000.00 · 1 USD = 43 UAH"
    data class Default(val rate: Rate) : TransferRateMode

    // Expanded. User edits rate, destination amount auto-calculated and shown read-only.
    data class CustomRate(val rate: String) : TransferRateMode

    // Expanded. User edits destination amount directly.
    data class CustomAmount(val targetAmount: String) : TransferRateMode
}
```

### Cycle

Always 3 modes: `Default` → `CustomRate` → `CustomAmount` → `Default`.

For same-currency accounts, `Default` has `Rate.Same` (1:1). The cycle still works — user can override the amount even for same-currency transfers.

### Auto-reset

When accounts change (selection or swap) and currencies differ, mode resets to `Default` with a freshly fetched rate from `CurrencyConvertUseCase.getRate(fromCurrencyId, toCurrencyId)`.

### Derived values

- **Default mode**: `targetAmount = amount * rate`
- **CustomRate mode**: `targetAmount = amount * customRate` (auto-calculated, displayed read-only)
- **CustomAmount mode**: rate is auto-calculated as `targetAmount / amount` (displayed in pill, read-only)

## Architecture

All events follow existing pattern: View dispatches Action → ViewModel maps to UseCase Action → UseCase.perform updates MutableStateFlow → state flows to ViewModel → ViewModel maps to view State → UI recomposes.

### UseCase layer

**New `TransactionEditUseCase.Action` entries:**
- `CycleTransferRateMode` — advance to next mode in the cycle
- `ChangeTargetAmount(amount: String)` — user edits destination amount (CustomAmount mode)
- `ChangeTransferRate(rate: String)` — user edits rate (CustomRate mode)
- `SwapAccounts` — swap source and target accounts

**Extended `TransactionEditUseCase.State.Transfer`:**
```kotlin
data class Transfer(
    val accounts: List<TransactionEditAccount> = emptyList(),
    val selectedAccount: TransactionEditAccount? = null,
    val targetAccounts: List<TransactionEditAccount> = emptyList(),
    val selectedTargetAccount: TransactionEditAccount? = null,
    val amount: String = "",
    val targetAmount: String = "",
    val transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same),
    val sourceCurrencySymbol: String = "",
    val targetCurrencySymbol: String = "",
    val date: Long = 0L,
) : State
```

**New dependency:**
- `CurrencyConvertUseCase` added to `TransactionEditComponent.Dependencies` and injected into `DefaultTransactionEditUseCase`

**CompositeState extensions:**
- `targetAmount: String = ""`
- `transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same)`

**Rate fetching:**
- When source or target account changes (and they have different currencies), launch coroutine to call `currencyConvertUseCase.getRate(fromCurrencyId, toCurrencyId)` and update `transferRateMode` to `Default(fetchedRate)`.
- When same currency, set `transferRateMode` to `Default(Rate.Same)`.

**Save logic fix:**
- Current: `targetAmount = Amount(state.amount.toBigDecimalOrNull())` (copies source amount)
- New: compute actual target amount based on rate mode:
  - `Default` / `CustomRate`: `amount * rate`
  - `CustomAmount`: use `targetAmount` directly from state

### ViewModel layer

**`TransactionEditTransferViewModel.Action`** — new entries:
- `CycleRateMode`
- `ChangeTargetAmount(amount: String)`
- `ChangeTransferRate(rate: String)`
- `SwapAccounts`

**`TransactionEditTransferViewModel.State`** — extended:
- `targetAmount: String = ""`
- `transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same)`
- `sourceCurrencySymbol: String = ""`
- `targetCurrencySymbol: String = ""`
- `date: Long = 0L`

**`DefaultTransactionEditTransferViewModel`** — maps new actions to UseCase actions, maps new state fields from `TransactionEditUseCase.State.Transfer`.

### View layer

**`TransactionEditTransferViewProvider.kt`** — full rewrite of composable:
- Extend `AmountDisplay` with a `showCurrencySelector: Boolean = true` parameter. Transfer passes `false` to hide the currency symbol and dropdown arrow entirely.
- New `RateModePill` private composable — renders current mode, handles click to dispatch `CycleRateMode`
- Reuse `SelectorCard` for From/To accounts
- New `SwapButton` private composable — circular button between account cards, dispatches `SwapAccounts`
- Reuse `DatePickerCard`

### Files modified

| File | Change |
|------|--------|
| `zero-api/.../currencies/CurrencyConvertUseCase.kt` | No change (already has `getRate`) |
| `zero-core/.../transactions/edit/TransferRateMode.kt` | **Create** — sealed interface |
| `zero-core/.../transactions/edit/TransactionEditUseCase.kt` | Add 4 actions, extend Transfer state |
| `zero-core/.../transactions/edit/DefaultTransactionEditUseCase.kt` | Handle new actions, add `CurrencyConvertUseCase` dep, rate fetching, fix save logic |
| `zero-core/.../transactions/edit/TransactionEditComponent.kt` | Add `CurrencyConvertUseCase` to Dependencies, pass to UseCase |
| `zero-core/.../transactions/edit/transfer/TransactionEditTransferViewModel.kt` | Add 4 actions, extend state |
| `zero-core/.../transactions/edit/transfer/DefaultTransactionEditTransferViewModel.kt` | Map new actions/state |
| `zero-core/.../transactions/edit/transfer/TransactionEditTransferViewProvider.kt` | Full rewrite |

### Files not modified

- `TransactionEditViewProvider.kt` (parent layout with toggle + FAB — unchanged)
- Expense/income views — unchanged
- `AmountDisplay.kt` — minor extension (`showCurrencySelector` param), backwards compatible
- `SelectorCard.kt`, `DatePickerCard.kt` — reused as-is
