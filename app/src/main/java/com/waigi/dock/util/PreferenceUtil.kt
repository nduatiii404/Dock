package com.waigi.dock.util

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Theme preference keys ─────────────────────────────────────────────────────
const val ACCENT_COLOR_KEY      = "accent_color"
const val BACKGROUND_STYLE_KEY  = "background_style"
const val SHOW_GRADIENT_HEADER = "show_gradient_header"

// Background style values
const val BG_AMOLED  = 0   // Pure black #000000 (default)
const val BG_DARK    = 1   // Material dark #121212
const val BG_DYNAMIC = 2   // Material You (Android 12+)


// yt-dlp update channel preference key (stored value = UpdateChannel ordinal)
const val YT_DLP_CHANNEL_KEY = "yt_dlp_channel"


// ── Preference Keys ───────────────────────────────────────────────────────────
const val EXTRACT_AUDIO = "extract_audio"
const val CREATE_THUMBNAIL = "create_thumbnail"
const val DOWNLOAD_PLAYLIST = "playlist"
const val SUBDIRECTORY_PLAYLIST = "subdirectory_playlist_title"
const val EMBED_METADATA = "embed_metadata"
const val EMBED_THUMBNAIL = "embed_thumbnail"
const val EMBED_SUBTITLE = "embed_subtitle"
const val SUBTITLE = "subtitle"
const val SUBTITLE_LANGUAGE = "sub_lang"
const val AUTO_SUBTITLE = "auto_subtitle"
const val AUTO_TRANSLATED_SUBTITLES = "translated_subs"
const val KEEP_SUBTITLE_FILES = "keep_subtitle"
const val CONVERT_SUBTITLE = "convert_subtitle"
const val SUBTITLE_FONT_SIZE = "subtitle_font_size"
const val SUBTITLE_COLOR = "subtitle_color"
const val SUBTITLE_BACKGROUND_OPACITY = "subtitle_background_opacity"
const val SUBTITLE_DELAY = "subtitle_delay"
const val SUBTITLE_BORDER_STYLE = "subtitle_border_style"
const val ARIA2C = "aria2c"
const val CONCURRENT_FRAGMENTS = "concurrent_fragments"
const val COOKIES = "cookies"
const val USER_AGENT_STRING = "user_agent_string"
const val PROXY = "proxy"
const val PROXY_URL = "proxy_url"
const val FORCE_IPV4 = "force_ipv4"
const val SPONSORBLOCK = "sponsorblock"
const val SPONSORBLOCK_CATEGORIES = "sponsorblock_categories"
const val RATE_LIMIT = "rate_limit"
const val MAX_RATE = "max_rate"
const val RESTRICT_FILENAMES = "restrict_filenames"
const val DOWNLOAD_ARCHIVE = "download_archive"
const val CROP_ARTWORK = "crop_artwork"
const val VIDEO_DIRECTORY = "download_dir"
const val AUDIO_DIRECTORY = "audio_dir"
const val AUDIO_FORMAT = "audio_format_preferred"
const val AUDIO_QUALITY = "audio_quality"
const val VIDEO_FORMAT = "video_format"
const val VIDEO_QUALITY = "quality"
const val FORMAT_SORTING = "format_sorting"
const val SORTING_FIELDS = "sorting_fields"
const val OUTPUT_TEMPLATE = "output_template"
const val CUSTOM_OUTPUT_TEMPLATE = "custom_output_template"
const val MAX_FILE_SIZE = "max_file_size"
const val YT_DLP_UPDATE_TIME = "yt-dlp_last_update"
const val YT_DLP_AUTO_UPDATE = "yt-dlp_update"
const val DARK_THEME_VALUE = "dark_theme_value"
const val DYNAMIC_COLOR = "dynamic_color"
const val PRIVATE_MODE = "private_mode"
const val NOTIFICATION = "notification"
const val CELLULAR_DOWNLOAD = "cellular_download"
const val CLIPBOARD_AUTO_PASTE = "clipboard_auto_paste"
const val CUSTOM_CMD_ARGS = "custom_cmd_args"
const val MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"

