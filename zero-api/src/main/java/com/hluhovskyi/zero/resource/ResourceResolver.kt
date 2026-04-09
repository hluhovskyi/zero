package com.hluhovskyi.zero.resource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface ResourceResolver {

    fun <Request : ResourceRequest<Result>, Result> resolve(request: Request): Flow<ResourceStatus<Result>>

    object Noop : ResourceResolver {
        override fun <Request : ResourceRequest<Result>, Result> resolve(request: Request): Flow<ResourceStatus<Result>> = emptyFlow()
    }
}

interface ResourceRequest<T> {

    companion object
}

sealed interface ResourceStatus<T> {
    interface Idle<T> : ResourceStatus<T> {
        companion object
    }

    interface InProgress<T> : ResourceStatus<T> {
        companion object
    }

    interface Result<T> : ResourceStatus<T> {
        val result: T

        operator fun component1(): T = result

        companion object
    }
}
