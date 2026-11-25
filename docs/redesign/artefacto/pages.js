/* Páginas de texto del explorador: portada, estado actual, arquitectura e implementación. */
(function () {
  const NJ = window.NJ; const h = NJ.h; const P = NJ.PAGE_RENDER;
  const esc = h.esc;

  P.portada = function () {
    const order = ['tanabata', 'obi', 'transit', 'sumi', 'canon'];
    return `<header class="pg-hero"><h1>Nijiyome Sparkles</h1>
      <p class="x-lede">El rediseño completo de Nijiyome para escritorio y Android. Abajo está Inicio en las cinco direcciones; tocá una para usarla en todas las pantallas. La única regla fija es que no se pierde nada de lo que la app hace hoy.</p></header>
    <section class="pg-wall" aria-label="Inicio en cada dirección">${order.map((id, i) => { const d = NJ.directions[id]; return `<figure class="pg-wall-item${i === 0 ? ' is-lead' : ''}"><div class="pg-wall-frame" data-wall="${id}"></div><figcaption><button type="button" class="pg-wall-pick" data-set="dir" data-value="${id}" aria-pressed="${NJ.state.dir === id}"><b>${esc(d ? d.name : id)}</b><span>${esc(d ? d.tagline || '' : 'Cargando…')}</span></button></figcaption></figure>`; }).join('')}</section>
    <div class="pg-actions"><a class="x-btn is-primary" href="#pantallas">${h.icon('devices')}Recorrer las 15 pantallas</a><a class="x-btn" href="#tarjetas">${h.icon('style')}Ver las 16 tarjetas</a><a class="x-btn" href="#paridad">${h.icon('fact_check')}Revisar la paridad</a></div>
    <section class="x-grid-2">
      <div class="x-panel"><h2>Cómo usarlo</h2><p>La barra de arriba combina dirección, plataforma, tema (claro, oscuro, OLED), paleta, tarjeta, movimiento y navegación, y queda guardada en este navegador. En Pantallas, cada una trae dos o tres estructuras; los controles de las maquetas responden. Mezclá libremente: el color y el tipo de una dirección, la tarjeta de otra y la estructura de Inicio de una tercera.</p>
        <p class="x-note">Base del trabajo: unas 330 superficies y 1.600 acciones y ajustes leídos del código de v1.2.1, 43 capturas reales con tu perfil copiado (escritorio, moto g51 5G y Hayase) y el código de LiMusic Forge.</p></div>
      <div class="x-panel"><h2>Los problemas que más pesan hoy</h2><ul class="x-list">
        <li>Elecciones enterradas: el estado de lista solo con pulsación larga, descargar y compartir dentro de "Más", 12 presets de mejora en una rejilla sin grupos.</li>
        <li>Torrents en cuatro lugares, Debrid dos veces, la Biblioteca con siete pestañas que mezclan cosas distintas.</li>
        <li>Ajustes que se guardan de dos maneras distintas, campos de texto para puertos, rutas y horarios, y una búsqueda que no lleva al ajuste.</li>
        <li>Ningún lugar para elegir varios episodios a la vez; ningún deshacer más allá de un caso.</li>
        <li>Textos fijos en español dentro del reproductor, la paleta Ctrl+K, el monitor de Link y la bandeja.</li>
        <li>Inicio con más de 15 filas que no se pueden ordenar ni ocultar.</li>
      </ul></div>
    </section>
    <section class="x-panel"><h2>De dónde sale cada dirección</h2><p>Tanabata es la dirección asignada por la tirada de impeccable y coincide con tu idea de los tanzaku. Obi es la elección propia de impeccable. Tránsito nocturno es la alternativa externa que mejor resistió. Sumi y hanko es el carril que mantiene la identidad actual. Canon es Material 3 tal cual, la vara para comparar.</p></section>
    <section class="x-panel"><h2>Lo que queda para decidir</h2><ul class="x-list">
      <li>La dirección base y si se combina con la tarjeta de otra.</li>
      <li>La estructura de Inicio, Ficha, Reproductor, Biblioteca y Calendario.</li>
      <li>La navegación: riel con todo, riel con selector de medio, o comandos primero.</li>
      <li>Cuántas paletas quedan (la propuesta reduce las 20 actuales a 3–6 por dirección).</li>
      <li>Los puntos marcados como pendientes en la página de Paridad.</li>
    </ul></section>`;
  };

  const CAPS = {
    escritorio: [['d-inicio', 'Inicio: banner destacado, aviso de límite de AniList y filas'], ['det-ep', 'Ficha: estado, acciones y episodios; descargas y compartir dentro de "Más"'], ['det-mas', 'El menú "Más" de la ficha'], ['src-1', 'Fuentes del episodio, modo manual'], ['d-biblioteca', 'Biblioteca: siete pestañas'], ['d-calendario', 'Calendario semanal'], ['d-buscar', 'Buscar: tipo, fuente y filtros en una fila'], ['d-parati', 'Para ti con afinidad'], ['d-ajustes', 'Ajustes › Apariencia: 20 paletas'], ['d-extensiones', 'Extensiones: un scroll con cinco secciones'], ['d-torrents', 'Torrents vacío'], ['d-descargas', 'Descargas'], ['d-manga', 'Manga'], ['d-novelas', 'Novelas'], ['d-perfil', 'Perfil de AniList'], ['mpv-card', 'Reproducción en mpv externo con lsfg-vk']],
    android: [['a-home', 'Inicio con color dinámico'], ['a-home3', 'Top 10 y Para ti'], ['a-buscar', 'Buscar'], ['a-calendario', 'Calendario con límite de AniList'], ['a-biblioteca', 'Biblioteca'], ['a-tu', 'Tú'], ['a-det1', 'Ficha de One Piece']],
    hayase: [['h-01', 'Inicio de Hayase'], ['h-home2', 'Filas de pósters con datos'], ['h-search', 'Búsqueda con filtros en desplegables'], ['h-schedule', 'Calendario mensual'], ['h-downloads', 'Cliente torrent con globo de pares'], ['h-settings', 'Ajustes de subtítulos con vista previa'], ['h-profile', 'Cuentas'], ['h-users', 'Ver juntos']],
  };
  P.actual = function () {
    const block = (k, title, note) => `<section class="x-panel"><h2>${title}</h2><p class="x-note">${note}</p><div class="pg-caps ${k === 'android' ? 'is-phone' : ''}">${CAPS[k].map(([f, c]) => `<figure><img src="capturas/${k}/${f}.webp" alt="${esc(c)}" loading="lazy"><figcaption>${esc(c)}</figcaption></figure>`).join('')}</div></section>`;
    return `<header style="display:grid;gap:8px"><h1>Cómo está hoy</h1><p class="x-lede">Capturas reales de Nijiyome v1.2.1 con tu perfil copiado (escritorio en un sway sin pantalla a 1920 × 1080, verificado contra tu DP-2; Android en el moto g51 5G) y de Hayase, también con tu perfil copiado.</p></header>
      ${block('escritorio', 'Nijiyome en escritorio', 'La base es sólida y coherente, con Material 3 y violeta. Lo que falla es dónde viven las cosas: acciones escondidas, filas que no se ordenan y ajustes en texto.')}
      ${block('android', 'Nijiyome en Android', 'El color dinámico del sistema reemplaza la paleta elegida por defecto, así que en Android casi nadie ve la identidad de la app.')}
      ${block('hayase', 'Hayase (referencia)', 'Lo que funciona: pósters grandes que dominan, flujo corto de Inicio a reproducir y vista previa en vivo de subtítulos. Lo que no: filtros en desplegables, acento único sin sistema de estados y poca orientación a lo que te toca ver.')}`;
  };
  P['actual:mount'] = () => {};
  P['portada:mount'] = function (el) {
    el.querySelectorAll('[data-wall]').forEach((host) => {
      const id = host.dataset.wall; if (!NJ.directions[id] || !NJ.screens.inicio) return;
      const box = document.createElement('div'); box.className = 'x-device'; host.appendChild(box);
      const savedPal = NJ.state.palette; NJ.state.palette = null;
      NJ.renderFrame(box, 'inicio', 'desktop', { dir: id, maxW: host.clientWidth || 300 });
      NJ.state.palette = savedPal;
      box.setAttribute('aria-hidden', 'true'); box.querySelectorAll('button,a,input,[tabindex]').forEach((b) => b.setAttribute('tabindex', '-1'));
    });
  };

  const MAP = [
    ['Inicio', 'Inicio', 'Las filas se pueden ordenar y ocultar; "Esta noche" junta continuar y lo que se estrena hoy.'],
    ['Buscar, Para ti', 'Explorar', 'Búsqueda, descubrimiento, Atlas, Top, búsqueda por imagen, personajes y catálogos de extensiones en un solo lugar.'],
    ['Calendario', 'Calendario', 'Semana, tablero de salidas o agenda; recordatorios por título.'],
    ['Biblioteca (7 pestañas)', 'Biblioteca', 'Medio Anime, Manga o Novela; estados, favoritos, historial. Los torrents salen de aquí.'],
    ['Manga, Novelas', 'Manga, Novelas', 'Siguen en el riel; en Android quedan dentro de Tú. Opción: un selector de medio en la barra superior.'],
    ['Descargas, Torrents, Ajustes › Torrents', 'Transferencias', 'Una sola cola con descargas web, torrents, debrid y biblioteca local.'],
    ['Extensiones, Debrid (dos lugares)', 'Fuentes', 'Pestañas Instaladas, Repositorios, Stremio, Proveedores, Servidores en tu PC y Debrid.'],
    ['Ajustes › Link, monitor, chip de la barra', 'Link', 'Pantalla propia con mapa de dispositivos, perfil de mejora visual y registros.'],
    ['Ajustes (13 secciones)', 'Ajustes (14 categorías)', 'Sistema deja de vivir dentro de Datos; Mejoras de video y Subtítulos tienen categoría propia; la búsqueda salta al ajuste.'],
    ['Perfil, Tú', 'Perfil / Tú', 'En escritorio, perfil de AniList; en Android, el hub Tú.'],
  ];
  P.arquitectura = function () {
    const rail = (items) => items.map((i) => `<li>${h.icon(i.icon)}<span>${esc(i.label)}</span></li>`).join('');
    return `<header style="display:grid;gap:8px"><h1>Arquitectura de información</h1><p class="x-lede">El riel pasa de 13 destinos en una lista plana a 6 destinos principales y 4 utilidades. Nada desaparece: cada función del inventario tiene su lugar nuevo en la página Paridad.</p></header>
    <section class="x-grid-2">
      <div class="x-panel"><h2>Hoy</h2><ol class="pg-rail is-old">${['Inicio', 'Buscar', 'Calendario', 'Para ti', 'Biblioteca', 'Manga', 'Novelas', 'Descargas', 'Torrents', 'Extensiones', 'Perfil', 'Ajustes'].map((x) => `<li>${esc(x)}</li>`).join('')}</ol><p class="x-note">Más Link en la barra de título, Ctrl+K y Ctrl+P. En Android: Inicio, Buscar, Calendario, Biblioteca y Tú.</p></div>
      <div class="x-panel"><h2>Propuesta</h2><div class="pg-railnew"><div><h3>Principal</h3><ul class="pg-rail">${rail(NJ.NAV.primary)}</ul></div><div><h3>Utilidades</h3><ul class="pg-rail">${rail(NJ.NAV.utility)}</ul></div><div><h3>Android</h3><ul class="pg-rail">${rail(NJ.NAV.android)}</ul></div></div></div>
    </section>
    <section class="x-panel"><h2>De dónde a dónde</h2><div class="x-scroll"><table class="x-table"><thead><tr><th>Hoy</th><th>Propuesta</th><th>Qué cambia</th></tr></thead><tbody>${MAP.map((r) => `<tr><td>${esc(r[0])}</td><td><b>${esc(r[1])}</b></td><td>${esc(r[2])}</td></tr>`).join('')}</tbody></table></div></section>
    <section class="x-grid-2">
      <div class="x-panel"><h2>Siempre a mano</h2><ul class="x-list"><li><b>Ctrl+K</b>: ir a cualquier pantalla, abrir un título, ejecutar un comando o saltar al ajuste exacto. En Android, la misma búsqueda.</li><li><b>Ctrl+,</b>: Ajustes en superposición (hoy Ctrl+P, que puede quedar como alias).</li><li><b>Soltar archivos</b> en cualquier parte: .kai, .torrent, magnet, video o subtítulo, con una capa que dice qué va a pasar.</li><li><b>Chip de Link</b> en la barra superior con el estado y los dispositivos.</li><li>La bandeja del sistema y las notificaciones de escritorio siguen igual; las notificaciones pasan a abrir la pantalla correcta, como en Android.</li></ul></div>
      <div class="x-panel"><h2>Tres navegaciones para probar</h2><ul class="x-list"><li><b>Riel</b>: todo visible, para quien usa todo.</li><li><b>Por medio</b>: un selector Anime · Manga · Novela en la barra superior y el riel sin Manga ni Novelas; Inicio, Explorar y Biblioteca cambian de medio.</li><li><b>Comandos</b>: riel compacto de iconos y la barra Ctrl+K como centro.</li></ul><p class="x-note">Se cambia desde la barra de arriba y afecta a todas las pantallas de escritorio.</p></div>
    </section>`;
  };

  P.notas = function () {
    return `<header style="display:grid;gap:8px"><h1>Implementación</h1><p class="x-lede">Cómo se lleva cualquier combinación a Compose Multiplatform sin rehacer la app dos veces.</p></header>
    <section class="x-grid-2">
      <div class="x-panel"><h2>Tokens y temas</h2><ul class="x-list"><li>Cada dirección es un paquete de tema: <code>ColorScheme</code> claro, oscuro y OLED por paleta, más un <code>CompositionLocal</code> propio para los colores de estado, las formas y la tipografía display.</li><li>Los tokens ya se generan desde <code>tokens/tokens.json</code>; se agregan los grupos de estado, forma y movimiento que hoy se generan pero no se usan.</li><li>El color dinámico de Android pasa a ser opcional y no viene encendido: hoy esconde la paleta elegida.</li></ul></div>
      <div class="x-panel"><h2>Tarjetas mezclables</h2><ul class="x-list"><li>Una interfaz <code>TitleCard(item, context, size)</code> con una implementación por estilo (tanzaku, obi, estación, hanko, Material). La pantalla nunca sabe cuál se usa.</li><li>Cada estilo resuelve los seis contextos: fila, rejilla, continuar, compacta, deseo y cabecera.</li></ul></div>
      <div class="x-panel"><h2>Movimiento</h2><ul class="x-list"><li>Un <code>MotionScheme</code> en un <code>CompositionLocal</code> con tres perfiles: Completo, Calmo y Rendimiento.</li><li>Calmo se activa solo cuando el sistema pide quitar animaciones (Android: <code>ANIMATOR_DURATION_SCALE = 0</code>).</li><li>Rendimiento se propone automáticamente en <code>isLowRamDevice</code>, con refresco de 60 Hz o con render por software en escritorio.</li><li>Los balanceos y trazos usan <code>Animatable</code> con <code>spring</code> en <code>graphicsLayer</code>, sin recomponer.</li></ul></div>
      <div class="x-panel"><h2>Idiomas</h2><ul class="x-list"><li>Todo el texto fijo que hoy está en español dentro del reproductor, la paleta, el monitor de Link y la bandeja pasa a recursos.</li><li>Las maquetas están en español latino; la app final sale en inglés y español latino.</li></ul></div>
    </section>
    <section class="x-panel"><h2>Orden sugerido después de elegir</h2><ol class="x-list"><li>Escribir DESIGN.md con la combinación elegida y cerrar las paletas.</li><li>Shell y navegación nuevos, con la paleta de comandos y la capa de soltar archivos.</li><li>Ficha, fuentes y reproductor (el camino diario), con multiselección y hojas agrupadas.</li><li>Biblioteca, Explorar y Calendario.</li><li>Transferencias, Fuentes y Link.</li><li>Ajustes reorganizados, con un solo modelo de guardado.</li><li>Lectores y narrador.</li><li>Auditoría final de paridad contra la matriz y QA en el teléfono.</li></ol></section>`;
  };
})();
