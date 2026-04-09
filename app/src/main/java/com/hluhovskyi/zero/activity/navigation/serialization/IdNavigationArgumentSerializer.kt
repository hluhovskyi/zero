package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.valueOrEmpty
import kotlin.reflect.KClass

internal object IdNavigationArgumentSerializer : TypedNavigationArgumentSerializer<Id>() {
    override val actualClass: KClass<Id> = Id::class

    override fun performDeserialization(argument: Argument<Id>, rawValue: String): ArgumentValue<Id> = argument.withValue(Id(rawValue))

    override fun performSerialization(argumentValue: ArgumentValue<Id>): String = argumentValue.value.valueOrEmpty()
}
