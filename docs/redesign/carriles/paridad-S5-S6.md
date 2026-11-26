# Paridad del carril S5-S6

Generado de `docs/redesign/artefacto/paridad.json` (áreas: Reproductor). 96 entradas. Cada una debe seguir existiendo en lo que entregues; marca en tu informe final cada id como `hecho`, `igual` (no hizo falta tocarlo) o `falta` con el motivo.

## PLY-01 · Estados del reproductor
- Plataformas: desktop, android · Cambio: mejorado · Patrón: copy de carga dispersa → un estado con acción clara
- Funciones: Volver; Elegir una fuente; Ir a Fuentes (sin fuentes); Abrir dispositivo enlazado; Reintentar; Reintento al volver la red; Progreso de búsqueda de fuentes; Estado de metadatos del torrent
- Lugar nuevo: pantalla `reproductor`: Estado del reproductor antes de la sesión, con acción principal por caso

## PLY-02 · Error de reproducción
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 6 botones sueltos → jerarquía principal y secundaria
- Funciones: Reintentar; Elegir una fuente; Reproducir en el teléfono; Usar la mejor fuente web; Elegir el archivo del torrent; Ir a Fuentes; Lista de fuentes probadas
- Lugar nuevo: pantalla `reproductor`: Estado de error con una acción principal y secundarias en jerarquía

## PLY-03 · Aviso de datos móviles
- Plataformas: desktop, android · Cambio: igual
- Funciones: Ver de todos modos; Elegir fuente web o cancelar; Ir a ajustes de torrents
- Lugar nuevo: pantalla `reproductor`: Diálogo antes de iniciar un torrent con datos móviles

## PLY-04 · Hoja de fuentes del episodio
- Plataformas: desktop, android · Cambio: mejorado · Patrón: dos capas de filtros → facetas visibles y comparador
- Funciones: Ir a ajustes de fuentes; Filtrar Todas, Web o Torrent; Solo en caché de debrid; Filtrar sub, dob, audio dual, multidob; Solo multisub; Filtrar por resolución; Piso de fiabilidad media o alta; Pistas de audio por idioma; Idiomas de subtítulos; Origen BD, REMUX, WEB, TV, DVD; Códec H.264, HEVC, AV1, VP9; 10 bits, lote, SeaDex, compatible; Limpiar filtros; Reproducir una fuente; Descargar para después; Reintentar carga; Aviso de fuente recordada
- Lugar nuevo: pantalla `fuentes-episodio`: Hoja con facetas o comparador, modo manual o automático

## PLY-05 · Controles de escritorio
- Plataformas: desktop · Cambio: reubicado · Patrón: fila de chips que se desborda → hojas agrupadas
- Funciones: Volver; Chip de estado de mejoras; Chip de fuente; Abrir en el cliente de torrents; Línea de estado del torrent; Descargar episodio; Captura de pantalla; Episodios; Mejoras; Atajos de teclado; Imagen en imagen; Pantalla completa; Episodio anterior; Retroceder 5 s; Reproducir o pausar; Avanzar 5 s; Episodio siguiente; Barra de progreso con capas y marcas; Burbuja de tiempo al pasar; Silenciar; Volumen; Chip Más; Chip Calidad; Chip Subtítulos; Chip Audio; Chip Velocidad; Clic pausa, doble clic pantalla completa, rueda salta
- Lugar nuevo: pantalla `reproductor`: Barra superior, transporte y fila de hojas: Pistas, Mejoras, Imagen, Velocidad y temporizador, Episodios, Más

## PLY-06 · Menú Más opciones (ventana angosta)
- Plataformas: desktop · Cambio: fusionado · Patrón: menú desplegable → hoja Más
- Funciones: Descargar episodio; Bloquear controles; Captura de pantalla; Episodios; Mejoras; Atajos de teclado
- Lugar nuevo: pantalla `reproductor`: Hoja Más en ventanas angostas

## PLY-07 · Contenedor de hojas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Título y cerrar; Cerrar al tocar fuera o deslizar; Panel lateral o hoja inferior
- Lugar nuevo: pantalla `reproductor`: Contenedor común de todas las hojas del reproductor

