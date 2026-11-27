// v1.3.3 GPU image upscaling (docs/plan-upscaling.md §4.1): settings and their JSON codec, qualification and factor choice, cache key,
// LRU result cache, the LIFO queue with reference-counted tickets and pause gates, and the engine / codec / source interfaces in
// commonMain. The platforms (step 4) bring the JNI engine, the RGBA codec, the Coil source reader and the pause signals.
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The contract exposes StateFlow and @Serializable types.
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            // Step 4: the source bytes come from Coil's disk cache and the refresh hook is a Coil EventListener. Only coil-core (no
            // Compose); on desktop it also brings Skiko, which the codec uses (Skia decode, scale and WebP encode).
            // CATÁLOGO: mover a libs.versions.toml (coil-core, version.ref = "coil")
            implementation("io.coil-kt.coil3:coil-core:${libs.versions.coil.get()}")
        }
        androidMain.dependencies { implementation(libs.kotlinx.coroutines.android) }
    }
}
