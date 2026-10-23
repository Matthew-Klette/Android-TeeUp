package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleGuidanceTest {
    private val tee = GeoPoint(-33.9700, 22.4100)
    private val pin = GeoMath.destinationPoint(tee, 0.0, 400.0)
    private val front = GeoMath.destinationPoint(pin, 180.0, 12.0)
    private val back = GeoMath.destinationPoint(pin, 0.0, 10.0)
    private val par4 = HoleLayout(1, tee, front, pin, back, doglegs = emptyList())

    @Test
    fun defaultTarget_longHole_isADriveDownTheLineOfPlay() {
        val target = HoleGuidance.defaultTarget(par4)!!
        assertEquals(HoleGuidance.DEFAULT_TARGET_METERS, GeoMath.distanceMeters(tee, target), 1.0)
    }

    @Test
    fun defaultTarget_shortHoleOrNoRoomForADrive_aimsAtTheGreenInstead() {
        val par3 = HoleLayout(2, tee, null, GeoMath.destinationPoint(tee, 0.0, 160.0), null, emptyList())
        assertNull(HoleGuidance.defaultTarget(par3))
    }

    @Test
    fun defaultTarget_shortishPar4_staysShortOfTheGreen() {
        val hole = HoleLayout(3, tee, null, GeoMath.destinationPoint(tee, 0.0, 280.0), null, emptyList())
        val target = HoleGuidance.defaultTarget(hole)!!
        assertEquals(280.0 * HoleGuidance.MAX_TARGET_FRACTION, GeoMath.distanceMeters(tee, target), 1.0)
    }

    @Test
    fun compute_withoutGps_measuresFromTheTee() {
        val target = HoleGuidance.defaultTarget(par4)
        val guidance = HoleGuidance.compute(par4, userPosition = null, userOnThisHole = false, target = target)
        assertFalse(guidance.fromGps)
        assertEquals(tee, guidance.origin)
        assertEquals(410, guidance.toBack)
        assertEquals(400, guidance.toMiddle)
        assertEquals(388, guidance.toFront)
        assertEquals(230, guidance.originToTarget)
        assertEquals(170, guidance.targetToPin)
        assertEquals(400, guidance.holeLengthMeters)
    }

    @Test
    fun compute_onThisHole_measuresFromTheGolfer() {
        val golfer = GeoMath.destinationPoint(tee, 0.0, 250.0)
        val guidance = HoleGuidance.compute(par4, golfer, userOnThisHole = true, target = null)
        assertTrue(guidance.fromGps)
        assertEquals(150, guidance.toMiddle)
        assertNull(guidance.originToTarget)
        assertNull(guidance.targetToPin)
    }

    @Test
    fun compute_gpsButNotOnThisHole_stillMeasuresFromTheTee() {
        val elsewhere = GeoPoint(-33.9249, 18.4241)
        val guidance = HoleGuidance.compute(par4, elsewhere, userOnThisHole = false, target = null)
        assertFalse(guidance.fromGps)
        assertEquals(400, guidance.toMiddle)
    }

    @Test
    fun compute_greenWithoutOutline_hasNoFrontOrBack() {
        val hole = par4.copy(greenFront = null, greenBack = null)
        val guidance = HoleGuidance.compute(hole, null, userOnThisHole = false, target = null)
        assertNull(guidance.toFront)
        assertNull(guidance.toBack)
        assertNotNull(guidance.toMiddle)
    }

    @Test
    fun holeLength_followsTheDogleg() {
        val corner = GeoMath.destinationPoint(tee, 0.0, 250.0)
        val bentPin = GeoMath.destinationPoint(corner, 90.0, 150.0)
        val dogleg = HoleLayout(4, tee, null, bentPin, null, listOf(corner))
        assertEquals(400, HoleGuidance.compute(dogleg, null, false, null).holeLengthMeters)
    }
}
