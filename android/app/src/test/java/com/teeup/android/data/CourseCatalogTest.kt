package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CourseCatalogTest {
    private val byName = MockCatalog.courses.associateBy { it.name }

    @Test
    fun everyCourseIsInSouthAfrica() {
        // Mainland South Africa's bounding box.
        MockCatalog.courses.forEach {
            assertTrue("${it.name} isn't in South Africa", it.latitude in -35.0..-22.0 && it.longitude in 16.4..33.0)
        }
    }

    @Test
    fun theMadeUpCourseIsGone() {
        assertFalse(byName.containsKey("Emeris Municipal Golf Course"))
    }

    @Test
    fun closedCoursesAreGone() {
        // Walmer Country Club shut in 2019 and Shark River is listed as permanently closed; on
        // current imagery one is grown over and the other is bush.
        assertFalse(byName.containsKey("Walmer Country Club"))
        assertFalse(byName.containsKey("Shark River Golf Club"))
    }

    @Test
    fun everyPortElizabethCourseIsListed() {
        val portElizabeth = listOf(
            "Humewood Golf Club", "Port Elizabeth Golf Club", "Little Walmer Golf Club",
            "Wedgewood Golf Estate", "Sardinia Bay Golf Estate"
        )
        portElizabeth.forEach { assertNotNull("$it is missing", byName[it]) }
    }

    @Test
    fun kraggaKammaIsLeftOut() {
        assertFalse(byName.containsKey("Kragga Kamma Golf Club"))
    }

    @Test
    fun namesAndIdsAreUnique() {
        assertEquals(MockCatalog.courses.size, MockCatalog.courses.map { it.name }.toSet().size)
        assertEquals(MockCatalog.courses.size, MockCatalog.courses.map { it.id }.toSet().size)
    }

    @Test
    fun everyTeeTimeIsOnACatalogCourse() {
        MockCatalog.teeTimes.forEach { assertNotNull(it.id, MockCatalog.courseById(it.courseId)) }
    }

    @Test
    fun everyBundledHoleMapLiesOnItsCourse() {
        // Unit tests run from the module directory, so the bundled assets are on disk here.
        val assets = File("src/main/assets")
        HoleLayoutSource.bundledCourseNames.forEach { name ->
            val course = requireNotNull(byName[name]) { "$name has a hole map but isn't in the catalog" }
            val centre = GeoPoint(course.latitude, course.longitude)
            val json = File(assets, requireNotNull(HoleLayoutSource.bundledAsset(name))).readText()
            val holes = TeeUpApiClient.parseHoleLayouts(json)
            assertTrue("$name has no holes", holes.isNotEmpty())
            assertEquals("$name's holes aren't numbered 1..n", (1..holes.size).toList(), holes.map { it.hole }.sorted())
            holes.flatMap { listOfNotNull(it.tee, it.greenMiddle) }.forEach { point ->
                val metres = GeoMath.distanceMeters(centre, point)
                assertTrue("$name: a tee or green is ${metres.toInt()} m from the course centre", metres < MAX_METRES_FROM_CENTRE)
            }
        }
    }

    private companion object {
        /** Big enough for a long, spread-out course; far smaller than the gap between any two clubs' maps. */
        const val MAX_METRES_FROM_CENTRE = 2_500.0
    }
}
