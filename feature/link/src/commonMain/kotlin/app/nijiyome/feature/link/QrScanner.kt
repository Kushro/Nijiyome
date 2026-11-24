package app.nijiyome.feature.link

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource

/**
 * Live QR scanner. The caller must already hold the camera permission ([rememberCameraPermission], asked in context by the Link
 * screen) and must keep a "paste code" alternative visible. [onResult] fires once with the decoded text.
 */
@Composable
expect fun QrScanner(onResult: (String) -> Unit, modifier: Modifier = Modifier)

/**
 * Whether this device scans QR codes with a camera (Android always; desktop when a webcam is plugged in). Without one the screens
 * offer pasting the code or link only.
 */
internal expect val qrCameraAvailable: Boolean

/**
 * A text written for the phone replaced by this platform's own wording (desktop: a PC linking to another PC's extension server).
 * Android returns [res] itself.
 */
internal expect fun platformHint(res: StringResource): StringResource

/** The runtime permission to use the camera for [QrScanner]. */
internal interface CameraPermission {
    fun isGranted(): Boolean

    /** Asks the user. The answer goes to the `onResult` given to [rememberCameraPermission]. */
    fun request()
}

@Composable
internal expect fun rememberCameraPermission(onResult: (Boolean) -> Unit): CameraPermission

/** Reads the clipboard's plain text when called, or null (nothing copied, not text, or the read is refused). */
@Composable
internal expect fun rememberClipboardReader(): () -> String?
