// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.feature.link.profileLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.theme.NijiyomeFonts
import app.nijiyome.model.SkipKind
import app.nijiyome.model.SubtitleStyle
import app.nijiyome.player.FailKind
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.format.formatRate
import app.nijiyome.ui.state.userMessage
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.designsystem.theme.NijiyomeControls

/** "4.2 MB/s · 12 peers · 37 % of the file". */
@Composable
internal fun torrentLine(t: TorrentUi): String =
    stringResource(Res.string.player_torrent_status, formatRate(t.downBps), t.peers, (t.progress * 100).roundToInt().coerceIn(0, 100))

/** Overlay text of the torrent start: "Waiting for torrent metadata…" then speed / peers / %. */
@Composable
internal fun TorrentStatusText(t: TorrentUi, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.onBackground
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (t.waitingMetadata) {
            Text(stringResource(Res.string.player_torrent_metadata), color = on, textAlign = TextAlign.Center)
        }
        Text(torrentLine(t), color = on.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
        t.fileName?.let {
            Text(stringResource(Res.string.player_torrent_file, it), color = on.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Buffering / resolving overlay on top of a running engine (failover, torrent start, stalls). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BufferingOverlay(state: PlayerUiState, modifier: Modifier = Modifier, showIndicator: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    // BUG-106: with the controls up the indicator lives in the play button and this card only carries the text, out of their way.
    if (!showIndicator && !hasBufferingText(state)) return
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh.copy(alpha = 0.85f), contentColor = scheme.onSurface) {
        Column(
            if (showIndicator) Modifier.padding(20.dp) else Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showIndicator) LoadingIndicator(Modifier.size(48.dp))
            val t = state.torrent
            if (t != null) {
                TorrentStatusText(t)
            } else if (state.phase == PlayerPhase.RESOLVING) {
                val name = state.current?.candidate?.let { "${it.extName} · ${it.label}" }
                Text(if (name != null) stringResource(Res.string.player_resolving_from, name) else stringResource(Res.string.player_resolving))
            } else if (!state.remoteless) {
                // BUG-095: a seek past what the PC already made restarts its engine there (up to ~15 s with A4K + LSFG)
                // One line in landscape (the card sits in the strip between the top bar and the transport row there)
                Text(stringResource(Res.string.player_link_preparing), Modifier.widthIn(max = 360.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Whether [BufferingOverlay] has something to say besides the spinner (torrent status, source being resolved, Link preparing). */
internal fun hasBufferingText(state: PlayerUiState): Boolean =
    state.torrent != null || state.phase == PlayerPhase.RESOLVING || !state.remoteless

/**
 * Short confirmation over the video ("Intro skipped", "Trying another source"); disappears by itself. 0.4.0 (worker D): the quality
 * engine's automatic changes and the phone frame generation's failures use it too (one style for every notice), with "Deshacer"
 * ([onUndo]) after an undoable frame-drop step-down.
 */
@Composable
internal fun FlashToast(notice: PlayerNotice, onDone: () -> Unit, modifier: Modifier = Modifier, onUndo: (() -> Unit)? = null) {
    LaunchedEffect(notice) {
        delay(
            when (notice) {
                PlayerNotice.AssSimplified, is PlayerNotice.StoredSourceIgnored, PlayerNotice.PretranslateUnavailable, PlayerNotice.SwitchedToMpv,
                PlayerNotice.SwitchedToMedia3, PlayerNotice.LinkSessionRenewed, PlayerNotice.LinkProfileRetried,
                is PlayerNotice.LinkPhoneCannotPlay, is PlayerNotice.LinkPreferredReplaced, is PlayerNotice.Processing,
                is PlayerNotice.FrameGen, is PlayerNotice.TranslationRateLimited, PlayerNotice.TranslationImageSubtitles,
                is PlayerNotice.DebridFallback -> 5_000
                // long enough to reach "Deshacer"
                is PlayerNotice.Quality -> if (notice.event.undoable) 7_000 else 5_000
                is PlayerNotice.LinkProfile -> if (notice.result is LinkProfileResult.Failed) 5_000 else FLASH_MS
                is PlayerNotice.ScreenshotSaved -> 4_000
                else -> FLASH_MS
            },
        )
        onDone()
    }
    val text = when (notice) {
        PlayerNotice.TryingNext -> stringResource(Res.string.player_fallback_next)
        is PlayerNotice.NoFileNext -> stringResource(Res.string.player_torrent_no_file_next, notice.episode)
        PlayerNotice.IntroSkipped -> stringResource(Res.string.player_intro_skipped)
        PlayerNotice.AssSimplified -> stringResource(Res.string.player_ass_simplified)
        is PlayerNotice.ScreenshotSaved -> stringResource(Res.string.player_screenshot_saved, notice.path)
        PlayerNotice.ScreenshotFailed -> stringResource(Res.string.player_screenshot_failed)
        is PlayerNotice.SubtitleLoaded -> stringResource(Res.string.player_sub_loaded, notice.name)
        PlayerNotice.SubtitleFailed -> stringResource(Res.string.player_sub_load_failed)
        PlayerNotice.SleepFired -> stringResource(Res.string.player_sleep_fired)
        is PlayerNotice.StoredSourceIgnored -> stringResource(Res.string.player_stored_ignored)
        is PlayerNotice.TranslationFailed -> stringResource(Res.string.player_translation_failed, notice.message)
        is PlayerNotice.PretranslateDone -> if (notice.failed > 0) {
            stringResource(Res.string.player_pretranslate_done_failed, notice.failed)
        } else {
            stringResource(Res.string.player_pretranslate_done)
        }
        PlayerNotice.PretranslateUnavailable -> stringResource(Res.string.player_pretranslate_unavailable)
        // 0.4.0 (sub-translation)
        is PlayerNotice.TranslationRateLimited -> stringResource(
            if (notice.google) Res.string.player_translation_rate_limited_google else Res.string.player_translation_rate_limited,
            notice.seconds,
        )
        PlayerNotice.TranslationImageSubtitles -> stringResource(Res.string.player_translation_image_subs)
        PlayerNotice.SwitchedToMpv -> stringResource(Res.string.player_switched_to_mpv)
        PlayerNotice.SwitchedToMedia3 -> stringResource(Res.string.player_switched_to_media3)
        is PlayerNotice.DebridFallback -> stringResource(Res.string.player_debrid_fallback, notice.service, stringResource(notice.problem.labelRes()))
        is PlayerNotice.ExternalUnavailable -> stringResource(Res.string.player_external_unavailable, notice.detail)
        is PlayerNotice.Quality -> qualityEventText(notice.event)
        is PlayerNotice.FrameGen -> stringResource(Res.string.player_fg_notice, frameGenNoticeReason(notice))
        PlayerNotice.LinkSessionRenewed -> stringResource(Res.string.player_link_renewed)
        is PlayerNotice.LinkProfile -> stringResource(linkProfileText(notice.result))
        PlayerNotice.LinkProfileRetried -> stringResource(Res.string.player_link_profile_retried)
        is PlayerNotice.LinkPhoneCannotPlay -> notice.to?.let { stringResource(Res.string.player_link_phone_cannot_play, profileLabel(it)) }
            ?: stringResource(Res.string.player_link_phone_cannot_play_generic)
        is PlayerNotice.LinkPreferredReplaced ->
            stringResource(Res.string.player_link_preferred_replaced, profileLabel(notice.from), profileLabel(notice.to))
        // 0.4.0 (worker C): "Procesar en" switches (ProcessingText.kt)
        is PlayerNotice.Processing -> processingNoticeText(notice)
    }
    val failed = (notice is PlayerNotice.LinkProfile && notice.result is LinkProfileResult.Failed) || notice is PlayerNotice.LinkPhoneCannotPlay ||
        (notice is PlayerNotice.Processing && (notice.failure != null || notice.block != null))
    val warning = notice is PlayerNotice.FrameGen || notice is PlayerNotice.ExternalUnavailable || notice is PlayerNotice.TranslationRateLimited || notice is PlayerNotice.TranslationImageSubtitles ||
        (notice is PlayerNotice.Quality && notice.event.kind != app.nijiyome.player.QualityEventKind.RESTORED)
    val undo = onUndo?.takeIf { notice is PlayerNotice.Quality && notice.event.undoable }
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.widthIn(max = 560.dp).padding(horizontal = 16.dp), shape = RoundedCornerShape(50), color = scheme.inverseSurface, contentColor = scheme.inverseOnSurface) {
        Row(
            Modifier.padding(start = 16.dp, end = if (undo != null) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
            Arrangement.spacedBy(8.dp),
            Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    failed -> Icons.Rounded.ErrorOutline
                    warning -> Icons.Rounded.WarningAmber
                    else -> Icons.Rounded.Check
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
            if (undo != null) {
                TextButton(onClick = undo, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.quality_undo), color = scheme.inversePrimary) }
            }
        }
    }
}

/** The notice text of a quality engine event: the reason of a step-down / suspension, "Volvió la calidad elegida" on a restore. */
@Composable
internal fun qualityEventText(e: app.nijiyome.player.QualityEvent): String = when (e.kind) {
    app.nijiyome.player.QualityEventKind.RESTORED -> stringResource(Res.string.quality_event_restored)
    app.nijiyome.player.QualityEventKind.PRECISION_FALLBACK -> stringResource(Res.string.quality_event_precision)
    else -> stringResource(e.reason.labelRes)
}

/** BUG-131: the notice of each way a Link profile change ends. */
internal fun linkProfileText(result: LinkProfileResult): org.jetbrains.compose.resources.StringResource = when (result) {
    is LinkProfileResult.Changed -> Res.string.player_link_profile_changed
    LinkProfileResult.SameProfile -> Res.string.player_link_profile_same
    LinkProfileResult.Busy -> Res.string.player_link_profile_busy
    LinkProfileResult.NoSession -> Res.string.player_link_profile_failed_no_session
    is LinkProfileResult.Failed -> when (result.reason) {
        LinkProfileFailure.TIMEOUT -> Res.string.player_link_profile_failed_timeout
        LinkProfileFailure.OFFLINE -> Res.string.player_link_profile_failed_offline
        LinkProfileFailure.SESSION_ENDED -> Res.string.player_link_profile_failed_ended
        LinkProfileFailure.PC_ERROR -> Res.string.player_link_profile_failed
    }
}

/** "Skip intro / recap / ending" while the position is inside a mark. */
@Composable
internal fun SkipButton(kind: SkipKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (kind) {
        SkipKind.INTRO -> Res.string.player_jump_intro
        SkipKind.OUTRO -> Res.string.player_jump_outro
        SkipKind.RECAP -> Res.string.player_jump_recap
    }
    FilledTonalButton(onClick = onClick, modifier = modifier, shape = NijiyomeControls.shape) {
        Icon(Icons.Rounded.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(label), Modifier.padding(start = 8.dp))
    }
}

/**
 * Next-episode card with the countdown ring (prototype `player.js` `ended()`): `autoNextCountdownSecs` long; without auto-next
 * ([NextEpisodeUi.secondsLeft] null) only the button, nothing counts. 0.4.1: the full end screen ([EpisodeEndScreen]) shows the same
 * countdown; this compact card remains for when the user hides that screen to look at the last frame.
 */
@Composable
internal fun NextEpisodeCard(next: NextEpisodeUi, onPlayNow: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh, contentColor = scheme.onSurface) {
        Row(Modifier.padding(12.dp), Arrangement.spacedBy(12.dp), Alignment.CenterVertically) {
            val left = next.secondsLeft
            if (left != null) CountdownRing(left, next.totalSecs)
            Column(Modifier.widthIn(max = 220.dp)) {
                Text(stringResource(Res.string.player_next), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.player_next_episode_n, next.episode), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (next.sameTorrent) {
                    Text(stringResource(Res.string.player_next_same_torrent), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                }
            }
            IconButton(onClick = onPlayNow) { Icon(Icons.Rounded.SkipNext, stringResource(Res.string.player_play_now)) }
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, stringResource(Res.string.player_cancel)) }
        }
    }
}

/** Mobile data confirmation before a torrent (11 §4.1 step 2). */
@Composable
internal fun MeteredDialog(prompt: MeteredPrompt, onWatch: () -> Unit, onWeb: () -> Unit, onSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onWeb,
        icon = { Icon(Icons.Rounded.CellTower, contentDescription = null) },
        title = { Text(stringResource(Res.string.player_metered_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    prompt.sizeBytes?.let { stringResource(Res.string.player_metered_body, formatBytes(it)) }
                        ?: stringResource(Res.string.player_metered_body_unknown),
                )
                TextButton(onClick = onSettings, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_metered_settings)) }
            }
        },
        confirmButton = { Button(onClick = onWatch, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_metered_watch)) } },
        dismissButton = {
            TextButton(onClick = onWeb, shape = NijiyomeControls.shape) {
                Text(stringResource(if (prompt.hasWeb) Res.string.player_metered_web else Res.string.player_cancel))
            }
        },
    )
}

