// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.model.ProcessingLocation

// Milestone 3: moved verbatim from ProcessingText.kt; back in commonMain with PlayerNotice and PlayerViewModel.

/** The flash text of a [PlayerNotice.Processing]. */
@Composable
internal fun processingNoticeText(notice: PlayerNotice.Processing): String {
    val reason = when {
        notice.block != null -> stringResource(processingBlockText(notice.block))
        notice.failure != null -> stringResource(processingFailureText(notice.failure))
        else -> null
    }
    return when {
        notice.onPhoneInstead -> stringResource(Res.string.player_processing_on_phone_instead, reason.orEmpty())
        reason != null -> reason
        else -> stringResource(
            when (notice.to) {
                ProcessingLocation.PHONE -> Res.string.player_processing_switched_phone
                ProcessingLocation.PC -> Res.string.player_processing_switched_pc
                ProcessingLocation.MIXED -> Res.string.player_processing_switched_mixed
            },
        )
    }
}
