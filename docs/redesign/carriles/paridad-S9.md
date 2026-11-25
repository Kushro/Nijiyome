# Paridad del carril S9

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Fuentes, Link). 38 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## DESK-05 · Monitor de Link y sistema
- Plataformas: desktop · Cambio: fusionado · Patrón: diálogo suelto → sección de Link
- Funciones: Ajustes de Link; Cerrar; Detener sesión; Abrir torrents; Sesiones, caché y memoria
- Lugar nuevo: pantalla `link`: Link › Monitor; también desde el chip y la bandeja

## DESK-10 · Abrir desde el teléfono (Continuar en PC)
- Plataformas: desktop · Cambio: igual
- Funciones: Abrir reproductor en la posición del teléfono
- Lugar nuevo: pantalla `reproductor`: Igual

## EXT-01 · Hub de extensiones
- Plataformas: desktop, android · Cambio: mejorado · Patrón: una columna larga con 5 conceptos → pestañas con búsqueda
- Funciones: Contador de instaladas; Actualizaciones disponibles; Desactivar o activar todas; Avisos de instalación y errores
- Lugar nuevo: pantalla `fuentes`: Fuentes con pestañas Instaladas, Repositorios, Stremio, Proveedores, Servidores en tu PC, Debrid

## EXT-02 · Instalar extensión
- Plataformas: desktop, android · Cambio: mejorado · Patrón: soltar solo en el panel → capa global
- Funciones: Elegir archivo .kai; Instalar desde URL o ruta; Soltar .kai (escritorio)
- Lugar nuevo: pantalla `fuentes`: Fuentes › Instaladas › Instalar; soltar .kai en cualquier parte

## EXT-03 · Instalar desde URL
- Plataformas: desktop, android · Cambio: igual
- Funciones: URL o ruta; Instalar
- Lugar nuevo: pantalla `fuentes`: Fuentes › Instaladas › Instalar

## EXT-04 · Tarjeta de extensión
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 6 zonas densas → jerarquía clara con detalle plegable
- Funciones: Activar; Eliminar; Preferencias; Actualizar; Ver registro; Compatibilidad y permisos; Aviso de navegador oculto
- Lugar nuevo: pantalla `fuentes`: Fuentes › Instaladas

## EXT-05 · Preferencias de extensión
- Plataformas: desktop, android · Cambio: mejorado · Patrón: guardado mixto invisible → estado sin guardar visible
- Funciones: Interruptores; Opciones; Texto con Guardar; Secreto con Quitar
- Lugar nuevo: pantalla `fuentes`: Fuentes › Instaladas › Preferencias

## EXT-06 · Eliminar extensión
- Plataformas: desktop, android · Cambio: igual
- Funciones: Confirmar eliminación
- Lugar nuevo: pantalla `fuentes`: Diálogo en Fuentes

## EXT-07 · Instalar versión anterior
- Plataformas: desktop, android · Cambio: igual
- Funciones: Instalar de todos modos; Cancelar
- Lugar nuevo: pantalla `fuentes`: Diálogo en Fuentes

## EXT-08 · Actualizaciones de extensiones
- Plataformas: desktop, android · Cambio: igual
- Funciones: Buscar al iniciar; Buscar ahora; Actualizar todas; Actualizar una
- Lugar nuevo: pantalla `fuentes`: Fuentes › Instaladas, banner de actualizaciones

## EXT-09 · Desactivar todas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Desactivar todas con confirmación; Activar todas
- Lugar nuevo: pantalla `fuentes`: Menú de Fuentes

## EXT-10 · Proveedores
- Plataformas: desktop, android · Cambio: reubicado · Patrón: solo en Extensiones → también donde se navega cada medio
- Funciones: Lista de catálogos con tipo Anime, Manga, Novela; Abrir proveedor
- Lugar nuevo: pantalla `fuentes`: Fuentes › Proveedores; catálogos también en Explorar, Manga y Novelas

