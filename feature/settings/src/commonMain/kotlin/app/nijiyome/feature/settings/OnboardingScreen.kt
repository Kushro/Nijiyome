// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.nijiyome.feature.settings

import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.designsystem.direction.Directions
import app.nijiyome.designsystem.direction.ThemeTone
import app.nijiyome.model.AppLanguage
import app.nijiyome.model.AppSettings
import app.nijiyome.model.DesignDirection
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
 * What the wizard covers, in order ([onboardingSteps] drops what a device does not need). DOWNLOADS and SYSTEM are platform steps
 * (desktop: download folder, system rows): only where [platformOnboardingSteps] lists them. Since the Sparkles redesign they are shown
 * as four pages ([onboardingPages]).
 */
internal enum class OnboardingStep { WELCOME, APPEARANCE, PLAYBACK, DOWNLOADS, LISTS, LINK, PERMISSIONS, SYSTEM, DONE }

/** The steps a platform adds itself ([platformOnboardingSteps]); every other step is shared. */
internal val PLATFORM_ONBOARDING_STEPS: Set<OnboardingStep> = setOf(OnboardingStep.DOWNLOADS, OnboardingStep.SYSTEM)

/**
 * The wizard's steps on this device (language, engine and source preferences, AniList / MAL, permissions). PERMISSIONS only when a
 * runtime permission is still worth asking: notifications need one from Android 13 (API 33). Pure, unit tested.
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

/** The four pages of the wizard (artifact `bienvenida`, structure `pasos`). */
internal enum class OnboardingPage { LOOK, LIST, SOURCES, LINK_SYSTEM }

/**
 * [steps] grouped into the four pages: Idioma y aspecto (welcome, language, style), Tu lista y cómo ves (accounts, playback),
 * Fuentes (sources, desktop download folder), Link y sistema (PC, permissions, desktop system). A page with nothing of this device
 * left is dropped, except the first two. Pure, unit tested.
 */
internal fun onboardingPages(steps: List<OnboardingStep>): List<Pair<OnboardingPage, List<OnboardingStep>>> {
    fun pageOf(step: OnboardingStep): OnboardingPage? = when (step) {
        OnboardingStep.WELCOME, OnboardingStep.APPEARANCE -> OnboardingPage.LOOK
        OnboardingStep.PLAYBACK, OnboardingStep.LISTS -> OnboardingPage.LIST
        OnboardingStep.DOWNLOADS -> OnboardingPage.SOURCES
        OnboardingStep.LINK, OnboardingStep.PERMISSIONS, OnboardingStep.SYSTEM -> OnboardingPage.LINK_SYSTEM
        OnboardingStep.DONE -> null
    }
    val grouped = steps.groupBy(::pageOf)
    return OnboardingPage.entries.mapNotNull { page ->
        val inPage = grouped[page].orEmpty()
        // Fuentes always explains where sources come from; Link y sistema only when something of it applies here.
        if (inPage.isEmpty() && page == OnboardingPage.LINK_SYSTEM) null else page to inPage
    }
}

