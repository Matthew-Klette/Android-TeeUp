package com.teeup.android.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {
    @Test
    fun beatsRunInStoryOrder() {
        val beats = with(SplashTimeline) {
            listOf(TEE_RISE_END, BALL_LAND, BALL_SETTLE, CLUB_ENTER, IMPACT, CLUB_EXIT, GLASS_HIT, BALL_REBOUND, FLIP_START, END)
        }

        assertEquals(beats.sorted(), beats)
    }

    @Test
    fun clubConnectsBeforeTheBallReachesTheGlass() {
        assertTrue(SplashTimeline.CLUB_ENTER < SplashTimeline.IMPACT)
        assertTrue(SplashTimeline.IMPACT < SplashTimeline.GLASS_HIT)
    }

    @Test
    fun wholeIntroStaysUnderThreeSeconds() {
        assertTrue(SplashTimeline.END < 3000f)
    }

    @Test
    fun progressIsClampedToTheWindow() {
        assertEquals(0f, SplashTimeline.progress(-50f, 0f, 100f), 0f)
        assertEquals(0.5f, SplashTimeline.progress(50f, 0f, 100f), 1e-6f)
        assertEquals(1f, SplashTimeline.progress(500f, 0f, 100f), 0f)
    }

    @Test
    fun easingCurvesStartAtZeroAndLandOnOne() {
        listOf(Ease::outCubic, Ease::inQuad, Ease::inOutSine).forEach { curve ->
            assertEquals(0f, curve(0f), 1e-6f)
            assertEquals(1f, curve(1f), 1e-6f)
        }
        assertEquals(0f, Ease.outBack(0f), 1e-6f)
        assertEquals(1f, Ease.outBack(1f), 1e-6f)
    }

    @Test
    fun outBackOvershootsBeforeSettling() {
        assertTrue(Ease.outBack(0.8f) > 1f)
    }
}
