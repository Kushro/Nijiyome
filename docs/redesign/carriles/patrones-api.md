# API de patrones de interacción (carril I1)

Paquete `app.nijiyome.ui.patterns` (`core/ui/src/commonMain/kotlin/app/nijiyome/ui/patterns/`). Léelo antes de escribir un patrón
propio. Todo es común a escritorio y Android, funciona con ratón, teclado y táctil, y respeta `LocalMotion.current` (con `OFF` nada se
mueve). Los parámetros son datos y lambdas: ningún componente recibe un modelo de vista ni un repositorio. Los textos van ya traducidos
(los propios del paquete están en `strings_rd_i1.xml`). Cada función pública tiene KDoc con un ejemplo; aquí va la firma y un uso mínimo.

Regla de forma: donde un patrón tiene forma de teléfono y forma ancha, decide con `isCompactWindow()` (ancho < 600 dp) salvo que le
pases `compact = …`. Popover en ancho, hoja (`AdaptiveSheet`) en teléfono.

## Comunes (`PatternsCommon.kt`)

```kotlin
const val PATTERNS_COMPACT_WIDTH_DP: Float = 600f
@Composable fun isCompactWindow(): Boolean
fun <T> List<T>.moved(from: Int, to: Int): List<T>          // to = índice final, como list.add(to, list.removeAt(from))
```
```kotlin
if (isCompactWindow()) PhoneLayout() else WideLayout()
queue = queue.moved(from = 3, to = 0)
```

`ActionEmphasis { FILLED, TONAL, OUTLINED, DANGER }` y `ActionButton(label, onClick, modifier, icon, emphasis, enabled)`: botón con
icono del peso indicado, compartido por barras y diálogos.
```kotlin
ActionButton(retryLabel, onClick = vm::retry, icon = Icons.Outlined.Replay, emphasis = ActionEmphasis.FILLED)
```

## 1. Multiselección de episodios (`eps`, `Selection.kt`)

```kotlin
@Stable class SelectionState<K>(initial: Set<K> = emptySet()) {
    val selected: Set<K>; val anchor: K?; val active: Boolean; val count: Int
    fun isSelected(key: K): Boolean; fun start(key: K? = null); fun toggle(key: K); fun extendTo(key: K, order: List<K>)
    fun fillSpan(order: List<K>); fun selectAll(keys: Collection<K>); fun selectWhere(keys: Collection<K>, predicate: (K) -> Boolean)
    fun clear(); fun exit(); fun retainOnly(keys: Collection<K>)
}
@Composable fun <K> rememberSelectionState(): SelectionState<K>
@Composable fun <K> Modifier.multiSelectable(state, key: K, order: () -> List<K>, onOpen: () -> Unit, enabled: Boolean = true, onDisabledClick: (() -> Unit)? = null): Modifier
fun <K> Modifier.selectionKeys(state, selectable: () -> Collection<K>): Modifier      // Ctrl+A, Esc
@Composable fun SelectionCheckbox(selected: Boolean, visible: Boolean, modifier = Modifier)
data class SelectionAction(label, icon, onClick, enabled = true, emphasis = TONAL, confirmLabel: String? = null)
@Composable fun SelectionActionBar(visible, count, actions: List<SelectionAction>, onExit, modifier = Modifier, onSelectAll: (() -> Unit)? = null, selectAllLabel: String? = null, emptyHint: String? = null, compact = isCompactWindow())
@Composable fun SelectionTopBar(count, onExit, modifier = Modifier, onSelectSpan: (() -> Unit)? = null, onSelectAll: (() -> Unit)? = null, onSelectUnwatched: (() -> Unit)? = null)
```
`multiSelectable` reemplaza el `clickable` de la fila: fuera del modo, clic abre (`onOpen`); Ctrl/Shift+clic o pulsación larga entran
al modo; dentro, clic alterna y Shift+clic suma el rango desde el ancla; pulsación larga sobre otro suma el rango (táctil); Espacio
alterna, Shift+Espacio suma rango. `confirmLabel` arma un segundo toque en rojo (prefiere deshacer cuando se pueda).
```kotlin
val sel = rememberSelectionState<Int>()
LazyColumn(Modifier.selectionKeys(sel) { aired }) { items(eps) { EpisodeRow(it, Modifier.multiSelectable(sel, it.n, { aired }, onOpen = { play(it) }, enabled = it.aired)) } }
SelectionActionBar(sel.active, sel.count, listOf(SelectionAction(markLabel, Icons.Outlined.Visibility, { vm.mark(sel.selected) }, emphasis = ActionEmphasis.FILLED)), onExit = sel::exit, modifier = Modifier.align(Alignment.BottomCenter))
```
En teléfono, mientras `sel.active`, pon `SelectionTopBar(sel.count, sel::exit, onSelectSpan = { sel.fillSpan(aired) }, onSelectAll = { sel.selectAll(aired) })` en lugar de la barra de la pantalla.

