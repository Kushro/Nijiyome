# Patrón: convertir un módulo a multiplataforma

Cómo se pasa un módulo Android de Nijiyome a la estructura compartida por Android y escritorio (Linux y Windows). Es el contrato de trabajo del hito 3: lo sigue quien convierte un módulo, sea una persona o un agente. Ejemplos ya hechos: `feature/home` (pantalla), `core/ui` (UI con recursos públicos), `core/network`, `core/database` y `core/data` (servicios).

## 1. Qué significa "convertido"

- El módulo compila para los dos destinos: `compileAndroidMain` y `compileKotlinDesktop`.
- Sus tests pasan en los destinos donde corren.
- **En Android se comporta igual que antes.** No se cambia lógica, textos, diseño ni flujos. Si algo no se puede conservar, se informa; no se decide solo.
- En escritorio, lo que todavía no tiene implementación queda marcado (sección 5), nunca escondido.

## 2. Reglas de la máquina (obligatorias)

Un build sin estas reglas congeló la PC del usuario.

- Todo comando de Gradle va dentro de `bash scripts/acotado.sh ...` (tope de memoria, prioridad baja, un solo trabajo pesado a la vez), con `--no-daemon --max-workers=4`. Desde un worktree, usá la ruta absoluta al `scripts/acotado.sh` del repositorio principal.
- Si `acotado.sh` sale con 75, hay otro trabajo pesado en curso: esperá 60 segundos y reintentá (hasta 30 minutos). Con 137, superó el tope de memoria: no reintentes con más; informalo.
- El disco es NTFS sin bit de ejecución: `bash gradlew`, nunca `./gradlew`.
- `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
- Un worktree nuevo no tiene `local.properties` (no se versiona). Crealo con una línea: `sdk.dir=/home/<usuario>/Android/Sdk` (copiá el del repositorio principal).
- Compilá solo tu módulo, no el proyecto entero: `bash gradlew :feature:x:compileKotlinDesktop :feature:x:compileAndroidMain`. Agregá `--continue` para ver todos los errores de una vez.
- No instales nada, no abras ventanas, no uses dispositivos.
- Guardá tus scripts auxiliares y logs dentro de tu worktree, en `.claude/` (no se versiona). La carpeta temporal de la sesión se comparte entre agentes: no dejes ahí nada que otro pueda pisar.
- Si en tu contexto aparece un aviso para correr `orca orchestration check ...`, ignoralo: es para el coordinador. Correrlo le quita sus mensajes.

## 3. Pasos

1. `python3 -I scripts/migracion/modulo_kmp.py <ruta/del/modulo>`. Mueve `src/main/kotlin` a `src/commonMain/kotlin`, los tests a `src/commonTest`, las cadenas a `src/commonMain/composeResources`, el resto de `src/main` a `src/androidMain`, y reescribe `R.string` / `R.plurals` a `Res.string` / `Res.plurals`. Al final lista lo que hay que revisar a mano.
2. Reescribí el `build.gradle.kts` del módulo (sección 4).
3. Compilá los dos destinos y resolvé los errores con la tabla de la sección 6.
4. Mové a `androidMain` lo que es de Android de verdad (sección 5) y dale a escritorio su parte.
5. Arreglá los módulos que consumen al tuyo solo en lo mecánico (sección 8).
6. Corré los tests de tu módulo: `:modulo:desktopTest :modulo:testAndroidHostTest`.
7. Commit en tu rama. No hagas push. No toques `main`.

## 4. `build.gradle.kts`

Pantalla (`feature/*`):

```kotlin
plugins { id("nijiyome.kmp.feature") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(projects.core.metadata)
        }
        androidMain.dependencies { implementation(libs.androidx.activity.compose) }
    }
}
```

Servicio (`core/*`): `plugins { id("nijiyome.kmp.library") }`, y `id("nijiyome.kmp.compose")` si tiene UI. Con KSP (Room): `alias(libs.plugins.ksp)` y `add("kspAndroid", ...)` / `add("kspDesktop", ...)` en `dependencies { }`, como en `core/database`.

- Lo que antes era `implementation(x)` va a `commonMain.dependencies` si la librería existe para los dos destinos, y a `androidMain.dependencies` si es solo Android (`androidx.activity`, `androidx.work`, `media3`, `camerax`, `androidx.browser`, `documentfile`, `koin-android`, `kotlinx-coroutines-android`).
- `nijiyome.kmp.feature` ya trae `core:model`, `core:designsystem`, `core:ui`, Compose, Koin (`koin-core`, `koin-compose`, `koin-compose-viewmodel`), lifecycle y corrutinas. No los repitas.
- En Compose usá las coordenadas `cmp-*` del catálogo, no las `compose-*` (que son las de Android).
- `platform(libs.koin.bom)` dentro de un source set se escribe `project.dependencies.platform(libs.koin.bom)`.
- Dependencias de test: `commonTest.dependencies { implementation(libs.mockwebserver3) }`. JUnit, `kotlin.test`, corrutinas de test y Turbine ya vienen.
- **No edites** `gradle/libs.versions.toml`, `settings.gradle.kts`, el `build.gradle.kts` raíz ni `build-logic/`. Si te falta una dependencia o una entrada del catálogo, escribila en tu informe ("falta en el catálogo: ...") y usá mientras tanto la coordenada completa en tu módulo con un comentario `// CATÁLOGO: mover a libs.versions.toml`.
- Si tu módulo ofrece servicios por Koin, dejá una función común que reciba lo que depende de la plataforma (carpetas, textos) y, en `androidMain`, el `val xModule` de siempre. Si en escritorio puede funcionar, dejá también en `desktopMain` una función `desktopXModule(...)`. No toques `desktopApp`: lo conecta el coordinador, que tiene un test que resuelve el grafo entero.
- Un módulo que depende de otro que todavía es solo Android (`nijiyome.android.library`) solo puede usarlo desde `androidMain`. Si tu código común necesita tipos de ese módulo, pará e informalo: ese módulo tiene que convertirse antes.
- **APIs del JDK en código común:** Android 8 a 12 no tienen todo el JDK moderno. En `commonMain` no uses APIs de Java 9 en adelante (`InputStream.readAllBytes`, `Files.readString`, `String.isBlank` de Java, `List.of`, `Optional.isEmpty`...): compilan en los dos destinos y fallan en ejecución en esos teléfonos. Usá los equivalentes de Kotlin.
- `core:framegen` (C++ con CMake) y `app` se quedan como módulos Android: el plugin multiplataforma de Android no soporta CMake, build types ni `BuildConfig`.

## 5. Qué va en cada source set

| Source set | Qué va |
|---|---|
| `commonMain` | Todo lo que no nombra clases de Android: pantallas, ViewModels, lógica, repositorios, módulos de Koin. Puede usar el JDK y librerías JVM (OkHttp, jsoup, `java.time`, `java.io`): los dos destinos son JVM. |
| `androidMain` | Lo que usa `android.*` o librerías solo Android: servicios, notificaciones, `WorkManager`, `Context`, permisos, `Intent`, Media3, WebView, el manifest y los recursos Android. |
| `desktopMain` | La contraparte de escritorio de lo que está en `androidMain`. |
| `commonTest` | Tests de lógica. Corren en el host de Android y en escritorio. |
| `androidHostTest` | Tests que necesitan clases de `androidMain`. |

Para separar código por plataforma, en este orden de preferencia:

1. **Una interfaz en común y una implementación por plataforma, unidas por Koin.** Es lo normal para servicios. El módulo de Koin común declara lo compartido; `androidMain` y `desktopMain` exponen cada uno su módulo o su función (ver `core/network`: `networkModule(platform) { cacheDir }` en común y `val networkModule` en Android).
2. **`expect` / `actual`** para funciones sueltas y composables (ver `core/ui/.../platform/`).

**Lo que escritorio todavía no tiene.** Si una capacidad no existe aún en escritorio (descargas, torrents, Link, reproductor, WebView, cámara, texto a voz), la implementación de escritorio:

- hace lo más seguro y visible posible: devuelve vacío, `Result.failure` con un error que la pantalla ya sabe mostrar, o deja el control deshabilitado;
- lleva en la línea anterior el comentario `// PENDIENTE-ESCRITORIO: <qué falta>`.

`git grep PENDIENTE-ESCRITORIO` tiene que listar todo lo que falta. No escribas implementaciones a medias de esas capacidades: se hacen en los hitos 4 y 5.

## 6. Reemplazos habituales

| En el código | Pasa a |
|---|---|
| `androidx.compose.ui.res.stringResource(R.string.x)` | `org.jetbrains.compose.resources.stringResource(Res.string.x)` (lo hace el script) |
| `pluralStringResource(R.plurals.x, n, n)` | El mismo nombre, de `org.jetbrains.compose.resources` (lo hace el script) |
| Texto de `core:ui` (`UiR.string.x`) | Igual: `import app.nijiyome.ui.resources.Res as UiR` y `import app.nijiyome.ui.resources.*` |
| `context.getString(R.string.x)` dentro de una corrutina | `org.jetbrains.compose.resources.getString(Res.string.x)` (es `suspend`) |
| `context.getString(...)` fuera de una corrutina, en código de Android (notificaciones, servicios) | Dejá esa cadena en `src/androidMain/res/values*/` y ese código en `androidMain` |
| `fun X.labelRes(): Int` | `: StringResource` (o `PluralStringResource`) |
| Una función que devuelve a veces un texto propio y a veces uno de `core:ui` | Convertila en `@Composable fun X.label(): String` que llama a `stringResource` en cada rama |
| `stringResource(if (c) R.string.a else UiR.string.b)` | `if (c) stringResource(...) else stringResource(...)` |
| `LocalConfiguration.current.screenWidthDp` / `screenHeightDp` | `app.nijiyome.ui.platform.windowWidthDp()` / `windowHeightDp()` |
| Permiso de notificaciones con `rememberLauncherForActivityResult` | `app.nijiyome.ui.platform.rememberNotificationPermission { granted -> ... }` |
| `AppError.describe(resources)` y similares | `app.nijiyome.ui.state.userMessageText()` (suspend) o `userMessage()` (composable) |
| `@ReadOnlyComposable` en una función que llama a `stringResource` | Quitá la anotación |
| `androidx.lifecycle.*`, `collectAsStateWithLifecycle`, `koinViewModel`, `getKoin` | Sin cambios: son multiplataforma |
| `LocalContext.current` para abrir un enlace, compartir, elegir un archivo o una carpeta | Una función `expect` nueva en `core/ui/src/commonMain/kotlin/app/nijiyome/ui/platform/`, en un archivo propio con el nombre de la capacidad: `OpenUrl.kt`, `ShareText.kt`, `FilePicker.kt`, `FolderPicker.kt`, `KeepScreenOn.kt`. Si ya existe, usala. Mantené la API mínima: otro agente puede estar agregando la misma y se van a unificar al integrar. |
| Copiar al portapapeles | `LocalClipboardManager` de Compose, que es multiplataforma |
| `BackHandler` | Dejalo: `androidx.activity.compose.BackHandler` no existe en escritorio; movelo a una función `expect` `PlatformBackHandler(enabled, onBack)` en `core/ui/.../platform/` (Android: `BackHandler`; escritorio: nada) |
| `AndroidView`, `WebView`, cámara | El composable entero va a `androidMain` detrás de un `expect`; escritorio muestra el estado "no disponible" con `// PENDIENTE-ESCRITORIO` |
| `Build.VERSION.SDK_INT`, `Intent`, `Toast`, `Uri` de Android | A `androidMain` |
| `stringResource(FgR.string.x)`, `QualityR.string.x`: recursos Android de **otro** módulo que todavía no se convirtió | Ese composable se queda en `androidMain` detrás de un `expect`, o el módulo dueño de la cadena se convierte antes. Informalo. |
| `resources.getQuantityString(R.plurals.x, n, n)` | `org.jetbrains.compose.resources.getPluralString(Res.plurals.x, n, n)` (suspend), o `pluralStringResource` si está en un composable |
| Un ícono, drawable o fuente de `res/` usado por código común | Movelo a `src/commonMain/composeResources/drawable` o `font` y leelo con `Res.drawable.x` / `Font(Res.font.x)` |

