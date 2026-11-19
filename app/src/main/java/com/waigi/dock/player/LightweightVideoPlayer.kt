package com.waigi.dock.player

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import com.waigi.dock.R as LucideR

@OptIn(UnstableApi::class)
@Composable
fun LightweightVideoPlayer(
    uri: Uri,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    initialMuted: Boolean = false,
    onDismissRequest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isMuted by remember { mutableStateOf(initialMuted) }
    var isSpeedBoosting by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    var localPosition by remember { mutableLongStateOf(0L) }
    var localDuration by remember { mutableLongStateOf(0L) }
    var isVideoReady by remember { mutableStateOf(false) }

    // Scrubber interaction & track animation states
    var isDraggingSeekbar by remember { mutableStateOf(false) }
    var scrubStartPosition by remember { mutableLongStateOf(0L) }
    val seekInteractionSource = remember { MutableInteractionSource() }
    val isSeekPressed by seekInteractionSource.collectIsPressedAsState()
    val isSeekDragged by seekInteractionSource.collectIsDraggedAsState()
    val isSeekInteracting = isDraggingSeekbar || isSeekPressed || isSeekDragged

    val trackScaleY by animateFloatAsState(
        targetValue = if (isSeekInteracting) 1.7f else 1f,
        animationSpec = tween(150),
        label = "TrackScaleY"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isSeekInteracting) 1.4f else 1f,
        animationSpec = tween(150),
        label = "ThumbScale"
    )

    // Double-tap & pinch zoom
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    // Low-latency ExoPlayer
    val player = remember(uri) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_500,
                /* maxBufferMs = */ 8_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val cacheFactory = VideoCacheManager.buildCacheDataSourceFactory(context)
        val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaSource(mediaSource)
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = autoPlay
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    localDuration = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }

    // Pause on background, resume on foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
            else if (event == Lifecycle.Event.ON_RESUME && isPlaying) player.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Position updater
    LaunchedEffect(player, isPlaying, isSeekInteracting) {
        while (true) {
            if (player.isPlaying && !isSeekInteracting) {
                localPosition = player.currentPosition
                localDuration = player.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    // Controls timeout (auto-hide after 3.2s)
    LaunchedEffect(showControls, isPlaying, isSeekInteracting) {
        if (showControls && isPlaying && !isSeekInteracting) {
            delay(3200)
            showControls = false
        }
    }

    // Audio focus & volume
    LaunchedEffect(isMuted, player) {
        player.volume = if (isMuted) 0f else 1f
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        player.setAudioAttributes(audioAttrs, !isMuted)
    }

    fun formatMs(ms: Long): String {
        val totalSec = ms / 1000L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = {
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.05f) {
                                launch { scaleAnim.animateTo(1f, tween(250)) }
                                launch { offsetAnim.animateTo(Offset.Zero, tween(250)) }
                            } else {
                                launch { scaleAnim.animateTo(2.5f, tween(250)) }
                            }
                        }
                    },
                    onLongPress = {
                        isSpeedBoosting = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        player.setPlaybackSpeed(2.0f)
                    },
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (isSpeedBoosting) {
                                isSpeedBoosting = false
                                player.setPlaybackSpeed(1.0f)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Video Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scaleAnim.value,
                    scaleY = scaleAnim.value,
                    translationX = offsetAnim.value.x,
                    translationY = offsetAnim.value.y
                ),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isVideoReady) 1f else 0f }
            )

            // Zero-flicker Thumbnail placeholder
            if (!isVideoReady && thumbnailUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbnailUrl).crossfade(false).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Hold-for-2x Boost Pill
        AnimatedVisibility(
            visible = isSpeedBoosting,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = LucideR.drawable.lucide_ic_fast_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text("2.0x Speed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Close button (Top Left)
                if (onDismissRequest != null) {
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(0.4f), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = LucideR.drawable.lucide_ic_x),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Center Play/Pause Indicator
                IconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) player.play() else player.pause()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(Color.Black.copy(0.45f), CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = if (isPlaying) LucideR.drawable.lucide_ic_pause else LucideR.drawable.lucide_ic_play),
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // ── MODERN SCRUBBER BAR ──
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    // Top Row: Mute on left (34dp), Play/Pause on right (34dp)
                    // (Fades out when actively scrubbing)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (isSeekInteracting) 0f else 1f },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable(enabled = !isSeekInteracting) { isMuted = !isMuted }
                        ) {
                            Icon(
                                painter = painterResource(id = if (isMuted) LucideR.drawable.lucide_ic_volume_off else LucideR.drawable.lucide_ic_volume_2),
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable(enabled = !isSeekInteracting) {
                                    isPlaying = !isPlaying
                                    if (isPlaying) player.play() else player.pause()
                                }
                        ) {
                            Icon(
                                painter = painterResource(id = if (isPlaying) LucideR.drawable.lucide_ic_pause else LucideR.drawable.lucide_ic_play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress math & jump detection
                    val rawSeekProgress = if (localDuration > 0)
                        (localPosition.toFloat() / localDuration.toFloat()).coerceIn(0f, 1f)
                    else 0f
                    var prevSeekProgress by remember { mutableFloatStateOf(rawSeekProgress) }
                    val seekDelta = rawSeekProgress - prevSeekProgress
                    val isSeekJump = seekDelta < 0f || kotlin.math.abs(seekDelta) > 0.05f
                    LaunchedEffect(rawSeekProgress) { prevSeekProgress = rawSeekProgress }

                    val smoothSeekProgress by animateFloatAsState(
                        targetValue = rawSeekProgress,
                        animationSpec = if (isSeekJump) snap() else tween(120, easing = LinearEasing),
                        label = "SmoothSeek"
                    )
                    val seekProgress = if (isSeekInteracting || isSeekJump) rawSeekProgress else smoothSeekProgress

                    // Scrubber Track & Floating Delta Bubble
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().height(26.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val maxW = maxWidth
                        val thumbSz = 10.dp
                        val trackH = 3.dp

                        // Floating delta bubble above thumb
                        if (isSeekInteracting && localDuration > 0L) {
                            val deltaSec = ((localPosition - scrubStartPosition) / 1000L).toInt()
                            val deltaStr = if (deltaSec >= 0) "+${deltaSec}s" else "${deltaSec}s"

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .zIndex(20f)
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(Constraints())
                                        val maxWPx = maxW.roundToPx()
                                        val thumbXPx = (maxWPx * seekProgress).roundToInt()
                                        val x = (thumbXPx - placeable.width / 2).coerceIn(0, (maxWPx - placeable.width).coerceAtLeast(0))
                                        val y = -placeable.height - 10.dp.roundToPx()
                                        layout(0, constraints.maxHeight) {
                                            placeable.place(x, y)
                                        }
                                    }
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF181818).copy(alpha = 0.85f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatMs(localPosition),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "($deltaStr)",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Background inactive track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackH)
                                .graphicsLayer { scaleY = trackScaleY }
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(0.3f))
                        )
                        // Active played track
                        Box(
                            modifier = Modifier
                                .width((maxW * seekProgress).coerceAtLeast(0.dp))
                                .height(trackH)
                                .graphicsLayer { scaleY = trackScaleY }
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                        // Scrubber thumb
                        Box(
                            modifier = Modifier
                                .offset(x = maxW * seekProgress - thumbSz / 2)
                                .size(thumbSz)
                                .graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                                .clip(CircleShape)
                                .background(Color.White)
                        )

                        // Invisible touch hit area
                        Slider(
                            value = rawSeekProgress,
                            onValueChange = { v ->
                                if (!isDraggingSeekbar) scrubStartPosition = localPosition
                                isDraggingSeekbar = true
                                localPosition = (v * localDuration).toLong()
                            },
                            onValueChangeFinished = {
                                player.seekTo(localPosition)
                                isDraggingSeekbar = false
                            },
                            interactionSource = seekInteractionSource,
                            modifier = Modifier.fillMaxWidth().height(26.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                                disabledThumbColor = Color.Transparent,
                                disabledActiveTrackColor = Color.Transparent,
                                disabledInactiveTrackColor = Color.Transparent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Timestamps: Current time left, total duration right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMs(localPosition), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f))
                        Text(formatMs(localDuration), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f))
                    }
                }
            }
        }
    }
}