## 2. Arrastrar un título a una lista o estado (`dnd`, `DragToTarget.kt`)

```kotlin
data class DropModifiers(val copy: Boolean = false, val move: Boolean = false)   // Ctrl copia, Shift mueve
@Stable class DragDropState<T : Any> {
    val dragging: T?; val picked: T?; val active: T?; val pointer: Offset?; val hovered: Any?
    fun pick(item: T); fun cancel(); fun refusal(targetKey: Any): String?; fun accepts(targetKey: Any): Boolean
    fun dropOn(targetKey: Any, modifiers: DropModifiers = DropModifiers.None): Boolean; fun labelOf(key: Any): String?
}
@Composable fun <T : Any> rememberDragDropState(): DragDropState<T>
@Composable fun <T : Any> DragDropContainer(state, itemLabel: (T) -> String, modifier = Modifier, ghost: (@Composable (item: T, hint: String, refused: Boolean) -> Unit)? = null, content: @Composable BoxScope.() -> Unit)
@Composable fun <T : Any> Modifier.dragSource(state, item: T, enabled = true, touchLongPress = true, shape = MaterialTheme.shapes.medium): Modifier
@Composable fun <T : Any> Modifier.dropTarget(state, key: Any, label: String, onDrop: (item: T, modifiers: DropModifiers) -> Unit, refuse: (item: T) -> String? = { null }, shape = MaterialTheme.shapes.large): Modifier
@Composable fun <T : Any> DropHereButton(state, targetKey: Any, modifier = Modifier)
@Composable fun PickedBanner(itemLabel: String, onCancel: () -> Unit, modifier = Modifier)
@Composable fun DropModeMenu(expanded, itemTitle, targetLabel, sourceLabel, onMove, onCopy, onDismiss, rememberChoice: Boolean, onRememberChoiceChange: (Boolean) -> Unit)
@Composable fun DragGhostChip(title: String, hint: String, refused: Boolean, modifier = Modifier)
```
El arrastre vive dentro de la ventana (no es el arrastre del sistema). Ratón: presionar y mover. Táctil: pulsación larga y mover
(`touchLongPress = false` lo apaga si el teléfono usa la pulsación larga para la hoja). Alternativas que no son arrastre: Espacio toma
el título y `DropHereButton` lo suelta; TalkBack recibe una acción por destino válido; y un `MenuAction` puede llamar
`state.pick(id)`. Los destinos válidos se marcan, los que no pueden recibir se atenúan y dicen por qué (`refuse`).
```kotlin
val dnd = rememberDragDropState<String>()
DragDropContainer(dnd, itemLabel = { titleOf(it) }) { Row { statuses.forEach { s -> Column(Modifier.weight(1f).dropTarget(dnd, s, moveTo(s), onDrop = { id, _ -> vm.setStatus(id, s) }, refuse = { if (statusOf(it) == s) alreadyIn(s) else null })) { cards(s).forEach { TitleCard(it, onClick = { open(it) }, context = CardContext.COMPACT, modifier = Modifier.dragSource(dnd, it.key)) } } } } }
```

## 3. Reordenar (`reo`, `Reorder.kt`)

