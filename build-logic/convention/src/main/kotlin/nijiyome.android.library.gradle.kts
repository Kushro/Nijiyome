import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<LibraryExtension> {
    namespace = project.nijiyomeNamespace()
    configureAndroidCommon(this)
}

dependencies {
    add("testImplementation", nijiyomeCatalog.bundle("jvm-test"))
}
