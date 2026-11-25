// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.LocalSignalColors
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.RegionData
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.feature.home.resources.Res
import app.nijiyome.feature.home.resources.*
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkServerState
import app.nijiyome.model.LinkState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

// Sparkles redesign, lane S2: the side column of "Esta noche" (artifact `linkPanel`, `transfersPanel`): Nijiyome Link at a glance and
// the active downloads. Read-only views of the Link server (desktop) or the Link client (phone); actions open their screens.

/** Panel "Nijiyome Link": nothing when this build has no Link at all. [compact] = the phone strip. */
@Composable
internal fun LinkPanel(onOpenLink: () -> Unit, onOpenMonitor: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val koin = getKoin()
    val server = remember(koin) { runCatching { koin.getOrNull<LinkServerController>() }.getOrNull() }
    val client = remember(koin) { if (server != null) null else runCatching { koin.getOrNull<LinkRepository>() }.getOrNull() }
    when {
        server != null -> {
            val st by server.state.collectAsStateWithLifecycle()
            ServerPanel(st, onOpenLink, onOpenMonitor, modifier, compact)
        }
        client != null -> {
            val st by client.state.collectAsStateWithLifecycle()
            ClientPanel(st, onOpenLink, modifier)
        }
    }
}

@Composable
private fun PanelSurface(modifier: Modifier, region: DecorRegion?, data: RegionData = RegionData(), content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().then(if (region != null) Modifier.decor(region, data) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun PanelHead(title: String, status: String?, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (status != null) StatusDot(status, ok)
    }
}

@Composable
private fun StatusDot(text: String, ok: Boolean) {
    val signals = LocalSignalColors.current
    val color = if (ok) signals.ok else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (ok) signals.ok else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServerPanel(st: LinkServerState, onOpenLink: () -> Unit, onOpenMonitor: () -> Unit, modifier: Modifier, compact: Boolean) {
    val online = st.devices.filter { it.online }
    val status = when {
        !st.enabled -> stringResource(Res.string.rd_s2_link_off)
        !st.running -> stringResource(Res.string.rd_s2_link_starting)
        online.isNotEmpty() -> stringResource(Res.string.rd_s2_link_connected)
        else -> stringResource(Res.string.rd_s2_link_waiting)
    }
    PanelSurface(modifier, DecorRegion.LINK, RegionData(connected = st.running && online.isNotEmpty())) {
        PanelHead(stringResource(Res.string.rd_s2_link_title), status, ok = st.running && online.isNotEmpty())
        val sessions = st.activeSessions
        if (!compact) {
            st.devices.take(MAX_DEVICES).forEach { device ->
                val session = sessions.firstOrNull { it.deviceId == device.id }
                val watching = session?.title
                val episode = session?.episode
                val line = when {
                    watching != null && episode != null -> stringResource(Res.string.rd_s2_link_watching_ep, watching, episode)
                    watching != null -> stringResource(Res.string.rd_s2_link_watching, watching)
                    device.online -> stringResource(Res.string.rd_s2_link_device_online)
                    else -> stringResource(Res.string.rd_s2_link_device_offline)
                }
                DeviceLine(device.name, line, phone = device.platform.contains("android", ignoreCase = true), on = device.online)
            }
            if (st.devices.isEmpty()) {
                Text(stringResource(Res.string.rd_s2_link_no_devices), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sessions.isNotEmpty()) {
                val limit = st.cacheLimitMb.toLong() * MIB * sessions.size
                val used = st.totalCacheBytes
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.rd_s2_link_cache), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(Res.string.rd_s2_link_cache_value, used / MIB, limit / MIB),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    ProgressTrack(progress = if (limit > 0) used.toFloat() / limit else 0f, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = onOpenLink) {
                Icon(Icons.Outlined.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s2_link_open), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onOpenMonitor) {
                Icon(Icons.Outlined.MonitorHeart, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.rd_s2_link_monitor), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun DeviceLine(name: String, line: String, phone: Boolean, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (phone) Icons.Outlined.Smartphone else Icons.Outlined.DesktopWindows,
            contentDescription = null,
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Phone: one tappable line "Link con <PC>" with its state, or the way to pair. */
@Composable
private fun ClientPanel(st: LinkState, onOpenLink: () -> Unit, modifier: Modifier) {
    val (title, line, ok) = when (st) {
        is LinkState.Paired -> Triple(
            stringResource(Res.string.rd_s2_link_with, st.pc.serverName),
            stringResource(if (st.reachable) Res.string.rd_s2_link_connected else Res.string.rd_s2_link_unreachable),
            st.reachable,
        )
        LinkState.Pairing -> Triple(stringResource(Res.string.rd_s2_link_title), stringResource(Res.string.rd_s2_link_pairing), false)
        is LinkState.Failed -> Triple(stringResource(Res.string.rd_s2_link_title), st.message, false)
        LinkState.Unpaired -> Triple(stringResource(Res.string.rd_s2_link_title), stringResource(Res.string.rd_s2_link_pair_hint), false)
    }
    Surface(
        onClick = onOpenLink,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).decor(DecorRegion.LINK, RegionData(connected = ok)),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val signals = LocalSignalColors.current
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) signals.ok else Color.Transparent))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Panel "Descargando (N)": the active downloads, each opening Transferencias. */
@Composable
internal fun TransfersPanel(activity: DownloadActivity?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val total = activity?.total ?: 0
    PanelSurface(modifier, region = null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (total > 0) stringResource(Res.string.home_downloading, total) else stringResource(Res.string.rd_s2_transfers_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) { Text(stringResource(Res.string.home_see_all)) }
        }
        if (activity == null || total == 0) {
            Text(stringResource(Res.string.rd_s2_transfers_idle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            activity.web.take(MAX_DOWNLOADS).forEach { item ->
                DownloadItemCard(
                    item.poster, item.title, stringResource(Res.string.home_downloading_episode, item.episode), item.progress.toFloat(),
                    web = true, onClick = onOpen, modifier = Modifier.fillMaxWidth(),
                )
            }
            activity.torrents.take(MAX_DOWNLOADS).forEach { t ->
                DownloadItemCard(
                    t.poster,
                    t.series ?: t.name,
                    t.episode?.let { stringResource(Res.string.home_downloading_episode, it) } ?: stringResource(Res.string.home_downloading_torrent),
                    t.progress.toFloat(),
                    web = false,
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val MAX_DEVICES = 3
private const val MAX_DOWNLOADS = 4
private const val MIB = 1024L * 1024L
