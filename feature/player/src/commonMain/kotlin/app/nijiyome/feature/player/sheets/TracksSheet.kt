// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.player.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nijiyome.feature.player.SubtitleQuickStyleBody
import app.nijiyome.feature.player.SubtitleStyleEditor
import app.nijiyome.feature.player.isMedia3
import app.nijiyome.feature.player.keyLabel
import app.nijiyome.feature.player.needsKey
import app.nijiyome.feature.player.playerCapabilities
import app.nijiyome.feature.player.providerLabel
import app.nijiyome.feature.player.resources.Res
import app.nijiyome.feature.player.resources.*
import app.nijiyome.model.SecretKeys
import app.nijiyome.model.SecretStore
import app.nijiyome.model.SettingsSection
import app.nijiyome.model.TranslationProvider
import app.nijiyome.player.AUDIO_TRACK_OFF
import app.nijiyome.player.PlayerKeymap
import app.nijiyome.player.SubtitleDelay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import java.util.Locale
import app.nijiyome.designsystem.theme.NijiyomeControls

// Sparkles redesign (lane S6): the "Pistas" sheet (artifact `shPistas`, parity PLY-09 / PLY-10 / PLE-16 / PLE-17 / PLE-26): audio and
// subtitles side by side instead of two sheets, with the delay, the second track, the quick style with its live preview (and the full
// editor as a step of the same sheet) and the translation. Picking a track no longer closes the sheet, so the delay can follow.

private const val SUB_OFF = Int.MIN_VALUE
private const val SUB_FILE = Int.MIN_VALUE + 1
private const val SECOND_NONE = Int.MIN_VALUE

@Composable
internal fun TracksSheetContent(
    data: PlayerSheetData,
    actions: PlayerSheetActions,
    openSettings: (SettingsSection) -> Unit,
    onClose: () -> Unit,
) {
    val vm = data.vm
    val style = data.state.settings.subtitleStyle
    val preview: (app.nijiyome.model.SubtitleStyle) -> Unit = actions.onPreviewSubtitleStyle ?: vm::previewSubtitleStyle
    val save: (app.nijiyome.model.SubtitleStyle) -> Unit = actions.onSaveSubtitleStyle ?: vm::saveSubtitleStyle
    var fullStyle by rememberSaveable { mutableStateOf(false) }
    if (fullStyle) {
        // A step of this sheet: every subtitle option, with Back to the tracks.
        SheetHeader(stringResource(Res.string.sub_style_title), onClose = null, onBack = { fullStyle = false })
        SubtitleStyleEditor(style = style, onChange = save, onPreview = preview, showHeader = false, horizontalPadding = 0.dp)
        return
    }
    AudioSection(data)
    SheetDivider()
    SubtitleSection(data, actions)
    SheetDivider()
    SecondTrackSection(data)
    SheetDivider()
    SheetSection(stringResource(Res.string.rd_s6_style_quick)) {
        SubtitleQuickStyleBody(style, preview, save, onMore = { fullStyle = true }, showAss = data.ps.fullAss)
    }
    SheetDivider()
    TranslationSection(data, openSettings, onClose)
}

// ---- audio -------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun AudioSection(data: PlayerSheetData) {
    val ps = data.ps
    SheetSection(stringResource(Res.string.player_audio)) {
        if (ps.audioTracks.isEmpty()) {
            Hint(stringResource(Res.string.rd_s6_audio_none))
        } else {
            val choices = ps.audioTracks.map { a -> Choice(a.id, listOfNotNull(a.label, a.detail).joinToString(" · ")) } +
                Choice(AUDIO_TRACK_OFF, stringResource(Res.string.rd_s6_audio_off))
            ChoiceChips(
                choices = choices,
                selected = ps.audioTracks.firstOrNull { it.selected }?.id ?: AUDIO_TRACK_OFF,
                onSelect = data.session::selectAudio,
            )
        }
        keyHint(data, PlayerKeymap.AUDIO_CYCLE)?.let { Hint(stringResource(Res.string.rd_s6_audio_key_hint, it)) }
    }
}

// ---- subtitles -------------------------------------------------------------------------------------------------------------------------

@Composable
private fun SubtitleSection(data: PlayerSheetData, actions: PlayerSheetActions) {
    val vm = data.vm
    val ps = data.ps
    val custom = data.state.customSubtitle
    SheetSection(stringResource(Res.string.player_subtitles)) {
        if (!ps.fullAss && ps.subtitles.any { it.ass }) StateNote(stringResource(Res.string.player_ass_simplified))
        val choices = buildList {
            add(Choice(SUB_OFF, stringResource(Res.string.rd_s6_subtitles_off)))
            ps.subtitles.forEach { add(Choice(it.groupIndex, it.label)) }
            if (custom != null) add(Choice(SUB_FILE, stringResource(Res.string.player_sub_custom, custom.name)))
        }
        val selected = when {
            custom != null -> SUB_FILE
            !ps.subtitlesEnabled -> SUB_OFF
            else -> ps.subtitles.firstOrNull { it.selected }?.groupIndex ?: SUB_OFF
        }
        ChoiceChips(choices, selected, onSelect = { id ->
            when (id) {
                SUB_OFF -> vm.selectSubtitle(null)
                SUB_FILE -> Unit
                else -> vm.selectSubtitle(id)
            }
        })
        actions.onLoadSubtitleFile?.let { load ->
            OutlinedButton(onClick = load, shape = NijiyomeControls.shape) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.player_sub_load))
            }
        }
        Stepper(
            label = stringResource(Res.string.player_sub_delay),
            valueText = SubtitleDelay.format(ps.subtitleDelayMs),
            onMinus = { vm.stepSubtitleDelay(-1) },
            onPlus = { vm.stepSubtitleDelay(1) },
            minusDescription = stringResource(Res.string.player_sub_delay_earlier),
            plusDescription = stringResource(Res.string.player_sub_delay_later),
            onReset = { vm.stepSubtitleDelay(0) },
            resetDescription = stringResource(Res.string.player_sub_delay_reset),
            resetEnabled = ps.subtitleDelayMs != 0L,
        )
        val earlier = keyHint(data, PlayerKeymap.SUB_DELAY_EARLIER)
        val later = keyHint(data, PlayerKeymap.SUB_DELAY_LATER)
        if (earlier != null && later != null) Hint(stringResource(Res.string.rd_s6_delay_key_hint, earlier, later))
        if (data.session.isMedia3 && ps.subtitleDelayMs < 0) Hint(stringResource(Res.string.player_sub_delay_media3_hint))
    }
}

