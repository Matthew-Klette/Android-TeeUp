package com.teeup.android.ui.splash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * Beat sheet for the splash intro, in ms from the first frame. Every element
 * derives its pose from these as a pure function of time, which is what lets
 * the cracked-glass frame be rendered up front and still match the live one.
 */
internal object SplashTimeline {
    /** Plays the beat sheet this much slower than written, for a calmer read; res/raw/splash_sfx is mixed at this pace. */
    const val PACE = 1.2f
    const val TEE_RISE_END = 300f
    const val BALL_DROP_START = 110f
    const val BALL_LAND = 400f
    const val BALL_SETTLE = 540f
    const val PUSH_IN_START = 300f
    const val CLUB_ENTER = 690f
    const val IMPACT = 780f
    const val CLUB_EXIT = 850f
    const val GLASS_HIT = 1020f
    const val BALL_REBOUND = 1240f
    const val CRACK_SPREAD = 70f
    const val FLIP_START = 1320f
    const val FLIP_STAGGER = 420f
    const val FLIP_DURATION = 480f
    /** Random per-shard delay on top of the stagger, so the wave isn't mechanically even. */
    const val FLIP_JITTER = 40f
    const val EDGE_FADE = 160f
    const val END = FLIP_START + FLIP_STAGGER + FLIP_JITTER + FLIP_DURATION + EDGE_FADE

    /** 0..1 position of [t] inside [start, end], clamped. */
    fun progress(t: Float, start: Float, end: Float): Float =
        ((t - start) / (end - start)).coerceIn(0f, 1f)
}

internal object Ease {
    fun outCubic(x: Float): Float = 1f - (1f - x).pow(3)

    fun inQuad(x: Float): Float = x * x

    fun inOutSine(x: Float): Float = (1f - cos(PI.toFloat() * x)) / 2f

    /** Runs slightly past 1 and back, like a pane snapping into its frame. */
    fun outBack(x: Float, overshoot: Float = 1.2f): Float {
        val c3 = overshoot + 1f
        return 1f + c3 * (x - 1f).pow(3) + overshoot * (x - 1f).pow(2)
    }
}
