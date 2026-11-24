// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.2), owner: worker r.
// Web downloads (HLS / MP4) and the local library. Multiplatform (docs/patron-modulo-kmp.md): the rules, names and JSON stores are
// shared; Android keeps the Media3 DownloadManager + NijiyomeDownloadService (foreground, dataSync), the auto-download worker
// (WorkManager) and SAF; desktop has its own engine over OkHttp (HLS segments and ranged files, resumable) and a watched library folder.
// New dependencies go to NOTES-deps.md of this module.
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            // release-name parser for the local library import / scan matching
            implementation(projects.core.release)
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.bundles.media3)
            implementation(libs.media3.database)
            implementation(libs.media3.datasource)
            implementation(libs.work.runtime)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.mockwebserver3)
        }
    }
}
