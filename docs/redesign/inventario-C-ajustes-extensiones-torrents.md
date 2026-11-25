# Parity inventory C: Settings, Extensions, Torrents, Downloads, Link + Design system

> **Hard rule of the redesign:** no current functionality may be lost. Each `ID` below is a checklist item: the redesign must keep the surface, or explicitly move it elsewhere, along with every control listed under it.

- **Snapshot:** branch `Kushro/Redesign-sparkles` @ `2c62582` (v1.2.1), read-only code reading on 2026-11-25. Nothing was built or run, so all states come from reading the code.
- **Scope:** `feature/settings`, `feature/extensions`, `feature/torrents`, `feature/downloads`, `feature/link`, the desktop Link Monitor (`desktopApp/.../system/LinkMonitorDialog.kt`), and the settings rows that other modules embed in Settings (player, sources, discover, manga/novel reader, TTS). Also `core/designsystem` + `tokens/`.
- **Out of scope:** shell, home, details and search (inventory A); player, manga and novel screens (inventory B). Cross-links to them are noted where a surface here opens one of theirs.
- **Labels:** the English string resources (`composeResources/values/*.xml`). Hardcoded Spanish literals are marked **Spanish literal**.
- **Platform:** *both* / *desktop* / *android*. Platform gating is done with `expect/actual` and Koin `getOrNull<…>()` bindings, so a missing binding hides a whole section (Accounts, Network, Link server and others).

## ID scheme
| Prefix | Area | Part |
|---|---|---|
| `SET-00…04` | Settings shell, search, Ctrl+P modal, Ctrl+K entries, highlight | 1 |
| `SET-APP-*`, `SET-PLY-*`, `SET-MET-*`, `SET-ACC-*`, `SET-LNK-*`, `SET-LNKSRV-*`, `SET-NET-*`, `SET-TOR`, `SET-DL`, `SET-RDR`, `SET-DAT-*`, `SET-ABT-*`, `SET-ONB` | Settings sections + first-run wizard | 1 |
| `TOR-01…27` | Torrent client (library, detail tabs, files, peers, trackers, downloads, history, debrid, add torrent, torrent settings) | 2 |
| `DL-01…16` | Downloads page, auto-download, local library, season download, Settings › Downloads | 2 |
| `EXT-01…16` | Extensions hub, install (file, link, path, drop), repos, Stremio, providers, PC server extensions | 3 |
| `LNK-01…20` | Linked device (client), extension servers, Link Monitor, title bar chip | 3b |
| `SET-PLY-EXTRA/SUBS/SRC`, `SET-MET-DISC`, `SET-RDR-MANGA/NOVEL/TTS` | Settings rows owned by other modules | 4 |

---

# Part 1. Settings (`feature/settings`)

Paths below are relative to `feature/settings/src/` unless noted. `common` = `commonMain/kotlin/app/nijiyome/feature/settings/`, `desktop` = `desktopMain/...`, `android` = `androidMain/...`.

**Section order** (`common/SettingsScreen.kt:225`): Appearance, Player, Metadata, Accounts (only when `ListsRepository` is bound), Nijiyome Link, Network (only when `NetworkSettingsController` is bound = desktop), Torrents, Downloads, Reader, Data, About.

**Row primitives** (`common/SettingsControls.kt`): `SectionPanel` (icon + title card), `SettingRow` (label + hint, control on the right or stacked below, hairline divider), `SwitchRow` (whole row toggles), `NavRow` (ListItem with chevron), `SectionHint` (muted paragraph). Nearly every choice is a `Segmented` control from the design system.

## SET-00 Settings screen shell (hub / master-detail)
- **Source:** `common/SettingsScreen.kt:106` (`SettingsScreen`), `:276` (`SettingsContent`). **Platform:** both.
- **How to get there:** Desktop / wide windows (≥840 dp): the rail destination `Route.Settings`. Phones: You tab › Settings. Deep link `Route.SettingsSection(section)`, used from Library ("Accounts"), the update snackbar (About) and the Link Monitor (Link). Also the Ctrl+P modal (SET-02) and the Ctrl+K palette (SET-03).
- **Wide layout (≥840 dp):** a 264 dp left column with the search field (SET-01) and a `NavigationDrawerItem` per section (icon + title, selected highlight). The right pane, max 840 dp, shows the selected `SectionDetailPane`. Torrents opens its subpage (TOR-25) inline in the right pane.
- **Compact layout:** the **Category Hub** is a LazyColumn with a "Settings" headline when there is no back arrow, the search field, then one card per section showing icon, title, a **live one-line summary** and a chevron. A tap opens that section as a full page with a top app bar back arrow, and back returns to the hub. Torrents › Torrent settings opens a full-page `TorrentSettingsPage`.
- **Live summaries** (`:722`):
  - Appearance: "AMOLED/Claro/Oscuro/Sistema · {paletteId}"
  - Player: "libmpv/Media3/Externo · Auto/Manual"
  - Metadata: "N proveedores · romaji"
  - Accounts: "Sin cuentas vinculadas" or "N vinculadas"
  - Link: "Vinculado" or "PC ↔ Teléfono y sincronización"
  - Network: "Proxy SOCKS5/HTTP · FlareSolverr"
  - Torrents: "Motor, límites y Debrid"
  - Downloads: "Descargas web y almacenamiento"
  - Reader: "Manga, novelas y narrador TTS"
  - Data: "{size} en caché"
  - About: "Registros de error, versión y licencias"
- **Events → snackbar:** "Settings saved", "Synced", "Cache cleared ({bytes})", "That did not work. Try again.". `ResetDone` restarts the app.
- **UX notes:** All summaries are **hardcoded Spanish** and the palette summary shows the raw id (`nijiyome-violet`), not its label. Torrents and Downloads are split across Settings and their own screens.

## SET-01 Settings live search + results
- **Source:** `common/SettingsScreen.kt:471` (field), `:585` (results list), `:621` (result card); index in `common/SettingsSearchIndex.kt`. **Platform:** both.
- **Controls:** a text field ("Buscar ajuste…", search icon, clear ×). Results show "Resultados para «q» (n)", then cards with the section icon, title, subtitle and a section pill. Selecting a result opens that section and shows the **highlight banner** (SET-04).
- **Index:** 39 bilingual, accent-folded entries (`SettingsCatalog.items`). Scoring is prefix 4 > word-start 3 > contains 2 > keyword 1, and every token must match. Entries are filtered to the sections available on the platform.
- **Empty state:** "Sin coincidencias", with example terms (AMOLED, proxy, subtítulos, logs, debrid).
- **UX notes:** Search lands on the **section**, not on the row: there is no scroll-to or flash on the actual control, only a banner with the setting's title. Index titles and subtitles are Spanish-only text even in English UI. The index is hand-maintained and can drift from the real rows; the subtitle claims "20 temas curados", which matches the 20 palettes today.

## SET-02 Settings modal (desktop Ctrl+P / Ctrl+,)
- **Source:** `common/SettingsScreen.kt:155` (`SettingsModalDialog`), opened from `feature/shell/.../NijiyomeApp.kt:511`. Shortcut in `feature/shell/.../ShellShortcuts.kt:45` (`Key.P`, `Key.Comma`). **Platform:** desktop (shortcut), the composable is common.
- **Also opened from:** the title-bar gear (`desktopApp/.../DesktopTitleBar.kt`), the tray "Settings" item, the palette action "Abrir Configuración" and the Link Monitor's "Link Settings" (opens with section LINK).
- **Layout:** a centered dialog at 92 % width (max 1020 dp) and 88 % height (max 760 dp), with the title "Settings", a "Ctrl+P" pill and a close × (content description "Cerrar configuración"). The body is the full SET-00 shell. `Esc` closes. Navigating to Extensions, Linked device or Debrid closes the modal first.

## SET-03 Command palette entries that reach Settings (Ctrl+K)
- **Source:** `feature/shell/src/commonMain/kotlin/app/nijiyome/nav/CommandPalette.kt:117-215`. **Platform:** desktop (Ctrl+K). The shell agent owns the palette itself.
- **Settings-related entries:**
  - Actions: "Abrir Configuración" (badge Ctrl+P); "Activar/Desactivar Negro puro AMOLED" (instant toggle); "Cambiar tema (Sistema / Claro / Oscuro)" (cycles DARK→LIGHT→SYSTEM); "Alternar barra lateral compacta" (Ctrl+B, wide only); "Abrir registros de error (PC + Móvil)" (opens About).
  - Navigation: "Ir a Descargas", "Ir a Torrents", "Ir a Extensiones", and others.
  - **Every `SettingsCatalog` item** as a SETTING entry (Tune icon, section badge), which opens the modal at that section.
- **UX notes:** All palette text is hardcoded Spanish.

## SET-04 Search highlight banner
- **Source:** `common/SettingsScreen.kt:670`. **Platform:** both.
- A primaryContainer strip at the top of the section showing the search icon, the matched setting's title and a dismiss × ("Ocultar aviso").
- **UX note:** This is the only "where is it" cue. The redesign should scroll to and flash the target row.

## SET-APP Appearance section
**Source:** `common/SettingsSections.kt:122` (`AppearanceSection`) + `common/AppearanceLook.kt:81` (`LookGroup`). **Platform:** both (exceptions noted per row).

### SET-APP-01 Live preview card ("PREVIA")
- `SettingsSections.kt:193`. A mock-up with a mini rail, a "Continuar · Ep 4" primary pill, a "1080p" chip, three cards and a mode pill ("OLED #000"/Claro/Oscuro/Sistema). It reacts to palette, AMOLED and contrast.
- **UX:** It does not reflect the Look options (card style, density) and its text is Spanish only.

### SET-APP-02 … SET-APP-07 Theme rows

| ID | Setting | Control | Options | Default | Notes |
|---|---|---|---|---|---|
| SET-APP-02 | Palette | Horizontal swatch row (48 dp circles, check when selected, label under), fading edges | 20 palettes from `NijiyomeTokens.paletteInfo` (see Design system) | `nijiyome-violet` | Selection hidden while dynamic color is on (Android 12+) |
| SET-APP-03 | Dynamic color | Switch | on/off | **on** | **Android 12+ only** (`dynamicColorSupported`). Hint "Uses the wallpaper colors (Android 12+)." |
| SET-APP-04 | Mode | Segmented | System / Light / Dark | System | |
| SET-APP-05 | AMOLED black | Switch | on/off | off | Disabled in Light mode (hint "Disponible solo en modo Oscuro o Sistema", Spanish literal) |
| SET-APP-06 | Contrast | Segmented | Standard / Medium / High | Standard | Disabled while dynamic color is on (Spanish literal hint) |
| SET-APP-07 | Language | Segmented | System / English / Spanish | System | Same control in onboarding |

### SET-APP-08 … SET-APP-21 "Layout and style" (Look) group (`AppearanceLook.kt`)
"A preset sets every option at once. You can change each one afterwards." Status line: "Preset: X" or "Custom: your options no longer match a preset".

| ID | Setting | Control | Options | Default |
|---|---|---|---|---|
| SET-APP-08 | Preset | Horizontal row of radio cards (title + 3-line hint) | Nijiyome ("Spotlight banner, detailed cards and lively animations") / Editorial cinema ("Full-bleed art with a large title, large cards and an icons-only rail") / Dense ("Rows first, compact cards and lists, subtle motion") / Calm ("Carousel without autoplay, classic cards, subtle motion") | Nijiyome |
| SET-APP-09 | Home ("What Home shows at the top.") | Segmented | Spotlight / Carousel / Rows only | Spotlight |
| SET-APP-10 | Page style ("How the title page and the Home banner are drawn.") | Segmented | Standard / Editorial | Standard |
| SET-APP-11 | Title page header | **2×2 visual cards with drawn schematic thumbnails** | One line / Fact sheet / Next episode / Facts and episode (each with a hint) | Facts and episode |
| SET-APP-12 | Cards | Segmented | Classic / Detailed / Poster only | Detailed |
| SET-APP-13 | Card size ("Large and Extra large show the poster beside the title…") | Segmented small | Small / Medium / Large / Extra large | Medium |
| SET-APP-14 | Density | Segmented | Comfortable / Compact | Comfortable |
| SET-APP-15 | Animations ("Subtle changes only color and opacity. Off is the same as Reduce motion.") | Segmented | Expressive / Subtle / Off | Expressive |
| SET-APP-16 | Episodes | Segmented | Cards / List / Compact | List |
| SET-APP-17 | Related titles | Segmented | Graph / List | Graph |
| SET-APP-18 | Calendar | Segmented small | Week / Agenda / Month / Today | Week |
| SET-APP-19 | Side menu ("Tablets and wide windows only") | Segmented | With names / Icons only | With names. **Shown only in the expanded (wide) layout** |
| SET-APP-20 | Rotate the banner | Switch | on/off | on. Shown only when Home ≠ Rows only |
| SET-APP-20b | Seconds per title | Slider 4–30 s, step 1, saved on release ("N seconds") | | 9. Shown only when autoplay is on |
| SET-APP-21 | Show the greeting | Switch | on/off | off |

- Hidden model field: `look.glass` (blur) has no UI ("too heavy for phones"). `reduceMotion` is unified into Animations=Off.
- **UX notes:** 11 consecutive segmented rows with no sub-grouping (Home / Title page / Lists / Navigation). Only the header has visual thumbnails; Cards, Card size, Density, Episodes, Relations and Calendar are plain-text choices that need previews. The preset row scrolls horizontally, so presets can sit off screen. Ctrl+B toggles the rail independently of SET-APP-19.

## SET-PLY Player section
**Source:** `common/SettingsSections.kt:306` (`PlayerSection`). **Platform:** both, with heavy platform branching. The rows owned by `feature/player`, `feature/extensions` and the subtitle sheet are inventoried in Part 4 (SET-PLY-EXTRA, SET-PLY-SUBS, SET-PLY-SRC).

| ID | Setting | Control | Options | Default | Platform / condition |
|---|---|---|---|---|---|
| SET-PLY-01 | Engine | Segmented (fill width) + per-engine hint | Android: Media3 / libmpv / External. Desktop: libmpv / External | libmpv | Media3 stored on desktop is shown as libmpv |
| SET-PLY-02 | External player | Segmented small | Ask every time / mpv-android / VLC (adds "· not installed" when missing) | Ask every time (null) | **Android**, only when Engine = External. Hint adds "No external player is installed: playback will offer Media3 instead." |
| SET-PLY-03 | mpv (path + status) | Text field "mpv executable" (placeholder "Empty: the first mpv on PATH."), saved on Done or on focus loss | free path | empty | **Desktop**, Engine = External. Status line: "Looking for mpv…" / "Found: {version · path}" / "mpv is not installed…" |
| SET-PLY-04 | Enhanced mode (mpv-anime-build) | Switch, disabled until downloaded | on/off | off | **Desktop**, External. Shows "Download (X MB)" button → progress "Downloading… N %" → failure text. Hint about the pinned SHA |
| SET-PLY-05 | The build's interface (uosc) | Switch | on/off | — | **Desktop**, only when enhanced is on and downloaded |
| SET-PLY-06 | Default upscaling (preset) | **Exposed dropdown**, each item shows label + hint | Off, Anime4K A, A+A, B, C, B+B, C+A, FSR, Fidelity (FSRCNNX), Sharpen (CAS); **desktop adds** Upscale (NVScaler). ArtCNN excluded | Anime4K A+A | Hint adds "Only the libmpv engine applies it." when the engine is not mpv. Battery hint differs per platform |
| SET-PLY-07 | Upscaling quality | Segmented | Fast / Balanced / High quality | Balanced | libmpv only |
| SET-PLY-08 | Deband | Switch | on/off | on | libmpv only |
| SET-PLY-09 | Automatic adjustments | Switch | on/off | on | libmpv only; hint differs per platform |
| SET-PLY-10 | Decoder | Segmented small | Auto / HW / HW+ / SW | Auto | libmpv only |
| SET-PLY-11 | Decoder status line | Read-only text | "Hardware decoding: VAAPI with the system libva X (GPU): H.264, HEVC…" / "No VAAPI (why)…" / "Last video: …" | — | **Desktop only** (`DecoderStatus.desktop.kt:33`) |
| SET-PLY-12 | Smoothing | Switch ("mpv interpolation with display-resample. Not real frame generation.") | on/off | off | libmpv only |
| SET-PLY-13 | Verify certificates (libmpv) | Switch | on/off | off | **Android only**, Engine = libmpv |
| SET-PLY-14 | Estilo del reproductor (player chrome) | Segmented | Cine / Minimalista | Cine | **Android only**. Label and hint hardcoded Spanish |
| SET-PLY-15 | Source selection | Segmented | Automatic / Manual | Automatic | |
| SET-PLY-16 | Preferred type | Segmented small | Any / Webstream / Torrent | Any | |
| SET-PLY-17 | Minimum quality | Segmented small | 480p / 720p / 1080p | 720p | |
| SET-PLY-18 | Preferred subtitles | Segmented small | ES / EN / PT | ES | |
| SET-PLY-19 | Preferred audio | Segmented small | JA / ES / EN | JA | |
| SET-PLY-19a | Auto-play next episode ("10 s countdown at the end.") | Switch | on/off | on | |
| SET-PLY-19b | Skip intro/OP ("AniSkip markers when available.") | Switch | on/off | on | |
| — | Player extras (translation, secondary sub, countdown, stable volume, background playback, shortcuts) | see SET-PLY-EXTRA (Part 4) | | | |
| SET-PLY-19c | Browser for extensions (hidden browser) | Status text + path field "Browser executable" + **Test** button → "Testing…" / "Works: …" / "Does not work: …" | — | auto-detect | **Desktop** (`HiddenBrowserSettings.desktop.kt:57`). Validation: missing / not executable / Flatpak-Snap. The embed rows below show **only when a browser is found** |
| — | Embed sources rows | see SET-PLY-SRC (Part 4) | | | Android always; desktop only with a browser |
| SET-PLY-19d | Subtitle style | Summary "{font} · {scale}% · background {n}%" + "Edit" tonal button → SET-PLY-SUBS sheet | | Outfit | |
| SET-PLY-19e | Extensions | NavRow → Extensions screen (EXT-01) | | | |

### SET-PLY-FG Frame generation (inside Player)
- **Android** (`android/FrameGenSettingsSection.kt:59`), only when `FrameGenController` is bound:
  - Title "Frame generation on the phone", hint (MPV only, battery and latency).
  - Status line: "On the phone · N×" / "Available" / "Available · Experimental on this GPU" / "Checking this device…" / reasons (needs Vulkan 1.3, PiP pause, battery saver, 8K, HDR passthrough, GPU reset, "The PC already does it (Nijiyome Link)", import shaders…).
  - **LSFG shaders (Lossless.dll):** "Import Lossless.dll" / "Import again" (SAF picker `*/*`) and "Remove". State: Importing… / Imported · date / Not imported. Snackbars for every install result.
  - **Use when playing:** switch ("On the phone"), enabled only if available or suspended.
  - **Multiplier:** segmented, Auto / 2× / 3× / 4× / 5×.
  - **Profile:** segmented, Saver / Balanced / Quality.
  - Switches: **Change the screen refresh rate**, **Allow on untested GPUs** (Vulkan 1.1/1.2, experimental), **Show frame generation stats**.
- **Desktop** (`desktop/ExternalPlayerSettings.desktop.kt:187`):
  - **Frame generation (lsfg-vk)** switch, disabled with its reason: no Vulkan layer / no DLL / not Linux / "It also needs mpv installed."
  - When on: **Multiplier** segmented (Auto / ×2 / ×3 / ×4) and a **Performance mode** switch.
  - **Shader file** path field, shown when the layer exists.
- **UX notes:** The Player section is the longest page (about 30 rows on Android). Engine-specific rows appear and vanish with no grouping header. The preset dropdown is the right pattern but has no visual before/after preview. ES/EN/PT and JA/ES/EN are fixed 3-language lists shown as raw ISO codes. Frame-gen status reasons are a wall of text.

## SET-MET Metadata section
**Source:** `common/SettingsSections.kt:540`. **Platform:** both.
- **SET-MET-01 Provider priority:** an ordered list (index number in mono, name, sub-line), with Up/Down icon buttons per row. Default order: AniList ("GraphQL · 90 req/min"), ani.zip ("Episodes and mappings"), Kitsu ("JSON:API · no published limit"), Tenrai. MAL and Shikimori are known ids. Hint: "The first source that answers wins; the others are fallbacks."
- **SET-MET-02 Metadata cache:** a size line ("X · N entries" / Measuring… / Unknown). **Desktop only:** a "Clear" button (`metadataCacheClearOffered`).
- **SET-MET-03 Keep details for:** segmented small, 6 h / 24 h / 3 d / 7 d. Default 24 h.
- **SET-MET-DISC:** title language, 18+ content, clear recent searches. See Part 4.
- **SET-MET-04 Recordatorios de emisión** (airing reminders): switch, default on. **Spanish literal** label and hint.
- **SET-MET-05 Notificaciones de AniList** (social notifications): switch, default on. **Spanish literal.**
- **UX notes:** Reordering uses arrow buttons only, with no drag handle and no on/off per provider. Notification toggles sit under "Metadata", which is the wrong home (they should live in a Notifications group).

## SET-ACC Accounts (Lists and accounts) section
**Source:** `common/AccountsSection.kt:86`, `common/AccountsViewModel.kt`. **Platform:** both, shown only when `ListsRepository` is bound. Hint texts differ per platform ("this phone" vs "this PC").
- **SET-ACC-01 Provider cards** (AniList, MyAnimeList). Each card shows:
  - Avatar (or initial), name, and a status line: "Signed in as X" / "Not linked" / "This build has no client id…".
  - Action: **Link** (disabled without a client id) → spinner while linking → **Unlink**.
  - When linked: a "First sync pending" warn pill and a **Choose** / **Import / export** button that opens SET-ACC-02.
  - A "Session expired · link again" pill, and an inline error with an icon.
  - OAuth opens a Custom Tab (Android) or the system browser with a loopback listener (desktop). Desktop error when the port is busy: "Port N is in use…". After returning from the tab without a redirect, the spinner stops after 1.5 s.
- **SET-ACC-02 Sync mode dialog** "How to sync with X": option cards **Merge (recommended)**, **Import from X**, **Export to X**, each with a hint. The dismiss button reads "Later" on the first sync, "Cancel" otherwise.
- **SET-ACC-03 List sync row:** status text ("Local lists only…" / "Last sync …" / "Not synced yet"), a pill "N pending" (warn) or "Up to date" (ok), and a **Sync lists now** button (spinner, "Syncing…").
- **SET-ACC-04 Conflicts block:** "N conflicts" with **Keep all mine**. Per conflict (max 20 shown, then "And N more"): local vs remote summary (status · ep x / y · ★ score) with **Keep mine** / **Use the service one**.
- **SET-ACC-05 Switches:**
  - Update progress automatically (default on)
  - Add to Watching when I start (default off)
  - Send changes to accounts (write-back, default on)
- **SET-ACC-06 Import and export:** **Export list** (JSON), **Export CSV** (**desktop only**), **Import list** (file picker: JSON on Android; JSON, MAL XML, AniList dump or Tatakai file on desktop; 8 MiB cap). The file is named `nijiyome-lists-YYYY-MM-DD.json/csv`. Snackbars "List exported" / "Could not write the file" / "N imported".
- **SET-ACC-07 Unlink dialog** "Unlink X?" with a body explaining that pending changes are discarded; destructive **Unlink**.
- Footnote: "The watch history is not part of the account…"
- **Error texts:** cancelled, denied, exchange, expired, file, http, not linked, offline, rate, state, timeout (`AccountsErrors.kt`).
- **UX notes:** The OAuth flow, sync status, conflict resolution, behavior switches and file transfer all sit in one long panel. The conflict list caps at 20 with no paging.

## SET-LNK Nijiyome Link section, client (phone)
**Source:** `common/SettingsSections.kt:636` (`LinkSection`), chosen by `LinkServerSection.kt:77` when no `LinkServerController` is bound. **Platform:** Android in practice; the desktop binds the server and shows SET-LNKSRV instead.
- Hint: "Connect this app to your PC so it enhances video (Anime4K)…"
- **SET-LNK-01 Linked device** tonal button → LNK-01.
- **SET-LNK-02 Process by default on:** segmented, Phone / PC / Mixed. Default Phone. **Disabled when not paired** (hint "Link a PC to process video on it…").
- **SET-LNK-03 History between your devices:** switch "Sync history with the PC" (default on). Status line: "No PC linked: history stays on this phone." / "History synced {relative}" / "Not synced yet".
- **SET-LNK-04 Sync now** button (spinner "Syncing…"). Enabled only when paired, sync is on and nothing is running.

## SET-LNKSRV Nijiyome Link section, server (desktop "this PC")
**Source:** `common/LinkServerSection.kt:101` (`LinkServerSection`). **Platform:** desktop (`LinkServerController` bound in `desktopApp/.../Main.kt`).

