package com.teeup.android.summary

import com.teeup.android.data.HoleScore
import com.teeup.android.data.PlayedRound
import java.util.Locale

/**
 * What the round summary shows. Totals, averages, net and Stableford all come from the server
 * (RoundDto, EME-304: SUM/AVG over the scorecard rows there), not recomputed on the device.
 * Null strings show as "—" (unavailable): no round yet, or no handicap for net/Stableford.
 */
data class SummaryStats(
    val roundId: String?,
    val holes: List<HoleScore>,
    val holesPlayed: Int,
    val totalStrokes: Int,
    val totalPutts: Int,
    val averagePutts: String?,
    val netScore: String?,
    val stableford: String?,
    /** Holes are in but there's no net score: the player has no handicap index set. */
    val showHandicapHint: Boolean
) {
    companion object {
        fun from(round: PlayedRound?, locale: Locale = Locale.getDefault()): SummaryStats {
            val holes = round?.scorecard?.sortedBy { it.holeNumber }.orEmpty()
            return SummaryStats(
                roundId = round?.id,
                holes = holes,
                holesPlayed = holes.size,
                totalStrokes = round?.totalStrokes ?: 0,
                totalPutts = round?.totalPutts ?: 0,
                averagePutts = round?.let { String.format(locale, "%.1f", it.averagePutts) },
                netScore = round?.netScore?.let { String.format(locale, "%.1f", it) },
                stableford = round?.stablefordScore?.toString(),
                showHandicapHint = holes.isNotEmpty() && round?.netScore == null
            )
        }
    }
}
