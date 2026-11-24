// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.PlayerVolume
import app.nijiyome.player.mpv.external.DesktopPlayerPrefs
import app.nijiyome.player.mpv.external.DesktopPlayerPrefsStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Hito 6: installed / imported subtitle fonts for libass, and the volume kept across runs. */
class DesktopPlayerParityTest {
    private val root: File = Files.createTempDirectory("mpvparity").toFile().apply { deleteOnExit() }

    @Test
    fun subtitleFamilyOfInstalledAndImportedFonts() {
        assertEquals("Noto Sans", desktopSubtitleFamily("family:Noto Sans", null))
        assertEquals("Noto Sans", desktopSubtitleFamily("family:Noto Sans", root))
        // an imported file that is not a real font: its file name stands for the family
        File(root, "Custom.ttf").writeBytes(ByteArray(16))
        assertEquals("Custom", desktopSubtitleFamily("Custom.ttf", root))
        assertNull(desktopSubtitleFamily("Gone.ttf", root))
        assertNull(desktopSubtitleFamily(null, root))
        assertNull(desktopSubtitleFamily("Custom.ttf", null))
    }

    @Test
    fun volumeIsKeptInTheSettingsFile() {
        val file = File(root, "desktop-player.json")
        val memory = PlayerVolumeMemory(DesktopPlayerPrefsStore(file))
        assertNull(memory.last())
        memory.remember(PlayerVolume(40, muted = true))
        // a new run of the app reads it back
        val again = PlayerVolumeMemory(DesktopPlayerPrefsStore(file))
        assertEquals(PlayerVolume(40, muted = true), again.last())
        // and the other settings of the file are not touched
        assertEquals(DesktopPlayerPrefs(volume = 40, muted = true), DesktopPlayerPrefs.fromJson(file.readText()))
    }

    @Test
    fun volumeWithoutStoreLastsForTheRunAndIsClamped() {
        val memory = PlayerVolumeMemory()
        memory.remember(PlayerVolume(150, muted = false))
        assertEquals(PlayerVolume(100, muted = false), memory.last())
        assertEquals(100, DesktopPlayerPrefs.fromJson("""{"volume":300}""").volume)
    }
}
