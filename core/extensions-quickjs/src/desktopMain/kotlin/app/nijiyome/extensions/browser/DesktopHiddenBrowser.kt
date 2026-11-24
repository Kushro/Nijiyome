// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.extensions.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * What Settings and the Extensions screen show about the desktop's hidden browser.
 *
 * @property browser the Chrome / Edge / Chromium in use; null = none (bundles get no browser, embed pages are not offered).
 * @property manualPath the path set in Settings (empty = automatic).
 * @property manualProblem why [manualPath] is not used; null when it is (or is empty).
 * @property sandboxed a Flatpak / Snap install that was found but is not used in 1.1.
 */
data class HiddenBrowserState(
    val browser: ChromiumExecutable? = null,
    val manualPath: String = "",
    val manualProblem: ChromiumLocator.Problem? = null,
    val sandboxed: String? = null,
) {
    val available: Boolean get() = browser != null
}

/**
 * The desktop's hidden browser for extensions (1.1, F4): finds the user's browser, keeps the path set in Settings in
 * `<data>/desktop-browser.json`, and owns the [ChromiumHost] shared by [runtime] and [sniffer].
 */
class DesktopHiddenBrowser(
    private val prefsFile: File,
    private val locator: ChromiumLocator = ChromiumLocator(),
    logs: app.nijiyome.extensions.ExtensionLogStore? = null,
) {
    private val _state = MutableStateFlow(HiddenBrowserState())
    val state: StateFlow<HiddenBrowserState> = _state.asStateFlow()

    val host = ChromiumHost(executable = { _state.value.browser })
    val runtime = CdpBrowserRuntime(host, logs)
    val sniffer = CdpEmbedSniffer(host, logs)

    init {
        refresh(readManualPath())
    }

    /** Looks for the browser again (the installed browsers may have changed); cheap, nothing is started. */
    fun refresh(manualPath: String = _state.value.manualPath) {
        val manual = manualPath.trim()
        _state.value = HiddenBrowserState(
            browser = locator.find(manual.ifEmpty { null }),
            manualPath = manual,
            manualProblem = manual.takeIf { it.isNotEmpty() }?.let(locator::check),
            sandboxed = locator.sandboxedInstall(),
        )
    }

    /** Settings: the browser path (empty = automatic). Saved, then the browser is looked for again. */
    fun setManualPath(path: String) {
        val p = path.trim()
        runCatching {
            prefsFile.parentFile?.mkdirs()
            val tmp = File(prefsFile.parentFile, "${prefsFile.name}.tmp")
            val json = JsonObject(if (p.isEmpty()) emptyMap() else mapOf("browser_path" to JsonPrimitive(p))).toString()
            tmp.writeText(json)
            if (!tmp.renameTo(prefsFile)) {
                prefsFile.writeText(json)
                tmp.delete()
            }
        }
        refresh(p)
    }

    /** Settings' "Probar": [path] (or the browser in use) started headless on its own; its product name or why it failed. */
    suspend fun probe(path: String? = null): Result<String> {
        val target = path?.trim()?.takeIf { it.isNotEmpty() }
        if (target != null) locator.check(target)?.let { return Result.failure(ProblemException(it)) }
        val exe = target ?: _state.value.browser?.path ?: return Result.failure(ProblemException(ChromiumLocator.Problem.MISSING))
        return ChromiumHost.probe(exe)
    }

    /** Why a path cannot be used, as a failure of [probe]. */
    class ProblemException(val problem: ChromiumLocator.Problem) : Exception(problem.name)

    private fun readManualPath(): String =
        runCatching { (Json.parseToJsonElement(prefsFile.readText()) as? JsonObject)?.get("browser_path") as? JsonPrimitive }
            .getOrNull()?.contentOrNull?.trim().orEmpty()

    companion object {
        const val PREFS_FILE = "desktop-browser.json"
    }
}
