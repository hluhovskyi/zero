# Keyboard Dismiss Behaviour Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the soft keyboard dismiss when tapping outside the amount field or opening any picker, and ensure auto-focus fires only once per screen open.

**Architecture:** Three-part change — (1) shared UI components (`SelectorCard`, `DatePickerCard`) self-clear focus on activation; (2) each edit screen's outer `Box` gets a background tap handler that clears focus on taps that children don't consume; (3) a one-time auto-focus guard prevents the keyboard from re-appearing after the user dismisses it.

**Tech Stack:** Kotlin, Jetpack Compose, `LocalFocusManager`, `rememberSaveableStateHolder`

**Spec:** `docs/superpowers/specs/2026-04-11-keyboard-dismiss-design.md`

---

## File Map

| File | Change |
|------|--------|
| `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SelectorCard.kt` | Add `LocalFocusManager.current.clearFocus()` before click action |
| `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DatePickerCard.kt` | Add `LocalFocusManager.current.clearFocus()` before dialog show |
| `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt` | Background tap handler on outer `Box` + `remember` auto-focus guard |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt` | Background tap handler + `rememberSaveableStateHolder` wrapping expense/income slot |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt` | Replace `LaunchedEffect(Unit)` with `rememberSaveable`-guarded version |

---

### Task 1: SelectorCard — clear focus on activation

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/SelectorCard.kt`

- [ ] **Open the file and locate the `clickable` lambda (line 53)**

The current lambda is:
```kotlin
.clickable { if (onClick != null) onClick() else expanded = true }
```

- [ ] **Add `LocalFocusManager` import and call `clearFocus()` before the existing action**

Replace the `clickable` lambda and add the import. Full updated file:

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> SelectorCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    items: List<T>,
    nameMapping: (T) -> String,
    onItemSelected: (T) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                .clickable {
                    focusManager.clearFocus()
                    if (onClick != null) onClick() else expanded = true
                }
                .padding(16.dp),
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = OnSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                ) {
                    Text(text = nameMapping(item))
                }
            }
        }
    }
}
```

- [ ] **Build to confirm no compile errors**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-ui:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/SelectorCard.kt
git commit -m "feat: clear keyboard focus when SelectorCard is activated"
```

---

### Task 2: DatePickerCard — clear focus on activation

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/DatePickerCard.kt`

- [ ] **Locate the `clickable` lambda (line 48) in DatePickerCard**

Current lambda opens `DatePickerDialog` directly.

- [ ] **Add `LocalFocusManager` and call `clearFocus()` before showing the dialog**

Full updated file:

```kotlin
package com.hluhovskyi.zero.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun DatePickerCard(
    modifier: Modifier = Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val formattedDate = remember(date) {
        date.toJavaLocalDateTime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }

    Column(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable {
                focusManager.clearFocus()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateSelected(LocalDateTime(year, month + 1, dayOfMonth, 0, 0, 0))
                    },
                    date.year,
                    date.monthNumber - 1,
                    date.dayOfMonth,
                ).show()
            }
            .padding(16.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = formattedDate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Build to confirm no compile errors**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-ui:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/DatePickerCard.kt
git commit -m "feat: clear keyboard focus when DatePickerCard is activated"
```

---

### Task 3: AccountEditViewProvider — background tap handler + one-time auto-focus guard

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`

- [ ] **Locate `AccountEditView` composable (line 57)**

Current structure: outer `Box(modifier = Modifier.fillMaxSize())` at line 68, `LaunchedEffect(Unit) { focusRequester.requestFocus() }` at lines 64–66.

- [ ] **Apply two changes: wrap `LaunchedEffect` with a guard, add background tap handler to the outer `Box`**

New imports needed: `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.ui.platform.LocalFocusManager`.

Full updated `AccountEditView` function and `AccountEditViewProvider` class (replace from line 42 to end of file):

```kotlin
internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel,
    private val onClose: OnCloseHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel,
            onClose = onClose,
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel,
    onClose: OnCloseHandler,
) {
    val state by viewModel.state.collectAsState(initial = AccountEditViewModel.State())
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var hasAutoFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasAutoFocused) {
            focusRequester.requestFocus()
            hasAutoFocused = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            },
    ) {
        Column {
            ModalHeader(
                title = "New Account",
                onClose = { onClose.onClose() },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AmountDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    amount = state.balance,
                    currencySymbol = state.selectedCurrency?.symbol ?: "",
                    focusRequester = focusRequester,
                    onAmountChange = { balance ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeBalance(balance))
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Currency",
                        value = state.selectedCurrency?.name ?: "",
                        items = emptyList<String>(),
                        nameMapping = { it },
                        onItemSelected = {},
                        onClick = { viewModel.perform(AccountEditViewModel.Action.OpenCurrencyPicker) },
                    )
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Account Type",
                        value = state.category.displayName,
                        items = AccountCategory.entries,
                        nameMapping = { it.displayName },
                        onItemSelected = { category ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
                        },
                    )
                }

                InputCard(
                    label = "Account Name",
                    value = state.name,
                    placeholder = "e.g. Everyday Checking",
                    onValueChange = { name ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
                    },
                )

                InputCard(
                    modifier = Modifier.padding(bottom = 96.dp),
                    label = "Details",
                    value = state.details,
                    placeholder = "e.g. Chase Bank",
                    onValueChange = { details ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeDetails(details))
                    },
                )
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = { Icon(Icons.Filled.Check, contentDescription = "Save account") },
            text = { Text("Save") },
            onClick = { viewModel.perform(AccountEditViewModel.Action.Save) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}