| ID | Control | Details |
|---|---|---|
| SET-LNKSRV-01 | **Nijiyome Link on this PC** switch | Private network only, HTTPS, paired phones. Status line: Starting… / "Listening on {addresses}" / Off / errors (port in use, no private address, identity unreadable, other) |
| SET-LNKSRV-02 | "Try port N" text button | Only on a port-in-use error; picks an alternative that the extension server does not use |
| SET-LNKSRV-03 | Regenerate identity (inline) | Only on the identity error; opens SET-LNKSRV-15 |
| SET-LNKSRV-04 | Fingerprint display | "Fingerprint XXXX…" (mono) + "Your phone shows the same fingerprint before pairing." |
| SET-LNKSRV-05 | FFmpeg missing warning | Red text when running without FFmpeg |
| SET-LNKSRV-06 | **Active sessions (N)** cards | Device · title · Ep; pipeline (upscale · frame gen · encoder); "Caché RAM: X MB / Y MB · Adelanto: +N s" (mono); **Detener** button per session. Spanish literals |
| SET-LNKSRV-07 | **Pair a phone** | Button → QR (200 dp, white quiet zone) + code + "Valid for N more seconds, single use." countdown + **Copy pairing text** + Cancel |
| SET-LNKSRV-08 | **Paired devices** list | Per device: name, "Connected now" / "Last seen …" / "Not seen since pairing", history contributed count; **Rename** (dialog with text field + Save); **Revoke** (confirm dialog); **History** segmented Full / Receive only / Paused; **Use as the source** (Full only, "Sending its whole history…" while resyncing). Empty: "No phone paired yet." |
| SET-LNKSRV-09 | This PC capability line | "{GPU} · {encoder} · N enhancement profiles" |
| SET-LNKSRV-10 | **Name phones see** | Text field + Save (blank = default host name) |
| SET-LNKSRV-11 | **Announce on the network** (mDNS) | Switch |
| SET-LNKSRV-12 | **Library folder** | Text field + Save ("Phones can play the videos of this folder (not its subfolders). Blank: none.") **Plain text, no folder picker** |
| SET-LNKSRV-13 | **Port** | Numeric field (≤5 digits, 1024–65535, ≠ ext port) + Save. Default **47321**; saving restarts the server |
| SET-LNKSRV-14 | **Caché de reproducción en RAM** | Segmented small: 512 MB / 1 GB / 2 GB / 4 GB. Default 1 GB. Spanish literal label and hint. Also in the Link Monitor (LNK-18) |
| SET-LNKSRV-15 | **Firewall** | Windows: **Allow on private network** button (UAC) → Waiting… / added / cancelled / failed. Linux and others: hint with the ports to open (ufw, firewalld) |
| SET-LNKSRV-16 | **Regenerate identity** | Outlined button → confirm dialog "Regenerate the Link identity?" (every paired phone is forgotten, backup kept) |
| SET-LNKSRV-17 | **Extension server on this PC** switch | Plain HTTP on the LAN; each phone needs its own code. Error: port in use (with "Try port N") / could not start |
| SET-LNKSRV-18 | Extension server **Port** | Default **8099**, only when enabled |
| SET-LNKSRV-19 | **New code for a phone** (extension pairing) | QR + selectable `nijiyome://` link + **Copy link** + Cancel; shown only while the server runs |
| SET-LNKSRV-20 | **Viewer password** | Status ("A viewer password is set." / "No viewer password…"); password field (10–1024 chars) + Save; **Remove password**; result text (saved / removed / failed) |
| SET-LNKSRV-21 | Extension **Linked devices** | Name + **Revoke** (no confirmation) |
| SET-LNKSRV-22 | **Requests and errors (N)** log | Show/Hide toggle; mono list "time · event · route · name · count · ms · error", newest first, max 320 dp; empty text |

- **UX notes:** Two servers (Link and extension server) with two pairing flows, two ports and two device lists are stacked in one panel. Ports are raw numeric fields. Library folder is a text field instead of a folder picker. Revoking extension devices has no confirmation, while revoking Link devices does. Mixed languages.

## SET-NET Network section (desktop)
**Source:** `common/NetworkSettingsSection.kt:41`. **Platform:** desktop (`NetworkSettingsController` bound in `desktopApp`). Hint: "Outbound proxy, FlareSolverr and the player's local media proxy. Changes apply to the next connection."
- **SET-NET-01 Outbound proxy:** switch (local draft until saved).
- **SET-NET-02 Type:** segmented SOCKS5 / HTTP.
- **SET-NET-03 Address:** Host field (placeholder 127.0.0.1, max 253 chars) + Port field (digits, 1–65535, error state).
- **SET-NET-04 Credentials (optional):** User field + Password field ("Password (saved; type to replace)" when one is stored). Kept in the system keyring.
- **SET-NET-05 Test** ("Testing…" → "Works (N ms) · detail" / "Does not work: reason") and **Save** (enabled when the draft differs and is valid).
- **SET-NET-06 Scope note:** torrents use the proxy only with SOCKS5; with HTTP, torrents go direct.
- **SET-NET-07 FlareSolverr:** URL field (placeholder `http://127.0.0.1:8191`) + Test + Save. Empty = off.
- **SET-NET-08 Local media proxy:** switch.
- **UX notes:** Explicit Save for the proxy while every other setting auto-saves, which is inconsistent and gives no dirty-state indicator. The Save label reuses the Link string resource.

## SET-TOR Torrents section (entry)
- **Source:** `common/SettingsSections.kt:705`. **Platform:** both.
- Hint plus two NavRows:
  - **Torrent settings** ("Speed · Network · Storage · Background") → `TorrentSettingsPage` (TOR-25/26). It opens inline in the right pane on wide windows and full screen on phones.
  - **Debrid (TorBox and others)** → `Route.Torrents(DEBRID)` (TOR-21).
- **UX:** Settings keeps only links here; the real controls live in Part 2.

## SET-DL Downloads section
- `DownloadsSettingsSection` from `feature/downloads`. Inventoried as **DL-14 / DL-15** in Part 2.

## SET-RDR Reader section
- `ReaderSettingsSection` (manga) + `NovelReaderSettingsSection` (novel + TTS). Inventoried in Part 4 (SET-RDR-MANGA, SET-RDR-NOVEL, SET-RDR-TTS).

## SET-DAT Data section
**Source:** `common/SettingsSections.kt:724` + `common/ResetAppRow.kt:59` + `desktop/DesktopSystemSettings.kt:113`. **Platform:** both (desktop rows noted).
- **SET-DAT-01 Location hint:** Android "Torrents are stored…"; desktop "Everything stays on this computer ({dir})."
- **SET-DAT-02 Data folder › Open folder** (**desktop**): "The database, settings, logs and sealed account tokens."
- **SET-DAT-03 Storage bar + legend:**
  - A 12 dp stacked bar: cache (primary), torrents (tertiary), others (secondary, desktop), free.
  - Legend in mono: Metadata cache, Images on disk, Torrents, [desktop: Web downloads, Logs, Extensions], Free. Plus "Completed torrents move to the folder you chose." or the app-folder variant.
  - States: skeleton after 150 ms / error "Could not measure storage" + Retry / Ok.
- **SET-DAT-04 Telemetry:** "None" OK pill ("This build sends nothing. There is no switch…").
- **SET-DAT-05 Desktop system rows** (`DesktopSystemPrefs` defaults in brackets):
  - **Tray icon** [on]. Hint becomes "No system tray answered (GNOME needs AppIndicator)" when there is none.
  - **Close to the tray** [off], needs tray.
  - **Start with the system** [off], packaged builds only.
  - **Start minimized in the tray** [off], needs autostart + tray.
  - **Discord Rich Presence** [off], disabled without an app id.
  - **Barra de título nativa del sistema** [off], Spanish literal, applies on restart.
  - **Open magnet links and .torrent files with Nijiyome**: reads the real OS state, disabled while reading; packaged builds only.
  - Keyring note when secrets fall back to a file.
  - **Secrets unreadable** error notice with **OK** dismiss ("link your accounts again", backup file name).
- **SET-DAT-06 Clear cache:** outlined button (spinner) → dialog "Deletes X of cached metadata and images. Your history and torrents are kept." with destructive Clear cache.
- **SET-DAT-07 Watch history:**
  - **Export history** (JSON `nijiyome-history-YYYY-MM-DD.json`).
  - **Clear history** → dialog "Clear the watch history?" with a checkbox **Also on the paired PC** (only when paired) and destructive confirm.
- **SET-DAT-08 Reset app** (only when the reset service is bound): destructive outlined button ("Resetting…").
  - Android: a simple confirm dialog, "Reset everything".
  - **Desktop:** a typed dialog. Type **RESET** (case-insensitive) plus a checkbox **Keep downloaded torrents** (default on; off warns that files are removed).
  - Afterwards the app restarts (Android recreates the Activity; desktop relaunches) into onboarding.
- **UX notes:** Desktop system preferences (tray, autostart, Discord, title bar, magnet handler) are buried under "Data", which is the wrong grouping; they need a "System" section. Destructive actions are mixed in with read-only storage info.

## SET-ABT About section
**Source:** `common/SettingsSections.kt:842`, `common/AboutExtras.kt`. **Platform:** both.
- **SET-ABT-01 Facts** (key/value, value in mono): Nijiyome version · package; component versions.
  - Android: Media3 1.11.1, libmpv 1.1.0, libtorrent4j, Nijiyome Link v1.
  - Desktop: Compose MP, MediaMP, libtorrent4j, QuickJS, OkHttp, Java runtime, Link.
  - License: AGPL-3.0-or-later. About body text differs per platform.
- **SET-ABT-02 Update check** (when bound): switch **Check for new versions** (default on, at most daily, GitHub releases) + **Check for updates** button → "Up to date" pill / "Version X available" link (opens the release URL) / "Could not check".
- **SET-ABT-03 Licenses** → `AdaptiveSheet` with THIRD_PARTY_NOTICES + OFL texts as mono paragraphs; loading / error+retry / empty ("No license files are bundled in this build.").
- **SET-ABT-04 Open logs** → **Logs sheet** (`AboutExtras.kt:116`):
  - Header actions: refresh, open folder (desktop), **Clear**, **Share** (Android share sheet / desktop reveals the file).
  - **Source chips** when linked devices shipped logs (desktop): "Esta PC" / each device / "Todos".
  - **Enviar a la PC** (push logs, Android when paired) → snackbar "Se enviaron N líneas a la PC" or "ya estaban al día".
  - **Severity chips:** Todos / WARN+ / ERROR.
  - **Filter field:** "Filtrar por etiqueta, error o mensaje…".
  - Mono lines colored by level (error / tertiary / onSurface), auto-scrolled to the end; empty "No log lines yet".
- **UX notes:** The logs UI mixes Spanish literals with resources. Share means different things per platform. Severity detection is a substring match (" E/", " W/").

## SET-ONB First-run wizard (onboarding)
**Source:** `common/OnboardingScreen.kt:146`, `desktop/OnboardingDesktop.kt`, `android/SettingsPlatform.android.kt:256`. **Platform:** both.
- **How to get there:** shown by the shell while `onboardingDone = false` (`feature/shell/.../NijiyomeApp.kt:240`), and again after Reset app.
- **Steps:** WELCOME, APPEARANCE, PLAYBACK, DOWNLOADS (desktop), LISTS (if lists are available), LINK (if Link is available), PERMISSIONS (Android 13+), SYSTEM (desktop), DONE.
- **Chrome:** a linear progress bar and **Skip** (marks done). Footer: **Back** / **Start** / **Next** / **Finish**.
- **Per step:**
  - Welcome: text only.
  - Appearance: Language segmented + Mode segmented.
  - Playback: Engine + hint, Source selection, Preferred type, Minimum quality, Subtitles and Audio language segmenteds (same as Settings).
  - Lists: provider cards (link/unlink without confirmation) + sync-mode dialog.
  - Link: text only ("pair from You › Linked device").
  - Permissions (Android): Notifications card with **Allow** (POST_NOTIFICATIONS) → Allowed / denied text.
  - Downloads (desktop): current folder or "{dir} (default)", **Choose folder…**, **Use the default**, and segmented "After watching an episode, its file" Keep / Delete.
  - System (desktop): all SET-DAT-05 rows + Check for new versions.
  - Done: "Install an extension from Extensions to start watching."
- **UX notes:** The wizard reuses settings controls verbatim and offers no palette choice (only Mode). The Link step is text with no action.

---

# Part 2. Torrents (`feature/torrents`) and Downloads (`feature/downloads`)

Paths are relative to the repo root. `T/` = `feature/torrents/src/commonMain/kotlin/app/nijiyome/feature/torrents/`, `D/` = `feature/downloads/src/commonMain/kotlin/app/nijiyome/feature/downloads/`. Strings come from the English `composeResources/values/strings.xml` of each module and of `core/ui`.

Platform switches: `desktopTorrentUi` (torrents, `TorrentPlatform.desktop.kt:44` = true, `.android.kt:59` = false) and `desktopDownloadsUi` (downloads, `DownloadFolder.desktop.kt:20`). `rememberOpenFolder()` is non-null on desktop only, so every "Open folder" action is desktop-only.

### Entry points (navigation)
- Route `Route.Torrents(section = LIBRARY, torrentId = null)` (`feature/shell/.../nav/Routes.kt:41`) goes to `TorrentsScreen` (`feature/shell/.../nav/NijiyomeApp.kt:993`). Section changes call `replaceTop`, so they do not grow the back stack.
  - Wide windows: a rail destination ("Torrents"). The rail icon shows a badge with the number of active downloads (99+ cap) (`NijiyomeApp.kt:556`).
  - Compact windows: Torrents is owned by Library (`Routes.kt:150`). The Library nav item carries the active-downloads badge (`NijiyomeApp.kt:420`).
  - Other ways in: You page "Torrents" (`NijiyomeApp.kt:850`); the command palette "Ir a Torrents" (`CommandPalette.kt:194`); the "Configure debrid" links in Settings, Library and the empty Torrents tab, which open `Torrents(DEBRID)` (`NijiyomeApp.kt:518/763/776/860/870`); the player chip "Open in the client" and Details after "Download", which open `Torrents(OVERVIEW, id)`; the Downloads page torrent row, which opens `Torrents(FILES, id)` (`NijiyomeApp.kt:652`); torrent notification taps (`NijiyomeApp.kt:314`); the desktop tray and Link Monitor "Open torrents" (`desktopApp/.../Main.kt:277,410`); and system `magnet:` / `.torrent` intents (PendingMagnetLink), which open the Add torrent sheet.
- Phone Library › Torrents tab: `TorrentsLibraryTab` (`feature/library/.../LibraryScreen.kt:185`).
- Settings › Torrents: `TorrentSettingsPage` (`feature/settings/.../SettingsScreen.kt:321` full page on compact, `:407` pane on desktop/expanded).
- Settings › Downloads: `DownloadsSettingsSection` (`SettingsScreen.kt:708`).
- Details: `DownloadSeasonSlot(media)` (`feature/details/.../DetailsSections.kt:237`) and `rememberDownloadGate()` (`DetailsScreen.kt:236`).
- Route `Route.Downloads(tab = ACTIVE)` goes to `DownloadsScreen` (`NijiyomeApp.kt:906`). Wide windows: a rail destination with no back arrow. Compact windows: owned by You.
  - Other ways in: You "Downloads"; the command palette "Ir a Descargas"; download notifications (`WaveBLinks.kt:20`, tab by id); the chapter-downloaded notification (`WaveBLinks.kt:23`, MANGA/NOVELS tab).
- Orphan: `TorrentDownloadsScreen` (`T/TorrentsScreens.kt:367`) is public but has no caller outside the module. `TorrentDetailScreen` is used only internally by `TorrentsScreen`.

---

## TOR-01 Torrents client shell (top bar + section switcher + adaptive panes)
- **Source:** `T/TorrentsScreens.kt:89-225`. **Platform:** both (adaptive).
- **How to get there / leads to:** `Route.Torrents` (see Entry points). It leads to Library (TOR-02), Downloads (TOR-16), History (TOR-19), Debrid (TOR-21), Settings (TOR-25), the per-torrent detail (TOR-08) and Add torrent (TOR-23).
- **Controls & actions:**
  - Top app bar with the title "Torrents". A back arrow ("Back") shows only on single-pane layouts.
  - Top-bar action "+" ("Add torrent"): resets the Add view model and opens TOR-23.
  - Segmented header with 5 options: "Library", "Downloads", "History", "Debrid", "Settings". Selecting Library keeps the current torrent id; the other options clear it.
  - Automatic: an incoming system magnet or `.torrent` opens TOR-23 pre-filled (`incoming` flow, `:105`).
  - After a successful add, it navigates to OVERVIEW of the new torrent.
  - Phone with a torrent id (per-torrent section or LIBRARY): the whole screen becomes the TOR-08 detail. Its back arrow returns to the previous route.
  - Two panes (expanded window) with a torrent id: a `ListDetailPaneScaffold` with the library list on the left and the detail on the right. The selected card is highlighted. The detail's "back" goes to LIBRARY with no torrent.
- **Data shown:** the section content for the selected segment.
- **States:** delegated to each section.
- **UX notes for redesign:** five sections sit in one segmented control, mixing lists (Library, History), a mode screen (Downloads), account setup (Debrid) and preferences (Settings). Settings and Debrid are reachable both here and from app Settings. The Library item is labelled "Library", which collides with the app's own Library destination.

## TOR-02 Torrent library list (cards)
- **Source:** `T/ui/LibraryContent.kt:87-214` (list), `:218-300` (card). Used by `LibraryPane` (`T/TorrentsScreens.kt:229`) and `TorrentsLibraryTab` (`:322`). **Platform:** both. Android always uses cards in engine order (newest first).
- **How to get there / leads to:** Torrents › Library, or the phone Library › Torrents tab. A tap opens TOR-08 (OVERVIEW). "Play" opens the player.
- **Controls & actions:**
  - Card tap: opens the torrent. In selection mode, a tap toggles the card's selection.
  - Card long-press: toggles selection (a11y long-click label "Select {title}"). This enters multi-select (TOR-05).
  - Checkbox on each card in selection mode (replaces the overflow menu).
  - "⋮" "Torrent actions" overflow menu (TOR-04).
  - Empty-state action "Configure debrid": only in the phone Library tab, opens `Torrents(DEBRID)`.
  - Retry on error ("Retry").
- **Data shown (per card):**
  - Poster (52 dp) or a Movie-icon placeholder.
  - Title: series, or the torrent name if there is no series (max 2 lines).
  - Detail line: "Ep. N" for a single file, "Batch · N files" for several, otherwise the name; then "· size" (monospace).
  - State pill: Starting / Downloading / Seeding / Paused / Error. Tones: OK for Downloading and Seeding, NEUTRAL for Paused, ERR for Error, PRIMARY for Starting.
  - "Downloaded 42%", plus the relative added time ("5 minutes ago").
  - Thin wavy progress bar.
  - "Kept after watching" or "Will be deleted after watching".
  - Header count "N torrents" (Android). On desktop the count sits in the toolbar (TOR-03).
- **States:**
  - Loading: spinner after 150 ms.
  - Error: "Could not load the torrent library" with a mapped message and "Retry".
  - Empty: icon, "No torrents yet", "Pick a torrent source in an episode to start. Torrents you play or download show up here." (+ the optional debrid action).
  - Selected-in-pane highlight (secondaryContainer).
- **UX notes for redesign:** no search, no state filter chips, and no sort on Android. Poster size is inconsistent: 52 dp wide on a cover vs a 52×52 square placeholder. Persist status is plain text under the bar. The empty state does not offer "Add torrent".

## TOR-03 Library toolbar: sort, direction, cards/list view, list columns (desktop)
- **Source:** `T/ui/LibraryContent.kt:376-463` (toolbar, columns), `:466-534` (row), `T/vm/LibrarySort.kt`. **Platform:** desktop.
- **How to get there / leads to:** the top of the Library list on desktop.
- **Controls & actions:**
  - Text button "Sort by: {key}". It opens a dropdown with 6 options, the current one checkmarked: "Date added" (default), "Name", "Episode", "Size", "Progress", "State".
  - Direction icon button: ↓ "Descending" (default) or ↑ "Ascending".
  - Small segmented control "Cards" (default) | "List".
  - All three are `rememberSaveable` only, so they are not persisted across sessions.
  - Sort rules: Name is case-insensitive by series or name; Episode puts missing episodes last in both directions; State order is Downloading, Seeding, Starting, Paused, Error.
  - List-row clicks, long-press, checkbox and ⋮ menu behave as on the cards.
- **Data shown:**
  - List view column headers: Name (flex), Episode (72 dp), Size (88 dp), State (112 dp), Progress (56 dp), Date added (120 dp). State and Date added are hidden below a 640 dp list width.
  - Row: title plus the raw name on a second line when it differs from the series; "Ep. N", "Batch · N files" or "—"; size; state pill; %; relative date or "—".
- **States:** same as TOR-02.
- **UX notes for redesign:** the column headers are not clickable for sorting; sorting goes through a separate dropdown. Sort and view are not remembered. The Android version lacks all of this.

## TOR-04 Library item overflow menu (⋮)
- **Source:** `T/ui/LibraryContent.kt:304-372`. **Platform:** both; some items are platform-specific.
- **How to get there / leads to:** ⋮ on a library card or row.
- **Controls & actions (in order):**
  1. "Open in the torrent client": opens TOR-08.
  2. "Play": only when the item has an episode and a media id. Opens the player.
  3. "Resume" if paused, otherwise "Pause".
  4. "Open folder": desktop, when a path is set.
  5. "Keep after watching" or "Don't keep after watching": toggles persist.
  6. Android "Share magnet link" (share sheet). Desktop "Copy magnet link" (clipboard, then a toast with the same text).
  7. "Delete": opens TOR-06.
- **Data shown:** labels only.
- **States:** items show or hide by item state and platform.
- **UX notes for redesign:** 7 mixed actions sit in one flat menu with no grouping. Destructive Delete is not separated from the others. Pause and Play share the same PlayArrow icon, which is ambiguous.

## TOR-05 Library multi-select contextual bar
- **Source:** `T/ui/LibraryContent.kt:537-564`, `T/vm/TorrentViewModels.kt:96-118`. **Platform:** both.
- **How to get there / leads to:** long-press any card or row.
- **Controls & actions:**
  - ✕ "Clear selection".
  - "N selected" count label.
  - Icon buttons: "Select all" (all visible items), "Pause", "Resume", "Keep after watching" (sets persist = true; there is no bulk un-keep), "Delete selected" (opens TOR-06 with N).
  - Removing items also drops them from the selection.
- **Data shown:** the selection count.
- **States:** visible only while the selection is non-empty.
- **UX notes for redesign:** icon-only actions with no labels. There is no bulk "Don't keep". The selection does not auto-exit after Pause or Resume. On desktop there is no Ctrl/Shift-click range selection; selection is long-press only.

## TOR-06 Delete torrent(s) dialog
- **Source:** `T/ui/LibraryContent.kt:568-596`. Also used from TOR-08 (`T/ui/DetailContent.kt:216`). **Platform:** both.
- **How to get there / leads to:** ⋮ › Delete, the selection bar's Delete, or the detail menu's Remove.
- **Controls & actions:**
  - Title: "Delete this torrent?" or "Delete N torrents?".
  - Body: "It leaves the library and stops downloading and seeding."
  - Checkbox "Also delete the downloaded files", default **on**. When off, a hint shows: "Off: the video files stay in the download folder."
  - Buttons "Delete" and "Close".
  - Snackbar afterwards: "Torrent removed" or "Removed N from the library". From the detail, the screen navigates back.
- **States:** none.
- **UX notes for redesign:** the dismiss button says "Close" rather than "Cancel". The confirm button is not styled as destructive.

## TOR-07 Per-torrent section picker / no-torrent empty state
- **Source:** `T/TorrentsScreens.kt:229-282`. **Platform:** both.
- **How to get there / leads to:** a deep link to Overview, Files, Peers or Trackers without a torrent id.
- **Controls & actions:**
  - Header "Choose a torrent" with "Overview, Files, Peers and Trackers show one torrent at a time." above the library list.
  - Tapping a card opens that section for the chosen torrent.
  - If the library is empty: an EmptyState "No torrents yet", "Play a torrent source from an episode, or check the Library for saved torrents.", with the action "Open the library".
- **States:** loading, error and empty as in TOR-02.
- **UX notes for redesign:** none.

## TOR-08 Torrent detail: header, tabs, header overflow menu
- **Source:** `T/ui/DetailContent.kt:75-226`. **Platform:** both.
- **How to get there / leads to:** a library card, a deep link, the player, Details, or the Downloads page. Contains TOR-09 to TOR-15.
- **Controls & actions:**
  - Top bar "Torrents" with "Back" (hidden when embedded in two panes).
  - Fixed header: name (series or name), state pill, and a Pause/Resume icon button.
  - "⋮" header menu:
    1. "Copy info-hash" (snackbar "Info-hash copied").
    2. "Share magnet link" (Android) or "Copy magnet link" (desktop).
    3. "Open folder" (desktop, with a path).
    4. "Keep after watching" or "Don't keep after watching" (only when the torrent is in the library).
    5. "Remove": opens TOR-06.
  - Scrollable tab row "Overview", "Files", "Peers", "Trackers" over a swipeable HorizontalPager. The initial tab comes from the route section.
  - Auto-back when the torrent is removed.
