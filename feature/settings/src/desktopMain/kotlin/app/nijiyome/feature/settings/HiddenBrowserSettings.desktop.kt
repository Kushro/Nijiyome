// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.extensions.browser.ChromiumLocator
import app.nijiyome.extensions.browser.DesktopHiddenBrowser
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.settings_hbrowser
import app.nijiyome.feature.settings.resources.settings_hbrowser_bad_exec
import app.nijiyome.feature.settings.resources.settings_hbrowser_bad_missing
import app.nijiyome.feature.settings.resources.settings_hbrowser_bad_sandboxed
import app.nijiyome.feature.settings.resources.settings_hbrowser_found
import app.nijiyome.feature.settings.resources.settings_hbrowser_hint
import app.nijiyome.feature.settings.resources.settings_hbrowser_missing
import app.nijiyome.feature.settings.resources.settings_hbrowser_path
import app.nijiyome.feature.settings.resources.settings_hbrowser_path_hint
import app.nijiyome.feature.settings.resources.settings_hbrowser_sandboxed
import app.nijiyome.feature.settings.resources.settings_hbrowser_test
import app.nijiyome.feature.settings.resources.settings_hbrowser_test_failed
import app.nijiyome.feature.settings.resources.settings_hbrowser_test_ok
import app.nijiyome.feature.settings.resources.settings_hbrowser_testing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// 1.1 F4: Settings › Reproductor on desktop, above "Fuentes embed": which Chrome / Edge / Chromium the hidden browser uses, a path to
// set by hand (validated: it must exist, be executable and not be a Flatpak / Snap launcher) and "Probar". The embed rows show only
// while a browser is there; without one they stay hidden, as before 1.1.

private sealed interface ProbeState {
    data object Idle : ProbeState
    data object Running : ProbeState
    data class Done(val ok: Boolean, val text: String) : ProbeState
}

@Composable
internal actual fun EmbedSourcesRows(rows: @Composable () -> Unit) {
    val koin = getKoin()
    val browser = remember { koin.getOrNull<DesktopHiddenBrowser>() } ?: return
    val state by browser.state.collectAsState()
    // installed browsers may have changed since the app started
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { browser.refresh() } }
    var probe by remember { mutableStateOf<ProbeState>(ProbeState.Idle) }
    val scope = rememberCoroutineScope()

    val status = when {
        state.browser != null -> stringResource(Res.string.settings_hbrowser_found, state.browser!!.path)
        state.sandboxed != null -> stringResource(Res.string.settings_hbrowser_sandboxed, state.sandboxed!!)
        else -> stringResource(Res.string.settings_hbrowser_missing)
    }
    val manualError = state.manualProblem?.let { stringResource(it.res()) }
    SettingRow(
        stringResource(Res.string.settings_hbrowser),
        listOfNotNull(stringResource(Res.string.settings_hbrowser_hint), status).joinToString(" "),
        stack = true,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PathField(
                value = state.manualPath,
                label = stringResource(Res.string.settings_hbrowser_path),
                hint = stringResource(Res.string.settings_hbrowser_path_hint),
                onDone = { path ->
                    probe = ProbeState.Idle
                    browser.setManualPath(path.orEmpty())
                },
            )
            manualError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            val failedText = stringResource(Res.string.settings_hbrowser_test_failed, "%s")
            val okText = stringResource(Res.string.settings_hbrowser_test_ok, "%s")
            val problemTexts = ChromiumLocator.Problem.entries.associateWith { stringResource(it.res()) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    enabled = probe != ProbeState.Running && (state.browser != null || state.manualPath.isNotEmpty()),
                    onClick = {
                        probe = ProbeState.Running
                        scope.launch {
                            val result = browser.probe(state.manualPath.ifEmpty { null })
                            probe = result.fold(
                                onSuccess = { ProbeState.Done(true, okText.replace("%s", it)) },
                                onFailure = { e ->
                                    val why = (e as? DesktopHiddenBrowser.ProblemException)?.let { problemTexts[it.problem] } ?: (e.message ?: e.javaClass.simpleName)
                                    ProbeState.Done(false, failedText.replace("%s", why))
                                },
                            )
                        }
                    },
                ) { Text(stringResource(Res.string.settings_hbrowser_test)) }
                when (val p = probe) {
                    ProbeState.Idle -> Unit
                    ProbeState.Running -> Text(stringResource(Res.string.settings_hbrowser_testing), style = MaterialTheme.typography.bodySmall)
                    is ProbeState.Done -> Text(
                        p.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (p.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (state.available) rows()
}

private fun ChromiumLocator.Problem.res(): StringResource = when (this) {
    ChromiumLocator.Problem.MISSING -> Res.string.settings_hbrowser_bad_missing
    ChromiumLocator.Problem.NOT_EXECUTABLE -> Res.string.settings_hbrowser_bad_exec
    ChromiumLocator.Problem.SANDBOXED -> Res.string.settings_hbrowser_bad_sandboxed
}
