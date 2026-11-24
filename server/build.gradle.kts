// SPDX-License-Identifier: AGPL-3.0-or-later
// The desktop servers (hito 5, W2; plan §5 rows tk-link / tk-server, §8 decision 2): Nijiyome Link v1 (HTTPS with optional client
// certificates, pairing, devices, FFmpeg / Anime4K / LSFG sessions over fMP4 HLS, subtitles, history, remote control, mDNS) and the
// integrated extension server (/api/server/v1, bearer tokens, media proxy, logs). Ported from Tatakai Next's tk-link / tk-server
// (Rust, AGPL-3.0, same author; docs/origen/THIRD_PARTY_NOTICES-escritorio.md). JVM only: Netty never reaches Android, and only
// :desktopApp depends on this module. The wire DTOs the phone already has come from :core:link (shared, not duplicated).
plugins { id("nijiyome.jvm.library") }

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.link)
    implementation(projects.core.database)
    implementation(projects.core.extensions)
    implementation(projects.core.extensionsQuickjs)
    // Settings › Red: the outbound proxy and FlareSolverr shared with the app's own clients
    implementation(projects.core.network)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)

    // Ktor server (Apache-2.0): the server stack of plan §3
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    // Bouncy Castle License (MIT): the CA, CSR checks, argon2id
    implementation(libs.bouncycastle.pkix)
    // JmDNS (Apache-2.0): the `_nijiyome-link._tcp` announcement
    implementation(libs.jmdns)

    testImplementation(libs.mockwebserver3)
    // the phone's own client of the integrated extension server, run against this server in the tests
    testImplementation(projects.core.extensionServer)
    // Second part (torrent sources): the real desktop engine (W1, natives included) and a plain libtorrent session that seeds over loopback
    testImplementation(projects.core.torrent)
    testImplementation(libs.libtorrent4j)
}

// libtorrent4j's native library replaces the JVM's SIGSEGV / SIGBUS handlers; HotSpot's signal-chaining library keeps the JVM's own
// first (same as :core:torrent's desktopTest and the app's SignalChaining).
tasks.withType<Test>().configureEach {
    environment("LD_PRELOAD", javaLauncher.get().metadata.installationPath.file("lib/libjsig.so").asFile.absolutePath)
}
