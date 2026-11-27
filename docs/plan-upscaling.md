# Plan: escalado de imágenes por GPU (v1.3.3)

Pedido del dueño: escalar internamente las imágenes (pósters, banners, miniaturas), con una cola, usando Anime4K o Real-ESRGAN anime, **en la GPU y nunca en la CPU**, y en la misma GPU que usan el reproductor y la generación de cuadros.

Este documento es solo el plan. No se compiló nada. Las rutas son relativas a la raíz del repositorio.

---

## 1. Resumen

- **Motor principal:** Real-ESRGAN `realesr-animevideov3` (x2, x3, x4) sobre **ncnn + Vulkan**, como biblioteca nativa propia cargada por JNI dentro del proceso, igual que `native/desktop-framegen`. Funciona en Linux, Windows y Android. Cada modelo pesa unos 1,25 MB y se incluye en la app.
- **Motor alternativo (fase 2, opcional):** Anime4K con los mismos `.glsl` que ya trae el reproductor, ejecutado por un ejecutor Vulkan de cómputo propio con SPIR-V generado al compilar. No se usa la instancia de libmpv.
- **GPU:** se elige con la misma política que la generación de cuadros. Esa política sale de `fg_desktop_engine.cpp` y pasa a una función compartida. Si no hay una GPU Vulkan real, la función se apaga. Nunca se usa la CPU.
- **Convivencia con el reproductor:** la cola se pausa mientras hay una pantalla de reproductor abierta, la generación de cuadros activa o Link procesando. Corre un trabajo a la vez, con prioridad baja y un tope de VRAM.
- **UI:** se muestra el original al instante y se reemplaza por la versión escalada cuando está lista. Hay caché propia en disco con tope y botón para borrarla.
- **Builds:** **2 builds completos locales** (escritorio Linux y APK Android), más CI para Windows. El tope es 3 si hay correcciones.

Hay que saber esto antes de empezar: **la mayoría de las tarjetas no van a necesitar escalado.** AniList `extraLarge` mide unos 460×650 px. Una tarjeta de 120 dp a densidad 2,0 pide 240 px de ancho, así que el original ya alcanza. El beneficio real está en el póster grande de Detalles, los fondos y el héroe de Inicio (cuando la fuente es la portada o el banner), las miniaturas de episodio a ancho completo, las imágenes `large` (230×325) de listas, personajes y social, y las pantallas HiDPI o 4K. Esto también limita cuánto trabajo recibe la cola.

---

## 2. Lo que hay hoy en el código

### 2.1 Reproductor y GPU

| Plataforma | Cómo renderiza | Elección de GPU |
|---|---|---|
| Android | libmpv `dev.jdtech.mpv:libmpv` 1.1.0, **solo OpenGL ES**: `vo=gpu`, `gpu-context=android`, `opengl-es=yes`, `hwdec=mediacodec-copy,…` (`core/player-mpv/src/androidMain/.../MpvEngine.kt:130-134`). Sin Vulkan (`THIRD_PARTY_NOTICES.md:178`). | Ninguna. El teléfono tiene una sola GPU. |
| Escritorio embebido | MediaMP 0.5.3 (JNI) con `vo=libmpv` y texturas de Skia: GL en Linux, D3D11 en Windows (`DesktopMpvEngine.kt:33,52-56`). No define `gpu-api` ni `vulkan-device`. Existe un gancho `extraOptions` (`DesktopMpvEngine.kt:44`) que no se usa. | Ninguna. Usa la GPU del contexto GL o D3D de Skia. |
| Escritorio con mpv externo | `--vo=gpu-next`. Con generación de cuadros agrega `--gpu-api=vulkan` y la capa lsfg-vk (`external/ExternalMpvLaunch.kt:68,93`). | Ninguna. |

- **Anime4K ya existe para video.** Hay presets A, A+A, B, C, B+B y C+A, además de FSR, CAS y NVScaler (`core/model/.../PlayerQuality.kt:16-46`). La cadena de shaders se arma en `core/player-mpv/src/commonMain/.../quality/ShaderCatalog.kt:49-72`. Los `.glsl` están en `core/player-mpv/src/androidMain/assets/shaders/` (21 archivos), y escritorio agrega VL y NVScaler en `src/desktopMain/resources/.../shaders/`. La licencia es MIT (`THIRD_PARTY_NOTICES.md:146`).
- **Generación de cuadros en escritorio** (`native/desktop-framegen`):
  - La GPU se elige en `fg_desktop_engine.cpp:221-281`: primero la discreta, después la integrada, después la virtual. Ante un empate gana la de mayor heap local, y se ignoran los dispositivos CPU.
  - La GPU se identifica con `vendorID<<32 | deviceID` (`:277`) y lsfg-vk la busca así (`third_party/lsfg-vk/src/core/device.cpp:73-110`). Con este método, dos GPU idénticas no se distinguen.
  - **No hay ningún ajuste para elegir GPU**, ni por id, UUID ni nombre.
  - Se carga con `System.load` sobre una lista de rutas candidatas (`DesktopFrameGenNative.kt:58-131`).
  - Se compila con CMake por fuera de Gradle (`packaging/linux/build-desktop-framegen.sh`, `packaging/windows/build-windows.ps1:37-64`). Gradle solo copia el binario (`desktopApp/build.gradle.kts:142-156`).
- **Generación de cuadros en Android** (`core/framegen`): NDK 28.2 y CMake 3.22.1, compilado solo para arm64-v8a y x86_64, unos 1,2 MB por ABI (`core/framegen/src/main/cpp/CMakeLists.txt:8,33,37`). Toma el primer dispositivo con Vulkan 1.1 o más (`fg_device.cpp:209-227`).
- **Riesgo que ya existe:** nadie verifica que la GPU Vulkan elegida sea la misma que renderiza el video en GL o D3D. En un portátil con dos GPU pueden no coincidir. La PC del dueño tiene una RX 9070 XT y la iGPU del 7600X, y `vulkaninfo` muestra las dos.

### 2.2 Imágenes (Coil 3.6.3)

- **Escritorio:** el único `ImageLoader` explícito está en `desktopApp/.../Main.kt:131-135`. Solo define el fetcher de OkHttp. Su caché de disco por defecto queda en la carpeta temporal del sistema (`/tmp/coil3_disk_cache`) y no se cuenta en el tamaño de caché de `Main.kt:232`.
- **Android:** **no hay un `ImageLoader` propio.** Usa el singleton por defecto de Coil.
- No hay interceptores, fetchers, decoders ni keyers propios. La única transformación es `CropBordersTransformation`, del lector de manga (`feature/manga/.../ReaderImages.kt:44`).
- `MediaCover` (`core/ui/.../media/MediaCover.kt:37-66`) es un `AsyncImage(url, Crop, fillMaxSize)` con `aspectRatio` de 13:18. Cubre casi todas las tarjetas.
- Varias imágenes anchas llaman a `AsyncImage` directamente:
  - el héroe de Inicio (`HomeSpotlight.kt:271`, de 260 a 500 dp de alto);
  - los fondos de Detalles (`DetailsEditorial.kt:56`, 330 dp; `FichaParts.kt:215`; `FichaStructures.kt:316`);
  - la miniatura de episodio (`DetailsEpisodes.kt:83`, en 16:9, de 112 dp a ancho completo);
  - el tráiler (`DetailsSections.kt:534`);
  - la tarjeta de siguiente episodio (`EndScreen.kt:334`);
  - el banner social (`SocialScreens.kt:405`).
- **De dónde salen las URL:**
  - AniList: `coverImage.extraLarge`, o `large` si no hay (`core/metadata/.../Mappers.kt:80-87`), y `bannerImage` de 1900×400 (`Media.kt:154-156`). En listas y social se usa `large` (`ListMapping.kt:320`, `AniListSocialApi.kt:93`).
  - Kitsu: `posterImage.large`.
  - MAL y Jikan: `large_image_url`.
  - ani.zip (TVDB): fanart de 1920×1080, banner, póster, clearlogo y las imágenes de episodio (`Mappers.kt:126-151`).
  - Extensiones: URL libres.
  - No hay TMDB.
- **Desenfoque:** el contenido adulto en modo BLUR (`DetailsEditorial.kt:64`) y el fondo de `EndScreen.kt:206` se desenfocan. No tiene sentido escalarlos.

### 2.3 Ajustes, colas y estado global

- **`AppSettings`** está en `core/model/.../Settings.kt:8-128` y se guarda con DataStore en `core/data/.../SettingsRepositoryImpl.kt`. Los objetos anidados se guardan como JSON, igual que `frame_gen_json` (`:134`). Escritorio tiene sus propios valores por defecto en `DESKTOP_DEFAULT_SETTINGS` (`DataModule.desktop.kt:168`).
- **Pantalla de Mejoras:** `EnhancementsCategory` (`feature/settings/.../SettingsSections.kt:236`) ya tiene el grupo `rd_s10_grp_upscale` (escalado de video) y la ranura `FrameGenSettingsSlot` (`:253-255`).
- **Buscador (Ctrl+K / Ctrl+P):** `SettingsSearchIndex.kt`, con entradas `e(id, plataforma, …)` (`:114`). El id tiene que coincidir con un `SettingAnchor`.
- **Textos:** van en `values/` y `values-es/`. Español es solo `values-es`, sin `-rMX` ni `-b+es+419`.
- **Caché de imágenes en Ajustes:** `ImageCache` y `CoilImageCache` (`feature/settings/.../ImageCache.kt:14-35`), `StorageInfo` y `clearCache()` (`SettingsViewModel.kt:286-348`), y la pantalla de Datos (`SettingsSections.kt:697-784`). `ExtraStorage` (`ExtraStorage.kt:9-21`) permite agregar carpetas medidas.
- **Colas:** no hay una cola genérica en memoria. El mejor modelo es el de `DesktopDownloadRepository.kt:95-223`: un `pump()`, un mapa `running` de `Job` y `cancelAndJoin`, aunque sin la parte persistente. Otro patrón de prefetch con cancelación está en `DesktopNarrator.kt:278-299`.
- **Señales que ya existen para pausar la cola:**
  - `NowPlaying` (`core/model/.../NowPlaying.kt:24-37`): solo la escribe escritorio.
  - `ActivePlayback` (`core/player/src/androidMain/.../PlaybackService.kt:33-47`).
  - `FgActiveFlag.active` en Android (`core/framegen/.../FgActiveFlag.kt:23-30`).
  - `RemoteProcessingState.active` (`core/player/src/commonMain/.../RemoteProcessingState.kt:13-20`).
  - `AppWindow.playerScreens` y `PlayerWindowOwner.active`: son contadores, no flows.
  - En escritorio **no hay una señal global de generación de cuadros activa**. Solo existe el estado de cada sesión en `DesktopFrameGenController.kt:34-56`.
