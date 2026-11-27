// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import app.nijiyome.ui.platform.windowHeightDp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.model.Media
import app.nijiyome.ui.media.displayTitle
import app.nijiyome.ui.media.UpscalableImage
import app.nijiyome.upscale.UpscaleImageKind

// Ficha "Cine editorial" (LookPrefs.pageStyle = EDITORIAL, the Cine preset): the sharp fanart bleeds to the edges under a veil that
// ends in the page background, with a large display title and the facts line on it; no poster and no blur. The actions row and the
// source line below are the ones of the standard phone header, so every "Primero el episodio" rule still holds.

/** 330 dp full-bleed fanart, veil to the background, display title (3 lines max) and [facts] (the A / C facts line; null = title only). */
@Composable
internal fun EditorialHeader(
    media: Media,
    state: DetailsUiState,
    actions: DetailsActions,
    facts: (@Composable () -> Unit)? = null,
    /** Share of the window height the hero may take (B / D on shorter phones: 0.36). */
    heightFraction: Float = 0.4f,
    /** B / D (no facts line): the "+18" badge goes under the title. */
    adultBadge: Boolean = false,
) {
    val background = MaterialTheme.colorScheme.background
    val title = media.displayTitle(state.titleLanguage)
    val blurred = state.coverBlurred
    val backdrop = headerBackdrop(media, state.artwork)
    // Short windows (landscape, split screen): at most 40 % of the height.
    val height = minOf(EDITORIAL_HEIGHT, windowHeightDp().dp * heightFraction)
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(height)) {
            UpscalableImage(
                url = backdrop.url,
                contentDescription = null,
                kind = UpscaleImageKind.BACKDROP,
                // a blurred (adult) backdrop is not worth a GPU job
                upscale = !blurred,
                contentScale = ContentScale.Crop,
                alignment = if (backdrop.lowRes && media.banner != null) Alignment.BottomCenter else Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(if (blurred) Modifier.blur(24.dp).clickable(onClick = actions.onRevealCover) else Modifier),
            )
            // The veil is what keeps the title readable (light mode too): a light top for the status bar and back button, clear in
            // the middle, then into the background under the title.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.4f),
                        0.22f to background.copy(alpha = if (backdrop.lowRes) 0.3f else 0f),
                        // dense from 45 %: a 3-line title keeps >= 4.5:1 on any art
                        0.45f to background.copy(alpha = 0.82f),
                        1f to background,
                    ),
                ),
            )
            // at the top (under the status bar and back button), never over the title
            if (blurred) MatureReveal(actions, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        title,
                        style = MaterialTheme.typography.displaySmall.copy(lineBreak = LineBreak.Heading),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                facts?.invoke()
                TitleQuickActions(title = title, media = media, actions = actions, adultBadge = adultBadge)
            }
        }
    }
}

private val EDITORIAL_HEIGHT = 330.dp
