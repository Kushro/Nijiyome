// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.link.RemotePlaybackAccess
import app.nijiyome.model.DiscoveredPc
import app.nijiyome.model.LinkCapabilities
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkSource
import app.nijiyome.model.LinkState
import app.nijiyome.model.PairedPc
import app.nijiyome.model.RemoteSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.OkHttpClient

// Milestone 3: what PlayerViewModel gets where no Link client is registered (desktop is not a Link client). Always unpaired: the
// "Procesar en" options stay blocked as on an unpaired phone, and nothing is ever sent to a PC.

/** A [LinkRepository] that is never paired. */
internal object NoLinkRepository : LinkRepository {
    override val state: StateFlow<LinkState> = MutableStateFlow<LinkState>(LinkState.Unpaired).asStateFlow()

    override fun discover(): Flow<List<DiscoveredPc>> = emptyFlow()

    override suspend fun pair(payload: String, deviceName: String): Result<PairedPc> = Result.failure(UnsupportedOperationException("pair"))

    override suspend fun capabilities(): Result<LinkCapabilities> = Result.failure(UnsupportedOperationException("capabilities"))

    override suspend fun startSession(source: LinkSource, profileId: String?): Result<RemoteSession> =
        Result.failure(UnsupportedOperationException("startSession"))

    override suspend fun stop(sessionId: String) = Unit

    override suspend fun forget() = Unit
}

/** A [RemotePlaybackAccess] without a paired PC (no mTLS client, no sessions). */
internal object NoRemotePlayback : RemotePlaybackAccess {
    override fun playbackClient(): OkHttpClient? = null

    override fun session(sessionId: String): RemoteSession? = null

    override suspend fun heartbeat(sessionId: String, positionSecs: Double, paused: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("heartbeat"))
}
