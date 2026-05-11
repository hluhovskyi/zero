package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultCategoriesQueryUseCaseTest {

    @Mock private lateinit var categoryRepository: CategoryRepository

    @Mock private lateinit var iconRepository: IconRepository

    @Mock private lateinit var colorRepository: ColorRepository

    @Mock private lateinit var transactionRepository: TransactionRepository

    // Fixed clock: 2024-06-01T12:00:00Z in UTC
    private val fixedInstant = Instant.parse("2024-06-01T12:00:00Z")
    private val testTimeZone = TimeZone.UTC
    private val fakeClock = object : Clock {
        override fun now() = fixedInstant
    }
    private val fakeZoneProvider = object : ZoneProvider {
        override fun timeZone() = testTimeZone
    }

    @Before
    fun setUp() {
        whenever(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(colorRepository.query(any<ColorRepository.Criteria<List<Color>>>()))
            .thenReturn(flowOf(emptyList()))
        whenever(colorRepository.schemeFor(any())).thenReturn(ColorScheme.Grey)
    }

    private fun createUseCase() = DefaultCategoriesQueryUseCase(
        categoryRepository = categoryRepository,
        iconRepository = iconRepository,
        colorRepository = colorRepository,
        transactionRepository = transactionRepository,
        clock = fakeClock,
        zoneProvider = fakeZoneProvider,
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

        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.CategoryUsageStatistic>>>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(
                            categoryId = Id.Known("a"),
                            transactionCount = 5,
                            lastUsedDateTime = LocalDateTime(2024, 4, 2, 12, 0, 0), // 60 days before fixedInstant
                        ),
                        TransactionRepository.CategoryUsageStatistic(
                            categoryId = Id.Known("b"),
                            transactionCount = 3,
                            lastUsedDateTime = LocalDateTime(2024, 6, 1, 12, 0, 0), // same as fixedInstant (0 days)
                        ),
                    ),
                ),
            )

        val useCase = createUseCase()
        val result = useCase.queryRanked(emptyFlow()).first()

        // B: decay ~1.0 * 3 = ~3.0; A: decay ~exp(-2) * 5 = ~0.68
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

        whenever(transactionRepository.query(any<TransactionRepository.Criteria<List<TransactionRepository.CategoryUsageStatistic>>>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(
                            categoryId = Id.Known("c"),
                            transactionCount = 1,
                            lastUsedDateTime = LocalDateTime(2024, 6, 1, 12, 0, 0),
                        ),
                    ),
                ),
            )

        val useCase = createUseCase()
        val result = useCase.queryRanked(emptyFlow()).first()

        assertEquals("Cherry", result[0].name)
        assertEquals("Apple", result[1].name)
        assertEquals("Zebra", result[2].name)
    }
}
