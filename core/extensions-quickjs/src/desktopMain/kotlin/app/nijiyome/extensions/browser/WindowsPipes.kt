// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The debugging pipe on Windows (1.1 review #10), so no TCP port is opened there either: two anonymous pipes whose browser ends are
 * inheritable and handed over with `--remote-debugging-pipe --remote-debugging-io-pipes=<in>,<out>` (Chromium's Windows switch: "a comma
 * separated list of two pipe handles serialized as unsigned integers"). The JDK starts processes with handle inheritance on, so the
 * browser receives them; our ends are made non-inheritable.
 *
 * A process started by another thread of the app at the same moment could inherit the browser ends too (it would only hold them
 * open); starts of the hidden browser are serialized by [ChromiumHost].
 */
// PENDIENTE-ESCRITORIO: pipe de depuración en Windows sin probar (compilado y revisado en Linux; si falla, ChromiumHost vuelve al puerto en 127.0.0.1)
internal class WindowsPipes private constructor(
    private val browserIn: WinNT.HANDLE,
    private val browserOut: WinNT.HANDLE,
    private val ourWrite: WinNT.HANDLE,
    private val ourRead: WinNT.HANDLE,
) {
    /** The switch with the browser's ends: what it reads, what it writes. */
    val switch: String get() = "--remote-debugging-io-pipes=${value(browserIn)},${value(browserOut)}"

    private val childEndsClosed = AtomicBoolean(false)

    /** After the browser started (or failed to): our copies of its ends are closed, so its exit ends our reads. */
    fun closeBrowserEnds() {
        if (!childEndsClosed.compareAndSet(false, true)) return
        Kernel32.INSTANCE.CloseHandle(browserIn)
        Kernel32.INSTANCE.CloseHandle(browserOut)
    }

    fun transport(): CdpTransport = PipeTransport(HandleOutput(ourWrite), HandleInput(ourRead))

    /** Everything, when the start failed before [transport] took our ends. */
    fun closeAll() {
        closeBrowserEnds()
        Kernel32.INSTANCE.CloseHandle(ourWrite)
        Kernel32.INSTANCE.CloseHandle(ourRead)
    }

    private class HandleInput(private val h: WinNT.HANDLE) : InputStream() {
        private val closed = AtomicBoolean(false)

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed.get()) return -1
            if (len == 0) return 0
            val tmp = if (off == 0) b else ByteArray(len)
            val n = IntByReference()
            // false at ERROR_BROKEN_PIPE: the browser exited
            if (!Kernel32.INSTANCE.ReadFile(h, tmp, len, n, null)) return -1
            if (n.value <= 0) return -1
            if (off != 0) System.arraycopy(tmp, 0, b, off, n.value)
            return n.value
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) Kernel32.INSTANCE.CloseHandle(h)
        }
    }

    private class HandleOutput(private val h: WinNT.HANDLE) : OutputStream() {
        private val closed = AtomicBoolean(false)

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed.get()) throw IOException("pipe closed")
            var sent = 0
            while (sent < len) {
                val chunk = if (off + sent == 0 && len == b.size) b else b.copyOfRange(off + sent, off + len)
                val n = IntByReference()
                if (!Kernel32.INSTANCE.WriteFile(h, chunk, chunk.size, n, null)) throw IOException("pipe write failed (${Kernel32.INSTANCE.GetLastError()})")
                if (n.value <= 0) throw IOException("pipe write failed")
                sent += n.value
            }
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) Kernel32.INSTANCE.CloseHandle(h)
        }
    }

    companion object {
        private fun value(h: WinNT.HANDLE): String = java.lang.Long.toUnsignedString(Pointer.nativeValue(h.pointer))

        /** Two inheritable pipes (Windows only). Throws [IOException] when they cannot be made. */
        fun create(): WindowsPipes {
            val k = Kernel32.INSTANCE
            val sa = WinBase.SECURITY_ATTRIBUTES().apply {
                dwLength = com.sun.jna.platform.win32.WinDef.DWORD(size().toLong())
                bInheritHandle = true
            }
            val inRead = WinNT.HANDLEByReference()
            val inWrite = WinNT.HANDLEByReference()
            val outRead = WinNT.HANDLEByReference()
            val outWrite = WinNT.HANDLEByReference()
            if (!k.CreatePipe(inRead, inWrite, sa, 0)) throw IOException("CreatePipe failed (${k.GetLastError()})")
            if (!k.CreatePipe(outRead, outWrite, sa, 0)) {
                k.CloseHandle(inRead.value)
                k.CloseHandle(inWrite.value)
                throw IOException("CreatePipe failed (${k.GetLastError()})")
            }
            // our ends stay with us
            k.SetHandleInformation(inWrite.value, WinBase.HANDLE_FLAG_INHERIT, 0)
            k.SetHandleInformation(outRead.value, WinBase.HANDLE_FLAG_INHERIT, 0)
            return WindowsPipes(browserIn = inRead.value, browserOut = outWrite.value, ourWrite = inWrite.value, ourRead = outRead.value)
        }
    }
}
