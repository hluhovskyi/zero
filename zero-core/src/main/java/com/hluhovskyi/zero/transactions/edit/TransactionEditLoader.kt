package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull

/** Resolves a saved transaction into the initial edit state (for edit and duplicate flows). */
internal interface TransactionEditLoader {

    /** Loads the saved transaction [id], or null (and reports) if it can't be resolved. */
    suspend fun fetch(id: Id.Known): TransactionRepository.Transaction?

    /**
     * Seeds [transaction] onto [state] (whose reference data is already loaded). Pure, so the caller
     * can apply it inside an atomic `MutableStateFlow.update {}`.
     */
    fun seed(
        state: TransactionEditState,
        transaction: TransactionRepository.Transaction,
        isDuplicate: Boolean,
    ): TransactionEditState
}

internal class DefaultTransactionEditLoader(
    private val transactionRepository: TransactionRepository,
    private val incorrectStateDetector: IncorrectStateDetector,
) : TransactionEditLoader {

    override suspend fun fetch(id: Id.Known): TransactionRepository.Transaction? {
        val transaction = transactionRepository
            .query(TransactionRepository.Criteria.ById(id))
            .firstOrNull()
        if (transaction == null) {
            incorrectStateDetector.assert("Transaction is not resolved with id=$id")
        }
        return transaction
    }

    override fun seed(
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
}
