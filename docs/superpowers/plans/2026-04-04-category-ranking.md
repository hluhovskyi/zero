# Category Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rank categories by frequency + recency in the transaction edit screen, with a "Show all" action item and selected-category-first behavior when editing.

**Architecture:** Add a `queryRanked(signals)` method to `CategoriesQueryUseCase` that combines category data with transaction usage statistics. The DAO layer gets a new aggregation query on `TransactionEntity`. The UI gets a "Show all" noop action item at position 0 in `CategoryScrollRow`.

**Tech Stack:** Kotlin, Room, Coroutines/Flow, Jetpack Compose, Dagger

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `zero-api/.../categories/CategoriesQueryUseCase.kt` | Modify | Add `RankSignal` sealed class, `queryRanked()` method |
| `zero-database/.../transactions/TransactionRoom.kt` | Modify | Add `selectCategoryUsageStatistic()` query |
| `zero-database/.../transactions/CategoryUsageStatistic.kt` | Create | Room result data class |
| `zero-database/.../transactions/RoomTransactionRepository.kt` | Modify | Expose usage stats flow via new repository method |
| `zero-api/.../transactions/TransactionRepository.kt` | Modify | Add `CategoryUsageStatistic` + query criteria |
| `zero-core/.../categories/DefaultCategoriesQueryUseCase.kt` | Modify | Implement `queryRanked()` with scoring logic |
| `zero-core/.../categories/CategoryComponent.kt` | Modify | Update factory to accept `TransactionRepository` |
| `app/.../ApplicationComponent.kt` | Modify | Pass `TransactionRepository` to `categoriesQueryUseCase` |
| `zero-core/.../transactions/edit/DefaultTransactionEditUseCase.kt` | Modify | Switch from `queryAll()` to `queryRanked()`, handle original category placement |
| `zero-core/.../transactions/edit/common/CategoryScrollRow.kt` | Modify | Add "Show all" item at position 0 |
| `zero-core/src/test/.../categories/DefaultCategoriesQueryUseCaseTest.kt` | Create | Tests for ranking logic |

---

### Task 1: Add CategoryUsageStatistic DAO Query

**Files:**
- Create: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategoryUsageStatistic.kt`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt:13-80`

- [ ] **Step 1: Create the Room result data class**

Create `zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategoryUsageStatistic.kt`:

```kotlin
package com.hluhovskyi.zero.transactions

import java.time.LocalDateTime

internal data class CategoryUsageStatistic(
    val categoryId: String,
    val transactionCount: Int,
    val lastUsedDateTime: LocalDateTime,
)
```

- [ ] **Step 2: Add the DAO query to TransactionRoom**

Add to `TransactionRoom.kt` (after the existing `selectById` methods, before `@Insert`):

```kotlin
@Query("""
    SELECT categoryId,
           COUNT(*) as transactionCount,
           MAX(enteredDateTime) as lastUsedDateTime
    FROM TransactionEntity
    WHERE userId = :userId AND categoryId IS NOT NULL
    GROUP BY categoryId
""")
fun selectCategoryUsageStatistic(userId: String): Flow<List<CategoryUsageStatistic>>
```

- [ ] **Step 3: Commit**

```bash
git add zero-database/src/main/java/com/hluhovskyi/zero/transactions/CategoryUsageStatistic.kt \
       zero-database/src/main/java/com/hluhovskyi/zero/transactions/TransactionRoom.kt
git commit -m "feat: add category usage statistic DAO query"
```

---

