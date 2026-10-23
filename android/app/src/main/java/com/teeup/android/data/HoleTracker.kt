package com.teeup.android.data

/** Where the golfer is relative to the course: the hole they're on and how far from its line of play. */
data class HoleFix(val hole: Int, val distanceToHoleMeters: Double, val onCourse: Boolean)

/**
 * Works out which hole a golfer is playing from a GPS fix: the hole whose line of play
 * (tee → doglegs → pin) passes closest to them. The same idea as Wedge's hole view, which
 * measures the golfer's distance to each hole's segment.
 */
object HoleTracker {
    /** Further than this from every hole means the golfer isn't on the course at all. */
    const val OFF_COURSE_METERS = 500.0

    /** How much closer another hole must be before we switch to it. Walking off a green
     *  past the next tee, or standing between two parallel fairways, would otherwise make
     *  the map flick back and forth between holes. */
    const val SWITCH_HYSTERESIS_METERS = 15.0

    /** Null only when [holes] is empty. Off course, the current hole (or the nearest, if
     *  there isn't one yet) is kept so the map still has something to show. */
    fun detect(position: GeoPoint, holes: List<HoleLayout>, currentHole: Int?): HoleFix? {
        val distances = holes.associate { it.hole to GeoMath.distanceToPathMeters(position, it.lineOfPlay) }
        val nearest = distances.minByOrNull { it.value } ?: return null
        val current = currentHole?.let { hole -> distances[hole]?.let { hole to it } }

        if (nearest.value > OFF_COURSE_METERS) {
            val (hole, distance) = current ?: nearest.toPair()
            return HoleFix(hole, distance, onCourse = false)
        }
        if (current != null && current.second <= nearest.value + SWITCH_HYSTERESIS_METERS) {
            return HoleFix(current.first, current.second, onCourse = true)
        }
        return HoleFix(nearest.key, nearest.value, onCourse = true)
    }
}
