# Implementation Plan: Extract Category Picker as Reusable Component

**Branch:** `feature/category-bottom-sheet` (continue on existing branch)

## Goal

Extract the category bottom sheet grid into a standalone reusable `CategoryPickerComponent` following the existing Component → ViewModel → ViewProvider pattern (same as `CategoryComponent`). The transaction edit screen should have near-zero changes — just wiring the new component and routing the selection callback.

## Reference Files

Study these files for the exact pattern to follow:
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt` — Component structure
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt` — ViewModel interface
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt` — ViewModel impl
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt` — ViewProvider
- `zero-core/src/main/java/com/hluhovskyi/zero/categories/OnCategorySelectedHandler.kt` — Handler callback

## Important Architecture Rules

- Read `AGENTS.md` and `docs/agents/architecture.md` before starting
- Components communicate via `fun interface` handlers, not shared state
- `attach()` returns `Closeable` that cancels coroutines
- ViewProvider is a simple class with `@Composable fun View()`
- Dagger `@Component.Builder` implements `Buildable<T>`
- Handler callbacks are passed via `@BindsInstance`

---

## Task 1: Create `CategoryPickerComponent` with ViewModel and ViewProvider

Create the following NEW files under `zero-core/src/main/java/com/hluhovskyi/zero/categories/picker/`:

### 1a. `CategoryPickerViewModel.kt`

```kotlin
package com.hluhovskyi.zero.categories.picker

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryPickerViewModel
    : AttachableActionStateModel<CategoryPickerViewModel.Action, CategoryPickerViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryPickerItem) : Action
    }

    data class State(
        val categories: List<CategoryPickerItem> = emptyList()
    )

    data class CategoryPickerItem(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    )
}
```

### 1b. `DefaultCategoryPickerViewModel.kt`

Follow the same pattern as `DefaultCategoryViewModel.kt`. Load categories via `CategoriesQueryUseCase.queryAll()`, sort alphabetically, map to `CategoryPickerItem`. On `SelectCategory` action, call `onCategorySelectedHandler.onSelected(categoryId)` on `Dispatchers.Main`.

```kotlin
package com.hluhovskyi.zero.categories.picker

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.OnCategorySelectedHandler
import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

internal class DefaultCategoryPickerViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : CategoryPickerViewModel {

    private val mutableState = MutableStateFlow(CategoryPickerViewModel.State())
    override val state: Flow<CategoryPickerViewModel.State> = mutableState

    override fun perform(action: CategoryPickerViewModel.Action) {
        when (action) {
            is CategoryPickerViewModel.Action.SelectCategory -> coroutineScope.launch(context = Dispatchers.Main) {
                onCategorySelectedHandler.onSelected(action.category.id)
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            categoriesQueryUseCase.queryAll()
                .map { categories ->
                    categories
                        .sortedBy { it.name }
                        .map { category ->
                            CategoryPickerViewModel.CategoryPickerItem(
                                id = category.id,
                                name = category.name,
                                icon = category.icon,
                                colorScheme = category.colorScheme,
                            )
                        }
                }
                .collectLatest { categories ->
                    mutableState.update { state ->
                        state.copy(categories = categories)
                    }
                }
        }
    }
}
```

### 1c. `CategoryPickerViewProvider.kt`

Renders a grid of categories (4 columns) using `Column` + `Row` chunked layout (NOT `LazyVerticalGrid` — this component may be embedded in scrollable containers). Reuse `CategoryIconView` for rendering. Use `verticalScroll` for scrollability.

```kotlin
package com.hluhovskyi.zero.categories.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface

private const val GRID_COLUMNS = 4

internal class CategoryPickerViewProvider(
    private val viewModel: CategoryPickerViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryPickerView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun CategoryPickerView(
    viewModel: CategoryPickerViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryPickerViewModel.State())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.categories.chunked(GRID_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { category ->
                    CategoryPickerGridItem(
                        modifier = Modifier.weight(1f),
                        imageLoader = imageLoader,
                        category = category,
                        onClick = { viewModel.perform(CategoryPickerViewModel.Action.SelectCategory(category)) }
                    )
                }
                repeat(GRID_COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerGridItem(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    category: CategoryPickerViewModel.CategoryPickerItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            size = 48.dp,
            contentPadding = 12.dp,
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
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
```

