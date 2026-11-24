// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import android.content.Context

/** Android: [DetailsPrefs] in the private SharedPreferences file `nijiyome_details`. */
class SharedPrefsDetailsPrefs(context: Context) : DetailsPrefs {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun adultAccepted(anilistId: Int): Boolean =
        prefs.getStringSet(KEY_ADULT, emptySet()).orEmpty().contains(anilistId.toString())

    override fun acceptAdult(anilistId: Int) {
        val next = prefs.getStringSet(KEY_ADULT, emptySet()).orEmpty().toMutableSet().apply { add(anilistId.toString()) }
        prefs.edit().putStringSet(KEY_ADULT, next).apply()
    }

    override fun episodeView(): EpisodeView =
        prefs.getString(KEY_VIEW, null)?.let { v -> EpisodeView.entries.firstOrNull { it.name == v } } ?: EpisodeView.LIST

    override fun setEpisodeView(view: EpisodeView) {
        prefs.edit().putString(KEY_VIEW, view.name).apply()
    }

    private companion object {
        const val FILE = "nijiyome_details"
        const val KEY_ADULT = "adult_ok"
        const val KEY_VIEW = "episode_view"
    }
}
