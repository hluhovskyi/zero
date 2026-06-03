package com.hluhovskyi.zero.transactions.edit

import kotlinx.datetime.LocalDateTime

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
    val categoryPickedFromPicker: Boolean = false,
    val currencies: List<TransactionEditCurrency> = emptyList(),
    val selectedCurrency: TransactionEditCurrency? = null,
    val localDateTime: LocalDateTime? = null,
    val amount: String = "",
    val rate: String = "",
    val rateAuto: Boolean = true,
    val targetAmount: String = "",
    val notes: String = "",
    val sourceSnapshot: TransactionEditUseCase.SourceSnapshot? = null,
    val isModified: Boolean = false,
)
