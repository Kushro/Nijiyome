// SPDX-License-Identifier: AGPL-3.0-or-later
// The PC side of Nijiyome Link's remote control and "Enviar a dispositivo" (after Tatakai Next tk-app/src/live/link.rs, AGPL-3.0, same
// author). Desktop only: Android binds none of this (there the phone is the Link client, see LinkRepository).
package app.nijiyome.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the PC's player shows, for `GET /v1/player`. [mediaId] = [HistoryEntry.animeId] form (`"21"`, `"mal:5114"`). */
data class PcPlayerState(
    val playing: Boolean,
    val title: String?,
    val episode: Int?,
    val positionSecs: Int,
    val durationSecs: Int?,
    val mediaId: String?,
)

/** A command a paired phone sent with `POST /v1/control`. [Seek.seconds] is an absolute position. */
sealed interface PcRemoteCommand {
    data object Play : PcRemoteCommand
    data object Pause : PcRemoteCommand
    data object Toggle : PcRemoteCommand
    data object Next : PcRemoteCommand
    data object Prev : PcRemoteCommand
    data class Seek(val seconds: Long) : PcRemoteCommand

    /** "Continuar en la PC": open [mediaId] at [episode] from [positionSecs] (the phone pushed its history row first). */
    data class Open(val mediaId: String, val episode: Int, val positionSecs: Int) : PcRemoteCommand
}

/** The PC's player as the Link server sees it: its state and the commands phones send it. */
interface PcPlayerRemote {
    /** null = no player open. */
    val state: StateFlow<PcPlayerState?>

    /** false = nobody can take it (no player open, for a transport command). [PcRemoteCommand.Open] is always taken. */
    fun command(cmd: PcRemoteCommand): Boolean
}

/**
 * Where the open player screen plugs in ([attach] / [publish] / [detach], one owner at a time: the newest wins) and where the app
 * listens for [opens] ("open" commands: the shell navigates to the player). Thread-safe.
 */
class PcPlayerHub : PcPlayerRemote {
    private val _state = MutableStateFlow<PcPlayerState?>(null)
    override val state: StateFlow<PcPlayerState?> = _state.asStateFlow()
    private val _opens = MutableSharedFlow<PcRemoteCommand.Open>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val opens: SharedFlow<PcRemoteCommand.Open> = _opens.asSharedFlow()

    private var owner: Any? = null
    private var handler: ((PcRemoteCommand) -> Boolean)? = null

    /** The player screen [owner] takes the commands; it replaces any earlier one. */
    @Synchronized
    fun attach(owner: Any, handler: (PcRemoteCommand) -> Boolean) {
        this.owner = owner
        this.handler = handler
    }

    /** What [owner] shows now (ignored unless it is the attached player). */
    @Synchronized
    fun publish(owner: Any, state: PcPlayerState?) {
        if (this.owner === owner) _state.value = state
    }

    /** [owner] left the screen: no player any more (unless another one took over meanwhile). */
    @Synchronized
    fun detach(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        handler = null
        _state.value = null
    }

    override fun command(cmd: PcRemoteCommand): Boolean {
        if (cmd is PcRemoteCommand.Open) {
            _opens.tryEmit(cmd)
            return true
        }
        val h = synchronized(this) { handler } ?: return false
        return h(cmd)
    }
}

/** A paired phone "Enviar a dispositivo" can send to. */
data class PhoneTarget(val id: String, val name: String, val online: Boolean)

/** What to send: the episode, where to resume, and the source the phone's session will read ([LinkSource.Url] or [LinkSource.Torrent]). */
data class SendToPhoneRequest(
    val mediaId: String?,
    val episode: Int,
    val positionSecs: Int,
    val title: String,
    val source: LinkSource,
)

/** "Enviar a dispositivo" on the PC: a Link session for the phone plus the hand-off its events long-poll delivers. */
interface SendToPhone {
    /** Paired, active phones (empty while the Link server is off). */
    val phones: StateFlow<List<PhoneTarget>>

    /** Fails with the Link error code as message (`source_unavailable`, `url_source_denied`, `device_not_authorized`…). */
    suspend fun send(deviceId: String, request: SendToPhoneRequest): Result<Unit>
}
