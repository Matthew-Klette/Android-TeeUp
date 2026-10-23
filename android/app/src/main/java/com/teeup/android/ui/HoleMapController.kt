package com.teeup.android.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import com.teeup.android.R
import com.teeup.android.data.GeoMath
import com.teeup.android.data.GeoPoint
import com.teeup.android.data.HoleFix
import com.teeup.android.data.HoleGuidance
import com.teeup.android.data.HoleLayout
import com.teeup.android.data.HoleLayoutSource
import com.teeup.android.data.HoleTracker
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * The live hole map shared by HoleMapActivity (course preview) and ScorecardActivity (in a
 * round): satellite imagery of one hole, rotated so it plays up the screen, with live distances
 * to the front, middle and back of the green and a draggable layup target, modelled on the
 * Wedge golf app's hole view.
 *
 * Owns state, GPS and every calculation ([HoleTracker] picks the hole, [HoleGuidance] works out
 * the distances); assets/hole_map.html only draws what it's told through `window.teeupMap` and
 * reports target drags back through [MapBridge]. The host layout must contain the map and
 * distance views bound in the constructor; hole navigation buttons stay with the host, which
 * hears about every change through [Listener].
 *
 * With no GPS fix on the course (location off, or previewing from home) every distance is
 * measured from the tee instead, so the map is still useful before a round.
 */
class HoleMapController(private val activity: Activity, private val listener: Listener) : LocationListener {
    interface Listener {
        /** The map now shows [hole], whether the host asked for it or GPS moved it there. */
        fun onHoleShown(hole: Int) {}
        fun onFollowGpsChanged(following: Boolean) {}
        /** The golfer dragged the layup target on [hole]. */
        fun onTargetMoved(hole: Int, target: GeoPoint) {}
    }

    companion object {
        private const val TAG = "HoleMapController"
        private const val REQUEST_LOCATION = 41
        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val LOCATION_MIN_DISTANCE_M = 2f
        private const val METERS_PER_KM = 1_000.0
        /** Network fixes are coarse, so they're ignored while GPS is delivering, but used again once
         *  GPS has gone quiet this long (e.g. under trees or inside the clubhouse). */
        private const val GPS_STALE_MS = 10_000L
        /** Placeholder https origin for the page (same host as CoursePreviewActivity). Requests to it
         *  are answered from assets/ by [AssetWebViewClient]; it never touches the network. */
        private const val MAP_HOST = "appassets.teeup.local"
        private const val MAP_BASE_URL = "https://$MAP_HOST/"
        private val LOCATION_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    }

    private val webView: WebView = activity.findViewById(R.id.webview_hole_map)
    private val mapStatusText: TextView = activity.findViewById(R.id.text_map_status)
    private val holeLengthText: TextView = activity.findViewById(R.id.text_hole_length)
    private val distanceSourceText: TextView = activity.findViewById(R.id.text_distance_source)
    private val frontText: TextView = activity.findViewById(R.id.text_distance_front)
    private val middleText: TextView = activity.findViewById(R.id.text_distance_middle)
    private val backText: TextView = activity.findViewById(R.id.text_distance_back)
    private val gpsStatusText: TextView = activity.findViewById(R.id.text_gps_status)

    private var holes: List<HoleLayout> = emptyList()
    private var holesLoaded = false
    /** The hole the host asked for. It may have no layout (a course mapped only in part). */
    var shownHoleNumber = 0
        private set
    /** A target the host restored for [shownHoleNumber], applied once the holes arrive. */
    private var restoredTarget: GeoPoint? = null
    private var target: GeoPoint? = null
    private var userPosition: GeoPoint? = null
    private var userAccuracyMeters: Float? = null
    private var lastFix: HoleFix? = null
    /** On by default: the map jumps to whichever hole GPS says you're on. Manual moves turn it off. */
    var isFollowingGps = true
        private set
    private var mapReady = false
    /** Set once the map can't be shown at all; it then stays hidden behind this message. */
    private var failureMessage: String? = null
    private var locationDenied = false
    /** Ask once per screen: asking again straight after a denial (or an Approximate-only grant)
     *  would loop the system prompt. */
    private var permissionRequested = false
    private var noLocationProvider = false
    private var hasCourse = false
    /** Mirrors the host's resumed state, so [start] after an async load still starts GPS. */
    private var isResumed = false
    private var lastGpsFixAt = 0L