```kotlin
@Stable class ReorderState { val listState: LazyListState; val draggingIndex: Int?; val insertionIndex: Int?; fun move(from: Int, to: Int, count: Int): Boolean }
@Composable fun rememberReorderState(listState: LazyListState = rememberLazyListState(), onMove: (from: Int, to: Int) -> Unit): ReorderState
@Composable fun Modifier.reorderContainer(state: ReorderState): Modifier      // en el LazyColumn: barra de inserción y autodesplazamiento
@Composable fun Modifier.reorderHandle(state: ReorderState, index: Int): Modifier
@Composable fun Modifier.reorderableItem(state: ReorderState, index: Int, count: Int): Modifier   // Alt+↑/↓, acciones de TalkBack
@Composable fun LazyItemScope.reorderPlacement(): Modifier                    // animateItem solo con movimiento espacial
@Composable fun ReorderableRow(state, index, count, title: String, modifier = Modifier, supporting: String? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, handleAtStart = !isCompactWindow())
```
El `LazyColumn` debe contener solo las filas reordenables (índice del ítem = índice de la fila) y usar `key`. Las demás filas no se
mueven mientras arrastras; una barra de 2 dp marca dónde cae y el cambio llega en `onMove` al soltar.
```kotlin
val reorder = rememberReorderState(onMove = { from, to -> vm.setQueue(queue.moved(from, to)) })
LazyColumn(state = reorder.listState, modifier = Modifier.reorderContainer(reorder)) { itemsIndexed(queue, key = { _, it -> it.id }) { i, it -> ReorderableRow(reorder, i, queue.size, it.name, reorderPlacement(), supporting = it.meta) } }
```

## 4. Soltar archivos en la ventana (`drop`, `FileDrop.kt`)

```kotlin
enum class DropKind { EXTENSION, TORRENT, MAGNET, VIDEO, SUBTITLE, UNSUPPORTED, UNKNOWN }
fun classifyDropName(name: String): DropKind
fun ExternalDrop.kind(): DropKind;  fun ExternalDrop.displayName(): String
val DropKind.icon: ImageVector
@Composable fun DropKind.label(): String; @Composable fun DropKind.hoverText(): String
@Composable fun DropKind.resultTitle(): String; @Composable fun DropKind.resultDetail(): String
data class DropAction(val label: String, val onClick: () -> Unit, val emphasis: ActionEmphasis = ActionEmphasis.TONAL)
@Composable fun FileDropOverlay(visible: Boolean, modifier = Modifier, kind: DropKind = DropKind.UNKNOWN, name: String? = null)
@Composable fun FileDropResult(kind, name, actions: List<DropAction>, onDismiss, modifier = Modifier, detail: String? = null, origin: String? = null, compact = isCompactWindow())
```
S1 lo conecta: en escritorio, `externalDropTarget(hovering = { over = it }, onDrop = { dropped = it; true })` en la raíz de la ventana y
`FileDropOverlay(over)` encima de todo; en Android, las intenciones «Abrir con» y «Compartir» llegan directo a `FileDropResult`
(`origin` = «Desde Archivos»). Sin acciones (`UNSUPPORTED`) explica los formatos y ofrece «Entendido».
```kotlin
Box(Modifier.fillMaxSize().externalDropTarget(hovering = { over = it }, onDrop = { dropped = it; true })) { App(); FileDropOverlay(over) }
dropped?.let { d -> FileDropResult(d.kind(), d.displayName(), actionsFor(d), onDismiss = { dropped = null }) }
```

## 5. Selector de estado de lista (`stp`, `StatusPicker.kt`)

```kotlin
fun StatusSlot.toListStatus(): ListStatus            // inversa de ListStatus.slot()
@Composable fun StatusSlot.label(): String           // redacción de anime; manga y novela pasan su labelOf
val StatusSlot.icon: ImageVector
@Composable fun ListStatusPicker(selected: StatusSlot?, onSelect: (StatusSlot) -> Unit, modifier = Modifier, labelOf: @Composable (StatusSlot) -> String = { it.label() }, disabledReason: (StatusSlot) -> String? = { null }, compact = isCompactWindow(), statuses: List<StatusSlot> = StatusSlot.entries)
@Composable fun ProgressStepper(value: Int, onValueChange: (Int) -> Unit, max: Int, modifier = Modifier, total: Int? = null, label: String = …)
object ListEntryRules { fun statusForProgress(status: StatusSlot?, progress: Int): StatusSlot?; fun progressForStatus(status: StatusSlot, progress: Int, total: Int?): Int }
@Composable fun ListStatusEditor(status: StatusSlot?, onStatusChange, progress: Int, onProgressChange, aired: Int, onRemove, modifier = Modifier, total: Int? = null, labelOf = …, disabledReason = …, compact = isCompactWindow())
```
Grupo de radio real (flechas cambian el estado), cada mosaico dibuja `StatusMark` de la dirección, con su icono. Ancho: una fila de
seis; teléfono: rejilla 3 × 2 de 64 dp. El estado actual y el contador animan con `StatusChangeTransition`. La región lleva
`decor(DecorRegion.STATUS_PICKER)`.
```kotlin
ListStatusPicker(entry?.status?.slot(), onSelect = { vm.setStatus(it.toListStatus()) }, disabledReason = { if (it == StatusSlot.REWATCHING && !completed) onlyCompleted else null })
ProgressStepper(entry.progress, vm::setProgress, max = aired, total = media.episodes)
```