## PLY-08 · Hoja de calidad
- Plataformas: desktop, android · Cambio: mejorado · Patrón: lista de filas → control segmentado
- Funciones: Calidad automática; Elegir altura del stream; Cambiar a fuente de otra calidad (escritorio)
- Lugar nuevo: pantalla `reproductor`: Hoja Imagen › Calidad, con aviso cuando cambia la fuente

## PLY-09 · Hoja de subtítulos
- Plataformas: desktop, android · Cambio: fusionado · Patrón: hoja larga mezclada → hoja Pistas con secciones
- Funciones: Desactivar subtítulos; Elegir pista; Cargar archivo; Abrir estilo; Retraso -0,1 s; Retraso +0,1 s; Restablecer retraso; Subtítulo secundario; Traducir subtítulos; Pretraducir la pista
- Lugar nuevo: pantalla `reproductor`: Hoja Pistas › Subtítulos

## PLY-10 · Hoja de audio
- Plataformas: desktop, android · Cambio: fusionado · Patrón: hoja aparte → Pistas junto a subtítulos
- Funciones: Elegir pista de audio; Sin audio
- Lugar nuevo: pantalla `reproductor`: Hoja Pistas › Audio

## PLY-11 · Hoja de velocidad
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 6 a 7 filas → chips o paso a paso
- Funciones: Elegir velocidad 0,5x a 2x
- Lugar nuevo: pantalla `reproductor`: Hoja Velocidad y temporizador

## PLY-12 · Hoja Más
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Más mezclado → temporizador con la velocidad
- Funciones: Estilo de controles Cine o Minimalista; Temporizador de apagado; Repetir episodio; Volumen estable
- Lugar nuevo: pantalla `reproductor`: Temporizador en Velocidad y temporizador; repetir y volumen estable en Más

## PLY-13 · Hoja de episodios
- Plataformas: desktop, android · Cambio: mejorado · Patrón: lista solo texto → filas con vistos y miniatura
- Funciones: Saltar a un episodio; Reintentar lista
- Lugar nuevo: pantalla `reproductor`: Hoja Episodios

## PLY-14 · Panel de atajos
- Plataformas: desktop, android · Cambio: mejorado · Patrón: solo lectura → enlace al editor
- Funciones: Ver atajos con su tecla; Cerrar con ? o Esc
- Lugar nuevo: pantalla `reproductor`: Más › Atajos, con enlace al editor en Ajustes › Reproducción

## PLY-15 · Indicador flotante (HUD)
- Plataformas: desktop · Cambio: igual
- Funciones: Brillo; Volumen o silencio; Salto; 2x al mantener; Velocidad; Subtítulo activo; Audio activo; Retraso; Repetir; Preset de escalado
- Lugar nuevo: pantalla `reproductor`: Píldora superior del reproductor

## PLY-16 · Avisos del reproductor
- Plataformas: desktop, android · Cambio: mejorado · Patrón: 40 avisos de 5 s sin historial → aviso más historial
- Funciones: Deshacer bajada de calidad; Aviso de fuente fallida y cambio; Intro saltada; Captura guardada; Subtítulos cargados; Temporizador cumplido; Errores de traducción; Cambio de motor; Fallo de debrid; Avisos de Link
- Lugar nuevo: pantalla `reproductor`: Aviso flotante; los importantes también en el panel de notificaciones

## PLY-17 · Carga y estado del torrent
- Plataformas: desktop, android · Cambio: igual
- Funciones: Indicador de carga; Pares y avance del torrent; Preparando en el PC; Renovar sesión de Link atascada
- Lugar nuevo: pantalla `reproductor`: Capa de carga del reproductor

## PLY-18 · Saltar intro, final o resumen
- Plataformas: desktop, android · Cambio: igual
- Funciones: Saltar intro; Saltar final; Saltar resumen; Enter salta (escritorio)
- Lugar nuevo: pantalla `reproductor`: Botón flotante sobre la barra

## PLY-19 · Tarjeta de siguiente episodio
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reproducir ahora; Cancelar avance automático; Cuenta regresiva
- Lugar nuevo: pantalla `reproductor`: Tarjeta compacta al final del episodio

