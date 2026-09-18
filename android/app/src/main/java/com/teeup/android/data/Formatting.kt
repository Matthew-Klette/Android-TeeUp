package com.teeup.android.data

/**
 * Formats an ISO-8601 UTC timestamp like "2026-09-19T14:45:53.639622Z"
 * (what the API's System.Text.Json serializer emits for a `DateTime`) into
 * "19 Sep · 14:45". Plain substring parsing on purpose: `java.time` needs
 * API 26+ (minSdk here is 24) and desugaring is out of scope for this
 * ticket, so this avoids the dependency entirely. Displays the UTC value
 * as-is rather than converting to the device's local time zone — fine for
 * a Part 2 prototype, worth revisiting later.
 */
private val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

fun formatTeeTime(isoDateTime: String): String {
    require(isoDateTime.length >= 16) { "Expected an ISO-8601 timestamp, got: $isoDateTime" }
    val day = isoDateTime.substring(8, 10).toInt()
    val month = isoDateTime.substring(5, 7).toInt()
    val hour = isoDateTime.substring(11, 13)
    val minute = isoDateTime.substring(14, 16)
    val monthName = MONTH_NAMES[(month - 1).coerceIn(0, 11)]
    return "$day $monthName · $hour:$minute"
}

fun formatPrice(price: Double): String =
    if (price <= 0.0) "Free" else "R%.0f".format(price)
