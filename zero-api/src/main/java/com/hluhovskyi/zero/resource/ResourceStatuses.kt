package com.hluhovskyi.zero.resource

@Suppress("unchecked_cast")
operator fun <T> ResourceStatus.Idle.Companion.invoke(): ResourceStatus.Idle<T> =
    AnyIdle as ResourceStatus.Idle<T>

@Suppress("unchecked_cast")
operator fun <T> ResourceStatus.InProgress.Companion.invoke(): ResourceStatus.InProgress<T> =
    AnyInProgress as ResourceStatus.InProgress<T>

operator fun <T> ResourceStatus.Result.Companion.invoke(value: T): ResourceStatus.Result<T> =
    ValueResult(value)

private object AnyIdle : ResourceStatus.Idle<Any>

private object AnyInProgress : ResourceStatus.InProgress<Any>

private class ValueResult<T>(
    override val result: T
) : ResourceStatus.Result<T>