## PLY-20 · Pantalla de fin de episodio
- Plataformas: desktop, android · Cambio: igual
- Funciones: Volver; Mostrar el video; Ver siguiente episodio; Cancelar cuenta regresiva; Ver de nuevo; Episodios; Volver a la ficha; Progreso marcado y sincronizado
- Lugar nuevo: pantalla `reproductor`: Pantalla de fin dentro del reproductor

## PLY-21 · Capa de bloqueo
- Plataformas: android · Cambio: igual
- Funciones: Mostrar desbloquear al tocar; Desbloquear
- Lugar nuevo: pantalla `reproductor`: Capa de bloqueo del reproductor (Android)

## PLY-22 · Progreso de pretraducción
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reproducir ahora
- Lugar nuevo: pantalla `reproductor`: Tarjeta central durante la pretraducción

## PLY-23 · Píldoras de Link y procesado
- Plataformas: android · Cambio: igual
- Funciones: Cambiando perfil; Cancelar cambio de procesado
- Lugar nuevo: pantalla `reproductor`: Píldora superior del reproductor

## PLY-24 · Gestos de puntero (escritorio)
- Plataformas: desktop · Cambio: igual
- Funciones: Mostrar controles al mover; Clic pausa; Doble clic pantalla completa; Rueda salta 5 s
- Lugar nuevo: pantalla `reproductor`: Superficie del video en escritorio

## PLY-25 · Tarjeta de mpv externo
- Plataformas: desktop · Cambio: igual
- Funciones: Detener; Fuentes; Volver; Ver sin generación de fotogramas; Ajustes de generación de fotogramas; Pantalla de fin encima
- Lugar nuevo: pantalla `reproductor`: Estado Reproduciendo en mpv

## PLY-26 · Reproductor externo (Android)
- Plataformas: android · Cambio: igual
- Funciones: Detener; Volver; Abrir de nuevo; Elegir app para reproducir
- Lugar nuevo: pantalla `reproductor`: Estado Reproduciendo en otra app

## PLY-27 · Enviar al teléfono
- Plataformas: desktop · Cambio: reubicado · Patrón: botón flotante aparte → acción en barra y Más
- Funciones: Enviar episodio y posición; Elegir teléfono si hay varios
- Lugar nuevo: pantalla `reproductor`: Más › Enviar a dispositivo, también en la barra superior

## PLY-28 · Controles del sistema y control remoto
- Plataformas: desktop · Cambio: igual
- Funciones: MPRIS reproducir, pausar, siguiente, anterior, posición; Mando desde el teléfono por Link; Discord Rich Presence
- Lugar nuevo: pantalla `link`: Link › Control remoto; MPRIS sin cambios

## PLY-29 · Capa de subtítulos en pantalla
- Plataformas: desktop, android · Cambio: igual
- Funciones: Subir sobre los controles; Secundario arriba
- Lugar nuevo: pantalla `reproductor`: Capa de video

## PLY-30 · Reproductor de escritorio no disponible
- Plataformas: desktop · Cambio: igual
- Funciones: Volver
- Lugar nuevo: pantalla `reproductor`: Estado vacío del reproductor

## PLY-31 · Accesos a ajustes desde el reproductor
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Ajustes › Reproductor → categorías nuevas con salto exacto
- Funciones: Ajustes de fuentes; Ajustes de torrents; Importar Lossless.dll; Ajustes de generación de fotogramas; Dispositivo enlazado
- Lugar nuevo: pantalla `ajustes`: Saltos exactos a Reproducción, Mejoras de video, Torrents y descargas, Nijiyome Link

## PLA-01 · Controles táctiles
- Plataformas: android · Cambio: igual
- Funciones: Mostrar u ocultar al tocar; Ocultar a los 4 s; Cuatro diseños Cine y Minimalista
- Lugar nuevo: pantalla `reproductor`: Capa de controles táctiles

## PLA-02 · Barra superior táctil
- Plataformas: android · Cambio: reubicado · Patrón: 6 a 8 iconos o menú → barra corta más hoja Más
- Funciones: Volver; Título; Abrir fuentes desde el sufijo; Chip de mejoras; Imagen en imagen; Pantalla completa; Más opciones; Descargar episodio; Bloquear controles; Captura de pantalla; Episodios; Mejoras; Atajos de teclado
- Lugar nuevo: pantalla `reproductor`: Barra superior; las acciones de menos uso pasan a la hoja Más

