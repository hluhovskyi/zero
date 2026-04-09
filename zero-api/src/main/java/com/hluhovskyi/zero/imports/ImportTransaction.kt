package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

sealed interface ImportTransaction {

    val id: Id.Known
    val amount: Amount
    val currencyId: Id.Known
    val accountId: Id.Known
    val dateTime: LocalDateTime

    data class Expense(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known,
    ) : ImportTransaction

    data class Income(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known,
    ) : ImportTransaction

    data class Transfer(
        override val id: Id.Known,
        override val amount: Amount,
        override val currencyId: Id.Known,
        override val accountId: Id.Known,
        override val dateTime: LocalDateTime,
        val targetAccount: Id.Known,
        val targetAmount: Amount,
    ) : ImportTransaction
}
