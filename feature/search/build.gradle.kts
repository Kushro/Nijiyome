// Search ("Buscar"): AniList filters, feeds, trace.moe image search, studio pages and the discovery rows mounted by Settings.
// Shared by Android and desktop. Recent searches and reading the picked image are per platform (androidMain / desktopMain).
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // parity wave A (docs/redesign/15 §3.1, worker o): TrendingFeed / TrendWindow (Tendencias window selector).
            implementation(projects.core.metadata)
            // Picked-image and trace.moe preview frames.
            implementation(libs.coil.compose)
            // Character search results open the character page (CharacterScreen of :feature:details) in place: SearchScreen has no
            // character callback in the contract. No cycle: :feature:details does not depend on :feature:search.
            implementation(projects.feature.details)
        }
        // Desktop image search: a pasted / dropped http(s) link is downloaded with the app's HTTP client.
        getByName("desktopMain").dependencies { implementation(libs.okhttp) }
        getByName("desktopTest").dependencies { implementation(libs.mockwebserver3) }
    }
}
