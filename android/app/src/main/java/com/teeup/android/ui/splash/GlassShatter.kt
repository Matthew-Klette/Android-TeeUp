package com.teeup.android.ui.splash

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The broken screen. Every shard carries the frozen golf frame on its front
 * and its piece of the destination screen on its back. The pane cracks out
 * from the impact in a few frames, sits shattered for a beat, then the shards
 * flip over in a wave from the impact and land as the login screen. Settled
 * shards stop drawing, so the real view underneath takes over with no seam.
 */
internal class GlassShatter(
    pattern: CrackPattern,
    sceneFrame: Bitmap,
    revealFrame: Bitmap,
    private val impactX: Float,
    private val impactY: Float,
    coreRadius: Float,
    private val unit: Float,
) {
    private class Pane(
        val path: Path,
        val rank: Float,
        val distance: Float,
        val cx: Float,
        val cy: Float,
        val nudgeX: Float,
        val nudgeY: Float,
        val nudgeDegrees: Float,
        val tint: Int,
        val flipAt: Float,
        val flipAboutX: Boolean,
        val flipDirection: Float,
    )

    private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(sceneFrame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(revealFrame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backingPaint = Paint().apply { color = SplashScene.SCENE_BACKGROUND }
    private val edgePaint = strokePaint(1.2f * unit)
    private val glowPaint = strokePaint(4.5f * unit)
    private val hairlinePaint = strokePaint(1f * unit).apply { strokeCap = Paint.Cap.ROUND }

    private val camera = Camera().apply { setLocation(0f, 0f, CAMERA_DISTANCE) }
    private val matrix = Matrix()

    private val panes: List<Pane>
    private val crackReach: Float
    private val hairlines: List<FloatArray> = pattern.microCracks
    private val crush: List<FloatArray>

    init {
        val rnd = Random(5)
        panes = pattern.shards.map { shard ->
            val cx = shard.centroidX
            val cy = shard.centroidY
            // Loose pieces near the impact slip further than the ones still held by the frame.
            val slip = (1f - shard.distanceRank) * 3.5f * unit + 0.8f * unit
            Pane(
                path = shard.toPath(),
                rank = shard.distanceRank,
                distance = hypot(cx - impactX, cy - impactY),
                cx = cx,
                cy = cy,
                nudgeX = rnd.signed() * slip,
                nudgeY = rnd.signed() * slip,
                nudgeDegrees = rnd.signed() * if (shard.isCore) 1.5f else 0.25f,
                tint = when {
                    // Pulverised glass under the ball scatters light: milky, not clear.
                    shard.isCore -> Color.argb(18 + rnd.nextInt(34), 235, 240, 236)
                    rnd.nextBoolean() -> Color.argb(rnd.nextInt(22), 255, 255, 255)
                    else -> Color.argb(rnd.nextInt(40), 0, 0, 0)
                },
                flipAt = SplashTimeline.FLIP_START + SplashTimeline.FLIP_STAGGER * shard.distanceRank.pow(0.8f) + rnd.nextFloat() * SplashTimeline.FLIP_JITTER,
                flipAboutX = rnd.nextBoolean(),
                flipDirection = if (rnd.nextBoolean()) 1f else -1f,
            )
        }
        crackReach = panes.maxOfOrNull { it.distance } ?: 1f
        crush = List(CRUSH_MARKS) {
            val angle = rnd.nextFloat() * 2f * PI.toFloat()
            val r = coreRadius * (0.3f + rnd.nextFloat() * 0.85f)
            val length = (4f + rnd.nextFloat() * 12f) * unit
            val heading = rnd.nextFloat() * 2f * PI.toFloat()
            val x = impactX + r * cos(angle)
            val y = impactY + r * sin(angle)
            floatArrayOf(x, y, x + length * cos(heading), y + length * sin(heading))
        }
    }

    /**
     * [behindCracks] draws between the shard faces and the crack lines, which is
     * where the ball sits while it is still pressed against the glass.
     */
    fun draw(canvas: Canvas, t: Float, behindCracks: (Canvas) -> Unit) {
        val since = t - SplashTimeline.GLASS_HIT
        val front = crackReach * Ease.outCubic(SplashTimeline.progress(since, 0f, SplashTimeline.CRACK_SPREAD))

        // Until the flip wave starts, crack gaps must show darkness, not the login screen underneath.
        val backing = 1f - SplashTimeline.progress(t, SplashTimeline.FLIP_START, SplashTimeline.FLIP_START + BACKING_FADE)
        if (backing > 0f) {
            backingPaint.alpha = (255 * backing).toInt()
            canvas.drawPaint(backingPaint)
        }

        canvas.save()
        // The hit flexes the pane toward the viewer; scaling up (never down) keeps the edges covered.
        val flex = 1f + 0.02f * exp(-since / 90f)
        canvas.scale(flex, flex, impactX, impactY)

        panes.forEach { pane ->
            val flipT = t - pane.flipAt
            when {
                flipT < 0f -> withCrackedPose(canvas, pane, pane.distance <= front) {
                    canvas.drawPath(pane.path, frontPaint)
                    if (pane.distance <= front) {
                        tintPaint.color = pane.tint
                        canvas.drawPath(pane.path, tintPaint)
                    }
                }
                flipT >= SplashTimeline.FLIP_DURATION && flipT < SplashTimeline.FLIP_DURATION + SplashTimeline.EDGE_FADE ->
                    drawSettled(canvas, pane, (flipT - SplashTimeline.FLIP_DURATION) / SplashTimeline.EDGE_FADE)
            }
        }
        behindCracks(canvas)
        panes.forEach { pane ->
            if (t < pane.flipAt && pane.distance <= front) {
                withCrackedPose(canvas, pane, cracked = true) { drawEdges(canvas, pane.path, edgeStrength(pane)) }
            }
        }
        drawRimDamage(canvas, t)
        // Last, so a shard lifting off the pane is never painted over by a flat neighbour.
        panes.forEach { pane ->
            val flipT = t - pane.flipAt
            if (flipT >= 0f && flipT < SplashTimeline.FLIP_DURATION) {
                drawFlipping(canvas, pane, flipT / SplashTimeline.FLIP_DURATION)
            }
        }
        canvas.restore()
    }

    private inline fun withCrackedPose(canvas: Canvas, pane: Pane, cracked: Boolean, block: () -> Unit) {
        canvas.save()
        if (cracked) {
            canvas.translate(pane.nudgeX, pane.nudgeY)
            canvas.rotate(pane.nudgeDegrees, pane.cx, pane.cy)
        }
        block()
        canvas.restore()
    }

    private fun drawFlipping(canvas: Canvas, pane: Pane, p: Float) {
        val angle = 180f * Ease.outBack(p.pow(1.3f), 0.6f)
        val showsBack = angle > 90f
        // Lifts off and is back down by ~2/3 through, so the tail is only the snap into place.
        val lift = sin(PI.toFloat() * minOf(1f, p * 1.6f))
        val towardX = (pane.cx - impactX) / max(pane.distance, 1f)
        val towardY = (pane.cy - impactY) / max(pane.distance, 1f)

        camera.save()
        val turn = (if (showsBack) angle - 180f else angle) * pane.flipDirection
        if (pane.flipAboutX) camera.rotateX(turn) else camera.rotateY(turn)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-pane.cx, -pane.cy)
        matrix.postTranslate(pane.cx, pane.cy)
        val pop = 1f + 0.07f * lift
        matrix.postScale(pop, pop, pane.cx, pane.cy)

        canvas.save()
        val settle = 1f - p
        canvas.translate(pane.nudgeX * settle + towardX * 16f * unit * lift, pane.nudgeY * settle + towardY * 16f * unit * lift)
        canvas.concat(matrix)
        canvas.drawPath(pane.path, if (showsBack) backPaint else frontPaint)
        // Only close to edge-on does a pane fall into shadow; just off it, it catches a glint.
        val edgeOn = sin(Math.toRadians(angle.toDouble())).toFloat().coerceIn(0f, 1f).pow(3)
        tintPaint.color = Color.argb((110 * edgeOn).toInt(), 0, 0, 0)
        canvas.drawPath(pane.path, tintPaint)
        val glint = max(0f, 1f - abs(angle - 50f) / 30f)
        if (glint > 0f) {
            tintPaint.color = Color.argb((60 * glint).toInt(), 255, 255, 255)
            canvas.drawPath(pane.path, tintPaint)
        }
        drawEdges(canvas, pane.path, edgeStrength(pane) * (1f - 0.5f * p))
        canvas.restore()
    }

    private fun drawSettled(canvas: Canvas, pane: Pane, fade: Float) {
        canvas.drawPath(pane.path, backPaint)
        drawEdges(canvas, pane.path, edgeStrength(pane) * 0.4f * (1f - fade))
    }

    /** Hairline cracks and crushed flecks around the impact, gone once the flip wave starts. */
    private fun drawRimDamage(canvas: Canvas, t: Float) {
        val alpha = 1f - SplashTimeline.progress(t, SplashTimeline.FLIP_START, SplashTimeline.FLIP_START + 120f)
        if (alpha <= 0f) return
        hairlinePaint.alpha = (150 * alpha).toInt()
        hairlines.forEach { c ->
            canvas.drawLine(c[0], c[1], c[2], c[3], hairlinePaint)
            canvas.drawLine(c[2], c[3], c[4], c[5], hairlinePaint)
        }
        hairlinePaint.alpha = (110 * alpha).toInt()
        crush.forEach { c -> canvas.drawLine(c[0], c[1], c[2], c[3], hairlinePaint) }
    }

    private fun drawEdges(canvas: Canvas, path: Path, strength: Float) {
        if (strength <= 0f) return
        glowPaint.alpha = (34 * strength).toInt()
        canvas.drawPath(path, glowPaint)
        edgePaint.alpha = (190 * strength).toInt()
        canvas.drawPath(path, edgePaint)
    }

    /** Cracks read brightest near the impact, where the glass is most shattered. */
    private fun edgeStrength(pane: Pane): Float = 0.35f + 0.55f * (1f - pane.rank)

    private fun strokePaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = EDGE_COLOR
        strokeWidth = width
    }

    private fun ShardShape.toPath(): Path = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        close()
    }

    private fun Random.signed(): Float = nextFloat() * 2f - 1f

    private companion object {
        const val CAMERA_DISTANCE = -18f
        const val CRUSH_MARKS = 110
        const val BACKING_FADE = 200f
        val EDGE_COLOR = Color.rgb(0xE6, 0xEE, 0xE8)
    }
}
