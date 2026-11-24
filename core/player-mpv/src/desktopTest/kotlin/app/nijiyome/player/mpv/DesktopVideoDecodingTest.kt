// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.mpv.SystemLibva.VaVersion
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopVideoDecodingTest {
    private fun folder(vararg libs: String, version: String = "2400"): File {
        val dir = Files.createTempDirectory("libva").toFile().apply { deleteOnExit() }
        for (name in libs) {
            val real = File(dir, "$name.$version.0").apply { writeBytes(byteArrayOf(0)) }
            Files.createSymbolicLink(File(dir, name).toPath(), real.toPath())
        }
        return dir
    }

    @Test
    fun `version comes from the real file name`() {
        val dir = folder("libva.so.2", version = "2400")
        assertEquals(VaVersion(1, 24), SystemLibva.versionOf(File(dir, "libva.so.2")))
        val debian = folder("libva.so.2", version = "2000")
        assertEquals(VaVersion(1, 20), SystemLibva.versionOf(File(debian, "libva.so.2")))
    }

    @Test
    fun `version falls back to the string inside the library`() {
        val bytes = "junk\u0000VA-API version %s\u00001.22.0\u0000more".toByteArray(Charsets.ISO_8859_1)
        assertEquals(VaVersion(1, 22), SystemLibva.versionIn(bytes))
        assertNull(SystemLibva.versionIn("no version here".toByteArray()))
        val plain = Files.createTempFile("libva.so.2", "").toFile().apply { writeBytes(bytes); deleteOnExit() }
        assertEquals(VaVersion(1, 22), SystemLibva.versionOf(plain))
    }

    @Test
    fun `the bundled libva of the MediaMP runtime is read from the class path`() {
        // MediaMP 0.5.3 bundles libva 2.20 (VA-API 1.20); the runtime jar is on the desktop test class path.
        assertEquals(VaVersion(1, 20), SystemLibva.bundledVersion())
    }

    @Test
    fun `a folder needs all three libraries and a version at least the bundled one`() {
        val partial = folder("libva.so.2", "libva-drm.so.2")
        val old = folder(*SystemLibva.LIBRARIES.toTypedArray(), version = "1800")
        val good = folder(*SystemLibva.LIBRARIES.toTypedArray(), version = "2400")
        val min = VaVersion(1, 20)
        assertEquals(SystemLibva.Choice.Use(good, VaVersion(1, 24)), SystemLibva.choose(listOf(partial, old, good), min))
        val skip = assertIs<SystemLibva.Choice.Skip>(SystemLibva.choose(listOf(partial, old), min))
        assertTrue("older" in skip.reason, skip.reason)
        assertEquals(DesktopVideoDecoding.Why.TOO_OLD, skip.why)
        assertEquals(SystemLibva.Choice.Skip("no system libva"), SystemLibva.choose(listOf(partial), min))
        assertEquals(SystemLibva.Choice.Skip("no system libva"), SystemLibva.choose(emptyList(), min))
    }

    @Test
    fun `preload loads libva first and keeps the bundled copy when a load fails`() {
        // Once the real system libva is in the process (an engine test ran first in this JVM), preload is a no-op by design.
        if (!isLinux() || SystemLibva.hasRun()) return
        val good = folder(*SystemLibva.LIBRARIES.toTypedArray(), version = "2400")
        val loads = mutableListOf<String>()
        val failed = SystemLibva.preload(dirs = listOf(good), load = { path ->
            loads += File(path).name
            if (path.endsWith("libva-x11.so.2")) throw UnsatisfiedLinkError("no X11")
        })
        assertEquals(SystemLibva.LIBRARIES, loads)
        assertEquals(DesktopVideoDecoding.LibvaSource.BUNDLED, failed.source)
        assertTrue(failed.reason.orEmpty().startsWith("libva-x11.so.2"), failed.reason)
        assertEquals(DesktopVideoDecoding.Why.LOAD_FAILED, failed.why)
        assertNull(SystemLibva.loadedDir())
        val off = SystemLibva.preload(enabled = false, dirs = listOf(good), load = { error("must not load") })
        assertEquals(DesktopVideoDecoding.LibvaSource.BUNDLED, off.source)
        assertEquals(DesktopVideoDecoding.Why.DISABLED, off.why)
    }

    @Test
    fun `codec caps follow the software decoders and the hardware profiles`() {
        val vaapi = DesktopVideoDecoding.Vaapi("Mesa", h264 = true, hevc = true, hevcMain10 = false, av1 = false, vp9 = true)
        val caps = desktopCodecCaps(setOf("h264", "hevc", "libdav1d"), vaapi, nvidia = false)
        assertTrue(caps.hevc && caps.hevcMain10 && caps.av1 && caps.av1Main10)
        assertTrue(caps.hevcHw)
        assertFalse(caps.hevcMain10Hw)
        assertFalse(caps.av1Hw)

        // An FFmpeg without HEVC or AV1 and no hardware decoder: those releases are not compatible.
        val none = desktopCodecCaps(setOf("h264"), null, nvidia = false)
        assertFalse(none.hevc || none.hevcMain10 || none.av1 || none.av1Main10)
        assertFalse(none.hevcHw || none.av1Hw)

        // Hardware alone makes a codec compatible; NVIDIA's driver decodes HEVC 8 / 10 bits.
        val nv = desktopCodecCaps(setOf("h264"), null, nvidia = true)
        assertTrue(nv.hevc && nv.hevcMain10 && nv.hevcHw && nv.hevcMain10Hw)
        assertFalse(nv.av1 || nv.av1Hw)

        // Not read yet: the bundled build's decoders (HEVC and dav1d).
        val unread = desktopCodecCaps(null, null, nvidia = false)
        assertTrue(unread.hevc && unread.av1 && unread.av1Main10)
        assertFalse(unread.hevcHw)
    }

    @Test
    fun `render nodes are listed in order`() {
        val dir = Files.createTempDirectory("dri").toFile().apply { deleteOnExit() }
        for (n in listOf("renderD129", "card0", "renderD128")) File(dir, n).writeText("")
        assertEquals(listOf("renderD128", "renderD129"), VaapiProbe.renderNodes(dir).map { it.name })
        assertNull(VaapiProbe.probe(null, listOf(File(dir, "renderD128"))))
        assertNull(VaapiProbe.probe(dir, emptyList()))
    }

    @Test
    fun `malloc trim runs on Linux`() {
        if (!isLinux()) return
        val before = NativeHeap.trims
        assertTrue(NativeHeap.trim())
        assertEquals(before + 1, NativeHeap.trims)
    }
}

class DesktopShaderResourcesTest {
    @kotlin.test.Test
    fun `the desktop only shaders are bundled and installable`() {
        kotlin.test.assertTrue(DesktopMpvAssets.hasDesktopShaders)
        val dir = java.nio.file.Files.createTempDirectory("shaders").toFile().apply { deleteOnExit() }
        val installer = DesktopShaderInstaller(dir)
        val specs = app.nijiyome.player.mpv.quality.ShaderCatalog.DESKTOP_FILES.map { app.nijiyome.player.mpv.quality.ShaderSpec(it) }
        val paths = installer.resolve(specs, fp16 = true)!!
        kotlin.test.assertEquals(specs.size, paths.size)
        paths.forEach { kotlin.test.assertTrue(java.io.File(it).length() > 1000, it) }
    }
}
