// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable

/** The runtime permission to post notifications. Android 13+: POST_NOTIFICATIONS. Desktop: always granted. */
interface NotificationPermission {
    /** true when a notification can be posted right now. */
    fun isGranted(): Boolean

    /** Asks the user. The answer goes to the `onResult` given to [rememberNotificationPermission]. */
    fun request()
}

/**
 * false where the app cannot post a notification at all, whatever the permission says: reminder switches are then hidden or
 * disabled instead of pretending to work.
 */
expect val platformPostsNotifications: Boolean

/** Never ask at app start: only when the user turns on something that notifies. */
@Composable
expect fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): NotificationPermission
