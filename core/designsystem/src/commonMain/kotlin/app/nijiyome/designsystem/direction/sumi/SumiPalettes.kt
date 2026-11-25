// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.sumi

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.SignalColors
import app.nijiyome.designsystem.direction.StatusColors
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_sumi_palette_luna
import app.nijiyome.designsystem.resources.rd_sumi_palette_matcha
import app.nijiyome.designsystem.resources.rd_sumi_palette_mono
import app.nijiyome.designsystem.resources.rd_sumi_palette_oceano
import app.nijiyome.designsystem.resources.rd_sumi_palette_sakura
import app.nijiyome.designsystem.resources.rd_sumi_palette_violeta
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Sumi y hanko palettes (docs/redesign/artefacto/dir-sumi.css). Three neutral inks (paper, sumi, OLED night) shared by every
// palette, one accent pair per palette, shu vermilion as tertiary (the seal red of the mark) and the hanko status inks.
// Contrast was checked with the WCAG 2.x formula for every palette, tone and contrast level: text roles >= 4.5:1 on every
// surface container, outline and status inks >= 3:1, seal kanji >= 4.5:1 on their seal.

/** The neutral ink of one tone: background, the surface ladder, lines and text. */
private class SumiInk(
    val background: Color,
    val lowest: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
    val dim: Color,
    val bright: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
)

/** Papel: cold, neutral paper. */
private val PaperInk = SumiInk(
    background = Color(0xFFEEEFF2),
    lowest = Color(0xFFFFFFFF),
    low = Color(0xFFF7F8FA),
    container = Color(0xFFF3F4F6),
    high = Color(0xFFE6E7EC),
    highest = Color(0xFFDCDDE4),
    dim = Color(0xFFDCDDE4),
    bright = Color(0xFFF7F8FA),
    line = Color(0xFFC6C7D0),
    lineStrong = Color(0xFF6C6D7B),
    text = Color(0xFF17161B),
    text2 = Color(0xFF44434D),
)

/** Sumi: tinted ink, never flat black. */
private val SumiDarkInk = SumiInk(
    background = Color(0xFF131217),
    lowest = Color(0xFF0E0D11),
    low = Color(0xFF17161B),
    container = Color(0xFF1A191F),
    high = Color(0xFF212027),
    highest = Color(0xFF2B2A32),
    dim = Color(0xFF131217),
    bright = Color(0xFF33323A),
    line = Color(0xFF3B3A44),
    lineStrong = Color(0xFF8C8A97),
    text = Color(0xFFECE9F1),
    text2 = Color(0xFFC4C0CD),
)

/** Night: pure black base, the surfaces of `[data-mode="oled"]`. */
private val NightInk = SumiInk(
    background = Color.Black,
    lowest = Color.Black,
    low = Color(0xFF0B0A0E),
    container = Color(0xFF0F0E12),
    high = Color(0xFF141318),
    highest = Color(0xFF1D1C22),
    dim = Color.Black,
    bright = Color(0xFF26252C),
    line = Color(0xFF2C2B32),
    lineStrong = Color(0xFF85838F),
    text = Color(0xFFECE9F1),
    text2 = Color(0xFFC4C0CD),
)

/** One accent of a role in light (`l*`) and dark (`d*`): the color, on it, its soft container and on the container. */
internal class Accent(
    val light: Color,
    val onLight: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color,
)

/** Shu vermilion, the 虹 seal: tertiary of every palette but Luna de sangre. */
private val Shu = Accent(
    light = Color(0xFFA83224), onLight = Color.White, lightContainer = Color(0xFFF8DCD6), onLightContainer = Color(0xFF4B0F07),
    dark = Color(0xFFFFAB98), onDark = Color(0xFF4A0D04), darkContainer = Color(0xFF5A2219), onDarkContainer = Color(0xFFFFDAD2),
)

/** Indigo of the kitsune robe: tertiary of Luna de sangre, whose accent is already red. */
private val Indigo = Accent(
    light = Color(0xFF3E5C76), onLight = Color.White, lightContainer = Color(0xFFD3E2F0), onLightContainer = Color(0xFF0F2436),
    dark = Color(0xFFA9C7E3), onDark = Color(0xFF0F2436), darkContainer = Color(0xFF2C4359), onDarkContainer = Color(0xFFD6E7F7),
)

