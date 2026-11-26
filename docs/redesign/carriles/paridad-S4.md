# Paridad del carril S4

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Ficha). 26 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## DET-01 · Ficha: contenedor y estados
- Plataformas: desktop, android · Cambio: igual
- Funciones: Tema por color del título; Cargando y error con Reintentar; Aviso de proveedor limitado; Discord en escritorio
- Lugar nuevo: pantalla `ficha`: Ficha

## DET-01a · Filtro 18+ de la ficha
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Mostrar y recordar por título
- Lugar nuevo: pantalla `ficha`: Ficha

## DET-02 · Fichas anidadas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir relacionado dentro de la ficha; Atrás vuelve al anterior
- Lugar nuevo: pantalla `ficha`: Ficha

## DET-03 · Barra superior
- Plataformas: desktop, android · Cambio: igual
- Funciones: Atrás flotante y fija; Título fijo; Ep. N; Pestañas fijas
- Lugar nuevo: pantalla `ficha`: Ficha

## DET-04 · Cabecera
- Plataformas: desktop, android · Cambio: igual
- Funciones: Seleccionar título; Copiar título; AniList y MAL; Más o menos sinopsis; Revelar portada
- Lugar nuevo: pantalla `ficha`: Ficha, cabecera

## DET-05 · Diseños de cabecera y datos
- Plataformas: desktop, android · Cambio: igual
- Funciones: Línea, Datos, Siguiente, Datos y siguiente; Estado, próximo ep., episodios, año, puntaje
- Lugar nuevo: pantalla `ficha`: Ficha, cabecera

## DET-06 · Botón Ver dividido
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver o continuar con fuente recordada; Abrir selector de fuente
- Lugar nuevo: pantalla `ficha`: Ficha; la flecha abre Fuentes del episodio

## DET-07 · Acciones de cabecera
- Plataformas: desktop, android · Cambio: mejorado · Patrón: menú de lista → selector de estado visible
- Funciones: Lista; Favorito; Seguir; Tráiler; Más
- Lugar nuevo: pantalla `ficha`: Fila de acciones visible: selector de estado de 6 opciones, Favorito, Seguir, Tráiler, Descargar, Compartir, Enviar

## DET-08 · Menú y editor de lista
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver LIB-06 y LIB-07
- Lugar nuevo: pantalla `ficha`: Selector de estado y editor de entrada en la ficha

## DET-09 · Menú Más
- Plataformas: desktop, android · Cambio: reubicado · Patrón: menú escondido → acciones visibles
- Funciones: Enviar a dispositivo; Descargar episodio N; Descargar temporada; Sin fuente torrent; Compartir
- Lugar nuevo: pantalla `ficha`: Fila de acciones visible bajo el título

## DET-10 · Tarjeta Siguiente
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver dividido; Línea de fuente
- Lugar nuevo: pantalla `ficha`: Ficha, cabecera

## DET-11 · Línea de fuente
- Plataformas: desktop, android · Cambio: igual
- Funciones: Cambiar fuente; Abrir o gestionar extensiones; Reintentar
- Lugar nuevo: pantalla `fuentes-episodio`: Ficha; abre Fuentes del episodio; extensiones → Fuentes

## DET-12 · Panel de fuente (columna lateral)
- Plataformas: desktop, android · Cambio: igual
- Funciones: Selección automática o manual; Fuente elegida; Descargar fuente torrent; Abrir extensiones; Reintentar
- Lugar nuevo: pantalla `fuentes-episodio`: Columna lateral de la ficha; ajuste → Ajustes › Fuentes de episodios

## DET-13 · Enviar a dispositivo
- Plataformas: desktop, android · Cambio: reubicado · Patrón: menú Más y panel lateral → acción visible
- Funciones: Emparejar un PC; Enviar al PC; Volver a comprobar; Enviar a cada teléfono
- Lugar nuevo: pantalla `ficha`: Acción visible Enviar a dispositivo

