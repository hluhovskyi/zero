package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
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

    sealed interface Transaction {

        val id: Id.Known
        val amount: Amount
        val currencyId: Id.Known
        val accountId: Id.Known

        data class Expense(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            val categoryId: Id.Known,
            val rate: Rate,
        ) : Transaction

        data class Income(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            val categoryId: Id.Known,
            val rate: Rate,
        ) : Transaction

        data class Transfer(
            override val id: Id.Known,
            override val amount: Amount,
            override val currencyId: Id.Known,
            override val accountId: Id.Known,
            val targetAccount: Id.Known,
            val targetAmount: Amount
        ) : Transaction
    }

    object Noop : TransactionRepository {
        override fun query(criteria: Criteria): Flow<List<Transaction>> = flowOf(emptyList())
        override suspend fun insert(transaction: Transaction) = Unit
    }
}