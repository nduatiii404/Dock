package com.waigi.dock.ui.screen

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.waigi.dock.player.LightweightVideoPlayer
import com.waigi.dock.player.VideoSwipeViewerScreen
import kotlin.math.roundToInt
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.waigi.dock.database.DownloadedItem
import com.waigi.dock.service.DownloadService
import com.waigi.dock.ui.components.HistoryItemInfoSheet
import com.waigi.dock.ui.viewmodel.HistoryViewModel
import com.waigi.dock.util.ThumbnailManager
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = koinViewModel(),
    onPlayVideo: ((videos: List<android.net.Uri>, initialIndex: Int) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current

    // Delay list loading until entry slide animation completes
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        isLoaded = true
    }

    // Selection States
    var selectedItems by remember { mutableStateOf(setOf<DownloadedItem>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var itemToConfirmDelete by remember { mutableStateOf<DownloadedItem?>(null) }
    var itemForInfoSheet by remember { mutableStateOf<DownloadedItem?>(null) }
    var playingItem by remember { mutableStateOf<DownloadedItem?>(null) }
    val swipeEnabled by com.waigi.dock.util.PreferenceUtil.swipeToDismissHistoryFlow.collectAsState()

    BackHandler(enabled = playingItem != null) {
        playingItem = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isSelectionMode) "${selectedItems.size} selected" else "History", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { 
                            isSelectionMode = false
                            selectedItems = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close, 
                                contentDescription = "Cancel selection",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedItems.isNotEmpty()) {
                            IconButton(onClick = { shareMultipleFiles(context, selectedItems.toList()) }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share selected",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { showDeleteSelectedDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete, 
                                contentDescription = "Delete selected",
                                tint = Color.White
                            )
                        }
                    } else if (items.isNotEmpty()) {
                        IconButton(onClick = viewModel::showDeleteAllDialog) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Delete all")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Search bar (only show when not selecting) ─────────────────────
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = expandVertically(animationSpec = tween(durationMillis = 300)) +
                        fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 150)),
                exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                       shrinkVertically(animationSpec = tween(durationMillis = 300, delayMillis = 100))
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search downloads…") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = viewModel::onClearSearch) {
                                Icon(Icons.Filled.Delete, contentDescription = "Clear search",
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }

            // ── Content ───────────────────────────────────────────────────────
            if (!isLoaded) {
                Spacer(modifier = Modifier.weight(1f))
            } else if (items.isEmpty()) {
                EmptyHistoryState(hasSearch = uiState.searchQuery.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Text(
                            text = "${items.size} item${if (items.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        val isSelected = selectedItems.contains(item)
                        if (isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                HistoryItemCard(
                                    item = item,
                                    index = index,
                                    onOpen = { openFile(context, item) { playingItem = it } },
                                    onShare = { shareFile(context, item) },
                                    isSelected = isSelected,
                                    isSelectionMode = true,
                                    onToggleSelect = {
                                        if (selectedItems.contains(item)) {
                                            selectedItems = selectedItems - item
                                            if (selectedItems.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            selectedItems = selectedItems + item
                                        }
                                    },
                                    onLongPress = {},
                                    onSelfHeal = { id, path -> viewModel.updateThumbnail(id, path) },
                                    onInfo = { itemForInfoSheet = item },
                                )
                            }
                        } else {
                            SwipeToRevealHistoryItem(
                                item = item,
                                index = index,
                                enabled = swipeEnabled,
                                onDeleteRequest = { itemToConfirmDelete = item },
                                onOpen = { openFile(context, item) { playingItem = it } },
                                onShare = { shareFile(context, item) },
                                isSelected = isSelected,
                                isSelectionMode = false,
                                onToggleSelect = {
                                    if (selectedItems.contains(item)) {
                                        selectedItems = selectedItems - item
                                        if (selectedItems.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        selectedItems = selectedItems + item
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems = setOf(item)
                                    }
                                },
                                onSelfHeal = { id, path -> viewModel.updateThumbnail(id, path) },
                                onInfo = { itemForInfoSheet = item },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        // ── Single item delete confirmation dialog (gray background) ─────────
        itemToConfirmDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToConfirmDelete = null },
                containerColor = Color(0xFF262626), // Gray confirmation dialog
                titleContentColor = Color.White,
                textContentColor = Color(0xFFDDDDDD),
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "Remove from history?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Remove \"${item.title.ifEmpty { "this download" }}\" from your history? The file stored on your device will remain untouched.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteItem(item)
                            itemToConfirmDelete = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToConfirmDelete = null }) {
                        Text("Cancel", color = Color.LightGray)
                    }
                },
            )
        }

        // ── Delete all dialog ─────────────────────────────────────────────────
        if (uiState.isDeleteAllDialogVisible) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDeleteAllDialog,
                title = { Text("Clear all history?") },
                text = { Text("This removes all ${items.size} entries from history. Downloaded files on disk are not deleted.") },
                confirmButton = {
                    TextButton(onClick = viewModel::deleteAll) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDeleteAllDialog) { Text("Cancel") }
                },
            )
        }

        // ── Delete selected dialog ───────────────────────────────────────────
        if (showDeleteSelectedDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedDialog = false },
                title = { Text("Delete selected items?") },
                text = { Text("Are you sure you want to delete the ${selectedItems.size} selected entries from history? Files on disk will not be deleted.") },
                confirmButton = {
                    TextButton(onClick = {
                        selectedItems.forEach { viewModel.deleteItem(it) }
                        isSelectionMode = false
                        selectedItems = emptySet()
                        showDeleteSelectedDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("Cancel") }
                },
            )
        }

        // ── Video Details Bottom Sheet ───────────────────────────────────────
        itemForInfoSheet?.let { item ->
            HistoryItemInfoSheet(
                item = item,
                onDismiss = { itemForInfoSheet = null },
                onRedownload = { toDownload ->
                    val thumb = toDownload.thumbnail?.takeIf { it.isNotBlank() } ?: toDownload.filePath
                    DownloadService.startDownload(
                        context = context,
                        url = toDownload.url,
                        title = toDownload.title,
                        thumbnail = thumb,
                        uploader = toDownload.uploader,
                    )
                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // Ultra-lightweight full-screen video swiper overlay
    playingItem?.let { item ->
        val file = File(item.filePath)
        if (file.exists()) {
            val videoItems = remember(items) {
                items.filter { !it.isAudio && File(it.filePath).exists() }
            }
            val initialIndex = videoItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
            val videoUris = remember(videoItems, file) {
                if (videoItems.isNotEmpty()) {
                    videoItems.map { android.net.Uri.fromFile(File(it.filePath)) }
                } else {
                    listOf(android.net.Uri.fromFile(file))
                }
            }

            if (onPlayVideo != null) {
                LaunchedEffect(item) {
                    onPlayVideo(videoUris, initialIndex)
                    playingItem = null
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(100f)
                ) {
                    VideoSwipeViewerScreen(
                        videos = videoUris,
                        initialIndex = initialIndex,
                        onClose = { playingItem = null }
                    )
                }
            }
        } else {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            playingItem = null
        }
    }
}
}

// ── Swipe-to-reveal wrapper ───────────────────────────────────────────────────

@Composable
private fun SwipeToRevealHistoryItem(
    item: DownloadedItem,
    index: Int,
    enabled: Boolean,
    onDeleteRequest: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onSelfHeal: ((Long, String) -> Unit)? = null,
    onInfo: () -> Unit = {},
) {
    if (!enabled || isSelectionMode) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            HistoryItemCard(
                item = item,
                index = index,
                onOpen = onOpen,
                onShare = onShare,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onToggleSelect = onToggleSelect,
                onLongPress = onLongPress,
                onSelfHeal = onSelfHeal,
                onInfo = onInfo,
            )
        }
        return
    }

    val density = LocalDensity.current
    val maxRevealPx = with(density) { 76.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val isRevealed = offsetX.value <= -maxRevealPx * 0.7f
    val revealFraction = (-offsetX.value / maxRevealPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        // Background: transparent delete area revealed when card slides left
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(enabled = isRevealed) {
                    onDeleteRequest()
                    coroutineScope.launch { offsetX.animateTo(0f) }
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = revealFraction },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Foreground: card that slides left and stays open at -76.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -maxRevealPx * 0.35f) {
                                    // Move and get stuck with delete icon visible
                                    offsetX.animateTo(
                                        -maxRevealPx,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                } else {
                                    // Return to closed
                                    offsetX.animateTo(
                                        0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX.value + dragAmount).coerceIn(-maxRevealPx * 1.15f, 0f)
                            coroutineScope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            HistoryItemCard(
                item = item,
                index = index,
                onOpen = {
                    if (isRevealed) {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                    } else {
                        onOpen()
                    }
                },
                onShare = {
                    if (isRevealed) {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                    } else {
                        onShare()
                    }
                },
                isSelected = isSelected,
                isSelectionMode = false,
                onToggleSelect = onToggleSelect,
                onLongPress = onLongPress,
                onSelfHeal = onSelfHeal,
                onInfo = onInfo,
            )
        }
    }
}

