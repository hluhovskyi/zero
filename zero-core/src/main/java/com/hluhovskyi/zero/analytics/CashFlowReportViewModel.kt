package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface CashFlowReportViewModel : AttachableActionStateModel<CashFlowReportViewModel.Action, CashFlowReportViewModel.State> {

    sealed interface Action {
        data object Back : Action
    }

    data class State(
        val currencySymbol: String = "",
        val report: Report? = null,
    )

    /** Everything the report renders, pre-shaped. Amounts are formatted by the view; percents/floats are final. */
    data class Report(
        val net: Amount,
        val totalIn: Amount,
        val totalOut: Amount,
        val savingsRate: Int,
        val months: List<MonthBar>,
        val latest: MonthBar?,
        val savingsTrend: List<Float>,
        val savingsRateMin: Int,
        val savingsRateMax: Int,
        val incomeSources: List<IncomeShare>,
        val moneyIn: Delta,
        val moneyOut: Delta,
        val savingsRateChange: RateChange,
    )

    data class MonthBar(val label: String, val income: Float, val expense: Float)

    data class IncomeShare(
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val amount: Amount,
        val sharePercent: Int,
    )

    /** Money period-over-period change: [magnitude] is the absolute delta; [isPositive] = current ≥ prior. */
    data class Delta(val now: Amount, val magnitude: Amount, val isPositive: Boolean)

    /** Savings-rate change in percentage points. */
    data class RateChange(val nowPercent: Int, val magnitudePoints: Int, val isPositive: Boolean)

    object Noop : CashFlowReportViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
