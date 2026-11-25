// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.EmptyState
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.DecorRegion
import app.nijiyome.designsystem.direction.EmptyArt
import app.nijiyome.designsystem.direction.EmptyKind
import app.nijiyome.designsystem.direction.SectionHeader
import app.nijiyome.designsystem.direction.decor
import app.nijiyome.designsystem.motion.LoadingPlaceholder
import app.nijiyome.designsystem.motion.cardSway
import app.nijiyome.designsystem.motion.cardToDetailsSource
import app.nijiyome.feature.novel.resources.Res
import app.nijiyome.feature.novel.resources.*
import app.nijiyome.model.LoadState
import app.nijiyome.model.Media
import app.nijiyome.model.MediaRef
import app.nijiyome.model.TitleLanguage
import app.nijiyome.ui.cards.CardContext
import app.nijiyome.ui.cards.TitleCard
import app.nijiyome.ui.cards.TitleCardData
import app.nijiyome.ui.cards.toCardData
import app.nijiyome.ui.look.cardWidth
import app.nijiyome.ui.media.COVER_ASPECT_RATIO
import app.nijiyome.ui.state.userMessage
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

// Pieces of the novel screens (copy of the manga ones; feature modules do not depend on each other) in the Sparkles design (artifact `screens-c.js`, screen `manga`, medium Novelas): page header with the medium switch,
// search field, filter chips with menus, card rows, empty states. Every card is a TitleCard keyed `novel:<id>`.

/** Card key of a manga title (`novel:` tells cards and the details transition this is reading material). */
internal fun novelCardKey(ref: MediaRef, fallback: String): String = "novel:" + (ref.anilistId ?: ref.malId ?: ref.kitsuId ?: fallback)

/** The card of a novel [Media], in the manga title language (novels share it). */
@Composable
internal fun Media.novelCard(lang: TitleLanguage, subtitle: String? = null): TitleCardData {
    val base = toCardData(lang)
    return base.copy(key = novelCardKey(ref, title), subtitle = subtitle ?: base.subtitle)
}

/** Which reading medium a home shows (the artifact's Manga / Novelas switch). */
internal enum class ReadingMedium { MANGA, NOVEL }

/**
 * Page header of a reading home: back (phones), title, optional facts line, the Manga / Novelas switch when the shell wires
 * [onSwitchMedium], and the refresh action.
 */
@Composable
internal fun ReadingPageHeader(
    title: String,
    medium: ReadingMedium,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSwitchMedium: ((ReadingMedium) -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.ui_back)) }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onSwitchMedium != null && !compact) MediumSwitch(medium, onSwitchMedium)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.rd_s11_refresh_catalog))
            }
        }
        if (onSwitchMedium != null && compact) MediumSwitch(medium, onSwitchMedium)
    }
}

@Composable
private fun MediumSwitch(medium: ReadingMedium, onSwitch: (ReadingMedium) -> Unit) {
    Segmented(
        options = listOf(
            SegmentedOption(ReadingMedium.MANGA, stringResource(Res.string.rd_s11_medium_manga)),
            SegmentedOption(ReadingMedium.NOVEL, stringResource(Res.string.rd_s11_medium_novels)),
        ),
        selected = medium,
        onSelect = { if (it != medium) onSwitch(it) },
        small = true,
    )
}

/** The search field of a reading home: Enter searches, "Borrar" clears query and filters. */
@Composable
internal fun ReadingSearchField(
    query: String,
    placeholder: String,
    showClear: Boolean,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(placeholder) },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f).widthIn(max = 720.dp),
        )
        if (showClear) TextButton(onClick = onClear) { Text(stringResource(Res.string.rd_s11_clear)) }
    }
}

/**
 * A filter as a chip that opens its options (the artifact's chip + popover): the chip reads "Género: Acción" when set, the open menu
 * marks the current option. [leadingIcon] marks the sort chip.
 */