## EXT-11 · Repositorios
- Plataformas: desktop, android · Cambio: mejorado · Patrón: todo en línea → búsqueda y filtro
- Funciones: Agregar repositorio; Actualizar; Quitar con confirmación; Instalar o actualizar extensión del repo
- Lugar nuevo: pantalla `fuentes`: Fuentes › Repositorios

## EXT-12 · Complementos de Stremio
- Plataformas: desktop, android · Cambio: reubicado
- Funciones: Instalar por manifiesto; Activar; Actualizar; Restablecer coincidencias; Quitar con confirmación
- Lugar nuevo: pantalla `fuentes`: Fuentes › Stremio

## EXT-13 · Extensiones de tu PC
- Plataformas: desktop, android · Cambio: reubicado
- Funciones: Actualizar servidor; Estado por servidor; Actualizar desde el repo o desde la PC; Ir a servidores de extensiones (escritorio)
- Lugar nuevo: pantalla `fuentes`: Fuentes › Servidores en tu PC

## EXT-14 · Catálogo de proveedor
- Plataformas: desktop, android · Cambio: reubicado · Patrón: pantalla desde Extensiones → dentro de Explorar
- Funciones: Buscar en el proveedor; Filas de inicio; Desplazamiento infinito
- Lugar nuevo: pantalla `explorar`: Explorar › Catálogos de extensiones (anime); Manga y Novelas para sus catálogos

## EXT-15 · Título de proveedor
- Plataformas: desktop, android · Cambio: reubicado · Patrón: pantalla propia → ficha común
- Funciones: Empezar o continuar; Guardar; Marcar completado; Página de AniList; Filtrar episodios; Ordenar; Reanudar desde el historial
- Lugar nuevo: pantalla `ficha`: Ficha (variante de proveedor)

## EXT-16 · Filas de fuentes en Ajustes
- Plataformas: desktop, android · Cambio: reubicado
- Funciones: Ver SET-PLY-SRC
- Lugar nuevo: pantalla `ajustes`: Ajustes › Fuentes de episodios

## LNK-01 · Pantalla de dispositivo vinculado
- Plataformas: desktop, android · Cambio: igual
- Funciones: Introducción; Tarjeta de traspaso; Configurar extensiones de la PC; Estado sin emparejar, falla o emparejado
- Lugar nuevo: pantalla `link`: Link

## LNK-02 · Emparejar
- Plataformas: desktop, android · Cambio: igual
- Funciones: Escanear QR; Cancelar escaneo; Pegar código; Permiso de cámara
- Lugar nuevo: pantalla `link`: Link › Emparejar

## LNK-03 · Pegar código
- Plataformas: desktop, android · Cambio: igual
- Funciones: Código de emparejamiento; Emparejar
- Lugar nuevo: pantalla `link`: Link › Emparejar

## LNK-04 · PCs en tu red
- Plataformas: desktop, android · Cambio: igual
- Funciones: Buscar PCs por mDNS; Emparejar con huella y código
- Lugar nuevo: pantalla `link`: Link › Emparejar

## LNK-05 · Estado del enlace
- Plataformas: desktop, android · Cambio: mejorado · Patrón: solo en la pantalla → también en el chip global
- Funciones: PC conectada o fuera de alcance; Huella CA y detalles; Última vez visto; Continuar en la PC; Reintentar; Olvidar PC
- Lugar nuevo: pantalla `link`: Link › Dispositivos; chip de Link en la barra superior

## LNK-06 · Perfil de mejora
- Plataformas: desktop, android · Cambio: mejorado · Patrón: lista de 15 radios → selector de dos ejes Escalado × Cuadros
- Funciones: Capacidades de la PC; Elegir perfil de mejora (unos 15); Motivo de perfiles no disponibles; Estado sostenido
- Lugar nuevo: pantalla `link`: Link › Perfil de mejora

## LNK-07 · Control remoto
- Plataformas: desktop, android · Cambio: igual
- Funciones: Anterior; Reproducir o pausar; Siguiente
- Lugar nuevo: pantalla `link`: Link › Control remoto

