// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.obi

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.SignalColors
import app.nijiyome.designsystem.direction.StatusColors
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_obi_palette_limitada
import app.nijiyome.designsystem.resources.rd_obi_palette_mono
import app.nijiyome.designsystem.resources.rd_obi_palette_nocturna
import app.nijiyome.designsystem.resources.rd_obi_palette_tienda
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The four palettes of dir-obi.css (Parte 2), one token set per tone. Ink and paper for the tool; saturated color only on the bands.
//
// Role mapping (artifact token -> ColorScheme), shared by every palette so the Obi decoration and cards can read the scheme:
//   background = --nj-bg, surface = --nj-surface (OLED: pure black), surfaceContainer = --nj-surface-2,
//   surfaceContainerHigh = --nj-surface-3, surfaceContainerHighest = --obi-shelf (the shelf edge), outline = --nj-line-strong,
//   outlineVariant = --nj-line, primary = --nj-accent, primaryContainer = --nj-accent-soft,
//   secondary = --nj-text, secondaryContainer = --nj-accent-soft (Material fills tonal buttons, the selected settings category,
//   navigation indicators, chips and segments with it: ink there made a bone-white block compete with the red), tertiaryContainer = --obi-new
//   (the novelty band), tertiary = --obi-new where it reads as text (dark) or the planning amber (light), inverseSurface = ink
//   (the snackbar is an ink band).
// Every text pair was checked at >= 4.5:1 (status marks >= 3:1) for the four palettes in light, dark and OLED.

/** The `--nj-*` and `--obi-*` tokens of one palette in one tone. */
internal class ObiTokens(
    val bg: Long,
    val surface: Long,
    val surface2: Long,
    val surface3: Long,
    val line: Long,
    val lineStrong: Long,
    val text: Long,
    val text2: Long,
    val text3: Long,
    val accent: Long,
    val onAccent: Long,
    val accentSoft: Long,
    val onAccentSoft: Long,
    val focus: Long,
    val new: Long,
    val onNew: Long,
    val shelf: Long,
)

/** Status and signal colors are the same for the four palettes; dark and OLED share theirs. */
internal object ObiStatus {
    val dark = StatusColors(
        watching = Color(0xFF7AA7FF),
        planning = Color(0xFFF4C34A),
        completed = Color(0xFF5FD08E),
        paused = Color(0xFFB89DFF),
        dropped = Color(0xFFFF8A80),
        rewatching = Color(0xFF4FD3C6),
    )
    val light = StatusColors(
        watching = Color(0xFF2353C4),
        planning = Color(0xFF815600),
        completed = Color(0xFF1A7442),
        paused = Color(0xFF6C4CC0),
        dropped = Color(0xFFB3302A),
        rewatching = Color(0xFF00706F),
    )
    val signalDark = SignalColors(ok = Color(0xFF5FD08E), warn = Color(0xFFF4C34A), err = Color(0xFFFF8A80), info = Color(0xFF7AA7FF))
    val signalLight = SignalColors(ok = Color(0xFF1A7442), warn = Color(0xFF815600), err = Color(0xFFB3261E), info = Color(0xFF1F58C7))
}

