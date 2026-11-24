// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.min

// v1.1 (F2): the desktop counterpart of Android's EmbeddedSubtitleExtractor (Media3's extractors do not exist on desktop). It reads
// the text subtitle tracks of a Matroska / WebM or MP4 file itself, so pre-translation and look-ahead work for embedded tracks on
// desktop too. Matroska: EBML header, Info (timestamp scale, duration), Tracks (subtitle entries), then Clusters, where only the
// header of each block is read and the payload of a subtitle block (video / audio payloads are skipped, never read when they are
// large). MP4: the sample tables of `moov` (tx3g / WebVTT tracks), then each subtitle sample at its offset. Samples are turned into
// the raw form Media3 emits (SSA `Dialogue:` lines, SubRip / WebVTT blocks with times relative to the sample) and decoded by the
// shared [SubtitleSamples], so both platforms produce the same cues.
//
// The byte source rules are Android's: a torrent's loopback stream is only read where it is downloaded (waits, then resumes at the
// next Matroska Cluster; jumps to the playhead when the viewer is far ahead), a remote file starts near the playhead, stays at most a
// few minutes ahead of it and has a byte budget. Fragmented MP4 is not read (rare for subtitles outside streaming, which goes
// through the HLS reader).

private class BudgetExceeded : IOException("byte budget exhausted")

/** The container is not what its header said (damage or a hole): resync at a Cluster (Matroska) or give up. */
private class Malformed(message: String) : IOException(message)

