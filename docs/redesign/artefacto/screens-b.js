/* Pantallas del agente S2: Explorar, Calendario, Biblioteca, Ajustes y Perfil/Tú. Ver CONTRATO.md.
   Todo el estado vive en ctx.state; los eventos se enganchan dentro de `el` en mount(). */
(function () {
  const NJ = window.NJ; if (!NJ) return;
  const D = NJ.data, h = NJ.h, esc = h.esc;
  const ic = (n, o) => h.icon(n, o);
  const isA = (ctx) => ctx.platform === 'android';
  const pid = (ctx, s) => (isA(ctx) ? 'a' : 'd') + '-b-' + s;
  const fold = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const clone = (v) => (v && typeof v === 'object' ? JSON.parse(JSON.stringify(v)) : v);
  function defaults(st, o) { for (const k in o) if (!(k in st)) st[k] = clone(o[k]); return st; }
  const on = (arr, v) => (arr || []).includes(v);
  const toggleIn = (arr, v) => (on(arr, v) ? arr.filter((x) => x !== v) : arr.concat([v]));

  /* Re-render conservando scroll y foco (los ids son estables por plataforma). */
  function keep(el, ctx) {
    const st = ctx.state; const sc = { __main: [el.scrollTop, el.scrollLeft] };
    el.querySelectorAll('[data-keep]').forEach((n) => { sc[n.dataset.keep] = [n.scrollTop, n.scrollLeft]; });
    st._scroll = sc;
    const a = document.activeElement;
    st._focus = a && el.contains(a) && a.id ? { id: a.id, s: a.selectionStart, e: a.selectionEnd } : null;
    (st._ctx || ctx).rerender();
  }
  function restore(el, ctx) {
    const st = ctx.state;
    if (st._scroll) {
      const sc = st._scroll; el.scrollTop = sc.__main[0]; el.scrollLeft = sc.__main[1];
      el.querySelectorAll('[data-keep]').forEach((n) => { const v = sc[n.dataset.keep]; if (v) { n.scrollTop = v[0]; n.scrollLeft = v[1]; } });
      st._scroll = null;
    }
    if (st._focus) {
      const f = el.querySelector('#' + CSS.escape(st._focus.id));
      if (f) { f.focus({ preventScroll: true }); try { if (st._focus.s != null) f.setSelectionRange(st._focus.s, st._focus.e); } catch (e) { /* no es campo de texto */ } }
      st._focus = null;
    }
  }
  /* Temporizador que se apaga solo cuando el marco desaparece. */
  function tick(el, ms, fn) { const t = setInterval(() => { if (!el.isConnected) { clearInterval(t); return; } fn(); }, ms); return t; }
  function later(el, ms, fn) { setTimeout(() => { if (el.isConnected) fn(); }, ms); }
  /* Igual que later, pero sigue al marco vigente aunque se haya re-dibujado entretanto. */
  function laterS(st, ms, fn) { setTimeout(() => { const cur = st._el; if (cur && cur.isConnected) fn(cur); }, ms); }

  /* Cableado común: [data-act] llama a H[act]; [data-bset] es un grupo de chips/segmentado ligado a st[key]. */
  function wire(el, ctx, H) {
    const st = ctx.state; const redraw = () => keep(el, ctx);
    st._el = el; st._ctx = ctx; restore(el, ctx);
    const dlg = el.querySelector('.nj-b-overlay [role="dialog"], .nj-b-overlay [role="alertdialog"]');
    if (dlg && !dlg.contains(document.activeElement)) dlg.focus({ preventScroll: true });
    el.addEventListener('click', (e) => {
      const opt = e.target.closest('[data-bset] .nj-chip, [data-bset] [role="radio"]');
      if (opt && el.contains(opt)) {
        const box = opt.closest('[data-bset]'); const key = box.dataset.bset; const v = opt.dataset.value; const mode = box.dataset.bmode || 'single';
        const path = key.split('.'); let o = st; while (path.length > 1) o = o[path.shift()]; const k = path[0];
        if (mode === 'multi') o[k] = toggleIn(o[k] || [], v); else if (mode === 'opt') o[k] = o[k] === v ? null : v; else o[k] = v;
        e.stopPropagation(); if (H.$set) H.$set(key, o[k], box); redraw(); return;
      }
      const a = e.target.closest('[data-act]');
      if (a && a.dataset.act === 'noop') return;
      if (a && el.contains(a) && !a.disabled) { const fn = H[a.dataset.act]; if (fn) { e.stopPropagation(); e.preventDefault(); fn(a, e, redraw); } return; }
      const go = e.target.closest('[data-go]');
      if (go && el.contains(go) && !NJ.screens[go.dataset.go === 'tu' ? 'perfil' : go.dataset.go]) { e.stopPropagation(); snack(el, ctx, 'Esa pantalla se diseña en otra parte del explorador.'); }
    });
    /* Escape también cuando el foco quedó en body tras un re-render; se desengancha solo. */
    const kd = (e) => {
      if (!el.isConnected) { document.removeEventListener('keydown', kd); return; }
      if (e.key !== 'Escape' || !H.$esc) return;
      if (e.target !== document.body && !el.contains(e.target)) return;
      if (H.$esc(redraw)) e.stopPropagation();
    };
    document.addEventListener('keydown', kd);
    return redraw;
  }

  /* Snackbar única con acción opcional; se dibuja dentro del marco. */
  function snackHtml(st) {
    if (!st._snack) return '';
    return `<div class="nj-b-snackwrap" role="status" aria-live="polite"><div class="nj-snackbar">${esc(st._snack.text)}${st._snack.undo ? `<button type="button" class="nj-btn is-text" data-act="undo">Deshacer</button>` : ''}<button type="button" class="nj-iconbtn" data-act="snackClose" aria-label="Cerrar aviso">${ic('close', { size: 18 })}</button></div></div>`;
  }
  function snack(el, ctx, text, undo) {
    const st = ctx.state; st._snack = { text, undo: !!undo, id: Date.now() }; st._undo = undo || null;
    const sid = st._snack.id; const cur = st._el && st._el.isConnected ? st._el : el; keep(cur, ctx);
    laterS(st, 6000, (now) => { if (st._snack && st._snack.id === sid) { st._snack = null; st._undo = null; keep(now, ctx); } });
  }
  const snackHandlers = (el, ctx) => ({
    undo: (a, e, redraw) => { const u = ctx.state._undo; ctx.state._snack = null; ctx.state._undo = null; if (u) u(); redraw(); },
    snackClose: (a, e, redraw) => { ctx.state._snack = null; redraw(); },
  });

  /* Aviso de límite de AniList con cuenta regresiva viva (UI-01). */
  function limitBanner(ctx, st, o = {}) {
    if (st.limitDismissed) return '';
    if (st.limitRetrying) return `<div class="nj-b-banner" role="status"><span class="nj-b-spin" aria-hidden="true"></span><span class="nj-b-banner-text">Reintentando con AniList…</span></div>`;
    const s = st.limitLeft == null ? 42 : st.limitLeft;
    return `<div class="nj-b-banner is-warn" role="status">${ic('hourglass_top')}<span class="nj-b-banner-text"><b>AniList está limitando las consultas.</b> ${esc(o.using || 'Usando Kitsu mientras tanto')}. Reintento en <span data-limit-left class="nj-b-tnum">0:${String(s).padStart(2, '0')}</span></span>${h.btn('Reintentar ahora', { kind: 'text', attrs: { 'data-act': 'limitRetry' } })}${h.iconBtn('close', 'Ocultar aviso', { attrs: { 'data-act': 'limitHide' } })}</div>`;
  }
  function limitWire(el, ctx) {
    const st = ctx.state;
    tick(el, 1000, () => {
      if (st.limitDismissed || st.limitRetrying) return;
      st.limitLeft = Math.max(0, (st.limitLeft == null ? 42 : st.limitLeft) - 1);
      const n = el.querySelector('[data-limit-left]'); if (n) n.textContent = '0:' + String(st.limitLeft).padStart(2, '0');
      if (st.limitLeft === 0) st.limitLeft = 42;
    });
    return {
      limitRetry: (a, e, redraw) => { st.limitRetrying = true; redraw(); laterS(st, 1400, (now) => { st.limitRetrying = false; st.limitLeft = 60; snack(now, ctx, 'AniList sigue limitado. Se usan los datos guardados.'); }); },
      limitHide: (a, e, redraw) => { st.limitDismissed = true; redraw(); },
    };
  }

  /* Hoja: inferior en Android, panel lateral en escritorio. */
  function sheet(ctx, title, body, o = {}) {
    const side = !isA(ctx);
    return `<div class="nj-overlay nj-b-overlay${side ? ' is-side' : ''}" data-act="${o.close || 'sheetClose'}" data-region="sheet"><section class="nj-sheet${side ? ' is-side' : ''} nj-b-sheet${o.wide ? ' is-wide' : ''}" tabindex="-1" role="dialog" aria-modal="true" aria-label="${esc(title)}" data-act="noop"><header class="nj-b-sheet-head"><h2 class="nj-h2">${esc(title)}</h2>${h.iconBtn('close', 'Cerrar', { attrs: { 'data-act': o.close || 'sheetClose' } })}</header><div class="nj-b-sheet-body" data-keep="sheet">${body}</div>${o.foot ? `<footer class="nj-b-sheet-foot">${o.foot}</footer>` : ''}</section></div>`;
  }

  function appbar(title, actions = '', o = {}) {
    return `<header class="nj-appbar nj-b-appbar">${o.back ? h.iconBtn('arrow_back', 'Volver', { attrs: { 'data-act': o.back } }) : ''}<h1 class="nj-h2">${esc(title)}</h1>${actions}</header>`;
  }
  function stepper(ctx, key, label, value, o = {}) {
    return `<div class="nj-b-stepper" role="group" aria-label="${esc(label)}">${h.iconBtn('remove', 'Bajar ' + label, { kind: 'tonal', attrs: { 'data-act': 'step', 'data-key': key, 'data-d': -(o.step || 1), disabled: o.min != null && value <= o.min } })}<output class="nj-b-stepper-val nj-b-tnum" aria-live="polite">${esc(o.fmt ? o.fmt(value) : value)}</output>${h.iconBtn('add', 'Subir ' + label, { kind: 'tonal', attrs: { 'data-act': 'step', 'data-key': key, 'data-d': o.step || 1, disabled: o.max != null && value >= o.max } })}</div>`;
  }
  function dualRange(key, label, min, max, step, val, fmt) {
    const [lo, hi] = val; const pc = (v) => ((v - min) / (max - min)).toFixed(4);
    return `<div class="nj-b-dual" data-dual="${key}" data-min="${min}" data-max="${max}" style="--lo:${pc(lo)};--hi:${pc(hi)}"><div class="nj-b-dual-head"><span>${esc(label)}</span><output class="nj-b-tnum">${esc(fmt(lo, hi))}</output></div><div class="nj-b-dual-track"><i></i><input type="range" min="${min}" max="${max}" step="${step}" value="${lo}" data-end="0" aria-label="${esc(label)}: desde"><input type="range" min="${min}" max="${max}" step="${step}" value="${hi}" data-end="1" aria-label="${esc(label)}: hasta"></div><div class="nj-b-dual-scale nj-b-tnum"><span>${min}</span><span>${max}</span></div></div>`;
  }
  function dualWire(el, ctx, st, onMove) {
    el.addEventListener('input', (e) => {
      const r = e.target; const box = r.closest('[data-dual]'); if (!box) return;
      const key = box.dataset.dual; const min = +box.dataset.min; const max = +box.dataset.max;
      const ins = box.querySelectorAll('input'); let lo = +ins[0].value; let hi = +ins[1].value;
      if (lo > hi) { if (r.dataset.end === '0') { lo = hi; ins[0].value = lo; } else { hi = lo; ins[1].value = hi; } }
      const path = key.split('.'); let o = st; while (path.length > 1) o = o[path.shift()]; o[path[0]] = [lo, hi];
      box.style.setProperty('--lo', (lo - min) / (max - min)); box.style.setProperty('--hi', (hi - min) / (max - min));
      if (onMove) onMove(key, lo, hi, box);
    });
  }
  const anyRange = (min, max, unit = '') => (lo, hi) => (lo <= min && hi >= max ? 'Cualquiera' : lo <= min ? `Hasta ${hi}${unit}` : hi >= max ? `Desde ${lo}${unit}` : lo === hi ? `${lo}${unit}` : `${lo} a ${hi}${unit}`);

  /* ======================================================================
     1. EXPLORAR (Buscar + Para ti + Atlas + Top + trace.moe + personajes + catálogos)
     ====================================================================== */
  const FORMATS = [{ id: 'TV', label: 'TV' }, { id: 'TV Short', label: 'TV corto' }, { id: 'Película', label: 'Película' }, { id: 'OVA', label: 'OVA' }, { id: 'ONA', label: 'ONA' }, { id: 'Especial', label: 'Especial' }, { id: 'Música', label: 'Música' }];
  const ESTADOS = [{ id: 'emision', label: 'En emisión' }, { id: 'finalizado', label: 'Finalizado' }, { id: 'proximo', label: 'Próximamente' }, { id: 'cancelado', label: 'Cancelado' }, { id: 'pausa', label: 'En pausa' }];
  const GENEROS = ['Acción', 'Aventura', 'Comedia', 'Drama', 'Ecchi', 'Fantasía', 'Terror', 'Isekai', 'Mahō shōjo', 'Mecha', 'Música', 'Misterio', 'Psicológico', 'Recuentos de la vida', 'Romance', 'Ciencia ficción', 'Deportes', 'Sobrenatural', 'Suspenso', 'Escolar'];
  const TEMPORADAS = [{ id: 'invierno', label: 'Invierno' }, { id: 'primavera', label: 'Primavera' }, { id: 'verano', label: 'Verano' }, { id: 'otono', label: 'Otoño' }];
  const ORDENES = [{ id: 'relevancia', label: 'Relevancia' }, { id: 'popularidad', label: 'Popularidad' }, { id: 'tendencia', label: 'Tendencia' }, { id: 'puntuacion', label: 'Puntuación' }, { id: 'inicio', label: 'Fecha de inicio' }, { id: 'titulo', label: 'Título' }, { id: 'favoritos', label: 'Favoritos' }, { id: 'episodios', label: 'Episodios' }];
  const MILISTA = [{ id: 'fuera', label: 'Fuera de mi lista' }].concat(D.statuses.map((s) => ({ id: s.id, label: s.name })));
  const FEEDS = [{ id: 'tendencia', label: 'En tendencia', icon: 'trending_up' }, { id: 'populares', label: 'Populares', icon: 'groups' }, { id: 'valorados', label: 'Mejor valorados', icon: 'star' }, { id: 'emision', label: 'En emisión', icon: 'sensors' }, { id: 'temporada', label: 'Esta temporada', icon: 'eco' }, { id: 'proximamente', label: 'Próximamente', icon: 'event_upcoming' }, { id: 'completados', label: 'Completados hace poco', icon: 'task_alt' }];
  const TIPOS = [{ id: 'anime', label: 'Anime', icon: 'movie' }, { id: 'personajes', label: 'Personajes', icon: 'person' }, { id: 'manga', label: 'Manga', icon: 'menu_book' }, { id: 'novelas', label: 'Novelas', icon: 'auto_stories' }, { id: 'extension', label: 'Catálogo de extensión', icon: 'extension' }];
  const ETIQUETAS = ['Protagonista masculino', 'Reencarnación', 'Magia', 'Videojuegos', 'Harem', 'Espadas', 'Mundo alternativo', 'Comedia romántica', 'Basado en novela ligera', 'Basado en manga', 'Primer amor', 'Dioses', 'Demonios', 'Gremio de aventureros', 'Antihéroe', 'Escuela de magia'];
  const EXTS = [{ id: 'toko', label: 'Toko' }, { id: 'torrentio', label: 'Stremio · Torrentio' }, { id: 'vidnest', label: 'Vidnest', none: true }];
  const RECOMS = [
    { id: 'goblin', pct: 72, why: ['Parecido a Tempal', 'Te gusta Aventura', 'De 2026, como lo que ves'] },
    { id: 'kanojo', pct: 70, why: ['Parecido a Chitose-kun 2', 'Te gusta Romance'] },
    { id: 'mahoyo', pct: 68, why: ['Muy bien valorado, como tus favoritos', 'Te gusta Fantasía'] },
    { id: 'mouse', pct: 64, why: ['Te gusta Comedia', 'Parecido a Seitokai ni mo Ana wa Aru!'] },
    { id: 'edgerunners', pct: 61, why: ['Del mismo estudio que algo que completaste', 'Te gusta Acción'] },
    { id: 'mezametara', pct: 54, why: ['Te gusta Isekai', 'De 2026, como lo que ves'] },
  ];

  const seasonOf = (t) => (t.aired === 0 ? 'invierno' : t.format === 'Película' ? 'primavera' : 'otono');
  const stateOf = (t) => (t.aired === 0 ? 'proximo' : t.aired >= t.eps ? 'finalizado' : 'emision');
  const exDefaults = { q: '', tipo: 'anime', feed: 'tendencia', ventana: 'semana', formato: [], estado: null, genero: [], generoQ: '', anio: [1960, 2027], temporada: null, orden: 'tendencia', milista: [], adulto: false, eps: [0, 200], score: [0, 100], tags: [], tagQ: '', spoiler: false, pop: null, vista: 'rejilla', recientes: ['tensei', 'koori no jouheki', 'ao no hako'], ext: 'toko', hidden: [], img: null, imgSim: 85, charOpen: null, charFav: false, atlasHover: null };

  function exResults(st) {
    let list = D.titles.slice();
    const q = fold(st.q.trim());
    if (q) list = list.filter((t) => fold(t.title + ' ' + (t.short || '') + ' ' + (t.romaji || '')).includes(q));
    if (st.formato.length) list = list.filter((t) => on(st.formato, t.format));
    if (st.estado) list = list.filter((t) => stateOf(t) === st.estado);
    if (st.genero.length) list = list.filter((t) => st.genero.every((g) => on(t.genres, g)));
    list = list.filter((t) => t.year >= st.anio[0] && t.year <= st.anio[1]);
    if (st.temporada) list = list.filter((t) => seasonOf(t) === st.temporada);
    if (st.milista.length) list = list.filter((t) => (on(st.milista, 'fuera') && !t.status) || on(st.milista, t.status));
    list = list.filter((t) => t.eps >= st.eps[0] && t.eps <= st.eps[1]);
    list = list.filter((t) => t.score == null ? st.score[0] === 0 : t.score * 10 >= st.score[0] && t.score * 10 <= st.score[1]);
    const by = { puntuacion: (a, b) => (b.score || 0) - (a.score || 0), titulo: (a, b) => a.title.localeCompare(b.title), episodios: (a, b) => b.eps - a.eps, popularidad: (a, b) => (b.score || 0) * b.aired - (a.score || 0) * a.aired }[st.orden];
    if (by) list.sort(by);
    return list;
  }
  function activeCount(st) {
    return [st.formato.length, st.estado, st.genero.length, st.anio[0] > 1960 || st.anio[1] < 2027, st.temporada, st.milista.length, st.adulto, st.eps[0] > 0 || st.eps[1] < 200, st.score[0] > 0 || st.score[1] < 100, st.tags.length].filter(Boolean).length;
  }
  const lbl = (arr, ids) => [].concat(ids).map((x) => (arr.find((o) => o.id === x) || { label: x }).label);
  function facetValue(st, key) {
    switch (key) {
      case 'formato': return st.formato.length ? lbl(FORMATS, st.formato).join(', ') : '';
      case 'estado': return st.estado ? lbl(ESTADOS, st.estado)[0] : '';
      case 'genero': return st.genero.length ? (st.genero.length > 2 ? st.genero.slice(0, 2).join(', ') + ' +' + (st.genero.length - 2) : st.genero.join(', ')) : '';
      case 'anio': return st.anio[0] > 1960 || st.anio[1] < 2027 ? anyRange(1960, 2027)(st.anio[0], st.anio[1]) : '';
      case 'temporada': return st.temporada ? lbl(TEMPORADAS, st.temporada)[0] : '';
      case 'orden': return lbl(ORDENES, st.orden)[0];
      case 'milista': return st.milista.length ? lbl(MILISTA, st.milista).join(', ') : '';
      case 'mas': { const n = [st.eps[0] > 0 || st.eps[1] < 200, st.score[0] > 0 || st.score[1] < 100, st.tags.length].filter(Boolean).length; return n ? n + ' activos' : ''; }
      default: return '';
    }
  }
  const FACETS = [['formato', 'Formato'], ['estado', 'Estado'], ['genero', 'Género'], ['anio', 'Año'], ['temporada', 'Temporada'], ['milista', 'Mi lista'], ['mas', 'Más filtros']];

  function facetBody(ctx, st, key) {
    const n = exResults(st).length;
    const foot = `<div class="nj-b-pop-foot"><span class="nj-meta" role="status"><b data-count class="nj-b-tnum">${n}</b> resultados</span>${h.btn('Limpiar', { kind: 'text', attrs: { 'data-act': 'facetClear', 'data-arg': key } })}${h.btn('Listo', { kind: 'tonal', attrs: { 'data-act': 'facetClose' } })}</div>`;
    let body = '';
    if (key === 'formato') body = `<div data-bset="formato" data-bmode="multi">${h.chips('Formato', FORMATS, st.formato, { multi: true })}</div>`;
    else if (key === 'estado') body = `<div data-bset="estado" data-bmode="opt">${h.chips('Estado', ESTADOS, st.estado ? [st.estado] : [])}</div>`;
    else if (key === 'temporada') body = `<div data-bset="temporada" data-bmode="opt">${h.chips('Temporada', TEMPORADAS, st.temporada ? [st.temporada] : [])}</div>`;
    else if (key === 'milista') body = `<div data-bset="milista" data-bmode="multi">${h.chips('Mi lista', MILISTA, st.milista, { multi: true })}</div>`;
    else if (key === 'genero') {
      const gq = fold(st.generoQ); const gs = GENEROS.filter((g) => !gq || fold(g).includes(gq));
      body = `<label class="nj-search nj-b-minisearch">${ic('search', { size: 18 })}<input id="${pid(ctx, 'genq')}" data-input="generoQ" placeholder="Filtrar géneros" value="${esc(st.generoQ)}" aria-label="Filtrar géneros"></label>${st.genero.length ? `<p class="nj-meta">Se muestran títulos con todos los géneros elegidos.</p>` : ''}<div data-bset="genero" data-bmode="multi" class="nj-b-pop-scroll" data-keep="gen">${gs.length ? h.chips('Género', gs.map((g) => ({ id: g, label: g, count: D.titles.filter((t) => on(t.genres, g)).length || null })), st.genero, { multi: true }) : '<p class="nj-meta">Ningún género coincide.</p>'}</div>`;
    } else if (key === 'anio') {
      body = `${dualRange('anio', 'Años', 1960, 2027, 1, st.anio, anyRange(1960, 2027))}<div class="nj-cluster nj-b-quick">${[['Esta temporada', [2026, 2026]], ['Últimos 5 años', [2022, 2027]], ['2010 a 2019', [2010, 2019]], ['Antes de 2000', [1960, 1999]]].map(([l, r]) => `<button type="button" class="nj-chip" data-act="yearPreset" data-lo="${r[0]}" data-hi="${r[1]}" aria-pressed="${st.anio[0] === r[0] && st.anio[1] === r[1]}">${l}</button>`).join('')}</div>`;
    } else if (key === 'orden') body = `<div data-bset="orden">${h.chips('Orden', ORDENES, [st.orden])}</div>`;
    else if (key === 'mas') {
      const tq = fold(st.tagQ); const tags = ETIQUETAS.filter((t) => !tq || fold(t).includes(tq));
      body = `${dualRange('eps', 'Episodios', 0, 200, 1, st.eps, anyRange(0, 200, ' ep.'))}${dualRange('score', 'Puntuación media', 0, 100, 5, st.score, anyRange(0, 100))}<div class="nj-b-pop-group"><div class="nj-section-head"><span class="nj-h3">Etiquetas</span>${h.toggle(pid(ctx, 't-spoiler'), 'Mostrar spoilers', st.spoiler)}</div><label class="nj-search nj-b-minisearch">${ic('search', { size: 18 })}<input id="${pid(ctx, 'tagq')}" data-input="tagQ" placeholder="Filtrar entre 412 etiquetas" value="${esc(st.tagQ)}" aria-label="Filtrar etiquetas"></label><div data-bset="tags" data-bmode="multi" class="nj-b-pop-scroll" data-keep="tags">${h.chips('Etiquetas', tags.map((t) => ({ id: t, label: t })), st.tags, { multi: true })}</div></div>`;
    }
    return body + foot;
  }
  function facetRow(ctx, st) {
    return `<div class="nj-b-facets" role="toolbar" aria-label="Filtros">${FACETS.map(([k, l], i) => {
      const v = facetValue(st, k); const open = st.pop === k;
      return `<div class="nj-b-facet${v ? ' is-on' : ''}"><button type="button" class="nj-chip" aria-pressed="${!!v}" aria-expanded="${open}" aria-haspopup="dialog" data-act="facet" data-arg="${k}">${esc(l)}${v ? `<span class="nj-b-facet-v">: ${esc(v)}</span>` : ''}${ic(open ? 'arrow_drop_up' : 'arrow_drop_down', { size: 18 })}</button>${v ? `<button type="button" class="nj-b-facet-x" data-act="facetClear" data-arg="${k}" aria-label="Quitar filtro ${esc(l)}">${ic('close', { size: 16 })}</button>` : ''}${open && !isA(ctx) ? `<div class="nj-b-pop${i > 3 ? ' is-end' : ''}" role="dialog" aria-label="${esc(l)}">${facetBody(ctx, st, k)}</div>` : ''}</div>`;
    }).join('')}<span class="nj-b-facet-sep" aria-hidden="true"></span><button type="button" class="nj-chip${st.adulto ? ' is-warn' : ''}" aria-pressed="${st.adulto}" data-act="adult">${ic(st.adulto ? 'check' : 'explicit', { size: 18 })}+18</button><div class="nj-b-facet"><button type="button" class="nj-chip" aria-expanded="${st.pop === 'orden'}" data-act="facet" data-arg="orden">${ic('sort', { size: 18 })}${esc(facetValue(st, 'orden'))}</button>${st.pop === 'orden' && !isA(ctx) ? `<div class="nj-b-pop is-end" role="dialog" aria-label="Orden">${facetBody(ctx, st, 'orden')}</div>` : ''}</div></div>`;
  }
  function searchBar(ctx, st, o = {}) {
    const ph = { anime: 'Buscar anime', personajes: 'Buscar personajes', manga: 'Buscar manga', novelas: 'Buscar novelas', extension: 'Buscar en el catálogo de ' + lbl(EXTS, st.ext)[0] }[st.tipo];
    const n = activeCount(st);
    return `<div class="nj-search nj-b-searchbar${o.big ? ' is-big' : ''}">${ic('search')}<input id="${pid(ctx, 'q')}" data-input="q" type="search" placeholder="${esc(ph)}" value="${esc(st.q)}" aria-label="${esc(ph)}" autocomplete="off">${st.q ? h.iconBtn('close', 'Borrar búsqueda', { attrs: { 'data-act': 'clearQ' } }) : ''}${h.iconBtn('image_search', 'Buscar por imagen', { attrs: { 'data-act': 'imgOpen' } })}${isA(ctx) && st.tipo === 'anime' ? `<span class="nj-b-badgewrap">${h.iconBtn('tune', 'Filtros' + (n ? ` (${n} activos)` : ''), { attrs: { 'data-act': 'facet', 'data-arg': 'all' } })}${n ? `<span class="nj-b-dotcount">${n}</span>` : ''}</span>` : ''}</div>`;
  }
  function typeSwitch(ctx, st) {
    if (isA(ctx)) return `<div class="nj-tabs nj-b-typetabs" role="tablist" aria-label="Qué buscar" data-local data-keep="tipos">${TIPOS.map((t) => `<button type="button" role="tab" class="nj-tab" aria-selected="${st.tipo === t.id}" data-act="tipo" data-arg="${t.id}">${t.id === 'extension' ? 'Extensiones' : esc(t.label)}</button>`).join('')}</div>`;
    return `<div data-bset="tipo" class="nj-b-typeseg">${h.seg('Qué buscar', TIPOS, st.tipo)}</div>`;
  }
  function feedChips(st, o = {}) {
    return `<div class="nj-cluster nj-b-feeds" role="group" aria-label="Explorar rápido">${FEEDS.map((f) => `<button type="button" class="nj-chip${o.assist ? ' is-assist' : ''}" data-act="feed" data-arg="${f.id}" aria-pressed="${st.feed === f.id && !st.q}">${ic(f.icon, { size: 18 })}${esc(f.label)}</button>`).join('')}</div>`;
  }
  function recentRow(st) {
    if (!st.recientes.length) return '';
    return `<div class="nj-b-recents"><span class="nj-meta">Recientes</span><div class="nj-cluster">${st.recientes.map((r, i) => `<span class="nj-b-recent"><button type="button" class="nj-chip" data-act="recent" data-arg="${esc(r)}">${ic('history', { size: 18 })}${esc(r)}</button><button type="button" class="nj-b-facet-x" data-act="recentDel" data-arg="${i}" aria-label="Quitar ${esc(r)} de recientes">${ic('close', { size: 16 })}</button></span>`).join('')}${h.btn('Borrar todo', { kind: 'text', attrs: { 'data-act': 'recentAll' } })}</div></div>`;
  }
  function emptyState(icon, title, text, actions) {
    return `<div class="nj-b-empty" data-region="empty">${ic(icon, { size: 40 })}<h3 class="nj-h3">${esc(title)}</h3>${text ? `<p class="nj-meta">${esc(text)}</p>` : ''}${actions ? `<div class="nj-cluster nj-b-empty-acts">${actions}</div>` : ''}</div>`;
  }

  function exResultsBlock(ctx, st) {
    const A = isA(ctx);
    if (st.tipo === 'personajes') {
      const chars = [{ id: 'lily', name: 'Lily Flambert', from: 'laidoff' }, { id: 'tempal-p', name: 'Protagonista de Tempal', from: 'tempal' }, { id: 'aono-p', name: 'Protagonista de Ao no Hako', from: 'aonohako' }].filter((c) => !st.q || fold(c.name).includes(fold(st.q)));
      if (!chars.length) return emptyState('person_search', `Ningún personaje se llama «${st.q}»`, 'Prueba con el nombre de pila o en romaji.', h.btn('Borrar búsqueda', { kind: 'tonal', attrs: { 'data-act': 'clearQ' } }));
      return `<p class="nj-meta nj-b-count"><b class="nj-b-tnum">${chars.length}</b> personajes · datos de ejemplo</p><div class="nj-list nj-b-charlist">${chars.map((c) => `<button type="button" class="nj-list-row" data-act="charOpen" data-arg="${c.id}"><span class="nj-b-avatar" aria-hidden="true">${esc(c.name[0])}</span><span class="nj-grow"><span class="nj-b-rowtitle">${esc(c.name)}</span><span class="nj-meta">Aparece en ${esc(D.byId[c.from].short)}</span></span>${ic('chevron_right')}</button>`).join('')}</div>`;
    }
    if (st.tipo === 'novelas') return emptyState('auto_stories', 'No tienes extensiones de novelas', 'Las novelas vienen de extensiones .kai con catálogo de novelas. Instala una y vuelve a buscar.', h.btn('Abrir Fuentes', { kind: 'filled', icon: 'extension', attrs: { 'data-go': 'fuentes' } }) + h.btn('Buscar manga', { kind: 'outline', attrs: { 'data-act': 'tipo', 'data-arg': 'manga' } }));
    if (st.tipo === 'manga') {
      const ms = D.manga.filter((m) => !st.q || fold(m.title).includes(fold(st.q))).map((m) => ({ id: m.id, title: m.title, poster: m.poster, format: m.kind === 'manga' ? 'Manga' : m.kind === 'manhwa' ? 'Manhwa' : 'Manhua', year: m.chapter }));
      if (!ms.length) return emptyState('search_off', `Sin manga para «${st.q}»`, '', h.btn('Borrar búsqueda', { kind: 'tonal', attrs: { 'data-act': 'clearQ' } }));
      return `<p class="nj-meta nj-b-count"><b class="nj-b-tnum">${ms.length}</b> resultados en MangaDex y tus extensiones</p><div class="nj-grid nj-b-results" style="--nj-cell:${A ? 104 : 132}px" data-region="grid">${ms.map((m) => h.card(m, { size: 's', context: 'grid' })).join('')}</div>`;
    }
    const list = exResults(st).filter((t) => !on(st.hidden, t.id));
    const n = activeCount(st);
    if (!list.length) {
      return emptyState('filter_alt_off', st.q ? `Nada coincide con «${st.q}» y tus filtros` : 'Ningún título cumple estos filtros', n ? `Tienes ${n} filtros activos. El año es el que más recorta.` : 'Revisa la ortografía o busca por el título en romaji.', (n ? h.btn(`Quitar los ${n} filtros`, { kind: 'filled', icon: 'filter_alt_off', attrs: { 'data-act': 'resetAll' } }) : '') + h.btn('Buscar en tus extensiones', { kind: 'outline', icon: 'extension', attrs: { 'data-act': 'tipo', 'data-arg': 'extension' } }));
    }
    const ext = st.tipo === 'extension';
    const sig = st.tipo + ':' + list.map((t) => t.id).join(','); const anim = sig !== st._sig ? ' nj-b-anim-in' : ''; st._sig = sig;
    const head = `<div class="nj-b-reshead"><p class="nj-meta nj-b-count" role="status"><b data-count class="nj-b-tnum">${list.length}</b> ${ext ? 'resultados de ' + esc(lbl(EXTS, st.ext)[0]) : 'resultados'}${st.adulto ? ' · incluye +18' : ''}</p>${A ? '' : `<div class="nj-b-viewtoggle" role="group" aria-label="Vista">${h.iconBtn('grid_view', 'Rejilla', { attrs: { 'data-act': 'vista', 'data-arg': 'rejilla', 'aria-pressed': st.vista === 'rejilla' } })}${h.iconBtn('view_list', 'Lista', { attrs: { 'data-act': 'vista', 'data-arg': 'lista', 'aria-pressed': st.vista === 'lista' } })}</div>`}</div>`;
    if (st.vista === 'lista' && !A) {
      return head + `<div class="nj-list nj-b-reslist${anim}">${list.map((t) => `<div class="nj-b-resrow">${h.card(t, { context: 'compact', show: { status: true } })}<span class="nj-meta">${esc(t.format)} · ${t.year} · ${t.eps} ep.</span><span class="nj-b-score nj-b-tnum">${t.score ? ic('star', { size: 16, fill: true }) + t.score.toFixed(1) : 'Sin nota'}</span>${h.statusChip(t.status) || `<button type="button" class="nj-btn is-outline nj-b-addbtn" data-act="addList" data-arg="${t.id}">${ic('playlist_add')}Agregar</button>`}</div>`).join('')}</div>`;
    }
    return head + `${ext ? `<p class="nj-meta nj-b-exthint">${ic('info', { size: 18 })}Estos títulos vienen del catálogo de la extensión. Tócalos para abrirlos ahí o búscalos en tus metadatos desde su menú.</p>` : ''}<div class="nj-grid nj-b-results${anim}" style="--nj-cell:${A ? 104 : 136}px" data-region="grid">${list.map((t) => h.card(t, { size: 's', context: 'grid', show: { status: true, score: true } })).join('')}</div>`;
  }

  function imgSheet(ctx, st) {
    const A = isA(ctx);
    const matches = [{ id: 'laidoff', ep: 4, at: '12:31', sim: 97, img: 'img/frame-laidoff.webp' }, { id: 'tsuihou', ep: 2, at: '03:08', sim: 88, img: 'img/hero-sbr.webp' }, { id: 'kikansha', ep: 7, at: '18:40', sim: 81, img: 'img/hero-onepiece.webp' }, { id: 'revo', ep: 1, at: '00:52', sim: 71, img: 'img/p-revo-barai.webp' }];
    const shown = matches.filter((m) => m.sim >= st.imgSim); const below = matches.length - shown.length;
    let body;
    if (!st.img) {
      body = `<div class="nj-b-drop" data-act="imgPick" role="button" tabindex="0" aria-label="Elegir una imagen o soltarla aquí">${ic('add_photo_alternate', { size: 40 })}<b>${A ? 'Elige una captura del episodio' : 'Suelta una captura aquí'}</b><span class="nj-meta">${A ? 'También puedes compartir una imagen a Nijiyome desde la galería.' : 'O elige un archivo. Funciona mejor con un cuadro completo, sin bordes.'}</span>${h.btn('Elegir imagen', { kind: 'filled', icon: 'image', attrs: { 'data-act': 'imgPick' } })}</div>${A ? '' : `<div class="nj-b-urlrow"><label class="nj-field">${ic('link', { size: 18 })}<input id="${pid(ctx, 'imgurl')}" placeholder="https://… enlace a una imagen" aria-label="Enlace a una imagen"></label>${h.btn('Buscar', { kind: 'tonal', attrs: { 'data-act': 'imgPick' } })}</div>`}<p class="nj-meta">La búsqueda la hace trace.moe. La imagen no se guarda.</p>`;
    } else {
      body = `<div class="nj-b-imgsrc"><img src="img/frame-laidoff.webp" alt="Imagen buscada" width="160" height="90"><div class="nj-stack" style="gap:4px"><b>captura-0412.png</b><span class="nj-meta">${matches.length} coincidencias</span>${h.btn('Elegir otra', { kind: 'text', attrs: { 'data-act': 'imgReset' } })}</div></div>${h.slider(pid(ctx, 's-imgSim'), 'Similitud mínima', { min: 50, max: 100, step: 1, value: st.imgSim, unit: ' %' })}<div class="nj-list nj-b-matches" data-cards-local>${shown.map((m) => { const t = D.byId[m.id]; const lv = m.sim >= 95 ? 'ok' : m.sim >= 85 ? 'warn' : 'err'; return `<button type="button" class="nj-b-match" data-act="openFicha"><img src="${m.img}" alt="" width="128" height="72"><span class="nj-grow"><span class="nj-b-rowtitle">${esc(t.short)}</span><span class="nj-meta">Episodio ${m.ep} a las ${m.at}</span></span><span class="nj-b-sim is-${lv} nj-b-tnum">${m.sim} %</span></button>`; }).join('')}</div>${below ? `<p class="nj-meta">${below} ${below === 1 ? 'coincidencia queda' : 'coincidencias quedan'} por debajo del umbral.</p>` : ''}`;
    }
    return sheet(ctx, 'Buscar por imagen', body, { close: 'imgClose' });
  }
  function charSheet(ctx, st) {
    const c = { lily: ['Lily Flambert', 'laidoff'], 'tempal-p': ['Protagonista de Tempal', 'tempal'], 'aono-p': ['Protagonista de Ao no Hako', 'aonohako'] }[st.charOpen];
    if (!c) return '';
    const t = D.byId[c[1]];
    const body = `<div class="nj-b-charhead"><span class="nj-b-avatar is-xl" aria-hidden="true">${esc(c[0][0])}</span><div class="nj-stack" style="gap:6px"><h3 class="nj-h2">${esc(c[0])}</h3><span class="nj-meta">Datos de ejemplo de AniList</span><button type="button" class="nj-btn ${st.charFav ? 'is-tonal' : 'is-outline'}" data-act="charFav" aria-pressed="${st.charFav}">${ic('favorite', { fill: st.charFav })}${st.charFav ? 'En favoritos' : 'Agregar a favoritos'}</button></div></div><dl class="nj-b-kv"><dt>Edad</dt><dd>Sin dato</dd><dt>Cumpleaños</dt><dd>Sin dato</dd><dt>Grupo sanguíneo</dt><dd>Sin dato</dd><dt>Género</dt><dd>Sin dato</dd></dl><p class="nj-b-clamp">La descripción de AniList aparece aquí, recortada a cuatro líneas.</p>${h.btn('Leer más', { kind: 'text' })}<h3 class="nj-h3">Aparece en</h3><div class="nj-row">${h.card(t, { size: 's', context: 'row' })}</div>`;
    return sheet(ctx, 'Personaje', body, { close: 'charClose' });
  }

  function exAndroidFacetSheet(ctx, st) {
    const k = st.pop; if (!k) return '';
    if (k === 'all') {
      const body = FACETS.concat([['orden', 'Orden']]).map(([key, l]) => `<section class="nj-b-sheetsec"><h3 class="nj-h3">${esc(l)}</h3>${facetBody(ctx, st, key).replace(/<div class="nj-b-pop-foot">[\s\S]*$/, '')}</section>`).join('') + `<section class="nj-b-sheetsec">${h.toggle(pid(ctx, 't-adulto'), 'Incluir títulos +18', st.adulto, { desc: 'La ficha pide confirmación antes de mostrar un título adulto.' })}</section>`;
      return sheet(ctx, 'Filtros', body, { close: 'facetClose', foot: `<span class="nj-meta" role="status"><b data-count class="nj-b-tnum">${exResults(st).length}</b> resultados</span>${h.btn('Quitar todo', { kind: 'text', attrs: { 'data-act': 'resetAll' } })}${h.btn('Ver resultados', { kind: 'filled', attrs: { 'data-act': 'facetClose' } })}` });
    }
    const name = (FACETS.concat([['orden', 'Orden']]).find((f) => f[0] === k) || [k, k])[1];
    return sheet(ctx, name, facetBody(ctx, st, k), { close: 'facetClose' });
  }

  function atlasSvg(ctx, st, w, hgt) {
    const clusters = [{ g: ['Fantasía', 'Isekai'], name: 'Fantasía e isekai', c: 'accent', x: 0.3, y: 0.38 }, { g: ['Romance', 'Comedia', 'Escolar'], name: 'Romance y comedia', c: 'warn', x: 0.72, y: 0.3 }, { g: ['Acción', 'Sobrenatural', 'Ciencia ficción', 'Mecha'], name: 'Acción', c: 'info', x: 0.56, y: 0.74 }, { g: ['Drama', 'Misterio', 'Deportes'], name: 'Drama y misterio', c: 'ok', x: 0.18, y: 0.78 }];
    let seed = 7; const rnd = () => { seed = (seed * 9301 + 49297) % 233280; return seed / 233280; };
    const dots = D.titles.map((t) => {
      const ci = clusters.findIndex((c) => c.g.includes(t.genres[0])); const c = clusters[ci < 0 ? 0 : ci];
      const a = rnd() * Math.PI * 2; const r = 0.05 + rnd() * 0.13;
      return { t, c, x: Math.min(0.95, Math.max(0.05, c.x + Math.cos(a) * r)) * w, y: Math.min(0.92, Math.max(0.08, c.y + Math.sin(a) * r * 1.1)) * hgt, seen: ['watching', 'completed', 'paused', 'dropped'].includes(t.status) };
    });
    return `<svg class="nj-b-atlas" viewBox="0 0 ${w} ${hgt}" role="img" aria-label="Atlas de tus gustos: cada punto es un título"><g class="nj-b-atlas-labels">${clusters.map((c) => `<text x="${c.x * w}" y="${c.y * hgt - 0.17 * hgt}" text-anchor="middle">${esc(c.name)}</text>`).join('')}</g>${dots.map((d, i) => `<a href="#pantallas" class="nj-b-dot is-${d.c.c}${d.seen ? ' is-seen' : ''}" data-act="openFicha" style="--i:${i}" aria-label="${esc(d.t.short)}${d.seen ? ', ya visto' : ', recomendado'}"><title>${esc(d.t.short)}${d.seen ? ' · visto' : ' · recomendado'}</title><circle cx="${d.x.toFixed(1)}" cy="${d.y.toFixed(1)}" r="${d.seen ? 7 : 8}"></circle>${st.atlasLabels ? `<text x="${(d.x + 11).toFixed(1)}" y="${(d.y + 4).toFixed(1)}">${esc(d.t.short.slice(0, 16))}</text>` : ''}</a>`).join('')}</svg>`;
  }
  function recomList(ctx, st, max) {
    const items = RECOMS.filter((r) => !on(st.hidden, r.id)).slice(0, max || 99);
    if (!items.length) return emptyState('recommend', 'No quedan recomendaciones', 'Ocultaste todas. Mira unos episodios más y vuelven a calcularse en este equipo.', h.btn('Mostrar las ocultas', { kind: 'tonal', attrs: { 'data-act': 'unhideAll' } }));
    return `<div class="nj-b-recs" data-cards-local>${items.map((r) => { const t = D.byId[r.id]; return `<article class="nj-b-rec"><button type="button" class="nj-b-rec-art" data-act="openFicha" aria-label="Abrir ${esc(t.short)}">${h.poster(t, { alt: '' })}</button><div class="nj-b-rec-body"><div class="nj-b-rec-top"><h3 class="nj-h3"><button type="button" class="nj-b-linkbtn" data-act="openFicha">${esc(t.short)}</button></h3><span class="nj-b-match-pill nj-b-tnum">${r.pct} % de afinidad</span></div><div class="nj-cluster nj-b-why">${r.why.map((w) => `<span class="nj-badge">${esc(w)}</span>`).join('')}</div><div class="nj-cluster nj-b-rec-acts">${h.btn('Agregar a Pendiente', { kind: 'tonal', icon: 'playlist_add', attrs: { 'data-act': 'addList', 'data-arg': r.id } })}${h.btn('No me interesa', { kind: 'text', attrs: { 'data-act': 'hideRec', 'data-arg': r.id } })}</div></div></article>`; }).join('')}</div>`;
  }
  const TASTE = ['Fantasía', 'Comedia', 'Acción', 'Romance', 'Aventura', 'Isekai'];

  function renderExplorar(ctx) {
    const st = defaults(ctx.state, exDefaults); const A = isA(ctx);
    const lienzo = ctx.structure === 'lienzo';
    let html;
    if (lienzo) {
      const trend = D.titles.slice().sort((a, b) => (b.score || 0) - (a.score || 0)).slice(0, A ? 8 : 9);
      const trendRow = `<section class="nj-section nj-b-sec" data-region="row" data-row-title="En tendencia"><div class="nj-section-head"><h2 class="nj-h2">En tendencia</h2><div data-bset="ventana" class="nj-b-segsm">${h.seg('Ventana de tiempo', [{ id: 'hoy', label: 'Hoy' }, { id: 'semana', label: 'Semana' }, { id: 'mes', label: 'Mes' }, { id: 'siempre', label: 'Siempre' }], st.ventana)}</div></div><div class="nj-row" data-keep="trend">${trend.map((t, i) => `<div class="nj-b-rank"><span class="nj-b-rank-n nj-b-tnum" aria-hidden="true">${i + 1}</span>${h.card(t, { size: A ? 's' : 'm', context: 'row', show: { status: true } })}</div>`).join('')}</div></section>`;
      const atlas = `<section class="nj-b-panel nj-b-atlaswrap"><div class="nj-section-head"><h2 class="nj-h2">Atlas de tus gustos</h2>${h.btn(st.atlasLabels ? 'Ocultar nombres' : 'Mostrar nombres', { kind: 'text', attrs: { 'data-act': 'atlasLabels' } })}</div><p class="nj-meta">Cada punto es un título y los cercanos se parecen. Los puntos con aro son recomendaciones; tócalos para abrirlos.</p>${atlasSvg(ctx, st, A ? 360 : 520, A ? 260 : 360)}<div class="nj-cluster nj-b-legend"><span><i class="nj-b-lg is-seen"></i>Visto</span><span><i class="nj-b-lg"></i>Recomendado</span></div></section>`;
      const forYou = `<section class="nj-b-foryou"><div class="nj-section-head"><h2 class="nj-h2">Para ti</h2><span class="nj-meta">Calculado en este equipo</span></div><div class="nj-b-taste"><span class="nj-meta">Tus gustos, a partir de 25 títulos</span><div class="nj-cluster">${TASTE.map((g) => `<button type="button" class="nj-chip" data-act="genreGo" data-arg="${g}">${esc(g)}</button>`).join('')}</div></div>${recomList(ctx, st, A ? 4 : 6)}</section>`;
      const top = `<div class="nj-b-lz-top">${searchBar(ctx, st, { big: !A })}${feedChips(st, { assist: true })}</div>`;
      if (A) html = `${appbar('Explorar', h.iconBtn('account_circle', 'Tu perfil', { attrs: { 'data-go': 'perfil' } }))}<div class="nj-b-pad nj-stack">${top}</div>${trendRow}<div class="nj-b-pad nj-stack nj-b-gap-l">${forYou}${atlas}</div>`;
      else html = `<div class="nj-b-page"><header class="nj-b-head"><h1 class="nj-b-title">Explorar</h1></header>${top}${trendRow.replace('nj-section nj-b-sec', 'nj-b-sec')}<div class="nj-b-lz-grid">${forYou}${atlas}</div></div>`;
    } else {
      const anime = st.tipo === 'anime' || st.tipo === 'extension';
      const extPick = st.tipo === 'extension' ? `<div class="nj-cluster nj-b-extpick" role="group" aria-label="Extensión">${EXTS.map((x) => `<button type="button" class="nj-chip" data-act="${x.none ? 'extNone' : 'ext'}" data-arg="${x.id}" aria-pressed="${st.ext === x.id}"${x.none ? ' aria-disabled="true"' : ''}>${ic('extension', { size: 18 })}${esc(x.label)}${x.none ? ' · sin catálogo' : ''}</button>`).join('')}</div>` : '';
      const idle = !st.q && anime ? `<div class="nj-b-idle">${recentRow(st)}<div class="nj-b-feedline"><span class="nj-meta">Explorar</span>${feedChips(st)}</div>${st.feed === 'tendencia' ? `<div class="nj-b-trendwin"><span class="nj-meta">Tendencia de</span><div data-bset="ventana" class="nj-b-segsm">${h.seg('Ventana de tiempo', [{ id: 'hoy', label: 'Hoy' }, { id: 'semana', label: 'Esta semana' }, { id: 'mes', label: 'Este mes' }, { id: 'siempre', label: 'Siempre' }], st.ventana)}</div></div>` : ''}</div>` : '';
      if (A) {
        const chipRow = anime ? `<div class="nj-b-chiprow" data-keep="achips">${FACETS.concat([['orden', 'Orden']]).map(([k, l]) => { const v = facetValue(st, k); return `<button type="button" class="nj-chip" aria-pressed="${!!v && k !== 'orden'}" data-act="facet" data-arg="${k}">${esc(k === 'orden' ? v : v ? v : l)}${ic('arrow_drop_down', { size: 18 })}</button>`; }).join('')}</div>` : '';
        html = `<div class="nj-b-pad nj-b-asearch">${searchBar(ctx, st)}</div>${typeSwitch(ctx, st)}${extPick ? `<div class="nj-b-pad">${extPick}</div>` : ''}${chipRow}<div class="nj-b-pad nj-stack">${idle}${exResultsBlock(ctx, st)}</div>${exAndroidFacetSheet(ctx, st)}`;
      } else {
        html = `<div class="nj-b-page"><header class="nj-b-head"><h1 class="nj-b-title">Explorar</h1><span class="nj-meta nj-b-head-hint">${ic('keyboard', { size: 18 })}Ctrl K también busca y abre resultados</span></header><div class="nj-b-searchline">${searchBar(ctx, st)}${typeSwitch(ctx, st)}</div>${extPick}${anime ? facetRow(ctx, st) : ''}${idle}<div class="nj-b-resarea">${exResultsBlock(ctx, st)}</div></div>`;
      }
    }
    return `<div class="nj-b-explorar">${html}${st.imgOpen ? imgSheet(ctx, st) : ''}${st.charOpen ? charSheet(ctx, st) : ''}${snackHtml(st)}</div>`;
  }

  function mountExplorar(el, ctx) {
    const st = ctx.state;
    const redraw = wire(el, ctx, Object.assign(snackHandlers(el, ctx), {
      $esc: (rd) => { if (st.pop) { st.pop = null; rd(); return true; } if (st.imgOpen) { st.imgOpen = false; rd(); return true; } if (st.charOpen) { st.charOpen = null; rd(); return true; } return false; },
      noop: () => {},
      facet: (a, e, rd) => { const k = a.dataset.arg; st.pop = st.pop === k ? null : k; rd(); },
      facetClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.pop = null; rd(); },
      facetClear: (a, e, rd) => { const k = a.dataset.arg; const d = exDefaults; if (k === 'mas') { st.eps = clone(d.eps); st.score = clone(d.score); st.tags = []; } else if (k === 'orden') st.orden = 'tendencia'; else st[k] = clone(d[k]); rd(); },
      resetAll: (a, e, rd) => { ['formato', 'estado', 'genero', 'anio', 'temporada', 'milista', 'eps', 'score', 'tags', 'adulto'].forEach((k) => { st[k] = clone(exDefaults[k]); }); st.pop = null; rd(); },
      yearPreset: (a, e, rd) => { st.anio = [+a.dataset.lo, +a.dataset.hi]; rd(); },
      adult: (a, e, rd) => { st.adulto = !st.adulto; rd(); },
      clearQ: (a, e, rd) => { st.q = ''; rd(); },
      tipo: (a, e, rd) => { st.tipo = a.dataset.arg; st.pop = null; rd(); },
      ext: (a, e, rd) => { st.ext = a.dataset.arg; rd(); },
      extNone: () => snack(el, ctx, 'Vidnest no tiene un catálogo propio para buscar.'),
      feed: (a, e, rd) => { st.feed = a.dataset.arg; st.q = ''; st.orden = { tendencia: 'tendencia', populares: 'popularidad', valorados: 'puntuacion' }[st.feed] || 'tendencia'; st.estado = { emision: 'emision', proximamente: 'proximo', completados: 'finalizado' }[st.feed] || null; if (ctx.structure === 'lienzo') NJ.go('explorar', { structure: 'facetas' }); else rd(); },
      genreGo: (a) => { st.genero = [a.dataset.arg]; st.q = ''; NJ.go('explorar', { structure: 'facetas' }); },
      recent: (a, e, rd) => { st.q = a.dataset.arg; rd(); },
      recentDel: (a, e, rd) => { const i = +a.dataset.arg; const r = st.recientes[i]; st.recientes.splice(i, 1); snack(el, ctx, `Se quitó «${r}» de recientes.`, () => { st.recientes.splice(i, 0, r); }); },
      recentAll: (a, e, rd) => { const old = st.recientes.slice(); st.recientes = []; snack(el, ctx, 'Se borraron las búsquedas recientes.', () => { st.recientes = old; }); },
      vista: (a, e, rd) => { st.vista = a.dataset.arg; rd(); },
      addList: (a) => { const t = D.byId[a.dataset.arg]; snack(el, ctx, `${t.short} se agregó a Pendiente.`, () => {}); },
      hideRec: (a) => { const id = a.dataset.arg; st.hidden = st.hidden.concat([id]); snack(el, ctx, 'Se ocultó de tus recomendaciones.', () => { st.hidden = st.hidden.filter((x) => x !== id); }); },
      unhideAll: (a, e, rd) => { st.hidden = []; rd(); },
      atlasLabels: (a, e, rd) => { st.atlasLabels = !st.atlasLabels; rd(); },
      openFicha: () => { if (NJ.screens.ficha) NJ.go('ficha'); },
      imgOpen: (a, e, rd) => { st.imgOpen = true; st.img = st.img || null; rd(); },
      imgClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.imgOpen = false; rd(); },
      imgPick: (a, e, rd) => { st.img = 'captura'; rd(); },
      imgReset: (a, e, rd) => { st.img = null; rd(); },
      charOpen: (a, e, rd) => { st.charOpen = a.dataset.arg; rd(); },
      charClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.charOpen = null; rd(); },
      charFav: (a, e, rd) => { st.charFav = !st.charFav; rd(); },
    }));
    let qt;
    el.addEventListener('input', (e) => {
      const k = e.target.dataset && e.target.dataset.input; if (k) { st[k] = e.target.value; clearTimeout(qt); qt = setTimeout(() => { if (el.isConnected) redraw(); }, k === 'q' ? 220 : 60); return; }
      if (e.target.id === pid(ctx, 's-imgSim')) { st.imgSim = +e.target.value; }
    });
    el.addEventListener('change', (e) => {
      if (e.target.id === pid(ctx, 's-imgSim')) redraw();
      if (e.target.id === pid(ctx, 't-spoiler')) { st.spoiler = e.target.checked; }
      if (e.target.id === pid(ctx, 't-adulto')) { st.adulto = e.target.checked; redraw(); }
      if (e.target.closest && e.target.closest('[data-dual]')) redraw();
    });
    el.addEventListener('keydown', (e) => { if (e.key === 'Enter' && e.target.dataset && e.target.dataset.input === 'q' && st.q.trim()) { st.recientes = [st.q.trim()].concat(st.recientes.filter((r) => r !== st.q.trim())).slice(0, 6); redraw(); } });
    el.addEventListener('click', (e) => {
      if (!st.pop || isA(ctx) || !el.isConnected) return;
      if (e.target.closest('.nj-b-pop, [data-act="facet"], [data-act="facetClear"]')) return;
      st.pop = null; redraw();
    });
    dualWire(el, ctx, st, (key, lo, hi, box) => {
      const out = box.querySelector('output'); const fm = key === 'anio' ? anyRange(1960, 2027) : key === 'eps' ? anyRange(0, 200, ' ep.') : anyRange(0, 100);
      if (out) out.textContent = fm(lo, hi);
      const n = exResults(st).length; el.querySelectorAll('[data-count]').forEach((c) => { c.textContent = n; });
    });
  }

  NJ.registerScreen({
    id: 'explorar', name: 'Explorar', group: 'Descubrir', order: 1, platforms: ['desktop', 'android'],
    summary: 'Buscar, Para ti, Atlas, Top, búsqueda por imagen, personajes y catálogos de extensiones pasan a ser una sola pantalla; el tipo de búsqueda se separa de los filtros y cada filtro es un chip que dice su valor y abre su propio panel.',
    parity: 'SRCH-01, SRCH-02, SRCH-03, SRCH-04, SRCH-05, SRCH-06, SRCH-07, SRCH-08, SRCH-09, IMG-01, CHAR-01, FYOU-01, FYOU-02, HOME-10, HOME-12, UI-01. Ctrl K (SHELL-08) sigue buscando el catálogo. Los ajustes de descubrir (SRCH-10) viven en Ajustes › Metadatos y descubrir.',
    structures: [
      { id: 'facetas', name: 'Facetas', desc: 'Búsqueda con chips de filtro que abren un panel, contador en vivo y tipo de búsqueda aparte.' },
      { id: 'lienzo', name: 'Lienzo', desc: 'Descubrir sin escribir: tendencia, Para ti con afinidad y el mapa Atlas de tus gustos.' },
    ],
    render: renderExplorar, mount: mountExplorar,
  });

  NJ.B = { laterS, isA, pid, ic, keep, restore, wire, snack, snackHtml, snackHandlers, limitBanner, limitWire, sheet, appbar, stepper, dualRange, dualWire, anyRange, emptyState, defaults, on, toggleIn, fold, clone, tick, later };
})();

