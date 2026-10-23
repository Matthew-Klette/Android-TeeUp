package com.teeup.android.creategroup

import com.teeup.android.data.handicapValue
import com.teeup.android.data.roundTimestamp
import com.teeup.android.data.validHandicap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MIN_PLAYERS = 1
private const val MAX_PLAYERS = 20

/** What the Create/Edit Group form holds when Save is tapped. [paceIndex] 0 is "Any pace";
 *  1..3 are the PaceOfPlay ordinals 0..2. */
data class GroupForm(
    val isEdit: Boolean,
    val courseId: String?,
    val dateTimeIso: String?,
    val holes: Int,
    val openSpots: Int,
    val handicapMinText: String,
    val handicapMaxText: String,
    val paceIndex: Int
)

/** A form that passed validation, ready for createGroup / editGroup. */
data class ValidGroup(
    val courseId: String?,
    val dateTimeIso: String,
    val holes: Int,
    val openSpots: Int,
    val handicapMin: Double?,
    val handicapMax: Double?,
    val pace: Int?
)

enum class FormError { COURSE_REQUIRED, DATE_REQUIRED, DATE_IN_PAST, HANDICAP_MIN_INVALID, HANDICAP_MAX_INVALID, HANDICAP_RANGE }

sealed interface GroupFormResult {
    data class Valid(val group: ValidGroup) : GroupFormResult
    data class Invalid(val error: FormError) : GroupFormResult
}

object GroupFormValidator {
    /** Checks in the order the user sees problems: course (create only — editing never sends
     *  it), date and time, then the optional handicap range. */
    fun validate(form: GroupForm, nowMillis: Long): GroupFormResult {
        if (!form.isEdit && form.courseId == null) return GroupFormResult.Invalid(FormError.COURSE_REQUIRED)
        val dateTimeIso = form.dateTimeIso ?: return GroupFormResult.Invalid(FormError.DATE_REQUIRED)
        if (roundTimestamp(dateTimeIso) <= nowMillis) return GroupFormResult.Invalid(FormError.DATE_IN_PAST)
        if (!validHandicap(form.handicapMinText)) return GroupFormResult.Invalid(FormError.HANDICAP_MIN_INVALID)
        if (!validHandicap(form.handicapMaxText)) return GroupFormResult.Invalid(FormError.HANDICAP_MAX_INVALID)
        val min = handicapValue(form.handicapMinText)
        val max = handicapValue(form.handicapMaxText)
        if (min != null && max != null && min > max) return GroupFormResult.Invalid(FormError.HANDICAP_RANGE)
        return GroupFormResult.Valid(
            ValidGroup(
                courseId = form.courseId,
                dateTimeIso = dateTimeIso,
                holes = form.holes,
                openSpots = form.openSpots,
                handicapMin = min,
                handicapMax = max,
                pace = (form.paceIndex - 1).takeIf { it in 0..2 }
            )
        )
    }
}

object PlayersNeeded {
    fun change(current: Int, delta: Int): Int = (current + delta).coerceIn(MIN_PLAYERS, MAX_PLAYERS)
    fun clamp(value: Int): Int = value.coerceIn(MIN_PLAYERS, MAX_PLAYERS)
}

/**
 * A date and time as the user picked it: plain local wall-clock numbers, no zone attached.
 * [monthIndex] is 0-based like [Calendar.MONTH]. Converted to real UTC for the API — just
 * appending "Z" would send the wrong hour.
 */
data class PickedDateTime(val year: Int, val monthIndex: Int, val day: Int, val hour: Int, val minute: Int) {
    fun toMillis(zone: TimeZone): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, monthIndex, day, hour, minute, 0)
    }.timeInMillis

    fun toUtcIso(zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(toMillis(zone)))

    /** "1 Feb · 09:05", in the style of formatTeeTime but showing what was picked, not UTC. */
    fun label(monthAbbreviations: Array<String>): String =
        "%d %s · %02d:%02d".format(day, monthAbbreviations.getOrNull(monthIndex).orEmpty(), hour, minute)

    companion object {
        fun from(calendar: Calendar) = PickedDateTime(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )

        /** An API timestamp shown in [zone]; throws IllegalArgumentException if it's malformed. */
        fun fromIso(iso: String, zone: TimeZone = TimeZone.getDefault()): PickedDateTime =
            from(Calendar.getInstance(zone).apply { timeInMillis = roundTimestamp(iso) })
    }
}