class ContainerSubtitleReader(
    private val source: MediaByteSource,
    private val playheadMs: () -> Long,
    private val policy: Policy = Policy.forSource(source),
) : EmbeddedSubtitleReader {
    /** Same meaning as EmbeddedSubtitleExtractor.Policy (Android). */
    data class Policy(val maxBytes: Long, val maxAheadMs: Long?, val startNearPlayhead: Boolean, val waitMs: Long = WAIT_MS) {
        companion object {
            fun forSource(source: MediaByteSource) =
                if (source.remote) Policy(maxBytes = 1_500L * 1024 * 1024, maxAheadMs = 6 * 60_000L, startNearPlayhead = true)
                else Policy(maxBytes = Long.MAX_VALUE, maxAheadMs = null, startNearPlayhead = false)
        }
    }

    private val _state = MutableStateFlow(EmbeddedSubtitlesState())
    override val state: StateFlow<EmbeddedSubtitlesState> = _state.asStateFlow()

    private val collectors = LinkedHashMap<Int, Collector>()
    private var bytesRead = 0L
    private var lastSampleMs = -1L
    private var runStartMs = 0L
    private var dirtyCues = 0
    private var lastPublish = 0L
    private var tracksEnded = false
    private var durationMs = -1L
    private var firstClusterPos = -1L

    override suspend fun run() = withContext(Dispatchers.IO) {
        try {
            source.prepare()
            when (sniff()) {
                Kind.MKV -> runMatroska()
                Kind.MP4 -> runMp4()
                null -> finish(failure = "unsupported container")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: BudgetExceeded) {
            finish(failure = "budget")
        } catch (e: StackOverflowError) {
            // v1.1 review #7: last resort (the walks are bounded); a broken file must never take the player down.
            finish(failure = "malformed container")
        } catch (e: OutOfMemoryError) {
            finish(failure = "malformed container")
        } catch (e: Exception) {
            finish(failure = e.message ?: e.javaClass.simpleName)
        } finally {
            cursor?.close()
            runCatching { source.close() }
        }
    }

    private enum class Kind { MKV, MP4 }

    private suspend fun sniff(): Kind? {
        while (true) {
            coroutineContext.ensureActive()
            try {
                val head = ByteArray(12)
                val c = cursorAt(0)
                val n = c.readUpTo(head, 12)
                if (n >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()) {
                    return Kind.MKV
                }
                if (n >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) in MP4_FIRST_BOXES) return Kind.MP4
                return null
            } catch (e: DataNotAvailableException) {
                waitForData()
            }
        }
    }

    // ---- byte access --------------------------------------------------------------------------------------------------------------

    private var cursor: Cursor? = null

    private fun cursorAt(position: Long): Cursor {
        cursor?.close()
        return Cursor(position).also { cursor = it }
    }

    /**
     * Sequential reads from [pos] (one open connection / channel position), never past what the source has downloaded
     * ([DataNotAvailableException]) and counted against the budget. Large skips reopen at the target instead of reading through.
     */
    private inner class Cursor(var pos: Long) {
        private var raw: RawReader? = null

        fun close() {
            runCatching { raw?.close() }
            raw = null
        }

        /** Up to [len] bytes into [buf]; fewer only at the end of the file. */
        fun readUpTo(buf: ByteArray, len: Int, offset: Int = 0): Int {
            var got = 0
            while (got < len) {
                val total = source.length
                if (total >= 0 && pos >= total) break
                var want = len - got
                val ranges = source.available()
                if (ranges != null) {
                    val r = ranges.firstOrNull { pos in it } ?: throw DataNotAvailableException(pos)
                    want = min(want.toLong(), r.last - pos + 1).toInt()
                }
                val reader = raw ?: source.openAt(pos).also { raw = it }
                val n = reader.read(buf, offset + got, want)
                if (n < 0) break
                pos += n
                got += n
                bytesRead += n
                if (bytesRead > policy.maxBytes) throw BudgetExceeded()
            }
            return got
        }

        fun readFully(len: Int): ByteArray {
            val b = ByteArray(len)
            if (readUpTo(b, len) < len) throw EOFException()
            return b
        }

        fun byte(): Int {
            val b = ByteArray(1)
            if (readUpTo(b, 1) < 1) throw EOFException()
            return b[0].toInt() and 0xFF
        }

        /** Moves [n] bytes on: read through when short (one connection), else reopen there (nothing in between is read). */
        fun skip(n: Long) {
            if (n <= 0) return
            if (n <= READ_THROUGH_BYTES) {
                val scratch = ByteArray(min(n, SKIP_CHUNK.toLong()).toInt())
                var left = n
                while (left > 0) {
                    val got = readUpTo(scratch, min(left, scratch.size.toLong()).toInt())
                    if (got <= 0) throw EOFException()
                    left -= got
                }
            } else {
                close()
                pos += n
            }
        }

        fun seek(to: Long) {
            if (to == pos) return
            if (to > pos && to - pos <= READ_THROUGH_BYTES) skip(to - pos) else {
                close()
                pos = to
            }
        }
    }

    private suspend fun waitForData() {
        publish(force = true, waiting = true)
        delay(policy.waitMs)
    }

    // ---- Matroska -----------------------------------------------------------------------------------------------------------------

    private class MkvTrack(
        val number: Int, val ordinal: Int, val mime: String?, val language: String?, val name: String?, val webmVtt: Boolean = false,
    )

    private var timestampScaleNs = 1_000_000L
    private val mkvTracks = HashMap<Int, MkvTrack>()

    private suspend fun runMatroska() {
        var c = cursorAt(0)
        var gapped = false
        var startDecided = false
        var resyncs = 0
        var lastFarCheck = 0L
        // Segment level: children of the Segment are read here; a Cluster is entered (its blocks) and left again.
        var segmentEnd = Long.MAX_VALUE
        var inSegment = false
        var clusterEnd = -1L // -1 = not in a cluster
        var clusterTimestamp = 0L
        while (true) {
            coroutineContext.ensureActive()
            try {
                val total = source.length
                if (total >= 0 && c.pos >= total || c.pos >= segmentEnd) {
                    flushOpen()
                    return finish(complete = !gapped)
                }
                if (clusterEnd >= 0 && c.pos >= clusterEnd) clusterEnd = -1
                val start = c.pos
                val id = readId(c)
                val size = readSize(c)
                val dataStart = c.pos
                if (!inSegment) {
                    when (id) {
                        EBML -> c.skip(size ?: throw Malformed("EBML header of unknown size"))
                        SEGMENT -> {
                            inSegment = true
                            segmentEnd = size?.let { dataStart + it } ?: Long.MAX_VALUE
                        }
                        else -> throw Malformed("not a Matroska segment")
                    }
                    continue
                }
                if (clusterEnd >= 0 && id in TOP_LEVEL) {
                    // A cluster of unknown size ends where the next top-level element starts.
                    clusterEnd = -1
                }
                if (clusterEnd >= 0) {
                    when (id) {
                        // v1.1 review #7: an unsigned integer is at most 8 bytes (never allocate what a damaged size says).
                        TIMESTAMP -> clusterTimestamp = uint(c.readFully(checkedSize(size, 8)))
                        SIMPLE_BLOCK -> block(c, size ?: throw Malformed("block size"), clusterTimestamp, null)
                        BLOCK_GROUP -> blockGroup(c, size ?: throw Malformed("group size"), clusterTimestamp)
                        else -> c.skip(size ?: throw Malformed("unknown-size element in a cluster"))
                    }
                    publish()
                } else {
                    when (id) {
                        INFO -> info(c.readFully(checkedSize(size, MAX_HEADER_ELEMENT)))
                        TRACKS -> tracks(c.readFully(checkedSize(size, MAX_HEADER_ELEMENT)))
                        CLUSTER -> {
                            if (firstClusterPos < 0) firstClusterPos = start
                            if (!tracksEnded) {
                                tracksEnded = true
                                publish(force = true)
                            }
                            clusterEnd = size?.let { dataStart + it } ?: Long.MAX_VALUE
                            clusterTimestamp = 0
                        }
                        else -> c.skip(size ?: throw Malformed("unknown-size top-level element"))
                    }
                }
                if (!tracksEnded || firstClusterPos < 0) continue
                // Remote: after the header, start where the viewer is (the beginning is behind them).
                if (policy.startNearPlayhead && !startDecided) {
                    startDecided = true
                    val target = (playheadMs() - JUMP_BACK_MS).coerceAtLeast(0)
                    if (target > FAR_MS) {
                        jumpTo(target, c.pos)?.let { at ->
                            gapped = true
                            resetOpen()
                            c = cursorAt(at)
                            clusterEnd = -1
                            runStartMs = target
                        }
                    }
                }
                if (policy.maxAheadMs != null) paceRemote()
                val now = System.nanoTime() / 1_000_000
                if (policy.maxAheadMs != null && now - lastFarCheck > FAR_CHECK_MS) {
                    lastFarCheck = now
                    val target = (playheadMs() - JUMP_BACK_MS).coerceAtLeast(0)
                    if (target > maxOf(lastSampleMs, 0) + FAR_MS) {
                        jumpTo(target, c.pos)?.let { at ->
                            gapped = true
                            resetOpen()
                            c = cursorAt(at)
                            clusterEnd = -1
                            runStartMs = target
                        }
                    }
                }
            } catch (e: DataNotAvailableException) {
                if (!tracksEnded) {
                    // The header itself is not downloaded yet: start over once it is.
                    waitForData()
                    resetOpen()
                    inSegment = false
                    segmentEnd = Long.MAX_VALUE
                    clusterEnd = -1
                    c = cursorAt(0)
                    continue
                }
                // Only ever continue from a Cluster start (resumePoint waits for it to be downloaded).
                var at: Long?
                do {
                    at = resumePoint(e.position)
                } while (at == null)
                if (at > e.position) gapped = true
                resetOpen()
                c = cursorAt(at)
                clusterEnd = -1
            } catch (e: Malformed) {
                if (!tracksEnded || ++resyncs > MAX_RESYNCS) throw e
                val at = findClusterForward(c.pos + 1, RESYNC_SCAN_BYTES) ?: throw e
                gapped = true
                resetOpen()
                c = cursorAt(at)
                clusterEnd = -1
            } catch (e: EOFException) {
                flushOpen()
                return finish(complete = !gapped)
            }
        }
    }

    /** EBML id at the cursor (marker kept). */
    private fun readId(c: Cursor): Long {
        val first = c.byte()
        val len = vintLength(first)
        if (len !in 1..4) throw Malformed("bad element id")
        var v = first.toLong()
        repeat(len - 1) { v = (v shl 8) or c.byte().toLong() }
        return v
    }

    /** EBML data size at the cursor; null = unknown (all ones). */
    private fun readSize(c: Cursor): Long? {
        val (v, len) = readVint(c)
        return if (v == (1L shl (7 * len)) - 1) null else v
    }

    /** An EBML vint (marker removed) and its length. */
    private fun readVint(c: Cursor): Pair<Long, Int> {
        val first = c.byte()
        val len = vintLength(first)
        if (len !in 1..8) throw Malformed("bad vint")
        var v = (first.toLong() and 0xFF) and ((1L shl (8 - len)) - 1)
        repeat(len - 1) { v = (v shl 8) or c.byte().toLong() }
        return v to len
    }

    private fun checkedSize(size: Long?, max: Long): Int {
        if (size == null || size < 0 || size > max) throw Malformed("header element too large")
        return size.toInt()
    }

    /** A Block / SimpleBlock of [size] bytes at the cursor: decoded when it belongs to a text subtitle track, else skipped. */
    private fun block(c: Cursor, size: Long, clusterTs: Long, durationTicks: Long?) {
        val start = c.pos
        val (track, trackLen) = readVint(c)
        val tc = c.readFully(3) // int16 relative timestamp + flags
        val headerLen = trackLen + 3
        val t = mkvTracks[track.toInt()]
        val payload = size - headerLen
        if (t == null || !SubtitleSamples.isText(t.mime) || payload <= 0 || payload > MAX_SAMPLE_BYTES) {
            c.seek(start + size)
            return
        }
        // Subtitle blocks are never laced (Matroska spec); a laced one is skipped.
        if ((tc[2].toInt() and 0x06) != 0) {
            c.seek(start + size)
            return
        }
        val data = c.readFully(payload.toInt())
        val rel = ((tc[0].toInt() shl 8) or (tc[1].toInt() and 0xFF)).toShort().toLong()
        val startMs = (clusterTs + rel) * timestampScaleNs / 1_000_000
        val durMs = durationTicks?.let { it * timestampScaleNs / 1_000_000 } ?: OPEN_CUE_MS
        val raw = mkvRawSample(t.mime, data, durMs, webmVtt = t.webmVtt) ?: return
        runCatching { SubtitleSamples.decode(t.mime, startMs, raw) }.getOrNull()?.let { collector(t).accept(it) }
    }

    /** A BlockGroup: its Block plus BlockDuration (which may come after the Block: the Block's position is kept until then). */
    private fun blockGroup(c: Cursor, size: Long, clusterTs: Long) {
        val end = c.pos + size
        var blockAt = -1L
        var blockSize = 0L
        var duration: Long? = null
        while (c.pos < end) {
            val id = readId(c)
            val s = readSize(c) ?: throw Malformed("unknown size in a block group")
            when (id) {
                BLOCK -> {
                    // Peek the track: a non-subtitle block's group is left right away.
                    val at = c.pos
                    val (track, _) = readVint(c)
                    val t = mkvTracks[track.toInt()]
                    if (t == null || !SubtitleSamples.isText(t.mime)) {
                        c.seek(end)
                        return
                    }
                    blockAt = at
                    blockSize = s
                    c.seek(at + s)
                }
                BLOCK_DURATION -> duration = uint(c.readFully(checkedSize(s, 8)))
                else -> c.skip(s)
            }
        }
        if (blockAt < 0) return
        c.seek(blockAt)
        block(c, blockSize, clusterTs, duration)
        c.seek(end)
    }

    private fun info(b: ByteArray) {
        var durationTicks: Double? = null
        children(b, 0, b.size) { id, s, e ->
            when (id) {
                TIMESTAMP_SCALE -> timestampScaleNs = uint(b.copyOfRange(s, e)).takeIf { it > 0 } ?: 1_000_000L
                DURATION -> durationTicks = float(b, s, e - s)
            }
        }
        durationTicks?.let { durationMs = (it * timestampScaleNs / 1_000_000).toLong() }
    }

    private fun tracks(b: ByteArray) {
        var ordinal = 0
        children(b, 0, b.size) { id, s, e ->
            if (id != TRACK_ENTRY) return@children
            var number = -1
            var type = -1
            var codec: String? = null
            var lang: String? = null
            var bcp47: String? = null
            var name: String? = null
            children(b, s, e) { cid, cs, ce ->
                when (cid) {
                    TRACK_NUMBER -> number = uint(b.copyOfRange(cs, ce)).toInt()
                    TRACK_TYPE -> type = uint(b.copyOfRange(cs, ce)).toInt()
                    CODEC_ID -> codec = str(b, cs, ce)
                    LANGUAGE -> lang = str(b, cs, ce)
                    LANGUAGE_BCP47 -> bcp47 = str(b, cs, ce)
                    NAME -> name = str(b, cs, ce)
                }
            }
            if (type == TRACK_TYPE_SUBTITLE && number >= 0 && mkvTracks.size < MAX_SUBTITLE_TRACKS) {
                val t = MkvTrack(number, ordinal++, mkvMime(codec), bcp47 ?: lang ?: "eng", name, webmVtt = codec == WEBM_VTT)
                mkvTracks[number] = t
                collector(t)
            }
        }
        publish(force = true)
    }

    private fun collector(t: MkvTrack): Collector = collectors.getOrPut(t.number) { Collector(EmbeddedTrack(t.number, t.ordinal, t.mime, t.language, t.name)) }

    // ---- Matroska resync / jumps (Android's logic over this reader) ---------------------------------------------------------------

    private suspend fun paceRemote() {
        val ahead = policy.maxAheadMs ?: return
        while (lastSampleMs >= 0 && lastSampleMs > playheadMs() + ahead) {
            publish(force = true)
            delay(policy.waitMs)
            if (playheadMs() < runStartMs - FAR_MS) return
        }
    }

    private suspend fun resumePoint(failedAt: Long): Long? {
        val ranges = source.available() ?: return failedAt
        val here = ranges.firstOrNull { failedAt in it }
        val total = source.length
        fun usable(r: LongRange, from: Long) = r.last - from >= MIN_RESUME_BYTES || (total > 0 && r.last >= total - 1)
        if (here != null && usable(here, failedAt)) {
            clusterNear(failedAt, here)?.let { return it }
        }
        if (firstClusterPos >= 0) {
            val target = (playheadMs() - JUMP_BACK_MS).coerceAtLeast(0)
            if (target > lastSampleMs + FAR_MS) {
                val estimate = estimateByte(target)
                val range = ranges.firstOrNull { estimate in it && usable(it, estimate) }
                    ?: ranges.firstOrNull { it.first > estimate && it.first - estimate < FAR_BYTES }
                if (range != null) {
                    clusterNear(maxOf(range.first, estimate), range)?.let { at ->
                        runStartMs = target
                        return at
                    }
                }
            }
        }
        waitForData()
        return null
    }

    private suspend fun clusterNear(pos: Long, range: LongRange): Long? =
        findClusterBackward(pos, floor = range.first) ?: findClusterForward(pos, minOf(RESYNC_SCAN_BYTES, range.last - pos + 1))

    private fun estimateByte(timeMs: Long): Long {
        val total = source.length
        if (total <= 0 || durationMs <= 0 || firstClusterPos < 0) return firstClusterPos.coerceAtLeast(0)
        val frac = (timeMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        return firstClusterPos + ((total - firstClusterPos) * frac).toLong()
    }

    private suspend fun jumpTo(timeMs: Long, current: Long): Long? {
        val estimate = estimateByte(timeMs)
        if (estimate <= firstClusterPos || estimate < current + MIN_JUMP_BYTES) return null
        val ranges = source.available()
        val range = ranges?.firstOrNull { estimate in it } ?: if (ranges != null) return null else 0L until Long.MAX_VALUE
        return clusterNear(estimate, range)
    }

    private fun readable(pos: Long, len: Int): Int {
        val ranges = source.available() ?: return len
        val r = ranges.firstOrNull { pos in it } ?: return 0
        return min(len.toLong(), r.last - pos + 1).toInt()
    }

    private fun readBlock(pos: Long, buf: ByteArray, len: Int): Int {
        val n = readable(pos, len)
        if (n <= 0) return 0
        var got = 0
        while (got < n) {
            val r = source.readAt(pos + got, buf, got, n - got)
            if (r <= 0) break
            got += r
        }
        bytesRead += got
        if (bytesRead > policy.maxBytes) throw BudgetExceeded()
        return got
    }

    private suspend fun findClusterForward(from: Long, maxScan: Long): Long? {
        val buf = ByteArray(SCAN_CHUNK + 16)
        var pos = from
        val end = from + maxScan
        while (pos < end) {
            coroutineContext.ensureActive()
            val n = readBlock(pos, buf, minOf(buf.size.toLong(), end - pos + 16).toInt())
            if (n < 4) return null
            val lastI = if (n == buf.size) n - 16 else n - 4
            for (i in 0..lastI) if (isClusterAt(buf, i, n)) return pos + i
            pos += lastI + 1
        }
        return null
    }

    private suspend fun findClusterBackward(pos: Long, floor: Long = 0): Long? {
        val buf = ByteArray(SCAN_CHUNK + 16)
        var windowEnd = pos + 4
        val stop = maxOf(pos - BACK_SCAN_BYTES, firstClusterPos.coerceAtLeast(0), floor)
        while (windowEnd > stop) {
            coroutineContext.ensureActive()
            val start = (windowEnd - SCAN_CHUNK).coerceAtLeast(stop)
            val n = readBlock(start, buf, (windowEnd - start).toInt() + 12)
            if (n < 4) return null
            for (i in minOf(n - 4, (pos - start).toInt()) downTo 0) if (isClusterAt(buf, i, n)) return start + i
            windowEnd = start + 3
            if (start == stop) break
        }
        return null
    }

    // ---- MP4 ----------------------------------------------------------------------------------------------------------------------

    private suspend fun runMp4() {
        var attempts = 0
        var lastAvailable = -1L
        while (true) {
            coroutineContext.ensureActive()
            try {
                return readMp4()
            } catch (e: DataNotAvailableException) {
                // The sample tables live in `moov`, which can be anywhere: try again from the start once more is downloaded.
                if (++attempts > MAX_MP4_RETRIES) return finish(failure = "not downloaded")
                resetOpen()
                while (true) {
                    waitForData()
                    val avail = source.available()?.sumOf { it.last - it.first + 1 } ?: Long.MAX_VALUE
                    if (avail - lastAvailable >= MP4_RETRY_GROWTH || avail == Long.MAX_VALUE) {
                        lastAvailable = avail
                        break
                    }
                }
            }
        }
    }

    private class Mp4Sample(val offset: Long, val size: Int, val startMs: Long, val durMs: Long)

    private class Mp4Track(val id: Int, val ordinal: Int, val mime: String?, val language: String?, val samples: List<Mp4Sample>)

    private suspend fun readMp4() {
        val c = cursorAt(0)
        var moov: ByteArray? = null
        while (moov == null) {
            val total = source.length
            if (total >= 0 && c.pos >= total) break
            val start = c.pos
            val head = ByteArray(8)
            if (c.readUpTo(head, 8) < 8) break
            var size = u32(head, 0)
            val type = String(head, 4, 4, Charsets.ISO_8859_1)
            var headerLen = 8
            if (size == 1L) {
                size = u64(c.readFully(8), 0)
                headerLen = 16
            } else if (size == 0L) {
                size = if (total >= 0) total - start else Long.MAX_VALUE
            }
            if (size < headerLen) throw Malformed("bad box size")
            when (type) {
                "moov" -> moov = c.readFully(checkedSize(size - headerLen, MAX_MOOV))
                "moof" -> return finish(failure = "fragmented mp4")
                else -> c.seek(start + size)
            }
        }
        val m = moov ?: return finish(failure = "no moov")
        val tracks = mp4Tracks(m)
        tracksEnded = true
        for (t in tracks) collectors.getOrPut(t.id) { Collector(EmbeddedTrack(t.id, t.ordinal, t.mime, t.language, null)) }
        publish(force = true)
        val all = tracks.filter { SubtitleSamples.isText(it.mime) }.flatMap { t -> t.samples.map { t to it } }.sortedBy { it.second.offset }
        var sampleBytes = 0L
        for ((t, s) in all) {
            coroutineContext.ensureActive()
            if (s.size <= 0 || s.size > MAX_SAMPLE_BYTES) continue
            // v1.1 review #7: subtitles are small; damaged tables pointing at megabytes many times over stop here.
            sampleBytes += s.size
            if (sampleBytes > MAX_MP4_SUBTITLE_BYTES) return finish(failure = "too much subtitle data")
            c.seek(s.offset)
            val data = c.readFully(s.size)
            val cue = runCatching { SubtitleSamples.decode(t.mime, s.startMs, data) }.getOrNull() ?: continue
            // The sample table knows each sample's duration: no "until the next one" guess.
            collectors[t.id]?.accept(cue.copy(endMs = cue.endMs ?: (s.startMs + s.durMs)))
            publish()
        }
        flushOpen()
        finish(complete = true)
    }

    private fun mp4Tracks(moov: ByteArray): List<Mp4Track> {
        val out = ArrayList<Mp4Track>()
        var ordinal = 0
        boxes(moov, 0, moov.size) { type, s, e ->
            if (type != "trak") return@boxes
            var id = 0
            var timescale = 0L
            var lang: String? = null
            var handler: String? = null
            var mime: String? = null
            var stts: Pair<Int, Int>? = null
            var stsc: Pair<Int, Int>? = null
            var stsz: Pair<Int, Int>? = null
            var stco: Pair<Int, Int>? = null
            var co64 = false
            // v1.1 review #7: trak > mdia > minf > stbl is three levels; a file that nests deeper is damaged (no StackOverflowError).
            fun walk(from: Int, to: Int, depth: Int) {
                if (depth > MAX_BOX_DEPTH) throw Malformed("boxes nested too deep")
                boxes(moov, from, to) { t, bs, be ->
                    when (t) {
                        "mdia", "minf", "stbl" -> walk(bs, be, depth + 1)
                        "tkhd" -> id = u32(moov, bs + if (moov[bs].toInt() == 1) 20 else 12).toInt()
                        "mdhd" -> {
                            val v1 = moov[bs].toInt() == 1
                            timescale = u32(moov, bs + if (v1) 20 else 12)
                            val l = ((moov[bs + if (v1) 32 else 20].toInt() and 0xFF) shl 8) or (moov[bs + if (v1) 33 else 21].toInt() and 0xFF)
                            lang = String(charArrayOf(((l shr 10 and 0x1F) + 0x60).toChar(), ((l shr 5 and 0x1F) + 0x60).toChar(), ((l and 0x1F) + 0x60).toChar()))
                                .takeIf { it.all { ch -> ch in 'a'..'z' } && it != "und" }
                        }
                        "hdlr" -> handler = String(moov, bs + 8, 4, Charsets.ISO_8859_1)
                        "stsd" -> if (be - bs >= 16) {
                            mime = when (String(moov, bs + 12, 4, Charsets.ISO_8859_1)) {
                                "tx3g" -> SubtitleMimes.TX3G
                                "wvtt" -> SubtitleMimes.MP4VTT
                                else -> null
                            }
                        }
                        "stts" -> stts = bs to be
                        "stsc" -> stsc = bs to be
                        "stsz" -> stsz = bs to be
                        "stco" -> stco = bs to be
                        "co64" -> { stco = bs to be; co64 = true }
                    }
                }
            }
            walk(s, e, 0)
            if (handler !in setOf("sbtl", "text", "subt") || out.size >= MAX_SUBTITLE_TRACKS) return@boxes
            val samples = runCatching { mp4Samples(moov, timescale, stts, stsc, stsz, stco, co64) }.getOrDefault(emptyList())
            out += Mp4Track(id, ordinal++, mime, lang, samples)
        }
        return out
    }

    /**
     * The samples of one track from its sample tables. v1.1 review #7: every count is bounded by its box's size and by
     * [MAX_CUES_PER_TRACK], and every loop by the number of samples, so damaged tables cannot allocate or spin without end.
     */
    private fun mp4Samples(
        b: ByteArray, timescale: Long, stts: Pair<Int, Int>?, stsc: Pair<Int, Int>?, stsz: Pair<Int, Int>?, stco: Pair<Int, Int>?, co64: Boolean,
    ): List<Mp4Sample> = mp4SampleTable(b, timescale, stts, stsc, stsz, stco, co64, MAX_CUES_PER_TRACK * 2)
        .map { (offset, size, start, dur) -> Mp4Sample(offset, size, start, dur) }

    // ---- cues ---------------------------------------------------------------------------------------------------------------------

    /** One track's cues (Android's TextCollector without Media3): de-duplicated layers, karaoke / drawings set aside, capped. */
    private inner class Collector(val track: EmbeddedTrack) {
        private val seen = HashSet<Triple<Long, Long, String>>()
        val cues = ArrayList<TimedCue>()
        val skipped = HashSet<String>()
        private var open: SampleCue? = null
        var sorted = true

        fun accept(c: SampleCue) {
            if (c.startMs > lastSampleMs) lastSampleMs = c.startMs
            if (c.endMs == null) {
                open?.let { prev -> add(prev.copy(endMs = maxOf(c.startMs, prev.startMs + 1))) }
                open = c.takeIf { it.text.isNotEmpty() }
                return
            }
            add(c)
        }

        fun flushOpen() {
            open?.let { add(it.copy(endMs = it.startMs + OPEN_CUE_MS)) }
            open = null
        }

        fun resetOpen() {
            open = null
        }

        private fun add(c: SampleCue) {
            val end = c.endMs ?: return
            if (c.text.isEmpty() || end <= c.startMs) return
            if (c.karaoke || c.drawing) {
                skipped += c.text
                return
            }
            if (cues.size >= MAX_CUES_PER_TRACK) return
            if (!seen.add(Triple(c.startMs, end, c.text))) return
            if (cues.isNotEmpty() && cues.last().startMs > c.startMs) sorted = false
            cues += TimedCue(c.startMs, end, c.text)
            dirtyCues++
        }
    }

    private fun flushOpen() = collectors.values.forEach { it.flushOpen() }

    private fun resetOpen() = collectors.values.forEach { it.resetOpen() }

    private fun publish(force: Boolean = false, waiting: Boolean = false) {
        val now = System.nanoTime() / 1_000_000
        if (!force && dirtyCues < PUBLISH_EVERY_CUES && now - lastPublish < PUBLISH_EVERY_MS) return
        lastPublish = now
        dirtyCues = 0
        _state.value = snapshot().copy(waiting = waiting)
    }

    private fun snapshot(): EmbeddedSubtitlesState {
        val tracks = collectors.values.map { it.track }
        val cues = collectors.values.filter { it.track.text }.associate { c ->
            if (!c.sorted) {
                c.cues.sortBy { it.startMs }
                c.sorted = true
            }
            c.track.id to c.cues.toList()
        }
        val skipped = collectors.values.filter { it.skipped.isNotEmpty() }.associate { it.track.id to it.skipped.toSet() }
        return EmbeddedSubtitlesState(tracks, cues, skipped)
    }

    private fun finish(complete: Boolean = false, failure: String? = null) {
        _state.value = snapshot().copy(complete = complete, failure = failure, finished = true)
    }

    companion object {
        private const val WAIT_MS = 3_000L
        private const val MIN_RESUME_BYTES = 2L * 1024 * 1024
        private const val FAR_MS = 2 * 60_000L
        private const val JUMP_BACK_MS = 20_000L
        private const val FAR_BYTES = 64L * 1024 * 1024
        private const val MIN_JUMP_BYTES = 16L * 1024 * 1024
        private const val FAR_CHECK_MS = 10_000L
        private const val RESYNC_SCAN_BYTES = 16L * 1024 * 1024
        private const val BACK_SCAN_BYTES = 12L * 1024 * 1024
        private const val SCAN_CHUNK = 64 * 1024
        private const val MAX_RESYNCS = 32
        private const val MAX_MP4_RETRIES = 40
        private const val MP4_RETRY_GROWTH = 32L * 1024 * 1024
        private const val MAX_CUES_PER_TRACK = 20_000
        private const val OPEN_CUE_MS = 4_000L
        private const val PUBLISH_EVERY_CUES = 40
        private const val PUBLISH_EVERY_MS = 1_500L

        /** Skips up to this long are read through (one connection); longer ones reopen at the target. */
        private const val READ_THROUGH_BYTES = 256L * 1024
        private const val SKIP_CHUNK = 64 * 1024
        private const val MAX_HEADER_ELEMENT = 16L * 1024 * 1024
        private const val MAX_SAMPLE_BYTES = 1L * 1024 * 1024
        private const val MAX_MOOV = 64L * 1024 * 1024

        /** v1.1 review #7: limits against damaged / hostile files. */
        private const val MAX_BOX_DEPTH = 8
        private const val MAX_MP4_SUBTITLE_BYTES = 64L * 1024 * 1024
        private const val MAX_SUBTITLE_TRACKS = 64

        private val MP4_FIRST_BOXES = setOf("ftyp", "moov", "mdat", "free", "skip", "wide", "pnot")

        // Matroska element ids (with their length marker, as written).
        private const val EBML = 0x1A45DFA3L
        private const val SEGMENT = 0x18538067L
        private const val SEEK_HEAD = 0x114D9B74L
        private const val INFO = 0x1549A966L
        private const val TRACKS = 0x1654AE6BL
        private const val CLUSTER = 0x1F43B675L
        private const val CUES = 0x1C53BB6BL
        private const val CHAPTERS = 0x1043A770L
        private const val TAGS = 0x1254C367L
        private const val ATTACHMENTS = 0x1941A469L
        private const val TIMESTAMP_SCALE = 0x2AD7B1L
        private const val DURATION = 0x4489L
        private const val TRACK_ENTRY = 0xAEL
        private const val TRACK_NUMBER = 0xD7L
        private const val TRACK_TYPE = 0x83L
        private const val CODEC_ID = 0x86L
        private const val LANGUAGE = 0x22B59CL
        private const val LANGUAGE_BCP47 = 0x22B59DL
        private const val NAME = 0x536EL
        private const val TIMESTAMP = 0xE7L
        private const val SIMPLE_BLOCK = 0xA3L
        private const val BLOCK_GROUP = 0xA0L
        private const val BLOCK = 0xA1L
        private const val BLOCK_DURATION = 0x9BL
        private const val TRACK_TYPE_SUBTITLE = 0x11
        private val TOP_LEVEL = setOf(SEEK_HEAD, INFO, TRACKS, CLUSTER, CUES, CHAPTERS, TAGS, ATTACHMENTS)
    }
}

// ---- Matroska helpers (pure) ----------------------------------------------------------------------------------------------------

/** Media3-style mime of a Matroska subtitle CodecID. */
internal fun mkvMime(codecId: String?): String? = when (codecId) {
    "S_TEXT/ASS", "S_TEXT/SSA", "S_ASS", "S_SSA" -> SubtitleMimes.SSA
    "S_TEXT/UTF8", "S_TEXT/ASCII" -> SubtitleMimes.SUBRIP
    "S_TEXT/WEBVTT", WEBM_VTT -> SubtitleMimes.VTT
    "S_HDMV/PGS" -> SubtitleMimes.PGS
    "S_VOBSUB" -> SubtitleMimes.VOBSUB
    "S_DVBSUB" -> SubtitleMimes.DVBSUBS
    else -> codecId
}

/** WebM's WebVTT CodecID: each block is `<cue id>\n<cue settings>\n<text>` (Matroska's S_TEXT/WEBVTT block is only the text). */
internal const val WEBM_VTT = "D_WEBVTT/SUBTITLES"

/**
 * A Matroska subtitle block as the raw sample Media3's MatroskaExtractor emits (the form [SubtitleSamples.decode] reads): SSA blocks
 * (`ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text`) become `Dialogue: 0:00:00:00,<duration>,<block>`; SubRip and
 * WebVTT become one block with times relative to the sample.
 */
internal fun mkvRawSample(mime: String?, data: ByteArray, durationMs: Long, webmVtt: Boolean = false): ByteArray? {
    var text = String(data, Charsets.UTF_8).trimEnd('\u0000')
    if (webmVtt) text = text.split('\n', limit = 3).getOrElse(2) { "" }
    val raw = when (mime) {
        SubtitleMimes.SSA -> "Dialogue: 0:00:00:00,${ssaClock(durationMs)},$text"
        SubtitleMimes.SUBRIP -> "1\n00:00:00,000 --> ${srtClock(durationMs)}\n$text\n"
        SubtitleMimes.VTT -> "WEBVTT\n\n00:00:00.000 --> ${srtClock(durationMs).replace(',', '.')}\n$text\n"
        else -> return null
    }
    return raw.toByteArray(Charsets.UTF_8)
}

private fun ssaClock(ms: Long): String {
    val cs = ms.coerceAtLeast(0) / 10
    return "%d:%02d:%02d:%02d".format(java.util.Locale.ROOT, cs / 360_000, cs / 6_000 % 60, cs / 100 % 60, cs % 100)
}

private fun srtClock(ms: Long): String {
    val v = ms.coerceAtLeast(0)
    return "%02d:%02d:%02d,%03d".format(java.util.Locale.ROOT, v / 3_600_000, v / 60_000 % 60, v / 1000 % 60, v % 1000)
}

/** One MP4 sample: file offset, size, start and duration in milliseconds. */
internal data class Mp4TableSample(val offset: Long, val size: Int, val startMs: Long, val durMs: Long)

/**
 * The samples an MP4 track's tables describe ([stts] durations, [stsz] sizes, [stco] / co64 chunk offsets split by [stsc]); each
 * table is the (start, end) of its box's payload in [b]. Pure and bounded (v1.1 review #7): at most [maxSamples] samples, entry
 * counts never larger than what fits in their box, chunk ranges walked once in increasing order. Damaged tables give fewer or no
 * samples, never an endless loop or a huge allocation.
 */
internal fun mp4SampleTable(
    b: ByteArray, timescale: Long, stts: Pair<Int, Int>?, stsc: Pair<Int, Int>?, stsz: Pair<Int, Int>?, stco: Pair<Int, Int>?, co64: Boolean,
    maxSamples: Int,
): List<Mp4TableSample> {
    if (timescale <= 0 || stts == null || stsc == null || stsz == null || stco == null) return emptyList()
    fun fits(box: Pair<Int, Int>, header: Int, entry: Int, declared: Long): Int =
        min(declared, ((box.second - box.first - header).coerceAtLeast(0) / entry).toLong()).toInt().coerceAtLeast(0)
    if (stsz.second - stsz.first < 12) return emptyList()
    val fixed = u32(b, stsz.first + 4).toInt()
    val declaredCount = u32(b, stsz.first + 8)
    val count = (if (fixed != 0) min(declaredCount, maxSamples.toLong()).toInt() else fits(stsz, 12, 4, declaredCount)).coerceIn(0, maxSamples)
    if (count == 0) return emptyList()
    val sizes = IntArray(count) { i -> if (fixed != 0) fixed else u32(b, stsz.first + 12 + 4 * i).toInt() }
    // Times: each stts entry is (sample count, duration); stop as soon as every sample has its time.
    val starts = LongArray(count)
    val durs = LongArray(count)
    var i = 0
    var t = 0L
    if (stts.second - stts.first < 8) return emptyList()
    val sttsEntries = fits(stts, 8, 8, u32(b, stts.first + 4))
    for (e in 0 until sttsEntries) {
        if (i >= count) break
        val n = u32(b, stts.first + 8 + 8 * e)
        val d = u32(b, stts.first + 12 + 8 * e)
        val take = min(n, (count - i).toLong()).toInt()
        for (k in 0 until take) {
            starts[i] = t
            durs[i] = d
            i++
            t += d
        }
    }
    // Offsets: chunks (stco / co64) split into samples by stsc, whose first chunks must increase.
    if (stco.second - stco.first < 8 || stsc.second - stsc.first < 8) return emptyList()
    val chunks = fits(stco, 8, if (co64) 8 else 4, u32(b, stco.first + 4))
    fun chunkOffset(k: Int): Long = if (co64) u64(b, stco.first + 8 + 8 * k) else u32(b, stco.first + 8 + 4 * k)
    val stscEntries = fits(stsc, 8, 12, u32(b, stsc.first + 4))
    val offsets = LongArray(count)
    var sample = 0
    var nextChunk = 0
    for (e in 0 until stscEntries) {
        if (sample >= count || nextChunk >= chunks) break
        val firstChunk = (u32(b, stsc.first + 8 + 12 * e) - 1).coerceIn(0, chunks.toLong()).toInt()
        val perChunk = u32(b, stsc.first + 12 + 12 * e)
        val nextFirst = if (e + 1 < stscEntries) (u32(b, stsc.first + 8 + 12 * (e + 1)) - 1).coerceIn(0, chunks.toLong()).toInt() else chunks
        // Each chunk at most once, in order: a decreasing or repeated first chunk adds nothing.
        var chunk = maxOf(firstChunk, nextChunk)
        while (chunk < nextFirst && sample < count) {
            var off = chunkOffset(chunk)
            val inChunk = min(perChunk, (count - sample).toLong()).toInt()
            for (k in 0 until inChunk) {
                offsets[sample] = off
                off += sizes[sample]
                sample++
            }
            chunk++
        }
        nextChunk = maxOf(nextChunk, chunk)
    }
    val n = min(sample, i)
    return (0 until n).map { k -> Mp4TableSample(offsets[k], sizes[k], starts[k] * 1000 / timescale, durs[k] * 1000 / timescale) }
}

// ---- byte helpers (pure) ------------------------------------------------------------------------------------------------------------

/** Big-endian unsigned integer of up to 8 bytes. */
internal fun uint(b: ByteArray): Long = b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xFF) }

