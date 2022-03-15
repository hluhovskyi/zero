package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AccountRepository {

    fun query(criteria: Criteria): Flow<List<Account>>

    sealed interface Criteria {

        class All : Criteria
    }

    data class Account(
        val id: Id.Known,
        val name: String,
        val currencyId: Id.Known,
    )

    suspend fun insert(account: AccountInsert)

    data class AccountInsert(
        val name: String,
        val currencyId: Id.Known
    )

    object Noop : AccountRepository {
        override fun query(criteria: Criteria): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun insert(account: AccountInsert) = Unit
    }
}