## PLA-03 · Menú Más opciones táctil
- Plataformas: android · Cambio: fusionado · Patrón: menú desplegable → hoja Más
- Funciones: Descargar episodio; Bloquear controles; Captura de pantalla; Episodios; Mejoras; Cambiar fuente; Atajos de teclado
- Lugar nuevo: pantalla `reproductor`: Hoja Más

## PLA-04 · Chips de estado superiores
- Plataformas: android · Cambio: mejorado · Patrón: faltaba en Minimalista ancho → siempre visible
- Funciones: Chip de mejoras; Chip de fuente; Abrir en el cliente de torrents
- Lugar nuevo: pantalla `reproductor`: Barra superior del reproductor, presente en todos los diseños

## PLA-05 · Transporte central
- Plataformas: android · Cambio: igual
- Funciones: Episodio anterior; Retroceder 10 s; Reproducir o pausar; Avanzar 10 s; Episodio siguiente
- Lugar nuevo: pantalla `reproductor`: Transporte central

## PLA-06 · Tarjeta de carga
- Plataformas: android · Cambio: igual
- Funciones: Estado del torrent; Resolviendo fuente; Preparando en el PC
- Lugar nuevo: pantalla `reproductor`: Capa de carga

## PLA-07 · Barra de progreso táctil
- Plataformas: android · Cambio: igual
- Funciones: Tocar para saltar; Arrastrar con vista previa; Burbuja con tiempo y segmento; Alternar total y restante; Leyenda búfer, PC y OP/ED; Velocidad de descarga
- Lugar nuevo: pantalla `reproductor`: Barra de progreso del reproductor

## PLA-08 · Píldoras inferiores (Cine)
- Plataformas: android · Cambio: reubicado · Patrón: píldoras que se desbordan → hojas agrupadas
- Funciones: Audio; Subtítulos; Calidad; Velocidad; Ciclo de aspecto; Episodio siguiente; Más
- Lugar nuevo: pantalla `reproductor`: Fila inferior: Pistas, Imagen, Velocidad y temporizador, Episodios, Más

## PLA-09 · Barra inferior (Minimalista)
- Plataformas: android · Cambio: mejorado · Patrón: objetivos de 30 dp → 48 dp
- Funciones: Audio; Subtítulos; Velocidad; Calidad; Ciclo de aspecto; Mejoras; Episodio siguiente; Más
- Lugar nuevo: pantalla `reproductor`: Fila inferior con objetivos de 48 dp

## PLA-10 · Gestos táctiles
- Plataformas: android · Cambio: mejorado · Patrón: gestos ocultos → ayuda visible
- Funciones: Tocar muestra controles; Doble toque -10 s y +10 s; Mantener para 2x; Brillo con arrastre izquierdo; Volumen con arrastre derecho; Arrastre horizontal para buscar
- Lugar nuevo: pantalla `reproductor`: Superficie del video, con ayuda de gestos en Más › Atajos

## PLA-11 · Indicador de brillo
- Plataformas: android · Cambio: igual
- Funciones: Barra de brillo con porcentaje
- Lugar nuevo: pantalla `reproductor`: Superficie del video

## PLA-12 · Indicador de volumen
- Plataformas: android · Cambio: igual
- Funciones: Barra de volumen con porcentaje; Estado silenciado
- Lugar nuevo: pantalla `reproductor`: Superficie del video

## PLA-13 · Arco de doble toque
- Plataformas: android · Cambio: igual
- Funciones: Segundos acumulados
- Lugar nuevo: pantalla `reproductor`: Superficie del video

## PLA-14 · Indicador de arrastre horizontal
- Plataformas: android · Cambio: igual
- Funciones: Tiempo destino; Diferencia; Mini barra
- Lugar nuevo: pantalla `reproductor`: Superficie del video

## PLA-15 · Indicador de aspecto
- Plataformas: android · Cambio: igual
- Funciones: Ajustar, Rellenar, Estirar
- Lugar nuevo: pantalla `reproductor`: Píldora superior

