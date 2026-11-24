// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/** System back (gesture / button). Android: `androidx.activity.compose.BackHandler`. Desktop: no system back, does nothing. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
