package com.hluhovskyi.zero.activity.navigation

import com.hluhovskyi.zero.common.Id

internal object Destinations {

    sealed interface Account : Destination {
        object All : Account, Destination by destinationOf("accounts")
        object Edit : Account, Destination by destinationOf("accounts/edit")

        sealed interface Item : Account {
            object AccountId : Argument<Id.Known> by idKnownValueOf("accountId")
            object Detail : Item, Destination by destinationOf("accounts/{accountId}", AccountId)
            object Edit : Item, Destination by destinationOf("accounts/{accountId}/edit", AccountId)
        }
    }

    sealed interface Transaction : Destination {
        object Filter : Transaction, Destination by destinationOf("transactions/filter")
        object Edit : Transaction, Destination by destinationOf("transactions/edit", SelectedCategoryId, SelectedAccountId) {
            object SelectedCategoryId : Argument<Id> by idOptionalValueOf("selectedCategoryId")
            object SelectedAccountId : Argument<Id> by idOptionalValueOf("selectedAccountId")
        }

        sealed interface Item : Transaction {
            object TransactionId : Argument<Id> by idValueOf("transactionId")

            object Preview : Item, Destination by destinationOf("transactions/{transactionId}", TransactionId)
            object Edit : Item, Destination by destinationOf("transactions/{transactionId}/edit", TransactionId)
        }
    }

    sealed interface Category : Destination {
        object All : Category, Destination by destinationOf("categories")
        object Edit : Category, Destination by destinationOf("categories/edit", InitialType) {
            object InitialType : Argument<String> by stringOptionalValueOf("initialType")
        }
        object Picker : Category, Destination by destinationOf("categories/picker", RequestId, SelectedCategoryId) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
            object SelectedCategoryId : Argument<Id> by idOptionalValueOf("selectedCategoryId")
        }

        sealed interface Item : Category {
            object CategoryId : Argument<Id.Known> by idKnownValueOf("categoryId")

            object Detail : Item, Destination by destinationOf("categories/{categoryId}", CategoryId)
            object Edit : Item, Destination by destinationOf("categories/{categoryId}/edit", CategoryId)
        }
    }

    sealed interface Currency : Destination {
        object Picker : Currency, Destination by destinationOf("currencies/picker", RequestId, SelectedCurrencyId) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
            object SelectedCurrencyId : Argument<Id> by idOptionalValueOf("selectedCurrencyId")
        }
    }

    sealed interface Icon : Destination {
        object Picker : Icon, Destination by destinationOf("icons/picker", RequestId, ColorId, SelectedIconId, MoneyPlacement) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
            object ColorId : Argument<Id> by idOptionalValueOf("colorId")
            object SelectedIconId : Argument<Id> by idOptionalValueOf("selectedIconId")
            object MoneyPlacement : Argument<String> by stringOptionalValueOf("moneyPlacement")
        }
    }

    sealed interface Color : Destination {
        object Picker : Color, Destination by destinationOf("colors/picker", RequestId) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
        }
    }

    object Home : Destination by destinationOf("home")
    object Settings : Destination by destinationOf("settings")
    object Import : Destination by destinationOf("import")
}
