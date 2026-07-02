package com.waigi.dock.util

import android.util.Log
import com.waigi.dock.util.FileUtil.audioDownloadDir
import com.waigi.dock.util.FileUtil.getArchiveFile
import com.waigi.dock.util.FileUtil.getCookiesFile
import com.waigi.dock.util.FileUtil.videoDownloadDir
import com.waigi.dock.util.PreferenceUtil.getString
import com.yausername.aria2c.Aria2c
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "DownloadUtil"

/** yt-dlp output template constants */
private const val BASENAME = "%(title).200B"
private const val EXTENSION = ".%(ext)s"
private const val ID = "[%(id)s]"
private const val CLIP_TIMESTAMP = "%(section_start)d-%(section_end)d"

const val OUTPUT_TEMPLATE_DEFAULT = "$BASENAME$EXTENSION"
const val OUTPUT_TEMPLATE_ID = "$BASENAME $ID$EXTENSION"
const val OUTPUT_TEMPLATE_CHAPTER = "chapter:$BASENAME/%(section_number)d - %(section_title).200B$EXTENSION"
const val OUTPUT_TEMPLATE_PLAYLIST = "$BASENAME/$OUTPUT_TEMPLATE_DEFAULT"

private val jsonFormat = Json { ignoreUnknownKeys = true }

// ── Serializable data classes for yt-dlp JSON output ─────────────────────────

@Serializable
data class VideoInfo(
    val id: String = "",
    val title: String = "",
    val url: String? = null,
    val thumbnail: String? = null,
    val description: String? = null,
    val duration: Double? = null,
    val uploader: String? = null,
    val upload_date: String? = null,
    val webpage_url: String? = null,
    val extractor: String? = null,
    val ext: String? = null,
    val formats: List<FormatInfo>? = null,
    val requested_formats: List<FormatInfo>? = null,
    val subtitles: Map<String, List<SubtitleInfo>>? = null,
    val automatic_captions: Map<String, List<SubtitleInfo>>? = null,
)

@Serializable
data class PlaylistResult(
    val id: String = "",
    val title: String? = null,
    val type: String? = null,
    val entries: List<VideoInfo>? = null,
    val webpage_url: String? = null,
)

@Serializable
data class FormatInfo(
    val format_id: String = "",
    val ext: String? = null,
    val resolution: String? = null,
    val fps: Double? = null,
    val height: Double? = null,
    val width: Double? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Double? = null,
    val filesize_approx: Double? = null,
    val tbr: Double? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val format_note: String? = null,
)

@Serializable
data class SubtitleInfo(
    val ext: String? = null,
    val url: String? = null,
    val name: String? = null,
)

// ── Download Preferences ──────────────────────────────────────────────────────

data class DownloadPreferences(
    val extractAudio: Boolean = false,
    val createThumbnail: Boolean = false,
    val downloadPlaylist: Boolean = false,
    val subdirectoryPlaylist: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedSubtitle: Boolean = false,
    val writeSubtitle: Boolean = false,
    val autoSubtitle: Boolean = false,
    val autoTranslatedSubtitles: Boolean = false,
    val keepSubtitleFiles: Boolean = false,
    val convertSubtitle: Int = NOT_SPECIFIED,
    val subtitleLanguage: String = "en",
    val useAria2c: Boolean = false,
    val concurrentFragments: Int = 1,
    val useCookies: Boolean = false,
    val userAgentString: String = "",
    val useProxy: Boolean = false,
    val proxyUrl: String = "",
    val forceIpv4: Boolean = false,
    val useSponsorBlock: Boolean = false,
    val sponsorBlockCategories: String = "sponsor",
    val useRateLimit: Boolean = false,
    val maxRate: String = "1M",
    val restrictFilenames: Boolean = false,
    val useDownloadArchive: Boolean = false,
    val cropArtwork: Boolean = false,
    val audioFormat: Int = NOT_SPECIFIED,
    val audioQuality: Int = NOT_SPECIFIED,
    val videoFormat: String = "",
    val videoQuality: Int = NOT_SPECIFIED,
    val formatSorting: Boolean = false,
    val sortingFields: String = "",
    val outputTemplate: String = "",
    val customOutputTemplate: Boolean = false,
    val maxFileSize: String = "",
    val isAudioDirectory: Boolean = false,
) {
    companion object {
        /** Read all preferences from MMKV and build a DownloadPreferences instance. */
        fun createFromPreferences(): DownloadPreferences = with(PreferenceUtil) {
            DownloadPreferences(
                extractAudio = extractAudio,
                createThumbnail = createThumbnail,
                downloadPlaylist = downloadPlaylist,
                subdirectoryPlaylist = SUBDIRECTORY_PLAYLIST.getBoolean(),
                embedMetadata = embedMetadata,
                embedThumbnail = embedThumbnail,
                embedSubtitle = embedSubtitle,
                writeSubtitle = subtitle,
                autoSubtitle = autoSubtitle,
                autoTranslatedSubtitles = autoTranslatedSubtitles,
                keepSubtitleFiles = keepSubtitleFiles,
                convertSubtitle = convertSubtitle,
                subtitleLanguage = subtitleLanguage,
                useAria2c = useAria2c,
                concurrentFragments = concurrentFragments,
                useCookies = useCookies,
                userAgentString = userAgentString,
                useProxy = useProxy,
                proxyUrl = proxyUrl,
                forceIpv4 = forceIpv4,
                useSponsorBlock = useSponsorBlock,
                sponsorBlockCategories = sponsorBlockCategories,
                useRateLimit = useRateLimit,
                maxRate = maxRate,
                restrictFilenames = restrictFilenames,
                useDownloadArchive = useDownloadArchive,
                cropArtwork = cropArtwork,
                audioFormat = audioFormat,
                audioQuality = audioQuality,
                videoFormat = videoFormat,
                videoQuality = videoQuality,
                formatSorting = formatSorting,
                sortingFields = sortingFields,
                outputTemplate = outputTemplate,
                customOutputTemplate = outputTemplate.isNotEmpty(),
                maxFileSize = maxFileSize,
                isAudioDirectory = extractAudio,
            )
        }
    }
}

