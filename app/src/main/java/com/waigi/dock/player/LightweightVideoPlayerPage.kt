package com.waigi.dock.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import com.waigi.dock.R

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(UnstableApi::class)
@Composable
fun LightweightVideoPlayerPage(
    uri: Uri,
    isPageActive: Boolean,
    playWhenActive: Boolean = true,
    pageIndex: Int = 1,
    totalVideos: Int = 1,
    onGestureLockChanged: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showLockOverlay by remember { mutableStateOf(true) }
    var isZoomMode by remember { mutableStateOf(false) }
    var isRepeatOne by remember { mutableStateOf(true) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

    var localPosition by remember { mutableLongStateOf(0L) }
    var localDuration by remember { mutableLongStateOf(0L) }
    var isVideoReady by remember { mutableStateOf(false) }
    var playerInitiated by remember(uri) { mutableStateOf(false) }

    // Double-tap +/-10s seek ripple state
    var doubleTapSeekDirection by remember { mutableIntStateOf(0) } // -1 left, 1 right, 0 none
    var doubleTapSeekCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(doubleTapSeekCounter) {
        if (doubleTapSeekDirection != 0) {
            delay(600)
            doubleTapSeekDirection = 0
        }
    }

    // Video title derived from Uri
    val videoTitle = remember(uri) {
        try {
            val seg = uri.lastPathSegment?.substringAfterLast('/')
            if (!seg.isNullOrBlank()) seg else "Video"
        } catch (e: Exception) {
            "Video"
        }
    }

    // Brightness HUD & Gesture State
    val activity = remember(context) { context.findActivity() }
    var currentBrightness by remember {
        val initial = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initial < 0f) 0.5f else initial.coerceIn(0.01f, 1f))
    }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var brightnessHudCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(brightnessHudCounter) {
        if (showBrightnessHud) {
            delay(1200)
            showBrightnessHud = false
        }
    }

    // Volume HUD & Gesture State
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    var currentVolumeRatio by remember(audioManager, maxVolume) {
        val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVolume / 2)
        mutableFloatStateOf((cur.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f))
    }
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumeHudCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(volumeHudCounter) {
        if (showVolumeHud) {
            delay(1200)
            showVolumeHud = false
        }
    }

    // Instant Frame 0 memory bitmap
    var frame0Bitmap by remember(uri) {
        mutableStateOf(VideoFrame0Manager.getMemoryBitmap(uri.toString()))
    }
    LaunchedEffect(uri) {
        if (frame0Bitmap == null) {
            frame0Bitmap = VideoFrame0Manager.loadOrExtractFrame0(context, uri, uri.toString())
        }
    }

    // Zoom & pan states
    val scaleAnim = remember { Animatable(1f) }
    val offsetAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val isPinchZoomed = scaleAnim.value > 1.05f

    LaunchedEffect(isPinchZoomed, isControlsLocked, isPageActive) {
        if (isPageActive) {
            onGestureLockChanged(isPinchZoomed || isControlsLocked)
        }
    }

    // Strict Scroll Gating from Blueprint:
    LaunchedEffect(isPageActive, playWhenActive) {
        if (isPageActive && playWhenActive) {
            // Only spin up the ExoPlayer once the spring has fully settled on this page
            playerInitiated = true
        } else if (!isPageActive) {
            // Instantly release the ExoPlayer when the user swipes away to free hardware codecs
            playerInitiated = false
            isVideoReady = false
        }
    }

    // Scroll Gated Player: Only instantiate ExoPlayer when playerInitiated is true
    val player = remember(uri, playerInitiated) {
        if (!playerInitiated) null
        else {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_500, 8_000, 500, 1_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val cacheFactory = VideoCacheManager.buildCacheDataSourceFactory(context)
            val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
                .createMediaSource(MediaItem.fromUri(uri))

            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
                    repeatMode = if (isRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    playbackParameters = playbackParameters.withSpeed(selectedSpeed)
                    prepare()
                    playWhenReady = isPlaying
                }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    localDuration = player?.duration?.coerceAtLeast(0L) ?: 0L
                }
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.stop()
            player?.clearMediaItems()
            player?.release()
        }
    }

    // Pause on background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player?.pause()
            else if (event == Lifecycle.Event.ON_RESUME && isPlaying && isPageActive) player?.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Scrubber interaction states
    var isDraggingSeekbar by remember { mutableStateOf(false) }
    var scrubStartPosition by remember { mutableLongStateOf(0L) }
    val seekInteractionSource = remember { MutableInteractionSource() }
    val isSeekPressed by seekInteractionSource.collectIsPressedAsState()
    val isSeekDragged by seekInteractionSource.collectIsDraggedAsState()
    val isSeekInteracting = isDraggingSeekbar || isSeekPressed || isSeekDragged

    // Scrubber animations
    val trackScaleY by animateFloatAsState(
        targetValue = if (isSeekInteracting) 1.7f else 1f,
        animationSpec = tween(150),
        label = "TrackY"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isSeekInteracting) 1.4f else 1f,
        animationSpec = tween(150),
        label = "ThumbScale"
    )

    LaunchedEffect(player, isPlaying, isSeekInteracting) {
        while (true) {
            if (player?.isPlaying == true && !isSeekInteracting) {
                localPosition = player.currentPosition
                localDuration = player.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    // Audio focus & volume: request only when unmuted and active, drop when muted or paused
    LaunchedEffect(isMuted, player, isPageActive, isPlaying) {
        if (player != null) {
            player.volume = if (isMuted) 0f else 1f
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            val shouldHoldAudioFocus = !isMuted && isPageActive && isPlaying
            player.setAudioAttributes(audioAttrs, shouldHoldAudioFocus)
        }
    }

    fun formatMs(ms: Long): String {
        val totalSec = ms / 1000L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isControlsLocked) {
                detectTapGestures(
                    onTap = {
                        if (isControlsLocked) {
                            showLockOverlay = !showLockOverlay
                        } else {
                            showControls = !showControls
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!isControlsLocked) {
                            val w = size.width.toFloat()
                            if (offset.x < w * 0.40f) {
                                // Double tap LEFT: seek -10s
                                val newPos = (localPosition - 10_000L).coerceAtLeast(0L)
                                localPosition = newPos
                                player?.seekTo(newPos)
                                doubleTapSeekDirection = -1
                                doubleTapSeekCounter++
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (offset.x > w * 0.60f) {
                                // Double tap RIGHT: seek +10s
                                val newPos = (localPosition + 10_000L).coerceAtMost(localDuration)
                                localPosition = newPos
                                player?.seekTo(newPos)
                                doubleTapSeekDirection = 1
                                doubleTapSeekCounter++
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else {
                                // Center double tap: pinch-scale toggle
                                coroutineScope.launch {
                                    if (scaleAnim.value > 1.05f) {
                                        launch { scaleAnim.animateTo(1f, tween(250)) }
                                        launch { offsetAnim.animateTo(Offset.Zero, tween(250)) }
                                    } else {
                                        launch { scaleAnim.animateTo(2.5f, tween(250)) }
                                    }
                                }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Video Surface + Frame 0 Bitmap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetAnim.value.x
                    translationY = offsetAnim.value.y
                },
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                AndroidView(
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(
                            R.layout.player_texture_view,
                            null
                        ) as PlayerView).apply {
                            useController = false
                            this.player = player
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.resizeMode = if (isZoomMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (isVideoReady) 1f else 0f }
                )
            }

            // Frame 0 bitmap displayed until first video frame renders (or while swiping)
            if (!isVideoReady && frame0Bitmap != null) {
                Image(
                    bitmap = frame0Bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = if (isZoomMode) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Vertical Edge Gestures (Brightness on Left 35%, Volume on Right 35%) ──
        if (!isControlsLocked) {
            // Left: Screen Brightness Drag Gesture (left 35%)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                showBrightnessHud = true
                                brightnessHudCounter++
                                val delta = -dragAmount / 450f
                                val updated = (currentBrightness + delta).coerceIn(0.01f, 1f)
                                currentBrightness = updated
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = updated
                                    act.window.attributes = lp
                                }
                            }
                        )
                    }
            )

            // Right: Media Volume Drag Gesture (right 35%)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                showVolumeHud = true
                                volumeHudCounter++
                                val delta = -dragAmount / 450f
                                val updated = (currentVolumeRatio + delta).coerceIn(0f, 1f)
                                currentVolumeRatio = updated
                                val targetVol = (updated * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            }
                        )
                    }
            )
        }

        // ── Brightness Floating HUD Pill (Left Center, Borderless, Clean Aesthetic) ──
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(tween(150)) + slideInHorizontally { -it / 2 },
            exit = fadeOut(tween(300)) + slideOutHorizontally { -it / 2 },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(23.dp),
                color = Color.Black.copy(alpha = 0.60f),
                modifier = Modifier
                    .width(46.dp)
                    .height(148.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lucide_ic_sun),
                        contentDescription = "Brightness",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(currentBrightness.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "${(currentBrightness * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Volume Floating HUD Pill (Right Center, Borderless, Clean Aesthetic) ──
        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn(tween(150)) + slideInHorizontally { it / 2 },
            exit = fadeOut(tween(300)) + slideOutHorizontally { it / 2 },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(23.dp),
                color = Color.Black.copy(alpha = 0.60f),
                modifier = Modifier
                    .width(46.dp)
                    .height(148.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (currentVolumeRatio <= 0.01f) R.drawable.lucide_ic_volume_off else R.drawable.lucide_ic_volume_2
                        ),
                        contentDescription = "Volume",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(currentVolumeRatio.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "${(currentVolumeRatio * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Double-Tap +/-10s Seek Ripple Overlay ──
        AnimatedVisibility(
            visible = doubleTapSeekDirection != 0,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.15f, animationSpec = tween(250)),
            modifier = Modifier
                .align(if (doubleTapSeekDirection < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (doubleTapSeekDirection < 0) R.drawable.lucide_ic_rotate_ccw else R.drawable.lucide_ic_rotate_cw
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (doubleTapSeekDirection < 0) "-10s" else "+10s",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Floating Unlock Button when Controls are Locked (Bottom Center, No accent color, No outline)
        AnimatedVisibility(
            visible = isControlsLocked && showLockOverlay,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        isControlsLocked = false
                        showControls = true
                        onGestureLockChanged(false)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lucide_ic_lock),
                    contentDescription = "Unlock Controls",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ── Controls Overlay ──
        AnimatedVisibility(
            visible = showControls && !isControlsLocked,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ── TOP INFO BAR (Shifted up by 7dp; Close & Lock aligned vertically with seekbar ends) ──
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 5.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button (Left edge flush with x=0 of 20dp margin to match seekbar left end)
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = onClose)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lucide_ic_x),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Title & Counter
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = videoTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (totalVideos > 1) {
                            Text(
                                text = "$pageIndex / $totalVideos",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Aspect Ratio Toggle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                isZoomMode = !isZoomMode
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isZoomMode) R.drawable.lucide_ic_minimize else R.drawable.lucide_ic_maximize
                            ),
                            contentDescription = if (isZoomMode) "Fit to Screen" else "Zoom to Fill",
                            tint = if (isZoomMode) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Lock Controls Toggle (Right edge flush with right end of seekbar track)
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                isControlsLocked = true
                                showControls = false
                                showLockOverlay = true
                                onGestureLockChanged(true)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lucide_ic_unlock),
                            contentDescription = "Lock Controls",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // ── MODERN SCRUBBER & ACTION BAR ──
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // Actions Row: Mute flush left above seekbar; Play flush right above seekbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = if (isSeekInteracting) 0f else 1f },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute Toggle (Directly above left end of seekbar track)
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable(enabled = !isSeekInteracting) { isMuted = !isMuted }
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isMuted) R.drawable.lucide_ic_volume_off else R.drawable.lucide_ic_volume_2
                                    ),
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Loop / Repeat Mode Toggle
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable(enabled = !isSeekInteracting) {
                                        isRepeatOne = !isRepeatOne
                                        player?.repeatMode = if (isRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.lucide_ic_repeat),
                                    contentDescription = "Repeat",
                                    tint = if (isRepeatOne) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Speed Selector Chip
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.Black.copy(alpha = 0.40f),
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(enabled = !isSeekInteracting) {
                                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                        val curIdx = speeds.indexOf(selectedSpeed)
                                        val nextIdx = if (curIdx in speeds.indices) (curIdx + 1) % speeds.size else 2
                                        selectedSpeed = speeds[nextIdx]
                                        player?.setPlaybackSpeed(selectedSpeed)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.lucide_ic_gauge),
                                        contentDescription = "Speed",
                                        tint = if (selectedSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "${selectedSpeed}x",
                                        color = if (selectedSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Single Play/Pause Button (Directly above right end of seekbar track)
                        Box(
                            contentAlignment = Alignment.CenterEnd,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(enabled = !isSeekInteracting) {
                                    isPlaying = !isPlaying
                                    if (isPlaying) player?.play() else player?.pause()
                                }
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.lucide_ic_pause else R.drawable.lucide_ic_play
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val maxW = maxWidth
                        val thumbSz = 10.dp
                        val trackH = 3.dp

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
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = formatMs(localPosition), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "($deltaStr)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Background Track (RenderThread-only scaleY)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackH)
                                .graphicsLayer { scaleY = trackScaleY }
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(0.3f))
                        )
                        // Active Played Track (RenderThread-only scaleY)
                        Box(
                            modifier = Modifier
                                .width((maxW * seekProgress).coerceAtLeast(0.dp))
                                .height(trackH)
                                .graphicsLayer { scaleY = trackScaleY }
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                        // Scrubber Thumb (RenderThread-only scaleX, scaleY)
                        Box(
                            modifier = Modifier
                                .offset(x = maxW * seekProgress - thumbSz / 2)
                                .size(thumbSz)
                                .graphicsLayer {
                                    scaleX = thumbScale
                                    scaleY = thumbScale
                                }
                                .clip(CircleShape)
                                .background(Color.White)
                        )

                        // Slider Gesture Hit Area
                        Slider(
                            value = rawSeekProgress,
                            onValueChange = { v ->
                                if (!isDraggingSeekbar) scrubStartPosition = localPosition
                                isDraggingSeekbar = true
                                localPosition = (v * localDuration).toLong()
                            },
                            onValueChangeFinished = {
                                player?.seekTo(localPosition)
                                isDraggingSeekbar = false
                            },
                            interactionSource = seekInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp),
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
