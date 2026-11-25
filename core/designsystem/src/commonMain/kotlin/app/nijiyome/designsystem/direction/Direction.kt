// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.designsystem.direction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.generated.ContrastLevel
import app.nijiyome.designsystem.generated.NijiyomeTokens

// Sparkles redesign contract (docs/redesign/CONTRATO-COMPOSE.md). Owner: integrator. FROZEN: names and signatures do not change.
// A direction is a theme pack plus a set of decoration slots. Screens never ask which direction is active: they use MaterialTheme,
// the locals below and the slot functions at the end of this file.

/** Light, dark, or dark on pure black. */
enum class ThemeTone { LIGHT, DARK, OLED }

/** FREE = new identity, IDENTITY = keeps the kitsune ensō / 虹 seal, CANON = plain Material 3. */
enum class DirectionTrack { FREE, IDENTITY, CANON }

/** List status, in the design system's own words (`:core:designsystem` does not see `:core:model`; `:core:ui` maps `ListStatus`). */
enum class StatusSlot { WATCHING, PLANNING, COMPLETED, PAUSED, DROPPED, REWATCHING }

/** One color per list status (artifact `--nj-st-*`). Each must reach 3:1 against the surface it sits on, and is never the only cue. */
@Immutable
data class StatusColors(
    val watching: Color,
    val planning: Color,
    val completed: Color,
    val paused: Color,
    val dropped: Color,
    val rewatching: Color,
) {
    fun of(slot: StatusSlot): Color = when (slot) {
        StatusSlot.WATCHING -> watching
        StatusSlot.PLANNING -> planning
        StatusSlot.COMPLETED -> completed
        StatusSlot.PAUSED -> paused
        StatusSlot.DROPPED -> dropped
        StatusSlot.REWATCHING -> rewatching
    }
}

/** Success / warning / error / info roles (artifact `--nj-ok --nj-warn --nj-err --nj-info`), readable as text on the surface. */
@Immutable
data class SignalColors(val ok: Color, val warn: Color, val err: Color, val info: Color)

/** One selectable palette of a direction. Must answer every tone and contrast level. */
interface DirectionPalette {
    val id: String

    /** The dot shown in the palette picker. */
    val swatch: Color

    @Composable fun name(): String

    fun scheme(tone: ThemeTone, contrast: ContrastLevel): ColorScheme

    fun status(tone: ThemeTone): StatusColors

    fun signal(tone: ThemeTone): SignalColors
}

/** Where a direction may decorate (artifact `data-region`). */
enum class DecorRegion {
    /** The whole window behind the content (paper, night sky). */
    APP_BACKGROUND,
    RAIL,
    TOP_BAR,

    /** Highlighted header of Home or Details. */
    HERO,

    /** The "Esta noche" / continue row. */
    TONIGHT,

    /** Any horizontal row of cards. */
    ROW,
    GRID,

    /** Episode list container. */
    EPISODES,

    /** Airing agenda / calendar. */
    AIRING,
    STATUS_PICKER,
    LINK,
    PLAYER_SEEK,
    EMPTY,

    /** Sheets and side panels. */
    SHEET,
}

/** State of one episode in a list, for [DirectionDecor.EpisodeMark]. */
enum class EpisodeMarkState { UNAIRED, AIRED, IN_PROGRESS, WATCHED }

/** What an empty state is about, for [DirectionDecor.EmptyArt]. */
enum class EmptyKind { LIBRARY, SEARCH, DOWNLOADS, SOURCES, CALENDAR, LINK, ERROR, GENERIC }

/**
 * Decoration slots. Every function has a plain Material default, so a direction overrides only what it changes. A direction changes
 * the material (texture, line, seal, type), never the organisation of a screen. Under the PERF motion level
 * (`LocalMotion.current.level`) an implementation drops textures, blur and continuous drawing.
 */
interface DirectionDecor {
    /** Background, texture or drawing of a region; applied by the screen as `Modifier.decor(region)`. */
    @Composable
    fun regionModifier(region: DecorRegion): Modifier = Modifier

