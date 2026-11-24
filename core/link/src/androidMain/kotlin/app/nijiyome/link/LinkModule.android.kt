// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/**
 * Android: Koin bindings of Nijiyome Link. Requires `androidContext()`. The key is sealed under `noBackupFilesDir` (review E4).
 * parity wave B (16 §3.11, worker r): mDNS discovery ([NsdPcDiscovery]), the foreground gate of the `GET /v1/events` long-poll
 * ([AppForeground]) and the "Enviar a dispositivo" notification ([AndroidLinkHandoffNotifier]).
 */
val linkModule: Module = linkModule(
    vault = { LinkKeyVault(androidContext().noBackupFilesDir, AndroidKeystoreKey.cipher()) },
    repository = { vault ->
        val app = androidContext().applicationContext as? Application
        LinkRepositoryImpl(
            vault = vault,
            pcDiscovery = NsdPcDiscovery(androidContext()),
            foreground = app?.let { AppForeground(it).visible },
            handoffNotifier = AndroidLinkHandoffNotifier(androidContext()),
            decodeLimits = { AvcDecodeLimits.device() },
        )
    },
)
