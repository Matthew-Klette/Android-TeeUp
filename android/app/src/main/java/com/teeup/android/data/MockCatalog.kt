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
    /**
     * Real South African courses only, the same list as infra/courses/seed-courses.sql. Each pin
     * is the course's middle in OpenStreetMap (or the imagery/listing that seed file names), so
     * Course Preview opens on the actual course. No ratings: there's no verified source for them.
     */
    val courses: List<Course> = listOf(
        // Port Elizabeth (Gqeberha): every course in the city.
        Course(id = "mock-course-humewood", name = "Humewood Golf Club", latitude = -33.99959, longitude = 25.67858, rating = null),
        Course(id = "mock-course-pegc", name = "Port Elizabeth Golf Club", latitude = -33.95878, longitude = 25.58108, rating = null),
        Course(id = "mock-course-little-walmer", name = "Little Walmer Golf Club", latitude = -33.97387, longitude = 25.5734, rating = null),
        Course(id = "mock-course-wedgewood", name = "Wedgewood Golf Estate", latitude = -33.91149, longitude = 25.39287, rating = null),
        Course(id = "mock-course-sardinia-bay", name = "Sardinia Bay Golf Estate", latitude = -34.0171, longitude = 25.4953, rating = null),
        // The rest of South Africa.
        Course(id = "mock-course-fancourt", name = "Fancourt Links", latitude = -33.96776, longitude = 22.41567, rating = null),
        Course(id = "mock-course-wanderers", name = "Wanderers Golf Club", latitude = -26.12946, longitude = 28.05751, rating = null),
        Course(id = "mock-course-durban-country-club", name = "Durban Country Club", latitude = -29.82133, longitude = 31.03458, rating = null),
        Course(id = "mock-course-royal-johannesburg-kensington", name = "Royal Johannesburg and Kensington Golf Club", latitude = -26.14993, longitude = 28.11108, rating = null),
        Course(id = "mock-course-leopard-creek", name = "Leopard Creek Country Club", latitude = -25.44434, longitude = 31.5362, rating = null),
        Course(id = "mock-course-gary-player", name = "Gary Player Country Club", latitude = -25.33957, longitude = 27.10674, rating = null),
        Course(id = "mock-course-erinvale", name = "Erinvale Golf Club", latitude = -34.06396, longitude = 18.8828, rating = null),
        Course(id = "mock-course-simola", name = "Simola Golf Club", latitude = -34.00765, longitude = 23.03357, rating = null),
        Course(id = "mock-course-zwartkop", name = "Zwartkop Country Club", latitude = -25.83258, longitude = 28.16368, rating = null),
        Course(id = "mock-course-silver-lakes", name = "Silver Lakes Golf Estate", latitude = -25.77234, longitude = 28.37121, rating = null),
        Course(id = "mock-course-arabella", name = "Arabella Golf Club", latitude = -34.31589, longitude = 19.13099, rating = null),
        Course(id = "mock-course-steenberg", name = "Steenberg Golf Club", latitude = -34.06796, longitude = 18.42695, rating = null),
        Course(id = "mock-course-selborne", name = "Selborne Golf Estate", latitude = -30.37229, longitude = 30.6777, rating = null),
        Course(id = "mock-course-east-london", name = "East London Golf Club", latitude = -33.00012, longitude = 27.93485, rating = null),
        Course(id = "mock-course-st-francis-links", name = "St Francis Links", latitude = -34.1608, longitude = 24.8164, rating = null),
        Course(id = "mock-course-pinnacle-point", name = "Pinnacle Point Golf Estate", latitude = -34.2017, longitude = 22.0933, rating = null),
        Course(id = "mock-course-pearl-valley", name = "Pearl Valley Golf Estate", latitude = -33.8222, longitude = 18.9793, rating = null)
    )

    val teeTimes: List<TeeTime> = listOf(
        TeeTime(
            id = "mock-teetime-fancourt-am",
            hostUserId = null,
            courseId = "mock-course-fancourt",
            dateTime = isoAt(daysFromToday = 0, hour = 7),
            openSpots = 3,
            spotsRemaining = 3,
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
            spotsRemaining = 2,
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
            spotsRemaining = 4,
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
            spotsRemaining = 2,
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
            spotsRemaining = 3,
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
            spotsRemaining = 4,
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
