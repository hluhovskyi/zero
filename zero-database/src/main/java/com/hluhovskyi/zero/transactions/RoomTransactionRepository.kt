package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Rate
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.take

internal class RoomTransactionRepository(
    private val transactionRoom: () -> TransactionRoom,
    private val currentUserId: Flow<Id.Known>,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val zonedClock: ZonedClock,
) : TransactionRepository {

    override fun <T> query(
        criteria: TransactionRepository.Criteria<T>,
        trigger: Flow<*>,
    ): Flow<T> = currentUserId.take(1)
        .flatMapConcat { userId ->
            when (criteria) {
                is TransactionRepository.Criteria.All -> if (trigger === TransactionRepository.NO_TRIGGER) {
                    transactionRoom().selectAllAlive(userId.value)
                        .map { entities -> entities.mapNotNull { it.toRepository() } }
                } else {
                    paginatedFlow(userId, trigger)
                }

                is TransactionRepository.Criteria.HasAny -> transactionRoom()
                    .selectHasAny(userId.value)

                is TransactionRepository.Criteria.ById ->
                    flow<TransactionRepository.Transaction> {
                        transactionRoom().selectById(criteria.id, userId)
                            ?.toRepository()
                            ?.let { transaction ->
                                emit(transaction)
                            }
                    }

                is TransactionRepository.Criteria.CategoryUsageStatistics -> transactionRoom()
                    .selectCategoryUsageStatistic(userId.value)
                    .map { entities ->
                        entities.map { entity ->
                            TransactionRepository.CategoryUsageStatistic(
                                categoryId = Id.Known(entity.categoryId),
                                transactionCount = entity.transactionCount,
                                lastUsedDateTime = entity.lastUsedDateTime,
                            )
                        }
                    }

                is TransactionRepository.Criteria.CategoryUsageStatisticsByAccount -> transactionRoom()
                    .selectCategoryUsageStatisticByAccount(userId.value, criteria.accountId.value)
                    .map { entities ->
                        entities.map { entity ->
                            TransactionRepository.CategoryUsageStatistic(
                                categoryId = Id.Known(entity.categoryId),
                                transactionCount = entity.transactionCount,
                                lastUsedDateTime = entity.lastUsedDateTime,
                            )
                        }
                    }

                is TransactionRepository.Criteria.CategoryUsageStatisticsByMonth -> transactionRoom()
                    .selectCategoryUsageStatisticByMonth(userId.value, "%02d".format(criteria.month))
                    .map { entities ->
                        entities.map { entity ->
                            TransactionRepository.CategoryUsageStatistic(
                                categoryId = Id.Known(entity.categoryId),
                                transactionCount = entity.transactionCount,
                                lastUsedDateTime = entity.lastUsedDateTime,
                            )
                        }
                    }

                is TransactionRepository.Criteria.CategoryAmountStatistics -> transactionRoom()
                    .selectCategoryAmountStatistic(userId.value)
                    .map { entities ->
                        entities.map { entity ->
                            TransactionRepository.CategoryAmountStatistic(
                                categoryId = Id.Known(entity.categoryId),
                                averageAmount = entity.averageAmount,
                            )
                        }
                    }

                is TransactionRepository.Criteria.Search -> {
                    val escaped = criteria.query
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                    transactionRoom()
                        .search(userId.value, "%$escaped%")
                        .map { entities -> entities.mapNotNull { it.toRepository() } }
                }

                is TransactionRepository.Criteria.CategorySpendingBetween -> transactionRoom()
                    .selectCategorySpendingBetween(
                        userId = userId.value,
                        from = criteria.from.toString(),
                        to = criteria.to.toString(),
                    )
                    .map { rows ->
                        rows.map { row ->
                            TransactionRepository.CategorySpendingStatistic(
                                categoryId = Id.Known(row.categoryId),
                                currencyId = Id.Known(row.currencyId),
                                totalAmount = Amount(row.totalAmount),
                                transactionCount = row.transactionCount,
                            )
                        }
                    }

                is TransactionRepository.Criteria.ForCategories -> transactionRoom()
                    .selectByCategories(userId.value, criteria.categoryIds.map { it.value })
                    .map { entities -> entities.mapNotNull { it.toRepository() } }

                is TransactionRepository.Criteria.ForCategoryBetween -> transactionRoom()
                    .selectByCategoryBetween(
                        userId = userId.value,
                        categoryId = criteria.categoryId.value,
                        from = criteria.from.toString(),
                        to = criteria.to.toString(),
                    )
                    .map { entities -> entities.mapNotNull { it.toRepository() } }

                is TransactionRepository.Criteria.ForAccounts -> transactionRoom()
                    .selectByAccounts(userId.value, criteria.accountIds.map { it.value })
                    .map { entities -> entities.mapNotNull { it.toRepository() } }

                is TransactionRepository.Criteria.ForAccountBetween -> transactionRoom()
                    .selectByAccountBetween(
                        userId = userId.value,
                        accountId = criteria.accountId.value,
                        from = criteria.from.toString(),
                        to = criteria.to.toString(),
                    )
                    .map { entities -> entities.mapNotNull { it.toRepository() } }

                is TransactionRepository.Criteria.AccountBalanceDeltas -> transactionRoom()
                    .selectAccountBalanceDeltas(userId.value)
                    .map { rows ->
                        rows.associate { Id.Known(it.accountId) to Amount(it.value) }
                    }
            }
        }
        .uncheckedCast()

    // trigger grows the window by PAGE_SIZE (scan seeds the first page); flatMapLatest keeps one
    // live selectWindow at the current size, so Room surfaces inserts/edits/deletes without re-fetch.
    private fun paginatedFlow(
        userId: Id.Known,
        trigger: Flow<*>,
    ): Flow<List<TransactionRepository.Transaction>> = trigger
        .map { Unit }
        .scan(PAGE_SIZE) { size, _ -> size + PAGE_SIZE }
        .flatMapLatest { size -> transactionRoom().selectWindow(userId.value, size) }
        .map { entities -> entities.mapNotNull { it.toRepository() } }

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

    override suspend fun delete(id: Id.Known) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            val now = zonedClock.localDateTime().toString()
            transactionRoom().softDelete(
                id = id.value,
                userId = userId.value,
                deletedAt = now,
                updatedDateTime = now,
            )
        }
        // softDelete writes the table -> the live selectWindow re-emits with the row gone.
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
                    updatedDateTime = updatedDateTime,
                    categoryId = categoryId,
                    rate = rate.convert(),
                    notes = notes,
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
                    updatedDateTime = updatedDateTime,
                    categoryId = categoryId,
                    rate = rate.convert(),
                    notes = notes,
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
                    updatedDateTime = updatedDateTime,
                    targetAccount = Id.Known(targetAccount),
                    targetAmount = targetAmount.convert(),
                    notes = notes,
                )
            }
        }
    }

    private fun TransactionRepository.Transaction.toEntity(userId: Id.Known): TransactionEntity = when (this) {
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
            creationDateTime = zonedClock.localDateTime(),
            updatedDateTime = updatedDateTime,
            deletedAt = null,
            notes = notes,
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
            creationDateTime = zonedClock.localDateTime(),
            updatedDateTime = updatedDateTime,
            deletedAt = null,
            notes = notes,
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
            creationDateTime = zonedClock.localDateTime(),
            updatedDateTime = updatedDateTime,
            deletedAt = null,
            notes = notes,
        )
    }

    private fun AmountEntity.convert(): Amount = Amount(value)

    private fun Amount.convert(): AmountEntity = AmountEntity(value)

    private fun RateEntity.convert(): Rate = Rate(value)

    private fun Rate.convert(): RateEntity = RateEntity(value)

    private companion object {
        const val PAGE_SIZE = 100
    }
}
