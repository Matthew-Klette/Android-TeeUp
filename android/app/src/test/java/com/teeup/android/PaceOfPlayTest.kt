package com.teeup.android

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Placeholder unit test so the Gradle CI workflow (EME-288) has something
 * real to run; superseded once client-side domain logic lands.
 */
class PaceOfPlayTest {
    @Test
    fun defaultPaceOfPlayIsStandard() {
        assertEquals("Standard", PaceOfPlay.Standard.name)
    }
}

enum class PaceOfPlay {
    Relaxed,
    Standard,
    Brisk
}