/** First-run wizard state: every choice is written at once; finishing (or Omitir) writes `onboardingDone` and `design.introSeen`. */
class OnboardingViewModel(private val settingsRepo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings

    fun setLanguage(value: AppLanguage) = viewModelScope.launch { settingsRepo.update { it.copy(language = value) } }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { settingsRepo.update { it.copy(themeMode = value) } }

    /** Theme tiles: Sistema, Claro, Oscuro, OLED (= dark on pure black). */
    fun setTone(mode: ThemeMode, oled: Boolean) = viewModelScope.launch { settingsRepo.update { it.copy(themeMode = mode, amoled = oled) } }

    fun setDirection(value: DesignDirection) = viewModelScope.launch {
        settingsRepo.update { it.copy(look = it.look.copy(design = it.look.design.copy(direction = value, palette = null))) }
    }

    fun setDirectionPalette(id: String) = viewModelScope.launch {
        settingsRepo.update { it.copy(dynamicColor = false, look = it.look.copy(design = it.look.design.copy(palette = id))) }
    }

    fun setCanonPalette(id: String) = viewModelScope.launch { settingsRepo.update { it.copy(paletteId = id, dynamicColor = false) } }

    // Playback: the same fields as Ajustes › Reproducción / Fuentes / Subtítulos.
    fun setPlayerEngine(value: PlayerEngine) = viewModelScope.launch { settingsRepo.update { it.copy(playerEngine = value) } }

    fun setSourceMode(value: SourceMode) = updatePrefs { it.copy(mode = value) }

    /** null = any kind. */
    fun setPreferredKind(value: SourceKind?) = updatePrefs { it.copy(prefer = value) }

    fun setMinQuality(value: Quality) = updatePrefs { it.copy(minQuality = value) }

    fun setSubLang(value: String) = updatePrefs { it.copy(subLang = value) }

    fun setDubLang(value: String) = updatePrefs { it.copy(dubLang = value) }

    private fun updatePrefs(transform: (SourcePrefs) -> SourcePrefs) =
        viewModelScope.launch { settingsRepo.update { it.copy(sourcePrefs = transform(it.sourcePrefs)) } }

    /** Done or skipped: a new install never sees the post-update "elige tu estilo" notice (it chose here). */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(onboardingDone = true, look = it.look.copy(design = it.look.design.copy(introSeen = true))) }
            onDone()
        }
    }
}

