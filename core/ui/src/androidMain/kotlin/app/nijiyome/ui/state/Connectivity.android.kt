// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.ui.state

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// android wave contract (docs/redesign/12 §3.9). Owner: worker b. Name and signature FIXED.

@Composable
actual fun rememberIsOnline(): Boolean {
    val context = LocalContext.current.applicationContext
    val cm = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    var online by remember(cm) { mutableStateOf(cm?.isValidatedNow() ?: true) }
    DisposableEffect(cm) {
        if (cm == null) return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.isValidatedInternet()
            }

            override fun onLost(network: Network) {
                online = cm.isValidatedNow()
            }

            override fun onUnavailable() {
                online = false
            }
        }
        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
        online = cm.isValidatedNow()
        onDispose { if (registered) runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun NetworkCapabilities.isValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

private fun ConnectivityManager.isValidatedNow(): Boolean = runCatching {
    val network = activeNetwork ?: return@runCatching false
    getNetworkCapabilities(network)?.isValidatedInternet() ?: false
}.getOrDefault(true)
