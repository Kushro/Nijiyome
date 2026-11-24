// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.nijiyome.model.Container
import app.nijiyome.model.QualityPresets
import app.nijiyome.model.QualityTier
import app.nijiyome.player.PlayableSource
import app.nijiyome.player.PlaybackListener
import app.nijiyome.player.PlayerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Manual smoke run of [MpvVideoSurface] (not a JUnit test): a Compose window with the video of a [DesktopMpvSessionFactory] session
 * and a Compose bar drawn on top. Run it in a headless compositor, never on a user's desktop, and without audio (it always passes
 * `ao=null`), for example `gamescope --backend headless -w 1920 -h 1080 -- java -cp <desktopTest runtime classpath + the Skiko
 * runtime of the OS> app.nijiyome.player.mpv.MpvSurfaceSmokeKt <clip> <out dir> <scenario>`. Everything it measures goes to stdout
 * as `[smoke] ...` lines; it quits by itself.
 *
 * Scenarios: `reload` (one session loads the file N times, as between episodes), `shaders` (plays without shaders, then with Anime4K A+A: frame drops, Compose draws per second, the same paused frame
 * with and without the chain, captures with the bar on top, the engine's own frame capture) and `cycle` (opens and closes the player
 * N times and logs the process RSS).
 */
fun main(args: Array<String>) {
    val clip = File(args.getOrElse(0) { error("clip path") })
    val out = File(args.getOrElse(1) { error("out dir") }).apply { mkdirs() }
    val scenario = args.getOrElse(2) { "shaders" }
    val maxSeconds = System.getenv("SMOKE_MAX_SECONDS")?.toLongOrNull() ?: 120
    thread(isDaemon = true, name = "smoke-watchdog") {
        Thread.sleep(maxSeconds * 1000)
        log("watchdog: $maxSeconds s, halt")
        Runtime.getRuntime().halt(3)
    }
    val factory = DesktopMpvSessionFactory(
        cacheDir = File(out, "cache"),
        mainDispatcher = Dispatchers.Main,
        extraOptions = buildMap {
            put("ao", "null")
            System.getenv("SMOKE_MPV_LOG")?.let { put("log-file", it) }
        },
    )
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val draws = AtomicLong()
    var layer: SkiaLayer? = null
    log("start clip=$clip scenario=$scenario rssKb=${rssKb()}")
    application {
        val holder = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PlayerSession?>(null) }
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(position = WindowPosition(0.dp, 0.dp), size = DpSize(1920.dp, 1080.dp)),
            title = "Nijiyome mpv smoke",
            undecorated = true,
            focusable = false,
        ) {
            LaunchedEffect(Unit) {
                val l = findSkiaLayer(window) ?: error("no SkiaLayer")
                layer = l
                val orig = l.renderDelegate ?: error("no render delegate")
                l.renderDelegate = SkikoRenderDelegate { canvas, w, h, nanos ->
                    orig.onRender(canvas, w, h, nanos)
                    draws.incrementAndGet()
                }
                log("renderInfo ${l.renderInfo.replace("\n", " | ")}")
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val session = holder.value
                if (session != null) {
                    MpvVideoSurface(session, Modifier.fillMaxSize())
                    val st by session.state.collectAsState()
                    val q by session.quality.collectAsState()
                    BasicText(
                        "%s / %s  ·  preset %s  ·  perdidos %d  ·  Compose encima de mpv".format(
                            Locale.ROOT, fmt(st.positionMs), fmt(st.durationMs), q.effective.preset, q.droppedFrames,
                        ),
                        style = TextStyle(color = Color.White, fontSize = 22.sp),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(20.dp),
                    )
                }
            }
            LaunchedEffect(Unit) {
                runCatching {
                    when (scenario) {
                        "cycle" -> cycle(factory, scope, clip, holder)
                        "reload" -> reload(factory, scope, clip, holder)
                        "play" -> play(factory, scope, clip, holder, out, draws) { layer }
                        "desktopshaders" -> desktopShaders(factory, scope, clip, holder, out, draws) { layer }
                        else -> shaders(factory, scope, clip, holder, out, draws) { layer }
                    }
                }.onFailure { log("FAILED $it"); it.printStackTrace() }
                log("done rssKb=${rssKb()}")
                holder.value?.release()
                holder.value = null
                delay(800)
                Runtime.getRuntime().halt(0)
            }
        }
    }
}

private val listener = object : PlaybackListener {}

