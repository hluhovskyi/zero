package com.hluhovskyi.zero.transactions

import androidx.compose.runtime.Stable
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

@Stable
interface TransactionViewModel : AttachableActionStateModel<TransactionViewModel.Action, TransactionViewModel.State> {

    sealed interface Action {
        data class SelectTransaction(val item: Item.Transaction) : Action
        data object LoadMore : Action
        data class UpdateSearchQuery(val query: String) : Action
        data class ToggleSelection(val id: Id.Known) : Action
        data object ExitSelection : Action
        data object DeleteSelected : Action

        sealed interface Filter : Action {
            data object Open : Filter
            data object RemovePeriod : Filter
            data object RemoveType : Filter
            data object RemoveCategories : Filter
            data object RemoveAccounts : Filter
            data object Clear : Filter
        }
    }

    data class State(
        val transactions: List<Item> = emptyList(),
        val searchQuery: String = "",
        val activeFilter: TransactionFilter = TransactionFilter(),
        val selectedIds: Set<Id.Known> = emptySet(),
    ) {
        val inSelectionMode: Boolean = selectedIds.isNotEmpty()
        val selectionCount: Int = selectedIds.size

        fun isSelected(id: Id.Known): Boolean = id in selectedIds
    }

    @Stable
    sealed interface Item {

        data class Summary(
            val date: LocalDate,
            val total: Amount,
            val currencySymbol: String,
        ) : Item

        @Stable
        sealed interface Transaction : Item {

            val id: Id.Known
            val date: LocalDateTime

            @Stable
            data class Expense(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val accountName: String,
                val accountIcon: Image,
                val accountColorScheme: ColorScheme,
                val categoryName: String,
                val categoryColorScheme: ColorScheme,
                val categoryIcon: Image,
                val conversion: Conversion,
            ) : Transaction

            @Stable
            data class Income(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val accountName: String,
                val accountIcon: Image,
                val accountColorScheme: ColorScheme,
                val categoryName: String,
                val categoryColorScheme: ColorScheme,
                val categoryIcon: Image,
                val conversion: Conversion,
            ) : Transaction

            @Stable
            data class Transfer(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val accountName: String,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val targetAccountName: String,
                val targetAmount: Amount,
                val targetCurrencyId: Id.Known,
                val targetCurrencySymbol: String,
                val transferIcon: Image,
                val transferColorScheme: ColorScheme,
            ) : Transaction
        }
    }

    sealed interface Conversion {

        data class WithAmount(
            val amount: Amount,
            val currencyId: Id.Known,
            val currencySymbol: String,
        ) : Conversion

        object None : Conversion
    }
}
