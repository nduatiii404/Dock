package com.waigi.dock.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.waigi.dock.download.Downloader
import com.waigi.dock.ui.components.DownloadThumbnailBanner
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Tune
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

    val tasks by Downloader.tasks.collectAsState()
    val activeTask = remember(tasks, uiState.currentTaskId) {
        uiState.currentTaskId?.let { tasks[it] } ?: tasks.values.lastOrNull { !it.state.isTerminal }
    }
    val isBannerVisible = !uiState.isBannerDismissed && (uiState.videoInfo != null || activeTask != null)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
        ) {
            // ── Animated gradient header ──────────────────────────────────────
            val showGradient by com.waigi.dock.util.PreferenceUtil.showGradientHeaderFlow.collectAsState()
            DockHeader(showGradient = showGradient, isCompact = isBannerVisible)

            // ── Large Thumbnail Banner Preview ────────────────────────────────
            AnimatedVisibility(
                visible = isBannerVisible,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 200)
                ),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 150)
                ),
            ) {
                DownloadThumbnailBanner(
                    videoInfo = uiState.videoInfo,
                    task = activeTask,
                    selectedQuality = uiState.selectedQuality,
                    selectedAudio = uiState.selectedAudio,
                    onPause = Downloader::pause,
                    onResume = Downloader::resume,
                    onCancel = Downloader::cancel,
                    onDismiss = viewModel::dismissThumbnailBanner,
                )
            }

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

                // ── Action Buttons Row with Bouncy Morphing Split Animation ────
                val canReopenSheet = uiState.videoInfo != null &&
                    uiState.url.trim().isNotEmpty() &&
                    uiState.url.trim() == uiState.lastFetchedUrl

                val splitProgress by animateFloatAsState(
                    targetValue = if (uiState.isLoadingInfo) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "splitProgress"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Main Action / Fetching Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (canReopenSheet) {
                                viewModel.openFormatSheet()
                            } else {
                                viewModel.fetchInfo()
                            }
                        },
                        enabled = uiState.url.isNotBlank() && !uiState.isLoadingInfo,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 1. Fetching State: fades in smoothly as split reaches open width
                            val fetchingAlpha = ((splitProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            if (fetchingAlpha > 0f) {
                                Row(
                                    modifier = Modifier.alpha(fetchingAlpha),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        strokeCap = StrokeCap.Round,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Fetching info…",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }

                            // 2. Action State: fades in smoothly as button closes back to full width
                            val normalAlpha = ((0.55f - splitProgress) / 0.55f).coerceIn(0f, 1f)
                            if (normalAlpha > 0f) {
                                Row(
                                    modifier = Modifier.alpha(normalAlpha),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (canReopenSheet) {
                                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Show Download Options",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    } else {
                                        Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Get Download Options",
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fluid Animated Gap & Cancel Button
                    if (splitProgress > 0.001f) {
                        Spacer(modifier = Modifier.width((8 * splitProgress).dp))

                        Box(
                            modifier = Modifier
                                .width((100 * splitProgress).dp)
                                .fillMaxHeight()
                                .clipToBounds()
                                .alpha(splitProgress.coerceIn(0f, 1f))
                        ) {
                            Button(
                                onClick = viewModel::cancelFetchInfo,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        painter = painterResource(id = com.waigi.dock.R.drawable.lucide_ic_x),
                                        contentDescription = "Cancel",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Supported platforms hint ───────────────────────────────────────
            SupportedPlatformsHint()
        }

        // ── Format picker bottom sheet ────────────────────────────────────────
        if (uiState.showFormatSheet && uiState.videoInfo != null) {
            com.waigi.dock.ui.components.DownloadOptionsDialog(
                url = uiState.url,
                videoInfo = uiState.videoInfo,
                onDismiss = viewModel::onDismissFormatSheet,
                onDownload = { _, prefs ->
                    viewModel.onAudioSelected(prefs.extractAudio)
                    viewModel.onQualitySelected(prefs.videoQuality)
                    viewModel.onSubtitleToggle(prefs.writeSubtitle)
                    viewModel.onSubtitleLangSelected(prefs.subtitleLanguage)
                    viewModel.startDownload(context, prefs)
                }
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
private fun DockHeader(showGradient: Boolean, isCompact: Boolean = false) {
    if (showGradient) {
        val headerHeight by animateDpAsState(
            targetValue = if (isCompact) 64.dp else 140.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "gradientHeaderHeight"
        )
        val verticalPadding by animateDpAsState(
            targetValue = if (isCompact) 6.dp else 12.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "gradientHeaderPadding"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(horizontal = 16.dp, vertical = verticalPadding)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF3F51B5), // Deep blue
                            Color(0xFF00BCD4)  // Teal
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 12.dp),
                verticalArrangement = if (isCompact) Arrangement.Center else Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.height(if (isCompact) 36.dp else 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = com.waigi.dock.R.drawable.ic_logo),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (isCompact) 22.dp else 24.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dock",
                        style = if (isCompact) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                else MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
                AnimatedVisibility(
                    visible = !isCompact,
                    enter = fadeIn(tween(150)) + expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
                    exit = fadeOut(tween(100)) + shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stash your streams, zero strings attached",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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


