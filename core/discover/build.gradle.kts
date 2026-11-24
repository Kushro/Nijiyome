// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.1), owner: worker r.
// Airing calendar + followed shows + local reminders (WorkManager) and the on-device recommendations ("Para ti", atlas).
// Shared by Android and desktop: the repositories, the engine and the JSON stores are in commonMain; the reminder worker, its
// scheduler and its notifications (WorkManager, androidx.core) are Android-only.
// Everything below is already in the catalog; new dependencies go to NOTES-deps.md of this module.
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
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
    }
}
