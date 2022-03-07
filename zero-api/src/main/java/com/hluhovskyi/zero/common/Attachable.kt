package com.hluhovskyi.zero.common

import java.io.Closeable

interface Attachable {

    fun attach(): Closeable

    object Noop : Attachable {
        override fun attach(): Closeable = Closeables.empty()
    }
}