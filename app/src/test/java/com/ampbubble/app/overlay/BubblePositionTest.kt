package com.ampbubble.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePositionTest {

    @Test
    fun keepsBubbleInsideSystemUiSafeArea() {
        val safeBounds = BubbleSafeBounds(left = 24, top = 80, right = 456, bottom = 840)

        assertEquals(24 to 80, clampBubblePosition(-100, -50, 56, 56, safeBounds))
        assertEquals(400 to 784, clampBubblePosition(900, 1200, 56, 56, safeBounds))
    }

    @Test
    fun clampsOldPositionAfterRotationToSmallerDisplay() {
        val portraitBounds = BubbleSafeBounds(left = 0, top = 64, right = 400, bottom = 850)
        val landscapeBounds = BubbleSafeBounds(left = 0, top = 32, right = 850, bottom = 400)

        val portraitPosition = clampBubblePosition(320, 760, 56, 56, portraitBounds)
        val landscapePosition = clampBubblePosition(
            portraitPosition.first,
            portraitPosition.second,
            56,
            56,
            landscapeBounds
        )

        assertEquals(320 to 760, portraitPosition)
        assertEquals(320 to 344, landscapePosition)
    }
}
