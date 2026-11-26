// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.feature.settings.SettingsCatalog
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_badge_current
import app.nijiyome.feature.shell.resources.rd_s1_badge_off
import app.nijiyome.feature.shell.resources.rd_s1_badge_on
import app.nijiyome.feature.shell.resources.rd_s1_cmd_amoled_off
import app.nijiyome.feature.shell.resources.rd_s1_cmd_amoled_on
import app.nijiyome.feature.shell.resources.rd_s1_cmd_amoled_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_logs
import app.nijiyome.feature.shell.resources.rd_s1_cmd_logs_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_nav_command
import app.nijiyome.feature.shell.resources.rd_s1_cmd_nav_command_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_nav_medium
import app.nijiyome.feature.shell.resources.rd_s1_cmd_nav_medium_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_nav_rail
import app.nijiyome.feature.shell.resources.rd_s1_cmd_nav_rail_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_rail
import app.nijiyome.feature.shell.resources.rd_s1_cmd_rail_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_settings
import app.nijiyome.feature.shell.resources.rd_s1_cmd_settings_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_style
import app.nijiyome.feature.shell.resources.rd_s1_cmd_style_sub
import app.nijiyome.feature.shell.resources.rd_s1_cmd_theme
import app.nijiyome.feature.shell.resources.rd_s1_cmd_theme_sub
import app.nijiyome.feature.shell.resources.rd_s1_pal_catalog
import app.nijiyome.feature.shell.resources.rd_s1_pal_catalog_sub
import app.nijiyome.feature.shell.resources.rd_s1_pal_clear
import app.nijiyome.feature.shell.resources.rd_s1_pal_empty
import app.nijiyome.feature.shell.resources.rd_s1_pal_group_catalog
import app.nijiyome.feature.shell.resources.rd_s1_pal_group_commands
import app.nijiyome.feature.shell.resources.rd_s1_pal_group_go
import app.nijiyome.feature.shell.resources.rd_s1_pal_group_settings
import app.nijiyome.feature.shell.resources.rd_s1_pal_group_shortcuts
import app.nijiyome.feature.shell.resources.rd_s1_pal_hint_close
import app.nijiyome.feature.shell.resources.rd_s1_pal_hint_move
import app.nijiyome.feature.shell.resources.rd_s1_pal_hint_open
import app.nijiyome.feature.shell.resources.rd_s1_pal_placeholder
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_calendar
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_explore
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_foryou
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_home
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_image
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_library
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_link
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_manga
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_notifications
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_novels
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_profile
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_settings
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_sources
import app.nijiyome.feature.shell.resources.rd_s1_pal_sub_transfers
import app.nijiyome.feature.shell.resources.rd_s1_pal_title
import app.nijiyome.feature.shell.resources.rd_s1_sc_back
import app.nijiyome.feature.shell.resources.rd_s1_sc_digits
import app.nijiyome.feature.shell.resources.rd_s1_sc_palette
import app.nijiyome.feature.shell.resources.rd_s1_sc_rail
import app.nijiyome.feature.shell.resources.rd_s1_sc_settings
import app.nijiyome.feature.shell.resources.rd_s1_sc_sub
import app.nijiyome.feature.shell.resources.rd_s1_section_about
import app.nijiyome.feature.shell.resources.rd_s1_section_accounts
import app.nijiyome.feature.shell.resources.rd_s1_section_appearance
import app.nijiyome.feature.shell.resources.rd_s1_section_data
import app.nijiyome.feature.shell.resources.rd_s1_section_downloads
import app.nijiyome.feature.shell.resources.rd_s1_section_link
import app.nijiyome.feature.shell.resources.rd_s1_section_metadata
import app.nijiyome.feature.shell.resources.rd_s1_section_network
import app.nijiyome.feature.shell.resources.rd_s1_section_player
import app.nijiyome.feature.shell.resources.rd_s1_section_reader
import app.nijiyome.feature.shell.resources.rd_s1_section_torrents
import app.nijiyome.feature.shell.resources.rd_s1_theme_dark
import app.nijiyome.feature.shell.resources.rd_s1_theme_light
import app.nijiyome.feature.shell.resources.rd_s1_theme_system
import app.nijiyome.model.AppSettings
import app.nijiyome.model.NavStyle
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.ThemeMode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal enum class PaletteGroup { GO, COMMANDS, SETTINGS, SHORTCUTS, CATALOG }

