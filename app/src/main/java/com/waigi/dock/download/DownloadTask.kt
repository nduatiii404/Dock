package com.waigi.dock.download

import java.util.UUID

/**
 * Represents a single download task with all its state.
 * Immutable — a new copy is produced for every state change.
 */
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "",
    val thumbnail: String? = null,
    val uploader: String? = null,
    val state: TaskState = TaskState.Queued,
    val progress: Float = 0f,            // 0..100
    val speedText: String = "",           // e.g. "3.2 MiB/s"
    val etaSeconds: Long = -1L,
    val outputPath: String? = null,
    val errorMessage: String? = null,
    val isAudio: Boolean = false,
    val formatId: String? = null,
    val totalSize: String = "",
) {
    /** Notification ID derived from the task ID so each task gets its own notification. */
    val notificationId: Int get() = id.hashCode()
}

sealed class TaskState {
    /** Waiting in queue before info is fetched */
    object Queued : TaskState()

    /** Fetching video metadata (title, formats, thumbnail) */
    object FetchingInfo : TaskState()

    /** Actively downloading */
    data class Downloading(
        val progress: Float = 0f,
        val speedText: String = "",
        val etaSeconds: Long = -1L,
    ) : TaskState()

    /** Download paused by the user */
    data class Paused(
        val progress: Float = 0f,
        val speedText: String = "",
        val etaSeconds: Long = -1L,
    ) : TaskState()

    /** Download finished successfully */
    data class Completed(val outputPath: String) : TaskState()

    /** Download cancelled by the user */
    object Cancelled : TaskState()

    /** An error occurred */
    data class Error(val message: String) : TaskState()

    val isTerminal: Boolean
        get() = this is Completed || this is Cancelled || this is Error

    val isBusy: Boolean
        get() = this is FetchingInfo || this is Downloading
}
