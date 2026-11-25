# Paridad del carril S8

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Transferencias). 43 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## TOR-01 · Cliente de torrents
- Plataformas: desktop, android · Cambio: fusionado · Patrón: destino propio y pestaña en Biblioteca → parte de Transferencias; Debrid a Fuentes y Ajustes a Ajustes
- Funciones: Agregar torrent (+); Secciones: Biblioteca, Descargas, Historial, Debrid, Ajustes; Abrir magnet o .torrent entrante; Abrir el torrent recién agregado; Vista de dos paneles en ventana ancha
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents

## TOR-02 · Biblioteca de torrents
- Plataformas: desktop, android · Cambio: reubicado · Patrón: pestaña en Biblioteca → Transferencias, con búsqueda y filtros de estado
- Funciones: Abrir torrent; Reproducir; Mantener presionado para seleccionar; Menú de acciones; Configurar debrid desde el vacío; Reintentar; Estado, progreso y si se conserva
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Biblioteca

## TOR-03 · Orden y vista de la biblioteca
- Plataformas: desktop · Cambio: mejorado · Patrón: menú de orden aparte → encabezados de columna ordenables y orden recordado
- Funciones: Ordenar por fecha, nombre, episodio, tamaño, progreso, estado; Dirección ascendente o descendente; Vista Tarjetas o Lista; Columnas de la lista
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Biblioteca, barra de herramientas

## TOR-04 · Menú de un torrent
- Plataformas: desktop, android · Cambio: mejorado · Patrón: menú plano → agrupado y con Eliminar separado
- Funciones: Abrir en el cliente; Reproducir; Pausar o reanudar; Abrir carpeta (escritorio); Conservar o no tras ver; Compartir o copiar magnet; Eliminar
- Lugar nuevo: pantalla `transferencias`: Menú de la fila en Transferencias › Torrents

## TOR-05 · Selección múltiple de torrents
- Plataformas: desktop, android · Cambio: mejorado · Patrón: iconos sin texto → acciones con etiqueta, No conservar en lote y selección por rango
- Funciones: Quitar selección; Contador; Seleccionar todo; Pausar; Reanudar; Conservar tras ver; Eliminar seleccionados
- Lugar nuevo: pantalla `transferencias`: Barra contextual en Transferencias › Torrents

## TOR-06 · Eliminar torrents
- Plataformas: desktop, android · Cambio: mejorado · Patrón: Cerrar → Cancelar y botón destructivo
- Funciones: Confirmar eliminación de uno o varios; Borrar también los archivos
- Lugar nuevo: pantalla `transferencias`: Diálogo en Transferencias

## TOR-07 · Elegir torrent
- Plataformas: desktop, android · Cambio: igual
- Funciones: Elegir un torrent para ver una pestaña; Abrir la biblioteca si está vacía
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents

## TOR-08 · Detalle de torrent
- Plataformas: desktop, android · Cambio: reubicado · Patrón: título genérico Torrents → nombre real en la barra
- Funciones: Pausar o reanudar; Copiar info-hash; Compartir o copiar magnet; Abrir carpeta (escritorio); Conservar o no tras ver; Quitar; Pestañas Resumen, Archivos, Pares, Rastreadores
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Detalle

## TOR-09 · Resumen del torrent
- Plataformas: desktop, android · Cambio: mejorado · Patrón: rejilla densa → jerarquía con interruptores arriba
- Funciones: Copiar info-hash; Conservar tras ver; Seguir compartiendo al terminar; Progreso y tiempo restante; Transferencia, enjambre, protocolo
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Detalle › Resumen

## TOR-10 · Archivos del torrent
- Plataformas: desktop, android · Cambio: mejorado · Patrón: lista plana → árbol de carpetas con casillas
- Funciones: Buscar archivos; Alinear episodios; Abrir acciones del archivo; Resumen de seleccionados
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Detalle › Archivos

## TOR-11 · Acciones de un archivo
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reproducir este archivo; Descargar solo este; Guardar en Descargas
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Detalle › Archivos

