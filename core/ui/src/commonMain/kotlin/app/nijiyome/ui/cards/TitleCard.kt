// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.theme.parseHexColor
import app.nijiyome.model.CardSize
import app.nijiyome.model.Media
import app.nijiyome.model.TitleCardIds
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.effectiveCardId
import app.nijiyome.ui.media.LocalTitleLanguage
import app.nijiyome.ui.media.MediaStatusKind
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.media.mediaStatusKind

// Sparkles redesign contract (docs/redesign/CONTRATO-COMPOSE.md). Owner: integrator. FROZEN: names and signatures do not change.
// A screen draws a title with TitleCard(...) and never knows which style is active; a style draws every context and size.

/** Where the card sits (artifact `h.card` `context`). Every style must handle all six. */
enum class CardContext {
    /** Horizontal row of posters. */
    ROW,

    /** Wrapping grid (search results, library). */
    GRID,

    /** "Continuar": resume, shows progress and the episode to play. */
    CONTINUE,

    /** Wish / planning list: emphasis on when it airs or starts. */
    WISH,

    /** Dense list row (small poster, one or two lines). */
    COMPACT,

    /** Large header card (Home stage, featured). May use [TitleCardData.bannerUrl]. */
    HERO,
}

/** Everything a card may show. Screens fill what they have; a style ignores what it does not draw. All text is already localised. */
@Immutable
data class TitleCardData(
    /** Stable id of the title, e.g. `"anime:1234"`; also the shared-transition key towards Details. */
    val key: String,
    val title: String,
    val coverUrl: String?,
    /** Facts line under the title ("2026 · TV"). */
    val subtitle: String? = null,
    /** Watch / read progress of the current episode or of the series, 0..1. */
    val progress: Float? = null,
    /** The user's list status. */
    val listStatus: StatusSlot? = null,
    /** Localised label of [listStatus] ("Viendo"). */
    val listStatusLabel: String? = null,
    /** Airing status of the title. */
    val airing: MediaStatusKind? = null,
    /** "Ep 9 · en 6 d", "Cap. 180". */
    val nextLabel: String? = null,
    /** "7.4". */
    val score: String? = null,
    val episodesSeen: Int? = null,
    val episodesTotal: Int? = null,
    /** Episodes aired so far, when known. */
    val airedEpisodes: Int? = null,
    /** A new episode / chapter the user has not seen is out (cards may flag it). */
    val isNew: Boolean = false,
    val bannerUrl: String? = null,
    /** Cover color of the title, when the provider gives one. */
    val accent: Color? = null,
    val format: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
)

/** Which optional parts the screen wants (artifact `show`). */
@Immutable
data class CardShow(val progress: Boolean = true, val status: Boolean = true, val next: Boolean = true, val score: Boolean = false)

/** One card style. Implementations are `object`s listed in [TitleCards]. */
interface TitleCardStyle {
    /** A [TitleCardIds] id. */
    val id: String

    /** `DesignDirection.id` of the direction it comes from (any style works under any direction). */
    val directionId: String

    @Composable fun name(): String

    /**
     * Draws the card. The style owns its width in [CardContext.ROW] / [CardContext.CONTINUE] / [CardContext.WISH] (from [size] and
     * `LocalLook.current.density`, see `CardSizing`) and fills the width it is given in the other contexts. It handles press and
     * focus feedback, a 48 dp touch target, and semantics (one merged node with the title).
     */
    @Composable
    fun Card(
        data: TitleCardData,
        context: CardContext,
        size: CardSize,
        show: CardShow,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
    )
}

/** Every card style, in picker order. Stubs are filled by lanes D1 to D4; nobody else edits this list. */
object TitleCards {
    val all: List<TitleCardStyle> = listOf(
        MaterialCard.Classic, MaterialCard.Detailed, MaterialCard.Minimal,
        app.nijiyome.ui.cards.tanabata.TanzakuCard, app.nijiyome.ui.cards.tanabata.TanzakuSealCard,
        app.nijiyome.ui.cards.tanabata.TanzakuVerticalCard, app.nijiyome.ui.cards.tanabata.TanzakuThreadCard,
        app.nijiyome.ui.cards.obi.ObiCard, app.nijiyome.ui.cards.obi.ObiSpineCard, app.nijiyome.ui.cards.obi.ObiDiagonalCard,
        app.nijiyome.ui.cards.transit.StationCard, app.nijiyome.ui.cards.transit.TicketCard, app.nijiyome.ui.cards.transit.LineCard,
        app.nijiyome.ui.cards.sumi.HankoCard, app.nijiyome.ui.cards.sumi.EnsoCard, app.nijiyome.ui.cards.sumi.SumiPaperCard,
    )

    /** The style with [id]; Material detailed for an unknown id. */
    fun byId(id: String?): TitleCardStyle = all.firstOrNull { it.id == id } ?: MaterialCard.Detailed
}

/**
 * The play action of the card being drawn, set by [TitleCard] from its `onPlay`. A style reads it in [CardContext.CONTINUE] and
 * [CardContext.HERO]: non-null makes its play disc a real button (see [CardPlayTarget]); null keeps the disc decorative.
 */
val LocalCardPlayAction: ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

/**
 * Key of the title whose card is being drawn, set by [TitleCard] from [TitleCardData.key]. The card's cover ([app.nijiyome.ui.media.MediaCover])
 * registers itself as the source of the card-to-Details transition under it, so every style opens Details from its poster without
 * wiring it itself; null outside a [TitleCard].
 */
val LocalCardSourceKey: ProvidableCompositionLocal<String?> = compositionLocalOf { null }

/** Position of the card being drawn in its row or grid (0-based), set by [TitleCard] from its `index`; -1 = unknown. For staggering. */
val LocalCardIndex: ProvidableCompositionLocal<Int> = compositionLocalOf { -1 }

/**
 * The card of a title in the style the user picked. This is what screens call. [onPlay] = the resume / play action of a CONTINUE or
 * HERO card (the card's play disc becomes its button); [index] = the card's position in its row, when the screen has it.
 */
@Composable
fun TitleCard(
    data: TitleCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    context: CardContext = CardContext.ROW,
    size: CardSize = LocalLook.current.cardSize,
    show: CardShow = CardShow(),
    onLongClick: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    index: Int = -1,
) {
    val style = TitleCards.byId(LocalLook.current.effectiveCardId())
    CompositionLocalProvider(LocalCardPlayAction provides onPlay, LocalCardIndex provides index, LocalCardSourceKey provides data.key) {
        style.Card(data, context, size, show, onClick, modifier, onLongClick)
    }
}

/** Card data of a [Media] with nothing user-specific; `copy(...)` the progress, list status and next label on top. */
@Composable
fun Media.toCardData(titleLanguage: TitleLanguage = LocalTitleLanguage.current): TitleCardData = TitleCardData(
    key = "${kind.name.lowercase()}:${ref.anilistId ?: ref.malId ?: ref.kitsuId ?: title}",
    title = displayTitle(titleLanguage),
    coverUrl = cover,
    subtitle = app.nijiyome.ui.media.detailedFactsLine(this) ?: app.nijiyome.ui.media.mediaSubtitle(this),
    airing = mediaStatusKind(status),
    score = score?.let { java.lang.String.format(java.util.Locale.ROOT, "%.1f", it / 10.0) },
    episodesTotal = episodes,
    bannerUrl = banner,
    accent = parseHexColor(color),
    format = format,
    year = year,
    genres = genres,
    synopsis = synopsis,
)
