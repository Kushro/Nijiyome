# Paridad del carril S10

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Ajustes, Bienvenida). 26 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## SRCH-10 · Extras de Descubrir
- Plataformas: desktop, android · Cambio: igual
- Funciones: Idioma de títulos; Mostrar 18+; Contenido maduro; Proveedores de metadatos; Borrar búsquedas recientes
- Lugar nuevo: pantalla `ajustes`: Ajustes › Metadatos y descubrir

## SET-00 · Contenedor de Ajustes
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 11 secciones con resúmenes en español fijo → 14 categorías localizadas
- Funciones: Lista de secciones con icono y título; Panel maestro-detalle en ventana ancha; Hub por tarjetas en teléfono; Resumen en vivo por sección; Abrir sección por enlace directo; Avisos: guardado, sincronizado, caché borrada, error; Reiniciar la app tras restablecer
- Lugar nuevo: pantalla `ajustes`: Ajustes: 14 categorías con resumen en vivo

## SET-01 · Buscar ajustes
- Plataformas: desktop, android · Cambio: mejorado · Patrón: lleva a la sección → salta y resalta la fila exacta
- Funciones: Campo Buscar ajuste con borrar; Resultados con sección y subtítulo; Puntaje por prefijo y palabra; Estado sin coincidencias con ejemplos; Abrir la sección del resultado
- Lugar nuevo: pantalla `ajustes`: Buscador de Ajustes y paleta Ctrl+K con salto exacto a la fila

## SET-02 · Ajustes en superposición
- Plataformas: desktop · Cambio: mejorado · Patrón: Ctrl+P → Ctrl+,
- Funciones: Abrir con Ctrl+P o Ctrl+,; Abrir desde el engranaje de la barra de título; Abrir desde la bandeja; Abrir desde la paleta; Abrir en Link desde el monitor; Cerrar con Esc o ×
- Lugar nuevo: pantalla `ajustes`: Superposición global con Ctrl+, (Ctrl+P hoy)
- Riesgo: Ctrl+P deja de ser el atajo principal: conviene mantenerlo como alias

## SET-03 · Entradas de Ajustes en la paleta
- Plataformas: desktop · Cambio: mejorado · Patrón: solo escritorio y en español fijo → global, localizada y con salto exacto
- Funciones: Abrir Configuración; Alternar negro AMOLED; Cambiar tema; Alternar barra lateral compacta; Abrir registros de error; Ir a Descargas, Torrents, Extensiones; Cada ajuste del catálogo como entrada
- Lugar nuevo: pantalla `inicio`: Paleta Ctrl+K global; también búsqueda en Android

## SET-04 · Aviso de ajuste encontrado
- Plataformas: desktop, android · Cambio: mejorado · Patrón: franja en la cabecera → desplazamiento y destello en la fila
- Funciones: Franja con el ajuste buscado; Ocultar aviso
- Lugar nuevo: pantalla `ajustes`: Resaltado de la fila destino al saltar desde la búsqueda

## SET-APP · Apariencia
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 11 segmentados seguidos en texto → subgrupos con vistas previas visuales
- Funciones: Vista previa en vivo; Paleta (20 temas); Color dinámico (Android 12+); Modo: Sistema, Claro, Oscuro; Negro AMOLED; Contraste: Estándar, Medio, Alto; Idioma: Sistema, Inglés, Español; Preset de aspecto: Nijiyome, Cine editorial, Denso, Calmo; Inicio: Destacado, Carrusel, Solo filas; Estilo de página: Estándar, Editorial; Cabecera de la ficha (4 diseños visuales); Tarjetas: Clásica, Detallada, Solo póster; Tamaño de tarjeta: Chico a Extra grande; Densidad: Cómoda, Compacta; Animaciones: Expresivas, Sutiles, Apagadas; Episodios: Tarjetas, Lista, Compacta; Relacionados: Grafo, Lista; Calendario: Semana, Agenda, Mes, Hoy; Menú lateral: Con nombres, Solo iconos (ancho); Rotar el banner; Segundos por título (4 a 30 s); Mostrar el saludo
- Lugar nuevo: pantalla `ajustes`: Ajustes › Apariencia, con subgrupos Inicio, Ficha, Listas y Navegación

