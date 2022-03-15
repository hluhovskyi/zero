package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomTransactionRepository(
    private val transactionRoom: () -> TransactionRoom
) : TransactionRepository {
    override fun query(criteria: TransactionRepository.Criteria): Flow<List<Transaction>> =
        transactionRoom().selectAll()
            .map { entities ->
                entities.mapNotNull { entity ->
                    when (entity.type) {
                        TransactionEntity.Type.EXPENSE -> {
                            val categoryId = entity.categoryId?.let(Id::Known) ?: return@mapNotNull null
                            Transaction.Expense(
                                id = entity.id,
                                amount = entity.amount.convert(),
                                accountId = entity.accountId,
                                currencyId = entity.currencyId,
                                categoryId = categoryId,
                                rate = entity.rate.convert()
                            )
                        }

                        TransactionEntity.Type.INCOME -> {
                            val categoryId = entity.categoryId?.let(Id::Known) ?: return@mapNotNull null
                            Transaction.Income(
                                id = entity.id,
                                amount = entity.amount.convert(),
                                accountId = entity.accountId,
                                currencyId = entity.currencyId,
                                categoryId = categoryId,
                                rate = entity.rate.convert()
                            )
                        }

                        TransactionEntity.Type.TRANSFER -> {
                            val targetAccount = entity.targetAccount ?: return@mapNotNull null
                            Transaction.Transfer(
                                id = entity.id,
                                amount = entity.amount.convert(),
                                accountId = entity.accountId,
                                currencyId = entity.currencyId,
                                targetAccount = Id.Known(targetAccount),
                                targetAmount = entity.targetAmount.convert()
                            )
                        }
                    }
                }
            }

    override suspend fun insert(transaction: Transaction) {
        val entity = when (transaction) {
            is Transaction.Expense -> TransactionEntity(
                id = transaction.id,
                type = TransactionEntity.Type.EXPENSE,
                currencyId = transaction.currencyId,
                accountId = transaction.accountId,
                categoryId = transaction.categoryId.value,
                amount = transaction.amount.convert(),
                rate = transaction.rate.convert(),
                targetAccount = null,
                targetAmount = AmountEntity.empty()
            )

            is Transaction.Income -> TransactionEntity(
                id = transaction.id,
                type = TransactionEntity.Type.INCOME,
                currencyId = transaction.currencyId,
                accountId = transaction.accountId,
                categoryId = transaction.categoryId.value,
                amount = transaction.amount.convert(),
                rate = transaction.rate.convert(),
                targetAccount = null,
                targetAmount = AmountEntity.empty()
            )

            is Transaction.Transfer -> TransactionEntity(
                id = transaction.id,
                type = TransactionEntity.Type.INCOME,
                currencyId = transaction.currencyId,
                accountId = transaction.accountId,
                categoryId = null,
                amount = transaction.amount.convert(),
                rate = RateEntity.empty(),
                targetAccount = transaction.targetAccount.value,
                targetAmount = transaction.targetAmount.convert()
            )
        }

        transactionRoom().insert(entity)
    }

    private fun AmountEntity.convert(): Amount = Amount(value)

    private fun Amount.convert(): AmountEntity = AmountEntity(value)

    private fun RateEntity.convert(): Rate = Rate(value)

    private fun Rate.convert(): RateEntity = RateEntity(value)
}