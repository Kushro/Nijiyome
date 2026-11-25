# Contrato del explorador de rediseño de Nijiyome

Lee este archivo entero antes de escribir. Es la única coordinación entre los agentes que trabajan en paralelo:
cada uno escribe **solo sus archivos** y se apoya en lo que aquí se define. Nadie edita `app.js`, `core.css`,
`data.js`, `index.html` ni archivos de otro agente; si necesitas algo que falta, resuélvelo dentro de tus archivos
y anótalo en tu informe final.

## Qué es

Un artefacto HTML (página estática, sin build) que presenta el rediseño completo de Nijiyome como alternativas
combinables. El dueño elige en la barra del explorador:

| Control | Valores | Atributo en `.nj-app` |
|---|---|---|
| Dirección visual | `tanabata`, `obi`, `transit`, `sumi`, `canon` | `data-dir` |
| Plataforma | `desktop` (marco 1440×900), `android` (marco 412×892) | `data-platform` |
| Tema | `light`, `dark`, `oled` | `data-mode` |
| Paleta | ids que declara cada dirección (2 a 4 por dirección) | `data-palette` |
| Movimiento | `full`, `calm` (reducido), `perf` (modo rendimiento) | `data-motion` |
| Estilo de tarjeta | ids registrados con `NJ.registerCard` (se puede mezclar con cualquier dirección) | `data-card` |
| Navegación | `rail`, `medio`, `comando` | `data-nav` |
| Estructura de pantalla | ids que declara cada pantalla | `data-structure` |

Todo lo que se ve dentro de un marco de dispositivo vive dentro de `<div class="nj-app" …>`. El resto de la página
(el explorador) tiene su propio estilo neutro y no se toca.

Idioma: español latino en todo el texto visible. La app final tendrá inglés y español; las maquetas van en español.

## Archivos y dueños

| Archivo | Dueño | Contenido |
|---|---|---|
| `index.html`, `app.js`, `core.css`, `data.js`, `CONTRATO.md` | integrador | explorador, runtime, tokens base (canon), componentes base, datos |
| `screens-a.js`, `screens-a.css` | agente S1 | Inicio, Ficha, Fuentes del episodio, Reproductor |
| `screens-b.js`, `screens-b.css` | agente S2 | Explorar/Buscar, Calendario, Biblioteca, Ajustes |
| `screens-c.js`, `screens-c.css` | agente S3 | Transferencias, Fuentes (extensiones), Link, Lectores (manga y novela), Primer arranque |
| `dir-tanabata.js/.css` | agente D1 | dirección Tanabata |
| `dir-obi.js/.css` | agente D2 | dirección Obi |
| `dir-transit.js/.css` | agente D3 | dirección Tránsito nocturno |
| `dir-sumi.js/.css` | agente D4 | dirección Sumi y hanko (mantiene identidad) |
| `lab.js`, `lab.css` | agente L1 | secciones Patrones de interacción y Laboratorio de movimiento |
| `paridad.json`, `paridad.js` | agente P1 | matriz de paridad y su vista |

`img/` tiene pósters (`p-*.webp`), portadas de manga (`m-*.webp`) y fondos (`hero-*.webp`, `frame-laidoff.webp`).
Las imágenes externas están bloqueadas por la CSP: usa solo `img/…` relativas o SVG/canvas propios.

## Runtime (`window.NJ`, definido en `app.js`)

```js
NJ.data            // = window.NJ_DATA (ver data.js)
NJ.h               // ayudantes, ver abajo
NJ.registerDirection({ id, name, track, tagline, summary, fonts, palettes, defaultCard, notes })
NJ.registerCard({ id, name, dir, desc, render(item, opts) })            // estilo de tarjeta mezclable
NJ.registerScreen({ id, name, group, order, platforms, fullBleed, structures, render(ctx), mount(el, ctx) })
NJ.registerLab({ id, name, order, render(el) })                          // secciones propias de la página
```

- `track`: `'free'` (identidad nueva), `'identity'` (mantiene kitsune/ensō/虹) o `'canon'`.
- `fonts`: URLs completas de `https://fonts.googleapis.com/css2?...&display=swap`; el runtime las inyecta.
- `palettes`: `[{ id, name, swatch: '#hex' }]`. La primera es la por defecto. Los valores reales los pone tu CSS
  bajo `.nj-app[data-dir="x"][data-palette="id"]`.
- `notes`: `{ thesis, signature, motion, android, risks, compose }` en texto plano (se muestran en la ficha de la
  dirección). `compose` explica cómo se haría en Compose Multiplatform (por ejemplo `graphicsLayer`, `Modifier.drawBehind`,
  `animateFloatAsState`, `SharedTransitionLayout`).
- `render(ctx)` devuelve un string HTML. `mount(el, ctx)` (opcional) se llama después de insertar el HTML, para
  enganchar eventos o canvas. Ambas deben funcionar al re-renderizarse muchas veces (sin listeners globales sueltos:
  engánchalos dentro de `el`).
