package com.teeup.android.scorecard

import com.teeup.android.data.HoleScore
import com.teeup.android.data.PlayedRound
import com.teeup.android.data.ScheduledRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorecardLogicTest {
    private fun played(holes: Int) = PlayedRound(
        id = "r", teeTimeId = "t", totalStrokes = 0, totalPutts = 0, averagePutts = 0.0, netScore = null, stablefordScore = null,
        scorecard = (1..holes).map { HoleScore("h$it", it, 4, 2, synced = true) }
    )

    private fun round(id: String, date: String, holes: Int? = 18, scored: Int? = null) =
        ScheduledRound(id, "c", date, holes, scored?.let(::played))

    @Test
    fun landing_listsOnlyStartedRounds_newestFirst() {
        val items = ScorecardLanding.items(
            listOf(
                round("old", "2026-09-01T08:00:00Z", scored = 3),
                round("unstarted", "2026-10-01T08:00:00Z"),
                round("new", "2026-10-20T08:00:00Z", scored = 1)
            )
        )
        assertEquals(listOf("new", "old"), items.map { it.round.teeTimeId })
    }

    @Test
    fun landing_completeWhenEveryHoleOfItsLengthIsScored() {
        val items = ScorecardLanding.items(
            listOf(
                round("nine", "2026-10-02T08:00:00Z", holes = 9, scored = 9),
                round("partial", "2026-10-01T08:00:00Z", holes = 18, scored = 9),
                round("legacy", "2026-09-30T08:00:00Z", holes = null, scored = 18)
            )
        )
        assertTrue(items[0].isComplete)
        assertFalse(items[1].isComplete)
        assertEquals(18, items[2].totalHoles)
        assertTrue(items[2].isComplete)
    }

    @Test
    fun holeStep_movesThroughMappedHolesOnly() {
        val mapped = listOf(1, 2, 4, 5)
        assertEquals(4, HoleStep.next(mapped, current = 2, delta = 1))
        assertEquals(2, HoleStep.next(mapped, current = 4, delta = -1))
    }

    @Test
    fun holeStep_stopsAtTheEnds() {
        val mapped = listOf(1, 2, 3)
        assertNull(HoleStep.next(mapped, current = 3, delta = 1))
        assertNull(HoleStep.next(mapped, current = 1, delta = -1))
    }

    @Test
    fun holeStep_unmappedCurrentHoleGoesNowhere() {
        assertNull(HoleStep.next(listOf(1, 2), current = 7, delta = 1))
    }

    @Test
    fun holeStep_canStep_reportsBothDirections() {
        val mapped = listOf(1, 2, 3)
        assertEquals(false to true, HoleStep.canStep(mapped, 1))
        assertEquals(true to true, HoleStep.canStep(mapped, 2))
        assertEquals(true to false, HoleStep.canStep(mapped, 3))
        assertEquals(false to false, HoleStep.canStep(emptyList(), 0))
    }
}
