// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.components

import androidx.compose.material3.BasicAlertDialogOverride
import androidx.compose.material3.BasicAlertDialogOverrideScope
import androidx.compose.material3.DefaultBasicAlertDialogOverride
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Every material3 `AlertDialog` / `BasicAlertDialog` goes through this override (desktop provides it with
 * `LocalBasicAlertDialogOverride`): the dialog is drawn as material3 draws it and counted by [tracker] while it is shown.
 */
@OptIn(ExperimentalMaterial3ComponentOverrideApi::class)
class TrackedAlertDialogOverride(private val tracker: ModalTracker) : BasicAlertDialogOverride {
    @Composable
    override fun BasicAlertDialogOverrideScope.BasicAlertDialog() {
        DisposableEffect(tracker) {
            tracker.opened()
            onDispose { tracker.closed() }
        }
        with(DefaultBasicAlertDialogOverride) { BasicAlertDialog() }
    }
}
