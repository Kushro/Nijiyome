// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.upscale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/** What the phone's power state says right now (plan §4.6). */
data class UpscalePowerState(
    val batterySaver: Boolean = false,
    /** 0..100, or null when unknown. */
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    /** Thermal status at MODERATE or above (API 29+; never on older phones). */
    val hot: Boolean = false,
)

/**
 * The Android power gates of the upscale queue: battery saver, battery at or below [LOW_BATTERY_PERCENT] while not charging (the
 * threshold of `MpvQualityController`), thermal status at `THERMAL_STATUS_MODERATE` or more, and NOT_CHARGING when the user asked
 * for "Solo mientras carga". Listens for the process lifetime from the first [reasons] call (system broadcasts and the thermal
 * listener; nothing polls).
 */
class AndroidPowerSignals(context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val state = MutableStateFlow(UpscalePowerState())
    val power: StateFlow<UpscalePowerState> = state.asStateFlow()

    @Volatile
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> refreshSaver()
            }
        }
    }

    private val thermalListener: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { status -> onThermal(status) }
    } else {
        null
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Both are system broadcasts, which a not-exported receiver still gets.
        val sticky = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        }.getOrNull()
        sticky?.let(::onBattery)
        refreshSaver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = powerManager
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener
            if (pm != null && listener != null) {
                runCatching { pm.addThermalStatusListener(appContext.mainExecutor, listener) }
                runCatching { onThermal(pm.currentThermalStatus) }
            }
        }
    }

    /** The pause reasons of the power state and the settings' "only while charging". */
    fun reasons(settings: Flow<ImageUpscaleSettings>): Flow<Set<UpscalePauseReason>> {
        start()
        return combine(state, settings) { p, s ->
            buildSet {
                if (p.batterySaver) add(UpscalePauseReason.BATTERY_SAVER)
                if (!p.charging && p.batteryPercent != null && p.batteryPercent <= LOW_BATTERY_PERCENT) add(UpscalePauseReason.LOW_BATTERY)
                if (p.hot) add(UpscalePauseReason.HOT)
                if (s.onlyWhileCharging && !p.charging) add(UpscalePauseReason.NOT_CHARGING)
            }
        }
    }

    private fun onBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        update { it.copy(batteryPercent = percent, charging = charging) }
    }

    private fun refreshSaver() {
        val saver = runCatching { powerManager?.isPowerSaveMode == true }.getOrDefault(false)
        update { it.copy(batterySaver = saver) }
    }

    private fun onThermal(status: Int) {
        update { it.copy(hot = status >= PowerManager.THERMAL_STATUS_MODERATE) }
    }

    private inline fun update(transform: (UpscalePowerState) -> UpscalePowerState) {
        while (true) {
            val before = state.value
            if (state.compareAndSet(before, transform(before))) return
        }
    }

    companion object {
        const val LOW_BATTERY_PERCENT = 20
    }
}
