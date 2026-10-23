package com.teeup.android.creategroup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GroupFormTest {
    private val now = 1_800_000_000_000L // 2027-01-15T08:00:00Z
    private val future = "2027-02-01T07:30:00Z"
    private val past = "2027-01-01T07:30:00Z"

    private fun form(
        isEdit: Boolean = false,
        courseId: String? = "c1",
        dateTimeIso: String? = future,
        min: String = "",
        max: String = "",
        paceIndex: Int = 0
    ) = GroupForm(isEdit, courseId, dateTimeIso, holes = 18, openSpots = 2, handicapMinText = min, handicapMaxText = max, paceIndex = paceIndex)

    private fun error(form: GroupForm) = (GroupFormValidator.validate(form, now) as GroupFormResult.Invalid).error
    private fun valid(form: GroupForm) = (GroupFormValidator.validate(form, now) as GroupFormResult.Valid).group

    @Test
    fun create_needsACourse() {
        assertEquals(FormError.COURSE_REQUIRED, error(form(courseId = null)))
    }

    @Test
    fun edit_doesNotNeedACourse() {
        assertNull(valid(form(isEdit = true, courseId = null)).courseId)
    }

    @Test
    fun dateIsRequired() {
        assertEquals(FormError.DATE_REQUIRED, error(form(dateTimeIso = null)))
    }

    @Test
    fun dateMustBeInTheFuture() {
        assertEquals(FormError.DATE_IN_PAST, error(form(dateTimeIso = past)))
    }

    @Test
    fun courseIsCheckedBeforeDate() {
        assertEquals(FormError.COURSE_REQUIRED, error(form(courseId = null, dateTimeIso = null)))
    }

    @Test
    fun handicapsMustBeValid_minFirst() {
        assertEquals(FormError.HANDICAP_MIN_INVALID, error(form(min = "60", max = "abc")))
        assertEquals(FormError.HANDICAP_MAX_INVALID, error(form(min = "5", max = "12.34")))
    }

    @Test
    fun minAboveMaxIsRejected() {
        assertEquals(FormError.HANDICAP_RANGE, error(form(min = "20", max = "10")))
    }

    @Test
    fun blankHandicapsAreOptional_andCommaDecimalsAccepted() {
        val group = valid(form(min = "", max = "12,5"))
        assertNull(group.handicapMin)
        assertEquals(12.5, group.handicapMax!!, 0.0)
    }

    @Test
    fun paceIndexZeroIsAnyPace_restMapToPaceOrdinals() {
        assertNull(valid(form(paceIndex = 0)).pace)
        assertEquals(0, valid(form(paceIndex = 1)).pace)
        assertEquals(2, valid(form(paceIndex = 3)).pace)
    }

    @Test
    fun validGroupCarriesTheForm() {
        val group = valid(form())
        assertEquals("c1", group.courseId)
        assertEquals(future, group.dateTimeIso)
        assertEquals(18, group.holes)
        assertEquals(2, group.openSpots)
    }

    @Test
    fun playersNeeded_staysBetweenOneAndTwenty() {
        assertEquals(1, PlayersNeeded.change(1, -1))
        assertEquals(20, PlayersNeeded.change(20, 1))
        assertEquals(5, PlayersNeeded.change(4, 1))
    }

    @Test
    fun pickedTime_convertsLocalWallClockToUtcIso() {
        val joburg = TimeZone.getTimeZone("Africa/Johannesburg") // UTC+2, no DST
        val picked = PickedDateTime(2027, Calendar.FEBRUARY, 1, 9, 30)
        assertEquals("2027-02-01T07:30:00Z", picked.toUtcIso(joburg))
    }

    @Test
    fun pickedTime_fromIso_isInTheLocalZone() {
        val joburg = TimeZone.getTimeZone("Africa/Johannesburg")
        assertEquals(PickedDateTime(2027, Calendar.FEBRUARY, 1, 9, 30), PickedDateTime.fromIso("2027-02-01T07:30:00Z", joburg))
    }

    @Test
    fun pickedTime_label_showsWhatWasPicked() {
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        assertEquals("1 Feb · 09:05", PickedDateTime(2027, Calendar.FEBRUARY, 1, 9, 5).label(months))
    }
}
