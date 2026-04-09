package com.hluhovskyi.zero

import com.hluhovskyi.zero.common.Logger
import timber.log.Timber

class TimberLogger(private val tag: String = "") : Logger {

    override fun withTag(tag: String): Logger = TimberLogger(
        if (this.tag.isBlank()) {
            tag
        } else {
            this.tag + "." + tag
        },
    )

    override fun log(priority: Logger.Priority, message: String, throwable: Throwable?) {
        val tree = if (tag.isNotBlank()) {
            Timber.tag(tag)
        } else {
            Timber
        }
        tree.log(priority.value, throwable, message)
    }
}