- **Data shown:** title and state.
- **States:** the header shows no pill or controls until stats load. "This torrent is no longer in the session. It may have been removed after watching." (error code `torrent:missing`) comes through the tab error states.
- **UX notes for redesign:** the top-bar title is the generic "Torrents" while the real name sits in the body. Keep/un-keep is duplicated as a switch in Overview. The Pause/Resume icon is not labelled.

## TOR-09 Overview tab
- **Source:** `T/ui/DetailTabs.kt:98-220`. **Platform:** both.
- **How to get there / leads to:** TOR-08, tab 1.
- **Controls & actions:**
  - Copy-icon button next to the info-hash ("Copy info-hash"; snackbar "Info-hash copied").
  - Switch "Keep after watching". Hint: "Kept after watching" or "Will be deleted after watching".
  - Switch "Keep sharing after completion" (seed), default off. Hint, Android: "Off by default on Android: the torrent pauses when the download is complete." Desktop: "Off by default: the torrent pauses when the download is complete."
  - Both switches show only when the torrent is in the library.
  - Retry on error.
- **Data shown:**
  - Error banner (state ERROR) with the engine's message or "Could not read this torrent".
  - A 96 dp circular wavy progress ring with %. Next to it: "Download progress", "X of Y", and "Streaming", "Complete" or "{eta} left".
  - Section **Transfer** (2-column grid): Downloaded, Uploaded, Size ("X of Y": wanted of total), Pieces ("have / total × piece size"), Download speed, Upload speed, Remaining (duration or "Unknown"), Elapsed.
  - Section **Swarm**: Seeders, Leechers, Wires (connected peers), Ratio (2 decimals).
  - Section **Info-hash**: monospace hash.
  - Section **Protocol**: pills DHT, LSD, PEX, NAT, Forwarding, Persisting, Streaming, green when active (a11y "X: Active/Inactive").
  - Duration format: "12 s", "3 min 4 s", "1 h 5 min", "2 d 3 h", "—".
- **States:**
  - Loading after 150 ms.
  - Error: "Could not read this torrent" with "Retry".
  - ERROR torrent state: the error banner.
  - Streaming vs complete vs ETA text.
- **UX notes for redesign:** a dense label/value grid with no visual hierarchy. The term "Wires" is unclear. The Protocol pills are engine internals shown to end users. The seed and keep switches sit at the bottom, below the fold.

## TOR-10 Files tab
- **Source:** `T/ui/DetailTabs.kt:262-398`, `filterFiles` in `T/vm/TorrentViewModels.kt:148`. **Platform:** both. Polls every 2 s.
- **How to get there / leads to:** TOR-08, tab 2. Leads to TOR-11 (row tap) and TOR-12 ("Align episodes").
- **Controls & actions:**
  - Search field "Search files", placeholder "Name or folder". Case-insensitive substring over the path. Clear ✕ ("Clear search").
  - Text button "Align episodes": opens TOR-12.
  - File row tap: opens TOR-11.
  - No-match empty-state action "Clear search".
- **Data shown:**
  - Summary "N files · N selected · selected size".
  - Per row: name (2 lines); "Ep. N" pill; "size · %"; "Skipped" when deselected; a thin progress bar; a trailing Stream icon ("Being streamed now") when it is being streamed.
- **States:**
  - Loading.
  - Error: "Could not read the files".
  - Empty: "Waiting for metadata", "The file list appears once peers send the torrent's metadata."
  - No match: "No matching files", "Nothing matches "{q}".", with "Clear search".
- **UX notes for redesign:** flat list with no folder tree. Rows have no checkboxes, so file selection is only possible one file at a time via "Download only this one". There is no sort.

## TOR-11 File action sheet
- **Source:** `T/ui/DetailTabs.kt:328-369`. **Platform:** both.
- **How to get there / leads to:** tap a file row in TOR-10.
- **Controls & actions:**
  - Header: the file name (3 lines).
  - "Play this file": opens the player with the torrent id, file index and episode.
  - "Download only this one": only when the file is skipped. Calls `downloadFile(index)`.
  - "Save to Downloads": only when the file is 100%. Exports it; snackbar "Saved to Downloads". On Android below 10: "Saving to Downloads needs Android 10 or newer."
- **States:** items are conditional. Errors go to a snackbar ("That action did not work" or a mapped error such as "The file is not completely downloaded yet.").
- **UX notes for redesign:** the sheet can contain a single item. "Download only this one" only adds that file and does not deselect the others, so the label is misleading.

## TOR-12 Align episodes sheet
- **Source:** `T/ui/ParityContent.kt:446-498`. **Platform:** both (AdaptiveSheet: bottom sheet on phones, side sheet when wide).
- **How to get there / leads to:** Files › "Align episodes".
- **Controls & actions:**
  - Int field "First episode": 1–9999, default 1, digits only, max 5 chars.
  - Int field "Offset": −9999 to 9999, default 0, a leading "−" allowed.
  - Live preview list.
  - "Use the names": resets the alignment to null.
  - "Save": disabled with no videos. Saves, then snackbar "Saved" (`ui_settings_saved`).
- **Data shown:**
  - Title "Align episodes" with the hint "Names with a number keep it plus the offset; the others are numbered in order from the first episode."
  - Per video (sorted by path): file name; "Number found in the name" or "By order (check it)"; an episode pill "Ep. N" or "—" (green when parsed, warn when by order).
- **States:** "This torrent has no video files" empty state.
- **UX notes for redesign:** the offset and first-episode semantics are hard to grasp. There is no per-file override. The fields are plain text inputs with no steppers.

## TOR-13 Peers tab
- **Source:** `T/ui/DetailTabs.kt:408-509`, `sortPeers` in `T/vm/TorrentViewModels.kt:141`. **Platform:** both.
- **How to get there / leads to:** TOR-08, tab 3. Leads to TOR-14.
- **Controls & actions:**
  - Small segmented sort: "Down" (default), "Up", "Progress". All sort descending.
  - "?" icon "What the flags mean": opens TOR-14.
- **Data shown:**
  - Summary "N connected · down X · up Y".
  - Per peer: Hub icon; address in monospace; "client (or Unknown client) · country"; "↓ rate · ↑ rate · %"; trailing flag letters string.
- **States:**
  - Loading.
  - Error: "Could not read the peers".
  - Empty: "No peers yet", "Looking for peers through DHT, PEX and the trackers."
- **UX notes for redesign:** flags are raw letters that need a separate legend sheet. Country is a raw code, with no flag icon. There are no peer actions (ban, copy).

## TOR-14 Peer flags legend sheet
- **Source:** `T/ui/DetailTabs.kt:454-482`. **Platform:** both.
- **How to get there / leads to:** "?" in TOR-13.
- **Controls & actions:** "Close".
- **Data shown:**
  - D: "Downloading from this peer"
  - U: "Uploading to this peer"
  - I: "Incoming connection"
  - P: "uTP connection"
  - E: "Encrypted connection"
  - X: "Found through PEX"
  - H: "Found through DHT"
  - S: "Snubbed: sends nothing"
- **States:** none.
- **UX notes for redesign:** this could be tooltips or icons inline in the rows.

## TOR-15 Trackers tab
- **Source:** `T/ui/DetailTabs.kt:522-576`, `ReannounceButton` in `T/ui/ParityContent.kt:503`. **Platform:** both. Polls every 5 s.
- **How to get there / leads to:** TOR-08, tab 4.
- **Controls & actions:**
  - Tonal button "⚡ Re-announce": shows a spinner and is disabled while busy. The table refreshes 3 s later.
  - The empty-state action is also "Re-announce".
- **Data shown:**
  - Summary "N trackers · N working".
  - Per tracker: URL (monospace, 1 line); "Seeders X · Leechers Y · Downloaded Z" ("—" when unknown); the tracker message (2 lines); a status pill: Working (OK), Updating (neutral), Failed (ERR), Not contacted (neutral).
- **States:**
  - Loading.
  - Error: "Could not read the trackers".
  - Empty: "No trackers", "This torrent finds peers through DHT and PEX only."
- **UX notes for redesign:** trackers cannot be added, removed or copied per torrent. The global own-trackers list lives in TOR-26.

## TOR-16 Torrents › Downloads section (background downloads list)
- **Source:** `T/ui/DownloadsContent.kt:151-320`, `T/TorrentsScreens.kt:297-318`, view model `T/vm/TorrentViewModels.kt:277-338`. **Platform:** both; chips are Android-only in practice.
- **How to get there / leads to:** Torrents › "Downloads" segment (also the orphan `TorrentDownloadsScreen`). A row tap opens TOR-08 (OVERVIEW). Until the warning is acknowledged, the section shows TOR-17 instead.
- **Controls & actions:**
  - Switch row "Background downloads". Hint, Android: "Keep downloading saved torrents with the app in the background, and allow downloads from the source picker." Desktop: "Keep downloading and sharing saved torrents while the app is open, and allow downloads from the source picker." Turning it on also requests the notification permission on Android 13+.
  - Status chips (Android signals):
    - "Waiting for Wi-Fi" (no-op).
    - "Paused: low battery" (no-op).
    - "Paused by the system. Tap to resume": resumes all listed items.
  - Per row: Pause/Resume icon ("Pause"/"Resume"), Delete icon "Remove download" (opens TOR-18), and a row tap that opens the torrent.
- **Data shown:**
  - Off banner: "Background downloads are off: saved torrents pause when you stop watching."
  - List = persisted torrents with progress below 1.
  - Per row: series or name (2 lines); "Episode N"; a thin wavy progress bar; state pill; a live line from per-row stats (1 s tick): "X of Y · ↓rate down · ↑rate up · N peers · eta left".
- **States:**
  - Loading.
  - Error: "Could not load the downloads".
  - Unacknowledged: the TOR-17 warning.
  - Empty: "Nothing downloading" with "Choose Download in the source picker of an episode to fetch it for later." (on) or "Turn background downloads on to fetch episodes for later." (off).
  - Off banner; chips.
- **UX notes for redesign:** this overlaps the app's Downloads page Active tab (DL-02), which also lists active torrents, and the Torrents Library. Users meet three lists for the same torrents. The status chips look tappable but two of them do nothing.

## TOR-17 Background downloads warning (full screen) + DownloadGate dialog
- **Source:** `T/ui/DownloadsContent.kt:77-146` (content), `T/DownloadGate.kt:43-104` (full-screen dialog wrapper). **Platform:** both; texts differ.
- **How to get there / leads to:**
  - Inline: Torrents › Downloads while `backgroundAck` is false.
  - As a full-screen non-dismissable dialog: any "Download" action that goes through `rememberDownloadGate().launch` (Details "Download", source picker) while not acknowledged or turned off. Back = "Not now".
- **Controls & actions:**
  - Button "I understand": stores the ack, turns background downloads on, waits up to 2 s for settings, asks POST_NOTIFICATIONS on Android 13+, then runs the pending download.
  - Text button "Not now": the inline version goes back to Library; the dialog cancels the download.
- **Data shown:**
  - Warning icon.
  - Title, Android: "Background downloads use your phone". Desktop: "Background downloads use your computer".
  - Body, Android: "Saved torrents keep downloading and sharing even when nothing plays. This means:". Desktop: "While the app is open, saved torrents keep downloading and sharing even when nothing plays. This means:".
  - Bullets (Android adds battery and notification):
    - "Some CPU use to check and write pieces."
    - (Android) "Battery use while downloading, also with the screen off."
    - "Storage space and writes in the download folder."
    - "Network traffic, which counts against metered or capped connections."
    - "Uploading to other peers: your IP address is visible to them."
    - (Android) "Android shows a permanent notification while torrents transfer."
  - Android 13+: "Nijiyome asks for permission to show notifications so you can see the progress, pause everything and know when an episode is ready."
  - Note, Android: "You can turn background downloads off at any time in Settings › Torrents. With Wi-Fi only on, nothing downloads in the background on mobile data." Desktop: "…in Torrents › Settings."
- **States:** none.
- **UX notes for redesign:** a full-screen wall of text before the first download. The inline version completely hides the Downloads list until it is accepted.

## TOR-18 Remove download dialog
- **Source:** `T/ui/DownloadsContent.kt:242-255`. **Platform:** both.
- **How to get there / leads to:** the Delete icon on a TOR-16 row.
- **Controls & actions:**
  - Title "Remove this download?"; body "It leaves the library and stops downloading and seeding."
  - "Remove download": always deletes the files (`deleteFiles = true`, view model `:322`).
  - "Close".
- **States:** none.
- **UX notes for redesign:** unlike TOR-06 there is no "keep files" option, which is inconsistent.

## TOR-19 Torrents › History section
- **Source:** `T/ui/ParityContent.kt:515-610`, view model `T/vm/ParityViewModels.kt:635-676`. **Platform:** both.
- **How to get there / leads to:** Torrents › "History". A row tap opens TOR-08. If the torrent has left the library, the tap re-adds it (streamed, not kept) by info-hash and then opens it.
- **Controls & actions:**
  - Text button "🗑 Clear": opens TOR-20.
  - Row tap: open or re-add.
  - Retry on error.
- **Data shown:**
  - Count "N torrents" (monospace).
  - Per row: series or name; the raw name when it differs; "Ep. N · relative time · downloaded bytes"; origin pill "Played", "Download", "Added" or "Magnet"; a "Removed" warn pill when the torrent was removed.
- **States:**
  - Loading.
  - Error: "Could not load the history".
  - Empty: "No torrents yet", "Every torrent you play, download or add shows up here with its date."
  - Failure snackbar "That action did not work".
- **UX notes for redesign:** no search, filter or date grouping. Re-adding a removed torrent happens silently on tap, with no confirmation or indication.

## TOR-20 Clear torrent history dialog
- **Source:** `T/ui/ParityContent.kt:558-571`. **Platform:** both.
- **Controls & actions:** title "Clear the session history?"; body "Only the list is cleared; torrents in the library stay."; buttons "Clear" and "Cancel".
- **States:** none.
- **UX notes for redesign:** none.

## TOR-21 Torrents › Debrid section (global switches + service list)
- **Source:** `T/ui/ParityContent.kt:619-675` (section), `:778-799` (switch rows), view model `T/vm/ParityViewModels.kt:697-766`. **Platform:** both; the intro text differs.
- **How to get there / leads to:** Torrents › "Debrid", "Configure debrid" (Library empty state, Settings), or Settings › Torrents › Debrid's note. Contains TOR-22 cards.
- **Controls & actions (debrid preferences card):**
  - Switch "Use debrid". Default **on**. Hint: "Off: no cache lookups and every torrent goes through P2P."
  - Switch "Prefer TorBox when cached". Default on, disabled when "Use debrid" is off. Hint: "A torrent cached on your service moves up in the source list and can be the one Auto plays."
  - Switch "Download through TorBox when cached". Default on, disabled when "Use debrid" is off. Hint: "Download on a cached torrent fetches the episode over HTTPS with the web downloads (Settings › Downloads rules) instead of P2P."
  - Snackbars: "{Service} linked as {user}"; "{Service}: the key works ({user})"; "{Service}: {problem}". Problems: the API key is not valid; active downloads limit reached; monthly limit reached; your plan does not include it; too large for your plan; too many requests, try again in a moment; not cached; no connection; the link did not work; no video file for this episode; service error.
- **Data shown:**
  - Intro, Android: "With a debrid account, a torrent the service already has plays at once over HTTPS, without downloading it on the phone. The key is kept in the Android Keystore." Desktop: "…on this computer. The key is stored encrypted, with its key in the system keyring."
  - Footer note: "The first linked service in the list is preferred. Sources cached on a service show a badge in the source picker."
  - Service order: linked accounts first (by priority), then unlinked services with TorBox (recommended) first. The four services are TorBox, Real-Debrid, AllDebrid and Premiumize.
- **States:**
  - Debrid not available in this build: an ErrorState "Debrid is not available in this build." with a "Retry" button that does nothing.
  - None linked: every card shows the key field.
- **UX notes for redesign:** the switch labels hard-code "TorBox" even when another service is preferred. The same three switches appear again in Settings › Torrents (TOR-26). The unavailable state has a dead Retry button. There is no explicit drag-to-reorder; priority changes only via "Prefer".

## TOR-22 Debrid service card (TorBox / Real-Debrid / AllDebrid / Premiumize)
- **Source:** `T/ui/ParityContent.kt:678-771`. **Platform:** both.
- **How to get there / leads to:** within TOR-21, one card per service.
- **Controls & actions:**
  - Unlinked:
    - Password field "API key": masked, trimmed, password keyboard. Inline error texts: "The service did not accept this key.", "No connection to the service. Try again.", or the service-specific problem.
    - Tonal button "Test and link": disabled while blank or while linking (spinner).
  - Linked:
    - Outlined "Test the key" (spinner while checking).
    - Outlined "Prefer": only when the card's priority is greater than #1. It moves the service to first.
    - Red text "Unlink": no confirmation.
