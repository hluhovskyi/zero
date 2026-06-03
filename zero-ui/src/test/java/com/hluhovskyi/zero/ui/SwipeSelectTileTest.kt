package com.hluhovskyi.zero.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeSelectTileTest {

    private fun resolve(dy: Float, canPrev: Boolean = true, canNext: Boolean = true) =
        resolveSwipe(
            totalDy = dy,
            commitThreshold = 20f,
            tapSlop = 6f,
            canSelectPrevious = canPrev,
            canSelectNext = canNext,
        )

    @Test
    fun `swipe up past threshold selects next`() {
        assertEquals(SwipeOutcome.Next, resolve(dy = -30f))
    }

    @Test
    fun `swipe down past threshold selects previous`() {
        assertEquals(SwipeOutcome.Previous, resolve(dy = 30f))
    }

    @Test
    fun `tiny movement is a tap`() {
        assertEquals(SwipeOutcome.Tap, resolve(dy = 3f))
    }

    @Test
    fun `mid drag that is neither tap nor commit does nothing`() {
        assertEquals(SwipeOutcome.None, resolve(dy = 12f))
    }

    @Test
    fun `swipe next at end bounces to none`() {
        assertEquals(SwipeOutcome.None, resolve(dy = -30f, canNext = false))
    }

    @Test
    fun `swipe previous at start bounces to none`() {
        assertEquals(SwipeOutcome.None, resolve(dy = 30f, canPrev = false))
    }
}
