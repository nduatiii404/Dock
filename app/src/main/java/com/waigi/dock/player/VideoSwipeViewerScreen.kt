package com.waigi.dock.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun VideoSwipeViewerScreen(
    videos: List<Uri>,
    initialIndex: Int = 0,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0)),
        pageCount = { videos.size }
    )
    var isGestureLocked by remember { mutableStateOf(false) }

    // Track active extraction jobs so stale jobs are cancelled on fast swipe
    val extractionJobs = remember { mutableMapOf<Int, Job>() }

    // Preload Frame 0 for adjacent videos (current, next, previous)
    LaunchedEffect(pagerState.currentPage, videos) {
        val curr = pagerState.currentPage
        val indicesToPreload = listOf(curr, curr - 1, curr + 1, curr + 2)
            .filter { it in videos.indices }

        // Cancel jobs for indices that are no longer adjacent
        val activeKeys = extractionJobs.keys.toList()
        activeKeys.forEach { idx ->
            if (idx !in indicesToPreload) {
                extractionJobs[idx]?.cancel()
                extractionJobs.remove(idx)
            }
        }

        // Start preloading for adjacent indices
        indicesToPreload.forEach { idx ->
            if (extractionJobs[idx]?.isActive != true && VideoFrame0Manager.getMemoryBitmap(videos[idx].toString()) == null) {
                extractionJobs[idx] = launch {
                    val uri = videos[idx]
                    VideoFrame0Manager.loadOrExtractFrame0(context, uri, uri.toString())
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            extractionJobs.values.forEach { it.cancel() }
            extractionJobs.clear()
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = !isGestureLocked,
        key = { videos[it] },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) { page ->
        val isPageSettled = pagerState.settledPage == page
        val isActivelyScrolling = pagerState.isScrollInProgress
        val videoUri = videos[page]

        LightweightVideoPlayerPage(
            uri = videoUri,
            isPageActive = isPageSettled,
            playWhenActive = isPageSettled && !isActivelyScrolling,
            pageIndex = page + 1,
            totalVideos = videos.size,
            onGestureLockChanged = { locked -> isGestureLocked = locked },
            onClose = onClose
        )
    }
}
