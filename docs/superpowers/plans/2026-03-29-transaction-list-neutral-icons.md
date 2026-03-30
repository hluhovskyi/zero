# Transaction List — Neutral Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace category-colored circular icon containers with neutral squircle containers and tinted-neutral icons, and fix expense/income amount colors.

**Architecture:** All visual changes are confined to `TransactionExpenseView.kt`. The `categoryColor` parameter is removed from the public API of `TransactionExpenseView`, `TransactionIncomeView`, and the private `TransactionView`. The call sites in `TransactionViewProvider.kt` are updated to drop the removed argument.

**Tech Stack:** Jetpack Compose, Kotlin, Material Design 3 color tokens

---

## File Map

| File | Change |
|------|--------|
| `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt` | Remove `categoryColor` param, squircle container, neutral icon tint, fix amount colors |
| `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` | Remove `categoryColor` argument from `TransactionExpenseView` and `TransactionIncomeView` call sites |

---

### Task 1: Update `TransactionView` — squircle container + neutral icon tint

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt`

- [ ] **Step 1: Remove `categoryColor` from `TransactionView` signature and update icon container**

Replace the entire `TransactionView` function (lines 73–136) with:

```kotlin
@Composable
fun TransactionView(
    modifier: Modifier,
    categoryName: String,
    amountColor: ColorValue,
    amount: String,
    accountName: String,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { iconView ->
            Box(
                modifier = Modifier
                    .background(Color(0xFFEFEDF2), shape = RoundedCornerShape(percent = 30))
                    .size(40.dp)
                    .padding(8.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides Color(0xFF44464F)) {
                    iconView()
                }
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = categoryName,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 18.sp,
                    color = amountColor.toCompose(),
                    text = amount
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    accountIcon?.invoke()
                    Text(
                        text = accountName,
                        modifier = Modifier.weight(1f),
                    )
                }
                convertedAmount?.let {
                    Text(
                        fontSize = 14.sp,
                        text = convertedAmount,
                    )
                }
            }
        }
    }
}
```

Update imports at the top of the file — remove `CircleShape`, add `RoundedCornerShape` and `LocalContentColor`:

```kotlin
package com.hluhovskyi.zero.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.toCompose
```

- [ ] **Step 2: Remove `categoryColor` from `TransactionExpenseView` and fix expense amount color**

Replace `TransactionExpenseView` (lines 23–46):

```kotlin
@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amountColor = ColorValue(Color(0xFFBA1A1A).value),
        amount = "-$amount",
        accountName = accountName,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}
```

- [ ] **Step 3: Remove `categoryColor` from `TransactionIncomeView` and fix income amount color**

Replace `TransactionIncomeView` (lines 48–71):

```kotlin
@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amountColor = ColorValue(Color(0xFF006C4A).value),
        amount = "+$amount",
        accountName = accountName,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}
```

- [ ] **Step 4: Build to confirm no compile errors**

```bash
./gradlew :zero-ui:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt
git commit -m "feat: neutral squircle icon container, tinted icons, fix amount colors"
```

---

### Task 2: Update call sites in `TransactionViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

- [ ] **Step 1: Remove `categoryColor` argument from `TransactionExpenseView` call (line 95)**

Current call at line 93–114:
```kotlin
is TransactionViewModel.Item.Transaction.Expense ->
    TransactionExpenseView(
        modifier = transactionModifier,
        categoryColor = transaction.categoryColor,  // remove this line
        categoryName = transaction.categoryName,
        ...
    )
```

Updated call:
```kotlin
is TransactionViewModel.Item.Transaction.Expense ->
    TransactionExpenseView(
        modifier = transactionModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol
        ),
        accountName = transaction.accountName,
        accountIcon = transaction.accountIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier
                .alpha(ContentAlpha.medium)
                .padding(end = 6.dp)
                .size(20.dp),
        ),
        convertedAmount = transaction.conversion.format(amountFormatter),
        icon = transaction.categoryIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier.size(24.dp),
        ),
    )
```

- [ ] **Step 2: Remove `categoryColor` argument from `TransactionIncomeView` call (line 118)**

Updated call:
```kotlin
is TransactionViewModel.Item.Transaction.Income -> {
    TransactionIncomeView(
        modifier = transactionModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol,
        ),
        accountName = transaction.accountName,
        convertedAmount = transaction.conversion.format(amountFormatter),
        icon = transaction.categoryIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier.size(24.dp),
        ),
    )
}
```

- [ ] **Step 3: Build full project to confirm no compile errors**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Visual verification in emulator**

Run the app and navigate to the Transactions screen. Confirm:
- Icon containers are squircle-shaped (rounded rectangle, not circle)
- Icon container background is light neutral gray (`#EFEDF2`)
- Icons are rendered in neutral gray tint (`#44464F`)
- Expense amounts show in red (`#BA1A1A`)
- Income amounts show in green (`#006C4A`)
- No colored circle backgrounds remain

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "feat: remove categoryColor from transaction item call sites"
```
