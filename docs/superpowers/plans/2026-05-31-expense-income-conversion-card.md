# Transaction-edit FX UX — ConversionCard + live-linked transfer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match the Claude Design FX UX on the transaction edit screen — expense/income get the
ConversionCard (live "converts to" + editable rate with auto/reset); transfer gets live-linked
"You send" / rate / "You get" fields (drop the 3-mode cycle). Both driven by the inline keypad.

**Architecture:** Rate lives in the shared `TransactionEditUseCase` (single source of truth for the
parent `TransactionEditViewModel` and the child expense-income / transfer VMs) as `rate: String` +
`rateAuto: Boolean`, auto-derived from the active currency pair via `CurrencyConvertUseCase` by one
reactive collector. A `TransactionEditFocusTarget` (Amount / Rate / Received) routes the inline
`AmountKeypad`. Transfer keeps `amount` + `targetAmount` live-linked through the rate. The old
`TransferRateMode` (Default/CustomRate/CustomAmount) is removed.

**Tech Stack:** Kotlin, Jetpack Compose (Material + ZeroTheme tokens), Dagger, coroutines/Flow.

**Spec:** `docs/superpowers/specs/2026-05-31-expense-income-conversion-card-design.md`

Read first: `zero-core/AGENTS.md` (rule 7 — ViewProvider runs no derivation). Before writing
composables, grep `ZeroColors` for exact token names (`surfaceContainerLow`, `surfaceContainerHigh`,
`surfaceContainer`, `primaryContainer`, `onSurfaceVariant`, `outline`, `surface`).

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

- [ ] **Step 2: Run, verify fail** — `./gradlew :zero-ui:testDebugUnitTest --tests "*AmountKeypadTest*"` → FAIL (no `maxDecimals`).

- [ ] **Step 3: Implement.** Add `maxDecimals: Int = 2` to `handleAmountKeypadKey` and use it in the cap check; add `maxDecimals: Int = 2` to the `AmountKeypad` composable (after `keyHeight`) and pass it into the handler call:

```kotlin
internal fun handleAmountKeypadKey(value: String, key: String, maxDecimals: Int = 2): String = when {
    key == "⌫" -> if (value.length <= 1) "0" else value.dropLast(1)
    key == "." -> if (value.contains(".")) value else "$value."
    else -> {
        if (value == "0") {
            key
        } else {
            val dotIndex = value.indexOf('.')
            if (dotIndex >= 0 && value.length - dotIndex - 1 >= maxDecimals) value else "$value$key"
        }
    }
}
```

In `AmountKeypad`, change the key handler to `onChange(handleAmountKeypadKey(value, key, maxDecimals))`.

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

- [ ] **Step 2: Commit** `feat: add TransactionEditFocusTarget enum` (compiles standalone).

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

Keep `ChangeAmount`, `ChangeRate`, `ChangeTargetAmount` (the last is the transfer "received" edit).

- [ ] **Step 2: Expense/Income state.** Add to both `State.Expense` and `State.Income`:

```kotlin
val rateAuto: Boolean = true,
val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
val convertedAmountText: String = "",
```

- [ ] **Step 3: Transfer state.** Replace `transferRateMode` with rate fields:

```kotlin
data class Transfer(
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
    val notes: String = "",
    override val date: LocalDateTime,
    override val sourceSnapshot: SourceSnapshot? = null,
) : State
```

Remove the now-unused `import ...Rate` if the interface no longer references it (keep if still used).

- [ ] **Step 4: Compile** — expected FAIL in `DefaultTransactionEditUseCase` + VMs (next tasks). Commit together with Task 4.

---

### Task 4: `DefaultTransactionEditUseCase` — auto-rate collector, live-linking, save

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`
- Delete: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransferRateMode.kt`
- Modify (DI): `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt` if needed (AmountFormatter binding — the parent VM already receives one, so the binding exists; pass it to the use case).

- [ ] **Step 1: Inject `AmountFormatter`.** Add `private val amountFormatter: AmountFormatter,` to the constructor; import `com.hluhovskyi.zero.common.AmountFormatter`, `com.hluhovskyi.zero.common.Amount`. Confirm `TransactionEditComponent`/`MainActivityScreenComponent` provide the use case its deps and thread the formatter through.

