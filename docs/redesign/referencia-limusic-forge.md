# Referencia de diseño: LiMusic Forge

Qué hace que la interfaz de LiMusic Forge funcione y cómo llevar esos **patrones** (no los píxeles)
al rediseño de Nijiyome en Compose Multiplatform (escritorio + Android).

- Fuente: `/media/octavio/Warehouse/Personal Github Repositories/limusic-forge` (Tauri 2 + SvelteKit,
  Svelte 5, Tailwind v4, shadcn-svelte/bits-ui, iconos Hugeicons). Todas las rutas de abajo son
  relativas a esa raíz.
- Leído el 2026-11-25. Solo lectura: no se compiló ni ejecutó nada.
- Capturas revisadas: `assets/docs/screen-playlist.webp`, `screen-album.webp`, `screen-artist.webp`.

Una cosa a tener presente al leerlo: casi cada decisión visual de LiMusic viene con una medición en
el comentario al lado (frames por encima de 20 ms, MB retenidos por WebKitGTK). Su estética es
contenida *porque* se fue podando lo que costaba frames. Esa disciplina es lo más valioso que se
puede portar, incluso más que cualquier componente.

---

## 1. Layout y navegación

### 1.1 Estructura del shell

`ui/src/routes/+layout.svelte:240-319`

```
┌───────────────────────── Titlebar (h-9, propia, drag region) ─────────────────────────┐
│ ← → ⟳                  título centrado              cuenta · iconos · _ □ ✕            │
├──────────┬─────────────────────────────────────────────────────┬──────────────────────┤
│ Sidebar  │ <main> (único scroller de la página)                │ Paneles flotantes    │
│ w-16     │                                                     │ (Lyrics | Queue)     │
│ lg:w-60  │  hero con artwork difuminado → cabecera → filas     │ absolute, w-80       │
│          │                                                     │ sobre el contenido   │
├──────────┴─────────────────────────────────────────────────────┴──────────────────────┤
│ PlayerBar (aparece con fly y:64, 250 ms la primera vez que suena algo)                │
└───────────────────────────────────────────────────────────────────────────────────────┘
  Overlays globales montados una sola vez: CommandPalette, AddToPlaylist, Settings, Share,
  toasts (z-100), barra de selección flotante, NowPlaying (cubre main), TheaterMode (fullscreen).
```

Decisiones que vale la pena copiar:

- **Titlebar propia, fina (36 px)**: navegación atrás/adelante/recargar a la izquierda, cuenta e
  integraciones a la derecha, título centrado en `text-xs text-muted-foreground`
  (`components/Titlebar.svelte:150-200`). La barra no compite con el contenido.
- **Sidebar en dos estados por breakpoint, no por gesto**: `w-16` (rail de iconos) por debajo de
  `lg`, `lg:w-60` con etiquetas y lista de playlists encima. El colapso manual es solo una
  preferencia de pantallas grandes; cuando está colapsado simplemente no se emiten las clases `lg:`
  (`components/Sidebar.svelte:86-90, 159-163`). En el rail, el badge numérico se convierte en un
  punto sobre el icono (`Sidebar.svelte:225-232`).
- **Indicador de sección activa**: fondo `bg-primary/10 text-primary` más una barrita vertical de
  4×20 px en el borde izquierdo que entra con `scale` 200 ms desde 0.4 (`Sidebar.svelte:205-218`).
- **La sidebar es también zona de destino** (playlists del usuario debajo de la navegación, con
  fijadas arriba y una regla separando fijadas del resto). Ver §2.3.
- **Paneles laterales que flotan, no que se acoplan** (`+layout.svelte:105-124`,
  `components/QueuePanel.svelte`). Dos columnas acopladas dejaban el contenido "en una tira
  inservible". Cola y letras se abren de forma independiente y se apilan a la derecha sobre la
  página; por debajo de `lg` hay un scrim `bg-black/40` que cierra al tocar. Comentarios ocupan el
  hueco de letras (mutuamente excluyentes) en lugar de una tercera columna.
- **"Apartarse" sin cerrar**: cuando la vista Now Playing está abierta en modo "tabbed", los
  paneles se ocultan pero conservan su estado y vuelven al cerrarla (`+layout.svelte:119-124`).
- **Un único scroller por página** con la cabecera que se va con el scroll (playlist y álbum), y
  restauración de scroll por entrada de historial, no por URL (`+layout.svelte:78-102`).
- **Ancho mínimo de ventana 900×620** (`src-tauri/tauri.conf.json:20-21`), por eso usan `lg` y no
  `md` como corte principal.

### 1.2 Jerarquía y densidad (lo que se ve en las capturas)

- **Hero con artwork**: portada pequeña (~160 px) + bloque de texto: overline en mayúsculas
  pequeñas ("PLAYLIST", "ALBUM • 2013"), título en `font-heading text-4xl font-bold`, metadatos en
  muted, y una fila de acciones (primaria rellena "Play" en el color de acento, secundarias en
  píldora con borde, después iconos redondos ⋯ y "modo selección"). Detrás, la portada ampliada y
  difuminada (o nítida, configurable) que se funde con un degradado al fondo.
- **Búsqueda local de la lista arriba a la derecha** ("Search this playlist") y "Sort" justo
  encima de las columnas. Lo que actúa sobre la lista está pegado a la lista.
- **Filas de 56 px**: número/índice → miniatura 40 px redondeada → título (`text-sm font-medium`)
  y artista (`text-xs muted`) → columnas configurables (reproducciones, álbum, añadido, etc.) →
  marca "guardado" → duración. La fila activa lleva `bg-accent/10` y título en `text-primary`.
- **Controles revelados al pasar el cursor**: like/dislike, ⋯ y el botón play en el lugar del
  número solo aparecen en hover/focus (`invisible group-hover:visible group-focus-within:visible`,
  `components/TrackRow.svelte:318-331, 487-488, 556`). En reposo la lista es muy limpia.
- **Columnas que caben**: el contenedor mide su ancho y `fitColumns` decide qué columnas entran
  (`routes/playlist/[id]/+page.svelte:1645-1650`, `lib/trackcolumns.ts`). Densidad adaptativa sin
  romper filas.
- **Shelves que cambian de forma según lo que contienen** (`components/Shelf.svelte:1-20`):
  canciones → columnas de filas numeradas paginables; artistas → pósteres altos con el nombre sobre
  la foto; playlists → portada con "hojas" apiladas detrás (`StackCard.svelte`); el resto → tarjeta
  cuadrada. Evita "el muro indiferenciado de cuadrados" de YouTube Music.
