package com.teeup.android.data

import android.content.Context
import android.util.Log
import com.teeup.android.R
import org.json.JSONException

/**
 * Where HoleMapActivity gets a course's holes from. The API comes first, so newly mapped
 * courses appear without an app update; a copy bundled in assets/holes/ covers the demo course
 * when the API is unreachable (no signal out on the course) or that course isn't seeded yet.
 * The bundled files come from the same generator as the API's seed SQL
 * (infra/osm-holes/import_osm_holes.py), so both sources hold identical data.
 */
object HoleLayoutSource {
    private const val TAG = "HoleLayoutSource"

    /** Keyed by course name rather than id: the API's row and MockCatalog's offline copy of the
     *  same course have different ids but the same name. Every course OpenStreetMap has holes
     *  for, plus the Port Elizabeth courses traced by hand (infra/courses/holes/ says how); the
     *  rest have no hole map until someone maps them. */
    private val BUNDLED_LAYOUTS = mapOf(
        "Fancourt Links" to "holes/fancourt-links.json",
        "Humewood Golf Club" to "holes/humewood.json",
        "Wanderers Golf Club" to "holes/wanderers.json",
        "Port Elizabeth Golf Club" to "holes/port-elizabeth.json",
        "Little Walmer Golf Club" to "holes/little-walmer.json",
        "Wedgewood Golf Estate" to "holes/wedgewood.json",
        "Durban Country Club" to "holes/durban-country-club.json",
        "Royal Johannesburg and Kensington Golf Club" to "holes/royal-johannesburg-kensington.json",
        "Leopard Creek Country Club" to "holes/leopard-creek.json",
        "Erinvale Golf Club" to "holes/erinvale.json",
        "Silver Lakes Golf Estate" to "holes/silver-lakes.json",
        "Steenberg Golf Club" to "holes/steenberg.json",
        "East London Golf Club" to "holes/east-london.json"
    )

    /** Names the deployed database used before infra/courses/seed-courses.sql renamed these
     *  courses, so their maps work whether or not that seed has run. */
    private val FORMER_NAMES = mapOf(
        "Little Walmer Golf Course" to "Little Walmer Golf Club",
        "Wedgewood Golf & Country Estate" to "Wedgewood Golf Estate"
    )

    private const val MOCK_ID_PREFIX = "mock-"
    private const val HTTP_NOT_FOUND = 404

    /** What to show when the API couldn't give us a course's holes. */
    enum class Fallback { BUNDLED, UNMAPPED, ERROR }

    fun bundledAsset(courseName: String): String? = BUNDLED_LAYOUTS[FORMER_NAMES[courseName] ?: courseName]

    val bundledCourseNames: Set<String> get() = BUNDLED_LAYOUTS.keys

    /** A 404 means the API has no map for the course (or, on an API deployed before the holes
     *  endpoint, no endpoint at all): that's an unmapped course, not "this item is no longer
     *  available". Anything else is only an error when there's no bundled copy to fall back on. */
    fun fallbackFor(error: Exception, hasBundled: Boolean): Fallback = when {
        hasBundled -> Fallback.BUNDLED
        error is ApiException && error.statusCode == HTTP_NOT_FOUND -> Fallback.UNMAPPED
        else -> Fallback.ERROR
    }

    /**
     * Blocks; call off the main thread. Returns the course's holes, or an empty list if nobody
     * has mapped it. Throws [ApiException] only when the API couldn't be reached (or broke) and
     * there's no bundled copy to fall back on.
     */
    fun load(context: Context, courseId: String, courseName: String): List<HoleLayout> {
        val bundledAsset = bundledAsset(courseName)
        if (courseId.startsWith(MOCK_ID_PREFIX)) {
            Log.d(TAG, "Offline catalog course $courseName; using bundled holes ($bundledAsset)")
            return bundledAsset?.let { readAsset(context, it) } ?: emptyList()
        }

        val fromApi = try {
            TeeUpApiClient.fetchHoleLayouts(courseId)
        } catch (e: Exception) {
            // ApiException for network/HTTP failures; JSONException when a 200 isn't our JSON
            // (e.g. a Wi-Fi captive portal's login page).
            if (e !is ApiException && e !is JSONException) throw e
            when (fallbackFor(e, hasBundled = bundledAsset != null)) {
                Fallback.BUNDLED -> {
                    Log.w(TAG, "Hole map API failed for $courseName, using bundled copy: ${e.message}")
                    return readAsset(context, requireNotNull(bundledAsset))
                }
                Fallback.UNMAPPED -> {
                    Log.i(TAG, "API has no hole map for $courseName and there's no bundled copy")
                    return emptyList()
                }
                Fallback.ERROR -> throw e as? ApiException ?: ApiException(context.getString(R.string.hole_map_load_failed))
            }
        }

        if (fromApi.isEmpty() && bundledAsset != null) {
            Log.i(TAG, "API has no hole map for $courseName yet; using bundled copy")
            return readAsset(context, bundledAsset)
        }
        Log.d(TAG, "Loaded ${fromApi.size} holes for $courseName from the API")
        return fromApi
    }

    private fun readAsset(context: Context, path: String): List<HoleLayout> =
        TeeUpApiClient.parseHoleLayouts(context.assets.open(path).bufferedReader().use { it.readText() })
}
