// android wave contract (docs/redesign/12-android-wave-contract.md), owner: worker f.
// libtorrent4j 2.1.0-39 session + loopback HTTP streaming server + TorrentService (foreground) + TorrentRepository impl (Koin torrentModule).
// Multiplatform (docs/patron-modulo-kmp.md): the engine wrapper, policies, stores, streaming server and debrid clients are shared;
// the service, notifications, storage/device monitors and TorrentRepositoryImpl (Context/Uri) stay in androidMain.
// libtorrent4j: the Java/SWIG classes are shared, each platform brings its natives (docs/spikes/nativos.md).
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.release)
            implementation(libs.libtorrent4j)
            // parity wave A (15 §3.9, worker q): debrid HTTPS clients over the shared OkHttpClient.
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        // 1.1 (F7): the fake TorBox of the debrid tests
        commonTest.dependencies { implementation(libs.mockwebserver3) }
        androidMain.dependencies {
            implementation(libs.libtorrent4j.android.arm64)
            implementation(libs.libtorrent4j.android.arm)
            implementation(libs.libtorrent4j.android.x64)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.service)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        getByName("desktopMain").dependencies {
            // Settings › Red (1.1): the SOCKS5 outbound proxy of the session
            implementation(projects.core.network)
            // Natives only: LibtorrentNative unpacks the one of this platform and points libtorrent4j.jni.path to it.
            implementation(libs.libtorrent4j.linux)
            implementation(libs.libtorrent4j.windows)
        }
    }
}
// libtorrent4j's native library replaces the JVM's SIGSEGV / SIGBUS handlers once it has done disk work, and HotSpot's own
// deliberate faults then kill the JVM (exit 139, no crash report). HotSpot's signal-chaining library keeps the JVM's handlers first;
// the desktop app preloads it the same way (desktopApp SignalChaining).
tasks.matching { it.name == "desktopTest" }.configureEach {
    val test = this as Test
    test.environment("LD_PRELOAD", test.javaLauncher.get().metadata.installationPath.file("lib/libjsig.so").asFile.absolutePath)
}
