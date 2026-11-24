// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose `LocalContext`, or null. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}
