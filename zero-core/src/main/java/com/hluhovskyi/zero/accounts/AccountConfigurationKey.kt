package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class AccountConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("accounts") {

    object HasAddedAccount : AccountConfigurationKey<Boolean>(
        name = "has_added_account",
        defaultValue = false,
    )
}
