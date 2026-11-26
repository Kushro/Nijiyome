// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.AppSnackbarHost
import app.nijiyome.designsystem.components.LocalAppSnackbar
import app.nijiyome.designsystem.components.rememberAppSnackbarController
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.designsystem.direction.Directions
import app.nijiyome.designsystem.theme.LocalNijiyomeAmoled
import app.nijiyome.feature.torrents.TorrentSettingsPage
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.LoadState
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.SettingsStructure
import app.nijiyome.model.SourceMode
import app.nijiyome.model.ThemeMode
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.look.structures
import app.nijiyome.ui.patterns.gallery.DesignGalleryScreen
import app.nijiyome.ui.platform.PlatformBackHandler
import app.nijiyome.ui.state.screenInsets
import kotlinx.coroutines.delay
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/**
 * Settings (artifact screen `ajustes`): 14 categories in the structure the user picked (`structures.settings`): [SettingsStructure.LIST_DETAIL]
 * (categories with a live summary beside the open category; phones: list, then the category) or [SettingsStructure.SEARCH_FIRST] (the
 * search is the centre, with the most searched settings and the recent ones, categories below). Both share the view model and every
 * category's content.
 *
 * [initialSection] is the legacy deep link (`Route.SettingsSection`), mapped to its category by [toCategory]. [initialItemId] is a
 * [SettingsCatalog] id (`SettingCatalogItem.id`, what the command palette holds): Settings opens its category, scrolls to the row and
 * flashes it. [onOpenCalendar] (optional) adds "Ver el calendario" to Notificaciones.
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
    initialItemId: String? = null,
    onOpenCalendar: (() -> Unit)? = null,
    jumpSection: SettingsSection? = null,
    jumpItemId: String? = null,
    jumpTick: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val restartAfterReset = rememberRestartAfterReset()
    val currentSnackbar by rememberUpdatedState(snackbar)
    // One save model: every change applies at once; the legend of the open category confirms it ("Guardado").
    var savedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.refreshStorage()
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.Saved -> {
                    savedTick++
                    null
                }
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
        initialItemId = initialItemId,
        savedTick = savedTick,
        onOpenExtensions = onOpenExtensions,
        onOpenLinked = onOpenLinked,
        onOpenDebrid = onOpenDebrid,
        onOpenCalendar = onOpenCalendar,
        onBack = onBack,
        jumpSection = jumpSection,
        jumpItemId = jumpItemId,
        jumpTick = jumpTick,
        modifier = modifier,
    )
}

/**
 * Desktop centered Settings modal (`Ctrl+,`, alias `Ctrl+P`): opens over the current screen so `Esc` or `×` returns immediately without
 * losing scroll or navigation state. [initialItemId]: as [SettingsScreen].
 */
@Composable
fun SettingsModalDialog(
    initialSection: SettingsSection? = null,
    onDismiss: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenLink: () -> Unit,
    onOpenLinked: () -> Unit = {},
    onOpenDebrid: () -> Unit = {},
    initialItemId: String? = null,
    onOpenCalendar: (() -> Unit)? = null,
) {
    TrackModal()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog is its own window: the shell's snackbar host draws under it. Notices Settings raises while it is a modal
        // ("Diseño restablecido · Deshacer", account and log results) get a host of their own, on top, where Undo can be pressed.
        val modalSnackbar = rememberAppSnackbarController()
        CompositionLocalProvider(LocalAppSnackbar provides modalSnackbar) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 1180.dp).fillMaxHeight(0.9f).heightIn(max = 820.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                SettingsScreen(
                    onOpenExtensions = { onDismiss(); onOpenExtensions() },
                    onOpenLink = { onDismiss(); onOpenLink() },
                    initialSection = initialSection,
                    initialItemId = initialItemId,
                    onOpenLinked = { onDismiss(); onOpenLinked() },
                    onOpenDebrid = { onDismiss(); onOpenDebrid() },
                    onOpenCalendar = onOpenCalendar?.let { open -> { onDismiss(); open() } },
                )
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("Ctrl ,", tone = PillTone.NEUTRAL)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s10_close_settings))
                    }
                }
                AppSnackbarHost(modalSnackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
            }
        }
        }
    }
}

/** What a deep-linked section lands on when no exact setting was asked for. */
private fun SettingsSection.defaultItem(): String? = when (this) {
    SettingsSection.NETWORK -> "network_proxy"
    SettingsSection.TORRENTS -> "torrents_engine"
    SettingsSection.DOWNLOADS -> "downloads_web"
    else -> null
}

