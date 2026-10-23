package com.teeup.android.profile

private const val MAX_HANDICAP = 54.0

sealed interface HandicapEntry {
    /** A handicap to save; null clears it. */
    data class Value(val handicap: Double?) : HandicapEntry
    data object Invalid : HandicapEntry
}

/** Playing Details' handicap field: blank clears it, otherwise a number from 0 to 54. This
 *  screen has always used a plain toDoubleOrNull (no comma decimals), unlike the filters. */
object PlayingHandicap {
    fun parse(text: String): HandicapEntry {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return HandicapEntry.Value(null)
        val value = trimmed.toDoubleOrNull()?.takeIf { it in 0.0..MAX_HANDICAP } ?: return HandicapEntry.Invalid
        return HandicapEntry.Value(value)
    }
}

object DisplayName {
    /** The trimmed name, or null when there's nothing left to save. */
    fun validOrNull(text: String): String? = text.trim().takeIf { it.isNotEmpty() }
}

/** What Offline Sync reports about the connection. */
enum class ConnectionKind {
    NONE, WIFI, MOBILE, ETHERNET, OTHER;

    companion object {
        /** From the active network's capabilities; the first matching transport wins. */
        fun of(hasNetwork: Boolean, internet: Boolean, wifi: Boolean, cellular: Boolean, ethernet: Boolean): ConnectionKind = when {
            !hasNetwork || !internet -> NONE
            wifi -> WIFI
            cellular -> MOBILE
            ethernet -> ETHERNET
            else -> OTHER
        }
    }
}
