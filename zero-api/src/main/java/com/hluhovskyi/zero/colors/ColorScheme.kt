package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id

/**
 * Pair of [primary] and [background] colors used for theming categories and accounts.
 * [Grey] is the default fallback when no color is selected.
 */
data class ColorScheme(
    val swatch: Color,
    val primary: Color,
    val background: Color,
) {

    companion object {

        val Grey = ColorScheme(
            swatch = Color(id = Id("grey"), value = ColorValue(0xFF424242UL)),
            primary = Color(id = Id("grey_primary"), value = ColorValue(0xFF424242UL)),
            background = Color(id = Id("grey_background"), value = ColorValue(0xFFF5F5F5UL)),
        )
    }
}
