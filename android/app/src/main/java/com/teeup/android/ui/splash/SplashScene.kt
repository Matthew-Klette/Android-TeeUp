package com.teeup.android.ui.splash

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.teeup.android.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * The golf half of the splash: the dark spot-lit stage from the TeeUp logo,
 * the tee rising out of the turf, the ball dropping onto it, the strike, and
 * the ball's flight at the lens. Ball, tee and grass are the real logo art
 * (res/drawable-nodpi/splash_*), not redraws. Pure function of time, so
 * [draw] at [SplashTimeline.GLASS_HIT] reproduces the exact frame the glass
 * breaks over.
 */
internal class SplashScene(resources: Resources, private val width: Float, private val height: Float) {
    private val ball = decode(resources, R.drawable.splash_ball)
    private val tee = decode(resources, R.drawable.splash_tee)
    private val backdrop = decode(resources, R.drawable.splash_backdrop)
    private val turf = decode(resources, R.drawable.splash_turf)

    private val unit = width / REFERENCE_WIDTH
    val ballRadius = width * BALL_WIDTH_FRACTION
    private val artScale = ballRadius / BALL_SOURCE_RADIUS

    private val restX = width / 2f
    // Placed so the tee's point enters the turf at GROUND_Y of the screen.
    private val restY = height * GROUND_Y - (TEE_SOURCE_TOP - BALL_SOURCE_Y + TEE_GROUND_LINE) * artScale
    private val teeLeft = restX + (TEE_SOURCE_LEFT - BALL_SOURCE_X) * artScale
    private val teeTop = restY + (TEE_SOURCE_TOP - BALL_SOURCE_Y) * artScale
    private val groundY = teeTop + TEE_GROUND_LINE * artScale

    val impactX = width * IMPACT_X
    val impactY = height * IMPACT_Y
    private val ballRadiusAtGlass = ballRadius * PUSH_IN * GLASS_SCALE
    val coreRadius = ballRadiusAtGlass * CORE_FRACTION

