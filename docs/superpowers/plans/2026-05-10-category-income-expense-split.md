# Category Income/Expense Type Split — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add EXPENSE/INCOME type to categories; filter them in transaction edit by transaction type; add Expense/Income tabs to the categories screen and a type toggle to the add-category sheet.

**Architecture:** New `CategoryType` enum in zero-api propagates through DB entity → repository → query use case → UI models. ViewModels filter/tab by type. FAB in categories screen moves into `CategoryViewProvider` and calls a new `OnAddCategoryHandler` with the current tab type, which the app layer routes to `Category.Edit` with an `initialType` nav argument.

**Tech Stack:** Kotlin, Jetpack Compose, Dagger, Room (zero-database v5), coroutines/Flow.

---

### Task 1: CategoryType enum

**Files:**
- Create: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoryType.kt`

- [ ] Create the file:

```kotlin
package com.hluhovskyi.zero.categories

enum class CategoryType {
    EXPENSE,
    INCOME,
}
```

- [ ] Compile check:
```bash
./gradlew :zero-api:compileKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoryType.kt
git commit -m "feat: add CategoryType enum (EXPENSE/INCOME)"
```

---

### Task 2: DB migration 4→5 — add `type` column to CategoryEntity

**Files:**
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/categories/CategoryEntity.kt`
- Create: `zero-database/src/main/java/com/hluhovskyi/zero/categories/CategoryMigrations.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt`

- [ ] Update `CategoryEntity.kt` — add `type` field (default keeps existing rows as EXPENSE):

```kotlin
package com.hluhovskyi.zero.categories

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

@Entity(
    indices = [Index("userId")],
)
data class CategoryEntity(
    @PrimaryKey val id: Id.Known,
    val userId: Id.Known,
    val name: String,
    val iconId: String?,
    val colorId: String?,
    val type: String = "EXPENSE",
    val creationDateTime: LocalDateTime,
    val updatedDateTime: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
)
```

- [ ] Create `CategoryMigrations.kt`:

```kotlin
package com.hluhovskyi.zero.categories

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE CategoryEntity ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
    }
}
```

- [ ] Update `MainDatabase.kt` — bump version to 5:

```kotlin
private const val MAIN_DATABASE_VERSION = 5
```

- [ ] Update `DatabaseComponent.kt` — import and register migration:

Add import: `import com.hluhovskyi.zero.categories.MIGRATION_4_5`

In `mainDatabase()`, change:
```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```
to:
```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] Compile check:
```bash
./gradlew :zero-database:compileDebugKotlin 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/categories/CategoryEntity.kt zero-database/src/main/java/com/hluhovskyi/zero/categories/CategoryMigrations.kt zero-database/src/main/java/com/hluhovskyi/zero/MainDatabase.kt zero-database/src/main/java/com/hluhovskyi/zero/DatabaseComponent.kt
git commit -m "feat: add type column to CategoryEntity, DB migration 4→5"
```

---

### Task 3: CategoryRepository — propagate type through data layer

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoryRepository.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategoryRepository.kt`

