package com.teeup.android.home

import com.teeup.android.data.Course
import com.teeup.android.data.GroupMember
import com.teeup.android.data.TeeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFilteringTest {
    private val today = "2026-10-23"
    private val tomorrow = "2026-10-24"
    private val courses = mapOf(
        "fancourt" to Course("fancourt", "Fancourt Links", 0.0, 0.0, null),
        "humewood" to Course("humewood", "Humewood Golf Club", 0.0, 0.0, null)
    )

    private fun host(handicap: Double?, pace: Int) = GroupMember("h", "Host", handicap, pace, isHost = true)

    private fun teeTime(
        id: String,
        courseId: String = "fancourt",
        date: String = today,
        spots: Int = 2,
        holes: Int? = 18,
        members: List<GroupMember> = listOf(host(10.0, 1))
    ) = TeeTime(
        id = id, hostUserId = "h", courseId = courseId, dateTime = "${date}T07:40:00Z",
        openSpots = 3, spotsRemaining = spots, price = 0.0, type = 1, holes = holes,
        wantedHandicapMin = null, wantedHandicapMax = null, wantedPace = null, status = 0, members = members
    )

    private fun ids(filters: HomeFilters, vararg teeTimes: TeeTime) =
        HomeFiltering.apply(teeTimes.toList(), courses, filters, today, tomorrow).map { it.id }

    @Test
    fun noFilters_keepsEverythingInOrder() {
        assertEquals(listOf("a", "b"), ids(HomeFilters(), teeTime("a"), teeTime("b", courseId = "humewood")))
    }

    @Test
    fun search_matchesCourseNameIgnoringCase() {
        val result = ids(HomeFilters(search = "fanc"), teeTime("a"), teeTime("b", courseId = "humewood"))
        assertEquals(listOf("a"), result)
    }

    @Test
    fun search_blank_isIgnored() {
        assertEquals(2, ids(HomeFilters(search = "   "), teeTime("a"), teeTime("b")).size)
    }

    @Test
    fun dateFilter_todayAndTomorrow() {
        val all = arrayOf(teeTime("today"), teeTime("tomorrow", date = tomorrow), teeTime("later", date = "2026-10-30"))
        assertEquals(listOf("today"), ids(HomeFilters(date = DateFilter.TODAY), *all))
        assertEquals(listOf("tomorrow"), ids(HomeFilters(date = DateFilter.TOMORROW), *all))
    }

    @Test
    fun minOpenSpots_keepsGroupsWithAtLeastThatMany() {
        val result = ids(HomeFilters(minOpenSpots = 2), teeTime("one", spots = 1), teeTime("two", spots = 2))
        assertEquals(listOf("two"), result)
    }

    @Test
    fun courseAndHoles_filterExactly() {
        val all = arrayOf(teeTime("f18"), teeTime("f9", holes = 9), teeTime("h18", courseId = "humewood"))
        assertEquals(listOf("f9"), ids(HomeFilters(courseId = "fancourt", holes = 9), *all))
    }

    @Test
    fun skillFilter_checksTheHost() {
        val all = arrayOf(
            teeTime("low", members = listOf(host(5.0, 1))),
            teeTime("high", members = listOf(host(20.0, 1))),
            teeTime("noHandicap", members = listOf(host(null, 1))),
            teeTime("fast", members = listOf(host(5.0, 2)))
        )
        assertEquals(listOf("low", "fast"), ids(HomeFilters(maxHandicap = 10.0), *all))
        assertEquals(listOf("fast"), ids(HomeFilters(maxHandicap = 10.0, pace = 2), *all))
    }

    @Test
    fun skillFilter_groupWithoutHost_isExcluded() {
        val guestOnly = GroupMember("g", "Guest", 3.0, 1, isHost = false)
        assertTrue(ids(HomeFilters(pace = 1), teeTime("a", members = listOf(guestOnly))).isEmpty())
    }

    @Test
    fun hasSkillFilter_andActiveCount() {
        assertFalse(HomeFilters().hasSkillFilter)
        assertTrue(HomeFilters(pace = 0).hasSkillFilter)
        assertEquals(0, HomeFilters(search = "x", mineOnly = true).activeRefinements)
        assertEquals(4, HomeFilters(date = DateFilter.TODAY, minOpenSpots = 1, holes = 9, maxHandicap = 5.0).activeRefinements)
    }
}
