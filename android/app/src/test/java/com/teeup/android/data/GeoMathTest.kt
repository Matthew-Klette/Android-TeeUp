package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {
    // Fancourt Links hole 1 (OpenStreetMap): tee and pin, about 346 m apart.
    private val tee = GeoPoint(-33.9681293, 22.4046756)
    private val pin = GeoPoint(-33.9702036, 22.4074681)

    @Test
    fun distanceMeters_samePointIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(tee, tee), 1e-9)
    }

    @Test
    fun distanceMeters_oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195.0, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 1.0)
    }

    @Test
    fun distanceMeters_matchesKnownHoleLength() {
        assertEquals(346.0, GeoMath.distanceMeters(tee, pin), 2.0)
    }

    @Test
    fun bearingDegrees_cardinalDirections() {
        val origin = GeoPoint(0.0, 0.0)
        assertEquals(0.0, GeoMath.bearingDegrees(origin, GeoPoint(1.0, 0.0)), 1e-6)
        assertEquals(90.0, GeoMath.bearingDegrees(origin, GeoPoint(0.0, 1.0)), 1e-6)
        assertEquals(180.0, GeoMath.bearingDegrees(origin, GeoPoint(-1.0, 0.0)), 1e-6)
        assertEquals(270.0, GeoMath.bearingDegrees(origin, GeoPoint(0.0, -1.0)), 1e-6)
    }

    @Test
    fun destinationPoint_roundTripsWithDistanceAndBearing() {
        val bearing = GeoMath.bearingDegrees(tee, pin)
        val landed = GeoMath.destinationPoint(tee, bearing, 200.0)
        assertEquals(200.0, GeoMath.distanceMeters(tee, landed), 0.5)
        assertEquals(bearing, GeoMath.bearingDegrees(tee, landed), 0.1)
    }

    @Test
    fun distanceToSegmentMeters_pointBesideMiddleOfSegment() {
        val start = GeoPoint(0.0, 0.0)
        val end = GeoPoint(0.0, 0.01)
        val beside = GeoMath.destinationPoint(GeoPoint(0.0, 0.005), 0.0, 50.0)
        assertEquals(50.0, GeoMath.distanceToSegmentMeters(beside, start, end), 0.5)
    }

    @Test
    fun distanceToSegmentMeters_pointPastEndMeasuresToEnd() {
        val start = GeoPoint(0.0, 0.0)
        val end = GeoPoint(0.0, 0.01)
        val past = GeoMath.destinationPoint(end, 90.0, 80.0)
        assertEquals(80.0, GeoMath.distanceToSegmentMeters(past, start, end), 0.5)
    }

    @Test
    fun pathLengthMeters_sumsEachLeg() {
        val corner = GeoMath.destinationPoint(tee, 90.0, 150.0)
        val end = GeoMath.destinationPoint(corner, 180.0, 100.0)
        assertEquals(250.0, GeoMath.pathLengthMeters(listOf(tee, corner, end)), 0.5)
    }

    @Test
    fun pointAlongPath_walksAroundTheCorner() {
        val corner = GeoMath.destinationPoint(tee, 90.0, 150.0)
        val end = GeoMath.destinationPoint(corner, 180.0, 100.0)
        val point = GeoMath.pointAlongPath(listOf(tee, corner, end), 200.0)
        assertEquals(50.0, GeoMath.distanceMeters(corner, point), 0.5)
        assertEquals(50.0, GeoMath.distanceMeters(point, end), 0.5)
    }

    @Test
    fun pointAlongPath_beyondTheEndStopsAtTheEnd() {
        val point = GeoMath.pointAlongPath(listOf(tee, pin), 10_000.0)
        assertEquals(0.0, GeoMath.distanceMeters(point, pin), 1e-6)
    }
}
