// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import androidx.compose.runtime.Composable

/**
 * Hito 6 (D1): tells the platform which title's Details page is on screen ([title] null while it loads). Desktop: Discord's
 * "Viendo la ficha de <título>" (`NowViewing`). Android: nothing.
 */
@Composable
internal expect fun DetailsPresence(title: String?)
