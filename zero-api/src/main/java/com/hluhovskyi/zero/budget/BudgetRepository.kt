package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.DateRange
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.LocalDate

interface BudgetRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    suspend fun insert(budget: BudgetInsert)

    suspend fun insert(budgets: List<BudgetInsert>)

    suspend fun delete(id: Id.Known)

    /**
     * Atomic batch replace within [period] and [type]: any budget in that scope whose category
     * is not present in [inserts] is soft-deleted, and [inserts] are upserted. One round-trip
     * to the underlying store regardless of how many rows are involved.
     */
    suspend fun replace(period: DateRange, type: BudgetType, inserts: List<BudgetInsert>)

    sealed interface Criteria<T> {

        class All : Criteria<List<Budget>>

        data class ForPeriod(
            val from: LocalDate,
            val to: LocalDate,
            val type: BudgetType = BudgetType.EXPENSE,
        ) : Criteria<List<Budget>>

        data class ForCategoryAndPeriod(
            val categoryId: Id.Known,
            val from: LocalDate,
            val to: LocalDate,
            val type: BudgetType = BudgetType.EXPENSE,
        ) : Criteria<Budget?>

        data class HasAnyForPeriod(
            val from: LocalDate,
            val to: LocalDate,
            val type: BudgetType = BudgetType.EXPENSE,
        ) : Criteria<Boolean>
    }

    data class Budget(
        val id: Id.Known,
        val categoryId: Id.Known,
        val type: BudgetType,
        val amount: Amount,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
    )

    data class BudgetInsert(
        val id: Id = Id.Unknown,
        val categoryId: Id.Known,
        val type: BudgetType,
        val amount: Amount,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
    )

    object Noop : BudgetRepository {
        override fun <T> query(criteria: Criteria<T>): Flow<T> = emptyFlow()
        override suspend fun insert(budget: BudgetInsert) = Unit
        override suspend fun insert(budgets: List<BudgetInsert>) = Unit
        override suspend fun delete(id: Id.Known) = Unit
        override suspend fun replace(period: DateRange, type: BudgetType, inserts: List<BudgetInsert>) = Unit
    }
}
