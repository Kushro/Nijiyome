// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel

import kotlinx.coroutines.flow.StateFlow

/** A [app.nijiyome.model.Narrator] that reports the last failure of a narration run (the reader shows it under the narration bar). */
interface NarratorErrors {
    /** Last failure of a narration run; null when the last run did not fail. */
    val lastError: StateFlow<Throwable?>
}
