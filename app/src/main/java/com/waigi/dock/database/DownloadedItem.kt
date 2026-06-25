package com.waigi.dock.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing one completed download stored in history.
 */
@Entity(tableName = "download_history")
data class DownloadedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnail: String? = null,

    /** Absolute path to the downloaded file on disk. */
    val filePath: String,

    /** File extension — "mp4", "mp3", "webm", etc. */
    val fileExtension: String = "",

    /** File size in bytes (-1 if unknown). */
    val fileSizeBytes: Long = -1L,

    /** Duration in seconds (-1 if unknown or audio). */
    val durationSeconds: Long = -1L,

    /** True if this was an audio-only download. */
    val isAudio: Boolean = false,

    /** Unix epoch millis when the download completed. */
    val downloadedAt: Long = System.currentTimeMillis(),

    /** The extractor name — "youtube", "tiktok", "twitter", etc. */
    val extractor: String? = null,
)