/** Navigation state shared by both structures. */
private class SettingsNav(
    val categories: List<SettingsCategory>,
    current: SettingsCategory,
    open: Boolean,
) {
    var current by mutableStateOf(current)
    /** Phones: the category page is open (else the list / hub). */
    var open by mutableStateOf(open)
    var gallery by mutableStateOf(false)
    var query by mutableStateOf("")
    val recents = mutableStateListOf<String>()

    fun select(c: SettingsCategory) {
        current = c
        open = true
        gallery = false
    }

    companion object {
        /** Keeps the category, the open page, the gallery, the search text and the recents across rotation / process death. */
        fun saver(categories: List<SettingsCategory>): Saver<SettingsNav, Any> = listSaver(
            save = { listOf(it.current.name, it.open, it.gallery, it.query, ArrayList(it.recents)) },
            restore = { saved ->
                val current = SettingsCategory.entries.firstOrNull { it.name == saved[0] }?.takeIf { it in categories } ?: categories.first()
                SettingsNav(categories, current, saved[1] as Boolean).apply {
                    gallery = saved[2] as Boolean
                    query = saved[3] as String
                    @Suppress("UNCHECKED_CAST")
                    recents.addAll(saved[4] as List<String>)
                }
            },
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    initialSection: SettingsSection?,
    initialItemId: String?,
    savedTick: Int,
    onOpenExtensions: () -> Unit,
    onOpenLinked: () -> Unit,
    onOpenDebrid: () -> Unit,
    onOpenCalendar: (() -> Unit)?,
    onBack: (() -> Unit)?,
    jumpSection: SettingsSection? = null,
    jumpItemId: String? = null,
    jumpTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val categories = remember(koin) {
        settingsCategories(hasLists = koin.getOrNull<ListsRepository>() != null, systemOffered = systemCategoryOffered)
    }
    val targetItem = initialItemId ?: initialSection?.defaultItem()
    val initialCategory = (SettingsCatalog.categoryOf(targetItem) ?: initialSection?.toCategory())
        ?.let { c -> if (c in categories) c else if (c == SettingsCategory.SYSTEM) SettingsCategory.SOURCES else null }
    val nav = rememberSaveable(initialSection, initialItemId, saver = SettingsNav.saver(categories)) {
        SettingsNav(categories, initialCategory ?: categories.first(), open = initialCategory != null)
    }
    val jump = remember(initialSection, initialItemId) { SettingsJumpState(targetItem) }
    // AJ-07: live jump from Ctrl+K when Settings is already open (switch category + scroll + highlight in place).
    var lastHandledJump by remember { mutableIntStateOf(jumpTick) }
    LaunchedEffect(jumpTick) {
        if (jumpTick > lastHandledJump) {
            lastHandledJump = jumpTick
            val item = jumpItemId ?: jumpSection?.defaultItem()
            val cat = (SettingsCatalog.categoryOf(item) ?: jumpSection?.toCategory())
                ?.let { c -> if (c in categories) c else if (c == SettingsCategory.SYSTEM) SettingsCategory.SOURCES else null }
            if (cat != null) nav.select(cat)
            if (item != null) jump.jumpTo(item)
        }
    }
    val catalog = SettingsCatalog.items
    val allowed = remember(categories) { categories.toSet() }
    val results = remember(nav.query, catalog, allowed) { SettingsCatalog.search(nav.query, catalog, allowed) }
    val pick: (SettingCatalogItem) -> Unit = { item ->
        nav.select(item.category)
        nav.query = ""
        nav.recents.remove(item.id)
        nav.recents.add(0, item.id)
        if (nav.recents.size > 5) nav.recents.removeAt(nav.recents.lastIndex)
        jump.jumpTo(item.id)
    }

    CompositionLocalProvider(LocalSettingsJump provides jump) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val wide = maxWidth >= EXPANDED_MIN_WIDTH
            val actions = SettingsActions(
                onOpenExtensions = onOpenExtensions,
                onOpenLinked = onOpenLinked,
                onOpenDebrid = onOpenDebrid,
                onOpenCalendar = onOpenCalendar,
                onOpenGallery = { nav.gallery = true },
                wide = wide,
            )
            if (nav.gallery) {
                // The design gallery as a full page inside Settings.
                PlatformBackHandler { nav.gallery = false }
                DesignGalleryScreen(onBack = { nav.gallery = false }, modifier = Modifier.fillMaxSize())
                return@BoxWithConstraints
            }
            val page: @Composable (Modifier) -> Unit = { m -> CategoryPage(nav.current, state, viewModel, actions, savedTick, showHeader = wide, modifier = m) }
            val ctx = SettingsLayoutCtx(state, nav, results, catalog, pick, page, wide, onBack, deepLinked = initialCategory != null)
            when (structures.settings) {
                SettingsStructure.LIST_DETAIL -> ListDetailSettings(ctx)
                SettingsStructure.SEARCH_FIRST -> SearchFirstSettings(ctx)
            }
        }
    }
}