## TOR-12 · Alinear episodios
- Plataformas: desktop, android · Cambio: mejorado · Patrón: campos de texto → selectores con pasos
- Funciones: Primer episodio; Desfase; Vista previa en vivo; Usar los nombres; Guardar
- Lugar nuevo: pantalla `transferencias`: Hoja en Transferencias › Torrents › Archivos

## TOR-13 · Pares
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ordenar por bajada, subida, progreso; Ver qué significan las marcas
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Detalle › Pares

## TOR-14 · Leyenda de marcas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: hoja aparte → iconos con descripción
- Funciones: Explicación de D, U, I, P, E, X, H, S
- Lugar nuevo: pantalla `transferencias`: Información en línea en Pares

## TOR-15 · Rastreadores
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver a anunciar; Estado por rastreador
- Lugar nuevo: pantalla `transferencias`: Transferencias › Torrents › Detalle › Rastreadores

## TOR-16 · Descargas de torrents
- Plataformas: desktop, android · Cambio: fusionado · Patrón: tres listas para los mismos torrents → una lista de activas
- Funciones: Descargas en segundo plano; Esperando Wi-Fi, batería baja, pausado por el sistema (Android); Pausar o reanudar; Quitar descarga; Abrir torrent
- Lugar nuevo: pantalla `transferencias`: Transferencias › Activas (web y torrents juntos)

## TOR-17 · Aviso de descargas en segundo plano
- Plataformas: desktop, android · Cambio: mejorado · Patrón: pared de texto que tapa la lista → aviso breve sin bloquear
- Funciones: Entiendo; Ahora no; Pedir permiso de notificaciones (Android 13+)
- Lugar nuevo: pantalla `transferencias`: Diálogo al primer Descargar, desde la ficha o Transferencias

## TOR-18 · Quitar descarga
- Plataformas: desktop, android · Cambio: mejorado · Patrón: sin opción de conservar → igual que Eliminar torrent
- Funciones: Quitar descarga y archivos
- Lugar nuevo: pantalla `transferencias`: Diálogo en Transferencias › Activas

## TOR-19 · Historial de torrents
- Plataformas: desktop, android · Cambio: fusionado · Patrón: dos historiales → uno con filtro por tipo
- Funciones: Borrar historial; Abrir o volver a agregar; Reintentar; Origen: Reproducido, Descarga, Agregado, Magnet
- Lugar nuevo: pantalla `transferencias`: Transferencias › Historial (web y torrents)

## TOR-20 · Borrar historial de torrents
- Plataformas: desktop, android · Cambio: igual
- Funciones: Confirmar borrado de la lista
- Lugar nuevo: pantalla `transferencias`: Diálogo en Transferencias › Historial

## TOR-21 · Debrid
- Plataformas: desktop, android · Cambio: reubicado · Patrón: sección dentro de Torrents → pestaña de Fuentes
- Funciones: Usar debrid; Preferir servicio con caché; Descargar por el servicio con caché; Orden de servicios
- Lugar nuevo: pantalla `fuentes`: Fuentes › Debrid

## TOR-22 · Tarjeta de servicio debrid
- Plataformas: desktop, android · Cambio: mejorado · Patrón: desvincular sin confirmar → con confirmación y enlace para obtener la clave
- Funciones: Clave de API; Probar y vincular; Probar la clave; Preferir; Desvincular; Cuenta y vigencia premium
- Lugar nuevo: pantalla `fuentes`: Fuentes › Debrid

## TOR-23 · Agregar torrent
- Plataformas: desktop, android · Cambio: mejorado · Patrón: soltar solo sobre la hoja → soltar en cualquier parte; pegar del portapapeles
- Funciones: Magnet o info-hash; Elegir archivo .torrent; Soltar .torrent (escritorio); Vincular a una serie (buscar en AniList); Primer episodio y desfase; Conservar y descargar; Agregar
- Lugar nuevo: pantalla `transferencias`: Transferencias › Agregar; también capa global al soltar o pegar magnet

