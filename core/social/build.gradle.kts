// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.7), owner: worker t.
// AniList social (profile, follow, activity, notifications by polling, reviews) over graphql.anilist.co with the token of :core:data's
// ListsRepository. No own backend. Dependencies already in the catalog; new ones -> NOTES-deps.md.
// commonMain: the AniList client, parsers and repository (Android + desktop). androidMain: WorkManager polling and the system
// notification, with their Android string resources.
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
            implementation(libs.work.runtime)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}
