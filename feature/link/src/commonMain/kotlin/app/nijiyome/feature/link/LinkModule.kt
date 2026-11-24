package app.nijiyome.feature.link

import app.nijiyome.model.VideoDecodeLimits
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The Link screens' view models. [deviceName] is how this device is registered on the PC; [decodeLimits] what its video decoder
 * plays (null = unknown). Android: `linkFeatureModule` (androidMain). Desktop registers none yet: the app is not a Link client there.
 */
fun linkFeatureModule(deviceName: () -> String, decodeLimits: () -> VideoDecodeLimits?): Module = module {
    viewModel {
        LinkViewModel(
            link = get(),
            deviceName = deviceName(),
            // parity wave B (16 §3.11, worker r): "Continuar en la PC" + mDNS pairing
            history = getOrNull(),
            discovery = getOrNull(),
            // BUG-132: profiles the phone's AVC decoder cannot play are shown disabled
            decodeLimits = decodeLimits,
            // 2026-11 history per device: "Esta PC no guarda lo que reproduces aquí" / "Sincronización pausada por la PC"
            historyStatus = getOrNull(),
        )
    }
    // docs/redesign/13 §3.10: PC extension server connect screen
    viewModel { ExtensionServersViewModel(repository = get(), pending = get()) }
}

/**
 * Desktop: only the PC extension server screen's view model (the PC is a client of other PCs' extension servers, as in Tatakai Next;
 * it is not a Link client, so [linkFeatureModule] is not registered there). Needs `extensionServerModule` (repository and
 * PendingServerLink).
 */
val extensionServersFeatureModule: Module = module {
    viewModel { ExtensionServersViewModel(repository = get(), pending = get()) }
}
