package com.hluhovskyi.zero.analytics.breakdown

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.transactions.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

/** Filter-scoped spending breakdown, ranked By category / By account. */
interface SpendingBreakdownViewModel : AttachableActionStateModel<SpendingBreakdownViewModel.Action, SpendingBreakdownViewModel.State> {

    sealed interface Action {
        data object Back : Action
        data class SelectDimension(val dimension: Dimension) : Action
    }

    enum class Dimension { Category, Account }

    data class State(
        val totalAmount: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val transactionCount: Int = 0,
        val scopedAccountCount: Int = 0,
        val context: Context = Context(),
        val showAccountDimension: Boolean = false,
        val selectedDimension: Dimension = Dimension.Category,
        val categoryRows: List<Row> = emptyList(),
        val accountRows: List<Row> = emptyList(),
    ) {
        val rows: List<Row> = if (selectedDimension == Dimension.Account) accountRows else categoryRows
        val segments: List<Segment> = rows.map { Segment(it.amount.value.toFloat(), it.colorScheme) }
    }

    /** Filter context chips; 0 / null = no chip. */
    data class Context(
        val categoryCount: Int = 0,
        val accountCount: Int = 0,
        val type: TransactionFilter.TransactionType? = null,
        val dateRange: DateRange? = null,
    )

    data class Row(
        val name: String,
        val amount: Amount,
        val transactionCount: Int,
        val sharePercent: Int,
        val shareFraction: Float,
        val colorScheme: ColorScheme,
        val icon: Image,
    )

    data class Segment(
        val value: Float,
        val colorScheme: ColorScheme,
    )

    object Noop : SpendingBreakdownViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
