@file:OptIn(ExperimentalMaterial3Api::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.designsystem.theme.LocalNijiyomeAmoled
import app.nijiyome.feature.downloads.DownloadsSettingsSection
import app.nijiyome.feature.manga.ReaderSettingsSection
import app.nijiyome.feature.novel.NovelReaderSettingsSection
import app.nijiyome.feature.torrents.TorrentSettingsPage
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LoadState
import app.nijiyome.model.NetworkSettingsController
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SourceMode
import app.nijiyome.model.ThemeMode
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.state.screenInsets
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/**
 * Settings screen with:
 * - Instant live search across every individual setting (bilingual ES/EN, accent-folded).
 * - Desktop / Wide (>= 840 dp): true Master-Detail (persistent sidebar index + active category detail pane, keeping Torrents embedded cleanly).
 * - Mobile / Compact (< 840 dp): clean Category Hub with live value summaries -> focused category subpage.
 */
@Composable
fun SettingsScreen(
    onOpenExtensions: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    initialSection: SettingsSection? = null,
    onOpenLinked: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenDebrid: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                SettingsEvent.ResetDone -> {
                    restartAfterReset()
                    null
                }
            }
            if (message != null) currentSnackbar.show(message)
        }
    }

    SettingsContent(
        state = state,
        viewModel = viewModel,
        initialSection = initialSection,
        onOpenExtensions = onOpenExtensions,
        onOpenLinked = onOpenLinked,
        onOpenDebrid = onOpenDebrid,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Desktop centered Settings modal (`Ctrl+P` / `Ctrl+,`), inspired by LiMusic Forge: opens over the current
 * screen so `Esc` or `×` returns immediately without losing scroll or navigation state.
 */
@Composable
fun SettingsModalDialog(
    initialSection: SettingsSection? = null,
    onDismiss: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLink: () -> Unit,
    onOpenLinked: () -> Unit = {},
    onOpenDebrid: () -> Unit = {},
) {
    TrackModal()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 1020.dp)
                .fillMaxHeight(0.88f)
                .heightIn(max = 760.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Pill("Ctrl+P", tone = PillTone.NEUTRAL)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cerrar configuración")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SettingsScreen(
                        onOpenExtensions = {
                            onDismiss()
                            onOpenExtensions()
                        },
                        onOpenLink = {
                            onDismiss()
                            onOpenLink()
                        },
                        initialSection = initialSection,
                        onOpenLinked = {
                            onDismiss()
                            onOpenLinked()
                        },
                        onOpenDebrid = {
                            onDismiss()
                            onOpenDebrid()
                        },
                    )
                }
            }
        }
    }
}

/** Section order of Settings. */
internal val SETTINGS_SECTIONS: List<SettingsSection> = listOf(
    SettingsSection.APPEARANCE,
    SettingsSection.PLAYER,
    SettingsSection.METADATA,
    SettingsSection.ACCOUNTS,
    SettingsSection.LINK,
    SettingsSection.NETWORK,
    SettingsSection.TORRENTS,
    SettingsSection.DOWNLOADS,
    SettingsSection.READER,
    SettingsSection.DATA,
    SettingsSection.ABOUT,
)

/** Sections visible on this platform: Accounts when ListsRepository is bound, Network when NetworkSettingsController is bound. */
internal fun settingsSections(hasLists: Boolean, hasNetwork: Boolean = false): List<SettingsSection> =
    SETTINGS_SECTIONS.filter { s ->
        (hasLists || s != SettingsSection.ACCOUNTS) &&
            (hasNetwork || s != SettingsSection.NETWORK)
    }