## TOR-24 · Vista previa de archivos al agregar
- Plataformas: desktop · Cambio: igual
- Funciones: Todos; Ninguno; Solo videos; Casilla por archivo; Reintentar; Estado de metadatos
- Lugar nuevo: pantalla `transferencias`: Hoja Agregar torrent

## TOR-25 · Ajustes de torrents
- Plataformas: desktop, android · Cambio: reubicado · Patrón: campo y deslizador duplicados en KiB/s → un control con unidades legibles; puerto con selector
- Funciones: Límite de bajada; Límite de subida; Solo Wi-Fi (Android); Subir con datos móviles (Android); Puerto de escucha; Reenvío UPnP; Conexiones máximas; DHT; PEX; Descubrimiento local LSD; Almacenamiento: app o carpeta (Android); Carpeta de descarga, usar la predeterminada, abrir (escritorio); Uso de disco; Tras ver: conservar o borrar; Descargar en segundo plano; Pausar con batería baja (Android)
- Lugar nuevo: pantalla `ajustes`: Ajustes › Torrents y descargas; acceso desde Transferencias

## TOR-26 · Más ajustes de torrents
- Plataformas: desktop, android · Cambio: fusionado · Patrón: horas en texto → selector de hora; debrid duplicado → un solo lugar
- Funciones: Interruptores de debrid; Límites por horario: desde, hasta, bajada, subida; Rastreadores propios; Liberar espacio automáticamente y límite; Precargar el siguiente episodio
- Lugar nuevo: pantalla `ajustes`: Ajustes › Torrents y descargas; Debrid solo en Fuentes › Debrid

## TOR-27 · Avisos y errores de torrents
- Plataformas: desktop, android · Cambio: igual
- Funciones: Avisos: eliminado, guardado, error; Errores: falta, movido, sin espacio, sin archivo, segundo plano apagado, incompleto, fuente o archivo inválido
- Lugar nuevo: pantalla `transferencias`: Avisos globales
- Riesgo: Varios textos apuntan a Torrents › Descargas: hay que reescribirlos con las rutas nuevas

## DL-01 · Página de descargas
- Plataformas: desktop, android · Cambio: fusionado · Patrón: 6 pestañas mezcladas → Activas, Descargado (por medio), Historial y Biblioteca local
- Funciones: Abrir carpeta (escritorio); Revisar archivos; Buscar en descargas; Pestañas Activas, Anime, Manga, Novelas, Historial, Biblioteca local; Uso de almacenamiento; Pedir permiso de notificaciones
- Lugar nuevo: pantalla `transferencias`: Transferencias

## DL-02 · Descargas activas
- Plataformas: desktop, android · Cambio: fusionado · Patrón: filas distintas web y torrent → una fila común y pausar todo
- Funciones: Pausar, reanudar o reintentar; Cancelar descarga; Abrir torrent; Pausar o reanudar torrent; Ver torrents pausados o completos
- Lugar nuevo: pantalla `transferencias`: Transferencias › Activas

## DL-03 · Cancelar descarga
- Plataformas: desktop, android · Cambio: mejorado · Patrón: Cancelar descarga y Cancelar → verbos distintos
- Funciones: Confirmar cancelación y borrado
- Lugar nuevo: pantalla `transferencias`: Diálogo en Transferencias › Activas

## DL-04 · Episodios descargados
- Plataformas: desktop, android · Cambio: reubicado · Patrón: borrar sin confirmar → con deshacer
- Funciones: Abrir ficha; Borrar todos los episodios; Reproducir sin conexión; Volver a descargar; Abrir carpeta (escritorio); Borrar episodio; Torrents completos o pausados
- Lugar nuevo: pantalla `transferencias`: Transferencias › Descargado › Anime

## DL-05 · Auto-descarga
- Plataformas: desktop, android · Cambio: reubicado · Patrón: tarjeta sobre la pestaña Anime → sección propia con borrar regla y póster
- Funciones: Simular; Activar regla; Perfil: Mejor calidad, Equilibrado, Ahorro; Sugerencias de Viendo: Agregar; Revisar ahora
- Lugar nuevo: pantalla `transferencias`: Transferencias › Auto-descarga

