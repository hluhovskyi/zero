// Cash-flow report mock data + projection. Swatch colors are entity colors (not theme roles),
// so this file is ZeroThemeBypass-exempt like ChartMockData.
@file:Suppress("ZeroThemeBypass")

package com.hluhovskyi.zero.ui.chart

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

@Immutable
internal data class CashflowReport(
    val net: Float,
    val totalIn: Float,
    val totalOut: Float,
    val savingsRate: Int,
    val months: List<MonthlyFlow>,
    val latest: MonthlyFlow,
    val savingsTrend: List<Float>,
    val savingsRateMin: Int,
    val savingsRateMax: Int,
    val incomeSources: List<IncomeShare>,
    val comparisons: List<PeriodComparison>,
)

@Immutable
internal data class MonthlyFlow(val label: String, val moneyIn: Float, val moneyOut: Float)

@Immutable
internal data class IncomeShare(val name: String, val amount: Float, val sharePercent: Int, val swatch: Color)

@Immutable
internal data class PeriodComparison(val label: String, val nowValue: Float, val delta: Float, val isMoney: Boolean)

// Numbers straight from the Analytics design (CashflowReport in analytics-screens.jsx). Swatches are
// dark-mapped entity tones (pastel-on-dark), matching how ChartMockData carries category colors.
private val INCOME = listOf(
    Triple("Salary", 28800f, Color(0xFF7FD18C)),
    Triple("Freelance", 1800f, Color(0xFF9CC0FF)),
    Triple("Interest", 500f, Color(0xFFBCAAA4)),
)
private const val PRIOR_IN = 29400f
private const val PRIOR_OUT = 24100f
private const val PRIOR_SAVINGS_RATE = 18

/** Projects the fixed mock data into the cash-flow report UI model. All arithmetic lives here. */
internal fun cashflowReport(): CashflowReport {
    val months = ChartMockData.flow6.map { MonthlyFlow(it.first, it.second, it.third) }
    val totalIn = months.sumOf { it.moneyIn.toDouble() }.toFloat()
    val totalOut = months.sumOf { it.moneyOut.toDouble() }.toFloat()
    val net = totalIn - totalOut
    val savingsRate = (net / totalIn * 100).roundToInt()
    val savingsTrend = months.map { ((it.moneyIn - it.moneyOut) / it.moneyIn * 100).roundToInt().toFloat() }
    val incomeSources = INCOME.map { (name, amount, swatch) ->
        IncomeShare(name, amount, (amount / totalIn * 100).roundToInt(), swatch)
    }
    val comparisons = listOf(
        PeriodComparison("Money in", totalIn, totalIn - PRIOR_IN, isMoney = true),
        PeriodComparison("Money out", totalOut, totalOut - PRIOR_OUT, isMoney = true),
        PeriodComparison(
            label = "Savings rate",
            nowValue = savingsRate.toFloat(),
            delta = (savingsRate - PRIOR_SAVINGS_RATE).toFloat(),
            isMoney = false,
        ),
    )
    return CashflowReport(
        net = net,
        totalIn = totalIn,
        totalOut = totalOut,
        savingsRate = savingsRate,
        months = months,
        latest = months.last(),
        savingsTrend = savingsTrend,
        savingsRateMin = savingsTrend.min().toInt(),
        savingsRateMax = savingsTrend.max().toInt(),
        incomeSources = incomeSources,
        comparisons = comparisons,
    )
}