/** Hanko inks. Dark tones: shubun (light seal, kanji in sumi). Light tone: hakubun (deep seal, kanji in paper). */
internal object SumiStatusInks {
    val light = StatusColors(
        watching = Color(0xFF2C5BBF),
        planning = Color(0xFF855900),
        completed = Color(0xFF1F7444),
        paused = Color(0xFF565A6C),
        dropped = Color(0xFFB5372A),
        rewatching = Color(0xFF00686D),
    )
    val dark = StatusColors(
        watching = Color(0xFF8FB2FF),
        planning = Color(0xFFE8C170),
        completed = Color(0xFF86CF9D),
        paused = Color(0xFFB4B8C8),
        dropped = Color(0xFFFF9A85),
        rewatching = Color(0xFF6FD0CC),
    )
}

private val SignalLight = SignalColors(ok = Color(0xFF1A6A3D), warn = Color(0xFF855900), err = Color(0xFFB3261E), info = Color(0xFF0B5CAD))
private val SignalDark = SignalColors(ok = Color(0xFF7FD3A0), warn = Color(0xFFF0C46E), err = Color(0xFFFF9F92), info = Color(0xFF9CC8FF))

/** Straight sRGB interpolation (the contrast figures were computed this way; `lerp` of Compose works in Oklab). */
private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun ContrastLevel.k(): Float = when (this) {
    ContrastLevel.Standard -> 0f
    ContrastLevel.Medium -> 0.5f
    ContrastLevel.High -> 1f
}

/** One Sumi palette. The seven values per tone are `--sp-*` of the artifact. */
internal class SumiPalette(
    override val id: String,
    override val swatch: Color,
    private val label: StringResource,
    /** The kanji of its seal in the palette picker (紫 桜 茶 海 月 墨). */
    val kanji: String,
    private val accent: Accent,
    private val tertiary: Accent,
) : DirectionPalette {

    @Composable
    override fun name(): String = stringResource(label)

    override fun scheme(tone: ThemeTone, contrast: ContrastLevel): ColorScheme {
        val k = contrast.k()
        val ink = when (tone) {
            ThemeTone.LIGHT -> PaperInk
            ThemeTone.DARK -> SumiDarkInk
            ThemeTone.OLED -> NightInk
        }
        val light = tone == ThemeTone.LIGHT
        // Higher contrast pushes accents away from the surface (darker on paper, lighter on sumi) and text2 / lines towards text.
        val away = if (light) Color.Black else Color.White
        val text2 = mix(ink.text2, ink.text, 0.5f * k)
        val outline = mix(ink.lineStrong, ink.text, 0.35f * k)
        val outlineVariant = mix(ink.line, ink.lineStrong, 0.4f * k)
        val push = if (light) 0.3f * k else 0.35f * k
        val pushOn = if (light) 0.5f * k else 0.6f * k

        val primary = mix(if (light) accent.light else accent.dark, away, push)
        val onPrimary = if (light) accent.onLight else accent.onDark
        val primaryContainer = if (light) accent.lightContainer else accent.darkContainer
        val onPrimaryContainer = mix(if (light) accent.onLightContainer else accent.onDarkContainer, away, pushOn)
        val secondary = mix(mix(if (light) accent.light else accent.dark, ink.text2, 0.45f), away, push)
        val tertiaryColor = mix(if (light) tertiary.light else tertiary.dark, away, push)
        val onTertiary = if (light) tertiary.onLight else tertiary.onDark
        val tertiaryContainer = if (light) tertiary.lightContainer else tertiary.darkContainer
        val onTertiaryContainer = mix(if (light) tertiary.onLightContainer else tertiary.onDarkContainer, away, pushOn)

        val error = if (light) Color(0xFFB3261E) else Color(0xFFFF9F92)
        val inverseSurface = if (light) SumiDarkInk.highest else PaperInk.background
        return ColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = if (light) accent.dark else accent.light,
            secondary = secondary,
            onSecondary = onPrimary,
            // The tonal role of the artifact is the accent soft (`--nj-accent-soft`): nav indicator, tonal buttons, selected chips.
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = tertiaryColor,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = ink.background,
            onBackground = ink.text,
            surface = ink.background,
            onSurface = ink.text,
            surfaceVariant = ink.highest,
            onSurfaceVariant = text2,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = if (light) SumiDarkInk.text else PaperInk.text,
            error = error,
            onError = if (light) Color.White else Color(0xFF4A0C06),
            errorContainer = if (light) Color(0xFFF9DEDC) else Color(0xFF5C1F19),
            onErrorContainer = if (light) Color(0xFF410E0B) else Color(0xFFFFDAD5),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = ink.bright,
            surfaceDim = ink.dim,
            surfaceContainer = ink.container,
            surfaceContainerHigh = ink.high,
            surfaceContainerHighest = ink.highest,
            surfaceContainerLow = ink.low,
            surfaceContainerLowest = ink.lowest,
            primaryFixed = accent.lightContainer,
            primaryFixedDim = accent.dark,
            onPrimaryFixed = accent.onLightContainer,
            onPrimaryFixedVariant = accent.light,
            secondaryFixed = accent.lightContainer,
            secondaryFixedDim = accent.dark,
            onSecondaryFixed = accent.onLightContainer,
            onSecondaryFixedVariant = accent.light,
            tertiaryFixed = tertiary.lightContainer,
            tertiaryFixedDim = tertiary.dark,
            onTertiaryFixed = tertiary.onLightContainer,
            onTertiaryFixedVariant = tertiary.light,
        )
    }

    override fun status(tone: ThemeTone): StatusColors = if (tone == ThemeTone.LIGHT) SumiStatusInks.light else SumiStatusInks.dark

    override fun signal(tone: ThemeTone): SignalColors = if (tone == ThemeTone.LIGHT) SignalLight else SignalDark
}

