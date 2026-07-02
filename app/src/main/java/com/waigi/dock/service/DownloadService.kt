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
        const val ACTION_REPOST_NOTIFICATION = "com.waigi.dock.REPOST_NOTIFICATION"

        const val EXTRA_URL       = "extra_url"
        const val EXTRA_TASK_ID   = "extra_task_id"
        const val EXTRA_IS_AUDIO  = "extra_is_audio"
        const val EXTRA_VIDEO_QUALITY = "extra_video_quality"
        const val EXTRA_WRITE_SUBTITLE = "extra_write_subtitle"
        const val EXTRA_EMBED_SUBTITLE = "extra_embed_subtitle"
        const val EXTRA_SUBTITLE_LANG = "extra_subtitle_lang"
        const val EXTRA_EMBED_THUMBNAIL = "extra_embed_thumbnail"

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
                putExtra(EXTRA_EMBED_THUMBNAIL, preferences.embedThumbnail)
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
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
    }
    private var hasStartedActiveTask = false
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val processedTasks = mutableSetOf<String>()
    private val startedToastsShown = mutableSetOf<String>()
    private var isFirstEmission = true


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
                val embedThumbnail = intent.getBooleanExtra(EXTRA_EMBED_THUMBNAIL, true)

                val prefs = DownloadPreferences.createFromPreferences().copy(
                    extractAudio = isAudio,
                    videoQuality = videoQuality,
                    writeSubtitle = writeSubtitle,
                    embedSubtitle = embedSubtitle,
                    subtitleLanguage = subtitleLang,
                    embedThumbnail = embedThumbnail
                )
                Log.d(TAG, "Starting download: $url")
                Downloader.enqueue(url, prefs)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                Log.d(TAG, "Cancelling task: $taskId")
                Downloader.cancel(taskId)
            }
            ACTION_REPOST_NOTIFICATION -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                repostProgressNotification(taskId)
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop all requested")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }


    // ── Notification management ───────────────────────────────────────────────

    private fun onTasksUpdated(taskMap: Map<String, com.waigi.dock.download.DownloadTask>) {
        if (isFirstEmission) {
            isFirstEmission = false
            taskMap.forEach { (id, task) ->
                if (task.state.isTerminal) {
                    processedTasks.add(id)
                }
                if (task.state is TaskState.Downloading) {
                    startedToastsShown.add(id)
                }
            }
        }

        // Clean up any untracked task IDs to keep memory clean
        processedTasks.retainAll(taskMap.keys)
        startedToastsShown.retainAll(taskMap.keys)

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
            val state = task.state
            if (state is TaskState.Downloading) {
                if (task.id !in startedToastsShown) {
                    startedToastsShown.add(task.id)
                    serviceScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@DownloadService,
                            "Download started in background…",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            if (state.isTerminal) {
                if (task.id !in processedTasks) {
                    processedTasks.add(task.id)

                    when (state) {
                        is TaskState.Completed -> {
                            notificationManager.cancel(task.notificationId)
                            if (notificationsEnabled) {
                                val notif = NotificationUtil.buildCompleteNotification(
                                    context = this,
                                    title = task.title,
                                    requestCode = task.notificationId + 100_000
                                )
                                notificationManager.notify(task.notificationId + 100_000, notif)
                            }
                            serviceScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@DownloadService,
                                    "Download complete: ${task.title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        is TaskState.Error -> {
                            notificationManager.cancel(task.notificationId)
                            if (notificationsEnabled) {
                                val notif = NotificationUtil.buildErrorNotification(
                                    context = this,
                                    title = task.title.ifEmpty { task.url },
                                    error = state.message,
                                    requestCode = task.notificationId + 200_000
                                )
                                notificationManager.notify(task.notificationId + 200_000, notif)
                            }
                            serviceScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@DownloadService,
                                    state.message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> Unit
                    }
                }
            } else {
                processedTasks.remove(task.id)
                if (state == TaskState.Queued) {
                    startedToastsShown.remove(task.id)
                }
            }
        }

        // Stop the service when nothing is running
        if (hasStartedActiveTask && activeCount == 0 && taskMap.values.all { it.state.isTerminal }) {
            Log.d(TAG, "No active tasks — stopping service")
            stopSelf()
        }
    }

    private fun repostProgressNotification(taskId: String) {
        val task = Downloader.tasks.value[taskId] ?: return
        val state = task.state
        if (state is TaskState.Downloading || state is TaskState.FetchingInfo) {
            val notificationsEnabled = com.waigi.dock.util.PreferenceUtil.notificationsEnabled
            if (notificationsEnabled) {
                val notif = if (state is TaskState.Downloading) {
                    NotificationUtil.buildProgressNotification(
                        context = this,
                        taskId = task.id,
                        title = task.title,
                        progress = state.progress,
                        speedText = state.speedText,
                        etaSeconds = state.etaSeconds,
                        totalSize = task.totalSize,
                    )
                } else {
                    NotificationUtil.buildProgressNotification(
                        context = this,
                        taskId = task.id,
                        title = task.title.ifEmpty { task.url },
                        progress = 0f,
                        speedText = "Fetching info…",
                        etaSeconds = -1,
                    )
                }
                notificationManager.notify(task.notificationId, notif)
            }
        }
    }
}
