// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * v1.1 (F2): the "native leak" of the embedded player (spike reserve 1, ~4 to 9 MB per file opened) is glibc keeping freed memory.
 * Measured in gamescope (docs/spikes/reproductor.md, F2 addendum): libmpv alone with the same render API levels off, and the app's
 * own RSS stays flat over 30 loads when `malloc_trim(0)` runs after each one, while without it it grows by ~3.8 MB per load and ~7.7
 * MB per open / close. mpv's demuxer cache, decoder pools and render buffers are freed when a file ends, but they come from many
 * threads (many malloc arenas) and glibc only gives a freed chunk back when it is at the top of its heap.
 *
 * So after a file is loaded (the previous one's buffers are gone by then) and after a player is released, [scheduleTrim] asks glibc
 * to return what is free. Linux with glibc only (musl has no `malloc_trim`: nothing happens); `NIJIYOME_MALLOC_TRIM=0` turns it off.
 * The call takes a few milliseconds and runs on its own daemon thread, never on the UI thread.
 */
internal object NativeHeap {
    private val enabled = isLinux() && System.getenv("NIJIYOME_MALLOC_TRIM") != "0"

    private val trimFunction: Function? by lazy {
        if (!enabled) null else runCatching { NativeLibrary.getInstance("c").getFunction("malloc_trim") }.getOrNull()
    }

    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "nijiyome-malloc-trim").apply { isDaemon = true } }
    }

    private var pending: ScheduledFuture<*>? = null

    @Volatile
    var trims: Int = 0
        private set

    /** `malloc_trim(0)` now; false when unavailable. */
    fun trim(): Boolean {
        val f = trimFunction ?: return false
        return runCatching { f.invokeInt(arrayOf(0)); trims++ }.isSuccess
    }

    /**
     * One trim [delayMs] from now. A request while one is pending joins it (several loads in a row trim once) and never postpones it,
     * so a steady stream of opens and closes still trims every [delayMs].
     */
    @Synchronized
    fun scheduleTrim(delayMs: Long = TRIM_DELAY_MS) {
        if (!enabled) return
        if (pending?.isDone == false) return
        pending = executor.schedule({ trim() }, delayMs, TimeUnit.MILLISECONDS)
    }

    /** After a load or a release: long enough for mpv to free the previous file and for MediaMP to destroy a released player. */
    const val TRIM_DELAY_MS = 2_000L
}
