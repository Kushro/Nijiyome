// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.2), owner: worker r.
// Downloads page (web + torrents + chapters + local library), "Descargar temporada" sheet mounted in Details, Settings › Descargas.
// Shared by Android and desktop.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.serialization.json) // ChapterDownload.localRef = JSON of the reader args
        }
        androidMain.dependencies {
            implementation(libs.androidx.documentfile) // name of the chosen download folder (Settings › Descargas)
        }
    }
}