const val PREFERRED_PLAYER = "preferred_player"
const val PLAYER_IN_APP = 0
const val PLAYER_EXTERNAL = 1

const val AUTO_RETRY_ON_CONNECT = "auto_retry_on_connect"
const val ONBOARDING_COMPLETED = "onboarding_completed"



const val NOT_SPECIFIED = 0
const val DEFAULT = NOT_SPECIFIED

/**
 * Centralized preferences backed by MMKV (fast, synchronous, crash-safe).
 * All read/writes go through this object.
 */
object PreferenceUtil {

    private val kv: MMKV by lazy { MMKV.defaultMMKV() }

    // ── Typed accessors ──────────────────────────────────────────────────────

    fun String.getBoolean(default: Boolean = false): Boolean =
        kv.decodeBool(this, default)

    fun String.getInt(default: Int = NOT_SPECIFIED): Int =
        kv.decodeInt(this, default)

    fun String.getString(default: String = ""): String =
        kv.decodeString(this) ?: default

    fun String.updateBoolean(value: Boolean) = kv.encode(this, value)
    fun String.updateInt(value: Int) = kv.encode(this, value)
    fun String.updateString(value: String) = kv.encode(this, value)

    // ── Convenience properties ────────────────────────────────────────────────

    val extractAudio get() = EXTRACT_AUDIO.getBoolean()
    val createThumbnail get() = CREATE_THUMBNAIL.getBoolean()
    val downloadPlaylist get() = DOWNLOAD_PLAYLIST.getBoolean()
    val embedMetadata get() = EMBED_METADATA.getBoolean()
    val embedThumbnail get() = EMBED_THUMBNAIL.getBoolean(true)
    val embedSubtitle get() = EMBED_SUBTITLE.getBoolean()
    val subtitle get() = SUBTITLE.getBoolean()
    val subtitleLanguage get() = SUBTITLE_LANGUAGE.getString("en")
    val autoSubtitle get() = AUTO_SUBTITLE.getBoolean()
    val autoTranslatedSubtitles get() = AUTO_TRANSLATED_SUBTITLES.getBoolean()
    val keepSubtitleFiles get() = KEEP_SUBTITLE_FILES.getBoolean()
    val convertSubtitle get() = CONVERT_SUBTITLE.getInt()
    val subtitleFontSize get() = SUBTITLE_FONT_SIZE.getInt(18)
    val subtitleColor get() = SUBTITLE_COLOR.getInt(0)
    val subtitleBackgroundOpacity get() = SUBTITLE_BACKGROUND_OPACITY.getInt(50)
    val subtitleDelay get() = SUBTITLE_DELAY.getInt(0)
    val subtitleBorderStyle get() = SUBTITLE_BORDER_STYLE.getInt(1)
    val useAria2c get() = ARIA2C.getBoolean()
    val concurrentFragments get() = CONCURRENT_FRAGMENTS.getInt(1)
    val useCookies get() = COOKIES.getBoolean()
    val userAgentString get() = USER_AGENT_STRING.getString()
    val useProxy get() = PROXY.getBoolean()
    val proxyUrl get() = PROXY_URL.getString()
    val forceIpv4 get() = FORCE_IPV4.getBoolean()
    val useSponsorBlock get() = SPONSORBLOCK.getBoolean()
    val sponsorBlockCategories get() = SPONSORBLOCK_CATEGORIES.getString("sponsor")
    val useRateLimit get() = RATE_LIMIT.getBoolean()
    val maxRate get() = MAX_RATE.getString("1M")
    val restrictFilenames get() = RESTRICT_FILENAMES.getBoolean()
    val useDownloadArchive get() = DOWNLOAD_ARCHIVE.getBoolean()
    val cropArtwork get() = CROP_ARTWORK.getBoolean()
    val audioFormat get() = AUDIO_FORMAT.getInt()
    val audioQuality get() = AUDIO_QUALITY.getInt()
    val videoFormat get() = VIDEO_FORMAT.getString()
    val videoQuality get() = VIDEO_QUALITY.getInt()
    val formatSorting get() = FORMAT_SORTING.getBoolean()
    val sortingFields get() = SORTING_FIELDS.getString()
    val outputTemplate get() = OUTPUT_TEMPLATE.getString()
    val customOutputTemplate get() = CUSTOM_OUTPUT_TEMPLATE.getBoolean()
    val maxFileSize get() = MAX_FILE_SIZE.getString()
    val ytDlpAutoUpdate get() = YT_DLP_AUTO_UPDATE.getBoolean(true)
    val isDynamicColor get() = DYNAMIC_COLOR.getBoolean(true)
    val darkThemeValue get() = DARK_THEME_VALUE.getInt()
    val privateMode get() = PRIVATE_MODE.getBoolean()
    val notificationsEnabled get() = NOTIFICATION.getBoolean(true)
    val allowCellularDownload get() = CELLULAR_DOWNLOAD.getBoolean(true)
    val showGradientHeader get() = SHOW_GRADIENT_HEADER.getBoolean(false)
    val clipboardAutoPaste get() = CLIPBOARD_AUTO_PASTE.getBoolean(false)
    val customCmdArgs get() = CUSTOM_CMD_ARGS.getString()
    val maxConcurrentDownloads get() = MAX_CONCURRENT_DOWNLOADS.getInt(2)
    val preferredPlayer get() = PREFERRED_PLAYER.getInt(PLAYER_IN_APP)
    val autoRetryOnConnect get() = AUTO_RETRY_ON_CONNECT.getBoolean(true)
    val onboardingCompleted get() = ONBOARDING_COMPLETED.getBoolean(false)

