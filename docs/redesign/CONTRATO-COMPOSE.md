# Contrato de implementación de Nijiyome Sparkles (Compose)

Lee este archivo entero antes de escribir. Es la única coordinación entre los carriles: cada uno escribe **solo sus
archivos** y se apoya en lo que aquí se define. Si te falta algo del contrato, resuélvelo dentro de tus archivos y
anótalo en tu informe final; no edites lo ajeno ni esperes respuesta.

## Qué se construye

El rediseño completo del artefacto `docs/redesign/artefacto/` (ábrelo como código: `index.html`, `app.js`, `core.css`,
`screens-*.js/css`, `dir-*.js/css`, `lab.js/css`; su propio contrato es `artefacto/CONTRATO.md`). Todo se implementa
y el usuario elige en Ajustes › Apariencia. Los ejes son independientes entre sí:

| Eje | Valores | Dónde vive la elección |
|---|---|---|
| Dirección | `tanabata` (por defecto), `obi`, `transit`, `sumi`, `canon` | `LookPrefs.design.direction` |
| Paleta | las de cada dirección; Canon usa las 20 de siempre (`AppSettings.paletteId`) | `design.palette` |
| Tema | claro, oscuro, OLED | `AppSettings.themeMode` + `amoled` (sin cambios) |
| Tarjeta | 16 estilos (`TitleCardIds`) | `design.card` (null = la de la dirección) |
| Movimiento | perfil `full`/`calm`/`perf`, modo rendimiento, variante por momento | `design.motionProfile`, `perfMode`, `motionVariants` |
| Navegación | `rail`, `medio`, `comando` (solo ventanas anchas) | `design.nav` |
| Estructura por pantalla | ids del artefacto, un enum por pantalla | `design.structures` |
| Filas de Inicio | orden y ocultas | `design.homeRowOrder`, `homeRowsHidden` |

**Regla dura: ninguna pantalla pregunta qué dirección, tarjeta o variante de movimiento está activa.** Las pantallas
usan `MaterialTheme`, los `CompositionLocal` y las funciones de este contrato. Una pantalla solo hace `when` sobre
**su propia** estructura, una vez, en su raíz. Las direcciones cambian el material, nunca la organización.

## Archivos del contrato (congelados; nadie los edita salvo el integrador)

- `core/model/.../model/Design.kt`: `DesignPrefs`, `DesignDirection`, `MotionProfile`, `PerfMode`, `NavStyle`, los enums
  de estructura (`HomeStructure`, `DetailsStructure`, `SourcePickerStructure`, `PlayerStructure`, `ExploreStructure`,
  `CalendarStructure`, `LibraryStructure`, `SettingsStructure`, `TransfersStructure`, `SourcesStructure`,
  `LinkStructure`, `MangaStructure`), `ScreenStructures`, `MotionMoments`, `TitleCardIds`, `HomeRowIds`.
- `core/model/.../model/Look.kt` (`LookPrefs.design`), `Settings.kt`, `core/data/...SettingsRepositoryImpl.kt`.
- `core/designsystem/.../direction/Direction.kt`: `DirectionSpec`, `DirectionPalette`, `DirectionDecor`, `DecorRegion`,
  `StatusColors`, `SignalColors`, `StatusSlot`, `ThemeTone`, los locals (`LocalDirection`, `LocalThemeTone`,
  `LocalStatusColors`, `LocalSignalColors`, `LocalAccentFont`, `LocalCoverAccent`) y lo que llaman las pantallas:
  `Modifier.decor(region)`, `SectionHeader`, `EpisodeMark`, `StatusMark`, `ProgressTrack`, `EmptyArt`.
- `core/designsystem/.../direction/CanonDirection.kt`: implementación de referencia de una dirección y el registro
  `Directions`.
- `core/designsystem/.../theme/NijiyomeTheme.kt`.
- `core/designsystem/.../motion/MotionApi.kt`: firmas congeladas; **los cuerpos son de M1**.
- `core/ui/.../cards/TitleCard.kt`: `TitleCardStyle`, `TitleCardData`, `CardContext`, `CardShow`, registro `TitleCards`,
  `TitleCard(...)`, `Media.toCardData()`. `cards/MaterialCard.kt` es la implementación de referencia de una tarjeta.
