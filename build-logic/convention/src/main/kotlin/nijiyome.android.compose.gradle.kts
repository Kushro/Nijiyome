import com.android.build.api.dsl.CommonExtension

// Apply AFTER nijiyome.android.library / nijiyome.android.application.
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<CommonExtension>("android") {
    buildFeatures.compose = true
}

dependencies {
    add("implementation", platform(nijiyomeCatalog.lib("compose-bom")))
    add("debugImplementation", nijiyomeCatalog.lib("compose-ui-tooling"))
}
