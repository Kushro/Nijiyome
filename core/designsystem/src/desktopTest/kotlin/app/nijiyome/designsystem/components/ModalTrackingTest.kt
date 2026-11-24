// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi::class)

package app.nijiyome.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalBasicAlertDialogOverride
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v1.1, desktop dialogs: with the window's [ModalTracker] (and the material3 dialog override) every AlertDialog and sheet counts as
 * open while it is shown, so the app-wide keys can stay off, and its field takes the focus when it opens. Without a tracker (Android)
 * nothing is counted and no field is focused.
 */
class ModalTrackingTest {
    private var frame = 0L

    private fun ImageComposeScene.frames(n: Int = 4) = repeat(n) { render(frame++ * 16_000_000L) }

    private fun scene(width: Int = 1280, content: @Composable () -> Unit) =
        ImageComposeScene(width = width, height = 800, density = Density(1f), content = content)

    @Composable
    private fun Field(onFocus: (Boolean) -> Unit) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            singleLine = true,
            modifier = Modifier.onFocusChanged { onFocus(it.hasFocus) }.focusOnOpen(),
        )
    }

    @Test
    fun anAlertDialogIsCountedWhileShownAndItsFieldTakesTheFocus() {
        val tracker = ModalTracker()
        var show by mutableStateOf(true)
        var focused = false
        val scene = scene {
            CompositionLocalProvider(LocalModalTracker provides tracker, LocalBasicAlertDialogOverride provides TrackedAlertDialogOverride(tracker)) {
                if (show) {
                    AlertDialog(
                        onDismissRequest = { show = false },
                        title = { Text("Install from a URL or a path") },
                        text = { Field { focused = it } },
                        confirmButton = { TextButton(onClick = {}) { Text("Install") } },
                    )
                }
            }
        }
        try {
            scene.frames()
            assertEquals(1, tracker.openCount)
            assertTrue(focused, "the dialog's field has the focus")
            show = false
            scene.frames()
            assertEquals(0, tracker.openCount)
            assertFalse(tracker.isOpen)
        } finally {
            scene.close()
        }
    }

    @Test
    fun aSideSheetAndABottomSheetAreCountedToo() {
        for (width in listOf(1280, 400)) {
            val tracker = ModalTracker()
            var show by mutableStateOf(true)
            var focused = false
            val scene = scene(width) {
                CompositionLocalProvider(LocalModalTracker provides tracker) {
                    if (show) AdaptiveSheet(onDismissRequest = { show = false }) { Field { focused = it } }
                }
            }
            try {
                scene.frames(6)
                assertEquals(1, tracker.openCount, "width $width")
                assertTrue(focused, "width $width: the sheet's field has the focus")
                show = false
                scene.frames()
                assertEquals(0, tracker.openCount, "width $width")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun withoutATrackerNothingIsCountedOrFocused() {
        var focused = false
        val scene = scene {
            AlertDialog(onDismissRequest = {}, text = { Field { focused = it } }, confirmButton = {})
        }
        try {
            scene.frames()
            assertFalse(focused, "Android: no field is focused when a dialog opens")
        } finally {
            scene.close()
        }
    }
}
