// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import com.sun.jna.platform.unix.X11
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NativeWindowDragAndTrayTest {
    @Test
    fun buildMoveResizeEventSelectsNativeLongUnionWithoutThrowing() {
        // X11.XEvent JNA union layout is Linux/X11-only (NativeLong size differs on Windows LLP64).
        if (!System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)) return
        val atom = X11.Atom(42L)
        val event = NativeWindowDrag.buildMoveResizeEvent(
            windowId = 0x123456L,
            atom = atom,
            rootX = 500L,
            rootY = 200L,
            direction = NativeWindowDrag.DIR_MOVE,
        )
        assertEquals(X11.ClientMessage, event.xclient.type)
        assertEquals(32, event.xclient.format)
        assertEquals(500L, event.xclient.data.l[0].toLong())
        assertEquals(200L, event.xclient.data.l[1].toLong())
        assertEquals(NativeWindowDrag.DIR_MOVE, event.xclient.data.l[2].toLong())
        assertEquals(1L, event.xclient.data.l[3].toLong())
        assertEquals(1L, event.xclient.data.l[4].toLong())
    }

    @Test
    fun quantizedForTrayStabilizesMinorRamJitter() {
        val base = TrayLiveStatus(
            spanish = true,
            jvmUsedBytes = 151L * 1024 * 1024,
            jvmMaxBytes = 8L * 1024 * 1024 * 1024,
            diskCacheBytes = 97L * 1024 * 1024,
        )
        val jittered = base.copy(
            jvmUsedBytes = 154L * 1024 * 1024,
            diskCacheBytes = 98L * 1024 * 1024,
        )
        val bigJump = base.copy(jvmUsedBytes = 220L * 1024 * 1024)
        assertEquals(base.quantizedForTray(), jittered.quantizedForTray())
        assertNotEquals(base.quantizedForTray(), bigJump.quantizedForTray())
    }
}
