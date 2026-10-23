package com.teeup.android.summary

import com.teeup.android.data.HoleScore
import com.teeup.android.data.PlayedRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SummaryStatsTest {
    private fun played(netScore: Double? = 70.4, stableford: Int? = 34, holes: List<Int> = listOf(3, 1, 2)) = PlayedRound(
        id = "r", teeTimeId = "t", totalStrokes = 12, totalPutts = 5, averagePutts = 1.6666, netScore = netScore, stablefordScore = stableford,
        scorecard = holes.map { HoleScore("h$it", it, 4, 2, synced = true) }
    )

    @Test
    fun totalsComeFromTheServer_holesSorted() {
        val stats = SummaryStats.from(played(), Locale.US)
        assertEquals(3, stats.holesPlayed)
        assertEquals(12, stats.totalStrokes)
        assertEquals(5, stats.totalPutts)
        assertEquals(listOf(1, 2, 3), stats.holes.map { it.holeNumber })
    }

    @Test
    fun averagesAndNet_oneDecimalInTheLocale() {
        assertEquals("1.7", SummaryStats.from(played(), Locale.US).averagePutts)
        assertEquals("70.4", SummaryStats.from(played(), Locale.US).netScore)
        assertEquals("70,4", SummaryStats.from(played(), Locale.GERMANY).netScore)
    }

    @Test
    fun noHandicap_netAndStablefordUnavailable_withTheHint() {
        val stats = SummaryStats.from(played(netScore = null, stableford = null), Locale.US)
        assertNull(stats.netScore)
        assertNull(stats.stableford)
        assertTrue(stats.showHandicapHint)
    }

    @Test
    fun hintOnlyWhenThereAreHoles() {
        assertFalse(SummaryStats.from(played(netScore = null, holes = emptyList()), Locale.US).showHandicapHint)
        assertFalse(SummaryStats.from(played(), Locale.US).showHandicapHint)
    }

    @Test
    fun noRoundYet_zeroTotals_nothingElse() {
        val stats = SummaryStats.from(null, Locale.US)
        assertEquals(0, stats.holesPlayed)
        assertEquals(0, stats.totalStrokes)
        assertEquals(0, stats.totalPutts)
        assertNull(stats.averagePutts)
        assertNull(stats.roundId)
        assertFalse(stats.showHandicapHint)
    }
}
