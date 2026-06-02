# Transaction-edit currency-exchange UX — ConversionCard + live-linked transfer

## Goal

Rework the foreign-exchange UX on the transaction edit screen to match the Claude Design
`ui_kits/zero/index.html` (`AddTransactionSheet`):

- **Expense / Income** — replace the plain `OutlinedTextField` rate field with the design's
  **ConversionCard** (simple mode): a live "converts to" preview plus an inline-keypad-editable
  rate pill with an auto-derived default and a reset affordance.
- **Transfer** — replace the current three-mode rate pill (`Default` / `CustomRate` /
  `CustomAmount` cycling) with the design's **live-linked fields**: side-by-side
  **"You send" / "You get"** amount fields and a **rate connector** pill between them; editing any
  one of {send, rate, receive} keeps the other two consistent. Auto rate by default with reset.

Both modes are driven by the existing inline `AmountKeypad`, which edits whichever field is
focused.

The design also defines a `transfer` variant of `ConversionCard`, but `AddTransactionSheet`
renders `TransferBlock` (the dual-field layout) — the `ConversionCard` transfer branch is dead
code. We implement `TransferBlock`.

## Out of scope

- Re-boxing the expense/income hero amount into the design's `AmountField` chip — the existing
  pinned `AmountDisplay` stays for expense/income. (The transfer amount *does* move into the block,
  because transfer has two amounts.)
- Currency picker behaviour, category row, notes — unchanged.
- Saved-transaction persistence format — unchanged.

## Shared model

- **`TransactionEditFocusTarget`** — new enum `{ Amount, Rate, Received }`. Identifies which field
  the inline keypad drives. Expense/income use `Amount`/`Rate`; transfer uses all three; `Received`
  is transfer-only.
- The rate is a `String` plus a `rateAuto: Boolean`, on the shared `TransactionEditUseCase` (single
  source of truth for parent + child VMs). Auto-derived from the active currency pair via
  `CurrencyConvertUseCase.getRate` by a single reactive collector in `attach()`:
  - expense/income pair = (`selectedCurrency.id`, `selectedAccount.currencyId`)
  - transfer pair = (`selectedAccount.currencyId`, `selectedTargetAccount.currencyId`)

  When `rateAuto` and the pair differs, it sets the formatted rate (and, for transfer, recomputes
  the received amount).
- **Actions** (shared): `ChangeAmount`, `ChangeRate` (sets `rateAuto = false`), `ResetRate`
  (re-derive + `rateAuto = true`), `FocusAmount`, `FocusRate`. Transfer adds `FocusReceived` and
  reuses `ChangeTargetAmount` as the "received" edit. Account/currency selection and swap set
  `rateAuto = true` and `editTarget = Amount`; the reactive collector recomputes.
- **Live-linking (transfer only), 2-dp rounding:**
  - `ChangeAmount(v)` → `received = v × rate`
  - `ChangeRate(v)` → `received = amount × v`, `rateAuto = false`
  - `ChangeTargetAmount(v)` → `amount = v ÷ rate` (when `rate > 0`)
  - `ResetRate` → `rate = derived`, `received = amount × rate`

## Expense/Income — ConversionCard (simple mode)

Same as the prior spec revision. Shown when `selectedCurrency.id != selectedAccount.currencyId`:

- **Header:** `CONVERTS TO {account currency name}` + `≈ {acctSymbol}{converted}` where
  `converted = amount × rate`, formatted by `AmountFormatter` in the UseCase (so VM/ViewProvider do
  no derivation, per zero-core `ViewProviderDerivation`).
- **Editable rate pill:** `1 {txSymbol} = {rate} {acctSymbol}`, focus border + caret when
  `editTarget == Rate`. Trailing **Reset** (refresh) once manual, else a subtle **Edit** (pencil)
  hint.

Composable `TransactionEditConversionCard` replaces `TransactionEditRateTextField` (deleted).

## Transfer — TransferBlock (live-linked)

Replaces `RateModePill` + `AccountSelectorsWithSwap`. `TransferRateMode` and its actions
(`CycleTransferRateMode`, `ChangeTransferRate`) and file are removed; the use case now keeps
`amount`, `targetAmount`, `rate`, `rateAuto`, `editTarget` on `State.Transfer`.

- **Currencies differ:** Row of two `TransactionEditAmountField`s — **You send** (source symbol,
  `amount`) and **You get** (target symbol, `targetAmount`) — each tappable to focus; a
  `TransactionEditRateConnector` pill below (`1 {srcSym} = {rate} {dstSym}`, focus border + caret
  when `editTarget == Rate`, Reset/Edit trailing).
- **Currencies match:** a single **Amount** `TransactionEditAmountField`.
- **Account row (constant):** `From` tile + swap button + `To` tile (reuse `SelectorCard` styled as
  the From/To tiles with a centered swap, as today; lay them out horizontally per the design).
- Date picker below.

New composables: `TransactionEditAmountField` (in `common`, boxed caption + currency symbol +
right-aligned value + focus caret) and `TransactionEditRateConnector` (transfer package). Model
token usage after the existing `RateModePill` / `AmountDisplay`.

## Parent screen wiring

`TransactionEditViewProvider`:
- Hide the pinned `AmountDisplay` when `selectedTransactionType == TRANSFER` (the block owns the
  transfer amounts).
- Route the inline keypad by `editTarget`: value/onChange switch between `amount`/`ChangeAmount`,
  `rate`/`ChangeRate` (6 decimals), and `targetAmount`/`ChangeTargetAmount`.
- Tapping the expense/income hero amount sends `FocusAmount`.

`TransactionEditViewModel.State` gains `rate`, `targetAmount`, `editTarget` (Transfer maps real
values; expense/income maps `rate`/`editTarget`, `targetAmount = ""`). New parent actions
`ChangeRate`, `ChangeTargetAmount`, `FocusAmount`.

## Testing

- `AmountKeypadTest`: 6-decimal cap accepts a 6th decimal, rejects a 7th; 2-dp default unchanged.
- `android-ui-inspector`:
  - Expense with differing currencies → ConversionCard renders, keypad edits the rate (pill caret +
    live "Converts to"), Reset restores the auto rate, tapping amount returns focus to amount.
  - Transfer with differing-currency accounts → You send / You get + rate connector; editing send
    updates get, editing get back-computes send, editing rate updates get and shows Reset; same-
    currency accounts show a single Amount field.
