// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.sumi

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import app.nijiyome.designsystem.direction.DirectionDecor
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.DirectionTrack
import app.nijiyome.designsystem.resources.Res
import app.nijiyome.designsystem.resources.rd_sumi_name
import app.nijiyome.designsystem.resources.rd_sumi_tagline
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane D4: "Sumi y hanko" of docs/redesign/artefacto/dir-sumi.{css,js}, the track that keeps the identity:
// the Nijiyome violet, the kitsune inside the ensō, the 虹 seal, and list statuses as hanko seals.

/** Sumi ink or cold paper, the violet accent, hanko seals as list statuses and the ensō stroke as progress. */
object SumiDirection : DirectionSpec {
    override val id: String = "sumi"
    override val track: DirectionTrack = DirectionTrack.IDENTITY
    override val palettes: List<DirectionPalette> = SumiPaletteList

    @Composable
    override fun name(): String = stringResource(Res.string.rd_sumi_name)

    @Composable
    override fun tagline(): String = stringResource(Res.string.rd_sumi_tagline)

    @Composable
    override fun typography(base: Typography): Typography {
        val ui = sumiUiFamily()
        val brush = sumiBrushFamily()
        return remember(base, ui, brush) { sumiTypography(base, ui, brush) }
    }

    @Composable
    override fun accentFont(): FontFamily = sumiBrushFamily()

    override val shapes: Shapes get() = SumiShapes
    override val decor: DirectionDecor get() = SumiDecor

    /** Kanji of the seal of palette [id] in a picker (紫 桜 茶 海 月 墨); null for an unknown id. */
    fun paletteKanji(id: String): String? = SumiPaletteList.firstOrNull { it.id == id }?.kanji
}
