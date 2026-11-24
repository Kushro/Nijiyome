// Repositories. commonMain holds what runs on both platforms (settings, metadata, history, lists and accounts, update check, the
// sealed secret file); Android keeps the Keystore key, the reset and the WorkManager schedulers, desktop its keyring, loopback OAuth
// and reset.
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.metadata)
            implementation(projects.core.database)
            implementation(projects.core.release)
            implementation(projects.core.link)
            api(libs.androidx.datastore.preferences)
            implementation(libs.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.work.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
        }
        getByName("desktopMain").dependencies {
            // DPAPI (Windows) for the secrets key. Apache-2.0 / LGPL-2.1 dual licence; its JNA is the one the desktop app ships.
            implementation(libs.jna.platform)
        }
        getByName("desktopTest").dependencies { implementation(libs.mockwebserver3) }
        getByName("androidHostTest").dependencies { implementation(libs.mockwebserver3) }
    }
}
