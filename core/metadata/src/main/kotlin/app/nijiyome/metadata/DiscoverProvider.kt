// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.metadata

import app.nijiyome.model.Character
import app.nijiyome.model.CharacterRef
import app.nijiyome.model.CharactersPage
import app.nijiyome.model.FeedPage
import app.nijiyome.model.MediaDetails
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MediaRelation
import app.nijiyome.model.MediaSearchQuery
import app.nijiyome.model.MediaTag
import app.nijiyome.model.Recommendation
import app.nijiyome.model.StaffEdge

/**
 * Parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.1, worker o): the "Descubrir y ficha" calls of the classic app
 * (`SearchPage`, `AnimeDetailLayout`, `CharacterPage`, `TrendingPage`). Only providers with [Capability.DISCOVER] implement it (AniList
 * today); the router reaches them in the user's priority order like the other capabilities.
 */
interface DiscoverProvider {
    /** Every filter of [query] applied server-side; [MediaSearchQuery.studio] switches to the studio's catalogue. */
    suspend fun searchAdvanced(kind: MediaKind, query: MediaSearchQuery, page: Int): FeedPage

    suspend fun details(ref: MediaRef): MediaDetails?

    suspend fun relations(ref: MediaRef): List<MediaRelation>

    /** Watch order: the PREQUEL / SEQUEL chain (anime only), first season first, [ref] included. */
    suspend fun seasons(ref: MediaRef): List<app.nijiyome.model.Media>

    suspend fun characters(ref: MediaRef, page: Int): CharactersPage

    suspend fun staff(ref: MediaRef): List<StaffEdge>

    suspend fun recommendations(ref: MediaRef): List<Recommendation>

    suspend fun character(id: Int): Character?

    suspend fun searchCharacters(query: String, page: Int): List<CharacterRef>

    suspend fun genres(kind: MediaKind): List<String>

    suspend fun tags(kind: MediaKind): List<MediaTag>

    /** Trending feed over a time window (classic `TrendingPage` today / week / month / all-time selector). */
    suspend fun trending(kind: MediaKind, window: TrendWindow, page: Int, adult: Boolean): FeedPage
}

/**
 * Time window of the trending feed. [TODAY] is AniList's own `TRENDING_DESC` (activity of the last days), [WEEK] / [MONTH] aggregate
 * the per-day `mediaTrends` of the last [days], [ALL_TIME] is the all-time popularity ranking.
 */
enum class TrendWindow(val days: Int) { TODAY(1), WEEK(7), MONTH(30), ALL_TIME(0) }