@Composable
private fun SecondTrackSection(data: PlayerSheetData) {
    val vm = data.vm
    val ps = data.ps
    val state = data.state
    SheetSection(stringResource(Res.string.rd_s6_second_track)) {
        val none = stringResource(Res.string.player_sub_secondary_off)
        when {
            // libmpv draws a second engine track at the top
            data.session.supportsSecondaryTracks -> ChoiceChips(
                choices = listOf(Choice(SECOND_NONE, none)) + ps.subtitles.map { Choice(it.groupIndex, it.label) },
                selected = ps.secondarySubtitleId ?: SECOND_NONE,
                onSelect = { id -> vm.selectSecondary(id.takeIf { it != SECOND_NONE }, null) },
            )
            // Media3: the stream's external tracks, drawn by the screen
            state.externalSubtitles.isEmpty() -> Hint(stringResource(Res.string.player_sub_secondary_none))
            else -> ChoiceChips(
                choices = listOf(Choice(SECOND_NONE, none)) + state.externalSubtitles.mapIndexed { i, t -> Choice(i, t.label.ifBlank { t.lang }) },
                selected = state.secondaryExternal ?: SECOND_NONE,
                onSelect = { i -> vm.selectSecondary(null, i.takeIf { it != SECOND_NONE }) },
            )
        }
    }
}

// ---- translation ---------------------------------------------------------------------------------------------------------------------

@Composable
private fun TranslationSection(data: PlayerSheetData, openSettings: (SettingsSection) -> Unit, onClose: () -> Unit) {
    val translation = data.state.translation
    val t = data.state.settings.subtitleTranslation
    val missingKey = rememberMissingTranslationKey(t.provider)
    val toSettings = stringResource(Res.string.rd_s6_translation_settings)
    SheetSection(stringResource(Res.string.rd_s6_translation)) {
        if (!translation.available) {
            // No engine chosen: the switch would do nothing; say where it is set up.
            StateNote(stringResource(Res.string.rd_s6_translation_off), action = toSettings, onAction = { openSettings(SettingsSection.PLAYER) })
        } else {
            TranslationControls(data, missingKey, toSettings, openSettings, onClose)
        }
    }
}

@Composable
private fun TranslationControls(
    data: PlayerSheetData,
    missingKey: Boolean,
    toSettings: String,
    openSettings: (SettingsSection) -> Unit,
    onClose: () -> Unit,
) {
    val vm = data.vm
    val translation = data.state.translation
    val t = data.state.settings.subtitleTranslation
    run {
        ToggleRow(
            title = stringResource(Res.string.player_sub_translate),
            checked = translation.enabled,
            onChange = vm::setTranslationEnabled,
            hint = stringResource(Res.string.player_sub_translate_hint, providerLabel(t.provider), languageName(t.targetLang)),
            icon = Icons.Rounded.Translate,
        )
        if (missingKey) {
            StateNote(stringResource(Res.string.rd_s6_translation_no_key, providerLabel(t.provider)), NoteTone.WARN, toSettings) {
                openSettings(SettingsSection.PLAYER)
            }
        }
        translation.error?.let { StateNote(it, NoteTone.ERROR, toSettings) { openSettings(SettingsSection.PLAYER) } }
        if (translation.enabled) {
            TextButton(onClick = { vm.pretranslate(); onClose() }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.player_sub_pretranslate)) }
        }
    }
}

/** The engine needs an API key and none is saved (false while unknown or when the key store is not there). */
@Composable
private fun rememberMissingTranslationKey(provider: TranslationProvider): Boolean {
    val koin = getKoin()
    val secrets = remember { koin.getOrNull<SecretStore>() }
    var missing by remember(provider) { mutableStateOf(false) }
    LaunchedEffect(provider, secrets) {
        missing = provider.needsKey() && secrets != null &&
            runCatching { secrets.get(SecretKeys.translationApiKey(provider.id)) == null }.getOrDefault(false)
    }
    return missing
}

// ---- words ---------------------------------------------------------------------------------------------------------------------------

/** The key bound to [action] on desktop, as a label; null on touch or when unbound. */
@Composable
private fun keyHint(data: PlayerSheetData, action: String): String? {
    if (!playerCapabilities.desktopShortcuts) return null
    val key = PlayerKeymap.resolve(data.state.settings.playerKeymap, true)[action] ?: return null
    return keyLabel(key)
}

/** "Español", "English": a language code in the user's language (falls back to the code). */
internal fun languageName(code: String): String {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        ?: code.uppercase()
}
