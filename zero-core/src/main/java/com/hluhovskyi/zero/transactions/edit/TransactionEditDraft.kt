package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id
import kotlinx.datetime.LocalDateTime

/**
 * The user's editable intent, by *reference id* + scalar — never resolved objects. Both the user
 * (via actions) and a loaded transaction (via [applyLoaded]) write into this one draft; nothing
 * else does. [resolve] turns it into a [TransactionEditState] against the latest reference lists,
 * so selection follows data reactively and order of arrival never matters.
 */
internal data class TransactionEditDraft(
    val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
    val accountId: Id.Known? = null,
    val targetAccountId: Id.Known? = null,
    val categoryId: Id.Known? = null,
    val pinSelectedCategory: Boolean = false,
    val currencyId: Id.Known? = null,
    val manuallyChangedCurrency: Boolean = false,
    val pickedCurrency: TransactionEditCurrency? = null,
    val amount: String = "",
    val rate: String = "",
    val rateAuto: Boolean = true,
    val targetAmount: String = "",
    val notes: String = "",
    val localDateTime: LocalDateTime? = null,
    val sourceSnapshot: SnapshotIntent? = null,
) {
    /** Duplicate-header source, carried by id so [resolve] can resolve its currency symbol. */
    data class SnapshotIntent(
        val amount: String,
        val date: LocalDateTime,
        val currencyId: Id.Known,
    )
}
