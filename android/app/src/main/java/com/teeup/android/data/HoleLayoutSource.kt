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
     *  same course have different ids but the same name. */
    private val BUNDLED_LAYOUTS = mapOf("Fancourt Links" to "holes/fancourt-links.json")

    private const val MOCK_ID_PREFIX = "mock-"

    /**
     * Blocks; call off the main thread. Returns the course's holes, or an empty list if nobody
     * has mapped it. Throws [ApiException] only when the API failed and there's no bundled copy
     * to fall back on.
     */
    fun load(context: Context, courseId: String, courseName: String): List<HoleLayout> {
        val bundledAsset = BUNDLED_LAYOUTS[courseName]
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
            if (bundledAsset == null) {
                throw e as? ApiException ?: ApiException(context.getString(R.string.hole_map_load_failed))
            }
            Log.w(TAG, "Hole map API failed for $courseName, using bundled copy: ${e.message}")
            return readAsset(context, bundledAsset)
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
