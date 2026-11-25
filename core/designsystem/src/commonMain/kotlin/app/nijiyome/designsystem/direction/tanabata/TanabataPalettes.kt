// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.SignalColors
import app.nijiyome.designsystem.direction.StatusColors
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_tanabata_palette_alba
import app.nijiyome.designsystem.resources.rd_tanabata_palette_bambu
import app.nijiyome.designsystem.resources.rd_tanabata_palette_festival
import app.nijiyome.designsystem.resources.rd_tanabata_palette_noche
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The `--nj-*` color tokens of one palette in one tone (docs/redesign/artefacto/dir-tanabata.css). Text on every surface was checked
 * at 5.1:1 or more (text-3 worst case), accent on its ground at 6.3:1 or more, line-strong at 3.6:1 or more.
 */
internal data class TzTokens(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
) {
    /** OLED: the same tokens on a pure black floor with the palette's own near-black panels. */
    fun oled(surface: Color, surface2: Color, surface3: Color, line: Color): TzTokens =
        copy(bg = Color.Black, surface = surface, surface2 = surface2, surface3 = surface3, line = line)
}

private val statusDark = StatusColors(
    watching = Color(0xFFF6876C),
    planning = Color(0xFF8FB1FF),
    completed = Color(0xFF7FD39D),
    paused = Color(0xFFF3D46E),
    dropped = Color(0xFFC0A6F6),
    rewatching = Color(0xFFF5A8C6),
)

// 4.2:1 or more on surface-3, 4.9:1 or more on the floor: as marks they need 3:1.
private val statusLight = StatusColors(
    watching = Color(0xFFA02F19),
    planning = Color(0xFF2A55B8),
    completed = Color(0xFF1C6435),
    paused = Color(0xFF724F00),
    dropped = Color(0xFF6A45B8),
    rewatching = Color(0xFF942A5B),
)

private val signalDark = SignalColors(ok = Color(0xFF7FD6A0), warn = Color(0xFFF5C46B), err = Color(0xFFFF9FAE), info = Color(0xFF9EC0FF))

// The artifact's light err/info (#b3261e, #1f56b0) fall to 4.1-4.4:1 on surface-3; darkened a step to pass 4.5:1 there.
private val signalLight = SignalColors(ok = Color(0xFF19643A), warn = Color(0xFF744D00), err = Color(0xFFA3221A), info = Color(0xFF1B4FA3))

