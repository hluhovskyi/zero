package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.accounts.AccountCategory
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountEntity
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.requireCurrentUserId
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
) : AccountRepository {
    override fun query(criteria: AccountRepository.Criteria): Flow<List<AccountRepository.Account>> = when (criteria) {
        is AccountRepository.Criteria.All -> currentUserId.take(1)
            .flatMapConcat { userId ->
                accountRoom().selectByUserId(userId)
                    .map { accounts ->
                        accounts.map { account ->
                            AccountRepository.Account(
                                id = account.id,
                                name = account.name,
                                currencyId = account.currencyId,
                                iconId = account.iconId,
                                initialBalance = Amount(account.initialBalance.value),
                                category = runCatching {
                                    AccountCategory.valueOf(account.category)
                                }.getOrDefault(AccountCategory.OTHER),
                                details = account.details,
                            )
                        }
                    }
            }
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

    private fun AccountRepository.AccountInsert.toEntity(userId: Id.Known): AccountEntity {
        val sentinel = LocalDateTime(2000, 1, 1, 0, 0)
        return AccountEntity(
            id = (id as? Id.Known) ?: idGenerator(),
            userId = userId,
            currencyId = currencyId,
            name = name,
            iconId = iconId,
            initialBalance = AmountEntity(initialBalance.value),
            category = category.name,
            details = details,
            creationDateTime = sentinel,
            updatedDateTime = sentinel,
        )
    }
}