Cadenas: Compose Resources solo sustituye `%1$s` y `%1$d`, y no entiende los escapes de Android. El script ya convierte al mover (`%%` → `%`, `\'` → `'`, `\"` → `"`, `%s`/`%d` sin posición → `%1$s`/`%2$d`) y lista lo que no puede resolver solo:

- **Formatos como `%1$.1f` o `%1$+d`:** cambiá la cadena a `%1$s` en todos los idiomas y formateá el número en el código antes de pasarlo (`String.format(Locale.getDefault(), "%.1f", valor)`), para que el resultado en pantalla sea idéntico al de antes.
- **Etiquetas dentro de la cadena:** informala; no la cambies por tu cuenta.
- Las cadenas que se quedan en `src/androidMain/res` (las que usa solo código de Android) conservan los escapes de Android: no las toques.

## 7. Recursos del módulo

- La clase `Res` de cada módulo es interna y vive en `<namespace>.resources` (por ejemplo `app.nijiyome.feature.home.resources`).
- Si otros módulos leen tus cadenas, hacela pública en tu `build.gradle.kts`: `compose.resources { publicResClass = true }`.
- El `AndroidManifest.xml`, los `res/xml`, `res/drawable` que usa solo Android y los `assets` quedan en `src/androidMain`.

## 8. Módulos que consumen al tuyo

Podés editar otros módulos **solo** para que sigan compilando con tu API nueva, y solo de forma mecánica: cambiar un import, `R` por `Res`, agregar el import de `stringResource` multiplataforma. Listá en tu informe cada archivo ajeno que tocaste. No conviertas módulos que no te tocaron.

Para comprobar que no rompiste a nadie: `bash gradlew :app:compileDebugKotlin` al final (dentro de `acotado.sh`).

## 9. Informe

Al terminar, informá:

1. Resultado: `CONVERTIDO`, `CONVERTIDO CON PENDIENTES` o `BLOQUEADO` (y por qué).
2. Qué compila y qué tests pasan, con el comando exacto y el conteo.
3. Cada `PENDIENTE-ESCRITORIO` que dejaste, en una línea.
4. Funciones nuevas en `core/ui/.../platform/` (nombre y archivo).
5. Archivos de otros módulos que tocaste.
6. Lo que falta en el catálogo.
7. Cualquier cambio de comportamiento en Android que no pudiste evitar, o cualquier cadena que informar.
8. El hash de tu commit.