internal fun u32(b: ByteArray, at: Int): Long = uint(b.copyOfRange(at, at + 4))

internal fun u64(b: ByteArray, at: Int): Long = uint(b.copyOfRange(at, at + 8))

/** EBML float (4 or 8 bytes). */
internal fun float(b: ByteArray, at: Int, len: Int): Double? = when (len) {
    4 -> java.lang.Float.intBitsToFloat(u32(b, at).toInt()).toDouble()
    8 -> java.lang.Double.longBitsToDouble(u64(b, at))
    else -> null
}

internal fun str(b: ByteArray, from: Int, to: Int): String = String(b, from, to - from, Charsets.UTF_8).trimEnd('\u0000')

/** Length of an EBML vint from its first byte (1..8), 0 when invalid. */
internal fun vintLength(first: Int): Int = if (first == 0) 0 else Integer.numberOfLeadingZeros(first and 0xFF) - 23

/** EBML children of [b] in [from, to): (id with marker, data start, data end). Stops at anything malformed. */
internal fun children(b: ByteArray, from: Int, to: Int, visit: (id: Long, start: Int, end: Int) -> Unit) {
    var p = from
    while (p < to) {
        val idLen = vintLength(b[p].toInt())
        if (idLen !in 1..4 || p + idLen > to) return
        val id = uint(b.copyOfRange(p, p + idLen))
        p += idLen
        if (p >= to) return
        val sLen = vintLength(b[p].toInt())
        if (sLen !in 1..8 || p + sLen > to) return
        var size = (b[p].toLong() and 0xFF) and ((1L shl (8 - sLen)) - 1)
        for (k in 1 until sLen) size = (size shl 8) or (b[p + k].toLong() and 0xFF)
        p += sLen
        val end = p + size
        if (size < 0 || end > to) return
        visit(id, p, end.toInt())
        p = end.toInt()
    }
}