- **Energía (solo Android):** batería en `MpvPlayerSession.kt:139-158`, `ThermalWatcher` en `core/player-mpv/src/androidMain/.../quality/ThermalWatcher.kt:23` y ahorro de batería en `FgCapabilities.kt:89`. Escritorio no detecta batería ni temperatura.
- **"Modo rendimiento"** solo existe como la variante LSFG 3.1P (`FgConfig.kt:34`). No hay un modo de rendimiento global.

---

## 3. Opciones evaluadas

| Opción | Veredicto | Por qué |
|---|---|---|
| **Real-ESRGAN `realesr-animevideov3` con ncnn y Vulkan, como biblioteca JNI propia** | **Principal** | Las tres plataformas usan Vulkan. El modelo es chico (unos 1,25 MB por escala en `.bin`, unos 3 KB en `.param`) ([upscayl/models](https://github.com/upscayl/upscayl/tree/main/models), [video2x/models](https://github.com/k4yt3x/video2x/tree/master/models/realesrgan)). Licencias permisivas: el código de Real-ESRGAN es BSD-3 ([LICENSE](https://github.com/xinntao/Real-ESRGAN/blob/master/LICENSE)), realesrgan-ncnn-vulkan es MIT ([LICENSE](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan/blob/master/LICENSE)) y ncnn es BSD-3 ([LICENSE](https://github.com/Tencent/ncnn/blob/master/LICENSE.txt)). ncnn expone `get_gpu_count`, `get_gpu_info(i).physical_device()`, `vendor_id`, `device_id`, `type()` y `get_heap_budget()` ([gpu.h](https://github.com/Tencent/ncnn/blob/master/src/gpu.h)), así que se puede elegir el mismo dispositivo y descartar los de CPU (`type()==3`). Tiene compilaciones oficiales para Android con Vulkan ([release 20261020](https://github.com/Tencent/ncnn/releases/tag/20261020)). |
| Real-ESRGAN con la CLI `realesrgan-ncnn-vulkan` en un proceso aparte | Descartada | Sin cambios desde 2022 (v0.2.0) ([releases](https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan/releases)). Arrancar un proceso por imagen le suma un segundo o más a cada una (ver la tabla de waifu2x-ncnn-vulkan, [velocidad](https://github.com/nihui/waifu2x-ncnn-vulkan#speed-comparison-with-waifu2x-caffe-cui)), y en Android lanzar ejecutables es frágil. Igual sirve como referencia: el código del ciclo de mosaicos sale de su `main.cpp` (MIT). |
| `RealESRGAN_x4plus_anime_6B` | Opcional, más adelante | Un solo modelo x4 de unos 8,9 MB ([mirror HF](https://huggingface.co/aigchacker/realesrgan-x4/tree/main)). Es más lento y no aporta x2. Si se agrega, conviene que se descargue. |
| Anime4K con la instancia de libmpv | Descartada | En Android mpv solo usa GLES. En escritorio, MediaMP usa `vo=libmpv` y una sola instancia por reproductor. `screenshot-to-file … scaled` necesita un VO activo ([manual](https://mpv.io/manual/master/#command-interface-screenshot-to-file)). Pelearía con la reproducción, abriría ventanas o contextos y heredaría las opciones del usuario. |
| Anime4K con libplacebo (`pl_mpv_user_shader_parse`) | Posible solo en escritorio | Funciona sin ventana sobre Vulkan ([custom.h](https://code.videolan.org/videolan/libplacebo/-/blob/master/src/include/libplacebo/shaders/custom.h)). Su licencia es LGPL-2.1: se puede enlazar dinámicamente, pero suma una dependencia grande con glslang o shaderc por plataforma, y para Android habría que compilarla con el NDK. |
| **Anime4K en un ejecutor Vulkan de cómputo propio** | **Alternativa (fase 2)** | Toma los mismos `.glsl` que ya usa el reproductor, los pasa a shaders de cómputo con un script al compilar, genera SPIR-V con `glslc` y los ejecuta con volk, igual que desktop-framegen. Se puede usar como referencia el port a WebGPU, que es MIT ([Anime4K-WebGPU](https://github.com/Anime4KWebBoost/Anime4K-WebGPU)). La licencia de Anime4K es MIT y está en v4.0.1 ([repo](https://github.com/bloc97/Anime4K)). Sirve de plan B si ncnn falla en algún driver de Adreno. |
| Anime4KCPP | Descartada | Pasó a GPL-3.0 en las versiones nuevas y solo usa OpenCL o CUDA, sin Vulkan ([repo](https://github.com/TianZerL/Anime4KCPP)). |
| Filtro `libplacebo` de FFmpeg | Descartada | Habría que distribuir otro FFmpeg con Vulkan solo para esto ([docs](https://ffmpeg.org/ffmpeg-filters.html#libplacebo)). |
| NNAPI, o LiteRT con delegado de GPU (Android) | Descartada | NNAPI está obsoleta desde Android 15 ([guía](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide)). LiteRT corre sobre OpenCL o GL y no Vulkan, obliga a convertir el modelo y solo existe en Android. |

**Velocidad:** no encontré mediciones publicadas de ncnn con animevideov3 para imágenes sueltas. Real-ESRGAN solo publica cifras de PyTorch en una V100, donde 480p corre a unos 66 fps ([anime_comparisons.md](https://github.com/xinntao/Real-ESRGAN/blob/master/docs/anime_comparisons.md)). Mi estimación, que hay que medir, es menos de 100 ms por póster de 460×650 a x2 en la RX 9070 XT y de 0,5 a 2 s en un teléfono de gama media. El paso 1 del plan incluye la medición. RealSR-NCNN-Android avisa de problemas con algunos modelos en Adreno 840 ([releases](https://github.com/tumuyan/RealSR-NCNN-Android/releases)).

**VRAM:** con mosaico (tile) de 100 se usan unos 200 MB y con 200 unos 640 MB (tabla de waifu2x-ncnn). realesrgan-ncnn-vulkan elige el mosaico según el presupuesto del heap. Acá se fija un tope propio (sección 4.6).

---

## 4. Arquitectura

### 4.1 Módulos

- **`native/upscale/`**: un proyecto CMake nuevo en C++20. Incluye ncnn como submódulo, fijado a la versión `20261020`, compilado con `NCNN_VULKAN=ON` y sin las capas que no se usan, para que pese menos. Produce `libnijiyome_upscale.so`, `nijiyome_upscale.dll` y `libnijiyome_upscale.so` para Android (arm64-v8a y x86_64). JNI mínimo:
  - `nProbe(prefKey) → json`: devuelve la GPU, el tipo, el heap, si hay fp16 y un error si corresponde.
  - `nInit(deviceIndex, modelDir)`.
  - `nUpscale(rgba, w, h, scale, tile, cancelToken) → rgba`.
  - `nRelease()`.
- **`native/common/gpu_pick.h`**: la política de `fg_desktop_engine.cpp:221-281`, extraída sin cambiar su comportamiento. La usan desktop-framegen y upscale. Devuelve `vendorID`, `deviceID` y `deviceUUID` real (`VkPhysicalDeviceIDProperties`). Upscale traduce eso al índice de ncnn comparando por UUID.
- **`:core:upscale`** (KMP, `nijiyome.kmp.library`, según `docs/patron-modulo-kmp.md`):
  - `commonMain`: modelo de ajustes, cola, planificador, caché, clave, compuertas (gates) y la interfaz `UpscaleEngine`.
  - `desktopMain`: carga JNI (copiando el patrón de `DesktopFrameGenNative.kt`) y lectura de la señal de energía.
  - `androidMain`: la implementación Android y la batería.
- **`:core:upscale-native`** (Android, `nijiyome.android.library` con CMake, igual que `:core:framegen`): solo compila la `.so` y carga la biblioteca. El plugin KMP de Android no soporta CMake (`patron-modulo-kmp.md` §4).
- **Koin:** `upscaleModule` va en `NijiyomeApplication.kt:70-90`, y `desktopUpscaleModule(cacheDir)` en `DesktopModules.kt:54-125`. Los consumidores lo piden con `getOrNull()`, así que si falta la función se oculta y no se rompe nada.

### 4.2 Cómo llega a la UI

1. **Un `ImageLoader` explícito en las dos plataformas.**
   - En Android, agregar `SingletonImageLoader.Factory` en `NijiyomeApplication` **con los mismos valores por defecto de hoy**: la misma carpeta de caché y los mismos tamaños, para no cambiar el comportamiento (regla del patrón, §1).
   - En escritorio, el `ImageLoader` actual ya es explícito. Se aprovecha para mover la caché de disco de `/tmp` a `paths.cacheDir/coil3`. Ese cambio pasa a ser una pregunta para el dueño (P5).
2. **Composable nuevo `UpscalableImage(url, kind, contentScale, …)`** en `core/ui/.../media/`. Reemplaza los `AsyncImage` de `MediaCover` y los de la lista de la sección 2.2.
   - Pinta el original como siempre.
   - Con `onSizeChanged` obtiene el tamaño en píxeles en pantalla, y con `onSuccess` obtiene el tamaño de la fuente (`result.image.width/height`).
   - Si la imagen califica (4.3), llama a `UpscaleQueue.request(key, url, kind, targetPx)` y observa `state(key)`.
   - Cuando la versión escalada está lista, cambia el `model` al archivo escalado. Usa `placeholderMemoryCacheKey` igual al original, así no hay parpadeo.
   - Si la versión escalada ya está en caché, la primera pintada ya sale escalada.
3. **Fuente de bytes:** se lee la entrada de la caché de disco de Coil (`diskCache.openSnapshot(url)`), sin volver a descargar. Si no está, se descarta el trabajo.
4. **Mientras la cola está pausada o la función apagada**, `UpscalableImage` se comporta igual que el `AsyncImage` de hoy.

### 4.3 Qué califica y con qué factor

- **Factor necesario:** `f = max(objetivoW / fuenteW, objetivoH / fuenteH)`, con `ContentScale.Crop`, que es el caso común. `objetivo` son los píxeles reales en pantalla, o sea dp × densidad.
- **Se omite si:**
  - `f ≤ 1,15`, porque el original ya alcanza;
  - la fuente es menor que 48 px de lado;
  - la imagen se muestra desenfocada (contenido adulto o fondo del `EndScreen`);
  - es un avatar de menos de 64 dp;
  - es un clearlogo (`ContentScale.Fit`, con transparencia);
  - es una página del lector de manga (fuera de alcance, ver P4).
- **Escala del modelo:** x2 si `f ≤ 2`, x3 si `f ≤ 3`, x4 si es mayor. Después se reduce con filtro bilineal al tamaño objetivo antes de guardar, así no se guarda más de lo que se muestra.
- **Topes de salida:** el lado largo no pasa de 2560 px en escritorio ni de 1440 px en Android. Si el factor necesario los supera, se baja la escala o se omite.
- **Tipos de imagen** (cada uno se puede activar aparte): portadas y pósters, fondos y banners, miniaturas de episodio, personajes y avatares (apagado por defecto), y portadas de manga y novelas.

### 4.4 Cola

- Vive en memoria y no persiste. Corre en `Dispatchers.Default.limitedParallelism(1)`, sobre un hilo nativo propio. **Hay un solo trabajo en GPU a la vez.**
- **Prioridad:** el pedido más reciente va primero (LIFO). Lo que acaba de entrar en composición es lo que el usuario está viendo. Los fondos de la pantalla actual van primero, porque son lo más visible.
- **Cancelación:**
  - `UpscalableImage` cuenta referencias por clave con `DisposableEffect`. Cuando la lista perezosa saca un elemento y su contador llega a 0, el trabajo pendiente se descarta.
  - Si el trabajo ya está corriendo, se marca el `cancelToken` y ncnn corta entre mosaicos.
- **Tamaño de la cola:** como máximo 64 pendientes. Al pasarse, se descartan los más viejos.
- **Duplicados:** se unifican por clave, al estilo de `running: Map<key, Job>` de `DesktopDownloadRepository`.
- **Estado observable:** `StateFlow<UpscaleStatus>` con pendientes, hechos en la sesión, GPU, motivo de pausa y último error. Lo usa la pantalla de Ajustes.

### 4.5 Caché de resultados

- Es una `DiskCache` de Coil separada, en `cacheDir/upscaled/`, con desalojo LRU incluido.
- **Tope por defecto:** 256 MB en escritorio y 128 MB en Android. Se puede configurar entre 64 MB y 2 GB.
- **Clave:** `sha256(url) + modelo + versión del modelo + escala + tamaño objetivo redondeado a 64 px`.
- **Formato:** WebP con pérdida, calidad 92. En Android se codifica con `Bitmap.compress`; en escritorio, con Skia `EncodedImageFormat.WEBP`.
- **Metadatos:** cada entrada guarda el `sha1` de los bytes de la fuente.
- **Invalidación cuando cambia la fuente:**
  - Cuando Coil trae la URL desde la red (`DataSource.NETWORK`), se compara el `sha1` nuevo con el guardado. Si es distinto, se borra la entrada y se vuelve a encolar.
  - Si se cambia el modelo o su versión, la clave cambia sola.
- **Borrado:** hay un botón propio en Ajustes. Además, `clearCache()` en Datos borra también esta caché, que se mide con `ExtraStorage` o con un segundo `ImageCache`.

### 4.6 GPU compartida y convivencia

- **Misma GPU:** se usa `gpu_pick.h`, es decir, la misma elección que la generación de cuadros. En Android hay una sola GPU.
  - En escritorio, además, se registra en el log el UUID del contexto GL de Skia (`GL_EXT_memory_object`, `GL_DEVICE_UUID_EXT`) en Linux, o el LUID del adaptador D3D11 en Windows, y se compara con el de Vulkan.
  - Si no coinciden, se prefiere el dispositivo Vulkan con ese UUID o LUID, que es la GPU que realmente pinta la app.
  - Esto también corrige el riesgo que ya tiene la generación de cuadros, pero ese cambio en framegen queda como decisión aparte (P2).
- **Sin GPU Vulkan real** (no hay dispositivos, solo hay llvmpipe o lavapipe, o `type()==3`, o falla `nInit`): la función queda **deshabilitada** y la pantalla dice por qué. **Nunca hay respaldo en la CPU.** Coil sigue mostrando los originales.
- **La cola se pausa si se cumple cualquiera de estas condiciones** (se combinan en un `StateFlow<PauseReason?>`):
  - Hay una pantalla de reproductor abierta: `NowPlaying.info != null` en escritorio, `PlayerWindowOwner.active > 0` o `ActivePlayback` en Android. Mientras se ve video no hay pósters en pantalla, así que no se pierde nada.
  - La generación de cuadros está activa: `FgActiveFlag` en Android y un `DesktopFrameGenActive` global nuevo (un `StateFlow` alimentado desde `onActiveChanged` en `DesktopMpvPlayerSession.kt:69-77`).
  - `RemoteProcessingState.active`, o el servidor Link de escritorio transcodificando con temple-lsfg. Esa señal hay que agregarla.
  - La ventana está minimizada o la app en segundo plano.
  - **En Android:** ahorro de batería activo; batería en 20 % o menos sin cargar (el mismo umbral que `MpvQualityController`); temperatura en `MODERATE` o más, usando `ThermalWatcher`; o el ajuste "Solo mientras carga" activado.
- **Presupuesto:**
  - Mosaico de 64 en Android y de 128 en escritorio, siempre que no supere el 25 % del heap que devuelve `get_heap_budget()`. Tope de 256 MB en escritorio y 96 MB en Android. El hilo nativo corre con prioridad baja.
  - Entre trabajos se hace una pausa de 50 ms en escritorio y 250 ms en Android, para no ocupar la GPU todo el tiempo.
  - El motor (`nRelease`) se libera después de 60 s sin trabajo, para devolver la VRAM.
- **Rendimiento:** no hay un modo de rendimiento global, y la variante 3.1P de LSFG corre mientras se reproduce, que es cuando la cola ya está en pausa. Para la propia función hay un selector de calidad: "Rápido" fuerza x2 con mosaico chico, y "Calidad" permite x3 y x4. Si más adelante aparece un modo rendimiento global, debería forzar "Rápido".

---

## 5. Ajustes

- **Modelo:** `AppSettings.imageUpscale: ImageUpscaleSettings`, guardado como JSON en la clave `image_upscale_json` (igual que `frame_gen_json`). Campos:
  - `enabled`
  - `engine` (`REALESRGAN_ANIME_V3`; `ANIME4K` en la fase 2)
  - `quality` (`FAST` o `QUALITY`)
  - `kinds: Set<ImageKind>`
  - `cacheMb`
  - `chargingOnly` (solo Android)
  - `sanitized()`
- **Ubicación:** en `EnhancementsCategory`, un `SettingsGroup` nuevo llamado "Imágenes" ("Images"), debajo de los grupos de video, con `SettingAnchor("images_upscale")`. Entrada en el buscador: `e("images_upscale", ALL, …, "upscale", "escalado", "esrgan", "anime4k", "póster", "miniaturas")`.
- **Controles:**
  - Interruptor "Escalar imágenes con la GPU".
  - Motor: Real-ESRGAN anime, y Anime4K deshabilitado con la nota "próximamente" hasta la fase 2.
  - Calidad: Rápido o Calidad.
  - Casillas por tipo de imagen.
  - "Solo mientras carga" (solo Android).
  - Tamaño de caché.
  - Botón "Borrar imágenes escaladas", con el tamaño ocupado.
- **Estado en vivo:** "GPU: AMD Radeon RX 9070 XT · 3 en cola · 41 escaladas", o "En pausa: reproduciendo" / "No disponible: no hay GPU Vulkan".
- **Textos:** van en `feature/settings/src/commonMain/composeResources/values/` y `values-es/`, en un archivo nuevo `strings_upscale.xml`, con español latinoamericano como el resto.

---

## 6. Impacto en compilación, tamaño y CI

- **ncnn desde el código fuente con Vulkan:** compila glslang y unos cientos de shaders. Es la parte más pesada. Hay que hacerlo una sola vez por plataforma y cachearlo en `build/ncnn-<ver>-<os>` y en el caché de CI.
- **Tamaños estimados (hay que medirlos):**
  - `libnijiyome_upscale` con ncnn estático y sin capas innecesarias: de 3 a 6 MB por SO o ABI. Como referencia, el zip oficial de Android con Vulkan pesa 40,7 MB y trae todas las ABI y todas las capas.
  - Modelos x2, x3 y x4: unos 3,8 MB en total. Van en `resources` en escritorio y en `assets` en Android; se copian a `filesDir/models/` la primera vez, igual que `ShaderInstaller`.
- **Escritorio:**
  - `packaging/linux/build-desktop-upscale.sh` nuevo, más un bloque en `build-windows.ps1`.
  - `stageAppResources` (`desktopApp/build.gradle.kts:142-156`) lee `NIJIYOME_DESKTOP_UPSCALE` o `build/desktop-upscale/`.
  - Agregar el archivo al AppImage (`build-appimage.sh`).
- **Android:** `:core:upscale-native` con `externalNativeBuild`, solo arm64-v8a y x86_64, igual que `:core:framegen`. La APK crece unos 8 MB por ABI, contando bibliotecas y modelos.
- **CI:**
  - El job nativo de `ci.yml:181-231` agrega `native/upscale` y su filtro de rutas (`:62-67`).
  - `release.yml:84-87` y los pasos de Windows (`:120-183`) llaman al script nuevo.
  - Hay que fijar la versión de ncnn y sumar sus licencias.
- **Avisos de terceros:** `THIRD_PARTY_NOTICES.md` suma ncnn (BSD-3, con sus dependencias), Real-ESRGAN y sus pesos (BSD-3), y el código adaptado de realesrgan-ncnn-vulkan (MIT).

---

## 7. Pasos de trabajo

| # | Paso | Depende de | En paralelo con |
|---|---|---|---|
| 1 | `native/common/gpu_pick.h` extraído de framegen, sin cambiar su comportamiento. `native/upscale/` con ncnn como submódulo, JNI, ciclo de mosaicos con cancelación y rechazo de CPU. Script de compilación para Linux y bloque para Windows. | — | 2, 3 |
| 2 | `:core:upscale` en común: `ImageUpscaleSettings` y su codec, clave, caché, cola LIFO con conteo de referencias, compuertas de pausa e interfaz del motor. Tests de lógica en `commonTest` (clasificación por tamaño, factor, orden, cancelación, invalidación). | — | 1, 3 |
| 3 | UI: `UpscalableImage` en `core/ui`, migración de `MediaCover` y de los `AsyncImage` de 2.2, grupo de Ajustes, entrada en el buscador, textos en inglés y español, y medición de la caché en Datos. | contrato del paso 2 (interfaz) | 1, 2 |
| 4 | Conexión con las plataformas: `ImageLoader` explícito en Android con los valores de hoy, carga JNI en escritorio, flow global `DesktopFrameGenActive` y señal de Link, módulos de Koin, y `:core:upscale-native` (NDK). | 1, 2 | — |
| 5 | Empaquetado y CI: `stageAppResources`, AppImage, `build-windows.ps1`, `ci.yml`, `release.yml`, `THIRD_PARTY_NOTICES.md` y READMEs. | 1 | 4 |
| **Build 1** | **Escritorio Linux:** nativo, `compileKotlinDesktop`, tests de `:core:upscale` y `:feature:settings`, y app de escritorio. Incluye la medición de ms por póster y de VRAM en la RX 9070 XT, sobre una copia del perfil real (`NIJIYOME_DATA_DIR`). | 1–5 | — |
| **Build 2** | **APK Android de depuración** (incluye el NDK). La prueba en el teléfono real con scrcpy queda para el final: medir tiempos, temperatura y Adreno o Mali. | Build 1 | — |
| — | CI compila Windows. Se revisa el resultado; si falla, se corrige y se reutiliza el mismo CI, sin build local. | Build 2 | — |
| 6 (fase 2) | Anime4K en el ejecutor Vulkan de cómputo: script de traducción a SPIR-V y backend `ANIME4K`. Necesita un build más. | v1.3.3 publicada | — |

- **Agentes:** pasos 1, 2 y 3 en paralelo (3 agentes); 4 y 5 en paralelo (2 agentes). Todos los builds van en secuencia dentro de `scripts/acotado.sh`, y el dueño confirma cada uno.
- **Builds:** **2 previstos y 3 como máximo** si el build 1 obliga a corregir algo antes del APK.

---

## 8. Riesgos y preguntas para el dueño

| # | Pregunta o riesgo | Mi recomendación |
|---|---|---|
| P1 | ¿Real-ESRGAN solo en v1.3.3, y Anime4K para imágenes en una versión siguiente? | Sí. Real-ESRGAN es el único camino que sirve igual en las tres plataformas sin usar libmpv. Anime4K queda en la fase 2 con el ejecutor propio. |
| P2 | Hoy la generación de cuadros elige la GPU "discreta primero" sin comprobar que sea la que pinta el video. ¿Se arregla también framegen con la comparación por UUID o LUID, o solo upscale? | Upscale en v1.3.3. framegen en un cambio aparte, después de ver en el log de v1.3.3 si de verdad hay diferencias. |
| P3 | ¿Agregar un ajuste manual de GPU (lista de dispositivos Vulkan) compartido por framegen y upscale? | Todavía no. Que sea automático y coincida con la GPU que pinta. Agregarlo solo si aparecen portátiles con dos GPU que lo necesiten. |
| P4 | ¿Escalar también las páginas del lector de manga? | No en esta versión. Son imágenes grandes, hay muchas por capítulo y ya tienen prefetch y recorte propios. Merece su propio plan. |
| P5 | La caché de Coil en escritorio vive en `/tmp` y no se mide. ¿La muevo a `cacheDir`? | Sí, porque la necesito para leer las fuentes, y así también aparece en Datos. Se pierde una vez la caché actual de imágenes. |
| P6 | ¿Activada por defecto? | Activada en escritorio. En Android, activada pero respetando las compuertas de energía; "Solo mientras carga" apagado. |
| P7 | ¿El modelo x4plus-anime-6B (8,9 MB) se descarga aparte? | No en v1.3.3. Más adelante, como descarga opcional desde un release propio con checksum. |
| R1 | ncnn con Vulkan puede fallar en algunos drivers de Adreno (aviso de RealSR-NCNN-Android). | Lista de GPU bloqueadas en el probe, igual que `FgPolicy.kt`. Si `nInit` o la primera imagen fallan, deshabilitar y avisar. Sin respaldo en la CPU. |
| R2 | La velocidad está estimada, no medida. | El build 1 mide. Si un póster pasa de 300 ms en escritorio o de 3 s en el teléfono, se fuerza "Rápido" y se suben los umbrales de calificación. |
| R3 | Cambiar el `ImageLoader` de Android puede alterar la caché o el comportamiento. | Copiar los valores por defecto de Coil 3.6.3 tal cual y comprobar en el teléfono que las imágenes y la caché se ven igual. |
| R4 | Reemplazar unos 15 `AsyncImage` toca muchas pantallas del rediseño. | `UpscalableImage` tiene la misma firma que `AsyncImage` y sin la función se comporta igual. El cambio es mecánico. |
| R5 | Las extensiones pueden servir imágenes con encabezados como Referer, o URL que cambian. | Solo se escalan las fuentes que ya están en la caché de disco de Coil. Si no hay entrada, no se escala. |

---

## Paso 5: hecho

No se compiló nada (ni Gradle, ni CMake, ni CI). Se validó la sintaxis: `bash -n` en los scripts, el parser de PowerShell en los `.ps1`, `yaml.safe_load` en los dos workflows y `node --test contracts/test/guard.test.mjs` (pasa).

### Escritorio

- **`desktopApp/build.gradle.kts`, `stageAppResources`:** lee `NIJIYOME_DESKTOP_UPSCALE` o, si no está, `build/desktop-upscale/` (también `Release/`), igual que framegen. Solo si hay biblioteca copia esto a `appResources/<linux-x64|windows-x64>/`:
  - `libnijiyome_upscale.so` o `nijiyome_upscale.dll`, en la raíz de los recursos, como la de framegen;
  - `upscale-models/`: los tres `.param` y **un solo `.bin`** (`realesr-animevideov3-x4.bin`). Es seguro porque `upscale_engine.cpp` usa el `-x4.bin` cuando falta el de la escala pedida (paso 1). Ahorra unos 2,5 MB;
  - `upscale-licenses/`: `ncnn-LICENSE.txt`, `glslang-LICENSE.txt` (los dos salen del submódulo, solo si está bajado), `Real-ESRGAN-LICENSE.txt` y `realesrgan-ncnn-vulkan-LICENSE.txt`.
- **Rutas en la app instalada:** `compose.application.resources.dir` es `lib/app/resources/` en Linux y `app\resources\` en Windows. El modelo se puede pasar directo a `nInit` (`<resources>/upscale-models`), porque ncnn solo lee con `fopen`. En el AppImage esa carpeta es de solo lectura, y alcanza.
- **`packaging/linux/build-appimage.sh`:** compila la biblioteca con `build-desktop-upscale.sh`, salvo que reciba `--upscale <lib>`, `--no-upscale` o `NIJIYOME_DESKTOP_UPSCALE`. Se la pasa a Gradle. Antes de empaquetar revisa que estén los cuatro archivos del modelo si la biblioteca está en la imagen; si no hay biblioteca, avisa y sigue.
- **`packaging/linux/install-tree.sh`:** si la imagen trae `upscale-licenses/`, las instala en `share/licenses/nijiyome/upscale/` (AppImage y paquete de Arch).
- **`packaging/windows/build-windows.ps1`:** el bloque del paso 1 ya está pegado, con un `-NoUpscale` nuevo. Pasa `NIJIYOME_DESKTOP_UPSCALE` a Gradle y revisa los modelos en la imagen, igual que en Linux.
- **`packaging/README.md`:** fila de desktop-upscale y la carpeta de recursos.
- `PKGBUILD` no se tocó. Toma la biblioteca de `build/desktop-upscale/` si ya está compilada, como pasa hoy con framegen.

### CI

- **`ci.yml`:**
  - Hay un filtro nuevo, `upscale`: `native/upscale/**`, `native/common/**`, `.gitmodules`, los dos scripts y el propio workflow. El filtro `android` lo incluye, porque la APK compila `native/upscale` dentro de `:core:upscale-native`.
  - `native/common/**` también entra en el filtro `lsfg`, así un cambio en `gpu_pick.h` vuelve a compilar framegen.
  - Hay un job nuevo, `desktop-upscale`, en Linux y Windows. Baja el submódulo de ncnn y solo su glslang, cachea `build/desktop-upscale` con la clave SO + commit de ncnn + hash de `CMakeLists.txt`, `src/**` y `native/common/**` (si cambia el código propio, `restore-keys` reutiliza ncnn ya compilado) y llama a los scripts del paso 1.
  - El job de Android baja el submódulo antes de Gradle, y su tope de tiempo pasa de 75 a 100 min.
- **`release.yml`:**
  - Linux: submódulo, caché, paso `desktop-upscale`, que deja `NIJIYOME_DESKTOP_UPSCALE` en `GITHUB_ENV`. Tope de 90 a 120 min.
  - Windows: submódulo y caché; `build-windows.ps1` compila la DLL. Tope de 120 a 150 min.
  - Android: submódulo. Tope de 75 a 100 min.
- `actions/cache` está fijado a v4.3.0 (`0057852b…`), como las otras acciones.
- No se usa `submodules: recursive`, porque bajaría también `pybind11`.

### Avisos de terceros y READMEs

- **`THIRD_PARTY_NOTICES.md`:**
  - Hay una fila en la sección 1 por el código adaptado de realesrgan-ncnn-vulkan.
  - Hay una sección nueva, "GPU image upscaling", con la tabla de ncnn, glslang, realesrgan-ncnn-vulkan y los pesos de Real-ESRGAN. La sección dice que la biblioteca es una obra combinada GPL-3.0, porque incluye `gpu_pick.h`.
  - Trae los textos completos de las licencias de ncnn, realesrgan-ncnn-vulkan y Real-ESRGAN, y el de glslang sin los textos estándar de Apache-2.0 y GPL-3.0. Así Android los muestra en Ajustes › Acerca de › Licencias sin tocar `:app` ni los lectores, que solo listan `OFL-*`. El texto completo de glslang va en `upscale-licenses/` en escritorio.
- **`README.md` y `README.es.md`:** hay una viñeta nueva de escalado de imágenes por GPU (Real-ESRGAN anime, Vulkan, se apaga sin GPU y nunca usa la CPU) y una fila de `native/upscale/` en la tabla de rutas. `docs/readme/` solo tiene capturas.

### Para el paso 4 (no lo toqué)

1. **Carga en escritorio:** las candidatas son `NIJIYOME_DESKTOP_UPSCALE`, `<resources.dir>/<lib>`, `<java.home>/../app/resources/<lib>`, y `build/desktop-upscale/[Release/]<lib>` y `desktopApp/build/generated/appResources/<os>/<lib>` desde `user.dir`. Los modelos se buscan en `<carpeta de la lib>/upscale-models/` y, en desarrollo, en `native/upscale/models/`.
2. **Android:** `:core:upscale-native` tiene que meter en `assets` (por ejemplo `assets/upscale-models/`) los tres `.param` y solo `realesr-animevideov3-x4.bin`. `sourceSets.main.assets.srcDir` no filtra, así que conviene una tarea `Copy` con `include`, o los cuatro archivos y nada más. Las licencias ya están en `THIRD_PARTY_NOTICES.md`.
3. El CMake de AGP compila ncnn y glslang por ABI y por variante, y su salida (`.cxx/`) no se cachea en CI. Fijar `-DCMAKE_BUILD_TYPE=Release` también en debug acorta el job de Android.

### Sin verificar

- No corrió ningún job de CI. Hay que ver en el primer CI de Windows si la caché de un build de CMake con MSVC se restaura bien.
- Si la imagen del runner cambia el compilador o CMake, la caché restaurada puede hacer fallar la configuración. En ese caso basta con borrar la caché `desktop-upscale-*` en GitHub.
- `--no-upscale` y `-NoUpscale` dejan la variable vacía, pero `stageAppResources` igual toma `build/desktop-upscale/` si ahí quedó una biblioteca. Con framegen pasa lo mismo.

---

## Paso 3: hecho

No se compiló nada. El código solo usa el contrato `core/upscale/.../UpscaleContract.kt`.

### `UpscalableImage`

- Está en `core/ui/src/commonMain/kotlin/app/nijiyome/ui/media/UpscalableImage.kt`. Recibe los mismos parámetros que los `AsyncImage` que reemplaza (`url`, `contentDescription`, `modifier`, `alignment`, `contentScale`, `alpha`, `onSuccess`, `onError`), más `kind` y `upscale` (en `false` para las imágenes difuminadas).
- `core/ui/build.gradle.kts` agrega `api(projects.core.upscale)`, así los módulos de funciones ven `UpscaleImageKind`.
- Define dos `CompositionLocal` que valen `null` por defecto:
  - `LocalUpscaleQueue: UpscaleQueue?`. Si es `null`, se pinta el mismo `AsyncImage` de siempre.
  - `LocalUpscaleSettings: StateFlow<ImageUpscaleSettings>?`. Es opcional. Si está, la función apagada o un tipo de imagen desactivado no llegan a la cola. Si falta, decide la cola, que responde `Skipped`.
- Cómo funciona:
  1. Mide el tamaño en pantalla con `onSizeChanged`, redondeado a 64 px.
  2. Pide `cached(...)` para la primera pintada.
  3. Cuando carga el original, toma el tamaño decodificado y la `MemoryCache.Key` de `onSuccess`.
  4. Llama a `request(...)` dentro de un `DisposableEffect`, que libera el ticket al salir de la composición o al cambiar de tamaño, y observa `state(key)`.
  5. Con `Done`, cambia el modelo por un `ImageRequest` del archivo (`okio.Path`) con `placeholderMemoryCacheKey` igual a la clave del original.
- Si el archivo escalado falla al cargar (por ejemplo, porque se borró de la caché), vuelve al original.
- Los avatares de menos de 64 dp nunca se piden.

### Lugares migrados

| Lugar | Tipo |
|---|---|
| `MediaCover` (todas las tarjetas, con el nuevo parámetro `kind`, por defecto `POSTER`) | `POSTER` |
| `EpisodeRow.kt` (miniatura 16:9) y `EndScreen.kt` `NextUpCard` | `EPISODE_THUMB` |
| `MaterialCard.kt` `MaterialBanner` | `BANNER` si hay banner, si no `POSTER` |
| `MediaCover` de personajes, actores de voz, staff, avatares de cuentas, riel, Tú, biblioteca, explorar y personajes de manga | `AVATAR` (apagado por defecto) |
| `HomeSpotlight.kt` (héroe de Inicio) | `BANNER` si viene del banner, si no `BACKDROP` |
| `DetailsEditorial.kt`, `FichaParts.kt` y `FichaStructures.kt` (fondos de Detalles) | `BACKDROP`, con `upscale = false` cuando está difuminado |
| `DetailsEpisodes.kt` (miniatura de episodio) y `DetailsSections.kt` (tráiler) | `EPISODE_THUMB` |
| `SocialScreens.kt` (banner del perfil) | `BANNER` |
| `SocialComponents.kt` `Avatar` | `AVATAR` |

Lugares que quedan con `AsyncImage` a propósito:

- el logo de título de `HomeSpotlight` (Fit, con transparencia);
- el arte de respaldo atenuado de `EpisodeThumb`;
- el fondo difuminado de `EndScreen`;
- las páginas y miniaturas del lector de manga;
- la búsqueda por imagen (archivo local y vistas previas de trace.moe).

### Ajustes

- Hay un grupo nuevo, "Imágenes" ("Images"), en `ImageUpscaleSection.kt`. Va debajo de Generación de cuadros, en Mejoras, con el ancla `images_upscale` y su entrada en `SettingsSearchIndex.kt`. Mejoras no tiene subpáginas, así que no hizo falta otra correspondencia.
- Controles:
  - interruptor;
  - calidad (Rápido, Equilibrado o Mejor);
  - chips de selección múltiple por tipo de imagen;
  - "Solo mientras carga", solo en Android (`upscaleOnPhone`, un `expect` nuevo);
  - tamaño de caché en chips de 64 MB a 2 GB, donde `null` es el valor por defecto de la plataforma: 128 MB en Android y 256 MB en escritorio;
  - "Borrar imágenes escaladas", con confirmación en la misma página;
  - línea de estado con la GPU, si está escalando o en espera, la cantidad en cola, el motivo de pausa y los ms de la última imagen.
- Sin `UpscaleSettingsStore` o sin `UpscaleQueue` en Koin, el grupo solo dice que la función no está disponible en esta versión.
- Datos: `StorageInfo.upscaledBytes` sale de `UpscaleQueue.status.cacheBytes` y aparece como "Imágenes escaladas". "Vaciar caché" también llama a `UpscaleSettingsStore.clearCache()`. `SettingsViewModel` recibe `upscale` y `upscaleQueue` como opcionales, y `settingsModule` los pide con `getOrNull()`.
- Los textos están en `strings_upscale.xml`, en `values/` y `values-es/`.

### Lo que tiene que dar el paso 4

1. Registrar en Koin un `UpscaleQueue` y un `UpscaleSettingsStore` como singletons, en `upscaleModule` (Android) y en `desktopUpscaleModule` (escritorio). Ajustes ya los pide con `getOrNull()`.
2. En la raíz de la UI de las dos plataformas, envolver el contenido en `CompositionLocalProvider(LocalUpscaleQueue provides queue, LocalUpscaleSettings provides store.settings)`. Si no hay cola, no hay que proveer nada.
3. Que `status.cacheBytes` se actualice después de `clearCache()`, porque Datos y el botón leen ese valor.
4. Que `UpscaleState.Done.path` sea una ruta absoluta del sistema de archivos. La UI la abre con `path.toPath()` y el `PathMapper` de Coil.

---

## Paso 2: hecho

No se compiló nada. Todo está en `core/upscale/` (`nijiyome.kmp.library`, sin Compose). Se agregó `include(":core:upscale")` a `settings.gradle.kts`. Dependencias: `kotlinx-coroutines-core` y `kotlinx-serialization-json` como `api`, porque el contrato expone `StateFlow` y tipos `@Serializable`. No hace falta `okio` ni Koin.

### Qué hay en `commonMain`

| Archivo | Qué hace |
|---|---|
| `UpscalePlanner.kt` | `UpscaleLimits` (topes por plataforma), `UpscalePlan` y `UpscalePlanner.decide(...)`. Aplica §4.3: se omite con `f ≤ 1,15`, con una fuente de menos de 48 px de lado, con un tipo apagado o con la función apagada. Elige x2, x3 o x4 según `f`, con el tope de la calidad (Rápido x2, Equilibrado x3, Mejor x4) y el tope de salida (2560 o 1440 px de lado largo). El resultado se guarda reducido al tamaño en pantalla. |
| `UpscaleKey.kt` | La clave es `sha256(url, tipo, motor, versión del modelo, calidad, objetivo redondeado a 64 px)`. `UpscaleKey.sourceHash(bytes)` es el hash de la fuente. |
| `UpscaleResultCache.kt` y `UpscaleFileStore.kt` | La caché de resultados: `<clave>.img` más `<clave>.json` con url, tipo, hash de la fuente, factor y tamaño. Tiene tope y desalojo LRU. La hora de acceso se guarda como fecha de modificación del archivo, sin escribir en cada acierto. `DirectoryUpscaleFileStore(dir)` es la implementación real. |
| `UpscaleIo.kt` | `RgbaImage`, `UpscaleSourceProvider` (bytes de la fuente), `UpscaleImageCodec` (RGBA a archivo y al revés) y los `expect` `platformUpscaleLimits()` y `platformUpscaleCodec()`. |
| `DefaultUpscaleQueue.kt` | La cola. Es LIFO, corre un trabajo a la vez y usa tickets con conteo de referencias. Unifica duplicados por clave y guarda como máximo 64 pendientes. Tiene compuertas de pausa y libera el motor después de 60 s sin trabajo. |
| `UpscaleSettingsCodec.kt` | El JSON de `ImageUpscaleSettings`. Lee campo por campo: si una clave es desconocida, la ignora; si un enum o un tipo no sirve, usa el valor por defecto; si un tipo de imagen es desconocido, lo descarta. También están `sanitized()` (Anime4K se lee como Real-ESRGAN y la caché queda entre 64 y 2048 MB) y `cacheBytes(limits)`. |
| `UpscaleSettingsStoreImpl.kt` | `PersistedUpscaleSettingsStore` (la implementación de `UpscaleSettingsStore`), `UpscaleSettingsPersistence` y `FileUpscaleSettingsPersistence(file)`. |

**Agregado al contrato:** `UpscaleQueue.sourceRefreshed(url)`, con una implementación por defecto vacía. No se renombró ni se quitó nada.

### Comportamiento que conviene saber

- `request` vuelve a decidir con el tamaño real de la fuente después de decodificarla. Coil puede entregar a la UI un tamaño reducido, así que el filtro de la UI solo puede dejar pasar de más.
- El factor y el hash de la fuente no entran en la clave, porque `cached(url, kind, w, h)` tiene que responder antes de leer la fuente. Los dos quedan guardados en la entrada. Si el hash no coincide, la entrada se borra.
- Pausa: es cualquier motivo en `pauseReasons`, o la función apagada. Al pausar se cancela el trabajo en curso, que vuelve arriba de la cola, y se libera el motor en el momento.
- Si `probe()` devuelve `null`, o si el motor falla antes de su primer éxito, la cola se apaga durante la sesión. `pausedBy` pasa a `NO_GPU` o `ENGINE_FAILED` y los pedidos responden `Skipped`. Nunca se usa la CPU.
- Un `null` de `UpscaleEngine.upscale` cuenta como cancelación si el ticket se liberó o hubo una pausa. Si no, cuenta como fallo.
- Los `StateFlow` de cada clave sobreviven a la liberación del ticket. Así, la UI puede recordar el flow de una clave entre una liberación y un pedido nuevo.

### Lo que tiene que hacer el paso 4 (plataformas)

1. **Códec:** reemplazar los stubs `platformUpscaleCodec()` (marcados `TODO(step 4)`).
   - Android: `BitmapFactory`, `createScaledBitmap(filter = true)` y `compress(WEBP_LOSSY, 92)`.
   - Escritorio: Skia. Hay que sumar la dependencia en `desktopMain`.
   - Mientras falten, cada trabajo termina en `Failed("decode failed")`.
2. **Fuente:** implementar `UpscaleSourceProvider` sobre `imageLoader.diskCache?.openSnapshot(url)`. Si no hay entrada, devuelve `null`. Nunca descarga.
3. **Motor:** implementar `UpscaleEngine` sobre JNI (paso 1).
   - `probe()` devuelve el nombre de la GPU o `null`.
   - `upscale` consulta `cancelled` entre mosaicos.
   - El hilo nativo corre con prioridad baja.
4. **Compuertas:** armar un `StateFlow<Set<UpscalePauseReason>>` por plataforma.
   - Motivos: `PLAYER_OPEN`, `FRAMEGEN_ACTIVE`, `LINK_BUSY`, `APP_HIDDEN`, `BATTERY_SAVER`, `LOW_BATTERY` y `HOT`.
   - `NOT_CHARGING` solo va cuando `onlyWhileCharging` está activado y el equipo no carga.
   - `DISABLED`, `NO_GPU` y `ENGINE_FAILED` los pone la cola; no hace falta enviarlos.
5. **Koin:** el orden de armado es este.
   - `val cache = UpscaleResultCache(DirectoryUpscaleFileStore(File(cacheDir, "upscaled")), UpscaleResultCache.megabytes(limits.defaultCacheMb))`
   - `val store = PersistedUpscaleSettingsStore(persistence, cache, appScope)`
   - `DefaultUpscaleQueue(engine, source, codec, cache, store.settings, pauses, appScope)`
   - `appScope` tiene que correr en `Dispatchers.Default` y no en Main, porque los colectores de la cola ajustan el tope de la caché.
   - `UpscaleQueue` y `UpscaleSettingsStore` van como singletons.
6. **Persistencia de ajustes:** `ImageUpscaleSettings` no puede ser un campo de `AppSettings`. `AppSettings` está en `:core:model`, que es JVM puro y no depende de `:core:upscale`, y el tipo del contrato vive en `app.nijiyome.upscale`. Hay dos opciones:
   - `FileUpscaleSettingsPersistence(File(dataDir, "upscale-settings.json"))`, como `torrent-settings.json` en escritorio. Es lo más simple.
   - Un `UpscaleSettingsPersistence` sobre la clave `image_upscale_json` del DataStore de ajustes, como dice §5. Para eso `:core:data` tiene que exponer lectura y escritura de esa clave como texto.
7. **Coil:** cuando una URL llega desde `DataSource.NETWORK`, llamar a `queue.sourceRefreshed(url)`. Lo puede hacer `UpscalableImage` en `onSuccess`, o un `EventListener` del `ImageLoader`.

### Tests (`commonTest`)

- `UpscalePlannerTest`: calificación por tamaño, factor por calidad, tope de salida y claves.
- `UpscaleResultCacheTest`: invalidación por hash, LRU, orden después de reiniciar y huérfanos.
- `UpscaleSettingsTest`: el códec en modo permisivo y el store.
- `DefaultUpscaleQueueTest`:
  - orden LIFO y unificación de duplicados;
  - cancelación al liberar, en cola y en curso;
  - invalidación con `sourceRefreshed`;
  - pausa y reanudación;
  - liberación después de 60 s y al pausar;
  - sin GPU, con el motor fallido y con la función apagada.

Se usan un motor, una fuente y un códec falsos, y `StandardTestDispatcher`. Comando del build 1: `:core:upscale:desktopTest :core:upscale:testAndroidHostTest`.

---

## Paso 1: hecho

Nada se compiló (ni CMake, ni Gradle, ni compiladores). Todo lo de abajo está escrito contra el código de ncnn `20261020` leído a mano, sin verificar con un build.

### Archivos

- `native/common/gpu_pick.h` (nuevo, solo cabecera, GPL-3.0-only como el origen): la política de `fg_desktop_engine.cpp` (discreta > integrada > virtual, nunca CPU ni "other", desempate por mayor heap `DEVICE_LOCAL`, ante empate exacto gana la primera). No incluye ninguna cabecera de Vulkan, porque framegen usa volk y upscale usa `simplevk.h` de ncnn. Trae `pickBest`, `pickPreferred` (por UUID de 32 hex o LUID de 16 hex), el rechazo de llvmpipe, lavapipe, SwiftShader y softpipe por nombre, y `VkIdPropertiesLayout` para leer `deviceUUID` y `deviceLUID`.
- `native/desktop-framegen/src/fg_desktop_engine.cpp` y `CMakeLists.txt`: framegen ahora usa `pickBest` con `rejectSoftwareByName = false`. La elección es la misma de antes. Las consultas de features y extensiones se hacen solo sobre el dispositivo elegido, y el resultado es igual porque antes quedaban las del último "mejor". `deviceUuid` sigue siendo `vendorID<<32 | deviceID`, que es lo que lsfg-vk espera.
- `native/upscale/CMakeLists.txt`: un solo proyecto para Linux, Windows y Android (solo arm64-v8a y x86_64; en otras ABI no compila nada). ncnn va estático con `NCNN_VULKAN=ON`, `simplevk` (no hace falta el SDK de Vulkan), el glslang incluido, sin OpenMP, sin API de plataforma y sin herramientas. Las capas se apagan todas leyendo la lista del propio ncnn, salvo `input split convolution prelu interp binaryop pixelshuffle` (las del modelo) y `packing padding cast crop flatten reshape quantize dequantize requantize bias scale innerproduct` (las que crea ncnn por dentro).
- `native/upscale/third_party/ncnn`: **submódulo** fijado al tag `20261020` (commit `7e36d25cb153092ba87a60530455c7376fedf452`, `shallow = true` en `.gitmodules`). Usa el submódulo anidado `glslang` (commit `780b6a2`); `pybind11` no hace falta. Quedó **en el índice** (`git add` de `.gitmodules` y del gitlink), sin commit.
- `native/upscale/src/upscale_engine.{h,cpp}`: el motor. El ciclo de mosaicos está adaptado de `realesrgan.cpp` de realesrgan-ncnn-vulkan (MIT, aviso en la cabecera y `third_party/realesrgan-ncnn-vulkan/LICENSE`).
- `native/upscale/src/upscale_shaders.h`: shaders de pre y postproceso (MIT, adaptados). Se compilan en tiempo de ejecución con `ncnn::compile_spirv_module`, así que no hay paso de `glslangValidator` al compilar.
- `native/upscale/src/upscale_jni.cpp`: JNI. Las firmas de Kotlin están documentadas arriba del archivo.
- `native/upscale/models/`: `realesr-animevideov3-x{2,3,4}.{param,bin}`, `LICENSE-Real-ESRGAN.txt` (BSD-3) y `README.md` con origen y hashes. `.gitattributes` los marca `-text`.
- `packaging/linux/build-desktop-upscale.sh`: igual que el de framegen. Además baja el submódulo si falta, compila solo el target `nijiyome_upscale` y acepta `--jobs` para `scripts/acotado.sh`. Imprime la ruta de `libnijiyome_upscale.so` en la última línea.
- `packaging/windows/build-desktop-upscale.ps1`: script aparte (no toqué `build-windows.ps1`). En su ayuda está el bloque de 3 líneas que hay que pegar en `build-windows.ps1` y en CI. Imprime la ruta de `nijiyome_upscale.dll`.

### Contrato JNI

Biblioteca `nijiyome_upscale` (`System.loadLibrary("nijiyome_upscale")` en Android; `System.load` sobre rutas candidatas en escritorio, igual que `DesktopFrameGenNative.kt`). Clase `app.nijiyome.upscale.UpscaleNative`, un `object` con todas las funciones `@JvmStatic`:

```kotlin
internal object UpscaleNative {
    @JvmStatic external fun nProbe(preferredKey: String?): String
    @JvmStatic external fun nInit(deviceIndex: Int, modelDir: String): Int
    @JvmStatic external fun nUpscale(rgba: ByteArray, width: Int, height: Int, scale: Int, tile: Int,
                                     cancelToken: java.util.function.BooleanSupplier?): ByteArray?
    @JvmStatic external fun nRelease()
}
```

- `nProbe` devuelve JSON: `ok`, `index` (índice de ncnn, el que va a `nInit`), `device`, `type` (`discrete|integrated|virtual|cpu|other`), `vendorId`, `deviceId`, `uuid`, `luid`, `heapMb`, `fp16`, `int8`, `driver`, `preferredMatched`, `error` (`no_vulkan` o `no_hardware_gpu`), `detail` y `devices[]` (`index`, `name`, `type`, `uuid`, `usable`) para el log. `preferredKey` es opcional: el UUID del contexto GL de Skia o el LUID D3D11 (§4.6). Si no coincide con ningún dispositivo usable, se aplica la política normal.
- `nInit` devuelve `0` si salió bien. Si no, devuelve uno de estos códigos: `-2` argumentos, `-3` sin Vulkan, `-4` dispositivo inválido, CPU o software, `-5` faltan modelos, `-6` el modelo no carga, `-7` el shader no compila, `-8` error de GPU. Carga x2 en ese momento para que un driver roto falle ahí (R1). x3 y x4 se cargan cuando se piden por primera vez.
- `nUpscale` recibe RGBA8 de `width*height*4` bytes y devuelve RGBA8 de `(width*scale) x (height*scale)`; `scale` puede ser 2, 3 o 4. Si `tile <= 0` usa 128 en escritorio y 64 en Android, y siempre lo recorta al presupuesto: 25 % de `get_heap_budget()`, con tope de 256 MB en escritorio y 96 MB en Android. Antes de cada mosaico llama a `cancelToken.getAsBoolean()`. Devuelve `null` si se canceló o si falló: Kotlin consulta su token para saber cuál de los dos pasó, y los fallos quedan en stderr o en logcat (tag `NijiyomeUpscale`). Si la imagen es opaca, se procesa como RGB y el alfa sale en 255.
- `nRelease` libera las redes, los pipelines y la VRAM del pool. La instancia y el `VkDevice` de ncnn siguen vivos hasta que termina el proceso. Además, el pool se vacía al final de cada trabajo.
- Las cuatro llamadas pasan por un mismo mutex. Hay que llamarlas desde el hilo único de la cola. La prioridad baja del hilo se fija del lado de Kotlin (`Thread.priority` o `Process.setThreadPriority`).

### Modelos

Vienen del release `v0.2.5.0` de Real-ESRGAN, archivo `realesrgan-ncnn-vulkan-20220424-ubuntu.zip` (SHA-256 `e5aa6eb131234b87c0c51f82b89390f5e3e642b7b70f2b9bbe95b6a285a40c96`). Los hashes de cada archivo están en `native/upscale/models/README.md`.

- `.param`: x2 `b88ff4f0…2a04`, x3 `d1a57550…0881`, x4 `850a248e…435e`.
- `.bin`: los tres son **idénticos**: `548a36f9c3f4ab8da56cd3b13badf23968bee207b396dad14d04b830e5f2ab2d`.

**Importante para los pasos 2 y 3:** la red siempre escala x4. x2 y x3 son x4 más un `Interp` bicúbico al final, así que **"Rápido" (x2) no ahorra tiempo de GPU**, solo memoria de salida. Si "Rápido" tiene que ser más rápido de verdad, debería usar mosaicos más chicos o subir los umbrales. Esto se ve mejor cuando el build 1 tenga mediciones.

### Para el paso 4

- `:core:upscale-native`: `externalNativeBuild { cmake { path = file("../../native/upscale/CMakeLists.txt"); version = "3.22.1" } }`, `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }`, `targets += "nijiyome_upscale"`, NDK 28.2 y STL `c++_static`, igual que `:core:framegen`. ncnn más glslang es pesado: AGP lo compila por ABI y por variante. Conviene compilar solo `release` o fijar `-DCMAKE_BUILD_TYPE=Release` también para debug.
- Hay que copiar los modelos desde `assets` o `resources` a una carpeta real (`filesDir/models/` en Android, la carpeta de datos en escritorio) antes de `nInit`. ncnn los lee con `fopen`. Alcanza con el `-x4.bin` y los tres `.param`.
- Lo que nativo no hace: no tiene una lista de GPU bloqueadas (Adreno, R1), que va en Kotlin como `FgPolicy`; no lee el UUID de GL ni el LUID de D3D11, que hay que pasar en `preferredKey`; y no tiene un temporizador de 60 s para `nRelease`.

### Para el paso 5

- CI: agregar `native/upscale/**`, `native/common/**` y `packaging/linux/build-desktop-upscale.sh` al filtro de rutas. **`native/common/**` también tiene que disparar el job de framegen**, porque ahora lo incluye. El checkout necesita el submódulo (`git submodule update --init --depth 1 native/upscale/third_party/ncnn` y después `git -C native/upscale/third_party/ncnn submodule update --init --depth 1 glslang`); los scripts lo hacen solos si falta.
- Cachear `build/desktop-upscale` en CI, porque la primera compilación de ncnn y glslang es la parte lenta.
- `THIRD_PARTY_NOTICES.md`: sumar ncnn (BSD-3) con glslang (BSD-3, Apache-2.0 y otras), realesrgan-ncnn-vulkan (MIT, código adaptado) y los pesos de Real-ESRGAN (BSD-3).

### Sin verificar (no hubo build)

- No se compiló. Puede haber errores de compilación, sobre todo en estos puntos de la API de ncnn: `ncnn::vkGetPhysicalDeviceProperties2KHR`, los campos de `ncnn::Option` y `Layer::forward` con `VkMat`.
- No se probó que los shaders compilen con el preámbulo de ncnn: `uint8_t` con `GL_EXT_shader_8bit_storage`, `float16_t` con fp16 storage.
- No se verificó que la lista mínima de capas alcance. Si al cargar falta una capa, ncnn falla en `load_param` y `nInit` devuelve `-6`; en ese caso se agrega a `NJUP_NCNN_LAYERS_ON`.
- El cálculo de memoria por mosaico es una estimación, no una medición.
- Falta medir el tamaño real del `.so`/`.dll` y los ms por póster.

---

## Paso 4: hecho

No se compiló nada (ni Gradle ni CMake). Todo está escrito contra el catálogo y contra los jars de Coil 3.6.3 y Skiko 0.150.2 de la caché de Gradle, leídos con `javap`.

### Archivos

| Archivo | Qué hace |
|---|---|
| `core/upscale/.../NativeUpscaleEngine.kt` | `UpscaleNativeBridge` (las cuatro llamadas JNI detrás de una interfaz), el parser del JSON de `nProbe`, `UpscaleGpuPolicy` (lista de GPU bloqueadas) y `NativeUpscaleEngine`, el `UpscaleEngine` común. `probe()` carga la biblioteca, pide la GPU y aplica la lista. `nInit` corre en el primer `upscale` y otra vez después de cada `release`, así un driver roto aparece como `ENGINE_FAILED`. |
| `core/upscale/.../UpscaleService.kt` | `UpscaleService.create(...)`: arma caché, store y cola en el orden del paso 2, con el scope en `Dispatchers.Default` y un hilo propio de prioridad mínima (daemon) como `worker`. `close()` lo apaga (Koin `onClose`). También `upscalePauses(...)`, `pauseWhen(...)`, `CoilDiskCacheSource` y `UpscaleRefreshListener`. |
| `core/upscale/src/desktopMain/.../UpscalePlatform.desktop.kt` | Códec Skia: `Image.makeFromEncoded`, lectura RGBA sin premultiplicar, redimensión bilineal en una `Surface` y `encodeToData(WEBP, 92)`. |
| `core/upscale/src/desktopMain/.../DesktopUpscaleNative.kt` | `UpscaleNative` (JNI), `DesktopUpscaleBridge` (carga como `DesktopFrameGenNative`: `NIJIYOME_DESKTOP_UPSCALE`, `compose.application.resources.dir`, rutas de jpackage, `desktopApp/build/generated/appResources/<os>/`, `build/desktop-upscale/` y `Release/`) y `desktopUpscaleEngine()`. Los modelos se leen en su lugar desde `<resources.dir>/upscale-models/` (lo que deja el paso 5) o desde `native/upscale/models/` en desarrollo; no se copian. |
| `core/upscale/src/androidMain/.../UpscalePlatform.android.kt` | Códec Android: `BitmapFactory` sin premultiplicar, `createScaledBitmap(filter = true)` y `compress(WEBP_LOSSY, 92)` (`WEBP` antes de API 30). Premultiplica en Kotlin solo si hay alfa, porque `Canvas` rechaza bitmaps sin premultiplicar. |
| `core/upscale/src/androidMain/.../AndroidPowerSignals.kt` | Ahorro de batería, batería ≤ 20 % sin cargar, temperatura ≥ `THERMAL_STATUS_MODERATE` (API 29+) y `NOT_CHARGING` con "Solo mientras carga". Usa broadcasts del sistema y el listener térmico; no sondea. |
| `core/upscale-native/` (nuevo, `nijiyome.android.library`) | `externalNativeBuild` sobre `native/upscale/CMakeLists.txt`, solo arm64-v8a y x86_64, target `nijiyome_upscale`, NDK 28.2, `-DCMAKE_BUILD_TYPE=Release` también en debug y `c++_static`. Una tarea copia a los assets (`upscale-models/`) solo los tres `.param` y el `-x4.bin`. `AndroidUpscaleEngine.kt`: `UpscaleNative`, `System.loadLibrary`, el instalador de modelos a `filesDir/upscale-models/<build>/` (como `AssetShaderInstaller`) y la prioridad `THREAD_PRIORITY_BACKGROUND` del hilo. `consumer-rules.pro` conserva `UpscaleNative`. |
| `core/player-mpv/.../framegen/DesktopFrameGenActive.kt` | Flow global nuevo: lo alimenta `onActiveChanged` de cada `DesktopMpvPlayerSession` y se limpia en `release()`. Framegen no lo lee; su comportamiento no cambia. |
| `desktopApp/.../DesktopUpscale.kt` | `desktopUpscaleModule(dataDir, cacheDir)` y `DesktopWindowHidden`. Se registra en `DesktopModules.kt`. |
| `desktopApp/.../Main.kt` | P5: la caché de disco de Coil pasa a `<cache>/coil3` con los tamaños por defecto. Se agrega el `UpscaleRefreshListener` y un efecto que marca la ventana oculta o minimizada. |
| `app/.../UpscaleAndroid.kt` | `upscaleModule` y `AppVisibility` (actividades iniciadas, registrado en `onCreate` antes de la primera actividad). |
| `app/.../NijiyomeApplication.kt` | `SingletonImageLoader.Factory` y `upscaleModule` en la lista de módulos. |
| `feature/shell/.../NijiyomeApp.kt` | Provee `LocalUpscaleQueue` y `LocalUpscaleSettings` con `getOrNull()`. Si no hay cola, provee `null`, que es lo mismo que no proveer nada. |
| `settings.gradle.kts`, `app/build.gradle.kts`, `core/upscale/build.gradle.kts` | `include(":core:upscale-native")`. La app suma `projects.core.upscaleNative` y `libs.coil.compose`. `:core:upscale` suma `coil-core` con la coordenada completa y la versión del catálogo (no hay entrada `coil-core` en el catálogo). |

### Decisiones

- **Persistencia de ajustes:** `FileUpscaleSettingsPersistence`, en `filesDir/upscale-settings.json` en Android y en `<data>/upscale-settings.json` en escritorio. Es la opción más simple: no toca `:core:data` ni el DataStore, y `ImageUpscaleSettings` no puede vivir en `AppSettings` (`:core:model` no depende de `:core:upscale`). "Restablecer la app" borra los dos lugares, porque borra la carpeta de datos.
- **`ImageLoader` de Android:** `ImageLoader.Builder(context).build()` arma lo mismo que el singleton por defecto de Coil 3.6.3. Lo verifiqué en el bytecode: el singleton por defecto es ese mismo builder más una marca interna `isDefault`. Usa la misma caché de disco compartida (`SYSTEM_TEMPORARY_DIRECTORY/coil3_disk_cache`, que en Android es `cacheDir`, con 2 % del volumen, entre 10 y 250 MB), la caché de memoria `maxSizePercent(context)` y los componentes por ServiceLoader (el fetcher de OkHttp). Lo único nuevo es el `EventListener`.
- **`sourceRefreshed`:** Coil tiene un gancho barato, `EventListener.onSuccess` con `result.dataSource`. Con `NETWORK` y una URL `http(s)`, se llama a `queue.sourceRefreshed(url)`. Si la URL no tiene resultados, la cola sale enseguida (`keysFor` es en memoria).
- **Compuertas:**
  - Escritorio: `PLAYER_OPEN` (`NowPlaying.current != null`), `FRAMEGEN_ACTIVE` (`DesktopFrameGenActive`), `LINK_BUSY` (`LinkServerState.activeSessions` no vacío, publicado cada 2 s) y `APP_HIDDEN` (bandeja o minimizada).
  - Android: `PLAYER_OPEN` (`ActivePlayback.player != null`, que publican los dos motores), `FRAMEGEN_ACTIVE` (`FgActiveFlag`), `LINK_BUSY` (`RemoteProcessingState`), `APP_HIDDEN` (`AppVisibility`) y las de `AndroidPowerSignals`.
  - La cola ya libera el motor al pausar y después de 60 s sin trabajo.
- **Coincidencia de GPU (solo upscale):** no se pasa `preferredKey`. El dispositivo GL o D3D de Skia no se puede leer de forma segura: `SkiaLayer.renderInfo` llama a `glGetString` sin contexto actual, desde cualquier hilo. Se usa la elección compartida de `gpu_pick.h`. En el log quedan todos los dispositivos Vulkan y el elegido. En Linux también queda el id PCI de la tarjeta con `boot_vga = 1` (la que usa Mesa por defecto), con un aviso si no coincide con el elegido. Framegen no cambió.
- **Lista de GPU bloqueadas (R1):** `UpscaleGpuPolicy`. Bloquea Adreno 840 (por el aviso de RealSR-NCNN-Android) y los Adreno anteriores al 600. Hay que ajustarla con la prueba en el teléfono. Una GPU bloqueada apaga la función; nunca se usa la CPU.

### Sin verificar (no hubo build)

- La API de Skia (`Surface.canvas`, `drawImageRect` con `SamplingMode` y `Paint`, `encodeToData(WEBP, 92)`) se tomó de las firmas del jar, sin compilar. Tampoco se probó que Skiko traiga el codificador WebP.
- Que AGP respete `-DCMAKE_BUILD_TYPE=Release` en las variantes debug. Si AGP lo pisa, sigue funcionando, pero más lento.
- `RECEIVER_NOT_EXPORTED` con `ACTION_BATTERY_CHANGED` y `ACTION_POWER_SAVE_MODE_CHANGED`: son broadcasts del sistema y deberían llegar igual.
- La caché vieja `/tmp/coil3_disk_cache` de escritorio no se borra; queda para la limpieza del sistema.
