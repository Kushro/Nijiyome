plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

configureJvmCommon()

dependencies {
    add("testImplementation", nijiyomeCatalog.bundle("jvm-test"))
}
