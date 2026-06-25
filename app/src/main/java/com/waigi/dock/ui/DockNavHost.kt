package com.waigi.dock.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import com.waigi.dock.util.PreferenceUtil
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.waigi.dock.ui.navigation.Screen
import com.waigi.dock.ui.navigation.bottomNavTabs
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.waigi.dock.ui.screen.DownloadsScreen
import com.waigi.dock.ui.screen.HistoryScreen
import com.waigi.dock.ui.screen.HomeScreen
import com.waigi.dock.ui.screen.SettingsScreen
import com.waigi.dock.ui.theme.DockTheme
import com.waigi.dock.download.Downloader
import com.waigi.dock.download.TaskState
import androidx.compose.runtime.collectAsState
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

/** Root composable — owns the nav controller, bottom bar, and screen routing. */
@Composable
fun DockNavHost(sharedUrl: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var onboardingStep by remember {
        mutableStateOf(
            if (PreferenceUtil.onboardingCompleted) OnboardingStep.COMPLETED else OnboardingStep.WELCOME
        )
    }

    var clipboardRect by remember { mutableStateOf<Rect?>(null) }
    var downloadsRect by remember { mutableStateOf<Rect?>(null) }
    var historyRect by remember { mutableStateOf<Rect?>(null) }
    var settingsRect by remember { mutableStateOf<Rect?>(null) }

    // Decide whether to show the bottom bar (hide on detail screens)
    val showBottomBar = bottomNavTabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    DockTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            DockBottomBar(
                                currentDestination = currentDestination,
                                onTabSelected = { tab ->
                                    navController.navigate(tab.route) {
                                        // Pop back to start so back stack doesn't grow infinitely
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onTabPositioned = { tab, rect ->
                                    when (tab) {
                                        Screen.Downloads -> downloadsRect = rect
                                        Screen.History -> historyRect = rect
                                        Screen.Settings -> settingsRect = rect
                                        else -> {}
                                    }
                                }
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets.navigationBars,
                ) { innerPadding ->
                    val getTabIndex: (String?) -> Int = { route ->
                        when (route) {
                            Screen.Home.route -> 0
                            Screen.Downloads.route -> 1
                            Screen.History.route -> 2
                            Screen.Settings.route -> 3
                            else -> 0
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        enterTransition = {
                            val initialIndex = getTabIndex(initialState.destination.route)
                            val targetIndex = getTabIndex(targetState.destination.route)
                            val direction = if (targetIndex >= initialIndex) {
                                AnimatedContentTransitionScope.SlideDirection.Start
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.End
                            }
                            slideIntoContainer(direction, tween(NAV_ANIM_DURATION)) +
                                fadeIn(tween(NAV_ANIM_DURATION))
                        },
                        exitTransition = {
                            val initialIndex = getTabIndex(initialState.destination.route)
                            val targetIndex = getTabIndex(targetState.destination.route)
                            val direction = if (targetIndex >= initialIndex) {
                                AnimatedContentTransitionScope.SlideDirection.Start
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.End
                            }
                            slideOutOfContainer(direction, tween(NAV_ANIM_DURATION)) +
                                fadeOut(tween(NAV_ANIM_DURATION))
                        },
                        popEnterTransition = {
                            val initialIndex = getTabIndex(initialState.destination.route)
                            val targetIndex = getTabIndex(targetState.destination.route)
                            val direction = if (targetIndex >= initialIndex) {
                                AnimatedContentTransitionScope.SlideDirection.Start
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.End
                            }
                            slideIntoContainer(direction, tween(NAV_ANIM_DURATION)) +
                                fadeIn(tween(NAV_ANIM_DURATION))
                        },
                        popExitTransition = {
                            val initialIndex = getTabIndex(initialState.destination.route)
                            val targetIndex = getTabIndex(targetState.destination.route)
                            val direction = if (targetIndex >= initialIndex) {
                                AnimatedContentTransitionScope.SlideDirection.Start
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.End
                            }
                            slideOutOfContainer(direction, tween(NAV_ANIM_DURATION)) +
                                fadeOut(tween(NAV_ANIM_DURATION))
                        },
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                sharedUrl = sharedUrl,
                                onClipboardPositioned = { rect ->
                                    clipboardRect = rect
                                }
                            )
                        }
                        composable(Screen.Downloads.route) {
                            DownloadsScreen()
                        }
                        composable(Screen.History.route) {
                            HistoryScreen()
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen()
                        }
                    }
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
                            OnboardingStep.WELCOME -> OnboardingStep.CLIPBOARD
                            OnboardingStep.CLIPBOARD -> OnboardingStep.DOWNLOADS
                            OnboardingStep.DOWNLOADS -> OnboardingStep.HISTORY
                            OnboardingStep.HISTORY -> OnboardingStep.SETTINGS
                            OnboardingStep.SETTINGS -> {
                                PreferenceUtil.updateOnboardingCompleted(true)
                                OnboardingStep.COMPLETED
                            }
                            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED
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
    currentDestination: androidx.navigation.NavDestination?,
    onTabSelected: (Screen) -> Unit,
    onTabPositioned: (Screen, Rect) -> Unit = { _, _ -> },
) {
    // Observe active downloads for the animated icon
    val tasks by Downloader.tasks.collectAsState()
    val hasActiveDownloads = tasks.values.any {
        it.state is TaskState.Downloading ||
        it.state is TaskState.FetchingInfo ||
        it.state is TaskState.Queued
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

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        bottomNavTabs.forEach { tab ->
            val isSelected = currentDestination?.hierarchy
                ?.any { it.route == tab.route } == true
            val isDownloadsTab = tab == Screen.Downloads

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.onGloballyPositioned {
                    onTabPositioned(tab, it.boundsInRoot())
                },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                        contentDescription = tab.label,
                        modifier = if (isDownloadsTab && hasActiveDownloads) {
                            Modifier.graphicsLayer { translationY = bounceY }
                        } else Modifier
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
