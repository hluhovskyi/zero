package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryViewModel : AttachableActionStateModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryItem) : Action
    }

    data class State(
        val categories: List<CategoryItem> = emptyList(),
        val grandTotal: Amount = Amount.zero(),
        val currencySymbol: String = "",
    )

    data class CategoryItem(
        val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
        val spending: Spending = Spending.None,
    )

    sealed class Spending {
        data class Active(
            val totalAmount: Amount,
            val transactionCount: Int,
        ) : Spending()

        object None : Spending()
    }
}
