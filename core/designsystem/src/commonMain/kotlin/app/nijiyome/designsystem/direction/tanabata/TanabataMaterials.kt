// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction.tanabata

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.direction.ThemeTone

/** The three tones of a bamboo rod: lit edge, body, shaded edge (artifact `--tz-bamboo-hi`, `--tz-bamboo`, `--tz-bamboo-lo`). */
@Immutable
data class Bamboo(val hi: Color, val body: Color, val lo: Color)

/**
 * Paper, thread, bamboo and lantern of Tanabata (artifact `--tz-*`). They do not depend on the palette, so the tanzaku cards
 * read them under any direction (a card style can be mixed with any direction).
 */
object TanabataMaterials {
    /** Sumi ink on paper; every text on a tanzaku uses it. */
    val ink: Color = Color(0xFF14102A)

    private val paperDark = mapOf(
        StatusSlot.WATCHING to Color(0xFFF27A5E),
        StatusSlot.PLANNING to Color(0xFF8AACF5),
        StatusSlot.COMPLETED to Color(0xFF7ACD97),
        StatusSlot.PAUSED to Color(0xFFF3D36A),
        StatusSlot.DROPPED to Color(0xFFBBA3F2),
        StatusSlot.REWATCHING to Color(0xFFF4A8C5),
    )
    private val paperLight = mapOf(
        StatusSlot.WATCHING to Color(0xFFF28466),
        StatusSlot.PLANNING to Color(0xFF8FB0F6),
        StatusSlot.COMPLETED to Color(0xFF7FCF9B),
        StatusSlot.PAUSED to Color(0xFFF4D570),
        StatusSlot.DROPPED to Color(0xFFBFA8F3),
        StatusSlot.REWATCHING to Color(0xFFF5AECB),
    )

    /** Kinari, the paper of a title with no list status. */
    fun blankPaper(tone: ThemeTone): Color = if (tone == ThemeTone.LIGHT) Color(0xFFEFE8D6) else Color(0xFFEBE3D0)

    /** Paper color of a list status: bermellón, blue, green, yellow, violet, pink; kinari for none. Ink on any of them is 6.7:1 or more. */
    fun paper(status: StatusSlot?, tone: ThemeTone): Color =
        status?.let { (if (tone == ThemeTone.LIGHT) paperLight else paperDark).getValue(it) } ?: blankPaper(tone)

    /** The inked part of a paper (progress runs down the strip as ink). Ink on it stays above 5:1. */
    fun inked(paper: Color): Color = lerp(paper, ink, 0.16f)

    /** The soft edge where the ink stops. */
    fun inkEdge(paper: Color): Color = lerp(paper, ink, 0.34f)

    /** A pressed paper. */
    fun pressed(paper: Color): Color = inked(paper)

    fun thread(tone: ThemeTone): Color = if (tone == ThemeTone.LIGHT) Color(0xFF8F8068) else Color(0xFFD8CCB2)

    fun bamboo(tone: ThemeTone): Bamboo = if (tone == ThemeTone.LIGHT) {
        Bamboo(hi = Color(0xFFA9CB88), body = Color(0xFF6C9455), lo = Color(0xFF3F6233))
    } else {
        Bamboo(hi = Color(0xFF9FC27F), body = Color(0xFF5F8A4C), lo = Color(0xFF34512B))
    }

    /** Lantern amber: only for focus and for what happens now. 3:1 or more on every Tanabata ground (a UI color, not a text color). */
    fun lantern(tone: ThemeTone): Color = if (tone == ThemeTone.LIGHT) Color(0xFFA55A00) else Color(0xFFFFB54D)

    /** The amber of a fresh episode's thread and stamp; the same in both tones (it sits on paper or on the rod, never as text). */
    val freshAmber: Color = Color(0xFFFFB54D)

    /** Fibres of the washi texture over a paper: a lighter version of the paper itself. */
    fun fibre(paper: Color): Color = lerp(paper, Color.White, 0.55f)

    /** Shadow color under paper (artifact `--tz-paper-shadow`). */
    fun paperShadow(tone: ThemeTone): Color = if (tone == ThemeTone.LIGHT) Color(0x591E2250) else Color(0xB302030E)
}