### 1d. `CategoryPickerComponent.kt`

Follow `CategoryComponent.kt` exactly. Dependencies needs `imageLoader` and `categoriesQueryUseCase`. Builder takes `onCategorySelectedHandler` via `@BindsInstance`.

```kotlin
package com.hluhovskyi.zero.categories.picker

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.OnCategorySelectedHandler
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryPickerScope

private const val TAG = "CategoryPickerComponent"

@CategoryPickerScope
@dagger.Component(
    dependencies = [CategoryPickerComponent.Dependencies::class],
    modules = [CategoryPickerComponent.Module::class]
)
abstract class CategoryPickerComponent : AttachableViewComponent {

    internal abstract val viewModel: CategoryPickerViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val imageLoader: ImageLoader
        val categoriesQueryUseCase: CategoriesQueryUseCase
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerCategoryPickerComponent.builder()
            .dependencies(dependencies)
            .onCategorySelectedHandler(OnCategorySelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<CategoryPickerComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onCategorySelectedHandler(handler: OnCategorySelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @CategoryPickerScope
        fun viewModel(
            categoriesQueryUseCase: CategoriesQueryUseCase,
            onCategorySelectedHandler: OnCategorySelectedHandler,
        ): CategoryPickerViewModel = DefaultCategoryPickerViewModel(
            categoriesQueryUseCase = categoriesQueryUseCase,
            onCategorySelectedHandler = onCategorySelectedHandler,
        )

        @Provides
        @CategoryPickerScope
        fun viewProvider(
            viewModel: CategoryPickerViewModel,
            imageLoader: ImageLoader,
        ): ViewProvider = CategoryPickerViewProvider(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}
```

**Commit:** `feat: add reusable CategoryPickerComponent with grid layout`

---

## Task 2: Wire CategoryPickerComponent into TransactionEditComponent

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

1. Make `TransactionEditComponent` implement `CategoryPickerComponent.Dependencies` (it already has `imageLoader` and `categoriesQueryUseCase` available via its own `Dependencies`)
2. Add a `@Provides` method in Module that creates `CategoryPickerComponent.Builder`:

```kotlin
@Provides
@TransactionEditScope
fun categoryPickerComponentBuilder(
    component: TransactionEditComponent,
): CategoryPickerComponent.Builder =
    CategoryPickerComponent.builder(component)
```

3. Update the `viewProvider` `@Provides` method to accept `categoryPickerComponentBuilder: CategoryPickerComponent.Builder` and pass it to `TransactionEditViewProvider`

**Commit:** `feat: wire CategoryPickerComponent into TransactionEditComponent`

---

