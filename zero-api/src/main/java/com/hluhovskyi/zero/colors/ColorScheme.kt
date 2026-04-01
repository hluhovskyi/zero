package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id

data class ColorScheme(
    val primary: Color,
    val background: Color,
) {

    companion object {

        val Grey = ColorScheme(
            primary = Color(id = Id("grey_primary"), value = ColorValue(0xFF424242UL)),
            background = Color(id = Id("grey_background"), value = ColorValue(0xFFF5F5F5UL)),
        )
    }
}
