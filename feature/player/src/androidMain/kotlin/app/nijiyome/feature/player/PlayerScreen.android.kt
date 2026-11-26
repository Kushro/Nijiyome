// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.stringResource
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.nijiyome.model.EmbedSniffer
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SecretStore
import app.nijiyome.model.torrent.DebridRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.ExternalPlayer
import app.nijiyome.player.HistoryFlush
import app.nijiyome.player.MPV_SESSION_FACTORY
import app.nijiyome.player.PlaybackControllerFactory
import app.nijiyome.player.PlayerSessionFactory
import app.nijiyome.player.SubtitleTools
import org.koin.compose.getKoin
import org.koin.core.qualifier.named
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * Android player (see the common [PlayerScreen]). The host Activity needs `android:supportsPictureInPicture="true"` and
 * `configChanges` for orientation|screenSize for PiP to work. Drawn in a dark AMOLED variant of the app palette (video chrome).
 * Milestone 4: the view model (Media3 / libmpv / external app) is built here; the screen itself is the shared [PlayerScreenContent].
 */
@Composable
actual fun PlayerScreen(
    args: PlayerArgs,
    onBack: () -> Unit,
    onReplace: (PlayerArgs) -> Unit,
    onOpenTorrent: (torrentId: String) -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenDetails: (anilistId: Int) -> Unit,
) {
    val koin = getKoin()
    val vm: PlayerViewModel = viewModel(
        key = playerViewModelKey(args),
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    args = args,
                    metadata = koin.get(),
                    extensions = koin.get(),
                    history = koin.get(),
                    link = koin.get(),
                    remote = koin.get(),
                    media3 = koin.get<PlaybackControllerFactory>(),
                    flush = koin.getOrNull<HistoryFlush>(),
                    settingsRepository = koin.getOrNull(),
                    torrents = koin.getOrNull(),
                    stats = koin.getOrNull(),
                    skipMarks = koin.getOrNull(),
                    mpvFactory = koin.getOrNull<PlayerSessionFactory>(named(MPV_SESSION_FACTORY)),
                    subtitleTools = koin.getOrNull<SubtitleTools>(),
                    secrets = koin.getOrNull<SecretStore>(),
                    debrid = koin.getOrNull<DebridRepository>(),
                    embedSniffer = koin.getOrNull<EmbedSniffer>(),
                    // 0.4.0 (worker C): bound by the coordinator to worker A's quality API / worker B's FrameGenController
                    enhancementGate = koin.getOrNull<app.nijiyome.model.LocalEnhancementGate>() ?: app.nijiyome.model.LocalEnhancementGate.NONE,
                    frameGenGate = koin.getOrNull<app.nijiyome.model.PhoneFrameGenGate>() ?: app.nijiyome.model.PhoneFrameGenGate.NONE,
                    externalPlayer = ExternalPlayer,
                )
            }
        },
    )
    PlayerScreenContent(
        vm = vm,
        args = args,
        onBack = onBack,
        onReplace = onReplace,
        onOpenTorrent = onOpenTorrent,
        onOpenSettings = onOpenSettings,
        onOpenExtensions = onOpenExtensions,
        onOpenLinked = onOpenLinked,
        onOpenDetails = onOpenDetails,
    )
}

/** The EXTERNAL engine: launches the other app once, then shows the informative screen until Stop / Back. */
@Composable
internal actual fun ExternalBranch(
    vm: PlayerViewModel,
    state: PlayerUiState,
    title: String,
    onBack: () -> Unit,
    onOpenSettings: (SettingsSection) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val external = state.external ?: return
    val context = LocalContext.current
    // Like desktop external mpv: when the other app closes, record the progress it reports and go back (09 §4.4).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onExternalResult(ExternalPlayer.resultPositionMs(result.data), ExternalPlayer.resultDurationMs(result.data))
        onBack()
    }
    val chooserTitle = stringResource(Res.string.player_external_chooser)
    fun open() {
        // BUG-046: with no app able to play it, say so here instead of "Playing in an external player" + the system's empty chooser.
        val ok = ExternalPlayer.canLaunch(context, external.launch) && try {
            launcher.launch(ExternalPlayer.intent(external.launch, chooserTitle))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
        vm.onExternalLaunched(ok)
    }
    LaunchedEffect(external.launch) { if (!external.launched) open() }
    val appLabel = remember(external.launch.packageName) { ExternalPlayer.appLabel(context, external.launch.packageName) }
    if (external.failed) {
        Centered {
            Text(stringResource(Res.string.player_external_no_app_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                external.launch.packageName?.let { pkg -> stringResource(Res.string.player_external_app_missing, appLabelFor(pkg)) }
                    ?: stringResource(Res.string.player_external_no_app),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = vm::useBuiltInPlayer, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_external_use_builtin)) }
            OutlinedButton(onClick = { onOpenSettings(SettingsSection.PLAYER) }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_external_open_settings)) }
        }
        return
    }
    ExternalPlayerContent(
        title = title,
        appLabel = appLabel,
        onStop = onBack,
        onBack = onBack,
        torrentLine = state.torrent?.let { torrentLine(it) },
        onReopen = { open() },
    )
}

/** Display name of a known external player package (it is not installed, so the system cannot name it). */
private fun appLabelFor(pkg: String): String = when (pkg) {
    ExternalPlayer.PACKAGE_MPV -> "mpv-android"
    ExternalPlayer.PACKAGE_VLC -> "VLC"
    else -> pkg
}
