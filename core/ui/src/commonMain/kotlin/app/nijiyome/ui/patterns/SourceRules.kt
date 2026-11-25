// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalLayoutApi::class)

package app.nijiyome.ui.patterns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Pill
import app.nijiyome.designsystem.components.PillTone
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_i1_src_by_rules
import app.nijiyome.ui.resources.rd_i1_src_by_hand
import app.nijiyome.ui.resources.rd_i1_src_differs
import app.nijiyome.ui.resources.rd_i1_src_fails
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign, lane I1. The reusable parts of pattern `src` of docs/redesign/artefacto/lab.js (the picker screen itself is lane
// S4's): automatic-choice rules as chips, the "why this one / why not" line, the attribute chips that highlight what differs from the
// chosen source, and the badge that says who chose it.

/** One chip of a rule ("1080p", "Prefer cached", "ES"). */
@Immutable
data class RuleOption(val id: String, val label: String)

/**
 * One rule as a labelled row of chips. [single] rules (minimum quality, codec) pick exactly one; the others toggle each chip. Changing a
 * rule should recompute the choice at once (a pure `pick(sources, rules)` in the view model).
 *
 * ```kotlin
 * RuleChipRow(label = minQuality, options = listOf(RuleOption("720p", "720p"), RuleOption("1080p", "1080p")), selected = setOf(rules.minRes),
 *     single = true, onToggle = { vm.setMinRes(it) })
 * ```
 */
@Composable
fun RuleChipRow(
    label: String,
    options: List<RuleOption>,
    selected: Set<String>,
    onToggle: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
    single: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val on = option.id in selected
                FilterChip(
                    selected = on,
                    onClick = { if (!(single && on)) onToggle(option.id) },
                    label = { Text(option.label, maxLines = 1) },
                    leadingIcon = if (on) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * The live summary of the rules ("With these rules Yoru Fansub plays · 1080p HEVC, without asking"), announced when it changes.
 *
 * ```kotlin
 * RuleSummary(text = summaryOf(choice))
 * ```
 */
@Composable
fun RuleSummary(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.AutoMirrored.Outlined.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Who chose the source: the rules (badge with the rules icon) or the user (touch icon). */
@Composable
fun SourceChoiceBadge(manual: Boolean, modifier: Modifier = Modifier) {
    Pill(
        stringResource(if (manual) Res.string.rd_i1_src_by_hand else Res.string.rd_i1_src_by_rules),
        modifier = modifier,
        tone = PillTone.OK,
        icon = if (manual) Icons.Outlined.TouchApp else Icons.AutoMirrored.Outlined.Rule,
    )
}

/**
 * "Does not meet: 720p, you asked for 1080p or more · embedded player". Draws nothing when [reasons] is empty.
 *
 * ```kotlin
 * WhyNotRow(reasons = fails(source, rules))
 * ```
 */
@Composable
fun WhyNotRow(reasons: List<String>, modifier: Modifier = Modifier) {
    if (reasons.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            stringResource(Res.string.rd_i1_src_fails, reasons.joinToString(" · ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One attribute of a source ("1080p", "HEVC 10-bit", "Subs ES"); [key] pairs it with the same attribute of another source. */
@Immutable
data class SourceAttribute(val key: String, val text: String)

/**
 * The attributes of a source as small chips. With [compareTo] (the chosen source's attributes) the ones that differ are outlined in
 * the primary color and say so to TalkBack, so two sources compare at a glance.
 *
 * ```kotlin
 * AttributeChips(attrs = source.attributes(), compareTo = chosen?.attributes()?.takeIf { source != chosen })
 * ```
 */
@Composable
fun AttributeChips(attrs: List<SourceAttribute>, modifier: Modifier = Modifier, compareTo: List<SourceAttribute>? = null) {
    val reference = compareTo?.associate { it.key to it.text }
    val differs = stringResource(Res.string.rd_i1_src_differs)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attrs.forEach { attr ->
            val diff = reference != null && reference[attr.key] != attr.text
            val shape = CircleShape
            Text(
                attr.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (diff) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(shape)
                    .background(if (diff) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(if (diff) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics { if (diff) stateDescription = differs },
            )
        }
    }
}
