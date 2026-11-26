// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import app.nijiyome.player.PlayerSession
import app.nijiyome.player.mpv.framegen.DesktopFgTransport
import app.nijiyome.player.mpv.framegen.DesktopFrameGenController
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Hito 4 + v1.3.2: the video of a desktop mpv session ([DesktopMpvSessionFactory]).
 *
 * - With Frame Generation OFF: mpv renders directly into GPU textures shared with Compose's Skia context via MediaMP.
 * - With Frame Generation ON: a zero-copy GPU overlay ([FrameGenVideoOverlay]) sits directly over [MpvMediampPlayerSurface]
 *   (still underneath all Compose controls, Mejoras panel, seekbar, and HUD), intercepting each newly rendered mpv texture
 *   (with Anime4K/FSR/NVScaler and subtitles already applied), running LSFG 3.1/3.1P in VRAM via `libnijiyome_fg_desktop`,
 *   and presenting paced interpolated frames at the monitor's refresh rate.
 */
@Composable
fun MpvVideoSurface(session: PlayerSession, modifier: Modifier = Modifier) {
    val desktopSession = session as? DesktopMpvPlayerSession ?: return
    val engine = desktopSession.engine
    val frameGen = desktopSession.frameGen
    val fgState = frameGen?.state?.collectAsState()?.value

    DisposableEffect(engine) {
        engine.surfaceAttached = true
        onDispose {
            engine.surfaceAttached = false
            engine.onViewport(0, 0)
        }
    }

    Box(modifier.onSizeChanged { engine.onViewport(it.width, it.height) }) {
        MpvMediampPlayerSurface(engine.player, Modifier.fillMaxSize())
        if (frameGen != null && fgState != null && fgState.enabled && fgState.unavailable == null) {
            FrameGenVideoOverlay(
                player = engine.player,
                frameGen = frameGen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FrameGenVideoOverlay(
    player: MpvMediampPlayer,
    frameGen: DesktopFrameGenController,
    modifier: Modifier = Modifier,
) {
    val bridge = remember(player, frameGen) { FrameGenSurfaceBridge(player, frameGen) }
    var frameTick by remember { mutableLongStateOf(0L) }

    // Drive Compose redraws at the display refresh rate (60 / 120 / 144 / 180 Hz) while FG is mounted.
    LaunchedEffect(bridge) {
        while (true) {
            withFrameNanos { nanos ->
                frameTick = nanos
            }
        }
    }

    DisposableEffect(bridge) {
        onDispose { bridge.release() }
    }

    Canvas(modifier) {
        // Read frameTick so Compose invalidates this Canvas on every VSync.
        val tickNs = frameTick
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        drawIntoCanvas { canvas ->
            bridge.drawFrame(
                nativeCanvas = canvas.nativeCanvas,
                dstWidth = size.width,
                dstHeight = size.height,
                nowNs = if (tickNs > 0L) tickNs else System.nanoTime(),
            )
        }
    }
}

/**
 * Render-thread bridge between MediaMP's surface ring and [DesktopFrameGenController].
 * Runs exclusively inside Skiko's draw pass (where the OpenGL / D3D12 context is current).
 */
private class FrameGenSurfaceBridge(
    private val player: MpvMediampPlayer,
    private val frameGen: DesktopFrameGenController,
) {
    private var lastFrameState: Long = Long.MIN_VALUE
    private var activeTransport: DesktopFgTransport = DesktopFgTransport.NONE
    private var wrappedWidth = 0
    private var wrappedHeight = 0
    private var wrappedContext: DirectContext? = null
    private var wrappedSurfaces: Array<Surface?> = emptyArray()
    private var wrappedTargets: Array<BackendRenderTarget?> = emptyArray()
    private var blitSurface: Surface? = null
    private var cachedSlot: Int = -1
    private var cachedImage: Image? = null
    private var stagingUploadBuf: ByteBuffer? = null
    private var stagingDownloadBuf: ByteBuffer? = null
    private var stagingDims = IntArray(2)
    private var paint = Paint().apply { isAntiAlias = false }
    private val vsyncNs: Long = run {
        val hz = displayRefreshRate()?.toDouble()?.takeIf { it > 1.0 } ?: 60.0
        (1_000_000_000.0 / hz).roundToLong()
    }

    fun drawFrame(nativeCanvas: org.jetbrains.skia.Canvas, dstWidth: Float, dstHeight: Float, nowNs: Long) {
        val access = MediaMpRingReflection.read(player) ?: return
        val ctx = access.surfaceContext ?: return
        val state = access.getFrameState()
        val activeSlot = ((state ushr 44) and 0xFL).toInt()
        val fw = ((state ushr 30) and 0x3FFFL).toInt()
        val fh = ((state ushr 16) and 0x3FFFL).toInt()
        if (activeSlot == 15 || fw <= 0 || fh <= 0) return

        val d3d12Ptr = if (access.isD3d11Backend) access.requestedDevicePtr else 0L
        val transport = frameGen.ensureStartedOnRenderThread(fw, fh, d3d12Ptr, vsyncNs)
        if (transport == DesktopFgTransport.NONE) {
            closeWraps()
            return
        }

        if (transport != activeTransport || fw != wrappedWidth || fh != wrappedHeight || ctx !== wrappedContext) {
            closeWraps()
            activeTransport = transport
            wrappedWidth = fw
            wrappedHeight = fh
            wrappedContext = ctx
            if (transport == DesktopFgTransport.GL_VULKAN_ZERO_COPY) {
                runCatching { ctx.resetGLAll() }
            }
            if (transport.isZeroCopy) {
                val count = frameGen.slotCount()
                wrappedSurfaces = arrayOfNulls(count)
                wrappedTargets = arrayOfNulls(count)
                blitSurface = Surface.makeRenderTarget(ctx, false, ImageInfo.makeN32Premul(fw, fh))
            }
        }

        // 1) Feed newly rendered mpv frame into the LSFG worker (zero-copy on GL/D3D12, staging fallback otherwise).
        if (state != lastFrameState) {
            val submitted = when (transport) {
                DesktopFgTransport.GL_VULKAN_ZERO_COPY -> {
                    val texId = access.getBufferTexture(activeSlot).toInt()
                    texId > 0 && frameGen.submitGlFrame(texId, nowNs)
                }
                DesktopFgTransport.D3D12_VULKAN_ZERO_COPY -> {
                    val resPtr = access.getBufferTexture(activeSlot)
                    resPtr != 0L && frameGen.submitD3d12Frame(resPtr, nowNs)
                }
                DesktopFgTransport.STAGING -> submitStaging(access, fw, fh, nowNs)
                DesktopFgTransport.NONE -> false
            }
            if (submitted) lastFrameState = state
        }

        // 2) Acquire paced frame slot from PresentRing and draw it opaque over the video viewport.
        val slot = frameGen.acquirePresentSlot(nowNs)
        if (slot < 0) return

        val dstRect = Rect.makeWH(dstWidth, dstHeight)
        if (transport.isZeroCopy) {
            val blit = blitSurface ?: return
            if (slot != cachedSlot || cachedImage == null) {
                val surface = ensureWrappedSlot(slot, fw, fh, ctx, transport) ?: return
                surface.notifyContentWillChange(ContentChangeMode.DISCARD)
                surface.draw(blit.canvas, 0, 0, null)
                cachedImage?.close()
                cachedImage = blit.makeImageSnapshot()
                cachedSlot = slot
            }
            val img = cachedImage ?: return
            nativeCanvas.drawImageRect(img, Rect.makeWH(fw.toFloat(), fh.toFloat()), dstRect, SamplingMode.LINEAR, paint, false)
        } else if (transport == DesktopFgTransport.STAGING) {
            val bytes = fw * fh * 4
            val buf = ensureDirectBuf(stagingDownloadBuf, bytes).also { stagingDownloadBuf = it }
            if (frameGen.readSlotRgba(slot, buf, bytes)) {
                val arr = ByteArray(bytes)
                buf.position(0)
                buf.get(arr)
                val info = ImageInfo(fw, fh, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
                val img = Image.makeRaster(info, arr, fw * 4)
                try {
                    nativeCanvas.drawImageRect(img, Rect.makeWH(fw.toFloat(), fh.toFloat()), dstRect, SamplingMode.LINEAR, paint, false)
                } finally {
                    img.close()
                }
            }
        }
    }

    private fun submitStaging(access: MediaMpRingAccess, fw: Int, fh: Int, nowNs: Long): Boolean {
        val pixels = access.readSurfacePixels(stagingDims) ?: return false
        if (stagingDims[0] != fw || stagingDims[1] != fh || pixels.size < fw * fh) return false
        val bytes = fw * fh * 4
        val buf = ensureDirectBuf(stagingUploadBuf, bytes).also { stagingUploadBuf = it }
        buf.position(0)
        // MediaMP readSurfacePixels returns ARGB ints; convert to RGBA8 bytes.
        val out = ByteArray(bytes)
        var j = 0
        for (i in 0 until fw * fh) {
            val p = pixels[i]
            out[j++] = ((p ushr 16) and 0xFF).toByte()
            out[j++] = ((p ushr 8) and 0xFF).toByte()
            out[j++] = (p and 0xFF).toByte()
            out[j++] = 0xFF.toByte()
        }
        buf.put(out)
        buf.flip()
        return frameGen.submitRgbaFrame(buf, bytes, nowNs)
    }

    private fun ensureWrappedSlot(
        slot: Int,
        w: Int,
        h: Int,
        ctx: DirectContext,
        transport: DesktopFgTransport,
    ): Surface? {
        if (slot !in wrappedSurfaces.indices) return null
        wrappedSurfaces[slot]?.let { return it }
        val handle = frameGen.slotHandle(slot)
        if (handle == 0L) return null
        val target = when (transport) {
            DesktopFgTransport.GL_VULKAN_ZERO_COPY ->
                BackendRenderTarget.makeGL(w, h, 1, 0, handle.toInt(), GL_RGBA8)
            DesktopFgTransport.D3D12_VULKAN_ZERO_COPY ->
                BackendRenderTarget.makeDirect3D(w, h, handle, DXGI_FORMAT_R8G8B8A8_UNORM, 1, 1)
            else -> return null
        }
        val origin = when (transport) {
            DesktopFgTransport.GL_VULKAN_ZERO_COPY -> SurfaceOrigin.BOTTOM_LEFT
            else -> SurfaceOrigin.TOP_LEFT
        }
        val surface = Surface.makeFromBackendRenderTarget(
            ctx, target, origin, SurfaceColorFormat.RGBA_8888, null, null,
        )
        if (surface == null) {
            target.close()
            return null
        }
        wrappedTargets[slot] = target
        wrappedSurfaces[slot] = surface
        return surface
    }

    private fun ensureDirectBuf(existing: ByteBuffer?, bytes: Int): ByteBuffer =
        if (existing != null && existing.capacity() >= bytes) existing
        else ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())

    private fun closeWraps() {
        runCatching { cachedImage?.close() }
        cachedImage = null
        cachedSlot = -1
        runCatching { blitSurface?.close() }
        blitSurface = null
        for (i in wrappedSurfaces.indices) {
            runCatching { wrappedSurfaces[i]?.close() }
            wrappedSurfaces[i] = null
        }
        for (i in wrappedTargets.indices) {
            runCatching { wrappedTargets[i]?.close() }
            wrappedTargets[i] = null
        }
        wrappedSurfaces = emptyArray()
        wrappedTargets = emptyArray()
        wrappedContext = null
        wrappedWidth = 0
        wrappedHeight = 0
        lastFrameState = Long.MIN_VALUE
    }

    fun release() {
        closeWraps()
        frameGen.stopOnRenderThread()
        runCatching { paint.close() }
    }

    companion object {
        private const val GL_RGBA8 = 0x8058
        private const val DXGI_FORMAT_R8G8B8A8_UNORM = 28
    }
}

private class MediaMpRingAccess(
    val handlePtr: Long,
    private val backend: Any,
    val isD3d11Backend: Boolean,
    val surfaceContext: DirectContext?,
    val requestedDevicePtr: Long,
    private val mGetFrameState: Method,
    private val mGetBufferTexture: Method,
    private val mReadSurfacePixels: Method,
) {
    fun getFrameState(): Long = mGetFrameState.invoke(backend, handlePtr) as Long
    fun getBufferTexture(slot: Int): Long = mGetBufferTexture.invoke(backend, handlePtr, slot) as Long
    fun readSurfacePixels(dims: IntArray): IntArray? = mReadSurfacePixels.invoke(backend, handlePtr, dims) as? IntArray
}

/**
 * Reads MediaMP's active `MpvSurfaceRing` (Linux OpenGL & Windows D3D11/D3D12) via cached reflection
 * so Nijiyome needs no fork of MediaMP and no compile-time access to its `internal` package.
 */
private object MediaMpRingReflection {
    private const val RING_CLASS = "org.openani.mediamp.mpv.internal.MpvSurfaceRing"
    private const val GL_BACKEND_CLASS = "org.openani.mediamp.mpv.internal.OpenGLSurfaceRingBackend"
    private const val D3D11_BACKEND_CLASS = "org.openani.mediamp.mpv.internal.D3D11SurfaceRingBackend"
    private const val RING_BACKEND_CLASS = "org.openani.mediamp.mpv.internal.MpvSurfaceRingBackend"

    private val renderingField = runCatching {
        MpvMediampPlayer::class.java.getDeclaredField("rendering").apply { isAccessible = true }
    }.getOrNull()
    private val renderingClass = runCatching {
        Class.forName("org.openani.mediamp.mpv.MpvMediampPlayer\$Rendering")
    }.getOrNull()
    private val renderingSurfaceField = runCatching {
        renderingClass?.getDeclaredField("surface")?.apply { isAccessible = true }
    }.getOrNull()
    private val ringClass = runCatching { Class.forName(RING_CLASS) }.getOrNull()
    private val ringHandleField = runCatching { ringClass?.getDeclaredField("handlePtr")?.apply { isAccessible = true } }.getOrNull()
    private val ringBackendField = runCatching { ringClass?.getDeclaredField("backend")?.apply { isAccessible = true } }.getOrNull()
    private val ringContextField = runCatching { ringClass?.getDeclaredField("surfaceContext")?.apply { isAccessible = true } }.getOrNull()
    private val ringDevicePtrField = runCatching { ringClass?.getDeclaredField("requestedDevicePtr")?.apply { isAccessible = true } }.getOrNull()

    private val backendIface = runCatching { Class.forName(RING_BACKEND_CLASS) }.getOrNull()
    private val mGetFrameState = runCatching {
        backendIface?.getMethod("getFrameState", Long::class.javaPrimitiveType)?.apply { isAccessible = true }
    }.getOrNull()
    private val mGetBufferTexture = runCatching {
        backendIface?.getMethod("getBufferTexture", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)?.apply { isAccessible = true }
    }.getOrNull()
    private val mReadSurfacePixels = runCatching {
        backendIface?.getMethod("readSurfacePixels", Long::class.javaPrimitiveType, IntArray::class.java)?.apply { isAccessible = true }
    }.getOrNull()
    private val d3dConsumerClass = runCatching {
        Class.forName("org.openani.mediamp.mpv.internal.D3D11SurfaceConsumer")
    }.getOrNull()
    private val d3dConsumerRingField = runCatching {
        d3dConsumerClass?.getDeclaredField("ring")?.apply { isAccessible = true }
    }.getOrNull()

    fun read(player: MpvMediampPlayer): MediaMpRingAccess? = runCatching {
        val rc = ringClass ?: return null
        val mState = mGetFrameState ?: return null
        val mTex = mGetBufferTexture ?: return null
        val mRead = mReadSurfacePixels ?: return null
        val rendering = renderingField?.get(player) ?: return null
        val surface = renderingSurfaceField?.get(rendering) ?: return null
        val ring: Any = if (rc.isInstance(surface)) {
            surface
        } else if (d3dConsumerClass?.isInstance(surface) == true) {
            d3dConsumerRingField?.get(surface)?.takeIf { rc.isInstance(it) } ?: return null
        } else {
            return null
        }
        val handlePtr = ringHandleField?.getLong(ring) ?: return null
        val backend = ringBackendField?.get(ring) ?: return null
        val backendName = backend.javaClass.name
        if (backendName != GL_BACKEND_CLASS && backendName != D3D11_BACKEND_CLASS) return null
        val ctx = ringContextField?.get(ring) as? DirectContext
        val devicePtr = ringDevicePtrField?.getLong(ring) ?: 0L
        MediaMpRingAccess(
            handlePtr = handlePtr,
            backend = backend,
            isD3d11Backend = backendName == D3D11_BACKEND_CLASS,
            surfaceContext = ctx,
            requestedDevicePtr = devicePtr,
            mGetFrameState = mState,
            mGetBufferTexture = mTex,
            mReadSurfacePixels = mRead,
        )
    }.getOrNull()
}
