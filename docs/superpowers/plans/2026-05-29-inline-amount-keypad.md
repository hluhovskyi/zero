# Inline Amount Keypad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the system numeric keyboard on the New/Edit Transaction screen with an in-app inline keypad that is docked at the bottom, drives the amount field, gives native-style haptic feedback, and is the *same* shared component the Budget screens already use.

**Architecture:** Promote the existing budget `NumPad` (zero-ui) into a shared `AmountKeypad` with haptics, used by both Budget and Transaction. On the Transaction screen, **lift the amount display up to the parent `TransactionEditView`** (it already shares the `TransactionEditUseCase` instance with its children), pin header + segmented toggle + amount at the top, scroll the rest, and dock the FAB-on-top-of-keypad cluster at the bottom. The amount display becomes a tap-to-focus, caret-only label (no system IME); the keypad edits the amount via `ChangeAmount`.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, existing `TransactionEditUseCase` state machine.

---

## Scope & Non-Goals

In scope (maps to the 7 requirements):
1. Minimal keypad only — digits `1-9 . 0 ⌫`, **no calculator operators** (the existing `NumPad` is already exactly this layout).
2. Native-style haptic feedback on every key press (`HapticFeedbackConstants.KEYBOARD_TAP`).
3. Keypad is fixed (non-scrollable); only the form above it scrolls.
4. The Save FAB is pinned directly on top of the keypad.
5. One shared component reused by Budget + Transaction (moved into a general `zero-ui` package).
6. Keypad wired to amount entry (`TransactionEditUseCase.Action.ChangeAmount`).
7. On **Edit** (bottom sheet) the keypad appears only after the user taps the amount; on **New** (full screen) it auto-shows.

