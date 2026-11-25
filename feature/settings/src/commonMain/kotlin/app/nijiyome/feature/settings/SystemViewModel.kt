// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nijiyome.model.AppError
import app.nijiyome.model.DeviceLogSource
import app.nijiyome.model.HistoryRepository
import app.nijiyome.model.LoadState
import app.nijiyome.model.LogPushTrigger
import app.nijiyome.model.LogRepository
import app.nijiyome.model.RemoteLogsRepository
import app.nijiyome.model.UpdateInfo
import app.nijiyome.model.UpdateRepository
import app.nijiyome.ui.state.runCatchingApp
import app.nijiyome.ui.state.toAppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// parity wave A (15 §3.4, worker q; checklist system.open-logs, system.update-check, system.export-history,
// system.clear-watch-history): the Acerca de / Datos actions that are not plain settings.

/** Result of "Buscar actualizaciones": null = never asked in this screen. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class Available(val info: UpdateInfo) : UpdateCheckState
    data class Failed(val cause: AppError) : UpdateCheckState
}

/** Which source the log viewer shows: local app, a specific linked device, or all merged chronologically. */
sealed interface LogSourceFilter {
    data object Local : LogSourceFilter
    data class Device(val id: String, val name: String) : LogSourceFilter
    data object All : LogSourceFilter
}

data class SystemUiState(
    val update: UpdateCheckState = UpdateCheckState.Idle,
    /** Last lines of the selected log source; Loading until "Abrir registros" asks for them. */
    val logs: LoadState<List<String>> = LoadState.Loading,
    val logDevices: List<DeviceLogSource> = emptyList(),
    val logSource: LogSourceFilter = LogSourceFilter.Local,
    val pushingLogs: Boolean = false,
    val exportingHistory: Boolean = false,
    val clearingHistory: Boolean = false,
)

sealed interface SystemEvent {
    /** The history export document; the screen writes it to the SAF file the user picked. */
    data class HistoryExported(val json: String) : SystemEvent
    data object HistoryCleared : SystemEvent
    data object LogsCleared : SystemEvent
    data class LogsPushed(val count: Int) : SystemEvent
    data class Failed(val cause: AppError) : SystemEvent
}

