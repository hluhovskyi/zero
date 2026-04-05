package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable

/** A named color with a [ColorValue]. Used within [ColorScheme]. */
data class Color(
    override val id: Id.Known,
    val value: ColorValue
) : Identifiable {

    companion object {

        fun empty(): Color = Color(
            id = Id("empty_color"),
            value = ColorValue.unspecified()
        )
    }
}

