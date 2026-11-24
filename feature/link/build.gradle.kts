// Nijiyome Link screens (pair with the PC, linked device, PC extension servers). Shared by Android and desktop.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // docs/redesign/13 §3.10: connect screen of the PC extension servers
            implementation(projects.core.extensionServer)
            // parity wave B (16 §3.11, worker r): LinkDiscoveryAccess (mDNS pairing with the TOFU fingerprint)
            implementation(projects.core.link)
        }
        androidMain.dependencies {
            // QrScanner.android.kt: CameraX preview + ZXing decoding
            implementation(libs.bundles.camerax)
            implementation(libs.zxing.core)
            implementation(libs.androidx.activity.compose)
        }
        getByName("desktopMain").dependencies {
            // v1.1 (F6) QrScanner.desktop.kt: ZXing decoding of webcam frames; JNA for the V4L2 capture on Linux (no new library).
            implementation(libs.zxing.core)
            implementation(libs.jna.platform)
        }
        // v1.1 (F6): the scanner and the extension-server screen are drawn off screen in tests (this OS's Skia runtime).
        getByName("desktopTest").dependencies { implementation(compose.desktop.currentOs) }
    }
}
