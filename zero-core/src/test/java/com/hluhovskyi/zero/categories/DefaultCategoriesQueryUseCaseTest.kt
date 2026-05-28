package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.Color
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.config.ConfigurationKey
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class DefaultCategoriesQueryUseCaseTest {

    @Mock private lateinit var categoryRepository: CategoryRepository

    @Mock private lateinit var iconRepository: IconRepository

    @Mock private lateinit var colorRepository: ColorRepository

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var configurationRepository: ConfigurationRepository

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
        whenever(
            transactionRepository.query(
                any<TransactionRepository.Criteria.CategoryAmountStatistics>(),
                any(),
            ),
        ).thenReturn(flowOf(emptyList()))
        whenever(configurationRepository.observe(any<ConfigurationKey<Boolean>>(), any()))
            .thenReturn(flowOf(true))
    }

    private fun createUseCase() = DefaultCategoriesQueryUseCase(
        categoryRepository = categoryRepository,
        iconRepository = iconRepository,
        colorRepository = colorRepository,
        transactionRepository = transactionRepository,
        configurationRepository = configurationRepository,
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

        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
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

        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
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

    @Test
    fun `queryRanked boosts categories used with selected account`() = runTest {
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

        val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(Id.Known("a"), 3, recentDate),
                        TransactionRepository.CategoryUsageStatistic(Id.Known("b"), 3, recentDate),
                    ),
                ),
            )
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatisticsByAccount>(), any()))
            .thenReturn(
                flowOf(
                    listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("b"), 2, recentDate)),
                ),
            )

        val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
            CategoriesQueryUseCase.RankSignal.AccountChanged(Id.Known("acc-1")),
        )

        val result = createUseCase().queryRanked(signals).first()
        assertEquals(Id.Known("b"), result[0].id)
        assertEquals(Id.Known("a"), result[1].id)
    }

    @Test
    fun `queryRanked boosts categories used in same month`() = runTest {
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

        val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(Id.Known("a"), 3, recentDate),
                        TransactionRepository.CategoryUsageStatistic(Id.Known("b"), 3, recentDate),
                    ),
                ),
            )
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatisticsByMonth>(), any()))
            .thenReturn(
                flowOf(
                    listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("a"), 3, recentDate)),
                ),
            )

        val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
            CategoriesQueryUseCase.RankSignal.DateChanged(LocalDate(2024, 6, 15)),
        )

        val result = createUseCase().queryRanked(signals).first()
        assertEquals(Id.Known("a"), result[0].id)
        assertEquals(Id.Known("b"), result[1].id)
    }

    @Test
    fun `queryRanked boosts categories close to entered amount on log scale`() = runTest {
        val catSmall = CategoryRepository.Category(
            id = Id.Known("small"), parentCategoryId = Id.Unknown,
            name = "Small", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catLarge = CategoryRepository.Category(
            id = Id.Known("large"), parentCategoryId = Id.Unknown,
            name = "Large", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catSmall, catLarge)))

        val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(Id.Known("small"), 2, recentDate),
                        TransactionRepository.CategoryUsageStatistic(Id.Known("large"), 2, recentDate),
                    ),
                ),
            )
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryAmountStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryAmountStatistic(Id.Known("small"), BigDecimal("6.00")),
                        TransactionRepository.CategoryAmountStatistic(Id.Known("large"), BigDecimal("500.00")),
                    ),
                ),
            )

        val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
            CategoriesQueryUseCase.RankSignal.AmountChanged(BigDecimal("5.00")),
        )

        val result = createUseCase().queryRanked(signals).first()
        assertEquals(Id.Known("small"), result[0].id)
        assertEquals(Id.Known("large"), result[1].id)
    }

    @Test
    fun `queryRanked combines all signals multiplicatively`() = runTest {
        val catMatch = CategoryRepository.Category(
            id = Id.Known("match"), parentCategoryId = Id.Unknown,
            name = "Match", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catBase = CategoryRepository.Category(
            id = Id.Known("base"), parentCategoryId = Id.Unknown,
            name = "Base", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catMatch, catBase)))

        val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
        // catBase has HIGHER base usage to confirm signals can flip the ranking.
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate),
                        TransactionRepository.CategoryUsageStatistic(Id.Known("base"), 3, recentDate),
                    ),
                ),
            )
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatisticsByAccount>(), any()))
            .thenReturn(
                flowOf(
                    listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate)),
                ),
            )
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatisticsByMonth>(), any()))
            .thenReturn(
                flowOf(
                    listOf(TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate)),
                ),
            )
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryAmountStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryAmountStatistic(Id.Known("match"), BigDecimal("10.00")),
                        TransactionRepository.CategoryAmountStatistic(Id.Known("base"), BigDecimal("500.00")),
                    ),
                ),
            )

        val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
            CategoriesQueryUseCase.RankSignal.AccountChanged(Id.Known("acc-1")),
            CategoriesQueryUseCase.RankSignal.DateChanged(LocalDate(2024, 6, 15)),
            CategoriesQueryUseCase.RankSignal.AmountChanged(BigDecimal("10.00")),
        )

        val result = createUseCase().queryRanked(signals).first()
        assertEquals(Id.Known("match"), result[0].id)
        assertEquals(Id.Known("base"), result[1].id)
    }

    @Test
    fun `queryRanked ignores signals when ranking signals are disabled in config`() = runTest {
        whenever(configurationRepository.observe(any<ConfigurationKey<Boolean>>(), any()))
            .thenReturn(flowOf(false))

        val catMatch = CategoryRepository.Category(
            id = Id.Known("match"), parentCategoryId = Id.Unknown,
            name = "Match", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        val catBase = CategoryRepository.Category(
            id = Id.Known("base"), parentCategoryId = Id.Unknown,
            name = "Base", iconId = Id.Unknown, colorId = Id.Unknown,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(catMatch, catBase)))

        // Same setup as the multiplicative-signals test: catBase has higher base usage.
        val recentDate = LocalDateTime(2024, 6, 1, 12, 0, 0)
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.CategoryUsageStatistics>(), any()))
            .thenReturn(
                flowOf(
                    listOf(
                        TransactionRepository.CategoryUsageStatistic(Id.Known("match"), 2, recentDate),
                        TransactionRepository.CategoryUsageStatistic(Id.Known("base"), 3, recentDate),
                    ),
                ),
            )

        // Even with signals that would normally boost catMatch, the disabled config keeps base ranking.
        val signals = flowOf<CategoriesQueryUseCase.RankSignal>(
            CategoriesQueryUseCase.RankSignal.AccountChanged(Id.Known("acc-1")),
            CategoriesQueryUseCase.RankSignal.DateChanged(LocalDate(2024, 6, 15)),
            CategoriesQueryUseCase.RankSignal.AmountChanged(BigDecimal("10.00")),
        )

        val result = createUseCase().queryRanked(signals).first()
        assertEquals(Id.Known("base"), result[0].id)
        assertEquals(Id.Known("match"), result[1].id)
    }
}
