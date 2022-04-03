package com.hluhovskyi.zero.common.coroutines

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart

@Suppress("unchecked_cast")
fun <T> castingFlowOf(value: Any): Flow<T> = flowOf(value as T)

@Suppress("unchecked_cast")
fun <T> castingFlowOfNonNull(value: Any?): Flow<T> = value?.let { flowOf(it as T) } ?: emptyFlow()

fun <T> Flow<List<T>>.onStartWithEmptyList(): Flow<List<T>> =
    onStart { emit(emptyList()) }

fun <T> Flow<List<T>>.onEmptyReturnEmptyList(): Flow<List<T>> =
    onEmpty { emit(emptyList()) }

fun <T : Identifiable> Flow<List<T>>.associateById(): Flow<Map<Id.Known, T>> =
    map { list -> list.associateBy { it.id } }

@Suppress("unchecked_cast")
fun <T, R> Flow<T>.uncheckedCast(): Flow<R> = this as Flow<R>