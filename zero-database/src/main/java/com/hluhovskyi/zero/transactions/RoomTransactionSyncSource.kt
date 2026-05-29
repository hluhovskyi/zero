package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.sync.EntitySyncSource
import com.hluhovskyi.zero.sync.SyncTransaction
import kotlinx.datetime.LocalDateTime

internal class RoomTransactionSyncSource(
    private val dao: () -> TransactionSyncDao,
) : EntitySyncSource<SyncTransaction> {

    override suspend fun exportAll(userId: Id.Known): List<SyncTransaction> = dao().selectAllForSync(userId).map { it.toSyncModel() }

    override suspend fun exportSince(userId: Id.Known, since: LocalDateTime): List<SyncTransaction> = dao().selectSince(userId, since).map { it.toSyncModel() }

    override suspend fun lastModifiedAt(userId: Id.Known): LocalDateTime? = dao().selectLastModifiedAt(userId)

    private fun TransactionEntity.toSyncModel() = SyncTransaction(
        id = id,
        type = when (type) {
            TransactionEntity.Type.EXPENSE -> SyncTransaction.Type.EXPENSE
            TransactionEntity.Type.INCOME -> SyncTransaction.Type.INCOME
            TransactionEntity.Type.TRANSFER -> SyncTransaction.Type.TRANSFER
        },
        accountId = accountId,
        currencyId = currencyId,
        categoryId = categoryId,
        amount = amount.value.toPlainString(),
        rate = rate.value.toPlainString(),
        targetAccountId = targetAccount,
        targetAmount = targetAmount.value.takeIf { targetAccount != null }?.toPlainString(),
        enteredDateTime = enteredDateTime,
        creationDateTime = creationDateTime,
        updatedDateTime = updatedDateTime,
        deletedAt = deletedAt,
        notes = notes,
    )
}
