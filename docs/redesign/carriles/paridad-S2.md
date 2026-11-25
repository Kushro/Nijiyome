# Paridad del carril S2

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Inicio, Calendario). 19 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## HOME-01 · Inicio: contenedor y diseños
- Plataformas: desktop, android · Cambio: igual
- Funciones: Diseño Destacado, Carrusel o Solo filas; Densidad compacta; Tamaño y estilo de tarjeta; Reintentar por fila; Esqueletos con demora
- Lugar nuevo: pantalla `inicio`: Inicio

## HOME-02 · Saludo, campana y chips rápidos
- Plataformas: desktop, android · Cambio: reubicado · Patrón: campana de Inicio → panel global; Para ti → Explorar
- Funciones: Saludo según la hora; Campana con insignia de AniList; Chip Calendario; Chip Para ti; Chip Manga; Chip Novelas
- Lugar nuevo: pantalla `inicio`: Campana → panel de notificaciones global; chips rápidos en teléfono

## HOME-03 · Acceso de búsqueda
- Plataformas: android · Cambio: reubicado · Patrón: Buscar → Explorar
- Funciones: Píldora Buscar anime
- Lugar nuevo: pantalla `explorar`: Píldora abre Explorar o la búsqueda de Ctrl+K

## HOME-04 · Destacado
- Plataformas: desktop, android · Cambio: igual
- Funciones: Deslizar título; Saltar a título N; Ver o Continuar ep. N; Agregar a Planeado; Abrir ficha; Campana; Reproducción automática
- Lugar nuevo: pantalla `inicio`: Héroe de Inicio

## HOME-05 · Carrusel
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir ficha desde la diapositiva; Ver ep. 1; Detalles; Deslizar y reproducción automática
- Lugar nuevo: pantalla `inicio`: Héroe de Inicio

## HOME-06 · Aviso de proveedor limitado (Inicio)
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reintentar proveedor principal; Cuenta regresiva
- Lugar nuevo: pantalla `inicio`: Bajo el saludo

## HOME-07 · Continuar viendo
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver todo; Abrir ficha; Pulsación larga; Reanudar episodio
- Lugar nuevo: pantalla `inicio`: Fila Continuar de Inicio; Ver todo → Biblioteca › Continuar

## HOME-08 · Descargando
- Plataformas: desktop, android · Cambio: mejorado · Patrón: fila sin acción → abre Transferencias
- Funciones: Ver descargas activas con progreso
- Lugar nuevo: pantalla `transferencias`: Fila de Inicio que abre Transferencias

## HOME-09 · Seguidos y últimos episodios
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver todo → Calendario; Abrir ficha
- Lugar nuevo: pantalla `inicio`: Filas de Inicio

## HOME-10 · Top 10
- Plataformas: desktop, android · Cambio: igual
- Funciones: Hoy, Semana, Mes; Abrir ficha
- Lugar nuevo: pantalla `inicio`: Fila de Inicio; Top completo en Explorar

## HOME-11 · Para ti y Porque viste
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Para ti → Explorar
- Funciones: Ver todo → Para ti; Abrir ficha
- Lugar nuevo: pantalla `inicio`: Filas de Inicio; Ver todo → Explorar › Para ti

## HOME-12 · Chips de género
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir género; Flechas de desplazamiento
- Lugar nuevo: pantalla `explorar`: Chips en Inicio que abren Explorar filtrado

## HOME-13 · Filas de feeds
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Buscar → Explorar
- Funciones: Ver todo → Buscar con feed; Abrir ficha
- Lugar nuevo: pantalla `explorar`: Filas de Inicio; Ver todo → Explorar con feed

## HOME-14 · Próximos estrenos y Más favoritos
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir ficha
- Lugar nuevo: pantalla `inicio`: Filas de Inicio

## HOME-15 · Filas de extensiones
- Plataformas: desktop, android · Cambio: reubicado · Patrón: página de proveedor → Explorar
- Funciones: Ver todo → página del proveedor; Abrir ítem del proveedor; Reintentar
- Lugar nuevo: pantalla `explorar`: Filas de Inicio; Ver todo → Explorar › catálogos de extensiones

## HOME-16 · Filas de género infinitas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver todo → género; Abrir ficha; Reintentar
- Lugar nuevo: pantalla `inicio`: Filas de Inicio

## HOME-17 · Inicio vacío
- Plataformas: desktop, android · Cambio: mejorado · Patrón: texto sin acción → botón a Ajustes
- Funciones: Texto de conexión o fuentes de metadatos
- Lugar nuevo: pantalla `inicio`: Estado vacío con botón a Ajustes › Metadatos y descubrir

## HOME-18 · Filtro de contenido maduro
- Plataformas: desktop, android · Cambio: igual
- Funciones: Mostrar, Desenfocar o Advertir; Revelar al tocar; Diálogo Abrir o Cancelar
- Lugar nuevo: pantalla `inicio`: Igual en todas las tarjetas

## CAL-01 · Calendario de emisión
- Plataformas: desktop, android · Cambio: igual
- Funciones: Hoy; Semana, Agenda, Mes, Día; Anterior y Siguiente; Solo los que sigo; Elegir día; Abrir ficha; Seguir o dejar de seguir; Recordatorio por serie; Dejar de seguir en la lista
- Lugar nuevo: pantalla `calendario`: Calendario
