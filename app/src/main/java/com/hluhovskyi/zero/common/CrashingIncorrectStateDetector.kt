package com.hluhovskyi.zero.common

internal object CrashingIncorrectStateDetector : IncorrectStateDetector {

    override fun assert(message: String) {
        throw IllegalStateException(message)
    }

    override fun <T> assertOrValue(message: String, value: T): T {
        throw IllegalStateException(message)
    }

    override fun <T> requireNonNull(value: T?, message: String?, block: (T) -> Unit) {
        if (value == null) {
            assertNull(value)
        }
        block(value)
    }

    override suspend fun <T> asyncRequireNonNull(
        value: T?,
        message: String?,
        block: suspend (T) -> Unit
    ) {
        if (value == null) {
            assertNull(message)
        }
        block(value)
    }

    private fun assertNull(message: String?): Nothing {
        val resultMessage = message ?: "Provided value is required to be non null"
        throw IllegalStateException(resultMessage)
    }
}