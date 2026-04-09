package com.hluhovskyi.zero.currencies

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class CurrencyConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("currency") {

    object PrimaryCurrency : CurrencyConfigurationKey<Id>(
        name = "primary_currency",
        defaultValue = Id.Unknown,
    )
}
