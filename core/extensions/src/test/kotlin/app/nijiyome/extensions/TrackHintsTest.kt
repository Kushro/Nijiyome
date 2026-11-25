// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions

import kotlin.test.Test
import kotlin.test.assertTrue

class TrackHintsTest {
    @Test
    fun parsesNyaaDualAudioMultiSubAndDubTags() {
        val trix = Candidates.parseTrackHints(
            releaseName = "[Trix] The Laid-Off Cheat-Granting Mage Enjoys a Second Lease on Life S01E08 [WEBRip 1080p AV1 Opus] (Dual Audio, Multi-Subs)",
            note = null,
            label = "Trix",
            existingSubs = emptyList(),
            existingDubs = listOf("ja"),
            explicitDualAudio = false,
            explicitIsDub = false,
        )
        assertTrue(trix.dualAudio)
        assertTrue(trix.multiSub)
        assertTrue("ja" in trix.dubs && "en" in trix.dubs)

        val yameii = Candidates.parseTrackHints(
            releaseName = "[Yameii] The Laid-Off Cheat-Granting Mage Enjoys a Second Lease on Life - S01E08 [English Dub] [CR WEB-DL 1080p H264]",
            note = null,
            label = "Yameii",
            existingSubs = emptyList(),
            existingDubs = emptyList(),
            explicitDualAudio = false,
            explicitIsDub = false,
        )
        assertTrue("en" in yameii.dubs)

        val multiDub = Candidates.parseTrackHints(
            releaseName = "[Group] Show S01E01 [Multi-Dub] [JA+EN+ES] [EN/ES/PT/FR]",
            note = null,
            label = "Group",
            existingSubs = emptyList(),
            existingDubs = emptyList(),
            explicitDualAudio = false,
            explicitIsDub = false,
        )
        assertTrue(multiDub.dualAudio)
        assertTrue(multiDub.multiSub)
        assertTrue(multiDub.dubs.containsAll(listOf("ja", "en", "es")))
        assertTrue(multiDub.subs.containsAll(listOf("en", "es", "pt", "fr")))

        val lat = Candidates.parseTrackHints(
            releaseName = null,
            note = "Audio Latino · Sub Esp",
            label = "Server 1",
            existingSubs = emptyList(),
            existingDubs = emptyList(),
            explicitDualAudio = false,
            explicitIsDub = false,
        )
        assertTrue("es-419" in lat.dubs)
        assertTrue("es" in lat.subs)
    }
}
