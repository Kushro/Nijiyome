# Paridad del carril S11

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Manga, Novelas). 41 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## MNG-01 · Inicio de manga
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Recargar; Buscar manga; Limpiar búsqueda; Filtros; Carrusel destacado; Ver ficha; Seguir leyendo; Capítulos nuevos de lo que lees; Filas Tendencia, Popular, Mejor valorado; Reintentar fila; Géneros; Ocultar zona 18+; Mostrar zona 18+
- Lugar nuevo: pantalla `manga`: Manga › Catálogo

## MNG-02 · Búsqueda y filtros de manga
- Plataformas: desktop, android · Cambio: mejorado · Patrón: desplegables largos → chips y rango de años
- Funciones: Origen; Estado; Género; Año; Orden; Abrir resultado; Carga infinita; Reintentar
- Lugar nuevo: pantalla `manga`: Manga › Catálogo; también en Explorar

## MNG-03 · Confirmar zona 18+
- Plataformas: desktop, android · Cambio: igual
- Funciones: Tengo 18 años o más; Cancelar
- Lugar nuevo: pantalla `manga`: Manga › Catálogo › Zona 18+

## MNG-04 · Aviso de título para adultos
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir; Cancelar; Tocar para mostrar portada
- Lugar nuevo: pantalla `manga`: Manga › Catálogo y Ficha

## MNG-05 · Biblioteca de manga
- Plataformas: desktop, android · Cambio: fusionado · Patrón: pestaña Manga → medio Manga de Biblioteca
- Funciones: Todos; Filtrar por estado; Sincronizar con AniList; Seguir leyendo; Abrir ficha; Vincular AniList
- Lugar nuevo: pantalla `biblioteca`: Biblioteca › medio Manga

## MNG-06 · Ficha de manga
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Mostrar portada; Leer o continuar; Estado de lista; Progreso - y +; Expandir sinopsis; Géneros; Mostrar etiquetas con spoiler; Adaptación de anime; Series relacionadas; Recomendaciones
- Lugar nuevo: pantalla `manga`: Manga › Ficha

## MNG-07 · Estado de lectura
- Plataformas: desktop, android · Cambio: mejorado · Patrón: menú desplegable → selector visible
- Funciones: Leyendo; Planeo leer; Completado; En pausa; Abandonado; Releyendo; Quitar de la lista
- Lugar nuevo: pantalla `manga`: Manga › Ficha › selector de estado de 6 opciones

## MNG-08 · Capítulos de manga
- Plataformas: desktop, android · Cambio: igual
- Funciones: Recargar; Borrar caché; Seleccionar capítulos; Elegir fuente; Fuentes e idiomas; Filtrar capítulos; Orden; Agrupar por volumen; Idioma; Abrir capítulo; Marcar leído; Ir a Fuentes; Reintentar
- Lugar nuevo: pantalla `manga`: Manga › Ficha › Capítulos

## MNG-09 · Selección de capítulos
- Plataformas: desktop, android · Cambio: igual
- Funciones: Marcar capítulo; Seleccionar todo; Descargar
- Lugar nuevo: pantalla `manga`: Manga › Ficha › multiselección

## MNG-10 · Fuentes e idiomas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Idiomas preferidos; Automático; Elegir fuente; Preferir fuente para el título; Ir a Fuentes
- Lugar nuevo: pantalla `manga`: Manga › Ficha › hoja Fuentes e idiomas

## MNG-11 · Avisos de ficha de manga
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ir a descargas; Abrir fuentes e idiomas; Sin capítulo; Error
- Lugar nuevo: pantalla `manga`: Manga › Ficha

## MNG-12 · Lector de manga
- Plataformas: desktop, android · Cambio: igual
- Funciones: Toque izquierdo y derecho cambia página; Toque central muestra barras; Deslizar páginas; Desplazamiento vertical; Doble toque zoom; Pellizcar zoom; Arrastrar con zoom; Ctrl + rueda zoom; Teclas; Esc; Siguiente capítulo; Reintentar página; Cambiar fuente
- Lugar nuevo: pantalla `lector`: Lector de manga

