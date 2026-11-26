// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.framegen

import app.nijiyome.player.mpv.external.DesktopPlayerPrefs
import app.nijiyome.player.mpv.external.DesktopPlayerPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFrameGenTest {
    @Test
    fun findLosslessDllPrefersSiblingLosslessOverLsfgVkDll() {
        val dir = Files.createTempDirectory("fg-dll-test").toFile()
        try {
            val lsfgVk = File(dir, "lsfg-vk.dll").apply { writeText("stub") }
            val lossless = File(dir, "Lossless.dll").apply { writeText("stub") }
            val found = DesktopFrameGenNative.findLosslessDll(
                configured = lsfgVk.absolutePath,
                env = emptyMap(),
                home = dir,
                windows = false,
            )
            assertEquals(lossless.absolutePath, found?.absolutePath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun findLosslessDllFindsSteamInstallOnLinuxAndWindows() {
        val home = Files.createTempDirectory("fg-home-test").toFile()
        try {
            val linuxSteam = File(home, ".local/share/Steam/steamapps/common/Lossless Scaling/Lossless.dll")
            linuxSteam.parentFile.mkdirs()
            linuxSteam.writeText("stub")
            val foundLinux = DesktopFrameGenNative.findLosslessDll(null, emptyMap(), home, windows = false)
            assertEquals(linuxSteam.absolutePath, foundLinux?.absolutePath)

            val pf = File(home, "ProgramFilesX86")
            val winSteam = File(pf, "Steam/steamapps/common/Lossless Scaling/Lossless.dll")
            winSteam.parentFile.mkdirs()
            winSteam.writeText("stub")
            val foundWin = DesktopFrameGenNative.findLosslessDll(
                configured = null,
                env = mapOf("ProgramFiles(x86)" to pf.absolutePath),
                home = home,
                windows = true,
            )
            assertEquals(winSteam.absolutePath, foundWin?.absolutePath)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun prefsRoundTripIncludesAntiArtifactsAndAudioOffset() {
        val dir = Files.createTempDirectory("fg-prefs-test").toFile()
        try {
            val file = File(dir, "desktop-player.json")
            val store = DesktopPlayerPrefsStore(file)
            store.update {
                it.copy(
                    frameGen = true,
                    frameGenMultiplier = 4,
                    frameGenPerformance = true,
                    frameGenAntiArtifacts = false,
                    frameGenAudioOffsetMs = 999,
                )
            }
            val reloaded = DesktopPlayerPrefsStore(file).prefs.value
            assertTrue(reloaded.frameGen)
            assertEquals(4, reloaded.frameGenMultiplier)
            assertTrue(reloaded.frameGenPerformance)
            assertFalse(reloaded.frameGenAntiArtifacts)
            assertEquals(500, reloaded.frameGenAudioOffsetMs) // clamped
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun controllerPersistsChangesToStoreAndStartsCleanlyWithoutNativeCrash() {
        val dir = Files.createTempDirectory("fg-ctrl-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = DesktopPlayerPrefsStore(File(dir, "desktop-player.json"))
            var audioDelay = 0.0
            val ctrl = DesktopFrameGenController(
                prefsStore = store,
                scope = scope,
                readAudioDelay = { audioDelay },
                writeAudioDelay = { audioDelay = it },
                onActiveChanged = {},
                refreshHz = { 180f },
            )
            ctrl.setEnabled(true)
            ctrl.setMultiplier(3)
            ctrl.setPerformance(true)
            ctrl.setAntiArtifacts(false)
            assertEquals(true, store.prefs.value.frameGen)
            assertEquals(3, store.prefs.value.frameGenMultiplier)
            assertEquals(true, store.prefs.value.frameGenPerformance)
            assertEquals(false, store.prefs.value.frameGenAntiArtifacts)
            assertEquals(3, ctrl.state.value.multiplier)
            assertNull(DesktopFrameGenNative.findLosslessDll(File(dir, "missing.dll").absolutePath, emptyMap(), dir, false))
            ctrl.release()
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    @Test
    fun nativeLibraryProbeWorksWhenBuiltLocally() {
        if (!DesktopFrameGenNative.ensureLoaded()) return
        val probe = DesktopFrameGenNative.probe(null)
        assertNotNull(probe)
    }
}
