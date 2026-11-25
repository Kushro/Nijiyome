# Conexiones pendientes de integración

Lo que los carriles dejaron para conectar, tomado de sus informes finales. Se completa cuando terminen S8, S9, S10 y S11.

## Shell (`feature/shell`, `desktopApp`)

- **Movimiento (M1):** envolver el `NavDisplay` en `CardToDetailsHost { … }` (S1 ya lo envuelve: comprobar); agregar un
  `NavEntryDecorator` que ejecute `CardToDetailsEntry(LocalNavAnimatedContentScope.current) { entry.Content() }`; cuando la
  entrada superior es Details usar `LocalMotion.current.cardToDetailsEnterTransform(density)` / `cardToDetailsPopTransform(density)`
  en `transitionSpec` / `popTransitionSpec`; pasar `lowEndDevice = rememberLowEndDevice()` a `rememberMotionChoices`.
  Ver `docs/redesign/carriles/movimiento-api.md`.
- **Regla de equipo lento:** el modo automático solo debe activarse con poca RAM o render por software. Un panel de 60 Hz no
  activa nada: separar la señal de 60 Hz en `rememberLowEndDevice` (por ejemplo una función aparte) para que Ajustes solo sugiera.
- **Explorar (S3):** en `Route.Search` y `Route.SearchFor` pasar a `SearchScreen`:
  `forYouPanel = { onGenre, m -> ForYouPanel(onMediaClick = ::openMedia, modifier = m, onGenre = onGenre) }`,
  `forYouPreview = { onSeeAll, onGenre, m -> ForYouPreview(::openMedia, onSeeAll, onGenre, m) }`,
  `tasteAtlas = { m -> TasteAtlas(::openMedia, m) }`, `onOpenExtensions = { … Route.Extensions }`.
  `Route.ForYou` llama a `SearchScreen` con lo mismo más `initialTab = ExploreTab.FOR_YOU`.
- **Inicio (S2):** `HomeScreen(onOpenDownloads → Route.Downloads, onOpenLink → Route.Linked, onOpenLinkMonitor → monitor de Link,
  onOpenMetadataSettings → Ajustes › Metadatos y descubrir, onOpenExtensions → Route.Extensions)`.
- **Biblioteca (S7):** `LibraryScreen(onOpenTransfers → Route.Downloads, sección torrents)`; `Route.LibraryTab(TORRENTS)` debe ir a
  `Route.Downloads`. `YouScreen(onOpenTorrents)` ya no se dibuja.
- **Ficha (S4):** el reproductor puede pasar `onOpenExtensions` a `SourcePickerSheet`.
- **Capa de soltar (S1/I1):** S1 escribió la suya en `feature/shell/FileDropLayer.kt` porque la de I1 no estaba; decidir si se
  reemplaza por `FileDropOverlay` / `FileDropResult` de `core/ui/patterns` o se deja (no duplicar comportamiento).
- **Medio (S1):** `LocalShellMedium` vive en `feature/shell`; Inicio, Explorar y Biblioteca no lo leen. En navegación «por medio»
  el selector navega entre los inicios de Anime / Manga / Novelas; con eso alcanza salvo que se quiera filtrar Explorar y Biblioteca.
- **Galería de diseño (I1):** `DesignGalleryScreen(onBack)`; S10 la abre dentro de Ajustes. Comprobar que quedó accesible.
- **Salto al ajuste exacto (S1 + S10):** el shell guarda el id en `openSettings(section, itemId)`; pasarlo al parámetro nuevo de
  `SettingsScreen` cuando S10 lo entregue.
- **Aviso tras actualizar:** el primer arranque (S10) debe marcar `introSeen = true`.
- **Notificaciones de escritorio (DESK-09):** `SystemNotifier.notify(title, body)` (core/model) no lleva destino y `DesktopNotifier`
  no escucha el clic. Agregar un destino opcional con valor por defecto y el manejador de clic.
- **Link en escritorio:** el destino Link está oculto porque `Route.Linked` necesita `LinkRepository`; ver informe de S9 y ajustar
  `RouteAvailability`.
- **Textos que aún eligen idioma en código:** diálogo del monitor de Link, etiquetas de estado de torrents en `Main.kt`, título de
  la ventana (`windowTitle`, fijado por una prueba).
- **Logo de Sumi en la barra superior:** `SumiMark(tile = true)` es público, pero el shell no puede preguntar por la dirección.
  Hace falta una ranura de marca en `DirectionDecor` (por defecto el logo actual).
- **Indicador de navegación:** en Obi `secondaryContainer` es tinta; si el indicador del riel debe ser el acento, usar `primary`.