    private val shownHole: HoleLayout? get() = holes.firstOrNull { it.hole == shownHoleNumber }

    /** Hole numbers that have a layout, in playing order. Empty until they've loaded. */
    val mappedHoles: List<Int> get() = holes.map { it.hole }

    /** True while GPS is placing the golfer on a hole, so the map will move with them. */
    val isFollowingOnCourse: Boolean get() = isFollowingGps && lastFix?.onCourse == true

    /** Limits the map to the round's holes (a 9-hole round on an 18-hole course); null shows every mapped hole. */
    private var playableHoles: IntRange? = null

    /** [startHole] 0 opens on the first mapped hole. [startTarget] restores a target dragged on [startHole].
     *  [playableHoles] keeps GPS from moving the map to a hole outside the round. */
    fun start(
        courseId: String,
        courseName: String,
        startHole: Int,
        startTarget: GeoPoint? = null,
        playableHoles: IntRange? = null
    ) {
        hasCourse = true
        shownHoleNumber = startHole
        restoredTarget = startTarget
        this.playableHoles = playableHoles
        updateMapMessage()
        setUpMap()
        loadHoles(courseId, courseName)
        // A host that only knows the course after a network call starts the map once already resumed.
        if (isResumed) startLocationUpdates()
    }

    /** For a screen opened without a course: shows why there's no map. */
    fun showUnavailable() {
        Log.w(TAG, "No course to map")
        failureMessage = activity.getString(R.string.hole_map_unavailable)
        updateMapMessage()
        gpsStatusText.visibility = View.GONE
    }

    /** Moves to [hole] at the host's request. Turns GPS follow off unless [keepFollowing], so the
     *  map doesn't jump straight back to the hole the golfer is standing on. */
    fun showHole(hole: Int, keepFollowing: Boolean = false) {
        if (!keepFollowing && isFollowingGps) {
            isFollowingGps = false
            listener.onFollowGpsChanged(false)
            Log.d(TAG, "Manually moved to hole $hole; GPS follow off")
        }
        if (hole == shownHoleNumber) return
        shownHoleNumber = hole
        restoredTarget = null
        selectHole()
    }

    fun resumeFollowingGps() {
        isFollowingGps = true
        listener.onFollowGpsChanged(true)
        val fix = lastFix
        if (fix != null && fix.onCourse) jumpToHole(fix.hole)
        Log.d(TAG, "GPS follow back on")
    }

    fun onResume() {
        isResumed = true
        if (hasCourse) startLocationUpdates()
    }

    fun onPause() {
        isResumed = false
        stopLocationUpdates()
    }

