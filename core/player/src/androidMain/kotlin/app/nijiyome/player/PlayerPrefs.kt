// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import android.content.Context
import android.content.SharedPreferences
import app.nijiyome.model.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

// parity wave A (docs/redesign/15 §3.2 / §3.5, worker p): player settings that had no field in `AppSettings` were kept PRIVATE to the
// player module in its own SharedPreferences ("nijiyome_player"). 0.4.1: the only ones (the subtitle colours, outline size, position and
// imported font, 0.4.0's `SubtitleExtras`) moved into `AppSettings.subtitleStyle` (DataStore); this class now only migrates them once.
// The pure parts ([LegacySubtitleExtras], [FontNames]) are in commonMain; the SharedPreferences and the font folder stay here.

/**
 * The player's private SharedPreferences. One instance per app (Koin `single(createdAtStart = true)` in `playerModule`): at start it
 * folds 0.4.0's [LegacySubtitleExtras] into `AppSettings.subtitleStyle` (DataStore) and deletes them, so nobody loses their subtitle look.
 */
class PlayerPrefs(
    context: Context,
    settings: SettingsRepository? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val appContext = context.applicationContext

    init {
        if (settings != null) {
            scope.launch {
                val prefs: SharedPreferences = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                val legacy = LegacySubtitleExtras.from(prefs.all) ?: return@launch
                // Deleted only after the DataStore write succeeded: a failure retries on the next start.
                runCatching { settings.update { it.copy(subtitleStyle = legacy.applyTo(it.subtitleStyle)) } }
                    .onSuccess { prefs.edit().apply { LegacySubtitleExtras.KEYS.forEach { k -> remove(k) } }.apply() }
            }
        }
    }

    private companion object {
        const val FILE = "nijiyome_player"
    }
}

/**
 * Fonts the user imports for subtitles (checklist player.subs.custom-fonts): TTF / OTF copied to `filesDir/fonts`. mpv gets the folder as
 * `sub-fonts-dir` (libass also finds the fonts ASS styles ask for there) and, when one is chosen, its family name as `sub-font`; Media3
 * draws with it through Compose `Font(File)`.
 *
 * 0.4.1: the same folder also holds static copies of the app's own fonts for libass (files named [BUNDLED_PREFIX]..., installed by
 * :core:player-mpv); they are not user fonts, so [list] hides them and [import] never writes over them.
 */
class SubtitleFonts(context: Context) {
    val dir: File = File(context.applicationContext.filesDir, "fonts")

    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && isFontName(f.name) && !f.name.startsWith(BUNDLED_PREFIX) }?.sortedBy { it.name.lowercase() }.orEmpty()

    fun file(name: String?): File? = name?.let { File(dir, it) }?.takeIf { it.isFile }

    /** Family name of [name] (from the font's `name` table), or its file name without extension. */
    fun familyName(name: String): String? {
        val f = file(name) ?: return null
        val bytes = runCatching { f.readBytes() }.getOrNull() ?: return null
        return FontNames.family(bytes) ?: f.nameWithoutExtension
    }

    /** Copies [input] as [displayName] (sanitised, `.ttf` / `.otf` only). Returns the stored file name or null when refused. */
    fun import(displayName: String, input: java.io.InputStream): String? {
        val safe = displayName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
            .let { if (it.startsWith(BUNDLED_PREFIX)) "user_$it" else it }
        if (!isFontName(safe)) return null
        dir.mkdirs()
        val target = File(dir, safe)
        input.use { src -> target.outputStream().use { src.copyTo(it) } }
        if (target.length() == 0L || target.length() > MAX_FONT_BYTES) {
            target.delete()
            return null
        }
        return safe
    }

    fun delete(name: String): Boolean = file(name)?.delete() == true

    companion object {
        const val MAX_FONT_BYTES = 30L * 1024 * 1024

        /** File-name prefix of the app's own fonts installed for libass (`nijiyome_outfit_medium.ttf`...). */
        const val BUNDLED_PREFIX = "nijiyome_"

        fun isFontName(name: String): Boolean = name.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") || it.endsWith(".ttc") }
    }
}
