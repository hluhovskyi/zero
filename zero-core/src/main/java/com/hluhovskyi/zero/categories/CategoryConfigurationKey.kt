package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.config.Scope
import com.hluhovskyi.zero.config.ScopedConfigurationKey
import com.hluhovskyi.zero.config.scopeOf

internal sealed class CategoryConfigurationKey<Type>(
    override val name: String,
    override val defaultValue: Type,
) : ScopedConfigurationKey<Type>,
    Scope by scopeOf("categories") {

    object HasAddedCategory : CategoryConfigurationKey<Boolean>(
        name = "has_added_category",
        defaultValue = false,
    )

    object RankingSignalsEnabled : CategoryConfigurationKey<Boolean>(
        name = "ranking_signals_enabled",
        defaultValue = false,
    )
}
