package com.teeup.android.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeeTimePartsTest {
    private val UTC = java.util.TimeZone.getTimeZone("UTC")
    private val SAST = java.util.TimeZone.getTimeZone("Africa/Johannesburg")

    @Test
    fun parse_showsLocalTimeAndDay() {
        // 22:30 UTC on Saturday 31 Oct is 00:30 on Sunday 1 Nov in South Africa.
        val parts = TeeTimeParts.parse("2026-10-31T22:30:00Z", SAST)!!
        assertEquals("00:30", parts.time)
        assertEquals(1, parts.dayOfMonth)
        assertEquals(10, parts.monthIndex) // November
        assertEquals(0, parts.weekdayIndex) // Sunday
    }

    @Test
    fun localDate_isThePhonesDay() {
        assertEquals("2026-11-01", HomeFiltering.localDate("2026-10-31T22:30:00Z", SAST))
        assertEquals("2026-10-31", HomeFiltering.localDate("2026-10-31T22:30:00Z", UTC))
        assertEquals("", HomeFiltering.localDate("garbage", SAST))
    }

    @Test
    fun parse_splitsDateAndTime() {
        val parts = TeeTimeParts.parse("2026-10-24T07:40:00Z", UTC)!!
        assertEquals(24, parts.dayOfMonth)
        assertEquals(9, parts.monthIndex)
        // 24 Oct 2026 is a Saturday: Calendar.SATURDAY = 7 → index 6 with Sunday = 0.
        assertEquals(6, parts.weekdayIndex)
        assertEquals("07:40", parts.time)
    }

    @Test
    fun parse_acceptsFractionalSecondsAndOffsetsLikeTheApi() {
        assertEquals("14:45", TeeTimeParts.parse("2026-09-19T14:45:53.639622Z", UTC)!!.time)
    }

    @Test
    fun parse_rejectsMalformedOrOutOfRange() {
        assertNull(TeeTimeParts.parse("not a date"))
        assertNull(TeeTimeParts.parse("2026-13-01T07:40:00Z"))
        assertNull(TeeTimeParts.parse("2026-10-24T25:00:00Z"))
    }
}
