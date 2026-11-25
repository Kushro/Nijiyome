# Inventory B: player, manga, novel (functionality parity checklist)

Checklist for the redesign. Rule: **no current functionality may be lost.** Each `##` section is one user-facing surface with a
stable ID. Read from code on branch `Kushro/Redesign-sparkles` (read-only, no build). Labels use the English string resources.

**ID prefixes**
- `PLY-xx`: player shell, desktop controls, shared sheets (quality, audio, speed, more, episodes), source picker, notices, end screen, external mpv, desktop Link and MPRIS.
- `PLA-xx`: Android/touch player: touch chrome (Cinema/Minimal), gestures, HUDs, lock, PiP, immersive, media notification, external app, phone frame generation.
- `PLE-xx`: Enhancements panel (shaders Anime4K/FSR/…, tiers, picture, decoder, stats, Process-on, Link profiles), subtitles (sheet, style editor, fonts, translation), audio/volume, Settings › Player rows owned by `feature/player`.
- `MNG-xx` / `NOV-xx`: manga and novel home, library tab, details, reader, reader settings, narrator (TTS).

**Platform legend**: *both* = commonMain; *android* / *desktop* = `androidMain` / `desktopMain` or a capability flag (`playerCapabilities`, see the PLY table below).

**Overlaps.** Some surfaces were inventoried from two angles. Keep both, and use the more detailed one as the source of truth:

| General entry | Detailed entry |
|---|---|
| PLY-09 Subtitles sheet | PLE-16 |
| PLY-10 Audio sheet | PLE-26 |
| PLY-22 Pre-translate overlay | PLE-20 |
| PLY-23 Link/processing pills | PLE-12, PLE-13 |
| PLY-25 External mpv card | PLE-15 (enhancement modes) |
| PLY-21 Lock overlay | PLA-17 |
| PLY-12 controls-style segment | PLA-27 |
| PLY-15 desktop HUD | PLE-14, PLE-27 |
| PLY-16 notice catalogue | PLE-11, PLE-21, PLA-25 |

Parts: 1) PLY player core · 2) PLA Android/touch · 3) PLE enhancements and subtitles · 4) MNG/NOV manga and novel · 5) Cross-cutting and counts.

# Player — shared shell, desktop controls, sheets, sources, end screen, external mpv (PLY)

Base path: `feature/player/src/` (abbreviated `fp/`). `commonMain` = both platforms; which control bar is drawn depends on
`playerCapabilities.touchFirst` (Android `true` → `TouchControls` in `TouchPlayerUi.kt`, see PLA-xx; desktop `false` → `Controls`
in `PlayerControls.kt`, PLY-05). Capability flags (`fp/commonMain/.../PlayerScreenPlatform.kt:90`, actual values in
`PlayerScreenPlatform.android.kt:387` and `PlayerScreenPlatform.desktop.kt:149`):

| Flag | Android | Desktop |
|---|---|---|
| pictureInPicture | yes (system PiP) | yes (window becomes small always-on-top; toggles) |
| fullscreenToggle | yes (immersive + landscape) | yes (window fullscreen) |
| escapeLeavesFullscreen | no (Esc = system back) | yes |
| screenLock | yes | no |
| screenshot | yes | yes (PNG to `<Pictures>/Nijiyome`) |
| subtitleFile ("Load file…") | yes | yes (file chooser, `text/plain`) |
| backgroundPlayback | yes | no |
| touchFirst | yes | no |
| seekStepMs | 10 s | 5 s |
| speeds | 0.5, 0.75, 1, 1.25, 1.5, 2 | 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2 |
| volumeControls (mute + slider in bar) | no (system volume) | yes |
| sourceQualities (Quality = switch source by quality) | no (stream heights) | yes |
| desktopShortcuts (extra keys, Ctrl+1..6, Esc chain) | no | yes |
| hasHardwareKeyboard (shortcuts button/menu item) | only if `Configuration.keyboard != NOKEYS` | always |

---

## PLY-01 — Player host and status body (loading / manual / no sources / remote unavailable / failed)

- **Source**: `fp/commonMain/.../PlayerScreen.kt:16` (expect), `PlayerBody.kt:66` (`PlayerScreenContent`), `PlayerBody.kt:112` (`PlayerBody`), `PlayerBody.kt:214` (`StatusBody`); desktop `fp/desktopMain/.../PlayerScreen.desktop.kt:11`; Android `fp/androidMain/.../PlayerScreen.android.kt` (PLA).
- **Platform**: both. Theme is forced dark AMOLED with the user's palette/contrast/dynamic colour (`PlayerBody.kt:88`).
- **Entry / exits**: opened from Details episode rows, Home "continue watching", Downloads (local file `localUri`), Torrents (`torrentId`), Link hand-off (`remoteSessionId`), PC library loose files. Exits: `onBack` (system back / Esc / back arrow), `onReplace` (next/prev episode or source change that needs a new route), `onOpenTorrent` → Torrents overview, `onOpenSettings(section)` → Settings (PLAYER / TORRENTS), `onOpenExtensions` → Extensions, `onOpenLinked` → Linked device (Settings › Link), `onOpenDetails(anilistId)` → Details.
- **Controls & actions**

| Control | Type | Action | Notes |
|---|---|---|---|
| Back arrow (top-left) | icon button | leave player | always shown while no session or on error (`PlayerBody.kt:176`) |
| System back / `PlatformBackHandler` | system | leave player | `PlayerBody.kt:107` |
| "Choose a source" (Manual phase) | filled tonal button | open Source picker (PLY-04) | picker also auto-opens once 300 ms after entering Manual (`PlayerBody.kt:146`) |
| "Go to Extensions" (No sources) | empty-state action button | → Extensions | |
| "Linked device" (Remote unavailable) | empty-state action button | → Settings › Link / linked device | |
| "Retry" (Failed) | error-state button | `vm.retry()` | media/candidate list failed to load |
| Network comes back | automatic | `vm.retry()` if phase was ERROR after an offline→online change | `PlayerBody.kt:133` |

- **Data & states**: LOADING / RESOLVING / BUFFERING (before a session): loading indicator after 150 ms + "Looking for sources…" or "Finding a source · <ext> · <label>", "Source N of M"; torrent: "Waiting for torrent metadata…", "<rate> · N peers · P% of the file", "File: <name>". MANUAL: "No source chosen yet". NO_SOURCES: "No sources for this episode"/"Install or enable an extension…" vs (extensions asked) "No sources found"/"The installed extensions returned no source…". REMOTE_UNAVAILABLE: "The session on your PC is no longer available." FAILED: "This video could not be played." + error message. ERROR → PLY-02. EXTERNAL → PLY-25 (desktop) / external screen (Android). A FlashToast (PLY-16) can show here too (top centre). Media notification text: title + "Episode N".
- **UX notes**: Back is the only control on these screens; no "change engine", no "open details" from a failure. Loading copy differs between pre-session and in-session (PLY-17) — unify.

## PLY-02 — Playback error view ("Playback failed" / "Lost the connection to your PC")

- **Source**: `fp/commonMain/.../PlayerOverlays.kt:239` (`PlaybackErrorView`), called from `PlayerBody.kt:277`.
- **Platform**: both.
- **Entry / exits**: phase ERROR (all sources failed, direct source failed, Link failure). Exits to Source picker, Torrents (file pick), Extensions.
- **Controls & actions**

| Control | Type | Action | Condition |
|---|---|---|---|
| Retry | button | `vm.retry()` | always |
| Choose a source | outlined button | open Source picker | `state.canPickSource` |
| Play on the phone | filled tonal button | `vm.playOnPhoneInstead()` (drop the PC/Link session, play the original source locally) | Link session with a phone-side fallback (`linkPhoneFallback`) |
| Use the best web source | filled tonal button | `vm.useBestWeb()` | last failure was a torrent and a web source exists |
| Choose the file | filled tonal button | → Torrents (that torrent) to pick the file | torrent had no file for this episode |
| Go to Extensions | text button | → Extensions | not a Link session |
| Network returns | automatic | retry | PLY-01 |

- **Data & states**: title "Playback failed" or "Lost the connection to your PC". Cause line per case: Link connection lost ("Playback continues by itself when the network is back…"), Link session ended ("The PC closed this session… Send the episode again from the PC."), PC error, phone decoder ("Your phone cannot play this profile. Tap Retry…"), torrent with no file, raw error, "This source did not work. There are N more sources…" (plural), "No other source is available…", decoder prefix "This phone could not decode the video of this source." "Tried" list: one monospace line per failed source `<name>: <reason>` (no peers / no file for this episode / skipped on mobile data / the extension gave no playable stream / raw detail).
- **UX notes**: up to 6 buttons stacked with mixed styles (filled, outlined, tonal ×3, text) — needs a primary/secondary hierarchy. The "Tried" list is monospace debug-style text. Strings say "Tap Retry" on desktop too.

## PLY-03 — Mobile data (metered) dialog

- **Source**: `fp/commonMain/.../PlayerOverlays.kt:214` (`MeteredDialog`), shown from `PlayerBody.kt:187`.
- **Platform**: both (practically Android; triggered when the network is metered before a torrent).
- **Entry / exits**: before starting a torrent source on metered network. Exit to Settings › Torrents.
- **Controls & actions**

| Control | Type | Action |
|---|---|---|
| Watch anyway | button (confirm) | `vm.acceptMetered()` |
| Choose a web source / Cancel | text button (dismiss) | `vm.declineMetered()` (label depends on whether a web source exists) |
| Torrent settings | text button inside body | decline + → Settings › Torrents |
| Outside tap / back | dismiss | same as decline |

- **Data & states**: "You are on mobile data"; "Watching by torrent can use about <size>." or "…can use a lot of data."
- **UX notes**: the settings link sits inside the body as a third button; dialog has no "remember my choice".

## PLY-04 — Source picker sheet ("Episode sources")

- **Source**: `fp/commonMain/.../sources/SourcePicker.kt:23` (`SourcePickerSheet`), `:53` content, `:167` mode banner, `:211` filter bar, `:413` source row, `:526` facts, `:586` reliability bar; filters model `sources/SourceFilters.kt`; missing pick `sources/MissingPick.kt`. Also reused by Details (other agent) with `onDownload` (torrent "Download for later").
- **Platform**: both. Side sheet (`AdaptiveSheet`) when window ≥ `SIDE_SHEET_MIN_WIDTH_DP`, else `ModalBottomSheet`.
- **Entry / exits**: PLY-01 Manual button / auto-open; source chip in the control bar (PLY-05 / touch bar); "Choose a source" in PLY-02; "Sources" in external mpv card (PLY-25). Exits: pick → plays source (`vm.pickSource(key)`, remembered for this episode); "Settings" → Settings › Player.
- **Controls & actions**

| Control | Type | Action | Values / notes |
|---|---|---|---|
| Mode banner "Settings" | text button | → Settings › Player | banner text: "Auto · If it fails, the next one in the list is tried" / "Manual · …"; in Manual: "Manual selection: tap a source to play it." |
| All / Web / Torrent | segmented control (small) | filter by kind | |
| Debrid cached | filter chip (toggle) | only debrid-cached torrents | shown if any cached |
| Sub / Dub / Dual audio / Multi-dub | filter chips (multi) | audio mode filter | only modes present |
| Multi-sub | filter chip | only multi-sub releases | if present |
| Resolution chips 2160p / 1440p / 1080p / 720p / 480p | filter chips (multi) | resolution filter | only those present |
| Medium or higher / High only | filter chips (mutually exclusive toggle) | reliability floor ≥0.7 / ≥0.9 | |
| "Languages & video (N)" | filter chip with chevron | expand advanced panel | count = active advanced filters |
| Audio tracks (language codes) | filter chips (multi, FlowRow) | audio language filter | advanced panel |
| Subtitle languages (codes) | filter chips (multi) | subtitle language filter | advanced panel |
| Quality & release: BD / REMUX / WEB / TV / DVD | filter chips (multi) | release source filter | advanced |
| Codec: H.264 / HEVC / AV1 / VP9 | filter chips (multi) | codec filter | advanced |
| 10-bit, Batch, SeaDex best, Compatible with this device | filter chips (toggle) | flags | advanced, only if present |
| Chip strip chevrons | icon buttons (scroll) | scroll the horizontal chip strip | `HorizontalScrollStrip(showChevrons = true)` |
| "Clear filters" | text button | reset filters | when any active; also action of the "No source matches the filters" empty state |
| Source row (card) | list row / radio card | play this source | disabled when extension is off |
| Download for later | text button in row | queue torrent download | Details only (player passes null) |
| Retry | error/empty-state button | `vm.reloadSources()` | |

- **Data & states**: count "N of M"; loading skeletons ("Looking for sources…", 3 placeholders) and a linear progress bar while more sources arrive; error "The sources could not be loaded"; empty "No sources for this episode"/"Install or enable an extension that has this title."; filtered-empty state; "Your pick (<label>) didn't come up this time." (live region, only after loading finished). Row: kind icon (globe/hub), "<ext> · <server/group>", picked check, hardware-decoder warning icon; pills Web/Torrent, Recommended (primary border), "Cached · <service>", "<ext> off" (error), note text; tag pills (group, codes, N-bit, Dual audio, Multi-dub, Multi-sub, Batch / Batch a–b, SeaDex best); facts line (web: server, quality + container or "Embed", latency ms; torrent: size, seeders, leechers) + "Subs <codes>" + "Audio <codes>" ("not stated"/"several"); "No hardware decoder for this video: it may be slow on this device"; reliability bar with "Reliability: High/Medium/Low · N %". Previous pick sorted to top; otherwise ranking order (filtering never reorders). Filters are local to the sheet (`rememberSaveable`), not persisted.
- **UX notes**: two filter layers (top strip + hidden "Languages & video" panel) — users miss the advanced ones. Reliability floors are two separate chips acting as a single choice (segmented would fit). Language chips are raw ISO codes. Filters are not remembered across episodes. No sort option. Row is dense (up to 5 text lines + pills).

## PLY-05 — Desktop control overlay (top bar, transport, seek bar, bottom chip row)

- **Source**: `fp/commonMain/.../PlayerControls.kt:147` (`PlayerContent`), `:780` (`Controls`), `:1075` (`SeekBar`), `:1173` legend; volume `PlayerVolumeControl.kt:10`; hover bubble `SeekHover.kt:40,64`; ranges `SeekBarRanges.kt`; download `DownloadAction.kt:12`.
- **Platform**: desktop (drawn when `touchFirst == false`). Android draws `TouchControls` (PLA) but shares the sheets below.
- **Entry / exits**: shown on pointer move; hidden after 4 s of playback without interaction (stays up before first frame, while resolving, or a sheet is open). Cursor hides with the controls. Leads to sheets PLY-08…PLY-13, Enhancements panel (PLE), Source picker (PLY-04), Torrents, Downloads queue.
- **Controls & actions**

| Control | Type | Action | Condition / notes |
|---|---|---|---|
| Back | icon button | leave player | top-left |
| Title "<series> · Episode N" | text | — | ellipsised |
| Enhancement status chip | chip | open Enhancements panel (PLE) | always first chip; text e.g. "Anime4K A · Balanced", "FG ×2", "PC", "Media3 · no enhancements" |
| Source chip "Source N of M · <ext> · <label>" | assist chip with chevron | open Source picker | only clickable if `canPickSource`; globe or hub icon |
| "Open in the torrent client" | assist chip | → Torrents (this torrent), pauses playback | torrent with known file |
| Torrent line "<rate> · N peers · P%" | monospace text | — | torrent |
| Download episode | icon button | enqueue the current stream to Downloads, snackbar "queued"/"failed"; icon turns to "Downloaded" | hidden for local files, torrents, Link sessions, DASH |
| Lock controls | icon button | lock overlay | Android only (flag) |
| Screenshot | icon button | save frame; toast with path | disabled until first frame |
| Episodes | icon button | Episodes sheet (PLY-13) | not Link / not loose file |
| Enhancements (sparkles) | icon button | Enhancements panel | |
| Keyboard shortcuts | icon button | Shortcuts panel (PLY-14) | hardware keyboard |
| Picture in picture | icon button | desktop: toggle small always-on-top window | |
| Full screen / Exit full screen | icon button | toggle window fullscreen | |
| Compact width (< 600 dp) | layout | PiP + fullscreen stay; Download, Lock, Screenshot, Episodes, Enhancements, Shortcuts move to "More options" (PLY-06); chips get their own row | |
| Previous episode | icon button | `vm.playPrevious()` | `hasPrevEpisode` |
| Rewind 5 s (desktop) / 10 s | filled tonal icon button 56 dp | seek −step | |
| Play / Pause | filled icon button 80 dp | toggle; shows loading indicator while buffering | disabled until first frame |
| Forward 5 s / 10 s | filled tonal icon button | seek +step | |
| Next episode | icon button | `vm.playNext()` | has next and not Link |
| Buffering text strip | text card above transport | — | PLY-17 |
| Seek bar | slider (drag, click) | seek on release | track shows buffered (primary), torrent pieces downloaded (neutral), PC-processed range (tertiary; softer when estimated), intro/outro/recap marks as thin lines |
| Seek hover bubble | hover-only | shows time under the pointer | desktop only |
| Position / duration | monospace text | — | "--:--" until known |
| Seek legend "■ Downloaded ■ Processed by the PC" | text | — | when there is more than a plain buffer |
| Mute / Unmute | icon button | toggle engine mute | desktop (`volumeControls`) |
| Volume slider 0–100 % + "Volume N %" | slider | set engine volume (unmutes) | desktop |
| More | assist chip | More sheet (PLY-12) | first chip |
| Quality ("1080p" / height / "Quality") | assist chip | Quality sheet (PLY-08) | desktop: shown when sources name qualities and the stream has no renditions |
| Subtitles | assist chip | Subtitles sheet (PLY-09) | once tracks exist or playback started |
| Audio / "Audio · JA" | assist chip | Audio sheet (PLY-10) | once started |
| Speed "1x" | assist chip | Speed sheet (PLY-11) | TalkBack/semantics "Speed 1" |
| Next episode / "Next episode from the same torrent" | assist chip | `vm.playNext()` | has next, not Link |
| Click on video | mouse | play/pause | PLY-24 |
| Double-click on video | mouse | toggle fullscreen | |
| Wheel on video | mouse wheel | seek ±5 s (up = forward) + HUD "+5 s" | |
| Keyboard | keys | see Cross-cutting shortcut table | focus returns to player when bars hide |

- **Data & states**: PlaybackState (position, duration, buffered, playing, started, ended, speed, tracks, heights), torrent, markers, PC processing info. Buffering spinner in play button. Controls remain visible until first frame.
- **UX notes**: Next episode appears twice (transport icon + chip). Speed and Quality are sheets of 6–7 rows for a single choice (segmented/stepper would fit). The chip row scrolls horizontally on narrow windows, hiding chips with no affordance. "More" mixes unrelated things (sleep timer, loop, stable volume). Transport layout is centred on the video (YouTube-mobile style) on desktop too.

## PLY-06 — Compact top bar "More options" menu

- **Source**: `fp/commonMain/.../PlayerControls.kt:876–922`.
- **Platform**: desktop `Controls` with window width < 600 dp (the touch bar has its own, PLA).
- **Controls & actions**: dropdown menu items — Download episode (or "Downloaded", disabled while queued); Lock controls (Android flag only); Screenshot (disabled before first frame); Episodes; Enhancements; Keyboard shortcuts (if keyboard). Opened by the ⋮ icon button.
- **UX notes**: same actions as the wide bar in a different place; Enhancements is reachable from both this menu and the status chip.

## PLY-07 — Shared player sheet container

- **Source**: `fp/commonMain/.../EnhancementWords.kt:233` (`EnhancementSheet`), header row `PlayerControls.kt:594–603`.
- **Platform**: both. Side sheet (`AdaptiveSheet`) on wide/landscape windows so the video stays visible; modal bottom sheet on narrow portrait.
- **Controls**: title text + Close (X) icon button; scrim/outside tap or swipe-down dismiss. Rows are `ListItem`s with a check on the selected one; most rows close the sheet on pick.
- **UX notes**: the "Speed" sheet title is hard-coded Spanish ("Velocidad", `PlayerControls.kt:589`) while the others are localised.

## PLY-08 — Quality sheet

