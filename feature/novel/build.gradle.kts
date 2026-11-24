// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.6), owner: worker s.
// Novel home, details, chapter list, reader with typography + narration controls, readlist tab of Library, Settings › Lector de novela.
// Shared by Android and desktop.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // NovelRepositoryImpl extras, Narrator errors, chapter helpers; core/novel exposes core/manga (MangaCatalog) as api.
            implementation(projects.core.novel)
            implementation(libs.coil.compose)
            implementation(libs.cmp.material.icons.extended)
        }
    }
}
