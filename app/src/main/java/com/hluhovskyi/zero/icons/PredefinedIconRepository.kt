package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow

internal class PredefinedIconRepository(
    private val androidUriResourceFactory: AndroidUriResourceFactory,
) : IconRepository {

    private val icons: Map<Id.Known, Icon> = mapOf(
        iconOf(
            id = IconRepository.unknownCategoryIconId(),
            resourceName = "ic_unknown_category_24",
            description = "Unknown",
        ),
        iconOf(
            id = IconRepository.defaultAccountIconId(),
            resourceName = "ic_cash_24",
            description = "Cash",
        ),
        iconOf(
            id = IconRepository.transferIconId(),
            resourceName = "ic_transfer_24",
            description = "Transfer",
        ),

        iconOf(id = KnownIconIds.cash, resourceName = "ic_cash_24", description = "Cash"),
        iconOf(id = KnownIconIds.bank, resourceName = "ic_bank_24", description = "Bank"),
        iconOf(id = KnownIconIds.creditCard, resourceName = "ic_credit_card_24", description = "Credit card"),

        iconOf(id = KnownIconIds.flowers, resourceName = "ic_florist_24", description = "Flowers"),
        iconOf(id = KnownIconIds.grocery, resourceName = "ic_grocery_store_24", description = "Grocery"),
        iconOf(id = KnownIconIds.fastfood, resourceName = "ic_fastfood_24", description = "Fast food"),
        iconOf(id = KnownIconIds.car, resourceName = "ic_car_24", description = "Car"),
        iconOf(id = KnownIconIds.carRepair, resourceName = "ic_car_repair_24", description = "Car repair"),
        iconOf(id = KnownIconIds.diamond, resourceName = "ic_diamond_24", description = "Diamond"),
        iconOf(id = KnownIconIds.gameController, resourceName = "ic_game_controller_24", description = "Game controller"),
        iconOf(id = KnownIconIds.book, resourceName = "ic_book_24", description = "Book"),
        iconOf(id = KnownIconIds.movie, resourceName = "ic_movie_24", description = "Movie"),
        iconOf(id = KnownIconIds.beach, resourceName = "ic_beach_24", description = "Beach"),
        // TODO: Add more starting from Відсотки і кешбек
    )

    override fun <T> query(criteria: IconRepository.Criteria<T>): Flow<T> = when (criteria) {
        is IconRepository.Criteria.All -> castingFlowOf(icons.values.toList())
        is IconRepository.Criteria.ById -> castingFlowOfNonNull(icons[criteria.id])
    }

    private fun iconOf(
        id: Id.Known,
        resourceName: String,
        description: String,
    ) = id to Icon(
        id = id,
        image = Image(
            uri = androidUriResourceFactory.drawable(resourceName),
            description = description,
        ),
    )
}
