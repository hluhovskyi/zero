package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface TransactionRepository {

    fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = emptyFlow<Any>()): Flow<T>

    sealed interface Criteria<T> {

        class All : Criteria<List<Transaction>>
        data class ById(val id: Id.Known) : Criteria<Transaction>
        data class After(val dateTime: LocalDateTime) : Criteria<List<Transaction>>
        class CategoryUsageStatistics : Criteria<List<CategoryUsageStatistic>>
        data class Search(val query: String) : Criteria<List<Transaction>>
        data class CategorySpendingBetween(
            val from: LocalDate,
            val to: LocalDate,
        ) : Criteria<List<CategorySpendingStatistic>>
    }

    sealed interface Trigger {

        object LoadAll : Trigger
    }

    suspend fun insert(transaction: Transaction)

    suspend fun insert(transactions: List<Transaction>)

    suspend fun delete(id: Id.Known)

    data class CategoryUsageStatistic(
        val categoryId: Id.Known,
        val transactionCount: Int,
        val lastUsedDateTime: LocalDateTime,
    )

    data class CategorySpendingStatistic(
        val categoryId: Id.Known,
        val currencyId: Id.Known,
        val totalAmount: Amount,
        val transactionCount: Int,
    )

    sealed interface Transaction {

        val id: Id.Known
        val amount: Amount
        val currencyId: Id.Known
        val accountId: Id.Known
        val dateTime: LocalDateTime
        val updatedDateTime: LocalDateTime

        data class Expense(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val categoryId: Id.Known,
            val rate: Rate,
        ) : Transaction

        data class Income(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val categoryId: Id.Known,
            val rate: Rate,
        ) : Transaction

        data class Transfer(
            override val id: Id.Known,
            override val amount: Amount,
            override val currencyId: Id.Known,
            override val accountId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val targetAccount: Id.Known,
            val targetAmount: Amount,
        ) : Transaction
    }

    object Noop : TransactionRepository {
        override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
        override suspend fun insert(transaction: Transaction) = Unit
        override suspend fun insert(transactions: List<Transaction>) = Unit
        override suspend fun delete(id: Id.Known) = Unit
    }
}
