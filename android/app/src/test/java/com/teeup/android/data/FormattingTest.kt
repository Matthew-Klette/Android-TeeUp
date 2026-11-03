package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    private val UTC = java.util.TimeZone.getTimeZone("UTC")
    private val SAST = java.util.TimeZone.getTimeZone("Africa/Johannesburg")

    @Test
    fun formatTeeTime_showsSouthAfricanLocalTime() {
        // 06:00 UTC is an 08:00 tee time in Gqeberha: it must not show as 06:00.
        assertEquals("19 Sep · 08:00", formatTeeTime("2026-09-19T06:00:00Z", SAST))
    }

    @Test
    fun formatTeeTime_rollsOverToTheNextLocalDay() {
        assertEquals("1 Jan · 00:30", formatTeeTime("2025-12-31T22:30:00Z", SAST))
    }

    @Test
    fun teeTimeMillis_isTheUtcInstant() {
        assertEquals(1_789_797_600_000L, teeTimeMillis("2026-09-19T06:00:00Z"))
        assertEquals(null, teeTimeMillis("2026-02-31T06:00:00Z"))
        assertEquals(null, teeTimeMillis("not a date"))
    }

    @Test
    fun formatTeeTime_parsesIsoUtcTimestamp() {
        assertEquals("19 Sep · 14:45", formatTeeTime("2026-09-19T14:45:53.639622Z", UTC))
    }

    @Test
    fun formatTeeTime_handlesTimestampWithoutFractionalSeconds() {
        assertEquals("1 Jan · 09:00", formatTeeTime("2026-01-01T09:00:00Z", UTC))
    }

    @Test
    fun formatPrice_zeroIsFree() {
        assertEquals("Free", formatPrice(0.0))
    }

    @Test
    fun formatPrice_roundsToWholeRand() {
        assertEquals("R450", formatPrice(450.0))
    }

    @Test
    fun canRequestToJoin_trueWhenNoExistingRequest() {
        assertEquals(true, canRequestToJoin(emptyList(), "user-1"))
    }

    @Test
    fun canRequestToJoin_falseWhenAlreadyPending() {
        val existing = listOf(JoinRequest("jr-1", "tt-1", "user-1", "Test Golfer", JoinRequestStatus.PENDING))
        assertEquals(false, canRequestToJoin(existing, "user-1"))
    }

    @Test
    fun canRequestToJoin_falseWhenAlreadyAccepted() {
        val existing = listOf(JoinRequest("jr-1", "tt-1", "user-1", "Test Golfer", JoinRequestStatus.ACCEPTED))
        assertEquals(false, canRequestToJoin(existing, "user-1"))
    }

    @Test
    fun canRequestToJoin_trueAgainAfterDeclined() {
        val existing = listOf(JoinRequest("jr-1", "tt-1", "user-1", "Test Golfer", JoinRequestStatus.DECLINED))
        assertEquals(true, canRequestToJoin(existing, "user-1"))
    }

    @Test
    fun canRequestToJoin_ignoresOtherUsersRequests() {
        val existing = listOf(JoinRequest("jr-1", "tt-1", "someone-else", "Test Golfer", JoinRequestStatus.PENDING))
        assertEquals(true, canRequestToJoin(existing, "user-1"))
    }
}