## DET-14 · Pestañas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 4 pestañas → 5 con Reseñas
- Funciones: Episodios, Info, Personajes, Relacionados
- Lugar nuevo: pantalla `ficha`: Episodios, Información, Personajes, Relacionados, Reseñas

## DET-15 · Pestaña Episodios
- Plataformas: desktop, android · Cambio: mejorado · Patrón: marcar distinto por vista → multiselección y acciones iguales en las 3 vistas
- Funciones: Buscar episodio; Tarjetas, Lista, Compacto; Rangos de 100; Episodios anteriores; Reproducir episodio; Marcar visto o no visto; Descargar episodio
- Lugar nuevo: pantalla `ficha`: Ficha › Episodios con multiselección

## DET-16 · Pestaña Info
- Plataformas: desktop, android · Cambio: igual
- Funciones: Géneros; Estudios; Etiquetas con spoiler; Ver todas las etiquetas; Tráiler; Enlaces oficiales
- Lugar nuevo: pantalla `ficha`: Ficha › Información

## DET-17 · Pestaña Personajes
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir personaje; Cargar más; Staff
- Lugar nuevo: pantalla `ficha`: Ficha › Personajes

## DET-18 · Relacionados y grafo
- Plataformas: desktop, android · Cambio: igual
- Funciones: Temporadas; Grafo o Lista; Abrir nodo; Grafo a pantalla completa; Mover y zoom; Acercar, alejar, ajustar; Cerrar; Relaciones; Recomendaciones
- Lugar nuevo: pantalla `ficha`: Ficha › Relacionados

## DET-19 · Mensajes de la ficha
- Plataformas: desktop, android · Cambio: igual
- Funciones: Usando fuente; Descarga agregada; Por Debrid; Respaldo P2P; Errores de envío, lista y favorito
- Lugar nuevo: pantalla `ficha`: Snackbars de la ficha

## DET-20 · Selector de fuente
- Plataformas: desktop, android · Cambio: mejorado · Patrón: lista → facetas o comparador, manual o automática
- Funciones: Elegir y recordar fuente; Reintentar; Ajustes de reproductor; Descargar fuente torrent
- Lugar nuevo: pantalla `fuentes-episodio`: Hoja Fuentes del episodio con facetas o comparador

## CHAR-01 · Página de personaje
- Plataformas: desktop, android · Cambio: igual
- Funciones: Atrás; Favorito de personaje; Más o menos; Apariciones
- Lugar nuevo: pantalla `ficha`: Ficha › Personajes y Explorar › Personajes

## SOC-04 · Reseñas de AniList
- Plataformas: desktop, android · Cambio: reubicado · Patrón: bajo Episodios → pestaña propia
- Funciones: Escribir reseña; Leer más; Abrir en AniList; Cargar más
- Lugar nuevo: pantalla `ficha`: Ficha › Reseñas

## A-OCULTA-01 · Recordatorio por título (FollowShowRow)
- Plataformas: desktop, android · Cambio: nuevo-acceso · Patrón: código sin uso → interruptor en Seguir
- Funciones: Avisarme cuando salga un episodio
- Lugar nuevo: pantalla `ficha`: Acción Seguir de la ficha con recordatorio por título

## A-OCULTA-02 · Menú de descarga de la ficha (DownloadMenuButton)
- Plataformas: desktop, android · Cambio: nuevo-acceso · Patrón: código sin uso → acción visible
- Funciones: Descargar episodio; Descargar temporada
- Lugar nuevo: pantalla `ficha`: Acción Descargar visible en la ficha

## A-OCULTA-03 · Enviar al teléfono en escritorio (SendToPhoneButton)
- Plataformas: desktop · Cambio: nuevo-acceso · Patrón: solo panel lateral → acción visible
- Funciones: Enviar a un teléfono emparejado
- Lugar nuevo: pantalla `ficha`: Acción Enviar a dispositivo en la ficha