- [ ] Update `CategoryRepository.kt` — add `type: CategoryType` with default to both data classes:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface CategoryRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    sealed interface Criteria<T> {
        class All : Criteria<List<Category>>
        data class ById(val categoryId: Id.Known) : Criteria<Category>
    }

    data class Category(
        val id: Id.Known,
        val parentCategoryId: Id,
        val name: String,
        val iconId: Id,
        val colorId: Id,
        val type: CategoryType = CategoryType.EXPENSE,
    )

    suspend fun insert(category: CategoryInsert)

    suspend fun insert(categories: List<CategoryInsert>)

    data class CategoryInsert(
        val id: Id = Id.Unknown,
        val parentCategoryId: Id,
        val name: String,
        val iconId: Id,
        val colorId: Id,
        val type: CategoryType = CategoryType.EXPENSE,
    )

    object Noop : CategoryRepository {
        override fun <T> query(criteria: Criteria<T>): Flow<T> = emptyFlow()
        override suspend fun insert(category: CategoryInsert) = Unit
        override suspend fun insert(categories: List<CategoryInsert>) = Unit
    }
}
```

- [ ] Update `RoomCategoryRepository.kt` — map `type` in both directions:

In `toRepositoryModel()`:
```kotlin
private fun CategoryEntity.toRepositoryModel(): CategoryRepository.Category = CategoryRepository.Category(
    id = id,
    parentCategoryId = Id.Unknown,
    name = name,
    colorId = Id(colorId),
    iconId = Id(iconId),
    type = runCatching { CategoryType.valueOf(type) }.getOrElse { CategoryType.EXPENSE },
)
```

Add import: `import com.hluhovskyi.zero.categories.CategoryType`

In `toEntity()`:
```kotlin
private fun CategoryRepository.CategoryInsert.toEntity(userId: Id.Known): CategoryEntity = CategoryEntity(
    id = (id as? Id.Known) ?: idGenerator(),
    userId = userId,
    name = name,
    iconId = iconId.valueOrNull(),
    colorId = colorId.valueOrNull(),
    type = type.name,
    creationDateTime = clock.localDateTime(zoneProvider.timeZone()),
    updatedDateTime = clock.localDateTime(zoneProvider.timeZone()),
)
```

- [ ] Compile check:
```bash
./gradlew :zero-database:compileDebugKotlin 2>&1 | tail -5
```

- [ ] Commit:
```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoryRepository.kt zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategoryRepository.kt
git commit -m "feat: propagate CategoryType through CategoryRepository"
```

---

### Task 4: SyncCategory — add optional type field (backward compat)

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncCategory.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategorySyncSink.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategorySyncSource.kt`

- [ ] Update `SyncCategory.kt` — `type` is optional so old clients syncing without it default to EXPENSE:

```kotlin
@Serializable
data class SyncCategory(
    @SerialName("id") @Serializable(with = IdKnownSerializer::class) override val id: Id.Known,
    @SerialName("name") val name: String,
    @SerialName("iconId") val iconId: String?,
    @SerialName("colorId") val colorId: String?,
    @SerialName("parentCategoryId") val parentCategoryId: String?,
    @SerialName("type") val type: String? = null,
    @SerialName("creationDateTime") val creationDateTime: LocalDateTime,
    @SerialName("updatedDateTime") override val updatedDateTime: LocalDateTime,
    @SerialName("deletedAt") override val deletedAt: LocalDateTime?,
) : SyncEntity
```

- [ ] Update `RoomCategorySyncSink.kt` — apply type with fallback to EXPENSE:

```kotlin
import com.hluhovskyi.zero.categories.CategoryType

private fun SyncCategory.toEntity(userId: Id.Known) = CategoryEntity(
    id = id,
    userId = userId,
    name = name,
    iconId = iconId,
    colorId = colorId,
    type = type ?: CategoryType.EXPENSE.name,
    creationDateTime = creationDateTime,
    updatedDateTime = updatedDateTime,
    deletedAt = deletedAt,
)
```

- [ ] Update `RoomCategorySyncSource.kt` — export type:

```kotlin
private fun CategoryEntity.toSyncModel() = SyncCategory(
    id = id,
    name = name,
    iconId = iconId,
    colorId = colorId,
    parentCategoryId = null,
    type = type,
    creationDateTime = creationDateTime,
    updatedDateTime = updatedDateTime,
    deletedAt = deletedAt,
)
```

- [ ] Compile check:
```bash
./gradlew :zero-database:compileDebugKotlin :zero-sync:compileKotlin 2>&1 | tail -5
```

- [ ] Commit:
```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/sync/SyncCategory.kt zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategorySyncSink.kt zero-database/src/main/java/com/hluhovskyi/zero/categories/RoomCategorySyncSource.kt
git commit -m "feat: add optional type field to SyncCategory (backward compat)"
```

---

### Task 5: CategoriesQueryUseCase — propagate type

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt`

- [ ] Update `CategoriesQueryUseCase.kt` — add `type: CategoryType` with default:

```kotlin
data class Category(
    override val id: Id.Known,
    val name: String,
    val icon: Image,
    val colorScheme: ColorScheme,
    val type: CategoryType = CategoryType.EXPENSE,
) : Identifiable
```

Add import: `import com.hluhovskyi.zero.categories.CategoryType`

- [ ] Update `DefaultCategoriesQueryUseCase.kt` — propagate `type` in `resolve()`:

```kotlin
return CategoriesQueryUseCase.Category(
    id = category.id,
    name = category.name,
    icon = icon.image,
    colorScheme = colorScheme,
    type = category.type,
)
```

- [ ] Run tests — existing tests use default `type` so no breakage expected:
```bash
./gradlew :zero-core:testDebugUnitTest 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt
git commit -m "feat: propagate CategoryType through CategoriesQueryUseCase"
```

---

### Task 6: TransactionEditCategory + type-filtered category list in DefaultTransactionEditUseCase

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditCategory.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt`

