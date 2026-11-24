// The app shell shared by Android and desktop: the routes, the adaptive navigation (bottom bar / rail), the Navigation 3 back
// stack and the screen graph. The Android app and the desktop app add only what belongs to their platform (Activity, window,
// dependency graph).
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Route is a NavKey and part of this module's API (the Android app builds routes from intents).
            api(libs.navigation3.runtime)
            implementation(libs.cmp.navigation3.ui)
            implementation(libs.cmp.lifecycle.viewmodel.navigation3)
            implementation(libs.cmp.material3.adaptive)
            implementation(libs.cmp.material3.adaptive.layout)
            implementation(libs.cmp.material3.adaptive.navigation)
            implementation(libs.cmp.material3.adaptive.navigation3)
            implementation(libs.cmp.material3.adaptive.navigation.suite)
            implementation(libs.cmp.material.icons.extended)
            implementation(libs.kotlinx.serialization.json)

            // LibraryTab is part of Route.LibraryTab.
            api(projects.feature.library)
            implementation(projects.feature.home)
            implementation(projects.feature.search)
            implementation(projects.feature.details)
            implementation(projects.feature.settings)
            implementation(projects.feature.extensions)
            implementation(projects.feature.link)
            implementation(projects.feature.player)
            implementation(projects.feature.torrents)
            implementation(projects.feature.you)
            implementation(projects.feature.downloads)
            implementation(projects.feature.manga)
            implementation(projects.feature.novel)
            implementation(projects.feature.social)
            // ExtensionServerRepository: what the extension servers screen needs (RouteAvailability).
            implementation(projects.core.extensionServer)
        }
    }
}
