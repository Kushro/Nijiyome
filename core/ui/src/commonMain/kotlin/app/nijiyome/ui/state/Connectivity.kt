// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.state

import androidx.compose.runtime.Composable

/**
 * Android: true while the default network has INTERNET + VALIDATED (ConnectivityManager callback, lifecycle-aware). Drives the
 * offline banner. Desktop: a usable network (a default route on Linux), see `DesktopConnectivity`.
 * The callback lives as long as the calling composition (the app shell), so it follows the activity lifecycle. Any failure to read
 * the connectivity service is treated as online (the banner must never block the app on a platform quirk).
 */
@Composable
expect fun rememberIsOnline(): Boolean