private fun accentOf(al: Long, sl: Long, osl: Long, ad: Long, ond: Long, sd: Long, osd: Long) = Accent(
    light = Color(al), onLight = Color.White, lightContainer = Color(sl), onLightContainer = Color(osl),
    dark = Color(ad), onDark = Color(ond), darkContainer = Color(sd), onDarkContainer = Color(osd),
)

/** The six palettes, curated from the twenty of Canon. The first (Violeta Nijiyome) is the default. */
internal val SumiPaletteList: List<SumiPalette> = listOf(
    SumiPalette(
        "violeta", Color(0xFF7C4DFF), Res.string.rd_sumi_palette_violeta, "紫",
        accentOf(0xFF5B34D1, 0xFFE2DBFB, 0xFF29106E, 0xFFB8A3FF, 0xFF1E1145, 0xFF372B5E, 0xFFE9E1FF), Shu,
    ),
    SumiPalette(
        "sakura", Color(0xFFE86A92), Res.string.rd_sumi_palette_sakura, "桜",
        accentOf(0xFFA82A5C, 0xFFFADBE6, 0xFF5A0F30, 0xFFFF9FC0, 0xFF3D0A20, 0xFF5A2338, 0xFFFFE0EB), Shu,
    ),
    SumiPalette(
        "matcha", Color(0xFF5E9E6E), Res.string.rd_sumi_palette_matcha, "茶",
        accentOf(0xFF2F6B3F, 0xFFD9EBDC, 0xFF123D1E, 0xFF8FD19E, 0xFF0D2A16, 0xFF24432C, 0xFFD5F2DA), Shu,
    ),
    SumiPalette(
        "oceano", Color(0xFF0091C2), Res.string.rd_sumi_palette_oceano, "海",
        accentOf(0xFF0B5F8F, 0xFFD5E8F5, 0xFF06344F, 0xFF7CC6F0, 0xFF062536, 0xFF1C3E55, 0xFFD4ECFB), Shu,
    ),
    SumiPalette(
        "luna", Color(0xFFC62828), Res.string.rd_sumi_palette_luna, "月",
        accentOf(0xFFA8231F, 0xFFF7DBD8, 0xFF5C0D0A, 0xFFFF8F86, 0xFF3D0605, 0xFF55201D, 0xFFFFDCD8), Indigo,
    ),
    SumiPalette(
        "mono", Color(0xFF808080), Res.string.rd_sumi_palette_mono, "墨",
        accentOf(0xFF26252B, 0xFFDCDBE0, 0xFF1D1C22, 0xFFE6E4EA, 0xFF131217, 0xFF34333A, 0xFFECEBF0), Shu,
    ),
)
