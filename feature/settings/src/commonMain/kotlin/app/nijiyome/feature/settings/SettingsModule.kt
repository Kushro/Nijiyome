package app.nijiyome.feature.settings

import app.nijiyome.data.PROP_ANILIST_CLIENT_ID
import app.nijiyome.data.PROP_MAL_CLIENT_ID
import app.nijiyome.model.ListProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Every collaborator but the settings repository is optional (getOrNull) so Settings never crashes when a module is not bound.
 * 2026-11-09 amendment: no `AuthRepository`; identity comes from `ListsRepository`, history sync from `LinkRepository` + `HistoryRepository`.
 *
 * [images] is the image disk cache "Limpiar caché" also empties (Coil's singleton loader, which needs the platform context).
 * Android: `settingsModule` (androidMain). Desktop: `desktopSettingsModule()` (desktopMain). Without a `ListsRepository` (desktop for
 * builds without accounts) Listas y cuentas and the wizard's lists step are not shown.
 */
fun settingsModule(images: Scope.() -> ImageCache?): Module = module {
    viewModel {
        SettingsViewModel(
            settingsRepo = get(),
            history = getOrNull(),
            metadata = getOrNull(),
            torrents = getOrNull(),
            lists = getOrNull(),
            link = getOrNull(),
            reset = getOrNull(),
            images = images(),
            extraStorage = getOrNull(),
        )
    }
    // parity wave A (15 §3.3-3.4, worker q): Acerca de / Datos actions, first-run wizard.
    viewModel { SystemViewModel(logs = getOrNull(), updates = getOrNull(), history = getOrNull()) }
    viewModel { OnboardingViewModel(settingsRepo = get()) }
    // parity wave A (15 §3.3, worker q): Listas y cuentas (OAuth redirect from PendingOAuthRedirect) and the wizard's lists step.
    // A provider without a client id (NIJIYOME_*_CLIENT_ID unset) shows "Vincular" disabled with the reason. Only created where a
    // ListsRepository is bound: the section and the wizard step are not shown otherwise.
    viewModel {
        val configured = buildSet {
            if (!getPropertyOrNull<String>(PROP_ANILIST_CLIENT_ID).isNullOrBlank()) add(ListProvider.ANILIST)
            if (!getPropertyOrNull<String>(PROP_MAL_CLIENT_ID).isNullOrBlank()) add(ListProvider.MAL)
        }
        AccountsViewModel(lists = get(), settingsRepo = get(), redirects = getOrNull(), configured = configured)
    }
}