internal class ObiPalette(
    override val id: String,
    swatch: Long,
    private val label: StringResource,
    private val dark: ObiTokens,
    private val oled: ObiTokens,
    private val light: ObiTokens,
) : DirectionPalette {
    override val swatch: Color = Color(swatch)

    @Composable
    override fun name(): String = stringResource(label)

    private val cache = HashMap<Pair<ThemeTone, ContrastLevel>, ColorScheme>()

    override fun scheme(tone: ThemeTone, contrast: ContrastLevel): ColorScheme =
        cache.getOrPut(tone to contrast) { build(tone, contrast) }

    override fun status(tone: ThemeTone): StatusColors = if (tone == ThemeTone.LIGHT) ObiStatus.light else ObiStatus.dark

    override fun signal(tone: ThemeTone): SignalColors = if (tone == ThemeTone.LIGHT) ObiStatus.signalLight else ObiStatus.signalDark

    private fun build(tone: ThemeTone, contrast: ContrastLevel): ColorScheme {
        val t = when (tone) {
            ThemeTone.LIGHT -> light
            ThemeTone.DARK -> dark
            ThemeTone.OLED -> oled
        }
        val isLight = tone == ThemeTone.LIGHT
        val oledTone = tone == ThemeTone.OLED
        val bg = Color(t.bg)
        val text = Color(t.text)
        val surface = if (oledTone) Color.Black else Color(t.surface)
        val signal = signal(tone)
        val status = status(tone)

        // Contrast levels: Standard is the artifact; Medium and High pull secondary text and lines towards the ink and lift the
        // accent until it reads at 5.5:1 / 7:1 on the background.
        val textFloor = when (contrast) {
            ContrastLevel.Standard -> 4.5f
            ContrastLevel.Medium -> 5.5f
            ContrastLevel.High -> 7f
        }
        val text2 = when (contrast) {
            ContrastLevel.Standard -> Color(t.text2)
            ContrastLevel.Medium -> ObiInk.mix(Color(t.text2), text, 0.5f)
            ContrastLevel.High -> text
        }
        val outline = when (contrast) {
            ContrastLevel.Standard -> Color(t.lineStrong)
            ContrastLevel.Medium -> ObiInk.mix(Color(t.lineStrong), text, 0.3f)
            ContrastLevel.High -> ObiInk.mix(Color(t.lineStrong), text, 0.6f)
        }
        val outlineVariant = when (contrast) {
            ContrastLevel.Standard -> Color(t.line)
            ContrastLevel.Medium -> ObiInk.mix(Color(t.line), Color(t.lineStrong), 0.4f)
            ContrastLevel.High -> Color(t.lineStrong)
        }
        val accent = ObiInk.ensureContrast(Color(t.accent), bg, textFloor, text)
        val tertiary = ObiInk.ensureContrast(if (isLight) status.planning else Color(t.new), bg, textFloor, text)
        val error = ObiInk.ensureContrast(signal.err, bg, textFloor, text)

        val surface2 = Color(t.surface2)
        val surface3 = Color(t.surface3)
        val shelf = Color(t.shelf)
        // Dark palettes use the third surface as their shelf: lift the shelf a little so the edge still reads against cards.
        val highest = if (isLight) shelf else ObiInk.mix(shelf, Color(t.line), 0.35f)
        val other = if (isLight) dark else light

        return ColorScheme(
            primary = accent,
            onPrimary = Color(t.onAccent),
            primaryContainer = Color(t.accentSoft),
            onPrimaryContainer = Color(t.onAccentSoft),
            inversePrimary = Color(other.accent),
            secondary = text,
            onSecondary = bg,
            secondaryContainer = Color(t.accentSoft),
            onSecondaryContainer = Color(t.onAccentSoft),
            tertiary = tertiary,
            onTertiary = if (isLight) Color.White else Color(t.onNew),
            tertiaryContainer = Color(t.new),
            onTertiaryContainer = Color(t.onNew),
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface2,
            onSurfaceVariant = text2,
            surfaceTint = text,
            inverseSurface = text,
            inverseOnSurface = bg,
            error = error,
            onError = if (isLight) Color.White else Color(0xFF2A0705),
            errorContainer = ObiInk.mix(if (isLight) Color(t.surface) else bg, signal.err, 0.2f),
            onErrorContainer = text,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = if (isLight) Color(t.surface) else surface3,
            surfaceDim = if (isLight) surface3 else bg,
            surfaceContainer = surface2,
            surfaceContainerHigh = surface3,
            surfaceContainerHighest = highest,
            surfaceContainerLow = if (isLight) bg else Color(t.surface),
            surfaceContainerLowest = if (isLight) Color(t.surface) else bg,
            primaryFixed = Color(light.accentSoft),
            primaryFixedDim = ObiInk.mix(Color(light.accentSoft), Color(light.accent), 0.35f),
            onPrimaryFixed = Color(light.onAccentSoft),
            onPrimaryFixedVariant = Color(light.accent),
            secondaryFixed = Color(light.surface3),
            secondaryFixedDim = Color(light.shelf),
            onSecondaryFixed = Color(light.text),
            onSecondaryFixedVariant = Color(light.text2),
            tertiaryFixed = Color(light.new),
            tertiaryFixedDim = ObiInk.mix(Color(light.new), Color(light.onNew), 0.15f),
            onTertiaryFixed = Color(light.onNew),
            onTertiaryFixedVariant = ObiInk.mix(Color(light.onNew), Color(light.new), 0.2f),
        )
    }
}

internal object ObiPalettes {
    val tienda = ObiPalette(
        id = "tienda",
        swatch = 0xFFC4122F,
        label = Res.string.rd_obi_palette_tienda,
        dark = ObiTokens(
            0xFF131217, 0xFF1B1A20, 0xFF222128, 0xFF2C2B33, 0xFF3B3A44, 0xFF8D8B97, 0xFFF3F1EC, 0xFFCFCCC4, 0xFFA9A69E,
            0xFFFF5C5C, 0xFF1A0505, 0xFF4D1A1D, 0xFFFFDCDB, 0xFFFFD60A, 0xFFFFD60A, 0xFF121212, 0xFF2C2B33,
        ),
        oled = ObiTokens(
            0xFF000000, 0xFF0C0C0E, 0xFF151518, 0xFF1F1F23, 0xFF2E2E34, 0xFF8D8B97, 0xFFF3F1EC, 0xFFCFCCC4, 0xFFA9A69E,
            0xFFFF5C5C, 0xFF1A0505, 0xFF4D1A1D, 0xFFFFDCDB, 0xFFFFD60A, 0xFFFFD60A, 0xFF121212, 0xFF1F1F23,
        ),
        light = ObiTokens(
            0xFFF4F3EF, 0xFFFFFFFF, 0xFFEBE9E3, 0xFFDFDDD5, 0xFFCBC8BF, 0xFF77736A, 0xFF121212, 0xFF3B3934, 0xFF57544D,
            0xFFC4122F, 0xFFFFFFFF, 0xFFF9D9DC, 0xFF6A0817, 0xFF121212, 0xFFFFD60A, 0xFF121212, 0xFFD6D2C8,
        ),
    )

