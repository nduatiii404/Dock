package com.waigi.dock.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.zIndex
import com.waigi.dock.player.VideoSwipeViewerScreen
import com.waigi.dock.download.Downloader
import com.waigi.dock.download.TaskState
import com.waigi.dock.ui.navigation.Screen
import com.waigi.dock.ui.navigation.bottomNavTabs
import com.waigi.dock.ui.screen.DownloadsScreen
import com.waigi.dock.ui.screen.HistoryScreen
import com.waigi.dock.ui.screen.HomeScreen
import com.waigi.dock.ui.screen.SettingsScreen
import com.waigi.dock.ui.theme.DockTheme
import com.waigi.dock.util.PreferenceUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NAV_ANIM_DURATION = 300

private enum class OnboardingStep {
    WELCOME,
    CLIPBOARD,
    DOWNLOADS,
    HISTORY,
    SETTINGS,
    COMPLETED
}

/** Root composable — owns the pager state, nav controller (for detail screens), bottom bar, and screen routing. */
@Composable
fun DockNavHost(
    sharedUrl: String? = null,
    navigateTo: String? = null,
    onNavigationHandled: () -> Unit = {}
) {
    // NavController is kept solely for non-bottom-tab detail screens (e.g. FormatPicker)
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    // Map route name -> pager page index
    fun routeToPage(route: String?): Int = when (route) {
        Screen.Home.route      -> 0
        Screen.Downloads.route -> 1
        Screen.History.route   -> 2
        Screen.Settings.route  -> 3
        else                   -> -1
    }

    val pagerState = rememberPagerState(initialPage = 0) { bottomNavTabs.size }

    val fadeAlpha = remember { Animatable(1f) }
    val incomingScale = remember { Animatable(1f) }

    // Called exclusively from bottom-bar tab taps (not swipe)
    fun navigateByClick(targetPage: Int) {
        if (targetPage == pagerState.currentPage) return
        coroutineScope.launch {
            // 1. Fade out the current page quickly (90ms)
            fadeAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing)
            )
            
            // 2. Snap incoming scale to 96% and switch the page instantly while fully transparent
            incomingScale.snapTo(0.96f)
            pagerState.scrollToPage(targetPage)
            
            // 3. Wait for the new page to recompose and render its first frame while transparent (60ms)
            delay(60)
            
            // 4. Fade in and scale up the new page concurrently (210ms)
            launch {
                fadeAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 210, easing = LinearOutSlowInEasing)
                )
            }
            launch {
                incomingScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 210, easing = LinearOutSlowInEasing)
                )
            }
        }
    }

    // When `navigateTo` targets a bottom-tab route, use the click-fade path
    LaunchedEffect(navigateTo) {
        if (navigateTo != null) {
            val page = routeToPage(navigateTo)
            if (page >= 0) {
                navigateByClick(page)
            } else {
                // Non-tab destination (e.g. format_picker) — use navController
                navController.navigate(navigateTo) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onNavigationHandled()
        }
    }

    var onboardingStep by remember {
        mutableStateOf(
            if (PreferenceUtil.onboardingCompleted) OnboardingStep.COMPLETED else OnboardingStep.WELCOME
        )
    }

    var clipboardRect by remember { mutableStateOf<Rect?>(null) }
    var downloadsRect by remember { mutableStateOf<Rect?>(null) }
    var historyRect by remember { mutableStateOf<Rect?>(null) }
    var settingsRect by remember { mutableStateOf<Rect?>(null) }

    var activeVideoPlaylist by remember { mutableStateOf<List<Uri>?>(null) }
    var activeVideoInitialIndex by remember { mutableIntStateOf(0) }

    // Helper: render the screen for a given page index
    @Composable
    fun PageContent(page: Int) {
        when (page) {
            0 -> HomeScreen(
                sharedUrl = sharedUrl,
                onClipboardPositioned = { rect -> clipboardRect = rect }
            )
            1 -> DownloadsScreen()
            2 -> HistoryScreen(
                onPlayVideo = { uris, initialIndex ->
                    activeVideoPlaylist = uris
                    activeVideoInitialIndex = initialIndex
                }
            )
            3 -> SettingsScreen()
        }
    }

    val isFloatingNav by PreferenceUtil.floatingBottomNavFlow.collectAsState()

    DockTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Scaffold(
                    bottomBar = {
                        if (activeVideoPlaylist == null) {
                            DockBottomBar(
                                pagerState = pagerState,
                                isFloating = isFloatingNav,
                                onTabSelected = { page -> navigateByClick(page) },
                                onTabPositioned = { tab, rect ->
                                    when (tab) {
                                        Screen.Downloads -> downloadsRect = rect
                                        Screen.History   -> historyRect = rect
                                        Screen.Settings  -> settingsRect = rect
                                        else             -> {}
                                    }
                                }
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // ── HorizontalPager (handles swipe gestures & animated click transitions) ──
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = fadeAlpha.value
                                    scaleX = incomingScale.value
                                    scaleY = incomingScale.value
                                },
                            beyondViewportPageCount = 1,
                            userScrollEnabled = activeVideoPlaylist == null,
                            key = { it },
                        ) { page ->
                            PageContent(page)
                        }
                    }
                }
            }

            // Fullscreen Lightweight Video Player Overlay (Covers bottom nav and tabs completely)
            activeVideoPlaylist?.let { playlist ->
                BackHandler {
                    activeVideoPlaylist = null
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(999f)
                ) {
                    VideoSwipeViewerScreen(
                        videos = playlist,
                        initialIndex = activeVideoInitialIndex,
                        onClose = { activeVideoPlaylist = null }
                    )
                }
            }

            if (onboardingStep != OnboardingStep.COMPLETED) {
                OnboardingOverlay(
                    step = onboardingStep,
                    clipboardRect = clipboardRect,
                    downloadsRect = downloadsRect,
                    historyRect = historyRect,
                    settingsRect = settingsRect,
                    onNext = {
                        onboardingStep = when (onboardingStep) {
                            OnboardingStep.WELCOME    -> OnboardingStep.CLIPBOARD
                            OnboardingStep.CLIPBOARD  -> OnboardingStep.DOWNLOADS
                            OnboardingStep.DOWNLOADS  -> OnboardingStep.HISTORY
                            OnboardingStep.HISTORY    -> OnboardingStep.SETTINGS
                            OnboardingStep.SETTINGS   -> {
                                PreferenceUtil.updateOnboardingCompleted(true)
                                OnboardingStep.COMPLETED
                            }
                            OnboardingStep.COMPLETED  -> OnboardingStep.COMPLETED
                        }
                    },
                    onSkip = {
                        PreferenceUtil.updateOnboardingCompleted(true)
                        onboardingStep = OnboardingStep.COMPLETED
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingOverlay(
    step: OnboardingStep,
    clipboardRect: Rect?,
    downloadsRect: Rect?,
    historyRect: Rect?,
    settingsRect: Rect?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    if (step == OnboardingStep.COMPLETED) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true, onClick = {})
    ) {
        if (step == OnboardingStep.WELCOME) {
            WelcomeScreenOverlay(onStart = onNext, onSkip = onSkip)
        } else {
            val activeRect = when (step) {
                OnboardingStep.CLIPBOARD -> clipboardRect
                OnboardingStep.DOWNLOADS -> downloadsRect
                OnboardingStep.HISTORY -> historyRect
                OnboardingStep.SETTINGS -> settingsRect
                else -> null
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = 0.99f)
            ) {
                drawRect(Color.Black.copy(alpha = 0.82f))

                activeRect?.let { rect ->
                    val padding = 8.dp.toPx()
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(rect.left - padding, rect.top - padding),
                        size = Size(rect.width + padding * 2, rect.height + padding * 2),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )
                }
            }

            val title = when (step) {
                OnboardingStep.CLIPBOARD -> "Instant Clipboard Paste"
                OnboardingStep.DOWNLOADS -> "Active Downloads"
                OnboardingStep.HISTORY -> "Search & Download History"
                OnboardingStep.SETTINGS -> "Advanced Settings"
                else -> ""
            }

            val body = when (step) {
                OnboardingStep.CLIPBOARD -> "Tap here to quickly paste a URL from your clipboard. You can also turn on Auto-Paste in Settings to let Dock paste links automatically."
                OnboardingStep.DOWNLOADS -> "Monitor your active downloads in real-time, view speeds, and play downloaded files directly."
                OnboardingStep.HISTORY -> "Access your search history and completed downloads, replay them, or clear items anytime."
                OnboardingStep.SETTINGS -> "Configure preferred formats, download paths, yt-dlp arguments, and customize subtitle styles."
                else -> ""
            }

            val stepIndex = when (step) {
                OnboardingStep.CLIPBOARD -> 0
                OnboardingStep.DOWNLOADS -> 1
                OnboardingStep.HISTORY -> 2
                OnboardingStep.SETTINGS -> 3
                else -> 0
            }

            val icon = when (step) {
                OnboardingStep.CLIPBOARD -> Icons.Filled.ContentPaste
                OnboardingStep.DOWNLOADS -> Icons.Filled.Download
                OnboardingStep.HISTORY -> Icons.Filled.History
                OnboardingStep.SETTINGS -> Icons.Filled.Settings
                else -> Icons.Filled.ContentPaste
            }

            val isTargetAtBottom = activeRect?.let { it.top > 400f } ?: true
            val alignmentModifier = if (isTargetAtBottom) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 20.dp, end = 20.dp)
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 220.dp, start = 20.dp, end = 20.dp)
            }

            Card(
                modifier = alignmentModifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF141414)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(4) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (i == stepIndex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = onSkip) {
                                Text("Skip", color = Color.Gray)
                            }
                            Button(
                                onClick = onNext,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (step == OnboardingStep.SETTINGS) "Finish" else "Next",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreenOverlay(
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = com.waigi.dock.R.drawable.ic_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to Dock",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your ultimate offline stream companion.\nStash your streams, zero strings attached.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.Gray
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Start Quick Tour",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip and get started",
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun DockBottomBar(
    pagerState: PagerState,
    isFloating: Boolean,
    onTabSelected: (Int) -> Unit,
    onTabPositioned: (Screen, Rect) -> Unit = { _, _ -> },
) {
    val coroutineScope = rememberCoroutineScope()

    // Observe active downloads for the animated icon
    val tasks by Downloader.tasks.collectAsState()
    val hasActiveDownloads = tasks.values.any {
        it.state is TaskState.Downloading ||
        it.state is TaskState.FetchingInfo ||
        it.state is TaskState.Queued ||
        it.state is TaskState.Merging
    }

    val infiniteTransition = rememberInfiniteTransition(label = "download_bounce")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "download_icon_bounce"
    )

    // ── Live Animation transitions between Floating and Fixed mode ──
    val animHorizontalPadding by animateDpAsState(
        targetValue = if (isFloating) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "nav_horiz_padding"
    )
    val animBottomPadding by animateDpAsState(
        targetValue = if (isFloating) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "nav_bottom_padding"
    )
    val animCornerRadius by animateDpAsState(
        targetValue = if (isFloating) 26.dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "nav_corner_radius"
    )
    val animElevation by animateDpAsState(
        targetValue = if (isFloating) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "nav_elevation"
    )
    val animBorderAlpha by animateFloatAsState(
        targetValue = if (isFloating) 0.35f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "nav_border_alpha"
    )
    val animContainerColor by animateColorAsState(
        targetValue = if (isFloating) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f) else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 350),
        label = "nav_container_color"
    )

    val continuousPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (bottomNavTabs.size - 1).toFloat())
    val isScrollInProgress = pagerState.isScrollInProgress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = animHorizontalPadding, vertical = animBottomPadding)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(animCornerRadius),
            color = animContainerColor,
            border = if (animBorderAlpha > 0.01f) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = animBorderAlpha))
            } else null,
            shadowElevation = animElevation,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val tabCount = bottomNavTabs.size
                val tabWidth = maxWidth / tabCount
                val pillWidth = tabWidth - 6.dp
                val pillHeight = 58.dp // 64dp bar - 3dp top - 3dp bottom = 58dp
                val pillCornerRadius = if (animCornerRadius > 3.dp) {
                    (animCornerRadius - 3.dp)
                } else {
                    12.dp
                }

                val density = LocalDensity.current
                val tabWidthPx = with(density) { tabWidth.toPx() }.coerceAtLeast(1f)
                val pageSizePx = pagerState.layoutInfo.pageSize.toFloat().coerceAtLeast(1f)

                // The target position follows continuous page offset 1:1 during swipes and gestures
                val targetPillX = (continuousPage * tabWidth.value).dp + 3.dp
                val animatedPillX by animateDpAsState(
                    targetValue = targetPillX,
                    animationSpec = if (isScrollInProgress) snap() else spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    ),
                    label = "tab_pill_x"
                )

                // ── The full-height flowing indicator pill covering both icon and text ──
                Box(
                    modifier = Modifier
                        .offset(x = animatedPillX, y = 3.dp)
                        .width(pillWidth)
                        .height(pillHeight)
                        .clip(RoundedCornerShape(pillCornerRadius))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                )

                // ── Tab items rendered on top of the sliding indicator pill & supporting hold-and-slide ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(tabWidthPx, pageSizePx) {
                            detectHorizontalDragGestures(
                                onDragStart = { },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    // Dragging right moves pill right 1:1 with finger
                                    val pagerDeltaPx = (dragAmount / tabWidthPx) * pageSizePx
                                    pagerState.dispatchRawDelta(pagerDeltaPx)
                                },
                                onDragEnd = {
                                    val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                        .roundToInt()
                                        .coerceIn(0, bottomNavTabs.size - 1)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(target)
                                    }
                                },
                                onDragCancel = {
                                    val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                        .roundToInt()
                                        .coerceIn(0, bottomNavTabs.size - 1)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(target)
                                    }
                                }
                            )
                        }
                ) {
                    bottomNavTabs.forEachIndexed { index, tab ->
                        val isSelected = index == pagerState.currentPage
                        val isDownloadsTab = tab == Screen.Downloads

                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tab_icon_color"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tab_text_color"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onTabSelected(index) }
                                )
                                .onGloballyPositioned {
                                    onTabPositioned(tab, it.boundsInRoot())
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    tint = iconColor,
                                    modifier = if (isDownloadsTab && hasActiveDownloads) {
                                        Modifier.size(24.dp).graphicsLayer { translationY = bounceY }
                                    } else {
                                        Modifier.size(24.dp)
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = textColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
