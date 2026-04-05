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
