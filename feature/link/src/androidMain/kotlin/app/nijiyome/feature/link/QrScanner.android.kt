package app.nijiyome.feature.link

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX preview + ZXing decoding of the luminance plane. No Google Play services (06 #3). */
@Composable
actual fun QrScanner(onResult: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener(
            {
                val p = providerFuture.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a ->
                        a.setAnalyzer(executor, QrAnalyzer { text ->
                            if (delivered.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute { currentOnResult(text) }
                            }
                        })
                    }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            provider?.unbindAll()
            shutdown(executor)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun shutdown(executor: ExecutorService) {
    executor.shutdown()
}

private class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(
                data, plane.rowStride, image.height, 0, 0, image.width, image.height, false,
            )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            result.text?.let(onText)
        } catch (_: Exception) {
            // NotFoundException on frames without a QR code is the normal case.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

@Composable
internal actual fun rememberCameraPermission(onResult: (Boolean) -> Unit): CameraPermission {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> callback.value(granted) }
    return remember(context, launcher) {
        object : CameraPermission {
            override fun isGranted(): Boolean =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

            override fun request() = launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
internal actual fun rememberClipboardReader(): () -> String? {
    val context = LocalContext.current
    return remember(context) {
        {
            // The clipboard's plain text, or null (nothing copied, not text, or the read is refused).
            runCatching {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
            }.getOrNull()
        }
    }
}

internal actual val qrCameraAvailable: Boolean = true

/** Android keeps its texts: every one of them is written for the phone. */
internal actual fun platformHint(res: org.jetbrains.compose.resources.StringResource): org.jetbrains.compose.resources.StringResource = res
