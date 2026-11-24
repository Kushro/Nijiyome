// Extension screens (installed, catalog, repositories, permissions, log, PC servers). Shared by Android and desktop.
// The .kai picker is core:ui's rememberFilePicker; the SAF display name and DateUtils relative time are in androidMain.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // parity wave B closure (16 §3.4, worker t; server.api.extensions): ExtensionServerRepository.extensions for the PC servers section
            implementation(projects.core.extensionServer)
        }
        // 1.1 F4: the desktop hidden browser (whether the extension card says it is limited)
        getByName("desktopMain").dependencies { implementation(projects.core.extensionsQuickjs) }
    }
}
