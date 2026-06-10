package com.hluhovskyi.zero.transactions.breakdown

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class DefaultSpendingBreakdownUseCaseTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    private val identityConvert = object : CurrencyConvertUseCase {
        override suspend fun getRate(fromId: Id.Known, toId: Id.Known) = Rate(BigDecimal.ONE)
        override suspend fun convertToPrimary(amount: Amount, currencyId: Id.Known) = amount
    }

    // Filter is irrelevant (repository mocked); trendSince = Mar 1 splits recent (>=) vs prior.
    private val filter = TransactionFilterCriteria()
    private val trendSince = LocalDate(2026, 3, 1)

    private fun useCase() = DefaultSpendingBreakdownUseCase(
        transactionRepository = transactionRepository,
        categoriesQueryUseCase = categoriesQueryUseCase,
        currencyConvertUseCase = identityConvert,
    )

    private fun stub(transactions: List<TransactionRepository.Transaction>, categories: List<CategoriesQueryUseCase.Category>) {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.Filtered>(), any()))
            .thenReturn(flowOf(transactions))
        whenever(categoriesQueryUseCase.queryAll()).thenReturn(flowOf(categories))
    }

    @Test
    fun `ranks expense categories by spend with recent-vs-prior halves and totals`() = runTest {
        stub(
            transactions = listOf(
                expense("food", "100", "2026-01-10"), // prior (before Mar 1)
                expense("food", "50", "2026-03-05"), // recent
                expense("rent", "300", "2026-02-01"), // prior
                income("1000", "2026-01-15"),
                transfer("200", "2026-02-10"),
            ),
            categories = listOf(category("food"), category("rent")),
        )

        val breakdown = useCase().query(filter, trendSince).last()

        assertEquals(listOf(Id.Known("rent"), Id.Known("food")), breakdown.categories.map { it.categoryId })
        assertEquals(0, BigDecimal("450").compareTo(breakdown.total.value))
        assertEquals(3, breakdown.transactionCount)
        val food = breakdown.categories.first { it.categoryId == Id.Known("food") }
        assertEquals(0, BigDecimal("150").compareTo(food.amount.value))
        assertEquals(2, food.transactionCount)
        assertEquals(0, BigDecimal("50").compareTo(food.recentAmount.value))
        assertEquals(0, BigDecimal("100").compareTo(food.priorAmount.value))
    }

    @Test
    fun `excludes unknown categories and income, categoryCount counts expense categories`() = runTest {
        stub(
            transactions = listOf(
                expense("food", "100", "2026-01-10"),
                expense("ghost", "999", "2026-02-02"), // category not in the list
                income("1000", "2026-01-15"),
            ),
            categories = listOf(category("food"), category("salary", CategoryType.INCOME)),
        )

        val breakdown = useCase().query(filter, trendSince).last()

        assertEquals(listOf(Id.Known("food")), breakdown.categories.map { it.categoryId })
        assertEquals(0, BigDecimal("100").compareTo(breakdown.total.value))
        assertEquals(1, breakdown.categoryCount) // only "food" is an expense category
    }

    @Test
    fun `leaves recent and prior at zero when no trend split is requested`() = runTest {
        stub(
            transactions = listOf(expense("food", "100", "2026-01-10")),
            categories = listOf(category("food")),
        )

        val food = useCase().query(filter, trendSince = null).last().categories.single()

        assertEquals(Amount.zero(), food.recentAmount)
        assertEquals(Amount.zero(), food.priorAmount)
    }

    private fun expense(categoryId: String, amount: String, date: String) = TransactionRepository.Transaction.Expense(
        id = Id.Known("e-$categoryId-$date"),
        amount = Amount(BigDecimal(amount)),
        accountId = Id.Known("acc"),
        currencyId = Id.Known("usd"),
        dateTime = LocalDateTime.parse("${date}T10:00:00"),
        updatedDateTime = LocalDateTime.parse("${date}T10:00:00"),
        categoryId = Id.Known(categoryId),
        rate = Rate(BigDecimal.ONE),
    )

    private fun income(amount: String, date: String) = TransactionRepository.Transaction.Income(
        id = Id.Known("i-$date"),
        amount = Amount(BigDecimal(amount)),
        accountId = Id.Known("acc"),
        currencyId = Id.Known("usd"),
        dateTime = LocalDateTime.parse("${date}T10:00:00"),
        updatedDateTime = LocalDateTime.parse("${date}T10:00:00"),
        categoryId = Id.Known("salary"),
        rate = Rate(BigDecimal.ONE),
    )

    private fun transfer(amount: String, date: String) = TransactionRepository.Transaction.Transfer(
        id = Id.Known("t-$date"),
        amount = Amount(BigDecimal(amount)),
        accountId = Id.Known("acc"),
        currencyId = Id.Known("usd"),
        dateTime = LocalDateTime.parse("${date}T10:00:00"),
        updatedDateTime = LocalDateTime.parse("${date}T10:00:00"),
        targetAccount = Id.Known("acc2"),
        targetAmount = Amount(BigDecimal(amount)),
    )

    private fun category(id: String, type: CategoryType = CategoryType.EXPENSE) = CategoriesQueryUseCase.Category(
        id = Id.Known(id),
        name = "Cat $id",
        icon = Image.empty(),
        colorScheme = ColorScheme.Grey,
        type = type,
    )
}
