/* Dirección Tránsito nocturno (agente D3). Ver CONTRATO.md.
   Cada serie es una línea con su color (item.color), cada episodio una estación. Las líneas se dibujan con SVG
   sin viewBox: las estaciones usan cx en %, así el círculo no se deforma con el ancho de la tarjeta. */
(function () {
  const NJ = window.NJ;
  if (!NJ) return;
  const h = NJ.h;
  const esc = h.esc;
  const D = NJ.data;
  const READ_KINDS = { manga: 1, manhwa: 1, manhua: 1, novela: 1 };

  const FONTS = [
    'https://fonts.googleapis.com/css2?family=Atkinson+Hyperlegible+Next:wght@400;500;600;700;800&family=Overpass:wght@500;600;700;800;900&family=Overpass+Mono:wght@500;600&display=swap',
  ];

  /* ---------------- modelo de línea ---------------- */

  function num(n) { return String(n).padStart(2, '0'); }

  function norm(it) {
    const reading = !!it.chapter || !!READ_KINDS[it.kind];
    const total = Math.max(1, it.eps || it.total || 1);
    let done = it.progress || 0;
    if (reading) {
      const m = String(it.chapter || '').match(/\d+/);
      done = m ? +m[0] : Math.round((it.progress || 0) * total);
    }
    done = Math.min(Math.max(0, done), total);
    const aired = reading ? total : it.aired == null ? total : Math.min(it.aired, total);
    const resume = it.resume && it.resume.ep;
    const finished = !resume && done >= total;
    const cur = resume || (finished ? total : done + 1);
    const pending = !resume && !finished && cur > aired;
    return { reading, total, done, aired, cur, finished, pending, prefix: reading ? 'C' : 'E' };
  }

  function statesOf(m) {
    const s = [];
    for (let i = 1; i <= m.total; i++) s.push(i <= m.done ? 'w' : i <= m.aired ? 'a' : 'u');
    return s;
  }

  /* Para series largas o capítulos de manga se dibujan solo las estaciones que dicen algo. */
  function pick(N, keys, max) {
    if (N <= max) return Array.from({ length: N }, (_, i) => i);
    const set = new Set([0, N - 1]);
    keys.forEach((k) => { if (k >= 0 && k < N) set.add(k); });
    const step = Math.max(1, Math.round(N / (max - 4)));
    for (let i = step; i < N - 1; i += step) set.add(i);
    return Array.from(set).sort((a, b) => a - b);
  }

  /* states: 'w' visto, 'a' emitido sin ver, 'u' sin emitir, 'd' retrasado. cur: índice del tren (0..N-1) o -1. */
  function route(states, cur, o = {}) {
    const N = states.length;
    const X = (i) => (N === 1 ? 50 : (i / (N - 1)) * 100);
    const lastW = states.lastIndexOf('w');
    let lastA = -1;
    states.forEach((s, i) => { if (s !== 'u') lastA = i; });
    const ht = o.h || 14;
    const y = ht / 2;
    const idx = pick(N, [lastW, lastA, cur], o.max || 26);
    const p = lastW <= 0 ? 0 : X(lastW) / 100;
    const pa = lastA <= 0 ? 0 : X(lastA) / 100;
    const stations = idx.map((i, k) => `<circle class="tr-st" data-s="${states[i]}" data-k="${i}" cx="${X(i).toFixed(3)}%" cy="${y}" r="3.5" style="--i:${k}"></circle>`).join('');
    const svg = `<svg class="tr-svg" aria-hidden="true" focusable="false" width="100%" height="${ht}"><line class="tr-future" x1="0%" x2="100%" y1="${y}" y2="${y}"></line><line class="tr-track" x1="0%" x2="100%" y1="${y}" y2="${y}" pathLength="1"></line><line class="tr-run" x1="0%" x2="100%" y1="${y}" y2="${y}" pathLength="1"></line>${stations}</svg>`;
    const train = cur >= 0 ? `<span class="tr-train" aria-hidden="true" style="--x:${(X(cur) / 100).toFixed(4)}">${o.label != null ? `<b>${esc(o.label)}</b>` : ''}<i></i></span>` : '';
    let labels = '';
    if (o.labels) {
      const keys = new Set([0, N - 1, cur, lastA]);
      labels = `<span class="tr-labs" aria-hidden="true">${idx.map((i) => `<span class="tr-lab${keys.has(i) ? ' is-key' : ''}" data-k="${i}" style="--x:${(X(i) / 100).toFixed(4)}">${esc((o.prefix || 'E') + num(i + 1))}</span>`).join('')}</span>`;
    }
    const tag = o.block ? 'div' : 'span';
    return `<${tag} class="tr-route${o.cls ? ' ' + o.cls : ''}" style="--h:${ht}px;--p:${p.toFixed(4)};--pa:${pa.toFixed(4)}">${svg}${train}${labels}</${tag}>`;
  }

  function cardRoute(m, o = {}) {
    return route(statesOf(m), m.finished ? m.total - 1 : m.cur - 1, Object.assign({ prefix: m.prefix }, o));
  }

  function code(m, n, o = {}) {
    const digits = String(n).length;
    const cls = `tr-code${m.pending ? ' is-pending' : ''}${m.finished ? ' is-done' : ''}${o.cls ? ' ' + o.cls : ''}`;
    return `<span class="${cls}" aria-hidden="true" data-len="${Math.max(2, digits)}"><b>${m.prefix}</b><i>${num(n)}</i></span>`;
  }

  function srLine(m) {
    if (m.reading) return `Leído hasta el capítulo ${m.done} de ${m.total}.`;
    if (m.aired === 0) return `Sin episodios emitidos, ${m.total} en total.`;
    return `Visto ${m.done} de ${m.total}${m.aired < m.total ? `, ${m.aired} emitidos` : ''}.`;
  }

  function subOf(it, m, o) {
    const s = o.show || {};
    if (o.context === 'continue') {
      if (it.resume) return `Ep. ${it.resume.ep} · quedan ${it.resume.left}`;
      if (m.pending && it.next) return `Ep. ${it.next.ep} emite en ${it.next.in}`;
      return m.reading ? it.chapter : `Ep. ${m.cur} · siguiente`;
    }
    if (m.reading) return `${it.chapter} de ${m.total}`;
    if (o.context === 'wish') return it.next ? `Ep. ${it.next.ep} en ${it.next.in}` : m.aired === 0 ? 'Por estrenar' : `${it.format || ''} · ${m.total} ep.`;
    if (s.next && it.next) return `Ep. ${it.next.ep} en ${it.next.in}`;
    if (m.finished) return `Completada · ${m.total} ep.`;
    return `${it.format || ''} · ${it.year || ''}`;
  }

  function open(it, o, style, cls) {
    const color = it.color || (D.byId[it.id] && D.byId[it.id].color);
    const ln = color ? `--ln:${esc(color)}` : '';
    return `<button type="button" class="nj-card tr-card${cls ? ' ' + cls : ''}" data-style="${style}" data-context="${esc(o.context)}" data-size="${esc(o.size)}" data-status="${esc(it.status || '')}" data-id="${esc(it.id)}" style="${ln}">`;
  }

  const title = (it) => `<span class="nj-card-title">${esc(it.short || it.title)}</span>`;
  const sub = (txt) => `<span class="nj-card-sub">${esc(txt)}</span>`;
  const sr = (txt) => `<span class="tr-sr">${esc(txt)}</span>`;
  const play = () => `<span class="tr-play" aria-hidden="true">${h.icon('play_arrow', { fill: true })}</span>`;
  const chip = (it, s) => (s.status && it.status ? h.statusChip(it.status) : '');

  /* ---------------- tarjeta 1: estación ---------------- */
  function renderEstacion(it, o) {
    const s = o.show || {};
    const m = norm(it);
    const ctx = o.context;
    const tx = `<span class="tr-text">${title(it)}${sub(subOf(it, m, o))}</span>`;
    if (ctx === 'compact') {
      return `${open(it, o, 'transit-estacion')}<span class="tr-art">${h.poster(it, { alt: '' })}</span>${tx}${chip(it, s)}${code(m, m.cur, { cls: 'is-sm' })}${sr(srLine(m))}</button>`;
    }
    if (ctx === 'continue') {
      return `${open(it, o, 'transit-estacion')}${sr('Reproducir:')}<span class="tr-art">${h.poster(it, { alt: '' })}</span><span class="tr-body"><span class="tr-head">${code(m, m.cur)}${tx}</span>${cardRoute(m, { h: 14 })}</span>${play()}${sr(srLine(m))}</button>`;
    }
    if (ctx === 'hero') {
      return `${open(it, o, 'transit-estacion')}<span class="tr-art">${h.poster(it, { alt: '' })}${chip(it, s) ? `<span class="tr-badges">${chip(it, s)}</span>` : ''}</span><span class="tr-body"><span class="tr-head">${code(m, m.cur, { cls: 'is-lg' })}${tx}</span>${cardRoute(m, { h: 22, labels: true })}</span>${sr(srLine(m))}</button>`;
    }
    const badges = chip(it, s) ? `<span class="tr-badges">${chip(it, s)}</span>` : '';
    return `${open(it, o, 'transit-estacion')}<span class="tr-art">${h.poster(it, { alt: '' })}${badges}</span><span class="tr-plate">${code(m, m.cur)}${tx}${cardRoute(m, { h: 12 })}</span>${sr(srLine(m))}</button>`;
  }

  /* ---------------- tarjeta 2: billete ---------------- */
  function segs(it, m, o) {
    const left = it.resume ? ['Quedan', it.resume.left] : m.pending && it.next ? ['Emite en', it.next.in] : m.finished ? ['Visto', `${m.total}/${m.total}`] : m.aired === 0 ? ['Estreno', 'Sin fecha'] : ['Visto', `${m.done}/${m.total}`];
    const first = [m.reading ? 'Capítulo' : 'Episodio', `${m.prefix}${num(m.cur)}`];
    return `<span class="tr-segs"><span class="tr-seg"><small>${esc(first[0])}</small><b>${esc(first[1])}</b></span><i class="tr-perf" aria-hidden="true"></i><span class="tr-seg"><small>${esc(left[0])}</small><b>${esc(left[1])}</b></span>${o.context === 'continue' ? `<i class="tr-perf" aria-hidden="true"></i><span class="tr-seg is-go">${play()}</span>` : ''}</span>`;
  }

  function renderBillete(it, o) {
    const s = o.show || {};
    const m = norm(it);
    const ctx = o.context;
    const lead = ctx === 'continue' ? sr('Reproducir:') : '';
    if (ctx === 'compact') {
      return `${open(it, o, 'transit-billete')}<span class="tr-ticket is-h"><span class="tr-stub is-code">${code(m, m.cur, { cls: 'is-sm' })}</span><span class="tr-tbody"><span class="tr-text">${title(it)}${sub(subOf(it, m, o))}</span>${chip(it, s)}</span></span>${sr(srLine(m))}</button>`;
    }
    if (ctx === 'continue' || ctx === 'hero') {
      return `${open(it, o, 'transit-billete')}${lead}<span class="tr-ticket is-h"><span class="tr-stub">${h.poster(it, { alt: '' })}</span><span class="tr-tbody"><span class="tr-text">${title(it)}${ctx === 'hero' ? sub(subOf(it, m, o)) : ''}</span>${segs(it, m, o)}${cardRoute(m, { h: ctx === 'hero' ? 18 : 12, labels: ctx === 'hero' })}</span></span>${sr(srLine(m))}</button>`;
    }
    const badges = chip(it, s) ? `<span class="tr-badges">${chip(it, s)}</span>` : '';
    return `${open(it, o, 'transit-billete')}<span class="tr-ticket is-v"><span class="tr-stub">${h.poster(it, { alt: '' })}${badges}</span><span class="tr-tbody">${title(it)}${segs(it, m, o)}${s.progress ? cardRoute(m, { h: 10 }) : ''}</span></span>${sr(srLine(m))}</button>`;
  }

  /* ---------------- tarjeta 3: línea ---------------- */
  function renderLinea(it, o) {
    const s = o.show || {};
    const m = norm(it);
    const ctx = o.context;
    const tx = `<span class="tr-text">${title(it)}${sub(subOf(it, m, o))}</span>`;
    if (ctx === 'compact' || ctx === 'continue') {
      return `${open(it, o, 'transit-linea')}${ctx === 'continue' ? sr('Reproducir:') : ''}${code(m, m.cur, { cls: 'is-sm' })}${tx}${cardRoute(m, { h: 14, cls: 'is-row' })}${ctx === 'continue' ? play() : chip(it, s)}${sr(srLine(m))}</button>`;
    }
    if (ctx === 'hero') {
      return `${open(it, o, 'transit-linea')}<span class="tr-head"><span class="tr-thumb">${h.poster(it, { alt: '' })}</span>${code(m, m.cur, { cls: 'is-lg' })}${tx}${chip(it, s)}</span>${cardRoute(m, { h: 22, labels: true })}${sr(srLine(m))}</button>`;
    }
    const foot = `<span class="tr-foot">${code(m, m.cur, { cls: 'is-xs' })}<span class="tr-foot-txt">${esc(m.reading ? `${m.done} / ${m.total}` : m.aired === 0 ? 'Sin emitir' : `${m.done} de ${m.total}`)}</span>${chip(it, s)}</span>`;
    return `${open(it, o, 'transit-linea')}<span class="tr-head"><span class="tr-thumb">${h.poster(it, { alt: '' })}</span>${tx}</span>${cardRoute(m, { h: 12 })}${foot}${sr(srLine(m))}</button>`;
  }

  /* ---------------- dirección ---------------- */
  NJ.registerDirection({
    id: 'transit',
    name: 'Tránsito nocturno',
    track: 'free',
    tagline: 'Señalética de metro sobre grafito: cada serie es una línea de color, cada episodio una estación.',
    summary: 'La app sigue siendo una app: rieles, listas y controles estándar. El metro presta el tipo de señalética, los colores de línea, la densidad de un tablero de salidas y un gesto propio: el tren que avanza por la línea de episodios.',
    fonts: FONTS,
    palettes: [
      { id: 'grafito', name: 'Grafito', swatch: '#4f9dff' },
      { id: 'anden', name: 'Andén', swatch: '#bf231a' },
      { id: 'yamanote', name: 'Línea Yamanote', swatch: '#9acd32' },
      { id: 'ultima', name: 'Última salida', swatch: '#ffb547' },
    ],
    defaultCard: 'transit-estacion',
    notes: {
      thesis: 'Seguir una temporada es viajar en una línea: sabés en qué estación estás, cuál viene y cuánto falta. Cada serie toma su color propio como color de línea, los episodios son estaciones con número en círculo (E09, como G09 en Ginza), las temporadas y relaciones son transbordos y Link es la estación donde se cambia de dispositivo. El calendario de emisión es un tablero de salidas. Todo lo demás es una herramienta nocturna sobria sobre grafito neutro, para que las líneas de color sean las protagonistas; el esmalte azul queda reservado a tableros y carteles (calendario de salidas), y el modo claro es esmalte blanco de andén.',
      signature: 'El indicador de "estás aquí" recorre la línea de episodios: al pasar el puntero o el foco por un episodio el tren se desplaza hasta esa estación, y al marcar uno como visto la estación se llena y el tren avanza a la siguiente. Al entrar en una ficha la línea se dibuja de izquierda a derecha y el tren llega al próximo episodio.',
      motion: 'Un solo momento por pantalla. Ficha: trazo de la línea con stroke-dashoffset (520 ms), estaciones que aparecen en el orden del trazo y llegada del tren (500 ms, retraso 380 ms), curva exponencial de salida cubic-bezier(.16,1,.3,1). Inicio: la vía de "Esta noche" se tiende bajo las tarjetas. Calendario: las filas del tablero caen como paletas de un tablero de salidas, 30 ms entre filas y tope de 240 ms. Calmo y movimiento reducido: sin trazo ni desplazamientos, el estado final aparece directo. Rendimiento: solo el trazo de la línea, sin escalonado ni llegada del tren.',
      android: 'Material 3 intacto: barra inferior, hojas, chips y conmutadores estándar con la paleta como esquema de color. Los rótulos de estación respetan 48 dp porque la tarjeta entera es el objetivo táctil; los círculos de estación no son botones sueltos. En la ficha del teléfono la línea de episodios muestra solo los números clave (primero, siguiente, último emitido, último) para no apretar etiquetas; en tableta y TV se ven todos. El foco del D-pad mueve el tren igual que el puntero.',
      risks: 'Los colores de serie vienen de los pósters y algunos son claros (beige, amarillo): la línea se mezcla con blanco o negro según el modo para llegar a 3:1, y el texto del rótulo va siempre en tinta sobre porcelana. Con más de 26 episodios la línea se muestrea y deja de ser un mapa literal. Si se abusa del vocabulario (andén, transbordo) en el copy la app se vuelve disfraz: el texto visible sigue diciendo "Episodio", "Siguiente" y "Visto". El tablero oscuro dentro del modo claro es una isla deliberada que puede sentirse pesada en pantallas chicas.',
      compose: 'La línea es un Canvas en Modifier.drawBehind: drawLine para la vía y para el tramo visto, con PathEffect.dashPathEffect(floatArrayOf(len, len), phase) donde phase sale de un Animatable<Float> que va de len a 0 al entrar (tween 520 ms, EaseOutExpo). Lo no emitido usa dashPathEffect(floatArrayOf(3.dp, 4.dp)) fijo. Las estaciones son drawCircle con radio y color animados con animateFloatAsState y animateColorAsState según watched/aired. El tren es un Box con Modifier.offset { IntOffset(x.roundToPx(), 0) } y x = animateDpAsState(stationX(index)), así se mueve solo en el layout y no recompone la lista. El rótulo E09 es un Surface circular con borde del color de línea y texto en Overpass con FontFeatureSettings "tnum". Las paletas son ColorScheme estáticos (claro, oscuro y un oscuro OLED con background negro) y el color de cada serie se inyecta con CompositionLocal. Modo rendimiento: sin Animatable de llegada ni escalonado, phase salta a 0 tras el primer trazo, y el tablero no anima paletas; nada de graphicsLayer con blur.',
    },
    decorate,
  });

  NJ.registerCard({ id: 'transit-estacion', name: 'Estación', dir: 'transit', desc: 'Póster con rótulo de estación debajo: círculo con el código del episodio en el color de la serie y la línea de episodios con el tren en el siguiente.', render: renderEstacion });
  NJ.registerCard({ id: 'transit-billete', name: 'Billete', dir: 'transit', desc: 'Billete con troquel y segmentos (episodio y tiempo restante). Pensada para continuar viendo: se lee de un vistazo desde el sofá.', render: renderBillete });
  NJ.registerCard({ id: 'transit-linea', name: 'Línea', dir: 'transit', desc: 'Una fila por serie con su línea y estaciones. La más densa, para listas, búsqueda y biblioteca.', render: renderLinea });

  /* ---------------- decoración de regiones ---------------- */
  const visited = new WeakMap();
  const truthy = (v) => v != null && v !== 'false' && v !== '0';

  function decorate(app, ctx) {
    if (!app) return;
    ctx = ctx || {};
    const host = (app.parentElement && app.parentElement.closest('.x-device')) || app.parentElement || app;
    const screen = app.getAttribute('data-screen') || ctx.screen || 'tarjetas';
    const entering = visited.get(host) !== screen;
    visited.set(host, screen);
    if (entering) {
      app.setAttribute('data-tr-enter', '');
      setTimeout(() => app.removeAttribute('data-tr-enter'), 1800);
    }
    app.querySelectorAll('[data-region="tonight"]').forEach((r) => tonight(r));
    app.querySelectorAll('[data-region="airing"]').forEach((r) => airing(r));
    app.querySelectorAll('[data-region="episodes"]').forEach((r) => episodes(app, r));
    app.querySelectorAll('[data-region="link"]').forEach((r) => link(r));
    app.querySelectorAll('[data-region="player-seek"]').forEach((r) => seek(r));
    app.querySelectorAll('[data-region="empty"]').forEach((r) => empty(r));
  }

  function itemOf(el) {
    const id = el.getAttribute('data-id') || (el.querySelector('[data-id]') || { getAttribute: () => null }).getAttribute('data-id');
    return id && D.byId[id];
  }
  function paint(el, it) {
    if (it && it.color && !el.style.getPropertyValue('--ln')) el.style.setProperty('--ln', it.color);
    el.setAttribute('data-tr-ln', '');
  }

  function tonight(region) {
    const row = region.matches('.nj-row') ? region : region.querySelector('.nj-row');
    if (!row) return;
    const kids = Array.from(row.children).filter((k) => k.offsetParent !== null || k.getClientRects().length);
    if (kids.length > 1 && kids[0].offsetTop !== kids[1].offsetTop) return; /* pila vertical: sin vía */
    row.classList.add('tr-tracked');
    kids.forEach((k, i) => { paint(k, itemOf(k)); k.style.setProperty('--i', Math.min(i, 8)); k.classList.add('tr-stop'); });
  }

  function airing(region) {
    region.classList.add('tr-board');
    region.querySelectorAll('[data-time]').forEach((el, i) => {
      el.style.setProperty('--i', Math.min(i, 8));
      const it = itemOf(el);
      if (it) paint(el, it);
    });
  }

  /* Ficha: línea de episodios sobre los .nj-ep existentes, sin tocar su orden ni su contenido. */
  function episodes(app, region) {
    const eps = Array.from(region.querySelectorAll('.nj-ep'));
    if (!eps.length) return;
    /* En la Ficha "Ruta" la fila de estaciones de la pantalla ya es la línea: no se duplica. */
    if (app.getAttribute('data-screen') === 'ficha' && app.getAttribute('data-structure') === 'ruta') return;
    const old = region.querySelector(':scope .tr-epline');
    if (old) old.remove();
    const heroIt = itemOf(region) || (() => { const hr = app.querySelector('[data-region="hero"]'); return hr && itemOf(hr); })() || (app.getAttribute('data-screen') === 'ficha' ? D.byId.laidoff : null);
    if (heroIt && heroIt.color) region.style.setProperty('--ln', heroIt.color);
    region.setAttribute('data-tr-ln', '');
    const plat = app.getAttribute('data-platform');

    const read = () => eps
      .map((el, i) => ({ el, n: parseInt(el.getAttribute('data-ep'), 10) || i + 1, w: truthy(el.getAttribute('data-watched')), a: el.hasAttribute('data-aired') ? truthy(el.getAttribute('data-aired')) : true, d: truthy(el.getAttribute('data-delayed')) || el.getAttribute('data-state') === 'delayed', next: el.getAttribute('data-next') === 'true' }))
      .sort((x, y) => x.n - y.n);
    const model = () => {
      const list = read();
      const states = list.map((e) => (e.w ? 'w' : e.d ? 'd' : e.a ? 'a' : 'u'));
      const lastW = states.lastIndexOf('w');
      let next = list.findIndex((e) => e.next && !e.w);
      if (next < 0) next = states.findIndex((s, i) => i > lastW && s === 'a');
      if (next < 0) next = states.indexOf('a');
      return { list, states, next };
    };
    let mdl = model();
    const labelOf = (i) => (i >= 0 ? 'E' + num(mdl.list[i].n) : '');
    const cur0 = mdl.next >= 0 ? mdl.next : Math.max(0, mdl.states.lastIndexOf('w'));

    const strip = document.createElement('div');
    strip.className = 'tr-epline';
    strip.setAttribute('data-tr-ln', '');
    strip.innerHTML = `<div class="tr-epline-head"><span class="tr-code is-lg" aria-hidden="true" data-len="2"><b>E</b><i></i></span><div class="tr-epline-read"><b class="tr-epline-title"></b><span class="nj-meta tr-epline-meta"></span></div>${plat === 'android' ? '' : `<ul class="tr-legend" aria-label="Leyenda de la línea"><li data-s="w">Visto</li><li data-s="next">Siguiente</li><li data-s="a">Emitido</li><li data-s="u">Por emitir</li><li data-s="d">Retrasado</li></ul>`}</div>${route(mdl.states, cur0, { h: 30, labels: true, label: labelOf(cur0), cls: 'is-strip', block: true, max: 30 })}`;
    const first = eps[0];
    const list = first.parentElement;
    if (list && list !== region && region.contains(list)) list.parentElement.insertBefore(strip, list);
    else region.insertBefore(strip, first);

    const rt = strip.querySelector('.tr-route');
    const train = strip.querySelector('.tr-train');
    const N = mdl.states.length;
    const X = (i) => (N === 1 ? 0.5 : i / (N - 1));

    function moveTo(i, preview) {
      if (!train || i < 0) return;
      train.style.setProperty('--x', X(i).toFixed(4));
      const b = train.querySelector('b');
      if (b) b.textContent = labelOf(i);
      strip.toggleAttribute('data-preview', !!preview);
      strip.querySelectorAll('.tr-lab').forEach((l) => l.classList.toggle('is-here', +l.getAttribute('data-k') === i));
    }
    function sync() {
      mdl = model();
      const lastW = mdl.states.lastIndexOf('w');
      let lastA = -1;
      mdl.states.forEach((s, i) => { if (s !== 'u') lastA = i; });
      rt.style.setProperty('--p', (lastW <= 0 ? 0 : X(lastW)).toFixed(4));
      rt.style.setProperty('--pa', (lastA <= 0 ? 0 : X(lastA)).toFixed(4));
      strip.querySelectorAll('.tr-st').forEach((c) => c.setAttribute('data-s', mdl.states[+c.getAttribute('data-k')]));
      mdl.list.forEach((e, i) => e.el.setAttribute('data-tr', i === mdl.next ? 'next' : mdl.states[i]));
      const watched = mdl.states.filter((s) => s === 'w').length;
      const airedN = mdl.states.filter((s) => s !== 'u').length;
      const left = N - airedN;
      const nx = mdl.next >= 0 ? mdl.list[mdl.next].n : null;
      strip.querySelector('.tr-code i').textContent = num(nx || (mdl.list[Math.max(0, lastW)] || { n: 1 }).n);
      strip.querySelector('.tr-epline-title').textContent = nx ? `Siguiente: episodio ${nx}` : airedN && watched >= airedN ? 'Al día con lo emitido' : 'Sin episodios emitidos';
      strip.querySelector('.tr-epline-meta').textContent = `Visto ${watched} de ${airedN} emitidos${left ? ` · ${left} por emitir` : ''}`;
      moveTo(mdl.next >= 0 ? mdl.next : Math.max(0, lastW), false);
    }
    sync();

    const idxOfEp = (el) => mdl.list.findIndex((e) => e.el === el);
    region.addEventListener('pointerover', (ev) => { const ep = ev.target.closest('.nj-ep'); if (ep && region.contains(ep)) moveTo(idxOfEp(ep), true); });
    region.addEventListener('focusin', (ev) => { const ep = ev.target.closest('.nj-ep'); if (ep) moveTo(idxOfEp(ep), true); });
    const back = () => moveTo(mdl.next >= 0 ? mdl.next : Math.max(0, mdl.states.lastIndexOf('w')), false);
    region.addEventListener('pointerleave', back);
    region.addEventListener('focusout', (ev) => { if (!region.contains(ev.relatedTarget)) back(); });
    if (window.MutationObserver) {
      new MutationObserver(sync).observe(region, { subtree: true, attributes: true, attributeFilter: ['data-watched', 'data-aired', 'data-delayed', 'data-state', 'data-next'] });
    }
  }

  /* Link: el transbordo entre la línea del PC y la del teléfono. */
  function link(region) {
    if (region.querySelector(':scope > .tr-xfer')) return;
    /* Convención: [data-device][data-state="connected|idle|failed"]; el transbordo vale lo que su tramo más débil. */
    const devs = Array.from(region.querySelectorAll('[data-device][data-state]')).map((d) => d.getAttribute('data-state'));
    const st = devs.includes('failed') ? 'failed' : devs.includes('idle') ? 'idle' : devs.length ? 'connected' : region.getAttribute('data-state') || (D.link && D.link.state) || 'connected';
    region.setAttribute('data-tr-link', st);
    const el = document.createElement('div');
    el.className = 'tr-xfer';
    el.setAttribute('aria-hidden', 'true');
    el.innerHTML = '<svg width="100%" height="40" focusable="false"><line class="tr-xfer-a" x1="0" x2="50%" y1="20" y2="20" pathLength="1"></line><line class="tr-xfer-b" x1="50%" x2="100%" y1="20" y2="20" pathLength="1"></line><circle class="tr-xfer-end is-a" cx="0" cy="20" r="5"></circle><circle class="tr-xfer-end is-b" cx="100%" cy="20" r="5"></circle><svg x="50%" y="0" overflow="visible"><rect class="tr-xfer-cap" x="-25" y="8" width="50" height="24" rx="12"></rect><circle class="tr-xfer-st is-a" cx="-12" cy="20" r="6"></circle><circle class="tr-xfer-st is-b" cx="12" cy="20" r="6"></circle></svg></svg>';
    region.insertBefore(el, region.firstChild);
  }

  /* Reproductor: capítulos como estaciones sobre la barra. */
  function seek(region) {
    const raw = region.getAttribute('data-chapters');
    if (!raw || region.querySelector('.tr-chapters')) return;
    const toSec = (t) => { const s = String(t).trim(); if (/^\d+(:\d+){1,2}$/.test(s)) return s.split(':').reduce((a, b) => a * 60 + +b, 0); return parseFloat(s); };
    let pts = [];
    try {
      const j = JSON.parse(raw);
      pts = [].concat(j).map((c) => (typeof c === 'object' && c ? toSec(c.at ?? c.t ?? c.start ?? c.pos ?? c.time) : toSec(c)));
    } catch (e) {
      pts = raw.split(/[,;|\s]+/).filter(Boolean).map((t) => toSec(t.split('=')[0]));
    }
    pts = pts.filter((v) => isFinite(v));
    if (!pts.length) return;
    const dur = toSec(region.getAttribute('data-duration') || '');
    const max = Math.max.apply(null, pts);
    const f = pts.map((v) => (max <= 1 ? v : isFinite(dur) && dur > 0 ? v / dur : max <= 100 ? v / 100 : v / max)).filter((v) => v > 0.001 && v < 0.999);
    const target = region.querySelector('input[type="range"], [role="slider"], .nj-progress') || region;
    let x = 0, y = 0, n = target;
    while (n && n !== region) { x += n.offsetLeft; y += n.offsetTop; n = n.offsetParent; }
    const box = document.createElement('div');
    box.className = 'tr-chapters';
    box.setAttribute('aria-hidden', 'true');
    if (target !== region) Object.assign(box.style, { left: x + 'px', top: y + 'px', width: target.offsetWidth + 'px', height: target.offsetHeight + 'px', right: 'auto', bottom: 'auto' });
    box.innerHTML = f.map((v) => `<i style="--x:${v.toFixed(4)}"></i>`).join('');
    region.appendChild(box);
  }

  function empty(region) {
    if (region.querySelector('.tr-empty-art')) return;
    const el = document.createElement('div');
    el.className = 'tr-empty-art';
    el.setAttribute('aria-hidden', 'true');
    el.innerHTML = '<svg width="176" height="44" focusable="false"><line class="tr-e-run" x1="6" x2="112" y1="22" y2="22"></line><line class="tr-e-gap" x1="124" x2="170" y1="22" y2="22"></line><circle class="tr-e-st" cx="6" cy="22" r="4.5"></circle><circle class="tr-e-st" cx="56" cy="22" r="4.5"></circle><circle class="tr-e-end" cx="106" cy="22" r="8"></circle><line class="tr-e-stop" x1="122" x2="122" y1="12" y2="32"></line></svg>';
    region.insertBefore(el, region.firstChild);
  }
})();
