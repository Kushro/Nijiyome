// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import app.nijiyome.player.CodecCaps
import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// v1.1 (F2): hardware decoding of the embedded player on Linux, and what Settings and the source picker learn about it.
//
// MediaMP's Linux runtime bundles libva 1.20, built for Debian's driver folder, and it cannot load the VAAPI drivers of a current Mesa
// ("Failed to initialize VAAPI", docs/spikes/reproductor.md §4.3), so AMD / Intel decoded in software. libmpv / FFmpeg find libva by
// its soname (`libva.so.2`, RUNPATH $ORIGIN). The dynamic linker reuses a library already loaded under that soname, so loading the
// system's libva (and its -drm / -x11 front ends) into the process before MediaMP loads libmpv makes the whole runtime use it: the
// same effect as the spike's LD_PRELOAD, without touching the environment. The system copy is taken only when all three files exist
// and its VA-API version is at least the bundled one (libva keeps its ABI backward compatible within soname 2); otherwise the bundled
// copy stays, exactly as before. `NIJIYOME_SYSTEM_LIBVA=0` turns it off.

/** What the desktop engine knows about video decoding (Settings › Reproductor, the source picker's codec checks). */
object DesktopVideoDecoding {
    /** Which libva the embedded player loaded. */
    enum class LibvaSource {
        /** The system's libva (VAAPI drivers of the system work). */
        SYSTEM,

        /** MediaMP's own libva 1.20 (VAAPI drivers of a current Mesa do not load: software or NVDEC only). */
        BUNDLED,

        /** No libva on this platform (Windows: D3D11VA), or the runtime was not prepared yet. */
        NONE,
    }

    data class Libva(
        val source: LibvaSource,
        /** VA-API version of the loaded copy (`1.24`), when known. */
        val version: String? = null,
        /** Why the system copy was not used, in detail (log line). */
        val reason: String? = null,
        val why: Why? = null,
    )

    /** Why the system libva was not used (Settings words it). */
    enum class Why { MISSING, TOO_OLD, DISABLED, LOAD_FAILED }

    /** What VAAPI decodes on this machine (probed once through the libva the player uses); null = not probed or no VAAPI. */
    data class Vaapi(
        val vendor: String?,
        val h264: Boolean,
        val hevc: Boolean,
        val hevcMain10: Boolean,
        val av1: Boolean,
        val vp9: Boolean,
    )

    private val _libva = MutableStateFlow(Libva(LibvaSource.NONE))
    val libva: StateFlow<Libva> = _libva.asStateFlow()

    private val _lastDecoder = MutableStateFlow<String?>(null)

    /** mpv `hwdec-current` of the last video the embedded player decoded (`vaapi-copy`, `nvdec`, `no` = software). */
    val lastDecoder: StateFlow<String?> = _lastDecoder.asStateFlow()

    @Volatile
    private var softwareCodecs: Set<String>? = null

    internal fun reportLibva(value: Libva) {
        _libva.value = value
    }

    internal fun reportDecoder(hwdecCurrent: String?) {
        val v = hwdecCurrent?.trim()?.takeIf { it.isNotEmpty() } ?: return
        _lastDecoder.value = v
    }

    /** FFmpeg decoders of the bundled libmpv (`decoder-list`, codec names), read once from the first engine. */
    internal fun reportSoftwareCodecs(codecs: Set<String>) {
        if (codecs.isNotEmpty()) softwareCodecs = codecs
    }

    /**
     * VAAPI decode support, probed once through the libva the player will use (blocking, tens of ms: [warmUp] runs it off the UI
     * thread at startup). Loading the system libva this early is what the runtime does anyway before libmpv.
     */
    val vaapi: Vaapi? by lazy {
        if (!isLinux()) return@lazy null
        SystemLibva.preload()
        VaapiProbe.probe(SystemLibva.loadedDir())
    }

    private val nvidia: Boolean by lazy { isLinux() && File("/proc/driver/nvidia/version").isFile }

    /** Probes VAAPI on a background thread, so the first [codecCaps] does not wait for the driver. */
    fun warmUp() {
        if (!isLinux()) return
        Thread({ runCatching { vaapi } }, "nijiyome-vaapi-probe").apply { isDaemon = true }.start()
    }

