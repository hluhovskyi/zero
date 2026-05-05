package com.hluhovskyi.zero.categories.detail

fun interface OnCategoryDetailCreateTransactionHandler {
    fun onCreate()

    object Noop : OnCategoryDetailCreateTransactionHandler {
        override fun onCreate() = Unit
    }
}