private class SettingsLayoutCtx(
    val state: SettingsUiState,
    val nav: SettingsNav,
    val results: List<SettingCatalogItem>,
    val catalog: List<SettingCatalogItem>,
    val pick: (SettingCatalogItem) -> Unit,
    val page: @Composable (Modifier) -> Unit,
    val wide: Boolean,
    val onBack: (() -> Unit)?,
    /** Opened at a category from a deep link: Back from that category leaves Settings. */
    val deepLinked: Boolean,
)

// ==================================================================== structure: Lista y detalle

@Composable
private fun ListDetailSettings(ctx: SettingsLayoutCtx) {
    val nav = ctx.nav
    if (!ctx.wide) {
        PhoneSettings(ctx) {
            SettingsSearchField(nav.query, { nav.query = it }, Modifier.fillMaxWidth())
        }
        return
    }
    Scaffold(contentWindowInsets = screenInsets, topBar = { BackBar(ctx) }) { padding ->
        Row(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsTitle()
                SettingsSearchField(nav.query, { nav.query = it }, Modifier.fillMaxWidth(), showKeys = true)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (nav.query.isNotBlank()) {
                        SearchResults(nav.query, ctx.results, nav.current, ctx.pick, onSuggestion = { nav.query = it })
                    } else {
                        nav.categories.forEach { c ->
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text(c.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(categorySummary(c, ctx.state), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                },
                                selected = c == nav.current,
                                onClick = { nav.select(c) },
                                icon = { Icon(c.icon(), contentDescription = null) },
                                // The open category is the accent's soft tone (artifact `--nj-accent-soft`), not the strongest fill.
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                ctx.page(Modifier.widthIn(max = 960.dp).fillMaxSize())
            }
        }
    }
}

// ==================================================================== structure: Búsqueda primero

@Composable
private fun SearchFirstSettings(ctx: SettingsLayoutCtx) {
    val nav = ctx.nav
    if (!ctx.wide) {
        PhoneSettings(ctx) {
            SettingsSearchField(nav.query, { nav.query = it }, Modifier.fillMaxWidth(), big = true)
            if (nav.query.isBlank()) {
                FrequentChips { nav.query = it }
                RecentSettings(ctx)
            }
        }
        return
    }
    Scaffold(contentWindowInsets = screenInsets, topBar = { BackBar(ctx) }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsTitle()
                SettingsSearchField(nav.query, { nav.query = it }, Modifier.weight(1f).widthIn(max = 720.dp), big = true, showKeys = true)
            }
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (nav.query.isNotBlank()) {
                        SearchResults(nav.query, ctx.results, nav.current, ctx.pick, onSuggestion = { nav.query = it })
                    } else {
                        FrequentChips { nav.query = it }
                        RecentSettings(ctx)
                        CategoryCards(nav.categories, ctx.state, current = nav.current, onSelect = nav::select)
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    ctx.page(Modifier.widthIn(max = 960.dp).fillMaxSize())
                }
            }
        }
    }
}

// ==================================================================== shared pieces

