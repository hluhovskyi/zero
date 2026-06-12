package com.hluhovskyi.zero.analytics

fun interface OnSeeAllCategoriesHandler {
    fun onSeeAllCategories()

    companion object {
        val Noop = OnSeeAllCategoriesHandler { }
    }
}
