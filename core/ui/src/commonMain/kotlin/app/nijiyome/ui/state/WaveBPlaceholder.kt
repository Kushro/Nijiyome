// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.state

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource

/**
 * Parity wave B contract (docs/redesign/16-parity-wave-b-contract-android.md §1): the ONLY allowed body of a screen stub of this wave.
 * Shows [title] in a top bar (with a back arrow when [onBack] is given) and `ui_wave_b_placeholder`. Every call site is replaced by its
 * owner (r / s / t); the auditor greps `WaveBPlaceholder(` and `ui_wave_b_placeholder`: none may remain when the wave closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveBPlaceholder(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.ui_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.ui_wave_b_placeholder), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
