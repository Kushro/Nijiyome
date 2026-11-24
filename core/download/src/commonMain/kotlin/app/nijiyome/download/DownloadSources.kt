// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import app.nijiyome.model.Container
import app.nijiyome.model.DownloadProfile
import app.nijiyome.model.EmbedSniffer
import app.nijiyome.model.ExtensionRepository
import app.nijiyome.model.Media
import app.nijiyome.model.SourceCandidate
import app.nijiyome.model.StreamSource

/**
 * Best downloadable stream of an episode for [profile] (embed pages are sniffed first; DASH skipped), with the label of its source.
 * Shared by the Android (Media3) and desktop download engines. [sniffer] is read for each embed candidate (it may be bound later).
 */
internal suspend fun resolveDownloadStream(
    extensions: ExtensionRepository,
    sniffer: () -> EmbedSniffer?,
    media: Media,
    episode: Int,
    server: String?,
    lang: String?,
    profile: DownloadProfile,
): Pair<StreamSource, String>? {
    val ranked = runCatching { extensions.resolveCandidates(media, episode) }.getOrDefault(emptyList())
    for (r in DownloadLogic.candidatesFor(ranked, profile, server, lang)) {
        val c: SourceCandidate = r.candidate
        val resolved: StreamSource? = if (c.isEmbed) {
            val w = c.web ?: continue
            val sn = sniffer() ?: continue
            runCatching { sn.sniff(w.url, w.headers, c.extId).toStream(c) }.getOrNull()
        } else {
            c.toStream()
        }
        val stream = resolved ?: continue
        if (stream.container == Container.DASH) continue
        return stream to c.label
    }
    // No ranked candidate: the plain streams of the extensions (wave android path).
    val plain = runCatching { extensions.resolveStreams(media, episode) }.getOrDefault(emptyList())
    return plain.firstOrNull { it.container != Container.DASH }?.let { it to it.provider }
}