- **Source**: `fp/commonMain/.../PlayerControls.kt:606–613`; desktop variant `SourceQualities.kt` (`SourceQualityRows`).
- **Platform**: both, different semantics.
- **Controls & actions**: Android/stream renditions: rows "Auto" + each height "1080p", "720p"… → `session.selectVideoHeight(h|null)`. Desktop (stream without renditions, `sourceQualities`): rows "Auto" (= ranking's recommended source) + each source quality (2160p…480p) → switches to the best-ranked not-failed source of that quality (`vm.pickSource`). Each row closes the sheet.
- **UX notes**: on desktop "Quality" silently means "switch source"; no indication that the source changes. Fits a segmented control.

## PLY-09 — Subtitles sheet

- **Source**: `fp/commonMain/.../PlayerExtras.kt:164` (`SubtitlesSheetContent`); opened from `PlayerControls.kt:614`. Style panels → PLE.
- **Platform**: both. "Load file…" on both (Android SAF picker, desktop file chooser). Secondary track list differs by engine (`session.supportsSecondaryTracks`).
- **Controls & actions**

| Control | Type | Action | Notes |
|---|---|---|---|
| ASS simplified notice | text row | — | "Simplified subtitles: styled (ASS) subtitles show without their formatting in this engine…" |
| Off | check row | disable subtitles, close | |
| Track rows (label) | check rows | select track, close | |
| "File: <name>" | check row (selected, inert) | — | after loading a file |
| Load file… | list row | open file picker, close sheet; toast "Subtitles loaded: <name>" / "The subtitle file could not be read" | SRT / VTT / ASS / SSA |
| Style — "Size, background, position and more" | list row | open Subtitle style quick panel (PLE) | |
| Subtitle delay − | filled tonal icon button | −0.1 s ("Show earlier") | |
| "Delay: <value>" | text | — | |
| Subtitle delay + | filled tonal icon button | +0.1 s ("Show later") | |
| Reset delay | icon button | 0 | disabled at 0 |
| Media3 hint | text | "With this engine, showing subtitles earlier only works for external or loaded subtitle files." | Media3 + negative delay |
| Secondary subtitle: None + tracks | check rows | `vm.selectSecondary(group|null, external|null)`; drawn at top | engine tracks (mpv) or external tracks (Media3); "This stream has no external track to show as a second subtitle." |
| Translate subtitles | switch row | toggle live translation; supporting text "<Engine> → <LANG>" or the error | only when a translation engine is configured |
| Pre-translate the whole track | text button | `vm.pretranslate()`, close; shows PLY-22 | when translation on |

- **UX notes**: one long scrolling sheet mixing track choice, file import, style, timing, secondary track and translation — candidates for tabs/sections. Delay only in 0.1 s steps (no direct entry, no long-press repeat). Picking a track closes the sheet, so changing delay after picking means reopening.

## PLY-10 — Audio sheet

- **Source**: `fp/commonMain/.../PlayerControls.kt:625–644`.
- **Platform**: both.
- **Controls & actions**: rows "<label> · <detail>" per track → `session.selectAudio(id)`, closes; "Sin audio" row (no audio track) → `AUDIO_TRACK_OFF`. Empty: "No se detectaron pistas de audio adicionales".
- **UX notes**: both strings are hard-coded Spanish (not in resources). "No audio" option sits next to real tracks with no confirmation.

## PLY-11 — Speed sheet

- **Source**: `fp/commonMain/.../PlayerControls.kt:646–648`.
- **Platform**: both; values from `capabilities.speeds` (Android 0.5/0.75/1/1.25/1.5/2; desktop adds 1.75). Rows "Nx" → `session.setSpeed`, closes. Also Android long-press = 2× while held (PLA) and desktop keys `.` / `,` (PLY-14).
- **UX notes**: 6–7 full-width rows for one value; a chip/segmented row or stepper fits. No custom speed.

## PLY-12 — More sheet (controls style, sleep timer, loop, stable volume)

- **Source**: `fp/commonMain/.../PlayerExtras.kt:257` (`MoreSheetContent`); timer model `core/player/.../PlayerTimers.kt:12`.
- **Platform**: both; "Estilo de controles" only on touch (Android).
- **Controls & actions**

| Control | Type | Action | Values |
|---|---|---|---|
| Controls style "Cine" / "Minimalista" | segmented (small, full width) | `vm.setPlayerChromeStyle` (persisted) | CINEMA / MINIMAL — Android only; labels hard-coded Spanish |
| Sleep timer rows | check list rows | `vm.setSleepTimer(o)` | Off, 15 min, 30 min, 60 min, End of the episode; selected row shows "<time> left" |
| Loop — "Start the episode again when it ends" | switch | `vm.setLoop` | also key L (desktop) |
| Stable volume — "Evens out loud and quiet scenes" | switch | `vm.setStableVolume` (persisted setting) | |

- **Data & states**: when the timer fires: playback pauses, no auto-next, toast "Sleep timer: playback paused".
- **UX notes**: sleep timer is 5 stacked rows (chips fit). "More" hides useful toggles; loop state is not visible in the bar.

## PLY-13 — Episodes sheet (jump to episode)

- **Source**: `fp/commonMain/.../PlayerExtras.kt:317` (`EpisodesSheetContent`).
- **Platform**: both. Not offered in Link sessions or loose files.
- **Entry**: Episodes icon (bar), overflow menu, end screen "Episodes".
- **Controls & actions**: rows "N · <title>" / "Episode N" → `vm.playEpisode(n)` (new route); current row disabled with "Playing now" + play icon; error state Retry → `loadEpisodes(force = true)`.
- **Data & states**: loading indicator (after 150 ms), "The episode list could not be loaded", "No episodes to show". Max height 560 dp.
- **UX notes**: text-only list (no thumbnails, no watched marks, no filler flags) while Details has richer rows; no scroll-to-current.

## PLY-14 — Keyboard shortcuts panel ("?")

- **Source**: `fp/commonMain/.../PlayerExtras.kt:11` (`ShortcutsPanel`), shown `PlayerControls.kt:531`; labels `PlayerLabels.kt:15,21`; keymap `core/player/.../PlayerKeymap.kt`.
- **Platform**: both (Android only with hardware keyboard; PiP row only where PiP exists; desktop adds extras + "Upscaling: Off, A, A+A, B, C, FSR — Ctrl+1…6").
- **Entry / exits**: `?` key (SLASH), keyboard icon button, overflow item. Closes with X, scrim click, `?` again, Esc (desktop).
- **Data**: each action with its current key chip (custom map from Settings › Player › Keyboard shortcuts, which is the editor — settings agent).
- **UX notes**: read-only; no link to the editor. No mouse/gesture help.

## PLY-15 — HUD pill (desktop) / OSD

- **Source**: `fp/commonMain/.../PlayerControls.kt:749` (`HudPill`), `PlayerVolumeControl.kt:56` (`DesktopHudContent`); Android uses `TouchGestureOverlay` (PLA).
- **Platform**: desktop (pill at top centre, 900 ms).
- **Data**: Brightness N %; Volume N % / Muted; Seek "+5 s"; "2× while held"; Speed "1.5x"; "Subtitles: <track>" / "Subtitles: Off" / "No subtitles"; "Audio: <track>"; "Delay: <value>"; "Loop on/off"; "Upscaling: <preset>". Aspect label (from aspect cycle) uses Spanish words "Ajustar/Rellenar/…" (`TouchPlayerUi.kt:140`).

## PLY-16 — Flash toast (notice catalogue)

- **Source**: `fp/commonMain/.../PlayerOverlays.kt:64` (`FlashToast`), notices `PlayerViewModel.kt:132` (`PlayerNotice`).
- **Platform**: both; top centre, inverse surface pill, icon check / warning / error; 2.6 s default, 4–7 s for long ones.
- **Controls**: "Undo" text button on undoable quality step-downs (frame drops) → `session.undoAutoDowngrade()`.
- **Notices**: That source failed. Trying another one · This torrent has no file for episode N. Playing another source · Intro skipped (auto-skip setting) · Simplified subtitles (ASS) · Screenshot saved to <path> / could not be saved · Subtitles loaded / could not be read · Sleep timer: playback paused · The remembered source was ignored: it does not match your preferences · Translation failed: <msg> · Subtitles translated (N lines failed…) · Couldn't read the full text of this track; it's translated live · Google is limiting translations; retrying in N s / The translation service is limiting requests… · Image subtitles (PGS/VobSub) can't be translated · switched to libmpv (software) / switched to Media3 (no upscaling) · <debrid> could not serve it (<problem>): playing over P2P · mpv did not open (<detail>): built-in player (desktop) · quality engine reasons (renderer, shader error, 8K, 4K, remote, frame gen, battery ≤20 %, thermal, dropped frames, PiP, viewport, "Back to the chosen quality", "Shaders switched to full precision") · Frame generation: <reason> · Link: session renewed, profile changed / same / busy / failed (timeout, offline, ended, PC error, no session), new session retried, phone cannot play that profile (back to X), preferred profile replaced · Processing switch results/blocks (PLE).
- **UX notes**: single transient channel for ~40 messages, several of them actionable or important (fallbacks, Link failures) that disappear in 5 s with no history; only one has an action (Undo).

## PLY-17 — Buffering overlay and torrent status

- **Source**: `fp/commonMain/.../PlayerOverlays.kt:28` (`BufferingOverlay`), `:12` (`TorrentStatusText`); placement `PlayerControls.kt:401,977`.
- **Platform**: both.
- **States**: controls hidden → centred card with spinner; controls shown → spinner moves into the play button and the text sits above the transport row. Texts: torrent status lines; "Finding a source · <ext> · <label>"; Link: "Your PC is preparing the video from this point…" (debounced). Link stall watchdog (`LinkStallWatchdog.kt`) silently renews a stuck PC session (stall 9 s, 25 s after a seek, 3 attempts with backoff) then gives up into PLY-02 with "Play on the phone".

## PLY-18 — Skip intro / ending / recap button

- **Source**: `fp/commonMain/.../PlayerOverlays.kt:175` (`SkipButton`), shown `PlayerControls.kt:441`; markers `core/player/.../SkipMarkers.kt`, `AniSkipRepository.kt`.
- **Platform**: both; bottom-right above the bar, visible while the position is inside a mark (independent of controls visibility).
- **Controls**: "Skip intro" / "Skip ending" / "Skip recap" filled tonal button → `vm.seekBySkip()`; desktop key Enter (only while a segment is active). Setting "Skip intro" (Settings › Player) auto-jumps once per load + toast.
- **UX notes**: no countdown/auto-hide; no "skip outro → next episode" combined action.

## PLY-19 — Next-episode compact card

- **Source**: `fp/commonMain/.../PlayerOverlays.kt:193` (`NextEpisodeCard`), `EndScreen.kt:386` (`CountdownRing`); countdown `PlayerViewModel.kt:1896`.
- **Platform**: both; shown at episode end when the full end screen is hidden ("Show the video").
- **Controls**: Play now (skip-next icon button), Cancel (X icon button → `cancelNext`, no more auto-advance this episode). Countdown ring with seconds (auto-next on; `autoNextCountdownSecs` 3–30, default 10); without auto-next only the button.
- **Data**: "Next episode", "Episode N", "From the same torrent".

## PLY-20 — Episode end screen

- **Source**: `fp/commonMain/.../EndScreen.kt:106` (`EpisodeEndScreen`), state `:37`, progress `:91`; host `PlayerControls.kt:660`.
- **Platform**: both (also over the external-mpv branch on desktop, without episodes/progress).
- **Entry / exits**: playback ended (not looping, after a first frame, not in PiP). Back → leaves player; Details; Episodes sheet; next episode (new route).
- **Controls & actions**

| Control | Type | Action | Condition |
|---|---|---|---|
| Back | icon button | leave player | |
| Show the video (X) | icon button | hide end screen, show last frame + controls; compact card keeps counting | |
| Next thumbnail (16:9 with ring/play) | clickable image | play next | has next |
| "Watch episode N" | button (56 dp) | play next | has next |
| Cancel | text button | stop countdown | while counting |
| Watch again | filled tonal button | `vm.replay()` (seek 0 + play; countdown re-arms) | |
| Episodes | filled tonal button | Episodes sheet | known title |
| Back to details | outlined button | → Details | has AniList id and (next or not last) |
| Back (outlined) | outlined button | leave | no details id but has next |
| Open details / Back (FinishedCard) | button | → Details or leave | no next episode |
| Arrow keys / Enter | keyboard | move between buttons; only the "next episode" key (K) keeps its action | desktop |

- **Data & states**: blurred poster backdrop (blur on Android 12+ only, else dimmed), badge + "Episode N finished" / "Video finished" / "You finished the series" (trophy, tertiary); series title; progress pill "Episode marked as watched" / "Progress updated: episode N" / "Progress updated: N of M" / "Marked as completed on your list" + "syncs with AniList · MyAnimeList"; "Next in N s" / "Up next", next title, "From the same torrent"; series finished body "Rate it and find related titles on its page." Layouts: wide (≥600 dp) two columns; short (<480 dp tall) compact row.
- **UX notes**: the screen swallows all taps (no gesture to dismiss besides X). "Rate it" text but no rating control here.

## PLY-21 — Lock overlay (shared component)

- **Source**: `fp/commonMain/.../PlayerExtras.kt:359` (`LockOverlay`); lock state `PlayerControls.kt:176,381`. Android only (`screenLock`); details in PLA.
- **Controls**: tap anywhere → show Unlock button (hides after 2.5 s); "Unlock" filled tonal button; hint "Controls locked". Keys ignored while locked; unlocked automatically at episode end.

## PLY-22 — Pre-translation progress overlay

- **Source**: `fp/commonMain/.../PlayerExtras.kt:127` (`PretranslateOverlay`).
- **Platform**: both. Centre card over paused video: "Translating subtitles… N / M", linear progress, "Play now" (text button → `cancelPretranslate`, continues live).

## PLY-23 — Link profile / processing switch pills

- **Source**: `fp/commonMain/.../PlayerControls.kt:725` (`LinkProfileChangingPill`), `:737` (`ProcessingSwitchPill`).
- **Platform**: both (Link client is Android; desktop uses `NoLinkRepository`, `NoLinkClient.kt`, so in practice Android).
- **States**: "Changing profile…" spinner pill; "Switching to the phone/PC/mixed…" pill with "Cancel" text button → `vm.cancelProcessingSwitch()`. Visible with controls hidden. Results come as PLY-16 notices.

## PLY-24 — Desktop pointer gesture layer

- **Source**: `fp/desktopMain/.../PlayerScreenPlatform.desktop.kt:51` (`PlayerGestureLayer`).
- **Platform**: desktop.
- **Controls**: pointer move → show controls + cursor; idle → cursor hidden with controls; single click → play/pause; double click → fullscreen toggle; wheel → seek ±5 s (up = forward) with HUD. No brightness/volume gestures; no right-click menu.
- **UX notes**: because a double-click is also handled, `detectTapGestures` waits for the double-click timeout, so a single click pauses only after about 300 ms. There is no right-click context menu, which desktop players usually have.

## PLY-25 — External mpv card (desktop)

- **Source**: `fp/desktopMain/.../ExternalMpvCard.kt:13` (`ExternalMpvBranch`), `:77` (`ExternalMpvCard`); session/launch `core/player-mpv/src/desktopMain/.../external/*` (ExternalMpvPlayerSession, ExternalMpvLaunch, AnimeBuild, LsfgVk).
- **Platform**: desktop. Shown when the engine is EXTERNAL (Settings › Player "external mpv"), or when frame generation (lsfg-vk) forces mpv's own window.
- **Entry / exits**: any playback with that engine. mpv window closed → back to the previous screen, unless an episode ended with a next one (end screen PLY-20 stays, countdown). "Frame generation settings" → Settings › Player.
- **Controls & actions**

| Control | Type | Action | Condition |
|---|---|---|---|
| Stop | button | end external session, leave | |
| Sources | filled tonal button | Source picker (PLY-04) | sources exist |
| Back | text button | leave | |
| Watch without frame generation | filled tonal button | `vm.useBuiltInPlayer()` → this episode moves into embedded libmpv at the same position (setting unchanged) | forced by FG |
| Frame generation settings | text button | → Settings › Player | forced by FG |
| mpv's own window | external | mpv's own OSC/keys | app draws no controls |
| End screen over it | PLY-20 | Watch next / Cancel / Watch again / Back / Show the video (cancels + leaves if mpv closed) | episodes/details hidden |

- **Data & states**: "Playing in mpv", title, mode line: "Your own mpv setup" / "Enhanced mode (mpv-anime-build)" / "Nijiyome's enhancements" + "Frame generation ×N (lsfg-vk)"; capped note "You asked for ×N; a H Hz screen shows ×M"; "Opening mpv…" spinner; "m:ss / m:ss" + "Paused" + progress bar (read-only); torrent line; "Frame generation only works in mpv's own window, so this episode plays there."; error-coloured "The enhanced mode could not start, so your own mpv setup is used."; hint "Use mpv's own controls and keys…". Failure to launch → toast "mpv did not open (…)" and built-in player.
- **UX notes**: the progress bar is not seekable and there is no play/pause in the app window, although the IPC session exposes them (MPRIS and Link remote can drive it). Stop and Back do the same thing.

## PLY-26 — External app screen (Android external player)

- **Source**: `fp/commonMain/.../ExternalPlayerScreen.kt:12,25` (content); Android launch/branch in `PlayerScreenPlatform.android.kt` (PLA) and `core/player/src/androidMain/.../ExternalPlayer.kt`.
- **Platform**: Android (component is common).
- **Controls**: Stop (button), Back (outlined), "Open again" (text button, after the external app closed without leaving). Data: "Playing in <app>" / "Playing in an external player", title, torrent line, "Use the other app's controls. Coming back here records your progress." Related strings (PLA): "Play with" chooser, "No external player" / "No app can play this video…" + "Open Settings", "<app> is not installed…" + "Play with Media3".

## PLY-27 — "Send to phone" button (desktop, Link)

- **Source**: `fp/desktopMain/.../DesktopLinkRemote.kt:14`.
- **Platform**: desktop, when a phone is paired and the current source is sendable (torrent or public http(s); not local files/loopback/embeds).
- **Controls**: hover-revealed filled tonal icon button (top-right, shown 3 s after pointer movement) → sends episode + current position to the single phone, or a dropdown of phones when several; pauses local playback on success.
- **States**: "Sending to the phone…", "Sent to <name>: it shows "Play here"", "Could not send it (internal error / source unavailable / source not allowed / Link server is off / device not authorized)".
- **UX notes**: separate hover timer from the main controls (appears/disappears independently); lives outside the top bar.

## PLY-28 — System media controls and remote control (desktop)

- **Source**: `fp/desktopMain/.../mpris/DesktopMediaControls.kt:27`, `mpris/Mpris.kt`; Link remote `DesktopLinkRemote.kt:137` (`applyRemote`).
- **Platform**: desktop (MPRIS on Linux only; Link hub on any desktop).
- **Actions**: MPRIS Play, Pause, PlayPause, Next (if next episode), Previous, SetPosition/Seek; metadata title/episode/poster, Seeked signal on jumps; Raise/Quit/OpenUri are no-ops. Paired phone commands: Play, Pause, Toggle, Seek(s), Next, Prev; `GET /v1/player` state (playing, title, episode, position, duration, media id). Discord Rich Presence updated every 15 s (`PlayerScreenPlatform.desktop.kt:136`).
- **UX notes**: invisible to the user; no in-player indicator that a phone is driving playback.

## PLY-29 — On-screen subtitle layer

- **Source**: `fp/commonMain/.../PlayerExtras.kt:63` (`ScreenSubtitleLayer`); spec `core/player/.../ScreenSubtitleSpec.kt`.
- **Platform**: both (draws Media3 cues, loaded files on Media3, secondary track at the top, translated lines on both engines; mpv draws its own otherwise).
- **States**: lifts above visible controls (136 dp) unless the style's bottom margin is larger; secondary line at top under the status bar; ASS vector drawings filtered.

## PLY-30 — "Desktop player not available" placeholder

- **Source**: `fp/desktopMain/.../PlayerScreen.desktop.kt:51`.
- **Platform**: desktop when no libmpv factory is registered (normally never). Empty state "The desktop player is not available yet" + "Back" button.

## PLY-31 — Settings consumed by the player (navigation targets, owned by the settings agent)

Listed so the redesign keeps the in-player shortcuts that jump there: Settings › Player (engine Media3 / libmpv / external; source mode Auto/Manual; skip intro; auto-next + countdown 3–30 s; background playback; subtitle style; translation engine/mode/target/keys/cache; keyboard shortcut editor; phone frame generation incl. "Import Lossless.dll"); Settings › Torrents (metered); Settings › Link (Link RAM cache 512 MB–4 GB for instant rewinds with Anime4K/LSFG = the "rewind cache"; mpv demuxer back-buffer is fixed, not user-visible). In-player entry points: source picker "Settings", metered "Torrent settings", Enhancements "Import Lossless.dll in Settings", external mpv "Frame generation settings", "Linked device".

---

# Player parity inventory — Android / touch (PLA-xx)

Scope: `TouchPlayerUi.kt` (touch chrome, both styles), `feature/player/src/androidMain/*`, `core/framegen` user-visible bits, `core/player/src/androidMain` user-visible bits.
Paths below are relative to the repo root. `FP/` = `feature/player/src/commonMain/kotlin/app/nijiyome/feature/player/`, `FA/` = `feature/player/src/androidMain/kotlin/app/nijiyome/feature/player/`, `FG/` = `core/framegen/src/main/kotlin/app/nijiyome/framegen/`, `CP/` = `core/player/src/androidMain/kotlin/app/nijiyome/player/`.
Names are the English string values (`composeResources/values/strings.xml`, `core/framegen/src/main/res/values/strings_frame_gen.xml`). Strings in "quotes (hardcoded)" are literals in the code, not resources (most of them Spanish).

## When the touch UI applies vs. desktop `Controls`

- `playerCapabilities.touchFirst` is `true` only in `FA/PlayerScreenPlatform.android.kt:387-396` and `false` in `feature/player/src/desktopMain/.../PlayerScreenPlatform.desktop.kt:159`. It is fixed per platform, not per input device: every Android device (phone, tablet, Chromebook, TV with a remote) gets `TouchControls`; every desktop build gets `Controls`.
- Switch points in `FP/PlayerControls.kt`:
  - `:404-408`: gesture HUD → `TouchGestureOverlay` (touch) vs. `HudPill` at top center (desktop).
  - `:459-485`: chrome → `TouchControls(style = settings.playerChromeStyle, …)` (touch) vs. `Controls(...)` (desktop).
  - `:983`: desktop `SeekBar(hoverTime = !touchFirst)`.
  - `FP/PlayerExtras.kt:351`: "Estilo de controles" segmented control in the More sheet, touch only (PLA-27).
  - `FP/PlayerSettingsExtras.kt:400`: shortcuts hint text ("For a hardware keyboard or remote. Tap an action to change its key." vs. "Click an action to change its key.").
- Within the touch UI, `style` is `PlayerChromeStyle.CINEMA` or `MINIMAL`, and `compact = windowWidthDp() < 600` (`TouchPlayerUi.kt:198-199`) changes the top bar layout. That gives four layouts: Cinema/compact, Cinema/wide, Minimal/compact, Minimal/wide.
- Shared across both platforms (covered by other inventories; only referenced here): lock state, sheets (`Sheet` enum: Quality, Subtitles, Audio, Speed, More, Episodes, Enhancements, SubtitleStyle, SubtitleStyleFull), SkipButton, NextEpisodeCard, EndScreen, FlashToast, Statistics overlay body, Link pills.

---

## PLA-01 — Touch player chrome (container)

- **Source**: `FP/TouchPlayerUi.kt:172-596` (`TouchControls`), wired at `FP/PlayerControls.kt:457-485`; visibility rules `FP/PlayerControls.kt:271-283`, timeouts `:1192-1195`.
- **Platform**: Android (all form factors).
- **Entry / exits**: shown when the player opens (it stays up while `!ps.started` or the phase is RESOLVING, `keepUp`). Tapping the video toggles it (PLA-10). It hides by itself 4 s (`CONTROLS_TIMEOUT_MS`) after the last interaction while playing with no sheet open. Fades in and out (`AnimatedVisibility`). It is not drawn in PiP, while locked, or when the end screen is up.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Scrim | passive | Edge gradients (top and bottom); the middle of the frame stays clear | Cinema: 6-stop gradient; Minimal: 4-stop gradient (`:205-223`) |
| Any control press | — | `onInteract()` restarts the 4 s hide timer | Some actions do not call it (see the UX notes) |

- **Data & states**: style (Cinema / Minimal), compact (<600 dp) / wide, buffering (passed in: `(isBuffering || RESOLVING) && error == null`), fullscreen flag, the enhancement chip model, PC Link processing info (`pc` only when processing is not PHONE).
- **UX notes**: all the chrome is one overlay, so a redesign must keep the four layout permutations or merge them on purpose. The style is chosen only inside the player (More sheet, PLA-27); grep found no Settings entry for it.

## PLA-02 — Top bar

- **Source**: `FP/TouchPlayerUi.kt:270-402`.
- **Platform**: Android.
- **Entry / exits**: part of PLA-01.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Back | icon button | `onBack` (leaves the player) | All layouts |
| Title | text | Episode/title, 1 line, ellipsized | All layouts |
| Source suffix " · ext label" | clickable text | Opens the source picker (`onOpenPicker`) when `canPickSource` | Minimal/wide only (`:296-307`), max 180 dp; no visual affordance that it is tappable |
| Top status chips row | chip row | See PLA-04 | Cinema/wide: under the title (`:320-323`); compact (both styles): own row under the bar (`:399-401`); **not shown in Minimal/wide** |
| Enhancement status chip | assist chip | Opens the Enhancements sheet | Minimal/wide shows it standalone in the bar (`:326-328`) |
| Picture in picture | icon button | `platform.enterPictureInPicture()` (16:9 PiP) | When `capabilities.pictureInPicture` (always on Android) |
| Full screen / Exit full screen | icon button (toggle) | Toggles immersive + forced landscape (PLA-19) | Icon Fullscreen / FullscreenExit |
| More options (⋮) | icon button → menu | Opens PLA-03 | Compact only |
| Download episode / Downloaded | icon button | `DownloadAction`: queues an episode download | Wide only, when `LocalDownloadArgs` is present; shows the done state |
| Lock controls | icon button | `locked = true`, closes the open sheet (PLA-17) | Wide only; the icon is `LockOpen` (an open padlock for the "lock" action) |
| Screenshot | icon button | Captures the frame (PLA-26) | Wide only; disabled until `ps.started` |
| Episodes | icon button | Opens the Episodes sheet | Wide only; when `remoteless && !looseFile` |
| Enhancements | icon button | Opens the Enhancements sheet | Cinema/wide only (`:392-394`) |
| Keyboard shortcuts | icon button | Opens the shortcuts help panel | Only when a hardware keyboard is attached (`Configuration.keyboard != KEYBOARD_NOKEYS`) |

- **Data & states**: title; source "ext · label"; fullscreen state; screenshot enabled/disabled; keyboard present.
- **UX notes**: the same actions sit in a menu on compact and in a row of 6-8 icons on wide. Minimal/wide has no torrent "Open in the torrent client" entry at all (the chip row is never composed there). The padlock icon is reversed (LockOpen for "Lock controls").

## PLA-03 — "More options" overflow menu (compact top bar)

- **Source**: `FP/TouchPlayerUi.kt:331-382`.
- **Platform**: Android, window width <600 dp (phone portrait, and most phones in landscape in dp terms only if <600).
- **Entry / exits**: More options (⋮) in PLA-02. Each item closes the menu. Opening it restarts the hide timer, but the timer keeps running while the menu is open (a dropdown is not a `sheet`), so the chrome can fade out under an open menu.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Download episode / Downloaded | menu item | Queues a download | When `LocalDownloadArgs` is present |
| Lock controls | menu item | Locks (PLA-17) | `capabilities.screenLock` |
| Screenshot | menu item | Captures the frame | Disabled until `ps.started` |
| Episodes | menu item | Episodes sheet | `remoteless && !looseFile` |
| Enhancements | menu item | Enhancements sheet | Always |
| "Cambiar fuente" (hardcoded) | menu item | Source picker | Only Minimal + `canPickSource`; not localized |
| Keyboard shortcuts | menu item | Shortcuts help | Hardware keyboard only |

- **Data & states**: item enabled states as above.
- **UX notes**: "Cambiar fuente" is Spanish-only and appears only in Minimal; Cinema/compact changes the source only through the source chip (PLA-04). Lock and Screenshot, which are used often, are one tap deeper on phones.

## PLA-04 — Top status chips row

- **Source**: `FP/TouchPlayerUi.kt:227-253`, `TouchPill` `:673-715`, `EnhancementStatusChip` `FP/EnhancementWords.kt:196-226`, chip model `FP/EnhancementChip.kt:79-125`.
- **Platform**: Android (Cinema all widths; Minimal compact only).
- **Entry / exits**: part of the top bar; the row scrolls horizontally with a fading edge.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Enhancement status chip ("Enhancements: %s. Tap to change them.") | assist chip | Opens the Enhancements sheet | Tone: ACTIVE (primary container), WARNING (error container + warning icon), NEUTRAL (outlined). Icon: spinner while switching, warning, Computer (PC), Devices (Mixed), AutoAwesome (phone) |
| "Source %1$d of %2$d · %3$s" | pill (+ ExpandMore when switchable) | Opens the source picker if `canPickSource`, otherwise does nothing | Icon Hub for a torrent, Language for HTTP; max 220 dp label |
| Open in the torrent client | pill | `onOpenTorrent(torrentId)`; PlayerBody pauses first (`vm.pauseForClient()`) | Only for a torrent with a selected `fileIndex` |

- **Data & states**: the chip label covers Phone (level + RUNNING / RESTRICTED / PAUSED / OFF + phone FG ×N + extras deband/sharpen/smoothing), Media3 (no enhancements), PC (falling behind / degraded → warning), Mixed (phone FG problem → warning), Switching (spinner).
- **UX notes**: when `canPickSource` is false the source pill still looks like a button but does nothing on tap. The phone FG state shows only as a chip tone and suffix; there is no dedicated FG indicator.

## PLA-05 — Center transport row

- **Source**: `FP/TouchPlayerUi.kt:405-489`.
- **Platform**: Android.
- **Entry / exits**: part of PLA-01.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Previous episode | icon button | `vm.playPrevious()` | When `hasPrevEpisode` (no `remoteless` check) |
| Rewind 10 seconds | icon button (Minimal: bare 48 dp; Cinema: 46 dp glass circle) | `seekBy(-10 s)` (`capabilities.seekStepMs`, 10 000 ms on Android) | |
| Play / Pause | button (Minimal: 64 dp bare; Cinema: 62 dp primary filled circle) | `togglePlay()` | Disabled until `ps.started`; while buffering the icon is replaced by a `LoadingIndicator` (the content description stays Play/Pause) |
| Forward 10 seconds | icon button | `seekBy(+10 s)` | |
| Next episode | icon button | `vm.playNext()` | When `hasNextEpisode && remoteless` |

- **Data & states**: playing/paused, buffering spinner inside the play button, disabled before the first frame.
- **UX notes**: Previous and Next have different conditions (Previous ignores `remoteless`). Next appears twice (here and in the bottom bar). The Minimal play button is a clickable `Box` with no Button role.

## PLA-06 — Buffering text card (controls visible)

- **Source**: `FP/TouchPlayerUi.kt:491-493` → `BufferingOverlay(showIndicator = false)` `FP/PlayerOverlays.kt:89-110`. When the controls are hidden, the full card with a spinner is centered (`FP/PlayerControls.kt:399-401`).
- **Platform**: Android (the hidden-controls variant is shared).
- **Entry / exits**: shown while buffering or resolving and the error is null; placed above the transport row.
- **Controls & actions**: none (passive).
- **Data & states**: torrent status text (peers/progress via `TorrentStatusText`), "Resolving from <ext · label>" while RESOLVING, or the "preparing on the PC" Link text (`player_link_preparing`) when not remoteless. The card is not shown if there is no text.
- **UX notes**: there is no buffering percentage or ETA. The text-only card can show without a visible spinner when the play button is off-screen.

## PLA-07 — Touch seek bar (scrub bubble, legend, time)

- **Source**: `FP/TouchPlayerUi.kt:720-1013` (`TouchSeekBar`), Minimal time row `:1016-1069`, legend dot `:1072-1077`.
- **Platform**: Android.
- **Entry / exits**: bottom of PLA-01. Disabled when `durationMs <= 0` (live or unknown duration).
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Seek track (34 dp hit area) | tap | `seekTo(fraction × duration)` at once | |
| Seek track | horizontal drag | Moves the preview thumb; `seekTo` only on release; cancel = no seek | The track and thumb grow while dragging (Cinema 4.5→8 dp, Minimal 3.5→7 dp) |
| Scrub bubble | passive (while dragging) | Shows target time, signed delta ("+1:05" / "-12s") and the active segment label "Opening" / "Ending" / "Resumen" (hardcoded) or "Buffer +Ns" (≥3 s ahead) | Follows the thumb and stays inside the bar |
| Time "0:00 / 24:00" (Cinema, under the bar) | tap toggle | Switches between total " / 24:00" and remaining "  -mm:ss" | Default: total |
| Remaining "-mm:ss" (Minimal, right of the row above the bar) | tap toggle | Switches between remaining (default) and total | Minimal's default is the opposite of Cinema's |
| Legend (Cinema) | passive | "Buffer +Ns" (≥2 s), "PC" (cyan, PC-processed range), "OP/ED" (amber markers) | Hardcoded labels |
| Torrent download speed | passive | `formatRate(downBps)` | Cinema: right of the time row; Minimal: in the cyan telemetry text |

- **Data & states**: track layers drawn from back to front: base track; PC Link processed range (cyan, dimmer when estimated); downloaded torrent pieces; engine/torrent ready buffer from the playhead; played (primary, with a halo while dragging); Opening/Ending/Recap markers (amber, intro brighter); thumb with a halo. "--:--" when the duration is unknown.
- **UX notes**: no thumbnail preview. Segment labels, "Buffer", "PC", "OP/ED" are hardcoded (Opening/Ending in English, "Resumen" in Spanish). Remaining-time toggles behave in opposite directions in the two styles. No haptics. A tap on the track seeks with no confirmation.

## PLA-08 — Bottom action pills (Cinema)

- **Source**: `FP/TouchPlayerUi.kt:534-592`; quality label `:513-517`.
- **Platform**: Android, Cinema style.
- **Entry / exits**: below the seek bar; the row scrolls horizontally with fading edges.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Audio / "Audio · JA" | pill | Opens the Audio sheet | When there are tracks or `ps.started`; shows the selected language uppercased |
| Subtitles / "Subtitles · EN" | pill | Opens the Subtitles sheet | Language only while subtitles are enabled |
| Quality / "1080p" | pill | Opens the Quality sheet | Only when `videoHeights` is not empty (Android `sourceQualities = false`, so source qualities never apply) |
| Speed "1x" ("Speed %s") | pill | Opens the Speed sheet | Android speeds: 0.5, 0.75, 1, 1.25, 1.5, 2 (`PHONE_SPEEDS`) |
| Aspect "Ajustar"/"Rellenar"/"Estirar" (hardcoded) | pill (cycle) | Cycles Fit → Fill → Stretch → Fit; shows the Aspect HUD (PLA-15) | Highlighted when not Fit. 16:9 and 4:3 exist in `AspectMode` but the touch cycle never reaches them (only the quality engine / Enhancements can set them) |
| Next episode / Next episode from the same torrent | pill | `vm.playNext()` | When `hasNextEpisode && remoteless`; does not call `onInteract` |
| More | pill | Opens the More sheet (chrome style, sleep timer, loop, stable volume…) | |

- **Data & states**: active audio/subtitle languages, selected height, speed, aspect.
- **UX notes**: the aspect labels are Spanish-only. On narrow screens the pills scroll off the edge; More can be hidden past the fold.

## PLA-09 — Bottom action bar (Minimal)

- **Source**: `FP/TouchPlayerUi.kt:599-670`.
- **Platform**: Android, Minimal style.
- **Entry / exits**: below the seek bar. Compact: one row that scrolls horizontally; wide: two groups spaced apart.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Audio icon + "JA" / "Audio" | icon+label item | Audio sheet | Same condition as Cinema |
| Subtitles icon + "EN" / "Sub" / "Off" | icon+label item | Subtitles sheet | "Sub"/"Off"/"Audio" hardcoded |
| Speed "1x" | icon+label item | Speed sheet | |
| Quality "1080p" / Quality | icon+label item | Quality sheet | When `hasQuality` |
| Aspect (icon only at Fit, label otherwise) | icon item (cycle) | Fit → Fill → Stretch | Tinted primary when not Fit |
| Enhancements | icon button 36 dp | Enhancements sheet | |
| Next episode | icon button 36 dp | `playNext()` | `hasNextEpisode && remoteless` |
| More | icon button 36 dp | More sheet | |

- **Data & states**: as in PLA-08.
- **UX notes**: the items are 18-20 dp icons in roughly 30-36 dp targets, under the 48 dp guideline. Aspect at Fit is an icon with no text.

## PLA-10 — Touch gesture layer (video surface)

- **Source**: `FA/PlayerScreenPlatform.android.kt:134-258` (`PlayerGestureLayer` actual), mounted at `FP/PlayerControls.kt:383-394`.
- **Platform**: Android.
- **Entry / exits**: active whenever not in PiP and not locked. It sits under the chrome, so taps on empty chrome areas reach it.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Single tap anywhere | tap | Toggles the controls | Waits for the double-tap timeout first (default Compose detector) |
| Double tap, left half | double-tap | `seekBy(-10 s)`; HUD PLA-13 | Repeated double-taps on the same side within 950 ms add up in the HUD (-10, -20, …); each one seeks 10 s |
| Double tap, right half | double-tap | `seekBy(+10 s)`; HUD PLA-13 | Same accumulation |
| Long-press anywhere | long-press (hold) | Sets speed 2× (`HOLD_SPEED`); on release it goes back to the previous speed; HUD PLA-16 | Fixed 2×; the HUD lasts 900 ms, not the whole hold |
| Vertical drag, left half | drag | Window brightness 1 %–100 % (`screenBrightness`), about 0.83 of the screen height for the full range; HUD PLA-11 | Starts from the window value or 50 % if unset; restored when the last player closes |
| Vertical drag, right half | drag | `STREAM_MUSIC` volume, one step per 1.2/max of the height, no system volume panel; HUD PLA-12 | Read again from the system at every drag start; a muted stream stays muted (the HUD shows 0 %) |
| Horizontal drag anywhere | drag | Relative scrub: a full screen width = 90 s; seeks on release if \|delta\| > 0.5 s; HUD PLA-14 | Clamped to 0..duration; no cancel gesture |

- **Data & states**: none of its own (it posts `Hud` values).
- **UX notes**: no edge exclusion for the system back/home gestures, so drags near the edges compete with them. The gestures cannot be discovered from the UI (no hints), and none can be configured (step, hold speed, sides) or turned off. The volume gesture cannot unmute. Tap response is delayed by double-tap detection.

## PLA-11 — Brightness rail HUD

- **Source**: `FP/TouchPlayerUi.kt:1085-1090`, `VerticalGestureRail` `:1127-1167`.
- **Platform**: Android.
- **Entry / exits**: left vertical drag; disappears 900 ms after the last update (`HUD_TIMEOUT_MS`).
- **Controls & actions**: none (passive).
- **Data & states**: BrightnessMedium icon, a 104 dp vertical fill bar, "N%". Left side, vertically centered.
- **UX notes**: the HUD has no text label (the icon only); the desktop HudPill uses "Brightness".

## PLA-12 — Volume rail HUD

- **Source**: `FP/TouchPlayerUi.kt:1091-1096`, `:1127-1167`.
- **Platform**: Android.
- **Entry / exits**: right vertical drag; 900 ms timeout.
- **Controls & actions**: none.
- **Data & states**: VolumeUp icon or VolumeOff (muted, dimmed), fill bar, "N%" or "0%" when muted. Right side, centered.
- **UX notes**: a muted state shows "0%" instead of "Muted" (the desktop pill says "Muted"), so it is unclear why the drag does nothing.

## PLA-13 — Double-tap seek arc

- **Source**: `FP/TouchPlayerUi.kt:1097-1101`, `DoubleTapSeekArc` `:1170-1207`.
- **Platform**: Android.
- **Entry / exits**: double tap on either half; 900 ms timeout.
- **Controls & actions**: none.
- **Data & states**: a half-pill gradient over 36 % of the width on the tapped side, the FastRewind/FastForward icon, and the accumulated "+20s" / "-10s".
- **UX notes**: the accumulated value resets after 950 ms of no double taps, while the HUD hides at 900 ms.

## PLA-14 — Horizontal scrub HUD

- **Source**: `FP/TouchPlayerUi.kt:1102-1105`, `HorizontalScrubHud` `:1210-1254`.
- **Platform**: Android.
- **Entry / exits**: horizontal drag on the video; it stays while the drag keeps updating, then hides after 900 ms.
- **Controls & actions**: none.
- **Data & states**: target time, signed delta, a mini progress bar (played + buffered) when the duration is known.
- **UX notes**: no segment markers (OP/ED) here, although the seek bar bubble has them.

## PLA-15 — Aspect HUD pill

- **Source**: `FP/TouchPlayerUi.kt:1106-1120`; posted by `cycleAspect()` `FP/PlayerControls.kt:199-205`.
- **Platform**: Android.
- **Entry / exits**: the Aspect pill or item (PLA-08/09); 900 ms.
- **Controls & actions**: none.
- **Data & states**: the AspectRatio icon plus "Ajustar" / "Rellenar" / "Estirar" (hardcoded, top center).
- **UX notes**: the label is not localized. When the quality engine is available the aspect follows `quality.requested.aspect` (`:198`), so the local cycle and the Enhancements setting share one value with no visible link between them.

## PLA-16 — Hold-speed and key HUD pill

- **Source**: `HudPill` `FP/PlayerControls.kt:749-775`, used by `TouchGestureOverlay` for every other kind (`FP/TouchPlayerUi.kt:1121`).
- **Platform**: Android (shared component).
- **Entry / exits**: long-press (Hold) or keyboard actions that post a HUD; 900 ms.
- **Controls & actions**: none.
- **Data & states**: Hold: Speed icon + "2× while held". Seek (keyboard): "player_seek_seconds" "%+d". The other kinds go to `DesktopHudContent` (not reached on Android, where the desktop actions are disabled).
- **UX notes**: the "2× while held" pill hides after 900 ms while the hold continues, so nothing on screen shows the 2× state for the rest of the hold.

## PLA-17 — Lock overlay

- **Source**: `LockOverlay` `FP/PlayerExtras.kt:450-466`; state `FP/PlayerControls.kt:175-176, 228-233, 380-382`; unlocked automatically at the end screen (`:302`).
- **Platform**: Android (`capabilities.screenLock = true`).
- **Entry / exits**: "Lock controls" (PLA-02/03). Exit: Unlock (the controls show again). The end of the episode unlocks by itself. The system Back still leaves the player while locked (`PlatformBackHandler(enabled = true)`, `FP/PlayerBody.kt:108`).
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Tap anywhere | tap | Shows the Unlock button again | |
| Unlock | filled tonal button (Lock icon) | `locked = false`, controls visible | Left side, vertically centered; hides 2.5 s after it appears (`LOCK_BUTTON_TIMEOUT_MS`) |
| Hint "Controls locked" | text | — | Under the button |
| Hardware keys | — | All player keys are ignored while locked (`onPreviewKeyEvent` returns false) | |

- **Data & states**: locked + button visible or hidden. Gestures, the chrome, the HUDs, skip/next cards and the buffering overlay are all suppressed while locked (the code at `:383` and below sits under `!locked`).
- **UX notes**: Back is not blocked, which is often the reason a user locks the screen. Unlocking takes a single tap (no hold or slide), so the lock is easy to undo by accident. Skip intro and the next-episode card are hidden while locked.

## PLA-18 — Picture-in-picture

- **Source**: `FA/PlayerScreenPlatform.android.kt:353-376` (listener, auto-enter, `pipParams`), `AndroidPlayerActions.enterPictureInPicture` `:412-414`; FG/quality told at `FP/PlayerControls.kt:268`.
- **Platform**: Android 8+ (manual), 12+ (auto-enter).
- **Entry / exits**: the PiP button (PLA-02), the `P` key, or automatically on Home/Recents while playing on Android 12+ (`setAutoEnterEnabled(isPlaying)`). Exit: system PiP controls (expand / close).
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| System PiP window | system | Expand / close / drag | No custom `RemoteAction`s (no play/pause or skip in the window from the app) |

- **Data & states**: in PiP the player hides all chrome, gestures, the lock and the end screen. The aspect ratio is fixed at 16:9. The quality engine caps its tier and phone FG is suspended (`fg_reason_pip` "Paused in picture-in-picture"). ON_STOP does not pause in PiP.
- **UX notes**: the PiP ratio does not follow the video's aspect. Android 8-11 users get no auto-PiP: Home pauses playback unless background playback is on. Play/pause in the PiP window relies on the system and the MediaSession, which exists only with background playback on (PLA-20).

## PLA-19 — Window mode: fullscreen / immersive, orientation, keep-awake

- **Source**: `FA/PlayerScreenPlatform.android.kt:269-367` (`PlayerWindowScope`, `PlayerWindowOwner`, `PlayerContentEffects`), `initialFullscreen() = true` `:398-399`.
- **Platform**: Android.
- **Entry / exits**: the player opens fullscreen. Toggle with Full screen / Exit full screen (PLA-02) or the `F` key. Leaving the last player screen restores orientation, system bars, brightness and keep-awake.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Full screen (on) | state | Hides the system bars (swipe shows them for a moment), forces `SENSOR_LANDSCAPE` | |
| Exit full screen (off) | state | Shows the system bars, orientation `UNSPECIFIED` (the device decides) | No "portrait player" layout of its own; the same chrome is used |
| Keep screen on | automatic | `keepScreenOn` only while playing | |
| Pause on stop | automatic | ON_STOP pauses unless in PiP or background playback is on | |

- **Data & states**: fullscreen is saveable across recomposition and rotation. The window state is shared between overlapping player screens (next-episode handover).
- **UX notes**: there is no orientation lock or rotate button (landscape is forced in fullscreen). Fullscreen is not remembered between sessions; it is always on at start.

## PLA-20 — Background playback media notification / lock screen

- **Source**: `CP/PlaybackService.kt` (whole file), started from `FA/PlayerScreenPlatform.android.kt:349-352`; metadata `FA/PlayerPlatform.android.kt:43-45`.
- **Platform**: Android, only with Settings › Player "background playback" on (`AppSettings.backgroundPlayback`), Media3 and libmpv.
- **Entry / exits**: starts when playback begins with the setting on. It stops when the engine is released, at playback end (`STATE_ENDED`), or when the task is swiped from Recents (that also pauses). Tapping the notification opens the app's launch intent (resets the task if needed).
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Play / Pause | notification / lock-screen media button | Forwarded to the active engine | Media3 `DefaultMediaNotificationProvider` defaults |
| Seek back / Seek forward / seek bar | notification media controls | Seek on the engine | Default Media3 command set (exact buttons depend on the Android version) |
| Headset / Bluetooth media buttons | hardware | Play/pause/seek through the MediaSession | Only while the service is running |
| Notification tap | tap | Opens the app (player screen) | |

- **Data & states**: title, subtitle ("Episode N"), artwork (poster) from `ActivePlayback.metadata`; channel name "Playback".
- **UX notes**: there is no next/previous episode command in the notification. With the setting off there is no MediaSession at all, so headset buttons, the lock-screen controls and PiP media controls are missing in normal foreground playback too (inferred from the code).

## PLA-21 — External player launch + "Playing in …" screen

- **Source**: `ExternalBranch` `FA/PlayerScreen.android.kt:96-149`, `ExternalPlayerContent` `FP/ExternalPlayerScreen.kt:55-111`, intent builder `CP/ExternalPlayer.kt`.
- **Platform**: Android, when the engine is EXTERNAL (Settings › Player).
- **Entry / exits**: on load it launches once with `ACTION_VIEW` to the chosen package (mpv-android `is.xyz.mpv`, VLC `org.videolan.vlc`) or the system chooser "Play with" when no package is set. It passes headers, title, start position, subtitle URLs and `return_result`. When the other app returns, the progress (position/duration extras) is recorded and the screen goes Back automatically.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Stop | button | `onBack` | Same as Back |
| Back | outlined button | `onBack` | |
| Open again | text button | Relaunches the intent | |
| System chooser "Play with" | system dialog | Pick an app | Only when no package is configured |

- **Data & states**: "Playing in %1$s" (app label) or "Playing in an external player"; the title; the torrent line (monospace) for torrents; hint "Use the other app's controls. Coming back here records your progress."
- **UX notes**: Stop and Back do the same thing. MIME types: HLS `application/x-mpegURL`, DASH `application/dash+xml`, MP4, mkv/webm by extension, otherwise `video/*`.

## PLA-22 — External player: no app error state

- **Source**: `FA/PlayerScreen.android.kt:126-139`.
- **Platform**: Android.
- **Entry / exits**: `canLaunch` is false or `ActivityNotFoundException` (BUG-046).
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Play with Media3 | button | `vm.useBuiltInPlayer()` (switches to the built-in player) | |
| Open Settings | outlined button | Opens Settings › Player | |

- **Data & states**: title "No external player"; body "%1$s is not installed on this phone. Install it, choose another app in Settings › Player, or play here." (mpv-android / VLC / package name) or "No app can play this video. Install mpv or VLC, or choose another engine in Settings › Player."
- **UX notes**: there is no Back button on this state (only the system Back). The fallback always says Media3 even when libmpv could play the file.

## PLA-23 — Frame generation (phone) card in the Enhancements sheet

- **Source**: `FrameGenSection` `FA/PhoneFrameGen.android.kt:88-151`, mounted at `FP/EnhancementPanel.kt:137-140`; state model `FG/FgUiContract.kt`; texts `FG/FrameGenText.kt`; values `FG/FgProfile.kt`, `core/model/.../FrameGen.kt`.
- **Platform**: Android, libmpv engine only, processing location PHONE or MIXED (hidden for strict PC).
- **Entry / exits**: Enhancements sheet (chip, top bar, menu, Minimal bar). Opening the card probes eligibility (`fg.refresh()`). "Import Lossless.dll in Settings" closes the sheet, **pauses playback**, and opens Settings › Player.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| "Use when playing" | switch row | `fg.setEnabled` (applied while playing, persisted, global) | Disabled when INELIGIBLE and off; warning style when a reason is not SUSPENDED |
| Status hint under the switch | text | — | "On the phone · %d×" (active) / "Checking this device…" / reason text / "Available" / "Off" |
| Import Lossless.dll in Settings | text button | Pause + Settings › Player | Shown when the reason is SHADERS or `shadersImported == false` |
| Live line "%1$s → %2$s fps ×%3$d" | text (primary) | — | Only active with measured in-fps > 0 |
| "The GPU cannot keep up" | warning line | — | Overloaded: measured input <80 % of the source for >5 s |
| "Experimental on this GPU" | info line | — | GPU outside the tested range (not Adreno 7xx+) |
| Multiplier | segmented (small, full width) | `fg.setMultiplier`: **Auto, 2×, 3×, 4×, 5×** | Hidden when ineligible. Hint: "Auto picks the one that matches the screen refresh rate and lowers it if the GPU falls behind." |
| Profile | segmented | `fg.setProfile`: **Saver, Balanced, Quality** (+ **Custom** only while it is the active one) | Hidden when ineligible. Hint: "Saver works at 75 % resolution; Quality uses the full LSFG model and costs about twice as much." Presets: Saver = performance, flow 0.5, input 75 %; Balanced (default) = performance, flow 0.5, input 100 %; Quality = quality mode LSFG_3_1, flow 1.0, input 100 % |

- **Data & states** (`FgUnavailableReason` → English text):
  - INELIGIBLE: ABI "Not available on this device/version (needs a 64-bit ARM or x86 build)"; LIBRARY "…(the frame generation library did not load)"; VULKAN "Not available on this device: needs Vulkan 1.3"; SHADERS "Import Lossless.dll to use it".
  - SUSPENDED (resumes by itself): PIP "Paused in picture-in-picture"; BACKGROUND "Paused in the background"; LOW_POWER "Paused by battery saver"; EIGHT_K "Paused for 8K video"; HDR_PASSTHROUGH "Paused with HDR passthrough"; REMOTE "The PC already does it (Nijiyome Link)".
  - FAILED (no retry until another file): DEVICE_LOST "Stopped: the GPU reset during playback"; START_FAILED "Could not start for this video" or, with the no-input watchdog, "Could not start for this video: MPV delivered no frames".
  - Other states: active (multiplier N), checking, ready, off, experimental, overloaded.
  - **No thermal state exists** in `core/framegen` (grep for "thermal" finds nothing); overload and battery saver are the only load signals.
- **FG options held only in Settings › Player (not in the player; listed for parity)**: Change the screen refresh rate (on by default; "video fps × multiplier"), Allow on untested GPUs (Vulkan 1.1/1.2, experimental), Show frame generation stats, Audio offset −500…+500 ms ("%+d ms"), LSFG shaders Import / Import again / Remove (with the import results: "Shaders imported", "That file is too large to be Lossless.dll", "The file could not be read", "That file does not contain the LSFG shaders (is it Lossless.dll?)", "The shaders could not be saved", "Not available on this device/version", "Shaders removed"), Custom profile tuning (performance yes/no, flow scale 0.25–1.0, input scale 75 %/100 %), priority FLUIDITY (default) / SHARPNESS (caps at 2×), video-fit floor 0 / 0.5 / 0.75 / 1.0, FG at screen resolution. Their exact Settings UI is outside this inventory.
- **UX notes**: Custom cannot be chosen in the player (it shows only while it is active). The import action pauses the video and leaves the player. Ineligible devices see only the switch and the reason, with no explanation of how to become eligible beyond the reason text. The FG card sits deep in a long sheet.

## PLA-24 — FG line in the Statistics overlay

- **Source**: `AndroidPhoneFrameGen.StatsLine` `FA/PhoneFrameGen.android.kt:62-73`, used at `FP/EnhancementStats.kt:75-77`; status rules `FG/FgStatsView.kt:153-200`.
- **Platform**: Android libmpv, when "Statistics" is switched on in the Enhancements sheet and the quality engine is available.
- **Entry / exits**: the Statistics switch (player-local `statsVisible`, `FP/PlayerControls.kt:196`; it is not the FG "Show frame generation stats" setting). The overlay sits at top-left and moves down when the controls are visible.
- **Controls & actions**: none.
- **Data & states**: ACTIVE/PAUSED: "FG: %1$s → %2$s fps ×%3$d · %4$s ms" (in fps, out fps, N, latency ms); amber (#FFD180) when overloaded. FAILED/SUSPENDED: "FG: <reason>". Otherwise "FG: Off". The fps values are measured when ACTIVE, otherwise nominal.
- **UX notes**: two separate "stats" switches (the player's Statistics, the FG setting "Show frame generation stats") with no visible link between them; from the code the FG setting seems to drive only the logcat line (not confirmed).

## PLA-25 — FG failure notice (flash toast)

- **Source**: `FA/PhoneFrameGen.android.kt:40-49` (posts `PlayerNotice.FrameGen`), text `FP/PlayerOverlays.kt:172`, duration `:128-133`.
- **Platform**: Android libmpv.
- **Entry / exits**: posted when the reason changes to a FAILED kind while FG is enabled (mid-video). Shown for 5 s, top center, warning style.
- **Controls & actions**: none (no action button).
- **Data & states**: "Frame generation: <reason>" (DEVICE_LOST / START_FAILED / no input).
- **UX notes**: ineligible and suspended states never get a notice, only the card text. The notice has no "Retry" or "Open settings" action.

## PLA-26 — Screenshot capture + result notice

- **Source**: `captureScreenshot` `FA/PlayerScreenPlatform.android.kt:445-475`, `CP/Screenshots.kt`, notice `FP/PlayerOverlays.kt:137,148-149`, `PlayerViewModel.onScreenshot` `:3021`.
- **Platform**: Android.
- **Entry / exits**: Screenshot (top bar or menu, enabled after `started`), or the `S` key.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| Screenshot | action | mpv writes a PNG (`screenshot-to-file`); Media3 or an mpv failure falls back to `PixelCopy` of the SurfaceView; saved to `Pictures/Nijiyome` (MediaStore on 10+, app Pictures dir before) | File name `Nijiyome_<title>_E<ep>_<yyyyMMdd-HHmmss>.png` |

- **Data & states**: "Screenshot saved to %1$s" (4 s) or "The screenshot could not be saved".
- **UX notes**: no share/open action on the notice. The PixelCopy path captures the screen surface (screen resolution, no subtitles drawn by Compose), so Media3 shots may differ from mpv shots.

## PLA-27 — Controls style picker (More sheet, touch only)

- **Source**: `FP/PlayerExtras.kt:348-366`.
- **Platform**: Android (`touchFirst`).
- **Entry / exits**: More (PLA-08/09) → top of the More sheet.
- **Controls & actions**:

| Control | Type | Action | Notes |
|---|---|---|---|
| "Estilo de controles" (hardcoded) | segmented | `vm.setPlayerChromeStyle`: **"Cine" (CINEMA)**, **"Minimalista" (MINIMAL)** | Persisted in settings (`settings.playerChromeStyle`) |

- **Data & states**: the current style.
- **UX notes**: the title and labels are Spanish-only literals. A choice that changes the whole player layout sits inside the More sheet.

---

## Android cross-cutting

### Gesture map

| Zone / gesture | Action | Condition |
|---|---|---|
| Anywhere, single tap | Toggle the controls | Not locked, not PiP |
| Left half, double tap | −10 s (adds up per repeat within 950 ms in the HUD) | Not locked, not PiP |
| Right half, double tap | +10 s (same) | Not locked, not PiP |
| Anywhere, long-press and hold | 2× speed while held, previous speed on release | Not locked, not PiP |
| Left half, vertical drag | Window brightness 1–100 % | Not locked, not PiP |
| Right half, vertical drag | System media volume (steps; muted stays muted) | Not locked, not PiP |
| Anywhere, horizontal drag | Relative scrub, 90 s per screen width, seek on release if >0.5 s | Not locked, not PiP |
| Seek bar, tap | Absolute seek | Duration known |
| Seek bar, horizontal drag | Absolute scrub with preview bubble, seek on release | Duration known |
| Time text (Cinema) / remaining text (Minimal), tap | Toggle total vs. remaining | Controls visible |
| Locked screen, tap | Show the Unlock button for 2.5 s | Locked |
| System back gesture/button | Leave the player (closes the open sheet first: not verified here) | Always, including while locked |
| Home / Recents | Auto PiP (Android 12+, playing); otherwise pause on stop unless background playback | — |
| Pinch | **None** (no pinch-to-zoom/fill) | — |

### Hardware / media keys (Android)

| Key (default, remappable in Settings) | Action | Condition |
|---|---|---|
| SPACE | Play/pause | Not locked; end screen not up |
| DPAD_LEFT / DPAD_RIGHT | −10 s / +10 s | Same |
| DPAD_UP / DPAD_DOWN | System volume up/down (system volume panel, no HUD) | Same |
| M | Toggle mute (system) | Same |
| F | Toggle fullscreen | Same |
| P | Enter PiP | Same |
| J / K | Previous / next episode (K only with `hasNextEpisode && remoteless`; K also works on the end screen) | Same |
| S | Screenshot | Same |
| C | Toggle subtitles (off, or the first track) | Same |
| SLASH (?) | Shortcuts help panel | Same |
| Reserved (cannot be bound) | BACK, HOME, APP_SWITCH, POWER, ESCAPE, TAB, ENTER, DPAD_CENTER, MENU | — |
| ESCAPE | System back on Android (`escapeLeavesFullscreen = false`) | — |
| Desktop extras (speed ±, audio cycle, sub delay Z/X, Enter skip, L loop, Ctrl+1..6 presets) | **Not available on Android** (`desktopShortcuts = false`) | — |
| Hardware volume keys | System default (volume panel) | Always |
| Headset / BT / media keys (MEDIA_PLAY_PAUSE …) | Only through the MediaSession of PlaybackService | Background playback setting on and the service running; not mapped by PlayerKeymap |

### Counts

- Surfaces: **27** (PLA-01 … PLA-27).
- Action rows in the per-surface "Controls & actions" tables: **89**. The cross-cutting tables add 14 gesture rows and 15 key rows.

---

# Player parity inventory: enhancements, shaders/quality, subtitles, audio/volume

Scope: Enhancements panel and its notices, the shader/quality engine, desktop external mpv (lsfg-vk, mpv-anime-build) and hwdec, subtitles (sheet, style editors, fonts, translation, Link subtitles), audio tracks and volume.
Paths are relative to the repo root. `FP` = `feature/player/src/commonMain/kotlin/app/nijiyome/feature/player/`, `QM` = `core/player-mpv/src/commonMain/kotlin/app/nijiyome/player/mpv/quality/`.
Platform flags: `playerCapabilities` (`FP/PlayerScreenPlatform.kt:60-99`; Android `feature/player/src/androidMain/.../PlayerScreenPlatform.android.kt:387-396`; desktop `feature/player/src/desktopMain/.../PlayerScreenPlatform.desktop.kt:149-165`). `PhoneFrameGen` is non-null only on Android libmpv (`androidMain/.../PhoneFrameGen.android.kt:37-51`; desktop returns null, `PlayerScreenPlatform.desktop.kt:215`). Desktop has no Link client (`FP/NoLinkClient.kt`), so it is always "unpaired".

---

## PLE-01 — Enhancement status chip ("Enhancements: … Tap to change them.")
- **Source**: `FP/EnhancementWords.kt:194-223` (chip UI), `FP/EnhancementChip.kt:20-222` (state model, label, tone). Mounted in `FP/PlayerControls.kt:818` (pointer/desktop top bar chips row), `FP/TouchPlayerUi.kt:234, 327` (touch top bar chips).
- **Platform**: both. PC/Mixed variants only reachable on Android (desktop is never paired). Media3 variant Android only.
- **Entry / exits**: always visible first in the top bar chip row while controls are shown. Tap opens PLE-02 (`Sheet.Enhancements`).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Status chip | AssistChip (tap) | Opens Enhancements panel | Label max 1 line, ellipsized; a11y "Enhancements: %s. Tap to change them." |

- **Data & states** (label parts joined with " · "):
  - Phone RUNNING: "<preset> · <tier>" (+ "FG ×N" when phone FG presents). Tone ACTIVE (primaryContainer), icon AutoAwesome.
  - Phone OFF: lists running extras "Deband", "Sharpening", "Smoothing" (+FG), else "No enhancements". Tone NEUTRAL (outlined) unless FG runs.
  - Phone PAUSED (viewport fills / 4K / size pending): "<asked preset> · <tier> · paused". Neutral.
  - Phone RESTRICTED (reasons SHADER_ERROR, RES_8K, BATTERY_LOW, THERMAL, FRAME_DROPS, PIP, RENDERER_UNSUPPORTED): "<effective> · Restricted" or "Restricted". Tone WARNING (errorContainer) + WarningAmber icon.
  - Media3: "Media3 · no enhancements" (neutral).
  - PC: "PC · Anime4K <h>p + LSFG ×N | LSFG auto | AMD FRC ×2 | no enhancements", "PC · …" before first poll. Computer icon; WARNING if PC falling behind or degraded.
  - Mixed: "PC upscales · Phone FG ×N" / "Phone FG". Devices icon; WARNING if phone FG has a problem.
  - Switching: "Switching to the phone… / PC… / mixed…" with spinner.
- **UX notes**: the only always-visible quality indicator; long labels truncate on phones. "paused" is lowercase while "Restricted" is capitalised. Tone carries meaning; keep the warning state and the icon distinction (PC / Mixed / phone).

## PLE-02 — Enhancements panel ("Enhancements") container
- **Source**: `FP/EnhancementPanel.kt:99-153`, container `FP/EnhancementWords.kt:231-243` (`EnhancementSheet`), wired in `FP/PlayerControls.kt:539-554`.
- **Platform**: both. Side sheet (400 dp) when window ≥ `SIDE_SHEET_MIN_WIDTH_DP`, modal bottom sheet otherwise.
- **Entry / exits**: chip (PLE-01); AutoAwesome icon button in top bar (`PlayerControls.kt:933`, `TouchPlayerUi.kt:393` when not minimal, `TouchPlayerUi.kt:640` minimal bottom bar); overflow menu item "Enhancements" on compact width (`PlayerControls.kt:909-913`, `TouchPlayerUi.kt:362-366`). Exits: Close icon, scrim/swipe dismiss; "Import Lossless.dll in Settings" closes the panel, pauses playback and opens Settings › Player (`PlayerControls.kt:550`).
- **Section order**: Process on (PLE-03) → PC card + profiles (PLE-04/05, PC/Mixed only) → Media3 card (PLE-06) or Upscaling on the phone (PLE-07) or a note → Frame generation (PLE-08, Android libmpv, not in strict PC) → Picture (PLE-09, libmpv only) → Statistics switch.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Close | Icon button | Dismiss panel | — |
| Statistics | Switch row | Show/hide PLE-10 overlay | Hint "Over the video: what was asked, what runs, dropped frames and frame generation." Kept with `rememberSaveable` (this player only, not persisted). |

- **Data & states**: Mixed shows note "The PC already upscales the video: the phone's upscaling is paused and the phone only generates the frames." Strict PC shows "Your phone's enhancements are paused: the PC processes the video." (`EnhancementPanel.kt:131-136`).
- **UX notes**: one long scroll of up to 7 cards; no tabs/anchors. Statistics toggle is buried at the very bottom. Everything applies live; only "Use as default" persists, so the session-vs-default model needs to stay visible.

## PLE-03 — "Process on" section (Phone | PC | Mixed)
- **Source**: `FP/EnhancementPanel.kt:157-212`; rules `FP/ProcessingState.kt:15-155`; texts `FP/ProcessingText.kt:12-46`; switch engine `FP/ProcessingSwitcher.kt:49-222`; VM `PlayerViewModel.kt:2539-2563`.
- **Platform**: both render it. Desktop: PC/Mixed always disabled with "Link a PC first in Settings › Link." and the option is labelled "Phone" on a computer.
- **Entry / exits**: top of PLE-02. Blocked reason "Link a PC first in Settings › Link." points to Settings › Link (text only, no link). "Import Lossless.dll in Settings" text button when Mixed is blocked by missing FG shaders → Settings › Player.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Process on | Segmented (3, full width) | `setProcessingLocation` | Phone / PC / Mixed. Disabled while a switch runs or option blocked (current always enabled). Choosing a blocked one posts a notice with the reason. |
| Cancel | Text button (inline progress row) | Cancel running switch | Shown with spinner + "Switching to …" |
| Import Lossless.dll in Settings | Text button | Close panel, pause, open Settings › Player | Only when Mixed blocked by missing LSFG shaders |

- **Data & states**: per location, label ("On the phone", "On the PC", "Mixed (PC + phone)") + hint ("The phone plays the original source with its own enhancements." / "Your PC enhances the video and sends it; the phone only plays it." / "Your PC upscales the video and the phone generates the frames."), selected one in primary colour. Block reasons (info line): not paired; "Your PC does not answer."; "It is a download on this phone: your PC cannot read it."; "This source cannot be sent to your PC."; "Wait until a source plays."; "Not with the external player."; "This video is from your PC: there is no copy for the phone."; "Needs the MPV engine, not in this build."; "This phone cannot generate frames." + FG reason; "Your PC does not allow it: update Nijiyome on the PC."; "Your PC has no upscaling profile this phone can play.". Timeouts: start 95 s (torrent), renew 20 s, ready wait 20 s polling 500 ms. Location carries to next/previous episode of the same title for this app run (`ProcessingCarry`); default from Settings › Link "Process by default on".
- **UX notes**: the segmented hint duplicates the per-option text listed below it (shown twice). Block reasons appear as small grey info lines under each option; hard to scan. Desktop shows a useless Phone/PC/Mixed control; hide or relabel per platform.

## PLE-04 — "What the PC does" card ("What <PC name> does")
- **Source**: `FP/EnhancementPanel.kt:216-273`; texts `feature/link/src/commonMain/.../LinkProcessingText.kt:25-74`.
- **Platform**: Android (PC / Mixed locations only).
- **Entry / exits**: PLE-02 when location ≠ Phone.
- **Controls & actions**: none (read-only).
- **Data & states**: "Waiting for your PC…" until first status. Rows: Profile; Upscaling ("Anime4K <MODE> (<VARIANT>) → W×H", "No upscaling · W×H", "Anime4K"); Frame generation ("LSFG ×N → fps", "LSFG auto: ×N → fps", AMD FRC, none); Codec ("<encoder> · N Mb/s", hidden on older PCs); Ready ahead ("N s" or "N s · waiting for the player" when throttled). Warnings (red): "Your PC may not keep up with this profile."; degraded "PC lowered the profile (was …)". Refreshed every few seconds.
- **UX notes**: good candidate for a compact stats-like block; warnings only shown here and as chip tone.

## PLE-05 — "Enhancement profile" list (Link profile change)
- **Source**: `FP/EnhancementPanel.kt:227-249`; lists `feature/link/.../LinkProcessingText.kt:80-102` (grouped), `feature/link/.../LinkProfileName.kt:86-140` (radio rows); engine `FP/LinkProfileChange.kt:63-163`; decoder rejection fallback `FP/LinkProfileChange.kt:165-215`; group labels `FP/ProcessingText.kt:49-54`.
- **Platform**: Android (PC / Mixed with profiles).
- **Entry / exits**: PLE-02 under PLE-04. Also reachable from the compact top bar "More options" menu entry for the Link profile per comment in `PlayerControls.kt:808-811` (routes to Enhancements).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Profile row | Radio list row | `setLinkProfile` / `changeLinkProfile`: PC restarts session with this profile at same point | Grouped: "No enhancement", "Upscaling only", "Frame generation only", "Upscaling + frame generation" (Mixed: Upscaling only). Unsupported rows disabled with reason; "may drop frames" note rows stay enabled. |

- **Data & states**: hint "The PC restarts the session with the chosen profile at the same point. Your preferred profile does not change."; inline spinner "Changing profile…" + pill over video (PLE-13). Results → notices (PLE-13). After a decoder rejection (Media3 codes 4001/4003/4005) the session falls back to the previous playable profile or closest playable, else "No enhancement"; Retry never repeats a rejected profile.
- **UX notes**: can be a dozen rows (long radio list); groups help. Selection is session-only but the copy is the only hint of that.

## PLE-06 — "Enhancements need MPV" card (Media3)
- **Source**: `FP/EnhancementPanel.kt:277-289`; `core/player/src/commonMain/.../Quality.kt` `canSwitchToMpv` (~line 210).
- **Platform**: Android (Media3 engine on Phone location).
- **Entry / exits**: PLE-02 when engine is Media3.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Switch to MPV | Button | `switchToMpvByUser()` restarts same source in libmpv at the same position (this playback only), closes panel | Only if libmpv bundled and not a Link session |

- **Data & states**: body "Media3 plays the video as it comes. MPV adds upscaling (Anime4K, FSR, Fidelity), deband, picture settings and frame generation, all on the phone. The video goes on at the same point."; when not possible: "This video cannot move to MPV right now."
- **UX notes**: Picture section (PLE-09) is hidden entirely on Media3, so aspect/zoom here are only via quick aspect cycle (touch UI).

## PLE-07 — "Upscaling on the phone" (preset, quality tier, extras)
- **Source**: `FP/EnhancementPanel.kt:291-392`; labels/hints `FP/EnhancementWords.kt:51-105`; engine `QM/MpvQualityController.kt`, `QM/QualityPlanner.kt`, `QM/ShaderCatalog.kt`; model `core/model/src/main/kotlin/app/nijiyome/model/PlayerQuality.kt`.
- **Platform**: both (libmpv on Phone location). Desktop adds NVScaler and HQ = VL/M networks; ArtCNN disabled everywhere.
- **Entry / exits**: PLE-02. "Use as default" writes Settings (`vm.saveQualityAsDefault`, `PlayerViewModel.kt:1361`).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Preset | FilterChip grid (FlowRow) | `session.setQuality(preset, tier)` live | No upscaling, Anime4K A, A+A, B, C, B+B, C+A, FSR, Fidelity, CAS, (NVScaler desktop), ArtCNN (always disabled). Hint line under the selection; disabled ones list "<preset>: <reason>". |
| Quality | Segmented (small, full width) | `setQuality(preset, tier)` | Fast / Balanced / HQ. Enabled only for tiered presets (Anime4K *, Fidelity). Hint "Anime4K and Fidelity only. HQ needs a strong GPU." |
| Use as default / This is your default | Text button | Saves preset + tier to Settings | Disabled when equal to saved default. Caption "Changes here apply to this playback." |
| Undo | Filled tonal button | `session.undoAutoDowngrade()` | Only when a frame-drop step-down is in force; row shows "<reason> (<from level>)" |
| Sharpening | Segmented (4) | `setSharpen` | Off / Low / Medium / High (CAS 0.0 / 0.0 / 0.5 / 1.0). Low and Off both 0.0 sharpness but Low still adds the CAS pass. |
| Deband | Switch row | `setDeband(enabled)` | Hint "Smooths colour banding in gradients." Params fixed 2/40/20/6 (no in-player editor). |
| Smoothing (interpolation) | Switch row | `setSmoothing` | Hint "Paces the frames to the screen. It is not real frame generation."; with FG active: "Frame generation is on: smoothing is off". |
| Automatic adjustments | Switch row | `setAutoAdjust` | Hint "Lowers the quality when the phone is hot, the battery is low or frames drop." |

- **Data & states**: "Now: <effective level>" line; each active `QualityReason` as warning (restricting) or info line: "Needs the Vulkan renderer, not available in this build", "A shader failed on this GPU: upscaling is off for this video", "8K video: no upscaling or deband", "4K video: no upscaling needed on this screen", "On the PC: …", "Frame generation is on: smoothing is off", "Battery at 20 % or less: upscaling, deband and smoothing off until it charges", "The phone is hot: one quality step lower", "Too many dropped frames: lower quality", "Picture-in-picture: fastest quality", "The video already fills the screen: no upscaling needed", "Waiting for the video size". Automatic rules in the reference section.
- **UX notes**: 11-12 preset chips in a flat grid with a single hint line for the selected one; no comparison or grouping (Anime4K family vs others). Tier disabled silently for FSR/CAS/NVScaler. Low vs Off sharpening are visually identical choices. Strings say "phone" on desktop ("Upscaling on the phone", "The phone is hot").

## PLE-08 — "Frame generation (phone)" card
- **Source**: `feature/player/src/androidMain/kotlin/app/nijiyome/feature/player/PhoneFrameGen.android.kt:88-151`; enums `core/framegen/src/main/kotlin/app/nijiyome/framegen/FgUiContract.kt:20-75, 131-137`; strings `core/framegen/src/main/res/values/strings_frame_gen.xml`.
- **Platform**: Android only, libmpv, location Phone or Mixed.
- **Entry / exits**: PLE-02. "Import Lossless.dll in Settings" → Settings › Player (pauses playback).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Use when playing | Switch row | `fg.setEnabled` | Disabled when ineligible (unless already on). Hint = status text; red when problem. |
| Import Lossless.dll in Settings | Text button | Open Settings › Player | When shaders missing |
| Multiplier | Segmented (small) | `fg.setMultiplier` | Auto / 2× / 3× / 4× / 5×. Hint "Auto picks the one that matches the screen refresh rate and lowers it if the GPU falls behind." Hidden when ineligible. |
| Profile | Segmented (small) | `fg.setProfile` | Saver / Balanced / Quality (+ Custom only shown while in use; editable in Settings). Hint "Saver works at 75 % resolution; Quality uses the full LSFG model and costs about twice as much." |

- **Data & states**: status "On the phone · N×", "Checking this device…", "Available", "Off", or reason (ABI, library, Vulkan 1.3, shaders, PiP, background, battery saver, 8K, HDR passthrough, GPU reset, start failed, PC already does it, no frames from MPV). Live "<in> → <out> fps ×N" (primary titleMedium); "The GPU cannot keep up" warning; "Experimental on this GPU" info. FG failure mid-video posts a toast (PLE-11).
- **UX notes**: multiplier list can be 5 options in a small segmented; Custom profile appears/disappears. Desktop's equivalent (lsfg-vk) lives only in Settings and the external mpv card (PLE-15), not in this panel.

## PLE-09 — "Picture" card (aspect, zoom, image, decoder)
- **Source**: `FP/EnhancementPanel.kt:399-470, 533-551`; mpv mapping `QM/MpvOptions.kt:26-50`; desktop hwdec mapping `core/player-mpv/src/desktopMain/.../DesktopMpvAssets.kt:135-160`.
- **Platform**: both, libmpv only.
- **Entry / exits**: PLE-02.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Aspect | Segmented (small, 5) | `setAspect(mode, zoom)` | Fit / Fill / Stretch / 16:9 / 4:3 |
| Zoom | Slider | `setAspect(aspect, zoom)` live while dragging | `QualityRequest.MIN_ZOOM..MAX_ZOOM`, step 5, shown "N %"; mpv clamps 25..400 |
| Brightness | Slider | `setImage` live | −100..100 step 1, "+N"/"N" |
| Contrast | Slider | same | −100..100 |
| Saturation | Slider | same | −100..100 |
| Gamma | Slider | same | −100..100 |
| Hue | Slider | same | −100..100 |
| Reset | Text button (conditional) | Image neutral + Fit + 100 % | Only when something differs |
| Decoder | Segmented (4) | `setDecoder` | Auto / HW / HW+ / SW. Android hwdec: `mediacodec-copy,mediacodec,no` / `mediacodec-copy` / `mediacodec` / `no`. Desktop: Auto = `auto-safe` (Windows) or Linux validated list, HW = `auto-copy`, HW+ = `auto`, SW = `no`. |

- **Data & states**: "In use: SW/HW/HW+/<raw hwdec>"; hint "HW+ uses the least battery; SW plays almost anything but is the slowest." Changes are session-only (no "use as default" here). The quick aspect cycle in the touch UI (`PlayerControls.kt:197-205`) writes the same aspect.
- **UX notes**: 6 sliders with no individual reset; one global reset hides when neutral. Decoder labels (HW vs HW+) are opaque, and the hint is Android-specific on desktop. Decoder is not really "Picture".

## PLE-10 — Statistics overlay ("Statistics")
- **Source**: `FP/EnhancementStats.kt:32-85`; FG line `PhoneFrameGen.android.kt:62-73`; mounted `FP/PlayerControls.kt:421-428`.
- **Platform**: both (FG line Android only; PC line Android Link only).
- **Entry / exits**: PLE-02 "Statistics" switch only. No shortcut. Touches pass through (no Surface).
- **Controls & actions**: none.
- **Data & states**: top-start, max 380 dp, monospace, black 62 % bg; moves down to 112 dp when controls are visible. Lines: "Asked: …", "Running: …", reasons in amber, "Shaders: a → b → c · FP16" or "none", "Decoder: X (asked Y)", "Dropped frames: N", "Video F fps · display H Hz", "W×H → view W×H", "Renderer: …", "FG: in → out fps ×N · L ms" (amber when overloaded) / "FG: <status>", "PC: <upscale + fg>". libmpv lines hidden on Media3.
- **UX notes**: per-player toggle, resets on new player; no copy/export.

## PLE-11 — Quality / engine / FG notices (flash toast with Undo)
- **Source**: announce filter `FP/EnhancementChip.kt:227-242`; toast `FP/PlayerOverlays.kt:120-212`; text `FP/PlayerOverlays.kt:214-218`; posted `PlayerViewModel.kt:1169`; FG notice `PhoneFrameGen.android.kt:41-49`.
- **Platform**: both (thermal/battery/FG Android; drops/shader/8K both).
- **Entry / exits**: automatic. Top centre, 64 dp below status bar; also shown over end screen.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Undo | Text button in toast | `session.undoAutoDowngrade()` + dismiss | Only frame-drop step-downs; toast lasts 7 s (else 5 s) |

- **Data & states**: announced: Battery low, Thermal (loss and restore "Back to the chosen quality"), Frame drops / Shader error / 8K (loss only). Silent: viewport, size pending, PiP, Link remote, FG-turns-off-smoothing, renderer unsupported, 4K, FP16 precision fallback ("Shaders switched to full precision on this GPU" string exists but never shown). Icons: Check / WarningAmber (non-restore) / ErrorOutline. Related engine notices: "This phone has no hardware decoder for this video: it now plays with libmpv (software), it may be slow", "libmpv could not open this stream: it now plays with Media3, without upscaling", "Frame generation: <reason>".
- **UX notes**: 7 s to reach Undo in a pill; the same Undo also lives in PLE-07, good. Silent downgrades are only explained inside the panel.

## PLE-12 — Process-on switch pill and notices
- **Source**: pill `FP/PlayerControls.kt:415-420, 735-746`; text `FP/ProcessingNoticeText.kt:13-31`, `FP/ProcessingText.kt:40-46`.
- **Platform**: Android (desktop: only the "blocked" notice when tapping PC/Mixed).
- **Entry / exits**: shown while `processing.switchingTo != null`, top centre, even with controls hidden (not while a HUD or profile change shows).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Cancel | Text button in pill | `cancelProcessingSwitch` | Video keeps playing on the old side |

- **Data & states**: success "Now on the phone, at the same point." / "Now your PC processes it, at the same point." / "Now mixed: your PC upscales and the phone generates the frames."; failure "Could not switch: your PC did not answer in time / no connection with your PC / your PC cannot open this source / your PC could not start the video. It goes on where it was."; "Could not play it on the phone: there is no source for it here."; fallback "It plays on the phone: <reason>"; blocked option → block reason text. 5 s.
- **UX notes**: two different cancel affordances (panel row and pill) for the same action; keep both or merge.

## PLE-13 — Link profile change pill and notices
- **Source**: pill `FP/PlayerControls.kt:411-414, 723-733`; texts `FP/PlayerOverlays.kt:174-179, 220-232`.
- **Platform**: Android.
- **Entry / exits**: automatic while a change runs / on result.
- **Controls & actions**: none.
- **Data & states**: pill "Changing profile…" with spinner. Results: "Profile changed: the session continues at the same point.", "You are already using that profile.", "The profile is already changing…", "Could not change the profile: this video does not come from your PC.", failures (timeout "Try again", offline, session ended "Send it again from the PC", PC error "The previous one continues."). Also "The new session did not start: your PC opened it again at the same point.", "Your phone cannot play that profile: back to <profile>" / "…back to the previous one.", "Your phone does not play <A>: your preferred profile is now <B>." Error screen text "Your phone cannot play this profile. Tap Retry to open it with one it can play."
- **UX notes**: several near-identical toasts; failure ones 5 s, others ~flash length.

## PLE-14 — Desktop upscaling shortcuts (Ctrl+1…6) and HUD
- **Source**: `FP/PlayerControls.kt:346-354`; `FP/DesktopPlayerKeys.kt:14-28`; HUD `FP/PlayerVolumeControl.kt:127-130`; listed in shortcuts panel `FP/PlayerExtras.kt:121-126`.
- **Platform**: desktop (`desktopShortcuts`).
- **Entry / exits**: keyboard while player focused.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Ctrl+1 … Ctrl+6 (also numpad) | Keyboard shortcut | `setQuality(preset, current tier)` | 1 No upscaling, 2 Anime4K A, 3 A+A, 4 B, 5 C, 6 FSR. Not rebindable. |

- **Data & states**: HUD pill "Upscaling: <preset>".
- **UX notes**: only 6 of 12 presets reachable; not shown in the panel.

## PLE-15 — External mpv card (desktop, lsfg-vk / mpv-anime-build)
- **Source**: `feature/player/src/desktopMain/kotlin/app/nijiyome/feature/player/ExternalMpvCard.kt:73-265`; `core/player-mpv/src/desktopMain/.../external/LsfgVk.kt:25-193`, `.../external/AnimeBuild.kt:19-305`; settings `feature/settings/src/desktopMain/.../ExternalPlayerSettings.desktop.kt:185-245`.
- **Platform**: desktop. lsfg-vk Linux only.
- **Entry / exits**: replaces the player when mpv runs in its own window (external engine, or frame generation forces it). Exits: Stop/Back → previous screen; Sources → source picker; Frame generation settings → Settings › Player; mpv window close → back (or end screen if a next episode is queued).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Watch without frame generation | Filled tonal button | `vm.useBuiltInPlayer` | Only when FG forced external mpv |
| Frame generation settings | Text button | Settings › Player | Same condition |
| Stop | Button | Leave player | — |
| Sources | Filled tonal button | Open source picker | When sources exist |
| Back | Text button | Leave player | — |
| (Settings) Frame generation | Switch | Enable lsfg-vk | Disabled when layer/DLL/mpv missing; hint names the found DLL or the missing piece |
| (Settings) Multiplier | Segmented | Requested multiplier | Auto / ×2 / ×3 / ×4; fitted to refresh: min(request, floor(Hz/23.976)), clamped 2..4 |
| (Settings) Performance mode | Switch | lsfg `performance_mode` | — |
| (Settings) DLL path | Path field | Lossless.dll / lsfg-vk.dll location | Shown when the layer exists |

- **Data & states**: "Playing in mpv", title, mode line "Your own mpv setup" / "Enhanced mode (mpv-anime-build)" / "Nijiyome's enhancements" + "Frame generation ×N (lsfg-vk)"; "You asked for ×A; a H Hz screen shows ×B"; "Opening mpv…"; time "m:ss / m:ss" + progress bar; "Paused"; torrent line; "Frame generation only works in mpv's own window, so this episode plays there."; error "The enhanced mode could not start, so your own mpv setup is used."; hint "Use mpv's own controls and keys…". Flow scale fixed 0.85; mpv-anime-build v5.6 download (56 MB, SHA-256 pinned) is triggered from Settings.
- **UX notes**: no in-app controls for quality here; the user must understand three config modes from one line of text.

## PLE-16 — Subtitles sheet ("Subtitles")
- **Source**: `FP/PlayerExtras.kt:250-344`; sheet host `FP/PlayerControls.kt:594-622`; delay `core/player/src/commonMain/kotlin/app/nijiyome/player/PlayerTimers.kt:68-98`; VM `PlayerViewModel.kt:2091-2123, 2205, 2470`.
- **Platform**: both. "Load file…" on both (`subtitleFile` true); Android SAF MIME types `application/x-subrip, text/vtt, text/x-ssa, text/plain, application/octet-stream, */*`; desktop file chooser `text/plain` only (`PlayerScreenPlatform.desktop.kt:237`). Secondary track list: native secondary tracks when `session.supportsSecondaryTracks` (libmpv), else external files only.
- **Entry / exits**: "Subtitles" chip in bottom chips row (`PlayerControls.kt:1003-1009`, shown once playing); touch bottom bar Subtitles actions (`TouchPlayerUi.kt:554, 626`). Exits: "Style" → PLE-17; "Load file…" closes sheet and opens picker.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Off | Check row | `selectSubtitle(null)`, close | — |
| <track label> | Check row (per track) | `selectSubtitle(groupIndex)`, close | Engine labels |
| File: <name> | Check row (read-only, selected) | — | Loaded custom file |
| Load file… | List row | File picker → `loadSubtitleFile` (mpv `sub-add`; Media3 parses) | Toast "Subtitles loaded: <name>" / "The subtitle file could not be read" |
| Style | List row | Opens PLE-17 | Hint "Size, background, position and more" |
| Show earlier (−0.1 s) | Filled tonal icon button (−) | `stepSubtitleDelay(-1)` | Step 100 ms, clamp ±60 s |
| Delay value | Text | — | "Delay: +0.3 s" |
| Show later (+0.1 s) | Filled tonal icon button (+) | `stepSubtitleDelay(1)` | — |
| Reset delay | Icon button | `stepSubtitleDelay(0)` | Disabled at 0 |
| Secondary: None / <track> | Check rows | `selectSecondary(groupIndex or external index)` | "This stream has no external track to show as a second subtitle." when none |
| Translate subtitles | Switch row | `setTranslationEnabled` | Only when a provider is configured; supporting text "<Provider> → <LANG>" or the last error |
| Pre-translate the whole track | Text button | `vm.pretranslate()`, close | Only while translation on → PLE-20 |

- **Data & states**: "Simplified subtitles: styled (ASS) subtitles show without their formatting in this engine. Choose mpv in Settings › Player for full styling." (Media3 + ASS); Media3 negative delay hint "With this engine, showing subtitles earlier only works for external or loaded subtitle files."
- **UX notes**: tracks, file loading, style, delay, secondary and translation share one flat list; selecting a primary track closes the sheet, selecting a secondary does not (inconsistent). Delay has no fine/coarse choice or typing. Secondary uses same full track list again (long). Translation provider/target only changeable in Settings.

## PLE-17 — Subtitle style quick panel ("Subtitle style")
- **Source**: `FP/SubtitleStyleEditor.kt:318-386`; preview `:147-170`; host `FP/PlayerControls.kt:555-571`; live preview `PlayerViewModel.kt:2065`.
- **Platform**: both. ASS card only when the engine draws full ASS (`ps.fullAss`, libmpv).
- **Entry / exits**: PLE-16 "Style". "More options" → PLE-18. Side sheet in landscape so subtitles stay visible.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Close | Icon button | Dismiss | — |
| Size | Slider | live preview, save on release | 50–250 %, snapped to 5 % |
| Background | Switch | Toggle box | — |
| Background transparency | Slider | live/save | 0–100 % (= 1 − opacity); disabled without box |
| Vertical position | Slider | live/save | 0–100 % (100 = bottom) |
| ASS subtitle style | Segmented (3) | save | Keep original / Apply mine / Plain text + hint |
| Reset | Text button | `SubtitleStyle()` defaults (all fields) | No confirm |
| More options | Filled tonal button | Open PLE-18 | — |

- **Data & states**: preview card with two sample lines ("This is how your subtitles will look", "Changes show right away"). Every change is saved globally to Settings (not session-only, unlike enhancements).
- **UX notes**: "Reset" here resets every option, including ones not visible in the quick panel. No numeric entry.

## PLE-18 — Subtitle style full editor ("Subtitle style" / "More options")
- **Source**: `FP/SubtitleStyleEditor.kt:389-596` (editor + `SubtitleStyleSheet`), colour picker `:227-288`, font picker `:598-657`; model `core/model/src/main/kotlin/app/nijiyome/model/SubtitleStyle.kt`; mpv mapping `core/player-mpv/src/commonMain/.../MpvSubtitleStyle.kt:32-53`; screen-drawn mapping `core/player/src/commonMain/.../ScreenSubtitleSpec.kt`.
- **Platform**: both. Imported fonts both; installed system fonts desktop only (PLE-19).
- **Entry / exits**: player PLE-17 "More options"; Settings › Player › "Subtitle style" row (`feature/settings/.../SettingsSections.kt:411`). Font import opens the platform file picker (TTF/OTF).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Close | Icon button | Dismiss | — |
| Quick looks | FilterChips (4) | `withPreset` | Default / Outline / Box / Yellow; keep size, position, margins, font, ASS |
| Size | Slider | live/save | 50–250 %, step 5 % |
| Font (bundled) | FilterChips | font id, clears file | Outfit / Space Grotesk / System |
| Imported font | InputChip + delete icon | select / delete file | Name without extension |
| Choose an installed font… / Installed font: X | FilterChip → dialog | PLE-19 | Desktop |
| Import fonts… | Text button | Import TTF/OTF (multiple) | Error "Some files were not valid fonts"; note "On libmpv a newly imported font is used from the next episode." |
| Bold | FilterChip toggle | — | — |
| Italic | FilterChip toggle | — | — |
| Line alignment | Segmented (3) | `sub-justify` | Left / Centre / Right |
| Text colour | Swatches (7) + Custom chip | pick | White, Yellow #FFEB3B, #80DEEA, #A5D6A7, #F48FB1, #FFCC80, Black |
| Custom (text) | FilterChip → R/G/B sliders | live/save | each 0–255 |
| Text opacity | Slider | live/save | 0–100 % |
| Draw an outline | Switch | — | — |
| Thickness | Slider | live/save | 0–6, step 0.25; disabled without outline |
| Outline colour | Swatches (6) + Custom + Opacity | — | Black, #424242, #1A237E, #4A148C, #004D40, White; disabled without outline |
| Background box | Switch | — | hint "A box behind the text" |
| Background transparency | Slider | live/save | 0–100 %; disabled without box |
| Background colour | Swatches (5) + Custom (no opacity slider) | — | Black, #212121, #1A237E, #3E2723, White |
| Shadow distance | Slider | live/save | 0–6, step 0.25; disabled while box on (note explains why) |
| Shadow colour | Swatches (6) + Custom + Opacity | — | Outline palette; default black 50 % |
| Vertical position | Slider | live/save | 0–100 % (note "At 100 the subtitles sit at the bottom of the screen") |
| Bottom margin | Slider | live/save | 0–150 (default 22) |
| Side margin | Slider | live/save | 0–150 (default 25) |
| Line spacing | Slider | live/save | 0–20, step 0.5 |
| Edge softness | Slider | live/save | 0–5, step 0.25 (screen-drawn blur needs Android 12+) |
| ASS subtitle style | Segmented (3) | `sub-ass-override` | Keep original (`no`/`scale`) / Apply mine (`force`) / Plain text (`strip`) |
| Apply the size to ASS too | Switch | `assScale` | Only with Keep original |
| Reset every option | Text button | defaults | No confirm |

- **Data & states**: notes "Applies to libmpv and Media3, also to translated and secondary lines.", "Only libmpv draws ASS styles. Media3 shows them as plain text with your style." Disabled states for outline/box/shadow dependencies.
- **UX notes**: ~25 controls in one scroll; three colour pickers each with swatches + hidden RGB sliders; no per-group reset (Enigmata source had one). Shadow disabled-when-box rule is non-obvious. Sliders show values only in the label.

## PLE-19 — Installed font chooser dialog
- **Source**: `FP/InstalledFontPicker.kt:43-105`.
- **Platform**: desktop (Android lists none, chip hidden).
- **Entry / exits**: chip in PLE-18 font row. Dismiss via Cancel.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Search fonts | Text field (focused on open) | Filter families (all words, case-insensitive) | "No font matches" when empty |
| Family row | List row (rendered in its own font, check on selected) | Select and close | max list height 360 dp |
| Cancel | Text button | Close | No confirm button |

- **UX notes**: fine; no preview of the subtitle with the font before choosing.

## PLE-20 — Pre-translate overlay
- **Source**: `FP/PlayerExtras.kt:216-231`; mounted `FP/PlayerControls.kt:528-530`.
- **Platform**: both (translation configured).
- **Entry / exits**: PLE-16 "Pre-translate the whole track", or mode "Pre-translate" in Settings. Centre card over paused video.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Play now | Text button | `cancelPretranslate` | Remaining lines translate live |

- **Data & states**: "Translating subtitles… N / M" + linear progress. Ends with toast "Subtitles translated" / "Subtitles translated (N lines failed and will be translated live)" / "Couldn't read the full text of this track; it's translated live."

## PLE-21 — Subtitle/translation toasts and desktop subtitle HUD
- **Source**: `FP/PlayerOverlays.kt:150-166`; HUD `FP/PlayerVolumeControl.kt:106-122`; keys `FP/DesktopPlayerKeys.kt:61-97`; keymap `core/player/src/commonMain/.../PlayerKeymap.kt:12-72`.
- **Platform**: toasts both; keys C on both (with keyboard), Z/X/V desktop.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| C | Keyboard shortcut (rebindable) | Cycle subtitle tracks (desktop) / toggle (Android) | HUD "Subtitles: <label>" / "Subtitles: Off" / "No subtitles" |
| Z / X | Keyboard shortcut (desktop, rebindable) | Subtitle delay −/+ 0.1 s | HUD "Delay: ±N.N s" |
| V | Keyboard shortcut (desktop, rebindable) | Cycle audio track | HUD "Audio: <label>" |

- **Data & states**: toasts "Subtitles loaded: X", "The subtitle file could not be read", "Translation failed: <msg>", "Google is limiting translations; retrying in N s" / "The translation service is limiting requests; retrying in N s", "This subtitle track is made of images (PGS or VobSub) and can't be translated. Pick a text track.", ASS simplified notice.

## PLE-22 — Settings › Player: playback rows (PlayerSettingsExtras)
- **Source**: `FP/PlayerSettingsExtras.kt:91-164`; mounted `feature/settings/.../SettingsSections.kt:400`.
- **Platform**: both; Background playback Android only (`backgroundPlayback`).
- **Entry / exits**: Settings › Player (after "Skip intro"). Saves immediately.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Auto-next countdown | Slider | `autoNextCountdownSecs` | 3–30 s, 1 s steps; disabled when auto-next off ("off" text) |
| Stable volume | Switch row | `stableVolume` | "Evens out loud and quiet scenes" |
| Background playback | Switch row | `backgroundPlayback` | Android only |

## PLE-23 — Settings › Player: secondary subtitle language
- **Source**: `FP/PlayerSettingsExtras.kt:166-186`.
- **Platform**: both.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Secondary subtitle | FilterChips (horizontal scroll, single select) | `secondarySubLang` | None, EN, ES, JA, PT, FR, DE, IT, KO, ZH, RU, AR; hint "A second subtitle language shown at the top" |

- **UX notes**: bare ISO codes, no language names; list differs from the translation target list.

## PLE-24 — Settings › Player: subtitle translation
- **Source**: `FP/PlayerSettingsExtras.kt:188-388`; providers/targets `core/player/src/commonMain/.../SubtitleTranslator.kt:87-196`; pacing `core/player/src/commonMain/.../TranslationThrottle.kt:27-123`; labels `FP/PlayerLabels.kt:52-62`.
- **Platform**: both. Key storage needs `SecretStore` ("Not available in this build" otherwise).
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Engine | FilterChips (h-scroll) | provider | Off / Google / DeepL / DeepLX / Azure / OpenAI-compatible. Off → hint "Pick an engine to translate subtitles while you watch" and hides the rest. Google hint "works without a key". |
| Mode | FilterChips (2) | mode | Live ("Each line is translated as it appears") / Pre-translate ("The whole track is translated before playback") |
| Target language | FilterChips (h-scroll) | targetLang | ES, EN, PT, FR, DE, IT, JA, KO, ZH, RU, AR, ID, TR, PL |
| Server URL / Endpoint (optional) | Text field | endpoint | DeepLX, OpenAI (required), Azure (optional) |
| Model | Text field | model | OpenAI only |
| Region | Text field | region | Azure only (required) |
| Save (connection) | Filled tonal button | Save endpoint/model/region together | Enabled when dirty |
| API key status | List row | — | "API key" (DeepL, Azure) / "API key (optional)" (DeepLX, OpenAI); "Key saved" / "No key saved" |
| Remove | Text button | Delete stored key | When saved |
| New key | Password text field | — | error state on failed save |
| Save (key) | Filled tonal button | Store key, clear field | Enabled when non-blank |
| Test / Testing… | Filled tonal button | Translates "Hello! Where are you going?" | "OK: <text>" / "Failed: <msg>"; "the service is limiting requests; try again in a minute"; "Fill in the key, URL or region this engine needs" |
| Translation cache | List row + Clear cache | Clear | "<size> used" |

- **Data & states**: pacing (not user-set): Google free burst 2 then 1 per 1.5 s, keyed providers burst 4 then 1 per 250 ms, exponential back-off 2 s…10 min with Retry-After.
- **UX notes**: two separate Save buttons in one section; target language as bare codes; in-player switch (PLE-16) cannot change provider or language.

## PLE-25 — Settings › Player: keyboard shortcuts editor + key capture dialog
- **Source**: `FP/PlayerSettingsExtras.kt:390-471`; keymap `core/player/src/commonMain/.../PlayerKeymap.kt`.
- **Platform**: both; desktop lists extra actions; PiP row hidden where no PiP.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Action row | List row (tap) | Open capture dialog | Key badge; "conflict" error line |
| Reset | Text button | `PlayerKeymap.reset()` | Enabled when customised |
| Capture area | Key capture (dialog) | Records next key | "Press a key"; reserved keys error; clash list |
| Save / Cancel | Dialog buttons | Bind / close | Save disabled for reserved |

- **Data & states**: defaults Space play/pause, ←/→ seek, F fullscreen, M mute, P PiP, J/K prev/next episode, ↑/↓ volume, S screenshot, C subtitles, / help; desktop extras . / , speed, V audio, Z/X sub delay, Enter skip, L loop. Ctrl+1…6 (PLE-14) not editable. (Only listed here because the file is in scope; primary owner is likely the main-controls inventory.)

## PLE-26 — Audio sheet ("Audio")
- **Source**: `FP/PlayerControls.kt:625-644`; chip `FP/PlayerControls.kt:1010-1019`; touch `FP/TouchPlayerUi.kt:546, 623`.
- **Platform**: both (Media3 and libmpv).
- **Entry / exits**: "Audio" / "Audio · <LANG>" chip once playing; touch bottom bar Audio action. Row tap closes sheet.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| <label · detail> | Check row (per track) | `session.selectAudio(id)`, close | — |
| "Sin audio" | Check row | `selectAudio(AUDIO_TRACK_OFF)` | Selected when none selected |

- **Data & states**: empty "No se detectaron pistas de audio adicionales". When switching to the PC the selected audio stream index is sent to Link (`ProcessingSwitcher.kt:61-62, 129`).
- **UX notes**: "Sin audio" and the empty text are hard-coded Spanish (no string resource, no English); sheet title for Speed is also hard-coded "Velocidad" (`PlayerControls.kt:589`). No language grouping, no default marker.

## PLE-27 — Volume control and volume HUD
- **Source**: `FP/PlayerVolumeControl.kt:52-96`; mounted `FP/PlayerControls.kt:986-987`; keys/platform `FP/PlayerScreenPlatform.kt:105-113`, desktop `PlayerScreenPlatform.desktop.kt:177-180`; HUD `FP/PlayerControls.kt:758-…`.
- **Platform**: desktop inline control (`volumeControls`); Android uses the system volume panel (no in-app slider; gestures in touch UI, out of scope).
- **Entry / exits**: controls row while controls visible.
- **Controls & actions**

| Control | Type | Action | Values/Notes |
|---|---|---|---|
| Mute / Unmute | Icon button | `toggleMute` | Icon VolumeOff when muted or 0 |
| Volume | Slider (120 dp) | `changeVolume(delta)` on release (unmutes) | 0–100; label "Volume N %" |
| M / ↑ / ↓ | Keyboard shortcut | Mute / ±5 % (desktop) / system volume (Android) | Desktop shows HUD; Android shows system panel |

- **Data & states**: polls engine volume every 500 ms so key changes reflect. Stable volume toggle is in Settings and the More sheet.
- **UX notes**: value only applied on release (no live audio feedback while dragging).

## PLE-28 — Subtitle carry-over on Link sessions (behaviour, no UI)
- **Source**: `FP/LinkSubtitles.kt:18-107`.
- **Platform**: Android (PC on Media3, Mixed on libmpv).
- **Data & states**: when the PC takes over, the PC's text tracks are side-loaded (`.ass` on libmpv for ASS sources, else `.vtt`; image tracks skipped). Labels: file title, else language display name, else "#N". Selection keeps the phone's choice (same file URL → same title+language → same language, original position first, forced tracks last), else Settings preferred language, else source default; Off stays Off.
- **UX notes**: invisible logic; must survive a redesign of the subtitle sheet (track identity must stay label+language based).

---

## Enhancements/subtitles reference

### Shader presets (in-player chips, Settings default, Ctrl+1…6)

| Preset (UI name) | id | Chain (Fast / Balanced / HQ) | Hint shown | Tiered | Platform |
|---|---|---|---|---|---|
| No upscaling | off | none | "The video as it comes, no shaders." | — | both |
| Anime4K A | anime4k-a | Clamp, Restore_CNN_{big}, Upscale_x2_{big}, AutoDownscalePre_x2, Upscale_x2_{small} | "For blurry or compressed sources (most web episodes)." | yes | both |
| Anime4K A+A | anime4k-aa | Clamp, Restore{big}, Up{big}, Restore{small}, Down_x2, Up{small} | "Like A with a second pass: sharper, and heavier on the GPU." | yes | both |
| Anime4K B | anime4k-b | Clamp, Restore_Soft{big}, Up{big}, Down_x2, Up{small} | "For slightly blurry sources: touches the lines less." | yes | both |
| Anime4K C | anime4k-c | Clamp, Upscale_Denoise{big}, Down_x2, Up{small} | "For clean sources: upscales and removes noise only." | yes | both |
| Anime4K B+B | anime4k-bb | Clamp, Soft{big}, Up{big}, Down_x2, Soft{small}, Up{small} | "Like B with a second pass." | yes | both |
| Anime4K C+A | anime4k-ca | Clamp, Denoise{big}, Down_x2, Restore{small}, Up{small} | "C, then A: cleans first, then restores." | yes | both |
| FSR | fsr | FSR.glsl | "AMD FidelityFX Super Resolution: light, good for any video." | no | both |
| Fidelity | fidelity | Fast: SSimSuperRes · Balanced: FSRCNNX LineArt + SSimSuperRes · HQ: FSRCNNX + KrigBilateral + SSimSuperRes + SSimDownscaler | "FSRCNNX + KrigBilateral: true to the source, without Anime4K's look." | yes | both |
| CAS | cas | CAS.glsl (sharpen at chosen level, Medium if Off) | "Sharpening only (FidelityFX CAS), no upscaling." | no | both |
| NVScaler | nvscaler | NVScaler.glsl (compute pass) | "NVIDIA Image Scaling: fast upscaling with sharpening, any GPU." | no | desktop only (not offered on Android) |
| ArtCNN | artcnn | — (needs gpu-next + Vulkan compute) | disabled: "Needs the Vulkan renderer, not available in this build" | — | listed disabled on both |

Anime4K network sizes (big/small): Fast S/S, Balanced M/S (default tier), HQ L/M on Android, VL/M on desktop. Extra CAS pass appended when Sharpening ≠ Off (except CAS preset). Anime4K passes use FP16 copies by default (`Anime4KShaderOptimizer` v2); first shader error falls back to full precision silently, a second error turns the chain off for the file.

### Quality levels, automatic downgrades and fallbacks (`QM/QualityPlanner.kt:79-216`, `QM/MpvQualityController.kt`)

| Trigger | Effect | Needs "Automatic adjustments" | Notice (toast) | Platform |
|---|---|---|---|---|
| Unsupported preset (ArtCNN, NVScaler w/o desktop shaders) | Preset off | no | no | both |
| Shader compile error | FP16 → full precision first; then chain + sharpen off for this file | no | yes (off only) | both |
| Link remote processing | Chain, sharpen, deband, smoothing off | no | no | Android |
| 8K source | Chain, sharpen, deband off | no | yes (loss) | both |
| 4K source | Chain, sharpen, deband off | no | no | both |
| Video size unknown | Preset off until known | no | no | both |
| Battery ≤ 20 % unplugged | Everything off | yes | yes (loss + restore) | Android (ThermalWatcher/battery in androidMain) |
| Phone FG active | Smoothing off | no | no | Android |
| Thermal (status ≥ SEVERE or headroom ≥ 0.95; exit status < MODERATE and headroom < 0.80) | One tier down, never below Fast, never off | yes | yes (loss + restore) | Android |
| Picture-in-picture | Tier capped to Fast | no | no | both |
| Frame drops (8 s window, ≥ 12 drops and > 15 % of expected frames, sampled every 2 s) | Step: tier −1 → chain off → extras off; session-only | yes | yes, with Undo (7 s) | both |
| Video already fills view (scale ≤ 1) | Upscaler passes dropped (Fidelity keeps Krig/SSimDown) | no | no | both |
| Undo | Clears drop ceiling, no further drop step-down until next file | — | — | both |

Other engine fallbacks with notices: no HW decoder → libmpv software; libmpv cannot open → Media3 without upscaling (Android).

### Desktop-only enhancement options
- lsfg-vk frame generation (Linux, external mpv window): enable, multiplier Auto/×2/×3/×4 (fitted to refresh at 23.976 fps source, 2..4), performance mode, DLL path; flow scale fixed 0.85, pacing vsync. Detects layer manifest and `lsfg-vk.dll` / `Lossless.dll`.
- mpv-anime-build v5.6 "Enhanced mode" for the external mpv (download on request, pinned SHA-256, sanitised mpv.conf).
- Hardware decoding: system libva preferred over bundled (`NIJIYOME_SYSTEM_LIBVA=0` disables); Settings shows libva source and VAAPI codecs (H.264, HEVC, HEVC Main10, AV1, VP9).

### Subtitle style options (`core/model/.../SubtitleStyle.kt`)

| Option | Control | Range / values | Default | mpv property |
|---|---|---|---|---|
| Size | Slider | 50–250 %, step 5 % | 100 % | sub-scale |
| Font | Chips / imported / installed (desktop) | Outfit, Space Grotesk, System, imported TTF/OTF, installed family | Outfit | sub-font |
| Bold | Toggle chip | on/off | off | sub-bold |
| Italic | Toggle chip | on/off | off | sub-italic |
| Line alignment | Segmented | Left / Centre / Right | Centre | sub-justify (+ sub-ass-justify when not Original) |
| Text colour | 7 swatches + RGB 0–255 | ARGB | White | sub-color |
| Text opacity | Slider | 0–100 % | 100 % | sub-color alpha |
| Outline | Switch | on/off | on | — |
| Outline thickness | Slider | 0–6, step 0.25 | 2.5 | sub-outline-size |
| Outline colour + opacity | 6 swatches + RGB + slider | ARGB | Black | sub-outline-color |
| Background box | Switch | on/off | on | sub-border-style=background-box |
| Background transparency | Slider | 0–100 % (opacity 1−x) | 40 % (opacity 0.6) | sub-back-color alpha |
| Background colour | 5 swatches + RGB | RGB | Black | sub-back-color |
| Shadow distance | Slider (only without box) | 0–6, step 0.25 | 0 | sub-shadow-offset |
| Shadow colour + opacity | 6 swatches + RGB + slider | ARGB | Black 50 % | sub-back-color (no box) |
| Vertical position | Slider | 0–100 % (100 = bottom) | 100 | sub-pos |
| Bottom margin | Slider | 0–150 | 22 | sub-margin-y |
| Side margin | Slider | 0–150 | 25 | sub-margin-x |
| Line spacing | Slider | 0–20, step 0.5 | 0 | sub-line-spacing |
| Edge softness | Slider | 0–5, step 0.25 | 0 | sub-blur |
| ASS style | Segmented | Keep original / Apply mine / Plain text | Keep original | sub-ass-override no/scale, force, strip |
| Apply the size to ASS too | Switch (Original only) | on/off | off | sub-ass-override=scale |
| Quick looks | Chips | Default; Outline (no box, outline 3, shadow 1.5); Box (box 85 %, no outline); Yellow (#FFEB3B, no box, outline 3, shadow 1) | — | — |

Other subtitle settings: delay step 0.1 s, range ±60 s (sheet buttons, Z/X on desktop, reset); secondary track (in-player) and secondary language (Settings: None + 11 codes); translation providers Google (no key), DeepL (key; free/pro endpoints), DeepLX (URL), Azure (key + region, optional endpoint), OpenAI-compatible (URL + model, optional key); modes Live / Pre-translate; 14 target languages; translation cache with clear; file loading (Android SRT/VTT/SSA/ASS/any, desktop chooser filtered to text/plain). Pre-0.4.1 SharedPreferences extras migrate once (`core/player/.../LegacySubtitleExtras.kt`).

### Counts
- Surfaces: 28 (PLE-01 … PLE-28).
- Action rows in Controls & actions tables: 123.

---

# Manga & Novel functionality parity inventory

Repo: `Redesign-sparkles` worktree. Paths are relative to the repo root. Abbreviations:
`fm/` = `feature/manga/src/commonMain/kotlin/app/nijiyome/feature/manga/`, `fn/` = `feature/novel/src/commonMain/kotlin/app/nijiyome/feature/novel/`,
`model/` = `core/model/src/main/kotlin/app/nijiyome/model/`. Labels are the English values in `composeResources/values/strings.xml`
(manga: `feature/manga/...`, novel: `feature/novel/...`, shared: `core/ui/.../values/strings.xml`).
"Both" = commonMain code, so Android and desktop.

Settings models (every option, default, range):

- `ReaderSettings` (`model/Manga.kt:166-208`): `mode` PAGED|VERTICAL|WEBTOON (default PAGED); `fit` WIDTH|HEIGHT|BOTH|NATURAL (WIDTH);
  `direction` LTR|RTL (RTL); `doublePage` (false); `maxWidthDp` 0 = no limit, sanitized 0..4000, UI slider 0..2000 in 50 dp steps (0);
  `pageGapDp` 0..64 (0); `background` black|white|gray|theme (black); `brightness` -1 = system, else 0..1 (-1); `cropBorders` (false);
  `eagerLoading` (false); `prefetchPages` 1..20 (5); `infiniteScroll` (true); `autoScrollSpeed` 0 = off, sanitized 0..2000, UI 0..600 in
  20 px/s steps (0); `fullscreenOnOpen` (true); `hideChromeOnTap` (true); `showProgress` (true); `zoomShortcuts` (true); `keymap`
  (overrides only, empty = defaults).
- `NovelReaderSettings` (`model/Novel.kt:65-110`): `font` sans|serif|georgia|lora|dyslexic|system|custom (+ desktop `mono`, `family:<name>`)
  (serif); `customFontPath` (null); `sizeSp` 10..40 (18); `lineHeight` 1.0..2.5 (1.6); `paragraphSpacingDp` 0..48 (12); `maxWidthDp` 0 =
  no limit, sanitized 0..4000, UI 0..1600 in 50 dp steps (720); `background` theme|sepia|black|white|gray (theme); `textAlignJustify` (false);
  `continuousScroll` (true); `showProgressBar` (true); `fullscreenOnOpen` (true); `keepScreenOn` (true).
- `TtsSettings` (`model/Novel.kt:125-147`): `engine` SYSTEM|OPENAI|ELEVENLABS|OPENAI_COMPATIBLE|KITTEN (desktop only) (SYSTEM); `voice`
  (null = engine default); `speed` 0.5..2.0 (1.0); `endpoint` (""); `model` (""); `highlight` (true); `pitch` 0.5..2.0 (1.0, desktop
  system voice only). API keys live in `SecretStore` (`SecretKeys.ttsApiKey(engine.id)`), never in settings. `TtsState` IDLE|PREPARING|
  SPEAKING|PAUSED.
- `MangaPrefs` (`core/manga/.../ReaderKeymap.kt:79-91`, file `filesDir/manga/prefs.json`): `languages` (extra preferred chapter
  languages), `preferredSource` (global, no UI found), `preferredSourceByMedia`, `adultGateAccepted`, `matureAccepted` (per title).
- Outside these modules but consumed here: `AppSettings.mangaTitleLanguage` ROMAJI|ENGLISH|NATIVE (also used by novels),
  `AppSettings.showAdult` (false), `AppSettings.matureContent` SHOW|BLUR|WARN (WARN) (`model/Settings.kt:76,105,147`; edited in Settings ›
  Metadata, not in these modules).
- Reader keymap (`core/manga/.../ReaderKeymap.kt:24-45`): 12 actions, defaults in the cross-cutting table. Reserved keys BACK, ESCAPE, HOME.
- Narration voices: OpenAI fixed list `alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse, marin, cedar` (legacy
  models `tts-1`/`tts-1-hd`: first 9 without ballad/verse/marin/cedar) (`core/novel/.../CloudTts.kt:112-115`); ElevenLabs `GET /v1/voices`;
  compatible server `GET <base>/audio/voices`; default models `gpt-4o-mini-tts`, `eleven_multilingual_v2`, placeholder `kokoro`.
  KittenTTS voices `Bella, Jasper, Luna, Bruno, Rosie, Hugo, Kiki, Leo` (en-US) (`core/novel/src/desktopMain/.../kitten/KittenModel.kt:42`),
  one model "KittenTTS nano 0.8 (int8)", ~28 MB (`kitten/Kitten.kt:83`). Desktop system voice: SAPI via PowerShell (Windows),
  speech-dispatcher (Linux), else espeak-ng to file (`tts/DesktopVoiceCatalog.kt`). Android system voice: `TextToSpeech` voices listed
  as "<locale> · <name>" (`core/novel/src/androidMain/.../NarratorImpl.kt:125-133`).

---

## MNG-01 — Manga home (catalog)

- **Source**: `fm/MangaHomeScreen.kt:103-205` (screen), `:342-475` (HomeContent), `:494-519` (HeroSlide, ContinueCard), `:521-543` (still
  loading hint); `fm/MangaUi.kt:63-126` (MangaCard, MediaRowSection, skeleton); VM `fm/MangaHomeViewModel.kt:93-179`.
- **Platform**: both. `onBack` is null on wide windows (rail destination) so no back arrow there.
- **Entry / exits**: Entry: `Route.Manga(query)` from Home quick chip "Manga", Search type chip (passes `initialQuery`), nav rail, command
  palette "Ir a Manga", You. Exits: card/hero → Manga Details (MNG-06); Continue-reading card → Manga Reader (MNG-12) or Details when
  list-only; followed-update card → Reader; genre chip → stays here in search mode (MNG-02); back → previous.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Back arrow | icon button | Navigate back (hidden on wide windows) | both |
| Refresh | icon button (top bar) | `repo.refreshHome()` reloads all rows | both |
| Search field "Search manga" | text field | Edits query; IME Search submits; typing alone does not search | both |
| Clear search (X) | icon button (trailing, only when filters active) | Resets query + all filters | both |
| Filter chips row | chip → dropdown | See MNG-02 | both |
| Hero carousel slide | tap / card | Opens Details | both |
| Hero "View" | button | Opens Details | both |
| Hero carousel paging | swipe / auto carousel (`HeroCarousel`, core/ui) | Changes slide | both |
| "Continue reading" card (saved page) | card tap | Opens Reader at saved chapter/page | both |
| "Continue reading" card (list-only) | card tap | Opens Details (source picked there) | both |
| "New chapters of what you read" card | card tap | Dismisses the update then opens Reader at that chapter | both |
| Trending / Popular / Top rated row cards | card tap (horizontal LazyRow) | Opens Details | both |
| Row error "Retry" | button (compact error) | Refreshes home | both |
| Genres chips | suggestion chip (FlowRow) | Starts a search filtered by that genre (MNG-02) | both |
| 18+ zone "Hide" | text button (section header) | Resets gate (`adultGateAccepted=false`) | both |
| "Show 18+ zone" | tonal button | Opens adult gate dialog (MNG-03) | both |
| 18+ row card | card tap | WARN: mature dialog once per title (MNG-04); BLUR: first tap reveals, second opens; SHOW: opens Details | both |

- **Data & states**: hero (banner, title, up to 3 genres); continue line "Ch. N · page X / Y" + progress bar, or "Chapter N"; updates line
  "New: chapter N"; cards show cover, title (by manga title language), subtitle. Loading: skeleton after 150 ms; hero spinner; "Still
  loading…" at 6 s and "Still loading: the catalogs are slow to answer." at 20 s. Row error: compact ErrorState + Retry; empty rows hidden.
  18+ states: `showAdult` off → text "Adult titles are off. Turn on "Show adult content" in Settings › Metadata…"; gate not accepted →
  button; loading → skeleton; empty → "Nothing found"; error → ErrorBlock + Retry.
- **UX notes**: the 18+ disabled message names Settings › Metadata but offers no link. Followed-update cards dismiss on tap with no undo and
  no explicit dismiss control. `onOpenAccounts` is passed to HomeContent but unused. Novel home has no Top rated row and no 18+ zone
  (inconsistency).

## MNG-02 — Manga search filters and results grid

- **Source**: `fm/MangaHomeScreen.kt:207-273` (SearchHeader), `:275-293` (FilterDropdown), `:295-340` (SearchResultsGrid); filters model
  `fm/MangaHomeViewModel.kt:31-56`, `setFilters`/`search`/`loadMore` `:104-153`.
- **Platform**: both.
- **Entry / exits**: shown in place of the home feed when `filters.active` (query, origin, status, genre or year set). Entry: typing + IME
  Search, any filter pick, a genre chip, Search type chip `initialQuery`. Exit: result card → Details; Clear (X) → home feed.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Origin | filter chip → dropdown | Any, Japan, Korea, China (label table also has Taiwan, not offered) | both |
| Status | filter chip → dropdown | Any, Publishing, Finished, Hiatus, Cancelled, Upcoming | both |
| Genre | filter chip → dropdown | Any + every genre from the home catalog | both |
| Year | filter chip → dropdown | Any, (current year + 1) down to 1950 (~77 items) | both |
| Sort | filter chip → dropdown | Popularity, Trending, Score, Newest, Title, Favourites (Popularity becomes Relevance when a query is typed) | both |
| Result card | card tap (adaptive grid, 120 dp) | Opens Details | both |
| Infinite load | scroll near end (6 items) | Loads next page | both |
| Load-more error "Retry" | text button | Retries next page | both |
| Error block "Retry" | button | Re-runs search | both |

- **Data & states**: chip label "<Label>: <value>" when set; cards subtitle "year · status". Loading → LoadingState after delay; error →
  "Could not load" + message + Retry; empty → "Nothing found" / "Try another title or fewer filters."; loading more → linear progress.
- **UX notes**: Year and Genre are very long single-select dropdowns (a year range slider / searchable picker would fit). Single-select
  genre only. Changing only Sort with no other filter calls `clearSearch()` which resets filters to defaults, so the Sort pick is lost
  silently (`MangaHomeViewModel.kt:106-115`). Filters search instantly but the text query needs Enter: inconsistent. Active filters
  have no "clear all" except X in the field.

## MNG-03 — 18+ zone gate dialog

- **Source**: `fm/MangaHomeScreen.kt:180-194`; `acceptAdultGate` `fm/MangaHomeViewModel.kt:155-160`.
- **Platform**: both.
- **Entry / exits**: "Show 18+ zone" in MNG-01. Exit: back to Home with zone loaded or unchanged.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "I am 18 or older" | text button (confirm) | Persists gate accepted, loads the 18+ zone | both |
| "Cancel" | text button | Closes | both |
| Outside tap / back | dismiss | Closes | both |

- **Data & states**: shield icon, title "Adult content", message "This zone lists titles for adults only. Confirm that you are of legal
  age to see it."
- **UX notes**: accepted once forever (until "Hide"); no indication in the zone that it can be re-locked besides the "Hide" button.

## MNG-04 — Mature title warning dialog

- **Source**: `fm/MangaUi.kt:138-147` (MatureWarningDialog); used at `fm/MangaHomeScreen.kt:195-204` and `fm/MangaDetailsScreen.kt:215-217`.
- **Platform**: both.
- **Entry / exits**: WARN mode only. Home 18+ card tap for a not-yet-accepted title; Details opening an `isAdult` title. Exit: Details
  (confirm) or stays (Home) / navigates back (Details dismiss calls `onBack`).
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "Open" | text button | Adds id to `matureAccepted`, opens / shows the title | both |
| "Cancel" / dismiss | text button / outside tap | Home: closes; Details: leaves the Details screen | both |

- **Data & states**: "Adult title" / "This title is marked for adults. Do you want to open it?". BLUR mode instead blurs covers (24 dp)
  with "Tap to show" until tapped (cards: `MangaUi.kt:63-91`; Details header `MangaDetailsScreen.kt:377-410`).
- **UX notes**: per-title acceptance cannot be reviewed or revoked from these screens. Novels never apply WARN/BLUR (no equivalent in
  `fn/`).

## MNG-05 — Library › Manga tab

- **Source**: `fm/MangaLibraryTab.kt:67-209` (VM 67-93, tab 99-129, ReadlistContent 135-209); mounted by
  `feature/library/.../LibraryScreen.kt:193`.
- **Platform**: both.
- **Entry / exits**: Library screen, tab MANGA. Exits: row / continue card → Manga Details; empty-state "Link AniList" → Settings › Accounts.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "All" | filter chip | Shows every status | both |
| Status chips "Reading · n", "Plan to read · n", "Completed · n", "On hold · n", "Dropped · n", "Rereading · n" | filter chip (single select, default Reading) | Filters the list | both |
| "Sync with AniList" | icon button (only when AniList linked; disabled while syncing) | Pulls AniList list, retries pending pushes | both |
| Auto sync | automatic on open when linked | Same as above | both |
| Continue reading card | card tap (LazyRow) | Opens Details (not the Reader) | both |
| List row | list row tap (needs AniList id) | Opens Details | both |
| "Link AniList" | button (empty state, only when not linked) | Settings › Accounts | both |

- **Data & states**: row = cover, title, "<status> · x / y chapters", progress bar when total known; continue line "Ch. N · page X / Y" or
  "Chapter N". Syncing → top linear progress. Loading (null) → blank. Empty → "Your manga list is empty" + linked/unlinked message.
  Status with no entries → "Nothing in this list yet."
- **UX notes**: continue cards here open Details while the same cards on Home open the Reader (inconsistent). No sort, no search, no
  long-press actions on rows. Sync gives no success/failure feedback (`runCatching` swallows errors).

## MNG-06 — Manga Details screen

- **Source**: `fm/MangaDetailsScreen.kt:136-255` (screen), `:257-375` (DetailsBody), `:377-410` (Header), `:412-470` (ActionsRow),
  `:474-481` (Synopsis), `:483-547` (Facts, ScoreBox, relation labels), `:647-662` (rankings); VM `fm/MangaDetailsViewModel.kt`.
- **Platform**: both (detail pane of a list-detail scene on wide windows).
- **Entry / exits**: `Route.Details(MANGA, id)` from any manga card (Home, search, Library, Series, Recommendations, social feeds, Search).
  Exits: back; Read button → Reader; anime adaptation row / anime relation → anime Details; manga relation / recommendation → another
  Manga Details (replaces top Details); genre chip → global `Route.SearchFor(genre)`; empty sources → Extensions; snackbar → Downloads /
  Sources sheet.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Back | icon button (top bar) | Navigate back | both |
| Blurred banner/cover "Tap to show" | tap (BLUR mode) | Reveals cover | both |
| "Read" / "Continue" / "Read chapter N" / "Continue chapter N" | button (spinner while resolving) | Resumes saved page if it wins over list progress, else opens the next unread chapter with the best source (`bestChapter`), else first chapter | both |
| Status button ("Add to reading list" or current status) | tonal button → dropdown | See MNG-07 | both |
| Progress "−" / "+" | outlined buttons (only when on list) | Decrement / increment chapters read | both |
| Synopsis | tap to expand / "More" / "Less" | Toggles 5-line clamp | both |
| Genre chips | assist chip | Opens global Search filtered by genre | both |
| Tag chips ("name rank%") | suggestion chip (no-op on click) | None | both |
| "Show spoiler tags" / "Hide spoilers" | text button | Toggles spoiler tags | both |
| Anime adaptation row | list row | Opens anime Details | both |
| Series card (relation subtitle) | card tap (LazyRow) | Anime → anime Details; manga → Manga Details | both |
| Characters row | display only (LazyRow) | None | both |
| Recommendations card | card tap | Opens Manga Details | both |
| Chapters section | — | See MNG-08 | both |

- **Data & states**: header pills (format Manga / One shot / Light novel, status, origin, year, 18+), title + alternative title;
  Information card: Authors (roles), Chapters, Volumes, Status, Publication, Publishers, Serialization, Licensed (MangaUpdates), Started,
  Ended, Origin, Source; score boxes AniList score %, Mean score %, MangaUpdates score, Members, Favourites; up to 4 ranking pills
  ("#N highest rated all time" etc.). Loading → spinner after delay; error → "Could not load" + Retry; WARN gate → MNG-04 instead of body.
- **UX notes**: tag chips look tappable but do nothing. Genre chip goes to the global (anime-oriented) search while the Home genre chip
  filters manga: inconsistent destination. `onOpenAccounts` param unused. Status/progress failures only show a generic "Could not load"
  snackbar.

## MNG-07 — Details › Reading-list status menu

- **Source**: `fm/MangaDetailsScreen.kt:436-459`; statuses `:472`.
- **Platform**: both.
- **Entry / exits**: status tonal button in MNG-06.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Reading / Plan to read / Completed / On hold / Dropped / Rereading | dropdown items (check icon on current) | `setReadStatus` (local + AniList when linked) | both |
| "Remove from list" | dropdown item (only when on list) | Removes entry | both |

- **Data & states**: current status checked.
- **UX notes**: six statuses in a menu fit a segmented/chip picker in a sheet; no score editing here.

## MNG-08 — Details › Chapters section (sources, toolbar, list)

- **Source**: `fm/MangaDetailsScreen.kt:551-631` (section), `:633-645` (source chip label), `:664-672` (problem labels), `:674-705`
  (ChapterToolbar), `:707-752` (ChapterRow); VM `fm/MangaDetailsViewModel.kt:135-263`; chapter logic `core/manga/.../ChapterLogic.kt:87-135`.
- **Platform**: both.
- **Entry / exits**: inline in Details. Exits: chapter row → Reader (offline if downloaded); "Extensions" → Extensions; "Sources and
  languages" → MNG-10.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Refresh | icon button (section header) | Reloads chapters of the current source (network refresh) | both |
| Clear cache | icon button (DeleteSweep) | Forgets matches and chapter lists, searches sources again | both |
| Select chapters / Close | icon button (toggle) | Enters / leaves selection mode (MNG-09) | both |
| Source chips "<ext> · count · LANGS · reliability%" | filter chip (single select; disabled when the source did not match) | Switches source, resets language filter | both |
| "Sources and languages" | assist chip | Opens MNG-10 | both |
| Filter field "Filter by number, title or group" | text field | Filters chapter list live | both |
| "Oldest first" / "Newest first" | assist chip (toggle) | Flips order | both |
| "Group by volume" | filter chip (toggle) | Groups list under "Volume N" / "No volume" headers | both |
| "Language" | filter chip → dropdown (only when > 1 language) | Any + each language display name | both |
| Chapter row | list row tap | Opens Reader (selection mode: toggles checkbox) | both |
| Mark read / not read | icon button (check circle; hidden in selection mode or without number) | Sets list progress to max(P,n) / min(P,n-1); adds as Reading; reopens Completed | both |
| "Extensions" (no sources) | button (empty state) | Opens Extensions | both |
| Retry | button (sources or chapters error) | Reloads | both |

- **Data & states**: "N chapters" count; row "Chapter N · title", supporting "group · LANG · date · Volume N"; read rows dimmed; downloaded
  icon. Sources loading → spinner; sources error → ErrorBlock; no sources → "No manga sources" / "Install a manga extension…" + Extensions;
  chapters loading → spinner; chapters error → ErrorBlock; no source matched at all → "None of your sources found this title. Try another
  source in Sources and languages or install a manga extension."; current source unmatched → "This source did not find the title.";
  filter empty → "No chapters match."; source problem labels "no results" / "timed out" / "failed".
- **UX notes**: source chip label packs 4 facts in plain text (`ext · 123 · EN/ES · 87%`); reliability % is unexplained. Refresh vs
  Clear cache are adjacent unlabeled icons with very different cost. Read toggle is a small icon only. No long-press to start selection,
  no range select.

## MNG-09 — Details › Chapter selection mode / download bar

- **Source**: `fm/MangaDetailsScreen.kt:198-209` (BottomAppBar), `:732` (checkbox); VM `toggle`/`selectAllShown`/`downloadSelected`
  `fm/MangaDetailsViewModel.kt:201-221`.
- **Platform**: both.
- **Entry / exits**: Select chapters icon (MNG-08). Exit: Close icon, or after Download (selection cleared) → snackbar (MNG-11).
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Row checkbox / row tap | checkbox / list row | Toggles chapter | both |
| "Select all" | text button | Selects all chapters currently shown (respects filter) | both |
| "Download" | button (disabled when none) | Queues selected chapters for download | both |

- **Data & states**: "N selected" (plural).
- **UX notes**: no "deselect all", no "select unread"/range; Select all silently depends on current filter.

## MNG-10 — Details › "Sources and languages" sheet

- **Source**: `fm/MangaDetailsScreen.kt:242-254` (mount), `:756-812` (SourceSheet), `:814` (LANGUAGE_CHOICES); VM
  `setPreferredSource`/`setLanguages` `fm/MangaDetailsViewModel.kt:157-170`.
- **Platform**: both. `AdaptiveSheet`: bottom sheet on compact windows, side sheet on medium+ (desktop, tablets).
- **Entry / exits**: "Sources and languages" chip (MNG-08); "Sources and languages" action of the no-source snackbar. Exit: dismiss;
  selecting a source closes it; "Extensions" → Extensions.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Preferred languages chips: English, Spanish, Portuguese, French, German, Italian, Russian, Indonesian, Vietnamese, Thai, Japanese, Korean, Chinese, Arabic, Turkish, Polish | filter chips (multi-select) | Adds/removes extra preferred language; reloads sources | both |
| "Automatic" | list row (check when active) | Clears per-title preferred source | both |
| Source row | list row tap (disabled when unmatched) | Selects source for this view and closes | both |
| "Prefer" | text button per source | Sets this source as preferred for the title | both |
| "Extensions" | text button | Opens Extensions | both |

- **Data & states**: hint "Chapters in these languages come first; the app language and English are always added."; "Best source by
  language and reliability."; row supporting text = source chip label or "This source did not find the title."; current source row tinted;
  preferred has check icon.
- **UX notes**: language chips have no order (but priority "best first" matters in the model); "Select" vs "Prefer" on the same row is
  subtle. Global `MangaPrefs.preferredSource` has no UI.

## MNG-11 — Details snackbars

- **Source**: `fm/MangaDetailsScreen.kt:161-183`.
- **Platform**: both.
- **Entry / exits**: VM events.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "%d chapters queued for download" + "Downloads" | snackbar action | Opens Downloads (Manga tab) | both |
| "None of your sources found this title." + "Sources and languages" | snackbar action | Opens MNG-10 | both |
| "No chapter to open yet." | snackbar | Info | both |
| "Could not load" | snackbar | Any failure (status, progress, download) | both |

- **Data & states**: as listed.
- **UX notes**: the failure snackbar never says what failed.

## MNG-12 — Manga Reader: page canvas (modes, gestures, states)

- **Source**: `fm/MangaReaderScreen.kt:147-417` (screen), `:221-227` (background colours), `:229-269` (chrome/zoom/escape/actions),
  `:271-288` (keys), `:290-359` (states, tap zones), `:361-375` (dim overlay, page counter), `:502-577` (ZoomBox), `:581-650`
  (PagedReader), `:652-747` (ScrollReader), `:749-814` (PageImage), `:816-838` (PagesError, EndCard); window helpers in
  `core/ui/.../platform/` (KeepScreenOn, ScreenBrightness, SystemBars, ScreenOrientation); VM `fm/MangaReaderViewModel.kt:70-334`;
  images `fm/ReaderImages.kt`, `feature/manga/src/androidMain/.../ReaderImages.android.kt`, `.../desktopMain/.../ReaderImages.desktop.kt`.
- **Platform**: both, with differences: Android brightness sets the window brightness, desktop draws a black overlay (clamped 20..100 %);
  Android "fullscreen" hides system bars while chrome is hidden, desktop toggles window fullscreen (`ReaderWindowFullscreen`); landscape
  auto-hide and compact bar only on Android (`isLandscapeOrientation()` is always false on desktop); Esc handling and RTL key mirroring
  desktop only.
- **Entry / exits**: `Route.MangaReader(args)` from Details chapter row / Read button, Home continue/updates cards, Downloads, extension
  provider item pages. Next/previous chapter replace the top route. Exit: back / Esc (saves progress).
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Left third tap (paged, not zoomed) | tap zone | Previous page (LTR) / next page (RTL) | both |
| Right third tap (paged, not zoomed) | tap zone | Next page (LTR) / previous page (RTL) | both |
| Centre third tap (paged) / any tap (vertical, webtoon, or zoomed) | tap zone | Toggles bars only if "Tap hides the controls" is on | both |
| Horizontal swipe (paged) | swipe (HorizontalPager, reversed in RTL; disabled while zoomed) | Turns spread | both |
| Vertical scroll (vertical / webtoon) | swipe / mouse wheel (LazyColumn) | Scrolls pages; reaching the end appends next chapter when Infinite scroll is on | both |
| Double tap | double-tap | 1x ↔ 2.5x zoom centred on the tap | both |
| Pinch | pinch-zoom | Zoom 1x..5x | both (touch) |
| One-finger drag while zoomed | drag | Pan (clamped) | both |
| Ctrl + mouse wheel | mouse wheel + modifier (only with "Zoom shortcuts") | Zoom ×1.1 / ×0.9 | both (desktop practical) |
| Keyboard / remote keys | keyboard shortcut | See cross-cutting shortcut table | both |
| Esc | keyboard | Close open sheet → leave fullscreen → exit reader | desktop |
| System Back | system | Save progress + exit (sheet closes first via sheet back handling) | android |
| End card "Next chapter" | button (hidden in scroll modes when Infinite scroll is on) | Opens next chapter | both |
| Page failed "Retry" | outlined button | Reloads that page image | both |
| Page failed "Switch source" | button | Opens MNG-17 | both |
| Chapter failed "Retry" | outlined button | Reloads chapter pages (or appended chapter) | both |
| Chapter failed "Switch source" | button | Opens MNG-17 | both |

- **Data & states**: modes Paged (pager, optional double-page spreads when width > height, end card as extra page), Vertical (list with
  page gap), Webtoon (list, gap forced 0, fit forced Width). Fit Width / Height / Screen (both) / Natural. Max width. Crop borders
  transformation. Background black / gray (#424242) / white / theme. Page counter "x / y" bottom-centre when bars hidden and "Show page
  progress" on. Loading: centre spinner (first chapter), per-page spinner, 240 dp spinner row for appended chapter. Errors: "The pages
  could not be loaded" + message; "Page N failed to load". End: "End of chapter", "This is the last chapter available." Chapter header
  "Chapter N" between appended chapters. Progress saved every 3 s, at last page, on chapter change, on exit; next chapter prefetched at
  70 %; lazy warm range = `prefetchPages` ahead, eager = whole chapter. Screen kept on while reading. A failing page lowers source
  reliability and preloads alternatives.
- **UX notes**: tap zones are invisible and not configurable (fixed thirds); no tap-zone overlay/help. Webtoon silently ignores Fit and
  Gap settings. Desktop brightness below 20 % has no effect though the slider goes to 5 %. Desktop paged mode has no explicit mouse-wheel
  page turn (not found in code; only Ctrl+wheel zoom). Zoom resets are not exposed as a control. Auto-scroll state is not persisted.

## MNG-13 — Manga Reader top bar + overflow menu

- **Source**: `fm/MangaReaderScreen.kt:377-400` (mount), `:840-892` (TopChrome).
- **Platform**: both (opaque `surfaceContainer`, fades in/out with chrome).
- **Entry / exits**: visible on open, toggled by centre tap / Space / auto-hidden on Android landscape rotation and page turns. Exits:
  Back; Settings › Reader.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Back | icon button | Save progress, exit | both |
| Chapters | icon button (list) | Opens MNG-16 | both |
| Reader settings | icon button (gear) | Opens MNG-15 | both |
| More options | icon button → dropdown | Opens overflow | both |
| "Fullscreen" / "Exit fullscreen" | dropdown item | Toggles fullscreen | both |
| "Switch source" | dropdown item | Opens MNG-17 and reloads alternatives | both |
| "Save page image" | dropdown item | Saves current page as PNG (Android `Pictures/Nijiyome` via MediaStore; desktop `<Pictures>/Nijiyome`, never overwrites) → snackbar | both |
| "All reader settings" | dropdown item | Opens Settings › Reader | both |

- **Data & states**: title; "Chapter N · chapter title · source name".
- **UX notes**: two gear-like entries (sheet vs full settings) in different places; fullscreen is buried in overflow (key F exists on
  desktop). Capture has no share/open action after saving.

## MNG-14 — Manga Reader bottom bar

- **Source**: `fm/MangaReaderScreen.kt:401-415` (mount), `:894-979` (BottomChrome).
- **Platform**: both; compact single-row layout only on Android landscape.
- **Entry / exits**: with the chrome.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Page slider ("N" … "total") | slider (RTL layout in paged RTL) | Jumps to page on release | both |
| Previous chapter | icon button (disabled at first) | Opens previous chapter | both |
| Next chapter | icon button (disabled at last) | Opens next chapter | both |
| Pages | icon button (carousel) | Opens page rail (MNG-18) | both |
| Auto-scroll play/pause | icon button (only vertical/webtoon and speed > 0) | Starts/stops auto-scroll at the set px/s | both |

- **Data & states**: "Page X of Y"; zero pages → indeterminate linear progress; single page → "Page 1 of 1".
- **UX notes**: auto-scroll button is hidden until a speed is set deep in settings (discoverability); no speed control in the bar.

## MNG-15 — Manga Reader settings sheet ("Reader settings")

- **Source**: `fm/MangaReaderScreen.kt:420-428`; rows `fm/ReaderSettingsSection.kt:125-207` (ReaderSettingsRows).
- **Platform**: both (AdaptiveSheet).
- **Entry / exits**: gear in MNG-13. Exits: dismiss / Esc; "All reader settings" → Settings › Reader (MNG-20).
- **Controls & actions** (changes apply live and persist):

| Control | Type | Action | Platform |
|---|---|---|---|
| Reading mode: Paged / Vertical / Webtoon | segmented | Sets mode | both |
| Direction: Right to left / Left to right | segmented | Sets direction | both |
| Double page | switch row | Two pages side by side on wide screens (paged) | both |
| Page fit: Width / Height / Screen / Natural | segmented | Sets fit | both |
| Maximum width | slider 0..2000 dp, 40 stops, snaps to 50 ("No limit" at 0) | Limits page width | both |
| Gap between pages | slider 0..64 dp (16 stops) | Gap in vertical mode | both |
| Crop borders | switch row | Trims white/black margins | both |
| Background: Black / Gray / White / Theme | segmented | Reader background | both |
| Use system brightness | switch row (on = -1; off sets 60 %) | Toggles override | both |
| Brightness | slider 5..100 % (only when override) | Window brightness (Android) / dim overlay (desktop) | both |
| Load the whole chapter | switch row | Eager loading | both |
| Pages to preload | slider 1..20 (only when not eager) | Lazy look-ahead | both |
| Infinite scroll | switch row | Vertical/webtoon continue into next chapter | both |
| Auto-scroll speed | slider 0..600 px/s, steps of 20 ("Off" at 0) | Enables auto-scroll button | both |
| Fullscreen when opening | switch row | Default fullscreen | both |
| Tap hides the controls | switch row | Centre tap toggles bars | both |
| Show page progress | switch row | Counter when bars hidden | both |
| Zoom shortcuts | switch row | Enables Ctrl+wheel and zoom keys | both |
| "All reader settings" | text button | Closes sheet, opens Settings › Reader | both |

- **Data & states**: section titles Layout, Colours, Page loading, Behaviour; hints under switches.
- **UX notes**: long single scroll of 18 rows; mode-irrelevant rows are always shown (Direction/Double page in scroll modes, Gap in paged
  and webtoon, Fit in webtoon). Sliders give no live preview until released. Good candidates for grouped tabs / mode-aware rows.

## MNG-16 — Manga Reader chapters sheet

- **Source**: `fm/MangaReaderScreen.kt:429-453`.
- **Platform**: both.
- **Entry / exits**: Chapters icon (MNG-13). Exit: selecting a chapter opens it (saves progress) and closes the sheet.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Chapter row | list row (max 480 dp, scrolled to current) | Opens chapter | both |

- **Data & states**: ascending list, current chapter in primary colour, supporting "group · LANG"; empty → "No chapters match.".
- **UX notes**: no read/downloaded markers, no filter or order toggle (unlike Details).

## MNG-17 — Manga Reader "Switch source" sheet

- **Source**: `fm/MangaReaderScreen.kt:454-472`; VM `loadAlternatives`/`switchSource` `fm/MangaReaderViewModel.kt:263-297`.
- **Platform**: both.
- **Entry / exits**: overflow "Switch source"; "Switch source" buttons on page / chapter errors. Exit: picking a source replaces the
  reader with the same chapter number (preferred language first) at the same page.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Source row | list row (disabled for current) | Switches extension | both |
| Retry | button (error) | Reloads alternatives | both |

- **Data & states**: hint "Open this chapter in another installed extension."; row "count · LANGS · reliability%" or problem label;
  loading spinner; error block; empty → "No manga sources". Snackbar "That source does not have this chapter." when no match.
- **UX notes**: no preview of chapter availability before switching.

## MNG-18 — Manga Reader page rail sheet ("Pages")

- **Source**: `fm/MangaReaderScreen.kt:473-490`.
- **Platform**: both.
- **Entry / exits**: Pages icon (MNG-14). Exit: tapping a thumbnail jumps and closes.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Thumbnail (72×104, number below) | horizontal LazyRow item | Jumps to page | both |

- **Data & states**: current page outlined in primary; thumbnails uncropped.
- **UX notes**: horizontal-only strip in a bottom/side sheet; a grid would scan faster on desktop.

## MNG-19 — Manga Reader snackbars

- **Source**: `fm/MangaReaderScreen.kt:172-186`, `:391-397`, host `:416`.
- **Platform**: both.
- **Entry / exits**: VM events, capture.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "That source does not have this chapter." | snackbar | Info | both |
| "This is the last chapter available." | snackbar (next at last chapter) | Info | both |
| "Saved to <path>" / "Could not save the page" | snackbar | Capture result | both |
| "Could not load" | snackbar | Source switch failure | both |

- **Data & states**: as listed.
- **UX notes**: no actions on capture snackbar (open folder/share).

## MNG-20 — Settings › Reader › "Manga reader" section

- **Source**: `fm/ReaderSettingsSection.kt:54-82` (section), `:125-207` (rows), `:209-237` (labels), `:240-303` (shortcut editor);
  mounted in `feature/settings/.../SettingsScreen.kt:709-712` (`SettingsSection.READER`, followed by the novel section NOV-15).
- **Platform**: both. Shortcut rows shown on both (Android keys come from keyboards/remotes).
- **Entry / exits**: Settings › Reader; "All reader settings" from manga and novel readers. Exit: back.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Manga title language: Romaji / English / Native | segmented | `mangaTitleLanguage` (also used by novels) | both |
| All MNG-15 rows (18 rows) | as in MNG-15 | Same settings | both |
| Shortcut row per action (12) with key badge | list row tap | Opens capture dialog (MNG-21) | both |
| "Reset shortcuts" | text button (disabled when no overrides) | Clears keymap overrides | both |

- **Data & states**: hint "Separate from the anime title language."; "Keyboard and remote keys of the manga reader."; conflict line "Same
  key as another action" in error colour; key badges (arrows, "Space", "=", "−", letters).
- **UX notes**: manga and novel settings are stacked in one long page; title language for novels lives under "Manga reader" (label says
  manga). No per-action "reset" or "unbind".

## MNG-21 — Shortcut capture dialog

- **Source**: `fm/ReaderSettingsSection.kt:263-302`; key name mapping `fm/ReaderKeys.kt`, android `feature/manga/src/androidMain/.../ReaderKeys.android.kt:7`,
  desktop `.../desktopMain/.../ReaderKeys.desktop.kt:12-13`.
- **Platform**: both; desktop ignores Ctrl/Alt/Meta combinations (Shift is the same key).
- **Entry / exits**: shortcut row tap (MNG-20). Exit: Save / Cancel / outside.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Any key press | keyboard capture | Records key | both |
| "Save" | text button (disabled for none or reserved) | Binds key (default key drops override) | both |
| "Cancel" | text button | Closes | both |

- **Data & states**: "Press the new key", "Waiting for a key…", captured key large; "That key is reserved" (Back/Escape/Home); "Also used by:
  <actions>" (save still allowed; first action in list order wins).
- **UX notes**: conflicting binds are allowed silently with only a warning; no modifier combos.

---

## NOV-01 — Novel home (catalog)

- **Source**: `fn/NovelHomeScreen.kt:93-268`; UI helpers `fn/NovelUi.kt:57-122` (header, NovelCard, NovelRow, NovelError,
  NovelFilterDropdown); VM `fn/NovelViewModels.kt:49-165`.
- **Platform**: both; no back arrow on wide windows.
- **Entry / exits**: `Route.Novels(query)` from Home chip "Novels", Search type chip, nav rail, command palette "Ir a Novelas", You.
  Exits: card → Novel Details (`Route.NovelDetails`); continue card → Novel Reader or Details (list-only); update card → Reader.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Back | icon button | Navigate back (hidden wide) | both |
| Refresh | icon button | Reloads rows | both |
| Search field "Search light novels" | text field + IME Search | Query; submit searches | both |
| Clear search (X) | icon button (when active) | Resets query + filters | both |
| Genre / Status / Origin / Sort | filter chip → dropdown | See NOV-02 | both |
| Hero slide / "View" | tap / button | Opens Details | both |
| Continue card (saved) | card tap | Opens Reader at saved paragraph | both |
| Continue card (list-only) | card tap | Opens Details | both |
| "Updates of what you follow" card | card tap | Dismisses update, opens Reader at that chapter | both |
| Trending / Popular row cards | card tap | Opens Details | both |
| Row error "Retry" | button | Refresh | both |
| Genre chips | suggestion chip | Searches by genre | both |

- **Data & states**: continue line "Ch. N · P%" + progress bar; hero has title only (no genres, unlike manga). Loading skeletons after
  delay; hero spinner; errors "Could not load" + Retry; empty rows hidden.
- **UX notes**: no "Still loading…" hint, no Top rated row, no adult zone or mature handling compared with manga.

## NOV-02 — Novel search filters and results grid

- **Source**: `fn/NovelHomeScreen.kt:124-180`; filters `fn/NovelViewModels.kt:49-71`.
- **Platform**: both.
- **Entry / exits**: as MNG-02. Exit: card → Novel Details.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Genre | filter chip → dropdown | Any + catalog genres | both |
| Status | filter chip → dropdown | Any, Publishing, Finished, Hiatus, Cancelled, Upcoming | both |
| Origin | filter chip → dropdown | Any, Japan, Korea, China | both |
| Sort | filter chip → dropdown | Popularity, Trending, Score, Newest, Title, Favourites (Relevance with a query) | both |
| Result card | card tap | Opens Details | both |
| Infinite load | scroll | Next page | both |
| Error "Retry" | button | Re-runs search | both |

- **Data & states**: loading, error, empty ("Nothing found" / "Try another title or fewer filters."), loading more (linear progress).
- **UX notes**: same Sort-reset issue as manga (`setFilters` → `clear()` when not active). No year filter (manga has one). Load-more error
  has no retry row (manga has one).

## NOV-03 — Library › Novels tab

- **Source**: `fn/NovelHomeScreen.kt:270-335`; mounted `feature/library/.../LibraryScreen.kt:198`.
- **Platform**: both.
- **Entry / exits**: Library tab NOVELS. Exits: row / continue card → Novel Details; "Link AniList" → Settings › Accounts.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "All" + status chips with counts (Reading default) | filter chips | Filters list | both |
| "Sync with AniList" | icon button (linked only) | Pull/merge AniList list | both |
| Continue card | card tap | Opens Details | both |
| List row | list row tap | Opens Details | both |
| "Link AniList" | button (empty, unlinked) | Settings › Accounts | both |

- **Data & states**: row "<status> · x / y chapters" (no progress bar, unlike manga); syncing linear progress; empty "Your novel list is
  empty"; "Nothing in this list yet."
- **UX notes**: missing progress bar vs manga rows (inconsistent).

## NOV-04 — Novel Details screen

- **Source**: `fn/NovelDetailsScreen.kt:122-349` (screen: header 193-211, actions 212-251, synopsis 252-260, facts 261-283, genres
  284-290), `:449-485` (authors line, TranslationFact); VM `fn/NovelViewModels.kt:218-433`.
- **Platform**: both (detail pane on wide).
- **Entry / exits**: `Route.NovelDetails(id)` from novel cards, Library. Exits: back; Read → Reader; genre chip → global
  `Route.SearchFor(genre)`; Extensions; Downloads (snackbar).
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Back | icon button | Navigate back | both |
| "Read" / "Continue" / "Read chapter N" / "Continue chapter N" | button | Resumes saved paragraph, else next unread chapter of selected source, else first | both |
| Status button | tonal button → dropdown | See NOV-05 | both |
| Progress "−" / "+" | outlined buttons (on list) | Change chapters read | both |
| Synopsis | tap, "More"/"Less" | Expand/collapse | both |
| Translation fact "… and N more" | tap (only with > 3 names; a11y click label "Show all"/"Show fewer") | Expands names | both |
| Genre chips | assist chip | Global search by genre | both |
| Chapters section | — | See NOV-06 | both |

- **Data & states**: pills (Light novel, status, origin, year); Information: Authors (roles), "<Language> translation: names" per language,
  Volumes, Chapters, Status, Started, Ended, Score %, Origin. Loading spinner after delay; error + Retry.
- **UX notes**: vs manga Details: no mature WARN/BLUR, no tags, relations, characters, recommendations, anime adaptation, score boxes,
  rankings, no spinner on Read while resolving. `onOpenAccounts` unused.

## NOV-05 — Novel Details › status menu

- **Source**: `fn/NovelDetailsScreen.kt:225-240`; statuses `fn/NovelHomeScreen.kt:335`.
- **Platform**: both.
- **Entry / exits**: status button (NOV-04).
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Reading / Plan to read / Completed / On hold / Dropped / Rereading | dropdown items | Sets status | both |
| "Remove from list" | dropdown item (on list) | Removes | both |

- **Data & states**: check icon on current.
- **UX notes**: same as MNG-07.

## NOV-06 — Novel Details › Chapters section (sources, tools, list)

- **Source**: `fn/NovelDetailsScreen.kt:292-344` (section), `:351-374` (SourceRow), `:376-404` (NovelChapterTools), `:406-447`
  (NovelChapterRow); VM `checkAll`/`selectSource`/`refreshChapters`/filters `fn/NovelViewModels.kt:235-314`, `toggleRead` `:380`.
- **Platform**: both.
- **Entry / exits**: inline. Exits: chapter row → Reader (offline when downloaded); "Extensions" → Extensions.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "Check all" | text button with icon (spinner while checking) | Queries every source for chapter counts | both |
| Refresh | icon button | Reloads chapters of selected source | both |
| Select chapters / Close | icon button | Toggles selection (NOV-07) | both |
| Source row | list row (vertical list; disabled when unmatched) | Selects source | both |
| Filter "Filter by number or title" | text field | Filters list | both |
| "Oldest first" / "Newest first" | assist chip toggle | Order | both |
| "Translator" | filter chip → dropdown (when translators exist) | Any + translators | both |
| "Language" | filter chip → dropdown (> 1 language) | Any + languages | both |
| Chapter row | list row tap | Opens Reader / toggles selection | both |
| Mark read / not read | icon button | Same list-progress rules as manga | both |
| "Extensions" (no sources) | button | Opens Extensions | both |
| Retry | button (sources / chapters error) | Reloads | both |

- **Data & states**: source row "N chapters" or "Not checked yet" · LANGS · translator; problems "Did not answer in time.", "Failed while
  looking for the title.", "This source did not find the title."; selected source check icon; "N chapters" count; row "Chapter N · title",
  "translator · LANG · date"; downloaded icon; read dimmed. Loading spinners; errors; "No novel sources" / "Install a novel extension to
  list chapters by source."; "No chapters match.".
- **UX notes**: sources are a vertical list here but chips in manga (inconsistent); no "Sources and languages" sheet, no preferred source,
  no clear cache, no group by volume. "Check all" may be slow with only a small spinner.

## NOV-07 — Novel Details › selection / download bar

- **Source**: `fn/NovelDetailsScreen.kt:169-180`, checkbox `:429`; VM `:309-327`.
- **Platform**: both.
- **Entry / exits**: Select chapters icon. Exit: Close, or after Download.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Row checkbox / row tap | checkbox / list row | Toggle chapter | both |
| "Select all" | text button | Selects shown chapters | both |
| "Download" | button | Queues selected | both |

- **Data & states**: "N selected".
- **UX notes**: same as MNG-09.

## NOV-08 — Novel Details snackbars

- **Source**: `fn/NovelDetailsScreen.kt:142-157`.
- **Platform**: both.
- **Entry / exits**: VM events.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "%d chapters queued for download" + "Downloads" | snackbar action | Downloads (Novels tab) | both |
| "No chapter to open yet." | snackbar | Info | both |
| "Could not load" | snackbar | Any failure | both |

- **Data & states**: as listed.
- **UX notes**: generic failure text.

## NOV-09 — Novel Reader: text canvas (gestures, states)

- **Source**: `fn/NovelReaderScreen.kt:111-394` (screen: window 144-146, typography 148-157, rows 159-171, layout/landscape 172-189,
  position 190-195, continuous 196-201, narration follow 202-208, Esc 210-223, states 225-233, list 235-286, progress bar 289-291);
  palette/fonts `fn/NovelUi.kt:164-205`; Esc `feature/novel/src/desktopMain/.../NovelPlatform.desktop.kt:81-97` (Android no-op
  `.../androidMain/.../NovelPlatform.android.kt:48-49`); VM `fn/NovelReaderViewModel.kt:109-259`.
- **Platform**: both. Desktop: Esc chain, window fullscreen, installed font families + Mono. Android: system bars hidden in fullscreen,
  landscape auto-hide of bars.
- **Entry / exits**: `Route.NovelReader(args)` from Details, Home continue/update cards, Downloads. Exit: back / Esc (saves progress,
  stops narration on close).
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Tap anywhere on text area | tap | Toggles bars | both |
| Tap paragraph while narrating | tap | Restarts narration at that paragraph | both |
| Vertical scroll | swipe / mouse wheel | Scrolls; reaching the end appends next chapter when Continuous scroll is on | both |
| End "Next chapter" | button (only when Continuous scroll off) | Opens next chapter | both |
| Error "Retry" | button / outlined button | Reloads chapter text | both |
| Esc | keyboard | Close sheet → leave fullscreen → exit | desktop |
| System Back | system | Save + exit | android |

- **Data & states**: chapter header "Chapter N" + title (size + 6 sp); paragraphs with font, size, line height, paragraph spacing, max
  width, justify, dyslexic letter spacing 0.06 em; palette Theme / Sepia (#F4ECD8 on #5B4636) / Black / White / Gray (#2E2E2E); highlighted
  paragraph while narrating (if highlight on) with auto-scroll to it; bottom linear progress bar (if on). Loading spinner; "The chapter
  could not be loaded" + message + Retry; appended chapter loading/error rows; "End of chapter", "This is the last chapter available.".
  Screen kept on when "Keep the screen on" or while speaking. Progress (paragraph + fraction) saved every 3 s, at completion, on exit.
- **UX notes**: no keyboard shortcuts at all besides Esc (no page down/next chapter/play-pause keys), unlike the manga reader's remappable
  keymap. No tap zones for paging; no paged mode. No in-text find, bookmarks or text selection found.

## NOV-10 — Novel Reader top bar

- **Source**: `fn/NovelReaderScreen.kt:293-314`.
- **Platform**: both; compact one-line title on Android landscape.
- **Entry / exits**: with chrome.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Back | icon button | Save + exit | both |
| Chapters | icon button | Opens NOV-13 | both |
| "Text and layout" | icon button (TextFields) | Opens NOV-12 | both |
| Fullscreen / exit fullscreen | icon button | Toggles fullscreen | both |

- **Data & states**: title + "Chapter N".
- **UX notes**: fullscreen is a top-level icon here but an overflow item in the manga reader (inconsistent). No overflow menu.

## NOV-11 — Novel Reader bottom bar + narration controls

- **Source**: `fn/NovelReaderScreen.kt:315-352`; VM `speak`/`pause`/`resume`/`stopNarration`/`setSpeed` `fn/NovelReaderViewModel.kt:189-223`;
  error text `fn/NovelReaderSettingsSection.kt:257-271`.
- **Platform**: both. Android pause restarts the paragraph on resume (TextToSpeech cannot pause mid-sentence); desktop clips can pause
  mid-sentence (libmpv) or live voice pauses.
- **Entry / exits**: visible with chrome, and always while narration is not idle.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Previous chapter | icon button | Opens previous | both |
| "Read aloud" | text button with icon (idle) | Starts narration at current paragraph | both |
| Preparing | spinner (state) | — | both |
| Pause | icon button (speaking) | Pauses | both |
| Resume | icon button (paused) | Resumes | both |
| Stop | icon button (not idle) | Stops narration | both |
| Speed "1.0×" | slider 0.5..2.0 step 0.1 (only while narrating) | Sets and persists speed | both |
| Next chapter | icon button | Opens next | both |

- **Data & states**: "N% read"; narration error line (red): "Save the API key of this engine first.", "Choose a voice supported by this
  engine and model.", "Choose a speech model.", "Enter a valid http(s) server URL without credentials.", "The narration service answered
  HTTP %d…", "The server returned no audio…", "Could not reach the narration service.", "The system text-to-speech engine is not
  available.", "Download the KittenTTS model in Settings › Narrator first.", "KittenTTS needs espeak-ng installed…", "Narration failed: %s".
  Narration continues into next chapter (appending it if needed).
- **UX notes**: errors point to "Settings › Narrator" but there is no link button, and voice/engine cannot be changed from the reader.
  No media notification / lock-screen controls on Android (string `novel_tts_notification_title` "Reading aloud" exists in
  `core/novel/src/androidMain/res/values/strings.xml` but no code uses it). No sleep timer.

## NOV-12 — Novel Reader "Text and layout" sheet

- **Source**: `fn/NovelReaderScreen.kt:357-366`; rows `fn/NovelReaderSettingsSection.kt:131-244` (NovelTypographyRows, NovelFlowRows).
- **Platform**: both (AdaptiveSheet).
- **Entry / exits**: "Text and layout" icon. Exit: dismiss / Esc; "All reader settings" → Settings › Reader (lands on the manga section
  first).
- **Controls & actions**: same rows as NOV-15 (font, import, size, line height, paragraph spacing, width, colours, justify, preview,
  continuous scroll, progress bar, fullscreen, keep screen on) plus:

| Control | Type | Action | Platform |
|---|---|---|---|
| "All reader settings" | text button | Opens Settings › Reader | both |

- **Data & states**: live preview text.
- **UX notes**: narrator settings are absent from the sheet. "All reader settings" opens a page where novel options are below all manga
  options.

## NOV-13 — Novel Reader chapters sheet

- **Source**: `fn/NovelReaderScreen.kt:367-391`.
- **Platform**: both.
- **Entry / exits**: Chapters icon. Exit: pick opens chapter.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Chapter row | list row (max 480 dp, scrolled to current) | Opens chapter (saves progress) | both |

- **Data & states**: current in primary; "translator · LANG"; empty "No chapters match.".
- **UX notes**: no read markers / filter.

## NOV-14 — Novel Reader snackbar

- **Source**: `fn/NovelReaderScreen.kt:130-139`, host `:353`.
- **Platform**: both.
- **Entry / exits**: next at last chapter.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "This is the last chapter available." | snackbar | Info | both |

- **Data & states**: —
- **UX notes**: —

## NOV-15 — Settings › Reader › "Novel reader" section (typography + flow)

- **Source**: `fn/NovelReaderSettingsSection.kt:85-98` (section), `:131-235` (typography), `:237-244` (flow); fonts `fn/NovelUi.kt:178-216`;
  importers `feature/novel/src/androidMain/.../NovelPlatform.android.kt:22-39`, `.../desktopMain/.../NovelPlatform.desktop.kt:36-60`.
- **Platform**: both; desktop adds Mono preset and "Installed font…".
- **Entry / exits**: Settings › Reader (below MNG-20); also NOV-12.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Font: Serif, Sans, Georgia, Lora, Dyslexia friendly, (Mono), System, Imported font (only when imported) | filter chips (single select, horizontal scroll) | Sets font | both (Mono desktop) |
| "Installed font…" / family name | filter chip | Opens NOV-16 | desktop |
| "Import TTF / OTF" | text button → file picker | Copies font to app dir, selects "custom" (desktop max 64 MB) | both |
| "Remove imported font" | text button (when imported) | Deletes file, falls back to Serif | both |
| Text size | slider 10..40 sp | Font size | both |
| Line height | slider 1.0..2.5× (0.1 steps) | Line height | both |
| Paragraph spacing | slider 0..48 dp | Spacing | both |
| Maximum width | slider 0..1600 dp, snaps to 50 ("No limit") | Column width | both |
| Colours: Theme / Sepia / White / Gray / Black | segmented | Palette | both |
| Justify text | switch row | Justify | both |
| Preview | live text sample | Shows typography | both |
| Continuous scroll | switch row | Next chapter below | both |
| Progress bar | switch row | Bottom bar | both |
| Fullscreen when opening | switch row | Default fullscreen | both |
| Keep the screen on | switch row | Wake lock | both |

- **Data & states**: "Imported: <file>"; error "That file is not a TTF / OTF font."; on Android Georgia/Lora/Dyslexic fall back to system
  serif/sans (no bundled fonts).
- **UX notes**: Georgia/Lora/Dyslexia presets look different from their names on Android (fallbacks) with no hint. Segment order differs
  from manga background (manga: Black, Gray, White, Theme; novel: Theme, Sepia, White, Gray, Black). Background row is labelled "Colours".

## NOV-16 — Installed font dialog

- **Source**: `fn/NovelReaderSettingsSection.kt:524-571`.
- **Platform**: desktop only (`installedFontsOffered`).
- **Entry / exits**: "Installed font…" chip. Exit: pick / Use / Cancel.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "Family name" | text field (autofocus) | Filters families (prefix matches first) | desktop |
| Family row (rendered in its own font) | list row tap | Selects `family:<name>` | desktop |
| "Use" | text button (enabled when typed name is installed) | Selects typed family | desktop |
| "Cancel" | text button | Closes | desktop |

- **Data & states**: "No installed font matches."; current family in primary.
- **UX notes**: —

## NOV-17 — Settings › "Narrator" section

- **Source**: `fn/NovelReaderSettingsSection.kt:273-450` (NarratorRows), `:452-469` (NarratorStatusRow); options
  `core/novel/src/commonMain/.../NarratorOptions.kt`; desktop engines `core/novel/src/desktopMain/.../tts/DesktopNarrator.kt:85-87`;
  cloud `core/novel/src/commonMain/.../CloudTts.kt`; Android `core/novel/src/androidMain/.../NarratorImpl.kt`.
- **Platform**: both. Android engines: System voice, OpenAI, ElevenLabs, Local server; desktop: System voice, KittenTTS, OpenAI,
  ElevenLabs, Local server. Pitch and status row desktop only.
- **Entry / exits**: Settings › Reader (after NOV-15). Not reachable from the reader.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| Engine: System voice / (KittenTTS) / OpenAI / ElevenLabs / Local server | segmented (resets voice) | Sets engine | both (Kitten desktop) |
| Engine status line ("Speaking with <backend>." or problem) | text | — | desktop |
| Voice | outlined button → dropdown ("Engine default" + listed voices; disabled when none) | Sets voice | both |
| "Voice id" + "Save" | text field + text button (ElevenLabs, Local server) | Manual voice id | both |
| Speed | slider 0.5..2.0× | Default speed | both |
| Pitch | slider 0.5..2.0× (system voice only) | Pitch | desktop |
| Highlight the paragraph being read | switch row | Highlight + follow | both |
| "Server URL (OpenAI-compatible)" | text field (Local server) | Endpoint (placeholder `http://127.0.0.1:8880/v1`) | both |
| "Model" | text field (OpenAI, ElevenLabs, Local server) | Model id (placeholders `gpt-4o-mini-tts`, `eleven_multilingual_v2`, `kokoro`) | both |
| "Save" (endpoint + model) | text button | Persists both | both |
| "API key" + "Save" | password field + text button (cloud engines) | Stores key in SecretStore | both |
| "Remove" (key) | text button (when saved) | Deletes key | both |
| "Test voice" / "Playing…" | tonal button | Speaks "This is how the narrator sounds with the current settings." | both |
| "Stop" (test) | text button (while testing) | Stops preview | both |
| KittenTTS model row | panel | See NOV-18 | desktop |

- **Data & states**: Android System hint "Uses the text-to-speech engine installed on the device; voices come from its settings."; key
  states "Key saved", "No key saved", "Optional for local servers", "Secure storage is not available"; "No voices listed (save the key or
  check the server)."; desktop status texts: "No system voice found. Install speech-dispatcher (or espeak-ng) and come back.", "KittenTTS
  (local voice) is not downloaded yet.", "KittenTTS is downloaded but espeak-ng is missing…", "KittenTTS has no build for this system.",
  "<backend> did not start." + raw detail; test errors use the NOV-11 error list.
- **UX notes**: endpoint/model need an explicit Save while all other rows apply instantly (no unsaved indicator). Voice list can be long
  (system voices) in a plain dropdown without search or language grouping. Pitch label reuses the "N×" speed format. Engine segmented with
  5 long labels may not fit narrow widths.

## NOV-18 — KittenTTS local voice row

- **Source**: `fn/NovelReaderSettingsSection.kt:471-521`; installer `core/novel/src/desktopMain/.../tts/kitten/Kitten.kt:104-200`.
- **Platform**: desktop only (engine KITTEN).
- **Entry / exits**: inside NOV-17 when KittenTTS is selected.
- **Controls & actions**:

| Control | Type | Action | Platform |
|---|---|---|---|
| "Download" | tonal button (not installed) | Downloads ONNX Runtime + model, SHA-256 checked | desktop |
| "Remove" | text button (installed) | Deletes runtime and all models | desktop |

- **Data & states**: model label "KittenTTS nano 0.8 (int8)"; "Model of about 28 MB plus ONNX Runtime (54 MB). Runs offline; English
  only."; progress "Downloading ONNX Runtime… x / y MB" / "Downloading the model… x / y MB" + bar; "Downloaded and checked"; raw error text.
- **UX notes**: no cancel during download; the model id comes from `tts.model` but no model picker exists for KittenTTS (only one model).

---

## Manga/Novel cross-cutting

### Reader shortcut table

Manga keymap: `core/manga/.../ReaderKeymap.kt:29-45`, handling `fm/MangaReaderScreen.kt:254-288`, direction mirroring `fm/ReaderKeys.kt:22-29`.
Novel reader: only Esc (desktop).

| Action | Default key | Platform | Remappable? |
|---|---|---|---|
| Next page (paged) / scroll down 85 % (scroll modes) | → (DPAD_RIGHT) | both; desktop paged RTL mirrors to previous page | yes |
| Previous page / scroll up 85 % | ← (DPAD_LEFT) | both; desktop paged RTL mirrors to next page | yes |
| Scroll down (same step as next page) | ↓ (DPAD_DOWN) | both | yes |
| Scroll up | ↑ (DPAD_UP) | both | yes |
| Next chapter | N | both | yes |
| Previous chapter | P | both | yes |
| Toggle fullscreen | F | both | yes |
| Cycle reading mode Paged → Vertical → Webtoon (persists) | D | both | yes |
| Zoom in ×1.25 (needs "Zoom shortcuts") | = (EQUALS) | both | yes |
| Zoom out ×0.8 (needs "Zoom shortcuts") | − (MINUS) | both | yes |
| Show / hide controls (ignores "Tap hides the controls") | Space | both | yes |
| Reload pages | R | both | yes |
| Close sheet → leave fullscreen → exit (manga and novel readers) | Esc | desktop | no (reserved) |
| Exit reader (saves progress) | Back | android | no (reserved) |
| Home | Home | both | no (reserved, passed through) |
| Zoom (manga) | Ctrl + mouse wheel | both (needs "Zoom shortcuts") | no |
| Novel reader: any other key | — | — | no keymap exists |

Desktop ignores keys pressed with Ctrl / Alt / Meta (so app shortcuts such as Ctrl+K are not reader keys). On a key conflict the first
action in list order wins.

### Gesture map

| Zone / gesture | Manga Paged | Manga Vertical | Manga Webtoon | Novel reader |
|---|---|---|---|---|
| Tap left third | Prev page (LTR) / next page (RTL); at 1x only | Toggle bars* | Toggle bars* | Toggle bars |
| Tap centre third | Toggle bars* | Toggle bars* | Toggle bars* | Toggle bars |
| Tap right third | Next page (LTR) / prev page (RTL); at 1x only | Toggle bars* | Toggle bars* | Toggle bars |
| Tap while zoomed (>1x) | Toggle bars* | Toggle bars* | Toggle bars* | — |
| Tap paragraph while narrating | — | — | — | Restart narration at paragraph |
| Horizontal swipe | Turn spread (reversed in RTL; disabled when zoomed) | — | — | — |
| Vertical swipe / wheel | — (not handled in code) | Scroll | Scroll | Scroll |
| Double tap | 1x ↔ 2.5x at tap point | same | same | — |
| Pinch | Zoom 1x..5x | same | same | — |
| One-finger drag when zoomed | Pan | Pan | Pan | — |
| Ctrl + wheel | Zoom ×1.1 / ×0.9 | same | same | — |
| Reach end of content | End card page ("Next chapter") | Append next chapter (Infinite scroll) or end card | same | Append next chapter (Continuous scroll) or end block |
| Page step from key | Pager ±1 spread; at end → next chapter | Scroll ±85 % viewport | same | — |

\* only when "Tap hides the controls" is on. Android landscape: bars auto-hide on rotation and on every page turn (manga) or when the
text starts scrolling while not narrating (novel).

### Count

Surfaces: 39 (21 manga, 18 novel). Action rows (rows in the Controls & actions tables): 253.

---

# Cross-cutting

## X-01 — Player keyboard shortcuts (all)

Source: `core/player/.../PlayerKeymap.kt`, handled in `feature/player/.../PlayerControls.kt:342-368`, desktop extras in `DesktopPlayerKeys.kt`. Remappable entries are edited in Settings › Player › Keyboard shortcuts (PLE-25) and shown read-only in the `?` panel (PLY-14). Keys are ignored while the screen is locked. Over the end screen, only K (next episode) keeps its action; the arrows and Enter move focus between the buttons.

| Action | Default key | Platform | Remappable | Notes |
|---|---|---|---|---|
| Play / pause | Space | both | yes | |
| Seek back | ← | both | yes | 10 s Android, 5 s desktop |
| Seek forward | → | both | yes | same |
| Full screen | F | both | yes | |
| Mute | M | both | yes | Android: system volume; desktop: engine volume |
| Picture in picture | P | both | yes | desktop: toggles the small always-on-top window |
| Previous episode | J | both | yes | |
| Next episode | K | both | yes | only with a next episode and not Link |
| Volume up / down | ↑ / ↓ | both | yes | desktop ±5 % |
| Screenshot | S | both | yes | |
| Subtitles | C | both | yes | Android: on/off toggle; desktop: cycles tracks, then off |
| Show shortcuts | ? (Slash) | both | yes | |
| Faster / slower | . / , | desktop | yes | steps through the speed list |
| Next audio track | V | desktop | yes | |
| Subtitles 0.1 s earlier / later | Z / X | desktop | yes | |
| Skip intro / ending | Enter | desktop | yes (Enter itself is reserved in the editor) | only while a segment is active |
| Loop on / off | L | desktop | yes | |
| Upscaling preset Off, A, A+A, B, C, FSR | Ctrl+1…6 (also numpad) | desktop | no | HUD "Upscaling: …" |
| Esc chain | Esc | desktop | no | closes help, then the sheet, then leaves fullscreen, then leaves the player |
| Back | Back | android | no | system back leaves the player, even while locked |
| Media keys / headset | play, pause, next, prev | android | no | through the media session (PLA-20); MPRIS on Linux (PLY-28) |

Reserved keys (the editor refuses them): Back, Home, App switch, Power, Escape, Tab, Enter, D-pad centre, Menu. On a conflict, the first action in list order wins.

## X-02 — Player pointer and gesture map

| Input | Android (touch) | Desktop (mouse) |
|---|---|---|
| Single tap / click on video | show or hide controls | play / pause (fires only after the double-click timeout) |
| Double tap / double-click | left half −10 s, right half +10 s (accumulates) | toggle fullscreen |
| Long press | 2× while held | — |
| Vertical drag, left half | brightness | — |
| Vertical drag, right half | system volume | — |
| Horizontal drag on video | relative scrub (90 s per screen width) | — |
| Mouse wheel | — | seek ±5 s (wheel up = forward) |
| Pointer move | — | shows controls and cursor; both hide after 4 s of playback |
| Hover over seek bar | — | time bubble |
| Seek bar tap or drag | absolute seek with preview bubble | absolute seek |
| Pinch / right-click | none | none |
| Locked screen tap | shows Unlock for 2.5 s | (no lock) |

Full Android detail: PLA-10 and the "Android cross-cutting" section. Reader gestures and shortcuts: "Manga/Novel cross-cutting" section above (manga keymap with 12 remappable actions; the novel reader has only Esc).

## X-03 — Counts

| Part | Surfaces | Action rows in tables |
|---|---|---|
| PLY player core | 30 (+ PLY-31 settings reference) | 109 (+≈30 listed inline in smaller sections) |
| PLA Android/touch | 27 | 89 (+14 gesture rows, 15 key rows) |
| PLE enhancements/subtitles | 28 | 123 |
| MNG/NOV manga + novel | 39 (21 + 18) | 253 |
| **Total** | **124 (≈116 unique after the overlaps above)** | **574 (+≈60 cross-cutting/inline)** |

## X-04 — Main UX pain points for the redesign (summary)

1. **Hard-coded Spanish strings in the player**: Speed sheet title, the Audio sheet's "Sin audio" row and empty text, "Estilo de controles", "Cine", "Minimalista", aspect names, "Cambiar fuente".
2. **Single choices as long row lists**: speed (6–7 rows), quality, sleep timer, 12 shader presets in an ungrouped chip grid, Year and Genre dropdowns in the catalogs. Chips, segmented controls or steppers would fit.
3. **The touch layouts don't offer the same actions**: Cinema and Minimal × compact and wide give four layouts. Some actions exist in only one of them (e.g. "Open in the torrent client" is missing in Minimal-wide), and Next episode is shown twice.
4. **About 40 notices share one transient toast** (5 s, no history), including fallbacks, Link failures and quality downgrades. Only one of them has an action (Undo).
5. **Overloaded sheets**: the Subtitles sheet mixes tracks, file import, style, delay, secondary track and translation. The style editor has about 25 controls. The Enhancements panel can show up to 7 cards in one scroll.
6. **Two persistence models**: enhancement changes last only for this playback (unless "Use as default"), while subtitle style changes are saved globally. Reset buttons ask for no confirmation.
7. **Desktop shows phone-only concepts**: "Process on Phone/PC/Mixed" and phone wording in the copy, even though desktop is never a Link client.
8. **External mpv card is read-only**: no seek or play/pause in the app window, even though the IPC session, MPRIS and Link can drive it. Stop and Back do the same thing.
9. **Readers**: the manga settings sheet shows options that do nothing in the current mode. Novel narration can't change voice or engine from the reader. Manga and novel readers are inconsistent (keymap, fullscreen placement, sources as chips vs. a list).
10. **Possible bug and dead controls**: a Sort-only pick resets the manga and novel catalogs (`MangaHomeViewModel.kt:106-115`). Tag chips look tappable but do nothing. Tap zones are invisible and can't be changed.

## X-05 — Not determined (open questions)

- Whether Android Back closes an open sheet before leaving the player.
- The exact Media3 notification buttons (library defaults).
- Whether the battery and thermal downgrades ever fire on desktop.
- The exact zoom limits of `QualityRequest` (mpv clamps to 25–400 %).
- Which files the desktop subtitle picker really accepts (its filter is `text/plain` only).
- Whether the mouse wheel turns pages in the paged manga reader on desktop.
- What `HeroCarousel` and `AdaptiveSheet` do with gestures and back.
- No thermal frame-generation state was found in `core/framegen`.
- `MangaPrefs.preferredSource` (the global source) has no UI.
- "Rewind cache" is only the Settings › Link RAM cache (512 MB–4 GB, settings agent). mpv's demuxer back-buffer is fixed, not user-visible.
