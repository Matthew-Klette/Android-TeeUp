package com.teeup.android.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.teeup.android.data.GeoPoint

/** One-off "where is the phone?" for suggestions like Start a Round's Nearby courses. The live,
 *  continuous fix the hole map needs stays in [HoleMapController]. */
object DeviceLocation {
    private const val TAG = "DeviceLocation"

    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context): Boolean =
        PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Calls [onResult] on the main thread: straight away with the freshest last-known fix, if any,
     * then again with a current one when the phone gets it. Never called without permission, and
     * not at all when location is off.
     */
    @SuppressLint("MissingPermission")
    fun request(context: Context, onResult: (GeoPoint) -> Unit) {
        if (!hasPermission(context)) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        // "Approximate" only (coarse) can't read GPS. The fused provider (Android 12+) serves
        // either grant, coarsened as needed; before that coarse gets the network provider only.
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(LocationManager.FUSED_PROVIDER) else emptyList()
        val usable = fused + if (precise) listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)
        try {
            val providers = usable.filter { it in manager.getProviders(true) }
            providers.mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { onResult(it.toGeoPoint()) }
            val provider = providers.firstOrNull() ?: return
            val noCancel: CancellationSignal? = null
            LocationManagerCompat.getCurrentLocation(manager, provider, noCancel, ContextCompat.getMainExecutor(context)) { location: Location? ->
                location?.let { onResult(it.toGeoPoint()) }
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: no suggestions, search still works.
            Log.w(TAG, "Location permission withdrawn: ${e.message}")
        }
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
