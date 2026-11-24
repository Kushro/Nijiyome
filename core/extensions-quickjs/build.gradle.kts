// The extension host: QuickJS engine, repository, catalog and Stremio addons, for Android and desktop.
// The hidden browser: WebViews on Android (WebViewBrowserRuntime, WebViewEmbedSniffer), the user's Chromium over CDP on desktop.
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.extensions)
            implementation(projects.core.model)
            // quickjs-kt 1.0.18: the KMP coordinate resolves to quickjs-kt-android on Android and quickjs-kt-jvm (Linux/Windows natives) on desktop.
            implementation(libs.quickjs.kt)
            implementation(libs.jsoup)
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            // android wave (12-android-wave-contract.md): ranking of candidates (worker a's :core:release)
            implementation(projects.core.release)
        }
        androidMain.dependencies {
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        // Settings › Red (1.1): the extensions' client takes the outbound proxy and FlareSolverr on desktop
        getByName("desktopMain").dependencies {
            implementation(projects.core.network)
            // 1.1 review #10: the hidden browser's debugging pipe on Windows (anonymous pipes), already in the catalog
            implementation(libs.jna.platform)
        }
        // 1.1 F4: the desktop sniffer blocks the same ad hosts as Android's (the asset folder holds only adblock-hosts.txt)
        getByName("desktopMain").resources.srcDir("src/androidMain/assets")
        // parity wave B (16 §3.4, worker t): repository / Stremio parsers tested against a fake HTTP server (catalog entry, no new dependency).
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}
