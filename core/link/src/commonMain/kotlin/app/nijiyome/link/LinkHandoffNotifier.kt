// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

import app.nijiyome.model.LinkHandoff

/**
 * Parity wave B (16 §3.11, worker r, checklist link.send-to-device): shows "La PC te envió <título>" when the PC hands an episode
 * to this phone. Android: [AndroidLinkHandoffNotifier] (androidMain).
 */
fun interface LinkHandoffNotifier {
    fun notify(handoff: LinkHandoff)
}
