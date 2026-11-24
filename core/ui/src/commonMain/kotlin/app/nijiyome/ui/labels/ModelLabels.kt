// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.labels

import app.nijiyome.ui.resources.Res
import org.jetbrains.compose.resources.StringResource
import app.nijiyome.ui.resources.*
import app.nijiyome.model.ChapterKind
import app.nijiyome.model.DownloadState
import app.nijiyome.model.ListProvider
import app.nijiyome.model.ListStatus
import app.nijiyome.model.SkipKind
import app.nijiyome.model.SourceKind
import app.nijiyome.model.torrent.AfterWatch
import app.nijiyome.model.torrent.TorrentSection
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.model.torrent.TrackerStatus

// android wave contract (docs/redesign/12 §3.10): shared labels of model enums. The keys live in :core:ui values/ + values-es/
// (gradle.properties has nonTransitiveRClass=true: reference them as app.nijiyome.ui.Res.string.*). Implemented here (contract).

fun TorrentState.labelRes(): StringResource = when (this) {
    TorrentState.INITIALIZING -> Res.string.torrent_state_initializing
    TorrentState.DOWNLOADING -> Res.string.torrent_state_downloading
    TorrentState.SEEDING -> Res.string.torrent_state_seeding
    TorrentState.PAUSED -> Res.string.torrent_state_paused
    TorrentState.ERROR -> Res.string.torrent_state_error
}

fun TrackerStatus.labelRes(): StringResource = when (this) {
    TrackerStatus.WORKING -> Res.string.torrent_tracker_working
    TrackerStatus.UPDATING -> Res.string.torrent_tracker_updating
    TrackerStatus.FAILED -> Res.string.torrent_tracker_failed
    TrackerStatus.UNKNOWN -> Res.string.torrent_tracker_unknown
}

fun TorrentSection.labelRes(): StringResource = when (this) {
    TorrentSection.OVERVIEW -> Res.string.torrent_section_overview
    TorrentSection.FILES -> Res.string.torrent_section_files
    TorrentSection.PEERS -> Res.string.torrent_section_peers
    TorrentSection.TRACKERS -> Res.string.torrent_section_trackers
    TorrentSection.LIBRARY -> Res.string.torrent_section_library
    TorrentSection.DOWNLOADS -> Res.string.torrent_section_downloads
    TorrentSection.HISTORY -> Res.string.torrent_section_history
    TorrentSection.DEBRID -> Res.string.torrent_section_debrid
    TorrentSection.SETTINGS -> Res.string.torrent_section_settings
}

fun AfterWatch.labelRes(): StringResource = when (this) {
    AfterWatch.KEEP -> Res.string.torrent_after_watch_keep
    AfterWatch.DELETE -> Res.string.torrent_after_watch_delete
}

fun SkipKind.labelRes(): StringResource = when (this) {
    SkipKind.INTRO -> Res.string.player_skip_intro
    SkipKind.OUTRO -> Res.string.player_skip_outro
    SkipKind.RECAP -> Res.string.player_skip_recap
}

fun SourceKind.labelRes(): StringResource = when (this) {
    SourceKind.WEB -> Res.string.src_web
    SourceKind.TORRENT -> Res.string.src_torrent
}

// parity wave A (docs/redesign/15 §3.7): list labels shared by Library (q), ListButton (q, mounted by o) and Settings (q). Implemented here (contract).

fun ListStatus.labelRes(): StringResource = when (this) {
    ListStatus.WATCHING -> Res.string.list_status_watching
    ListStatus.PLANNING -> Res.string.list_status_planning
    ListStatus.COMPLETED -> Res.string.list_status_completed
    ListStatus.PAUSED -> Res.string.list_status_paused
    ListStatus.DROPPED -> Res.string.list_status_dropped
    ListStatus.REPEATING -> Res.string.list_status_repeating
}

fun ListProvider.labelRes(): StringResource = when (this) {
    ListProvider.ANILIST -> Res.string.list_provider_anilist
    ListProvider.MAL -> Res.string.list_provider_mal
}

