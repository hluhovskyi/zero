# Account Edit/Create Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the account create/edit page from a form with mismatched labels and plain selectors into a clean fullscreen form matching the "The Private Vault" design system — balance with dynamic label + tappable currency chip, inline-labeled cards, icon tile + type selector row, Cash-aware detail field, and FAB save.

**Architecture:** Three-file change: (1) `AmountDisplay` in `zero-ui` gains a `label` param and renders a styled chip when `onCurrencyClick` is provided; (2) `AccountEditComponent` gains `imageLoader` in its Dependencies so the ViewProvider can render the selected icon; (3) `AccountEditViewProvider` is fully reworked to match the design layout. No new screens, no navigation changes — the page is already fullscreen.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger (Hilt-style component graph), `zero-ui` shared components, `zero-api` domain types

**Out of scope:** Color picker, icon picker redesign — use existing `IconPickerComponent` and `ColorPickerComponent` as-is with default grey `UiColorScheme`.

**Design reference:** `/tmp/design-Vf1_zIAe4o-vugpP_OsacQ/zero-design-system/project/ui_kits/zero/index.html` — `AddAccountSheet` component (line 851)

---

## File Map

| File | Change |
|------|--------|
| `zero-ui/src/main/java/com/hluhovskyi/zero/ui/AmountDisplay.kt` | ✅ DONE — `label` param + currency chip |
| `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditComponent.kt` | Add `imageLoader: ImageLoader` to `Dependencies`; pass to `viewProvider` |
| `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt` | Full layout rework — new private composables `FormCard`, `IconTile` |

---

## Task 1: AmountDisplay — label param + currency chip ✅ ALREADY DONE

Changes already applied:
- Added `label: String = "AMOUNT"` parameter (default preserves existing callers)
- When `onCurrencyClick != null`, currency renders as a rounded chip (`SurfaceContainerLow` bg, `PrimaryContainer` text, `ArrowDropDown` icon) instead of plain text

No further work needed for this task.

---

## Task 2: AccountEditComponent — add imageLoader dependency

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditComponent.kt`

`ActivityComponent` already provides `imageLoader: ImageLoader` and implements `AccountEditComponent.Dependencies`. Adding it to `Dependencies` makes it available in the component graph without any wiring in `ActivityComponent`.

- [ ] **Step 1: Add `imageLoader` to `Dependencies` and thread it to `viewProvider`**

In `AccountEditComponent.kt`, make these two edits:

```kotlin
// In interface Dependencies — add after idGenerator:
val imageLoader: ImageLoader

// In Module.viewProvider — add imageLoader param and pass it:
@Provides
@AccountEditScope
fun viewProvider(
    viewModel: AccountEditViewModel,
    onCloseHandler: OnCloseHandler,
    imageLoader: ImageLoader,          // ← add
): ViewProvider = AccountEditViewProvider(
    viewModel = viewModel,
    onClose = onCloseHandler,
    imageLoader = imageLoader,         // ← add
)
```

Also add the import at the top:
```kotlin
import com.hluhovskyi.zero.ImageLoader
```

- [ ] **Step 2: Build to verify the component graph resolves**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL (no unresolved reference errors)

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditComponent.kt
git commit -m "feat: add imageLoader to AccountEditComponent.Dependencies"
```

---

## Task 3: AccountEditViewProvider — full layout rework

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt`

**Design spec summary (from `AddAccountSheet` in index.html):**

```
ModalHeader ← keep
│
├─ BALANCE LABEL (10sp, centered, "OPENING BALANCE")
├─ [currency chip] ←→ [52sp amount input right-aligned]
│
├─ ┌────────────────────────────────┐
│  │ ACCOUNT NAME (10sp label)      │   ← FormCard
│  │ text field                     │
│  └────────────────────────────────┘
│
├─ [icon tile 64×64] [TYPE card fills rest]  ← Row
│
└─ ┌────────────────────────────────┐
   │ ACCOUNT NUMBER / TYPE (label)  │   ← FormCard, hidden for Cash
   │ text field                     │
   └────────────────────────────────┘

                         [Save FAB] ← keep, bottom-end