## PLA-16 · Indicador de 2x y teclas
- Plataformas: android · Cambio: igual
- Funciones: 2x al mantener; Salto por tecla
- Lugar nuevo: pantalla `reproductor`: Píldora superior

## PLA-17 · Bloqueo de controles
- Plataformas: android · Cambio: igual
- Funciones: Bloquear controles; Tocar muestra desbloquear; Desbloquear; Ignorar teclas; Desbloqueo al terminar
- Lugar nuevo: pantalla `reproductor`: Barra superior y capa de bloqueo

## PLA-18 · Imagen en imagen
- Plataformas: android · Cambio: igual
- Funciones: Entrar con botón o P; Entrar solo al salir (Android 12+)
- Lugar nuevo: pantalla `reproductor`: Barra superior del reproductor

## PLA-19 · Pantalla completa y orientación
- Plataformas: android · Cambio: igual
- Funciones: Pantalla completa con horizontal forzada; Salir de pantalla completa; Pantalla encendida al reproducir; Pausar al salir
- Lugar nuevo: pantalla `reproductor`: Barra superior del reproductor

## PLA-20 · Notificación de reproducción en segundo plano
- Plataformas: android · Cambio: igual
- Funciones: Reproducir o pausar; Saltar atrás y adelante; Botones de auriculares; Abrir la app
- Lugar nuevo: pantalla `reproductor`: Notificación del sistema; se activa en Ajustes › Reproducción

## PLA-21 · Lanzar reproductor externo
- Plataformas: android · Cambio: igual
- Funciones: Detener; Volver; Abrir de nuevo; Elegir app
- Lugar nuevo: pantalla `reproductor`: Estado Reproduciendo en otra app

## PLA-22 · Sin app externa
- Plataformas: android · Cambio: igual
- Funciones: Reproducir con Media3; Abrir Ajustes
- Lugar nuevo: pantalla `reproductor`: Estado de error del reproductor externo

## PLA-23 · Generación de fotogramas en el teléfono
- Plataformas: android · Cambio: reubicado · Patrón: tarjeta al fondo de un panel largo → sección propia en Mejoras
- Funciones: Usar al reproducir; Importar Lossless.dll en Ajustes; Multiplicador Auto a 5x; Perfil Ahorro, Equilibrado, Calidad; Estado y fps en vivo
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras › Generación de fotogramas

## PLA-24 · Línea de generación en estadísticas
- Plataformas: android · Cambio: igual
- Funciones: Fps de entrada y salida; Latencia; Motivo de pausa o fallo
- Lugar nuevo: pantalla `reproductor`: Capa de estadísticas

## PLA-25 · Aviso de fallo de generación
- Plataformas: android · Cambio: igual
- Funciones: Aviso de fallo con motivo
- Lugar nuevo: pantalla `reproductor`: Aviso flotante del reproductor

## PLA-26 · Captura de pantalla
- Plataformas: android · Cambio: igual
- Funciones: Guardar fotograma en Imágenes; Aviso con la ruta
- Lugar nuevo: pantalla `reproductor`: Barra superior o hoja Más, tecla S

## PLA-27 · Estilo de controles
- Plataformas: android · Cambio: pendiente
- Funciones: Elegir Cine o Minimalista
- Lugar nuevo: pantalla `reproductor`: Sin hogar definido: hoy en la hoja Más
- Riesgo: La arquitectura no dice si se mantienen los dos estilos de controles táctiles; si el chrome se unifica se pierde una preferencia guardada del usuario. Decidir: conservar el selector (Más o Ajustes › Reproducción) o retirarlo a propósito.

## PLE-01 · Chip de estado de mejoras
- Plataformas: desktop, android · Cambio: igual
- Funciones: Abrir hoja Mejoras; Tono activo, aviso o neutro; Estado teléfono, PC o mixto
- Lugar nuevo: pantalla `reproductor`: Barra superior, abre la hoja Mejoras

## PLE-02 · Hoja Mejoras
- Plataformas: desktop, android · Cambio: mejorado · Patrón: una columna de 7 tarjetas → secciones con anclas
- Funciones: Cerrar; Mostrar estadísticas
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras con secciones Procesar en y presets

## PLE-03 · Procesar en
- Plataformas: desktop, android · Cambio: igual
- Funciones: Teléfono, PC o mixto; Cancelar cambio; Importar Lossless.dll en Ajustes; Motivos de bloqueo
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras › Procesar en

