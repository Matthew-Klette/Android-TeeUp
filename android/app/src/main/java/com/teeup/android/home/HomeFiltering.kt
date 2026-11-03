package com.teeup.android.home

import com.teeup.android.data.Course
import com.teeup.android.data.TeeTime
import com.teeup.android.data.teeTimeMillis
import java.util.Calendar
import java.util.Locale
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
    /** [today]/[tomorrow] are "yyyy-MM-dd" on the phone's calendar, matching how tee times are shown
     *  (Formatting.kt), so a 00:30 tee time counts as that local day, not the UTC day before. */
    fun apply(
        teeTimes: List<TeeTime>,
        coursesById: Map<String, Course>,
        filters: HomeFilters,
        today: String,
        tomorrow: String,
        zone: TimeZone = TimeZone.getDefault()
    ): List<TeeTime> = teeTimes.filter { teeTime ->
        val courseName = coursesById[teeTime.courseId]?.name.orEmpty()
        val matchesSearch = filters.search.isBlank() || courseName.contains(filters.search.trim(), ignoreCase = true)
        val matchesDate = when (filters.date) {
            DateFilter.ALL -> true
            DateFilter.TODAY -> localDate(teeTime.dateTime, zone) == today
            DateFilter.TOMORROW -> localDate(teeTime.dateTime, zone) == tomorrow
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

    /** Today (0) or a day offset from it as "yyyy-MM-dd" on the phone's calendar. */
    fun isoDate(daysFromToday: Int, zone: TimeZone = TimeZone.getDefault()): String {
        val calendar = Calendar.getInstance(zone).apply { add(Calendar.DAY_OF_MONTH, daysFromToday) }
        return dateKey(calendar)
    }

    /** The local "yyyy-MM-dd" a tee time falls on, or "" if its timestamp is unreadable. */
    fun localDate(isoDateTime: String, zone: TimeZone = TimeZone.getDefault()): String {
        val millis = teeTimeMillis(isoDateTime) ?: return ""
        return dateKey(Calendar.getInstance(zone).apply { timeInMillis = millis })
    }

    private fun dateKey(calendar: Calendar) = String.format(
        Locale.US, "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH)
    )

}

/** The pieces of a tee time's date the card's date tile shows, in the golfer's local time like
 *  formatTeeTime (the API's UTC 06:00 is an 08:00 tee time in South Africa). */
data class TeeTimeParts(val dayOfMonth: Int, val monthIndex: Int, val weekdayIndex: Int, val time: String) {
    companion object {
        fun parse(isoDateTime: String, zone: TimeZone = TimeZone.getDefault()): TeeTimeParts? {
            val millis = teeTimeMillis(isoDateTime) ?: return null
            val local = Calendar.getInstance(zone).apply { timeInMillis = millis }
            return TeeTimeParts(
                dayOfMonth = local.get(Calendar.DAY_OF_MONTH),
                monthIndex = local.get(Calendar.MONTH),
                weekdayIndex = local.get(Calendar.DAY_OF_WEEK) - 1,
                time = String.format(Locale.US, "%02d:%02d", local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE))
            )
        }
    }
}
