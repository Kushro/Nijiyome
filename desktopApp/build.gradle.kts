import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Desktop entry point (Linux and Windows): the window, the main(), the dependency wiring and the packaging.
// Screens and logic live in the shared modules.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.network)
    implementation(projects.core.metadata)
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.discover)
    implementation(projects.core.extensions)
    implementation(projects.core.extensionsQuickjs)
    implementation(projects.core.manga)
    implementation(projects.core.novel)
    implementation(projects.core.player)
    implementation(projects.core.playerMpv)
    implementation(projects.core.torrent)
    implementation(projects.core.download)
    implementation(projects.feature.shell)
    implementation(projects.feature.torrents)
    implementation(projects.feature.home)
    implementation(projects.feature.search)
    implementation(projects.feature.details)
    implementation(projects.feature.library)
    implementation(projects.feature.settings)
    implementation(projects.feature.extensions)
    // v1.1 review #5: the player's system media controls leave the session bus when the app quits (Main.kt)
    implementation(projects.feature.player)
    // PC extension servers (this PC as a client of another PC's server) and their screen
    implementation(projects.core.extensionServer)
    implementation(projects.feature.link)
    implementation(projects.feature.manga)
    implementation(projects.feature.novel)
    implementation(projects.feature.downloads)
    implementation(projects.feature.social)
    // Hito 5 (W3): the AniList profile, activity and notifications on desktop (needs the lists repository's account).
    implementation(projects.core.social)
    implementation(projects.feature.you)
    // Hito 5 (W2): the Link server and the integrated extension server (Settings › Nijiyome Link)
    implementation(projects.server)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Hito 5 (W3): the tray icon. MIT. StatusNotifierItem over D-Bus (sd-bus) on Linux, the notification area on Windows; AWT's
    // own tray only speaks XEmbed, which KDE Plasma 6 and GNOME no longer show.
    implementation(libs.composenativetray)
    // Native WM drag/resize (_NET_WM_MOVERESIZE on X11/XWayland, WM_NCLBUTTONDOWN on Windows) to prevent drag artifacts
    implementation(libs.jna.platform)

    testImplementation(libs.bundles.jvm.test)
    testImplementation(libs.cmp.lifecycle.viewmodel.compose)
}

// Tests never reach a system keyring (the secrets key stays in their temporary data directory).
tasks.test { environment("NIJIYOME_SECRETS", "file") }

// --- Build info (Hito 5, W3) ----------------------------------------------------------------------------------------------------
// The version comes from gradle.properties (`nijiyome.version`), or NIJIYOME_VERSION_NAME (the release tag in CI). The public client
// ids of the desktop OAuth clients (AniList / MAL, loopback redirect http://127.0.0.1:47410/callback) and of the Discord application
// come from local.properties or the environment, never from the repository; blank = the feature stays disabled with its text.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun cfg(name: String): String = (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()
val appVersion: String = cfg("NIJIYOME_VERSION_NAME").removePrefix("v").ifEmpty { providers.gradleProperty("nijiyome.version").get() }
// jpackage wants MAJOR.MINOR.PATCH with numbers only: a pre-release suffix (`0.2.0-beta.1`) is dropped there.
val packageVersionNumber: String = appVersion.substringBefore('-').substringBefore('+')

val buildInfoDir = layout.buildDirectory.dir("generated/buildInfo")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val out = buildInfoDir
    val values = mapOf(
        "NIJIYOME_VERSION" to appVersion,
        "NIJIYOME_ANILIST_CLIENT_ID_DESKTOP" to cfg("NIJIYOME_ANILIST_CLIENT_ID_DESKTOP"),
        "NIJIYOME_MAL_CLIENT_ID_DESKTOP" to cfg("NIJIYOME_MAL_CLIENT_ID_DESKTOP"),
        "NIJIYOME_DISCORD_APP_ID" to cfg("NIJIYOME_DISCORD_APP_ID"),
    )
    inputs.properties(values)
    outputs.dir(out)
    doLast {
        val file = out.get().file("nijiyome-build.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(values.entries.joinToString("\n", postfix = "\n") { (k, v) -> "$k=${v.replace("\\", "\\\\")}" })
    }
}
sourceSets.main { resources.srcDir(generateBuildInfo) }

