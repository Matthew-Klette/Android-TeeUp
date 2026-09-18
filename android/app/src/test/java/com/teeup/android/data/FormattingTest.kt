package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun formatTeeTime_parsesIsoUtcTimestamp() {
        assertEquals("19 Sep · 14:45", formatTeeTime("2026-09-19T14:45:53.639622Z"))
    }

    @Test
    fun formatTeeTime_handlesTimestampWithoutFractionalSeconds() {
        assertEquals("1 Jan · 09:00", formatTeeTime("2026-01-01T09:00:00Z"))
    }

    @Test
    fun formatPrice_zeroIsFree() {
        assertEquals("Free", formatPrice(0.0))
    }

    @Test
    fun formatPrice_roundsToWholeRand() {
        assertEquals("R450", formatPrice(450.0))
    }
}
