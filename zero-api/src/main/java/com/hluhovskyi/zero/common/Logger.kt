package com.hluhovskyi.zero.common

interface Logger {

    fun withTag(tag: String): AttributedLogger

    interface AttributedLogger {

        fun log()
    }
}