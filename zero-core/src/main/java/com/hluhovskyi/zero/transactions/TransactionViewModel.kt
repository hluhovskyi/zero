package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface TransactionViewModel : AttachableActionStateModel<TransactionViewModel.Action, TransactionViewModel.State> {

    sealed interface Action {
        data class SelectTransaction(val item: Item.Transaction) : Action
        data object LoadMore : Action
    }

    data class State(
        val transactions: List<Item> = emptyList(),
    )

    sealed interface Item {

        data class Summary(
            val date: LocalDate,
            val total: Amount,
            val currencySymbol: String,
        ) : Item

        sealed interface Transaction : Item {

            val id: Id.Known
            val date: LocalDateTime

            data class Expense(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val accountName: String,
                val accountIcon: Image,
                val categoryName: String,
                val categoryColorScheme: ColorScheme,
                val categoryIcon: Image,
                val conversion: Conversion,
            ) : Transaction

            data class Income(
                override val id: Id.Known,
                override val date: LocalDateTime,
                val amount: Amount,
                val currencyId: Id.Known,
                val currencySymbol: String,
                val accountName: String,
                val accountIcon: Image,
                val categoryName: String,
                val categoryColorScheme: ColorScheme,
                val categoryIcon: Image,
                val conversion: Conversion,
            ) : Transaction

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
