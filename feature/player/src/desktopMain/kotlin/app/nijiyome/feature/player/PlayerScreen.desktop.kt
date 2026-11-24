// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.player_back
import app.nijiyome.feature.player.resources.player_desktop_unavailable
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.SettingsSection
import app.nijiyome.player.MPV_SESSION_FACTORY
import app.nijiyome.player.PlayerSessionFactory
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.core.qualifier.named

/**
 * Milestone 4: the desktop player, the shared [PlayerScreenContent] over the libmpv view model ([desktopPlayerViewModel]), held in the
 * entry's view model store like on Android (it survives recomposition and is cleared when the screen leaves). Without a libmpv
 * factory in the graph it shows "not available yet" instead (the view model cannot be built without one).
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
    val hasMpv = remember(koin) { koin.getOrNull<PlayerSessionFactory>(named(MPV_SESSION_FACTORY)) != null }
    if (!hasMpv) {
        PlayerUnavailable(onBack)
        return
    }
    val vm: PlayerViewModel = viewModel(
        key = playerViewModelKey(args),
        factory = viewModelFactory { initializer { koin.desktopPlayerViewModel(args) } },
    )
    // v1.1 (F2): the system's media controls (MPRIS on Linux)
    app.nijiyome.feature.player.mpris.DesktopMediaControls(vm, args)
    // Nijiyome Link: paired phones see and drive this player, and can be sent the episode (desktop only)
    DesktopLinkRemote(vm, args) {
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
}

/** Shown only if no libmpv factory (MPV_SESSION_FACTORY) is registered, which desktopApp always does (DesktopModules). */
@Composable
private fun PlayerUnavailable(onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Rounded.OndemandVideo,
                title = stringResource(Res.string.player_desktop_unavailable),
                actionLabel = stringResource(Res.string.player_back),
                onAction = onBack,
            )
        }
    }
}
