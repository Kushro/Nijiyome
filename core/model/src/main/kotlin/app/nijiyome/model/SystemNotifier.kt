// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

/**
 * A system notification on desktop (torrent finished or failed, episode downloaded). Bound by the desktop app; the desktop torrent
 * host and download engine post through it when it is bound. Android posts through its own notification channels instead.
 */
fun interface SystemNotifier {
    /** Shows [title] / [body]; returns at once and never throws. */
    fun notify(title: String, body: String)
}

/** The few desktop notification texts (English, or Spanish on a Spanish system), shared by the modules that post them. */
object DesktopNoticeTexts {
    private val spanish: Boolean get() = java.util.Locale.getDefault().language == "es"

    fun episodeReady(episode: Int?): String = when {
        episode == null -> if (spanish) "Descarga lista" else "Download ready"
        spanish -> "Episodio $episode listo"
        else -> "Episode $episode ready"
    }

    val torrentProblem: String get() = if (spanish) "Problema con un torrent" else "Torrent problem"

    fun downloaded(title: String, episode: Int): String = if (spanish) "$title · episodio $episode descargado" else "$title · episode $episode downloaded"

    fun downloadFailed(title: String, episode: Int): String = if (spanish) "$title · episodio $episode falló" else "$title · episode $episode failed"
}