## 6. Facetas con chips y popover (`fac`, `Facets.kt`)

```kotlin
data class FacetOption(val id: String, val label: String, val count: Int? = null)
data class Facet(val id: String, val label: String, val options: List<FacetOption>, val single: Boolean = false, val searchable: Boolean = options.size > 10)
typealias FacetSelection = Map<String, Set<String>>
fun FacetSelection.isActive(facet: Facet): Boolean; fun FacetSelection.with(facet: Facet, picked: Set<String>): FacetSelection
fun facetSummary(facet: Facet, selection: FacetSelection): String          // "Género: Acción, Drama +2"
@Composable fun FacetBar(facets, selection, onSelectionChange: (FacetSelection) -> Unit, resultCount: Int, modifier = Modifier, resultText: String? = null, compact = isCompactWindow())
@Composable fun FacetPanel(facet, picked: Set<String>, onPickedChange: (Set<String>) -> Unit, resultCount: Int, onDone: () -> Unit, modifier = Modifier)
```
Los conteos por opción (`FacetOption.count`) y el total los calcula la pantalla (un `derivedStateOf` sobre la lista filtrada con las
demás facetas). Cada faceta activa muestra su valor y una × para quitarla.
```kotlin
var sel by remember { mutableStateOf<FacetSelection>(emptyMap()) }
FacetBar(facets(sel), sel, onSelectionChange = { sel = it }, resultCount = results.size, resultText = countText)
```

## 7. Selector de fuente y reglas automáticas (`src`, `SourceRules.kt`; la pantalla es de S4)

```kotlin
data class RuleOption(val id: String, val label: String)
@Composable fun RuleChipRow(label, options: List<RuleOption>, selected: Set<String>, onToggle: (optionId: String) -> Unit, modifier = Modifier, single = false)
@Composable fun RuleSummary(text: String, modifier = Modifier)
@Composable fun SourceChoiceBadge(manual: Boolean, modifier = Modifier)
@Composable fun WhyNotRow(reasons: List<String>, modifier = Modifier)
data class SourceAttribute(val key: String, val text: String)
@Composable fun AttributeChips(attrs: List<SourceAttribute>, modifier = Modifier, compareTo: List<SourceAttribute>? = null)
```
```kotlin
RuleChipRow(minQuality, resolutions, selected = setOf(rules.minRes), single = true, onToggle = vm::setMinRes)
AttributeChips(source.attrs(), compareTo = chosen.attrs().takeIf { source != chosen }); WhyNotRow(fails(source, rules))
```

## 8. Menú contextual unificado (`ctx`, `ContextMenu.kt`)