internal data class PaletteEntry(
    val id: String,
    val group: PaletteGroup,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** Key cap on the right (`1`, `Ctrl ,`); [note] is plain text there instead. */
    val keys: String? = null,
    val note: String? = null,
    val keywords: List<String> = emptyList(),
    val run: () -> Unit,
)

/** What the palette can do; the shell fills it. Null actions are left out (no rail on phones, no shortcuts without a keyboard). */
internal class PaletteActions(
    val onNavigate: (Route) -> Unit,
    /** Settings at [section] (null = the first page); [itemId] is the exact `SettingsCatalog` entry to land on. */
    val onOpenSettings: (section: SettingsSection?, itemId: String?) -> Unit,
    val onSearchCatalog: (String) -> Unit,
    val onToggleAmoled: () -> Unit,
    val onCycleThemeMode: () -> Unit,
    val onToggleRail: (() -> Unit)?,
    val onSetNav: ((NavStyle) -> Unit)?,
    val onGoBack: (() -> Unit)?,
    /** The shortcuts work here (desktop window): the palette lists them. */
    val keyboard: Boolean,
)

/**
 * Command palette (Ctrl+K, Sparkles `pal`): one field that goes to any screen, runs a command, jumps to a setting or searches the
 * catalog, plus the keyboard shortcuts. Empty: the destinations, the commands and the shortcuts; typed: ranked matches by group and
 * always the catalog search last. ↑ ↓ move, Enter opens, Esc or Ctrl+K closes. Phones get it full screen.
 *
 * [destinations] are the screens offered here, in rail order; the first nine carry their number key.
 */
