package com.waigi.dock.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * All navigation destinations in Dock.
 * Bottom-tab destinations carry [icon], [selectedIcon], and [label] directly
 * so they can be iterated generically in the bottom bar.
 */
sealed class Screen(
    val route: String,
    val label: String = "",
    val icon: ImageVector = Icons.Outlined.Home,
    val selectedIcon: ImageVector = Icons.Filled.Home,
) {
    // ── Bottom Nav Tabs ───────────────────────────────────────────────────────
    data object Home : Screen(
        route = "home",
        label = "Home",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    )

    data object Downloads : Screen(
        route = "downloads",
        label = "Downloads",
        icon = Icons.Outlined.Download,
        selectedIcon = Icons.Filled.Download,
    )

    data object History : Screen(
        route = "history",
        label = "History",
        icon = Icons.Outlined.History,
        selectedIcon = Icons.Filled.History,
    )

    data object Settings : Screen(
        route = "settings",
        label = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    )

    // ── Detail destinations (no bottom bar tab) ───────────────────────────────
    data object FormatPicker : Screen(route = "format_picker/{taskId}") {
        fun createRoute(taskId: String) = "format_picker/$taskId"
    }
}

/** The four tabs shown in the bottom navigation bar, in display order. */
val bottomNavTabs: List<Screen> = listOf(
    Screen.Home,
    Screen.Downloads,
    Screen.History,
    Screen.Settings,
)