## MNG-13 · Barra superior del lector
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Capítulos; Ajustes del lector; Más opciones; Pantalla completa; Cambiar fuente; Guardar imagen de página; Todos los ajustes del lector
- Lugar nuevo: pantalla `lector`: Lector › barra superior

## MNG-14 · Barra inferior del lector
- Plataformas: desktop, android · Cambio: igual
- Funciones: Deslizador de página; Capítulo anterior; Capítulo siguiente; Páginas; Desplazamiento automático
- Lugar nuevo: pantalla `lector`: Lector › barra inferior

## MNG-15 · Ajustes rápidos del lector
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 18 filas fijas → filas según el modo
- Funciones: Modo de lectura; Dirección; Doble página; Ajuste de página; Ancho máximo; Separación; Recortar bordes; Fondo; Brillo del sistema; Brillo; Cargar capítulo entero; Páginas a precargar; Desplazamiento infinito; Velocidad de desplazamiento; Pantalla completa al abrir; Tocar oculta controles; Mostrar progreso; Atajos de zoom; Todos los ajustes
- Lugar nuevo: pantalla `lector`: Lector › hoja Ajustes

## MNG-16 · Capítulos en el lector
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir capítulo
- Lugar nuevo: pantalla `lector`: Lector › hoja Capítulos

## MNG-17 · Cambiar fuente en el lector
- Plataformas: desktop, android · Cambio: igual
- Funciones: Elegir otra fuente; Reintentar
- Lugar nuevo: pantalla `lector`: Lector › hoja Cambiar fuente

## MNG-18 · Tira de páginas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Saltar a página
- Lugar nuevo: pantalla `lector`: Lector › hoja Páginas

## MNG-19 · Avisos del lector
- Plataformas: desktop, android · Cambio: igual
- Funciones: Fuente sin capítulo; Último capítulo; Página guardada; Error
- Lugar nuevo: pantalla `lector`: Lector

## MNG-20 · Ajustes del lector de manga
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Ajustes › Lector → Lectores y narrador
- Funciones: Idioma del título de manga; Las 18 filas del lector; Atajo por acción; Restablecer atajos
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Manga

## MNG-21 · Capturar tecla
- Plataformas: desktop, android · Cambio: igual
- Funciones: Grabar tecla; Guardar; Cancelar
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Atajos

## NOV-01 · Inicio de novelas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Recargar; Buscar novelas; Limpiar búsqueda; Filtros; Destacado; Seguir leyendo; Novedades de lo que sigues; Filas Tendencia y Popular; Reintentar fila; Géneros
- Lugar nuevo: pantalla `manga`: Novelas › Catálogo

## NOV-02 · Búsqueda y filtros de novelas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: desplegables → chips
- Funciones: Género; Estado; Origen; Orden; Abrir resultado; Carga infinita; Reintentar
- Lugar nuevo: pantalla `manga`: Novelas › Catálogo; también en Explorar

## NOV-03 · Biblioteca de novelas
- Plataformas: desktop, android · Cambio: fusionado · Patrón: pestaña Novelas → medio Novela de Biblioteca
- Funciones: Todos; Filtrar por estado; Sincronizar con AniList; Seguir leyendo; Abrir ficha; Vincular AniList
- Lugar nuevo: pantalla `biblioteca`: Biblioteca › medio Novela

## NOV-04 · Ficha de novela
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Leer o continuar; Estado de lista; Progreso - y +; Expandir sinopsis; Ver todos los traductores; Géneros
- Lugar nuevo: pantalla `manga`: Novelas › Ficha

## NOV-05 · Estado de lectura (novela)
- Plataformas: desktop, android · Cambio: mejorado · Patrón: menú desplegable → selector visible
- Funciones: Leyendo; Planeo leer; Completado; En pausa; Abandonado; Releyendo; Quitar de la lista
- Lugar nuevo: pantalla `manga`: Novelas › Ficha › selector de estado de 6 opciones

## NOV-06 · Capítulos de novela
- Plataformas: desktop, android · Cambio: igual
- Funciones: Revisar todas las fuentes; Recargar; Seleccionar capítulos; Elegir fuente; Filtrar capítulos; Orden; Traductor; Idioma; Abrir capítulo; Marcar leído; Ir a Fuentes; Reintentar
- Lugar nuevo: pantalla `manga`: Novelas › Ficha › Capítulos

