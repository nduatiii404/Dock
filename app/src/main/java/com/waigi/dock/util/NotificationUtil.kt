package com.waigi.dock.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.waigi.dock.MainActivity
import com.waigi.dock.R
import com.waigi.dock.service.DownloadService

/**
 * Centralizes all notification creation for Dock.
 *
 * Channels:
 * - [CHANNEL_DOWNLOAD_PROGRESS] — persistent, low-priority progress notifications
 * - [CHANNEL_DOWNLOAD_COMPLETE] — high-priority one-shot completion alerts
 */
object NotificationUtil {

    const val CHANNEL_DOWNLOAD_PROGRESS = "dock_download_progress"
    const val CHANNEL_DOWNLOAD_COMPLETE = "dock_download_complete"

    const val FOREGROUND_NOTIFICATION_ID = 1

    /** Create all notification channels (required on Android 8+). */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val progressChannel = NotificationChannel(
            CHANNEL_DOWNLOAD_PROGRESS,
            "Download Progress",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress while files are downloading"
            setShowBadge(false)
        }

        val completeChannel = NotificationChannel(
            CHANNEL_DOWNLOAD_COMPLETE,
            "Download Complete",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifies when a download finishes or fails"
        }

        manager.createNotificationChannels(listOf(progressChannel, completeChannel))
    }

    /** Build the sticky foreground notification shown while the service is alive. */
    fun buildForegroundNotification(
        context: Context,
        activeCount: Int,
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val contentText = if (activeCount > 0) {
            "$activeCount download${if (activeCount > 1) "s" else ""} in progress"
        } else {
            "Download service running"
        }

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Dock")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Build a per-task progress notification. */
    fun buildProgressNotification(
        context: Context,
        taskId: String,
        title: String,
        progress: Float,
        speedText: String,
        etaSeconds: Long,
        totalSize: String = "",
    ): Notification {
        val cancelIntent = PendingIntent.getService(
            context,
            taskId.hashCode(),
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val deleteIntent = PendingIntent.getService(
            context,
            taskId.hashCode() + 1,
            Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_REPOST_NOTIFICATION
                putExtra(DownloadService.EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val progressInt = progress.toInt().coerceIn(0, 100)
        val etaText = if (etaSeconds > 0) formatEta(etaSeconds) else ""
        
        val sizeText = if (totalSize.isNotEmpty()) {
            val downloaded = com.waigi.dock.download.Downloader.calculateDownloadedSize(totalSize, progress)
            if (downloaded.isNotEmpty()) "$downloaded / $totalSize" else totalSize
        } else ""

        val subText = listOf(sizeText, speedText, etaText).filter { it.isNotEmpty() }.joinToString(" · ")

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifEmpty { "Downloading…" })
            .setContentText(subText.ifEmpty { "Starting…" })
            .setProgress(100, progressInt, progressInt == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Build a completion notification (success). */
    fun buildCompleteNotification(
        context: Context,
        title: String,
        requestCode: Int = 0,
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "history")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_COMPLETE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()
    }

    /** Build an error notification. */
    fun buildErrorNotification(
        context: Context,
        title: String,
        error: String,
        requestCode: Int = 0,
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "downloads")
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_COMPLETE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download failed")
            .setContentText(title.ifEmpty { error })
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun formatEta(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