```kotlin
sealed interface MenuEntry
data class MenuAction(label, onClick, icon: ImageVector? = null, enabled = true, destructive = false, checked: Boolean? = null, shortcut: String? = null) : MenuEntry
data class MenuSubmenu(label, entries: List<MenuEntry>, icon: ImageVector? = null) : MenuEntry
data object MenuDivider : MenuEntry
enum class ContextMenuForm { DROPDOWN, SHEET }
@Stable class ContextMenuState { val form: ContextMenuForm?; val isOpen: Boolean; fun showAt(offset: Offset); fun showSheet(); fun dismiss() }
@Composable fun rememberContextMenuState(): ContextMenuState
@Composable fun ContextMenuArea(entries: () -> List<MenuEntry>, modifier = Modifier, state = rememberContextMenuState(), enabled = true, sheetHeader: (@Composable () -> Unit)? = null, content: @Composable BoxScope.(ContextMenuState) -> Unit)
fun Modifier.contextMenuGestures(state: ContextMenuState, areaSize: () -> IntSize): Modifier
@Composable fun ContextMenuButton(entries: () -> List<MenuEntry>, modifier = Modifier, contentDescription = "Más acciones", sheetHeader = null, compact = isCompactWindow())
@Composable fun MenuEntriesDropdown(expanded, entries, onDismiss, modifier = Modifier)
@Composable fun MenuEntriesSheet(entries, onDismiss, header: (@Composable () -> Unit)? = null)
@Composable fun MenuSheetHeader(title: String, modifier = Modifier, supporting: (@Composable () -> Unit)? = null)
```
Una sola lista de acciones: clic derecho la abre en el puntero, Shift+F10 o la tecla Menú con el foco dentro, ⋮ bajo el botón (hoja en
teléfono) y la pulsación larga como hoja (pásale `menu::showSheet` al `onLongClick` de la tarjeta). Los submenús reemplazan la lista
en su lugar con «Volver» (→ y ← en teclado).
```kotlin
ContextMenuArea(entries = { actionsFor(item) }, sheetHeader = { MenuSheetHeader(item.title) }) { menu -> TitleCard(item, onClick = { open(item) }, onLongClick = menu::showSheet) }
ContextMenuButton(entries = { actionsFor(item) }, contentDescription = moreFor(item.title))
```

## 9. Ajustes con vista previa en vivo (`sub`, `LivePreview.kt`)

```kotlin
@Composable fun LivePreviewScaffold(preview: @Composable () -> Unit, modifier = Modifier, caption: String? = null, stacked = isCompactWindow(), controlsWidth: Dp = 360.dp, embedded: Boolean = false, controls: @Composable ColumnScope.() -> Unit)
@Composable fun ResetStyleButton(enabled: Boolean, modifier = Modifier, label: String = "Restablecer estilo", onReset: () -> Unit)
```
Ancho: controles a la izquierda (se desplazan) y vista previa al lado, siempre visible. Teléfono: vista previa arriba fija y controles
debajo.
```kotlin
LivePreviewScaffold(preview = { SubtitlePreview(style) }, caption = unitsNote) { SizeSlider(style, vm::set); ResetStyleButton(style != Default) { reset() } }
```
`embedded`: con `false` (por defecto, pantalla completa) llena el alto disponible y desplaza los controles por su cuenta. Con `true`
toma solo el alto de su contenido y no tiene desplazamiento propio: es el modo para ponerlo dentro de algo que ya se desplaza (una
hoja, una columna con `verticalScroll`, un ítem de `LazyColumn`). Ahí la vista previa no queda fija: se desplaza con lo demás.
```kotlin
Column(Modifier.verticalScroll(rememberScrollState())) {
    LivePreviewScaffold(preview = { ReaderPagePreview(prefs) }, embedded = true) { … }
}
```

## 10. Selectores en lugar de campos crudos (`pk`, `Pickers.kt`)

