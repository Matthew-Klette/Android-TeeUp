package com.teeup.android.data

/**
 * Which GPS fixes the hole map may measure from, and for how long. A yardage that's quietly wrong
 * is worse than none, so vague, old or lost fixes fall back to tee distances with a status line
 * saying why, instead of showing numbers from the wrong place.
 */
object GpsFixPolicy {
    /** Vaguer than this and the fix isn't used at all: ±50 m is half a par 3. */
    const val MAX_USABLE_ACCURACY_METERS = 50f

    /** A fix older than this when it arrives (a cached last-known fix) is ignored. */
    const val MAX_FIX_AGE_MS = 30_000L

    /** No new fix for this long: still shown, with a "signal lost" warning. */
    const val STALE_AFTER_MS = 20_000L

    /** No new fix for this long: the old position is dropped and distances go back to the tee. */
    const val EXPIRE_AFTER_MS = 90_000L

    /** Further than this from the shown hole's line, "distances from you" would be meaningless
     *  (the car park, the clubhouse), so they're measured from the tee instead. */
    const val NEAR_HOLE_METERS = 150.0

    enum class Freshness { LIVE, STALE, EXPIRED }

    /** [accuracyMeters] null means the fix didn't report one. */
    fun isUsable(accuracyMeters: Float?, ageMs: Long): Boolean =
        accuracyMeters != null && accuracyMeters <= MAX_USABLE_ACCURACY_METERS && ageMs in 0..MAX_FIX_AGE_MS

    fun freshness(sinceLastFixMs: Long): Freshness = when {
        sinceLastFixMs >= EXPIRE_AFTER_MS -> Freshness.EXPIRED
        sinceLastFixMs >= STALE_AFTER_MS -> Freshness.STALE
        else -> Freshness.LIVE
    }

    /** Whether to measure from the golfer rather than the tee on the shown hole. */
    fun measureFromGolfer(fix: HoleFix?, shownHole: Int, freshness: Freshness): Boolean =
        fix != null && fix.onCourse && fix.hole == shownHole &&
            fix.distanceToHoleMeters <= NEAR_HOLE_METERS && freshness != Freshness.EXPIRED
}
