// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The readers' settings sheets (manga and novel) in a wide window: the side sheet already scrolls, so their body must not scroll again
 * (that threw "measured with an infinity maximum height" and closed the app on desktop and on tablets in landscape).
 */
class AdaptiveSheetScrollTest {
    private fun render(widthPx: Int, body: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = widthPx, height = 800, density = Density(1f)) {
            AdaptiveSheet(onDismissRequest = {}) { body() }
        }
        try {
            // a few frames: the dialog layer of the side sheet is laid out after the first one
            repeat(3) { frame -> scene.render(frame * 16_000_000L) }
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun TallRows() = repeat(80) { Text("Row $it") }

    @Test
    fun theReaderSheetBodyOpensWide() {
        render(1280) { AdaptiveSheetScrollColumn { TallRows() } }
    }

    @Test
    fun theReaderSheetBodyOpensCompact() {
        render(400) { AdaptiveSheetScrollColumn { TallRows() } }
    }

    @Test
    fun theOldBodyFailedWide() {
        // What MangaReaderScreen / NovelReaderScreen had: proves the wide render above really exercises the side sheet.
        assertFailsWith<IllegalStateException> {
            render(1280) { Column(Modifier.verticalScroll(rememberScrollState())) { TallRows() } }
        }
    }
}
