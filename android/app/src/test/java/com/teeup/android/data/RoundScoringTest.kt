package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundScoringTest {
    private fun score(hole: Int, strokes: Int = 4, putts: Int = 2) = HoleScoreInput(hole, strokes, putts)

    private fun scoring(totalHoles: Int = 9, saved: List<Int> = emptyList(), pending: List<Int> = emptyList()) =
        RoundScoring(totalHoles, saved.associateWith(::score), pending.associateWith(::score))

    @Test
    fun entryOrNull_validScore_returnsEntry() {
        assertEquals(score(3, 5, 2), RoundScoring.entryOrNull(3, 5, 2))
    }

    @Test
    fun entryOrNull_rejectsMissingZeroOrTooManyPutts() {
        assertNull(RoundScoring.entryOrNull(1, null, 2))
        assertNull(RoundScoring.entryOrNull(1, 4, null))
        assertNull(RoundScoring.entryOrNull(1, 0, 0))
        assertNull(RoundScoring.entryOrNull(1, 4, -1))
        assertNull(RoundScoring.entryOrNull(1, 3, 4))
    }

    @Test
    fun entryOrNull_allowsAHoleInOne() {
        assertEquals(score(7, 1, 0), RoundScoring.entryOrNull(7, 1, 0))
    }

    @Test
    fun withEntry_replacesAPendingScoreForTheSameHole() {
        val edited = scoring(pending = listOf(2)).withEntry(score(2, 6, 3))
        assertEquals(score(2, 6, 3), edited.scoreFor(2))
        assertEquals(1, edited.pending.size)
    }

    @Test
    fun withEntry_leavesTheOriginalUnchanged() {
        val original = scoring()
        original.withEntry(score(1))
        assertTrue(original.pending.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun withEntry_savedHole_isRejected() {
        scoring(saved = listOf(1)).withEntry(score(1))
    }

    @Test
    fun scoreFor_prefersPendingThenSaved() {
        val round = RoundScoring(9, mapOf(1 to score(1, 5, 2)), mapOf(2 to score(2, 3, 1)))
        assertEquals(score(1, 5, 2), round.scoreFor(1))
        assertEquals(score(2, 3, 1), round.scoreFor(2))
        assertNull(round.scoreFor(3))
    }

    @Test
    fun scoredCountAndComplete_countSavedAndPendingTogether() {
        val round = scoring(totalHoles = 9, saved = listOf(1, 2, 3, 4), pending = listOf(5, 6, 7, 8))
        assertEquals(8, round.scoredCount)
        assertFalse(round.isComplete)
        assertTrue(round.withEntry(score(9)).isComplete)
    }

    @Test
    fun isLastToScore_onlyForTheOneRemainingHole() {
        val round = scoring(totalHoles = 9, saved = listOf(1, 2, 3, 4), pending = listOf(5, 6, 7, 9))
        assertTrue(round.isLastToScore(8))
        assertFalse(round.isLastToScore(1))
        assertFalse(scoring(totalHoles = 9).isLastToScore(1))
    }

    @Test
    fun isLastToScore_editingTheFinalPendingHoleStillFinishes() {
        val round = scoring(totalHoles = 9, saved = (1..8).toList(), pending = listOf(9))
        assertTrue(round.isLastToScore(9))
    }

    @Test
    fun holeAfter_skipsScoredHolesAndWrapsRound() {
        // Hole 4 was deleted on the round summary and reopened, so it's a gap behind the golfer.
        val round = scoring(totalHoles = 9, saved = listOf(1, 2, 3, 5, 6), pending = listOf(7, 8, 9))
        assertEquals(4, round.holeAfter(9))
        assertEquals(4, round.holeAfter(1))
        assertEquals(4, scoring(totalHoles = 9, saved = listOf(1, 2, 3)).holeAfter(3))
    }

    @Test
    fun holeAfter_everythingScored_returnsNull() {
        assertNull(scoring(totalHoles = 9, saved = (1..9).toList()).holeAfter(4))
    }

    @Test
    fun firstUnscored_findsTheFirstGap() {
        assertEquals(3, scoring(saved = listOf(1, 2, 5)).firstUnscored())
    }

    @Test
    fun withoutStalePending_dropsHolesNowSavedOrOutsideTheRound() {
        val round = scoring(totalHoles = 9, saved = listOf(1), pending = listOf(1, 2, 12))
            .withoutStalePending()
        assertEquals(setOf(2), round.pending.keys)
    }
}
