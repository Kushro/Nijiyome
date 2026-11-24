import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared module: Android (through the Android-KMP library plugin, AGP 9) + desktop JVM.
// Source sets: commonMain, androidMain, desktopMain; tests in androidHostTest and desktopTest.
// commonMain is "shared by Android and desktop", not "platform-neutral": both targets are JVM, so it may use the JDK and
// JVM-only libraries (OkHttp, jsoup, java.time). Kotlin cannot express a JVM + Android shared source set (KT-42466), so the
// metadata compilation of commonMain, which would reject those references, is disabled. Nothing here is published as a library.
// The Android-KMP plugin has no build types, flavours, BuildConfig or CMake: modules that need those stay on
// nijiyome.android.library.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    android {
        namespace = project.nijiyomeNamespace()
        compileSdk = NijiyomeAndroid.COMPILE_SDK
        minSdk = NijiyomeAndroid.MIN_SDK
        androidResources { enable = true }
        withHostTest { isReturnDefaultValues = true }
        compilerOptions { jvmTarget.set(NijiyomeAndroid.JVM_TARGET) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(NijiyomeAndroid.JVM_TARGET) }
    }
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        getByName("androidHostTest").dependencies { implementation(nijiyomeCatalog.bundle("jvm-test")) }
        getByName("desktopTest").dependencies { implementation(nijiyomeCatalog.bundle("jvm-test")) }
    }
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("KotlinMetadata") }.configureEach { enabled = false }
