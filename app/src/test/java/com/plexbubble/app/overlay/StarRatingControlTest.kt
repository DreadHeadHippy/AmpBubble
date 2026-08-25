package com.plexbubble.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class StarRatingControlTest {

    @Test
    fun formatStarRating_omitsDecimalForWholeValues() {
        assertEquals("5/5", formatStarRating(5f))
        assertEquals("3/5", formatStarRating(3f))
    }

    @Test
    fun formatStarRating_preservesHalfValues() {
        assertEquals("4.5/5", formatStarRating(4.5f))
    }
}