- [ ] Update `TransactionEditCategory.kt`:

```kotlin
package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

data class TransactionEditCategory(
    val id: Id.Known,
    val name: String,
    val colorScheme: ColorScheme,
    val icon: Image,
    val type: CategoryType = CategoryType.EXPENSE,
)
```

- [ ] Update `DefaultTransactionEditUseCase.kt`:

**a) Rename `categories` to `allCategories` in `CompositeState`:**
```kotlin
private data class CompositeState(
    val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
    val accounts: List<TransactionEditAccount> = emptyList(),
    val selectedAccount: TransactionEditAccount? = null,
    val targetAccounts: List<TransactionEditAccount> = emptyList(),
    val selectedTargetAccount: TransactionEditAccount? = null,
    val allCategories: List<TransactionEditCategory> = emptyList(),
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

**b) Filter in state mapping — replace both Expense and Income branches:**
```kotlin
TransactionEditType.EXPENSE -> TransactionEditUseCase.State.Expense(
    accounts = state.accounts,
    selectedAccount = state.selectedAccount,
    categories = state.allCategories.filter { it.type == CategoryType.EXPENSE },
    selectedCategory = state.selectedCategory?.takeIf { it.type == CategoryType.EXPENSE },
    currencies = state.currencies,
    selectedCurrency = state.selectedCurrency,
    amount = state.amount,
    rate = state.rate,
    date = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone()),
)

TransactionEditType.INCOME -> TransactionEditUseCase.State.Income(
    accounts = state.accounts,
    selectedAccount = state.selectedAccount,
    categories = state.allCategories.filter { it.type == CategoryType.INCOME },
    selectedCategory = state.selectedCategory?.takeIf { it.type == CategoryType.INCOME },
    currencies = state.currencies,
    selectedCurrency = state.selectedCurrency,
    amount = state.amount,
    rate = state.rate,
    date = state.localDateTime ?: clock.localDateTime(zoneProvider.timeZone()),
)
```

Add at the top of the file: `import com.hluhovskyi.zero.categories.CategoryType`

**c) Reset selectedCategory when switching transaction type — update `SwitchTransaction` handler:**
```kotlin
is TransactionEditUseCase.Action.SwitchTransaction -> {
    mutableState.update { state ->
        val targetType = when (action.type) {
            TransactionEditType.EXPENSE -> CategoryType.EXPENSE
            TransactionEditType.INCOME -> CategoryType.INCOME
            TransactionEditType.TRANSFER -> null
        }
        val newSelected = if (targetType != null && state.selectedCategory?.type != targetType) {
            state.allCategories.firstOrNull { it.type == targetType }
        } else {
            state.selectedCategory
        }
        state.copy(transactionType = action.type, selectedCategory = newSelected)
    }
}
```

**d) In `attach()` — map `type` when building `TransactionEditCategory`:**
```kotlin
TransactionEditCategory(
    id = category.id,
    name = category.name,
    colorScheme = category.colorScheme,
    icon = category.icon,
    type = category.type,
)
```

**e) In `attach()` — update all `state.categories` references to `state.allCategories`:**

Rename in the category loading `collectLatest` block:
```kotlin
.collectLatest { categories ->
    logger.d("attach, categories=${categories.joinIdsToString()}")
    mutableState.update { state ->
        if (state.selectedCategory != null) {
            val updated = categories.find { it.id == state.selectedCategory.id }
            state.copy(
                allCategories = categories,
                selectedCategory = if (updated != state.selectedCategory) updated else state.selectedCategory,
            )
        } else {
            val preSelected = (preSelectedCategoryId as? Id.Known)
                ?.let { id -> categories.find { it.id == id } }
            val reordered = if (preSelected != null) {
                listOf(preSelected) + categories.filter { it.id != preSelected.id }
            } else {
                categories
            }
            state.copy(
                allCategories = reordered,
                selectedCategory = preSelected ?: reordered.firstOrNull { it.type == CategoryType.EXPENSE },
            )
        }
    }
}
```

**f) In the `transactionEditCategoryUseCase.state` collect — use `allCategories`:**
```kotlin
val category = state.allCategories.firstOrNull { it.id == picked.categoryId }
if (category != null) state.copy(selectedCategory = category) else state
```

**g) In `resolveCategoryForEdit` calls — pass `state.allCategories`:**
```kotlin
val (categoryToSelect, reorderedCategories) =
    resolveCategoryForEdit(state.allCategories, transaction.categoryId)
