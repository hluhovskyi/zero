package com.hluhovskyi.zero.activity

internal sealed class Destination(val route: String) {
    sealed class Transaction(route: String): Destination(route) {
        object All : Transaction("transactions")
        object Edit : Transaction("transactions/edit")
    }
    sealed class Category(route: String): Destination(route) {
        object All : Category("categories")
        object Edit : Category("categories/edit")
    }
}