/** Final ErrorState of the source flow (or of a direct source) with `failed[]` and the ways out. */
@Composable
internal fun PlaybackErrorView(
    state: PlayerUiState,
    onRetry: () -> Unit,
    onUseWeb: () -> Unit,
    onPick: () -> Unit,
    onOpenTorrent: (String) -> Unit,
    onOpenExtensions: () -> Unit,
    /** 0.4.1: a Link error with the original source on the phone ([PlayerUiState.linkPhoneFallback]). */
    onPlayOnPhone: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val noFile = state.noFileTorrentId
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(56.dp))
        val remote = state.remoteFailure
        Text(
            stringResource(if (remote == RemoteFailure.CONNECTION_LOST) Res.string.player_link_lost_title else Res.string.player_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onBackground,
        )
        val cause = when {
            // BUG-026 / BUG-038: a Link session explains itself in words (no Media3 code)
            remote == RemoteFailure.CONNECTION_LOST -> stringResource(Res.string.player_link_lost)
            remote == RemoteFailure.SESSION_ENDED -> stringResource(Res.string.player_link_ended)
            remote == RemoteFailure.PC_ERROR -> stringResource(Res.string.player_link_pc_error)
            // BUG-132: the phone's decoder refused the stream: not the PC's fault
            remote == RemoteFailure.PHONE_DECODER -> stringResource(Res.string.player_link_phone_decoder)
            !state.remoteless -> stringResource(Res.string.player_link_pc_error)
            noFile != null -> stringResource(Res.string.player_torrent_no_file_cause)
            state.failed.isEmpty() && state.error != null -> state.error.userMessage()
            // BUG-020: never "no other source" while the list still has sources to try.
            state.remaining > 0 -> pluralStringResource(Res.plurals.player_error_remaining, state.remaining, state.remaining)
            else -> stringResource(Res.string.player_error_no_more)
        }.let { base ->
            if (state.failed.lastOrNull()?.let { it.kind == FailKind.PLAYBACK && it.detail.contains("DECOD") } == true) {
                stringResource(Res.string.player_error_decoder) + " " + base
            } else {
                base
            }
        }
        Text(cause, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (state.failed.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainer, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.player_failed_list), style = MaterialTheme.typography.labelLarge)
                    state.failed.forEach { f ->
                        Text(
                            stringResource(Res.string.player_failed_entry, f.name, failReason(f.kind, f.detail)),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry, shape = NijiyomeControls.shape) { Text(stringResource(UiR.string.ui_retry)) }
            if (state.canPickSource) OutlinedButton(onClick = onPick, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_pick_source)) }
        }
        if (onPlayOnPhone != null && state.linkPhoneFallback && !state.remoteless) {
            FilledTonalButton(onClick = onPlayOnPhone, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_link_play_on_phone)) }
        }
        if (state.lastFailedTorrent && state.hasWeb) {
            FilledTonalButton(onClick = onUseWeb, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_use_web)) }
        }
        if (noFile != null) {
            FilledTonalButton(onClick = { onOpenTorrent(noFile) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_torrent_pick_file)) }
        }
        // Extensions play no part in a Link session (BUG-026)
        if (state.remoteless) TextButton(onClick = onOpenExtensions, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_go_extensions)) }
    }
}

@Composable
private fun failReason(kind: FailKind, detail: String): String = when (kind) {
    FailKind.NO_PEERS -> stringResource(Res.string.player_fail_no_peers)
    FailKind.NO_FILE -> stringResource(Res.string.player_fail_no_file)
    FailKind.METERED_DECLINED -> stringResource(Res.string.player_fail_metered)
    FailKind.RESOLVE -> stringResource(Res.string.player_fail_resolve)
    FailKind.PLAYBACK, FailKind.UNKNOWN -> detail.ifBlank { stringResource(Res.string.player_fail_resolve) }
}

internal const val FLASH_MS = 2_600L
