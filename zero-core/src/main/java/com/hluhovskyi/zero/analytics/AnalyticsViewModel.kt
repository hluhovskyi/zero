package com.hluhovskyi.zero.analytics

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

interface AnalyticsViewModel : AttachableActionStateModel<AnalyticsViewModel.Action, AnalyticsViewModel.State> {

    sealed interface Action {
        data object SeeAllCategories : Action
        data class SelectCategory(val categoryId: Id.Known) : Action
    }

    data class State(
        val currencySymbol: String = "",
        val cashFlow: CashFlow? = null,
        val breakdown: Breakdown? = null,
    )

    /** Cash-flow hero: net + in/out totals and the monthly bars. */
    data class CashFlow(
        val net: Amount,
        val totalIn: Amount,
        val totalOut: Amount,
        val bars: List<Bar>,
    )

    data class Bar(val label: String, val income: Float, val expense: Float)

    /** Category breakdown: donut + legend + ranked rows. */
    data class Breakdown(
        val totalSpent: Amount,
        val donut: List<Slice>,
        val legend: List<LegendItem>,
        val rows: List<Row>,
        val categoryCount: Int,
    )

    /** [colorScheme] null marks the aggregated "Other" slice. */
    data class Slice(val colorScheme: ColorScheme?, val value: Float)

    /** [name] null marks the aggregated "Other" entry. */
    data class LegendItem(val colorScheme: ColorScheme?, val name: String?, val sharePercent: Int)

    data class Row(
        val categoryId: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val amount: Amount,
        val sharePercent: Int,
        val barFraction: Float,
        val trend: Trend,
    )

    sealed interface Trend {
        data object New : Trend
        data object Flat : Trend
        data class Up(val percent: Int) : Trend // spent MORE than the prior half
        data class Down(val percent: Int) : Trend // spent LESS than the prior half
    }

    object Noop : AnalyticsViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