Non-goals (explicitly out of scope, leave as-is):
- The Transfer screen's **rate** and **target-amount** fields keep their existing system `Decimal` keyboards. The inline keypad drives only the primary/source amount.
- No calculator/expression mode (requirement #1 — "without calculator signs right now").
- Auto-expanding the Edit bottom sheet to full height when the keypad opens — the keypad renders within the current sheet height; the sheet is already drag-expandable. (Known minor limitation; note in PR.)

---

## File Structure

**zero-ui (shared component):**
- Rename/move `zero-ui/.../ui/budget/NumPad.kt` → `zero-ui/.../ui/AmountKeypad.kt` (package `com.hluhovskyi.zero.ui`, composable `AmountKeypad`, internal fn `handleAmountKeypadKey`), add haptics.
- Rename/move test `zero-ui/.../ui/budget/NumPadTest.kt` → `zero-ui/.../ui/AmountKeypadTest.kt`.
- Modify `zero-ui/.../ui/AmountDisplay.kt` → tap-to-focus caret label, no system IME.

**zero-core (budget call sites — import update only):**
- `zero-core/.../budget/BudgetViewProvider.kt`
- `zero-core/.../budget/over/BudgetOverViewProvider.kt`
- `zero-core/.../budget/edit/BudgetEditViewProvider.kt`

**zero-core (transaction screen):**
- `zero-core/.../transactions/edit/TransactionEditViewModel.kt` — add State fields + Actions.
- `zero-core/.../transactions/edit/DefaultTransactionEditViewModel.kt` — map them.
- `zero-core/.../transactions/edit/TransactionEditViewProvider.kt` — restructure layout, host keypad + amount.
- `zero-core/.../transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt` — remove its `AmountDisplay`.
- `zero-core/.../transactions/edit/transfer/TransactionEditTransferViewProvider.kt` — remove its `AmountDisplay`.

**app (e2e):**
- `app/src/androidTest/.../robots/TransactionEditRobot.kt` — enter amount via keypad.

---

## Task 1: Promote `NumPad` → shared `AmountKeypad` with haptics

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountKeypad.kt`
- Delete: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/budget/NumPad.kt`
- Create: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/AmountKeypadTest.kt`
- Delete: `zero-ui/src/test/java/com/hluhovskyi/zero/ui/budget/NumPadTest.kt`

Structural template: the current `NumPad.kt` (logic is identical; only package, names, and the added haptic call change). Keep row height `50.dp` and `SpaceEvenly` so Budget's visual layout is unchanged.

- [ ] **Step 1: Move the test, rename to the new symbols**

Create `AmountKeypadTest.kt` (package `com.hluhovskyi.zero.ui`), identical cases to `NumPadTest` but calling `handleAmountKeypadKey`:

```kotlin
package com.hluhovskyi.zero.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountKeypadTest {

    @Test
    fun `backspace on single digit emits zero`() {
        assertEquals("0", handleAmountKeypadKey("5", "⌫"))
    }

    @Test
    fun `backspace on zero emits zero`() {
        assertEquals("0", handleAmountKeypadKey("0", "⌫"))
    }

    @Test
    fun `dot is ignored when value already contains dot`() {
        assertEquals("12.5", handleAmountKeypadKey("12.5", "."))
    }

    @Test
    fun `digit replaces zero when value is zero`() {
        assertEquals("7", handleAmountKeypadKey("0", "7"))
    }

    @Test
    fun `digit is rejected when two decimal places already present`() {
        assertEquals("12.50", handleAmountKeypadKey("12.50", "3"))
    }
}
```

Then delete `zero-ui/src/test/java/com/hluhovskyi/zero/ui/budget/NumPadTest.kt`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "com.hluhovskyi.zero.ui.AmountKeypadTest"`
Expected: FAIL — `handleAmountKeypadKey` unresolved.

- [ ] **Step 3: Create `AmountKeypad.kt`**

```kotlin
package com.hluhovskyi.zero.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

internal fun handleAmountKeypadKey(value: String, key: String): String = when {
    key == "⌫" -> if (value.length <= 1) "0" else value.dropLast(1)
    key == "." -> if (value.contains(".")) value else "$value."
    else -> {
        if (value == "0") {
            key
        } else {
            val dotIndex = value.indexOf('.')
            if (dotIndex >= 0 && value.length - dotIndex - 1 >= 2) {
                value
            } else {
                "$value$key"
            }
        }
    }
}

@Composable
fun AmountKeypad(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Column(modifier = modifier.fillMaxWidth()) {
        KEYS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onChange(handleAmountKeypadKey(value, key))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == "⌫") {
                            Icon(
                                imageVector = Icons.Filled.Backspace,
                                contentDescription = stringResource(R.string.numpad_backspace_cd),
                                tint = ZeroTheme.colors.outlineVariant,
                            )
                        } else {
                            Text(
                                text = key,
                                style = TextStyle(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ZeroTheme.colors.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
```

Then delete `zero-ui/src/main/java/com/hluhovskyi/zero/ui/budget/NumPad.kt`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :zero-ui:testDebugUnitTest --tests "com.hluhovskyi.zero.ui.AmountKeypadTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountKeypad.kt \
        zero-ui/src/test/java/com/hluhovskyi/zero/ui/AmountKeypadTest.kt
git add -A zero-ui/src/main/java/com/hluhovskyi/zero/ui/budget/NumPad.kt \
        zero-ui/src/test/java/com/hluhovskyi/zero/ui/budget/NumPadTest.kt
git commit -m "feat(ui): shared AmountKeypad with native-style haptics (was budget NumPad)"
```

---

## Task 2: Point Budget call sites at `AmountKeypad`

**Files:**
- Modify: `zero-core/.../budget/BudgetViewProvider.kt` (import line ~68, call ~285)
- Modify: `zero-core/.../budget/over/BudgetOverViewProvider.kt` (import ~53, call ~562)
- Modify: `zero-core/.../budget/edit/BudgetEditViewProvider.kt` (import line 40, call line 185)

Pure rename: `import com.hluhovskyi.zero.ui.budget.NumPad` → `import com.hluhovskyi.zero.ui.AmountKeypad`, and each `NumPad(` call site → `AmountKeypad(`. Arguments are unchanged (`value`, `onChange`, `modifier`).

- [ ] **Step 1: Update all three imports and call sites** (3 files, 6 edits).

- [ ] **Step 2: Compile zero-core**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/budget
git commit -m "refactor(budget): use shared AmountKeypad"
```

---

## Task 3: Make `AmountDisplay` a tap-to-focus caret label (no system IME)

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountDisplay.kt`

The amount is now edited by the keypad, not typed. `AmountDisplay` becomes a read-only, tappable label showing the formatted amount with a blinking caret when `active`. It is the **only** caller-facing amount field, used by the parent `TransactionEditView`. New signature drops `focusRequester`/`onAmountChange`, adds `active` + `onClick`. Keep the `testTag("TransactionEdit.amountField")` for robots.

Note: confirm `AmountDisplay` has no callers outside the two transaction view providers being refactored:
`grep -rn "AmountDisplay(" --include=*.kt . | grep -v build/`. Expected: only `common/TransactionEditExpenseIncomeViewProvider.kt` and `transfer/TransactionEditTransferViewProvider.kt` (both removed in Task 5) — i.e. after this plan only the parent calls it.

- [ ] **Step 1: Rewrite `AmountDisplay`**

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.ZeroTheme

@Composable
fun AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onCurrencyClick: (() -> Unit)? = null,
) {
    val empty = amount.isEmpty() || amount == "0"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 3.sp,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            // Currency pinned to left — chip style when clickable, plain text otherwise
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                if (onCurrencyClick != null) {
                    Row(
                        modifier = Modifier
                            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(12.dp))
                            .clickable(onClick = onCurrencyClick)
                            .padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = currencySymbol,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZeroTheme.colors.primaryContainer,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ZeroTheme.colors.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = currencySymbol,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    )
                }
            }

            // Amount right-aligned, tap to (re)open the keypad
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 70.dp)
                    .clickable(onClick = onClick)
                    .testTag("TransactionEdit.amountField"),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (empty) stringResource(R.string.amount_display_placeholder) else amount,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (empty) {
                        MaterialTheme.colors.primary.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colors.primary
                    },
                    textAlign = TextAlign.Right,
                )
                if (active) {
                    BlinkingCaret()
                }
            }
        }
    }
}

@Composable
private fun BlinkingCaret() {
    val transition = rememberInfiniteTransition(label = "caret")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1100
                1f at 0
                1f at 549
                0f at 550
                0f at 1100
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "caret-alpha",
    )
    Spacer(Modifier.width(3.dp))
    Box(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .height(44.dp)
            .width(3.dp)
            .graphicsLayer { this.alpha = alpha }
            .alpha(alpha)
            .background(MaterialTheme.colors.primary, RoundedCornerShape(2.dp)),
    )
}
```

(`amount` here is the raw `state.amount` string; thousands grouping is not introduced — keep parity with today's display which showed the raw typed string.)

- [ ] **Step 2: Compile zero-ui**

Run: `./gradlew :zero-ui:compileDebugKotlin 2>&1 | tail -15`
Expected: BUILD SUCCESSFUL (the two old callers still reference the old signature and live in zero-core, so zero-ui compiles independently).

- [ ] **Step 3: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountDisplay.kt
git commit -m "feat(ui): AmountDisplay is a tap-to-focus caret label for the inline keypad"
```

---

## Task 4: Lift amount + currency into the parent `TransactionEditViewModel`

**Files:**
- Modify: `zero-core/.../transactions/edit/TransactionEditViewModel.kt`
- Modify: `zero-core/.../transactions/edit/DefaultTransactionEditViewModel.kt`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModelTest.kt` (create if absent; if a test for this VM already exists, add cases to it)

The parent VM is backed by the same `TransactionEditUseCase` as the children, so it can surface the amount/currency the keypad needs. `currencySymbol`: Expense/Income → `selectedCurrency?.currencySymbol ?: ""`, Transfer → `sourceCurrencySymbol`. `canPickCurrency`: true for Expense/Income, false for Transfer.

- [ ] **Step 1: Extend the `TransactionEditViewModel` interface**

In `TransactionEditViewModel.kt`, add to `Action`:

```kotlin
        data class ChangeAmount(val amount: String) : Action
        object PickCurrency : Action
```

Add to `State` (defaults keep existing call sites valid):

```kotlin
        val amount: String = "",
        val currencySymbol: String = "",
        val canPickCurrency: Boolean = false,
```

- [ ] **Step 2: Map them in `DefaultTransactionEditViewModel`**

In the `.map { state -> ... }` block, add these to the `TransactionEditViewModel.State(...)` construction:

```kotlin
                amount = state.amount,
                currencySymbol = when (state) {
                    is TransactionEditUseCase.State.Expense -> state.selectedCurrency?.currencySymbol ?: ""
                    is TransactionEditUseCase.State.Income -> state.selectedCurrency?.currencySymbol ?: ""
                    is TransactionEditUseCase.State.Transfer -> state.sourceCurrencySymbol
                },
                canPickCurrency = state !is TransactionEditUseCase.State.Transfer,
```

`state.amount` resolves on every variant (each declares `val amount`). In `perform`, add to the `when (action)`:

```kotlin
            is TransactionEditViewModel.Action.ChangeAmount ->
                TransactionEditUseCase.Action.ChangeAmount(action.amount)

            is TransactionEditViewModel.Action.PickCurrency ->
                TransactionEditUseCase.Action.ShowAllCurrencies
```

- [ ] **Step 3: Add a mapping test**

Model after the existing default-viewmodel tests in `zero-core/src/test/.../transactions/edit/` (use `runTest` + a fake/mocked `TransactionEditUseCase` exposing a `MutableStateFlow`, following the nearest existing `Default*ViewModelTest`). Assert that an `Expense` use-case state with `amount = "12.50"` and a `selectedCurrency` whose `currencySymbol = "$"` maps to `state.amount == "12.50"`, `state.currencySymbol == "$"`, `state.canPickCurrency == true`; and that a `Transfer` state maps `canPickCurrency == false` with `currencySymbol == sourceCurrencySymbol`.

If no `Default*ViewModelTest` precedent exists in that package, skip the unit test and rely on the e2e + UI inspector verification in Task 8 (do not invent a test harness).

- [ ] **Step 4: Compile + run the test**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.transactions.edit.DefaultTransactionEditViewModelTest" 2>&1 | tail -20`
Expected: PASS (or, if Step 3 was skipped, run `./gradlew :zero-core:compileDebugKotlin` → BUILD SUCCESSFUL).

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt
git add -A zero-core/src/test/java/com/hluhovskyi/zero/transactions/edit/ 2>/dev/null || true
git commit -m "feat(transactions): expose amount/currency on parent edit ViewModel for inline keypad"
```

---

## Task 5: Remove the per-child `AmountDisplay` (it moves to the parent)

**Files:**
- Modify: `zero-core/.../transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt`
- Modify: `zero-core/.../transactions/edit/transfer/TransactionEditTransferViewProvider.kt`

Both children currently render their own `AmountDisplay` (plus focus plumbing). The parent now owns it, so delete it from the children and drop the now-unused `FocusRequester`/`focusTarget`/`shouldFocus` autofocus machinery tied to the amount field.

- [ ] **Step 1: Expense/Income view provider**

In `TransactionEditExpenseIncomeViewProvider.kt`:
- Delete the `AmountDisplay(...)` block (lines ~62-76).
- Delete the `focusRequester` + `if (shouldFocus) LaunchedEffect { focusRequester.requestFocus() }` and the `.then(if (!shouldFocus) Modifier.focusTarget() else Modifier)` modifier; the root `Column` keeps only `Modifier.padding(horizontal = 24.dp)`.
- Remove now-unused params/imports (`shouldFocus`, `isNewTransaction`, `FocusRequester`, `focusTarget`, `AmountDisplay`, `LaunchedEffect`, `remember`). Keep `isNewTransaction` plumbing in the class only if still referenced elsewhere; otherwise drop it from the constructor and its `View()` call.
- The first child becomes `CategoryScrollRow` — adjust its top padding so the form doesn't butt against the pinned amount (use `top = 8.dp`).

- [ ] **Step 2: Transfer view provider**

In `TransactionEditTransferViewProvider.kt`:
- Delete the source `AmountDisplay(...)` block (lines ~86-97) and the same focus machinery as above.
- The first child becomes `RateModePill`; keep its existing padding.

- [ ] **Step 3: Verify both childrens' constructors still match their components**

If `isNewTransaction`/`shouldFocus` was removed from a view provider constructor, update the corresponding `*Component` provider that constructs it (`TransactionEditExpenseIncomeComponent` / `TransactionEditTransferComponent`). Grep: `grep -rn "isNewTransaction\|shouldFocus" zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/`. Update call sites so they compile. (If the flag is still needed for something else, leave it; only the amount-autofocus usage is being removed.)

- [ ] **Step 4: Compile**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (the parent in Task 6 will supply the amount UI; until Task 6 the screen has no amount field, which is fine to compile).

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt \
        zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/transfer/TransactionEditTransferViewProvider.kt
git add -A zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/
git commit -m "refactor(transactions): remove per-child AmountDisplay (lifting to parent)"
```

---

## Task 6: Restructure `TransactionEditView` — pinned amount, scrolling form, docked FAB+keypad

**Files:**
- Modify: `zero-core/.../transactions/edit/TransactionEditViewProvider.kt`

Replace the `Box { LazyColumn(fillMaxSize) ; ZeroFab(BottomEnd) }` with a vertical `Column`:
1. **Pinned top cluster** (not scrolling): `ModalHeader` + `SegmentedToggle` + `AmountDisplay`.
2. **Scrolling middle** (`LazyColumn`, `Modifier.weight(1f)`): the type-specific component (`expenseIncomeComponent`/`transferComponent` via `AttachWithView()`) + the Notes card.
3. **Pinned bottom cluster** (not scrolling): the `ZeroFab` (end-aligned) directly above an `AnimatedVisibility(keypadVisible) { AmountKeypad(...) }`. Apply `Modifier.navigationBarsPadding()` to this cluster so the keypad clears the system nav bar on the full-screen (New) case.

Keypad visibility:
- `var keypadVisible by rememberSaveable { mutableStateOf(state.headerMode is HeaderMode.New) }` — auto-shown for New, hidden for Edit/Duplicate until the amount is tapped. (Seed it once; `rememberSaveable` survives config change.)
- `AmountDisplay(active = keypadVisible, onClick = { keypadVisible = true }, ...)`.
- `BackHandler(enabled = keypadVisible) { keypadVisible = false }` — back dismisses the keypad first (see memory: inline overlays need BackHandler).

Wire the keypad and currency to the parent VM:
- `AmountKeypad(value = state.amount, onChange = { viewModel.perform(TransactionEditViewModel.Action.ChangeAmount(it)) })`
- `AmountDisplay(onCurrencyClick = if (state.canPickCurrency) ({ viewModel.perform(TransactionEditViewModel.Action.PickCurrency) }) else null, currencySymbol = state.currencySymbol, amount = state.amount, label = stringResource(R.string.transaction_edit_amount_display_label).uppercase())`

- [ ] **Step 1: Rewrite the `TransactionEditView` composable body**

Skeleton (keep the existing `ModalHeader`/dropdown-menu block verbatim — only its container changes from a `LazyColumn item {}` to a direct call in the pinned cluster; keep the existing Notes `item {}` content, moved into the scrolling `LazyColumn`):

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colors.background),
) {
    // ── Pinned: header ──
    ModalHeader( /* unchanged title/subtitle/onClose/trailingContent block */ )

    // ── Pinned: type toggle ──
    SegmentedToggle(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp),
        items = state.transactionTypes,
        selectedItem = state.selectedTransactionType,
        onItemSelected = { type ->
            viewModel.perform(TransactionEditViewModel.Action.ChangeTransactionType(type))
        },
        labelMapping = { type -> when (type) {
            TransactionEditType.EXPENSE -> labelExpense
            TransactionEditType.INCOME -> labelIncome
            TransactionEditType.TRANSFER -> labelTransfer
        } },
    )

    // ── Pinned: amount ──
    AmountDisplay(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 8.dp),
        label = stringResource(R.string.transaction_edit_amount_display_label).uppercase(),
        amount = state.amount,
        currencySymbol = state.currencySymbol,
        active = keypadVisible,
        onClick = { keypadVisible = true },
        onCurrencyClick = if (state.canPickCurrency) {
            { viewModel.perform(TransactionEditViewModel.Action.PickCurrency) }
        } else {
            null
        },
    )

    // ── Scrolling: type-specific form + notes ──
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            when (state.selectedTransactionType) {
                TransactionEditType.EXPENSE,
                TransactionEditType.INCOME,
                -> expenseIncomeComponent.AttachWithView()
                TransactionEditType.TRANSFER -> transferComponent.AttachWithView()
            }
        }
        item { /* unchanged Notes card Column block */ }
    }

    // ── Pinned: FAB on top of keypad ──
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.End,
    ) {
        ZeroFab(
            modifier = Modifier.padding(end = 16.dp, bottom = 12.dp),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
            icon = Icons.Filled.Check,
            contentDescription = stringResource(R.string.transaction_edit_save),
            expanded = true,
            text = stringResource(R.string.transaction_edit_save),
        )
        AnimatedVisibility(visible = keypadVisible) {
            AmountKeypad(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ZeroTheme.colors.surfaceContainerLow)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                value = state.amount,
                onChange = { viewModel.perform(TransactionEditViewModel.Action.ChangeAmount(it)) },
            )
        }
    }
}
```

Add the keypad-visibility state + BackHandler near the top of the composable (after `state` is collected):

```kotlin
var keypadVisible by rememberSaveable {
    mutableStateOf(state.headerMode is TransactionEditViewModel.HeaderMode.New)
}
BackHandler(enabled = keypadVisible) { keypadVisible = false }
```

New imports to add: `androidx.activity.compose.BackHandler`, `androidx.compose.animation.AnimatedVisibility`, `androidx.compose.foundation.layout.navigationBarsPadding`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.mutableStateOf`/`getValue`/`setValue` (already partially present), `com.hluhovskyi.zero.ui.AmountDisplay`, `com.hluhovskyi.zero.ui.AmountKeypad`. Remove now-unused `BasicTextField`-for-amount-only imports if they become unused (Notes still uses `BasicTextField`, so keep it).

