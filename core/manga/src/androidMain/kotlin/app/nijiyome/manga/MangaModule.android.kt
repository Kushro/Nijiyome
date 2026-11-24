// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.manga

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/** Android: the manga store lives in the app's private files directory. Requires `androidContext()`. */
val mangaModule: Module = mangaModule { androidContext().filesDir }