    /**
     * What this desktop decodes: software from the bundled FFmpeg (`decoder-list`; until an engine has run, the codecs that runtime is
     * built with), hardware from VAAPI (through the libva the player loaded) or NVDEC (NVIDIA's driver: HEVC 8 / 10 bits; AV1 only
     * when VAAPI says so). Windows: unknown, everything counts as compatible (as before).
     */
    fun codecCaps(): CodecCaps {
        if (!isLinux()) return CodecCaps.ALL
        return desktopCodecCaps(softwareCodecs, vaapi, nvidia)
    }
}

/** Pure part of [DesktopVideoDecoding.codecCaps] (unit tested). [software] null = not read yet (assume the bundled build's set). */
internal fun desktopCodecCaps(software: Set<String>?, vaapi: DesktopVideoDecoding.Vaapi?, nvidia: Boolean): CodecCaps {
    val sw = software ?: BUNDLED_SOFTWARE_CODECS
    val swHevc = "hevc" in sw
    val swAv1 = "av1" in sw || "libdav1d" in sw
    val hwHevc = vaapi?.hevc == true || nvidia
    val hwHevc10 = vaapi?.hevcMain10 == true || nvidia
    val hwAv1 = vaapi?.av1 == true
    return CodecCaps(
        hevc = swHevc || hwHevc,
        // FFmpeg's HEVC and dav1d decode 10 bits in software.
        hevcMain10 = swHevc || hwHevc10,
        av1 = swAv1 || hwAv1,
        av1Main10 = swAv1 || hwAv1,
        hevcHw = hwHevc,
        hevcMain10Hw = hwHevc10,
        av1Hw = hwAv1,
        // VAProfileAV1Profile0 covers 8 and 10 bits 4:2:0.
        av1Main10Hw = hwAv1,
    )
}

/** Decoders of MediaMP 0.5.3's FFmpeg 8.0.1 that matter to [desktopCodecCaps] (built with --enable-libdav1d). */
private val BUNDLED_SOFTWARE_CODECS = setOf("h264", "hevc", "av1", "libdav1d", "vp9")

/** The system's libva, preferred over the bundled one when compatible (see the file header). */
internal object SystemLibva {
    /** The three libraries libmpv / FFmpeg link (libva-x11 for mpv's X11 VAAPI, libva-drm for the copy decoder). */
    val LIBRARIES = listOf("libva.so.2", "libva-drm.so.2", "libva-x11.so.2")

    /** Library folders of the common x86_64 distributions (Debian / Ubuntu, Fedora / openSUSE, Arch). */
    val SEARCH_DIRS = listOf("/usr/lib/x86_64-linux-gnu", "/usr/lib64", "/usr/lib", "/lib/x86_64-linux-gnu", "/lib64")

    /** VA-API version of MediaMP 0.5.3's libva (read from the runtime when possible, see [bundledVersion]). */
    val BUNDLED_FALLBACK = VaVersion(1, 20)

    @Volatile
    private var loaded: File? = null

    /** The first real [preload]'s result (later calls return it). */
    @Volatile
    private var done: DesktopVideoDecoding.Libva? = null

    private val SYSTEM_LOAD: (String) -> Unit = System::load

    /** Whether the process-wide preload already ran (its result is final). */
    internal fun hasRun(): Boolean = done != null

    /** The folder of the system libva loaded into the process, or null (bundled, not prepared, or not Linux). */
    fun loadedDir(): File? = loaded

