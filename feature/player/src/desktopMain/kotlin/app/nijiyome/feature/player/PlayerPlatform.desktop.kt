// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import app.nijiyome.model.VideoDecodeLimits
import app.nijiyome.player.CodecCaps
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.platform.SystemFont
import app.nijiyome.player.DesktopSubtitleTools
import app.nijiyome.player.EmbeddedSubtitleReader
import app.nijiyome.player.EmbeddedTrackRef
import app.nijiyome.player.MediaByteSource
import app.nijiyome.player.PlayerSubtitleTools
import app.nijiyome.player.SubtitleTranslator
import app.nijiyome.player.TimedCue
import app.nijiyome.player.TranslationCache
import app.nijiyome.player.TranslatorConfig
import app.nijiyome.ui.platform.androidKeyName
import app.nijiyome.ui.platform.rememberFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.getKoin
import org.koin.core.Koin
import java.awt.GraphicsEnvironment
import java.io.File

/**
 * v1.1 (F2): what the embedded libmpv decodes here: software from its FFmpeg (`decoder-list`), hardware from VAAPI (through the
 * libva the player loads) or NVDEC; see DesktopVideoDecoding.codecCaps. Windows: everything counts as compatible (not probed).
 */
internal actual fun deviceCodecCaps(): CodecCaps = app.nijiyome.player.mpv.DesktopVideoDecoding.codecCaps()

internal actual fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000

/** Desktop has no logcat: the player's informational lines go to stderr, like the mpv engines' (MpvLog.desktop.kt). */
internal actual fun playerLogInfo(tag: String, message: String) {
    System.err.println("I/$tag: $message")
}

/** The episode playing, for Discord Rich Presence and the system media controls (both read [NowPlaying]). */
internal actual fun publishPlaybackMetadata(title: String, subtitle: String?, artworkUri: String?) {
    val art = artworkUri?.takeIf { it.startsWith("https://") }
    app.nijiyome.model.NowPlaying.update { now ->
        now?.copy(title = title, subtitle = subtitle, artworkUrl = art) ?: app.nijiyome.model.NowPlayingInfo(title, subtitle, art)
    }
    // v1.1 (F2): MPRIS reads it too (mpris/DesktopMediaControls.kt); Windows' SMTC is marked in mpris/Mpris.kt.
}

// Desktop (by design): desktop H.264 decoder limits for the Link profiles; desktop is not a Link client, so nothing is filtered
internal actual fun deviceAvcDecodeLimits(): VideoDecodeLimits? = null

/** A subtitle file the user picked ("Load file…"): on desktop, a file ([SubtitleFile]). */
actual abstract class SubtitleDocument {
    actual abstract override fun toString(): String
}

/** The desktop [SubtitleDocument]: a file on disk, picked with the desktop file chooser. */
internal class SubtitleFile(val file: File) : SubtitleDocument() {
    override fun toString(): String = file.path
}

/** The shared [PlayerViewModel]'s subtitle tools over :core:player's [DesktopSubtitleTools] (whose documents are files). */
internal class DesktopDocumentSubtitleTools(private val tools: DesktopSubtitleTools) : PlayerSubtitleTools<SubtitleDocument> {
    override val translationCache: TranslationCache get() = tools.translationCache

    override suspend fun fetch(url: String, headers: Map<String, String>, format: String?, via: OkHttpClient?): List<TimedCue> =
        tools.fetch(url, headers, format, via)

    override suspend fun import(uri: SubtitleDocument): Pair<File, List<TimedCue>> =
        tools.import((uri as? SubtitleFile)?.file ?: File(uri.toString()))

    override fun translator(config: TranslatorConfig): SubtitleTranslator = tools.translator(config)

    override fun mediaSource(
        url: String,
        headers: Map<String, String>,
        torrentRanges: (() -> List<ClosedFloatingPointRange<Double>>)?,
    ): MediaByteSource? = tools.mediaSource(url, headers, torrentRanges)

    override suspend fun hlsSubtitles(masterUrl: String, headers: Map<String, String>, want: EmbeddedTrackRef): List<TimedCue>? =
        tools.hlsSubtitles(masterUrl, headers, want)

    override fun embeddedReader(media: MediaByteSource, playheadMs: () -> Long): EmbeddedSubtitleReader = tools.embeddedReader(media, playheadMs)
}

/** Milestone 4: the key's [playerKeyName] (Android's `KeyEvent.keyCodeToString` name without `KEYCODE_`). */
internal actual fun playerKeyName(e: KeyEvent): String? = playerKeyName(e.key)

/**
 * The [app.nijiyome.player.PlayerKeymap] name of a Compose [Key], the same Android gives that key (`SPACE`, `DPAD_LEFT`, `F`, `0`,
 * `SLASH`), so a keymap saved on one platform means the same keys on the other; null for keys without one here.
 */
internal fun playerKeyName(key: Key): String? = androidKeyName(key)

internal actual val posterBlurSupported: Boolean = true

internal actual fun fontFamilyOf(file: File): FontFamily = FontFamily(Font(file))

private class DesktopSubtitleToolsAccess(private val tools: DesktopSubtitleTools) : SubtitleToolsAccess {
    override val translationCache: TranslationCache get() = tools.translationCache
    override fun translator(config: TranslatorConfig): SubtitleTranslator = tools.translator(config)
    override fun fonts(): List<File> = tools.fonts.list()
    override fun fontFile(name: String?): File? = tools.fonts.file(name)
    override fun deleteFont(name: String) {
        tools.fonts.delete(name)
    }
    override fun installedFamilies(): List<String> = InstalledFonts.families()
}

internal actual fun Koin.subtitleToolsAccess(): SubtitleToolsAccess? = getOrNull<DesktopSubtitleTools>()?.let(::DesktopSubtitleToolsAccess)

/** The desktop file chooser (several fonts at once), each chosen file copied into the imported fonts, off the Swing thread. */
@Composable
internal actual fun rememberSubtitleFontImporter(onDone: (failed: Boolean) -> Unit): () -> Unit {
    val koin = getKoin()
    val tools = remember { koin.getOrNull<DesktopSubtitleTools>() }
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    return rememberFilePicker(FONT_MIME_TYPES, multiple = true) { paths ->
        val t = tools ?: return@rememberFilePicker
        if (paths.isEmpty()) return@rememberFilePicker
        scope.launch {
            val failed = withContext(Dispatchers.IO) { paths.count { p -> runCatching { t.fonts.import(File(p)) }.getOrNull() == null } > 0 }
            done(failed)
        }
    }
}

/** A system font family for the screen-drawn subtitles and the previews (Skia finds it by name). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
internal actual fun installedFontFamily(name: String): FontFamily? = runCatching { FontFamily(SystemFont(name)) }.getOrNull()

/** The font families installed on the system (AWT's list without its logical fonts), read once. */
internal object InstalledFonts {
    private val cached: List<String> by lazy {
        if (GraphicsEnvironment.isHeadless()) {
            emptyList()
        } else {
            runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList() }.getOrDefault(emptyList())
                .let(::cleaned)
        }
    }

    fun families(): List<String> = cached

    /** Drops Java's logical fonts (Dialog, Monospaced...) and duplicates, sorted without case. */
    internal fun cleaned(names: List<String>): List<String> =
        names.map { it.trim() }.filter { it.isNotEmpty() && it !in LOGICAL }.distinct().sortedBy { it.lowercase() }

    private val LOGICAL = setOf("Dialog", "DialogInput", "Monospaced", "SansSerif", "Serif")
}

private val FONT_MIME_TYPES = listOf("font/ttf", "font/otf")