    fun updateOnboardingCompleted(completed: Boolean) {
        ONBOARDING_COMPLETED.updateBoolean(completed)
    }

    // ── Pending fetch video info (share-sheet background flow) ────────────────
    fun storePendingVideoInfo(url: String, infoJson: String) {
        kv.encode("pending_video_url", url)
        kv.encode("pending_video_info_json", infoJson)
    }

    fun retrievePendingVideoInfo(): Pair<String, String>? {
        val url = kv.decodeString("pending_video_url") ?: return null
        val json = kv.decodeString("pending_video_info_json") ?: return null
        return Pair(url, json)
    }

    fun clearPendingVideoInfo() {
        kv.remove("pending_video_url")
        kv.remove("pending_video_info_json")
    }



    // ── Reactive preference flows ─────────────────────────────────────────────
    private val _accentColorFlow = MutableStateFlow(ACCENT_COLOR_KEY.getInt(0))
    val accentColorFlow = _accentColorFlow.asStateFlow()

    private val _backgroundStyleFlow = MutableStateFlow(BACKGROUND_STYLE_KEY.getInt(BG_AMOLED))
    val backgroundStyleFlow = _backgroundStyleFlow.asStateFlow()

    private val _showGradientHeaderFlow = MutableStateFlow(SHOW_GRADIENT_HEADER.getBoolean(false))
    val showGradientHeaderFlow = _showGradientHeaderFlow.asStateFlow()

    fun updateAccentColor(index: Int) {
        ACCENT_COLOR_KEY.updateInt(index)
        _accentColorFlow.value = index
    }

    fun updateBackgroundStyle(style: Int) {
        BACKGROUND_STYLE_KEY.updateInt(style)
        _backgroundStyleFlow.value = style
    }

    fun updateShowGradientHeader(show: Boolean) {
        SHOW_GRADIENT_HEADER.updateBoolean(show)
        _showGradientHeaderFlow.value = show
    }
}

