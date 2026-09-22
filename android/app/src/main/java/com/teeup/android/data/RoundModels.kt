package com.teeup.android.data

import com.teeup.android.R
import com.teeup.android.TeeUpApplication
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** No Context is passed into [roundTimestamp] (a plain top-level fun, same as Formatting.kt),
 *  so this reads off the app-wide Context the same way DevIdentity does — falling back to
 *  English when unset, which is only true in plain-JVM unit tests (RobustnessTest). */
private fun invalidRoundDateMessage(value: String): String =
    TeeUpApplication.appContextOrNull?.getString(R.string.invalid_round_date_format, value)
        ?: "Invalid round date: $value"

/** [holes] is the tee time's intended round length (9 or 18) — null only for a legacy/solo row
 *  created before the API persisted it. Lets RoundsActivity tell "finished a 9-hole round" apart
 *  from "9 of 18 holes scored, still going" instead of assuming every round is 18 holes. */
data class ScheduledRound(val teeTimeId: String, val courseId: String, val dateTime: String, val holes: Int?, val round: PlayedRound?)

/** [averagePutts] is putts per hole played (AVG). [netScore]/[stablefordScore] are null when
 *  the scoring player has no handicap index set — both are computed net of handicap server-side
 *  (RoundDto, EME-304), not recomputed here. */
data class PlayedRound(
    val id: String,
    val teeTimeId: String,
    val scorecard: List<HoleScore>,
    val totalStrokes: Int,
    val totalPutts: Int,
    val averagePutts: Double,
    val netScore: Double?,
    val stablefordScore: Int?
)
data class HoleScore(val id: String, val holeNumber: Int, val strokes: Int, val putts: Int, val synced: Boolean)

/** What POST /api/rounds/{id}/scorecard accepts per hole — no id/synced yet, those come back from the server. */
data class HoleScoreInput(val holeNumber: Int, val strokes: Int, val putts: Int)

// ASP.NET may emit a UTC DateTime without a suffix for older stored rows.
fun roundTimestamp(value: String): Long {
    val match = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,7}))?(Z|[+-]\\d{2}:\\d{2})?$")
        .matchEntire(value) ?: throw IllegalArgumentException(invalidRoundDateMessage(value))
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
        throw IllegalArgumentException(invalidRoundDateMessage(value), e)
    }
    return (parsed ?: throw IllegalArgumentException(invalidRoundDateMessage(value))).time
}

fun selectRounds(rounds: List<ScheduledRound>, history: Boolean, now: Long): List<ScheduledRound> {
    val selected = rounds.filter { (roundTimestamp(it.dateTime) < now) == history }
        .sortedBy { roundTimestamp(it.dateTime) }
    return if (history) selected.reversed() else selected
}
