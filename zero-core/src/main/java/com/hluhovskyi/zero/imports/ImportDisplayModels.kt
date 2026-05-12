package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import kotlinx.datetime.LocalDateTime

data class ImportCategory(
    val id: Id.Known,
    val name: String,
    val colorScheme: ColorScheme,
    val icon: Image,
    val transactionCount: Int,
    val existingId: Id.Known? = null,
)

data class ImportAccount(
    val id: Id.Known,
    val name: String,
    val currencyId: Id.Known,
    val transactionCount: Int,
    val icon: Image? = null,
    val existingId: Id.Known? = null,
)

sealed interface ImportTransaction {
    val id: Id.Known
    val accountId: Id.Known
    val currencyId: Id.Known
    val amount: Amount
    val dateTime: LocalDateTime

    data class Expense(
        override val id: Id.Known,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val amount: Amount,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known?,
        val categoryName: String?,
    ) : ImportTransaction

    data class Income(
        override val id: Id.Known,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val amount: Amount,
        override val dateTime: LocalDateTime,
        val categoryId: Id.Known?,
        val categoryName: String?,
    ) : ImportTransaction

    data class Transfer(
        override val id: Id.Known,
        override val accountId: Id.Known,
        override val currencyId: Id.Known,
        override val amount: Amount,
        override val dateTime: LocalDateTime,
        val targetAccountId: Id.Known,
        val targetAmount: Amount,
        val targetCurrencyId: Id.Known,
    ) : ImportTransaction
}
