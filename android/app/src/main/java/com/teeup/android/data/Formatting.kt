package com.teeup.android.data

import com.teeup.android.R
import com.teeup.android.TeeUpApplication

/**
 * Formats an ISO-8601 UTC timestamp like "2026-09-19T14:45:53.639622Z"
 * (what the API's System.Text.Json serializer emits for a `DateTime`) into
 * "19 Sep · 14:45". Plain substring parsing on purpose: `java.time` needs
 * API 26+ (minSdk here is 24) and desugaring is out of scope for this
 * ticket, so this avoids the dependency entirely. Displays the UTC value
 * as-is rather than converting to the device's local time zone — fine for
 * a Part 2 prototype, worth revisiting later. No Context is passed in (every
 * caller is a plain top-level fun), so this reads resources off the app-wide
 * Context the same way DevIdentity does — falling back to the English default
 * when that Context doesn't exist yet, which is only true in plain-JVM unit
 * tests (FormattingTest): Application.onCreate() always runs first in a real
 * app, so production code always has it.
 */
private val FALLBACK_MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

fun formatTeeTime(isoDateTime: String): String {
    val context = TeeUpApplication.appContextOrNull
    val dateUnavailable = context?.getString(R.string.date_unavailable) ?: "Date unavailable"
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*$").matches(isoDateTime)) return dateUnavailable
    val day = isoDateTime.substring(8, 10).toIntOrNull() ?: return dateUnavailable
    val month = isoDateTime.substring(5, 7).toIntOrNull() ?: return dateUnavailable
    val hour = isoDateTime.substring(11, 13)
    val minute = isoDateTime.substring(14, 16)
    if (day !in 1..31 || month !in 1..12 || hour.toInt() !in 0..23 || minute.toInt() !in 0..59)
        return dateUnavailable
    val monthName = context?.resources?.getStringArray(R.array.month_abbreviations)?.get(month - 1)
        ?: FALLBACK_MONTH_NAMES[month - 1]
    return "$day $monthName · $hour:$minute"
}

fun formatPrice(price: Double): String = when {
    price > 0.0 -> "R%.0f".format(price)
    else -> TeeUpApplication.appContextOrNull?.getString(R.string.price_free) ?: "Free"
}

/** True if `userId` has no Pending or Accepted request against this tee time already. */
fun canRequestToJoin(existing: List<JoinRequest>, userId: String): Boolean =
    existing.none { it.guestUserId == userId && it.status != JoinRequestStatus.DECLINED }