    fun onDestroy() {
        // A WebView must be detached before destroy(), or it can leak the Activity.
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    fun onRequestPermissionsResult(requestCode: Int) {
        if (requestCode != REQUEST_LOCATION) return
        locationDenied = !hasLocationPermission()
        Log.i(TAG, if (locationDenied) "Location permission denied; tee distances only" else "Location permission granted")
        if (!locationDenied) startLocationUpdates() else updateGpsStatus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpMap() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = AssetWebViewClient()
        // Only exposes MapBridge's @JavascriptInterface methods, all of which just post to the UI thread.
        webView.addJavascriptInterface(MapBridge(), "TeeUpBridge")
        val html = activity.assets.open("hole_map.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL(MAP_BASE_URL, html, "text/html", "utf-8", null)
    }

    private fun loadHoles(courseId: String, courseName: String) {
        Thread {
            try {
                val loaded = HoleLayoutSource.load(activity, courseId, courseName)
                activity.runOnUiThread { if (isAlive()) onHolesLoaded(loaded) }
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't load holes for $courseName", e)
                activity.runOnUiThread {
                    if (isAlive()) {
                        failureMessage = e.message ?: activity.getString(R.string.hole_map_load_failed)
                        updateMapMessage()
                    }
                }
            }
        }.start()
    }

    private fun isAlive() = !activity.isFinishing && !activity.isDestroyed

    private fun onHolesLoaded(allHoles: List<HoleLayout>) {
        val loaded = playableHoles?.let { playable -> allHoles.filter { it.hole in playable } } ?: allHoles
        holes = loaded
        holesLoaded = true
        if (loaded.isEmpty()) {
            // Nothing to measure against, so live GPS would only cost battery.
            hasCourse = false
            stopLocationUpdates()
            gpsStatusText.visibility = View.GONE
        } else if (shownHoleNumber == 0) {
            shownHoleNumber = loaded.first().hole
        }
        Log.i(TAG, "${loaded.size} holes loaded; opening on hole $shownHoleNumber")
        // A fix that arrived while holes were still loading couldn't be placed on a hole yet.
        userPosition?.let { updateFix(it) }
        selectHole()
    }

    /** Resets the target for a newly shown hole (or restores the saved one) and draws it. */
    private fun selectHole() {
        val hole = shownHole
        target = restoredTarget ?: hole?.let(HoleGuidance::defaultTarget)
        restoredTarget = null
        renderHole()
    }

    private fun renderHole() {
        updateMapMessage()
        val hole = shownHole
        if (hole != null && mapReady) {
            val payload = JSONObject()
                .put("tee", pointJson(hole.tee))
                .put("pin", pointJson(hole.greenMiddle))
                .put("doglegs", JSONArray(hole.doglegs.map(::pointJson)))
                .put("bearing", GeoMath.bearingDegrees(hole.tee, hole.greenMiddle))
            callMap("showHole", payload)
        }
        refreshGuidance()
        listener.onHoleShown(shownHoleNumber)
    }

    /** Recomputes every distance for the shown hole and redraws the guide line. */
    private fun refreshGuidance() {
        updateGpsStatus()
        val hole = shownHole
        if (hole == null) {
            holeLengthText.text = ""
            distanceSourceText.setText(R.string.hole_map_from_tee)
            listOf(frontText, middleText, backText).forEach { it.setText(R.string.hole_map_no_distance) }
            return
        }
        val fix = lastFix
        val onThisHole = fix != null && fix.onCourse && fix.hole == hole.hole
        val guidance = HoleGuidance.compute(hole, userPosition, onThisHole, target)

        holeLengthText.text = activity.getString(R.string.hole_map_distance_format, guidance.holeLengthMeters)
        distanceSourceText.setText(if (guidance.fromGps) R.string.hole_map_from_you else R.string.hole_map_from_tee)
        frontText.text = formatDistance(guidance.toFront)
        middleText.text = formatDistance(guidance.toMiddle)
        backText.text = formatDistance(guidance.toBack)

        if (!mapReady) return
        val points = listOfNotNull(guidance.origin, target, hole.greenMiddle)
        val labels = listOfNotNull(guidance.originToTarget, guidance.targetToPin)
            .ifEmpty { listOf(guidance.toMiddle) }
            .map { activity.getString(R.string.hole_map_distance_format, it) }
        val payload = JSONObject()
            .put("points", JSONArray(points.map(::pointJson)))
            .put("labels", JSONArray(labels))
            .put("hasTarget", target != null)
        callMap("setGuide", payload)
    }

    private fun jumpToHole(holeNumber: Int) {
        if (holeNumber == shownHoleNumber || holes.none { it.hole == holeNumber }) return
        shownHoleNumber = holeNumber
        restoredTarget = null
        Log.i(TAG, "GPS moved the map to hole $holeNumber")
        selectHole()
    }

    // ---- Location ----

    /** Precise or Approximate both work: Approximate just means network-level accuracy. */
    private fun hasLocationPermission() =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            if (!permissionRequested) {
                permissionRequested = true
                activity.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQUEST_LOCATION
                )
            } else {
                locationDenied = true
            }
            updateGpsStatus()
            return
        }
        // Covers granting it from Settings after an earlier denial.
        locationDenied = false
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Registered even while switched off, so turning location on mid-screen starts fixes
        // arriving without a pause/resume (the listener gets onProviderEnabled).
        // The GPS provider needs Precise; with Approximate only, the network provider is all we may use.
        val precise = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = LOCATION_PROVIDERS
            .filter { it in manager.allProviders }
            .filter { precise || it == LocationManager.NETWORK_PROVIDER }
        noLocationProvider = providers.none { manager.isProviderEnabled(it) }
        try {
            providers.forEach { manager.requestLocationUpdates(it, LOCATION_INTERVAL_MS, LOCATION_MIN_DISTANCE_M, this, activity.mainLooper) }
            Log.d(TAG, "Location updates started from $providers")
        } catch (e: SecurityException) {
            // Permission revoked between the check and the request (e.g. from Settings mid-screen).
            Log.w(TAG, "Location permission lost", e)
            locationDenied = true
        }
        updateGpsStatus()
    }

    private fun stopLocationUpdates() {
        // GPS is the biggest battery cost here; never keep it running off-screen.
        (activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        Log.d(TAG, "Location updates paused")
    }

    override fun onLocationChanged(location: Location) {
        val accuracy = location.accuracy
        val now = SystemClock.elapsedRealtime()
        if (location.provider == LocationManager.GPS_PROVIDER) {
            lastGpsFixAt = now
        } else if (lastGpsFixAt != 0L && now - lastGpsFixAt < GPS_STALE_MS) {
            // GPS is delivering; a coarse network fix would only make the dot jump about.
            return
        }

        val position = GeoPoint(location.latitude, location.longitude)
        userPosition = position
        userAccuracyMeters = accuracy
        callMap("setUser", pointJson(position))
        updateFix(position)
        refreshGuidance()
    }

    private fun updateFix(position: GeoPoint) {
        if (holes.isEmpty()) return
        val current = lastFix?.hole ?: shownHoleNumber.takeIf { it != 0 }
        val fix = HoleTracker.detect(position, holes, current) ?: return
        if (fix.hole != lastFix?.hole || fix.onCourse != lastFix?.onCourse) {
            Log.d(TAG, "Fix: hole ${fix.hole}, ${fix.distanceToHoleMeters.roundToInt()} m from its line, onCourse=${fix.onCourse}")
        }
        lastFix = fix
        if (isFollowingGps && fix.onCourse) jumpToHole(fix.hole)
    }

    // Overridden explicitly: on API 24-28 these aren't default methods, so leaving them out
    // throws AbstractMethodError the first time a provider changes state.
    @Deprecated("Deprecated in API 29 but still called on older devices")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) {
        noLocationProvider = false
        updateGpsStatus()
    }

    override fun onProviderDisabled(provider: String) {
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        noLocationProvider = LOCATION_PROVIDERS.none { it in manager.allProviders && manager.isProviderEnabled(it) }
        updateGpsStatus()
    }

    private fun updateGpsStatus() {
        val fix = lastFix
        val accuracy = userAccuracyMeters
        gpsStatusText.text = when {
            locationDenied -> activity.getString(R.string.hole_map_gps_denied)
            noLocationProvider -> activity.getString(R.string.hole_map_gps_disabled)
            userPosition == null || accuracy == null -> activity.getString(R.string.hole_map_gps_waiting)
            fix != null && !fix.onCourse ->
                activity.getString(R.string.hole_map_gps_off_course_format, fix.distanceToHoleMeters / METERS_PER_KM)
            else -> activity.getString(R.string.hole_map_gps_accuracy_format, accuracy.roundToInt())
        }
    }

    // ---- Map page plumbing ----

    private fun callMap(function: String, argument: Any) {
        if (!mapReady) return
        // JSONObject.toString() is valid JS literal syntax, so no further escaping is needed.
        webView.evaluateJavascript("window.teeupMap.$function($argument);", null)
    }

    private fun pointJson(point: GeoPoint) = JSONObject().put("lat", point.latitude).put("lng", point.longitude)

    private fun formatDistance(meters: Int?): String =
        meters?.let { activity.getString(R.string.hole_map_distance_format, it) }
            ?: activity.getString(R.string.hole_map_no_distance)

    /** Covers the map with a message while it loads, when the shown hole has no layout, or for
     *  good once it can't be shown. The distances below the map keep working regardless. */
    private fun updateMapMessage() {
        val noLayout = holesLoaded && shownHole == null
        val message = failureMessage ?: when {
            noLayout -> activity.getString(R.string.hole_map_unavailable)
            !holesLoaded || !mapReady -> activity.getString(R.string.hole_map_loading)
            else -> null
        }
        mapStatusText.text = message.orEmpty()
        mapStatusText.visibility = if (message == null) View.GONE else View.VISIBLE
        // While loading, the page starts up behind the message; otherwise the blank map is hidden.
        webView.visibility = if (failureMessage != null || noLayout) View.INVISIBLE else View.VISIBLE
    }

    /** Called from assets/hole_map.html on a WebView binder thread, so every call hops to the UI thread. */
    private inner class MapBridge {
        @JavascriptInterface
        fun onReady() = activity.runOnUiThread {
            if (!isAlive()) return@runOnUiThread
            mapReady = true
            Log.d(TAG, "Map page ready")
            userPosition?.let { callMap("setUser", pointJson(it)) }
            renderHole()
        }

        @JavascriptInterface
        fun onTargetMoved(latitude: Double, longitude: Double) = activity.runOnUiThread {
            if (!isAlive()) return@runOnUiThread
            val moved = GeoPoint(latitude, longitude)
            target = moved
            refreshGuidance()
            listener.onTargetMoved(shownHoleNumber, moved)
        }

        /** MapLibre couldn't start (e.g. no WebGL). Distances below the map still work without it. */
        @JavascriptInterface
        fun onMapError(message: String) = activity.runOnUiThread {
            Log.e(TAG, "Map page failed to start: $message")
            if (isAlive()) {
                failureMessage = activity.getString(R.string.hole_map_load_failed)
                updateMapMessage()
            }
        }
    }

    /**
     * Serves the page's own files (assets/maplibre/...) for requests to [MAP_HOST], so the map
     * library loads with no signal. Tiles and anything else go to the network as normal. Also
     * keeps link taps (the map's attribution links) out of this WebView, so the JS bridge is
     * never reachable from another site's page.
     */
    private inner class AssetWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host != MAP_HOST) return null
            val path = url.path?.trimStart('/').orEmpty()
            return try {
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path)) ?: "application/octet-stream"
                WebResourceResponse(mimeType, "utf-8", activity.assets.open(path))
            } catch (e: java.io.IOException) {
                Log.w(TAG, "No bundled asset for $url")
                WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), "".byteInputStream())
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, request.url))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No app to open ${request.url}")
            }
            return true
        }
    }
}

/*
References:

Android Developers (2026). LocationManager. [online] Available at: <https://developer.android.com/reference/android/location/LocationManager> [Accessed 23 Oct. 2026].

Android Developers (2026). Building web apps in WebView: Bind JavaScript code to Android code. [online] Available at: <https://developer.android.com/develop/ui/views/layout/webapps/webview#BindingJavaScript> [Accessed 23 Oct. 2026].

Android Developers (2026). Request location permissions. [online] Available at: <https://developer.android.com/develop/sensors-and-location/location/permissions> [Accessed 23 Oct. 2026].
*/