- **Atajos en Home como baldosas anchas** (art pegado al borde izquierdo, título legible, 4 por
  fila) en vez de cuadrados pequeños con títulos truncados (`components/Shortcuts.svelte:1-20`).
- **Barras sticky que se vuelven opacas solo al fijarse** (chips de Home y campo de búsqueda):
  `IntersectionObserver` detecta "stuck" y entonces pinta fondo (`routes/search/+page.svelte:333-343`).
  Mientras están sobre el hero no tapan el artwork.

### 1.3 Now Playing y modo teatro

- **Now Playing** sube desde abajo (`fly y:100%`, 320 ms, `cubicOut`) y cubre solo `main` (deja la
  sidebar visible, `left-16 lg:left-60`), `components/NowPlaying.svelte:452-470`. Cola, letras y
  comentarios pasan a ser pestañas dentro de la vista.
- **Fondo "habitación iluminada por la portada"**: la portada pre-difuminada una vez a un bitmap
  pequeño y estirado, blobs de color pintados y un viñeteado de vuelta al fondo. Sin filtro en vivo
  (`NowPlaying.svelte:472-500`, `lib/artroom.svelte.ts`).
- **Modo teatro**: fullscreen, columnas opcionales de letras y cola (`TheaterMode.svelte`).
- **Píldora "Now playing ↑/↓"** flotando sobre la lista cuando la fila que suena está fuera de
  pantalla; un clic te lleva de vuelta (`components/NowPlayingPill.svelte`).

**Para Nijiyome**: shell con titlebar fina + rail/sidebar que en escritorio lista las *listas del
usuario* (Viendo, Pendientes, Favoritos, categorías de manga) como destinos de arrastre; en Android
la misma información pasa a `NavigationBar` + `ModalNavigationDrawer`. Los paneles de
episodios/capítulos o de fuentes deben flotar sobre la ficha (sheet lateral en escritorio,
`ModalBottomSheet` en Android), no comprimir la portada. `WindowSizeClass` sustituye a `lg:`.

---

## 2. Manipulación directa

Todo el arrastre de LiMusic está construido sobre HTML5 drag and drop con **un MIME propio por tipo
de arrastre**. Es la pieza más portable conceptualmente, aunque la API cambie.

### 2.1 Payloads tipados y estado compartido del arrastre

`ui/src/lib/dnd.ts:6-39`, `ui/src/lib/rowdrag.svelte.ts`

```ts
export const ITEM_MIME        = 'application/x-limusic-item';        // tarjeta → grid de Atajos
export const QUEUE_ROW_MIME   = 'application/x-limusic-queue-row';   // reordenar cola
export const SECTION_ROW_MIME = 'application/x-limusic-section-row'; // reordenar secciones de Home
export const TRACK_ROWS_MIME  = 'application/x-limusic-track-rows';  // filas de playlist → reordenar o copiar/mover
export type TrackRowsDrag = { from: string | null; fromTitle: string; rows: RowRef[] };

// Durante dragover solo se ven los *tipos*, no los datos: por eso existe este estado global,
// para que los destinos sepan cuántas filas vienen y de dónde (y no se ofrezcan a sí mismos).
export const rowDrag = $state({ active: false, count: 0, from: null as string | null });
```

Puntos clave:

- El tipo decide qué destinos reaccionan. Un reordenamiento de la cola no hace scroll en la página
  de detrás y viceversa (`dragScroll` es por MIME).
- Cada fila arrastrada lleva su "ancla" (la fila siguiente que se queda), para que deshacer un
  movimiento la devuelva exactamente a su sitio (`lib/reorder.ts:60` `anchorsFor`).
- Los arrastres ajenos (archivos, enlaces) se bloquean a nivel de ventana salvo que sean un import
  reconocido (Spotify/CSV), que abre el diálogo de importar (`dnd.ts:159`, `+layout.svelte:215-219`).
- Las `<img>` nunca son fuente de arrastre (`-webkit-user-drag: none`, `layout.css`): la fuente es
  siempre la fila o la tarjeta entera, así no aparecen "fantasmas" de imagen sueltos.

### 2.2 Reordenar arrastrando: tres variantes, cada una para su caso

| Dónde | Feedback | Cuándo se escribe | Archivo |
|---|---|---|---|
| Página de playlist | Filas arrastradas a `opacity-40`, **barra de 2 px `bg-primary`** en el borde superior de la fila delante de la que caerá (la última fila dibuja también una abajo) | **Staged**: nada se escribe hasta "Guardar orden" | `routes/playlist/[id]/+page.svelte:1180-1380, 1656-1680` |
| Cola | Igual (barra + fila atenuada) y `animate:flip` 200 ms para que el resto se deslice | Inmediato (`api.moveInQueue`) | `components/QueueList.svelte:36-63, 260-283` |
| Editar Home (secciones) | **Reordenamiento en vivo** sin marcador: la fila se mueve al pasar por encima de sus vecinas; arrastrada en `bg-muted opacity-60` | Al soltar (`dragend`), una sola vez | `components/HomeLayoutDialog.svelte:92-121, 259-310` |
| Grid de Atajos (Home) | Barra vertical de 2 px en el borde izquierdo de la baldosa destino, `animate:flip` 200 ms | Inmediato | `components/Shortcuts.svelte:110-160` |

Cálculo del punto de inserción (mismo patrón en todas):

```ts
// playlist/[id]/+page.svelte:1331-1337
function rowDragOver(e: DragEvent, n: number) {
  if (!reordering || !isDragRows(e) || !e.dataTransfer) return;
  e.preventDefault();                      // sin esto el drop nunca se dispara
  e.dataTransfer.dropEffect = 'move';
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
  dropAt = e.clientY < r.top + r.height / 2 ? n : n + 1;   // mitad superior / inferior
}
```

Detalles finos que evitan bugs reales:

- **Histéresis de media fila** en el reordenamiento en vivo: solo se intercambia cuando el puntero
  pasa la *mitad* de la vecina; intercambiar al tocar el borde hace que la lista rebote entre dos
  órdenes con un píxel de temblor (`HomeLayoutDialog.svelte:93-100`).
- **Sin `flip` en el reordenamiento en vivo**: una animación por transform mueve la caja de
  impacto y el `dragover` durante el deslizamiento pide volver atrás (ping-pong)
  (`HomeLayoutDialog.svelte:262-267`). `flip` solo donde se reordena *al soltar*.
- **Bloque de varias filas**: si la fila arrastrada está en la selección, se arrastran todas las
  seleccionadas y caen juntas en su orden (`moveBlock`, `lib/reorder.ts:32`).
- **Limpieza en `dragend` a nivel de ventana**: un arrastre cancelado con Esc nunca recibe `drop`;
  sin el handler global la barra quedaba pintada (`QueueList.svelte:303-310`,
  `Shortcuts.svelte:95-97`).
