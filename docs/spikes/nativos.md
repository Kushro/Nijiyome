**Veredicto:** `quickjs-kt: FUNCIONA` · `libtorrent4j: FUNCIONA CON RESERVAS` (Linux x86_64 ejecutado; Windows x64 inspeccionado, no ejecutado)

# Spike S3: piezas nativas en JVM de escritorio

Fecha: 2026-11-22. Máquina: CachyOS x86_64, glibc 2.44, kernel 7.3.5. JDK 21.0.13 (`/usr/lib/jvm/java-21-openjdk`), Gradle 9.7.1, Kotlin 2.4.21.
Proyecto descartable: `spikes/nativos/` (Kotlin/JVM puro, sin Android). Todos los builds corrieron con `scripts/acotado.sh`, `--no-daemon --max-workers=4`, de a uno. Ninguno superó el tope de memoria (no hubo código 137 ni 75).

Cómo reproducir (desde `spikes/nativos/`, con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`):

```
bash ../../../scripts/acotado.sh bash gradlew test --no-daemon --max-workers=4                       # quickjs-kt 1.0.15
bash ../../../scripts/acotado.sh bash gradlew test -PquickjsVersion=1.0.18 --no-daemon --max-workers=4
bash ../../../scripts/acotado.sh bash gradlew torrentSpike --no-daemon --max-workers=4               # red, <= 5 min
```

(`acotado.sh` vive en el `scripts/` del repositorio principal; la ruta relativa de arriba vale desde el checkout principal.)

---

## A. quickjs-kt en JVM de escritorio

### A.1 Artefacto y nativos

El artefacto JVM es `io.github.dokar3:quickjs-kt-jvm`. La coordenada KMP `io.github.dokar3:quickjs-kt` (la del catálogo) resuelve sola a esa variante en un proyecto JVM vía Gradle module metadata; en Gradle el jar se guarda como `quickjs-jvm.jar`. Comprobé que es idéntico byte a byte al `quickjs-kt-jvm-<v>.jar` de Maven Central (sha256 abajo).

Los nativos vienen **dentro del jar** y la biblioteca los extrae y carga sola (`com.dokar.quickjs.NativeLibraryLoader`, elige por `os.name`/`os.arch`). No hay que hacer nada más.

| Archivo en el jar | 1.0.15 (bytes) | 1.0.18 (bytes) | Tipo (`file`) |
|---|---|---|---|
| `jni/linux_x64/libquickjs.so` | 988752 | 991448 | ELF 64-bit x86-64 |
| `jni/linux_aarch64/libquickjs.so` | 956520 | 959088 | ELF 64-bit ARM aarch64 |
| `jni/windows_x64/libquickjs.dll` | 861184 | 862720 | PE32+ DLL x86-64 |
| `jni/macos_x64/libquickjs.dylib` | 802180 | 802734 | Mach-O x86_64 |
| `jni/macos_aarch64/libquickjs.dylib` | 852240 | 852784 | Mach-O arm64 |

- sha256 `quickjs-kt-jvm-1.0.15.jar` = `c8a8d0b47e2c3cd6fb21ea618204ba0a04027e09e41dc036c0f60518823f3d6d`; `1.0.18` = `b2267ce5e9186c5815747e3849af1bb9632d93c707100c963adf7653d24eb7d3`.
- Dependencias del `.so` Linux x64: `libm.so.6`, `libpthread.so.0`, `libc.so.6`; símbolo glibc más nuevo que pide: `GLIBC_2.17`, o sea que corre en cualquier distro actual.
- Imports de la `.dll` Windows: `KERNEL32.dll` y `api-ms-win-crt-*` (UCRT, que viene con Windows 10 y posteriores). No pide ningún runtime de MSVC aparte. **Inspeccionado, no ejecutado.**
- POM: 1.0.15 depende de `kotlin-stdlib 2.4.10` y `kotlinx-coroutines-core-jvm 1.11.0`; 1.0.18 depende de `kotlin-stdlib 2.4.21` y `kotlinx-coroutines-core-jvm 1.11.0`. Si se sube a 1.0.18, el stdlib pasa a 2.4.21 (el proyecto usa Kotlin 2.4.0).

### A.2 ¿Los tests actuales de `core/extensions-quickjs` ejecutan QuickJS?

**No.** Ninguno de los tests de `src/test` (CatalogStore, ExtensionRepositoryCandidates, ExtensionRepositoryImpl, ExtensionSearch, HostCallGate, RepoIndex, StreamParamsTitles, StremioWire, WebViewBrowserRuntimeHelpers) instancia `QuickJsEngine` ni `QuickJs`:
- `ExtensionRepositoryImplTest` usa un `JsEngine` que hace `error("engine must not run here")`.
- `ExtensionRepositoryCandidatesTest` usa un `JsEngine` guionado (`object : JsEngine { ... }`).
- `HostCallGateTest` prueba la compuerta en Kotlin puro (el comentario habla de quickjs-kt, pero no lo carga).
- En `:core:extensions`, `ExtensionHostTest` también usa `FakeEngine`/`FakeSession`, que reproducen a mano lo que devolvería el bundle.

Por eso hoy no se usa ningún nativo en el host: el módulo es `tatakai.android.library` (`build.gradle.kts`: `implementation(libs.quickjs.kt)`), así que las pruebas unitarias resuelven la variante Android (`quickjs-kt-android`, un AAR con `.so` de Android). En la caché de Gradle de esta máquina, antes del spike, solo estaban `quickjs-kt` y `quickjs-kt-android` 1.0.15, no `quickjs-kt-jvm`. `NOTES-deps.md` no dice nada del nativo (solo "No new dependencies requested"). **Hasta este spike, QuickJS real no se había ejecutado nunca fuera del teléfono.**

### A.3 Tests en el proyecto descartable

`src/test/kotlin/spike/QuickJsSpikeTest.kt`: todo con `QuickJs` real, sin fakes.
- `asyncHostFunctionAwaitedFromScript`: registra `__host_fetch` con `asyncFunction`, igual que `QuickJsSession.bind()`. La función suspende 150 ms. El script la llama dos veces en paralelo con `await Promise.all(...)` y el test verifica el resultado.
- `memoryLimitStopsAllocation`: `memoryLimit = 16 MiB` y un bucle que reserva memoria tiene que fallar con `out of memory`. Después, el runtime tiene que seguir usable.
- `evaluationTimeoutInterruptsInfiniteLoop`: `evaluationTimeoutMillis = 500` (el mecanismo de `QuickJsEngine`) contra `while(true){}`.
- `interruptEvaluationFromWatchdog`: `interruptEvaluation()` desde otra corrutina a los 300 ms (el watchdog de `QuickJsSession.invoke`) contra `while(true){}`.

Salida con **1.0.15** (la versión del proyecto), `BUILD SUCCESSFUL`, 8/8 tests (incluye los de A.4):

```
[quickjs] os.name=Linux os.arch=amd64 java=21.0.13.1 quickjs-kt=1.0.15 jar=quickjs-jvm.jar 6*7=42
QuickJsSpikeTest > nativeLoadsAndReportsHost PASSED
[quickjs] async result=https://a.example/x|https://b.example/y|200|aG9sYQ== calls=[fetch:https://a.example/x, fetch:https://b.example/y, log:status 200, log:status 200] elapsedMs=160
QuickJsSpikeTest > asyncHostFunctionAwaitedFromScript PASSED
[quickjs] memoryLimit=16MiB -> com.dokar.quickjs.QuickJsException: InternalError: out of memory
QuickJsSpikeTest > memoryLimitStopsAllocation PASSED
[quickjs] evaluationTimeoutMillis=500 -> com.dokar.quickjs.QuickJsInterruptedException: InternalError: interrupted tras 500ms
QuickJsSpikeTest > evaluationTimeoutInterruptsInfiniteLoop PASSED
[quickjs] interruptEvaluation() a los 300ms -> com.dokar.quickjs.QuickJsInterruptedException: InternalError: interrupted tras 300ms
QuickJsSpikeTest > interruptEvaluationFromWatchdog PASSED
```

Salida con **1.0.18**, `BUILD SUCCESSFUL`, 8/8:

```
[quickjs] os.name=Linux os.arch=amd64 java=21.0.13.1 quickjs-kt=1.0.18 jar=quickjs-jvm.jar 6*7=42
[quickjs] async result=https://a.example/x|https://b.example/y|200|aG9sYQ== calls=[fetch:https://b.example/y, fetch:https://a.example/x, log:status 200, log:status 200] elapsedMs=152
[quickjs] memoryLimit=16MiB -> com.dokar.quickjs.QuickJsException: InternalError: out of memory
[quickjs] evaluationTimeoutMillis=500 -> com.dokar.quickjs.QuickJsInterruptedException: InternalError: stack overflow tras 500ms
[quickjs] interruptEvaluation() a los 300ms -> com.dokar.quickjs.QuickJsInterruptedException: InternalError: stack overflow tras 300ms
```

**Ojo con 1.0.18:** la interrupción sigue llegando como `QuickJsInterruptedException`, pero el mensaje pasó de `InternalError: interrupted` a `InternalError: stack overflow`. `QuickJsSession.isInterruption()` primero mira la clase y después el texto `"InternalError: interrupted"`. El comentario de BUG-128 dice que el plazo propio del runtime a veces llega como error JS con ese texto y no como `QuickJsInterruptedException`. Con 1.0.18, ese camino por texto dejaría de reconocer el timeout, y `mapFailure` podría tomarlo como `stack overflow`/`BundleError` en vez de `Timeout`. Este spike no reprodujo el caso envuelto: queda **no comprobado**. Si se sube de versión hay que revisarlo. Con 1.0.15 no hay cambio.

Nota: en un primer intento, `asyncHostFunctionAwaitedFromScript` falló porque el test registraba las llamadas en un `ArrayList` no sincronizado. Las dos funciones async del host corren en paralelo en `Dispatchers.Default`, así que el fallo era del test y no de la biblioteca; se corrigió con `synchronizedList`. Lo dejo anotado porque confirma que los `asyncFunction` del host **sí corren en paralelo**: el código del host que comparta estado tiene que ser thread-safe.

### A.4 Extensión empaquetada ejecutada en QuickJS desde la JVM

Se pudo hacer **sin reescribir ni copiar nada al repositorio**. `spikes/nativos/build.gradle.kts` compila, tal como están, las fuentes de `:core:model`, `:core:release` y `:core:extensions` (ya son módulos `tatakai.jvm.library`, sin Android), más dos archivos de `:core:extensions-quickjs` que no dependen de Android: `QuickJsEngine.kt` y `HostCallGate.kt`. También incluye el recurso `tatakai/host-prelude.js`. Las fuentes se sincronizan a `build/generated/`. Se compilaron sin ningún cambio.

`src/test/kotlin/spike/PackedExtensionTest.kt` lee `contracts/fixtures/extensions/packed/synthetic-full.kai` con `KaiReader` y lo registra en `ExtensionHost` con el `QuickJsEngine` real. Es la misma aserción que `ExtensionHostTest.streamRouteMatchesFixture` contra `expected.stream.json`, pero esta vez ejecutando el bundle en QuickJS. El `JsHost` es mínimo: aplica `NetworkPolicy.requireAllowed` como `OkHttpJsHost` y no sale a la red.

```
[kai] synthetic-full 1.0.0 compat=FULL bundle=3301 chars
[kai] stream en 11 ms: count=3 urls=[https://cdn.alpha.example.invalid/hls/1/3/master.m3u8?t=a28b1e3e, https://cdn.beta.example.invalid/mp4/1/3_720.mp4?t=a28b1e3e, https://cdn.beta.example.invalid/mp4/1/3_480.mp4?t=a28b1e3e]
[kai] providers=2 customHome.firstItem=x1 OK
PackedExtensionTest > syntheticFullStreamRouteMatchesExpected PASSED
[kai] probeNetwork -> "Network permission denied"
PackedExtensionTest > syntheticFullNetworkGate PASSED
[kai] synthetic-fs compat=UNSUPPORTED (expected.json: unsupported)
[kai] synthetic-fs open en QuickJS -> Failure(app.tatakai.next.extensions.ExtError$BundleError: Error: Module 'fs' is not supported by the native extension runtime)
PackedExtensionTest > syntheticFsLoads PASSED
```

El token `a28b1e3e` sale de `require('crypto')` del prelude a través de `__host_hash` (SHA-1 de la JVM). Coincide con el oráculo. Resultados iguales con 1.0.18.

Para el plan de escritorio: de `:core:extensions-quickjs`, lo único atado a Android es `WebViewBrowserRuntime.kt`, `WebViewEmbedSniffer.kt` y `ExtensionsModule.kt` (Koin/Context); son los que importan `android.*`/`androidx.*`. `QuickJsEngine.kt` y `HostCallGate.kt` ya funcionan en JVM. `OkHttpJsHost.kt`, `ExtensionRepositoryImpl.kt`, `ExtensionCatalog.kt`, `StremioAddons.kt` y `ExtensionSearch.kt` no importan Android. **No comprobé que compilen en JVM**: no los incluí en el spike porque A.4 no los necesita.

---

## B. libtorrent4j en JVM de escritorio

### B.1 Artefactos 2.1.0-39 en Maven Central

En `org/libtorrent4j/` existen: `libtorrent4j`, `libtorrent4j-android-{arm,arm64,x86,x86_64}`, `libtorrent4j-linux`, `libtorrent4j-windows`, `libtorrent4j-macos` y `libtorrent4j-macosx`. Este último es viejo: la última versión es 1.2.3.0 y **no hay 2.1.0-39**.

| Coordenada | Contenido nativo | Arquitectura (`file`) | sha256 |
|---|---|---|---|
| `org.libtorrent4j:libtorrent4j:2.1.0-39` | ninguno (solo clases Java/SWIG, 833 KB) | — | `f3cc58c3…3d6d` |
| `org.libtorrent4j:libtorrent4j-linux:2.1.0-39` | `lib/x86_64/libtorrent4j.so` (18137224 B) | ELF 64-bit x86-64 | `4b68d9fc…e835` |
| | `lib/arm/libtorrent4j.so` (14898152 B) | ELF **32-bit ARM** EABI5 (no hay aarch64) | |
| `org.libtorrent4j:libtorrent4j-windows:2.1.0-39` | `lib/x86_64/libtorrent4j.dll` (13076480 B) | PE32+ DLL x86-64 | `98b94baa…e346e` |
| `org.libtorrent4j:libtorrent4j-macos:2.1.0-39` | `lib/arm64/libtorrent4j.dylib` (12457344 B) | Mach-O arm64 (no hay x86_64) | `d23b3a2d…a22` |

Cada jar de plataforma depende en su POM de `libtorrent4j:2.1.0-39`.

- Linux x86_64 `.so`: `NEEDED` es solo `libm.so.6`, `libc.so.6` y `ld-linux-x86-64.so.2` (libstdc++ va enlazado estáticamente), pero **pide `GLIBC_2.38`** (`__isoc23_strtol`, `__isoc23_sscanf`, etc.) y `GLIBC_2.34`. Ver las sorpresas.
- Windows `.dll`: importa solo DLLs del sistema: `KERNEL32`, `ADVAPI32`, `bcrypt`, `CRYPT32`, `IPHLPAPI`, `MSWSOCK`, `USER32` y `WS2_32`. No necesita runtime de MSVC. **Inspeccionado, no ejecutado.**

### B.2 Sesión real en Linux

`src/main/kotlin/spike/TorrentSpike.kt` (tarea `torrentSpike`):
1. extrae el nativo del jar a un temporal y fija `libtorrent4j.jni.path`;
2. arranca `SessionManager` con DHT y puertos aleatorios;
3. agrega el magnet de **Sintel** (WebTorrent/Blender, CC-BY) y espera los metadatos;
4. lista los archivos y elige el primer video;
5. prioriza solo ese archivo y, dentro de él, solo las piezas del primer tramo de 20 MiB (`prioritizePieces`, lo demás en `IGNORE`); además activa `SEQUENTIAL_DOWNLOAD` y `setPieceDeadline` en las 4 primeras piezas;
6. corta al llegar a 20 MiB de payload, lee los primeros 32 bytes del `.mp4` del disco, borra el torrent con `DELETE_FILES`, para la sesión y borra el directorio temporal.

Salida (sin recortes de las líneas del spike):

```
[torrent +   0,1s] nativo extraído: lib/x86_64/libtorrent4j.so -> /tmp/spike-torrent-2572105527503044486/native/libtorrent4j.so (18137224 bytes)
[torrent +   0,1s] sesión arrancada: running=true listen=0.0.0.0:0,[::]:0 libtorrent=2.1.0.0
[torrent +   0,1s] [Sintel] agregando magnet
[torrent +   2,1s] [Sintel] esperando metadatos: peers=4 dhtNodes=5 state=DOWNLOADING_METADATA
[torrent +   4,1s] [Sintel] esperando metadatos: peers=15 dhtNodes=102 state=DOWNLOADING
[torrent +   4,1s] [Sintel] metadatos: name=Sintel files=11 pieces=987 pieceLength=131072
[torrent +   4,1s]    #0 Sintel/Sintel.de.srt (1652 bytes)
[torrent +   4,1s]    #1 Sintel/Sintel.en.srt (1514 bytes)
[torrent +   4,1s]    #2 Sintel/Sintel.es.srt (1554 bytes)
[torrent +   4,1s]    #3 Sintel/Sintel.fr.srt (1618 bytes)
[torrent +   4,1s]    #4 Sintel/Sintel.it.srt (1546 bytes)
[torrent +   4,1s]    #5 Sintel/Sintel.mp4 (129241752 bytes)
[torrent +   4,1s]    #6 Sintel/Sintel.nl.srt (1537 bytes)
[torrent +   4,1s]    #7 Sintel/Sintel.pl.srt (1536 bytes)
[torrent +   4,1s]    #8 Sintel/Sintel.pt.srt (1551 bytes)
[torrent +   4,1s]    #9 Sintel/Sintel.ru.srt (2016 bytes)
[torrent +   4,1s]    #10 Sintel/poster.jpg (46115 bytes)
[torrent +   4,1s] [Sintel] video #5 Sintel/Sintel.mp4: piezas 0..159 (20971520 bytes como mucho)
[torrent +   4,1s] [Sintel] piezas 1/160 payload=909884 B rate=68 KiB/s peers=15 seq=false
[torrent +   7,1s] [Sintel] piezas 85/160 payload=14020187 B rate=2398 KiB/s peers=25 seq=true
[torrent +  10,1s] [Sintel] piezas 91/160 payload=15333197 B rate=1448 KiB/s peers=30 seq=true
[torrent +  13,1s] [Sintel] piezas 96/160 payload=17049492 B rate=1029 KiB/s peers=36 seq=true
[torrent +  16,2s] [Sintel] piezas 107/160 payload=18965482 B rate=842 KiB/s peers=40 seq=true
[torrent +  19,2s] [Sintel] piezas 123/160 payload=21166183 B rate=809 KiB/s peers=42 seq=true
[torrent +  19,2s] [Sintel] tope de 20 MB alcanzado
[torrent +  19,2s] [Sintel] descargado payload=21166183 B (tope 20971520) primera pieza=completa
[torrent +  19,2s] [Sintel] primeros 32 bytes de Sintel.mp4: 00 00 00 20 66 74 79 70 69 73 6f 6d 00 00 02 00 69 73 6f 6d 69 73 6f 32 61 76 63 31 6d 70 34 31
[torrent +  19,2s] [Sintel] formato detectado: MP4 (caja ftyp 'isom')
[torrent +  19,2s] RESULTADO: OK
[torrent +  21,8s] sesión detenida
[torrent +  21,8s] directorio temporal /tmp/spike-torrent-2572105527503044486 borrado: true
BUILD SUCCESSFUL in 28s
```

Observaciones:
- Hubo pares de sobra: metadatos en 4 s y hasta 42 peers. No hizo falta el magnet de reserva (Big Buck Bunny).
- El payload llegó a 21166183 B, unos 195 KB por encima de 20 MiB. Dos causas: el tope se controla cada 500 ms y `totalPayloadDownload` también cuenta bloques de piezas que todavía no terminaron (123 de 160 completas). Con la prioridad por piezas el techo real es de unas 160 piezas de 128 KiB más lo que esté en vuelo. Si un tope duro importa, hay que limitarlo con prioridades o con `downloadRateLimit`, no con un sondeo.
- `seq=false` en el primer sondeo: `setFlags` se aplica de forma asíncrona, y en el sondeo siguiente ya figuraba `true`.
- La pieza 0 incluye también los `.srt` anteriores (unos 8 KB), porque el video no está alineado a pieza; el `.mp4` igual empieza con la caja `ftyp` correcta.

### B.3 `apps/android-native/core/torrent/`: qué depende de Android

Ruta base: `apps/android-native/core/torrent/src/main/`.

**Dependen de Android** (servicio, notificaciones, almacenamiento, sensores del sistema, DI):
- `kotlin/app/tatakai/next/torrent/TorrentService.kt`: `LifecycleService` en primer plano, wake/wifi locks.
- `kotlin/app/tatakai/next/torrent/system/TorrentNotifications.kt`: canales y notificaciones, `R.string`.
- `kotlin/app/tatakai/next/torrent/system/StorageOps.kt`: MediaStore, `DocumentFile`, `StorageManager`, `Uri`.
- `kotlin/app/tatakai/next/torrent/system/DeviceMonitors.kt`: `ConnectivityManager`, `BatteryManager`, `PowerManager`, broadcasts.
- `kotlin/app/tatakai/next/torrent/TorrentModule.kt`: Koin con `androidContext`.
- `kotlin/app/tatakai/next/torrent/TorrentRepositoryImpl.kt`: la lógica es de motor, pero toma `Context` (`filesDir`, `noBackupFilesDir`, `getExternalFilesDir`, `contentResolver` + `Uri.parse`), crea `NetworkMonitor`/`BatteryMonitor`/`StorageOps`, arranca `TorrentService` y llama a `TorrentNotifications`. Para moverlo hay que inyectar esas cosas detrás de interfaces.
- `AndroidManifest.xml`, `res/values/strings.xml` y `res/values-es/strings.xml`.

**Motor puro o JVM puro** (no importan `android.*`, `androidx.*` ni `R`; solo libtorrent4j, `java.*`, kotlinx, okhttp o `:core:model`/`:core:release`):
- `kotlin/app/tatakai/next/torrent/engine/LibtorrentSession.kt`: libtorrent4j + `java.io.File`.
- `kotlin/app/tatakai/next/torrent/engine/TorrentMapping.kt`
- `kotlin/app/tatakai/next/torrent/policy/Policies.kt`, `policy/ParityPolicies.kt`, `policy/PieceMath.kt`
- `kotlin/app/tatakai/next/torrent/store/LabelStore.kt`, `store/SessionHistoryStore.kt` (archivos JSON vía `java.io.File`)
- `kotlin/app/tatakai/next/torrent/stream/LoopbackStreamServer.kt`, `stream/HttpRange.kt`, `stream/StreamProvider.kt`
- `kotlin/app/tatakai/next/torrent/debrid/DebridParsing.kt`, `debrid/DebridRepositoryImpl.kt` (okhttp, que ya es JVM)
- `kotlin/app/tatakai/next/torrent/PendingMagnetLink.kt`

Matiz: varias de estas clases y funciones son `internal` (`LibtorrentSession`, `TorrentMapping`, `stream/*`, `DebridParsing`). Si el motor va a un módulo distinto del que tenga `TorrentRepositoryImpl`, hay que hacerlas públicas o mover las dos cosas juntas. No compilé estos archivos en JVM; la clasificación sale de los imports y del uso de `Context`/`R`. Los tests del módulo (`src/test`: policy, store, stream, debrid) ya son tests JVM de esas clases.

---

## Sorpresas que cambian el plan

1. **libtorrent4j no extrae su nativo.** `org.libtorrent4j.swig.libtorrent_jni` hace `System.loadLibrary("torrent4j")` (en Windows, `"libtorrent4j"`) o `System.load(System.getProperty("libtorrent4j.jni.path"))`. Poner `libtorrent4j-linux` o `-windows` en el classpath no alcanza: la app de escritorio tiene que extraer `lib/x86_64/libtorrent4j.{so,dll}` del jar (como hace el spike en `extractNative`) o empaquetarlo al lado y fijar `java.library.path`/`libtorrent4j.jni.path` **antes** de tocar cualquier clase de libtorrent4j. En Android esto lo resuelven los AAR (`jniLibs`).
2. **El `.so` de Linux pide glibc ≥ 2.38.** No carga en Ubuntu 22.04 (2.35) ni en Debian 12 (2.36); sí en Ubuntu 24.04 (2.39), Fedora 39+ y Arch/CachyOS. El piso de Linux soportado queda definido por libtorrent4j, salvo que se compile libtorrent4j propio. (Que falla en esas distros es una inferencia a partir de las versiones de símbolos; no lo ejecuté en ellas.)
3. **No hay Linux aarch64 ni macOS x86_64 para libtorrent4j 2.1.0-39** (`lib/arm` es ARM de 32 bits; macOS solo arm64). No afecta a los objetivos Linux x64 y Windows x64.
4. quickjs-kt 1.0.18 cambia el mensaje de interrupción a `InternalError: stack overflow` (ver A.3). Si se sube de versión, hay que revisar `QuickJsSession.isInterruption`. Con 1.0.15 no hay cambios.
5. QuickJS real nunca se había probado en el host: los tests del módulo usan motores falsos (A.2). Este spike agrega la primera prueba real, y se puede reutilizar tal cual como test de conformidad en el futuro módulo JVM.

## Coordenadas para el catálogo (escritorio)

```toml
[versions]
quickjsKt = "1.0.15"          # sin cambio; 1.0.18 también funciona (ver A.3, mensaje de interrupción)
libtorrent4j = "2.1.0-39"     # sin cambio

[libraries]
# quickjs-kt: la coordenada KMP actual alcanza; en un target JVM/desktop resuelve a quickjs-kt-jvm, que trae los nativos linux_x64 y windows_x64.
quickjs-kt = { module = "io.github.dokar3:quickjs-kt", version.ref = "quickjsKt" }
# Equivalente explícito para un módulo JVM que no use Gradle module metadata:
quickjs-kt-jvm = { module = "io.github.dokar3:quickjs-kt-jvm", version.ref = "quickjsKt" }

# libtorrent4j: núcleo (ya está) + un jar de nativos por plataforma de escritorio (runtimeOnly / por distribución)
libtorrent4j = { module = "org.libtorrent4j:libtorrent4j", version.ref = "libtorrent4j" }
libtorrent4j-linux = { module = "org.libtorrent4j:libtorrent4j-linux", version.ref = "libtorrent4j" }      # lib/x86_64/libtorrent4j.so
libtorrent4j-windows = { module = "org.libtorrent4j:libtorrent4j-windows", version.ref = "libtorrent4j" }  # lib/x86_64/libtorrent4j.dll
```

Además, el módulo JVM necesita `jsoup`, `kotlinx-coroutines-core` y `kotlinx-serialization-json`, que ya están en el catálogo. El `macos` (`org.libtorrent4j:libtorrent4j-macos:2.1.0-39`, solo arm64) no hace falta para los objetivos actuales.
