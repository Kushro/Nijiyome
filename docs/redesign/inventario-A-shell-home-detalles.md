# Inventory A: shell, Home, Search, Details, Library, You, Social, core/ui, desktop window

Functionality parity checklist for the redesign. Scope: `feature/shell`, `feature/home`, `feature/search`, `feature/details`,
`feature/library`, `feature/you`, `feature/social`, `core/ui`, `desktopApp/src`. Player, readers, manga/novel, settings,
extensions, torrents, downloads, Link and the design system belong to other inventories; they appear here only as navigation
targets.

Read from source on branch `Kushro/Redesign-sparkles` (base v1.2.1). Labels are the English strings in
`composeResources/values/strings.xml`. Paths are relative to the repo root; `:NNN` is a line number.

Conventions:
- **Platform**: both / android / desktop. "wide" = rail layout (width >= 600 dp and height >= 480 dp, or always on desktop).
- **Actions table**: every control, its current control type, and what it does. Rule for the redesign: every row must still
  exist somewhere (it can move, merge or change control type, but it cannot disappear).
- Hard-coded Spanish strings (not localized) are flagged `[hard-coded ES]`.

---

## SHELL-01 App root and first-run gate

- **Source**: `feature/shell/src/commonMain/kotlin/app/nijiyome/nav/NijiyomeApp.kt:194` (`NijiyomeApp`), `:257` (`AppShell`).
- **Platform**: both. Desktop passes `windowTopBar` (custom title bar, DESK-02) and `shortcuts`; Android passes neither.
- **Entry**: app launch.
- **Behavior**:
  - Applies the theme from settings: palette, AMOLED, contrast, dynamic color, theme mode (System / Light / Dark).
  - Provides app-wide locals: reduce motion (setting "Reduce motion" or Look › Motion = Off), snackbar host, title language,
    Look settings.
  - While `onboardingDone` is false shows `OnboardingScreen` (first-run wizard, owned by the settings inventory) instead of the shell.
  - Back stack is Navigation3, persisted (`RouteSavedState`), starts at `[Home]`.
- **Leads to**: SHELL-02/03 navigation, every screen.

## SHELL-02 Bottom navigation bar (compact)

- **Source**: `NijiyomeApp.kt:413-438`, routes `Routes.kt:121`.
- **Platform**: android only in practice (phones < 600 dp wide, or phones in landscape < 480 dp tall). Desktop never shows it
  (the "You" route is not offered on desktop, so the rail is forced, `NijiyomeApp.kt:343`).
- **Items (5, fixed order)**: Home · Search · Calendar · Library · You.
- **Hidden** over Player, Manga reader, Novel reader.

| Control | Type | Does |
|---|---|---|
| Home | nav item (icon + label) | `navigateTop(Home)`: resets the stack to `[Home]` |
| Search | nav item | stack `[Home, Search]` |
| Calendar | nav item | stack `[Home, Calendar]` |
| Library | nav item + badge | stack `[Home, Library]`; badge = active torrent downloads ("99+" cap) |
| You | nav item | stack `[Home, You]` |

- **Selection rule** (`Routes.kt:147`): compact owners: Torrents -> Library; Extensions, Provider, Link, Linked,
  ExtensionServers, Settings, SettingsSection, ForYou, Downloads, Profile, ActivityThread, Notifications -> You;
  Manga/Novels/readers -> Home; Details/Player highlight whatever owns the entry below them.
- **UX notes**: Library badge counts torrents only, not web downloads. Manga and Novels have no bar entry on phones (only Home
  quick chips, Search chips, You rows).

## SHELL-03 Navigation rail (wide)

- **Source**: `NijiyomeApp.kt:524` (`ScrollableRail`), `Routes.kt:127`.
- **Platform**: both (tablets / landscape tablets, desktop always).
- **Items (wide order)**: Home · Search · Calendar · For you · Library · Manga · Novels · Downloads · Torrents · Extensions ·
  Link (Linked device) · Profile · Settings. Filtered by `RouteAvailability` (`RouteAvailability.kt:340`): on desktop **Link** is
  not offered (no `LinkRepository`), so desktop shows 12 items. Downloads / Profile disappear when their service is missing.
- **Styles**: labelled (icon + label) or slim (icons only, label becomes content description). Default from
  Look › "Menú lateral" (`RailStyle`), inverted by Ctrl+B or the palette action (session only, not saved).
- Column scrolls vertically when items do not fit; AMOLED hairline divider on the right.
- **Hidden** over Player / readers.

| Control | Type | Does |
|---|---|---|
| Each destination (13) | rail item | `navigateTop(route)` (stack `[Home, route]`) |
| Torrents badge | badge on icon | active torrent downloads count |
| Ctrl+B | keyboard (desktop) | toggle labelled / slim |
| 1..9 / NumPad1..9 | keyboard (desktop) | jump to rail item N (Home=1 ... Torrents=9 on desktop); ignored while typing, over a modal or a viewer |

- **UX notes**: 13 flat items, no grouping (browse / library / tools / system). Items 10+ have no number shortcut. No tooltip in
  slim mode (only accessibility description). Rail selection of Details uses the owner below in the stack.

## SHELL-04 List-detail panes and back behavior

- **Source**: `NijiyomeApp.kt:607-1004` (`AppNavDisplay`), `:1071` (`navigateTop`).
- **Platform**: both.
- **Two panes** only when a detail route is on top (Details, Character, ProviderItem, NovelDetails, ActivityThread) and the window
  is >= 840 dp wide and >= 480 dp tall. List panes: Calendar, ForYou, Search, SearchFor, ImageSearch, Library, LibraryTab,
  Provider, Manga, Novels, Profile.
- **Back**: system back / predictive back (Android), Esc and title-bar back arrow (desktop, DESK-02) pop one entry; switching a
  top-level destination resets to `[Home, dest]`, so Back from any tab returns to Home. Opening Details from Details replaces the
  top entry (no deep chain) for anime/manga/novel; inside Details, related titles stack in place (DET-02).
- Transitions: default slide/fade; instant when reduce motion is on.
- **Route table (what each screen gets)**: see the `entry<...>` blocks; summary of targets reachable from this scope:
  Details(ANIME) -> DetailsScreen; Details(MANGA) -> MangaDetailsScreen (other inventory); NovelDetails; Character; Player;
  MangaReader; NovelReader; Settings / SettingsSection(section); Extensions; Provider / ProviderItem; Downloads(tab);
  Torrents(section, id); Linked / Link; ExtensionServers; Profile(user?); ActivityThread; Notifications; Calendar; ForYou;
  SearchFor(query|feed|genre|studio); ImageSearch(uri?); LibraryTab(tab).
- **Not available** routes: push shows snackbar SHELL-07 instead of opening.
- **UX notes**: rail/bar destinations hide their back arrow on wide (Calendar always, ForYou/Settings/Extensions/Downloads/Manga/
  Novels/own Profile on wide only); same screen shows or hides back depending on width. Two different "Settings": full screen
  (rail) and modal (Ctrl+P, SHELL-09).

## SHELL-05 Offline banner

- **Source**: `NijiyomeApp.kt:576` (`OfflineBanner`), connectivity `core/ui/.../state/Connectivity.{android,desktop}.kt`.
- **Platform**: both (hidden over Player/readers). Desktop title bar also shows an "Offline" chip (DESK-02).
- **Data**: icon + "Offline. Showing cached content." Polite live region. Animated expand/shrink at the bottom of the content.
- **Actions**: none.

## SHELL-06 Global snackbar host and update notice

- **Source**: `NijiyomeApp.kt:394-403`, `:463`; controller `core/designsystem/.../components/AppSnackbar.kt:24`.
- **Platform**: both.
- One host for the whole app, bottom-center, lifted above the offline banner. Short duration; Long when it has an action.
- **Update notice**: once per newer release: "Nijiyome %s is available" + action **View** -> Settings › About.

| Control | Type | Does |
|---|---|---|
| Snackbar action (varies) | text button in snackbar | e.g. View (update), Undo (For you), Downloads (Details), Send (share), Retry (You sync) |

- **UX notes**: all feedback in the app is snackbars (no toasts, no inline success states). Many success/failure messages
  listed per screen below; keep their wording.

## SHELL-07 "Not available on this device yet"

- **Source**: `NijiyomeApp.kt:636-643`.
- **Platform**: desktop in practice (Link/Linked, You, missing services).
- Snackbar shown when a screen pushes a route the platform does not offer.

## SHELL-08 Command palette (Ctrl+K)

- **Source**: `feature/shell/.../nav/CommandPalette.kt:100`; opened from `NijiyomeApp.kt:474`, `ShellShortcuts.kt` (Ctrl+K),
  title-bar pill (DESK-02).
- **Platform**: desktop (shortcuts and title bar exist only there). Android has no entry point. `[hard-coded ES]` for every
  title, subtitle, group, placeholder and footer.