@Composable
internal fun <T> FilterChipMenu(
    label: String,
    value: String?,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    sort: Boolean = false,
    active: Boolean = value != null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(if (value != null) "$label: $value" else label, maxLines = 1) },
            leadingIcon = if (sort) ({ Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            options.forEach { option ->
                val isCurrent = option == selected
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = { if (isCurrent) Icon(Icons.Outlined.Check, contentDescription = null) else Box(Modifier.size(24.dp)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** One active filter shown under the chips, with its × (artifact `nj-c-activef`). */
internal data class ActiveFilter(val label: String, val onRemove: () -> Unit)

/** The active filters as removable chips plus "Limpiar filtros"; nothing when none is active. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveFilterRow(filters: List<ActiveFilter>, onClearAll: () -> Unit, modifier: Modifier = Modifier) {
    if (filters.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        filters.forEach { f ->
            val removeLabel = stringResource(Res.string.rd_s11_filter_remove, f.label)
            InputChip(
                selected = true,
                onClick = f.onRemove,
                label = { Text(f.label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = removeLabel, modifier = Modifier.size(16.dp)) },
            )
        }
        TextButton(onClick = onClearAll) { Text(stringResource(Res.string.rd_s11_filters_clear)) }
    }
}

/** Empty state with the direction's art, its text and an optional action (region `EMPTY`). */
@Composable
internal fun ReadingEmpty(
    kind: EmptyKind,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().decor(DecorRegion.EMPTY), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyArt(kind, Modifier.padding(top = 16.dp))
        EmptyState(icon = icon, title = title, message = message, actionLabel = actionLabel, onAction = onAction)
    }
}

/** A row of poster placeholders while a row loads. */
@Composable
internal fun CardRowPlaceholder(modifier: Modifier = Modifier) {
    val w = cardWidth()
    Row(modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(5) { LoadingPlaceholder(Modifier.width(w).height(w / COVER_ASPECT_RATIO)) }
    }
}

/**
 * A titled row of cards from a [LoadState]: placeholders while loading, a compact error with Reintentar, hidden when empty.
 * [cardOf] builds each card (so the adult zone can hide covers); [onSeeAll] adds "Ver todo".
 */
@Composable
internal fun MediaCardRow(
    title: String,
    state: LoadState<List<Media>>,
    onRetry: () -> Unit,
    onOpen: (Media) -> Unit,
    cardOf: @Composable (Media) -> TitleCardData,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    onCardClick: ((Media, TitleCardData) -> Unit)? = null,
) {
    when (state) {
        LoadState.Loading -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title, Modifier.padding(horizontal = 16.dp))
            CardRowPlaceholder()
        }
        is LoadState.Err -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title, Modifier.padding(horizontal = 16.dp))
            ErrorState(message = state.cause.userMessage(), retryLabel = stringResource(UiR.string.ui_retry), onRetry = onRetry, compact = true)
        }
        is LoadState.Ok -> if (state.value.isNotEmpty()) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title,
                    Modifier.padding(horizontal = 16.dp),
                    trailing = onSeeAll?.let { see -> { TextButton(onClick = see) { Text(stringResource(Res.string.rd_s11_see_all)) } } },
                )
                CardLazyRow(state.value, cardOf = cardOf, onClick = { m, d -> onCardClick?.invoke(m, d) ?: onOpen(m) })
            }
        }
    }
}

/** The LazyRow of a section (region `ROW`), each card swaying and ready to grow into its details page. */
@Composable
internal fun CardLazyRow(
    items: List<Media>,
    cardOf: @Composable (Media) -> TitleCardData,
    onClick: (Media, TitleCardData) -> Unit,
    context: CardContext = CardContext.ROW,
) {
    LazyRow(
        Modifier.fillMaxWidth().decor(DecorRegion.ROW),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { i, m -> "${m.ref.anilistId ?: m.title}-$i" }) { i, media ->
            val data = cardOf(media)
            TitleCard(data, onClick = { onClick(media, data) }, context = context, modifier = Modifier.cardSway(i).cardToDetailsSource(data.key))
        }
    }
}