```

- [ ] **Step 1: Replace the entire file with the new implementation**

```kotlin
package com.hluhovskyi.zero.accounts.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.AmountDisplay
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SelectorCard
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

internal class AccountEditViewProvider(
    private val viewModel: AccountEditViewModel,
    private val onClose: OnCloseHandler,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        AccountEditView(
            viewModel = viewModel,
            onClose = onClose,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun AccountEditView(
    viewModel: AccountEditViewModel,
    onClose: OnCloseHandler,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = AccountEditViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ModalHeader(
                title = "New Account",
                onClose = { onClose.onClose() },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AmountDisplay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    label = "OPENING BALANCE",
                    amount = state.balance,
                    currencySymbol = state.selectedCurrency?.symbol ?: "",
                    focusRequester = focusRequester,
                    onAmountChange = { balance ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeBalance(balance))
                    },
                    onCurrencyClick = {
                        viewModel.perform(AccountEditViewModel.Action.OpenCurrencyPicker)
                    },
                )

                FormCard(
                    label = "Account Name",
                    value = state.name,
                    placeholder = state.category.namePlaceholder,
                    onValueChange = { name ->
                        viewModel.perform(AccountEditViewModel.Action.ChangeName(name))
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconTile(
                        modifier = Modifier.fillMaxHeight(),
                        image = state.selectedIcon,
                        imageLoader = imageLoader,
                        onClick = { viewModel.perform(AccountEditViewModel.Action.SelectIcon) },
                    )
                    SelectorCard(
                        modifier = Modifier.weight(1f),
                        label = "Type",
                        value = state.category.displayName,
                        items = AccountCategory.entries,
                        nameMapping = { it.displayName },
                        onItemSelected = { category ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeCategory(category))
                        },
                    )
                }

                if (state.category != AccountCategory.CASH) {
                    FormCard(
                        label = state.category.detailLabel,
                        value = state.details,
                        placeholder = state.category.detailPlaceholder,
                        onValueChange = { details ->
                            viewModel.perform(AccountEditViewModel.Action.ChangeDetails(details))
                        },
                    )
                }

                Spacer(modifier = Modifier.height(96.dp))
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

@Composable
private fun FormCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.5.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = OnSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun IconTile(
    modifier: Modifier = Modifier,
    image: com.hluhovskyi.zero.common.Image,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val scheme = UiColorScheme.default()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(scheme.background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        imageLoader.View(
            image = image,
            modifier = Modifier
                .align(Alignment.Center)
                .size(26.dp),
            tint = scheme.primary,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(14.dp),
            tint = scheme.primary,
        )
    }
}

private val AccountCategory.displayName: String
    get() = when (this) {
        AccountCategory.CASH -> "Cash"
        AccountCategory.BANK -> "Bank"
        AccountCategory.CREDIT_CARDS -> "Credit Cards"
        AccountCategory.DIGITAL_WALLETS -> "Digital Wallets"
        AccountCategory.CRYPTO -> "Crypto"
        AccountCategory.OTHER -> "Other"
    }

private val AccountCategory.namePlaceholder: String
    get() = when (this) {
        AccountCategory.CASH -> "e.g. Wallet"
        AccountCategory.CREDIT_CARDS -> "e.g. Amex Gold"
        AccountCategory.BANK -> "e.g. Chase Sapphire"
        AccountCategory.DIGITAL_WALLETS -> "e.g. PayPal"
        AccountCategory.CRYPTO -> "e.g. Bitcoin"
        AccountCategory.OTHER -> "e.g. Savings"
    }

private val AccountCategory.detailLabel: String
    get() = when (this) {
        AccountCategory.CASH -> ""
        AccountCategory.CREDIT_CARDS -> "Last 4 / Nickname"
        AccountCategory.BANK -> "Account Number / Type"
        AccountCategory.DIGITAL_WALLETS -> "Account Details"
        AccountCategory.CRYPTO -> "Wallet Address"
        AccountCategory.OTHER -> "Details"
    }

private val AccountCategory.detailPlaceholder: String
    get() = when (this) {
        AccountCategory.CASH -> ""
        AccountCategory.CREDIT_CARDS -> "••• 1209"
        AccountCategory.BANK -> "Checking"
        AccountCategory.DIGITAL_WALLETS -> "user@example.com"
        AccountCategory.CRYPTO -> "bc1q..."
        AccountCategory.OTHER -> ""
    }
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :zero-core:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Install and visually verify with android-ui-inspector**

```bash
./gradlew :app:installDebug
```

Then navigate to Accounts → tap FAB to open New Account.

Verify with `/android-ui-inspector`:
- Balance label reads "OPENING BALANCE" (not "AMOUNT")
- Currency symbol renders as a pill-shaped chip with a dropdown arrow
- Account Name card has the label inside the card, above the text field
- Icon tile (grey square) and Type card sit on the same row at the same height
- Detail card is visible (default category is OTHER, not Cash)
- Tapping Type until "Cash" hides the detail card
- FAB is present at bottom-right

- [ ] **Step 4: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/accounts/edit/AccountEditViewProvider.kt
git commit -m "feat: rework account edit/create page layout to match design"
```

---

## Task 4: Create PR

- [ ] **Step 1: Create PR**

```bash
gh pr create \
  --title "feat: rework account create/edit page" \
  --body "$(cat <<'EOF'
## Summary
- Fullscreen form (was already fullscreen; no navigation change)
- Balance section: dynamic label (OPENING BALANCE), currency renders as tappable chip with ArrowDropDown instead of plain text
- Account Name card: label moved inside the card (matches design system pattern)
- Icon tile + Type selector sit on the same row, tile height matches card via IntrinsicSize.Min
- Detail card hidden for Cash accounts, label/placeholder vary by account type
- Save remains as FAB (no change)
- AmountDisplay gains `label` param (default "AMOUNT" — existing callers unaffected)

## Out of scope
- Color / icon picker redesign (uses existing pickers)
- Edit mode (ViewModel only supports create today)

## Test plan
- [ ] Open Accounts → tap FAB → New Account opens fullscreen
- [ ] Balance label shows "OPENING BALANCE"
- [ ] Currency chip is tappable and opens currency picker
- [ ] Account Name card has label inside the rounded rect
- [ ] Icon tile is square, grey, tappable (opens icon picker)
- [ ] Type card cycles through all 6 types via dropdown
- [ ] Detail card hidden when Type = Cash, visible for all others
- [ ] Detail label = "Account Number / Type" for Bank, "Last 4 / Nickname" for Credit Cards
- [ ] FAB saves and navigates back
- [ ] Existing transaction edit form unaffected (AmountDisplay `label` defaults to "AMOUNT")

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- ✅ Fullscreen page — already was; no change needed
- ✅ Balance label dynamic — Task 3
- ✅ Currency as tappable chip — Task 1 (done) + Task 3
- ✅ Account Name card with label inside — Task 3 `FormCard`
- ✅ Icon tile (square, tinted, chevron) — Task 3 `IconTile`
- ✅ Type selector — Task 3 `SelectorCard` with `AccountCategory.entries`
- ✅ Detail card hidden for Cash — Task 3 conditional
- ✅ FAB save — already present, kept
- ✅ imageLoader threaded through — Task 2
- ✅ Color/icon picker out of scope — existing pickers used as-is

**Placeholder scan:** No TBDs, no "implement later", all code blocks complete.

**Type consistency:**
- `AccountEditViewModel.Action.SelectIcon` — defined in `AccountEditViewModel.kt` ✅
- `AccountEditViewModel.Action.OpenCurrencyPicker` — defined ✅
- `AccountEditViewModel.Action.ChangeCategory(category)` — defined ✅
- `UiColorScheme.default()` — defined in `zero-ui/UiColorScheme.kt` ✅
- `imageLoader.View(image, modifier, tint)` — extension in `ImageLoader.kt` ✅
- `SelectorCard` with `items: List<T>` — matches existing signature ✅
