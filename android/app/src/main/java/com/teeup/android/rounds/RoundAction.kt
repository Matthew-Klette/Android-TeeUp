package com.teeup.android.rounds

import com.teeup.android.data.ScheduledRound
import com.teeup.android.data.roundTimestamp

/** The legacy/solo-row default, the same fallback ScorecardActivity uses without EXTRA_HOLE_COUNT. */
private const val DEFAULT_HOLES = 18

/** What a round's card offers. */
sealed interface RoundAction {
    /** Unstarted and before its tee time: the API rejects a scorecard until then (RoundService). */
    data object NotYetStartable : RoundAction

    /** [fixedHoles] is the group's hole count, fixed at creation; null for a legacy/solo row,
     *  which asks. Always asking let a 9-hole group answer 18 and post holes the API rejects. */
    data class Start(val fixedHoles: Int?) : RoundAction

    data class Continue(val totalHoles: Int) : RoundAction

    data object ViewSummary : RoundAction

    companion object {
        /** Throws IllegalArgumentException when the round's date can't be read. */
        fun of(round: ScheduledRound, nowMillis: Long): RoundAction {
            val (scored, total) = progress(round)
            return when {
                round.round == null && roundTimestamp(round.dateTime) > nowMillis -> NotYetStartable
                round.round == null -> Start(round.holes)
                scored < total -> Continue(total)
                else -> ViewSummary
            }
        }

        /** Holes scored and the round's intended length — a 9-hole round with all 9 scored is
         *  finished, not "9 of 18, keep going". */
        fun progress(round: ScheduledRound): Pair<Int, Int> =
            (round.round?.scorecard?.size ?: 0) to (round.holes ?: DEFAULT_HOLES)
    }
}
