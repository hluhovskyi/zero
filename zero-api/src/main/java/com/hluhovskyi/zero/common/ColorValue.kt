package com.hluhovskyi.zero.common

@JvmInline
value class ColorValue(val hex: ULong) {

    fun isUnspecified(): Boolean = this == UNSPECIFIED

    companion object {

        private val UNSPECIFIED = ColorValue(0x00000000UL)

        fun unspecified(): ColorValue = UNSPECIFIED
    }
}