private suspend fun open(
    factory: DesktopMpvSessionFactory,
    scope: CoroutineScope,
    clip: File,
    holder: androidx.compose.runtime.MutableState<PlayerSession?>,
): PlayerSession {
    val s = factory.create(scope, listener, remote = false)
    holder.value = s
    s.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)
    val ok = kotlinx.coroutines.withTimeoutOrNull(20_000) { s.state.first { it.started && it.isPlaying } }
    if (ok == null) error("no start in 20 s: ${s.state.value}")
    return s
}

private suspend fun shaders(
    factory: DesktopMpvSessionFactory,
    scope: CoroutineScope,
    clip: File,
    holder: androidx.compose.runtime.MutableState<PlayerSession?>,
    out: File,
    draws: AtomicLong,
    layer: () -> SkiaLayer?,
) {
    val s = open(factory, scope, clip, holder)
    val desktop = s as DesktopMpvPlayerSession
    delay(2_000) // vo restored once the render context exists; let it settle
    measure("sin shaders", desktop, draws, 10_000)
    capture(layer(), File(out, "smoke-sin-shaders.png"))

    // Same paused frame without and with the chain.
    s.pause()
    s.seekTo(12_000)
    delay(1_500)
    val plain = capture(layer(), File(out, "smoke-pausa-sin-shaders.png"))
    delay(500)
    val plainAgain = capture(layer(), File(out, "smoke-pausa-sin-shaders-2.png"))
    if (plain != null && plainAgain != null) log("diff control (dos capturas sin shaders): ${diff(plain, plainAgain)}")
    s.setQuality(QualityPresets.ANIME4K_AA, QualityTier.BALANCED)
    delay(2_500)
    log("quality ${s.quality.value.effective} shaders=${s.quality.value.activeShaders} reasons=${s.quality.value.reasons}")
    val withChain = capture(layer(), File(out, "smoke-pausa-con-anime4k.png"))
    if (plain != null && withChain != null) log("diff pausa sin/con Anime4K: ${diff(plain, withChain)}")
    s.setQuality(QualityPresets.OFF, QualityTier.BALANCED)
    delay(1_500)
    val back = capture(layer(), File(out, "smoke-pausa-shaders-quitados.png"))
    if (plain != null && back != null) log("diff sin shaders / cadena quitada: ${diff(plain, back)}")
    s.setQuality(QualityPresets.ANIME4K_AA, QualityTier.BALANCED)
    delay(1_000)
    val frame = File(out, "smoke-captura-motor.png")
    log("screenshotToFile=${s.screenshotToFile(frame.absolutePath)} bytes=${frame.length()}")

    s.play()
    delay(2_000)
    measure("con Anime4K A+A", desktop, draws, 10_000)
    capture(layer(), File(out, "smoke-con-anime4k.png"))
}

/** v1.1 (F2): plays [clip] for SMOKE_MEASURE_S seconds (default 60) and logs drops, CPU and the decoder (libva source, VAAPI). */
private suspend fun play(
    factory: DesktopMpvSessionFactory,
    scope: CoroutineScope,
    clip: File,
    holder: androidx.compose.runtime.MutableState<PlayerSession?>,
    out: File,
    draws: AtomicLong,
    layer: () -> SkiaLayer?,
) {
    val s = open(factory, scope, clip, holder) as DesktopMpvPlayerSession
    log("libva ${DesktopVideoDecoding.libva.value} vaapi=${DesktopVideoDecoding.vaapi} caps=${DesktopVideoDecoding.codecCaps()}")
    delay(2_000)
    val secs = System.getenv("SMOKE_MEASURE_S")?.toLongOrNull() ?: 60
    measure("play ${clip.name}", s, draws, secs * 1000)
    log("lastDecoder=${DesktopVideoDecoding.lastDecoder.value} osd=${s.engine.getProperty("osd-width")}x${s.engine.getProperty("osd-height")} surface=${s.engine.state.value.surfaceWidth}x${s.engine.state.value.surfaceHeight}")
    capture(layer(), File(out, "smoke-play.png"))
}

