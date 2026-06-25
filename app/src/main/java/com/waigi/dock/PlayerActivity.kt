package com.waigi.dock

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.oneuiproject.oneui.R as ouiR
import kotlinx.coroutines.delay

import java.io.File
import kotlin.math.roundToInt

enum class AspectRatioMode(val label: String, val modeValue: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private val isInPipModeState = mutableStateOf(false)
    private var tempSubtitleFiles: List<File> = emptyList()

    fun setTempSubtitleFiles(files: List<File>) {
        this.tempSubtitleFiles = files
    }

    private val pipReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_PIP_PLAY_PAUSE") {
                val player = exoPlayer ?: return
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
                updatePipActions()
            }
        }
    }

    fun setExoPlayer(player: ExoPlayer?) {
        this.exoPlayer = player
    }

    fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val player = exoPlayer ?: return
            val isPlaying = player.isPlaying
            val actionIntent = android.content.Intent("ACTION_PIP_PLAY_PAUSE").apply {
                setPackage(packageName)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                0,
                actionIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val icon = android.graphics.drawable.Icon.createWithResource(
                this,
                if (isPlaying) ouiR.drawable.ic_oui_control_pause else ouiR.drawable.ic_oui_control_play
            )
            val description = if (isPlaying) "Pause" else "Play"
            val action = android.app.RemoteAction(
                icon,
                description,
                description,
                pendingIntent
            )
            
            val videoSize = player.videoSize
            val width = videoSize.width
            val height = videoSize.height
            val paramBuilder = android.app.PictureInPictureParams.Builder()
            if (width > 0 && height > 0) {
                val ratio = width.toFloat() / height.toFloat()
                val finalRatio = ratio.coerceIn(0.418f, 2.39f)
                paramBuilder.setAspectRatio(android.util.Rational((finalRatio * 1000).toInt(), 1000))
            }
            paramBuilder.setActions(listOf(action))
            try {
                setPictureInPictureParams(paramBuilder.build())
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register PiP control receiver
        val filter = android.content.IntentFilter("ACTION_PIP_PLAY_PAUSE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(pipReceiver, filter)
        }
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Make activity full screen and transparent bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUi()

        val filePath = intent.getStringExtra("FILE_PATH") ?: ""
        val title = intent.getStringExtra("TITLE") ?: File(filePath).name
        val isAudio = intent.getBooleanExtra("IS_AUDIO", false)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    PlayerScreen(
                        filePath = filePath,
                        title = title,
                        isAudio = isAudio,
                        isInPipMode = isInPipModeState.value,
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
    }

    private fun hideSystemUi() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        exoPlayer?.release()
        exoPlayer = null
        for (file in tempSubtitleFiles) {
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {}
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    filePath: String,
    title: String,
    isAudio: Boolean,
    isInPipMode: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Scan side-loaded subtitle tracks and apply delay pre-shifting if specified
    val subtitleConfigs = remember {
        val list = mutableListOf<MediaItem.SubtitleConfiguration>()
        val tempFiles = mutableListOf<File>()
        try {
            val videoFile = File(filePath)
            val parentDir = videoFile.parentFile
            val offsetMs = com.waigi.dock.util.PreferenceUtil.subtitleDelay
            if (parentDir != null && parentDir.exists()) {
                val baseName = videoFile.nameWithoutExtension
                val files = parentDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && file.name.startsWith("$baseName.") && file.absolutePath != videoFile.absolutePath) {
                            val ext = file.extension.lowercase()
                            val mimeType = when (ext) {
                                "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
                                "srt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                                "ass", "ssa" -> androidx.media3.common.MimeTypes.TEXT_SSA
                                else -> null
                            }
                            if (mimeType != null) {
                                val parts = file.nameWithoutExtension.split('.')
                                val lang = if (parts.size > 1) parts.last() else "und"
                                
                                val finalFile = if (offsetMs != 0 && (ext == "srt" || ext == "vtt")) {
                                    val tempDir = com.waigi.dock.util.FileUtil.getTempDir()
                                    val tempFile = File(tempDir, "temp_sub_${System.currentTimeMillis()}_${file.name}")
                                    com.waigi.dock.util.FileUtil.shiftSubtitleTimestamps(file, tempFile, offsetMs)
                                    tempFiles.add(tempFile)
                                    tempFile
                                } else {
                                    file
                                }
                                
                                val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(finalFile))
                                    .setMimeType(mimeType)
                                    .setLanguage(lang)
                                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                                    .build()
                                list.add(config)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        (context as? PlayerActivity)?.setTempSubtitleFiles(tempFiles)
        list
    }

    // ExoPlayer initialization
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(if (isAudio) androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC else androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
            playWhenReady = true
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(filePath)))
                .setSubtitleConfigurations(subtitleConfigs)
                .build()
            setMediaItem(mediaItem)
            prepare()
        }
    }

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }

    // Player Controls Layout
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var screenshotBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(screenshotBitmap) {
        if (screenshotBitmap != null) {
            delay(3000)
            screenshotBitmap = null
        }
    }

    // Gestures Overlay Values
    var brightnessGestureVal by remember { mutableFloatStateOf(-1f) }
    var volumeGestureVal by remember { mutableFloatStateOf(-1f) }
    var horizontalGestureVal by remember { mutableFloatStateOf(-1f) }
    var dragStartSeekVal by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    val thumbScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isInteracting) 1.5f else 1.0f,
        label = "ThumbScale"
    )
    val trackScaleY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isInteracting) 1.5f else 1.0f,
        label = "TrackScale"
    )

    // Auto-hide controls handler
    LaunchedEffect(showControls, isPlaying, isDraggingSlider, isInPipMode) {
        if (isInPipMode) {
            showControls = false
        } else if (showControls && isPlaying && !isDraggingSlider) {
            delay(3000)
            showControls = false
        }
    }

    // Progress updates tracker
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition
            delay(200)
        }
    }

    // Listeners definition
    val playerListener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
                (context as? PlayerActivity)?.updatePipActions()
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val activity = context as? PlayerActivity
        activity?.setExoPlayer(exoPlayer)
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
            activity?.setExoPlayer(null)
        }
    }

    // Handle screen interactions/gestures
    val onScreenClick = {
        if (!isLocked) {
            showControls = !showControls
        } else {
            showControls = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                if (isLocked) {
                    detectVerticalDragGestures(
                        onDragEnd = { showControls = false }
                    ) { _, _ ->
                        showControls = true
                    }
                    return@pointerInput
                }
 
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val isLeft = offset.x < size.width / 2f
                        if (isLeft) {
                            val window = (context as? ComponentActivity)?.window
                            val lp = window?.attributes
                            val currentBrightness = lp?.screenBrightness ?: 0.5f
                            brightnessGestureVal = if (currentBrightness < 0) 0.5f else currentBrightness
                        } else {
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                            volumeGestureVal = currentVol / maxVol
                        }
                    },
                    onDragEnd = {
                        brightnessGestureVal = -1f
                        volumeGestureVal = -1f
                    },
                    onDragCancel = {
                        brightnessGestureVal = -1f
                        volumeGestureVal = -1f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val totalHeight = size.height.toFloat()
                    val delta = -dragAmount / totalHeight
 
                    if (brightnessGestureVal >= 0) {
                        brightnessGestureVal = (brightnessGestureVal + delta).coerceIn(0f, 1f)
                        val window = (context as? ComponentActivity)?.window
                        val lp = window?.attributes
                        if (lp != null) {
                            lp.screenBrightness = brightnessGestureVal
                            window.attributes = lp
                        }
                    } else if (volumeGestureVal >= 0) {
                        volumeGestureVal = (volumeGestureVal + delta).coerceIn(0f, 1f)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVol = (volumeGestureVal * maxVol).roundToInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    }
                }
            }
            .pointerInput(isLocked, isInPipMode) {
                if (isInPipMode || isLocked) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragStartSeekVal = currentPosition
                        horizontalGestureVal = currentPosition.toFloat()
                    },
                    onDragEnd = {
                        exoPlayer.seekTo(horizontalGestureVal.toLong())
                        horizontalGestureVal = -1f
                    },
                    onDragCancel = {
                        horizontalGestureVal = -1f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val totalWidth = size.width.toFloat()
                    val deltaMs = (dragAmount / totalWidth) * duration.toFloat()
                    horizontalGestureVal = (horizontalGestureVal + deltaMs).coerceIn(0f, duration.toFloat())
                }
            }
            .pointerInput(isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onTap = { onScreenClick() },
                    onDoubleTap = { offset ->
                        if (!isLocked) {
                            val width = size.width.toFloat()
                            val x = offset.x
                            if (x < width * 0.35f) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                                Toast.makeText(context, "Rewind 10s", Toast.LENGTH_SHORT).show()
                            } else if (x > width * 0.65f) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration))
                                Toast.makeText(context, "Fast Forward 10s", Toast.LENGTH_SHORT).show()
                            } else {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    Toast.makeText(context, "Paused", Toast.LENGTH_SHORT).show()
                                } else {
                                    exoPlayer.play()
                                    Toast.makeText(context, "Playing", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
    ) {
        if (isAudio) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Playing Audio File",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val playerView = android.view.LayoutInflater.from(ctx).inflate(
                        com.waigi.dock.R.layout.player_view, null
                    ) as PlayerView
                    playerViewRef = playerView
                    playerView.apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = aspectRatioMode.modeValue

                        // Apply custom subtitle styling
                        val subtitleFontSize = com.waigi.dock.util.PreferenceUtil.subtitleFontSize
                        val subtitleColorIndex = com.waigi.dock.util.PreferenceUtil.subtitleColor
                        val subtitleBgOpacity = com.waigi.dock.util.PreferenceUtil.subtitleBackgroundOpacity
                        val subtitleBorderStyleIndex = com.waigi.dock.util.PreferenceUtil.subtitleBorderStyle

                        val foreColor = when (subtitleColorIndex) {
                            1 -> android.graphics.Color.YELLOW
                            2 -> android.graphics.Color.CYAN
                            3 -> android.graphics.Color.GREEN
                            else -> android.graphics.Color.WHITE
                        }
                        val bgColor = android.graphics.Color.argb(
                            (subtitleBgOpacity * 2.55f).toInt(),
                            0, 0, 0
                        )
                        val edgeType = when (subtitleBorderStyleIndex) {
                            0 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                            2 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                            3 -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED
                            else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        }
                        val style = androidx.media3.ui.CaptionStyleCompat(
                            foreColor,
                            bgColor,
                            android.graphics.Color.TRANSPARENT,
                            edgeType,
                            android.graphics.Color.BLACK,
                            null
                        )
                        subtitleView?.setStyle(style)
                        subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleFontSize.toFloat())
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = aspectRatioMode.modeValue
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gesture HUD Indicators
        if (brightnessGestureVal >= 0) {
            GestureHud(
                iconRes = ouiR.drawable.ic_oui_brightness,
                label = "Brightness",
                value = brightnessGestureVal
            )
        }
        if (volumeGestureVal >= 0) {
            GestureHud(
                iconRes = ouiR.drawable.ic_oui_media_volume,
                label = "Volume",
                value = volumeGestureVal
            )
        }

        // Seek Gesture HUD
        if (horizontalGestureVal >= 0) {
            GestureHud(
                iconRes = ouiR.drawable.ic_oui_video,
                label = "Seek: ${formatTime(horizontalGestureVal.toLong())}",
                value = horizontalGestureVal / duration.coerceAtLeast(1L).toFloat()
            )
        }

        // Buffer Loading indicator
        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        // Overlay Controllers (Top / Middle / Bottom)
        val isEnded = playbackState == Player.STATE_ENDED || (duration > 0 && currentPosition >= duration)
        val showOverlay = (showControls || isEnded || isLocked) && !isInPipMode
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLocked) {
                    IconButton(
                        onClick = { isLocked = false; showControls = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 36.dp)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            painter = painterResource(id = ouiR.drawable.ic_oui_unlock),
                            contentDescription = "Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                } else {
                    if (showControls) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                    )
                                )
                                .align(Alignment.TopCenter)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                    )
                                )
                                .align(Alignment.BottomCenter)
                        )
                    }

                    if (showControls) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(start = 4.dp, end = 16.dp, top = 32.dp, bottom = 32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    painter = painterResource(id = ouiR.drawable.ic_oui_close),
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (!isAudio) {
                                val ratioIcon = if (aspectRatioMode == AspectRatioMode.FILL) {
                                    com.waigi.dock.R.drawable.ic_fullscreen_exit
                                } else {
                                    com.waigi.dock.R.drawable.ic_fullscreen_enter
                                }
                                IconButton(onClick = {
                                    aspectRatioMode = when (aspectRatioMode) {
                                        AspectRatioMode.FIT -> AspectRatioMode.FILL
                                        AspectRatioMode.FILL -> AspectRatioMode.STRETCH
                                        AspectRatioMode.STRETCH -> AspectRatioMode.FIT
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(id = ratioIcon),
                                        contentDescription = "Aspect Ratio",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            var showSpeedMenu by remember { mutableStateOf(false) }
                            Box {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (playbackSpeed != 1.0f) {
                                        Text(
                                            text = "${playbackSpeed}x",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                    IconButton(onClick = { showSpeedMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Speed,
                                            contentDescription = "Speed",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                MaterialTheme(
                                    colorScheme = MaterialTheme.colorScheme.copy(
                                        surface = Color.Black,
                                        surfaceDim = Color.Black,
                                        surfaceContainer = Color.Black,
                                        surfaceContainerLow = Color.Black,
                                        surfaceContainerHigh = Color.Black,
                                        onSurface = Color.White
                                    )
                                ) {
                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = { showSpeedMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = Color.Black,
                                        modifier = Modifier
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                    ) {
                                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                            DropdownMenuItem(
                                                text = { Text("${speed}x", color = Color.White) },
                                                onClick = {
                                                    playbackSpeed = speed
                                                    exoPlayer.setPlaybackSpeed(speed)
                                                    showSpeedMenu = false
                                                },
                                                colors = androidx.compose.material3.MenuDefaults.itemColors(
                                                    textColor = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val showCenterControls = (showControls || isEnded) && !isInPipMode

                    AnimatedVisibility(
                        visible = showCenterControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showControls) {
                                IconButton(
                                    onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Replay10,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isEnded) {
                                        exoPlayer.seekTo(0)
                                        exoPlayer.play()
                                    } else {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .then(
                                        if (!showControls && isEnded) Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        else Modifier
                                    )
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isEnded) ouiR.drawable.ic_oui_refresh
                                             else if (isPlaying) ouiR.drawable.ic_oui_control_pause
                                             else ouiR.drawable.ic_oui_control_play
                                    ),
                                    contentDescription = if (isEnded) "Replay" else "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(64.dp)
                                )
                            }

                            if (showControls) {
                                IconButton(
                                    onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(duration)) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showControls) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp, top = 24.dp)
                        ) {
                            // Custom Playback Slider
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(22.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val maxWidthDp = maxWidth
                                val clampedValue = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                                val thumbSize = 12.dp
                                val trackHeight = 3.dp

                                // 1. Inactive Track Line (Background)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp)
                                        .height(trackHeight)
                                        .graphicsLayer {
                                            scaleY = trackScaleY
                                        }
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(Color.White.copy(alpha = 0.3f))
                                )

                                // 2. Active Track Line (Progress Fill)
                                Box(
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .width(((maxWidthDp - 20.dp) * clampedValue).coerceAtLeast(0.dp))
                                        .height(trackHeight)
                                        .graphicsLayer {
                                            scaleY = trackScaleY
                                        }
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(Color.White)
                                )

                                // 3. Slider Thumb
                                Box(
                                    modifier = Modifier
                                        .offset(x = 10.dp + (maxWidthDp - 20.dp) * clampedValue - thumbSize / 2)
                                        .size(thumbSize)
                                        .graphicsLayer {
                                            scaleX = thumbScale
                                            scaleY = thumbScale
                                        }
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )

                                // 4. Underlying Slider for gesture detection
                                Slider(
                                    value = clampedValue,
                                    onValueChange = {
                                        isDraggingSlider = true
                                        currentPosition = (it * duration).toLong()
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(currentPosition)
                                        isDraggingSlider = false
                                    },
                                    valueRange = 0f..1f,
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp),
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

                            Spacer(modifier = Modifier.height(2.dp))

                            // Separated Timestamps Row right below the slider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = formatTime(duration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Control Row with equal spacing from far left to far right
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Lock Icon
                                IconButton(onClick = { isLocked = true }) {
                                    Icon(
                                        painter = painterResource(id = ouiR.drawable.ic_oui_lock),
                                        contentDescription = "Lock",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 2. Subtitles Toggle Icon
                                IconButton(onClick = {
                                    subtitlesEnabled = !subtitlesEnabled
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                        .build()
                                }) {
                                    Icon(
                                        painter = painterResource(id = ouiR.drawable.ic_oui_live_caption),
                                        contentDescription = "Subtitles",
                                        tint = if (subtitlesEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 3. Screenshot Capture Icon
                                IconButton(onClick = {
                                    val activity = context as? ComponentActivity
                                    val currentPV = playerViewRef
                                    if (activity != null && currentPV != null) {
                                        takePlayerScreenshot(currentPV, activity) { bitmap ->
                                            screenshotBitmap = bitmap
                                        }
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(id = ouiR.drawable.ic_oui_screenshot),
                                        contentDescription = "Screenshot",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 4. Screen Rotation Icon
                                IconButton(onClick = {
                                    val activity = context as? ComponentActivity
                                    if (activity != null) {
                                        val newOrientation = if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                        activity.requestedOrientation = newOrientation
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(id = ouiR.drawable.ic_oui_rotate),
                                        contentDescription = "Rotate Screen",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 5. Pop Up View (PiP) Icon
                                IconButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val activity = context as? ComponentActivity
                                        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                                        val isPipAllowed = appOpsManager?.checkOpNoThrow(
                                            android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                                            android.os.Process.myUid(),
                                            context.packageName
                                        ) == android.app.AppOpsManager.MODE_ALLOWED

                                        if (!isPipAllowed) {
                                            val intent = android.content.Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", Uri.parse("package:${context.packageName}"))
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val genericIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                                    context.startActivity(genericIntent)
                                                } catch (ex: Exception) {
                                                    Toast.makeText(context, "Could not open overlay settings", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            Toast.makeText(context, "Please enable Picture-in-Picture for Dock", Toast.LENGTH_LONG).show()
                                        } else {
                                            val paramBuilder = android.app.PictureInPictureParams.Builder()
                                            val player = exoPlayer
                                            if (player != null) {
                                                val videoSize = player.videoSize
                                                val width = videoSize.width
                                                val height = videoSize.height
                                                if (width > 0 && height > 0) {
                                                    val ratio = width.toFloat() / height.toFloat()
                                                    val finalRatio = ratio.coerceIn(0.418f, 2.39f)
                                                    paramBuilder.setAspectRatio(android.util.Rational((finalRatio * 1000).toInt(), 1000))
                                                }
                                            }
                                            // Set initial play/pause actions
                                            val isPlaying = player?.isPlaying == true
                                            val actionIntent = android.content.Intent("ACTION_PIP_PLAY_PAUSE").apply {
                                                setPackage(context.packageName)
                                            }
                                            val pendingIntent = android.app.PendingIntent.getBroadcast(
                                                context,
                                                0,
                                                actionIntent,
                                                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                            )
                                            val icon = android.graphics.drawable.Icon.createWithResource(
                                                context,
                                                if (isPlaying) ouiR.drawable.ic_oui_control_pause else ouiR.drawable.ic_oui_control_play
                                            )
                                            val description = if (isPlaying) "Pause" else "Play"
                                            val action = android.app.RemoteAction(
                                                icon,
                                                description,
                                                description,
                                                pendingIntent
                                            )
                                            paramBuilder.setActions(listOf(action))

                                            try {
                                                activity?.enterPictureInPictureMode(paramBuilder.build())
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error entering PiP", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Picture in Picture not supported on this version", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        painter = painterResource(id = ouiR.drawable.ic_oui_popup_player),
                                        contentDescription = "Pop Up View",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Screenshot Preview Overlay Card
        AnimatedVisibility(
            visible = screenshotBitmap != null,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 120.dp)
        ) {
            val bitmap = screenshotBitmap
            if (bitmap != null) {
                val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                        .clickable { screenshotBitmap = null },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Screenshot Preview",
                        modifier = Modifier
                            .then(
                                if (bitmap.width > bitmap.height) {
                                    Modifier.width(80.dp).aspectRatio(bitmapRatio)
                                } else {
                                    Modifier.height(80.dp).aspectRatio(bitmapRatio)
                                }
                            )
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Column {
                        Text(
                            text = "Screenshot captured",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Saved to Pictures/Dock",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GestureHud(
    iconRes: Int,
    label: String,
    value: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {} // Block click through
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Progress Bar representing level
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(value)
                        .background(Color.White)
                )
            }
        }
    }
}

private fun takePlayerScreenshot(playerView: PlayerView, activity: ComponentActivity, onComplete: (android.graphics.Bitmap) -> Unit) {
    val textureView = playerView.videoSurfaceView as? android.view.TextureView
    val videoBitmap = textureView?.bitmap
    if (videoBitmap != null) {
        saveBitmapToStorage(activity, videoBitmap)
        onComplete(videoBitmap)
    } else {
        takeActivityScreenshot(activity, onComplete)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun takeActivityScreenshot(activity: ComponentActivity, onComplete: (android.graphics.Bitmap) -> Unit) {
    val window = activity.window
    val context = activity
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val bitmap = android.graphics.Bitmap.createBitmap(window.decorView.width, window.decorView.height, android.graphics.Bitmap.Config.ARGB_8888)
        val locationOfViewInWindow = IntArray(2)
        window.decorView.getLocationInWindow(locationOfViewInWindow)
        try {
            android.view.PixelCopy.request(
                window,
                android.graphics.Rect(
                    locationOfViewInWindow[0],
                    locationOfViewInWindow[1],
                    locationOfViewInWindow[0] + window.decorView.width,
                    locationOfViewInWindow[1] + window.decorView.height
                ),
                bitmap,
                { copyResult ->
                    if (copyResult == android.view.PixelCopy.SUCCESS) {
                        saveBitmapToStorage(context, bitmap)
                        activity.runOnUiThread {
                            onComplete(bitmap)
                        }
                    } else {
                        activity.runOnUiThread {
                            Toast.makeText(context, "Screenshot failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Screenshot not supported on this Android version", Toast.LENGTH_SHORT).show()
    }
}

private fun saveBitmapToStorage(context: Context, bitmap: android.graphics.Bitmap) {
    val filename = "Dock_Screenshot_${System.currentTimeMillis()}.png"
    var fos: java.io.OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Dock")
            }
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                fos = resolver.openOutputStream(imageUri)
            }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/Dock"
            val dir = File(imagesDir)
            if (!dir.exists()) dir.mkdirs()
            val image = File(dir, filename)
            fos = java.io.FileOutputStream(image)
        }
        fos?.use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            (context as? ComponentActivity)?.runOnUiThread {
                Toast.makeText(context, "Screenshot saved to Pictures/Dock", Toast.LENGTH_LONG).show()
            }
        }
    } catch (e: Exception) {
        (context as? ComponentActivity)?.runOnUiThread {
            Toast.makeText(context, "Failed to save screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
