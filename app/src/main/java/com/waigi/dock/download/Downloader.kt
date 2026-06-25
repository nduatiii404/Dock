package com.waigi.dock.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.waigi.dock.database.DownloadedItem
import com.waigi.dock.repository.HistoryRepository
import com.waigi.dock.util.DownloadPreferences
import com.waigi.dock.util.DownloadUtil
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "Downloader"

/**
 * Singleton state holder for all active downloads.
 *
 * - Maintains a [StateFlow] of [DownloadTask] maps so the UI observes live updates.
 * - Manages a coroutine per task so they run concurrently.
 * - The [DownloadService] calls into this to start/cancel work;
 *   the UI observes [tasks] directly.
 */
object Downloader : KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyRepository: HistoryRepository by inject()
    private lateinit var appContext: Context

    /** Must be called once in DockApp.onCreate before any downloads start. */
    fun init(context: Context) {
        appContext = context.applicationContext
        registerNetworkCallback(appContext)
    }


    /** All active + recently completed tasks, keyed by task ID. */
    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks.asStateFlow()

    /** Running coroutine jobs keyed by task ID, used for cancellation. */
    private val jobs = mutableMapOf<String, Job>()

    private val taskPreferences = java.util.concurrent.ConcurrentHashMap<String, DownloadPreferences>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Enqueue a new download. Returns the task ID.
     */
    fun enqueue(
        url: String,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): String {
        val task = DownloadTask(url = url, isAudio = preferences.extractAudio)
        taskPreferences[task.id] = preferences
        addTask(task)
        checkQueue()
        return task.id
    }

    /**
     * Cancel a running or queued download by task ID.
     */
    fun cancel(taskId: String) {
        val job = jobs[taskId]
        if (job != null) {
            job.cancel()
        } else {
            updateTask(taskId) { it.copy(state = TaskState.Cancelled) }
            checkQueue()
        }
        YoutubeDL.getInstance().destroyProcessById(taskId)
        taskPreferences.remove(taskId)
        Log.d(TAG, "Cancelled task: $taskId")
    }

    /**
     * Pause a running download by task ID.
     */
    fun pause(taskId: String) {
        val job = jobs[taskId]
        val task = _tasks.value[taskId] ?: return
        val currentState = task.state
        if (currentState is TaskState.Downloading) {
            job?.cancel()
            YoutubeDL.getInstance().destroyProcessById(taskId)
            updateTask(taskId) {
                it.copy(
                    state = TaskState.Paused(
                        progress = currentState.progress,
                        speedText = "Paused",
                        etaSeconds = -1
                    )
                )
            }
            Log.d(TAG, "Paused task: $taskId")
        }
    }

    /**
     * Resume a paused download by task ID.
     */
    fun resume(taskId: String, preferences: DownloadPreferences = DownloadPreferences.createFromPreferences()) {
        val task = _tasks.value[taskId] ?: return
        val state = task.state
        if (state is TaskState.Paused) {
            taskPreferences[taskId] = preferences
            updateTask(taskId) {
                it.copy(
                    state = TaskState.Queued,
                    progress = state.progress,
                    speedText = "Queued…",
                    etaSeconds = -1
                )
            }
            checkQueue()
            Log.d(TAG, "Resumed task: $taskId")
        }
    }

    /**
     * Retry a failed task with the same URL and preferences.
     */
    fun retry(taskId: String, preferences: DownloadPreferences = DownloadPreferences.createFromPreferences()) {
        val task = _tasks.value[taskId] ?: return
        taskPreferences[taskId] = preferences
        updateTask(taskId) { it.copy(state = TaskState.Queued, progress = 0f, errorMessage = null) }
        checkQueue()
    }

    /**
     * Remove a terminal task from the active map (e.g. to dismiss from the list).
     */
    fun dismiss(taskId: String) {
        val task = _tasks.value[taskId] ?: return
        if (task.state.isTerminal) {
            _tasks.update { it - taskId }
            taskPreferences.remove(taskId)
        }
    }

    /** Number of tasks currently in progress. */
    val activeCount: Int
        get() = _tasks.value.values.count { it.state.isBusy }

    private fun checkQueue() {
        val maxActive = com.waigi.dock.util.PreferenceUtil.maxConcurrentDownloads
        val activeCount = _tasks.value.values.count { it.state == TaskState.FetchingInfo || it.state is TaskState.Downloading }
        if (activeCount >= maxActive) return

        val nextTask = _tasks.value.values.firstOrNull { it.state == TaskState.Queued } ?: return
        val prefs = taskPreferences[nextTask.id] ?: DownloadPreferences.createFromPreferences()

        updateTask(nextTask.id) { it.copy(state = TaskState.FetchingInfo) }
        startTask(nextTask, prefs)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun startTask(task: DownloadTask, preferences: DownloadPreferences) {
        val job = scope.launch {
            try {
                runTask(task.id, task.url, preferences)
            } finally {
                checkQueue()
            }
        }
        jobs[task.id] = job
        job.invokeOnCompletion { jobs.remove(task.id) }
    }

    private suspend fun runTask(
        taskId: String,
        url: String,
        preferences: DownloadPreferences,
    ) {
        if (!com.waigi.dock.util.PreferenceUtil.allowCellularDownload && isCellularNetwork(appContext)) {
            updateTask(taskId) {
                it.copy(
                    state = TaskState.Error("Cellular download is disabled in settings"),
                    errorMessage = "Cellular download is disabled in settings",
                )
            }
            return
        }

        val existingTask = _tasks.value[taskId]
        val skipFetch = existingTask != null && existingTask.title.isNotEmpty()

        if (!skipFetch) {
            // Step A: fetch info (title, thumbnail)
            updateTask(taskId) { it.copy(state = TaskState.FetchingInfo) }

            val infoResult = DownloadUtil.fetchVideoInfo(url, preferences, taskKey = taskId)
            infoResult.onSuccess { info ->
                updateTask(taskId) {
                    it.copy(
                        title = info.title,
                        thumbnail = info.thumbnail,
                        uploader = info.uploader,
                    )
                }
            }.onFailure { err ->
                Log.e(TAG, "Failed to fetch info for $url", err)
                updateTask(taskId) {
                    it.copy(
                        state = TaskState.Error(err.message ?: "Failed to fetch video info"),
                        errorMessage = err.message,
                    )
                }
                return
            }
        }

        // Resolve unique filename and title if a duplicate exists in output folder
        val currentTask = _tasks.value[taskId]
        val originalTitle = currentTask?.title ?: ""
        val outputDir = if (preferences.extractAudio) {
            com.waigi.dock.util.FileUtil.audioDownloadDir
        } else {
            com.waigi.dock.util.FileUtil.videoDownloadDir
        }
        val resolvedUniqueTitle = if (originalTitle.isNotEmpty()) {
            DownloadUtil.getUniqueTitle(outputDir, originalTitle)
        } else {
            originalTitle
        }

        if (resolvedUniqueTitle.isNotEmpty() && resolvedUniqueTitle != originalTitle) {
            updateTask(taskId) { it.copy(title = resolvedUniqueTitle) }
        }

        // Step B: execute the download
        updateTask(taskId) {
            it.copy(
                state = TaskState.Downloading(
                    progress = existingTask?.progress ?: 0f,
                    speedText = "Starting…",
                    etaSeconds = -1
                )
            )
        }

        val downloadResult = DownloadUtil.download(
            url = url,
            taskKey = taskId,
            preferences = preferences,
            title = if (resolvedUniqueTitle.isNotEmpty()) resolvedUniqueTitle else null,
        ) { progress, etaSeconds, line ->
            val parsedProgress = extractProgress(line, progress)
            val parsedEta = extractEta(line, etaSeconds)
            val speedText = extractSpeed(line)
            val parsedTotal = extractTotalSize(line)
            updateTask(taskId) {
                val resolvedTotal = if (parsedTotal.isNotEmpty()) parsedTotal else it.totalSize
                val resolvedSpeed = if (speedText.isNotEmpty()) speedText else it.speedText
                it.copy(
                    progress = parsedProgress,
                    speedText = resolvedSpeed,
                    etaSeconds = parsedEta,
                    totalSize = resolvedTotal,
                    state = TaskState.Downloading(
                        progress = parsedProgress,
                        speedText = resolvedSpeed,
                        etaSeconds = parsedEta,
                    ),
                )
            }
        }

        downloadResult.onSuccess { msg ->
            Log.d(TAG, "Task $taskId completed: $msg")
            val isPath = msg.startsWith("/")
            val outputPath = if (isPath) msg else ""

            if (outputPath.isNotEmpty() && preferences.embedSubtitle && !preferences.keepSubtitleFiles) {
                com.waigi.dock.util.FileUtil.cleanUpSubtitleFiles(outputPath)
            }

            // Update task output path and completed state
            updateTask(taskId) {
                it.copy(
                    outputPath = outputPath,
                    state = TaskState.Completed(outputPath),
                    progress = 100f,
                )
            }

            // Retrieve updated task with title/meta populated to save to history
            val task = _tasks.value[taskId]
            task?.let { t ->
                scope.launch {
                    if (!com.waigi.dock.util.PreferenceUtil.privateMode) {
                        historyRepository.save(
                            DownloadedItem(
                                url = t.url,
                                title = t.title.ifEmpty { t.url },
                                uploader = t.uploader,
                                thumbnail = t.thumbnail,
                                filePath = outputPath,
                                isAudio = t.isAudio,
                            )
                        )
                    }
                    // Scan into MediaStore so it appears in Files/Gallery
                    if (outputPath.isNotEmpty() && ::appContext.isInitialized) {
                        MediaScannerConnection.scanFile(
                            appContext, arrayOf(outputPath), null, null
                        )
                    }
                }
            }
        }.onFailure { err ->
            if (err is kotlinx.coroutines.CancellationException) {
                updateTask(taskId) { it.copy(state = TaskState.Cancelled) }
            } else {
                Log.e(TAG, "Task $taskId failed", err)
                updateTask(taskId) {
                    it.copy(
                        state = TaskState.Error(err.message ?: "Download failed"),
                        errorMessage = err.message,
                    )
                }
            }
        }
    }

    private fun addTask(task: DownloadTask) {
        _tasks.update { it + (task.id to task) }
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { map ->
            val existing = map[taskId] ?: return@update map
            map + (taskId to transform(existing))
        }
    }

    /** Parse download speed from a yt-dlp progress line, e.g. "3.20MiB/s". */
    private fun extractSpeed(line: String): String {
        val regex = Regex("""(\d+\.?\d*\s?(?:KiB|MiB|GiB|KB|MB|GB)/s)""")
        return regex.find(line)?.value ?: ""
    }

    /** Parse total size from a yt-dlp progress line, e.g. "[download]  23.5% of  15.42MiB at ..." */
    private fun extractTotalSize(line: String): String {
        val stdMatch = Regex("""of\s+(\d+\.?\d*\s*(?:KiB|MiB|GiB|KB|MB|GB|B|iB))""", RegexOption.IGNORE_CASE).find(line)
        if (stdMatch != null) {
            return stdMatch.groupValues[1]
        }
        val ariaMatch = Regex("""/\s*(\d+\.?\d*\s*(?:KiB|MiB|GiB|KB|MB|GB|B|iB))\(""", RegexOption.IGNORE_CASE).find(line)
        if (ariaMatch != null) {
            return ariaMatch.groupValues[1]
        }
        return ""
    }

    private fun extractProgress(line: String, defaultProgress: Float): Float {
        val stdMatch = Regex("""\[download\]\s+(\d+\.?\d*)%""").find(line)
        if (stdMatch != null) {
            return stdMatch.groupValues[1].toFloatOrNull() ?: defaultProgress
        }
        val ariaMatch = Regex("""\((\d+)%\)""").find(line)
        if (ariaMatch != null) {
            return ariaMatch.groupValues[1].toFloatOrNull() ?: defaultProgress
        }
        return defaultProgress
    }

    private fun extractEta(line: String, defaultEta: Long): Long {
        val stdMatch = Regex("""ETA\s+(\d{1,2}:)?(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE).find(line)
        if (stdMatch != null) {
            val hrs = stdMatch.groupValues[1].removeSuffix(":").toIntOrNull() ?: 0
            val mins = stdMatch.groupValues[2].toIntOrNull() ?: 0
            val secs = stdMatch.groupValues[3].toIntOrNull() ?: 0
            return (hrs * 3600 + mins * 60 + secs).toLong()
        }
        val ariaMatch = Regex("""ETA:(\d+h)?(\d+m)?(\d+s)""", RegexOption.IGNORE_CASE).find(line)
        if (ariaMatch != null) {
            val hrs = ariaMatch.groupValues[1]?.removeSuffix("h")?.toIntOrNull() ?: 0
            val mins = ariaMatch.groupValues[2]?.removeSuffix("m")?.toIntOrNull() ?: 0
            val secs = ariaMatch.groupValues[3]?.removeSuffix("s")?.toIntOrNull() ?: 0
            return (hrs * 3600 + mins * 60 + secs).toLong()
        }
        val ariaSimple = Regex("""ETA:(\d+)s""", RegexOption.IGNORE_CASE).find(line)
        if (ariaSimple != null) {
            return ariaSimple.groupValues[1].toLongOrNull() ?: defaultEta
        }
        return defaultEta
    }

    /** Calculate downloaded size based on progress percentage and total size string. */
    fun calculateDownloadedSize(totalSizeStr: String, progressPercent: Float): String {
        if (totalSizeStr.isEmpty()) return ""
        val regex = Regex("""([\d.]+)\s*([a-zA-Z]+)""")
        val match = regex.find(totalSizeStr) ?: return ""
        val number = match.groupValues[1].toDoubleOrNull() ?: return ""
        val unit = match.groupValues[2]
        val downloadedVal = number * (progressPercent / 100f)
        return String.format(java.util.Locale.US, "%.2f %s", downloadedVal, unit)
    }

    private fun isCellularNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private var isNetworkAvailable = false

    private fun registerNetworkCallback(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    val wasOffline = !isNetworkAvailable
                    isNetworkAvailable = true
                    if (wasOffline && com.waigi.dock.util.PreferenceUtil.autoRetryOnConnect) {
                        retryFailedDownloads()
                    }
                }

                override fun onLost(network: android.net.Network) {
                    super.onLost(network)
                    isNetworkAvailable = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun retryFailedDownloads() {
        scope.launch {
            kotlinx.coroutines.delay(1000)
            _tasks.value.forEach { (taskId, task) ->
                if (task.state is TaskState.Error) {
                    Log.d(TAG, "Auto-retrying failed task: $taskId due to connection restoration")
                    retry(taskId)
                }
            }
        }
    }
}

