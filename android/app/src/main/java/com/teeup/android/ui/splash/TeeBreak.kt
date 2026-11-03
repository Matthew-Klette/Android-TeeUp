package com.teeup.android.ui.splash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The tee snapping under the club: a jagged fracture through the upper shaft,
 * the stump left in the turf with raw wood showing under the black paint, the
 * cup spinning off toward the camera, and a spray of splinters and wood dust.
 * Everything is placed in the tee bitmap's own pixel space (113 × 390, cut
 * from the TeeUp logo) and posed as a pure function of time since impact.
 */
internal class TeeBreak(
    private val tee: Bitmap,
    private val left: Float,
    private val top: Float,
    private val scale: Float,
    private val unit: Float,
) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RAW_WOOD }
    private val fiberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = RAW_WOOD
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.3f
    }
    private val splinterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK_PAINT }
    private val wholeBounds = RectF()

    private val stumpPath = Path().apply {
        moveTo(FRACTURE[0], FRACTURE[1])
        for (i in 2 until FRACTURE.size step 2) lineTo(FRACTURE[i], FRACTURE[i + 1])
        lineTo(tee.width.toFloat(), tee.height.toFloat())
        lineTo(0f, tee.height.toFloat())
        close()
    }
    private val capPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(tee.width.toFloat(), 0f)
        for (i in FRACTURE.size - 2 downTo 0 step 2) lineTo(FRACTURE[i], FRACTURE[i + 1])
        close()
    }
    private val stumpWood = woodBand(BAND_DEPTH)
    private val capWood = woodBand(-BAND_DEPTH)

    private val breakX = left + SHAFT_CENTER * scale
    private val breakY = top + BREAK_Y * scale
    private val splinters = Random(23).let { rnd -> List(SPLINTER_COUNT) { Splinter.random(rnd, breakX, breakY, scale, unit) } }
    private val dust = Random(29).let { rnd -> List(DUST_COUNT) { Dust.random(rnd, breakX, breakY, scale, unit) } }

    /** The intact tee, [rise] px below its resting place while it pushes up out of the turf. */
    fun drawWhole(canvas: Canvas, rise: Float) {
        wholeBounds.set(left, top + rise, left + tee.width * scale, top + rise + tee.height * scale)
        canvas.drawBitmap(tee, null, wholeBounds, bitmapPaint)
    }

    fun drawStump(canvas: Canvas) {
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.clipPath(stumpPath)
        canvas.drawBitmap(tee, 0f, 0f, bitmapPaint)
        canvas.drawPath(stumpWood, woodPaint)
        canvas.restore()
        drawFibers(canvas, left, top, scale, STUMP_FIBERS)
    }

    /** Cup and upper shaft flying off, [since] ms after impact. */
    fun drawCap(canvas: Canvas, since: Float) {
        if (since > CAP_LIFE) return
        drawCapAt(canvas, (since - BLUR_LAG).coerceAtLeast(0f), GHOST_ALPHA)
        drawCapAt(canvas, since, 255)
    }

    fun drawDebris(canvas: Canvas, since: Float) {
        val seconds = since / 1000f
        dust.forEach { it.draw(canvas, seconds, splinterPaint) }
        splinters.forEach { splinter ->
            splinter.draw(canvas, (seconds - BLUR_LAG / 1000f).coerceAtLeast(0f), GHOST_ALPHA, splinterPaint, paintPaint)
            splinter.draw(canvas, seconds, 255, splinterPaint, paintPaint)
        }
    }

    private fun drawCapAt(canvas: Canvas, since: Float, alpha: Int) {
        val s = since / 1000f
        val pivotX = left + CAP_PIVOT_X * scale
        val pivotY = top + CAP_PIVOT_Y * scale
        canvas.save()
        canvas.translate(pivotX + CAP_VX * unit * s, pivotY + CAP_VY * unit * s + 0.5f * GRAVITY * unit * s * s)
        canvas.rotate(CAP_SPIN * s)
        val grow = scale * (1f + CAP_GROWTH * s)
        canvas.scale(grow, grow)
        canvas.translate(-CAP_PIVOT_X, -CAP_PIVOT_Y)
        canvas.clipPath(capPath)
        bitmapPaint.alpha = alpha
        woodPaint.alpha = alpha
        canvas.drawBitmap(tee, 0f, 0f, bitmapPaint)
        canvas.drawPath(capWood, woodPaint)
        bitmapPaint.alpha = 255
        woodPaint.alpha = 255
        canvas.restore()
    }

    private fun drawFibers(canvas: Canvas, x: Float, y: Float, s: Float, fibers: FloatArray) {
        fiberPaint.strokeWidth = 1.1f * s
        for (i in fibers.indices step 4) {
            canvas.drawLine(x + fibers[i] * s, y + fibers[i + 1] * s, x + fibers[i + 2] * s, y + fibers[i + 3] * s, fiberPaint)
        }
    }

    /** Thin strip of exposed wood following the fracture, [depth] px into the piece. */
    private fun woodBand(depth: Float) = Path().apply {
        val inner = (2 until FRACTURE.size - 2 step 2).toList()
        moveTo(FRACTURE[inner.first()], FRACTURE[inner.first() + 1])
        inner.drop(1).forEach { lineTo(FRACTURE[it], FRACTURE[it + 1]) }
        inner.reversed().forEach { lineTo(FRACTURE[it], FRACTURE[it + 1] + depth) }
        close()
    }

    private class Splinter(
        val shape: Path,
        val stripe: Path,
        val color: Int,
        val painted: Boolean,
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val angle: Float,
        val spin: Float,
        val growth: Float,
        val life: Float,
        val gravity: Float,
    ) {
        fun draw(canvas: Canvas, seconds: Float, alpha: Int, paint: Paint, paintStripe: Paint) {
            if (seconds <= 0f || seconds > life) return
            val fade = (1f - SplashTimeline.progress(seconds, life * 0.65f, life))
            canvas.save()
            canvas.translate(x + vx * seconds, y + vy * seconds + 0.5f * gravity * seconds * seconds)
            canvas.rotate(angle + spin * seconds)
            val grow = 1f + growth * seconds
            canvas.scale(grow, grow)
            paint.color = if (painted) BLACK_PAINT else color
            paint.alpha = (alpha * fade).toInt()
            canvas.drawPath(shape, paint)
            if (!painted) {
                paintStripe.alpha = (alpha * fade).toInt()
                canvas.drawPath(stripe, paintStripe)
            }
            canvas.restore()
        }

        companion object {
            fun random(rnd: Random, bx: Float, by: Float, scale: Float, unit: Float): Splinter {
                val length = (16f + rnd.nextFloat() * 40f) * scale
                val width = (2.2f + rnd.nextFloat() * 3.4f) * scale
                // Spray follows the club down and toward the lens: mostly down and sideways, a few up.
                val heading = (rnd.nextFloat() * 2f - 1f) * 0.9f * PI.toFloat()
                val speed = (600f + rnd.nextFloat() * 900f) * unit * (0.75f + 0.35f * cos(heading))
                return Splinter(
                    shape = Path().apply {
                        moveTo(-length / 2f, 0f)
                        lineTo(-length * 0.2f, -width / 2f)
                        lineTo(length * 0.3f, -width * 0.35f)
                        lineTo(length / 2f, -width * 0.05f)
                        lineTo(length * 0.45f, width * 0.15f)
                        lineTo(length * 0.1f, width / 2f)
                        lineTo(-length * 0.35f, width * 0.3f)
                        close()
                    },
                    stripe = Path().apply {
                        moveTo(-length * 0.2f, -width / 2f)
                        lineTo(length * 0.3f, -width * 0.35f)
                        lineTo(length * 0.32f, -width * 0.1f)
                        lineTo(-length * 0.25f, -width * 0.18f)
                        close()
                    },
                    color = WOOD_TONES[rnd.nextInt(WOOD_TONES.size)],
                    painted = rnd.nextFloat() < 0.3f,
                    x = bx + (rnd.nextFloat() * 2f - 1f) * 14f * scale,
                    y = by + (rnd.nextFloat() * 2f - 1f) * 6f * scale,
                    vx = sin(heading) * speed,
                    vy = cos(heading) * speed,
                    angle = rnd.nextFloat() * 360f,
                    spin = (rnd.nextFloat() * 2f - 1f) * 1500f,
                    growth = 0.6f + rnd.nextFloat() * 1.8f,
                    life = 0.45f + rnd.nextFloat() * 0.3f,
                    gravity = GRAVITY * unit,
                )
            }
        }
    }

    private class Dust(
        val x: Float,
        val y: Float,
        val vx: Float,
        val vy: Float,
        val radius: Float,
        val life: Float,
    ) {
        fun draw(canvas: Canvas, seconds: Float, paint: Paint) {
            if (seconds <= 0f || seconds > life) return
            // Fine dust brakes hard in air, so it travels on an exponential, not a straight line.
            val travel = (1f - exp(-seconds * DUST_DRAG)) / DUST_DRAG
            paint.color = RAW_WOOD
            paint.alpha = (200 * (1f - seconds / life)).toInt()
            canvas.drawCircle(x + vx * travel, y + vy * travel, radius, paint)
        }

        companion object {
            fun random(rnd: Random, bx: Float, by: Float, scale: Float, unit: Float): Dust {
                val heading = rnd.nextFloat() * 2f * PI.toFloat()
                val speed = (900f + rnd.nextFloat() * 1300f) * unit
                return Dust(
                    x = bx + (rnd.nextFloat() * 2f - 1f) * 10f * scale,
                    y = by,
                    vx = cos(heading) * speed,
                    vy = sin(heading) * speed,
                    radius = (0.8f + rnd.nextFloat() * 1.4f) * unit,
                    life = 0.25f + rnd.nextFloat() * 0.25f,
                )
            }
        }
    }

    companion object {
        /** Fracture across the shaft just under the cup's flare (x 40–72 is the shaft at this height). */
        private val FRACTURE = floatArrayOf(
            0f, 104f, 40f, 104f, 43f, 96f, 46f, 109f, 49f, 99f, 53f, 116f, 57f, 101f,
            61f, 107f, 64f, 93f, 67f, 105f, 70f, 99f, 72f, 104f, 113f, 104f,
        )
        private val STUMP_FIBERS = floatArrayOf(49f, 99f, 48f, 88f, 64f, 93f, 65f, 81f, 57f, 101f, 56f, 94f, 43f, 96f, 42f, 90f)
        private const val BREAK_Y = 104f
        private const val SHAFT_CENTER = 56f
        private const val BAND_DEPTH = 5f
        private const val CAP_PIVOT_X = 56f
        private const val CAP_PIVOT_Y = 50f
        private const val CAP_VX = 950f
        private const val CAP_VY = -420f
        private const val CAP_SPIN = -1400f
        private const val CAP_GROWTH = 1.8f
        private const val CAP_LIFE = 700f
        private const val GRAVITY = 2600f
        private const val BLUR_LAG = 7f
        private const val GHOST_ALPHA = 80
        private const val SPLINTER_COUNT = 30
        private const val DUST_COUNT = 34
        private const val DUST_DRAG = 7f

        private val RAW_WOOD = Color.rgb(0xCF, 0xB4, 0x86)
        private val BLACK_PAINT = Color.rgb(0x1A, 0x1B, 0x1C)
        private val WOOD_TONES = intArrayOf(
            Color.rgb(0xCD, 0xB0, 0x85), Color.rgb(0xB8, 0x97, 0x6A),
            Color.rgb(0xD9, 0xC2, 0x9A), Color.rgb(0xA8, 0x85, 0x59),
        )
    }
}
