package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

interface CategoryRepository {

    fun <T> query(criteria: Criteria<T>): Flow<T>

    sealed interface Criteria<T> {

        class All : Criteria<List<Category>>
        data class ById(val categoryId: Id.Known): Criteria<Category>
    }

    data class Category(
        val id: Id.Known,
        val parentCategoryId: Id,
        val name: String,
        val iconId: Id,
        val colorId: Id,
    )

    suspend fun insert(category: CategoryInsert)

    suspend fun insert(categories: List<CategoryInsert>)

    data class CategoryInsert(
        val id: Id = Id.Unknown,
        val parentCategoryId: Id,
        val name: String,
        val iconId: Id,
        val colorId: Id
    )

    object Noop : CategoryRepository {
        override fun <T> query(criteria: Criteria<T>): Flow<T> = emptyFlow()
        override suspend fun insert(category: CategoryInsert) = Unit
        override suspend fun insert(categories: List<CategoryInsert>) = Unit
    }
}