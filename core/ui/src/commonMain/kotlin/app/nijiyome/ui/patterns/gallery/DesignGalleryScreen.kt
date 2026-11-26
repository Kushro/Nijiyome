// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.nijiyome.ui.patterns.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.designsystem.direction.DirectionPalette
import app.nijiyome.designsystem.direction.DirectionSpec
import app.nijiyome.designsystem.direction.Directions
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.EpisodeMark
import app.nijiyome.designsystem.direction.EpisodeMarkState
import app.nijiyome.designsystem.direction.LocalDirection
import app.nijiyome.designsystem.direction.LocalThemeTone
import app.nijiyome.designsystem.direction.ProgressTrack
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.StatusMark
import app.nijiyome.designsystem.direction.StatusSlot
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.motion.CardToDetailsHost
import app.nijiyome.designsystem.motion.ControlsEdge
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.LocalMotion
import app.nijiyome.designsystem.motion.PlayerControlsTransition
import app.nijiyome.designsystem.motion.StatusChangeTransition
import app.nijiyome.designsystem.motion.WatchedTransition
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.designsystem.motion.cardToDetailsTarget
import app.nijiyome.designsystem.motion.homeEntrance
import app.nijiyome.model.MotionMoments
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.CardShow
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.TitleCardStyle
import app.nijiyome.ui.cards.TitleCards
import app.nijiyome.ui.look.LocalLook
import app.nijiyome.ui.look.effectiveCardId
import app.nijiyome.ui.patterns.UndoSnackbar
import app.nijiyome.ui.patterns.isCompactWindow
import app.nijiyome.ui.patterns.label
import app.nijiyome.ui.patterns.rememberUndoController
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_gal_active
import app.nijiyome.ui.resources.rd_i1_gal_back
import app.nijiyome.ui.resources.rd_i1_gal_cards
import app.nijiyome.ui.resources.rd_i1_gal_cards_hint
import app.nijiyome.ui.resources.rd_i1_gal_decor
import app.nijiyome.ui.resources.rd_i1_gal_decor_empty
import app.nijiyome.ui.resources.rd_i1_gal_decor_episode
import app.nijiyome.ui.resources.rd_i1_gal_decor_progress
import app.nijiyome.ui.resources.rd_i1_gal_decor_section
import app.nijiyome.ui.resources.rd_i1_gal_decor_status
import app.nijiyome.ui.resources.rd_i1_gal_directions
import app.nijiyome.ui.resources.rd_i1_gal_directions_hint
import app.nijiyome.ui.resources.rd_i1_gal_intro
import app.nijiyome.ui.resources.rd_i1_gal_m_carga
import app.nijiyome.ui.resources.rd_i1_gal_m_controles
import app.nijiyome.ui.resources.rd_i1_gal_m_estado
import app.nijiyome.ui.resources.rd_i1_gal_m_ficha
import app.nijiyome.ui.resources.rd_i1_gal_m_inicio
import app.nijiyome.ui.resources.rd_i1_gal_m_snack
import app.nijiyome.ui.resources.rd_i1_gal_m_tanzaku
import app.nijiyome.ui.resources.rd_i1_gal_m_visto
import app.nijiyome.ui.resources.rd_i1_gal_motion
import app.nijiyome.ui.resources.rd_i1_gal_motion_level
import app.nijiyome.ui.resources.rd_i1_gal_play
import app.nijiyome.ui.resources.rd_i1_gal_sample_section
import app.nijiyome.ui.resources.rd_i1_gal_see_all
import app.nijiyome.ui.resources.rd_i1_gal_snack_msg
import app.nijiyome.ui.resources.rd_i1_gal_title
import app.nijiyome.ui.resources.rd_i1_gal_variant
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign, lane I1. The design gallery: for the active theme, every card style in the six contexts, every direction with its
// palettes, the decoration slots and one trigger per motion moment, then live demos of the interaction patterns. For the user to preview
// choices and for a visual reviewer. Data comes from parameters or the bundled samples, never from a repository.

/**
 * The design gallery screen. The shell opens it from Ajustes › Apariencia (or a debug entry) and passes [onBack].
 *
 * ```kotlin
 * DesignGalleryScreen(onBack = { nav.pop() })
 * ```
 */
