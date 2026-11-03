package com.teeup.android.ui.splash

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.pow

/**
 * A driver swinging through the ball toward the lens. At ~45 m/s the head
 * crosses most of the frame inside one 1/60 s exposure, so a camera records
 * it as a translucent smear, not an object. That is what is drawn: many faint
 * sub-frame copies of the head across a shutter window, and the shaft as the
 * wedge it sweeps. Head geometry is in ball-radius units, face-on.
 */
internal class ClubSwing(
    private val strikeX: Float,
    private val strikeY: Float,
    private val ballRadius: Float,
    width: Float,
    height: Float,
) {
    private class Pose(val x: Float, val y: Float, val size: Float, val tilt: Float)

    private val startX = strikeX + width * 0.12f
    private val startY = -height * 0.18f
    private val endX = strikeX - width * 0.16f
    private val endY = height * 1.25f
    private val handsX = strikeX + width * 0.42f
    private val handsY = -height * 1.1f

    private val head = Path().apply {
        moveTo(1.55f, -0.55f)
        cubicTo(0.8f, -0.85f, -1.0f, -0.95f, -1.6f, -0.55f)
        cubicTo(-1.95f, -0.25f, -1.95f, 0.45f, -1.5f, 0.7f)
        cubicTo(-0.6f, 0.95f, 0.9f, 0.92f, 1.55f, 0.62f)
        cubicTo(1.85f, 0.45f, 1.85f, -0.35f, 1.55f, -0.55f)
        close()
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, -0.9f, 0f, 0.9f,
            intArrayOf(Color.rgb(0x4A, 0x4E, 0x53), Color.rgb(0x17, 0x19, 0x1B), Color.rgb(0x0A, 0x0B, 0x0C)),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x3E, 0x42) }
    private val sweep = Path()

    fun draw(canvas: Canvas, t: Float) {
        val open = t - SHUTTER
        if (t < SplashTimeline.CLUB_ENTER || open > SplashTimeline.CLUB_EXIT) return
        drawShaftSweep(canvas, maxOf(open, SplashTimeline.CLUB_ENTER), minOf(t, SplashTimeline.CLUB_EXIT))
        for (k in SAMPLES downTo 0) {
            val sample = t - SHUTTER * k / SAMPLES
            if (sample < SplashTimeline.CLUB_ENTER || sample > SplashTimeline.CLUB_EXIT) continue
            drawHead(canvas, pose(sample), (255 * SAMPLE_ALPHA * (1f - 0.5f * k / SAMPLES)).toInt())
        }
    }

    private fun pose(t: Float): Pose {
        if (t < SplashTimeline.IMPACT) {
            val q = SplashTimeline.progress(t, SplashTimeline.CLUB_ENTER, SplashTimeline.IMPACT)
            return Pose(lerp(startX, strikeX, q), lerp(startY, strikeY, q), ballRadius * lerp(0.45f, 1f, q), lerp(-14f, -4f, q))
        }
        // Past the ball it is closing on the lens, so it balloons and leaves frame fast.
        val q = SplashTimeline.progress(t, SplashTimeline.IMPACT, SplashTimeline.CLUB_EXIT)
        return Pose(lerp(strikeX, endX, q), lerp(strikeY, endY, q), ballRadius * (1f + 2.4f * q.pow(1.4f)), lerp(-4f, 10f, q))
    }

    private fun drawHead(canvas: Canvas, pose: Pose, alpha: Int) {
        canvas.save()
        canvas.translate(pose.x, pose.y)
        canvas.rotate(pose.tilt)
        canvas.scale(pose.size, pose.size)
        headPaint.alpha = alpha
        canvas.drawPath(head, headPaint)
        canvas.restore()
    }

    /** The area the shaft passes through while the shutter is open, as one faint wedge. */
    private fun drawShaftSweep(canvas: Canvas, from: Float, to: Float) {
        val a = pose(from)
        val b = pose(to)
        sweep.rewind()
        sweep.moveTo(handsX, handsY)
        sweep.lineTo(a.x + HOSEL_X * a.size, a.y + HOSEL_Y * a.size)
        sweep.lineTo(b.x + HOSEL_X * b.size, b.y + HOSEL_Y * b.size)
        sweep.close()
        shaftPaint.alpha = SHAFT_ALPHA
        canvas.drawPath(sweep, shaftPaint)
    }

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

    private companion object {
        /** 1/60 s exposure. */
        const val SHUTTER = 16f
        const val SAMPLES = 14
        const val SAMPLE_ALPHA = 0.14f
        const val SHAFT_ALPHA = 70
        const val HOSEL_X = 1.5f
        const val HOSEL_Y = -0.6f
    }
}
