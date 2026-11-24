// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/** Android: the 18+ decisions and the episode view mode in SharedPreferences. */
val detailsModule: Module = detailsModule { SharedPrefsDetailsPrefs(androidContext()) }
