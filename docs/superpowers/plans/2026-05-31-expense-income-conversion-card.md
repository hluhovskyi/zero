# Expense/Income ConversionCard FX UX — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain `OutlinedTextField` exchange-rate field on the Expense/Income
transaction screen with the design's ConversionCard — a live "converts to" preview plus an
inline-keypad-editable rate pill with an auto-derived default and a reset affordance.

**Architecture:** The rate lives in the shared `TransactionEditUseCase` (single source of truth
for the parent `TransactionEditViewModel` and the child `TransactionEditExpenseIncomeViewModel`).
A new `editTarget` (Amount | Rate) routes the existing inline `AmountKeypad` to whichever field is
focused. The UseCase auto-derives the rate from the currency pair via `CurrencyConvertUseCase`
(the same source the Transfer flow uses) and pre-computes the formatted "converts to" string, so
the ViewModel/ViewProvider do no derivation (zero-core `ViewProviderDerivation` rule). Transfer's
`TransferRateMode` is untouched.

**Tech Stack:** Kotlin, Jetpack Compose (Material + ZeroTheme tokens), Dagger, coroutines/Flow.

**Spec:** `docs/superpowers/specs/2026-05-31-expense-income-conversion-card-design.md`

Read first: `zero-core/AGENTS.md` (rule 7 — ViewProvider runs no derivation).

---

### Task 1: `AmountKeypad` accepts `maxDecimals`

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountKeypad.kt`
- Test: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/AmountKeypadTest.kt`

- [ ] **Step 1: Add failing tests** for a 6-decimal cap in `AmountKeypadTest.kt`:

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

- [ ] **Step 2: Run, verify fail**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "*AmountKeypadTest*"`
Expected: FAIL (no `maxDecimals` parameter).

- [ ] **Step 3: Implement.** Give `handleAmountKeypadKey` a `maxDecimals: Int = 2` param and use
it in the decimal-cap check; add the same param to the `AmountKeypad` composable and pass it down:

```kotlin
internal fun handleAmountKeypadKey(value: String, key: String, maxDecimals: Int = 2): String = when {
    key == "⌫" -> if (value.length <= 1) "0" else value.dropLast(1)
    key == "." -> if (value.contains(".")) value else "$value."
    else -> {
        if (value == "0") {
            key
        } else {
            val dotIndex = value.indexOf('.')
            if (dotIndex >= 0 && value.length - dotIndex - 1 >= maxDecimals) {
                value
            } else {
                "$value$key"
            }
        }
    }
}
```

In `AmountKeypad(...)` add `maxDecimals: Int = 2` to the signature (after `keyHeight`) and change
the key handler call to `onChange(handleAmountKeypadKey(value, key, maxDecimals))`.

- [ ] **Step 4: Run, verify pass** (same command). All existing + new tests PASS.

- [ ] **Step 5: Commit** `feat: AmountKeypad supports configurable maxDecimals`.

---

### Task 2: `TransactionEditFocusTarget` enum

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditFocusTarget.kt`

- [ ] **Step 1: Create the enum:**

```kotlin
package com.hluhovskyi.zero.transactions.edit

/** Which field the inline amount keypad currently drives on the expense/income screen. */
enum class TransactionEditFocusTarget {
    Amount,
    Rate,
}
```

- [ ] **Step 2: Compile** `./gradlew :zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 3: Commit** `feat: add TransactionEditFocusTarget enum`.

---

### Task 3: Extend `TransactionEditUseCase` State + Actions

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`

- [ ] **Step 1:** Add three actions to `sealed interface Action`:

```kotlin
object FocusAmount : Action
object FocusRate : Action
object ResetRate : Action
```

- [ ] **Step 2:** Add fields to **both** `State.Expense` and `State.Income` (keep existing fields):

```kotlin
val rateAuto: Boolean = true,
val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
val convertedAmountText: String = "",
```

