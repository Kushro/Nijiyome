// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.model.AppLanguage
import app.nijiyome.model.AppSettings
import app.nijiyome.model.LinkRepository
import app.nijiyome.model.ListsRepository
import app.nijiyome.model.PlayerEngine
import app.nijiyome.model.Quality
import app.nijiyome.model.SettingsRepository
import app.nijiyome.model.SourceKind
import app.nijiyome.model.SourceMode
import app.nijiyome.model.SourcePrefs
import app.nijiyome.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import app.nijiyome.ui.resources.Res as UiR
import app.nijiyome.ui.resources.*

/**
 * Steps of the first-run wizard, in order ([onboardingSteps] drops the ones a device does not need). DOWNLOADS and SYSTEM are
 * platform steps (v1.1 F6, desktop: Tatakai Next's download folder and system steps): only shown where [platformOnboardingSteps]
 * lists them.
 */
internal enum class OnboardingStep { WELCOME, APPEARANCE, PLAYBACK, DOWNLOADS, LISTS, LINK, PERMISSIONS, SYSTEM, DONE }

/** The steps a platform adds itself ([platformOnboardingSteps]); every other step is shared. */
internal val PLATFORM_ONBOARDING_STEPS: Set<OnboardingStep> = setOf(OnboardingStep.DOWNLOADS, OnboardingStep.SYSTEM)

/**
 * The wizard's steps on this device (checklist account.setup-wizard: language, engine and source preferences, AniList / MAL, permissions).
 * PERMISSIONS only when a runtime permission is still worth asking: notifications need one from Android 13 (API 33). Pure, unit tested.
 */
internal fun onboardingSteps(
    notificationsNeedPermission: Boolean,
    listsAvailable: Boolean = true,
    linkAvailable: Boolean = true,
    platformSteps: Set<OnboardingStep> = emptySet(),
): List<OnboardingStep> = OnboardingStep.entries.filter {
    (it != OnboardingStep.PERMISSIONS || notificationsNeedPermission) &&
        (it != OnboardingStep.LISTS || listsAvailable) &&
        (it != OnboardingStep.LINK || linkAvailable) &&
        (it !in PLATFORM_ONBOARDING_STEPS || it in platformSteps)
}

/** First-run wizard state: writes language / theme at once and `onboardingDone = true` at the end (or on Skip). */
class OnboardingViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings

    fun setLanguage(value: AppLanguage) = viewModelScope.launch { settingsRepo.update { it.copy(language = value) } }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { settingsRepo.update { it.copy(themeMode = value) } }

    // Playback step: the same fields as Settings › Reproductor (engine and source preferences).
    fun setPlayerEngine(value: PlayerEngine) = viewModelScope.launch { settingsRepo.update { it.copy(playerEngine = value) } }

    fun setSourceMode(value: SourceMode) = updatePrefs { it.copy(mode = value) }

    /** null = any kind. */
    fun setPreferredKind(value: SourceKind?) = updatePrefs { it.copy(prefer = value) }

    fun setMinQuality(value: Quality) = updatePrefs { it.copy(minQuality = value) }

    fun setSubLang(value: String) = updatePrefs { it.copy(subLang = value) }

    fun setDubLang(value: String) = updatePrefs { it.copy(dubLang = value) }

    private fun updatePrefs(transform: (SourcePrefs) -> SourcePrefs) =
        viewModelScope.launch { settingsRepo.update { it.copy(sourcePrefs = transform(it.sourcePrefs)) } }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(onboardingDone = true) }
            onDone()
        }
    }
}

