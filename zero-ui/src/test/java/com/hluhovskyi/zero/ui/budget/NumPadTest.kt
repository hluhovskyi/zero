package com.hluhovskyi.zero.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class NumPadTest {

    @Test
    fun `backspace on single digit emits zero`() {
        assertEquals("0", handleNumPadKey("5", "⌫"))
    }

    @Test
    fun `backspace on zero emits zero`() {
        assertEquals("0", handleNumPadKey("0", "⌫"))
    }

    @Test
    fun `dot is ignored when value already contains dot`() {
        assertEquals("12.5", handleNumPadKey("12.5", "."))
    }

    @Test
    fun `digit replaces zero when value is zero`() {
        assertEquals("7", handleNumPadKey("0", "7"))
    }

    @Test
    fun `digit is rejected when two decimal places already present`() {
        assertEquals("12.50", handleNumPadKey("12.50", "3"))
    }
}
