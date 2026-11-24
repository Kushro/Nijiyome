// Home, calendar and "For you". Shared by Android and desktop.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            // TrendingFeed (Top 10 windows), same as :feature:search
            implementation(projects.core.metadata)
        }
    }
}
