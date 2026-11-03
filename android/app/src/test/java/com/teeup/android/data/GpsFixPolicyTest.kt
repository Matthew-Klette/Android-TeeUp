package com.teeup.android.data

import com.teeup.android.data.GpsFixPolicy.Freshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsFixPolicyTest {
    @Test fun accurateFreshFixIsUsable() {
        assertTrue(GpsFixPolicy.isUsable(5f, 0))
        assertTrue(GpsFixPolicy.isUsable(GpsFixPolicy.MAX_USABLE_ACCURACY_METERS, GpsFixPolicy.MAX_FIX_AGE_MS))
    }

    @Test fun vagueFixIsRejected() {
        assertFalse(GpsFixPolicy.isUsable(GpsFixPolicy.MAX_USABLE_ACCURACY_METERS + 1f, 0))
        assertFalse(GpsFixPolicy.isUsable(1_500f, 0)) // a typical network/Approximate fix
    }

    @Test fun fixWithoutAccuracyIsRejected() {
        assertFalse(GpsFixPolicy.isUsable(null, 0))
    }

    @Test fun oldOrFutureFixIsRejected() {
        assertFalse(GpsFixPolicy.isUsable(5f, GpsFixPolicy.MAX_FIX_AGE_MS + 1))
        assertFalse(GpsFixPolicy.isUsable(5f, -1)) // clock skew: never trust it
    }

    @Test fun freshnessBands() {
        assertEquals(Freshness.LIVE, GpsFixPolicy.freshness(0))
        assertEquals(Freshness.LIVE, GpsFixPolicy.freshness(GpsFixPolicy.STALE_AFTER_MS - 1))
        assertEquals(Freshness.STALE, GpsFixPolicy.freshness(GpsFixPolicy.STALE_AFTER_MS))
        assertEquals(Freshness.EXPIRED, GpsFixPolicy.freshness(GpsFixPolicy.EXPIRE_AFTER_MS))
    }

    @Test fun measuresFromGolferOnlyWhenOnTheShownHoleAndNearIt() {
        val near = HoleFix(hole = 4, distanceToHoleMeters = 30.0, onCourse = true)
        assertTrue(GpsFixPolicy.measureFromGolfer(near, 4, Freshness.LIVE))
        assertTrue(GpsFixPolicy.measureFromGolfer(near, 4, Freshness.STALE))
        assertFalse(GpsFixPolicy.measureFromGolfer(near, 5, Freshness.LIVE)) // browsing another hole
        assertFalse(GpsFixPolicy.measureFromGolfer(near, 4, Freshness.EXPIRED))
        assertFalse(GpsFixPolicy.measureFromGolfer(null, 4, Freshness.LIVE))
    }

    @Test fun carParkIsTooFarToMeasureFromGolfer() {
        val carPark = HoleFix(hole = 1, distanceToHoleMeters = 400.0, onCourse = true)
        assertFalse(GpsFixPolicy.measureFromGolfer(carPark, 1, Freshness.LIVE))
        val offCourse = HoleFix(hole = 1, distanceToHoleMeters = 20.0, onCourse = false)
        assertFalse(GpsFixPolicy.measureFromGolfer(offCourse, 1, Freshness.LIVE))
    }
}
