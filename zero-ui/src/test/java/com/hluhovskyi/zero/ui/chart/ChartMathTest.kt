package com.hluhovskyi.zero.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {
    @Test fun `empty values normalize to empty`() {
        assertEquals(emptyList<Float>(), normalizeToFractions(emptyList()))
    }

    @Test fun `single value normalizes to mid`() {
        assertEquals(listOf(0.5f), normalizeToFractions(listOf(42f)))
    }

    @Test fun `flat series normalizes to mid for all`() {
        assertEquals(listOf(0.5f, 0.5f, 0.5f), normalizeToFractions(listOf(7f, 7f, 7f)))
    }

    @Test fun `min maps to 0 and max maps to 1`() {
        val out = normalizeToFractions(listOf(10f, 20f, 30f))
        assertEquals(0f, out.first(), 0.0001f)
        assertEquals(1f, out.last(), 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
    }

    @Test fun `adaptive bar width shrinks as buckets grow`() {
        assertEquals(12, adaptiveBarWidthDp(6))
        assertEquals(10, adaptiveBarWidthDp(9))
        assertEquals(8, adaptiveBarWidthDp(12))
        assertEquals(5, adaptiveBarWidthDp(16))
        assertEquals(3, adaptiveBarWidthDp(24))
    }

    @Test fun `labels hidden beyond 14 buckets`() {
        assertEquals(true, shouldShowBarLabels(14))
        assertEquals(false, shouldShowBarLabels(15))
    }
}
