package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    // Single currency: conversion is identity, so amounts pass through unchanged.
    private val identityConvert = object : CurrencyConvertUseCase {
        override suspend fun getRate(fromId: Id.Known, toId: Id.Known) = Rate(BigDecimal.ONE)
        override suspend fun convertToPrimary(amount: Amount, currencyId: Id.Known) = amount
    }

    // The category breakdown is the shared use case's job; here it's a stub we pass through.
    private var breakdown = emptyBreakdown()
    private val spendingBreakdownUseCase = object : SpendingBreakdownUseCase {
        override fun query(criteria: TransactionRepository.Criteria.Filtered, trendSince: LocalDate?): Flow<SpendingBreakdownUseCase.Breakdown> = flowOf(breakdown)
    }

    // Jan 1 – Apr 30: buckets Jan/Feb/Mar/Apr.
    private val range = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 4, 30))

    private fun useCase() = DefaultAnalyticsUseCase(
        transactionRepository = transactionRepository,
        currencyConvertUseCase = identityConvert,
        spendingBreakdownUseCase = spendingBreakdownUseCase,
    )

    private fun stub(transactions: List<TransactionRepository.Transaction>) {
        whenever(transactionRepository.query(any<TransactionRepository.Criteria.Filtered>(), any()))
            .thenReturn(flowOf(transactions))
    }

    @Test
    fun `cash flow has one labelled bucket per month, chronological`() = runTest {
        stub(transactions = listOf(expense("100", "2026-01-10")))

        val cashFlow = useCase().query(range).first().cashFlow

        assertEquals(listOf("Jan", "Feb", "Mar", "Apr"), cashFlow.map { it.label })
    }

    @Test
    fun `buckets sum income and expense per month and ignore transfers, empty month is zero`() = runTest {
        stub(
            transactions = listOf(
                income("1000", "2026-01-15"),
                expense("100", "2026-01-10"),
                expense("300", "2026-02-01"),
                income("1000", "2026-03-01"),
                expense("50", "2026-03-05"),
                transfer("200", "2026-02-10"),
            ),
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
    fun `breakdown is passed through from the spending breakdown use case`() = runTest {
        stub(transactions = emptyList())
        breakdown = SpendingBreakdownUseCase.Breakdown(
            total = Amount(BigDecimal("141")),
            transactionCount = 2,
            categoryCount = 3,
            categories = emptyList(),
        )

        val result = useCase().query(range).first().breakdown

        assertSame(breakdown, result)
    }

    private fun emptyBreakdown() = SpendingBreakdownUseCase.Breakdown(Amount.zero(), 0, 0, emptyList())

    private fun expense(amount: String, date: String) = TransactionRepository.Transaction.Expense(
        id = Id.Known("e-$date"),
        amount = Amount(BigDecimal(amount)),
        accountId = Id.Known("acc"),
        currencyId = Id.Known("usd"),
        dateTime = LocalDateTime.parse("${date}T10:00:00"),
        updatedDateTime = LocalDateTime.parse("${date}T10:00:00"),
        categoryId = Id.Known("food"),
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
}
