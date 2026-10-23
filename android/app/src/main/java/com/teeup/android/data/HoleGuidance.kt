package com.teeup.android.data

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The numbers the hole map shows, all whole metres. Measured from the golfer's GPS position
 * when they're on this hole ([fromGps]), otherwise from the tee, so the map is still useful
 * for previewing a hole from the clubhouse (or a lecture hall).
 */
data class HoleGuidance(
    val origin: GeoPoint,
    val fromGps: Boolean,
    val toFront: Int?,
    val toMiddle: Int,
    val toBack: Int?,
    /** Null when there's no target (e.g. a par 3: you aim straight at the green). */
    val originToTarget: Int?,
    val targetToPin: Int?,
    val holeLengthMeters: Int
) {
    companion object {
        /** Roughly a club golfer's drive; where the target marker starts on longer holes. */
        const val DEFAULT_TARGET_METERS = 230.0

        /** Holes shorter than this are treated as par 3s: no layup target. */
        const val SHORT_HOLE_METERS = 220.0

        /** On a shortish par 4 the default target stays this fraction of the way to the pin,
         *  so it never lands on or past the green. */
        const val MAX_TARGET_FRACTION = 0.75

        /** Where the draggable target starts, measured along the line of play from the tee. */
        fun defaultTarget(hole: HoleLayout): GeoPoint? {
            val length = GeoMath.pathLengthMeters(hole.lineOfPlay)
            if (length < SHORT_HOLE_METERS) return null
            return GeoMath.pointAlongPath(hole.lineOfPlay, min(DEFAULT_TARGET_METERS, length * MAX_TARGET_FRACTION))
        }

        fun compute(hole: HoleLayout, userPosition: GeoPoint?, userOnThisHole: Boolean, target: GeoPoint?): HoleGuidance {
            val fromGps = userPosition != null && userOnThisHole
            val origin = if (fromGps) userPosition!! else hole.tee
            fun metres(a: GeoPoint, b: GeoPoint) = GeoMath.distanceMeters(a, b).roundToInt()
            return HoleGuidance(
                origin = origin,
                fromGps = fromGps,
                toFront = hole.greenFront?.let { metres(origin, it) },
                toMiddle = metres(origin, hole.greenMiddle),
                toBack = hole.greenBack?.let { metres(origin, it) },
                originToTarget = target?.let { metres(origin, it) },
                targetToPin = target?.let { metres(it, hole.greenMiddle) },
                holeLengthMeters = GeoMath.pathLengthMeters(hole.lineOfPlay).roundToInt()
            )
        }
    }
}