- **Data shown:**
  - Service name.
  - Pill "Recommended" (TorBox, primary) or "Experimental · untested" (others, warn).
  - Priority pill "#N" (#1 primary).
  - Pill "Linked" or "Not linked".
  - Capability text: TorBox and Premiumize show "Shows which sources are cached before playing."; Real-Debrid and AllDebrid show "No cache lookup: it is tried when a torrent plays, falling back to the phone (desktop: this computer) if the service does not have it."
  - When linked: "Account: {user}"; "Premium until {date}" (red when expired) or "Premium end unknown"; "Key saved".
- **States:** linking, checking, error, linked, expired premium.
- **UX notes for redesign:** "Unlink" is instant with no confirmation. Four tall cards stack up. There is no link to get an API key from the service site.

## TOR-23 Add torrent sheet
- **Source:** `T/ui/ParityContent.kt:129-298`, view model `T/vm/ParityViewModels.kt:438-631`. **Platform:** both (AdaptiveSheet); drag-drop and preview are desktop-only.
- **How to get there / leads to:** the "+" in TOR-01, or a system `magnet:` / `.torrent` intent (pre-filled). On success it opens TOR-08 of the new torrent.
- **Controls & actions:**
  - Source field "Magnet link or info-hash" (2–4 lines, autofocus, placeholder `magnet:?xt=urn:btih:…`). The live classification hint changes with the input:
    - Empty: "Paste a magnet link, a 40-character info-hash or pick a .torrent file."
    - Magnet: "Magnet link: the metadata is fetched from peers."
    - Hash: "Info-hash: the metadata is fetched from peers."
    - Invalid: error "This is not a valid magnet link or info-hash."
  - Outlined "📎 Choose a .torrent file": a file picker accepting application/x-bittorrent, octet-stream or `*/*`. When a file is picked, the field is replaced by a file chip (name) with ✕ "Remove the file".
  - Desktop: drag a `.torrent` from the file manager onto the sheet. The sheet shows a primary border while hovering; the hint text changes to "Drop the .torrent file here" (otherwise "You can also drag a .torrent file onto this sheet."). Only the first `.torrent` among the dropped files is taken.
  - **Series (optional)**, hint "Link the files to a title so they show as its episodes. Set the first episode or an offset for packs that do not start at 1."
    - Search field "Search AniList": 350 ms debounce, at least 2 characters, max 8 results.
    - Results list: cover, title, year; tap to pick.
    - The picked series shows as a cover + title chip with ✕ "Remove the series".
  - Int fields "First episode" (1–9999, default 1) and "Offset" (±9999, default 0).
  - Desktop file list: TOR-24.
  - Switch "Keep and download", default **on**. Hint: "Downloads every file and keeps it in the library. Off: it behaves like a streamed torrent."
  - Buttons "Cancel" and "Add" ("Adding…" with a spinner). "Add" is enabled only with a valid source, when not busy, and, if a file list exists, with at least one file chosen.
- **Data shown:**
  - Series results: "No titles match", a loading spinner, or a mapped error.
  - Add errors in red, for example "That file is not a valid .torrent."
- **States:** empty, invalid, magnet, hash, file, adding, error; preview loading or error (TOR-24).
- **UX notes for redesign:** a long single-column sheet. On Android it has no file preview, so the user cannot pick files before adding. The alignment fields appear even with no series picked. There is no paste-from-clipboard button.

## TOR-24 Add torrent: file preview / selection (desktop)
- **Source:** `T/ui/ParityContent.kt:307-419`, `loadPreview` in `T/vm/ParityViewModels.kt:539`. **Platform:** desktop.
- **How to get there / leads to:** automatic once the TOR-23 source is valid (600 ms debounce for typed magnets).
- **Controls & actions:**
  - "Retry" on a preview error.
  - Text buttons "All", "None", and "Videos only" (shown only when some files are not videos).
  - Per-file checkbox; tapping the row toggles it. All files are checked at first.
  - If a subset is chosen, the add uses `addManualFiles`; otherwise it uses `addManual`.
- **Data shown:**
  - Status under the source field:
    - Reading a file: "Reading the files of the torrent…"
    - Magnet: "Looking for the metadata in the swarm… X s of Y", with "No peers yet" or "N peers connected".
    - "Adding now downloads the whole torrent."
    - Timeout: "No peer sent the metadata in N s. Check the magnet, or try again later."
    - Other errors: "Could not read the files of the torrent: …"
  - Section "Files": "N of M files · size"; a list of max 320 dp height sorted by path, each row with checkbox, path (2 lines), "Ep. N" or "—" (alignment applied live), and size.
  - Footer: "Only the checked files download. Check the episode of each file before adding." When none are checked, in red: "Choose at least one file".
- **States:** loading with progress, timeout, error, loaded.
- **UX notes for redesign:** a nested scroll list (320 dp) inside a scrolling sheet. The metadata status sits far from the file list. There is no folder grouping.

## TOR-25 Torrent settings page (Speed, Network, Storage, Background)
- **Source:** `T/ui/SettingsContent.kt:59-362`, `T/TorrentsScreens.kt:397-425`, view model `T/vm/TorrentViewModels.kt:345-394`, defaults `core/model/.../torrent/Torrent.kt:172-232`. **Platform:** both; many rows are platform-gated.
- **How to get there / leads to:** Torrents › "Settings" (embedded, no top bar); Settings › Torrents (full page with top bar "Settings" and Back on compact, or a pane on desktop). Every field saves immediately and shows the snackbar "Saved" (`ui_settings_saved`).
- **Controls & actions:**
  - Intro: "How the torrent engine downloads, connects and stores files."
  - **Speed** card:
    - "Download limit": number field (suffix KiB/s, 0–1,000,000, max 7 digits, default 0) plus a 10-stop slider [0, 128, 256, 512, 1024, 2048, 5120, 10240, 25600, 51200] KiB/s. Hint: "0 = unlimited. Applies at once." Error: "Enter a number from 0 to 1000000".
    - "Upload limit": the same control, default 0.
  - **Network** card:
    - Switch "Wi-Fi only" (Android, default on). Hint: "No background downloads on mobile data; playing a torrent on mobile data asks first."
    - Switch "Upload on mobile data" (Android, default off). Hint: "Off: on metered networks the app uploads at most 1 KiB/s."
    - Number "Listen port": 0 or 1024–65535, default 0. Hint: "0 = random port. Other peers connect to you here." Error: "Use 0 or a port from 1024 to 65535".
    - Switch "UPnP port forwarding": default off on Android, on on desktop. Hint, Android: "Ask the router to open the listen port. Off by default on phones." Desktop: "Ask the router to open the listen port, for more incoming connections."
    - Number "Max connections": 1–500, default 30 on Android, 50 on desktop. Hint: "Peers connected at the same time per torrent." Error: "Enter a number from 1 to 500".
    - Subheading "Peer discovery":
      - Switch "DHT" (default on). Hint: "Find peers without trackers (needed for most magnet links)."
      - Switch "Peer exchange (PEX)" (default on). Hint: "Learn about more peers from the connected ones. Applies to torrents added from now on."
      - Switch "Local peer discovery (LSD)" (default on). Hint, Android: "Find peers on your local network (only on unmetered networks)." Desktop: "Find peers on your local network."
  - **Storage** card:
    - Android: segmented "App storage" (default) | "Folder". Choosing Folder opens the SAF tree picker with a persistable grant. Hint: "Downloads stay in the app's own storage, without permissions. Uninstalling the app deletes them." or "Completed downloads move to {folder}.". With a folder chosen, the button "Change folder" shows.
    - Desktop: "Download folder" with the hint "New torrents download to {path}. Torrents already added stay in their folder."; buttons "Change folder" (folder picker), "Use the default folder" (only when one was chosen), and "Open folder".
    - Usage line "Used X · free Y". A compact error with "Retry" when it cannot be read.
    - "After watching": segmented "Keep in the library" | "Delete after watching" (default Delete). Hint: "Delete frees the space when the player closes; Keep leaves the torrent in the library."
  - (TOR-26 cards are inserted here: Debrid, Bandwidth schedule, Own trackers, Streaming cache.)
  - **Background downloads** card:
    - Switch "Download in the background": default off, disabled until the warning is acknowledged. Hint: "Accept the warning in Torrents › Downloads first." when locked; otherwise Android "Saved torrents keep downloading with the app in the background, following the network and battery rules." or desktop "Saved torrents keep downloading while the app is open."
    - Switch "Pause with low battery" (Android, default on). Hint: "Below 20 % without a charger, or with battery saver on."
- **Data shown:** current values, storage usage, folder.
- **States:** storage loading, error or ok; numeric field error states; background switch locked.
- **UX notes for redesign:** one very long scroll with 8 cards. Speed limits show a slider and a text field for the same value, with KiB/s raw units. The locked background switch sends the user to another screen to unlock it. The Android "Folder" segment opens a picker on select, an unusual pattern.

## TOR-26 Torrent settings: Debrid, Bandwidth schedule, Own trackers, Streaming cache / preload
- **Source:** `T/ui/ParityContent.kt:808-925`. **Platform:** both.
- **How to get there / leads to:** inside TOR-25, between Storage and Background.
- **Controls & actions:**
  - **Debrid** card: the same three switches as TOR-21 ("Use debrid", "Prefer TorBox when cached", "Download through TorBox when cached"). Note: "TorBox is the recommended, tested service. Real-Debrid, AllDebrid and Premiumize are experimental and untested. Link an account in Torrents › Debrid." The note has no link button.
  - **Bandwidth schedule** card:
    - Switch "Use other limits during a time window", default off. Turning it on seeds 08:00–23:00, down 1024 KiB/s, up 0. Hint: "For example 1 MiB/s during the day and no limit at night. The window can cross midnight."
    - When on: text time fields "From (HH:MM)" and "Until (HH:MM)". They accept `HH:MM`, `H:MM` or `HHMM`, 24 h; placeholder "23:00"; error state when invalid.
    - When on: int fields "Download KiB/s" and "Upload KiB/s" (0–1,000,000). Hint: "0 = unlimited inside the window. Outside it the limits above apply."
  - **Own trackers** card:
    - Multiline field "Trackers (one per line)" (3–8 lines). Hint: "udp://, http://, https:// or wss:// URLs announced on every torrent." Invalid schemes are dropped silently on save.
    - Button "Save trackers": enabled only when changed.
  - **Streaming cache** card:
    - Switch "Free space automatically", default off. Turning it on sets 10 GiB. Hint: "When streamed torrents (not kept) exceed the limit, the oldest are deleted."
    - When on: int field "Limit (GiB)", at least 1 (max 1,000,000 MiB).
    - Switch "Preload the next episode": default on (Android), off (desktop). Hint: "In a pack, the next episode starts downloading when the current one is 80 % downloaded."
- **Data shown:** current values.
- **States:** the schedule and cache detail fields show only when switched on; time field error.
- **UX notes for redesign:** times are typed as free text with no time picker. "Preload next" sits under "Streaming cache", which is a grouping mismatch. Debrid is duplicated with TOR-21 with no link to it. Tracker validation fails silently.

## TOR-27 Torrent feedback: snackbars and error texts (cross-cutting)
- **Source:** `T/ui/Common.kt:83-129`. **Platform:** both; some codes apply to Android only.
- **How to get there / leads to:** global snackbar from every torrent view model.
- **Data shown:**
  - Snackbars: "Torrent removed"; "Removed N from the library"; "Saved to Downloads"; "Saved"; "That action did not work".
  - Error codes mapped to text:
    - missing: "This torrent is no longer in the session. It may have been removed after watching."
    - moved: "This torrent was moved to your folder when it completed."
    - no_space: "Not enough free space: X needed."
    - no_file: "No file in this torrent matches the episode. Pick it in Files."
    - background_off: "Background downloads are off. Turn them on in Torrents › Downloads."
    - incomplete: "The file is not completely downloaded yet."
    - export_unsupported: "Saving to Downloads needs Android 10 or newer."
    - invalid_source: "This is not a valid magnet link or info-hash."
    - invalid_file: "That file is not a valid .torrent."
    - other: "That did not work".
  - Loading is shown only after 150 ms.
- **UX notes for redesign:** some error texts point to locations ("Torrents › Downloads") that the redesign may move.

---

## DL-01 Downloads page shell (top bar, search, tabs)
- **Source:** `D/DownloadsScreen.kt:127-258`, view model `D/DownloadsViewModel.kt:116-274`. **Platform:** both.
- **How to get there / leads to:** `Route.Downloads(tab)` (see Entry points). It holds tabs DL-02 to DL-09.
- **Controls & actions:**
  - Top bar "Downloads"; back arrow ("Back") only when not a rail destination.
  - Action "Open folder" (desktop): opens the web-download root (the chosen folder or the default `<Downloads>/Nijiyome/Episodes`).
  - Action "Check files" (FactCheck icon): integrity check, disabled while busy. Snackbar "Every downloaded file is fine" or "N broken files found".
  - Search field "Search downloads": filters every tab (title, source, chapter, file name). Clear ✕ has the hard-coded Spanish content description "Limpiar búsqueda".
  - Scrollable tabs: "Active" (default), "Anime", "Manga", "Novels", "History", "Local library".
  - Automatic: asks POST_NOTIFICATIONS once an active download exists (never on an empty screen).
- **Data shown:**
  - "Using X": web storage plus the bytes torrents hold on disk.
  - A linear progress bar while busy (verify, check now, import, scan).
- **States:** loading after a delay (`!loaded`); busy bar. Each tab's empty state is below.
- **UX notes for redesign:** 6 tabs mix media type (Anime, Manga, Novels), lifecycle (Active, History) and source (Local library). The search applies across tabs without saying so. The "Check files" icon is unlabelled. There is a Spanish a11y string.

## DL-02 Active tab
- **Source:** `D/DownloadsScreen.kt:325-432`. **Platform:** both.
- **How to get there / leads to:** Downloads › Active. A torrent row opens TOR-08 (FILES).
- **Controls & actions:**
  - Web download row:
    - Pause (while queued or downloading), Resume (paused), or Retry (failed).
    - Delete icon "Cancel download": opens DL-03.
  - "Torrents" group header, then torrent rows (torrents with progress below 1 that are not seeding):
    - Row tap: opens the torrent client.
    - Pause/Resume icon.
  - When the tab is empty but completed or paused torrents exist: a list item "N paused or complete torrent(s) · size" with the hint "They are in the Anime tab, with the downloaded episodes." and the button "View" (row tap too), which switches to the Anime tab.
- **Data shown:**
  - Web row: 44 dp cover; title; "Episode N · source · state (Queued/Downloading/Paused/Failed) · eta left". Then either a progress bar and "42% · X / Y", or the failure text in red. Failure texts:
    - "Broken file": "The file is missing or incomplete. Download it again."
    - "The download failed."
    - "Debrid service: {problem}"
    - the raw error.
  - Torrent row: cover; series or name; "Episode N · Torrent · size"; a progress bar.
- **States:** empty: "Nothing downloading", "Download an episode from its page, the player or Download season." (+ the torrents-elsewhere hint).
- **UX notes for redesign:** web and torrent items have different row designs and action sets (torrents cannot be removed here). There is no bulk pause/resume-all. ETA is estimated from 2 samples.

## DL-03 Cancel download dialog
- **Source:** `D/DownloadsScreen.kt:397-404`, `ConfirmDialog` at `:860`. **Platform:** both.
- **Controls & actions:** title "Cancel download"; text "Cancel {title} · episode N and delete what was downloaded?"; confirm "Cancel download"; dismiss "Cancel".
- **States:** none.
- **UX notes for redesign:** the confirm and dismiss labels ("Cancel download" / "Cancel") are confusingly similar.

## DL-04 Anime tab (downloaded episodes grouped by title + completed torrents)
- **Source:** `D/DownloadsScreen.kt:436-520`, grouping in `D/DownloadsViewModel.kt:82`. **Platform:** both.
- **How to get there / leads to:** Downloads › Anime. Leads to Details (group header), the offline player (episode), and the torrent client (torrent row). The auto-download card (DL-05) sits at the top.
- **Controls & actions:**
  - Group header tap: opens Details (only with an AniList id).
  - Group Delete icon "Delete all episodes": opens DL-06.
  - Episode row tap or ▶ "Play offline": plays the local file (disabled when broken).
  - Broken episode: pill "Broken file" (ERR) and ↻ "Download again".
  - Episode "Open folder" (desktop).
  - Episode Delete icon "Delete": **no confirmation**.
  - "Torrents" group: completed or paused torrent rows (tap opens the client; Pause/Resume when progress is below 1).
- **Data shown:**
  - Group header: 48 dp cover; title; "N episodes · size". Groups are sorted by most recent completion; episodes are in ascending order.
  - Episode row: "Episode N"; "source · size".
- **States:** empty, when there are no episodes and no completed torrents: "No downloaded episodes", "Downloaded episodes play here without a connection." The auto-download card still shows.
- **UX notes for redesign:** single-episode delete has no confirmation while group delete does. The auto-download card pushes the content down on every visit. The episode rows have no thumbnail and no watched state.

## DL-05 Auto-download card (rules)
- **Source:** `D/DownloadsScreen.kt:524-583`. **Platform:** both; the body text differs.
- **How to get there / leads to:** the top of the Anime tab.
- **Controls & actions:**
  - Per rule:
    - Text button "Simulate": snackbar "The next check would queue episodes 5, 6" or "No new episodes to queue right now".
    - Switch enabled/disabled (default enabled).
    - Small segmented profile picker "Best quality" | "Balanced (≤1080p)" (default) | "Data saver (≤720p)".
  - Suggestions, "From your Watching list": up to 8 WATCHING titles that have no rule, each with the button "Add" (creates a rule with the Balanced profile).
  - Tonal button "↻ Check now": busy. Snackbar "N new episodes queued" or an error.
- **Data shown:**
  - Body, Android: "New episodes of these shows are queued automatically (every 30 min on Wi-Fi, or with Check now)." Desktop: "New episodes of these series queue by themselves (when the app opens and every 30 min, or with Check now)."
  - Per rule: title and "Last queued: episode N".
- **States:** no rules (only suggestions and the button).
- **UX notes for redesign:** rules cannot be deleted from the UI, only disabled. "Simulate" results go only to a transient snackbar. Three long segmented labels on each rule row crowd phones. There is no poster.

## DL-06 Delete all episodes of a title dialog
- **Source:** `D/DownloadsScreen.kt:481-488`. **Platform:** both.
- **Controls & actions:** title "Delete all episodes"; text "Delete every downloaded episode of {title}?"; confirm "Delete all episodes"; dismiss "Cancel".
- **States:** none.
- **UX notes for redesign:** none.

## DL-07 Manga and Novels tabs (downloaded chapters)
- **Source:** `D/DownloadsScreen.kt:590-632`. **Platform:** both.
- **How to get there / leads to:** Downloads › Manga or Novels. A tap on a completed chapter opens the manga or novel reader offline. If its args cannot be decoded, snackbar "This chapter can't be opened offline."
- **Controls & actions:**
  - Chapter row tap: enabled only when completed.
  - ↻ "Retry" when the chapter failed or is broken.
  - Delete icon "Delete": no confirmation.
- **Data shown:**
  - Grouped by media: 48 dp cover and title.
  - Chapters sorted by number: label; "state · size"; a progress bar while queued or downloading.
- **States:** empty: "No downloaded manga chapters" or "No downloaded novel chapters", with "Download chapters from the reader or the title page."
- **UX notes for redesign:** groups have no delete-all and no "open title", unlike Anime. Chapter rows have no pause or resume.

## DL-08 History tab (web downloads)
- **Source:** `D/DownloadsScreen.kt:636-680`. **Platform:** both.
- **How to get there / leads to:** Downloads › History. A tap on a completed row plays it offline.
- **Controls & actions:**
  - Text button "Clear history": **no confirmation**.
  - Row tap (completed only).
  - ↻ "Retry" on rows that are not completed.
- **Data shown:**
  - 40 dp cover; title; "Episode N · date/time" (medium date, short time); the error text in red.
  - State pill: Downloaded (OK), Failed or Broken file (ERR), others neutral.
- **States:** empty: "No history yet", "Finished and failed downloads are listed here."
- **UX notes for redesign:** clearing the history is instant here, while the torrent history (TOR-20) asks first. This History is separate from Torrents › History.

## DL-09 Local library tab
- **Source:** `D/DownloadsScreen.kt:684-772`. **Platform:** both.
- **How to get there / leads to:** Downloads › Local library. A row tap opens the player with the local URI. Leads to DL-10 and DL-11.
- **Controls & actions:**
  - Tonal "🎞 Import videos": file picker for `video/*`, several files allowed. Snackbar "N videos added".
  - Outlined "📁 Scan a folder": folder picker; the same snackbar.
  - Row tap: play.
  - Row "Open folder" (desktop).
  - Row 🔗 "Match": opens DL-10.
  - Row Delete: opens DL-11.
- **Data shown:**
  - 40 dp cover; title or file name; "Episode N · file name · size"; pill "Not matched" (warn) when there is no ref or no episode.
- **States:**
  - Empty: "Your local library is empty", "Import videos or scan a folder to play them here and match them to their shows."
  - Busy bar during import or scan.
- **UX notes for redesign:** a flat list with no grouping by series and no sort. The matched/unmatched filter is missing. On desktop, watched folders are managed elsewhere (DL-15).

## DL-10 Match / repair local video dialog
- **Source:** `D/DownloadsScreen.kt:776-857`. **Platform:** both.
- **How to get there / leads to:** 🔗 on a local row.
- **Controls & actions:**
  - Text field "Show title" (autofocus, prefilled with the title or the file name without extension). The 🔍 trailing icon searches AniList (6 results).
  - Result rows: cover and title. Tap to pick (highlighted).
  - Field "Episode": digits only, max 4.
  - "Save": saves the picked match, or keeps the current one when only the episode changed.
  - "Unmatch": clears the match. It sits in the dismiss slot, so there is no plain Cancel.
- **Data shown:** a progress bar while searching; the error "The search failed. Check your connection."
- **States:** searching, failed, picked.
- **UX notes for redesign:** there is no Cancel; the dismiss slot is the destructive "Unmatch". Search runs only on the icon tap (not on Enter). There is no episode count or year shown to disambiguate titles.

## DL-11 Remove local video dialog
- **Source:** `D/DownloadsScreen.kt:747-771`. **Platform:** both.
- **Controls & actions:** title "Remove from the library"; text "{file} will leave the library. Delete the file too?"; buttons "Keep the file", "Delete the file" (red), and "Cancel".
- **States:** none.
- **UX notes for redesign:** none.

## DL-12 Download season slot (on Details)
- **Source:** `D/DownloadSeasonSlot.kt:71-103`. **Platform:** both.
- **How to get there / leads to:** Details of an anime, under the episode list. It is hidden for non-anime titles, without a DownloadRepository, or with an unknown episode count (the count falls back to the provider's episode list). Leads to DL-13.
- **Controls & actions:** tonal button "☁ Download season".
- **Data shown:** "N of M downloaded", when any are done.
- **States:** hidden when the total is 0.
- **UX notes for redesign:** none.

## DL-13 Download season sheet
- **Source:** `D/DownloadSeasonSlot.kt:106-185`. **Platform:** both (AdaptiveSheet).
- **Controls & actions:**
  - RangeSlider for episodes 1..total (default the whole season; hidden when there is only 1 episode).
  - Text field "Preferred audio language": max 8 characters, defaulting to the settings value. Hint: "ISO code, e.g. ja, es, en. Empty = any."
  - Text field "Preferred server": max 40 characters, defaulting to the settings value. Hint: "Sources whose name contains this go first."
  - Switch "Remember language and server" (default off; only with a SettingsRepository): writes both fields to the download settings.
  - "Cancel".
  - "Download N" (N = episodes not yet downloaded). Disabled when busy or when everything is already downloaded. Snackbar "N episodes queued" or an error text: "This source uses DASH, which can't be downloaded. Pick another server.", "The download folder reached its storage limit.", "No downloadable source was found for this episode.", "The folder is not available. Choose it again.", or "Could not download: …".
- **Data shown:** title "Download season"; the media title; "Episodes A to B"; "X of Y already downloaded or queued".
- **States:** busy; all already downloaded (button disabled).
- **UX notes for redesign:** audio language is a free-text ISO code with no picker. Server is a free-text substring, with no list of known servers. The range slider is imprecise for long series and has no numeric inputs. The sheet does not use the DownloadGate or show torrent options.

## DL-14 Settings › Downloads section
- **Source:** `D/DownloadSeasonSlot.kt:193-305`, defaults `core/model/.../Download.kt:130-160`. **Platform:** both; rows are gated by `desktopDownloadsUi`.
- **How to get there / leads to:** Settings › Downloads (`SettingsSection.DOWNLOADS`). Saved immediately through `onUpdate`.
- **Controls & actions:**
  - "Download folder" row:
    - Label: the chosen folder name; or "App folder: {path}" (Android); or "Default folder: {path}" (desktop).
    - Buttons: "📁 Change folder" (SAF tree with a persistable grant, or a desktop path); "Use the app folder" (Android) or "Use the default folder" (desktop), only when a folder is chosen; "Open folder" (desktop).
  - "Simultaneous downloads", hint "How many episodes download at the same time.": small segmented 1 | 2 | 3 | 4 | 5, default 2.
  - Desktop "Speed", hint "Total speed of the web downloads.": small segmented "No limit" (default), 512 KiB/s, 1 MiB/s, 2 MiB/s, 5 MiB/s, 10 MiB/s. The labels come from `formatRate`.
  - Android switch "Wi-Fi only", default on. Hint: "Downloads wait for an unmetered network."
  - "Storage limit": hint "In use: X" (when known) or "Auto-downloads stop when the folder reaches it." Small segmented "No limit" (default), 5 GB, 10 GB, 25 GB, 50 GB, 100 GB.
  - Switch "Check for new episodes", default on. Hint, Android: "Every 30 minutes on Wi-Fi with enough battery, for the auto-download rules." Desktop: "When the app opens and every 30 minutes while it is open, for the auto-download rules."
  - "Download quality", hint "Used by Download season and auto-download.": segmented "Best quality" | "Balanced (≤1080p)" (default) | "Data saver (≤720p)".
  - Switch "Free watched episodes", default off. Hint: "Delete a downloaded episode once you finish watching it."
  - Text field "Preferred server": max 40 characters, default empty. Hint: "Sources whose name contains this go first."
  - Text field "Preferred audio language": max 8 characters, default empty. Hint: "ISO code, e.g. ja, es, en. Empty = any."
  - Desktop: local library folders (DL-15).
- **States:** usage unknown (falls back to the hint).
- **UX notes for redesign:** 6-option segmented rows (Speed, Storage) are cramped on narrow panes. Values snap to the nearest step, so custom values set elsewhere are hidden. The free-text server and language fields save on every keystroke. The web download settings are separate from Torrent settings (TOR-25) even though both control downloads.

## DL-15 Settings › Downloads › Local library folders (desktop)
- **Source:** `D/DownloadSeasonSlot.kt:315-351`. **Platform:** desktop.
- **Controls & actions:**
  - "＋📁 Add folder": folder picker, scanned at once.
  - Per folder: "Open folder" (OpenInNew icon) and Delete "Stop watching {folder}". No confirmation; the videos stay in the library.
- **Data shown:**
  - "Local library", hint "Watched folders: new videos that appear in them join the library by themselves."
  - Folder paths (2 lines).
- **States:** empty: "No folders yet. Add one and its videos join on their own."
- **UX notes for redesign:** this is placed at the end of the Download settings instead of next to the Local library tab (DL-09). Android has no watched-folders concept; it only has a one-off "Scan a folder".

## DL-16 Downloads feedback: snackbars and error texts (cross-cutting)
- **Source:** `D/DownloadsScreen.kt:147-167, 277-321`. **Platform:** both.
- **Data shown:**
  - Snackbars: "N new episodes queued"; "N broken files found" or "Every downloaded file is fine"; "N videos added"; "The next check would queue episodes …" or "No new episodes to queue right now"; "This chapter can't be opened offline."
  - Mapped errors:
    - dash_not_supported: "This source uses DASH, which can't be downloaded. Pick another server."
    - storage_limit: "The download folder reached its storage limit."
    - no_source: "No downloadable source was found for this episode."
    - folder_unavailable: "The folder is not available. Choose it again."
    - other: "Could not download: {msg}"
  - Row errors: "The file is missing or incomplete. Download it again.", "The download failed.", "Debrid service: {problem}".
- **UX notes for redesign:** results of user-initiated checks (Check files, Simulate) appear only as transient snackbars with no persistent result view.

---

**Counts:** 43 surfaces (27 torrents TOR-01..TOR-27, 16 downloads DL-01..DL-16); about 245 settings and actions (about 151 torrents, about 94 downloads). The tally covers individual buttons, menu items, toggles, fields, sort, filter and segmented options, per-item and bulk actions, and gestures such as drag-drop and long-press. Each segmented or dropdown option counts once. Per-service debrid card controls count once rather than four times. Controls duplicated across locations (the Debrid switches, Re-announce) count once per location.

---

# Part 3. Extensions (`feature/extensions`)

Paths are relative to `feature/extensions/src/commonMain/kotlin/app/nijiyome/feature/extensions/` unless noted.

## EXT-01 Extensions screen (hub)
- **Source:** `ExtensionsScreen.kt:103`. **Platform:** both.
- **How to get there:** Desktop rail "Extensions" (`Route.Extensions`, top-level on wide windows); phones via You › Extensions; Settings › Player › Extensions; the Settings modal; Ctrl+K "Ir a Extensiones"; and the onboarding Done text.
- **Leads to:** Provider (EXT-14), Extension servers (LNK-12) and every sheet or dialog below.
- **Top bar:** title "Extensions", back arrow (phones only), a pill "N installed", the **updates badge** button ("N updates", opens EXT-08) and the **overflow ⋮** with "Disable all" / "Enable all" (EXT-09).
- **Body order:**
  1. Intro: "Extensions (.kai) resolve episode streams. Install only extensions you trust."
  2. Install panel (EXT-02)
  3. Updates banner (when updates exist; "View")
  4. "Every extension is disabled…" banner with **Enable all**
  5. Install error (ErrorState "Could not install: reason" + **Choose file**)
  6. Installed list (EXT-04)
  7. Providers (EXT-10)
  8. Repositories (EXT-11)
  9. Stremio addons (EXT-12, only when the Stremio repo is bound)
  10. Server extensions (EXT-13)
- **States:** delayed loading; empty "No extensions installed" / "Install a .kai package to start watching."
- **Snackbars:** installed, enabled/disabled, removed, updated to X, N updates found, repo added, addon installed or updated, matches reset, all enabled/disabled, "Could not complete: reason".
- **UX notes:** A single very long LazyColumn mixes 5 different concepts (local .kai, catalog providers, repositories, Stremio, PC servers) with only text section heads. It needs tabs or segmented sub-pages and a search or filter.

## EXT-02 Install panel (file / drop)
- **Source:** `ExtensionsScreen.kt:401`. **Platform:** both; drop and paths on **desktop only** (`kaiDropAndPaths`).
- **Controls:**
  - **Choose file** (SAF picker `*/*`, then checks the `.kai` name; "Installing…" while busy).
  - **Install from URL** (Android) or **Install from URL or path** (desktop) → EXT-03.
  - **Desktop drag and drop** of a `.kai`: a 2 dp primary border on hover; a non-.kai drop shows "Choose a file with the .kai extension."
- **Hint:** "Choose a .kai package from your files." (Android) or "Choose or drop a .kai package, or paste its path or its link." (desktop).

## EXT-03 Install from URL (or path) dialog
- **Source:** `ExtensionsHub.kt:203`. **Platform:** both; path accepted on desktop.
- **Controls:** a URL field (label "URL" or "URL or path") that accepts an http(s) `.kai` link or a `manifest.json` next to it (desktop: also a local `.kai` path, with quotes and `file://` cleaned). Validation errors: "Enter an http(s) link." / "…or the path of a .kai file." **Install** ("Installing…"), **Close**. Closes on success.

## EXT-04 Installed extension card
- **Source:** `ExtensionsScreen.kt:438`. **Platform:** both.
- **Shows:**
  - Initial avatar, name, and a compatibility pill: **Full** (ok) / **Partial** (warn) / **Unsupported** (err).
  - "Version X · Torrents/Web streams" and the description (3 lines).
  - **Permissions** chips: up to 6, then "+N". Labels: Internet / Saves data / Uses torrents / host names for `network:domain:*`; "No special permissions"; the heading becomes "Accesses" when every permission is a domain.
  - **Compat banner:** Partial "Search and streams work; the custom catalog is skipped." / Unsupported "needs features that are not available…" + "Technical detail: reason".
  - **Footer facts:** "Updated {relative}", "Sources: streams, torrents", SHA-256, domain count. Error "Could not read the extension details." + Retry.
  - **Hidden-browser chip** ("N sites need a browser") + desktop warning when no Chromium is found.
- **Actions:**
  - Enable **Switch** ("Use X"; disabled when Unsupported)
  - **Delete** icon → EXT-06
  - **Preferences** (when the extension declares any) → EXT-05
  - **Update to X** button ("Updating…")
  - **Log / Hide log** toggle → inline mono log panel (max 260 dp), with `[ok|warn|err] op: message` lines or "Nothing logged yet."
- **UX notes:** The card is dense (6 information zones). Compat and permissions need a clearer visual hierarchy. There is no sort or filter, and no per-source toggles inside an extension.

## EXT-05 Extension preferences dialog
- **Source:** `ExtensionsHub.kt:250`. **Platform:** both.
- Title "X preferences". The fields are generated from the extension's manifest:
  - **TOGGLE** → Switch (saved on change).
  - **SELECT** → a FilterChip row of options (saved on change).
  - **TEXT** → text field with the default as placeholder (saved on **Save**).
  - **SECRET** → password field; once stored it shows only "Saved" + **Remove**.
- Optional description per field. States: loading / error / "This extension has no preferences."
- **UX:** SELECT renders as chips, which is fine for a few options and breaks with many. The mixed save model (instant vs on Save) is invisible to the user.

## EXT-06 Remove extension dialog
- `ExtensionsScreen.kt:284`. "Remove X?" / "The extension and its data are deleted from this device." Actions: Remove / Close.

## EXT-07 Downgrade dialog
- `ExtensionsScreen.kt:301`. "Install an older version of X?" / "You are about to install vA over vB, which is newer…" Actions: **Install anyway** / Cancel.

## EXT-08 Updates sheet + banner
- **Source:** `ExtensionsHub.kt:137`. **Platform:** both.
- **Controls:**
  - **Check for updates when the app starts** switch (`extensionUpdateCheck`, default on).
  - **Check now** ("Checking…").
  - **Update all** (shown when more than one update).
  - Per row: name, "A → B", release notes, **Update** ("Updating…").
- **States:** "Add a repository to receive updates." / "Every extension is up to date." / list (max 420 dp scroll).

## EXT-09 Kill switch (Disable all / Enable all)
- `ExtensionsHub.kt:91`, `:124`. Overflow menu item. "Disable every extension?" / "Nothing is uninstalled and preferences are kept…" Actions: **Disable all** / Close. Enable all applies with no confirmation.

## EXT-10 Providers section (custom catalogs)
- **Source:** `ExtensionsHub.kt:363`. **Platform:** both.
- Head "Providers": "Catalogs of the extensions with their own sources, including those of your PC server."
- Rows show source name, "extension · description" and a kind pill: **Anime / Manga / Novel**. A tap opens EXT-14.
- States: loading, error + Retry, empty "No installed extension offers its own catalog."

## EXT-11 Repositories section
- **Source:** `ExtensionsHub.kt:386`, `:513`. **Platform:** both.
- **Add row:** "Repository URL" field (http(s) validation, inline error) + **Add** ("Working…").
- **Repo card:** name, "N extensions · read {relative}", "Last refresh failed: …", **Refresh** icon, **Remove** icon → confirm "Remove X?" ("Installed extensions stay…").
- **Entries inside the card:** name + description, then **Update** / "Installed" pill / **Install** ("Installing…").
- Empty: "No repositories" / "Repositories you add appear here with their extensions."
- **UX:** Repository browsing has no search or category filter, and every entry is rendered inline in the card.

## EXT-12 Stremio addons section (manifests)
- **Source:** `ExtensionsHub.kt:407`, `:558`. **Platform:** both, only when `stremioAvailable`.
- **Add row:** "Manifest URL" (accepts http(s) and `stremio://`) + **Install**.
- **Addon card:** name, "Version X · types", enable **Switch** ("Use X"), description, "This addon offers no streams: it will not add sources." (error), **Update**, **Reset matches** (snackbar "Title matches reset"), **Remove** → confirm.
- Empty: "No Stremio addons installed."

## EXT-13 Server extensions section ("Extensions on your PC" / "Extensions from other PCs")
- **Source:** `ServerExtensionsSection.kt:49`. **Platform:** both. The desktop title is "Extensions from other PCs" and adds a **Extension servers** button → LNK-12.
- **Per linked server:** a head (name, "N extensions" or URL, **Refresh**). States:
  - Loading.
  - "This server is turned off on this phone."
  - Error: unreachable / unauthorized ("Pair it again in You › Linked device") / incompatible / other, each with Retry.
  - "This server has no extensions installed."
- **Per extension row:** name, "vX · ecosystem · kinds", an Enabled/Disabled pill. When the PC has a newer copy than the local one: a warning text and **Update** (from the repository) and/or **Update from the PC** ("Updating…") with a result line ("X updated to Y" or a specific error: gone, install, integrity SHA-256, PC too old, unreachable).
- Empty: "No PC server linked yet."

## EXT-14 Provider screen (catalog browser)
- **Source:** `ProviderScreens.kt:98`. **Platform:** both. Route `Route.Provider(ext, source)`, the list pane.
- **Top bar:** source name + extension name, back.
- **Controls:** a search field "Search in this provider". Results list with infinite scroll (loads more within 3 items of the end); without a query, home rows of poster cards (horizontal).
- **States:** loading (delayed), error "The provider did not answer" + Retry, "Nothing found for 'q'.", empty "Nothing to show" / "This provider has no home rows. Use the search above."

## EXT-15 Provider item screen
- **Source:** `ProviderScreens.kt:240`. **Platform:** both. `Route.ProviderItem`, the detail pane.
- **Top bar:** title, back, **bookmark** toggle (Save / Remove from saved).
- **Header:** cover, kind pill, status pill, Completed pill, genres. Buttons:
  - **Start watching / Start reading / Continue {n}**
  - **Save / Remove from saved**
  - **Completed** FilterChip toggle
  - **AniList page** (only when matched)
  - Description
- **Entries:** a **Filter episodes** field + a **sort toggle** (oldest/newest first). Rows: "Episode N" or "Chapter N" or title, sub "title · LANG"; trailing Watched check or "In progress" pill. A tap plays (watch) or opens the reader (read/novel).
- **States:** "The provider no longer has this title.", "This title has no episodes or chapters yet.", "No entry matches the filter."
- **Non-UI helper:** `ProviderResume.kt` resumes a provider episode from history (Continue watching). Keep it in the redesign.

## EXT-16 Sources settings rows (embedded in Settings › Player)
- `ProviderScreens.kt:463` `SourcesSettingsExtras`. See SET-PLY-SRC in Part 4.

# Part 3b. Nijiyome Link (`feature/link`) + Link Monitor (`desktopApp`)

Paths are relative to `feature/link/src/commonMain/kotlin/app/nijiyome/feature/link/` unless noted.

## LNK-01 Linked device screen (shell)
- **Source:** `LinkedDeviceScreen.kt:128`. **Platform:** both. Routes `Route.Linked` and `Route.Link` render the same screen.
- **How to get there:** Desktop rail "Linked device" (Computer icon); phones via You › Linked device; Settings › Nijiyome Link › **Linked device** (SET-LNK-01); the Android notification "Link handoff" (`WaveBLinks.kt:28`).
- **Leads to:** the Player (handoff, continue), Extension servers (LNK-12).
- **Behavior:** asks POST_NOTIFICATIONS once paired (for "Send to device"), polls `GET /v1/player` every 2 s while visible and paired, and shows a snackbar for errors.
- **Body order:**
  1. Intro
  2. Handoff card (LNK-09)
  3. Extension server entry card "Extensions from your PC" + **Set up** → LNK-12
  4. A state-dependent block: Unpaired (LNK-02/03/04) / Failed (LNK-11) / Paired (LNK-05…08)

## LNK-02 Unpaired: empty state + Pair a device panel + QR scanner
- **Source:** `LinkedDeviceScreen.kt:235`; scanner in `QrScanner.android.kt` (camera) and `QrScanner.desktop.kt:62` (V4L2 webcam, `camera/*`). **Platform:** both.
- **Empty state:** "No PC linked" / "On the PC open Nijiyome › Link › Open pairing window and scan the code."
- **Controls:**
  - **Scan QR** (asks camera permission) → inline live scanner + **Cancel scan**.
  - **Paste code** → LNK-03.
  - "Pairing…" spinner.
  - Messages: "Camera permission denied. Paste the code instead.", the pair error text, and a hint about the camera permission.
- **Desktop scanner states:** starting / aim / "no camera" / "camera failed: detail".

## LNK-03 Paste code dialog
- `LinkedDeviceScreen.kt:308`. Field "Pairing code" (mono), accepting a `nijiyome-link:v1?…` text or a 10-char code. Invalid: "That is not a nijiyome-link:v1 link or a 10-character code." Actions: **Pair** / Cancel.

## LNK-04 PCs on your network (mDNS discovery) + pair dialog
- **Source:** `LinkedDeviceScreen.kt:647`, `:683`. **Platform:** both.
- Panel "PCs on your network": "Looking for PCs with Nijiyome open on this network…" (spinner), or rows (name, host) with **Pair**.
- **Dialog** "Pair with X": the fingerprint in readable groups (TOFU) + "Check that the fingerprint matches…", a code field (10 alphanumerics), **Trust and pair** (disabled without a fingerprint) / Cancel.

## LNK-05 Paired: status card
- **Source:** `LinkedDeviceScreen.kt:369`. **Platform:** both.
- **Shows:**
  - A status avatar and "PC connected" (primaryContainer) or "PC out of reach" (errorContainer) + a hint.
  - "{server name} · https://host:port".
  - **CA fingerprint** (short form + **Show details / Hide details** for the full grouped form).
  - "Last seen …" (refreshes every 30 s) and "This phone is registered as X (Android)".
  - History notices: "This PC does not save what you play here" / "History sync paused by the PC" / "Syncing the history with the PC…".
- **Actions:**
  - **Continue on the PC** (spinner), with "{title} · episode N at mm:ss" or "Watch something on the phone first…".
  - **Retry** when unreachable.
  - **Forget PC** (destructive text) → LNK-10.

## LNK-06 This PC's capabilities + enhance profile picker
- **Source:** `LinkedDeviceScreen.kt:484`, `LinkProfileName.kt:86`. **Platform:** both.
- **Facts:** Server ("name · version"); Frame generation on the phone ("Allowed" / "Only on the PC").
- **Enhance profile:** a vertical **radio list** (about 15 profiles with LSFG combos). Labels: "No enhancement", "Anime4K to Np", "Frame generation ×N", "Frame generation, auto (N fps)", and combinations.
  - Unplayable profiles are disabled with a reason ("Your phone does not play Np", "could not play").
  - Note "May drop frames on this phone".
  - Profiles measured as not sustainable are hidden.
  - Fallback notice: "Your phone does not play X: your preferred profile is now Y."
  - Below: a sustain pill ("Sustained in real time" / "May stutter" / "Not measured yet") + output resolution.
- **States:** delayed loading, error + Retry, "This PC has no profile it can sustain in real time."
- **UX:** About 15 radio rows of technical labels with no comparison or preview. It needs grouped chips (Upscale × FrameGen) or a two-axis picker.

## LNK-07 Remote control panel
- `LinkedDeviceScreen.kt:564`. "Nothing is playing on the PC…", or "title · episode N" + "Playing/Paused · mm:ss / mm:ss". Buttons: **Previous**, **Play/Pause**, **Next** (FilledTonalIconButtons). 409 → "Nothing is playing on the PC".
- **UX:** There is no seek or volume control.

## LNK-08 Enhance a stream panel
- `LinkedDeviceScreen.kt:753`. Field "Stream URL" (URI keyboard, no autocorrect, IME Go), hint "The PC fetches and enhances this stream." Errors: invalid / rejected / timeout + session error. **Start session** ("Starting…"), enabled when the capabilities loaded.

## LNK-09 Handoff card ("The PC sent you …")
- `LinkedDeviceScreen.kt:604`. Title, "Episode N · from mm:ss · {age}". **Play here** / **Dismiss**.

## LNK-10 Forget PC dialog
- `LinkedDeviceScreen.kt:725`. "Forget this PC?" / "The client certificate is deleted. You will need to pair again." Actions: destructive **Forget PC** / Cancel.

## LNK-11 Failed link state
- `LinkedDeviceScreen.kt:207`. ErrorState "Link problem" + message + Retry, plus Forget PC.

## LNK-12 Extension servers screen
- **Source:** `ExtensionServersScreen.kt:96`. **Platform:** both (`Route.ExtensionServers`, when `ExtensionServerRepository` is bound).
- **How to get there:** LNK-01 card "Set up"; EXT-13 desktop button "Extension servers".
- **Top bar:** "Extensions from your PC", back, **Check servers** (refresh).
- **Body:** intro, error banner (dismiss), Connect panel (LNK-13), Servers panel (LNK-15), Info panel (LNK-16).
- **Errors:** invalid link, insecure host (non-LAN must use https), invalid URL, expired link, unreachable, wrong password, pairing required, incompatible, setup required, throttled, generic (code).

## LNK-13 Connect panel
- `ExtensionServersScreen.kt:172`.
- **With a camera:** **Scan QR code** + **Paste pairing link**. **Without a camera** (desktop with no webcam): **Paste pairing link** is the main button.
- **Manual connect** ("Or enter the address and password"): "Server address" (mono, placeholder `http://192.168.1.20:8090`) + "Viewer password" (not saved in instance state) + **Connect** ("Connecting…").
- Hints: plain http only on LAN ranges (192.168, 10, 172.16–31, Tailscale 100.64, link-local, .local); away from home use the HTTPS tunnel or Tailscale.

## LNK-14 Paste pairing link dialog
- `ExtensionServersScreen.kt:291`. Pre-filled from the clipboard when it looks like a link. Field "Pairing link" (`nijiyome://extensionserver/connect?server=…&code=…`). Actions: **Connect** / Cancel.

## LNK-15 Linked servers list + remove dialog
- `ExtensionServersScreen.kt:335`.
- **Per server:** name, URL, enable **Switch**, **Remove** icon → "Remove server" / "Remove X from this device?…".
- **Status pill:** Disabled / Online ("N sources") / Offline (err) / Access revoked (warn) / Checking… / Not checked yet.
- **Offline fallback note:** local extensions vs other online servers. Unauthorized note: "pair again from the PC".
- Empty: "No servers linked yet" / "Scan the QR code shown on your PC…".

## LNK-16 Info panel ("Why link your phone to your PC?")
- `ExtensionServersScreen.kt:444`. Static lists: **Advantages** (5), **Disadvantages** (5), **Extras** (4: QR, revocable, fallback, viewer password).
- **UX:** Static marketing text inside a settings screen; a collapsible "Learn more" would serve it better.

## LNK-17 Confirm pending link dialog (deep link)
- `ExtensionServersScreen.kt:157`. When a `nijiyome://` pairing link opens the app: "Connect to this PC?" / "…Only accept links you generated on your own PC." Actions: **Connect** / Cancel.

## LNK-18 Link & System Monitor dialog
- **Source:** `desktopApp/src/main/kotlin/app/nijiyome/desktop/system/LinkMonitorDialog.kt:101`, hosted in `desktopApp/.../Main.kt:387`. **Platform:** desktop.
- **How to get there:** the title-bar **Link port chip** (LNK-19); tray menu "Open Link & System Monitor" / "Link & Status Monitor" (`DesktopShell.kt:314/319`). Telemetry is sampled every 1.5 s while open (3 s otherwise).
- **Header:**
  - Status dot: primary = active sessions, tertiary = running, outline = off.
  - Title "Nijiyome Link & System Monitor".
  - Subtitle: "Link server unavailable" / "stopped" / "Port :N · N active sessions" / "Port :N · Idle · N paired devices".
  - **Link Settings** (opens the Settings modal at LINK) and close ×.
- **Section 1, "Devices using this PC (Link)":**
  - Off card: "Enable it in Settings › Nijiyome Link…".
  - Idle card: "No device is currently streaming…" + GPU · encoder · addresses (mono).
  - **Session cards:** device chip; state chip (Paused / Buffer full / Processing); source chip (Torrent / Library / Web); title · Ep; **Stop** button; pipeline chips (upscale, frame gen, "No upscaling", encoder); a **progress bar with the cached RAM window overlay** + "pos / dur" + "Cached in RAM: a–b"; metrics (RAM Cache X / Y MB, Lead ahead +N s, Segments, Render pace s/seg).
- **Section 2, "Active Torrents":** total ↓/↑, **Open Torrents**; up to 5 rows (name, %, progress bar, "state · N peers", ↓/↑), or empty text.
- **Section 3, "Memory & Cache":**
  - Link segment cache bar (used / limit) + **per-session budget segmented** 512 MB / 1 GB / 2 GB / 4 GB (same setting as SET-LNKSRV-14).
  - App RAM (JVM) bar.
  - Disk: image cache and torrent storage (+ free).
- **UX notes:** All text is an inline ES/EN `if (spanish)` switch, outside the resources. It duplicates the RAM cache setting. This is the only place with live per-session telemetry; keep it.

## LNK-19 Title bar Link chip + tray entries
- **Source:** `desktopApp/.../system/DesktopTitleBar.kt:220` (`LinkPortChip`), `desktopApp/.../system/DesktopShell.kt:314-319`. **Platform:** desktop (custom title bar; hidden in fullscreen and PiP).
- The chip shows the Link port and active session count and opens LNK-18. The tray has Monitor, Torrents and Settings items (the shell agent owns the rest of the tray).
- **UX:** With the native title bar on (SET-DAT-05), the chip is gone and only the tray reaches the Monitor.

## LNK-20 Link logs (from devices)
- Lives in Settings › About › Logs sheet (SET-ABT-04): desktop source chips per linked device and **Enviar a la PC** on Android. Listed here so Link parity checks include it.

---

# Part 4. Settings rows embedded from other modules (player, extensions, search, manga, novel)

Settings composables that live outside `:feature:settings`. Paths are relative to the repo root. "Default" comes from the model classes in `core/model/src/main/kotlin/app/nijiyome/model/`. Labels are the English strings from `values/strings.xml` of each module. None of the six files contains a hardcoded Spanish UI literal. The hardcoded non-localized literals are listed per surface. Every change saves immediately through `onUpdate` (host `SettingsViewModel.updateSettings`, which shows the "saved" snackbar) unless a control has an explicit Save button.

Host mount points (for context): `feature/settings/src/commonMain/kotlin/app/nijiyome/feature/settings/SettingsSections.kt:400` (player extras), `:402` (sources extras, wrapped in `EmbedSourcesRows`), `:403-412` (the "Subtitle style" row with the "Edit" button that opens the subtitle sheet), `:588` (discover extras). `SettingsScreen.kt:710-711` mounts the manga and novel readers under `SettingsSection.READER`.

---

## SET-PLY-EXTRA: PlayerSettingsExtras

- **Source:** `feature/player/src/commonMain/kotlin/app/nijiyome/feature/player/PlayerSettingsExtras.kt:92` (rows `PlaybackRows` :135, `SecondaryLanguageRow` :171, `TranslationRows` :196, `ShortcutRows` :394, `KeyCaptureDialog` :431). Labels: `PlayerLabels.kt`. Platform flags: `playerCapabilities` (expect at `PlayerScreenPlatform.kt:99`; actuals at `androidMain/.../PlayerScreenPlatform.android.kt:387` and `desktopMain/.../PlayerScreenPlatform.desktop.kt:149`).
- **Platform:** both, with the differences marked per row. The host mounts it right after the "Skip intro" switch in Settings › Player.
- **Layout:** three plain-text group titles (`titleSmall`, primary colour): "Subtitles (advanced)", "Subtitle translation", "Keyboard shortcuts". The playback rows have no title of their own.

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-PLY-20 | Slider, 3..30 s, step 1 (`steps=26`); saves on release | "Next episode countdown". Value line: "%d seconds", or "Turn on auto-next to use the countdown" | 3–30 | `autoNextCountdownSecs = 10` | Always shown. Disabled while `autoNext` is off (the auto-next switch belongs to the host) |
| SET-PLY-21 | Toggle | "Stable volume". Hint: "Evens out loud and quiet scenes" | on/off | `stableVolume = false` | Always |
| SET-PLY-22 | Toggle | "Background playback". Hint: "Keep the audio playing with media controls when you leave the app" | on/off | `backgroundPlayback = true` | **Android only** (`backgroundPlayback` capability is false on desktop) |
| SET-PLY-23 | Single-select chips, horizontal scroll | "Secondary subtitle". Hint: "A second subtitle language shown at the top" | None, EN, ES, JA, PT, FR, DE, IT, KO, ZH, RU, AR (raw ISO codes, uppercased) | `secondarySubLang = null` (None) | Always. Under group "Subtitles (advanced)" |
| SET-PLY-24 | Single-select chips, horizontal scroll | "Engine" | Off, Google, DeepL, DeepLX, Azure, OpenAI-compatible | `subtitleTranslation.provider = NONE` (Off) | Always. When Off: hint "Pick an engine to translate subtitles while you watch", and **every row below is hidden**. When Google: hint "Google works without a key (public endpoint)" |
| SET-PLY-25 | Single-select chips (2) + hint text that follows the choice | "Mode" | Live ("Each line is translated as it appears"), Pre-translate ("The whole track is translated before playback") | `mode = LIVE` | Engine ≠ Off |
| SET-PLY-26 | Single-select chips, horizontal scroll | "Target language" | ES, EN, PT, FR, DE, IT, JA, KO, ZH, RU, AR, ID, TR, PL (14 raw codes, from `SubtitleTranslators.TARGET_LANGUAGES`) | `targetLang = "es"` | Engine ≠ Off |
| SET-PLY-27 | Text field | "Server URL" (DeepLX, OpenAI-compatible) / "Endpoint (optional)" (Azure) | free text | `endpoint = ""` | Engine ∈ {DeepLX, Azure, OpenAI-compatible} |
| SET-PLY-28 | Text field | "Model" | free text, no placeholder | `model = ""` | Engine = OpenAI-compatible |
| SET-PLY-29 | Text field | "Region" | free text | `region = ""` | Engine = Azure |
| SET-PLY-30 | Button (action) | "Save" (saves 27–29 together, trimmed) | — | — | Same rows as 27–29. Enabled only when a field has changed |
| SET-PLY-31 | Status row + text button (action) | "API key" (DeepL, Azure) / "API key (optional)" (DeepLX, OpenAI-compatible). Status: "Key saved" / "No key saved" / "Not available in this build". Trailing button: "Remove" | — | no key (SecretStore `SecretKeys.translationApiKey(provider)`) | Engine ∈ {DeepL, DeepLX, Azure, OpenAI-compatible}. "Remove" appears only while a key is saved |
| SET-PLY-32 | Password text field + button | "New key" + "Save". The field clears after saving and turns to error state if the save fails | — | empty | Same as 31, and only when a SecretStore exists |
| SET-PLY-33 | Button (action) + inline result | "Test" / "Testing…". Result: "OK: <translation>" or "Failed: <reason>". Reasons: "the service is limiting requests; try again in a minute", or "Fill in the key, URL or region this engine needs", or the raw exception message | Translates the fixed English line "Hello! Where are you going?" | — | Engine ≠ Off. Disabled while a test runs or when the subtitle tools are not present |
| SET-PLY-34 | Info row + text button (action) | "Translation cache" · "%s used" · "Clear cache" | — | — | Engine ≠ Off and subtitle tools present. The button is disabled at 0 bytes |
| SET-PLY-35 | List of tappable rows, each showing its key as a pill → opens a **dialog** | Hint: "For a hardware keyboard or remote. Tap an action to change its key." (Android) / "Click an action to change its key." (desktop). Rows that clash show "Same key as another action" in red | See the keymap table below | `playerKeymap = emptyMap()` (= the defaults) | Always (Android too). PiP is listed only where the platform has PiP (both have it today) |
| SET-PLY-36 | Text button (action) | "Restore defaults" | — | — | Enabled only when the keymap has custom entries |

**Keymap actions (SET-PLY-35), from `core/player/src/commonMain/kotlin/app/nijiyome/player/PlayerKeymap.kt`:**

| Action | Label (Android / desktop) | Default key |
|---|---|---|
| play_pause | Play / pause | Space |
| seek_back | Back 10 s / **Back 5 s** | ← |
| seek_forward | Forward 10 s / **Forward 5 s** | → |
| fullscreen | Full screen | F |
| mute | Mute | M |
| pip | Picture in picture | P |
| prev_episode | Previous episode | J |
| next_episode | Next episode | K |
| volume_up | Volume up | ↑ |
| volume_down | Volume down | ↓ |
| screenshot | Screenshot | S |
| subs_toggle | Subtitles on / off / **Next subtitle track** | C |
| help | Show shortcuts | ? (SLASH) |
| *desktop only:* speed_up | Faster | . (PERIOD) |
| speed_down | Slower | , (COMMA) |
| audio_cycle | Next audio track | V |
| sub_delay_earlier | Subtitles 0.1 s earlier | Z |
| sub_delay_later | Subtitles 0.1 s later | X |
| skip_segment | Skip intro / ending | ENTER |
| loop | Loop on / off | L |

That is 13 rows on Android and 20 on desktop. Reserved keys that cannot be bound: BACK, HOME, APP_SWITCH, POWER, ESCAPE, TAB, ENTER, DPAD_CENTER, MENU, UNKNOWN.

### Dialogs / sheets
- **KeyCaptureDialog** (`PlayerSettingsExtras.kt:431`, AlertDialog). Title: the action label. Body: "Press the new key", then the captured key in large type or "Waiting for a key…". Errors: "That key belongs to the system" (reserved key) and "Already used by: <actions>" (clash; saving is still allowed). Buttons: "Save" (disabled until a non-reserved key is captured) and "Cancel". It captures key events through focus, so on a touch-only phone it does nothing without a hardware keyboard.

### UX notes for redesign
- The translation block is 6–10 stacked rows that change with the engine. Engine-specific connection fields plus their own Save, a separate key field with a second Save, Test, and Cache would fit a single "Translation engine" sub-page or card with an explicit configured/not-configured state.
- Language pickers (23, 26) are long horizontal rows of bare ISO codes ("ZH", "AR", "ID"). Show language names, or use a searchable list.
- Two Save buttons sit next to each other (connection fields and key). Fields that save on commit with a single key action would be clearer.
- The countdown slider depends on the host's auto-next switch, which lives in another module. The redesign should keep the two together; today the slider only greys out.
- The secondary-subtitle hint says "shown at the top", but the model comment says "under the first". Check which is true before rewriting the copy.
- desktop: `skip_segment` defaults to ENTER, which is also in RESERVED. After a user rebinds it, they cannot set it back to Enter except with "Restore defaults".
- The shortcut editor shows on Android (touch-first) with no check for a hardware keyboard. It could be collapsed by default there.
- Hardcoded non-localized: language codes; the test sentence (English, by design).

---

## SET-PLY-SUBS: SubtitleStyleSheet (full subtitle style editor)

- **Source:** `feature/player/src/commonMain/kotlin/app/nijiyome/feature/player/SubtitleStyleEditor.kt:592` (`SubtitleStyleSheet`), which wraps `SubtitleStyleEditor` :397. Building blocks: `SubtitleStylePreview` :149, `ColorPicker` :232, `AssModeRow` :291, `FontPicker` :600. The installed-font chooser is in `InstalledFontPicker.kt:44`. The font importer is expect `PlayerPlatform.kt:82`, with actuals `androidMain/.../PlayerPlatform.android.kt:74` (SAF `OpenMultipleDocuments`) and `desktopMain/.../PlayerPlatform.desktop.kt:124` (desktop file chooser, multiple). The container is `EnhancementSheet` (`EnhancementWords.kt:233`).
- **Platform:** both. The installed-font chooser is desktop only (Android reports no installed families). Container: a side sheet (`AdaptiveSheet`) when the window is ≥ 600 dp wide, otherwise a scrolling `ModalBottomSheet`.
- **Entry:** the host row "Subtitle style" (with a summary line) → button "Edit". Model: `AppSettings.subtitleStyle: SubtitleStyle` (`SubtitleStyle.kt`), saved through `vm::setSubtitleStyle`. Sliders update the preview while dragging and save on release. Chips, toggles and swatches save on tap.
- **Header:** title "Subtitle style" + Close icon button ("Close").
- **Preview (always on top):** a 150 dp stand-in video frame (fixed blue/brown gradient) with two sample lines: "This is how your subtitles will look" / "Changes show right away". It shows the font, size, colours, outline, shadow, box, blur, alignment, position and side margin live. Below it: the note "Applies to libmpv and Media3, also to translated and secondary lines."
- **Layout:** stacked `SectionCard`s titled Quick looks · Text · Outline · Background · Shadow · Position and spacing · ASS subtitle style, then a Reset button.

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-PLY-SUBS-01 | Chips, apply-on-tap (a chip shows selected when the current style matches that preset) | Card "Quick looks" | Default, Outline, Box, Yellow. A preset sets the colours, outline, box, shadow, blur, bold and italic. It keeps size, position, margins, font and ASS mode | matches "Default" | Always |
| SET-PLY-SUBS-02 | Slider, 50 %..250 %, snapped to 5 % | Card "Text": "Size: %d%" | 0.5–2.5 | `scale = 1.0` (100 %) | Always |
| SET-PLY-SUBS-03 | Single-select chips | "Font" | Outfit, Space Grotesk, System, then one chip per imported font (with a delete icon, "Delete font") | `font = outfit`, `fontFile = null` | Imported chips appear only once fonts have been imported |
| SET-PLY-SUBS-04 | Chip that opens a **dialog** | "Choose an installed font…" / "Installed font: <name>" | Any system font family | — | **Desktop only** (when the system lists installed families) |
| SET-PLY-SUBS-05 | File picker (action) | "Import fonts…" + note "Imported TTF / OTF fonts are also used by styled (ASS) subtitles On libmpv a newly imported font is used from the next episode." On error: "Some files were not valid fonts" | TTF/OTF, several files | — | When the subtitle tools are present |
| SET-PLY-SUBS-06 | Toggle chip | "Bold" | on/off | `bold = false` | Always |
| SET-PLY-SUBS-07 | Toggle chip | "Italic" | on/off | `italic = false` | Always |
| SET-PLY-SUBS-08 | Segmented | "Line alignment" | Left, Centre, Right | `align = CENTER` | Always |
| SET-PLY-SUBS-09 | Colour picker: swatches + "Custom" chip (R/G/B sliders 0..255) + opacity slider | "Text colour", "Opacity: %d%", "Red: %d" / "Green: %d" / "Blue: %d" | Swatches: White, Yellow #FFEB3B, #80DEEA, #A5D6A7, #F48FB1, #FFCC80, Black | `textColor = 0xFFFFFFFF` (white, 100 %) | RGB sliders only while "Custom" is on (that state is not saved) |
| SET-PLY-SUBS-10 | Toggle | Card "Outline": "Draw an outline" | on/off | `outline = true` | Always |
| SET-PLY-SUBS-11 | Slider, 0..6, snapped to 0.25 | "Thickness: %s" | 0–6 | `outlineSize = 2.5` | Disabled when outline is off |
| SET-PLY-SUBS-12 | Colour picker + custom RGB + opacity | "Outline colour" | Black, #424242, #1A237E, #4A148C, #004D40, White | `outlineColor = 0xFF000000` | Disabled when outline is off |
| SET-PLY-SUBS-13 | Toggle | Card "Background": "Background box". Hint: "A box behind the text" | on/off | `background = true` | Always |
| SET-PLY-SUBS-14 | Slider 0..100 % (stored inverted as opacity) | "Background transparency: %d%" | 0–1 | `backgroundOpacity = 0.6` → shows 40 % | Disabled when the box is off |
| SET-PLY-SUBS-15 | Colour picker + custom RGB, **no** opacity slider | "Background colour" | Black, #212121, #1A237E, #3E2723, White | `backgroundColor = black` | Disabled when the box is off |
| SET-PLY-SUBS-16 | Slider, 0..6, snapped to 0.25 | Card "Shadow": "Distance: %s" | 0–6 | `shadowOffset = 0` | **Disabled while the box is on.** The note "With the background box on there is no shadow: libmpv paints the box with the shadow colour." shows only then |
| SET-PLY-SUBS-17 | Colour picker + custom RGB + opacity | "Shadow colour" | the same swatches as the outline | `shadowColor = 0x80000000` (black, 50 %) | Disabled while the box is on |
| SET-PLY-SUBS-18 | Slider 0..100, step 1 | Card "Position and spacing": "Vertical position: %d%" + note "At 100 the subtitles sit at the bottom of the screen" | 0–100 | `positionPct = 100` | Always |
| SET-PLY-SUBS-19 | Slider 0..150, step 1 | "Bottom margin: %d" | 0–150 | `marginY = 22` | Always |
| SET-PLY-SUBS-20 | Slider 0..150, step 1 | "Side margin: %d" | 0–150 | `marginX = 25` | Always |
| SET-PLY-SUBS-21 | Slider, 0..20, snapped to 0.5 | "Line spacing: %s" | 0–20 | `lineSpacing = 0` | Always |
| SET-PLY-SUBS-22 | Slider, 0..5, snapped to 0.25 | "Edge softness: %s" | 0–5 | `blur = 0` | Always |
| SET-PLY-SUBS-23 | Segmented + hint text that follows the choice | Card "ASS subtitle style" | Keep original ("Signs, karaoke and fonts stay as the release made them."), Apply mine ("Dialogue uses your style; positioned signs keep their place."), Plain text ("Removes every ASS effect: plain text with your style.") | `assMode = ORIGINAL` | Always. Footer note: "Only libmpv draws ASS styles. Media3 shows them as plain text with your style." |
| SET-PLY-SUBS-24 | Toggle | "Apply the size to ASS too". Hint: "Only the size: the rest of the original style stays" | on/off | `assScale = false` | Only when ASS mode = Keep original |
| SET-PLY-SUBS-25 | Text button (action) | "Reset every option" | Restores `SubtitleStyle()` | — | Always. No confirmation |

### Dialogs / sheets
- **The sheet itself**: side sheet or bottom sheet (`EnhancementSheet`).
- **InstalledFontChooser dialog** (desktop): title "Choose an installed font…", a search field "Search fonts", a list where each family is drawn in its own font (check mark on the current one), empty state "No font matches", and "Cancel". Tapping a row picks it.
- **System file picker** for importing fonts (Android SAF, desktop file chooser).

### UX notes for redesign
- The preview is good but fixed: one gradient and two lines. There is no light or busy background to judge contrast, and it scrolls away in the bottom-sheet layout. Make it sticky, or offer a background toggle.
- Shadow and box are mutually exclusive, but the UI only greys out the shadow card. A single "Background: box / shadow / none" choice would show the libass rule.
- The three colour pickers differ: the background one has no opacity slider, because transparency is a separate slider in reverse polarity (transparency, not opacity). Unify them on one "opacity" control.
- Custom colour is three raw R/G/B sliders and the "Custom" state resets every time the sheet opens. A hue/saturation picker or hex field would be faster.
- Presets are text chips. They should be visual thumbnails that show the look.
- Thirteen sliders with the value only in the label text ("Thickness: 2.5"), in unlabelled units (scaled px). Name the units, or add an inline value badge.
- The in-player `SubtitleStyleQuickPanel` (same file, :323) repeats a subset of these controls: size, box, transparency, position, ASS mode, Reset, "More options". Keep them in sync.
- "Reset every option" has no confirmation and no undo.

---

## SET-PLY-SRC: SourcesSettingsExtras

- **Source:** `feature/extensions/src/commonMain/kotlin/app/nijiyome/feature/extensions/ProviderScreens.kt:463`. Model: `SourcePrefs` (`core/model/.../Sources.kt:161-171`).
- **Platform:** both. On **Android** it is always shown. On **desktop** the host wrapper `EmbedSourcesRows` (`feature/settings/src/desktopMain/.../HiddenBrowserSettings.desktop.kt:57`, `:120`) shows these rows only when a hidden Chromium browser is available (`state.available`). That wrapper also adds its own hidden-browser status and test rows above them (owned by `:feature:settings`, not inventoried here).

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-PLY-SRC-01 | Toggle | "Embed sources". Hint: "Open embed pages in a hidden browser and play the video they load. They rank below direct HLS / MP4 sources." | on/off | `sourcePrefs.allowEmbed = true` | See platform |
| SET-PLY-SRC-02 | Slider 5..60 s, step 5 (`steps=10`); saves on release | "Embed wait time: %d s". Hint: "How long to wait for the page to start the video before trying the next source." | 5–60 | `embedTimeoutSecs = 20` | Disabled when Embed sources is off |

### Dialogs / sheets
None.

### UX notes for redesign
- The row only greys out when Embed is off. It could hide or collapse instead.
- On desktop the two rows vanish entirely when Chromium is missing, so the user gets no hint that the feature exists. The redesign could show them disabled with the reason.
- These rows are mounted in the middle of the Player section. Their natural group is "Sources", next to the source-mode and quality rows.

---

## SET-MET-DISC: DiscoverSettingsExtras

- **Source:** `feature/search/src/commonMain/kotlin/app/nijiyome/feature/search/DiscoverSettingsExtras.kt:54` (`MetadataProviderRows` :144, confirm dialog :127).
- **Platform:** both (no expect/actual, no platform checks). The host mounts it in Settings › Metadata after the cache-TTL row.

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-MET-DISC-01 | Segmented | "Title language". Hint: "Used in Search, Details and the cards when the provider has that title." | Romaji, English, Native | `titleLanguage = ROMAJI` | Always |
| SET-MET-DISC-02 | Toggle | "Show 18+ content". Hint: "Initial value of the Search toggle. Details still asks before showing an adult title." | on/off | `showAdult = false` | Always |
| SET-MET-DISC-03 | Segmented | "Mature content". Hint: "How adult titles look in Home, Search and the details page once 18+ content is allowed." | Show, Blur, Warn | `matureContent = WARN` | **Always shown**, even while 18+ is off (where it has no effect) |
| SET-MET-DISC-04 | 6 toggles with a live status line each | "Providers". Hint: "Turn sources on or off; the list above sets their order. MAL needs a client id built into the app." Names: AniList, ani.zip, Kitsu, MyAnimeList, Shikimori, Tenrai. Status: "Off" / "Paused for %d min after errors" / "Error: %s" (red) / "Working" / "Not used yet in this session" | on/off each. Turning one on appends it to the end of the priority list; turning one off removes it | On: AniList, ani.zip, Kitsu, Tenrai. Off: MyAnimeList, Shikimori (`DEFAULT_PROVIDER_PRIORITY`) | Always. The switch of the last enabled provider is disabled, so the list is never empty |
| SET-MET-DISC-05 | Info + outlined button (action) → **confirm dialog** | "Recent searches" · "None saved" / "%d saved on this device" · "Clear recent searches" | — | — | The button is disabled when the count is 0 |

### Dialogs / sheets
- **Clear recent searches** AlertDialog: title "Clear recent searches", text "Remove every recent search saved on this device?", buttons "Clear recent searches" / "Cancel". On confirm it shows the snackbar "Recent searches cleared".

### UX notes for redesign
- The provider order is edited in a separate host list ("the list above"). Enable and order are split over two controls. Merge them into one reorderable list with switches.
- "Mature content" should be shown, or enabled, only when "Show 18+ content" is on. Together they form one privacy group.
- Hardcoded non-localized literals: the provider brand names (fine).
- Note on the host, right after this block: `SettingsSections.kt:591` has the **hardcoded Spanish literal** "Recordatorios de emisión" (airing reminders switch). It is outside this composable but in the same visual group.

---

## SET-RDR-MANGA: ReaderSettingsSection (manga)

- **Source:** `feature/manga/src/commonMain/kotlin/app/nijiyome/feature/manga/ReaderSettingsSection.kt:55` (`ReaderSettingsRows` ~:126, which the in-reader sheet reuses; `ReaderShortcutRows` ~:241 with an inline key-capture dialog). Model: `ReaderSettings` (`core/model/.../Manga.kt:166`). Keymap: `core/manga/src/commonMain/kotlin/app/nijiyome/manga/ReaderKeymap.kt`.
- **Platform:** both (no expect/actual or platform checks in the rows).
- **Layout:** group titles "Manga reader" › (title language) › "Layout" › "Colours" › "Page loading" › "Behaviour" › "Reader shortcuts".

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-RDR-MANGA-01 | Segmented | "Manga title language". Hint: "Separate from the anime title language." | Romaji, English, Native | `mangaTitleLanguage = ROMAJI` | Always (Settings only, not in the in-reader sheet) |
| SET-RDR-MANGA-02 | Segmented | "Reading mode" | Paged, Vertical, Webtoon | `mode = PAGED` | Always |
| SET-RDR-MANGA-03 | Segmented | "Direction" | Right to left, Left to right | `direction = RTL` | Always (also shown for Vertical/Webtoon, where it does not apply) |
| SET-RDR-MANGA-04 | Toggle | "Double page". Hint: "Two pages side by side on wide screens (paged mode)." | on/off | `doublePage = false` | Always (also shown outside paged mode) |
| SET-RDR-MANGA-05 | Segmented | "Page fit" | Width, Height, Screen, Natural | `fit = WIDTH` | Always |
| SET-RDR-MANGA-06 | Slider 0..2000 dp, step 50 | "Maximum width". Value: "No limit" at 0, else "%d dp" | 0–2000 (the model accepts up to 4000) | `maxWidthDp = 0` (No limit) | Always |
| SET-RDR-MANGA-07 | Slider 0..64 dp, step 4 | "Gap between pages". Value: "%d dp" | 0–64 | `pageGapDp = 0` | Always |
| SET-RDR-MANGA-08 | Toggle | "Crop borders". Hint: "Trims the white or black margins of scans." | on/off | `cropBorders = false` | Always |
| SET-RDR-MANGA-09 | Segmented | Group "Colours": "Background" | Black, Gray, White, Theme | `background = "black"` | Always |
| SET-RDR-MANGA-10 | Toggle | "Use system brightness" | on/off (turning it off sets the brightness to 60 %) | on (`brightness = -1`) | Always |
| SET-RDR-MANGA-11 | Slider 5 %..100 %, step 5 % | "Brightness". Value "%d%" (hardcoded format) | 0.05–1.0 | — (60 % on first switch-off) | **Only when "Use system brightness" is off** |
| SET-RDR-MANGA-12 | Toggle | Group "Page loading": "Load the whole chapter". Hint: "Eager loading; off loads pages around the one you read." | on/off | `eagerLoading = false` | Always |
| SET-RDR-MANGA-13 | Slider 1..20, step 1 | "Pages to preload". Value: "%d pages" | 1–20 | `prefetchPages = 5` | **Only when whole-chapter loading is off** |
| SET-RDR-MANGA-14 | Toggle | "Infinite scroll". Hint: "Vertical and webtoon modes continue into the next chapter." | on/off | `infiniteScroll = true` | Always (also in paged mode) |
| SET-RDR-MANGA-15 | Slider 0..600 px/s, step 20 | Group "Behaviour": "Auto-scroll speed". Value: "Off" at 0, else "%d px/s" | 0–600 (the model accepts up to 2000) | `autoScrollSpeed = 0` (Off) | Always |
| SET-RDR-MANGA-16 | Toggle | "Fullscreen when opening" | on/off | `fullscreenOnOpen = true` | Always |
| SET-RDR-MANGA-17 | Toggle | "Tap hides the controls". Hint: "A tap in the middle shows or hides the bars." | on/off | `hideChromeOnTap = true` | Always |
| SET-RDR-MANGA-18 | Toggle | "Show page progress" | on/off | `showProgress = true` | Always |
| SET-RDR-MANGA-19 | Toggle | "Zoom shortcuts". Hint: "Ctrl + wheel and the zoom keys." | on/off | `zoomShortcuts = true` | Always |
| SET-RDR-MANGA-20 | List of tappable rows, each with a key pill → **dialog** | Group "Reader shortcuts". Hint: "Keyboard and remote keys of the manga reader." Conflict line: "Same key as another action" | 12 actions (below) | `keymap = emptyMap()` | Always (Settings only) |
| SET-RDR-MANGA-21 | Text button (action) | "Reset shortcuts" | — | — | Enabled only when there are custom entries |

**Reader keymap defaults:** Next page →, Previous page ←, Scroll down ↓, Scroll up ↑, Next chapter N, Previous chapter P, Fullscreen F, Change reading mode D, Zoom in =, Zoom out −, Show / hide controls Space, Reload pages R. Reserved keys: BACK, ESCAPE, HOME.

### Dialogs / sheets
- **Key-capture dialog** (inline AlertDialog in `ReaderShortcutRows`). Title: the action. Body: "Press the new key", then the captured key or "Waiting for a key…". Errors: "That key is reserved", "Also used by: …". Buttons: "Save" / "Cancel". It is almost identical to the player's dialog: two copies of the same component.

### UX notes for redesign
- Direction, Double page and Infinite scroll each apply to only some reading modes, but they are always visible. Gate them on the mode (paged → direction and double page; vertical/webtoon → infinite scroll).
- Reading mode, Direction, Page fit and Background are plain text segments. They would read better as visual pickers: mode and direction icons, fit diagrams, colour swatches for the background.
- There is no preview of page fit, gap, background or brightness.
- The slider ranges are narrower than what the model accepts (max width 2000 vs 4000, auto-scroll 600 vs 2000). A value imported or synced above the range is clamped by the slider.
- The player and the manga reader each have their own copy of the key-capture dialog and shortcut list. A shared component is worth considering.
- Hardcoded non-localized literals: the brightness "%d%" format and the key glyphs (→ ← ↑ ↓ = −).

---

## SET-RDR-NOVEL: NovelReaderSettingsSection (typography + flow)

- **Source:** `feature/novel/src/commonMain/kotlin/app/nijiyome/feature/novel/NovelReaderSettingsSection.kt:86` (`NovelTypographyRows` ~:133, `NovelFlowRows` ~:239, `InstalledFontDialog` ~:526). Font list and labels: `NovelUi.kt:198-216`. Platform flag: `installedFontsOffered` (expect `NovelPlatform.kt:31`; `false` in `NovelPlatform.android.kt:46`, `true` in `NovelPlatform.desktop.kt:79`). Model: `NovelReaderSettings` (`core/model/.../Novel.kt:65`).
- **Platform:** both. Desktop adds the "Mono" preset and the installed-font chooser.
- **Layout:** group title "Novel reader", with typography rows and flow rows in one run (no sub-titles), then the group "Narrator" (SET-RDR-TTS).

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-RDR-NOVEL-01 | Single-select chips, horizontal scroll | "Font" | Serif, Sans, Georgia, Lora, Dyslexia friendly, **Mono (desktop)**, System, Imported font | `font = "serif"` | The "Imported font" chip shows only when a font has been imported |
| SET-RDR-NOVEL-02 | Chip → **dialog** | "Installed font…" (or the chosen family name) | Any system family (`family:<name>`) | — | **Desktop only** |
| SET-RDR-NOVEL-03 | File picker (action) | "Import TTF / OTF". On error: "That file is not a TTF / OTF font." After import: "Imported: <file>". Selects the imported font | One TTF/OTF file | `customFontPath = null` | Always |
| SET-RDR-NOVEL-04 | Text button (action) | "Remove imported font" (deletes the file; falls back to Serif if it was in use) | — | — | Only when a font has been imported |
| SET-RDR-NOVEL-05 | Slider 10..40 sp, step 1 | "Text size". Value "%d sp" | 10–40 | `sizeSp = 18` | Always |
| SET-RDR-NOVEL-06 | Slider 1.0..2.5, step 0.1 | "Line height". Value "%.1f×" (hardcoded format) | 1.0–2.5 | `lineHeight = 1.6` | Always |
| SET-RDR-NOVEL-07 | Slider 0..48 dp, step 2 | "Paragraph spacing". Value "%d dp" | 0–48 | `paragraphSpacingDp = 12` | Always |
| SET-RDR-NOVEL-08 | Slider 0..1600 dp, step 50 | "Maximum width". Value "No limit" at 0, else "%d dp" | 0–1600 (the model accepts up to 4000) | `maxWidthDp = 720` | Always |
| SET-RDR-NOVEL-09 | Segmented | "Colours" | Theme, Sepia, White, Gray, Black | `background = "theme"` | Always |
| SET-RDR-NOVEL-10 | Toggle | "Justify text" | on/off | `textAlignJustify = false` | Always |
| (preview) | Live preview card | Sample text "The wind carried the scent of rain as she turned the page." in the chosen font, size, line height and background colours | — | — | Always. It **does not show** paragraph spacing, width or justify |
| SET-RDR-NOVEL-11 | Toggle | "Continuous scroll". Hint: "The next chapter follows below the current one." | on/off | `continuousScroll = true` | Always |
| SET-RDR-NOVEL-12 | Toggle | "Progress bar" | on/off | `showProgressBar = true` | Always |
| SET-RDR-NOVEL-13 | Toggle | "Fullscreen when opening" | on/off | `fullscreenOnOpen = true` | Always |
| SET-RDR-NOVEL-14 | Toggle | "Keep the screen on" | on/off | `keepScreenOn = true` | Always |

### Dialogs / sheets
- **InstalledFontDialog** (desktop): title "Installed font", a search field "Family name" pre-filled with the current family, the list drawn in each font (current one in primary colour), empty state "No installed font matches.", buttons "Use" (takes the typed name if it resolves to a font) and "Cancel". Tapping a row picks it.
- **System file picker** (single file).

### UX notes for redesign
- The font chips are text-only. Render each chip label in its own font, as the desktop dialog already does.
- The preview sits after the colour and justify rows and skips paragraph spacing and justify. Put it at the top, keep it sticky, and show two paragraphs.
- "Colours" is a text segment with 5 options. Use background swatches.
- Typography and flow toggles run together with no sub-heading. Split them into "Typography" and "Reading flow".
- The novel and subtitle editors each have their own import and installed-font flow, with different copy ("Installed font…" vs "Choose an installed font…") and different matching rules.

---

## SET-RDR-TTS: Narrator (inside NovelReaderSettingsSection)

- **Source:** `NovelReaderSettingsSection.kt` `NarratorRows` ~:275, `NarratorStatusRow` ~:454, `LocalVoiceRows` ~:473. Options interface: `core/novel/src/commonMain/kotlin/app/nijiyome/novel/NarratorOptions.kt` (desktop implementation `core/novel/src/desktopMain/.../tts/DesktopNarrator.kt:85-94`; Android's narrator does not implement it, so it gets the default engines, no pitch, no status row and no local voice). Model: `TtsSettings` (`core/model/.../Novel.kt:125`). The key is in SecretStore `SecretKeys.ttsApiKey(engine)`.
- **Platform:** both, with the differences marked.
- **Layout:** group title "Narrator".

### Controls

| ID | Control | Label (EN) | Options / range | Default | Visibility / conditions |
|---|---|---|---|---|---|
| SET-RDR-TTS-01 | Segmented | "Engine" | Android: System voice, OpenAI, ElevenLabs, Local server. Desktop: System voice, **KittenTTS**, OpenAI, ElevenLabs, Local server | `engine = SYSTEM` | Always. Changing the engine resets the voice to the default |
| SET-RDR-TTS-02 | Info text | Android + System voice: "Uses the text-to-speech engine installed on the device; voices come from its settings." Desktop: a status line per engine: "Speaking with %s." / "No system voice found. Install speech-dispatcher (or espeak-ng) and come back." / "KittenTTS (local voice) is not downloaded yet." / "KittenTTS is downloaded but espeak-ng is missing: install it with your package manager." / "KittenTTS has no build for this system." / "%s did not start.", plus a small raw detail line | — | — | As described |
| SET-RDR-TTS-03 | Model row + Download / Remove (action), with a progress bar | "KittenTTS nano 0.8 (int8)" · "Model of about 28 MB plus ONNX Runtime (54 MB). Runs offline; English only." / "Downloading ONNX Runtime… x / y MB" / "Downloading the model… x / y MB" / "Downloaded and checked" · "Download" / "Remove". The error message is shown raw | — | not installed | **Desktop only**, when Engine = KittenTTS |
| SET-RDR-TTS-04 | Dropdown (button showing the current value) | "Voice" | "Engine default" + the voices the engine lists. Empty: "No voices listed (save the key or check the server)." | `voice = null` (Engine default) | Always. Disabled when no voices are listed |
| SET-RDR-TTS-05 | Text field + Save | "Voice id" + "Save" | free text | — | Engine ∈ {ElevenLabs, Local server} |
| SET-RDR-TTS-06 | Slider 0.5..2.0, step 0.1 | "Speed". Value "%s×" | 0.5–2.0 | `speed = 1.0` | Always |
| SET-RDR-TTS-07 | Slider 0.5..2.0, step 0.1 | "Pitch". Value "%s×" | 0.5–2.0 | `pitch = 1.0` | **Desktop only**, Engine = System voice (`hasPitch`) |
| SET-RDR-TTS-08 | Toggle | "Highlight the paragraph being read" | on/off | `highlight = true` | Always |
| SET-RDR-TTS-09 | Text field (URL keyboard) | "Server URL (OpenAI-compatible)", placeholder `http://127.0.0.1:8880/v1` (hardcoded) | free text | `endpoint = ""` | Engine = Local server |
| SET-RDR-TTS-10 | Text field + Save (saves 09 + 10) | "Model". Placeholder (hardcoded): `gpt-4o-mini-tts` (OpenAI) / `eleven_multilingual_v2` (ElevenLabs) / `kokoro` (Local server) | free text | `model = ""` | Engine ∈ {OpenAI, ElevenLabs, Local server} |
| SET-RDR-TTS-11 | Status + password field + Save + Remove (action) | "API key". Status: "Key saved" / "No key saved" / "Optional for local servers" / "Secure storage is not available". Buttons "Save" and "Remove" | — | no key | Engine ∈ {OpenAI, ElevenLabs, Local server}. "Remove" only while a key is saved |
| SET-RDR-TTS-12 | Button (action) + Stop | "Test voice" / "Playing…" + "Stop". Speaks "This is how the narrator sounds with the current settings." Errors are mapped: "Save the API key of this engine first.", "Choose a voice supported by this engine and model.", "Choose a speech model.", "Enter a valid http(s) server URL without credentials.", "The narration service answered HTTP %d. Check the key, voice and model.", "The server returned no audio. Check the voice and the endpoint.", "Could not reach the narration service.", "The system text-to-speech engine is not available.", "Download the KittenTTS model in Settings › Narrator first.", "KittenTTS needs espeak-ng installed on this computer (it turns the text into phonemes).", "Narration failed: %s" | — | — | Always |

### Dialogs / sheets
- The voice DropdownMenu (SET-RDR-TTS-04). No dialogs.

### UX notes for redesign
- Voice selection is a plain dropdown that can list many voices (system voices, ElevenLabs) with no search, language grouping or per-voice preview. Use a searchable picker with a play button per voice.
- Cloud engines show three or four config blocks (voice id, model, URL, key), each with its own Save button. Use one "engine setup" card with a clear ready/not-ready state, which the desktop status row already computes.
- "Test voice" sits at the very bottom, far from Voice, Speed and Pitch. Put it next to the voice picker.
- The Model field shows for ElevenLabs, but the `TtsSettings` doc says ElevenLabs ignores the model. Check this before keeping or removing the field.
- The engine segment has up to 5 text options on desktop. It will be cramped on narrow windows. Use cards with a "local / cloud / needs key" badge.
- Hardcoded non-localized literals: the URL and model placeholders; the KittenTTS model label (from code); raw download error messages.

---

### Counts (Part 4)

Each "setting" is a persisted value. Each "action" is a button that does something (save, test, clear, reset, import, remove, download) or opens a dialog. Per-item keymap rows are counted as one setting per editor.

| Surface | Settings | Actions | Dialogs / sheets | Notes |
|---|---|---|---|---|
| SET-PLY-EXTRA (SET-PLY-20..36) | 12 (countdown, stable volume, background, secondary lang, engine, mode, target, endpoint, model, region, API key, keymap) | 5 (save connection, remove key, test, clear cache, restore shortcuts) | 1 (key capture) | Keymap has 13 actions on Android, 20 on desktop |
| SET-PLY-SUBS (01..25) | 22 (font = 1, counting the bundled, imported and installed choices together) | 4 (apply preset, import fonts, delete imported font, reset all) | sheet + installed-font dialog + file picker | 3 colour pickers each with custom RGB sub-sliders |
| SET-PLY-SRC (01..02) | 2 | 0 | 0 | Desktop: only shown with Chromium available |
| SET-MET-DISC (01..05) | 9 (title lang, 18+, mature mode, 6 provider toggles) | 1 (clear recent searches) | 1 (confirm) | — |
| SET-RDR-MANGA (01..21) | 19 (incl. keymap) | 1 (reset shortcuts) | 1 (key capture) | 12 keymap actions |
| SET-RDR-NOVEL (01..14) | 12 (font incl. installed and imported, size, line height, paragraph, width, colours, justify, 4 flow toggles) | 2 (import, remove font) | installed-font dialog + file picker | — |
| SET-RDR-TTS (01..12) | 8 (engine, voice, speed, pitch, highlight, endpoint, model, API key) | 5 (KittenTTS download/remove, save voice id, save endpoint/model, save/remove key, test voice + stop) | voice dropdown | Pitch and KittenTTS: desktop only |
| **Total** | **84** | **18** | **7** | Desktop-only: 4 (installed subtitle font, novel Mono + installed font, KittenTTS, pitch). Android-only: 1 (background playback) |

---

# Part 5. Design system

## Design system snapshot

Repo: `Redesign-sparkles` worktree (branch `Kushro/Redesign-sparkles`, clean at 2c62582). Paths are relative to the repo root. `DS/` = `core/designsystem/src/commonMain/kotlin/app/nijiyome/designsystem/`.

Stack: Material 3 Expressive (`MaterialExpressiveTheme`), CMP material3 `1.12.0-alpha03` / androidx material3 `1.5.0-alpha22` (`gradle/libs.versions.toml:9,13`), materialKolor `5.0.1` (`libs.versions.toml:32`), material-icons-extended (`core/designsystem/build.gradle.kts:12`).

### 1. Themes & palettes

**Entry point:** `NijiyomeTheme(paletteId, amoled, contrast, dynamicColor, darkTheme)` (`DS/theme/NijiyomeTheme.kt:38-75`). It picks the color scheme like this (`:47-60`):
1. If `dynamicColor` is on and the platform has one, use the system scheme. Android 12+ uses `dynamicLight/DarkColorScheme` (Monet) (`androidMain/.../NijiyomeTheme.android.kt:11-16`). Desktop always returns `null` (`desktopMain/.../NijiyomeTheme.desktop.kt:7`). When dark and AMOLED are both on, `toAmoled(contrast)` runs on top.
2. Otherwise use the generated `NijiyomeTokens.palette(id, contrast)`, falling back to `DEFAULT_PALETTE`. Light gives `schemes.light`, AMOLED gives `schemes.amoled.toAmoled(contrast)`, and plain dark gives `schemes.dark`.

It also provides `LocalNijiyomeDarkTheme`, `LocalNijiyomeAmoled` and `LocalNijiyomeContrast` (`NijiyomeTheme.kt:26-32`).

**Curated palettes:** 20 entries in `tokens/tokens.json:5-146`. Each has a seed, an MCU scheme style, a base contrast and `amoledCapable`. All 20 are `amoledCapable: true`.

| id | label | seed | style | line |
|---|---|---|---|---|
| nijiyome-violet (**default**) | Nijiyome Violet | #7C4DFF | Vibrant | 6 |
| midnight | Midnight | #3D5AFE | TonalSpot | 13 |
| sakura | Sakura | #E86A92 | Vibrant | 20 |
| matcha | Matcha | #5E9E6E | TonalSpot | 27 |
| ocean | Ocean | #0091C2 | Vibrant | 34 |
| neon-tokyo | Neon Tokyo | #FF2E88 | Vibrant | 41 |
| blood-moon | Blood Moon | #C62828 | Fidelity | 48 |
| royal-gold | Royal Gold | #C9A227 | Fidelity | 55 |
| aurora | Aurora | #00BFA5 | Vibrant | 62 |
| mono | Mono | #808080 | Monochrome (base contrast 1) | 69 |
| cherry-blossom | Cherry Blossom | #EC4899 | Vibrant | 76 |
| deep-ocean | Deep Ocean | #1E5AA8 | TonalSpot | 83 |
| cyberpunk | Cyberpunk | #C6D300 | Vibrant | 90 |
| zen-garden | Zen Garden | #2E8B57 | TonalSpot | 97 |
| solar | Solar | #F59E0B | Vibrant | 104 |
| caffeine | Caffeine | #8B5A2B | Fidelity | 111 |
| dark-matter | Dark Matter | #5B21B6 | Vibrant | 118 |
| frostbite | Frostbite | #38BDF8 | TonalSpot | 125 |
| synthwave | Synthwave | #D946EF | Vibrant | 132 |
| vampire | Vampire | #8B0000 | Fidelity | 139 |

- The default is set in three places: `tokens.json:4`, `Tokens.kt:81` (`DEFAULT_PALETTE`) and `core/model/.../Settings.kt:117` (`DEFAULT_PALETTE_ID`).
- Supported styles are Expressive, TonalSpot, Vibrant, Fidelity and Monochrome (`tokens/lib/colors.mjs:73-79`). No palette uses Expressive.

**Modes:**
- `ThemeMode { SYSTEM, LIGHT, DARK }` defaults to `SYSTEM` (`core/model/.../Settings.kt:131,16`). It is resolved in `feature/shell/.../NijiyomeApp.kt:212-216`.
- AMOLED is a separate boolean, default `false` (`Settings.kt:10`). It only applies when the mode is not LIGHT (`feature/settings/.../SettingsSections.kt:157-164`, which hardcodes a Spanish hint at `:162`).
- The AMOLED scheme is built in two layers:
  - **Generator** (`tokens/lib/colors.mjs:92-107`): starts from the dark scheme. Background, surface, surfaceDim and surfaceContainerLowest become #000. The other containers are re-picked from neutral tones T5/T8/T12/T16/T20.
  - **Runtime `ColorScheme.toAmoled()`** (`NijiyomeTheme.kt:144-163`): sets background, surface, Dim, Lowest, Low and Container to `Color.Black`. It fixes High to #141418, Highest to #1E1E24, surfaceVariant to #1A1A20 and Bright to #26262E. It sets outlineVariant to white at alpha 0.14 / 0.24 / 0.38 for Standard / Medium / High.
  - The runtime layer overrides most of the generated AMOLED containers (see §8).
- The player always forces `amoled = true, darkTheme = true` (`feature/player/.../PlayerBody.kt:89-95`).

**Dynamic color (Monet):** `AppSettings.dynamicColor` defaults to **`true`** (`Settings.kt:12`).
- The switch only appears where `dynamicColorSupported` is true: Android SDK ≥ 31 (`feature/settings/src/androidMain/.../SettingsPlatform.android.kt:60`). Desktop is `false` (`SettingsPlatform.desktop.kt:23`).
- While dynamic color is on, no palette swatch shows as selected (`SettingsSections.kt:137`) and the contrast control is disabled (`:165-175`, Spanish hint hardcoded at `:168`).
- The desktop Link Monitor dialog passes `dynamicColor = false` (`desktopApp/.../Main.kt:398-404`).

**Contrast:**
- The user setting is `ContrastPref { STANDARD, MEDIUM, HIGH }`, default STANDARD (`Settings.kt:5,11`).
- It maps to the generated `ContrastLevel { Standard, Medium, High }` (`Tokens.kt:28`) in `core/ui/.../state/SettingsMapping.kt:9-13`. `desktopApp/Main.kt:393-397` duplicates this mapping.
- Values used by the generator: standard is the palette's base contrast, medium is `max(0.5, base)`, high is `1.0` (`colors.mjs:81-87`).
- Every palette is precomputed in 3 contrast levels × 3 modes (light / dark / amoled), giving 180 `private fun scheme…()` functions in `Tokens.kt`.

**Per-content seed theme:** `NijiyomeSeedTheme(seed, dark, amoled, contrast)` (`NijiyomeTheme.kt:82-122`). It uses materialKolor `DynamicMaterialExpressiveTheme` with `PaletteStyle.TonalSpot`. Contrast maps to 0 / 0.5 / 1.0. It animates only when reduce-motion is off and re-applies `toAmoled` in AMOLED.
- The only call site is Details, seeded from the AniList cover color: `feature/details/.../DetailsScreen.kt:290`, using `parseHexColor` (`NijiyomeTheme.kt:129-137`).

**Accent options:** there are no accent or seed choices beyond the 20 curated seeds and dynamic color. Users cannot pick a custom seed. The palette picker is a LazyRow of 48dp circular swatches showing the raw seed color (`SettingsSections.kt:127-142, 279-296`). The check tint is chosen by luminance with a hardcoded `Color(0xFF0A0A0D)` (`:280`).

**"Look" presets** (layout and style, not color): `core/model/.../Look.kt`. Defaults come from `LookPrefs` (`:225-253`).

| Setting | Options (default first) | Look.kt line |
|---|---|---|
| LookPreset | NIJIYOME, CINEMA, DENSE, CALM | 203 |
| HomeLayout | SPOTLIGHT, CAROUSEL, ROWS | 23 |
| CardStyle | DETAILED, CLASSIC, MINIMAL | 39 |
| CardSize | MEDIUM, SMALL, LARGE, XL | 59 |
| RailStyle | LABELED, SLIM | 74 |
| Density | COMFORTABLE, COMPACT | 87 |
| Motion | EXPRESSIVE, SUBTLE, OFF | 97 |
| EpisodeView | LIST, CARDS, COMPACT | 111 |
| RelationsView | GRAPH, LIST | 127 |
| CalendarView | WEEK, AGENDA, MONTH, DAY | 140 |
| PageStyle | STANDARD, EDITORIAL | 162 |
| HeaderLayout | FACTS_NEXT_UP, LINE, FACTS, NEXT_UP | 178 |

- Other `LookPrefs` fields: `glass = false` (desktop only, not offered on Android), `heroAutoplay = true`, `heroIntervalSecs = 9` (range 4..30), `showGreeting = false`.
- Preset bundles are defined in `Look.kt:274-305`.
- The UI is `feature/settings/.../AppearanceLook.kt:82-192` (`LookGroup`, preset cards, a 2×2 grid of HeaderLayout thumbnails, `LookSegmented` rows).
- A live preview card, `AppearanceLivePreview`, is at `SettingsSections.kt:192-270`.

### 2. Typography

- **Bundled variable fonts** (`[wght]`, SIL OFL 1.1):
  - Space Grotesk for display, headline and title.
  - Outfit for body and label.
  - JetBrains Mono for figures and mono.
  - Declared in `DS/theme/Fonts.kt:8-39` and `tokens.json:148-152`.
- **Files:** Android `core/designsystem/src/androidMain/res/font/{space_grotesk,outfit,jetbrains_mono}.ttf`; desktop `src/desktopMain/resources/font/*.ttf`. Licenses are in `androidMain/assets/licenses/OFL-*.txt`.
- **Loading:** Android uses `Font(resId, weight, variationSettings)` (`Fonts.android.kt:15-30`). Desktop uses `Font(resource = "font/<name>.ttf")` (`Fonts.desktop.kt:9-19`). Weights are pinned to W400/500/600/700 (`Fonts.kt:28`).
- **Type scale:** the full M3 roles, 15 roles plus 15 `*Emphasized` (`tokens.json:153-277`, emitted by `tokens/lib/emit-kotlin.mjs:24-36,160-163`). Font assignment is display for display, headline and title; text for body and label.
  - display L/M/S: 57/64, 45/52, 36/44, weight 400 (emphasized 600)
  - headline L/M/S: 32/40, 28/36, 24/32, weight 400 (emphasized 600)
  - title L: 22/28, weight 400; title M/S: 16/24, 14/20, weight 500 (emphasized 700)
  - body L/M/S: 16/24, 14/20, 12/16, weight 400 (emphasized 500)
  - label L/M/S: 14/20, 12/16, 11/16, weight 500 (emphasized 600)
  - Tracking follows M3 defaults.
- **TV scale:** `tvScale` is 1.15, giving `TV_TYPE_SCALE` (`tokens.json:154`). It is **never used**: `typography()` is always called with scale 1 (`NijiyomeTheme.kt:61`).
- **Usage across features and core:** bodySmall 237, bodyMedium 134, titleSmall 77, labelLarge 76, titleMedium 70, labelMedium 69, labelSmall 59, bodyLarge 34, titleLarge 31, headlineSmall 13, headlineMedium 9, display* 4.
  - `*Emphasized` styles: **0 uses**.
  - Mono is applied ad hoc: `FontFamily.Monospace` in `feature/details/.../DetailsSections.kt:1110` and `feature/extensions/.../ExtensionsScreen.kt:667`, and `NijiyomeFonts.jetBrainsMono` in `feature/link/.../LinkedDeviceScreen.kt:333,704`.
  - There is no named mono or numeric text style token.

### 3. Shapes, spacing, elevation

- **Shape radii** (`tokens.json:278-289`): none 0, extraSmall 4, small 8, medium 12, large 16, largeIncreased 20, extraLarge 28, extraLargeIncreased 32, extraExtraLarge 48, full 9999.
  - These are emitted as M3 `Shapes` (`emit-kotlin.mjs:129-138`) and as `object ShapeRadius` (`Tokens.kt:66`).
  - Usage: `MaterialTheme.shapes.large` 48, `.medium` 39, `.extraLarge` 12, `.small` 10, `.extraSmall` 1. `CircleShape` appears 97 times. `ShapeRadius.*` has **0 uses**.
  - There are 50+ raw `RoundedCornerShape(…)` calls: player 25, desktopApp 8, settings 6, details 6, core/ui 4.
  - Material shape morphs: `MaterialShapes.Cookie9Sided` and `Sunny` in `LinkStatusAvatar` (`DS/components/StatusBadge.kt:41-42`).
- **Spacing** (`tokens.json:290-306`): unit 4, scale 0, 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64. Emitted as `NijiyomeTokens.spacing.s0…s64` (`emit-kotlin.mjs:85-89,150-154`). **0 uses**: every screen uses literal `.dp`.
- **Elevation** (`tokens.json:343-350`): 0, 1, 3, 6, 8, 12, as `NijiyomeTokens.elevation`. **0 uses**. Components hardcode their elevations:
  - `NijiyomeFloatingToolbar`: tonal 3dp, shadow 6dp (`ButtonGroups.kt:67-68`)
  - Side sheet: tonal 1dp (`AdaptiveSheet.kt:82`)
  - Chevron: shadow 2dp (`HorizontalFadingEdge.kt:154`)
- **Density** (`tokens.json:337-342`): phone 0, tablet 0, desktop -1, tv 1, as `NijiyomeTokens.density`. **0 uses**. The density actually used is the user setting `Look.Density` COMFORTABLE/COMPACT, applied via `rowSpacing()` (8 vs 12dp) and `cardWidth()` in `core/ui/.../look/LookUi.kt:45-51` and `CardSizing.kt`.

### 4. Motion / animation

- **Theme motion:** `MotionScheme.expressive()`, hardcoded in `NijiyomeTheme.kt:69` and `:101`.
  - The generated `NijiyomeTokens.motion` exists but has **0 uses**. It defines spatial springs (damping 0.9; stiffness fast 1400, default 700, slow 300) and effects springs (damping 1; stiffness 3800 / 1600 / 800). Sources: `tokens.json:307-336`, `emit-kotlin.mjs:140-148`.
  - Only 2 call sites read `MaterialTheme.motionScheme`: the HeroCarousel dots (`HeroCarousel.kt:111,116`).
- **Reduce motion:** `LocalReduceMotion` (`DS/theme/Motion.kt:14`). It is true when `rememberReduceMotion(setting)` returns true, which happens when the setting is on OR the system has animations disabled (`Motion.kt:18`).
  - Android watches `Settings.Global.ANIMATOR_DURATION_SCALE == 0` live through a ContentObserver (`androidMain/.../Motion.android.kt:17-36`).
  - Desktop always returns `false` (`desktopMain/.../Motion.desktop.kt:7`) and never reads an OS reduced-motion preference.
  - The app root computes `settings.reduceMotion || look.motion == OFF` (`NijiyomeApp.kt:227-230`). The settings VM keeps both values in sync (`feature/settings/.../SettingsViewModel.kt:160-173`).
  - 11 sites read `LocalReduceMotion`, including Skeleton, StatusBadge, WavyProgress, HeroCarousel, NijiyomeSeedTheme, LookUi, MetadataFallbackUi, HomeScreen and NijiyomeApp.
- **Look motion levels** (`core/ui/.../look/LookUi.kt:34-71`):
  - `lookMotion()` is OFF when reduce-motion is on.
  - `spatialMotion()` is true only for EXPRESSIVE.
  - `lookFadeSpec(180ms)` is a `tween`, or `snap` when OFF.
  - `Modifier.pressScale(0.96f)` uses `spring(DampingRatioMediumBouncy, StiffnessMediumLow)` and only runs in EXPRESSIVE.
  - SUBTLE is consulted only through `spatialMotion()` and in `feature/home/.../HomeSpotlight.kt:155,209`.
- **Hardcoded durations** that are not tokens:
  - Skeleton pulse `tween(900)` reversing between alpha 0.45 and 0.9 (`Skeleton.kt:30-36`)
  - LinkStatusAvatar crossfade `tween(200)` (`StatusBadge.kt:46`)
  - MetadataFallbackUi `tween(2400)` and `tween(1800)` linear (`core/ui/.../MetadataFallbackUi.kt:108,213`)
  - Spotlight progress uses a linear tween (`HomeSpotlight.kt:153`)
  - HeroCarousel autoplay default 6000ms, minimum 1000 (`HeroCarousel.kt:63,77`); Look sets 9s
  - Skeleton show delay 150ms (`Skeleton.kt:49`)
  - Overall there are only 9 `animate*AsState` call sites in features and core.

### 5. Iconography

- **Icon set:** Material Icons Extended, exported as `api(libs.cmp.material.icons.extended)` (`core/designsystem/build.gradle.kts:12`).
  - Usage counts by style: `Icons.Outlined` 493, `Icons.Rounded` 166, `Icons.Filled` 75, `AutoMirrored.Filled` 32, `AutoMirrored.Outlined` 25, `AutoMirrored.Rounded` 17. Outlined, Rounded and Filled are mixed across screens.
  - There are no custom `ImageVector.Builder` icons and no `painterResource` icons.
- **App icon:**
  - Android adaptive icon at `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, with background `@color/icon_background` = `#F3E9D2` (`app/src/main/res/values/colors.xml:4`), foreground `drawable/ic_launcher_foreground.xml` and a **monochrome** layer `drawable/ic_launcher_monochrome.xml` (for themed icons).
  - No legacy PNG mipmaps and no `roundIcon` (`AndroidManifest.xml:29`).
  - Desktop and packaging sources are in `packaging/icons/`: `nijiyome.svg`, `-foreground.svg`, `-mono.svg`, `.ico`, and PNGs at 16/32/48/64/128/256/512. `desktopApp/build.gradle.kts:177,183` uses the 512 PNG and the `.ico`. The runtime copy is `desktopApp/src/main/resources/icons/nijiyome.{ico,png}`.

### 6. Reusable components

`core/designsystem` (`DS/components/`):
- **SectionCard**`(modifier, title?, content)`: a `shapes.large` surface on `surfaceContainer` with 16dp padding, a primary `titleMedium` heading, and a 1dp outlineVariant border in AMOLED. **ListGroupHeader**`(text)` is a `titleLarge` heading. (`SectionCard.kt:19-54`)
- **SkeletonBox**`(modifier, shape = shapes.medium)`: `surfaceContainerHighest` with a pulse, or a static 0.65 alpha under reduce-motion. `rememberAfterDelay(150)` delays it. (`Skeleton.kt:25-56`)
- **AppSnackbar**: an interface plus `LocalAppSnackbar`, `AppSnackbarController` (deduplicates the visible message; Long duration when there is an action, else Short), `rememberAppSnackbarController()`, and `AppSnackbarHost` (the default M3 `Snackbar`). There is one host, in the shell. (`AppSnackbar.kt:22-68`)
- **SingleChoiceButtonGroup**`(options, selected, label, onSelected)`: an M3E `ButtonGroup` with toggleable items. **NijiyomeFloatingToolbar**`(expanded, content)`: a hand-made `CircleShape` surface that replaces `HorizontalFloatingToolbar` because of an NPE bug in alpha15. (`ButtonGroups.kt:23-77`)
- **ModalTracker / LocalModalTracker / TrackModal() / Modifier.focusOnOpen()**: on desktop, counts open modals so global hotkeys are suppressed and dialog fields get focus. Android has no tracker. (`ModalTracking.kt:25-77`)
- **TrackedAlertDialogOverride**(tracker): desktop `BasicAlertDialogOverride` that counts every M3 AlertDialog. (`desktopMain/.../TrackedAlertDialogOverride.kt:16-25`)
- **LinkStatusAvatar**`(connected, icon, size = 56.dp)`: a Cookie9Sided shape when disconnected and Sunny when connected, crossfading. **InfoChip**`(text, icon?)`: a `secondaryContainer` pill. (`StatusBadge.kt:34-84`)
- **WavyProgress**`(progress?, circular = false, thin = false)`: M3E Linear or Circular WavyProgressIndicator, determinate or indeterminate (when `progress` is null).
  - Thin: 4dp stroke. Thick: 8dp linear on a 14dp container; 6dp circular at 56dp (40dp when thin).
  - Reduce motion makes the line flat with no wave. (`WavyProgress.kt:30-85`)
- **Pill**`(text, tone = PillTone.NEUTRAL, icon?, plain = false)`. Tones are NEUTRAL, OK, WARN, ERR and PRIMARY, mapped to surfaceContainerHighest, primaryContainer, tertiaryContainer, errorContainer and primary.
  - Helpers: `containerColor()`, `contentColor()`, `accentColor()`.
  - There are **no success or warning color roles**: OK reuses primary and WARN reuses tertiary. (`Pill.kt:25-94`)
- **AdaptiveSheet**`(onDismissRequest, content)`: `ModalBottomSheet` below 600dp (`SIDE_SHEET_MIN_WIDTH_DP`).
  - At 600dp and up it is a custom side sheet: a `Dialog` with a 0.56 scrim, a 400dp surface and 28dp inner corners.
  - Bottom sheet scrim is 0.64. AMOLED uses `surfaceContainerHigh` plus a border; otherwise `surfaceContainerLow`.
  - Keeps immersive mode on Android.
  - **AdaptiveSheetScrollColumn** scrolls only in bottom-sheet mode.
  - Sources: `AdaptiveSheet.kt:33-107`; platform parts in `AdaptiveSheet.platform.kt`, `.android.kt` and `.desktop.kt`.
- **Segmented**`(options: List<SegmentedOption>, selected, onSelect, small, iconOnly, fillWidth)`: `SingleChoiceSegmentedButtonRow`.
  - `SegmentedOption(value, label, enabled, icon?, description?)`.
  - Scrolls horizontally unless `fillWidth` is set. `iconOnly` keeps a 48dp minimum touch target.
  - Small padding is 10×4. (`Segmented.kt:27-109`)
- **State views:** **LoadingState** (M3E `LoadingIndicator`, 48dp); **EmptyState**`(icon, title, message?, actionLabel?, onAction?)` (56dp primary icon); **ErrorState**`(message, retryLabel, onRetry, title?, compact)` (compact is an inline row, the full variant is centered). (`StateViews.kt:30-115`)
- **HeroCarousel**`<T>(items, key, autoplay = true, autoplayMillis = 6000, itemContent)`: `HorizontalMultiBrowseCarousel`.
  - Item width is maxWidth−48, between 160 and 720. Height is 16:10, at most 420dp.
  - Items are clipped with `maskClip(shapes.extraLarge)`.
  - Page dots grow from 8dp to 20dp, inside 24dp touch targets.
  - Autoplay pauses while dragging or under reduce-motion. (`HeroCarousel.kt:57-133`)
- **Modifier.horizontalFadingEdge**`(ScrollState | LazyListState, edgeWidth = 28.dp)`: a DstIn alpha mask, applied only on edges that can still scroll. **HorizontalScrollStrip**`(scrollState, showChevrons = false, stepPx = 260)`: adds 28dp chevron buttons; their content descriptions are **hardcoded Spanish** (`:159`). (`HorizontalFadingEdge.kt:43-164`)

`core/ui` (shared UI built on the design system):
- **MediaCard**: two overloads. One takes raw fields `(title, coverUrl, subtitle?, progress?…)`; the other takes `(media, titleLanguage = LocalTitleLanguage, style = LocalLook.cardStyle)` and supports the CLASSIC, DETAILED and MINIMAL styles. `mediaSubtitle()` lives here too. (`core/ui/.../media/MediaCard.kt:43,133,156`)
- **MediaWideCard**`(media, title, size: CardSize, width)`: horizontal card for the LARGE and XL sizes. Uses a hardcoded star color `0xFFFFC547` (`MediaWideCard.kt:53,169`). Also holds `agoLabel` and `episodeAgoLine`.
- **MediaCover**`(url, contentDescription?, shape = shapes.medium, aspectRatio = COVER_ASPECT_RATIO)` (`MediaCover.kt:24`).
- **EpisodeRow**`(number, title?, imageUrl?, airDate?…)` (`EpisodeRow.kt:29`).
- **MediaFacts**: `mediaStatusKind`, `MediaStatusKind.dotColor()` (hardcoded hues, see §8), `label()`, `mediaFormatLabel`, `classicFactsLine`, `detailedFactsLine` (`MediaFacts.kt:17-78`).
- **Look helpers**: `LocalLook`, `lookMotion()`, `spatialMotion()`, `lookFadeSpec()`, `rowSpacing()`, `cardWidth()`, `Modifier.pressScale()` (`look/LookUi.kt`). `CardSizing` sets poster widths: SMALL 96, MEDIUM 120, compact ×0.87, row padding 16, gap 12, wide window from 600dp (`look/CardSizing.kt:13-56`).
- **State**: `LoadStateContent<T>(state, loading = { LoadingState() }, …)` (`state/LoadStateContent.kt:14`); `MetadataFallbackBanner(state)` and `AppErrorState(cause, compact, fallbackProvider?, title?)` (`state/MetadataFallbackUi.kt:67,157`); `WaveBPlaceholder(title, onBack?)` (`state/WaveBPlaceholder.kt:30`); `Insets.kt`; `Connectivity`; `ErrorMapping`.
- **Platform expect/actuals** (not visual): BackHandler, FilePicker, FolderPicker, KeepScreenOn, SystemBars, WindowSize, ScreenOrientation, ScreenBrightness, ShareText, OpenUrl and others (`core/ui/.../platform/`).

### 7. Token pipeline

- **Source:** `tokens/tokens.json`, validated by `tokens/tokens.schema.json`. The required top-level groups are `version, defaultPalette, palettes, allowContrastFailures, fonts, typography, shape, spacing, motion, density, elevation` (`schema:8`).
  - `allowContrastFailures` is an empty list (`tokens.json:147`).
  - Palettes may have `overrides.{light,dark,amoled}`; dark overrides also apply to amoled (`colors.mjs:125-128`). None are used.
- **Generator:** `node tokens/generate.mjs`, plain Node with no npm dependencies; `--check` reports drift (`generate.mjs:1-49`).
  - `lib/colors.mjs` expands each seed with the **vendored** material-color-utilities (`tokens/vendor/material-color-utilities/`, Apache-2.0) into the 48 ColorScheme roles, in the exact order of the M3 1.5 constructor (`colors.mjs:55-68`). It covers modes light/dark/amoled × contrast standard/medium/high (`:70-71,133-143`).
  - `lib/emit-kotlin.mjs` writes `DS/generated/Tokens.kt` (10,048 lines, `// DO NOT EDIT BY HAND`).
- **Tokens.kt contents:**
  - Types: `enum ContrastLevel`, `PaletteSchemes(light, dark, amoled)`, `PaletteInfo(id, label, seed, style, amoledCapable)`, `NijiyomeFontFamilies(display, text, mono)`, `Spacing`, `DensitySteps`, `object ShapeRadius`.
  - `object NijiyomeTokens` holds `VERSION`, `DEFAULT_PALETTE`, `TV_TYPE_SCALE`, `fontNames`, `paletteInfo`, `palettes` (lazy), `palette(id, level)`, `shapes`, `motion`, `spacing`, `elevation`, `density` and `typography(fonts, scale)`.
  - It also contains 180 private scheme functions.
- **Tests** (`tokens/test/tokens.test.mjs`): schema validity, at least 16 palettes, all 48 roles present, AMOLED surfaces black, regeneration produces no diff and is deterministic, API contract, **WCAG 4.5:1 on 9 on/container pairs** for every palette × mode × contrast (`lib/contrast.mjs:162-175`), and seed hue kept in primary for non-mono palettes (`test:143`).
  - CI runs them when `tokens/**` changes (`.github/workflows/ci.yml:68,82`).

### 8. UX/design notes for the redesign

- **Most generated tokens are unused.** Spacing, ShapeRadius, elevation, density, motion and TV_TYPE_SCALE all have 0 references outside `Tokens.kt`. Code outside the design system uses only `palette()`, `paletteInfo`, `shapes`, `typography` and `ContrastLevel`. This is the biggest gap: spacing and elevation exist on paper only.
- **Raw `.dp` literals in `feature/*`:** about 1,850 in main sources.
  - By module: player 396, settings 252, details 245, home 149, manga 132, torrents 122, extensions 104, novel 95, search 74, social 68, downloads 62, link 58, library 50, you 25, shell 21.
  - Also desktopApp 80, core/ui 33, core/designsystem 58.
  - Most common values: 8dp (556), 16 (399), 12 (241), 4 (223), 6 (138), 18 (92), 2 (89), 24 (83), 20 (55), 10 (51).
  - Values off the 4dp grid (6, 18, 10, 5, 3, 14) are common, and `spacing.scale` does not contain them.
- **Hardcoded colors outside tokens** (`Color(0x…)`):
  - Player: `AMBER_SEGMENT #F5B841`, `CYAN_PC_LINK #66D9EF`, `GLASS_BG #0E0E15 @0.62`, `SCRUB_BUBBLE_BG #12111A @0.94` (`feature/player/.../TouchPlayerUi.kt:126-131`); a subtitle-preview gradient (`SubtitleStyleEditor.kt:160`); an overload color `#FFD180` (`PhoneFrameGen.android.kt:68`). The player also has 41 `Color.Black/White` uses.
  - Status dot hues: Airing #4C9BFF, Finished #FF9B42, Upcoming #B089FF, Cancelled #E5484D, Hiatus #9A9A9A (`core/ui/.../MediaFacts.kt:26-32`). They do not adapt to the palette or contrast setting.
  - Star colors: #FFC547 (`MediaWideCard.kt:169`) and a separate star tint in `feature/details/.../DetailsEpisodes.kt:112`.
  - Reader themes: novel sepia, black, white and gray (`feature/novel/.../NovelUi.kt:165-168`) and manga gray (`MangaReaderScreen.kt:223`).
  - Swatch check tint (`SettingsSections.kt:280`) and the AMOLED neutrals in `toAmoled` (`NijiyomeTheme.kt:157-160`).
  - **There are no semantic success, warning or info roles** (stated at `Pill.kt:26`). Status colors are either borrowed from primary/tertiary or hardcoded.
- **AMOLED is defined twice and the two disagree.** The generator makes neutral-tinted containers (T5–T20) and checks their contrast. The runtime `toAmoled()` replaces them with fixed untinted grays and pure black for Low and Container. The tested values are not the ones that ship, and on AMOLED `surfaceContainer` equals `surface`. That is why SectionCard, the sheet and the toolbar need a manual 1dp border in AMOLED (`SectionCard.kt:30`, `AdaptiveSheet.kt:81`, `ButtonGroups.kt:66`). Components also branch on `LocalNijiyomeAmoled` to pick the container: `AdaptiveSheet.kt:49`, `ButtonGroups.kt:64`.
- **Motion tokens are ignored.** The theme uses `MotionScheme.expressive()`. Components use ad-hoc `tween(200/900/1800/2400)` and the Compose spring presets. SUBTLE barely differs from EXPRESSIVE outside press-scale and the Spotlight pager. Desktop never reads an OS reduced-motion preference.
- **Dynamic color is on by default** (`Settings.kt:12`). On Android 12+ new users see Monet, not Nijiyome Violet, and the contrast setting is disabled. The curated palettes and contrast work are hidden by default on Android.
- **Shapes:** 50+ raw `RoundedCornerShape(N.dp)` calls, 97 `CircleShape` uses, and the 28dp sheet corner hardcoded instead of `shapes.extraLarge`. The M3E shapes `largeIncreased`, `extraLargeIncreased` and `extraExtraLarge` are generated but almost never used.
- **Typography:** the 15 emphasized styles are unused, `TV_TYPE_SCALE` is unused, there is no mono or numeric style token, and mono is applied three different ways (see §2). Hierarchy relies heavily on bodySmall (237 uses).
- **Icons** mix Outlined (493), Rounded (166) and Filled (75), with no stated rule. Selected and unselected pairs are not standardized.
- **i18n inside DS components:** Spanish strings are hardcoded in `HorizontalFadingEdge.kt:159` and in `SettingsSections.kt:162,168,212-215` (preview labels "Claro/Oscuro/Sistema/OLED #000").
- **Duplicated mappings:** the default palette id is defined three times (§1), and the ContrastPref→ContrastLevel mapping is written in both `SettingsMapping.kt` and `desktopApp/Main.kt:393-397`.
- **Workarounds tied to alpha APIs:** the hand-made floating toolbar (`ButtonGroups.kt:47-52`), the custom side sheet because `ModalSideSheet` is missing (`AdaptiveSheet.kt:35-38`), and a held material3 version (`libs.versions.toml:13`). A redesign should re-check these against newer M3E releases.
- **Gaps:**
  - No custom accent or seed picker.
  - No per-palette AMOLED preview.
  - `glass` exists in the model but is not offered on Android.
  - No density tokens tied to form factor at runtime.
  - No breakpoint tokens: 600dp appears as `SIDE_SHEET_MIN_WIDTH_DP` and `CardSizing.WIDE_WINDOW_DP`, and 840/480dp literals appear 12 and 11 times in features.
  - `docs/redesign/` is present but empty in this worktree, so the `01 §x` references in KDoc point to docs that are not here.

---

# Cross-cutting

## X-01 Where each area is reached (entry-point matrix)
| Surface | Desktop / wide (≥840 dp) | Phone (compact) | Shortcuts / other |
|---|---|---|---|
| Settings | Rail destination; title-bar gear → modal | You › Settings | Ctrl+P / Ctrl+, (modal); Ctrl+K palette (actions + 39 setting entries); deep link `Route.SettingsSection` from Library, the update snackbar, Link Monitor |
| Torrents | Rail (active-downloads badge) | Library › Torrents; You › Torrents | Ctrl+K "Ir a Torrents"; tray; Link Monitor; notifications; `magnet:` / `.torrent` intents; Details / Player chips |
| Debrid | Settings › Torrents › Debrid → `Torrents(DEBRID)` | same | Library and empty-Torrents "Configure debrid" |
| Downloads | Rail | You › Downloads | Ctrl+K; download notifications (tab by id) |
| Extensions | Rail | You › Extensions | Settings › Player › Extensions; Ctrl+K |
| Linked device | Rail ("Linked device") | You › Linked device | Settings › Link › Linked device; handoff notification |
| Extension servers | Extensions › "Extension servers" | Linked device › "Set up" | `nijiyome://extensionserver/connect` deep link (LNK-17) |
| Link Monitor | Title-bar Link chip; tray | — | — |

## X-02 Platform divergence that must survive the redesign
- **Desktop only:**
  - Network section (proxy, FlareSolverr, media proxy)
  - The whole Link **server** UI (SET-LNKSRV)
  - The Link Monitor
  - Tray, autostart, start minimized, Discord, native title bar, magnet handler (SET-DAT-05)
  - Data folder › Open folder
  - Typed RESET dialog with "Keep downloaded torrents"
  - Metadata cache "Clear"
  - Export CSV and extra import formats (MAL XML…)
  - The external mpv rows (path, enhanced mpv-anime-build, uosc)
  - lsfg-vk frame generation
  - VAAPI decoder status
  - NVScaler preset
  - Hidden-browser path + Test
  - `.kai` drag-and-drop and path install
  - Desktop onboarding steps (Downloads, This PC)
  - Torrent list view, sort, file preview (Part 2)
- **Android only:**
  - Media3 engine
  - External player app (mpv-android / VLC)
  - Verify certificates (libmpv)
  - Player chrome style (Cine / Minimalista)
  - Dynamic color (12+)
  - Phone LSFG frame generation with Lossless.dll import, profile, refresh-rate match, untested GPUs, stats
  - Notifications permission step
  - Link **client** settings (default processing location, history sync); the client is common code but in practice it runs on Android
  - Push logs to PC
  - Background playback (Part 4)
- **Wide-layout only:** Side menu style (SET-APP-19).

## X-03 State badges and status vocabulary in use (keep a consistent system)
- **`Pill` tones** OK / WARN / ERR / NEUTRAL are the only status primitive:
  - Extension compat: Full / Partial / Unsupported
  - "N pending" / "Up to date" (lists)
  - "First sync pending", "Session expired"
  - Server status: Online / Offline / Access revoked / Checking / Not checked yet / Disabled
  - Sustain: Sustained / May stutter / Not measured
  - Telemetry "None"
  - The "Ctrl+P" badge
- **Experimental / untested:**
  - "Experimental on this GPU" (phone FG)
  - "Allow on untested GPUs" switch
  - ArtCNN preset hidden (needs Vulkan)
  - "May drop frames on this phone" (Link profiles)
- **Keyring / secret store:**
  - "secrets sealed in a file (no keyring)" hint
  - **"The saved secrets could not be opened; link your accounts again"** error notice (SET-DAT-05)
  - Link identity unreadable error + Regenerate (SET-LNKSRV-01/03)
  - Proxy password "saved; type to replace"
  - Debrid keys in the Keystore / keyring (TOR-21)
- **Link states:** Unpaired / Pairing / Paired (connected / out of reach) / Failed; history mode notices; server Starting / Listening / Off / port-in-use / no address / identity.
- **There are no semantic success or warning color roles** (see Design system §8), so these badges borrow primary, tertiary or error.

## X-04 Save-model inconsistency
- Most rows auto-save on change (snackbar "Settings saved").
- **Explicit Save** is needed for: the proxy block (SET-NET-05), FlareSolverr, Link server name, library folder and ports, the viewer password, extension TEXT/SECRET preferences (on "Save" of the dialog), path fields (save on Done or blur), the subtitle-translation and TTS connection blocks (Part 4), and several torrent numeric fields (Part 2).
- The redesign should show a dirty state, or make everything auto-save consistently.

## X-05 Confirmation inconsistency (destructive actions)
- **Confirmed:** remove extension, repo, addon; disable all extensions; unlink account; revoke a Link device; regenerate identity; clear cache; clear history; reset app; forget PC; remove extension server.
- **Not confirmed:** revoke an *extension-server* device (SET-LNKSRV-21), logs **Clear** (SET-ABT-04), onboarding account unlink, enable all extensions, and in Part 2 the single episode or chapter delete, Downloads "Clear history" and debrid "Unlink".

## X-06 Raw text entry where a picker belongs
- Ports: Link 47321, extension server 8099, proxy, torrents.
- Library folder (SET-LNKSRV-12).
- mpv path, lsfg shader path, browser path.
- Proxy host.
- Stream URL (LNK-08).
- Torrent HH:MM and KiB/s fields (Part 2).
- ISO language codes (ES/EN/PT, JA/ES/EN shown as codes).

## X-07 Hardcoded Spanish (not localized)
- SET-00 hub summaries; the whole `SettingsCatalog` (search + Ctrl+K); the command palette.
- "Buscar ajuste…", "Sin coincidencias", "Resultados para…", "Ocultar aviso", "Cerrar configuración".
- AMOLED/contrast disabled hints; "PREVIA" preview text.
- "Estilo del reproductor" (Cine/Minimalista).
- "Recordatorios de emisión" / "Notificaciones de AniList".
- Link server "Sesiones activas", "Caché RAM…", "Detener", "Caché de reproducción en RAM".
- "Barra de título nativa del sistema".
- Logs sheet chips, "Enviar a la PC", the push snackbar and the filter placeholder.
- Link Monitor (inline ES/EN `if (spanish)` switch, outside resources).
- Torrents "Limpiar búsqueda" (Part 2).
- `HorizontalFadingEdge` (Design system).

## X-08 Grouping problems worth fixing in the redesign
- **Desktop OS integration lives under "Data"**: tray, autostart, Discord, title bar and magnet handler need a "System" section.
- **Notifications are split:** airing and AniList under Metadata; download notifications in Part 2; the permission only in onboarding.
- **The Player section mixes** engine, quality defaults, source picking, languages, subtitle style, translation, shortcuts, embed browser and frame generation (about 30 rows on Android) with no sub-headers.
- **Torrents and Downloads settings are split** between Settings and their own screens (Part 2 notes four places that list torrents).
- **Link has two servers on desktop** (Link + extension server), each with its own pairing, port and device list, all in one panel. Phone side: Linked device vs Extension servers vs Settings › Link.
- **Duplicated controls:** the Link RAM cache (SET-LNKSRV-14 and LNK-18); the Debrid switches (TOR-21 and TOR-26); the Link logs reached from About.
- **Settings search lands on the section, not the control** (SET-04).

## X-09 Docs and anchors
- `docs/redesign/` was empty before this file. KDoc references such as "docs/redesign/12 §3.13" or "16 §3.4" point to documents that are not in this worktree.

---

# Counts

These are hand tallies from reading the code. A "surface" is a screen, section, card, sheet, dialog, banner or menu with its own `##` or table row. "Settings/actions" counts each control once; segmented and dropdown options are listed but not counted separately.

| Part | Surfaces | Settings + actions |
|---|---|---|
| 1. Settings (shell, 11 sections, onboarding) | 18 sections/screens + ~13 dialogs/sheets (modal, sync mode, unlink, rename, revoke, regenerate, clear cache, clear history, reset ×2, licenses, logs, pairing QR) ≈ **31** | ≈ **210** (107 numbered rows, many with 2–6 controls) |
| 2. Torrents + Downloads | **43** (TOR 27, DL 16) | ≈ **245** (torrents ~151, downloads ~94) |
| 3. Extensions | **16** | ≈ **65** |
| 3b. Link + Link Monitor | **20** | ≈ **60** |
| 4. Embedded settings rows | **7** + 7 dialogs/sheets | **102** (84 settings + 18 actions) |
| **Total** | **≈ 124** | **≈ 680** |

Design system: **20 palettes** × light / dark / AMOLED × 3 contrast levels (180 generated schemes), plus Android dynamic color. **4 Look presets** with 11 per-option Look settings. 3 bundled font families. About 15 reusable components (see the snapshot).

# Not determined (needs a running app or more context)
- Runtime look and behavior: no build was run, so nothing here is visually verified. States come from code paths.
- Which optional Koin bindings are present on each platform at runtime: `stremioAvailable`, the reset service (`hasReset`), the update checker (`hasUpdates`), logs (`hasLogs`) and `FrameGenController` on low-end devices. The UI hides these silently when unbound.
- The Android QR scanner internals (camera library) were not read in detail; only the desktop V4L2 path was checked.
- `TorrentDownloadsScreen` is public but has no caller (possibly dead code; Part 2).
- Whether the compact-width Link client (LNK-*) is ever reached on desktop: it is common code, but desktop is the server, and the LinkRepository binding there was not traced.
