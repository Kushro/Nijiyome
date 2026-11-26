@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.CanonDirection
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack
import app.nijiyome.designsystem.direction.LocalAccentFont
import app.nijiyome.designsystem.direction.LocalCoverAccent
import app.nijiyome.designsystem.direction.LocalDirection
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.LocalStatusColors
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.generated.NijiyomeTokens
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

/**
 * The light/dark mode the app resolved (Settings › Apariencia › Modo, or the system when it is "Sistema"), so nested
 * themes such as [NijiyomeSeedTheme] follow the app instead of `isSystemInDarkTheme()`.
 */
// Outside NijiyomeTheme (a bare preview) both default to light / not AMOLED.
val LocalNijiyomeDarkTheme = staticCompositionLocalOf { false }

/** Whether the app is on Negro AMOLED (only meaningful while [LocalNijiyomeDarkTheme] is true). */
val LocalNijiyomeAmoled = staticCompositionLocalOf { false }

/** Active contrast preference, forwarded to [NijiyomeSeedTheme] so Details never silently drops high contrast. */
val LocalNijiyomeContrast = staticCompositionLocalOf { ContrastLevel.Standard }

/**
 * App theme = `MaterialExpressiveTheme` + the active [direction] (Sparkles redesign: palettes, type, shapes, status colors and
 * decoration) + optional system dynamic color (Android 12+) + `MotionScheme.expressive()` (01 §2, §5).
 *
 * Canon reads [paletteId] (the 20 generated token palettes); every other direction reads [directionPalette], an id of its own
 * `DirectionSpec.palettes` (null = its default). Dynamic color, when on, replaces the scheme of any direction.
 */
@Composable
fun NijiyomeTheme(
    paletteId: String = NijiyomeTokens.DEFAULT_PALETTE,
    amoled: Boolean = false,
    contrast: ContrastLevel = ContrastLevel.Standard,
    dynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    direction: DirectionSpec = CanonDirection,
    directionPalette: String? = null,
    content: @Composable () -> Unit,
) {
    val tone = when {
        !darkTheme -> ThemeTone.LIGHT
        amoled -> ThemeTone.OLED
        else -> ThemeTone.DARK
    }
    val palette = remember(direction, paletteId, directionPalette) {
        direction.palette(if (direction.track == DirectionTrack.CANON) paletteId else directionPalette)
    }
    val systemScheme = if (dynamicColor) systemDynamicColorScheme(darkTheme) else null
    val colorScheme = remember(palette, tone, contrast, systemScheme) {
        if (systemScheme != null) {
            if (tone == ThemeTone.OLED) systemScheme.toAmoled(contrast) else systemScheme
        } else {
            palette.scheme(tone, contrast)
        }
    }
    val baseTypography = remember { NijiyomeTokens.typography(NijiyomeFonts.families) }
    val typography = direction.typography(baseTypography)
    CompositionLocalProvider(
        LocalNijiyomeDarkTheme provides darkTheme,
        LocalNijiyomeAmoled provides (darkTheme && amoled),
        LocalNijiyomeContrast provides contrast,
        LocalDirection provides direction,
        LocalThemeTone provides tone,
        LocalStatusColors provides palette.status(tone),
        LocalSignalColors provides palette.signal(tone),
        LocalAccentFont provides direction.accentFont(),
        LocalControlShape provides controlShapeOf(direction),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = direction.shapes,
            typography = typography,
            content = content,
        )
    }
}

/**
 * Per-content accent (e.g. the poster colour in Details), derived at runtime with materialKolor (01 §2).
 * Uses [PaletteStyle.TonalSpot] (not Expressive, which rotated hues into muddy olive), honours [LocalNijiyomeContrast]
 * and [LocalReduceMotion], and keeps AMOLED surfaces pure black with crisp hairlines so Details harmonises with the shell.
 */
@Composable
fun NijiyomeSeedTheme(
    seed: Color?,
    dark: Boolean = LocalNijiyomeDarkTheme.current,
    amoled: Boolean = LocalNijiyomeAmoled.current,
    contrast: ContrastLevel = LocalNijiyomeContrast.current,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    if (seed == null) {
        content()
    } else if (LocalDirection.current.track != DirectionTrack.CANON) {
        // Sparkles: only Canon re-themes the page by its cover; the other directions keep their palette and read the cover color.
        CompositionLocalProvider(LocalCoverAccent provides seed, content = content)
    } else {
        val contrastDouble = when (contrast) {
            ContrastLevel.Standard -> 0.0
            ContrastLevel.Medium -> 0.5
            ContrastLevel.High -> 1.0
        }
        DynamicMaterialExpressiveTheme(
            seedColor = seed,
            motionScheme = MotionScheme.expressive(),
            isDark = dark,
            isAmoled = dark && amoled,
            style = PaletteStyle.TonalSpot,
            contrastLevel = contrastDouble,
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            animate = !reduceMotion,
        ) {
            if (dark && amoled) {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme.toAmoled(contrast),
                    shapes = MaterialTheme.shapes,
                    typography = MaterialTheme.typography,
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}

/** The system's dynamic colour scheme (Android 12+); null where the platform has none. */
@Composable
internal expect fun systemDynamicColorScheme(dark: Boolean): ColorScheme?

/** Parses `#RRGGBB` / `#AARRGGBB` (as delivered by AniList cover colours); null when malformed. */
fun parseHexColor(hex: String?): Color? {
    val s = hex?.trim()?.removePrefix("#") ?: return null
    val v = s.toLongOrNull(16) ?: return null
    return when (s.length) {
        6 -> Color(0xFF000000L or v)
        8 -> Color(v)
        else -> null
    }
}

/**
 * True-Black OLED palette: pure `#000000` for background, surface, rail and base cards, paired with crisp neutral hairlines
 * (`white/14%`, scaled up for Medium/High contrast) and neutral dark-gray elevated containers (`#141418`, `#1E1E24`) for
 * sheets, chips and interactive states. Eliminates muddy purple/olive patches on both dynamic and curated palettes.
 */
fun ColorScheme.toAmoled(contrast: ContrastLevel = ContrastLevel.Standard): ColorScheme {
    val hairlineAlpha = when (contrast) {
        ContrastLevel.Standard -> 0.14f
        ContrastLevel.Medium -> 0.24f
        ContrastLevel.High -> 0.38f
    }
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF141418),
        surfaceContainerHighest = Color(0xFF1E1E24),
        surfaceVariant = Color(0xFF1A1A20),
        surfaceBright = Color(0xFF26262E),
        outlineVariant = Color.White.copy(alpha = hairlineAlpha),
    )
}
