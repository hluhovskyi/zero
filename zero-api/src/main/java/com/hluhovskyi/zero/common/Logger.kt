package com.hluhovskyi.zero.common

interface Logger {

    fun withTag(tag: String): Logger

    fun log(priority: Priority, message: String, throwable: Throwable? = null)

    enum class Priority(val value: Int) {
        Verbose(2),
        Debug(3),
        Info(4),
        Warn(5),
        Error(6),
        Assert(7),
    }

    object Noop : Logger {
        override fun withTag(tag: String): Logger = this
        override fun log(priority: Priority, message: String, throwable: Throwable?) = Unit
    }
}

fun Logger.d(message: String) {
    log(Logger.Priority.Debug, message)
}