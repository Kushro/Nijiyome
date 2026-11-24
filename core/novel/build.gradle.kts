// parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §3.6), owner: worker s.
// Light novels: home / details through metadata (AniList MANGA format NOVEL), chapters through `.kai` custom `novel` sources, progress,
// readlist, chapter text downloads, narration (system TextToSpeech; OpenAI / ElevenLabs / OpenAI-compatible audio played with Media3).
// Shared by Android and desktop: Android narration (TextToSpeech, Media3) and the SQLite text store are Android only; desktop narrates
// with speech-dispatcher / espeak-ng / SAPI, the shared cloud engines and KittenTTS, played with libmpv (1.1).
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            // Shared private clients and stores of the readers (AniList manga API, ReadlistStore, JsonFileStore, chapter logic).
            api(projects.core.manga)
            implementation(libs.okhttp)
            implementation(libs.jsoup)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.media3.common)
            implementation(libs.media3.exoplayer)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        // 1.1: the desktop narrator plays its clips with an audio-only libmpv session of the desktop player (no new dependency).
        getByName("desktopMain").dependencies {
            implementation(projects.core.player)
            implementation(projects.core.playerMpv)
        }
        getByName("desktopTest").dependencies { implementation(libs.kotlinx.coroutines.swing) }
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}

// The desktop tests never reach the user's session or speakers: headless AWT, no display variables, and libmpv plays to ao=null /
// ao=pcm (a file). NIJIYOME_TEST_ESPEAK / NIJIYOME_TEST_KITTEN_DIR / NIJIYOME_TEST_AUDIO_OUT point the engine tests at a phonemizer,
// a KittenTTS install and the folder for the generated clips (the tests are skipped without them).
tasks.matching { it.name == "desktopTest" }.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    setEnvironment(System.getenv().filterKeys { it !in setOf("DISPLAY", "WAYLAND_DISPLAY", "XAUTHORITY", "SPEECHD_ADDRESS") })
}
