// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import androidx.compose.runtime.Composable
import app.nijiyome.model.AppSettings
import app.nijiyome.model.LoadState

// What Settings needs from the platform. Android keeps what it always did; desktop has its own side in desktopMain.

/** Ajustes › Sistema (tray, autostart, Discord, title bar, proxy) exists on this platform: desktop yes, Android no. */
internal expect val systemCategoryOffered: Boolean

/** Dynamic color (wallpaper colors). Android 12+. */
internal expect val dynamicColorSupported: Boolean

/** The packages of [packages] that are installed (the external players of Settings › Reproductor). */
@Composable
internal expect fun rememberInstalledPackages(packages: Set<String>, key: Any?): Set<String>

/** "version · id" of the installed app for Acerca de; blank parts are left out by the caller. */
@Composable
internal expect fun rememberAppVersionParts(): List<String>

/** The bundled third-party notices and font licenses, split in paragraphs. Ok(empty) when nothing is bundled. */
@Composable
internal expect fun rememberLicensesLoader(): suspend () -> LoadState<List<String>>

/** After "Restablecer la app": start over (Android recreates the activity, which lands on the first-run wizard). */
@Composable
internal expect fun rememberRestartAfterReset(): () -> Unit

/**
 * Saves a text document to a file the user picks: call the returned function with the suggested file name and the content.
 * [onDone] gets true when it was written, false when it could not be; it is not called when the user cancels.
 */
@Composable
internal expect fun rememberSaveJsonDocument(onDone: suspend (Boolean) -> Unit): (fileName: String, json: String) -> Unit

/** Opens a release page in the browser; nothing happens when nothing can open it. */
@Composable
internal expect fun rememberOpenReleaseUrl(): (url: String) -> Unit

/** Shares the app log file (Android: FileProvider + share sheet). Returns false when it could not. */
@Composable
internal expect fun rememberShareLog(system: SystemViewModel): () -> Boolean

/** true when posting notifications needs a runtime permission (Android 13+): the wizard then has its permissions step. */
internal expect val notificationsNeedPermission: Boolean

/**
 * Opens an OAuth authorize URL. Android: a Custom Tab (any browser second); desktop: the loopback listener, then the system
 * browser. false when nothing can open it.
 */
@Composable
internal expect fun rememberOpenAuthUrl(): (url: String) -> Boolean

/**
 * v1.1 (F6): Listas y cuentas reads other apps' list files too (MyAnimeList XML, AniList dump, Tatakai Next) and exports CSV
 * besides JSON (desktop, as Tatakai Next's desktop). Android: false, its JSON only.
 */
internal expect val listFileFormatsOffered: Boolean

/** Lets the user pick a JSON document to import; [onResult] gets its text, or null when it could not be read. */
@Composable
internal expect fun rememberOpenJsonDocument(onResult: suspend (String?) -> Unit): () -> Unit

/** v1.1 (F6): the wizard steps this platform adds (desktop: download folder, system). Android: none. */
internal expect val platformOnboardingSteps: Set<OnboardingStep>

/** v1.1 (F6): the body of a step of [platformOnboardingSteps], under its header. */
@Composable
internal expect fun OnboardingPlatformStep(step: OnboardingStep)

/** The notifications row of the wizard's permissions step. */
@Composable
internal expect fun OnboardingNotifications()

/**
 * Settings › Reproductor: the frame generation rows. Android: the phone's (:core:framegen). Desktop: lsfg-vk on the external mpv
 * window.
 */
@Composable
internal expect fun FrameGenSettingsSlot()

/** v1.1 (F2): presets only this platform's player has (desktop: NVScaler); Android: none. */
internal expect val extraPresets: List<String>

/** v1.1 (F2): Settings › Reproductor, under the decoder choice. Android: nothing. Desktop: which decoder the embedded player uses. */
@Composable
internal expect fun DecoderStatusSlot()

/**
 * Settings › Reproductor, under the engine choice while it is External. Android: which app the video is handed to. Desktop: the mpv
 * that runs in its own window and its enhanced mode (mpv-anime-build).
 */
@Composable
internal expect fun ExternalPlayerSettingsRows(state: SettingsUiState, vm: SettingsViewModel)

/** The hint of the External engine choice. */
internal expect val externalEngineHintRes: org.jetbrains.compose.resources.StringResource

/** The hint of the libmpv engine choice (Android: processed on the phone; desktop: inside the window). */
internal expect val mpvEngineHintRes: org.jetbrains.compose.resources.StringResource

/** Engines this platform can play with, in display order. Desktop has no Media3. */
internal expect val offeredEngines: List<app.nijiyome.model.PlayerEngine>

/** A hint written for the phone (battery, heat) replaced by this platform's own wording; Android returns [res] itself. */
internal expect fun platformHint(res: org.jetbrains.compose.resources.StringResource): org.jetbrains.compose.resources.StringResource

/**
 * The embed sources rows ([rows]: the "Fuentes embed" switch and its timeout), which need the hidden browser. Android: [rows] as they
 * were. Desktop (1.1, F4): the rows of the browser in use (path, "Probar") and [rows] while a Chrome / Edge / Chromium is there.
 */
@Composable
internal expect fun EmbedSourcesRows(rows: @Composable () -> Unit)

/** v1.1: the "verify certificates" row of libmpv (Android's libmpv; the desktop engines keep their own TLS defaults). */
internal expect val mpvTlsSettingShown: Boolean

/** Versions of what is bundled, shown in Acerca de (technical strings, not translated). */
internal expect val componentVersions: List<Pair<String, String>>

/** The hint of Ajustes › Datos: where this platform keeps the app's data. */
@Composable
internal expect fun dataLocationHint(): String

/** Ajustes › Datos: rows only this platform has (desktop: tray, autostart, Discord). Android: none. */
@Composable
internal expect fun PlatformSystemRows()

/** Hito 6 (D1): the row under the hint of Ajustes › Datos that opens the data folder (desktop). Android: none. */
@Composable
internal expect fun DataFolderRow()

/** Hito 6 (D1): "Restablecer la app" asks to type a word and offers to remove the torrents too (desktop). Android: its dialog. */
internal expect val resetNeedsTypedWord: Boolean

/** Hito 6 (D1): Ajustes › Metadatos › caché has its own "Vaciar" (desktop). Android: the row shows only the size. */
internal expect val metadataCacheClearOffered: Boolean

/**
 * Hito 6 (D1): the snackbar text after [rememberOpenAuthUrl] answered false. Android: "no browser" (its only reason). Desktop: also
 * "the loopback port is taken".
 */
@Composable
internal expect fun rememberAuthOpenFailureText(): suspend () -> String

