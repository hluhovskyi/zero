package com.hluhovskyi.zero.categories.edit

fun interface OnCategorySavedHandler {

    fun onSaved()

    object Noop : OnCategorySavedHandler {
        override fun onSaved() = Unit
    }
}
