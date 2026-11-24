// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import app.nijiyome.ui.platform.JsonPrefsFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileDetailsPrefsTest {
    private val file = Files.createTempDirectory("details").toFile().apply { deleteOnExit() }.resolve("details.json")

    @Test
    fun adultDecisionsAndEpisodeViewSurviveARestart() {
        val first = FileDetailsPrefs(JsonPrefsFile(file))
        assertFalse(first.adultAccepted(42))
        assertEquals(EpisodeView.LIST, first.episodeView())
        first.acceptAdult(42)
        first.acceptAdult(7)
        first.setEpisodeView(EpisodeView.COMPACT)
        val again = FileDetailsPrefs(JsonPrefsFile(file))
        assertTrue(again.adultAccepted(42))
        assertTrue(again.adultAccepted(7))
        assertFalse(again.adultAccepted(8))
        assertEquals(EpisodeView.COMPACT, again.episodeView())
    }
}
