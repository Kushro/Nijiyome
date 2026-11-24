// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.external

import app.nijiyome.network.outbound
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

// Hito 4, paso D: mpv-anime-build (https://github.com/Chinna95P/mpv-anime-build, MIT), the "modo mejorado" of the external mpv.
// Ported from Tatakai Next (tk-app/src/anime_build.rs, tk-player/src/enhanced_conf.rs). Tatakai shipped the 53.6 MB release zip in its
// repository; Nijiyome downloads that same release once, on request, into the app's cache, and accepts it only with the pinned
// SHA-256 below. It is then extracted into `<data>/mpv-anime-build/<VERSION>/` (marker file written last, holding the digest and the
// sanitizer revision). Nothing in the release is executed by the app: mpv loads its Lua scripts itself.

object AnimeBuild {
    const val VERSION = "5.6"
    const val ZIP_NAME = "mpv-anime-build-v5.6.zip"
    const val URL = "https://github.com/Chinna95P/mpv-anime-build/releases/download/v5.6/mpv-anime-build-v5.6.zip"

    /** SHA-256 of [ZIP_NAME] (Tatakai Next's `vendor/mpv-anime-build/SHA256SUMS`). */
    const val SHA256 = "291fca874d8a31b56b98fa62ea331ba33889de94dbf1129c081881b907936377"

    /** Size of the release zip in bytes (shown before downloading). */
    const val ZIP_BYTES = 56_236_802L

    /** The message of a download stopped by its caller. */
    const val CANCELLED = "mpv-anime-build download cancelled"

    /** Bump when [sanitizeMpvConf] or the extraction layout changes: an older extraction is redone. */
    const val SANITIZE_REV = 1
    const val MARKER = ".nijiyome-extracted"

    /** The build's original `mpv.conf`, kept next to the sanitized one. */
    const val UPSTREAM_CONF = "mpv.conf.upstream"

    /** Prefix of a line commented out by [sanitizeMpvConf]. */
    const val DISABLED_PREFIX = "# nijiyome (external): "

    /** Where the zip is downloaded to. */
    fun zipFile(cacheDir: File): File = File(File(cacheDir, "mpv-anime-build"), ZIP_NAME)

    /** Where it is extracted to (mpv's config directory in the enhanced mode). */
    fun configDir(dataDir: File): File = File(File(dataDir, "mpv-anime-build"), VERSION)

    /** What Settings shows. */
    enum class State { NOT_DOWNLOADED, DOWNLOADED, READY }

    /** Cheap (no hashing): READY once extracted by this revision, DOWNLOADED when only the zip is there. */
    fun state(dataDir: File, cacheDir: File): State = when {
        isCurrent(configDir(dataDir)) -> State.READY
        zipFile(cacheDir).isFile -> State.DOWNLOADED
        else -> State.NOT_DOWNLOADED
    }

    internal fun markerBody(digest: String = SHA256): String = "$digest\nrev=$SANITIZE_REV\n"

    internal fun isCurrent(dir: File, digest: String = SHA256): Boolean =
        runCatching { File(dir, MARKER).readText() == markerBody(digest) }.getOrDefault(false)

    /** One download at a time in this process: two would write the same `.part` file. */
    private val downloadLock = Any()

    /**
     * Downloads the release into [cacheDir] unless a copy with the pinned hash is there (blocking; [progress] gets bytes so far and
     * the total). A file with another hash is deleted and the call fails: nothing unverified is ever kept. One download at a time (a
     * second caller waits and then finds the verified zip); [isCancelled] stops it between reads and [onCall] gets the HTTP call so
     * the caller can cancel a blocked read. A cancelled download leaves no `.part` behind.
     */
    fun download(
        cacheDir: File,
        client: OkHttpClient = defaultClient,
        url: String = URL,
        sha256: String = SHA256,
        isCancelled: () -> Boolean = { false },
        onCall: (okhttp3.Call) -> Unit = {},
        progress: (Long, Long) -> Unit = { _, _ -> },
    ): File = synchronized(downloadLock) { downloadLocked(cacheDir, client, url, sha256, isCancelled, onCall, progress) }

