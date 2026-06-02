# Transaction-edit currency-exchange UX — unified Exchange-rate field

## Goal

Rework the foreign-exchange UX on the transaction edit screen to match the Claude Design
`ui_kits/zero/index.html` (`AddTransactionSheet`). The design consolidates the rate UX into a
single shared boxed component — `RateFieldRow` (label **"Exchange rate"**) — used by both modes:

- **Expense / Income** — replace the plain `OutlinedTextField` rate field with the
  Exchange-rate tile: a tappable rate row (`1 {srcSym} = {rate} {dstSym}`, auto default + Reset),
  plus a second line below a divider showing the converted total
  (`Converts to · {account currency} ≈ {sym}{amount}`).
- **Transfer** — replace the current 3-mode rate pill (`Default` / `CustomRate` / `CustomAmount`
  cycling) with side-by-side **"From amount" / "To amount"** fields and the same Exchange-rate tile
  (without the converted line) below them; editing any one of {from, rate, to} keeps the other two
  consistent. Auto rate by default with reset.

Both modes are driven by the existing inline `AmountKeypad`, which edits whichever field is focused
(`Amount` / `Rate` / `Received`).

The design file still defines older `ConversionCard` / transfer-variant branches, but
`AddTransactionSheet` renders `AmountField` + `RateFieldRow` + `TransferBlock` — those are the live
components; the rest is dead code.

## Out of scope (not the rate UX)

- Re-boxing the expense/income hero amount into the design's `AmountField` chip — the existing
  pinned `AmountDisplay` stays for expense/income. (Transfer *does* get the boxed From/To amount
  fields, because transfer has two amounts and no pinned hero.)
- The design's new category picker (`CategoryFieldRow` + quick chips) — unchanged.
- The design's bottom-sheet account picker — transfer keeps the existing `SelectorCard` dropdown
  on the From/To tiles.
- Currency picker behaviour, notes, saved-transaction persistence format — unchanged.

## Shared model

- **`TransactionEditFocusTarget`** — new enum `{ Amount, Rate, Received }`: which field the inline
  keypad drives. Expense/income use `Amount`/`Rate`; transfer uses all three.
- The rate is a `String` + `rateAuto: Boolean` on the shared `TransactionEditUseCase` (single
  source of truth for parent + child VMs). Auto-derived from the active currency pair via
  `CurrencyConvertUseCase.getRate` by one reactive collector in `attach()`:
  - expense/income pair = (`selectedCurrency.id`, `selectedAccount.currencyId`)
  - transfer pair = (`selectedAccount.currencyId`, `selectedTargetAccount.currencyId`)

  When `rateAuto` and the pair differs, it sets the formatted rate (and, for transfer, recomputes
  the received amount).
- **Actions** (shared): `ChangeAmount`, `ChangeRate` (sets `rateAuto = false`), `ChangeTargetAmount`
  (transfer "received" edit), `ResetRate` (re-derive + `rateAuto = true`), `FocusAmount`,
  `FocusRate`, `FocusReceived`. Account/currency selection and swap set `rateAuto = true` and
  `editTarget = Amount`; the collector recomputes.
- **Live-linking (transfer only), 2-dp rounding:**
  - `ChangeAmount(v)` → `received = v × rate`
  - `ChangeRate(v)` → `received = amount × v`, `rateAuto = false`
  - `ChangeTargetAmount(v)` → `amount = v ÷ rate` (when `rate > 0`)
  - `ResetRate` → re-derive rate, `received = amount × rate`

## Shared composable — `TransactionEditRateField`

One boxed tile (replaces the prior separate ConversionCard / RateConnector). Token usage models the
existing transfer `RateModePill`.

- Container: `surfaceLow` rounded box; `surface` fill + `1.5dp primaryContainer` border when focused.
- Top row: `EXCHANGE RATE` label (left) and the rate group (right):
  `1{srcSym} = {rate} {dstSym}` with a caret when focused, then **Reset** (refresh) once manual or a
  subtle **pencil** when auto.
- Optional second line (expense/income only), above a `surfaceContainer` divider:
  `Converts to · {accountCurrencyName}` (left) + `≈ {dstSym}{convertedAmount}` (right).
- Tapping the tile emits `FocusRate`; tapping Reset emits `ResetRate`.

The converted string is formatted in the use case (`AmountFormatter`) so the VM/ViewProvider do no
derivation (zero-core `ViewProviderDerivation`).

## Expense/Income

Shown when `selectedCurrency.id != selectedAccount.currencyId`: render `TransactionEditRateField`
with the converted line. Deletes `TransactionEditRateTextField`. Hero amount unchanged.

## Transfer

Replaces `RateModePill` + `AccountSelectorsWithSwap`. `TransferRateMode` and its actions
(`CycleTransferRateMode`, `ChangeTransferRate`) and file are removed; `State.Transfer` now keeps
`amount`, `targetAmount`, `rate`, `rateAuto`, `editTarget`.

- **Currencies differ:** Row of two `TransactionEditAmountField`s — **From amount** (source symbol,
  `amount`) and **To amount** (target symbol, `targetAmount`) — each tappable to focus; the
  `TransactionEditRateField` (no converted line) below.
- **Currencies match:** a single **Amount** `TransactionEditAmountField`.
- **Account row (constant):** `From` tile + a narrow vertical swap bar + `To` tile (reuse
  `SelectorCard`, laid out horizontally).
- Date picker below.

New composables: `TransactionEditAmountField` (boxed caption + currency symbol + right-aligned value
+ focus caret) and `TransactionEditRateField` (shared, in `common`).

## Parent screen wiring

`TransactionEditViewProvider`:
- Hide the pinned `AmountDisplay` when `selectedTransactionType == TRANSFER`.
- Route the inline keypad by `editTarget`: `amount`/`ChangeAmount`, `rate`/`ChangeRate` (6 decimals),
  `targetAmount`/`ChangeTargetAmount`. Keep the keypad visible on transfer (no hero to tap).
- Tapping the expense/income hero amount emits `FocusAmount`.

`TransactionEditViewModel.State` gains `rate`, `targetAmount`, `editTarget`. New parent actions
`ChangeRate`, `ChangeTargetAmount`, `FocusAmount`.

## Testing

- `AmountKeypadTest`: 6-decimal cap accepts a 6th decimal, rejects a 7th; 2-dp default unchanged.
- `android-ui-inspector`:
  - Expense, currencies differ → Exchange-rate tile renders (rate row + Converts-to line); keypad
    edits the rate (caret, 6 dp, converted updates), Reset restores auto, tapping amount returns
    focus to amount. Same-currency hides the tile.
  - Transfer, differing-currency accounts → From amount / To amount + Exchange-rate tile; editing
    From updates To, editing To back-computes From, editing rate updates To and shows Reset; swap
    stays consistent; same-currency accounts show a single Amount field.
