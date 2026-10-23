package com.teeup.android.creategroup

import java.util.Calendar

private const val DAYS_IN_WEEK = 7

/** The month grid of the date picker. Months are 0-based like [Calendar.MONTH]. */
object CalendarMonth {
    /** The month's days laid out in whole weeks starting on [firstDayOfWeek] (a Calendar day
     *  constant), with nulls for the blank cells before the 1st and after the last day. */
    fun cells(year: Int, monthIndex: Int, firstDayOfWeek: Int): List<Int?> {
        val first = Calendar.getInstance().apply {
            clear()
            set(year, monthIndex, 1)
        }
        val leading = (first.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val trailing = (DAYS_IN_WEEK - (leading + days) % DAYS_IN_WEEK) % DAYS_IN_WEEK
        return List(leading) { null } + (1..days).toList() + List(trailing) { null }
    }

    /** Today and later can be picked; [today] is (year, monthIndex, day). */
    fun isSelectable(year: Int, monthIndex: Int, day: Int, today: Triple<Int, Int, Int>): Boolean =
        compareValuesBy(Triple(year, monthIndex, day), today, { it.first }, { it.second }, { it.third }) >= 0

    /** The month [by] months after (year, monthIndex). */
    fun shift(year: Int, monthIndex: Int, by: Int): Pair<Int, Int> {
        val total = year * 12 + monthIndex + by
        return Math.floorDiv(total, 12) to Math.floorMod(total, 12)
    }
}
