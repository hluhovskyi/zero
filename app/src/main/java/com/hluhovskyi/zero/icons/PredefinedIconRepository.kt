package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.accounts.AccountCategory
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
    val health = IconCategory("health", "Health")
    val billsUtilities = IconCategory("bills_utilities", "Bills & Utilities")
    val home = IconCategory("home", "Home")
    val transport = IconCategory("transport", "Transport")
    val personalFamily = IconCategory("personal_family", "Personal & Family")
    val work = IconCategory("work", "Work")
}

internal class PredefinedIconRepository(
    private val androidUriResourceFactory: AndroidUriResourceFactory,
) : IconRepository {

    private val systemIcons: Map<Id.Known, Icon> = mapOf(
        iconOf(
            id = IconRepository.unknownCategoryIconId(),
            resourceName = "ic_unknown_category_24",
            description = "Unknown",
            category = IconCategory.system(),
        ),
        iconOf(
            id = IconRepository.transferIconId(),
            resourceName = "ic_transfer_24",
            description = "Transfer",
            category = IconCategory.system(),
        ),
    )

    private val icons: Map<Id.Known, Icon> = mapOf(
        // Money & Banking
        iconOf(id = KnownIconIds.cash, resourceName = "ic_cash_24", description = "Cash", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.bank, resourceName = "ic_bank_24", description = "Bank", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.creditCard, resourceName = "ic_credit_card_24", description = "Credit card", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.wallet, resourceName = "ic_wallet_24", description = "Wallet", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.crypto, resourceName = "ic_crypto_24", description = "Crypto", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.salary, resourceName = "ic_salary_24", description = "Salary", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.savings, resourceName = "ic_savings_24", description = "Savings", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.atm, resourceName = "ic_atm_24", description = "ATM", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.currencyExchange, resourceName = "ic_currency_exchange_24", description = "Currency exchange", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.receiptLong, resourceName = "ic_receipt_long_24", description = "Receipt", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.trendingUp, resourceName = "ic_trending_up_24", description = "Investments", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.accountBalanceWallet, resourceName = "ic_account_balance_wallet_24", description = "Account balance", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.payments, resourceName = "ic_payments_24", description = "Payments", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.monetizationOn, resourceName = "ic_monetization_on_24", description = "Money", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.paid, resourceName = "ic_paid_24", description = "Paid", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.cardGiftcard, resourceName = "ic_card_giftcard_24", description = "Gift card", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.balance, resourceName = "ic_balance_24", description = "Balance", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.pieChart, resourceName = "ic_pie_chart_24", description = "Portfolio", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.creditScore, resourceName = "ic_credit_score_24", description = "Credit score", category = Categories.moneyBanking),
        iconOf(id = KnownIconIds.requestQuote, resourceName = "ic_request_quote_24", description = "Invoice", category = Categories.moneyBanking),

        // Food & Drink
        iconOf(id = KnownIconIds.grocery, resourceName = "ic_grocery_store_24", description = "Grocery", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.fastfood, resourceName = "ic_fastfood_24", description = "Fast food", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.restaurant, resourceName = "ic_restaurant_24", description = "Restaurant", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.localCafe, resourceName = "ic_local_cafe_24", description = "Cafe", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.localBar, resourceName = "ic_local_bar_24", description = "Bar", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.bakeryDining, resourceName = "ic_bakery_dining_24", description = "Bakery", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.lunchDining, resourceName = "ic_lunch_dining_24", description = "Lunch", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.icecream, resourceName = "ic_icecream_24", description = "Ice cream", category = Categories.foodDrink),
        iconOf(id = KnownIconIds.liquor, resourceName = "ic_liquor_24", description = "Liquor", category = Categories.foodDrink),

        // Travel
        iconOf(id = KnownIconIds.car, resourceName = "ic_car_24", description = "Car", category = Categories.travel),
        iconOf(id = KnownIconIds.carRepair, resourceName = "ic_car_repair_24", description = "Car repair", category = Categories.travel),
        iconOf(id = KnownIconIds.beach, resourceName = "ic_beach_24", description = "Beach", category = Categories.travel),
        iconOf(id = KnownIconIds.flight, resourceName = "ic_flight_24", description = "Flight", category = Categories.travel),
        iconOf(id = KnownIconIds.hotel, resourceName = "ic_hotel_24", description = "Hotel", category = Categories.travel),
        iconOf(id = KnownIconIds.luggage, resourceName = "ic_luggage_24", description = "Luggage", category = Categories.travel),
        iconOf(id = KnownIconIds.hiking, resourceName = "ic_hiking_24", description = "Hiking", category = Categories.travel),

        // Shopping
        iconOf(id = KnownIconIds.diamond, resourceName = "ic_diamond_24", description = "Diamond", category = Categories.shopping),
        iconOf(id = KnownIconIds.shoppingCart, resourceName = "ic_shopping_cart_24", description = "Shopping cart", category = Categories.shopping),
        iconOf(id = KnownIconIds.flowers, resourceName = "ic_florist_24", description = "Flowers", category = Categories.shopping),
        iconOf(id = KnownIconIds.checkroom, resourceName = "ic_checkroom_24", description = "Clothing", category = Categories.shopping),
        iconOf(id = KnownIconIds.redeem, resourceName = "ic_redeem_24", description = "Gift", category = Categories.shopping),
        iconOf(id = KnownIconIds.devices, resourceName = "ic_devices_24", description = "Electronics", category = Categories.shopping),
        iconOf(id = KnownIconIds.localMall, resourceName = "ic_local_mall_24", description = "Mall", category = Categories.shopping),

        // Entertainment
        iconOf(id = KnownIconIds.gameController, resourceName = "ic_game_controller_24", description = "Game controller", category = Categories.entertainment),
        iconOf(id = KnownIconIds.movie, resourceName = "ic_movie_24", description = "Movie", category = Categories.entertainment),
        iconOf(id = KnownIconIds.musicNote, resourceName = "ic_music_note_24", description = "Music", category = Categories.entertainment),
        iconOf(id = KnownIconIds.headphones, resourceName = "ic_headphones_24", description = "Headphones", category = Categories.entertainment),
        iconOf(id = KnownIconIds.theaterComedy, resourceName = "ic_theater_comedy_24", description = "Theater", category = Categories.entertainment),
        iconOf(id = KnownIconIds.tv, resourceName = "ic_tv_24", description = "TV", category = Categories.entertainment),
        iconOf(id = KnownIconIds.confirmationNumber, resourceName = "ic_confirmation_number_24", description = "Tickets", category = Categories.entertainment),

        // Education
        iconOf(id = KnownIconIds.book, resourceName = "ic_book_24", description = "Book", category = Categories.education),
        iconOf(id = KnownIconIds.school, resourceName = "ic_school_24", description = "School", category = Categories.education),
        iconOf(id = KnownIconIds.menuBook, resourceName = "ic_menu_book_24", description = "Textbook", category = Categories.education),
        iconOf(id = KnownIconIds.calculate, resourceName = "ic_calculate_24", description = "Calculator", category = Categories.education),

        // Health
        iconOf(id = KnownIconIds.health, resourceName = "ic_health_24", description = "Health", category = Categories.health),
        iconOf(id = KnownIconIds.medication, resourceName = "ic_medication_24", description = "Medication", category = Categories.health),
        iconOf(id = KnownIconIds.medicalServices, resourceName = "ic_medical_services_24", description = "Medical", category = Categories.health),
        iconOf(id = KnownIconIds.fitnessCenter, resourceName = "ic_fitness_center_24", description = "Fitness", category = Categories.health),
        iconOf(id = KnownIconIds.spa, resourceName = "ic_spa_24", description = "Spa", category = Categories.health),
        iconOf(id = KnownIconIds.psychology, resourceName = "ic_psychology_24", description = "Therapy", category = Categories.health),

        // Bills & Utilities
        iconOf(id = KnownIconIds.bolt, resourceName = "ic_bolt_24", description = "Electricity", category = Categories.billsUtilities),
        iconOf(id = KnownIconIds.waterDrop, resourceName = "ic_water_drop_24", description = "Water", category = Categories.billsUtilities),
        iconOf(id = KnownIconIds.wifi, resourceName = "ic_wifi_24", description = "Internet", category = Categories.billsUtilities),
        iconOf(id = KnownIconIds.smartphone, resourceName = "ic_smartphone_24", description = "Phone", category = Categories.billsUtilities),
        iconOf(id = KnownIconIds.localFireDepartment, resourceName = "ic_local_fire_department_24", description = "Gas", category = Categories.billsUtilities),
        iconOf(id = KnownIconIds.subscriptions, resourceName = "ic_subscriptions_24", description = "Subscriptions", category = Categories.billsUtilities),

        // Home
        iconOf(id = KnownIconIds.home, resourceName = "ic_home_24", description = "Home", category = Categories.home),
        iconOf(id = KnownIconIds.chair, resourceName = "ic_chair_24", description = "Furniture", category = Categories.home),
        iconOf(id = KnownIconIds.bed, resourceName = "ic_bed_24", description = "Bed", category = Categories.home),
        iconOf(id = KnownIconIds.cleaningServices, resourceName = "ic_cleaning_services_24", description = "Cleaning", category = Categories.home),
        iconOf(id = KnownIconIds.build, resourceName = "ic_build_24", description = "Repairs", category = Categories.home),

        // Transport
        iconOf(id = KnownIconIds.gasStation, resourceName = "ic_gas_station_24", description = "Fuel", category = Categories.transport),
        iconOf(id = KnownIconIds.localTaxi, resourceName = "ic_local_taxi_24", description = "Taxi", category = Categories.transport),
        iconOf(id = KnownIconIds.train, resourceName = "ic_train_24", description = "Train", category = Categories.transport),
        iconOf(id = KnownIconIds.directionsBus, resourceName = "ic_directions_bus_24", description = "Bus", category = Categories.transport),
        iconOf(id = KnownIconIds.directionsBike, resourceName = "ic_directions_bike_24", description = "Bike", category = Categories.transport),
        iconOf(id = KnownIconIds.localParking, resourceName = "ic_local_parking_24", description = "Parking", category = Categories.transport),
        iconOf(id = KnownIconIds.twoWheeler, resourceName = "ic_two_wheeler_24", description = "Scooter", category = Categories.transport),

        // Personal & Family
        iconOf(id = KnownIconIds.pets, resourceName = "ic_pets_24", description = "Pets", category = Categories.personalFamily),
        iconOf(id = KnownIconIds.childCare, resourceName = "ic_child_care_24", description = "Childcare", category = Categories.personalFamily),
        iconOf(id = KnownIconIds.familyRestroom, resourceName = "ic_family_restroom_24", description = "Family", category = Categories.personalFamily),
        iconOf(id = KnownIconIds.volunteerActivism, resourceName = "ic_volunteer_activism_24", description = "Donations", category = Categories.personalFamily),

        // Work
        iconOf(id = KnownIconIds.work, resourceName = "ic_work_24", description = "Work", category = Categories.work),
        iconOf(id = KnownIconIds.businessCenter, resourceName = "ic_business_center_24", description = "Business", category = Categories.work),
        iconOf(id = KnownIconIds.computer, resourceName = "ic_computer_24", description = "Computer", category = Categories.work),
        iconOf(id = KnownIconIds.badge, resourceName = "ic_badge_24", description = "Badge", category = Categories.work),
    )

    override fun <T> query(criteria: IconRepository.Criteria<T>): Flow<T> = when (criteria) {
        is IconRepository.Criteria.All -> castingFlowOf(icons.values.toList() + systemIcons.values.toList())
        is IconRepository.Criteria.ById -> castingFlowOfNonNull(icons[criteria.id] ?: systemIcons[criteria.id])
    }

    override fun iconFor(category: AccountCategory): Icon {
        val id = when (category) {
            AccountCategory.CASH -> KnownIconIds.cash
            AccountCategory.BANK -> KnownIconIds.bank
            AccountCategory.CREDIT_CARDS -> KnownIconIds.creditCard
            AccountCategory.DIGITAL_WALLETS -> KnownIconIds.wallet
            AccountCategory.CRYPTO -> KnownIconIds.crypto
            AccountCategory.OTHER -> KnownIconIds.bank
        }
        return icons.getValue(id)
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
