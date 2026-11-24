# NOTES-deps (:core:manga, parity wave B, worker s)

Nada nuevo pedido por el contrato (OkHttp, DocumentFile, Koin, serialización ya están en el catálogo). Si s necesita Room para el
progreso / readlist / capítulos descargados, puede usar `libs.room.runtime` + `libs.room.ktx` + ksp `libs.room.compiler` (ya en el
catálogo; mirar `core/database/build.gradle.kts` para el plugin ksp) con una base de datos PROPIA (`manga.db`), nunca `NijiyomeDatabase`.
Añade filas aquí (coordenadas, versión, motivo) para cualquier otra dependencia.

Hito 3 (multiplataforma): el módulo no usa Room (no existe `manga.db`); el progreso, la readlist y los capítulos descargados se
guardan como JSON y archivos bajo `storeDir`. `commonMain` tiene `mangaModule(filesDir)`; Android expone `val mangaModule` con
`androidContext().filesDir`. `androidx.core.ktx` y `documentfile` quedan solo en `androidMain` (el código no los usa).
