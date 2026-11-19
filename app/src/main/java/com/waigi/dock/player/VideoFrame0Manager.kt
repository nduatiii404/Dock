package com.waigi.dock.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoFrame0Manager {
    private const val MAX_DIMENSION = 1920

    // 24MB Memory Cache for instant swipe rendering
    private val memoryCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun getMemoryBitmap(key: String): Bitmap? = memoryCache.get(key)

    fun putMemoryBitmap(key: String, bitmap: Bitmap) {
        memoryCache.put(key, downscaleIfNeeded(bitmap))
    }

    private fun downscaleIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) return bitmap

        val scale = MAX_DIMENSION.toFloat() / maxOf(width, height).toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    suspend fun loadOrExtractFrame0(context: Context, videoUri: Uri, key: String): Bitmap? = withContext(Dispatchers.IO) {
        memoryCache.get(key)?.let { return@withContext it }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val bmp = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bmp != null) {
                val processed = downscaleIfNeeded(bmp)
                memoryCache.put(key, processed)
                return@withContext processed
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        null
    }
}
