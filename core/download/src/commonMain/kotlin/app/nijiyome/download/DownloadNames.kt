// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.download

import java.io.File
import java.util.Locale

/**
 * File names and layout of the desktop downloads (port of Tatakai Next's `tk-core/download/files.rs`, docs/origen):
 * `<folder>/<Title>/<Title> - 01.ts|mp4`. While running, a direct file is `<target>.part` and an HLS download keeps its segments in
 * `<base>.parts/NNNNN.seg` (the resume checkpoint: a segment file exists only once complete). Pure.
 */
object DownloadNames {
    /** Characters Windows / macOS / Linux refuse (or that confuse shells) in a file name. */
    private val FORBIDDEN = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
    private const val MAX_NAME = 120

    /**
     * A safe single path component: forbidden and control characters become `_`, runs of spaces collapse, leading / trailing dots and
     * spaces go, reserved Windows device names get a `_` suffix; empty -> `untitled`.
     */
    fun sanitize(name: String): String {
        val out = StringBuilder(name.length)
        var lastSpace = false
        for (raw in name) {
            val c = if (raw in FORBIDDEN || raw.isISOControl()) '_' else raw
            if (c.isWhitespace()) {
                if (!lastSpace) out.append(' ')
                lastSpace = true
            } else {
                out.append(c)
                lastSpace = false
            }
        }
        var s = out.toString().trim { it == '.' || it == ' ' }
        s = s.take(MAX_NAME).trimEnd { it == '.' || it == ' ' }
        if (s.isEmpty()) return "untitled"
        val upper = s.substringBefore('.').uppercase(Locale.ROOT)
        val reserved = upper in setOf("CON", "PRN", "AUX", "NUL") ||
            ((upper.startsWith("COM") || upper.startsWith("LPT")) && upper.length == 4 && upper[3].isDigit())
        return if (reserved) s + "_" else s
    }

    /** `<Title> - 01` (two digits at least), no extension. */
    fun episodeBaseName(title: String, episode: Int): String = "${sanitize(title)} - ${"%02d".format(episode)}"

    /** `<folder>/<Title>/<Title> - 01` (no extension: HLS picks `.ts` or `.mp4` once the playlist is known). */
    fun episodeBase(folder: File, title: String, episode: Int): File = File(File(folder, sanitize(title)), episodeBaseName(title, episode))

    fun withExtension(base: File, ext: String): File = File(base.path + "." + ext)

    /** `<target>.part` (a direct file in progress, or the concatenation of the segments). */
    fun partFile(target: File): File = File(target.path + ".part")

    /** `<base>.parts/` (HLS segments in progress). */
    fun partsDir(base: File): File = File(base.path + ".parts")

    fun segmentFile(parts: File, index: Int): File = File(parts, "%05d.seg".format(index))

    /** Init segment (`#EXT-X-MAP`) inside the parts directory. */
    fun initFile(parts: File): File = File(parts, "init.seg")
}
