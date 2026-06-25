package com.waigi.dock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.waigi.dock.util.ACCENT_COLOR_KEY
import com.waigi.dock.util.BACKGROUND_STYLE_KEY
import com.waigi.dock.util.BG_AMOLED
import com.waigi.dock.util.BG_DARK
import com.waigi.dock.util.BG_DYNAMIC
import com.waigi.dock.util.PreferenceUtil.getInt

// ── Background style constants ────────────────────────────────────────────────
// Defined in PreferenceUtil:  BG_AMOLED=0, BG_DARK=1, BG_DYNAMIC=2

private fun buildDarkScheme(accent: AccentPalette, amoled: Boolean) = darkColorScheme(
    primary            = accent.main,
    onPrimary          = accent.onMain,
    primaryContainer   = accent.container,
    onPrimaryContainer = accent.main.copy(alpha = 0.8f),
    secondary          = accent.main.copy(alpha = 0.7f),
    onSecondary        = accent.onMain,
    tertiary           = accent.main.copy(alpha = 0.5f),
    background         = if (amoled) Black else DarkBg,
    onBackground       = OnSurface,
    surface            = if (amoled) Surface800 else DarkSurface,
    onSurface          = OnSurface,
    surfaceVariant     = if (amoled) Surface600 else DarkSurfaceVar,
    onSurfaceVariant   = OnSurfaceVar,
    surfaceContainer   = if (amoled) Surface700 else DarkSurface,
    surfaceContainerHigh = if (amoled) Surface600 else DarkSurfaceVar,
    outline            = Outline,
    outlineVariant     = OutlineVar,
    error              = Color(0xFFCF6679),
    onError            = Color(0xFF000000),
)

private fun buildLightScheme(accent: AccentPalette) = lightColorScheme(
    primary            = accent.lightMain,
    onPrimary          = Color.White,
    primaryContainer   = accent.main.copy(alpha = 0.12f),
    onPrimaryContainer = accent.lightMain,
    secondary          = accent.lightMain.copy(alpha = 0.7f),
    background         = LightBg,
    onBackground       = OnLight,
    surface            = LightSurface,
    onSurface          = OnLight,
    surfaceVariant     = LightSurfaceVar,
    onSurfaceVariant   = OnLightVar,
)

@Composable
fun DockTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val accentIndex by com.waigi.dock.util.PreferenceUtil.accentColorFlow.collectAsState()
    val bgStyle     by com.waigi.dock.util.PreferenceUtil.backgroundStyleFlow.collectAsState()

    val accent = accentPalettes.getOrElse(accentIndex) { accentPalettes[ACCENT_TANGERINE] }

    val colorScheme = when {
        bgStyle == BG_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark || bgStyle == BG_AMOLED || bgStyle == BG_DARK -> {
            buildDarkScheme(accent, amoled = bgStyle == BG_AMOLED)
        }
        else -> buildLightScheme(accent)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = DockTypography,
        content     = content,
    )
}