## DL-06 · Borrar episodios de un título
- Plataformas: desktop, android · Cambio: igual
- Funciones: Confirmar borrado
- Lugar nuevo: pantalla `transferencias`: Diálogo en Transferencias › Descargado

## DL-07 · Capítulos descargados
- Plataformas: desktop, android · Cambio: reubicado · Patrón: sin borrar todo → igual que Anime
- Funciones: Abrir capítulo sin conexión; Reintentar; Borrar
- Lugar nuevo: pantalla `transferencias`: Transferencias › Descargado › Manga y Novela

## DL-08 · Historial de descargas web
- Plataformas: desktop, android · Cambio: fusionado · Patrón: borrar sin confirmar → con confirmación como torrents
- Funciones: Borrar historial; Reproducir completada; Reintentar
- Lugar nuevo: pantalla `transferencias`: Transferencias › Historial

## DL-09 · Biblioteca local
- Plataformas: desktop, android · Cambio: reubicado · Patrón: lista plana → agrupada por serie con filtro sin emparejar
- Funciones: Importar videos; Escanear carpeta; Reproducir; Abrir carpeta (escritorio); Emparejar; Quitar
- Lugar nuevo: pantalla `transferencias`: Transferencias › Biblioteca local

## DL-10 · Emparejar video local
- Plataformas: desktop, android · Cambio: mejorado · Patrón: sin Cancelar → Cancelar y Desemparejar separados
- Funciones: Buscar título en AniList; Elegir resultado; Episodio; Guardar; Desemparejar
- Lugar nuevo: pantalla `transferencias`: Diálogo en Biblioteca local

## DL-11 · Quitar video local
- Plataformas: desktop, android · Cambio: igual
- Funciones: Conservar archivo; Borrar archivo; Cancelar
- Lugar nuevo: pantalla `transferencias`: Diálogo en Biblioteca local

## DL-12 · Descargar temporada en la ficha
- Plataformas: desktop, android · Cambio: reubicado · Patrón: botón bajo la lista → acción en la fila de acciones
- Funciones: Descargar temporada; N de M descargados
- Lugar nuevo: pantalla `ficha`: Ficha › acción Descargar visible

## DL-13 · Hoja Descargar temporada
- Plataformas: desktop, android · Cambio: mejorado · Patrón: código ISO y texto libre → selector de idioma y de servidor; rango con números
- Funciones: Rango de episodios; Idioma de audio preferido; Servidor preferido; Recordar idioma y servidor; Descargar N
- Lugar nuevo: pantalla `ficha`: Hoja desde Ficha › Descargar y multiselección de episodios

## DL-14 · Ajustes de descargas
- Plataformas: desktop, android · Cambio: fusionado · Patrón: texto libre → selectores; junto a los ajustes de torrents
- Funciones: Carpeta de descarga: cambiar, usar la predeterminada, abrir; Descargas simultáneas 1 a 5; Velocidad (escritorio); Solo Wi-Fi (Android); Límite de almacenamiento; Buscar episodios nuevos; Calidad de descarga; Liberar episodios vistos; Servidor preferido; Idioma de audio preferido
- Lugar nuevo: pantalla `ajustes`: Ajustes › Torrents y descargas

## DL-15 · Carpetas vigiladas
- Plataformas: desktop · Cambio: reubicado · Patrón: al final de Ajustes → junto a la Biblioteca local
- Funciones: Agregar carpeta; Abrir carpeta; Dejar de vigilar
- Lugar nuevo: pantalla `transferencias`: Transferencias › Biblioteca local (y Ajustes › Torrents y descargas)

## DL-16 · Avisos y errores de descargas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: solo aviso pasajero → resultado visible de Revisar y Simular
- Funciones: Avisos: episodios en cola, archivos rotos, videos agregados, simulación; Errores: DASH, límite, sin fuente, carpeta no disponible
- Lugar nuevo: pantalla `transferencias`: Avisos globales y resultado persistente en Transferencias
