package com.hluhovskyi.zero.common

/**
 * ARGB hex color as an inline value class.
 *
 * To convert to Compose Color: `ComposeColor(colorValue.hex.toInt())`.
 * Do NOT pass [hex] directly to `ComposeColor(ULong)` — that constructor encodes colorspace
 * bits in the lower 6 bits and will produce wrong colors.
 */
@JvmInline
value class ColorValue(val hex: ULong) {

    fun isUnspecified(): Boolean = this == UNSPECIFIED

    companion object {

        private val UNSPECIFIED = ColorValue(0x00000000UL)

        fun unspecified(): ColorValue = UNSPECIFIED
    }
}