- **Vista ordenada o filtrada → no se puede reordenar, y se dice por qué**: al empezar a arrastrar
  sobre una vista con sort/filtro aparece una franja "Para reordenar, vuelve al orden manual" con
  un botón que lo hace (`+page.svelte:1197-1199, 1300-1308, 1632-1638`). Nada de rechazar el drop
  en silencio.

```ts
// lib/reorder.ts:32-57
export function moveBlock<T>(items: T[], picked: number[], to: number): T[] {
  const set = new Set(picked.filter((i) => i >= 0 && i < items.length));
  if (!set.size) return items;
  const block = items.filter((_, i) => set.has(i));
  const rest: T[] = []; let at = 0;
  for (let i = 0; i < items.length; i++) { if (i === to) at = rest.length; if (!set.has(i)) rest.push(items[i]); }
  if (to >= items.length) at = rest.length;
  return [...rest.slice(0, at), ...block, ...rest.slice(at)];
}
/** Alt+↑/↓: mover las filas seleccionadas un paso como bloque. */
export function nudge<T>(items: T[], picked: number[], dir: -1 | 1): T[] { /* … usa moveBlock … */ }
```

### 2.3 Soltar sobre una playlist de la sidebar: copiar o mover

`components/Sidebar.svelte:123-160, 314-330`, `components/DropConfirm.svelte`,
`lib/transfer.svelte.ts`

Mecánica:

1. Al arrancar el arrastre de filas, `startRowDrag(count, from)` publica el estado global.
2. **Toda la sidebar reacciona antes del drop**: las playlists que pueden recibir (tuyas, editables,
   que no son la de origen ni "Me gusta"/"On Repeat") se marcan con `ring-1 ring-primary/30`; las
   que no pueden se atenúan a `opacity-50`; la que está bajo el puntero se rellena con
   `bg-primary/15 ring-2 ring-primary`. **Sin transición** (son filas repetidas).
3. Modificadores al soltar: **Ctrl = copiar, Shift = mover**, si no, la preferencia guardada. El
   cursor cambia en vivo vía `dropEffect`.
4. Si la preferencia es "preguntar", aparece un **popover anclado a la fila destino** (no un modal
   centrado): dos botones grandes Copiar / Mover, un grupo de radio "¿Y los duplicados?" (omitir,
   permitir, consolidar), un checkbox "No volver a preguntar" y el coste estimado de la operación.
5. El resultado sale en un toast con **Deshacer** (§6).

```ts
// lib/transfer.svelte.ts:28-34
export function dropModeFor(drag: TrackRowsDrag, e: { ctrlKey: boolean; shiftKey: boolean; metaKey?: boolean }): DropMode {
  if (!drag.from) return 'copy';            // una lista que no es tuya solo se puede copiar
  if (e.ctrlKey || e.metaKey) return 'copy';
  if (e.shiftKey) return 'move';
  return prefs.dropMode;                    // 'copy' | 'move' | 'ask'
}
```

```svelte
<!-- Sidebar.svelte:318-330: tres estados de destino, ninguna transición -->
<div class="group/row relative rounded-lg {over === pl.id
    ? 'bg-primary/15 ring-2 ring-primary'
    : droppable(pl) ? 'ring-1 ring-primary/30'
    : rowDrag.active ? 'opacity-50' : ''}"
  ondragover={(e) => dragOverRow(e, pl)} ondragleave={(e) => dragLeaveRow(e, pl)} ondrop={(e) => dropOnRow(e, pl)}>
```