## SET-PLY · Reproductor
- Plataformas: desktop, android · Cambio: reubicado · Patrón: una sección de unas 30 filas → cuatro categorías con subtítulos; códigos ISO → nombres de idioma
- Funciones: Motor: Media3, libmpv, Externo; Reproductor externo: Preguntar, mpv-android, VLC (Android); Ruta de mpv y estado (escritorio); Modo mejorado mpv-anime-build y descarga (escritorio); Interfaz uosc (escritorio); Preset de escalado por defecto (Anime4K, FSR, FSRCNNX, CAS, NVScaler); Calidad de escalado: Rápida, Equilibrada, Alta; Deband; Ajustes automáticos; Decodificador: Auto, HW, HW+, SW; Estado del decodificador VAAPI (escritorio); Suavizado (interpolación); Verificar certificados libmpv (Android); Estilo del reproductor: Cine, Minimalista (Android); Selección de fuente: Automática, Manual; Tipo preferido: Cualquiera, Webstream, Torrent; Calidad mínima: 480p, 720p, 1080p; Subtítulos preferidos: ES, EN, PT; Audio preferido: JA, ES, EN; Siguiente episodio automático; Saltar intro con AniSkip; Navegador oculto para extensiones: ruta y Probar (escritorio); Estilo de subtítulos: resumen y Editar; Ir a Extensiones; Generación de cuadros en el teléfono: estado, importar Lossless.dll, usar al reproducir, multiplicador, perfil, cambiar tasa de refresco, GPU no probadas, estadísticas (Android); Generación de cuadros lsfg-vk: interruptor, multiplicador, modo rendimiento, archivo de shader (escritorio)
- Lugar nuevo: pantalla `ajustes`: Repartido en Reproducción, Mejoras de video, Subtítulos y audio y Fuentes de episodios

## SET-MET · Metadatos
- Plataformas: desktop, android · Cambio: reubicado · Patrón: flechas para ordenar → lista arrastrable con interruptor; avisos fuera de Metadatos
- Funciones: Prioridad de proveedores (subir y bajar); Tamaño de la caché de metadatos; Borrar caché (escritorio); Conservar detalles: 6 h, 24 h, 3 d, 7 d; Recordatorios de emisión; Notificaciones de AniList
- Lugar nuevo: pantalla `ajustes`: Ajustes › Metadatos y descubrir; los dos avisos pasan a Ajustes › Notificaciones

## SET-ACC · Listas y cuentas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: un panel largo → tarjetas por cuenta y sincronización visible en Biblioteca
- Funciones: Vincular y desvincular AniList y MyAnimeList; Estado de sesión y sesión vencida; Elegir modo de sincronización: Combinar, Importar, Exportar; Estado de sincronización y pendientes; Sincronizar listas ahora; Conflictos: Conservar los míos, Usar los del servicio; Actualizar progreso automáticamente; Agregar a Viendo al empezar; Enviar cambios a las cuentas; Exportar lista JSON; Exportar CSV (escritorio); Importar lista (JSON, MAL XML, AniList, Tatakai); Confirmar desvinculación
- Lugar nuevo: pantalla `ajustes`: Ajustes › Listas y cuentas; estado de sincronización también en Biblioteca

## SET-LNK · Link en el teléfono
- Plataformas: android · Cambio: reubicado · Patrón: ajustes y pantalla separados → Link para operar, Ajustes para preferencias
- Funciones: Abrir Dispositivo vinculado; Procesar por defecto en: Teléfono, PC, Mixto; Sincronizar historial con la PC; Sincronizar ahora
- Lugar nuevo: pantalla `ajustes`: Ajustes › Nijiyome Link; emparejar y estado en la pantalla Link

## SET-LNKSRV · Link en esta PC (servidor)
- Plataformas: desktop · Cambio: reubicado · Patrón: dos servidores en un panel → secciones separadas en Link; puertos y carpeta con selectores propios
- Funciones: Nijiyome Link en esta PC (interruptor y estado); Probar otro puerto; Regenerar identidad ante error; Huella digital; Aviso sin FFmpeg; Sesiones activas con caché RAM y Detener; Emparejar un teléfono: QR, código, cuenta regresiva, copiar; Dispositivos emparejados: renombrar, revocar, modo de historial, usar como fuente; Capacidades de esta PC; Nombre que ven los teléfonos; Anunciar en la red (mDNS); Carpeta de biblioteca; Puerto (47321); Caché de reproducción en RAM: 512 MB a 4 GB; Firewall: permitir en red privada o puertos a abrir; Regenerar identidad; Servidor de extensiones en esta PC; Puerto del servidor de extensiones (8099); Nuevo código para un teléfono; Contraseña de visor; Dispositivos del servidor de extensiones: revocar; Registro de solicitudes y errores
- Lugar nuevo: pantalla `link`: Link › Este PC, Dispositivos, Servidor de extensiones y Registros; preferencias en Ajustes › Nijiyome Link

