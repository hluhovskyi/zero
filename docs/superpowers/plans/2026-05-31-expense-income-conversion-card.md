# Transaction-edit FX UX — unified Exchange-rate field — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match the Claude Design FX UX on the transaction edit screen with one shared boxed
**Exchange-rate** tile. Expense/income show it (rate row + "Converts to" line) under the amount;
transfer shows "From amount"/"To amount" fields + the same tile (no converted line), all live-linked
through the rate. Both driven by the inline keypad.

**Architecture:** Rate lives in the shared `TransactionEditUseCase` (single source of truth for the
parent `TransactionEditViewModel` and the child expense-income / transfer VMs) as `rate: String` +
`rateAuto: Boolean`, auto-derived from the active currency pair via `CurrencyConvertUseCase` by one
reactive collector. A `TransactionEditFocusTarget` (Amount / Rate / Received) routes the inline
`AmountKeypad`. Transfer keeps `amount` + `targetAmount` live-linked. The old `TransferRateMode` is
removed.

**Tech Stack:** Kotlin, Jetpack Compose (Material + ZeroTheme tokens), Dagger, coroutines/Flow.

**Spec:** `docs/superpowers/specs/2026-05-31-expense-income-conversion-card-design.md`

Read first: `zero-core/AGENTS.md` (rule 7 — ViewProvider runs no derivation). Before writing
composables, grep `ZeroColors` for exact token names (`surfaceContainerLow`, `surfaceContainer`,
`surface`, `primaryContainer`, `onSurfaceVariant`, `outline`).

---

### Task 1: `AmountKeypad` accepts `maxDecimals`

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountKeypad.kt`
- Test: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/AmountKeypadTest.kt`

- [ ] **Step 1: Add failing tests** to `AmountKeypadTest.kt`:

```kotlin
@Test
fun `digit accepted up to sixth decimal place`() {
    assertEquals("1.23456", handleAmountKeypadKey("1.2345", "6", maxDecimals = 6))
}

@Test
fun `digit rejected past sixth decimal place`() {
    assertEquals("1.234567", handleAmountKeypadKey("1.234567", "8", maxDecimals = 6))
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :zero-ui:testDebugUnitTest --tests "*AmountKeypadTest*"` → FAIL.

- [ ] **Step 3: Implement.** Add `maxDecimals: Int = 2` to `handleAmountKeypadKey` and use it in the cap check; add `maxDecimals: Int = 2` to the `AmountKeypad` composable (after `keyHeight`) and pass it into the handler call `onChange(handleAmountKeypadKey(value, key, maxDecimals))`:

```kotlin
if (dotIndex >= 0 && value.length - dotIndex - 1 >= maxDecimals) value else "$value$key"
```

- [ ] **Step 4: Run, verify pass** (same command). All PASS.
- [ ] **Step 5: Commit** `feat: AmountKeypad supports configurable maxDecimals`.

---

### Task 2: `TransactionEditFocusTarget` enum

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditFocusTarget.kt`

- [ ] **Step 1:**

```kotlin
package com.hluhovskyi.zero.transactions.edit

/** Which field the inline amount keypad currently drives. `Received` is transfer-only. */
enum class TransactionEditFocusTarget {
    Amount,
    Rate,
    Received,
}
```

- [ ] **Step 2: Commit** `feat: add TransactionEditFocusTarget enum`.

---

### Task 3: `TransactionEditUseCase` interface — unified rate state, drop modes

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`

- [ ] **Step 1: Actions.** Remove `CycleTransferRateMode` and `ChangeTransferRate`. Add:

```kotlin
object FocusAmount : Action
object FocusRate : Action
object FocusReceived : Action
object ResetRate : Action
```

Keep `ChangeAmount`, `ChangeRate`, `ChangeTargetAmount`.

- [ ] **Step 2: Expense/Income state.** Add to both `State.Expense` and `State.Income`:

```kotlin
val rateAuto: Boolean = true,
val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
val convertedAmountText: String = "",
```

- [ ] **Step 3: Transfer state.** Replace `transferRateMode` with:

```kotlin
val rate: String = "",
val rateAuto: Boolean = true,
val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
```

Keep `amount`, `targetAmount`, `sourceCurrencySymbol`, `targetCurrencySymbol`, etc. Remove the
`Rate` import if no longer referenced.

