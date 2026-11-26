# Paridad del carril transversal

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Transversal). 11 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## NOTA-C-sistema-de-diseno · Sistema de diseño actual
- Plataformas: desktop, android · Cambio: igual
- Funciones: 20 paletas × claro, oscuro, AMOLED × 3 contrastes; Color dinámico Android; 4 presets de aspecto con 11 opciones; Tema por portada en la ficha; Reproductor siempre oscuro AMOLED
- Lugar nuevo: pantalla `ajustes`: Ajustes › Apariencia
- Riesgo: No hay roles de color de éxito o advertencia ni tokens de breakpoints; glass existe sin UI a propósito

## NOTA-C-entradas · Puntos de entrada
- Plataformas: desktop, android · Cambio: fusionado · Patrón: accesos dispersos → riel de utilidades y hub Tú
- Funciones: Ajustes: riel, engranaje, Ctrl+P, Ctrl+K, enlaces directos; Torrents: riel, Biblioteca, Tú, paleta, bandeja, monitor, notificaciones, magnet; Descargas, Extensiones y Link: riel o Tú; Monitor: chip y bandeja
- Lugar nuevo: pantalla `transferencias`: Transferencias, Fuentes, Link y Ajustes en el riel; Tú en Android

## NOTA-C-plataformas · Diferencias de plataforma
- Plataformas: desktop, android · Cambio: igual
- Funciones: Solo escritorio: Red, servidor de Link, monitor, sistema, mpv externo, lsfg-vk, VAAPI, NVScaler, navegador oculto, soltar .kai, vista de lista de torrents; Solo Android: Media3, reproductor externo, verificar certificados, estilo del reproductor, color dinámico, generación de cuadros en el teléfono, permiso de notificaciones, cliente Link, enviar registros, segundo plano; Solo ancho: estilo del menú lateral
- Lugar nuevo: pantalla `ajustes`: Se conservan por plataforma
- Riesgo: Ninguna diferencia puede perderse al unificar pantallas

## NOTA-C-estados · Vocabulario de estados
- Plataformas: desktop, android · Cambio: mejorado · Patrón: colores prestados → roles de estado propios
- Funciones: Píldoras OK, Advertencia, Error, Neutro; Experimental o no probado; Llavero y secretos; Estados de Link
- Lugar nuevo: pantalla `ajustes`: Sistema de estados común en todas las pantallas

## NOTA-C-guardado · Modelo de guardado
- Plataformas: desktop, android · Cambio: mejorado · Patrón: guardado mixto → guardado coherente con estado sin guardar visible
- Funciones: Proxy, FlareSolverr, nombre, carpeta y puertos de Link, contraseña de visor, preferencias de extensión, rutas, traducción, narrador y campos de torrents piden Guardar
- Lugar nuevo: pantalla `ajustes`: Todo Ajustes

## NOTA-C-confirmaciones · Confirmaciones destructivas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: mezcla → regla única (confirmar o deshacer)
- Funciones: Sin confirmar hoy: revocar dispositivo de extensiones, borrar registros, desvincular en el asistente, activar todas, borrar episodio o capítulo, borrar historial de descargas, desvincular debrid
- Lugar nuevo: pantalla `transferencias`: Confirmar o deshacer de forma uniforme

## NOTA-C-texto-crudo · Texto crudo donde va un selector
- Plataformas: desktop, android · Cambio: mejorado · Patrón: campo de texto → selector
- Funciones: Puertos; Carpeta de biblioteca; Rutas de mpv, shader y navegador; Host del proxy; URL de stream; Horas y KiB/s de torrents; Códigos ISO de idioma
- Lugar nuevo: pantalla `ajustes`: Selectores propios de puerto, carpeta, hora e idioma

## NOTA-C-espanol-fijo · Español sin localizar
- Plataformas: desktop, android · Cambio: mejorado · Patrón: literales → recursos
- Funciones: Resúmenes y catálogo de Ajustes; Paleta de comandos; Avisos de AMOLED y contraste; Estilo del reproductor; Recordatorios y notificaciones de AniList; Sesiones y caché del servidor Link; Barra de título nativa; Registros; Monitor de Link
- Lugar nuevo: pantalla `ajustes`: Todo localizado en inglés y español

## NOTA-C-agrupacion · Agrupación a corregir
- Plataformas: desktop, android · Cambio: fusionado · Patrón: repartido → un hogar por función
- Funciones: Sistema bajo Datos; Notificaciones repartidas; Reproductor sobrecargado; Torrents y Descargas repartidos; Dos servidores en Link; Controles duplicados: caché RAM, debrid, registros; Búsqueda que no llega a la fila
- Lugar nuevo: pantalla `ajustes`: 14 categorías, Transferencias y Link

## NOTA-C-docs · Referencias de documentos
- Plataformas: desktop, android · Cambio: igual
- Funciones: Referencias KDoc a docs/redesign que no existen
- Lugar nuevo: pantalla `ajustes`: Sin superficie

## NOTA-C-sin-determinar · Sin determinar
- Plataformas: desktop, android · Cambio: pendiente
- Funciones: Bindings opcionales por plataforma: Stremio, restablecer, actualizaciones, registros, generación de cuadros; Escáner QR de Android sin revisar; TorrentDownloadsScreen sin llamador; Si el cliente Link se usa en escritorio
- Lugar nuevo: pantalla `transferencias`: Verificar con la app corriendo
- Riesgo: El dueño debe confirmar qué secciones ocultas por binding existen en cada plataforma antes de dibujarlas; TorrentDownloadsScreen parece código muerto
