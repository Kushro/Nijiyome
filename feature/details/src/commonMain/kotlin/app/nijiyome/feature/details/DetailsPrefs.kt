// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

/**
 * Module-private persistence of Details (15 §3.5: a field that AppSettings does not carry is kept privately by its owner):
 * the 18+ gate decisions per title (`details.adult-gate`) and the episode list view mode (`details.episodes.view-mode`).
 */
interface DetailsPrefs {
    fun adultAccepted(anilistId: Int): Boolean

    fun acceptAdult(anilistId: Int)

    fun episodeView(): EpisodeView

    fun setEpisodeView(view: EpisodeView)
}

/**
 * 2026-11 UI refresh: the shared Diseño y estilo choice (CARDS = large cards with synopsis, LIST = rows with a small thumbnail,
 * COMPACT = numbers and titles). The ids of the old private enum (LIST / COMPACT) are the same names, so stored values still read.
 */
typealias EpisodeView = app.nijiyome.model.EpisodeView

/** Default for tests and previews. */
class InMemoryDetailsPrefs : DetailsPrefs {
    private val adult = mutableSetOf<Int>()
    private var view = EpisodeView.LIST

    override fun adultAccepted(anilistId: Int): Boolean = anilistId in adult

    override fun acceptAdult(anilistId: Int) {
        adult += anilistId
    }

    override fun episodeView(): EpisodeView = view

    override fun setEpisodeView(view: EpisodeView) {
        this.view = view
    }
}
