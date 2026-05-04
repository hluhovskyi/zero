package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import kotlin.reflect.KClass

internal object IdKnownNavigationArgumentSerializer : TypedNavigationArgumentSerializer<Id.Known>() {
    override val actualClass: KClass<Id.Known> = Id.Known::class

    override fun performDeserialization(argument: Argument<Id.Known>, rawValue: String): ArgumentValue<Id.Known> = argument.withValue(Id.Known(rawValue))

    override fun performSerialization(argumentValue: ArgumentValue<Id.Known>): String = argumentValue.value.value
}
