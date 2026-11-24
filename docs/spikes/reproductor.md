APROBADO CON RESERVAS: libmpv incrustado vía MediaMP reprodujo 1080p60 con y sin Anime4K A+A sin perder ningún cuadro, con controles de Compose encima, pantalla completa y saltos estables. Pero hay una fuga de memoria nativa al abrir y cerrar el reproductor, el escalado de XWayland no sale nítido en monitores con otra escala y la decodificación por hardware necesita un arreglo.

# Spike S1: libmpv incrustado en Compose Desktop (Linux)

Fecha: 2026-11-22. Rama `spike-reproductor`. Proyecto en `spikes/reproductor/`, notas de avance en `spikes/reproductor/NOTAS.md` y datos crudos de cada corrida en `spikes/reproductor/resultados/`.

## Resumen

- **Funciona**: MediaMP 0.5.3 (`mediamp-mpv`) usado como dependencia, sin parches. En Linux, mpv dibuja con su render API (OpenGL) en un anillo de texturas compartido con el contexto GL de Skiko, sin pasar por la CPU. La barra de Compose se dibuja encima, semitransparente, sin trucos.
- **Dónde se midió**: casi todo corrió en `gamescope --backend headless` con la GPU real (RX 9070 XT). Así se pudo renderizar a 1920×1080 y a 3840×2160 sin mostrar nada en el escritorio. Además hubo una corrida corta visible en el monitor DP-2 de la sesión real de KWin (XWayland) para medir escala y pantalla completa.
- **GPU**: ningún error de amdgpu o drm en el journal durante las 18 corridas.
- **Reservas** (detalle en la sección 4):
  1. Fuga nativa de unos 7 a 11 MB por cada vez que se abre y cierra el reproductor, sin meseta.
  2. Compose ignora la escala de XWayland (`Xft.dpi=120`). En DP-2 (escala 1, con XWayland a 1.25) KWin reescala la ventana y el video no sale a resolución nativa.
  3. La decodificación por hardware no anda con la libva que trae MediaMP. Anda precargando la libva del sistema.
  4. La medición larga se hizo en gamescope. En KWin real solo hubo una corrida de unos 10 s.
  5. Windows no se probó.

## 1. Mediciones

Video local: 180 s de Big Buck Bunny 1080p60 H.264, copiado sin recodificar de la variante 1080p de `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`. El video es de libre uso. Siempre sin audio (`ao=null`, `mute=yes`).

Cómo se mide:

- Ventana de 60 s, contada desde que la reproducción pasa de 1 s.
- **Cuadros perdidos** = delta de `frame-drop-count` más `decoder-frame-drop-count`, dividido por los segundos reproducidos × `container-fps`.
- **CPU** = utime+stime del proceso Java (incluye mpv, que corre dentro del proceso) sobre el tiempo transcurrido, en % de un núcleo.
- **draws/s** = cuadros que Skia compuso de verdad (contador en el `SkikoRenderDelegate`).
- **GPU busy** = `/sys/class/drm/card1/device/gpu_busy_percent` muestreado cada 5 s. Incluye el escritorio del usuario.

