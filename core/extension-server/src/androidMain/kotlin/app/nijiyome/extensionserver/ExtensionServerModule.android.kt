// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensionserver

import android.os.Build
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import java.io.File

/**
 * Android: Koin bindings of the extension server client (docs/redesign/13 §3.10). Requires `androidContext()` and the shared
 * `OkHttpClient`. The connections are sealed with [ExtensionServerKey] in one file under `noBackupFilesDir`.
 */
val extensionServerModule: Module = extensionServerModule(
    store = { ExtensionServerStore(File(androidContext().noBackupFilesDir, STORE_FILE), ExtensionServerKey.cipher()) },
    deviceLabel = {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        "Nijiyome ($model)".take(100)
    },
)

private const val STORE_FILE = "extension-servers.bin"
