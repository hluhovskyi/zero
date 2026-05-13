package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Rate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface TransactionRepository {

    fun <T> query(criteria: Criteria<T>, trigger: Flow<*> = NO_TRIGGER): Flow<T>

    /**
     * Identity-comparable sentinel used as the default `trigger` of [query]. Implementations
     * may compare `trigger === NO_TRIGGER` to detect "caller did not supply a trigger" and pick a
     * different code path (e.g. fetch all rows in one batch instead of paginating).
     */
    companion object {
        val NO_TRIGGER: Flow<*> = emptyFlow<Any>()
    }

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
        data class ForCategories(val categoryIds: Set<Id.Known>) : Criteria<List<Transaction>>
        data class ForCategoryBetween(
            val categoryId: Id.Known,
            val from: LocalDate,
            val to: LocalDate,
        ) : Criteria<List<Transaction>>

        data class ForAccounts(val accountIds: Set<Id.Known>) : Criteria<List<Transaction>>
        data class ForAccountBetween(
            val accountId: Id.Known,
            val from: LocalDate,
            val to: LocalDate,
        ) : Criteria<List<Transaction>>

        class AccountBalanceDeltas : Criteria<Map<Id.Known, Amount>>
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
        val notes: String?

        data class Expense(
            override val id: Id.Known,
            override val amount: Amount,
            override val accountId: Id.Known,
            override val currencyId: Id.Known,
            override val dateTime: LocalDateTime,
            override val updatedDateTime: LocalDateTime,
            val categoryId: Id.Known,
            val rate: Rate,
            override val notes: String? = null,
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
            override val notes: String? = null,
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
            override val notes: String? = null,
        ) : Transaction
    }

    object Noop : TransactionRepository {
        override fun <T> query(criteria: Criteria<T>, trigger: Flow<*>): Flow<T> = emptyFlow()
        override suspend fun insert(transaction: Transaction) = Unit
        override suspend fun insert(transactions: List<Transaction>) = Unit
        override suspend fun delete(id: Id.Known) = Unit
    }
}
