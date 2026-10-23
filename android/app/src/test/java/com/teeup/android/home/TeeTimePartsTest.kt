package com.teeup.android.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeeTimePartsTest {
    @Test
    fun parse_splitsDateAndTime() {
        val parts = TeeTimeParts.parse("2026-10-24T07:40:00Z")!!
        assertEquals(24, parts.dayOfMonth)
        assertEquals(9, parts.monthIndex)
        // 24 Oct 2026 is a Saturday: Calendar.SATURDAY = 7 → index 6 with Sunday = 0.
        assertEquals(6, parts.weekdayIndex)
        assertEquals("07:40", parts.time)
    }

    @Test
    fun parse_acceptsFractionalSecondsAndOffsetsLikeTheApi() {
        assertEquals("14:45", TeeTimeParts.parse("2026-09-19T14:45:53.639622Z")!!.time)
    }

    @Test
    fun parse_rejectsMalformedOrOutOfRange() {
        assertNull(TeeTimeParts.parse("not a date"))
        assertNull(TeeTimeParts.parse("2026-13-01T07:40:00Z"))
        assertNull(TeeTimeParts.parse("2026-10-24T25:00:00Z"))
    }
}
