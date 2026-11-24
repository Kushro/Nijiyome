package app.nijiyome.feature.search

/** Recent searches (prototype `settings.searchHistory`: newest first, at most [SEARCH_HISTORY_SIZE], local to the device). */
interface SearchHistoryStore {
    fun load(): List<String>

    fun save(entries: List<String>)
}

const val SEARCH_HISTORY_SIZE = 6

/** Default for tests and previews: keeps the history in memory only. */
class InMemorySearchHistoryStore(initial: List<String> = emptyList()) : SearchHistoryStore {
    private var entries = initial

    override fun load(): List<String> = entries

    override fun save(entries: List<String>) {
        this.entries = entries
    }
}

/** Newest first, case-sensitive dedupe on the trimmed text, capped to [SEARCH_HISTORY_SIZE]. Queries shorter than 2 chars are ignored. */
fun List<String>.withSearch(query: String): List<String> {
    val q = query.trim()
    if (q.length < 2) return this
    return (listOf(q) + filter { it != q }).take(SEARCH_HISTORY_SIZE)
}