/* ======================================================================
   2. CALENDARIO (semana, tablero de salidas, agenda)
   ====================================================================== */
(function () {
  const NJ = window.NJ; if (!NJ || !NJ.B) return;
  const D = NJ.data, h = NJ.h, esc = h.esc, B = NJ.B, ic = B.ic;
  const WD = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
  const WDS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  const TODAY = 2; const NOW = '21:04';
  const BASE = Date.UTC(2026, 10, 23);
  const dayDate = (wk, di) => new Date(BASE + (wk * 7 + di) * 86400000);
  const fmt = (d, o) => d.toLocaleDateString('es', Object.assign({ timeZone: 'UTC' }, o));
  const TIMES = ['00:30', '09:30', '10:00', '11:00', '12:30', '17:30', '19:00', '22:00', '23:30', '01:00'];
  /* Horario semanal: lo de hoy viene de data.js; el resto se reparte de forma fija (ejemplo). */
  const WEEK = (function () {
    const today = D.airingToday.map((a) => ({ id: a.id, di: TODAY, time: a.time, ep: a.ep }));
    const used = new Set(today.map((x) => x.id)); const rest = [];
    D.titles.forEach((t, i) => {
      if (used.has(t.id) || t.format === 'Película' || (t.aired >= t.eps && t.aired > 0)) return;
      const di = [0, 1, 3, 4, 5, 6][(i * 5) % 6];
      rest.push({ id: t.id, di, time: TIMES[(i * 7) % TIMES.length], ep: t.aired === 0 ? 1 : t.aired + (di > TODAY ? 1 : 0), premiere: t.aired === 0 });
    });
    return today.concat(rest).sort((a, b) => a.di - b.di || a.time.localeCompare(b.time));
  })();
  const calDefaults = { wk: 0, day: TODAY, mine: false, follow: D.titles.filter((t) => t.status === 'watching').map((t) => t.id), remind: ['aonohako', 'tempal', 'laidoff', 'koori'], changed: false, boardDay: TODAY, showRem: false };

  function items(st, di) {
    return WEEK.filter((x) => (di == null || x.di === di) && (!st.mine || B.on(st.follow, x.id))).map((x) => Object.assign({}, x, { ep: x.ep + st.wk, t: D.byId[x.id] }));
  }
  function airState(st, x) {
    const rel = st.wk * 7 + x.di - TODAY;
    if (rel < 0) return 'emitido';
    if (rel > 0) return 'programado';
    if (x.id === 'psyren' && st.changed) return 'retrasado';
    if (x.time <= '20:59') return 'emitido';
    if (x.time <= NOW || x.id === 'aonohako') return 'emision';
    return 'programado';
  }
  const STATE_L = { emitido: 'Emitido', emision: 'En emisión', retrasado: 'Retrasado', programado: 'Programado' };
  const bell = (st, x) => { const f = B.on(st.follow, x.id); return h.iconBtn(f ? 'notifications_active' : 'notification_add', f ? `Dejar de seguir ${x.t.short}` : `Seguir ${x.t.short}`, { kind: f ? 'tonal' : '', fill: f, attrs: { 'data-act': 'follow', 'data-arg': x.id, 'aria-pressed': f } }); };
  const weekLabel = (st) => { const a = dayDate(st.wk, 0); const b = dayDate(st.wk, 6); return st.wk === 0 ? `Esta semana · ${fmt(a, { day: 'numeric' })} al ${fmt(b, { day: 'numeric', month: 'short' })}` : `Semana del ${fmt(a, { day: 'numeric', month: 'short' })} al ${fmt(b, { day: 'numeric', month: 'short' })}`; };

  function toolbar(ctx, st) {
    const A = B.isA(ctx);
    const views = h.seg('Vista', [{ id: 'salidas', label: 'Tablero', icon: 'departure_board' }, { id: 'semana', label: 'Semana', icon: 'view_week' }, { id: 'agenda', label: 'Agenda', icon: 'view_agenda' }], ctx.structure);
    const nav = `<div class="nj-b-periodnav">${h.iconBtn('chevron_left', 'Semana anterior', { attrs: { 'data-act': 'wk', 'data-d': -1 } })}<span class="nj-b-period" aria-live="polite">${esc(weekLabel(st))}</span>${h.iconBtn('chevron_right', 'Semana siguiente', { attrs: { 'data-act': 'wk', 'data-d': 1 } })}${st.wk !== 0 ? h.btn('Hoy', { kind: 'outline', attrs: { 'data-act': 'today' } }) : ''}</div>`;
    const mine = `<button type="button" class="nj-chip" data-act="mine" aria-pressed="${st.mine}">${ic(st.mine ? 'check' : 'filter_list', { size: 18 })}Solo lo que sigo</button>`;
    if (A) return `<div class="nj-b-pad nj-stack nj-b-caltools"><div data-local class="nj-b-segfull" data-viewseg>${views}</div><div class="nj-b-calrow2">${nav}</div><div class="nj-cluster">${mine}</div></div>`;
    return `<div class="nj-b-caltools"><div data-local data-viewseg>${views}</div>${nav}${mine}</div>`;
  }
  function evItem(ctx, st, x, o = {}) {
    const f = B.on(st.follow, x.id); const s = airState(st, x);
    return `<li class="nj-b-ev${f ? ' is-follow' : ''}${s === 'emision' ? ' is-now' : ''}" data-time="${x.time}"${s === 'emision' ? ' data-now="true"' : ''}><button type="button" class="nj-b-ev-main" data-act="ficha"><img src="${esc(x.t.poster)}" alt="" width="34" height="51" loading="lazy"><span class="nj-b-ev-text"><span class="nj-b-ev-time nj-b-tnum">${x.time}${s === 'emision' ? ' · ahora' : ''}</span><span class="nj-b-ev-title">${esc(x.t.short)}</span><span class="nj-meta">${x.premiere ? 'Estreno · ' : ''}Ep. ${x.ep}${s === 'retrasado' ? ' · retrasado' : ''}</span></span></button>${o.noBell ? '' : bell(st, x)}</li>`;
  }
  function weekView(ctx, st) {
    if (B.isA(ctx)) {
      const strip = `<div class="nj-b-daystrip" role="tablist" aria-label="Días de la semana">${WDS.map((d, i) => { const n = items(st, i).length; const dt = dayDate(st.wk, i); return `<button type="button" role="tab" class="nj-b-daycell${i === st.day ? ' is-sel' : ''}${st.wk === 0 && i === TODAY ? ' is-today' : ''}" aria-selected="${i === st.day}" data-act="day" data-arg="${i}"><span>${d}</span><b class="nj-b-tnum">${fmt(dt, { day: 'numeric' })}</b><span class="nj-b-daycount">${n ? n + ' ep.' : 'nada'}</span></button>`; }).join('')}</div>`;
      const its = items(st, st.day);
      return `${strip}<div class="nj-b-pad"><h2 class="nj-h3 nj-b-dayhead">${esc(WD[st.day])} ${fmt(dayDate(st.wk, st.day), { day: 'numeric', month: 'long' })}</h2>${its.length ? `<ul class="nj-b-evlist" data-region="airing">${its.map((x) => evItem(ctx, st, x)).join('')}</ul>` : dayEmpty(st)}</div>`;
    }
    return `<div class="nj-b-week" data-region="airing" role="grid" aria-label="${esc(weekLabel(st))}">${WD.map((d, i) => { const its = items(st, i); const today = st.wk === 0 && i === TODAY; return `<section class="nj-b-wcol${today ? ' is-today' : ''}${i === st.day ? ' is-sel' : ''}" role="gridcell" aria-label="${d}"><header class="nj-b-wcol-head"><span>${WDS[i]}</span><b class="nj-b-tnum">${fmt(dayDate(st.wk, i), { day: 'numeric' })}</b>${today ? '<span class="nj-b-todaypill">Hoy</span>' : ''}</header>${its.length ? `<ul class="nj-b-evlist">${its.map((x) => evItem(ctx, st, x)).join('')}</ul>` : `<p class="nj-meta nj-b-wcol-empty">${st.mine ? 'Nada de lo que sigues' : 'Sin episodios'}</p>`}</section>`; }).join('')}</div>`;
  }
  function dayEmpty(st) { return B.emptyState('event_busy', st.mine ? 'Nada de lo que sigues sale este día' : 'AniList no tiene episodios este día', st.mine ? 'Quita el filtro para ver todo lo que se emite.' : 'Prueba con otro día de la semana.', st.mine ? h.btn('Ver todo', { kind: 'tonal', attrs: { 'data-act': 'mine' } }) : ''); }

  function boardView(ctx, st) {
    const A = B.isA(ctx); const di = st.boardDay;
    const its = items(st, di); const dt = dayDate(st.wk, di); const isToday = st.wk === 0 && di === TODAY;
    const head = `<div class="nj-b-boardhead"><div class="nj-stack" style="gap:2px"><h2 class="nj-h2">${isToday ? 'Salidas de hoy' : 'Salidas del ' + esc(WD[di].toLowerCase())}</h2><span class="nj-meta">${esc(fmt(dt, { weekday: 'long', day: 'numeric', month: 'long' }))} · hora de tu equipo</span></div>${isToday ? `<span class="nj-b-clock nj-b-tnum" aria-label="Hora actual ${NOW}">${NOW}</span>` : ''}</div><div class="nj-b-chiprow is-plain" role="group" aria-label="Día">${WDS.map((d, i) => `<button type="button" class="nj-chip" data-act="boardDay" data-arg="${i}" aria-pressed="${i === di}">${d} ${fmt(dayDate(st.wk, i), { day: 'numeric' })}</button>`).join('')}</div>`;
    if (!its.length) return head + dayEmpty(st);
    const rows = its.map((x) => {
      const s = airState(st, x); const f = B.on(st.follow, x.id); const chg = x.id === 'psyren' && st.changed && isToday;
      const pill = `<span class="nj-b-air is-${s}${chg ? ' is-flip' : ''}">${s === 'emision' ? '<i aria-hidden="true"></i>' : ''}${STATE_L[s]}${s === 'retrasado' ? ' · 23:45' : ''}</span>`;
      if (A) return `<li class="nj-b-brow${f ? ' is-follow' : ''}${chg ? ' is-changed' : ''}${s === 'emitido' ? ' is-past' : ''}" data-time="${x.time}"${s === 'emision' ? ' data-now="true"' : ''}><span class="nj-b-btime nj-b-tnum">${s === 'retrasado' ? '<s>' + x.time + '</s>' : x.time}</span><button type="button" class="nj-b-btitle" data-act="ficha"><b>${esc(x.t.short)}</b><span class="nj-meta">Ep. ${x.ep}${x.premiere ? ' · estreno' : ''}</span></button>${pill}</li>`;
      return `<tr class="nj-b-brow${f ? ' is-follow' : ''}${chg ? ' is-changed' : ''}${s === 'emitido' ? ' is-past' : ''}" data-time="${x.time}"${s === 'emision' ? ' data-now="true"' : ''}><td class="nj-b-btime nj-b-tnum">${s === 'retrasado' ? `<s>${x.time}</s> 23:45` : x.time}</td><td><button type="button" class="nj-b-btitle" data-act="ficha"><img src="${esc(x.t.poster)}" alt="" width="28" height="42" loading="lazy"><b>${esc(x.t.title)}</b></button></td><td class="nj-b-tnum">Ep. ${x.ep}${x.premiere ? ' · estreno' : ''}</td><td>${pill}${chg ? '<span class="nj-b-chg">Cambió a las 21:06</span>' : ''}</td><td class="nj-b-bbell">${bell(st, x)}</td></tr>`;
    }).join('');
    if (A) return head + `<ul class="nj-b-board is-list" data-region="airing">${rows}</ul>`;
    return head + `<div class="nj-b-boardwrap"><table class="nj-b-board" data-region="airing"><thead><tr><th scope="col">Hora</th><th scope="col">Título</th><th scope="col">Episodio</th><th scope="col">Estado</th><th scope="col"><span class="nj-b-vh">Seguir</span></th></tr></thead><tbody>${rows}</tbody></table></div>`;
  }

  function agendaView(ctx, st) {
    const days = [0, 1, 2, 3, 4, 5, 6].map((i) => ({ i, its: items(st, i) })).filter((d) => d.its.length);
    if (!days.length) return dayEmpty(st);
    return `<div class="nj-b-agenda" data-region="airing">${days.map((d) => { const today = st.wk === 0 && d.i === TODAY; let nowDone = false; return `<section class="nj-b-agday${today ? ' is-today' : ''}"><h2 class="nj-h3 nj-b-aghead">${today ? '<span class="nj-b-todaypill">Hoy</span>' : ''}${esc(WD[d.i])} ${fmt(dayDate(st.wk, d.i), { day: 'numeric', month: 'long' })}<span class="nj-meta">${d.its.length} ep.</span></h2><ul class="nj-b-evlist is-agenda">${d.its.map((x) => { let line = ''; if (today && !nowDone && x.time > NOW) { nowDone = true; line = `<li class="nj-b-nowline" aria-label="Ahora, ${NOW}"><span class="nj-b-tnum">${NOW}</span></li>`; } return line + evItem(ctx, st, x); }).join('')}</ul></section>`; }).join('')}</div>`;
  }

  function miniMonth(ctx, st) {
    const mon = dayDate(st.wk, 0); const y = mon.getUTCFullYear(); const m = mon.getUTCMonth();
    const first = new Date(Date.UTC(y, m, 1)); const lead = (first.getUTCDay() + 6) % 7; const days = new Date(Date.UTC(y, m + 1, 0)).getUTCDate();
    const cells = []; for (let i = 0; i < lead; i++) cells.push('<span></span>');
    for (let dnum = 1; dnum <= days; dnum++) {
      const t = Date.UTC(y, m, dnum); const off = Math.round((t - BASE) / 86400000); const wk = Math.floor(off / 7); const di = ((off % 7) + 7) % 7;
      const n = items(st, di).length; const inWeek = wk === st.wk; const today = off === TODAY;
      cells.push(`<button type="button" class="nj-b-mday${inWeek ? ' is-week' : ''}${today ? ' is-today' : ''}${inWeek && di === st.day ? ' is-sel' : ''}" data-act="jump" data-wk="${wk}" data-di="${di}" aria-label="${dnum} de ${fmt(first, { month: 'long' })}, ${n} episodios"><span class="nj-b-tnum">${dnum}</span><i class="nj-b-mdots" style="--n:${Math.min(3, Math.ceil(n / 2))}" aria-hidden="true"></i></button>`);
    }
    return `<section class="nj-b-panel nj-b-month"><div class="nj-section-head"><h2 class="nj-h3">${esc(fmt(first, { month: 'long', year: 'numeric' }))}</h2><span class="nj-meta">Mes</span></div><div class="nj-b-mgrid" role="group" aria-label="Mes">${WDS.map((d) => `<span class="nj-b-mwd">${d[0]}</span>`).join('')}${cells.join('')}</div></section>`;
  }
  function reminders(ctx, st) {
    const fl = st.follow.map((id) => D.byId[id]).filter(Boolean);
    return `<section class="nj-b-panel nj-b-rem"><div class="nj-section-head"><h2 class="nj-h3">Lo que sigues</h2><span class="nj-meta">${fl.length}</span></div><p class="nj-meta">La campana avisa cuando sale un episodio. ${B.isA(ctx) ? 'Notificaciones permitidas en este teléfono.' : 'El aviso sale en la bandeja del sistema.'}</p>${fl.length ? `<ul class="nj-b-remlist">${fl.map((t) => { const r = B.on(st.remind, t.id); const w = WEEK.find((x) => x.id === t.id); return `<li><img src="${esc(t.poster)}" alt="" width="32" height="48" loading="lazy"><span class="nj-grow"><b>${esc(t.short)}</b><span class="nj-meta">${w ? WDS[w.di] + ' ' + w.time : 'Sin fecha'}</span></span>${h.iconBtn(r ? 'notifications_active' : 'notifications_off', r ? `Avisos activos para ${t.short}` : `Activar avisos para ${t.short}`, { kind: r ? 'tonal' : '', fill: r, attrs: { 'data-act': 'remind', 'data-arg': t.id, 'aria-pressed': r } })}<button type="button" class="nj-btn is-text nj-b-small" data-act="follow" data-arg="${t.id}">Dejar de seguir</button></li>`; }).join('')}</ul>` : B.emptyState('notifications_none', 'No sigues nada todavía', 'Toca la campana de cualquier episodio para seguir la serie.')}</section>`;
  }

  function renderCal(ctx) {
    const st = B.defaults(ctx.state, calDefaults); const A = B.isA(ctx);
    const body = ctx.structure === 'salidas' ? boardView(ctx, st) : ctx.structure === 'agenda' ? agendaView(ctx, st) : weekView(ctx, st);
    const banner = B.limitBanner(ctx, st, { using: 'Ves la semana guardada hace 12 min' });
    if (A) {
      return `<div class="nj-b-cal">${B.appbar('Calendario', h.iconBtn('today', 'Ir a hoy', { attrs: { 'data-act': 'today' } }))}${banner ? `<div class="nj-b-pad">${banner}</div>` : ''}${toolbar(ctx, st)}<div class="${ctx.structure === 'semana' ? '' : 'nj-b-pad'}">${body}</div><div class="nj-b-pad nj-stack nj-b-gap-l"><button type="button" class="nj-b-expander" data-act="showRem" aria-expanded="${st.showRem}">${ic('notifications')}<span class="nj-grow">Lo que sigues y recordatorios</span><span class="nj-meta">${st.follow.length}</span>${ic(st.showRem ? 'expand_less' : 'expand_more')}</button>${st.showRem ? reminders(ctx, st) : ''}</div>${B.snackHtml(st)}</div>`;
    }
    return `<div class="nj-b-page nj-b-cal"><header class="nj-b-head"><h1 class="nj-b-title">Calendario</h1></header>${banner}${toolbar(ctx, st)}<div class="nj-b-calgrid"><div class="nj-b-calmain">${body}</div><aside class="nj-b-calaside">${miniMonth(ctx, st)}${reminders(ctx, st)}</aside></div>${B.snackHtml(st)}</div>`;
  }
  function mountCal(el, ctx) {
    const st = ctx.state;
    const H = Object.assign(B.snackHandlers(el, ctx), B.limitWire(el, ctx), {
      wk: (a, e, rd) => { st.wk += +a.dataset.d; rd(); },
      today: (a, e, rd) => { st.wk = 0; st.day = TODAY; st.boardDay = TODAY; rd(); },
      mine: (a, e, rd) => { st.mine = !st.mine; rd(); },
      day: (a, e, rd) => { st.day = +a.dataset.arg; rd(); },
      boardDay: (a, e, rd) => { st.boardDay = +a.dataset.arg; rd(); },
      jump: (a, e, rd) => { st.wk = +a.dataset.wk; st.day = +a.dataset.di; st.boardDay = st.day; rd(); },
      showRem: (a, e, rd) => { st.showRem = !st.showRem; rd(); },
      ficha: () => { if (NJ.screens.ficha) NJ.go('ficha'); },
      follow: (a) => { const id = a.dataset.arg; const t = D.byId[id]; const was = B.on(st.follow, id); st.follow = B.toggleIn(st.follow, id); if (was) st.remind = st.remind.filter((x) => x !== id); B.snack(el, ctx, was ? `Dejaste de seguir ${t.short}.` : `Sigues ${t.short}. Te avisamos cuando salga.`, was ? () => { st.follow = st.follow.concat([id]); } : null); },
      remind: (a, e, rd) => { st.remind = B.toggleIn(st.remind, a.dataset.arg); rd(); },
    });
    B.wire(el, ctx, H);
    el.querySelectorAll('[data-viewseg] [role="radio"]').forEach((b) => b.addEventListener('click', (e) => { e.stopPropagation(); NJ.go('calendario', { structure: b.dataset.value }); }));
    if (!st.changed) B.laterS(st, 2600, (now) => { st.changed = true; B.keep(now, ctx); });
  }

  NJ.registerScreen({
    id: 'calendario', name: 'Calendario', group: 'Descubrir', order: 2, platforms: ['desktop', 'android'],
    summary: 'Suma un tablero de salidas que muestra el estado de cada episodio de hoy y deja marcado lo que cambia; la campana para seguir y los recordatorios por serie están junto a cada episodio y en un panel fijo, no al final de la lista.',
    parity: 'CAL-01 (Hoy, vistas Semana, Agenda, Mes y Día: Mes es el panel lateral y Día es el tablero; anterior y siguiente, Solo lo que sigo, campana por episodio también en la vista semanal, Lo que sigues con aviso por serie y dejar de seguir, error con Reintentar), UI-01, HOME-09 (Ver todo llega aquí), SET-APP-18 (vista por defecto en Ajustes).',
    structures: [
      { id: 'semana', name: 'Semana', desc: 'Rejilla de 7 días con lo que sigues resaltado; en Android, tira de días y lista.' },
      { id: 'salidas', name: 'Tablero de salidas', desc: 'Como en un aeropuerto: hora, título, episodio y estado; la fila que cambia queda marcada.' },
      { id: 'agenda', name: 'Agenda', desc: 'Lista por día con la línea de la hora actual.' },
    ],
    render: renderCal, mount: mountCal,
  });
})();