internal class TanabataPalette(
    override val id: String,
    override val swatch: Color,
    private val label: StringResource,
    private val dark: TzTokens,
    private val light: TzTokens,
    private val oled: TzTokens,
) : DirectionPalette {
    @Composable
    override fun name(): String = stringResource(label)

    private val schemes: Map<Pair<ThemeTone, ContrastLevel>, ColorScheme> by lazy {
        ThemeTone.entries.flatMap { tone -> ContrastLevel.entries.map { c -> (tone to c) to build(tone, c) } }.toMap()
    }

    override fun scheme(tone: ThemeTone, contrast: ContrastLevel): ColorScheme = schemes.getValue(tone to contrast)

    override fun status(tone: ThemeTone): StatusColors = if (tone == ThemeTone.LIGHT) statusLight else statusDark

    override fun signal(tone: ThemeTone): SignalColors = if (tone == ThemeTone.LIGHT) signalLight else signalDark

    private fun build(tone: ThemeTone, contrast: ContrastLevel): ColorScheme {
        val t = when (tone) {
            ThemeTone.LIGHT -> light
            ThemeTone.DARK -> dark
            ThemeTone.OLED -> oled
        }
        val isLight = tone == ThemeTone.LIGHT
        // Contrast levels pull secondary text and lines toward the main text; on light, the accent darkens too.
        val k = when (contrast) {
            ContrastLevel.Standard -> 0f
            ContrastLevel.Medium -> 1f
            ContrastLevel.High -> 2f
        }
        val textVariant = lerp(t.text2, t.text, 0.35f * k)
        val outline = lerp(t.lineStrong, t.text, 0.22f * k)
        val outlineVariant = lerp(t.line, t.lineStrong, 0.5f * k)
        val primary = if (isLight) lerp(t.accent, t.text, 0.15f * k) else t.accent
        val bamboo = if (isLight) lerp(Color(0xFF3F6233), t.text, 0.2f * k) else Color(0xFF9FC27F)
        val lantern = if (isLight) Color(0xFF8A4A00) else Color(0xFFFFB54D)

        val black = tone == ThemeTone.OLED
        val floor = t.bg
        val lowest = when {
            black -> Color.Black
            isLight -> lerp(t.surface, Color.White, 0.5f)
            else -> lerp(t.bg, Color.Black, 0.35f)
        }
        val low = when {
            black -> Color.Black
            isLight -> t.surface
            else -> lerp(t.bg, t.surface, 0.5f)
        }
        val container = if (isLight) lerp(t.surface, t.surface2, 0.5f) else t.surface

        return ColorScheme(
            primary = primary,
            onPrimary = t.onAccent,
            primaryContainer = t.accentSoft,
            onPrimaryContainer = t.onAccentSoft,
            inversePrimary = if (isLight) Color(0xFFC3CDFF) else Color(0xFF22307A),
            secondary = bamboo,
            onSecondary = if (isLight) Color.White else Color(0xFF13220F),
            secondaryContainer = t.accentSoft,
            onSecondaryContainer = t.onAccentSoft,
            tertiary = lantern,
            onTertiary = if (isLight) Color.White else Color(0xFF2A1600),
            tertiaryContainer = if (isLight) Color(0xFFF6DFC2) else lerp(t.surface, Color(0xFFFFB54D), 0.22f),
            onTertiaryContainer = if (isLight) Color(0xFF3A1F00) else Color(0xFFFFE2B8),
            background = floor,
            onBackground = t.text,
            surface = floor,
            onSurface = t.text,
            surfaceVariant = t.surface2,
            onSurfaceVariant = textVariant,
            surfaceTint = primary,
            // Snackbars and tooltips: a note written on kinari paper at night, an indigo slip by day (artifact .nj-snackbar).
            inverseSurface = if (isLight) Color(0xFF1D2350) else Color(0xFFEBE3D0),
            inverseOnSurface = if (isLight) Color(0xFFF1EADB) else TanabataMaterials.ink,
            error = if (isLight) Color(0xFFA3221A) else Color(0xFFFF9FAE),
            onError = if (isLight) Color.White else Color(0xFF4A0D18),
            errorContainer = if (isLight) Color(0xFFF6DCD9) else Color(0xFF5D1A2A),
            onErrorContainer = if (isLight) Color(0xFF410E0B) else Color(0xFFFFDADF),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = if (isLight) Color(0xFF141838) else Color(0xFF04050F),
            surfaceBright = if (isLight) t.surface else lerp(t.surface3, t.text, 0.08f),
            surfaceDim = if (isLight) lerp(t.bg, t.surface3, 0.5f) else floor,
            surfaceContainer = container,
            surfaceContainerHigh = t.surface2,
            surfaceContainerHighest = t.surface3,
            surfaceContainerLow = low,
            surfaceContainerLowest = lowest,
            primaryFixed = light.accentSoft,
            primaryFixedDim = lerp(light.accentSoft, light.accent, 0.25f),
            onPrimaryFixed = light.onAccentSoft,
            onPrimaryFixedVariant = light.accent,
            secondaryFixed = Color(0xFFCFE5B8),
            secondaryFixedDim = Color(0xFF9FC27F),
            onSecondaryFixed = Color(0xFF13220F),
            onSecondaryFixedVariant = Color(0xFF3F6233),
            tertiaryFixed = Color(0xFFFFDCB0),
            tertiaryFixedDim = Color(0xFFFFB54D),
            onTertiaryFixed = Color(0xFF2A1600),
            onTertiaryFixedVariant = Color(0xFF6B3A00),
        )
    }
}

/** The four palettes of `registerDirection({ palettes })`, in picker order; Noche índigo is the default. */
internal object TanabataPalettes {
    private val nocheDark = TzTokens(
        bg = Color(0xFF150F3B), surface = Color(0xFF1C1647), surface2 = Color(0xFF231C53), surface3 = Color(0xFF2D2562),
        line = Color(0xFF30424A), lineStrong = Color(0xFF73A07E),
        text = Color(0xFFF0EEFB), text2 = Color(0xFFCAC5EC),
        accent = Color(0xFFF1EADB), onAccent = Color(0xFF170F36), accentSoft = Color(0xFF352B74), onAccentSoft = Color(0xFFEBE7FF),
    )

    val noche = TanabataPalette(
        id = "noche",
        swatch = Color(0xFF283889),
        label = Res.string.rd_tanabata_palette_noche,
        dark = nocheDark,
        light = TzTokens(
            bg = Color(0xFFE1DCEA), surface = Color(0xFFEDE8F1), surface2 = Color(0xFFDED7E7), surface3 = Color(0xFFD2CADD),
            line = Color(0xFFADC0A6), lineStrong = Color(0xFF557A4C),
            text = Color(0xFF14123A), text2 = Color(0xFF37366B),
            accent = Color(0xFF2E2A8A), onAccent = Color.White, accentSoft = Color(0xFFB8B5E7), onAccentSoft = Color(0xFF1B1766),
        ),
        oled = nocheDark.oled(Color(0xFF0C0921), Color(0xFF120E2C), Color(0xFF1A153B), Color(0xFF223338)),
    )

