// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/** Desktop: the novel screens, with the reader's imported fonts in `<dataDir>/fonts/novel`. */
fun desktopNovelFeatureModule(dataDir: File): Module = module {
    includes(novelFeatureModule)
    single { NovelFontsDir(File(dataDir, FontFiles.DIR)) }
}
