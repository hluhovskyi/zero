package com.hluhovskyi.zero.categories

fun interface OnAddCategoryHandler {
    fun onAdd(type: CategoryType)

    object Noop : OnAddCategoryHandler {
        override fun onAdd(type: CategoryType) = Unit
    }
}