## PLE-04 · Qué hace el PC
- Plataformas: android · Cambio: igual
- Funciones: Perfil; Escalado; Generación; Códec; Adelanto listo; Avisos del PC
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras › Procesar en › PC

## PLE-05 · Perfil de mejora de Link
- Plataformas: android · Cambio: igual
- Funciones: Elegir perfil del PC por grupo
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras › Procesar en › Perfil

## PLE-06 · Mejoras requieren MPV
- Plataformas: android · Cambio: igual
- Funciones: Cambiar a MPV
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras con motor Media3

## PLE-07 · Escalado en el dispositivo
- Plataformas: desktop, android · Cambio: mejorado · Patrón: rejilla plana de 12 chips → presets agrupados
- Funciones: Preset de escalado; Calidad Rápida, Equilibrada, Alta; Usar como predeterminado; Deshacer bajada; Nitidez; Deband; Suavizado; Ajustes automáticos
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras › Presets

## PLE-08 · Generación de fotogramas (teléfono)
- Plataformas: android · Cambio: igual
- Funciones: Usar al reproducir; Importar Lossless.dll; Multiplicador; Perfil
- Lugar nuevo: pantalla `reproductor`: Hoja Mejoras › Generación de fotogramas

## PLE-09 · Imagen y decodificador
- Plataformas: desktop, android · Cambio: reubicado · Patrón: dentro de Mejoras → hoja Imagen propia
- Funciones: Aspecto; Zoom; Brillo; Contraste; Saturación; Gamma; Tono; Restablecer imagen; Decodificador Auto, HW, HW+, SW
- Lugar nuevo: pantalla `reproductor`: Hoja Imagen

## PLE-10 · Capa de estadísticas
- Plataformas: desktop, android · Cambio: igual
- Funciones: Pedido y en uso; Shaders; Decodificador; Fotogramas perdidos; Fps y pantalla; Generación y PC
- Lugar nuevo: pantalla `reproductor`: Capa sobre el video, interruptor en Mejoras

## PLE-11 · Avisos de calidad y motor
- Plataformas: desktop, android · Cambio: igual
- Funciones: Deshacer bajada por fotogramas; Aviso de batería y temperatura; Cambio de motor
- Lugar nuevo: pantalla `reproductor`: Aviso flotante del reproductor

## PLE-12 · Píldora de cambio de procesado
- Plataformas: android · Cambio: igual
- Funciones: Cancelar cambio; Resultado del cambio
- Lugar nuevo: pantalla `reproductor`: Píldora superior

## PLE-13 · Píldora de cambio de perfil
- Plataformas: android · Cambio: igual
- Funciones: Cambiando perfil; Resultado del cambio
- Lugar nuevo: pantalla `reproductor`: Píldora superior

## PLE-14 · Atajos Ctrl+1 a Ctrl+6
- Plataformas: desktop · Cambio: mejorado · Patrón: atajo oculto → mostrado junto al preset
- Funciones: Ctrl+1 a 6 elige preset; Indicador de preset
- Lugar nuevo: pantalla `reproductor`: Teclado; visibles en la hoja Mejoras y en Más › Atajos

## PLE-15 · mpv externo con lsfg-vk
- Plataformas: desktop · Cambio: reubicado · Patrón: Ajustes › Reproductor → Mejoras de video
- Funciones: Ver sin generación; Ajustes de generación; Detener; Fuentes; Volver; Activar lsfg-vk; Multiplicador; Modo rendimiento; Ruta de la DLL
- Lugar nuevo: pantalla `ajustes`: Ajustes › Mejoras de video › Escritorio; tarjeta en el reproductor

## PLE-16 · Subtítulos (detalle)
- Plataformas: desktop, android · Cambio: fusionado · Patrón: lista plana → sección de Pistas
- Funciones: Desactivar; Elegir pista; Cargar archivo; Estilo; Mostrar antes; Mostrar después; Restablecer retraso; Subtítulo secundario; Traducir; Pretraducir
- Lugar nuevo: pantalla `reproductor`: Hoja Pistas › Subtítulos

