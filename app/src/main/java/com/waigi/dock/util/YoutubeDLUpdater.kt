package com.waigi.dock.util

import android.content.Context
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

    /**
     * Initialize yt-dlp, ffmpeg, and aria2c.
     * Must be called before any download operations.
     */
    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            Aria2c.getInstance().init(context)
            _ytDlpVersion.value = YoutubeDL.getInstance().version(context) ?: "unknown"
            Log.i(TAG, "yt-dlp initialized. Version: ${_ytDlpVersion.value}")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "Failed to initialize yt-dlp", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during yt-dlp init", e)
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