    val nocturna = ObiPalette(
        id = "nocturna",
        swatch = 0xFFFFA23A,
        label = Res.string.rd_obi_palette_nocturna,
        dark = ObiTokens(
            0xFF0D1320, 0xFF131B2B, 0xFF192235, 0xFF212C42, 0xFF303D58, 0xFF8391AD, 0xFFEEF2F8, 0xFFC4CDDC, 0xFF9BA7BC,
            0xFFFFA23A, 0xFF1F1000, 0xFF3F2A0E, 0xFFFFDCB0, 0xFF7FD3FF, 0xFFFFE14D, 0xFF121212, 0xFF212C42,
        ),
        oled = ObiTokens(
            0xFF000000, 0xFF070B13, 0xFF0E1420, 0xFF172033, 0xFF253048, 0xFF8391AD, 0xFFEEF2F8, 0xFFC4CDDC, 0xFF9BA7BC,
            0xFFFFA23A, 0xFF1F1000, 0xFF3F2A0E, 0xFFFFDCB0, 0xFF7FD3FF, 0xFFFFE14D, 0xFF121212, 0xFF172033,
        ),
        light = ObiTokens(
            0xFFEDF0F5, 0xFFFFFFFF, 0xFFE2E7EF, 0xFFD5DCE7, 0xFFBFC8D6, 0xFF627089, 0xFF0D1320, 0xFF2F3B52, 0xFF4A566D,
            0xFF9A4C00, 0xFFFFFFFF, 0xFFFFE2C0, 0xFF4A2400, 0xFF0D1320, 0xFFFFD60A, 0xFF121212, 0xFFCDD5E1,
        ),
    )

    val limitada = ObiPalette(
        id = "limitada",
        swatch = 0xFFD6B46C,
        label = Res.string.rd_obi_palette_limitada,
        dark = ObiTokens(
            0xFF15171A, 0xFF1D2024, 0xFF25292E, 0xFF2F343A, 0xFF40464E, 0xFF8F97A1, 0xFFEEECE6, 0xFFC9C6BD, 0xFFA3A097,
            0xFFD6B46C, 0xFF1D1503, 0xFF3B3220, 0xFFF4E4BD, 0xFFE9EEF5, 0xFFDFE4EA, 0xFF121212, 0xFF2F343A,
        ),
        oled = ObiTokens(
            0xFF000000, 0xFF0B0C0E, 0xFF141619, 0xFF1E2125, 0xFF30353B, 0xFF8F97A1, 0xFFEEECE6, 0xFFC9C6BD, 0xFFA3A097,
            0xFFD6B46C, 0xFF1D1503, 0xFF3B3220, 0xFFF4E4BD, 0xFFE9EEF5, 0xFFDFE4EA, 0xFF121212, 0xFF1E2125,
        ),
        light = ObiTokens(
            0xFFECEBE7, 0xFFF9F8F5, 0xFFE4E3DE, 0xFFD8D6D0, 0xFFBBB8B0, 0xFF6B6860, 0xFF15171A, 0xFF393B40, 0xFF52545A,
            0xFF765913, 0xFFFFFFFF, 0xFFEDE0BD, 0xFF3A2B04, 0xFF15171A, 0xFFC7A447, 0xFF121212, 0xFFCFCCC4,
        ),
    )

    val mono = ObiPalette(
        id = "mono",
        swatch = 0xFF6B6B6B,
        label = Res.string.rd_obi_palette_mono,
        dark = ObiTokens(
            0xFF121212, 0xFF1A1A1A, 0xFF222222, 0xFF2C2C2C, 0xFF3A3A3A, 0xFF8E8E8E, 0xFFF2F2F2, 0xFFCCCCCC, 0xFFA6A6A6,
            0xFFF2F2F2, 0xFF121212, 0xFF333333, 0xFFF2F2F2, 0xFFFFD60A, 0xFFF2F2F2, 0xFF121212, 0xFF2C2C2C,
        ),
        oled = ObiTokens(
            0xFF000000, 0xFF0A0A0A, 0xFF141414, 0xFF1E1E1E, 0xFF2E2E2E, 0xFF8E8E8E, 0xFFF2F2F2, 0xFFCCCCCC, 0xFFA6A6A6,
            0xFFF2F2F2, 0xFF121212, 0xFF333333, 0xFFF2F2F2, 0xFFFFD60A, 0xFFF2F2F2, 0xFF121212, 0xFF1E1E1E,
        ),
        light = ObiTokens(
            0xFFF2F2F2, 0xFFFFFFFF, 0xFFE8E8E8, 0xFFDCDCDC, 0xFFC6C6C6, 0xFF707070, 0xFF121212, 0xFF3A3A3A, 0xFF555555,
            0xFF121212, 0xFFFFFFFF, 0xFFE0E0E0, 0xFF121212, 0xFF121212, 0xFF121212, 0xFFFFFFFF, 0xFFD4D4D4,
        ),
    )

    val all: List<DirectionPalette> = listOf(tienda, nocturna, limitada, mono)
}
