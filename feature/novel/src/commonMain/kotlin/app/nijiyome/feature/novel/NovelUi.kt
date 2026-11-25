// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import app.nijiyome.feature.novel.resources.Res
import app.nijiyome.feature.novel.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import app.nijiyome.model.NovelReaderSettings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.model.AppError
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaSort
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.media.COVER_ASPECT_RATIO
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.state.userMessage
import java.io.File
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

@Composable
internal fun NovelError(cause: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    ErrorState(message = cause.userMessage(), retryLabel = stringResource(UiR.string.ui_retry), onRetry = onRetry, modifier = modifier, title = stringResource(UiR.string.ui_error_title))
}

@Composable
internal fun <T> NovelFilterDropdown(label: String, value: String?, options: List<T>, optionLabel: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = value != null, onClick = { open = true }, label = { Text(if (value != null) "$label: $value" else label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = {
                    open = false
                    onSelect(o)
                })
            }
        }
    }
}

@Composable
internal fun novelStatusLabel(status: String?): String? = when (status?.uppercase()) {
    "RELEASING" -> stringResource(Res.string.novel_status_releasing)
    "FINISHED" -> stringResource(Res.string.novel_status_finished)
    "HIATUS" -> stringResource(Res.string.novel_status_hiatus)
    "CANCELLED" -> stringResource(Res.string.novel_status_cancelled)
    "NOT_YET_RELEASED" -> stringResource(Res.string.novel_status_upcoming)
    null -> null
    else -> status
}

@Composable
internal fun novelOriginLabel(origin: String?): String? = when (origin?.uppercase()) {
    "JP" -> stringResource(Res.string.novel_origin_jp)
    "KR" -> stringResource(Res.string.novel_origin_kr)
    "CN" -> stringResource(Res.string.novel_origin_cn)
    null -> null
    else -> origin
}

@Composable
internal fun novelSortLabel(sort: MediaSort): String = stringResource(
    when (sort) {
        MediaSort.POPULARITY -> Res.string.novel_sort_popularity
        MediaSort.TRENDING -> Res.string.novel_sort_trending
        MediaSort.SCORE -> Res.string.novel_sort_score
        MediaSort.START_DATE -> Res.string.novel_sort_newest
        MediaSort.TITLE -> Res.string.novel_sort_title
        MediaSort.FAVOURITES -> Res.string.novel_sort_favourites
        MediaSort.EPISODES -> Res.string.novel_sort_chapters
        MediaSort.RELEVANCE -> Res.string.novel_sort_relevance
    },
)

// ---- typography (checklist novel.reader.typography / novel.reader.custom-font) ----

internal data class NovelPalette(val background: Color, val text: Color, val highlight: Color)

/** Reader colours: `theme` follows the app theme; sepia / black / white / gray are fixed reading palettes. */
@Composable
internal fun novelPalette(background: String): NovelPalette = when (background) {
    "sepia" -> NovelPalette(Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0x33B5835A))
    "black" -> NovelPalette(Color.Black, Color(0xFFDDDDDD), Color(0x33FFFFFF))
    "white" -> NovelPalette(Color.White, Color(0xFF1B1B1B), Color(0x22000000))
    "gray" -> NovelPalette(Color(0xFF2E2E2E), Color(0xFFE2E2E2), Color(0x33FFFFFF))
    else -> NovelPalette(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
}

/**
 * The font of a preset, port of the classic CSS stacks: Georgia and Lora fall back to the system serif (Android ships neither), the
 * dyslexia preset uses the sans family with wider letter spacing ([dyslexicSpacing]); `custom` loads the imported TTF / OTF.
 * [installed] finds a font family installed on the computer by name (desktop, Tatakai Next's stacks `Georgia, serif`,
 * `Lora, serif`, `OpenDyslexic, sans-serif`); on Android it finds none, so the presets are what they always were there.
 */
internal fun novelFontFamily(font: String, customPath: String?, installed: (String) -> FontFamily? = ::installedFontFamily): FontFamily {
    NovelReaderSettings.installedFamily(font)?.let { name -> return installed(name) ?: FontFamily.Default }
    return when (font) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "georgia" -> installed("Georgia") ?: FontFamily.Serif
        "lora" -> customPath?.takeIf { File(it).name.contains("lora", ignoreCase = true) }
            ?.let { p -> runCatching { FontFamily(fontFromFile(p)) }.getOrNull() } ?: installed("Lora") ?: FontFamily.Serif
        "dyslexic" -> installed("OpenDyslexic") ?: FontFamily.SansSerif
        NovelReaderSettings.FONT_MONO -> FontFamily.Monospace
        "custom" -> customPath?.let { p -> runCatching { FontFamily(fontFromFile(p)) }.getOrNull() } ?: FontFamily.Serif
        else -> FontFamily.Default
    }
}

internal fun dyslexicSpacing(font: String): Float = if (font == "dyslexic") 0.06f else 0f

/** The narration speed as the old `%1$.1f×` string showed it ("1.5" / "1,5" by locale); the string adds the "×". */
internal fun speedText(speed: Float): String = String.format(java.util.Locale.getDefault(), "%.1f", speed)

internal val NOVEL_FONTS: List<String> = listOf("serif", "sans", "georgia", "lora", "dyslexic", "system", "custom")

/** The presets this platform offers: desktop adds `mono` (Tatakai Next's READER_FONTS); an installed family has its own picker. */
internal fun novelFonts(desktop: Boolean = installedFontsOffered): List<String> =
    if (desktop) NOVEL_FONTS.take(5) + NovelReaderSettings.FONT_MONO + NOVEL_FONTS.drop(5) else NOVEL_FONTS

@Composable
internal fun fontLabel(font: String): String = NovelReaderSettings.installedFamily(font) ?: stringResource(
    when (font) {
        NovelReaderSettings.FONT_MONO -> Res.string.novel_font_mono
        "sans" -> Res.string.novel_font_sans
        "georgia" -> Res.string.novel_font_georgia
        "lora" -> Res.string.novel_font_lora
        "dyslexic" -> Res.string.novel_font_dyslexic
        "system" -> Res.string.novel_font_system
        "custom" -> Res.string.novel_font_custom
        else -> Res.string.novel_font_serif
    },
)
