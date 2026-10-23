package com.teeup.android.creategroup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarMonthTest {
    @Test
    fun cells_padTheFirstWeekToTheWeekStart_sundayFirst() {
        // October 2026 starts on a Thursday.
        val cells = CalendarMonth.cells(2026, Calendar.OCTOBER, Calendar.SUNDAY)
        assertEquals(listOf(null, null, null, null, 1, 2, 3), cells.take(7))
    }

    @Test
    fun cells_padTheFirstWeekToTheWeekStart_mondayFirst() {
        val cells = CalendarMonth.cells(2026, Calendar.OCTOBER, Calendar.MONDAY)
        assertEquals(listOf(null, null, null, 1, 2, 3, 4), cells.take(7))
    }

    @Test
    fun cells_fillWholeWeeks_andEndOnTheLastDay() {
        // October 2026 fills exactly five weeks; November 2026 (starts on a Sunday) leaves five blanks.
        val october = CalendarMonth.cells(2026, Calendar.OCTOBER, Calendar.SUNDAY)
        assertEquals(35, october.size)
        assertEquals(31, october.last())
        val november = CalendarMonth.cells(2026, Calendar.NOVEMBER, Calendar.SUNDAY)
        assertEquals(35, november.size)
        assertEquals(30, november.filterNotNull().last())
        assertNull(november.last())
    }

    @Test
    fun cells_leapFebruary() {
        assertEquals(29, CalendarMonth.cells(2028, Calendar.FEBRUARY, Calendar.SUNDAY).filterNotNull().last())
    }

    @Test
    fun selectable_fromTodayOnwards() {
        val today = Triple(2026, Calendar.OCTOBER, 23)
        assertFalse(CalendarMonth.isSelectable(2026, Calendar.OCTOBER, 22, today))
        assertTrue(CalendarMonth.isSelectable(2026, Calendar.OCTOBER, 23, today))
        assertTrue(CalendarMonth.isSelectable(2026, Calendar.NOVEMBER, 1, today))
        assertFalse(CalendarMonth.isSelectable(2025, Calendar.DECEMBER, 31, today))
    }

    @Test
    fun shift_wrapsYears() {
        assertEquals(2027 to Calendar.JANUARY, CalendarMonth.shift(2026, Calendar.DECEMBER, 1))
        assertEquals(2025 to Calendar.DECEMBER, CalendarMonth.shift(2026, Calendar.JANUARY, -1))
    }
}
