package com.hluhovskyi.zero.transactions.edit

fun interface OnShowAllCategoriesHandler {
    fun onShowAll()

    object Noop : OnShowAllCategoriesHandler {
        override fun onShowAll() = Unit
    }
}