/* ======================================================================
   3. BIBLIOTECA (estantes, tablero arrastrable, lista densa)
   ====================================================================== */
(function () {
  const NJ = window.NJ; if (!NJ || !NJ.B) return;
  const D = NJ.data, h = NJ.h, esc = h.esc, B = NJ.B, ic = B.ic;
  const ORDER = ['watching', 'planning', 'paused', 'rewatching', 'completed', 'dropped'];
  const READ = { watching: 'Leyendo', rewatching: 'Releyendo' };
  const UPD = ['hace 16 h', 'hace 2 d', 'hace 3 h', 'hace 5 d', 'ayer', 'hace 1 sem', 'hace 4 h', 'hace 2 sem', 'hace 6 d', 'hace 1 mes'];
  const MSTAT = ['watching', 'watching', 'planning', 'paused', 'watching', 'completed', 'watching', 'planning', 'dropped'];
  const libDefaults = { medio: 'anime', tab: 'lista', q: '', st: 'todos', sort: { k: 'upd', dir: 1 }, sel: [], selMode: false, over: {}, prog: {}, score: {}, fav: ['aonohako', 'koori', 'mahoyo'], favChars: ['Lily Flambert'], hist: ['tempal', 'laidoff', 'aonohako', 'koori', 'magical', 'chitose'], edit: null, ed: {}, datePick: null, sync: 'idle', synced: 'hace 3 min', collapsed: ['completed', 'dropped'], menu: null, confirmRm: false, landed: null, longMenu: null };

  const sName = (st, id) => (st.medio === 'manga' && READ[id]) || h.status(id).name;
  function entries(st) {
    let list;
    if (st.medio === 'manga') list = D.manga.map((m, i) => ({ id: m.id, title: m.title, short: m.title, poster: m.poster, status: MSTAT[i], progress: parseInt(m.chapter.replace(/\D/g, ''), 10), total: m.total, score: [8.5, 7, null, 6.5, 8, 9, 7.5, null, 5][i], format: m.kind, year: m.chapter, unit: 'Cap.', updated: UPD[i % UPD.length] }));
    else if (st.medio === 'novela') list = [];
    else list = D.titles.map((t, i) => ({ id: t.id, title: t.title, short: t.short, poster: t.poster, status: t.status, progress: t.progress, total: t.eps, score: t.score, next: t.next, format: t.format, year: t.year, resume: t.resume, eps: t.eps, unit: 'Ep.', updated: UPD[i % UPD.length], i }));
    list = list.map((e) => Object.assign(e, { status: st.over[e.id] || e.status, progress: st.prog[e.id] != null ? st.prog[e.id] : e.progress, score: st.score[e.id] !== undefined ? st.score[e.id] : e.score }));
    const q = B.fold(st.q.trim()); if (q) list = list.filter((e) => B.fold(e.title + ' ' + e.short).includes(q));
    return list;
  }
  const progTxt = (e) => `${e.unit} ${e.progress}${e.total ? ' / ' + e.total : ''}`;
  const cardItem = (e) => Object.assign({}, e, { progress: e.progress, eps: e.total });

  function header(ctx, st) {
    const A = B.isA(ctx);
    const syncBtn = st.sync === 'run' ? `<span class="nj-b-syncing" role="status"><span class="nj-b-spin" aria-hidden="true"></span>Sincronizando…</span>` : h.btn('Sincronizar ahora', { kind: A ? 'tonal' : 'text', icon: 'sync', attrs: { 'data-act': 'sync' } });
    const sync = `<div class="nj-b-syncline"><span class="nj-badge nj-b-localpill">${ic('smartphone', { size: 16 })}${A ? 'Local' : 'Local en este dispositivo'}</span><span class="nj-meta">Historial sincronizado con ${esc(D.link.pc)} ${esc(st.synced)}</span>${syncBtn}</div>`;
    const accounts = `<div class="nj-cluster nj-b-accline"><span class="nj-badge">${ic('link', { size: 16 })}AniList · MyAnimeList</span><span class="nj-badge nj-b-warnpill">${ic('schedule', { size: 16 })}2 cambios pendientes</span></div>`;
    const medio = `<div data-bset="medio" class="${A ? 'nj-b-segfull' : ''}">${h.seg('Medio', [{ id: 'anime', label: 'Anime', icon: 'movie' }, { id: 'manga', label: 'Manga', icon: 'menu_book' }, { id: 'novela', label: 'Novela', icon: 'auto_stories' }], st.medio)}</div>`;
    const tabs = `<div class="nj-tabs nj-b-libtabs" role="tablist" data-local>${[['lista', 'Mi lista'], ['historial', 'Historial'], ['favoritos', 'Favoritos']].map(([id, l]) => `<button type="button" role="tab" class="nj-tab" aria-selected="${st.tab === id}" data-act="tab" data-arg="${id}">${l}</button>`).join('')}${A ? '' : `<span class="nj-b-tabs-end">${h.btn('Torrents descargados', { kind: 'text', icon: 'swap_vert', attrs: { 'data-go': 'transferencias' } })}</span>`}</div>`;
    if (A) return `${B.appbar('Biblioteca', h.iconBtn('swap_vert', 'Torrents descargados, en Transferencias', { attrs: { 'data-go': 'transferencias' } }))}<div class="nj-b-pad nj-stack">${medio}${sync}</div>${tabs}`;
    return `<header class="nj-b-head"><h1 class="nj-b-title">Biblioteca</h1>${medio}<span class="nj-b-head-end">${accounts}</span></header>${sync}${tabs}`;
  }
  function filters(ctx, st, list, o = {}) {
    const counts = {}; list.forEach((e) => { counts[e.status] = (counts[e.status] || 0) + 1; });
    const opts = [{ id: 'todos', label: 'Todo', count: list.length }].concat(ORDER.filter((s) => counts[s]).map((s) => ({ id: s, label: sName(st, s), count: counts[s] })));
    return `<div class="nj-b-libfilters"><label class="nj-search nj-b-minisearch">${ic('search', { size: 18 })}<input id="${B.pid(ctx, 'libq')}" data-input="q" placeholder="Filtrar por título" value="${esc(st.q)}" aria-label="Filtrar por título"></label>${o.status ? `<div data-bset="st" class="nj-b-chiprow is-plain" data-keep="libst">${h.chips('Estado', opts, [st.st])}</div>` : ''}</div>`;
  }
  function cardWrap(ctx, st, e, opts) {
    return `<div class="nj-b-cardwrap${st.landed === e.id ? ' is-landed' : ''}" data-id="${e.id}">${h.card(cardItem(e), opts)}${h.iconBtn('edit', 'Editar ' + e.short, { kind: 'tonal', attrs: { 'data-act': 'edit', 'data-arg': e.id, 'data-stop': '' } })}</div>`;
  }

  function shelves(ctx, st, list) {
    const A = B.isA(ctx);
    const cont = st.medio === 'anime' ? list.filter((e) => e.status === 'watching' && (e.resume || e.progress)).slice(0, 6) : [];
    const groups = ORDER.map((s) => ({ s, its: list.filter((e) => e.status === s) })).filter((g) => g.its.length && (st.st === 'todos' || st.st === g.s));
    return `${cont.length && !st.q ? `<section class="nj-b-sec" data-region="tonight"><div class="nj-section-head"><h2 class="nj-h2">Continuar</h2><span class="nj-meta">Toca para seguir donde quedaste</span></div><div class="nj-row" data-region="row" data-row-title="Continuar" data-keep="cont">${cont.map((e) => h.card(cardItem(e), { context: 'continue' })).join('')}</div></section>` : ''}${filters(ctx, st, list, { status: true })}${groups.length ? groups.map((g) => { const col = B.on(st.collapsed, g.s); return `<section class="nj-b-shelf" data-status="${g.s}"><button type="button" class="nj-b-shelfhead" data-act="collapse" data-arg="${g.s}" aria-expanded="${!col}">${ic(col ? 'chevron_right' : 'expand_more')}<span class="nj-status" data-status="${g.s}">${esc(sName(st, g.s))}</span><span class="nj-meta nj-b-tnum">${g.its.length}</span></button>${col ? '' : `<div class="${A ? 'nj-row' : 'nj-grid'} nj-b-shelfgrid" style="--nj-cell:${A ? 112 : 128}px" data-region="${A ? 'row' : 'grid'}" data-row-title="${esc(sName(st, g.s))}" data-keep="sh-${g.s}">${g.its.map((e) => cardWrap(ctx, st, e, { size: 's', context: A ? 'row' : 'grid', show: { progress: true, next: g.s === 'watching' } })).join('')}</div>`}</section>`; }).join('') : noMatch(st)}`;
  }
  function noMatch(st) { return B.emptyState('search_off', st.q ? `Nada en tu lista coincide con «${st.q}»` : 'No hay títulos con este estado', '', h.btn(st.q ? 'Borrar filtro' : 'Ver todo', { kind: 'tonal', attrs: { 'data-act': 'clearFilter' } })); }

  function board(ctx, st, list) {
    const A = B.isA(ctx);
    return `${filters(ctx, st, list)}<p class="nj-meta nj-b-boardhint">${ic(A ? 'touch_app' : 'drag_pan', { size: 18 })}${A ? 'Mantén pulsada una tarjeta para moverla a otro estado.' : 'Arrastra una tarjeta a otra columna para cambiar su estado. Con teclado, usa su menú Mover a.'}</p><div class="nj-b-kanban" data-cards-local data-keep="kanban">${ORDER.map((s) => { const its = list.filter((e) => e.status === s); return `<section class="nj-b-kcol" data-status="${s}" data-drop="${s}" aria-label="${esc(sName(st, s))}"><header class="nj-b-kcol-head"><span class="nj-status" data-status="${s}">${esc(sName(st, s))}</span><span class="nj-meta nj-b-tnum">${its.length}</span></header><ul class="nj-b-klist">${its.map((e) => `<li class="nj-b-kcard${st.landed === e.id ? ' is-landed' : ''}" draggable="${!A}" data-drag="${e.id}" data-act="edit" data-arg="${e.id}">${h.card(cardItem(e), { context: 'compact', show: { status: false } })}<span class="nj-b-kmeta nj-b-tnum">${progTxt(e)}${e.score ? ' · ' + ic('star', { size: 14, fill: true }) + e.score : ''}</span>${A ? '' : h.iconBtn('more_vert', 'Mover a…', { attrs: { 'data-act': 'moveMenu', 'data-arg': e.id, 'aria-haspopup': 'menu', 'aria-expanded': st.menu === e.id } })}${st.menu === e.id ? moveMenu(st, e) : ''}</li>`).join('')}${its.length ? '' : `<li class="nj-b-kempty">Suelta aquí</li>`}</ul></section>`; }).join('')}</div>`;
  }
  function moveMenu(st, e) { return `<div class="nj-menu nj-b-movemenu" role="menu" aria-label="Mover a">${ORDER.filter((s) => s !== e.status).map((s) => `<button type="button" role="menuitem" class="nj-menu-item" data-act="moveTo" data-arg="${e.id}" data-to="${s}"><span class="nj-status" data-status="${s}">${esc(sName(st, s))}</span></button>`).join('')}</div>`; }

  const COLS = [['title', 'Título'], ['status', 'Estado'], ['prog', 'Progreso'], ['score', 'Puntuación'], ['next', 'Próximo episodio'], ['upd', 'Actualizado']];
  function sorted(st, list) {
    const k = st.sort.k; const dir = st.sort.dir;
    const val = (e) => ({ title: e.title, status: ORDER.indexOf(e.status), prog: e.total ? e.progress / e.total : 0, score: e.score || 0, next: e.next ? e.next.in : 'zz', upd: UPD.indexOf(e.updated) }[k]);
    return list.slice().sort((a, b) => { const x = val(a); const y = val(b); return (x < y ? -1 : x > y ? 1 : 0) * dir; });
  }
  function table(ctx, st, list) {
    const A = B.isA(ctx); const rows = sorted(st, st.st === 'todos' ? list : list.filter((e) => e.status === st.st));
    const all = rows.length && rows.every((e) => B.on(st.sel, e.id));
    if (!rows.length) return filters(ctx, st, list, { status: true }) + noMatch(st);
    if (A) {
      return `${filters(ctx, st, list, { status: true })}<div class="nj-b-sortline"><span class="nj-meta">${rows.length} títulos</span><button type="button" class="nj-chip" data-act="sortCycle">${ic('sort', { size: 18 })}${esc(COLS.find((c) => c[0] === st.sort.k)[1])}${ic(st.sort.dir > 0 ? 'arrow_upward' : 'arrow_downward', { size: 16 })}</button>${st.selMode ? h.btn('Listo', { kind: 'text', attrs: { 'data-act': 'selDone' } }) : h.btn('Seleccionar', { kind: 'text', attrs: { 'data-act': 'selMode' } })}</div><ul class="nj-b-alist" data-cards-local>${rows.map((e) => { const s = B.on(st.sel, e.id); return `<li class="nj-b-arow${s ? ' is-sel' : ''}" data-long="${e.id}" data-act="${st.selMode ? 'selOne' : 'edit'}" data-arg="${e.id}">${st.selMode ? `<span class="nj-b-check${s ? ' is-on' : ''}" aria-hidden="true">${ic('check', { size: 18 })}</span>` : ''}${h.card(cardItem(e), { context: 'compact' })}<span class="nj-b-arow-meta"><span class="nj-status" data-status="${e.status}">${esc(sName(st, e.status))}</span><span class="nj-meta nj-b-tnum">${progTxt(e)}</span></span></li>`; }).join('')}</ul>`;
    }
    return `${filters(ctx, st, list, { status: true })}<div class="nj-b-tablewrap" data-cards-local data-keep="table"><table class="nj-b-table"><thead><tr><th scope="col" class="nj-b-tcheck"><input type="checkbox" data-act="selAll" aria-label="Seleccionar todo" ${all ? 'checked' : ''}></th>${COLS.map(([k, l]) => { const cur = st.sort.k === k; return `<th scope="col" aria-sort="${cur ? (st.sort.dir > 0 ? 'ascending' : 'descending') : 'none'}"><button type="button" class="nj-b-sortbtn" data-act="sort" data-arg="${k}">${l}${ic(cur ? (st.sort.dir > 0 ? 'arrow_upward' : 'arrow_downward') : 'unfold_more', { size: 16 })}</button></th>`; }).join('')}<th scope="col"><span class="nj-b-vh">Acciones</span></th></tr></thead><tbody>${rows.map((e) => { const s = B.on(st.sel, e.id); return `<tr class="${s ? 'is-sel' : ''}"><td class="nj-b-tcheck"><input type="checkbox" data-act="selOne" data-arg="${e.id}" aria-label="Seleccionar ${esc(e.short)}" ${s ? 'checked' : ''}></td><td class="nj-b-ttitle">${h.card(cardItem(e), { context: 'compact' })}</td><td><span class="nj-status" data-status="${e.status}">${esc(sName(st, e.status))}</span></td><td><div class="nj-b-tprog">${h.progress(e.total ? e.progress / e.total : 0, { label: 'Progreso de ' + e.short })}<span class="nj-b-tnum">${e.progress}${e.total ? '/' + e.total : ''}</span></div></td><td class="nj-b-tnum">${e.score ? e.score.toFixed(1) : '<span class="nj-muted">Sin nota</span>'}</td><td class="nj-b-tnum">${e.next ? 'Ep. ' + e.next.ep + ' en ' + esc(e.next.in) : '<span class="nj-muted">Sin fecha</span>'}</td><td class="nj-meta">${esc(e.updated)}</td><td>${h.iconBtn('edit', 'Editar ' + e.short, { attrs: { 'data-act': 'edit', 'data-arg': e.id } })}</td></tr>`; }).join('')}</tbody></table></div>`;
  }
  function batchBar(ctx, st) {
    if (!st.sel.length) return '';
    return `<div class="nj-b-batch" role="toolbar" aria-label="Acciones para la selección"><span class="nj-b-batch-n" role="status"><b class="nj-b-tnum">${st.sel.length}</b> ${st.sel.length === 1 ? 'seleccionado' : 'seleccionados'}</span><div class="nj-b-batch-acts"><span class="nj-b-relwrap">${h.btn('Cambiar estado', { kind: 'tonal', icon: 'swap_horiz', attrs: { 'data-act': 'batchMenu', 'aria-haspopup': 'menu', 'aria-expanded': st.menu === '__batch' } })}${st.menu === '__batch' ? `<div class="nj-menu nj-b-movemenu is-up" role="menu">${ORDER.map((s) => `<button type="button" role="menuitem" class="nj-menu-item" data-act="batchTo" data-to="${s}"><span class="nj-status" data-status="${s}">${esc(sName(st, s))}</span></button>`).join('')}</div>` : ''}</span>${h.btn('+1 episodio', { kind: 'tonal', icon: 'exposure_plus_1', attrs: { 'data-act': 'batchPlus' } })}${st.confirmRm ? h.btn(`Quitar ${st.sel.length}`, { kind: 'danger', icon: 'delete', attrs: { 'data-act': 'batchRm' } }) : h.iconBtn('playlist_remove', 'Quitar de la lista', { attrs: { 'data-act': 'batchRmAsk' } })}${h.btn('Limpiar', { kind: 'text', attrs: { 'data-act': 'selClear' } })}</div></div>`;
  }

  function history(ctx, st) {
    const its = st.hist.map((id, i) => ({ t: D.byId[id], ep: (D.byId[id].progress || 1), ago: ['hace 2 h', 'hace 16 h', 'ayer', 'hace 2 d', 'hace 3 d', 'hace 1 sem'][i] }));
    if (!its.length) return B.emptyState('history', 'No hay historial', 'Lo que veas aparece aquí, también lo que veas en el teléfono si está vinculado.', h.btn('Ir a Explorar', { kind: 'filled', attrs: { 'data-go': 'explorar' } }));
    return `<div class="nj-b-histhead"><span class="nj-meta">${its.length} episodios · incluye lo visto en ${esc(D.link.phone)}</span>${h.btn('Borrar historial', { kind: 'text', attrs: { 'data-act': 'histClear' } })}</div><ul class="nj-list nj-b-hist" data-cards-local>${its.map((x, i) => `<li class="nj-b-histrow"><button type="button" class="nj-list-row" data-act="ficha"><img src="${esc(x.t.poster)}" alt="" width="40" height="60" loading="lazy"><span class="nj-grow"><span class="nj-b-rowtitle">${esc(x.t.short)}</span><span class="nj-meta">Ep. ${x.ep} · visto ${x.ago}</span></span><span class="nj-badge">Visto</span></button>${h.iconBtn('close', 'Quitar del historial', { attrs: { 'data-act': 'histDel', 'data-arg': i } })}</li>`).join('')}</ul>`;
  }
  function favorites(ctx, st) {
    const its = st.fav.map((id) => D.byId[id]).filter(Boolean);
    if (!its.length && !st.favChars.length) return B.emptyState('favorite', 'Sin favoritos todavía', 'Marca el corazón en una ficha o en un personaje.', '');
    return `<section class="nj-b-sec"><h2 class="nj-h3">Personajes</h2>${st.favChars.length ? `<div class="nj-cluster">${st.favChars.map((c, i) => `<span class="nj-b-favchar"><span class="nj-b-avatar" aria-hidden="true">${esc(c[0])}</span>${esc(c)}${h.iconBtn('close', `Quitar a ${c} de favoritos`, { attrs: { 'data-act': 'favCharDel', 'data-arg': i } })}</span>`).join('')}</div>` : '<p class="nj-meta">Ningún personaje favorito.</p>'}</section><section class="nj-b-sec"><h2 class="nj-h3">Títulos</h2><div class="nj-grid" style="--nj-cell:${B.isA(ctx) ? 104 : 128}px" data-region="grid">${its.map((t) => h.card(t, { size: 's', context: 'grid', show: { status: true } })).join('')}</div></section>`;
  }

  /* Editor de entrada (LIB-06): todo se guarda al tocar. */
  function editor(ctx, st, list) {
    const e = list.find((x) => x.id === st.edit) || entries(Object.assign({}, st, { q: '' })).find((x) => x.id === st.edit); if (!e) return '';
    const ed = B.defaults(st.ed[e.id] = st.ed[e.id] || {}, { start: e.progress ? 3 : null, end: null, rep: 0, notes: '', priv: false });
    const fav = B.on(st.fav, e.id);
    const dateBtn = (k, label) => `<div class="nj-b-datefield"><span class="nj-meta">${label}</span><button type="button" class="nj-btn is-outline" data-act="datePick" data-arg="${k}" aria-expanded="${st.datePick === k}">${ic('calendar_today', { size: 18 })}${ed[k] ? ed[k] + ' nov 2026' : 'Sin fecha'}</button>${st.datePick === k ? `<div class="nj-b-datepop" role="dialog" aria-label="${label}"><div class="nj-b-mgrid">${['L', 'M', 'M', 'J', 'V', 'S', 'D'].map((d) => `<span class="nj-b-mwd">${d}</span>`).join('')}${'<span></span>'.repeat(6)}${Array.from({ length: 30 }, (_, i) => `<button type="button" class="nj-b-mday${ed[k] === i + 1 ? ' is-sel' : ''}${i + 1 === 25 ? ' is-today' : ''}" data-act="dateSet" data-k="${k}" data-arg="${i + 1}"><span class="nj-b-tnum">${i + 1}</span></button>`).join('')}</div>${h.btn('Quitar fecha', { kind: 'text', attrs: { 'data-act': 'dateSet', 'data-k': k, 'data-arg': 0 } })}</div>` : ''}</div>`;
    const body = `<div class="nj-b-edhead"><img src="${esc(e.poster)}" alt="" width="64" height="96"><div class="nj-stack" style="gap:4px"><b class="nj-b-edtitle">${esc(e.title)}</b><span class="nj-meta">${esc(e.format || '')} · ${e.total ? e.total + (st.medio === 'manga' ? ' capítulos' : ' episodios') : ''}</span><span class="nj-b-savedline">${ic('cloud_done', { size: 16 })}Cada cambio se guarda al tocar y se envía a AniList</span></div></div>
      <section class="nj-b-edsec" data-region="status-picker"><h3 class="nj-h3">Estado</h3><div class="nj-cluster" role="radiogroup" aria-label="Estado">${ORDER.map((s) => `<button type="button" role="radio" class="nj-chip nj-b-stchip" data-status="${s}" aria-checked="${e.status === s}" aria-pressed="${e.status === s}" data-act="edStatus" data-arg="${s}">${e.status === s ? ic('check', { size: 18 }) : ''}${esc(sName(st, s))}</button>`).join('')}</div></section>
      <section class="nj-b-edsec nj-b-edgrid"><div><h3 class="nj-h3">Progreso</h3>${B.stepper(ctx, 'prog', 'progreso', e.progress, { min: 0, max: e.total || 9999, fmt: (v) => `${e.unit} ${v}${e.total ? ' de ' + e.total : ''}` })}</div><div><h3 class="nj-h3">Repeticiones</h3>${B.stepper(ctx, 'rep', 'repeticiones', ed.rep, { min: 0, max: 99, fmt: (v) => (v ? v + (v === 1 ? ' vez' : ' veces') : 'Ninguna') })}</div></section>
      <section class="nj-b-edsec">${h.slider(B.pid(ctx, 's-edscore'), 'Puntuación', { min: 0, max: 100, step: 5, value: e.score ? Math.round(e.score * 10) : 0, unit: ' / 100' })}<p class="nj-meta">0 deja el título sin puntuación.</p></section>
      <section class="nj-b-edsec nj-b-edgrid">${dateBtn('start', 'Empecé')}${dateBtn('end', 'Terminé')}</section>
      <section class="nj-b-edsec"><label class="nj-b-flabel" for="${B.pid(ctx, 'ednotes')}">Notas</label><textarea id="${B.pid(ctx, 'ednotes')}" class="nj-b-textarea" rows="3" data-ed="notes" placeholder="Solo las ves tú, salvo que la entrada sea pública">${esc(ed.notes)}</textarea></section>
      <section class="nj-b-edsec">${h.toggle(B.pid(ctx, 't-edpriv'), 'Entrada privada', ed.priv, { desc: 'No aparece en tu perfil ni en la actividad de AniList.' })}</section>
      <section class="nj-b-edsec nj-cluster"><button type="button" class="nj-btn ${fav ? 'is-tonal' : 'is-outline'}" data-act="edFav" aria-pressed="${fav}">${ic('favorite', { fill: fav })}${fav ? 'En favoritos' : 'Agregar a favoritos'}</button>${st.confirmRm === 'ed' ? h.btn('Confirmar: quitar de la lista', { kind: 'danger', icon: 'delete', attrs: { 'data-act': 'edRemove' } }) : h.btn('Quitar de la lista', { kind: 'text', attrs: { 'data-act': 'edRemoveAsk' } })}</section>`;
    return B.sheet(ctx, 'Editar entrada', body, { close: 'edClose' });
  }

  function renderLib(ctx) {
    const st = B.defaults(ctx.state, libDefaults); const A = B.isA(ctx);
    const list = entries(st);
    let main;
    if (st.medio === 'novela') main = B.emptyState('auto_stories', 'Todavía no sigues novelas', 'Las novelas que agregues desde Explorar o desde AniList aparecen aquí con su capítulo.', h.btn('Buscar novelas', { kind: 'filled', icon: 'search', attrs: { 'data-act': 'goNovels' } }) + h.btn('Listas y cuentas', { kind: 'outline', attrs: { 'data-act': 'goAccounts' } }));
    else if (st.tab === 'historial') main = history(ctx, st);
    else if (st.tab === 'favoritos') main = favorites(ctx, st);
    else main = ctx.structure === 'tablero' ? board(ctx, st, list) : ctx.structure === 'lista' ? table(ctx, st, list) : shelves(ctx, st, list);
    const longSheet = st.longMenu ? B.sheet(ctx, 'Mover a…', `<div class="nj-list">${ORDER.map((s) => `<button type="button" class="nj-list-row" data-act="moveTo" data-arg="${st.longMenu}" data-to="${s}"><span class="nj-status" data-status="${s}">${esc(sName(st, s))}</span></button>`).join('')}</div><button type="button" class="nj-list-row" data-act="edit" data-arg="${st.longMenu}">${ic('edit')}<span>Editar entrada…</span></button>`, { close: 'longClose' }) : '';
    const inner = `${header(ctx, st)}<div class="${A ? 'nj-b-pad nj-stack' : 'nj-b-libmain'}">${main}</div>${A ? '' : ''}${batchBar(ctx, st)}${st.edit ? editor(ctx, st, list) : ''}${longSheet}${B.snackHtml(st)}`;
    return A ? `<div class="nj-b-lib">${inner}</div>` : `<div class="nj-b-page nj-b-lib">${inner}</div>`;
  }

  function mountLib(el, ctx) {
    const st = ctx.state; const D2 = D;
    const findE = (id) => entries(Object.assign({}, st, { q: '' })).find((x) => x.id === id);
    const move = (id, to) => {
      const e = findE(id); if (!e || e.status === to) return;
      const prev = st.over[id]; st.over[id] = to; st.landed = id; st.menu = null; st.longMenu = null;
      B.snack(el, ctx, `${e.short} pasó a ${sName(st, to)}.`, () => { if (prev) st.over[id] = prev; else delete st.over[id]; });
      B.laterS(st, 900, () => { st.landed = null; });
    };
    const H = Object.assign(B.snackHandlers(el, ctx), {
      $esc: (rd) => { if (st.datePick) { st.datePick = null; rd(); return true; } if (st.edit) { st.edit = null; rd(); return true; } if (st.menu || st.longMenu) { st.menu = null; st.longMenu = null; rd(); return true; } if (st.sel.length) { st.sel = []; rd(); return true; } return false; },
      tab: (a, e, rd) => { st.tab = a.dataset.arg; rd(); },
      collapse: (a, e, rd) => { st.collapsed = B.toggleIn(st.collapsed, a.dataset.arg); rd(); },
      clearFilter: (a, e, rd) => { st.q = ''; st.st = 'todos'; rd(); },
      edit: (a, e, rd) => { if (st.selMode && B.isA(ctx)) { st.sel = B.toggleIn(st.sel, a.dataset.arg); rd(); return; } st.edit = a.dataset.arg; st.confirmRm = false; st.datePick = null; st.longMenu = null; rd(); },
      edClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.edit = null; st.datePick = null; rd(); },
      longClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.longMenu = null; rd(); },
      edStatus: (a, e, rd) => { st.over[st.edit] = a.dataset.arg; rd(); },
      step: (a, e, rd) => { const d = +a.dataset.d; const en = findE(st.edit); if (a.dataset.key === 'prog') st.prog[st.edit] = Math.max(0, Math.min(en.total || 9999, en.progress + d)); else { const ed = st.ed[st.edit]; ed.rep = Math.max(0, ed.rep + d); } rd(); },
      datePick: (a, e, rd) => { st.datePick = st.datePick === a.dataset.arg ? null : a.dataset.arg; rd(); },
      dateSet: (a, e, rd) => { st.ed[st.edit][a.dataset.k] = +a.dataset.arg || null; st.datePick = null; rd(); },
      edFav: (a, e, rd) => { st.fav = B.toggleIn(st.fav, st.edit); rd(); },
      edRemoveAsk: (a, e, rd) => { st.confirmRm = 'ed'; rd(); },
      edRemove: (a) => { const id = st.edit; const en = findE(id); st.edit = null; st.confirmRm = false; B.snack(el, ctx, `${en.short} salió de tu lista.`, () => {}); },
      moveMenu: (a, e, rd) => { st.menu = st.menu === a.dataset.arg ? null : a.dataset.arg; rd(); },
      moveTo: (a) => move(a.dataset.arg, a.dataset.to),
      sort: (a, e, rd) => { const k = a.dataset.arg; st.sort = st.sort.k === k ? { k, dir: -st.sort.dir } : { k, dir: 1 }; rd(); },
      sortCycle: (a, e, rd) => { const i = COLS.findIndex((c) => c[0] === st.sort.k); st.sort = { k: COLS[(i + 1) % COLS.length][0], dir: 1 }; rd(); },
      selOne: (a, e, rd) => { st.sel = B.toggleIn(st.sel, a.dataset.arg); st.confirmRm = false; rd(); },
      selAll: (a, e, rd) => { const ids = sorted(st, entries(st)).filter((x) => st.st === 'todos' || x.status === st.st).map((x) => x.id); st.sel = ids.every((id) => B.on(st.sel, id)) ? [] : ids; rd(); },
      selMode: (a, e, rd) => { st.selMode = true; rd(); },
      selDone: (a, e, rd) => { st.selMode = false; st.sel = []; rd(); },
      selClear: (a, e, rd) => { st.sel = []; st.confirmRm = false; st.menu = null; rd(); },
      batchMenu: (a, e, rd) => { st.menu = st.menu === '__batch' ? null : '__batch'; rd(); },
      batchTo: (a) => { const to = a.dataset.to; const prev = {}; st.sel.forEach((id) => { prev[id] = st.over[id]; st.over[id] = to; }); const n = st.sel.length; st.sel = []; st.menu = null; B.snack(el, ctx, `${n} títulos pasaron a ${sName(st, to)}.`, () => { Object.keys(prev).forEach((id) => { if (prev[id]) st.over[id] = prev[id]; else delete st.over[id]; }); }); },
      batchPlus: (a) => { const prev = {}; st.sel.forEach((id) => { const en = findE(id); prev[id] = st.prog[id]; st.prog[id] = Math.min(en.total || 9999, en.progress + 1); }); B.snack(el, ctx, `Se sumó un episodio a ${st.sel.length} títulos.`, () => { Object.keys(prev).forEach((id) => { if (prev[id] == null) delete st.prog[id]; else st.prog[id] = prev[id]; }); }); },
      batchRmAsk: (a, e, rd) => { st.confirmRm = true; rd(); },
      batchRm: (a) => { const n = st.sel.length; st.sel = []; st.confirmRm = false; B.snack(el, ctx, `${n} títulos salieron de tu lista.`, () => {}); },
      sync: (a, e, rd) => { st.sync = 'run'; rd(); B.laterS(st, 1300, (now) => { st.sync = 'idle'; st.synced = 'hace un momento'; B.snack(now, ctx, 'Historial sincronizado.'); }); },
      histDel: (a) => { const i = +a.dataset.arg; const id = st.hist[i]; st.hist.splice(i, 1); B.snack(el, ctx, 'Se quitó del historial.', () => { st.hist.splice(i, 0, id); }); },
      histClear: (a) => { const old = st.hist.slice(); st.hist = []; B.snack(el, ctx, 'Historial borrado en este equipo.', () => { st.hist = old; }); },
      favCharDel: (a) => { const i = +a.dataset.arg; const c = st.favChars[i]; st.favChars.splice(i, 1); B.snack(el, ctx, `${c} salió de favoritos.`, () => { st.favChars.splice(i, 0, c); }); },
      ficha: () => { if (NJ.screens.ficha) NJ.go('ficha'); },
      goNovels: () => { const s = NJ.screenState['explorar:' + ctx.platform] = NJ.screenState['explorar:' + ctx.platform] || {}; s.tipo = 'novelas'; NJ.go('explorar', { structure: 'facetas' }); },
      goAccounts: () => { const s = NJ.screenState['ajustes:' + ctx.platform] = NJ.screenState['ajustes:' + ctx.platform] || {}; s.cat = 'cuentas'; s.open = true; NJ.go('ajustes'); },
      noop: () => {},
    });
    const redraw = B.wire(el, ctx, H);
    let qt;
    el.addEventListener('input', (e) => {
      const t = e.target;
      if (t.dataset.input === 'q') { st.q = t.value; clearTimeout(qt); qt = setTimeout(() => { if (el.isConnected) redraw(); }, 200); }
      if (t.dataset.ed && st.edit) st.ed[st.edit][t.dataset.ed] = t.value;
    });
    el.addEventListener('change', (e) => {
      const t = e.target;
      if (t.id === B.pid(ctx, 's-edscore')) { st.score[st.edit] = +t.value ? +t.value / 10 : null; redraw(); }
      if (t.id === B.pid(ctx, 't-edpriv')) st.ed[st.edit].priv = t.checked;
    });
    el.addEventListener('contextmenu', (e) => { const c = e.target.closest('.nj-b-cardwrap, .nj-b-kcard, .nj-b-table tr'); if (!c || B.isA(ctx)) return; const id = c.dataset.id || c.dataset.drag || (c.querySelector('[data-act="edit"]') || {}).dataset?.arg; if (id) { e.preventDefault(); st.edit = id; redraw(); } });
    /* Arrastrar y soltar entre columnas (escritorio). */
    el.addEventListener('dragstart', (e) => { const c = e.target.closest('[data-drag]'); if (!c) return; st.drag = c.dataset.drag; e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', st.drag); c.classList.add('is-dragging'); el.querySelector('.nj-b-kanban')?.classList.add('is-dragging'); });
    el.addEventListener('dragend', (e) => { el.querySelectorAll('.is-dragging, .is-over').forEach((n) => n.classList.remove('is-dragging', 'is-over')); });
    el.addEventListener('dragover', (e) => { const col = e.target.closest('[data-drop]'); if (!col || !st.drag) return; e.preventDefault(); e.dataTransfer.dropEffect = 'move'; el.querySelectorAll('[data-drop].is-over').forEach((n) => n !== col && n.classList.remove('is-over')); col.classList.add('is-over'); });
    el.addEventListener('dragleave', (e) => { const col = e.target.closest('[data-drop]'); if (col && !col.contains(e.relatedTarget)) col.classList.remove('is-over'); });
    el.addEventListener('drop', (e) => { const col = e.target.closest('[data-drop]'); if (!col || !st.drag) return; e.preventDefault(); const id = st.drag; st.drag = null; move(id, col.dataset.drop); });
    /* Pulsación larga en Android: abre "Mover a…" o entra en modo selección en la lista. */
    let lt = null;
    el.addEventListener('pointerdown', (e) => {
      if (!B.isA(ctx)) return; const c = e.target.closest('[data-drag], [data-long]'); if (!c) return;
      lt = setTimeout(() => { lt = null; st._longFired = true; if (c.dataset.long) { st.selMode = true; st.sel = B.toggleIn(st.sel, c.dataset.long); } else st.longMenu = c.dataset.drag; redraw(); }, 480);
    });
    ['pointerup', 'pointerleave', 'pointercancel', 'pointermove'].forEach((n) => el.addEventListener(n, (e) => { if (n === 'pointermove' && Math.abs(e.movementX) + Math.abs(e.movementY) < 4) return; clearTimeout(lt); lt = null; }));
    el.addEventListener('click', (e) => { if (st._longFired) { st._longFired = false; e.stopPropagation(); e.preventDefault(); } }, true);
  }

  NJ.registerScreen({
    id: 'biblioteca', name: 'Biblioteca', group: 'Tu colección', order: 1, platforms: ['desktop', 'android'],
    summary: 'Las 7 pestañas mezcladas pasan a un segmentado de medio (Anime, Manga, Novela) y tres vistas (Mi lista, Historial, Favoritos); el estado se cambia arrastrando, en lote o desde un editor que siempre está a un toque, y Torrents se va a Transferencias con un acceso directo.',
    parity: 'LIB-01 (Local en este dispositivo, Sincronizar ahora, pestañas reordenadas), LIB-02 (Continuar), LIB-03 (Historial, ahora con quitar y borrar), LIB-04 (estados con conteo, filtrar por título, orden, cuentas y cambios pendientes, editar también con clic derecho), LIB-05 (personajes y títulos favoritos), LIB-06 (estado, progreso, puntuación, fechas, repeticiones, notas, privado, favorito, quitar), LIB-07 (Mover a…), LIB-08 (Manga y Novela en el segmentado; Torrents en Transferencias).',
    structures: [
      { id: 'estantes', name: 'Estantes', desc: 'Continuar arriba y una sección plegable por estado.' },
      { id: 'tablero', name: 'Tablero', desc: 'Kanban por estado: arrastra una tarjeta a otra columna; en Android, pulsación larga y Mover a.' },
      { id: 'lista', name: 'Lista', desc: 'Tabla densa con columnas ordenables y selección múltiple con barra de lote.' },
    ],
    render: renderLib, mount: mountLib,
  });
})();

