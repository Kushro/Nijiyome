// Settings as one sectioned screen, plus the first-run wizard. Shared by Android and desktop.
// What stays in androidMain (behind internal `expect`s of this module): the Custom Tab OAuth of AniList / MAL and the SAF import /
// export of Listas y cuentas (desktop: the loopback login and the file picker), the SAF export of the history, sharing the log through the FileProvider, the installed external players, the app version, the bundled
// licenses (assets), the notification permission step, recreating the activity after "Restablecer la app", the phone frame generation
// rows (:core:framegen, Android only).
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.material.icons.extended)
            // android wave (12-android-wave-contract.md): TorrentSettingsPage (worker f)
            implementation(projects.feature.torrents)
            // parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.2): PlayerSettingsExtras (worker p's rows inside
            // Settings › Reproductor) and the subtitle style editor.
            implementation(projects.feature.player)
            // parity wave A (worker q): PendingOAuthRedirect / ListErrors / OAuthRedirect of :core:data (Listas y cuentas).
            // No cycle: :core:data depends on no feature module.
            implementation(projects.core.data)
            // parity wave A closure (worker q, checklist system.clear-cache): "Limpiar caché" also clears Coil's image disk cache through
            // SingletonImageLoader.
            implementation(libs.coil.compose)
            // parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.9): DownloadsSettingsSection (r), ReaderSettingsSection /
            // NovelReaderSettingsSection (s), SourcesSettingsExtras (r, :feature:extensions). No cycle: none of them depends on :feature:settings.
            implementation(projects.feature.downloads)
            implementation(projects.feature.manga)
            implementation(projects.feature.novel)
            implementation(projects.feature.extensions)
            // parity wave A (15 §3.1): DiscoverSettingsExtras (worker o's rows inside Metadatos).
            implementation(projects.feature.search)
        }
        androidMain.dependencies {
            // SAF launchers (CreateDocument / OpenDocument), the notification permission request.
            implementation(libs.androidx.activity.compose)
            // Custom Tabs for the AniList / MAL OAuth (worker q).
            implementation(libs.androidx.browser)
            // FileProvider of "Abrir registros" › Compartir, ContextCompat.
            implementation(libs.androidx.core.ktx)
            // 0.4.0 player quality (worker B): Settings › Reproductor › Generación de cuadros en el teléfono (FrameGenSettingsSection).
            implementation(projects.core.framegen)
        }
        getByName("desktopMain").dependencies {
            // 1.1 F4: the desktop hidden browser (state, Settings path and "Probar")
            implementation(projects.core.extensionsQuickjs)
            // Hito 4, paso D: Settings › Reproductor for mpv in its own window (its settings store, mpv-anime-build, lsfg-vk).
            implementation(projects.core.playerMpv)
        }
    }
}
