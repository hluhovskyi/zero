package com.hluhovskyi.zero.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class CashflowReportTest {

    private val report = cashflowReport()

    @Test
    fun `totals are sums of the 6-month flow`() {
        assertEquals(31100f, report.totalIn)
        assertEquals(24290f, report.totalOut)
        assertEquals(6810f, report.net)
    }

    @Test
    fun `savings rate is net over income, rounded`() {
        assertEquals(22, report.savingsRate)
    }

    @Test
    fun `savings trend is per-month savings rate, oldest to newest`() {
        assertEquals(listOf(21f, 14f, 28f, 23f, 21f, 26f), report.savingsTrend)
        assertEquals(14, report.savingsRateMin)
        assertEquals(28, report.savingsRateMax)
    }

    @Test
    fun `latest month is the newest flow row`() {
        assertEquals("Apr", report.latest.label)
        assertEquals(5050f, report.latest.moneyIn)
        assertEquals(3760f, report.latest.moneyOut)
    }

    @Test
    fun `income shares are each source over total income, rounded`() {
        assertEquals(listOf("Salary", "Freelance", "Interest"), report.incomeSources.map { it.name })
        assertEquals(listOf(93, 6, 2), report.incomeSources.map { it.sharePercent })
    }

    @Test
    fun `comparisons are now minus prior, flagged money vs points`() {
        assertEquals(
            listOf("Money in" to 1700f, "Money out" to 190f, "Savings rate" to 4f),
            report.comparisons.map { it.label to it.delta },
        )
        assertEquals(listOf(true, true, false), report.comparisons.map { it.isMoney })
        assertEquals(listOf(31100f, 24290f, 22f), report.comparisons.map { it.nowValue })
    }
}