    private val festivalDark = TzTokens(
        bg = Color(0xFF1A1026), surface = Color(0xFF22152F), surface2 = Color(0xFF2A1A39), surface3 = Color(0xFF352246),
        line = Color(0xFF3C4434), lineStrong = Color(0xFF8EA36F),
        text = Color(0xFFF7EEF2), text2 = Color(0xFFDCCAD5),
        accent = Color(0xFFF8DCC0), onAccent = Color(0xFF24121C), accentSoft = Color(0xFF46283F), onAccentSoft = Color(0xFFFBE6F0),
    )

    val festival = TanabataPalette(
        id = "festival",
        swatch = Color(0xFFF27A5E),
        label = Res.string.rd_tanabata_palette_festival,
        dark = festivalDark,
        light = TzTokens(
            bg = Color(0xFFF0E3E8), surface = Color(0xFFF7EDF1), surface2 = Color(0xFFE8D6DE), surface3 = Color(0xFFDDC8D1),
            line = Color(0xFFC4C5A6), lineStrong = Color(0xFF5F7340),
            text = Color(0xFF2A1420), text2 = Color(0xFF553646),
            accent = Color(0xFF74204F), onAccent = Color.White, accentSoft = Color(0xFFE2B6CB), onAccentSoft = Color(0xFF4A1034),
        ),
        oled = festivalDark.oled(Color(0xFF0F0812), Color(0xFF170D1B), Color(0xFF211426), Color(0xFF2B3226)),
    )

    private val albaDark = TzTokens(
        bg = Color(0xFF121826), surface = Color(0xFF19202F), surface2 = Color(0xFF1F2738), surface3 = Color(0xFF283144),
        line = Color(0xFF2F4240), lineStrong = Color(0xFF7C9C84),
        text = Color(0xFFF0F1F4), text2 = Color(0xFFC9CDD8),
        accent = Color(0xFFF3D3BF), onAccent = Color(0xFF251610), accentSoft = Color(0xFF343A50), onAccentSoft = Color(0xFFEEE2F0),
    )

    val alba = TanabataPalette(
        id = "alba",
        swatch = Color(0xFFE6DCC4),
        label = Res.string.rd_tanabata_palette_alba,
        dark = albaDark,
        light = TzTokens(
            bg = Color(0xFFE9E3EA), surface = Color(0xFFF3EEF3), surface2 = Color(0xFFDED6DF), surface3 = Color(0xFFD3CAD5),
            line = Color(0xFFB8C1A6), lineStrong = Color(0xFF5A7044),
            text = Color(0xFF1D1A2B), text2 = Color(0xFF433E54),
            accent = Color(0xFF2A4688), onAccent = Color.White, accentSoft = Color(0xFFB9C3E3), onAccentSoft = Color(0xFF152C5E),
        ),
        oled = albaDark.oled(Color(0xFF0A0E16), Color(0xFF10151F), Color(0xFF181E2B), Color(0xFF223230)),
    )

    private val bambuDark = TzTokens(
        bg = Color(0xFF0B1613), surface = Color(0xFF10201B), surface2 = Color(0xFF152822), surface3 = Color(0xFF1D342C),
        line = Color(0xFF2B4A3A), lineStrong = Color(0xFF7FAE7C),
        text = Color(0xFFEDF4EE), text2 = Color(0xFFC3D6C8),
        accent = Color(0xFFD8EBB5), onAccent = Color(0xFF13220F), accentSoft = Color(0xFF24402F), onAccentSoft = Color(0xFFDFF0D6),
    )

    val bambu = TanabataPalette(
        id = "bambu",
        swatch = Color(0xFF6F9A5A),
        label = Res.string.rd_tanabata_palette_bambu,
        dark = bambuDark,
        light = TzTokens(
            bg = Color(0xFFE2EADF), surface = Color(0xFFEDF3EA), surface2 = Color(0xFFD7E2D1), surface3 = Color(0xFFCBD9C4),
            line = Color(0xFFA8C09A), lineStrong = Color(0xFF4B7040),
            text = Color(0xFF142019), text2 = Color(0xFF374A3D),
            accent = Color(0xFF2F5C2A), onAccent = Color.White, accentSoft = Color(0xFFB6CFA6), onAccentSoft = Color(0xFF183516),
        ),
        oled = bambuDark.oled(Color(0xFF07110E), Color(0xFF0C1915), Color(0xFF13241E), Color(0xFF1F3A2C)),
    )

    val all: List<DirectionPalette> = listOf(noche, festival, alba, bambu)
}
