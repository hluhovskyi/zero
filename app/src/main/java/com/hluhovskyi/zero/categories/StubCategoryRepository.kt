package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Category
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class StubCategoryRepository(
    private val uriFactory: AndroidUriResourceFactory
) : CategoryRepository {

    override fun query(criteria: CategoryRepository.Criteria): Flow<List<Category>> =
        flowOf(
            listOf(
                Category(
                    id = Id("food"),
                    parentCategoryId = Id.Unknown,
                    name = "Food",
                    icon = Image(
                        uri = uriFactory.drawable("ic_fastfood_24"),
                        description = "Food icon"
                    )
                ),
                Category(
                    id = Id("grocery"),
                    parentCategoryId = Id("food"),
                    name = "Grocery",
                    icon = Image(
                        uri = uriFactory.drawable("ic_grocery_store_24"),
                        description = "Grocery icon"
                    )
                ),
                Category(
                    id = Id("presents"),
                    parentCategoryId = Id.Unknown,
                    name = "Presents",
                    icon = Image.empty()
                ),
                Category(
                    id = Id("flowers"),
                    parentCategoryId = Id.Unknown,
                    name = "Flowers",
                    icon = Image(
                        uri = uriFactory.drawable("ic_florist_24"),
                        description = "Flower icon"
                    )
                ),
            )
        )

    override suspend fun insert(category: Category) {

    }
}