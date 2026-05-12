package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDateTime

interface AccountRepository {

    fun query(criteria: Criteria): Flow<List<Account>>

    sealed interface Criteria {

        class All : Criteria

        class ById(val id: Id.Known) : Criteria
    }

    data class Account(
        override val id: Id.Known,
        val name: String,
        val currencyId: Id.Known,
        val iconId: Id.Known,
        val colorId: Id = Id.Unknown,
        val initialBalance: Amount,
        val category: AccountCategory,
        val details: String?,
        val archivedAt: LocalDateTime? = null,
    ) : Identifiable

    suspend fun insert(account: AccountInsert)

    suspend fun insert(accounts: List<AccountInsert>)

    suspend fun archive(id: Id.Known)

    suspend fun unarchive(id: Id.Known)

    data class AccountInsert(
        val id: Id = Id.Unknown,
        val name: String,
        val currencyId: Id.Known,
        val iconId: Id.Known,
        val colorId: Id = Id.Unknown,
        val initialBalance: Amount,
        val category: AccountCategory = AccountCategory.OTHER,
        val details: String? = null,
    )

    object Noop : AccountRepository {
        override fun query(criteria: Criteria): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun insert(account: AccountInsert) = Unit
        override suspend fun insert(accounts: List<AccountInsert>) = Unit
        override suspend fun archive(id: Id.Known) = Unit
        override suspend fun unarchive(id: Id.Known) = Unit
    }
}
