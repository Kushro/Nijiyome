// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import app.nijiyome.ui.platform.JsonPrefsFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.module.Module
import java.io.File

/**
 * Desktop: Details and the character page. Needs metadata, history, extensions and settings; Link, lists, torrents and downloads
 * are optional (what depends on them is hidden).
 */
// "Enviar a dispositivo" on desktop is the PC sending to a paired phone: from the Ficha (its side panel or toolbar menu of the
// paired phones, SendToPhone of :server) and from the player ("Enviar al teléfono", feature/player DesktopLinkRemote). The phone's
// own panel / button (send to the PC) stay hidden here (no LinkRepository).
// The 18+ decisions per title and the episode view are kept in `<dataDir>/details.json`.
fun desktopDetailsModule(dataDir: File): Module = detailsModule { FileDetailsPrefs(JsonPrefsFile(File(dataDir, "details.json"))) }

/** [DetailsPrefs] in a JSON file (Android: the SharedPreferences file `nijiyome_details`), with the same keys. */
internal class FileDetailsPrefs(private val file: JsonPrefsFile) : DetailsPrefs {
    override fun adultAccepted(anilistId: Int): Boolean = anilistId in adult()

    override fun acceptAdult(anilistId: Int) {
        val next = adult() + anilistId
        file.put(KEY_ADULT, JsonArray(next.sorted().map(::JsonPrimitive)))
    }

    override fun episodeView(): EpisodeView =
        runCatching { file.read()[KEY_VIEW]?.jsonPrimitive?.content }.getOrNull()
            ?.let { v -> EpisodeView.entries.firstOrNull { it.name == v } } ?: EpisodeView.LIST

    override fun setEpisodeView(view: EpisodeView) {
        file.put(KEY_VIEW, JsonPrimitive(view.name))
    }

    private fun adult(): Set<Int> =
        (file.read()[KEY_ADULT] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }?.toSet().orEmpty()

    private companion object {
        const val KEY_ADULT = "adult_ok"
        const val KEY_VIEW = "episode_view"
    }
}
