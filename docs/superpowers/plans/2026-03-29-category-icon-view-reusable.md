# CategoryIconView Reusable Component Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the rounded-square category icon style from the transaction screen into a reusable `CategoryIconView` composable and apply it to all category-related screens, wiring up the actual category color everywhere.

**Architecture:** A new `CategoryIconView` composable lives in `zero-ui` and wraps any icon content in a `RoundedCornerShape(percent = 30)` box with a caller-supplied background color. All five files that currently inline their own `Box + CircleShape` icon containers are updated to use it. The transaction screen is also fixed to pass the actual `categoryColor` instead of the hardcoded `#DDE3FF`.

**Tech Stack:** Kotlin, Jetpack Compose, existing `ColorValue.toCompose()` extension from `zero-ui`

---

## File Map

| Action | File |
|--------|------|
| **Create** | `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt` |
| **Modify** | `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt` |
| **Modify** | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt` |
| **Modify** | `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt` |
| **Modify** | `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt` |
| **Modify** | `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt` |

---

### Task 1: Create `CategoryIconView`

**Files:**
- Create: `zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.hluhovskyi.zero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CategoryIconView(
    color: Color,
    size: Dp = 40.dp,
    contentPadding: Dp = 8.dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(color, shape = RoundedCornerShape(percent = 30))
            .size(size)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/ui/CategoryIconView.kt
git commit -m "feat: add reusable CategoryIconView composable"
```

---

### Task 2: Update `TransactionExpenseView` to use `CategoryIconView`

**Files:**
- Modify: `zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt`

The current `TransactionView` has an inline `Box` with hardcoded `Color(0xFFDDE3FF)`. Replace it with `CategoryIconView` and thread an `iconColor` parameter through.

- [ ] **Step 1: Update the file**

Replace the entire file content with:

```kotlin
package com.hluhovskyi.zero.transaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ui.CategoryIconView

@Composable
fun TransactionExpenseView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColor: Color? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amount = "-$amount",
        accountName = accountName,
        iconColor = iconColor,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionIncomeView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColor: Color? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    TransactionView(
        modifier = modifier,
        categoryName = categoryName,
        amount = "+$amount",
        accountName = accountName,
        iconColor = iconColor,
        accountIcon = accountIcon,
        convertedAmount = convertedAmount,
        icon = icon,
    )
}

