package com.hluhovskyi.zero.categories.detail

fun interface OnCategoryDetailBackHandler {
    fun onBack()

    object Noop : OnCategoryDetailBackHandler {
        override fun onBack() = Unit
    }
}