```

And update the state copy to use `allCategories`:
```kotlin
partialState.copy(
    transactionType = TransactionEditType.EXPENSE,
    allCategories = reorderedCategories,
    selectedCategory = categoryToSelect ?: state.selectedCategory,
    rate = transaction.rate.value.toString(),
)
```
(Same for the Income branch.)

**h) In `SelectCategory` handler — update to use allCategories in the picker result lookup (already reads from `state.categories` in the `transactionEditCategoryUseCase` handler, updated in step f).**

- [ ] Run tests:
```bash
./gradlew :zero-core:testDebugUnitTest 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/TransactionEditCategory.kt zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
git commit -m "feat: filter transaction edit categories by expense/income type"
```

---

### Task 7: CategoryViewModel — Expense/Income tab state

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt`

- [ ] Update `CategoryViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryViewModel : AttachableActionStateModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryItem) : Action
        data class SelectTab(val type: CategoryType) : Action
    }

    data class State(
        val categories: List<CategoryItem> = emptyList(),
        val grandTotal: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val selectedTab: CategoryType = CategoryType.EXPENSE,
    )

    data class CategoryItem(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val spending: Spending = Spending.None,
    )

    sealed class Spending {
        data class Active(
            val totalAmount: Amount,
            val transactionCount: Int,
        ) : Spending()

        object None : Spending()
    }
}
```

- [ ] Update `DefaultCategoryViewModel.kt` — handle `SelectTab` and filter categories by tab:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultCategoryViewModel(
    private val categoriesQueryUseCase: CategoriesQueryUseCase,
    private val categorySpendingUseCase: CategorySpendingUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onCategorySelectedHandler: OnCategorySelectedHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : CategoryViewModel {

    private val mutableState = MutableStateFlow(CategoryViewModel.State())
    override val state: Flow<CategoryViewModel.State> = mutableState

    override fun perform(action: CategoryViewModel.Action) {
        when (action) {
            is CategoryViewModel.Action.SelectCategory -> coroutineScope.launch(Dispatchers.Main) {
                onCategorySelectedHandler.onSelected(action.category.id)
            }
            is CategoryViewModel.Action.SelectTab -> {
                mutableState.update { it.copy(selectedTab = action.type) }
            }
        }
    }

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch {
            val currencySymbol = currencyPrimaryUseCase.getPrimaryCurrency().symbol
            mutableState.update { it.copy(currencySymbol = currencySymbol) }

            mutableState
                .map { it.selectedTab }
                .distinctUntilChanged()
                .flatMapLatest { selectedTab ->
                    combine(
                        categoriesQueryUseCase.queryAll(),
                        categorySpendingUseCase.query(CategorySpendingUseCase.Period.CurrentMonth),
                    ) { categories, spendingList ->
                        val spendingById = spendingList.associateBy { it.categoryId }
                        val items = categories
                            .filter { it.type == selectedTab }
                            .map { category ->
                                val spending = spendingById[category.id]
                                CategoryViewModel.CategoryItem(
                                    id = category.id,
                                    name = category.name,
                                    icon = category.icon,
                                    colorScheme = category.colorScheme,
                                    spending = if (spending != null && spending.totalAmount > 0L) {
                                        CategoryViewModel.Spending.Active(
                                            totalAmount = spending.totalAmount,
                                            transactionCount = spending.transactionCount,
                                        )
                                    } else {
                                        CategoryViewModel.Spending.None
                                    },
                                )
                            }
                        val (active, inactive) = items.partition { it.spending is CategoryViewModel.Spending.Active }
                        val grandTotal = active.fold(Amount.zero()) { acc, item ->
                            acc + (item.spending as CategoryViewModel.Spending.Active).totalAmount
                        }
                        val sorted = active.sortedByDescending {
                            (it.spending as CategoryViewModel.Spending.Active).totalAmount.value
                        } + inactive.sortedBy { it.name }
                        sorted to grandTotal
                    }
                }
                .collectLatest { (items, grandTotal) ->
                    mutableState.update { it.copy(categories = items, grandTotal = grandTotal) }
                }
        }
    }
}
```

- [ ] Compile check:
```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