- [ ] **Step 2: Compile**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt
git commit -m "feat(transactions): dock inline AmountKeypad with FAB pinned on top, amount pinned above scrolling form"
```

---

## Task 7: Update the e2e robot to enter amount via the keypad

**Files:**
- Modify: `app/src/androidTest/.../robots/TransactionEditRobot.kt`

`fillExpense` currently does `onNodeWithTag("TransactionEdit.amountField").performClick().performTextReplacement(amount)`. There is no longer a text field. Tap the amount to ensure the keypad is open, then tap each character on the keypad (pattern lifted from `BudgetInlineNumpadRobot.typeDigits`/`tapKey`).

- [ ] **Step 1: Replace amount entry in `fillExpense`**

```kotlin
    fun fillExpense(amount: String, category: String, account: String): TransactionEditRobot {
        composeRule.apply {
            onNodeWithTag("TransactionEdit.amountField").performClick() // ensure keypad visible
            amount.forEach { ch ->
                onAllNodesWithText(ch.toString()).filter(hasClickAction()).onFirst().performClick()
            }
            onNodeWithText(category).performClick()
            onNodeWithText("ACCOUNT").performClick()
            onAllNodesWithText(account).onLast().performClick()
        }
        return this
    }
```

Add imports: `androidx.compose.ui.test.filter`, `androidx.compose.ui.test.hasClickAction`, `androidx.compose.ui.test.onFirst`. Drop the now-unused `performTextReplacement` import. (`amount` strings in the e2e seed presets are plain like `"12.50"`, so digit/`.` taps suffice.)

- [ ] **Step 2: Compile the androidTest sources**

Run: `./gradlew :app:compileDebugAndroidTestKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionEditRobot.kt
git commit -m "test(e2e): enter transaction amount via inline keypad"
```

---

## Task 8: Verification

- [ ] **Step 1: Spotless**

Run: `./gradlew spotlessApply`
Then `git add -A && git commit -m "style: spotlessApply"` if it changed anything.

- [ ] **Step 2: Unit tests + lint (batched, fail-fast)**

Run: `./gradlew testDebugUnitTest lintDebug 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL. Fix any failure before proceeding.

