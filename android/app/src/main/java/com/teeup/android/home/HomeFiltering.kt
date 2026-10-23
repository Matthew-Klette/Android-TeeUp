package com.teeup.android.home

import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import java.util.Calendar
import java.util.TimeZone

enum class DateFilter { ALL, TODAY, TOMORROW }

/**
 * Every Home filter in one immutable value. Search, date, spots, course and holes filter the
 * loaded list on the device; [maxHandicap]/[pace] and [mineOnly] also change what's fetched
 * (see HomeViewModel), since the public list is filtered by host skill on the server.
 */
data class HomeFilters(
    val search: String = "",
    val date: DateFilter = DateFilter.ALL,
    val minOpenSpots: Int = 0,
    val courseId: String? = null,
    val holes: Int? = null,
    val maxHandicap: Double? = null,
    val pace: Int? = null,
    val mineOnly: Boolean = false
) {
    val hasSkillFilter: Boolean get() = maxHandicap != null || pace != null

    /** How many refinements beyond search and My groups are on, for the "Clear filters" affordance. */
    val activeRefinements: Int
        get() = listOf(date != DateFilter.ALL, minOpenSpots > 0, courseId != null, holes != null, hasSkillFilter)
            .count { it }
}

object HomeFiltering {
    /** [today]/[tomorrow] are "yyyy-MM-dd" in UTC, matching how tee times are shown (Formatting.kt). */
    fun apply(
        teeTimes: List<TeeTime>,
        coursesById: Map<String, Course>,
        filters: HomeFilters,
        today: String,
        tomorrow: String
    ): List<TeeTime> = teeTimes.filter { teeTime ->
        val courseName = coursesById[teeTime.courseId]?.name.orEmpty()
        val matchesSearch = filters.search.isBlank() || courseName.contains(filters.search.trim(), ignoreCase = true)
        val matchesDate = when (filters.date) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> teeTime.dateTime.take(10) == today
            DateFilter.TOMORROW -> teeTime.dateTime.take(10) == tomorrow
        }
        matchesSearch &&
            matchesDate &&
            teeTime.spotsRemaining >= filters.minOpenSpots &&
            (filters.courseId == null || teeTime.courseId == filters.courseId) &&
            (filters.holes == null || teeTime.holes == filters.holes) &&
            matchesHostSkill(teeTime, filters)
    }

    /** My groups isn't filtered by host skill on the server like the public list is, so this
     *  re-checks it against the host in the member list. A no-op where the server already did. */
    private fun matchesHostSkill(teeTime: TeeTime, filters: HomeFilters): Boolean {
        if (!filters.hasSkillFilter) return true
        val host = teeTime.members.firstOrNull { it.isHost } ?: return false
        val maxHandicap = filters.maxHandicap
        val matchesHandicap = maxHandicap == null || (host.handicapIndex != null && host.handicapIndex <= maxHandicap)
        val matchesPace = filters.pace == null || host.paceOfPlay == filters.pace
        return matchesHandicap && matchesPace
    }

    /** Today (0) or a day offset from it as "yyyy-MM-dd" in UTC. */
    fun isoDate(daysFromToday: Int): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_MONTH, daysFromToday)
        }
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}

/** The pieces of a tee time's date the card's date tile shows. Read in UTC like formatTeeTime. */
data class TeeTimeParts(val dayOfMonth: Int, val monthIndex: Int, val weekdayIndex: Int, val time: String) {
    companion object {
        private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}).*$")

        fun parse(isoDateTime: String): TeeTimeParts? {
            val match = ISO.matchEntire(isoDateTime) ?: return null
            val (year, month, day, hour, minute) = match.destructured.toList().map { it.toInt() }
            if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(year, month - 1, day)
            }
            return TeeTimeParts(
                dayOfMonth = day,
                monthIndex = month - 1,
                weekdayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1,
                time = "%02d:%02d".format(hour, minute)
            )
        }
    }
}
