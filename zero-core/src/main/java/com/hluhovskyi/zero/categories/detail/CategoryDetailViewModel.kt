package com.hluhovskyi.zero.categories.detail

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import java.io.Closeable

interface CategoryDetailViewModel : AttachableActionStateModel<CategoryDetailViewModel.Action, CategoryDetailViewModel.State> {

    sealed interface Action {
        object Edit : Action
        object Back : Action
        object CreateTransaction : Action
    }

    data class State(
        val categoryName: String = "",
        val categoryIcon: Image = Image.empty(),
        val categoryColorScheme: ColorScheme = ColorScheme.Grey,
        val periodDate: LocalDate? = null,
        val totalAmount: Amount = Amount.zero(),
        val currencySymbol: String = "",
        val transactionCount: Int = 0,
        val averageAmount: Amount = Amount.zero(),
        val largestAmount: Amount = Amount.zero(),
        val trend: List<TrendPoint> = emptyList(),
    )

    data class TrendPoint(
        val value: Float,
        val label: String,
        val amountLabel: String,
        val isCurrent: Boolean,
    )

    object Noop : CategoryDetailViewModel {
        override val state: Flow<State> = emptyFlow()
        override fun perform(action: Action) = Unit
        override fun attach(): Closeable = Closeable { }
    }
}
