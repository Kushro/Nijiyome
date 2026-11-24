// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.link.camera.DesktopWebcams
import app.nijiyome.feature.link.camera.WebcamBackend
import app.nijiyome.feature.link.camera.GrayFrame
import app.nijiyome.feature.link.camera.scanQr
import app.nijiyome.feature.link.resources.Res
import app.nijiyome.feature.link.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** v1.1 (F6): a webcam scans the QR code (Link and extension servers); without one the screens offer pasting the link, as before. */
internal actual val qrCameraAvailable: Boolean get() = DesktopWebcams.available

internal sealed interface ScanState {
    data object Starting : ScanState

    data object Aiming : ScanState

    data object NoCamera : ScanState

    data class Failed(val detail: String) : ScanState
}

/**
 * The first webcam (V4L2 on Linux, FFmpeg's DirectShow input on Windows), opened only while this is shown: a gray preview of what
 * it sees and ZXing on every frame. [onResult] fires once; leaving the screen (or Cancel) closes the camera.
 */
@Composable
actual fun QrScanner(onResult: (String) -> Unit, modifier: Modifier) {
    val currentOnResult by rememberUpdatedState(onResult)
    var state by remember { mutableStateOf<ScanState>(ScanState.Starting) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    // One owner: this coroutine opens the camera, reads it and closes it, on the same IO thread, after the last read returned.
    // Leaving the screen only cancels it; every read gives up within its timeout, so the close follows shortly (v1.1 review #2:
    // closing from the UI thread unmapped V4L2 buffers while a frame was being copied).
    LaunchedEffect(Unit) {
        val text = withContext(Dispatchers.IO) { scanFirstCamera(DesktopWebcams.backend, onState = { state = it }, onFrame = { preview = it.toImageBitmap() }) }
        if (text != null) currentOnResult(text)
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            val message = when (val s = state) {
                ScanState.Starting -> stringResource(Res.string.link_qr_starting)
                ScanState.Aiming -> stringResource(Res.string.link_qr_aim)
                ScanState.NoCamera -> stringResource(Res.string.link_qr_no_camera)
                is ScanState.Failed -> stringResource(Res.string.link_qr_camera_failed, s.detail)
            }
            val overPreview = preview != null && state == ScanState.Aiming
            Surface(
                color = if (overPreview) Color.Black.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.align(if (overPreview) Alignment.BottomCenter else Alignment.Center).fillMaxWidth(),
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overPreview) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(if (overPreview) 12.dp else 24.dp),
                )
            }
        }
    }
}

/**
 * Opens the first camera of [backend], scans it until a QR code is read and closes it, all on the calling thread (the stream's
 * only owner). Cancelling the coroutine ends the scan between two reads; null when there is no camera or it failed ([onState]).
 */
internal suspend fun scanFirstCamera(backend: WebcamBackend, onState: (ScanState) -> Unit, onFrame: (GrayFrame) -> Unit): String? {
    val camera = runCatching { backend.devices().firstOrNull() }.getOrNull()
    if (camera == null) {
        onState(ScanState.NoCamera)
        return null
    }
    val stream = try {
        backend.open(camera)
    } catch (e: Exception) {
        onState(ScanState.Failed(e.message ?: e.javaClass.simpleName))
        return null
    }
    return try {
        onState(ScanState.Aiming)
        scanQr(stream, onFrame)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onState(ScanState.Failed(e.message ?: e.javaClass.simpleName))
        null
    } finally {
        stream.close()
    }
}

private fun GrayFrame.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.GRAY_8, ColorAlphaType.OPAQUE))
    bitmap.installPixels(if (luma.size == width * height) luma else luma.copyOf(width * height))
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}

/** No runtime permission on desktop: "Scan" opens [QrScanner] (which says so when there is no camera). */
@Composable
internal actual fun rememberCameraPermission(onResult: (Boolean) -> Unit): CameraPermission {
    val callback = rememberUpdatedState(onResult)
    return remember {
        object : CameraPermission {
            override fun isGranted(): Boolean = true
            override fun request() = callback.value(true)
        }
    }
}

@Composable
internal actual fun rememberClipboardReader(): () -> String? {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) { { runCatching { clipboard.getText()?.text?.trim() }.getOrNull() } }
}

/**
 * The desktop wording of the extension-server screen: a PC linking to another PC's server, not a phone to its PC. The pairing hint
 * also says how to scan when a webcam is there.
 */
internal actual fun platformHint(res: StringResource): StringResource = when (res) {
    Res.string.extserver_pair_hint -> if (qrCameraAvailable) Res.string.extserver_pair_hint_pc_camera else Res.string.extserver_pair_hint_pc
    else -> desktopTexts[res] ?: res
}

private val desktopTexts: Map<StringResource, StringResource> = mapOf(
    Res.string.extserver_title to Res.string.extserver_title_pc,
    Res.string.extserver_intro to Res.string.extserver_intro_pc,
    Res.string.extserver_pair_title to Res.string.extserver_pair_title_pc,
    Res.string.extserver_password_hint to Res.string.extserver_password_hint_pc,
    Res.string.extserver_confirm_body to Res.string.extserver_confirm_body_pc,
    Res.string.extserver_none_body to Res.string.extserver_none_body_pc,
    Res.string.extserver_remove_body to Res.string.extserver_remove_body_pc,
    Res.string.extserver_err_pairing_required to Res.string.extserver_err_pairing_required_pc,
    Res.string.extserver_benefits_title to Res.string.extserver_benefits_title_pc,
    Res.string.extserver_pro_other_ecosystems to Res.string.extserver_pro_other_ecosystems_pc,
    Res.string.extserver_pro_pc_scrapes to Res.string.extserver_pro_pc_scrapes_pc,
    Res.string.extserver_pro_battery to Res.string.extserver_pro_battery_pc,
    Res.string.extserver_con_pc_on to Res.string.extserver_con_pc_on_pc,
    Res.string.extserver_con_video_proxy to Res.string.extserver_con_video_proxy_pc,
)
