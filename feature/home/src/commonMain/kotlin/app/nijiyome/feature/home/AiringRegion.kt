// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.regionContentColor

/**
 * Content of an airing region (`Modifier.decor(DecorRegion.AIRING)`): text drawn straight on the region takes the color the direction
 * gives it, since a direction may turn the region into a dark island on a light theme (a departure board). Cards and rows on their
 * own `Surface` keep their colors.
 */
@Composable
internal fun AiringContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides regionContentColor(DecorRegion.AIRING), content = content)
}

/**
 * [normal], unless the airing region around it is a dark island on a light theme (its content color is light while the theme's
 * surface is light); then [island]. Lets a fixed role (secondary text, the "now" line) stay readable on that island.
 */
@Composable
internal fun onAiring(normal: Color, island: Color): Color {
    val content = LocalContentColor.current
    return if (content.luminance() > 0.5f && MaterialTheme.colorScheme.surface.luminance() > 0.5f) island else normal
}

/** Secondary text drawn straight on an airing region. */
@Composable
internal fun airingSecondaryText(): Color =
    onAiring(MaterialTheme.colorScheme.onSurfaceVariant, LocalContentColor.current.copy(alpha = 0.8f))
