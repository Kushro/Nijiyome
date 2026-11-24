// Library: continue watching, lists, favorites, history and the manga / novel / torrents tabs. Shared by Android and desktop.
// The platform relative time ("hace 5 min", DateUtils) is in androidMain; desktop uses core:ui's.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // android wave (12-android-wave-contract.md): TorrentsLibraryTab (worker f)
            implementation(projects.feature.torrents)
            // parity wave B (16 §3.5 / §3.6, worker s): MangaLibraryTab / NovelLibraryTab (LibraryTab.MANGA / NOVELS). No cycle.
            implementation(projects.feature.manga)
            implementation(projects.feature.novel)
            implementation(libs.cmp.material.icons.extended)
        }
    }
}
