package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface CategoryRepository {

    fun query(criteria: Criteria): Flow<List<Category>>

    sealed interface Criteria {

        class All : Criteria
    }

    data class Category(
        val id: Id.Known,
        val parentCategoryId: Id,
        val name: String,
        val iconId: Id,
        val colorId: Id,
    )

    suspend fun insert(category: CategoryInsert)

    data class CategoryInsert(
        val parentCategoryId: Id,
        val name: String,
        val iconId: Id,
        val colorId: Id
    )

    object Noop : CategoryRepository {
        override fun query(criteria: Criteria): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun insert(category: CategoryInsert) = Unit
    }
}