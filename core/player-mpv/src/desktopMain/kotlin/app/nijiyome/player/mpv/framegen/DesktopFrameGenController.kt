// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.framegen

import app.nijiyome.player.mpv.displayRefreshRate
import app.nijiyome.player.mpv.external.DesktopPlayerPrefs
import app.nijiyome.player.mpv.external.DesktopPlayerPrefsStore
import app.nijiyome.player.mpv.external.LsfgVk
import app.nijiyome.player.mpv.quality.mpvLogInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToLong

enum class DesktopFgUnavailable {
    NO_LIBRARY,
    NO_DLL,
    NO_VULKAN,
    DEVICE_UNSUPPORTED,
    FAILED,
}

/** Reactive UI + runtime state of embedded desktop Frame Generation. */
data class DesktopFrameGenState(
    val enabled: Boolean = false,
    val active: Boolean = false,
    val auto: Boolean = true,
    val requestedMultiplier: Int = 0,
    val multiplier: Int = 2,
    val performance: Boolean = false,
    val flowScale: Float = LsfgVk.DEFAULT_FLOW_SCALE,
    val antiArtifacts: Boolean = true,
    val transport: DesktopFgTransport = DesktopFgTransport.NONE,
    val deviceName: String = "",
    val dllFound: Boolean = false,
    val eligibilityKnown: Boolean = false,
    val unavailable: DesktopFgUnavailable? = null,
    val inFps: Double = 0.0,
    val outFps: Double = 0.0,
    val computeMs: Double = 0.0,
    val latencyMs: Double = 0.0,
    val droppedLate: Long = 0L,
    val overloaded: Boolean = false,
    val errorDetail: String = "",
)

/**
 * Coordinates the native LSFG 3.1 / 3.1P engine ([DesktopFrameGenNative]) with one embedded
 * desktop mpv session: persists settings into [DesktopPlayerPrefsStore], probes GPU/DLL
 * availability, manages `audio-delay` compensation while active, and exposes reactive state
 * to the player's Mejoras panel and [app.nijiyome.player.mpv.MpvVideoSurface].
 */
