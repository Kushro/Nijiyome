// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberNotificationPermission(onResult: (Boolean) -> Unit): NotificationPermission {
    val callback = rememberUpdatedState(onResult)
    return remember {
        object : NotificationPermission {
            override fun isGranted(): Boolean = true
            override fun request() = callback.value(true)
        }
    }
}

// Desktop posts system notifications without a permission to ask: torrents and downloads (SystemNotifier), airing reminders
// (core/discover DesktopAiringReminders) and new AniList notifications (core/social DesktopSocialPoller).
actual val platformPostsNotifications: Boolean = true
