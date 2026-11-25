// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.transit

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.SignalColors
import app.nijiyome.designsystem.direction.StatusColors
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_transit_palette_anden
import app.nijiyome.designsystem.resources.rd_transit_palette_grafito
import app.nijiyome.designsystem.resources.rd_transit_palette_ultima
import app.nijiyome.designsystem.resources.rd_transit_palette_yamanote
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Tránsito nocturno palettes, from docs/redesign/artefacto/dir-transit.css (`--nj-*` and `--tr-*` per palette and mode).
// The Material roles are derived by [transitScheme]; the same math lives in the lane's contrast script (every text pair >= 4.5:1,
// outline and status marks >= 3:1, for the four palettes x light / dark / OLED x three contrast levels).

/** The artifact tokens of one palette in one mode. Board fields are the dark "departure board" island (unused in light). */
@Immutable
data class TransitTokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val focus: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    val info: Color,
    val stWatching: Color,
    val stPlanning: Color,
    val stCompleted: Color,
    val stPaused: Color,
    val stDropped: Color,
    val stRewatching: Color,
    val tertiary: Color,
    val onTertiary: Color,
    /** Link lines: the computer (a) and the phone (b). */
    val trA: Color,
    val trB: Color,
    val boardBg: Color,
    val boardS: Color,
    val boardLine: Color,
    val boardText: Color,
    val boardText2: Color,
    val boardHi: Color,
)

/** What OLED replaces of the dark tokens: pure black floor and darker surfaces. */
@Immutable
data class OledSurfaces(val bg: Color, val surface: Color, val surface2: Color, val surface3: Color, val line: Color)

/** Station plate colors, fixed in every mode (artifact `--tr-porcelain`, `--tr-ink`). */
object TransitInk {
    val porcelain = Color(0xFFF8F9FB)
    val ink = Color(0xFF0B1430)
}

internal object TransitTokenData {
    val grafitoDark = TransitTokens(
        bg = Color(0xFF101113),
        surface = Color(0xFF16181B),
        surface2 = Color(0xFF1C1F23),
        surface3 = Color(0xFF252930),
        line = Color(0xFF343941),
        lineStrong = Color(0xFF7F8793),
        text = Color(0xFFF3F4F6),
        text2 = Color(0xFFC9CDD4),
        text3 = Color(0xFFA7ADB7),
        accent = Color(0xFF4F9DFF),
        onAccent = Color(0xFF031A33),
        accentSoft = Color(0xFF14304F),
        onAccentSoft = Color(0xFFD6E8FF),
        focus = Color(0xFFFFC24A),
        ok = Color(0xFF4FD08A),
        warn = Color(0xFFFFC24A),
        err = Color(0xFFFF8A7E),
        info = Color(0xFF5AA9FF),
        stWatching = Color(0xFF5AA9FF),
        stPlanning = Color(0xFFFFB547),
        stCompleted = Color(0xFF4FD08A),
        stPaused = Color(0xFFB39CFF),
        stDropped = Color(0xFFFF7466),
        stRewatching = Color(0xFF3FD6CF),
        tertiary = Color(0xFFFFC24A),
        onTertiary = Color(0xFF2A1700),
        trA = Color(0xFF5AA9FF),
        trB = Color(0xFFFF7466),
        boardBg = Color(0xFF0B1A4A),
        boardS = Color(0xFF13245C),
        boardLine = Color(0xFF2A3D7A),
        boardText = Color(0xFFF3F5FB),
        boardText2 = Color(0xFFC6D0EE),
        boardHi = Color(0xFFFFC24A),
    )