/** v1.1 (F2): NVScaler and the HQ tier with the VL networks: the same paused frame with and without each, then 10 s playing each. */
private suspend fun desktopShaders(
    factory: DesktopMpvSessionFactory,
    scope: CoroutineScope,
    clip: File,
    holder: androidx.compose.runtime.MutableState<PlayerSession?>,
    out: File,
    draws: AtomicLong,
    layer: () -> SkiaLayer?,
) {
    val s = open(factory, scope, clip, holder) as DesktopMpvPlayerSession
    delay(2_000)
    log("presets offered: ${s.quality.value.presets.map { "${it.id}${if (it.available) "" else "(off)"}" }}")
    s.pause()
    s.seekTo(12_000)
    delay(1_500)
    val plain = capture(layer(), File(out, "smoke-pausa-sin-shaders.png"))
    for ((preset, tier) in listOf(QualityPresets.NVSCALER to QualityTier.BALANCED, QualityPresets.ANIME4K_A to QualityTier.HQ)) {
        s.setQuality(preset, tier)
        delay(3_000)
        val q = s.quality.value
        log("quality $preset/$tier effective=${q.effective} shaders=${q.activeShaders.map { it.substringAfterLast('/') }} reasons=${q.reasons}")
        val shot = capture(layer(), File(out, "smoke-pausa-$preset-${tier.name.lowercase()}.png"))
        if (plain != null && shot != null) log("diff pausa sin/con $preset ${tier.name}: ${diff(plain, shot)}")
    }
    for ((preset, tier) in listOf(QualityPresets.OFF to QualityTier.BALANCED, QualityPresets.NVSCALER to QualityTier.BALANCED, QualityPresets.ANIME4K_A to QualityTier.HQ)) {
        s.setQuality(preset, tier)
        s.play()
        delay(System.getenv("SMOKE_WARM_MS")?.toLongOrNull() ?: 2_000)
        measure("$preset ${tier.name}", s, draws, System.getenv("SMOKE_MEASURE_MS")?.toLongOrNull() ?: 10_000)
        s.pause()
    }
}

private suspend fun cycle(
    factory: DesktopMpvSessionFactory,
    scope: CoroutineScope,
    clip: File,
    holder: androidx.compose.runtime.MutableState<PlayerSession?>,
) {
    val n = System.getenv("SMOKE_CYCLES")?.toIntOrNull() ?: 10
    log("cycle n=$n")
    for (i in 1..n) {
        val s = open(factory, scope, clip, holder)
        delay(1_500)
        holder.value = null
        delay(300) // the surface leaves composition first, as when the player screen closes
        s.release()
        delay(700)
        System.gc()
        delay(300)
        if (System.getenv("SMOKE_TRIM") != null) log("cycle $i before trim rssKb=${rssKb()} trim=${mallocTrim()}")
        log("cycle $i rssKb=${rssKb()} trims=${NativeHeap.trims}")
        if (i == 1) nmt("baseline")
    }
    nmt("summary.diff")?.let { d -> d.lines().filter { it.contains("committed=") && it.contains("+") }.forEach { log("nmt $it") } }
}

/** One session, the same file loaded N times (next episode): RSS after each load. */
private suspend fun reload(
    factory: DesktopMpvSessionFactory,
    scope: CoroutineScope,
    clip: File,
    holder: androidx.compose.runtime.MutableState<PlayerSession?>,
) {
    val n = System.getenv("SMOKE_CYCLES")?.toIntOrNull() ?: 10
    val s = open(factory, scope, clip, holder)
    for (i in 1..n) {
        delay(1_500)
        s.load(PlayableSource(clip.absolutePath, emptyMap(), Container.PROGRESSIVE, "clip", localFile = true), startMs = 0)
        val ok = kotlinx.coroutines.withTimeoutOrNull(20_000) { s.state.first { it.loadId == i + 1 && it.started && it.isPlaying } }
        if (ok == null) error("reload $i did not start: ${s.state.value}")
        delay(700)
        System.gc()
        delay(300)
        if (System.getenv("SMOKE_TRIM") != null) log("reload $i before trim rssKb=${rssKb()} trim=${mallocTrim()}")
        log("reload $i rssKb=${rssKb()}")
        if (i == 1) nmt("baseline")
    }
    nmt("summary.diff")?.let { d -> d.lines().filter { it.contains("committed=") && it.contains("+") }.forEach { log("nmt $it") } }
}

/** glibc `malloc_trim(0)` (returns freed heap to the system), so allocator retention is told apart from a leak. */
private fun mallocTrim(): Int = runCatching {
    com.sun.jna.NativeLibrary.getInstance("c").getFunction("malloc_trim").invokeInt(arrayOf(0))
}.getOrDefault(-1)

