// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android: Settings with Coil's image disk cache of the app context (Listas y cuentas is declared by the shared module). */
val settingsModule: Module = module {
    includes(settingsModule { CoilImageCache(androidContext().applicationContext) })
}