- `ctx = { platform, dir, structure, mode, motion, card, nav, data, h, state, rerender() }`.
- `structures`: `[{ id, name, desc }]`. La primera es la por defecto. Al menos 2 en las pantallas clave.
- `fullBleed: true` para pantallas que no llevan el shell de navegación (reproductor, lectores).

### `NJ.h` (ayudantes)

```js
h.esc(str)                       // escapa HTML
h.icon(name, { fill, size, cls }) // icono Material Symbols Rounded: <span class="nj-ic">play_arrow</span>
h.card(item, opts)               // tarjeta en el estilo elegido (data-card); opts abajo
h.status(id)                     // { id, name } de NJ.data.statuses
h.statusChip(id)                 // chip de estado con su color de estado
h.poster(item, { w, ratio })     // <img> del póster con alt
h.progress(value 0..1, { label }) // barra de progreso accesible
h.seg(name, options[{id,label,icon}], selectedId)   // control segmentado (radio group real)
h.chips(name, options, selectedIds, { multi })      // chips de filtro/selección
h.toggle(id, label, on, { desc })                   // interruptor con etiqueta
h.slider(id, label, { min, max, step, value, unit })
h.btn(label, { kind: 'filled'|'tonal'|'outline'|'text'|'danger', icon, attrs })
h.iconBtn(icon, label, { kind })
h.ep(n) / h.timeAgo(str)
```

`opts` de `h.card`: `{ size: 's'|'m'|'l', context: 'row'|'grid'|'continue'|'wish'|'compact'|'hero', show: { progress, status, next, score } }`.
Toda tarjeta debe funcionar en todos los contextos y tamaños.

### Eventos de demostración

Los controles de las maquetas pueden ser interactivos dentro de su marco (abrir una hoja, cambiar de pestaña,
seleccionar, arrastrar). Guarda estado efímero en `ctx.state` (objeto por pantalla que el runtime conserva) y llama
`ctx.rerender()`. Para navegar a otra pantalla: `NJ.go(screenId, { structure })`.

## Tokens (`core.css` los define para `canon`; cada dirección los redefine)

Todos con prefijo `--nj-`. Los componentes base solo usan estos tokens, así que una dirección cambia casi todo
redefiniéndolos. Definir siempre los tres modos: `[data-mode="light"]`, `[data-mode="dark"]`, `[data-mode="oled"]`.

```
Color:   --nj-bg --nj-surface --nj-surface-2 --nj-surface-3 --nj-line --nj-line-strong
         --nj-text --nj-text-2 --nj-text-3 --nj-accent --nj-on-accent --nj-accent-soft --nj-on-accent-soft
         --nj-focus --nj-scrim --nj-ok --nj-warn --nj-err --nj-info
Estado:  --nj-st-watching --nj-st-planning --nj-st-completed --nj-st-paused --nj-st-dropped --nj-st-rewatching
Tipo:    --nj-font-ui --nj-font-display --nj-font-accent --nj-font-mono
         --nj-fs-xs --nj-fs-s --nj-fs-m --nj-fs-l --nj-fs-xl --nj-fs-2xl --nj-fs-3xl
Forma:   --nj-r-xs --nj-r-s --nj-r-m --nj-r-l --nj-r-xl --nj-r-pill
Prof.:   --nj-shadow-1 --nj-shadow-2 --nj-shadow-3
Motion:  --nj-ease --nj-ease-emph --nj-dur-1 --nj-dur-2 --nj-dur-3 --nj-dur-4
Densidad:--nj-gap --nj-pad --nj-rail-w --nj-touch
```

`data-motion="calm"` debe quitar desplazamientos, balanceos y parallax (deja solo fundidos ≤150 ms).
`data-motion="perf"` debe quitar blur, sombras grandes, texturas animadas y canvas continuos; y bajar a una sola
animación a la vez. Escribe esas reglas en tu CSS para lo que tú agregues.

## Clases base (en `core.css`, úsalas; las direcciones las reestilizan)

Shell: `.nj-shell` `.nj-rail` `.nj-rail-item[aria-current]` `.nj-topbar` `.nj-cmd` (búsqueda/comandos) `.nj-main`
`.nj-bottombar` `.nj-appbar` `.nj-statusbar` (Android) `.nj-gesturebar`.

Contenido: `.nj-section` `.nj-section-head` `.nj-h1` `.nj-h2` `.nj-h3` `.nj-meta` `.nj-row` (fila con scroll horizontal)
`.nj-grid` (rejilla de tarjetas que refluye por celdas enteras) `.nj-stack` `.nj-cluster` `.nj-divider`.

Controles: `.nj-btn` (+ `.is-filled` `.is-tonal` `.is-outline` `.is-text` `.is-danger`) `.nj-iconbtn` `.nj-chip`
(`[aria-pressed=true]`) `.nj-seg` `.nj-tabs` `.nj-tab[aria-selected]` `.nj-toggle` `.nj-slider` `.nj-field` `.nj-search`
`.nj-menu` `.nj-menu-item` `.nj-sheet` (hoja inferior en Android, panel lateral en escritorio) `.nj-dialog` `.nj-snackbar`
`.nj-tooltip` `.nj-badge` `.nj-status` (`data-status`) `.nj-progress` `.nj-list` `.nj-list-row` `.nj-kbd`.

