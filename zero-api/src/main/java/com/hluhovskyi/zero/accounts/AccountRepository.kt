package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AccountRepository {

    fun query(criteria: Criteria): Flow<List<Account>>

    interface Criteria {

        class All : Criteria
    }

    object Noop : AccountRepository {
        override fun query(criteria: Criteria): Flow<List<Account>> = flowOf(emptyList())
    }
}