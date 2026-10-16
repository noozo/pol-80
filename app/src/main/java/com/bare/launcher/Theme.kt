package com.bare.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Style D Miami dusk tokens — centralized palette for Bare chrome.
 * Refs: style-board/keepers/ (D variants), style-D-miami-walkman.png
 */
object BareColors {
    /** Near-black indigo void (wallpaper stays user-chosen; chrome only). */
    val Bg = Color(0xFF050510)
    val BgAlt = Color(0xFF0A0614)

    /** Sheet surface — still fairly opaque for drawers/pickers. */
    val Surface = Color(0xE612081F)
    /** Header/dock glass fallback (~35%); palm art uses its own light scrim. */
    val Glass = Color(0x5912081F)
    val GlassSoft = Color(0x9912081F)

    val NeonMagenta = Color(0xFFFF2D95)
    val NeonMagentaSoft = Color(0xFFE040A0)
    val NeonCyan = Color(0xFF00E5FF)
    val NeonCyanSoft = Color(0xFF20C0E0)
    val DuskCoral = Color(0xFFF06050)
    val DuskCoralBright = Color(0xFFFF7A4D)
    val VioletMid = Color(0xFF4A1880)
    val VioletDeep = Color(0xFF301070)

    /** Primary UI text — bright (user wants no gray labels). */
    val TextPrimary = Color(0xFFF5F0FF)
    /** Muted — use sparingly. */
    val TextMuted = Color(0xFF9A90B8)

    /** SYS phosphor — nudged toward Miami cyan; still readable as "tech". */
    val TechGreen = Color(0xFF7CFFB2)
    val SysAccent = NeonCyan

    val StatusAmber = Color(0xFFFFC107)
    val StatusRed = Color(0xFFFF5252)
    val BadgeRed = Color(0xCCFF5252)

    val Placeholder = Color(0x33F5F0FF)
    val ScrimTap = Color(0x66050510)

    /** Soft dusk header wash: indigo → magenta haze. */
    val HeaderGradient: Brush
        get() = Brush.verticalGradient(
            colors = listOf(
                VioletDeep.copy(alpha = 0.55f),
                VioletMid.copy(alpha = 0.35f),
                NeonMagenta.copy(alpha = 0.18f),
                Color.Transparent,
            ),
        )

    val HeaderRim: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                NeonCyan.copy(alpha = 0.70f),
                VioletMid.copy(alpha = 0.35f),
                NeonMagenta.copy(alpha = 0.65f),
            ),
        )

    val DockUnderline: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(
                NeonCyan.copy(alpha = 0.15f),
                DuskCoral.copy(alpha = 0.85f),
                NeonMagenta.copy(alpha = 0.80f),
                NeonCyan.copy(alpha = 0.15f),
            ),
        )
}

@Composable
fun BareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Transparent,
            surface = BareColors.Surface,
            primary = BareColors.NeonCyan,
            secondary = BareColors.NeonMagenta,
            tertiary = BareColors.DuskCoral,
            onBackground = BareColors.TextPrimary,
            onSurface = BareColors.TextPrimary,
            onPrimary = BareColors.Bg,
            onSecondary = BareColors.TextPrimary,
        ),
        content = content,
    )
}