/**
 * First-run wizard (artifact screen `bienvenida`, structure `pasos`): four pages, Omitir always visible. 1 Idioma y aspecto (language,
 * direction, theme, palette), 2 Tu lista y cómo ves (AniList / MAL through the same [AccountsViewModel] as Settings, engine and source
 * preferences, languages), 3 Fuentes (where episodes come from; desktop: download folder and what happens after watching), 4 Link y
 * sistema (pairing hint, notifications on Android 13+, desktop system rows). Terminar or Omitir writes `onboardingDone = true` and
 * `design.introSeen = true`; the shell reacts to the flag and [onDone] is an extra hook. Shown while `onboardingDone` is false, so it runs
 * again after "Restablecer la app".
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm: OnboardingViewModel = koinViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val koin = getKoin()
    val pages = remember {
        onboardingPages(
            onboardingSteps(
                notificationsNeedPermission = notificationsNeedPermission,
                listsAvailable = koin.getOrNull<ListsRepository>() != null,
                linkAvailable = koin.getOrNull<LinkRepository>() != null,
                platformSteps = platformOnboardingSteps,
            ),
        )
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val (page, steps) = pages[index.coerceIn(0, pages.lastIndex)]
    val last = index >= pages.lastIndex
    val finish = { vm.finish(onDone) }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepStrip(pages.map { it.first }, index, Modifier.weight(1f))
                    TextButton(onClick = finish, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(Res.string.settings_onboarding_skip)) }
                }
                AnimatedContent(targetState = page, modifier = Modifier.weight(1f), label = "onboarding") { current ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        when (current) {
                            OnboardingPage.LOOK -> LookPage(settings, vm)
                            OnboardingPage.LIST -> ListPage(settings, vm, accounts = OnboardingStep.LISTS in steps)
                            OnboardingPage.SOURCES -> SourcesPage(downloads = OnboardingStep.DOWNLOADS in steps)
                            OnboardingPage.LINK_SYSTEM -> LinkSystemPage(steps)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) TextButton(onClick = { index -= 1 }) { Text(stringResource(Res.string.settings_onboarding_back)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (last) finish() else index += 1 }) {
                        Text(
                            stringResource(
                                when {
                                    last -> Res.string.settings_onboarding_finish
                                    index == 0 -> Res.string.settings_onboarding_start
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

/** The four steps with their names; the current one is filled. Announces "Paso 2 de 4". */
@Composable
private fun StepStrip(pages: List<OnboardingPage>, current: Int, modifier: Modifier = Modifier) {
    val position = stringResource(Res.string.rd_s10_onb_step_of, current + 1, pages.size)
    Row(modifier.semantics { stateDescription = position }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        pages.forEachIndexed { i, p ->
            val on = i <= current
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Icon(
                    p.icon(),
                    contentDescription = null,
                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(p.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun OnboardingPage.icon(): ImageVector = when (this) {
    OnboardingPage.LOOK -> Icons.Outlined.Palette
    OnboardingPage.LIST -> Icons.Outlined.PlaylistAddCheck
    OnboardingPage.SOURCES -> Icons.Outlined.Extension
    OnboardingPage.LINK_SYSTEM -> Icons.Outlined.Devices
}

private fun OnboardingPage.labelRes() = when (this) {
    OnboardingPage.LOOK -> Res.string.rd_s10_onb_look
    OnboardingPage.LIST -> Res.string.rd_s10_onb_list
    OnboardingPage.SOURCES -> Res.string.rd_s10_onb_sources
    OnboardingPage.LINK_SYSTEM -> Res.string.rd_s10_onb_link
}

@Composable
private fun PageHeader(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

// ---- 1 Idioma y aspecto: the style (direction and theme) in one step

@Composable
private fun LookPage(settings: AppSettings, vm: OnboardingViewModel) {
    PageHeader(stringResource(Res.string.rd_s10_onb_look_title), stringResource(Res.string.rd_s10_onb_look_body))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(Res.string.settings_language))
        Segmented(
            options = listOf(
                SegmentedOption(AppLanguage.SYSTEM, stringResource(Res.string.settings_lang_system)),
                SegmentedOption(AppLanguage.ES, stringResource(Res.string.settings_lang_es)),
                SegmentedOption(AppLanguage.EN, stringResource(Res.string.settings_lang_en)),
            ),
            selected = settings.language,
            onSelect = { vm.setLanguage(it) },
            fillWidth = true,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(Res.string.rd_s10_design_direction))
        DirectionPicker(settings) { spec -> DesignDirection.entries.firstOrNull { it.id == spec.id }?.let(vm::setDirection) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(Res.string.rd_s10_onb_theme))
        ThemeTiles(settings, vm)
    }
    PaletteRow(settings, onCanonPalette = { vm.setCanonPalette(it) }, onDirectionPalette = { vm.setDirectionPalette(it) }, divider = false)
}

/** Sistema, Claro, Oscuro and OLED, each previewed in the chosen direction's palette. */
@Composable
private fun ThemeTiles(settings: AppSettings, vm: OnboardingViewModel) {
    val spec = Directions.byId(settings.look.design.direction.id)
    val palette = if (spec.id == app.nijiyome.designsystem.direction.CanonDirection.id) spec.palette(settings.paletteId) else spec.palette(settings.look.design.palette)
    val level = settings.contrast.level()
    val oled = settings.amoled && settings.themeMode != ThemeMode.LIGHT
    data class Tile(val mode: ThemeMode, val oled: Boolean, val tone: ThemeTone, val label: String)
    val tiles = listOf(
        Tile(ThemeMode.SYSTEM, false, ThemeTone.DARK, stringResource(Res.string.settings_mode_system)),
        Tile(ThemeMode.LIGHT, false, ThemeTone.LIGHT, stringResource(Res.string.settings_mode_light)),
        Tile(ThemeMode.DARK, false, ThemeTone.DARK, stringResource(Res.string.settings_mode_dark)),
        Tile(ThemeMode.DARK, true, ThemeTone.OLED, stringResource(Res.string.rd_s10_tone_oled)),
    )
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { t ->
            val selected = settings.themeMode == t.mode && oled == t.oled
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.large)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { vm.setTone(t.mode, t.oled) }),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DirectionThumb(palette, t.tone, level)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ---- 2 Tu lista y cómo ves

@Composable
private fun ListPage(settings: AppSettings, vm: OnboardingViewModel, accounts: Boolean) {
    PageHeader(stringResource(Res.string.rd_s10_onb_list_title), stringResource(platformHint(Res.string.settings_onboarding_lists_body)))
    if (accounts) OnboardingAccounts()
    val prefs = settings.sourcePrefs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(stringResource(Res.string.rd_s10_onb_how_you_watch))
        LanguagePickerRow(stringResource(Res.string.settings_sub_lang), prefs.subLang, COMMON_SUB_LANGS, { vm.setSubLang(it) })
        LanguagePickerRow(stringResource(Res.string.settings_dub_lang), prefs.dubLang, COMMON_DUB_LANGS, { vm.setDubLang(it) })
        SettingRow(stringResource(Res.string.settings_min_quality), stack = true) {
            Segmented(
                options = listOf(Quality.P480, Quality.P720, Quality.P1080).map { SegmentedOption(it, it.label()) },
                selected = prefs.minQuality,
                onSelect = { vm.setMinQuality(it) },
                fillWidth = true,
            )
        }
        SettingRow(stringResource(Res.string.settings_source_mode), stringResource(Res.string.settings_source_mode_hint), stack = true) {
            Segmented(
                options = listOf(
                    SegmentedOption(SourceMode.AUTO, stringResource(UiR.string.src_mode_auto)),
                    SegmentedOption(SourceMode.MANUAL, stringResource(UiR.string.src_mode_manual)),
                ),
                selected = prefs.mode,
                onSelect = { vm.setSourceMode(it) },
                fillWidth = true,
            )
        }
        SettingRow(stringResource(Res.string.settings_prefer), stack = true) {
            Segmented(
                options = listOf<SegmentedOption<SourceKind?>>(
                    SegmentedOption(null, stringResource(Res.string.settings_prefer_any)),
                    SegmentedOption(SourceKind.WEB, stringResource(UiR.string.src_web)),
                    SegmentedOption(SourceKind.TORRENT, stringResource(UiR.string.src_torrent)),
                ),
                selected = prefs.prefer,
                onSelect = { vm.setPreferredKind(it) },
                fillWidth = true,
            )
        }
        SettingRow(stringResource(Res.string.settings_engine), stringResource(settings.playerEngine.shown().hintRes()), stack = true) {
            Segmented(options = engineOptions(), selected = settings.playerEngine.shown(), onSelect = { vm.setPlayerEngine(it) }, fillWidth = true)
        }
    }
}

// ---- 3 Fuentes

@Composable
private fun SourcesPage(downloads: Boolean) {
    PageHeader(stringResource(Res.string.rd_s10_onb_sources_title), stringResource(Res.string.rd_s10_onb_sources_body))
    InfoCard(Icons.Outlined.Extension, stringResource(Res.string.rd_s10_onb_sources_where), stringResource(Res.string.settings_onboarding_done_body))
    if (downloads) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(stringResource(Res.string.settings_onboarding_downloads_title))
            OnboardingPlatformStep(OnboardingStep.DOWNLOADS)
        }
    }
}

// ---- 4 Link y sistema

@Composable
private fun LinkSystemPage(steps: List<OnboardingStep>) {
    PageHeader(stringResource(Res.string.rd_s10_onb_link_title), stringResource(Res.string.rd_s10_onb_link_body))
    if (OnboardingStep.LINK in steps) {
        InfoCard(Icons.Outlined.Devices, stringResource(Res.string.settings_onboarding_link_title), stringResource(Res.string.settings_onboarding_link_body))
    }
    if (OnboardingStep.PERMISSIONS in steps) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(stringResource(Res.string.settings_onboarding_permissions_title))
            OnboardingNotifications()
        }
    }
    if (OnboardingStep.SYSTEM in steps) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(stringResource(Res.string.settings_onboarding_system_title))
            OnboardingPlatformStep(OnboardingStep.SYSTEM)
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