- [ ] **Step 2: CompositeState.** Replace `transferRateMode` with shared fields:

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

- [ ] **Step 4: State mapping.** In the `Expense`/`Income` branches, compute and pass the converted
text + effective target (see prior revision — unchanged):

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
convertedAmountText = convertedText` into both. In the `Transfer` branch, pass `rate = state.rate,
rateAuto = state.rateAuto, editTarget = state.editTarget` and the existing `targetAmount` /
symbols; drop `transferRateMode`.

- [ ] **Step 5: `perform` handlers.** Replace the relevant branches:

```kotlin
is TransactionEditUseCase.Action.ChangeAmount -> mutableState.update { s ->
    val received = if (s.transactionType == TransactionEditType.TRANSFER) s.amount.let { _ -> action.amount.timesRate(s.rate) } else s.targetAmount
    s.copy(amount = action.amount, targetAmount = received, editTarget = TransactionEditFocusTarget.Amount)
}

is TransactionEditUseCase.Action.ChangeRate -> mutableState.update { s ->
    val received = if (s.transactionType == TransactionEditType.TRANSFER) s.amount.timesRate(action.rate) else s.targetAmount
    s.copy(rate = action.rate, rateAuto = false, targetAmount = received)
}

is TransactionEditUseCase.Action.ChangeTargetAmount -> mutableState.update { s ->
    s.copy(targetAmount = action.amount, amount = action.amount.divByRate(s.rate), editTarget = TransactionEditFocusTarget.Received)
}

is TransactionEditUseCase.Action.FocusAmount ->
    mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Amount) }
is TransactionEditUseCase.Action.FocusRate ->
    mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Rate) }
is TransactionEditUseCase.Action.FocusReceived ->
    mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Received) }