@Composable
fun DesignGalleryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    cards: List<TitleCardData> = gallerySampleCards(),
) {
    val undo = rememberUndoController()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.rd_i1_gal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.rd_i1_gal_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { Intro() }
                item { GalleryHeading(stringResource(Res.string.rd_i1_gal_directions), stringResource(Res.string.rd_i1_gal_directions_hint)) }
                items(Directions.all, key = { "dir:" + it.id }) { DirectionEntry(it) }
                item { GalleryHeading(stringResource(Res.string.rd_i1_gal_cards), stringResource(Res.string.rd_i1_gal_cards_hint)) }
                items(TitleCards.all, key = { "card:" + it.id }) { CardStyleEntry(it, cards) }
                item { GalleryHeading(stringResource(Res.string.rd_i1_gal_decor), null) }
                item { DecorSection() }
                item { GalleryHeading(stringResource(Res.string.rd_i1_gal_motion), stringResource(Res.string.rd_i1_gal_motion_level, LocalMotion.current.level.name)) }
                item { MotionSection(onSnack = { msg -> undo.show(msg, undo = {}) }) }
                item { PatternsGallery(cards, undo) }
            }
            UndoSnackbar(undo, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** A section title of the gallery, drawn with the active direction's [SectionHeader]. */
@Composable
internal fun GalleryHeading(title: String, hint: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title, Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A labelled block inside a section. */
@Composable
internal fun GalleryBlock(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
internal fun CodeTag(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Intro() {
    val direction = LocalDirection.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.rd_i1_gal_intro), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(direction.name(), tone = PillTone.PRIMARY)
            Pill(LocalThemeTone.current.name, tone = PillTone.NEUTRAL)
            Pill(LocalLook.current.effectiveCardId(), tone = PillTone.NEUTRAL)
            Pill(LocalMotion.current.level.name, tone = PillTone.NEUTRAL)
        }
    }
}

@Composable
private fun DirectionEntry(spec: DirectionSpec) {
    val active = LocalDirection.current.id == spec.id
    val tone = LocalThemeTone.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(spec.name(), style = MaterialTheme.typography.titleMedium)
            CodeTag(spec.id)
            if (active) Pill(stringResource(Res.string.rd_i1_gal_active), tone = PillTone.OK)
        }
        val tagline = spec.tagline()
        if (tagline.isNotBlank()) Text(tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            spec.palettes.forEach { PaletteChip(it, tone) }
        }
        val status = spec.palettes.first().status(tone)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusSlot.entries.forEach { slot -> Box(Modifier.size(14.dp).clip(CircleShape).background(status.of(slot))) }
        }
    }
}