@Composable
internal fun NijiyomeCommandPalette(
    settings: AppSettings,
    allowedSections: Set<SettingsSection>,
    destinations: List<Route>,
    numbered: List<Route>,
    extraScreens: List<Route>,
    fullScreen: Boolean,
    actions: PaletteActions,
    onDismiss: () -> Unit,
) {
    TrackModal()
    var query by remember { mutableStateOf("") }
    var selectedIdx by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val goEntries = (destinations + extraScreens).distinct().map { route ->
        val position = numbered.indexOf(route).takeIf { it in 0..8 }?.plus(1)
        PaletteEntry(
            id = "go:${route.paletteKey()}",
            group = PaletteGroup.GO,
            title = route.label(),
            subtitle = stringResource(route.paletteSubtitle()),
            icon = route.icon(false),
            keys = position?.toString(),
            keywords = route.paletteKeywords(),
            run = { actions.onNavigate(route) },
        )
    }

    val nextTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.SYSTEM
    }
    val currentBadge = stringResource(Res.string.rd_s1_badge_current)
    val commandEntries = buildList {
        add(
            PaletteEntry(
                "cmd:settings", PaletteGroup.COMMANDS, stringResource(Res.string.rd_s1_cmd_settings), stringResource(Res.string.rd_s1_cmd_settings_sub),
                Icons.Outlined.Settings, keys = if (actions.keyboard) "Ctrl ," else null,
                keywords = listOf("ajustes", "settings", "preferencias", "config", "configuracion"),
            ) { actions.onOpenSettings(null, null) },
        )
        add(
            PaletteEntry(
                "cmd:style", PaletteGroup.COMMANDS, stringResource(Res.string.rd_s1_cmd_style), stringResource(Res.string.rd_s1_cmd_style_sub),
                Icons.Outlined.Palette, keywords = listOf("apariencia", "appearance", "diseno", "direccion", "tanabata", "obi", "sumi", "canon", "paleta", "tarjeta"),
            ) { actions.onOpenSettings(SettingsSection.APPEARANCE, null) },
        )
        add(
            PaletteEntry(
                "cmd:amoled", PaletteGroup.COMMANDS,
                stringResource(if (settings.amoled) Res.string.rd_s1_cmd_amoled_off else Res.string.rd_s1_cmd_amoled_on),
                stringResource(Res.string.rd_s1_cmd_amoled_sub), Icons.Outlined.DarkMode,
                note = stringResource(if (settings.amoled) Res.string.rd_s1_badge_on else Res.string.rd_s1_badge_off),
                keywords = listOf("amoled", "oled", "negro", "black", "pure"),
                run = actions.onToggleAmoled,
            ),
        )
        add(
            PaletteEntry(
                "cmd:theme", PaletteGroup.COMMANDS, stringResource(Res.string.rd_s1_cmd_theme),
                stringResource(Res.string.rd_s1_cmd_theme_sub, stringResource(settings.themeMode.paletteName()), stringResource(nextTheme.paletteName())), Icons.Outlined.Tune,
                keywords = listOf("tema", "theme", "dark", "light", "oscuro", "claro", "sistema", "modo"),
                run = actions.onCycleThemeMode,
            ),
        )
        actions.onToggleRail?.let { toggle ->
            add(
                PaletteEntry(
                    "cmd:rail", PaletteGroup.COMMANDS, stringResource(Res.string.rd_s1_cmd_rail), stringResource(Res.string.rd_s1_cmd_rail_sub),
                    Icons.Outlined.ViewSidebar, keys = if (actions.keyboard) "Ctrl B" else null,
                    keywords = listOf("rail", "riel", "sidebar", "barra", "menu", "compacto", "etiquetas"), run = toggle,
                ),
            )
        }
        actions.onSetNav?.let { setNav ->
            val current = settings.look.design.nav
            listOf(
                Triple(NavStyle.RAIL, Res.string.rd_s1_cmd_nav_rail, Res.string.rd_s1_cmd_nav_rail_sub),
                Triple(NavStyle.MEDIUM, Res.string.rd_s1_cmd_nav_medium, Res.string.rd_s1_cmd_nav_medium_sub),
                Triple(NavStyle.COMMAND, Res.string.rd_s1_cmd_nav_command, Res.string.rd_s1_cmd_nav_command_sub),
            ).forEach { (style, title, sub) ->
                add(
                    PaletteEntry(
                        "cmd:nav:${style.id}", PaletteGroup.COMMANDS, stringResource(title), stringResource(sub), Icons.Outlined.ViewQuilt,
                        note = if (style == current) currentBadge else null,
                        keywords = listOf("navegacion", "navigation", "riel", "rail", "medio", "medium", "comando", "command", "anime", "manga", "novela"),
                    ) { setNav(style) },
                )
            }
        }
        add(
            PaletteEntry(
                "cmd:logs", PaletteGroup.COMMANDS, stringResource(Res.string.rd_s1_cmd_logs), stringResource(Res.string.rd_s1_cmd_logs_sub),
                Icons.Outlined.Terminal, keywords = listOf("logs", "registros", "errores", "crash", "diagnostico", "debug"),
            ) { actions.onOpenSettings(SettingsSection.ABOUT, "about_logs") },
        )
    }

    val shortcutSub = stringResource(Res.string.rd_s1_sc_sub)
    val shortcutEntries = if (!actions.keyboard) emptyList() else buildList {
        add(PaletteEntry("sc:palette", PaletteGroup.SHORTCUTS, stringResource(Res.string.rd_s1_sc_palette), shortcutSub, Icons.Outlined.Keyboard, keys = "Ctrl K", keywords = listOf("atajo", "shortcut", "teclado", "keyboard")) {})
        add(PaletteEntry("sc:settings", PaletteGroup.SHORTCUTS, stringResource(Res.string.rd_s1_sc_settings), shortcutSub, Icons.Outlined.Keyboard, keys = "Ctrl ,", keywords = listOf("atajo", "shortcut", "ajustes", "ctrl p")) { actions.onOpenSettings(null, null) })
        actions.onToggleRail?.let { add(PaletteEntry("sc:rail", PaletteGroup.SHORTCUTS, stringResource(Res.string.rd_s1_sc_rail), shortcutSub, Icons.Outlined.Keyboard, keys = "Ctrl B", keywords = listOf("atajo", "shortcut", "riel", "rail"), run = it)) }
        add(PaletteEntry("sc:back", PaletteGroup.SHORTCUTS, stringResource(Res.string.rd_s1_sc_back), shortcutSub, Icons.Outlined.Keyboard, keys = "Esc", keywords = listOf("atajo", "shortcut", "atras", "back", "volver")) { actions.onGoBack?.invoke() })
        if (numbered.isNotEmpty()) {
            add(PaletteEntry("sc:digits", PaletteGroup.SHORTCUTS, stringResource(Res.string.rd_s1_sc_digits), shortcutSub, Icons.Outlined.Keyboard, keys = "1-${numbered.size.coerceAtMost(9)}", keywords = listOf("atajo", "shortcut", "numero", "number", "destino")) { actions.onNavigate(numbered.first()) })
        }
    }

    val settingEntries = SettingsCatalog.items.filter { it.section in allowedSections }.map { item ->
        PaletteEntry(
            id = "set:${item.id}",
            group = PaletteGroup.SETTINGS,
            title = item.title,
            subtitle = item.subtitle,
            icon = Icons.Outlined.Tune,
            note = stringResource(item.section.paletteName()),
            keywords = item.keywords,
            run = { actions.onOpenSettings(item.section, item.id) },
        )
    }

    val trimmed = query.trim()
    val catalogEntry = if (trimmed.isEmpty()) null else PaletteEntry(
        id = "catalog",
        group = PaletteGroup.CATALOG,
        title = stringResource(Res.string.rd_s1_pal_catalog, trimmed),
        subtitle = stringResource(Res.string.rd_s1_pal_catalog_sub),
        icon = Icons.Outlined.Search,
        run = { actions.onSearchCatalog(trimmed) },
    )
    val all = goEntries + commandEntries + settingEntries + shortcutEntries
    val groups: List<Pair<PaletteGroup, List<PaletteEntry>>> = if (trimmed.isEmpty()) {
        listOf(PaletteGroup.GO to goEntries, PaletteGroup.COMMANDS to commandEntries, PaletteGroup.SHORTCUTS to shortcutEntries)
    } else {
        val ranked = rankEntries(trimmed, all)
        PaletteGroup.entries.filter { it != PaletteGroup.CATALOG }.map { g -> g to ranked.filter { it.group == g }.take(GROUP_CAP) } +
            listOf(PaletteGroup.CATALOG to listOfNotNull(catalogEntry))
    }.filter { it.second.isNotEmpty() }
    val flat = groups.flatMap { it.second }
    val noMatches = trimmed.isNotEmpty() && flat.size <= 1

    LaunchedEffect(flat.size) {
        selectedIdx = if (flat.isEmpty()) 0 else selectedIdx.coerceIn(0, flat.lastIndex)
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Lazy index of each entry: one header row before every group.
    val lazyIndexOf = run {
        var lazy = if (noMatches) 1 else 0
        buildList { groups.forEach { (_, entries) -> lazy++; entries.forEach { _ -> add(lazy++) } } }
    }
    LaunchedEffect(selectedIdx, lazyIndexOf) {
        lazyIndexOf.getOrNull(selectedIdx)?.let { target ->
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (target <= first || target >= last) listState.scrollToItem((target - 1).coerceAtLeast(0))
        }
    }

    fun execute(entry: PaletteEntry) {
        onDismiss()
        entry.run()
    }

    val scheme = MaterialTheme.colorScheme
    val paneLabel = stringResource(Res.string.rd_s1_pal_title)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = if (fullScreen) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.large,
                color = if (fullScreen) scheme.surface else scheme.surfaceContainerHigh,
                border = if (fullScreen) null else BorderStroke(1.dp, scheme.outlineVariant),
                tonalElevation = if (fullScreen) 0.dp else 6.dp,
                shadowElevation = if (fullScreen) 0.dp else 12.dp,
                modifier = Modifier
                    .then(if (fullScreen) Modifier.fillMaxSize() else Modifier.padding(top = 64.dp, bottom = 48.dp).fillMaxWidth(0.92f).widthIn(max = 640.dp))
                    .semantics { paneTitle = paneLabel }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.DirectionDown -> {
                                if (flat.isNotEmpty()) selectedIdx = (selectedIdx + 1) % flat.size
                                true
                            }
                            e.key == Key.DirectionUp -> {
                                if (flat.isNotEmpty()) selectedIdx = (selectedIdx - 1 + flat.size) % flat.size
                                true
                            }
                            e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                                flat.getOrNull(selectedIdx)?.let(::execute)
                                true
                            }
                            e.key == Key.Escape || (e.isCtrlPressed && e.key == Key.K) -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = if (fullScreen) 64.dp else 54.dp).padding(horizontal = if (fullScreen) 4.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (fullScreen) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.rd_s1_pal_hint_close))
                            }
                        } else {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
                        }
                        val placeholder = stringResource(Res.string.rd_s1_pal_placeholder)
                        BasicTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                selectedIdx = 0
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = placeholder },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(placeholder, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    inner()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.rd_s1_pal_clear))
                            }
                        } else if (!fullScreen) {
                            KeyCap("Esc")
                        }
                    }
                    HorizontalDivider(color = scheme.outlineVariant)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().then(if (fullScreen) Modifier.weight(1f) else Modifier.heightIn(max = 440.dp)),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    ) {
                        if (noMatches) {
                            item(key = "empty") {
                                Text(
                                    stringResource(Res.string.rd_s1_pal_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                                )
                            }
                        }
                        var offset = 0
                        groups.forEach { (group, entries) ->
                            val start = offset
                            offset += entries.size
                            item(key = "h:${group.name}") {
                                Text(
                                    stringResource(group.title()),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp).semantics { heading() },
                                )
                            }
                            items(entries, key = { it.id }) { entry ->
                                val idx = start + entries.indexOf(entry)
                                PaletteRow(entry, active = idx == selectedIdx, onClick = { execute(entry) })
                            }
                        }
                    }
                    if (actions.keyboard && !fullScreen) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FooterHint("↑ ↓", stringResource(Res.string.rd_s1_pal_hint_move))
                            FooterHint("Enter", stringResource(Res.string.rd_s1_pal_hint_open))
                            FooterHint("Esc", stringResource(Res.string.rd_s1_pal_hint_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(entry: PaletteEntry, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (active) scheme.onSecondaryContainer else scheme.onSurface
    val fg2 = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (active) scheme.secondaryContainer else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(entry.icon, contentDescription = null, tint = fg2, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.subtitle.isNotEmpty()) {
                Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        entry.note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = fg2, maxLines = 1) }
        entry.keys?.let { KeyCap(it, color = fg2) }
    }
}

