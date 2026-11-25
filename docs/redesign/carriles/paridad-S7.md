# Paridad del carril S7

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Biblioteca, Perfil). 12 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## LIB-01 · Biblioteca: cabecera y pestañas
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 7 pestañas mezcladas → medio y 4 vistas; Torrents sale
- Funciones: Sincronizar ahora; Cambiar pestaña; Local o sincronizado
- Lugar nuevo: pantalla `biblioteca`: Biblioteca con medio Anime, Manga, Novela; Continuar, Estados, Favoritos, Historial

## LIB-02 · Continuar
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reanudar episodio; Explorar anime si está vacío
- Lugar nuevo: pantalla `biblioteca`: Biblioteca › Continuar

## LIB-03 · Historial
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir ficha; Explorar anime si está vacío
- Lugar nuevo: pantalla `biblioteca`: Biblioteca › Historial; en Android también en Tú

## LIB-04 · Listas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Estado de 6 opciones; Filtrar por título; Ordenar; Vincular AniList o MAL; Pendientes de sincronizar; Abrir ficha; Editar entrada
- Lugar nuevo: pantalla `biblioteca`: Biblioteca › Estados de lista

## LIB-05 · Favoritos
- Plataformas: desktop, android · Cambio: igual
- Funciones: Personajes favoritos; Quitar personaje; Títulos; Editar entrada
- Lugar nuevo: pantalla `biblioteca`: Biblioteca › Favoritos

## LIB-06 · Editor de entrada
- Plataformas: desktop, android · Cambio: igual
- Funciones: Estado; Progreso menos y más; Puntaje; Favorito; Quitar de la lista
- Lugar nuevo: pantalla `biblioteca`: Editor de entrada, desde Biblioteca y la ficha

## LIB-07 · Botón y menú de lista
- Plataformas: desktop, android · Cambio: mejorado · Patrón: menú desplegable → selector visible
- Funciones: Estados; Editar; Favorito; Quitar
- Lugar nuevo: pantalla `ficha`: Selector de estado de 6 opciones visible en la ficha

## LIB-08 · Pestañas Manga, Novelas y Torrents
- Plataformas: desktop, android · Cambio: reubicado · Patrón: pestañas → selector de medio; Torrents fuera de Biblioteca
- Funciones: Manga; Novelas; Torrents
- Lugar nuevo: pantalla `transferencias`: Manga y Novelas → medio de Biblioteca; Torrents → Transferencias

## YOU-01 · Página Tú
- Plataformas: android · Cambio: mejorado · Patrón: Descargas y Torrents → Transferencias; Para ti → Explorar
- Funciones: Sincronizar ahora; Cuentas; Para ti; Manga; Novelas; Perfil; Notificaciones; Historial; Descargas; Torrents; Extensiones; Dispositivo vinculado; Ajustes
- Lugar nuevo: pantalla `perfil`: Tú: Manga, Novelas, Transferencias, Fuentes, Link, Ajustes, Perfil, Notificaciones, Historial
- Riesgo: Para ti y la sincronización deben seguir a un toque desde Tú o Biblioteca

## SOC-01 · Perfil de AniList
- Plataformas: desktop, android · Cambio: igual
- Funciones: Atrás; Actualizar; Abrir en AniList; Editar perfil; Seguir; Seguidores y seguidos; Pestañas de perfil; Publicar estado; Solo a quienes sigo; Abrir actividad; Abrir usuario; Abrir ficha; Me gusta; Respuestas; Cargar más
- Lugar nuevo: pantalla `perfil`: Perfil

## SOC-02 · Hilo de actividad
- Plataformas: desktop, android · Cambio: igual
- Funciones: Me gusta; Responder; Abrir en AniList; Cargar respuestas
- Lugar nuevo: pantalla `perfil`: Perfil › actividad

## SOC-03 · Notificaciones de AniList
- Plataformas: desktop, android · Cambio: reubicado · Patrón: pantalla → panel global
- Funciones: Actualizar; Deslizar para actualizar; Permitir notificaciones; Abrir notificación; Cargar más
- Lugar nuevo: pantalla `perfil`: Panel de notificaciones global y Perfil
