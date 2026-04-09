package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.common.Id

fun interface OnCategorySelectedHandler {

    fun onSelected(categoryId: Id.Known)

    object Noop : OnCategorySelectedHandler {
        override fun onSelected(categoryId: Id.Known) = Unit
    }
}