## PLE-17 · Estilo rápido de subtítulos
- Plataformas: desktop, android · Cambio: reubicado · Patrón: hoja aparte → paso dentro de Pistas
- Funciones: Tamaño; Fondo; Transparencia del fondo; Posición vertical; Estilo ASS; Restablecer; Más opciones
- Lugar nuevo: pantalla `reproductor`: Hoja Pistas › Estilo

## PLE-18 · Editor completo de estilo
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Ajustes › Reproductor → Subtítulos y audio
- Funciones: Estilos rápidos; Tamaño; Fuente incluida; Fuente importada; Fuente instalada; Importar fuentes; Negrita; Cursiva; Alineación; Color del texto; Color personalizado; Opacidad del texto; Contorno; Grosor; Color del contorno; Caja de fondo; Transparencia del fondo; Color del fondo; Distancia de sombra; Color de sombra; Posición vertical; Margen inferior; Margen lateral; Interlineado; Suavizado de bordes; Estilo ASS; Escalar ASS; Restablecer todo
- Lugar nuevo: pantalla `ajustes`: Ajustes › Subtítulos y audio › Estilo, también desde Pistas

## PLE-19 · Elegir fuente instalada
- Plataformas: desktop · Cambio: igual
- Funciones: Buscar fuentes; Elegir familia; Cancelar
- Lugar nuevo: pantalla `ajustes`: Ajustes › Subtítulos y audio › Estilo › Fuente

## PLE-20 · Pretraducción
- Plataformas: desktop, android · Cambio: igual
- Funciones: Reproducir ahora
- Lugar nuevo: pantalla `reproductor`: Tarjeta central del reproductor

## PLE-21 · Atajos y avisos de subtítulos
- Plataformas: desktop, android · Cambio: igual
- Funciones: C cambia subtítulos; Z y X retraso; V cambia audio; Avisos de traducción
- Lugar nuevo: pantalla `reproductor`: Teclado y avisos del reproductor

## PLE-22 · Ajustes de reproducción
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Ajustes › Reproductor → Reproducción
- Funciones: Cuenta regresiva del siguiente; Volumen estable; Reproducción en segundo plano
- Lugar nuevo: pantalla `ajustes`: Ajustes › Reproducción

## PLE-23 · Idioma del subtítulo secundario
- Plataformas: desktop, android · Cambio: mejorado · Patrón: códigos ISO → nombres de idioma
- Funciones: Elegir idioma secundario
- Lugar nuevo: pantalla `ajustes`: Ajustes › Subtítulos y audio

## PLE-24 · Traducción de subtítulos
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Ajustes › Reproductor → Subtítulos y audio
- Funciones: Motor de traducción; Modo en vivo o pretraducir; Idioma destino; URL del servidor; Modelo; Región; Guardar conexión; Estado de la clave; Quitar clave; Nueva clave; Guardar clave; Probar; Borrar caché
- Lugar nuevo: pantalla `ajustes`: Ajustes › Subtítulos y audio › Traducción

## PLE-25 · Editor de atajos del reproductor
- Plataformas: desktop, android · Cambio: reubicado · Patrón: Ajustes › Reproductor → Reproducción
- Funciones: Cambiar tecla de una acción; Restablecer atajos; Capturar tecla; Guardar o cancelar
- Lugar nuevo: pantalla `ajustes`: Ajustes › Reproducción › Atajos

## PLE-26 · Audio (detalle)
- Plataformas: desktop, android · Cambio: fusionado · Patrón: hoja aparte → Pistas
- Funciones: Elegir pista de audio; Sin audio
- Lugar nuevo: pantalla `reproductor`: Hoja Pistas › Audio

## PLE-27 · Volumen
- Plataformas: desktop, android · Cambio: igual
- Funciones: Silenciar; Volumen; M, flechas arriba y abajo
- Lugar nuevo: pantalla `reproductor`: Barra inferior de escritorio; volumen del sistema en Android

## PLE-28 · Subtítulos en sesiones de Link
- Plataformas: android · Cambio: igual
- Funciones: Conservar subtítulo al pasar al PC
- Lugar nuevo: pantalla `reproductor`: Comportamiento sin interfaz

