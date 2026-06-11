package com.hluhovskyi.zero.activity.navigation

import android.os.Bundle
import com.hluhovskyi.zero.activity.navigation.serialization.TransactionFilterNavigationArgumentSerializer
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionFilter

internal class BundleArguments(
    private val bundle: Bundle?,
    private val destination: Destination,
) : NavigatorEntry.Arguments {

    @Suppress("unchecked_cast")
    override fun <T : Any> get(key: Argument<T>): ArgumentValue<T> {
        if (bundle == null) {
            assertMissingArguments(key)
        }

        return when (key.argumentClass) {
            String::class -> {
                val value = bundle.getString(key.key)
                if (!key.optional && value == null) {
                    assertMissingArguments(key)
                }

                GenericArgumentValue(
                    argument = key,
                    // TODO: Add support for optional fallback in ArgumentValue
                    value = value.orEmpty() as T,
                )
            }
            Id::class, Id.Known::class -> {
                val value = bundle.getString(key.key)
                if (!key.optional && value == null) {
                    assertMissingArguments(key)
                }

                GenericArgumentValue(
                    argument = key,
                    // TODO: Add support for optional fallback in ArgumentValue
                    value = Id(value) as T,
                )
            }
            TransactionFilter::class -> {
                val value = bundle.getString(key.key)
                if (!key.optional && value == null) {
                    assertMissingArguments(key)
                }

                TransactionFilterNavigationArgumentSerializer.deserialize(key, value.orEmpty())
            }
            else -> assertMissingArguments(key)
        }
    }

    // TODO: Fallback and report instead of crash
    private fun assertMissingArguments(key: Argument<*>): Nothing = throw IllegalStateException("Argument with key=${key.key} expected to be passed for destination=${destination.route}")
}
