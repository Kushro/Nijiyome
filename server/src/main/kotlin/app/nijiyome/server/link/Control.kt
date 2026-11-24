// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/control.rs (AGPL-3.0, same author). CANONICAL wire shared with :core:link LinkControl.kt.
package app.nijiyome.server.link

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * `POST /v1/control` body (`{ "command": "play" | "pause" | "toggle" | "next" | "prev" | "seek" | "open", ... }`, camelCase):
 * `204` when the host's player took it, `409 no_player` when nothing is playing (`open` = "Continuar en la PC" is always taken).
 */
sealed interface ControlCommand {
    data object Play : ControlCommand
    data object Pause : ControlCommand
    data object Toggle : ControlCommand
    data object Next : ControlCommand
    data object Prev : ControlCommand
    data class Seek(val seconds: Long) : ControlCommand
    data class Open(val mediaId: String, val episode: Long, val positionSecs: Long = 0) : ControlCommand

    companion object {
        private fun JsonObject.u32(key: String): Long? {
            val p = this[key] as? JsonPrimitive ?: return null
            if (p.isString) return null
            return p.longOrNull?.takeIf { it in 0..UInt.MAX_VALUE.toLong() }
        }

        /** The command of a JSON body; null for an unknown command, a missing or ill-typed field. */
        fun parse(o: JsonObject): ControlCommand? {
            val cmd = (o["command"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            return when (cmd) {
                "play" -> Play
                "pause" -> Pause
                "toggle" -> Toggle
                "next" -> Next
                "prev" -> Prev
                "seek" -> Seek(o.u32("seconds") ?: return null)
                "open" -> {
                    val media = (o["mediaId"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                    val episode = o.u32("episode") ?: return null
                    val pos = if (o.containsKey("positionSecs")) o.u32("positionSecs") ?: return null else 0
                    Open(media, episode, pos)
                }
                else -> null
            }
        }

        /** Shape checks beyond JSON (ids bounded, episodes from 1, seeks within a day). */
        fun valid(c: ControlCommand): Boolean = when (c) {
            is Open -> c.mediaId.isNotBlank() && c.mediaId.length <= 128 && c.mediaId.none { it.isISOControl() } && c.episode >= 1
            is Seek -> c.seconds <= 24 * 3600
            else -> true
        }
    }
}

/** Longest `wait` of `GET /v1/events` (seconds); larger values are clamped. */
const val EVENTS_MAX_WAIT_SECS = 25

/** Events kept per device until it polls (oldest dropped first). */
const val EVENTS_QUEUE_MAX = 16

fun clampWait(wait: Long?): Int = (wait ?: EVENTS_MAX_WAIT_SECS.toLong()).coerceIn(1, EVENTS_MAX_WAIT_SECS.toLong()).toInt()

/** What a `history_resync` event asks the phone to do ("Use this device as the source"). */
enum class ResyncMode(val wire: String) { PUSH_ALL("push_all"), PULL_REPLACE("pull_replace") }

/** One PC -> phone event of `GET /v1/events` (tagged by `type`, camelCase fields; unknown values omitted). */
sealed interface PhoneEvent {
    fun toJson(): JsonObject

    /** "Enviar a dispositivo": play the Link session [sessionId] (already started by the PC) on the phone. */
    data class Handoff(val sessionId: String, val mediaId: String? = null, val episode: Int? = null, val positionSecs: Int = 0, val title: String? = null) : PhoneEvent {
        override fun toJson() = buildJsonObject {
            put("type", "handoff")
            put("sessionId", sessionId)
            mediaId?.let { put("mediaId", it) }
            episode?.let { put("episode", it) }
            put("positionSecs", positionSecs)
            title?.let { put("title", it) }
        }
    }

    data class HistoryResync(val mode: ResyncMode) : PhoneEvent {
        override fun toJson() = buildJsonObject {
            put("type", "history_resync")
            put("mode", mode.wire)
        }
    }
}

fun eventsPage(events: List<PhoneEvent>): JsonObject = buildJsonObject {
    put("events", buildJsonArray { events.forEach { add(it.toJson()) } })
}
