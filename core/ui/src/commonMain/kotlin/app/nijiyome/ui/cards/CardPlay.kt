// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.rd_contract_play_title
import org.jetbrains.compose.resources.stringResource

// Sparkles redesign contract. Owner: integrator. How a card style turns its play disc into the card's play button.

/**
 * Wraps a card's play [disc] in CONTINUE and HERO. With a play action ([LocalCardPlayAction]) the disc becomes its own button: a 48 dp
 * target, a click that calls the action, and a node read as "Play <title>" apart from the card. Without one it stays decorative and
 * this adds nothing but the [modifier] (alignment, padding) around the disc.
 */
@Composable
fun CardPlayTarget(title: String, modifier: Modifier = Modifier, shape: Shape = CircleShape, disc: @Composable () -> Unit) {
    val play = LocalCardPlayAction.current
    if (play == null) {
        Box(modifier, contentAlignment = Alignment.Center) { disc() }
        return
    }
    val label = stringResource(Res.string.rd_contract_play_title, title)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = play)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { disc() }
}

/**
 * For styles whose frame clears the content's semantics (one node per card): the play action as a custom accessibility action of
 * the card node, so screen readers still reach it. Call inside the frame's `semantics { }` with [playLabel] from [cardPlayLabel].
 */
fun SemanticsPropertyReceiver.cardPlayAction(play: (() -> Unit)?, playLabel: String?) {
    if (play == null || playLabel == null) return
    customActions = listOf(CustomAccessibilityAction(playLabel) { play(); true })
}

/** "Play <title>" when the card has a play action, else null. */
@Composable
fun cardPlayLabel(title: String): String? =
    if (LocalCardPlayAction.current != null) stringResource(Res.string.rd_contract_play_title, title) else null
