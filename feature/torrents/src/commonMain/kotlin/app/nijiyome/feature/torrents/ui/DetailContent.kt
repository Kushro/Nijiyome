// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.torrents.ui

import app.nijiyome.feature.torrents.resources.Res
import app.nijiyome.feature.torrents.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import app.nijiyome.ui.platform.rememberOpenFolder
import app.nijiyome.ui.platform.rememberShareText
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.feature.torrents.vm.TorrentDetailViewModel
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerArgs
import app.nijiyome.model.torrent.TorrentSection
import app.nijiyome.model.torrent.TorrentState
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*
import app.nijiyome.ui.labels.labelRes
import app.nijiyome.ui.state.screenInsets
import kotlinx.coroutines.launch

/** The four per-torrent sections in tab order. */
internal val DETAIL_SECTIONS = listOf(TorrentSection.OVERVIEW, TorrentSection.FILES, TorrentSection.PEERS, TorrentSection.TRACKERS)

/**
 * Detail of one torrent (11 §3.3): fixed header (name, state pill, pause / resume, menu) + PrimaryScrollableTabRow + HorizontalPager
 * with Overview / Files / Peers / Trackers. [showBack] false when embedded next to the library (expanded list-detail).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailContent(
    viewModel: TorrentDetailViewModel,
    initialSection: TorrentSection,
    onBack: () -> Unit,
    onPlay: (PlayerArgs) -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val removed by viewModel.removed.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    MessagesEffect(viewModel.messages, snackbar)
    LaunchedEffect(removed) { if (removed) onBack() }

    val loaded = (stats as? LoadState.Ok)?.value
    val title = loaded?.name ?: item?.series ?: item?.name ?: ""
    var confirmRemove by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val shareText = rememberShareText()
    val openFolder = rememberOpenFolder()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            if (showBack) {
                TopAppBar(
                    // Sparkles (TOR-08): the torrent's real name in the bar, not the generic "Torrents".
                    title = {
                        Text(
                            title.ifBlank { stringResource(Res.string.rd_s8_tor_untitled) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // fixed header
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    loaded?.let { StatePill(it.state) }
                    Spacer(Modifier.weight(1f))
                    if (loaded != null) {
                        if (loaded.state == TorrentState.PAUSED) {
                            IconButton(onClick = viewModel::resume) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.torrent_overview_resume))
                            }
                        } else {
                            IconButton(onClick = viewModel::pause) {
                                Icon(Icons.Outlined.Pause, contentDescription = stringResource(Res.string.torrent_overview_pause))
                            }
                        }
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.torrent_library_actions))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val copyLabel = stringResource(Res.string.torrent_overview_copy_hash)
                            val copied = stringResource(Res.string.torrent_android_hash_copied)
                            DropdownMenuItem(
                                text = { Text(copyLabel) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                onClick = {
                                    menu = false
                                    copyToClipboard(clipboard, viewModel.torrentId)
                                    snackbar.show(copied)
                                },
                            )
                            // Desktop: "sharing" copies the link to the clipboard (rememberShareText), and says so.
                            val shareTitle = stringResource(if (desktopTorrentUi) Res.string.torrent_desktop_copy_magnet else Res.string.torrent_android_share_magnet)
                            DropdownMenuItem(
                                text = { Text(shareTitle) },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menu = false
                                    shareText(magnetOf(viewModel.torrentId), shareTitle)
                                },
                            )
                            val folderPath = item?.path?.takeIf { it.isNotBlank() }
                            if (openFolder != null && folderPath != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.torrent_desktop_open_folder)) },
                                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                    onClick = {
                                        menu = false
                                        openFolder(folderPath)
                                    },
                                )
                            }
                            item?.let { current ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (current.persist) Res.string.torrent_library_unkeep else Res.string.torrent_library_keep)) },
                                    leadingIcon = { Icon(if (current.persist) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                                    onClick = {
                                        menu = false
                                        viewModel.setPersist(!current.persist)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.torrent_overview_remove)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = {
                                    menu = false
                                    confirmRemove = true
                                },
                            )
                        }
                    }
                }
            }

            val initialPage = DETAIL_SECTIONS.indexOf(initialSection).coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = initialPage) { DETAIL_SECTIONS.size }
            val scope = rememberCoroutineScope()
            LaunchedEffect(initialPage) { if (pager.currentPage != initialPage) pager.scrollToPage(initialPage) }
            PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage) {
                DETAIL_SECTIONS.forEachIndexed { index, section ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(stringResource(section.labelRes())) },
                    )
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (DETAIL_SECTIONS[page]) {
                    TorrentSection.OVERVIEW -> OverviewTab(viewModel, item)
                    TorrentSection.FILES -> FilesTab(viewModel, item, onPlay)
                    TorrentSection.PEERS -> PeersTab(viewModel)
                    else -> TrackersTab(viewModel)
                }
            }
        }
    }

    if (confirmRemove) {
        DeleteDialog(
            count = 1,
            onConfirm = { deleteFiles ->
                confirmRemove = false
                viewModel.remove(deleteFiles)
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