| Prueba | Resultado | Corrida |
|---|---|---|
| 1080p sin shaders, 1920×1080 | **0 perdidos de 3612 (0 %)**. vo 0, dec 0, `vo-delayed` 0. `estimated-vf-fps` 60.0 en las 12 muestras. Compose 60.0 draws/s. CPU 31.9 % de un núcleo (decodificación por software). GPU busy ~16 % | r3 |
| 1080p con Anime4K A+A, 1920×1080 | **0 perdidos de 3613 (0 %)**. vf-fps 60.0. 60.0 draws/s. CPU 33.3 %. GPU busy ~24 % | r4 |
| 1080p con A+A escalando a 3840×2160 (gamescope headless a 4K, sin monitor 4K) | **0 perdidos de 3613 (0 %)**. vf-fps 60.0. 60.0 draws/s. CPU 35.2 %. GPU busy ~40 % | r6 |
| 1080p con A+A y hwdec `vaapi-copy` (precargando la libva del sistema) | 0 perdidos de 3612. 60.0 draws/s. CPU **15.0 %** | r17 |
| HLS remoto 1080p60 (mux) con `Referer` y `User-Agent` propios | 0 perdidos de 3600. 60.0 draws/s. CPU 34.4 % | r14 |
| `display-fps` | No medido: la propiedad no existe con `vo=libmpv`, porque mpv no tiene ventana ni pantalla. En su lugar se usan los draws/s de Compose, que dieron 60 en todas las corridas | |
| Shaders realmente activos | **Sí.** Mismo cuadro pausado (t=30 s, salto exacto), con la cadena A+A puesta, quitada y vuelta a poner en caliente por IPC: **17.62 % de los píxeles cambian más de 8 niveles** (diferencia media 4.85). Al volver a ponerla, la imagen sale idéntica bit a bit (diferencia 0.0). Ver `reproductor-anime4k.png`. Además, el log de mpv muestra compiladas las pasadas `conv2d_*` de Restore CNN a 1080p y también las de Upscale CNN (`conv2d_last_tf`) a 4K | r5, r4, r6 |
| Pantalla completa: entrar y salir 5 veces | **Estable.** 10 transiciones (ventana de 1280×720 ↔ 1920×1080). En cada una el video sigue visible (luma de la zona del video entre 129 y 237, nunca negro), avanza, y hay 0 perdidos. En KWin real, en DP-2: 4 transiciones (1024×576 ↔ 2400×1350), mismo resultado y sin tomar el foco | r7, r18 |
| Saltos: 10 de ±10 s y 3 largos | **Estables, sin cuelgues.** Tiempo hasta el evento `playback-restart`: **15 a 31 ms** en los relativos (caen en el keyframe más cercano) y **87, 151 y 245 ms** en los absolutos a 120, 20 y 150 s. Después de cada salto el video avanza y la imagen no es negra | r8 |
| Controles encima | **Sí.** La captura se toma desde la app con `SkiaLayer.screenshot()` y muestra la barra semitransparente de Compose (tiempo, cuadros perdidos, botón) sobre el video. Ver `reproductor-controles.jpg` | r2 |
| Nitidez y escalado | KWin tiene XWayland en modo "las apps escalan solas", con `Scale=1.25` y `Xft.dpi=120`. La app ve `density=1.0`, `contentScale=1.0` y AWT 1.0×. DP-2 (1920×1080 físico, escala 1) aparece en X11 como **2400×1350**. En pantalla completa, mpv dibuja a 2400×1350 y KWin lo reduce a 1920×1080: **el video no sale a resolución nativa en DP-2**. En DP-1 (escala 1.25, aparece como 2560×1440) saldría 1:1; eso se deduce de xrandr pero no se midió. En gamescope, sin escala, sí sale 1:1 (`osd` 1920×1080 = ventana) | r18 |
| Fuga de recursos: abrir y cerrar 10 veces | **Fuga nativa.** El heap de Java se mantiene (~9.5 MB), igual que los fds (86) y los hilos (24 de Java, 78 nativos). En cambio, la RSS sube de **449 a 594 MB en 10 ciclos** y de 452 a 690 MB en 22, sin meseta. Por `smaps`, todo el crecimiento es memoria anónima; los mapeos de librerías y de GPU no cambian. Con `MALLOC_ARENA_MAX=2` baja de ~11 a ~7.5 MB por ciclo, pero no desaparece | r9 a r12 |
| Cabecera HTTP propia | **Llega.** Servidor HLS local que registra las cabeceras: las 24 peticiones (m3u8 y segmentos .ts) traen `Referer: https://nijiyome.test/episodio/1` y `User-Agent: NijiyomeSpike/1.0`. MediaMP las pasa a mpv como `referrer`, `user-agent` y `http-header-fields` | r13 |
| Canal de control | IPC JSON de mpv (`input-ipc-server`) para reproducir, pausar, saltar, pantalla completa y leer propiedades, más un socket propio de la app para captura, abrir y cerrar, memoria, escala y salida. Todas las pruebas se manejaron así, sin teclado ni ratón | todas |

