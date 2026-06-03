# Category Tile + Chips Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the transaction-edit category tile strip with a boxed "Category" field row (matching Date/Account) plus a one-time quick-chip shortcut row.

**Architecture:** Pure view-layer swap. A new `CategoryField` composable replaces `CategoryScrollRow`; it renders a boxed field row (leading squircle + label + value + chevron, tap → existing `ShowAllCategories` picker) and, only while no category is selected, a `LazyRow` of the top-6 ranked categories as pill chips (tap → existing `SelectCategory`). No ViewModel / UseCase / data-flow changes — `Form.ExpenseIncome.categories` (already ranked + type-filtered) and `.selectedCategory` already carry everything needed.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), `ZeroTheme` tokens, `CategoryIconView`, `ImageLoader`.

**Spec:** `docs/superpowers/specs/2026-06-02-category-tile-chips-design.md`

**Conventions:** Follow `zero-core/.../AGENTS.md`. Compose layout in this repo is verified via lint + the `android-ui-inspector`, not unit tests; the e2e `ZeroE2eTest` covers the create-with-category flow.

---

### Task 1: String resources

**Files:**
- Modify: `zero-core/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the placeholder string**

Next to `transaction_edit_category_label` (id `Category`), add:

```xml
<string name="transaction_edit_choose_category">Choose category</string>
```

- [ ] **Step 2: Remove the now-unused strip strings**

Delete these two (only referenced by the strip being removed; confirmed no other refs):

```xml
<string name="transaction_edit_all_categories">All</string>
<string name="transaction_edit_show_all_categories_description">…</string>
```

Re-verify before deleting:

Run: `grep -rn "transaction_edit_all_categories\|transaction_edit_show_all_categories_description" zero-core/src app/src`
Expected: matches only in `CategoryScrollRow.kt` (deleted in Task 3) and `strings.xml`.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/res/values/strings.xml
git commit -m "feat(transactions): add choose-category string, drop strip strings"
```

---

### Task 2: Create `CategoryField` composable

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryField.kt`

Structural analog: `SelectorCard.kt` / `DatePickerCard.kt` (boxed field row) and the old `CategoryScrollRow.kt` (icon rendering via `CategoryIconView` + `ImageLoader`, `toUi()`, theme-aware tint).

- [ ] **Step 1: Write the file**

```kotlin
package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

private const val QUICK_CHIP_COUNT = 6

/**
 * Category selector for the transaction edit screen: a boxed field row (matches
 * Date/Account; always opens the picker) plus a one-time quick-chip shortcut row that
 * shows only while nothing is picked and vanishes after the first selection.
 */
@Composable
internal fun CategoryField(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Column(modifier = modifier) {
        CategoryRow(
            imageLoader = imageLoader,
            selectedCategory = selectedCategory,
            onClick = onOpenPicker,
        )
        AnimatedVisibility(visible = selectedCategory == null) {
            QuickChipsRow(
                modifier = Modifier.padding(top = 10.dp),
                imageLoader = imageLoader,
                categories = categories.take(QUICK_CHIP_COUNT),
                onCategorySelected = onCategorySelected,
            )
        }
    }
}

@Composable
private fun CategoryRow(
    imageLoader: ImageLoader,
    selectedCategory: TransactionEditCategory?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selectedCategory != null) {
            CategoryIconView(
                colorScheme = selectedCategory.colorScheme.toUi(),
                size = 38.dp,
                contentPadding = 9.dp,
            ) { iconTint ->
                imageLoader.View(
                    modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                    image = selectedCategory.icon,
                    tint = iconTint,
                )
            }
        } else {
            CategoryIconView(
                color = ZeroTheme.colors.surfaceContainer,
                size = 38.dp,
                contentPadding = 9.dp,
            ) {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.sizeIn(maxHeight = 20.dp, maxWidth = 20.dp),
                    tint = ZeroTheme.colors.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.transaction_edit_category_label).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = selectedCategory?.name
                    ?: stringResource(R.string.transaction_edit_choose_category),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedCategory != null) {
                    ZeroTheme.colors.primary
                } else {
                    ZeroTheme.colors.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = ZeroTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickChipsRow(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    onCategorySelected: (TransactionEditCategory) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id.value }) { category ->
            QuickChip(
                imageLoader = imageLoader,
                category = category,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun QuickChip(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    onClick: () -> Unit,
) {
    val scheme = category.colorScheme.toUi()
    // Mirror CategoryIconView's tint logic so a bare icon reads as theme-coherent.
    val iconTint = if (ZeroTheme.colors.isLight) scheme.primary else scheme.background
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(ZeroTheme.colors.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        imageLoader.View(
            modifier = Modifier.sizeIn(maxHeight = 19.dp, maxWidth = 19.dp),
            image = category.icon,
            tint = iconTint,
        )
        Text(
            text = category.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = ZeroTheme.colors.onSurface,
            maxLines = 1,
        )
    }
}
```

- [ ] **Step 2: Confirm it compiles in isolation later** — it is wired in Task 3; no standalone build here.

---

### Task 3: Wire the form, delete the old strip

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/ExpenseIncomeForm.kt:46-55`
- Delete: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryScrollRow.kt`

- [ ] **Step 1: Replace the `CategoryScrollRow(...)` call** with:

```kotlin
        CategoryField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 32.dp),
            imageLoader = imageLoader,
            categories = form.categories,
            selectedCategory = form.selectedCategory,
            onCategorySelected = { perform(TransactionEditViewModel.Action.SelectCategory(it)) },
            onOpenPicker = { perform(TransactionEditViewModel.Action.ShowAllCategories) },
        )
