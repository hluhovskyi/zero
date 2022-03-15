package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

internal class RoomAccountRepository(
    private val accountRoom: () -> AccountRoom,
    private val currentUserId: Flow<Id.Known>,
    private val idGenerator: IdGenerator,
    private val incorrectStateDetector: IncorrectStateDetector,
) : AccountRepository {
    override fun query(criteria: AccountRepository.Criteria): Flow<List<AccountRepository.Account>> =
        when (criteria) {
            is AccountRepository.Criteria.All -> currentUserId.take(1)
                .flatMapConcat { userId ->
                    accountRoom().selectByUserId(userId)
                        .map { accounts ->
                            accounts.map { account ->
                                AccountRepository.Account(
                                    id = account.id,
                                    name = account.name,
                                    currencyId = account.currencyId
                                )
                            }
                        }
                }
        }

    override suspend fun insert(account: AccountRepository.AccountInsert) {
        incorrectStateDetector.asyncRequireNonNull(
            value = currentUserId.firstOrNull(),
            message = "Current user id is empty"
        ) { userId ->
            accountRoom().insert(AccountEntity(
                id = idGenerator(),
                userId = userId,
                currencyId = account.currencyId,
                name = account.name
            ))
        }
    }
}