// parity wave B (docs/redesign/16 §3.10): download states (Downloads page of r, Details badges), readlist labels (readers of s, same
// ListStatus enum as the anime list), chapter kinds. Implemented here (contract).

fun DownloadState.labelRes(): StringResource = when (this) {
    DownloadState.QUEUED -> Res.string.download_state_queued
    DownloadState.DOWNLOADING -> Res.string.download_state_downloading
    DownloadState.PAUSED -> Res.string.download_state_paused
    DownloadState.COMPLETED -> Res.string.download_state_completed
    DownloadState.FAILED -> Res.string.download_state_failed
    DownloadState.REMOVING -> Res.string.download_state_removing
    DownloadState.BROKEN -> Res.string.download_state_broken
}

/** The manga / novel wording of a [ListStatus] ("Reading" instead of "Watching"). */
fun ListStatus.readLabelRes(): StringResource = when (this) {
    ListStatus.WATCHING -> Res.string.read_status_watching
    ListStatus.PLANNING -> Res.string.read_status_planning
    ListStatus.COMPLETED -> Res.string.read_status_completed
    ListStatus.PAUSED -> Res.string.read_status_paused
    ListStatus.DROPPED -> Res.string.read_status_dropped
    ListStatus.REPEATING -> Res.string.read_status_repeating
}

fun ChapterKind.labelRes(): StringResource = when (this) {
    ChapterKind.MANGA -> Res.string.chapter_kind_manga
    ChapterKind.NOVEL -> Res.string.chapter_kind_novel
}

/**
 * BUG-072 (UX-13): an AniList genre ("Action", "Sci-Fi", "Slice of Life") as a string resource, with the desktop's words
 * (`tk-ui` i18n `search.genre.*`); null for a genre without a translation (shown as AniList sends it).
 */
fun genreLabelRes(genre: String): StringResource? = when (genre.trim().lowercase().replace(' ', '_').replace('-', '_')) {
    "action" -> Res.string.genre_action
    "adventure" -> Res.string.genre_adventure
    "comedy" -> Res.string.genre_comedy
    "drama" -> Res.string.genre_drama
    "ecchi" -> Res.string.genre_ecchi
    "fantasy" -> Res.string.genre_fantasy
    "horror" -> Res.string.genre_horror
    "mahou_shoujo" -> Res.string.genre_mahou_shoujo
    "mecha" -> Res.string.genre_mecha
    "music" -> Res.string.genre_music
    "mystery" -> Res.string.genre_mystery
    "psychological" -> Res.string.genre_psychological
    "romance" -> Res.string.genre_romance
    "sci_fi" -> Res.string.genre_sci_fi
    "slice_of_life" -> Res.string.genre_slice_of_life
    "sports" -> Res.string.genre_sports
    "supernatural" -> Res.string.genre_supernatural
    "thriller" -> Res.string.genre_thriller
    "hentai" -> Res.string.genre_hentai
    else -> null
}

/** BUG-072: [genreLabelRes] in the UI language, or the genre as AniList sends it. */
@androidx.compose.runtime.Composable
fun genreLabel(genre: String): String = genreLabelRes(genre)?.let { org.jetbrains.compose.resources.stringResource(it) } ?: genre

/** BUG-098: AniList `MediaSource` ("LIGHT_NOVEL", "OTHER"…) as a string resource; same words as Details (desktop `details.source.*`). */
fun mediaSourceLabelRes(source: String): StringResource = when (source.trim().uppercase()) {
    "ORIGINAL" -> Res.string.media_source_original
    "MANGA" -> Res.string.media_source_manga
    "LIGHT_NOVEL" -> Res.string.media_source_light_novel
    "NOVEL" -> Res.string.media_source_novel
    "WEB_NOVEL" -> Res.string.media_source_web_novel
    "VISUAL_NOVEL" -> Res.string.media_source_visual_novel
    "VIDEO_GAME", "GAME" -> Res.string.media_source_game
    "WEB_MANGA" -> Res.string.media_source_web_manga
    "ANIME" -> Res.string.media_source_anime
    "DOUJINSHI" -> Res.string.media_source_doujinshi
    "LIVE_ACTION" -> Res.string.media_source_live_action
    "COMIC" -> Res.string.media_source_comic
    "MULTIMEDIA_PROJECT" -> Res.string.media_source_multimedia
    "PICTURE_BOOK" -> Res.string.media_source_picture_book
    else -> Res.string.media_source_other
}

