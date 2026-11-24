package app.nijiyome.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nijiyome.model.AppError
import app.nijiyome.model.AppLanguage
import app.nijiyome.model.AppReset
import app.nijiyome.model.SelectiveAppReset
import app.nijiyome.model.AppSettings
import app.nijiyome.model.LookPrefs
import app.nijiyome.model.LookPreset
import app.nijiyome.model.ContrastPref
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkState
import app.nijiyome.model.LinkedAccount
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LoadState
import app.nijiyome.model.MetadataCacheStats
import app.nijiyome.model.MetadataRepository
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.PlayerQualityPrefs
import app.nijiyome.model.ProcessingLocation
import app.nijiyome.model.Quality
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.model.ThemeMode
import app.nijiyome.model.torrent.TorrentRepository
import app.nijiyome.model.torrent.TorrentStorage
import app.nijiyome.ui.state.runCatchingApp
import app.nijiyome.ui.state.toAppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data & storage figures (Settings › Datos y almacenamiento): metadata cache, image disk cache ([imageBytes], Coil) and torrent storage.
 * Any of them may be unknown.
 */
data class StorageInfo(val cache: MetadataCacheStats?, val torrents: TorrentStorage?, val imageBytes: Long? = null) {
    /** What "Limpiar caché" frees: metadata table + images on disk; null when neither is known. */
    val clearableBytes: Long?
        get() = if (cache == null && imageBytes == null) null else (cache?.bytes ?: 0L) + (imageBytes ?: 0L)
}

/**
 * 2026-11-09 amendment (plan 14 §0.1 #9): no own login. The identity shown in Settings is the list of linked AniList / MAL accounts
 * ([accounts], `ListsRepository`); the history sync figures ([lastSyncedAt], [syncing], [linkPaired]) describe Nijiyome Link.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    // --- android wave (12 §4 b.4) ---
    val lastSyncedAt: Long? = null,
    val syncing: Boolean = false,
    val storage: LoadState<StorageInfo> = LoadState.Loading,
    val clearingCache: Boolean = false,
    // --- parity wave A (15 §3.3 / §3.4, worker q) ---
    /** Linked list providers (empty = local lists only). Worker q adds the OAuth / sync / conflict state it needs beside this. */
    val accounts: List<LinkedAccount> = emptyList(),
    /** A PC is paired over Nijiyome Link (history sync possible). */
    val linkPaired: Boolean = false,
    /** 0.4.0 (worker C): Enlace › "Procesar por defecto en" (`LinkedDevice.defaultProcessing`). */
    val linkDefaultProcessing: ProcessingLocation = ProcessingLocation.PHONE,
    val resetting: Boolean = false,
    /** Hito 6 (D1): what [ExtraStorage] measured (desktop: web downloads, logs, extensions); empty on Android. */
    val extraStorage: List<ExtraStorageItem> = emptyList(),
)

sealed interface SettingsEvent {
    /** Prototype `snack(t("settings.saved"))` after a change. */
    data object Saved : SettingsEvent
    data class CacheCleared(val freedBytes: Long) : SettingsEvent
    data class Failed(val cause: AppError) : SettingsEvent
    data object Synced : SettingsEvent

    /** parity wave A (worker q): "Restablecer la app" finished; the shell recreates the activity. */
    data object ResetDone : SettingsEvent
}

