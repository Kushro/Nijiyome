// android wave contract (docs/redesign/12-android-wave-contract.md), owner: worker d.
// libmpv engine (dev.jdtech.mpv:libmpv 1.1.0, class dev.jdtech.mpv.MPVLib) kept out of :core:player so the native library stays optional.
// API reference: .deps-sources/libmpv-1.0.0/MPVLib.signatures.txt (javap of the AAR; the sources jar is empty).
// Milestone 3: the mpv quality logic (QualityPlanner, ShaderCatalog, MpvQualityController over QualityMpvPort, MpvOptions, shader
// names / sources, ThermalPolicy) and MpvSubtitleStyle are in commonMain for the desktop player. The Android engine (MPVLib, the
// session, the Media3 adapter, the AndroidView surface, frame generation, the asset shader installer, the thermal watcher) and the
// bundled shaders / subtitle fonts (src/androidMain/assets) stay in androidMain.
// Milestone 4: the desktop engine (desktopMain) is libmpv embedded through MediaMP (org.openani.mediamp, Apache-2.0; spike in
// docs/spikes/reproductor.md). Its surface is a Compose Desktop composable, so desktopMain adds Compose UI; Android keeps only the
// Compose compiler plus the Compose runtime in commonMain (the compiler requires it on every target) and its own Compose libraries
// in androidMain (the surface).
plugins {
    id("nijiyome.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Milestone 4: desktop reads the bundled shaders and subtitle fonts as classpath resources. Android reads the same files as APK
// assets (src/androidMain/assets, unchanged), so they are copied at build time under this module's package
// (app/nijiyome/player/mpv/shaders/..., .../subfonts/...) instead of being duplicated in git.
val mpvDesktopAssetsRoot = layout.buildDirectory.dir("generated/mpvDesktopAssets")
val mpvDesktopAssets = tasks.register<Sync>("mpvDesktopAssets") {
    from("src/androidMain/assets") { include("shaders/**", "subfonts/**") }
    into(mpvDesktopAssetsRoot.map { it.dir("app/nijiyome/player/mpv") })
}

// The native runtime of the build host only: Compose Desktop packages one OS per build, and each runtime is ~30 MB.
val windowsHost = System.getProperty("os.name").orEmpty().lowercase().contains("win")

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.player)
            implementation(libs.kotlinx.coroutines.core)
            // The Compose compiler runs on every compilation and needs a runtime on the class path (desktop included).
            implementation(libs.cmp.runtime)
            implementation(libs.okhttp) // BUG-059: HlsPrefetch downloads the HLS master (both engines)
        }
        androidMain.dependencies {
            implementation(libs.libmpv)
            // 0.4.0 (worker B): phone frame generation; api so feature/player sees FrameGenController (MpvPlayerSession.frameGen).
            api(projects.core.framegen)
            // parity wave A (15 §3.2, worker p): MpvPlayerAdapter is a Media3 SimpleBasePlayer so PlaybackService serves both engines.
            implementation(libs.media3.common)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose.core)
            implementation(libs.kotlinx.coroutines.android)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.android)
        }
        getByName("desktopMain") {
            resources.srcDir(mpvDesktopAssets.map { mpvDesktopAssetsRoot.get() })
            dependencies {
                // Settings › Red (1.1): the outbound proxy on the Anime build download
                implementation(projects.core.network)
                implementation(libs.mediamp.mpv)
                // v1.1 (F2): libc (malloc_trim) and libva (decoder probe) through JNA, the same JNA the app already ships.
                implementation(libs.jna.platform)
                if (windowsHost) {
                    // PENDIENTE-ESCRITORIO: runtime de Windows sin probar (render D3D11 de MediaMP, ver docs/spikes/reproductor.md §3)
                    runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
                } else {
                    runtimeOnly(libs.mediamp.mpv.runtime.linux.x64)
                }
                implementation(libs.cmp.ui)
                implementation(libs.cmp.foundation)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
    }
}

// The desktop tests never reach the user's session: headless AWT and no display variables (the libmpv integration test plays
// with vo=null and ao=null).
tasks.matching { it.name == "desktopTest" }.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    setEnvironment(System.getenv().filterKeys { it !in setOf("DISPLAY", "WAYLAND_DISPLAY", "XAUTHORITY") })
}
