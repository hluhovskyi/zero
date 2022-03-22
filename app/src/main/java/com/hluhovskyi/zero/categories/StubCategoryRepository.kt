package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class StubCategoryRepository(
    private val uriFactory: AndroidUriResourceFactory
) : CategoryRepository {

    override fun query(criteria: CategoryRepository.Criteria): Flow<List<CategoryRepository.Category>> =
        flowOf(
            listOf(
                CategoryRepository.Category(
                    id = Id("food"),
                    parentCategoryId = Id.Unknown,
                    name = "Food",
                    iconId = Id("fastfood"),
                    colorId = Id("red"),
                ),
                CategoryRepository.Category(
                    id = Id("grocery"),
                    parentCategoryId = Id("food"),
                    name = "Grocery",
                    iconId = Id("grocery"),
                    colorId = Id("blue"),
                ),
                CategoryRepository.Category(
                    id = Id("presents"),
                    parentCategoryId = Id.Unknown,
                    name = "Presents",
                    iconId = Id("presents"),
                    colorId = Id("red"),
                ),
                CategoryRepository.Category(
                    id = Id("flowers"),
                    parentCategoryId = Id.Unknown,
                    name = "Flowers",
                    iconId = Id("flowers"),
                    colorId = Id("blue"),
                ),
            )
        )

    override suspend fun insert(category: CategoryRepository.Category) {

    }
}