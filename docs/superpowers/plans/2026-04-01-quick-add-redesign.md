# Quick Add Transaction Screen Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the transaction add screen (expense/income) to match the Stitch "Updated Quick Add - Neutral Icons" design, with a new color palette, segmented toggle, large amount display, horizontal category scroll, card-style selectors, and ExtendedFAB.

**Architecture:** Primarily view-layer changes. Rewrite composables in existing ViewProvider files. Add `OnDiscardHandler` for the close button, routed through ViewModel → UseCase → Handler (same pattern as `OnTransactionSavedHandler` / `Save`). Change navigation display from BottomSheet to FullyVisible.

**Tech Stack:** Kotlin, Jetpack Compose (Material 1), Dagger 2

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/.../ui/theme/Color.kt` | Modify | Stitch design system color constants |
| `app/.../ui/theme/Theme.kt` | Modify | Wire new colors into Material palette |
| `zero-core/.../transactions/edit/OnDiscardHandler.kt` | Create | Discard callback interface |
| `zero-core/.../transactions/edit/TransactionEditViewModel.kt` | Modify | Add `Action.Discard` |
| `zero-core/.../transactions/edit/TransactionEditUseCase.kt` | Modify | Add `Action.Discard` |
| `zero-core/.../transactions/edit/DefaultTransactionEditViewModel.kt` | Modify | Map `Action.Discard` to UseCase |
| `zero-core/.../transactions/edit/DefaultTransactionEditUseCase.kt` | Modify | Handle `Action.Discard`, accept `OnDiscardHandler` |
| `zero-core/.../transactions/edit/TransactionEditComponent.kt` | Modify | Wire `OnDiscardHandler` through Dagger to UseCase |
| `zero-core/.../transactions/edit/TransactionEditViewProvider.kt` | Modify | Header, segmented toggle, FAB layout |
| `zero-core/.../transactions/edit/expense/TransactionEditExpenseViewProvider.kt` | Modify | Amount display, category row, selector cards |
| `zero-core/.../transactions/edit/income/TransactionEditIncomeViewProvider.kt` | Modify | Same as expense |
| `app/.../activity/screens/MainActivityScreenComponent.kt` | Modify | Wire `onDiscardHandler`, change display option |

---

### Task 1: Update Theme Colors

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/ui/theme/Theme.kt`

- [ ] **Step 1: Replace color constants in Color.kt**

Replace the entire contents of `Color.kt` with the Stitch design system palette:

```kotlin
package com.hluhovskyi.zero.ui.theme

import androidx.compose.ui.graphics.Color

// Stitch "The Private Vault" design system
val Primary = Color(0xFF000E2F)
val PrimaryContainer = Color(0xFF0A2351)
val OnPrimary = Color(0xFFFFFFFF)
val OnPrimaryContainer = Color(0xFF778BBF)

val Secondary = Color(0xFF006C4A)
val SecondaryContainer = Color(0xFF82F5C1)
val OnSecondary = Color(0xFFFFFFFF)
val OnSecondaryContainer = Color(0xFF00714E)

val Surface = Color(0xFFFAF8FD)
val SurfaceContainerLow = Color(0xFFF5F3F7)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainer = Color(0xFFEFEDF2)
val SurfaceContainerHigh = Color(0xFFE9E7EC)

val OnSurface = Color(0xFF1B1B1F)
val OnSurfaceVariant = Color(0xFF44464F)

val OutlineVariant = Color(0xFFC5C6D0)
val Outline = Color(0xFF757780)

val Error = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)
val OnError = Color(0xFFFFFFFF)

val InverseSurface = Color(0xFF303034)
val InverseOnSurface = Color(0xFFF2F0F5)
val InversePrimary = Color(0xFFB1C6FD)
```

- [ ] **Step 2: Update Theme.kt palettes**

Replace the palette definitions in `Theme.kt`:

```kotlin
package com.hluhovskyi.zero.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val LightColorPalette = lightColors(
    primary = PrimaryContainer,
    primaryVariant = Primary,
    secondary = Secondary,
    secondaryVariant = SecondaryContainer,
    background = Surface,
    surface = Surface,
    error = Error,
    onPrimary = OnPrimary,
    onSecondary = OnSecondary,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onError = OnError,
)

private val DarkColorPalette = darkColors(
    primary = InversePrimary,
    primaryVariant = PrimaryContainer,
    secondary = SecondaryContainer,
    background = InverseSurface,
    surface = InverseSurface,
    error = Error,
    onPrimary = Primary,
    onSecondary = OnSecondaryContainer,
    onBackground = InverseOnSurface,
    onSurface = InverseOnSurface,
    onError = OnError,
)

@Composable
fun ZeroTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/ui/theme/Color.kt app/src/main/java/com/hluhovskyi/zero/ui/theme/Theme.kt
git commit -m "feat: update theme to Stitch design system palette"
```

