package com.teeup.android.notifications

import com.teeup.android.data.HoleScore
import com.teeup.android.data.PlayedRound
import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.teeTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeeTimeRemindersTest {
    private val now = teeTimeMillis("2026-11-03T10:00:00Z")!!
    private val names = mapOf("c1" to "Humewood Golf Club")
    private val hourMs = 60 * 60 * 1000L

    private fun round(id: String, at: String, holes: Int? = 18, scored: Int = 0) = ScheduledRound(
        teeTimeId = id,
        courseId = "c1",
        dateTime = at,
        holes = holes,
        round = if (scored == 0) null else PlayedRound(
            id = "r-$id", teeTimeId = id,
            scorecard = (1..scored).map { HoleScore("h$it", it, 4, 2, synced = true) },
            totalStrokes = 4 * scored, totalPutts = 2 * scored, averagePutts = 2.0, netScore = null, stablefordScore = null
        )
    )

    @Test fun remindsAnHourBeforeAnUpcomingRound() {
        val plans = TeeTimeReminders.plan(listOf(round("a", "2026-11-03T14:00:00Z")), names, "?", now)
        assertEquals(1, plans.size)
        val plan = plans.single()
        assertEquals("Humewood Golf Club", plan.courseName)
        assertEquals(plan.teeAtMillis - hourMs, plan.fireAtMillis)
        assertEquals(teeTimeMillis("2026-11-03T14:00:00Z"), plan.teeAtMillis)
    }

    @Test fun skipsRoundsWhoseReminderTimeHasPassed() {
        // Tees off in 30 minutes: the hour-before mark is gone, and a late reminder is noise.
        val plans = TeeTimeReminders.plan(listOf(round("a", "2026-11-03T10:30:00Z"), round("b", "2026-11-02T08:00:00Z")), names, "?", now)
        assertTrue(plans.isEmpty())
    }

    @Test fun skipsFinishedRoundsAndUnreadableDates() {
        val plans = TeeTimeReminders.plan(
            listOf(round("done", "2026-11-04T08:00:00Z", holes = 9, scored = 9), round("bad", "soon")),
            names, "?", now
        )
        assertTrue(plans.isEmpty())
    }

    @Test fun keepsAPartlyScoredUpcomingRound() {
        val plans = TeeTimeReminders.plan(listOf(round("part", "2026-11-04T08:00:00Z", holes = 18, scored = 3)), names, "?", now)
        assertEquals(listOf("part"), plans.map { it.teeTimeId })
    }

    @Test fun skipsRoundsBeyondTheHorizon() {
        val plans = TeeTimeReminders.plan(listOf(round("far", "2027-03-01T08:00:00Z")), names, "?", now)
        assertTrue(plans.isEmpty())
    }

    @Test fun unknownCourseUsesFallbackName() {
        val r = round("a", "2026-11-03T14:00:00Z").copy(courseId = "missing")
        assertEquals("Unknown course", TeeTimeReminders.plan(listOf(r), names, "Unknown course", now).single().courseName)
    }
}
