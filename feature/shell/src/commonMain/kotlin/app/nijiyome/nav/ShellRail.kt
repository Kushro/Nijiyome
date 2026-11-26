// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.shell.resources.Res
import app.nijiyome.feature.shell.resources.app_active_downloads
import app.nijiyome.feature.shell.resources.rd_s1_nav_explore
import app.nijiyome.feature.shell.resources.rd_s1_nav_image_search
import app.nijiyome.feature.shell.resources.rd_s1_nav_link
import app.nijiyome.feature.shell.resources.rd_s1_nav_main
import app.nijiyome.feature.shell.resources.rd_s1_nav_sources
import app.nijiyome.feature.shell.resources.rd_s1_nav_transfers
import app.nijiyome.feature.shell.resources.rd_s1_nav_transfers_short
import app.nijiyome.feature.shell.resources.rd_s1_notifications
import app.nijiyome.ui.media.MediaCover
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.nav_home
import app.nijiyome.ui.resources.nav_library
import app.nijiyome.ui.resources.nav_manga
import app.nijiyome.ui.resources.nav_novels
import app.nijiyome.ui.resources.nav_profile
import app.nijiyome.ui.resources.nav_settings
import app.nijiyome.ui.resources.nav_torrents
import app.nijiyome.ui.resources.nav_you
import app.nijiyome.ui.resources.ui_nav_calendar
import app.nijiyome.ui.resources.ui_nav_for_you
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Sparkles rail (`.nj-rail`): the primary group at the top, the utilities pushed to the foot with the account entry under them.
 * [slim] draws icons only, with a tooltip and the label as the accessible name. The whole column scrolls when the window is too short.
 */
@Composable
internal fun ShellRail(
    groups: RailGroups,
    selected: Route?,
    activeDownloads: Int,
    slim: Boolean,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
    /** The linked account's picture for the account entry (the artifact's avatar); null keeps the profile icon. */
    accountAvatar: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val navLabel = stringResource(Res.string.rd_s1_nav_main)
    Row(modifier.fillMaxHeight()) {
        BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .background(scheme.background)
                .decor(DecorRegion.RAIL)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)),
        ) {
            val viewport = maxHeight
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .width(if (slim) RAIL_SLIM_WIDTH else RAIL_WIDTH)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics { contentDescription = navLabel }
                    .selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                groups.primary.forEach { route ->
                    RailItem(route, route == selected, if (route is Route.Downloads) activeDownloads else 0, slim, showLabel = !slim, onSelect)
                }
                // `.nj-rail-sep`: the utilities sit at the foot when there is room.
                Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                groups.utility.forEach { route ->
                    RailItem(route, route == selected, if (route is Route.Downloads) activeDownloads else 0, slim, showLabel = !slim, onSelect)
                }
                groups.account?.let { route ->
                    RailItem(route, route == selected, 0, slim, showLabel = false, onSelect, avatar = accountAvatar)
                }
            }
        }
        VerticalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailItem(
    route: Route,
    selected: Boolean,
    badge: Int,
    slim: Boolean,
    showLabel: Boolean,
    onSelect: (Route) -> Unit,
    avatar: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val label = route.label()
    val shortLabel = route.railLabel()
    val badgeDescription = pluralStringResource(Res.plurals.app_active_downloads, badge, badge)
    val item: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(route) })
                .semantics { contentDescription = if (badge > 0) "$label, $badgeDescription" else label }
                .padding(top = 4.dp, bottom = if (showLabel) 6.dp else 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .size(width = if (slim) 44.dp else 56.dp, height = if (slim) 40.dp else 32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    // The artifact's `--nj-accent-soft` pill: the accent family reads the same in every direction (Obi's secondary is ink).
                    .background(if (selected) scheme.primaryContainer else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                if (!avatar.isNullOrBlank()) {
                    MediaCover(url = avatar, modifier = Modifier.size(30.dp), shape = CircleShape, aspectRatio = 1f)
                } else if (badge > 0) {
                    BadgedBox(badge = { Badge { Text(if (badge > 99) "99+" else badge.toString()) } }) {
                        Icon(route.icon(selected), contentDescription = null, tint = tint)
                    }
                } else {
                    Icon(route.icon(selected), contentDescription = null, tint = tint)
                }
            }
            if (showLabel) {
                Text(
                    shortLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showLabel) {
        item()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.End),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) { item() }
    }
}

private val RAIL_WIDTH = 88.dp
private val RAIL_SLIM_WIDTH = 64.dp

internal fun Route.icon(selected: Boolean): ImageVector = when (this) {
    Route.Home -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Route.Search, is Route.SearchFor -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
    Route.Calendar -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    Route.Library, is Route.LibraryTab -> if (selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary
    is Route.Manga -> if (selected) Icons.Filled.MenuBook else Icons.Outlined.MenuBook
    is Route.Novels -> if (selected) Icons.Filled.AutoStories else Icons.Outlined.AutoStories
    is Route.Downloads -> if (selected) Icons.Filled.SwapVert else Icons.Outlined.SwapVert
    is Route.Torrents -> if (selected) Icons.Filled.Download else Icons.Outlined.Download
    Route.Extensions -> if (selected) Icons.Filled.Extension else Icons.Outlined.Extension
    Route.Linked, Route.Link -> if (selected) Icons.Filled.Devices else Icons.Outlined.Devices
    Route.Settings, is Route.SettingsSection -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    is Route.Profile -> if (selected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle
    Route.ForYou -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    Route.Notifications -> if (selected) Icons.Filled.Notifications else Icons.Outlined.Notifications
    is Route.ImageSearch -> if (selected) Icons.Filled.ImageSearch else Icons.Outlined.ImageSearch
    else -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

/** The destination's full name (accessible name, tooltip, palette). */
@Composable
internal fun Route.label(): String = when (this) {
    Route.Home -> stringResource(UiR.string.nav_home)
    Route.Search, is Route.SearchFor -> stringResource(Res.string.rd_s1_nav_explore)
    Route.Calendar -> stringResource(UiR.string.ui_nav_calendar)
    Route.Library, is Route.LibraryTab -> stringResource(UiR.string.nav_library)
    is Route.Manga -> stringResource(UiR.string.nav_manga)
    is Route.Novels -> stringResource(UiR.string.nav_novels)
    is Route.Downloads -> stringResource(Res.string.rd_s1_nav_transfers)
    is Route.Torrents -> stringResource(UiR.string.nav_torrents)
    Route.Extensions -> stringResource(Res.string.rd_s1_nav_sources)
    Route.Linked, Route.Link -> stringResource(Res.string.rd_s1_nav_link)
    Route.Settings, is Route.SettingsSection -> stringResource(UiR.string.nav_settings)
    is Route.Profile -> stringResource(UiR.string.nav_profile)
    Route.ForYou -> stringResource(UiR.string.ui_nav_for_you)
    Route.Notifications -> stringResource(Res.string.rd_s1_notifications)
    is Route.ImageSearch -> stringResource(Res.string.rd_s1_nav_image_search)
    else -> stringResource(UiR.string.nav_you)
}

/** The label under a rail icon (`Transfer.` where the full name does not fit 72 dp). */
@Composable
internal fun Route.railLabel(): String =
    if (this is Route.Downloads) stringResource(Res.string.rd_s1_nav_transfers_short) else label()
