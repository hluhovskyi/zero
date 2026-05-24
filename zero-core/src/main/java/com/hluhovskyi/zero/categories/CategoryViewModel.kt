package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface CategoryViewModel : AttachableActionStateModel<CategoryViewModel.Action, CategoryViewModel.State> {

    sealed interface Action {
        data class SelectCategory(val category: CategoryItem) : Action
        data class SelectTab(val type: CategoryType) : Action
    }

    data class State(
        val categoriesByType: Map<CategoryType, List<CategoryItem>> = emptyMap(),
        val grandTotalByType: Map<CategoryType, Amount> = emptyMap(),
        val currencySymbol: String = "",
        val selectedTab: CategoryType = CategoryType.EXPENSE,
        val hasAddedCategory: Boolean = true,
    ) {
        /** Categories with `Spending.Active` per type — pre-partitioned for the view. */
        val activeCategoriesByType: Map<CategoryType, List<CategoryItem>> =
            categoriesByType.mapValues { (_, items) ->
                items.filter { it.spending is Spending.Active }
            }

        /** Categories with `Spending.None` per type — pre-partitioned for the view. */
        val inactiveCategoriesByType: Map<CategoryType, List<CategoryItem>> =
            categoriesByType.mapValues { (_, items) ->
                items.filter { it.spending is Spending.None }
            }
    }

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
