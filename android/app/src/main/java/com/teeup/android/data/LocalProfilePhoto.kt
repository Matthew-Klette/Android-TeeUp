package com.teeup.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Saves the user's profile photo to this device's private local storage only.
 * No server involvement, so other users never see it and it doesn't survive a reinstall.
 * Downsampled/compressed on save so it stays small on disk.
 *
 * Keyed by [uid] (the signed-in Firebase user id), so switching accounts on this
 * device never shows the previous account's photo.
 */
object LocalProfilePhoto {
    private const val MAX_DIMENSION_PX = 512
    private const val JPEG_QUALITY = 85

    private fun fileFor(context: Context, uid: String): File =
        File(context.filesDir, "profile_photo_$uid.jpg")

    /** Reads, downsamples and re-encodes [imageUri] (camera or gallery), overwriting
     *  any previously saved photo for this [uid]. Blocks; call off the main thread. */
    fun save(context: Context, uid: String, imageUri: Uri) {
        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read the selected photo.")

        val bitmap = BitmapSampling.decodeSampled(bytes, MAX_DIMENSION_PX)
            ?: throw IllegalStateException("Could not decode the selected photo.")

        FileOutputStream(fileFor(context, uid)).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        }
    }

    /** Null if this [uid] hasn't saved a photo yet. Already small (downsampled at
     *  save time), so no further subsampling on the way back out. */
    fun loadOrNull(context: Context, uid: String): Bitmap? {
        val file = fileFor(context, uid)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }
}