// ── DownloadUtil ──────────────────────────────────────────────────────────────

/**
 * Core download engine. Wraps the youtubedl-android library and exposes
 * high-level functions for fetching video info and executing downloads.
 *
 * All functions that talk to yt-dlp are `suspend` and run on [Dispatchers.IO].
 */
object DownloadUtil {

    /**
     * Fetch metadata (title, thumbnail, formats, subtitles) for a URL
     * without downloading anything.
     */
    suspend fun fetchVideoInfo(
        url: String,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
        playlistIndex: Int? = null,
        taskKey: String? = null,
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = YoutubeDLRequest(url).apply {
                addOption("-o", BASENAME)
                addOption("-R", "3")
                addOption("--socket-timeout", "30")
                addOption("--no-warnings")
                if (url.contains("tiktok.com")) {
                    addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                }
                if (playlistIndex != null) {
                    addOption("--playlist-items", playlistIndex.toString())
                    addOption("--dump-json")
                } else {
                    addOption("--dump-single-json")
                    addOption("--no-playlist")
                }
                with(preferences) {
                    if (extractAudio) addOption("-x")
                    if (restrictFilenames) addOption("--restrict-filenames")
                    if (useCookies) enableCookies(userAgentString)
                    if (useProxy) enableProxy(proxyUrl)
                    if (forceIpv4) addOption("-4")
                    if (autoSubtitle) {
                        addOption("--write-auto-subs")
                        if (!autoTranslatedSubtitles) {
                            addOption("--extractor-args", "youtube:skip=translated_subs")
                        }
                    }
                }
            }
            val response = YoutubeDL.getInstance().execute(request, taskKey, null)
            jsonFormat.decodeFromString<VideoInfo>(response.out)
        }
    }

    /**
     * Fetch playlist metadata (list of entries) for a playlist URL.
     */
    suspend fun fetchPlaylistInfo(
        url: String,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): Result<PlaylistResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("-o", BASENAME)
                addOption("-R", "3")
                addOption("--socket-timeout", "30")
                addOption("--no-warnings")
                if (url.contains("tiktok.com")) {
                    addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                }
                with(preferences) {
                    if (extractAudio) addOption("-x")
                    if (useCookies) enableCookies(userAgentString)
                    if (useProxy) enableProxy(proxyUrl)
                    if (forceIpv4) addOption("-4")
                }
            }
            val response = YoutubeDL.getInstance().execute(request, null, null)
            jsonFormat.decodeFromString<PlaylistResult>(response.out)
        }
    }

    /**
     * Build and execute a full download request.
     *
     * @param url       The video/audio URL to download.
     * @param taskKey   Unique ID used to cancel this specific download.
     * @param preferences Download options derived from user settings.
     * @param progressCallback Called with (percent, etaSeconds, speedBytesPerSec, line).
     */
    suspend fun download(
        url: String,
        taskKey: String,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
        title: String? = null,
        progressCallback: ((Float, Long, String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val outputDir = if (preferences.extractAudio) audioDownloadDir else videoDownloadDir
            
            val finalTitle = if (!title.isNullOrBlank()) {
                getUniqueTitle(outputDir, title)
            } else {
                null
            }

            val outputTemplate = if (finalTitle != null) {
                "$finalTitle.%(ext)s"
            } else {
                buildOutputTemplate(preferences)
            }

            val request = YoutubeDLRequest(url).apply {
                // ── Output path ───────────────────────────────────────────
                addOption("-o", "$outputDir/$outputTemplate")
                addOption("-P", "temp:${FileUtil.getTempDir().absolutePath}")
                if (url.contains("tiktok.com")) {
                    addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                }

                // ── Audio extraction ──────────────────────────────────────
                if (preferences.extractAudio) {
                    addOption("-x")
                    if (preferences.audioFormat != NOT_SPECIFIED) {
                        addOption("--audio-format", audioFormatName(preferences.audioFormat))
                    }
                    if (preferences.audioQuality != NOT_SPECIFIED) {
                        addOption("--audio-quality", preferences.audioQuality.toString())
                    }
                    if (preferences.embedThumbnail) {
                        addOption("--embed-thumbnail")
                        if (preferences.cropArtwork) addOption(
                            "--ppa",
                            """ffmpeg: -c:v mjpeg -vf crop="'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'""""
                        )
                    }
                } else {
                    // ── Video format ─────────────────────────────────────
                    if (preferences.videoFormat.isNotEmpty()) {
                        addOption("-f", preferences.videoFormat)
                    }
                    if (preferences.videoQuality != NOT_SPECIFIED) {
                        applyVideoQuality(preferences.videoQuality)
                    }
                    if (preferences.embedThumbnail) addOption("--embed-thumbnail")
                }

                // ── Metadata & chapters ───────────────────────────────────
                if (preferences.embedMetadata) {
                    addOption("--embed-metadata")
                    addOption("--embed-chapters")
                }

                // ── Subtitles ─────────────────────────────────────────────
                val downloadSubs = preferences.writeSubtitle || preferences.embedSubtitle || preferences.autoSubtitle
                if (downloadSubs) {
                    if (preferences.writeSubtitle || preferences.embedSubtitle) {
                        addOption("--write-subs")
                    }
                    if (preferences.autoSubtitle) {
                        addOption("--write-auto-subs")
                        if (!preferences.autoTranslatedSubtitles) {
                            addOption("--extractor-args", "youtube:skip=translated_subs")
                        }
                    }

                    val langs = if (preferences.subtitleLanguage.isNotEmpty()) {
                        preferences.subtitleLanguage
                    } else {
                        "en"
                    }
                    addOption("--sub-langs", langs)

                    if (preferences.embedSubtitle) {
                        addOption("--embed-subs")
                    }

                    val formatStr = when (preferences.convertSubtitle) {
                        1 -> "srt"
                        2 -> "vtt"
                        3 -> "ass"
                        4 -> "lrc"
                        else -> null
                    }
                    if (formatStr != null) {
                        addOption("--convert-subs", formatStr)
                    }
                }

                // ── Playlist ──────────────────────────────────────────────
                if (!preferences.downloadPlaylist) {
                    addOption("--no-playlist")
                }

                // ── Sponsorblock ──────────────────────────────────────────
                if (preferences.useSponsorBlock) {
                    addOption("--sponsorblock-remove", preferences.sponsorBlockCategories)
                }

                // ── Download archive (no re-downloads) ────────────────────
                if (preferences.useDownloadArchive) {
                    addOption("--download-archive", getArchiveFile().absolutePath)
                }

                // ── File size limit ───────────────────────────────────────
                if (preferences.maxFileSize.isNotEmpty()) {
                    addOption("--max-filesize", preferences.maxFileSize)
                }

                // ── Filenames ─────────────────────────────────────────────
                if (preferences.restrictFilenames) addOption("--restrict-filenames")

                // ── Aria2c (fast parallel downloading) ────────────────────
                if (preferences.useAria2c) {
                    addOption("--downloader", "libaria2c.so")
                    addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")
                }
                if (preferences.concurrentFragments > 1) {
                    addOption("-N", preferences.concurrentFragments.toString())
                }

                // ── Rate limiting ─────────────────────────────────────────
                if (preferences.useRateLimit && preferences.maxRate.isNotEmpty()) {
                    addOption("-r", preferences.maxRate)
                }

                // ── Cookies ───────────────────────────────────────────────
                if (preferences.useCookies) enableCookies(preferences.userAgentString)

                // ── Network ───────────────────────────────────────────────
                if (preferences.useProxy) enableProxy(preferences.proxyUrl)
                if (preferences.forceIpv4) addOption("-4")

                // ── Format sorting ────────────────────────────────────────
                if (preferences.formatSorting && preferences.sortingFields.isNotEmpty()) {
                    addOption("-S", preferences.sortingFields)
                }

                // ── Common stability options ──────────────────────────────
                addOption("--no-mtime")
                addOption("--print", "after_move:filepath")
                addOption("--progress")
                addOption("--newline")
                addOption("--no-warnings")

                // ── Custom Arguments ──────────────────────────────────────
                val customArgs = PreferenceUtil.customCmdArgs
                if (customArgs.isNotEmpty()) {
                    val tokens = customArgs.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                    var i = 0
                    while (i < tokens.size) {
                        val token = tokens[i]
                        if (token.startsWith("-") && i + 1 < tokens.size && !tokens[i+1].startsWith("-")) {
                            addOption(token, tokens[i+1])
                            i += 2
                        } else {
                            addOption(token)
                            i += 1
                        }
                    }
                }
            }

            Log.d(TAG, "Starting download: $url")
            val response = YoutubeDL.getInstance().execute(request, taskKey) { progress, etaInSeconds, line ->
                progressCallback?.invoke(progress, etaInSeconds, line)
            }

            var filePath = response.out.lines()
                .map { it.trim() }
                .lastOrNull { it.startsWith("/") }
                ?: ""

            if (filePath.isEmpty()) {
                val alreadyDownloadedLine = response.out.lines()
                    .map { it.trim() }
                    .firstOrNull { it.contains("has already been downloaded", ignoreCase = true) }
                if (alreadyDownloadedLine != null) {
                    val path = alreadyDownloadedLine
                        .substringAfter("[download]")
                        .substringBefore("has already been downloaded")
                        .trim()
                    if (path.startsWith("/")) {
                        filePath = path
                    }
                }
            }

            Log.d(TAG, "Extracted output filepath: $filePath")
            filePath.ifEmpty { "Download completed: $url" }
        }
    }

    /**
     * Cancel an in-progress download by its task key.
     * @return true if the task was found and cancelled.
     */
    fun cancelDownload(taskKey: String): Boolean =
        YoutubeDL.getInstance().destroyProcessById(taskKey)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildOutputTemplate(prefs: DownloadPreferences): String {
        if (prefs.customOutputTemplate && prefs.outputTemplate.isNotEmpty()) {
            return prefs.outputTemplate
        }
        return OUTPUT_TEMPLATE_DEFAULT
    }

    private fun YoutubeDLRequest.enableCookies(userAgent: String) {
        addOption("--cookies", getCookiesFile().absolutePath)
        if (userAgent.isNotEmpty()) addOption("--user-agent", userAgent)
    }

    private fun YoutubeDLRequest.enableProxy(proxyUrl: String) {
        if (proxyUrl.isNotEmpty()) addOption("--proxy", proxyUrl)
    }

    private fun YoutubeDLRequest.applyVideoQuality(quality: Int) {
        // 0=best, 1=1080p, 2=720p, 3=480p, 4=360p, 5=2160p, 6=1440p
        val heightMap = mapOf(
            5 to 2160,
            6 to 1440,
            1 to 1080,
            2 to 720,
            3 to 480,
            4 to 360
        )
        heightMap[quality]?.let { h ->
            addOption("-f", "bestvideo[height<=?$h]+bestaudio/best")
            if (h > 1080) {
                // High resolutions (2K/4K) require VP9 or AV1 codecs on YouTube.
                // Do not force codec:h264 for resolutions above 1080p.
                addOption("-S", "res:$h")
            } else {
                addOption("-S", "res:$h,codec:h264")
            }
        }
    }

    private fun audioFormatName(format: Int): String = when (format) {
        1 -> "mp3"
        2 -> "m4a"
        3 -> "opus"
        4 -> "wav"
        5 -> "flac"
        6 -> "aac"
        7 -> "ogg"
        else -> "mp3"
    }

    fun getUniqueTitle(outputDir: String, title: String): String {
        val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var targetTitle = sanitizedTitle
        var fileExists = checkFileExistsWithBaseName(outputDir, targetTitle)
        var counter = 1
        while (fileExists) {
            targetTitle = "$sanitizedTitle ($counter)"
            fileExists = checkFileExistsWithBaseName(outputDir, targetTitle)
            counter++
        }
        return targetTitle
    }

    private fun checkFileExistsWithBaseName(outputDir: String, baseName: String): Boolean {
        val dir = java.io.File(outputDir)
        if (!dir.exists() || !dir.isDirectory) return false
        val files = dir.listFiles() ?: return false
        return files.any { file ->
            file.isFile && file.nameWithoutExtension.equals(baseName, ignoreCase = true)
        }
    }
}
