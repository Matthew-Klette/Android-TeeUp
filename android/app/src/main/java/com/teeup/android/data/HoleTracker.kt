package com.teeup.android.data

/** Where the golfer is relative to the course: the hole they're on and how far from its line of play. */
data class HoleFix(val hole: Int, val distanceToHoleMeters: Double, val onCourse: Boolean)

/**
 * Works out which hole a golfer is playing from one GPS fix, using each hole's line of play
 * (tee → doglegs → pin), the same idea as Wedge's hole view.
 *
 * "Nearest hole" alone is wrong on a real course: holes run side by side, so a sliced drive into
 * the next fairway would move the map, and the scorecard with it, to the wrong hole. So the
 * golfer stays on the hole they're playing and only moves on in playing order:
 *  - to the next hole once they're standing on its tee, or have scored this hole and are heading
 *    its way;
 *  - to any hole only when they're clearly nowhere near the current one (started on 10, skipped
 *    a hole) and right on another.
 * They never move backwards by themselves; the ‹ › arrows cover going back.
 */
object HoleTracker {
    /** Further than this from every hole means the golfer isn't on the course at all. */
    const val OFF_COURSE_METERS = 500.0

    /** How much nearer another hole's line must be before it can win. Stops flicking between
     *  two holes when the golfer is roughly equidistant. */
    const val SWITCH_HYSTERESIS_METERS = 15.0

    /** Unscored hole: within this of the next tee (and well clear of this green) counts as being
     *  on the next tee. Tight, so a chip that runs off the back towards the next tee doesn't count. */
    const val NEXT_TEE_STRICT_METERS = 25.0

    /** Scored hole: anywhere this close to the next tee, and nearer it than this green, counts. */
    const val NEXT_TEE_RELAXED_METERS = 60.0

    /** Unscored hole: an errant shot can land this far off the line and still be on this hole. */
    const val STRAY_SHOT_METERS = 80.0

    /** Further than this from the current hole, the golfer has moved on to some other hole. */
    const val LOST_METERS = 150.0

    /**
     * [currentHole] is the hole the golfer is playing (null before the first fix). [currentHoleDone]
     * is true once it has a score, which lets the golfer move on to the next hole more readily.
     * Returns null only when [holes] is empty.
     */
    fun detect(position: GeoPoint, holes: List<HoleLayout>, currentHole: Int?, currentHoleDone: Boolean = false): HoleFix? {
        val ordered = holes.sortedBy { it.hole }
        val distances = ordered.associate { it.hole to GeoMath.distanceToPathMeters(position, it.lineOfPlay) }
        val nearest = distances.minByOrNull { it.value } ?: return null
        val current = ordered.firstOrNull { it.hole == currentHole }
            ?: return HoleFix(nearest.key, nearest.value, onCourse = nearest.value <= OFF_COURSE_METERS)
        val onCurrent = distances.getValue(current.hole)

        fun stay() = HoleFix(current.hole, onCurrent, onCourse = nearest.value <= OFF_COURSE_METERS)
        if (nearest.value > OFF_COURSE_METERS) return stay()

        val next = ordered.firstOrNull { it.hole > current.hole }
        if (next != null) {
            val onNext = distances.getValue(next.hole)
            val toNextTee = GeoMath.distanceMeters(position, next.tee)
            val toThisGreen = GeoMath.distanceMeters(position, current.greenMiddle)
            val reachedNextTee = if (currentHoleDone) {
                toNextTee <= NEXT_TEE_RELAXED_METERS && toNextTee < toThisGreen
            } else {
                toNextTee <= NEXT_TEE_STRICT_METERS && toThisGreen >= 2 * toNextTee
            }
            val playingNext = onNext + SWITCH_HYSTERESIS_METERS < onCurrent &&
                (currentHoleDone || onCurrent > STRAY_SHOT_METERS)
            if (reachedNextTee || playingNext) return HoleFix(next.hole, onNext, onCourse = true)
        }

        if (onCurrent > LOST_METERS && nearest.value + SWITCH_HYSTERESIS_METERS < onCurrent) {
            return HoleFix(nearest.key, nearest.value, onCourse = true)
        }
        return stay()
    }
}

/**
 * Turns the stream of GPS fixes into a steady current hole: a switch needs [CONFIRMATIONS]
 * agreeing fixes in a row, and a fix vaguer than [MAX_SWITCH_ACCURACY_METERS] never switches,
 * so one bad fix (multipath off a clubhouse roof, a network fix) can't move the map. Pure
 * Kotlin so the rules are unit-tested off-device.
 */
class HoleFollower(currentHole: Int?) {
    companion object {
        const val CONFIRMATIONS = 2
        const val MAX_SWITCH_ACCURACY_METERS = 25f
    }

    var currentHole: Int? = currentHole
        private set
    private var candidate: Int? = null
    private var candidateCount = 0

    /** Sets the hole outright (the golfer picked it), dropping any half-confirmed switch. */
    fun anchor(hole: Int?) {
        currentHole = hole
        clearCandidate()
    }

    /** [accuracyMeters] null means the fix didn't say, which is treated as untrustworthy. */
    fun update(
        position: GeoPoint,
        accuracyMeters: Float?,
        holes: List<HoleLayout>,
        isHoleDone: (Int) -> Boolean = { false }
    ): HoleFix? {
        val current = currentHole?.takeIf { hole -> holes.any { it.hole == hole } }
        val detected = HoleTracker.detect(position, holes, current, current?.let(isHoleDone) ?: false) ?: return null
        if (current == null) {
            anchor(detected.hole)
            return detected
        }
        if (detected.hole == current) {
            clearCandidate()
            return detected
        }
        val trustworthy = accuracyMeters != null && accuracyMeters <= MAX_SWITCH_ACCURACY_METERS
        if (!trustworthy) {
            clearCandidate()
        } else {
            candidateCount = if (candidate == detected.hole) candidateCount + 1 else 1
            candidate = detected.hole
            if (candidateCount >= CONFIRMATIONS) {
                anchor(detected.hole)
                return detected
            }
        }
        val line = holes.first { it.hole == current }.lineOfPlay
        return HoleFix(current, GeoMath.distanceToPathMeters(position, line), detected.onCourse)
    }

    private fun clearCandidate() {
        candidate = null
        candidateCount = 0
    }
}
