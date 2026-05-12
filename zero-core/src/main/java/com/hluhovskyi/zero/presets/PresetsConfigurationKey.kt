package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class PresetsConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("presets") {

    object PresetsSeeded : PresetsConfigurationKey<Boolean>(
        name = "seeded",
        defaultValue = false,
    )
}
