package com.hluhovskyi.zero.common

import java.util.Locale

internal object SystemLocaleProvider : LocaleProvider {

    override fun locale(): Locale = Locale.getDefault()
}