# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

Nijiyome is one Kotlin + Compose Multiplatform codebase. It ships on Android (phones and tablets) and on Linux and Windows desktops, with one shared design language. Desktop is a first-class target, not a port: custom title bar, tray, native drag and drop, keyboard shortcuts (Ctrl+K command palette, Ctrl+P).

## Users

Power users of anime, manga and light novels (confirmed by the owner, 2026-11-25). They install and configure their own `.kai` extensions, Stremio addons, torrent and debrid services, video shaders and Nijiyome Link, and they tweak settings often. The interface can assume someone who adjusts things, as long as the everyday path (find, continue, watch, read) stays fast.

Main usage scenes (confirmed): a desktop PC at night on a large monitor for long viewing sessions, and a tablet or TV-sized screen from the couch. Manga and novel reading also happen, with readers that matter as much as the player.

## Product Purpose

Watch anime and read manga and novels from sources the user chooses, keeping lists and progress in sync with AniList or MyAnimeList. The app ships no content. Success means the user goes from opening the app to playing or reading the right thing in a few actions, and can reach any advanced control without hunting for it.

## Positioning

All four are differentiators the design should make visible (confirmed):

- **Nijiyome Link:** phone and PC paired; the PC can process video for the phone (upscaling, frame generation) and watch history stays in sync.
- **Enhanced player:** libmpv with Anime4K, FSR, FSRCNNX, CAS, NVScaler, automatic step-down, hardware decoding, external mpv with lsfg-vk frame generation.
- **Three media in one app:** anime, manga and novels, with dedicated readers and a TTS narrator (system voices or KittenTTS on desktop).
- **Open sources:** `.kai` extensions (Tatakai-compatible), Stremio manifests, a built-in libtorrent client with streaming, and debrid (TorBox recommended; Real-Debrid, AllDebrid and Premiumize experimental).

## Operating Context

- Metadata comes from AniList with fallbacks (Kitsu and others); AniList rate limits (HTTP 429) are a normal, visible state.
- Sources are resolved per episode: manual or automatic selection, with quality, codec, audio, subtitle, debrid-cache and reliability attributes.
- Typical loop: Home → Details → episode → source → player → next episode; Library and Calendar drive "what's next".

## Capabilities and Constraints

- The single hard rule for the redesign: **no current functionality may be lost**. The parity inventory lives in `docs/redesign/inventario-A-shell-home-detalles.md`, `inventario-B-reproductor-manga-novela.md` and `inventario-C-ajustes-extensiones-torrents.md`.
- Themes: light, dark and OLED dark are all required. User-selectable palettes stay, but fewer than the current 20.
- Languages: English and Latin-American Spanish in the app.
- Target hardware: high-end and upper-mid devices, plus a performance mode for low-end ones (test phone: moto g51 5G).
- Material 3 governs structure and controls on Android.

## Brand Commitments

- Name: Nijiyome (虹, rainbow). Logo: an inked kitsune with a fan and a play triangle inside an ensō, with the 虹 seal (`packaging/icons/`).
- The owner asked for two identity tracks: one keeping this identity, one free of it.
- Tanzaku (Tanabata paper wish strips) was named by the owner as a motif to explore for cards.

## Evidence on Hand

- Real screenshots of the current app (desktop, Android) and of Hayase: `docs/redesign/capturas/`.
- Reference study of LiMusic Forge (the owner's own app): `docs/redesign/referencia-limusic-forge.md`.
- No user research, usage metrics or testimonials exist; none may be invented.

## Product Principles

1. Nothing is lost: every current capability has a findable home.
2. Choices over typing: chips, segmented controls, multi-select and visual pickers instead of long dropdowns and raw text fields.
3. The everyday path is short; depth is one deliberate step away, never buried.
4. Show the machine: Link, enhancements and sources are visible strengths, with honest states (rate limits, cache, fallbacks).
5. Strong identity and motion that never slow down watching or reading.

## Accessibility & Inclusion

- Respect reduced-motion settings; motion-heavy design ships with a calm mode.
- 48 dp touch targets on Android; keyboard and D-pad focus visible on large screens.
