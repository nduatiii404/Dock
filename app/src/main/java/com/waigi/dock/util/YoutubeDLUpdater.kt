package com.waigi.dock.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

private const val TAG = "YoutubeDLUpdater"

/**
 * Manages initialization and updates of the yt-dlp engine,
 * ffmpeg, and aria2c binaries.
 *
 * Call [init] once in [DockApp.onCreate]. After that, use [updateYtDlp]
 * to pull the latest yt-dlp binary so downloads keep working as platforms change.
 */
object YoutubeDLUpdater {

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class Success(val newVersion: String) : UpdateState()
        object AlreadyUpToDate : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _ytDlpVersion = MutableStateFlow("unknown")
    val ytDlpVersion: StateFlow<String> = _ytDlpVersion.asStateFlow()

    @Volatile
    private var isInitialized = false

    @Volatile
    var lastInitError: Throwable? = null
        private set

    /**
     * Check if yt-dlp is initialized; if not, initialize it immediately.
     * Throws an informative exception if initialization fails so callers
     * never execute against an uninitialized singleton.
     */
    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        if (isInitialized) return true
        init(context)
        if (!isInitialized) {
            val cause = lastInitError
            val msg = cause?.localizedMessage ?: cause?.message ?: "unknown native initialization failure"
            throw IllegalStateException("yt-dlp engine failed to initialize: $msg", cause)
        }
        return true
    }

    /**
     * Ensures all required native libraries (.so and .zip.so) exist in
     * nativeLibraryDir. If missing on disk (common in Android 10+ / split APK installs),
     * automatically extracts them from the APK into internal storage.
     */
    private fun ensureNativeLibraries(appContext: Context) {
        val currentLibDir = File(appContext.applicationInfo.nativeLibraryDir)
        val pythonZip = File(currentLibDir, "libpython.zip.so")
        val pythonSo = File(currentLibDir, "libpython.so")

        if (pythonZip.exists() && pythonZip.length() > 0 && pythonSo.exists()) {
            // Already present and non-empty in nativeLibraryDir
            return
        }

        Log.w(TAG, "Native libs missing from ${currentLibDir.absolutePath}. Attempting fallback extraction from APK…")
        try {
            val apkPath = appContext.applicationInfo.sourceDir ?: appContext.packageCodePath
            val apkFile = File(apkPath)
            if (!apkFile.exists() || !apkFile.canRead()) {
                Log.e(TAG, "Cannot read source APK at $apkPath for native extraction")
                return
            }

            val fallbackDir = File(appContext.filesDir, "youtubedl-bin")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()

            ZipFile(apkFile).use { zip ->
                val supportedAbis = Build.SUPPORTED_ABIS ?: arrayOf("arm64-v8a", "armeabi-v7a")
                var selectedAbi: String? = null

                for (abi in supportedAbis) {
                    val sample = zip.getEntry("lib/$abi/libpython.zip.so")
                    if (sample != null) {
                        selectedAbi = abi
                        break
                    }
                }

                if (selectedAbi == null) {
                    // Try any ABI prefix present
                    selectedAbi = zip.entries().asSequence()
                        .mapNotNull { it.name }
                        .filter { it.startsWith("lib/") && it.contains("libpython.zip.so") }
                        .map { it.split("/")[1] }
                        .firstOrNull()
                }

                if (selectedAbi != null) {
                    Log.i(TAG, "Extracting native libraries for ABI: $selectedAbi to ${fallbackDir.absolutePath}")
                    val prefix = "lib/$selectedAbi/"
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.startsWith(prefix) && !entry.isDirectory) {
                            val fileName = entry.name.removePrefix(prefix)
                            val targetFile = File(fallbackDir, fileName)
                            if (!targetFile.exists() || targetFile.length() != entry.size) {
                                zip.getInputStream(entry).use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                targetFile.setExecutable(true, false)
                                targetFile.setReadable(true, false)
                            }
                        }
                    }
                    // Redirect nativeLibraryDir to the extracted directory
                    appContext.applicationInfo.nativeLibraryDir = fallbackDir.absolutePath
                    Log.i(TAG, "Native library directory redirected to: ${fallbackDir.absolutePath}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed during fallback native extraction", t)
        }
    }

    /**
     * Initialize yt-dlp, ffmpeg, and aria2c.
     * Must be called before any download operations.
     */
    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext ?: context
        try {
            ensureNativeLibraries(appContext)
            YoutubeDL.getInstance().init(appContext)
            try {
                FFmpeg.getInstance().init(appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "FFmpeg init warning: ${t.message}")
            }
            try {
                Aria2c.getInstance().init(appContext)
            } catch (t: Throwable) {
                Log.w(TAG, "Aria2c init warning: ${t.message}")
            }
            _ytDlpVersion.value = YoutubeDL.getInstance().version(appContext) ?: "unknown"
            isInitialized = true
            lastInitError = null
            Log.i(TAG, "yt-dlp initialized successfully. Version: ${_ytDlpVersion.value}")
        } catch (e: YoutubeDLException) {
            lastInitError = e
            Log.e(TAG, "Failed to initialize yt-dlp", e)
        } catch (t: Throwable) {
            lastInitError = t
            Log.e(TAG, "Unexpected error during yt-dlp init", t)
        }
    }

    /**
     * Update yt-dlp to the latest stable release.
     * This downloads a new Python binary — takes a few seconds on first run.
     *
     * Should be called:
     * - Once on app startup (if [PreferenceUtil.ytDlpAutoUpdate] is true and enough time has passed)
     * - When the user taps "Update yt-dlp" in Settings
     */
    suspend fun updateYtDlp(
        context: Context,
        channel: YoutubeDL.UpdateChannel = YoutubeDL.UpdateChannel.STABLE,
    ) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            val result = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
            when (result) {
                YoutubeDL.UpdateStatus.DONE -> {
                    val newVersion = YoutubeDL.getInstance().version(context) ?: "unknown"
                    _ytDlpVersion.value = newVersion
                    // Save last update time
                    MMKV.defaultMMKV().encode(YT_DLP_UPDATE_TIME, System.currentTimeMillis())
                    _updateState.value = UpdateState.Success(newVersion)
                    Log.i(TAG, "yt-dlp updated to: $newVersion")
                }
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> {
                    _updateState.value = UpdateState.AlreadyUpToDate
                    Log.i(TAG, "yt-dlp already up to date")
                }
                else -> {
                    _updateState.value = UpdateState.Error("Update failed")
                }
            }
        } catch (e: YoutubeDLException) {
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            Log.e(TAG, "yt-dlp update failed", e)
        }
    }

    /**
     * Returns true if enough time has passed since the last update
     * to warrant checking again (default: 7 days).
     */
    fun shouldAutoUpdate(): Boolean {
        if (!PreferenceUtil.ytDlpAutoUpdate) return false
        val lastUpdate = MMKV.defaultMMKV().decodeLong(YT_DLP_UPDATE_TIME, 0L)
        val intervalMs = 7 * 24 * 60 * 60 * 1000L // 7 days
        return System.currentTimeMillis() - lastUpdate > intervalMs
    }
}
