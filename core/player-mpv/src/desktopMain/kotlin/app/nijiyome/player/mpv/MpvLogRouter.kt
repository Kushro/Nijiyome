// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MPVLog
import org.openani.mediamp.mpv.MPVLogHandler
import org.openani.mediamp.mpv.MPVLogMessage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MediaMP has one process-wide log sink for every mpv instance; this routes each line to the engine it belongs to (its last error
 * becomes the load's error text, as on Android, and the quality engine looks for shader errors) and prints errors to stderr, as
 * MediaMP does without a sink.
 */
internal object MpvLogRouter : MPVLogHandler {
    private val engines = CopyOnWriteArrayList<DesktopMpvEngine>()

    @Volatile
    private var installed = false

    fun register(engine: DesktopMpvEngine) {
        if (!installed) {
            synchronized(this) {
                if (!installed) {
                    MPVHandle.setLogHandler(this)
                    installed = true
                }
            }
        }
        engines += engine
    }

    fun unregister(engine: DesktopMpvEngine) {
        engines -= engine
    }

    override fun onLog(message: MPVLogMessage) {
        for (e in engines) if (e.ownsLog(message.instanceHandle)) e.onLog(message.level, message.prefix, message.line)
        if (message.level <= MPVLog.ERROR) System.err.println("E/mpv: [${message.prefix}] ${message.line}")
    }
}
