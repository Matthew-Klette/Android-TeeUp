package com.teeup.android.rounds

import com.teeup.android.data.Course
import com.teeup.android.data.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCoursesTest {
    // Course centres from OpenStreetMap; the golfer stands on Walmer's Main Road, Gqeberha.
    private val humewood = Course("h", "Humewood Golf Club", -33.9996, 25.6786, null)
    private val pegc = Course("p", "Port Elizabeth Golf Club", -33.9588, 25.5811, null)
    private val littleWalmer = Course("w", "Little Walmer Golf Club", -33.9739, 25.5734, null)
    private val wanderers = Course("j", "Wanderers Golf Club", -26.1295, 28.0575, null)
    private val walmer = GeoPoint(-33.9790, 25.5830)

    @Test
    fun closestTwoComeFirst_nearestOnTop() {
        val nearby = NearbyCourses.pick(listOf(humewood, wanderers, littleWalmer, pegc), walmer)
        // Little Walmer ~1.1 km, PE Golf Club ~2.3 km, Humewood ~9 km.
        assertEquals(listOf("w", "p"), nearby.map { it.course.id })
    }

    @Test
    fun carriesEachCoursesDistance() {
        val nearby = NearbyCourses.pick(listOf(pegc), walmer)
        assertEquals(2_250.0, nearby.single().distanceMeters, 100.0)
    }

    @Test
    fun neverMoreThanTwo() {
        assertEquals(2, NearbyCourses.pick(listOf(humewood, pegc, littleWalmer), walmer).size)
    }

    @Test
    fun coursesBeyondTheRadiusAreNotNearby() {
        // Johannesburg is ~900 km from Gqeberha: closest in the list, but not "nearby".
        assertTrue(NearbyCourses.pick(listOf(wanderers), walmer).isEmpty())
    }

    @Test
    fun noLocation_noSuggestions() {
        assertTrue(NearbyCourses.pick(listOf(pegc, humewood), null).isEmpty())
    }
}
