// Integrated extension server client (docs/redesign/13-integrated-server-contract.md §3.10, owner w6).
// Models, HTTP client, host guard, deep link parser, repository, candidate / custom-source mapping, package check and the sealed
// connection store in commonMain; the AndroidKeyStore key that seals the tokens (ExtensionServerKey) and the Koin
// `val extensionServerModule` in androidMain; on desktop (a client too, as in Tatakai Next) `desktopExtensionServerModule`, whose
// sealing key lives in the app's SecretStore (system keyring).
plugins { id("nijiyome.kmp.library") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            // ExternalCandidateSource (merge point of ExtensionRepositoryImpl.resolveCandidates) and Candidates.mapCandidates
            implementation(projects.core.extensions)
            // KeyCipher / AesGcmKeyCipher: same at-rest sealing as the Link pairing
            implementation(projects.core.link)
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        commonTest.dependencies { implementation(libs.mockwebserver3) }
    }
}
