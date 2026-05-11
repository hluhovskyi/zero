package com.hluhovskyi.zero.categories.edit

fun interface OnDiscardHandler {

    fun onDiscard()

    object Noop : OnDiscardHandler {
        override fun onDiscard() = Unit
    }
}
