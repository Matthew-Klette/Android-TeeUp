package com.teeup.android.rounds

import com.teeup.android.data.HoleScore
import com.teeup.android.data.PlayedRound
import com.teeup.android.data.ScheduledRound
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundActionTest {
    private val now = 1_800_000_000_000L // 2027-01-15T08:00:00Z
    private val past = "2027-01-15T07:00:00Z"
    private val future = "2027-01-15T09:00:00Z"

    private fun played(holesScored: Int) = PlayedRound(
        id = "r", teeTimeId = "t", totalStrokes = 0, totalPutts = 0, averagePutts = 0.0, netScore = null, stablefordScore = null,
        scorecard = (1..holesScored).map { HoleScore("h$it", it, 4, 2, synced = true) }
    )

    private fun round(dateTime: String = past, holes: Int? = 18, played: PlayedRound? = null) =
        ScheduledRound("t", "c", dateTime, holes, played)

    @Test
    fun notStarted_beforeTeeTime_cantStartYet() {
        // The API rejects a scorecard before the tee time, so don't offer one.
        assertEquals(RoundAction.NotYetStartable, RoundAction.of(round(dateTime = future), now))
    }

    @Test
    fun notStarted_afterTeeTime_startsWithTheGroupsHoleCount() {
        assertEquals(RoundAction.Start(fixedHoles = 9), RoundAction.of(round(holes = 9), now))
    }

    @Test
    fun notStarted_legacyRowWithoutHoles_asksForHoleCount() {
        assertEquals(RoundAction.Start(fixedHoles = null), RoundAction.of(round(holes = null), now))
    }

    @Test
    fun partlyScored_continuesWithTheRoundsLength() {
        assertEquals(RoundAction.Continue(totalHoles = 18), RoundAction.of(round(played = played(9)), now))
    }

    @Test
    fun allNineOfANineHoleRound_isFinished() {
        assertEquals(RoundAction.ViewSummary, RoundAction.of(round(holes = 9, played = played(9)), now))
    }

    @Test
    fun legacyRowsCountAsEighteenHoles() {
        assertEquals(RoundAction.Continue(totalHoles = 18), RoundAction.of(round(holes = null, played = played(9)), now))
        assertEquals(RoundAction.ViewSummary, RoundAction.of(round(holes = null, played = played(18)), now))
    }

    @Test
    fun startedRound_inTheFuture_canStillContinue() {
        // Only an unstarted round is held back by the tee time.
        assertEquals(RoundAction.Continue(totalHoles = 18), RoundAction.of(round(dateTime = future, played = played(3)), now))
    }

    @Test
    fun progress_isScoredOverTotal() {
        assertEquals(9 to 18, RoundAction.progress(round(played = played(9))))
        assertEquals(0 to 9, RoundAction.progress(round(holes = 9)))
    }
}
