package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.transactions.TransactionRepository

/** Persists the in-progress [TransactionEditState] as a [TransactionRepository.Transaction]. */
internal interface TransactionEditSaver {

    /**
     * Inserts the transaction built from [state] and returns its id, or null if a required field
     * (account / category / target account) is missing.
     */
    suspend fun save(state: TransactionEditState): Id.Known?
}

internal class DefaultTransactionEditSaver(
    private val transactionId: Id,
    private val transactionRepository: TransactionRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : TransactionEditSaver {

    override suspend fun save(state: TransactionEditState): Id.Known? {
        val account = state.selectedAccount ?: return null
        val id = (transactionId as? Id.Known) ?: idGenerator()
        val now = clock.localDateTime(zoneProvider.timeZone())
        val dateTime = state.localDateTime ?: now

        val transaction = when (state.transactionType) {
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

        transactionRepository.insert(transaction)
        return id
    }
}
