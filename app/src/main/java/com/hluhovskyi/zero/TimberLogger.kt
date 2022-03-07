package com.hluhovskyi.zero

import com.hluhovskyi.zero.common.Logger
import timber.log.Timber

object TimberLogger : Logger {
    override fun withTag(tag: String): Logger.AttributedLogger =
        TaggedAttributedLogger(tag)

    private class TaggedAttributedLogger(
        private val tag: String
    ) : Logger.AttributedLogger {

        override fun log(priority: Logger.Priority, message: String, throwable: Throwable?) {
            Timber.tag(tag).log(priority.value, throwable, message)
        }
    }
}