    val grafitoLight = TransitTokens(
        bg = Color(0xFFF4F5F6),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFECEEF0),
        surface3 = Color(0xFFE1E4E8),
        line = Color(0xFFC8CCD2),
        lineStrong = Color(0xFF5D636D),
        text = Color(0xFF111316),
        text2 = Color(0xFF383D45),
        text3 = Color(0xFF535A64),
        accent = Color(0xFF0A58B0),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFD9E7FA),
        onAccentSoft = Color(0xFF062F5E),
        focus = Color(0xFF111316),
        ok = Color(0xFF13703F),
        warn = Color(0xFF8A5A00),
        err = Color(0xFFB8261B),
        info = Color(0xFF0A5FB4),
        stWatching = Color(0xFF0A5FB4),
        stPlanning = Color(0xFF8A5A00),
        stCompleted = Color(0xFF13703F),
        stPaused = Color(0xFF5E45B0),
        stDropped = Color(0xFFB8261B),
        stRewatching = Color(0xFF00706B),
        tertiary = Color(0xFF8A5A00),
        onTertiary = Color(0xFFFFFFFF),
        trA = Color(0xFF0A58B0),
        trB = Color(0xFFBF231A),
        boardBg = Color(0xFF000000),
        boardS = Color(0xFF000000),
        boardLine = Color(0xFF000000),
        boardText = Color(0xFF000000),
        boardText2 = Color(0xFF000000),
        boardHi = Color(0xFF000000),
    )

    val grafitoOled = OledSurfaces(bg = Color(0xFF000000), surface = Color(0xFF0A0B0C), surface2 = Color(0xFF111315), surface3 = Color(0xFF1A1C20), line = Color(0xFF2B2F35))

    val andenDark = TransitTokens(
        bg = Color(0xFF121519),
        surface = Color(0xFF181C22),
        surface2 = Color(0xFF1E232B),
        surface3 = Color(0xFF262C36),
        line = Color(0xFF343C49),
        lineStrong = Color(0xFF7C8698),
        text = Color(0xFFF2F4F7),
        text2 = Color(0xFFC7CDD8),
        text3 = Color(0xFFA4ACBA),
        accent = Color(0xFFFF7466),
        onAccent = Color(0xFF2A0705),
        accentSoft = Color(0xFF3D2028),
        onAccentSoft = Color(0xFFFFDCD6),
        focus = Color(0xFFFFC24A),
        ok = Color(0xFF4FD08A),
        warn = Color(0xFFFFC24A),
        err = Color(0xFFFF8A7E),
        info = Color(0xFF62AAFF),
        stWatching = Color(0xFF62AAFF),
        stPlanning = Color(0xFFFFB547),
        stCompleted = Color(0xFF4FD08A),
        stPaused = Color(0xFFB39CFF),
        stDropped = Color(0xFFFF7466),
        stRewatching = Color(0xFF3FD6CF),
        tertiary = Color(0xFFFFC24A),
        onTertiary = Color(0xFF2A1700),
        trA = Color(0xFF62AAFF),
        trB = Color(0xFFFF7466),
        boardBg = Color(0xFF0D1014),
        boardS = Color(0xFF161A20),
        boardLine = Color(0xFF2C333E),
        boardText = Color(0xFFF2F4F7),
        boardText2 = Color(0xFFC7CDD8),
        boardHi = Color(0xFFFFC24A),
    )

    val andenLight = TransitTokens(
        bg = Color(0xFFF5F6F8),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFECEEF2),
        surface3 = Color(0xFFE1E5EC),
        line = Color(0xFFC9CED8),
        lineStrong = Color(0xFF5C6473),
        text = Color(0xFF11151D),
        text2 = Color(0xFF373E4C),
        text3 = Color(0xFF525A69),
        accent = Color(0xFFBF231A),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFFDE0DC),
        onAccentSoft = Color(0xFF5C0B05),
        focus = Color(0xFF11151D),
        ok = Color(0xFF13703F),
        warn = Color(0xFF8A5A00),
        err = Color(0xFFB8261B),
        info = Color(0xFF0A58B0),
        stWatching = Color(0xFF0A58B0),
        stPlanning = Color(0xFF8A5A00),
        stCompleted = Color(0xFF13703F),
        stPaused = Color(0xFF5E45B0),
        stDropped = Color(0xFFB8261B),
        stRewatching = Color(0xFF00706B),
        tertiary = Color(0xFF8A5A00),
        onTertiary = Color(0xFFFFFFFF),
        trA = Color(0xFF0A58B0),
        trB = Color(0xFFB8261B),
        boardBg = Color(0xFF000000),
        boardS = Color(0xFF000000),
        boardLine = Color(0xFF000000),
        boardText = Color(0xFF000000),
        boardText2 = Color(0xFF000000),
        boardHi = Color(0xFF000000),
    )

    val andenOled = OledSurfaces(bg = Color(0xFF000000), surface = Color(0xFF08090B), surface2 = Color(0xFF0F1114), surface3 = Color(0xFF171A1F), line = Color(0xFF2A2F38))

    val yamanoteDark = TransitTokens(
        bg = Color(0xFF0A1A1E),
        surface = Color(0xFF0F2227),
        surface2 = Color(0xFF142B31),
        surface3 = Color(0xFF1B353C),
        line = Color(0xFF2A4952),
        lineStrong = Color(0xFF6F949C),
        text = Color(0xFFEEF6F2),
        text2 = Color(0xFFC4D8D0),
        text3 = Color(0xFFA1BAB2),
        accent = Color(0xFFA8D84C),
        onAccent = Color(0xFF152204),
        accentSoft = Color(0xFF22391B),
        onAccentSoft = Color(0xFFE3F6C6),
        focus = Color(0xFFFFC24A),
        ok = Color(0xFFA8D84C),
        warn = Color(0xFFFFC24A),
        err = Color(0xFFFF8A7E),
        info = Color(0xFF5AA9FF),
        stWatching = Color(0xFF5AA9FF),
        stPlanning = Color(0xFFFFB547),
        stCompleted = Color(0xFFA8D84C),
        stPaused = Color(0xFFB39CFF),
        stDropped = Color(0xFFFF7466),
        stRewatching = Color(0xFF3FD6CF),
        tertiary = Color(0xFFFFC24A),
        onTertiary = Color(0xFF2A1700),
        trA = Color(0xFF5AA9FF),
        trB = Color(0xFFA8D84C),
        boardBg = Color(0xFF071417),
        boardS = Color(0xFF0F2227),
        boardLine = Color(0xFF24414A),
        boardText = Color(0xFFEEF6F2),
        boardText2 = Color(0xFFC4D8D0),
        boardHi = Color(0xFFA8D84C),
    )

    val yamanoteLight = TransitTokens(
        bg = Color(0xFFF2F7F1),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFE7EFE5),
        surface3 = Color(0xFFDBE6D9),
        line = Color(0xFFC2D0BF),
        lineStrong = Color(0xFF56685A),
        text = Color(0xFF0D1A12),
        text2 = Color(0xFF2E4134),
        text3 = Color(0xFF4A5C4F),
        accent = Color(0xFF376C0E),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFE0EFCC),
        onAccentSoft = Color(0xFF1D3B05),
        focus = Color(0xFF0D1A12),
        ok = Color(0xFF376C0E),
        warn = Color(0xFF8A5A00),
        err = Color(0xFFB8261B),
        info = Color(0xFF0A58B0),
        stWatching = Color(0xFF0A58B0),
        stPlanning = Color(0xFF8A5A00),
        stCompleted = Color(0xFF376C0E),
        stPaused = Color(0xFF5E45B0),
        stDropped = Color(0xFFB8261B),
        stRewatching = Color(0xFF00706B),
        tertiary = Color(0xFF8A5A00),
        onTertiary = Color(0xFFFFFFFF),
        trA = Color(0xFF0A58B0),
        trB = Color(0xFF376C0E),
        boardBg = Color(0xFF000000),
        boardS = Color(0xFF000000),
        boardLine = Color(0xFF000000),
        boardText = Color(0xFF000000),
        boardText2 = Color(0xFF000000),
        boardHi = Color(0xFF000000),
    )

    val yamanoteOled = OledSurfaces(bg = Color(0xFF000000), surface = Color(0xFF050D0E), surface2 = Color(0xFF0A1517), surface3 = Color(0xFF111E21), line = Color(0xFF1F363C))

    val ultimaDark = TransitTokens(
        bg = Color(0xFF0F0E1F),
        surface = Color(0xFF16152A),
        surface2 = Color(0xFF1C1B33),
        surface3 = Color(0xFF25233F),
        line = Color(0xFF36334F),
        lineStrong = Color(0xFF817C9F),
        text = Color(0xFFF5F3F0),
        text2 = Color(0xFFD2CDD9),
        text3 = Color(0xFFAEA8BC),
        accent = Color(0xFFFFB547),
        onAccent = Color(0xFF2A1700),
        accentSoft = Color(0xFF3A2A1F),
        onAccentSoft = Color(0xFFFFE4BA),
        focus = Color(0xFFF5F3F0),
        ok = Color(0xFF4FD08A),
        warn = Color(0xFFFFB547),
        err = Color(0xFFFF8A7E),
        info = Color(0xFF5AA9FF),
        stWatching = Color(0xFF5AA9FF),
        stPlanning = Color(0xFFFFB547),
        stCompleted = Color(0xFF4FD08A),
        stPaused = Color(0xFFB39CFF),
        stDropped = Color(0xFFFF7466),
        stRewatching = Color(0xFF3FD6CF),
        tertiary = Color(0xFF5AA9FF),
        onTertiary = Color(0xFF031A33),
        trA = Color(0xFF5AA9FF),
        trB = Color(0xFFFFB547),
        boardBg = Color(0xFF0A0916),
        boardS = Color(0xFF15142A),
        boardLine = Color(0xFF2F2C47),
        boardText = Color(0xFFF5F3F0),
        boardText2 = Color(0xFFD2CDD9),
        boardHi = Color(0xFFFFB547),
    )

    val ultimaLight = TransitTokens(
        bg = Color(0xFFF5F4F8),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFEEECF3),
        surface3 = Color(0xFFE3E0EA),
        line = Color(0xFFCCC8D6),
        lineStrong = Color(0xFF625D73),
        text = Color(0xFF16142A),
        text2 = Color(0xFF3C3851),
        text3 = Color(0xFF56516A),
        accent = Color(0xFF955300),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFFDE8C6),
        onAccentSoft = Color(0xFF4A2900),
        focus = Color(0xFF16142A),
        ok = Color(0xFF13703F),
        warn = Color(0xFF955300),
        err = Color(0xFFB8261B),
        info = Color(0xFF0A58B0),
        stWatching = Color(0xFF0A58B0),
        stPlanning = Color(0xFF955300),
        stCompleted = Color(0xFF13703F),
        stPaused = Color(0xFF5E45B0),
        stDropped = Color(0xFFB8261B),
        stRewatching = Color(0xFF00706B),
        tertiary = Color(0xFF0A58B0),
        onTertiary = Color(0xFFFFFFFF),
        trA = Color(0xFF0A58B0),
        trB = Color(0xFF955300),
        boardBg = Color(0xFF000000),
        boardS = Color(0xFF000000),
        boardLine = Color(0xFF000000),
        boardText = Color(0xFF000000),
        boardText2 = Color(0xFF000000),
        boardHi = Color(0xFF000000),
    )

    val ultimaOled = OledSurfaces(bg = Color(0xFF000000), surface = Color(0xFF07070E), surface2 = Color(0xFF0E0D18), surface3 = Color(0xFF161523), line = Color(0xFF2A2840))
}

