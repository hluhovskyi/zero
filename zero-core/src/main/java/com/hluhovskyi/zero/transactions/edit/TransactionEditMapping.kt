package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.datetime.LocalDateTime

/**
 * Pure mappings between [TransactionEditState] and a stored [TransactionRepository.Transaction].
 * Kept as plain functions (not injected collaborators): they have one implementation, no I/O of
 * their own, and the DB seam is already `TransactionRepository`. Unit-tested directly.
 */

/**
 * Builds the transaction to persist from [state], or null if a required field (account / category /
 * target account) is missing. [now] is the update timestamp and the fallback when no date was picked.
 */
internal fun buildTransaction(
    state: TransactionEditState,
    id: Id.Known,
    now: LocalDateTime,
): TransactionRepository.Transaction? {
    val account = state.selectedAccount ?: return null
    val dateTime = state.localDateTime ?: now

    return when (state.transactionType) {
        TransactionEditType.EXPENSE -> {
            val category = state.selectedCategory ?: return null
            TransactionRepository.Transaction.Expense(
                id = id,
                amount = Amount(state.amount.toBigDecimalOrNull()),
                accountId = account.id,
                currencyId = state.selectedCurrency?.id ?: account.currencyId,
                categoryId = category.id,
                dateTime = dateTime,
                updatedDateTime = now,
                rate = Rate(state.rate.toBigDecimalOrNull()),
                notes = state.notes.ifBlank { null },
            )
        }

        TransactionEditType.INCOME -> {
            val category = state.selectedCategory ?: return null
            TransactionRepository.Transaction.Income(
                id = id,
                amount = Amount(state.amount.toBigDecimalOrNull()),
                accountId = account.id,
                currencyId = state.selectedCurrency?.id ?: account.currencyId,
                categoryId = category.id,
                dateTime = dateTime,
                updatedDateTime = now,
                rate = Rate(state.rate.toBigDecimalOrNull()),
                notes = state.notes.ifBlank { null },
            )
        }

        TransactionEditType.TRANSFER -> {
            val targetAccount = state.selectedTargetAccount ?: return null
            TransactionRepository.Transaction.Transfer(
                id = id,
                amount = Amount(state.amount.toBigDecimalOrNull()),
                accountId = account.id,
                currencyId = account.currencyId,
                targetAccount = targetAccount.id,
                dateTime = dateTime,
                updatedDateTime = now,
                targetAmount = Amount(state.targetAmount.toBigDecimalOrNull()),
                notes = state.notes.ifBlank { null },
            )
        }
    }
}

/**
 * Seeds a loaded [transaction] onto [state] (whose reference data is already loaded). Pure, so the
 * caller can apply it inside an atomic `MutableStateFlow.update {}`. [isDuplicate] keeps a source
 * snapshot for the duplicate header.
 */
internal fun seedEditState(
    state: TransactionEditState,
    transaction: TransactionRepository.Transaction,
    isDuplicate: Boolean,
): TransactionEditState {
    val account = state.accounts.firstOrNull { it.id == transaction.accountId }
    val currency = state.currencies.firstOrNull { it.id == transaction.currencyId }
    val snapshot = if (isDuplicate) {
        TransactionEditUseCase.SourceSnapshot(
            amount = transaction.amount.value.toString(),
            date = transaction.dateTime,
            currencySymbol = currency?.currencySymbol.orEmpty(),
        )
    } else {
        null
    }
    val base = state.copy(
        amount = transaction.amount.value.toString(),
        selectedAccount = account ?: state.selectedAccount,
        selectedCurrency = currency ?: state.selectedCurrency,
        localDateTime = transaction.dateTime,
        notes = transaction.notes.orEmpty(),
        rateAuto = false,
        sourceSnapshot = snapshot,
    )

    return when (transaction) {
        is TransactionRepository.Transaction.Expense ->
            base.withCategory(TransactionEditType.EXPENSE, transaction.categoryId, transaction.rate.value.toString())

        is TransactionRepository.Transaction.Income ->
            base.withCategory(TransactionEditType.INCOME, transaction.categoryId, transaction.rate.value.toString())

        is TransactionRepository.Transaction.Transfer -> {
            val toAmount = transaction.targetAmount.value.toString()
            base.copy(
                transactionType = TransactionEditType.TRANSFER,
                selectedTargetAccount = state.accounts.firstOrNull { it.id == transaction.targetAccount },
                targetAmount = toAmount,
                rate = rateFromAmounts(base.amount, toAmount) ?: "1",
            )
        }
    }
}

/** Selects [categoryId] (moved to the front) and sets the saved [rate]. */
private fun TransactionEditState.withCategory(
    type: TransactionEditType,
    categoryId: Id.Known,
    rate: String,
): TransactionEditState {
    val selected = allCategories.firstOrNull { it.id == categoryId }
    val reordered = if (selected != null) listOf(selected) + allCategories.filter { it.id != selected.id } else allCategories
    return copy(
        transactionType = type,
        allCategories = reordered,
        selectedCategory = selected ?: selectedCategory,
        rate = rate,
    )
}
