package com.waigi.dock.ui.screen

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waigi.dock.download.Downloader
import com.waigi.dock.download.TaskState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen() {
    val tasks by Downloader.tasks.collectAsState()
    val activeTasks = tasks.values.filter { it.state !is TaskState.Completed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Downloads", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        if (activeTasks.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No active downloads",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Go to Home and paste a URL to start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(activeTasks, key = { it.id }) { task ->
                    DownloadTaskCard(task)
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(task: com.waigi.dock.download.DownloadTask) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = task.title.ifEmpty { task.url },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (val state = task.state) {
                is TaskState.FetchingInfo -> {
                    Text(
                        "Fetching info…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                }
                is TaskState.Downloading -> {
                    val sizeText = if (task.totalSize.isNotEmpty()) {
                        val downloaded = com.waigi.dock.download.Downloader.calculateDownloadedSize(task.totalSize, state.progress)
                        if (downloaded.isNotEmpty()) "$downloaded / ${task.totalSize} · " else "${task.totalSize} · "
                    } else ""
                    Text(
                        text = buildString {
                            append(sizeText)
                            append("${state.progress.toInt()}%")
                            if (state.speedText.isNotEmpty()) append(" · ${state.speedText}")
                            if (state.etaSeconds > 0) append(" · ETA ${state.etaSeconds}s")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { Downloader.pause(task.id) }) {
                            Text("Pause")
                        }
                        TextButton(onClick = { Downloader.cancel(task.id) }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                is TaskState.Paused -> {
                    val sizeText = if (task.totalSize.isNotEmpty()) {
                        val downloaded = com.waigi.dock.download.Downloader.calculateDownloadedSize(task.totalSize, state.progress)
                        if (downloaded.isNotEmpty()) "$downloaded / ${task.totalSize} · " else "${task.totalSize} · "
                    } else ""
                    Text(
                        text = "${sizeText}Paused · ${state.progress.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { Downloader.resume(task.id) }) {
                            Text("Resume")
                        }
                        TextButton(onClick = { Downloader.dismiss(task.id) }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is TaskState.Queued -> {
                    Text(
                        "Queued",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is TaskState.Cancelled -> {
                    Text(
                        "Cancelled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { Downloader.retry(task.id) }) {
                            Text("Retry")
                        }
                        TextButton(onClick = { Downloader.dismiss(task.id) }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is TaskState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { Downloader.retry(task.id) }) {
                            Text("Retry")
                        }
                        TextButton(onClick = { Downloader.dismiss(task.id) }) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