---

### Task 2: Add OnDiscardHandler Routed Through UseCase

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnDiscardHandler.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

- [ ] **Step 1: Create OnDiscardHandler**

Create `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnDiscardHandler.kt`:

```kotlin
package com.hluhovskyi.zero.transactions.edit

fun interface OnDiscardHandler {
    fun onDiscard()

    object Noop : OnDiscardHandler {
        override fun onDiscard() = Unit
    }
}
```

- [ ] **Step 2: Add Action.Discard to TransactionEditViewModel**

In `TransactionEditViewModel.kt`, add `Discard` to the `Action` sealed interface:

```kotlin
sealed interface Action {
    data class ChangeTransactionType(val type: TransactionEditType) : Action
    object Save : Action
    object Discard : Action
}
```

- [ ] **Step 3: Add Action.Discard to TransactionEditUseCase**

In `TransactionEditUseCase.kt`, add `Discard` to the `Action` sealed interface:

```kotlin
sealed interface Action {
    data class SwitchTransaction(val type: TransactionEditType) : Action
    data class SelectAccount(val account: TransactionEditAccount) : Action
    data class SelectTargetAccount(val account: TransactionEditAccount) : Action
    data class SelectCurrency(val currency: TransactionEditCurrency) : Action
    data class SelectCategory(val category: TransactionEditCategory) : Action
    data class ChangeAmount(val amount: String) : Action
    data class ChangeRate(val rate: String) : Action
    object EditCategories : Action
    object Save : Action
    object Discard : Action
}
```

- [ ] **Step 4: Map Discard in DefaultTransactionEditViewModel**

In `DefaultTransactionEditViewModel.kt`, add the mapping in the `perform` method's `when` block:

```kotlin
override fun perform(action: TransactionEditViewModel.Action) {
    val useCaseAction = when (action) {
        is TransactionEditViewModel.Action.ChangeTransactionType ->
            TransactionEditUseCase.Action.SwitchTransaction(action.type)
        is TransactionEditViewModel.Action.Save ->
            TransactionEditUseCase.Action.Save
        is TransactionEditViewModel.Action.Discard ->
            TransactionEditUseCase.Action.Discard
    }

    useCase.perform(useCaseAction)
}
```

- [ ] **Step 5: Handle Discard in DefaultTransactionEditUseCase**

In `DefaultTransactionEditUseCase.kt`, add `onDiscardHandler` constructor parameter (after `onEditCategoriesHandler`):

