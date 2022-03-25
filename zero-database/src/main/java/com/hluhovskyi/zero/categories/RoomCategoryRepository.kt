package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.time.Clock
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
    private val incorrectStateDetector: IncorrectStateDetector,
) : CategoryRepository {

    override fun query(criteria: CategoryRepository.Criteria): Flow<List<CategoryRepository.Category>> =
        when (criteria) {
            is CategoryRepository.Criteria.All -> currentUserId.take(1)
                .flatMapConcat { userId ->
                    categoryRoom().selectByUserId(userId)
                        .map { categories ->
                            categories.map { category ->
                                CategoryRepository.Category(
                                    id = category.id,
                                    parentCategoryId = Id.Unknown,
                                    name = category.name,
                                    colorId = Id(category.colorId),
                                    iconId = Id(category.iconId),
                                )
                            }
                        }
                }
        }

    override suspend fun insert(category: CategoryRepository.CategoryInsert) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            categoryRoom().insert(
                CategoryEntity(
                    id = idGenerator(),
                    userId = userId,
                    name = category.name,
                    iconId = category.iconId.valueOrNull(),
                    colorId = category.colorId.valueOrNull(),
                    creationDateTime = clock.localDateTime(),
                    updatedDateTime = clock.localDateTime(),
                )
            )
        }
    }
}