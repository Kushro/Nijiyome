<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
# packaging/icons/

El ícono de Nijiyome: una kitsune entintada tras un abanico con ▶, dentro de un ensō, con el sello 虹, sobre papel washi.

| Archivo | Para qué |
|---|---|
| `nijiyome.svg` | Fuente maestra (512×512, fondo de papel con esquinas redondeadas). Solo trazados planos: sin filtros, `mask`, `pattern` ni degradados. |
| `nijiyome-foreground.svg` | Solo la figura sobre transparente, reducida a la zona segura de 66 dp. Es la capa de primer plano del ícono adaptativo de Android (`app/src/main/res/drawable/ic_launcher_foreground.xml` tiene los mismos trazados). |
| `nijiyome-mono.svg` | Una sola tinta, negro sobre transparente. De ahí sale la capa `<monochrome>` del ícono temático de Android 13+ (`ic_launcher_monochrome.xml`) y sirve para la bandeja del sistema. |
| `nijiyome-16.png` … `nijiyome-512.png` | La maestra rasterizada a 16, 32, 48, 64, 128, 256 y 512 px. Linux usa `nijiyome-512.png` (`nativeDistributions.linux.iconFile`); los tamaños menores quedan para el tema de íconos hicolor del hito 5; la ventana de escritorio carga `nijiyome-256.png`, copiado en `desktopApp/src/main/resources/icons/nijiyome.png`. |
| `nijiyome.ico` | Windows: capas de 16, 32, 48, 64, 128 y 256 px (`nativeDistributions.windows.iconFile`). |

Android: el fondo del ícono adaptativo es el color `@color/icon_background` (`#F3E9D2`, el papel). Desde minSdk 26 no hacen falta mipmaps PNG.

Los VectorDrawable usan `viewportWidth/Height = 512`, así que sus `pathData` son los mismos trazados que los SVG. Si cambia un SVG, hay que regenerar el VectorDrawable correspondiente y los PNG (`rsvg-convert -w N`) y el `.ico` (`magick nijiyome-16.png … nijiyome-256.png nijiyome.ico`).
