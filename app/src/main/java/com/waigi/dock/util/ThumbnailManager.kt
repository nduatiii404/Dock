package com.waigi.dock.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.waigi.dock.database.DownloadedItem
import com.waigi.dock.repository.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Manages persistent storage and extraction of video and audio thumbnails.
 * Thumbnails are saved in internal app storage (filesDir/thumbnails) so they
 * are never purged by OS cache cleaners or lost to expired CDN URLs.
 */
object ThumbnailManager {

    private const val TAG = "ThumbnailManager"
    private const val THUMBNAILS_DIR = "thumbnails"
    private const val MAX_THUMB_DIMENSION = 512

    /**
     * Get or create the dedicated internal directory for persistent thumbnails.
     */
    fun getThumbnailDir(context: Context): File {
        val dir = File(context.filesDir, THUMBNAILS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the persistent thumbnail file for a given key.
     */
    fun getThumbnailFile(context: Context, key: String): File {
        val hash = hashKey(key)
        return File(getThumbnailDir(context), "thumb_$hash.jpg")
    }

    /**
     * Downloads a remote thumbnail and saves it persistently to internal storage.
     * Returns the absolute path of the saved file, or null if download failed.
     */
    suspend fun persistRemoteThumbnail(
        context: Context,
        remoteUrl: String,
        key: String,
    ): String? = withContext(Dispatchers.IO) {
        if (remoteUrl.isBlank() || !remoteUrl.startsWith("http", ignoreCase = true)) {
            return@withContext null
        }

        val targetFile = getThumbnailFile(context, key)
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile.absolutePath
        }

        val tempFile = File(getThumbnailDir(context), "temp_${System.currentTimeMillis()}_${targetFile.name}")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(remoteUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (tempFile.renameTo(targetFile)) {
                        return@withContext targetFile.absolutePath
                    } else {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                        return@withContext targetFile.absolutePath
                    }
                }
            } else {
                Log.w(TAG, "Failed to download remote thumbnail: HTTP ${connection.responseCode} for $remoteUrl")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error downloading thumbnail for $remoteUrl: ${e.message}")
        } finally {
            connection?.disconnect()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
        null
    }

    /**
     * Extracts thumbnail from local media file (frame from video or embedded picture from audio)
     * and saves it to internal storage.
     */
    suspend fun extractAndSaveThumbnail(
        context: Context,
        filePath: String,
        isAudio: Boolean,
        key: String,
    ): String? = withContext(Dispatchers.IO) {
        val mediaFile = File(filePath)
        if (!mediaFile.exists() || mediaFile.length() == 0L) {
            return@withContext null
        }

        val targetFile = getThumbnailFile(context, key)
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile.absolutePath
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.fromFile(mediaFile))

            // 1. If audio or has embedded picture (ID3 album artwork)
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size)
                if (bitmap != null) {
                    val saved = saveBitmapToFile(bitmap, targetFile)
                    bitmap.recycle()
                    if (saved) return@withContext targetFile.absolutePath
                }
            }

            // 2. If video (or audio without embedded picture), retrieve video frame
            if (!isAudio) {
                // Try 1s frame first for a more representative frame, fallback to 0s
                var frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame == null) {
                    frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }

                if (frame != null) {
                    val scaled = downscaleBitmap(frame)
                    val saved = saveBitmapToFile(scaled, targetFile)
                    if (scaled != frame) {
                        frame.recycle()
                    }
                    scaled.recycle()
                    if (saved) return@withContext targetFile.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting thumbnail from $filePath: ${e.message}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
        null
    }

    /**
     * Persist thumbnail for a download. Attempts remote download first;
     * falls back to local video/audio frame extraction.
     */
    suspend fun persistThumbnail(
        context: Context,
        remoteUrl: String?,
        localFilePath: String?,
        isAudio: Boolean,
        key: String,
    ): String? = withContext(Dispatchers.IO) {
        // Step 1: try remote thumbnail if available
        if (!remoteUrl.isNullOrBlank()) {
            val savedPath = persistRemoteThumbnail(context, remoteUrl, key)
            if (savedPath != null) return@withContext savedPath
        }

        // Step 2: fallback to local file extraction
        if (!localFilePath.isNullOrBlank()) {
            val extractedPath = extractAndSaveThumbnail(context, localFilePath, isAudio, key)
            if (extractedPath != null) return@withContext extractedPath
        }

        null
    }

    /**
     * Checks if a thumbnail already exists locally for this item.
     */
    fun findLocalThumbnail(context: Context, item: DownloadedItem): File? {
        // 1. If item.thumbnail already points to an existing file
        if (!item.thumbnail.isNullOrBlank()) {
            val file = File(item.thumbnail)
            if (file.exists() && file.length() > 0) return file
        }

        // 2. Check by item filePath key
        val byFile = getThumbnailFile(context, item.filePath)
        if (byFile.exists() && byFile.length() > 0) return byFile

        // 3. Check by item url key
        val byUrl = getThumbnailFile(context, item.url)
        if (byUrl.exists() && byUrl.length() > 0) return byUrl

        // 4. Check by item ID if positive
        if (item.id > 0) {
            val byId = getThumbnailFile(context, "item_${item.id}")
            if (byId.exists() && byId.length() > 0) return byId
        }

        return null
    }

    /**
     * Self-heals a history item in background by extracting its thumbnail from disk
     * and updating the Room database.
     */
    fun selfHealThumbnail(
        context: Context,
        scope: CoroutineScope,
        item: DownloadedItem,
        historyRepository: HistoryRepository? = null,
        onHealed: ((String) -> Unit)? = null,
    ) {
        scope.launch(Dispatchers.IO) {
            val existingLocal = findLocalThumbnail(context, item)
            if (existingLocal != null) {
                if (item.thumbnail != existingLocal.absolutePath && item.id > 0) {
                    historyRepository?.updateThumbnail(item.id, existingLocal.absolutePath)
                }
                withContext(Dispatchers.Main) {
                    onHealed?.invoke(existingLocal.absolutePath)
                }
                return@launch
            }

            // Extract from local video/audio file
            val key = if (item.id > 0) "item_${item.id}" else item.filePath
            val extracted = extractAndSaveThumbnail(context, item.filePath, item.isAudio, key)
            if (extracted != null) {
                if (item.id > 0) {
                    historyRepository?.updateThumbnail(item.id, extracted)
                }
                withContext(Dispatchers.Main) {
                    onHealed?.invoke(extracted)
                }
            }
        }
    }

    private fun downscaleBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_THUMB_DIMENSION && height <= MAX_THUMB_DIMENSION) return bitmap

        val scale = MAX_THUMB_DIMENSION.toFloat() / maxOf(width, height).toFloat()
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        return scaled
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        val temp = File(file.parentFile, "temp_${System.currentTimeMillis()}_${file.name}")
        return try {
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (temp.exists() && temp.length() > 0) {
                if (temp.renameTo(file)) {
                    true
                } else {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                    true
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error saving bitmap to file ${file.absolutePath}: ${e.message}")
            if (temp.exists()) temp.delete()
            false
        }
    }

    private fun hashKey(key: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(key.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.hashCode().toString()
        }
    }
}
