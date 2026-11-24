// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.desktop.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * v1.1 (F2): the desktop scale on Linux (docs/spikes/reproductor.md §4.2). The app runs on X11 (XWayland under a Wayland session;
 * Skiko has no Wayland backend, SKIKO-28). KDE and GNOME tell X11 apps their scale through `Xft.dpi` (120 = 125 %) and XSETTINGS
 * (`Xft/DPI`), but AWT only follows an integer `GDK_SCALE` and OpenJDK's X11 `sun.java2d.uiScale` is truncated to an integer, so
 * Compose saw density 1.0 and drew the whole UI at 100 % on a 125 % desktop.
 *
 * This reads that scale and gives Compose the missing factor as [LocalDensity] (the window keeps its real pixels, so text and icons
 * are drawn sharp at the new size, not stretched). The video does not depend on it: mpv renders into a texture of the video area's
 * size in real pixels (MpvVideoSurface), so it is 1:1 with the window's pixels at any scale.
 *
 * What the app cannot change: KWin gives XWayland ONE scale for every monitor. On a monitor whose own scale differs from that one
 * (DP-2 at 100 % with XWayland at 125 % in the spike), KWin resamples the whole X11 window, video included, so it is not 1:1 there.
 * The fix is on the compositor side: KDE's "Legacy applications (X11): Apply scaling themselves" with every monitor at the same scale,
 * or a native Wayland Skiko. Documented in docs/spikes/reproductor.md.
 *
 * `NIJIYOME_SCALE` (e.g. `1.25`, `1` to turn it off) overrides what the desktop says.
 */
object LinuxUiScale {
    /** The factor Compose's density is multiplied by (1.0 = none); read once, before the first window. */
    val factor: Float by lazy { detect() }

    private fun detect(): Float {
        val linux = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("linux")
        if (!linux || GraphicsEnvironment.isHeadless()) return 1f
        val awt = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        }.getOrDefault(1.0)
        val dpi = xsettingsDpi() ?: xrdbDpi()
        return scaleFactor(System.getenv("NIJIYOME_SCALE"), dpi, awt)
    }

    /** XSETTINGS `Xft/DPI` as AWT exposes it (DPI × 1024), when an XSETTINGS manager runs. */
    private fun xsettingsDpi(): Double? = runCatching {
        (Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI") as? Int)?.takeIf { it > 0 }?.let { it / 1024.0 }
    }.getOrNull()

    /** `Xft.dpi` of the X resource database (`xrdb -query`), when the tool exists. */
    private fun xrdbDpi(): Double? = runCatching {
        val p = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(2, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return null
        }
        xftDpi(out)
    }.getOrNull()
}

/** `Xft.dpi:\t120` of `xrdb -query` output; null when absent. */
internal fun xftDpi(resources: String): Double? =
    resources.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("Xft.dpi:") }
        ?.substringAfter(':')?.trim()?.toDoubleOrNull()?.takeIf { it > 0 }

/**
 * The density factor: [override] (`NIJIYOME_SCALE`) when it parses, else [dpi] / 96 over what AWT already scales ([awtScale], an
 * integer `GDK_SCALE`). Anything under 1.05 or unknown is 1 (nothing to fix); capped at 4.
 */
internal fun scaleFactor(override: String?, dpi: Double?, awtScale: Double): Float {
    override?.trim()?.toFloatOrNull()?.takeIf { it > 0 }?.let { return it.coerceIn(0.5f, 4f) }
    val d = dpi ?: return 1f
    val f = d / 96.0 / awtScale.coerceAtLeast(1.0)
    return if (f < 1.05) 1f else f.coerceAtMost(4.0).toFloat()
}

/** [content] with Compose's density times [factor] (nothing changes at 1). */
@Composable
fun ScaledDensity(factor: Float, content: @Composable () -> Unit) {
    if (factor == 1f) {
        content()
        return
    }
    val base = LocalDensity.current
    val density = remember(base, factor) { Density(base.density * factor, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
