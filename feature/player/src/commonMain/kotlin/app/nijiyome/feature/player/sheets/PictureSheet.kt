// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.feature.player.PlayerUiState
import app.nijiyome.feature.player.PlayerViewModel
import app.nijiyome.feature.player.isAutoSource
import app.nijiyome.feature.player.pickSourceQuality
import app.nijiyome.feature.player.playerCapabilities
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.feature.player.sourceQualities
import app.nijiyome.model.AspectMode
import app.nijiyome.model.DecoderMode
import app.nijiyome.model.ImageAdjust
import app.nijiyome.model.Quality
import app.nijiyome.player.PlaybackState
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.QualityRequest
import app.nijiyome.player.QualityUiState
import app.nijiyome.player.decoderInUseLabel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign (lane S6): the "Imagen" sheet (artifact `shImagen`, parity PLY-08 / PLE-09 / B-OCULTA-03): quality of the stream,
// aspect and zoom, colour, decoder. Aspect, zoom, colour and decoder were the "Imagen" card of the "Mejoras" panel; the quality was
// its own sheet of rows.

@Composable
internal fun PictureSheetContent(data: PlayerSheetData, actions: PlayerSheetActions, onClose: () -> Unit) {
    val q = data.quality
    QualitySection(data.vm, data.session, data.state, data.ps)
    SheetDivider()
    if (q.available) {
        PictureControls(data.session, q, aspect = data.aspect, onAspect = actions.onAspect)
    } else {
        // Media3: the surface still takes the aspect from the chrome; zoom, colour and decoder are libmpv's.
        if (actions.onAspect != null) {
            AspectSection(data.aspect ?: q.requested.aspect, zoom = null, onAspect = { mode -> actions.onAspect?.invoke(mode) }, onZoom = {})
            SheetDivider()
        }
        SheetSection(null) {
            val vm = data.vm
            StateNote(
                stringResource(Res.string.rd_s6_picture_needs_mpv),
                action = if (vm.canSwitchToMpvNow()) stringResource(Res.string.quality_switch_to_mpv) else null,
                onAction = { if (vm.switchToMpvByUser()) onClose() },
            )
        }
    }
}

// ---- quality ---------------------------------------------------------------------------------------------------------------------------

private const val AUTO = -1

@Composable
private fun QualitySection(vm: PlayerViewModel, session: PlayerSession, state: PlayerUiState, ps: PlaybackState) {
    SheetSection(stringResource(Res.string.player_quality)) {
        val auto = stringResource(Res.string.player_quality_auto)
        when {
            // Desktop: the stream has no renditions; the qualities of the episode's sources, picking one switches source.
            playerCapabilities.sourceQualities && ps.videoHeights.isEmpty() -> {
                val qualities = sourceQualities(state.sources)
                val autoOn = isAutoSource(state)
                val current = state.current?.candidate?.quality
                ChoiceChips(
                    choices = listOf(Choice<Quality?>(null, auto)) + qualities.map { Choice<Quality?>(it, it.label()) },
                    selected = if (autoOn) null else current.takeIf { it in qualities },
                    onSelect = { pickSourceQuality(vm, state, it) },
                )
                Hint(stringResource(Res.string.rd_s6_quality_changes_source))
            }
            ps.videoHeights.isNotEmpty() -> ChoiceChips(
                choices = listOf(Choice(AUTO, auto)) + ps.videoHeights.map { Choice(it, stringResource(Res.string.player_quality_height, it)) },
                selected = ps.selectedHeight ?: AUTO,
                onSelect = { h -> session.selectVideoHeight(h.takeIf { it != AUTO }) },
            )
            else -> Hint(stringResource(Res.string.rd_s6_quality_single))
        }
    }
}

// ---- aspect, zoom, colour, decoder -----------------------------------------------------------------------------------------------------

/**
 * Aspect and zoom, colour (brightness, contrast, saturation, gamma, hue, reset) and decoder, applied live by libmpv. [aspect] = what the
 * surface uses now when the chrome holds it; [onAspect] tells the chrome about a new one.
 */
