package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseParsTest {
    @Test fun everyCatalogueCourseHasACard() {
        MockCatalog.courses.forEach { course ->
            assertNotNull("No scorecard for ${course.name}", CoursePars.cardFor(course.name))
        }
    }

    @Test fun everyBundledHoleMapCourseHasACard() {
        HoleLayoutSource.bundledCourseNames.forEach { name ->
            assertNotNull("No scorecard for mapped course $name", CoursePars.cardFor(name))
        }
    }

    @Test fun cardsAreNineOrEighteenHolesOfParThreeToFive() {
        CoursePars.courseNames.forEach { name ->
            val card = CoursePars.cardFor(name)!!
            assertTrue("$name has ${card.size} holes", card.size == 9 || card.size == 18)
            assertTrue("$name has a par outside 3-5", card.all { it in 3..5 })
        }
    }

    @Test fun knownCardsMatchTheirPublishedTotals() {
        assertEquals(72, CoursePars.cardFor("Humewood Golf Club")!!.sum())
        assertEquals(72, CoursePars.cardFor("Port Elizabeth Golf Club")!!.sum())
        assertEquals(73, CoursePars.cardFor("Fancourt Links")!!.sum())
        assertEquals(71, CoursePars.cardFor("Wanderers Golf Club")!!.sum())
        assertEquals(73, CoursePars.cardFor("East London Golf Club")!!.sum())
        assertEquals(32, CoursePars.cardFor("Sardinia Bay Golf Estate")!!.sum())
    }

    @Test fun parForReadsTheHole() {
        // Port Elizabeth Golf Club opens 5, 3, 4.
        assertEquals(5, CoursePars.parFor("Port Elizabeth Golf Club", 1))
        assertEquals(3, CoursePars.parFor("Port Elizabeth Golf Club", 2))
        assertEquals(5, CoursePars.parFor("Port Elizabeth Golf Club", 18))
    }

    @Test fun nineHoleCourseRepeatsForTheBackNine() {
        assertEquals(CoursePars.parFor("Sardinia Bay Golf Estate", 1), CoursePars.parFor("Sardinia Bay Golf Estate", 10))
        assertEquals(CoursePars.parFor("Sardinia Bay Golf Estate", 9), CoursePars.parFor("Sardinia Bay Golf Estate", 18))
    }

    @Test fun formerNamesStillFindTheCard() {
        assertEquals(CoursePars.parFor("Little Walmer Golf Club", 7), CoursePars.parFor("Little Walmer Golf Course", 7))
    }

    @Test fun unknownCourseOrHoleHasNoPar() {
        assertNull(CoursePars.parFor("Emeris Municipal Golf Course", 1))
        assertNull(CoursePars.parFor(null, 1))
        assertNull(CoursePars.parFor("Humewood Golf Club", 0))
    }
}