- [ ] **Step 4:** Commit together with Task 4 (compiles together).

---

### Task 4: `DefaultTransactionEditUseCase` — auto-rate collector, live-linking, save

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`
- Delete: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransferRateMode.kt`
- Modify (DI if needed): `.../TransactionEditComponent.kt` (`AmountFormatter` already in this graph — the parent VM receives one; thread it to the use case).

- [ ] **Step 1: Inject `AmountFormatter`.** Add `private val amountFormatter: AmountFormatter,`; import `...common.AmountFormatter`, `...common.Amount`.

- [ ] **Step 2: CompositeState.** Replace `transferRateMode` with:

```kotlin
val rate: String = "",
val rateAuto: Boolean = true,
val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
```

- [ ] **Step 3: Helpers** near `private fun BigDecimal.format()`:

```kotlin
private fun String.timesRate(rate: String): String =
    (toBigDecimalOrZero() * rate.toBigDecimalOrZero()).format()

private fun String.divByRate(rate: String): String {
    val r = rate.toBigDecimalOrNull()
    return if (r == null || r.signum() == 0) this
    else toBigDecimalOrZero().divide(r, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
```

- [ ] **Step 4: State mapping.** `Expense`/`Income` branches compute converted text + effective target:

```kotlin
val currenciesDiffer = state.selectedCurrency != null && state.selectedAccount != null &&
    state.selectedCurrency.id != state.selectedAccount.currencyId
val acctSymbol = state.selectedAccount?.let { acc ->
    state.currencies.firstOrNull { it.id == acc.currencyId }?.currencySymbol
}.orEmpty()
val convertedText = if (currenciesDiffer) {
    "≈ " + amountFormatter.format(
        Amount(state.amount.toBigDecimalOrNull()).withRate(Rate(state.rate.toBigDecimalOrNull())),
        acctSymbol,
    )
} else ""
val effectiveTarget = if (currenciesDiffer) state.editTarget else TransactionEditFocusTarget.Amount
```

Pass `rate = state.rate, rateAuto = state.rateAuto, editTarget = effectiveTarget,
convertedAmountText = convertedText` into both. The `Transfer` branch passes `rate = state.rate,
rateAuto = state.rateAuto, editTarget = state.editTarget` and the existing `targetAmount` / symbols;
drop `transferRateMode`.

- [ ] **Step 5: `perform` handlers** (replace the relevant branches; remove `CycleTransferRateMode`
and `ChangeTransferRate`):

```kotlin
is TransactionEditUseCase.Action.ChangeAmount -> mutableState.update { s ->
    val received = if (s.transactionType == TransactionEditType.TRANSFER) action.amount.timesRate(s.rate) else s.targetAmount
    s.copy(amount = action.amount, targetAmount = received, editTarget = TransactionEditFocusTarget.Amount)
}
is TransactionEditUseCase.Action.ChangeRate -> mutableState.update { s ->
    val received = if (s.transactionType == TransactionEditType.TRANSFER) s.amount.timesRate(action.rate) else s.targetAmount
    s.copy(rate = action.rate, rateAuto = false, targetAmount = received)
}
is TransactionEditUseCase.Action.ChangeTargetAmount -> mutableState.update { s ->
    s.copy(targetAmount = action.amount, amount = action.amount.divByRate(s.rate), editTarget = TransactionEditFocusTarget.Received)
}
is TransactionEditUseCase.Action.FocusAmount -> mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Amount) }
is TransactionEditUseCase.Action.FocusRate -> mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Rate) }
is TransactionEditUseCase.Action.FocusReceived -> mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Received) }
is TransactionEditUseCase.Action.ResetRate -> mutableState.update { it.copy(rateAuto = true, editTarget = TransactionEditFocusTarget.Amount) }
```

- [ ] **Step 6: Account selection / swap.** Drop the eager `fetchRateIfTransfer` calls; just update
selection and reset to auto so the collector re-derives:

```kotlin
private fun selectAccount(action: TransactionEditUseCase.Action.SelectAccount) {
    mutableState.update { it.copy(selectedAccount = action.account, rateAuto = true, editTarget = TransactionEditFocusTarget.Amount) }
}
```

