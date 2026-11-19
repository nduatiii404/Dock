package com.waigi.dock.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.waigi.dock.R
import com.waigi.dock.download.Downloader
import com.waigi.dock.download.DownloadTask
import com.waigi.dock.download.TaskState
import com.waigi.dock.util.VideoInfo
import java.util.Locale

/**
 * Large, interactive video thumbnail banner that appears between the header and input field.
 *
 * Features:
 * - 24.dp corner radius matching the gradient header.
 * - 16:9 thumbnail image with a semi-transparent floating pill at the bottom-right (duration & quality/size).
 * - Integrated title and live status row directly below the thumbnail.
 * - Pause / Resume, Cancel, and Dismiss controls with Lucide icons.
 * - Bottom-attached linear progress bar (no thumb) that is smoothly cut off by the card's 24.dp rounded corners.
 */
@Composable
fun DownloadThumbnailBanner(
    videoInfo: VideoInfo?,
    task: DownloadTask?,
    selectedQuality: Int,
    selectedAudio: Boolean,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(24.dp)
    val thumbnailUrl = task?.thumbnail ?: videoInfo?.thumbnail
    val title = task?.title?.ifEmpty { null } ?: videoInfo?.title?.ifEmpty { null } ?: "Media Download"
    val uploader = task?.uploader ?: videoInfo?.uploader

    // Pill calculations: duration & quality/size
    val durationSeconds = videoInfo?.duration?.toLong() ?: 0L
    val durationStr = formatDuration(durationSeconds)
    val qualityLabel = formatQualityLabel(selectedQuality, selectedAudio)
    val sizeStr = when {
        task != null && task.totalSize.isNotEmpty() -> task.totalSize
        videoInfo != null -> estimateFileSize(videoInfo, selectedQuality, selectedAudio)
        else -> ""
    }

    val pillText = buildString {
        if (durationStr.isNotEmpty()) {
            append(durationStr)
            append(" • ")
        }
        append(qualityLabel)
        if (sizeStr.isNotEmpty()) {
            append(" ($sizeStr)")
        }
    }

    // Progress bar calculation
    val progress = task?.progress ?: 0f
    val isTaskActive = task != null && !task.state.isTerminal
    val animatedProgress by animateFloatAsState(
        targetValue = if (isTaskActive) (progress / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "banner_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(cardShape),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── 16:9 Thumbnail preview with floating pill ───────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "DockPulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "PulseAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF1E1E1E))
            ) {
                AnimatedContent(
                    targetState = thumbnailUrl,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 450, delayMillis = 450))
                            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 450)))
                    },
                    label = "ThumbnailTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { targetUrl ->
                    if (!targetUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(targetUrl)
                                .crossfade(350)
                                .build(),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF222222)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_logo),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF222222)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_logo),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_logo),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Bottom gradient scrim for pill readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Bottom-right floating semi-transparent pill
                if (pillText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                    ) {
                        Text(
                            text = pillText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // ── Integrated Title, Status & Controls Row ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Title and status text
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val statusText = when {
                        task != null -> when (task.state) {
                            is TaskState.Downloading -> {
                                val speed = task.speedText.ifEmpty { "Downloading" }
                                val eta = if (task.etaSeconds > 0) " • ETA ${formatEta(task.etaSeconds)}" else ""
                                val sizeProgress = if (task.totalSize.isNotEmpty()) {
                                    val downloaded = Downloader.calculateDownloadedSize(task.totalSize, task.progress)
                                    if (downloaded.isNotEmpty()) " • $downloaded / ${task.totalSize}" else " • ${task.totalSize}"
                                } else ""
                                "${task.progress.toInt()}%$sizeProgress • $speed$eta"
                            }
                            is TaskState.Paused -> {
                                val sizeProgress = if (task.totalSize.isNotEmpty()) {
                                    val downloaded = Downloader.calculateDownloadedSize(task.totalSize, task.progress)
                                    if (downloaded.isNotEmpty()) " • $downloaded / ${task.totalSize}" else " • ${task.totalSize}"
                                } else ""
                                "Paused • ${task.progress.toInt()}%$sizeProgress"
                            }
                            is TaskState.FetchingInfo -> "Fetching info…"
                            is TaskState.Queued -> "Queued…"
                            is TaskState.Merging -> "Processing media…"
                            is TaskState.Completed -> "Download complete"
                            is TaskState.Cancelled -> "Cancelled"
                            is TaskState.Error -> task.errorMessage ?: "Download failed"
                        }
                        !uploader.isNullOrBlank() -> uploader
                        else -> "Ready to download"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Controls row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (task != null && !task.state.isTerminal) {
                        val isPaused = task.state is TaskState.Paused
                        // Pause / Resume button
                        IconButton(
                            onClick = {
                                if (isPaused) onResume(task.id) else onPause(task.id)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPaused) R.drawable.lucide_ic_play else R.drawable.lucide_ic_pause
                                ),
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Cancel button
                        IconButton(
                            onClick = { onCancel(task.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lucide_ic_x),
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Dismiss button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lucide_ic_x),
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Attached Bottom Progress Bar (No Thumb, Edge-Cut) ────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        if (isTaskActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
            ) {
                if (animatedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        Color(0xFF3F51B5), // Deep blue
                                        Color(0xFF00BCD4)  // Teal
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}

private fun formatEta(seconds: Long): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, secs)
}

private fun formatQualityLabel(quality: Int, isAudio: Boolean): String {
    if (isAudio) return "Audio"
    return when (quality) {
        5 -> "4K"
        6 -> "2K"
        1 -> "1080p"
        2 -> "720p"
        3 -> "480p"
        4 -> "360p"
        else -> "Best"
    }
}

private fun estimateFileSize(videoInfo: VideoInfo, quality: Int, isAudio: Boolean): String {
    val formats = videoInfo.formats ?: return ""
    if (isAudio) {
        val audioFormat = formats.filter { it.vcodec == "none" || it.acodec != "none" }
            .maxByOrNull { it.filesize ?: it.filesize_approx ?: 0.0 }
        val bytes = audioFormat?.filesize ?: audioFormat?.filesize_approx
        return if (bytes != null && bytes > 0) formatBytes(bytes) else ""
    }

    val targetHeight = when (quality) {
        5 -> 2160.0
        6 -> 1440.0
        1 -> 1080.0
        2 -> 720.0
        3 -> 480.0
        4 -> 360.0
        else -> 9999.0
    }

    val matched = formats.filter { (it.height ?: 0.0) <= targetHeight }
        .maxByOrNull { it.filesize ?: it.filesize_approx ?: 0.0 }
    val bytes = matched?.filesize ?: matched?.filesize_approx
    return if (bytes != null && bytes > 0) formatBytes(bytes) else ""
}

private fun formatBytes(bytes: Double): String {
    val mb = bytes / (1024 * 1024)
    val gb = mb / 1024
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
