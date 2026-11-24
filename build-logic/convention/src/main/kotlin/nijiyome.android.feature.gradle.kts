// feature module = library + compose + the common UI dependencies (core:ui, core:designsystem, core:model, Koin, lifecycle).
plugins {
    id("nijiyome.android.library")
    id("nijiyome.android.compose")
}

dependencies {
    add("implementation", project(":core:model"))
    add("implementation", project(":core:designsystem"))
    add("implementation", project(":core:ui"))
    add("implementation", nijiyomeCatalog.bundle("compose-core"))
    add("implementation", platform(nijiyomeCatalog.lib("koin-bom")))
    add("implementation", nijiyomeCatalog.bundle("koin-android"))
    add("implementation", nijiyomeCatalog.lib("androidx-lifecycle-runtime-compose"))
    add("implementation", nijiyomeCatalog.lib("androidx-lifecycle-viewmodel-compose"))
    add("implementation", nijiyomeCatalog.lib("kotlinx-coroutines-android"))
}
