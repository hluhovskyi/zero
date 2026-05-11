package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.common.valueOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.datetime.LocalDateTime

internal class RoomAccountRepository(
    private val accountRoom: () -> AccountRoom,
    private val currentUserId: Flow<Id.Known>,
    private val idGenerator: IdGenerator,
    private val incorrectStateDetector: IncorrectStateDetector,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : AccountRepository {
    override fun query(criteria: AccountRepository.Criteria): Flow<List<AccountRepository.Account>> = when (criteria) {
        is AccountRepository.Criteria.All -> currentUserId.take(1)
            .flatMapConcat { userId ->
                accountRoom().selectByUserId(userId).map { it.toDomain() }
            }
        is AccountRepository.Criteria.ById -> currentUserId.take(1)
            .flatMapConcat { userId ->
                accountRoom().selectById(userId, criteria.id).map { it.toDomain() }
            }
    }

    private fun List<AccountEntity>.toDomain(): List<AccountRepository.Account> = map { account ->
        AccountRepository.Account(
            id = account.id,
            name = account.name,
            currencyId = account.currencyId,
            iconId = account.iconId,
            colorId = Id(account.colorId),
            initialBalance = Amount(account.initialBalance.value),
            category = AccountCategory.from(account.category),
            details = account.details,
            archivedAt = account.archivedAt,
        )
    }

    override suspend fun insert(account: AccountRepository.AccountInsert) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            accountRoom().insert(account.toEntity(userId))
        }
    }

    override suspend fun insert(accounts: List<AccountRepository.AccountInsert>) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            accountRoom().insert(accounts.map { it.toEntity(userId) })
        }
    }

    override suspend fun archive(id: Id.Known) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            val entity = accountRoom().selectByIdOnce(userId.value, id.value) ?: return@requireCurrentUserId
            val now = clock.localDateTime(zoneProvider.timeZone())
            accountRoom().insert(entity.copy(archivedAt = now, updatedDateTime = now))
        }
    }

    override suspend fun unarchive(id: Id.Known) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            val entity = accountRoom().selectByIdOnce(userId.value, id.value) ?: return@requireCurrentUserId
            val now = clock.localDateTime(zoneProvider.timeZone())
            accountRoom().insert(entity.copy(archivedAt = null, updatedDateTime = now))
        }
    }

    private fun AccountRepository.AccountInsert.toEntity(userId: Id.Known): AccountEntity {
        val sentinel = LocalDateTime(2000, 1, 1, 0, 0)
        return AccountEntity(
            id = (id as? Id.Known) ?: idGenerator(),
            userId = userId,
            currencyId = currencyId,
            name = name,
            iconId = iconId,
            colorId = colorId.valueOrNull(),
            initialBalance = AmountEntity(initialBalance.value),
            category = category.name,
            details = details,
            creationDateTime = sentinel,
            updatedDateTime = sentinel,
        )
    }
}
