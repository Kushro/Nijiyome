// Details ("Ficha") of a title: header layouts, episodes, sources, characters and staff, related, recommendations, relations
// graph, character page and "Enviar a dispositivo". Shared by Android and desktop. Desktop has no LinkRepository yet: "Enviar a
// dispositivo" is hidden there (PENDIENTE-ESCRITORIO).
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            // android wave (12-android-wave-contract.md): SourcePickerSheet/SourceRow (worker d) and rememberDownloadGate (worker f)
            implementation(projects.feature.player)
            implementation(projects.feature.torrents)
            // parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.3): ListButton (worker q) mounted in the Details header
            implementation(projects.feature.library)
            // BUG-023: the exact Link error of "Enviar a dispositivo" (url_source_denied, source_unavailable…) for a clear message.
            implementation(projects.core.link)
            // parity wave B (docs/redesign/16-parity-wave-b-contract-android.md §3.1 / §3.2 / §3.7): the three slots of WaveBSlots —
            // FollowShowRow (r, :feature:home), DownloadSeasonSlot (r, :feature:downloads), AniListSocialSlot (t, :feature:social).
            // No cycle: none of the three depends on :feature:details.
            implementation(projects.feature.home)
            implementation(projects.feature.downloads)
            implementation(projects.feature.social)
        }
    }
}
