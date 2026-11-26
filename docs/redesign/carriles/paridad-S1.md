# Paridad del carril S1

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Shell, Escritorio, Global). 27 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## SHELL-01 · Raíz de la app y primer arranque
- Plataformas: desktop, android · Cambio: igual
- Funciones: Aplicar tema (paleta, AMOLED, contraste, color dinámico, modo); Reducir movimiento global; Idioma de títulos global; Mostrar asistente si no terminó; Restaurar pila de navegación
- Lugar nuevo: pantalla `bienvenida`: Primer arranque antes del shell; tema y locales globales sin cambios

## SHELL-02 · Barra inferior (teléfono)
- Plataformas: android · Cambio: reubicado · Patrón: Buscar → Explorar; insignia de torrents → Transferencias en Tú
- Funciones: Inicio; Buscar; Calendario; Biblioteca con insignia de torrents; Tú; Ocultar en reproductor y lectores
- Lugar nuevo: pantalla `inicio`: Barra inferior: Inicio, Explorar, Calendario, Biblioteca, Tú
- Riesgo: La insignia de descargas activas debe moverse al acceso de Transferencias dentro de Tú

## SHELL-03 · Riel de navegación (ancho)
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 13 ítems planos → principal y utilidades; Para ti, Descargas y Torrents fusionados
- Funciones: 13 destinos; Insignia de torrents activos; Etiquetado o delgado (Ctrl+B); Saltar con 1..9; Desplazar riel si no cabe; Ocultar en reproductor y lectores
- Lugar nuevo: pantalla `inicio`: Riel en dos grupos: principal (6) y utilidades (5) más Perfil

## SHELL-04 · Paneles lista y detalle, y Atrás
- Plataformas: desktop, android · Cambio: igual
- Funciones: Dos paneles desde 840 dp; Atrás del sistema y predictivo; Esc y flecha de la barra de título; Cambio de destino reinicia pila; Transiciones sin animación con movimiento reducido
- Lugar nuevo: pantalla `ficha`: Igual; las rutas nuevas (Explorar, Transferencias, Fuentes) son paneles de lista

## SHELL-05 · Aviso sin conexión
- Plataformas: desktop, android · Cambio: igual
- Funciones: Banner sin conexión con contenido en caché; Chip Sin conexión en la barra de título
- Lugar nuevo: pantalla `inicio`: Pie del contenido y chip en la barra superior

## SHELL-06 · Snackbars y aviso de versión
- Plataformas: desktop, android · Cambio: igual
- Funciones: Snackbar global con acción; Aviso de nueva versión con Ver; Deshacer, Reintentar, Enviar, Descargas
- Lugar nuevo: pantalla `inicio`: Snackbar global; Ver lleva a Ajustes › Acerca de

## SHELL-07 · No disponible en este dispositivo
- Plataformas: desktop, android · Cambio: igual
- Funciones: Snackbar al abrir una ruta no ofrecida
- Lugar nuevo: pantalla `inicio`: Igual

## SHELL-08 · Paleta de comandos (Ctrl+K)
- Plataformas: desktop · Cambio: mejorado · Patrón: solo escritorio y en español fijo → ambas plataformas, localizada, salto exacto
- Funciones: Campo de búsqueda con borrar; Navegar con flechas, Enter, Esc; Abrir Ajustes; Alternar AMOLED; Cambiar tema; Alternar riel compacto; Abrir registros de errores; Ir a cada destino; Abrir cada ajuste del catálogo; Buscar en el catálogo de anime
- Lugar nuevo: pantalla `inicio`: Ctrl+K global; en Android como búsqueda; ajustes con salto exacto a la fila

## SHELL-09 · Ajustes en superposición
- Plataformas: desktop · Cambio: mejorado · Patrón: Ctrl+P → Ctrl+,
- Funciones: Abrir con Ctrl+P o Ctrl+,; Abrir desde engrane, bandeja y paleta; Salir a Fuentes, Link y Debrid; Bloqueado sobre reproductor y lectores
- Lugar nuevo: pantalla `ajustes`: Superposición de Ajustes con Ctrl+,
- Riesgo: Decidir si Ctrl+P sigue como alias para no romper la costumbre

## SHELL-10 · Atajos de teclado (escritorio)
- Plataformas: desktop · Cambio: mejorado · Patrón: sin lista de atajos → lista en la paleta
- Funciones: Ctrl+K paleta; Ctrl+P y Ctrl+, Ajustes; Ctrl+B riel; Esc atrás por capas; 1..9 destino del riel
- Lugar nuevo: pantalla `inicio`: Atajos globales; lista de atajos visible en la paleta
- Riesgo: Los números 1..9 deben remapearse al nuevo orden del riel (11 destinos)

## SHELL-11 · Enlaces entrantes y toques de notificación
- Plataformas: android · Cambio: reubicado · Patrón: Torrents y Descargas → Transferencias; Buscar por imagen → Explorar
- Funciones: nijiyome://extensionserver; nijiyome://oauth; nijiyome://user; Compartir imagen; Magnet y .torrent; Toque en notificación de torrent; Toque en notificación de descarga; Toque en descarga de capítulo; Toque en aviso de emisión; Resumen de AniList; Aviso de Link
- Lugar nuevo: pantalla `transferencias`: Mismos enlaces; destinos renombrados (Transferencias, Fuentes › Servidores en tu PC, Explorar, Link)

