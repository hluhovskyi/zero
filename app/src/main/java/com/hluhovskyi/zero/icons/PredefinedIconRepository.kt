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

    private val icons: Map<Id.Known, Icon> = mapOf(
        iconOf(
            id = IconRepository.unknownCategoryIconId().value,
            resourceName = "ic_unknown_category_24",
            description = "Unknown"
        ),

        iconOf(id = "flowers", resourceName = "ic_florist_24", description = "Flowers"),
        iconOf(id = "grocery", resourceName = "ic_grocery_store_24", description = "Grocery"),
        iconOf(id = "fastfood", resourceName = "ic_fastfood_24", description = "Fast food"),
        iconOf(id = "car", resourceName = "ic_car_24", description = "Car"),
        iconOf(id = "car_repair", resourceName = "ic_car_repair_24", description = "Car repair"),
        iconOf(id = "diamond", resourceName = "ic_diamond_24", description = "Diamond"),
        iconOf(id = "game_controller", resourceName = "ic_game_controller_24", description = "Game controller"),
        iconOf(id = "book", resourceName = "ic_book_24", description = "Book"),
        iconOf(id = "movie", resourceName = "ic_movie_24", description = "Movie"),
        iconOf(id = "beach", resourceName = "ic_beach_24", description = "Beach"),
        // TODO: Add more starting from Відсотки і кешбек
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
        knownId to Icon(
            id = knownId,
            image = Image(
                uri = androidUriResourceFactory.drawable(resourceName),
                description = description
            )
        )
    }
}