    private val teeBreak = TeeBreak(tee, teeLeft, teeTop, artScale, unit)
    private val club = ClubSwing(restX, restY + ballRadius * 0.55f, ballRadius, width, height)

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val plainPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    /** The night tee-box photo, scaled to cover and shifted so its lit tuft sits under the tee. */
    private val backdropBounds = RectF().apply {
        val scale = maxOf(width / backdrop.width, height / backdrop.height) * BACKDROP_OVERSCAN
        val drawnW = backdrop.width * scale
        val drawnH = backdrop.height * scale
        val top = (groundY - BACKDROP_TUFT * drawnH).coerceIn(height - drawnH, 0f)
        set((width - drawnW) / 2f, top, (width + drawnW) / 2f, top + drawnH)
    }
    private val vignettePaint = Paint().apply {
        shader = RadialGradient(
            width / 2f, height * 0.42f, maxOf(width, height) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(0x90, 0, 0, 0)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    /** Everything up to the glass. [includeFlightBall] is false for the frozen glass frame. */
    fun draw(canvas: Canvas, t: Float, includeFlightBall: Boolean) {
        canvas.save()
        val zoom = 1f + (PUSH_IN - 1f) * Ease.inOutSine(SplashTimeline.progress(t, SplashTimeline.PUSH_IN_START, SplashTimeline.IMPACT))
        canvas.scale(zoom, zoom, restX, restY)
        applyStrikeShake(canvas, t)
        canvas.drawBitmap(backdrop, null, backdropBounds, plainPaint)

        val struck = t >= SplashTimeline.IMPACT
        if (!struck) club.draw(canvas, t)
        if (struck) {
            teeBreak.drawStump(canvas)
        } else {
            canvas.save()
            canvas.clipRect(0f, 0f, width, groundY + 8f * unit)
            teeBreak.drawWhole(canvas, teeRise(t))
            canvas.restore()
        }
        // Blades from the same photo, keyed out and laid back over the tee's base.
        canvas.drawBitmap(turf, null, backdropBounds, plainPaint)
        if (struck) {
            club.draw(canvas, t)
            teeBreak.drawDebris(canvas, t - SplashTimeline.IMPACT)
            teeBreak.drawCap(canvas, t - SplashTimeline.IMPACT)
        } else {
            drawRestingBall(canvas, t)
        }
        canvas.restore()

        canvas.drawRect(0f, 0f, width, height, vignettePaint)
        if (struck && includeFlightBall && t < SplashTimeline.GLASS_HIT) drawFlight(canvas, t)
    }

    /** The ball after it hits the glass: bouncing back off it into the dark, seen through the cracks. */
    fun drawBallRebound(canvas: Canvas, t: Float) {
        if (t < SplashTimeline.GLASS_HIT || t >= SplashTimeline.BALL_REBOUND) return
        val v = SplashTimeline.progress(t, SplashTimeline.GLASS_HIT, SplashTimeline.BALL_REBOUND)
        val radius = ballRadiusAtGlass * (1f - 0.55f * Ease.outCubic(v))
        ballPaint.alpha = (255 * (1f - SplashTimeline.progress(v, 0.45f, 1f))).toInt()
        drawBall(canvas, impactX, impactY + ballRadiusAtGlass * 0.3f * v * v, radius, FLIGHT_ROLL * (1f + 0.3f * v), 1f, 1f)
        ballPaint.alpha = 255
    }

    /** Starts fully below the turf, so it is pushed up out of the ground rather than appearing on it. */
    private fun teeRise(t: Float): Float =
        (TEE_GROUND_LINE + 12f) * artScale * (1f - Ease.outCubic(SplashTimeline.progress(t, 0f, SplashTimeline.TEE_RISE_END)))

    private fun drawRestingBall(canvas: Canvas, t: Float) {
        if (t < SplashTimeline.BALL_DROP_START) return
        val startY = -ballRadius * 1.3f
        fun yAt(time: Float): Float {
            if (time < SplashTimeline.BALL_LAND) {
                val q = SplashTimeline.progress(time, SplashTimeline.BALL_DROP_START, SplashTimeline.BALL_LAND)
                return startY + (restY - startY) * Ease.inQuad(q)
            }
            val b = SplashTimeline.progress(time, SplashTimeline.BALL_LAND, SplashTimeline.BALL_SETTLE)
            return restY - ballRadius * BOUNCE * sin(PI.toFloat() * b) * (1f - 0.3f * b)
        }
        if (t < SplashTimeline.BALL_LAND) {
            forEachBlurSample(t, SplashTimeline.BALL_DROP_START) { sample ->
                drawBall(canvas, restX, yAt(sample), ballRadius, 0f, 1f, 1f)
            }
        }
        drawBall(canvas, restX, yAt(t), ballRadius, 0f, 1f, 1f)
    }

    private fun drawFlight(canvas: Canvas, t: Float) {
        forEachBlurSample(t, SplashTimeline.IMPACT) { sample -> drawFlightAt(canvas, sample) }
        drawFlightAt(canvas, t)
    }

    /** Faint earlier copies, close enough together to read as shutter blur rather than a trail. */
    private inline fun forEachBlurSample(t: Float, earliest: Float, draw: (Float) -> Unit) {
        for (k in BLUR_SAMPLES downTo 1) {
            val sample = t - k * BLUR_SPACING
            if (sample < earliest) continue
            ballPaint.alpha = (BLUR_ALPHA * (1f - k / (BLUR_SAMPLES + 1f))).toInt()
            draw(sample)
        }
        ballPaint.alpha = 255
    }

    private fun drawFlightAt(canvas: Canvas, t: Float) {
        val u = SplashTimeline.progress(t, SplashTimeline.IMPACT, SplashTimeline.GLASS_HIT)
        // Perspective: on-screen size ∝ 1/distance, so it grows slowly, then rushes the lens.
        val depth = 1f - 1f / GLASS_SCALE
        val grow = 1f / (1f - depth * u.pow(1.2f))
        val along = (grow - 1f) / (GLASS_SCALE - 1f)
        val x = restX + (impactX - restX) * along
        val y = restY + (impactY - restY) * along
        // The club face squeezes the ball for a frame or two before it springs off.
        val squeeze = 0.06f * (1f - SplashTimeline.progress(t, SplashTimeline.IMPACT, SplashTimeline.IMPACT + 30f))
        drawBall(canvas, x, y, ballRadius * PUSH_IN * grow, FLIGHT_ROLL * u, 1f + squeeze, 1f - squeeze)
    }

    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, radius: Float, roll: Float, sx: Float, sy: Float) {
        val s = radius / BALL_SOURCE_RADIUS
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(roll)
        canvas.scale(s * sx, s * sy)
        canvas.drawBitmap(ball, -ball.width / 2f, -ball.height / 2f, ballPaint)
        canvas.restore()
    }

    private fun applyStrikeShake(canvas: Canvas, t: Float) {
        val since = t - SplashTimeline.IMPACT
        if (since < 0f || since > SHAKE_LENGTH) return
        val amplitude = SHAKE_AMPLITUDE * unit * exp(-since / 45f)
        canvas.translate(amplitude * sin(since * 0.09f), amplitude * cos(since * 0.13f))
    }

    private fun decode(resources: Resources, id: Int): Bitmap =
        BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false })
            ?: error("splash art $id failed to decode")

    companion object {
        val SCENE_BACKGROUND = Color.rgb(0x07, 0x08, 0x07)

        private const val REFERENCE_WIDTH = 1080f
        private const val BALL_WIDTH_FRACTION = 0.165f
        private const val GROUND_Y = 0.73f
        private const val BACKDROP_OVERSCAN = 1.06f
        private const val BACKDROP_TUFT = 0.716f
        private const val IMPACT_X = 0.53f
        private const val IMPACT_Y = 0.34f
        private const val PUSH_IN = 1.035f
        private const val GLASS_SCALE = 2.2f
        private const val CORE_FRACTION = 0.42f
        private const val BOUNCE = 0.07f
        private const val FLIGHT_ROLL = 35f
        private const val SHAKE_LENGTH = 140f
        private const val SHAKE_AMPLITUDE = 7f

        // Where the art sits in the 1254 px logo it was cut from, so ball and tee keep their original fit.
        private const val BALL_SOURCE_RADIUS = 163.5f
        private const val BALL_SOURCE_X = 627f
        private const val BALL_SOURCE_Y = 434f
        private const val TEE_SOURCE_LEFT = 575f
        private const val TEE_SOURCE_TOP = 586f
        private const val TEE_SOURCE_HEIGHT = 552f
        private const val TEE_GROUND_LINE = 478f
        private const val BLUR_SAMPLES = 8
        private const val BLUR_SPACING = 2f
        private const val BLUR_ALPHA = 46
    }
}
