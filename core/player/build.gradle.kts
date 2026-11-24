// B4 (m3e/android-media): player core. Depends on :core:link only for RemotePlaybackAccess (mTLS client of the paired PC).
// parity wave A (15 §3.2, worker p): kotlinx-serialization-json for the subtitle translators / cache, mockwebserver3 for their tests
// (both already in the catalog).
// Milestone 3: the engine-neutral logic (PlayerSession, source flow, subtitles, translation, AniSkip, quality models) is in commonMain
// for the desktop player (libmpv, a later milestone); Media3 / ExoPlayer, the media session service, Intents, MediaCodec,
// SharedPreferences and the Android string resources stay in androidMain.
plugins { id("nijiyome.kmp.library") }

kotlin {
    android {
        // Java JVM stand-ins of android.os.Build / android.util.* for Media3's extractors in host tests (src/androidHostTest/java).
        withJava()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            // 1.1 (F7): `score` of the source ranking, to re-rank once the debrid cache is known (no cycle: :core:release needs only :core:model)
            implementation(projects.core.release)
            implementation(projects.core.link)
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.bundles.media3)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        // Settings › Red (1.1): the player's client takes the outbound proxy on desktop
        getByName("desktopMain").dependencies { implementation(projects.core.network) }
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}
