package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.localDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

internal class RoomTransactionRepository(
    private val transactionRoom: () -> TransactionRoom,
    private val currentUserId: Flow<Id.Known>,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val clock: Clock
) : TransactionRepository {
    override fun query(
        criteria: TransactionRepository.Criteria
    ): Flow<List<TransactionRepository.Transaction>> = currentUserId.take(1).flatMapConcat { userId ->
        transactionRoom().selectByUserId(userId)
            .map { entities ->
                entities.mapNotNull { entity ->
                    when (entity.type) {
                        TransactionEntity.Type.EXPENSE -> {
                            val categoryId =
                                entity.categoryId?.let(Id::Known) ?: return@mapNotNull null
                            TransactionRepository.Transaction.Expense(
                                id = entity.id,
                                amount = entity.amount.convert(),
                                accountId = entity.accountId,
                                currencyId = entity.currencyId,
                                categoryId = categoryId,
                                rate = entity.rate.convert()
                            )
                        }

                        TransactionEntity.Type.INCOME -> {
                            val categoryId =
                                entity.categoryId?.let(Id::Known) ?: return@mapNotNull null
                            TransactionRepository.Transaction.Income(
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
                            TransactionRepository.Transaction.Transfer(
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
    }

    override suspend fun insert(transaction: TransactionRepository.Transaction) {
        incorrectStateDetector.asyncRequireNonNull(
            value = currentUserId.firstOrNull(),
            message = "Current user id is empty"
        ) { userId ->
            val entity = when (transaction) {
                is TransactionRepository.Transaction.Expense -> TransactionEntity(
                    id = transaction.id,
                    userId = userId,
                    type = TransactionEntity.Type.EXPENSE,
                    currencyId = transaction.currencyId,
                    accountId = transaction.accountId,
                    categoryId = transaction.categoryId.value,
                    amount = transaction.amount.convert(),
                    rate = transaction.rate.convert(),
                    targetAccount = null,
                    targetAmount = AmountEntity.empty(),
                    enteredDateTime = clock.localDateTime(),
                    creationDateTime = clock.localDateTime(),
                    updatedDateTime = clock.localDateTime(),
                )

                is TransactionRepository.Transaction.Income -> TransactionEntity(
                    id = transaction.id,
                    userId = userId,
                    type = TransactionEntity.Type.INCOME,
                    currencyId = transaction.currencyId,
                    accountId = transaction.accountId,
                    categoryId = transaction.categoryId.value,
                    amount = transaction.amount.convert(),
                    rate = transaction.rate.convert(),
                    targetAccount = null,
                    targetAmount = AmountEntity.empty(),
                    enteredDateTime = clock.localDateTime(),
                    creationDateTime = clock.localDateTime(),
                    updatedDateTime = clock.localDateTime(),
                )

                is TransactionRepository.Transaction.Transfer -> TransactionEntity(
                    id = transaction.id,
                    userId = userId,
                    type = TransactionEntity.Type.TRANSFER,
                    currencyId = transaction.currencyId,
                    accountId = transaction.accountId,
                    categoryId = null,
                    amount = transaction.amount.convert(),
                    rate = RateEntity.empty(),
                    targetAccount = transaction.targetAccount.value,
                    targetAmount = transaction.targetAmount.convert(),
                    enteredDateTime = clock.localDateTime(),
                    creationDateTime = clock.localDateTime(),
                    updatedDateTime = clock.localDateTime(),
                )
            }

            transactionRoom().insert(entity)
        }
    }

    private fun AmountEntity.convert(): Amount = Amount(value)

    private fun Amount.convert(): AmountEntity = AmountEntity(value)

    private fun RateEntity.convert(): Rate = Rate(value)

    private fun Rate.convert(): RateEntity = RateEntity(value)
}