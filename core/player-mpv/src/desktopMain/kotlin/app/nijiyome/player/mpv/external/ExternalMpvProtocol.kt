// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.player.mpv.MpvState
import app.nijiyome.player.mpv.MpvTrack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// Hito 4, paso D: mpv's JSON IPC (https://mpv.io/manual/stable/#json-ipc) as the external session speaks it. Pure: the command lines
// the session writes, the lines mpv sends back, and how they change the session's [ExternalMpvSnapshot]. Ported from Tatakai Next
// (tk-player/src/external.rs, `parse_message`), which only followed position, duration, pause and the end; the Nijiyome session
// follows the same properties as the embedded engine (DesktopMpvEngine) so the shared PlaybackState mapping (mpvPlaybackState) works
// unchanged.

/**
 * Properties observed on the event connection. The first group feeds [MpvState]; the second is read back by the quality controller
 * and the frame-drop monitor ([ExternalMpvSnapshot.props]).
 */
internal val OBSERVED_PROPERTIES: List<String> = listOf(
    "time-pos", "duration", "pause", "eof-reached", "paused-for-cache", "core-idle", "speed", "demuxer-cache-time", "track-list",
    "width", "height", "sub-delay", "loop-file", "secondary-sid", "volume", "mute", "sub-text", "vo-configured",
    "current-vo", "gpu-api", "hwdec-current", "frame-drop-count", "decoder-frame-drop-count", "vo-delayed-frame-count", "container-fps",
    "estimated-vf-fps", "estimated-display-fps", "display-fps",
)

/** Properties kept as plain text in [ExternalMpvSnapshot.props] (telemetry). */
private val TELEMETRY = setOf(
    "current-vo", "gpu-api", "hwdec-current", "frame-drop-count", "decoder-frame-drop-count", "vo-delayed-frame-count", "container-fps",
    "estimated-vf-fps", "estimated-display-fps", "display-fps", "volume", "mute",
)

/** One JSON IPC command line (without the newline): `{"command":[...],"request_id":N}`. Strings, numbers, booleans and string lists. */
internal fun ipcCommand(args: List<Any?>, requestId: Long? = null): String {
    val command = JsonArray(args.map(::jsonOf))
    val fields = buildMap<String, JsonElement> {
        put("command", command)
        if (requestId != null) put("request_id", JsonPrimitive(requestId))
    }
    return JsonObject(fields).toString()
}

private fun jsonOf(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Int -> JsonPrimitive(v)
    is Long -> JsonPrimitive(v)
    is Double -> JsonPrimitive(v)
    is Float -> JsonPrimitive(v.toDouble())
    is List<*> -> JsonArray(v.map(::jsonOf))
    else -> JsonPrimitive(v.toString())
}

/** The hello of the event connection: one `observe_property` per [OBSERVED_PROPERTIES] entry (id = index + 1). */
internal fun observeCommands(): List<String> = OBSERVED_PROPERTIES.mapIndexed { i, name -> ipcCommand(listOf("observe_property", i + 1, name)) }

/** What one line from mpv is. */
internal sealed interface IpcMessage {
    data class PropertyChange(val name: String, val data: JsonElement?) : IpcMessage

    /** Any other event (`file-loaded`, `playback-restart`, `end-file`, `shutdown`...) with all its fields. */
    data class Event(val name: String, val fields: JsonObject) : IpcMessage

    /** A command reply: [error] is `"success"` when it worked. */
    data class Reply(val requestId: Long?, val error: String, val data: JsonElement?) : IpcMessage
}

private val json = Json { ignoreUnknownKeys = true }

/** One line from mpv; null for blank lines and anything that is not a JSON object. */
internal fun parseIpcLine(line: String): IpcMessage? {
    val obj = runCatching { json.parseToJsonElement(line.trim()) }.getOrNull() as? JsonObject ?: return null
    val event = obj["event"]?.stringOrNull()
    if (event != null) {
        return if (event == "property-change") {
            val name = obj["name"]?.stringOrNull() ?: return null
            IpcMessage.PropertyChange(name, obj["data"]?.takeUnless { it is JsonNull })
        } else {
            IpcMessage.Event(event, obj)
        }
    }
    val error = obj["error"]?.stringOrNull() ?: return null
    return IpcMessage.Reply(obj["request_id"]?.jsonPrimitive?.longOrNull, error, obj["data"]?.takeUnless { it is JsonNull })
}

