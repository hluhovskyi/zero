package com.hluhovskyi.zero.categories

fun interface OnAddCategoryHandler {
    fun onAdd()

    object Noop : OnAddCategoryHandler {
        override fun onAdd() = Unit
    }
}