/** BUG-098: AniList `CharacterRole` (MAIN / SUPPORTING / BACKGROUND); null for an unknown value (shown as it comes). */
fun characterRoleLabelRes(role: String): StringResource? = when (role.trim().uppercase()) {
    "MAIN" -> Res.string.character_role_main
    "SUPPORTING" -> Res.string.character_role_supporting
    "BACKGROUND" -> Res.string.character_role_background
    else -> null
}

/**
 * BUG-098: an AniList staff role of a manga ("Story", "Art", "Story & Art", "Original Story"…). AniList adds details in parentheses
 * ("Story (ch 1-20)"): only the part before them is translated, the rest is kept. null = no translation (shown as it comes).
 */
fun staffRoleLabelRes(role: String): StringResource? = when (role.substringBefore('(').trim().lowercase()) {
    "story & art", "story and art" -> Res.string.staff_role_story_art
    "story" -> Res.string.staff_role_story
    "art" -> Res.string.staff_role_art
    "original story" -> Res.string.staff_role_original_story
    "original creator" -> Res.string.staff_role_original_creator
    "character design" -> Res.string.staff_role_character_design
    "illustration" -> Res.string.staff_role_illustration
    "assistant" -> Res.string.staff_role_assistant
    "translator" -> Res.string.staff_role_translator
    "lettering" -> Res.string.staff_role_lettering
    "editing", "editor" -> Res.string.staff_role_editing
    else -> null
}

@androidx.compose.runtime.Composable
fun mediaSourceLabel(source: String): String = org.jetbrains.compose.resources.stringResource(mediaSourceLabelRes(source))

@androidx.compose.runtime.Composable
fun characterRoleLabel(role: String): String =
    characterRoleLabelRes(role)?.let { org.jetbrains.compose.resources.stringResource(it) } ?: role.lowercase().replaceFirstChar(Char::uppercase)

@androidx.compose.runtime.Composable
fun staffRoleLabel(role: String): String {
    val res = staffRoleLabelRes(role) ?: return role
    val rest = role.substring(role.indexOf('(').takeIf { it >= 0 } ?: role.length).trim()
    val base = org.jetbrains.compose.resources.stringResource(res)
    return if (rest.isEmpty()) base else "$base $rest"
}

/** 1.1 (F7): the reason a debrid service gave, as a short phrase ("límite de descargas activas"). */
fun app.nijiyome.model.torrent.DebridProblem.labelRes(): StringResource = when (this) {
    app.nijiyome.model.torrent.DebridProblem.INVALID_KEY -> Res.string.debrid_problem_invalid_key
    app.nijiyome.model.torrent.DebridProblem.ACTIVE_LIMIT -> Res.string.debrid_problem_active_limit
    app.nijiyome.model.torrent.DebridProblem.MONTHLY_LIMIT -> Res.string.debrid_problem_monthly_limit
    app.nijiyome.model.torrent.DebridProblem.PLAN -> Res.string.debrid_problem_plan
    app.nijiyome.model.torrent.DebridProblem.TOO_LARGE -> Res.string.debrid_problem_too_large
    app.nijiyome.model.torrent.DebridProblem.RATE_LIMITED -> Res.string.debrid_problem_rate_limited
    app.nijiyome.model.torrent.DebridProblem.NOT_CACHED -> Res.string.debrid_problem_not_cached
    app.nijiyome.model.torrent.DebridProblem.OFFLINE -> Res.string.debrid_problem_offline
    app.nijiyome.model.torrent.DebridProblem.BAD_LINK -> Res.string.debrid_problem_bad_link
    app.nijiyome.model.torrent.DebridProblem.NO_FILE -> Res.string.debrid_problem_no_file
    app.nijiyome.model.torrent.DebridProblem.SERVICE -> Res.string.debrid_problem_service
}