```

(No import change needed — `CategoryField` is in the same `…edit.common` package.)

- [ ] **Step 2: Delete the old strip**

```bash
git rm zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryScrollRow.kt
```

- [ ] **Step 3: Build the module**

Run: `./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL. If `CategoryScrollRow` is still referenced, the call site in Step 1 was missed — fix it.

- [ ] **Step 4: Commit**

```bash
git add -A zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common
git commit -m "feat(transactions): category field row + one-time quick chips"
```

---

### Task 4: Update the e2e robot to select via the picker

**Why:** `fillExpense` taps a category by name. Post-redesign a name is directly tappable only when it is one of the 6 quick chips; route through the always-available picker for determinism.

**Files:**
- Modify: `app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionEditRobot.kt:17-32`

- [ ] **Step 1: Replace the single category tap** (`onNodeWithText(category).performClick()`, line 27) with opening the picker first, then selecting:

```kotlin
            // Category is now a boxed field row that opens the picker; the label
            // "CATEGORY" is the tap target. Select inside the picker for determinism
            // (the picker lists every category; quick chips only show the top 6).
            onNodeWithText("CATEGORY").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Search categories…").fetchSemanticsNodes().isNotEmpty()
            }
            onAllNodesWithText(category).onLast().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Search categories…").fetchSemanticsNodes().isEmpty()
            }
```

`onAllNodesWithText(...).onLast()` and `waitUntil` are already imported in this file.

- [ ] **Step 2: Commit**

```bash
git add app/src/androidTest/java/com/hluhovskyi/zero/robots/TransactionEditRobot.kt
git commit -m "test(e2e): select category via picker after redesign"
```

---

### Task 5: Verify

- [ ] **Step 1: Lint + unit gates (single invocation)**

Run: `./gradlew testDebugUnitTest lintDebug 2>&1 | tail -25`
Expected: BUILD SUCCESSFUL, 0 lint errors.

- [ ] **Step 2: e2e create-with-category flow**

Acquire the emulator (`./scripts/emulator/acquire`), then run `ZeroE2eTest` (the create flow that calls `fillExpense(category = "Food")`) per the project's instrumentation runner. Expected: PASS — "Food" is selected through the picker and the saved transaction shows it.

- [ ] **Step 3: UI inspector**

Invoke `zero-project:android-ui-inspector` on the New Transaction screen. Confirm:
- Empty state: boxed "CATEGORY" row with grid placeholder + "Choose category", quick-chip row below.
- Tap a chip → chips disappear, row shows the squircle + name in primary color.
- Tap the row → picker opens; pick another → row updates.
- Edit an existing transaction → no chip row (category already selected).

- [ ] **Step 4: Final commit if any inspector-driven tweaks were made.**

---

## Self-Review

- **Spec coverage:** boxed field row (Task 2 `CategoryRow`), one-time quick chips (Task 2 `QuickChipsRow`, visibility gated on `selectedCategory == null`), tap-row-opens-picker (`onOpenPicker → ShowAllCategories`), chip-selects (`onCategorySelected → SelectCategory`), no "All" chip (chips come from `categories.take(6)`, no synthetic All), strings (Task 1), wiring + old-strip removal (Task 3), e2e (Task 4) — all covered. Picker bottom sheet intentionally untouched (out of scope).
- **Type consistency:** `CategoryField(categories, selectedCategory, onCategorySelected, onOpenPicker)` matches the Task 3 call site exactly; `form.categories: List<TransactionEditCategory>` and `form.selectedCategory: TransactionEditCategory?` match `TransactionEditViewModel.Form.ExpenseIncome`; `Action.SelectCategory` / `Action.ShowAllCategories` exist.
- **Placeholders:** none — the strings.xml `…description` value is the existing literal; copy it as-is before deleting.
