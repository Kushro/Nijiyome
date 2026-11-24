// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.5), owner: worker s.
// Manga: metadata extras (AniList / MangaDex / MangaUpdates), chapters through `.kai` custom `read` sources, reading progress,
// readlist (local + AniList progress), chapter downloads (pages on disk). Shared by Android and desktop; new dependencies -> NOTES-deps.md.
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}