/** `jcmd VM.native_memory <cmd>` from inside (needs -XX:NativeMemoryTracking=summary); null without it. */
private fun nmt(cmd: String): String? = runCatching {
    val server = java.lang.management.ManagementFactory.getPlatformMBeanServer()
    server.invoke(
        javax.management.ObjectName("com.sun.management:type=DiagnosticCommand"), "vmNativeMemory",
        arrayOf<Any>(arrayOf(cmd)), arrayOf(Array<String>::class.java.name),
    ) as String
}.getOrNull()

private suspend fun measure(label: String, s: DesktopMpvPlayerSession, draws: AtomicLong, ms: Long) {
    val d0 = s.engine.droppedFrames() ?: 0
    val p0 = s.currentPositionMs()
    val c0 = draws.get()
    val t0 = System.nanoTime()
    val cpu0 = cpuTicks()
    delay(ms)
    val secs = (System.nanoTime() - t0) / 1e9
    val dropped = (s.engine.droppedFrames() ?: 0) - d0
    val played = (s.currentPositionMs() - p0) / 1000.0
    val fps = s.engine.videoFps() ?: 0.0
    val expected = played * fps
    val cpu = (cpuTicks() - cpu0) / 100.0 / secs * 100
    log(
        "measure [$label] played=%.2fs fps=%.2f dropped=%d of %.0f (%.2f%%) draws/s=%.1f cpu=%.1f%% decoder=%s renderer=%s".format(
            Locale.ROOT, played, fps, dropped, expected, if (expected > 0) dropped * 100 / expected else 0.0,
            (draws.get() - c0) / secs, cpu, s.quality.value.decoderInUse, s.quality.value.renderer,
        ),
    )
}

private suspend fun capture(layer: SkiaLayer?, file: File): Bitmap? {
    if (layer == null) return null
    val bmp = kotlinx.coroutines.withContext(Dispatchers.Main) { layer.screenshot() } ?: return null.also { log("capture failed") }
    val data = Image.makeFromBitmap(bmp).encodeToData(EncodedImageFormat.PNG) ?: return null
    file.writeBytes(data.bytes)
    log("capture ${bmp.width}x${bmp.height} lumaVideo=%.1f -> ${file.name}".format(Locale.ROOT, luma(bmp)))
    return bmp
}

/** Share of pixels of the upper 80 % (the video, not the bar) that differ by more than 8 levels, and the mean difference. */
private fun diff(a: Bitmap, b: Bitmap): String {
    val w = minOf(a.width, b.width)
    val h = minOf(a.height, b.height) * 8 / 10
    var changed = 0L
    var sum = 0.0
    var n = 0L
    for (y in 0 until h step 2) for (x in 0 until w step 2) {
        val ca = a.getColor(x, y)
        val cb = b.getColor(x, y)
        val d = maxOf(kotlin.math.abs((ca shr 16 and 0xFF) - (cb shr 16 and 0xFF)), kotlin.math.abs((ca shr 8 and 0xFF) - (cb shr 8 and 0xFF)), kotlin.math.abs((ca and 0xFF) - (cb and 0xFF)))
        if (d > 8) changed++
        sum += d
        n++
    }
    return "%.2f%% of pixels changed > 8, mean %.2f".format(Locale.ROOT, changed * 100.0 / n, sum / n)
}

private fun luma(bmp: Bitmap): Double {
    var sum = 0.0
    var n = 0
    for (y in 0 until bmp.height * 8 / 10 step 8) for (x in 0 until bmp.width step 8) {
        val c = bmp.getColor(x, y)
        sum += 0.2126 * (c shr 16 and 0xFF) + 0.7152 * (c shr 8 and 0xFF) + 0.0722 * (c and 0xFF)
        n++
    }
    return sum / n
}

private fun findSkiaLayer(c: java.awt.Component): SkiaLayer? {
    if (c is SkiaLayer) return c
    if (c is java.awt.Container) for (child in c.components) findSkiaLayer(child)?.let { return it }
    return null
}

private fun rssKb(): Long = runCatching {
    File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.split(Regex("\\s+"))[1].toLong()
}.getOrDefault(-1)

/** utime + stime of this process in clock ticks (Linux, 100 Hz). */
private fun cpuTicks(): Long = runCatching {
    val f = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
    f[11].toLong() + f[12].toLong()
}.getOrDefault(0)

private fun fmt(ms: Long): String = "%d:%02d".format(Locale.ROOT, ms / 60_000, ms / 1000 % 60)

private fun log(msg: String) {
    println("[smoke ${System.currentTimeMillis()}] $msg")
    System.out.flush()
}
