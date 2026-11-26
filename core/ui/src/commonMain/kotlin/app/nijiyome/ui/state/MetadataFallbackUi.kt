// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.state

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.theme.LocalReduceMotion
import app.nijiyome.model.AppError
import app.nijiyome.model.MetadataFallbackState
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.ui_error_title
import app.nijiyome.ui.resources.ui_rate_limit_fallback_active
import app.nijiyome.ui.resources.ui_rate_limit_fallback_soon
import app.nijiyome.ui.resources.ui_rate_limit_retrying_in
import app.nijiyome.ui.resources.ui_retry
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.theme.NijiyomeControls

/** Formats remaining seconds cleanly (`5s`, `0:42`, `1:15`). */
internal fun formatCountdown(remainingSec: Int): String {
    val s = remainingSec.coerceAtLeast(0)
    return if (s < 60) "${s}s" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * Animated top banner shown whenever the primary metadata provider (e.g. AniList) hits a rate limit and the router
 * switches to the next provider in the ladder, with a live countdown until primary recovery.
 */
@Composable
fun MetadataFallbackBanner(
    state: MetadataFallbackState?,
    onRetryPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val active = state ?: return@AnimatedVisibility
        var nowMs by remember(active.cooldownUntilMs) { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(active.cooldownUntilMs) {
            while (true) {
                nowMs = System.currentTimeMillis()
                if (nowMs >= active.cooldownUntilMs) break
                delay(500L)
            }
        }
        val totalMs = (active.cooldownUntilMs - active.startedAtMs).coerceAtLeast(1_000L)
        val remainingMs = (active.cooldownUntilMs - nowMs).coerceAtLeast(0L)
        val remainingSec = ((remainingMs + 999L) / 1_000L).toInt()
        val progress = (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

        val limited = providerLabel(active.limitedProvider)
        val fallback = active.fallbackProvider?.let(::providerLabel)
        val message = if (fallback != null) {
            stringResource(Res.string.ui_rate_limit_fallback_active, limited, fallback, formatCountdown(remainingSec))
        } else {
            stringResource(Res.string.ui_rate_limit_retrying_in, limited, formatCountdown(remainingSec))
        }

        val reduceMotion = LocalReduceMotion.current
        val rotation = if (reduceMotion) {
            0f
        } else {
            val inf = rememberInfiniteTransition(label = "metadata-fallback-spin")
            inf.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
                label = "spin",
            ).value
        }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).rotate(rotation),
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(shape = NijiyomeControls.shape, onClick = onRetryPrimary) {
                        Text(stringResource(Res.string.ui_retry))
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f),
                )
            }
        }
    }
}

/**
 * Error view with live animated countdown and automatic retry when [cause] is [AppError.RateLimited]; falls back to the
 * standard [ErrorState] for all other error kinds.
 */
@Composable
fun AppErrorState(
    cause: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fallbackProvider: String? = null,
    title: String? = null,
) {
    if (cause !is AppError.RateLimited) {
        ErrorState(
            message = cause.userMessage(),
            retryLabel = stringResource(Res.string.ui_retry),
            onRetry = onRetry,
            modifier = modifier,
            title = title ?: if (compact) null else stringResource(Res.string.ui_error_title),
            compact = compact,
        )
        return
    }

    // Immediate short 3s countdown when switching to a fallback provider; otherwise respect Retry-After (clamped 5..60s).
    val totalSec = remember(cause.provider, cause.retryAfterMs, fallbackProvider) {
        if (fallbackProvider != null) 3
        else ((cause.retryAfterMs ?: 15_000L) / 1_000L).toInt().coerceIn(5, 60)
    }
    var remainingSec by remember(cause.provider, cause.retryAfterMs, fallbackProvider) {
        mutableLongStateOf(totalSec.toLong())
    }
    LaunchedEffect(cause.provider, cause.retryAfterMs, fallbackProvider) {
        var left = totalSec
        remainingSec = left.toLong()
        while (left > 0) {
            delay(1_000L)
            left--
            remainingSec = left.toLong()
        }
        onRetry()
    }

    val limited = providerLabel(cause.provider)
    val next = fallbackProvider?.let(::providerLabel)
    val sec = remainingSec.toInt().coerceAtLeast(1)
    val message = if (next != null) {
        stringResource(Res.string.ui_rate_limit_fallback_soon, limited, next, sec)
    } else {
        stringResource(Res.string.ui_rate_limit_retrying_in, limited, formatCountdown(sec))
    }

    val reduceMotion = LocalReduceMotion.current
    val rotation = if (reduceMotion) {
        0f
    } else {
        val inf = rememberInfiniteTransition(label = "rate-limit-inline-spin")
        inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        ).value
    }
    val progress = (remainingSec.toFloat() / totalSec.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).rotate(rotation),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(shape = NijiyomeControls.shape, onClick = onRetry) {
                    Text(stringResource(Res.string.ui_retry))
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