/** Channel lerp in sRGB (not Oklab like `lerp(Color, Color)`), so the contrast script can reproduce every derived color. */
internal fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

private fun ContrastLevel.step(): Int = when (this) {
    ContrastLevel.Standard -> 0
    ContrastLevel.Medium -> 1
    ContrastLevel.High -> 2
}

/** Builds every Material role of one palette in one tone and contrast level from the artifact tokens. */
internal fun transitScheme(light: TransitTokens, dark: TransitTokens, t: TransitTokens, tone: ThemeTone, contrast: ContrastLevel): ColorScheme {
    val isDark = tone != ThemeTone.LIGHT
    val c = contrast.step()
    // Foreground colors move away from the surface to raise contrast: toward white on dark, toward black on light.
    val away = if (isDark) White else Black
    val fg = floatArrayOf(0f, 0.35f, 0.7f)[c]
    val ol = floatArrayOf(0f, 0.25f, 0.5f)[c]
    val acc = if (isDark) floatArrayOf(0f, 0.2f, 0.4f)[c] else floatArrayOf(0f, 0.18f, 0.36f)[c]
    val onc = floatArrayOf(0f, 0.4f, 0.8f)[c]
    val primary = mix(t.accent, away, acc)
    val onPrimaryContainer = mix(t.onAccentSoft, away, onc)
    val tertiaryContainer: Color
    val onTertiaryContainer: Color
    val errorContainer: Color
    val onErrorContainer: Color
    val onError: Color
    if (isDark) {
        tertiaryContainer = mix(t.surface, t.tertiary, 0.22f)
        onTertiaryContainer = mix(mix(t.tertiary, White, 0.6f), White, onc)
        errorContainer = mix(t.surface, t.err, 0.22f)
        onErrorContainer = mix(mix(t.err, White, 0.6f), White, onc)
        onError = Color(0xFF3A0A05)
    } else {
        tertiaryContainer = mix(White, t.tertiary, 0.16f)
        onTertiaryContainer = mix(mix(t.tertiary, Black, 0.45f), Black, onc)
        errorContainer = mix(White, t.err, 0.14f)
        onErrorContainer = mix(mix(t.err, Black, 0.5f), Black, onc)
        onError = White
    }
    val inverseSurface = if (isDark) light.surface2 else dark.surface2
    val inverseOnSurface = if (isDark) light.text else dark.text
    val surfaceDim = if (isDark) t.bg else t.surface3
    val surfaceBright = if (isDark) t.surface3 else t.surface
    val lowest = when (tone) {
        ThemeTone.OLED -> t.bg
        ThemeTone.DARK -> mix(t.bg, Black, 0.35f)
        ThemeTone.LIGHT -> t.surface
    }
    val low = if (isDark) mix(t.bg, t.surface, 0.5f) else mix(t.surface, t.bg, 0.4f)
    val container = if (isDark) t.surface else t.surface2
    val high = if (isDark) t.surface2 else mix(t.surface2, t.surface3, 0.5f)
    return ColorScheme(
        primary = primary,
        onPrimary = t.onAccent,
        primaryContainer = t.accentSoft,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = if (isDark) light.accent else dark.accent,
        secondary = mix(t.text2, t.text, fg),
        onSecondary = if (isDark) t.bg else White,
        secondaryContainer = t.accentSoft,
        onSecondaryContainer = onPrimaryContainer,
        tertiary = mix(t.tertiary, away, acc),
        onTertiary = t.onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = t.bg,
        onBackground = t.text,
        surface = t.bg,
        onSurface = t.text,
        surfaceVariant = t.surface2,
        onSurfaceVariant = mix(t.text2, t.text, fg),
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = mix(t.err, away, acc),
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = mix(t.lineStrong, t.text, ol),
        outlineVariant = mix(t.line, t.lineStrong, floatArrayOf(0f, 0.35f, 0.7f)[c]),
        scrim = Black,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = container,
        surfaceContainerHigh = high,
        surfaceContainerHighest = t.surface3,
        surfaceContainerLow = low,
        surfaceContainerLowest = lowest,
        primaryFixed = light.accentSoft,
        primaryFixedDim = dark.accent,
        onPrimaryFixed = light.onAccentSoft,
        onPrimaryFixedVariant = light.accent,
        secondaryFixed = light.surface3,
        secondaryFixedDim = dark.text2,
        onSecondaryFixed = light.text,
        onSecondaryFixedVariant = light.text2,
        tertiaryFixed = mix(White, light.tertiary, 0.16f),
        tertiaryFixedDim = dark.tertiary,
        onTertiaryFixed = mix(light.tertiary, Black, 0.45f),
        onTertiaryFixedVariant = light.tertiary,
    )
}

