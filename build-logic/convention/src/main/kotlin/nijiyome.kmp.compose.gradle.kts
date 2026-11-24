import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Apply AFTER nijiyome.kmp.library: Compose Multiplatform for a shared UI module.
// Strings, fonts and images shared by both platforms live in src/commonMain/composeResources and are read through the
// generated `Res` class of the module (package `<namespace>.resources`).
plugins {
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<KotlinMultiplatformExtension> {
    sourceSets.getByName("commonMain").dependencies { implementation(nijiyomeCatalog.bundle("cmp-core")) }
    // Android compiles the shared UI against the Jetpack Compose BOM and material3 the app runs with (Compose Multiplatform's
    // Android artifacts point at older Jetpack versions, which Gradle upgrades in the APK): a source change between the two then
    // fails the build instead of the phone at run time. Desktop keeps Compose Multiplatform's own artifacts.
    sourceSets.getByName("androidMain").dependencies {
        implementation(project.dependencies.platform(nijiyomeCatalog.lib("compose-bom")))
        implementation(nijiyomeCatalog.lib("compose-material3"))
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = project.nijiyomeNamespace() + ".resources"
    generateResClass = auto
}
