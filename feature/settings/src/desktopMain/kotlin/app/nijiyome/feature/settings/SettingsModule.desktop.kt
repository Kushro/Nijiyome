// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import coil3.PlatformContext
import org.koin.core.module.Module

/**
 * Desktop: Settings with Coil's image disk cache. Needs settings; history, metadata, torrents and Link are optional. Without
 * ListsRepository, LogRepository, UpdateRepository and AppReset (not bound on desktop yet) Listas y cuentas, Registros, Buscar
 * actualizaciones and Restablecer la app are hidden.
 */
fun desktopSettingsModule(): Module = settingsModule { CoilImageCache(PlatformContext.INSTANCE) }
