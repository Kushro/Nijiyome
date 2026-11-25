# API de movimiento (carril M1)

Todo vive en `core/designsystem/src/commonMain/kotlin/app/nijiyome/designsystem/motion/` (paquete
`app.nijiyome.designsystem.motion`). Las pantallas llaman estas funciones y **nunca** leen qué variante eligió el usuario:
cada función lee `LocalMotion.current` (nivel y variante de su momento) y hace lo que corresponde. Fuente del diseño:
`artefacto/lab.js` (sección Movimiento) y `lab.css`.

## Niveles

`LocalMotion.current.level` ya trae combinados Reducir movimiento, el ajuste del sistema y el modo rendimiento
(`rememberMotionChoices` en `:core:ui`).

| Nivel | Qué se permite | Atajo para el resto |
|---|---|---|
| `FULL` | Todo: resortes, desplazamientos, un momento de autor por pantalla. | `LocalMotion.current.spatial == true` |
| `CALM` | Solo fundidos de 150 ms o menos. Sin desplazamiento, balanceo ni parallax. | `spatial == false`, `rich == true` |
| `PERF` | Fundidos y traslaciones cortas, una animación a la vez. Sin desenfoque, sombras grandes, texturas animadas ni bucles. | `rich == false` |
| `OFF` | Nada se mueve: estado final inmediato. | `spatial == false`, `rich == false` |

Reglas que valen en los cuatro: salir dura el 70 % de entrar; en listas se anima en `graphicsLayer` o en dibujo, nunca el
layout; nada se mueve en bucle más de 5 s sin que el usuario lo pida. Todas las funciones de esta página animan con
`Animatable` / transiciones leídas en la fase de dibujo, así que no recomponen por cuadro.

## Valores compartidos (`MotionSpecs.kt`)

| Nombre | Valor | Uso |
|---|---|---|
| `MotionEasing.ExitExponential` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` (`--nj-ease`, «salida exponencial») | Casi todo: estados, menús, avisos, salidas. |
| `MotionEasing.Emphasized` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` (`--nj-ease-emph`) | Entradas que viajan lejos: ficha, revelados. |
| `MotionEasing.Linear` | `LinearEasing` | Lo que sigue al tiempo: brillo, vida del aviso. |
| `MotionDurations.INSTANT` | 100 ms (`--nj-dur-1`) | Respuesta a una presión; contenido en Rendimiento. |
| `MotionDurations.STATE` | 200 ms (`--nj-dur-2`) | Cambio de estado de un control. |
| `MotionDurations.LAYOUT` | 350 ms (`--nj-dur-3`) | Cambio de disposición, hoja o superposición. |
| `MotionDurations.AUTHOR` | 500 ms (`--nj-dur-4`) | El momento de autor de una pantalla. |
| `MotionDurations.CALM_FADE` / `CALM_QUICK` | 150 ms / 120 ms | Tope de Calmo / confirmaciones en Calmo. |
| `MotionDurations.exitOf(ms)` | 70 % de `ms` | Duración de la salida. |
| `MotionSprings.ui()` | ζ 1, rigidez 400 | Hojas, indicadores, soltar tras arrastrar. |
| `MotionSprings.stamp()` | ζ 0,6, rigidez 1500 | Sello de visto. |
| `MotionSprings.scrollTilt()` | ζ 0,5, rigidez 200 | Inclinación por scroll. |
| `MotionSprings.tanzaku()` | ζ 0,3, rigidez 50 | Balanceo de tiras (solo Completo). |
| `MotionLevel.tweenFor(ms, easing, delay)` | FULL tal cual; CALM ≤150 ms lineal sin retraso; PERF ≤350 ms sin retraso; OFF `snap()` | Opacidad o color de cualquier cosa. |
| `motionTween(ms, easing, delay)` | `tweenFor` del nivel actual (composable) | Igual, sin leer el nivel a mano. |
| `motionFadeIn(ms)` / `motionFadeOut(enterMs)` | Fundido de entrada / salida (70 %) del nivel actual | `AnimatedVisibility` de algo que solo aparece. |

```kotlin
val alpha by animateFloatAsState(if (selected) 1f else 0.6f, motionTween(MotionDurations.STATE))
AnimatedVisibility(open, enter = motionFadeIn(), exit = motionFadeOut()) { Panel() }
```

## Momentos

Variante por defecto en negrita. «Calmo», «Rend.» y «Off» dicen qué cambia en cada nivel.