```kotlin
@Composable fun NumberStepperField(value: Long, onValueChange: (Long) -> Unit, min: Long, max: Long, label: String, modifier = Modifier, step: Long = 1, bigStep: Long = step * 10, suffix: String? = null, errorText: String? = null, width: Dp = 220.dp)
data class PortCheck(val ok: Boolean, val message: String, val suggestion: Int? = null)
@Composable fun PortPicker(port: Int, onPortChange: (Int) -> Unit, label: String, modifier = Modifier, defaultPort: Int? = null, onTest: (suspend (Int) -> PortCheck)? = null, warnPrivileged = true)
fun folderPathSegments(path: String, internalLabel: String): List<String>
@Composable fun FolderPathPicker(path: String?, onPathChange: (String) -> Unit, label: String, modifier = Modifier, hint: String? = null)
fun formatTimeOfDay(minutes: Int): String
@Composable fun TimeOfDayPicker(minutes: Int, onMinutesChange: (Int) -> Unit, label: String, modifier = Modifier, stepMinutes: Int = 15)
@Composable fun TimeRangePicker(start: Int, end: Int, onChange: (start: Int, end: Int) -> Unit, label: String, modifier = Modifier, stepMinutes: Int = 15, allowOvernight = true)
data class QuantityUnit(val id: String, val label: String, val factor: Long)
@Composable fun speedUnits(): List<QuantityUnit>   // KiB/s base; también sizeUnits() (MB), minuteUnits() (min), secondUnits() (s)
fun bestUnit(base: Long, units: List<QuantityUnit>): QuantityUnit; fun formatQuantity(base: Long, units: List<QuantityUnit>): String
@Composable fun QuantityPicker(baseValue: Long, onBaseValueChange: (Long) -> Unit, units: List<QuantityUnit>, label: String, modifier = Modifier, min: Long = 0, max: Long = Long.MAX_VALUE / 4, presets: List<Long> = emptyList(), presetLabel: (Long) -> String = …)
```
Cada uno valida y dice por qué: el campo numérico nunca va solo (− y +, ↑/↓, RePág/AvPág, rango como error), el puerto se prueba con
`onTest` (la pantalla abre el puerto y responde), la carpeta usa el selector del sistema (`rememberFolderPicker`) y muestra la ruta en
migas, las horas son pasos que dan la vuelta, y la cantidad lleva su unidad.
```kotlin
PortPicker(settings.linkPort, vm::setLinkPort, linkPortLabel, defaultPort = 47321, onTest = { p -> vm.checkPort(p) })
QuantityPicker(limitKibs, vm::setLimit, speedUnits(), limitLabel, min = 1, presets = listOf(1024L, 5 * 1024L))
```

## 11. Deshacer (`Undo.kt`)

```kotlin
@Stable class UndoEntry { val id: Long; val message: String; val undone: Boolean; val undoable: Boolean }
@Stable class UndoController(val timeoutMillis: Long = 8_000, historySize: Int = 20) {
    val history: SnapshotStateList<UndoEntry>; val current: UndoEntry?; val confirmation: String?; val pendingCount: Int
    fun show(message: String, undo: (() -> Unit)? = null, onCommit: (() -> Unit)? = null): UndoEntry
    fun undo(entry: UndoEntry); fun dismiss(); fun commitAll()
}
@Composable fun rememberUndoController(timeoutMillis: Long = 8_000): UndoController
@Composable fun UndoSnackbar(controller: UndoController, modifier = Modifier)
fun AppSnackbar.showUndo(message: String, undoLabel: String, onUndo: () -> Unit)
@Composable fun rememberShowUndo(): (message: String, onUndo: () -> Unit) -> Unit
```
Regla del rediseño: confirmar o deshacer, de forma uniforme. Haz la acción y luego `show(mensaje, undo = { revertir })`. La cola no
pisa la anterior: «Anteriores (n)» y, al vencer, el chip «Deshacer recientes (n)» listan cada acción. `onCommit` sirve para trabajo
diferido (borrar archivos solo cuando ya no se puede deshacer): corre al salir de la cola o de la pantalla. Entra y sale con
`SnackbarTransition`; la línea de vida no se dibuja con `CALM` ni `OFF`, y se pausa con el puntero encima o el foco dentro. Para un
aviso simple por el host único de la app, `rememberShowUndo()`.
```kotlin
val undo = rememberUndoController(); vm.remove(item); undo.show(removedMsg, undo = { vm.restore(item) })
Box { Content(); UndoSnackbar(undo, Modifier.align(Alignment.BottomCenter)) }
```

## Galería de diseño (`gallery/`)

```kotlin
@Composable fun DesignGalleryScreen(onBack: () -> Unit, modifier = Modifier, cards: List<TitleCardData> = gallerySampleCards())
@Composable fun gallerySampleCards(): List<TitleCardData>
```
Con el tema activo: cada dirección con sus paletas (primario, secundario, terciario, superficies y los seis colores de estado), cada
estilo de `TitleCards.all` en los seis `CardContext`, los huecos de decoración (`SectionHeader`, `EpisodeMark` ×4, `StatusMark` ×6,
`ProgressTrack`, `EmptyArt` por `EmptyKind`), un disparador por momento de `MotionMoments` y demos vivas de los once patrones. Datos
propios de ejemplo, nada de repositorios. La abre el shell (S1) o Ajustes › Apariencia (S10).
```kotlin
DesignGalleryScreen(onBack = { nav.pop() })
```
