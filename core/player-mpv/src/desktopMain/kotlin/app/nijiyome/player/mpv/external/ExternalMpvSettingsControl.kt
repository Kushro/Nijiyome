// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hito 4, paso D: what Settings › Reproductor needs on desktop for the external mpv: the settings themselves ([store]), whether mpv is
 * there (and its version), the state and the download of mpv-anime-build, and whether lsfg-vk can run. Bound by
 * [desktopExternalMpvModule].
 */
class ExternalMpvSettingsControl(
    val store: DesktopPlayerPrefsStore,
    private val dataDir: File,
    private val cacheDir: File,
    private val env: Map<String, String> = System.getenv(),
) {
    /** The mpv the external window would run, or null. Looks at a few files. */
    fun mpv(): File? = findMpv(store.prefs.value.mpvPath, env)

    /** First line of `mpv --version` ("mpv v0.42.0 Copyright ..."), null when it does not run. Blocking (starts mpv without a window). */
    suspend fun mpvVersion(mpv: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder(mpv.absolutePath, "--no-config", "--version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            if (!p.waitFor(VERSION_TIMEOUT_S, TimeUnit.SECONDS)) p.destroyForcibly()
            out.lineSequence().firstOrNull()?.trim()?.substringBefore(" Copyright")?.takeIf { it.startsWith("mpv") }
        }.getOrNull()
    }

    fun animeBuildState(): AnimeBuild.State = AnimeBuild.state(dataDir, cacheDir)

    /** The mpv-anime-build download as Settings shows it. */
    sealed interface Install {
        data object Idle : Install

        /** [progress] 0..1. */
        data class Running(val progress: Float) : Install

        data class Failed(val message: String) : Install

        data object Done : Install
    }

    private val _install = MutableStateFlow<Install>(Install.Idle)

    /**
     * The download of mpv-anime-build. It belongs to this (app-wide) object, not to the Settings screen: leaving Settings does not
     * restart it, coming back shows its progress, and there is never more than one.
     */
    val install: StateFlow<Install> = _install.asStateFlow()

    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    /** Downloads (pinned hash) and extracts mpv-anime-build, then turns the enhanced mode on. Does nothing while one runs. */
    @Synchronized
    fun startInstall() {
        if (installJob?.isActive == true) return
        _install.value = Install.Running(0f)
        installJob = installScope.launch {
            val job = coroutineContext[Job]
            var call: okhttp3.Call? = null
            job?.invokeOnCompletion { if (it != null) call?.cancel() }
            val result = runCatching {
                var shown = -1
                val zip = AnimeBuild.download(
                    cacheDir,
                    isCancelled = { job?.isActive == false },
                    onCall = { call = it },
                ) { done, total ->
                    val f = (done.toFloat() / total).coerceIn(0f, 1f)
                    val percent = (f * 100).toInt()
                    if (percent != shown) {
                        shown = percent
                        _install.value = Install.Running(f)
                    }
                }
                AnimeBuild.prepare(zip, AnimeBuild.configDir(dataDir))
            }
            _install.value = result.fold(
                onSuccess = {
                    store.update { it.copy(enhanced = true) }
                    Install.Done
                },
                onFailure = { if (job?.isActive == false) Install.Idle else Install.Failed(it.message ?: it.toString()) },
            )
        }
    }

    /** Stops the download (its `.part` file is removed). */
    fun cancelInstall() {
        installJob?.cancel()
    }

    /** Whether lsfg-vk can run (the layer and the shaders), with the files found. Reads a few small files. */
    fun frameGen(): LsfgVk.Availability = LsfgVk.detect(store.prefs.value.lsfgDll, env)

    private companion object {
        const val VERSION_TIMEOUT_S = 5L
    }
}
