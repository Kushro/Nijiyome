// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.nijiyome.model.NowViewing

/** The page's title goes to [NowViewing] while it is on screen (the desktop app turns it into Discord's "Viendo la ficha de…"). */
@Composable
internal actual fun DetailsPresence(title: String?) {
    val owner = remember { Any() }
    DisposableEffect(owner, title) {
        if (title.isNullOrBlank()) NowViewing.hide(owner) else NowViewing.show(owner, title)
        onDispose { NowViewing.hide(owner) }
    }
}
