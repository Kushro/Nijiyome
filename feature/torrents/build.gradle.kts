// android wave contract (docs/redesign/12-android-wave-contract.md), owner: worker f.
// Torrent client screens (Library tab, detail Overview/Files/Peers/Trackers, Downloads + "Entiendo" warning, Torrent settings page).
// Shared by Android and desktop (docs/patron-modulo-kmp.md). On desktop the engine is the same (desktopTorrentModule of :core:torrent);
// without libtorrent4j's native library the screens show their empty / unavailable states.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.cmp.material3.adaptive)
            implementation(libs.cmp.material3.adaptive.layout)
            implementation(libs.coil.compose)
            // parity wave A (worker q): PendingMagnetLink, DebridRepositoryImpl (preference order) and the alignment preview rules of :core:torrent.
            // No cycle: :core:torrent depends only on :core:model and :core:release.
            implementation(projects.core.torrent)
            // parity wave A (worker q): release parser (episode numbers of the alignment preview, already a dependency of :core:torrent).
            implementation(projects.core.release)
        }
        androidMain.dependencies {
            implementation(libs.androidx.documentfile) // SAF tree name in Settings › Torrents › Almacenamiento
        }
    }
}
