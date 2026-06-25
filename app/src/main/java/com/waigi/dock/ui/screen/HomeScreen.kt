package com.waigi.dock.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.waigi.dock.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    sharedUrl: String? = null,
    viewModel: HomeViewModel = viewModel(),
    onClipboardPositioned: (Rect?) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle URL shared from other apps
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.onSharedUrl(sharedUrl)
        }
    }

    val clipboardAutoPaste = remember { com.waigi.dock.util.PreferenceUtil.clipboardAutoPaste }
    LaunchedEffect(Unit) {
        if (clipboardAutoPaste && sharedUrl.isNullOrBlank()) {
            val text = clipboard.getClipEntry()
                ?.clipData?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotBlank() && (text.startsWith("http://") || text.startsWith("https://"))) {
                viewModel.onUrlChanged(text)
                Toast.makeText(context, "Link pasted from clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Show snackbar when download starts
    LaunchedEffect(uiState.downloadStarted) {
        if (uiState.downloadStarted) {
            scope.launch {
                snackbarHostState.showSnackbar("Download started — check the Downloads tab")
            }
            viewModel.onDownloadStartedAcknowledged()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Animated gradient header ──────────────────────────────────────
            val showGradient by com.waigi.dock.util.PreferenceUtil.showGradientHeaderFlow.collectAsState()
            DockHeader(showGradient = showGradient)

            // ── URL Input card ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Paste a link to download",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                UrlInputField(
                    url = uiState.url,
                    onUrlChanged = viewModel::onUrlChanged,
                    onClear = viewModel::onUrlCleared,
                    onPaste = {
                        scope.launch {
                            val text = clipboard.getClipEntry()
                                ?.clipData?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotBlank()) viewModel.onUrlChanged(text)
                        }
                    },
                    onDone = {
                        keyboardController?.hide()
                        viewModel.fetchInfo()
                    },
                    onClipboardPositioned = onClipboardPositioned,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Error message
                AnimatedVisibility(
                    visible = uiState.error != null,
                    enter = expandVertically(animationSpec = tween(durationMillis = 300)) +
                            slideInVertically(animationSpec = tween(durationMillis = 300)) +
                            fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 150)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                           slideOutVertically(animationSpec = tween(durationMillis = 300)) +
                           shrinkVertically(animationSpec = tween(durationMillis = 300)),
                ) {
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }

                // Fetch / loading button
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.fetchInfo()
                    },
                    enabled = uiState.url.isNotBlank() && !uiState.isLoadingInfo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (uiState.isLoadingInfo) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Fetching info…", color = Color.White)
                    } else {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Get Download Options", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // ── Supported platforms hint ───────────────────────────────────────
            SupportedPlatformsHint()
        }

        // ── Format picker bottom sheet ────────────────────────────────────────
        if (uiState.showFormatSheet) {
            FormatPickerSheet(
                videoInfo = uiState.videoInfo,
                isAudio = uiState.selectedAudio,
                selectedQuality = uiState.selectedQuality,
                downloadSubtitles = uiState.downloadSubtitles,
                selectedSubtitleLang = uiState.selectedSubtitleLang,
                onAudioToggle = viewModel::onAudioSelected,
                onQualitySelected = viewModel::onQualitySelected,
                onSubtitleToggle = viewModel::onSubtitleToggle,
                onSubtitleLangSelected = viewModel::onSubtitleLangSelected,
                onDownload = { viewModel.startDownload(context) },
                onDismiss = viewModel::onDismissFormatSheet,
            )
        }

        // ── Playlist Prompt Dialog ───────────────────────────────────────────
        if (uiState.showPlaylistPrompt) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPlaylistPrompt,
                title = {
                    Text(
                        text = "Playlist Detected",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                text = {
                    Text(
                        text = "Do you want to download the entire playlist \"${uiState.playlistTitle}\" (${uiState.playlistVideoCount} items) or download a single video?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.startPlaylistDownload(context) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Download Playlist")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = viewModel::fetchSingleVideoFromPlaylist) {
                            Text("Download Single Video")
                        }
                        TextButton(onClick = viewModel::dismissPlaylistPrompt) {
                            Text("Cancel")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun DockHeader(showGradient: Boolean) {
    if (showGradient) {
        // Vibrant mesh gradient header with absolute premium design
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF3F51B5), // Deep blue
                            Color(0xFF00BCD4)  // Teal
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = com.waigi.dock.R.drawable.ic_logo),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dock",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Stash your streams, zero strings attached",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = com.waigi.dock.R.drawable.ic_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Dock",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

// ── URL Input ─────────────────────────────────────────────────────────────────

@Composable
private fun UrlInputField(
    url: String,
    onUrlChanged: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onDone: () -> Unit,
    onClipboardPositioned: (Rect?) -> Unit = {},
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "https://…",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = {
            Row {
                if (url.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                } else {
                    IconButton(
                        onClick = onPaste,
                        modifier = Modifier.onGloballyPositioned {
                            onClipboardPositioned(it.boundsInRoot())
                        }
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = "Paste",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    )
}

// ── Supported platforms hint ──────────────────────────────────────────────────

@Composable
private fun SupportedPlatformsHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Supported platforms",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        val platforms = listOf("YouTube", "TikTok", "X / Twitter", "Instagram", "Reddit",
            "Facebook", "Twitch", "Vimeo", "SoundCloud", "1k+ more")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            platforms.take(5).forEach { platform ->
                PlatformChip(platform)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            platforms.drop(5).forEach { platform ->
                PlatformChip(platform)
            }
        }
    }
}

@Composable
private fun PlatformChip(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Format Picker Bottom Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatPickerSheet(
    videoInfo: com.waigi.dock.util.VideoInfo?,
    isAudio: Boolean,
    selectedQuality: Int,
    downloadSubtitles: Boolean,
    selectedSubtitleLang: String,
    onAudioToggle: (Boolean) -> Unit,
    onQualitySelected: (Int) -> Unit,
    onSubtitleToggle: (Boolean) -> Unit,
    onSubtitleLangSelected: (String) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title + thumbnail
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                if (videoInfo?.thumbnail != null) {
                    AsyncImage(
                        model = videoInfo.thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoInfo?.title ?: "Ready to download",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (videoInfo?.uploader != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = videoInfo.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // ── Format selector: Video / Audio ────────────────────────────────
            Text(
                text = "Format",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !isAudio,
                    onClick = { onAudioToggle(false) },
                    label = { Text("Video") },
                    leadingIcon = {
                        Icon(
                            if (!isAudio) Icons.Filled.VideoFile else Icons.Outlined.VideoFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                FilterChip(
                    selected = isAudio,
                    onClick = { onAudioToggle(true) },
                    label = { Text("Audio only") },
                    leadingIcon = {
                        Icon(
                            if (isAudio) Icons.Filled.AudioFile else Icons.Outlined.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            // ── Quality selector (video only) ─────────────────────────────────
            AnimatedVisibility(visible = !isAudio) {
                Column {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val availableQualities = remember(videoInfo) {
                        val heights = videoInfo?.formats
                            ?.filter { it.vcodec != "none" && it.height != null && it.height > 0.0 }
                            ?.mapNotNull { it.height?.toInt() }
                            ?.toSet() ?: emptySet()

                        if (heights.isEmpty()) {
                            listOf(
                                0 to "Best",
                                1 to "1080p",
                                2 to "720p",
                                3 to "480p",
                                4 to "360p"
                            )
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableQualities.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedQuality == value,
                                onClick = { onQualitySelected(value) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // ── Subtitle selector (video only, if subtitles available) ────────
            AnimatedVisibility(visible = !isAudio) {
                val subtitleLanguages = remember(videoInfo) {
                    val map = mutableMapOf<String, String>() // code -> name
                    videoInfo?.subtitles?.forEach { (code, list) ->
                        val name = list.firstOrNull()?.name ?: code
                        map[code] = name
                    }
                    videoInfo?.automatic_captions?.forEach { (code, list) ->
                        val name = (list.firstOrNull()?.name ?: code) + " (auto)"
                        if (!map.containsKey(code)) {
                            map[code] = name
                        }
                    }
                    map.toList().sortedBy { it.second }
                }

                if (subtitleLanguages.isNotEmpty()) {
                    Column {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Subtitles",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = downloadSubtitles,
                                onCheckedChange = onSubtitleToggle,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download subtitles",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (downloadSubtitles) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var subtitleExpanded by remember { mutableStateOf(false) }
                            val currentLangName = subtitleLanguages.firstOrNull { it.first == selectedSubtitleLang }?.second ?: selectedSubtitleLang

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { subtitleExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentLangName.ifEmpty { "Select language" },
                                        style = MaterialTheme.typography.bodyMedium,
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
                                    subtitleLanguages.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                onSubtitleLangSelected(code)
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

            Spacer(modifier = Modifier.height(28.dp))

            // ── Download button ───────────────────────────────────────────────
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isAudio) "Download Audio" else "Download Video",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