@Composable
internal fun PictureControls(
    session: PlayerSession,
    q: QualityUiState,
    aspect: AspectMode? = null,
    onAspect: ((AspectMode) -> Unit)? = null,
) {
    val req = q.requested
    AspectSection(
        aspect ?: req.aspect,
        zoom = req.zoomPercent,
        onAspect = { mode ->
            session.setAspect(mode, req.zoomPercent)
            onAspect?.invoke(mode)
        },
        onZoom = { session.setAspect(req.aspect, it) },
    )
    SheetDivider()
    SheetSection(stringResource(Res.string.rd_s6_color)) {
        val img = req.image
        listOf(
            Triple(Res.string.player_fx_brightness, img.brightness) { v: Int -> img.copy(brightness = v) },
            Triple(Res.string.player_fx_contrast, img.contrast) { v: Int -> img.copy(contrast = v) },
            Triple(Res.string.player_fx_saturation, img.saturation) { v: Int -> img.copy(saturation = v) },
            Triple(Res.string.player_fx_gamma, img.gamma) { v: Int -> img.copy(gamma = v) },
            Triple(Res.string.player_fx_hue, img.hue) { v: Int -> img.copy(hue = v) },
        ).forEach { (label, value, change) ->
            ValueSlider(
                label = stringResource(label),
                value = value,
                range = ImageAdjust.MIN..ImageAdjust.MAX,
                step = 1,
                valueText = if (value > 0) "+$value" else "$value",
                onChange = { session.setImage(change(it)) },
            )
        }
        TextButton(
            onClick = { session.setImage(ImageAdjust()); session.setAspect(AspectMode.FIT, 100); onAspect?.invoke(AspectMode.FIT) },
            enabled = !img.isNeutral || req.zoomPercent != 100 || req.aspect != AspectMode.FIT,
        ) {
            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.rd_s6_picture_reset))
        }
    }
    SheetDivider()
    SheetSection(stringResource(Res.string.player_fx_decoder)) {
        Segmented(
            options = DecoderMode.entries.map { SegmentedOption(it, stringResource(decoderName(it)), description = stringResource(decoderHint(it))) },
            selected = req.decoder,
            onSelect = session::setDecoder,
            fillWidth = true,
            small = true,
        )
        Hint(stringResource(decoderHint(req.decoder)))
        decoderInUseLabel(q.decoderInUse)?.let { Hint(stringResource(Res.string.player_fx_decoder_in_use, it)) }
    }
}

@Composable
private fun AspectSection(aspect: AspectMode, zoom: Int?, onAspect: (AspectMode) -> Unit, onZoom: (Int) -> Unit) {
    SheetSection(stringResource(Res.string.player_fx_aspect)) {
        Segmented(
            options = AspectMode.entries.map { SegmentedOption(it, stringResource(aspectRes(it))) },
            selected = aspect,
            onSelect = onAspect,
            small = true,
        )
        if (zoom != null) {
            ValueSlider(
                label = stringResource(Res.string.player_fx_zoom),
                value = zoom,
                range = QualityRequest.MIN_ZOOM..QualityRequest.MAX_ZOOM,
                step = 5,
                valueText = stringResource(Res.string.player_fx_percent, zoom),
                onChange = onZoom,
            )
        }
    }
}

private fun aspectRes(mode: AspectMode): StringResource = when (mode) {
    AspectMode.FIT -> Res.string.player_fx_aspect_fit
    AspectMode.FILL -> Res.string.player_fx_aspect_fill
    AspectMode.STRETCH -> Res.string.player_fx_aspect_stretch
    AspectMode.RATIO_16_9 -> Res.string.player_fx_aspect_16_9
    AspectMode.RATIO_4_3 -> Res.string.player_fx_aspect_4_3
}

private fun decoderName(mode: DecoderMode): StringResource = when (mode) {
    DecoderMode.AUTO -> Res.string.player_fx_decoder_auto
    DecoderMode.HW -> Res.string.rd_s6_decoder_gpu
    DecoderMode.HW_PLUS -> Res.string.rd_s6_decoder_gpu_direct
    DecoderMode.SW -> Res.string.rd_s6_decoder_cpu
}

private fun decoderHint(mode: DecoderMode): StringResource = when (mode) {
    DecoderMode.AUTO -> Res.string.rd_s6_decoder_auto_hint
    DecoderMode.HW -> Res.string.rd_s6_decoder_gpu_hint
    DecoderMode.HW_PLUS -> Res.string.rd_s6_decoder_gpu_direct_hint
    DecoderMode.SW -> Res.string.rd_s6_decoder_cpu_hint
}
