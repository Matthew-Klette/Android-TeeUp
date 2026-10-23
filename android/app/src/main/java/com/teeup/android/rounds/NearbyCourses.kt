package com.teeup.android.rounds

import com.teeup.android.data.Course
import com.teeup.android.data.GeoMath
import com.teeup.android.data.GeoPoint

/** A course suggested under "Nearby" in Start a Round, with how far away it is. */
data class NearbyCourse(val course: Course, val distanceMeters: Double)

object NearbyCourses {
    /** Start a Round shows at most this many above the search results. */
    const val MAX_SUGGESTIONS = 2

    /** Further than a drive across the metro isn't "nearby", even if it's the closest course. */
    const val MAX_DISTANCE_METERS = 50_000.0

    /** The closest courses to [location], nearest first; none without a location. */
    fun pick(courses: List<Course>, location: GeoPoint?): List<NearbyCourse> {
        if (location == null) return emptyList()
        return courses
            .map { NearbyCourse(it, GeoMath.distanceMeters(location, GeoPoint(it.latitude, it.longitude))) }
            .filter { it.distanceMeters <= MAX_DISTANCE_METERS }
            .sortedBy { it.distanceMeters }
            .take(MAX_SUGGESTIONS)
    }
}
