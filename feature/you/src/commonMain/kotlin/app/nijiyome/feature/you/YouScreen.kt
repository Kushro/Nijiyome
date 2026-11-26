// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.you

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.SkeletonBox
import app.nijiyome.designsystem.components.rememberAfterDelay
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.theme.LocalNijiyomeAmoled
import app.nijiyome.feature.you.resources.Res
import app.nijiyome.feature.you.resources.*
import app.nijiyome.model.ListProvider
import app.nijiyome.model.LoadState
import app.nijiyome.ui.format.relativeTime
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.state.screenInsets
import app.nijiyome.ui.state.userMessage
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.nav_manga
import app.nijiyome.ui.resources.nav_novels
import app.nijiyome.ui.resources.ui_for_you_summary
import app.nijiyome.ui.resources.ui_nav_for_you
import app.nijiyome.ui.resources.ui_retry
import app.nijiyome.designsystem.theme.NijiyomeControls

// android wave contract (docs/redesign/12 §3.13). Name and signature FIXED; new parameters only with defaults.

/**
 * "Tú" (Sparkles screen `perfil`, Android hub): the account card with "Sincronizar ahora" and "Listas y cuentas", then three titled
 * groups whose rows say their state: Leer y descubrir (Manga, Novelas, Para ti), Tu actividad (Perfil de AniList, Notificaciones,
 * Historial) and Equipo y fuentes (Transferencias with the active-downloads badge, Fuentes, Link, Ajustes).
 * Transferencias replaces the old Descargas and Torrents rows: it calls [onOpenDownloads] (Route.Downloads); [onOpenTorrents] is
 * kept for the shell's signature and no longer drawn.
 */
@Composable
fun YouScreen(
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Listas y cuentas" -> Route.SettingsSection(ACCOUNTS). */
    onOpenAccounts: () -> Unit = {},
    /** Row "Manga" -> Route.Manga(). */
    onOpenManga: () -> Unit = {},
    /** Row "Novelas" -> Route.Novels(). */
    onOpenNovels: () -> Unit = {},
    /** Row "Perfil de AniList" -> Route.Profile(). */
    onOpenProfile: () -> Unit = {},
    /** Row "Notificaciones" -> Route.Notifications. */
    onOpenNotifications: () -> Unit = {},
    /** Row "Para ti" -> Route.ForYou (Para ti is a tab of Explorar; one tap from here). */
    onOpenForYou: (() -> Unit)? = null,
    /** Sparkles: Torrents live in Transferencias now; kept so the shell's call compiles, not drawn. */
    @Suppress("UNUSED_PARAMETER") onOpenTorrents: (() -> Unit)? = null,
) {
    val viewModel: YouViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val currentSnackbar by rememberUpdatedState(snackbar)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                YouEvent.Synced -> currentSnackbar.show(getString(Res.string.rd_s7_you_synced))
                is YouEvent.SyncFailed -> currentSnackbar.show(
                    getString(Res.string.rd_s7_you_sync_failed),
                    actionLabel = getString(UiR.string.ui_retry),
                    onAction = viewModel::syncNow,
                )
            }
        }
    }
    YouContent(
        state = state,
        onRetry = viewModel::retry,
        onSyncNow = viewModel::syncNow,
        onOpenHistory = onOpenHistory,
        onOpenDownloads = onOpenDownloads,
        onOpenExtensions = onOpenExtensions,
        onOpenLinked = onOpenLinked,
        onOpenSettings = onOpenSettings,
        onOpenAccounts = onOpenAccounts,
        onOpenManga = onOpenManga,
        onOpenNovels = onOpenNovels,
        onOpenProfile = onOpenProfile,
        onOpenNotifications = onOpenNotifications,
        onOpenForYou = onOpenForYou,
        modifier = modifier,
    )
}

