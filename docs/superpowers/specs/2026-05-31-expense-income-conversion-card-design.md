# Expense/Income currency-exchange UX — ConversionCard

## Goal

On the Expense/Income transaction edit screen, replace the plain Material `OutlinedTextField`
rate field with the design's **ConversionCard**: a live "converts to" preview plus an
inline-keypad-editable rate pill with an auto-derived default and a reset affordance.

Reference: design `ui_kits/zero/index.html` → `ConversionCard` + `AddTransactionSheet`
(`needsFx` branch).

The Transfer flow's `TransferRateMode` (Default / CustomRate / CustomAmount cycling) is
**unchanged** — per the user, keep modes for transfers, drop them for expense/income.

## Current vs target

**Current.** When the transaction currency differs from the account currency,
`State.showRate` reveals `TransactionEditRateTextField` — a system-keyboard `OutlinedTextField`
bound to `state.rate` (a free string with no sensible default).

**Target (design `ConversionCard`).** A card that appears under the same `showRate` condition:

- **Header row:** `CONVERTS TO` (uppercase label) + account-currency name, and on the right
  `≈ {acctSymbol}{converted}` where `converted = amount × rate`, formatted via `AmountFormatter`.
- **Editable rate pill:** `1 {txSymbol} = {rate} {acctSymbol}`. The existing inline
  `AmountKeypad` drives whichever field is focused — amount (2 decimals) or rate (6 decimals).
  Tapping the pill focuses the rate; a blinking caret + primary border show when it's the active
  keypad target.
- **Auto rate + reset:** the rate auto-derives from the currency pair via
  `CurrencyConvertUseCase.getRate` (the same source the transfer flow already uses), re-derived
  when the pair changes. Editing the rate via the keypad marks it manual and surfaces a **Reset**
  affordance (refresh icon) that restores the auto rate. While auto, a subtle edit (pencil) icon
  hints the pill is editable.

## Components / changes

1. **`AmountKeypad` (zero-ui)** — add `maxDecimals: Int = 2` and thread it through
   `handleAmountKeypadKey` (currently hardcodes 2). Default keeps today's behaviour. Cover the
   6-decimal path in `AmountKeypadTest`.

2. **`TransactionEditFocusTarget`** — new enum (`Amount`, `Rate`) in `transactions/edit`.
   Identifies which field the inline keypad edits. Transfer is always `Amount`.

3. **`TransactionEditUseCase` + `DefaultTransactionEditUseCase`** (single source of truth shared
   by parent and child VMs):
   - `State.Expense`/`.Income` gain `editTarget: TransactionEditFocusTarget`, `rateAuto: Boolean`,
     and `convertedAmountText: String` (pre-formatted, e.g. `≈ $1,234.56`). They already carry
     `rate`, `selectedCurrency`, `selectedAccount`.
   - New actions: `FocusAmount`, `FocusRate`, `ResetRate`. `ChangeRate` sets `rateAuto = false`.
     `ResetRate` re-derives the rate and sets `rateAuto = true`. `ChangeAmount`, `SwitchTransaction`,
     and currency/account changes set `editTarget = Amount`.
   - Auto-derive the expense/income rate when the currency pair differs (on currency or account
     change) while `rateAuto` is true, mirroring the transfer flow's `fetchRate`; store as a
     formatted string. When currencies match, `editTarget` stays `Amount`.
   - Compute `convertedAmountText` here (inject `AmountFormatter`) so the ViewModel and
     ViewProvider do no derivation, per the zero-core `ViewProviderDerivation` rule.

4. **`TransactionEditExpenseIncomeViewModel.State`** — expose the semantic, pre-computed shape:
   `rate`, `rateAuto`, `editTarget`, `convertedAmountText`, `accountCurrencyName`,
   `accountCurrencySymbol`, `txCurrencySymbol`, plus existing `showRate`. The `Default` VM maps
   these straight through (no derivation). Add `FocusRate`/`ResetRate` actions.

5. **`TransactionEditConversionCard`** — new composable replacing `TransactionEditRateTextField`
   (delete the latter). Renders the header + editable rate pill from the design using `ZeroTheme`
   tokens; structurally model after the transfer `RateModePill` (same `surfaceContainerLow` card,
   `primaryContainer` accents). Pill `onClick → FocusRate`; reset `onClick → ResetRate`; caret
   shown when `editTarget == Rate`.

6. **`TransactionEditExpenseIncomeViewProvider`** — swap `TransactionEditRateTextField` for
   `TransactionEditConversionCard` inside the existing `AnimatedVisibility(showRate)`.

7. **`TransactionEditViewModel` + parent `TransactionEditViewProvider`** — parent VM `State` gains
   `editTarget` and `rate` (read from the UseCase). The inline keypad routes `onChange` to
   `ChangeAmount` or `ChangeRate` and switches `value` + `maxDecimals` based on `editTarget`.
   Tapping the amount display sends `FocusAmount` (alongside showing the keypad). `keypadVisible`
   stays local parent UI state.

8. **Strings** — add `transaction_edit_converts_to` ("Converts to") and
   `transaction_edit_rate_reset` ("Reset"). The `1 {sym} = {rate} {sym}` pill is composed inline.

## Out of scope

- Transfer rate UX (`TransferRateMode`) — unchanged.
- Currency picker behaviour — unchanged.
- Saved-rate semantics / persistence — unchanged (`Rate(state.rate)` on save).
- A blinking caret on the big amount display — the existing tap-mode display has none; the rate
  pill's focus border + caret are the active-field signal, so amount stays as-is.

## Testing

- `AmountKeypadTest`: 6-decimal cap accepts a 6th decimal and rejects a 7th; 2-decimal default
  unchanged.
- `android-ui-inspector`: with differing currencies, the ConversionCard renders, the keypad edits
  the rate (pill caret + live "converts to"), and Reset restores the auto rate.
