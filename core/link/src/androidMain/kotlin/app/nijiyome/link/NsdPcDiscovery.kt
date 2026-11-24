// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.link

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

// Parity wave B (16 §3.11 / §7.4, worker r): the Android side of the discovery; the wire rules are DiscoveryWire (commonMain).

/** [PcDiscovery] over Android's `NsdManager` (multicast DNS-SD, no extra permission). Resolves one service at a time. */
class NsdPcDiscovery(private val context: Context) : PcDiscovery {
    override fun pcs(): Flow<List<FoundPc>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val found = LinkedHashMap<String, FoundPc>()
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        val lock = Any()

        fun publish() = trySend(synchronized(lock) { found.values.toList() })

        fun next() {
            val info = synchronized(lock) {
                if (resolving) return
                pending.removeFirstOrNull()?.also { resolving = true }
            } ?: return
            @Suppress("DEPRECATION")
            nsd.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        synchronized(lock) { resolving = false }
                        next()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = (serviceInfo.host as? Inet4Address)?.hostAddress
                        val txt = serviceInfo.attributes.mapValues { (_, v) -> v?.let { String(it, Charsets.UTF_8) } }
                        val pc = DiscoveryWire.parse(serviceInfo.serviceName, host, serviceInfo.port, txt)
                        synchronized(lock) {
                            if (pc != null) found[serviceInfo.serviceName] = pc
                            resolving = false
                        }
                        publish()
                        next()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                publish()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) {
                publish()
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { pending.addLast(serviceInfo) }
                next()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { found.remove(serviceInfo.serviceName) }
                publish()
            }
        }
        runCatching { nsd.discoverServices(DiscoveryWire.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { trySend(emptyList()) }
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }
}

/** True while an activity of the app is started (the Link events long-poll only runs then: no background service). */
class AppForeground(app: Application) {
    private val _visible = MutableStateFlow(isForegroundNow())
    val visible: StateFlow<Boolean> = _visible.asStateFlow()
    private var started = if (_visible.value) 1 else 0

    init {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    started++
                    _visible.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    started = (started - 1).coerceAtLeast(0)
                    if (started == 0) _visible.value = false
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun isForegroundNow(): Boolean = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }.getOrDefault(false)
}
