package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.RateEntity
import com.hluhovskyi.zero.sync.EntitySyncSink
import com.hluhovskyi.zero.sync.SyncTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

internal class RoomTransactionSyncSink(
    private val dao: () -> TransactionSyncDao,
    private val currentUserId: Flow<Id.Known>,
) : EntitySyncSink<SyncTransaction> {

    override suspend fun syncUpsert(entities: List<SyncTransaction>) {
        val userId = currentUserId.first()
        dao().syncUpsert(entities.map { it.toEntity(userId) })
    }

    private fun SyncTransaction.toEntity(userId: Id.Known) = TransactionEntity(
        id = id,
        userId = userId,
        type = when (type) {
            SyncTransaction.Type.EXPENSE -> TransactionEntity.Type.EXPENSE
            SyncTransaction.Type.INCOME -> TransactionEntity.Type.INCOME
            SyncTransaction.Type.TRANSFER -> TransactionEntity.Type.TRANSFER
        },
        currencyId = currencyId,
        accountId = accountId,
        categoryId = categoryId,
        amount = AmountEntity(BigDecimal(amount)),
        rate = RateEntity(BigDecimal(rate)),
        targetAccount = targetAccountId,
        targetAmount = AmountEntity(BigDecimal(targetAmount ?: "0")),
        enteredDateTime = enteredDateTime,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
    )
}