Same shape for `SelectTargetAccount` (inline) and `swapAccounts()` (swap source/target +
`rateAuto = true, editTarget = Amount`). Delete `cycleTransferRateMode()`, `fetchRateIfTransfer`,
and `fetchRate` (the collector calls `currencyConvertUseCase.getRate` directly).

- [ ] **Step 7: Reactive auto-derive** — one collector inside `attach()` for both modes:

```kotlin
launch {
    mutableState
        .map { s ->
            val pair = when (s.transactionType) {
                TransactionEditType.TRANSFER -> s.selectedAccount?.currencyId to s.selectedTargetAccount?.currencyId
                else -> (s.selectedCurrency?.id as Id?) to (s.selectedAccount?.currencyId as Id?)
            }
            Triple(s.transactionType, pair.first, pair.second)
        }
        .distinctUntilChanged()
        .collectLatest { (type, srcId, dstId) ->
            if (!mutableState.value.rateAuto) return@collectLatest
            if (srcId == null || dstId == null || srcId == dstId) return@collectLatest
            val rate = currencyConvertUseCase.getRate(srcId as Id.Known, dstId as Id.Known).value.format()
            mutableState.update { s ->
                if (!s.rateAuto) return@update s
                val received = if (s.transactionType == TransactionEditType.TRANSFER) s.amount.timesRate(rate) else s.targetAmount
                s.copy(rate = rate, targetAmount = received)
            }
        }
}
```

(`selectedCurrency.id` and `account.currencyId` are both `Id.Known`; the casts keep the `when` arms
type-aligned.)

- [ ] **Step 8: Edit-mode load.** Where a saved transaction is loaded in `attach()`: Expense/Income
set `rate = transaction.rate.value.toString()` and `rateAuto = false`. Transfer set
`targetAmount = transaction.targetAmount.value.toString()`, derive a `rate` string
(`targetAmount ÷ amount`, formatted) and `rateAuto = false`. Replace the
`transferRateMode = TransferRateMode.Default(...)` assignment accordingly.

- [ ] **Step 9: `save()`.** Replace the transfer `computedTargetAmount` `when(mode)` block with
`val computedTargetAmount = Amount(state.targetAmount.toBigDecimalOrNull())`.

- [ ] **Step 10: Delete** `TransferRateMode.kt`. `grep -r TransferRateMode src/main` → nothing.
- [ ] **Step 11: Compile** `:zero-core:compileDebugKotlin` (VMs/VPs still fail). Commit interface +
use case: `feat: unify FX rate state in transaction edit use case, drop transfer modes`.

---

### Task 5: Expense/Income VM — rate-field state

**Files:**
- Modify: `.../common/TransactionEditExpenseIncomeViewModel.kt`
- Modify: `.../common/DefaultTransactionEditExpenseIncomeViewModel.kt`

- [ ] **Step 1:** Interface — add actions `object FocusRate : Action`, `object ResetRate : Action`.
Replace the computed `showRate` with constructor fields and the pre-computed shape:

```kotlin
data class State(
    val accounts: List<TransactionEditAccount> = emptyList(),
    val selectedAccount: TransactionEditAccount? = null,
    val categories: List<TransactionEditCategory> = emptyList(),
    val selectedCategory: TransactionEditCategory? = null,
    val currencies: List<TransactionEditCurrency> = emptyList(),
    val selectedCurrency: TransactionEditCurrency? = null,
    val amount: String = "",
    val rate: String = "",
    val rateAuto: Boolean = true,
    val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
    val convertedAmountText: String = "",
    val accountCurrencyName: String = "",
    val accountCurrencySymbol: String = "",
    val txCurrencySymbol: String = "",
    val showRate: Boolean = false,
    val date: LocalDateTime? = null,
)
```

Import `...edit.TransactionEditFocusTarget`.

- [ ] **Step 2:** Default VM — map both `Expense`/`Income` via a shared helper that resolves the
currency display + `showRate` (trivial lookup):

