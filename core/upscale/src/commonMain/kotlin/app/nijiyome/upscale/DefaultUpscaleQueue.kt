// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The in-memory queue of plan §4.4: one job on the GPU at a time, newest request first (LIFO), reference-counted tickets (a key
 * whose last ticket is released is dropped while queued and cancelled between tiles while running), duplicates merged by key, at most
 * [UpscaleLimits.maxPending] pending (the oldest are dropped).
 *
 * Pausing: [pauseReasons] is fed by the platform (player open, frame generation, Link, app hidden, power gates; NOT_CHARGING only when
 * [ImageUpscaleSettings.onlyWhileCharging] is on). While any reason holds, or the settings turn the feature off, nothing starts, a
 * running job is cancelled and requeued, and the engine is released at once. With nothing to do the engine is released after
 * [UpscaleLimits.idleReleaseMillis]. No GPU ([UpscaleEngine.probe] null) or an engine that fails before its first success turns the
 * queue off for the session: requests are then skipped and the UI keeps the originals. There is never a CPU fallback.
 *
 * [request], [cached], [state] and ticket releases are cheap and may be called from the UI thread; the work (source reads, decode,
 * engine, encode, cache writes) runs on [worker], a single thread.
 */
class DefaultUpscaleQueue(
    private val engine: UpscaleEngine,
    private val source: UpscaleSourceProvider,
    private val codec: UpscaleImageCodec,
    private val cache: UpscaleResultCache,
    private val settings: StateFlow<ImageUpscaleSettings>,
    private val pauseReasons: StateFlow<Set<UpscalePauseReason>>,
    private val scope: CoroutineScope,
    private val limits: UpscaleLimits = platformUpscaleLimits(),
    private val worker: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UpscaleQueue {

    private class Job(
        val key: String,
        val url: String,
        val kind: UpscaleImageKind,
        val targetWidth: Int,
        val targetHeight: Int,
    ) {
        var refs = 0
        val cancelled = AtomicBoolean(false)
    }

    private inner class Ticket(override val key: String) : UpscaleTicket {
        private val released = AtomicBoolean(false)
        override fun release() {
            if (released.compareAndSet(false, true)) releaseKey(key)
        }
    }

    private val lock = Any()

    /** Keys with at least one live ticket. */
    private val live = HashMap<String, Job>()

    /** Queued jobs, newest last. */
    private val pending = ArrayDeque<Job>()
    private var running: Job? = null
    private val states = HashMap<String, MutableStateFlow<UpscaleState>>()

    private var probed = false
    private var gpuName: String? = null
    private var noGpu = false
    private var engineFailed = false
    private var anySuccess = false

    /** The engine ran since its last release, so it may hold VRAM. Worker only. */
    private var warm = false
    private var lastMillis: Long? = null

    private val wake = MutableStateFlow(0L)
    private val statusFlow = MutableStateFlow(UpscaleStatus(null, 0, false, null, 0L, null))
    override val status: StateFlow<UpscaleStatus> = statusFlow.asStateFlow()

    init {
        scope.launch {
            combine(settings, pauseReasons) { s, _ -> s }.collect { s -> onConditionsChanged(s) }
        }
        scope.launch { cache.clears.drop(1).collect { onCacheCleared() } }
        scope.launch { cache.bytes.collect { publishStatus() } }
        scope.launch(worker) { runLoop() }
    }

    override fun cached(url: String, kind: UpscaleImageKind, targetWidth: Int, targetHeight: Int): String? {
        val s = settings.value
        if (!s.enabled || kind !in s.kinds) return null
        return cache.peek(UpscaleKey.of(url, kind, s, targetWidth, targetHeight))
    }

    override fun request(
        url: String,
        kind: UpscaleImageKind,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): UpscaleTicket {
        val s = settings.value
        val key = UpscaleKey.of(url, kind, s, targetWidth, targetHeight)
        val ticket = Ticket(key)
        val queued = synchronized(lock) {
            val job = live.getOrPut(key) {
                Job(key, url, kind, UpscaleKey.bucket(targetWidth), UpscaleKey.bucket(targetHeight))
            }
            job.refs++
            admitLocked(job, s, sourceWidth, sourceHeight)
        }
        if (queued) signal()
        publishStatus()
        return ticket
    }

    override fun state(key: String): StateFlow<UpscaleState> = synchronized(lock) { stateLocked(key) }.asStateFlow()

    /**
     * Coil fetched [url] from the network again (`DataSource.NETWORK`): results made from other bytes are deleted and the live ones
     * are queued again (plan §4.5).
     */
    override fun sourceRefreshed(url: String) {
        scope.launch(worker) {
            val keys = cache.keysFor(url)
            if (keys.isEmpty()) return@launch
            val bytes = source.read(url) ?: return@launch
            val hash = UpscaleKey.sourceHash(bytes)
            var queued = false
            for (key in keys) {
                if (cache.entry(key)?.sourceHash == hash) continue
                cache.remove(key)
                if (synchronized(lock) { requeueRefreshedLocked(key) }) queued = true
            }
            if (queued) signal()
            publishStatus()
        }
    }

    /** A result of [key] was dropped because its source changed; true when its live request was queued again. Under [lock]. */
    private fun requeueRefreshedLocked(key: String): Boolean {
        val job = live[key]
        if (job == null) {
            states[key]?.value = UpscaleState.Idle
            return false
        }
        if (running === job || job in pending || !settings.value.enabled || noGpu || engineFailed) return false
        enqueueLocked(job)
        return true
    }

    // --- admission ---

    /** Decides what a request does with the state of its key; true when it queued a job. Under [lock]. */
    private fun admitLocked(job: Job, s: ImageUpscaleSettings, sourceWidth: Int, sourceHeight: Int): Boolean {
        val state = stateLocked(job.key)
        when (val current = state.value) {
            UpscaleState.Running -> return false
            UpscaleState.Queued -> {
                // Visible again: move to the top.
                pending.remove(job)
                pending.addLast(job)
                return false
            }
            is UpscaleState.Failed -> return false
            is UpscaleState.Done -> if (cache.peek(job.key) == current.path) return false
            else -> Unit
        }
        if (noGpu) return skipLocked(state, "no GPU")
        if (engineFailed) return skipLocked(state, "engine failed")
        cache.peek(job.key)?.let { path ->
            state.value = UpscaleState.Done(path)
            return false
        }
        // The decoded size the UI saw may be a downsampled one (Coil decodes at the view size); the worker decides again on the real
        // source pixels, so this check can only let through more than needed, never less.
        when (val decision = UpscalePlanner.decide(s, limits, job.kind, sourceWidth, sourceHeight, job.targetWidth, job.targetHeight)) {
            is UpscaleDecision.Skip -> return skipLocked(state, decision.why)
            is UpscaleDecision.Upscale -> Unit
        }
        enqueueLocked(job)
        return true
    }

    private fun skipLocked(state: MutableStateFlow<UpscaleState>, why: String): Boolean {
        state.value = UpscaleState.Skipped(why)
        return false
    }

    private fun enqueueLocked(job: Job) {
        job.cancelled.set(false)
        pending.remove(job)
        pending.addLast(job)
        stateLocked(job.key).value = UpscaleState.Queued
        while (pending.size > limits.maxPending) {
            val dropped = pending.removeFirst()
            stateLocked(dropped.key).value = UpscaleState.Idle
        }
    }

    private fun releaseKey(key: String) {
        synchronized(lock) {
            val job = live[key] ?: return
            job.refs--
            if (job.refs > 0) return
            live.remove(key)
            if (running === job) {
                // Cut between tiles; the worker drops it when the engine returns.
                job.cancelled.set(true)
                return
            }
            pending.remove(job)
            states[key]?.let { if (it.value == UpscaleState.Queued) it.value = UpscaleState.Idle }
            pruneStatesLocked()
        }
        publishStatus()
    }

    // --- conditions ---

    private fun onConditionsChanged(s: ImageUpscaleSettings) {
        cache.setMaxBytes(s.cacheBytes(limits))
        synchronized(lock) {
            if (!s.enabled) {
                for (job in pending) stateLocked(job.key).value = UpscaleState.Skipped("disabled")
                pending.clear()
            }
            cancelRunningIfPausedLocked()
        }
        signal()
        publishStatus()
    }

    private fun cancelRunningIfPausedLocked() {
        if (pauseReasonLocked() != null) running?.let { it.cancelled.set(true) }
    }

    private fun onCacheCleared() {
        synchronized(lock) {
            for (state in states.values) if (state.value is UpscaleState.Done) state.value = UpscaleState.Idle
        }
        publishStatus()
    }

    /** Internal reasons first, then the platform's in declaration order. Under [lock]. */
    private fun pauseReasonLocked(): UpscalePauseReason? = when {
        !settings.value.enabled -> UpscalePauseReason.DISABLED
        noGpu -> UpscalePauseReason.NO_GPU
        engineFailed -> UpscalePauseReason.ENGINE_FAILED
        else -> pauseReasons.value.let { external -> UpscalePauseReason.entries.firstOrNull { it in external } }
    }

    // --- worker ---

    private suspend fun runLoop() {
        cache.load()
        publishStatus()
        while (true) {
            val seen = wake.value
            val next = synchronized(lock) {
                if (pauseReasonLocked() == null) pending.removeLastOrNull()?.also { running = it } else null
            }
            if (next == null) {
                val paused = synchronized(lock) { pauseReasonLocked() != null }
                if (paused && warm) releaseEngine()
                cache.flushAccessTimes()
                publishStatus()
                val woke = waitForWake(seen, if (warm) limits.idleReleaseMillis else null)
                if (!woke && warm) releaseEngine()
                continue
            }
            publishStatus()
            val outcome = try {
                process(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                UpscaleState.Failed(e.message ?: e::class.simpleName ?: "error")
            }
            finish(next, outcome)
            cache.flushAccessTimes()
            publishStatus()
            delay(limits.pauseBetweenJobsMillis)
        }
    }

    private suspend fun waitForWake(seen: Long, timeoutMillis: Long?): Boolean {
        if (timeoutMillis == null) {
            wake.first { it != seen }
            return true
        }
        return withTimeoutOrNull(timeoutMillis) { wake.first { it != seen } } != null
    }

    /** Null when the job was cancelled (released, or paused). */
    private suspend fun process(job: Job): UpscaleState? {
        synchronized(lock) { stateLocked(job.key).value = UpscaleState.Running }
        val started = clock()
        val bytes = source.read(job.url) ?: return UpscaleState.Skipped("source not cached")
        if (job.cancelled.get()) return null
        val hash = UpscaleKey.sourceHash(bytes)
        cache.lookup(job.key, hash)?.let { return UpscaleState.Done(it) }

        val image = codec.decode(bytes) ?: return UpscaleState.Failed("decode failed")
        val plan = when (
            val decision = UpscalePlanner.decide(settings.value, limits, job.kind, image.width, image.height, job.targetWidth, job.targetHeight)
        ) {
            is UpscaleDecision.Skip -> return UpscaleState.Skipped(decision.why)
            is UpscaleDecision.Upscale -> decision.plan
        }
        if (!probeOnce()) return null
        if (job.cancelled.get()) return null

        warm = true
        val out = engine.upscale(image.rgba, image.width, image.height, plan.scale) { job.cancelled.get() }
        if (out == null) {
            if (job.cancelled.get()) return null
            if (!anySuccess) turnOff { engineFailed = true }
            return UpscaleState.Failed("engine failed")
        }
        anySuccess = true
        val upscaled = runCatching { RgbaImage(image.width * plan.scale, image.height * plan.scale, out) }.getOrNull()
            ?: return UpscaleState.Failed("engine returned a bad buffer")
        val encoded = codec.encode(upscaled, plan.outWidth, plan.outHeight) ?: return UpscaleState.Failed("encode failed")
        val entry = UpscaleCacheEntry(job.url, job.kind, hash, plan.scale, plan.outWidth, plan.outHeight)
        val path = cache.put(job.key, entry, encoded)
        lastMillis = clock() - started
        return UpscaleState.Done(path)
    }

    /** Probes the GPU the first time; false when there is none (the queue is then off for the session). */
    private fun probeOnce(): Boolean {
        if (!probed) {
            probed = true
            val name = runCatching { engine.probe() }.getOrNull()
            gpuName = name
            if (name == null) turnOff { noGpu = true }
        }
        return !noGpu
    }

    /** No GPU or a broken engine: every pending job is skipped and nothing new is queued. */
    private fun turnOff(mark: () -> Unit) {
        synchronized(lock) {
            mark()
            for (job in pending) stateLocked(job.key).value = UpscaleState.Skipped(if (noGpu) "no GPU" else "engine failed")
            pending.clear()
        }
        signal()
    }

    private fun finish(job: Job, outcome: UpscaleState?) {
        synchronized(lock) { finishLocked(job, outcome) }
    }

    private fun finishLocked(job: Job, outcome: UpscaleState?) {
        running = null
        // The live request of the key: this job, a newer one (released and requested again while it ran), or none.
        val current = live[job.key]
        if (current == null) {
            states[job.key]?.value = outcome ?: UpscaleState.Idle
            pruneStatesLocked()
            return
        }
        val state = stateLocked(job.key)
        if (outcome != null) {
            state.value = outcome
            return
        }
        if (noGpu || engineFailed) {
            state.value = UpscaleState.Skipped(if (noGpu) "no GPU" else "engine failed")
            return
        }
        if (!settings.value.enabled) {
            state.value = UpscaleState.Skipped("disabled")
            return
        }
        // Paused mid-job: back on top, it is still on screen.
        enqueueLocked(current)
    }

    private fun releaseEngine() {
        warm = false
        runCatching { engine.release() }
    }

    // --- plumbing ---

    /**
     * State flows outlive their tickets: the UI may remember a key's flow across a release and a new request of the same key, so a
     * flow is only forgotten when the map grows past [MAX_STATES], and never one of a live or running key.
     */
    private fun pruneStatesLocked() {
        if (states.size <= MAX_STATES) return
        val iterator = states.keys.iterator()
        while (iterator.hasNext() && states.size > MAX_STATES / 2) {
            val key = iterator.next()
            if (key !in live && running?.key != key) iterator.remove()
        }
    }

    private fun stateLocked(key: String): MutableStateFlow<UpscaleState> = states.getOrPut(key) { MutableStateFlow(UpscaleState.Idle) }

    private fun signal() {
        wake.update { it + 1 }
    }

    private companion object {
        const val MAX_STATES = 1024
    }

    private fun publishStatus() {
        statusFlow.value = synchronized(lock) {
            UpscaleStatus(
                gpuName = gpuName,
                queued = pending.size,
                running = running != null,
                pausedBy = pauseReasonLocked(),
                cacheBytes = cache.bytes.value,
                lastMillis = lastMillis,
            )
        }
    }
}
