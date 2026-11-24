// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InhibitCommandTest {
    @Test
    fun gnomeUsesItsSessionInhibitor() {
        val cmd = inhibitCommand("Linux", "ubuntu:GNOME", onPath = { true })!!
        assertEquals("gnome-session-inhibit", cmd.first())
        assertEquals("cat", cmd.last())
    }

    @Test
    fun otherLinuxDesktopsUseLogind() {
        val cmd = inhibitCommand("Linux", "KDE", onPath = { it == "systemd-inhibit" })!!
        assertEquals("systemd-inhibit", cmd.first())
        assertTrue("--what=idle:sleep" in cmd)
        // GNOME without its tool falls back to logind too
        assertEquals("systemd-inhibit", inhibitCommand("Linux", "GNOME", onPath = { it == "systemd-inhibit" })!!.first())
    }

    @Test
    fun windowsNeedsNoHelperProcess() {
        // SetThreadExecutionState through JNA on the inhibitor's thread: no powershell.exe per play / pause.
        assertNull(inhibitCommand("Windows 11", "", onPath = { true }))
    }

    @Test
    fun nothingWhenThereIsNoHelper() {
        assertNull(inhibitCommand("Linux", "sway", onPath = { false }))
    }
}

class DesktopBackDispatcherTest {
    @Test
    fun newestEnabledHandlerRuns() {
        val d = DesktopBackDispatcher()
        val calls = mutableListOf<String>()
        d.register(true) { calls += "a" }
        val b = d.register(true) { calls += "b" }
        assertTrue(d.dispatch())
        b.update(enabled = false) { calls += "b" }
        assertTrue(d.dispatch())
        b.remove()
        assertEquals(listOf("b", "a"), calls)
    }

    @Test
    fun nothingRegistered() {
        assertEquals(false, DesktopBackDispatcher().dispatch())
    }
}