/** One palette of Tránsito nocturno. Schemes are built once per tone and contrast level and reused. */
class TransitPalette internal constructor(
    override val id: String,
    override val swatch: Color,
    private val label: StringResource,
    private val light: TransitTokens,
    private val dark: TransitTokens,
    private val oled: OledSurfaces,
) : DirectionPalette {
    private val oledTokens: TransitTokens = dark.copy(
        bg = oled.bg,
        surface = oled.surface,
        surface2 = oled.surface2,
        surface3 = oled.surface3,
        line = oled.line,
        boardBg = Color(0xFF000000),
        boardS = Color(0xFF0A0B12),
    )

    private val schemes: Map<Pair<ThemeTone, ContrastLevel>, ColorScheme> by lazy {
        buildMap {
            for (tone in ThemeTone.entries) for (level in ContrastLevel.entries) {
                put(tone to level, transitScheme(light, dark, tokens(tone), tone, level))
            }
        }
    }

    @Composable
    override fun name(): String = stringResource(label)

    /** The artifact tokens of [tone] (OLED = dark with black floor). */
    fun tokens(tone: ThemeTone): TransitTokens = when (tone) {
        ThemeTone.LIGHT -> light
        ThemeTone.DARK -> dark
        ThemeTone.OLED -> oledTokens
    }

    override fun scheme(tone: ThemeTone, contrast: ContrastLevel): ColorScheme = schemes.getValue(tone to contrast)

    override fun status(tone: ThemeTone): StatusColors = tokens(tone).let {
        StatusColors(
            watching = it.stWatching,
            planning = it.stPlanning,
            completed = it.stCompleted,
            paused = it.stPaused,
            dropped = it.stDropped,
            rewatching = it.stRewatching,
        )
    }

    override fun signal(tone: ThemeTone): SignalColors = tokens(tone).let { SignalColors(ok = it.ok, warn = it.warn, err = it.err, info = it.info) }

    /** True when [scheme] was built from this palette (the container role is unique per palette and tone). */
    internal fun owns(scheme: ColorScheme, tone: ThemeTone): Boolean = tokens(tone).accentSoft == scheme.primaryContainer
}

