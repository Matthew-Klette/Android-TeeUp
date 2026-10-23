package com.teeup.android.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileFormsTest {
    @Test
    fun handicap_blankClearsIt() {
        assertEquals(HandicapEntry.Value(null), PlayingHandicap.parse("   "))
    }

    @Test
    fun handicap_zeroToFiftyFour() {
        assertEquals(HandicapEntry.Value(0.0), PlayingHandicap.parse("0"))
        assertEquals(HandicapEntry.Value(54.0), PlayingHandicap.parse("54"))
        assertEquals(HandicapEntry.Value(12.5), PlayingHandicap.parse(" 12.5 "))
    }

    @Test
    fun handicap_outOfRangeOrNotANumberIsInvalid() {
        assertEquals(HandicapEntry.Invalid, PlayingHandicap.parse("54.1"))
        assertEquals(HandicapEntry.Invalid, PlayingHandicap.parse("-1"))
        assertEquals(HandicapEntry.Invalid, PlayingHandicap.parse("abc"))
    }

    @Test
    fun handicap_keepsThisScreensExistingRule_noCommaDecimals() {
        // Playing Details has always parsed with toDoubleOrNull, unlike the group filters.
        assertEquals(HandicapEntry.Invalid, PlayingHandicap.parse("12,5"))
    }

    @Test
    fun displayName_trimmed_andRequired() {
        assertEquals("Matt", DisplayName.validOrNull("  Matt "))
        assertNull(DisplayName.validOrNull("   "))
    }

    @Test
    fun connection_noNetworkOrNoInternet() {
        assertEquals(ConnectionKind.NONE, ConnectionKind.of(hasNetwork = false, internet = true, wifi = true, cellular = false, ethernet = false))
        assertEquals(ConnectionKind.NONE, ConnectionKind.of(hasNetwork = true, internet = false, wifi = true, cellular = false, ethernet = false))
    }

    @Test
    fun connection_transportOrder_wifiThenMobileThenEthernet() {
        assertEquals(ConnectionKind.WIFI, ConnectionKind.of(hasNetwork = true, internet = true, wifi = true, cellular = true, ethernet = false))
        assertEquals(ConnectionKind.MOBILE, ConnectionKind.of(hasNetwork = true, internet = true, wifi = false, cellular = true, ethernet = true))
        assertEquals(ConnectionKind.ETHERNET, ConnectionKind.of(hasNetwork = true, internet = true, wifi = false, cellular = false, ethernet = true))
        assertEquals(ConnectionKind.OTHER, ConnectionKind.of(hasNetwork = true, internet = true, wifi = false, cellular = false, ethernet = false))
    }
}
