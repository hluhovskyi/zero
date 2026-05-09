package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow

private object Categories {
    val moneyBanking = IconCategory("money_banking", "Money & Banking")
    val foodDrink = IconCategory("food_drink", "Food & Drink")
    val travel = IconCategory("travel", "Travel")
    val shopping = IconCategory("shopping", "Shopping")
    val entertainment = IconCategory("entertainment", "Entertainment")
    val education = IconCategory("education", "Education")
}

internal class PredefinedIconRepository(
    private val androidUriResourceFactory: AndroidUriResourceFactory,
) : IconRepository {

    private val systemIcons: Map<Id.Known, Icon> = mapOf(
        iconOf(
            id = IconRepository.unknownCategoryIconId(),
            resourceName = "ic_unknown_category_24",
            description = "Unknown",
            category = IconCategory.unknown(),
        ),
        iconOf(
            id = IconRepository.transferIconId(),
            resourceName = "ic_transfer_24",
            description = "Transfer",
            category = IconCategory.unknown(),
        ),
    )

    private val icons: Map<Id.Known, Icon> = mapOf(
        iconOf(id = KnownIconIds.cash, resourceName = "ic_cash_24", description = "Cash", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.bank, resourceName = "ic_bank_24", description = "Bank", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.creditCard, resourceName = "ic_credit_card_24", description = "Credit card", category = Categories.moneyBanking),

        iconOf(id = KnownIconIds.flowers, resourceName = "ic_florist_24", description = "Flowers", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.grocery, resourceName = "ic_grocery_store_24", description = "Grocery", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.fastfood, resourceName = "ic_fastfood_24", description = "Fast food", category = Categories.foodDrink),

        iconOf(id = KnownIconIds.car, resourceName = "ic_car_24", description = "Car", category = Categories.travel),
        iconOf(id = KnownIconIds.carRepair, resourceName = "ic_car_repair_24", description = "Car repair", category = Categories.travel),
        iconOf(id = KnownIconIds.beach, resourceName = "ic_beach_24", description = "Beach", category = Categories.travel),

        iconOf(id = KnownIconIds.diamond, resourceName = "ic_diamond_24", description = "Diamond", category = Categories.shopping),

        iconOf(id = KnownIconIds.gameController, resourceName = "ic_game_controller_24", description = "Game controller", category = Categories.entertainment),
        iconOf(id = KnownIconIds.movie, resourceName = "ic_movie_24", description = "Movie", category = Categories.entertainment),

        iconOf(id = KnownIconIds.book, resourceName = "ic_book_24", description = "Book", category = Categories.education),
    )

    override fun <T> query(criteria: IconRepository.Criteria<T>): Flow<T> = when (criteria) {
        is IconRepository.Criteria.All -> castingFlowOf(icons.values.toList())
        is IconRepository.Criteria.ById -> castingFlowOfNonNull(icons[criteria.id] ?: systemIcons[criteria.id])
    }

    private fun iconOf(
        id: Id.Known,
        resourceName: String,
        description: String,
        category: IconCategory,
    ) = id to Icon(
        id = id,
        image = Image(
            uri = androidUriResourceFactory.drawable(resourceName),
            description = description,
        ),
        category = category,
    )
}