/**
 * Settings as one sectioned screen. Every field saves immediately through [SettingsRepository]. [history], [metadata], [torrents],
 * [lists], [link] and [reset] are optional so the existing tests (and a build without a module) keep working.
 */
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val history: HistoryRepository? = null,
    private val metadata: MetadataRepository? = null,
    private val torrents: TorrentRepository? = null,
    // parity wave A (15 §3.3-3.4, worker q)
    private val lists: ListsRepository? = null,
    private val link: LinkRepository? = null,
    private val reset: AppReset? = null,
    // parity wave A closure (worker q, checklist system.clear-cache): Coil's disk cache of covers / banners
    private val images: ImageCache? = null,
    // Hito 6 (D1): folders only desktop counts in Ajustes › Datos; Android binds none.
    private val extraStorage: ExtraStorage? = null,
) : ViewModel() {

    private val extras = MutableStateFlow(Extras())
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepo.settings,
        history?.lastSyncedAt ?: flowOf(null),
        lists?.accounts ?: flowOf(emptyList()),
        link?.let { l -> combine(l.state, l.linkedDevice) { st, d -> (st is LinkState.Paired) to (d?.defaultProcessing ?: ProcessingLocation.PHONE) } }
            ?: flowOf(false to ProcessingLocation.PHONE),
        extras,
    ) { s, synced, accounts, linkFacts, x ->
        val (paired, defaultProcessing) = linkFacts
        SettingsUiState(
            linkDefaultProcessing = defaultProcessing,
            settings = s,
            lastSyncedAt = synced,
            syncing = x.syncing,
            storage = x.storage,
            clearingCache = x.clearingCache,
            accounts = accounts,
            linkPaired = paired,
            resetting = x.resetting,
            extraStorage = x.extraStorage,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(settings = settingsRepo.settings.value),
    )

    // ---------------------------------------------------------------- appearance

    fun setPalette(id: String) = update { it.copy(paletteId = id) }

    fun setAmoled(value: Boolean) = update(announce = true) { it.copy(amoled = value) }

    fun setDynamicColor(value: Boolean) = update(announce = true) { it.copy(dynamicColor = value) }

    fun setContrast(value: ContrastPref) = update { it.copy(contrast = value) }

    fun setThemeMode(value: ThemeMode) = update { it.copy(themeMode = value) }

    fun setLanguage(value: AppLanguage) = update { it.copy(language = value) }

    fun setReduceMotion(value: Boolean) = update(announce = true) { it.copy(reduceMotion = value) }

    /** 2026-11 UI refresh: one Diseño y estilo field (the preset stays as it was; the screen shows "Personalizado" when nothing matches). */
    fun setLook(transform: (LookPrefs) -> LookPrefs) = update { it.copy(look = transform(it.look).sanitized()) }

    /** Every field of [preset]; the header composition is not part of any preset and stays as chosen. */
    fun applyLookPreset(preset: LookPreset) = update(announce = true) {
        it.copy(look = LookPrefs.of(preset).copy(headerLayout = it.look.headerLayout))
    }

    // ---------------------------------------------------------------- player

    fun setPlayerEngine(value: PlayerEngine) = update(announce = true) { it.copy(playerEngine = value) }

    /** v1.1: libmpv verifies HTTPS certificates against the system's CAs (Android; off by default). */
    fun setMpvTlsVerify(on: Boolean) = update(announce = true) { it.copy(mpvTlsVerify = on) }

    /** null = system chooser. */
    fun setExternalPlayerPackage(value: String?) = update(announce = true) { it.copy(externalPlayerPackage = value?.takeIf(String::isNotBlank)) }

    fun setPlayerPreset(value: String) = update(announce = true) { it.copy(playerPreset = value) }

    /** 0.4.0 (worker A): one field of the libmpv quality defaults (tier, deband, automatic adjustments, decoder, smoothing). */
    fun setPlayerQuality(transform: (PlayerQualityPrefs) -> PlayerQualityPrefs) =
        update(announce = true) { it.copy(playerQuality = transform(it.playerQuality).sanitized()) }

    fun setSourceMode(value: SourceMode) = updatePrefs { it.copy(mode = value) }

    /** null = any kind. */
    fun setPreferredKind(value: SourceKind?) = updatePrefs { it.copy(prefer = value) }

    fun setMinQuality(value: Quality) = updatePrefs { it.copy(minQuality = value) }

    fun setSubLang(value: String) = updatePrefs { it.copy(subLang = value) }

    fun setDubLang(value: String) = updatePrefs { it.copy(dubLang = value) }

    fun setAutoNext(value: Boolean) = update(announce = true) { it.copy(autoNext = value) }

    fun setSkipIntro(value: Boolean) = update(announce = true) { it.copy(skipIntro = value) }

    fun setSubtitleStyle(value: SubtitleStyle) = update {
        it.copy(subtitleStyle = value.sanitized()) // 0.4.1: every range lives in SubtitleStyle.sanitized()
    }

    // ---------------------------------------------------------------- metadata

    /** Moves [providerId] by [delta] positions in the metadata priority list (negative = earlier). */
    fun moveProvider(providerId: String, delta: Int) = update { it.copy(providerPriority = it.providerPriority.moved(providerId, delta)) }

    fun setMetadataTtl(hours: Int) = update(announce = true) { it.copy(metadataCacheTtlHours = hours.coerceIn(1, 168)) }

    // ---------------------------------------------------------------- history over Nijiyome Link (Enlace section)

    /** 0.4.0 (worker C): Enlace › "Procesar por defecto en" (kept with the pairing; the player switches it any time). */
    fun setLinkDefaultProcessing(location: ProcessingLocation) {
        val repo = link ?: return
        viewModelScope.launch {
            runCatching { repo.setDefaultProcessing(location) }
            _events.trySend(SettingsEvent.Saved)
        }
    }

    /** "Sincronizar ahora": push dirty rows to the paired PC and pull its recent ones. */
    fun syncNow() {
        val repo = history ?: return
        if (extras.value.syncing) return
        extras.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val result = runCatchingApp { repo.syncNow().getOrThrow() }
            extras.update { it.copy(syncing = false) }
            _events.trySend(result.fold(onSuccess = { SettingsEvent.Synced }, onFailure = { SettingsEvent.Failed(it.toAppError()) }))
        }
    }

    // ---------------------------------------------------------------- data & storage

    /** Loads (or reloads) the cache and torrent storage figures. A failure of one source only blanks that figure. */
    fun refreshStorage() {
        extras.update { it.copy(storage = LoadState.Loading) }
        viewModelScope.launch {
            val cache = metadata?.let { repo -> runCatchingApp { repo.cacheStats() } }
            val torrent = torrents?.let { repo -> runCatchingApp { repo.storageUsage() } }
            val image = images?.let { cache -> runCatchingApp { cache.sizeBytes() } }
            val extra = extraStorage?.let { source -> runCatchingApp { source.measure() }.getOrNull() }.orEmpty()
            val failures = listOfNotNull(cache?.exceptionOrNull(), torrent?.exceptionOrNull(), image?.exceptionOrNull())
            val loaded = StorageInfo(cache?.getOrNull(), torrent?.getOrNull(), image?.getOrNull())
            val nothing = loaded.cache == null && loaded.torrents == null && loaded.imageBytes == null
            val result: LoadState<StorageInfo> = if (failures.isNotEmpty() && nothing) {
                LoadState.Err(failures.first().toAppError())
            } else {
                LoadState.Ok(loaded)
            }
            val torrentDir = loaded.torrents?.downloadDir?.takeIf { it.isNotBlank() }
            val marked = extra.map { item ->
                item.copy(inTorrents = torrentDir != null && item.path != null && isInside(item.path, torrentDir))
            }
            extras.update { it.copy(storage = result, extraStorage = marked) }
        }
    }

    /**
     * Hito 6 (D1, desktop): "Vaciar" of Ajustes › Metadatos › caché, only the metadata responses (the images stay). Reports the
     * freed bytes like "Limpiar caché".
     */
    fun clearMetadataCache() {
        val repo = metadata ?: return
        if (extras.value.clearingCache) return
        extras.update { it.copy(clearingCache = true) }
        viewModelScope.launch {
            val before = runCatchingApp { repo.cacheStats().bytes }.getOrNull() ?: 0L
            val result = runCatchingApp { repo.clearCache() }
            val after = runCatchingApp { repo.cacheStats().bytes }.getOrNull() ?: 0L
            extras.update { it.copy(clearingCache = false) }
            val failure = result.exceptionOrNull()
            _events.trySend(if (failure != null) SettingsEvent.Failed(failure.toAppError()) else SettingsEvent.CacheCleared(freedBytes(before, after)))
            refreshStorage()
        }
    }

    /**
     * "Limpiar caché" (checklist system.clear-cache): the metadata cache table and the image disk cache (Coil). Both are tried even when
     * one fails; the freed bytes are measured (before - after), the first failure is reported instead.
     */
    fun clearCache() {
        if (metadata == null && images == null) return
        if (extras.value.clearingCache) return
        extras.update { it.copy(clearingCache = true) }
        viewModelScope.launch {
            val before = measureClearable()
            val metaResult = metadata?.let { repo -> runCatchingApp { repo.clearCache() } }
            val imageResult = images?.let { cache -> runCatchingApp { cache.clear() } }
            val after = measureClearable()
            extras.update { it.copy(clearingCache = false) }
            val failure = metaResult?.exceptionOrNull() ?: imageResult?.exceptionOrNull()
            _events.trySend(
                if (failure != null) SettingsEvent.Failed(failure.toAppError()) else SettingsEvent.CacheCleared(freedBytes(before, after)),
            )
            refreshStorage()
        }
    }

    /** Metadata cache + image disk cache bytes right now (an unreadable figure counts as 0). */
    private suspend fun measureClearable(): Long {
        val meta = metadata?.let { repo -> runCatchingApp { repo.cacheStats().bytes }.getOrNull() } ?: 0L
        val img = images?.let { cache -> runCatchingApp { cache.sizeBytes() }.getOrNull() } ?: 0L
        return meta + img
    }

    /** false where no [AppReset] is bound (desktop for now): "Restablecer la app" is not offered. Android always binds it. */
    val hasReset: Boolean get() = reset != null

    /** parity wave A (worker q, checklist system.reset-app): wipe everything through [AppReset]; the UI confirmed first. */
    fun resetApp(keepTorrents: Boolean = true) {
        val r = reset ?: return
        if (extras.value.resetting) return
        extras.update { it.copy(resetting = true) }
        viewModelScope.launch {
            // Hito 6 (D1): desktop's reset can also remove the torrents with their files; Android's AppReset has no such choice.
            val result = runCatchingApp { (if (r is SelectiveAppReset) r.resetAll(keepTorrents) else r.resetAll()).getOrThrow() }
            extras.update { it.copy(resetting = false) }
            _events.trySend(result.fold(onSuccess = { SettingsEvent.ResetDone }, onFailure = { SettingsEvent.Failed(it.toAppError()) }))
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun updatePrefs(transform: (SourcePrefs) -> SourcePrefs) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(sourcePrefs = transform(it.sourcePrefs)) }
            _events.trySend(SettingsEvent.Saved)
        }
    }

    /** parity wave A (15 §3.2): generic setter for rows owned by other modules (`PlayerSettingsExtras` of worker p). Announces "saved". */
    fun updateSettings(transform: (AppSettings) -> AppSettings) = update(announce = true, transform)

    private fun update(announce: Boolean = false, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepo.update(transform)
            if (announce) _events.trySend(SettingsEvent.Saved)
        }
    }

    private data class Extras(
        val syncing: Boolean = false,
        val storage: LoadState<StorageInfo> = LoadState.Loading,
        val clearingCache: Boolean = false,
        val resetting: Boolean = false,
        val extraStorage: List<ExtraStorageItem> = emptyList(),
    )
}

/** Pure list move used by the provider priority editor; ignores unknown ids and clamps to the list bounds. */
fun List<String>.moved(id: String, delta: Int): List<String> {
    val from = indexOf(id)
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().also {
        it.removeAt(from)
        it.add(to, id)
    }
}