/** The external session's view of one mpv process. [mpv] uses the same fields as the embedded engine; [props] holds telemetry. */
internal data class ExternalMpvSnapshot(
    val mpv: MpvState = MpvState(),
    val paused: Boolean = false,
    /** `core-idle`: mpv is not advancing (paused, buffering, seeking, nothing loaded). */
    val coreIdle: Boolean = false,
    /** `paused-for-cache`. */
    val pausedForCache: Boolean = false,
    /**
     * The current file is loaded (subtitles can be added): the `file-loaded` event, or a non-null `time-pos` / `duration` (they only
     * exist once a file is loaded). The event alone is not enough: mpv sends events only to the clients connected at that moment.
     */
    val fileLoaded: Boolean = false,
    /** The first `time-pos` of this file; a later different one means frames are playing ([MpvState.started]). */
    val firstPositionSecs: Double? = null,
    /** `vo-configured`: the video output got its first frame (true also while paused). */
    val voConfigured: Boolean = false,
    /** `end-file` came for this file: it is unloaded, so a later `eof-reached=false` is not a return from the end. */
    val fileEnded: Boolean = false,
    /** mpv exited (window closed, `q`, crash) or the IPC connection closed. */
    val closed: Boolean = false,
    val props: Map<String, String> = emptyMap(),
)

/** A `time-pos` this far from the first one of the file means playback advanced. */
private const val POSITION_EPSILON_SECS = 0.02

/** mpv's `end-file` reasons that end the playback normally. */
private const val END_EOF = "eof"
private const val END_ERROR = "error"

/** [s] after the line [msg]. Mirrors DesktopMpvEngine's listener (pause / cache / eof / core-idle / playback-restart / end-file). */
internal fun reduce(s: ExternalMpvSnapshot, msg: IpcMessage): ExternalMpvSnapshot = when (msg) {
    is IpcMessage.Reply -> s
    is IpcMessage.PropertyChange -> derive(reduceProperty(s, msg.name, msg.data))
    is IpcMessage.Event -> derive(reduceEvent(s, msg))
}

/**
 * Playing and buffering from the raw flags, after every line, so the state does not depend on which events the session saw: events
 * that fired before it connected are lost, but every observed property is sent again when it subscribes.
 */
private fun derive(s: ExternalMpvSnapshot): ExternalMpvSnapshot {
    // A frame reached the video output: started, also when the session connected after `playback-restart` and mpv is paused.
    val m = if (!s.mpv.started && s.voConfigured && s.fileLoaded) s.mpv.copy(started = true) else s.mpv
    val stopped = m.ended || m.error != null
    return s.copy(
        mpv = m.copy(
            playing = s.fileLoaded && !s.paused && !s.coreIdle && !stopped,
            buffering = !stopped && (s.pausedForCache || !m.started),
        ),
    )
}

private fun reduceProperty(s: ExternalMpvSnapshot, name: String, data: JsonElement?): ExternalMpvSnapshot {
    val m = s.mpv
    val props = if (name in TELEMETRY) {
        val text = data?.let(::propText)
        if (text == null) s.props - name else s.props + (name to text)
    } else {
        s.props
    }
    val base = s.copy(props = props)
    return when (name) {
        "pause" -> data.bool()?.let { base.copy(paused = it) } ?: base
        "paused-for-cache" -> base.copy(pausedForCache = data.bool() == true)
        "eof-reached" -> when (data.bool()) {
            true -> if (s.fileLoaded) base.copy(mpv = m.copy(ended = true)) else base
            // Back from the end (a seek in a keep-open window): playing again.
            false -> if (s.fileEnded) base else base.copy(mpv = m.copy(ended = false))
            null -> base
        }
        "vo-configured" -> base.copy(voConfigured = data.bool() == true)
        "core-idle" -> data.bool()?.let { base.copy(coreIdle = it) } ?: base
        "time-pos" -> {
            val pos = data.double()?.coerceAtLeast(0.0) ?: return base
            val first = s.firstPositionSecs ?: pos
            // The position moved on from where the file opened: frames are playing, even if `playback-restart` came before the
            // session connected.
            val moved = !s.paused && kotlin.math.abs(pos - first) > POSITION_EPSILON_SECS
            base.copy(fileLoaded = true, firstPositionSecs = first, mpv = m.copy(positionSecs = pos, started = m.started || moved))
        }
        "duration" -> data.double()?.let { base.copy(fileLoaded = true, mpv = m.copy(durationSecs = it.coerceAtLeast(0.0))) } ?: base
        "speed" -> data.double()?.let { base.copy(mpv = m.copy(speed = it)) } ?: base
        "demuxer-cache-time" -> data.double()?.let { base.copy(mpv = m.copy(cacheSecs = it.coerceAtLeast(0.0))) } ?: base
        "width" -> base.copy(mpv = m.copy(videoWidth = data.double()?.toInt() ?: 0))
        "height" -> base.copy(mpv = m.copy(videoHeight = data.double()?.toInt() ?: 0))
        "sub-delay" -> data.double()?.let { base.copy(mpv = m.copy(subDelayMs = Math.round(it * 1000))) } ?: base
        "loop-file" -> base.copy(mpv = m.copy(loop = loopOn(data)))
        "secondary-sid" -> base.copy(mpv = m.copy(secondarySid = trackId(data)))
        "sub-text" -> base.copy(mpv = m.copy(subText = data?.stringOrNull()?.takeIf { it.isNotBlank() }))
        "track-list" -> {
            val (subs, audio) = tracks(data)
            base.copy(mpv = m.copy(subtitles = subs, audio = audio))
        }
        else -> base
    }
}

