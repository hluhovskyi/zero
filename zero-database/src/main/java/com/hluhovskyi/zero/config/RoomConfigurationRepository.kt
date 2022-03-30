package com.hluhovskyi.zero.config

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.requireCurrentUserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

internal class RoomConfigurationRepository(
    private val configurationRoom: () -> ConfigurationRoom,
    private val currentUserId: Flow<Id.Known>,
    private val incorrectStateDetector: IncorrectStateDetector,
) : ConfigurationRepository {

    override fun <Value : Any> observe(
        key: ConfigurationRepository.Key<Value>,
        valueClass: KClass<Value>
    ): Flow<Value> = configurationRoom().observe(key.name)
        .map { entity ->
            mapToValue(
                entity = entity,
                key = key,
                klass = valueClass,
            )
        }
        .onStart {
            emit(
                mapToValue(
                    entity = configurationRoom().get(key.name),
                    key = key,
                    klass = valueClass
                )
            )
        }
        .distinctUntilChanged()

    override suspend fun <Value : Any> write(
        key: ConfigurationRepository.Key<Value>,
        valueClass: KClass<Value>,
        value: Value
    ) {
        incorrectStateDetector.requireCurrentUserId(currentUserId) { userId ->
            val rawValue = when (valueClass) {
                Boolean::class,
                Long::class,
                Int::class -> value.toString()

                String::class -> value as String

                else -> throw IllegalStateException("Unsupported class $valueClass, key=$key, value=$value")
            }

            configurationRoom().insert(
                ConfigurationEntity(
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
        key: ConfigurationRepository.Key<Value>,
        klass: KClass<Value>
    ): Value {
        val value = when (klass) {
            Boolean::class -> entity.value.toBoolean()
            Long::class -> entity.value.toLongOrNull()
            Int::class -> entity.value.toIntOrNull()
            String::class -> entity.value
            else -> throw IllegalStateException("Unsupported class $klass, key=$key, entity=$entity")
        } as? Value

        return value ?: key.defaultValue
    }
}