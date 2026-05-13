package com.hluhovskyi.zero.security

import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class BiometricConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("security") {

    object Enabled : BiometricConfigurationKey<Boolean>(
        name = "biometric_lock_enabled",
        defaultValue = false,
    )
}