```kotlin
val accountCurrency = currencies.firstOrNull { it.id == selectedAccount?.currencyId }
val showRate = selectedCurrency != null && selectedAccount != null &&
    selectedCurrency.id != selectedAccount.currencyId
TransactionEditExpenseIncomeViewModel.State(
    accounts = accounts, selectedAccount = selectedAccount,
    categories = categories, selectedCategory = selectedCategory,
    currencies = currencies, selectedCurrency = selectedCurrency,
    amount = amount, rate = rate, rateAuto = rateAuto, editTarget = editTarget,
    convertedAmountText = convertedAmountText,
    accountCurrencyName = accountCurrency?.name.orEmpty(),
    accountCurrencySymbol = accountCurrency?.currencySymbol.orEmpty(),
    txCurrencySymbol = selectedCurrency?.currencySymbol.orEmpty(),
    showRate = showRate, date = date,
)
```

- [ ] **Step 3:** Map new actions: `FocusRate → UseCase.Action.FocusRate`,
`ResetRate → UseCase.Action.ResetRate`.

- [ ] **Step 4:** Commit with Task 6/7.

---

### Task 6: `TransactionEditRateField` (shared) + `TransactionEditAmountField` + strings

**Files:**
- Create: `.../common/TransactionEditRateField.kt`
- Create: `.../common/TransactionEditAmountField.kt`
- Delete: `.../common/TransactionEditRateTextField.kt`
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Strings** (under `<!-- Transaction Edit -->`):

```xml
<string name="transaction_edit_exchange_rate">Exchange rate</string>
<string name="transaction_edit_converts_to">Converts to</string>
<string name="transaction_edit_rate_reset">Reset</string>
<string name="transfer_edit_from_amount">From amount</string>
<string name="transfer_edit_to_amount">To amount</string>
```

(`transaction_edit_amount_display_label` = "Amount" already exists for the single-amount caption.)

- [ ] **Step 2: `TransactionEditRateField`** — boxed "Exchange rate" tile, optional converted line:

```kotlin
package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
internal fun TransactionEditRateField(
    modifier: Modifier = Modifier,
    sourceCurrencySymbol: String,
    targetCurrencySymbol: String,
    rate: String,
    rateAuto: Boolean,
    focused: Boolean,
    onFocus: () -> Unit,
    onReset: () -> Unit,
    convertedAmountText: String? = null,
    convertedCurrencyName: String = "",
) {
    Column(
        modifier = modifier
            .background(if (focused) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .then(if (focused) Modifier.border(1.5.dp, ZeroTheme.colors.primaryContainer, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onFocus)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_exchange_rate).uppercase(),
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant, letterSpacing = 1.2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1$sourceCurrencySymbol = ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant)
                Text(rate.ifEmpty { "0" }, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primaryContainer)
                if (focused) {
                    Box(modifier = Modifier.padding(horizontal = 2.dp).width(2.dp).height(15.dp).background(ZeroTheme.colors.primaryContainer))
                }
                Text(" $targetCurrencySymbol", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant)
                if (!rateAuto) {
                    Row(modifier = Modifier.padding(start = 5.dp).clickable(onClick = onReset), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = ZeroTheme.colors.primaryContainer)
                        Text(stringResource(R.string.transaction_edit_rate_reset), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.primaryContainer, modifier = Modifier.padding(start = 2.dp))
                    }
                } else {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp), tint = if (focused) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.outline)
                }
            }
        }
        if (convertedAmountText != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(1.dp).background(ZeroTheme.colors.surfaceContainer))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.transaction_edit_converts_to) + " · " + convertedCurrencyName,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant,
                )
                Text(convertedAmountText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primaryContainer)
            }
        }
    }
}
```

- [ ] **Step 3: `TransactionEditAmountField`** — boxed caption + currency symbol + right-aligned
value + focus caret (read-only; the keypad edits via the parent):

```kotlin
package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
internal fun TransactionEditAmountField(
    modifier: Modifier = Modifier,
    caption: String,
    currencySymbol: String,
    value: String,
    focused: Boolean,
    onFocus: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(if (focused) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(18.dp))
            .then(if (focused) Modifier.border(1.5.dp, ZeroTheme.colors.primaryContainer, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onFocus)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(caption.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurfaceVariant, letterSpacing = 1.2.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currencySymbol, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ZeroTheme.colors.onSurfaceVariant)
            Box(modifier = Modifier.weight(1f))
            Text(value.ifEmpty { "0" }, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primaryContainer)
            Box(modifier = Modifier.padding(start = 3.dp).width(2.dp).height(22.dp).background(if (focused) ZeroTheme.colors.primaryContainer else Color.Transparent))
        }
    }
}
```