- **Layout**: dialog, max 640 dp, search field (autofocus) + result list (max 420 dp) + footer hints ("↑↓ Navegar · Enter Abrir ·
  Ctrl+P Ajustes · Esc Cerrar").
- **Empty query**: first 14 entries. **Typed**: ranked fuzzy match (title prefix > word prefix > contains > keyword) top 16 +
  last row "Search «q» in the anime catalog".

| Entry / control | Type | Does |
|---|---|---|
| Search field | text field | filters entries; resets selection |
| Clear (x) | icon button (when text) | clears query; "Esc" pill shown when empty |
| ↑ / ↓ | keyboard | move selection (wraps) |
| Enter / NumPad Enter | keyboard | run selected entry, close |
| Esc | keyboard | close |
| Row click | list row | run entry, close |
| Open Settings (badge Ctrl+P) | action | Settings modal (wide desktop) or Settings screen |
| Toggle AMOLED pure black (badge Active/Inactive) | action | flips `amoled` |
| Change theme (System/Light/Dark), subtitle shows current | action | cycles System -> Dark -> Light -> System |
| Toggle compact sidebar (badge Ctrl+B) | action (wide only) | toggles rail style |
| Open error logs (PC + phone) | action | Settings › About |
| Go to Home (1) / Search (2) / Calendar (3) / For you (4) / Library (5) | nav action, number badge | navigate top |
| Go to Manga / Novels / Downloads / Torrents / Extensions / Profile | nav action | navigate (top-level if in rail) |
| One row per `SettingsCatalog` item (badge = section name) | setting action | opens Settings (modal on desktop) at that section |
| Search «q» in the anime catalog (badge "Catálogo") | action | `SearchFor(query = q)` |

- **States**: no "no results" message (the catalog row is always present when typing).
- **UX notes**: not localized; settings rows only open the section, they do not scroll to or highlight the setting; number badges
  are hard-coded and would drift if rail order changes; no recent/pinned commands; no catalog result previews (only a jump).

## SHELL-09 Settings modal (Ctrl+P)

- **Source**: opened `NijiyomeApp.kt:511` (`SettingsModalDialog`, content owned by the settings inventory).
- **Platform**: desktop (Ctrl+P, Ctrl+, , title-bar gear, tray "Settings", palette setting rows). Disabled over Player/readers.
- **Callbacks out of the modal**: Extensions (top-level), Link / Linked (when offered), Debrid (Torrents › Debrid).

## SHELL-10 Keyboard shortcuts (desktop)

- **Source**: `feature/shell/.../nav/ShellShortcuts.kt:203`, handler `NijiyomeApp.kt:1011`; key routing `desktopApp/.../Main.kt:311,351`.
- Keys reach the shell only when no screen consumed them; text-field input sessions block digits; any open dialog/modal sheet
  (`ModalTracker`) blocks all shell shortcuts.

| Key | Does |
|---|---|
| Ctrl+K | open command palette (SHELL-08). Note: `focusSearchPending` (focus Search field) is never set any more: dead path |
| Ctrl+P / Ctrl+, | open Settings modal (not over Player/readers) |
| Ctrl+B | toggle rail labelled / slim |
| Esc | newest screen back handler (sheet, panel, inline page) first; else pop back stack; over a viewer, viewer's own Esc |
| 1..9, NumPad 1..9 | rail destination N (not while typing, not over viewers) |

- **UX notes**: no in-app list of shortcuts (only footer hints in the palette and the "Ctrl+K" pill). Android with a hardware
  keyboard gets none of these.

## SHELL-11 Incoming links and notification taps (Android)

- **Source**: `app/src/main/AndroidManifest.xml:43-94`, `feature/shell/src/androidMain/.../WaveBLinks.kt:31`,
  `TorrentLink.android.kt:8`, consumers `NijiyomeApp.kt:270-333`.
- **Platform**: android (desktop equivalents in DESK-06/07).

| Input | Opens |
|---|---|
| `nijiyome://extensionserver/connect?server=&code=` | ExtensionServers (asks before pairing) |
| `nijiyome://oauth/<anilist\|mal>?code=&state=` | Settings › Lists and accounts (exchange finishes in background) |
| `nijiyome://user/<name>` (no name = own) | Profile(name) |
| Share image (`ACTION_SEND image/*`) | ImageSearch(uri), searches at once |
| `magnet:` link, `.torrent` file (content/file) | Torrents client with Add sheet prefilled (other inventory) |
| Torrent notification tap | Torrents(section, torrentId); per-torrent section without id -> Library section |
| Download notification tap (extra DOWNLOADS_TAB) | Downloads(tab) |
| Chapter download notification | Downloads(MANGA or NOVELS) |
| Airing notification (DETAILS_ANILIST_ID) | Details(ANIME, id) |
| AniList notifications summary | Notifications |
| Link handoff notification | Linked |

- Each link is pushed once (not duplicated if already on top) and Back returns to where the user was.

---

## HOME-01 Home screen (container and layouts)

- **Source**: `feature/home/src/commonMain/kotlin/app/nijiyome/feature/home/HomeScreen.kt:110` (`HomeScreen`), `:221` (`HomeContent`);
  view model `HomeViewModel.kt:100`.
- **Platform**: both. Entry: bar/rail "Home", launch, palette.
- **Layout setting** (Look › Home, `HomeLayout`): **Spotlight** (full-bleed pager under the status bar, HOME-04), **Carousel**
  (rounded hero carousel, HOME-05), **Rows only** (no hero; Trending becomes the first feed row). Spotlight falls back to
  Carousel when the trending feed errors.
- **Density**: compact density tightens row spacing (4 vs 8 dp). Card size/style from Look (MediaCard vs MediaWideCard).
- **Section order** (top to bottom): Spotlight · Greeting row · Search pill (phones) · Metadata fallback banner · Empty state
  (stops here) · Carousel hero · Continue watching · Downloading · New from shows you follow · Latest episodes · Top 10 · For you ·
  Because you watched … · Genres chips · Feed rows (Airing now, This season, Popular, Top rated; + Trending in Rows layout) ·
  Upcoming premieres · Most favourited · From your extensions · infinite "More <genre>" rows.
- **States**: every row loads, fails (inline compact error + Retry) and retries on its own; skeletons appear after a short delay
  (no flash for fast answers). Status bar gets an opaque scrim once the list scrolls.
- **UX notes**: very long single scroll with 15+ row types and no way to reorder/hide rows on screen; no pull-to-refresh and
  no global refresh (only per-row Retry); "See all" exists only on some rows (inconsistent).

## HOME-02 Greeting row, notifications bell, quick chips

- **Source**: `HomeScreen.kt:384` (`Greeting`), `HomeRows.kt:85` (`NotificationBell`), `:107` (`QuickChips`).
- **Platform**: both. Greeting text hidden on wide when Look › "show greeting" is off and Spotlight is active; quick chips only
  on narrow (< 600 dp).
- **Data**: "Good morning / Good afternoon / Good evening" (by local hour <12, <19).

| Control | Type | Does |
|---|---|---|
| Notifications bell (badge = AniList unread, "99+") | icon button (tonal over Spotlight) | Notifications screen (SOC-03); hidden when Spotlight shows its own bell |
| Calendar | assist chip (phones) | Calendar (also marks "followed" as seen) |
| For you | assist chip (phones) | For you screen |
| Manga | assist chip (phones) | Manga home |
| Novels | assist chip (phones) | Novels home |

- **UX notes**: on phones the chips duplicate the Calendar bar item; on wide there is no Manga/Novels shortcut on Home (rail only).
  Bell badge only counts when the social module is loaded.

## HOME-03 Search entry pill

- **Source**: `HomeScreen.kt:427`. **Platform**: narrow windows only.

| Control | Type | Does |
|---|---|---|
| "Search anime" pill | full-width clickable surface (fake field) | navigateTop(Search) |

## HOME-04 Spotlight hero ("Destacado")

- **Source**: `HomeScreen.kt:552` (`SpotlightSection`), `HomeSpotlight.kt:123` (`Spotlight`), `:218` (`SpotlightPage`).
- **Platform**: both. Height 500 dp phone / 440 dp wide; Editorial page style caps it at 45 % of window height (min 260 dp).
- **Data**: first 5 Trending titles, ordered banner-first; backdrop = ani.zip fanart > banner > cover; clear logo image instead
  of title text when available; fact pills (episodes, format, status with colored dot, season + year, score % with star; hidden
  in Editorial); up to 4 genres joined by " · "; synopsis (hidden in Editorial).
- **Autoplay**: Look › hero autoplay seconds (off when Motion = Off, reduce motion, or 1 item); pauses while a finger is down;
  segmented progress bar fills per page.

| Control | Type | Does |
|---|---|---|
| Swipe | horizontal pager gesture | change title |
| Progress segments (one per title) | clickable bars (24 dp hit) | jump to title N ("Go to N of M") |
| Watch / "Continue ep. N" | button | plays ep 1, or resumes the history entry if the title was started |
| Add to list / On list | tonal icon button (bookmark) | adds to Plan to watch (snackbar "added" / "failed"); no-op if already on a list; hidden without lists service |
| Details (i) | tonal icon button | opens Details |
| Notifications bell | tonal icon button (top-end) | Notifications |

- **States**: skeleton while loading; empty -> just status-bar spacer; error -> Carousel fallback.
- **UX notes**: list button cannot remove or change status (one-way); no pause/play control for autoplay; tapping the backdrop
  does nothing (only the buttons act), unlike the Carousel where the slide opens Details.

## HOME-05 Carousel hero

- **Source**: `HomeScreen.kt:451` (`HeroSection`), `:487` (`HeroSlide`); `HeroCarousel` from the design system.
- **Platform**: both. Height 260 dp.
- **Data**: banner/cover, "#N Trending" kicker with bolt icon, status pill (Airing / Finished / Upcoming / Cancelled / On hiatus),
  title (2 lines), synopsis (2 lines).

| Control | Type | Does |
|---|---|---|
| Slide | clickable card | Details |
| Watch | button | Player ep 1 |
| Details | tonal button | Details |
| Swipe / autoplay | carousel | next slide (autoplay ms from Look, default 9 s) |

- **States**: skeleton, compact error with Retry (rate-limit aware, UI-02).

## HOME-06 Metadata fallback banner (Home)

- See UI-01. Shown under the greeting when the primary metadata provider is rate-limited. Action **Retry** (retry primary
  provider). Auto-recovers when the cooldown ends.

## HOME-07 Continue watching row

- **Source**: `HomeScreen.kt:606` (`ContinueWatchingRow`), `:631` (`ContinueCard`).
- **Platform**: both. Shown only with history.
- **Data per card (280 dp)**: poster, title (2 lines), "Episode N", "M min left" (if not completed), progress bar.

| Control | Type | Does |
|---|---|---|
| See all | text button in header | Library (Continue tab is default) |
| Card tap | clickable card | opens Details (or ProviderItem page for `ext:` extension history; or plays if no AniList id) |
| Card long-press | long-press (haptic) | same as tap (opens Details) |
| Play (round) | icon button | resumes the episode in the Player (extension titles resume via provider) |

- **UX notes**: tap and long-press do the same thing (long-press has no menu: no "remove from continue watching", no "mark
  watched"). Library › Continue tab tap plays directly, Home tap opens Details: inconsistent.

## HOME-08 Downloading row

- **Source**: `HomeRows.kt:131` (`DownloadingRow`), `:146` (`DownloadChip`).
- **Platform**: both. Shown while torrents or web downloads are active.
- **Data**: header "Downloading (N)"; per item poster, title/series, "Episode N" or "Torrent", "· NN%", progress line.
- **Actions**: none (cards are not clickable).
- **UX notes**: no tap-through to Downloads/Torrents, no pause/cancel; a "See all" -> Downloads would be expected.

## HOME-09 Followed and latest episodes rows

- **Source**: `HomeRows.kt:211` (`homeDiscoverTop`), `:350` (`airingItems`).
- **Platform**: both.
- **Rows**: "New from shows you follow" (followed shows), "Latest episodes" (aired in the last 7 days, one per series, max 20).
- **Data per card**: poster card, "Episode N", "N ago" episode line; mature gating.

| Control | Type | Does |
|---|---|---|
| See all (both rows) | text button | Calendar (marks followed as seen) |
| Card | media card | Details (through mature gate HOME-18) |

- **States**: hidden when empty; skeleton; compact error + Retry.

## HOME-10 Top 10 row

- **Source**: `HomeRows.kt:232-257`, `:365` (`RankedCard`).
- **Platform**: both. Always shown (empty text "Nothing here right now.").

| Control | Type | Does |
|---|---|---|
| Today / This week / This month | segmented control | reloads Top 10 for that window |
| Ranked card (big rank number + card) | media card | Details |

- No "See all".

## HOME-11 For you and "Because you watched" rows

- **Source**: `HomeRows.kt:258-288`.
- **For you**: cards with "NN% match" subtitle; **See all** -> For you screen. Hidden when empty.
- **Because you watched <title>**: from the latest history entry with an AniList id; cards -> Details. No "See all".

## HOME-12 Genre chips

- **Source**: `HomeScreen.kt:691` (`GenreChips`). Genre cloud from the discover service, else top 10 genres of loaded feeds.
- **Platform**: both. Horizontal strip with fading edges; chevrons on desktop/wide (`HorizontalScrollStrip showChevrons`).

| Control | Type | Does |
|---|---|---|
| Genre chip (localized label) | assist chip | `SearchFor(genre)` |
| Scroll chevrons | icon buttons (strip) | scroll the strip |

## HOME-13 Feed rows

- **Source**: `HomeScreen.kt:709` (`FeedRowSection`); feeds `HomeViewModel.kt:94`.
- **Rows**: Airing now, This season, Popular, Top rated (+ Trending in Rows-only layout).

| Control | Type | Does |
|---|---|---|
| See all | text button | `SearchFor(feed)` (Search with that feed preselected) |
| Card | media card / wide card | Details via mature gate |

## HOME-14 Upcoming premieres and Most favourited

- **Source**: `HomeRows.kt:291`.
- **Upcoming premieres**: subtitle "Winter/Spring/Summer/Fall YYYY", "YYYY" or "To be announced". **Most favourited**: plain cards.
- Cards -> Details. No "See all" on either.

## HOME-15 Extension rows ("From your extensions")

- **Source**: `HomeScreen.kt:751` (`ExtensionRows`). Requested lazily when scrolled into view.
- **Data**: one row per extension `custom/home` row: title, "Row from the <extension name> extension", cards (poster + title).

| Control | Type | Does |
|---|---|---|
| See all (row header) | text button | Provider page (extension source, other inventory) |
| Item card | media card | ProviderItem page |
| Retry | button in error | reload extension rows |

- **States**: skeleton; error "The extension rows could not be loaded." + reason + Retry; hidden when no extension has rows.

## HOME-16 Infinite genre rows

- **Source**: `HomeRows.kt:325`. A sentinel at the bottom appends one "More <genre>" row at a time until every cloud genre has one.
- **See all** -> `SearchFor(genre)`; cards -> Details; per-row Retry.

## HOME-17 Home empty state

- **Source**: `HomeScreen.kt:287`. When every feed is empty, nothing to continue and no discover content.
- "Nothing to show yet" / "Check your connection or reorder the metadata sources in Settings." No action button.
- **UX note**: tells the user to go to Settings but offers no button to it.

## HOME-18 Mature content gate (cards and dialog)

- **Source**: `feature/home/.../MatureContent.kt:56` (`MatureGate`), `:79` (dialog), `:108` (`GatedMediaCard`).
- **Platform**: both. Mode from Settings (Show / Blur / Warn). Mature = genre "Hentai".
- **Blur**: card cover blurred with "Mature · tap to show"; first tap reveals, second opens.
- **Warn**: dialog "Mature content" / "<title> is marked as adult content. Open it anyway?"

| Control | Type | Does |
|---|---|---|
| Open | dialog confirm | accepts this title for the session and opens it |
| Cancel | dialog dismiss | closes |

---

## CAL-01 Airing calendar

- **Source**: `feature/home/.../CalendarViews.kt:110` (`CalendarScreen`), VM `DiscoverViewModels.kt:154-240`.
- **Platform**: both. Entry: bar (phones) / rail, Home quick chip, "See all" of followed/latest rows, palette. No back arrow
  (top-level). Default view from Look settings; the view picked here is saved back.
- **Top bar**: "Airing calendar".

| Control | Type | Does |
|---|---|---|
| Today | icon button (top bar) | jump anchor to today |
| Week / Agenda / Month / Day | segmented (icons + labels, full width) | change view |
| Previous / Next | icon buttons (chevrons) | shift by week / month / day depending on view |
| Period title | text (animated) | "Week of <date>", "Month YYYY", full date for Day |
| Only the ones I follow | filter chip | filter to followed shows |
| Day cell (phone week strip: weekday, day number, episode count) | selectable surface | selects day, list below |
| Month grid cell (day number, up to 3 dots / "+N" on wide, count on phone) | clickable cell | selects day |
| Episode row (cover, title, "Episode N · time") | clickable row | Details (`openMedia`) |
| Follow / Unfollow (bell+ / bell active) | icon button per episode row | toggles follow; asks notification permission when following |
| Wide week column card (title, "Episode N") | clickable card | Details (no follow button here) |
| "Shows you follow" list: reminder | icon toggle (bell) | per-show "Notify me when an episode airs" (disabled if platform cannot notify) |
| "Shows you follow" list: Unfollow | text button | unfollow |

- **Views**: Agenda (day headers for days with episodes), Week (phone: 7-day strip + selected day list; >= 720 dp: 7 columns,
  horizontally scrollable below 7 × 140 dp), Month (grid + selected day list), Day (hour headings + "now" line).
- **Highlighting**: rows of tracked titles (followed or in "Watching") use secondary container color.
- **States**: loading (no explicit item), error + Retry, empty "No episodes this week" / "AniList has no airing episodes in
  this week. Try another week." or "None of the shows you follow airs this week."; empty day text.
- **UX notes**: follow toggle exists in list rows but not in wide week cards or month cells; reminder per show only reachable in
  the bottom "Shows you follow" list (Details has no reminder switch: `FollowShowRow` with that switch is dead code,
  `DiscoverScreens.kt:323`).

## FYOU-01 For you (recommendations)

- **Source**: `feature/home/.../DiscoverScreens.kt:168` (`ForYouScreen`), `:203` (`RecsList`), `:243` (`RecRow`).
- **Platform**: both. Entry: rail (wide), You first row (phones), Home row "See all", quick chip, palette. Back arrow on phones.

| Control | Type | Does |
|---|---|---|
| List / Atlas | segmented (icons) | switch tab |
| Taste profile chips | info chips (not clickable) | "Your taste" top 6 genres |
| Recommendation row | clickable row | Details |
| Not interested | text button per row | hides it; snackbar "Hidden from your recommendations" + **Undo** |

- **Data per row**: cover, title, "NN% match" pill, reason chips ("You like X", "Made by X", "From the X", "Highly rated, like
  your favourites", "Similar to X").
- **States**: delayed loading, error + Retry, empty "No recommendations yet" / "Watch a few episodes or link AniList / MAL ...
  computed on this device."

## FYOU-02 For you › Atlas

- **Source**: `DiscoverScreens.kt:265` (`AtlasTab`), `:278` (`AtlasCanvas`).
- Canvas of dots (filled = watched, ringed = recommended, colored by cluster); legend chips.

| Control | Type | Does |
|---|---|---|
| Tap a dot (28 dp hit radius) | canvas tap | Details of that title |

- **States**: loading, error + Retry, empty "The atlas needs some watch history first".
- **Data**: hint line "Every dot is a title; close dots are similar. Ringed dots are recommendations. Tap one to open it."
- **UX notes**: no labels/hover on dots (desktop gets no tooltip), no zoom/pan.

---

## SRCH-01 Search screen: top bar and query

- **Source**: `feature/search/src/commonMain/kotlin/app/nijiyome/feature/search/SearchScreen.kt:120`, `:294` (`SearchTopBar`);
  VM `SearchViewModel.kt` (debounce 350 ms, `:558`).
- **Platform**: both. Entry: bar/rail Search, Home pill, palette catalog row, `SearchFor` from Home "See all", genre chips, Details
  genre/studio chips. Two-pane list pane.

| Control | Type | Does |
|---|---|---|
| Query field ("Search anime" / "Search characters") | text field, IME Search | live search after 350 ms debounce; IME Search submits + saves to recent |
| Clear (x) | icon button (when text) | clears query |
| Search by image | icon button | ImageSearch (IMG-01) |
| Filters (badge = active count) | icon button | opens filters sheet (SRCH-09); only for Anime type with Metadata source |

## SRCH-02 Search chips row

- **Source**: `SearchScreen.kt:387` (`ChipsRow`). One horizontally scrolling row (no chevrons).

| Control | Type | Does |
|---|---|---|
| Anime / Characters | filter chips (single select) | search type |
| Manga | filter chip (never selected) | opens Manga home with the typed query |
| Novels | filter chip (never selected) | opens Novels home with the typed query |
| Metadata | filter chip | source = metadata providers (default) |
| <Extension name> (puzzle icon) | filter chip toggle | search that extension's own catalog |
| <Extension> without catalog | disabled chip; tap shows snackbar "<name> has no searchable catalogue of its own" | info |
| Active feed (e.g. "Trending") | removable chip | clears the feed |
| "Studio: X" | removable chip | clears studio browse |
| Each genre / #tag / format / year / season / status / year range / episodes range / score range / sort | removable chips | removes that filter |
| "18+ included" | warning pill (not removable here) | info |

- **UX notes**: type, source, navigation (Manga/Novels) and active filters are mixed in one undifferentiated chip row; Manga/
  Novels look like filters but navigate away; genre removable chips show raw English genre names (no `genreLabel`).

## SRCH-03 Trend window (Trending feed)

- **Source**: `SearchScreen.kt:357`. Visible when feed = Trending, empty query, Anime, Metadata.
- Segmented small: Today / This week / This month / All time.

## SRCH-04 Search idle content

- **Source**: `SearchScreen.kt:511` (`IdleContent`).
- **Data**: empty-state header (anime / characters / extension variants), "Explore" feed chips, "Recent searches" (max 6, local).

| Control | Type | Does |
|---|---|---|
| Explore: Trending, Popular, Top rated, Airing now, This season, Upcoming, Recently completed | assist chips (flow row) | select feed |
| Recent search row | clickable row | runs that query |
| Remove (x) per recent | icon button | removes it |
| Clear all | text button | clears recent searches (no confirm here; Settings version confirms) |

## SRCH-05 Search results (anime)

- **Source**: `SearchScreen.kt:606` (`ResultsList`), `:686` (`ResultRow`), `:594` (`LoadMoreFooter`).
- **Data per row**: cover (blurred if mature + Blur mode), title in preferred language, "alt title · year · season", score pill
  (x.x), status pill (colored), "18+" pill, "Adult content · tap to show" when blurred. Header "N results".

| Control | Type | Does |
|---|---|---|
| Result row | clickable row | Details (first tap reveals when blurred) |
| Infinite scroll | auto load on footer | next page |
| Load more | outlined button (after a failure) | retry next page |

- **States**: delayed loading (after 150 ms), error + Retry, empty "No results" with query text, "Reset" action when filters
  are active; all results filtered out locally -> empty + Reset.
- **UX notes**: list only (no grid view, no sort control outside the sheet, no "add to list" from results).

## SRCH-06 Character results

- **Source**: `SearchScreen.kt:634`. Rows (image, name ...) -> opens the character page **inline** inside Search
  (`SearchScreen.kt:148-158`, back closes it), not the `Character` route. Infinite paging + Load more.
- **UX notes**: inline page means no two-pane detail and different back behaviour vs Details › Characters (which pushes a route).

## SRCH-07 Extension catalog results

- **Source**: `SearchScreen.kt:658`. Rows with hint "From the extension · tap to find it in your metadata".
- Tap: switches back to Metadata source and searches the item's title (does not open the extension item).
- **UX note**: indirect; Home extension rows open the ProviderItem page instead (inconsistent).

## SRCH-08 Inline CharacterScreen in Search

- Same screen as CHAR-01, hosted inside Search; Esc/back closes it.

## SRCH-09 Search filters sheet

- **Source**: `feature/search/.../SearchFiltersSheet.kt:76`. `AdaptiveSheet` (bottom sheet / dialog). Draft state; applies only on
  **Apply filters**.

| Group | Control type | Values |
|---|---|---|
| Sort by | single-select chips (tap again clears) | Relevance, Popularity, Trending, Score, Start date, Title, Favourites, Episodes |
| Genre | multi-select chips | genre options from the provider (raw names) |
| Format | multi-select chips | TV, TV Short, Movie, OVA, ONA, Special, Music |
| Status | single-select chips | Airing, Finished, Upcoming, Cancelled, On hiatus |
| Year | single-select chips | next year down to 10 years back (12 chips) |
| Season | single-select chips | Winter, Spring, Summer, Fall |
| Year range | range slider | 1960 .. next year (ends = no limit, label "Any") |
| Episodes | range slider | 0 .. 200 |
| Average score | range slider (steps of 5) | 0 .. 100 |
| Show spoiler tags | switch | shows spoiler tags in the list |
| Filter tags | text field | filters tag chips |
| Tags | multi-select chips (24 shown) + "Show all N tags" text button | AniList tag catalog; loading / "The tag list is not available right now." |
| Include 18+ titles | switch (with hint) | adult results |
| Clear | text button | resets and applies immediately |
| Apply filters | button | applies draft |

- **UX notes**: long single sheet; Year chips and Year range slider overlap in meaning; genre chips not localized; no count
  preview; sliders have no numeric input.

## IMG-01 Image search (trace.moe)

- **Source**: `feature/search/.../ImageSearchScreen.kt:86`, VM `ImageSearchViewModel.kt`.
- **Platform**: both; desktop adds drag-and-drop and a URL field (`SearchPlatform.desktop.kt` `imageDropAndLinks = true`).
- **Entry**: Search top bar icon, Android share-to-app. No palette entry.

| Control | Type | Does |
|---|---|---|
| Back | icon button | back |
| Pick an image (top bar) | icon button | system picker `image/*` |
| Pick an image (empty-state action) | button | same |
| Drop zone (whole screen, title switches to "Drop to search") | drag-and-drop target (desktop) | searches dropped image file or link; non-image -> snackbar "not an image" |
| Image link (http / https) + Search | text field + button / IME Search (desktop) | searches by URL |
| Minimum similarity: NN% | slider 50..100 % | hides matches below; "N more below the threshold" |
| Match row | clickable row | resolves and opens Details; failure snackbar "That title could not be opened." |
| Retry | error button | retry |

- **Data per match**: preview frame (16:9), title, "Episode N at mm:ss" / "At mm:ss", similarity pill (green >= 95, amber >= 85, red).
- **States**: idle, loading (picked preview + spinner), unreadable image, too large, error, empty "no match" + pick again,
  "opening" overlay.

## SRCH-10 Discover settings extras (embedded in Settings › Metadata)

- **Source**: `feature/search/.../DiscoverSettingsExtras.kt:54`. Rendered by the settings screen (other inventory) but code lives here.

| Control | Type | Does |
|---|---|---|
| Title language: Romaji / English / Native | segmented | `titleLanguage` (Search, Details, cards) |
| Show 18+ content | switch | initial value of Search 18+ toggle |
| Mature content: Show / Blur / Warn | segmented | `matureContent` |
| Provider switches (AniList, Kitsu, ani.zip, MAL, Shikimori, Tenrai) + live status text | switches | enable/disable provider; last one cannot be turned off |
| Clear recent searches (count) | outlined button -> confirm dialog | clears history; snackbar "Recent searches cleared" |

---

## DET-01 Details screen (anime): container and states

- **Source**: `feature/details/src/commonMain/kotlin/app/nijiyome/feature/details/DetailsScreen.kt:119`, `:398` (`DetailsContent`);
  VM `DetailsViewModel.kt`.
- **Platform**: both. Entry: any media card/row (Home, Search, Calendar, For you, Library, Character, Related, social), Android
  airing notification, Player end screen "Back to details". Detail pane on two-pane windows.
- Seeded theme: the page recolors from the title's AniList color (`NijiyomeSeedTheme`).
- **States**: loading / error + Retry (`LoadStateContent`, UI-03); adult gate (DET-01a); metadata fallback banner under the header.
- Desktop: the title is published to Discord presence while visible (`DetailsPlatform.desktop.kt:11`).

### DET-01a Adult gate

- **Source**: `DetailsScreen.kt:671`. "Adult content" / "This title is marked 18+ on AniList. Show it anyway? Nijiyome remembers
  your choice for this title."

| Control | Type | Does |
|---|---|---|
| Go back | outlined button | back |
| Show | button | remembers per title, shows page |

## DET-02 Nested details stack

- **Source**: `DetailsScreen.kt:147-175`. Related / seasons / recommendations / graph nodes open another Details **inside** the
  same entry (stack of ids, back pops). Not visible in the rail/back stack.
- **UX note**: back from a nested Details stays inside; the title-bar back button and Esc pop it via the back dispatcher.

## DET-03 Top bar (floating and pinned)

- **Source**: `DetailsScreen.kt:438-511`.

| Control | Type | Does |
|---|---|---|
| Back (floating, translucent) | tonal icon button, before scrolling | back |
| Back (pinned bar) | icon button, after scrolling | back |
| Title (pinned bar) | text | display title in chosen language |
| "▶ Ep. N" | tonal button (phones, pinned bar, when playable) | same as Watch |
| Pinned tabs | tab row copy (once list tabs pass under the bar) | switch tab and scroll to tab top |

## DET-04 Header (editorial / phone compact / wide)

- **Source**: `DetailsSections.kt:191-215`, `:264` (`Header`, wide), `DetailsPhone.kt:93` (`CompactHeader`),
  `DetailsEditorial.kt:38` (`EditorialHeader`), `DetailsPhone.kt:158` (`TitleQuickActions`), `:242` (`MatureReveal`).
- Variant: Look › page style Editorial -> editorial hero (fraction of height); otherwise phone compact header (fanart + overlapping
  poster) or wide header (backdrop, 114 dp poster, title, alt title, synopsis).
- **Data**: backdrop (fanart > banner > cover), poster, title (selectable text), alternative title (wide), "+18" badge, synopsis
  (wide; 4 lines, toggle when > 120 chars), facts line or grid (DET-05).

| Control | Type | Does |
|---|---|---|
| Title text | selectable text (long-press / drag select) | copy part of the title |
| Copy title | small text+icon button | copies; label flips to "Title copied" 1.8 s + snackbar "Title copied" |
| AniList / MAL | small text+icon button | opens anilist.co or myanimelist.net page in browser |
| More / Less (synopsis) | text button (wide header) | expand synopsis |
| Tap to show (blurred art) | clickable area | reveals blurred cover (Blur mode) |

## DET-05 Header body layouts and facts

- **Source**: `DetailsHead.kt:144` (`HeaderBody`), `:254` (`FactsLine`), `:330` (`FactsGrid`), `:638` (`NextUpCard`).
- Look › header layout (`HeaderLayout`): **LINE** (watch button, source line, boxed icon actions), **FACTS** (facts grid, watch
  button, action chips, source line with episode), **NEXT_UP** (plain icon actions + Next-up card), **FACTS_NEXT_UP** (facts grid +
  chips + card beside when >= 720 dp, else stacked). On >= 840 dp the card moves to the side column.
- **Facts**: Status (live dot when airing), Next episode + countdown ("2 d 5 h"), Episodes (watched / total), Year, Score (x.x);
  "18+"; spoken TalkBack descriptions.
- "Sending…" row while a Link send starts.
- **Actions**: none in facts (read-only).

## DET-06 Watch split button

- **Source**: `DetailsPhone.kt:299` (`WatchButton`), labels `:271`.
- **Labels by state**: Watch episode N / Start · Continue episode N · Watch again (from ep 1) · Up to date (next not aired) ·
  Coming soon. Sub-line "3:39 of 24 min" / "Resume at 3:39"; thin progress bar inside.

| Control | Type | Does |
|---|---|---|
| Main part | button (disabled when not playable) | plays the next/continue episode with the remembered/auto source, resuming position |
| Chevron (v) | button | opens source picker (DET-20), plays on pick; disabled without extensions/panel |

## DET-07 Header actions (List, Favorite, Follow, Trailer, More)

- **Source**: `DetailsHead.kt:455` (`HeadActions`), `:568` (`HeadAction`). Rendered as boxed icons, plain icons, or chips
  depending on DET-05 layout.

| Control | Type | Does |
|---|---|---|
| List (label = status or "List") | icon/chip, opens menu | list menu (LIB-07); hidden without lists service |
| Favorite | toggle icon/chip | toggles favorite (AniList/MAL); offline + linked -> snackbar "Saved. It will sync when you are back online."; failure snackbar |
| Follow / Following | toggle icon/chip (only airing/upcoming anime with airing service) | follows for calendar + reminders; asks notification permission; denied -> "Following without reminders..." snackbar; failure snackbar |
| Trailer | icon/chip (when trailer) | opens trailer URL in browser |
| More | icon/chip -> menu (DET-09) | more actions |

## DET-08 List menu and editor (from Details)

- See LIB-07 (`ListButton`) and LIB-06 (`ListEntryEditor`).

## DET-09 "More" menu

- **Source**: `DetailsPhone.kt:398` (`MoreActionsButton`). Dropdown menu.

| Item | Type | Does |
|---|---|---|
| Send to device / Sending… | menu item (when a PC is paired; Android) | send dialog (DET-13) or Linked device screen when not paired |
| Download episode N | menu item (best torrent source exists) | download through the "I understand" download gate (torrent inventory) |
| Download season | menu item (best torrent is a batch) | download season |
| No torrent source for this episode | disabled item | info |
| Share | menu item (has AniList/MAL id) | copies link; snackbar "Link copied" + **Send** (Android system share sheet; desktop only copies) |

- **UX notes**: downloads, sharing and device send are buried in an overflow; desktop "send to phone" is only in the side panel
  (the `SendToPhoneButton` toolbar menu `DetailsSections.kt:525` and `DownloadMenuButton` `:341` are unused code).

## DET-10 Next-up card

- **Source**: `DetailsHead.kt:638`.
- **Data**: optional episode thumbnail (editorial, full fit), kicker ("Episode N of M", "Next ep N in 2 d 5 h", "All watched",
  "Coming soon · ...", movie label), episode title, progress bar, "watched / remaining" or "Resume at".
- **Controls**: Watch split button (short label), Source line (DET-11) as footer.

## DET-11 Source line

- **Source**: `DetailsPhone.kt:463`.
- **States (colored dot)**: not aired ("Episode N has not aired yet"), no extension ("No source installed" + **Open
  Extensions**), searching ("Looking for sources…"), error (+ **Retry**), no sources ("No source available" + **Manage
  extensions**), OK ("Source: <summary>", optional "Ep N ·", "Change (n)").

| Control | Type | Does |
|---|---|---|
| Whole row (when OK) | clickable row ("Change source") | source picker without auto-play |
| Open Extensions / Manage extensions | text button | Extensions |
| Retry | text button | retry source resolution |

## DET-12 Source panel card (wide side column)

- **Source**: `DetailsSections.kt:387` (`SourcePanelCard`).

| Control | Type | Does |
|---|---|---|
| "Source selection: Automatic/Manual" + settings link | text button | Settings › Player |
| Chosen source row (SourceRow of :feature:player) | clickable row | picker |
| Download (torrent sources) | icon on source row | download this source |
| "Picked by the system / Picked by you" | text | info |
| Open Extensions (empty) | empty-state action | Extensions |
| Retry | error action | retry sources |

## DET-13 Send to device / phone (Link)

- **Source**: `DetailsSections.kt:466` (`SendToDevicePanel`), `:501` (`SendToPhonePanel`), dialogs `DetailsScreen.kt:594`, `:636`.
- **Platform**: Android sends to a paired PC; desktop sends to paired phones.

| Control | Type | Does |
|---|---|---|
| Pair a PC (no PC paired) | text button | Linked device screen |
| Send / Sending… (PC panel; "Connected"/"Not answering") | button | confirm dialog |
| Dialog "Send to device": Send | dialog confirm (only when PC reachable) | sends; result snackbars |
| Dialog: Check again | dialog button (PC unreachable) | re-check reachability |
| Dialog: Cancel | dialog dismiss | close |
| Per phone: Send ("Connected now"/offline) | button (desktop) | confirm dialog -> send to that phone |

- **Feedback snackbars**: sent to <phone>; failure reasons (source denied, unavailable, profile unavailable, PC offline, not
  paired, timeout, other); "sent other source/torrent"; no web source.

## DET-14 Tabs

- **Source**: `DetailsSections.kt:550` (`DetailsTabs`). Scrollable primary tab row: Episodes · Info · Characters · Related.

## DET-15 Episodes tab

- **Source**: `DetailsSections.kt:585` (`EpisodeToolbar`), `:623` (`episodeItems`), `:811` (`DetailsEpisodeRow`),
  `DetailsEpisodes.kt:119` (`EpisodeCard`), `:215` (`CompactEpisodeLine`).

| Control | Type | Does |
|---|---|---|
| Search episode | text field + clear | filter by number/title ("No episode matches") |
| Cards / List / Compact | segmented, icon-only | episode view (saved in Look) |
| Range chips "1–100", "101–200"... | filter chips (strip with chevrons on wide) | page of 100 episodes |
| "Earlier episodes (a–b)" | clickable row | reveals watched episodes hidden above the current one |
| Episode row/card/line tap | clickable (disabled when unaired) | plays that episode (auto/remembered source) |
| Episode row "⋮" (list view) | icon button -> dropdown | Mark episode N as watched / not watched; Download episode N |
| Mark watched (card view) | icon button | toggle watched (updates list progress/status) |
| Download (card view) | icon button | download episode via gate |

- **Data**: thumbnail (fallback art), "N. Title", rating star badge, runtime badge, air date / "Not aired yet", Filler, Downloaded,
  "Resume at mm:ss" on current, "Next" tag, watched check, progress bar; current row highlighted; unaired dimmed.
- **States**: loading, error + Retry, empty "No episode list".
- Below the list: Download season slot (downloads inventory) and AniList reviews slot (SOC-04).
- **UX notes**: mark watched is a toggle icon in Cards but buried in "⋮" in List; Compact has neither; no bulk "mark up to here".

## DET-16 Info tab

- **Source**: `DetailsSections.kt:943` (`InfoTab`), `:993` (`FactsCards`), `:1071` (`TagsCard`).

| Control | Type | Does |
|---|---|---|
| Genre chips | assist chips | `SearchFor(genre)` |
| Studio chips | assist chips | `SearchFor(studio)` (browse studio) |
| Show spoiler tags | switch | reveal spoiler tags |
| Show all N tags | text button | expand beyond 12 |
| Trailer thumbnail | clickable image + play icon | open trailer URL |
| Official links (one per link) | text buttons | open URL |

- **Data**: Synopsis; Technical sheet (format, source, duration, start/end date, mean score %, popularity, favourites, country,
  producers, studios); tags with relevance %; Identifiers (AniList #, MAL #, Kitsu #, metadata provider, status, season,
  episodes); Other titles (all titles + synonyms).
- **States**: details loading / compact error + Retry.

## DET-17 Characters tab

- **Source**: `DetailsSections.kt:1120`, `:1164`, `:1193`.
- Rows: character image, name, role (Main/Supporting/Background), preferred voice actor + language; infinite paging, "Load more
  characters" after an error; Staff section below.
- Row tap -> Character page (CHAR-01). Empty "No characters" / error + Retry.

## DET-18 Related tab and relations graph

- **Source**: `DetailsSections.kt:1221` (`RelatedTab`), `RelationsGraph.kt`.

| Control | Type | Does |
|---|---|---|
| Season cards ("Season N", "Season N · this one") | media cards (horizontal) | open that season's Details (nested) |
| Graph / List | segmented icon-only | relations view (saved in Look) |
| Graph node | tap | open that title (nested) |
| Expand graph | tonal icon button / tap empty canvas | fullscreen graph dialog |
| Fullscreen: pan / pinch zoom | gestures | navigate graph |
| Fullscreen: Zoom in / Zoom out / Fit | tonal icon buttons | zoom ×1.3, fit |
| Fullscreen: Close | icon button / back | close |
| Relation cards (relation label, kind, year) | media cards | open (nested) |
| Recommendation cards ("N votes") | media cards | open (nested) |

- **States**: loading / compact error + Retry per section; empty "Nothing related yet".
- **UX note**: desktop has no mouse-wheel zoom noted in code (only buttons + transform gestures).

## DET-19 Snackbar events of Details

- **Source**: `DetailsScreen.kt:189-233`. Using <source>; Download added (+ Downloads); Download added via Debrid (+ Downloads);
  Debrid fallback to P2P; download failed; no torrent source; send failures; list update failed; favorite failed; sent to phone;
  phone send failures.

## DET-20 Source picker sheet (navigates into player inventory)

- **Source**: `feature/player/.../sources/SourcePicker.kt:117`, opened `DetailsScreen.kt:295`. Callbacks from Details: pick
  (remembered per episode), retry, open Settings › Player, download a torrent source. Detailed in the player inventory.

## CHAR-01 Character page

- **Source**: `feature/details/.../CharacterScreen.kt:71`.
- **Platform**: both. Entry: Details › Characters row (route `Character`, detail pane), Search character results (inline).

| Control | Type | Does |
|---|---|---|
| Back | icon button | back |
| Favorite character | icon toggle (disabled while saving) | add/remove favorite; failure snackbar |
| More / Less | text button | expand description |
| Appearance cards | media cards (horizontal) | Details |

- **Data**: image, name, "N favourites on AniList", age, birthday, blood type, gender, description, voice actors, appearances.
- **States**: loading / error + Retry.

---

## LIB-01 Library screen (header and tabs)

- **Source**: `feature/library/src/commonMain/kotlin/app/nijiyome/feature/library/LibraryScreen.kt:81`, tabs `LibraryTab.kt:11`.
- **Platform**: both. Entry: bar/rail Library, Home "See all" (Continue), You › History (`LibraryTab(HISTORY)`), palette.
- **Tabs (scrollable)**: Continue · History · Lists · Favorites · Manga · Novels · Torrents (badge = active downloads, Lists and
  Favorites hidden without the lists service).
- **Header**: "Local on this device" pill ("Local" on narrow), "Synced <time ago>" / "Not synced yet".

| Control | Type | Does |
|---|---|---|
| Sync now / Syncing… | text button / progress | history sync (Link); snackbar "History synced" or "Could not sync the history. Try again later." |
| Tab | tab | switch tab (saved) |

- **UX notes**: 7 tabs mixing anime state (Continue/History), list provider data (Lists/Favorites), other media (Manga/Novels)
  and a download client (Torrents); Sync button always shown even with nothing to sync with.

## LIB-02 Continue tab

- **Source**: `LibraryScreen.kt:286`. Adaptive grid (min 110 dp) of poster cards: title, "Episode N" / "Episode N · mm:ss",
  progress.
- Tap -> **plays** the episode (resume). No long-press. Empty "Nothing watched yet" + **Explore anime** (-> Home).

## LIB-03 History tab

- **Source**: `LibraryScreen.kt:323`, `:348` (`HistoryRow`). Rows: thumbnail, title, "Watched" pill, "Episode N · watched <time
  ago>", chevron.
- Tap -> Details (or plays when no AniList id). Empty "No history yet" + Explore anime.
- **UX notes**: no remove entry / clear history / search / filter here.

## LIB-04 Lists tab

- **Source**: `feature/library/.../ListsTabs.kt:117`, controls `:200`, sort `:251`, card `:286`.

| Control | Type | Does |
|---|---|---|
| Status: Watching · Plan to watch · Completed · On hold · Dropped · Rewatching (with counts "· N") | segmented (6 options) | filter by status |
| Filter by title | text field + clear | filter |
| Sort: <value> | button -> dropdown (check on current) | Last updated, Title, Progress, Score |
| "Local list" pill + Link AniList or MAL | pill + text button (no account) | Settings › Lists and accounts |
| Accounts pill (AniList · MyAnimeList) / "N pending" pill | pills | info (pending offline writes) |
| Entry card tap | card | Details (or editor when no AniList id) |
| Entry card long-press | long-press ("Edit…") | List entry editor (LIB-06) |

- **Data per card**: poster, favorite heart, title, "Ep p / total" or "Ep p", "★ score".
- **States**: loading, error "Could not load your list" + Retry, empty "Nothing in <status> yet" (local / linked text, Link button),
  no match.
- **Snackbars**: "Saved. It will sync when you are back online.", "Could not save the change. Try again."
- **UX notes**: 6-option segmented on a phone is cramped; edit only via long-press (not discoverable, no desktop right-click).

## LIB-05 Favorites tab

- **Source**: `ListsTabs.kt:324`, `:396`.
- Favorite characters row (avatar + remove "x" icon button "Remove X from favorites"), "Titles" grid (cards as Lists, heart
  badge); tap -> Details, long-press -> editor.
- Empty "No favorites yet" (local / linked text) + Link button; error + Retry.

## LIB-06 List entry editor sheet

- **Source**: `ListsTabs.kt:425` (`ListEntryEditor`). AdaptiveSheet; each change is written immediately.

| Control | Type | Does |
|---|---|---|
| Status (6 values) | single-select chips (horizontal list) | set status |
| Progress − / + | tonal icon buttons ("Ep p / total") | change progress (bounded by total) |
| Score | slider 0..100 step 5 ("Score: N / 100" / "No score") | set score on release |
| Favorite / Remove favorite | outlined button | toggle favorite |
| Remove from list | text button (when on a list) | removes entry, closes |

## LIB-07 List button and menu (shared)

- **Source**: `ListsTabs.kt:527` (`ListButton`). Used by Details header (DET-07) and other screens.

| Item | Type | Does |
|---|---|---|
| Anchor (label = status or "Add to list", heart if favorite) | tonal button / custom anchor | opens menu |
| Watching … Rewatching (check on current) | dropdown items | set status |
| Edit… | dropdown item (on a list) | editor sheet LIB-06 |
| Favorite / Remove favorite | dropdown item | toggle favorite |
| Remove from list | dropdown item (on a list) | remove |

## LIB-08 Manga / Novels / Torrents tabs (other inventories)

- Manga (`MangaLibraryTab`) -> Details(MANGA) / Accounts; Novels (`NovelLibraryTab`) -> NovelDetails / Accounts; Torrents
  (`TorrentsLibraryTab`) -> Torrents(FILES, id), play, Debrid. Detailed by the manga/novel and torrents inventories.

---

## YOU-01 "You" page

- **Source**: `feature/you/src/commonMain/kotlin/app/nijiyome/feature/you/YouScreen.kt:89`.
- **Platform**: android (phones' "more" page). Not offered on desktop (needs Lists + Link services as client).
- **Account card**: avatar initial, username or "Guest", "No account linked · lists and history on this phone" or providers,
  history sync line ("History synced with <PC> <time>" / "not synced with <PC> yet" / "History only on this phone · link a PC").

| Control | Type | Does |
|---|---|---|
| Sync now / Syncing… | tonal button (only when a PC is paired) | history sync; snackbar ok / fail + **Retry** |
| Connect AniList or MAL / Lists and accounts | tonal button | Settings › Lists and accounts |
| For you | list row | For you |
| Manga ("Trending, reading list and chapters") | list row | Manga home |
| Novels | list row | Novels home |
| AniList profile | list row | Profile (own) |
| Notifications | list row | Notifications |
| History (count) | list row | Library › History |
| Downloads (active / total / none, badge) | list row | Downloads |
| Torrents ("Cliente BitTorrent, añadir magnet y Debrid" `[hard-coded ES]`) | list row | Torrents |
| Extensions (count / "None installed") | list row | Extensions |
| Linked device (none / problem / "<PC> · PC connected" / out of reach) | list row | Linked device |
| Settings ("Appearance · Player · Torrents") | list row | Settings |

- **States**: skeleton, error "Could not load your profile" + Retry.
- **UX notes**: three card groups but no headings; one subtitle hard-coded in Spanish.

---

## SOC-01 AniList profile

- **Source**: `feature/social/src/commonMain/kotlin/app/nijiyome/feature/social/SocialScreens.kt:128`.
- **Platform**: both (needs SocialRepository). Entry: rail Profile (own), You row, `nijiyome://user/<name>`, user taps in
  activity/notifications/followers.

| Control | Type | Does |
|---|---|---|
| Back | icon button (not on own profile as rail destination) | back |
| Refresh | icon button | reload |
| Open in AniList | icon button | browser |
| Edit profile (own) | outlined button | AniList settings page in browser |
| Follow / Following (others) | button / outlined (busy-disabled) | toggle follow |
| Followers count / Following count | clickable texts (header) | switch tab |
| Activity · Statistics · Favourites · Followers · Following | filter chips (tabs) | switch tab |
| Status composer "Write a status…" + Post | text field + icon button (own, Activity tab) | post; snackbar "Status posted" |
| Only people I follow | filter chip (own) | activity filter |
| Activity card | card | ActivityThread |
| Avatar in card | clickable avatar | that user's profile |
| Media line in card | clickable row | Details |
| Like (count) | text button with heart | toggle like |
| Replies (count) | text button | ActivityThread |
| User row (followers/following; "You follow each other" etc.) | clickable row | profile |
| Favourite anime/manga cards | media cards | Details |
| Load more | outlined button / auto paging | next page |

- **Data**: banner, avatar, name, "Follows you" pill, stats (Anime, Episodes, Days watched, Mean score, Manga, Chapters read),
  favourite characters.
- **States**: loading, "AniList did not answer" + Retry, "Connect AniList" + Open accounts (not linked), empty activity / follow /
  stats / favourites.
- **UX notes**: tabs are filter chips (not tabs); count texts double as tab switches (hidden affordance).

## SOC-02 Activity thread

- **Source**: `SocialScreens.kt:402`. Top bar "Activity", Back, Open in AniList; full activity card (like); replies list (paged);
  bottom composer "Write a reply…" + Reply (icon button).
- Empty "No replies yet. Be the first."; error + Retry.

## SOC-03 AniList notifications

- **Source**: `SocialScreens.kt:494`, row `:571`.
- **Platform**: both. Entry: Home bell, You row, Android notification tap.

| Control | Type | Does |
|---|---|---|
| Back | icon button | back |
| Refresh | icon button | refresh |
| Pull to refresh | gesture | refresh (only pull-to-refresh in the whole scope) |
| Allow (permission card "Allow notifications to get a summary... every 15 minutes") | text button | request OS permission |
| Notification row | clickable row | media Details / activity thread / user profile by type |
| Load more | button / auto | paging |

- **Data**: avatar or media cover, text, unread marker ("Unread"); marks read on resume.
- **States**: loading, error + Retry / Connect AniList, empty "No notifications".

## SOC-04 AniList reviews slot (inside Details › Episodes)

- **Source**: `SocialScreens.kt:601` (`AniListSocialSlot`), `:650` (`ReviewCard`).

| Control | Type | Does |
|---|---|---|
| Write a review | text button | AniList review editor in browser |
| Read more / Show less | text button per review | expand |
| Open in AniList | text button per review | browser |
| Load more | button | next page |

- **Data**: "AniList reviews", mean score, reviewer avatar, "N of M found it helpful", score pill (green >= 70, amber >= 50,
  red); "Your activity on this title" (max 5 lines).
- **UX note**: reviews live under the Episodes tab, not Info/Related.

---

## UI-01 Metadata fallback banner (rate limit)

- **Source**: `core/ui/src/commonMain/kotlin/app/nijiyome/ui/state/MetadataFallbackUi.kt:67`.
- Used by Home (HOME-06) and Details. Text: "<provider> is rate-limited · Using <fallback> · Retrying <provider> in mm:ss" or
  "... Retrying in mm:ss…". Action **Retry**.

## UI-02 App error state (rate-limit aware)

- **Source**: `MetadataFallbackUi.kt:157` (`AppErrorState`). Generic error + **Retry**; for HTTP 429 a live countdown
  ("Retrying with <next> in N…" or "Retrying in mm:ss…").

## UI-03 Load state wrapper

- **Source**: `core/ui/.../state/LoadStateContent.kt:14`. Loading -> delayed spinner; error -> ErrorState + Retry; ok -> content.
  Errors are mapped to user text by `ErrorMapping.kt`.

## UI-04 Media cards

- **Source**: `core/ui/.../media/MediaCard.kt:46,135`, `MediaWideCard.kt:56`, `EpisodeRow.kt:33`.
- Poster card (cover, title, subtitle, progress, status dot) and wide card (backdrop, episode line), sizes S/M/L/XL from Look.
  Single click only: no long-press, no hover overlay, no right-click menu anywhere in this scope.

## UI-05 External drag and drop (desktop)

- **Source**: `core/ui/src/desktopMain/.../platform/ExternalDrop.desktop.kt:7`, common `ExternalDrop.kt`.
- Accepts files (absolute paths, also `file://` text/uri-list) and dropped text (links). Hover callback for highlight.
- **Used by**: Image search (IMG-01) and Extensions screen (install `.kai`, other inventory). Android: no-op.
- **UX note**: no drop target on Home/window level (e.g. drop a magnet or video file on the window is not handled; only command
  line / file association).

## UI-06 Platform services

- `BackHandler.desktop.kt` (Esc dispatcher, SHELL-10), `Connectivity.*` (offline), `NotificationPermission.*` (Android 13+
  prompt; desktop always "granted"), `OpenUrl.*` (browser), `ShareText.*` (Android share sheet; desktop: none, copy only),
  `FilePicker.android.kt` / `SwingChooser.desktop.kt` (pickers), `KeepScreenOn.desktop.kt`, `AppWindow.desktop.kt` (fullscreen and
  picture-in-picture window 480×270, min 320×180, always on top; owned by the player).

---

## DESK-01 Main window

- **Source**: `desktopApp/src/main/kotlin/app/nijiyome/desktop/Main.kt:100-441`.
- Default size 1280×800 × Linux UI scale (Xft.dpi, `LinuxUiScale.kt`); minimum 720×540 (320×180 in PiP); title
  "Nijiyome · Link :<port> · Offline" for taskbar/Alt-Tab; app icon; undecorated unless "native title bar" pref.
- Close button: hides to tray when tray on + "close to tray", else quits. Focus regained -> refreshes AniList unread count.
- Window state (size/position) is **not persisted** between launches (not found in code).
- `--version` / `-V` prints version and exits.

## DESK-02 Custom title bar

- **Source**: `desktopApp/.../system/DesktopTitleBar.kt:87`. 36 dp (28 dp in PiP); hidden in fullscreen. Text `[hard-coded ES/EN]`
  by language check (not resources).

| Control | Type | Does |
|---|---|---|
| Back (‹) | icon button (disabled when stack has 1 entry) | back (sheet/panel first) |
| Drag area | press-drag (native WM move, snapping) | move window; dragging a maximized window restores it under the cursor |
| Double-click on bar | double-click | maximize / restore |
| "Link :<port>" or "<device> · <upscale/frame-gen or title> (+N)" | chip button | Link Monitor dialog (DESK-05) |
| "Offline" | chip | info |
| "Search or command…  Ctrl+K" | pill button | command palette |
| Settings (gear, "Settings (Ctrl+P)") | icon button with hover | Settings modal |
| Minimize / Maximize-Restore / Close (red hover) | window buttons | window controls |

- **UX notes**: no tooltips (only content descriptions); title bar strings are not in resources.

## DESK-03 Resizable frame

- **Source**: `DesktopTitleBar.kt:460`. Invisible 4-5 dp edges and 10 dp corners with resize cursors (native WM resize on X11,
  manual fallback) while floating.

## DESK-04 System tray

- **Source**: `desktopApp/.../system/DesktopShell.kt:209` (`NijiyomeTray`), wiring `Main.kt:199-296`. `[hard-coded ES/EN]`.
- Enabled by pref "tray icon" (Settings, other inventory) and tray availability. If the tray goes away while hidden, the window
  is shown.
- **Tooltip**: "Nijiyome · Link: <device> (<pipeline>)", "Nijiyome · ↓ <rate>", "Nijiyome · Link :<port>", or "Nijiyome".

| Menu item | Type | Does |
|---|---|---|
| Icon click (primary action) | tray click | show window |
| Link status header ("Link active: <device> — <title> · <pipe>", "Link :port · Idle (N devices)", "Link · Stopped") | menu item | show + Link Monitor |
| Link Sessions (N) › one per session | submenu | Link Monitor |
| Torrents (N): ↓ · ↑ › per torrent "name · NN% · ↓ rate", Open Torrents | submenu | show + Torrents |
| "Torrents: idle" | menu item | show + Torrents |
| Performance & Cache › App RAM, Link cache, Disk cache · Torrents (disabled info), Open Link & System Monitor | submenu | info / monitor |
| Show Nijiyome | menu item | show window |
| Link & Status Monitor | menu item | monitor |
| Settings | menu item | show + Settings modal |
| Quit | menu item | exit |

- Telemetry refresh every 3 s (1.5 s while monitor open), quantized and frozen 15 s while the menu is open (KDE flicker fix).

## DESK-05 Link & System Monitor dialog

- **Source**: `desktopApp/.../system/LinkMonitorDialog.kt:101`. `[hard-coded ES/EN]`. Opened from title-bar Link chip and tray.

| Control | Type | Does |
|---|---|---|
| Link Settings | text button | closes, opens Settings modal › Link |
| Close | icon button | close |
| Stop (per active session) | outlined button | stops that Link session |
| Open Torrents | text button | Torrents |

- **Data**: server state (unavailable / stopped / port + sessions / idle + devices); per session: device, title, state
  (Paused / Buffer full / Processing), source kind (Library ...), upscaling / frame-gen chips or "No upscaling", RAM cache bar,
  lead ahead, segments, render pace; active torrents (name, %, progress bar, state · peers, ↓/↑); memory & cache (Link segment
  cache vs per-session budget, JVM RAM, disk cache, torrent storage with free space).

## DESK-06 Single instance and launch arguments

- **Source**: `InstanceLock.kt`, `system/InstanceChannel.kt`, `system/LaunchArgs.kt:38`, `system/DesktopShell.kt:34` (`LaunchRouter`).
- One app per data directory; a second launch forwards its arguments over a local socket and exits; the running app shows its
  window and opens the target. If forwarding fails: error dialog "Nijiyome is already running."

| Argument | Opens |
|---|---|
| `--minimized` | start hidden in tray (only if autostart + start minimized + tray on) |
| `magnet:` / `.torrent` path | Torrents with Add sheet prefilled |
| `nijiyome://oauth/<provider>` | Settings › Lists and accounts (also loopback OAuth listener) |
| `nijiyome://user/<name>` | Profile |
| `nijiyome://extensionserver/connect…` | Extension servers (asks before pairing) |
| `file:` URI or path to a video | Player on the local file (no online lookup) |

- Fatal startup dialogs: data folder unusable; already running.

## DESK-07 Link handlers, autostart, restart

- `system/LinkHandlers.kt`: register as handler for `magnet:`, `.torrent` and `nijiyome:` (Linux desktop entry + mimeapps,
  Windows HKCU). Toggle lives in the wizard and Settings › Data (other inventory).
- `system/Autostart.kt`: "Start with system" (XDG autostart / HKCU Run) with `--minimized`; reconciled at start.
- `DesktopShell.kt:66` (`AppRestarter`): after "Reset the app" the app relaunches itself.

## DESK-08 Discord Rich Presence

- **Source**: `DesktopShell.kt:339`, `system/DiscordPresence.kt`. States: Watching (player episode) > Viewing "<Details title>" >
  Browsing; cleared when the pref is off. Pref in Settings (other inventory).

## DESK-09 Desktop notifications

- **Source**: `system/DesktopNotifier.kt:23`. Title + body only (gdbus / notify-send on Linux, tray balloon on Windows).
  Sources: torrent finished/failed, episode downloaded, airing reminders (`DesktopAiringReminders`), AniList summary
  (`DesktopSocialPoller`).
- **UX note**: unlike Android, desktop notifications have no click action (cannot open Details/Downloads).

## DESK-10 Link remote open ("Continue on PC")

- **Source**: `desktopApp/.../LinkRemoteOpen.kt`. A paired phone's open command pushes a Player route at the phone's position.

---

## Cross-cutting

**Navigation structure**
- Phones: 5-item bottom bar (Home, Search, Calendar, Library, You); everything else via You page, Home chips or in-screen links.
- Wide/desktop: 13-item rail (12 on desktop), labelled or slim; Ctrl+B toggles; 1..9 jump.
- Top-level switch resets stack to `[Home, dest]`; Back returns to Home. Details-family routes are detail panes at >= 840 dp.
- Nested navigation hidden from the stack: Details related titles (DET-02), Search character page (SRCH-06).

**Global search / command**
- Search screen (live, 350 ms debounce, recent searches x6) and desktop-only command palette (Ctrl+K, settings + nav + quick
  toggles + catalog jump). Android has no palette.

**Keyboard (desktop)**: Ctrl+K, Ctrl+P, Ctrl+, , Ctrl+B, Esc, 1..9; palette ↑ ↓ Enter Esc; IME Search in fields. Blocked while
typing (digits) or a modal is open.

**Drag and drop**: desktop external drop only on Image search and Extensions; title bar drag/double-click; resize edges.

**Deep links**: `nijiyome://oauth`, `nijiyome://user`, `nijiyome://extensionserver/connect`, `magnet:`, `.torrent`, share image
(Android), video file argument (desktop), notification extras (Android).

**Tray / window**: tray menu with live Link/torrent/RAM telemetry, close-to-tray, start minimized, autostart, single instance,
PiP window, custom/native title bar, Link Monitor.

**Notifications**: Android (tap routes into the app) vs desktop (no action). Permission asked only when following a show /
notifications screen.

**Feedback**: snackbars only; offline banner + title-bar chip; rate-limit banner with countdown; per-row inline errors with Retry.

**Mature/adult**: three layers: Home/Search gate (Show/Blur/Warn), Search 18+ toggle, Details 18+ gate remembered per title.

**Look settings that change these screens** (owned by the settings inventory; must keep working): Home layout, show greeting,
hero autoplay, card size/style, density, motion, rail style, page style (Editorial), header layout (4), episode view (3),
relations view (2), calendar default view (4), title language.

**Dead or unreachable code worth deciding on** (not user-facing today): `FollowShowRow` (per-title reminder switch),
`DownloadMenuButton`, `SendToPhoneButton`, `SeeAllArrow`, `GenreChip`, Search `focusQuery` (Ctrl+K now opens the palette).

**Top UX pain points (summary)**
1. Command palette fully hard-coded in Spanish, desktop-only, and settings rows only open the section.
2. Search chip row mixes type, source, navigation (Manga/Novels) and active filters; genre chips not localized.
3. Filters sheet is one long list; Year chips and Year range duplicate; no result-count preview.
4. Details downloads/share/send buried in a "More" overflow; mark-watched differs per episode view (icon / "⋮" / none).
5. Home: 15+ row types, no reorder/hide, no pull-to-refresh, "See all" on some rows only; Downloading row not clickable.
6. Continue watching: tap and long-press do the same; Library Continue plays while Home Continue opens Details.
7. List editing via long-press only (no right-click, no visible edit affordance); 6-option status segmented on phones.
8. 13-item flat rail; slim mode has no tooltips; no tooltips anywhere on desktop.
9. Library mixes 7 unrelated tabs (anime state, lists, manga, novels, torrent client).
10. Title bar, tray, Link Monitor and a You subtitle use inline ES/EN strings instead of resources; desktop notifications
    cannot be clicked.

## Counts

- Surfaces: **94** ID'd sections (SHELL 11, HOME 18, CAL 1, FYOU 2, SRCH 10, IMG 1, DET 21 incl. DET-01a, CHAR 1, LIB 8, YOU 1,
  SOC 4, UI 6, DESK 10). Dialogs and sheets inside them (mature dialog, adult gate, send dialogs, graph fullscreen, list editor,
  filters sheet, palette, settings modal entry, Link Monitor, clear-history confirm) are counted inside their parent section.
- Actions: **293** data rows across all controls tables (each row = one control or one input/key/menu item). A few actions are
  described in prose only (SHELL-06 snackbar actions, DET-19 events, DET-17 character row, LIB-02/03 card taps, SOC-02 thread
  composer), so the real total is slightly above 293.
