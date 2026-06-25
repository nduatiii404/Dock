package com.waigi.dock.ui.theme

import androidx.compose.ui.graphics.Color

// ── Neutrals (AMOLED-first) ───────────────────────────────────────────────────
val Black          = Color(0xFF000000)
val Surface800     = Color(0xFF0A0A0A)
val Surface700     = Color(0xFF111111)
val Surface600     = Color(0xFF1A1A1A)
val Surface500     = Color(0xFF222222)
val Surface400     = Color(0xFF2C2C2C)
val OnSurface      = Color(0xFFE8E8E8)
val OnSurfaceVar   = Color(0xFF9E9E9E)
val Outline        = Color(0xFF333333)
val OutlineVar     = Color(0xFF222222)

// ── Material Dark fallback (non-AMOLED) ───────────────────────────────────────
val DarkBg         = Color(0xFF121212)
val DarkSurface    = Color(0xFF1E1E1E)
val DarkSurfaceVar = Color(0xFF2A2A2A)

// ── Light scheme ──────────────────────────────────────────────────────────────
val LightBg        = Color(0xFFFAFAFA)
val LightSurface   = Color(0xFFFFFFFF)
val LightSurfaceVar= Color(0xFFF0F0F0)
val OnLight        = Color(0xFF1A1A1A)
val OnLightVar     = Color(0xFF666666)

// ── Accent palettes ───────────────────────────────────────────────────────────

// Tangerine (default)
val TangerineMain      = Color(0xFFFF6D00)
val TangerineOnMain    = Color(0xFFFFFFFF)
val TangerineContainer = Color(0xFF3D1C00)
val TangerineLight     = Color(0xFFFF6D00)

// Violet
val VioletMain         = Color(0xFF7C4DFF)
val VioletOnMain       = Color(0xFFFFFFFF)
val VioletContainer    = Color(0xFF1C0070)
val VioletLight        = Color(0xFF7C4DFF)

// Teal
val TealMain           = Color(0xFF00BCD4)
val TealOnMain         = Color(0xFF000000)
val TealContainer      = Color(0xFF003038)
val TealLight          = Color(0xFF006878)

// Rose
val RoseMain           = Color(0xFFE91E63)
val RoseOnMain         = Color(0xFFFFFFFF)
val RoseContainer      = Color(0xFF3D001E)
val RoseLight          = Color(0xFFE91E63)

// Emerald
val EmeraldMain      = Color(0xFF00E676)
val EmeraldOnMain    = Color(0xFF000000)
val EmeraldContainer = Color(0xFF003816)
val EmeraldLight     = Color(0xFF00C853)

// Cobalt
val CobaltMain      = Color(0xFF2979FF)
val CobaltOnMain    = Color(0xFFFFFFFF)
val CobaltContainer = Color(0xFF002266)
val CobaltLight     = Color(0xFF2979FF)

// Amber
val AmberMain      = Color(0xFFFFB300)
val AmberOnMain    = Color(0xFF000000)
val AmberContainer = Color(0xFF3E2723)
val AmberLight     = Color(0xFFFFB300)

// Coral
val CoralMain      = Color(0xFFFF5722)
val CoralOnMain    = Color(0xFFFFFFFF)
val CoralContainer = Color(0xFF4E1D00)
val CoralLight     = Color(0xFFFF5722)

// Mint
val MintMain      = Color(0xFF00BFA5)
val MintOnMain    = Color(0xFF000000)
val MintContainer = Color(0xFF00382D)
val MintLight     = Color(0xFF00BFA5)

// ── Accent index mapping ──────────────────────────────────────────────────────
const val ACCENT_TANGERINE = 0
const val ACCENT_VIOLET    = 1
const val ACCENT_TEAL      = 2
const val ACCENT_ROSE      = 3
const val ACCENT_EMERALD   = 4
const val ACCENT_COBALT    = 5
const val ACCENT_AMBER     = 6
const val ACCENT_CORAL     = 7
const val ACCENT_MINT      = 8

data class AccentPalette(
    val name: String,
    val main: Color,
    val onMain: Color,
    val container: Color,
    val lightMain: Color,
)

val accentPalettes = listOf(
    AccentPalette("Tangerine", TangerineMain, TangerineOnMain, TangerineContainer, TangerineLight),
    AccentPalette("Violet",    VioletMain,    VioletOnMain,    VioletContainer,    VioletLight),
    AccentPalette("Teal",      TealMain,      TealOnMain,      TealContainer,      TealLight),
    AccentPalette("Rose",      RoseMain,      RoseOnMain,      RoseContainer,      RoseLight),
    AccentPalette("Emerald",   EmeraldMain,   EmeraldOnMain,   EmeraldContainer,   EmeraldLight),
    AccentPalette("Cobalt",    CobaltMain,    CobaltOnMain,    CobaltContainer,    CobaltLight),
    AccentPalette("Amber",     AmberMain,     AmberOnMain,     AmberContainer,     AmberLight),
    AccentPalette("Coral",     CoralMain,     CoralOnMain,     CoralContainer,     CoralLight),
    AccentPalette("Mint",      MintMain,      MintOnMain,      MintContainer,      MintLight),
)
