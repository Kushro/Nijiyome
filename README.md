# Nijiyome

Anime, manga y novelas para Android, Linux y Windows, con extensiones `.kai`, listas en AniList o MyAnimeList y
reproducción con libmpv. Kotlin + Compose Multiplatform.

> **Versión 1.1.0.** Android y escritorio (Linux y Windows) comparten el mismo código. Las descargas están en
> [Releases](https://github.com/Kushro/Nijiyome/releases): APK de Android, AppImage de Linux, instalador y zip
> portable de Windows. El plan de la migración está en [`docs/plan-migracion.md`](docs/plan-migracion.md).

## Origen

Nijiyome continúa Tatakai Next 0.4.2, del mismo autor (`Kushro/Tatakai-own@196bfe89`), con identidad nueva y una
sola base de código para todas las plataformas. Los comentarios del código que citan `docs/redesign/…` o
`apps/desktop-native/…` se refieren a documentos y fuentes de ese repositorio.

Las extensiones `.kai` hechas para Tatakai siguen funcionando: el host conserva los nombres anteriores de sus
funciones globales como alias (ver [`contracts/extension-host/globals.v1.md`](contracts/extension-host/globals.v1.md)).

## Repositorio

| Ruta | Qué es |
|---|---|
| `app/` | Aplicación Android (`app.nijiyome`) |
| `core/`, `feature/` | Módulos de lógica y de pantallas |
| `build-logic/` | Plugins de convención de Gradle |
| `contracts/` | Contratos compartidos: host de extensiones, Nijiyome Link v1 (OpenAPI), fixtures |
| `tokens/` | Tokens de diseño: `tokens.json` → `Tokens.kt` |
| `native/temple-lsfg/` | Helper de generación de cuadros (C++/Vulkan) para Nijiyome Link |
| `packaging/` | Empaquetado de escritorio heredado; se reescribe durante la migración |
| `scripts/migracion/` | Herramientas de la migración (renombrado, conversión de parches del origen) |

## Compilar

Requisitos: JDK 21, Android SDK (plataforma 37, build-tools 36.0.0, NDK 28.2.13676358, CMake 3.22.1) y Node 22 o
superior para los tests de contratos.

```
cp local.properties.example local.properties     # y completar sdk.dir
./gradlew verificar            # todos los tests, el APK debug y la distribución de escritorio
./gradlew :desktopApp:run      # abre la app de escritorio
node --test tokens/test/*.test.mjs contracts/test/*.test.mjs
```

Los ids de cliente OAuth de AniList y MyAnimeList son opcionales para compilar; sin ellos, "Vincular" queda
deshabilitado. Se configuran en `local.properties` o por variables de entorno (`NIJIYOME_ANILIST_CLIENT_ID`,
`NIJIYOME_MAL_CLIENT_ID`).

## Licencia

AGPL-3.0-or-later. Ver [`LICENSE`](LICENSE) y [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
