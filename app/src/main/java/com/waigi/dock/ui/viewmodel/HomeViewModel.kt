package com.waigi.dock.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waigi.dock.download.Downloader
import com.waigi.dock.service.DownloadService
import com.waigi.dock.util.DownloadPreferences
import com.waigi.dock.util.DownloadUtil
import com.waigi.dock.util.VideoInfo
import com.waigi.dock.util.PlaylistResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val url: String = "",
    val isLoadingInfo: Boolean = false,
    val videoInfo: VideoInfo? = null,
    val error: String? = null,
    val showFormatSheet: Boolean = false,
    val selectedAudio: Boolean = false,
    val selectedQuality: Int = 0,           // 0 = best
    val downloadStarted: Boolean = false,
    val isPlaylist: Boolean = false,
    val playlistTitle: String? = null,
    val playlistVideoCount: Int = 0,
    val playlistEntries: List<VideoInfo>? = null,
    val showPlaylistPrompt: Boolean = false,
    val downloadSubtitles: Boolean = false,
    val selectedSubtitleLang: String = "",
    val currentTaskId: String? = null,
    val isBannerDismissed: Boolean = false,
    val lastFetchedUrl: String? = null,
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var fetchJob: kotlinx.coroutines.Job? = null

    // ── URL input ─────────────────────────────────────────────────────────────

    fun onUrlChanged(url: String) {
        _uiState.update { current ->
            val isSameAsLast = current.lastFetchedUrl != null && url.trim() == current.lastFetchedUrl
            current.copy(
                url = url,
                error = null,
                videoInfo = if (isSameAsLast) current.videoInfo else null,
            )
        }
    }

    fun onUrlCleared() {
        _uiState.update {
            it.copy(
                url = "",
                error = null,
                videoInfo = null,
                showFormatSheet = false,
                lastFetchedUrl = null,
            )
        }
    }

    /** Called when a URL is shared into the app from another app. */
    fun onSharedUrl(url: String) {
        if (url.isNotBlank()) {
            _uiState.update { it.copy(url = url) }
            fetchInfo(url)
        }
    }

    // ── Info fetching ─────────────────────────────────────────────────────────

    fun cancelFetchInfo() {
        fetchJob?.cancel()
        fetchJob = null
        _uiState.update { it.copy(isLoadingInfo = false) }
    }

    fun openFormatSheet() {
        if (_uiState.value.videoInfo != null) {
            _uiState.update { it.copy(showFormatSheet = true) }
        }
    }

    fun fetchInfo(url: String = _uiState.value.url) {
        if (url.isBlank()) return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInfo = true, error = null) }
            val trimmedUrl = url.trim()

            // Allow the UI split spring animation to complete smoothly at 60/120fps before spawning engine
            kotlinx.coroutines.delay(260)
            if (!isActive) return@launch

            val isPlaylistUrl = trimmedUrl.contains("list=") || trimmedUrl.contains("/sets/") || trimmedUrl.contains("/playlist")

            if (isPlaylistUrl) {
                val playlistResult = DownloadUtil.fetchPlaylistInfo(
                    url = trimmedUrl,
                    preferences = DownloadPreferences.createFromPreferences(),
                )
                playlistResult.onSuccess { playlist ->
                    val entries = playlist.entries
                    if (!entries.isNullOrEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoadingInfo = false,
                                isPlaylist = true,
                                playlistTitle = playlist.title ?: "Playlist",
                                playlistVideoCount = entries.size,
                                playlistEntries = entries,
                                showPlaylistPrompt = true,
                            )
                        }
                        return@launch
                    }
                }
            }

            // Fallback / Standard single video fetch
            val result = DownloadUtil.fetchVideoInfo(
                url = trimmedUrl,
                preferences = DownloadPreferences.createFromPreferences(),
            )
            result.fold(
                onSuccess = { info ->
                    val defaultSub = info.subtitles?.keys?.firstOrNull()
                        ?: info.automatic_captions?.keys?.firstOrNull()
                        ?: ""
                    _uiState.update {
                        it.copy(
                            isLoadingInfo = false,
                            videoInfo = info,
                            showFormatSheet = true,
                            isPlaylist = false,
                            showPlaylistPrompt = false,
                            downloadSubtitles = false,
                            selectedSubtitleLang = defaultSub,
                            isBannerDismissed = false,
                            lastFetchedUrl = trimmedUrl,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingInfo = false,
                            error = cleanErrorMessage(err.message),
                            isPlaylist = false,
                            showPlaylistPrompt = false,
                        )
                    }
                },
            )
        }
    }

    fun startPlaylistDownload(context: Context) {
        val state = _uiState.value
        val entries = state.playlistEntries ?: return

        viewModelScope.launch {
            entries.forEach { entry ->
                val entryUrl = entry.webpage_url ?: entry.url ?: if (entry.id.isNotEmpty()) {
                    "https://www.youtube.com/watch?v=${entry.id}"
                } else null

                if (entryUrl != null) {
                    val prefs = DownloadPreferences.createFromPreferences().copy(
                        extractAudio = state.selectedAudio,
                        videoQuality = state.selectedQuality,
                    )
                    DownloadService.startDownload(context, entryUrl, prefs)
                }
            }
        }

        _uiState.update {
            it.copy(
                showPlaylistPrompt = false,
                downloadStarted = true,
                url = "",
                playlistEntries = null,
                videoInfo = null,
                lastFetchedUrl = null,
            )
        }
    }

    fun dismissPlaylistPrompt() {
        _uiState.update { it.copy(showPlaylistPrompt = false) }
    }

    fun fetchSingleVideoFromPlaylist() {
        val url = _uiState.value.url.trim()
        _uiState.update { it.copy(showPlaylistPrompt = false) }
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInfo = true, error = null, videoInfo = null) }
            kotlinx.coroutines.delay(260)
            if (!isActive) return@launch
            val result = DownloadUtil.fetchVideoInfo(
                url = url,
                preferences = DownloadPreferences.createFromPreferences().copy(downloadPlaylist = false),
            )
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isLoadingInfo = false,
                            videoInfo = info,
                            showFormatSheet = true,
                            lastFetchedUrl = url,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingInfo = false,
                            error = cleanErrorMessage(err.message),
                        )
                    }
                },
            )
        }
    }

    // ── Format sheet ──────────────────────────────────────────────────────────

    fun onAudioSelected(isAudio: Boolean) {
        _uiState.update { it.copy(selectedAudio = isAudio) }
    }

    fun onQualitySelected(quality: Int) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    fun onDismissFormatSheet() {
        _uiState.update { it.copy(showFormatSheet = false) }
    }

    fun onSubtitleToggle(download: Boolean) {
        _uiState.update { it.copy(downloadSubtitles = download) }
    }

    fun onSubtitleLangSelected(langCode: String) {
        _uiState.update { it.copy(selectedSubtitleLang = langCode) }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    fun startDownload(context: Context, customPrefs: DownloadPreferences? = null) {
        val state = _uiState.value
        val url = state.url.trim()
        if (url.isBlank()) return

        val prefs = customPrefs ?: DownloadPreferences.createFromPreferences().copy(
            extractAudio = state.selectedAudio,
            videoQuality = state.selectedQuality,
            writeSubtitle = state.downloadSubtitles,
            embedSubtitle = state.downloadSubtitles,
            subtitleLanguage = state.selectedSubtitleLang,
        )

        val taskId = java.util.UUID.randomUUID().toString()
        DownloadService.startDownload(context, url, prefs, taskId = taskId)

        _uiState.update {
            it.copy(
                showFormatSheet = false,
                downloadStarted = true,
                currentTaskId = taskId,
                isBannerDismissed = false,
                url = "",
                videoInfo = null,
                lastFetchedUrl = null,
            )
        }
    }

    fun dismissThumbnailBanner() {
        _uiState.update { it.copy(isBannerDismissed = true) }
    }

    fun onDownloadStartedAcknowledged() {
        _uiState.update { it.copy(downloadStarted = false) }
    }

    private fun cleanErrorMessage(message: String?): String {
        if (message == null) return "Failed to fetch video info"
        return message.lines()
            .map { it.trim() }
            .filter { line ->
                !line.contains("impersonation", ignoreCase = true) &&
                !line.contains("WARNING:", ignoreCase = true)
            }
            .joinToString("\n")
            .trim()
            .ifEmpty { "Failed to fetch video info" }
    }
}
