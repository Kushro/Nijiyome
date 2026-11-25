// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.nijiyome.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.motion.LocalMotion
import kotlinx.coroutines.delay

/**
 * The setting Settings jumps to (search result, command palette, deep link): the row whose [SettingAnchor] carries [targetId]
 * scrolls into view and flashes once. [consume] clears it so going back and forth does not flash again.
 */
@Stable
internal class SettingsJumpState(initial: String?) {
    var targetId: String? by mutableStateOf(initial)
        private set

    /** Bumped on every jump so jumping twice to the same row flashes twice. */
    var serial: Int by mutableStateOf(0)
        private set

    fun jumpTo(id: String?) {
        targetId = id
        serial++
    }

    fun consume() {
        targetId = null
    }
}

internal val LocalSettingsJump = staticCompositionLocalOf<SettingsJumpState?> { null }

/**
 * Wraps the rows of one setting (or one embedded block from another module) with its stable catalog [ids]. When it is the jump target
 * it is brought into view and its background flashes in the primary container color (a fade, held still with motion off).
 */
@Composable
internal fun SettingAnchor(vararg ids: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val jump = LocalSettingsJump.current
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    val motion = LocalMotion.current
    val isTarget = jump?.targetId?.let { it in ids } == true
    LaunchedEffect(isTarget, jump?.serial) {
        if (!isTarget || jump == null) return@LaunchedEffect
        // let the category lay out first
        delay(120)
        requester.bringIntoView()
        flash.snapTo(1f)
        if (motion.rich) {
            delay(500)
            flash.animateTo(0f, tween(1400, easing = LinearEasing))
        } else {
            delay(1600)
            flash.snapTo(0f)
        }
        jump.consume()
    }
    val color = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .drawBehind {
                val a = flash.value
                if (a > 0f) {
                    val r = 12.dp.toPx()
                    drawRoundRect(color.copy(alpha = 0.55f * a), cornerRadius = CornerRadius(r, r))
                }
            },
        content = content,
    )
}

/** Announces its text politely (the "Guardado" confirmation). */
internal fun Modifier.politeLiveRegion(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