- [ ] **Step 4: Delete** `TransactionEditRateTextField.kt`.
- [ ] **Step 5: Commit** `feat: add shared Exchange-rate + AmountField composables`.

---

### Task 7: Expense/Income ViewProvider — render the Exchange-rate tile

**Files:**
- Modify: `.../common/TransactionEditExpenseIncomeViewProvider.kt`

- [ ] **Step 1:** Replace the `AnimatedVisibility(state.showRate) { TransactionEditRateTextField(...) }`
block with:

```kotlin
AnimatedVisibility(visible = state.showRate) {
    TransactionEditRateField(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        sourceCurrencySymbol = state.txCurrencySymbol,
        targetCurrencySymbol = state.accountCurrencySymbol,
        rate = state.rate,
        rateAuto = state.rateAuto,
        focused = state.editTarget == TransactionEditFocusTarget.Rate,
        onFocus = { viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.FocusRate) },
        onReset = { viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.ResetRate) },
        convertedAmountText = state.convertedAmountText,
        convertedCurrencyName = state.accountCurrencyName,
    )
}
```

Import `...edit.TransactionEditFocusTarget`.

- [ ] **Step 2: Commit** `feat: render Exchange-rate tile on expense-income screen`.

---

### Task 8: Transfer VM — live-linked state + focus actions

**Files:**
- Modify: `.../transfer/TransactionEditTransferViewModel.kt`
- Modify: `.../transfer/DefaultTransactionEditTransferViewModel.kt`

- [ ] **Step 1:** Interface — Actions: remove `ChangeTransferRate`, `CycleRateMode`, `ChangeAmount`,
`ChangeTargetAmount` (value edits come from the parent keypad). Keep account/date, add focus/reset:

```kotlin
sealed interface Action {
    data class SelectAccount(val account: TransactionEditAccount) : Action
    data class SelectTargetAccount(val account: TransactionEditAccount) : Action
    data class ChangeDate(val date: LocalDateTime) : Action
    object FocusAmount : Action
    object FocusReceived : Action
    object FocusRate : Action
    object ResetRate : Action
    object SwapAccounts : Action
}

data class State(
    val accounts: List<TransactionEditAccount> = emptyList(),
    val selectedAccount: TransactionEditAccount? = null,
    val targetAccounts: List<TransactionEditAccount> = emptyList(),
    val selectedTargetAccount: TransactionEditAccount? = null,
    val amount: String = "",
    val targetAmount: String = "",
    val rate: String = "",
    val rateAuto: Boolean = true,
    val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
    val sourceCurrencySymbol: String = "",
    val targetCurrencySymbol: String = "",
    val needsFx: Boolean = false,
    val date: LocalDateTime? = null,
)
```

Remove `import ...Rate` / `...TransferRateMode`; import `...TransactionEditFocusTarget`.

- [ ] **Step 2:** Default VM — map from `State.Transfer`, computing
`needsFx = selectedAccount != null && selectedTargetAccount != null &&
selectedAccount.currencyId != selectedTargetAccount.currencyId`; map the new fields through. In
`perform`: `FocusAmount → FocusAmount`, `FocusReceived → FocusReceived`, `FocusRate → FocusRate`,
`ResetRate → ResetRate`, account/swap/date as today.

- [ ] **Step 3:** Commit with Task 9.

---

### Task 9: Transfer ViewProvider rewrite

**Files:**
- Modify: `.../transfer/TransactionEditTransferViewProvider.kt`

- [ ] **Step 1:** Replace `RateModePill` + `AccountSelectorsWithSwap` body. Amount area adapts to
`needsFx`; account row (From | swap | To) is constant:

```kotlin
Column(
    modifier = Modifier.padding(horizontal = 24.dp)
        .then(if (!shouldFocus) Modifier.focusTarget() else Modifier),
) {
    if (state.needsFx) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TransactionEditAmountField(
                modifier = Modifier.weight(1f),
                caption = stringResource(R.string.transfer_edit_from_amount),
                currencySymbol = state.sourceCurrencySymbol, value = state.amount,
                focused = state.editTarget == TransactionEditFocusTarget.Amount,
                onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusAmount) },
            )
            TransactionEditAmountField(
                modifier = Modifier.weight(1f),
                caption = stringResource(R.string.transfer_edit_to_amount),
                currencySymbol = state.targetCurrencySymbol, value = state.targetAmount,
                focused = state.editTarget == TransactionEditFocusTarget.Received,
                onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusReceived) },
            )
        }
        TransactionEditRateField(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            sourceCurrencySymbol = state.sourceCurrencySymbol,
            targetCurrencySymbol = state.targetCurrencySymbol,
            rate = state.rate, rateAuto = state.rateAuto,
            focused = state.editTarget == TransactionEditFocusTarget.Rate,
            onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusRate) },
            onReset = { viewModel.perform(TransactionEditTransferViewModel.Action.ResetRate) },
        )
    } else {
        TransactionEditAmountField(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            caption = stringResource(R.string.transaction_edit_amount_display_label),
            currencySymbol = state.sourceCurrencySymbol, value = state.amount,
            focused = true,
            onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusAmount) },
        )
    }

    AccountSelectorsWithSwap(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
        state = state,
        onSourceSelected = { viewModel.perform(TransactionEditTransferViewModel.Action.SelectAccount(it)) },
        onTargetSelected = { viewModel.perform(TransactionEditTransferViewModel.Action.SelectTargetAccount(it)) },
        onSwap = { viewModel.perform(TransactionEditTransferViewModel.Action.SwapAccounts) },
    )

    state.date?.let { date ->
        DatePickerCard(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.transaction_edit_date_label),
            date = date,
            onDateSelected = { viewModel.perform(TransactionEditTransferViewModel.Action.ChangeDate(it)) },
        )
    }
}
```

- [ ] **Step 2:** Rework `AccountSelectorsWithSwap` to a horizontal `Row`: From `SelectorCard`
(weight 1f) | a narrow vertical swap bar | To `SelectorCard` (weight 1f). The swap bar matches the
design `SwapButton`: ~36.dp wide, full height, `RoundedCornerShape(14.dp)`, `surfaceContainerLow`
background, centered swap icon (`Icons.Filled.SwapVert` or the existing icon). Keep the existing
`SelectorCard` dropdown wiring.

- [ ] **Step 3:** Delete `RateModePill`, `formatDefaultPillText`, `computeTargetFromRate`,
`amountFormat`, and now-unused imports (`Rate`, `TransferRateMode`, `BasicTextField`,
`KeyboardOptions`, etc.).

- [ ] **Step 4: Compile** `:zero-core:compileDebugKotlin` (with Task 8) → PASS. Commit:
`feat: live-linked From/To transfer FX layout with shared rate field`.

---

### Task 10: Parent VM + ViewProvider — keypad routing, hide hero amount on transfer

**Files:**
- Modify: `.../TransactionEditViewModel.kt`
- Modify: `.../DefaultTransactionEditViewModel.kt`
- Modify: `.../TransactionEditViewProvider.kt`

- [ ] **Step 1:** Interface — `State` adds `rate: String = ""`, `targetAmount: String = ""`,
`editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount`. Actions add
`data class ChangeRate(val rate: String)`, `data class ChangeTargetAmount(val amount: String)`,
`object FocusAmount`. Import the enum.

- [ ] **Step 2:** Default VM — map `rate`/`editTarget` from Expense/Income/Transfer; `targetAmount`
from Transfer (else `""`). `perform`: `ChangeRate → ChangeRate`,
`ChangeTargetAmount → ChangeTargetAmount`, `FocusAmount → FocusAmount`.

- [ ] **Step 3:** ViewProvider — wrap the pinned `AmountDisplay` in
`if (state.selectedTransactionType != TransactionEditType.TRANSFER) { … }`, and add
`viewModel.perform(TransactionEditViewModel.Action.FocusAmount)` to its `onClick` (alongside
`keypadVisible = true`).

- [ ] **Step 4:** Keypad — route by `editTarget`:

```kotlin
AnimatedVisibility(visible = keypadVisible) {
    val target = state.editTarget
    AmountKeypad(
        modifier = Modifier.fillMaxWidth().background(ZeroTheme.colors.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 8.dp),
        value = when (target) {
            TransactionEditFocusTarget.Rate -> state.rate
            TransactionEditFocusTarget.Received -> state.targetAmount
            TransactionEditFocusTarget.Amount -> state.amount
        },
        onChange = {
            when (target) {
                TransactionEditFocusTarget.Rate -> viewModel.perform(TransactionEditViewModel.Action.ChangeRate(it))
                TransactionEditFocusTarget.Received -> viewModel.perform(TransactionEditViewModel.Action.ChangeTargetAmount(it))
                TransactionEditFocusTarget.Amount -> viewModel.perform(TransactionEditViewModel.Action.ChangeAmount(it))
            }
        },
        maxDecimals = if (target == TransactionEditFocusTarget.Rate) 6 else 2,
        keyHeight = 58.dp,
    )
}
```

Keep the keypad visible on transfer (no hero to tap):
`LaunchedEffect(state.selectedTransactionType) { if (state.selectedTransactionType == TransactionEditType.TRANSFER) keypadVisible = true }`.
Import `...edit.TransactionEditFocusTarget`.

- [ ] **Step 5: Compile** `:zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 6: Commit** `feat: route inline keypad to amount/rate/received by focus`.

---

### Task 11: Full verification

- [ ] **Step 1:** `./gradlew :zero-ui:testDebugUnitTest :zero-core:testDebugUnitTest lintDebug 2>&1 | tail -25` → all PASS. Fix any `ViewProviderDerivation` lint hits by moving logic into VM/use-case mapping.
- [ ] **Step 2:** Acquire emulator (`./scripts/emulator/acquire`), build+install, and via
`android-ui-inspector`:
  - **Expense**, currency ≠ account → Exchange-rate tile (rate row + "Converts to · …" line);
    tap rate → keypad edits rate (caret, 6 dp, converted updates); Reset appears after manual edit
    and restores auto; tap amount → keypad edits amount (2 dp). Same-currency hides the tile.
  - **Transfer**, differing-currency accounts → From amount / To amount + Exchange-rate tile; edit
    From → To updates; edit To → From back-computes; edit rate → To updates and Reset appears; swap
    stays consistent; same-currency accounts show a single Amount field.
- [ ] **Step 3:** Final polish commit if needed: `fix: FX layout polish`.

---

## Self-review notes

- **Spec coverage:** keypad maxDecimals (T1); focus enum incl. Received (T2); unified use-case rate
  state + collector + live-linking + save + mode removal (T3–4); expense/income VM (T5); shared
  `TransactionEditRateField` + `TransactionEditAmountField` + strings (T6); expense/income VP (T7);
  transfer VM (T8); transfer VP rewrite (T9); parent VM/VP keypad routing + hidden hero amount on
  transfer (T10); tests + UI (T11).
- **One shared rate composable:** `TransactionEditRateField` serves both modes — converted line only
  when `convertedAmountText != null` (expense/income). No separate ConversionCard/RateConnector.
- **Naming consistency:** `editTarget`, `rateAuto`, `rate`, `targetAmount`, `convertedAmountText`,
  `TransactionEditFocusTarget.{Amount,Rate,Received}`, actions `FocusAmount/FocusRate/FocusReceived/
  ResetRate/ChangeRate/ChangeTargetAmount` used identically across use case, both child VMs, parent
  VM, and views.
- **Removals:** `TransferRateMode.kt`, `CycleTransferRateMode`, `ChangeTransferRate`,
  `cycleTransferRateMode()`, `RateModePill`, `fetchRate`/`fetchRateIfTransfer`,
  `TransactionEditRateTextField`. After T4/T9, `grep -r TransferRateMode src/main` is empty.
- **Derivation placement:** `convertedAmountText` formatted in the use case; `needsFx`/`showRate`
  and currency lookups are trivial joins in the VM mapping; ViewProviders only read.
- **Out of scope (not rate UX):** expense/income hero amount stays (not re-boxed); category picker
  unchanged; transfer keeps `SelectorCard` dropdowns (no bottom-sheet account picker).
- **Open checks for executor:** confirm `ZeroTheme.colors` token names; confirm `AmountFormatter`
  reaches `DefaultTransactionEditUseCase`; confirm edit-mode load derives a transfer rate string.