- `core/ui/.../look/DesignUi.kt`: `design`, `structures`, `effectiveCardId()`, `ListStatus.slot()`, `rememberMotionChoices`.
- `core/ui/.../media/MediaCard.kt`: `MediaCard(...)` ya despacha al estilo elegido; los sitios que la llaman no cambian.
- `feature/shell/**` es solo de S1. `settings.gradle.kts`, `gradle/libs.versions.toml`, todos los `build.gradle.kts`,
  `tokens/**`, `core/data/**`, `core/model/**` (salvo lo que tu carril tenga asignado) no se tocan.

## Cómo usa esto una pantalla

```kotlin
@Composable
fun LibraryScreen(/* la misma firma pública de hoy; parámetros nuevos solo con valor por defecto */) {
    when (structures.library) {                       // una sola vez, en la raíz
        LibraryStructure.SHELVES -> LibraryShelves(state, actions)
        LibraryStructure.BOARD -> LibraryBoard(state, actions)
        LibraryStructure.LIST -> LibraryList(state, actions)
    }
}

// dentro de una estructura
SectionHeader(stringResource(Res.string.rd_s7_watching), trailing = { TextButton(onClick = onSeeAll) { Text(seeAll) } })
LazyRow(Modifier.decor(DecorRegion.ROW)) {
    itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
        TitleCard(item, onClick = { onOpen(item) }, context = CardContext.ROW, modifier = Modifier.cardSway(i))
    }
}
StatusMark(entry.status.slot(), label = entry.status.label(), selected = true)
ProgressTrack(progress = seen / total.toFloat(), segments = total)
val warn = LocalSignalColors.current.warn
```

- Las tres o dos estructuras de una pantalla comparten estado, modelo de vista y acciones; cambia solo la composición.
- Pon `Modifier.decor(DecorRegion.X)` en las regiones del artefacto (`data-region`): `HERO`, `TONIGHT`, `ROW`, `GRID`,
  `EPISODES`, `AIRING`, `STATUS_PICKER`, `LINK`, `PLAYER_SEEK`, `EMPTY`, `SHEET`. El shell pone `APP_BACKGROUND`, `RAIL`
  y `TOP_BAR`.
- Movimiento: `Modifier.homeEntrance(index)`, `Modifier.cardSway(seed)`, `Modifier.cardToDetailsSource(key)` /
  `cardToDetailsTarget(key)`, `WatchedTransition`, `PlayerControlsTransition`, `LoadingPlaceholder`,
  `StatusChangeTransition`, `SnackbarTransition`. Para lo demás, `LocalMotion.current.spatial` / `.rich` deciden si se
  anima; con `MotionLevel.OFF` nada se mueve. Un momento orquestado por pantalla, no efectos sueltos.
- Patrones de interacción (multiselección, arrastrar a estado, reordenar, selector de estado, facetas, menú
  contextual, pickers de puerto/ruta/hora, deshacer): están en `core/ui/.../patterns/` (carril I1). Léelos antes de
  escribir uno propio. Si tu carril arranca antes de que exista el que necesitas, escríbelo local y anótalo.
- La firma pública de los composables de entrada de tu módulo (los que llama `feature/shell`) **no se rompe**: solo
  parámetros nuevos con valor por defecto. Si necesitas una navegación o un callback nuevo desde el shell, agrégalo
  con valor por defecto y anótalo en tu informe para que el integrador lo conecte.

## Cómo se escribe una dirección (D1 a D4)

Tu objeto ya existe como stub en `core/designsystem/.../direction/<id>/` y está en el registro. Reemplaza su cuerpo.

- `palettes`: las de tu `registerDirection` (`palettes`), con los valores de tu CSS. Cada una responde `scheme(tone, contrast)`
  para claro, oscuro y OLED (OLED: fondo y superficies base en negro puro) y los tres niveles de contraste, más
  `status(tone)` (`--nj-st-*`) y `signal(tone)` (`--nj-ok/warn/err/info`). Llena **todos** los roles de `ColorScheme`
  (parte de `lightColorScheme()` / `darkColorScheme()` y copia); texto ≥ 4.5:1 sobre su superficie, verificado con cuentas.
- `typography(base)`: parte de `base` y cambia familias y pesos. Fuentes en
  `core/designsystem/src/commonMain/composeResources/font/` con nombre `rd_<id>_<fuente>.ttf`, cargadas con
  `org.jetbrains.compose.resources.Font(Res.font.…)`. Solo licencia OFL o Apache; anota fuente y licencia en tu informe.
  Tope: 1,5 MB de fuentes por dirección (usa variables o subconjunto latino con `pyftsubset` si está instalado; los
  títulos en japonés caen a la fuente del sistema).
