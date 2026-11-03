package com.teeup.android.ui.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.view.doOnLayout

/**
 * Plays the splash intro over [play]'s reveal view: golf scene → strike →
 * ball through the screen → shards flip into the reveal. The reveal view must
 * sit directly underneath this one at the same size, since settled shards stop
 * drawing and let it show through. Swallows touches while it runs.
 */
class SplashShatterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Fired once, when the flip wave has turned the top of the screen over to the reveal. */
    var onRevealStarting: (() -> Unit)? = null

    /** Fired once, on the intro's first frame, so its sound can start in sync. Not fired if the intro is skipped. */
    var onPlaybackStarted: (() -> Unit)? = null

    /** Fired once, when the reveal is fully showing (or immediately if animations are off). */
    var onFinished: (() -> Unit)? = null

    private val tag = "SplashShatterView"
    private var scene: SplashScene? = null
    private var glass: GlassShatter? = null
    private var animator: ValueAnimator? = null
    private var time = 0f
    private var revealAnnounced = false
    private var warmedUp = false

    init {
        isClickable = true
    }

    fun play(reveal: View) {
        // The reveal is laid out before this view in the same pass (it sits below it), so
        // wait for the reveal, then post to land after this view's own layout.
        reveal.doOnLayout {
            post {
                if (width > 0 && height > 0) start(reveal) else finish()
            }
        }
    }

    private fun start(reveal: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            // Reduced motion / battery saver: skip the intro entirely.
            finish()
            return
        }
        try {
            build(reveal)
        } catch (e: RuntimeException) {
            // Missing/corrupt art must never strand the user on a black screen.
            Log.e(tag, "splash intro failed to build; skipping it", e)
            finish()
            return
        } catch (e: OutOfMemoryError) {
            // Two full-screen frames are ~20 MB; on a starved device, go straight to the app.
            Log.e(tag, "not enough memory for the splash intro; skipping it", e)
            finish()
            return
        }

        animator = ValueAnimator.ofFloat(0f, SplashTimeline.END).apply {
            // Time runs through the beat sheet unchanged; the animator just takes PACE times as long.
            duration = (SplashTimeline.END * SplashTimeline.PACE).toLong()
            // Lets the system launch window finish fading before the tee starts to rise.
            startDelay = START_DELAY
            interpolator = LinearInterpolator()
            addUpdateListener {
                time = it.animatedValue as Float
                onPlaybackStarted?.let { started ->
                    onPlaybackStarted = null
                    started()
                }
                if (!revealAnnounced && time >= REVEAL_REACHES_TOP) {
                    revealAnnounced = true
                    onRevealStarting?.invoke()
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
            start()
        }
    }

    private fun build(reveal: View) {
        val w = width.toFloat()
        val h = height.toFloat()
        val builtScene = SplashScene(resources, w, h)

        val sceneFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        builtScene.draw(Canvas(sceneFrame), SplashTimeline.GLASS_HIT, includeFlightBall = false)
        val revealFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        reveal.draw(Canvas(revealFrame))
        // Upload both textures now, not on the frame the glass breaks.
        sceneFrame.prepareToDraw()
        revealFrame.prepareToDraw()

        val pattern = CrackPattern.generate(w, h, builtScene.impactX, builtScene.impactY, builtScene.coreRadius)
        glass = GlassShatter(pattern, sceneFrame, revealFrame, builtScene.impactX, builtScene.impactY, builtScene.coreRadius, w / 1080f)
        scene = builtScene
    }

    private fun finish() {
        if (!revealAnnounced) {
            revealAnnounced = true
            onRevealStarting?.invoke()
        }
        onFinished?.invoke()
        onFinished = null
    }

    override fun onDraw(canvas: Canvas) {
        val scene = scene ?: return canvas.drawColor(SplashScene.SCENE_BACKGROUND)
        val glass = glass
        if (!warmedUp && glass != null) {
            warmedUp = true
            warmUp(canvas, glass)
        }
        if (time < SplashTimeline.GLASS_HIT || glass == null) {
            scene.draw(canvas, time, includeFlightBall = true)
        } else {
            glass.draw(canvas, time) { scene.drawBallRebound(it, time) }
        }
    }

    /**
     * Draws the glass once in each of its states on the first frame, under the
     * opaque scene, so the GPU builds its shaders and path caches before the
     * ball arrives instead of stalling the frame the screen breaks.
     */
    private fun warmUp(canvas: Canvas, glass: GlassShatter) {
        WARM_UP_TIMES.forEach { t -> glass.draw(canvas, t) {} }
    }

    private companion object {
        /** When the flip wave reaches the status bar, so the bar can turn light with it. */
        const val REVEAL_REACHES_TOP = SplashTimeline.FLIP_START + 400f
        const val START_DELAY = 200L
        val WARM_UP_TIMES = floatArrayOf(
            SplashTimeline.GLASS_HIT + SplashTimeline.CRACK_SPREAD,
            SplashTimeline.FLIP_START + SplashTimeline.FLIP_STAGGER / 2f,
            SplashTimeline.END - SplashTimeline.EDGE_FADE / 2f,
        )
    }

    override fun onDetachedFromWindow() {
        // Leaving mid-intro (back press, rotation) must not route from a dead activity.
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