class DesktopFrameGenController(
    private val prefsStore: DesktopPlayerPrefsStore?,
    private val scope: CoroutineScope,
    private val readAudioDelay: () -> Double?,
    private val writeAudioDelay: (Double) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
    private val refreshHz: () -> Float? = ::displayRefreshRate,
) {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DesktopFrameGenState> = _state.asStateFlow()

    @Volatile
    private var nativeHandle: Long = 0L

    @Volatile
    private var startedKey: StartKey? = null

    @Volatile
    private var released = false

    @Volatile
    private var currentDll: File? = null

    @Volatile
    private var currentSourceFps: Double = LsfgVk.SOURCE_FPS

    // Audio-delay compensation tracking (mirrors core/framegen FgAudioDelay + FgPolicy).
    private var audioCompActive = false
    private var appliedAudioCompSec = 0.0
    private var lastAudioUpdateMs = 0L

    private var prefsJob: Job? = null
    private var statsJob: Job? = null

    private data class StartKey(
        val dllPath: String,
        val width: Int,
        val height: Int,
        val multiplier: Int,
        val flowScale: Float,
        val performance: Boolean,
    )

    init {
        prefsStore?.let { store ->
            prefsJob = scope.launch {
                store.prefs.collect { p ->
                    if (!released) applyPrefs(p)
                }
            }
        }
        scope.launch { refreshEligibility() }
        statsJob = scope.launch {
            while (isActive && !released) {
                delay(STATS_POLL_MS)
                pollStats()
            }
        }
    }

    private fun initialState(): DesktopFrameGenState {
        val p = prefsStore?.prefs?.value ?: DesktopPlayerPrefs()
        val eff = LsfgVk.multiplierFor(p.frameGenMultiplier, refreshHz()?.toDouble())
        return DesktopFrameGenState(
            enabled = p.frameGen,
            auto = p.frameGenMultiplier == 0,
            requestedMultiplier = p.frameGenMultiplier,
            multiplier = eff,
            performance = p.frameGenPerformance,
            flowScale = p.frameGenFlowScale,
            antiArtifacts = p.frameGenAntiArtifacts,
        )
    }

    private fun applyPrefs(p: DesktopPlayerPrefs) {
        val prevDll = currentDll?.absolutePath
        val eff = LsfgVk.multiplierFor(p.frameGenMultiplier, refreshHz()?.toDouble())
        _state.update { s ->
            s.copy(
                enabled = p.frameGen,
                auto = p.frameGenMultiplier == 0,
                requestedMultiplier = p.frameGenMultiplier,
                multiplier = eff,
                performance = p.frameGenPerformance,
                flowScale = p.frameGenFlowScale,
                antiArtifacts = p.frameGenAntiArtifacts,
            )
        }
        val h = nativeHandle
        if (h != 0L) DesktopFrameGenNative.nSetAntiArtifacts(h, p.frameGenAntiArtifacts)
        if (!p.frameGen) {
            clearAudioComp()
            onActiveChanged(false)
        }
        if (p.lsfgDll != prevDll) {
            scope.launch { refreshEligibility() }
        }
    }

    suspend fun refreshEligibility() {
        val configured = prefsStore?.prefs?.value?.lsfgDll
        val (dll, probe) = withContext(Dispatchers.IO) {
            val foundDll = DesktopFrameGenNative.findLosslessDll(configured)
            val p = if (!DesktopFrameGenNative.ensureLoaded()) {
                DesktopFgProbe(ok = false, errorCode = "no_library")
            } else if (foundDll == null) {
                DesktopFrameGenNative.probe(null).copy(ok = false, errorCode = "no_dll")
            } else {
                DesktopFrameGenNative.probe(foundDll.absolutePath)
            }
            foundDll to p
        }
        currentDll = dll
        val unavail = when {
            probe.ok -> null
            probe.errorCode == "no_library" -> DesktopFgUnavailable.NO_LIBRARY
            probe.errorCode == "no_dll" || probe.errorCode == "dll_unreadable" || probe.errorCode == "missing_spirv" ->
                DesktopFgUnavailable.NO_DLL
            probe.errorCode == "no_vulkan" -> DesktopFgUnavailable.NO_VULKAN
            probe.errorCode == "device_unsupported" -> DesktopFgUnavailable.DEVICE_UNSUPPORTED
            else -> DesktopFgUnavailable.FAILED
        }
        _state.update { s ->
            s.copy(
                deviceName = probe.deviceName,
                dllFound = dll != null && unavail != DesktopFgUnavailable.NO_DLL,
                eligibilityKnown = true,
                unavailable = unavail,
                errorDetail = if (unavail == DesktopFgUnavailable.FAILED) probe.errorCode else "",
            )
        }
    }

    // ---- UI actions (from Mejoras panel) ---------------------------------------------------------------------------------------

    fun setEnabled(enabled: Boolean) {
        if (prefsStore != null) {
            prefsStore.update { it.copy(frameGen = enabled) }
        } else {
            _state.update { it.copy(enabled = enabled) }
            if (!enabled) {
                clearAudioComp()
                onActiveChanged(false)
            }
        }
    }

    fun setMultiplier(requested: Int) {
        val clean = if (requested == 0 || requested in 2..4) requested else 0
        if (prefsStore != null) {
            prefsStore.update { it.copy(frameGenMultiplier = clean) }
        } else {
            val eff = LsfgVk.multiplierFor(clean, refreshHz()?.toDouble())
            _state.update { it.copy(auto = clean == 0, requestedMultiplier = clean, multiplier = eff) }
        }
    }

    fun setPerformance(performance: Boolean) {
        if (prefsStore != null) {
            prefsStore.update { it.copy(frameGenPerformance = performance) }
        } else {
            _state.update { it.copy(performance = performance) }
        }
    }

    fun setAntiArtifacts(enabled: Boolean) {
        if (prefsStore != null) {
            prefsStore.update { it.copy(frameGenAntiArtifacts = enabled) }
        } else {
            _state.update { it.copy(antiArtifacts = enabled) }
            val h = nativeHandle
            if (h != 0L) DesktopFrameGenNative.nSetAntiArtifacts(h, enabled)
        }
    }

    // ---- mpv playback lifecycle hooks ------------------------------------------------------------------------------------------

    fun onPaused(paused: Boolean) {
        val h = nativeHandle
        if (h != 0L) DesktopFrameGenNative.nSetPaused(h, paused)
        if (paused) clearAudioComp()
    }

    fun onSeekOrReload() {
        val h = nativeHandle
        if (h != 0L) DesktopFrameGenNative.nFlush(h)
    }

    fun onVideoFps(fps: Double) {
        if (!fps.isFinite() || fps <= 1.0) return
        currentSourceFps = fps
        val h = nativeHandle
        if (h != 0L) DesktopFrameGenNative.nSetSourcePeriodNs(h, (1_000_000_000.0 / fps).roundToLong())
    }

    // ---- Surface draw-thread hooks (called on Skiko GL / D3D12 thread!) --------------------------------------------------------

    /**
     * Ensures the native engine is started and matches the current viewport `(width, height)` and config.
     * Returns the active [DesktopFgTransport], or [DesktopFgTransport.NONE] when disabled or unavailable.
     * Must be called on Skiko's graphics thread.
     */
    fun ensureStartedOnRenderThread(
        width: Int,
        height: Int,
        d3d12DeviceStructPtr: Long,
        vsyncNs: Long,
    ): DesktopFgTransport {
        val st = _state.value
        if (released || !st.enabled || st.unavailable != null || width <= 0 || height <= 0) {
            stopOnRenderThread()
            return DesktopFgTransport.NONE
        }
        val dll = currentDll ?: return DesktopFgTransport.NONE
        val key = StartKey(
            dllPath = dll.absolutePath,
            width = width,
            height = height,
            multiplier = st.multiplier,
            flowScale = st.flowScale,
            performance = st.performance,
        )
        if (nativeHandle != 0L && startedKey == key && st.active) {
            DesktopFrameGenNative.nSetVsyncNs(nativeHandle, vsyncNs)
            return st.transport
        }
        stopOnRenderThread()
        if (!DesktopFrameGenNative.ensureLoaded()) return DesktopFgTransport.NONE
        val h = DesktopFrameGenNative.nCreate()
        if (h == 0L) return DesktopFgTransport.NONE
        val transportCode = DesktopFrameGenNative.nStart(
            handle = h,
            dllPath = key.dllPath,
            width = key.width,
            height = key.height,
            multiplier = key.multiplier,
            flowScale = key.flowScale,
            performance = key.performance,
            antiArtifacts = st.antiArtifacts,
            vsyncNs = vsyncNs,
            forceTransport = DesktopFrameGenNative.forcedTransportCode(),
            d3d12DeviceStructPtr = d3d12DeviceStructPtr,
        )
        val transport = DesktopFgTransport.fromCode(transportCode)
        if (transport == DesktopFgTransport.NONE) {
            val err = DesktopFrameGenNative.readStats(h).errorCode
            mpvLogInfo("embedded framegen failed to start (${key.width}x${key.height} x${key.multiplier}): $err")
            DesktopFrameGenNative.nDestroy(h)
            _state.update { it.copy(active = false, transport = DesktopFgTransport.NONE, unavailable = DesktopFgUnavailable.FAILED, errorDetail = err) }
            onActiveChanged(false)
            return DesktopFgTransport.NONE
        }
        mpvLogInfo(
            "embedded framegen started: ${key.width}x${key.height} x${key.multiplier} " +
                "(${if (key.performance) "3.1P" else "3.1"}) via ${transport.name} " +
                "(refresh=${refreshHz()?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: "unknown"}Hz)",
        )
        nativeHandle = h
        startedKey = key
        if (currentSourceFps > 1.0) {
            DesktopFrameGenNative.nSetSourcePeriodNs(h, (1_000_000_000.0 / currentSourceFps).roundToLong())
        }
        _state.update { it.copy(active = true, transport = transport, unavailable = null, errorDetail = "") }
        onActiveChanged(true)
        // Initial audio-delay compensation: ~1 source frame + 6 ms margin until live latencyMs arrives.
        applyAudioComp((1000.0 / currentSourceFps.coerceAtLeast(15.0)) + 6.0)
        return transport
    }

    /** Stops and destroys native GPU resources. Must be called on Skiko's graphics thread. */
    fun stopOnRenderThread() {
        val h = nativeHandle
        nativeHandle = 0L
        startedKey = null
        if (h != 0L) {
            DesktopFrameGenNative.nStop(h)
            DesktopFrameGenNative.nDestroy(h)
        }
        if (_state.value.active) {
            _state.update { it.copy(active = false, transport = DesktopFgTransport.NONE, inFps = 0.0, outFps = 0.0) }
            onActiveChanged(false)
        }
        clearAudioComp()
    }

    fun submitGlFrame(srcGlTexId: Int, timestampNs: Long): Boolean {
        val h = nativeHandle
        return h != 0L && DesktopFrameGenNative.nSubmitGlFrame(h, srcGlTexId, timestampNs)
    }

    fun submitD3d12Frame(srcResourcePtr: Long, timestampNs: Long): Boolean {
        val h = nativeHandle
        return h != 0L && DesktopFrameGenNative.nSubmitD3d12Frame(h, srcResourcePtr, timestampNs)
    }

    fun submitRgbaFrame(buf: ByteBuffer, bytes: Int, timestampNs: Long): Boolean {
        val h = nativeHandle
        return h != 0L && DesktopFrameGenNative.nSubmitRgbaFrame(h, buf, bytes, timestampNs)
    }

    fun acquirePresentSlot(nowNs: Long): Int {
        val h = nativeHandle
        return if (h != 0L) DesktopFrameGenNative.nAcquirePresentSlot(h, nowNs) else -1
    }

    fun slotCount(): Int {
        val h = nativeHandle
        return if (h != 0L) DesktopFrameGenNative.nSlotCount(h) else 0
    }

    fun slotHandle(slot: Int): Long {
        val h = nativeHandle
        return if (h != 0L) DesktopFrameGenNative.nSlotHandle(h, slot) else 0L
    }

    fun readSlotRgba(slot: Int, buf: ByteBuffer, bytes: Int): Boolean {
        val h = nativeHandle
        return h != 0L && DesktopFrameGenNative.nReadSlotRgba(h, slot, buf, bytes)
    }

    private fun pollStats() {
        val h = nativeHandle
        if (h == 0L || released) return
        val s = DesktopFrameGenNative.readStats(h)
        _state.update { cur ->
            cur.copy(
                active = s.active,
                transport = s.transport,
                inFps = s.inFps,
                outFps = s.outFps,
                computeMs = s.computeMs,
                latencyMs = s.latencyMs,
                droppedLate = s.droppedLate,
                overloaded = s.overloaded,
                errorDetail = s.errorCode,
            )
        }
        if (s.active && s.latencyMs > 1.0) {
            applyAudioComp(s.latencyMs)
        }
        if (LOG_STATS && s.active) {
            mpvLogInfo(
                "embedded framegen stats: ${s.transport.name} x${s.multiplier} " +
                    "in=${"%.1f".format(java.util.Locale.ROOT, s.inFps)}fps out=${"%.1f".format(java.util.Locale.ROOT, s.outFps)}fps " +
                    "compute=${"%.2f".format(java.util.Locale.ROOT, s.computeMs)}ms lat=${"%.1f".format(java.util.Locale.ROOT, s.latencyMs)}ms " +
                    "inF=${s.inFrames} outF=${s.outFrames} genF=${s.generatedFrames} drop=${s.droppedLate} cuts=${s.sceneCuts}",
            )
        }
    }

    // ---- Audio-delay compensation (preserves user's manual audio-delay) --------------------------------------------------------

    private fun applyAudioComp(latencyMs: Double) {
        val now = System.nanoTime() / 1_000_000L
        val offsetMs = prefsStore?.prefs?.value?.frameGenAudioOffsetMs ?: 0
        // Quantize to 5 ms steps to avoid churning mpv's audio-delay on normal jitter.
        val quantizedMs = (((latencyMs + offsetMs) / 5.0).roundToLong() * 5L).coerceIn(-500L, 500L)
        val wantCompSec = quantizedMs / 1000.0
        if (audioCompActive && abs(wantCompSec - appliedAudioCompSec) < 0.010 && (now - lastAudioUpdateMs) < 2_000L) {
            return
        }
        val currentEff = readAudioDelay() ?: 0.0
        val userPart = if (audioCompActive) currentEff - appliedAudioCompSec else currentEff
        writeAudioDelay(userPart + wantCompSec)
        appliedAudioCompSec = wantCompSec
        audioCompActive = true
        lastAudioUpdateMs = now
    }

    private fun clearAudioComp() {
        if (!audioCompActive) return
        val currentEff = readAudioDelay() ?: 0.0
        writeAudioDelay(currentEff - appliedAudioCompSec)
        appliedAudioCompSec = 0.0
        audioCompActive = false
    }

    fun release() {
        if (released) return
        released = true
        prefsJob?.cancel()
        statsJob?.cancel()
        clearAudioComp()
        // If surface already disposed, nativeHandle is already 0; otherwise ensure cleanup.
        val h = nativeHandle
        nativeHandle = 0L
        if (h != 0L) {
            DesktopFrameGenNative.nStop(h)
            DesktopFrameGenNative.nDestroy(h)
        }
    }

    companion object {
        private const val STATS_POLL_MS = 500L
        private val LOG_STATS = System.getenv("NIJIYOME_FG_LOG") == "1"

        /** Quick availability check for Settings / ExternalMpvSessionFactory (off UI thread). */
        fun probeEmbeddedAvailability(configuredDll: String?): DesktopFgProbe {
            if (!DesktopFrameGenNative.ensureLoaded()) {
                return DesktopFgProbe(ok = false, errorCode = "no_library")
            }
            val dll = DesktopFrameGenNative.findLosslessDll(configuredDll)
                ?: return DesktopFrameGenNative.probe(null).copy(ok = false, errorCode = "no_dll")
            return DesktopFrameGenNative.probe(dll.absolutePath)
        }
    }
}
