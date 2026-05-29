package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class BackupConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("backup") {

    object WifiOnly : BackupConfigurationKey<Boolean>(
        name = "wifi_only",
        defaultValue = true,
    )
}
