package com.waigi.dock.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.waigi.dock.download.Downloader
import com.waigi.dock.download.TaskState
import com.waigi.dock.util.DownloadPreferences
import com.waigi.dock.util.DownloadUtil
import com.waigi.dock.util.NotificationUtil
import com.waigi.dock.util.PreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Foreground service that keeps downloads alive when the user leaves the app.
 *
 * How it works:
 * 1. Started by [startDownload] companion function when a new URL is submitted.
 * 2. Observes [Downloader.tasks] to update per-task progress notifications.
 * 3. Stops itself automatically when no downloads are active.
 *
 * Intents it handles:
 * - [ACTION_DOWNLOAD] — start a new download (URL in extras)
 * - [ACTION_CANCEL]   — cancel a task (task ID in extras)
 * - [ACTION_STOP]     — cancel all and stop the service
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"

        const val ACTION_DOWNLOAD = "com.waigi.dock.DOWNLOAD"
        const val ACTION_CANCEL   = "com.waigi.dock.CANCEL"
        const val ACTION_STOP     = "com.waigi.dock.STOP"
        const val ACTION_FETCH_INFO = "com.waigi.dock.FETCH_INFO"

        const val EXTRA_URL       = "extra_url"
        const val EXTRA_TASK_ID   = "extra_task_id"
        const val EXTRA_IS_AUDIO  = "extra_is_audio"
        const val EXTRA_VIDEO_QUALITY = "extra_video_quality"
        const val EXTRA_WRITE_SUBTITLE = "extra_write_subtitle"
        const val EXTRA_EMBED_SUBTITLE = "extra_embed_subtitle"
        const val EXTRA_SUBTITLE_LANG = "extra_subtitle_lang"

        /** Convenience: start a download from anywhere in the app. */
        fun startDownload(
            context: Context,
            url: String,
            preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_IS_AUDIO, preferences.extractAudio)
                putExtra(EXTRA_VIDEO_QUALITY, preferences.videoQuality)
                putExtra(EXTRA_WRITE_SUBTITLE, preferences.writeSubtitle)
                putExtra(EXTRA_EMBED_SUBTITLE, preferences.embedSubtitle)
                putExtra(EXTRA_SUBTITLE_LANG, preferences.subtitleLanguage)
            }
            context.startForegroundService(intent)
        }

        /** Convenience: cancel a specific task. */
        fun cancelTask(context: Context, taskId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }

        /** Start a background fetch of video info (share-sheet flow). */
        fun startFetchInfo(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_FETCH_INFO
                putExtra(EXTRA_URL, url)
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    private var hasStartedActiveTask = false
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }


    override fun onCreate() {
        super.onCreate()
        NotificationUtil.createChannels(this)

        // Start as a foreground service immediately
        startForeground(
            NotificationUtil.FOREGROUND_NOTIFICATION_ID,
            NotificationUtil.buildForegroundNotification(this, 0),
        )

        // Observe all task state changes and update notifications
        Downloader.tasks
            .onEach { taskMap -> onTasksUpdated(taskMap) }
            .launchIn(serviceScope)

        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
                val videoQuality = intent.getIntExtra(EXTRA_VIDEO_QUALITY, 0)
                val writeSubtitle = intent.getBooleanExtra(EXTRA_WRITE_SUBTITLE, false)
                val embedSubtitle = intent.getBooleanExtra(EXTRA_EMBED_SUBTITLE, false)
                val subtitleLang = intent.getStringExtra(EXTRA_SUBTITLE_LANG) ?: "en"

                val prefs = DownloadPreferences.createFromPreferences().copy(
                    extractAudio = isAudio,
                    videoQuality = videoQuality,
                    writeSubtitle = writeSubtitle,
                    embedSubtitle = embedSubtitle,
                    subtitleLanguage = subtitleLang
                )
                Log.d(TAG, "Starting download: $url")
                Downloader.enqueue(url, prefs)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                Log.d(TAG, "Cancelling task: $taskId")
                Downloader.cancel(taskId)
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop all requested")
                stopSelf()
            }
            ACTION_FETCH_INFO -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                Log.d(TAG, "Fetching info for: $url")
                handleFetchInfo(url)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleFetchInfo(url: String) {
        // Show indeterminate fetching notification
        val fetchingNotif = NotificationUtil.buildFetchingInfoNotification(this, url)
        notificationManager.notify(NotificationUtil.FETCH_INFO_NOTIFICATION_ID, fetchingNotif)

        serviceScope.launch {
            val result = DownloadUtil.fetchVideoInfo(url)
            // Always cancel the fetching notification when done
            notificationManager.cancel(NotificationUtil.FETCH_INFO_NOTIFICATION_ID)

            result.fold(
                onSuccess = { info ->
                    // Serialize VideoInfo to JSON and cache in MMKV
                    val jsonStr = json.encodeToString(info)
                    PreferenceUtil.storePendingVideoInfo(url, jsonStr)
                    // Post tappable "ready" notification
                    val readyNotif = NotificationUtil.buildFetchReadyNotification(this@DownloadService, info.title)
                    notificationManager.notify(NotificationUtil.FETCH_READY_NOTIFICATION_ID, readyNotif)
                    Log.d(TAG, "Fetch info succeeded: ${info.title}")
                },
                onFailure = { err ->
                    val msg = err.message?.lines()
                        ?.filter { !it.contains("WARNING:", ignoreCase = true) }
                        ?.joinToString(" ")
                        ?.trim()
                        ?.take(120)
                        ?: "Failed to fetch video info"
                    val errorNotif = NotificationUtil.buildFetchErrorNotification(this@DownloadService, msg)
                    notificationManager.notify(NotificationUtil.FETCH_ERROR_NOTIFICATION_ID, errorNotif)
                    Log.e(TAG, "Fetch info failed: $msg")
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@DownloadService, msg, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }


    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }


    // ── Notification management ───────────────────────────────────────────────

    private fun onTasksUpdated(taskMap: Map<String, com.waigi.dock.download.DownloadTask>) {
        val activeTasks = taskMap.values.filter { it.state.isBusy }
        val activeCount = activeTasks.size

        if (activeCount > 0) {
            hasStartedActiveTask = true
        }

        // Update the sticky foreground notification
        val foregroundNotif = NotificationUtil.buildForegroundNotification(this, activeCount)
        notificationManager.notify(NotificationUtil.FOREGROUND_NOTIFICATION_ID, foregroundNotif)

        val notificationsEnabled = com.waigi.dock.util.PreferenceUtil.notificationsEnabled

        // Update per-task progress notifications
        activeTasks.forEach { task ->
            if (notificationsEnabled) {
                val state = task.state
                if (state is TaskState.Downloading) {
                    val notif = NotificationUtil.buildProgressNotification(
                        context = this,
                        taskId = task.id,
                        title = task.title,
                        progress = state.progress,
                        speedText = state.speedText,
                        etaSeconds = state.etaSeconds,
                        totalSize = task.totalSize,
                    )
                    notificationManager.notify(task.notificationId, notif)
                } else if (state is TaskState.FetchingInfo) {
                    val notif = NotificationUtil.buildProgressNotification(
                        context = this,
                        taskId = task.id,
                        title = task.title.ifEmpty { task.url },
                        progress = 0f,
                        speedText = "Fetching info…",
                        etaSeconds = -1,
                    )
                    notificationManager.notify(task.notificationId, notif)
                }
            } else {
                notificationManager.cancel(task.notificationId)
            }
        }

        // Post one-shot complete/error notifications for newly finished tasks
        taskMap.values.forEach { task ->
            when (val state = task.state) {
                is TaskState.Completed -> {
                    notificationManager.cancel(task.notificationId)
                    if (notificationsEnabled) {
                        val notif = NotificationUtil.buildCompleteNotification(this, task.title)
                        notificationManager.notify(task.notificationId + 100_000, notif)
                    }
                }
                is TaskState.Error -> {
                    notificationManager.cancel(task.notificationId)
                    if (notificationsEnabled) {
                        val notif = NotificationUtil.buildErrorNotification(
                            this, task.title, state.message
                        )
                        notificationManager.notify(task.notificationId + 200_000, notif)
                    }
                }
                is TaskState.Cancelled -> {
                    notificationManager.cancel(task.notificationId)
                }
                is TaskState.Paused -> {
                    notificationManager.cancel(task.notificationId)
                }
                else -> Unit
            }
        }

        // Stop the service when nothing is running
        if (hasStartedActiveTask && activeCount == 0 && taskMap.values.all { it.state.isTerminal }) {
            Log.d(TAG, "No active tasks — stopping service")
            stopSelf()
        }
    }
}