```kotlin
internal class DefaultTransactionEditUseCase(
    private val transactionId: Id,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
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

Add the `Discard` case in the `perform` method's `when` block (after the `Save` case):

```kotlin
is TransactionEditUseCase.Action.Discard -> {
    coroutineScope.launch(context = Dispatchers.Main) {
        onDiscardHandler.onDiscard()
    }
}
```

- [ ] **Step 6: Wire OnDiscardHandler through TransactionEditComponent**

In `TransactionEditComponent.kt`, add to `companion object.builder()`:

```kotlin
companion object {

    fun builder(dependencies: Dependencies): Builder = DaggerTransactionEditComponent.builder()
        .dependencies(dependencies)
        .transactionId(Id.Unknown)
        .onTransactionSavedHandler(OnTransactionSavedHandler.Noop)
        .onEditCategoriesHandler(OnEditCategoriesHandler.Noop)
        .onDiscardHandler(OnDiscardHandler.Noop)
}
```

Add to `Builder` interface:

```kotlin
@BindsInstance
fun onDiscardHandler(handler: OnDiscardHandler): Builder
```

Update the `useCase` provider in `Module` to pass `onDiscardHandler`:

```kotlin
@Provides
@TransactionEditScope
fun useCase(
    transactionId: Id,
    accountRepository: AccountRepository,
    categoriesQueryUseCase: CategoriesQueryUseCase,
    currencyRepository: CurrencyRepository,
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

The `viewProvider` provider does NOT need `OnDiscardHandler` — it stays as-is (the view dispatches `Action.Discard` through the ViewModel).

- [ ] **Step 7: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/OnDiscardHandler.kt \
       zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt \
       zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditUseCase.kt \
       zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt \
       zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt \
       zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt
git commit -m "feat: add OnDiscardHandler routed through ViewModel and UseCase"
```

---

### Task 3: Redesign TransactionEditViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

- [ ] **Step 1: Rewrite TransactionEditViewProvider.kt**

Replace the entire file contents:

```kotlin
package com.hluhovskyi.zero.transactions.edit

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val expenseComponent: Buildable<out AttachableViewComponent>,
    private val incomeComponent: Buildable<out AttachableViewComponent>,
    private val transferComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditView(
            viewModel = viewModel,
            expenseComponent = expenseComponent,
            incomeComponent = incomeComponent,
            transferComponent = transferComponent
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TransactionEditView(
    viewModel: TransactionEditViewModel,
    expenseComponent: Buildable<out AttachableViewComponent>,
    incomeComponent: Buildable<out AttachableViewComponent>,
    transferComponent: Buildable<out AttachableViewComponent>
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditViewModel.State())

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
        ) {
            item {
                TransactionEditHeader(
                    onDiscard = { viewModel.perform(TransactionEditViewModel.Action.Discard) }
                )
            }
            item {
                TransactionTypeToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp),
                    types = state.transactionTypes,
                    selectedType = state.selectedTransactionType,
                    onTypeSelected = { type ->
                        viewModel.perform(TransactionEditViewModel.Action.ChangeTransactionType(type))
                    }
                )
            }
            item {
                when (state.selectedTransactionType) {
                    TransactionEditType.EXPENSE -> expenseComponent.AttachWithView()
                    TransactionEditType.INCOME -> incomeComponent.AttachWithView()
                    TransactionEditType.TRANSFER -> transferComponent.AttachWithView()
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .height(56.dp),
            onClick = { viewModel.perform(TransactionEditViewModel.Action.Save) },
            text = {
                Text(
                    text = "Save Transaction",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            },
            backgroundColor = PrimaryContainer,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun TransactionEditHeader(
    onDiscard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDiscard) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Discard",
                tint = PrimaryContainer,
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = "New Transaction",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryContainer,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Spacer to balance the close button width
        Box(modifier = Modifier.widthIn(min = 48.dp))
    }
}

@Composable
private fun TransactionTypeToggle(
    modifier: Modifier = Modifier,
    types: List<TransactionEditType>,
    selectedType: TransactionEditType?,
    onTypeSelected: (TransactionEditType) -> Unit
) {
    Row(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        types.forEach { type ->
            val isSelected = type == selectedType
            val label = when (type) {
                TransactionEditType.EXPENSE -> "Expense"
                TransactionEditType.INCOME -> "Income"
                TransactionEditType.TRANSFER -> "Transfer"
            }

            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(type) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) SurfaceContainerLowest else Color.Transparent,
                elevation = if (isSelected) 2.dp else 0.dp,
            ) {
                Text(
                    modifier = Modifier.padding(vertical = 10.dp),
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colors.primary
                    } else {
                        OnSurfaceVariant
                    },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt
git commit -m "feat: redesign transaction edit with header, segmented toggle, and FAB"
```

---

### Task 4: Redesign TransactionEditExpenseViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/expense/TransactionEditExpenseViewProvider.kt`

- [ ] **Step 1: Rewrite TransactionEditExpenseViewProvider.kt**

Replace the entire file contents:

```kotlin
package com.hluhovskyi.zero.transactions.edit.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.colors.Color as DomainColor

private val TintWhite = DomainColor(
    id = Id("tint_white"),
    value = ColorValue(0xFFFFFFFF.toULong())
)
private val TintPrimaryContainer = DomainColor(
    id = Id("tint_primary_container"),
    value = ColorValue(0xFF0A2351.toULong())
)

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

@Composable
private fun TransactionEditExpenseView(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditExpenseViewModel.State())

    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 40.dp),
            amount = state.amount,
            currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
            onAmountChange = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeAmount(it))
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
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = "Currency",
                value = state.selectedCurrency?.let { "${it.currencySymbol} - ${it.name}" } ?: "",
                items = state.currencies,
                nameMapping = { "${it.currencySymbol} - ${it.name}" },
                onItemSelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCurrency(it))
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

@Composable
private fun AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    onAmountChange: (String) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AMOUNT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 3.sp,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = currencySymbol,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
            )
            BasicTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.sizeIn(minWidth = 80.dp),
                textStyle = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun CategoryScrollRow(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(categories, key = { it.id.value }) { category ->
            val isSelected = category.id == selectedCategory?.id
            CategoryItem(
                imageLoader = imageLoader,
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryItem(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isSelected) PrimaryContainer else Color(0xFFF1F5F9),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                image = category.icon,
                tint = if (isSelected) TintWhite else TintPrimaryContainer,
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = category.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colors.primary else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun <T> SelectorCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    items: List<T>,
    nameMapping: (T) -> String,
    onItemSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                .padding(16.dp)
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
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = OnSurfaceVariant,
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                ) {
                    Text(text = nameMapping(item))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/expense/TransactionEditExpenseViewProvider.kt
git commit -m "feat: redesign expense view with amount display, category scroll, and card selectors"
```

---

### Task 5: Redesign TransactionEditIncomeViewProvider

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/income/TransactionEditIncomeViewProvider.kt`

- [ ] **Step 1: Rewrite TransactionEditIncomeViewProvider.kt**

Replace the entire file contents. This is structurally identical to the expense view but references `TransactionEditIncomeViewModel`:

```kotlin
package com.hluhovskyi.zero.transactions.edit.income

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import com.hluhovskyi.zero.colors.Color as DomainColor

private val TintWhite = DomainColor(
    id = Id("tint_white"),
    value = ColorValue(0xFFFFFFFF.toULong())
)
private val TintPrimaryContainer = DomainColor(
    id = Id("tint_primary_container"),
    value = ColorValue(0xFF0A2351.toULong())
)

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

@Composable
private fun TransactionEditIncomeView(
    viewModel: TransactionEditIncomeViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditIncomeViewModel.State())

    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 40.dp),
            amount = state.amount,
            currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
            onAmountChange = {
                viewModel.perform(TransactionEditIncomeViewModel.Action.ChangeAmount(it))
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
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = "Currency",
                value = state.selectedCurrency?.let { "${it.currencySymbol} - ${it.name}" } ?: "",
                items = state.currencies,
                nameMapping = { "${it.currencySymbol} - ${it.name}" },
                onItemSelected = {
                    viewModel.perform(TransactionEditIncomeViewModel.Action.SelectCurrency(it))
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

@Composable
private fun AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    onAmountChange: (String) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AMOUNT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 3.sp,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = currencySymbol,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
            )
            BasicTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.sizeIn(minWidth = 80.dp),
                textStyle = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(MaterialTheme.colors.primary),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun CategoryScrollRow(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(categories, key = { it.id.value }) { category ->
            val isSelected = category.id == selectedCategory?.id
            CategoryItem(
                imageLoader = imageLoader,
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryItem(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isSelected) PrimaryContainer else Color(0xFFF1F5F9),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                image = category.icon,
                tint = if (isSelected) TintWhite else TintPrimaryContainer,
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = category.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colors.primary else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun <T> SelectorCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    items: List<T>,
    nameMapping: (T) -> String,
    onItemSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                .padding(16.dp)
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
                    imageVector = Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = OnSurfaceVariant,
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                ) {
                    Text(text = nameMapping(item))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/income/TransactionEditIncomeViewProvider.kt
git commit -m "feat: redesign income view with amount display, category scroll, and card selectors"
```

---

### Task 6: Wire OnDiscardHandler in Navigation and Change Display Option

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] **Step 1: Update transactionEditNavigationEntry**

In `MainActivityScreenComponent.kt`, update the `transactionEditNavigationEntry` method (lines 222-235). Change display option from `BottomSheet` to `FullyVisible` and add `onDiscardHandler`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun transactionEditNavigationEntry(
    componentBuilder: TransactionEditComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(
    destination = Destinations.Transaction.Edit,
    displayOption = NavigatorEntry.DisplayOption.FullyVisible,
) {
    componentBuilder
        .transactionId(Id.Unknown)
        .onTransactionSavedHandler { navigator.back() }
        .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
        .onDiscardHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 2: Update transactionItemEditNavigationEntry**

Also update the `transactionItemEditNavigationEntry` method (lines 240-250) to add `onDiscardHandler`:

```kotlin
@Provides
@IntoSet
@MainActivityScreenScope
fun transactionItemEditNavigationEntry(
    componentBuilder: TransactionEditComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(Destinations.Transaction.Item.Edit) {
    componentBuilder
        .transactionId(arguments.getValue(Destinations.Transaction.Item.TransactionId))
        .onTransactionSavedHandler { navigator.back() }
        .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
        .onDiscardHandler { navigator.back() }
        .logging(logger)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: wire OnDiscardHandler in navigation, change edit to full screen"
```

---

### Task 7: Build Verification

- [ ] **Step 1: Run the build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. If there are compilation errors, fix them — likely causes:
- Missing imports (adjust imports in the rewritten files)
- `ImageLoader.View` tint parameter type mismatch (the tint is `com.hluhovskyi.zero.colors.Color`, an int-based domain type — verify constructor usage)
- `Surface` with `onClick` requires `ExperimentalMaterialApi` opt-in (already included)
- `Icons.Filled.UnfoldMore` may need `material-icons-extended` dependency — if not available, use `Icons.Filled.ArrowDropDown` instead

- [ ] **Step 2: Fix any compilation issues and commit**

```bash
git add -A
git commit -m "fix: resolve compilation issues from redesign"
```

- [ ] **Step 3: Run the app on device/emulator and verify visually**

Check:
- New color palette applies across the app
- Transaction edit opens full-screen with close button header
- Segmented toggle switches between Expense/Income/Transfer
- Amount input works with large centered display
- Categories scroll horizontally, selection is visually indicated
- Currency and Account selectors open dropdown menus
- Rate field appears when currencies differ
- Save FAB triggers save and navigates back
- Close button navigates back
- Transfer tab still renders the old form correctly