Contra el criterio de aprobado: menos de 1 % de cuadros perdidos en las dos primeras filas (dio 0 % y 0 %), shaders activos (sí), pantalla completa y saltos estables (sí), controles visibles encima (sí). Lo que lo deja **con reservas** es la sección 4.

Advertencias de método:

- gamescope headless entrega 60 Hz exactos y no tiene compositor real. Las tasas de pérdida en KWin con monitores de 180 Hz (DP-1) o 60 Hz (DP-2) pueden ser distintas. En KWin solo se midió un tramo de unos 10 s (0 perdidos).
- Los contadores de mpv no ven un cuadro que mpv dibujó en el anillo pero Compose no llegó a mostrar. Por eso se agrega el contador de draws de Skia, que se mantuvo en 60.0/s.

## 2. Camino recomendado

**MediaMP como dependencia** (Apache-2.0, `org.openani.mediamp:mediamp-mpv` + `mediamp-mpv-runtime-<os>`), con versión fijada. **No hace falta fork** para lo que se probó.

Por qué:

- Cumplió los cuatro requisitos sin tocar su código: render API con textura compartida, cabeceras HTTP, `glsl-shaders` y acceso a todo mpv por `MPVHandle.option/command/getProperty*`, además del IPC normal.
- Ya resuelve las tres plataformas: GL compartido en Linux, Metal/IOSurface en macOS y D3D11 a D3D12 en Windows.
- Un JNI propio sobre `mpv_render_context` tendría que rehacer lo mismo: anillo de texturas, hilo de render, protocolo de cambio de tamaño, interoperabilidad con Skiko e integración con el ciclo de vida de Compose. Eso es un módulo nativo por plataforma.

Costo de mantenerlo:

- MediaMP está antes de 1.0 (0.5.x; la API cambia entre versiones menores).
- Entra por reflexión a campos privados de Skiko (redrawer, contexto, dispositivo). Cada actualización de Compose o Skiko obliga a repetir una prueba de humo como `spikes/reproductor/scripts/corrida.sh`.
- Trae su propio libmpv y ffmpeg (mpv 0.41.0, FFmpeg 8.0.1, libplacebo 6.338.2, libva 1.20), no los del sistema. Las actualizaciones de mpv llegan cuando las publica MediaMP.

Un **fork parcial** se justificaría solo por dos cosas:

1. Para corregir la fuga (sección 4.1), si su origen está en MediaMP y arriba no lo arreglan.
2. Para empaquetar un runtime de Linux propio (por ejemplo, contra la libva o el libmpv del sistema) y que la decodificación por hardware funcione sin `LD_PRELOAD`.

En los dos casos es el runtime o el JNI, no la parte de Compose.

## 3. Windows (no probado)

Leído `mediamp-mpv/mpv-d3d11-rendering.md` (rama `main`, consultado 2026-11-22). Estado según ese documento: implementado en julio de 2026 y probado en humo por los autores. Riesgos:

