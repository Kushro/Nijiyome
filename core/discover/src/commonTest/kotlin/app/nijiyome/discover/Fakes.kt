// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.discover

import app.nijiyome.model.AiringEpisode
import app.nijiyome.model.Episode
import app.nijiyome.model.FeedKind
import app.nijiyome.model.FeedPage
import app.nijiyome.model.Media
import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import app.nijiyome.model.MetadataRepository

internal fun media(id: Int, genres: List<String> = listOf("Action"), year: Int? = 2020, score: Int? = 75, title: String = "T$id") = Media(
    ref = MediaRef(anilistId = id, malId = id * 10), kind = MediaKind.ANIME, title = title, titles = emptyMap(), cover = null, banner = null,
    color = null, synopsis = null, genres = genres, episodes = 12, status = "RELEASING", season = null, year = year, score = score,
    sourceProvider = "anilist",
)

internal class FakeMetadata(var schedule: List<AiringEpisode> = emptyList()) : MetadataRepository {
    var scheduleCalls = 0
    override suspend fun feed(kind: MediaKind, feed: FeedKind, page: Int) = FeedPage(emptyList(), page, false)
    override suspend fun search(kind: MediaKind, query: String, page: Int) = FeedPage(emptyList(), page, false)
    override suspend fun media(kind: MediaKind, ref: MediaRef): Media? = null
    override suspend fun episodes(ref: MediaRef): List<Episode> = emptyList()
    override suspend fun airingSchedule(fromEpochSec: Long, toEpochSec: Long, page: Int): List<AiringEpisode> {
        scheduleCalls++
        return if (page == 1) schedule.filter { it.airingAtEpochSec in fromEpochSec until toEpochSec } else emptyList()
    }
}