- **Equivalente sin arrastre**: "Mover a…" en la barra de selección abre `MoveToPlaylist.svelte`
  con la misma lista y orden que la sidebar ("para cuando la sidebar está colapsada o el destino
  está muy abajo").
- **Auto-scroll en los bordes mientras se arrastra**: 96 px de zona, hasta 1800 px/s, con rampa
  lineal; lo mueve un temporizador por tiempo transcurrido (no rAF, porque WebKitGTK deja de mandar
  `dragover` si el puntero está quieto), y se apaga solo tras 3 s sin eventos
  (`lib/dnd.ts:62-150`).

```ts
// lib/dnd.ts:75-82
export function edgeVelocity(y: number, top: number, bottom: number): number {
  const up = y - top;
  if (up < EDGE) return -SPEED * (1 - Math.max(up, 0) / EDGE);
  const down = bottom - y;
  if (down < EDGE) return SPEED * (1 - Math.max(down, 0) / EDGE);
  return 0;
}
```

### 2.4 Modelo de selección múltiple

`lib/selection.ts`, `lib/selection.svelte.ts`, `components/TrackRow.svelte:147-225`,
`components/TrackSelectionBar.svelte`, `components/TrackSelectButton.svelte`

- **Modo selección explícito**. En reposo una fila es "clic = reproducir", sin checkbox. Un botón
  en la cabecera de la lista (icono de cursor con destellos, `aria-pressed`) entra en modo selección:
  aparecen checkboxes y **el clic en la fila pasa a seleccionar**; **Enter sigue reproduciendo**.
- **Clic** alterna la fila y la convierte en ancla. **Shift+clic** añade el rango inclusivo desde el
  ancla visible, sin quitar lo demás (no hay Ctrl+clic porque en modo selección el clic ya es
  aditivo). **Espacio** alterna, **Shift+Espacio** rango, **Ctrl/⌘+A** selecciona todo, **Esc** sale.
- **Las claves son ocurrencias, no IDs**: una canción repetida en la playlist son dos filas
  seleccionables distintas. La selección sobrevive a filtros/ordenaciones (lo oculto se cuenta como
  "N ocultas") y a recargas parciales (lo que aún no llegó queda "pendiente", no "perdido").
- **"Seleccionar todo (N)" carga primero las páginas que faltan** y lo dice en el botón
  ("Cargando…"); usa el total real de la lista, no las filas en pantalla.

```ts
// lib/selection.ts:73-86
export function toggleTrack(state: SelectionState, key: string, visible: string[], range = false): SelectionState {
  if (!visible.includes(key)) return state;
  const keys = new Set(state.keys);
  const from = state.anchor === null ? -1 : visible.indexOf(state.anchor);
  if (range && from >= 0) {
    const to = visible.indexOf(key);
    for (const k of visible.slice(Math.min(from, to), Math.max(from, to) + 1)) keys.add(k);
    return { keys, anchor: state.anchor };          // el rango conserva el ancla
  }
  if (range || !keys.delete(key)) keys.add(key);
  return { keys, anchor: key };
}
```

**Barra de selección flotante** (`TrackSelectionBar.svelte:113-221`): píldora `rounded-2xl` centrada
abajo (por encima de la barra del reproductor), entra con `fly y:16` 220 ms. Razón explícita: una
franja insertada entre cabecera y filas *hace que la lista se mueva* justo cuando marcas una casilla.
Contenido, de izquierda a derecha: "N seleccionadas · M ocultas" (o una pista si no hay nada) │
iconos: reproducir a continuación, añadir a la cola, añadir a playlist, descargar, mover a…, quitar
(solo si la lista es editable), reproducir en bucle │ "Seleccionar todo (N)", "Limpiar", ✕.
Debajo, una línea de estado si hay pendientes o perdidas.

- **Quitar exige segundo clic**: el icono se convierte en botón rojo "Quitar N" y cualquier cambio
  en la selección cancela la confirmación a medias (`TrackSelectionBar.svelte:38-47`). Además hay
  Deshacer en el toast.
- **El menú ⋯ de una fila seleccionada actúa sobre toda la selección** (`TrackRow.svelte:128-145`).

### 2.5 Reordenar en lote "staged" + teclado

`routes/playlist/[id]/+page.svelte:1180-1265, 1366-1375, 1614-1640`

- Arrastres, Alt+↑/↓ (bloque seleccionado), "Barajar" e "Invertir" **modifican solo la vista**.
  Aparece una franja sticky: "N cambios sin guardar — Descartar | Guardar orden". Varios arrastres
  cuestan una sola escritura y se pueden tirar.
- El número de cambios es **el mínimo real de movimientos** (filas fuera de la subsecuencia
  creciente más larga, `movedCount`/`lisLength`, `lib/reorder.ts:7-29`). Si el usuario devuelve
  todo a su sitio, la franja desaparece sola.
- "Guardar este orden en la playlist" desde el menú Sort convierte una ordenación visual en el
  orden real (`PlaylistSortPanel.svelte`).

### 2.6 Herramienta de duplicados (y el resto de herramientas)

`components/PlaylistToolsDialog.svelte`, `components/tools/DedupTab.svelte`,
`components/OccurrencesDialog.svelte`, `components/PlaylistChips.svelte`

- Un diálogo con una pestaña por herramienta (Duplicados, Dividir, Fusionar, Extraer, Reordenar),
  cada una con los mismos **tres tiempos: configurar → revisar (y desmarcar) → aplicar**, con
  Deshacer en el toast.
- Duplicados: tres niveles de búsqueda como tarjetas-checkbox con título + explicación (exacto,
  mismo título, similar) y un radio "¿Cuál conservar?" (primera, última, preferir canción). La
  revisión muestra grupos con una copia "conservada" y el resto marcadas; puedes elegir otra copia
  a conservar con un clic (`keepRow`), o desmarcar.
- En la lista de filas, un chip **"×2"** señala que la herramienta de duplicados tiene trabajo
  (`TrackRow.svelte` prop `copies`).
- En "Biblioteca ▸ En tus playlists", cada canción lleva chips de las playlists donde está: dos y
  "+N" en reposo; al hover/focus se revelan hasta cinco y, si hay más, hacen carrusel a 30 px/s
  tras 1 s de pausa, y se detienen si el puntero está sobre un chip. "+N" abre un diálogo con
  todas las ocurrencias y acciones por lote (quitar, mover). Con reduced motion no hay carrusel
  (`lib/chips.ts`).

### 2.7 Deshacer

`lib/playlistops.svelte.ts`, `lib/player.svelte.ts:1347-1379`

Cada edición de playlist devuelve una entrada de diario del backend; la UI la convierte en un toast
de éxito con botón **Deshacer** y una barra de vida de 2 px que se vacía en 8 s. Las operaciones
múltiples se deshacen juntas (de la más nueva a la más vieja). Además hay un historial de las
últimas 20 con Deshacer en cada una.

```ts
export function announceOp(op: PlaylistOp | null, msg: string) {
  if (op?.undoable) toast.action(msg, t('undo.action'), () => void undoOp(op.id));
  else toast.success(msg);
}
```

**Para Nijiyome**: arrastrar series/obras desde la biblioteca o el calendario a listas de la
sidebar (Viendo/Pendientes/categorías), con Ctrl=copiar, Shift=mover y popover de confirmación
anclado al destino; reordenar la cola de descargas o una lista de seguimiento con barra de inserción;
modo selección para episodios/capítulos (marcar vistos/leídos, descargar, borrar descargas) con
barra flotante; "Deshacer" en cada acción destructiva. Compose: `Modifier.dragAndDropSource/Target`
(Compose MP ≥1.7 en escritorio y Android) o una implementación propia de puntero para listas;
`LazyColumn` + `Modifier.animateItem()` como equivalente de `flip`.

---

## 3. Elegir cosas sin dropdowns largos

LiMusic usa `Select` nativo solo para listas cortas y cerradas (orden de la Biblioteca, fuente,
canal de yt-dlp). En cuanto la lista puede crecer, cambia de forma. Catálogo:

| Patrón | Dónde | Por qué esa forma |
|---|---|---|
| **Command palette (Ctrl+K)** | `CommandPalette.svelte`, `lib/palette.ts` | Comandos propios (ir a…, tus playlists, acciones) ranqueados localmente arriba (prefijo > palabra > contiene > keyword, sin tildes ni mayúsculas); resultados de YouTube debajo, sin re-filtrar. Grupos con encabezado. Ctrl+K abre y cierra. Clic derecho sobre un resultado abre su menú contextual |
| **Typeahead con debounce 300 ms** | `SearchSuggest.svelte` | Mismas filas que la paleta: unos pocos resultados (canción/artista/álbum) y luego completados. Flechas para moverse; Enter sin selección cae al submit del formulario |
| **Chips de categoría tipo píldora** | `routes/search/+page.svelte:325-330, 436-450`, Home (moods) | Selección exclusiva visible de un vistazo; activo `bg-primary text-primary-foreground`, inactivo `bg-foreground/5`. Fila con scroll horizontal (`.rail`) |
| **Facet chips con popover** | `FilterChips.svelte` | Cada chip dice su valor actual, abre un popover para cambiarlo (rango de fechas, rango de minutos, multiselección de playlists *con buscador interno*), y se limpia con ×. Chip activo: `border-primary/40 bg-primary/15 text-primary` |
| **Chips que ciclan al clic** | `FilterChips.svelte:256-300` | Facetas de tres valores (todos/repetidos/en otra; todos/canciones/vídeos; descargado sí/no) cambian con cada clic: "pocos valores, un menú solo los ralentizaría" |
| **Toggle `.*`** | `FilterChips.svelte` | El buscador de la lista pasa a regex; en error el chip se pone en rojo con tooltip |
| **Segmented control** | Modo claro/oscuro/sistema (`AppearanceSettings.svelte:518-535`), calidad de audio (`SettingsDialog.svelte:1765-1782`) | Para una elección exclusiva corta. Pista `bg-muted p-0.5`, segmento activo `bg-background shadow-sm`. Colocado *debajo* de la descripción para no apretar el título |
| **Toggle de vista con iconos** | Biblioteca grid/lista (`routes/library/+page.svelte:303-325`) | Dos iconos en un grupo con borde, `aria-pressed` |
| **Swatches** | Acento y temas (`AppearanceSettings.svelte:328, 540-575`) | Círculos 28 px con tick dentro del elegido y anillo `ring-2 ring-foreground/60`; el swatch de "acento del tema" separado por una regla |
| **Picker con búsqueda en modal/popover** | `AddToPlaylist.svelte`, `PlaylistSelect.svelte`, `MoveToPlaylist.svelte`, `ShortcutPicker.svelte` | Campo con foco automático, filtro por subcadena, filas con miniatura. Add to playlist es **multiselección** con contador "odómetro" en el botón confirmar, "Nueva playlist" como primera fila y un segundo paso para duplicados |
| **Panel en lugar de dropdown** | `LanguagePicker.svelte` | Idiomas con buscador, crece "hacia los lados" en vez de hacia abajo; muestra % traducido solo cuando es <90 % |
| **Popover que previsualiza en vivo** | `LyricsSourcePicker.svelte` | Cada proveedor lado a lado; al elegir, las letras de detrás cambian ya (son la preview). Más un ajuste de sincronía en pasos de 0,25 s |
| **Menú contextual = menú ⋯** | `TrackMenu.svelte`, `PlaylistMenu.svelte`, `lib/menu.ts` | Un mismo menú se abre desde el botón ⋯ o con clic derecho en cualquier parte del ítem (`data-ctx`). Solo los *ítems* son hosts; cabeceras y héroes tienen su ⋯ visible. Botones internos (Like) no abren el menú de la fila. Shift+clic derecho da el menú nativo |
| **Panel lateral que se aplica en vivo** | `HomeLayoutDialog.svelte` | Editar Home: la página detrás es la preview; no hay Cancelar porque no hay nada pendiente; solo Reset y Hecho |
| **Rail de pestañas con pista** | `SettingsDialog.svelte:873-905` | Ajustes: rail izquierdo de 13 secciones con icono; cabecera de la sección con título + una línea de "qué hay aquí". Atajo Ctrl+P. Pestañas con preview (Temas, Discord) ensanchan el diálogo sin animar el ancho |
| **Chip de velocidad** | `SpeedPicker.svelte` | Velocidades de podcast como pasos, sustituye a Shuffle mientras suena un episodio; si está bloqueado, el chip explica por qué |

Posicionamiento de popovers (`lib/menu.ts:54-106`): se miden antes de pintarse
(`visibility:hidden`), se colocan debajo o encima según quepa, se pegan al borde derecho si se
salen, nunca a menos de 8 px del borde de la ventana, y el `transform-origin` es la esquina pegada
al ancla para que la animación "crezca desde el puntero". `transition:none` al colocar evita que el
menú se deslice desde arriba.

**Para Nijiyome**: la paleta Ctrl+K encaja directamente (ir a sección, abrir obra de la biblioteca,
acciones como "Buscar en extensiones", y resultados remotos debajo). El selector de fuente
(`source-picker`) debería ser un popover/sheet con las fuentes lado a lado y preview inmediata, como
`LyricsSourcePicker`. Filtros de biblioteca y del explorador de extensiones como facet chips (estado,
tipo, idioma, género con buscador interno). Calidad/servidor de vídeo como segmented. En Compose:
`FilterChip`/`InputChip`, `SingleChoiceSegmentedButtonRow`, `DropdownMenu` + `ContextMenuArea`
(escritorio) y `ModalBottomSheet` (Android) para el mismo contenido.

---

## 4. Movimiento

### 4.1 Inventario de animaciones

Easing casi único: `cubicOut` de Svelte. Duraciones en una banda corta. Todo es opacidad o
transform.

| Elemento | Animación | Archivo |
|---|---|---|
| Toast, banner de actualización, barra de selección | `fly y:16`, 220 ms, cubicOut (banner de error de BD: `y:-16`) | `+layout.svelte:348, 378, 398`; `TrackSelectionBar.svelte:118` |
| Barra del reproductor (primera aparición) | `fly y:64`, 250 ms; sale sin animación | `+layout.svelte:297` |
| Paneles cola/letras/comentarios | panel `fly x:32`, 220 ms; scrim `fade` 150 ms | `QueuePanel.svelte`, `LyricsPanel.svelte`, `CommentsPanel.svelte` |
| Diálogos propios (Add to playlist, Share, ShortcutPicker) | overlay `fade` 150 ms; tarjeta `scale` desde 0.96, 180 ms | `AddToPlaylist.svelte:138-146` |
| Popovers/menús (shadcn) | `animate-in fade-in-0 zoom-in-95 duration-150` | `DropConfirm.svelte:91` |
| Now Playing | `fly y:100%`, 320 ms | `NowPlaying.svelte:459` |
| Portada nueva en Now Playing | `scale` desde 0.96, 360 ms ("la única animación ahí, y dice que cambió la canción") | `NowPlaying.svelte:325` |
| Flash play/pausa sobre la imagen | entra `scale 0.7→1` 150 ms, sale `scale →1.3` 320 ms | `NowPlaying.svelte:280-281` |
| Fondo difuminado al cambiar de pista | crossfade `fade` 700 ms | `NowPlaying.svelte:496`, `TheaterMode.svelte:434` |
| Indicador de sección activa | `scale` desde 0.4, 200 ms | `Sidebar.svelte:215` |
| Contador del botón confirmar (odómetro) | píldora `scale` 0.4→1 150 ms; número `fly y:±12` 180 ms según suba o baje | `AddToPlaylist.svelte:256-263` |
| Filas de la cola: alta/baja | alta `fly y:-6` 180 ms, baja `fade` 144 ms, resto `flip` 200 ms. Solo para ediciones pequeñas (≤25 filas de diferencia, cola ≤ umbral de ventana, misma pista) | `QueueList.svelte:133-180` |
| Shuffle de la cola | en vez de flip de cientos de filas, las ≤12 visibles bajo la actual "se asientan" con WAAPI: `opacity 0→1, translateY 8px→0`, 240 ms, escalonado 30 ms, `cubic-bezier(0.2,0,0,1)` | `QueueList.svelte:183-202` |
| Grid de Atajos | `flip` 200 ms | `Shortcuts.svelte:159` |
| Contenido cargado sobre skeleton | `content-in`: solo opacidad, 150 ms ease-out | `layout.css:886-893` |
| Skeletons | `bg-muted animate-pulse` con la forma exacta de la fila/tarjeta | `ui/skeleton`, `TrackRowSkeleton.svelte`, `MediaCardSkeleton.svelte` |
| Like | `heart-pop`: escala 1→1.4→1, 300 ms | `layout.css:1175-1180` |
| Vida del toast con acción | `scaleX 1→0` lineal durante la duración del toast | `layout.css:1183-1187`, `+layout.svelte:424-428` |
| Títulos largos (marquee) | 3 pasadas al cambiar de pista, en bucle solo con el puntero encima, pausa sobre enlaces | `layout.css:1147-1168`, `Marquee.svelte` |
| Tarjetas de mood/género (búsqueda) | `:active scale(0.98)`, borde a color del mood 200 ms, arte con fade 300 ms al decodificar, disco que gira 5 s en hover, funda inclinada que se endereza 250 ms | `layout.css:940-1140` |
| Play en tarjetas | botón circular aparece con `transition-opacity 200ms`; sombra hover es una capa pre-difuminada que hace fade 300 ms | `MediaCard.svelte:83-100, 146` |
| Sliders | pulgar invisible hasta hover/focus (150 ms), `scale(1.25)` al pulsar | `layout.css:1190-1240` |
| Scroll de la cola al seguir la pista | `smooth` solo si avanza 1 fila y no hay reduced motion | `QueueList.svelte:236-238` |

No hay View Transitions API, ni springs, ni transiciones de ruta (el cambio de página es
instantáneo con fade de contenido). Las únicas "físicas" son el auto-scroll del arrastre.

### 4.2 Cómo se mantiene fluido (`docs/UI-PERFORMANCE.md`)

No hay un "modo rendimiento" conmutable: hay **reglas de diseño** escritas tras cada informe de lag,
con números. Las que importan para un diseñador:

1. **El hover anima color, no transform**, en cualquier cosa repetida (tarjetas, filas, items de
   sidebar). Quitar el zoom de tarjetas y otros dos efectos llevó Home de 46-52 % de frames lentos a
   7-9 %. Transform en hover solo sobre un elemento único (un botón de play).
2. **Las filas de listas con scroll no llevan transición alguna**; los controles de hover se
   revelan con `visibility`, no con `opacity` (la cola pasó de 50 % a 6 % de frames lentos).
3. **Blur sobre una fuente pequeña, una vez**: miniatura de 96 px para el fondo difuminado; para
   pantalla completa, se hornea en un canvas pequeño por pista y se escala.
4. **Nada de `backdrop-filter` en overlays grandes**: los diálogos usan `bg-black/80` plano.
5. **Sin sombra en reposo en tarjetas repetidas**; la sombra hover es una capa ya difuminada que
   cambia de opacidad.
6. **Escrituras por pista o por tick van a una hoja, no a la raíz**: el tinte por artwork solo
   cambia rellenos de superficie, no texto/bordes (cada cambio de color en raíz retenía ~20 MB en
   WebKitGTK).
7. **Un control que cambia un token raíz previsualiza en una miniatura y confirma al soltar**
   (colour picker, slider de tinte).
8. **Bucles de animación solo mientras se ven y cambian** (karaoke de letras, marquee de 3
   pasadas, carrusel de chips solo con hover).
9. **Listas largas con ventana** (`rows.svelte.ts`): solo ~30 filas montadas, padding arriba/abajo.
10. **Elementos a pantalla completa quietos.**
11. **Reduced motion**: regla global que reduce animaciones y transiciones a 0,01 ms
    (`layout.css:1276-1283`) y, además, cada animación propia con lógica (flip de cola, píldora,
    carrusel, marquee, composer) consulta `prefers-reduced-motion` y se desactiva del todo.

**Para Nijiyome**: tabla de tokens de movimiento corta (150/180/220/250/320 ms, un easing
emphasized-decelerate), animar solo `graphicsLayer` (alpha/scale/translation), nunca layout en
filas de `LazyColumn`, sin animaciones en hover de tarjetas del grid de biblioteca, blur de portada
precalculado a bitmap pequeño (Android <12 no tiene `Modifier.blur`), y respetar
`Settings.Global.ANIMATOR_DURATION_SCALE` / reduced motion del sistema en escritorio con un ajuste
propio.

---

## 5. Sistema visual

### 5.1 Tokens y capas de tema

`ui/src/routes/layout.css:30-125, 790-840`, `ui/src/lib/theme.svelte.ts`

Estructura de capas, de abajo arriba (la de arriba gana):

1. **Preset de paleta** (`.theme-<id>` en `<html>`): Default, Catppuccin, Tokyo Night, Caffeine,
   Neon, Breeze, Nord, Dracula, AMOLED. Cada preset define *todo*: colores claro y oscuro, radio,
   fuentes y escala de sombras.
2. **Capa custom** (estilos inline, sobrevive al cambiar de preset): acento, tono de los neutros
   (`--hue`, un solo número tiñe todos los grises), radio, fuente de texto y de títulos, fuentes
   cargadas desde disco. `null` = "lo que diga el preset".
3. **Tinte por artwork** (opcional): acento + matiz de superficies tomados de la portada que suena.
4. **Matugen** (opcional, opt-in): si está activo, posee toda la paleta y desactiva 2 y 3.

Tokens (nomenclatura shadcn): `background/foreground`, `card`, `popover`, `primary`, `secondary`,
`muted`, `accent`, `destructive`, `border`, `input`, `ring`, `chart-1..5`, `sidebar-*`. Definidos en
**OKLCH**. Escala de radios derivada de uno solo (`--radius: 0.45rem`):
`sm ×0.6, md ×0.8, lg ×1, xl ×1.4, 2xl ×1.8, 3xl ×2.2, 4xl ×2.6`.

### 5.2 Color desde la portada

`lib/artcolor.ts`, `lib/theme.svelte.ts:386-475`, `layout.css:740-788`

- Se dibuja la portada (la de 120 px que ya cargó la barra) en un canvas de 32×32, histograma de
  3 bits por canal, cada píxel puntúa `saturación × (1 − |valor − 0.65|)` (gana el color
  "característico", no el negro/blanco que ocupa más área). Portada casi gris (s < 0.15) → no se
  inventa un color, se queda el tema del usuario.
- `toAccent` lleva ese color al **brillo percibido objetivo según el modo** (0.65 oscuro, 0.39
  claro), recortando saturación lo justo si hace falta; la saturación se limita a 0.45-0.85. El
  mismo cálculo decide si el texto sobre el acento es claro u oscuro.
- Se **pre-calcula el color de la siguiente pista** en idle para que el cambio sea en el mismo frame
  que el artwork.
- El matiz va a `--art-h` y una clase `.art-tint` deriva **solo rellenos** (background, card,
  popover, secondary, muted, sidebar) con croma baja (0.01-0.04): toda la UI "deriva" hacia la
  portada sin perder legibilidad.

```ts
// lib/artcolor.ts (resumen)
const TARGET_DARK = 0.65, TARGET_LIGHT = 0.39, S_MIN = 0.45, S_MAX = 0.85;
export function toAccent(hex: string, dark: boolean): string { /* brillo objetivo por modo */ }
export function pickAccent(data: Uint8ClampedArray): string | null { /* histograma ponderado */ }
```

```css
/* layout.css:779-788 */
html.art-tint.dark, html.art-tint.theme-amoled {
  --background: oklch(0.165 0.028 var(--art-h));
  --card:       oklch(0.225 0.038 var(--art-h));
  --muted:      oklch(0.275 0.042 var(--art-h));
  --sidebar:    oklch(0.195 0.032 var(--art-h));
}
```

### 5.3 Matugen (paleta desde el fondo de pantalla)

`docs/MATUGEN.md`, `docs/matugen-template.css`, `theme.svelte.ts:476-513`

- Opt-in desde Ajustes ▸ Apariencia; sin activar no se lee ni se vigila nada.
- El usuario copia la plantilla a sus templates de Matugen; Matugen escribe
  `~/.config/limusic/matugen.css`; la app inyecta esa hoja, pone `matugen-theme` en `<html>` y la
  recarga en vivo cuando cambia el wallpaper (watcher en Rust).
- Mapeo de roles Material 3 → tokens de la app (este es el contrato reutilizable):

| Token app | Rol Material 3 |
|---|---|
| background / foreground | background / on_background |
| card | surface_container |
| popover | surface_container_high |
| muted / muted-foreground | surface_container_low / on_surface_variant |
| primary = accent | primary / on_primary |
| secondary | secondary_container / on_secondary_container |
| border, input | outline_variant |
| ring | primary |
| destructive | error / on_error |
| sidebar, sidebar-accent | surface, secondary_container |
| chart-1..5 | primary, secondary, tertiary, error, outline |

### 5.4 Tipografía, elevación, iconos, claro/oscuro

- **Tipografía**: texto en Oxanium (geométrica, algo "tech"), títulos en IBM Plex Sans; los presets
  pueden cambiar ambas; se pueden cargar fuentes desde archivo. Escala muy compacta: casi todo es
  `text-xs` (12) / `text-sm` (14) / `text-[13px]` en la sidebar; títulos de página
  `text-4xl font-bold tracking-tight` con `drop-shadow` sobre el artwork; overlines en mayúsculas
  pequeñas. Números con `tabular-nums` en duraciones y contadores.
- **Elevación**: casi plana. Superficies separadas por un paso de luminosidad (background → card →
  popover) y bordes `oklch(1 0 0 / 10%)` en oscuro. Sombras solo en lo que flota (toasts, barra de
  selección, popovers `shadow-xl`, paneles `shadow-2xl`). Profundidad en Now Playing por luz de la
  portada, no por sombra ("un anillo, no una sombra: el brillo detrás es la profundidad").
- **Iconos**: Hugeicons stroke, 16 px en filas y barras, 20 px en navegación, `strokeWidth 2` en
  controles de la titlebar. Estado alterno con `altIcon/showAlt` (sol/luna, play/pausa).
- **Claro/oscuro**: `mode-watcher` (claro/oscuro/sistema) con segmented; cada preset trae ambos;
  el acento de portada se recalcula al cambiar de modo; AMOLED se queda negro también en claro.
- **Superficies de color con `color-mix`**: tarjetas de mood mezclan el color de YouTube al 14 %
  con `--card`, así el texto siempre es `--foreground` en cualquier tema.

**Para Nijiyome**: material-color-utilities (o MaterialKolor) para generar el `ColorScheme`
completo desde la portada de la obra en la ficha y en el reproductor; en Android 12+
`dynamicDarkColorScheme(context)` es el equivalente nativo de Matugen (wallpaper) y en Linux se
puede leer la misma salida de Matugen. Mantener la regla "solo rellenos se tiñen, texto y bordes
salen del esquema base" y el piso de brillo por modo. Tokens nombrados por rol (los de M3 ya lo
son) y radio derivado de un único valor.