## Contrato (`core/designsystem`, `core/ui`)

- **Datos para la decoración:** `regionModifier(region)` no recibe datos de la pantalla. Faltan: texto de estado y episodio en la
  faja del héroe (Obi), estado de conexión en LINK (Tránsito lo dibuja siempre conectado), marcas de capítulo en PLAYER_SEEK
  (Tránsito no las dibuja), color de texto del tablero de salidas en claro (Tránsito). Propuesta: un `CompositionLocal` de datos de
  región o un parámetro opcional `RegionData` con valor por defecto, y que las pantallas lo llenen.
- **Reproducir desde la tarjeta:** las tarjetas CONTINUE y HERO dibujan un disco de reproducir que solo es visual; la única acción
  es `onClick`. S2 resolvió «Esta noche» con un botón propio bajo la tarjeta. Decidir si se agrega `onPlay` al contrato.
- **Índice de tarjeta:** `TitleCard` no recibe el índice en la fila (Sumi no puede escalonar el trazo del ensō).
- **Balanceo:** `cardSway` rota la tarjeta entera; en «tanzaku sello» debería balancearse solo la tira.
- **Sellos «Nuevo» / «Pronto» de Tanabata** y **banda de novedad de Obi diagonal:** `TitleCardData.isNew` y `airedEpisodes` ya
  existen; D1, D2 y D3 se escribieron antes: hacer que los usen (Tránsito infiere los emitidos de `nextLabel`).
- **Números de ranking** en la fila de tendencias de Explorar (S3 los omitió por la regla de numeración decorativa): restituir,
  son información.
- **Duplicación:** S6 escribió chips, stepper y deslizador propios en `feature/player/sheets/SheetParts.kt` antes de que existieran
  los de `core/ui/patterns`.
- **Código sin uso:** en `feature/details`: `HeaderBody`, `HeadActions`, `MoreActionsButton`, `CompactHeader`, `EpisodeCard`,
  `CompactEpisodeLine`, `DownloadMenuButton`. `PlayerViewModel.setPlayerChromeStyle`. `TrayTexts` + `DesktopLocaleTest`.

## Licencias

Agregar los textos OFL a `assets/licenses/` y las filas a `THIRD_PARTY_NOTICES.md` §3:
Zen Kaku Gothic New, Yuji Boku (Sumi); Dela Gothic One, M PLUS 1 (Obi); M PLUS Rounded 1c, Yuji Syuku (Tanabata);
Atkinson Hyperlegible Next, Overpass (Tránsito). Orígenes en `google/fonts` `ofl/<familia>/`.

## Ajustes que cambian de lugar (para comprobar contra lo que entregue S10)

- Reproductor (S6): cuenta atrás, volumen estable, reproducción en segundo plano y editor de atajos en Reproducción; segundo
  idioma de subtítulos, traducción y editor de estilo en Subtítulos y audio; generación de cuadros de escritorio en Mejoras de
  video; selector de estructura del reproductor en Reproducción.
- Ficha (S4): `headerLayout`, `episodeView`, `relationsView`, `pageStyle` en Apariencia › Ficha, junto a su estructura.
- Explorar (S3): todo `DiscoverSettingsExtras` en Metadatos y descubrir.
- Inicio (S2): estructura de Inicio con autoplay e intervalo del héroe al lado; estructura del calendario;
  Notificaciones › nuevos episodios enlaza al calendario.
- Biblioteca (S7): estructura de la biblioteca en Apariencia.

## Para probar en el build o en el dispositivo (de los informes)

- Fuentes y vectores de `composeResources` de `:core:designsystem` (primer uso de `font/` y `drawable/` ahí) en escritorio y Android.
- Tinte de la textura washi (matriz de color, opacidades 0,35 / 0,5 / 0,05).
- Texto vertical de Tanabata y Obi; forma del billete de Tránsito; `ModifierLocal` de la fila «Esta noche» dentro de `LazyRow`.
- Arrastre del tablero de Biblioteca contra el desplazamiento horizontal; pulsación larga contra menú contextual.
- Reproductor: panel con seis pestañas en 360 dp; pantalla final y bloqueo en el reproductor vertical; flechas con el panel abierto;
  Panel cae a Cine por debajo de 900 dp.
- Nombres de iconos no confirmados en `feature/details` (`DownloadDone`, `VideoLibrary`, `UnfoldMore`, `Filled.PlayCircle`).
- Costo de reportar los límites de cada tarjeta por cuadro (transición tarjeta-ficha) en el moto g51.