- `shapes`, `accentFont()` y `decor`: un `object : DirectionDecor` que sobreescribe lo que tu dirección dibuja distinto
  (estaciones, sellos, tiras, varas, líneas). Con `LocalMotion.current.rich == false` no hay texturas ni dibujo continuo.
- Tus tarjetas: stubs en `core/ui/.../cards/<id>/`. Cada estilo dibuja los seis `CardContext` y los cuatro `CardSize`,
  con foco visible, objetivo táctil de 48 dp y un nodo de semántica con el título. Anchos de fila con `CardSizing` /
  `cardWidth()`; pósters con `MediaCover`.
- Nombres visibles: `strings_rd_<id>.xml` en `composeResources/values` y `values-es` del módulo donde está el código.

## Reglas para todos

1. **Paridad**: tu lista está en `docs/redesign/carriles/paridad-<carril>.md` (y la transversal en
   `paridad-transversal.md`). Nada de lo que la app hace hoy se pierde. Los inventarios completos están en
   `docs/redesign/inventario-*.md`. Lo que el artefacto no rediseña se deja como está en el código.
2. **Textos**: inglés en `values/`, español latino en `values-es/`, en un archivo nuevo `strings_rd_<carril>.xml` del
   módulo donde está tu código, con claves `rd_<carril>_*` (en minúsculas: `rd_s7_…`, `rd_tanabata_…`). No edites los
   `strings*.xml` existentes. Los textos fijos en español que encuentres en tus archivos pasan a recursos. Copy directo,
   acciones con verbo, sin "¡Ups!", sin guiones largos.
3. **Dependencias**: no agregues ninguna. Si de verdad hace falta, pídela en el informe.
4. **Sin builds**: no ejecutes Gradle, ni compilación, ni pruebas, ni la app. Escribe Kotlin que compile: verifica
   cada import y cada firma leyendo el código que llamas. Actualiza las pruebas de tu módulo que tus cambios rompan,
   sin correrlas.
5. **Accesibilidad**: contraste ≥ 4.5:1 en texto, foco visible, 48 dp en táctil, `contentDescription` / semántica en
   controles, y `MotionLevel.OFF` / `CALM` respetados.
6. **Prohibido** (artefacto, regla 4): texto con degradado, blur decorativo, barra de color en un solo lado de una caja
   redondeada, sombras duras desplazadas, numeración decorativa, emoji como iconos, tarjetas anidadas.
7. **Plataformas**: escritorio y Android. Usa `WindowSize` (core/ui/platform) como hoy; lo que es solo de una
   plataforma sigue siéndolo.
8. Código nuevo con cabecera `// SPDX-License-Identifier: AGPL-3.0-or-later`. Comentarios solo donde aporten.
9. Commits en tu rama, en inglés, pequeños. No hagas push.

## Decisiones ya tomadas

- Por defecto: Tanabata + Tanzaku. Quien actualiza conserva su diseño de Inicio, calendario, movimiento y estilo de
  controles táctiles (`DesignPrefs.migratedFrom`) y ve un aviso único que lleva al selector (`design.introSeen`; lo
  muestra S1, lo marca visto S1 o el primer arranque de S10).
- Color dinámico de Android: apagado en instalaciones nuevas; un `true` guardado se conserva.
- Canon conserva las 20 paletas y los tres estilos de tarjeta anteriores (`material-*`).
- Tema por portada en la Ficha: solo Canon; las demás direcciones leen `LocalCoverAccent`.
- Rutas: no se renombra ninguna `Route`. Explorar = `Route.Search` (Para ti es una pestaña suya; `Route.ForYou` abre
  esa pestaña). Transferencias = `Route.Downloads` (los torrents son una sección; `Route.Torrents` sigue abriendo el
  detalle). Fuentes = `Route.Extensions`. Link = `Route.Linked`.
- Riel nuevo: principal Inicio, Explorar, Calendario, Biblioteca, Manga, Novelas; utilidades Transferencias, Fuentes,
  Link, Ajustes. Android: Inicio, Explorar, Calendario, Biblioteca, Tú. Ctrl+P queda como alias de Ctrl+,. Las teclas
  1 a 9 van a los primeros nueve destinos visibles.
