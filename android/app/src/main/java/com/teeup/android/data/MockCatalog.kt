package com.teeup.android.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Local fallback catalog used when the API is unreachable or returns no
 * results, so Home (including search) and the Preview/Book flow always have
 * something real to demo against instead of a bare empty state. IDs are
 * prefixed "mock-" so TeeTimeDetailActivity can recognize and resolve them
 * without a network round trip.
 */
object MockCatalog {
    val courses: List<Course> = listOf(
        Course(id = "mock-course-fancourt", name = "Fancourt Links", latitude = -33.9608, longitude = 22.4478, rating = 4.8),
        Course(id = "mock-course-humewood", name = "Humewood Golf Club", latitude = -33.9711, longitude = 25.656, rating = 4.5),
        Course(id = "mock-course-wanderers", name = "Wanderers Golf Club", latitude = -26.1448, longitude = 28.0473, rating = 4.6),
        // Port Elizabeth (Gqeberha) area courses — were missing entirely, so searches like
        // "PEGC" or "Walmer" returned nothing even though there were real courses to match.
        Course(id = "mock-course-pegc", name = "Port Elizabeth Golf Club", latitude = -33.9364, longitude = 25.5850, rating = 4.4),
        Course(id = "mock-course-little-walmer", name = "Little Walmer Golf Course", latitude = -33.9924, longitude = 25.6104, rating = 4.1),
        Course(id = "mock-course-wedgewood", name = "Wedgewood Golf & Country Estate", latitude = -33.8683, longitude = 25.5217, rating = 4.3)
    )

    val teeTimes: List<TeeTime> = listOf(
        TeeTime(
            id = "mock-teetime-fancourt-am",
            hostUserId = null,
            courseId = "mock-course-fancourt",
            dateTime = isoAt(daysFromToday = 0, hour = 7),
            openSpots = 3,
            price = 450.0,
            type = 0,
            holes = null,
            wantedHandicapMin = null,
            wantedHandicapMax = null,
            wantedPace = null,
            status = 0,
            members = emptyList()
        ),
        TeeTime(
            id = "mock-teetime-humewood-open",
            hostUserId = null,
            courseId = "mock-course-humewood",
            dateTime = isoAt(daysFromToday = 0, hour = 13),
            openSpots = 2,
            price = 0.0,
            type = 1,
            holes = null,
            wantedHandicapMin = null,
            wantedHandicapMax = null,
            wantedPace = null,
            status = 0,
            members = emptyList()
        ),
        TeeTime(
            id = "mock-teetime-wanderers-am",
            hostUserId = null,
            courseId = "mock-course-wanderers",
            dateTime = isoAt(daysFromToday = 1, hour = 9),
            openSpots = 4,
            price = 380.0,
            type = 0,
            holes = null,
            wantedHandicapMin = null,
            wantedHandicapMax = null,
            wantedPace = null,
            status = 0,
            members = emptyList()
        ),
        TeeTime(
            id = "mock-teetime-pegc-am",
            hostUserId = null,
            courseId = "mock-course-pegc",
            dateTime = isoAt(daysFromToday = 0, hour = 8),
            openSpots = 2,
            price = 320.0,
            type = 0,
            holes = null,
            wantedHandicapMin = null,
            wantedHandicapMax = null,
            wantedPace = null,
            status = 0,
            members = emptyList()
        ),
        TeeTime(
            id = "mock-teetime-little-walmer-open",
            hostUserId = null,
            courseId = "mock-course-little-walmer",
            dateTime = isoAt(daysFromToday = 1, hour = 10),
            openSpots = 3,
            price = 0.0,
            type = 1,
            holes = null,
            wantedHandicapMin = null,
            wantedHandicapMax = null,
            wantedPace = null,
            status = 0,
            members = emptyList()
        ),
        TeeTime(
            id = "mock-teetime-wedgewood-am",
            hostUserId = null,
            courseId = "mock-course-wedgewood",
            dateTime = isoAt(daysFromToday = 0, hour = 11),
            openSpots = 4,
            price = 410.0,
            type = 0,
            holes = null,
            wantedHandicapMin = null,
            wantedHandicapMax = null,
            wantedPace = null,
            status = 0,
            members = emptyList()
        )
    )

    private val coursesById = courses.associateBy { it.id }

    fun courseById(id: String): Course? = coursesById[id]

    fun teeTimeById(id: String): TeeTime? = teeTimes.firstOrNull { it.id == id }

    /** Same UTC-based "today/tomorrow" convention as HomeActivity's date filter. */
    private fun isoAt(daysFromToday: Int, hour: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, daysFromToday)
        }
        return "%04d-%02d-%02dT%02d:00:00Z".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            hour
        )
    }
}
