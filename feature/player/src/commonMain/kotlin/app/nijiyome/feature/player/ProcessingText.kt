// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player

import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import org.jetbrains.compose.resources.StringResource
import app.nijiyome.model.LinkProfileGroupKind
import app.nijiyome.model.ProcessingLocation

// 0.4.0 (worker C): the words of "Procesar en" for worker D's control and the notices. Resource ids only (no UI here).

fun processingLocationLabel(location: ProcessingLocation): StringResource = when (location) {
    ProcessingLocation.PHONE -> Res.string.player_processing_phone
    ProcessingLocation.PC -> Res.string.player_processing_pc
    ProcessingLocation.MIXED -> Res.string.player_processing_mixed
}

/** One line under each option: what that location does. */
fun processingLocationHint(location: ProcessingLocation): StringResource = when (location) {
    ProcessingLocation.PHONE -> Res.string.player_processing_phone_hint
    ProcessingLocation.PC -> Res.string.player_processing_pc_hint
    ProcessingLocation.MIXED -> Res.string.player_processing_mixed_hint
}

/** Why an option is disabled ([ProcessingOption.block]). */
fun processingBlockText(block: ProcessingBlock): StringResource = when (block) {
    ProcessingBlock.NOT_PAIRED -> Res.string.player_processing_block_not_paired
    ProcessingBlock.PC_OFFLINE -> Res.string.player_processing_block_offline
    ProcessingBlock.LOCAL_FILE -> Res.string.player_processing_block_local_file
    ProcessingBlock.SOURCE_NOT_SENDABLE -> Res.string.player_processing_block_not_sendable
    ProcessingBlock.NO_SOURCE_YET -> Res.string.player_processing_block_no_source
    ProcessingBlock.EXTERNAL_PLAYER -> Res.string.player_processing_block_external
    ProcessingBlock.NO_ORIGINAL_SOURCE -> Res.string.player_processing_block_no_original
    ProcessingBlock.MPV_UNAVAILABLE -> Res.string.player_processing_block_mpv
    ProcessingBlock.PHONE_FG_UNAVAILABLE -> Res.string.player_processing_block_phone_fg
    ProcessingBlock.PC_SUPPRESSES_PHONE_FG -> Res.string.player_processing_block_pc_fg
    ProcessingBlock.PC_NO_UPSCALE_PROFILE -> Res.string.player_processing_block_no_upscale
}

fun processingFailureText(failure: ProcessingFailure): StringResource = when (failure) {
    ProcessingFailure.TIMEOUT -> Res.string.player_processing_failed_timeout
    ProcessingFailure.OFFLINE -> Res.string.player_processing_failed_offline
    ProcessingFailure.SOURCE_REFUSED -> Res.string.player_processing_failed_source
    ProcessingFailure.PC_ERROR -> Res.string.player_processing_failed_pc
    ProcessingFailure.LOCAL_UNAVAILABLE -> Res.string.player_processing_failed_local
}

/** Headers of the in-player profile list ([ProcessingUiState.profileGroups]). */
fun linkProfileGroupLabel(kind: LinkProfileGroupKind): StringResource = when (kind) {
    LinkProfileGroupKind.NONE -> Res.string.player_processing_group_none
    LinkProfileGroupKind.UPSCALE -> Res.string.player_processing_group_upscale
    LinkProfileGroupKind.FRAME_GEN -> Res.string.player_processing_group_frame_gen
    LinkProfileGroupKind.UPSCALE_FRAME_GEN -> Res.string.player_processing_group_both
}