/** MP4 boxes of [b] in [from, to): (type, payload start, end). */
internal fun boxes(b: ByteArray, from: Int, to: Int, visit: (type: String, start: Int, end: Int) -> Unit) {
    var p = from
    while (p + 8 <= to) {
        var size = u32(b, p)
        var header = 8
        if (size == 1L && p + 16 <= to) {
            size = u64(b, p + 8)
            header = 16
        } else if (size == 0L) {
            size = (to - p).toLong()
        }
        if (size < header || p + size > to) return
        visit(String(b, p + 4, 4, Charsets.ISO_8859_1), p + header, (p + size).toInt())
        p += size.toInt()
    }
}

/** A Matroska Cluster id at [i] whose size vint is followed by a cluster child (Timestamp, Position, PrevSize). */
internal fun isClusterAt(b: ByteArray, i: Int, n: Int): Boolean {
    if (b[i] != 0x1F.toByte() || b[i + 1] != 0x43.toByte() || b[i + 2] != 0xB6.toByte() || b[i + 3] != 0x75.toByte()) return false
    val sizeAt = i + 4
    if (sizeAt >= n) return true
    val sizeLen = vintLength(b[sizeAt].toInt())
    if (sizeLen == 0) return false
    val childAt = sizeAt + sizeLen
    if (childAt >= n) return true
    val child = b[childAt].toInt() and 0xFF
    return child == 0xE7 || child == 0xA7 || child == 0xAB
}
