package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.first
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
class DefaultAnalyticsUseCaseTest {

    @Mock private lateinit var transactionRepository: TransactionRepository

    @Mock private lateinit var categoriesQueryUseCase: CategoriesQueryUseCase

    // Single currency: conversion is identity, so amounts pass through unchanged.
    private val identityConvert = object : CurrencyConvertUseCase {
        override suspend fun getRate(fromId: Id.Known, toId: Id.Known) = Rate(BigDecimal.ONE)
        override suspend fun convertToPrimary(amount: Amount, currencyId: Id.Known) = amount
    }

    // Jan 1 – Apr 30: buckets Jan/Feb/Mar/Apr; midpoint = Mar 1 (recent = on/after Mar 1).
    private val range = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 4, 30))

    private fun useCase() = DefaultAnalyticsUseCase(
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
    fun `cash flow has one labelled bucket per month, chronological`() = runTest {
        stub(transactions = listOf(expense("food", "100", "2026-01-10")), categories = listOf(category("food")))

        val cashFlow = useCase().query(range).first().cashFlow

        assertEquals(listOf("Jan", "Feb", "Mar", "Apr"), cashFlow.map { it.label })
    }

    @Test
    fun `buckets sum income and expense per month and ignore transfers, empty month is zero`() = runTest {
        stub(
            transactions = listOf(
                income("1000", "2026-01-15"),
                expense("food", "100", "2026-01-10"),
                expense("rent", "300", "2026-02-01"),
                income("1000", "2026-03-01"),
                expense("food", "50", "2026-03-05"),
                transfer("200", "2026-02-10"),
            ),
            categories = listOf(category("food"), category("rent")),
        )

        val analytics = useCase().query(range).first()
        val byMonth = analytics.cashFlow.associateBy { it.label }

        assertEquals(0, BigDecimal("1000").compareTo(byMonth.getValue("Jan").income.value))
        assertEquals(0, BigDecimal("100").compareTo(byMonth.getValue("Jan").expense.value))
        assertEquals(0, BigDecimal.ZERO.compareTo(byMonth.getValue("Feb").income.value))
        assertEquals(0, BigDecimal("300").compareTo(byMonth.getValue("Feb").expense.value))
        assertEquals(0, BigDecimal.ZERO.compareTo(byMonth.getValue("Apr").income.value))
        assertEquals(0, BigDecimal.ZERO.compareTo(byMonth.getValue("Apr").expense.value))
        assertEquals(0, BigDecimal("2000").compareTo(analytics.totalIn.value))
        assertEquals(0, BigDecimal("450").compareTo(analytics.totalOut.value))
    }

    @Test
    fun `breakdown ranks expense categories by spend with recent-vs-prior halves`() = runTest {
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

        val breakdown = useCase().query(range).last().breakdown

        assertEquals(listOf(Id.Known("rent"), Id.Known("food")), breakdown.map { it.categoryId })
        val food = breakdown.first { it.categoryId == Id.Known("food") }
        assertEquals(0, BigDecimal("150").compareTo(food.amount.value))
        assertEquals(2, food.transactionCount)
        assertEquals(0, BigDecimal("50").compareTo(food.recentAmount.value))
        assertEquals(0, BigDecimal("100").compareTo(food.priorAmount.value))
    }

    @Test
    fun `breakdown excludes unknown categories and income, categoryCount counts expense categories`() = runTest {
        stub(
            transactions = listOf(
                expense("food", "100", "2026-01-10"),
                expense("ghost", "999", "2026-02-02"), // category not in the list
                income("1000", "2026-01-15"),
            ),
            categories = listOf(category("food"), category("salary", CategoryType.INCOME)),
        )

        val analytics = useCase().query(range).last()

        assertEquals(listOf(Id.Known("food")), analytics.breakdown.map { it.categoryId })
        assertEquals(1, analytics.categoryCount) // only "food" is an expense category
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