@Composable
private fun PaletteChip(palette: DirectionPalette, tone: app.nijiyome.designsystem.direction.ThemeTone) {
    val scheme = palette.scheme(tone, ContrastLevel.Standard)
    Column(
        Modifier.width(132.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp).clip(MaterialTheme.shapes.extraSmall)) {
            listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surface, scheme.surfaceContainerHigh).forEach { c ->
                Box(Modifier.weight(1f).height(24.dp).background(c))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(palette.swatch))
            Text(palette.name(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun CardStyleEntry(style: TitleCardStyle, cards: List<TitleCardData>) {
    val active = LocalLook.current.effectiveCardId() == style.id
    val size = LocalLook.current.cardSize
    val compact = isCompactWindow()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(style.name(), style = MaterialTheme.typography.titleMedium)
            CodeTag(style.id)
            if (active) Pill(stringResource(Res.string.rd_i1_gal_active), tone = PillTone.OK)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CardContext.entries.forEachIndexed { i, context ->
                val data = cards[i % cards.size]
                val width = when (context) {
                    CardContext.GRID -> 160.dp
                    CardContext.COMPACT -> if (compact) 280.dp else 340.dp
                    CardContext.HERO -> if (compact) 300.dp else 480.dp
                    else -> null
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodeTag(context.name)
                    style.Card(
                        data,
                        context,
                        size,
                        CardShow(progress = true, status = true, next = true, score = true),
                        {},
                        if (width != null) Modifier.width(width) else Modifier,
                        null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DecorSection() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        GalleryBlock(stringResource(Res.string.rd_i1_gal_decor_section)) {
            SectionHeader(stringResource(Res.string.rd_i1_gal_sample_section), trailing = {
                TextButton(shape = NijiyomeControls.shape, onClick = {}) { Text(stringResource(Res.string.rd_i1_gal_see_all)) }
            })
        }
        GalleryBlock(stringResource(Res.string.rd_i1_gal_decor_episode)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                EpisodeMarkState.entries.forEachIndexed { i, state ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EpisodeMark(state, number = i + 1)
                        CodeTag(state.name)
                    }
                }
            }
        }
        GalleryBlock(stringResource(Res.string.rd_i1_gal_decor_status)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusSlot.entries.forEach { slot -> StatusMark(slot, slot.label()) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusSlot.entries.forEach { slot -> StatusMark(slot, slot.label(), selected = true) }
            }
        }
        GalleryBlock(stringResource(Res.string.rd_i1_gal_decor_progress)) {
            ProgressTrack(progress = 4 / 12f, modifier = Modifier.fillMaxWidth(), segments = 12)
            ProgressTrack(progress = 0.8f, modifier = Modifier.fillMaxWidth())
        }
        GalleryBlock(stringResource(Res.string.rd_i1_gal_decor_empty)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EmptyKind.entries.forEach { kind ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.size(120.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center,
                        ) { EmptyArt(kind, Modifier.size(96.dp)) }
                        CodeTag(kind.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentRow(moment: String, name: String, onPlay: () -> Unit, demo: @Composable () -> Unit) {
    val variant = LocalMotion.current.variant(moment, MotionMoments.VARIANTS[moment]?.firstOrNull().orEmpty())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            CodeTag(stringResource(Res.string.rd_i1_gal_variant, moment, variant))
            FilledTonalButton(shape = NijiyomeControls.shape, onClick = onPlay) { Text(stringResource(Res.string.rd_i1_gal_play)) }
        }
        Box(Modifier.fillMaxWidth().height(132.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(12.dp)) {
            demo()
        }
    }
}

@Composable
private fun MotionSection(onSnack: (String) -> Unit) {
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primaryContainer
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        var replay by remember { mutableIntStateOf(0) }
        MomentRow(MotionMoments.HOME_ENTRANCE, stringResource(Res.string.rd_i1_gal_m_inicio), onPlay = { replay++ }) {
            key(replay) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i -> Box(Modifier.homeEntrance(i).fillMaxWidth().height(28.dp).clip(MaterialTheme.shapes.small).background(block)) }
                }
            }
        }
        var sway by remember { mutableStateOf(true) }
        MomentRow(MotionMoments.CARD_SWAY, stringResource(Res.string.rd_i1_gal_m_tanzaku), onPlay = { sway = !sway }) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(5) { i ->
                    Box((if (sway) Modifier.cardSway(i) else Modifier).size(width = 48.dp, height = 96.dp).clip(MaterialTheme.shapes.small).background(accent))
                }
            }
        }
        var open by remember { mutableStateOf(false) }
        MomentRow(MotionMoments.CARD_TO_DETAILS, stringResource(Res.string.rd_i1_gal_m_ficha), onPlay = { open = !open }) {
            CardToDetailsHost {
                if (open) {
                    Box(Modifier.cardToDetailsTarget("gallery:ficha").fillMaxWidth().height(104.dp).clip(MaterialTheme.shapes.medium).background(accent))
                } else {
                    Box(Modifier.cardToDetailsSource("gallery:ficha").size(width = 64.dp, height = 96.dp).clip(MaterialTheme.shapes.small).background(accent))
                }
            }
        }
        var watched by remember { mutableStateOf(false) }
        MomentRow(MotionMoments.MARK_WATCHED, stringResource(Res.string.rd_i1_gal_m_visto), onPlay = { watched = !watched }) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                WatchedTransition(watched) { w -> EpisodeMark(if (w) EpisodeMarkState.WATCHED else EpisodeMarkState.AIRED, number = 4) }
                CodeTag(if (watched) EpisodeMarkState.WATCHED.name else EpisodeMarkState.AIRED.name)
            }
        }
        var controls by remember { mutableStateOf(true) }
        MomentRow(MotionMoments.PLAYER_CONTROLS, stringResource(Res.string.rd_i1_gal_m_controles), onPlay = { controls = !controls }) {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).background(Color.Black)) {
                val bar = Modifier.fillMaxWidth().height(20.dp).background(Color.White.copy(alpha = 0.35f))
                PlayerControlsTransition(controls, ControlsEdge.TOP, Modifier.align(Alignment.TopCenter)) { Box(bar) }
                PlayerControlsTransition(controls, ControlsEdge.CENTER, Modifier.align(Alignment.Center)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
                }
                PlayerControlsTransition(controls, ControlsEdge.BOTTOM, Modifier.align(Alignment.BottomCenter)) { Box(bar) }
            }
        }
        var loading by remember { mutableStateOf(true) }
        MomentRow(MotionMoments.LOADING, stringResource(Res.string.rd_i1_gal_m_carga), onPlay = { loading = !loading }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    if (loading) {
                        LoadingPlaceholder(Modifier.size(width = 72.dp, height = 104.dp))
                    } else {
                        Box(Modifier.size(width = 72.dp, height = 104.dp).clip(MaterialTheme.shapes.medium).background(accent))
                    }
                }
            }
        }
        var status by remember { mutableIntStateOf(0) }
        MomentRow(MotionMoments.STATUS_CHANGE, stringResource(Res.string.rd_i1_gal_m_estado), onPlay = { status = (status + 1) % StatusSlot.entries.size }) {
            StatusChangeTransition(StatusSlot.entries[status]) { slot -> StatusMark(slot, slot.label(), selected = true) }
        }
        val snackMessage = stringResource(Res.string.rd_i1_gal_snack_msg)
        MomentRow(MotionMoments.SNACKBAR, stringResource(Res.string.rd_i1_gal_m_snack), onPlay = { onSnack(snackMessage) }) {
            Text(snackMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