    data class VaVersion(val major: Int, val minor: Int) : Comparable<VaVersion> {
        override fun compareTo(other: VaVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor })
        override fun toString(): String = "$major.$minor"
    }

    sealed interface Choice {
        data class Use(val dir: File, val version: VaVersion) : Choice
        data class Skip(val reason: String, val why: DesktopVideoDecoding.Why = DesktopVideoDecoding.Why.MISSING) : Choice
    }

    /** The first folder of [dirs] with all of [LIBRARIES] whose libva is at least [minimum]; [Choice.Skip] says why none was. */
    fun choose(dirs: List<File>, minimum: VaVersion, version: (File) -> VaVersion? = ::versionOf): Choice {
        var older: String? = null
        for (dir in dirs) {
            if (LIBRARIES.any { !File(dir, it).exists() }) continue
            val v = version(File(dir, LIBRARIES[0]))
            if (v == null) {
                older = older ?: "unknown libva version in $dir"
                continue
            }
            if (v < minimum) {
                older = older ?: "system libva $v is older than the bundled $minimum"
                continue
            }
            return Choice.Use(dir, v)
        }
        return older?.let { Choice.Skip(it, DesktopVideoDecoding.Why.TOO_OLD) } ?: Choice.Skip("no system libva")
    }

    /**
     * VA-API version of a libva file: from its real name (`libva.so.2.2400.0` = libva 2.24 = VA-API 1.24), else from the version string
     * the library carries (`1.24.0`, printed as "VA-API version %s").
     */
    fun versionOf(file: File): VaVersion? {
        val real = runCatching { file.toPath().toRealPath().fileName.toString() }.getOrNull() ?: file.name
        Regex("""^libva\.so\.2\.(\d{2})(\d{2})\.\d+$""").find(real)?.let { m ->
            return VaVersion(1, m.groupValues[1].toInt())
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return versionIn(bytes)
    }

    /** The `1.NN.N` string (NUL-terminated) of libva's "VA-API version %s"; null when absent. */
    fun versionIn(bytes: ByteArray): VaVersion? {
        val text = String(bytes, Charsets.ISO_8859_1)
        val m = Regex("""\u0000(1)\.(\d{1,2})\.(\d{1,3})\u0000""").find(text) ?: return null
        return VaVersion(m.groupValues[1].toInt(), m.groupValues[2].toInt())
    }

    /** The bundled libva's version, read from MediaMP's runtime on the class path (falls back to [BUNDLED_FALLBACK]). */
    fun bundledVersion(loader: ClassLoader = SystemLibva::class.java.classLoader ?: ClassLoader.getSystemClassLoader()): VaVersion =
        runCatching { loader.getResourceAsStream(LIBRARIES[0])?.use { versionIn(it.readBytes()) } }.getOrNull() ?: BUNDLED_FALLBACK

    /**
     * Loads the system libva into the process when [choose] finds a compatible one (Linux only, once, before MediaMP loads libmpv) and
     * reports the result to [DesktopVideoDecoding]. A failure keeps the bundled copy.
     */
    @Synchronized
    fun preload(
        enabled: Boolean = System.getenv("NIJIYOME_SYSTEM_LIBVA") != "0",
        dirs: List<File> = SEARCH_DIRS.map(::File),
        load: (String) -> Unit = SYSTEM_LOAD,
    ): DesktopVideoDecoding.Libva {
        if (!isLinux()) return DesktopVideoDecoding.Libva(DesktopVideoDecoding.LibvaSource.NONE).also(DesktopVideoDecoding::reportLibva)
        done?.let { return it }
        val bundled = bundledVersion()
        val result = if (!enabled) {
            DesktopVideoDecoding.Libva(
                DesktopVideoDecoding.LibvaSource.BUNDLED, bundled.toString(), "NIJIYOME_SYSTEM_LIBVA=0", DesktopVideoDecoding.Why.DISABLED,
            )
        } else {
            when (val c = choose(dirs, bundled)) {
                is Choice.Skip -> DesktopVideoDecoding.Libva(DesktopVideoDecoding.LibvaSource.BUNDLED, bundled.toString(), c.reason, c.why)
                is Choice.Use -> {
                    // libva first: its two front ends link it by soname, which then resolves to this copy.
                    val failure = LIBRARIES.firstNotNullOfOrNull { name ->
                        runCatching { load(File(c.dir, name).absolutePath) }.exceptionOrNull()?.let { "$name: ${it.message}" }
                    }
                    if (failure == null) {
                        loaded = c.dir
                        DesktopVideoDecoding.Libva(DesktopVideoDecoding.LibvaSource.SYSTEM, c.version.toString())
                    } else {
                        DesktopVideoDecoding.Libva(
                            DesktopVideoDecoding.LibvaSource.BUNDLED, bundled.toString(), failure, DesktopVideoDecoding.Why.LOAD_FAILED,
                        )
                    }
                }
            }
        }
        mpvLogInfoLine("libva: ${result.source} ${result.version.orEmpty()} ${result.reason.orEmpty()}".trim())
        DesktopVideoDecoding.reportLibva(result)
        // A test's fake folder (a load function of its own) never pins the process-wide result.
        if (load === SYSTEM_LOAD) done = result
        return result
    }
}

