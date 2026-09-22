package com.teeup.android.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Downsamples before the bitmap lands in memory, so a multi-megabyte phone photo
 * doesn't get decoded at full size for a small use (avatar display, photo upload).
 */
object BitmapSampling {
    fun decodeSampled(bytes: ByteArray, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
            bounds.outHeight / (sampleSize * 2) >= targetSizePx
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

/*
References:

Google (n.d.). Load large bitmaps efficiently. [online] Android Developers. Available at: <https://developer.android.com/topic/performance/graphics/load-bitmap> [Accessed 22 Sep. 2026].
*/
