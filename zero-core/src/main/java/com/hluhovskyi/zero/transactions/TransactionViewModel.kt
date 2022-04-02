package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableActionStateModel
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import java.time.LocalDate

interface TransactionViewModel
    : AttachableActionStateModel<TransactionViewModel.Action, TransactionViewModel.State> {

    sealed class Action {

    }

    data class State(
        val transactions: List<Item> = emptyList()
    )

    sealed interface Item {

        data class Summary(
            val date: LocalDate,
            val total: Amount,
        ) : Item

        sealed interface Transaction : Item {

            val id: Id.Known

            data class Expense(
                override val id: Id.Known,
                val amount: Amount,
                val currencySymbol: String,
                val accountName: String,
                val categoryName: String,
                val categoryColor: ColorValue,
                val categoryIcon: Image,
                val conversion: Conversion
            ) : Transaction

            data class Income(
                override val id: Id.Known,
                val amount: Amount,
                val accountName: String,
            ) : Transaction

            data class Transfer(
                override val id: Id.Known,
                val amount: Amount,
                val accountName: String,
                val targetAccountName: String
            ) : Transaction
        }
    }

    sealed interface Conversion {

        data class WithAmount(
            val amount: Amount,
            val currencySymbol: String,
        ) : Conversion

        object None : Conversion
    }
}