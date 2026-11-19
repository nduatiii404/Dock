package com.waigi.dock.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.waigi.dock.util.DownloadPreferences
import com.waigi.dock.util.VideoInfo
import com.waigi.dock.util.formatSourcePlatform
import java.util.Locale

/**
 * Custom sleek toggle pill:
 * - 22dp crisp white circle visible in BOTH ON and OFF states (never invisible or shrunk)
 * - Exact 2dp uniform gap between the circle and the outer pill
 * - Outer pill corner radius (13dp) matching the 22dp circle + 2dp padding for a unified look
 */
@Composable
fun DockSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackWidth = 48.dp
    val trackHeight = 26.dp
    val thumbSize = 22.dp
    val padding = 2.dp
    val cornerRadius = 13.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) (trackWidth - thumbSize - padding) else padding,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "dock_switch_thumb"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color(0xFF38383A),
        animationSpec = tween(durationMillis = 200),
        label = "dock_switch_track"
    )

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsDialog(
    url: String,
    videoInfo: VideoInfo? = null,
    initialPreferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    onDismiss: () -> Unit,
    onDownload: (url: String, preferences: DownloadPreferences) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isAudio by remember { mutableStateOf(initialPreferences.extractAudio) }
    var selectedQuality by remember { mutableIntStateOf(initialPreferences.videoQuality) }
    var downloadSubtitles by remember { mutableStateOf(false) }

    // Build available subtitle languages dynamically from video metadata + common languages
    val availableSubtitleLangs = remember(videoInfo) {
        val list = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()

        videoInfo?.subtitles?.forEach { (code, _) ->
            if (seen.add(code.lowercase())) {
                val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifEmpty { code.uppercase() }
                list.add(code to name)
            }
        }
        videoInfo?.automatic_captions?.forEach { (code, _) ->
            if (seen.add(code.lowercase())) {
                val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifEmpty { code.uppercase() }
                list.add(code to "$name (auto)")
            }
        }
        val defaultLangs = listOf(
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "ja" to "Japanese",
            "ko" to "Korean",
            "zh" to "Chinese",
            "ar" to "Arabic",
            "hi" to "Hindi"
        )
        for ((code, name) in defaultLangs) {
            if (seen.add(code.lowercase())) {
                list.add(code to name)
            }
        }
        list
    }

    var selectedSubtitleLang by remember {
        mutableStateOf(
            videoInfo?.subtitles?.keys?.firstOrNull()
                ?: videoInfo?.automatic_captions?.keys?.firstOrNull()
                ?: initialPreferences.subtitleLanguage.ifEmpty { "en" }
        )
    }
    var subtitleExpanded by remember { mutableStateOf(false) }

    val qualityOptions = listOf(
        0 to "Best",
        5 to "4K",
        6 to "2K",
        1 to "1080p",
        2 to "720p",
        3 to "480p",
        4 to "360p"
    )

    val audioQualityOptions = listOf(
        0 to "Best (320k)",
        1 to "256 kbps",
        2 to "192 kbps",
        3 to "128 kbps"
    )

    val currentLangName = availableSubtitleLangs.firstOrNull { it.first.equals(selectedSubtitleLang, ignoreCase = true) }?.second
        ?: selectedSubtitleLang.uppercase()

    val hostName = remember(url, videoInfo?.extractor) {
        formatSourcePlatform(url, videoInfo?.extractor)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
        ) {
            // ── 1. Header Section ──
            if (videoInfo != null) {
                // Pre-loaded video info (HomeScreen): Thumbnail on top-left + details on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = videoInfo.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(76.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = videoInfo.title.ifEmpty { url },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!videoInfo.uploader.isNullOrBlank()) {
                                Text(
                                    text = videoInfo.uploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            if (hostName.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = hostName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // URL-only mode (ShareActivity): Chain link icon on left of URL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hostName.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = hostName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 2. Format Segment: Video vs Audio (generous touch targets) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isAudio = false },
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (!isAudio) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = if (!isAudio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Video",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (!isAudio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isAudio = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isAudio) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = if (isAudio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Audio Only",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isAudio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 3. Quality Selection (animated smoothly between video resolutions and audio bitrates) ──
            Text(
                text = if (isAudio) "Audio Bitrate" else "Resolution & Quality",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            AnimatedContent(
                targetState = isAudio,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(180)) + slideInHorizontally { if (targetState) it / 5 else -it / 5 })
                        .togetherWith(fadeOut(animationSpec = tween(140)) + slideOutHorizontally { if (targetState) -it / 5 else it / 5 })
                },
                label = "quality_switch"
            ) { audioSelected ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentOptions = if (audioSelected) audioQualityOptions else qualityOptions
                    currentOptions.forEach { (code, label) ->
                        val isSelected = selectedQuality == code
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedQuality = code },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.5.dp
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(38.dp)
                        )
                    }
                }
            }

            // ── 4. Subtitles Option (fluidly expands/shrinks for Video) ──
            AnimatedVisibility(
                visible = !isAudio,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = if (downloadSubtitles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Download Subtitles",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (downloadSubtitles) "Embedded in file" else "Subtitles disabled",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DockSwitch(
                                    checked = downloadSubtitles,
                                    onCheckedChange = { downloadSubtitles = it }
                                )
                            }

                            AnimatedVisibility(
                                visible = downloadSubtitles,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { subtitleExpanded = true },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = "Language: $currentLangName",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                    color = Color.White
                                                )
                                            }
                                            Text(
                                                text = "Change",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Compact & functional subtitle language popup
                                        DropdownMenu(
                                            expanded = subtitleExpanded,
                                            onDismissRequest = { subtitleExpanded = false },
                                            modifier = Modifier
                                                .widthIn(min = 190.dp, max = 260.dp)
                                                .heightIn(max = 240.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            availableSubtitleLangs.forEach { (code, name) ->
                                                val isChosen = code.equals(selectedSubtitleLang, ignoreCase = true)
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = name,
                                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                                                ),
                                                                color = if (isChosen) MaterialTheme.colorScheme.primary else Color.White,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            if (isChosen) {
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedSubtitleLang = code
                                                        subtitleExpanded = false
                                                    },
                                                    modifier = Modifier.height(40.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 5. Action Buttons (comfortable touch targets) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        val prefs = initialPreferences.copy(
                            extractAudio = isAudio,
                            videoQuality = selectedQuality,
                            writeSubtitle = downloadSubtitles,
                            embedSubtitle = downloadSubtitles,
                            subtitleLanguage = selectedSubtitleLang
                        )
                        onDownload(url, prefs)
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}
