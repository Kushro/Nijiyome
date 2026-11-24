# NOTES-deps (:feature:manga, parity wave B, worker s)

Sin dependencias externas nuevas: todo está ya en el catálogo. Módulo multiplataforma (Android + escritorio).
- `projects.core.manga` (módulo core del mismo worker), en `commonMain`.
- `libs.coil.network.okhttp` (como `:core:ui`, en `androidMain` y `desktopMain`: no tiene variante común): `NetworkHeaders` / `httpHeaders` para las páginas que exigen el `Referer` de la extensión.
- `libs.cmp.material.icons.extended` en `commonMain`.
- La ventana del lector (pantalla encendida, brillo, modo inmersivo, orientación, atrás) va por `app.nijiyome.ui.platform` de `:core:ui`.