internal fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.APPEARANCE -> Icons.Outlined.Palette
    SettingsSection.PLAYER -> Icons.Outlined.PlayCircle
    SettingsSection.METADATA -> Icons.Outlined.Sync
    SettingsSection.ACCOUNTS -> Icons.Outlined.Bookmarks
    SettingsSection.LINK -> Icons.Outlined.Link
    SettingsSection.NETWORK -> Icons.Outlined.Public
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
    SettingsSection.NETWORK -> stringResource(Res.string.settings_net_title)
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
    onOpenDebrid: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val sections = remember(koin) {
        settingsSections(
            hasLists = koin.getOrNull<ListsRepository>() != null,
            hasNetwork = koin.getOrNull<NetworkSettingsController>() != null,
        )
    }
    val sectionSet = remember(sections) { sections.toSet() }

    // Desktop active section (always has one selected) vs Compact active subpage (null = Category Hub).
    var activeDesktopSection by rememberSaveable(initialSection) {
        mutableStateOf(initialSection?.takeIf { it in sections } ?: sections.first())
    }
    var activeMobileSection by rememberSaveable(initialSection) {
        mutableStateOf(initialSection?.takeIf { it in sections })
    }
    var torrentsSubpageOpen by rememberSaveable(initialSection) {
        mutableStateOf(initialSection == SettingsSection.TORRENTS)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var highlightedSetting by remember { mutableStateOf<String?>(null) }

    val searchResults = remember(searchQuery, sectionSet) {
        SettingsCatalog.search(searchQuery, sectionSet)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val expanded = maxWidth >= EXPANDED_MIN_WIDTH

        // On compact phones, full-page TorrentSettingsPage when navigated deep into Torrents engine.
        if (!expanded && torrentsSubpageOpen) {
            val closeTorrents: () -> Unit = {
                if (initialSection == SettingsSection.TORRENTS && onBack != null) onBack() else torrentsSubpageOpen = false
            }
            PlatformBackHandler(onBack = closeTorrents)
            TorrentSettingsPage(modifier = Modifier.fillMaxSize(), onBack = closeTorrents)
            return@BoxWithConstraints
        }

        // On compact phones, handle Back from a focused category subpage back to the Category Hub.
        if (!expanded && activeMobileSection != null && (initialSection == null || activeMobileSection != initialSection)) {
            PlatformBackHandler {
                activeMobileSection = null
                highlightedSetting = null
            }
        }

        val currentMobile = activeMobileSection
        val topBarTitle = if (!expanded && currentMobile != null) currentMobile.title() else stringResource(Res.string.settings_title)
        val topBarBack: (() -> Unit)? = when {
            !expanded && currentMobile != null -> {
                if (initialSection != null && currentMobile == initialSection && onBack != null) onBack
                else ({ activeMobileSection = null; highlightedSetting = null })
            }
            onBack != null -> onBack
            else -> null
        }

        Scaffold(
            contentWindowInsets = screenInsets,
            topBar = {
                if (topBarBack != null) {
                    TopAppBar(
                        title = { Text(topBarTitle) },
                        navigationIcon = {
                            IconButton(onClick = topBarBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                            }
                        },
                    )
                }
            },
        ) { padding ->
            if (expanded) {
                Row(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                    // Left persistent Master column with live search + categories
                    Column(
                        Modifier
                            .width(264.dp)
                            .fillMaxHeight()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SettingsSearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            sections.forEach { section ->
                                NavigationDrawerItem(
                                    label = { Text(section.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    selected = searchQuery.isBlank() && section == activeDesktopSection,
                                    onClick = {
                                        searchQuery = ""
                                        highlightedSetting = null
                                        activeDesktopSection = section
                                    },
                                    icon = { Icon(section.icon(), contentDescription = null) },
                                )
                            }
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // Right Detail pane
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        if (searchQuery.isNotBlank()) {
                            SettingsSearchResultsList(
                                query = searchQuery,
                                results = searchResults,
                                onSelect = { item ->
                                    activeDesktopSection = item.section
                                    highlightedSetting = item.title
                                    searchQuery = ""
                                },
                                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight(),
                            )
                        } else if (activeDesktopSection == SettingsSection.TORRENTS && torrentsSubpageOpen) {
                            TorrentSettingsPage(
                                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight(),
                                onBack = { torrentsSubpageOpen = false },
                            )
                        } else {
                            SectionDetailPane(
                                section = activeDesktopSection,
                                state = state,
                                viewModel = viewModel,
                                expanded = true,
                                highlightedSetting = highlightedSetting,
                                onDismissHighlight = { highlightedSetting = null },
                                onOpenExtensions = onOpenExtensions,
                                onOpenLinked = onOpenLinked,
                                onOpenTorrents = { torrentsSubpageOpen = true },
                                onOpenDebrid = onOpenDebrid,
                                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight(),
                            )
                        }
                    }
                }
            } else {
                // Compact / Mobile: Category Hub or Focused Category Subpage
                Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
                    if (currentMobile != null) {
                        SectionDetailPane(
                            section = currentMobile,
                            state = state,
                            viewModel = viewModel,
                            expanded = false,
                            highlightedSetting = highlightedSetting,
                            onDismissHighlight = { highlightedSetting = null },
                            onOpenExtensions = onOpenExtensions,
                            onOpenLinked = onOpenLinked,
                            onOpenTorrents = { torrentsSubpageOpen = true },
                            onOpenDebrid = onOpenDebrid,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CompactSettingsHub(
                            state = state,
                            sections = sections,
                            showTitle = topBarBack == null,
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            searchResults = searchResults,
                            onSelectSection = { section ->
                                highlightedSetting = null
                                activeMobileSection = section
                            },
                            onSelectSearchItem = { item ->
                                highlightedSetting = item.title
                                searchQuery = ""
                                activeMobileSection = item.section
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text("Buscar ajuste…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun CompactSettingsHub(
    state: SettingsUiState,
    sections: List<SettingsSection>,
    showTitle: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<SettingCatalogItem>,
    onSelectSection: (SettingsSection) -> Unit,
    onSelectSearchItem: (SettingCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val amoled = LocalNijiyomeAmoled.current
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTitle) {
            item(key = "title") {
                Text(
                    stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
                )
            }
        }
        item(key = "search") {
            SettingsSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        if (query.isNotBlank()) {
            if (searchResults.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "Sin coincidencias",
                        message = "Prueba con otro término como «AMOLED», «proxy», «subtítulos» o «logs».",
                    )
                }
            } else {
                items(searchResults, key = { it.id }) { item ->
                    SearchResultCard(item = item, onClick = { onSelectSearchItem(item) })
                }
            }
        } else {
            items(sections, key = { it.id }) { section ->
                val summary = sectionSummary(section, state)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onSelectSection(section) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(section.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(section.title(), style = MaterialTheme.typography.titleMedium)
                            if (summary.isNotBlank()) {
                                Text(
                                    summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchResultsList(
    query: String,
    results: List<SettingCatalogItem>,
    onSelect: (SettingCatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hdr") {
            Text(
                "Resultados para «${query.trim()}» (${results.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (results.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "Sin coincidencias",
                    message = "Prueba buscando «AMOLED», «proxy», «audio», «subtítulos», «debrid» o «logs».",
                )
            }
        } else {
            items(results, key = { it.id }) { item ->
                SearchResultCard(item = item, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun SearchResultCard(item: SettingCatalogItem, onClick: () -> Unit) {
    val amoled = LocalNijiyomeAmoled.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(item.section.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Pill(item.section.title(), tone = PillTone.NEUTRAL)
        }
    }
}

@Composable
private fun SectionDetailPane(
    section: SettingsSection,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    expanded: Boolean,
    highlightedSetting: String?,
    onDismissHighlight: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenTorrents: () -> Unit,
    onOpenDebrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (highlightedSetting != null) {
            item(key = "highlight") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            highlightedSetting,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = onDismissHighlight, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Ocultar aviso", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        item(key = section.id) {
            SectionPanel(section) {
                when (section) {
                    SettingsSection.APPEARANCE -> AppearanceSection(state, viewModel, showRailStyle = expanded)
                    SettingsSection.PLAYER -> {
                        PlayerSection(state, viewModel, onOpenExtensions)
                        FrameGenSettingsSlot()
                    }
                    SettingsSection.METADATA -> MetadataSection(state, viewModel)
                    SettingsSection.ACCOUNTS -> AccountsSection(state, viewModel)
                    SettingsSection.LINK -> LinkSectionForPlatform(state, viewModel, onOpenLinked)
                    SettingsSection.NETWORK -> NetworkSectionForPlatform()
                    SettingsSection.TORRENTS -> TorrentsSection(onOpenTorrents, onOpenDebrid)
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

@Composable
private fun sectionSummary(section: SettingsSection, state: SettingsUiState): String {
    val s = state.settings
    return when (section) {
        SettingsSection.APPEARANCE -> {
            val mode = when {
                s.amoled && s.themeMode != ThemeMode.LIGHT -> "AMOLED"
                s.themeMode == ThemeMode.LIGHT -> "Claro"
                s.themeMode == ThemeMode.DARK -> "Oscuro"
                else -> "Sistema"
            }
            "$mode · ${s.paletteId}"
        }
        SettingsSection.PLAYER -> {
            val eng = when (s.playerEngine.shown()) {
                PlayerEngine.MPV -> "libmpv"
                PlayerEngine.MEDIA3 -> "Media3"
                PlayerEngine.EXTERNAL -> "Externo"
            }
            val src = if (s.sourcePrefs.mode == SourceMode.MANUAL) "Manual" else "Auto"
            "$eng · $src"
        }
        SettingsSection.METADATA -> "${s.providerPriority.size} proveedores · ${s.titleLanguage.name.lowercase()}"
        SettingsSection.ACCOUNTS -> if (state.accounts.isEmpty()) "Sin cuentas vinculadas" else "${state.accounts.size} vinculadas"
        SettingsSection.LINK -> if (state.linkPaired) "Vinculado" else "PC ↔ Teléfono y sincronización"
        SettingsSection.NETWORK -> "Proxy SOCKS5/HTTP · FlareSolverr"
        SettingsSection.TORRENTS -> "Motor, límites y Debrid"
        SettingsSection.DOWNLOADS -> "Descargas web y almacenamiento"
        SettingsSection.READER -> "Manga, novelas y narrador TTS"
        SettingsSection.DATA -> {
            val cache = (state.storage as? LoadState.Ok)?.value?.clearableBytes
            if (cache != null) "${formatBytes(cache)} en caché" else "Caché, historial y sistema"
        }
        SettingsSection.ABOUT -> "Registros de error, versión y licencias"
    }
}

private val EXPANDED_MIN_WIDTH = 840.dp