/**
 * VAAPI decode profiles through the system libva the player loaded (vaGetDisplayDRM on the first render node, vaInitialize,
 * vaQueryConfigProfiles / vaQueryConfigEntrypoints). Null when there is no system libva in the process, no render node or no driver.
 */
internal object VaapiProbe {
    // va.h: VAProfile and VAEntrypoint values (stable ABI).
    private const val H264_MAIN = 6
    private const val H264_HIGH = 7
    private const val HEVC_MAIN = 17
    private const val HEVC_MAIN10 = 18
    private const val VP9_PROFILE0 = 19
    private const val AV1_PROFILE0 = 32
    private const val ENTRYPOINT_VLD = 1
    private const val O_RDWR = 2

    fun probe(libvaDir: File?, nodes: List<File> = renderNodes()): DesktopVideoDecoding.Vaapi? {
        val dir = libvaDir ?: return null
        val node = nodes.firstOrNull() ?: return null
        return runCatching {
            val va = NativeLibrary.getInstance(File(dir, "libva.so.2").absolutePath)
            val vaDrm = NativeLibrary.getInstance(File(dir, "libva-drm.so.2").absolutePath)
            val libc = NativeLibrary.getInstance("c")
            val fd = libc.getFunction("open").invokeInt(arrayOf(node.absolutePath, O_RDWR))
            if (fd < 0) return null
            try {
                val dpy = vaDrm.getFunction("vaGetDisplayDRM").invokePointer(arrayOf(fd)) ?: return null
                val major = IntByReference()
                val minor = IntByReference()
                if (va.fn("vaInitialize").invokeInt(arrayOf(dpy, major, minor)) != 0) return null
                try {
                    val profiles = decodeProfiles(va, dpy)
                    DesktopVideoDecoding.Vaapi(
                        vendor = va.fn("vaQueryVendorString").invokeString(arrayOf(dpy), false),
                        h264 = H264_HIGH in profiles || H264_MAIN in profiles,
                        hevc = HEVC_MAIN in profiles,
                        hevcMain10 = HEVC_MAIN10 in profiles,
                        av1 = AV1_PROFILE0 in profiles,
                        vp9 = VP9_PROFILE0 in profiles,
                    )
                } finally {
                    va.fn("vaTerminate").invokeInt(arrayOf(dpy))
                }
            } finally {
                libc.getFunction("close").invokeInt(arrayOf(fd))
            }
        }.onFailure { mpvLogInfoLine("vaapi probe failed: $it") }.getOrNull()
    }

    /** Profiles with a decode (VLD) entry point. */
    private fun decodeProfiles(va: NativeLibrary, dpy: Pointer): Set<Int> {
        val max = va.fn("vaMaxNumProfiles").invokeInt(arrayOf(dpy)).coerceIn(0, 256)
        val list = IntArray(max)
        val n = IntByReference()
        if (va.fn("vaQueryConfigProfiles").invokeInt(arrayOf(dpy, list, n)) != 0) return emptySet()
        val maxEp = va.fn("vaMaxNumEntrypoints").invokeInt(arrayOf(dpy)).coerceIn(0, 64)
        val out = HashSet<Int>()
        for (profile in list.take(n.value.coerceIn(0, max))) {
            val eps = IntArray(maxEp)
            val nEp = IntByReference()
            if (va.fn("vaQueryConfigEntrypoints").invokeInt(arrayOf(dpy, profile, eps, nEp)) != 0) continue
            if (eps.take(nEp.value.coerceIn(0, maxEp)).contains(ENTRYPOINT_VLD)) out += profile
        }
        return out
    }

    private fun NativeLibrary.fn(name: String): Function = getFunction(name)

    /** `/dev/dri/renderD*`, sorted (renderD128 first). */
    fun renderNodes(dir: File = File("/dev/dri")): List<File> =
        dir.listFiles { f -> f.name.startsWith("renderD") }?.sortedBy { it.name }.orEmpty()
}

internal fun isLinux(): Boolean = System.getProperty("os.name").orEmpty().lowercase(java.util.Locale.ROOT).contains("linux")

private fun mpvLogInfoLine(message: String) = app.nijiyome.player.mpv.quality.mpvLogInfo(message)
