package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.coroutines.uncheckedCast
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.localDateTime
import com.hluhovskyi.zero.common.valueOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

internal class RoomCategoryRepository(
    private val categoryRoom: () -> CategoryRoom,
    private val currentUserId: Flow<Id.Known>,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val incorrectStateDetector: IncorrectStateDetector,
) : CategoryRepository {

    override fun <T> query(criteria: CategoryRepository.Criteria<T>): Flow<T> = when (criteria) {
        is CategoryRepository.Criteria.All -> currentUserId.take(1)
            .flatMapConcat { userId ->
                categoryRoom().selectByUserId(userId)
                    .map { categories ->
                        categories.map { category -> category.toRepositoryModel() }
                    }
            }
            .uncheckedCast()

        is CategoryRepository.Criteria.ById -> currentUserId.take(1)
            .flatMapConcat { userId ->
                categoryRoom().selectById(id = criteria.categoryId, userId = userId)
                    .map { it.toRepositoryModel() }
            }
            .uncheckedCast()
    }

    override suspend fun insert(category: CategoryRepository.CategoryInsert) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            categoryRoom().insert(category.toEntity(userId))
        }
    }

    override suspend fun insert(categories: List<CategoryRepository.CategoryInsert>) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            categoryRoom().insert(categories.map { it.toEntity(userId) })
        }
    }

    private fun CategoryEntity.toRepositoryModel(): CategoryRepository.Category = CategoryRepository.Category(
        id = id,
        // TODO: Handle parent category
        parentCategoryId = Id.Unknown,
        name = name,
        colorId = Id(colorId),
        iconId = Id(iconId),
        type = CategoryType.from(type),
    )

    private fun CategoryRepository.CategoryInsert.toEntity(userId: Id.Known): CategoryEntity = CategoryEntity(
        id = (id as? Id.Known) ?: idGenerator(),
        userId = userId,
        name = name,
        iconId = iconId.valueOrNull(),
        colorId = colorId.valueOrNull(),
        creationDateTime = clock.localDateTime(zoneProvider.timeZone()),
        updatedDateTime = clock.localDateTime(zoneProvider.timeZone()),
        type = type.name,
    )
}
