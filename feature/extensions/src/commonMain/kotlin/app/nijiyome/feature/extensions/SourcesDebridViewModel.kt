// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nijiyome.model.AppError
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.torrent.DebridAccount
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.torrent.DebridService
import app.nijiyome.model.torrent.DebridSettings
import kotlinx.coroutines.CancellationException
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

// Sparkles S9: the Debrid tab of Fuentes. Debrid lived in Torrents › Debrid and in Settings › Torrents; Fuentes shows it once, through the
// DebridRepository contract of :core:model (the torrents module is not a dependency of this one).

data class DebridUiState(
    /** False when this build registers no debrid client (the tab says so). */
    val available: Boolean = false,
    val accounts: List<DebridAccount> = emptyList(),
    /** Service whose key is being checked for linking. */
    val linking: DebridService? = null,
    val errors: Map<DebridService, AppError> = emptyMap(),
    val settings: DebridSettings = DebridSettings(),
    /** "Probar la clave" running on this linked service. */
    val checking: DebridService? = null,
)

/** One-shot outcomes for the snackbar. */
sealed interface DebridNotice {
    data class Linked(val account: DebridAccount) : DebridNotice
    data class CheckOk(val account: DebridAccount) : DebridNotice
    data class CheckFailed(val service: DebridService, val error: AppError) : DebridNotice
    data class Unlinked(val service: DebridService) : DebridNotice
}

class SourcesDebridViewModel(
    private val debrid: DebridRepository?,
    private val settings: SettingsRepository? = null,
) : ViewModel() {
    private val local = MutableStateFlow(DebridUiState(available = debrid != null))
    private val _notices = Channel<DebridNotice>(Channel.BUFFERED)
    val notices: Flow<DebridNotice> = _notices.receiveAsFlow()

    val state: StateFlow<DebridUiState> = combine(
        debrid?.accounts ?: flowOf(emptyList()),
        settings?.settings?.map { it.torrent.debrid } ?: flowOf(DebridSettings()),
        local,
    ) { accounts, prefs, l -> l.copy(accounts = accounts, settings = prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebridUiState(available = debrid != null))

    /** "Probar y vincular": validates the key against the service and stores it (the key is never shown again). */
    fun link(service: DebridService, apiKey: String) {
        val repo = debrid ?: return
        val key = apiKey.trim()
        if (key.isEmpty() || local.value.linking != null) return
        local.update { it.copy(linking = service, errors = it.errors - service) }
        viewModelScope.launch {
            val result = guarded { repo.link(service, key).getOrThrow() }
            local.update { l ->
                l.copy(linking = null, errors = result.exceptionOrNull()?.let { l.errors + (service to it.toError()) } ?: l.errors)
            }
            result.getOrNull()?.let { _notices.trySend(DebridNotice.Linked(it)) }
        }
    }

    /** "Probar la clave" of a linked account. */
    fun recheck(service: DebridService) {
        val repo = debrid ?: return
        if (local.value.checking != null) return
        local.update { it.copy(checking = service) }
        viewModelScope.launch {
            val result = guarded { repo.recheck(service).getOrThrow() }
            local.update { it.copy(checking = null) }
            _notices.trySend(
                result.fold(onSuccess = { DebridNotice.CheckOk(it) }, onFailure = { DebridNotice.CheckFailed(service, it.toError()) }),
            )
        }
    }

    fun unlink(service: DebridService) {
        val repo = debrid ?: return
        viewModelScope.launch {
            guarded { repo.unlink(service) }.onSuccess { _notices.trySend(DebridNotice.Unlinked(service)) }
        }
    }

    fun clearError(service: DebridService) = local.update { it.copy(errors = it.errors - service) }

    /** The three switches (use debrid, prefer cached, download cached), saved in `AppSettings.torrent.debrid`. */
    fun setSettings(transform: (DebridSettings) -> DebridSettings) {
        val repo = settings ?: return
        viewModelScope.launch { guarded { repo.update { it.copy(torrent = it.torrent.copy(debrid = transform(it.torrent.debrid))) } } }
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** Linked services first (their stored order), then the others with the recommended one first. Pure. */
internal fun debridOrder(accounts: List<DebridAccount>): List<DebridService> =
    accounts.map { it.service } + DebridService.entries.filter { s -> accounts.none { it.service == s } }.sortedByDescending { it.recommended }

/** Where each service hands out its API key (opened in the browser by "Obtener una clave"). */
internal fun debridKeyPage(service: DebridService): String = when (service) {
    DebridService.TORBOX -> "https://torbox.app/settings"
    DebridService.REAL_DEBRID -> "https://real-debrid.com/apitoken"
    DebridService.ALLDEBRID -> "https://alldebrid.com/apikeys"
    DebridService.PREMIUMIZE -> "https://www.premiumize.me/account"
}
