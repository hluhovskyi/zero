package com.hluhovskyi.zero.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher

interface DispatcherProvider {

    fun io(): CoroutineDispatcher

    fun cpu(): CoroutineDispatcher

    fun main(): CoroutineDispatcher
}
