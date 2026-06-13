package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.transactions.TransactionFilterCriteria
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal

class DefaultAnalyticsDetailUseCaseTest {

    private val range = DateRange(LocalDate(2026, 1, 1), LocalDate(2026, 4, 30))

    // Cash flow is the shared use case's job; here it's a fixed list we compose over.
    private val cashFlow = listOf(
        bucket("Jan", income = "1000", expense = "100"),
        bucket("Feb", income = "0", expense = "300"),
        bucket("Mar", income = "1000", expense = "50"),
        bucket("Apr", income = "0", expense = "0"),
    )
    private val monthlyCashFlowUseCase = object : MonthlyCashFlowUseCase {
        override fun query(range: DateRange) = flowOf(cashFlow)
    }

    private var breakdown = emptyBreakdown()
    private val spendingBreakdownUseCase = object : SpendingBreakdownUseCase {
        override fun query(filter: TransactionFilterCriteria, trendSince: LocalDate?): Flow<SpendingBreakdownUseCase.Breakdown> = flowOf(breakdown)
    }

    private fun useCase() = DefaultAnalyticsDetailUseCase(
        monthlyCashFlowUseCase = monthlyCashFlowUseCase,
        spendingBreakdownUseCase = spendingBreakdownUseCase,
    )

    @Test
    fun `totals fold income and expense across buckets, cash flow passes through`() = runTest {
        val analytics = useCase().query(range).first()

        assertEquals(0, BigDecimal("2000").compareTo(analytics.totalIn.value))
        assertEquals(0, BigDecimal("450").compareTo(analytics.totalOut.value))
        assertSame(cashFlow, analytics.cashFlow)
    }

    @Test
    fun `breakdown is passed through from the spending breakdown use case`() = runTest {
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

    private fun bucket(label: String, income: String, expense: String) = MonthlyCashFlowUseCase.MonthBucket(
        label = label,
        income = Amount(BigDecimal(income)),
        expense = Amount(BigDecimal(expense)),
    )
}