## B-X-01 · Atajos de teclado del reproductor
- Plataformas: desktop, android · Cambio: igual
- Funciones: Espacio reproduce o pausa; Flechas saltan; F pantalla completa; M silencio; P imagen en imagen; J y K episodio; Flechas volumen; S captura; C subtítulos; ? atajos; . y , velocidad; V audio; Z y X retraso; Enter saltar intro; L repetir; Ctrl+1 a 6 presets; Esc en cadena
- Lugar nuevo: pantalla `reproductor`: Teclado; lista en Más › Atajos, editor en Ajustes › Reproducción

## B-X-02 · Gestos y puntero del reproductor
- Plataformas: desktop, android · Cambio: igual
- Funciones: Toque o clic; Doble toque o doble clic; Mantener 2x; Brillo; Volumen; Arrastre horizontal; Rueda; Pasar sobre la barra
- Lugar nuevo: pantalla `reproductor`: Superficie del video

## B-OCULTA-02 · Control de mpv externo desde la app
- Plataformas: desktop · Cambio: nuevo-acceso · Patrón: IPC sin interfaz → controles en la tarjeta
- Funciones: Pausar y reanudar mpv externo; Saltar en la barra
- Lugar nuevo: pantalla `reproductor`: Tarjeta Reproduciendo en mpv

## B-OCULTA-03 · Aspecto 16:9 y 4:3 en táctil
- Plataformas: android · Cambio: nuevo-acceso · Patrón: fuera del ciclo táctil → opciones en Imagen
- Funciones: Elegir 16:9 o 4:3
- Lugar nuevo: pantalla `reproductor`: Hoja Imagen › Aspecto

## NOTA-B-touch-vs-desktop · Cuándo aplica la interfaz táctil
- Plataformas: desktop, android · Cambio: igual · Patrón: fijo por plataforma, no por dispositivo de entrada
- Funciones: Táctil en todo Android, puntero en todo escritorio; Cuatro diseños Cine y Minimalista por ancho
- Lugar nuevo: pantalla `reproductor`: Regla de plataforma del reproductor

## NOTA-B-android-cross · Transversal Android del reproductor
- Plataformas: android · Cambio: igual
- Funciones: Mapa de gestos; Teclas del hardware y del control; Teclas reservadas; Botones multimedia vía sesión
- Lugar nuevo: pantalla `reproductor`: Superficie del video y teclado

## NOTA-B-enh-ref · Referencia de mejoras y subtítulos
- Plataformas: desktop, android · Cambio: igual
- Funciones: 12 presets de shaders; Niveles y bajadas automáticas; lsfg-vk y mpv-anime-build; Decodificación por hardware; Opciones de estilo de subtítulos; Proveedores de traducción
- Lugar nuevo: pantalla `ajustes`: Ajustes › Mejoras de video y Subtítulos y audio; hoja Mejoras

## NOTA-B-X-03 · Conteos del inventario B
- Plataformas: desktop, android · Cambio: igual · Patrón: referencia de conteo
- Funciones: 
- Lugar nuevo: pantalla `reproductor`: 124 superficies, 574 filas de acción

## NOTA-B-X-04 · Problemas principales de UX
- Plataformas: desktop, android · Cambio: mejorado
- Funciones: Textos en español fijos en el reproductor; Elecciones simples en listas largas; Diseños táctiles con acciones distintas; Avisos sin historial; Hojas sobrecargadas; Dos modelos de guardado; Conceptos de teléfono en escritorio; mpv externo solo lectura; Lectores inconsistentes; Orden que reinicia filtros
- Lugar nuevo: pantalla `reproductor`: Guía del rediseño

## NOTA-B-X-05 · Preguntas abiertas
- Plataformas: desktop, android · Cambio: pendiente
- Funciones: Atrás cierra hoja antes de salir; Botones de la notificación Media3; Bajadas por batería en escritorio; Límites de zoom; Archivos del selector de subtítulos; Rueda en lector paginado; Gestos del carrusel y hojas
- Lugar nuevo: pantalla `reproductor`: Sin verificar en la app
- Riesgo: Comportamientos que el inventario no pudo confirmar sin la app; el dueño debe verificarlos en dispositivo antes de fijar el diseño de hojas, zoom y rueda en el lector.
