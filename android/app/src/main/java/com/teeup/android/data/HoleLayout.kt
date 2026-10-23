package com.teeup.android.data

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Mirrors the API's HoleLayoutDto (api/TeeUp.Api/Dtos/CourseDtos.cs). [greenMiddle] is the pin;
 * [greenFront]/[greenBack] are null when the green's outline isn't mapped. [doglegs] are the
 * turns in the line of play, ordered tee to green.
 */
data class HoleLayout(
    val hole: Int,
    val tee: GeoPoint,
    val greenFront: GeoPoint?,
    val greenMiddle: GeoPoint,
    val greenBack: GeoPoint?,
    val doglegs: List<GeoPoint>
) {
    /** Tee → each dogleg → pin: the route the hole is designed to be played along. */
    val lineOfPlay: List<GeoPoint> get() = listOf(tee) + doglegs + greenMiddle
}
