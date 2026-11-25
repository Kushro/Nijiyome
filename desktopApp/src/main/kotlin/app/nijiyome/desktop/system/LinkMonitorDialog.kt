// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.components.TrackModal
import app.nijiyome.model.LinkActiveSession
import app.nijiyome.model.LinkServerController
import app.nijiyome.model.LinkServerState
import app.nijiyome.nav.LinkMonitorTexts
import app.nijiyome.nav.rememberLinkMonitorTexts
import app.nijiyome.ui.format.formatBytes
import app.nijiyome.ui.format.formatDuration
import app.nijiyome.ui.format.formatRate
import kotlin.math.roundToInt

/** Live torrent entry summary shown in the Link Monitor and the Desktop system tray. */
data class ActiveTorrentSummary(
    val id: String,
    val name: String,
    val progress: Double,
    val downBps: Long,
    val upBps: Long,
    val peers: Int,
    val stateLabel: String,
)

/** Live PC telemetry snapshot shared between the Desktop Tray menu and [LinkMonitorDialog]. */
data class TrayLiveStatus(
    val spanish: Boolean = true,
    val link: LinkServerState? = null,
    val activeTorrents: List<ActiveTorrentSummary> = emptyList(),
    val totalDownBps: Long = 0L,
    val totalUpBps: Long = 0L,
    val jvmUsedBytes: Long = 0L,
    val jvmMaxBytes: Long = 1L,
    val diskCacheBytes: Long = 0L,
    val torrentStorageBytes: Long = 0L,
    val torrentFreeBytes: Long = 0L,
)