## UI-01 · Aviso de proveedor limitado
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reintentar; Cuenta regresiva
- Lugar nuevo: pantalla `inicio`: Inicio y ficha

## UI-02 · Estado de error
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reintentar; Cuenta regresiva en 429
- Lugar nuevo: pantalla `inicio`: Todas las pantallas

## UI-03 · Envoltura de carga
- Plataformas: desktop, android · Cambio: igual
- Funciones: Carga con demora; Error con Reintentar
- Lugar nuevo: pantalla `inicio`: Todas las pantallas

## UI-04 · Tarjetas de medio
- Plataformas: desktop, android · Cambio: igual
- Funciones: Póster y tarjeta ancha; Tamaños S a XL
- Lugar nuevo: pantalla `inicio`: Estilos de tarjeta del rediseño

## UI-05 · Soltar archivos (escritorio)
- Plataformas: desktop · Cambio: mejorado · Patrón: solo Buscar por imagen y Extensiones → cualquier parte con capa de acción
- Funciones: Soltar archivos y enlaces; Resaltar al pasar
- Lugar nuevo: pantalla `inicio`: Capa de soltar global: .kai, .torrent, magnet, video, subtítulo

## UI-06 · Servicios de plataforma
- Plataformas: desktop, android · Cambio: igual
- Funciones: Esc; Conectividad; Permiso de notificaciones; Abrir enlace; Compartir; Selectores de archivo; Pantalla encendida; Pantalla completa y PiP
- Lugar nuevo: pantalla `inicio`: Igual

## DESK-01 · Ventana principal
- Plataformas: desktop · Cambio: igual
- Funciones: Tamaño según escala; Cerrar a la bandeja; Actualizar no leídos al enfocar; --version
- Lugar nuevo: pantalla `inicio`: Igual

## DESK-02 · Barra de título propia
- Plataformas: desktop · Cambio: igual
- Funciones: Atrás; Arrastrar; Doble clic maximiza; Chip de Link; Chip Sin conexión; Buscar o comando; Ajustes; Minimizar, maximizar, cerrar
- Lugar nuevo: pantalla `inicio`: Barra superior con chip de Link, paleta y Ajustes

## DESK-03 · Bordes redimensionables
- Plataformas: desktop · Cambio: igual
- Funciones: Redimensionar por bordes y esquinas
- Lugar nuevo: pantalla `inicio`: Igual

## DESK-04 · Bandeja del sistema
- Plataformas: desktop · Cambio: igual
- Funciones: Mostrar ventana; Estado de Link; Sesiones de Link; Torrents activos; Rendimiento y caché; Monitor; Ajustes; Salir
- Lugar nuevo: pantalla `link`: Bandeja sin cambios; Torrents abre Transferencias

## DESK-06 · Instancia única y argumentos
- Plataformas: desktop · Cambio: igual
- Funciones: --minimized; Magnet y .torrent; OAuth; Perfil; Servidor de extensiones; Abrir video local
- Lugar nuevo: pantalla `transferencias`: Igual; destinos renombrados

## DESK-07 · Manejadores, inicio con sistema, reinicio
- Plataformas: desktop · Cambio: igual
- Funciones: Registrar magnet, .torrent y nijiyome:; Iniciar con el sistema; Reiniciar tras restablecer
- Lugar nuevo: pantalla `ajustes`: Ajustes › Sistema solo en escritorio

## DESK-08 · Discord Rich Presence
- Plataformas: desktop · Cambio: igual
- Funciones: Viendo, Mirando ficha, Navegando
- Lugar nuevo: pantalla `ajustes`: Ajustes › Sistema solo en escritorio

## DESK-09 · Notificaciones de escritorio
- Plataformas: desktop · Cambio: igual
- Funciones: Torrent terminado o fallido; Episodio descargado; Aviso de emisión; Resumen de AniList
- Lugar nuevo: pantalla `ajustes`: Ajustes › Notificaciones
- Riesgo: Hoy no se pueden tocar; el panel de notificaciones puede darles destino

## A-OCULTA-04 · Enfocar búsqueda con atajo (focusSearchPending)
- Plataformas: desktop · Cambio: nuevo-acceso · Patrón: ruta muerta → paleta con títulos
- Funciones: Ir al campo de búsqueda
- Lugar nuevo: pantalla `explorar`: Ctrl+K busca títulos; Explorar enfoca el campo al entrar

## NOTA-A-transversal · Transversal del shell
- Plataformas: desktop, android · Cambio: igual
- Funciones: Enlaces profundos (oauth, user, extensionserver, magnet, .torrent, imagen, video); Tres capas de contenido adulto; Ajustes de Apariencia que cambian estas pantallas (diseño de Inicio, saludo, héroe, tarjeta, densidad, movimiento, riel, estilo de página, cabecera, vista de episodios, vista de relaciones, vista de calendario, idioma de títulos); Notificaciones: Android con destino, escritorio sin acción; Mensajes solo por snackbar
- Lugar nuevo: pantalla `inicio`: Todas las pantallas
- Riesgo: Los ajustes de Apariencia listados deben seguir afectando las pantallas nuevas