- [ ] Commit:
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewModel.kt zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoryViewModel.kt
git commit -m "feat: add Expense/Income tab state to CategoryViewModel"
```

---

### Task 8: OnAddCategoryHandler + update CategoryViewProvider + CategoryComponent

**Files:**
- Create: `zero-core/src/main/java/com/hluhovskyi/zero/categories/OnAddCategoryHandler.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt`

- [ ] Create `OnAddCategoryHandler.kt`:

```kotlin
package com.hluhovskyi.zero.categories

fun interface OnAddCategoryHandler {
    fun onAdd(type: CategoryType)
}
```

- [ ] Update `CategoryViewProvider.kt` — wrap in `Box`, add `SegmentedToggle` header item, add FAB:

Replace the full file:

```kotlin
package com.hluhovskyi.zero.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.SegmentedToggle
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.SurfaceContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

internal class CategoryViewProvider(
    private val viewModel: CategoryViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
    private val onAddCategory: OnAddCategoryHandler,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryView(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
            onAddCategory = onAddCategory,
        )
    }
}

@Composable
private fun CategoryView(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onAddCategory: OnAddCategoryHandler,
) {
    val state by viewModel.state.collectAsState(initial = CategoryViewModel.State())

    val currencySymbol = state.currencySymbol
    val grandTotal = state.grandTotal
    val active = remember(state.categories) {
        state.categories.filter { it.spending is CategoryViewModel.Spending.Active }
    }
    val inactive = remember(state.categories) {
        state.categories.filter { it.spending is CategoryViewModel.Spending.None }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
            item {
                Text(
                    text = "Categories",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Primary,
                    ),
                )
            }

            item {
                SegmentedToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                    items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
                    selectedItem = state.selectedTab,
                    onItemSelected = { viewModel.perform(CategoryViewModel.Action.SelectTab(it)) },
                    labelMapping = { if (it == CategoryType.EXPENSE) "Expense" else "Income" },
                )
            }

            items(active, key = { it.id.value }) { category ->
                val spending = category.spending as CategoryViewModel.Spending.Active
                val barFraction = if (grandTotal > 0L) {
                    (spending.totalAmount / grandTotal).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                val percentOfTotal = if (grandTotal > 0L) {
                    (spending.totalAmount / grandTotal * 100).toInt()
                } else {
                    0
                }

                ActiveCategoryCard(
                    category = category,
                    spending = spending,
                    formattedTotal = amountFormatter.format(spending.totalAmount, currencySymbol),
                    barFraction = barFraction,
                    percentOfTotal = percentOfTotal,
                    barColor = category.colorScheme.toUi().primary,
                    onClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) },
                    imageLoader = imageLoader,
                )
            }

            if (inactive.isNotEmpty()) {
                item {
                    Text(
                        text = "Unused this month",
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 10.dp,
                            bottom = 6.dp,
                        ),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Outline,
                            letterSpacing = 0.7.sp,
                        ),
                    )
                }
                items(inactive, key = { it.id.value }) { category ->
                    InactiveCategoryCard(
                        category = category,
                        onClick = { viewModel.perform(CategoryViewModel.Action.SelectCategory(category)) },
                        imageLoader = imageLoader,
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add category") },
            text = { Text("Add category") },
            onClick = { onAddCategory.onAdd(state.selectedTab) },
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}

// ActiveCategoryCard, InactiveCategoryCard, SpendingBar — unchanged from existing file
```

Keep `ActiveCategoryCard`, `InactiveCategoryCard`, and `SpendingBar` composables exactly as they are in the existing file — only the `CategoryViewProvider` class and `CategoryView` function change.

- [ ] Update `CategoryComponent.kt` — add `onAddCategoryHandler` to builder and module:

In `Builder` interface, add:
```kotlin
@BindsInstance
fun onAddCategoryHandler(handler: OnAddCategoryHandler): Builder
```

In `companion object.builder()`, add a noop default:
```kotlin
fun builder(dependencies: Dependencies): Builder = DaggerCategoryComponent.builder()
    .dependencies(dependencies)
    .onCategorySelectedHandler(OnCategorySelectedHandler.Noop)
    .onAddCategoryHandler { _ -> }
```

In `Module.viewProvider()`, add `onAddCategoryHandler` param:
```kotlin
@Provides
@CategoryScope
fun viewProvider(
    viewModel: CategoryViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onAddCategoryHandler: OnAddCategoryHandler,
): ViewProvider = CategoryViewProvider(
    viewModel = viewModel,
    imageLoader = imageLoader,
    amountFormatter = amountFormatter,
    onAddCategory = onAddCategoryHandler,
)
```

- [ ] Compile check:
```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

- [ ] Commit:
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/OnAddCategoryHandler.kt zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryViewProvider.kt zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt
git commit -m "feat: add type tabs and category FAB to CategoryViewProvider"
```

---

### Task 9: CategoryEditViewModel — type field + SelectType action

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt`
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditComponent.kt`

- [ ] Update `CategoryEditViewModel.kt`:

```kotlin
package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image

interface CategoryEditViewModel : AttachableActionStateModel<CategoryEditViewModel.Action, CategoryEditViewModel.State> {

    sealed interface Action {
        data class ChangeName(val name: String) : Action
        object SelectIcon : Action
        object SelectColor : Action
        data class SelectType(val type: CategoryType) : Action
        object Save : Action
    }

    data class State(
        val name: String = "",
        val icon: Image = Image.empty(),
        val colorScheme: ColorScheme = ColorScheme(
            swatch = Color.empty(),
            primary = Color.empty(),
            background = Color.empty(),
        ),
        val type: CategoryType = CategoryType.EXPENSE,
    )
}
```

- [ ] Update `DefaultCategoryEditViewModel.kt`:

**a) Add `initialType` constructor param:**
```kotlin
internal class DefaultCategoryEditViewModel(
    private val categoryId: Id,
    private val initialType: CategoryType = CategoryType.EXPENSE,
    private val categoryRepository: CategoryRepository,
    ...
)
```

**b) Add `type` to `CompositeState`:**
```kotlin
private data class CompositeState(
    val name: String = "",
    val iconId: Id = Id.Unknown,
    val icon: Image = Image.empty(),
    val colorId: Id = Id.Unknown,
    val colorScheme: ColorScheme = ColorScheme(
        swatch = Color.empty(),
        primary = Color.empty(),
        background = Color.empty(),
    ),
    val type: CategoryType = CategoryType.EXPENSE,
)
```

**c) Include `type` in the state mapping:**
```kotlin
override val state: Flow<CategoryEditViewModel.State> = mutableState.map { state ->
    CategoryEditViewModel.State(
        name = state.name,
        icon = state.icon,
        colorScheme = state.colorScheme,
        type = state.type,
    )
}
```

**d) Handle `SelectType` in `perform()`:**
```kotlin
is CategoryEditViewModel.Action.SelectType ->
    mutableState.update { it.copy(type = action.type) }
```

**e) In `attach()` — new category branch: apply `initialType`:**
```kotlin
} else {
    mutableState.update { it.copy(type = initialType) }
    launch {
        iconRepository.query(IconRepository.Criteria.ById(IconRepository.unknownCategoryIconId()))
            .firstOrNull()?.let { icon ->
                mutableState.update { it.copy(iconId = icon.id, icon = icon.image) }
            }
    }
    launch {
        colorRepository.query(ColorRepository.Criteria.ById(ColorRepository.unknownCategoryColorId()))
            .firstOrNull()?.let { color ->
                mutableState.update { it.copy(colorId = color.id, colorScheme = colorRepository.schemeFor(color.id)) }
            }
    }
}
```

**f) In `attach()` — existing category branch: load type from repository:**
After loading the category and resolving color/icon, add `type = category.type` to the `mutableState.update`:
```kotlin
mutableState.update { state ->
    state.copy(
        name = category.name,
        iconId = icon.id,
        icon = icon.image,
        colorId = color.id,
        colorScheme = colorRepository.schemeFor(color.id),
        type = category.type,
    )
}
```

**g) In `Save` — include type in `CategoryInsert`:**
```kotlin
categoryRepository.insert(
    CategoryRepository.CategoryInsert(
        id = categoryId,
        parentCategoryId = Id.Unknown,
        name = state.name,
        iconId = state.iconId,
        colorId = state.colorId,
        type = state.type,
    ),
)
```

- [ ] Update `CategoryEditComponent.kt` — add `initialType` qualifier + binding:

Add at the top (alongside `CategoryEditId`):
```kotlin
@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class CategoryEditInitialType
```

In `Builder`:
```kotlin
@BindsInstance
fun initialType(@CategoryEditInitialType type: CategoryType): Builder
```

In `companion object.builder()`:
```kotlin
fun builder(dependencies: Dependencies): Builder = DaggerCategoryEditComponent.builder()
    .dependencies(dependencies)
    .categoryEditIconUseCase(CategoryEditIconUseCase.Noop)
    .categoryEditColorUseCase(CategoryEditColorUseCase.Noop)
    .onCategorySavedHandler(OnCategorySavedHandler.Noop)
    .onDiscardHandler(OnDiscardHandler.Noop)
    .initialType(CategoryType.EXPENSE)