### Entrada de Inicio, `inicio`: `Modifier.homeEntrance(index: Int)`

Va en cada sección de Inicio; `index` es su posición. Solo la primera vez que Inicio se abre en la sesión (ventana de 4 s
desde la primera sección; cada índice se reproduce una vez), nunca al volver atrás ni al cambiar de estructura.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`cascade`** | Fundido y subida de 8 dp, 240 ms, 30 ms entre secciones (tope 300 ms), salida exponencial. | Fundido único de 150 ms. | Fundido de 100 ms. | Nada. |
| `rows` | Cada sección se descubre de izquierda a derecha, 350 ms énfasis, 80 ms entre secciones. | Fundido de 150 ms. | Sin entrada. | Nada. |
| `none` | Fundido de 150 ms. | Igual. | Fundido de 100 ms. | Nada. |

```kotlin
sections.forEachIndexed { i, s -> HomeSection(s, Modifier.homeEntrance(i)) }
```

### Balanceo tanzaku, `tanzaku`: `Modifier.cardSway(seed: Int)`

En cada tarjeta colgante de una fila; `seed` es el índice. Gira la tarjeta desde su centro superior en la capa.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`spring`** | Empujón al llegar (solo las seis primeras, 90 ms entre ellas) y en cada presión; se asienta en unos 2 s. | Resalte de opacidad de 150 ms al llegar. | Solo se mueve la tarjeta presionada. | Quieta. |
| `wind` | Una ráfaga de 4 s al llegar (las ocho primeras), cada tira con su desfase; luego se calma. | Quieta. | Quieta. | Quieta. |
| `scroll` | Se inclina según la velocidad de su fila (máx. 8°) y vuelve 90 ms después de parar. | Quieta. | Quieta. | Quieta. |

«Al llegar» es una vez por pantalla (guardado con `rememberSaveable`: al volver atrás la fila ya está asentada).

```kotlin
itemsIndexed(items, key = { _, it -> it.key }) { i, item -> TitleCard(item, onClick = { open(item) }, modifier = Modifier.cardSway(i)) }
```

### De la tarjeta a la ficha, `ficha`

- `Modifier.cardToDetailsSource(key: String)`: en el póster de la tarjeta.
- `Modifier.cardToDetailsTarget(key: String)`: en el póster o héroe de la cabecera de la Ficha. Mismo `key` (p. ej. `"anime:1234"`).
- `CardToDetailsHost { … }`: envuelve el host de navegación, una vez, en el shell. Es un `SharedTransitionLayout` en todos los
  niveles (el árbol no cambia con el ajuste) y anota dónde cae cada presión.
- `CardToDetailsEntry(animatedVisibilityScope) { … }`: nuevo. Da a las pantallas de una entrada de navegación su
  `AnimatedVisibilityScope`; con él, `ct` es una transformación real con `sharedBounds`.
- `MotionChoices.cardToDetailsEnterTransform(density)` / `cardToDetailsPopTransform(density)`: nuevos. `ContentTransform` de
  pantalla completa para el `transitionSpec` / `popTransitionSpec` del `NavDisplay` cuando la entrada nueva (o la que se va) es
  una Ficha.

Solo hay transición si la Ficha se abre desde una tarjeta presionada en los últimos 1,5 s con ese `key`; si el mismo título
aparece dos veces en pantalla, solo la copia presionada viaja.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`ct`** | El póster crece hasta el héroe (500 ms énfasis; vuelta 350 ms salida exponencial); el contenido aparece cuando ya casi llegó. | Fundido de 150 ms, sin crecer. | La misma transformación en 350 ms (vuelta 245 ms), sin sombra. | Nada. |
| `axis` | La lista se va 30 dp a la izquierda y la Ficha llega 30 dp desde la derecha, 350 ms. | Fundido de 150 ms sin desplazamiento. | Igual en 250 ms. | Nada. |
| `paper` | La Ficha se despliega desde la tarjeta: primero a lo ancho (40 %), luego hacia abajo (60 %), 500 ms énfasis. | Fundido de 150 ms. | Fundido de 150 ms (el recorte animado cuesta en GPU débiles). | Nada. |

Cómo corre según lo que conecte el shell:

- **Conectado** (`CardToDetailsEntry` en cada entrada): `ct` usa `sharedBounds` en los dos sentidos, incluido el atrás
  predictivo; `axis` lo hace la pantalla entera con `cardToDetailsEnterTransform`; `paper` lo hace el héroe.
