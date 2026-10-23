package com.teeup.android.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Test

class HoleLayoutFallbackTest {
    @Test
    fun notFound_withABundledCopy_usesIt() {
        // The deployed API answers 404 for /holes until it's redeployed; that's "no map here",
        // not "this item is no longer available".
        assertEquals(HoleLayoutSource.Fallback.BUNDLED, HoleLayoutSource.fallbackFor(ApiException("gone", 404), hasBundled = true))
    }

    @Test
    fun notFound_withoutABundledCopy_isUnmappedNotAnError() {
        assertEquals(HoleLayoutSource.Fallback.UNMAPPED, HoleLayoutSource.fallbackFor(ApiException("gone", 404), hasBundled = false))
    }

    @Test
    fun offline_usesTheBundledCopy() {
        assertEquals(HoleLayoutSource.Fallback.BUNDLED, HoleLayoutSource.fallbackFor(ApiException("no signal"), hasBundled = true))
        assertEquals(HoleLayoutSource.Fallback.BUNDLED, HoleLayoutSource.fallbackFor(JSONException("captive portal"), hasBundled = true))
    }

    @Test
    fun offline_withoutABundledCopy_isAnError() {
        assertEquals(HoleLayoutSource.Fallback.ERROR, HoleLayoutSource.fallbackFor(ApiException("no signal"), hasBundled = false))
        assertEquals(HoleLayoutSource.Fallback.ERROR, HoleLayoutSource.fallbackFor(ApiException("server", 500), hasBundled = false))
    }

    @Test
    fun bundledMapsAreFoundByTheCoursesExactName() {
        assertEquals("holes/humewood.json", HoleLayoutSource.bundledAsset("Humewood Golf Club"))
        assertEquals("holes/port-elizabeth.json", HoleLayoutSource.bundledAsset("Port Elizabeth Golf Club"))
        assertEquals("holes/little-walmer.json", HoleLayoutSource.bundledAsset("Little Walmer Golf Club"))
        assertEquals("holes/wedgewood.json", HoleLayoutSource.bundledAsset("Wedgewood Golf Estate"))
        // The live database still has these under their old names until infra/courses runs.
        assertEquals("holes/little-walmer.json", HoleLayoutSource.bundledAsset("Little Walmer Golf Course"))
        assertEquals("holes/wedgewood.json", HoleLayoutSource.bundledAsset("Wedgewood Golf & Country Estate"))
        // OSM has Pinnacle Point's greens and tees but not which tee plays to which green.
        assertEquals(null, HoleLayoutSource.bundledAsset("Pinnacle Point Golf Estate"))
        assertEquals(null, HoleLayoutSource.bundledAsset("Emeris Municipal Golf Course"))
    }
}