---

## 6. Feedback y estados

| Situación | Patrón | Archivo |
|---|---|---|
| Resultado de una acción | **Un único toast** centrado abajo (encima del reproductor), icono por tipo (éxito en `primary`, error en `destructive`, info en muted). 2,5 s; con acción 8 s y barra de vida. Un toast nuevo reemplaza al visible | `player.svelte.ts:1347-1379`, `+layout.svelte:395-432` |
| Acción reversible | Toast con **Deshacer** (diario del backend) + historial de 20 | `playlistops.svelte.ts` |
| Destructivo en lote | Segundo clic que convierte el icono en botón rojo "Quitar N"; se cancela si cambia la selección | `TrackSelectionBar.svelte:38-47` |
| Borrar playlist | Diálogo que **no se cierra hasta que el borrado vuelve**, para no aparentar que se borró si luego falla | `playlist/[id]/+page.svelte:1377-1395` |
| Optimista | Like/dislike y estado "guardado" cambian al instante y se revierten si YouTube rechaza | `player.svelte.ts:1058-1068` |
| Carga | Skeletons con la forma real (cabecera de playlist + 8 filas) y fade-in de 150 ms del contenido | `playlist/[id]/+page.svelte:1397-1410` |
| Re-ordenando en servidor | Lista a `opacity-50` con `aria-busy` mientras llega el nuevo orden | `+page.svelte:1641-1644` |
| Error de carga | `ErrorState`: icono en círculo muted, título humano ("Sin conexión" / "Algo falló"), detalle crudo más pequeño y en muted (útil para reportes), botón Reintentar; y **reintento automático** al volver la red | `ErrorState.svelte` |
| Vacío | Una línea útil, no una ilustración: Atajos vacío = una sola zona discontinua "Añadir atajo / arrastra aquí" que también es destino de drop | `Shortcuts.svelte:125-150` |
| Progreso largo en segundo plano | Píldora en la sidebar con barra de 4 px (import de Spotify, split/merge); clic = volver al diálogo o detener | `Sidebar.svelte:255-290` |
| Banners persistentes | Actualización disponible y fallo de migración de BD: arriba/abajo, z-100, con cerrar | `+layout.svelte:346-393` |
| Estado por fila | Descarga: % / en cola / error con tooltip; "×2" duplicados; marca "guardada en N playlists" | `TrackRow.svelte:180-195, 500-520` |
| Coste antes de actuar | "≈ N unidades de M disponibles" junto a copiar/mover y qué pasa si se excede | `ytdata/EngineChoice.svelte` |
| Accesibilidad del feedback | `role="status"` en contadores, `aria-live` al mover secciones con teclado ("movida a posición 3 de 12") | `TrackSelectionBar.svelte`, `HomeLayoutDialog.svelte:118, 353` |
| Focus | Un clic no deja foco "capturado": el foco puesto por el puntero se suelta para que Espacio vuelva a ser pausa; Tab devuelve el modo teclado | `lib/shortcuts.ts:158-170` |

