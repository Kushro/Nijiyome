// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.BrandMark
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalDirection
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.rd_s1_cmd_field
import app.nijiyome.feature.shell.resources.rd_s1_medium_anime
import app.nijiyome.feature.shell.resources.rd_s1_medium_label
import app.nijiyome.feature.shell.resources.rd_s1_medium_manga
import app.nijiyome.feature.shell.resources.rd_s1_medium_novel
import app.nijiyome.feature.shell.resources.rd_s1_notifications
import app.nijiyome.model.MediaKind
import app.nijiyome.model.NavStyle
import org.jetbrains.compose.resources.stringResource

/** The medium the wide navigation is browsing (Sparkles "Por medio": Anime · Manga · Novela). */
enum class ShellMedium { ANIME, MANGA, NOVEL }

/**
 * The medium of the screen on top, for Inicio, Explorar and Biblioteca to follow when the navigation is [NavStyle.MEDIUM].
 * Provided by the shell; defined here until the integrator lifts it to a shared module.
 */
val LocalShellMedium: ProvidableCompositionLocal<ShellMedium> = compositionLocalOf { ShellMedium.ANIME }

/** Opens the command palette (Ctrl+K). Null where the shell is not composed (previews, tests). Any screen may offer it. */
val LocalOpenCommandPalette: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/** The medium a route belongs to; null for routes of no medium (Ajustes, Transferencias, Fuentes...), which keep the previous one. */
fun Route.medium(): ShellMedium? = when (this) {
    is Route.Manga, is Route.MangaReader -> ShellMedium.MANGA
    is Route.Details -> if (kind == MediaKind.MANGA) ShellMedium.MANGA else ShellMedium.ANIME
    is Route.Novels, is Route.NovelDetails, is Route.NovelReader -> ShellMedium.NOVEL
    Route.Home, Route.Search, is Route.SearchFor, is Route.ImageSearch, Route.Calendar, Route.ForYou, Route.Library, is Route.LibraryTab,
    is Route.Player, is Route.Character -> ShellMedium.ANIME
    else -> null
}

/** The home of a medium: where the selector goes. */
fun ShellMedium.home(): Route = when (this) {
    ShellMedium.ANIME -> Route.Home
    ShellMedium.MANGA -> Route.Manga()
    ShellMedium.NOVEL -> Route.Novels()
}

/**
 * What the window's own top bar (desktop title bar, drawn by the host outside the shell's back stack) needs from the shell: the
 * navigation style, the medium and the actions behind the selector, the command field and the bell. The shell writes it; the host's
 * bar reads it through [ShellMediumSelector], [ShellCommandField] and [ShellNotificationsButton].
 */
@Stable
class ShellChrome internal constructor() {
    /** The shell (not the first-run wizard) is on screen. */
    var ready: Boolean by mutableStateOf(false)
        internal set
    var nav: NavStyle by mutableStateOf(NavStyle.RAIL)
        internal set

    /** The rail layout is in use (desktop always; tablets). The navigation style only applies then. */
    var wide: Boolean by mutableStateOf(false)
        internal set

    /** The player or a reader is on top: the bar keeps only the window controls. */
    var viewer: Boolean by mutableStateOf(false)
        internal set
    var medium: ShellMedium by mutableStateOf(ShellMedium.ANIME)
        internal set
    internal var selectMedium: (ShellMedium) -> Unit = {}
    internal var openPalette: () -> Unit = {}
    internal var openNotifications: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
}

val LocalShellChrome: ProvidableCompositionLocal<ShellChrome?> = staticCompositionLocalOf { null }

/** Anime · Manga · Novela segmented selector; drawn only with [NavStyle.MEDIUM] on wide windows. */
@Composable
fun ShellMediumSelector(modifier: Modifier = Modifier, height: Dp = 28.dp) {
    val chrome = LocalShellChrome.current ?: return
    if (!chrome.ready || chrome.viewer || !chrome.wide || chrome.nav != NavStyle.MEDIUM) return
    MediumSegments(current = chrome.medium, onSelect = chrome.selectMedium, modifier = modifier, height = height)
}

@Composable
internal fun MediumSegments(current: ShellMedium, onSelect: (ShellMedium) -> Unit, modifier: Modifier = Modifier, height: Dp = 28.dp) {
    val scheme = MaterialTheme.colorScheme
    val groupLabel = stringResource(Res.string.rd_s1_medium_label)
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(999.dp))
            .padding(2.dp)
            .semantics { contentDescription = groupLabel }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ShellMedium.entries.forEach { m ->
            val selected = m == current
            val label = stringResource(
                when (m) {
                    ShellMedium.ANIME -> Res.string.rd_s1_medium_anime
                    ShellMedium.MANGA -> Res.string.rd_s1_medium_manga
                    ShellMedium.NOVEL -> Res.string.rd_s1_medium_novel
                },
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) scheme.secondaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { if (!selected) onSelect(m) })
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The "Search or run a command · Ctrl K" field that opens the palette. With [NavStyle.COMMAND] it is the centre of the bar (wider);
 * otherwise the compact pill at the end. Hidden over the player and the readers.
 */
@Composable
fun ShellCommandField(modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val chrome = LocalShellChrome.current ?: return
    if (!chrome.ready || chrome.viewer) return
    val centre = chrome.wide && chrome.nav == NavStyle.COMMAND
    CommandFieldPill(onClick = chrome.openPalette, centre = centre, modifier = modifier, height = height)
}

/** True when the command field is the centre of the bar: the host adds room on both sides of it. */
val shellCommandCentred: Boolean
    @Composable get() = LocalShellChrome.current?.let { it.ready && !it.viewer && it.wide && it.nav == NavStyle.COMMAND } == true

@Composable
internal fun CommandFieldPill(onClick: () -> Unit, centre: Boolean, modifier: Modifier = Modifier, height: Dp = 30.dp) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .height(height)
            .then(if (centre) Modifier.widthIn(min = 320.dp, max = 560.dp).fillMaxWidth() else Modifier.widthIn(min = 220.dp)),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                stringResource(Res.string.rd_s1_cmd_field),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = centre),
            )
            KeyCap("Ctrl K")
        }
    }
}

/** A keyboard key as text (`Ctrl K`, `Esc`, `1-9`). */
@Composable
internal fun KeyCap(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (color == Color.Unspecified) scheme.onSurfaceVariant else color
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = fg,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** The bell of the top bar (AniList notifications), when this device can open them. */
@Composable
fun ShellNotificationsButton(modifier: Modifier = Modifier) {
    val chrome = LocalShellChrome.current ?: return
    val open = chrome.openNotifications
    if (!chrome.ready || chrome.viewer || open == null) return
    IconButton(onClick = open, modifier = modifier) {
        Icon(Icons.Outlined.Notifications, contentDescription = stringResource(Res.string.rd_s1_notifications), modifier = Modifier.size(20.dp))
    }
}

/**
 * The shell's own top bar on wide windows that have no host bar (tablets, desktop with the native title bar) when the navigation
 * style needs one: the medium selector or the centred command field.
 */
@Composable
internal fun ShellTopBar(modifier: Modifier = Modifier) {
    val centred = shellCommandCentred
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .decor(DecorRegion.TOP_BAR)
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (LocalDirection.current.decor.hasBrandMark) BrandMark(Modifier.size(26.dp))
        ShellMediumSelector(height = 40.dp)
        Box(Modifier.weight(1f))
        ShellCommandField(height = 40.dp)
        if (centred) Box(Modifier.weight(1f))
        ShellNotificationsButton()
    }
}
