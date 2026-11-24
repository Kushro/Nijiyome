// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.link

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import app.nijiyome.designsystem.theme.NijiyomeTheme
import app.nijiyome.extensionserver.ExtensionServerConnection
import app.nijiyome.extensionserver.ExtensionServerException
import app.nijiyome.extensionserver.ExtensionServerRepository
import app.nijiyome.extensionserver.PairingLink
import app.nijiyome.extensionserver.PendingServerLink
import app.nijiyome.extensionserver.ServerStatus
import app.nijiyome.feature.link.camera.DesktopWebcams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1.1 (F6): the desktop extension-server screen and the webcam scanner drawn off screen (no window, no camera: the camera is a
 * picture through `nijiyome.webcam.image`). The pictures go to `build/f6-renders` for the report.
 */
class ExtensionServersRenderTest {
    private val out = File("build/f6-renders").apply { mkdirs() }

    private class FakeRepo : ExtensionServerRepository {
        override val servers = MutableStateFlow<List<ExtensionServerConnection>>(emptyList())
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
        override val status = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
        override suspend fun connectWithLink(link: PairingLink): ExtensionServerConnection = throw ExtensionServerException(ExtensionServerException.INVALID_PASSWORD, 401)
        override suspend fun connectWithPassword(url: String, password: String): ExtensionServerConnection = throw ExtensionServerException(ExtensionServerException.INVALID_PASSWORD, 401)
        override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
        override suspend fun remove(id: String) = Unit
        override suspend fun refresh(id: String?) = Unit
    }

    private fun save(scene: ImageComposeScene, name: String, nanos: Long) {
        val bytes = scene.render(nanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, name).writeBytes(bytes)
    }

    private fun picture(withQr: Boolean): File {
        val image = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = java.awt.GradientPaint(0f, 0f, Color(185, 174, 152), 640f, 480f, Color(120, 112, 98))
        g.fillRect(0, 0, 640, 480)
        g.color = Color(60, 60, 70)
        g.fillRoundRect(220, 90, 200, 300, 30, 30) // a phone held up, its screen dark
        if (withQr) {
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(LINK, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0)
            g.color = Color.WHITE
            g.fillRect(235, 150, m.width * 5, m.height * 5)
            g.color = Color.BLACK
            for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) g.fillRect(235 + x * 5, 150 + y * 5, 5, 5)
        }
        g.dispose()
        return Files.createTempFile("camera", ".png").toFile().also { ImageIO.write(image, "png", it); it.deleteOnExit() }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { NijiyomeTheme(darkTheme = true) { content() } }
    }

    @Test
    fun theDesktopScreenSpeaksOfAPcAndOffersTheWebcam() {
        // DesktopWebcams.backend is read once per JVM: the camera picture is set before anything touches it.
        System.setProperty(DesktopWebcams.IMAGE_PROPERTY, picture(withQr = true).path)
        assertTrue(qrCameraAvailable)
        val scene = ImageComposeScene(width = 1100, height = 2200, density = Density(1f)) {
            KoinApplication(application = { modules(module { viewModel { ExtensionServersViewModel(FakeRepo(), PendingServerLink()) } }) }) {
                Themed { ExtensionServersScreen(onBack = {}) }
            }
        }
        repeat(5) { scene.render(it * 16_000_000L) }
        save(scene, "servidores-extensiones-escritorio.png", 100_000_000L)
        scene.close()
    }

    @Test
    fun theScannerShowsTheCameraAndReadsTheCode() {
        System.setProperty(DesktopWebcams.IMAGE_PROPERTY, picture(withQr = true).path)
        val result = AtomicReference<String?>(null)
        val scene = ImageComposeScene(width = 480, height = 480, density = Density(1f)) {
            Themed { Box(Modifier.size(480.dp)) { QrScanner(onResult = { result.set(it) }, modifier = Modifier.size(480.dp)) } }
        }
        var t = 0L
        val deadline = System.currentTimeMillis() + 10_000
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            scene.render(t)
            t += 16_000_000L
            Thread.sleep(20)
        }
        save(scene, "escaner-webcam.png", t)
        scene.close()
        assertEquals(LINK, result.get())
    }

    private companion object {
        const val LINK = "nijiyome://extensionserver/connect?server=https%3A%2F%2F192.168.1.20%3A47321&code=ABCD-1234"
    }
}
