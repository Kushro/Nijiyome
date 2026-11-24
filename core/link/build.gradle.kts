// The client of Nijiyome Link (the phone against the PC): protocol, HTTP/mTLS, loopback proxy, pairing and key vault in commonMain;
// AndroidKeyStore sealing, NsdManager discovery, the foreground gate, the "Enviar a dispositivo" notification and the H.264
// decoder limits in androidMain. Desktop is not a Link client yet (it will be the server): its bindings always read as unpaired.
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
            // parity wave B (worker r): NotificationCompat of the "Enviar a dispositivo" notification
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}