```

In `Module.viewModel()`:
```kotlin
@Provides
@CategoryEditScope
fun viewModel(
    @CategoryEditId categoryId: Id,
    @CategoryEditInitialType initialType: CategoryType,
    categoryRepository: CategoryRepository,
    iconRepository: IconRepository,
    colorRepository: ColorRepository,
    categoryEditIconUseCase: CategoryEditIconUseCase,
    categoryEditColorUseCase: CategoryEditColorUseCase,
    onCategorySavedHandler: OnCategorySavedHandler,
): CategoryEditViewModel = DefaultCategoryEditViewModel(
    categoryId = categoryId,
    initialType = initialType,
    categoryRepository = categoryRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    categoryEditIconUseCase = categoryEditIconUseCase,
    categoryEditColorUseCase = categoryEditColorUseCase,
    onCategorySavedHandler = onCategorySavedHandler,
)
```

- [ ] Compile check:
```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

- [ ] Commit:
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditViewModel.kt zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/DefaultCategoryEditViewModel.kt zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoryEditComponent.kt
git commit -m "feat: add type field and SelectType action to CategoryEditViewModel"
```

---

### Task 10: CategoriesEditViewProvider — add Expense/Income toggle

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt`

- [ ] Add `SegmentedToggle` above the icon/name row in `CategoryEditView`. Add imports:

```kotlin
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.ui.SegmentedToggle
```

Inside `CategoryEditView`, in the `Column` after `ModalHeader`, insert before the `Row` with the icon tile:

```kotlin
// Type toggle
SegmentedToggle(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
    items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
    selectedItem = state.type,
    onItemSelected = { viewModel.perform(CategoryEditViewModel.Action.SelectType(it)) },
    labelMapping = { if (it == CategoryType.EXPENSE) "Expense" else "Income" },
)
```

The full `Column` block after this change:
```kotlin
Column(
    modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    SegmentedToggle(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
        selectedItem = state.type,
        onItemSelected = { viewModel.perform(CategoryEditViewModel.Action.SelectType(it)) },
        labelMapping = { if (it == CategoryType.EXPENSE) "Expense" else "Income" },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryIconTile(
            modifier = Modifier.fillMaxHeight(),
            colorScheme = state.colorScheme.toUi(),
            imageLoader = imageLoader,
            icon = state.icon,
            onClick = { viewModel.perform(CategoryEditViewModel.Action.SelectIcon) },
        )
        NameFormCard(
            modifier = Modifier.weight(1f),
            value = state.name,
            onValueChange = { viewModel.perform(CategoryEditViewModel.Action.ChangeName(it)) },
        )
    }

    Spacer(modifier = Modifier.height(96.dp))
}
```

- [ ] Compile check:
```bash
./gradlew :zero-core:compileDebugKotlin 2>&1 | tail -5
```

- [ ] Commit:
```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/edit/CategoriesEditViewProvider.kt
git commit -m "feat: add Expense/Income toggle to category edit sheet"
```

---

### Task 11: App wiring — navigation argument, remove old FAB, wire onAddCategoryHandler

