import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Shared feature module = kmp library + Compose Multiplatform + what every screen uses
// (core:model, core:designsystem, core:ui, Koin, lifecycle ViewModel).
plugins {
    id("nijiyome.kmp.library")
    id("nijiyome.kmp.compose")
}

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("commonMain").dependencies {
        implementation(project(":core:model"))
        implementation(project(":core:designsystem"))
        implementation(project(":core:ui"))
        implementation(project.dependencies.platform(nijiyomeCatalog.lib("koin-bom")))
        implementation(nijiyomeCatalog.lib("koin-core"))
        implementation(nijiyomeCatalog.lib("koin-compose"))
        implementation(nijiyomeCatalog.lib("koin-compose-viewmodel"))
        implementation(nijiyomeCatalog.lib("cmp-lifecycle-runtime-compose"))
        implementation(nijiyomeCatalog.lib("cmp-lifecycle-viewmodel-compose"))
        implementation(nijiyomeCatalog.lib("kotlinx-coroutines-core"))
    }
    sourceSets.getByName("androidMain").dependencies {
        implementation(nijiyomeCatalog.lib("koin-android"))
        implementation(nijiyomeCatalog.lib("kotlinx-coroutines-android"))
    }
    sourceSets.getByName("desktopMain").dependencies {
        implementation(nijiyomeCatalog.lib("kotlinx-coroutines-swing"))
    }
}
