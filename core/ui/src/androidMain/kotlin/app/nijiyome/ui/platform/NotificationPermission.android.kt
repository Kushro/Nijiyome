// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberNotificationPermission(onResult: (Boolean) -> Unit): NotificationPermission {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> callback.value(granted) }
    return remember(context, launcher) {
        object : NotificationPermission {
            override fun isGranted(): Boolean =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            override fun request() {
                if (isGranted()) callback.value(true) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

actual val platformPostsNotifications: Boolean = true