- [ ] **Step 3: Acquire emulator + install**

Run: `./scripts/emulator/acquire` then `./scripts/install-app.sh`.

- [ ] **Step 4: UI inspection via `android-ui-inspector`**

Verify on device (use `./scripts/ui/open-screen.sh` to reach New Transaction, and open an existing transaction for the Edit case):
- **New Transaction (full screen):** keypad is visible on open (autofocus), docked at the bottom, FAB sits directly above it, amount + header + toggle are pinned, the category/date/account/notes area scrolls beneath. Tapping digits updates the pinned amount. Backspace works. Haptic fires on tap (device-dependent; confirm no crash).
- **Edit existing transaction (bottom sheet):** keypad is hidden on open; tapping the amount reveals it; system back hides the keypad (not the sheet) while it is visible.
- **Budget Set/Edit/Over:** keypad still renders and edits correctly (regression check of the shared component).

Capture bounds with `./scripts/ui/dump-ui.sh` to confirm the keypad is non-scrolling and the FAB rect is immediately above the keypad rect.

- [ ] **Step 5: Final commit if any inspector-driven tweak was needed**

---

## Self-Review (checklist run by plan author)

- **#1 no calculator signs** → Task 1 keeps the digits-only `KEYS` list. ✅
- **#2 native haptics** → Task 1 `performHapticFeedback(KEYBOARD_TAP)` on every key. ✅
- **#3 keypad non-scroll, content scrolls** → Task 6 pinned bottom cluster + `LazyColumn(weight 1f)`. ✅
- **#4 FAB on top of keypad** → Task 6 `Column { ZeroFab; AmountKeypad }`. ✅
- **#5 reused with budget / moved to ui component** → Tasks 1-2 shared `AmountKeypad` in `zero-ui`, budget call sites updated. ✅
- **#6 connected to amount entry** → Tasks 4+6 `ChangeAmount` → `TransactionEditUseCase`. ✅
- **#7 appears on tapping amount when editing** → Task 6 `keypadVisible` seeded by `HeaderMode.New`, set true on amount tap. ✅
- **Type consistency:** `handleAmountKeypadKey`, `AmountKeypad`, `AmountDisplay(active,onClick)`, `Action.ChangeAmount/PickCurrency`, `State.amount/currencySymbol/canPickCurrency` are used identically across tasks. ✅
- **Placeholder scan:** no TBD/TODO; the one conditional (VM test only if a `Default*ViewModelTest` precedent exists) has an explicit fallback. ✅
