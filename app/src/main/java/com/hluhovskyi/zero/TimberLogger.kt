package com.hluhovskyi.zero

import com.hluhovskyi.zero.common.Logger

object TimberLogger : Logger {
    override fun withTag(tag: String): Logger.AttributedLogger = TaggedAttributedLogger(tag)

    private class TaggedAttributedLogger(private val tag: String) : Logger.AttributedLogger {
        override fun log() {

        }
    }
}