## SET-NET · Red (escritorio)
- Plataformas: desktop · Cambio: pendiente · Patrón: guardado explícito → guardado coherente con estado sin guardar
- Funciones: Proxy de salida; Tipo: SOCKS5, HTTP; Host y puerto; Usuario y contraseña; Probar y Guardar; Nota: torrents solo con SOCKS5; FlareSolverr: URL, Probar, Guardar; Proxy local de medios
- Lugar nuevo: pantalla `ajustes`: Sin categoría propia entre las 14; candidata: Sistema solo en escritorio o Fuentes de episodios
- Riesgo: La nueva lista de 14 categorías no nombra Red. El dueño debe decidir si va en Sistema (escritorio) o en Fuentes de episodios (FlareSolverr).

## SET-TOR · Entrada de Torrents
- Plataformas: desktop, android · Cambio: fusionado · Patrón: dos enlaces sueltos → categoría única con los controles reales
- Funciones: Ir a Ajustes de torrents; Ir a Debrid
- Lugar nuevo: pantalla `ajustes`: Ajustes › Torrents y descargas; Debrid en Fuentes › Debrid

## SET-DL · Entrada de Descargas
- Plataformas: desktop, android · Cambio: fusionado · Patrón: Torrents y Descargas separados → una categoría
- Funciones: Sección Descargas (ver DL-14 y DL-15)
- Lugar nuevo: pantalla `ajustes`: Ajustes › Torrents y descargas

## SET-RDR · Entrada de Lectores
- Plataformas: desktop, android · Cambio: igual
- Funciones: Lector de manga, novela y narrador (ver SET-RDR-MANGA, NOVEL y TTS)
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador

## SET-DAT · Datos
- Plataformas: desktop, android · Cambio: reubicado · Patrón: integración con el sistema bajo Datos → categoría Sistema propia
- Funciones: Aviso de ubicación de datos; Abrir carpeta de datos (escritorio); Barra de almacenamiento con leyenda; Reintentar medición; Telemetría: ninguna; Ícono en la bandeja (escritorio); Cerrar a la bandeja (escritorio); Iniciar con el sistema (escritorio); Iniciar minimizado (escritorio); Discord Rich Presence (escritorio); Barra de título nativa (escritorio); Abrir magnet y .torrent con Nijiyome (escritorio); Aviso de llavero y secretos ilegibles; Borrar caché con confirmación; Exportar historial; Borrar historial (también en la PC); Restablecer la app (RESET y conservar torrents en escritorio)
- Lugar nuevo: pantalla `ajustes`: Ajustes › Datos y almacenamiento; filas del sistema a Ajustes › Sistema solo en escritorio

## SET-ABT · Acerca de
- Plataformas: desktop, android · Cambio: mejorado · Patrón: registros solo en Acerca de → también en Link
- Funciones: Versión y componentes; Licencia AGPL; Buscar nuevas versiones (interruptor); Buscar actualizaciones ahora; Licencias de terceros; Registros: actualizar, abrir carpeta, borrar, compartir; Filtrar registros por dispositivo; Enviar registros a la PC (Android); Filtro por gravedad; Filtrar por texto
- Lugar nuevo: pantalla `ajustes`: Ajustes › Acerca de; registros también en Link › Registros

## SET-ONB · Asistente de primer arranque
- Plataformas: desktop, android · Cambio: mejorado · Patrón: solo modo y paso de Link sin acción → elegir paleta y emparejar desde el asistente
- Funciones: Pasos: Bienvenida, Apariencia, Reproducción, Descargas, Listas, Link, Permisos, Sistema, Listo; Barra de progreso y Omitir; Atrás, Empezar, Siguiente, Terminar; Idioma y modo; Motor, fuente, tipo, calidad, idiomas; Vincular cuentas y modo de sincronización; Permitir notificaciones (Android 13+); Carpeta de descargas y qué hacer tras ver (escritorio); Filas del sistema y buscar versiones (escritorio)
- Lugar nuevo: pantalla `bienvenida`: Primer arranque

## SET-PLY-EXTRA · Extras del reproductor
- Plataformas: desktop, android · Cambio: reubicado · Patrón: bloques apilados → tarjeta de motor de traducción con estado; códigos → nombres de idioma
- Funciones: Cuenta regresiva del siguiente episodio (3 a 30 s); Volumen estable; Reproducción en segundo plano (Android); Subtítulo secundario (12 idiomas); Motor de traducción: Google, DeepL, DeepLX, Azure, compatible con OpenAI; Modo: En vivo, Pretraducir; Idioma de destino (14); URL del servidor; Modelo; Región; Guardar conexión; Clave de API: guardar y quitar; Probar traducción; Caché de traducción y borrar; Atajos del teclado: cambiar tecla (13 en Android, 20 en escritorio); Restaurar atajos; Diálogo para capturar tecla
- Lugar nuevo: pantalla `ajustes`: Reproducción (cuenta regresiva, volumen, segundo plano, atajos); Subtítulos y audio (secundario y traducción)

