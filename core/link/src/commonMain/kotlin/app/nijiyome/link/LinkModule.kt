// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

import app.nijiyome.model.LinkRepository
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Koin bindings of Nijiyome Link for a platform: [vault] is where the paired identity lives (Android: sealed with AndroidKeyStore
 * under `noBackupFilesDir`, review E4) and [repository] builds the client with that platform's discovery, foreground gate,
 * notifier and decoder limits. Android: `val linkModule` (androidMain).
 * parity wave A (2026-11-09 amendment): [LinkHistoryRemote] is the history peer consumed by :core:data's `HistorySyncer` (worker q).
 * parity wave B (16 §3.11, worker r): [LinkDiscoveryAccess] for :feature:link.
 */
fun linkModule(vault: Scope.() -> LinkKeyVault, repository: Scope.(LinkKeyVault) -> LinkRepositoryImpl): Module = module {
    single { vault() }
    single { repository(get()) }
    single<LinkRepository> { get<LinkRepositoryImpl>() }
    single<RemotePlaybackAccess> { get<LinkRepositoryImpl>() }
    single<LinkDiscoveryAccess> { get<LinkRepositoryImpl>() }
    single { LinkHistoryRemoteImpl(get<LinkRepositoryImpl>()) }
    single<LinkHistoryRemote> { get<LinkHistoryRemoteImpl>() }
    // 2026-11 history per device: this phone's mode on the PC + "resync running", read by :feature:link
    single<LinkHistoryStatus> { get<LinkHistoryRemoteImpl>() }
}