// Settings › Acerca de › Licencias (Hito 6, D1): the same texts Android ships in its assets, on the classpath under `licenses/`
// with an index (a jar cannot be listed): THIRD_PARTY_NOTICES.md and the OFL licenses of the bundled fonts (:core:designsystem).
val licensesDir = layout.buildDirectory.dir("generated/licenses")
val generateLicenses = tasks.register("generateLicenses") {
    val out = licensesDir
    val notices = rootProject.file("THIRD_PARTY_NOTICES.md")
    val fonts = rootProject.fileTree("core/designsystem/src/androidMain/assets/licenses") { include("OFL-*.txt") }
    inputs.file(notices)
    inputs.files(fonts)
    outputs.dir(out)
    doLast {
        val dir = out.get().dir("licenses").asFile
        dir.deleteRecursively()
        dir.mkdirs()
        val names = listOf(notices) + fonts.files.sortedBy { it.name }
        names.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
        dir.resolve("index.txt").writeText(names.joinToString("\n", postfix = "\n") { it.name })
    }
}
sourceSets.main { resources.srcDir(generateLicenses) }

// --- Packaging ------------------------------------------------------------------------------------------------------------------
// `createReleaseDistributable` makes the app image (jlink runtime + jars + launcher) that packaging/linux/build-appimage.sh turns
// into the AppImage and packaging/windows.nsi into the installer and the portable zip. The temple-lsfg helper (packaging/linux/
// build-temple-lsfg.sh, or CMake on Windows) goes into the image's resources when NIJIYOME_TEMPLE_LSFG points at the built binary.
val appResources = layout.buildDirectory.dir("generated/appResources")
val stageAppResources = tasks.register<Copy>("stageAppResources") {
    val lsfg = cfg("NIJIYOME_TEMPLE_LSFG")
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    into(appResources.map { it.dir(if (windows) "windows-x64" else "linux-x64") })
    if (lsfg.isNotEmpty()) from(lsfg) { rename { if (windows) "temple-lsfg.exe" else "temple-lsfg" } }
    // The notices of what the app ships, readable from Settings › Acerca de › Licencias and next to the binaries.
    from(rootProject.file("LICENSE"))
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
}

compose.desktop {
    application {
        mainClass = "app.nijiyome.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "Nijiyome"
            packageVersion = packageVersionNumber
            description = "Anime player with libmpv, Anime4K and local extensions"
            vendor = "Nijiyome"
            copyright = "AGPL-3.0-or-later"
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(appResources)
            // The JDK modules jlink keeps (`suggestRuntimeModules`, plus the SunEC / PKCS#11 providers TLS needs). java.base brings
            // lib/libjsig.so, which SignalChaining.kt preloads on Linux.
            modules(
                "java.compiler", "java.instrument", "java.management", "java.naming", "java.net.http", "java.prefs", "java.rmi",
                "java.scripting", "java.security.jgss", "java.sql", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.unsupported",
                "jdk.management", "jdk.net", "jdk.zipfs", "jdk.security.auth",
            )
            linux {
                iconFile.set(rootProject.file("packaging/icons/nijiyome-512.png"))
                packageName = "nijiyome"
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo;Video;Player"
            }
            windows {
                iconFile.set(rootProject.file("packaging/icons/nijiyome.ico"))
                menuGroup = "Nijiyome"
                perUserInstall = true
                // Stable across versions so an MSI upgrades the previous one.
                upgradeUuid = "6b2f7f0e-4f3c-4c8e-9a51-1f6f2c7f9a10"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(stageAppResources) }