/**
 * Dedicated Desktop live monitor for Nijiyome Link sessions (showing which paired device is streaming/upscaling what,
 * active Anime4K / LSFG / encoder pipeline, visual RAM rewind cache window), active torrents, and JVM/cache memory usage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkMonitorDialog(
    status: TrayLiveStatus,
    linkServer: LinkServerController?,
    onDismiss: () -> Unit,
    onOpenLinkSettings: () -> Unit,
    onOpenTorrents: () -> Unit,
    texts: LinkMonitorTexts = rememberLinkMonitorTexts(),
) {
    TrackModal()
    val scheme = MaterialTheme.colorScheme
    val link = status.link

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 520.dp, max = 760.dp)
                .fillMaxWidth(0.86f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainerLow,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    link?.activeSessions?.isNotEmpty() == true -> scheme.primary
                                    link?.running == true -> scheme.tertiary
                                    else -> scheme.outline
                                },
                            ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            texts.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        val subtitle = when {
                            link == null -> texts.unavailable
                            !link.running -> texts.stopped
                            link.activeSessions.isNotEmpty() -> {
                                val n = link.activeSessions.size
                                (if (n == 1) texts.sessionsOne else texts.sessionsOther).format(link.port, n)
                            }
                            else -> texts.idle.format(link.port, link.devices.size)
                        }
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onDismiss(); onOpenLinkSettings() }) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(texts.linkSettings)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = texts.close)
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)

                // Body
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ---- Section 1: Active Link sessions ----
                    SectionHeader(
                        icon = Icons.Outlined.Devices,
                        title = texts.devices,
                    )
                    if (link == null || !link.running) {
                        MonitorCard {
                            Text(
                                texts.serverOff,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    } else if (link.activeSessions.isEmpty()) {
                        MonitorCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    texts.noStreaming,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    buildList {
                                        link.gpu?.let { add("GPU: $it") }
                                        link.encoder?.let { add("Encoder: $it") }
                                        if (link.addresses.isNotEmpty()) add(link.addresses.joinToString(", "))
                                    }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    } else {
                        link.activeSessions.forEach { session ->
                            ActiveLinkSessionCard(
                                session = session,
                                cacheLimitMb = link.cacheLimitMb,
                                texts = texts,
                                onStop = { linkServer?.stopSession(session.sessionId) },
                            )
                        }
                    }

                    // ---- Section 2: Active Torrents ----
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SectionHeader(
                                icon = Icons.Outlined.Download,
                                title = texts.torrents,
                            )
                        }
                        if (status.totalDownBps > 0 || status.totalUpBps > 0) {
                            Text(
                                "↓ ${formatRate(status.totalDownBps)}  ↑ ${formatRate(status.totalUpBps)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.primary,
                            )
                        }
                        TextButton(onClick = { onDismiss(); onOpenTorrents() }) {
                            Text(texts.openTransfers)
                        }
                    }
                    if (status.activeTorrents.isEmpty()) {
                        MonitorCard {
                            Text(
                                texts.noTorrents,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        MonitorCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                status.activeTorrents.take(5).forEachIndexed { idx, t ->
                                    if (idx > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            Text(
                                                "${(t.progress * 100).roundToInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = scheme.primary,
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = { t.progress.toFloat().coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                                        )
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                texts.peers.format(t.stateLabel, t.peers),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = scheme.onSurfaceVariant,
                                            )
                                            Text(
                                                "↓ ${formatRate(t.downBps)} · ↑ ${formatRate(t.upBps)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = scheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---- Section 3: Memory & Cache ----
                    SectionHeader(
                        icon = Icons.Outlined.Memory,
                        title = texts.memory,
                    )
                    MonitorCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Link RAM cache bar + quick size selector
                            val linkCacheLimitBytes = (link?.cacheLimitMb ?: 1024).toLong() * 1024L * 1024L
                            val linkCacheUsed = link?.totalCacheBytes ?: 0L
                            UsageBarRow(
                                label = texts.linkCache,
                                detail = "${formatBytes(linkCacheUsed)} / ${formatBytes(linkCacheLimitBytes)}",
                                fraction = if (linkCacheLimitBytes > 0) (linkCacheUsed.toFloat() / linkCacheLimitBytes.toFloat()).coerceIn(0f, 1f) else 0f,
                                barColor = scheme.primary,
                            )
                            if (linkServer != null && link != null) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        texts.budget,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Segmented(
                                        options = listOf(512, 1024, 2048, 4096).map { mb ->
                                            SegmentedOption(mb, if (mb >= 1024) "${mb / 1024} GB" else "$mb MB")
                                        },
                                        selected = link.cacheLimitMb,
                                        onSelect = linkServer::setCacheLimitMb,
                                        small = true,
                                    )
                                }
                            }
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                            // JVM memory
                            val jvmFrac = (status.jvmUsedBytes.toFloat() / status.jvmMaxBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                            UsageBarRow(
                                label = texts.appRam,
                                detail = "${formatBytes(status.jvmUsedBytes)} / ${formatBytes(status.jvmMaxBytes)}",
                                fraction = jvmFrac,
                                barColor = scheme.secondary,
                            )
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                            // Disk & torrent storage
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                                    Column {
                                        Text(texts.diskCache, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                        Text(formatBytes(status.diskCacheBytes), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                    }
                                }
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                                    Column {
                                        Text(texts.torrentStorage, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                        Text(
                                            buildString {
                                                append(formatBytes(status.torrentStorageBytes))
                                                if (status.torrentFreeBytes > 0) {
                                                    append(" (" + texts.free.format(formatBytes(status.torrentFreeBytes)) + ")")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
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
private fun ActiveLinkSessionCard(
    session: LinkActiveSession,
    cacheLimitMb: Int,
    texts: LinkMonitorTexts,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val displayTitle = buildString {
        append(session.title?.takeIf { it.isNotBlank() } ?: texts.activePlayback)
        session.episode?.let { append(" · Ep $it") }
    }
    MonitorCard(highlighted = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(session.deviceName, scheme.primaryContainer, scheme.onPrimaryContainer)
                        StatusChip(
                            when {
                                session.paused -> texts.paused
                                session.throttled -> texts.bufferFull
                                else -> texts.processing
                            },
                            scheme.secondaryContainer,
                            scheme.onSecondaryContainer,
                        )
                        StatusChip(
                            when (session.sourceKind) {
                                "torrent" -> "Torrent"
                                "library" -> texts.library
                                else -> "Web"
                            },
                            scheme.surfaceContainerHigh,
                            scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        displayTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(texts.stop)
                }
            }

            // Active enhancement pipeline pills
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                session.upscaleLabel?.let {
                    StatusChip(it, scheme.tertiaryContainer, scheme.onTertiaryContainer, icon = Icons.Outlined.AutoAwesome)
                }
                session.frameGenLabel?.let {
                    StatusChip(it, scheme.primaryContainer, scheme.onPrimaryContainer, icon = Icons.Outlined.Speed)
                }
                if (session.upscaleLabel == null && session.frameGenLabel == null) {
                    StatusChip(texts.noUpscaling, scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
                }
                StatusChip(session.encoder, scheme.surfaceContainerHigh, scheme.onSurfaceVariant)
            }

            // Playback + RAM rewind cache window bar
            val dur = session.durationSecs?.takeIf { it > 0.0 }
            if (dur != null) {
                val playFrac = (session.positionSecs / dur).toFloat().coerceIn(0f, 1f)
                val cacheStartFrac = ((session.cachedFromSecs ?: session.positionSecs) / dur).toFloat().coerceIn(0f, 1f)
                val cacheEndFrac = ((session.cachedToSecs ?: session.positionSecs) / dur).toFloat().coerceIn(cacheStartFrac, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val w = size.width
                        val h = size.height
                        val cr = CornerRadius(h / 2f)
                        drawRoundRect(scheme.surfaceContainerHighest, Offset.Zero, Size(w, h), cr)
                        // Cached rewind + ahead window in RAM
                        if (cacheEndFrac > cacheStartFrac) {
                            drawRoundRect(
                                scheme.tertiary.copy(alpha = 0.55f),
                                Offset(w * cacheStartFrac, 0f),
                                Size(w * (cacheEndFrac - cacheStartFrac), h),
                                cr,
                            )
                        }
                        // Played progress
                        drawRoundRect(scheme.primary, Offset.Zero, Size(w * playFrac, h), cr)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${formatDuration(session.positionSecs.toLong())} / ${formatDuration(dur.toLong())}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        val fromSecs = session.cachedFromSecs
                        val toSecs = session.cachedToSecs
                        val cachedWindowText = if (fromSecs != null && toSecs != null) {
                            texts.cachedRam.format("${formatDuration(fromSecs.toLong())}–${formatDuration(toSecs.toLong())}")
                        } else ""
                        Text(
                            cachedWindowText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = scheme.tertiary,
                        )
                    }
                }
            }

            // Numeric telemetry row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricItem(
                    label = texts.ramCache,
                    value = "${formatBytes(session.cachedBytes)} / ${cacheLimitMb} MB",
                )
                MetricItem(
                    label = texts.lead,
                    value = "+${session.leadSecs.roundToInt()} s",
                )
                MetricItem(
                    label = texts.segments,
                    value = "${session.cachedSegments}",
                )
                session.segIntervalMs?.takeIf { it > 0 }?.let { ms ->
                    MetricItem(
                        label = texts.pace,
                        value = String.format("%.1f s/seg", ms / 1000.0),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun MonitorCard(highlighted: Boolean = false, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainer,
        border = BorderStroke(1.dp, if (highlighted) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) { content() }
    }
}

@Composable
private fun StatusChip(
    text: String,
    bg: Color,
    fg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = bg, contentColor = fg) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun UsageBarRow(label: String, detail: String, fraction: Float, barColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
    }
}