object TransitPalettes {
    val grafito = TransitPalette(
        "grafito", Color(0xFF4F9DFF), Res.string.rd_transit_palette_grafito,
        TransitTokenData.grafitoLight, TransitTokenData.grafitoDark, TransitTokenData.grafitoOled,
    )
    val anden = TransitPalette(
        "anden", Color(0xFFBF231A), Res.string.rd_transit_palette_anden,
        TransitTokenData.andenLight, TransitTokenData.andenDark, TransitTokenData.andenOled,
    )
    val yamanote = TransitPalette(
        "yamanote", Color(0xFF9ACD32), Res.string.rd_transit_palette_yamanote,
        TransitTokenData.yamanoteLight, TransitTokenData.yamanoteDark, TransitTokenData.yamanoteOled,
    )
    val ultima = TransitPalette(
        "ultima", Color(0xFFFFB547), Res.string.rd_transit_palette_ultima,
        TransitTokenData.ultimaLight, TransitTokenData.ultimaDark, TransitTokenData.ultimaOled,
    )

    val all: List<TransitPalette> = listOf(grafito, anden, yamanote, ultima)

    /**
     * The tokens behind the active Material scheme, for the decoration (departure board, Link lines). Grafito when the scheme is not
     * one of ours (Android dynamic color).
     */
    fun tokensOf(scheme: ColorScheme, tone: ThemeTone): TransitTokens =
        (all.firstOrNull { it.owns(scheme, tone) } ?: grafito).tokens(tone)
}
