package com.hluhovskyi.zero.activity.navigation

import com.hluhovskyi.zero.common.Id

internal object Destinations {

    sealed interface Account : Destination {
        object All : Account, Destination by destinationOf("accounts")
        object Edit : Account, Destination by destinationOf("accounts/edit")
    }

    sealed interface Transaction : Destination {
        object All : Transaction, Destination by destinationOf("transactions")
        object Edit : Transaction, Destination by destinationOf("transactions/edit")

        sealed interface Item : Transaction {
            object TransactionId : Argument<Id> by idValueOf("transactionId")

            object Preview : Item, Destination by destinationOf("transactions/{transactionId}", TransactionId)
            object Edit : Item, Destination by destinationOf("transactions/{transactionId}/edit", TransactionId)
        }
    }

    sealed interface Category : Destination {
        object All : Category, Destination by destinationOf("categories")
        object Edit : Category, Destination by destinationOf("categories/edit")
        object Picker : Category, Destination by destinationOf("categories/picker", RequestId) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
        }

        sealed interface Item : Category {
            object Edit : Item, Destination by destinationOf("categories/{categoryId}/edit", CategoryId) {
                object CategoryId : Argument<Id> by idValueOf("categoryId")
            }
        }
    }

    sealed interface Icon : Destination {
        object Picker : Icon, Destination by destinationOf("icons/picker", RequestId, ColorId) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
            object ColorId : Argument<Id> by idOptionalValueOf("colorId")
        }
    }

    sealed interface Color : Destination {
        object Picker : Color, Destination by destinationOf("colors/picker", RequestId) {
            object RequestId : Argument<Id> by idOptionalValueOf("requestId")
        }
    }

    object Settings : Destination by destinationOf("settings")
    object Import : Destination by destinationOf("import")
}