/* ======================================================================
   4. AJUSTES (lista y detalle, búsqueda primero)
   ====================================================================== */
(function () {
  const NJ = window.NJ; if (!NJ || !NJ.B) return;
  const D = NJ.data, h = NJ.h, esc = h.esc, B = NJ.B, ic = B.ic;
  const CAT_IC = { apariencia: 'palette', reproduccion: 'play_circle', mejoras: 'hd', subtitulos: 'subtitles', fuentes: 'source', cuentas: 'account_circle', metadatos: 'dataset', link: 'devices', transferencias: 'swap_vert', lectores: 'menu_book', notificaciones: 'notifications', sistema: 'computer', datos: 'storage', acerca: 'info' };
  const RATE_STOPS = [0, 128, 256, 512, 1024, 2048, 5120, 10240, 25600, 51200];
  const fmtRate = (k) => (!k ? 'Sin límite' : k < 1024 ? k + ' KiB/s' : (k / 1024).toLocaleString('es') + ' MiB/s');
  const KEYNAME = { ' ': 'Espacio', ArrowLeft: '←', ArrowRight: '→', ArrowUp: '↑', ArrowDown: '↓', Enter: 'Intro', '/': '?', '.': '.', ',': ',' };
  const RESERVED = ['Escape', 'Tab', 'Meta', 'ContextMenu', 'Home'];
  const KEYS_BASE = [['play', 'Reproducir o pausar', ' '], ['back', 'Atrás 5 s', 'ArrowLeft'], ['fwd', 'Adelante 5 s', 'ArrowRight'], ['full', 'Pantalla completa', 'f'], ['mute', 'Silenciar', 'm'], ['pip', 'Imagen en imagen', 'p'], ['prev', 'Episodio anterior', 'j'], ['next', 'Episodio siguiente', 'k'], ['volup', 'Subir volumen', 'ArrowUp'], ['voldn', 'Bajar volumen', 'ArrowDown'], ['shot', 'Captura de pantalla', 's'], ['subs', 'Siguiente pista de subtítulos', 'c'], ['help', 'Ver atajos', '/']];
  const KEYS_DESK = [['faster', 'Más rápido', '.'], ['slower', 'Más lento', ','], ['audio', 'Siguiente pista de audio', 'v'], ['subE', 'Subtítulos 0,1 s antes', 'z'], ['subL', 'Subtítulos 0,1 s después', 'x'], ['skip', 'Saltar intro o ending', 'Enter'], ['loop', 'Repetir en bucle', 'l']];
  const keyLabel = (k) => KEYNAME[k] || (k.length === 1 ? k.toUpperCase() : k);

  function vDefaults(ctx) {
    const pals = (NJ.directions[ctx.dir] || {}).palettes || [];
    return {
      tema: { light: 'claro', dark: 'oscuro', oled: 'oled' }[ctx.mode] || 'oscuro', paleta: pals[0] ? pals[0].id : 'base', dyn: false, contraste: 'estandar', idioma: 'sistema', preset: 'nijiyome', inicio: 'destacado', rotar: true, segs: 9, saludo: false, pagina: 'estandar', encabezado: 'factsep', tarjeta: 'detallada', tsize: 'm', densidad: 'comoda', anim: { full: 'completo', calm: 'calmo', perf: 'rendimiento' }[ctx.motion] || 'completo', episodios: 'lista', relacionados: 'grafo', calvista: 'semana', menu: 'nombres',
      motor: B.isA(ctx) ? 'libmpv' : 'libmpv', extPlayer: 'preguntar', mpvPath: '', mpvEnh: false, uosc: true, estilo: 'cine', autoNext: true, countdown: 10, skipIntro: true, seekStep: '5', speed: '1', sleep: 'off', stableVol: false, bgPlay: true, certs: false,
      tDir: '/home/octavio/Videos/Anime', wDir: '/home/octavio/Descargas/Nijiyome', down: 6, up: 3, sched: false, slots: [0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0], schedDown: 4, schedUp: 2, port: 51413, portRandom: false, upnp: !B.isA(ctx), maxConn: 50, dht: true, pex: true, lsd: true, wifiOnly: true, upMobile: false, debrid: true, preferTorbox: true, dlTorbox: false, cacheAuto: false, cacheGb: 10, preload: !B.isA(ctx), after: 'borrar', bg: false, bgAck: false, lowBat: true, simul: '2', wSpeed: 'sin', wStore: 'sin', checkNew: true, dlQ: 'equilibrada', freeWatched: false, prefServer: 'cualquiera', prefAudio: 'cualquiera',
      tray: true, closeTray: false, autostart: false, startMin: false, discord: false, nativeBar: false, magnet: true, mediaProxy: true,
    };
  }
  const DRAFT0 = { proxy: { on: false, type: 'socks5', host: '127.0.0.1', port: 1080, user: '', pass: '' }, trackers: { text: 'udp://tracker.opentrackr.org:1337/announce\nudp://open.stealth.si:80/announce' }, linksrv: { name: 'octavio-pc', port: 47321, folder: '/home/octavio/Videos/Anime' }, tr: { url: '', model: '', region: '', key: '' } };

  /* -------- primitivas de fila -------- */
  function mk(ctx, st, cat) {
    const A = B.isA(ctx); const V = st.v;
    const idx = st._collect;
    const reg = (key, label, hint) => { if (idx) idx.push({ cat, key, label, hint: hint || '' }); };
    const rid = (key) => B.pid(ctx, 'row-' + key);
    const saved = (key) => `<span class="nj-b-saved${st._saved === key ? ' is-on' : ''}" aria-hidden="${st._saved !== key}">${ic('check', { size: 16 })}Guardado</span>`;
    const R = {
      row(key, label, hint, ctl, o = {}) { reg(key, label, hint); return `<div class="nj-b-row${o.stack ? ' is-stack' : ''}${st.flash === key ? ' is-flash' : ''}${o.disabled ? ' is-disabled' : ''}" id="${rid(key)}" data-key="${key}"><div class="nj-b-row-text"><span class="nj-b-row-label">${esc(label)}${o.badge ? `<span class="nj-badge nj-b-rowbadge">${esc(o.badge)}</span>` : ''}${saved(key)}</span>${hint ? `<span class="nj-b-row-hint">${esc(hint)}</span>` : ''}</div><div class="nj-b-row-ctl">${ctl}</div></div>`; },
      tog(key, label, hint, o = {}) { reg(key, label, hint); return `<div class="nj-b-row is-toggle${st.flash === key ? ' is-flash' : ''}${o.disabled ? ' is-disabled' : ''}" id="${rid(key)}" data-key="${key}">${h.toggle(B.pid(ctx, 't-' + key), label, !!V[key], { desc: hint })}${o.badge ? `<span class="nj-badge nj-b-rowbadge">${esc(o.badge)}</span>` : ''}${saved(key)}</div>`.replace('<input', o.disabled ? '<input disabled' : '<input'); },
      seg(key, label, opts, hint, o = {}) { return R.row(key, label, hint, `<div data-bset="v.${key}"${o.disabled ? ' class="is-disabled" inert' : ''}>${h.seg(label, opts.map((x) => (typeof x === 'string' ? { id: B.fold(x).replace(/[^a-z0-9]/g, ''), label: x } : x)), V[key])}</div>`, Object.assign({ stack: opts.length > 3 }, o)); },
      chips(key, label, opts, hint, o = {}) { return R.row(key, label, hint, `<div data-bset="v.${key}"${o.multi ? ' data-bmode="multi"' : ''}>${h.chips(label, opts.map((x) => (typeof x === 'string' ? { id: B.fold(x).replace(/[^a-z0-9]/g, ''), label: x } : x)), [].concat(V[key]), { multi: o.multi })}</div>`, Object.assign({ stack: true }, o)); },
      sld(key, label, min, max, step, unit, hint, o = {}) { reg(key, label, hint); return `<div class="nj-b-row is-stack${st.flash === key ? ' is-flash' : ''}" id="${rid(key)}" data-key="${key}">${h.slider(B.pid(ctx, 's-' + key), label, { min, max, step, value: V[key], unit })}${hint ? `<span class="nj-b-row-hint">${esc(hint)}</span>` : ''}${saved(key)}</div>`; },
      stops(key, label, stops, f, hint) { reg(key, label, hint); const i = V[key]; return `<div class="nj-b-row is-stack${st.flash === key ? ' is-flash' : ''}" id="${rid(key)}" data-key="${key}"><div class="nj-slider"><div class="nj-slider-head"><label for="${B.pid(ctx, 'r-' + key)}">${esc(label)}</label><b class="nj-b-tnum" data-stopout="${key}">${esc(f(stops[i]))}</b></div><input type="range" id="${B.pid(ctx, 'r-' + key)}" min="0" max="${stops.length - 1}" step="1" value="${i}" data-stops="${key}" aria-valuetext="${esc(f(stops[i]))}"><div class="nj-b-ticks nj-b-tnum" aria-hidden="true"><span>${esc(f(stops[0]))}</span><span>${esc(f(stops[stops.length - 1]))}</span></div></div>${hint ? `<span class="nj-b-row-hint">${esc(hint)}</span>` : ''}${saved(key)}</div>`; },
      act(key, label, hint, btn, o = {}) { return R.row(key, label, hint, h.btn(btn, { kind: o.kind || 'outline', icon: o.icon, attrs: { 'data-act': o.act || 'specAct', 'data-arg': key } }), o); },
      info(key, label, value, o = {}) { return R.row(key, label, o.hint, `<span class="nj-b-infoval${o.mono ? ' is-mono' : ''}${o.ok ? ' is-ok' : ''}">${o.ok ? ic('check_circle', { size: 18 }) : ''}${esc(value)}</span>`, o); },
      folder(key, label, hint, o = {}) { return R.row(key, label, hint, `<div class="nj-b-path"><span class="nj-b-pathval" title="${esc(V[key])}">${ic('folder', { size: 18 })}<span>${esc(V[key])}</span></span><div class="nj-cluster">${h.btn('Cambiar carpeta…', { kind: 'tonal', attrs: { 'data-act': 'folderPick', 'data-arg': key } })}${A ? '' : h.btn('Abrir', { kind: 'text', icon: 'open_in_new', attrs: { 'data-act': 'specAct', 'data-arg': 'open-' + key } })}</div></div>`, { stack: true }); },
      group(title, body, o = {}) { return `<section class="nj-b-group${o.commit ? ' is-commit' : ''}"${o.id ? ` id="${B.pid(ctx, 'grp-' + o.id)}"` : ''}><h3 class="nj-b-group-title">${o.icon ? ic(o.icon, { size: 20 }) : ''}${esc(title)}${o.commit ? `<span class="nj-b-commit-tag">${ic('pending_actions', { size: 16 })}Se aplica con botón</span>` : ''}</h3>${o.hint ? `<p class="nj-b-group-hint">${esc(o.hint)}</p>` : ''}${body}</section>`; },
    };
    return R;
  }
  function commitFoot(ctx, st, block, o = {}) {
    const dirty = JSON.stringify(st.draft[block]) !== JSON.stringify(st.applied[block]);
    const res = st.test && st.test.block === block ? `<span class="nj-b-testres is-${st.test.ok ? 'ok' : st.test.ok === false ? 'err' : 'run'}" role="status">${st.test.ok == null ? '<span class="nj-b-spin" aria-hidden="true"></span>Probando…' : ic(st.test.ok ? 'check_circle' : 'error', { size: 18 }) + esc(st.test.text)}</span>` : '';
    return `<div class="nj-b-commitfoot${dirty ? ' is-dirty' : ''}">${dirty ? `<span class="nj-b-dirty">${ic('edit_note', { size: 18 })}Cambios sin aplicar</span>` : `<span class="nj-meta">${ic('check', { size: 16 })}Aplicado</span>`}${res}<span class="nj-b-grow"></span>${o.test ? h.btn('Probar', { kind: 'text', attrs: { 'data-act': 'commitTest', 'data-arg': block } }) : ''}${dirty ? h.btn('Descartar', { kind: 'text', attrs: { 'data-act': 'commitDiscard', 'data-arg': block } }) + h.btn(o.apply || 'Aplicar', { kind: 'filled', attrs: { 'data-act': 'commitApply', 'data-arg': block } }) : ''}</div>`;
  }
  const field = (ctx, id, label, value, o = {}) => `<label class="nj-b-field"><span class="nj-b-flabel">${esc(label)}</span><span class="nj-field${o.mono ? ' is-mono' : ''}">${o.icon ? ic(o.icon, { size: 18 }) : ''}<input id="${B.pid(ctx, 'f-' + id)}" data-draft="${id}" value="${esc(value)}" ${o.type ? `type="${o.type}"` : ''} placeholder="${esc(o.ph || '')}" autocomplete="off"></span></label>`;

  /* -------- Apariencia -------- */
  function miniHome(kind) {
    return `<span class="nj-b-mini" aria-hidden="true">${kind === 'destacado' ? '<i class="m-hero"></i><i class="m-row"></i>' : kind === 'carrusel' ? '<i class="m-car"></i><i class="m-car"></i><i class="m-car"></i><i class="m-row"></i>' : '<i class="m-row"></i><i class="m-row"></i><i class="m-row"></i>'}</span>`;
  }
  function miniHeader(kind) { return `<span class="nj-b-mini is-head" aria-hidden="true">${{ linea: '<i class="m-line"></i><i class="m-txt"></i><i class="m-txt"></i>', ficha: '<i class="m-pst"></i><i class="m-txt"></i><i class="m-txt"></i>', episodio: '<i class="m-line"></i><i class="m-ep"></i>', factsep: '<i class="m-pst"></i><i class="m-txt"></i><i class="m-ep"></i>' }[kind]}</span>`; }
  function miniCard(kind) { return `<span class="nj-b-mini is-card" aria-hidden="true"><i class="m-pst"></i>${kind === 'poster' ? '' : '<i class="m-t1"></i>'}${kind === 'detallada' ? '<i class="m-t2"></i><i class="m-chip"></i>' : ''}</span>`; }
  function visualPick(key, label, opts, cur, thumb, o = {}) {
    return `<div class="nj-b-vpick${o.cols ? ' is-' + o.cols : ''}" role="radiogroup" aria-label="${esc(label)}">${opts.map(([id, l, hint]) => `<button type="button" role="radio" class="nj-b-vopt" aria-checked="${cur === id}" data-act="vset" data-key="${key}" data-arg="${id}">${thumb(id)}<span class="nj-b-vopt-l">${cur === id ? ic('check_circle', { size: 18, fill: true }) : ''}${esc(l)}</span>${hint ? `<span class="nj-b-vopt-h">${esc(hint)}</span>` : ''}</button>`).join('')}</div>`;
  }
  function preview(ctx, st) {
    const V = st.v; const mode = V.tema === 'sistema' ? ctx.mode : { claro: 'light', oscuro: 'dark', oled: 'oled' }[V.tema];
    const mot = { completo: 'full', calmo: 'calm', rendimiento: 'perf' }[V.anim];
    const posters = ['aonohako', 'tempal', 'koori', 'chitose'].map((id) => D.byId[id]);
    const cards = posters.slice(0, V.tsize === 's' ? 4 : V.tsize === 'xl' ? 2 : 3).map((t) => `<span class="nj-b-pv-card is-${V.tarjeta}"><img src="${esc(t.poster)}" alt="" width="60" height="90">${V.tarjeta === 'poster' ? '' : `<b>${esc(t.short)}</b>`}${V.tarjeta === 'detallada' ? `<span>Ep. ${t.progress + 1} · ${t.format}</span>` : ''}</span>`).join('');
    const hero = V.inicio === 'filas' ? '' : V.inicio === 'carrusel' ? `<div class="nj-b-pv-car"><span style="background-image:url(img/hero-sbr.webp)"></span><span style="background-image:url(img/hero-onepiece.webp)"></span><span style="background-image:url(img/frame-laidoff.webp)"></span></div>` : `<div class="nj-b-pv-hero" style="background-image:url(img/frame-laidoff.webp)"><b>Laid-Off Mage</b><span class="nj-b-pv-acts"><span class="nj-b-pv-btn">${ic('play_arrow', { size: 16, fill: true })}Continuar · Ep 4</span><span class="nj-badge">1080p</span></span></div>`;
    return `<figure class="nj-b-prevwrap" aria-label="Vista previa en vivo"><div class="nj-app nj-b-prev is-${V.densidad} is-ct-${V.contraste}" data-dir="${esc(ctx.dir)}" data-mode="${mode}" data-palette="${esc(V.paleta)}" data-motion="${mot}" data-platform="${ctx.platform}" data-card="${esc(ctx.card)}"><div class="nj-b-pv-rail"><i class="is-on">${ic('home', { size: 16, fill: true })}</i><i>${ic('explore', { size: 16 })}</i><i>${ic('calendar_month', { size: 16 })}</i>${V.menu === 'nombres' ? '<span>Inicio</span>' : ''}</div><div class="nj-b-pv-main">${V.saludo ? '<b class="nj-b-pv-greet">Buenas noches</b>' : ''}${hero}<span class="nj-b-pv-rowt">Esta noche</span><div class="nj-b-pv-row is-${V.tsize}">${cards}</div><span class="nj-b-pv-status"><span class="nj-status" data-status="watching">Viendo</span><span class="nj-meta">Texto secundario</span></span></div></div><figcaption class="nj-meta">Vista previa en vivo · ${esc({ claro: 'Claro', oscuro: 'Oscuro', oled: 'OLED', sistema: 'Sistema' }[V.tema])} · animaciones ${esc(V.anim)}</figcaption></figure>`;
  }
  function catApariencia(ctx, st, R) {
    const V = st.v; const A = B.isA(ctx); const pals = (NJ.directions[ctx.dir] || {}).palettes || [];
    const themeTile = (id, l) => `<button type="button" role="radio" class="nj-b-theme" aria-checked="${V.tema === id}" data-act="vset" data-key="tema" data-arg="${id}"><span class="nj-b-theme-sw">${id === 'sistema' ? `<span class="nj-app nj-b-sw" data-dir="${esc(ctx.dir)}" data-mode="light" data-palette="${esc(V.paleta)}"><i></i></span><span class="nj-app nj-b-sw" data-dir="${esc(ctx.dir)}" data-mode="dark" data-palette="${esc(V.paleta)}"><i></i></span>` : `<span class="nj-app nj-b-sw" data-dir="${esc(ctx.dir)}" data-mode="${{ claro: 'light', oscuro: 'dark', oled: 'oled' }[id]}" data-palette="${esc(V.paleta)}"><i></i></span>`}</span><span class="nj-b-vopt-l">${V.tema === id ? ic('check_circle', { size: 18, fill: true }) : ''}${l}</span></button>`;
    const tema = R.row('tema', 'Tema', 'OLED usa negro puro en fondos y superficies.', `<div class="nj-b-vpick is-4" role="radiogroup" aria-label="Tema">${[['sistema', 'Sistema'], ['claro', 'Claro'], ['oscuro', 'Oscuro'], ['oled', 'OLED']].map(([id, l]) => themeTile(id, l)).join('')}</div>`, { stack: true });
    const paleta = R.row('paleta', 'Paleta', pals.length > 1 ? 'Pocas paletas curadas por la dirección visual; cada una trae claro, oscuro y OLED.' : 'Esta dirección trae una sola paleta curada.', `<div class="nj-b-swatches${V.dyn ? ' is-off' : ''}" role="radiogroup" aria-label="Paleta">${pals.map((p) => `<button type="button" role="radio" class="nj-b-swatch" aria-checked="${V.paleta === p.id && !V.dyn}" data-act="vset" data-key="paleta" data-arg="${esc(p.id)}" style="--sw:${esc(p.swatch)}"${V.dyn ? ' disabled' : ''}><i aria-hidden="true">${V.paleta === p.id && !V.dyn ? ic('check', { size: 18 }) : ''}</i><span>${esc(p.name)}</span></button>`).join('')}</div>`, { stack: true });
    return R.group('Tema y color', `${tema}${paleta}${A ? R.tog('dyn', 'Color del fondo de pantalla', 'Android 12 o superior. Mientras esté activo no se usa la paleta ni el contraste.') : ''}${R.seg('contraste', 'Contraste', [{ id: 'estandar', label: 'Estándar' }, { id: 'medio', label: 'Medio' }, { id: 'alto', label: 'Alto' }], V.dyn ? 'No disponible con el color del fondo de pantalla.' : 'Alto oscurece el texto secundario y los bordes.', { disabled: V.dyn })}${R.seg('idioma', 'Idioma', [{ id: 'sistema', label: 'Sistema' }, { id: 'es', label: 'Español' }, { id: 'en', label: 'English' }], '')}`, { icon: 'contrast' })
      + R.group('Estilo general', R.row('preset', 'Estilo predefinido', 'Cambia todas las opciones de abajo de una vez; luego puedes ajustar cada una.', visualPick('preset', 'Estilo predefinido', [['nijiyome', 'Nijiyome', 'Banner destacado, tarjetas con detalle y animación viva'], ['cine', 'Cine editorial', 'Arte a sangre, título grande y menú solo con iconos'], ['denso', 'Denso', 'Filas primero, tarjetas compactas y movimiento sutil'], ['calmo', 'Calmo', 'Carrusel sin giro automático y movimiento sutil']], V.preset, () => '', { cols: A ? 2 : 4 }), { stack: true }) + `<p class="nj-b-presetstate nj-meta">${st.custom ? ic('tune', { size: 16 }) + 'Personalizado: tus opciones ya no coinciden con un estilo' : ic('check', { size: 16 }) + 'Estilo: ' + esc({ nijiyome: 'Nijiyome', cine: 'Cine editorial', denso: 'Denso', calmo: 'Calmo' }[V.preset])}</p>`, { icon: 'style' })
      + R.group('Inicio', `${R.row('inicio', 'Qué muestra Inicio arriba', '', visualPick('inicio', 'Inicio', [['destacado', 'Destacado'], ['carrusel', 'Carrusel'], ['filas', 'Solo filas']], V.inicio, miniHome, { cols: 3 }), { stack: true })}${V.inicio !== 'filas' ? R.tog('rotar', 'Rotar el banner', 'Pasa al siguiente título solo.') : ''}${V.inicio !== 'filas' && V.rotar ? R.sld('segs', 'Segundos por título', 4, 30, 1, ' s', '') : ''}${R.tog('saludo', 'Mostrar el saludo', 'Buenos días, buenas tardes o buenas noches arriba de Inicio.')}`, { icon: 'home' })
      + R.group('Tarjetas y listas', `${R.row('tarjeta', 'Estilo de tarjeta', '', visualPick('tarjeta', 'Estilo de tarjeta', [['clasica', 'Clásica'], ['detallada', 'Detallada'], ['poster', 'Solo póster']], V.tarjeta, miniCard, { cols: 3 }), { stack: true })}${R.seg('tsize', 'Tamaño de tarjeta', [{ id: 's', label: 'Chica' }, { id: 'm', label: 'Mediana' }, { id: 'l', label: 'Grande' }, { id: 'xl', label: 'Muy grande' }], 'Grande y Muy grande ponen el póster al lado del título.')}${R.seg('densidad', 'Densidad', [{ id: 'comoda', label: 'Cómoda' }, { id: 'compacta', label: 'Compacta' }], '')}`, { icon: 'view_module' })
      + R.group('Ficha del título', `${R.seg('pagina', 'Estilo de página', [{ id: 'estandar', label: 'Estándar' }, { id: 'editorial', label: 'Editorial' }], 'Cómo se dibuja la ficha y el banner de Inicio.')}${R.row('encabezado', 'Encabezado', '', visualPick('encabezado', 'Encabezado', [['linea', 'Una línea'], ['ficha', 'Ficha técnica'], ['episodio', 'Próximo episodio'], ['factsep', 'Datos y episodio']], V.encabezado, miniHeader, { cols: 2 }), { stack: true })}${R.seg('episodios', 'Episodios', [{ id: 'tarjetas', label: 'Tarjetas' }, { id: 'lista', label: 'Lista' }, { id: 'compacta', label: 'Compacta' }], '')}${R.seg('relacionados', 'Títulos relacionados', [{ id: 'grafo', label: 'Grafo' }, { id: 'lista', label: 'Lista' }], '')}`, { icon: 'article' })
      + R.group('Navegación y movimiento', `${R.seg('anim', 'Animaciones', [{ id: 'completo', label: 'Completo' }, { id: 'calmo', label: 'Calmo' }, { id: 'rendimiento', label: 'Rendimiento' }], { completo: 'Transiciones y el movimiento propio de la dirección.', calmo: 'Solo fundidos cortos, sin desplazamientos. Equivale a reducir movimiento.', rendimiento: 'Sin sombras grandes ni desenfoques, una animación a la vez. Para equipos de gama baja.' }[V.anim])}${R.seg('calvista', 'Vista del calendario', [{ id: 'tablero', label: 'Tablero' }, { id: 'semana', label: 'Semana' }, { id: 'agenda', label: 'Agenda' }, { id: 'mes', label: 'Mes' }], '')}${A ? '' : R.seg('menu', 'Menú lateral', [{ id: 'nombres', label: 'Con nombres' }, { id: 'iconos', label: 'Solo iconos' }], 'Ctrl B lo alterna sin entrar aquí.')}`, { icon: 'animation' });
  }

  /* -------- Reproducción -------- */
  function catReproduccion(ctx, st, R) {
    const V = st.v; const A = B.isA(ctx);
    const motores = A ? [['media3', 'Media3', 'El de Android. Menos batería, sin escalado.'], ['libmpv', 'libmpv', 'Escalado, ASS completo y mejoras de video.'], ['externo', 'Externo', 'mpv-android o VLC.']] : [['libmpv', 'libmpv integrado', 'Escalado, ASS completo y mejoras de video.'], ['externo', 'mpv externo', 'Tu mpv del sistema, con lsfg-vk si lo tienes.']];
    let ext = '';
    if (V.motor === 'externo' && A) ext = R.seg('extPlayer', 'Reproductor externo', [{ id: 'preguntar', label: 'Preguntar' }, { id: 'mpv', label: 'mpv-android' }, { id: 'vlc', label: 'VLC · no instalado' }], 'Si no hay ninguno instalado, se ofrece Media3.');
    if (V.motor === 'externo' && !A) ext = R.row('mpvPath', 'Ejecutable de mpv', 'Encontrado: mpv 0.39.0 en /usr/bin/mpv', `<div class="nj-b-path"><span class="nj-b-pathval">${ic('terminal', { size: 18 })}<span>${esc(V.mpvPath || 'El primero en el PATH')}</span></span>${h.btn('Elegir ejecutable…', { kind: 'tonal', attrs: { 'data-act': 'specAct', 'data-arg': 'mpvPick' } })}</div>`, { stack: true }) + R.tog('mpvEnh', 'Modo mejorado (mpv-anime-build)', 'Descarga una compilación con shaders de anime verificada por su firma.') + (V.mpvEnh ? R.tog('uosc', 'Interfaz de la compilación (uosc)', '') : '');
    return R.group('Motor', `${R.row('motor', 'Motor de reproducción', '', `<div class="nj-b-vpick is-${motores.length}" role="radiogroup" aria-label="Motor">${motores.map(([id, l, hh]) => `<button type="button" role="radio" class="nj-b-vopt is-text" aria-checked="${V.motor === id}" data-act="vset" data-key="motor" data-arg="${id}"><span class="nj-b-vopt-l">${V.motor === id ? ic('check_circle', { size: 18, fill: true }) : ic('radio_button_unchecked', { size: 18 })}${l}</span><span class="nj-b-vopt-h">${hh}</span></button>`).join('')}</div>`, { stack: true })}${ext}${A && V.motor === 'libmpv' ? R.tog('certs', 'Verificar certificados', 'Rechaza fuentes con HTTPS inválido.') : ''}`, { icon: 'play_circle' })
      + R.group('Controles', `${A ? R.seg('estilo', 'Estilo del reproductor', [{ id: 'cine', label: 'Cine' }, { id: 'minimal', label: 'Minimalista' }], '') : ''}${R.chips('speed', 'Velocidad por defecto', [{ id: '0.75', label: '0,75×' }, { id: '1', label: '1×' }, { id: '1.25', label: '1,25×' }, { id: '1.5', label: '1,5×' }, { id: '2', label: '2×' }], 'Se puede cambiar en cada episodio desde el reproductor.')}${R.chips('sleep', 'Temporizador de apagado', [{ id: 'off', label: 'Apagado' }, { id: '15', label: '15 min' }, { id: '30', label: '30 min' }, { id: '60', label: '60 min' }, { id: 'ep', label: 'Al terminar el episodio' }], 'Pausa y apaga la pantalla del reproductor.')}${R.tog('stableVol', 'Volumen estable', 'Empareja escenas fuertes y suaves.')}${A ? R.tog('bgPlay', 'Seguir sonando en segundo plano', 'Con controles multimedia al salir de la app.') : ''}`, { icon: 'tune' })
      + R.group('Saltos', `${R.tog('skipIntro', 'Saltar intro y ending', 'Usa las marcas de AniSkip cuando existen.')}${R.tog('autoNext', 'Reproducir el siguiente episodio', '')}${V.autoNext ? R.sld('countdown', 'Cuenta regresiva antes del siguiente', 3, 30, 1, ' s', '') : ''}${R.seg('seekStep', 'Salto con las flechas', [{ id: '5', label: '5 s' }, { id: '10', label: '10 s' }, { id: '15', label: '15 s' }, { id: '30', label: '30 s' }], '')}`, { icon: 'fast_forward' })
      + R.group('Atajos de teclado', keyList(ctx, st, R), { icon: 'keyboard', hint: A ? 'Para un teclado físico o un control remoto. Toca una acción para cambiar su tecla.' : 'Haz clic en una acción y pulsa la tecla nueva.' })
      + (A ? '' : R.group('Navegador para extensiones', `${R.info('browser', 'Navegador oculto', 'Chromium 131 en /usr/bin/chromium', { ok: true, hint: 'Lo usan las fuentes embebidas. Ver Fuentes de episodios.' })}${R.act('browserTest', 'Comprobar que funciona', 'Abre una página de prueba sin mostrar ventana.', 'Probar', { icon: 'science' })}`, { icon: 'public' }));
  }
  function keyList(ctx, st, R) {
    const A = B.isA(ctx); const all = A ? KEYS_BASE : KEYS_BASE.concat(KEYS_DESK);
    const keyOf = (id, d) => st.keys[id] || d; const used = {}; all.forEach(([id, , d]) => { const k = keyOf(id, d); used[k] = (used[k] || 0) + 1; });
    return `<ul class="nj-b-keys" id="${B.pid(ctx, 'row-atajos')}">${all.map(([id, l, d]) => { const k = keyOf(id, d); const clash = used[k] > 1; return `<li><button type="button" class="nj-b-keyrow${clash ? ' is-clash' : ''}" data-act="capture" data-arg="${id}"><span class="nj-grow">${esc(l)}${clash ? '<span class="nj-b-clash">Misma tecla que otra acción</span>' : ''}</span><span class="nj-kbd nj-b-key">${esc(keyLabel(k))}</span></button></li>`; }).join('')}</ul>${(st._collect && st._collect.push({ cat: 'reproduccion', key: 'atajos', label: 'Atajos de teclado', hint: 'Cambiar teclas del reproductor' }), '')}<div class="nj-cluster nj-b-keyfoot">${h.btn('Restaurar los predeterminados', { kind: 'text', attrs: { 'data-act': 'keysReset', disabled: !Object.keys(st.keys).length } })}</div>`;
  }
  function captureDialog(ctx, st) {
    const all = KEYS_BASE.concat(KEYS_DESK); const a = all.find((x) => x[0] === st.capture); if (!a) return '';
    const k = st.capKey; const reserved = k && RESERVED.includes(k);
    const clash = k && !reserved ? all.filter((x) => x[0] !== a[0] && (st.keys[x[0]] || x[2]) === k).map((x) => x[1]) : [];
    return `<div class="nj-overlay nj-b-overlay is-center" data-act="capClose"><div class="nj-dialog" role="dialog" aria-modal="true" aria-label="${esc(a[1])}" data-act="noop" tabindex="-1" id="${B.pid(ctx, 'capdlg')}"><h2 class="nj-h2">${esc(a[1])}</h2><p class="nj-meta">Pulsa la nueva tecla. Esc cancela.</p><div class="nj-b-capkey${k ? ' is-set' : ''}" aria-live="polite">${k ? esc(keyLabel(k)) : 'Esperando una tecla…'}</div>${reserved ? '<p class="nj-b-err">Esa tecla es del sistema.</p>' : ''}${clash.length ? `<p class="nj-b-warn">Ya la usa: ${esc(clash.join(', '))}. Puedes guardarla igual.</p>` : ''}<div class="nj-cluster nj-b-dlgacts">${h.btn('Cancelar', { kind: 'text', attrs: { 'data-act': 'capClose' } })}${h.btn('Guardar', { kind: 'filled', attrs: { 'data-act': 'capSave', disabled: !k || reserved } })}</div></div></div>`;
  }

  /* -------- Torrents y descargas -------- */
  function slotsStrip(ctx, st) {
    const V = st.v; const ranges = []; let s = null;
    for (let i = 0; i <= 24; i++) { const on = i < 24 && V.slots[i]; if (on && s == null) s = i; if (!on && s != null) { ranges.push([s, i]); s = null; } }
    if (ranges.length > 1 && ranges[0][0] === 0 && ranges[ranges.length - 1][1] === 24) { const f = ranges.shift(); ranges[ranges.length - 1][1] = f[1] + 24; }
    const hh = (n) => String(n % 24).padStart(2, '0') + ':00';
    const txt = ranges.length ? ranges.map((r) => `${hh(r[0])} a ${hh(r[1])}`).join(' y ') : 'Ninguna franja elegida';
    return `<div class="nj-b-slots" id="${B.pid(ctx, 'row-slots')}"><div class="nj-b-slots-head"><span class="nj-b-row-label">Franjas con límite</span><b class="nj-b-tnum" data-slottxt>${esc(txt)}</b></div><div class="nj-b-slotgrid" role="group" aria-label="Horas del día con límite. Arrastra para pintar varias.">${V.slots.map((v, i) => `<button type="button" class="nj-b-slot${v ? ' is-on' : ''}" data-slot="${i}" aria-pressed="${!!v}" aria-label="${hh(i)} a ${hh(i + 1)}"></button>`).join('')}</div><div class="nj-b-slotscale nj-b-tnum" aria-hidden="true"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></div><p class="nj-b-row-hint">Toca o arrastra sobre las horas. Una franja puede cruzar la medianoche. Fuera de ellas rigen los límites de arriba.</p></div>`;
  }
  function catTorrents(ctx, st, R) {
    const V = st.v; const A = B.isA(ctx);
    const pt = st.portTest;
    const portCtl = `<div class="nj-b-portrow">${V.portRandom ? '<span class="nj-b-infoval">Aleatorio en cada inicio</span>' : B.stepper(ctx, 'port', 'puerto', V.port, { min: 1024, max: 65535, step: 1 })}<button type="button" class="nj-chip" data-act="portRandom" aria-pressed="${V.portRandom}">${V.portRandom ? ic('check', { size: 18 }) : ''}Aleatorio</button>${h.btn(pt === 'run' ? 'Probando…' : 'Probar puerto', { kind: 'tonal', icon: 'network_check', attrs: { 'data-act': 'portTest', disabled: pt === 'run' } })}</div>${pt && pt !== 'run' ? `<p class="nj-b-testres is-${pt === 'ok' ? 'ok' : 'err'}" role="status">${ic(pt === 'ok' ? 'check_circle' : 'error', { size: 18 })}${pt === 'ok' ? `Abierto: otros pares pueden conectarse al ${V.port}.` : 'Cerrado: el router no deja pasar conexiones. Activa UPnP o abre el puerto a mano.'}</p>` : ''}`;
    const used = `<div class="nj-b-usage"><div class="nj-b-usebar" role="img" aria-label="Usado 182 GB de 1,4 TB"><i style="--w:.13"></i></div><span class="nj-meta">Usado 182 GB · libre 1,2 TB (cifras de ejemplo)</span></div>`;
    return R.group('Carpetas', `${R.folder('tDir', 'Torrents', 'Los torrents nuevos se descargan aquí; los ya agregados se quedan donde están.')}${used}${R.folder('wDir', 'Descargas web', 'Episodios de fuentes web y HLS.')}${A ? '' : R.row('libDirs', 'Biblioteca local', 'Los videos nuevos de estas carpetas entran solos a la biblioteca.', `<ul class="nj-b-dirlist"><li>${ic('folder', { size: 18 })}<span>/mnt/media/Anime</span>${h.iconBtn('close', 'Dejar de vigilar /mnt/media/Anime', { attrs: { 'data-act': 'specAct', 'data-arg': 'unwatch' } })}</li></ul>${h.btn('Agregar carpeta…', { kind: 'text', icon: 'create_new_folder', attrs: { 'data-act': 'folderPick', 'data-arg': 'libDir' } })}`, { stack: true })}${R.seg('after', 'Después de ver un episodio', [{ id: 'mantener', label: 'Mantener' }, { id: 'borrar', label: 'Borrar' }], 'Borrar libera el espacio al cerrar el reproductor.')}`, { icon: 'folder' })
      + R.group('Límites de velocidad', `${R.stops('down', 'Descarga', RATE_STOPS, fmtRate, 'Se aplica al momento.')}${R.stops('up', 'Subida', RATE_STOPS, fmtRate, '')}`, { icon: 'speed' })
      + R.group('Programación de ancho de banda', `${R.tog('sched', 'Usar otros límites en ciertas horas', 'Por ejemplo, 1 MiB/s de día y sin límite de noche.')}${V.sched ? slotsStrip(ctx, st) + R.stops('schedDown', 'Descarga dentro de las franjas', RATE_STOPS, fmtRate, '') + R.stops('schedUp', 'Subida dentro de las franjas', RATE_STOPS, fmtRate, '') : ''}`, { icon: 'schedule' })
      + R.group('Red', `${R.row('port', 'Puerto de escucha', 'Los otros pares se conectan a ti por aquí.', portCtl, { stack: true })}${R.tog('upnp', 'Abrir el puerto en el router (UPnP)', A ? 'Apagado por defecto en teléfonos.' : 'Más conexiones entrantes.')}${R.sld('maxConn', 'Conexiones por torrent', 1, 500, 1, '', 'Pares conectados a la vez en cada torrent.')}${A ? R.tog('wifiOnly', 'Solo con Wi-Fi', 'Sin descargas en segundo plano con datos móviles.') + R.tog('upMobile', 'Subir con datos móviles', 'Apagado: con datos se sube 1 KiB/s como máximo.') : ''}${R.tog('dht', 'DHT', 'Encuentra pares sin rastreadores; lo necesitan casi todos los magnet.')}${R.tog('pex', 'Intercambio de pares (PEX)', '')}${R.tog('lsd', 'Pares en tu red local (LSD)', '')}`, { icon: 'lan' })
      + R.group('Rastreadores propios', `<label class="nj-b-flabel" for="${B.pid(ctx, 'f-trackers.text')}">Uno por línea; se anuncian en todos los torrents</label><textarea id="${B.pid(ctx, 'f-trackers.text')}" class="nj-b-textarea is-mono" rows="4" data-draft="trackers.text">${esc(st.draft.trackers.text)}</textarea>${/ftp:|http:\/\/[^\n]*\s/.test(st.draft.trackers.text) || st.draft.trackers.text.split('\n').some((l) => l.trim() && !/^(udp|https?|wss):\/\//.test(l.trim())) ? `<p class="nj-b-warn">${ic('warning', { size: 16 })}Hay líneas que no empiezan con udp://, http://, https:// o wss://; se ignoran al aplicar.</p>` : ''}${(st._collect && st._collect.push({ cat: 'transferencias', key: 'grp-trackers', label: 'Rastreadores propios', hint: 'Lista de trackers' }), '')}${commitFoot(ctx, st, 'trackers', { apply: 'Guardar rastreadores' })}`, { icon: 'hub', commit: true, id: 'trackers' })
      + R.group('Streaming y debrid', `${R.tog('preload', 'Precargar el siguiente episodio', 'En un pack, empieza a bajar el siguiente al 80 %.')}${R.tog('cacheAuto', 'Liberar espacio solo', 'Borra los torrents vistos en streaming más viejos al pasar el límite.')}${V.cacheAuto ? R.sld('cacheGb', 'Límite de la caché', 1, 200, 1, ' GiB', '') : ''}${R.tog('debrid', 'Usar debrid', 'TorBox es el servicio probado; Real-Debrid, AllDebrid y Premiumize son experimentales.')}${R.tog('preferTorbox', 'Preferir TorBox si está en caché', '')}${R.tog('dlTorbox', 'Descargar por TorBox si está en caché', '')}${R.act('debridAcc', 'Cuentas de debrid', 'Vincular TorBox y los demás servicios.', 'Abrir en Transferencias', { kind: 'text', icon: 'open_in_new' })}`, { icon: 'cloud_sync' })
      + R.group('Segundo plano', `${V.bgAck ? R.tog('bg', 'Descargar en segundo plano', A ? 'Siguen bajando con la app cerrada, según la red y la batería.' : 'Siguen bajando mientras la app está abierta.') : R.row('bg', 'Descargar en segundo plano', 'Antes de activarlo, lee el aviso: con la app cerrada se sigue compartiendo.', h.btn('Leer el aviso y activar', { kind: 'tonal', attrs: { 'data-act': 'bgAck' } }))}${A ? R.tog('lowBat', 'Pausar con batería baja', 'Por debajo de 20 % sin cargador o con ahorro de batería.') : ''}`, { icon: 'downloading' })
      + R.group('Descargas web', `${R.seg('simul', 'Descargas a la vez', ['1', '2', '3', '4', '5'].map((x) => ({ id: x, label: x })), '')}${A ? R.tog('wifiOnly', 'Solo con Wi-Fi', 'Las descargas esperan una red sin límite de datos.') : R.chips('wSpeed', 'Velocidad total', [{ id: 'sin', label: 'Sin límite' }, { id: '512', label: '512 KiB/s' }, { id: '1', label: '1 MiB/s' }, { id: '2', label: '2 MiB/s' }, { id: '5', label: '5 MiB/s' }, { id: '10', label: '10 MiB/s' }], '')}${R.chips('wStore', 'Límite de almacenamiento', [{ id: 'sin', label: 'Sin límite' }, { id: '5', label: '5 GB' }, { id: '10', label: '10 GB' }, { id: '25', label: '25 GB' }, { id: '50', label: '50 GB' }, { id: '100', label: '100 GB' }], 'Las descargas automáticas paran al llegar. En uso: 38 GB.')}${R.tog('checkNew', 'Buscar episodios nuevos', 'Cada 30 minutos, para las reglas de descarga automática.')}${R.seg('dlQ', 'Calidad de descarga', [{ id: 'mejor', label: 'La mejor' }, { id: 'equilibrada', label: 'Hasta 1080p' }, { id: 'ahorro', label: 'Hasta 720p' }], 'La usan Descargar temporada y las reglas automáticas.')}${R.tog('freeWatched', 'Borrar episodios vistos', '')}${R.chips('prefServer', 'Servidor preferido', [{ id: 'cualquiera', label: 'Cualquiera' }, { id: 'nebula', label: 'Nebula' }, { id: 'mirror', label: 'Mirror B' }, { id: 'kitsune', label: 'Kitsune Raws' }], 'Las fuentes con ese nombre van primero. Salen de tus extensiones.')}${R.chips('prefAudio', 'Idioma de audio preferido', [{ id: 'cualquiera', label: 'Cualquiera' }, { id: 'ja', label: 'Japonés' }, { id: 'es', label: 'Español' }, { id: 'en', label: 'Inglés' }], '')}`, { icon: 'download' });
  }

  /* -------- Sistema (solo escritorio) -------- */
  function catSistema(ctx, st, R) {
    const d = st.draft.proxy;
    return R.group('Ventana y arranque', `${R.tog('tray', 'Icono en la bandeja', 'En GNOME hace falta la extensión AppIndicator.')}${R.tog('closeTray', 'Cerrar a la bandeja', 'La ventana se oculta y las descargas siguen.', { disabled: !st.v.tray })}${R.tog('autostart', 'Iniciar con el sistema', 'Solo en las compilaciones empaquetadas.')}${R.tog('startMin', 'Iniciar minimizado en la bandeja', '', { disabled: !st.v.autostart || !st.v.tray })}${R.tog('nativeBar', 'Barra de título del sistema', 'En lugar de la barra propia de Nijiyome.', { badge: 'Al reiniciar' })}`, { icon: 'desktop_windows' })
      + R.group('Integraciones', `${R.tog('discord', 'Mostrar lo que ves en Discord', 'Rich Presence con el título y el episodio.')}${R.tog('magnet', 'Abrir enlaces magnet y archivos .torrent con Nijiyome', 'Lee el estado real del sistema operativo.')}${R.info('keyring', 'Contraseñas y tokens', 'En el llavero del sistema', { ok: true })}`, { icon: 'extension' })
      + R.group('Proxy de salida', `${`<div class="nj-b-row is-toggle" id="${B.pid(ctx, 'row-proxy')}" data-key="proxy">${h.toggle(B.pid(ctx, 'd-proxy.on'), 'Usar un proxy', d.on, { desc: 'Los cambios se aplican a la siguiente conexión.' })}</div>`}${(st._collect && st._collect.push({ cat: 'sistema', key: 'proxy', label: 'Proxy de salida', hint: 'SOCKS5, HTTP, FlareSolverr' }), '')}${d.on ? `<div class="nj-b-row is-stack"><div data-bset="draft.proxy.type">${h.seg('Tipo', [{ id: 'socks5', label: 'SOCKS5' }, { id: 'http', label: 'HTTP' }], d.type)}</div><div class="nj-b-fieldgrid">${field(ctx, 'proxy.host', 'Servidor', d.host, { ph: '127.0.0.1', mono: true })}<div class="nj-b-field"><span class="nj-b-flabel">Puerto</span>${B.stepper(ctx, 'proxy.port', 'puerto del proxy', d.port, { min: 1, max: 65535 })}</div>${field(ctx, 'proxy.user', 'Usuario (opcional)', d.user)}${field(ctx, 'proxy.pass', 'Contraseña (opcional)', d.pass, { type: 'password' })}</div><p class="nj-b-row-hint">${d.type === 'http' ? 'Con HTTP los torrents van directo; solo SOCKS5 cubre los torrents.' : 'SOCKS5 cubre también los torrents.'}</p></div>` : ''}${commitFoot(ctx, st, 'proxy', { test: d.on })}`, { icon: 'vpn_lock', commit: true, id: 'proxy', hint: 'Este bloque reinicia las conexiones, por eso pide Aplicar.' })
      + R.group('Otros servicios de red', `${R.row('flare', 'FlareSolverr', 'Vacío lo desactiva. Lo usan extensiones con protección anti bots.', `<span class="nj-field is-mono">${ic('link', { size: 18 })}<input id="${B.pid(ctx, 'f-flare')}" placeholder="http://127.0.0.1:8191" aria-label="Dirección de FlareSolverr"></span>`, { stack: true })}${R.tog('mediaProxy', 'Proxy local de medios', 'Lo usa el reproductor para fuentes con cabeceras especiales.')}`, { icon: 'dns' });
  }

  NJ.B.settings = { mk, commitFoot, field, vDefaults, DRAFT0, catApariencia, catReproduccion, catTorrents, catSistema, captureDialog, preview, keyLabel, RESERVED, KEYS_BASE, KEYS_DESK, CAT_IC, RATE_STOPS, fmtRate };
})();

(function () {
  const NJ = window.NJ; if (!NJ || !NJ.B || !NJ.B.settings) return;
  const D = NJ.data, h = NJ.h, esc = h.esc, B = NJ.B, ic = B.ic, S = NJ.B.settings;
  const MORE = { upscale: 'aa', uq: 'equilibrado', deband: true, autoadj: true, dec: 'auto', smooth: false, lsfg: false, lsfgMult: 'auto', lsfgPerf: false, fgUse: false, fgMult: 'auto', fgProf: 'equilibrado', fgHz: true, fgUntested: false, fgStats: false,
    subLang: 'es', audLang: 'ja', sub2: 'ninguno', trEngine: 'apagada', trMode: 'vivo', trTarget: 'es', srcMode: 'auto', srcType: 'cualquiera', minQ: '720p', embed: true, embedWait: 20,
    autoProg: true, addWatching: false, writeBack: true, ttl: '24h', titleLang: 'romaji', adult: false, mature: 'avisar', provs: [['AniList', true], ['ani.zip', true], ['Kitsu', true], ['Tenrai', true], ['MyAnimeList', false], ['Shikimori', false]],
    proc: 'telefono', histSync: true, linkOn: true, mdns: true, ram: '1gb', extSrv: false, histMode: 'completo',
    mTitle: 'romaji', mMode: 'paginado', mDir: 'rtl', mDouble: false, mFit: 'ancho', mWidth: 0, mGap: 0, mCrop: false, mBg: 'negro', mSysBright: true, mEager: false, mPrefetch: 5, mInf: true, mAuto: 0, mFull: true, mTap: true, mProg: true, mZoom: true,
    nFont: 'serif', nSize: 18, nLine: 16, nPara: 12, nWidth: 720, nBg: 'tema', nJust: false, nCont: true, nBar: true, nFull: true, nAwake: true, ttsEng: 'sistema', ttsSpeed: 10, ttsPitch: 10, ttsHl: true,
    nAir: true, nAni: true, updCheck: true };

  function catMejoras(ctx, st, R) {
    const V = st.v; const A = B.isA(ctx);
    const ups = [['off', 'Apagado'], ['a', 'Anime4K A'], ['aa', 'Anime4K A+A'], ['b', 'Anime4K B'], ['c', 'Anime4K C'], ['bb', 'Anime4K B+B'], ['ca', 'Anime4K C+A'], ['fsr', 'FSR'], ['fsrcnnx', 'Fidelidad (FSRCNNX)'], ['cas', 'Nitidez (CAS)']].concat(A ? [] : [['nis', 'NVScaler']]).map(([id, label]) => ({ id, label }));
    const fg = A ? `${R.info('fgState', 'Estado en este teléfono', 'Disponible', { ok: true, hint: 'Solo con libmpv. Gasta más batería y suma algo de latencia.' })}${R.act('fgDll', 'Shaders de LSFG (Lossless.dll)', 'Importados el 12 nov 2026.', 'Importar otra vez', { icon: 'upload_file' })}${R.tog('fgUse', 'Usar al reproducir', '')}${R.seg('fgMult', 'Multiplicador', ['Auto', '2×', '3×', '4×', '5×'].map((x) => ({ id: B.fold(x).replace(/\W/g, '') || 'auto', label: x })), '')}${R.seg('fgProf', 'Perfil', [{ id: 'ahorro', label: 'Ahorro' }, { id: 'equilibrado', label: 'Equilibrado' }, { id: 'calidad', label: 'Calidad' }], '')}${R.tog('fgHz', 'Cambiar la frecuencia de la pantalla', '')}${R.tog('fgUntested', 'Permitir en GPU sin probar', 'Vulkan 1.1 o 1.2. Experimental.')}${R.tog('fgStats', 'Mostrar estadísticas', '')}`
      : `${R.tog('lsfg', 'Generación de cuadros (lsfg-vk)', 'Necesita mpv externo, la capa de Vulkan y Lossless.dll.')}${V.lsfg ? R.seg('lsfgMult', 'Multiplicador', [{ id: 'auto', label: 'Auto' }, { id: '2', label: '×2' }, { id: '3', label: '×3' }, { id: '4', label: '×4' }], '') + R.tog('lsfgPerf', 'Modo rendimiento', '') : ''}${R.act('lsfgDll', 'Archivo de shaders', '~/.local/share/lsfg/Lossless.dll', 'Elegir archivo…', { kind: 'tonal' })}`;
    return R.group('Escalado', `${R.chips('upscale', 'Escalado por defecto', ups, V.motor === 'libmpv' ? 'Se aplica al abrir cada episodio.' : 'Solo lo aplica el motor libmpv.')}${R.seg('uq', 'Calidad del escalado', [{ id: 'rapido', label: 'Rápida' }, { id: 'equilibrado', label: 'Equilibrada' }, { id: 'alta', label: 'Alta' }], '')}${R.tog('deband', 'Quitar bandas de color', '')}${R.tog('autoadj', 'Ajustes automáticos', 'Baja un escalón si el equipo no llega a tiempo.')}`, { icon: 'hd' })
      + R.group('Decodificación', `${R.seg('dec', 'Decodificador', [{ id: 'auto', label: 'Auto' }, { id: 'hw', label: 'HW' }, { id: 'hwplus', label: 'HW+' }, { id: 'sw', label: 'SW' }], '')}${A ? '' : R.info('decState', 'Decodificación por hardware', 'VAAPI con la libva del sistema: H.264, HEVC, AV1', { ok: true })}${R.tog('smooth', 'Suavizado de movimiento', 'Interpolación de mpv con display-resample. No genera cuadros nuevos.')}`, { icon: 'memory' })
      + R.group('Generación de cuadros', fg, { icon: 'animation' });
  }
  function catSubtitulos(ctx, st, R) {
    const V = st.v; const L = [['es', 'Español'], ['en', 'Inglés'], ['pt', 'Portugués'], ['ja', 'Japonés'], ['fr', 'Francés'], ['de', 'Alemán'], ['it', 'Italiano'], ['ko', 'Coreano'], ['zh', 'Chino'], ['ru', 'Ruso'], ['ar', 'Árabe']].map(([id, label]) => ({ id, label }));
    const eng = V.trEngine; const needs = { deepl: 'clave', deeplx: 'url', azure: 'region', openai: 'modelo' }[eng];
    const d = st.draft.tr;
    const conn = eng !== 'apagada' && eng !== 'google' ? R.group('Conexión de ' + { deepl: 'DeepL', deeplx: 'DeepLX', azure: 'Azure', openai: 'el servidor compatible con OpenAI' }[eng], `<div class="nj-b-fieldgrid">${eng === 'deeplx' || eng === 'openai' || eng === 'azure' ? S.field(ctx, 'tr.url', eng === 'azure' ? 'Endpoint (opcional)' : 'Dirección del servidor', d.url, { ph: 'https://…', mono: true }) : ''}${eng === 'openai' ? S.field(ctx, 'tr.model', 'Modelo', d.model) : ''}${eng === 'azure' ? S.field(ctx, 'tr.region', 'Región', d.region) : ''}${S.field(ctx, 'tr.key', eng === 'deepl' || eng === 'azure' ? 'Clave de la API' : 'Clave de la API (opcional)', d.key, { type: 'password', ph: 'Sin clave guardada' })}</div>${S.commitFoot(ctx, st, 'tr', { test: true })}`, { icon: 'key', commit: true, id: 'tr', hint: needs ? 'La clave se guarda en el llavero del sistema.' : '' }) : '';
    return R.group('Idiomas', `${R.chips('subLang', 'Subtítulos preferidos', L.slice(0, 3), '')}${R.chips('audLang', 'Audio preferido', [L[3], L[0], L[1]], '')}${R.chips('sub2', 'Segundo subtítulo', [{ id: 'ninguno', label: 'Ninguno' }].concat(L), 'Muestra una segunda pista en otro idioma.')}`, { icon: 'translate' })
      + R.group('Estilo', `${R.row('subStyle', 'Estilo de subtítulos', 'Outfit · 100 % · caja al 40 %', h.btn('Editar con vista previa', { kind: 'tonal', icon: 'format_paint', attrs: { 'data-act': 'sheet', 'data-arg': 'subs' } }))}`, { icon: 'format_size' })
      + R.group('Traducción de subtítulos', `${R.chips('trEngine', 'Motor', [{ id: 'apagada', label: 'Apagada' }, { id: 'google', label: 'Google' }, { id: 'deepl', label: 'DeepL' }, { id: 'deeplx', label: 'DeepLX' }, { id: 'azure', label: 'Azure' }, { id: 'openai', label: 'Compatible con OpenAI' }], eng === 'apagada' ? 'Elige un motor para traducir mientras miras.' : eng === 'google' ? 'Google funciona sin clave.' : '')}${eng !== 'apagada' ? R.seg('trMode', 'Modo', [{ id: 'vivo', label: 'En vivo' }, { id: 'antes', label: 'Traducir antes' }], V.trMode === 'vivo' ? 'Cada línea se traduce al aparecer.' : 'Toda la pista se traduce antes de empezar.') + R.chips('trTarget', 'Traducir a', L.slice(0, 3).concat(L.slice(4)), '') + R.act('trTest', 'Probar la traducción', 'Traduce «Hello! Where are you going?».', 'Probar', { icon: 'science' }) + R.act('trCache', 'Caché de traducciones', '2,4 MB usados.', 'Vaciar caché', { kind: 'text' }) : ''}`, { icon: 'g_translate' }) + conn;
  }
  function catFuentes(ctx, st, R) {
    const A = B.isA(ctx);
    return R.group('Cómo se elige la fuente', `${R.seg('srcMode', 'Selección', [{ id: 'auto', label: 'Automática' }, { id: 'manual', label: 'Manual' }], st.v.srcMode === 'auto' ? 'Arranca la mejor fuente según lo de abajo.' : 'Siempre muestra la lista de fuentes.')}${R.seg('srcType', 'Tipo preferido', [{ id: 'cualquiera', label: 'Cualquiera' }, { id: 'web', label: 'Web' }, { id: 'torrent', label: 'Torrent' }], '')}${R.seg('minQ', 'Calidad mínima', [{ id: '480p', label: '480p' }, { id: '720p', label: '720p' }, { id: '1080p', label: '1080p' }], '')}`, { icon: 'source' })
      + R.group('Fuentes embebidas', `${A ? '' : R.info('chromium', 'Navegador oculto', 'Chromium encontrado', { ok: true })}${R.tog('embed', 'Usar fuentes embebidas', 'Abre páginas con reproductor en un navegador oculto. Quedan por debajo de HLS y MP4 directos.')}${R.sld('embedWait', 'Espera de la página', 5, 60, 5, ' s', 'Cuánto esperar antes de pasar a la siguiente fuente.')}`, { icon: 'web' })
      + R.group('Extensiones', R.act('exts', 'Extensiones, Stremio y repositorios', '3 instaladas · 1 actualización', 'Abrir Fuentes', { act: 'goScreen', kind: 'tonal', icon: 'extension' }).replace('data-arg="exts"', 'data-arg="fuentes"'), { icon: 'extension' });
  }
  function catCuentas(ctx, st, R) {
    const card = (name, linked) => `<article class="nj-b-acc"><span class="nj-b-avatar" aria-hidden="true">${name[0]}</span><div class="nj-grow"><b>${name}</b><span class="nj-meta">${linked ? 'Conectado como Kushro' : 'Sin vincular'}</span>${linked ? '<span class="nj-badge nj-b-okpill">Al día</span>' : ''}</div>${linked ? h.btn('Cómo sincronizar', { kind: 'text', attrs: { 'data-act': 'specAct', 'data-arg': 'syncMode' } }) + h.btn('Desvincular', { kind: 'danger', attrs: { 'data-act': 'specAct', 'data-arg': 'unlink' } }) : h.btn('Vincular', { kind: 'filled', attrs: { 'data-act': 'specAct', 'data-arg': 'link' } })}</article>`;
    return R.group('Servicios de listas', `${card('AniList', true)}${card('MyAnimeList', false)}${(st._collect && st._collect.push({ cat: 'cuentas', key: 'grp-acc', label: 'AniList y MyAnimeList', hint: 'Vincular, desvincular, modo de sincronización' }), '')}`, { icon: 'account_circle', id: 'acc' })
      + R.group('Sincronización', `${R.act('syncNow', 'Listas', 'Última sincronización hace 3 min · 2 cambios pendientes', 'Sincronizar ahora', { kind: 'tonal', icon: 'sync' })}${R.act('conflicts', '2 conflictos', 'Ao no Hako 2: aquí Ep. 8, puntuación 8,4 · en AniList Ep. 7', 'Conservar lo mío', { kind: 'outline' })}${R.tog('autoProg', 'Actualizar el progreso solo', 'Al terminar un episodio.')}${R.tog('addWatching', 'Pasar a Viendo al empezar', '')}${R.tog('writeBack', 'Enviar cambios a las cuentas', '')}`, { icon: 'sync' })
      + R.group('Importar y exportar', `${R.act('expJson', 'Exportar lista', 'nijiyome-lists-2026-11-25.json', 'Exportar JSON', { icon: 'download' })}${B.isA(ctx) ? '' : R.act('expCsv', 'Exportar a hoja de cálculo', '', 'Exportar CSV', { icon: 'table' })}${R.act('imp', 'Importar lista', B.isA(ctx) ? 'Archivo JSON.' : 'JSON, XML de MAL, volcado de AniList o archivo de Tatakai, hasta 8 MiB.', 'Elegir archivo…', { icon: 'upload_file' })}<p class="nj-b-group-hint">El historial de reproducción no es parte de la cuenta: se sincroniza con Link.</p>`, { icon: 'import_export' });
  }
  function catMetadatos(ctx, st, R) {
    const V = st.v;
    const provs = `<ol class="nj-b-order" id="${B.pid(ctx, 'row-provs')}">${V.provs.map(([n, onp], i) => `<li class="${onp ? '' : 'is-off'}"><span class="nj-b-ordn nj-b-tnum">${onp ? i + 1 : ''}</span><span class="nj-grow"><b>${esc(n)}</b><span class="nj-meta">${!onp ? 'Apagado' : n === 'AniList' ? 'Limitado: reintento en 0:42' : n === 'Kitsu' ? 'Funcionando · responde ahora' : 'Funcionando'}</span></span>${h.iconBtn('arrow_upward', 'Subir ' + n, { attrs: { 'data-act': 'provMove', 'data-arg': i, 'data-d': -1, disabled: i === 0 } })}${h.iconBtn('arrow_downward', 'Bajar ' + n, { attrs: { 'data-act': 'provMove', 'data-arg': i, 'data-d': 1, disabled: i === V.provs.length - 1 } })}<label class="nj-toggle nj-b-inltog"><span class="nj-b-vh">Usar ${esc(n)}</span><input type="checkbox" role="switch" data-act="provToggle" data-arg="${i}" ${onp ? 'checked' : ''} ${onp && V.provs.filter((p) => p[1]).length === 1 ? 'disabled' : ''}></label></li>`).join('')}</ol>${(st._collect && st._collect.push({ cat: 'metadatos', key: 'provs', label: 'Proveedores de metadatos', hint: 'AniList, Kitsu, ani.zip, orden y encendido' }), '')}`;
    return R.group('Proveedores', `<p class="nj-b-group-hint">El primero que responde gana; los demás son respaldo. El que se apaga sale de la lista.</p>${provs}`, { icon: 'dataset' })
      + R.group('Caché', `${R.info('mcache', 'Caché de metadatos', '48 MB · 3.120 entradas')}${B.isA(ctx) ? '' : R.act('mcacheClear', 'Vaciar la caché de metadatos', '', 'Vaciar', { kind: 'text' })}${R.seg('ttl', 'Conservar fichas por', [{ id: '6h', label: '6 h' }, { id: '24h', label: '24 h' }, { id: '3d', label: '3 d' }, { id: '7d', label: '7 d' }], '')}`, { icon: 'cached' })
      + R.group('Descubrir', `${R.seg('titleLang', 'Idioma de los títulos', [{ id: 'romaji', label: 'Romaji' }, { id: 'ingles', label: 'Inglés' }, { id: 'nativo', label: 'Nativo' }], 'En Explorar, la ficha y las tarjetas, cuando el proveedor lo tiene.')}${R.tog('adult', 'Mostrar contenido +18', 'Valor inicial del filtro +18 de Explorar.')}${V.adult ? R.seg('mature', 'Cómo se ven los títulos adultos', [{ id: 'mostrar', label: 'Mostrar' }, { id: 'difuminar', label: 'Difuminar' }, { id: 'avisar', label: 'Avisar' }], '') : ''}${R.act('recents', 'Búsquedas recientes', '3 guardadas en este equipo.', 'Borrar', { kind: 'text', confirm: true })}`, { icon: 'explore' });
  }
  function catLink(ctx, st, R) {
    const A = B.isA(ctx);
    if (A) return R.group('Este teléfono', `${R.act('linked', 'Dispositivo vinculado', `${D.link.pc} · conectado · ${D.link.latency}`, 'Abrir Link', { act: 'goScreen', kind: 'tonal', icon: 'devices' }).replace('data-arg="linked"', 'data-arg="link"')}${R.seg('proc', 'Procesar el video en', [{ id: 'telefono', label: 'Teléfono' }, { id: 'pc', label: 'PC' }, { id: 'mixto', label: 'Mixto' }], 'La PC escala y genera cuadros para el teléfono.')}${R.tog('histSync', 'Sincronizar el historial con la PC', 'Sincronizado hace 3 min.')}${R.act('linkSync', 'Sincronizar ahora', '', 'Sincronizar', { kind: 'tonal', icon: 'sync' })}`, { icon: 'devices' });
    const d = st.draft.linksrv;
    return R.group('Nijiyome Link en esta PC', `${R.tog('linkOn', 'Nijiyome Link en esta PC', 'Solo red privada, HTTPS y teléfonos emparejados. Escuchando en 192.168.1.20.')}${R.info('fp', 'Huella', '4F:9A:21:C7:…:0B', { mono: true, hint: 'El teléfono muestra la misma huella antes de emparejar.' })}${R.act('pair', 'Emparejar un teléfono', 'Muestra un QR válido por 120 s y de un solo uso.', 'Mostrar QR', { kind: 'filled', icon: 'qr_code_2' })}${R.row('paired', 'Teléfonos emparejados', '', `<ul class="nj-b-devlist"><li><span class="nj-grow"><b>${esc(D.link.phone)}</b><span class="nj-meta">Conectado ahora · 214 episodios aportados</span></span><div data-bset="v.histMode" class="nj-b-segsm">${h.seg('Historial', [{ id: 'completo', label: 'Completo' }, { id: 'recibir', label: 'Solo recibir' }, { id: 'pausado', label: 'Pausado' }], st.v.histMode)}</div>${h.btn('Renombrar', { kind: 'text', attrs: { 'data-act': 'specAct', 'data-arg': 'rename' } })}${h.btn('Revocar', { kind: 'danger', attrs: { 'data-act': 'specAct', 'data-arg': 'revoke' } })}</li></ul>`, { stack: true })}${R.seg('ram', 'Caché de reproducción en RAM', [{ id: '512', label: '512 MB' }, { id: '1gb', label: '1 GB' }, { id: '2gb', label: '2 GB' }, { id: '4gb', label: '4 GB' }], '')}${R.tog('mdns', 'Anunciar en la red', 'Los teléfonos encuentran esta PC sin escribir la dirección.')}`, { icon: 'devices' })
      + R.group('Servidor', `<div class="nj-b-fieldgrid">${S.field(ctx, 'linksrv.name', 'Nombre que ven los teléfonos', d.name)}<div class="nj-b-field"><span class="nj-b-flabel">Puerto</span>${B.stepper(ctx, 'linksrv.port', 'puerto de Link', d.port, { min: 1024, max: 65535 })}</div></div><div class="nj-b-field"><span class="nj-b-flabel">Carpeta de la biblioteca</span><div class="nj-b-path"><span class="nj-b-pathval">${ic('folder', { size: 18 })}<span>${esc(d.folder)}</span></span>${h.btn('Cambiar carpeta…', { kind: 'tonal', attrs: { 'data-act': 'folderPick', 'data-arg': 'linksrv.folder' } })}</div></div>${(st._collect && st._collect.push({ cat: 'link', key: 'grp-linksrv', label: 'Puerto y nombre de Link', hint: 'Servidor de Link en esta PC' }), '')}${S.commitFoot(ctx, st, 'linksrv', { apply: 'Aplicar y reiniciar Link' })}`, { icon: 'router', commit: true, id: 'linksrv', hint: 'Aplicar reinicia el servidor; los teléfonos se reconectan solos.' })
      + R.group('Seguridad', `${R.act('firewall', 'Cortafuegos', 'Abre 47321/tcp en ufw o firewalld para la red privada.', 'Ver comandos', { kind: 'text' })}${R.act('regen', 'Regenerar identidad', 'Todos los teléfonos emparejados se olvidan; queda un respaldo.', 'Regenerar…', { kind: 'danger', confirm: true })}`, { icon: 'shield' })
      + R.group('Servidor de extensiones', `${R.tog('extSrv', 'Compartir extensiones con teléfonos', 'HTTP en la red local, puerto 8099; cada teléfono necesita su código.')}${st.v.extSrv ? R.act('extCode', 'Código nuevo para un teléfono', '', 'Mostrar QR', { kind: 'tonal', icon: 'qr_code_2' }) + R.act('extPass', 'Contraseña de visor', 'No hay contraseña puesta.', 'Poner contraseña', { kind: 'text' }) + R.act('extLog', 'Peticiones y errores', '12 en la última hora.', 'Ver registro', { kind: 'text' }) : ''}`, { icon: 'extension' });
  }
  function catLectores(ctx, st, R) {
    const V = st.v; const A = B.isA(ctx); const paged = V.mMode === 'paginado';
    return R.group('Manga', `${R.seg('mTitle', 'Idioma del título', [{ id: 'romaji', label: 'Romaji' }, { id: 'ingles', label: 'Inglés' }, { id: 'nativo', label: 'Nativo' }], 'Aparte del de anime.')}${R.seg('mMode', 'Modo de lectura', [{ id: 'paginado', label: 'Paginado', icon: 'auto_stories' }, { id: 'vertical', label: 'Vertical', icon: 'swap_vert' }, { id: 'webtoon', label: 'Webtoon', icon: 'view_day' }], '')}${paged ? R.seg('mDir', 'Dirección', [{ id: 'rtl', label: 'Derecha a izquierda', icon: 'west' }, { id: 'ltr', label: 'Izquierda a derecha', icon: 'east' }], '') + R.tog('mDouble', 'Doble página', 'Dos páginas lado a lado en pantallas anchas.') : R.tog('mInf', 'Desplazamiento infinito', 'Sigue con el capítulo siguiente.')}${R.seg('mFit', 'Ajuste de página', [{ id: 'ancho', label: 'Ancho' }, { id: 'alto', label: 'Alto' }, { id: 'pantalla', label: 'Pantalla' }, { id: 'natural', label: 'Natural' }], '')}${R.sld('mWidth', 'Ancho máximo', 0, 2000, 50, ' dp', '0 es sin límite.')}${R.sld('mGap', 'Separación entre páginas', 0, 64, 4, ' dp', '')}${R.tog('mCrop', 'Recortar bordes', 'Quita los márgenes blancos o negros del escaneo.')}${R.chips('mBg', 'Fondo', [{ id: 'negro', label: 'Negro' }, { id: 'gris', label: 'Gris' }, { id: 'blanco', label: 'Blanco' }, { id: 'tema', label: 'Del tema' }], '')}${R.tog('mSysBright', 'Brillo del sistema', '')}${R.tog('mEager', 'Cargar el capítulo entero', '')}${V.mEager ? '' : R.sld('mPrefetch', 'Páginas a precargar', 1, 20, 1, '', '')}${R.sld('mAuto', 'Desplazamiento automático', 0, 600, 20, ' px/s', '0 lo apaga.')}${R.tog('mFull', 'Pantalla completa al abrir', '')}${R.tog('mTap', 'Un toque oculta los controles', '')}${R.tog('mProg', 'Mostrar el progreso de página', '')}${R.tog('mZoom', 'Atajos de zoom', 'Ctrl y la rueda, y las teclas de zoom.')}${R.act('mKeys', 'Atajos del lector de manga', '12 acciones.', 'Cambiar teclas', { kind: 'text' })}`, { icon: 'menu_book' })
      + R.group('Novelas', `${R.chips('nFont', 'Letra', [{ id: 'serif', label: 'Serif' }, { id: 'sans', label: 'Sans' }, { id: 'georgia', label: 'Georgia' }, { id: 'lora', label: 'Lora' }, { id: 'dislexia', label: 'Para dislexia' }].concat(A ? [] : [{ id: 'mono', label: 'Mono' }]).concat([{ id: 'sistema', label: 'Del sistema' }]), '')}${R.act('nImport', 'Letra propia', 'Archivo TTF u OTF.', A ? 'Importar…' : 'Importar o elegir instalada…', { kind: 'text' })}${R.sld('nSize', 'Tamaño del texto', 10, 40, 1, ' sp', '')}${R.sld('nLine', 'Interlineado (×10)', 10, 25, 1, '', '')}${R.sld('nPara', 'Espacio entre párrafos', 0, 48, 2, ' dp', '')}${R.sld('nWidth', 'Ancho máximo', 0, 1600, 50, ' dp', '')}${R.chips('nBg', 'Colores', [{ id: 'tema', label: 'Del tema' }, { id: 'sepia', label: 'Sepia' }, { id: 'blanco', label: 'Blanco' }, { id: 'gris', label: 'Gris' }, { id: 'negro', label: 'Negro' }], '')}${R.tog('nJust', 'Justificar', '')}${R.tog('nCont', 'Desplazamiento continuo', 'El capítulo siguiente sigue abajo.')}${R.tog('nBar', 'Barra de progreso', '')}${R.tog('nFull', 'Pantalla completa al abrir', '')}${R.tog('nAwake', 'Mantener la pantalla encendida', '')}`, { icon: 'auto_stories' })
      + R.group('Narrador', `${R.seg('ttsEng', 'Motor de voz', (A ? [] : [{ id: 'kitten', label: 'KittenTTS' }]).concat([{ id: 'sistema', label: 'Sistema' }, { id: 'openai', label: 'OpenAI' }, { id: 'eleven', label: 'ElevenLabs' }, { id: 'local', label: 'Servidor local' }]), A ? '' : 'KittenTTS corre sin conexión, solo en inglés.')}${R.act('ttsVoice', 'Voz', 'La predeterminada del motor.', 'Elegir y escuchar…', { kind: 'tonal', icon: 'record_voice_over' })}${R.sld('ttsSpeed', 'Velocidad (×10)', 5, 20, 1, '', '')}${A ? '' : R.sld('ttsPitch', 'Tono (×10)', 5, 20, 1, '', 'Solo con la voz del sistema.')}${R.tog('ttsHl', 'Resaltar el párrafo que se lee', '')}`, { icon: 'record_voice_over' });
  }
  function catNotif(ctx, st, R) {
    return R.group('Avisos', `${B.isA(ctx) ? R.info('perm', 'Permiso de notificaciones', 'Permitido', { ok: true }) : ''}${R.tog('nAir', 'Estrenos de lo que sigues', 'Un aviso cuando sale un episodio de una serie con campana.')}${R.act('follows', 'Series con aviso', '4 series con campana activa.', 'Abrir Calendario', { act: 'goScreen', kind: 'text' }).replace('data-arg="follows"', 'data-arg="calendario"')}${R.tog('nAni', 'Notificaciones de AniList', 'Seguidores, me gusta y respuestas, revisadas cada 15 minutos.')}${R.tog('checkNew', 'Buscar episodios nuevos para descargar', 'Para las reglas de descarga automática.')}`, { icon: 'notifications' });
  }
  function catDatos(ctx, st, R) {
    const A = B.isA(ctx);
    return R.group('Almacenamiento', `<div class="nj-b-row is-stack"><div class="nj-b-stack-bar" role="img" aria-label="Uso del disco por tipo"><i class="s1" style="--w:.06"></i><i class="s2" style="--w:.18"></i><i class="s3" style="--w:.05"></i></div><ul class="nj-b-legend2 nj-b-tnum"><li><i class="s1"></i>Caché de metadatos e imágenes · 1,2 GB</li><li><i class="s2"></i>Torrents · 38 GB</li>${A ? '' : '<li><i class="s3"></i>Descargas web, registros y extensiones · 9 GB</li>'}<li><i></i>Libre · 410 GB</li></ul><span class="nj-meta">Cifras de ejemplo.</span></div>${A ? '' : R.act('dataDir', 'Carpeta de datos', 'Base de datos, ajustes, registros y tokens sellados.', 'Abrir carpeta', { kind: 'text', icon: 'open_in_new' })}${R.info('telemetry', 'Telemetría', 'Ninguna', { ok: true, hint: 'Esta compilación no envía nada; no hay interruptor porque no hay nada que apagar.' })}`, { icon: 'storage' })
      + R.group('Historial y caché', `${R.act('cacheClear', 'Vaciar la caché', 'Borra 1,2 GB de metadatos e imágenes. El historial y los torrents se quedan.', 'Vaciar caché…', { kind: 'outline', confirm: true })}${R.act('histExport', 'Exportar historial', 'nijiyome-history-2026-11-25.json', 'Exportar', { kind: 'text', icon: 'download' })}${R.act('histClear', 'Borrar el historial', 'También en la PC emparejada si lo eliges.', 'Borrar historial…', { kind: 'danger', confirm: true })}`, { icon: 'history' })
      + R.group('Zona de riesgo', `${R.act('reset', 'Restablecer la app', A ? 'Vuelve al primer arranque.' : 'Pide escribir RESET; puedes conservar los torrents descargados.', 'Restablecer…', { kind: 'danger', confirm: true })}`, { icon: 'warning' });
  }
  function catAcerca(ctx, st, R) {
    const A = B.isA(ctx);
    const comps = A ? 'Media3 1.11.1 · libmpv 1.1.0 · libtorrent4j · Link v1' : 'Compose Multiplatform · MediaMP · libtorrent4j · QuickJS · OkHttp · Java 21 · Link v1';
    return R.group('Versión', `${R.info('ver', 'Nijiyome', '1.2.1', { mono: true })}${R.info('comps', 'Componentes', comps, { hint: 'Licencia AGPL-3.0 o posterior.' })}${R.tog('updCheck', 'Buscar versiones nuevas', 'Como mucho una vez al día, en las versiones de GitHub.')}${R.act('upd', 'Actualizaciones', st.updState === 'ok' ? 'Estás al día.' : 'Última revisión: ayer.', st.updState === 'run' ? 'Buscando…' : 'Buscar ahora', { kind: 'tonal' })}`, { icon: 'info' })
      + R.group('Diagnóstico', `${R.act('logs', 'Registros de errores', A ? 'De este teléfono; se pueden enviar a la PC.' : 'De esta PC y de los dispositivos vinculados.', 'Abrir registros', { kind: 'tonal', icon: 'bug_report' })}${R.act('licenses', 'Licencias de terceros', '', 'Ver licencias', { kind: 'text' })}`, { icon: 'bug_report' });
  }
  const RENDER = { apariencia: S.catApariencia, reproduccion: S.catReproduccion, mejoras: catMejoras, subtitulos: catSubtitulos, fuentes: catFuentes, cuentas: catCuentas, metadatos: catMetadatos, link: catLink, transferencias: S.catTorrents, lectores: catLectores, notificaciones: catNotif, sistema: S.catSistema, datos: catDatos, acerca: catAcerca };

  const cats = (ctx) => D.settingsCategories.filter((c) => !(c.desktop && B.isA(ctx)));
  function summary(ctx, st, id) {
    const V = st.v; const pal = ((NJ.directions[ctx.dir] || {}).palettes || []).find((p) => p.id === V.paleta);
    return { apariencia: `${{ claro: 'Claro', oscuro: 'Oscuro', oled: 'OLED', sistema: 'Sistema' }[V.tema]} · ${pal ? pal.name : 'paleta'} · animación ${V.anim}`, reproduccion: `${V.motor === 'externo' ? 'mpv externo' : V.motor} · velocidad ${String(V.speed).replace('.', ',')}×`, transferencias: `${S.fmtRate(S.RATE_STOPS[V.down])} ↓ · puerto ${V.portRandom ? 'aleatorio' : V.port}${V.sched ? ' · con horario' : ''}`, sistema: `${V.tray ? 'Bandeja' : 'Sin bandeja'} · proxy ${st.applied.proxy.on ? 'activo' : 'apagado'}`, mejoras: 'Anime4K A+A · decodificador automático', cuentas: 'AniList vinculada · 2 pendientes', link: `${D.link.pc} · ${B.isA(ctx) ? 'conectado' : 'escuchando'}` }[id] || (D.settingsCategories.find((c) => c.id === id) || {}).desc;
  }
  function buildIndex(ctx, st) {
    if (st._idx && st._idxP === ctx.platform) return st._idx;
    const list = []; const save = { flash: st.flash, saved: st._saved }; st._collect = list; st.flash = null;
    cats(ctx).forEach((c) => { try { RENDER[c.id](ctx, st, S.mk(ctx, st, c.id)); } catch (e) { console.error(e); } });
    st._collect = null; st.flash = save.flash;
    const extra = [['apariencia', 'tema', 'AMOLED negro puro', 'OLED, modo oscuro'], ['transferencias', 'grp-trackers', 'Trackers', 'Rastreadores propios'], ['sistema', 'proxy', 'Proxy SOCKS5 o HTTP', 'Red'], ['acerca', 'logs', 'Logs', 'Registros de errores'], ['transferencias', 'debridAcc', 'Debrid TorBox', 'Cuentas de debrid'], ['subtitulos', 'subStyle', 'Tamaño de subtítulos', 'Fuente, color, contorno']];
    extra.forEach(([cat, key, label, hint]) => { if (cats(ctx).some((c) => c.id === cat)) list.push({ cat, key, label, hint }); });
    st._idx = list; st._idxP = ctx.platform; return list;
  }
  function search(ctx, st) {
    const q = B.fold(st.q.trim()); if (!q) return [];
    const toks = q.split(/\s+/); const idx = buildIndex(ctx, st);
    return idx.map((e) => { const t = B.fold(e.label); const hh = B.fold(e.hint + ' ' + (D.settingsCategories.find((c) => c.id === e.cat) || {}).name); if (!toks.every((k) => t.includes(k) || hh.includes(k))) return null; const sc = t.startsWith(q) ? 4 : t.split(/\s+/).some((w) => w.startsWith(toks[0])) ? 3 : t.includes(q) ? 2 : 1; return Object.assign({ sc }, e); }).filter(Boolean).sort((a, b) => b.sc - a.sc).slice(0, 14);
  }
  const mark = (txt, q) => { const f = B.fold(txt); const i = f.indexOf(B.fold(q.trim().split(/\s+/)[0] || '')); if (!q.trim() || i < 0) return esc(txt); const n = (q.trim().split(/\s+/)[0] || '').length; return esc(txt.slice(0, i)) + '<mark>' + esc(txt.slice(i, i + n)) + '</mark>' + esc(txt.slice(i + n)); };

  function searchField(ctx, st, o = {}) { return `<label class="nj-search nj-b-setsearch${o.big ? ' is-big' : ''}">${ic('search')}<input id="${B.pid(ctx, 'setq')}" data-input="q" type="search" placeholder="Buscar un ajuste" value="${esc(st.q)}" aria-label="Buscar un ajuste">${st.q ? h.iconBtn('close', 'Borrar búsqueda', { attrs: { 'data-act': 'clearQ' } }) : ''}${o.kbd ? '<span class="nj-kbd">Ctrl P</span>' : ''}</label>`; }
  function resultsList(ctx, st) {
    const res = search(ctx, st);
    if (!res.length) return `<div class="nj-b-setempty">${B.emptyState('search_off', `Ningún ajuste coincide con «${st.q}»`, 'Prueba con AMOLED, proxy, subtítulos, registros o debrid.', ['AMOLED', 'proxy', 'subtítulos', 'debrid'].map((w) => `<button type="button" class="nj-chip" data-act="setQ" data-arg="${w}">${w}</button>`).join(''))}</div>`;
    return `<p class="nj-meta nj-b-rescount" role="status">${res.length} ${res.length === 1 ? 'ajuste' : 'ajustes'} para «${esc(st.q)}»</p><ul class="nj-b-setres">${res.map((r) => { const c = D.settingsCategories.find((x) => x.id === r.cat); return `<li><button type="button" class="nj-b-setresrow${st.cat === r.cat && st.flash === r.key ? ' is-cur' : ''}" data-act="jump" data-cat="${r.cat}" data-key="${r.key}">${ic(S.CAT_IC[r.cat] || 'settings', { size: 20 })}<span class="nj-grow"><span class="nj-b-rowtitle">${mark(r.label, st.q)}</span><span class="nj-meta">${esc(c.name)}${r.hint ? ' · ' + esc(r.hint) : ''}</span></span>${ic('arrow_forward', { size: 18 })}</button></li>`; }).join('')}</ul>`;
  }
  function catList(ctx, st, o = {}) {
    return `<nav class="nj-b-catlist${o.cards ? ' is-cards' : ''}" aria-label="Categorías">${cats(ctx).map((c) => `<button type="button" class="nj-b-cat" data-act="cat" data-arg="${c.id}"${st.cat === c.id && !o.cards && !B.isA(ctx) ? ' aria-current="page"' : ''}>${ic(S.CAT_IC[c.id], { size: 22, fill: st.cat === c.id && !o.cards })}<span class="nj-grow"><span class="nj-b-catname">${esc(c.name)}</span><span class="nj-b-catsum">${esc(summary(ctx, st, c.id))}</span></span>${B.isA(ctx) || o.cards ? ic('chevron_right', { size: 20 }) : ''}</button>`).join('')}</nav>`;
  }
  function detail(ctx, st) {
    const c = D.settingsCategories.find((x) => x.id === st.cat) || D.settingsCategories[0];
    const R = S.mk(ctx, st, c.id); const A = B.isA(ctx);
    const legend = `<p class="nj-b-savelegend">${ic('bolt', { size: 18 })}<span>Los cambios se guardan al tocar y muestran <b>Guardado</b>. Los bloques con ${ic('pending_actions', { size: 16 })} reinician una conexión y piden <b>Aplicar</b>.</span></p>`;
    const body = RENDER[c.id](ctx, st, R);
    const prev = c.id === 'apariencia' ? S.preview(ctx, st) : '';
    return `<div class="nj-b-detail${c.id === 'apariencia' ? ' has-preview' : ''}">${A ? '' : `<header class="nj-b-dethead">${ic(S.CAT_IC[c.id], { size: 28 })}<div><h2 class="nj-b-dettitle">${esc(c.name)}</h2><p class="nj-meta">${esc(c.desc)}</p></div></header>`}${legend}${prev ? `<div class="nj-b-prevsticky">${prev}</div>` : ''}<div class="nj-b-groups">${body}</div></div>`;
  }
  function folderDialog(ctx, st) {
    const tree = [['/home/octavio', 0], ['Descargas', 1], ['Nijiyome', 2], ['Videos', 1], ['Anime', 2], ['Películas', 2], ['/mnt/media', 0], ['Anime', 1], ['Respaldo', 1]];
    const paths = ['/home/octavio', '/home/octavio/Descargas', '/home/octavio/Descargas/Nijiyome', '/home/octavio/Videos', '/home/octavio/Videos/Anime', '/home/octavio/Videos/Películas', '/mnt/media', '/mnt/media/Anime', '/mnt/media/Respaldo'];
    const sel = st.folderSel || paths[4];
    return `<div class="nj-overlay nj-b-overlay is-center" data-act="folderClose"><div class="nj-dialog nj-b-folderdlg" tabindex="-1" role="dialog" aria-modal="true" aria-label="Elegir carpeta" data-act="noop"><h2 class="nj-h2">Elegir carpeta</h2><p class="nj-meta nj-b-mono">${esc(sel)}</p><ul class="nj-b-tree" role="tree">${tree.map(([n, d], i) => `<li role="treeitem" aria-selected="${paths[i] === sel}"><button type="button" class="nj-b-treerow${paths[i] === sel ? ' is-sel' : ''}" style="--d:${d}" data-act="folderSel" data-arg="${esc(paths[i])}">${ic(paths[i] === sel ? 'folder_open' : 'folder', { size: 20, fill: paths[i] === sel })}${esc(n)}</button></li>`).join('')}</ul><div class="nj-cluster nj-b-dlgacts">${h.btn('Nueva carpeta', { kind: 'text', icon: 'create_new_folder' })}<span class="nj-b-grow"></span>${h.btn('Cancelar', { kind: 'text', attrs: { 'data-act': 'folderClose' } })}${h.btn('Usar esta carpeta', { kind: 'filled', attrs: { 'data-act': 'folderOk' } })}</div></div></div>`;
  }
  function confirmDialog(ctx, st) {
    const c = st.confirm; if (!c) return '';
    return `<div class="nj-overlay nj-b-overlay is-center" data-act="confirmClose"><div class="nj-dialog" tabindex="-1" role="alertdialog" aria-modal="true" aria-label="${esc(c.title)}" data-act="noop"><h2 class="nj-h2">${esc(c.title)}</h2><p>${esc(c.body)}</p>${c.typed ? `<label class="nj-b-field"><span class="nj-b-flabel">Escribe RESET para confirmar</span><span class="nj-field is-mono"><input id="${B.pid(ctx, 'f-typed')}" data-typed value="${esc(st.typed || '')}" autocomplete="off"></span></label>${h.toggle(B.pid(ctx, 't-keepTor'), 'Conservar los torrents descargados', st.keepTor !== false)}` : ''}${c.check ? h.toggle(B.pid(ctx, 't-alsoPc'), c.check, true) : ''}<div class="nj-cluster nj-b-dlgacts">${h.btn('Cancelar', { kind: 'text', attrs: { 'data-act': 'confirmClose' } })}${h.btn(c.btn, { kind: 'danger', attrs: { 'data-act': 'confirmOk', disabled: c.typed && (st.typed || '').trim().toUpperCase() !== 'RESET' } })}</div></div></div>`;
  }
  function sheets(ctx, st) {
    if (st.sheet === 'subs') {
      const s = st.subs = st.subs || { look: 'pred', size: 100, bg: 'caja', pos: 100 };
      const bgCss = s.bg === 'caja' ? ' is-box' : s.bg === 'sombra' ? ' is-shadow' : '';
      return B.sheet(ctx, 'Estilo de subtítulos', `<div class="nj-b-subprev${st.subsLight ? ' is-light' : ''}" style="background-image:url(img/frame-laidoff.webp)"><span class="nj-b-subline is-${s.look}${bgCss}" style="--sz:${s.size / 100};--pos:${s.pos}">Así se van a ver tus subtítulos<br>Los cambios se ven al momento</span></div>${h.btn(st.subsLight ? 'Probar sobre fondo oscuro' : 'Probar sobre fondo claro', { kind: 'text', attrs: { 'data-act': 'subsBg' } })}<section class="nj-b-sheetsec"><h3 class="nj-h3">Estilos rápidos</h3><div class="nj-b-vpick is-4" role="radiogroup" aria-label="Estilos rápidos">${[['pred', 'Predeterminado'], ['contorno', 'Contorno'], ['caja', 'Caja'], ['amarillo', 'Amarillo']].map(([id, l]) => `<button type="button" role="radio" class="nj-b-vopt" aria-checked="${s.look === id}" data-act="subsSet" data-k="look" data-arg="${id}"><span class="nj-b-subthumb is-${id}">Aa</span><span class="nj-b-vopt-l">${l}</span></button>`).join('')}</div></section><section class="nj-b-sheetsec">${h.slider(B.pid(ctx, 's-subsize'), 'Tamaño', { min: 50, max: 250, step: 5, value: s.size, unit: ' %' })}${h.slider(B.pid(ctx, 's-subpos'), 'Posición vertical', { min: 0, max: 100, step: 1, value: s.pos, unit: ' %' })}</section><section class="nj-b-sheetsec"><h3 class="nj-h3">Fondo del texto</h3><div data-bset="subs.bg">${h.seg('Fondo', [{ id: 'caja', label: 'Caja' }, { id: 'sombra', label: 'Sombra' }, { id: 'nada', label: 'Nada' }], s.bg)}</div><p class="nj-meta">libmpv no dibuja sombra y caja a la vez; por eso es una sola elección.</p></section><section class="nj-b-sheetsec"><p class="nj-meta">También en esta hoja: letra (incluidas las importadas y las del sistema), negrita, cursiva, alineación, colores con opacidad, contorno, márgenes, interlineado, suavizado de bordes y el modo ASS.</p></section>`, { close: 'sheetClose', foot: h.btn('Restablecer todo', { kind: 'text', attrs: { 'data-act': 'subsReset' } }) });
    }
    if (st.sheet === 'logs') {
      const lines = [['21:03:58', 'I', 'Player', 'Fuente Kitsune Raws 1080p elegida'], ['21:03:41', 'W', 'AniList', 'HTTP 429, reintento en 42 s; se usa Kitsu'], ['21:02:10', 'E', 'Toko', 'Timeout en /episodes (8 s)'], ['21:01:55', 'I', 'Link', 'moto g51 5G conectado (14 ms)']];
      const lv = st.logLv || 'todos';
      const shown = lines.filter((l) => lv === 'todos' || (lv === 'warn' ? l[1] !== 'I' : l[1] === 'E'));
      return B.sheet(ctx, 'Registros', `<div class="nj-cluster">${B.isA(ctx) ? h.btn('Enviar a la PC', { kind: 'tonal', icon: 'send_to_mobile' }) : `<div data-bset="logSrc">${h.chips('Origen', [{ id: 'pc', label: 'Esta PC' }, { id: 'tel', label: D.link.phone }, { id: 'todos', label: 'Todos' }], [st.logSrc || 'pc'])}</div>`}</div><div data-bset="logLv">${h.chips('Gravedad', [{ id: 'todos', label: 'Todos' }, { id: 'warn', label: 'Avisos y errores' }, { id: 'err', label: 'Solo errores' }], [lv])}</div><ul class="nj-b-logs">${shown.map((l) => `<li class="is-${l[1]}"><span>${l[0]}</span><b>${l[1]}</b><span>${esc(l[2])}</span><span>${esc(l[3])}</span></li>`).join('')}</ul>`, { close: 'sheetClose', wide: true, foot: `${h.btn('Vaciar', { kind: 'text', attrs: { 'data-act': 'logsClear' } })}${h.btn(B.isA(ctx) ? 'Compartir' : 'Mostrar el archivo', { kind: 'tonal', icon: B.isA(ctx) ? 'share' : 'folder_open' })}` });
    }
    return '';
  }

  /* Paleta de comandos (lab.js): NJ.paletteTarget = { type: 'setting', section, key }. Se lee al dibujar y se limpia
     después del pase de render, para que también lo tome el segundo marco en la vista "Escritorio y Android juntos". */
  const PALETTE_KEYS = { 'sub-size': ['subtitulos', 'subStyle'], 'sub-outline': ['subtitulos', 'subStyle'], 'sub-lang': ['subtitulos', 'subLang'], 'min-quality': ['fuentes', 'minQ'], 'prefer-cache': ['transferencias', 'preferTorbox'], anime4k: ['mejoras', 'upscale'], framegen: ['mejoras', 'lsfg', 'fgUse'], motion: ['apariencia', 'anim'], 'perf-mode': ['apariencia', 'anim'], 'link-port': ['link', 'grp-linksrv', 'proc'], 'bw-schedule': ['transferencias', 'sched'], 'dl-folder': ['transferencias', 'tDir'], 'sleep-timer': ['reproduccion', 'sleep'], adult: ['metadatos', 'adult'], 'anilist-sync': ['cuentas', 'syncNow'], autostart: ['sistema', 'autostart'] };
  function takePaletteTarget(ctx, st) {
    const t = NJ.paletteTarget; if (!t || t.type !== 'setting') return;
    const m = PALETTE_KEYS[t.key] || [t.section, t.key];
    const cat = m[0] || t.section; if (!D.settingsCategories.some((c) => c.id === cat) || (cat === 'sistema' && B.isA(ctx))) return;
    st.cat = cat; st.flash = (B.isA(ctx) && m[2]) || m[1] || null; st.open = true; st.q = ''; st._jump = true; st._scroll = null;
    if (t.key === 'perf-mode') { st.v.anim = 'rendimiento'; }
    if (!t._clear) { t._clear = true; setTimeout(() => { if (NJ.paletteTarget === t) NJ.paletteTarget = null; }, 0); }
  }
  function renderSet(ctx) {
    const st = B.defaults(ctx.state, { cat: 'apariencia', open: false, q: '', flash: null, keys: {}, capture: null, capKey: null, sheet: null, confirm: null, portTest: null, test: null, folderPick: null, custom: false });
    st.v = Object.assign(S.vDefaults(ctx), MORE, st.v || {});
    if (!st.draft) { st.draft = B.clone(S.DRAFT0); st.applied = B.clone(S.DRAFT0); }
    if (B.isA(ctx) && st.cat === 'sistema') st.cat = 'apariencia';
    const A = B.isA(ctx); const sb = ctx.structure === 'buscador';
    takePaletteTarget(ctx, st);
    const overlays = `${st.capture ? S.captureDialog(ctx, st) : ''}${st.folderPick ? folderDialog(ctx, st) : ''}${confirmDialog(ctx, st)}${sheets(ctx, st)}${B.snackHtml(st)}`;
    const catName = (D.settingsCategories.find((c) => c.id === st.cat) || {}).name;
    if (A) {
      if (st.open) return `<div class="nj-b-set">${B.appbar(catName, '', { back: 'back' })}<div class="nj-b-pad" data-keep="det">${detail(ctx, st)}</div>${overlays}</div>`;
      const top = `${B.appbar('Ajustes', h.iconBtn('account_circle', 'Perfil', { attrs: { 'data-go': 'perfil' } }))}<div class="nj-b-pad nj-stack">${searchField(ctx, st, { big: sb })}${st.q ? '' : sb ? `<div class="nj-b-freq"><span class="nj-meta">Lo más buscado</span><div class="nj-cluster">${['AMOLED', 'Velocidad', 'Puerto', 'Subtítulos', 'Debrid', 'Registros'].map((w) => `<button type="button" class="nj-chip" data-act="setQ" data-arg="${w}">${w}</button>`).join('')}</div></div>` : ''}</div>`;
      return `<div class="nj-b-set">${top}<div class="nj-b-pad">${st.q ? resultsList(ctx, st) : catList(ctx, st, { cards: true })}</div>${overlays}</div>`;
    }
    if (sb) {
      const left = st.q ? resultsList(ctx, st) : `<div class="nj-b-freq"><span class="nj-meta">Lo más buscado</span><div class="nj-cluster">${['AMOLED', 'Proxy', 'Puerto', 'Atajos', 'Subtítulos', 'Debrid', 'Registros'].map((w) => `<button type="button" class="nj-chip" data-act="setQ" data-arg="${w}">${w}</button>`).join('')}</div></div>${catList(ctx, st, { cards: true })}`;
      return `<div class="nj-b-page nj-b-set is-buscador"><header class="nj-b-head nj-b-sethead"><h1 class="nj-b-title">Ajustes</h1>${searchField(ctx, st, { big: true, kbd: true })}</header><div class="nj-b-setgrid is-search"><div class="nj-b-setleft" data-keep="left">${left}</div><div class="nj-b-setright" data-keep="det">${detail(ctx, st)}</div></div>${overlays}</div>`;
    }
    return `<div class="nj-b-set nj-b-setgrid"><aside class="nj-b-setleft" data-keep="left"><h1 class="nj-b-title nj-b-settitle">Ajustes</h1>${searchField(ctx, st, { kbd: true })}${st.q ? resultsList(ctx, st) : catList(ctx, st)}</aside><div class="nj-b-setright" data-keep="det">${detail(ctx, st)}</div>${overlays}</div>`;
  }

  function mountSet(el, ctx) {
    const st = ctx.state;
    const markSaved = (key) => { st._saved = key; B.laterS(st, 1500, (now) => { if (st._saved === key) { st._saved = null; const n = now.querySelector(`#${CSS.escape(B.pid(ctx, 'row-' + key))} .nj-b-saved`); if (n) n.classList.remove('is-on'); } }); };
    const setV = (key, val) => { st.v[key] = val; if (['inicio', 'tarjeta', 'tsize', 'densidad', 'anim', 'pagina', 'encabezado', 'episodios', 'relacionados'].includes(key)) st.custom = true; markSaved(key); };
    const draftSet = (path, val) => { const [b, k] = path.split('.'); st.draft[b][k] = val; };
    const H = Object.assign(B.snackHandlers(el, ctx), {
      $set: (key, val) => { if (key.startsWith('v.')) setV(key.slice(2), val); },
      $esc: (rd) => { if (st.capture) { st.capture = null; st.capKey = null; rd(); return true; } if (st.confirm) { st.confirm = null; rd(); return true; } if (st.folderPick) { st.folderPick = null; rd(); return true; } if (st.sheet) { st.sheet = null; rd(); return true; } return false; },
      cat: (a, e, rd) => { st.cat = a.dataset.arg; st.open = true; st.flash = null; st._scroll = null; if (!B.isA(ctx) && ctx.structure === 'buscador') {} ctx.rerender(); },
      back: (a, e, rd) => { st.open = false; ctx.rerender(); },
      clearQ: (a, e, rd) => { st.q = ''; rd(); },
      setQ: (a, e, rd) => { st.q = a.dataset.arg; rd(); },
      jump: (a) => { st.cat = a.dataset.cat; st.flash = a.dataset.key; st.open = true; if (B.isA(ctx)) st.q = ''; st._jump = true; B.keep(el, ctx); },
      vset: (a, e, rd) => { const k = a.dataset.key; const v = a.dataset.arg; if (k === 'preset') { const P = { nijiyome: ['destacado', 'detallada', 'm', 'comoda', 'completo'], cine: ['destacado', 'detallada', 'l', 'comoda', 'completo'], denso: ['filas', 'clasica', 's', 'compacta', 'calmo'], calmo: ['carrusel', 'clasica', 'm', 'comoda', 'calmo'] }[v]; ['inicio', 'tarjeta', 'tsize', 'densidad', 'anim'].forEach((kk, i) => { st.v[kk] = P[i]; }); st.v.preset = v; st.custom = false; if (v === 'cine') st.v.menu = 'iconos'; markSaved('preset'); } else setV(k, v); rd(); },
      step: (a, e, rd) => { const k = a.dataset.key; const d = +a.dataset.d; if (k.includes('.')) { const [b, f] = k.split('.'); st.draft[b][f] = Math.max(1, Math.min(65535, st.draft[b][f] + d)); } else { st.v[k] = Math.max(1024, Math.min(65535, st.v[k] + d)); st.portTest = null; markSaved(k); } rd(); },
      portRandom: (a, e, rd) => { st.v.portRandom = !st.v.portRandom; st.portTest = null; markSaved('port'); rd(); },
      portTest: (a, e, rd) => { st.portTest = 'run'; rd(); B.laterS(st, 1200, (now) => { st.portTest = st.v.upnp ? 'ok' : 'err'; B.keep(now, ctx); }); },
      bgAck: (a, e, rd) => { st.confirm = { title: 'Descargar en segundo plano', body: 'Con la app cerrada los torrents siguen bajando y compartiéndose con otros pares. En Android se respetan Wi-Fi y batería. Puedes apagarlo cuando quieras.', btn: 'Entendido, activar', run: 'bgAck' }; rd(); },
      commitApply: (a) => { const b = a.dataset.arg; st.applied[b] = B.clone(st.draft[b]); st.test = null; B.snack(el, ctx, { proxy: 'Proxy aplicado. Las conexiones nuevas ya lo usan.', trackers: 'Rastreadores guardados.', linksrv: 'Link se reinició en el puerto ' + st.draft.linksrv.port + '.', tr: 'Conexión del motor guardada.' }[b]); },
      commitDiscard: (a, e, rd) => { const b = a.dataset.arg; st.draft[b] = B.clone(st.applied[b]); st.test = null; rd(); },
      commitTest: (a, e, rd) => { const b = a.dataset.arg; st.test = { block: b, ok: null }; rd(); B.laterS(st, 1100, (now) => { st.test = b === 'proxy' ? { block: b, ok: true, text: 'Funciona (84 ms)' } : { block: b, ok: false, text: 'Falta la clave de la API' }; B.keep(now, ctx); }); },
      capture: (a, e, rd) => { st.capture = a.dataset.arg; st.capKey = null; rd(); },
      capClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.capture = null; rd(); },
      capSave: (a) => { const k = st.capKey; st.keys[st.capture] = k; const l = (S.KEYS_BASE.concat(S.KEYS_DESK).find((x) => x[0] === st.capture) || [])[1]; st.capture = null; st.capKey = null; markSaved('atajos'); B.snack(el, ctx, `${l}: ahora con ${S.keyLabel(k)}.`); },
      keysReset: (a) => { const old = st.keys; st.keys = {}; B.snack(el, ctx, 'Atajos restaurados.', () => { st.keys = old; }); },
      folderPick: (a, e, rd) => { st.folderPick = a.dataset.arg; st.folderSel = null; rd(); },
      folderSel: (a, e, rd) => { st.folderSel = a.dataset.arg; rd(); },
      folderClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.folderPick = null; rd(); },
      folderOk: (a, e, rd) => { const k = st.folderPick; const p = st.folderSel || '/home/octavio/Videos/Anime'; st.folderPick = null; if (k.includes('.')) draftSet(k, p); else if (k === 'libDir') { B.snack(el, ctx, `Se vigila ${p}.`); return; } else { st.v[k] = p; markSaved(k); } rd(); },
      sheet: (a, e, rd) => { st.sheet = a.dataset.arg; rd(); },
      sheetClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.sheet = null; rd(); },
      subsSet: (a, e, rd) => { st.subs[a.dataset.k] = a.dataset.arg; if (a.dataset.arg === 'caja') st.subs.bg = 'caja'; rd(); },
      subsBg: (a, e, rd) => { st.subsLight = !st.subsLight; rd(); },
      subsReset: (a) => { const old = B.clone(st.subs); st.subs = null; B.snack(el, ctx, 'Estilo de subtítulos restablecido.', () => { st.subs = old; }); },
      logsClear: (a) => B.snack(el, ctx, 'Registros vaciados.'),
      confirmClose: (a, e, rd) => { if (e.target !== a && a.classList.contains('nj-overlay')) return; st.confirm = null; rd(); },
      confirmOk: (a) => { const c = st.confirm; st.confirm = null; st.typed = ''; if (c.run === 'bgAck') { st.v.bgAck = true; st.v.bg = true; markSaved('bg'); B.keep(el, ctx); return; } B.snack(el, ctx, c.done || 'Listo.'); },
      provMove: (a, e, rd) => { const i = +a.dataset.arg; const j = i + +a.dataset.d; const p = st.v.provs; [p[i], p[j]] = [p[j], p[i]]; markSaved('provs'); rd(); },
      provToggle: (a, e, rd) => { const i = +a.dataset.arg; const p = st.v.provs; p[i][1] = !p[i][1]; const it = p.splice(i, 1)[0]; if (it[1]) { const k = p.findIndex((x) => !x[1]); p.splice(k < 0 ? p.length : k, 0, it); } else p.push(it); markSaved('provs'); rd(); },
      goScreen: (a) => { if (NJ.screens[a.dataset.arg]) NJ.go(a.dataset.arg); else B.snack(el, ctx, 'Esa pantalla se diseña en otra parte del explorador.'); },
      specAct: (a, e, rd) => {
        const k = a.dataset.arg;
        const C = { recents: ['¿Borrar las búsquedas recientes?', 'Se quitan las 3 búsquedas guardadas en este equipo.', 'Borrar', 'Búsquedas recientes borradas.'], cacheClear: ['¿Vaciar la caché?', 'Se borran 1,2 GB de metadatos e imágenes. El historial y los torrents se quedan.', 'Vaciar caché', 'Caché vaciada (1,2 GB).'], histClear: ['¿Borrar el historial?', 'Se borra lo que viste en este equipo.', 'Borrar historial', 'Historial borrado.'], regen: ['¿Regenerar la identidad de Link?', 'Todos los teléfonos emparejados se olvidan; se guarda un respaldo de la identidad anterior.', 'Regenerar', 'Identidad nueva. Empareja de nuevo los teléfonos.'], reset: ['¿Restablecer Nijiyome?', 'Se borran ajustes, cuentas e historial y la app vuelve al primer arranque.', 'Restablecer', 'La app se reiniciaría ahora en el primer arranque.'], unlink: ['¿Desvincular AniList?', 'Los cambios pendientes se descartan. Tu lista local se queda.', 'Desvincular', 'AniList desvinculada.'], revoke: ['¿Revocar ' + D.link.phone + '?', 'El teléfono deja de poder conectarse hasta emparejarlo de nuevo.', 'Revocar', 'Teléfono revocado.'] }[k];
        if (C) { st.confirm = { title: C[0], body: C[1], btn: C[2], done: C[3], typed: k === 'reset' && !B.isA(ctx), check: k === 'histClear' ? 'También en la PC emparejada' : null }; rd(); return; }
        if (k === 'logs') { st.sheet = 'logs'; rd(); return; }
        if (k === 'debridAcc') { if (NJ.screens.transferencias) NJ.go('transferencias'); else B.snack(el, ctx, 'Las cuentas de debrid están en Transferencias.'); return; }
        if (k === 'upd') { st.updState = 'run'; rd(); B.laterS(st, 1000, (now) => { st.updState = 'ok'; B.keep(now, ctx); }); return; }
        const done = { syncNow: 'Listas sincronizadas.', linkSync: 'Historial sincronizado con ' + D.link.pc + '.', expJson: 'Lista exportada.', expCsv: 'Lista exportada como CSV.', histExport: 'Historial exportado.', browserTest: 'Chromium funciona: cargó la página de prueba en 1,8 s.', trTest: 'Prueba: «¡Hola! ¿A dónde vas?»', trCache: 'Caché de traducciones vaciada.', conflicts: 'Se conservaron tus 2 versiones.', mcacheClear: 'Caché de metadatos vaciada.' }[k];
        B.snack(el, ctx, done || 'En la app esto abre el selector del sistema.');
      },
    });
    const redraw = B.wire(el, ctx, H);
    let qt;
    el.addEventListener('input', (e) => {
      const t = e.target;
      if (t.dataset.input === 'q') { st.q = t.value; clearTimeout(qt); qt = setTimeout(() => { if (el.isConnected) redraw(); }, 160); return; }
      if (t.dataset.draft) { draftSet(t.dataset.draft, t.value); const blk = t.closest('.nj-b-group'); const was = blk && blk.querySelector('.nj-b-commitfoot.is-dirty'); if (!was) { clearTimeout(qt); qt = setTimeout(() => { if (el.isConnected) redraw(); }, 300); } return; }
      if (t.dataset.typed !== undefined) { st.typed = t.value; const ok = t.value.trim().toUpperCase() === 'RESET'; const b = el.querySelector('[data-act="confirmOk"]'); if (b) b.disabled = !ok; return; }
      if (t.dataset.stops) { const k = t.dataset.stops; const txt = S.fmtRate(S.RATE_STOPS[+t.value]); const o = el.querySelector(`[data-stopout="${k}"]`); if (o) o.textContent = txt; t.setAttribute('aria-valuetext', txt); return; }
      if (t.id === B.pid(ctx, 's-subsize') || t.id === B.pid(ctx, 's-subpos')) { const ln = el.querySelector('.nj-b-subline'); if (ln) ln.style.setProperty(t.id.endsWith('subsize') ? '--sz' : '--pos', t.id.endsWith('subsize') ? t.value / 100 : t.value); }
    });
    el.addEventListener('change', (e) => {
      const t = e.target; const id = t.id || '';
      const pre = B.pid(ctx, '');
      if (id.startsWith(pre + 't-')) { const k = id.slice((pre + 't-').length); if (k in st.v) { st.v[k] = t.checked; if (k === 'adult' && !t.checked) {} markSaved(k); redraw(); } else if (k === 'keepTor') st.keepTor = t.checked; return; }
      if (id.startsWith(pre + 'd-')) { draftSet(id.slice((pre + 'd-').length), t.checked); redraw(); return; }
      if (id.startsWith(pre + 's-')) { const k = id.slice((pre + 's-').length); if (k === 'subsize') { st.subs.size = +t.value; return; } if (k === 'subpos') { st.subs.pos = +t.value; return; } st.v[k] = +t.value; markSaved(k); redraw(); return; }
      if (t.dataset.stops) { st.v[t.dataset.stops] = +t.value; markSaved(t.dataset.stops); redraw(); }
    });
    /* Captura de teclas para atajos. */
    el.addEventListener('keydown', (e) => {
      if (!st.capture) return; if (e.key === 'Escape') return; if (['Shift', 'Control', 'Alt'].includes(e.key)) return;
      e.preventDefault(); e.stopPropagation(); st.capKey = e.key.length === 1 ? e.key.toLowerCase() : e.key; redraw();
    }, true);
    if (st.capture) { const d = el.querySelector('#' + CSS.escape(B.pid(ctx, 'capdlg'))); if (d) d.focus({ preventScroll: true }); }
    /* Franjas horarias: tocar o arrastrar pinta. */
    let paint = null;
    el.addEventListener('pointerdown', (e) => { const s = e.target.closest('[data-slot]'); if (!s) return; e.preventDefault(); const i = +s.dataset.slot; paint = st.v.slots[i] ? 0 : 1; st.v.slots[i] = paint; s.classList.toggle('is-on', !!paint); s.setAttribute('aria-pressed', !!paint); });
    el.addEventListener('pointerover', (e) => { if (paint == null) return; const s = e.target.closest('[data-slot]'); if (!s) return; const i = +s.dataset.slot; st.v.slots[i] = paint; s.classList.toggle('is-on', !!paint); s.setAttribute('aria-pressed', !!paint); });
    const endPaint = () => { if (paint == null) return; paint = null; markSaved('slots'); redraw(); };
    el.addEventListener('pointerup', endPaint); el.addEventListener('pointerleave', endPaint);
    el.addEventListener('click', (e) => { const s = e.target.closest('[data-slot]'); if (s && e.detail === 0) { const i = +s.dataset.slot; st.v.slots[i] = st.v.slots[i] ? 0 : 1; markSaved('slots'); redraw(); } });
    /* Salto al ajuste exacto: desplaza y resalta. */
    if (st.flash) {
      const row = el.querySelector('#' + CSS.escape(B.pid(ctx, 'row-' + st.flash))) || el.querySelector('#' + CSS.escape(B.pid(ctx, st.flash)));
      if (row) row.classList.add('is-flash');
      if (row && st._jump) { st._jump = false; const sc = row.closest('[data-keep="det"]') || el; const top = row.getBoundingClientRect().top - sc.getBoundingClientRect().top + sc.scrollTop - 96; sc.scrollTo({ top: Math.max(0, top), behavior: ctx.motion === 'full' ? 'smooth' : 'auto' }); const f = row.querySelector('input, button'); if (f) f.focus({ preventScroll: true }); }
      const key = st.flash; B.laterS(st, 2600, (now) => { if (st.flash === key) { st.flash = null; const r = now.querySelector('.is-flash'); if (r) r.classList.remove('is-flash'); } });
    }
  }

  NJ.registerScreen({
    id: 'ajustes', name: 'Ajustes', group: 'Configurar', order: 1, platforms: ['desktop', 'android'],
    summary: 'Catorce categorías con nombre de tarea; la búsqueda salta al ajuste exacto y lo resalta en vez de mostrar un aviso; puertos, carpetas, horas y teclas se eligen con selectores propios, y todo lo que pide Aplicar se ve distinto de lo que se guarda al tocar.',
    parity: 'SET-00, SET-01, SET-02 (Ctrl P), SET-03, SET-04 (ahora resalta la fila), SET-APP-01 a SET-APP-21, SET-PLY-01 a SET-PLY-36, SET-PLY-FG, SET-PLY-SUBS (hoja con vista previa; el detalle completo queda anotado en la hoja), SET-PLY-SRC, SET-MET-01 a 05 y SET-MET-DISC (orden y encendido en una sola lista), SET-ACC-01 a 07, SET-LNK, SET-LNKSRV (los dos servidores separados), SET-NET (dentro de Sistema), SET-TOR, TOR-25, TOR-26, SET-DL, DL-14, DL-15, SET-RDR-MANGA, SET-RDR-NOVEL, SET-RDR-TTS, SET-DAT (las opciones de escritorio pasan a Sistema), SET-ABT. Las notificaciones salen de Metadatos a su propia categoría.',
    structures: [
      { id: 'lista-detalle', name: 'Lista y detalle', desc: 'Categorías con resumen en vivo a la izquierda y el detalle a la derecha; en Android, lista y luego pantalla.' },
      { id: 'buscador', name: 'Búsqueda primero', desc: 'El buscador manda: los resultados saltan al ajuste exacto y lo resaltan.' },
    ],
    render: renderSet, mount: mountSet,
  });
})();

