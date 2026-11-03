package com.teeup.android.ui.splash

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.teeup.android.R
import java.io.IOException

/**
 * The intro's sound bed (res/raw/splash_sfx): breeze over the turf, the driver
 * strike with an open-air reverb, the tee snapping, the glass breaking and its
 * debris under the flip. It is one pre-mixed track timed to [SplashTimeline] at
 * [SplashTimeline.PACE], so it only has to start on the intro's first frame.
 *
 * Stays quiet when the phone is on silent/vibrate or the user is already
 * listening to something, since nobody expects an app launch to talk over that.
 */
internal class SplashSound(context: Context) {
    private val appContext = context.applicationContext
    private val tag = "SplashSound"
    private var player: MediaPlayer? = null

    fun start() {
        if (player != null || !shouldPlay()) return
        player = try {
            prepare()
        } catch (e: IOException) {
            // The intro still works silently; sound is a nice-to-have.
            Log.w(tag, "splash sound failed to load; playing silently", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(tag, "splash sound failed to start; playing silently", e)
            null
        }
    }

    /** Cuts the sound off, e.g. when the user leaves mid-intro. Safe to call more than once. */
    fun stop() {
        player?.let {
            player = null
            it.release()
        }
    }

    private fun prepare(): MediaPlayer {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            appContext.resources.openRawResourceFd(R.raw.splash_sfx).use { fd ->
                mediaPlayer.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            // ~85 KB on disk; preparing synchronously is a few ms and keeps the start on the beat.
            mediaPlayer.prepare()
            mediaPlayer.setOnCompletionListener { stop() }
            mediaPlayer.start()
            return mediaPlayer
        } catch (e: IOException) {
            mediaPlayer.release()
            throw e
        } catch (e: IllegalStateException) {
            mediaPlayer.release()
            throw e
        }
    }

    private fun shouldPlay(): Boolean {
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL && !audio.isMusicActive
    }
}

/*
References:

Mixkit (n.d.). Free sound effects: Arrow whoosh (#1491), Golf shot breaking the air (#2124),
Twig breaking (#2945), Glass break with hammer thud (#759), Cleaning up glass debris (#3084) and Breeze
through the trees (#2427). [online] Available at: <https://mixkit.co/free-sound-effects/>
[Accessed 23 Oct. 2026]. Used under the Mixkit Sound Effects Free License.
*/
