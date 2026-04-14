package com.hluhovskyi.zero.imports

fun interface OnImportFinishedHandler {

    fun onFinished()

    object Noop : OnImportFinishedHandler {
        override fun onFinished() = Unit
    }
}
