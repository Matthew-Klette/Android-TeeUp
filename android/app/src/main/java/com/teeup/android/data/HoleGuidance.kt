package com.teeup.android.data

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The numbers the hole map shows, all whole metres. Measured from wherever the golfer dragged
 * their marker, else from their GPS position when they're on this hole, else from the tee, so the
 * map is still useful for previewing a hole from the clubhouse (or a lecture hall). [source] says which.
 */
data class HoleGuidance(
    val origin: GeoPoint,
    val source: Source,
    val toFront: Int?,
    val toMiddle: Int,
    val toBack: Int?,
    /** To the flag: today's pin if the golfer dragged it, else the middle of the green. */
    val toPin: Int,
    val pinMoved: Boolean,
    /** Null when there's no target (e.g. a par 3: you aim straight at the green). */
    val originToTarget: Int?,
    val targetToPin: Int?,
    val holeLengthMeters: Int
) {
    enum class Source { TEE, GPS, PLACED }

    val fromGps: Boolean get() = source == Source.GPS

    companion object {
        /** Roughly a club golfer's drive; where the target marker starts on longer holes. */
        const val DEFAULT_TARGET_METERS = 230.0

        /** Holes shorter than this are treated as par 3s: no layup target. */
        const val SHORT_HOLE_METERS = 220.0

        /** On a shortish par 4 the default target stays this fraction of the way to the pin,
         *  so it never lands on or past the green. */
        const val MAX_TARGET_FRACTION = 0.75

        /** How far a dragged flag may go from the middle of a green whose edges aren't mapped. */
        const val DEFAULT_GREEN_RADIUS_METERS = 20.0

        /** Keeps a dragged flag on the green: no further from the middle than the green's mapped
         *  front or back edge (or [DEFAULT_GREEN_RADIUS_METERS]), in the direction it was dragged. */
        fun clampPin(hole: HoleLayout, flag: GeoPoint): GeoPoint {
            val middle = hole.greenMiddle
            val radius = listOfNotNull(hole.greenFront, hole.greenBack)
                .maxOfOrNull { GeoMath.distanceMeters(middle, it) } ?: DEFAULT_GREEN_RADIUS_METERS
            val distance = GeoMath.distanceMeters(middle, flag)
            if (distance <= radius) return flag
            return GeoMath.destinationPoint(middle, GeoMath.bearingDegrees(middle, flag), radius)
        }

        /** Where the draggable target starts, measured along the line of play from the tee. */
        fun defaultTarget(hole: HoleLayout): GeoPoint? {
            val length = GeoMath.pathLengthMeters(hole.lineOfPlay)
            if (length < SHORT_HOLE_METERS) return null
            return GeoMath.pointAlongPath(hole.lineOfPlay, min(DEFAULT_TARGET_METERS, length * MAX_TARGET_FRACTION))
        }

        /** [placedPlayer] is where the golfer dragged their marker and wins over GPS; [pin] is
         *  where they dragged the flag. Null for either means it hasn't been moved. */
        fun compute(
            hole: HoleLayout,
            userPosition: GeoPoint?,
            userOnThisHole: Boolean,
            target: GeoPoint?,
            placedPlayer: GeoPoint? = null,
            pin: GeoPoint? = null
        ): HoleGuidance {
            val (origin, source) = when {
                placedPlayer != null -> placedPlayer to Source.PLACED
                userPosition != null && userOnThisHole -> userPosition to Source.GPS
                else -> hole.tee to Source.TEE
            }
            val flag = pin ?: hole.greenMiddle
            fun metres(a: GeoPoint, b: GeoPoint) = GeoMath.distanceMeters(a, b).roundToInt()
            return HoleGuidance(
                origin = origin,
                source = source,
                toFront = hole.greenFront?.let { metres(origin, it) },
                toMiddle = metres(origin, hole.greenMiddle),
                toBack = hole.greenBack?.let { metres(origin, it) },
                toPin = metres(origin, flag),
                pinMoved = pin != null,
                originToTarget = target?.let { metres(origin, it) },
                targetToPin = target?.let { metres(it, flag) },
                holeLengthMeters = GeoMath.pathLengthMeters(hole.lineOfPlay).roundToInt()
            )
        }
    }
}
