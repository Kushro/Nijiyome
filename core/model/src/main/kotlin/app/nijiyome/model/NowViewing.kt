// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hito 6 (D1): the title of the Details page on screen, for desktop's Discord presence ("Viendo la ficha de <título>",
 * Tatakai Next's `PresenceVm::Viewing`). Only the desktop Details screen writes it; Android never does. Each page holds an [owner] token
 * so a page that goes away never clears the one that replaced it.
 */
object NowViewing {
    private data class Entry(val owner: Any, val title: String)

    private val entry = MutableStateFlow<Entry?>(null)
    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    @Synchronized
    fun show(owner: Any, title: String) {
        entry.value = Entry(owner, title)
        _title.value = title
    }

    @Synchronized
    fun hide(owner: Any) {
        if (entry.value?.owner !== owner) return
        entry.value = null
        _title.value = null
    }
}
