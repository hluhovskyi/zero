package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.categories.CategoryType
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

/**
 * The resolved read model: reference lists plus the selected objects [resolve] picked from them.
 * Consumed by the use case's public projection and by [buildTransaction] at save time.
 */
internal data class TransactionEditState(
    val transactionType: TransactionEditType = TransactionEditType.EXPENSE,
    val accounts: List<TransactionEditAccount> = emptyList(),
    val selectedAccount: TransactionEditAccount? = null,
    val targetAccounts: List<TransactionEditAccount> = emptyList(),
    val selectedTargetAccount: TransactionEditAccount? = null,
    val allCategories: List<TransactionEditCategory> = emptyList(),
    val selectedCategory: TransactionEditCategory? = null,
    val currencies: List<TransactionEditCurrency> = emptyList(),
    val selectedCurrency: TransactionEditCurrency? = null,
    val localDateTime: LocalDateTime? = null,
    val amount: String = "",
    val rate: String = "",
    val rateAuto: Boolean = true,
    val targetAmount: String = "",
    val notes: String = "",
    val sourceSnapshot: TransactionEditUseCase.SourceSnapshot? = null,
)

/** Category type to rank/filter by; null for transfers, which have no category. */
internal fun TransactionEditType.categoryType(): CategoryType? = when (this) {
    TransactionEditType.EXPENSE -> CategoryType.EXPENSE
    TransactionEditType.INCOME -> CategoryType.INCOME
    TransactionEditType.TRANSFER -> null
}