- [ ] **Step 3: Compile** `./gradlew :zero-core:compileDebugKotlin`. Expected: FAIL in
`DefaultTransactionEditUseCase` / VMs (handled next tasks). It's fine to commit after Task 4
compiles; for now just verify the interface file itself has no syntax errors by reading it back.

- [ ] **Step 4: Commit** with Task 4 (these compile together).

---

### Task 4: `DefaultTransactionEditUseCase` — auto-rate, focus, reset, converted text

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt` (constructor wiring if `AmountFormatter` not already provided — check first)

- [ ] **Step 1:** Inject `AmountFormatter`. Add `private val amountFormatter: AmountFormatter,`
to the constructor (place near `idGenerator`). Check `TransactionEditComponent.kt` /
`MainActivityScreenComponent.kt` to confirm `AmountFormatter` is available in this graph (the
parent `DefaultTransactionEditViewModel` already receives one, so the binding exists) and pass it
through. Import `com.hluhovskyi.zero.common.AmountFormatter` and `com.hluhovskyi.zero.common.Amount`.

- [ ] **Step 2:** Add `rateAuto`, `editTarget` to `CompositeState`:

```kotlin
val rateAuto: Boolean = true,
val editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount,
```

- [ ] **Step 3:** In the `state` mapping, for the `Expense` and `Income` branches, compute the
converted text and the effective edit target, then pass the new fields. Add this helper above the
`when`:

```kotlin
val currenciesDiffer = state.selectedCurrency != null && state.selectedAccount != null &&
    state.selectedCurrency.id != state.selectedAccount.currencyId
val acctSymbol = state.selectedAccount?.let { acc ->
    state.currencies.firstOrNull { it.id == acc.currencyId }?.currencySymbol
}.orEmpty()
val convertedText = if (currenciesDiffer) {
    val converted = Amount(state.amount.toBigDecimalOrNull()).withRate(Rate(state.rate.toBigDecimalOrNull()))
    "≈ " + amountFormatter.format(converted, acctSymbol)
} else {
    ""
}
val effectiveTarget = if (currenciesDiffer) state.editTarget else TransactionEditFocusTarget.Amount
```

Pass `rate = state.rate, rateAuto = state.rateAuto, editTarget = effectiveTarget,
convertedAmountText = convertedText` into both `State.Expense(...)` and `State.Income(...)`.

- [ ] **Step 4:** Handle the new actions in `perform(...)`:

```kotlin
is TransactionEditUseCase.Action.FocusAmount ->
    mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Amount) }

is TransactionEditUseCase.Action.FocusRate ->
    mutableState.update { it.copy(editTarget = TransactionEditFocusTarget.Rate) }

is TransactionEditUseCase.Action.ResetRate -> resetExpenseIncomeRate()
```

In the existing `ChangeRate` branch, also set `rateAuto = false`:

```kotlin
is TransactionEditUseCase.Action.ChangeRate ->
    mutableState.update { it.copy(rate = action.rate, rateAuto = false) }
```

In the existing `ChangeAmount` branch, also reset focus to Amount:

```kotlin
is TransactionEditUseCase.Action.ChangeAmount ->
    mutableState.update { it.copy(amount = action.amount, editTarget = TransactionEditFocusTarget.Amount) }
```

- [ ] **Step 5:** Add the reset helper + a shared rate-derivation helper near `fetchRate`:

```kotlin
private fun resetExpenseIncomeRate() {
    coroutineScope.launch {
        val state = mutableState.value
        val rate = deriveExpenseIncomeRate(state)
        mutableState.update {
            it.copy(rate = rate ?: it.rate, rateAuto = true, editTarget = TransactionEditFocusTarget.Amount)
        }
    }
}