@Composable
internal fun YouContent(
    state: YouUiState,
    onRetry: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenManga: () -> Unit = {},
    onOpenNovels: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenForYou: (() -> Unit)? = null,
) {
    Scaffold(modifier = modifier, contentWindowInsets = screenInsets) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            when (val overview = state.overview) {
                LoadState.Loading -> if (rememberAfterDelay(150)) YouSkeleton()
                is LoadState.Err -> ErrorState(
                    message = overview.cause.userMessage(),
                    retryLabel = stringResource(UiR.string.ui_retry),
                    onRetry = onRetry,
                    title = stringResource(Res.string.you_load_error),
                    modifier = Modifier.fillMaxSize(),
                )
                is LoadState.Ok -> {
                    val o = overview.value
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item(key = "title", contentType = "title") {
                            Text(
                                stringResource(Res.string.you_title),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        item(key = "account", contentType = "account") {
                            AccountCard(o, syncing = state.syncing, onSyncNow = onSyncNow, onOpenAccounts = onOpenAccounts)
                        }
                        item(key = "read", contentType = "group") {
                            YouGroup(stringResource(Res.string.rd_s7_you_group_read)) {
                                YouRow(Icons.Outlined.MenuBook, stringResource(UiR.string.nav_manga), stringResource(Res.string.you_manga_summary), onOpenManga)
                                YouRow(Icons.Outlined.AutoStories, stringResource(UiR.string.nav_novels), stringResource(Res.string.you_novels_summary), onOpenNovels)
                                if (onOpenForYou != null) {
                                    YouRow(Icons.Outlined.AutoAwesome, stringResource(UiR.string.ui_nav_for_you), stringResource(UiR.string.ui_for_you_summary), onOpenForYou)
                                }
                            }
                        }
                        item(key = "activity", contentType = "group") {
                            YouGroup(stringResource(Res.string.rd_s7_you_group_activity)) {
                                YouRow(Icons.Outlined.AccountCircle, stringResource(Res.string.you_profile), stringResource(Res.string.rd_s7_you_profile_summary), onOpenProfile)
                                YouRow(
                                    Icons.Outlined.Notifications,
                                    stringResource(Res.string.you_notifications),
                                    stringResource(Res.string.you_notifications_summary),
                                    onOpenNotifications,
                                    badge = o.unreadNotifications.takeIf { it > 0 },
                                    badgeDescription = stringResource(Res.string.rd_s7_you_unread, o.unreadNotifications),
                                )
                                YouRow(
                                    Icons.Outlined.History,
                                    stringResource(Res.string.you_history),
                                    if (o.historyCount == 0) {
                                        stringResource(Res.string.you_history_empty)
                                    } else {
                                        pluralStringResource(Res.plurals.you_history_count, o.historyCount, o.historyCount)
                                    },
                                    onOpenHistory,
                                )
                            }
                        }
                        item(key = "device", contentType = "group") {
                            YouGroup(stringResource(Res.string.rd_s7_you_group_device)) {
                                YouRow(
                                    Icons.Outlined.SwapVert,
                                    stringResource(Res.string.rd_s7_you_transfers),
                                    when {
                                        o.activeDownloads > 0 -> pluralStringResource(Res.plurals.you_downloads_active, o.activeDownloads, o.activeDownloads)
                                        o.torrentCount > 0 -> pluralStringResource(Res.plurals.you_downloads_total, o.torrentCount, o.torrentCount)
                                        else -> stringResource(Res.string.you_downloads_none)
                                    },
                                    onOpenDownloads,
                                    badge = o.activeDownloads.takeIf { it > 0 },
                                    badgeDescription = stringResource(Res.string.rd_s7_you_active, o.activeDownloads),
                                )
                                YouRow(
                                    Icons.Outlined.Extension,
                                    stringResource(Res.string.rd_s7_you_sources),
                                    if (o.extensionsCount == 0) {
                                        stringResource(Res.string.you_extensions_none)
                                    } else {
                                        pluralStringResource(Res.plurals.you_extensions_count, o.extensionsCount, o.extensionsCount)
                                    },
                                    onOpenExtensions,
                                )
                                if (o.linkClient) YouRow(
                                    Icons.Outlined.Devices,
                                    stringResource(Res.string.rd_s7_you_link),
                                    when (val linked = o.linked) {
                                        LinkedSummary.None -> stringResource(Res.string.you_linked_none)
                                        LinkedSummary.Problem -> stringResource(Res.string.you_linked_problem)
                                        is LinkedSummary.Paired -> stringResource(
                                            if (linked.reachable) Res.string.you_linked_connected else Res.string.you_linked_unreachable,
                                            linked.name,
                                        )
                                    },
                                    onOpenLinked,
                                )
                                YouRow(Icons.Outlined.Settings, stringResource(Res.string.you_settings), stringResource(Res.string.you_settings_summary), onOpenSettings)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(overview: YouOverview, syncing: Boolean, onSyncNow: () -> Unit, onOpenAccounts: () -> Unit) {
    // No own login: the identity is the linked AniList / MAL account(s); the history syncs with the paired PC.
    val primary = overview.accounts.firstOrNull { it.provider == ListProvider.ANILIST } ?: overview.accounts.firstOrNull()
    val amoled = LocalNijiyomeAmoled.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(primary?.username, primary?.avatar)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        primary?.username ?: stringResource(Res.string.you_guest_name),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val providers = if (overview.accounts.isEmpty()) {
                        stringResource(Res.string.you_guest)
                    } else {
                        val anilist = stringResource(ListProvider.ANILIST.labelRes())
                        val mal = stringResource(ListProvider.MAL.labelRes())
                        overview.accounts.joinToString(" · ") { if (it.provider == ListProvider.ANILIST) anilist else mal }
                    }
                    val linked = overview.linked
                    val historyLine = when {
                        linked is LinkedSummary.Paired && overview.lastSyncedAt != null ->
                            stringResource(Res.string.you_history_link, linked.name, relativeTime(overview.lastSyncedAt))
                        linked is LinkedSummary.Paired -> stringResource(Res.string.you_history_link_never, linked.name)
                        else -> stringResource(Res.string.you_history_local)
                    }
                    Text(providers, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(historyLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onSyncNow,
                    enabled = !syncing,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { if (syncing) liveRegion = LiveRegionMode.Polite },
                    shape = NijiyomeControls.shape,
                ) {
                    if (syncing) {
                        LoadingIndicator(Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(stringResource(if (syncing) Res.string.you_syncing else Res.string.you_sync_now), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onOpenAccounts, modifier = Modifier.heightIn(min = 48.dp), shape = NijiyomeControls.shape) {
                    Icon(Icons.Outlined.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (overview.accounts.isEmpty()) Res.string.you_sign_in else Res.string.you_accounts),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(username: String?, url: String?) {
    if (url != null) {
        MediaCover(url = url, modifier = Modifier.width(56.dp), shape = CircleShape, aspectRatio = 1f)
        return
    }
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initial = username?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (initial != null) {
            Text(initial.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** A titled group of rows (artifact `.nj-b-hubgroup`). */
@Composable
private fun YouGroup(title: String, rows: @Composable () -> Unit) {
    val amoled = LocalNijiyomeAmoled.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, modifier = Modifier.padding(horizontal = 4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { rows() }
        }
    }
}

@Composable
private fun YouRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: Int? = null,
    badgeDescription: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { if (badge != null && badgeDescription != null) contentDescription = "$title, $subtitle, $badgeDescription" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) Badge { Text(badge.toString()) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YouSkeleton() {
    Column(
        Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBox(Modifier.fillMaxWidth(0.3f).height(32.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(140.dp), shape = MaterialTheme.shapes.extraLarge)
        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(180.dp), shape = MaterialTheme.shapes.large) }
    }
}