- **Sin conectar**: el héroe hace una entrada coordinada que parte del rectángulo de la tarjeta presionada (transformación
  recortada sin deformar, desplazamiento de 30 dp o despliegue). Al volver corre la transición de atrás del shell.

```kotlin
MediaCover(media, Modifier.cardToDetailsSource("anime:${media.id}"))                  // en la tarjeta
DetailsHero(media, Modifier.cardToDetailsTarget("anime:${media.id}"))                 // en la Ficha
```

### Marcar visto, `visto`: `WatchedTransition(watched, modifier, shape = RectangleShape, tint = Color.Unspecified) { watched -> … }`

Envuelve la marca (o la fila, para `band`); `content` dibuja el estado que recibe. Solo se reproduce cuando `watched` cambia,
nunca en la primera composición. Nuevo: `animateWatchedProgress(target): () -> Float` para la línea de progreso (léela en
dibujo): viaja 350 ms en Completo y salta en los demás niveles.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`stamp`** | La marca cae de 1,6 a 1 con un resorte corto (ζ 0,6, 1500) y un giro leve que se asienta con ella. | Aparece con fundido de 120 ms. | Cambia sin escala ni giro. | Igual. |
| `stations` | La marca se enciende (0,6 a 1 en 120 ms) después de los 350 ms que viaja la línea. | Se enciende desde 30 % en 120 ms. | Salta. | Salta. |
| `band` | Un tinte (`tint`, por defecto `primaryContainer`) cruza de izquierda a derecha en 350 ms y queda; al desmarcar se retira en 200 ms. Recortado a `shape`. | El tinte aparece con fundido de 120 ms. | Se tiñe sin animación. | Igual. |

```kotlin
WatchedTransition(episode.watched) { seen -> Icon(if (seen) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null) }
```

### Controles del reproductor, `controles`: `PlayerControlsTransition(visible, edge, modifier, hiddenContent = null) { … }`

Un bloque de controles por llamada (`ControlsEdge.TOP`, `BOTTOM`, `CENTER`). Ocultar es más rápido que mostrar. Nuevo:
`hiddenContent` (se muestra mientras los controles están ocultos, solo en `min`) y `MiniProgressLine(progress: () -> Float)`,
la línea de 2 dp para ponerle.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`fade`** | Fundido, 200 ms al mostrar, 150 ms al ocultar. | Fundido de 120 ms. | Igual que Completo. | Instantáneo. |
| `slide` | Arriba sube 12 dp, abajo baja 16 dp, el centro se encoge a 0,88; 250 ms al mostrar, 200 ms al ocultar. | Fundido de 120 ms. | Fundido de 150 ms. | Instantáneo. |
| `min` | 150 ms; el bloque inferior crece desde la línea de 2 dp, que queda al ocultar. | Fundido de 120 ms; la línea queda fija. | Igual que Calmo. | Instantáneo. |

En Rendimiento el degradado oscuro detrás de los controles se cambia por barras sólidas: eso lo decide quien dibuja el
scrim, con `!LocalMotion.current.rich`.

```kotlin
PlayerControlsTransition(visible, ControlsEdge.BOTTOM, hiddenContent = { MiniProgressLine({ position / duration }) }) { SeekBar(…) }
```

### Carga, `carga`: `LoadingPlaceholder(modifier, shape, label = null, progress = null)`

El bloque que ocupa el lugar de algo que carga. Nuevo: `label` y `progress` (0..1) para `count`, y `Modifier.loadedReveal()`
para el contenido que lo reemplaza. Los bucles se detienen a los 5 s y el bloque queda quieto.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`shimmer`** | Un brillo recorre el bloque, 1,2 s por pasada, sincronizado entre bloques. Llegada: fundido de 150 ms. | Quieto. Llegada: fundido de 150 ms. | Quieto. Llegada directa. | Quieto. |
| `ink` | Una gota de tinta late en el centro (900 ms). Llegada: el contenido se abre en círculo (500 ms énfasis). | Quieto con «Cargando…» si cabe. Llegada: fundido. | Quieto con texto. Llegada directa. | Quieto. |
| `count` | Con `label` / `progress`: el texto y una barra que avanza 200 ms por respuesta. Sin ellos, bloque quieto. | La barra salta. | La barra salta. | La barra salta. |