private suspend fun deriveExpenseIncomeRate(state: CompositeState): String? {
    val currencyId = state.selectedCurrency?.id ?: return null
    val accountCurrencyId = state.selectedAccount?.currencyId ?: return null
    if (currencyId == accountCurrencyId) return null
    return currencyConvertUseCase.getRate(currencyId, accountCurrencyId).value.format()
}
```

(`Id` equality: `selectedCurrency.id` is `Id.Known`; `account.currencyId` is `Id`. Compare with
`!=` as elsewhere in this file, e.g. line ~688.)

- [ ] **Step 6:** Auto-derive reactively. Add a `launch { }` inside `attach()`'s outer
`coroutineScope.launch` (alongside the other `launch` blocks) that re-derives the rate when the
expense/income currency pair changes while `rateAuto` is true:

```kotlin
launch {
    mutableState
        .map { Triple(it.selectedCurrency?.id, it.selectedAccount?.currencyId, it.transactionType) }
        .distinctUntilChanged()
        .collectLatest { (currencyId, accountCurrencyId, type) ->
            if (type == TransactionEditType.TRANSFER) return@collectLatest
            if (!mutableState.value.rateAuto) return@collectLatest
            if (currencyId == null || accountCurrencyId == null || currencyId == accountCurrencyId) {
                return@collectLatest
            }
            val rate = currencyConvertUseCase.getRate(currencyId, accountCurrencyId).value.format()
            mutableState.update { if (it.rateAuto) it.copy(rate = rate) else it }
        }
}
```

- [ ] **Step 7: Compile** `./gradlew :zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 8: Commit** `feat: auto-derive + focus/reset expense-income FX rate in use case`.

---

### Task 5: Child VM — semantic state + actions

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/DefaultTransactionEditExpenseIncomeViewModel.kt`

- [ ] **Step 1:** In the interface, add actions `object FocusRate : Action` and
`object ResetRate : Action`. Extend `State` with the pre-computed fields and drop the old
`showRate` derivation in favour of a passed-in flag:

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

Import `com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget`. Remove the existing
`val showRate: Boolean = ...` computed body (it's now a constructor field).

- [ ] **Step 2:** In `DefaultTransactionEditExpenseIncomeViewModel`, map the new fields for both
`Expense` and `Income`. Compute the currency display values from the UseCase state's
`currencies`/`selectedAccount`/`selectedCurrency` (this is trivial join/lookup, allowed in the VM
mapping — same style as the existing Transfer symbol lookup in `DefaultTransactionEditUseCase`).
Add a private helper to avoid duplication:

```kotlin
private fun TransactionEditUseCase.State.Expense.toState() = buildState(
    accounts, selectedAccount, categories, selectedCategory, currencies, selectedCurrency,
    amount, rate, rateAuto, editTarget, convertedAmountText, date,
)
// identical for Income
```

Where `buildState(...)` resolves:

```kotlin
val accountCurrency = currencies.firstOrNull { it.id == selectedAccount?.currencyId }
val showRate = selectedCurrency != null && selectedAccount != null &&
    selectedCurrency.id != selectedAccount.currencyId
TransactionEditExpenseIncomeViewModel.State(
    /* passthrough fields … */
    rateAuto = rateAuto,
    editTarget = editTarget,
    convertedAmountText = convertedAmountText,
    accountCurrencyName = accountCurrency?.name.orEmpty(),
    accountCurrencySymbol = accountCurrency?.currencySymbol.orEmpty(),
    txCurrencySymbol = selectedCurrency?.currencySymbol.orEmpty(),
    showRate = showRate,
)
```

Keep the existing `.filter { Expense || Income }` and call the per-branch helper in `.map`.

- [ ] **Step 3:** In `perform(...)`, map the two new actions:

```kotlin
is TransactionEditExpenseIncomeViewModel.Action.FocusRate ->
    TransactionEditUseCase.Action.FocusRate
is TransactionEditExpenseIncomeViewModel.Action.ResetRate ->
    TransactionEditUseCase.Action.ResetRate
```

- [ ] **Step 4: Compile** `./gradlew :zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 5: Commit** `feat: expose ConversionCard state on expense-income view model`.

---

