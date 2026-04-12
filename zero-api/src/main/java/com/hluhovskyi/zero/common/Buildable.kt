package com.hluhovskyi.zero.common

/**
 * Wraps a Dagger Builder for lazy construction. Used by `NavigatorScope.buildable()` so
 * feature components are rebuilt on each navigation — giving fresh state per screen visit.
 */
interface Buildable<T> : () -> T {

    fun build(): T

    override fun invoke(): T = build()
}
