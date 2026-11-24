@file:OptIn(ExperimentalMaterial3Api::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.feature.downloads.DownloadsSettingsSection
import app.nijiyome.feature.manga.ReaderSettingsSection
import app.nijiyome.feature.novel.NovelReaderSettingsSection
import app.nijiyome.feature.torrents.TorrentSettingsPage
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.SettingsSection
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.state.screenInsets
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/**
 * Settings as ONE sectioned screen (prototype `S.settings`, 12 §4 b.4): sticky side index on expanded windows, a LazyColumn with a
 * quick-jump chip row on compact ones. [initialSection] scrolls to its section; [SettingsSection.TORRENTS] opens the Torrents subpage,
 * whose body is worker f's [TorrentSettingsPage]. Every field saves immediately; switches and player choices confirm with the global
 * snackbar ("Settings saved").
 *
 * [onOpenLink] (pre-wave) and [onOpenLinked] both lead to the Linked device screen (Route.Link and Route.Linked render the same
 * screen); the Enlace section button calls [onOpenLinked]. [onOpenLink] is kept for source compatibility and is not called.
 */
@Composable
fun SettingsScreen(
    onOpenExtensions: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    // --- android wave contract (docs/redesign/12 §3.14): new callbacks appended, wired by :app (worker b) ---
    /** Scroll to this section on open; SettingsSection.TORRENTS opens the Torrents subpage (TorrentSettingsPage of worker f). */
    initialSection: SettingsSection? = null,
    /** "Dispositivo enlazado" entry of the Enlace section -> Route.Linked. */
    onOpenLinked: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    /** "Debrid (TorBox y otros)" entry of the Torrents section -> Route.Torrents(TorrentSection.DEBRID). */
    onOpenDebrid: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var torrentsOpen by rememberSaveable { mutableStateOf(initialSection == SettingsSection.TORRENTS) }

    val snackbar = LocalAppSnackbar.current
    val restartAfterReset = rememberRestartAfterReset()
    val currentSnackbar by rememberUpdatedState(snackbar)
    LaunchedEffect(viewModel) {
        viewModel.refreshStorage()
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.Saved -> org.jetbrains.compose.resources.getString(UiR.string.ui_settings_saved)
                SettingsEvent.Synced -> org.jetbrains.compose.resources.getString(Res.string.settings_synced)
                is SettingsEvent.CacheCleared -> org.jetbrains.compose.resources.getString(Res.string.settings_cache_cleared, formatBytes(event.freedBytes))
                is SettingsEvent.Failed -> org.jetbrains.compose.resources.getString(Res.string.settings_action_failed)
                // parity wave A (worker q, system.reset-app): recreate the activity; the shell shows the first-run wizard again
                SettingsEvent.ResetDone -> {
                    restartAfterReset()
                    null
                }
            }
            if (message != null) currentSnackbar.show(message)
        }
    }

    if (torrentsOpen) {
        // Opened by a deep link (Route.SettingsSection(TORRENTS)): Back leaves the route; opened from the list: Back returns to it.
        val closeTorrents: () -> Unit = {
            if (initialSection == SettingsSection.TORRENTS && onBack != null) onBack() else torrentsOpen = false
        }
        PlatformBackHandler(onBack = closeTorrents)
        TorrentSettingsPage(modifier = modifier, onBack = closeTorrents)
        return
    }

    SettingsContent(
        state = state,
        viewModel = viewModel,
        initialSection = initialSection,
        onOpenExtensions = onOpenExtensions,
        onOpenLinked = onOpenLinked,
        onOpenTorrents = { torrentsOpen = true },
        onOpenDebrid = onOpenDebrid,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Section order of the prototype (+ Torrents before Data, 11 §3.5). */
internal val SETTINGS_SECTIONS: List<SettingsSection> = listOf(
    SettingsSection.APPEARANCE,
    SettingsSection.PLAYER,
    SettingsSection.METADATA,
    // parity wave A (15 §3.3, worker q): AniList / MAL accounts, the app's only identity (2026-11-09: no own login).
    SettingsSection.ACCOUNTS,
    SettingsSection.LINK,
    SettingsSection.TORRENTS,
    // parity wave B (16 §3.9): web downloads (r) and the readers (s), between Torrents and Data.
    SettingsSection.DOWNLOADS,
    SettingsSection.READER,
    SettingsSection.DATA,
    SettingsSection.ABOUT,
)

/**
 * The sections this build shows: Listas y cuentas only when a [ListsRepository] is bound (always on Android; desktop has none yet).
 */
internal fun settingsSections(hasLists: Boolean): List<SettingsSection> =
    if (hasLists) SETTINGS_SECTIONS else SETTINGS_SECTIONS - SettingsSection.ACCOUNTS

internal fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.APPEARANCE -> Icons.Outlined.Palette
    SettingsSection.PLAYER -> Icons.Outlined.PlayCircle
    SettingsSection.METADATA -> Icons.Outlined.Sync
    SettingsSection.ACCOUNTS -> Icons.Outlined.Bookmarks
    SettingsSection.LINK -> Icons.Outlined.Link
    SettingsSection.TORRENTS -> Icons.Outlined.Download
    SettingsSection.DOWNLOADS -> Icons.Outlined.CloudDownload
    SettingsSection.READER -> Icons.Outlined.MenuBook
    SettingsSection.DATA -> Icons.Outlined.Storage
    SettingsSection.ABOUT -> Icons.Outlined.Info
}

