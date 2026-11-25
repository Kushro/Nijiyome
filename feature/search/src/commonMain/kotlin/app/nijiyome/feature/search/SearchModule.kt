package app.nijiyome.feature.search

import app.nijiyome.metadata.TrendingFeed
import app.nijiyome.model.ListsRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * [history] builds the recent-searches persistence (Android: SharedPreferences) and [reader] the reader of the picked / shared
 * image (Android: ContentResolver).
 */
fun searchModule(history: Scope.() -> SearchHistoryStore, reader: Scope.() -> ImageBytesReader): Module = module {
    single<SearchHistoryStore> { history() }
    // parity wave A (15 §3.1, worker o): image search reads the picked / shared picture through the platform reader.
    single<ImageBytesReader> { reader() }
    viewModel {
        // TrendingFeed lives in metadataModule (:core:metadata); getOrNull keeps Search usable if it is not loaded.
        val trending = getOrNull<TrendingFeed>()
        SearchViewModel(
            metadata = get(),
            historyStore = get(),
            extensions = get(),
            settings = get(),
            trending = trending?.let { feed -> TrendingSource { window, page, adult -> feed.page(window, page, adult) } },
            // Sparkles Explorar: list status on the result cards, the "Mi lista" facet and changing a status from a result.
            lists = getOrNull<ListsRepository>(),
        )
    }
    viewModel { ImageSearchViewModel(metadata = get(), reader = get()) }
}
