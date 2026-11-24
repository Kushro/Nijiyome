// SPDX-License-Identifier: AGPL-3.0-or-later
// Ported from Tatakai Next tk-link/src/media/vaapi.rs (AGPL-3.0, same author).
package app.nijiyome.server.link.media

import java.io.File

enum class Vendor(val wire: String) { AMD("amd"), NVIDIA("nvidia"), INTEL("intel"), OTHER("other"), UNKNOWN("unknown") }

/** The VAAPI render node of the Linux GPU (`/dev/dri/renderD128`). */
data class RenderNode(val device: String, val vendor: Vendor) {
    /** VAAPI encode / decode is attempted on this node: never on NVIDIA (its VAAPI driver only decodes; NVENC is used there). */
    fun usable(): Boolean = vendor != Vendor.NVIDIA
}

object Vaapi {
    /** An absolute `/dev/dri/renderD*` path that overrides the discovered node. */
    const val DEVICE_ENV = "NIJIYOME_VAAPI_DEVICE"

    /** FFmpeg name of the VAAPI device (`-init_hw_device vaapi=va:...`). */
    const val DEVICE_NAME = "va"

    fun vendorFromPci(id: String): Vendor = when (id.trim().removePrefix("0x").removePrefix("0X").lowercase()) {
        "1002" -> Vendor.AMD
        "10de" -> Vendor.NVIDIA
        "8086" -> Vendor.INTEL
        "" -> Vendor.UNKNOWN
        else -> Vendor.OTHER
    }

    /** Render nodes under `<sys>/class/drm` (`renderD*`), preferring `boot_vga == 1`, then the lowest number. */
    fun discover(sys: File, devDri: File): RenderNode? {
        val entries = File(File(sys, "class"), "drm").listFiles() ?: return null
        return entries.mapNotNull { e ->
            val n = e.name.removePrefix("renderD").takeIf { it != e.name }?.toIntOrNull() ?: return@mapNotNull null
            val dev = File(e, "device")
            val vendor = runCatching { vendorFromPci(File(dev, "vendor").readText()) }.getOrDefault(Vendor.UNKNOWN)
            val boot = runCatching { File(dev, "boot_vga").readText().trim() == "1" }.getOrDefault(false)
            Triple(boot, n, RenderNode(File(devDri, e.name).path, vendor))
        }.sortedWith(compareBy({ !it.first }, { it.second })).firstOrNull()?.third
    }

    /** The node of this system: the [DEVICE_ENV] override or [discover] on `/sys`; null off Linux. */
    fun discoverSystem(env: (String) -> String? = System::getenv): RenderNode? {
        if (Platform.current() != Platform.LINUX) return null
        env(DEVICE_ENV)?.let(::File)?.takeIf { it.isAbsolute }?.let { p ->
            val vendor = runCatching { vendorFromPci(File("/sys/class/drm/${p.name}/device/vendor").readText()) }.getOrDefault(Vendor.UNKNOWN)
            return RenderNode(p.path, vendor)
        }
        return discover(File("/sys"), File("/dev/dri"))
    }
}