// ── History item card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    item: DownloadedItem,
    index: Int,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onSelfHeal: ((Long, String) -> Unit)? = null,
    onInfo: () -> Unit = {},
) {
    val animatableAlpha = remember { Animatable(0f) }
    val animatableTranslationY = remember { Animatable(32f) }

    LaunchedEffect(key1 = item.id) {
        val delayTime = if (index < 8) (index * 40).toLong() else 0L
        delay(delayTime)
        launch {
            animatableAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
        launch {
            animatableTranslationY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatableAlpha.value
                translationY = animatableTranslationY.value
            }
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onOpen()
                },
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                             else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp)
                )
            }

            // Thumbnail with persistent local caching & self-healing fallback
            HistoryThumbnail(
                item = item,
                onSelfHeal = onSelfHeal,
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Title + metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp, horizontal = 2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.uploader != null) {
                        Text(
                            text = item.uploader,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Text(
                        text = formatDate(item.downloadedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            // Action buttons (only show when not selecting)
            if (!isSelectionMode) {
                IconButton(onClick = onInfo, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Details",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyHistoryState(hasSearch: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasSearch) "No results" else "No downloads yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasSearch) "Try a different search term"
                       else "Completed downloads will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
            )
        }
    }
}

// ── File actions ──────────────────────────────────────────────────────────────

private fun openFile(
    context: Context,
    item: DownloadedItem,
    onPlayInApp: (DownloadedItem) -> Unit
) {
    val file = File(item.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
        return
    }
    if (com.waigi.dock.util.PreferenceUtil.preferredPlayer == com.waigi.dock.util.PLAYER_IN_APP) {
        onPlayInApp(item)
    } else {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mime = if (item.isAudio) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }
}

private fun shareFile(context: Context, item: DownloadedItem) {
    val file = File(item.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val mime = if (item.isAudio) "audio/*" else "video/*"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

private fun shareMultipleFiles(context: Context, items: List<DownloadedItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList<android.net.Uri>()
    var hasAudio = false
    var hasVideo = false
    for (item in items) {
        val file = File(item.filePath)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            uris.add(uri)
            if (item.isAudio) hasAudio = true else hasVideo = true
        }
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, "No files found to share", Toast.LENGTH_SHORT).show()
        return
    }
    val mime = when {
        hasAudio && hasVideo -> "*/*"
        hasAudio -> "audio/*"
        else -> "video/*"
    }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share selected via"))
}

private fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

@Composable
private fun HistoryThumbnail(
    item: DownloadedItem,
    onSelfHeal: ((Long, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentModel by remember(item.id, item.thumbnail, item.filePath) {
        val localFile = ThumbnailManager.findLocalThumbnail(context, item)
        val initial: Any? = when {
            localFile != null -> localFile
            !item.thumbnail.isNullOrBlank() -> item.thumbnail
            File(item.filePath).exists() -> File(item.filePath)
            else -> null
        }
        mutableStateOf(initial)
    }

    var hasFailed by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, item.filePath) {
        if (currentModel == null && File(item.filePath).exists()) {
            ThumbnailManager.selfHealThumbnail(
                context = context,
                scope = coroutineScope,
                item = item,
                onHealed = { healedPath ->
                    currentModel = File(healedPath)
                    onSelfHeal?.invoke(item.id, healedPath)
                }
            )
        }
    }

    val shape = RoundedCornerShape(14.dp)

    if (currentModel != null && !hasFailed) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(currentModel)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier
                .size(56.dp)
                .clip(shape),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.isAudio) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.isAudio) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            onError = {
                val mediaFile = File(item.filePath)
                if (mediaFile.exists() && currentModel != mediaFile) {
                    ThumbnailManager.selfHealThumbnail(
                        context = context,
                        scope = coroutineScope,
                        item = item,
                        onHealed = { healedPath ->
                            currentModel = File(healedPath)
                            onSelfHeal?.invoke(item.id, healedPath)
                        }
                    )
                } else {
                    hasFailed = true
                }
            }
        )
    } else {
        Box(
            modifier = modifier
                .size(56.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.isAudio) Icons.Filled.AudioFile
                              else Icons.Filled.VideoFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
