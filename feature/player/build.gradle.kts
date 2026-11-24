// B4 (m3e/android-media): player screen. Media3-Compose / M3E APIs used here were checked against .deps-sources (review E2).
// Milestone 3: the pieces other screens use (the source picker / rows of :feature:details, PlayerSettingsExtras and the subtitle
// style editor of :feature:settings), the end screen, the processing / Link logic and the pure UI models are in commonMain. The
// screen itself (PlayerViewModel over Media3 / libmpv, the video surface, PiP, gestures, the "Mejoras" panel with frame
// generation, the overlays that read Android resources of :core:player / :core:framegen) stays in androidMain behind
// `expect fun PlayerScreen`; desktop shows "not available yet" until the desktop player (libmpv, a later milestone).
// Milestone 4: the screen itself (body, controls, sheets, overlays, "Mejoras" panel) is in commonMain too; androidMain keeps the
// platform slots of PlayerScreenPlatform.kt (Media3 / libmpv surfaces, gestures, window, PiP, volume, screenshots) and the phone
// frame generation (:core:framegen and its Android texts). The quality texts moved here from :core:player's Android resources.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.player)
            // Milestone 3: PlayerViewModel (commonMain) names OkHttpClient (a source's own client for its side-loaded subtitles)
            implementation(libs.okhttp)
            implementation(projects.core.link)   // RemotePlaybackAccess (Link HLS + heartbeat)
            implementation(projects.feature.link) // BUG-022: the Link profile names / list for the in-player profile sheet
            // android wave (12-android-wave-contract.md): libmpv engine + surface (worker d)
            implementation(projects.core.playerMpv)
            implementation(libs.cmp.material.icons.extended)
            // 0.4.1: the end screen's blurred poster backdrop (MediaCover of :core:ui keeps a fixed aspect ratio)
            implementation(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.bundles.media3)
            implementation(libs.media3.ui.compose)
            implementation(libs.androidx.activity.compose)
            // 0.4.0 (worker D): the phone frame generation state / commands the "Mejoras" panel draws (FrameGenController)
            implementation(projects.core.framegen)
        }
        getByName("desktopMain").dependencies {
            // v1.1 (F2): MPRIS on the session bus (Linux system media controls), JDK unix sockets as transport
            implementation(libs.dbus.java.core)
            implementation(libs.dbus.java.transport.unixsocket)
        }
    }
}