**Para Nijiyome**: un `SnackbarHost` único con acción "Deshacer" e indicador de tiempo para marcar
episodios vistos en lote, quitar de lista, borrar descargas; píldora de progreso persistente para
descargas/torrents en el rail; `ErrorState` con mensaje humano para fallos de extensiones (con el
error crudo debajo); estados vacíos que *son* la acción (biblioteca vacía = "Explorar extensiones",
lista vacía = zona de drop).

---

## 7. Qué evitar o adaptar

1. **Hover como única vía de descubrimiento.** Controles de fila (like, ⋯, play) y chips extra solo
   aparecen con el puntero. En táctil no existen. En Nijiyome: acciones primarias siempre visibles en
   móvil, long-press = menú contextual/modo selección, swipe para la acción más común (marcar visto).
2. **HTML5 drag and drop y modificadores de teclado.** Ctrl/Shift para copiar/mover, Shift+clic para
   rangos, Alt+↑/↓: nada de eso existe en Android. Adaptación: long-press + arrastre con handle,
   "arrastrar para seleccionar" sobre la columna de checkboxes, y siempre una ruta por menú
   ("Mover a…", "Seleccionar rango").
3. **Menús por clic derecho.** Bien en escritorio, invisibles en móvil. Mantener siempre el ⋯ visible.
4. **Densidad de lista de música.** Filas de 56 px y `text-xs` funcionan para canciones; para
   anime/manga la portada es la información principal (póster 2:3, progreso, "nuevo episodio"),
   así que la tabla densa con columnas encaja en descargas/historial, no en biblioteca. Objetivos
   táctiles mínimos de 48 dp.
