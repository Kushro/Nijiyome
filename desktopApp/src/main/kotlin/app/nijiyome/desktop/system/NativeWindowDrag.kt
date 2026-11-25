// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import kotlin.math.roundToInt

private interface User32Ex : StdCallLibrary {
    fun ReleaseCapture(): Boolean

    companion object {
        val INSTANCE: User32Ex by lazy { Native.load("user32", User32Ex::class.java) }
    }
}

private interface X11Ex : Library {
    fun XUngrabPointer(display: X11.Display, time: NativeLong): Int

    companion object {
        val INSTANCE: X11Ex by lazy { Native.load("X11", X11Ex::class.java) }
    }
}

/**
 * Delegates floating window move and resize gestures of an undecorated Compose Desktop window directly to the OS window manager
 * (`_NET_WM_MOVERESIZE` on X11/XWayland, `WM_NCLBUTTONDOWN` on Windows). This eliminates OpenGL/Skiko compositor smearing and
 * visual trail artifacts when dragging on KDE Plasma Wayland (XWayland) and enables native edge snapping / Aero Snap.
 */
internal object NativeWindowDrag {
    // EWMH _NET_WM_MOVERESIZE direction constants
    const val DIR_TOP_LEFT = 0L
    const val DIR_TOP = 1L
    const val DIR_TOP_RIGHT = 2L
    const val DIR_RIGHT = 3L
    const val DIR_BOTTOM_RIGHT = 4L
    const val DIR_BOTTOM = 5L
    const val DIR_BOTTOM_LEFT = 6L
    const val DIR_LEFT = 7L
    const val DIR_MOVE = 8L

    // Windows WM_NCLBUTTONDOWN hit-test constants
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val HTCAPTION = 2L
    private const val HTLEFT = 10L
    private const val HTRIGHT = 11L
    private const val HTTOP = 12L
    private const val HTTOPLEFT = 13L
    private const val HTTOPRIGHT = 14L
    private const val HTBOTTOM = 15L
    private const val HTBOTTOMLEFT = 16L
    private const val HTBOTTOMRIGHT = 17L

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val isLinux: Boolean = System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)

    fun startMove(window: Window, screenX: Int, screenY: Int): Boolean =
        startAction(window, screenX, screenY, DIR_MOVE, HTCAPTION)

    fun startResize(window: Window, screenX: Int, screenY: Int, direction: Long): Boolean {
        val hitTest = when (direction) {
            DIR_TOP_LEFT -> HTTOPLEFT
            DIR_TOP -> HTTOP
            DIR_TOP_RIGHT -> HTTOPRIGHT
            DIR_RIGHT -> HTRIGHT
            DIR_BOTTOM_RIGHT -> HTBOTTOMRIGHT
            DIR_BOTTOM -> HTBOTTOM
            DIR_BOTTOM_LEFT -> HTBOTTOMLEFT
            DIR_LEFT -> HTLEFT
            else -> return false
        }
        return startAction(window, screenX, screenY, direction, hitTest)
    }

    private fun startAction(window: Window, screenX: Int, screenY: Int, x11Direction: Long, winHitTest: Long): Boolean = when {
        isWindows -> startWindows(window, winHitTest)
        isLinux -> startX11(window, screenX, screenY, x11Direction)
        else -> false
    }

    private fun startWindows(window: Window, hitTest: Long): Boolean = runCatching {
        val ptr = Native.getComponentPointer(window) ?: return false
        val hwnd = WinDef.HWND(ptr)
        User32Ex.INSTANCE.ReleaseCapture()
        User32.INSTANCE.SendMessage(hwnd, WM_NCLBUTTONDOWN, WinDef.WPARAM(hitTest), WinDef.LPARAM(0L))
        true
    }.getOrDefault(false)

    private fun startX11(window: Window, screenX: Int, screenY: Int, direction: Long): Boolean = runCatching {
        val xid = Native.getComponentID(window)
        if (xid == 0L) return false
        val x11 = X11.INSTANCE
        val display = x11.XOpenDisplay(null) ?: return false
        try {
            val atom = x11.XInternAtom(display, "_NET_WM_MOVERESIZE", false)
            if (atom == null || atom.toLong() == 0L) return false
            val root = x11.XDefaultRootWindow(display)
            val tx = window.graphicsConfiguration?.defaultTransform
            val rootX = if (tx != null && tx.scaleX > 0.0) (screenX * tx.scaleX).roundToInt().toLong() else screenX.toLong()
            val rootY = if (tx != null && tx.scaleY > 0.0) (screenY * tx.scaleY).roundToInt().toLong() else screenY.toLong()

            X11Ex.INSTANCE.XUngrabPointer(display, NativeLong(0L))
            val event = X11.XEvent()
            event.type = X11.ClientMessage
            event.setType(X11.XClientMessageEvent::class.java)
            event.xclient.type = X11.ClientMessage
            event.xclient.window = X11.Window(xid)
            event.xclient.message_type = atom
            event.xclient.format = 32
            event.xclient.data.setType(LongArray::class.java)
            event.xclient.data.l[0] = NativeLong(rootX)
            event.xclient.data.l[1] = NativeLong(rootY)
            event.xclient.data.l[2] = NativeLong(direction)
            event.xclient.data.l[3] = NativeLong(1L) // Button1
            event.xclient.data.l[4] = NativeLong(1L) // Source: normal application
            val mask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
            val sent = x11.XSendEvent(display, root, 0, mask, event)
            x11.XFlush(display)
            sent != 0
        } finally {
            x11.XCloseDisplay(display)
        }
    }.getOrDefault(false)
}