@Composable
private fun FooterHint(keys: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyCap(keys)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val GROUP_CAP = 6

private fun PaletteGroup.title(): StringResource = when (this) {
    PaletteGroup.GO -> Res.string.rd_s1_pal_group_go
    PaletteGroup.COMMANDS -> Res.string.rd_s1_pal_group_commands
    PaletteGroup.SETTINGS -> Res.string.rd_s1_pal_group_settings
    PaletteGroup.SHORTCUTS -> Res.string.rd_s1_pal_group_shortcuts
    PaletteGroup.CATALOG -> Res.string.rd_s1_pal_group_catalog
}

private fun ThemeMode.paletteName(): StringResource = when (this) {
    ThemeMode.SYSTEM -> Res.string.rd_s1_theme_system
    ThemeMode.LIGHT -> Res.string.rd_s1_theme_light
    ThemeMode.DARK -> Res.string.rd_s1_theme_dark
}

private fun SettingsSection.paletteName(): StringResource = when (this) {
    SettingsSection.APPEARANCE -> Res.string.rd_s1_section_appearance
    SettingsSection.PLAYER -> Res.string.rd_s1_section_player
    SettingsSection.METADATA -> Res.string.rd_s1_section_metadata
    SettingsSection.ACCOUNTS -> Res.string.rd_s1_section_accounts
    SettingsSection.LINK -> Res.string.rd_s1_section_link
    SettingsSection.NETWORK -> Res.string.rd_s1_section_network
    SettingsSection.TORRENTS -> Res.string.rd_s1_section_torrents
    SettingsSection.DOWNLOADS -> Res.string.rd_s1_section_downloads
    SettingsSection.READER -> Res.string.rd_s1_section_reader
    SettingsSection.DATA -> Res.string.rd_s1_section_data
    SettingsSection.ABOUT -> Res.string.rd_s1_section_about
}

private fun Route.paletteKey(): String = when (this) {
    is Route.Manga -> "manga"
    is Route.Novels -> "novels"
    is Route.Downloads -> "transfers"
    is Route.Profile -> "profile"
    is Route.ImageSearch -> "image"
    else -> toString()
}

private fun Route.paletteSubtitle(): StringResource = when (this) {
    Route.Home -> Res.string.rd_s1_pal_sub_home
    Route.Search -> Res.string.rd_s1_pal_sub_explore
    Route.Calendar -> Res.string.rd_s1_pal_sub_calendar
    Route.Library -> Res.string.rd_s1_pal_sub_library
    is Route.Manga -> Res.string.rd_s1_pal_sub_manga
    is Route.Novels -> Res.string.rd_s1_pal_sub_novels
    is Route.Downloads, is Route.Torrents -> Res.string.rd_s1_pal_sub_transfers
    Route.Extensions -> Res.string.rd_s1_pal_sub_sources
    Route.Linked, Route.Link -> Res.string.rd_s1_pal_sub_link
    is Route.Profile -> Res.string.rd_s1_pal_sub_profile
    Route.ForYou -> Res.string.rd_s1_pal_sub_foryou
    Route.Notifications -> Res.string.rd_s1_pal_sub_notifications
    is Route.ImageSearch -> Res.string.rd_s1_pal_sub_image
    else -> Res.string.rd_s1_pal_sub_settings
}

/** Search words in both languages, so a Spanish user typing "downloads" or an English one typing "descargas" still lands. */
private fun Route.paletteKeywords(): List<String> = when (this) {
    Route.Home -> listOf("home", "inicio", "esta noche", "tonight")
    Route.Search -> listOf("explorar", "explore", "buscar", "search", "genero", "estudio")
    Route.Calendar -> listOf("calendario", "calendar", "agenda", "airing", "emision")
    Route.Library -> listOf("biblioteca", "library", "listas", "lists", "historial")
    is Route.Manga -> listOf("manga", "lectura", "comic")
    is Route.Novels -> listOf("novelas", "novels", "ln", "narrador", "tts")
    is Route.Downloads -> listOf("transferencias", "transfers", "descargas", "downloads", "torrents", "debrid", "magnet")
    Route.Extensions -> listOf("fuentes", "sources", "extensiones", "extensions", "plugins", "repositorios", "kai")
    Route.Linked, Route.Link -> listOf("link", "enlace", "telefono", "phone", "pc", "dispositivo")
    Route.Settings -> listOf("ajustes", "settings", "configuracion")
    is Route.Profile -> listOf("perfil", "profile", "anilist", "social")
    Route.ForYou -> listOf("para ti", "for you", "recomendaciones", "atlas")
    Route.Notifications -> listOf("notificaciones", "notifications", "avisos")
    is Route.ImageSearch -> listOf("imagen", "image", "captura", "screenshot", "trace")
    else -> emptyList()
}

/** Ranked matches (prefix 4 > word start 3 > contains 2 > keyword prefix 2 > anywhere 1), every token must match. */
internal fun rankEntries(rawQuery: String, entries: List<PaletteEntry>): List<PaletteEntry> {
    val q = SettingsCatalog.fold(rawQuery)
    val tokens = q.split(WHITESPACE).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return entries
    return entries.mapNotNull { entry ->
        val title = SettingsCatalog.fold(entry.title)
        val sub = SettingsCatalog.fold(entry.subtitle)
        val kws = entry.keywords.map(SettingsCatalog::fold)
        var score = 0
        for (t in tokens) {
            val ts = when {
                title.startsWith(t) -> 4
                title.split(WORD_SPLIT).any { it.startsWith(t) } -> 3
                t in title -> 2
                kws.any { it.startsWith(t) } -> 2
                t in sub || kws.any { t in it } -> 1
                else -> 0
            }
            if (ts == 0) return@mapNotNull null
            score += ts
        }
        if (title.startsWith(q)) score += 3
        entry to score
    }.sortedByDescending { it.second }.map { it.first }
}

private val WHITESPACE = Regex("\\s+")
private val WORD_SPLIT = Regex("[\\s/()·:\\-]+")
