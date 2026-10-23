package com.teeup.android.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle maths for the hole map: haversine distance, initial bearing and destination
 * point (Veness, n.d.). Plain functions on [GeoPoint] so they're unit-testable off-device.
 */
object GeoMath {
    /** Mean Earth radius; gives 111,195 m per degree of latitude. */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Compass bearing from [from] towards [to], 0..360 clockwise from north. */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** The point [distanceMeters] away from [from] heading [bearingDegrees]. */
    fun destinationPoint(from: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val angular = distanceMeters / EARTH_RADIUS_METERS
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(from.latitude)
        val lng1 = Math.toRadians(from.longitude)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lng2 = lng1 + atan2(sin(bearing) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /**
     * Shortest distance from [point] to the segment [start]→[end]. Uses a flat projection around
     * [point], which is accurate to centimetres over a golf hole and keeps this cheap enough to
     * run against every hole on every GPS fix.
     */
    fun distanceToSegmentMeters(point: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val (ax, ay) = toLocalMeters(point, start)
        val (bx, by) = toLocalMeters(point, end)
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        // Fraction along the segment of the closest point to the origin (the golfer), clamped to the ends.
        val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    fun distanceToPathMeters(point: GeoPoint, path: List<GeoPoint>): Double = when (path.size) {
        0 -> Double.MAX_VALUE
        1 -> distanceMeters(point, path[0])
        else -> path.zipWithNext().minOf { (a, b) -> distanceToSegmentMeters(point, a, b) }
    }

    fun pathLengthMeters(path: List<GeoPoint>): Double =
        path.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

    /** The point [distanceMeters] along [path] from its start, or the last point if the path is shorter. */
    fun pointAlongPath(path: List<GeoPoint>, distanceMeters: Double): GeoPoint {
        var remaining = distanceMeters
        for ((a, b) in path.zipWithNext()) {
            val leg = distanceMeters(a, b)
            if (remaining <= leg) return destinationPoint(a, bearingDegrees(a, b), remaining)
            remaining -= leg
        }
        return path.last()
    }

    private fun toLocalMeters(origin: GeoPoint, point: GeoPoint): Pair<Double, Double> {
        val x = Math.toRadians(point.longitude - origin.longitude) * cos(Math.toRadians(origin.latitude)) * EARTH_RADIUS_METERS
        val y = Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS
        return x to y
    }
}

/*
References:

Veness, C. (n.d.). Calculate distance, bearing and more between Latitude/Longitude points. [online] Movable Type Scripts. Available at: <https://www.movable-type.co.uk/scripts/latlong.html> [Accessed 23 Oct. 2026].
*/
