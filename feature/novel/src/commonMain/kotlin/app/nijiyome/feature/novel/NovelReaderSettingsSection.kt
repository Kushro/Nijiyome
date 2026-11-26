// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.novel

import app.nijiyome.designsystem.components.focusOnOpen
import app.nijiyome.feature.novel.resources.Res
import app.nijiyome.feature.novel.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.nijiyome.ui.platform.rememberFilePicker
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nijiyome.designsystem.components.Segmented
import app.nijiyome.designsystem.components.SegmentedOption
import app.nijiyome.model.AppSettings
import app.nijiyome.model.Narrator
import app.nijiyome.model.NovelReaderSettings
import app.nijiyome.model.SecretKeys
import app.nijiyome.model.SecretStore
import app.nijiyome.model.TtsEngine
import app.nijiyome.model.TtsSettings
import app.nijiyome.model.TtsVoice
import app.nijiyome.novel.LocalVoiceInstaller
import app.nijiyome.novel.NarratorEngineStatus
import app.nijiyome.novel.NarratorOptions
import app.nijiyome.novel.NarratorProblem
import app.nijiyome.novel.TtsException
import app.nijiyome.novel.TtsFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import app.nijiyome.ui.resources.ui_cancel
import java.io.File
import kotlin.math.roundToInt
import app.nijiyome.designsystem.theme.NijiyomeControls

/**
 * Settings › Lectores y narrador › Novela (NOV-12, NOV-15 to NOV-18; lane S10 embeds it right after `ReaderSettingsSection`):
 * typography with its live preview on top (font presets, installed family, imported TTF / OTF, size, line height, paragraph
 * spacing, width, colours, justify), reading flow, and the narrator (engine, voice with "Probar voz", speed, pitch, highlight,
 * server / model, API key in SecretStore shown as "guardada" / "Quitar", the local KittenTTS model).
 */
@Composable
fun NovelReaderSettingsSection(settings: AppSettings, onUpdate: (transform: (AppSettings) -> AppSettings) -> Unit, modifier: Modifier = Modifier) {
    val koin = getKoin()
    val narrator = remember { koin.getOrNull<Narrator>() }
    val secrets = remember { koin.getOrNull<SecretStore>() }
    val reader = settings.novelReader.sanitized()
    var preview by remember { mutableStateOf<NovelReaderSettings?>(null) }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title(stringResource(Res.string.novel_settings_section))
        NovelPreview(preview ?: reader)
        NovelTypographyRows(reader, onPreview = { preview = it }) { t -> preview = null; onUpdate { it.copy(novelReader = t(it.novelReader).sanitized()) } }
        NovelFlowRows(reader) { t -> onUpdate { it.copy(novelReader = t(it.novelReader).sanitized()) } }
        Title(stringResource(Res.string.novel_narration_section))
        NarratorRows(settings.tts.sanitized(), narrator, secrets) { t -> onUpdate { it.copy(tts = t(it.tts).sanitized()) } }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) = NovelSwitch(title, hint, checked, onChange)

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: @Composable (Float) -> String, onDone: (Float) -> Unit) =
    NovelSlider(title, value, range, steps, label, onDone = onDone)

