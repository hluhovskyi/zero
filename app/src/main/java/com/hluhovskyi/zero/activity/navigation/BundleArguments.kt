package com.hluhovskyi.zero.activity.navigation

import android.os.Bundle
import kotlin.reflect.KClass

internal class BundleArguments(
    private val bundle: Bundle?,
    private val destination: Destination,
) : NavigatorEntry.Arguments {

    @Suppress("unchecked_cast")
    override fun <T : Any> get(key: Argument<T>, argumentClass: KClass<T>): ArgumentValue<T> {
        if (bundle == null) {
            assertMissingArguments(key)
        }

        return when (argumentClass) {
            String::class -> {
                val value = bundle.getString(key.key)
                if (!key.optional && value == null) {
                    assertMissingArguments(key)
                }

                StringArgumentValue(
                    argument = key as Argument<String>,
                    // TODO: Add support for optional fallback in ArgumentValue
                    value = value.orEmpty()
                ) as ArgumentValue<T>
            }
            else -> assertMissingArguments(key)
        }
    }

    // TODO: Fallback and report instead of crash
    private fun assertMissingArguments(key: Argument<*>): Nothing {
        throw IllegalStateException("Argument with key=${key.key} expected to be passed for destination=${destination.route}")
    }
}