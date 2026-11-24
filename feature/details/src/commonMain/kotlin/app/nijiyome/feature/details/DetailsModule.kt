package app.nijiyome.feature.details

import app.nijiyome.model.MediaKind
import app.nijiyome.model.MediaRef
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Parameters: (kind: MediaKind, ref: MediaRef) for Details; (characterId: Int) for the character page.
 * [prefs] builds the module-private persistence (Android: SharedPreferences).
 */
fun detailsModule(prefs: Scope.() -> DetailsPrefs): Module = module {
    // parity wave A (15 §5 o.2, worker o): 18+ gate decisions and the episode view mode, private to this module.
    single<DetailsPrefs> { prefs() }
    viewModel { params ->
        DetailsViewModel(
            kind = params.get<MediaKind>(),
            ref = params.get<MediaRef>(),
            metadata = get(),
            history = get(),
            extensions = get(),
            // linkModule is always bound on Android; desktop has no Link client yet and hides "Enviar a dispositivo".
            link = getOrNull(),
            settings = get(),
            // Bound by other workers of the android wave (torrentModule: f, dataModule: a); getOrNull keeps Details usable without them.
            torrents = getOrNull(),
            stats = getOrNull(),
            prefs = get(),
            // parity wave B (r): downloadModule (:core:download); offline playback of completed downloads.
            downloads = getOrNull(),
            // dataModule: list progress (AniList / MAL mirror) for resume, watched marks and the manual toggle.
            lists = getOrNull(),
            // desktop: this PC's Link server sends an episode to a paired phone (:server binds SendToPhone; Android binds none).
            phoneSender = getOrNull(),
            // 1.1 (F7): TorBox downloads of cached torrents (torrentModule)
            debrid = getOrNull(),
        )
    }
    viewModel { params ->
        CharacterViewModel(
            characterId = params.get<Int>(),
            metadata = get(),
            // ListsRepository is bound by worker q in dataModule; without it the favorite heart is hidden.
            lists = getOrNull(),
        )
    }
}
