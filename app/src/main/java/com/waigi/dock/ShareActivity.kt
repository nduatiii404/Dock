package com.waigi.dock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.waigi.dock.service.DownloadService
import com.waigi.dock.ui.theme.DockTheme
import com.waigi.dock.util.DownloadPreferences
import com.waigi.dock.util.DownloadUtil
import com.waigi.dock.util.PreferenceUtil
import com.waigi.dock.util.VideoInfo
import kotlinx.serialization.json.Json

class ShareActivity : ComponentActivity() {

    companion object {
        /** Action sent by the "ready to download" notification. */
        const val ACTION_SHOW_DOWNLOAD_SHEET = "com.waigi.dock.SHOW_DOWNLOAD_SHEET"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            // Case 1: Notification tapped — show download sheet with pre-fetched data
            intent?.action == ACTION_SHOW_DOWNLOAD_SHEET -> {
                val pending = PreferenceUtil.retrievePendingVideoInfo()
                if (pending == null) {
                    Toast.makeText(this, "Download info expired. Please share the video again.", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                val (url, infoJson) = pending
                val videoInfo = runCatching {
                    json.decodeFromString<VideoInfo>(infoJson)
                }.getOrNull()
                if (videoInfo == null) {
                    Toast.makeText(this, "Failed to read video info. Please share the video again.", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
                enableEdgeToEdge()
                setContent {
                    DockTheme {
                        ShareDialogContent(
                            sharedUrl = url,
                            prefetchedVideoInfo = videoInfo,
                            onDismiss = {
                                PreferenceUtil.clearPendingVideoInfo()
                                finish()
                            },
                            onStartDownload = { u, prefs ->
                                DownloadService.startDownload(this@ShareActivity, u, prefs)
                                Toast.makeText(this@ShareActivity, "Download started", Toast.LENGTH_SHORT).show()
                                PreferenceUtil.clearPendingVideoInfo()
                                finish()
                            }
                        )
                    }
                }
            }

            // Case 2: Share intent — fetch in background and dismiss immediately
            intent?.getSharedUrl() != null -> {
                val sharedUrl = intent!!.getSharedUrl()!!
                DownloadService.startFetchInfo(this, sharedUrl)
                Toast.makeText(this, "Fetching video details\u2026", Toast.LENGTH_SHORT).show()
                finish()
            }

            // Case 3: No usable URL
            else -> finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialogContent(
    sharedUrl: String?,
    prefetchedVideoInfo: VideoInfo? = null,
    onDismiss: () -> Unit,
    onStartDownload: (String, DownloadPreferences) -> Unit
) {
    var isLoading by remember { mutableStateOf(prefetchedVideoInfo == null && !sharedUrl.isNullOrBlank()) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(prefetchedVideoInfo) }
    var error by remember { mutableStateOf<String?>(null) }

    var isAudio by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(0) }
    var downloadSubtitles by remember { mutableStateOf(false) }
    var selectedSubtitleLang by remember { mutableStateOf("") }

    // Fallback fetch path (only if no prefetched info)
    LaunchedEffect(sharedUrl) {
        if (prefetchedVideoInfo != null || sharedUrl.isNullOrBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        val result = DownloadUtil.fetchVideoInfo(sharedUrl)
        result.fold(
            onSuccess = { info ->
                videoInfo = info
                isLoading = false
            },
            onFailure = { err ->
                error = cleanErrorMessage(err.message)
                isLoading = false
            }
        )
    }

    LaunchedEffect(videoInfo) {
        videoInfo?.let { info ->
            selectedSubtitleLang = info.subtitles?.keys?.firstOrNull()
                ?: info.automatic_captions?.keys?.firstOrNull()
                ?: ""
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Dock Quick Download",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Fetching video details\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (error != null) {
                Text(
                    text = error ?: "An error occurred",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK")
                }
            } else if (videoInfo != null) {
                val info = videoInfo!!

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    if (info.thumbnail != null) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.uploader != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = info.uploader,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isAudio,
                        onClick = { isAudio = false },
                        label = { Text("Video") },
                        leadingIcon = {
                            Icon(
                                if (!isAudio) Icons.Filled.VideoFile else Icons.Outlined.VideoFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = isAudio,
                        onClick = { isAudio = true },
                        label = { Text("Audio only") },
                        leadingIcon = {
                            Icon(
                                if (isAudio) Icons.Filled.AudioFile else Icons.Outlined.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                AnimatedVisibility(visible = !isAudio) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Quality",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val availableQualities = remember(info) {
                            val heights = info.formats
                                ?.filter { it.vcodec != "none" && it.height != null && it.height > 0.0 }
                                ?.mapNotNull { it.height?.toInt() }
                                ?.toSet() ?: emptySet()

                            if (heights.isEmpty()) {
                                listOf(0 to "Best", 1 to "1080p", 2 to "720p", 3 to "480p", 4 to "360p")
                            } else {
                                val list = mutableListOf<Pair<Int, String>>()
                                list.add(0 to "Best")
                                if (heights.any { it >= 2160 }) list.add(5 to "2160p (4K)")
                                if (heights.any { it in 1440..2159 }) list.add(6 to "1440p (2K)")
                                if (heights.any { it in 1080..1439 }) list.add(1 to "1080p")
                                if (heights.any { it in 720..1079 }) list.add(2 to "720p")
                                if (heights.any { it in 480..719 }) list.add(3 to "480p")
                                if (heights.any { it in 1..479 }) list.add(4 to "360p")
                                list
                            }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableQualities.forEach { (value, label) ->
                                FilterChip(
                                    selected = selectedQuality == value,
                                    onClick = { selectedQuality = value },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = !isAudio) {
                    val subtitleLanguages = remember(info) {
                        val map = mutableMapOf<String, String>()
                        info.subtitles?.forEach { (code, list) ->
                            val name = list.firstOrNull()?.name ?: code
                            map[code] = name
                        }
                        info.automatic_captions?.forEach { (code, list) ->
                            val name = (list.firstOrNull()?.name ?: code) + " (auto)"
                            if (!map.containsKey(code)) map[code] = name
                        }
                        map.toList().sortedBy { it.second }
                    }

                    if (subtitleLanguages.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Subtitles",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = downloadSubtitles,
                                    onCheckedChange = { downloadSubtitles = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Download subtitles", style = MaterialTheme.typography.bodyMedium)
                            }

                            if (downloadSubtitles) {
                                Spacer(modifier = Modifier.height(6.dp))
                                var subtitleExpanded by remember { mutableStateOf(false) }
                                val currentLangName =
                                    subtitleLanguages.firstOrNull { it.first == selectedSubtitleLang }?.second
                                        ?: selectedSubtitleLang

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { subtitleExpanded = true }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentLangName.ifEmpty { "Select language" },
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                    }

                                    DropdownMenu(
                                        expanded = subtitleExpanded,
                                        onDismissRequest = { subtitleExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        subtitleLanguages.forEach { (code, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    selectedSubtitleLang = code
                                                    subtitleExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            val prefs = DownloadPreferences.createFromPreferences().copy(
                                extractAudio = isAudio,
                                videoQuality = selectedQuality,
                                writeSubtitle = downloadSubtitles,
                                embedSubtitle = downloadSubtitles,
                                subtitleLanguage = selectedSubtitleLang
                            )
                            if (sharedUrl != null) {
                                onStartDownload(sharedUrl, prefs)
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
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
