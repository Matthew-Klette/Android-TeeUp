package com.teeup.android.data

import com.teeup.android.data.ScoreCheck.Invalid
import com.teeup.android.data.ScoreCheck.TooManyStrokes
import com.teeup.android.data.ScoreCheck.PuttsExceedStrokes
import com.teeup.android.data.ScoreCheck.Valid
import org.junit.Assert.assertEquals
import org.junit.Test

class HoleParTest {
    @Test fun maxStrokesIsParPlusThree() {
        assertEquals(6, HolePar.maxStrokes(3))
        assertEquals(7, HolePar.maxStrokes(4))
        assertEquals(8, HolePar.maxStrokes(5))
    }

    @Test fun onInTwoOnAParFourCanPuttUpToTheMax() {
        // On the green in 2: may putt 5 times (2 + 5 = 7 = par 4 + 3). No separate putt limit.
        assertEquals(Valid(HoleScoreInput(1, 7, 5)), RoundScoring.check(1, 7, 5, par = 4))
        assertEquals(Valid(HoleScoreInput(1, 7, 7)), RoundScoring.check(1, 7, 7, par = 4)) // putted from off the green
    }

    @Test fun moreThanParPlusThreeIsRejectedWithTheLimit() {
        assertEquals(TooManyStrokes(par = 4, max = 7), RoundScoring.check(1, 8, 2, par = 4))
        assertEquals(TooManyStrokes(par = 3, max = 6), RoundScoring.check(1, 7, 3, par = 3))
        assertEquals(Valid(HoleScoreInput(1, 8, 3)), RoundScoring.check(1, 8, 3, par = 5))
    }

    @Test fun puttsAreStrokesSoCannotExceedThem() {
        assertEquals(PuttsExceedStrokes(strokes = 3), RoundScoring.check(1, 3, 4, par = 4))
    }

    @Test fun unknownParMustBePickedFirst() {
        assertEquals(ScoreCheck.ParMissing, RoundScoring.check(1, 5, 2, par = null))
    }

    @Test fun missingOrNonsenseNumbersAreInvalid() {
        assertEquals(Invalid, RoundScoring.check(1, null, 2, par = 4))
        assertEquals(Invalid, RoundScoring.check(1, 4, null, par = 4))
        assertEquals(Invalid, RoundScoring.check(1, 0, 0, par = 4))
        assertEquals(Invalid, RoundScoring.check(1, 4, -1, par = 4))
    }
}
