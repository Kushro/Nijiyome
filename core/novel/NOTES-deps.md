# NOTES-deps (:core:novel, parity wave B, worker s)

Nada nuevo pedido por el contrato: OkHttp, jsoup (limpiar HTML de los capítulos), Media3 (`media3-exoplayer` para reproducir el audio
de los motores TTS en la nube), Koin y serialización ya están en el catálogo. `android.speech.tts.TextToSpeech` es de la plataforma.
KittenTTS NO tiene build Android (plan §0.1.13: solo escritorio). Añade filas aquí (coordenadas, versión, motivo) para lo demás.

Ola B, worker s (implementación): `:core:novel` depende ahora de `:core:manga` (`api(projects.core.manga)`, módulo del mismo worker, sin
dependencia externa nueva) para reutilizar el cliente AniList, `ReadlistStore`, `JsonFileStore` y la lógica de capítulos. El texto
descargado vive en un SQLite propio (`filesDir/novels/novel.db`, `android.database.sqlite.SQLiteDatabase` de la plataforma; sin Room).

Hito 3 (multiplataforma): `nijiyome.kmp.library`. Media3, `androidx.core`, `koin-android` y corrutinas de Android quedan en `androidMain`
(narrador y almacén SQLite); OkHttp, jsoup, serialización y Koin en `commonMain`. Sin dependencias nuevas.

1.1 (narrador de escritorio): `desktopMain` depende de `:core:player` y `:core:player-mpv` (módulos propios; el narrador reproduce sus clips con
una sesión de libmpv solo de audio) y `desktopTest` de `kotlinx-coroutines-swing` (ya en el catálogo). Sin dependencias externas nuevas en
el build: ONNX Runtime para Java 1.29.0 (MIT) y el modelo KittenTTS nano 0.8 int8 (Apache-2.0) se descargan a pedido con SHA-256 fijado
(`KittenRelease`), y espeak-ng / speech-dispatcher / SAPI son del sistema.
