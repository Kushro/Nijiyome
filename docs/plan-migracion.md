# Nijiyome — plan de migración a Compose Multiplatform

Fecha: 2026-11-22 · Origen: Tatakai Next 0.4.2 (`Kushro/Tatakai-own`, etiqueta `v0.4.2` = `196bfe89`) · Destinos: Android, Linux x86_64, Windows x64 (sin macOS ni iOS por ahora).
Alcance: solo Next (`apps/android-native`, `apps/desktop-native`, `contracts/`, `packages/tokens/`). La rama `legacy` queda fuera.
Las mediciones de la sección 1 y 4 son de 0.4.1; 0.4.2 suma ~5 900 líneas en 67 archivos (rediseño de la Ficha) y no cambia la estructura.

## 1. Qué hay hoy (medido en el código)

| Parte | Stack | Tamaño |
|---|---|---|
| Android | Kotlin 2.4.0, Jetpack Compose (M3 Expressive `1.5.0-alpha15`), Navigation 3, Koin, Room, Media3 + libmpv, 36 módulos Gradle | ~107 000 líneas de Kotlin, 211 archivos de test |
| Escritorio | Rust 1.94 + Dioxus 0.7 (WebKitGTK / WebView2) + libmpv, 8 crates | ~140 000 líneas de Rust + 10 000 de CSS |
| Compartido | `contracts/` (OpenAPI de Link, host de extensiones, checklist de paridad, fixtures), `packages/tokens/` | — |

Consecuencias para el plan:

1. **El origen no es Flutter.** Android ya es Compose, así que no hay que traducir widgets: el código de Android pasa a ser la base de Nijiyome y el escritorio se le agrega como target `jvm`.
2. **No hace falta cambiar OkHttp por Ktor, ni jsoup, ni `java.security`.** Esa recomendación vale cuando hay iOS. Con Android + JVM de escritorio todas esas librerías corren igual en los dos lados.
3. **Lo que se reemplaza es el escritorio en Rust.** De sus 140 000 líneas, la UI (`tk-ui`, 62 000 + CSS) y la lógica que Android ya tiene duplicada (`tk-core`, gran parte de `tk-app`) desaparecen. Lo que sí hay que portar es lo que solo existe en escritorio (sección 5).
4. De los 57 800 líneas de UI Compose (`designsystem`, `ui`, `feature/*`), solo ~44 de 135 archivos de `feature/*` importan APIs de Android. El resto pasa a `commonMain` casi sin cambios.

## 2. Riesgo principal: el video incrustado en escritorio

El ADR-000 de Tatakai Next descartó Compose Desktop justamente por esto ("embeber mpv en Swing/Skia es más difícil que el HWND hijo"). El estado a noviembre de 2026:

| Camino | Windows | Linux X11 / XWayland | Wayland nativo | Notas |
|---|---|---|---|---|
| Render API de mpv hacia una textura compartida con Skia (lo que hace [MediaMP](https://github.com/open-ani/mediamp), usado por [Animeko](https://github.com/open-ani/animeko)) | Sí: D3D11 de mpv 0.42 → textura abierta en el D3D12 de Skiko | Sí: contexto GLX en el grupo de Skiko | No | Controles de Compose encima sin trucos. SDR de 8 bits. Entra a campos privados de Skiko por reflexión: hay que revalidar en cada actualización de Compose. |
| `--wid` sobre un `Canvas` de AWT en `SwingPanel` | Sí | Sí | No | El panel siempre queda por encima de Compose; `compose.interop.blending` no sirve en Linux. Descartado para controles propios. |
| Ventana de mpv aparte por JSON IPC (el modo "mpv externo" actual) | Sí | Sí | Sí | Ya existe en `tk-player/external.rs` (1 033 líneas) y es por donde hoy pasa la generación de cuadros con lsfg-vk. Respaldo garantizado. |

Tres cosas que cambian respecto a hoy:

- **Compose Desktop no es nativo de Wayland**: corre por XWayland ([SKIKO-28](https://youtrack.jetbrains.com/issue/SKIKO-28) sigue abierto). Tu equipo principal es KDE Plasma en Wayland y hoy la app es nativa ahí. Hay que probar escalado fraccional y nitidez antes de comprometerse.
- **HDR sigue sin camino incrustado**, igual que hoy con GTK 3 / GL (`10-linux-video-embed-debt.md`). No es regresión.
- **La generación de cuadros incrustada sigue sin camino** (la render API da D3D11 u OpenGL, LSFG es Vulkan). Se mantiene lo actual: mpv externo con Vulkan + lsfg-vk.

No verificado y a confirmar en el spike: Anime4K y shaders `//!COMPUTE` por este camino, y el modo uosc de mpv-anime-build con reenvío de entrada.

Por eso la fase 0 incluye un spike de reproductor con criterio de aprobado/no aprobado. Su resultado decide el modo por defecto en escritorio (incrustado o ventana aparte), no si la migración sigue.

## 3. Stack propuesto (versiones verificadas al 2026-11-22)

| Pieza | Versión | Nota |
|---|---|---|
| Kotlin | 2.4.21 | 2.5.0 sale en diciembre; no subir durante la migración. |
| Compose Multiplatform | 1.12.2 | 1.13 está en RC; subir cuando sea estable (trae caché AOT de arranque). |
| Material 3 (CMP) | `1.12.0-alpha03` (= Jetpack M3 `1.5.0-alpha22`) | Las APIs Expressive que usa la app (`MaterialExpressiveTheme`, `MotionScheme`, `MaterialShapes`, `ButtonGroup`, `LoadingIndicator`, `HorizontalMultiBrowseCarousel`, toolbars) están en el artefacto de escritorio. Hoy la app está en alpha15: hay deriva de firmas menor. |
| Navigation 3 (CMP) | 1.1.2 (androidx 1.1.7) | Estable en escritorio. La app estaba en 1.0.1; se eligió la línea 1.1 por ser el salto más chico que tiene versión multiplataforma. Subir a 1.2 queda para después de la prueba final. |
| Adaptive / Lifecycle (CMP) | 1.3.0 / 2.11.0 | Incluye `lifecycle-viewmodel-navigation3`. |
| AGP + Gradle | 9.3.1 + Gradle 9.5–9.7 | Es el tope del rango probado con Kotlin 2.4.21. Hoy: AGP 8.13.2 + Gradle 9.3.1. |
| Koin / Room / DataStore / Coil | 4.2.2 / 2.8.5 / 1.3.0 / 3.6.3 | Todas publican variante JVM. Room 2.8 se queda (Room 3 es cambio de paquete y de API; la base tiene 434 líneas, se evalúa después). |
| quickjs-kt | 1.0.18 | Tiene target `jvm`. Falta confirmar que el jar traiga nativos de Windows x64 y Linux x64. |
| libtorrent4j | 2.1.0-39 (la misma de hoy) | Publica nativos de Windows x64 y Linux x64 además de Android. |
| Servidor HTTP (Link) | Ktor 3.6.1 con Netty | CIO no sirve para TLS del lado servidor. |
| JDK | Toolchain 21 para compilar; JBR para empaquetar | En este equipo hay 17, 21 y 27. Hot Reload pide JBR. |

**AGP 9 obliga a reestructurar**: el plugin KMP ya no convive con `com.android.application` / `com.android.library`. La estructura recomendada (y obligatoria) es:

```
androidApp/        com.android.application   (Activity, servicios, manifest, firma)
desktopApp/        kotlin("jvm") + compose.desktop   (main(), ventana, empaquetado)
core/*, feature/*  módulos compartidos (ver tipos abajo)
native/temple-lsfg helper C++ de LSFG, sin cambios
contracts/, tokens/
```

Los módulos KMP pierden build types, flavors y `BuildConfig`; `res/` y tests de Android son opt-in. Los 5 plugins de convención actuales (`tatakai.*`) se reescriben como:

| Plugin nuevo | Para | Módulos |
|---|---|---|
| `nijiyome.jvm.library` | Kotlin/JVM puro, lo consumen Android y escritorio sin cambios | `model`, `metadata`, `extensions`, `release` (ya lo son: 11 200 líneas), más `network` y `manga` (no importan nada de Android), más la lógica que se extraiga de los módulos de abajo |
| `nijiyome.kmp.library` | Servicios con parte de plataforma (`androidMain` / `desktopMain`) | `database`, `data`, `player`, `player-mpv`, `link`, `torrent`, `download`, `extensions-quickjs`, `extension-server`, `framegen`, `novel`, `social`, `discover` |
| `nijiyome.kmp.compose` / `nijiyome.kmp.feature` | UI en `commonMain` | `designsystem`, `ui`, los 14 `feature/*` |

Regla de diseño: **KMP no permite un source set compartido Android + JVM con acceso al JDK** (según la documentación de jerarquías; limitación KT-42466). Por eso el código que usa OkHttp, jsoup o `java.security` y debe correr en los dos lados va en módulos `jvm.library`, no en `commonMain`. `commonMain` queda para Compose, modelos y lo que solo use kotlinx.

## 4. Mapa de módulos Android → Nijiyome

| Módulo | Líneas | Acoplamiento a Android | Qué pasa |
|---|---|---|---|
| `model`, `metadata`, `extensions`, `release` | 11 167 | ninguno | Sin cambios. |
| `network`, `manga` | 2 941 | solo `androidContext` de Koin | Pasan a `jvm.library`. |
| `designsystem`, `ui` | 12 634 | 4 archivos (insets, color dinámico) | A `commonMain`; color dinámico y barras del sistema como `expect/actual`. |
| `feature/*` (14) | 45 217 | `LocalContext` ×103, `rememberLauncherForActivityResult` ×51, `LocalConfiguration` ×21, `BackHandler` ×12, `AndroidView` ×4 | A `commonMain`. Selector de archivos, permisos y compartir pasan a interfaces de plataforma. |
| `database` | 434 | Room | Room KMP con `BundledSQLiteDriver` en escritorio. |
| `data` | 3 915 | DataStore, WorkManager ×16, Keystore | DataStore KMP; secretos: Keystore en Android, llavero del sistema en escritorio; WorkManager detrás de una interfaz de tareas. |
| `player` | 5 629 | Media3, OkHttp | La lógica (subtítulos, traducción, `PlaybackController`, `SourceFlow`: ~3 500 líneas) es JVM pura. Media3 y `PlaybackService` quedan en `androidMain`. `PlayerSession` ya es la interfaz que el escritorio implementa. |
| `player-mpv` | 2 970 + shaders | superficie y ciclo de vida | `MpvQualityController`, `QualityPlanner` y los shaders son opciones de mpv: se comparten con el libmpv de escritorio. |
| `extensions-quickjs` | 3 308 | `android.webkit` ×21 | El host QuickJS se comparte. `WebViewBrowserRuntime` y `WebViewEmbedSniffer` necesitan un equivalente de escritorio (sección 5). |
| `torrent` | 4 078 | servicio en primer plano, notificaciones | El motor libtorrent4j se comparte; el servicio queda en `androidMain`. |
| `download` | 1 423 | Media3 `DownloadManager` | Android queda igual. Escritorio necesita motor propio (port de `tk-core/download`). |
| `link` | 2 926 | Keystore, notificaciones | Cliente compartido; se le suma el servidor (sección 5). |
| `framegen` | 4 000 + 90 C/C++ | NDK, `Surface` | Solo Android, sin cambios. |
| `novel`, `social`, `discover`, `extension-server` | 5 128 | TTS, WorkManager, notificaciones | Lógica compartida; TTS y notificaciones por plataforma. |

Recursos: 2 944 cadenas en 58 archivos (`en` + `es`), 2 685 llamadas a `stringResource`, 35 plurales y 141 `getString` con `Context`. Compose Resources acepta `strings.xml`, plurales y `values-es`, así que es una migración mecánica (`R.string.x` → `Res.string.x`) hecha con un script. Los 141 `getString` hay que revisarlos a mano. Límite conocido: solo argumentos `%1$s` / `%1$d`.

## 5. Lo que solo existe en escritorio y hay que portar

| Hoy en Rust | Líneas | En Nijiyome | Tamaño |
|---|---|---|---|
| `tk-player` (libmpv incrustado, mejorado con mpv-anime-build, mpv externo por IPC) | 4 055 | Implementación de escritorio de `PlayerSession`. Incrustado según el resultado del spike; externo por socket unix / named pipe con el JDK. | L, riesgo alto |
| `tk-link` (servidor Link: emparejamiento, sesiones FFmpeg, LSFG, subtítulos, historial) | 9 926 | Servidor en Kotlin (Ktor + Netty). FFmpeg y `temple-lsfg` ya son procesos externos por stdin/stdout: no hay binding nativo. Cliente y servidor comparten DTOs y se prueban uno contra otro en el mismo proceso. | L |
| `tk-server` (servidor integrado de extensiones) | 3 714 | Mismo servidor Ktor. | M |
| `tk-torrent` (librqbit) | 2 558 | Se reemplaza por el `core/torrent` de Android (libtorrent4j). | S |
| `tk-ext` (rquickjs) | 2 990 | Se reemplaza por `extensions` + `extensions-quickjs`. | S |
| Navegador oculto (Cloudflare) y sniffer de embeds | ~1 400 | Chromium embebido (KCEF/JCEF). Suma ~150–200 MB; conviene descargarlo al primer uso de una extensión que lo pida. | M, riesgo medio |
| `tk-core/download` (HLS, archivos, biblioteca local, watcher) | ~2 100 | Port a Kotlin/JVM. | M |
| Integración con el sistema: bandeja, Discord, autoarranque, instancia única, `magnet:` y OAuth, firewall, llavero, aviso de actualización | ~2 700 | Bandeja con ComposeNativeTray; el resto son ports chicos (D-Bus, registro, `netsh`). Compose no trae nada de esto. | M |
| TTS (sistema, nube, KittenTTS por ONNX) | ~2 350 | ONNX Runtime tiene binding Java; el resto es proceso externo y HTTP. | M |
| `temple-lsfg` (C++/Vulkan) | 1 450 | Sin cambios; se compila con CMake y va en los recursos de la app. | — |
| Empaquetado: AppImage, PKGBUILD, NSIS | — | `jpackage` da MSI/EXE y DEB/RPM. AppImage no viene incluido: se arma con `appimagetool` sobre la imagen de jpackage (lo que hace Animeko). No hay compilación cruzada: Linux y Windows se construyen cada uno en su CI. | M |

`tk-ui`, `tk-core` y `tk-app/live` no se portan: se usan como referencia de comportamiento, con `contracts/parity/checklist.json` como lista de control.

## 6. Fases

Cada fase termina en algo que corre. Android se puede publicar desde la fase 1 y nunca deja de compilar.

**Fase 0 — Spikes y decisiones (no toca el proyecto)**
- S1 Reproductor: app Compose Desktop mínima con libmpv incrustado, controles de Compose encima, una fuente HLS con cabeceras y Anime4K A+A. Probar en KDE Wayland (XWayland), X11 y Windows. Aprobado si: 1080p→4K sin cuadros perdidos sostenidos, shaders activos, nitidez correcta con escalado fraccional, cambio a pantalla completa estable. Sale también la decisión MediaMP como dependencia, fork, o JNI propio. **Si no aprueba, la migración sigue igual**: mpv en ventana aparte pasa a ser el modo por defecto y el incrustado queda como deuda técnica (sección 9).
- S2 Build: esqueleto con AGP 9.3.1 + Kotlin 2.4.21 + CMP 1.12.2 y `designsystem` compilado para escritorio mostrando la galería del tema.
- S3 Nativos: correr en JVM de escritorio los tests de fixtures de `extensions-quickjs` y un magnet con libtorrent4j, en Linux y Windows.
- S4 Paridad: cruzar `checklist.json` para listar qué hace el escritorio que Android no.
- Salida: informe de spikes. Ningún resultado frena la fase 1 (decisión 1 de la sección 8): S1 solo define cuál es el modo de reproductor por defecto en escritorio, así que la fase 0 y la fase 1 pueden ir en paralelo.

**Fase 1 — Repositorio e identidad (sigue siendo solo Android)**
- Repo público nuevo con historial limpio: un commit inicial que cita `Kushro/Tatakai-own@196bfe89` (v0.4.2) como origen. Licencia AGPL-3.0-or-later, con `THIRD_PARTY_NOTICES.md`.
- Renombrado total con un script reproducible: paquete `app.nijiyome`, `applicationId`, cadenas, carpetas de datos, esquema `nijiyome://`, servicio `_nijiyome-link._tcp`, variables `NIJIYOME_*`, nombres de binarios, artefactos e íconos. No queda "Tatakai" en nada visible.
- Globals del host de extensiones: pasan a `__nijiyome_fetch__`, `__nijiyome_parse_html__`, `__nijiyome_anitomy__` y `__nijiyome_chromedp__`. El host mantiene los nombres `__tatakai_*__` como alias internos, porque Toko y SpanishPack (y cualquier `.kai` de terceros) los llaman hoy; el alias se quita cuando las extensiones propias estén recompiladas. El formato `.kai` no cambia.
- Sin importación de datos de Tatakai Next: la cuenta de AniList/MAL trae listas y progreso, y las extensiones se instalan de nuevo.
- Clientes OAuth nuevos de AniList y MAL (cambia la URI de redirección; uno por plataforma), clave de firma nueva y su huella en la verificación del CI.
- CI portado (sección 10) y escaneo de secretos antes del primer push.
- Salida: Nijiyome para Android compila, pasa los tests y se comporta como Tatakai Next 0.4.2.

**Fase 2 — Build multiplataforma y shell de escritorio**
- Subida de toolchain, plugins de convención nuevos, `androidApp` + `desktopApp`.
- Recursos a Compose Resources; `designsystem` y `ui` a `commonMain`.
- Salida: ventana de escritorio con navegación y un Home que carga metadata real (los módulos JVM puros ya funcionan).

**Fase 3 — Datos y pantallas sin video**
- `database`, `data`, secretos, extensiones en escritorio; Home, Buscar, Ficha, Biblioteca, Ajustes, Extensiones, Tú, Social, Manga y Novelas.
- UX de escritorio: rail y paneles con Adaptive, teclado, hover, menús contextuales, barras de scroll, tamaño y posición de ventana.
- Salida: se puede navegar, iniciar sesión en AniList/MAL, instalar extensiones y leer manga y novelas en escritorio.

**Fase 4 — Reproductor de escritorio**
- `PlayerSession` de escritorio, `feature/player` compartido, mejoras de video (reutilizando `MpvQualityController`), subtítulos y traducción, modo mejorado con mpv-anime-build, mpv externo y generación de cuadros por lsfg-vk.
- Salida: primera alfa de escritorio usable para ver anime.

**Fase 5 — Torrents y descargas en escritorio**
- libtorrent4j con streaming por loopback, cliente de torrents, motor de descargas HLS, biblioteca local.

**Fase 6 — Servidor Link y servidor integrado**
- Port de `tk-link` y `tk-server` con los tests de Rust y `contracts/link/openapi.v1.json` como oráculo; mDNS; sincronización de historial.
- Salida: el teléfono con Nijiyome se empareja y procesa en la PC con Nijiyome.

**Fase 7 — Sistema, empaquetado y CI**
- Bandeja, Discord, autoarranque, instancia única, enlaces `magnet:` y OAuth, llavero, actualizaciones.
- AppImage, PKGBUILD, instalador y zip de Windows, APK; CI en Ubuntu y Windows.
- Adaptar el arnés de `qa/` al binario nuevo.

**Fase 8 — Beta y retiro de Rust**
- Auditoría de paridad contra Tatakai Next, beta, 1.0. Tatakai Next se archiva.

Las fases 5, 6 y 7 son independientes entre sí y pueden ir en paralelo después de la 4. La 3 y la 4 también, si el spike S1 aprobó.

## 7. Qué se gana y qué se pierde

| Se gana | Se pierde o empeora |
|---|---|
| Una sola UI y una sola lógica: se dejan de mantener ~140 000 líneas de Rust y las olas de paridad Android ↔ escritorio | Wayland nativo (pasa a XWayland) |
| Cada feature se escribe una vez; los DTOs de Link se comparten entre cliente y servidor | Tamaño y memoria: JVM empaquetada (instalador estimado 40–75 MB sin libmpv ni Chromium, dato comunitario sin verificar) frente a un binario Rust con la web view del sistema |
| Se elimina la deuda de WebKitGTK/DMA-BUF del video en Linux | Arranque más lento (JetBrains mide ~1,4 s en frío en Windows, ~0,65 s con la caché AOT de CMP 1.13) |
| Hot Reload en escritorio para iterar UI | El video incrustado depende de internos de Skiko; cada subida de Compose exige revalidar |
| Un solo toolchain (Gradle) salvo el helper C++ | El bypass de Cloudflare necesita Chromium propio en vez de la web view del sistema |

## 8. Decisiones tomadas (2026-11-22)

| # | Tema | Decisión |
|---|---|---|
| 1 | Video incrustado | Si el spike no convence, se sigue con mpv en ventana aparte y el incrustado queda como deuda técnica. La migración no se cancela por esto. |
| 2 | Servidor Link | Se porta a Kotlin (Ktor + Netty). No queda Rust como proceso auxiliar. |
| 3 | Historial | Limpio, desde cero, citando solo el commit de origen. |
| 4 | Línea base | Tatakai Next 0.4.2 (`v0.4.2` = `196bfe89`), ya publicada y en prueba en teléfono y PC. |
| 5 | Identidad | Reemplazo total: `app.nijiyome`, `~/.config/Nijiyome/`, `%APPDATA%\Nijiyome\`, `nijiyome://`, `_nijiyome-link._tcp`. Única excepción técnica: alias internos de los globals de extensiones (fase 1). |
| 6 | Datos de Tatakai Next | No se importan. AniList/MAL trae la cuenta; las extensiones `.kai` se instalan de nuevo. |
| 7 | Repositorio | Público, AGPL-3.0-or-later. El runtime de mpv de MediaMP en Linux es GPL-3.0, compatible. |
| 8 | Ubicación | El repositorio queda en el disco de datos actual (NTFS), no se mueve al disco del sistema. |

## 9. Deuda técnica registrada

| Deuda | Origen | Qué investigar |
|---|---|---|
| Fuga de memoria nativa del reproductor incrustado: 7 a 11 MB por cada apertura y cierre, sin meseta | Spike S1 (`docs/spikes/reproductor.md` §4.1) | Ubicar si es de MediaMP o de libmpv; reusar una sola instancia del reproductor entre episodios como mitigación. |
| Video incrustado sin resolución nativa cuando la escala del monitor difiere de la de XWayland | Compose ignora `Xft.dpi`; KWin reescala la ventana | Leer la escala real del monitor y fijar la densidad de Compose; seguir SKIKO-28 (Wayland nativo). |
| Decodificación por hardware en el reproductor incrustado | La libva que trae MediaMP no funciona en esta GPU | Empaquetar un runtime de Linux contra la libva del sistema, o precargarla. |
| Reproductor incrustado en Windows sin probar | No hay Windows en el equipo de desarrollo | Probar en CI y en una máquina real antes del release. |
| Sin control automático del nivel de API en el código común | Los módulos multiplataforma no pasan por el lint de Android, y `commonMain` compila contra el JDK completo | Una llamada a una API de Java 9+ compila y falla en Android 8 a 12. Evaluar una regla de lint propia o un test que escanee `commonMain`. |
| Los tests de `core:data` corren solo en el host de Android | Se movieron tal cual a `androidHostTest` | Pasar a `commonTest` los que prueban código ya compartido (`MetadataRepositoryImpl`, `ListMapping`). |
| Código común con acceso al JDK: la compilación de metadatos de `commonMain` está desactivada | Kotlin no permite un source set compartido JVM + Android (KT-42466) | Reactivarla si Kotlin lo soporta; mientras tanto el IDE puede marcar como error referencias válidas. |
| Video incrustado: depende de campos privados de Skiko y corre por XWayland | Compose Desktop sin Wayland nativo; MediaMP entra a Skiko por reflexión | Repetir la prueba de humo del spike en cada subida de Compose; seguir SKIKO-28; evaluar un shell sin AWT con subsuperficie Wayland para mpv. |
| HDR incrustado | La textura compartida es RGBA de 8 bits | Depende del backend Vulkan/Graphite de Skiko. |
| Generación de cuadros incrustada | La render API de mpv no da Vulkan | Compartir texturas D3D11↔Vulkan y GL↔Vulkan con el núcleo de `temple-lsfg`. |
| Alias `__tatakai_*__` en el host de extensiones | Compatibilidad con `.kai` existentes | Quitarlo cuando Toko y SpanishPack usen los nombres nuevos. |
| Room 2.8 → Room 3 | Cambio de paquete y de API | Después de la 1.0. |

## 10. CI y releases

Tatakai Next ya tiene el esquema que Nijiyome hereda:

- `runner.yml` (reutilizable) elige el proveedor: **Avrea** (`avrea-ubuntu-24.04-4-vcpu`, `avrea-windows-2025-4-vcpu`) por defecto, y runners de GitHub si la página de estado de Avrea reporta una caída, si la variable `RUNNER_PROVIDER` vale `github`, o si el PR viene de otro repositorio. Ese último caso importa más ahora que el repo es público: los forks nunca corren en los runners de Avrea.
- `next-ci.yml` prueba solo lo que cambió; `next-release.yml` construye desde una etiqueta `v<semver>` el AppImage, el instalador y el zip de Windows y los APK firmados, verifica la huella del certificado y publica el release. Los artefactos de cada corrida se guardan 7 días.
- `security.yml` corre gitleaks en cada push.

Cambios para Nijiyome:

| Qué | Cambio |
|---|---|
| Jobs de escritorio | `cargo build` / `cargo test` → Gradle (`:desktopApp:packageReleaseDistributionForCurrentOS` y tests JVM). Linux y Windows siguen en jobs separados: jpackage no compila en cruzado. |
| Dependencias de sistema en Linux | Se van WebKitGTK, GTK y libxdo. Queda `xvfb` para tests de UI y lo que pida libmpv. |
| `temple-lsfg` | El paso de CMake se conserva tal cual, en Linux y en Windows. |
| Caché | `Swatinem/rust-cache` → caché de Gradle (ya existe para Android). Se elimina `cargo audit`. |
| Versión | La fuente pasa de `Cargo.toml` a `gradle.properties`. |
| Secretos y variables | Nuevos en el repo nuevo: keystore de Nijiyome (generado el 2026-11-22, alias `nijiyome`, fuera del repositorio), `ANILIST_CLIENT_ID_DESKTOP` / `_ANDROID`, `RUNNER_PROVIDER`. Hay que habilitar Avrea para el repositorio nuevo. |
| Huella del certificado de release | SHA-256 `9fe9bad769b690f5768ee9840c6d410627c396496dfefc142ef26818ebb5febd`. Es la que el CI exige en cada APK publicado. |
| Nombres de artefactos | `Nijiyome-v<versión>-linux-x86_64.AppImage`, `…-windows-x64-setup.exe`, `…-portable.zip`, `…-android-<abi>.apk`. |

A confirmar al portar: en un repositorio público los runners estándar de GitHub y el almacenamiento de artefactos no se cobran, así que Avrea pasa a ser una elección de velocidad y no de costo.

## 11. Método de trabajo (acordado el 2026-11-22)

Una línea principal secuencial, módulo por módulo en orden de dependencias, compilando solo el módulo tocado después de cada paso. Sin contrato por ola, integrador ni auditor. Lo mecánico (renombrado, recursos, imports) va por script.

| Hito | Fases | Ayudantes | Build completo |
|---|---|---|---|
| 1. Identidad | 1 | Ninguno. Apenas existe el commit de importación arrancan los 2 spikes de la fase 0. | 1 |
| 2. Fundación | 2 | Ninguno | 1 |
| 3. Pantallas sin video | 3 | Workflow: las 13 pantallas restantes sobre el patrón de `feature/home`, cada una migrar → verificar, de a 3 | 1 |
| 4. Reproductor | 4 | Arrancan los ports de código nuevo: 2 o 3 a la vez, unos 6 en total | 1 |
| 5. Servicios | 5, 6, 7 | Terminan los ports | 1 |
| 6. Release | 8 | Workflow: auditoría de paridad por área, con verificación de cada hallazgo | 1, en CI |

Qué herramienta hace cada cosa:

| Trabajo | Herramienta | Modelo y esfuerzo |
|---|---|---|
| Identidad, fundación, reproductor, merges, coordinación | Sesión principal | Fable 5.2 |
| Spike del reproductor y los ~6 ports (servidor Link, descargas, mpv externo, sistema, empaquetado) | Trabajadores de Orca en worktrees hijos, visibles en la barra lateral | Opus 5.5 high en el spike y en Link; medium el resto |
| Spike de nativos; diferencia de paridad escritorio ↔ Android antes de los ports | Subagentes | Opus 5.5 medium; Sonnet 5.5 medium |
| 13 pantallas (hito 3) | Workflow | Migrar: Opus 5.5 medium. Verificar: Sonnet 5.5 medium |
| Auditoría de paridad final | Workflow | Sonnet 5.5 medium; verificación Opus 5.5 medium |
| Build completo de cada hito | Subagente | Sonnet 5.5 medium |
| Revisión independiente de cada hito (la de Link incluye seguridad) | Antigravity, lanzado por Orca | neon, esfuerzo alto |

- Pico de 3 agentes en paralelo. Los worktrees de los trabajadores de Orca quedan en la carpeta de workspaces de Orca; los de workflows y subagentes, dentro del repo.
- Las métricas del método se registraron en un diario aparte (no publicado) para evaluarlo al final contra lo esperado.
- Un build completo se repite solo si falla. Las compilaciones incrementales de un módulo no cuentan.
- Comprobación por hito: tests automáticos y arranque del binario de escritorio leyendo su log. **Nada se instala en el teléfono antes del build final.**
- Pruebas manuales una sola vez, al final: teléfono real por scrcpy con el audio en silencio y sin tocar la franja del notch; escritorio Linux con AniList y extensiones `.kai`. Windows real lo prueba el usuario.
- Windows se compila solo en CI.
- El avance se anotó en un diario aparte (no publicado).
- El build final se compila y se prueba en local antes de preparar el primer push y la prueba de CI en Avrea. Hasta entonces `github.com/Kushro/Nijiyome` (creado el 2026-11-22, público) queda vacío.
- Pruebas de escritorio sin tomar el teclado ni el ratón del usuario: lógica de UI con tests de Compose (sin ventana); app real en una pantalla virtual con su propia entrada; lo que necesite GPU real (video) en una ventana sin foco en el segundo monitor (DP-2), manejada por el IPC de mpv o un canal de control propio y medida con contadores, no con clics.

### Pendientes del usuario (recordar cuando hagan falta)

| Qué | Cuándo hace falta |
|---|---|
| Clientes OAuth de AniList y MAL con redirección `nijiyome://` (uno por plataforma) | Antes de las pruebas finales con cuenta |
| Habilitar Avrea en `Kushro/Nijiyome` | Antes de la primera corrida de CI |
| Probar el instalador en Windows real | Después del release de CI |
| Respaldar `nijiyome.keystore` fuera de este disco | Cuanto antes |

## 12. Notas del entorno

- El proyecto vive en un disco de datos NTFS montado en Linux. Si Gradle resulta lento o falla por bloqueos de archivos, el primer ajuste es mover solo `GRADLE_USER_HOME` y los directorios `build/` al disco del sistema, sin mover el repositorio.
- Faltan en el equipo: `JAVA_HOME` y `ANDROID_HOME` sin definir (el SDK está en la carpeta del usuario), y no hay IntelliJ ni Android Studio para Hot Reload y previews.
- mpv 0.42 y ffmpeg 9.1 del sistema alcanzan para el spike en Linux.
- El checkout local de `Tatakai-own` está en 0.4.1, cinco commits detrás de `origin/main`. La importación se hace desde la etiqueta `v0.4.2`, no desde el árbol de trabajo.

## Fuentes

- [Compose Multiplatform: versiones y compatibilidad](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) · [novedades 1.12](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html) · [novedades 1.13](https://kotlinlang.org/docs/multiplatform/whats-new-compose-113.html)
- [Migración a AGP 9](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html) · [estructura recomendada](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html) · [guía de compatibilidad](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Jerarquía de source sets](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html) · [Navigation 3 en CMP](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html) · [Compose Resources](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)
- [Distribución nativa](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) · [interop con Swing](https://kotlinlang.org/docs/multiplatform/compose-desktop-swing-interoperability.html) · [deep links](https://kotlinlang.org/docs/multiplatform/compose-navigation-deep-links.html)
- [MediaMP](https://github.com/open-ani/mediamp) · [su render D3D11](https://github.com/open-ani/mediamp/blob/main/mediamp-mpv/mpv-d3d11-rendering.md) · [Animeko](https://github.com/open-ani/animeko) · [render API D3D11 de mpv](https://github.com/mpv-player/mpv/pull/17764)
- [SKIKO-28 (Wayland)](https://youtrack.jetbrains.com/issue/SKIKO-28) · [libtorrent4j](https://github.com/aldenml/libtorrent4j) · [quickjs-kt](https://github.com/dokar3/quickjs-kt) · [compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform) · [ComposeNativeTray](https://github.com/NucleusFramework/ComposeNativeTray)
- [Room KMP](https://developer.android.com/kotlin/multiplatform/room) · [DataStore KMP](https://developer.android.com/kotlin/multiplatform/datastore) · [Ktor SSL](https://ktor.io/docs/server-ssl.html)