    /** Title of a section or card row, with an optional trailing action ("Ver todo"). */
    @Composable
    fun SectionHeader(title: String, modifier: Modifier, trailing: (@Composable () -> Unit)?) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
        }
    }

    /** The mark beside an episode (station, seal, strip). [number] is the episode number when the mark may print it. */
    @Composable
    fun EpisodeMark(state: EpisodeMarkState, number: Int?, modifier: Modifier) {
        val color = when (state) {
            EpisodeMarkState.WATCHED -> MaterialTheme.colorScheme.primary
            EpisodeMarkState.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
            EpisodeMarkState.AIRED -> MaterialTheme.colorScheme.outline
            EpisodeMarkState.UNAIRED -> MaterialTheme.colorScheme.outlineVariant
        }
        Box(modifier.size(10.dp).clip(CircleShape).background(color))
    }

    /** A list status shown as a mark with its [label] (chip, seal, ribbon). [selected] = it is the title's current status. */
    @Composable
    fun StatusMark(status: StatusSlot, label: String, selected: Boolean, modifier: Modifier) {
        val color = LocalStatusColors.current.of(status)
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    /** Watch / read / download progress, 0..1. [segments] = episode count when the direction may draw one tick per episode. */
    @Composable
    fun ProgressTrack(progress: Float, modifier: Modifier, segments: Int?) {
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier)
    }

    /** Illustration of an empty state; the default draws nothing (the screen still shows its text and action). */
    @Composable
    fun EmptyArt(kind: EmptyKind, modifier: Modifier) {
    }

    /** true when [BrandMark] draws the direction's own logo; the shell then shows it instead of the app logo. */
    val hasBrandMark: Boolean get() = false

    /** The direction's logo in the top bar / title bar; the default draws nothing (the shell keeps the app logo). */
    @Composable
    fun BrandMark(modifier: Modifier) {
    }

    /** The plain Material decoration. */
    object Plain : DirectionDecor
}

/** A visual direction: palettes, type, shape and decoration. Implementations are `object`s listed in [Directions]. */
interface DirectionSpec {
    /** `DesignDirection.id` of `:core:model` (`tanabata`, `obi`, `transit`, `sumi`, `canon`). */
    val id: String
    val track: DirectionTrack

    /** At least one; the first is the default. */
    val palettes: List<DirectionPalette>

    @Composable fun name(): String

    /** One line shown under the name in the picker. */
    @Composable fun tagline(): String

    /** The type scale of this direction, built on [base] (the app's generated scale). Fonts load from this module's `composeResources/font`. */
    @Composable
    fun typography(base: Typography): Typography = base

    /** Accent / brush face for a few display words (artifact `--nj-font-accent`); null = none. */
    @Composable
    fun accentFont(): FontFamily? = null

    val shapes: Shapes get() = NijiyomeTokens.shapes
    val decor: DirectionDecor get() = DirectionDecor.Plain

    /** The palette with [id], or the default one. */
    fun palette(id: String?): DirectionPalette = palettes.firstOrNull { it.id == id } ?: palettes.first()
}

/** The active direction. Provided by `NijiyomeTheme`. */
val LocalDirection: ProvidableCompositionLocal<DirectionSpec> = staticCompositionLocalOf { CanonDirection }

/** The active tone. Provided by `NijiyomeTheme`. */
val LocalThemeTone: ProvidableCompositionLocal<ThemeTone> = staticCompositionLocalOf { ThemeTone.LIGHT }

val LocalStatusColors: ProvidableCompositionLocal<StatusColors> = staticCompositionLocalOf { CanonStatusColors.light }

val LocalSignalColors: ProvidableCompositionLocal<SignalColors> = staticCompositionLocalOf { CanonSignalColors.light }

/** Accent face of the active direction, resolved once by `NijiyomeTheme` (null = none). */
val LocalAccentFont: ProvidableCompositionLocal<FontFamily?> = staticCompositionLocalOf { null }

/**
 * Cover color of the title on screen (Details), for directions that tint with it. Under CANON the cover still re-themes the page
 * (`NijiyomeSeedTheme`); the other directions keep their palette and may read this instead.
 */
val LocalCoverAccent: ProvidableCompositionLocal<Color?> = staticCompositionLocalOf { null }

// ---- What screens call. ----

/** Decorates [region] with the active direction. */
@Composable
fun Modifier.decor(region: DecorRegion): Modifier = this.then(LocalDirection.current.decor.regionModifier(region))

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) =
    LocalDirection.current.decor.SectionHeader(title, modifier, trailing)

@Composable
fun EpisodeMark(state: EpisodeMarkState, modifier: Modifier = Modifier, number: Int? = null) =
    LocalDirection.current.decor.EpisodeMark(state, number, modifier)

@Composable
fun StatusMark(status: StatusSlot, label: String, modifier: Modifier = Modifier, selected: Boolean = false) =
    LocalDirection.current.decor.StatusMark(status, label, selected, modifier)

@Composable
fun ProgressTrack(progress: Float, modifier: Modifier = Modifier, segments: Int? = null) =
    LocalDirection.current.decor.ProgressTrack(progress, modifier, segments)

@Composable
fun EmptyArt(kind: EmptyKind, modifier: Modifier = Modifier) = LocalDirection.current.decor.EmptyArt(kind, modifier)

/** The active direction's logo (nothing when it has none: check `LocalDirection.current.decor.hasBrandMark`). */
@Composable
fun BrandMark(modifier: Modifier = Modifier) = LocalDirection.current.decor.BrandMark(modifier)
