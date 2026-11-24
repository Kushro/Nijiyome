// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.sun.jna.platform.win32.Kernel32
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** No screensaver and no suspend while a reader is open or a video plays (see [ScreenSaverInhibitor]). */
@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        ScreenSaverInhibitor.shared.acquire()
        onDispose { ScreenSaverInhibitor.shared.release() }
    }
}

/**
 * Holds the system's idle inhibitor while [acquire]d at least once. [acquire] and [release] only post to one background thread, so
 * the UI thread never waits on the system. After the last [release] the lock is kept for [lingerMs]: a pause and play (the player
 * keeps the screen on only while playing) reuses the lock that is already held instead of starting another.
 */
internal class ScreenSaverInhibitor(
    private val lock: InhibitLock,
    private val lingerMs: Long = LINGER_MS,
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "nijiyome-keep-screen-on").apply { isDaemon = true }
    },
) {
    // Only touched on [worker].
    private var holders = 0
    private var held = false
    private var pendingLetGo: ScheduledFuture<*>? = null

    fun acquire() {
        worker.execute {
            holders++
            pendingLetGo?.cancel(false)
            pendingLetGo = null
            if (!held) held = runCatching { lock.hold() }.isSuccess
        }
    }

    fun release() {
        worker.execute {
            if (holders == 0 || --holders > 0) return@execute
            pendingLetGo?.cancel(false)
            pendingLetGo = worker.schedule({
                if (holders == 0 && held) {
                    runCatching { lock.letGo() }
                    held = false
                }
                pendingLetGo = null
            }, lingerMs, TimeUnit.MILLISECONDS)
        }
    }

    companion object {
        /** Short enough to let the screen sleep soon after reading or watching ends, long enough for a pause. */
        const val LINGER_MS: Long = 30_000

        val shared: ScreenSaverInhibitor by lazy { ScreenSaverInhibitor(platformInhibitLock()) }
    }
}

/** The system's idle lock. Both calls run on the inhibitor's own thread, always the same one. */
internal interface InhibitLock {
    fun hold()

    fun letGo()
}

private fun platformInhibitLock(): InhibitLock {
    val os = System.getProperty("os.name").orEmpty()
    if (os.startsWith("Windows", ignoreCase = true)) return WindowsExecutionState
    val command = inhibitCommand(os, desktop = System.getenv("XDG_CURRENT_DESKTOP").orEmpty(), onPath = ::onPath)
    return if (command != null) HelperProcessLock(command) else NoLock
}

private object NoLock : InhibitLock {
    override fun hold() = Unit

    override fun letGo() = Unit
}

/**
 * Windows: `SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED)` on the inhibitor's thread, cleared with
 * `ES_CONTINUOUS` alone. The state belongs to that thread and ends with the process.
 */
private object WindowsExecutionState : InhibitLock {
    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002

    override fun hold() {
        Kernel32.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED)
    }

    override fun letGo() {
        Kernel32.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS)
    }
}

/**
 * Linux: a helper that holds the lock until its standard input closes, so the lock also goes away if the app is killed
 * (`gnome-session-inhibit` on GNOME, whose screensaver does not follow logind; `systemd-inhibit` elsewhere).
 */
private class HelperProcessLock(private val command: List<String>) : InhibitLock {
    private var process: Process? = null

    override fun hold() {
        process = ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    }

    override fun letGo() {
        val p = process ?: return
        process = null
        runCatching { p.outputStream.close() }
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.descendants().forEach { it.destroy() }
            p.destroy()
        }
    }
}

private fun onPath(name: String): Boolean =
    System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).any { dir -> dir.isNotBlank() && File(dir, name).canExecute() }

private const val REASON = "Reading in Nijiyome"

/** The Linux helper that holds the idle lock until its standard input closes, or null when this system has none. */
internal fun inhibitCommand(os: String, desktop: String, onPath: (String) -> Boolean): List<String>? = when {
    os.startsWith("Windows", ignoreCase = true) -> null // no helper: SetThreadExecutionState (WindowsExecutionState)
    desktop.split(':').any { it.equals("GNOME", ignoreCase = true) } && onPath("gnome-session-inhibit") ->
        listOf("gnome-session-inhibit", "--inhibit", "idle:suspend", "--reason", REASON, "cat")
    onPath("systemd-inhibit") ->
        listOf("systemd-inhibit", "--what=idle:sleep", "--who=Nijiyome", "--why=$REASON", "--mode=block", "cat")
    else -> null
}
