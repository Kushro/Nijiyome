package app.nijiyome.feature.home

import app.nijiyome.metadata.TrendWindow
import app.nijiyome.metadata.TrendingFeed
import app.nijiyome.model.AiringRepository
import app.nijiyome.model.DownloadRepository
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.RecsRepository
import app.nijiyome.model.torrent.TorrentRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    viewModel {
        val trend = getOrNull<TrendingFeed>()
        HomeViewModel(
            metadata = get(),
            history = get(),
            settings = getOrNull(),
            extensions = getOrNull(),
            // parity wave B (16 §3.1, worker r): every new source is optional (getOrNull) so Home still works without them.
            airing = getOrNull<AiringRepository>(),
            recs = getOrNull<RecsRepository>(),
            trending = trend?.let { t ->
                HomeTrendSource { window, adult ->
                    val w = when (window) {
                        TopWindow.TODAY -> TrendWindow.TODAY
                        TopWindow.WEEK -> TrendWindow.WEEK
                        TopWindow.MONTH -> TrendWindow.MONTH
                    }
                    t.page(w, 1, adult).items
                }
            },
            downloads = getOrNull<DownloadRepository>(),
            torrents = getOrNull<TorrentRepository>(),
            // Sparkles (S2): list status / progress / "new" on the cards.
            lists = getOrNull<ListsRepository>(),
        )
    }
    viewModel { CalendarViewModel(airing = get(), lists = getOrNull(), settings = getOrNull()) }
    viewModel { ForYouViewModel(recs = get()) }
}
