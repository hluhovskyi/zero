package com.hluhovskyi.zero.transactions

import android.util.Log
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.localDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

internal class RoomTransactionRepository(
    private val transactionRoom: () -> TransactionRoom,
    private val currentUserId: Flow<Id.Known>,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val clock: Clock
) : TransactionRepository {

    override fun <T> query(criteria: TransactionRepository.Criteria<T>): Flow<T> = currentUserId.take(1)
        .flatMapConcat { userId ->
            Log.d("GOVNO", "zhepa, ${Thread.currentThread().name}")

            when (criteria) {
                is TransactionRepository.Criteria.All -> transactionRoom().selectByUserId(userId)
                    .map { entities ->
                        entities.mapNotNull { entity -> entity.toRepository() }
                    }

                is TransactionRepository.Criteria.ById ->
                    flow<TransactionRepository.Transaction> {
                        transactionRoom().selectById(criteria.id, userId)
                            ?.toRepository()
                            ?.let { transaction ->
                                emit(transaction)
                            }
                    }
            }
        }
        .uncheckedCast()

    override suspend fun insert(transaction: TransactionRepository.Transaction) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            transactionRoom().insert(transaction.toEntity(userId))
        }
    }

    override suspend fun insert(transactions: List<TransactionRepository.Transaction>) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            transactionRoom().insert(transactions.map { it.toEntity(userId) })
        }
    }

    private fun TransactionEntity.toRepository(): TransactionRepository.Transaction? {
        return when (type) {
            TransactionEntity.Type.EXPENSE -> {
                val categoryId =
                    categoryId?.let(Id::Known) ?: return null
                TransactionRepository.Transaction.Expense(
                    id = id,
                    amount = amount.convert(),
                    accountId = accountId,
                    currencyId = currencyId,
                    dateTime = enteredDateTime,
                    categoryId = categoryId,
                    rate = rate.convert()
                )
            }

            TransactionEntity.Type.INCOME -> {
                val categoryId =
                    categoryId?.let(Id::Known) ?: return null
                TransactionRepository.Transaction.Income(
                    id = id,
                    amount = amount.convert(),
                    accountId = accountId,
                    currencyId = currencyId,
                    dateTime = enteredDateTime,
                    categoryId = categoryId,
                    rate = rate.convert()
                )
            }

            TransactionEntity.Type.TRANSFER -> {
                val targetAccount = targetAccount ?: return null
                TransactionRepository.Transaction.Transfer(
                    id = id,
                    amount = amount.convert(),
                    accountId = accountId,
                    currencyId = currencyId,
                    dateTime = enteredDateTime,
                    targetAccount = Id.Known(targetAccount),
                    targetAmount = targetAmount.convert()
                )
            }
        }
    }

    private fun TransactionRepository.Transaction.toEntity(userId: Id.Known): TransactionEntity =
        when (this) {
            is TransactionRepository.Transaction.Expense -> TransactionEntity(
                id = id,
                userId = userId,
                type = TransactionEntity.Type.EXPENSE,
                currencyId = currencyId,
                accountId = accountId,
                categoryId = categoryId.value,
                amount = amount.convert(),
                rate = rate.convert(),
                targetAccount = null,
                targetAmount = AmountEntity.empty(),
                enteredDateTime = dateTime,
                creationDateTime = clock.localDateTime(),
                updatedDateTime = clock.localDateTime(),
            )

            is TransactionRepository.Transaction.Income -> TransactionEntity(
                id = id,
                userId = userId,
                type = TransactionEntity.Type.INCOME,
                currencyId = currencyId,
                accountId = accountId,
                categoryId = categoryId.value,
                amount = amount.convert(),
                rate = rate.convert(),
                targetAccount = null,
                targetAmount = AmountEntity.empty(),
                enteredDateTime = dateTime,
                creationDateTime = clock.localDateTime(),
                updatedDateTime = clock.localDateTime(),
            )

            is TransactionRepository.Transaction.Transfer -> TransactionEntity(
                id = id,
                userId = userId,
                type = TransactionEntity.Type.TRANSFER,
                currencyId = currencyId,
                accountId = accountId,
                categoryId = null,
                amount = amount.convert(),
                rate = RateEntity.empty(),
                targetAccount = targetAccount.value,
                targetAmount = targetAmount.convert(),
                enteredDateTime = dateTime,
                creationDateTime = clock.localDateTime(),
                updatedDateTime = clock.localDateTime(),
            )
        }


    private fun AmountEntity.convert(): Amount = Amount(value)

    private fun Amount.convert(): AmountEntity = AmountEntity(value)

    private fun RateEntity.convert(): Rate = Rate(value)

    private fun Rate.convert(): RateEntity = RateEntity(value)
}