## Task 3: Refactor TransactionEditViewProvider to use CategoryPickerComponent

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewProvider.kt`

1. Add `categoryPickerComponent: Buildable<out AttachableViewComponent>` constructor param
2. Replace the inline `CategoryBottomSheetGrid(...)` in `sheetContent` with `categoryPickerComponent.AttachWithView()`
3. Remove the import/usage of `CategoryBottomSheetGrid`
4. Remove the import/usage of `ImageLoader` (no longer needed directly — the picker component has its own)
5. Keep `ModalBottomSheetLayout`, `sheetState`, `LocalShowAllCategories`, and keyboard dismissal — these are the hosting/orchestration concerns

The `onCategorySelectedHandler` wired in the component builder will handle routing the selection back. The sheet hide + keyboard dismiss stays in the `LocalShowAllCategories` provider.

For the selection callback: the `CategoryPickerComponent.Builder.onCategorySelectedHandler` needs to both:
- Route the selected category ID back to `TransactionEditUseCase.Action.SelectCategory`
- Hide the bottom sheet

Since the handler is set at build time (not in Compose), and hiding the sheet needs a `CoroutineScope` + `ModalBottomSheetState`, we need to handle sheet dismissal separately. Use a `LaunchedEffect` that watches `sheetState.currentValue` — when it transitions to `Hidden` after a selection, it's already handled by the sheet's swipe-to-dismiss. For programmatic hide after selection, set a flag.

**Simpler approach:** Keep the `onCategorySelectedHandler` purely for routing the category ID to the use case. For dismissing the sheet, observe the handler's callback in Compose and hide the sheet.

**Simplest approach:** The `onCategorySelectedHandler` fires on Main thread. Wire it to:
1. Find the selected category from the use case state and perform `SelectCategory` on the use case
2. The sheet dismissal can be triggered via a shared `MutableState<Boolean>` or by just always hiding after any selection

Actually, the cleanest way following existing patterns: wire the `onCategorySelectedHandler` at the `TransactionEditComponent.Module` level to call `useCase.perform(SelectCategory(...))`. But we need the full `TransactionEditCategory` object, not just the ID. The handler only provides `Id.Known`.

**Resolution:** In `TransactionEditComponent.Module`, create the handler that:
1. Reads the current categories from the use case state to find the matching category
2. Calls `useCase.perform(SelectCategory(category))`

For sheet dismissal: in `TransactionEditViewProvider`, use a `LaunchedEffect` that monitors `state.selectedCategory` changes and hides the sheet when the category changes while the sheet is visible.

Here is the updated `TransactionEditViewProvider`:

```kotlin
internal class TransactionEditViewProvider(
    private val viewModel: TransactionEditViewModel,
    private val categoryPickerComponent: Buildable<out AttachableViewComponent>,
    private val expenseComponent: Buildable<out AttachableViewComponent>,
    private val incomeComponent: Buildable<out AttachableViewComponent>,
    private val transferComponent: Buildable<out AttachableViewComponent>,
) : ViewProvider {
    // ... View() delegates to TransactionEditView with all params
}
```

In the composable, `sheetContent` becomes:
```kotlin
sheetContent = {
    categoryPickerComponent.AttachWithView()
}
```

For auto-hiding the sheet after selection, add:
```kotlin
LaunchedEffect(state.selectedCategory) {
    if (sheetState.isVisible) {
        sheetState.hide()
    }
}
```

Remove `imageLoader` from `TransactionEditViewProvider` constructor (no longer needed at this level).

**Commit:** `refactor: use CategoryPickerComponent in TransactionEditViewProvider`

---

## Task 4: Update TransactionEditComponent.Module to wire everything

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditComponent.kt`

Update the `viewProvider` provides method:

```kotlin
@Provides
@TransactionEditScope
fun viewProvider(
    viewModel: TransactionEditViewModel,
    categoryPickerComponentBuilder: CategoryPickerComponent.Builder,
    expenseComponentBuilder: TransactionEditExpenseComponent.Builder,
    incomeComponentBuilder: TransactionEditIncomeComponent.Builder,
    transferComponentBuilder: TransactionEditTransferComponent.Builder,
    useCase: TransactionEditUseCase,
    logger: Logger
): ViewProvider {
    // Wire the category selection back to the use case
    val categoryPickerBuildable = categoryPickerComponentBuilder
        .onCategorySelectedHandler { categoryId ->
            // Find category from current state and select it
            useCase.perform(TransactionEditUseCase.Action.SelectCategoryById(categoryId))
        }

    return TransactionEditViewProvider(
        viewModel = viewModel,
        categoryPickerComponent = categoryPickerBuildable.logging(logger),
        expenseComponent = expenseComponentBuilder.logging(logger),
        incomeComponent = incomeComponentBuilder.logging(logger),
        transferComponent = transferComponentBuilder.logging(logger),
    )
}
```

Wait — there's a problem. The existing `TransactionEditUseCase.Action.SelectCategory` takes a `TransactionEditCategory` object, not an ID. We need a way to select by ID.

**Add `SelectCategoryById` action** to `TransactionEditUseCase`:

In `TransactionEditUseCase.kt`, add:
```kotlin
data class SelectCategoryById(val categoryId: Id.Known) : Action
```

In `DefaultTransactionEditUseCase.kt`, handle it:
```kotlin
is TransactionEditUseCase.Action.SelectCategoryById -> {
    mutableState.update { state ->
        val category = state.categories.firstOrNull { it.id == action.categoryId }
        if (category != null) state.copy(selectedCategory = category) else state
    }
}
```