```

Add these imports to the top of the file (alongside existing ones):
```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
```

- [ ] **Build to confirm no compile errors**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-core:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt
git commit -m "feat: dismiss keyboard on outside tap and guard auto-focus in account edit"
```

---

### Task 4: TransactionEditViewProvider — background tap handler + SaveableStateHolder

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

- [ ] **Locate `TransactionEditView` composable (line 48)**

Current outer container is `Box(modifier = Modifier.fillMaxSize().background(...))`. The expense/income slot at line 92 calls `expenseIncomeComponent.AttachWithView()` directly.

- [ ] **Apply two changes: add background tap handler to the outer `Box`, wrap expense/income slot with `SaveableStateProvider`**

New imports needed: `androidx.compose.foundation.gestures.detectTapGestures`, `androidx.compose.foundation.saveableStateOf`, `androidx.compose.runtime.saveable.rememberSaveableStateHolder`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.ui.platform.LocalFocusManager`.

Full updated `TransactionEditView` function (replace from line 48 to end of file):

```kotlin
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseIncomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>,
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())
    val focusManager = LocalFocusManager.current
    val stateHolder = rememberSaveableStateHolder()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                ModalHeader(
                    title = "New Transaction",
                    onClose = { viewModel.perform(TransactionEditViewModel.Action.Discard) },
                )
            }
            item {
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
                    labelMapping = { type ->
                        when (type) {
                            TransactionEditType.EXPENSE -> "Expense"
                            TransactionEditType.INCOME -> "Income"
                            TransactionEditType.TRANSFER -> "Transfer"
                        }
                    },
                )
            }
            item {
                when (state.selectedTransactionType) {
                    TransactionEditType.EXPENSE,
                    TransactionEditType.INCOME,
                    -> stateHolder.SaveableStateProvider("expense_income") {
                        expenseIncomeComponent.AttachWithView()
                    }
                    TransactionEditType.TRANSFER -> transferComponent.AttachWithView()
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                )
            },
            text = {
                Text(text = "Save Transaction")
            },
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}
```

Add these imports to the top of the file:
```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
```

- [ ] **Build to confirm no compile errors**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew :zero-core:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt
git commit -m "feat: dismiss keyboard on outside tap and preserve expense/income state across type switch"
```

---

### Task 5: TransactionEditExpenseIncomeViewProvider — one-time auto-focus guard

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt`

- [ ] **Locate the `LaunchedEffect(Unit)` block (lines 45–47)**

Current code:
```kotlin
LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}
```

- [ ] **Replace with a `rememberSaveable`-guarded version**

The `rememberSaveable` here is stored inside the `SaveableStateProvider("expense_income")` context added in Task 4, so its value survives the composable being removed and re-added when the user switches transaction types.

Replace lines 43–47 (the `val focusRequester` + `LaunchedEffect` block) with:

```kotlin
val focusRequester = remember { FocusRequester() }
var hasAutoFocused by rememberSaveable { mutableStateOf(false) }

LaunchedEffect(Unit) {
    if (!hasAutoFocused) {
        focusRequester.requestFocus()
        hasAutoFocused = true
    }
}
```

Add these imports:
```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
```

- [ ] **Build the full app to confirm everything compiles**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditExpenseIncomeViewProvider.kt
git commit -m "feat: guard auto-focus in transaction edit to fire only once per screen open"
```

---

### Task 6: Manual verification

- [ ] **Install the debug build**

```bash
cd /Users/google-mac/Projects/zero && ./gradlew installDebug
```

- [ ] **Account Edit screen — verify all three behaviours**

1. Open account edit → keyboard appears automatically (auto-focus on open)
2. Tap anywhere on the background (not on a field or selector) → keyboard dismisses
3. Dismiss the keyboard, then tap Currency selector → keyboard stays dismissed, picker opens
4. Re-open account edit → keyboard appears automatically again (fresh screen open)
5. Auto-focus fires, dismiss keyboard, tap back into amount field → keyboard appears (user-initiated tap still works)

- [ ] **Transaction Edit screen — verify all three behaviours**

1. Open transaction edit → keyboard appears automatically
2. Tap the background → keyboard dismisses
3. Dismiss keyboard, tap Account selector → keyboard stays dismissed, dropdown opens
4. Dismiss keyboard, tap Date picker → keyboard stays dismissed, date dialog opens
5. Dismiss keyboard, switch to Transfer tab, switch back to Expense tab → keyboard does NOT re-appear (auto-focus guard preserved via `SaveableStateProvider`)
6. Tap the amount field directly after dismissing → keyboard appears (user-initiated tap works)
