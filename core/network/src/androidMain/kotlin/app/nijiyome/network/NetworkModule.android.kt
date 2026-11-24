// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.network

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/** Android: HTTP cache in the app's cache directory. */
val networkModule: Module = networkModule(platform = "Android") { androidContext().cacheDir }
