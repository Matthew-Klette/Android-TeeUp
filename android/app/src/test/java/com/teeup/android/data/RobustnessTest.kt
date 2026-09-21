package com.teeup.android.data

import org.junit.Assert.*
import org.junit.Test

class RobustnessTest {
    @Test fun invalidHandicapsAreRejectedWithoutExceptions() {
        listOf("-1", "55", "1.25", "NaN", "Infinity", "1e3", "abc", ".", "1,2,3").forEach {
            assertFalse(it, validHandicap(it))
        }
        listOf("", "  ", "0", "54", "12.5", "12,5", "12.50").forEach {
            assertTrue(it, validHandicap(it))
        }
        assertEquals(12.5, handicapValue("12,5")!!, 0.0)
    }

    @Test fun nameBoundsMatchServer() {
        assertFalse(validDisplayName("   "))
        assertFalse(validDisplayName("x".repeat(101)))
        assertTrue(validDisplayName("x".repeat(100)))
        assertTrue(validDisplayName("  Golfer  "))
    }

    @Test fun malformedDatesCannotCrashRendering() {
        listOf("", "bad date", "2026-99-99T99:99", "2026-ab-xxT00:00", "2026-00-10T10:00").forEach {
            assertEquals("Date unavailable", formatTeeTime(it))
        }
    }

    @Test fun httpErrorsAreActionableWithoutServerDetails() {
        assertTrue(httpFailureMessage(401).contains("sign in"))
        assertTrue(httpFailureMessage(404).contains("no longer available"))
        assertTrue(httpFailureMessage(500).contains("unavailable"))
        assertTrue(httpFailureMessage(429).contains("Wait"))
    }

    /**
     * EME-305 QA pass: `roundTimestamp`'s regex only checks digit shape
     * (`\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}`), so an out-of-range value like
     * month 13 or hour 25 still reaches `SimpleDateFormat.parse`. That must
     * surface as the same `IllegalArgumentException` every other malformed
     * date does — caught by RoundsViewModel.load() — never an unchecked NPE.
     */
    @Test fun outOfRangeRoundTimestampsThrowIllegalArgumentWithoutCrashing() {
        listOf(
            "2026-13-40T25:99:99Z",
            "2026-02-30T10:00:00Z",
            "not-a-date",
            ""
        ).forEach { value ->
            try {
                roundTimestamp(value)
                fail("Expected IllegalArgumentException for \"$value\"")
            } catch (e: IllegalArgumentException) {
                // Expected — this is the contract callers rely on.
            }
        }
    }

    @Test fun wellFormedRoundTimestampsStillParse() {
        assertTrue(roundTimestamp("2026-09-19T14:45:53.639622Z") > 0)
        assertTrue(roundTimestamp("2026-09-19T14:45:53") > 0)
    }
}