class SystemViewModel(
    private val logs: LogRepository? = null,
    private val updates: UpdateRepository? = null,
    private val history: HistoryRepository? = null,
    private val remoteLogs: RemoteLogsRepository? = null,
    private val logPush: LogPushTrigger? = null,
) : ViewModel() {

    private val local = MutableStateFlow(SystemUiState())
    private val _events = Channel<SystemEvent>(Channel.BUFFERED)
    val events: Flow<SystemEvent> = _events.receiveAsFlow()

    val state: StateFlow<SystemUiState> = combine(local, updates?.latest ?: flowOf(null)) { l, latest ->
        // a newer release announced at startup shows up here too, without pressing the button
        if (latest != null && l.update == UpdateCheckState.Idle) l.copy(update = UpdateCheckState.Available(latest)) else l
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemUiState())

    val hasUpdates: Boolean get() = updates != null
    val hasLogs: Boolean get() = logs != null || remoteLogs != null
    val canPushLogs: Boolean get() = logPush != null

    fun checkUpdates() {
        val repo = updates ?: return
        if (local.value.update == UpdateCheckState.Checking) return
        local.update { it.copy(update = UpdateCheckState.Checking) }
        viewModelScope.launch {
            val result = runCatchingApp { repo.check(force = true).getOrThrow() }
            local.update {
                it.copy(
                    update = result.fold(
                        onSuccess = { info -> if (info == null) UpdateCheckState.UpToDate else UpdateCheckState.Available(info) },
                        onFailure = { e -> UpdateCheckState.Failed(e.toAppError()) },
                    ),
                )
            }
        }
    }

    fun selectLogSource(source: LogSourceFilter) {
        if (local.value.logSource == source) return
        local.update { it.copy(logSource = source) }
        loadLogs()
    }

    fun loadLogs() {
        val source = local.value.logSource
        local.update { it.copy(logs = LoadState.Loading) }
        viewModelScope.launch {
            val devices = runCatching { remoteLogs?.listDevices().orEmpty() }.getOrDefault(emptyList())
            val effectiveSource = if (source is LogSourceFilter.Device && devices.none { it.id == source.id }) {
                LogSourceFilter.Local
            } else {
                source
            }
            val result = runCatchingApp {
                when (effectiveSource) {
                    LogSourceFilter.Local -> logs?.recentLines(MAX_LOG_LINES).orEmpty()
                    is LogSourceFilter.Device -> remoteLogs?.recentLines(effectiveSource.id, MAX_LOG_LINES).orEmpty()
                    LogSourceFilter.All -> {
                        val localLines = logs?.recentLines(MAX_LOG_LINES).orEmpty().map { "[PC] $it" }
                        val remoteLines = remoteLogs?.recentLines(null, MAX_LOG_LINES).orEmpty()
                        (localLines + remoteLines).sortedBy { line ->
                            val start = line.indexOf("] ")
                            if (start > 0 && line.length > start + 24) line.substring(start + 2, start + 26) else line
                        }.takeLast(MAX_LOG_LINES)
                    }
                }
            }
            local.update { s ->
                s.copy(
                    logDevices = devices,
                    logSource = effectiveSource,
                    logs = result.fold(onSuccess = { LoadState.Ok(it) }, onFailure = { LoadState.Err(it.toAppError()) }),
                )
            }
        }
    }

    fun logFile(): File? = when (val src = local.value.logSource) {
        LogSourceFilter.Local -> logs?.logFile()
        is LogSourceFilter.Device -> remoteLogs?.logFile(src.id) ?: logs?.logFile()
        LogSourceFilter.All -> logs?.logFile()
    }

    fun logsFolder(): File? = remoteLogs?.logsDir() ?: logs?.logFile()?.parentFile

    fun clearLogs() {
        val src = local.value.logSource
        viewModelScope.launch {
            runCatchingApp {
                when (src) {
                    LogSourceFilter.Local -> logs?.clear()
                    is LogSourceFilter.Device -> remoteLogs?.clear(src.id)
                    LogSourceFilter.All -> {
                        logs?.clear()
                        remoteLogs?.clear(null)
                    }
                }
            }.fold(
                onSuccess = {
                    val devices = runCatching { remoteLogs?.listDevices().orEmpty() }.getOrDefault(emptyList())
                    local.update { it.copy(logs = LoadState.Ok(emptyList()), logDevices = devices) }
                    _events.trySend(SystemEvent.LogsCleared)
                },
                onFailure = { _events.trySend(SystemEvent.Failed(it.toAppError())) },
            )
        }
    }

    fun pushLogsToPc() {
        val trigger = logPush ?: return
        if (local.value.pushingLogs) return
        local.update { it.copy(pushingLogs = true) }
        viewModelScope.launch {
            val result = runCatchingApp { trigger.pushNow().getOrThrow() }
            local.update { it.copy(pushingLogs = false) }
            result.fold(
                onSuccess = { count -> _events.trySend(SystemEvent.LogsPushed(count)) },
                onFailure = { _events.trySend(SystemEvent.Failed(it.toAppError())) },
            )
        }
    }

    fun exportHistory() {
        val repo = history ?: return
        if (local.value.exportingHistory) return
        local.update { it.copy(exportingHistory = true) }
        viewModelScope.launch {
            val result = runCatchingApp { repo.exportJson() }
            local.update { it.copy(exportingHistory = false) }
            result.fold(
                onSuccess = { _events.trySend(SystemEvent.HistoryExported(it)) },
                onFailure = { _events.trySend(SystemEvent.Failed(it.toAppError())) },
            )
        }
    }

    /** [includeRemote] only after the "también en la PC" confirmation. */
    fun clearHistory(includeRemote: Boolean) {
        val repo = history ?: return
        if (local.value.clearingHistory) return
        local.update { it.copy(clearingHistory = true) }
        viewModelScope.launch {
            val result = runCatchingApp { repo.clearHistory(includeRemote).getOrThrow() }
            local.update { it.copy(clearingHistory = false) }
            result.fold(
                onSuccess = { _events.trySend(SystemEvent.HistoryCleared) },
                onFailure = { _events.trySend(SystemEvent.Failed(it.toAppError())) },
            )
        }
    }

    private companion object {
        const val MAX_LOG_LINES = 500
    }
}