- Insignia de descargas activas en Android: en la pestaña Tú y en la fila Transferencias de Tú. Sincronizar ahora:
  arriba en Tú y en el menú de Biblioteca.
- Estilo de controles táctiles (Cine / Minimalista) = `PlayerStructure.CINEMA` / `MINIMAL`; `playerChromeStyle` deja de
  leerse (S5), el selector queda en Ajustes › Reproducción y en la hoja Más.
- Red: proxy de salida y proxy local de medios en Sistema; FlareSolverr en Fuentes de episodios.
- Mejorar un stream por URL: Link › Perfil de mejora. «Procesar en» queda en la hoja Mejoras del reproductor.
- Ajustes: 14 categorías (ver `artefacto/data.js` `settingsCategories`), un solo modelo de guardado, la búsqueda salta
  al ajuste exacto. Solo S10 edita `feature/settings`; los demás carriles anotan qué ajustes suyos cambian de lugar.
- Textura washi (D1): un PNG en grises que se repite sin costura, generado por un script propio y teñido con el color
  de papel de la paleta; apagada cuando `LocalMotion.current.rich` es falso.

## Carriles y archivos propios

| Carril | Archivos propios |
|---|---|
| D1 Tanabata, D2 Obi, D3 Tránsito, D4 Sumi | `core/designsystem/.../direction/<id>/**`, `core/ui/.../cards/<id>/**`, sus fuentes `rd_<id>_*` y `strings_rd_<id>.xml` |
| M1 Movimiento | `core/designsystem/.../motion/**` (cuerpos de `MotionApi.kt`, detección de equipo lento con `expect/actual`) |
| I1 Patrones | `core/ui/.../patterns/**` y la galería de diseño `core/ui/.../patterns/gallery/**` |
| S1 Shell | `feature/shell/**`, la barra de título y atajos en `desktopApp/**` |
| S2 Inicio y Calendario | `feature/home/**` salvo `Discover*.kt` |
| S3 Explorar | `feature/search/**`, `feature/home/.../Discover*.kt` |
| S4 Ficha y fuentes del episodio | `feature/details/**`, `feature/player/.../sources/**` |
| S5 Reproductor A (estructuras, chrome, gestos) | en `feature/player`: `TouchPlayerUi`, `PlayerControls`, `PlayerOverlays`, `PlayerBody`, `PlayerScreen*`, `PlayerScreenPlatform*`, `PlayerPlatform*`, `SeekHover`, `SeekBarRanges`, `PlayerVolumeControl`, `EndScreen`, `DesktopPlayerKeys`, `ExternalPlayerScreen`, `ExternalMpvCard`, `PlayerExtras`, `PlayerUiModels` y archivos nuevos `chrome/**` |
| S6 Reproductor B (hojas, mejoras, subtítulos) | en `feature/player`: `SubtitleStyleEditor`, `Enhancement*`, `PlayerSettingsExtras`, `Processing*`, `LinkProfileChange`, `LinkSubtitles`, `InstalledFontPicker`, `DownloadAction`, `PlayerLabels`, `SourceQualities`, `PhoneFrameGen.android` y archivos nuevos `sheets/**` |
| S7 Biblioteca y Perfil | `feature/library/**`, `feature/you/**`, `feature/social/**` |
| S8 Transferencias | `feature/torrents/**`, `feature/downloads/**` |
| S9 Fuentes y Link | `feature/extensions/**`, `feature/link/**` |
| S10 Ajustes y primer arranque | `feature/settings/**` |
| S11 Manga, Novelas y lectores | `feature/manga/**`, `feature/novel/**` |

`feature/player/.../PlayerViewModel.kt` está congelado para S5 y S6: estado nuevo va en archivos propios. Lo que S5 y S6
necesiten uno del otro se llama por las funciones que ya existen hoy.

## Informe final de cada carril

1. Qué quedó hecho, por estructura o estilo.
2. Tabla de paridad: cada id de tu lista con `hecho`, `igual` o `falta` y el motivo.
3. Huecos del contrato que resolviste localmente y qué necesitas que conecte el integrador (callbacks, rutas, ajustes
   que cambian de lugar, dependencias, licencias de fuentes).
4. Lo que no pudiste verificar leyendo y conviene mirar en el build o en el dispositivo.
