// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.player.mpv.quality

// Adapted from Enigmata `app/src/main/java/app/enigmata/domain/quality/ThermalPolicy.kt` (AGPL-3.0-or-later, same author): package
// renamed, the watcher's callback feeds `MpvQualityController.onThermal`. Logic unchanged.

/**
 * Pure thermal-degrade decision with hysteresis. `status`: `PowerManager.getCurrentThermalStatus()` (0 NONE .. 6 SHUTDOWN), null = no
 * data. `headroom`: `PowerManager.getThermalHeadroom()` (1.0 = the SEVERE threshold, higher = hotter; NaN / null = no data).
 *
 * Enter when status >= SEVERE or headroom >= [ENTER_HEADROOM]; leave only when status < MODERATE (or unknown) and headroom is unknown
 * or < [EXIT_HEADROOM]. In between the previous verdict stays, so the tier does not flap around one threshold.
 */
object ThermalPolicy {
    const val STATUS_MODERATE = 2
    const val STATUS_SEVERE = 3
    const val ENTER_HEADROOM = 0.95f
    const val EXIT_HEADROOM = 0.80f

    fun isDegraded(status: Int?, headroom: Float?, wasDegraded: Boolean): Boolean {
        val h = headroom?.takeUnless { it.isNaN() }
        val enter = (status != null && status >= STATUS_SEVERE) || (h != null && h >= ENTER_HEADROOM)
        if (enter) return true
        val statusCool = status == null || status < STATUS_MODERATE
        val headroomCool = h == null || h < EXIT_HEADROOM
        if (statusCool && headroomCool) return false
        return wasDegraded
    }
}