## SET-PLY-SUBS · Estilo de subtítulos
- Plataformas: desktop, android · Cambio: mejorado · Patrón: estilos en texto → miniaturas visuales; vista previa fija y opacidad unificada
- Funciones: Vista previa en vivo; Estilos rápidos: Predeterminado, Contorno, Caja, Amarillo; Tamaño 50 a 250 %; Fuente: Outfit, Space Grotesk, Sistema, importadas; Elegir fuente instalada (escritorio); Importar fuentes TTF u OTF; Borrar fuente importada; Negrita; Cursiva; Alineación; Color del texto y opacidad; Contorno y grosor; Color del contorno; Caja de fondo y transparencia; Color de fondo; Sombra: distancia y color; Posición vertical; Margen inferior; Margen lateral; Interlineado; Suavizado de bordes; Estilo ASS: Original, Aplicar el mío, Texto plano; Aplicar tamaño a ASS; Restablecer todo
- Lugar nuevo: pantalla `ajustes`: Ajustes › Subtítulos y audio; acceso rápido desde la hoja Pistas del reproductor

## SET-PLY-SRC · Fuentes embebidas
- Plataformas: desktop, android · Cambio: reubicado · Patrón: en medio del reproductor y oculto sin Chromium → con las fuentes y desactivado con motivo
- Funciones: Fuentes embebidas; Tiempo de espera de la página (5 a 60 s)
- Lugar nuevo: pantalla `ajustes`: Ajustes › Fuentes de episodios

## SET-MET-DISC · Descubrir
- Plataformas: desktop, android · Cambio: fusionado · Patrón: orden y activación separados → una lista ordenable con interruptores
- Funciones: Idioma del título: Romaji, Inglés, Nativo; Mostrar contenido 18+; Contenido maduro: Mostrar, Difuminar, Avisar; Proveedores: AniList, ani.zip, Kitsu, MyAnimeList, Shikimori, Tenrai (activar y estado); Borrar búsquedas recientes
- Lugar nuevo: pantalla `ajustes`: Ajustes › Metadatos y descubrir

## SET-RDR-MANGA · Lector de manga
- Plataformas: desktop, android · Cambio: mejorado · Patrón: segmentos de texto → selectores visuales y filas según el modo
- Funciones: Idioma del título de manga; Modo: Paginado, Vertical, Webtoon; Dirección: derecha a izquierda, izquierda a derecha; Doble página; Ajuste de página: Ancho, Alto, Pantalla, Natural; Ancho máximo; Separación entre páginas; Recortar bordes; Fondo: Negro, Gris, Blanco, Tema; Brillo del sistema; Brillo; Cargar el capítulo completo; Páginas a precargar; Desplazamiento infinito; Velocidad de autodesplazamiento; Pantalla completa al abrir; Tocar oculta los controles; Mostrar progreso de página; Atajos de zoom; Atajos del lector (12 acciones); Restablecer atajos
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Manga; los mismos controles en la hoja del lector

## SET-RDR-NOVEL · Lector de novelas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: una sola tanda → subgrupos con vista previa fija y muestras de color
- Funciones: Fuente: Serif, Sans, Georgia, Lora, Dislexia, Mono (escritorio), Sistema, Importada; Fuente instalada (escritorio); Importar TTF u OTF; Quitar fuente importada; Tamaño de texto; Interlineado; Espacio entre párrafos; Ancho máximo; Colores: Tema, Sepia, Blanco, Gris, Negro; Justificar texto; Vista previa; Desplazamiento continuo; Barra de progreso; Pantalla completa al abrir; Mantener la pantalla encendida
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Novela (Tipografía y Flujo de lectura)

## SET-RDR-TTS · Narrador
- Plataformas: desktop, android · Cambio: mejorado · Patrón: menú de voces plano → selector con búsqueda y prueba por voz
- Funciones: Motor: Voz del sistema, KittenTTS (escritorio), OpenAI, ElevenLabs, Servidor local; Estado del motor; Descargar o quitar KittenTTS (escritorio); Voz; Id de voz; Velocidad; Tono (escritorio); Resaltar el párrafo leído; URL del servidor; Modelo; Clave de API: guardar y quitar; Probar voz y Detener
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Narrador
