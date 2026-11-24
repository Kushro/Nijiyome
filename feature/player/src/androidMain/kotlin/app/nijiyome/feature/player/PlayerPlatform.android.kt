// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.nijiyome.link.AvcDecodeLimits
import app.nijiyome.model.VideoDecodeLimits
import app.nijiyome.player.ActivePlayback
import app.nijiyome.player.CodecCaps
import app.nijiyome.player.PlaybackMetadata
import app.nijiyome.player.DeviceCodecs
import app.nijiyome.player.SubtitleTools
import app.nijiyome.player.SubtitleTranslator
import app.nijiyome.player.TranslationCache
import app.nijiyome.player.TranslatorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.core.Koin
import java.io.File

internal actual fun deviceCodecCaps(): CodecCaps = DeviceCodecs.capabilities()

internal actual fun monotonicNowMs(): Long = SystemClock.elapsedRealtime()

internal actual fun playerLogInfo(tag: String, message: String) {
    android.util.Log.i(tag, message)
}

internal actual fun publishPlaybackMetadata(title: String, subtitle: String?, artworkUri: String?) {
    ActivePlayback.setMetadata(PlaybackMetadata(title, subtitle, artworkUri))
}

internal actual fun deviceAvcDecodeLimits(): VideoDecodeLimits? = AvcDecodeLimits.device()

actual typealias SubtitleDocument = Uri

internal actual fun playerKeyName(e: KeyEvent): String? = keyNameOf(e.nativeKeyEvent.keyCode)

internal actual val posterBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal actual fun fontFamilyOf(file: File): FontFamily = FontFamily(Font(file))

private class AndroidSubtitleToolsAccess(private val tools: SubtitleTools) : SubtitleToolsAccess {
    override val translationCache: TranslationCache get() = tools.translationCache
    override fun translator(config: TranslatorConfig): SubtitleTranslator = tools.translator(config)
    override fun fonts(): List<File> = tools.fonts.list()
    override fun fontFile(name: String?): File? = tools.fonts.file(name)
    override fun deleteFont(name: String) {
        tools.fonts.delete(name)
    }
}

/** Android offers no installed system fonts for the subtitles (only the bundled and the imported ones). */
internal actual fun installedFontFamily(name: String): FontFamily? = null

internal actual fun Koin.subtitleToolsAccess(): SubtitleToolsAccess? = getOrNull<SubtitleTools>()?.let(::AndroidSubtitleToolsAccess)

/** OpenMultipleDocuments, each chosen file copied into `filesDir/fonts` (SubtitleFonts.import), on the IO dispatcher. */
@Composable
internal actual fun rememberSubtitleFontImporter(onDone: (failed: Boolean) -> Unit): () -> Unit {
    val koin = getKoin()
    val tools = remember { koin.getOrNull<SubtitleTools>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val t = tools ?: return@rememberLauncherForActivityResult
        scope.launch {
            var failed = false
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val name = t.displayName(uri) ?: "font.ttf"
                    val stored = runCatching { context.contentResolver.openInputStream(uri)?.let { t.fonts.import(name, it) } }.getOrNull()
                    if (stored == null) failed = true
                }
            }
            done(failed)
        }
    }
    return { importer.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) }
}