### Task 2: Expose Usage Statistics Through TransactionRepository

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt:1-78`
- Modify: `zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt:22-208`

- [ ] **Step 1: Add CategoryUsageStatistic and Criteria to TransactionRepository interface**

Add inside `TransactionRepository` interface, after the existing `Transaction` sealed interface:

```kotlin
data class CategoryUsageStatistic(
    val categoryId: Id.Known,
    val transactionCount: Int,
    val lastUsedDateTime: LocalDateTime,
)
```

Add to the `Criteria` sealed interface:

```kotlin
class CategoryUsageStatistics : Criteria<List<CategoryUsageStatistic>>
```

Update the `Noop` object — its `query` method already returns `emptyFlow()` for all criteria, so no change needed.

- [ ] **Step 2: Handle the new criteria in RoomTransactionRepository**

In `RoomTransactionRepository.query()`, add a new `when` branch inside `flatMapConcat`:

```kotlin
is TransactionRepository.Criteria.CategoryUsageStatistics -> transactionRoom()
    .selectCategoryUsageStatistic(userId.value)
    .map { entities ->
        entities.map { entity ->
            TransactionRepository.CategoryUsageStatistic(
                categoryId = Id.Known(entity.categoryId),
                transactionCount = entity.transactionCount,
                lastUsedDateTime = entity.lastUsedDateTime,
            )
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/transactions/TransactionRepository.kt \
       zero-database/src/main/java/com/hluhovskyi/zero/transactions/RoomTransactionRepository.kt
git commit -m "feat: expose category usage statistics through TransactionRepository"
```

---

### Task 3: Add RankSignal and queryRanked to CategoriesQueryUseCase Interface

**Files:**
- Modify: `zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt:1-22`

- [ ] **Step 1: Add RankSignal sealed class and queryRanked method**

Add imports and the new types. The full file should become:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>

    fun queryAll(): Flow<List<Category>>

    fun queryRanked(signals: Flow<RankSignal>): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    ) : Identifiable

    sealed class RankSignal {
        data class AccountChanged(val accountId: Id.Known?) : RankSignal()
        data class DateChanged(val date: LocalDate?) : RankSignal()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add zero-api/src/main/java/com/hluhovskyi/zero/categories/CategoriesQueryUseCase.kt
git commit -m "feat: add RankSignal and queryRanked to CategoriesQueryUseCase"
```

---

### Task 4: Implement queryRanked in DefaultCategoriesQueryUseCase

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt:1-69`
- Test: `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt`

- [ ] **Step 1: Write failing tests for ranking logic**

Create `zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt`:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlinx.coroutines.test.runTest
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultCategoriesQueryUseCaseTest {

    @Mock private lateinit var categoryRepository: CategoryRepository
    @Mock private lateinit var iconRepository: IconRepository
    @Mock private lateinit var colorRepository: ColorRepository
    @Mock private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setUp() {
        whenever(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(colorRepository.query(any<ColorRepository.Criteria<List<com.hluhovskyi.zero.colors.Color>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)
    }

    private fun createUseCase() = DefaultCategoriesQueryUseCase(
        categoryRepository = categoryRepository,
        iconRepository = iconRepository,
        colorRepository = colorRepository,
        transactionRepository = transactionRepository,
    )

    @Test
    fun `queryRanked sorts categories by frequency times recency`() = runTest {
        val catA = CategoryRepository.Category(
            id = Id.Known("a"), parentCategoryId = Id.Unknown,
            name = "A", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catB = CategoryRepository.Category(
            id = Id.Known("b"), parentCategoryId = Id.Unknown,
            name = "B", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catA, catB)))

        val now = LocalDateTime.now()
        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.CategoryUsageStatistic>>>(), any()))
            .thenReturn(flowOf(listOf(
                TransactionRepository.CategoryUsageStatistic(
                    categoryId = Id.Known("a"),
                    transactionCount = 5,
                    lastUsedDateTime = now.minusDays(60),
                ),
                TransactionRepository.CategoryUsageStatistic(
                    categoryId = Id.Known("b"),
                    transactionCount = 3,
                    lastUsedDateTime = now,
                ),
            )))

        val useCase = createUseCase()
        val result = useCase.queryRanked(emptyFlow()).first()

        // B should rank higher: used today (decay ~1.0) * 3 = ~3.0
        // A: used 60 days ago (decay ~0.135) * 5 = ~0.67
        assertEquals(Id.Known("b"), result[0].id)
        assertEquals(Id.Known("a"), result[1].id)
    }

    @Test
    fun `queryRanked puts unused categories at end sorted alphabetically`() = runTest {
        val catA = CategoryRepository.Category(
            id = Id.Known("a"), parentCategoryId = Id.Unknown,
            name = "Zebra", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catB = CategoryRepository.Category(
            id = Id.Known("b"), parentCategoryId = Id.Unknown,
            name = "Apple", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catC = CategoryRepository.Category(
            id = Id.Known("c"), parentCategoryId = Id.Unknown,
            name = "Cherry", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catA, catB, catC)))

        val now = LocalDateTime.now()
        // Only catC has usage
        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.CategoryUsageStatistic>>>(), any()))
            .thenReturn(flowOf(listOf(
                TransactionRepository.CategoryUsageStatistic(
                    categoryId = Id.Known("c"),
                    transactionCount = 1,
                    lastUsedDateTime = now,
                ),
            )))

        val useCase = createUseCase()
        val result = useCase.queryRanked(emptyFlow()).first()

        assertEquals("Cherry", result[0].name)  // used category first
        assertEquals("Apple", result[1].name)    // unused, alphabetical
        assertEquals("Zebra", result[2].name)    // unused, alphabetical
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.categories.DefaultCategoriesQueryUseCaseTest" --no-build-cache`
Expected: FAIL — `queryRanked` method doesn't exist yet.

- [ ] **Step 3: Implement queryRanked in DefaultCategoriesQueryUseCase**

Update `DefaultCategoriesQueryUseCase` to accept `TransactionRepository` and implement `queryRanked`:

```kotlin
package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.common.coroutines.onStartWithEmptyList
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.exp

internal class DefaultCategoriesQueryUseCase(
    private val categoryRepository: CategoryRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
    private val transactionRepository: TransactionRepository,
) : CategoriesQueryUseCase {

    private val queryAll = combine(
        categoryRepository.query(CategoryRepository.Criteria.All()),
        iconRepository.query(IconRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
        colorRepository.query(ColorRepository.Criteria.All())
            .onStartWithEmptyList()
            .onEmptyReturnEmptyList()
            .associateById(),
    ) { categories, idToIcons, idToColors ->
        categories.map { category ->
            resolve(
                category = category,
                idToIcons = idToIcons,
                idToColors = idToColors
            )
        }
    }

    override fun queryAll(): Flow<List<CategoriesQueryUseCase.Category>> = queryAll

    // TODO: Either share queryAll or use more specific queries
    override fun queryById(id: Id.Known): Flow<CategoriesQueryUseCase.Category> = queryAll
        .mapNotNull { categories -> categories.firstOrNull { it.id == id } }

    override fun queryRanked(
        signals: Flow<CategoriesQueryUseCase.RankSignal>,
    ): Flow<List<CategoriesQueryUseCase.Category>> = combine(
        queryAll,
        transactionRepository.query(TransactionRepository.Criteria.CategoryUsageStatistics()),
    ) { categories, usageStatistics ->
        rankCategories(categories, usageStatistics)
    }

    private fun rankCategories(
        categories: List<CategoriesQueryUseCase.Category>,
        usageStatistics: List<TransactionRepository.CategoryUsageStatistic>,
    ): List<CategoriesQueryUseCase.Category> {
        val statsById = usageStatistics.associateBy { it.categoryId }
        val now = LocalDateTime.now()

        val (used, unused) = categories.partition { statsById.containsKey(it.id) }

        val scored = used
            .map { category ->
                val stat = statsById.getValue(category.id)
                val daysSinceLastUse = Duration.between(stat.lastUsedDateTime, now).toDays().toDouble()
                val recencyDecay = exp(-daysSinceLastUse / DECAY_PERIOD_DAYS)
                val score = stat.transactionCount * recencyDecay
                category to score
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val alphabetical = unused.sortedBy { it.name }

        return scored + alphabetical
    }

    private fun resolve(
        category: CategoryRepository.Category,
        idToIcons: Map<Id.Known, Icon>,
        idToColors: Map<Id.Known, Color>
    ): CategoriesQueryUseCase.Category {
        val icon = idToIcons[category.iconId]
            ?: idToIcons[IconRepository.unknownCategoryIconId()]
            ?: Icon.empty()

        val color = idToColors[category.colorId]
            ?: idToColors[ColorRepository.unknownCategoryColorId()]

        val colorScheme = color?.let { colorRepository.schemeFor(it.id) }
            ?: colorRepository.schemeFor(ColorRepository.unknownCategoryColorId())

        return CategoriesQueryUseCase.Category(
            id = category.id,
            name = category.name,
            icon = icon.image,
            colorScheme = colorScheme,
        )
    }

    private companion object {
        const val DECAY_PERIOD_DAYS = 30.0
    }
}
```

Note: The `signals` parameter is accepted but not yet used in the `combine`. Phase 2 will add it as a third input to the `combine` to influence scoring based on account/date context.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :zero-core:testDebugUnitTest --tests "com.hluhovskyi.zero.categories.DefaultCategoriesQueryUseCaseTest" --no-build-cache`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCase.kt \
       zero-core/src/test/java/com/hluhovskyi/zero/categories/DefaultCategoriesQueryUseCaseTest.kt
git commit -m "feat: implement queryRanked with frequency * recency scoring"
```

---

### Task 5: Update DI Wiring

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt:40-48`
- Modify: `app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt:232-240`

- [ ] **Step 1: Update CategoryComponent.queryUseCase factory**

In `CategoryComponent.kt`, update the `queryUseCase` companion function to accept `TransactionRepository`:

```kotlin
fun queryUseCase(
    categoryRepository: CategoryRepository,
    iconRepository: IconRepository,
    colorRepository: ColorRepository,
    transactionRepository: TransactionRepository,
): CategoriesQueryUseCase = DefaultCategoriesQueryUseCase(
    categoryRepository = categoryRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    transactionRepository = transactionRepository,
)
```

Add the import at the top of the file:

```kotlin
import com.hluhovskyi.zero.transactions.TransactionRepository
```

- [ ] **Step 2: Update ApplicationComponent to pass TransactionRepository**

In `ApplicationComponent.kt`, update the `categoriesQueryUseCase` provider:

```kotlin
@Provides
@ApplicationScope
fun categoriesQueryUseCase(
    categoryRepository: CategoryRepository,
    iconRepository: IconRepository,
    colorRepository: ColorRepository,
    transactionRepository: TransactionRepository,
): CategoriesQueryUseCase = CategoryComponent.queryUseCase(
    categoryRepository = categoryRepository,
    iconRepository = iconRepository,
    colorRepository = colorRepository,
    transactionRepository = transactionRepository,
)
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/categories/CategoryComponent.kt \
       app/src/main/java/com/hluhovskyi/zero/ApplicationComponent.kt
git commit -m "feat: wire TransactionRepository into CategoriesQueryUseCase DI"
```

---

### Task 6: Switch DefaultTransactionEditUseCase to queryRanked

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt:318-349`

- [ ] **Step 1: Replace queryAll with queryRanked in the attach method**

Replace the category-loading `launch` block (lines 318-349) with:

```kotlin
launch {
    categoriesQueryUseCase.queryRanked(emptyFlow())
        .map { categories ->
            categories.map { category ->
                TransactionEditCategory(
                    id = category.id,
                    name = category.name,
                    colorScheme = category.colorScheme,
                    icon = category.icon,
                )
            }
        }
        .collectLatest { categories ->
            logger.d("attach, categories=${categories.joinIdsToString()}")
            mutableState.update { state ->
                state.copy(
                    categories = categories,
                    selectedCategory = if (state.selectedCategory != null) {
                        val updated =
                            categories.find { it.id == state.selectedCategory.id }
                        if (updated != state.selectedCategory) {
                            updated
                        } else {
                            state.selectedCategory
                        }
                    } else {
                        state.selectedCategory ?: categories.firstOrNull()
                    }
                )
            }
        }
}
```

Add import at the top:

```kotlin
import kotlinx.coroutines.flow.emptyFlow
```

Note: `emptyFlow()` is used for signals in Phase 1. Phase 2 will replace this with actual signal flows from form state changes.

- [ ] **Step 2: Add original category placement for edit mode**

In the existing block that loads the transaction for editing (inside the `if (transactionId is Id.Known)` block, around lines 229-282), after the `mutableState.update` that sets `selectedCategory`, we need to also reorder the categories list to place the original category first.

Find the `is TransactionRepository.Transaction.Expense` branch (line 242-251) and update both Expense and Income branches. In each branch, after `partialState.copy(...)`, the categories list should be reordered:

Replace the Expense branch:

```kotlin
is TransactionRepository.Transaction.Expense -> {
    val categoryToSelect =
        state.categories.firstOrNull { it.id == transaction.categoryId }

    val reorderedCategories = if (categoryToSelect != null) {
        listOf(categoryToSelect) + state.categories.filter { it.id != categoryToSelect.id }
    } else {
        state.categories
    }

    partialState.copy(
        transactionType = TransactionEditType.EXPENSE,
        categories = reorderedCategories,
        selectedCategory = categoryToSelect
            ?: state.selectedCategory,
        rate = transaction.rate.value.toString(),
    )
}
```

Replace the Income branch similarly:

```kotlin
is TransactionRepository.Transaction.Income -> {
    val categoryToSelect =
        state.categories.firstOrNull { it.id == transaction.categoryId }

    val reorderedCategories = if (categoryToSelect != null) {
        listOf(categoryToSelect) + state.categories.filter { it.id != categoryToSelect.id }
    } else {
        state.categories
    }

    partialState.copy(
        transactionType = TransactionEditType.INCOME,
        categories = reorderedCategories,
        selectedCategory = categoryToSelect
            ?: state.selectedCategory,
        rate = transaction.rate.value.toString()
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/DefaultTransactionEditUseCase.kt
git commit -m "feat: use ranked categories in transaction edit with original-first placement"
```

---

### Task 7: Add "Show All" Item to CategoryScrollRow

**Files:**
- Modify: `zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryScrollRow.kt:1-92`

- [ ] **Step 1: Add onShowAll parameter and "Show all" item**

Update `CategoryScrollRow` to accept an `onShowAll` callback and render the action item at position 0:

```kotlin
package com.hluhovskyi.zero.transactions.edit.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun CategoryScrollRow(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit,
    onShowAll: () -> Unit = {},
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        item(key = "show_all") {
            ShowAllItem(onClick = onShowAll)
        }
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
private fun ShowAllItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconView(
            color = MaterialTheme.colors.surface,
            size = 48.dp,
            contentPadding = 12.dp,
        ) {
            androidx.compose.material.Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "Show all categories",
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                tint = OnSurface,
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "All",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
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

- [ ] **Step 2: Verify callers compile — no changes needed**

Both `TransactionEditExpenseViewProvider.kt:73` and `TransactionEditIncomeViewProvider.kt:73` call `CategoryScrollRow` without `onShowAll`, which is fine since it has a default value of `{}`. No changes required in callers.

- [ ] **Step 3: Commit**

```bash
git add zero-core/src/main/java/com/hluhovskyi/zero/transactions/edit/common/CategoryScrollRow.kt
git commit -m "feat: add Show All action item to CategoryScrollRow"
```

---

### Task 8: Fix Existing Test

**Files:**
- Modify: `zero-core/src/test/java/com/hluhovskyi/zero/transactions/DefaultTransactionViewModelTest.kt:57`

- [ ] **Step 1: Update mock setup for queryRanked**

The existing test mocks `categoriesQueryUseCase.queryAll()` but now `DefaultTransactionViewModel` might also use `queryAll()`. Since we only changed `DefaultTransactionEditUseCase` to use `queryRanked`, check if `DefaultTransactionViewModel` is affected. Looking at `DefaultTransactionViewModelTest`, it uses `categoriesQueryUseCase.queryAll()` which is unchanged. No changes needed — verify by running:

Run: `./gradlew :zero-core:testDebugUnitTest --no-build-cache`
Expected: PASS (all existing tests plus new ones)

- [ ] **Step 2: If tests fail, fix accordingly and commit**

If `DefaultTransactionViewModelTest` fails due to the new `queryRanked` method needing a mock, add to `setUp()`:

```kotlin
whenever(categoriesQueryUseCase.queryRanked(any())).thenReturn(emptyFlow())
```

---

### Task 9: Build Verification

- [ ] **Step 1: Run full build**

Run: `./gradlew assembleDebug --no-build-cache`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew testDebugUnitTest --no-build-cache`
Expected: All tests PASS
