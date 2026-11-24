// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.search

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/** Android: recent searches in SharedPreferences; the picked / shared picture is read through the ContentResolver. */
val searchModule: Module = searchModule(
    history = { SharedPrefsSearchHistoryStore(androidContext()) },
    reader = { ContentImageReader(androidContext()) },
)