/**
 * Parity wave A (docs/redesign/15-parity-wave-a-contract-android.md §3.4, 2026-11-09 amendment, checklist account.setup-wizard).
 * Owner: worker q.
 *
 * First-run wizard (classic onboarding + setup wizard, trimmed to what Next has): welcome → language and theme → player engine and
 * source preferences → connect AniList / MAL (optional, Custom Tab through the same [AccountsViewModel] as Settings) → link a PC
 * (optional; explains where pairing lives) → permissions (notifications, Android 13+) → done.
 * Every step can be skipped. The last step (or Skip) writes `AppSettings.onboardingDone = true`; the shell reacts to the flag and
 * [onDone] is an extra hook. Shown by `NijiyomeApp` while `onboardingDone` is false, so it runs again after "Restablecer la app".
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm: OnboardingViewModel = koinViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val koin = getKoin()
    // Desktop (by design): paso "Tu PC" del asistente (sin LinkRepository no se muestra).
    val steps = remember {
        onboardingSteps(
            notificationsNeedPermission = notificationsNeedPermission,
            listsAvailable = koin.getOrNull<ListsRepository>() != null,
            linkAvailable = koin.getOrNull<LinkRepository>() != null,
            platformSteps = platformOnboardingSteps,
        )
    }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(0, steps.lastIndex)]
    val finish = { vm.finish(onDone) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { (stepIndex + 1f) / steps.size },
                        modifier = Modifier.weight(1f),
                    )
                    if (step != OnboardingStep.DONE) {
                        TextButton(onClick = finish, modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(Res.string.settings_onboarding_skip))
                        }
                    }
                }
                AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "onboarding") { current ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (current) {
                            OnboardingStep.WELCOME -> StepHeader(
                                Icons.Outlined.Celebration,
                                stringResource(Res.string.settings_onboarding_title),
                                stringResource(platformHint(Res.string.settings_onboarding_welcome_body)),
                            )
                            OnboardingStep.APPEARANCE -> {
                                StepHeader(
                                    Icons.Outlined.Palette,
                                    stringResource(Res.string.settings_onboarding_appearance_title),
                                    stringResource(Res.string.settings_onboarding_appearance_body),
                                )
                                Text(stringResource(Res.string.settings_language), style = MaterialTheme.typography.labelLarge)
                                Segmented(
                                    options = listOf(
                                        SegmentedOption(AppLanguage.SYSTEM, stringResource(Res.string.settings_lang_system)),
                                        SegmentedOption(AppLanguage.EN, stringResource(Res.string.settings_lang_en)),
                                        SegmentedOption(AppLanguage.ES, stringResource(Res.string.settings_lang_es)),
                                    ),
                                    selected = settings.language,
                                    onSelect = { vm.setLanguage(it) },
                                )
                                Text(stringResource(Res.string.settings_mode), style = MaterialTheme.typography.labelLarge)
                                Segmented(
                                    options = listOf(
                                        SegmentedOption(ThemeMode.SYSTEM, stringResource(Res.string.settings_mode_system)),
                                        SegmentedOption(ThemeMode.LIGHT, stringResource(Res.string.settings_mode_light)),
                                        SegmentedOption(ThemeMode.DARK, stringResource(Res.string.settings_mode_dark)),
                                    ),
                                    selected = settings.themeMode,
                                    onSelect = { vm.setThemeMode(it) },
                                )
                            }
                            OnboardingStep.PLAYBACK -> {
                                StepHeader(
                                    Icons.Outlined.PlayCircle,
                                    stringResource(Res.string.settings_onboarding_playback_title),
                                    stringResource(Res.string.settings_onboarding_playback_body),
                                )
                                OnboardingPlayback(settings, vm)
                            }
                            OnboardingStep.LISTS -> {
                                StepHeader(
                                    Icons.Outlined.Bookmarks,
                                    stringResource(Res.string.settings_onboarding_lists_title),
                                    stringResource(platformHint(Res.string.settings_onboarding_lists_body)),
                                )
                                OnboardingAccounts()
                            }
                            OnboardingStep.LINK -> StepHeader(
                                Icons.Outlined.Computer,
                                stringResource(Res.string.settings_onboarding_link_title),
                                stringResource(Res.string.settings_onboarding_link_body),
                            )
                            OnboardingStep.PERMISSIONS -> {
                                StepHeader(
                                    Icons.Outlined.NotificationsActive,
                                    stringResource(Res.string.settings_onboarding_permissions_title),
                                    stringResource(Res.string.settings_onboarding_permissions_body),
                                )
                                OnboardingNotifications()
                            }
                            OnboardingStep.DOWNLOADS -> {
                                StepHeader(
                                    Icons.Outlined.Download,
                                    stringResource(Res.string.settings_onboarding_downloads_title),
                                    stringResource(Res.string.settings_onboarding_downloads_body),
                                )
                                OnboardingPlatformStep(current)
                            }
                            OnboardingStep.SYSTEM -> {
                                StepHeader(
                                    Icons.Outlined.SettingsSuggest,
                                    stringResource(Res.string.settings_onboarding_system_title),
                                    stringResource(Res.string.settings_onboarding_system_body),
                                )
                                OnboardingPlatformStep(current)
                            }
                            OnboardingStep.DONE -> StepHeader(
                                Icons.Outlined.Celebration,
                                stringResource(Res.string.settings_onboarding_done_title),
                                stringResource(Res.string.settings_onboarding_done_body),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (stepIndex > 0) {
                        TextButton(onClick = { stepIndex -= 1 }) { Text(stringResource(Res.string.settings_onboarding_back)) }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (step == OnboardingStep.DONE) finish() else stepIndex += 1 }) {
                        Text(
                            stringResource(
                                when (step) {
                                    OnboardingStep.WELCOME -> Res.string.settings_onboarding_start
                                    OnboardingStep.DONE -> Res.string.settings_onboarding_finish
                                    else -> Res.string.settings_onboarding_next
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Engine and source preferences, the same options as Settings › Reproductor (the rest of that section stays there). */
@Composable
private fun OnboardingPlayback(settings: AppSettings, vm: OnboardingViewModel) {
    val prefs = settings.sourcePrefs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.settings_engine), style = MaterialTheme.typography.labelLarge)
        Segmented(
            options = engineOptions(),
            selected = settings.playerEngine.shown(),
            onSelect = { vm.setPlayerEngine(it) },
        )
        Text(
            stringResource(settings.playerEngine.shown().hintRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(Res.string.settings_source_mode), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Segmented(
            options = listOf(
                SegmentedOption(SourceMode.AUTO, stringResource(UiR.string.src_mode_auto)),
                SegmentedOption(SourceMode.MANUAL, stringResource(UiR.string.src_mode_manual)),
            ),
            selected = prefs.mode,
            onSelect = { vm.setSourceMode(it) },
        )
        Text(
            stringResource(Res.string.settings_source_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(Res.string.settings_prefer), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Segmented(
            options = listOf<SegmentedOption<SourceKind?>>(
                SegmentedOption(null, stringResource(Res.string.settings_prefer_any)),
                SegmentedOption(SourceKind.WEB, stringResource(UiR.string.src_web)),
                SegmentedOption(SourceKind.TORRENT, stringResource(UiR.string.src_torrent)),
            ),
            selected = prefs.prefer,
            onSelect = { vm.setPreferredKind(it) },
            small = true,
        )
        Text(stringResource(Res.string.settings_min_quality), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Segmented(
            options = listOf(Quality.P480, Quality.P720, Quality.P1080).map { SegmentedOption(it, it.label()) },
            selected = prefs.minQuality,
            onSelect = { vm.setMinQuality(it) },
            small = true,
        )
        Text(stringResource(Res.string.settings_sub_lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Segmented(options = langOptions(listOf("es", "en", "pt")), selected = prefs.subLang, onSelect = { vm.setSubLang(it) }, small = true)
        Text(stringResource(Res.string.settings_dub_lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Segmented(options = langOptions(listOf("ja", "es", "en")), selected = prefs.dubLang, onSelect = { vm.setDubLang(it) }, small = true)
    }
}
