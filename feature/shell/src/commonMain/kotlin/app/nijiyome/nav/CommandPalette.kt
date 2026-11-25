// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.feature.settings.SettingsCatalog
import app.nijiyome.model.AppSettings
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.ThemeMode

internal enum class PaletteGroup(val label: String) {
    ACTION("Acciones rápidas"),
    NAV("Navegación"),
    SETTING("Ajustes"),
    SEARCH("Catálogo"),
}

internal data class PaletteEntry(
    val id: String,
    val group: PaletteGroup,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String? = null,
    val keywords: List<String> = emptyList(),
    val run: () -> Unit,
)

/**
 * Global Command Palette (`Ctrl+K`) inspired by VS Code, Sublime Text and LiMusic Forge:
 * searches specific settings (jumping right into the Settings modal or screen), quick toggle actions
 * (AMOLED, theme mode, sidebar), navigation destinations, and direct catalog search.
 * Strictly Material Icons (zero decorative emojis) with full keyboard navigation (`↑/↓`, `Enter`, `Esc`).
 */
@Composable
internal fun NijiyomeCommandPalette(
    settings: AppSettings,
    allowedSections: Set<SettingsSection>,
    onDismiss: () -> Unit,
    onNavigate: (Route) -> Unit,
    onOpenSettings: (SettingsSection?) -> Unit,
    onSearchCatalog: (String) -> Unit,
    onToggleAmoled: () -> Unit,
    onCycleThemeMode: () -> Unit,
    onToggleRail: (() -> Unit)?,
) {
    TrackModal()
    var query by remember { mutableStateOf("") }
    var selectedIdx by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val baseEntries = remember(settings.amoled, settings.themeMode, allowedSections) {
        buildList {
            // Quick actions
            add(
                PaletteEntry(
                    id = "act:settings",
                    group = PaletteGroup.ACTION,
                    title = "Abrir Configuración",
                    subtitle = "Ajustes de apariencia, reproductor, red, torrents y diagnóstico",
                    icon = Icons.Outlined.Settings,
                    badge = "Ctrl+P",
                    keywords = listOf("ajustes", "settings", "preferencias", "config"),
                    run = { onOpenSettings(null) },
                ),
            )
            add(
                PaletteEntry(
                    id = "act:amoled",
                    group = PaletteGroup.ACTION,
                    title = if (settings.amoled) "Desactivar Negro puro AMOLED" else "Activar Negro puro AMOLED",
                    subtitle = "Alternar fondo #000000 OLED al instante",
                    icon = Icons.Outlined.DarkMode,
                    badge = if (settings.amoled) "Activo" else "Inactivo",
                    keywords = listOf("amoled", "oled", "negro", "black", "pure"),
                    run = onToggleAmoled,
                ),
            )
            add(
                PaletteEntry(
                    id = "act:theme",
                    group = PaletteGroup.ACTION,
                    title = "Cambiar tema (Sistema / Claro / Oscuro)",
                    subtitle = "Actual: " + when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "Sistema"
                        ThemeMode.LIGHT -> "Claro"
                        ThemeMode.DARK -> "Oscuro"
                    },
                    icon = Icons.Outlined.Tune,
                    keywords = listOf("tema", "dark", "light", "oscuro", "claro", "theme"),
                    run = onCycleThemeMode,
                ),
            )
            if (onToggleRail != null) {
                add(
                    PaletteEntry(
                        id = "act:rail",
                        group = PaletteGroup.ACTION,
                        title = "Alternar barra lateral compacta",
                        subtitle = "Cambiar entre iconos con etiquetas y barra estrecha",
                        icon = Icons.Outlined.ViewSidebar,
                        badge = "Ctrl+B",
                        keywords = listOf("rail", "sidebar", "barra", "menu"),
                        run = onToggleRail,
                    ),
                )
            }
            add(
                PaletteEntry(
                    id = "act:logs",
                    group = PaletteGroup.ACTION,
                    title = "Abrir registros de error (PC + Móvil)",
                    subtitle = "Inspeccionar errores y logs de dispositivos enlazados",
                    icon = Icons.Outlined.Terminal,
                    keywords = listOf("logs", "registros", "errores", "crash", "diagnostico"),
                    run = { onOpenSettings(SettingsSection.ABOUT) },
                ),
            )

            // Navigation destinations
            add(PaletteEntry("nav:home", PaletteGroup.NAV, "Ir a Inicio", "Destacado, continuar viendo y filas", Icons.Outlined.Home, "1", listOf("home", "inicio")) { onNavigate(Route.Home) })
            add(PaletteEntry("nav:search", PaletteGroup.NAV, "Ir a Buscar", "Buscar anime, géneros, estudios e imagen", Icons.Outlined.Search, "2", listOf("buscar", "search")) { onNavigate(Route.Search) })
            add(PaletteEntry("nav:calendar", PaletteGroup.NAV, "Ir a Calendario", "Emisiones semanales, agenda y seguidos", Icons.Outlined.CalendarMonth, "3", listOf("calendario", "agenda", "airing")) { onNavigate(Route.Calendar) })
            add(PaletteEntry("nav:foryou", PaletteGroup.NAV, "Ir a Para ti", "Recomendaciones personalizadas y atlas", Icons.Outlined.AutoAwesome, "4", listOf("para ti", "recomendaciones", "atlas")) { onNavigate(Route.ForYou) })
            add(PaletteEntry("nav:library", PaletteGroup.NAV, "Ir a Biblioteca", "Listas, historial y favoritos", Icons.Outlined.VideoLibrary, "5", listOf("biblioteca", "library", "listas")) { onNavigate(Route.Library) })
            add(PaletteEntry("nav:manga", PaletteGroup.NAV, "Ir a Manga", "Catálogo y lectura de manga", Icons.Outlined.MenuBook, null, listOf("manga", "lectura")) { onNavigate(Route.Manga()) })
            add(PaletteEntry("nav:novels", PaletteGroup.NAV, "Ir a Novelas", "Novelas ligeras y narrador TTS", Icons.Outlined.AutoStories, null, listOf("novelas", "novels", "ln")) { onNavigate(Route.Novels()) })
            add(PaletteEntry("nav:downloads", PaletteGroup.NAV, "Ir a Descargas", "Descargas web y biblioteca local", Icons.Outlined.CloudDownload, null, listOf("descargas", "downloads", "offline")) { onNavigate(Route.Downloads()) })
            add(PaletteEntry("nav:torrents", PaletteGroup.NAV, "Ir a Torrents", "Cliente BitTorrent y servicios Debrid", Icons.Outlined.Download, null, listOf("torrents", "debrid", "magnet")) { onNavigate(Route.Torrents()) })
            add(PaletteEntry("nav:extensions", PaletteGroup.NAV, "Ir a Extensiones", "Repositorios, fuentes y servidores", Icons.Outlined.Extension, null, listOf("extensiones", "plugins", "fuentes")) { onNavigate(Route.Extensions) })
            add(PaletteEntry("nav:profile", PaletteGroup.NAV, "Ir a Perfil", "Actividad social y notificaciones", Icons.Outlined.Person, null, listOf("perfil", "profile", "social", "anilist")) { onNavigate(Route.Profile()) })

            // Specific settings from SettingsCatalog
            SettingsCatalog.items
                .filter { it.section in allowedSections }
                .forEach { item ->
                    add(
                        PaletteEntry(
                            id = "set:${item.id}",
                            group = PaletteGroup.SETTING,
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = Icons.Outlined.Tune,
                            badge = sectionBadge(item.section),
                            keywords = item.keywords,
                            run = { onOpenSettings(item.section) },
                        ),
                    )
                }
        }
    }

    val results = remember(query, baseEntries) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            baseEntries.take(14)
        } else {
            val ranked = rankEntries(trimmed, baseEntries)
            val catalogAction = PaletteEntry(
                id = "search:catalog",
                group = PaletteGroup.SEARCH,
                title = "Buscar «$trimmed» en el catálogo de anime",
                subtitle = "Abrir resultados de búsqueda para «$trimmed»",
                icon = Icons.Outlined.Search,
                badge = "Catálogo",
                run = { onSearchCatalog(trimmed) },
            )
            (ranked.take(16) + catalogAction)
        }
    }

    LaunchedEffect(results.size) {
        selectedIdx = if (results.isEmpty()) 0 else selectedIdx.coerceIn(0, results.lastIndex)
    }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    fun executeSelected() {
        val entry = results.getOrNull(selectedIdx) ?: return
        onDismiss()
        entry.run()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 640.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> {
                            if (results.isNotEmpty()) selectedIdx = (selectedIdx + 1) % results.size
                            true
                        }
                        Key.DirectionUp -> {
                            if (results.isNotEmpty()) selectedIdx = (selectedIdx - 1 + results.size) % results.size
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            executeSelected()
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedIdx = 0
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text("Buscar ajuste, comando, pantalla o anime…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Limpiar")
                                }
                            } else {
                                Pill("Esc", tone = PillTone.NEUTRAL)
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(results, key = { _, item -> item.id }) { idx, item ->
                        val active = idx == selectedIdx
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    if (active) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable {
                                    onDismiss()
                                    item.run()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            item.badge?.let { Pill(it, tone = PillTone.NEUTRAL) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("↑↓ Navegar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Enter Abrir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Ctrl+P Ajustes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f))
                    Text("Esc Cerrar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun sectionBadge(s: SettingsSection): String = when (s) {
    SettingsSection.APPEARANCE -> "Apariencia"
    SettingsSection.PLAYER -> "Reproductor"
    SettingsSection.METADATA -> "Metadatos"
    SettingsSection.ACCOUNTS -> "Cuentas"
    SettingsSection.LINK -> "Link"
    SettingsSection.NETWORK -> "Red"
    SettingsSection.TORRENTS -> "Torrents"
    SettingsSection.DOWNLOADS -> "Descargas"
    SettingsSection.READER -> "Lector"
    SettingsSection.DATA -> "Datos"
    SettingsSection.ABOUT -> "Acerca de"
}

private fun rankEntries(rawQuery: String, entries: List<PaletteEntry>): List<PaletteEntry> {
    val q = SettingsCatalog.fold(rawQuery)
    val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return entries
    return entries.mapNotNull { entry ->
        val title = SettingsCatalog.fold(entry.title)
        val sub = SettingsCatalog.fold(entry.subtitle)
        val kws = entry.keywords.map(SettingsCatalog::fold)
        var score = 0
        for (t in tokens) {
            val ts = when {
                title.startsWith(t) -> 4
                title.split(Regex("[\\s/()·\\-]+")).any { it.startsWith(t) } -> 3
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