**Files:**
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/CategoriesScreen.kt`
- Modify: `app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt`

- [ ] Update `Destinations.kt` — add optional `InitialType` argument to `Category.Edit`:

```kotlin
sealed interface Category : Destination {
    object All : Category, Destination by destinationOf("categories")
    object Edit : Category, Destination by destinationOf("categories/edit", InitialType) {
        object InitialType : Argument<String> by stringOptionalValueOf("initialType")
    }
    object Picker : Category, Destination by destinationOf("categories/picker", RequestId, SelectedCategoryId) {
        object RequestId : Argument<Id> by idOptionalValueOf("requestId")
        object SelectedCategoryId : Argument<Id> by idOptionalValueOf("selectedCategoryId")
    }
    // Item destinations unchanged
    sealed interface Item : Category {
        object CategoryId : Argument<Id.Known> by idKnownValueOf("categoryId")
        object Detail : Item, Destination by destinationOf("categories/{categoryId}", CategoryId)
        object Edit : Item, Destination by destinationOf("categories/{categoryId}/edit", CategoryId)
    }
}
```

- [ ] Update `CategoriesScreen.kt` — remove `onCategoriesEdit` param and old FAB:

```kotlin
package com.hluhovskyi.zero.activity.screens

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

@Composable
fun CategoriesScreen(
    component: Buildable<out AttachableViewComponent>,
) {
    component.AttachWithView()
}
```

- [ ] Update `MainActivityScreenComponent.kt` — wire `onAddCategoryHandler` and `initialType`:

Add import: `import com.hluhovskyi.zero.categories.CategoryType`

**a) In `categoryNavigationEntry`** — remove `onCategoriesEdit` param from `CategoriesScreen`, add `onAddCategoryHandler`:

```kotlin
fun categoryNavigationEntry(
    componentBuilder: CategoryComponent.Builder,
    navigatorScope: NavigatorScope,
    logger: Logger,
): NavigatorEntry = navigatorScope.composable(Destinations.Category.All) {
    CategoriesScreen(
        component = componentBuilder
            .onCategorySelectedHandler { categoryId ->
                navigator.navigateTo(
                    Destinations.Category.Item.Detail,
                    Destinations.Category.Item.CategoryId.withValue(categoryId),
                )
            }
            .onAddCategoryHandler { type ->
                navigator.navigateTo(
                    Destinations.Category.Edit,
                    Destinations.Category.Edit.InitialType.withValue(type.name),
                )
            }
            .logging(logger),
    )
}
```

**b) In `categoryEditNavigationEntry`** — read `initialType` from arguments:

```kotlin
fun categoryEditNavigationEntry(
    componentBuilder: CategoryEditComponent.Builder,
    navigatorScope: NavigatorScope,
    categoryEditIconUseCase: CategoryEditIconUseCase,
    categoryEditColorUseCase: CategoryEditColorUseCase,
    logger: Logger,
): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Edit) {
    val initialTypeRaw = arguments.getValue(Destinations.Category.Edit.InitialType).value
    val initialType = runCatching { CategoryType.valueOf(initialTypeRaw) }
        .getOrElse { CategoryType.EXPENSE }
    componentBuilder
        .categoryId(Id.Unknown)
        .initialType(initialType)
        .categoryEditIconUseCase(categoryEditIconUseCase)
        .categoryEditColorUseCase(categoryEditColorUseCase)
        .onCategorySavedHandler { navigator.back() }
        .onDiscardHandler { navigator.back() }
        .logging(logger)
}
```

Note: `categoryEditItemNavigationEntry` (editing an existing category) does NOT need `initialType` — the companion default (`CategoryType.EXPENSE`) is set and the VM will override it from the stored category anyway.

- [ ] Full build:
```bash
./gradlew assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL

- [ ] Commit:
```bash
git add app/src/main/java/com/hluhovskyi/zero/activity/navigation/Destinations.kt app/src/main/java/com/hluhovskyi/zero/activity/screens/CategoriesScreen.kt app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenComponent.kt
git commit -m "feat: wire category type tabs and initialType navigation in app layer"
```

---

### Task 12: Tests + Lint

- [ ] Run all unit tests:
```bash
./gradlew testDebugUnitTest 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL (all new fields have defaults, no existing test breakage)

- [ ] Run lint:
```bash
./gradlew lintDebug 2>&1 | grep -E "error:|Error" | head -20
```
Expected: no errors

- [ ] If lint fails with unused import or similar: fix inline and amend or add a fix commit.
