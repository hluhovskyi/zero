package com.hluhovskyi.zero.common

interface IncorrectStateDetector {

    fun assert(message: String)

    fun <T> assertOrValue(
        message: String,
        value: T
    ): T

    fun <T> requireNonNull(
        value: T?,
        message: String? = null,
        block: (T) -> Unit
    )

    suspend fun <T> asyncRequireNonNull(
        value: T?,
        message: String? = null,
        block: suspend (T) -> Unit
    )

    companion object {

        fun ignoreIncorrect(): IncorrectStateDetector = IgnoreOnIncorrectStateDetector
    }
}

private object IgnoreOnIncorrectStateDetector : IncorrectStateDetector {

    override fun assert(message: String) {

    }

    override fun <T> assertOrValue(message: String, value: T): T = value

    override fun <T> requireNonNull(value: T?, message: String?, block: (T) -> Unit) {
        if (value != null) {
            block(value)
        }
    }

    override suspend fun <T> asyncRequireNonNull(
        value: T?,
        message: String?,
        block: suspend (T) -> Unit
    ) {
        if (value != null) {
            block(value)
        }
    }
}