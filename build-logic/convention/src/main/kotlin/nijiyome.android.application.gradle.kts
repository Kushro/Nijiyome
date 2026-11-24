import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
}

extensions.configure<ApplicationExtension> {
    defaultConfig.targetSdk = NijiyomeAndroid.TARGET_SDK
    configureAndroidCommon(this)
}

dependencies {
    add("testImplementation", nijiyomeCatalog.bundle("jvm-test"))
}

