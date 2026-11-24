// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop

import java.io.File
import kotlin.system.exitProcess

/**
 * libtorrent4j's native library installs its own SIGSEGV / SIGBUS handlers over the JVM's once it has done disk work, and HotSpot's
 * own deliberate faults (null checks, safepoints) then kill the app with SIGSEGV and no crash report. HotSpot's signal-chaining
 * library (`<java.home>/lib/libjsig.so`) keeps the JVM's handlers first and chains to the library's, but it only works when it is
 * preloaded before the JVM starts. So on Linux the app starts itself once more with `LD_PRELOAD` set and waits for that run, unless it
 * already has it (a launcher that preloads it, or this relaunch). Windows has no signals: nothing to do.
 */
internal object SignalChaining {
    /** The marker of the relaunched process (never relaunch twice). */
    private const val MARKER = "NIJIYOME_JSIG"

    /** Set by the jpackage launcher for its own re-exec; never passed on to a new launch of the app. */
    internal const val JPACKAGE_STAGE = "_JPACKAGE_LAUNCHER"

    /** The command to run again with libjsig preloaded, or null when this process does not need it. */
    fun relaunchCommand(
        env: (String) -> String?,
        osName: String,
        javaHome: String,
        command: String?,
        arguments: List<String>?,
    ): List<String>? {
        if (!osName.startsWith("Linux", ignoreCase = true)) return null
        if (env(MARKER) != null) return null
        if (env("LD_PRELOAD")?.contains("libjsig") == true) return null
        if (!File(javaHome, "lib/libjsig.so").isFile) return null
        val exe = command?.takeIf { it.isNotBlank() } ?: return null
        return listOf(exe) + (arguments ?: return null)
    }

    /**
     * This process's arguments (after the executable). `ProcessHandle.Info.arguments()` comes back empty for a long command line
     * (`gradlew run` passes the whole classpath), so on Linux they are read from `/proc/self/cmdline`.
     */
    private fun ownArguments(): List<String>? {
        val raw = runCatching { File("/proc/self/cmdline").readBytes() }.getOrNull() ?: return ProcessHandle.current().info().arguments().orElse(null)?.toList()
        val parts = String(raw, Charsets.UTF_8).split('\u0000').dropLastWhile { it.isEmpty() }
        return parts.drop(1)
    }

    /**
     * Runs the app again with libjsig preloaded and exits with its status; returns when this process should go on itself.
     * A packaged app (jpackage image, AppImage, installed package) runs its launcher again with [args], the arguments `main` got:
     * inside a jpackage launcher the process's executable is the runtime's `java`, which would not know the main class.
     */
    fun relaunchIfNeeded(args: Array<String>) {
        val javaHome = System.getProperty("java.home") ?: return
        val info = ProcessHandle.current().info()
        val launcher = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }
        val command = launcher ?: info.command().orElse(null)
        val arguments = if (launcher != null) args.toList() else ownArguments()
        val cmd = relaunchCommand(System::getenv, System.getProperty("os.name").orEmpty(), javaHome, command, arguments)
            ?: return
        val jsig = File(javaHome, "lib/libjsig.so").absolutePath
        val status = try {
            val pb = ProcessBuilder(cmd).inheritIO()
            pb.environment()["LD_PRELOAD"] = listOfNotNull(jsig, System.getenv("LD_PRELOAD")?.takeIf { it.isNotBlank() }).joinToString(" ")
            pb.environment()[MARKER] = "1"
            // The jpackage launcher passes its second stage through this variable: a child that inherits it hands its
            // arguments straight to the JVM ("magnet:…" as the main class) instead of starting the app.
            pb.environment().remove(JPACKAGE_STAGE)
            val child = pb.start()
            // Closing this process (Ctrl+C, a session ending) closes the app too.
            Runtime.getRuntime().addShutdownHook(Thread { if (child.isAlive) child.destroy() })
            child.waitFor()
        } catch (_: Exception) {
            // Could not start it: run here without the chaining (torrents may crash the app, everything else works).
            return
        }
        exitProcess(status)
    }
}
