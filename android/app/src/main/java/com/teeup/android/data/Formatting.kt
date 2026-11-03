package com.teeup.android.data

import android.content.Context
import com.teeup.android.R
import com.teeup.android.TeeUpApplication

/**
 * Formats an ISO-8601 UTC timestamp like "2026-09-19T14:45:53.639622Z" as the golfer's local
 * time, e.g. "19 Sep · 16:45" in South Africa (UTC+2). The API stores real UTC instants (see
 * PickedDateTime.toUtcIso), so showing the UTC value as is would put every tee time two hours
 * early. Uses Calendar since java.time needs API 26+ and minSdk here is 24. Falls back to
 * English month names if no app Context exists yet, which only happens in plain JVM unit tests.
 */
private val FALLBACK_MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

fun formatTeeTime(isoDateTime: String, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
    val context = TeeUpApplication.appContextOrNull
    val dateUnavailable = context?.getString(R.string.date_unavailable) ?: "Date unavailable"
    val millis = teeTimeMillis(isoDateTime) ?: return dateUnavailable
    val local = java.util.Calendar.getInstance(zone).apply { timeInMillis = millis }
    val month = local.get(java.util.Calendar.MONTH)
    val monthName = context?.resources?.getStringArray(R.array.month_abbreviations)?.get(month)
        ?: FALLBACK_MONTH_NAMES[month]
    return "%d %s · %02d:%02d".format(
        java.util.Locale.US,
        local.get(java.util.Calendar.DAY_OF_MONTH), monthName,
        local.get(java.util.Calendar.HOUR_OF_DAY), local.get(java.util.Calendar.MINUTE)
    )
}

/** The UTC instant of an API timestamp ("2026-09-19T14:45:53.639622Z"), to the minute, or null
 *  if it isn't one. Seconds are ignored: tee times are whole minutes. */
fun teeTimeMillis(isoDateTime: String): Long? {
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*$").matches(isoDateTime)) return null
    val year = isoDateTime.substring(0, 4).toIntOrNull() ?: return null
    val month = isoDateTime.substring(5, 7).toIntOrNull() ?: return null
    val day = isoDateTime.substring(8, 10).toIntOrNull() ?: return null
    val hour = isoDateTime.substring(11, 13).toIntOrNull() ?: return null
    val minute = isoDateTime.substring(14, 16).toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
    val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        isLenient = false
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }
    return try {
        utc.timeInMillis
    } catch (e: IllegalArgumentException) {
        null // e.g. 31 Feb
    }
}

fun formatPrice(price: Double): String = when {
    price > 0.0 -> "R%.0f".format(price)
    else -> TeeUpApplication.appContextOrNull?.getString(R.string.price_free) ?: "Free"
}

/** True if `userId` has no Pending or Accepted request against this tee time already. */
fun canRequestToJoin(existing: List<JoinRequest>, userId: String): Boolean =
    existing.none { it.guestUserId == userId && it.status != JoinRequestStatus.DECLINED }

// "14.5" or "20" (no trailing .0, no floating-point artifacts like 12.300000000000001).
// The one place this formatting is done, so every screen that shows or edits a
// handicap (Home/TeeTimeDetail cards, Profile header and stats, Playing Details'
// edit field) stays in sync instead of drifting into its own implementation.
fun formatHandicapValue(handicap: Double): String =
    if (handicap == handicap.toInt().toDouble()) handicap.toInt().toString() else "%.1f".format(handicap)

/** [formatHandicapValue], or a fallback string when null.
 *  Used for a member's own handicap and for wanted-range values. */
fun formatHandicap(handicap: Double?): String {
    if (handicap == null) {
        return TeeUpApplication.appContextOrNull?.getString(R.string.group_no_handicap) ?: "No handicap"
    }
    return formatHandicapValue(handicap)
}

/** [formatHandicapValue], or empty when null — for pre-filling an editable field,
 *  where a fallback label like [formatHandicap]'s would just look like stray text. */
fun formatHandicapOrEmpty(handicap: Double?): String = handicap?.let { formatHandicapValue(it) }.orEmpty()

/** Labels a PaceOfPlay ordinal (0=Relaxed, 1=Standard, 2=Brisk).
 *  Falls back to the first label if the value is out of range. */
fun paceLabel(context: Context, paceOfPlay: Int): String {
    val options = context.resources.getStringArray(R.array.pace_of_play_options)
    return options.getOrElse(paceOfPlay) { options[0] }
}
