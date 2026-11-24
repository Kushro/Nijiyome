// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.5), owner: worker s.
// Manga home, details, chapter list, reader (paged / vertical / webtoon), readlist tab of Library, Settings › Lector.
// Shared by Android and desktop.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // MangaCatalog / chapter logic / keymap of the readers (core module, no feature -> feature dependency).
            implementation(projects.core.manga)
            implementation(libs.coil.compose)
            implementation(libs.cmp.material.icons.extended)
        }
        androidMain.dependencies {
            // NetworkHeaders / httpHeaders for page images that need the extension's Referer (coil-network-core through the OkHttp fetcher).
            implementation(libs.coil.network.okhttp)
            implementation(libs.androidx.activity.compose) // BackHandler
            implementation(libs.androidx.core.ktx) // WindowCompat (immersive reader)
        }
        getByName("desktopMain").dependencies { implementation(libs.coil.network.okhttp) }
        // v1.1 (F6): the border crop and the page capture tests run Skia (its native library for this machine).
        getByName("desktopTest").dependencies { implementation(compose.desktop.currentOs) }
    }
}