## NOV-07 · Selección de capítulos (novela)
- Plataformas: desktop, android · Cambio: igual
- Funciones: Marcar capítulo; Seleccionar todo; Descargar
- Lugar nuevo: pantalla `manga`: Novelas › Ficha › multiselección

## NOV-08 · Avisos de ficha de novela
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ir a descargas; Sin capítulo; Error
- Lugar nuevo: pantalla `manga`: Novelas › Ficha

## NOV-09 · Lector de novelas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Tocar muestra barras; Tocar párrafo al narrar; Desplazamiento continuo; Siguiente capítulo; Reintentar; Esc
- Lugar nuevo: pantalla `lector`: Lector de novela

## NOV-10 · Barra superior del lector de novela
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Capítulos; Texto y diseño; Pantalla completa
- Lugar nuevo: pantalla `lector`: Lector de novela › barra superior

## NOV-11 · Barra inferior y narración
- Plataformas: desktop, android · Cambio: mejorado · Patrón: errores sin enlace → acceso a voz y motor desde el lector
- Funciones: Capítulo anterior; Leer en voz alta; Pausar; Reanudar; Detener; Velocidad de narración; Capítulo siguiente
- Lugar nuevo: pantalla `lector`: Lector de novela › barra inferior con narrador

## NOV-12 · Texto y diseño
- Plataformas: desktop, android · Cambio: igual
- Funciones: Fuente; Importar fuente; Tamaño; Interlineado; Espacio entre párrafos; Ancho; Colores; Justificar; Vista previa; Desplazamiento continuo; Barra de progreso; Pantalla completa; Pantalla encendida; Todos los ajustes
- Lugar nuevo: pantalla `lector`: Lector de novela › hoja Texto y diseño

## NOV-13 · Capítulos en el lector de novela
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir capítulo
- Lugar nuevo: pantalla `lector`: Lector de novela › hoja Capítulos

## NOV-14 · Aviso del lector de novela
- Plataformas: desktop, android · Cambio: igual
- Funciones: Último capítulo
- Lugar nuevo: pantalla `lector`: Lector de novela

## NOV-15 · Ajustes del lector de novela
- Plataformas: desktop, android · Cambio: reubicado · Patrón: debajo de todo el manga → sección propia
- Funciones: Fuente; Fuente instalada; Importar TTF u OTF; Quitar fuente importada; Tamaño; Interlineado; Espacio entre párrafos; Ancho máximo; Colores; Justificar; Vista previa; Desplazamiento continuo; Barra de progreso; Pantalla completa al abrir; Pantalla encendida
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Novela

## NOV-16 · Fuente instalada (novela)
- Plataformas: desktop · Cambio: igual
- Funciones: Buscar familia; Elegir familia; Usar; Cancelar
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Novela › Fuente

## NOV-17 · Narrador
- Plataformas: desktop, android · Cambio: mejorado · Patrón: solo en Ajustes → también desde el lector
- Funciones: Motor; Estado del motor; Voz; Id de voz; Velocidad; Tono; Resaltar párrafo; URL del servidor; Modelo; Guardar servidor y modelo; Clave de API; Quitar clave; Probar voz; Detener prueba
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Narrador, con acceso desde el lector

## NOV-18 · Voz local KittenTTS
- Plataformas: desktop · Cambio: igual
- Funciones: Descargar modelo; Quitar modelo
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Narrador

## B-OCULTA-01 · Fuente de manga preferida global
- Plataformas: desktop, android · Cambio: nuevo-acceso · Patrón: solo en código → fila en Ajustes
- Funciones: Elegir fuente de manga preferida para todos los títulos
- Lugar nuevo: pantalla `ajustes`: Ajustes › Lectores y narrador › Manga › Fuente preferida

## NOTA-B-lectores-cross · Transversal de lectores
- Plataformas: desktop, android · Cambio: igual
- Funciones: 12 atajos del lector de manga; Mapa de gestos por modo; Novela solo con Esc
- Lugar nuevo: pantalla `lector`: Lector de manga y novela
