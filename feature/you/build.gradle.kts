// android wave contract (docs/redesign/12-android-wave-contract.md), owner: worker b.
// "Tú" screen: account card + list (Historial, Descargas, Extensiones, Dispositivo enlazado, Ajustes). Shared by Android and desktop.
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.cmp.material.icons.extended)
        }
    }
}
