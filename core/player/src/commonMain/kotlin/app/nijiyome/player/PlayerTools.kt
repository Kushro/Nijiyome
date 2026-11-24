// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player

import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File

// Milestone 3: what the shared PlayerViewModel (:feature:player commonMain) needs from the platform's player tools. Android implements
// them with its existing classes (SubtitleTools, EmbeddedSubtitleExtractor, ExternalPlayer); desktop has none of them yet.

/** What to hand to the external app. [extras] values are String, Long or Array<String>. Pure, unit-tested. */
data class ExternalLaunch(
    val uri: String,
    val mimeType: String,
    val packageName: String?,
    val extras: Map<String, Any>,
)

/** Builds the [ExternalLaunch] of a source (Android: [ExternalPlayer], the EXTERNAL engine). */
fun interface ExternalLaunchBuilder {
    fun launch(source: PlayableSource, title: String, startMs: Long, packageName: String?): ExternalLaunch
}

/** Reads the subtitle tracks of one media file in the background (Android: [EmbeddedSubtitleExtractor]). */
interface EmbeddedSubtitleReader {
    val state: StateFlow<EmbeddedSubtitlesState>

    /** Returns when the file is fully read, the budget is spent, or it is cancelled. */
    suspend fun run()
}

/**
 * The subtitle helpers of the player (Android: [SubtitleTools]): fetch + parse external tracks, import a picked file ([D] = the
 * platform's document handle), translation, and random access to the playing file for the embedded-track reader.
 */
interface PlayerSubtitleTools<in D> {
    val translationCache: TranslationCache

    /** Downloads and parses the external track at [url] with the stream's [headers]; [via] = the stream's own client. */
    suspend fun fetch(url: String, headers: Map<String, String>, format: String? = null, via: OkHttpClient? = null): List<TimedCue>

    /** Copies a picked document where the engines can read it and parses it. Returns the file and its cues. */
    suspend fun import(uri: D): Pair<File, List<TimedCue>>

    fun translator(config: TranslatorConfig): SubtitleTranslator

    /** Random access to the playing file; null for streaming manifests and unknown schemes. */
    fun mediaSource(url: String, headers: Map<String, String>, torrentRanges: (() -> List<ClosedFloatingPointRange<Double>>)?): MediaByteSource?

    /** Cues of the HLS subtitle rendition the player selected; null when the stream has none. */
    suspend fun hlsSubtitles(masterUrl: String, headers: Map<String, String>, want: EmbeddedTrackRef): List<TimedCue>?

    /** The reader of the embedded subtitle tracks of [media]; [playheadMs] drives its pacing. */
    fun embeddedReader(media: MediaByteSource, playheadMs: () -> Long): EmbeddedSubtitleReader
}
