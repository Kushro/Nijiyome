// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.generated.NijiyomeTokens
import app.nijiyome.designsystem.generated.PaletteInfo
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_core_canon_name
import app.nijiyome.designsystem.resources.rd_core_canon_tagline
import app.nijiyome.designsystem.theme.toAmoled
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign. Owner: integrator. The reference implementation of DirectionSpec: copy its shape, not its values.

/** List status colors of Canon: fixed hues that read on Material surfaces in both tones. */
object CanonStatusColors {
    val light = StatusColors(
        watching = Color(0xFF1F6FEB),
        planning = Color(0xFF7A5AF8),
        completed = Color(0xFF1A7F37),
        paused = Color(0xFF9A6700),
        dropped = Color(0xFFCF222E),
        rewatching = Color(0xFF0E7C86),
    )
    val dark = StatusColors(
        watching = Color(0xFF6CB2FF),
        planning = Color(0xFFB7A4FF),
        completed = Color(0xFF56D364),
        paused = Color(0xFFE3B341),
        dropped = Color(0xFFFF7B72),
        rewatching = Color(0xFF39C5CF),
    )
}

object CanonSignalColors {
    val light = SignalColors(ok = Color(0xFF1A7F37), warn = Color(0xFF9A6700), err = Color(0xFFCF222E), info = Color(0xFF0969DA))
    val dark = SignalColors(ok = Color(0xFF56D364), warn = Color(0xFFE3B341), err = Color(0xFFFF7B72), info = Color(0xFF79C0FF))
}

/** One of the 20 generated token palettes (tokens/tokens.json) as a [DirectionPalette]. */
private class CanonPalette(private val info: PaletteInfo) : DirectionPalette {
    override val id: String get() = info.id
    override val swatch: Color get() = info.seed

    @Composable
    override fun name(): String = info.label

    override fun scheme(tone: ThemeTone, contrast: ContrastLevel): ColorScheme {
        val schemes = NijiyomeTokens.palette(info.id, contrast)
            ?: checkNotNull(NijiyomeTokens.palette(NijiyomeTokens.DEFAULT_PALETTE, contrast)) { "default palette missing" }
        return when (tone) {
            ThemeTone.LIGHT -> schemes.light
            ThemeTone.DARK -> schemes.dark
            ThemeTone.OLED -> schemes.amoled.toAmoled(contrast)
        }
    }

    override fun status(tone: ThemeTone): StatusColors = if (tone == ThemeTone.LIGHT) CanonStatusColors.light else CanonStatusColors.dark

    override fun signal(tone: ThemeTone): SignalColors = if (tone == ThemeTone.LIGHT) CanonSignalColors.light else CanonSignalColors.dark
}

/** Material 3 as it is today: the 20 palettes, the generated type scale and shapes, no decoration. */
object CanonDirection : DirectionSpec {
    override val id: String = "canon"
    override val track: DirectionTrack = DirectionTrack.CANON
    override val palettes: List<DirectionPalette> = NijiyomeTokens.paletteInfo.map { CanonPalette(it) }

    @Composable
    override fun name(): String = stringResource(Res.string.rd_core_canon_name)

    @Composable
    override fun tagline(): String = stringResource(Res.string.rd_core_canon_tagline)

    /** The default palette is the app's (`nijiyome-violet`), not the first of the list. */
    override fun palette(id: String?): DirectionPalette =
        palettes.firstOrNull { it.id == id } ?: palettes.first { it.id == NijiyomeTokens.DEFAULT_PALETTE }
}

/** Every direction, in picker order. The four stubs are filled by lanes D1 to D4; nobody else edits this list. */
object Directions {
    val all: List<DirectionSpec> = listOf(
        app.nijiyome.designsystem.direction.tanabata.TanabataDirection,
        app.nijiyome.designsystem.direction.obi.ObiDirection,
        app.nijiyome.designsystem.direction.transit.TransitDirection,
        app.nijiyome.designsystem.direction.sumi.SumiDirection,
        CanonDirection,
    )

    /** The direction with [id]; Canon for an unknown id. */
    fun byId(id: String?): DirectionSpec = all.firstOrNull { it.id == id } ?: CanonDirection
}