is TransactionEditUseCase.Action.ResetRate -> resetRate()
```

Remove the `CycleTransferRateMode` and `ChangeTransferRate` branches.

- [ ] **Step 6: `selectAccount` / `SelectTargetAccount` / `swapAccounts`.** Drop the eager
`fetchRateIfTransfer` calls; just update the account selection and reset to auto so the collector
re-derives:

```kotlin
private fun selectAccount(action: TransactionEditUseCase.Action.SelectAccount) {
    mutableState.update { it.copy(selectedAccount = action.account, rateAuto = true, editTarget = TransactionEditFocusTarget.Amount) }
}
```

For `SelectTargetAccount` (inline in `perform`): `mutableState.update { it.copy(selectedTargetAccount = action.account, rateAuto = true, editTarget = TransactionEditFocusTarget.Amount) }`.
For `swapAccounts()`: swap source/target and set `rateAuto = true, editTarget = Amount`. Remove
`fetchRateIfTransfer`, `cycleTransferRateMode`, and (if now unused) keep `fetchRate` only if the
collector uses it — the collector calls `currencyConvertUseCase.getRate` directly, so `fetchRate`
/`fetchRateIfTransfer` can be deleted.

- [ ] **Step 7: Reset + reactive auto-derive.**

```kotlin
private fun resetRate() {
    mutableState.update { it.copy(rateAuto = true, editTarget = TransactionEditFocusTarget.Amount) }
    // collector re-derives the rate + received from the pair
}
```

Add a `launch { }` inside `attach()` (alongside the other launches) — one collector for both modes:

```kotlin
launch {
    mutableState
        .map { s ->
            val pair = when (s.transactionType) {
                TransactionEditType.TRANSFER ->
                    s.selectedAccount?.currencyId to s.selectedTargetAccount?.currencyId
                else ->
                    (s.selectedCurrency?.id as Id?) to (s.selectedAccount?.currencyId as Id?)
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

(`selectedCurrency.id` and `account.currencyId` are both `Id.Known`; the `as Id?`/`as Id.Known`
casts keep the `when` arms type-aligned.)

- [ ] **Step 8: Load (edit mode).** In `attach()` where a saved transaction is loaded: for
`Expense`/`Income`, keep `rate = transaction.rate.value.toString()` and set `rateAuto = false`
(a saved rate is explicit). For `Transfer`, set `rate`/`targetAmount` from the saved transfer
(`targetAmount = transaction.targetAmount.value.toString()`, derive `rate` = target ÷ amount or via
`fetchRate`-equivalent) and `rateAuto = false`. Replace the `transferRateMode = TransferRateMode.Default(rate)` assignment accordingly.

- [ ] **Step 9: `save()`.** Replace the transfer `computedTargetAmount` `when(mode)` block with the
maintained value:

```kotlin
val computedTargetAmount = Amount(state.targetAmount.toBigDecimalOrNull())
```

- [ ] **Step 10: Delete** `TransferRateMode.kt`. Grep `TransferRateMode` → zero hits in `main`.
- [ ] **Step 11: Compile** `./gradlew :zero-core:compileDebugKotlin` → expect remaining failures
only in the VMs/ViewProviders (next tasks). Commit interface + use case together:
`feat: unify FX rate state in transaction edit use case, drop transfer modes`.

---

### Task 5: Expense/Income VM — ConversionCard state

**Files:**
- Modify: `.../common/TransactionEditExpenseIncomeViewModel.kt`
- Modify: `.../common/DefaultTransactionEditExpenseIncomeViewModel.kt`

- [ ] **Step 1:** Interface — add actions `object FocusRate : Action`, `object ResetRate : Action`.
Replace the computed `showRate` with constructor fields and add the pre-computed shape:

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

- [ ] **Step 2:** Default VM — map both `Expense`/`Income` via a shared helper that resolves
currency display + `showRate` (trivial lookup, allowed in VM mapping):

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

- [ ] **Step 4: Compile** `:zero-core:compileDebugKotlin` (transfer VM/VPs may still fail) →
verify this file's errors are gone. Commit with Task 6/7 or after Task 10.

---

### Task 6: `TransactionEditConversionCard` + strings

**Files:**
- Create: `.../common/TransactionEditConversionCard.kt`
- Delete: `.../common/TransactionEditRateTextField.kt`
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Strings** (under `<!-- Transaction Edit -->`):

```xml
<string name="transaction_edit_converts_to">Converts to</string>
<string name="transaction_edit_rate_reset">Reset</string>
```

- [ ] **Step 2: Composable** — header + editable rate pill; caret when focused; Reset (manual) /
Edit (auto) trailing. Full code:

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
internal fun TransactionEditConversionCard(
    modifier: Modifier = Modifier,
    txCurrencySymbol: String,
    accountCurrencyName: String,
    accountCurrencySymbol: String,
    rate: String,
    rateAuto: Boolean,
    convertedAmountText: String,
    focused: Boolean,
    onFocusRate: () -> Unit,
    onResetRate: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_converts_to).uppercase() + "  " + accountCurrencyName,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant, letterSpacing = 1.2.sp,
            )
            Text(
                text = convertedAmountText,
                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primaryContainer,
            )
        }
        RateEditRow(txCurrencySymbol, accountCurrencySymbol, rate, rateAuto, focused, onFocusRate, onResetRate)
    }
}

