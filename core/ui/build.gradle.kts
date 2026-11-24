// Shared UI building blocks (cards, covers, labels, formatters, load states) for Android and desktop.
plugins {
    id("nijiyome.kmp.library")
    id("nijiyome.kmp.compose")
}

// Other modules read these strings (nav labels, model labels, error messages): the generated Res class is public.
compose.resources {
    publicResClass = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            api(projects.core.model)
            implementation(libs.coil.compose)
            implementation(libs.cmp.material3.adaptive)
            implementation(libs.cmp.material3.adaptive.layout)
        }
        androidMain.dependencies {
            implementation(libs.coil.network.okhttp)
            implementation(libs.androidx.activity.compose) // permission launcher
            implementation(libs.androidx.core.ktx)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.coil.network.okhttp)
            // Windows: SetThreadExecutionState for KeepScreenOn (already in the app for DPAPI, core:data).
            implementation(libs.jna.platform)
        }
    }
}
