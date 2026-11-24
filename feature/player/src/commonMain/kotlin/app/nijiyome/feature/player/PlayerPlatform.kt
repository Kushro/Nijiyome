// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.font.FontFamily
import app.nijiyome.model.VideoDecodeLimits
import app.nijiyome.player.CodecCaps
import app.nijiyome.player.SubtitleTranslator
import app.nijiyome.player.TranslationCache
import app.nijiyome.player.TranslatorConfig
import org.koin.core.Koin
import java.io.File

// Milestone 3: what the shared pieces of the player module (source picker, subtitle style editor, PlayerSettingsExtras, end screen)
// need from the platform. Android: the same calls the screens made before; desktop: see the PENDIENTE-ESCRITORIO notes.

/** What this device's video decoders can do (the "compatible with this device" filter of the source picker). */
internal expect fun deviceCodecCaps(): CodecCaps

// Milestone 3: what the shared PlayerViewModel needs from the platform (Android: the calls it made before the move).

/** Monotonic clock in milliseconds (sleep timer, PC subtitle waits). Android: `SystemClock.elapsedRealtime()`. */
internal expect fun monotonicNowMs(): Long

/** Informational log line under [tag] (the Link profile changes). Android: `android.util.Log.i` (logcat). */
internal expect fun playerLogInfo(tag: String, message: String)

/** What the media notification / lock screen shows for the current episode. Android: `ActivePlayback.setMetadata`. */
internal expect fun publishPlaybackMetadata(title: String, subtitle: String?, artworkUri: String?)

/** BUG-132: what this device's H.264 decoder plays (Link profiles); null = unknown. Android: `AvcDecodeLimits.device()`. */
internal expect fun deviceAvcDecodeLimits(): VideoDecodeLimits?

/** A subtitle file the user picked ("Load file…"). Android: `android.net.Uri` (SAF document). */
expect abstract class SubtitleDocument {
    /** `Uri` declares it abstract. */
    abstract override fun toString(): String
}

/** [app.nijiyome.player.PlayerKeymap] key name of a pressed key, or null when the platform cannot name it. */
internal expect fun playerKeyName(e: KeyEvent): String?

/** Whether the end screen can blur the poster behind it (Android 12+); otherwise it only dims it. */
internal expect val posterBlurSupported: Boolean

/** Compose family of an imported TTF / OTF file (throws when the file is not a font). */
internal expect fun fontFamilyOf(file: File): FontFamily

/**
 * The subtitle files / fonts / translation helpers of the player (`SubtitleTools` of :core:player on Android) as the settings rows and
 * the style editor use them.
 */
internal interface SubtitleToolsAccess {
    val translationCache: TranslationCache

    fun translator(config: TranslatorConfig): SubtitleTranslator

    /** The imported subtitle fonts. */
    fun fonts(): List<File>

    /** The imported font [name], null when it is not there. */
    fun fontFile(name: String?): File?

    fun deleteFont(name: String)

    /** Hito 6: the font families installed on the system the user can choose (desktop); empty where they are not offered. */
    fun installedFamilies(): List<String> = emptyList()
}

/** Hito 6: the Compose family of an installed system font [name] (desktop); null where installed fonts are not offered (Android). */
internal expect fun installedFontFamily(name: String): FontFamily?

/** null where there are no subtitle tools (the rows that need them are then hidden or disabled, as before when Koin had none). */
internal expect fun Koin.subtitleToolsAccess(): SubtitleToolsAccess?

/**
 * "Importar fuentes…": returns the function that opens the platform's file picker for fonts and copies the chosen ones into the
 * imported fonts; [onDone] gets whether any of them failed.
 */
@Composable
internal expect fun rememberSubtitleFontImporter(onDone: (failed: Boolean) -> Unit): () -> Unit
