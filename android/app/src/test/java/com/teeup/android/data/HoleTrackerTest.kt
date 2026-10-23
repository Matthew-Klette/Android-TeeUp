package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleTrackerTest {
    // Two parallel 300 m holes running north, 60 m apart: hole 1's green sits next to hole 2's tee.
    private val tee1 = GeoPoint(-33.9700, 22.4100)
    private val pin1 = GeoMath.destinationPoint(tee1, 0.0, 300.0)
    private val tee2 = GeoMath.destinationPoint(pin1, 90.0, 60.0)
    private val pin2 = GeoMath.destinationPoint(tee2, 180.0, 300.0)
    private val holes = listOf(hole(1, tee1, pin1), hole(2, tee2, pin2))

    private fun hole(number: Int, tee: GeoPoint, pin: GeoPoint) =
        HoleLayout(number, tee, greenFront = null, greenMiddle = pin, greenBack = null, doglegs = emptyList())

    @Test
    fun detect_noHoles_returnsNull() {
        assertNull(HoleTracker.detect(tee1, emptyList(), currentHole = null))
    }

    @Test
    fun detect_onTheFairway_picksThatHole() {
        val fairway = GeoMath.destinationPoint(tee1, 0.0, 150.0)
        val fix = HoleTracker.detect(fairway, holes, currentHole = null)!!
        assertEquals(1, fix.hole)
        assertTrue(fix.onCourse)
    }

    @Test
    fun detect_justCloserToAnotherHole_keepsCurrentHole() {
        // 10 m nearer hole 2's line than hole 1's: inside the hysteresis band, so no flicker.
        val between = GeoMath.destinationPoint(GeoMath.destinationPoint(tee1, 0.0, 150.0), 90.0, 35.0)
        assertEquals(1, HoleTracker.detect(between, holes, currentHole = 1)!!.hole)
    }

    @Test
    fun detect_clearlyOnAnotherHole_switches() {
        val onHole2 = GeoMath.destinationPoint(tee2, 180.0, 50.0)
        assertEquals(2, HoleTracker.detect(onHole2, holes, currentHole = 1)!!.hole)
    }

    @Test
    fun detect_farFromEveryHole_isOffCourseAndKeepsCurrentHole() {
        val capeTown = GeoPoint(-33.9249, 18.4241)
        val fix = HoleTracker.detect(capeTown, holes, currentHole = 2)!!
        assertFalse(fix.onCourse)
        assertEquals(2, fix.hole)
        assertTrue(fix.distanceToHoleMeters > 300_000)
    }

    @Test
    fun detect_farFromEveryHoleWithNoCurrentHole_fallsBackToNearest() {
        val fix = HoleTracker.detect(GeoPoint(-33.9249, 18.4241), holes, currentHole = null)!!
        assertFalse(fix.onCourse)
        assertEquals(1, fix.hole)
    }

    @Test
    fun detect_unknownCurrentHole_picksNearest() {
        val onHole2 = GeoMath.destinationPoint(tee2, 180.0, 50.0)
        assertEquals(2, HoleTracker.detect(onHole2, holes, currentHole = 7)!!.hole)
    }
}
