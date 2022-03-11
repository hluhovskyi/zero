package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface CategoryRepository {

    fun query(criteria: Criteria): Flow<List<Category>>

    interface Criteria {

        class All : Criteria
    }

    suspend fun insert(category: Category)

    object Noop : CategoryRepository {
        override fun query(criteria: Criteria): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun insert(category: Category) = Unit
    }
}