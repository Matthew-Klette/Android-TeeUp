package com.teeup.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Saves the user's profile photo to this device's private local storage only — no
 * server involvement, so other users never see it and it doesn't survive a reinstall.
 * Downsampled/compressed on save so it stays small on disk.
 */
object LocalProfilePhoto {
    private const val FILE_NAME = "profile_photo.jpg"
    private const val MAX_DIMENSION_PX = 512
    private const val JPEG_QUALITY = 85

    /** Reads, downsamples and re-encodes [imageUri] (camera or gallery), overwriting
     *  any previously saved photo. Blocks; call off the main thread. */
    fun save(context: Context, imageUri: Uri) {
        val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read the selected photo.")

        val bitmap = BitmapSampling.decodeSampled(bytes, MAX_DIMENSION_PX)
            ?: throw IllegalStateException("Could not decode the selected photo.")

        FileOutputStream(File(context.filesDir, FILE_NAME)).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        }
    }

    /** Null if no photo has been saved yet. Already small (downsampled at save time),
     *  so no further subsampling on the way back out. */
    fun loadOrNull(context: Context): Bitmap? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }
}
