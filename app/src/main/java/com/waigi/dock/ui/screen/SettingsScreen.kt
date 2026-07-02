package com.waigi.dock.ui.screen

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Warning
import androidx.activity.compose.BackHandler
import com.tencent.mmkv.MMKV
import com.waigi.dock.ui.theme.ACCENT_TANGERINE
import com.waigi.dock.ui.theme.accentPalettes
import com.waigi.dock.util.ACCENT_COLOR_KEY
import com.waigi.dock.util.BG_AMOLED
import com.waigi.dock.util.BG_DARK
import com.waigi.dock.util.BG_DYNAMIC
import com.waigi.dock.util.BACKGROUND_STYLE_KEY
import com.waigi.dock.util.PreferenceUtil.getInt
import com.waigi.dock.util.PreferenceUtil.updateInt
import com.waigi.dock.util.PreferenceUtil.getBoolean
import com.waigi.dock.util.PreferenceUtil.updateBoolean
import com.waigi.dock.util.PreferenceUtil.getString
import com.waigi.dock.util.PreferenceUtil.updateString
import com.waigi.dock.util.SHOW_GRADIENT_HEADER
import com.waigi.dock.util.YT_DLP_CHANNEL_KEY
import com.waigi.dock.util.YoutubeDLUpdater
import com.waigi.dock.util.COOKIES
import com.waigi.dock.util.DOWNLOAD_PLAYLIST
import com.waigi.dock.util.SUBDIRECTORY_PLAYLIST
import com.waigi.dock.util.DOWNLOAD_ARCHIVE
import com.waigi.dock.util.RESTRICT_FILENAMES
import com.waigi.dock.util.CELLULAR_DOWNLOAD
import com.waigi.dock.util.EMBED_METADATA
import com.waigi.dock.util.EMBED_THUMBNAIL
import com.waigi.dock.util.SPONSORBLOCK
import com.waigi.dock.util.CROP_ARTWORK
import com.waigi.dock.util.EMBED_SUBTITLE
import com.waigi.dock.util.SUBTITLE
import com.waigi.dock.util.AUTO_SUBTITLE
import com.waigi.dock.util.SUBTITLE_LANGUAGE
import com.waigi.dock.util.KEEP_SUBTITLE_FILES
import com.waigi.dock.util.AUTO_TRANSLATED_SUBTITLES
import com.waigi.dock.util.CONVERT_SUBTITLE
import com.waigi.dock.util.ARIA2C
import com.waigi.dock.util.CONCURRENT_FRAGMENTS
import com.waigi.dock.util.PROXY
import com.waigi.dock.util.PROXY_URL
import com.waigi.dock.util.RATE_LIMIT
import com.waigi.dock.util.MAX_RATE
import com.waigi.dock.util.FORCE_IPV4
import com.waigi.dock.util.PRIVATE_MODE
import com.waigi.dock.util.NOTIFICATION
import com.waigi.dock.util.YT_DLP_AUTO_UPDATE
import com.waigi.dock.util.OUTPUT_TEMPLATE
import com.waigi.dock.util.USER_AGENT_STRING
import com.waigi.dock.util.SPONSORBLOCK_CATEGORIES
import com.waigi.dock.util.NOT_SPECIFIED
import com.waigi.dock.util.VIDEO_QUALITY
import com.waigi.dock.util.AUDIO_FORMAT
import com.waigi.dock.util.AUDIO_QUALITY
import com.waigi.dock.util.VIDEO_DIRECTORY
import com.waigi.dock.util.AUDIO_DIRECTORY
import com.waigi.dock.util.CLIPBOARD_AUTO_PASTE
import com.waigi.dock.util.CUSTOM_CMD_ARGS
import com.waigi.dock.util.MAX_CONCURRENT_DOWNLOADS
import com.waigi.dock.util.PREFERRED_PLAYER
import com.waigi.dock.util.PLAYER_IN_APP
import com.waigi.dock.util.PLAYER_EXTERNAL
import com.waigi.dock.util.AUTO_RETRY_ON_CONNECT
import com.waigi.dock.util.SUBTITLE_FONT_SIZE
import com.waigi.dock.util.SUBTITLE_COLOR
import com.waigi.dock.util.SUBTITLE_BACKGROUND_OPACITY
import com.waigi.dock.util.SUBTITLE_DELAY
import com.waigi.dock.util.SUBTITLE_BORDER_STYLE
import com.waigi.dock.util.FileUtil
import android.widget.Toast

import kotlinx.coroutines.launch

enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    APPEARANCE("Appearance", "Theme, accent color, and header banner", Icons.Outlined.Palette),
    GENERAL_DOWNLOADS("General Downloads", "Playlists, archives, naming, and cell-data options", Icons.Outlined.Folder),
    POST_PROCESSING("Post-Processing", "Metadata, thumbnails, SponsorBlock categories", Icons.Outlined.Tune),
    SUBTITLES("Subtitles", "Subtitle downloads, languages, and embedding", Icons.Outlined.Subtitles),
    NETWORK_SPEED("Network & Speed", "Parallel thread fragments, proxies, speed limits, user-agents", Icons.Outlined.Speed),
    COOKIES_ENGINE("Cookies & Engine", "Cookies importing, yt-dlp update channel and startup checks", Icons.Outlined.Cookie),
    ABOUT("About", "App versions and engine info", Icons.Outlined.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kv = MMKV.defaultMMKV()

    val ytDlpVersion by YoutubeDLUpdater.ytDlpVersion.collectAsState()
    val updateState  by YoutubeDLUpdater.updateState.collectAsState()

    val accentIndex by com.waigi.dock.util.PreferenceUtil.accentColorFlow.collectAsState()
    val bgStyle by com.waigi.dock.util.PreferenceUtil.backgroundStyleFlow.collectAsState()
    val showGradientHeader by com.waigi.dock.util.PreferenceUtil.showGradientHeaderFlow.collectAsState()
    // 0 = STABLE, 1 = NIGHTLY
    var ytDlpChannel by remember { mutableIntStateOf(YT_DLP_CHANNEL_KEY.getInt(1)) }
    var ytDlpAutoUpdate by remember { mutableStateOf(YT_DLP_AUTO_UPDATE.getBoolean(true)) }

    // General Downloads
    var downloadPlaylist by remember { mutableStateOf(DOWNLOAD_PLAYLIST.getBoolean()) }
    var subdirectoryPlaylist by remember { mutableStateOf(SUBDIRECTORY_PLAYLIST.getBoolean()) }
    var useDownloadArchive by remember { mutableStateOf(DOWNLOAD_ARCHIVE.getBoolean()) }
    var restrictFilenames by remember { mutableStateOf(RESTRICT_FILENAMES.getBoolean()) }
    var allowCellularDownload by remember { mutableStateOf(CELLULAR_DOWNLOAD.getBoolean(true)) }
    var privateMode by remember { mutableStateOf(PRIVATE_MODE.getBoolean()) }
    var notificationsEnabled by remember { mutableStateOf(NOTIFICATION.getBoolean(true)) }
    var outputTemplate by remember { mutableStateOf(OUTPUT_TEMPLATE.getString()) }

    // Post-Processing
    var embedMetadata by remember { mutableStateOf(EMBED_METADATA.getBoolean()) }
    var embedThumbnail by remember { mutableStateOf(EMBED_THUMBNAIL.getBoolean(true)) }
    var useSponsorBlock by remember { mutableStateOf(SPONSORBLOCK.getBoolean()) }
    var sponsorBlockCategories by remember { mutableStateOf(SPONSORBLOCK_CATEGORIES.getString("sponsor")) }
    var cropArtwork by remember { mutableStateOf(CROP_ARTWORK.getBoolean()) }

    // Subtitles
    var subtitle by remember { mutableStateOf(SUBTITLE.getBoolean()) }
    var embedSubtitle by remember { mutableStateOf(EMBED_SUBTITLE.getBoolean()) }
    var autoSubtitle by remember { mutableStateOf(AUTO_SUBTITLE.getBoolean()) }
    var subtitleLanguage by remember { mutableStateOf(SUBTITLE_LANGUAGE.getString("en")) }
    var keepSubtitleFiles by remember { mutableStateOf(KEEP_SUBTITLE_FILES.getBoolean()) }
    var autoTranslatedSubtitles by remember { mutableStateOf(AUTO_TRANSLATED_SUBTITLES.getBoolean()) }
    var convertSubtitleFormat by remember { mutableIntStateOf(CONVERT_SUBTITLE.getInt(0)) }
    var subtitleFontSizePreference by remember { mutableIntStateOf(com.waigi.dock.util.PreferenceUtil.subtitleFontSize) }
    var subtitleColorPreference by remember { mutableIntStateOf(com.waigi.dock.util.PreferenceUtil.subtitleColor) }
    var subtitleBgOpacityPreference by remember { mutableIntStateOf(com.waigi.dock.util.PreferenceUtil.subtitleBackgroundOpacity) }
    var subtitleDelayPreference by remember { mutableIntStateOf(com.waigi.dock.util.PreferenceUtil.subtitleDelay) }
    var subtitleBorderStylePreference by remember { mutableIntStateOf(com.waigi.dock.util.PreferenceUtil.subtitleBorderStyle) }

    // Network & Speed
    var useAria2c by remember { mutableStateOf(ARIA2C.getBoolean()) }
    var concurrentFragments by remember { mutableIntStateOf(CONCURRENT_FRAGMENTS.getInt(1)) }
    var useProxy by remember { mutableStateOf(PROXY.getBoolean()) }
    var proxyUrl by remember { mutableStateOf(PROXY_URL.getString()) }
    var useRateLimit by remember { mutableStateOf(RATE_LIMIT.getBoolean()) }
    var maxRate by remember { mutableStateOf(MAX_RATE.getString("1M")) }
    var forceIpv4 by remember { mutableStateOf(FORCE_IPV4.getBoolean()) }
    var userAgentString by remember { mutableStateOf(USER_AGENT_STRING.getString()) }

    // New Preferences States
    var videoQualityPreference by remember { mutableIntStateOf(VIDEO_QUALITY.getInt(NOT_SPECIFIED)) }
    var audioFormatPreference by remember { mutableIntStateOf(AUDIO_FORMAT.getInt(NOT_SPECIFIED)) }
    var audioQualityPreference by remember { mutableIntStateOf(AUDIO_QUALITY.getInt(NOT_SPECIFIED)) }
    var videoDownloadDirPreference by remember { mutableStateOf(VIDEO_DIRECTORY.getString()) }
    var audioDownloadDirPreference by remember { mutableStateOf(AUDIO_DIRECTORY.getString()) }
    var clipboardAutoPastePreference by remember { mutableStateOf(CLIPBOARD_AUTO_PASTE.getBoolean(false)) }
    var customCmdArgsPreference by remember { mutableStateOf(CUSTOM_CMD_ARGS.getString()) }
    var maxConcurrentDownloadsPreference by remember { mutableIntStateOf(MAX_CONCURRENT_DOWNLOADS.getInt(2)) }
    var preferredPlayerPreference by remember { mutableIntStateOf(PREFERRED_PLAYER.getInt(PLAYER_IN_APP)) }

    // New Storage Cleanup & Retry Preference states
    var autoRetryPreference by remember { mutableStateOf(AUTO_RETRY_ON_CONNECT.getBoolean(true)) }
    var isBatteryOptimized by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == false
            } else {
                false
            }
        )
    }
    var videoSize by remember { mutableStateOf("0 B") }
    var audioSize by remember { mutableStateOf("0 B") }
    var tempSize by remember { mutableStateOf("0 B") }
    var showCleanVideoDialog by remember { mutableStateOf(false) }
    var showCleanAudioDialog by remember { mutableStateOf(false) }
    var showCleanTempDialog by remember { mutableStateOf(false) }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(java.util.Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun updateSizes() {
        videoSize = formatSize(FileUtil.getFolderSize(FileUtil.videoDownloadDir))
        audioSize = formatSize(FileUtil.getFolderSize(FileUtil.audioDownloadDir))
        tempSize = formatSize(FileUtil.tempDirSize)
    }

    var currentCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var useCookies by remember { mutableStateOf(COOKIES.getBoolean()) }
    var cookiesExist by remember { mutableStateOf(com.waigi.dock.util.FileUtil.getCookiesFile().exists() && com.waigi.dock.util.FileUtil.getCookiesFile().length() > 0) }

    androidx.compose.runtime.LaunchedEffect(currentCategory) {
        if (currentCategory == SettingsCategory.GENERAL_DOWNLOADS) {
            updateSizes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                isBatteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == false
            } else {
                isBatteryOptimized = false
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, currentCategory) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (currentCategory == SettingsCategory.GENERAL_DOWNLOADS) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                        isBatteryOptimized = pm?.isIgnoringBatteryOptimizations(context.packageName) == false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showCleanVideoDialog) {
        AlertDialog(
            onDismissRequest = { showCleanVideoDialog = false },
            title = { Text("Delete Video Files?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all downloaded files in your video folder. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanVideoDialog = false
                        FileUtil.cleanFolder(FileUtil.videoDownloadDir)
                        updateSizes()
                        Toast.makeText(context, "Video downloads cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanVideoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCleanAudioDialog) {
        AlertDialog(
            onDismissRequest = { showCleanAudioDialog = false },
            title = { Text("Delete Audio Files?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all downloaded files in your audio folder. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanAudioDialog = false
                        FileUtil.cleanFolder(FileUtil.audioDownloadDir)
                        updateSizes()
                        Toast.makeText(context, "Audio downloads cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanAudioDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCleanTempDialog) {
        AlertDialog(
            onDismissRequest = { showCleanTempDialog = false },
            title = { Text("Clear Temporary Cache?", fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all temporary files and caches. Active downloads might be interrupted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanTempDialog = false
                        FileUtil.cleanTempDir()
                        updateSizes()
                        Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanTempDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BackHandler(enabled = currentCategory != null) {
        currentCategory = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = currentCategory?.title ?: "Settings", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    if (currentCategory != null) {
                        IconButton(onClick = { currentCategory = null }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (currentCategory == null) 20.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val category = currentCategory
            if (category == null) {
                SettingsCategory.entries.forEachIndexed { index, cat ->
                    CategoryRow(
                        category = cat,
                        onClick = { currentCategory = cat }
                    )
                    if (index < SettingsCategory.entries.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                when (category) {
                    SettingsCategory.APPEARANCE -> {
                        SettingsSectionHeader("Appearance")
                        SettingsLabel("Accent color")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            accentPalettes.forEachIndexed { index, palette ->
                                AccentColorSwatch(
                                    color = palette.main,
                                    label = palette.name,
                                    selected = accentIndex == index,
                                    onClick = { com.waigi.dock.util.PreferenceUtil.updateAccentColor(index) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsLabel("Background style")
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            BgStyleRow(
                                label = "AMOLED Black",
                                sublabel = "Pure black — best for OLED screens",
                                selected = bgStyle == BG_AMOLED,
                                onClick = { com.waigi.dock.util.PreferenceUtil.updateBackgroundStyle(BG_AMOLED) },
                            )
                            BgStyleRow(
                                label = "Dark",
                                sublabel = "Material dark mode",
                                selected = bgStyle == BG_DARK,
                                onClick = { com.waigi.dock.util.PreferenceUtil.updateBackgroundStyle(BG_DARK) },
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                BgStyleRow(
                                    label = "Dynamic (Material You)",
                                    sublabel = "Uses your wallpaper colors (Android 12+)",
                                    selected = bgStyle == BG_DYNAMIC,
                                    onClick = { com.waigi.dock.util.PreferenceUtil.updateBackgroundStyle(BG_DYNAMIC) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsLabel("Home screen banner")
                        Spacer(modifier = Modifier.height(8.dp))
                        SwitchRow(
                            label = "Gradient Header",
                            sublabel = "Vibrant gradient banner on home screen",
                            checked = showGradientHeader,
                            onCheckedChange = { com.waigi.dock.util.PreferenceUtil.updateShowGradientHeader(it) }
                        )
                    }
                    SettingsCategory.GENERAL_DOWNLOADS -> {
                        SettingsSectionHeader("General Downloads")
                        if (isBatteryOptimized) {
                            BatteryOptimizationWarningCard(
                                onResumeClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(fallbackIntent)
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        SwitchRow(
                            label = "Download Playlists",
                            sublabel = "Download all videos in a playlist if a playlist link is shared",
                            checked = downloadPlaylist,
                            onCheckedChange = { downloadPlaylist = it; DOWNLOAD_PLAYLIST.updateBoolean(it) }
                        )
                        if (downloadPlaylist) {
                            SwitchRow(
                                label = "Group Playlists in Subdirectories",
                                sublabel = "Save playlist videos in a dedicated directory matching the playlist name",
                                checked = subdirectoryPlaylist,
                                onCheckedChange = { subdirectoryPlaylist = it; SUBDIRECTORY_PLAYLIST.updateBoolean(it) }
                            )
                        }
                        SwitchRow(
                            label = "Use Download Archive",
                            sublabel = "Record downloaded videos in an archive file to skip duplicates in future downloads",
                            checked = useDownloadArchive,
                            onCheckedChange = { useDownloadArchive = it; DOWNLOAD_ARCHIVE.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "Restrict Filenames",
                            sublabel = "Restrict filenames to ASCII characters and remove spaces/special characters",
                            checked = restrictFilenames,
                            onCheckedChange = { restrictFilenames = it; RESTRICT_FILENAMES.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "Allow Cellular Downloads",
                            sublabel = "Allow downloading content over cellular networks",
                            checked = allowCellularDownload,
                            onCheckedChange = { allowCellularDownload = it; CELLULAR_DOWNLOAD.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "Private Mode",
                            sublabel = "Prevent recording downloaded items in the app history",
                            checked = privateMode,
                            onCheckedChange = { privateMode = it; PRIVATE_MODE.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "Show Notifications",
                            sublabel = "Display progress and completion notifications",
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it; NOTIFICATION.updateBoolean(it) }
                        )
                        InputFieldRow(
                            label = "Filename Output Template",
                            value = outputTemplate,
                            onValueChange = { outputTemplate = it; OUTPUT_TEMPLATE.updateString(it) },
                            placeholder = "e.g. %(title)s.%(ext)s"
                        )
                        SwitchRow(
                            label = "Auto-Paste Link on Launch",
                            sublabel = "Automatically paste and query links from the clipboard when starting the app",
                            checked = clipboardAutoPastePreference,
                            onCheckedChange = { clipboardAutoPastePreference = it; CLIPBOARD_AUTO_PASTE.updateBoolean(it) }
                        )
                        NumberPickerRow(
                            label = "Max Concurrent Downloads",
                            sublabel = "Maximum number of parallel downloads allowed at the same time",
                            value = maxConcurrentDownloadsPreference,
                            range = 1..10,
                            onValueChange = { maxConcurrentDownloadsPreference = it; MAX_CONCURRENT_DOWNLOADS.updateInt(it) }
                        )
                        SwitchRow(
                            label = "Auto-Retry on Connection Restored",
                            sublabel = "Automatically retry downloads in error states when network becomes available",
                            checked = autoRetryPreference,
                            onCheckedChange = { autoRetryPreference = it; AUTO_RETRY_ON_CONNECT.updateBoolean(it) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionHeader("Media Player Settings")
                        SettingsLabel("Default Media Player")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "In-App Player" to PLAYER_IN_APP,
                                "System Default / External" to PLAYER_EXTERNAL
                            ).forEach { (label, value) ->
                                FilterChip(
                                    selected = preferredPlayerPreference == value,
                                    onClick = { preferredPlayerPreference = value; PREFERRED_PLAYER.updateInt(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionHeader("Default Media Preferences")
                        SettingsLabel("Default Video Quality")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Always Ask" to NOT_SPECIFIED,
                                "Best" to 0,
                                "4K (2160p)" to 5,
                                "2K (1440p)" to 6,
                                "1080p" to 1,
                                "720p" to 2,
                                "480p" to 3,
                                "360p" to 4
                            ).forEach { (label, value) ->
                                FilterChip(
                                    selected = videoQualityPreference == value,
                                    onClick = { videoQualityPreference = value; VIDEO_QUALITY.updateInt(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsLabel("Default Audio Format")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Default/Best" to NOT_SPECIFIED,
                                "MP3" to 1,
                                "M4A" to 2,
                                "Opus" to 3,
                                "FLAC" to 5,
                                "AAC" to 6,
                                "WAV" to 4
                            ).forEach { (label, value) ->
                                FilterChip(
                                    selected = audioFormatPreference == value,
                                    onClick = { audioFormatPreference = value; AUDIO_FORMAT.updateInt(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsLabel("Default Audio Quality / Bitrate")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Default/Best" to NOT_SPECIFIED,
                                "320 kbps (HQ)" to 320,
                                "256 kbps" to 256,
                                "192 kbps" to 192,
                                "128 kbps (LQ)" to 128
                            ).forEach { (label, value) ->
                                FilterChip(
                                    selected = audioQualityPreference == value,
                                    onClick = { audioQualityPreference = value; AUDIO_QUALITY.updateInt(value) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionHeader("Storage Folders")
                        InputFieldRow(
                            label = "Video Download Folder (Absolute Path)",
                            value = videoDownloadDirPreference,
                            onValueChange = { videoDownloadDirPreference = it; VIDEO_DIRECTORY.updateString(it) },
                            placeholder = FileUtil.defaultVideoDir.absolutePath
                        )
                        InputFieldRow(
                            label = "Audio Download Folder (Absolute Path)",
                            value = audioDownloadDirPreference,
                            onValueChange = { audioDownloadDirPreference = it; AUDIO_DIRECTORY.updateString(it) },
                            placeholder = FileUtil.defaultAudioDir.absolutePath
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionHeader("Storage & Cleanup")
                        CleanupRow(
                            title = "Video Downloads",
                            sizeText = videoSize,
                            onClean = { showCleanVideoDialog = true }
                        )
                        CleanupRow(
                            title = "Audio Downloads",
                            sizeText = audioSize,
                            onClean = { showCleanAudioDialog = true }
                        )
                        CleanupRow(
                            title = "Temporary Cache Files",
                            sizeText = tempSize,
                            onClean = { showCleanTempDialog = true }
                        )
                    }
                    SettingsCategory.POST_PROCESSING -> {
                        SettingsSectionHeader("Post-Processing")
                        SwitchRow(
                            label = "Embed Metadata",
                            sublabel = "Embed description, uploader, title, and release information into files",
                            checked = embedMetadata,
                            onCheckedChange = { embedMetadata = it; EMBED_METADATA.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "Embed Thumbnail",
                            sublabel = "Embed video thumbnail as album/cover artwork in downloaded files",
                            checked = embedThumbnail,
                            onCheckedChange = { embedThumbnail = it; EMBED_THUMBNAIL.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "SponsorBlock integration",
                            sublabel = "Automatically skip sponsors, intros, and other non-music segments",
                            checked = useSponsorBlock,
                            onCheckedChange = { useSponsorBlock = it; SPONSORBLOCK.updateBoolean(it) }
                        )
                        if (useSponsorBlock) {
                            InputFieldRow(
                                label = "SponsorBlock Categories",
                                value = sponsorBlockCategories,
                                onValueChange = { sponsorBlockCategories = it; SPONSORBLOCK_CATEGORIES.updateString(it) },
                                placeholder = "e.g. sponsor,intro,outro,selfpromo,preview,filler"
                            )
                        }
                        SwitchRow(
                            label = "Crop Album Artwork",
                            sublabel = "Crop artwork to square shape (ideal for audio track album art)",
                            checked = cropArtwork,
                            onCheckedChange = { cropArtwork = it; CROP_ARTWORK.updateBoolean(it) }
                        )
                    }
                    SettingsCategory.SUBTITLES -> {
                        SettingsSectionHeader("Subtitles")
                        SwitchRow(
                            label = "Download Subtitles",
                            sublabel = "Download subtitle files along with videos",
                            checked = subtitle,
                            onCheckedChange = { subtitle = it; SUBTITLE.updateBoolean(it) }
                        )
                        if (subtitle) {
                            SwitchRow(
                                label = "Embed Subtitles",
                                sublabel = "Embed downloaded subtitles inside the video file (e.g. MKV/MP4)",
                                checked = embedSubtitle,
                                onCheckedChange = { embedSubtitle = it; EMBED_SUBTITLE.updateBoolean(it) }
                            )
                            SwitchRow(
                                label = "Download Auto-Generated Subtitles",
                                sublabel = "Fallback to auto-generated subtitles if uploader-provided ones are missing",
                                checked = autoSubtitle,
                                onCheckedChange = { autoSubtitle = it; AUTO_SUBTITLE.updateBoolean(it) }
                            )
                            SwitchRow(
                                label = "Auto-Translate Subtitles",
                                sublabel = "Auto-translate auto-generated subtitles to preferred language",
                                checked = autoTranslatedSubtitles,
                                onCheckedChange = { autoTranslatedSubtitles = it; AUTO_TRANSLATED_SUBTITLES.updateBoolean(it) }
                            )
                            SwitchRow(
                                label = "Keep Subtitle Files",
                                sublabel = "Do not delete separate subtitle files after downloading or embedding",
                                checked = keepSubtitleFiles,
                                onCheckedChange = { keepSubtitleFiles = it; KEEP_SUBTITLE_FILES.updateBoolean(it) }
                            )
                            InputFieldRow(
                                label = "Subtitle Language",
                                value = subtitleLanguage,
                                onValueChange = { subtitleLanguage = it; SUBTITLE_LANGUAGE.updateString(it) },
                                placeholder = "e.g., en, es, fr"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsLabel("Convert Subtitle Format")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "No Conversion" to 0,
                                    "SRT" to 1,
                                    "VTT" to 2,
                                    "ASS" to 3,
                                    "LRC" to 4
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = convertSubtitleFormat == value,
                                        onClick = { convertSubtitleFormat = value; CONVERT_SUBTITLE.updateInt(value) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            SettingsSectionHeader("In-App Player Subtitle Style")

                            SettingsLabel("Subtitle Font Size")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Small (14sp)" to 14,
                                    "Medium (18sp)" to 18,
                                    "Large (22sp)" to 22,
                                    "Extra Large (28sp)" to 28
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = subtitleFontSizePreference == value,
                                        onClick = { subtitleFontSizePreference = value; SUBTITLE_FONT_SIZE.updateInt(value) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsLabel("Subtitle Text Color")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "White" to 0,
                                    "Yellow" to 1,
                                    "Light Cyan" to 2,
                                    "Green" to 3
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = subtitleColorPreference == value,
                                        onClick = { subtitleColorPreference = value; SUBTITLE_COLOR.updateInt(value) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsLabel("Subtitle Background Opacity")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Transparent (0%)" to 0,
                                    "Translucent (25%)" to 25,
                                    "Semi-Transparent (50%)" to 50,
                                    "Dark (75%)" to 75,
                                    "Solid Black (100%)" to 100
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = subtitleBgOpacityPreference == value,
                                        onClick = { subtitleBgOpacityPreference = value; SUBTITLE_BACKGROUND_OPACITY.updateInt(value) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            SettingsLabel("Subtitle Border Style")
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "None" to 0,
                                    "Outline" to 1,
                                    "Drop Shadow" to 2,
                                    "Raised" to 3
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = subtitleBorderStylePreference == value,
                                        onClick = { subtitleBorderStylePreference = value; SUBTITLE_BORDER_STYLE.updateInt(value) },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            NumberPickerRow(
                                label = "Default Subtitle Delay (x100ms)",
                                sublabel = "Default timing offset for subtitles (e.g. +5 is +500ms)",
                                value = subtitleDelayPreference / 100,
                                range = -50..50,
                                onValueChange = { subtitleDelayPreference = it * 100; SUBTITLE_DELAY.updateInt(it * 100) }
                            )
                        }
                    }
                    SettingsCategory.NETWORK_SPEED -> {
                        SettingsSectionHeader("Network & Speed")
                        SwitchRow(
                            label = "Aria2c Downloader",
                            sublabel = "Use aria2c for multi-threaded fragment downloads to speed up downloading",
                            checked = useAria2c,
                            onCheckedChange = { useAria2c = it; ARIA2C.updateBoolean(it) }
                        )
                        if (useAria2c) {
                            NumberPickerRow(
                                label = "Concurrent fragments",
                                sublabel = "Number of parallel connections/threads for each download",
                                value = concurrentFragments,
                                range = 1..16,
                                onValueChange = { concurrentFragments = it; CONCURRENT_FRAGMENTS.updateInt(it) }
                            )
                        }
                        SwitchRow(
                            label = "Force IPv4",
                            sublabel = "Force using IPv4 network connections to bypass IPv6 CDN or ISP issues",
                            checked = forceIpv4,
                            onCheckedChange = { forceIpv4 = it; FORCE_IPV4.updateBoolean(it) }
                        )
                        SwitchRow(
                            label = "Use HTTP Proxy",
                            sublabel = "Route all downloader network requests through a custom proxy server",
                            checked = useProxy,
                            onCheckedChange = { useProxy = it; PROXY.updateBoolean(it) }
                        )
                        if (useProxy) {
                            InputFieldRow(
                                label = "Proxy URL",
                                value = proxyUrl,
                                onValueChange = { proxyUrl = it; PROXY_URL.updateString(it) },
                                placeholder = "e.g. http://127.0.0.1:8080 or socks5://user:pass@127.0.0.1:1080"
                            )
                        }
                        SwitchRow(
                            label = "Speed Limiter",
                            sublabel = "Restrict the maximum download rate to prevent network congestion",
                            checked = useRateLimit,
                            onCheckedChange = { useRateLimit = it; RATE_LIMIT.updateBoolean(it) }
                        )
                        if (useRateLimit) {
                            InputFieldRow(
                                label = "Maximum Download Rate",
                                value = maxRate,
                                onValueChange = { maxRate = it; MAX_RATE.updateString(it) },
                                placeholder = "e.g. 50K, 1M, 10M"
                            )
                        }
                        InputFieldRow(
                            label = "Custom User-Agent",
                            value = userAgentString,
                            onValueChange = { userAgentString = it; USER_AGENT_STRING.updateString(it) },
                            placeholder = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ..."
                        )
                        InputFieldRow(
                            label = "Custom yt-dlp Arguments (Power User)",
                            value = customCmdArgsPreference,
                            onValueChange = { customCmdArgsPreference = it; CUSTOM_CMD_ARGS.updateString(it) },
                            placeholder = "e.g. --referer https://site.com --geo-bypass"
                        )
                    }
                    SettingsCategory.COOKIES_ENGINE -> {
                        SettingsSectionHeader("Cookies")
                        SwitchRow(
                            label = "Use cookies",
                            sublabel = "Use imported cookies for restricted websites",
                            checked = useCookies,
                            onCheckedChange = { useCookies = it; COOKIES.updateBoolean(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri: android.net.Uri? ->
                            if (uri != null) {
                                scope.launch {
                                    runCatching {
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            val cookiesFile = com.waigi.dock.util.FileUtil.getCookiesFile()
                                            cookiesFile.outputStream().use { output -> input.copyTo(output) }
                                        }
                                    }.fold(
                                        onSuccess = { cookiesExist = true; Toast.makeText(context, "Cookies imported successfully", Toast.LENGTH_SHORT).show() },
                                        onFailure = { err -> Toast.makeText(context, "Failed to import cookies: ${err.message}", Toast.LENGTH_LONG).show() }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch("text/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            ) {
                                Text(if (cookiesExist) "Update cookies" else "Import cookies.txt")
                            }
                            if (cookiesExist) {
                                Button(
                                    onClick = {
                                        val cookiesFile = com.waigi.dock.util.FileUtil.getCookiesFile()
                                        if (cookiesFile.exists()) { cookiesFile.delete() }
                                        cookiesExist = false
                                        useCookies = false
                                        COOKIES.updateBoolean(false)
                                        Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                ) {
                                    Text("Clear cookies")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsSectionHeader("Download Engine")
                        SettingsInfoRow("yt-dlp version", ytDlpVersion)
                        Spacer(modifier = Modifier.height(8.dp))
                        SwitchRow(
                            label = "Auto-Update yt-dlp",
                            sublabel = "Check and update the download engine on app startup",
                            checked = ytDlpAutoUpdate,
                            onCheckedChange = { ytDlpAutoUpdate = it; YT_DLP_AUTO_UPDATE.updateBoolean(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsLabel("Update channel")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Stable" to 0, "Nightly" to 1).forEach { (label, idx) ->
                                FilterChip(
                                    selected = ytDlpChannel == idx,
                                    onClick  = { ytDlpChannel = idx; YT_DLP_CHANNEL_KEY.updateInt(idx) },
                                    label    = { Text(label) },
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                        val statusText = when (val s = updateState) {
                            is YoutubeDLUpdater.UpdateState.Checking       -> "Checking for updates…"
                            is YoutubeDLUpdater.UpdateState.Success        -> "Updated to ${s.newVersion}"
                            is YoutubeDLUpdater.UpdateState.AlreadyUpToDate -> "Already up to date"
                            is YoutubeDLUpdater.UpdateState.Error          -> "Error: ${s.message}"
                            else -> ""
                        }
                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val channel = if (ytDlpChannel == 1)
                                        com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.NIGHTLY
                                    else
                                        com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE
                                    YoutubeDLUpdater.updateYtDlp(context, channel)
                                }
                            },
                            enabled = updateState !is YoutubeDLUpdater.UpdateState.Checking,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Outlined.Update, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Update yt-dlp")
                        }
                    }
                    SettingsCategory.ABOUT -> {
                        SettingsSectionHeader("About")
                        SettingsInfoRow("App", "Dock")
                        SettingsInfoRow("Version", "1.0.0")
                        SettingsInfoRow("Engine", "yt-dlp via youtubedl-android")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showPrivacyPolicy = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        ) {
                            Text("View Privacy Policy")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (showPrivacyPolicy) {
            AlertDialog(
                onDismissRequest = { showPrivacyPolicy = false },
                title = { Text("Privacy Policy", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Dock is built as a local-first application. We care deeply about your privacy:\n\n" +
                                   "1. Local Storage:\n" +
                                   "All your download settings, options, and history are stored directly on your device. We do not run any remote database or track your app usage.\n\n" +
                                   "2. Content Processing:\n" +
                                   "When you submit a link to download, standard network queries are sent directly to the corresponding media platforms (e.g. YouTube, TikTok, Instagram). These platforms process the requests according to their own privacy terms.\n\n" +
                                   "3. No Trackers or Ads:\n" +
                                   "This application does not contain any analytic scripts, advertisements, background tracking, or third-party monitoring code.\n\n" +
                                   "4. Open & Offline:\n" +
                                   "You can run this application entirely offline or via standard local tools with complete transparency.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrivacyPolicy = false }) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun AccentColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BgStyleRow(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(sublabel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            if (sublabel.isNotEmpty()) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        )
    }
}

@Composable
private fun NumberPickerRow(
    label: String,
    sublabel: String,
    value: Int,
    range: IntRange = 1..16,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            if (sublabel.isNotEmpty()) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { if (value > range.first) onValueChange(value - 1) },
                enabled = value > range.first
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = if (value > range.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = { if (value < range.last) onValueChange(value + 1) },
                enabled = value < range.last
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = if (value < range.last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun InputFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CleanupRow(
    title: String,
    sizeText: String,
    onClean: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text("Total size: $sizeText", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = onClean,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun BatteryOptimizationWarningCard(onResumeClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x11FF5252))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Battery Optimization Enabled",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Downloads running in the background may be stopped by the system. Exclude the app to ensure uninterrupted downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onResumeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Disable Optimization", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

