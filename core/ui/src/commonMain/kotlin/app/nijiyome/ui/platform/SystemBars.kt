// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/**
 * Immersive mode: [hidden] hides the system bars (a swipe shows them for a moment); they come back when false and on leaving.
 * Android: `WindowInsetsControllerCompat`. Desktop: does nothing (no system bars).
 */
@Composable
expect fun SystemBarsHidden(hidden: Boolean)

/**
 * A reader's fullscreen for the whole window. Desktop: the window goes fullscreen while [fullscreen] and back on leaving. Android:
 * does nothing (the readers' fullscreen there is [SystemBarsHidden]).
 */
@Composable
expect fun ReaderWindowFullscreen(fullscreen: Boolean)
