// Root build. Conventions live in build-logic/ (nijiyome.* plugins).
// AGP 9 compiles Kotlin itself (built-in Kotlin): the org.jetbrains.kotlin.android plugin is not applied anywhere.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// One task that checks everything a change can break: every unit test (Android modules, JVM modules and both targets of the
// multiplatform ones), the debug APK and the desktop distribution.
//   ./gradlew verificar
tasks.register("verificar") {
    group = "verification"
    description = "All unit tests, the debug APK and the desktop distribution."
    dependsOn(":app:assembleDebug", ":desktopApp:createDistributable")
    dependsOn(
        subprojects.map { p ->
            p.tasks.matching { t ->
                t.name == "testDebugUnitTest" || t.name == "desktopTest" || t.name == "testAndroidHostTest" ||
                    (t.name == "test" && p.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm"))
            }
        },
    )
}
