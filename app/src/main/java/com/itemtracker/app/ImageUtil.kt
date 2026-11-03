package com.itemtracker.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/** Shrink item pictures and store them as base64 JPEG inside the data file */
object ImageUtil {
    private const val TAG = "ImageUtil"
    private const val MAX_SIDE = 320
    private const val JPEG_QUALITY = 85

    /**
     * Shrink a picture and encode it
     *
     * @param bytes the picture file contents
     * @return the base64 JPEG, or null when the bytes are not a picture
     */
    fun fromBytes(bytes: ByteArray): String? {
        // Read the size first so huge photos are decoded at a reduced sample size
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        }
        else {
            decoded
        }
        // JPEG has no transparency, so paint onto white to keep transparent PNGs from turning black
        val flattened = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        val output = ByteArrayOutputStream()
        flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Read, shrink and encode a picture the user picked
     *
     * @param context the [Context] to use
     * @param uri the picture URI
     * @return the base64 JPEG, or null when the picture cannot be read
     */
    fun fromUri(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }?.let(::fromBytes)
    }
    catch (e: IOException) {
        Log.w(TAG, "Failed to read picture $uri", e)
        null
    }
    catch (e: SecurityException) {
        Log.w(TAG, "No access to picture $uri", e)
        null
    }

    /**
     * Decode a stored picture for display
     *
     * @param base64 the base64 JPEG
     * @return the picture, or null when it cannot be decoded
     */
    fun toImageBitmap(base64: String): ImageBitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    catch (e: IllegalArgumentException) {
        Log.w(TAG, "Stored picture is not valid base64", e)
        null
    }
}