    private fun downloadLocked(
        cacheDir: File,
        client: OkHttpClient,
        url: String,
        sha256: String,
        isCancelled: () -> Boolean,
        onCall: (okhttp3.Call) -> Unit,
        progress: (Long, Long) -> Unit,
    ): File {
        val zip = zipFile(cacheDir)
        if (zip.isFile && sha256Hex(zip) == sha256) return zip
        zip.parentFile?.mkdirs()
        val part = File(zip.parentFile, "${zip.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "Nijiyome").build())
        onCall(call)
        try {
            if (isCancelled()) throw IOException(CANCELLED)
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("mpv-anime-build download failed: HTTP ${resp.code}")
                val total = resp.body.contentLength().takeIf { it > 0 } ?: ZIP_BYTES
                part.outputStream().use { out ->
                    resp.body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            if (isCancelled()) throw IOException(CANCELLED)
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            progress(done, total)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            part.delete()
            throw if (isCancelled() || call.isCanceled()) IOException(CANCELLED, e) else e
        }
        val got = digest.digest().toHex()
        if (got != sha256) {
            part.delete()
            throw IOException("mpv-anime-build download does not match its pinned SHA-256 (got $got)")
        }
        if (!part.renameTo(zip)) {
            part.copyTo(zip, overwrite = true)
            part.delete()
        }
        return zip
    }

    /**
     * Verifies [zip] against [sha256] and extracts it into [dir] unless [dir] already holds that release (blocking). Afterwards
     * `mpv.conf` is the sanitized one ([sanitizeMpvConf]; the original stays as [UPSTREAM_CONF]) and Nijiyome's starting profile
     * choices are seeded where the build has none ([seedProfileDefaults]).
     */
    @Synchronized
    fun prepare(zip: File, dir: File, sha256: String = SHA256): File {
        if (isCurrent(dir, sha256)) {
            seedProfileDefaults(dir)
            return dir
        }
        val got = sha256Hex(zip)
        if (got != sha256) throw IOException("${zip.name} does not match its pinned SHA-256 (got $got)")
        dir.mkdirs()
        File(dir, MARKER).delete()
        extract(zip, dir)
        sanitizeConf(dir)
        seedProfileDefaults(dir)
        val tmp = File(dir, "$MARKER.tmp")
        tmp.writeText(markerBody(sha256))
        if (!tmp.renameTo(File(dir, MARKER))) throw IOException("cannot write the mpv-anime-build marker")
        return dir
    }

    /** The entry's path relative to the target, or null if it is absolute, climbs out (`..`), uses backslashes or is empty. */
    internal fun safeRelative(name: String): String? {
        if (name.contains('\\') || name.startsWith("/")) return null
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || parts[0].contains(':')) return null
        return parts.joinToString("/")
    }

    /** Extracts every file and directory under [dir]; rejects the whole archive on any unsafe path. Returns the file count. */
    internal fun extract(zip: File, dir: File): Int {
        var files = 0
        ZipFile(zip).use { z ->
            val entries = z.entries().toList()
            val rels = entries.map { e -> safeRelative(e.name) ?: throw IOException("mpv-anime-build archive: unsafe path ${e.name}") }
            for ((e, rel) in entries.zip(rels)) {
                val out = File(dir, rel)
                if (e.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                z.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
                // uosc's ziggy helpers are the only executables of the release (`scripts/uosc/bin/ziggy-*`); java.util.zip does not
                // expose the archive's mode bits, so they are recognised by place. Nothing else is ever made executable.
                if (isHelperBinary(rel)) out.setExecutable(true, true)
                files++
            }
        }
        return files
    }

    internal fun isHelperBinary(rel: String): Boolean =
        rel.startsWith("scripts/uosc/bin/") && !rel.substringAfterLast('/').contains('.')

    private fun sanitizeConf(dir: File) {
        val conf = File(dir, "mpv.conf")
        if (!conf.isFile) return
        val upstream = conf.readText()
        File(dir, UPSTREAM_CONF).writeText(upstream)
        conf.writeText(sanitizeMpvConf(upstream))
    }

    /**
     * Options (without `--` / `no-`) that the app owns: the build's `mpv.conf` is written for a standalone mpv, and its
     * `profile-cond` profiles apply asynchronously after start-up, so overriding them on the command line is not enough. Tatakai's
     * list: video output and GPU API / context (frame generation forces Vulkan), window geometry and fullscreen, IPC servers,
     * watch-later resume (it would beat the app's `--start`).
     */
    private val BLOCKED_OPTIONS = setOf(
        "vo", "gpu-api", "gpu-context", "hwdec", "wid", "input-ipc-server", "input-default-bindings", "input-vo-keyboard", "osc",
        "fullscreen", "fs", "border", "geometry", "autofit", "autofit-larger", "autofit-smaller", "force-window", "ontop", "idle",
        "keep-open", "save-position-on-quit", "resume-playback",
    )

    /** Windows-only D3D11 tuning. */
    private val BLOCKED_PREFIXES = listOf("d3d11-")

    /** The option a config line sets, without `--` and `no-`; null for blanks, comments and `[profile]` headers. */
    internal fun optionName(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("[")) return null
        val name = t.removePrefix("--").split('=', ' ', '\t', '#').first().trim()
        if (name.isEmpty()) return null
        return name.removePrefix("no-")
    }

    private fun blocked(name: String) = name in BLOCKED_OPTIONS || BLOCKED_PREFIXES.any { name.startsWith(it) }

    /** Comments out the options the app owns; everything else (profiles, shaders, subtitles, audio) is kept as it is. */
    fun sanitizeMpvConf(src: String): String = buildString {
        for (line in src.lines().let { if (src.endsWith("\n")) it.dropLast(1) else it }) {
            if (optionName(line)?.let(::blocked) == true) append(DISABLED_PREFIX)
            append(line).append('\n')
        }
    }

    /** Scripts never loaded (file or directory names under `scripts/`). */
    private val EXCLUDED_SCRIPTS = setOf(
        "mpvSockets.lua", // IPC endpoints per instance
        "update_check.lua", // network version check, opens the browser
        "youtube-download.lua", // yt-dlp downloads
        "power_manager.lua", // PowerShell power plan checks
        "auto-audio-device.lua", // switches the system audio device (PowerShell)
        "autosave.lua", // watch-later saves: the app records progress
        "auto-save-state.lua",
        "autoload.lua", // directory playlists: meaningless for streams
        "pip.lua", // resizes / pins the mpv window
        "mpv-scripts.disable",
    )

    /** Scripts that only make sense with uosc's own UI. */
    private val UOSC_ONLY = setOf("uosc", "thumbfast.lua")

    internal fun scriptAllowed(name: String, uosc: Boolean): Boolean = name !in EXCLUDED_SCRIPTS && (uosc || name !in UOSC_ONLY)

    /** mpv's own auto-load rule: `*.lua` / `*.js` files and directories holding `main.lua` / `main.js`. */
    private fun isScript(f: File): Boolean =
        if (f.isDirectory) File(f, "main.lua").isFile || File(f, "main.js").isFile else f.extension == "lua" || f.extension == "js"

    /** The scripts of `<configDir>/scripts` to load, sorted by name like mpv's auto-load. */
    fun selectScripts(configDir: File, uosc: Boolean): List<File> =
        File(configDir, "scripts").listFiles().orEmpty().filter { scriptAllowed(it.name, uosc) && isScript(it) }.sortedBy { it.name }

    /** The [ExternalMpvConfig] of an extracted build. */
    fun config(configDir: File, uosc: Boolean): ExternalMpvConfig.AnimeBuild =
        ExternalMpvConfig.AnimeBuild(configDir, uosc, selectScripts(configDir, uosc))

    /**
     * Tatakai's starting point for the build's `anime_profile_controller.lua` while the build has not saved its own choices: anime
     * detection on, Anime4K A+A at HQ up to 4K (8K keeps the build's choice). Existing files are the user's and are never touched.
     */
    internal const val ANIME_MODE_DEFAULTS = "anime_mode=on\nfidelity_SD=false\nfidelity_HD=false\nfidelity_FHD=false\nfidelity_2K=false\nfidelity_4K=false\n"
    internal const val ANIME4K_DEFAULTS =
        "quality_SD=hq\nmode_SD=AA\nquality_HD=hq\nmode_HD=AA\nquality_FHD=hq\nmode_FHD=AA\nquality_2K=hq\nmode_2K=AA\nquality_4K=hq\nmode_4K=AA\n"

    internal fun seedProfileDefaults(dir: File) {
        val opts = File(dir, "script-opts")
        for ((name, body) in listOf("anime-mode.conf" to ANIME_MODE_DEFAULTS, "anime4k.conf" to ANIME4K_DEFAULTS)) {
            val f = File(opts, name)
            if (f.exists()) continue
            runCatching {
                opts.mkdirs()
                if (f.createNewFile()) f.writeText(body)
            }
        }
    }

    internal fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder().outbound().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }
}
