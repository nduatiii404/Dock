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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waigi.dock.service.DownloadService
import com.waigi.dock.ui.theme.DockTheme
import com.waigi.dock.util.DownloadPreferences

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val sharedUrl = intent?.getSharedUrl()
        if (sharedUrl.isNullOrBlank()) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            DockTheme {
                ShareDialogContent(
                    sharedUrl = sharedUrl,
                    onDismiss = { finish() },
                    onStartDownload = { url, prefs ->
                        DownloadService.startDownload(this@ShareActivity, url, prefs)
                        Toast.makeText(this@ShareActivity, "Fetching video details\u2026", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialogContent(
    sharedUrl: String,
    onDismiss: () -> Unit,
    onStartDownload: (String, DownloadPreferences) -> Unit
) {
    val defaultPrefs = remember { DownloadPreferences.createFromPreferences() }
    var isAudio by remember { mutableStateOf(defaultPrefs.extractAudio) }
    var selectedQuality by remember { mutableStateOf(defaultPrefs.videoQuality) }
    var downloadSubtitles by remember { mutableStateOf(defaultPrefs.writeSubtitle || defaultPrefs.embedSubtitle) }
    var selectedSubtitleLang by remember { mutableStateOf(defaultPrefs.subtitleLanguage.ifEmpty { "en" }) }
    var embedThumbnail by remember { mutableStateOf(defaultPrefs.embedThumbnail) }
    var subtitleExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Static quality options - yt-dlp will auto-fallback if a higher quality is not available
    val qualityOptions = listOf(
        0 to "Best",
        5 to "2160p (4K)",
        6 to "1440p (2K)",
        1 to "1080p",
        2 to "720p",
        3 to "480p",
        4 to "360p"
    )

    // Common subtitle languages
    val commonSubtitleLangs = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "zh" to "Chinese",
        "ja" to "Japanese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "hi" to "Hindi"
    )

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
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = sharedUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Format
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

            // Quality (video only)
            AnimatedVisibility(visible = !isAudio) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        qualityOptions.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedQuality == value,
                                onClick = { selectedQuality = value },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Additional Settings
            Text(
                text = "Additional Settings",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Embed thumbnail (always visible)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { embedThumbnail = !embedThumbnail }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = embedThumbnail,
                    onCheckedChange = { embedThumbnail = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Embed thumbnail", style = MaterialTheme.typography.bodyMedium)
            }

            // Subtitle settings (video only)
            AnimatedVisibility(visible = !isAudio) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { downloadSubtitles = !downloadSubtitles }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = downloadSubtitles,
                            onCheckedChange = { downloadSubtitles = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Download subtitles", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (downloadSubtitles) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val currentLangName =
                            commonSubtitleLangs.firstOrNull { it.first == selectedSubtitleLang }?.second
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
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                            DropdownMenu(
                                expanded = subtitleExpanded,
                                onDismissRequest = { subtitleExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                commonSubtitleLangs.forEach { (code, name) ->
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

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
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
                            subtitleLanguage = selectedSubtitleLang,
                            embedThumbnail = embedThumbnail
                        )
                        onStartDownload(sharedUrl, prefs)
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download")
                }
            }
        }
    }
}
