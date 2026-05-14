package com.hluhovskyi.zero.budget

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.time.ZonedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

internal class RoomBudgetRepository(
    private val budgetRoom: () -> BudgetRoom,
    private val currentUserId: Flow<Id.Known>,
    private val idGenerator: IdGenerator,
    private val zonedClock: ZonedClock,
    private val incorrectStateDetector: IncorrectStateDetector,
) : BudgetRepository {

    override fun <T> query(criteria: BudgetRepository.Criteria<T>): Flow<T> = when (criteria) {
        is BudgetRepository.Criteria.All -> currentUserId.take(1)
            .flatMapConcat { userId ->
                budgetRoom().selectByUserId(userId)
                    .map { entities -> entities.map { it.toRepositoryModel() } }
            }
            .uncheckedCast()

        is BudgetRepository.Criteria.ForPeriod -> currentUserId.take(1)
            .flatMapConcat { userId ->
                budgetRoom().selectForPeriod(userId, criteria.from, criteria.to, criteria.type.name)
                    .map { entities -> entities.map { it.toRepositoryModel() } }
            }
            .uncheckedCast()

        is BudgetRepository.Criteria.ForCategoryAndPeriod -> currentUserId.take(1)
            .flatMapConcat { userId ->
                budgetRoom().selectForCategoryAndPeriod(
                    userId,
                    criteria.categoryId,
                    criteria.from,
                    criteria.to,
                    criteria.type.name,
                ).map { it?.toRepositoryModel() }
            }
            .uncheckedCast()

        is BudgetRepository.Criteria.HasAnyForPeriod -> currentUserId.take(1)
            .flatMapConcat { userId ->
                budgetRoom().selectHasAnyForPeriod(userId, criteria.from, criteria.to, criteria.type.name)
            }
            .uncheckedCast()
    }

    override suspend fun insert(budget: BudgetRepository.BudgetInsert) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            budgetRoom().insert(budget.toEntity(userId))
        }
    }

    override suspend fun insert(budgets: List<BudgetRepository.BudgetInsert>) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            budgetRoom().insert(budgets.map { it.toEntity(userId) })
        }
    }

    override suspend fun delete(id: Id.Known) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            budgetRoom().softDelete(id, userId, zonedClock.localDateTime())
        }
    }

    private fun BudgetEntity.toRepositoryModel(): BudgetRepository.Budget = BudgetRepository.Budget(
        id = id,
        categoryId = categoryId,
        type = BudgetType.from(type),
        amount = Amount(amount),
        periodStart = periodStart,
        periodEnd = periodEnd,
    )

    private fun BudgetRepository.BudgetInsert.toEntity(userId: Id.Known): BudgetEntity {
        val now = zonedClock.localDateTime()
        return BudgetEntity(
            id = (id as? Id.Known) ?: idGenerator(),
            userId = userId,
            categoryId = categoryId,
            type = type.name,
            amount = amount.value,
            periodStart = periodStart,
            periodEnd = periodEnd,
            creationDateTime = now,
            updatedDateTime = now,
        )
    }
}
