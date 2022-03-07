package com.hluhovskyi.zero.common

interface Logger {

    fun withTag(tag: String): AttributedLogger

    interface AttributedLogger {

        fun log(priority: Priority, message: String, throwable: Throwable? = null)
    }

    enum class Priority(val value: Int) {
        Verbose(2),
        Debug(3),
        Info(4),
        Warn(5),
        Error(6),
        Assert(7),
    }

    object Noop : Logger {
        override fun withTag(tag: String): AttributedLogger = NoopAttributedLogger

        private object NoopAttributedLogger : AttributedLogger {
            override fun log(priority: Priority, message: String, throwable: Throwable?) = Unit
        }
    }
}

fun Logger.AttributedLogger.d(message: String) {
    log(Logger.Priority.Debug, message)
}