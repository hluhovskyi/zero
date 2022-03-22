package com.hluhovskyi.zero.common

@JvmInline
value class Color(val hex: ULong) {

    companion object {

        private val UNSPECIFIED = Color(0x00000000UL)

        fun unspecified(): Color = UNSPECIFIED
    }
}