/* ======================================================================
   5. PERFIL (escritorio: perfil de AniList) y TÚ (Android: hub)
   ====================================================================== */
(function () {
  const NJ = window.NJ; if (!NJ || !NJ.B) return;
  const D = NJ.data, h = NJ.h, esc = h.esc, B = NJ.B, ic = B.ic;
  const USER = 'Kushro';
  const ACTS0 = [
    { id: 'a1', verb: 'Vio el episodio 8 de', media: 'aonohako', ago: 'hace 2 h', likes: 2, liked: false, replies: ['Seguidor de ejemplo 1: ¡ese final!'] },
    { id: 'a2', verb: 'Completó', media: 'revo', ago: 'ayer', likes: 0, liked: false, replies: [] },
    { id: 'a3', verb: 'Agregó a Pendiente', media: 'edgerunners', ago: 'hace 2 d', likes: 1, liked: false, replies: [] },
    { id: 'a4', verb: 'Vio el episodio 4 de', media: 'tempal', ago: 'hace 3 d', likes: 0, liked: false, replies: [] },
  ];
  const NOTIF0 = [
    { id: 'n1', icon: 'live_tv', text: 'Salió el episodio 9 de Ao no Hako 2', media: 'aonohako', ago: 'hace 5 min', unread: true },
    { id: 'n2', icon: 'favorite', text: 'A Seguidor de ejemplo 1 le gustó tu actividad', ago: 'hace 1 h', unread: true },
    { id: 'n3', icon: 'chat_bubble', text: 'Seguidor de ejemplo 1 respondió a tu actividad', ago: 'hace 1 h', unread: true },
    { id: 'n4', icon: 'person_add', text: 'Seguidor de ejemplo 2 empezó a seguirte', ago: 'ayer', unread: false },
    { id: 'n5', icon: 'live_tv', text: 'Salió el episodio 7 de Tempal', media: 'tempal', ago: 'hace 2 d', unread: false },
  ];
  const pDefaults = { tab: 'actividad', view: null, draft: '', onlyFollow: false, acts: ACTS0, notif: NOTIF0, open: null, reply: '', refreshing: false };

  function stats() {
    const ts = D.titles.filter((t) => t.status); const eps = ts.reduce((n, t) => n + (t.progress || 0), 0);
    const scored = ts.filter((t) => t.score && ['watching', 'completed'].includes(t.status));
    const mean = scored.reduce((n, t) => n + t.score, 0) / scored.length;
    const g = {}; ts.forEach((t) => t.genres.forEach((x) => { g[x] = (g[x] || 0) + 1; }));
    const top = Object.entries(g).sort((a, b) => b[1] - a[1]).slice(0, 6);
    return { anime: ts.length, eps, days: (eps * 24 / 1440).toFixed(1).replace('.', ','), mean: Math.round(mean * 10), manga: D.manga.length, chapters: D.manga.reduce((n, m) => n + parseInt(m.chapter.replace(/\D/g, ''), 10), 0).toLocaleString('es'), top, max: top[0][1] };
  }
  function actCard(ctx, st, a) {
    const t = D.byId[a.media]; const open = st.open === a.id;
    return `<article class="nj-b-act"><span class="nj-b-avatar is-img" aria-hidden="true"><img src="img/p-psyren.webp" alt="" width="40" height="40"></span><div class="nj-grow nj-stack" style="gap:6px"><p class="nj-b-acttext"><b>${esc(a.self ? USER : USER)}</b> ${esc(a.verb)} ${t ? `<button type="button" class="nj-b-linkbtn" data-act="ficha">${esc(t.short)}</button>` : ''}${a.text ? `<span class="nj-b-status-text">${esc(a.text)}</span>` : ''}</p><span class="nj-meta">${esc(a.ago)}</span><div class="nj-cluster nj-b-actacts"><button type="button" class="nj-btn is-text nj-b-small${a.liked ? ' is-on' : ''}" data-act="like" data-arg="${a.id}" aria-pressed="${a.liked}">${ic('favorite', { size: 18, fill: a.liked })}${a.likes + (a.liked ? 1 : 0)}</button><button type="button" class="nj-btn is-text nj-b-small" data-act="thread" data-arg="${a.id}" aria-expanded="${open}">${ic('chat_bubble', { size: 18 })}${a.replies.length} ${a.replies.length === 1 ? 'respuesta' : 'respuestas'}</button></div>${open ? `<div class="nj-b-thread">${a.replies.length ? `<ul>${a.replies.map((r) => `<li>${esc(r)}</li>`).join('')}</ul>` : '<p class="nj-meta">Sin respuestas. Sé la primera persona en responder.</p>'}<div class="nj-b-composer is-small"><label class="nj-field"><input id="${B.pid(ctx, 'reply-' + a.id)}" data-input="reply" placeholder="Escribe una respuesta…" aria-label="Escribe una respuesta" value="${esc(st.reply)}"></label>${h.iconBtn('send', 'Responder', { kind: 'filled', attrs: { 'data-act': 'replySend', 'data-arg': a.id, disabled: !st.reply.trim() } })}</div></div>` : ''}</div></article>`;
  }
  function tabBody(ctx, st) {
    const A = B.isA(ctx);
    if (st.tab === 'actividad') {
      return `<div class="nj-b-composer"><label class="nj-b-field nj-grow"><span class="nj-b-vh">Escribe un estado</span><textarea id="${B.pid(ctx, 'status')}" data-input="draft" class="nj-b-textarea" rows="2" placeholder="Escribe un estado…">${esc(st.draft)}</textarea></label>${h.btn('Publicar', { kind: 'filled', icon: 'send', attrs: { 'data-act': 'post', disabled: !st.draft.trim() } })}</div><div class="nj-cluster"><button type="button" class="nj-chip" data-act="onlyFollow" aria-pressed="${st.onlyFollow}">${st.onlyFollow ? ic('check', { size: 18 }) : ''}Solo gente que sigo</button></div>${st.onlyFollow ? B.emptyState('group', 'Nadie que sigues publicó esta semana', 'Quita el filtro para ver tu propia actividad.', h.btn('Ver toda la actividad', { kind: 'tonal', attrs: { 'data-act': 'onlyFollow' } })) : `<div class="nj-b-acts">${st.acts.map((a) => actCard(ctx, st, a)).join('')}</div>${h.btn('Cargar más', { kind: 'outline', attrs: { 'data-act': 'more' } })}`}`;
    }
    if (st.tab === 'estadisticas') {
      const s = stats();
      const tiles = [['Anime', s.anime], ['Episodios', s.eps], ['Días vistos', s.days], ['Puntuación media', s.mean], ['Manga', s.manga], ['Capítulos', s.chapters]];
      return `<p class="nj-meta">Calculado con la lista de ejemplo de esta maqueta.</p><dl class="nj-b-stats">${tiles.map(([l, v]) => `<div><dt>${l}</dt><dd class="nj-b-tnum">${v}</dd></div>`).join('')}</dl><section class="nj-b-sec"><h3 class="nj-h3">Géneros que más ves</h3><ul class="nj-b-bars">${s.top.map(([g, n], i) => `<li><span>${esc(g)}</span><span class="nj-b-bar"><i style="--w:${(n / s.max).toFixed(3)};--i:${i}"></i></span><b class="nj-b-tnum">${n}</b></li>`).join('')}</ul></section>`;
    }
    if (st.tab === 'favoritos') {
      const fav = ['aonohako', 'koori', 'mahoyo', 'chitose'].map((id) => D.byId[id]);
      return `<section class="nj-b-sec"><h3 class="nj-h3">Anime</h3><div class="nj-grid" style="--nj-cell:${A ? 100 : 120}px" data-region="grid">${fav.map((t) => h.card(t, { size: 's', context: 'grid' })).join('')}</div></section><section class="nj-b-sec"><h3 class="nj-h3">Manga</h3><div class="nj-grid" style="--nj-cell:${A ? 100 : 120}px">${D.manga.slice(0, 3).map((m) => h.card({ id: m.id, title: m.title, poster: m.poster, format: 'Manga', year: m.chapter }, { size: 's', context: 'grid' })).join('')}</div></section><section class="nj-b-sec"><h3 class="nj-h3">Personajes</h3><div class="nj-cluster"><span class="nj-b-favchar"><span class="nj-b-avatar" aria-hidden="true">L</span>Lily Flambert</span></div></section>`;
    }
    const mutual = st.tab === 'seguidores';
    return `<ul class="nj-list nj-b-users">${[1, 2, 3].map((n) => `<li class="nj-b-userrow"><button type="button" class="nj-list-row" data-act="userOpen"><span class="nj-b-avatar" aria-hidden="true">S</span><span class="nj-grow"><span class="nj-b-rowtitle">${mutual ? 'Seguidor' : 'Cuenta seguida'} de ejemplo ${n}</span><span class="nj-meta">${n === 1 ? 'Se siguen mutuamente' : mutual ? 'Te sigue' : 'La sigues'}</span></span></button>${mutual && n > 1 ? h.btn('Seguir también', { kind: 'tonal', attrs: { 'data-act': 'followBack' } }) : ''}</li>`).join('')}</ul>`;
  }
  function profileHead(ctx, st) {
    const A = B.isA(ctx);
    return `<div class="nj-b-banner-img" style="background-image:url(img/hero-sbr.webp)" role="img" aria-label="Portada del perfil (imagen de ejemplo)"></div><div class="nj-b-profhead"><span class="nj-b-avatar is-img is-xl" aria-hidden="true"><img src="img/p-psyren.webp" alt="" width="96" height="96"></span><div class="nj-grow nj-stack" style="gap:4px"><h1 class="nj-b-title">${USER}</h1><div class="nj-cluster nj-b-follows"><button type="button" class="nj-b-linkbtn" data-act="tab" data-arg="seguidores"><b class="nj-b-tnum">21</b> seguidores</button><button type="button" class="nj-b-linkbtn" data-act="tab" data-arg="siguiendo"><b class="nj-b-tnum">24</b> siguiendo</button></div></div><div class="nj-cluster">${st.refreshing ? '<span class="nj-b-spin" aria-label="Actualizando"></span>' : h.iconBtn('refresh', 'Actualizar', { attrs: { 'data-act': 'refresh' } })}${h.iconBtn('open_in_new', 'Abrir en AniList', { attrs: { 'data-act': 'web' } })}${A ? '' : h.btn('Editar perfil', { kind: 'outline', icon: 'edit', attrs: { 'data-act': 'web' } })}</div></div><p class="nj-b-bio">La biografía de AniList se muestra aquí con su formato, recortada a cuatro líneas.</p><div class="nj-tabs" role="tablist" data-local>${[['actividad', 'Actividad'], ['estadisticas', 'Estadísticas'], ['favoritos', 'Favoritos'], ['seguidores', 'Seguidores'], ['siguiendo', 'Siguiendo']].map(([id, l]) => `<button type="button" role="tab" class="nj-tab" aria-selected="${st.tab === id}" data-act="tab" data-arg="${id}">${l}</button>`).join('')}</div>`;
  }
  function notifList(ctx, st) {
    const unread = st.notif.filter((n) => n.unread).length;
    return `<div class="nj-section-head nj-b-notifhead"><h2 class="nj-h3">Notificaciones${unread ? ` <span class="nj-badge nj-b-tnum">${unread}</span>` : ''}</h2>${unread ? h.btn('Marcar todo como leído', { kind: 'text', attrs: { 'data-act': 'readAll' } }) : ''}</div>${B.isA(ctx) ? `<div class="nj-b-permcard">${ic('notifications_active')}<span class="nj-grow">Recibes un resumen cada 15 minutos.</span><span class="nj-badge nj-b-okpill">Permitido</span></div>` : ''}<ul class="nj-list nj-b-notifs">${st.notif.map((n) => `<li><button type="button" class="nj-list-row${n.unread ? ' is-unread' : ''}" data-act="notifOpen" data-arg="${n.id}">${n.media ? `<img src="${esc(D.byId[n.media].poster)}" alt="" width="36" height="54" loading="lazy">` : `<span class="nj-b-avatar" aria-hidden="true">${ic(n.icon, { size: 20 })}</span>`}<span class="nj-grow"><span class="nj-b-rowtitle">${esc(n.text)}</span><span class="nj-meta">${esc(n.ago)}</span></span>${n.unread ? '<span class="nj-b-unread"><span class="nj-b-vh">Sin leer</span></span>' : ''}</button></li>`).join('')}</ul>`;
  }
  function hub(ctx, st) {
    const unread = st.notif.filter((n) => n.unread).length;
    const row = (icon, t, sub, act, arg, badge) => `<button type="button" class="nj-list-row nj-b-hubrow" data-act="${act}" data-arg="${arg}"><span class="nj-b-hubic">${ic(icon)}</span><span class="nj-grow"><span class="nj-b-rowtitle">${esc(t)}</span><span class="nj-meta">${esc(sub)}</span></span>${badge ? `<span class="nj-badge nj-b-tnum">${badge}</span>` : ''}${ic('chevron_right')}</button>`;
    return `${B.appbar('Tú', '')}<div class="nj-b-pad nj-stack nj-b-gap-l"><section class="nj-b-account"><div class="nj-b-acchead"><span class="nj-b-avatar is-img is-l" aria-hidden="true"><img src="img/p-psyren.webp" alt="" width="56" height="56"></span><div class="nj-grow"><b class="nj-b-accname">${USER}</b><span class="nj-meta">AniList · historial sincronizado con ${esc(D.link.pc)} ${st.synced || 'hace 3 min'}</span></div></div><div class="nj-b-accacts">${st.sync === 'run' ? '<span class="nj-b-syncing"><span class="nj-b-spin" aria-hidden="true"></span>Sincronizando…</span>' : h.btn('Sincronizar ahora', { kind: 'tonal', icon: 'sync', attrs: { 'data-act': 'sync' } })}${h.btn('Listas y cuentas', { kind: 'outline', icon: 'manage_accounts', attrs: { 'data-act': 'accounts' } })}</div></section>
      <section class="nj-b-hubgroup"><h2 class="nj-b-hubtitle">Leer y descubrir</h2><div class="nj-list">${row('menu_book', 'Manga', 'Tendencias, lista de lectura y capítulos', 'hubGo', 'manga')}${row('auto_stories', 'Novelas', 'Tendencias, lista de lectura y narrador', 'hubGo', 'novelas')}${row('recommend', 'Para ti', 'Recomendaciones y Atlas de tus gustos', 'hubGo', 'parati')}</div></section>
      <section class="nj-b-hubgroup"><h2 class="nj-b-hubtitle">Tu actividad</h2><div class="nj-list">${row('account_circle', 'Perfil de AniList', 'Actividad, estadísticas, favoritos y seguidores', 'view', 'perfil')}${row('notifications', 'Notificaciones', 'Episodios, seguidores, me gusta y respuestas', 'view', 'notif', unread || '')}${row('history', 'Historial', '6 episodios · también lo visto en la PC', 'hubGo', 'historial')}</div></section>
      <section class="nj-b-hubgroup"><h2 class="nj-b-hubtitle">Equipo y fuentes</h2><div class="nj-list">${row('swap_vert', 'Transferencias', '1 torrent bajando · 1 en cola', 'hubGo', 'transferencias', '1')}${row('extension', 'Fuentes', '3 extensiones · 1 actualización', 'hubGo', 'fuentes')}${row('devices', 'Nijiyome Link', `${D.link.pc} · conectado`, 'hubGo', 'link')}${row('settings', 'Ajustes', 'Apariencia, reproductor, torrents', 'hubGo', 'ajustes')}</div></section></div>`;
  }

  function renderPerfil(ctx) {
    const st = B.defaults(ctx.state, pDefaults); const A = B.isA(ctx);
    if (A) {
      let body;
      if (st.view === 'perfil') body = `${B.appbar('Perfil de AniList', '', { back: 'back' })}<div class="nj-b-prof is-phone">${profileHead(ctx, st)}<div class="nj-b-pad nj-stack">${tabBody(ctx, st)}</div></div>`;
      else if (st.view === 'notif') body = `${B.appbar('Notificaciones', h.iconBtn('refresh', 'Actualizar', { attrs: { 'data-act': 'refresh' } }), { back: 'back' })}<div class="nj-b-pad">${notifList(ctx, st)}</div>`;
      else body = hub(ctx, st);
      return `<div class="nj-b-perfil">${body}${B.snackHtml(st)}</div>`;
    }
    return `<div class="nj-b-page nj-b-perfil"><div class="nj-b-profgrid"><div class="nj-b-prof">${profileHead(ctx, st)}<div class="nj-stack nj-b-tabbody">${tabBody(ctx, st)}</div></div><aside class="nj-b-panel nj-b-notifpanel">${notifList(ctx, st)}</aside></div>${B.snackHtml(st)}</div>`;
  }
  function mountPerfil(el, ctx) {
    const st = ctx.state;
    const go = (ids) => { const id = ids.find((x) => NJ.screens[x]); if (id) NJ.go(id); else B.snack(el, ctx, 'Esa pantalla se diseña en otra parte del explorador.'); };
    const H = Object.assign(B.snackHandlers(el, ctx), {
      tab: (a, e, rd) => { st.tab = a.dataset.arg; rd(); },
      view: (a, e, rd) => { st.view = a.dataset.arg; ctx.rerender(); },
      back: (a, e, rd) => { st.view = null; ctx.rerender(); },
      post: (a) => { const txt = st.draft.trim(); st.acts = [{ id: 'p' + Date.now(), verb: 'publicó un estado', text: txt, ago: 'ahora', likes: 0, liked: false, replies: [] }].concat(st.acts); st.draft = ''; B.snack(el, ctx, 'Estado publicado en AniList.'); },
      onlyFollow: (a, e, rd) => { st.onlyFollow = !st.onlyFollow; rd(); },
      like: (a, e, rd) => { const x = st.acts.find((y) => y.id === a.dataset.arg); x.liked = !x.liked; rd(); },
      thread: (a, e, rd) => { st.open = st.open === a.dataset.arg ? null : a.dataset.arg; st.reply = ''; rd(); },
      replySend: (a, e, rd) => { const x = st.acts.find((y) => y.id === a.dataset.arg); x.replies = x.replies.concat([`${USER}: ${st.reply.trim()}`]); st.reply = ''; rd(); },
      more: (a) => B.snack(el, ctx, 'No hay más actividad de ejemplo.'),
      readAll: (a, e, rd) => { st.notif = st.notif.map((n) => Object.assign({}, n, { unread: false })); rd(); },
      notifOpen: (a, e, rd) => { const n = st.notif.find((x) => x.id === a.dataset.arg); n.unread = false; if (n.media && NJ.screens.ficha) { NJ.go('ficha'); return; } rd(); },
      refresh: (a, e, rd) => { st.refreshing = true; rd(); B.laterS(st, 900, (now) => { st.refreshing = false; B.keep(now, ctx); }); },
      web: () => B.snack(el, ctx, 'Se abriría AniList en el navegador.'),
      userOpen: () => B.snack(el, ctx, 'Abre el perfil de esa cuenta con la misma pantalla.'),
      followBack: () => B.snack(el, ctx, 'Ahora se siguen mutuamente.'),
      ficha: () => { if (NJ.screens.ficha) NJ.go('ficha'); },
      sync: (a, e, rd) => { st.sync = 'run'; rd(); B.laterS(st, 1200, (now) => { st.sync = 'idle'; st.synced = 'hace un momento'; B.snack(now, ctx, 'Historial sincronizado.'); }); },
      accounts: () => { const s = NJ.screenState['ajustes:' + ctx.platform] = NJ.screenState['ajustes:' + ctx.platform] || {}; s.cat = 'cuentas'; s.open = true; NJ.go('ajustes'); },
      hubGo: (a) => {
        const k = a.dataset.arg;
        if (k === 'parati') { NJ.go('explorar', { structure: 'lienzo' }); return; }
        if (k === 'historial') { const s = NJ.screenState['biblioteca:' + ctx.platform] = NJ.screenState['biblioteca:' + ctx.platform] || {}; s.tab = 'historial'; NJ.go('biblioteca'); return; }
        go({ manga: ['manga', 'lector'], novelas: ['novelas', 'lector'], transferencias: ['transferencias'], fuentes: ['fuentes'], link: ['link'], ajustes: ['ajustes'] }[k] || [k]);
      },
    });
    const redraw = B.wire(el, ctx, H);
    el.addEventListener('input', (e) => {
      const k = e.target.dataset.input; if (!k) return; const before = !st[k].trim(); st[k] = e.target.value;
      if (before !== !st[k].trim()) redraw();
    });
  }

  NJ.registerScreen({
    id: 'perfil', name: 'Perfil y Tú', group: 'Tu colección', order: 2, platforms: ['desktop', 'android'],
    summary: 'En Android, Tú se ordena en tres grupos con título y cada fila dice su estado; en escritorio el perfil de AniList usa pestañas reales, publica estados y deja las notificaciones a la vista en una columna.',
    parity: 'YOU-01 (cuenta, Sincronizar ahora, Listas y cuentas, Para ti, Manga, Novelas, Perfil de AniList, Notificaciones, Historial, Transferencias en lugar de Descargas y Torrents, Fuentes, Link, Ajustes), SOC-01 (portada, seguidores y siguiendo, actividad con Escribe un estado, Solo gente que sigo, me gusta, respuestas, estadísticas, favoritos, actualizar, abrir en AniList, editar perfil), SOC-02 (respuestas en línea), SOC-03 (lista, sin leer, permiso en Android, actualizar).',
    structures: [{ id: 'base', name: 'Hub y perfil', desc: 'Android: hub Tú. Escritorio: perfil de AniList con notificaciones.' }],
    render: renderPerfil, mount: mountPerfil,
  });
})();
