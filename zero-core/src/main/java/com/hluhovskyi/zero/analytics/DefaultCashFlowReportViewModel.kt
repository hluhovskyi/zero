package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.BaseViewModel
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Projects the [CashFlowReportUseCase] over the last 6 months (and the prior 6 for period-over-period)
 * into the report's view state. Hardcodes the range like the Analytics hub does, and does all the
 * shaping math (net, savings rate + per-month trend, income shares, deltas) so the view only formats.
 */
internal class DefaultCashFlowReportViewModel(
    private val cashFlowReportUseCase: CashFlowReportUseCase,
    private val currencyPrimaryUseCase: CurrencyPrimaryUseCase,
    private val onBackHandler: OnBackHandler,
    private val zonedClock: ZonedClock,
    private val dispatchers: DispatcherProvider,
) : BaseViewModel(dispatchers),
    CashFlowReportViewModel {

    private val mutableState = MutableStateFlow(CashFlowReportViewModel.State())
    override val state: Flow<CashFlowReportViewModel.State> = mutableState

    override fun perform(action: CashFlowReportViewModel.Action) {
        when (action) {
            CashFlowReportViewModel.Action.Back -> scope.launch(dispatchers.main()) { onBackHandler.onBack() }
        }
    }

    override fun attachOnMain() {
        scope.launch(dispatchers.io()) {
            val currencySymbol = currencyPrimaryUseCase.getPrimaryCurrency().symbol
            mutableState.update { it.copy(currencySymbol = currencySymbol) }
            val (current, prior) = ranges()
            cashFlowReportUseCase.query(current, prior).collectLatest { report ->
                mutableState.update { it.copy(report = project(report)) }
            }
        }
    }

    private fun project(report: CashFlowReportUseCase.Report): CashFlowReportViewModel.Report {
        val net = report.totalIn - report.totalOut
        val savingsRate = savingsRate(report.totalIn, report.totalOut)
        val months = report.months.map {
            CashFlowReportViewModel.MonthBar(it.label, it.income.value.toFloat(), it.expense.value.toFloat())
        }
        val savingsTrend = report.months.map { monthlySavingsRate(it).toFloat() }
        val priorSavingsRate = savingsRate(report.priorTotalIn, report.priorTotalOut)
        return CashFlowReportViewModel.Report(
            net = net,
            totalIn = report.totalIn,
            totalOut = report.totalOut,
            savingsRate = savingsRate,
            months = months,
            latest = months.lastOrNull(),
            savingsTrend = savingsTrend,
            savingsRateMin = savingsTrend.minOrNull()?.toInt() ?: 0,
            savingsRateMax = savingsTrend.maxOrNull()?.toInt() ?: 0,
            incomeSources = report.incomeSources.map {
                CashFlowReportViewModel.IncomeShare(
                    name = it.name,
                    icon = it.icon,
                    colorScheme = it.colorScheme,
                    amount = it.amount,
                    sharePercent = sharePercent(it.amount, report.totalIn),
                )
            },
            moneyIn = delta(report.totalIn, report.priorTotalIn),
            moneyOut = delta(report.totalOut, report.priorTotalOut),
            savingsRateChange = rateChange(savingsRate, priorSavingsRate),
        )
    }

    private fun savingsRate(totalIn: Amount, totalOut: Amount): Int = if (totalIn > 0L) (((totalIn - totalOut) / totalIn) * PERCENT).roundToInt() else 0

    private fun monthlySavingsRate(month: CashFlowReportUseCase.MonthlyCashFlow): Int = if (month.income > 0L) (((month.income - month.expense) / month.income) * PERCENT).roundToInt() else 0

    private fun sharePercent(amount: Amount, totalIn: Amount): Int = if (totalIn > 0L) ((amount / totalIn) * PERCENT).roundToInt() else 0

    private fun delta(now: Amount, prior: Amount): CashFlowReportViewModel.Delta {
        val isPositive = now >= prior
        val magnitude = if (isPositive) now - prior else prior - now
        return CashFlowReportViewModel.Delta(now = now, magnitude = magnitude, isPositive = isPositive)
    }

    private fun rateChange(now: Int, prior: Int): CashFlowReportViewModel.RateChange = CashFlowReportViewModel.RateChange(nowPercent = now, magnitudePoints = abs(now - prior), isPositive = now >= prior)

    private fun ranges(): Pair<DateRange, DateRange> {
        val today = zonedClock.localDateTime().date
        val currentStart = LocalDate(today.year, today.monthNumber, 1).minus(MONTHS_BACK, DateTimeUnit.MONTH)
        val priorStart = currentStart.minus(MONTHS_IN_WINDOW, DateTimeUnit.MONTH)
        val priorEnd = currentStart.minus(1, DateTimeUnit.DAY)
        return DateRange(start = currentStart, end = today) to DateRange(start = priorStart, end = priorEnd)
    }

    private companion object {
        const val MONTHS_BACK = 5 // current month + 5 prior = 6 buckets
        const val MONTHS_IN_WINDOW = 6
        const val PERCENT = 100.0
    }
}
