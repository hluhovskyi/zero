# Implementation Plan: Category Bottom Sheet

**Spec:** `docs/superpowers/specs/2026-04-05-category-bottom-sheet-design.md`
**Branch:** `feature/category-bottom-sheet`

---

## Task 1: Create `CategoryBottomSheetGrid` composable

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryBottomSheetGrid.kt` (NEW)

Create a new composable that renders a grid of all categories for the bottom sheet content.

```kotlin
package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface

@Composable
fun CategoryBottomSheetGrid(
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
) {
    val sortedCategories = categories.sortedBy { it.name }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(sortedCategories, key = { it.id.value }) { category ->
            val isSelected = category.id == selectedCategory?.id
            CategoryGridItem(
                imageLoader = imageLoader,
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryGridItem(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            size = 48.dp,
            contentPadding = 12.dp,
            isSelected = isSelected,
        ) { iconTint ->
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                image = category.icon,
                tint = iconTint,
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = category.name,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colors.primary else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
```

**Commit:** `feat: add CategoryBottomSheetGrid composable for all-categories view`

---

## Task 2: Wrap `TransactionEditExpenseViewProvider` in `ModalBottomSheetLayout`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/expense/TransactionEditExpenseViewProvider.kt`

Modify the `TransactionEditExpenseView` composable to:

1. Add `rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)` — the sheet starts hidden
2. Add `rememberCoroutineScope()` for launching show/hide
3. Wrap the existing `Column` content in `ModalBottomSheetLayout`
4. Set `sheetContent` to `CategoryBottomSheetGrid` with the same categories, selectedCategory, and imageLoader
5. Set `sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`
6. Wire `onShowAll` in `CategoryScrollRow` to `coroutineScope.launch { sheetState.show() }`
7. In the grid's `onCategorySelected`: dispatch `SelectCategory` action AND `coroutineScope.launch { sheetState.hide() }`

The full updated file should look like:

```kotlin
package com.hluhovskyi.zero.transactions.edit.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.AmountDisplay
import com.hluhovskyi.zero.transactions.edit.common.CategoryBottomSheetGrid
import com.hluhovskyi.zero.transactions.edit.common.CategoryScrollRow
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard
import kotlinx.coroutines.launch

internal class TransactionEditExpenseViewProvider(
    private val viewModel: TransactionEditExpenseViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditExpenseView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TransactionEditExpenseView(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditExpenseViewModel.State())
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetBackgroundColor = MaterialTheme.colors.background,
        sheetContent = {
            CategoryBottomSheetGrid(
                imageLoader = imageLoader,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = { category ->
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCategory(category))
                    coroutineScope.launch { sheetState.hide() }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            AmountDisplay(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 40.dp),
                amount = state.amount,
                currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
                focusRequester = focusRequester,
                onAmountChange = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeAmount(it))
                },
                currencies = state.currencies,
                onCurrencySelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCurrency(it))
                }
            )

            CategoryScrollRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                imageLoader = imageLoader,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCategory(it))
                },
                onShowAll = {
                    coroutineScope.launch { sheetState.show() }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DatePickerCard(
                    modifier = Modifier.weight(1f),
                    label = "Date",
                    date = state.date,
                    onDateSelected = {
                        viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeDate(it))
                    }
                )
                SelectorCard(
                    modifier = Modifier.weight(1f),
                    label = "Account",
                    value = state.selectedAccount?.name ?: "",
                    items = state.accounts,
                    nameMapping = { it.name },
                    onItemSelected = {
                        viewModel.perform(TransactionEditExpenseViewModel.Action.SelectAccount(it))
                    }
                )
            }

            AnimatedVisibility(visible = state.showRate) {
                TransactionEditRateTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    rate = state.rate,
                    onValueChange = { rate ->
                        viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeRate(rate))
                    }
                )
            }
        }
    }
}
```

**Important notes for implementation:**
- `ModalBottomSheetLayout` is in `androidx.compose.material` and requires `@OptIn(ExperimentalMaterialApi::class)`
- The `sheetContent` lambda MUST NOT be empty — it must always have content (even a `Box {}` as minimum), otherwise it crashes. Our `CategoryBottomSheetGrid` is always there so this is fine.
- Remove unused imports: `Box`, `background` from the old file (if present)

**Commit:** `feat: wire category bottom sheet in expense transaction edit`

---

## Task 3: Wrap `TransactionEditIncomeViewProvider` in `ModalBottomSheetLayout`

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/income/TransactionEditIncomeViewProvider.kt`

Exact same pattern as Task 2, but for the income variant. The full updated file:

```kotlin
package com.hluhovskyi.zero.transactions.edit.income

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.common.AmountDisplay
import com.hluhovskyi.zero.transactions.edit.common.CategoryBottomSheetGrid
import com.hluhovskyi.zero.transactions.edit.common.CategoryScrollRow
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField
import com.hluhovskyi.zero.ui.DatePickerCard
import com.hluhovskyi.zero.ui.SelectorCard
import kotlinx.coroutines.launch

internal class TransactionEditIncomeViewProvider(
    private val viewModel: TransactionEditIncomeViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditIncomeView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun TransactionEditIncomeView(
    viewModel: TransactionEditIncomeViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditIncomeViewModel.State())
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetBackgroundColor = MaterialTheme.colors.background,
        sheetContent = {
            CategoryBottomSheetGrid(
                imageLoader = imageLoader,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = { category ->
                    viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCategory(category))
                    coroutineScope.launch { sheetState.hide() }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            AmountDisplay(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 40.dp),
                amount = state.amount,
                currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
                focusRequester = focusRequester,
                onAmountChange = {
                    viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeAmount(it))
                },
                currencies = state.currencies,
                onCurrencySelected = {
                    viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCurrency(it))
                }
            )

            CategoryScrollRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                imageLoader = imageLoader,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = {
                    viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCategory(it))
                },
                onShowAll = {
                    coroutineScope.launch { sheetState.show() }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DatePickerCard(
                    modifier = Modifier.weight(1f),
                    label = "Date",
                    date = state.date,
                    onDateSelected = {
                        viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeDate(it))
                    }
                )
                SelectorCard(
                    modifier = Modifier.weight(1f),
                    label = "Account",
                    value = state.selectedAccount?.name ?: "",
                    items = state.accounts,
                    nameMapping = { it.name },
                    onItemSelected = {
                        viewModel.perform(TransactionEditIncomeViewModel.Action.SelectAccount(it))
                    }
                )
            }

            AnimatedVisibility(visible = state.showRate) {
                TransactionEditRateTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    rate = state.rate,
                    onValueChange = { rate ->
                        viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeRate(rate))
                    }
                )
            }
        }
    }
}
```

**Commit:** `feat: wire category bottom sheet in income transaction edit`

---

## Task 4: Build verification

Run:
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Fix any compilation errors. If `ModalBottomSheetLayout` requires the `sheetContent` to have a minimum height, add `Modifier.fillMaxWidth().defaultMinSize(minHeight = 1.dp)` to the grid's root modifier inside `CategoryBottomSheetGrid`.

**Commit (if fixes needed):** `fix: resolve build issues in category bottom sheet`

---

## Task 5: Push branch and open PR

```bash
git push -u origin feature/category-bottom-sheet
```

Open PR with:
- **Title:** `feat: show all categories via bottom sheet on transaction edit`
- **Body:**
  ```
  ## Summary
  - Add "show all categories" bottom sheet that opens when tapping the "All" button in the category scroll row
  - Bottom sheet displays a 4-column grid of all categories sorted alphabetically
  - Tapping a category selects it and dismisses the sheet
  - Implemented for both expense and income transaction types

  ## Test plan
  - [ ] Open transaction edit (expense) → tap "All" → bottom sheet opens with category grid
  - [ ] Scroll the sheet up → expands to full screen
  - [ ] Tap a category → it gets selected, sheet dismisses, category shows as selected in scroll row
  - [ ] Repeat for income transaction type
  - [ ] Verify existing category selection via scroll row still works
  ```
