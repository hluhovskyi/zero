package com.hluhovskyi.zero.activity

internal sealed class Destination(val route: String) {

    sealed class Account(route: String) : Destination(route) {
        object All : Account("accounts")
        object Edit : Transaction("accounts/edit")
    }

    sealed class Transaction(route: String) : Destination(route) {
        object All : Transaction("transactions")
        object Edit : Transaction("transactions/edit")
    }

    sealed class Category(route: String) : Destination(route) {
        object All : Category("categories")
        object Edit : Category("categories/edit")
    }
}