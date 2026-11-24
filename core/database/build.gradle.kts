// Room database (history, feed cache, source stats, lists), shared by Android and desktop.
plugins {
    id("nijiyome.kmp.library")
    alias(libs.plugins.ksp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            api(libs.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies { implementation(libs.koin.android) }
        getByName("desktopMain").dependencies { implementation(libs.sqlite.bundled) }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
