package com.hluhovskyi.zero.categories.detail

fun interface OnCategoryDetailEditHandler {
    fun onEdit()

    object Noop : OnCategoryDetailEditHandler {
        override fun onEdit() = Unit
    }
}