5. **Toast único que se reemplaza.** Si en Nijiyome se encadenan acciones (marcar 10 episodios y luego
   borrar descargas), el Deshacer del primero se pierde. Mejor cola de snackbars o historial de
   acciones visible.
6. **Selección por modo explícito con icono poco evidente** (cursor con destellos). En móvil el
   estándar es long-press para entrar; en escritorio sumar un texto "Seleccionar" o tooltip claro.
7. **Paneles flotantes que tapan el contenido.** Funciona para una cola de 320 px; para la lista de
   episodios junto al reproductor de vídeo puede tapar el vídeo. Valorar dock real en pantallas
   muy anchas y sheet en móvil.
8. **Reordenamiento "staged" con Guardar.** Útil cuando la escritura es cara (API remota con cuota).
   Para listas locales de Nijiyome basta con escritura inmediata + Deshacer; el patrón staged solo
   compensa con sincronización remota (AniList/MAL).
9. **Personalización amplia** (9 presets, tono de neutros, radio, fuentes de archivo, Matugen,
   tinte por portada). Mucho mantenimiento y combinaciones que probar. Para Nijiyome: Material 3 con
   color dinámico (wallpaper o portada), claro/oscuro/AMOLED y un acento; nada más al principio.
10. **Fuente Oxanium**: identidad muy "tech"; con títulos japoneses largos y furigana/romaji se
    necesita una fuente con buen soporte CJK y legibilidad a tamaños pequeños.
