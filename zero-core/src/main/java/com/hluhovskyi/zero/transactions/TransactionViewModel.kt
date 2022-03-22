package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AttachableStateViewModel
import com.hluhovskyi.zero.common.Color
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image

interface TransactionViewModel
    : AttachableStateViewModel<TransactionViewModel.Action, TransactionViewModel.State> {

    sealed class Action {

    }

    data class State(
        val transactions: List<TransactionItem> = emptyList()
    )

    sealed interface TransactionItem {

        abstract val id: Id.Known

        data class Expense(
            override val id: Id.Known,
            val amount: Amount,
            val currencySymbol: Char,
            val accountName: String,
            val categoryName: String,
            val categoryColor: Color,
            val categoryIcon: Image,
            val conversion: Conversion
        ) : TransactionItem

        data class Income(
            override val id: Id.Known,
            val amount: Amount,
            val accountName: String,
        ) : TransactionItem

        data class Transfer(
            override val id: Id.Known,
            val amount: Amount,
            val accountName: String,
            val targetAccountName: String
        ): TransactionItem
    }

    sealed interface Conversion {

        data class WithAmount(
            val amount: Amount,
            val currencySymbol: Char?
        ) : Conversion

        object None : Conversion
    }
}