/** A few paragraphs in the chosen typography and colours (the live preview of NOV-12 / NOV-15). */
@Composable
internal fun NovelPreview(reader: NovelReaderSettings, modifier: Modifier = Modifier) {
    val palette = novelPalette(reader.background)
    val family = remember(reader.font, reader.customFontPath) { novelFontFamily(reader.font, reader.customFontPath) }
    Surface(color = palette.background, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.novel_settings_preview_text),
            style = TextStyle(
                fontFamily = family,
                fontSize = reader.sizeSp.sp,
                lineHeight = (reader.sizeSp * reader.lineHeight).sp,
                letterSpacing = dyslexicSpacing(reader.font).em,
                color = palette.text,
                textAlign = if (reader.textAlignJustify) TextAlign.Justify else TextAlign.Start,
            ),
            maxLines = 6,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Typography (NOV-12 / NOV-15; also the reader's "Texto y diseño" panel): fonts as chips written in their own face, the installed
 * family (desktop), import / remove a TTF or OTF, size, line height, paragraph spacing, width, colours and justify. [onPreview]
 * follows a slider while it is dragged, so the preview (and the reader's text) change live before the value is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelTypographyRows(
    reader: NovelReaderSettings,
    onPreview: (NovelReaderSettings?) -> Unit = {},
    onUpdate: ((NovelReaderSettings) -> NovelReaderSettings) -> Unit,
) {
    val importFont = rememberFontImporter()
    val scope = rememberCoroutineScope()
    var fontError by remember { mutableStateOf(false) }
    var pickingFamily by remember { mutableStateOf(false) }
    if (pickingFamily) {
        InstalledFontDialog(
            current = NovelReaderSettings.installedFamily(reader.font),
            onPick = { name ->
                pickingFamily = false
                onUpdate { it.copy(font = NovelReaderSettings.FAMILY_PREFIX + name) }
            },
            onDismiss = { pickingFamily = false },
        )
    }
    val importer = rememberFilePicker(
        listOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "font/sfnt", "application/octet-stream"),
        multiple = false,
    ) { picked ->
        val uri = picked.firstOrNull() ?: return@rememberFilePicker
        scope.launch {
            val path = withContext(Dispatchers.IO) { runCatching { importFont(uri) }.getOrNull() }
            fontError = path == null
            if (path != null) onUpdate { it.copy(font = "custom", customFontPath = path) }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NovelGroup(stringResource(Res.string.rd_s11_typography)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                novelFonts().forEach { f ->
                    if (f == "custom" && reader.customFontPath == null) return@forEach
                    val family = remember(f, reader.customFontPath) { novelFontFamily(f, reader.customFontPath) }
                    FilterChip(selected = reader.font == f, onClick = { onUpdate { it.copy(font = f) } }, label = { Text(fontLabel(f), fontFamily = family) })
                }
                // Desktop (Tatakai Next `family:` fonts): any family installed on the computer.
                if (installedFontsOffered) {
                    val family = NovelReaderSettings.installedFamily(reader.font)
                    FilterChip(
                        selected = family != null,
                        onClick = { pickingFamily = true },
                        label = { Text(family ?: stringResource(Res.string.novel_font_installed)) },
                        leadingIcon = { Icon(Icons.Outlined.FontDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { importer() },
                    label = { Text(stringResource(Res.string.novel_settings_font_import)) },
                    leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            val installed = reader.customFontPath
            if (installed != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.novel_settings_font_installed, File(installed).name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        runCatching { File(installed).delete() }
                        onUpdate { it.copy(customFontPath = null, font = if (it.font == "custom") "serif" else it.font) }
                    }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_settings_font_remove)) }
                }
            }
            if (fontError) Text(stringResource(Res.string.novel_settings_font_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            NovelSlider(
                stringResource(Res.string.novel_settings_size), reader.sizeSp.toFloat(), 10f..40f, 29,
                label = { stringResource(Res.string.novel_settings_sp, it.roundToInt()) },
                onLive = { v -> onPreview(reader.copy(sizeSp = v.roundToInt())) },
            ) { v -> onUpdate { it.copy(sizeSp = v.roundToInt()) } }
            NovelSlider(
                stringResource(Res.string.novel_settings_line_height), reader.lineHeight, 1f..2.5f, 14,
                label = { "%.1f×".format(it) },
                onLive = { v -> onPreview(reader.copy(lineHeight = (v * 10).roundToInt() / 10f)) },
            ) { v -> onUpdate { it.copy(lineHeight = (v * 10).roundToInt() / 10f) } }
            NovelSlider(
                stringResource(Res.string.novel_settings_paragraph_spacing), reader.paragraphSpacingDp.toFloat(), 0f..48f, 23,
                label = { stringResource(Res.string.novel_settings_dp, it.roundToInt()) },
                onLive = { v -> onPreview(reader.copy(paragraphSpacingDp = v.roundToInt())) },
            ) { v -> onUpdate { it.copy(paragraphSpacingDp = v.roundToInt()) } }
            NovelSlider(
                stringResource(Res.string.novel_settings_width), reader.maxWidthDp.toFloat(), 0f..1600f, 31,
                label = { if (it.roundToInt() == 0) stringResource(Res.string.novel_settings_no_limit) else stringResource(Res.string.novel_settings_dp, it.roundToInt()) },
                onLive = { v -> onPreview(reader.copy(maxWidthDp = (v.roundToInt() / 50) * 50)) },
            ) { v -> onUpdate { it.copy(maxWidthDp = (v.roundToInt() / 50) * 50) } }
            NovelSwitch(stringResource(Res.string.novel_settings_justify), null, reader.textAlignJustify) { v -> onUpdate { it.copy(textAlignJustify = v) } }
        }
        NovelGroup(stringResource(Res.string.novel_settings_background)) {
            NovelSwatches(
                listOf(
                    "theme" to stringResource(Res.string.novel_bg_theme),
                    "sepia" to stringResource(Res.string.novel_bg_sepia),
                    "white" to stringResource(Res.string.novel_bg_white),
                    "gray" to stringResource(Res.string.novel_bg_gray),
                    "black" to stringResource(Res.string.novel_bg_black),
                ),
                colorOf = { novelPalette(it).background },
                selected = reader.background,
                onSelect = { v -> onUpdate { it.copy(background = v) } },
            )
        }
    }
}

/** Reading flow (NOV-12 / NOV-15): continuous scroll, progress bar, fullscreen on open, keep the screen on. */
@Composable
internal fun NovelFlowRows(reader: NovelReaderSettings, onUpdate: ((NovelReaderSettings) -> NovelReaderSettings) -> Unit) {
    NovelGroup(stringResource(Res.string.rd_s11_reading_flow)) {
        NovelSwitch(stringResource(Res.string.novel_settings_continuous), stringResource(Res.string.novel_settings_continuous_hint), reader.continuousScroll) { v -> onUpdate { it.copy(continuousScroll = v) } }
        NovelSwitch(stringResource(Res.string.novel_settings_progress_bar), null, reader.showProgressBar) { v -> onUpdate { it.copy(showProgressBar = v) } }
        NovelSwitch(stringResource(Res.string.novel_settings_fullscreen), null, reader.fullscreenOnOpen) { v -> onUpdate { it.copy(fullscreenOnOpen = v) } }
        NovelSwitch(stringResource(Res.string.novel_settings_keep_on), null, reader.keepScreenOn) { v -> onUpdate { it.copy(keepScreenOn = v) } }
    }
}

@Composable
private fun engineLabel(engine: TtsEngine): String = stringResource(
    when (engine) {
        TtsEngine.SYSTEM -> Res.string.novel_narration_engine_system
        TtsEngine.OPENAI -> Res.string.novel_narration_engine_openai
        TtsEngine.ELEVENLABS -> Res.string.novel_narration_engine_elevenlabs
        TtsEngine.OPENAI_COMPATIBLE -> Res.string.novel_narration_engine_compatible
        TtsEngine.KITTEN -> Res.string.novel_narration_engine_kitten
    },
)

/** Localized cause of a narration failure (also the reader's narration bar). */
@Composable
internal fun ttsErrorText(error: Throwable): String = when ((error as? TtsException)?.reason) {
    TtsFailure.MISSING_KEY -> stringResource(Res.string.novel_narration_error_key)
    TtsFailure.MISSING_VOICE -> stringResource(Res.string.novel_narration_error_voice)
    TtsFailure.MISSING_MODEL -> stringResource(Res.string.novel_narration_error_model)
    TtsFailure.BAD_ENDPOINT -> stringResource(Res.string.novel_narration_error_endpoint)
    TtsFailure.HTTP -> stringResource(Res.string.novel_narration_error_http, (error as TtsException).httpCode ?: 0)
    TtsFailure.NOT_AUDIO, TtsFailure.EMPTY_AUDIO -> stringResource(Res.string.novel_narration_error_audio)
    TtsFailure.NETWORK -> stringResource(Res.string.novel_narration_error_network)
    TtsFailure.ENGINE_UNAVAILABLE -> stringResource(Res.string.novel_narration_error_engine)
    TtsFailure.LOCAL_NOT_INSTALLED -> stringResource(Res.string.novel_narration_error_local_missing)
    TtsFailure.PHONEMIZER_MISSING -> stringResource(Res.string.novel_narration_error_phonemizer)
    null -> stringResource(Res.string.novel_narration_error_other, error.message ?: error::class.java.simpleName)
}

/** Narrator rows (checklist novel.tts.system / .openai / .elevenlabs / .openai-compatible / .controls). */
@Composable
private fun NarratorRows(tts: TtsSettings, narrator: Narrator?, secrets: SecretStore?, onUpdate: ((TtsSettings) -> TtsSettings) -> Unit) {
    val scope = rememberCoroutineScope()
    ListItem(
        headlineContent = { Text(stringResource(Res.string.novel_narration_engine)) },
        supportingContent = {
            Segmented(
                // 1.1: the engines of this platform (Android: the four it always had; desktop adds the local KittenTTS).
                options = NarratorOptions.enginesOf(narrator).map { SegmentedOption(it, engineLabel(it)) },
                selected = tts.engine,
                onSelect = { e -> onUpdate { it.copy(engine = e, voice = null) } },
                small = true,
            )
        },
    )
    val options = narrator as? NarratorOptions
    var installVersion by remember { mutableStateOf(0) }
    if (options == null) {
        if (tts.engine == TtsEngine.SYSTEM) {
            Text(stringResource(Res.string.novel_narration_system_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
    } else {
        // Desktop (1.1): what speaks here, or why it cannot (no speech-dispatcher / espeak-ng, KittenTTS not downloaded, …).
        NarratorStatusRow(options, tts.engine, installVersion)
        if (tts.engine == TtsEngine.KITTEN) options.localVoice?.let { local -> LocalVoiceRows(local, tts) { installVersion++ } }
    }

    // Voices of the engine (system voices, OpenAI list, ElevenLabs / server voices).
    var voices by remember(tts.engine) { mutableStateOf<List<TtsVoice>?>(null) }
    var keyVersion by remember { mutableStateOf(0) }
    LaunchedEffect(tts.engine, tts.endpoint, tts.model, keyVersion, narrator, installVersion) {
        voices = runCatching { narrator?.voices(tts.engine) }.getOrNull().orEmpty()
    }
    var voiceMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(Res.string.novel_narration_voice)) },
        supportingContent = {
            Column {
                val current = voices?.firstOrNull { it.id == tts.voice }?.label ?: tts.voice ?: stringResource(Res.string.novel_narration_voice_default)
                Box {
                    OutlinedButton(onClick = { voiceMenu = true }, enabled = !voices.isNullOrEmpty(), shape = NijiyomeControls.shape) { Text(current) }
                    DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.novel_narration_voice_default)) }, onClick = { voiceMenu = false; onUpdate { it.copy(voice = null) } })
                        voices.orEmpty().forEach { v ->
                            DropdownMenuItem(text = { Text(v.label) }, onClick = { voiceMenu = false; onUpdate { it.copy(voice = v.id) } })
                        }
                    }
                }
                if (voices != null && voices!!.isEmpty()) {
                    Text(stringResource(Res.string.novel_narration_voices_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (tts.engine == TtsEngine.ELEVENLABS || tts.engine == TtsEngine.OPENAI_COMPATIBLE) {
                    var manual by remember(tts.engine) { mutableStateOf(tts.voice.orEmpty()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = manual, onValueChange = { manual = it }, singleLine = true, label = { Text(stringResource(Res.string.novel_narration_voice_id)) }, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onUpdate { it.copy(voice = manual.trim().ifEmpty { null }) } }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_save)) }
                    }
                }
            }
        },
    )
    SliderRow(stringResource(Res.string.novel_narration_speed), tts.speed, 0.5f..2f, 14, label = { stringResource(Res.string.novel_narration_speed_value, speedText(it)) }) { v ->
        onUpdate { it.copy(speed = (v * 10).roundToInt() / 10f) }
    }
    if (options?.hasPitch(tts.engine) == true) {
        SliderRow(stringResource(Res.string.novel_narration_pitch), tts.pitch, 0.5f..2f, 14, label = { stringResource(Res.string.novel_narration_speed_value, speedText(it)) }) { v ->
            onUpdate { it.copy(pitch = (v * 10).roundToInt() / 10f) }
        }
    }
    SwitchRow(stringResource(Res.string.novel_narration_highlight), null, tts.highlight) { v -> onUpdate { it.copy(highlight = v) } }

    if (tts.engine == TtsEngine.OPENAI || tts.engine == TtsEngine.OPENAI_COMPATIBLE || tts.engine == TtsEngine.ELEVENLABS) {
        var endpoint by remember(tts.endpoint) { mutableStateOf(tts.endpoint) }
        var model by remember(tts.model) { mutableStateOf(tts.model) }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tts.engine == TtsEngine.OPENAI_COMPATIBLE) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.novel_narration_endpoint)) },
                    placeholder = { Text("http://127.0.0.1:8880/v1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.novel_narration_model)) },
                placeholder = {
                    Text(
                        when (tts.engine) {
                            TtsEngine.OPENAI -> "gpt-4o-mini-tts"
                            TtsEngine.ELEVENLABS -> "eleven_multilingual_v2"
                            else -> "kokoro"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onUpdate { it.copy(endpoint = endpoint.trim(), model = model.trim()) } }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_save)) }
        }
        // API key (SecretStore, SecretKeys.ttsApiKey): "saved" / "remove", never shown.
        val secretKey = SecretKeys.ttsApiKey(tts.engine.id)
        var saved by remember(secretKey) { mutableStateOf(false) }
        var keyInput by remember(secretKey) { mutableStateOf("") }
        LaunchedEffect(secretKey, secrets, keyVersion) { saved = runCatching { secrets?.get(secretKey) != null }.getOrDefault(false) }
        ListItem(
            headlineContent = { Text(stringResource(Res.string.novel_narration_api_key)) },
            supportingContent = {
                Column {
                    Text(
                        when {
                            secrets == null -> stringResource(Res.string.novel_narration_unavailable)
                            saved -> stringResource(Res.string.novel_narration_key_saved)
                            tts.engine == TtsEngine.OPENAI_COMPATIBLE -> stringResource(Res.string.novel_narration_key_optional)
                            else -> stringResource(Res.string.novel_narration_key_missing)
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(Res.string.novel_narration_api_key)) },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val value = keyInput.trim()
                                scope.launch {
                                    runCatching { secrets?.put(secretKey, value) }
                                    keyInput = ""
                                    keyVersion++
                                }
                            },
                            enabled = secrets != null && keyInput.isNotBlank(),
                            shape = NijiyomeControls.shape,
                        ) { Text(stringResource(Res.string.novel_save)) }
                    }
                    if (saved) {
                        TextButton(onClick = { scope.launch { runCatching { secrets?.put(secretKey, null) }; keyVersion++ } }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_narration_key_remove)) }
                    }
                }
            },
        )
    }

    // "Probar voz".
    val previewText = stringResource(Res.string.novel_narration_preview_sentence)
    var testing by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<Throwable?>(null) }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = {
                testing = true
                testError = null
                scope.launch {
                    val r = try {
                        narrator?.preview(previewText) ?: Result.failure(TtsException(TtsFailure.ENGINE_UNAVAILABLE))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    testError = r.exceptionOrNull()
                    testing = false
                }
            },
            enabled = !testing,
            shape = NijiyomeControls.shape,
        ) { Text(stringResource(if (testing) Res.string.novel_narration_testing else Res.string.novel_narration_test)) }
        if (testing) TextButton(onClick = { narrator?.stop(); testing = false }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_narration_stop)) }
    }
    testError?.let { Text(ttsErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
}

/** Desktop: the backend that speaks for [engine], or the reason it cannot. */
@Composable
private fun NarratorStatusRow(options: NarratorOptions, engine: TtsEngine, refresh: Int) {
    val status by produceState<NarratorEngineStatus?>(null, engine, refresh) { value = runCatching { options.status(engine) }.getOrNull() }
    val st = status ?: return
    val text = when (st.problem) {
        null -> stringResource(Res.string.novel_narration_status_ok, st.backend.orEmpty())
        NarratorProblem.NO_SYSTEM_VOICE -> stringResource(Res.string.novel_narration_status_no_system)
        NarratorProblem.NOT_INSTALLED -> stringResource(Res.string.novel_narration_status_not_installed)
        NarratorProblem.NO_PHONEMIZER -> stringResource(Res.string.novel_narration_status_no_phonemizer)
        NarratorProblem.UNSUPPORTED_PLATFORM -> stringResource(Res.string.novel_narration_status_unsupported)
        NarratorProblem.BROKEN -> stringResource(Res.string.novel_narration_status_broken, st.backend.orEmpty())
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (st.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        st.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Desktop KittenTTS: the model, its download (with progress, every file checked against a pinned SHA-256) and "Quitar". */
@Composable
private fun LocalVoiceRows(local: LocalVoiceInstaller, tts: TtsSettings, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    val progress by local.progress.collectAsState()
    val model = local.models.firstOrNull { it.id == tts.model } ?: local.models.firstOrNull { it.id == local.defaultModel } ?: return
    var installed by remember(model.id) { mutableStateOf(local.installed(model.id)) }
    var error by remember { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text(model.label) },
        supportingContent = {
            Column {
                val p = progress
                when {
                    p != null -> {
                        val total = p.total
                        Text(stringResource(if (p.stage == "runtime") Res.string.novel_narration_local_downloading_runtime else Res.string.novel_narration_local_downloading_model, mb(p.done), total?.let { mb(it) } ?: "?"))
                        if (total != null && total > 0) LinearProgressIndicator(progress = { (p.done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    installed -> Text(stringResource(Res.string.novel_narration_local_installed))
                    else -> Text(stringResource(Res.string.novel_narration_local_size, model.approxMb))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        trailingContent = {
            if (progress == null) {
                if (installed) {
                    TextButton(onClick = {
                        scope.launch {
                            local.remove()
                            installed = local.installed(model.id)
                            onChanged()
                        }
                    }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_narration_key_remove)) }
                } else {
                    FilledTonalButton(onClick = {
                        error = null
                        scope.launch {
                            local.install(model.id).exceptionOrNull()?.let { error = it.message ?: it::class.java.simpleName }
                            installed = local.installed(model.id)
                            onChanged()
                        }
                    }, shape = NijiyomeControls.shape) { Text(stringResource(Res.string.novel_narration_local_download)) }
                }
            }
        },
    )
}

private fun mb(bytes: Long): String = String.format(java.util.Locale.getDefault(), "%.1f", bytes / 1_048_576.0)

/** Font file checks (JVM-tested). */
/** The installed font families, filtered by what is typed; each name is shown in its own font. */
@Composable
private fun InstalledFontDialog(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val families by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { installedFontFamilies() } }
    var query by remember { mutableStateOf(current.orEmpty()) }
    val shown = remember(families, query) { filterFamilies(families, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.novel_font_installed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.novel_font_installed_search)) },
                    modifier = Modifier.focusOnOpen().fillMaxWidth(),
                )
                if (families.isNotEmpty() && shown.isEmpty()) Text(stringResource(Res.string.novel_font_installed_none), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it }) { name ->
                        val family = remember(name) { installedFontFamily(name) }
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family),
                            color = if (name == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(query.trim()) }, enabled = query.isNotBlank() && installedFontFamily(query.trim()) != null, shape = NijiyomeControls.shape) {
                Text(stringResource(Res.string.novel_font_installed_use))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = NijiyomeControls.shape) { Text(stringResource(app.nijiyome.ui.resources.Res.string.ui_cancel)) } },
    )
}

/** The families whose name contains [query] (any case), the ones that start with it first. */
internal fun filterFamilies(families: List<String>, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return families
    val matching = families.filter { it.contains(q, ignoreCase = true) }
    return matching.filter { it.startsWith(q, ignoreCase = true) } + matching.filterNot { it.startsWith(q, ignoreCase = true) }
}

internal object FontFiles {
    const val DIR: String = "fonts/novel"

    /** TrueType (`00 01 00 00` / `true`), OpenType CFF (`OTTO`), collections (`ttcf`). */
    fun isFont(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val tag = bytes.copyOfRange(0, 4)
        return tag.contentEquals(byteArrayOf(0, 1, 0, 0)) || String(tag, Charsets.ISO_8859_1) in setOf("OTTO", "true", "ttcf")
    }

    fun safeName(name: String, bytes: ByteArray): String {
        val ext = if (bytes.size >= 4 && String(bytes.copyOfRange(0, 4), Charsets.ISO_8859_1) == "OTTO") "otf" else "ttf"
        val stem = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "font" }.take(48)
        return "$stem.$ext"
    }
}
