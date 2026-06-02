package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.firstOrNull

/** Resolves a saved transaction into the initial edit state (for edit and duplicate flows). */
internal interface TransactionEditLoader {

    /**
     * Loads the transaction [id] and seeds it onto [state] (whose reference data is already loaded).
     * Returns the seeded state, or null if the transaction can't be resolved.
     */
    suspend fun load(
        id: Id.Known,
        isDuplicate: Boolean,
        state: TransactionEditState,
    ): TransactionEditState?
}

internal class DefaultTransactionEditLoader(
    private val transactionRepository: TransactionRepository,
    private val incorrectStateDetector: IncorrectStateDetector,
) : TransactionEditLoader {

    override suspend fun load(
        id: Id.Known,
        isDuplicate: Boolean,
        state: TransactionEditState,
    ): TransactionEditState? {
        val transaction = transactionRepository
            .query(TransactionRepository.Criteria.ById(id))
            .firstOrNull()

        var seeded: TransactionEditState? = null
        incorrectStateDetector.asyncRequireNonNull(
            value = transaction,
            message = "Transaction is not resolved with id=$id",
        ) { resolved ->
            seeded = state.seededFrom(resolved, isDuplicate)
        }
        return seeded
    }

    private fun TransactionEditState.seededFrom(
        transaction: TransactionRepository.Transaction,
        isDuplicate: Boolean,
    ): TransactionEditState {
        val account = accounts.firstOrNull { it.id == transaction.accountId }
        val currency = currencies.firstOrNull { it.id == transaction.currencyId }
        val snapshot = if (isDuplicate) {
            TransactionEditUseCase.SourceSnapshot(
                amount = transaction.amount.value.toString(),
                date = transaction.dateTime,
                currencySymbol = currency?.currencySymbol.orEmpty(),
            )
        } else {
            null
        }
        val base = copy(
            amount = transaction.amount.value.toString(),
            selectedAccount = account ?: selectedAccount,
            selectedCurrency = currency ?: selectedCurrency,
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
                    selectedTargetAccount = accounts.firstOrNull { it.id == transaction.targetAccount },
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