### Task 6: `TransactionEditConversionCard` composable + strings

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditConversionCard.kt`
- Delete: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditRateTextField.kt`
- Modify: `zero-core/src/main/res/values/strings.xml`

Model after `transfer/TransactionEditTransferViewProvider.kt`'s `RateModePill` for token usage.

- [ ] **Step 1:** Add strings (under the `<!-- Transaction Edit -->` block):

```xml
<string name="transaction_edit_converts_to">Converts to</string>
<string name="transaction_edit_rate_reset">Reset</string>
```

- [ ] **Step 2:** Create the composable. The pill row reads `1 {txSymbol} = {rate} {acctSymbol}`
with a blinking-free caret bar shown when focused; trailing edit/reset affordance:

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
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResourceUpper(R.string.transaction_edit_converts_to) + "  " + accountCurrencyName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = convertedAmountText,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primaryContainer,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (focused) ZeroTheme.colors.surface else ZeroTheme.colors.surfaceContainerHigh,
                    RoundedCornerShape(12.dp),
                )
                .then(
                    if (focused) {
                        Modifier.border(1.5.dp, ZeroTheme.colors.primaryContainer, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onFocusRate)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "1 $txCurrencySymbol = ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = rate.ifEmpty { "0" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ZeroTheme.colors.primaryContainer,
                )
                if (focused) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(2.dp)
                            .height(16.dp)
                            .background(ZeroTheme.colors.primaryContainer),
                    )
                }
                Text(
                    text = " $accountCurrencySymbol",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                )
            }
            if (rateAuto) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = ZeroTheme.colors.outline,
                )
            } else {
                Row(
                    modifier = Modifier.clickable(onClick = onResetRate),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = ZeroTheme.colors.primaryContainer,
                    )
                    Text(
                        text = stringResourceUpper(R.string.transaction_edit_rate_reset),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.primaryContainer,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}
```

Add a tiny helper (or inline `stringResource(id).uppercase()`):

```kotlin
@Composable
private fun stringResourceUpper(id: Int) = androidx.compose.ui.res.stringResource(id).uppercase()
```

(If `ZeroTheme.colors.surfaceContainerHigh` is absent, use `surfaceContainer` — grep
`ZeroColors` for the exact token names before writing.)

- [ ] **Step 3: Compile** `./gradlew :zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 4: Commit** `feat: add TransactionEditConversionCard, drop rate text field`.

---

### Task 7: Swap the field in the child ViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt`

- [ ] **Step 1:** Replace the `AnimatedVisibility(visible = state.showRate) { TransactionEditRateTextField(...) }`
block with the ConversionCard:

```kotlin
AnimatedVisibility(visible = state.showRate) {
    TransactionEditConversionCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        txCurrencySymbol = state.txCurrencySymbol,
        accountCurrencyName = state.accountCurrencyName,
        accountCurrencySymbol = state.accountCurrencySymbol,
        rate = state.rate,
        rateAuto = state.rateAuto,
        convertedAmountText = state.convertedAmountText,
        focused = state.editTarget == TransactionEditFocusTarget.Rate,
        onFocusRate = {
            viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.FocusRate)
        },
        onResetRate = {
            viewModel.perform(TransactionEditExpenseIncomeViewModel.Action.ResetRate)
        },
    )
}
```

Import `com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget`.

- [ ] **Step 2: Compile** `./gradlew :zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 3: Commit** `feat: render ConversionCard on expense-income screen`.

---

### Task 8: Route the inline keypad to the focused field

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

- [ ] **Step 1:** In `TransactionEditViewModel` (interface), add `rate: String = ""` and
`editTarget: TransactionEditFocusTarget = TransactionEditFocusTarget.Amount` to `State`, and add
actions `data class ChangeRate(val rate: String) : Action` and `object FocusAmount : Action`.
Import the enum.

- [ ] **Step 2:** In `DefaultTransactionEditViewModel.state` mapping, set:

```kotlin
rate = when (state) {
    is TransactionEditUseCase.State.Expense -> state.rate
    is TransactionEditUseCase.State.Income -> state.rate
    is TransactionEditUseCase.State.Transfer -> ""
},
editTarget = when (state) {
    is TransactionEditUseCase.State.Expense -> state.editTarget
    is TransactionEditUseCase.State.Income -> state.editTarget
    is TransactionEditUseCase.State.Transfer -> TransactionEditFocusTarget.Amount
},
```

In `perform(...)` map:

```kotlin
is TransactionEditViewModel.Action.ChangeRate -> TransactionEditUseCase.Action.ChangeRate(action.rate)
is TransactionEditViewModel.Action.FocusAmount -> TransactionEditUseCase.Action.FocusAmount
```

- [ ] **Step 3:** In `TransactionEditViewProvider`, route the keypad and amount tap. Replace the
`AmountDisplay(... onClick = { keypadVisible = true } ...)` `onClick` with one that also focuses
amount, and the `AmountKeypad(...)` block with focus-aware wiring:

```kotlin
onClick = {
    keypadVisible = true
    viewModel.perform(TransactionEditViewModel.Action.FocusAmount)
},
```

```kotlin
AnimatedVisibility(visible = keypadVisible) {
    val editingRate = state.editTarget == TransactionEditFocusTarget.Rate
    AmountKeypad(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        value = if (editingRate) state.rate else state.amount,
        onChange = {
            if (editingRate) {
                viewModel.perform(TransactionEditViewModel.Action.ChangeRate(it))
            } else {
                viewModel.perform(TransactionEditViewModel.Action.ChangeAmount(it))
            }
        },
        maxDecimals = if (editingRate) 6 else 2,
        keyHeight = 58.dp,
    )
}
```

Import `com.hluhovskyi.zero.transactions.edit.TransactionEditFocusTarget`.

- [ ] **Step 4: Compile** `./gradlew :zero-core:compileDebugKotlin` → PASS.
- [ ] **Step 5: Commit** `feat: inline keypad drives amount or FX rate by focus`.

---

### Task 9: Full verification

- [ ] **Step 1:** `./gradlew :zero-ui:testDebugUnitTest :zero-core:testDebugUnitTest lintDebug 2>&1 | tail -25` → all PASS.
- [ ] **Step 2:** Build + install per project scripts; via `android-ui-inspector`, open New
Transaction (Expense), pick a currency different from the account currency, confirm the
ConversionCard appears with an auto rate and live "Converts to" total; tap the rate pill → keypad
edits the rate (caret on pill, "Converts to" updates, 6 decimals allowed); confirm **Reset**
appears after a manual edit and restores the auto rate; tap the amount → keypad edits the amount
again (2 decimals). Confirm same-currency case hides the card; confirm the Transfer tab is
unchanged.
- [ ] **Step 3:** Final commit if any inspector-driven tweaks: `fix: ConversionCard layout polish`.

---

## Self-review notes

- **Spec coverage:** keypad maxDecimals (T1), focus enum (T2), UseCase state+auto-rate+reset+focus
  +converted text (T3–4), child VM semantic state (T5), ConversionCard + strings (T6), ViewProvider
  swap (T7), keypad routing + amount focus (T8), tests + UI verify (T9). Transfer untouched.
- **Naming consistency:** `editTarget`, `rateAuto`, `convertedAmountText`, `TransactionEditFocusTarget`,
  actions `FocusAmount`/`FocusRate`/`ResetRate` used identically across UseCase, both VMs, and views.
- **Derivation placement:** `convertedAmountText` formatted in the UseCase (injects `AmountFormatter`);
  VMs/ViewProviders only read it — satisfies `ViewProviderDerivation`.
- **Open check for executor:** confirm `ZeroTheme.colors.surfaceContainerHigh` exists (else use
  `surfaceContainer`); confirm `AmountFormatter` binding reaches `DefaultTransactionEditUseCase`.
