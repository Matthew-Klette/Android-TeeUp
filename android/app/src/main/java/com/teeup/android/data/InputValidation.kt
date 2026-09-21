package com.teeup.android.data

import java.math.BigDecimal

fun validDisplayName(value: String): Boolean = value.trim().length in 1..100

/** Blank is optional; otherwise match the API's 0–54, one-decimal contract. */
fun validHandicap(value: String): Boolean {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isEmpty()) return true
    if (!Regex("[0-9]+(?:\\.[0-9]+)?").matches(normalized)) return false
    val number = normalized.toBigDecimalOrNull() ?: return false
    return number >= BigDecimal.ZERO && number <= BigDecimal("54") &&
        number.stripTrailingZeros().scale() <= 1
}

fun handicapValue(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()

fun httpFailureMessage(code: Int): String = when (code) {
    400 -> "Some details are invalid. Check your entries and try again."
    401 -> "Please sign in again to continue."
    403 -> "You do not have permission to do that."
    404 -> "This item is no longer available."
    409 -> "This item has changed. Reload it before trying again."
    429 -> "Too many requests. Wait a moment and try again."
    in 500..599 -> "The service is unavailable. Please try again shortly."
    else -> "The request could not be completed. Please try again."
}