Tarjeta base: `.nj-card` con `data-style`, `data-size`, `data-context`, `data-status`.

## Reglas de diseño (impeccable + antislop, obligatorias)

1. **Paridad**: no se pierde ninguna función. Los inventarios están en `../inventario-A-…md`, `../inventario-B-…md`,
   `../inventario-C-…md`. Si una maqueta reorganiza algo, el lugar nuevo debe existir en la maqueta o en su nota.
2. **Elegir antes que escribir**: chips, segmentados, multiselección, selectores visuales y arrastrar y soltar en lugar
   de listas desplegables largas y campos de texto crudos. Puertos, rutas y horas usan selectores propios.
3. **Operar primero**: la dirección presta tipo, paleta, densidad y un movimiento distintivo; la navegación y los
   controles son estándar (Material 3 en Android). Nada de disfrazar la pantalla de póster o de objeto del mundo.
4. **Prohibido**: texto con degradado; glass/blur decorativo; barra de color en un solo lado de una caja redondeada;
   sombras duras desplazadas; eyebrows/kickers sobre títulos; numeración 01/02/03 decorativa; emoji como iconos;
   tarjetas anidadas; todo centrado; cream + serif + terracota; negro + un solo neón.
5. **Estados**: muestra al menos un estado real por pantalla (límite de AniList, sin fuentes, caché de debrid,
   Link desconectado, vacío con acción, error con recuperación).
6. **Accesibilidad**: contraste ≥4.5:1 en texto, foco visible, objetivos táctiles de 48 dp en Android, `aria-*` en
   controles, `prefers-reduced-motion` respetado además de `data-motion`.
7. **Copy**: español latino, directo, acciones con verbo ("Marcar como visto", "Descargar 4 episodios"). Sin
   "¡Ups!", sin relleno, sin guiones largos de inciso.
8. **Movimiento**: un momento orquestado por pantalla, no efectos sueltos en todo. Curvas exponenciales de salida.
9. **Datos**: usa `NJ.data`. Lo inventado (fuentes, tamaños, seeders) ya está marcado como ejemplo en el explorador.
10. **Rendimiento**: nada de librerías nuevas salvo que sea imprescindible (cdnjs fijado a versión exacta).
    Canvas solo donde la dirección lo justifica, y pausado en `data-motion="perf"`.

## Cómo probar tu parte

Sirve la carpeta con `python3 -m http.server 8765` desde `docs/redesign/artefacto/` y abre `index.html`. El navegador
de capturas del integrador es Chromium headless; no hace falta que tú captures, pero tu JS no debe lanzar errores:
valida con `node --check tu-archivo.js`.

## Puntos de anclaje entre pantallas y direcciones

Las pantallas no conocen las direcciones; las direcciones no editan pantallas. Se encuentran por estos atributos,
que **las pantallas deben poner** y **las direcciones pueden decorar** desde `decorate(app, ctx)` o desde su CSS:

| `data-region` | Dónde | Qué puede hacer una dirección |
|---|---|---|
| `hero` | cabecera destacada de Inicio o de la Ficha | fondo, textura, viñeta, tipografía display |
| `tonight` | fila "Esta noche"/continuar | colgar tarjetas de una vara, dibujar la línea, etc. |
| `row` | cualquier fila de tarjetas (con `data-row-title`) | riel, separadores |
| `grid` | rejilla de tarjetas | ritmo de celdas |
| `episodes` | lista de episodios (cada ítem `.nj-ep` con `data-ep`, `data-watched`, `data-aired`) | dibujar progreso como estaciones, tiras, sellos |
| `airing` | agenda o calendario de emisión (ítems `data-time`) | línea de tiempo, tablero |
| `status-picker` | selector de estado de lista | color de estado, sello |
| `link` | estado de Nijiyome Link | diagrama de conexión |
| `player-seek` | barra de progreso del reproductor (con `data-chapters`) | marcas de capítulos |
| `empty` | estados vacíos | ilustración propia |
| `sheet` | hojas y paneles laterales | material |

Además: `.nj-app` lleva `data-screen` y `data-structure`, así que una dirección puede ajustar una pantalla concreta
con CSS (`.nj-app[data-dir="x"][data-screen="inicio"] [data-region="tonight"] { … }`). Mantén esos ajustes al
mínimo: la dirección cambia el material, no la organización.

## Paleta de comandos

El agente L1 implementa `NJ.openPalette(appEl, ctx)` en `lab.js`: abre la paleta (Ctrl+K) dentro del marco que se le
pasa. El shell ya llama a esa función al tocar la barra de búsqueda del escritorio.

## Probar localmente

`python3 -m http.server <puerto>` en esta carpeta y abrir, por ejemplo,
`http://127.0.0.1:<puerto>/index.html?screen=ficha&dir=obi&platform=android&mode=light&structure=ruta#pantallas`.
Parámetros: `screen dir platform mode motion card nav palette structure both=1`. Captura con
`/home/octavio/.claude/skills/impeccable/scripts/impeccable shot "<url>"` (máximo 3 capturas por agente).