/** Phones (both structures): the hub ([top] = the structure's search block, then results or the category cards), or the open category. */
@Composable
private fun PhoneSettings(ctx: SettingsLayoutCtx, top: @Composable () -> Unit) {
    val nav = ctx.nav
    val closeCategory: () -> Unit = {
        if (ctx.deepLinked && ctx.onBack != null) ctx.onBack.invoke() else nav.open = false
    }
    if (nav.open) {
        PlatformBackHandler(onBack = closeCategory)
        Scaffold(
            contentWindowInsets = screenInsets,
            topBar = {
                TopAppBar(
                    title = { Text(nav.current.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = closeCategory) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { ctx.page(Modifier.fillMaxSize()) }
        }
        return
    }
    Scaffold(contentWindowInsets = screenInsets, topBar = { BackBar(ctx) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ctx.onBack == null) SettingsTitle()
            top()
            if (nav.query.isNotBlank()) {
                SearchResults(nav.query, ctx.results, null, ctx.pick, onSuggestion = { nav.query = it })
            } else {
                CategoryCards(nav.categories, ctx.state, current = null, onSelect = nav::select)
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

/** The back arrow when Settings was pushed (phones from Tú, a deep link); nothing on the rail destination. */
@Composable
private fun BackBar(ctx: SettingsLayoutCtx) {
    val back = ctx.onBack ?: return
    TopAppBar(
        title = { Text(stringResource(Res.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
        },
    )
}

@Composable
private fun SettingsTitle() {
    Text(
        stringResource(Res.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, big: Boolean = false, showKeys: Boolean = false) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        textStyle = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(Res.string.rd_s10_search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s10_search_clear)) }
            } else if (showKeys) {
                Pill("Ctrl K", tone = PillTone.NEUTRAL, modifier = Modifier.padding(end = 8.dp))
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}

/** "Lo más buscado": one-tap searches. */
@Composable
private fun FrequentChips(onPick: (String) -> Unit) {
    val words = buildList {
        add(stringResource(Res.string.rd_s10_freq_amoled))
        if (systemCategoryOffered) add(stringResource(Res.string.rd_s10_freq_proxy))
        add(stringResource(Res.string.rd_s10_freq_port))
        add(stringResource(Res.string.rd_s10_freq_shortcuts))
        add(stringResource(Res.string.rd_s10_freq_subtitles))
        add(stringResource(Res.string.rd_s10_freq_debrid))
        add(stringResource(Res.string.rd_s10_freq_logs))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.rd_s10_freq_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            words.forEach { w -> AssistChip(onClick = { onPick(w) }, label = { Text(w) }) }
        }
    }
}

/** The settings opened from search in this session, newest first. */
@Composable
private fun RecentSettings(ctx: SettingsLayoutCtx) {
    val items = ctx.nav.recents.mapNotNull { id -> ctx.catalog.firstOrNull { it.id == id } }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.rd_s10_recent_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item -> ResultRow(item, query = "", current = false, leading = Icons.Outlined.History, onClick = { ctx.pick(item) }) }
    }
}

@Composable
private fun SearchResults(query: String, results: List<SettingCatalogItem>, current: SettingsCategory?, onPick: (SettingCatalogItem) -> Unit, onSuggestion: (String) -> Unit) {
    if (results.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(Res.string.rd_s10_search_none, query.trim()),
            message = stringResource(Res.string.rd_s10_search_none_hint),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Res.string.rd_s10_freq_amoled, Res.string.rd_s10_freq_subtitles, Res.string.rd_s10_freq_debrid, Res.string.rd_s10_freq_logs).forEach { r ->
                val w = stringResource(r)
                AssistChip(onClick = { onSuggestion(w) }, label = { Text(w) })
            }
        }
        return
    }
    Text(
        stringResource(Res.string.rd_s10_search_count, results.size, query.trim()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp).politeLiveRegion(),
    )
    results.take(30).forEach { item -> ResultRow(item, query, current = item.category == current, leading = item.category.icon(), onClick = { onPick(item) }) }
}

@Composable
private fun ResultRow(item: SettingCatalogItem, query: String, current: Boolean, leading: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val amoled = LocalNijiyomeAmoled.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(leading, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(highlighted(item.title, query), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${item.category.title()} · ${item.subtitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** The first query word in bold inside [text] (accent-insensitive). */
@Composable
private fun highlighted(text: String, query: String) = buildAnnotatedString {
    val word = SettingsCatalog.fold(query.trim().split(' ').firstOrNull().orEmpty())
    val folded = SettingsCatalog.fold(text)
    val i = if (word.isEmpty()) -1 else folded.indexOf(word)
    if (i < 0 || folded.length != text.length) {
        append(text)
    } else {
        append(text.substring(0, i))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append(text.substring(i, i + word.length)) }
        append(text.substring(i + word.length))
    }
}

/** The categories as cards with their live summary. */
@Composable
private fun CategoryCards(categories: List<SettingsCategory>, state: SettingsUiState, current: SettingsCategory?, onSelect: (SettingsCategory) -> Unit) {
    val amoled = LocalNijiyomeAmoled.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { c ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (c == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (amoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onSelect(c) },
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(c.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.title(), style = MaterialTheme.typography.titleMedium)
                        Text(categorySummary(c, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One category: header (wide), the save legend, then its content. */
@Composable
private fun CategoryPage(
    category: SettingsCategory,
    state: SettingsUiState,
    vm: SettingsViewModel,
    actions: SettingsActions,
    savedTick: Int,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = if (showHeader) 72.dp else 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showHeader) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text(category.title(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(stringResource(category.descRes()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SaveLegend(savedTick)
        }
        when (category) {
            SettingsCategory.APPEARANCE -> AppearancePage(state, vm, actions, Modifier.weight(1f).padding(horizontal = 16.dp))
            SettingsCategory.TRANSFERS -> TransfersPage(state, vm, actions, Modifier.weight(1f))
            else -> Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { CategoryGroups(category, state, vm, actions) }
        }
    }
}

/** "Changes apply when you tap", which turns into "Guardado" for a moment after each saved change. */
@Composable
private fun SaveLegend(savedTick: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(savedTick) {
        if (savedTick == 0) return@LaunchedEffect
        shown = true
        delay(2_000)
        shown = false
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.politeLiveRegion()) {
        Icon(
            if (shown) Icons.Outlined.Check else Icons.Outlined.Bolt,
            contentDescription = null,
            tint = if (shown) app.nijiyome.designsystem.direction.LocalSignalColors.current.ok else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(if (shown) Res.string.rd_s10_saved else Res.string.rd_s10_save_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Torrents y descargas: the torrent settings page (lane S8, it scrolls by itself) and the web downloads section, as two tabs; Debrid
 * links to Fuentes › Debrid. A jump to the downloads entry opens its tab.
 */
@Composable
private fun TransfersPage(state: SettingsUiState, vm: SettingsViewModel, actions: SettingsActions, modifier: Modifier = Modifier) {
    val jump = LocalSettingsJump.current
    var downloads by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(jump?.targetId) {
        when (jump?.targetId) {
            "downloads_web" -> downloads = true
            "torrents_engine", "torrents_debrid" -> downloads = false
        }
    }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Segmented(
                options = listOf(
                    SegmentedOption(false, stringResource(Res.string.rd_s10_tab_torrents), icon = Icons.Outlined.Download),
                    SegmentedOption(true, stringResource(Res.string.rd_s10_set_downloads)),
                ),
                selected = downloads,
                onSelect = { downloads = it },
                fillWidth = true,
            )
            TransfersLinks(actions.onOpenDebrid)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (downloads) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { DownloadsGroup(state, vm) }
        } else {
            SettingAnchor("torrents_engine", modifier = Modifier.weight(1f)) {
                TorrentSettingsPage(modifier = Modifier.fillMaxSize(), onBack = null)
            }
        }
    }
}

/** Live one-line summary of a category in the list (localised). */
@Composable
private fun categorySummary(c: SettingsCategory, state: SettingsUiState): String {
    val s = state.settings
    return when (c) {
        SettingsCategory.APPEARANCE -> {
            val mode = stringResource(
                when {
                    s.amoled && s.themeMode != ThemeMode.LIGHT -> Res.string.rd_s10_tone_oled
                    s.themeMode == ThemeMode.LIGHT -> Res.string.settings_mode_light
                    s.themeMode == ThemeMode.DARK -> Res.string.settings_mode_dark
                    else -> Res.string.settings_mode_system
                },
            )
            "${Directions.byId(s.look.design.direction.id).name()} · $mode"
        }
        SettingsCategory.PLAYBACK -> stringResource(
            when (s.playerEngine.shown()) {
                PlayerEngine.MPV -> Res.string.settings_engine_mpv
                PlayerEngine.MEDIA3 -> Res.string.settings_engine_media3
                PlayerEngine.EXTERNAL -> Res.string.settings_engine_external
            },
        )
        SettingsCategory.ENHANCEMENTS -> stringResource(presetLabel(s.playerPreset))
        SettingsCategory.SUBTITLES -> stringResource(Res.string.rd_s10_sum_subtitles, displayLanguage(s.sourcePrefs.subLang), displayLanguage(s.sourcePrefs.dubLang))
        SettingsCategory.SOURCES -> "${stringResource(if (s.sourcePrefs.mode == SourceMode.MANUAL) UiR.string.src_mode_manual else UiR.string.src_mode_auto)} · ${s.sourcePrefs.minQuality.label()}"
        SettingsCategory.ACCOUNTS -> if (state.accounts.isEmpty()) stringResource(Res.string.rd_s10_sum_no_accounts) else stringResource(Res.string.rd_s10_sum_accounts, state.accounts.size)
        SettingsCategory.METADATA -> stringResource(Res.string.rd_s10_sum_providers, s.providerPriority.size)
        SettingsCategory.LINK -> if (state.linkPaired) stringResource(Res.string.rd_s10_sum_linked) else stringResource(c.descRes())
        SettingsCategory.DATA -> (state.storage as? LoadState.Ok)?.value?.clearableBytes?.let { stringResource(Res.string.rd_s10_sum_cache, formatBytes(it)) }
            ?: stringResource(c.descRes())
        else -> stringResource(c.descRes())
    }
}

private val EXPANDED_MIN_WIDTH: Dp = 840.dp