```kotlin
if (loading) LoadingPlaceholder(Modifier.fillMaxWidth().height(180.dp), label = stringResource(Res.string.rd_s4_finding, done, total), progress = done / total.toFloat())
else SourceList(sources, Modifier.loadedReveal())
```

### Cambio de estado, `estado`: `StatusChangeTransition(state) { value -> … }`

Para el estado de lista o cualquier valor pequeño con clave (un contador). Los dos valores se dibujan uno sobre otro mientras
dura el cambio; solo se mueven capas. Nuevo: `statusChangeSpec<T>()` para lo que anime un selector por su cuenta (posición del
indicador, color del chip).

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`ind`** | Fundido cruzado de 250 ms; el indicador del segmentado viaja con `statusChangeSpec` (250 ms énfasis). | Fundido de 120 ms; la marca salta. | Directo. | Directo. |
| `ribbon` | El valor nuevo barre sobre el anterior de izquierda a derecha, 300 ms énfasis. | Fundido de 120 ms. | Directo. | Directo. |
| `odo` | El valor nuevo rueda hacia arriba como un contador, 180 ms salida exponencial. | Fundido de 120 ms. | Directo. | Directo. |

```kotlin
StatusChangeTransition(entry.status) { s -> StatusMark(s.slot(), label = s.label(), selected = true) }
```

### Snackbar con deshacer, `snack`: `SnackbarTransition(visible, modifier) { … }`

Entrada y salida de un aviso. La cola, la barra de vida y el historial son del host de snackbars. Nuevo:
`Modifier.snackbarBehind(behind: Boolean)` para el aviso que uno nuevo empujó atrás.

| Variante | Completo | Calmo | Rend. | Off |
|---|---|---|---|---|
| **`rise`** | Entra 16 dp desde abajo en 220 ms; sale en 150 ms. | Fundido de 150 ms. | Aparece y se va sin transición. | Instantáneo. |
| `stack` | Igual que `rise`; el anterior, con `snackbarBehind(true)`, sube 8 dp, baja a 0,96 y a 75 % de opacidad en 220 ms (máximo 2 a la vista). | Los dos con fundido; el de atrás solo se atenúa. | Solo el último, con «y 1 más» (lo decide el host). | Instantáneo. |
| `origin` | Crece desde 0,4 alrededor del último punto presionado (el botón que lo causó), 250 ms énfasis. | Fundido de 150 ms en su lugar. | Fundido de 150 ms. | Instantáneo. |

```kotlin
SnackbarTransition(current != null) { AppSnackbar(current, Modifier.snackbarBehind(behind = index > 0)) }
```

## Equipo lento: `rememberLowEndDevice(): Boolean`

`expect/actual`. Android: `ActivityManager.isLowRamDevice`. Escritorio: Skia por software (`SOFTWARE_FAST` /
`SOFTWARE_COMPAT`, forzado con `skiko.renderApi` / `SKIKO_RENDER_API` o por la caída de Skiko al iniciar; se vuelve a mirar
tras el primer cuadro y a los 2 s). Un panel de 60 Hz no activa nada. Lo pasa el shell:

```kotlin
LocalMotion provides rememberMotionChoices(settings.look.design, reduce = reduceMotion, lowEndDevice = rememberLowEndDevice())
```

## Refresco bajo: `rememberLowRefreshRate(): Boolean`

`expect/actual`, aparte de lo anterior. Android: la pantalla cuyo modo más rápido es de 60 Hz o menos (se mira el máximo de
`Display.supportedModes`, no el refresco del momento, para no confundir un 120 Hz en ahorro de batería). Escritorio: siempre
`false`. No cambia el nivel de movimiento: Ajustes puede usarla para sugerir el modo rendimiento.

## Lo que conecta el shell (S1)

1. `CardToDetailsHost { AppNavDisplay(…) }` alrededor del `NavDisplay`.
2. Un `NavEntryDecorator` que envuelva cada entrada:
   `CardToDetailsEntry(LocalNavAnimatedContentScope.current) { entry.Content() }` (`androidx.navigation3.ui`).
3. En `transitionSpec` / `popTransitionSpec` del `NavDisplay`, cuando la entrada de arriba (o la que se va) es `Route.Details`
   o `Route.NovelDetails`: `motion.cardToDetailsEnterTransform(density)` / `motion.cardToDetailsPopTransform(density)` con
   `val motion = LocalMotion.current`; para lo demás, lo que ya usa.
4. `lowEndDevice = rememberLowEndDevice()` en `rememberMotionChoices`.
