// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import app.nijiyome.feature.settings.DesktopAppRestart
import app.nijiyome.feature.settings.DesktopSystemControl
import app.nijiyome.model.AppDataWiper
import app.nijiyome.model.SystemNotifier
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File

/** The executable a system entry (autostart) should start, or null for a development run. */
fun packagedLauncher(env: (String) -> String? = System::getenv): String? =
    env("APPIMAGE")?.takeIf { it.isNotBlank() && File(it).isFile }
        ?: System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() && File(it).isFile }

/**
 * The desktop's system integration: tray / autostart / Discord settings, system notifications and the restart after
 * "Restablecer la app" (Discord Rich Presence itself is started by main). [restarter] is the one [app.nijiyome.desktop.main] watches.
 */
fun desktopSystemModule(dataDir: File, build: BuildInfo, windows: Boolean, restarter: AppRestarter): Module = module {
    single {
        DesktopSystem(
            store = DesktopSystemPrefsStore(File(dataDir, "desktop-system.properties")),
            autostart = Autostart(packagedLauncher(), windows),
            secrets = getOrNull(),
            linkHandlers = LinkHandlers(packagedLauncher(), windows),
            discordConfigured = build.discordAppId != null,
            trayAvailable = DesktopSystem.trayHostPresent(windows),
        )
    } binds arrayOf(DesktopSystemControl::class, AppDataWiper::class)
    single<SystemNotifier> { DesktopNotifier(windows) }
    single { restarter } bind DesktopAppRestart::class
}
