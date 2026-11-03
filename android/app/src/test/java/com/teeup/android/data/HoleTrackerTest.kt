package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleTrackerTest {
    // Two parallel 300 m holes, 60 m apart: hole 1 plays north, and hole 2's tee sits 60 m east of
    // hole 1's green and plays back south. The classic out-and-back pair that fools nearest-hole logic.
    private val tee1 = GeoPoint(-33.9700, 22.4100)
    private val pin1 = GeoMath.destinationPoint(tee1, 0.0, 300.0)
    private val tee2 = GeoMath.destinationPoint(pin1, 90.0, 60.0)
    private val pin2 = GeoMath.destinationPoint(tee2, 180.0, 300.0)
    // Hole 3 is far away (another part of the course), for skip-ahead tests.
    private val tee3 = GeoMath.destinationPoint(tee1, 270.0, 600.0)
    private val pin3 = GeoMath.destinationPoint(tee3, 0.0, 350.0)
    private val holes = listOf(hole(1, tee1, pin1), hole(2, tee2, pin2), hole(3, tee3, pin3))

    private fun hole(number: Int, tee: GeoPoint, pin: GeoPoint) =
        HoleLayout(number, tee, greenFront = null, greenMiddle = pin, greenBack = null, doglegs = emptyList())

    private fun north(from: GeoPoint, m: Double) = GeoMath.destinationPoint(from, 0.0, m)
    private fun east(from: GeoPoint, m: Double) = GeoMath.destinationPoint(from, 90.0, m)
    private fun south(from: GeoPoint, m: Double) = GeoMath.destinationPoint(from, 180.0, m)

    // ---- HoleTracker.detect ----

    @Test fun detect_noHoles_returnsNull() {
        assertNull(HoleTracker.detect(tee1, emptyList(), currentHole = null))
    }

    @Test fun detect_noCurrentHole_picksNearest() {
        val fix = HoleTracker.detect(north(tee1, 150.0), holes, currentHole = null)!!
        assertEquals(1, fix.hole)
        assertTrue(fix.onCourse)
    }

    @Test fun detect_unknownCurrentHole_picksNearest() {
        assertEquals(2, HoleTracker.detect(south(tee2, 50.0), holes, currentHole = 7)!!.hole)
    }

    @Test fun detect_strayShotIntoParallelFairway_staysOnCurrentHole() {
        // On hole 1, ball sliced 55 m right into hole 2's fairway: hole 2's line is much nearer,
        // but hole 1 isn't finished, so the map (and the scorecard) must stay on hole 1.
        val inHole2Fairway = east(north(tee1, 150.0), 58.0)
        assertEquals(1, HoleTracker.detect(inHole2Fairway, holes, currentHole = 1)!!.hole)
    }

    @Test fun detect_strayIntoParallelFairwayAfterScoring_movesOn() {
        // Same spot once hole 1 has a score: walking down hole 2 is the obvious reading.
        val inHole2Fairway = east(north(tee1, 150.0), 58.0)
        assertEquals(2, HoleTracker.detect(inHole2Fairway, holes, currentHole = 1, currentHoleDone = true)!!.hole)
    }

    @Test fun detect_standingOnNextTee_advancesEvenWithoutScore() {
        val onTee2 = east(tee2, 5.0)
        assertEquals(2, HoleTracker.detect(onTee2, holes, currentHole = 1)!!.hole)
    }

    @Test fun detect_chipBetweenGreenAndNextTee_staysOnCurrentHole() {
        // Missed the green long, towards hole 2's tee: 30 m from the green, 30 m from the tee.
        val overTheBack = east(pin1, 30.0)
        assertEquals(1, HoleTracker.detect(overTheBack, holes, currentHole = 1)!!.hole)
    }

    @Test fun detect_walkingTowardsNextTeeAfterScoring_advances() {
        val betweenGreenAndTee = east(pin1, 35.0)
        assertEquals(2, HoleTracker.detect(betweenGreenAndTee, holes, currentHole = 1, currentHoleDone = true)!!.hole)
    }

    @Test fun detect_neverJumpsBackwards() {
        // On hole 2's tee box area but standing on hole 1's line: hole 2 has started, stay there.
        val nearHole1Line = north(tee1, 280.0)
        assertEquals(2, HoleTracker.detect(nearHole1Line, holes, currentHole = 2)!!.hole)
    }

    @Test fun detect_farFromCurrentHoleAndOnAnother_jumpsToIt() {
        // Started on another hole (or skipped some): far from hole 1, right on hole 3.
        val onHole3 = north(tee3, 100.0)
        assertEquals(3, HoleTracker.detect(onHole3, holes, currentHole = 1)!!.hole)
    }

    @Test fun detect_farFromEveryHole_isOffCourseAndKeepsCurrentHole() {
        val capeTown = GeoPoint(-33.9249, 18.4241)
        val fix = HoleTracker.detect(capeTown, holes, currentHole = 2)!!
        assertFalse(fix.onCourse)
        assertEquals(2, fix.hole)
        assertTrue(fix.distanceToHoleMeters > 300_000)
    }

    @Test fun detect_farFromEveryHoleWithNoCurrentHole_fallsBackToNearest() {
        val fix = HoleTracker.detect(GeoPoint(-33.9249, 18.4241), holes, currentHole = null)!!
        assertFalse(fix.onCourse)
        assertEquals(3, fix.hole) // hole 3 is the westernmost, so nearest to Cape Town
    }

    @Test fun detect_lastHole_hasNoNextAndStays() {
        val onHole3 = north(tee3, 340.0)
        assertEquals(3, HoleTracker.detect(onHole3, holes, currentHole = 3, currentHoleDone = true)!!.hole)
    }

    // ---- HoleFollower ----

    @Test fun follower_needsTwoAgreeingFixesToSwitch() {
        val follower = HoleFollower(currentHole = 1)
        val onTee2 = east(tee2, 3.0)
        assertEquals(1, follower.update(onTee2, accuracyMeters = 5f, holes = holes)!!.hole)
        assertEquals(2, follower.update(onTee2, accuracyMeters = 5f, holes = holes)!!.hole)
        assertEquals(2, follower.currentHole)
    }

    @Test fun follower_singleSpikeDoesNotSwitch() {
        val follower = HoleFollower(currentHole = 1)
        val fairway1 = north(tee1, 120.0)
        follower.update(north(tee3, 100.0), accuracyMeters = 8f, holes = holes) // one wild fix on hole 3
        assertEquals(1, follower.update(fairway1, accuracyMeters = 5f, holes = holes)!!.hole)
        assertEquals(1, follower.update(north(tee3, 100.0), accuracyMeters = 8f, holes = holes)!!.hole)
    }

    @Test fun follower_inaccurateFixNeverSwitches() {
        val follower = HoleFollower(currentHole = 1)
        val onTee2 = east(tee2, 3.0)
        repeat(5) { follower.update(onTee2, accuracyMeters = HoleFollower.MAX_SWITCH_ACCURACY_METERS + 10f, holes = holes) }
        assertEquals(1, follower.currentHole)
    }

    @Test fun follower_unknownAccuracyNeverSwitches() {
        val follower = HoleFollower(currentHole = 1)
        repeat(5) { follower.update(east(tee2, 3.0), accuracyMeters = null, holes = holes) }
        assertEquals(1, follower.currentHole)
    }

    @Test fun follower_reportsDistanceToTheHoleItStaysOn() {
        val follower = HoleFollower(currentHole = 1)
        val fix = follower.update(east(tee2, 3.0), accuracyMeters = 5f, holes = holes)!!
        assertEquals(1, fix.hole)
        assertEquals(GeoMath.distanceToPathMeters(east(tee2, 3.0), holes[0].lineOfPlay), fix.distanceToHoleMeters, 0.01)
    }

    @Test fun follower_anchorResetsPendingSwitch() {
        val follower = HoleFollower(currentHole = 1)
        val onTee2 = east(tee2, 3.0)
        follower.update(onTee2, 5f, holes)
        follower.anchor(1)
        assertEquals(1, follower.update(onTee2, 5f, holes)!!.hole) // count restarted
    }

    @Test fun follower_usesHoleDoneForCurrentHole() {
        val follower = HoleFollower(currentHole = 1)
        val inHole2Fairway = east(north(tee1, 150.0), 58.0)
        repeat(3) { follower.update(inHole2Fairway, 5f, holes, isHoleDone = { false }) }
        assertEquals(1, follower.currentHole)
        repeat(2) { follower.update(inHole2Fairway, 5f, holes, isHoleDone = { it == 1 }) }
        assertEquals(2, follower.currentHole)
    }

    @Test fun follower_noCurrentHole_takesFirstFixImmediately() {
        val follower = HoleFollower(currentHole = null)
        assertEquals(2, follower.update(south(tee2, 50.0), 5f, holes)!!.hole)
        assertEquals(2, follower.currentHole)
    }
}
