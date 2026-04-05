# Transfer Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the transfer transaction screen with prominent amount display, rate mode cycling pill, account selectors with swap, and date picker.

**Architecture:** Extends the existing MVI flow (View Action -> ViewModel -> UseCase -> MutableStateFlow -> UI). New `TransferRateMode` sealed interface drives a 3-mode cycle (Default/CustomRate/CustomAmount). `CurrencyConvertUseCase` is wired through Dagger to fetch exchange rates. View layer fully rewritten using shared components from the expense redesign (PR #6).

**Tech Stack:** Kotlin, Jetpack Compose (Material 1), Dagger 2, Coroutines/Flow

---

### File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `zero-core/.../transactions/edit/TransferRateMode.kt` | **Create** | Sealed interface for 3 rate modes |
| `zero-core/.../transactions/edit/TransactionEditUseCase.kt` | **Modify** | Add 4 new actions, extend Transfer state |
| `zero-core/.../transactions/edit/DefaultTransactionEditUseCase.kt` | **Modify** | Handle new actions, add CurrencyConvertUseCase dep, rate fetch, fix save |
| `zero-core/.../transactions/edit/TransactionEditComponent.kt` | **Modify** | Wire CurrencyConvertUseCase through DI |
| `zero-core/.../transactions/edit/transfer/TransactionEditTransferViewModel.kt` | **Modify** | Add 4 new actions, extend state |
| `zero-core/.../transactions/edit/transfer/DefaultTransactionEditTransferViewModel.kt` | **Modify** | Map new actions/state fields |
| `zero-core/.../transactions/edit/common/AmountDisplay.kt` | **Modify** | Add `showCurrencySelector` param |
| `zero-core/.../transactions/edit/transfer/TransactionEditTransferViewProvider.kt` | **Rewrite** | New UI with AmountDisplay, RateModePill, SelectorCards, SwapButton, DatePicker |

---

### Task 1: Create TransferRateMode sealed interface

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransferRateMode.kt`

- [ ] **Step 1: Create TransferRateMode.kt**

```kotlin
package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Rate

sealed interface TransferRateMode {

    data class Default(val rate: Rate) : TransferRateMode

    data class CustomRate(val rate: String) : TransferRateMode

    data class CustomAmount(val targetAmount: String) : TransferRateMode
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransferRateMode.kt
git commit -m "feat: add TransferRateMode sealed interface"
```

---

### Task 2: Extend TransactionEditUseCase interface

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`

- [ ] **Step 1: Add 4 new actions**

Inside the `sealed interface Action` block (after the existing `Discard` action, around line 24), add:

```kotlin
        data class ChangeTargetAmount(val amount: String) : Action
        data class ChangeTransferRate(val rate: String) : Action
        object CycleTransferRateMode : Action
        object SwapAccounts : Action
```

- [ ] **Step 2: Extend Transfer state**

Replace the existing `Transfer` data class (lines 55-62):

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
            override val date: LocalDateTime = LocalDateTime.now(),
        ) : State
```

Add these imports at the top of the file:

```kotlin
import com.hluhovskyi.zero.common.Rate
```

- [ ] **Step 3: Update Noop to handle new actions**

The existing `Noop.perform` already does `Unit` for all actions, so no changes needed — the new `Action` subtypes are covered by the existing `when` exhaustiveness (it's a no-op function body, not a `when` expression).

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: Compilation errors in `DefaultTransactionEditUseCase.kt` and `DefaultTransactionEditTransferViewModel.kt` because `when` expressions are now non-exhaustive. This is expected — Task 3 and Task 5 will fix them.

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt
git commit -m "feat: extend TransactionEditUseCase with transfer rate actions and state"
```

---

### Task 3: Extend DefaultTransactionEditUseCase

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

This is the largest task. It adds the `CurrencyConvertUseCase` dependency, handles 4 new actions, adds rate fetching on account change, and fixes the transfer save logic.

- [ ] **Step 1: Add CurrencyConvertUseCase constructor parameter**

Add the import at the top:

```kotlin
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
```

Add the parameter to `DefaultTransactionEditUseCase` constructor, after `currencyRepository`:

```kotlin
    private val currencyConvertUseCase: CurrencyConvertUseCase,
```

The full constructor becomes:

```kotlin
internal class DefaultTransactionEditUseCase(
    private val transactionId: Id,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyConvertUseCase: CurrencyConvertUseCase,
    private val transactionRepository: TransactionRepository,
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val idGenerator: IdGenerator,
    private val onTransactionSavedHandler: OnTransactionSavedHandler,
    private val onEditCategoriesHandler: OnEditCategoriesHandler,
    private val onDiscardHandler: OnDiscardHandler,
    private val clock: Clock,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val coroutineScope: CoroutineScope = CoroutineScope(context = Dispatchers.IO),
    logger: Logger,
) : TransactionEditUseCase {
```

- [ ] **Step 2: Add targetAmount and transferRateMode to CompositeState**

Update the `CompositeState` data class (at the bottom of the file, around line 369) to add two new fields:

```kotlin
    private data class CompositeState(
        val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val categories: List<TransactionEditCategory> = emptyList(),
        val selectedCategory: TransactionEditCategory? = null,
        val currencies: List<TransactionEditCurrency> = emptyList(),
        val selectedCurrency: TransactionEditCurrency? = null,
        val localDateTime: LocalDateTime? = null,
        val manuallyChangedCurrency: Boolean = false,
        val amount: String = "",
        val rate: String = "",
        val targetAmount: String = "",
        val transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same),
    )
```

- [ ] **Step 3: Update Transfer state mapping**

In the `state` flow mapping (around line 79), update the `TransactionEditType.TRANSFER` branch to pass the new fields. Also resolve currency symbols from the `currencies` list:

```kotlin
                TransactionEditType.TRANSFER -> {
                    val sourceCurrencySymbol = state.selectedAccount?.let { account ->
                        state.currencies.firstOrNull { it.id == account.currencyId }?.currencySymbol
                    } ?: ""
                    val targetCurrencySymbol = state.selectedTargetAccount?.let { account ->
                        state.currencies.firstOrNull { it.id == account.currencyId }?.currencySymbol
                    } ?: ""
                    TransactionEditUseCase.State.Transfer(
                        accounts = state.accounts,
                        selectedAccount = state.selectedAccount,
                        targetAccounts = state.targetAccounts,
                        selectedTargetAccount = state.selectedTargetAccount,
                        amount = state.amount,
                        targetAmount = state.targetAmount,
                        transferRateMode = state.transferRateMode,
                        sourceCurrencySymbol = sourceCurrencySymbol,
                        targetCurrencySymbol = targetCurrencySymbol,
                        date = state.localDateTime ?: clock.localDateTime()
                    )
                }
```

- [ ] **Step 4: Handle new actions in perform()**

Add these 4 cases inside the `when (action)` block in `perform()`, after the existing `Discard` handler:

```kotlin
            is TransactionEditUseCase.Action.ChangeTargetAmount -> {
                mutableState.update { state ->
                    state.copy(targetAmount = action.amount)
                }
            }
            is TransactionEditUseCase.Action.ChangeTransferRate -> {
                mutableState.update { state ->
                    state.copy(
                        transferRateMode = TransferRateMode.CustomRate(action.rate)
                    )
                }
            }
            is TransactionEditUseCase.Action.CycleTransferRateMode -> {
                mutableState.update { state ->
                    val nextMode = when (state.transferRateMode) {
                        is TransferRateMode.Default -> TransferRateMode.CustomRate("")
                        is TransferRateMode.CustomRate -> TransferRateMode.CustomAmount("")
                        is TransferRateMode.CustomAmount -> TransferRateMode.Default(Rate.Same).let {
                            // Restore rate if accounts have different currencies
                            it // Will be updated by fetchRate if needed
                        }
                    }
                    state.copy(transferRateMode = nextMode)
                }
                // If cycling back to Default, re-fetch the rate
                val currentState = mutableState.value
                if (currentState.transferRateMode is TransferRateMode.Default) {
                    fetchRate(currentState.selectedAccount, currentState.selectedTargetAccount)
                }
            }
            is TransactionEditUseCase.Action.SwapAccounts -> {
                mutableState.update { state ->
                    state.copy(
                        selectedAccount = state.selectedTargetAccount,
                        selectedTargetAccount = state.selectedAccount,
                    )
                }
                val currentState = mutableState.value
                fetchRate(currentState.selectedAccount, currentState.selectedTargetAccount)
            }
```

- [ ] **Step 5: Add fetchRate helper method**

Add this private method to `DefaultTransactionEditUseCase`:

```kotlin
    private fun fetchRate(
        sourceAccount: TransactionEditAccount?,
        targetAccount: TransactionEditAccount?
    ) {
        if (sourceAccount == null || targetAccount == null) return
        if (sourceAccount.currencyId == targetAccount.currencyId) {
            mutableState.update { state ->
                state.copy(transferRateMode = TransferRateMode.Default(Rate.Same))
            }
            return
        }
        coroutineScope.launch {
            val rate = currencyConvertUseCase.getRate(sourceAccount.currencyId, targetAccount.currencyId)
            mutableState.update { state ->
                state.copy(transferRateMode = TransferRateMode.Default(rate))
            }
        }
    }
```

- [ ] **Step 6: Add rate fetching on account selection**

Update the `SelectAccount` action handler to also trigger rate fetch when in transfer mode:

```kotlin
            is TransactionEditUseCase.Action.SelectAccount -> {
                mutableState.update { state ->
                    state.copy(selectedAccount = action.account)
                }
                if (mutableState.value.transactionType == TransactionEditType.TRANSFER) {
                    fetchRate(action.account, mutableState.value.selectedTargetAccount)
                }
            }
```

Update the `SelectTargetAccount` action handler similarly:

```kotlin
            is TransactionEditUseCase.Action.SelectTargetAccount -> {
                mutableState.update { state ->
                    state.copy(selectedTargetAccount = action.account)
                }
                if (mutableState.value.transactionType == TransactionEditType.TRANSFER) {
                    fetchRate(mutableState.value.selectedAccount, action.account)
                }
            }
```

- [ ] **Step 7: Fix transfer save logic**

Replace the `TransactionEditType.TRANSFER` branch in the `Save` action handler (around line 177):

```kotlin
                        TransactionEditType.TRANSFER -> {
                            val account = state.selectedAccount ?: return@launch
                            val targetAccount = state.selectedTargetAccount ?: return@launch

                            val sourceAmount = Amount(state.amount.toBigDecimalOrNull())
                            val computedTargetAmount = when (val mode = state.transferRateMode) {
                                is TransferRateMode.Default -> sourceAmount.withRate(mode.rate)
                                is TransferRateMode.CustomRate -> {
                                    val customRate = Rate(mode.rate.toBigDecimalOrNull())
                                    sourceAmount.withRate(customRate)
                                }
                                is TransferRateMode.CustomAmount -> {
                                    Amount(state.targetAmount.toBigDecimalOrNull())
                                }
                            }

                            TransactionRepository.Transaction.Transfer(
                                id = transactionId,
                                amount = sourceAmount,
                                accountId = account.id,
                                currencyId = account.currencyId,
                                targetAccount = targetAccount.id,
                                dateTime = dateTime,
                                targetAmount = computedTargetAmount
                            )
                        }
```

Note: `currencyId` now uses `account.currencyId` instead of `currency.id` since transfers derive currency from the account, not from a separate currency selector.

- [ ] **Step 8: Verify it compiles**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: Compilation errors in `DefaultTransactionEditTransferViewModel.kt` only (its `when` is non-exhaustive). This is expected — Task 5 fixes it.

- [ ] **Step 9: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
git commit -m "feat: handle transfer rate modes, rate fetching, and fix save logic in UseCase"
```

---

### Task 4: Wire CurrencyConvertUseCase through Dagger

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

- [ ] **Step 1: Add CurrencyConvertUseCase to Dependencies interface**

Add import at top:

```kotlin
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
```

Add to the `Dependencies` interface (after `currencyRepository`):

```kotlin
        val currencyConvertUseCase: CurrencyConvertUseCase
```

- [ ] **Step 2: Pass CurrencyConvertUseCase to UseCase provider**

Update the `Module.useCase()` function — add `currencyConvertUseCase` parameter and pass it to the constructor:

```kotlin
        @Provides
        @TransactionEditScope
        fun useCase(
            transactionId: Id,
            accountRepository: AccountRepository,
            categoriesQueryUseCase: CategoriesQueryUseCase,
            currencyRepository: CurrencyRepository,
            currencyConvertUseCase: CurrencyConvertUseCase,
            transactionRepository: TransactionRepository,
            idGenerator: IdGenerator,
            onTransactionSavedHandler: OnTransactionSavedHandler,
            onEditCategoriesHandler: OnEditCategoriesHandler,
            onDiscardHandler: OnDiscardHandler,
            incorrectStateDetector: IncorrectStateDetector,
            clock: Clock,
            logger: Logger,
        ): TransactionEditUseCase = DefaultTransactionEditUseCase(
            transactionId = transactionId,
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            currencyConvertUseCase = currencyConvertUseCase,
            transactionRepository = transactionRepository,
            categoriesQueryUseCase = categoriesQueryUseCase,
            idGenerator = idGenerator,
            onTransactionSavedHandler = onTransactionSavedHandler,
            onEditCategoriesHandler = onEditCategoriesHandler,
            onDiscardHandler = onDiscardHandler,
            incorrectStateDetector = incorrectStateDetector,
            clock = clock,
            logger = logger
        )
```

- [ ] **Step 3: Verify the `app` module provides CurrencyConvertUseCase to TransactionEditComponent.Dependencies**

Check that wherever `TransactionEditComponent.Dependencies` is implemented (in the `app` module), `currencyConvertUseCase` is available. Since it's already exposed at the app-level Dagger component (it's used elsewhere), this should be automatically satisfied. If not, the Dagger compiler will produce a clear error.

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (or errors in `DefaultTransactionEditTransferViewModel.kt` from Task 5, not from DI)

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt
git commit -m "feat: wire CurrencyConvertUseCase through TransactionEditComponent DI"
```

---

### Task 5: Extend TransactionEditTransferViewModel and DefaultTransactionEditTransferViewModel

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/DefaultTransactionEditTransferViewModel.kt`

- [ ] **Step 1: Add new actions and state fields to TransactionEditTransferViewModel**

Replace the entire file content:

```kotlin
package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.common.ActionStateModel
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransferRateMode
import java.time.LocalDateTime

interface TransactionEditTransferViewModel
    : ActionStateModel<TransactionEditTransferViewModel.Action, TransactionEditTransferViewModel.State> {

    sealed interface Action {
        data class SelectAccount(val account: TransactionEditAccount) : Action
        data class SelectTargetAccount(val account: TransactionEditAccount) : Action
        data class ChangeAmount(val amount: String) : Action
        data class ChangeTargetAmount(val amount: String) : Action
        data class ChangeTransferRate(val rate: String) : Action
        data class ChangeDate(val date: LocalDateTime) : Action
        object CycleRateMode : Action
        object SwapAccounts : Action
    }

    data class State(
        val accounts: List<TransactionEditAccount> = emptyList(),
        val selectedAccount: TransactionEditAccount? = null,
        val targetAccounts: List<TransactionEditAccount> = emptyList(),
        val selectedTargetAccount: TransactionEditAccount? = null,
        val amount: String = "",
        val targetAmount: String = "",
        val transferRateMode: TransferRateMode = TransferRateMode.Default(Rate.Same),
        val sourceCurrencySymbol: String = "",
        val targetCurrencySymbol: String = "",
        val date: LocalDateTime = LocalDateTime.now(),
    )
}
```

- [ ] **Step 2: Update DefaultTransactionEditTransferViewModel**

Replace the entire file content:

```kotlin
package com.hluhovskyi.zero.transactions.edit.transfer

import com.hluhovskyi.zero.transactions.edit.TransactionEditUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultTransactionEditTransferViewModel(
    private val useCase: TransactionEditUseCase
) : TransactionEditTransferViewModel {

    override val state: Flow<TransactionEditTransferViewModel.State> = useCase.state
        .filterIsInstance<TransactionEditUseCase.State.Transfer>()
        .map { state ->
            TransactionEditTransferViewModel.State(
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                targetAccounts = state.targetAccounts,
                selectedTargetAccount = state.selectedTargetAccount,
                amount = state.amount,
                targetAmount = state.targetAmount,
                transferRateMode = state.transferRateMode,
                sourceCurrencySymbol = state.sourceCurrencySymbol,
                targetCurrencySymbol = state.targetCurrencySymbol,
                date = state.date,
            )
        }

    override fun perform(action: TransactionEditTransferViewModel.Action) {
        val useCaseAction = when (action) {
            is TransactionEditTransferViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)
            is TransactionEditTransferViewModel.Action.SelectAccount ->
                TransactionEditUseCase.Action.SelectAccount(action.account)
            is TransactionEditTransferViewModel.Action.SelectTargetAccount ->
                TransactionEditUseCase.Action.SelectTargetAccount(action.account)
            is TransactionEditTransferViewModel.Action.ChangeTargetAmount ->
                TransactionEditUseCase.Action.ChangeTargetAmount(action.amount)
            is TransactionEditTransferViewModel.Action.ChangeTransferRate ->
                TransactionEditUseCase.Action.ChangeTransferRate(action.rate)
            is TransactionEditTransferViewModel.Action.ChangeDate ->
                TransactionEditUseCase.Action.ChangeDate(action.date)
            is TransactionEditTransferViewModel.Action.CycleRateMode ->
                TransactionEditUseCase.Action.CycleTransferRateMode
            is TransactionEditTransferViewModel.Action.SwapAccounts ->
                TransactionEditUseCase.Action.SwapAccounts
        }
        useCase.perform(useCaseAction)
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: Compilation errors in `TransactionEditTransferViewProvider.kt` only (it still references old composables). This is expected — Task 7 rewrites it.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewModel.kt
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/DefaultTransactionEditTransferViewModel.kt
git commit -m "feat: extend transfer ViewModel with rate mode, swap, and date actions"
```

---

### Task 6: Extend AmountDisplay with showCurrencySelector param

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/AmountDisplay.kt`

- [ ] **Step 1: Add showCurrencySelector parameter**

Add the parameter to `AmountDisplay` function signature. Place it after `onCurrencySelected`:

```kotlin
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
    currencies: List<TransactionEditCurrency> = emptyList(),
    onCurrencySelected: (TransactionEditCurrency) -> Unit = {},
    showCurrencySelector: Boolean = true,
) {
```

Note: `currencies` and `onCurrencySelected` now have defaults so transfer callers can omit them.

- [ ] **Step 2: Conditionally show/hide the dropdown arrow and click**

Replace the `Box` that contains the currency symbol and dropdown (lines 85-122) with:

```kotlin
            Box {
                Row(
                    modifier = Modifier
                        .then(
                            if (showCurrencySelector) Modifier.clickable { expanded = true }
                            else Modifier
                        )
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currencySymbol,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                    )
                    if (showCurrencySelector) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = OnSurfaceVariant,
                        )
                    }
                }

                if (showCurrencySelector) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        currencies.forEach { currencyItem ->
                            DropdownMenuItem(
                                onClick = {
                                    onCurrencySelected(currencyItem)
                                    expanded = false
                                }
                            ) {
                                Text(text = "${currencyItem.currencySymbol} - ${currencyItem.name}")
                            }
                        }
                    }
                }
            }
```

- [ ] **Step 3: Verify existing callers still work**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: Existing `AmountDisplay` calls in expense/income views pass `currencies` and `onCurrencySelected` explicitly, so they continue to work with `showCurrencySelector` defaulting to `true`.

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/AmountDisplay.kt
git commit -m "feat: add showCurrencySelector param to AmountDisplay"
```

---

### Task 7: Rewrite TransactionEditTransferViewProvider

**Files:**
- Rewrite: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewProvider.kt`

- [ ] **Step 1: Write the complete new view**

Replace the entire file:

```kotlin
package com.hluhovskyi.zero.transactions.edit.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.edit.TransferRateMode
import com.hluhovskyi.zero.transactions.edit.common.AmountDisplay
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerHigh
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

internal class TransactionEditTransferViewProvider(
    private val viewModel: TransactionEditTransferViewModel
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditTransferView(viewModel = viewModel)
    }
}

@Composable
private fun TransactionEditTransferView(
    viewModel: TransactionEditTransferViewModel
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditTransferViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        // Amount display
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 24.dp),
            amount = state.amount,
            currencySymbol = state.sourceCurrencySymbol,
            focusRequester = focusRequester,
            onAmountChange = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeAmount(it))
            },
            showCurrencySelector = false,
        )

        // Rate mode pill
        RateModePill(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            state = state,
            onCycleMode = {
                viewModel.perform(TransactionEditTransferViewModel.Action.CycleRateMode)
            },
            onRateChange = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeTransferRate(it))
            },
            onTargetAmountChange = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeTargetAmount(it))
            },
        )

        // From / Swap / To account selectors
        AccountSelectorsWithSwap(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            state = state,
            onSourceSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectAccount(it))
            },
            onTargetSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SelectTargetAccount(it))
            },
            onSwap = {
                viewModel.perform(TransactionEditTransferViewModel.Action.SwapAccounts)
            }
        )

        // Date picker (full width)
        DatePickerCard(
            modifier = Modifier.fillMaxWidth(),
            label = "Date",
            date = state.date,
            onDateSelected = {
                viewModel.perform(TransactionEditTransferViewModel.Action.ChangeDate(it))
            }
        )
    }
}

@Composable
private fun RateModePill(
    modifier: Modifier = Modifier,
    state: TransactionEditTransferViewModel.State,
    onCycleMode: () -> Unit,
    onRateChange: (String) -> Unit,
    onTargetAmountChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable { onCycleMode() }
            .padding(16.dp),
    ) {
        when (val mode = state.transferRateMode) {
            is TransferRateMode.Default -> {
                val pillText = formatDefaultPillText(
                    amount = state.amount,
                    rate = mode.rate,
                    sourceCurrencySymbol = state.sourceCurrencySymbol,
                    targetCurrencySymbol = state.targetCurrencySymbol,
                )
                Text(
                    text = pillText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant,
                )
            }

            is TransferRateMode.CustomRate -> {
                Text(
                    text = "RATE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                BasicTextField(
                    value = mode.rate,
                    onValueChange = onRateChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (mode.rate.isEmpty()) {
                            Text(
                                text = "Enter rate",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    }
                )
                // Show computed destination amount read-only
                val computedTarget = computeTargetFromRate(state.amount, mode.rate, state.targetCurrencySymbol)
                if (computedTarget.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = computedTarget,
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                    )
                }
            }

            is TransferRateMode.CustomAmount -> {
                Text(
                    text = "DESTINATION AMOUNT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                BasicTextField(
                    value = state.targetAmount,
                    onValueChange = onTargetAmountChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.targetCurrencySymbol.isNotEmpty()) {
                                Text(
                                    text = state.targetCurrencySymbol,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            Box {
                                if (state.targetAmount.isEmpty()) {
                                    Text(
                                        text = "0",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountSelectorsWithSwap(
    modifier: Modifier = Modifier,
    state: TransactionEditTransferViewModel.State,
    onSourceSelected: (com.hluhovskyi.zero.transactions.edit.TransactionEditAccount) -> Unit,
    onTargetSelected: (com.hluhovskyi.zero.transactions.edit.TransactionEditAccount) -> Unit,
    onSwap: () -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SelectorCard(
                modifier = Modifier.fillMaxWidth(),
                label = "From",
                value = state.selectedAccount?.name ?: "",
                items = state.accounts,
                nameMapping = { it.name },
                onItemSelected = onSourceSelected,
            )
            SelectorCard(
                modifier = Modifier.fillMaxWidth(),
                label = "To",
                value = state.selectedTargetAccount?.name ?: "",
                items = state.targetAccounts,
                nameMapping = { it.name },
                onItemSelected = onTargetSelected,
            )
        }

        // Swap button — centered, overlapping between cards
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-12).dp)
                .zIndex(1f)
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHigh)
                .clickable { onSwap() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = "Swap accounts",
                modifier = Modifier.size(20.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}

private val amountFormat = DecimalFormat("#,##0.00")

private fun formatDefaultPillText(
    amount: String,
    rate: Rate,
    sourceCurrencySymbol: String,
    targetCurrencySymbol: String,
): String {
    val sourceAmount = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val targetAmount = sourceAmount.multiply(rate.value)
    val formattedTarget = amountFormat.format(targetAmount)

    return if (sourceCurrencySymbol == targetCurrencySymbol || rate is Rate.Same) {
        "Receives $formattedTarget"
    } else {
        val formattedRate = rate.value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        "Receives $targetCurrencySymbol$formattedTarget \u00B7 1 $sourceCurrencySymbol = $formattedRate $targetCurrencySymbol"
    }
}

private fun computeTargetFromRate(
    sourceAmount: String,
    rateStr: String,
    targetCurrencySymbol: String,
): String {
    val amount = sourceAmount.toBigDecimalOrNull() ?: return ""
    val rate = rateStr.toBigDecimalOrNull() ?: return ""
    if (rate.compareTo(BigDecimal.ZERO) == 0) return ""
    val target = amount.multiply(rate)
    val formatted = amountFormat.format(target)
    return if (targetCurrencySymbol.isNotEmpty()) {
        "Receives $targetCurrencySymbol$formatted"
    } else {
        "Receives $formatted"
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :zero-core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewProvider.kt
git commit -m "feat: rewrite transfer view with AmountDisplay, rate pill, swap, and date picker"
```

---

### Task 8: Full build verification

- [ ] **Step 1: Run full project build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: If build fails, fix compilation errors**

Common issues to watch for:
- Missing imports (especially `Icons.Filled.SwapVert` may need `material-icons-extended` dependency — check if it's already in `build.gradle`; if not, use `Icons.Default.SwapVert` or a different icon)
- The `Rate.Same` check via `is Rate.Same` may need to be `rate == Rate.Same` since `Same` is an `object`
- `TransactionEditAccount` import may need to be fully qualified in the ViewProvider since it's in a parent package

- [ ] **Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve build issues from transfer redesign"
```
