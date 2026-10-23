package com.teeup.android.data

/**
 * Scoring rules for a round in progress on the in-round map screen. Holes can be scored in any
 * order — the map follows the golfer by GPS — so this tracks which holes still need a score
 * rather than assuming a 1..N prefix is done. Immutable: every change returns a new copy.
 */
data class RoundScoring(
    val totalHoles: Int,
    /** Already saved on the server. Read-only here: the API rejects a repeated hole number. */
    val saved: Map<Int, HoleScoreInput>,
    /** Entered this round but not sent yet; editable until the round is finished. */
    val pending: Map<Int, HoleScoreInput>
) {
    companion object {
        /** Null unless strokes is 1 or more and putts is between 0 and strokes. */
        fun entryOrNull(hole: Int, strokes: Int?, putts: Int?): HoleScoreInput? {
            if (strokes == null || strokes < 1 || putts == null || putts < 0 || putts > strokes) return null
            return HoleScoreInput(hole, strokes, putts)
        }
    }

    val holes: IntRange get() = 1..totalHoles

    val scoredCount: Int get() = holes.count(::isScored)

    val isComplete: Boolean get() = holes.all(::isScored)

    fun isScored(hole: Int) = hole in saved || hole in pending

    fun isSaved(hole: Int) = hole in saved

    fun scoreFor(hole: Int): HoleScoreInput? = pending[hole] ?: saved[hole]

    /** True when [hole] is the only one left without a score, so saving it ends the round. */
    fun isLastToScore(hole: Int) = !isSaved(hole) && holes.all { it == hole || isScored(it) }

    fun withEntry(entry: HoleScoreInput): RoundScoring {
        require(!isSaved(entry.holeNumber)) { "Hole ${entry.holeNumber} is already saved" }
        return copy(pending = pending + (entry.holeNumber to entry))
    }

    /** The first hole without a score, or null once every hole has one. */
    fun firstUnscored(): Int? = holes.firstOrNull { !isScored(it) }

    /** The next hole after [current] still needing a score, wrapping round to the start; null
     *  once every hole has one. */
    fun holeAfter(current: Int): Int? =
        holes.firstOrNull { it > current && !isScored(it) } ?: firstUnscored()

    /** Pending scores dropped for holes the server now has (e.g. scored on another phone) or
     *  outside this round's holes (a stale entry from a different hole count). */
    fun withoutStalePending(): RoundScoring =
        copy(pending = pending.filterKeys { it in holes && it !in saved })
}