@Composable
fun TransactionView(
    modifier: Modifier,
    categoryName: String,
    amount: String,
    accountName: String,
    iconColor: Color? = null,
    accountIcon: (@Composable () -> Unit)? = null,
    convertedAmount: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconColor != null) {
            CategoryIconView(color = iconColor) {
                icon()
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = categoryName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B1B1F),
                    text = amount,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                accountIcon?.invoke()
                Text(
                    text = accountName,
                    fontSize = 13.sp,
                    color = Color(0xFF44464F),
                    modifier = Modifier.weight(1f),
                )
                convertedAmount?.let {
                    Text(
                        fontSize = 12.sp,
                        color = Color(0xFF44464F),
                        text = convertedAmount,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-ui/src/main/java/com/hluhovskyi/zero/transaction/TransactionExpenseView.kt
git commit -m "refactor: use CategoryIconView in TransactionExpenseView"
```

---

### Task 3: Wire up `categoryColor` in `TransactionViewProvider`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt`

Pass `transaction.categoryColor.toCompose()` as `iconColor` to both `TransactionExpenseView` and `TransactionIncomeView`.

- [ ] **Step 1: Add import and pass `iconColor`**

Add this import to `TransactionViewProvider.kt`:
```kotlin
import com.hluhovskyi.zero.common.toCompose
```

In the `TransactionExpenseView(...)` call (around line 112), add:
```kotlin
iconColor = transaction.categoryColor.toCompose(),
```

In the `TransactionIncomeView(...)` call (around line 134), add:
```kotlin
iconColor = transaction.categoryColor.toCompose(),
```

Full updated calls:

```kotlin
is TransactionViewModel.Item.Transaction.Expense ->
    TransactionExpenseView(
        modifier = contentModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol
        ),
        accountName = transaction.accountName,
        iconColor = transaction.categoryColor.toCompose(),
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
is TransactionViewModel.Item.Transaction.Income -> {
    TransactionIncomeView(
        modifier = contentModifier,
        categoryName = transaction.categoryName,
        amount = amountFormatter.format(
            amount = transaction.amount,
            currencySymbol = transaction.currencySymbol,
        ),
        accountName = transaction.accountName,
        iconColor = transaction.categoryColor.toCompose(),
        convertedAmount = transaction.conversion.format(amountFormatter),
        icon = transaction.categoryIcon.toComposable(
            imageLoader = imageLoader,
            modifier = Modifier.size(24.dp),
        ),
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/TransactionViewProvider.kt
git commit -m "fix: use actual category color in transaction list icon"
```

---

### Task 4: Update `CategoryViewProvider` to use `CategoryIconView`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`

Replace the inline `Box + CircleShape` with `CategoryIconView`.

- [ ] **Step 1: Update imports and icon rendering**

Replace the existing `Box(...)` block (lines 58–71) with:

```kotlin
CategoryIconView(color = category.color.toCompose()) {
    imageLoader.View(
        image = category.icon,
        modifier = Modifier
            .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
            .aspectRatio(1f),
        scale = ImageLoader.Scale.Crop
    )
}
```

Add import at top of file:
```kotlin
import com.hluhovskyi.zero.ui.CategoryIconView
```

Remove now-unused imports:
- `androidx.compose.foundation.shape.CircleShape`
- `androidx.compose.foundation.background`
- `androidx.compose.foundation.layout.Box`

The `size` import remains (still used by `sizeIn`). The full updated `CategoryView` composable:

```kotlin
@Composable
private fun CategoryView(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryViewModel.State())
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(state.categories) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconView(color = category.color.toCompose()) {
                    imageLoader.View(
                        image = category.icon,
                        modifier = Modifier
                            .sizeIn(maxHeight = 24.dp, maxWidth = 24.dp)
                            .aspectRatio(1f),
                        scale = ImageLoader.Scale.Crop
                    )
                }
                Text(
                    text = category.name,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt
git commit -m "refactor: use CategoryIconView in categories list"
```

---

### Task 5: Update `CategoriesEditViewProvider` to use `CategoryIconView`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`

Replace the inline `imageLoader.View(... .background(... CircleShape) ...)` with `CategoryIconView(size=64.dp, contentPadding=12.dp)`.

- [ ] **Step 1: Update imports and icon rendering**

Replace the existing `imageLoader.View(...)` block (lines 57–63) with:

```kotlin
CategoryIconView(
    color = state.color.toCompose(),
    size = 64.dp,
    contentPadding = 12.dp,
) {
    imageLoader.View(image = state.icon)
}
```

Add import at top of file:
```kotlin
import com.hluhovskyi.zero.ui.CategoryIconView
```

Remove now-unused imports:
- `androidx.compose.foundation.shape.CircleShape`
- `androidx.compose.foundation.layout.sizeIn`

The full updated icon section inside `CategoryEditView`:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp),
    contentAlignment = Alignment.Center,
) {
    CategoryIconView(
        color = state.color.toCompose(),
        size = 64.dp,
        contentPadding = 12.dp,
    ) {
        imageLoader.View(image = state.icon)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt
git commit -m "refactor: use CategoryIconView in category edit screen"
```

---

### Task 6: Update `TransactionEditCategorySelect` to use `CategoryIconView`

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt`

Two places currently use an inline `Box + CircleShape`: the dropdown menu item and the selected item display. Replace both with `CategoryIconView`.

- [ ] **Step 1: Update imports and both icon usages**

Add import:
```kotlin
import com.hluhovskyi.zero.ui.CategoryIconView
```

Remove now-unused imports:
- `androidx.compose.foundation.shape.CircleShape`

**Menu item** (replace the `Box(...)` block, lines 43–54):
```kotlin
CategoryIconView(
    color = category.color.toCompose(),
    size = 32.dp,
    contentPadding = 6.dp,
    modifier = Modifier.padding(end = 12.dp),
) {
    imageLoader.View(
        modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
        image = category.icon
    )
}
```

**Selected item** (replace the `imageLoader.View(...)` block, lines 59–67):
```kotlin
CategoryIconView(
    color = category.color.toCompose(),
    size = 32.dp,
    contentPadding = 6.dp,
    modifier = Modifier.padding(start = 8.dp),
) {
    imageLoader.View(image = category.icon)
}
```

Full updated `TransactionEditCategorySelect`:

```kotlin
@Composable
fun TransactionEditCategorySelect(
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit
) {
    TextFieldDropdownMenu(
        modifier = modifier,
        items = categories,
        label = {
            Text(text = "Category")
        },
        nameMapping = { it.name },
        menuItem = { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconView(
                    color = category.color.toCompose(),
                    size = 32.dp,
                    contentPadding = 6.dp,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    imageLoader.View(
                        modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                        image = category.icon
                    )
                }
                Text(text = category.name)
            }
        },
        selectedItem = selectedCategory,
        selectedItemIcon = { category ->
            CategoryIconView(
                color = category.color.toCompose(),
                size = 32.dp,
                contentPadding = 6.dp,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                imageLoader.View(image = category.icon)
            }
        },
        onItemSelected = onCategorySelected
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/TransactionEditCategorySelect.kt
git commit -m "refactor: use CategoryIconView in category dropdown"
```
