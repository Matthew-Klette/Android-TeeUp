package com.teeup.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundProgressCodecTest {
    @Test
    fun entries_roundTrip() {
        val entries = listOf(HoleScoreInput(1, 5, 2), HoleScoreInput(4, 1, 0), HoleScoreInput(18, 12, 4))
        assertEquals(entries, RoundProgressCodec.decodeEntries(RoundProgressCodec.encodeEntries(entries)))
    }

    @Test
    fun decodeEntries_nullOrEmpty_isEmpty() {
        assertTrue(RoundProgressCodec.decodeEntries(null).isEmpty())
        assertTrue(RoundProgressCodec.decodeEntries("").isEmpty())
    }

    @Test
    fun decodeEntries_skipsMalformedOrInvalidParts() {
        // Wrong field count, not a number, and putts above strokes are dropped; the good one survives.
        val decoded = RoundProgressCodec.decodeEntries("1:5;2:x:1;3:4:2;4:3:5")
        assertEquals(listOf(HoleScoreInput(3, 4, 2)), decoded)
    }

    @Test
    fun point_roundTrip() {
        val point = GeoPoint(-33.9632, 22.4196)
        assertEquals(point, RoundProgressCodec.decodePoint(RoundProgressCodec.encodePoint(point)))
    }

    @Test
    fun point_nullOrMalformed_isNull() {
        assertNull(RoundProgressCodec.encodePoint(null))
        assertNull(RoundProgressCodec.decodePoint(null))
        assertNull(RoundProgressCodec.decodePoint("-33.9"))
        assertNull(RoundProgressCodec.decodePoint("a,b"))
    }
}