@Composable
private fun RateEditRow(
    txSym: String, acctSym: String, rate: String, rateAuto: Boolean,
    focused: Boolean, onFocus: () -> Unit, onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (focused) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainer,
                RoundedCornerShape(12.dp),
            )
            .then(if (focused) Modifier.border(1.5.dp, ZeroTheme.colors.primaryContainer, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onFocus)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("1 $txSym = ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant)
            Text(rate.ifEmpty { "0" }, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ZeroTheme.colors.primaryContainer)
            if (focused) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(2.dp).height(16.dp)
                        .background(ZeroTheme.colors.primaryContainer),
                )
            }
            Text(" $acctSym", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant)
        }
        if (rateAuto) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = ZeroTheme.colors.outline)
        } else {
            Row(modifier = Modifier.clickable(onClick = onReset), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = ZeroTheme.colors.primaryContainer)
                Text(
                    stringResource(R.string.transaction_edit_rate_reset),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.primaryContainer, modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Delete** `TransactionEditRateTextField.kt`.
- [ ] **Step 4: Commit** `feat: add TransactionEditConversionCard, drop rate text field`.

---

### Task 7: Expense/Income ViewProvider — render ConversionCard

**Files:**
- Modify: `.../common/TransactionEditExpenseIncomeViewProvider.kt`

- [ ] **Step 1:** Replace the `AnimatedVisibility(state.showRate) { TransactionEditRateTextField(...) }`
block with:

```kotlin
AnimatedVisibility(visible = state.showRate) {
    TransactionEditConversionCard(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        txCurrencySymbol = state.txCurrencySymbol,
        accountCurrencyName = state.accountCurrencyName,
        accountCurrencySymbol = state.accountCurrencySymbol,
        rate = state.rate,
        rateAuto = state.rateAuto,
        convertedAmountText = state.convertedAmountText,
        focused = state.editTarget == TransactionEditFocusTarget.Rate,
        onFocusRate = { viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.FocusRate) },
        onResetRate = { viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.ResetRate) },
    )
}
```

Import `...edit.TransactionEditFocusTarget`.

- [ ] **Step 2: Commit** `feat: render ConversionCard on expense-income screen`.

---

### Task 8: Transfer VM — live-linked state + focus actions

**Files:**
- Modify: `.../transfer/TransactionEditTransferViewModel.kt`
- Modify: `.../transfer/DefaultTransactionEditTransferViewModel.kt`

- [ ] **Step 1:** Interface — new Action set and State. Remove `ChangeTransferRate`, `CycleRateMode`,
`ChangeAmount`, `ChangeTargetAmount` (value edits now come from the parent keypad). Keep account/date:

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

Remove `import ...Rate` and `import ...TransferRateMode`; import `...TransactionEditFocusTarget`.

- [ ] **Step 2:** Default VM — map from `State.Transfer`, computing `needsFx`:

```kotlin
needsFx = state.selectedAccount != null && state.selectedTargetAccount != null &&
    state.selectedAccount.currencyId != state.selectedTargetAccount.currencyId,
```

Map the new fields straight through. In `perform`, map: `FocusAmount → UseCase.Action.FocusAmount`,
`FocusReceived → FocusReceived`, `FocusRate → FocusRate`, `ResetRate → ResetRate`,
`SelectAccount`/`SelectTargetAccount`/`SwapAccounts`/`ChangeDate` as today.

- [ ] **Step 3: Compile** — VP still fails (next task).
- [ ] **Step 4: Commit** with Task 9.

---

### Task 9: Transfer composables + ViewProvider rewrite

**Files:**
- Create: `.../common/TransactionEditAmountField.kt`
- Create: `.../transfer/TransactionEditRateConnector.kt`
- Modify: `.../transfer/TransactionEditTransferViewProvider.kt`
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Strings:**

```xml
<string name="transfer_edit_you_send">You send</string>
<string name="transfer_edit_you_get">You get</string>
<string name="transaction_edit_amount_label_caption">Amount</string>
```

(`transfer_edit_from_label`/`transfer_edit_to_label`/`transfer_edit_swap_description` already exist.)

- [ ] **Step 2: `TransactionEditAmountField`** — boxed caption + currency symbol + right-aligned
value + focus caret (read-only display; the keypad edits via the parent). Full code:

```kotlin
package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(18.dp))
            .clickable(onClick = onFocus)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = caption.uppercase(),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurfaceVariant, letterSpacing = 1.2.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currencySymbol,
                fontSize = 19.sp, fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = value.ifEmpty { "0" },
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primaryContainer,
            )
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .width(2.dp).height(22.dp)
                    .background(if (focused) ZeroTheme.colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }
}
```

- [ ] **Step 3: `TransactionEditRateConnector`** — the pill (`1 {srcSym} = {rate} {dstSym}`) with
caret + Reset/Edit. Full code:

```kotlin
package com.hluhovskyi.zero.transactions.edit.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
internal fun TransactionEditRateConnector(
    modifier: Modifier = Modifier,
    sourceCurrencySymbol: String,
    targetCurrencySymbol: String,
    rate: String,
    rateAuto: Boolean,
    focused: Boolean,
    onFocus: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(
                if (focused) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainerLow,
                RoundedCornerShape(999.dp),
            )
            .border(
                1.5.dp,
                if (focused) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.surfaceContainer,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onFocus)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("1$sourceCurrencySymbol = ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant)
        Text(rate.ifEmpty { "0" }, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (focused) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.onSurface)
        if (focused) {
            Box(modifier = Modifier.padding(horizontal = 2.dp).width(2.dp).height(14.dp).background(ZeroTheme.colors.primaryContainer))
        }
        Text(" $targetCurrencySymbol", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ZeroTheme.colors.onSurfaceVariant)
        if (!rateAuto) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(14.dp).clickable(onClick = onReset), tint = ZeroTheme.colors.primaryContainer)
        } else {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(13.dp), tint = if (focused) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.outline)
        }
    }
}
```

- [ ] **Step 4: ViewProvider rewrite.** Replace `RateModePill` + `AccountSelectorsWithSwap` body.
The amount area adapts to `needsFx`; the account row (From | swap | To) is constant. Keep the
existing `SelectorCard`-based account tiles + circular `SwapButton` but lay them **horizontally**:

```kotlin
Column(
    modifier = Modifier.padding(horizontal = 24.dp)
        .then(if (!shouldFocus) Modifier.focusTarget() else Modifier),
) {
    if (state.needsFx) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TransactionEditAmountField(
                modifier = Modifier.weight(1f),
                caption = stringResource(R.string.transfer_edit_you_send),
                currencySymbol = state.sourceCurrencySymbol, value = state.amount,
                focused = state.editTarget == TransactionEditFocusTarget.Amount,
                onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusAmount) },
            )
            TransactionEditAmountField(
                modifier = Modifier.weight(1f),
                caption = stringResource(R.string.transfer_edit_you_get),
                currencySymbol = state.targetCurrencySymbol, value = state.targetAmount,
                focused = state.editTarget == TransactionEditFocusTarget.Received,
                onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusReceived) },
            )
        }
        TransactionEditRateConnector(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
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
            caption = stringResource(R.string.transaction_edit_amount_label_caption),
            currencySymbol = state.sourceCurrencySymbol, value = state.amount,
            focused = true,
            onFocus = { viewModel.perform(TransactionEditTransferViewModel.Action.FocusAmount) },
        )
    }

    AccountSelectorsWithSwap( /* existing composable; lay out horizontally — see Step 5 */
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

Delete `RateModePill` and its helpers (`formatDefaultPillText`, `computeTargetFromRate`,
`amountFormat`). Remove now-unused imports (`Rate`, `TransferRateMode`, `BasicTextField`,
`KeyboardOptions`, etc.).

- [ ] **Step 5:** Adapt `AccountSelectorsWithSwap` to a horizontal `Row` (From `SelectorCard`
weight 1f, `SwapButton` from the design — a 38.dp circle with the swap icon, no overlap, To
`SelectorCard` weight 1f). Keep the existing `SelectorCard` usage and dropdown wiring.

- [ ] **Step 6: Compile** `:zero-core:compileDebugKotlin` → PASS (with Task 8). Commit:
`feat: live-linked You-send/You-get transfer FX layout`.

---

### Task 10: Parent VM + ViewProvider — keypad routing, hide hero amount on transfer

**Files:**
- Modify: `.../TransactionEditViewModel.kt`
- Modify: `.../DefaultTransactionEditViewModel.kt`
- Modify: `.../TransactionEditViewProvider.kt`

- [ ] **Step 1:** Interface — add to `State`: `rate: String = ""`, `targetAmount: String = ""`,
`editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount`. Add actions
`data class ChangeRate(val rate: String) : Action`,
`data class ChangeTargetAmount(val amount: String) : Action`, `object FocusAmount : Action`.
Import the enum.

- [ ] **Step 2:** Default VM — map per-branch:

```kotlin
rate = when (state) {
    is TransactionEditUseCase.State.Expense -> state.rate
    is TransactionEditUseCase.State.Income -> state.rate
    is TransactionEditUseCase.State.Transfer -> state.rate
},
targetAmount = when (state) {
    is TransactionEditUseCase.State.Transfer -> state.targetAmount
    else -> ""
},
editTarget = when (state) {
    is TransactionEditUseCase.State.Expense -> state.editTarget
    is TransactionEditUseCase.State.Income -> state.editTarget
    is TransactionEditUseCase.State.Transfer -> state.editTarget
},
```

`perform`: `ChangeRate → UseCase.Action.ChangeRate`,
`ChangeTargetAmount → UseCase.Action.ChangeTargetAmount`, `FocusAmount → UseCase.Action.FocusAmount`.

- [ ] **Step 3:** ViewProvider — hide the hero `AmountDisplay` for transfer, and add `FocusAmount`
to its tap:

```kotlin
if (state.selectedTransactionType != TransactionEditType.TRANSFER) {
    AmountDisplay(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
        label = stringResource(R.string.transaction_edit_amount_display_label).uppercase(),
        amount = state.amount,
        currencySymbol = state.currencySymbol,
        onClick = {
            keypadVisible = true
            viewModel.perform(TransactionEditViewModel.Action.FocusAmount)
        },
        onCurrencyClick = if (state.canPickCurrency) {
            { viewModel.perform(TransactionEditViewModel.Action.PickCurrency) }
        } else null,
    )
}
```

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

For transfer the keypad must stay visible (no hero amount to tap) — initialise `keypadVisible` so
it is `true` whenever the type is transfer, e.g. `LaunchedEffect(state.selectedTransactionType) { if (state.selectedTransactionType == TransactionEditType.TRANSFER) keypadVisible = true }`.
Import `...edit.TransactionEditFocusTarget`.

- [ ] **Step 5: Compile** `:zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 6: Commit** `feat: route inline keypad to amount/rate/received by focus`.

---

### Task 11: Full verification

- [ ] **Step 1:** `./gradlew :zero-ui:testDebugUnitTest :zero-core:testDebugUnitTest lintDebug 2>&1 | tail -25` → all PASS. Fix any `ViewProviderDerivation` lint hits by moving the logic into the VM/use-case mapping.
- [ ] **Step 2:** Acquire emulator (`./scripts/emulator/acquire`), build+install, and via
`android-ui-inspector`:
  - **Expense**, currency ≠ account currency → ConversionCard shows auto rate + live "Converts to";
    tap rate pill → keypad edits rate (caret, 6 dp), "Converts to" updates; **Reset** appears after a
    manual edit and restores auto; tap amount → keypad edits amount (2 dp). Same-currency hides card.
  - **Transfer**, differing-currency accounts → "You send"/"You get" + rate connector; edit send →
    get updates; edit get → send back-computes; edit rate → get updates and Reset appears; swap keeps
    it consistent; same-currency accounts show a single Amount field.
- [ ] **Step 3:** Final commit for any inspector-driven polish: `fix: FX layout polish`.

---

## Self-review notes

- **Spec coverage:** keypad maxDecimals (T1); focus enum incl. Received (T2); unified use-case rate
  state + auto-derive collector + live-linking + save + mode removal (T3–4); expense/income VM (T5);
  ConversionCard + strings (T6); expense/income VP (T7); transfer VM (T8); transfer composables + VP
  rewrite (T9); parent VM/VP keypad routing + hidden hero amount on transfer (T10); tests + UI (T11).
- **Naming consistency:** `editTarget`, `rateAuto`, `rate`, `targetAmount`, `convertedAmountText`,
  `TransactionEditFocusTarget.{Amount,Rate,Received}`, actions `FocusAmount/FocusRate/FocusReceived/
  ResetRate/ChangeRate/ChangeTargetAmount` used identically across use case, both child VMs, parent
  VM, and views.
- **Removals:** `TransferRateMode.kt`, `CycleTransferRateMode`, `ChangeTransferRate`,
  `cycleTransferRateMode()`, `RateModePill`, `fetchRate`/`fetchRateIfTransfer` (collector replaces).
  After T4/T9, `grep -r TransferRateMode src/main` returns nothing.
- **Derivation placement:** `convertedAmountText` formatted in the use case; `needsFx`/`showRate`
  and currency lookups are trivial joins in the VM mapping; ViewProviders only read — satisfies
  `ViewProviderDerivation`.
- **Open checks for executor:** confirm `ZeroTheme.colors.surfaceContainerLow/surfaceContainer/
  surface/outline` token names; confirm `AmountFormatter` reaches `DefaultTransactionEditUseCase`;
  confirm edit-mode load derives a transfer `rate` string from the saved source/target amounts.
