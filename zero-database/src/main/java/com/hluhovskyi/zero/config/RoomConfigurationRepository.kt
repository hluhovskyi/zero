package com.hluhovskyi.zero.config

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.requireCurrentUserId
import com.hluhovskyi.zero.common.valueOrEmpty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

private const val DEFAULT_SCOPE = "zero"

internal class RoomConfigurationRepository(
    private val configurationRoom: () -> ConfigurationRoom,
    private val currentUserId: Flow<Id.Known>,
    private val incorrectStateDetector: IncorrectStateDetector,
    logger: Logger,
    private val defaultScope: String = DEFAULT_SCOPE
) : ConfigurationRepository {

    private val logger = logger.withTag("RoomConfigurationRepository")

    override fun <Value : Any> observe(
        key: ConfigurationKey<Value>,
        valueClass: KClass<Value>
    ): Flow<Value> = configurationRoom().observe(key.scope, key.name)
        .map { entity ->
            if (entity == null) {
                key.defaultValue
            } else {
                mapToValue(
                    entity = entity,
                    key = key,
                    klass = valueClass,
                )
            }.also {
                logger.d("observe, value=$it")
            }
        }
        .onStart {
            configurationRoom().get(key.scope, key.name)?.let { entity ->
                emit(
                    mapToValue(
                        entity = entity,
                        key = key,
                        klass = valueClass
                    )
                )
            }
        }
        .distinctUntilChanged()

    override suspend fun <Value : Any> write(
        key: ConfigurationKey<Value>,
        valueClass: KClass<Value>,
        value: Value
    ) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            val rawValue = when (valueClass) {
                Boolean::class,
                Long::class,
                Int::class -> value.toString()

                String::class -> value as String

                Id::class -> (value as Id).valueOrEmpty()

                else -> throw IllegalStateException("Unsupported class $valueClass, key=$key, value=$value")
            }

            configurationRoom().insert(
                ConfigurationEntity(
                    scope = key.scope,
                    name = key.name,
                    userId = userId,
                    value = rawValue,
                )
            )
        }
    }

    @Suppress("unchecked_cast")
    private fun <Value : Any> mapToValue(
        entity: ConfigurationEntity,
        key: ConfigurationKey<Value>,
        klass: KClass<Value>
    ): Value {
        val value = when (klass) {
            Boolean::class -> entity.value.toBoolean()
            Long::class -> entity.value.toLongOrNull()
            Int::class -> entity.value.toIntOrNull()
            String::class -> entity.value
            Id::class -> Id(entity.value)
            else -> throw IllegalStateException("Unsupported class $klass, key=$key, entity=$entity")
        } as? Value

        return value ?: key.defaultValue
    }

    private val ConfigurationKey<*>.scope: String
        get() = if (this is ScopedConfigurationKey<*>) {
            scope
        } else {
            defaultScope
        }
}