package com.hluhovskyi.zero.common

sealed interface Transaction {

    val id: Id.Known
    val amount: Amount
    val currencyId: Id.Known
    val accountId: Id.Known

    data class Expense(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        val categoryId: Id.Known,
        val rate: Rate,
    ) : Transaction

    data class Income(
        override val id: Id.Known,
        override val amount: Amount,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        val categoryId: Id.Known,
        val rate: Rate,
    ) : Transaction

    data class Transfer(
        override val id: Id.Known,
        override val amount: Amount,
        override val currencyId: Id.Known,
        override val accountId: Id.Known,
        val targetAccount: Id.Known,
        val targetAmount: Amount
    ) : Transaction
}
