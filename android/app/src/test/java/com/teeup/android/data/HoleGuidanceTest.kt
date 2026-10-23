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
    fun compute_placedPlayer_measuresFromWhereTheyWereDropped_evenWithGps() {
        val gps = GeoMath.destinationPoint(tee, 0.0, 100.0)
        val placed = GeoMath.destinationPoint(tee, 0.0, 300.0)
        val guidance = HoleGuidance.compute(par4, gps, userOnThisHole = true, target = null, placedPlayer = placed)
        assertEquals(HoleGuidance.Source.PLACED, guidance.source)
        assertFalse(guidance.fromGps)
        assertEquals(placed, guidance.origin)
        assertEquals(100, guidance.toPin)
        assertEquals(88, guidance.toFront)
    }

    @Test
    fun compute_sourceSaysWhereDistancesAreFrom() {
        val golfer = GeoMath.destinationPoint(tee, 0.0, 250.0)
        assertEquals(HoleGuidance.Source.TEE, HoleGuidance.compute(par4, null, false, null).source)
        assertEquals(HoleGuidance.Source.GPS, HoleGuidance.compute(par4, golfer, true, null).source)
    }

    @Test
    fun compute_movedPin_measuresToThePinNotTheMiddle() {
        // Today's flag is at the back of the green.
        val flag = GeoMath.destinationPoint(pin, 0.0, 8.0)
        val target = HoleGuidance.defaultTarget(par4)
        val guidance = HoleGuidance.compute(par4, null, false, target, pin = flag)
        assertTrue(guidance.pinMoved)
        assertEquals(408, guidance.toPin)
        assertEquals(400, guidance.toMiddle)
        assertEquals(178, guidance.targetToPin)
    }

    @Test
    fun compute_unmovedPin_isTheMiddleOfTheGreen() {
        val guidance = HoleGuidance.compute(par4, null, false, null)
        assertFalse(guidance.pinMoved)
        assertEquals(guidance.toMiddle, guidance.toPin)
    }

    @Test
    fun clampPin_onTheGreen_staysPut() {
        val flag = GeoMath.destinationPoint(pin, 90.0, 6.0)
        assertEquals(flag, HoleGuidance.clampPin(par4, flag))
    }

    @Test
    fun clampPin_offTheGreen_comesBackToItsEdge() {
        // The green runs 12 m short and 10 m long of the middle, so a flag 50 m right of it
        // lands 12 m right of the middle.
        val wide = GeoMath.destinationPoint(pin, 90.0, 50.0)
        val clamped = HoleGuidance.clampPin(par4, wide)
        assertEquals(12.0, GeoMath.distanceMeters(pin, clamped), 0.5)
        assertEquals(90.0, GeoMath.bearingDegrees(pin, clamped), 1.0)
    }

    @Test
    fun clampPin_greenWithoutOutline_allowsATypicalGreen() {
        val hole = par4.copy(greenFront = null, greenBack = null)
        val clamped = HoleGuidance.clampPin(hole, GeoMath.destinationPoint(pin, 0.0, 60.0))
        assertEquals(HoleGuidance.DEFAULT_GREEN_RADIUS_METERS, GeoMath.distanceMeters(pin, clamped), 0.5)
    }

    @Test
    fun holeLength_followsTheDogleg() {
        val corner = GeoMath.destinationPoint(tee, 0.0, 250.0)
        val bentPin = GeoMath.destinationPoint(corner, 90.0, 150.0)
        val dogleg = HoleLayout(4, tee, null, bentPin, null, listOf(corner))
        assertEquals(400, HoleGuidance.compute(dogleg, null, false, null).holeLengthMeters)
    }
}