1. **Depende de un PR de mpv sin fusionar** (#17764, render API D3D11), que se aplica como parche en el mpv 0.41 que compila MediaMP. Hasta que entre en una versión de mpv, el binario de Windows solo puede venir del runtime de MediaMP; no sirve un libmpv oficial.
2. **Reflexión sobre la estructura nativa `DirectXDevice` de Skiko.** Los desplazamientos de los campos están deducidos, no son ABI; se validan con `QueryInterface` en tiempo de ejecución. Si fallan, el video queda negro y el audio sigue. Hay que revalidar con cada versión de Skiko.
3. **Ruta de respaldo** si la app fuerza `SKIKO_RENDER_API=OPENGL`: lectura de cada cuadro a la CPU y subida de nuevo (GPU→CPU→GPU). Es más cara y no hay que usarla.
4. La sincronización espera en la CPU, en el hilo de render, a que termine cada cuadro. No bloquea la UI, pero suma latencia por cuadro.
5. La decodificación con `d3d11va` comparte dispositivo con el render. Por el diseño, debería salir mejor que en Linux.

Antes de comprometerse con Windows hace falta un spike equivalente allá.

## 4. Problemas encontrados

1. **Fuga nativa al abrir y cerrar el reproductor (no resuelta).** Son unos 7 a 11 MB por ciclo, sin meseta en 22 ciclos. No es el heap de Java, ni fds, ni hilos, ni memoria de GPU mapeada: es memoria anónima nativa. Limitar las arenas de glibc la reduce pero no la elimina, así que hay una fuga real y además retención del asignador. No se aisló si es de MediaMP (JNI o anillo) o de libmpv/ffmpeg.
   - Impacto: unos 100 MB por cada 10 episodios abiertos en una misma sesión.
   - Mitigación posible sin aislarla: reusar un solo reproductor y cambiar de medio con `setMediaData` en vez de cerrarlo. Eso no se midió.
2. **Escala en XWayland (no resuelta; es de Compose Desktop, no de MediaMP).** Compose o Skiko toman densidad 1.0 aunque `Xft.dpi=120`, así que la UI sale sin escalar. Además, como KWin usa una sola escala para XWayland (1.25), las ventanas en un monitor de escala 1 (DP-2) se ven reescaladas, no 1:1. Eso afecta el video y el texto.
   - Mitigaciones: leer `Xft.dpi` y fijar `Density` a mano, o que el usuario elija en KDE "escalado por el sistema" (borroso) o "las apps escalan solas" (el actual).
   - Esto es [SKIKO-28](https://youtrack.jetbrains.com/issue/SKIKO-28) (sin Wayland nativo).
3. **No hay decodificación por hardware con el runtime de MediaMP (resuelta con un parche externo).**
   - La libva 1.20 que viene en el runtime busca drivers en `/usr/lib/x86_64-linux-gnu/dri`, una ruta de Debian. Aun apuntándola a `/usr/lib/dri`, no puede cargar el `radeonsi_drv_video.so` de Mesa 26 (`no function __vaDriverInit_1_0`).
   - Con `LIBVA_DRIVERS_PATH=/usr/lib/dri` más `LD_PRELOAD` de la libva 1.24 del sistema anda `vaapi-copy`, y la CPU baja de 32-33 % a 15-18 %.
   - Además, como Skiko usa GLX, mpv avisa "VAAPI hwdec only works with OpenGL or Vulkan backends" para la interop sin copia. Lo máximo posible es `vaapi-copy`.
   - La decodificación por software de 1080p60 H.264 funcionó bien (~32 % de un núcleo). Para HEVC 10 bits o 4K habría que medir.
4. **`canvas.readPixels` falla sobre la superficie GPU (resuelto).** Para capturar se usa `SkiaLayer.screenshot()` en el hilo de AWT. Es lo que hereda el código del primer intento.
5. **`vo-passes` y `display-fps` no existen con `vo=libmpv` (rodeado).** La prueba de los shaders se hizo por diferencia de imagen, y la tasa de presentación se tomó de Skia.
6. **HLS local armado con `ffmpeg -c copy`.** Sus marcas de tiempo empiezan en ~84 s y se reproduce a ~0.7×. Es un problema del archivo de prueba, no del reproductor: el HLS remoto anduvo a 1.0×. Ese HLS local se usó solo para verificar las cabeceras.
7. **GPU y journal.** En las 18 corridas de este intento no apareció ninguna línea de amdgpu o drm (filtro `amdgpu|drm|gpu reset|timeout`). Se excluye el ruido de base `Unsupported screen format RA24`, que son 1707 líneas, todas del arranque de KDE a las 05:53. En el arranque anterior, el del congelamiento, sí hay:
   - 04:35:56: `[mmhub] page fault` (ring 173, pasid 15360).
   - 04:35:59: `ring vcn_unified_0 timeout` (el decodificador de video por hardware), con el reset del anillo hecho bien.
   - 05:29: `penguind` (otro programa, no el spike) colgado por el watchdog de systemd.

   No hay rastro de que el spike estuviera corriendo durante el congelamiento. El timeout de VCN es un antecedente a tener en cuenta con la decodificación por hardware; en este intento no se repitió.
8. **Otro trabajo pesado en paralelo.** `acotado.sh` rechazó dos corridas (código 75) porque otro trabajador estaba compilando. `corrida.sh` ahora espera el lock (de a 2 min, hasta 20 min).

## 5. Deuda técnica si se adopta

- Encontrar y corregir la fuga de abrir y cerrar (valgrind o heaptrack sobre un ciclo con libmpv solo, y después con MediaMP), o diseñar la app con un solo reproductor reutilizado.
- Densidad en Linux: leer `Xft.dpi` (o la escala de KDE) y aplicarla a Compose. Documentar la falta de nitidez en monitores con otra escala hasta que exista Compose nativo en Wayland.
- Runtime de Linux: decodificación por hardware sin `LD_PRELOAD`, ya sea con un runtime propio enlazado contra la libva del sistema o con un arreglo arriba en MediaMP. Sin interop sin copia mientras Skiko use GLX.
- Prueba de humo automática (abrir, reproducir 10 s, capturar, comparar luma y verificar 0 perdidos) en cada actualización de Compose, Skiko o MediaMP, por la reflexión.
- Medir en KWin real con DP-1 a 180 Hz durante 60 s (no hecho, para no ocupar el monitor principal) y con HEVC 10 bits.
- Sin verificar: shaders `//!COMPUTE` (ninguno de los del repo usa compute), HDR (sigue sin camino, igual que hoy) y generación de cuadros con lsfg-vk (sigue siendo mpv externo).

## 6. Versiones y cómo reproducir

### Versiones

- **Sistema**: CachyOS, kernel 7.3.5-2-cachyos, KDE Plasma (Wayland) con XWayland, Mesa 26.3.1, GPU AMD Radeon RX 9070 XT (radeonsi, gfx1201), gamescope 3.17.0.
- **Build**: JDK 21 (`/usr/lib/jvm/java-21-openjdk`), Gradle 9.7.1, Kotlin 2.4.21, Compose Multiplatform 1.12.2, Skiko 0.150.2.
- **MediaMP 0.5.3**: `mediamp-mpv`, `mediamp-mpv-runtime-linux-x64`. Trae: libmpv 2.6.0 (mpv v0.41.0), FFmpeg 8.0.1, libplacebo 6.338.2, libva 1.20.0.
- **Shaders**: Anime4K de `apps/android-native/core/player-mpv/src/main/assets/shaders/`. Cadena A+A: Clamp_Highlights, Restore_CNN_M, Upscale_CNN_x2_M, Restore_CNN_S, AutoDownscalePre_x2, Upscale_CNN_x2_S.

### Comandos

Todo pesado pasa por `scripts/acotado.sh` (tope de 12 GB, prioridad baja, un solo trabajo a la vez):

```sh
cd spikes/reproductor
bash "<raíz del repositorio>/scripts/acotado.sh" \
  bash gradlew classpathFile --no-daemon --max-workers=4      # compila y escribe build/cp.txt

export RUNS=/ruta/resultados SPIKE_URL=/ruta/bbb1080.mp4      # bbb1080.mp4: ver abajo
# corrida.sh <nombre> <escenario> [segundos]: gamescope headless + medir.py + journal + pgrep
bash scripts/corrida.sh r3 play60 90                           # 1080p sin shaders
SPIKE_SHADERS=aa bash scripts/corrida.sh r4 play60 90          # 1080p A+A
SPIKE_SHADERS=aa GS_W=3840 GS_H=2160 bash scripts/corrida.sh r6 play60 90   # A+A a 4K
SPIKE_SHADERS=aa bash scripts/corrida.sh r5 shaderdiff 60      # prueba de shaders por diferencia
SPIKE_W=1280 SPIKE_H=720 bash scripts/corrida.sh r7 fullscreen 80
bash scripts/corrida.sh r8 seeks 80
LEAK_N=22 bash scripts/corrida.sh r10 leak 90                  # MALLOC_ARENA_MAX=2 para r12
SPIKE_URL=https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8 \
  SPIKE_HEADERS="Referer=https://nijiyome.test/episodio/1;User-Agent=NijiyomeSpike/1.0" \
  bash scripts/corrida.sh r14 play60 90                        # HLS remoto con cabeceras
# Cabeceras contra servidor local (registra cada petición en un .jsonl):
python3 scripts/servidor_hls.py <dir_hls> 18731 headers.jsonl &
SPIKE_URL=http://127.0.0.1:18731/bbb.m3u8 SPIKE_HEADERS="Referer=...;User-Agent=..." bash scripts/corrida.sh r13 smoke 60
# hwdec con la libva del sistema:
LIBVA_DRIVERS_PATH=/usr/lib/dri SPIKE_PRELOAD=/usr/lib/libva.so.2:/usr/lib/libva-x11.so.2:/usr/lib/libva-drm.so.2 \
  SPIKE_SHADERS=aa bash scripts/corrida.sh r17 play60 90
# Visible en DP-2 (sesión real, sin foco), escala y pantalla completa:
RUNNER=run-visible.sh FS_N=2 bash scripts/corrida.sh r18 fullscreen 60
```

Video de prueba (copia sin recodificar):

```sh
ffmpeg -i https://test-streams.mux.dev/x36xhzz/url_8/193039199_mp4_h264_aac_fhd_7.m3u8 \
  -t 180 -c copy -bsf:a aac_adtstoasc bbb1080.mp4
```

Control manual de una app ya corriendo:

- `python3 scripts/ctl.py app info|capture <png>|luma|rss|gc|open|close|quit`
- `python3 scripts/ctl.py mpv '["set_property","fullscreen",true]'`

La app se cierra sola a los `SPIKE_MAX_SECONDS` (90 por defecto), y `corrida.sh` además la corta con `timeout`, revisa el journal del kernel y verifica con `pgrep` que no quede ningún proceso.

## 7. Anexo v1.1 (F2): qué se resolvió de las reservas

Fecha: 2026-11-23. Rama `v11-f2-reproductor`. Todas las corridas con video fueron en `gamescope --backend headless` (RX 9070 XT, `ENABLE_GAMESCOPE_WSI=0`, clips de prueba sin audio, `ao=null`), de a una por `scripts/acotado.sh`. El journal del kernel no mostró líneas de amdgpu / drm en ninguna.

### 7.1 Decodificación por hardware (reserva 3): resuelta

La app carga la libva del sistema (`libva.so.2`, `libva-drm.so.2`, `libva-x11.so.2`) con `System.load` antes de que MediaMP cargue libmpv. El enlazador dinámico reutiliza una biblioteca ya cargada con el mismo soname, así que libmpv y FFmpeg usan esa copia: es lo mismo que el `LD_PRELOAD` del spike, sin tocar el entorno. Solo se usa si están las tres y su VA-API es al menos la de la copia de MediaMP (1.20, leída del runtime); si no, queda la de MediaMP como antes. `NIJIYOME_SYSTEM_LIBVA=0` lo apaga. Ajustes › Reproductor › Decodificador dice qué libva se cargó, qué decodifica VAAPI (sondeo con `vaQueryConfigProfiles`) y el decodificador del último video (`hwdec-current`).

1080p60, 60 s, ventana de 1920×1080 (`MpvSurfaceSmoke play`):

| Clip | libva | Decodificador | CPU (% de un núcleo) | Perdidos |
|---|---|---|---|---|
| H.264 8 Mb/s | sistema 1.24 | `vaapi-copy` | **19,3 %** | 0 de 3602 |
| H.264 8 Mb/s | MediaMP 1.20 | `no` (software) | 31,2 % | 0 de 3602 |
| HEVC Main10 | sistema 1.24 | `vaapi-copy` | **22,8 %** | 0 de 3602 |
| HEVC Main10 | MediaMP 1.20 | `no` (software) | 40,4 % | 0 de 3602 |

Sigue siendo `vaapi-copy` (Skiko usa GLX; sin interop sin copia).

### 7.2 Escala de XWayland (reserva 2): resuelta en la app, el caso del compositor queda documentado

La app lee la escala del escritorio (XSETTINGS `Xft/DPI`, si no `xrdb -query` → `Xft.dpi`; `NIJIYOME_SCALE` la reemplaza) y multiplica la densidad de Compose por lo que AWT no escala (OpenJDK en X11 solo sigue un `GDK_SCALE` entero). La ventana arranca con el tamaño escalado. El video no depende de eso: mpv dibuja en una textura del tamaño del área del video en píxeles reales.

Con `Xft.dpi: 120` en el X de gamescope: antes (equivalente a `NIJIYOME_SCALE=1`) la interfaz salía al 100 % y la superficie medía 1270×770; después sale al 125 % (ventana de 1600×1000) y la superficie 1590×970. En las dos, el OSD de mpv mide lo mismo que la superficie (render 1:1 con la ventana).

Lo que la app no puede cambiar: KWin le da a XWayland una sola escala. En un monitor con otra escala propia (DP-2 al 100 % con XWayland al 125 %), KWin reescala toda la ventana X11, video incluido. Se arregla del lado del compositor (todos los monitores con la misma escala y "Las aplicaciones X11 escalan solas") o con un Skiko nativo de Wayland.

### 7.3 "Fuga" nativa (reserva 1): era retención de glibc, mitigada

- libmpv sola (programa en C con el runtime de MediaMP): con `vo=null`, abrir el mismo archivo 15 veces en una instancia o crear y destruir 15 instancias deja la RSS plana (~62 MB en ciclos; el uso real del heap principal crece ~5 KB por ciclo). Con la render API de OpenGL (EGL sin superficie), crece ~0,5 MB por archivo y se aplana después de unas 10 cargas. `malloc_trim(0)` devuelve 50 a 65 MB en los dos casos.
- La app (`MpvSurfaceSmoke`): con `malloc_trim(0)` después de cada carga la RSS queda plana en 30 recargas, y NMT muestra que la JVM solo crece por el JIT (Code +4,4 MB, Metaspace +1,6 MB, una vez).

Conclusión: no hay una fuga que crezca sin límite; glibc retiene memoria liberada de muchos hilos (muchas arenas). La app llama a `malloc_trim(0)` en un hilo propio 2 s después de cada archivo cargado y de cada reproductor cerrado (solo Linux con glibc; `NIJIYOME_MALLOC_TRIM=0` lo apaga).

| Escenario (clip 720p, decodificación por software) | Antes | Después |
|---|---|---|
| Recargar en una sesión (próximo episodio) | 387 → 440 MB en 15 cargas (~3,8 MB por carga) | 382 → 385 MB en 30 cargas (oscila entre 376 y 403) |
| Abrir y cerrar el reproductor | 389 → 488 MB de la 2.ª a la 15.ª (~7,7 MB por ciclo) | 291 → 294 MB en 20 ciclos (entre 277 y 294) |

Con VAAPI, 30 recargas quedan entre 318 y 341 MB.

### 7.4 Escalados de Tatakai Next que faltaban (player-13, en parte)

NVScaler (NVIDIA Image Scaling 1.0.2, MIT) y las redes Anime4K VL (MIT), copiados de mpv-anime-build v5.6, van solo con el reproductor de escritorio (`core/player-mpv/src/desktopMain/resources`). En escritorio, Alta calidad usa VL/M como Tatakai Next, y NVScaler es un preset más. Android no los tiene ni los ofrece. NVScaler usa `//!COMPUTE`, y el renderer OpenGL de libmpv lo corre. Con un clip 720p en 1920×1080, en 20 s: sin shaders, NVScaler y Anime4K A en Alta (VL) dan 0 perdidos de 480. El mismo cuadro pausado cambia en el 3,9 % de los píxeles con NVScaler y en el 5,0 % con A/VL. `NIJIYOME_DESKTOP_SHADERS=0` vuelve a lo anterior (L/M y sin NVScaler).

### 7.5 Lo que sigue abierto

- Windows sin probar (D3D11 de MediaMP, `d3d11va`, separador `;` de shaders, mpv externo).
- SMTC (controles multimedia de Windows): diseño y marca en `feature/player/.../mpris/Mpris.kt`. Necesita código nativo (WinRT).
- Medición larga en KWin real (DP-1 a 180 Hz) sin hacer, para no ocupar el monitor del usuario.
