package com.teeup.android.data

import com.teeup.android.R
import com.teeup.android.TeeUpApplication
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

/** English fallbacks for when [TeeUpApplication.appContextOrNull] is unset — only true in
 *  plain-JVM unit tests (RobustnessTest); see Formatting.kt's doc comment for why. */
private val HTTP_ERROR_FALLBACKS = mapOf(
    400 to "Some details are invalid. Check your entries and try again.",
    401 to "Please sign in again to continue.",
    403 to "You do not have permission to do that.",
    404 to "This item is no longer available.",
    409 to "This item has changed. Reload it before trying again.",
    429 to "Too many requests. Wait a moment and try again."
)
private const val HTTP_ERROR_5XX_FALLBACK = "The service is unavailable. Please try again shortly."
private const val HTTP_ERROR_DEFAULT_FALLBACK = "The request could not be completed. Please try again."

fun httpFailureMessage(code: Int): String {
    val context = TeeUpApplication.appContextOrNull
    val resId = when (code) {
        400 -> R.string.http_error_400
        401 -> R.string.http_error_401
        403 -> R.string.http_error_403
        404 -> R.string.http_error_404
        409 -> R.string.http_error_409
        429 -> R.string.http_error_429
        in 500..599 -> R.string.http_error_5xx
        else -> R.string.http_error_default
    }
    context?.let { return it.getString(resId) }
    return HTTP_ERROR_FALLBACKS[code]
        ?: if (code in 500..599) HTTP_ERROR_5XX_FALLBACK else HTTP_ERROR_DEFAULT_FALLBACK
}