private fun reduceEvent(s: ExternalMpvSnapshot, e: IpcMessage.Event): ExternalMpvSnapshot {
    val m = s.mpv
    return when (e.name) {
        "start-file" -> s.copy(fileLoaded = false, firstPositionSecs = null, voConfigured = false, fileEnded = false, mpv = m.copy(ended = false, error = null, started = false))
        "file-loaded" -> s.copy(fileLoaded = true)
        "playback-restart" -> s.copy(mpv = m.copy(started = true, ended = false))
        "end-file" -> {
            val unloaded = s.copy(fileEnded = true)
            when (e.fields["reason"]?.stringOrNull()) {
                END_EOF -> if (s.fileLoaded || m.started) unloaded.copy(mpv = m.copy(ended = true)) else unloaded
                END_ERROR -> {
                    val detail = e.fields["file_error"]?.stringOrNull() ?: "unknown error"
                    unloaded.copy(mpv = m.copy(error = "mpv could not play the file: $detail"))
                }
                else -> unloaded
            }
        }
        "shutdown" -> s.copy(closed = true)
        else -> s
    }
}

/** mpv's `track-list` node: `[{id, type, title, lang, codec, selected, external}, ...]` split into subtitles and audio. */
internal fun tracks(data: JsonElement?): Pair<List<MpvTrack>, List<MpvTrack>> {
    val list = data as? JsonArray ?: return emptyList<MpvTrack>() to emptyList()
    val subs = mutableListOf<MpvTrack>()
    val audio = mutableListOf<MpvTrack>()
    for (el in list) {
        val t = el as? JsonObject ?: continue
        val id = t["id"]?.double()?.toInt() ?: continue
        val track = MpvTrack(
            id = id,
            title = t["title"]?.stringOrNull(),
            lang = t["lang"]?.stringOrNull(),
            codec = t["codec"]?.stringOrNull(),
            selected = t["selected"].bool() == true,
            external = t["external"].bool() == true,
        )
        when (t["type"]?.stringOrNull()) {
            "sub" -> subs += track
            "audio" -> audio += track
        }
    }
    return subs to audio
}

/** `loop-file`: `"inf"`, a count or `true` = on; `"no"` / `false` / 0 = off. */
private fun loopOn(data: JsonElement?): Boolean {
    val p = data as? JsonPrimitive ?: return false
    p.booleanOrNull?.let { return it }
    p.longOrNull?.let { return it > 0 }
    return p.contentOrNull.let { it != null && it != "no" && it != "0" }
}

/** A track id property (`sid`, `secondary-sid`): a number, or `false` / `"no"` = none. */
private fun trackId(data: JsonElement?): Int? = (data as? JsonPrimitive)?.let { p -> p.longOrNull?.toInt() ?: p.contentOrNull?.toIntOrNull() }

/** The text form of a telemetry value (`frame-drop-count` 3, `current-vo` "gpu-next", `mute` "yes"). */
private fun propText(data: JsonElement): String? {
    val p = data as? JsonPrimitive ?: return null
    p.booleanOrNull?.let { return if (it) "yes" else "no" }
    p.longOrNull?.let { return it.toString() }
    p.doubleOrNull?.let { return it.toString() }
    return p.contentOrNull
}

private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

private fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
