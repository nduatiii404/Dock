package com.waigi.dock.ui.screen

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.waigi.dock.database.DownloadedItem
import com.waigi.dock.ui.viewmodel.HistoryViewModel
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
fun HistoryScreen(viewModel: HistoryViewModel = koinViewModel()) {
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
                                    onOpen = { openFile(context, item) },
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
                                    onLongPress = {}
                                )
                            }
                        } else {
                            SwipeToDismissHistoryItem(
                                item = item,
                                index = index,
                                onDelete = { viewModel.deleteItem(item) },
                                onOpen = { openFile(context, item) },
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
                                }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
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
    }
}

// ── Swipe-to-dismiss wrapper ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissHistoryItem(
    item: DownloadedItem,
    index: Int,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isSelectionMode,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)),
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.error
                else
                    Color.Transparent,
                animationSpec = tween(200),
                label = "swipe_color",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val isSwipingLeft = runCatching { dismissState.requireOffset() }.getOrDefault(0f) < -0.5f
                if (isSwipingLeft) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                    )
                }
            }
        },
    ) {
        HistoryItemCard(
            item = item,
            index = index,
            onOpen = onOpen,
            onShare = onShare,
            isSelected = isSelected,
            isSelectionMode = isSelectionMode,
            onToggleSelect = onToggleSelect,
            onLongPress = onLongPress
        )
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Thumbnail or file-type icon
            if (item.thumbnail != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.thumbnail)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
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

            Spacer(modifier = Modifier.width(12.dp))

            // Title + metadata
            Column(modifier = Modifier.weight(1f)) {
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

private fun openFile(context: Context, item: DownloadedItem) {
    val file = File(item.filePath)
    if (!file.exists()) {
        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
        return
    }
    if (com.waigi.dock.util.PreferenceUtil.preferredPlayer == com.waigi.dock.util.PLAYER_IN_APP) {
        val intent = Intent(context, com.waigi.dock.PlayerActivity::class.java).apply {
            putExtra("FILE_PATH", item.filePath)
            putExtra("TITLE", item.title)
            putExtra("IS_AUDIO", item.isAudio)
        }
        context.startActivity(intent)
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
