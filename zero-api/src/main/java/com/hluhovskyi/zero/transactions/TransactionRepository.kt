package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface TransactionRepository {

    fun query(criteria: Criteria): Flow<List<Transaction>>

    sealed interface Criteria {

        class All(val trigger: Trigger = Trigger.LoadAll): Criteria
    }

    sealed interface Trigger {

        object LoadAll : Trigger
    }

    suspend fun insert(transaction: Transaction)

    object Noop : TransactionRepository {
        override fun query(criteria: Criteria): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction) = Unit
    }
}