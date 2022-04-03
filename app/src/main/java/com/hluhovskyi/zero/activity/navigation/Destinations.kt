package com.hluhovskyi.zero.activity.navigation

internal object Destinations {

    sealed interface Account : Destination {
        object All : Account, Destination by destinationOf("accounts")
        object Edit : Account, Destination by destinationOf("accounts/edit")
    }

    sealed interface Transaction : Destination {
        object All : Transaction, Destination by destinationOf("transactions")
        object Edit : Transaction, Destination by destinationOf("transactions/edit")
    }

    sealed interface Category : Destination {
        object All : Category, Destination by destinationOf("categories")
        object Edit : Category, Destination by destinationOf("categories/edit")

        sealed interface Item : Category {
            object Edit : Item, Destination by destinationOf("categories/{categoryId}/edit", CategoryId) {
                object CategoryId : Argument<String> by stringValueOf("categoryId")
            }
        }
    }

    sealed interface Icon : Destination {
        object Picker : Icon, Destination by destinationOf("icons/picker", RequestId) {
            object RequestId : Argument<String> by stringOptionalValueOf("requestId")
        }
    }

    sealed interface Color : Destination {
        object Picker : Color, Destination by destinationOf("colors/picker", RequestId) {
            object RequestId : Argument<String> by stringOptionalValueOf("requestId") {
                override fun toString(): String = "RequestId"
            }
        }
    }

    object Settings : Destination by destinationOf("settings")
    object Import : Destination by destinationOf("import")
}