## LNK-08 · Mejorar un stream
- Plataformas: desktop, android · Cambio: pendiente
- Funciones: URL del stream; Iniciar sesión de mejora
- Lugar nuevo: pantalla `link`: Sin lugar nombrado; candidato: Link › Perfil de mejora
- Riesgo: La lista de Link no nombra esta función. El dueño debe decidir si va en Perfil de mejora o en la hoja Mejoras del reproductor (Procesar en).

## LNK-09 · Traspaso desde la PC
- Plataformas: desktop, android · Cambio: mejorado · Patrón: solo en la pantalla Link → también como notificación
- Funciones: Reproducir aquí; Descartar
- Lugar nuevo: pantalla `link`: Link y panel de notificaciones

## LNK-10 · Olvidar PC
- Plataformas: desktop, android · Cambio: igual
- Funciones: Confirmar olvido
- Lugar nuevo: pantalla `link`: Diálogo en Link › Dispositivos

## LNK-11 · Falla del enlace
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reintentar; Olvidar PC
- Lugar nuevo: pantalla `link`: Link

## LNK-12 · Servidores de extensiones
- Plataformas: desktop, android · Cambio: reubicado · Patrón: pantalla aparte desde Link → pestaña de Fuentes
- Funciones: Revisar servidores; Aviso de error; Conectar; Lista de servidores; Información
- Lugar nuevo: pantalla `fuentes`: Fuentes › Servidores en tu PC (lado teléfono)

## LNK-13 · Conectar a un servidor
- Plataformas: desktop, android · Cambio: reubicado
- Funciones: Escanear QR; Pegar enlace; Dirección y contraseña de visor; Conectar
- Lugar nuevo: pantalla `fuentes`: Fuentes › Servidores en tu PC › Conectar

## LNK-14 · Pegar enlace de emparejamiento
- Plataformas: desktop, android · Cambio: igual
- Funciones: Enlace desde el portapapeles; Conectar
- Lugar nuevo: pantalla `fuentes`: Fuentes › Servidores en tu PC

## LNK-15 · Servidores vinculados
- Plataformas: desktop, android · Cambio: reubicado
- Funciones: Activar; Quitar con confirmación; Estado del servidor
- Lugar nuevo: pantalla `fuentes`: Fuentes › Servidores en tu PC

## LNK-16 · Por qué vincular
- Plataformas: desktop, android · Cambio: mejorado · Patrón: texto fijo largo → plegable
- Funciones: Ventajas; Desventajas; Extras
- Lugar nuevo: pantalla `fuentes`: Fuentes › Servidores en tu PC, sección plegable Más información

## LNK-17 · Confirmar enlace entrante
- Plataformas: desktop, android · Cambio: igual
- Funciones: Conectar; Cancelar
- Lugar nuevo: pantalla `fuentes`: Diálogo al abrir un enlace nijiyome://

## LNK-18 · Monitor de Link y sistema
- Plataformas: desktop · Cambio: reubicado · Patrón: diálogo solo por chip y bandeja → sección de Link; caché RAM en un solo control
- Funciones: Estado del servidor; Abrir ajustes de Link; Sesiones con caché RAM, adelanto y Detener; Torrents activos y abrir Torrents; Presupuesto de caché por sesión; RAM de la app; Disco
- Lugar nuevo: pantalla `link`: Link › Monitor (y desde el chip de Link)

## LNK-19 · Chip de Link y bandeja
- Plataformas: desktop · Cambio: mejorado · Patrón: desaparece con la barra de título nativa → chip en la barra superior de la app
- Funciones: Chip con puerto y sesiones; Abrir monitor; Bandeja: Monitor, Torrents, Ajustes
- Lugar nuevo: pantalla `link`: Chip de Link en la barra superior; bandeja sin cambios

## LNK-20 · Registros de dispositivos
- Plataformas: desktop, android · Cambio: reubicado · Patrón: escondido en Acerca de → sección de Link
- Funciones: Filtrar por dispositivo; Enviar a la PC (Android)
- Lugar nuevo: pantalla `link`: Link › Registros
