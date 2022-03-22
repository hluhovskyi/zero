package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow

internal class PredefinedIconRepository(
    private val androidUriResourceFactory: AndroidUriResourceFactory
) : IconRepository {

    private val icons: Map<Id.Known, IconRepository.Icon> = mapOf(
        iconOf(
            id = IconRepository.unknownCategoryIconId().value,
            resourceName = "ic_unknown_category_24",
            description = "Unknown"
        ),

        iconOf(id = "flowers", resourceName = "ic_florist_24", description = "Flowers"),
        iconOf(id = "grocery", resourceName = "ic_grocery_store_24", description = "Grocery"),
        iconOf(id = "fastfood", resourceName = "ic_fastfood_24", description = "Fast food"),
    )

    override fun <T> query(criteria: IconRepository.Criteria<T>): Flow<T> =
        when (criteria) {
            is IconRepository.Criteria.All -> castingFlowOf(icons.values.toList())
            is IconRepository.Criteria.ById -> castingFlowOfNonNull(icons[criteria.id])
        }

    private fun iconOf(
        id: String,
        resourceName: String,
        description: String
    ) = Id(id).let { knownId ->
        knownId to IconRepository.Icon(
            id = knownId,
            image = Image(
                uri = androidUriResourceFactory.drawable(resourceName),
                description = description
            )
        )
    }
}