11. **Muchas reglas existen por bugs de WebKitGTK/Chromium** (`relative z-0` para scrollbars,
    `.chromium`, `transition:none` en menús). No portarlas literalmente: portar el principio
    (medir, animar solo capas, no tocar el tema raíz por frame).
12. **Marquee y carruseles automáticos** en títulos: aceptables en la barra del reproductor; en un
    grid de pósteres serían ruido. Preferir 2 líneas con elipsis.

---

## 8. Mapa rápido a Compose

| LiMusic | Compose Multiplatform |
|---|---|
| MIME propio por tipo de arrastre + `rowDrag` global | `DragAndDropTransferData` con clase de payload sellada + `MutableState` de "arrastre activo" en un holder compartido para que los destinos se pinten antes del drop |
| Barra de inserción + filas atenuadas | `Modifier.drawBehind` en la fila destino; `graphicsLayer { alpha = .4f }` en las arrastradas |
| `animate:flip` | `LazyColumn` + `Modifier.animateItem()` |
| `dragScroll` por bordes | `LazyListState.scrollBy` en un `LaunchedEffect` con bucle por tiempo mientras el puntero está en la franja de 96 dp |
| Selección por ocurrencia con ancla | `SelectionState(keys: Set<Key>, anchor: Key?)` inmutable + función pura `toggle(key, visible, range)` (copiable tal cual) |
| Barra de selección flotante | `Box` alineado abajo con `AnimatedVisibility(slideInVertically { it/4 } + fadeIn)`; en Android, `BottomAppBar` contextual |
| Popover anclado al destino | `Popup` con `PopupPositionProvider` propio (debajo/encima, margen 8 dp) en escritorio; `ModalBottomSheet` en Android |
| Command palette | `Dialog` con `TextField` + `LazyColumn` agrupada; `onPreviewKeyEvent` para Ctrl+K a nivel de ventana |
| Facet chips | `FilterChip` + `DropdownMenu`/`Popup` con contenido propio; chips de tres estados con clic cíclico |
| Segmented | `SingleChoiceSegmentedButtonRow` |
| Toast con Deshacer y barra de vida | `SnackbarHost` + `SnackbarDuration.Long` y un `LinearProgressIndicator` fino animado |
| Color de portada | `material-color-utilities` (QuantizerCelebi + Scheme) o Palette en Android; aplicar a un `MaterialTheme` local de la ficha/reproductor |
| Matugen | Android 12+: `dynamic*ColorScheme`; Linux: leer `matugen.css`/JSON con el mismo mapeo de roles |
| Reduced motion | Leer escala de animaciones del sistema y un ajuste propio; pasar duración 0 a `tween` |