@Composable
internal fun SettingsSection.title(): String = when (this) {
    SettingsSection.APPEARANCE -> stringResource(Res.string.settings_appearance)
    SettingsSection.PLAYER -> stringResource(Res.string.settings_player)
    SettingsSection.METADATA -> stringResource(Res.string.settings_metadata)
    SettingsSection.ACCOUNTS -> stringResource(Res.string.settings_accounts)
    SettingsSection.LINK -> stringResource(Res.string.settings_link)
    SettingsSection.TORRENTS -> stringResource(UiR.string.nav_torrents)
    SettingsSection.DOWNLOADS -> stringResource(UiR.string.nav_downloads)
    SettingsSection.READER -> stringResource(Res.string.settings_reader)
    SettingsSection.DATA -> stringResource(Res.string.settings_data)
    SettingsSection.ABOUT -> stringResource(Res.string.settings_about)
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    initialSection: SettingsSection?,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenTorrents: () -> Unit,
    onOpenDebrid: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val sections = remember(koin) { settingsSections(hasLists = koin.getOrNull<ListsRepository>() != null) }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = screenInsets,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                    },
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            val expanded = maxWidth >= EXPANDED_MIN_WIDTH
            val listState = rememberLazyListState()
            val reduceMotion = LocalReduceMotion.current
            val scope = rememberCoroutineScope()
            // Items before the first section: the title (no top bar) and, on compact windows, the quick-jump chips.
            val headerCount = (if (onBack == null) 1 else 0) + (if (expanded) 0 else 1)
            val current by remember(headerCount, listState) {
                derivedStateOf { sections.getOrNull(listState.firstVisibleItemIndex - headerCount) ?: sections.first() }
            }
            val jumpTo: (SettingsSection) -> Unit = { section ->
                val index = headerCount + sections.indexOf(section)
                scope.launch { if (reduceMotion) listState.scrollToItem(index) else listState.animateScrollToItem(index) }
            }
            LaunchedEffect(initialSection, headerCount) {
                if (initialSection != null && initialSection in sections) listState.scrollToItem(headerCount + sections.indexOf(initialSection))
            }

            Row(Modifier.fillMaxSize()) {
                if (expanded) {
                    SettingsIndex(sections = sections, current = current, onJump = jumpTo)
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    SettingsList(
                        state = state,
                        viewModel = viewModel,
                        sections = sections,
                        listState = listState,
                        showTitle = onBack == null,
                        showChips = !expanded,
                        current = current,
                        onJump = jumpTo,
                        onOpenExtensions = onOpenExtensions,
                        onOpenLinked = onOpenLinked,
                        onOpenTorrents = onOpenTorrents,
                        onOpenDebrid = onOpenDebrid,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIndex(sections: List<SettingsSection>, current: SettingsSection, onJump: (SettingsSection) -> Unit) {
    Column(
        Modifier
            .width(240.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEach { section ->
            NavigationDrawerItem(
                label = { Text(section.title()) },
                selected = section == current,
                onClick = { onJump(section) },
                icon = { Icon(section.icon(), contentDescription = null) },
            )
        }
    }
}

@Composable
private fun SettingsList(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    sections: List<SettingsSection>,
    listState: LazyListState,
    showTitle: Boolean,
    showChips: Boolean,
    current: SettingsSection,
    onJump: (SettingsSection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenTorrents: () -> Unit,
    onOpenDebrid: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showTitle) {
            item(key = "title", contentType = "title") {
                Text(
                    stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        if (showChips) {
            item(key = "chips", contentType = "chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sections, key = { it.id }) { section ->
                        FilterChip(
                            selected = section == current,
                            onClick = { onJump(section) },
                            label = { Text(section.title()) },
                            leadingIcon = { Icon(section.icon(), contentDescription = null) },
                        )
                    }
                }
            }
        }
        items(sections, key = { it.id }, contentType = { "section" }) { section ->
            SectionPanel(section) {
                when (section) {
                    SettingsSection.APPEARANCE -> AppearanceSection(state, viewModel)
                    SettingsSection.PLAYER -> {
                        PlayerSection(state, viewModel, onOpenExtensions)
                        FrameGenSettingsSlot() // 0.4.0 (worker B): phone frame generation, its own file (Android only)
                    }
                    SettingsSection.METADATA -> MetadataSection(state, viewModel)
                    SettingsSection.ACCOUNTS -> AccountsSection(state, viewModel)
                    SettingsSection.LINK -> LinkSectionForPlatform(state, viewModel, onOpenLinked)
                    SettingsSection.TORRENTS -> TorrentsSection(onOpenTorrents, onOpenDebrid)
                    // parity wave B (16 §3.9): r / s own the rows; the contract only mounts them (same shape as PlayerSettingsExtras).
                    SettingsSection.DOWNLOADS -> DownloadsSettingsSection(settings = state.settings, onUpdate = viewModel::updateSettings)
                    SettingsSection.READER -> {
                        ReaderSettingsSection(settings = state.settings, onUpdate = viewModel::updateSettings)
                        NovelReaderSettingsSection(settings = state.settings, onUpdate = viewModel::updateSettings)
                    }
                    SettingsSection.DATA -> DataSection(state, viewModel)
                    SettingsSection.ABOUT -> AboutSection(state, viewModel)
                }
            }
        }
    }
}

private val EXPANDED_MIN_WIDTH = 840.dp