**Commit:** `refactor: wire CategoryPickerComponent selection back to TransactionEditUseCase`

---

## Task 5: Revert unnecessary changes to TransactionEditViewModel

**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditViewModel.kt`
**File:** `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditViewModel.kt`

Remove `categories`, `selectedCategory`, and `SelectCategory` action from `TransactionEditViewModel` — they were only added to support the inline grid which is now gone. The `TransactionEditViewModel.State` goes back to:

```kotlin
data class State(
    val transactionTypes: List<TransactionEditType> = emptyList(),
    val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
    val date: LocalDateTime = LocalDateTime.now(),
)
```

But KEEP `selectedCategory` — we still need it in `TransactionEditViewModel.State` to detect when a category is selected (for auto-hiding the sheet). Actually, we only need `selectedCategory: TransactionEditCategory?` (not the full list). Update accordingly:

```kotlin
data class State(
    val transactionTypes: List<TransactionEditType> = emptyList(),
    val selectedTransactionType: TransactionEditType = TransactionEditType.EXPENSE,
    val date: LocalDateTime = LocalDateTime.now(),
    val selectedCategory: TransactionEditCategory? = null,
)
```

And in `DefaultTransactionEditViewModel`, map `selectedCategory` from the use case state (same as current), but remove the `categories` list and the `SelectCategory` action.

**Commit:** `refactor: clean up TransactionEditViewModel state`

---

## Task 6: Delete old files

Delete these files that are no longer needed:
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryBottomSheetGrid.kt`
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/LocalShowAllCategories.kt`

Wait — `LocalShowAllCategories` IS still needed. The expense/income ViewProviders consume it to trigger the bottom sheet. Keep it.

Only delete:
- `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryBottomSheetGrid.kt`

**Commit:** `refactor: remove unused CategoryBottomSheetGrid`

---

## Task 7: Build verification

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Fix any issues. Common things to watch for:
- Dagger codegen: make sure `DaggerCategoryPickerComponent` is generated (may need a clean build: `./gradlew clean assembleDebug`)
- Import cleanup: remove unused imports in modified files
- The `CategoryPickerComponent` needs to be in `categories/picker/` package, not under `transactions/edit/`

**Commit (if fixes needed):** `fix: resolve build issues after category picker refactor`

---

## Task 8: Force-push and update PR

```bash
git push
```

The PR at https://github.com/hluhovskyi/zero/pull/13 will be updated automatically.

---

## Summary of Changes by File

| File | Change Type | Description |
|------|-------------|-------------|
| `categories/picker/CategoryPickerViewModel.kt` | NEW | ViewModel interface |
| `categories/picker/DefaultCategoryPickerViewModel.kt` | NEW | ViewModel impl, loads categories |
| `categories/picker/CategoryPickerViewProvider.kt` | NEW | Grid UI |
| `categories/picker/CategoryPickerComponent.kt` | NEW | Dagger component |
| `transactions/edit/TransactionEditUseCase.kt` | MODIFY | Add `SelectCategoryById` action |
| `transactions/edit/DefaultTransactionEditUseCase.kt` | MODIFY | Handle `SelectCategoryById` |
| `transactions/edit/TransactionEditComponent.kt` | MODIFY | Wire `CategoryPickerComponent`, update viewProvider provider |
| `transactions/edit/TransactionEditViewProvider.kt` | MODIFY | Use picker component instead of inline grid |
| `transactions/edit/TransactionEditViewModel.kt` | MODIFY | Remove `categories` list and `SelectCategory` action, keep `selectedCategory` |
| `transactions/edit/DefaultTransactionEditViewModel.kt` | MODIFY | Matching ViewModel changes |
| `transactions/edit/common/CategoryBottomSheetGrid.kt` | DELETE | Replaced by CategoryPickerViewProvider |

**Near-zero changes in existing classes:** The transaction edit flow only changes to add the picker component wiring and the `SelectCategoryById` action. All category loading/display logic lives in the new standalone `CategoryPickerComponent`.
