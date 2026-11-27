// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.nijiyome.framegen.FgActiveFlag
import app.nijiyome.player.ActivePlayback
import app.nijiyome.player.RemoteProcessingState
import app.nijiyome.upscale.AndroidPowerSignals
import app.nijiyome.upscale.CoilDiskCacheSource
import app.nijiyome.upscale.FileUpscaleSettingsPersistence
import app.nijiyome.upscale.UpscalePauseReason
import app.nijiyome.upscale.UpscaleQueue
import app.nijiyome.upscale.UpscaleService
import app.nijiyome.upscale.UpscaleSettingsStore
import app.nijiyome.upscale.androidUpscaleEngine
import app.nijiyome.upscale.androidUpscaleThreadSetup
import app.nijiyome.upscale.pauseWhen
import app.nijiyome.upscale.upscalePauses
import coil3.SingletonImageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

/**
 * Whether some activity of the app is started (visible). Registered in [NijiyomeApplication.onCreate], before any activity exists,
 * so the count is right from the first one; the upscale queue pauses while it is zero (plan §4.6, APP_HIDDEN).
 */
internal object AppVisibility : Application.ActivityLifecycleCallbacks {
    private var started = 0
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started++
        _visible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0) started--
        _visible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * GPU image upscaling on the phone (docs/plan-upscaling.md §4.1): the queue and the settings store as singletons. Settings in
 * `filesDir/upscale-settings.json`, results in `cacheDir/upscaled/`, sources in Coil's disk cache. Pauses: a player session (both
 * engines publish to [ActivePlayback]), phone frame generation, Link processing the stream, the app in the background, and the
 * power gates of [AndroidPowerSignals] (battery saver, battery at or below 20 % not charging, thermal MODERATE or above, "Solo
 * mientras carga").
 */
internal val upscaleModule = module {
    single {
        val context = androidContext()
        val fg = getOrNull<FgActiveFlag>()
        val remote = getOrNull<RemoteProcessingState>()
        val power = AndroidPowerSignals(context)
        UpscaleService.create(
            cacheDir = context.cacheDir,
            persistence = FileUpscaleSettingsPersistence(File(context.filesDir, "upscale-settings.json")),
            engine = androidUpscaleEngine(context),
            source = CoilDiskCacheSource { SingletonImageLoader.get(context) },
            pauses = { settings, scope ->
                upscalePauses(
                    scope,
                    listOf(
                        ActivePlayback.player.map { it != null }.pauseWhen(UpscalePauseReason.PLAYER_OPEN),
                        (fg?.active ?: flowOf(false)).pauseWhen(UpscalePauseReason.FRAMEGEN_ACTIVE),
                        (remote?.active ?: flowOf(false)).pauseWhen(UpscalePauseReason.LINK_BUSY),
                        AppVisibility.visible.map { !it }.pauseWhen(UpscalePauseReason.APP_HIDDEN),
                        power.reasons(settings),
                    ),
                )
            },
            threadSetup = ::androidUpscaleThreadSetup,
        )
    } onClose { it?.close() }
    single<UpscaleQueue> { get<UpscaleService>().queue }
    single<UpscaleSettingsStore> { get<UpscaleService>().store }
}
