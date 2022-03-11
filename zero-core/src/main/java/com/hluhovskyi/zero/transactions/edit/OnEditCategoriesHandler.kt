package com.hluhovskyi.zero.transactions.edit

fun interface OnEditCategoriesHandler {
    fun onEdit()

    object Noop : OnEditCategoriesHandler {
        override fun onEdit() = Unit
    }
}