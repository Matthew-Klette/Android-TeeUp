package com.teeup.android.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ScheduledRound(val teeTimeId: String, val courseId: String, val dateTime: String, val round: PlayedRound?)
data class PlayedRound(val id: String, val teeTimeId: String, val scorecard: List<HoleScore>)
data class HoleScore(val id: String, val holeNumber: Int, val strokes: Int, val putts: Int, val synced: Boolean)

// ASP.NET may emit a UTC DateTime without a suffix for older stored rows.
fun roundTimestamp(value: String): Long {
    val match = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,7}))?(Z|[+-]\\d{2}:\\d{2})?$")
        .matchEntire(value) ?: throw IllegalArgumentException("Invalid round date: $value")
    val milliseconds = match.groupValues[2].padEnd(3, '0').take(3)
    val zone = match.groupValues[3].ifEmpty { "Z" }
    val normalized = "${match.groupValues[1]}.$milliseconds$zone"
    // EME-305 QA pass: avoid `!!` on parse() — a malformed-but-regex-passing value
    // (e.g. day 32, month 13) should surface as the same "can't render this round"
    // failure the caller already handles, not an unchecked NPE crash.
    val parsed = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(normalized)
    } catch (e: java.text.ParseException) {
        throw IllegalArgumentException("Invalid round date: $value", e)
    }
    return (parsed ?: throw IllegalArgumentException("Invalid round date: $value")).time
}

fun selectRounds(rounds: List<ScheduledRound>, history: Boolean, now: Long): List<ScheduledRound> {
    val selected = rounds.filter { (roundTimestamp(it.dateTime) < now) == history }
        .sortedBy { roundTimestamp(it.dateTime) }
    return if (history) selected.reversed() else selected
}
