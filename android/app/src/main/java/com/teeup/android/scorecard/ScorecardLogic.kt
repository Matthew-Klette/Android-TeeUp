package com.teeup.android.scorecard

import com.teeup.android.data.ScheduledRound

/** The legacy/solo-row default, as in RoundsActivity and the in-round screen. */
private const val DEFAULT_HOLES = 18

/** One scorecard on the Scorecard tab's landing list. */
data class LandingItem(val round: ScheduledRound, val holesScored: Int, val totalHoles: Int) {
    val isComplete: Boolean get() = holesScored >= totalHoles
}

object ScorecardLanding {
    /** Every round with scoring started — in progress or complete — most recently dated first. */
    fun items(schedule: List<ScheduledRound>): List<LandingItem> =
        schedule.filter { it.round != null }
            .sortedByDescending { it.dateTime }
            .map { LandingItem(it, it.round?.scorecard?.size ?: 0, it.holes ?: DEFAULT_HOLES) }
}

/** Previous / next through the holes that actually have a map layout. */
object HoleStep {
    /** The mapped hole [delta] steps from [current], or null past either end (or if [current]
     *  isn't mapped). */
    fun next(mapped: List<Int>, current: Int, delta: Int): Int? {
        val index = mapped.indexOf(current)
        if (index < 0) return null
        return mapped.getOrNull(index + delta)
    }

    /** Whether there's a mapped hole before and after [current]. */
    fun canStep(mapped: List<Int>, current: Int): Pair<Boolean, Boolean> {
        val index = mapped.indexOf(current)
        if (index < 0) return false to false
        return (index > 0) to (index < mapped.lastIndex)
    }
}
