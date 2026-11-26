/* Pantallas del agente S1: Inicio, Ficha, Fuentes del episodio y Reproductor. Ver CONTRATO.md.
   Todo el estado efímero vive en ctx.state; los eventos se enganchan dentro de cada marco. */
(function () {
  'use strict';
  const NJ = window.NJ;
  if (!NJ || !NJ.registerScreen) return;
  const D = NJ.data, h = NJ.h, esc = h.esc;
  const T = (id) => D.byId[id];
  const ic = (n, o) => h.icon(n, o);
  const isA = (ctx) => ctx.platform === 'android';
  const pid = (ctx, id) => `${ctx.platform[0]}-${id}`;

  /* ---------- utilidades comunes ---------- */
  function save(el, ctx) {
    const s = {};
    el.querySelectorAll('[data-keep]').forEach((n) => { s[n.dataset.keep] = [n.scrollTop, n.scrollLeft]; });
    ctx.state._scroll = s;
    const f = document.activeElement;
    ctx.state._focus = f && el.contains(f) ? (f.dataset.fk ? { fk: f.dataset.fk } : f.dataset.act ? { act: f.dataset.act, v: f.dataset.v || f.dataset.n || '' } : null) : null;
  }
  function restore(el, ctx) {
    const s = ctx.state._scroll || {};
    el.querySelectorAll('[data-keep]').forEach((n) => { const v = s[n.dataset.keep]; if (v) { n.scrollTop = v[0]; n.scrollLeft = v[1]; } });
    const f = ctx.state._focus;
    if (!f) return;
    let t = null;
    if (f.fk) t = el.querySelector(`[data-fk="${f.fk}"]`);
    else t = [...el.querySelectorAll(`[data-act="${f.act}"]`)].find((n) => (n.dataset.v || n.dataset.n || '') === f.v);
    if (t) { try { t.focus({ preventScroll: true }); if (t.setSelectionRange && t.value) t.setSelectionRange(t.value.length, t.value.length); } catch (e) { /* sin foco */ } }
    ctx.state._focus = null;
  }
  function rr(el, ctx) { save(el, ctx); ctx.rerender(); }

  /* Un solo manejador por marco: data-act, segmentados [data-seg], chips [data-chips], interruptores y rangos. */
  function wire(el, ctx, H) {
    restore(el, ctx);
    el.addEventListener('click', (ev) => {
      const t = ev.target;
      if (H._pre && H._pre(t, ev) === false) return;
      const r = t.closest('[data-seg] [role="radio"]');
      if (r && el.contains(r) && H.seg) { ev.preventDefault(); ev.stopPropagation(); if (r.disabled) return; H.seg(r.closest('[data-seg]').dataset.seg, r.dataset.value, r, ev); return; }
      const c = t.closest('[data-chips] .nj-chip');
      if (c && el.contains(c) && H.chip) { ev.preventDefault(); ev.stopPropagation(); if (c.disabled) return; H.chip(c.closest('[data-chips]').dataset.chips, c.dataset.value, c, ev); return; }
      const a = t.closest('[data-act]');
      if (a && el.contains(a) && H[a.dataset.act]) {
        ev.preventDefault(); ev.stopPropagation();
        if (a.disabled || a.getAttribute('aria-disabled') === 'true') return;
        H[a.dataset.act](a, ev);
      }
    });
    el.addEventListener('change', (ev) => {
      const i = ev.target;
      if (i.matches('input[role="switch"]') && H.sw) H.sw(i.id.slice(2), i.checked, i);
      else if (i.type === 'range' && H.range) H.range(i.id.slice(2), Number(i.value), i, true);
    });
    el.addEventListener('input', (ev) => {
      const i = ev.target;
      if (i.type === 'range' && H.range) H.range(i.id.slice(2), Number(i.value), i, false);
      else if (i.matches('[data-fk]') && H.text) H.text(i.dataset.fk, i.value, i);
    });
  }

  const sw = (ctx, id, label, on, desc) => h.toggle(pid(ctx, id), label, on, { desc });
  const rng = (ctx, id, label, o) => h.slider(pid(ctx, id), label, o);
  const seg = (name, label, opts, sel) => `<div data-seg="${name}" data-local>${h.seg(label, opts, sel)}</div>`;
  const chips = (name, label, opts, sel, multi) => `<div data-chips="${name}" data-local>${h.chips(label, opts, sel, { multi })}</div>`;
  const B = (label, kind, act, o = {}) => h.btn(label, { kind, icon: o.icon, attrs: Object.assign({ 'data-act': act }, o.v != null ? { 'data-v': o.v } : {}, o.attrs || {}) });
  const IB = (icon, label, act, o = {}) => h.iconBtn(icon, label, { kind: o.kind, fill: o.fill, attrs: Object.assign(act ? { 'data-act': act } : {}, o.v != null ? { 'data-v': o.v } : {}, o.attrs || {}) });

  function snack(ctx) {
    const s = ctx.state.snack;
    if (!s) return '';
    return `<div class="nj-snackbar nj-a-snack" role="status" aria-live="polite"><span class="nj-a-grow">${esc(s.msg)}</span>${s.go ? `<button type="button" class="nj-btn is-text" data-go="${s.go}">${esc(s.goLabel)}</button>` : ''}${s.undo ? `<button type="button" class="nj-btn is-text" data-act="undo">Deshacer</button>` : ''}${IB('close', 'Cerrar aviso', 'snack-x')}${s.undo ? '<i class="nj-a-snack-life" aria-hidden="true"></i>' : ''}</div>`;
  }
  function dialog(title, body, actions, o = {}) {
    return `<div class="nj-overlay nj-a-dlgwrap" data-act="${o.close || 'dlg-x'}"><div class="nj-dialog nj-a-dlg" role="dialog" aria-modal="true" aria-label="${esc(title)}" data-act="dlg-stop"><h2 class="nj-h2">${esc(title)}</h2>${body}<div class="nj-a-dlg-actions">${actions}</div></div></div>`;
  }
  const head = (title, extra = '', o = {}) => `<div class="nj-section-head nj-a-head">${o.pre || ''}<h2 class="nj-h2"${o.id ? ` id="${o.id}"` : ''}>${esc(title)}</h2>${o.sub ? `<span class="nj-meta">${esc(o.sub)}</span>` : ''}<span class="nj-a-grow"></span>${extra}</div>`;
  const seeAll = (go, label = 'Ver todo') => `<button type="button" class="nj-btn is-text"${go ? ` data-go="${go}"` : ''}>${esc(label)}</button>`;
  const meter = (v, label) => `<span class="nj-a-meter" role="img" aria-label="${esc(label)}" style="--v:${v}"><i></i></span>`;

  /* ======================================================================
     INICIO
     ====================================================================== */
  const NOW = 21 * 60 + 4;
  const mins = (t) => { const [a, b] = t.split(':').map(Number); return a * 60 + b; };
  const TONIGHT = [
    { id: 'tempal', icon: 'play_circle', label: 'Sigue donde quedaste' },
    { id: 'aonohako', icon: 'new_releases', label: 'Ep. 9 salió a las 21:00' },
    { id: 'koori', icon: 'new_releases', label: 'Ep. 9 salió hoy · te faltan 2' },
    { id: 'chitose', icon: 'new_releases', label: 'Ep. 8 salió hoy · te faltan 2' },
    { id: 'magical', icon: 'new_releases', label: 'Ep. 10 salió hoy · te faltan 2' },
    { id: 'psyren', icon: 'schedule', label: 'Ep. 9 sale a las 23:30' },
  ];
  const HEROES = [
    { id: 'laidoff', img: D.heroes.laidoff, title: T('laidoff').title, facts: ['12 episodios', 'En emisión', 'Otoño 2026', 'Puntuación 7,4'], genres: T('laidoff').genres, syn: 'Rein, un encantador, es expulsado de su gremio de un día para otro. En vez de desesperarse, declara que ahora puede hacer lo que quiera y empieza a recuperar todas las mejoras que repartió.', cta: 'Continuar · Ep. 2', list: 'watching' },
    { id: 'sbr', img: D.heroes.sbr, title: 'JoJo no Kimyou na Bouken: Steel Ball Run', facts: ['11 episodios', 'ONA', 'En emisión', 'Otoño 2026'], genres: ['Acción', 'Aventura', 'Drama', 'Misterio', 'Sobrenatural'], syn: 'Una carrera a caballo que cruza Norteamérica de costa a costa. Johnny Joestar y Gyro Zeppeli compiten por el premio y por algo más.', cta: 'Ver Ep. 1' },
    { id: 'onepiece', img: D.heroes.onepiece, title: 'One Piece', facts: ['En emisión', 'Desde 1999', 'Puntuación 8,7'], genres: ['Acción', 'Aventura', 'Comedia', 'Drama'], syn: 'Luffy y su tripulación siguen su viaje por el Grand Line en busca del One Piece.', cta: 'Ver Ep. 1' },
  ];
  const TOP = {
    hoy: ['aonohako', 'koori', 'chitose', 'mahoyo', 'dawang', 'laidoff', 'horegusuri', 'psyren', 'kikansha', 'tempal'],
    semana: ['mahoyo', 'aonohako', 'koori', 'dawang', 'chitose', 'psyren', 'laidoff', 'kikansha', 'horegusuri', 'seitokai'],
    mes: ['mahoyo', 'aonohako', 'dawang', 'koori', 'kikansha', 'chitose', 'psyren', 'horegusuri', 'laidoff', 'revo'],
  };
  const FEEDS = {
    emision: { label: 'En emisión', ids: ['aonohako', 'koori', 'dawang', 'psyren', 'chitose', 'horegusuri', 'laidoff', 'seitokai'] },
    temporada: { label: 'Esta temporada', ids: ['laidoff', 'tsuihou', 'goblin', 'shinja', 'mouse', 'kanojo', 'mezametara', 'tempal'] },
    populares: { label: 'Populares', ids: ['aonohako', 'chitose', 'koori', 'mahoyo', 'kikansha', 'dawang', 'revo', 'psyren'] },
    mejores: { label: 'Mejor puntuados', ids: ['aonohako', 'mahoyo', 'koori', 'chitose', 'dawang', 'horegusuri', 'laidoff', 'psyren'] },
  };
  const GENRES = ['Acción', 'Aventura', 'Comedia', 'Drama', 'Fantasía', 'Romance', 'Misterio', 'Ciencia ficción', 'Deportes', 'Recuentos de la vida', 'Sobrenatural', 'Isekai'];
  const SHELVES = [
    { id: 'continuar', name: 'Esta noche', kind: 'continue' },
    { id: 'descargando', name: 'Descargando', kind: 'downloads' },
    { id: 'siguiendo', name: 'Novedades de lo que sigues', ids: ['laidoff', 'chitose', 'aonohako', 'koori', 'magical', 'horegusuri', 'psyren'], show: { next: true, status: true }, go: 'calendario' },
    { id: 'ultimos', name: 'Últimos episodios', ids: ['dawang', 'seitokai', 'magical', 'tsuihou', 'kanojo', 'goblin', 'shinja', 'mouse'], gate: 'magical', go: 'calendario' },
    { id: 'top', name: 'Top 10', kind: 'top' },
    { id: 'parati', name: 'Para ti', ids: ['mahoyo', 'koori', 'kikansha', 'revo', 'mezametara', 'edgerunners', 'choujun'], go: 'explorar' },
    { id: 'porque', name: 'Porque viste Tempal', ids: ['tsuihou', 'goblin', 'shinja', 'revo', 'kikansha', 'laidoff'], go: 'explorar' },
    { id: 'generos', name: 'Géneros', kind: 'genres' },
    { id: 'feeds', name: 'Catálogo', kind: 'feeds' },
    { id: 'estrenos', name: 'Próximos estrenos', kind: 'premieres' },
    { id: 'favoritos', name: 'Más favoritos', ids: ['aonohako', 'mahoyo', 'koori', 'kikansha', 'chitose', 'horegusuri', 'revo'], go: 'explorar' },
    { id: 'extensiones', name: 'De tus extensiones', kind: 'ext' },
    { id: 'mas', name: 'Más de Comedia', kind: 'infinite', ids: ['seitokai', 'mouse', 'choujun', 'goblin', 'tempal', 'kanojo', 'laidoff'] },
  ];

  function hState(ctx) {
    const s = ctx.state;
    if (!s.init) Object.assign(s, { init: true, rl: 'limited', top: 'hoy', feed: 'emision', hero: 0, heroPaused: false, heroList: {}, remind: { psyren: true }, revealed: false, extRetry: false, order: SHELVES.map((x) => x.id), hidden: [], edit: true, emptyDemo: false, snack: null, dlg: null });
    return s;
  }

  function rateLimit(ctx, compact) {
    const s = ctx.state;
    if (s.rl === 'ok') return '';
    const busy = s.rl === 'retrying';
    return `<div class="nj-a-banner${compact ? ' is-compact' : ''}" role="status" data-state="${busy ? 'busy' : 'warn'}">${busy ? '<span class="nj-a-spin" aria-hidden="true"></span>' : ic('sync_problem')}<span class="nj-a-grow"><b>AniList limitó las solicitudes.</b> Usando Kitsu. ${busy ? 'Reintentando AniList…' : 'Nuevo intento en 0:42.'}</span>${busy ? '' : B('Reintentar', 'text', 'rl-retry')}<i class="nj-a-banner-bar" style="--v:.3" aria-hidden="true"></i></div>`;
  }

  function tonight(ctx) {
    const a = isA(ctx), s = ctx.state;
    return `<div class="nj-row nj-a-tonight${s.entered ? '' : ' is-enter'}" data-region="tonight" data-row-title="Esta noche" data-keep="tonight">${TONIGHT.map((x, i) => `<div class="nj-a-slot" style="--i:${i}"><span class="nj-a-slot-label">${ic(x.icon, { size: 16 })}${esc(x.label)}</span>${h.card(T(x.id), { context: 'continue', size: a ? 's' : 'm' })}</div>`).join('')}</div>`;
  }

  function agendaItems() { return D.airingToday.map((x) => Object.assign({}, x, { t: T(x.id), m: mins(x.time) })); }
  function airState(x) { if (x.now) return { id: 'now', label: 'Recién salió' }; if (x.m <= NOW) return { id: 'past', label: 'Disponible' }; const d = x.m - NOW; return { id: 'next', label: `En ${Math.floor(d / 60)} h ${d % 60} min` }; }
  function airAction(ctx, x, st) {
    if (st.id === 'next') { const on = !!ctx.state.remind[x.id]; return IB(on ? 'notifications_active' : 'notification_add', on ? `Quitar recordatorio de ${x.t.short}` : `Avisarme cuando salga ${x.t.short}`, 'remind', { v: x.id, fill: on, attrs: { 'aria-pressed': String(on) } }); }
    return h.iconBtn('play_arrow', `Ver ${x.t.short} Ep. ${x.ep}`, { kind: st.id === 'now' ? 'filled' : 'tonal', fill: true, attrs: { 'data-go': 'reproductor' } });
  }
  function agenda(ctx) {
    const items = agendaItems();
    const out = [];
    let nowPlaced = false;
    items.forEach((x) => {
      if (!nowPlaced && x.m > NOW) { out.push(`<li class="nj-a-now" aria-label="Ahora, 21:04"><span>Ahora 21:04</span></li>`); nowPlaced = true; }
      const st = airState(x);
      out.push(`<li class="nj-a-air" data-time="${x.time}" data-state="${st.id}"${x.now ? ' data-now="true"' : ''}><div class="nj-a-air-top"><time>${x.time}</time><span class="nj-a-air-st">${esc(st.label)}</span></div><div class="nj-a-air-body"><button type="button" class="nj-a-air-main" data-go="ficha" aria-label="${esc(x.t.short)}, Ep. ${x.ep}. Abrir ficha">${h.poster(x.t, { alt: '' })}<span><b>${esc(x.t.short)}</b><span class="nj-meta">Ep. ${x.ep}</span></span></button>${airAction(ctx, x, st)}</div></li>`);
    });
    return `<section class="nj-section nj-a-agenda-wrap" aria-labelledby="${pid(ctx, 'air')}">${head('Hoy en emisión', seeAll('calendario', 'Abrir calendario'), { id: pid(ctx, 'air'), sub: '6 episodios · hora local' })}<ol class="nj-a-agenda" data-region="airing" data-keep="agenda">${out.join('')}</ol></section>`;
  }
  function agendaList(ctx) {
    const items = agendaItems().filter((x) => x.m >= 17 * 60);
    return `<section class="nj-section" aria-labelledby="${pid(ctx, 'air')}">${head('Hoy en emisión', seeAll('calendario', 'Calendario'), { id: pid(ctx, 'air') })}<ol class="nj-a-agenda is-list" data-region="airing">${items.map((x) => { const st = airState(x); return `<li class="nj-a-air" data-time="${x.time}" data-state="${st.id}"${x.now ? ' data-now="true"' : ''}><time>${x.time}</time><button type="button" class="nj-a-air-main" data-go="ficha">${h.poster(x.t, { alt: '' })}<span><b>${esc(x.t.short)}</b><span class="nj-meta">Ep. ${x.ep} · ${esc(st.label)}</span></span></button>${airAction(ctx, x, st)}</li>`; }).join('')}<li class="nj-a-air-more"><button type="button" class="nj-btn is-text" data-go="calendario">3 más temprano: Magical★Explorer, Chitose-kun 2, Tempal</button></li></ol></section>`;
  }

  function linkPanel(ctx) {
    const L = D.link;
    if (isA(ctx)) return `<section class="nj-a-linkstrip" data-region="link" aria-label="Nijiyome Link"><button type="button" class="nj-a-linkrow" data-go="link"><span class="nj-a-linkdot" aria-hidden="true"></span><span class="nj-a-grow"><b>Link con ${esc(L.pc)}</b><span class="nj-meta">Conectado · ${esc(L.latency)} · ${esc(L.profile)}</span></span>${ic('chevron_right')}</button>${downloadsList(ctx, true)}</section>`;
    return `<section class="nj-a-panel" data-region="link" aria-labelledby="${pid(ctx, 'lk')}"><div class="nj-a-panel-head"><h2 class="nj-h3" id="${pid(ctx, 'lk')}">Nijiyome Link</h2><span class="nj-a-ok">Conectado</span></div>
      <div class="nj-a-linkmap" aria-label="Este PC conectado con ${esc(L.phone)}"><span class="nj-a-lnode">${ic('desktop_windows')}<b>${esc(L.pc)}</b><small>Este equipo</small></span><span class="nj-a-wire" aria-hidden="true"><i></i><small>${esc(L.latency)}</small></span><span class="nj-a-lnode">${ic('smartphone')}<b>${esc(L.phone)}</b><small>Viendo Ao no Hako 2</small></span></div>
      <dl class="nj-a-kv"><div><dt>Perfil para el teléfono</dt><dd>${esc(L.profile)}</dd></div><div><dt>Caché para retroceder</dt><dd>${esc(L.ram)}${meter(0.6, 'Caché usada al 60 %')}</dd></div><div><dt>${esc(L.tablet)}</dt><dd class="nj-muted">Sin conexión desde ayer</dd></div></dl>
      <div class="nj-cluster">${h.btn('Abrir Link', { kind: 'tonal', icon: 'devices', attrs: { 'data-go': 'link' } })}${h.btn('Monitor', { kind: 'text', icon: 'monitor_heart', attrs: { 'data-go': 'link' } })}</div></section>`;
  }
  function downloadsList(ctx, compact) {
    const act = D.transfers.filter((t) => t.state !== 'done');
    return `<div class="nj-a-dls${compact ? ' is-compact' : ''}">${act.map((t) => `<button type="button" class="nj-a-dl" data-go="transferencias"><span class="nj-a-dl-ic">${ic(t.kind === 'torrent' ? 'hub' : 'cloud_download')}</span><span class="nj-a-grow"><b>${esc(t.name)}</b><span class="nj-meta">${t.state === 'downloading' ? `${Math.round(t.pct * 100)} % · ${esc(t.down)} · quedan ${esc(t.eta)}` : `En cola · ${esc(t.size)} vía TorBox`}</span>${h.progress(t.pct, { label: 'Progreso de ' + t.name })}</span></button>`).join('')}</div>`;
  }
  function transfersPanel(ctx) {
    return `<section class="nj-a-panel" aria-labelledby="${pid(ctx, 'tr')}"><div class="nj-a-panel-head"><h2 class="nj-h3" id="${pid(ctx, 'tr')}">Descargando (2)</h2>${seeAll('transferencias', 'Ver todo')}</div>${downloadsList(ctx)}<p class="nj-meta nj-a-panel-foot">${ic('check_circle', { size: 16 })} Ao no Hako S2 · Ep 8 terminó (412 MB)</p></section>`;
  }

  function gated(ctx, item, opts) {
    if (ctx.state.revealed) return h.card(item, opts);
    return `<div class="nj-a-gate" data-cards-local>${h.card(item, opts)}<button type="button" class="nj-a-gate-btn" data-act="gate" aria-label="${esc(item.short)} es contenido adulto. Toca para decidir">${ic('visibility_off')}<b>Adulto</b><small>Toca para ver</small></button></div>`;
  }
  const cardRow = (ctx, title, ids, o = {}) => `<div class="nj-row" data-region="row" data-row-title="${esc(title)}" data-keep="r-${esc(title)}">${ids.map((id) => (o.gate === id ? gated(ctx, T(id), { size: isA(ctx) ? 's' : 'm', context: 'row', show: o.show || {} }) : h.card(T(id), { size: isA(ctx) ? 's' : 'm', context: 'row', show: o.show || {} }))).join('')}</div>`;

  function shelf(ctx, sh, o = {}) {
    const s = ctx.state, a = isA(ctx);
    let title = sh.name, extra = seeAll(sh.go), body = '';
    switch (sh.kind) {
      case 'continue': body = tonight(ctx); extra = seeAll('biblioteca'); break;
      case 'downloads': title = 'Descargando (2)'; body = downloadsList(ctx); extra = seeAll('transferencias'); break;
      case 'top': extra = seg('top', 'Periodo del Top 10', [{ id: 'hoy', label: 'Hoy' }, { id: 'semana', label: 'Semana' }, { id: 'mes', label: 'Mes' }], s.top); body = `<div class="nj-row nj-a-ranked" data-region="row" data-row-title="Top 10" data-keep="top">${TOP[s.top].map((id, i) => `<div class="nj-a-rank"><span class="nj-a-rank-n" aria-hidden="true">${i + 1}</span>${h.card(T(id), { size: a ? 's' : 'm', context: 'row' })}</div>`).join('')}</div>`; break;
      case 'genres': extra = ''; body = `<div class="nj-row nj-a-genres" data-keep="genres">${GENRES.map((g) => `<button type="button" class="nj-chip" data-go="explorar">${esc(g)}</button>`).join('')}</div>`; break;
      case 'feeds': title = FEEDS[s.feed].label; extra = (a ? '' : seg('feed', 'Lista del catálogo', Object.entries(FEEDS).map(([id, f]) => ({ id, label: f.label })), s.feed)) + seeAll('explorar'); body = (a ? `<div class="nj-a-feedchips">${chips('feed', 'Lista del catálogo', Object.entries(FEEDS).map(([id, f]) => ({ id, label: f.label })), [s.feed])}</div>` : '') + cardRow(ctx, title, FEEDS[s.feed].ids); break;
      case 'premieres': extra = ''; body = `<div class="nj-row" data-region="row" data-row-title="Próximos estrenos">${[['edgerunners', 'Invierno 2027'], ['mahoyo', 'Llega a streaming en 2027'], ['kanojo', 'Segunda temporada · por anunciar']].map(([id, w]) => `<div class="nj-a-slot">${h.card(T(id), { size: a ? 's' : 'm', context: 'row' })}<span class="nj-a-slot-label is-under">${ic('event', { size: 16 })}${esc(w)}</span></div>`).join('')}</div>`; break;
      case 'ext': extra = seeAll('fuentes', 'Administrar'); body = `<div class="nj-stack"><div><p class="nj-meta nj-a-subhead">Toko · Recién agregados</p>${cardRow(ctx, 'Toko · Recién agregados', ['tempal', 'goblin', 'dawang', 'mouse', 'choujun', 'shinja'])}</div>${s.extRetry ? `<div class="nj-a-inline" role="status"><span class="nj-a-spin" aria-hidden="true"></span>Cargando las filas de Stremio · Torrentio…</div>` : `<div class="nj-a-inline is-err" role="alert">${ic('error')}<span class="nj-a-grow"><b>Las filas de Stremio · Torrentio no cargaron.</b> La extensión tardó más de 15 s en responder.</span>${B('Reintentar', 'tonal', 'ext-retry', { icon: 'refresh' })}</div>`}</div>`; break;
      case 'infinite': body = cardRow(ctx, title, sh.ids) + `<div class="nj-a-skel" aria-busy="true" aria-label="Cargando Más de Romance"><span class="nj-meta">Más de Romance</span><div class="nj-row">${'<i></i>'.repeat(7)}</div></div>`; extra = seeAll('explorar'); break;
      default: body = cardRow(ctx, title, sh.ids, sh);
    }
    const pre = o.edit ? `<span class="nj-a-handle" aria-hidden="true">${ic('drag_indicator')}</span>` : '';
    if (o.edit) extra = IB('visibility_off', `Ocultar ${title}`, 'shelf-hide', { v: sh.id });
    const tonightAttr = sh.kind === 'continue' ? '' : '';
    return `<section class="nj-section nj-a-shelf" data-shelf="${sh.id}"${tonightAttr} aria-label="${esc(title)}">${head(title, extra, { pre })}${body}</section>`;
  }

  function emptyHome(ctx) {
    return `<div class="nj-a-empty" data-region="empty">${ic('cloud_off', { size: 40 })}<h2 class="nj-h2">Nada para mostrar todavía</h2><p class="nj-meta">AniList y Kitsu no devolvieron contenido. Revisa la conexión o cambia el orden de las fuentes de metadatos.</p><div class="nj-cluster">${B('Reintentar', 'filled', 'empty-retry', { icon: 'refresh' })}${h.btn('Fuentes de metadatos', { kind: 'outline', icon: 'tune', attrs: { 'data-go': 'ajustes' } })}</div></div>`;
  }

  function hero(ctx) {
    const s = ctx.state, x = HEROES[s.hero || 0];
    const listed = x.list || s.heroList[x.id];
    return `<section class="nj-a-hero" data-region="hero" aria-roledescription="carrusel" aria-label="Destacados"><img class="nj-a-hero-img" src="${x.img}" alt="" fetchpriority="high" width="1600" height="900">
      <div class="nj-a-hero-body"><div class="nj-cluster nj-a-hero-facts">${x.facts.map((f) => `<span class="nj-badge">${esc(f)}</span>`).join('')}</div><h2 class="nj-h1 nj-a-hero-title">${esc(x.title)}</h2><p class="nj-a-hero-genres">${esc(x.genres.join(' · '))}</p><p class="nj-a-hero-syn">${esc(x.syn)}</p>
      <div class="nj-cluster">${h.btn(x.cta, { kind: 'filled', icon: 'play_arrow', attrs: { 'data-go': 'reproductor' } })}${listed ? B(listed === 'watching' ? 'En tu lista: Viendo' : 'En tu lista: Pendiente', 'tonal', 'hero-list', { icon: 'bookmark_added', v: x.id }) : B('Agregar a Pendientes', 'tonal', 'hero-list', { icon: 'bookmark_add', v: x.id })}${h.iconBtn('info', 'Abrir ficha', { kind: 'tonal', attrs: { 'data-go': 'ficha' } })}</div></div>
      <div class="nj-a-hero-pager">${HEROES.map((y, j) => `<button type="button" class="nj-a-pg${j === (s.hero || 0) ? ' is-on' : ''}${s.heroPaused ? ' is-paused' : ''}" data-act="hero" data-v="${j}" aria-label="Destacado ${j + 1} de ${HEROES.length}: ${esc(y.title)}" aria-current="${j === (s.hero || 0)}"><i></i></button>`).join('')}${IB(s.heroPaused ? 'play_arrow' : 'pause', s.heroPaused ? 'Reanudar el carrusel' : 'Pausar el carrusel', 'hero-pause')}</div></section>`;
  }

  function matureDlg(ctx) {
    if (ctx.state.dlg !== 'mature') return '';
    return dialog('Contenido adulto', `<p>Magical★Explorer está marcado como contenido adulto. ¿Mostrarlo de todos modos? Se recuerda solo durante esta sesión.</p>`, `${B('Cancelar', 'text', 'dlg-x')}${B('Mostrar', 'filled', 'gate-ok')}`);
  }

  function homeHeader(ctx, title, sub) {
    return `<header class="nj-a-home-head"><div><h1 class="nj-h1 nj-a-title">${esc(title)}</h1><p class="nj-meta">${esc(sub)}</p></div>${rateLimit(ctx)}<div class="nj-cluster">${B('Editar Inicio', 'outline', 'edit-home', { icon: 'dashboard_customize' })}${IB('refresh', 'Actualizar todo Inicio', 'refresh-home')}</div></header>`;
  }

  function editPanel(ctx) {
    const s = ctx.state, a = isA(ctx);
    const rows = s.order.map((id, i) => { const sh = SHELVES.find((x) => x.id === id); const on = !s.hidden.includes(id); return `<li class="nj-a-erow${on ? '' : ' is-off'}" draggable="true" data-id="${id}"><span class="nj-a-handle" aria-hidden="true">${ic('drag_indicator')}</span><span class="nj-a-grow">${esc(sh.id === 'feeds' ? 'Catálogo (En emisión, Temporada, Populares, Mejor puntuados)' : sh.name)}</span>${IB('arrow_upward', `Subir ${sh.name}`, 'shelf-up', { v: id, attrs: i === 0 ? { disabled: true } : {} })}${IB('arrow_downward', `Bajar ${sh.name}`, 'shelf-down', { v: id, attrs: i === s.order.length - 1 ? { disabled: true } : {} })}<button type="button" class="nj-a-eye" role="switch" aria-checked="${on}" data-act="shelf-toggle" data-v="${id}" aria-label="Mostrar ${esc(sh.name)}">${ic(on ? 'visibility' : 'visibility_off')}</button></li>`; }).join('');
    return `<aside class="nj-sheet ${a ? '' : 'is-side '}nj-a-editpanel" data-region="sheet" aria-labelledby="${pid(ctx, 'ed')}"><div class="nj-a-sheet-head"><h2 class="nj-h2" id="${pid(ctx, 'ed')}">Editar Inicio</h2><span class="nj-a-grow"></span>${B('Listo', 'filled', 'edit-done')}</div><p class="nj-meta">Arrastra para reordenar o usa las flechas. Los cambios se ven al instante detrás de este panel.</p><ol class="nj-a-elist" data-keep="elist">${rows}</ol><div class="nj-a-edit-foot">${sw(ctx, 'emptydemo', 'Ver Inicio sin datos', s.emptyDemo, 'Muestra el estado vacío tal como aparece sin conexión con AniList ni Kitsu.')}${B('Restablecer orden', 'text', 'edit-reset', { icon: 'restart_alt' })}</div></aside>`;
  }

  function visibleShelves(ctx, skip = []) { const s = ctx.state; return s.order.filter((id) => !s.hidden.includes(id) && !skip.includes(id)).map((id) => SHELVES.find((x) => x.id === id)); }

  function renderInicio(ctx) {
    const s = hState(ctx), a = isA(ctx), st = ctx.structure;
    let inner = '';
    if (!a) {
      if (st === 'escenario') {
        inner = `<div class="nj-a-scroll" data-keep="home">${hero(ctx)}<div class="nj-a-col">${rateLimit(ctx) ? `<div class="nj-a-pad">${rateLimit(ctx)}</div>` : ''}${visibleShelves(ctx).map((sh) => shelf(ctx, sh)).join('')}<div class="nj-a-endrow">${B('Editar Inicio', 'outline', 'edit-home', { icon: 'dashboard_customize' })}</div></div></div>`;
      } else if (st === 'estantes') {
        const list = s.emptyDemo ? [] : visibleShelves(ctx);
        inner = `<div class="nj-a-split-edit${s.edit ? ' is-editing' : ''}"><div class="nj-a-scroll" data-keep="home"><div class="nj-a-col">${homeHeader(ctx, 'Inicio', 'Tus filas, en el orden que elegiste')}${list.length ? list.map((sh) => shelf(ctx, sh, { edit: s.edit })).join('') : emptyHome(ctx)}</div></div>${s.edit ? editPanel(ctx) : ''}</div>`;
      } else {
        inner = `<div class="nj-a-scroll" data-keep="home"><div class="nj-a-home-grid"><div class="nj-a-home-main">${homeHeader(ctx, 'Esta noche', 'Miércoles 25 de noviembre · 6 episodios para ver')}${tonight(ctx)}${agenda(ctx)}${visibleShelves(ctx, ['continuar', 'descargando']).map((sh) => shelf(ctx, sh)).join('')}</div><aside class="nj-a-aside" aria-label="Estado del equipo">${linkPanel(ctx)}${transfersPanel(ctx)}</aside></div></div>`;
      }
    } else {
      const top = `<div class="nj-a-asearch"><button type="button" class="nj-search nj-a-searchbar" data-go="explorar">${ic('search')}<span class="nj-a-grow">Buscar anime, manga o novelas</span></button><span class="nj-a-bell">${h.iconBtn('notifications', 'Notificaciones, 3 sin leer', { attrs: { 'data-go': 'perfil' } })}<span class="nj-a-badgecount" aria-hidden="true">3</span></span></div><div class="nj-row nj-a-quick" data-keep="quick">${[['calendar_month', 'Calendario', 'calendario'], ['explore', 'Para ti', 'explorar'], ['menu_book', 'Manga', 'manga'], ['auto_stories', 'Novelas', 'novelas']].map(([i, l, g]) => `<button type="button" class="nj-chip" data-go="${g}">${ic(i)}${l}</button>`).join('')}</div>`;
      if (st === 'escenario') {
        inner = `<div class="nj-a-scroll" data-keep="home">${hero(ctx)}${top}<div class="nj-a-pad">${rateLimit(ctx, true)}</div>${visibleShelves(ctx).map((sh) => shelf(ctx, sh)).join('')}<div class="nj-a-endrow">${B('Editar Inicio', 'outline', 'edit-home', { icon: 'dashboard_customize' })}</div></div>`;
      } else if (st === 'estantes') {
        const list = s.emptyDemo ? [] : visibleShelves(ctx);
        inner = `<div class="nj-a-scroll${s.edit ? ' is-under-sheet' : ''}" data-keep="home"><div class="nj-appbar"><h1 class="nj-h2">Inicio</h1>${IB('dashboard_customize', 'Editar Inicio', 'edit-home')}</div><div class="nj-a-pad">${rateLimit(ctx, true)}</div>${list.length ? list.map((sh) => shelf(ctx, sh, { edit: s.edit })).join('') : emptyHome(ctx)}</div>${s.edit ? editPanel(ctx) : ''}`;
      } else {
        inner = `<div class="nj-a-scroll" data-keep="home">${top}<div class="nj-a-pad">${rateLimit(ctx, true)}</div><section class="nj-section">${head('Esta noche', seeAll('biblioteca'))}${tonight(ctx)}</section>${agendaList(ctx)}<div class="nj-a-pad">${linkPanel(ctx)}</div>${visibleShelves(ctx, ['continuar', 'descargando']).map((sh) => shelf(ctx, sh)).join('')}</div>`;
      }
    }
    return `<div class="nj-a-root nj-a-home" data-struct="${st}">${inner}${snack(ctx)}${matureDlg(ctx)}</div>`;
  }

  function mountInicio(el, ctx) {
    const s = hState(ctx);
    const re = () => rr(el, ctx);
    const move = (id, d) => { const o = s.order.slice(); const i = o.indexOf(id); const j = i + d; if (j < 0 || j >= o.length) return; o.splice(i, 1); o.splice(j, 0, id); s.order = o; };
    wire(el, ctx, {
      seg(name, v) { if (name === 'top') s.top = v; if (name === 'feed') s.feed = v; re(); },
      chip(name, v) { if (name === 'feed') { s.feed = v; re(); } },
      sw(id, on) { if (id === 'emptydemo') { s.emptyDemo = on; re(); } },
      'rl-retry'() { s.rl = 'retrying'; re(); setTimeout(() => { s.rl = 'ok'; s.snack = { msg: 'AniList volvió a responder. Inicio está al día.' }; ctx.rerender(); }, 1500); },
      remind(b) { const id = b.dataset.v; s.remind[id] = !s.remind[id]; s.snack = { msg: s.remind[id] ? `Te avisamos cuando salga ${T(id).short}.` : `Recordatorio de ${T(id).short} quitado.` }; re(); },
      gate() { s.dlg = 'mature'; re(); },
      'gate-ok'() { s.revealed = true; s.dlg = null; re(); },
      'dlg-x'() { s.dlg = null; re(); },
      'dlg-stop'() {},
      'snack-x'() { s.snack = null; re(); },
      undo() { if (s._undo) { s._undo(); s._undo = null; } s.snack = null; re(); },
      'ext-retry'() { s.extRetry = true; re(); setTimeout(() => { s.extRetry = false; s.snack = { msg: 'Stremio · Torrentio sigue sin responder. Prueba más tarde o revisa la extensión.', go: 'fuentes', goLabel: 'Extensiones' }; ctx.rerender(); }, 1400); },
      hero(b) { s.hero = Number(b.dataset.v); re(); },
      'hero-pause'() { s.heroPaused = !s.heroPaused; re(); },
      'hero-list'(b) { const id = b.dataset.v; if (id === 'laidoff' || s.heroList[id]) { NJ.go('ficha'); return; } s.heroList[id] = 'planning'; s._undo = () => { delete s.heroList[id]; }; s.snack = { msg: 'Agregado a Pendientes. Puedes cambiar el estado desde la ficha.', undo: true }; re(); },
      'edit-home'() { if (ctx.structure !== 'estantes') { NJ.state.structures.inicio = 'estantes'; hState(ctx).edit = true; NJ.go('inicio', { structure: 'estantes' }); return; } s.edit = !s.edit; re(); },
      'edit-done'() { s.edit = false; s.snack = { msg: 'Inicio guardado con tu orden.' }; re(); },
      'edit-reset'() { const prev = [s.order.slice(), s.hidden.slice()]; s.order = SHELVES.map((x) => x.id); s.hidden = []; s._undo = () => { s.order = prev[0]; s.hidden = prev[1]; }; s.snack = { msg: 'Orden original restablecido.', undo: true }; re(); },
      'shelf-up'(b) { move(b.dataset.v, -1); re(); },
      'shelf-down'(b) { move(b.dataset.v, 1); re(); },
      'shelf-toggle'(b) { const id = b.dataset.v; s.hidden = s.hidden.includes(id) ? s.hidden.filter((x) => x !== id) : s.hidden.concat(id); re(); },
      'shelf-hide'(b) { const id = b.dataset.v; s.hidden = s.hidden.concat(id); s._undo = () => { s.hidden = s.hidden.filter((x) => x !== id); }; s.snack = { msg: `${SHELVES.find((x) => x.id === id).name} quedó oculta.`, undo: true }; re(); },
      'empty-retry'() { s.emptyDemo = false; re(); },
      'refresh-home'() { s.snack = { msg: 'Inicio actualizado: 2 episodios nuevos en Esta noche.' }; re(); },
    });
    /* Arrastrar y soltar en el panel de edición (con barra de inserción). */
    const list = el.querySelector('.nj-a-elist');
    if (list) {
      let drag = null;
      list.addEventListener('dragstart', (e) => { const li = e.target.closest('.nj-a-erow'); if (!li) return; drag = li.dataset.id; li.classList.add('is-drag'); try { e.dataTransfer.setData('text/plain', drag); e.dataTransfer.effectAllowed = 'move'; } catch (x) { /* nada */ } });
      list.addEventListener('dragover', (e) => { const li = e.target.closest('.nj-a-erow'); if (!li || !drag) return; e.preventDefault(); const r = li.getBoundingClientRect(); list.querySelectorAll('.is-before,.is-after').forEach((n) => n.classList.remove('is-before', 'is-after')); li.classList.add(e.clientY < r.top + r.height / 2 ? 'is-before' : 'is-after'); });
      list.addEventListener('drop', (e) => { const li = e.target.closest('.nj-a-erow'); if (!li || !drag) return; e.preventDefault(); const after = li.classList.contains('is-after'); const o = s.order.filter((x) => x !== drag); let i = o.indexOf(li.dataset.id); if (after) i += 1; o.splice(i, 0, drag); s.order = o; drag = null; re(); });
      list.addEventListener('dragend', () => { drag = null; list.querySelectorAll('.is-drag,.is-before,.is-after').forEach((n) => n.classList.remove('is-drag', 'is-before', 'is-after')); });
    }
    if (!s.entered) setTimeout(() => { s.entered = true; }, 50);
  }

  NJ.registerScreen({
    id: 'inicio', name: 'Inicio', group: 'Ver', order: 1, platforms: ['desktop', 'android'],
    summary: 'Inicio abre con lo de esta noche (continuar y episodios nuevos), la agenda de hoy y el estado de Link y descargas; las 15 filas siguen abajo y ahora se pueden reordenar u ocultar.',
    parity: 'HOME-01…HOME-18 (HOME-04/05 en Escenario, HOME-13 agrupa los 4 feeds con selector, HOME-17 desde Editar Inicio), UI-01, SHELL-05 vía UI-01, DESK-05 (Monitor desde el panel de Link)',
    structures: [
      { id: 'esta-noche', name: 'Esta noche', desc: 'Continuar y nuevos arriba, agenda de hoy, columna de Link y descargas.' },
      { id: 'escenario', name: 'Escenario', desc: 'Cabecera destacada grande con carrusel pausable y filas debajo.' },
      { id: 'estantes', name: 'Estantes', desc: 'Filas como estantes que se reordenan u ocultan en Editar Inicio.' },
    ],
    render: renderInicio,
    mount: mountInicio,
  });

  /* ======================================================================
     FICHA (anime)
     ====================================================================== */
  const LO = T('laidoff');
  const FRAME = 'img/frame-laidoff.webp';
  const REMIND = { estreno: 'Aviso al estrenarse', '30': 'Aviso 30 min antes', off: 'Sin aviso' };
  const TABS = [['episodios', 'Episodios'], ['info', 'Información'], ['personajes', 'Personajes'], ['relacionados', 'Relacionados'], ['resenas', 'Reseñas']];

  function fState(ctx, o) {
    const s = ctx.state;
    if (!s.init) Object.assign(s, { init: true, tab: 'episodios', view: 'lista', sel: [2, 3, 4], selecting: true, anchor: 2, watched: {}, dl: {}, status: 'watching', fav: false, follow: true, remind: 'estreno', src: 'searching', drawer: true, sheetUp: false, menu: null, snack: null, dlg: null, focusEp: null, q: '', spoilers: false, relView: 'lista' }, o || {});
    return s;
  }
  function eps(ctx) { const s = ctx.state; return D.laidoffEpisodes.map((e) => Object.assign({}, e, { watched: s.watched[e.n] != null ? s.watched[e.n] : e.watched, dl: !!s.dl[e.n] })); }
  const nextOf = (list) => list.find((e) => e.aired && !e.watched);
  const epTitle = (e) => (e.title ? e.title : `Episodio ${e.n}`);
  const thumb = (e) => `<span class="nj-a-thumb" style="--x:${(e.n * 37) % 100}%;--y:${(e.n * 23) % 100}%">${e.aired ? `<img src="${FRAME}" alt="" loading="lazy" width="160" height="90">` : ''}<b aria-hidden="true">${e.n}</b></span>`;

  function epTag(e, isNext) {
    if (!e.aired) return `<span class="nj-a-tag is-wait">${ic('schedule', { size: 16 })}${e.n === 9 ? 'Sale en 6 d 7 h' : 'Sin fecha'}</span>`;
    if (e.watched) return `<span class="nj-a-tag is-ok">${ic('check_circle', { size: 16, fill: true })}Visto</span>`;
    if (isNext) return `<span class="nj-a-tag is-next">${ic('play_circle', { size: 16, fill: true })}Siguiente</span>`;
    return '';
  }
  function epItem(ctx, e, nx, variant) {
    const s = ctx.state, sel = s.sel.includes(e.n), isNext = nx && nx.n === e.n;
    const check = s.selecting ? `<input type="checkbox" class="nj-a-check" data-act="ep-check" data-n="${e.n}"${sel ? ' checked' : ''}${e.aired ? '' : ' disabled'} aria-label="Seleccionar episodio ${e.n}">` : '';
    const meta = e.aired ? `${esc(e.ago)} · ${e.dur}${e.dl ? ' · Descargado' : ''}` : (e.n === 9 ? 'Se emite el 1 de diciembre' : 'Fecha por confirmar');
    const resume = isNext && !e.watched ? `<span class="nj-a-resume">${h.progress(0.15, { label: 'Visto 3:39 de 24 min' })}<span class="nj-meta">Reanudar en 3:39</span></span>` : '';
    const side = e.aired ? `${IB(e.watched ? 'remove_done' : 'check', e.watched ? `Marcar episodio ${e.n} como no visto` : `Marcar episodio ${e.n} como visto`, 'ep-watch', { v: e.n, attrs: { 'data-n': e.n } })}${IB(e.dl ? 'download_done' : 'download', e.dl ? `Episodio ${e.n} descargado` : `Descargar episodio ${e.n}`, 'ep-dl', { v: e.n, attrs: { 'data-n': e.n } })}` : '';
    return `<div class="nj-ep nj-a-ep" data-variant="${variant}" role="listitem" data-ep="${e.n}" data-watched="${e.watched}" data-aired="${e.aired}"${isNext ? ' data-next="true"' : ''} data-selected="${sel}">${check}<button type="button" class="nj-a-ep-main" data-act="ep-open" data-n="${e.n}"${!e.aired && !s.selecting ? ' aria-disabled="true"' : ''}${variant === 'rejilla' ? ` aria-label="Episodio ${e.n}. ${esc(epTitle(e))}"` : ''}>${variant === 'rejilla' ? `<span class="nj-a-ep-num">${e.n}</span>` : thumb(e)}<span class="nj-a-ep-text"><span class="nj-a-ep-title">${variant === 'rejilla' ? '' : e.n + '. '}${esc(epTitle(e))}</span><span class="nj-meta">${meta}</span>${resume}</span></button><span class="nj-a-ep-side">${epTag(e, isNext)}${side}</span></div>`;
  }

  function batchBar(ctx, list) {
    const s = ctx.state;
    if (!s.selecting) return '';
    const n = s.sel.length, allW = n > 0 && s.sel.every((x) => list.find((e) => e.n === x).watched);
    const count = n ? `${n} seleccionado${n > 1 ? 's' : ''}` : (isA(ctx) ? 'Toca episodios para elegirlos' : 'Clic o Shift+clic para elegir episodios');
    const acts = n ? `${B(allW ? `Marcar ${n} como no vistos` : `Marcar ${n} como vistos`, 'filled', 'b-watch', { icon: allW ? 'remove_done' : 'done_all' })}${B(`Descargar ${n}`, 'tonal', 'b-dl', { icon: 'download' })}` : '';
    if (isA(ctx)) return `<div class="nj-a-ctxbar" role="toolbar" aria-label="Selección">${IB('close', 'Salir de la selección', 'b-exit')}<b class="nj-a-grow" role="status">${count}</b>${IB('select_all', 'Seleccionar los 8 emitidos', 'b-all')}</div>${n ? `<div class="nj-a-batch is-android" role="toolbar" aria-label="Acciones para la selección">${acts}</div>` : ''}`;
    return `<div class="nj-a-batch" role="toolbar" aria-label="Acciones para la selección"><b class="nj-a-batch-count" role="status">${count}</b>${acts}${B('Todos los emitidos (8)', 'text', 'b-all')}${IB('close', 'Salir de la selección (Esc)', 'b-exit')}</div>`;
  }

  function statusPicker(ctx) {
    const s = ctx.state;
    const btns = (cls) => D.statuses.map((st) => `<button type="button" role="radio"${cls ? ` class="${cls}"` : ''} aria-checked="${s.status === st.id}" aria-pressed="${s.status === st.id}" data-act="status" data-v="${st.id}" data-status="${st.id}"><span class="nj-a-dot" aria-hidden="true"></span>${esc(st.name)}</button>`).join('');
    if (isA(ctx)) return `<div class="nj-a-statuschips" role="radiogroup" aria-label="Estado en tu lista" data-region="status-picker" data-keep="status">${btns('nj-chip')}</div>`;
    return `<div class="nj-a-statusrow"><span class="nj-meta">En tu lista</span><div class="nj-seg nj-a-statusseg" role="radiogroup" aria-label="Estado en tu lista" data-region="status-picker">${btns('')}</div>${IB('edit_note', 'Editar progreso, nota y fechas', 'editor')}${IB('playlist_remove', 'Quitar de la lista', 'status', { v: 'none' })}</div>`;
  }

  function actionsRow(ctx) {
    const s = ctx.state, a = isA(ctx);
    const items = [
      ['fav', 'favorite', s.fav ? 'Favorito' : 'Favorito', s.fav],
      ['follow', s.follow ? 'notifications_active' : 'notification_add', s.follow ? 'Siguiendo' : 'Seguir', s.follow],
      ['trailer', 'movie', 'Tráiler'],
      ['dlmenu', 'download', 'Descargar'],
      ['share', 'share', 'Compartir'],
      ['send', a ? 'cast' : 'send_to_mobile', a ? 'Enviar al PC' : 'Enviar al teléfono'],
    ];
    const remind = s.follow ? `<button type="button" class="nj-chip nj-a-remind" data-act="remind" aria-label="${REMIND[s.remind]}. Cambiar aviso">${ic(s.remind === 'off' ? 'notifications_off' : 'alarm', { size: 18 })}${REMIND[s.remind]}</button>` : '';
    const menu = s.menu === 'dl' ? `<div class="nj-menu nj-a-pop" role="menu" aria-label="Descargar"><button type="button" class="nj-menu-item" role="menuitem" data-act="dl-season">${ic('video_library')}Descargar temporada (Ep. 1–8)</button><button type="button" class="nj-menu-item" role="menuitem" data-act="dl-next">${ic('download')}Descargar el siguiente (Ep. 2)</button><button type="button" class="nj-menu-item" role="menuitem" data-act="dl-pick">${ic('checklist')}Elegir episodios…</button></div>` : '';
    if (a) return `<div class="nj-a-actions is-android" data-keep="actions">${items.map(([act, i, l, on]) => `<button type="button" class="nj-a-act" data-act="${act}"${on != null ? ` aria-pressed="${!!on}"` : ''}><span class="nj-a-act-ic">${ic(i, { fill: !!on })}</span><span>${l}</span></button>`).join('')}</div>${remind ? `<div class="nj-a-pad nj-a-remindrow">${remind}</div>` : ''}${menu}`;
    return `<div class="nj-a-actions"><div class="nj-cluster">${items.map(([act, i, l, on]) => `<button type="button" class="nj-btn is-outline nj-a-actbtn" data-act="${act}"${on != null ? ` aria-pressed="${!!on}"` : ''}${act === 'dlmenu' ? ` aria-haspopup="menu" aria-expanded="${s.menu === 'dl'}"` : ''}>${ic(i, { fill: !!on })}${l}</button>`).join('')}${remind}</div>${menu}</div>`;
  }

  function factsBlock(ctx, list) {
    const w = list.filter((e) => e.watched).length;
    const rows = [['Estado', '<span class="nj-a-live" aria-hidden="true"></span>En emisión'], ['Próximo', 'Ep. 9 · en 6 d 7 h'], ['Episodios', `${w} de 12 vistos`], ['Temporada', 'Otoño 2026'], ['Puntuación', `${ic('star', { size: 16, fill: true })} 7.4`], ['Estudio', esc(LO.studio)]];
    return `<dl class="nj-a-facts">${(isA(ctx) ? rows.slice(0, 4) : rows).map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('')}</dl>`;
  }

  function watchSplit(ctx, list, compact) {
    const nx = nextOf(list);
    const label = nx ? (nx.n === 2 ? `Continuar Ep. ${nx.n}` : `Ver Ep. ${nx.n}`) : 'Al día';
    return `<div class="nj-a-split${compact ? ' is-compact' : ''}">${h.btn(label, { kind: 'filled', icon: 'play_arrow', attrs: Object.assign({ 'data-go': 'reproductor' }, nx ? {} : { disabled: true }) })}<button type="button" class="nj-btn is-filled nj-a-split-more" data-act="sources" aria-label="Elegir la fuente del Ep. ${nx ? nx.n : ''}">${ic('expand_more')}</button></div>`;
  }
  function srcLine(ctx) {
    const s = ctx.state;
    if (s.src === 'searching') return `<div class="nj-a-srcline" role="status" aria-live="polite"><span class="nj-a-spin" aria-hidden="true"></span><span class="nj-a-grow">Buscando fuentes… Toko respondió, falta Stremio · Torrentio.</span></div>`;
    return `<button type="button" class="nj-a-srcline is-ok" data-act="sources"><span class="nj-a-dotok" aria-hidden="true"></span><span class="nj-a-grow">Kitsune Raws · 1080p HEVC · <b>En caché en TorBox</b></span><span class="nj-a-linklike">Cambiar (6)</span></button>`;
  }
  function nextUp(ctx, list) {
    const nx = nextOf(list) || list[0];
    return `<section class="nj-a-panel nj-a-nextup" aria-label="Continuar"><div class="nj-a-panel-head"><span class="nj-meta">Ep. ${nx.n} de 12</span><span class="nj-meta">3:39 de 24 min</span></div><p class="nj-a-nextup-title">${esc(epTitle(nx))}</p>${h.progress(0.15, { label: 'Progreso del episodio' })}${watchSplit(ctx, list)}${srcLine(ctx)}</section>`;
  }
  function sourcePanel(ctx) {
    const s = ctx.state, x = D.sources[0];
    const body = s.src === 'searching'
      ? `<div class="nj-a-skel-rows" aria-busy="true" aria-label="Buscando fuentes"><i></i><i></i></div>`
      : `<button type="button" class="nj-a-srcmini" data-act="sources">${ic('hub')}<span class="nj-a-grow"><b>${esc(x.ext)} · ${esc(x.group)}</b><span class="nj-meta">${x.res} · ${x.codec} ${x.bit} · Audio dual · Subs varios</span><span class="nj-cluster nj-a-src-pills"><span class="nj-a-pill is-ok">${ic('cloud_done', { size: 14 })}En caché · TorBox</span><span class="nj-a-pill">${ic('check', { size: 14 })}Elegida por ti</span></span>${relMeter(x.reliability)}</span></button>`;
    return `<section class="nj-a-panel" aria-labelledby="${pid(ctx, 'sp')}"><div class="nj-a-panel-head"><h2 class="nj-h3" id="${pid(ctx, 'sp')}">Fuentes del episodio${s.src === 'searching' ? '' : ' · 6'}</h2>${h.btn('Ajustes', { kind: 'text', icon: 'tune', attrs: { 'data-go': 'ajustes' } })}</div><p class="nj-meta">Selección manual: la que elijas se recuerda para este episodio.</p>${body}<div class="nj-cluster">${B('Ver las 6 fuentes', 'tonal', 'sources', { icon: 'list' })}${s.src === 'searching' ? '' : IB('download', 'Descargar Kitsune Raws para después', 'dl-next')}</div></section>`;
  }
  function relLabel(v) { return v >= 0.9 ? 'alta' : v >= 0.7 ? 'media' : 'baja'; }
  function relMeter(v) { const p = Math.round(v * 100); return `<span class="nj-a-rel" data-level="${relLabel(v)}">${meter(v, `Confiabilidad ${relLabel(v)}, ${p} %`)}<span>Confiabilidad ${relLabel(v)} · ${p} %</span></span>`; }
  function sendPanel(ctx) {
    return `<section class="nj-a-panel" aria-labelledby="${pid(ctx, 'sd')}"><div class="nj-a-panel-head"><h2 class="nj-h3" id="${pid(ctx, 'sd')}">Enviar a dispositivo</h2></div><ul class="nj-a-devs"><li>${ic('smartphone')}<span class="nj-a-grow"><b>${esc(D.link.phone)}</b><span class="nj-meta nj-a-okt">Conectado ahora</span></span>${B('Enviar', 'tonal', 'send-to', { v: 'phone' })}</li><li>${ic('tablet_android')}<span class="nj-a-grow"><b>${esc(D.link.tablet)}</b><span class="nj-meta">Sin conexión</span></span>${B('Comprobar', 'text', 'send-check')}</li></ul></section>`;
  }

  function tabsBar(ctx) {
    return `<div class="nj-tabs nj-a-tabs" role="tablist" aria-label="Secciones de la ficha" data-local data-keep="tabs">${TABS.map(([id, l]) => `<button type="button" role="tab" class="nj-tab" aria-selected="${ctx.state.tab === id}" data-act="tab" data-v="${id}">${l}</button>`).join('')}</div>`;
  }
  function epToolbar(ctx) {
    const s = ctx.state, a = isA(ctx);
    return `<div class="nj-a-eptools"><label class="nj-search nj-a-epsearch">${ic('search')}<input type="search" placeholder="${a ? 'Buscar episodio' : 'Buscar por número o título'}" aria-label="Buscar episodio" data-fk="epq" value="${esc(s.q)}"></label>${seg('view', 'Vista de episodios', [{ id: 'lista', label: a ? '' : 'Lista', icon: 'view_list' }, { id: 'tarjetas', label: a ? '' : 'Tarjetas', icon: 'view_agenda' }, { id: 'rejilla', label: a ? '' : 'Rejilla', icon: 'grid_view' }], s.view)}${a ? '' : `<button type="button" class="nj-btn is-outline" data-act="select-mode" aria-pressed="${s.selecting}">${ic('checklist')}${s.selecting ? 'Terminar selección' : 'Seleccionar'}</button>`}</div><p class="nj-meta nj-a-hint">${a ? 'Mantén presionado un episodio para elegir varios.' : 'Clic reproduce. Ctrl+clic o Shift+clic eligen varios; Ctrl+A elige todos; Esc sale.'}</p>`;
  }
  function epPanel(ctx, list) {
    const s = ctx.state, nx = nextOf(list);
    const q = s.q.trim().toLowerCase();
    const shown = q ? list.filter((e) => String(e.n) === q || (e.title || '').toLowerCase().includes(q)) : list;
    const cls = { lista: 'nj-a-eplist', tarjetas: 'nj-a-epcards', rejilla: 'nj-a-epgrid' }[s.view];
    const listHtml = shown.length ? `<div class="${cls}" role="list" aria-label="Episodios" data-region="episodes">${shown.map((e) => epItem(ctx, e, nx, s.view)).join('')}</div>` : `<div class="nj-a-empty is-small" data-region="empty">${ic('search_off', { size: 32 })}<p>Ningún episodio coincide con «${esc(s.q)}».</p>${B('Borrar búsqueda', 'text', 'q-clear')}</div>`;
    return `${epToolbar(ctx)}${listHtml}<div class="nj-a-seasonrow">${ic('video_library')}<span class="nj-a-grow"><b>Temporada completa</b><span class="nj-meta">Lote 1–8 de Kitsune Raws · 2,1 GB · en caché en TorBox</span></span>${B('Descargar temporada', 'tonal', 'dl-season', { icon: 'download' })}</div>`;
  }
  function infoPanel(ctx) {
    const s = ctx.state;
    return `<div class="nj-a-info"><p class="nj-a-syn">Rein, un encantador, es expulsado de su gremio de un día para otro. En vez de desesperarse, declara: «Ahora puedo hacer lo que quiera» y empieza a recuperar la enorme cantidad de mejoras que había repartido entre sus antiguos compañeros.</p>
      <div class="nj-cluster">${LO.genres.map((g) => `<button type="button" class="nj-chip" data-go="explorar">${esc(g)}</button>`).join('')}<button type="button" class="nj-chip" data-go="explorar">${ic('apartment', { size: 18 })}${esc(LO.studio)}</button></div>
      <dl class="nj-a-kv is-grid"><div><dt>Formato</dt><dd>TV · 12 episodios</dd></div><div><dt>Duración</dt><dd>24 min por episodio</dd></div><div><dt>Temporada</dt><dd>Otoño 2026</dd></div><div><dt>Puntuación media</dt><dd>74 %</dd></div><div><dt>Estudio</dt><dd>${esc(LO.studio)}</dd></div><div><dt>Fuente de metadatos</dt><dd>AniList</dd></div></dl>
      <div class="nj-a-subsec"><div class="nj-a-head"><h3 class="nj-h3">Etiquetas</h3><span class="nj-a-grow"></span>${sw(ctx, 'spoil', 'Mostrar etiquetas con spoilers', s.spoilers)}</div><div class="nj-cluster">${['Magia', 'Gremios', 'Protagonista masculino', 'Segunda oportunidad'].concat(s.spoilers ? ['Traición del grupo'] : []).map((t) => `<span class="nj-badge">${esc(t)}</span>`).join('')}</div></div>
      <div class="nj-a-subsec"><h3 class="nj-h3">Otros títulos</h3><p class="nj-meta">${esc(LO.romaji)}</p></div>
      <div class="nj-cluster">${h.btn('Ver tráiler', { kind: 'tonal', icon: 'movie', attrs: { 'data-act': 'trailer' } })}${h.btn('AniList', { kind: 'text', icon: 'open_in_new' })}${h.btn('MyAnimeList', { kind: 'text', icon: 'open_in_new' })}${h.btn('Kitsu', { kind: 'text', icon: 'open_in_new' })}</div></div>`;
  }
  function charsPanel() {
    const ch = [['Rein', 'Principal'], ['Lily Flambert', 'Principal']];
    return `<ul class="nj-a-chars">${ch.map(([n, r]) => `<li><button type="button" class="nj-a-char"><span class="nj-a-avatar" aria-hidden="true">${n[0]}</span><span class="nj-a-grow"><b>${n}</b><span class="nj-meta">${r} · actor de voz en AniList</span></span>${ic('chevron_right')}</button></li>`).join('')}</ul><div class="nj-a-inline is-err" role="alert">${ic('error')}<span class="nj-a-grow">No se pudieron cargar más personajes: AniList limitó las solicitudes.</span>${B('Reintentar', 'tonal', 'noop', { icon: 'refresh' })}</div>`;
  }
  function relPanel(ctx) {
    const s = ctx.state;
    return `<div class="nj-a-head">${seg('rel', 'Vista de relaciones', [{ id: 'lista', label: 'Lista', icon: 'view_list' }, { id: 'grafo', label: 'Grafo', icon: 'account_tree' }], s.relView)}</div>${s.relView === 'grafo' ? `<div class="nj-a-empty is-small" data-region="empty">${ic('account_tree', { size: 32 })}<p>Este título no tiene secuelas ni precuelas registradas. El grafo aparece cuando hay relaciones.</p></div>` : `<p class="nj-meta">Temporadas</p><div class="nj-row" data-region="row" data-row-title="Temporadas">${h.card(LO, { size: 's', context: 'row' })}</div><p class="nj-meta">Recomendaciones de la comunidad</p>${cardRow(ctx, 'Recomendaciones', ['tsuihou', 'kikansha', 'revo', 'shinja', 'goblin'])}`}`;
  }
  function reviewsPanel() {
    return `<div class="nj-a-head"><h3 class="nj-h3">Reseñas de AniList</h3><span class="nj-meta">Puntuación media 74 %</span><span class="nj-a-grow"></span>${h.btn('Escribir reseña', { kind: 'tonal', icon: 'rate_review' })}</div><div class="nj-a-empty is-small" data-region="empty">${ic('reviews', { size: 32 })}<p>Todavía nadie escribió una reseña de este anime en AniList.</p></div>`;
  }
  function tabBody(ctx, list) {
    switch (ctx.state.tab) { case 'info': return infoPanel(ctx); case 'personajes': return charsPanel(); case 'relacionados': return relPanel(ctx); case 'resenas': return reviewsPanel(); default: return epPanel(ctx, list); }
  }

  function route(ctx, list) {
    const s = ctx.state, nx = nextOf(list), f = s.focusEp || (nx ? nx.n : 1);
    const fe = list.find((e) => e.n === f);
    const stations = list.map((e) => { const isNext = nx && nx.n === e.n; const st = !e.aired ? 'no emitido' : e.watched ? 'visto' : isNext ? 'siguiente' : 'sin ver'; return `<div class="nj-ep nj-a-station" role="listitem" data-ep="${e.n}" data-watched="${e.watched}" data-aired="${e.aired}"${isNext ? ' data-next="true"' : ''} data-focus="${e.n === f}" data-selected="${s.sel.includes(e.n)}">${s.selecting && e.aired ? `<input type="checkbox" class="nj-a-check" data-act="ep-check" data-n="${e.n}"${s.sel.includes(e.n) ? ' checked' : ''} aria-label="Seleccionar episodio ${e.n}">` : ''}<button type="button" class="nj-a-station-btn" data-act="station" data-n="${e.n}" aria-current="${e.n === f}" aria-label="Episodio ${e.n}, ${st}"><span class="nj-a-node">${e.watched ? ic('check', { size: 18 }) : isNext ? ic('play_arrow', { size: 20, fill: true }) : e.n}</span><span class="nj-a-station-n">Ep. ${e.n}</span><span class="nj-a-station-when">${e.aired ? (e.watched ? 'Visto' : isNext ? 'Siguiente' : esc(e.ago)) : (e.n === 9 ? 'En 6 d 7 h' : 'Sin fecha')}</span></button></div>`; }).join('');
    const detail = `<article class="nj-a-epdetail" aria-label="Episodio ${fe.n}">${fe.aired ? `<img src="${FRAME}" alt="" width="320" height="180" style="object-position:${(fe.n * 37) % 100}% ${(fe.n * 23) % 100}%">` : `<span class="nj-a-epdetail-wait">${ic('schedule', { size: 32 })}</span>`}<div class="nj-stack"><span class="nj-meta">Episodio ${fe.n} de 12 · ${fe.aired ? esc(fe.ago) + ' · 24 min' : 'todavía no se emite'}</span><h3 class="nj-h2">${esc(epTitle(fe))}</h3>${nx && fe.n === nx.n ? `${h.progress(0.15, { label: 'Visto 3:39 de 24 min' })}<span class="nj-meta">Reanudar en 3:39</span>` : ''}<div class="nj-cluster">${fe.aired ? `${h.btn(fe.n === 2 ? 'Continuar' : 'Reproducir', { kind: 'filled', icon: 'play_arrow', attrs: { 'data-go': 'reproductor' } })}${B('Fuentes', 'tonal', 'sources', { icon: 'list' })}${B(fe.watched ? 'Marcar como no visto' : 'Marcar como visto', 'outline', 'ep-watch', { v: fe.n, attrs: { 'data-n': fe.n }, icon: fe.watched ? 'remove_done' : 'check' })}${B(fe.dl ? 'Descargado' : 'Descargar', 'outline', 'ep-dl', { v: fe.n, attrs: { 'data-n': fe.n }, icon: 'download' })}` : `${B(ctx.state.follow ? 'Te avisamos al estrenarse' : 'Avisarme', 'tonal', 'follow', { icon: 'notifications_active' })}`}</div>${fe.aired ? srcLine(ctx) : ''}</div></article>`;
    return `<div class="nj-a-eptools">${s.selecting ? '' : `<p class="nj-meta nj-a-grow">${isA(ctx) ? 'Toca una estación para ver el episodio; mantén presionado para elegir varias.' : 'Clic en una estación para verla. Shift+clic o Ctrl+clic eligen varias.'}</p>`}${isA(ctx) ? '' : `<button type="button" class="nj-btn is-outline" data-act="select-mode" aria-pressed="${s.selecting}">${ic('checklist')}${s.selecting ? 'Terminar selección' : 'Seleccionar'}</button>`}</div><div class="nj-a-route" role="list" aria-label="Episodios" data-region="episodes" data-keep="route">${stations}</div>${detail}`;
  }

  function fichaHeader(ctx, list, o = {}) {
    const a = isA(ctx);
    return `<header class="nj-a-dethead${o.compact ? ' is-compact' : ''}" data-region="hero">${o.noBackdrop ? '' : `<div class="nj-a-backdrop"><img src="${FRAME}" alt="" fetchpriority="high" width="1600" height="900"></div>`}<div class="nj-a-dethead-body">${h.poster(LO, { alt: 'Póster de ' + LO.title, w: a ? 96 : 120 })}<div class="nj-stack nj-a-dettitle"><h1 class="nj-h1">${esc(LO.title)}</h1>${a ? '' : `<p class="nj-meta">${esc(LO.romaji)}</p>`}<div class="nj-cluster">${h.btn('Copiar título', { kind: 'text', icon: 'content_copy', attrs: { 'data-act': 'copy' } })}${h.btn('AniList', { kind: 'text', icon: 'open_in_new' })}${a ? '' : h.btn('MyAnimeList', { kind: 'text', icon: 'open_in_new' })}</div></div></div></header>`;
  }
  const backBtn = (cls) => `<span class="${cls}">${h.iconBtn('arrow_back', 'Volver', { kind: 'tonal', attrs: { 'data-go': 'inicio' } })}</span>`;

  function fichaDialogs(ctx) {
    const s = ctx.state, a = isA(ctx);
    if (s.dlg === 'send') {
      const body = a ? `<p>Ep. 2 seguirá en ${esc(D.link.pc)} desde 3:39, con el perfil ${esc(D.link.profile)}.</p><p class="nj-meta nj-a-okt">${esc(D.link.pc)} · conectado · ${esc(D.link.latency)}</p>` : `<p>Ep. 2 seguirá en el teléfono desde 3:39. Aparece como «Reproducir aquí».</p><ul class="nj-a-devs"><li>${ic('smartphone')}<span class="nj-a-grow"><b>${esc(D.link.phone)}</b><span class="nj-meta nj-a-okt">Conectado ahora</span></span></li><li>${ic('tablet_android')}<span class="nj-a-grow"><b>${esc(D.link.tablet)}</b><span class="nj-meta">Sin conexión</span></span></li></ul>`;
      return dialog(a ? 'Enviar al PC' : 'Enviar al teléfono', body, `${B('Cancelar', 'text', 'dlg-x')}${B(a ? 'Enviar a ' + D.link.pc : 'Enviar a ' + D.link.phone, 'filled', 'send-to', { v: 'phone' })}`);
    }
    if (s.dlg === 'editor') return dialog('Editar entrada', `<div class="nj-a-stepper"><span class="nj-a-grow">Progreso</span>${IB('remove', 'Un episodio menos', 'noop')}<b>${eps(ctx).filter((e) => e.watched).length} / 12</b>${IB('add', 'Un episodio más', 'noop')}</div>${rng(ctx, 'score', 'Tu nota', { min: 0, max: 10, step: 0.5, value: 7.5 })}<div class="nj-a-subsec"><span class="nj-meta">Empezaste</span>${chips('started', 'Fecha de inicio', [{ id: 'oct', label: '3 de octubre' }, { id: 'none', label: 'Sin fecha' }], ['oct'])}</div>`, `${B('Cancelar', 'text', 'dlg-x')}${B('Guardar', 'filled', 'dlg-x')}`);
    return '';
  }

  function renderFicha(ctx) {
    const s = fState(ctx), a = isA(ctx), st = ctx.structure, list = eps(ctx);
    let inner = '';
    const content = st === 'ruta' ? (s.tab === 'episodios' ? route(ctx, list) : tabBody(ctx, list)) : tabBody(ctx, list);
    if (!a) {
      if (st === 'cine') {
        inner = `<div class="nj-a-cine"><img class="nj-a-cine-bg" src="${FRAME}" alt="" fetchpriority="high" width="1600" height="900"><div class="nj-a-scroll nj-a-cine-info" data-keep="det">${backBtn('nj-a-back')}<div class="nj-stack"><h1 class="nj-h1">${esc(LO.title)}</h1><p class="nj-meta">${esc(LO.genres.join(' · '))}</p></div>${factsBlock(ctx, list)}${statusPicker(ctx)}${actionsRow(ctx)}${watchSplit(ctx, list)}${srcLine(ctx)}<div class="nj-cluster">${B(s.drawer ? 'Ocultar episodios' : 'Episodios y más', 'tonal', 'drawer', { icon: 'side_navigation', attrs: { 'aria-expanded': String(s.drawer) } })}${B('Enviar al teléfono', 'text', 'send', { icon: 'send_to_mobile' })}</div></div>${s.drawer ? `<aside class="nj-sheet is-side nj-a-drawer" data-region="sheet" aria-label="Episodios y detalles">${tabsBar(ctx)}<div class="nj-a-scroll" data-keep="drawer">${content}</div>${batchBar(ctx, list)}</aside>` : ''}</div>`;
      } else if (st === 'ruta') {
        inner = `<div class="nj-a-scroll" data-keep="det"><div class="nj-a-routehead">${backBtn('')}${h.poster(LO, { alt: '', w: 64 })}<div class="nj-stack nj-a-grow"><h1 class="nj-h2">${esc(LO.title)}</h1>${factsBlock(ctx, list)}</div>${watchSplit(ctx, list)}</div><div class="nj-a-col">${statusPicker(ctx)}${actionsRow(ctx)}${tabsBar(ctx)}<div class="nj-a-tabbody">${content}</div></div></div>${batchBar(ctx, list)}`;
      } else {
        inner = `<div class="nj-a-scroll" data-keep="det">${backBtn('nj-a-back is-float')}${fichaHeader(ctx, list)}<div class="nj-a-detgrid"><div class="nj-a-detmain">${factsBlock(ctx, list)}${statusPicker(ctx)}${actionsRow(ctx)}${tabsBar(ctx)}<div class="nj-a-tabbody">${content}</div></div><aside class="nj-a-detside" aria-label="Ver ahora">${nextUp(ctx, list)}${sourcePanel(ctx)}${sendPanel(ctx)}</aside></div></div>${batchBar(ctx, list)}`;
      }
    } else {
      const top = `<div class="nj-a-topbar">${h.iconBtn('arrow_back', 'Volver', { kind: 'tonal', attrs: { 'data-go': 'inicio' } })}<span class="nj-a-grow"></span>${IB('more_vert', 'Más opciones', 'noop', { kind: 'tonal' })}</div>`;
      if (st === 'cine') {
        inner = `<div class="nj-a-cine is-android"><img class="nj-a-cine-bg" src="${FRAME}" alt="" fetchpriority="high" width="1600" height="900"><div class="nj-a-scroll nj-a-cine-info" data-keep="det">${top}<h1 class="nj-h1">${esc(LO.short)}</h1>${factsBlock(ctx, list)}${statusPicker(ctx)}${watchSplit(ctx, list)}${srcLine(ctx)}${actionsRow(ctx)}</div><section class="nj-sheet nj-a-bsheet${s.sheetUp ? ' is-up' : ''}" data-region="sheet" aria-label="Episodios y detalles"><button type="button" class="nj-a-grab" data-act="sheet-up" aria-expanded="${s.sheetUp}">${s.sheetUp ? 'Bajar' : 'Subir'} la hoja de episodios</button>${tabsBar(ctx)}<div class="nj-a-scroll" data-keep="bs">${content}</div></section>${batchBar(ctx, list)}</div>`;
      } else {
        inner = `<div class="nj-a-scroll" data-keep="det">${fichaHeader(ctx, list)}${top}<div class="nj-a-pad nj-stack">${factsBlock(ctx, list)}</div>${statusPicker(ctx)}${actionsRow(ctx)}${st === 'ruta' ? `<div class="nj-a-pad">${watchSplit(ctx, list)}${srcLine(ctx)}</div>` : `<div class="nj-a-pad">${nextUp(ctx, list)}</div>`}${tabsBar(ctx)}<div class="nj-a-tabbody">${content}</div></div>${batchBar(ctx, list)}`;
      }
    }
    return `<div class="nj-a-root nj-a-det" data-struct="${st}"${s.selecting ? ' data-selecting="true"' : ''}>${inner}${snack(ctx)}${fichaDialogs(ctx)}</div>`;
  }

  function mountFicha(el, ctx) {
    const s = fState(ctx);
    const re = () => rr(el, ctx);
    const airedNs = () => eps(ctx).filter((e) => e.aired).map((e) => e.n);
    const toggle = (n, range) => {
      const aired = airedNs(); if (!aired.includes(n)) return;
      const set = new Set(s.sel);
      if (range && s.anchor != null) { const lo = Math.min(s.anchor, n), hi = Math.max(s.anchor, n); aired.filter((x) => x >= lo && x <= hi).forEach((x) => set.add(x)); } else { if (!set.delete(n)) set.add(n); s.anchor = n; }
      s.sel = [...set].sort((x, y) => x - y);
    };
    const snapW = () => { const w = Object.assign({}, s.watched); const d = Object.assign({}, s.dl); return () => { s.watched = w; s.dl = d; }; };
    let lpAt = 0;
    wire(el, ctx, {
      _pre(t) { if (s.menu && !t.closest('.nj-a-pop') && !t.closest('[data-act="dlmenu"]')) { s.menu = null; re(); return false; } return true; },
      seg(name, v) { if (name === 'view') s.view = v; if (name === 'rel') s.relView = v; re(); },
      chip() {},
      sw(id, on) { if (id === 'spoil') { s.spoilers = on; re(); } },
      text(fk, v) { if (fk === 'epq') { s.q = v; re(); } },
      tab(b) { s.tab = b.dataset.v; if (ctx.structure === 'cine') s.drawer = true; re(); },
      'ep-check'(b, ev) { s.selecting = true; toggle(Number(b.dataset.n), ev.shiftKey); re(); },
      'ep-open'(b, ev) {
        if (Date.now() - lpAt < 700) return;
        const n = Number(b.dataset.n);
        if (s.selecting || ev.shiftKey || ev.ctrlKey || ev.metaKey) { s.selecting = true; toggle(n, ev.shiftKey); re(); return; }
        if (b.getAttribute('aria-disabled') === 'true') return;
        NJ.go('reproductor');
      },
      station(b, ev) {
        if (Date.now() - lpAt < 700) return;
        const n = Number(b.dataset.n);
        if (s.selecting || ev.shiftKey || ev.ctrlKey || ev.metaKey) { s.selecting = true; toggle(n, ev.shiftKey); } else s.focusEp = n;
        re();
      },
      'ep-watch'(b) { const n = Number(b.dataset.n); const list = eps(ctx); const e = list.find((x) => x.n === n); s._undo = snapW(); s.watched[n] = !e.watched; s.snack = { msg: e.watched ? `Ep. ${n} marcado como no visto.` : `Ep. ${n} marcado como visto. AniList actualizado.`, undo: true }; re(); },
      'ep-dl'(b) { const n = Number(b.dataset.n); if (s.dl[n]) return; s._undo = snapW(); s.dl[n] = true; s.snack = { msg: `Ep. ${n} en cola de descarga.`, undo: true, go: 'transferencias', goLabel: 'Ver descargas' }; re(); },
      'select-mode'() { s.selecting = !s.selecting; if (!s.selecting) s.sel = []; re(); },
      'b-exit'() { s.selecting = false; s.sel = []; re(); },
      'b-all'() { s.sel = airedNs(); re(); },
      'b-watch'() { const list = eps(ctx); const n = s.sel.length; const allW = s.sel.every((x) => list.find((e) => e.n === x).watched); s._undo = snapW(); s.sel.forEach((x) => { s.watched[x] = !allW; }); const total = eps(ctx).filter((e) => e.watched).length; s.snack = { msg: allW ? `${n} episodios marcados como no vistos.` : `${n} episodios marcados como vistos. AniList: ${total} de 12.`, undo: true }; s.selecting = false; s.sel = []; re(); },
      'b-dl'() { const n = s.sel.length; s._undo = snapW(); s.sel.forEach((x) => { s.dl[x] = true; }); s.snack = { msg: `${n} episodios en cola de descarga.`, undo: true, go: 'transferencias', goLabel: 'Ver descargas' }; s.selecting = false; s.sel = []; re(); },
      undo() { if (s._undo) { s._undo(); s._undo = null; } s.snack = null; re(); },
      'snack-x'() { s.snack = null; re(); },
      status(b) { const prev = s.status; const v = b.dataset.v; s.status = v === 'none' ? null : v; s._undo = () => { s.status = prev; }; s.snack = { msg: v === 'none' ? 'Quitado de tu lista.' : `Estado cambiado a ${h.status(v).name}. AniList actualizado.`, undo: true }; re(); },
      fav() { s.fav = !s.fav; s.snack = { msg: s.fav ? 'Agregado a favoritos.' : 'Quitado de favoritos.' }; re(); },
      follow() { s.follow = !s.follow; s.snack = { msg: s.follow ? 'Siguiendo. Aparece en tu calendario y te avisamos al estrenarse.' : 'Ya no sigues este anime.' }; re(); },
      remind() { const k = Object.keys(REMIND); s.remind = k[(k.indexOf(s.remind) + 1) % k.length]; re(); },
      trailer() { s.snack = { msg: 'Abriendo el tráiler en el navegador.' }; re(); },
      dlmenu() { s.menu = s.menu === 'dl' ? null : 'dl'; re(); },
      'dl-season'() { s.menu = null; s._undo = snapW(); [1, 2, 3, 4, 5, 6, 7, 8].forEach((x) => { s.dl[x] = true; }); s.snack = { msg: 'Temporada (Ep. 1–8) en cola vía TorBox.', undo: true, go: 'transferencias', goLabel: 'Ver descargas' }; re(); },
      'dl-next'() { s.menu = null; s._undo = snapW(); s.dl[2] = true; s.snack = { msg: 'Ep. 2 en cola de descarga.', undo: true, go: 'transferencias', goLabel: 'Ver descargas' }; re(); },
      'dl-pick'() { s.menu = null; s.selecting = true; s.sel = []; s.tab = 'episodios'; re(); },
      share() { s.snack = isA(ctx) ? { msg: 'Enlace de AniList copiado.', go: 'inicio', goLabel: 'Enviar' } : { msg: 'Enlace de AniList copiado al portapapeles.' }; re(); },
      copy() { s.snack = { msg: 'Título copiado.' }; re(); },
      send() { s.dlg = 'send'; re(); },
      'send-to'() { s.dlg = null; s.snack = { msg: isA(ctx) ? `Enviado a ${D.link.pc}. Sigue desde 3:39.` : `Enviado a ${D.link.phone}: aparece «Reproducir aquí».` }; re(); },
      'send-check'() { s.snack = { msg: `${D.link.tablet} sigue sin responder.` }; re(); },
      editor() { s.dlg = 'editor'; re(); },
      'dlg-x'() { s.dlg = null; re(); },
      'dlg-stop'() {},
      sources() { NJ.go('fuentes-episodio'); },
      drawer() { s.drawer = !s.drawer; re(); },
      'sheet-up'() { s.sheetUp = !s.sheetUp; re(); },
      'q-clear'() { s.q = ''; re(); },
      noop() {},
    });
    /* Pulsación larga: entra en selección (táctil y ratón). */
    let tmr = null;
    el.addEventListener('pointerdown', (e) => { const b = e.target.closest('.nj-a-ep-main, .nj-a-station-btn'); if (!b || e.button > 0) return; tmr = setTimeout(() => { tmr = null; lpAt = Date.now(); if (navigator.vibrate) { try { navigator.vibrate(12); } catch (x) { /* nada */ } } s.selecting = true; toggle(Number(b.dataset.n), false); re(); }, 480); });
    ['pointerup', 'pointerleave', 'pointercancel', 'pointermove'].forEach((t) => el.addEventListener(t, (e) => { if (tmr && (t !== 'pointermove' || Math.abs(e.movementX) + Math.abs(e.movementY) > 6)) { clearTimeout(tmr); tmr = null; } }));
    el.addEventListener('contextmenu', (e) => { if (e.target.closest('.nj-a-ep-main, .nj-a-station-btn')) e.preventDefault(); });
    el.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') { if (s.dlg || s.menu) { s.dlg = null; s.menu = null; re(); } else if (s.selecting) { s.selecting = false; s.sel = []; re(); } }
      else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a' && e.target.closest('[data-region="episodes"]')) { e.preventDefault(); s.selecting = true; s.sel = airedNs(); re(); }
    });
    if (s.src === 'searching' && !s._t) s._t = setTimeout(() => { s.src = 'ok'; s._t = null; ctx.rerender(); }, 2200);
  }

  NJ.registerScreen({
    id: 'ficha', name: 'Ficha', group: 'Ver', order: 2, platforms: ['desktop', 'android'],
    summary: 'El estado de lista pasa a un selector visible de 6 estados, descargar, compartir y enviar salen de «Más», el aviso por título reaparece y los episodios admiten selección múltiple con barra de lote y Deshacer.',
    parity: 'DET-01…DET-19, DET-20 (abre Fuentes del episodio), LIB-06/LIB-07 (editor y estados), SOC-04 (Reseñas), CHAR-01 (entrada), DESK-10 vía Enviar. DET-15 rangos de 100 episodios: no aplican a 12 episodios; se mantienen como chips sobre la lista cuando hay más de 100. DET-01a (puerta +18) y DET-18 grafo a pantalla completa quedan como en hoy.',
    structures: [
      { id: 'panel', name: 'Panel', desc: 'Cabecera, pestañas y columna lateral con Continuar, fuentes y Enviar a dispositivo.' },
      { id: 'ruta', name: 'Ruta', desc: 'Los episodios como estaciones de una línea: visto, siguiente, no emitido.' },
      { id: 'cine', name: 'Cine', desc: 'Fondo a sangre, información compacta y episodios en un cajón.' },
    ],
    render: renderFicha,
    mount: mountFicha,
  });

  /* ======================================================================
     FUENTES DEL EPISODIO (hoja sobre la ficha)
     ====================================================================== */
  const FACETS = {
    kind: { label: 'Tipo', opts: [['torrent', 'Torrent'], ['web', 'Web']], test: (x, v) => x.kind === v },
    audio: { label: 'Audio', opts: [['sub', 'Original con subtítulos'], ['dual', 'Audio dual']], test: (x, v) => (v === 'dual' ? x.audio === 'Dual' : x.audio !== 'Dual') },
    langs: { label: 'Subtítulos', opts: [['ES', 'Español'], ['EN', 'Inglés'], ['Multi', 'Varios idiomas']], test: (x, v) => x.subs === v },
    res: { label: 'Resolución', opts: [['2160p', '2160p'], ['1080p', '1080p'], ['720p', '720p']], test: (x, v) => x.res === v },
    codec: { label: 'Códec', opts: [['HEVC', 'HEVC'], ['AV1', 'AV1'], ['H.264', 'H.264']], test: (x, v) => x.codec === v },
  };
  const REL = [['any', 'Cualquiera'], ['media', 'Media o más'], ['alta', 'Solo alta']];
  const SORTS = [{ id: 'rec', label: 'Recomendado' }, { id: 'calidad', label: 'Calidad' }, { id: 'seeders', label: 'Seeders' }, { id: 'tamano', label: 'Tamaño' }];
  const sizeMB = (x) => { const m = /([\d,]+)\s*(MB|GB)/.exec(x.size || ''); if (!m) return Infinity; const n = parseFloat(m[1].replace(',', '.')); return m[2] === 'GB' ? n * 1024 : n; };
  const resN = (x) => parseInt(x.res, 10) || 0;

  function sState(ctx) {
    const s = ctx.state;
    if (!s.init) Object.assign(s, { init: true, mode: 'manual', f: { kind: [], audio: [], langs: [], res: [], codec: [] }, cached: false, rel: 'any', sort: 'rec', picked: 's1', pop: null, ep: 2, csort: { key: 'rec', dir: -1 }, busy: false, snack: null, under: {} });
    return s;
  }
  function filtered(s) {
    let list = D.sources.filter((x) => {
      if (s.cached && !x.cached) return false;
      if (s.rel === 'media' && x.reliability < 0.7) return false;
      if (s.rel === 'alta' && x.reliability < 0.9) return false;
      return Object.keys(FACETS).every((k) => !s.f[k].length || s.f[k].some((v) => FACETS[k].test(x, v)));
    });
    const by = { rec: (a, b) => (b.id === s.picked) - (a.id === s.picked) || (b.recommended ? 1 : 0) - (a.recommended ? 1 : 0), calidad: (a, b) => resN(b) - resN(a) || (b.bit > a.bit ? 1 : -1), seeders: (a, b) => (b.seeders || -1) - (a.seeders || -1), tamano: (a, b) => sizeMB(a) - sizeMB(b) };
    return list.slice().sort(by[s.sort] || by.rec);
  }
  const activeCount = (s) => Object.values(s.f).reduce((n, v) => n + v.length, 0) + (s.cached ? 1 : 0) + (s.rel !== 'any' ? 1 : 0);
  const audioL = (x) => (x.audio === 'Dual' ? 'Audio dual' : 'Audio JA');
  const subsL = (x) => ({ Multi: 'Subs varios', ES: 'Subs español', EN: 'Subs inglés' }[x.subs] || 'Subs ' + x.subs);

  function srcRow(ctx, x) {
    const s = ctx.state, picked = s.picked === x.id;
    const facts = x.kind === 'torrent' ? `${x.size} · ${x.seeders} seeders · ${x.leechers} leechers · ${esc(x.langs)}` : `Transmisión HLS · servidor ${esc(x.group)} · ${esc(x.langs)}`;
    const hw = isA(ctx) && x.res === '2160p' ? `<span class="nj-a-warn">${ic('warning', { size: 16 })}Este teléfono no decodifica HEVC 10-bit 4K por hardware: puede ir lento.</span>` : '';
    return `<li class="nj-a-src${picked ? ' is-picked' : ''}" data-kind="${x.kind}"><button type="button" class="nj-a-src-main" data-act="pick" data-v="${x.id}" aria-pressed="${picked}"><span class="nj-a-src-head">${ic(x.kind === 'torrent' ? 'hub' : 'language')}<b class="nj-a-grow">${esc(x.ext)} · ${esc(x.group)}</b>${picked ? `<span class="nj-a-pill is-acc">${ic('check', { size: 14 })}${s.mode === 'manual' ? 'Elegida por ti' : 'Elegida por el sistema'}</span>` : ''}</span><span class="nj-cluster nj-a-src-pills">${x.recommended ? '<span class="nj-a-pill is-acc">Recomendada</span>' : ''}${x.cached ? `<span class="nj-a-pill is-ok">${ic('cloud_done', { size: 14 })}En caché · ${x.cached}</span>` : ''}<span class="nj-a-pill">${x.res}</span><span class="nj-a-pill">${x.codec} ${x.bit}</span><span class="nj-a-pill">${audioL(x)}</span><span class="nj-a-pill">${subsL(x)}</span>${x.embed ? '<span class="nj-a-pill">Reproductor incrustado</span>' : ''}</span><span class="nj-meta">${facts}</span>${relMeter(x.reliability)}${hw}</button>${x.kind === 'torrent' ? `<button type="button" class="nj-btn is-text nj-a-src-dl" data-act="dl-src" data-v="${x.id}">${ic('download')}Descargar para después</button>` : ''}</li>`;
  }

  function facetBar(ctx) {
    const s = ctx.state;
    const chip = (k) => { const F = FACETS[k], sel = s.f[k]; const val = sel.length ? (sel.length === 1 ? F.opts.find((o) => o[0] === sel[0])[1] : `${sel.length}`) : ''; return `<button type="button" class="nj-chip nj-a-facet" data-act="pop" data-v="${k}" aria-pressed="${sel.length > 0}" aria-expanded="${s.pop === k}" aria-haspopup="dialog">${esc(F.label)}${val ? `: ${esc(val)}` : ''}${ic('arrow_drop_down', { size: 18 })}</button>`; };
    const relChip = `<button type="button" class="nj-chip nj-a-facet" data-act="pop" data-v="rel" aria-pressed="${s.rel !== 'any'}" aria-expanded="${s.pop === 'rel'}">Confiabilidad${s.rel !== 'any' ? ': ' + REL.find((r) => r[0] === s.rel)[1] : ''}${ic('arrow_drop_down', { size: 18 })}</button>`;
    const cached = `<button type="button" class="nj-chip" data-act="cached" aria-pressed="${s.cached}">${ic(s.cached ? 'check' : 'cloud_done', { size: 18 })}Solo en caché debrid</button>`;
    let pop = '';
    if (s.pop) {
      const count = (k, v) => D.sources.filter((x) => FACETS[k].test(x, v)).length;
      const inner = s.pop === 'rel' ? seg('rel', 'Confiabilidad mínima', REL.map(([id, label]) => ({ id, label })), s.rel) : `<div class="nj-cluster" role="group" aria-label="${FACETS[s.pop].label}">${FACETS[s.pop].opts.map(([v, l]) => `<button type="button" class="nj-chip" data-act="facet" data-v="${s.pop}|${v}" aria-pressed="${s.f[s.pop].includes(v)}">${s.f[s.pop].includes(v) ? ic('check', { size: 18 }) : ''}${esc(l)} <span class="nj-muted">${count(s.pop, v)}</span></button>`).join('')}</div>`;
      pop = `<div class="nj-menu nj-a-facetpop" role="dialog" aria-label="Filtrar por ${s.pop === 'rel' ? 'confiabilidad' : FACETS[s.pop].label.toLowerCase()}">${inner}<div class="nj-a-dlg-actions">${B('Listo', 'text', 'pop-x')}</div></div>`;
    }
    return `<div class="nj-a-facets"><div class="nj-a-facetrow" data-keep="facets">${cached}${chip('kind')}${chip('audio')}${chip('langs')}${chip('res')}${chip('codec')}${relChip}</div>${pop}</div>`;
  }

  function modeBlock(ctx) {
    const s = ctx.state;
    return `<div class="nj-a-mode">${seg('mode', 'Selección de fuente', [{ id: 'auto', label: 'Automática' }, { id: 'manual', label: 'Manual' }], s.mode)}<p class="nj-meta nj-a-grow">${s.mode === 'manual' ? 'Toca una fuente para reproducirla. La recordamos para este episodio.' : 'El sistema elige la mejor; si falla, prueba la siguiente de la lista.'}</p>${h.btn('Ajustes', { kind: 'text', icon: 'tune', attrs: { 'data-go': 'ajustes' } })}</div>`;
  }

  function emptyEp(ctx) {
    const s = ctx.state;
    if (s.busy) return `<div class="nj-a-skel-rows" aria-busy="true" aria-label="Buscando fuentes"><p class="nj-meta" role="status">Buscando en Toko y Stremio · Torrentio…</p><i></i><i></i><i></i></div>`;
    if (s.ep === 9) return `<div class="nj-a-empty" data-region="empty">${ic('schedule', { size: 40 })}<h3 class="nj-h2">El Ep. 9 todavía no se emite</h3><p class="nj-meta">Sale el 1 de diciembre, en 6 d 7 h. Las fuentes aparecen unas horas después.</p>${B('Avisarme cuando haya fuentes', 'filled', 'notify', { icon: 'notifications_active' })}</div>`;
    return `<div class="nj-a-empty" data-region="empty">${ic('search_off', { size: 40 })}<h3 class="nj-h2">Todavía no hay fuentes para el Ep. 8</h3><p class="nj-meta">Salió hace 16 h. Toko y Stremio · Torrentio no devolvieron resultados; suele tardar unas horas más.</p><div class="nj-cluster">${B('Buscar de nuevo', 'filled', 'research', { icon: 'refresh' })}${h.btn('Administrar extensiones', { kind: 'outline', icon: 'extension', attrs: { 'data-go': 'fuentes' } })}</div></div>`;
  }

  function compareTable(ctx, list) {
    const s = ctx.state;
    const cols = [['fuente', 'Fuente'], ['res', 'Resolución'], ['codec', 'Códec'], ['audio', 'Audio'], ['subs', 'Subtítulos'], ['size', 'Tamaño'], ['seeders', 'Seeders'], ['cache', 'Caché'], ['rel', 'Confiabilidad']];
    const val = { res: resN, size: (x) => -sizeMB(x), seeders: (x) => x.seeders || 0, rel: (x) => x.reliability, cache: (x) => (x.cached ? 1 : 0), codec: (x) => ({ AV1: 3, HEVC: 2, 'H.264': 1 }[x.codec]), audio: (x) => (x.audio === 'Dual' ? 1 : 0), subs: (x) => (x.subs === 'Multi' ? 2 : 1), fuente: (x) => (x.recommended ? 1 : 0) };
    const k = s.csort.key;
    const rows = k === 'rec' ? list : list.slice().sort((a, b) => (val[k](b) - val[k](a)) * (s.csort.dir < 0 ? 1 : -1));
    const best = {}; ['res', 'size', 'seeders', 'rel'].forEach((c) => { const m = Math.max(...list.map(val[c]).filter((v) => isFinite(v))); best[c] = m; });
    const cell = (c, x) => {
      const isBest = best[c] != null && list.length > 1 && val[c](x) === best[c];
      const txt = { fuente: `<button type="button" class="nj-a-cmp-pick" data-act="pick" data-v="${x.id}" aria-pressed="${s.picked === x.id}">${ic(s.picked === x.id ? 'radio_button_checked' : 'radio_button_unchecked', { size: 20 })}<span><b>${esc(x.group)}</b><small>${esc(x.ext)} · ${x.kind === 'torrent' ? 'Torrent' : 'Web'}</small></span></button>`, res: x.res, codec: `${x.codec} ${x.bit}`, audio: audioL(x).replace('Audio ', ''), subs: subsL(x).replace('Subs ', ''), size: x.size === 'HLS' ? 'Transmisión' : x.size, seeders: x.seeders != null ? x.seeders : '—', cache: x.cached ? `${ic('cloud_done', { size: 16 })} ${x.cached}` : 'No', rel: `${Math.round(x.reliability * 100)} %` }[c];
      return `<td${isBest ? ' class="is-best"' : ''}>${txt}${isBest ? '<span class="nj-a-sr"> (mejor)</span>' : ''}</td>`;
    };
    return `<div class="nj-a-cmpwrap" data-keep="cmp"><table class="nj-a-cmp"><caption class="nj-a-sr">Comparación de fuentes del Ep. ${s.ep}. Los encabezados ordenan.</caption><thead><tr>${cols.map(([c, l]) => `<th scope="col" aria-sort="${k === c ? (s.csort.dir > 0 ? 'ascending' : 'descending') : 'none'}"><button type="button" data-act="csort" data-v="${c}">${l}${ic(k === c ? (s.csort.dir > 0 ? 'arrow_upward' : 'arrow_downward') : 'unfold_more', { size: 16 })}</button></th>`).join('')}<th scope="col"><span class="nj-a-sr">Acciones</span></th></tr></thead><tbody>${rows.map((x) => `<tr class="${s.picked === x.id ? 'is-picked' : ''}">${cols.map(([c]) => cell(c, x)).join('')}<td>${x.kind === 'torrent' ? IB('download', `Descargar ${x.group} para después`, 'dl-src', { v: x.id }) : ''}</td></tr>`).join('')}</tbody></table></div>`;
  }

  function sourcesSheet(ctx) {
    const s = ctx.state, a = isA(ctx), cmp = ctx.structure === 'comparador';
    const ep = D.laidoffEpisodes[s.ep - 1];
    const list = filtered(s);
    const noSrc = s.ep >= 8;
    const picked = D.sources.find((x) => x.id === s.picked);
    const note = noSrc ? '' : `<p class="nj-a-picknote" role="status">${ic('check_circle', { size: 18, fill: true })}<span>${s.mode === 'manual' ? `Elegida por ti: <b>${esc(picked.group)}</b>. Si no aparece la próxima vez, te lo decimos.` : `El sistema eligió <b>${esc(picked.group)}</b>: en caché, 1080p y la más confiable.`}</span></p>`;
    let body;
    if (noSrc || s.busy) body = emptyEp(ctx);
    else {
      const results = list.length ? (cmp ? compareTable(ctx, list) : `<ul class="nj-a-srclist" aria-label="Fuentes">${list.map((x) => srcRow(ctx, x)).join('')}</ul>`) : `<div class="nj-a-empty is-small" data-region="empty">${ic('filter_alt_off', { size: 32 })}<p>Ninguna fuente coincide con estos filtros.</p>${B('Limpiar filtros', 'tonal', 'clear')}</div>`;
      const n = activeCount(s);
      body = `${modeBlock(ctx)}${note}${facetBar(ctx)}<div class="nj-a-srcbar"><span role="status"><b>${list.length}</b> de ${D.sources.length} fuentes</span>${n ? B(`Limpiar filtros (${n})`, 'text', 'clear') : ''}<span class="nj-a-grow"></span>${cmp ? '' : (a ? `<div class="nj-a-sortchips">${chips('sort', 'Ordenar por', SORTS, [s.sort])}</div>` : seg('sort', 'Ordenar por', SORTS, s.sort))}</div>${results}`;
    }
    return `<section class="nj-sheet ${a ? '' : 'is-side '}nj-a-srcsheet${cmp ? ' is-wide' : ''}${s.entered ? '' : ' is-enter'}" data-region="sheet" role="dialog" aria-modal="true" aria-labelledby="${pid(ctx, 'fs')}"><div class="nj-a-sheet-head"><div class="nj-stack nj-a-grow"><h2 class="nj-h2" id="${pid(ctx, 'fs')}">Fuentes del episodio</h2><p class="nj-meta nj-a-clamp">${esc(LO.short)} · ${esc(epTitle(ep))}</p></div><div class="nj-a-epstep" role="group" aria-label="Episodio">${IB('chevron_left', 'Episodio anterior', 'ep-prev', { attrs: s.ep <= 1 ? { disabled: true } : {} })}<b>Ep. ${s.ep}</b>${IB('chevron_right', 'Episodio siguiente', 'ep-next', { attrs: s.ep >= 9 ? { disabled: true } : {} })}</div>${h.iconBtn('close', 'Cerrar (Esc)', { attrs: { 'data-go': 'ficha' } })}</div><div class="nj-a-scroll" data-keep="sheet">${body}</div></section>`;
  }

  function renderFuentes(ctx) {
    const s = sState(ctx);
    const under = { platform: ctx.platform, structure: 'panel', dir: ctx.dir, data: D, h, state: s.under, rerender() {} };
    fState(under, { selecting: false, sel: [], src: 'ok' });
    let underHtml = '';
    try { underHtml = renderFicha(under); } catch (e) { underHtml = ''; }
    return `<div class="nj-a-root nj-a-fuentes" data-struct="${ctx.structure}"><div class="nj-a-under" inert aria-hidden="true">${underHtml}</div><div class="nj-overlay nj-a-scrim" data-go="ficha" aria-hidden="true"></div>${sourcesSheet(ctx)}${snack(ctx)}</div>`;
  }

  function mountFuentes(el, ctx) {
    const s = sState(ctx);
    const re = () => rr(el, ctx);
    wire(el, ctx, {
      seg(name, v) { if (name === 'mode') { s.mode = v; if (v === 'auto') s.picked = 's1'; } if (name === 'sort') s.sort = v; if (name === 'rel') s.rel = v; re(); },
      chip(name, v) { if (name === 'sort') { s.sort = v; re(); } },
      pop(b) { s.pop = s.pop === b.dataset.v ? null : b.dataset.v; re(); },
      'pop-x'() { s.pop = null; re(); },
      facet(b) { const [k, v] = b.dataset.v.split('|'); s.f[k] = s.f[k].includes(v) ? s.f[k].filter((x) => x !== v) : s.f[k].concat(v); re(); },
      cached() { s.cached = !s.cached; re(); },
      clear() { Object.keys(s.f).forEach((k) => { s.f[k] = []; }); s.cached = false; s.rel = 'any'; s.pop = null; re(); },
      pick(b) { const x = D.sources.find((y) => y.id === b.dataset.v); s.picked = x.id; s.mode = 'manual'; s.snack = { msg: `Se reproduce con ${x.group}. La recordamos para el Ep. ${s.ep}.`, go: 'reproductor', goLabel: 'Abrir reproductor' }; re(); },
      'dl-src'(b) { const x = D.sources.find((y) => y.id === b.dataset.v); s.snack = { msg: `Descarga agregada: ${x.group}, Ep. ${s.ep}${x.cached ? ' vía TorBox' : ' por P2P'}.`, go: 'transferencias', goLabel: 'Ver descargas' }; re(); },
      csort(b) { const k = b.dataset.v; s.csort = s.csort.key === k ? { key: k, dir: -s.csort.dir } : { key: k, dir: -1 }; re(); },
      'ep-prev'() { s.ep = Math.max(1, s.ep - 1); s.pop = null; re(); },
      'ep-next'() { s.ep = Math.min(9, s.ep + 1); s.pop = null; re(); },
      research() { s.busy = true; re(); setTimeout(() => { s.busy = false; s.snack = { msg: 'Sin fuentes nuevas para el Ep. 8. Volvemos a buscar solos en 30 min.' }; ctx.rerender(); }, 1600); },
      notify() { s.snack = { msg: 'Te avisamos cuando el Ep. 9 tenga fuentes.' }; re(); },
      'snack-x'() { s.snack = null; re(); },
    });
    el.addEventListener('keydown', (e) => { if (e.key === 'Escape') { if (s.pop) { s.pop = null; re(); } else NJ.go('ficha'); } });
    if (!s.entered) setTimeout(() => { s.entered = true; }, 50);
  }

  NJ.registerScreen({
    id: 'fuentes-episodio', name: 'Fuentes del episodio', group: 'Ver', order: 3, platforms: ['desktop', 'android'],
    summary: 'Los dos niveles de filtros se juntan en una fila de facetas con contador, se agrega orden (hoy no existe), la confiabilidad pasa a un solo control y los idiomas dejan de ser códigos ISO.',
    parity: 'PLY-04 completo, DET-12 (elegida por ti o por el sistema, descarga para después), DET-20, PLY-01 (sin fuentes, no emitido, buscando) y PLY-31 (Ajustes del reproductor). SeaDex, Lote, BD/REMUX y «Compatible con este dispositivo» van como opciones extra de la faceta Códec cuando las fuentes las traen (los datos de ejemplo no las tienen).',
    structures: [
      { id: 'facetas', name: 'Facetas', desc: 'Chips con ventana para cada faceta, contador de resultados y orden segmentado.' },
      { id: 'comparador', name: 'Comparador', desc: 'Tabla ordenable que compara atributos y marca el mejor valor de cada columna.' },
    ],
    render: renderFuentes,
    mount: mountFuentes,
  });

  /* ======================================================================
     REPRODUCTOR
     ====================================================================== */
  const DUR = 1440;
  const CH = [[0, 'Avance'], [90, 'Opening'], [180, 'Parte A'], [700, 'Parte B'], [1290, 'Ending'], [1380, 'Avance']];
  const fmt = (t) => { t = Math.max(0, Math.round(t)); return Math.floor(t / 60) + ':' + String(t % 60).padStart(2, '0'); };
  const chapterAt = (t) => { let c = CH[0][1]; CH.forEach(([s, l]) => { if (t >= s) c = l; }); return c; };
  const PRESETS = [['off', null, 'Sin mejora'], ['anime4k', 'A', 'Anime4K A'], ['anime4k', 'A+A', 'Anime4K A+A'], ['anime4k', 'B', 'Anime4K B'], ['anime4k', 'C', 'Anime4K C'], ['fsr', 'FSR 1', 'FSR']];
  const FAM_HINT = { off: 'El video tal como llega, sin sombreadores.', anime4k: 'Para fuentes borrosas o comprimidas, como la mayoría de los episodios web.', fsr: 'AMD FidelityFX Super Resolution: liviano, sirve para cualquier video.', fsrcnnx: 'Escalado fiel a la fuente, sin el aspecto de Anime4K.', cas: 'Solo nitidez (FidelityFX CAS), sin escalar.', nvscaler: 'NVIDIA Image Scaling: escalado rápido con nitidez, cualquier GPU.' };
  const MODE_HINT = { A: 'Para fuentes borrosas o comprimidas.', 'A+A': 'Como A con una segunda pasada: más nítido y más pesado para la GPU.', B: 'Para fuentes algo borrosas: toca menos las líneas.', C: 'Para fuentes limpias: escala y quita ruido.', 'B+B': 'Como B con una segunda pasada.', 'C+A': 'Primero limpia con C, luego restaura con A.' };
  const SHEETS = { pistas: ['Pistas', 'subtitles'], mejoras: ['Mejoras', 'high_quality'], imagen: ['Imagen', 'aspect_ratio'], velocidad: ['Velocidad y temporizador', 'speed'], episodios: ['Episodios', 'video_library'], mas: ['Más', 'more_horiz'] };

  function pState(ctx) {
    const s = ctx.state, a = isA(ctx);
    if (!s.init) Object.assign(s, { init: true, pos: 125, playing: true, sheet: null, ptab: 'mejoras', stats: ctx.structure === 'panel', chrome: true, speed: '1', timer: 'off', loop: false, stable: false, aspect: 'fit', zoom: 100, img: { bri: 0, con: 0, sat: 0, gam: 0, hue: 0 }, decoder: 'auto', fam: 'anime4k', mode: 'A+A', tier: 'Equilibrado', sharpen: 'media', deband: true, smooth: false, autoAdj: true, fg: a ? '×2' : 'Desactivada', fgProfile: 'Equilibrado', proc: 'pc', audio: 'ja', sub: 'es', sub2: 'none', delay: 0, subSize: 100, subBg: true, subPos: 100, ass: 'original', translate: false, vol: 80, muted: false, locked: false, pip: false, ext: false, hud: null, keys: false, nextCancel: false, timeLeft: false });
    return s;
  }
  const enhLabel = (s) => (s.fam === 'off' ? 'Sin mejora' : `${{ anime4k: 'Anime4K ' + s.mode, fsr: 'FSR', fsrcnnx: 'FSRCNNX ' + s.mode, cas: 'CAS', nvscaler: 'NVScaler' }[s.fam]}${['anime4k', 'fsr'].includes(s.fam) ? ' · ' + s.tier : ''}`) + (s.fg !== 'Desactivada' ? ` · ${s.fg} cuadros` : '');

  function seekBar(ctx, o = {}) {
    const s = ctx.state, a = isA(ctx);
    const p = s.pos / DUR, buf = Math.min(1, (s.pos + 200) / DUR), pc = a && s.proc !== 'phone' ? Math.min(1, (s.pos + 90) / DUR) : 0;
    const segs = CH.map((c, i) => { const st = c[0] / DUR, en = (CH[i + 1] || [DUR])[0] / DUR; const f = Math.max(0, Math.min(1, (p - st) / (en - st))); const fb = Math.max(0, Math.min(1, (buf - st) / (en - st))); return `<span class="nj-a-chap" style="--s:${st};--e:${en};--f:${f};--fb:${fb}" data-label="${c[1]}"><i class="nj-a-chap-b"></i><i class="nj-a-chap-f"></i></span>`; }).join('');
    return `<div class="nj-a-seek${o.thin ? ' is-thin' : ''}" data-region="player-seek" data-chapters="${CH.map((c) => fmt(c[0]) + ' ' + c[1]).join(';')}" style="--p:${p};--pc:${pc}"><div class="nj-a-seek-track" role="slider" tabindex="0" aria-label="Posición del episodio" aria-valuemin="0" aria-valuemax="${DUR}" aria-valuenow="${Math.round(s.pos)}" aria-valuetext="${fmt(s.pos)} de 24:00, ${chapterAt(s.pos)}" data-act="seek">${segs}${pc ? '<i class="nj-a-seek-pc" aria-hidden="true"></i>' : ''}<i class="nj-a-seek-thumb" aria-hidden="true"></i><span class="nj-a-seek-bubble" aria-hidden="true"></span></div></div>`;
  }
  const timeText = (s) => `<button type="button" class="nj-a-time" data-act="time" aria-label="Cambiar entre tiempo total y restante">${fmt(s.pos)} ${s.timeLeft ? '· −' + fmt(DUR - s.pos) : '/ 24:00'}</button>`;

  function skipBtn(ctx) {
    const s = ctx.state;
    if (s.pos >= 90 && s.pos < 180) return `<div class="nj-a-skip">${B('Saltar opening', 'tonal', 'skip', { icon: 'fast_forward' })}${isA(ctx) ? '' : '<span class="nj-kbd">Enter</span>'}</div>`;
    if (s.pos >= 1290 && s.pos < 1380) return `<div class="nj-a-skip">${B('Saltar ending', 'tonal', 'skip', { icon: 'fast_forward' })}</div>`;
    return '';
  }
  function nextCard(ctx) {
    const s = ctx.state;
    if (s.pos < 1380 || s.nextCancel) return '';
    const e = D.laidoffEpisodes[2];
    return `<section class="nj-a-nextcard" aria-label="Siguiente episodio"><span class="nj-a-ring" style="--v:.7" role="img" aria-label="Empieza en 7 segundos"><b>7</b></span><span class="nj-a-grow"><span class="nj-meta">Siguiente episodio</span><b>Ep. 3 · ${esc(e.title)}</b></span>${IB('skip_next', 'Reproducir el Ep. 3 ahora', 'noop', { kind: 'filled' })}${IB('close', 'Cancelar el paso automático', 'next-cancel')}</section>`;
  }
  function statsBox(ctx) {
    const s = ctx.state, a = isA(ctx);
    if (!s.stats) return '';
    const L = [`Pedido: ${enhLabel(s)}`, `En uso: ${a && s.proc === 'pc' ? 'procesado en ' + D.link.pc : enhLabel(s)}`, `Sombreadores: ${s.fam === 'anime4k' ? 'Clamp → Restore_CNN_M → Upscale_x2_M → Restore_CNN_S → Upscale_x2_S · FP16' : s.fam === 'off' ? 'ninguno' : s.fam.toUpperCase()}`, `Decodificador: ${a ? 'HW+ (mediacodec)' : 'HW+ (vaapi)'} · pedido ${s.decoder === 'auto' ? 'Auto' : s.decoder.toUpperCase()}`, 'Cuadros perdidos: 3', `Video: 1920×1080 → ${a ? '2400×1080' : '2560×1440'} · 23,976 fps`, `Generación de cuadros: ${s.fg === 'Desactivada' ? 'apagada' : s.fg + ' · 47,95 fps' + (a ? ' (en el teléfono)' : ' (lsfg-vk)')}`];
    return `<div class="nj-a-stats" role="status" aria-label="Estadísticas">${L.map((l) => `<span>${esc(l)}</span>`).join('')}</div>`;
  }

  /* ---- hojas ---- */
  function shPistas(ctx) {
    const s = ctx.state, a = isA(ctx);
    return `<section class="nj-a-sec"><h3 class="nj-h3">Audio</h3>${chips('audio', 'Pista de audio', [{ id: 'ja', label: 'Japonés · AAC 2.0' }, { id: 'en', label: 'Inglés · AAC 2.0' }], [s.audio])}${a ? '' : '<p class="nj-meta">V cambia de pista con el teclado.</p>'}</section>
      <section class="nj-a-sec"><h3 class="nj-h3">Subtítulos</h3>${chips('sub', 'Subtítulos', [{ id: 'off', label: 'Desactivados' }, { id: 'es', label: 'Español · Kitsune' }, { id: 'en', label: 'Inglés' }, { id: 'fr', label: 'Francés' }], [s.sub])}${B('Cargar archivo…', 'outline', 'noop', { icon: 'upload_file' })}
        <div class="nj-a-stepper"><span class="nj-a-grow">Retraso <span class="nj-meta">${a ? '' : '(Z y X)'}</span></span>${IB('remove', 'Mostrar 0,1 s antes', 'delay', { v: '-1', kind: 'tonal' })}<b>${s.delay >= 0 ? '+' : ''}${(s.delay / 10).toFixed(1).replace('.', ',')} s</b>${IB('add', 'Mostrar 0,1 s después', 'delay', { v: '1', kind: 'tonal' })}${IB('restart_alt', 'Quitar el retraso', 'delay', { v: '0', attrs: s.delay === 0 ? { disabled: true } : {} })}</div></section>
      <section class="nj-a-sec"><h3 class="nj-h3">Segunda pista, arriba</h3>${chips('sub2', 'Segunda pista', [{ id: 'none', label: 'Ninguna' }, { id: 'en', label: 'Inglés' }, { id: 'ja', label: 'Japonés' }], [s.sub2])}</section>
      <section class="nj-a-sec"><h3 class="nj-h3">Estilo rápido</h3><div class="nj-a-subprev" style="--sz:${s.subSize / 100};--bg:${s.subBg ? 1 : 0}" aria-hidden="true"><img src="${FRAME}" alt="" width="320" height="180"><span>Así se ven tus subtítulos</span></div>${rng(ctx, 'subsize', 'Tamaño', { min: 50, max: 250, step: 5, value: s.subSize, unit: ' %' })}${sw(ctx, 'subbg', 'Fondo detrás del texto', s.subBg)}${rng(ctx, 'subpos', 'Posición vertical', { min: 0, max: 100, step: 1, value: s.subPos, unit: ' %' })}<p class="nj-meta">Subtítulos con estilo ASS</p>${seg('ass', 'Subtítulos con estilo ASS', [{ id: 'original', label: 'Originales' }, { id: 'mine', label: 'Con mi estilo' }, { id: 'plain', label: 'Texto simple' }], s.ass)}<div class="nj-cluster">${h.btn('Más opciones de estilo', { kind: 'tonal', icon: 'format_size', attrs: { 'data-go': 'ajustes' } })}${B('Restablecer', 'text', 'sub-reset')}</div></section>
      <section class="nj-a-sec">${sw(ctx, 'translate', 'Traducir subtítulos', s.translate, 'Google → Español, línea por línea')}${s.translate ? B('Traducir toda la pista ahora', 'text', 'noop', { icon: 'translate' }) : ''}</section>`;
  }
  function shMejoras(ctx) {
    const s = ctx.state, a = isA(ctx);
    const fams = [['off', 'Sin mejora', []]].concat(D.enhancements.families.map((f) => [f.id, f.name, f.modes]));
    const famTiles = fams.map(([id, name]) => { const dis = a && id === 'nvscaler'; return `<button type="button" class="nj-a-fam" role="radio" aria-checked="${s.fam === id}" data-act="fam" data-v="${id}"${dis ? ' disabled' : ''}><b>${esc(name)}</b><small>${dis ? 'Solo en escritorio' : esc(FAM_HINT[id])}</small></button>`; }).join('');
    const F = D.enhancements.families.find((f) => f.id === s.fam);
    const modes = F && F.modes.length > 1 ? `<p class="nj-meta">Modo</p>${chips('mode', 'Modo de ' + F.name, F.modes.map((m) => ({ id: m, label: m })), [s.mode])}${MODE_HINT[s.mode] ? `<p class="nj-meta">${esc(MODE_HINT[s.mode])}</p>` : ''}` : '';
    const tiers = F && F.tiers.length ? `<p class="nj-meta">Nivel</p>${seg('tier', 'Nivel', F.tiers.map((t) => ({ id: t, label: t })), F.tiers.includes(s.tier) ? s.tier : F.tiers[1])}` : '';
    const procText = { phone: 'El teléfono reproduce la fuente original con sus propias mejoras.', pc: `${D.link.pc} mejora el video y lo envía; el teléfono solo lo reproduce.`, mixto: `${D.link.pc} escala el video y el teléfono genera los cuadros.` }[s.proc];
    const proc = `<section class="nj-a-sec"><h3 class="nj-h3">${a ? 'Procesar en' : `Cuando ${esc(D.link.phone)} reproduce desde este PC`}</h3>${seg('proc', 'Procesar en', [{ id: 'phone', label: 'Teléfono', icon: 'smartphone' }, { id: 'pc', label: 'PC', icon: 'desktop_windows' }, { id: 'mixto', label: 'Mixto', icon: 'sync_alt' }], s.proc)}<p class="nj-meta">${esc(procText)}</p>${a && s.proc !== 'phone' ? `<dl class="nj-a-kv is-grid"><div><dt>Perfil</dt><dd>${esc(D.link.profile)}</dd></div><div><dt>Escalado</dt><dd>Anime4K A+A → 2400×1080</dd></div><div><dt>Cuadros</dt><dd>LSFG ×2 → 48 fps</dd></div><div><dt>Envío</dt><dd>HEVC · ${esc(D.link.latency)}</dd></div></dl>` : ''}</section>`;
    const fgOpts = D.enhancements.framegen.options.map((o) => ({ id: o, label: o }));
    return `<section class="nj-a-sec">${sw(ctx, 'stats', 'Estadísticas sobre el video', s.stats, 'Lo pedido, lo que corre, cuadros perdidos y generación.')}</section>
      ${proc}
      <section class="nj-a-sec"${a && s.proc === 'pc' ? ' aria-disabled="true"' : ''}><h3 class="nj-h3">Escalado ${a ? 'en el teléfono' : ''}</h3>${a && s.proc === 'pc' ? `<p class="nj-meta">En pausa: ${esc(D.link.pc)} procesa el video.</p>` : ''}<div class="nj-a-famgrid" role="radiogroup" aria-label="Familia de escalado">${famTiles}</div>${modes}${tiers}<div class="nj-a-row2"><span class="nj-meta nj-a-grow">Los cambios aplican a esta reproducción.</span>${B('Usar como predeterminado', 'text', 'default')}</div>
        <p class="nj-meta">Nitidez</p>${seg('sharpen', 'Nitidez', [{ id: 'off', label: 'Apagada' }, { id: 'baja', label: 'Baja' }, { id: 'media', label: 'Media' }, { id: 'alta', label: 'Alta' }], s.sharpen)}${sw(ctx, 'deband', 'Quitar bandas de color', s.deband, 'Suaviza los saltos en los degradados.')}${sw(ctx, 'smooth', 'Suavizado de movimiento', s.smooth && s.fg === 'Desactivada', s.fg !== 'Desactivada' ? 'Apagado mientras hay generación de cuadros.' : 'Acompasa los cuadros a la pantalla. No es generación de cuadros.')}${sw(ctx, 'autoadj', 'Ajustes automáticos', s.autoAdj, 'Baja la calidad si el equipo se calienta, la batería baja o se pierden cuadros.')}</section>
      <section class="nj-a-sec"><h3 class="nj-h3">Generación de cuadros</h3>${seg('fg', 'Generación de cuadros', fgOpts, s.fg)}<p class="nj-meta">${a ? (s.fg === 'Desactivada' ? 'Apagada.' : 'En el teléfono · 24 → 48 fps.') : 'Con lsfg-vk. Solo funciona en la ventana propia de mpv, así que el episodio se abre ahí.'}</p>${a && s.fg !== 'Desactivada' ? `<p class="nj-meta">Perfil</p>${seg('fgprofile', 'Perfil de generación', [{ id: 'Ahorro', label: 'Ahorro' }, { id: 'Equilibrado', label: 'Equilibrado' }, { id: 'Calidad', label: 'Calidad' }], s.fgProfile)}` : ''}</section>`;
  }
  function shImagen(ctx) {
    const s = ctx.state, a = isA(ctx);
    const dec = { auto: 'Elige solo el mejor disponible.', hw: 'Decodifica en la GPU y copia los cuadros: compatible con las mejoras.', 'hw+': 'Decodifica directo en la GPU: gasta menos batería.', sw: 'Reproduce casi todo, pero es lo más lento.' };
    return `<section class="nj-a-sec"><h3 class="nj-h3">Aspecto</h3>${seg('aspect', 'Aspecto', [{ id: 'fit', label: 'Ajustar' }, { id: 'fill', label: 'Rellenar' }, { id: 'stretch', label: 'Estirar' }, { id: '169', label: '16:9' }, { id: '43', label: '4:3' }], s.aspect)}${rng(ctx, 'zoom', 'Zoom', { min: 25, max: 400, step: 5, value: s.zoom, unit: ' %' })}</section>
      <section class="nj-a-sec"><h3 class="nj-h3">Color</h3>${[['bri', 'Brillo'], ['con', 'Contraste'], ['sat', 'Saturación'], ['gam', 'Gamma'], ['hue', 'Tono']].map(([k, l]) => rng(ctx, 'img-' + k, l, { min: -100, max: 100, step: 1, value: s.img[k] })).join('')}${B('Restablecer imagen', 'text', 'img-reset', { icon: 'restart_alt' })}</section>
      <section class="nj-a-sec"><h3 class="nj-h3">Decodificador</h3>${seg('decoder', 'Decodificador', [{ id: 'auto', label: 'Auto' }, { id: 'hw', label: 'GPU' }, { id: 'hw+', label: 'GPU directa' }, { id: 'sw', label: 'CPU' }], s.decoder)}<p class="nj-meta">${esc(dec[s.decoder])} En uso: GPU directa (${a ? 'mediacodec' : 'vaapi'}).</p></section>`;
  }
  function shVelocidad(ctx) {
    const s = ctx.state, a = isA(ctx);
    const sp = (a ? ['0.5', '0.75', '1', '1.25', '1.5', '2'] : ['0.5', '0.75', '1', '1.25', '1.5', '1.75', '2']).map((v) => ({ id: v, label: v.replace('.', ',') + '×' }));
    return `<section class="nj-a-sec"><h3 class="nj-h3">Velocidad</h3>${chips('speed', 'Velocidad', sp, [s.speed])}<p class="nj-meta">${a ? 'Mantén presionado el video para ir a 2× mientras no sueltes.' : 'Con el teclado: . sube y , baja.'}</p></section>
      <section class="nj-a-sec"><h3 class="nj-h3">Temporizador</h3>${chips('timer', 'Temporizador para pausar', [{ id: 'off', label: 'Apagado' }, { id: '15', label: '15 min' }, { id: '30', label: '30 min' }, { id: '60', label: '60 min' }, { id: 'end', label: 'Al terminar el episodio' }], [s.timer])}${s.timer !== 'off' ? `<p class="nj-meta">${s.timer === 'end' ? 'Se pausa en 21:55, cuando termina el episodio.' : `Se pausa en ${s.timer}:00.`}</p>` : ''}</section>
      <section class="nj-a-sec">${sw(ctx, 'loop', 'Repetir el episodio al terminar', s.loop)}${sw(ctx, 'stable', 'Volumen estable', s.stable, 'Empareja escenas fuertes y suaves.')}</section>`;
  }
  function shEpisodios(ctx) {
    return `<ul class="nj-a-pleps" data-region="episodes" aria-label="Episodios">${D.laidoffEpisodes.map((e) => `<li class="nj-ep" data-ep="${e.n}" data-watched="${e.watched}" data-aired="${e.aired}"${e.n === 2 ? ' data-next="true"' : ''}><button type="button" class="nj-a-plep" data-act="${e.aired && e.n !== 2 ? 'play-ep' : 'noop'}" data-v="${e.n}"${!e.aired || e.n === 2 ? ' aria-disabled="true"' : ''}>${thumb(e)}<span class="nj-a-grow"><b>${e.n}. ${esc(epTitle(e))}</b><span class="nj-meta">${e.n === 2 ? 'Reproduciendo ahora' : e.aired ? (e.watched ? 'Visto · ' : '') + esc(e.ago) : (e.n === 9 ? 'Sale en 6 d 7 h' : 'Sin fecha')}</span></span>${e.watched ? ic('check_circle', { fill: true, size: 20 }) : e.n === 2 ? ic('equalizer', { size: 20 }) : ''}</button></li>`).join('')}</ul>`;
  }
  function shMas(ctx) {
    const a = isA(ctx);
    const layouts = a ? [{ id: 'cine', label: 'Cine' }, { id: 'minimal', label: 'Mínimo' }, { id: 'panel', label: 'Vertical' }] : [{ id: 'cine', label: 'Clásico' }, { id: 'minimal', label: 'Mínimo' }, { id: 'panel', label: 'Panel' }];
    const items = [['photo_camera', 'Captura de pantalla', 'shot'], ['download', 'Descargar episodio', 'dl-ep'], ['hub', 'Abrir en el cliente de torrents', 'noop', true], ['keyboard', 'Atajos de teclado', 'keys']].concat(a ? [['lock', 'Bloquear controles', 'lock'], ['open_in_new', 'Abrir con otra app', 'noop']] : [['terminal', 'Reproducir en mpv externo', 'ext-on']]);
    return `<section class="nj-a-sec"><h3 class="nj-h3">Diseño de controles</h3>${seg('layout', 'Diseño de controles', layouts, ctx.structure)}</section>${a ? '' : `<section class="nj-a-sec"><h3 class="nj-h3">Calidad</h3>${seg('quality', 'Calidad', [{ id: 'auto', label: 'Auto' }, { id: '2160p', label: '2160p' }, { id: '1080p', label: '1080p' }, { id: '720p', label: '720p' }], '1080p')}<p class="nj-meta">En escritorio, cambiar la calidad cambia de fuente.</p></section>`}<ul class="nj-a-menu">${items.map(([i, l, act, dis]) => `<li><button type="button" class="nj-menu-item" data-act="${act}"${dis ? ' aria-disabled="true"' : ''}>${ic(i)}<span class="nj-a-grow">${l}${dis ? '<small class="nj-meta">Esta fuente viene de TorBox, no del cliente.</small>' : ''}</span></button></li>`).join('')}<li><button type="button" class="nj-menu-item" data-go="ajustes">${ic('settings')}<span class="nj-a-grow">Ajustes del reproductor</span></button></li></ul>`;
  }
  const SHEET_FN = { pistas: shPistas, mejoras: shMejoras, imagen: shImagen, velocidad: shVelocidad, episodios: shEpisodios, mas: shMas };

  function sheet(ctx) {
    const s = ctx.state;
    if (!s.sheet) return '';
    const [t] = SHEETS[s.sheet];
    return `<aside class="nj-sheet is-side nj-a-psheet" data-region="sheet" role="dialog" aria-label="${t}"><div class="nj-a-sheet-head"><h2 class="nj-h2 nj-a-grow">${t}</h2>${IB('close', 'Cerrar (Esc)', 'sheet-x')}</div><div class="nj-a-scroll" data-keep="psheet">${SHEET_FN[s.sheet](ctx)}</div></aside>`;
  }
  function keysDlg(ctx) {
    if (!ctx.state.keys) return '';
    const K = [['Espacio', 'Reproducir o pausar'], ['← →', 'Retroceder o avanzar 5 s'], ['↑ ↓', 'Volumen'], ['M', 'Silenciar'], ['F', 'Pantalla completa'], ['P', 'Ventana flotante'], ['J  K', 'Episodio anterior o siguiente'], ['S', 'Captura'], ['C', 'Cambiar subtítulos'], ['V', 'Cambiar audio'], ['Z  X', 'Retraso de subtítulos'], ['.  ,', 'Velocidad'], ['Enter', 'Saltar opening o ending'], ['L', 'Repetir'], ['?', 'Esta ayuda']];
    const P = PRESETS.map((p, i) => [`Ctrl ${i + 1}`, p[2]]);
    const col = (rows) => `<dl class="nj-a-keys">${rows.map(([k, d]) => `<div><dt>${k.split('  ').map((x) => `<span class="nj-kbd">${esc(x)}</span>`).join(' ')}</dt><dd>${esc(d)}</dd></div>`).join('')}</dl>`;
    return dialog('Atajos de teclado', `<div class="nj-a-keycols">${col(K)}<div>${col(P)}<p class="nj-meta">Ratón: clic pausa, doble clic pantalla completa, rueda ±5 s.</p></div></div>`, `${h.btn('Editar atajos', { kind: 'text', icon: 'edit', attrs: { 'data-go': 'ajustes' } })}${B('Cerrar', 'filled', 'keys-x')}`, { close: 'keys-x' });
  }

  /* ---- cromo del reproductor ---- */
  function videoEl(ctx) {
    const s = ctx.state, I = s.img;
    const filt = `brightness(${1 + I.bri / 100}) contrast(${1 + I.con / 100}) saturate(${1 + I.sat / 100}) hue-rotate(${I.hue * 1.8}deg)`;
    return `<div class="nj-a-video" data-aspect="${s.aspect}" data-act="surface"><img src="${FRAME}" alt="Cuadro del episodio 2 con el subtítulo «¿No íbamos a matar a un dragón?»" width="1600" height="900" style="transform:scale(${s.zoom / 100});filter:${filt}"></div>`;
  }
  const enhChip = (ctx) => { const s = ctx.state; const warn = isA(ctx) && s.proc === 'pc'; return `<button type="button" class="nj-chip nj-a-enh" data-act="sheet" data-v="mejoras" data-tone="${warn ? 'neutral' : 'active'}" aria-label="Mejoras: ${esc(enhLabel(s))}. Toca para cambiarlas.">${ic(warn ? 'desktop_windows' : 'high_quality', { size: 18 })}<span>${warn ? 'Mejoras en el PC' : esc(enhLabel(s))}</span></button>`; };
  const srcChip = () => `<button type="button" class="nj-chip nj-a-srcchip" data-go="fuentes-episodio">${ic('hub', { size: 18 })}<span>Fuente 1 de 6 · Kitsune Raws · TorBox</span>${ic('expand_more', { size: 18 })}</button>`;
  const linkPill = (ctx) => { const s = ctx.state; if (!isA(ctx) || s.proc === 'phone') return ''; return `<button type="button" class="nj-a-linkpill" data-act="sheet" data-v="mejoras">${ic('devices', { size: 18 })}${s.proc === 'pc' ? `Procesado por ${esc(D.link.pc)}` : `Mixto con ${esc(D.link.pc)}`} · ${esc(D.link.latency)}</button>`; };
  const titleBlock = () => `<div class="nj-a-ptitle"><b>${esc(LO.short)} · Ep. 2</b><span>${esc(D.laidoffEpisodes[1].title)}</span></div>`;
  const playBtn = (ctx, cls = '') => `<button type="button" class="nj-iconbtn is-filled nj-a-play ${cls}" data-act="play" aria-label="${ctx.state.playing ? 'Pausar' : 'Reproducir'} (Espacio)">${ic(ctx.state.playing ? 'pause' : 'play_arrow', { fill: true })}</button>`;
  const sheetBtn = (ctx, id, label) => { const [t, i] = SHEETS[id]; return `<button type="button" class="nj-chip nj-a-pchip" data-act="sheet" data-v="${id}" aria-pressed="${ctx.state.sheet === id || (ctx.structure === 'panel' && ctx.state.ptab === id)}">${ic(i, { size: 18 })}${esc(label || t)}</button>`; };
  const trackLabel = (s) => `Audio ${s.audio.toUpperCase()} · ${s.sub === 'off' ? 'Sin subs' : 'Sub ' + s.sub.toUpperCase()}`;
  const speedLabel = (s) => `${s.speed.replace('.', ',')}×`;
  const hud = (ctx) => (ctx.state.hud ? `<div class="nj-a-hud" role="status">${esc(ctx.state.hud)}</div>` : '');

  function deskTop(ctx, minimal) {
    if (minimal) return `<div class="nj-a-ptop is-min">${h.iconBtn('arrow_back', 'Volver a la ficha', { attrs: { 'data-go': 'ficha' } })}${titleBlock()}<button type="button" class="nj-a-srcsuffix" data-go="fuentes-episodio">· Kitsune Raws</button></div>`;
    return `<div class="nj-a-ptop">${h.iconBtn('arrow_back', 'Volver a la ficha', { attrs: { 'data-go': 'ficha' } })}${titleBlock()}<div class="nj-cluster nj-a-pchips">${enhChip(ctx)}${srcChip()}</div><span class="nj-a-grow"></span>${IB('download', 'Descargar episodio', 'dl-ep')}${IB('photo_camera', 'Captura (S)', 'shot')}${ctx.structure === 'panel' ? '' : IB('video_library', 'Episodios', 'sheet', { v: 'episodios' })}${IB('keyboard', 'Atajos de teclado (?)', 'keys')}${IB('send_to_mobile', `Enviar a ${D.link.phone}`, 'send-phone')}${IB('picture_in_picture_alt', 'Ventana flotante (P)', 'pip')}${IB('fullscreen', 'Pantalla completa (F)', 'full')}</div>`;
  }
  function deskBottom(ctx, minimal) {
    const s = ctx.state;
    const vol = `<span class="nj-a-volume">${IB(s.muted || s.vol === 0 ? 'volume_off' : 'volume_up', s.muted ? 'Activar sonido (M)' : 'Silenciar (M)', 'mute')}<input type="range" class="nj-a-vol" id="${pid(ctx, 'vol')}" min="0" max="100" value="${s.muted ? 0 : s.vol}" aria-label="Volumen ${s.vol} %"></span>`;
    if (minimal) return `<div class="nj-a-pbottom is-min">${playBtn(ctx, 'is-sm')}${seekBar(ctx, { thin: true })}${timeText(s)}<div class="nj-a-minacts">${[['pistas', 'Pistas'], ['mejoras', 'Mejoras'], ['velocidad', speedLabel(s)], ['episodios', 'Episodios'], ['mas', 'Más']].map(([id, l]) => `<button type="button" class="nj-a-minbtn" data-act="sheet" data-v="${id}">${ic(SHEETS[id][1])}<span>${esc(l)}</span></button>`).join('')}${IB('fullscreen', 'Pantalla completa (F)', 'full')}</div></div>`;
    return `<div class="nj-a-pbottom">${seekBar(ctx)}<div class="nj-a-pbar">${IB('skip_previous', 'Episodio anterior (J)', 'noop')}${IB('replay_5', 'Retroceder 5 s (←)', 'back5')}${playBtn(ctx)}${IB('forward_5', 'Avanzar 5 s (→)', 'fwd5')}${IB('skip_next', 'Siguiente episodio (K)', 'noop')}${timeText(s)}<span class="nj-a-chapname">${esc(chapterAt(s.pos))}</span>${vol}<span class="nj-a-grow"></span><div class="nj-cluster nj-a-pchips">${sheetBtn(ctx, 'pistas', trackLabel(s))}${sheetBtn(ctx, 'mas', 'Calidad 1080p')}${sheetBtn(ctx, 'velocidad', speedLabel(s))}${sheetBtn(ctx, 'imagen', 'Imagen')}${sheetBtn(ctx, 'mas', 'Más')}</div></div></div>`;
  }
  function extCard(ctx) {
    const s = ctx.state;
    const asked = parseInt(s.fg.replace('×', ''), 10);
    return `<div class="nj-a-ext"><section class="nj-a-extcard" aria-labelledby="${pid(ctx, 'mpv')}"><span class="nj-a-ext-ic">${ic('terminal', { size: 28 })}</span><h2 class="nj-h2" id="${pid(ctx, 'mpv')}">Reproduciendo en mpv</h2><p><b>${esc(LO.short)} · Ep. 2</b></p><p class="nj-meta">Mejoras de Nijiyome · Generación de cuadros ${esc(s.fg)} (lsfg-vk)</p>${asked > 2 ? `<p class="nj-a-warn">${ic('info', { size: 16 })}Pediste ×${asked}; una pantalla de 60 Hz muestra ×2.</p>` : ''}<div class="nj-a-extplay">${playBtn(ctx, 'is-sm')}${seekBar(ctx, { thin: true })}${timeText(s)}</div><p class="nj-meta">La generación de cuadros solo funciona en la ventana propia de mpv, así que este episodio se ve ahí. Usa sus controles o estos.</p><div class="nj-cluster">${B('Ver sin generación de cuadros', 'tonal', 'ext-off', { icon: 'play_circle' })}${h.btn('Fuentes', { kind: 'outline', icon: 'list', attrs: { 'data-go': 'fuentes-episodio' } })}${h.btn('Ajustes de generación', { kind: 'text', attrs: { 'data-go': 'ajustes' } })}${h.btn('Detener', { kind: 'text', icon: 'stop', attrs: { 'data-go': 'ficha' } })}</div></section></div>`;
  }

  function touchChrome(ctx, minimal) {
    const s = ctx.state;
    if (s.locked) return `<div class="nj-a-lock">${B('Desbloquear', 'tonal', 'unlock', { icon: 'lock_open' })}<span>Controles bloqueados</span></div>`;
    if (!s.chrome) return '';
    const top = `<div class="nj-a-ptop is-touch">${h.iconBtn('arrow_back', 'Volver a la ficha', { attrs: { 'data-go': 'ficha' } })}${titleBlock()}<span class="nj-a-grow"></span>${IB('lock', 'Bloquear controles', 'lock')}${IB('picture_in_picture_alt', 'Ventana flotante', 'pip')}${IB('more_vert', 'Más opciones', 'sheet', { v: 'mas' })}</div>${minimal ? '' : `<div class="nj-cluster nj-a-pchips is-touch">${enhChip(ctx)}${srcChip()}${linkPill(ctx)}</div>`}`;
    const rails = `<div class="nj-a-rail is-left" aria-hidden="true">${ic('brightness_6', { size: 18 })}<i style="--v:.62"></i><small>Desliza: brillo</small></div><div class="nj-a-rail is-right" aria-hidden="true">${ic('volume_up', { size: 18 })}<i style="--v:.8"></i><small>Desliza: volumen</small></div><span class="nj-a-tapzone is-left" aria-hidden="true">${ic('replay_10', { size: 20 })}Doble toque</span><span class="nj-a-tapzone is-right" aria-hidden="true">Doble toque${ic('forward_10', { size: 20 })}</span>`;
    const center = minimal ? '' : `<div class="nj-a-transport">${IB('skip_previous', 'Episodio anterior', 'noop')}${IB('replay_10', 'Retroceder 10 s', 'back10')}${playBtn(ctx, 'is-lg')}${IB('forward_10', 'Avanzar 10 s', 'fwd10')}${IB('skip_next', 'Siguiente episodio', 'noop')}</div>`;
    const aspectL = { fit: 'Ajustar', fill: 'Rellenar', stretch: 'Estirar', 169: '16:9', 43: '4:3' }[s.aspect];
    const bottom = minimal
      ? `<div class="nj-a-pbottom is-touch is-min">${playBtn(ctx, 'is-sm')}${seekBar(ctx, { thin: true })}<span class="nj-a-time">−${fmt(DUR - s.pos)}</span><div class="nj-a-minacts">${[['pistas', s.sub === 'off' ? 'Sin sub' : s.sub.toUpperCase()], ['velocidad', speedLabel(s)], ['mejoras', 'Mejoras'], ['mas', 'Más']].map(([id, l]) => `<button type="button" class="nj-a-minbtn" data-act="sheet" data-v="${id}">${ic(SHEETS[id][1])}<span>${esc(l)}</span></button>`).join('')}<button type="button" class="nj-a-minbtn" data-act="aspect-cycle">${ic('aspect_ratio')}<span>${aspectL}</span></button></div></div>`
      : `<div class="nj-a-pbottom is-touch">${seekBar(ctx)}<div class="nj-a-pbar">${timeText(s)}${s.proc !== 'phone' ? `<span class="nj-a-legend"><i aria-hidden="true"></i>Procesado por el PC</span>` : ''}<span class="nj-a-grow"></span></div><div class="nj-cluster nj-a-pills">${sheetBtn(ctx, 'pistas', `Audio JA · ${s.sub === 'off' ? 'Sin subs' : 'Sub ' + s.sub.toUpperCase()}`)}${sheetBtn(ctx, 'velocidad', speedLabel(s))}<button type="button" class="nj-chip nj-a-pchip" data-act="aspect-cycle" aria-pressed="${s.aspect !== 'fit'}">${ic('aspect_ratio', { size: 18 })}${aspectL}</button>${sheetBtn(ctx, 'imagen', 'Imagen')}${sheetBtn(ctx, 'episodios', 'Episodios')}<button type="button" class="nj-chip nj-a-pchip" data-act="noop">${ic('skip_next', { size: 18 })}Siguiente</button></div></div>`;
    return `${top}${rails}${center}${bottom}`;
  }

  function renderReproductor(ctx) {
    const s = pState(ctx), a = isA(ctx), st = ctx.structure;
    const common = `${statsBox(ctx)}${skipBtn(ctx)}${nextCard(ctx)}${hud(ctx)}`;
    if (!a) {
      const minimal = st === 'minimal';
      const stage = `<div class="nj-a-player" tabindex="0" aria-label="Reproductor" data-chrome="${s.chrome}" data-struct="${st}">${videoEl(ctx)}${s.ext ? extCard(ctx) : `${deskTop(ctx, minimal)}${deskBottom(ctx, minimal)}${common}`}${st === 'panel' ? '' : sheet(ctx)}</div>`;
      const inner = st === 'panel' ? `<div class="nj-a-pgrid">${stage}<aside class="nj-a-ppanel" data-region="sheet" aria-label="Pistas y mejoras"><div class="nj-tabs nj-a-ptabs" role="tablist" data-local>${Object.entries(SHEETS).map(([id, [t, i]]) => `<button type="button" role="tab" class="nj-tab" aria-selected="${s.ptab === id}" data-act="ptab" data-v="${id}">${ic(i, { size: 20 })}<span>${esc(t.split(' ')[0])}</span></button>`).join('')}</div><div class="nj-a-scroll" data-keep="ppanel">${SHEET_FN[s.ptab](ctx)}</div></aside></div>` : stage;
      return `<div class="nj-a-root nj-a-repro" data-struct="${st}">${inner}${keysDlg(ctx)}</div>`;
    }
    if (s.pip) return `<div class="nj-a-root nj-a-repro is-pip"><div class="nj-a-pipdesk"><p class="nj-meta">Nijiyome sigue reproduciendo en una ventana flotante mientras usas otra app.</p></div><div class="nj-a-pipwin" role="region" aria-label="Ventana flotante"><img src="${FRAME}" alt="Episodio 2 en ventana flotante" width="1600" height="900"><div class="nj-a-pipctl">${IB('open_in_full', 'Volver a pantalla completa', 'pip')}${playBtn(ctx, 'is-sm')}${IB('close', 'Cerrar y detener', 'noop')}</div></div></div>`;
    if (st === 'panel') {
      return `<div class="nj-a-root nj-a-repro is-vertical"><div class="nj-a-player is-touch is-mini" tabindex="0" aria-label="Reproductor">${videoEl(ctx)}<div class="nj-a-ptop is-touch">${h.iconBtn('arrow_back', 'Volver a la ficha', { attrs: { 'data-go': 'ficha' } })}<span class="nj-a-grow"></span>${IB('picture_in_picture_alt', 'Ventana flotante', 'pip')}</div><div class="nj-a-minicenter">${IB('replay_10', 'Retroceder 10 s', 'back10')}${playBtn(ctx)}${IB('forward_10', 'Avanzar 10 s', 'fwd10')}</div><div class="nj-a-pbottom is-touch is-min">${seekBar(ctx, { thin: true })}<span class="nj-a-time">${fmt(s.pos)} / 24:00</span>${h.iconBtn('fullscreen', 'Pantalla completa horizontal', { attrs: { 'data-act': 'layout-full' } })}</div>${skipBtn(ctx)}${hud(ctx)}</div><div class="nj-a-vinfo">${titleBlock()}<div class="nj-cluster nj-a-pchips is-wrap">${enhChip(ctx)}${srcChip()}${linkPill(ctx)}</div></div><div class="nj-tabs nj-a-ptabs" role="tablist" data-local data-keep="vt">${Object.entries(SHEETS).map(([id, [t, i]]) => `<button type="button" role="tab" class="nj-tab" aria-selected="${s.ptab === id}" data-act="ptab" data-v="${id}">${ic(i, { size: 20 })}<span>${esc(t.split(' ')[0])}</span></button>`).join('')}</div><div class="nj-a-scroll nj-a-vpanel" data-keep="vpanel" data-region="sheet">${SHEET_FN[s.ptab](ctx)}</div>${keysDlg(ctx)}</div>`;
    }
    return `<div class="nj-a-root nj-a-repro is-land"><div class="nj-a-land"><div class="nj-a-player is-touch" tabindex="0" aria-label="Reproductor, pantalla horizontal" data-chrome="${s.chrome}" data-struct="${st}">${videoEl(ctx)}${touchChrome(ctx, st === 'minimal')}${s.locked ? '' : common}${sheet(ctx)}</div></div>${keysDlg(ctx)}</div>`;
  }

  function mountReproductor(el, ctx) {
    const s = pState(ctx), a = isA(ctx);
    /* PLY-01: el reproductor fuerza el tema oscuro AMOLED con la paleta del usuario. */
    const app = el.closest('.nj-app'); if (app) app.setAttribute('data-mode', 'oled');
    const re = () => rr(el, ctx);
    const say = (t) => { s.hud = t; re(); };
    const seekTo = (t) => { s.pos = Math.max(0, Math.min(DUR - 1, t)); if (s.pos < 1380) s.nextCancel = false; };
    const setPreset = (i) => { const p = PRESETS[i]; s.fam = p[0]; if (p[1]) s.mode = p[1]; say(`Escalado: ${p[2]}`); };
    const openSheet = (v) => { if (ctx.structure === 'panel') { s.ptab = v; re(); return; } s.sheet = s.sheet === v ? null : v; re(); };
    wire(el, ctx, {
      seg(name, v) {
        if (name === 'layout') { NJ.go('reproductor', { structure: v }); return; }
        if (name === 'quality') { say(`Calidad ${v}: cambia a la mejor fuente ${v}`); return; }
        const map = { tier: 'tier', sharpen: 'sharpen', fg: 'fg', fgprofile: 'fgProfile', proc: 'proc', aspect: 'aspect', decoder: 'decoder', ass: 'ass' };
        if (map[name]) s[map[name]] = v;
        if (name === 'fg' && !a && v !== 'Desactivada') { s.ext = true; s.sheet = null; }
        if (name === 'proc') s.hud = { phone: 'Ahora en el teléfono, en el mismo punto.', pc: `Ahora ${D.link.pc} lo procesa, en el mismo punto.`, mixto: 'Ahora mixto: el PC escala y el teléfono genera los cuadros.' }[v];
        re();
      },
      chip(name, v) { const map = { audio: 'audio', sub: 'sub', sub2: 'sub2', speed: 'speed', timer: 'timer', mode: 'mode' }; if (map[name]) s[map[name]] = v; if (name === 'speed') s.hud = `Velocidad ${v.replace('.', ',')}×`; if (name === 'sub') s.hud = v === 'off' ? 'Subtítulos: desactivados' : `Subtítulos: ${{ es: 'Español', en: 'Inglés', fr: 'Francés' }[v]}`; re(); },
      sw(id, on) { const map = { stats: 'stats', subbg: 'subBg', translate: 'translate', deband: 'deband', smooth: 'smooth', autoadj: 'autoAdj', loop: 'loop', stable: 'stable' }; if (map[id]) s[map[id]] = on; re(); },
      range(id, v, input, done) {
        if (id === 'vol') { s.vol = v; s.muted = v === 0; if (done) say(`Volumen ${v} %`); return; }
        if (id === 'subsize') { s.subSize = v; const p = el.querySelector('.nj-a-subprev'); if (p) p.style.setProperty('--sz', v / 100); return; }
        if (id === 'subpos') { s.subPos = v; return; }
        if (id === 'zoom') { s.zoom = v; const im = el.querySelector('.nj-a-video img'); if (im) im.style.transform = `scale(${v / 100})`; return; }
        if (id.startsWith('img-')) { s.img[id.slice(4)] = v; const I = s.img; const im = el.querySelector('.nj-a-video img'); if (im) im.style.filter = `brightness(${1 + I.bri / 100}) contrast(${1 + I.con / 100}) saturate(${1 + I.sat / 100}) hue-rotate(${I.hue * 1.8}deg)`; }
      },
      sheet(b) { openSheet(b.dataset.v); },
      ptab(b) { s.ptab = b.dataset.v; re(); },
      'sheet-x'() { s.sheet = null; re(); },
      play() { s.playing = !s.playing; say(s.playing ? 'Reproduciendo' : 'En pausa'); },
      surface() { if (a) { s.chrome = !s.chrome; re(); } else if (s.sheet) { s.sheet = null; re(); } else { s.playing = !s.playing; say(s.playing ? 'Reproduciendo' : 'En pausa'); } },
      back5() { seekTo(s.pos - 5); say('−5 s'); }, fwd5() { seekTo(s.pos + 5); say('+5 s'); },
      back10() { seekTo(s.pos - 10); say('−10 s'); }, fwd10() { seekTo(s.pos + 10); say('+10 s'); },
      seek(b, ev) { if (!ev.detail) return; const r = b.getBoundingClientRect(); const f = r.height > r.width ? (ev.clientY - r.top) / r.height : (ev.clientX - r.left) / r.width; seekTo(f * DUR); re(); },
      skip() { const end = s.pos < 180 ? 180 : 1380; seekTo(end); say(end === 180 ? 'Opening saltado' : 'Ending saltado'); },
      'next-cancel'() { s.nextCancel = true; re(); },
      time() { s.timeLeft = !s.timeLeft; re(); },
      mute() { s.muted = !s.muted; say(s.muted ? 'Silenciado' : `Volumen ${s.vol} %`); },
      fam(b) { s.fam = b.dataset.v; const F = D.enhancements.families.find((f) => f.id === s.fam); if (F) { if (!F.modes.includes(s.mode)) s.mode = F.modes[0]; if (F.tiers.length && !F.tiers.includes(s.tier)) s.tier = F.tiers[1]; } s.hud = `Escalado: ${enhLabel(s)}`; re(); },
      default() { say('Guardado como predeterminado en Ajustes'); },
      delay(b) { const d = Number(b.dataset.v); s.delay = d === 0 ? 0 : s.delay + d; say(`Retraso ${(s.delay / 10).toFixed(1).replace('.', ',')} s`); },
      'sub-reset'() { s.subSize = 100; s.subBg = true; s.subPos = 100; s.ass = 'original'; re(); },
      'img-reset'() { s.img = { bri: 0, con: 0, sat: 0, gam: 0, hue: 0 }; s.zoom = 100; s.aspect = 'fit'; re(); },
      'aspect-cycle'() { const c = ['fit', 'fill', 'stretch']; s.aspect = c[(c.indexOf(s.aspect) + 1) % 3]; say({ fit: 'Ajustar', fill: 'Rellenar', stretch: 'Estirar' }[s.aspect]); },
      'play-ep'(b) { s.sheet = null; say(`Abriendo el Ep. ${b.dataset.v}…`); },
      shot() { say(`Captura guardada en ${a ? 'Pictures/Nijiyome' : 'Imágenes/Nijiyome'}`); },
      'dl-ep'() { say('Ep. 2 en cola de descarga'); },
      keys() { s.keys = true; s.sheet = null; re(); },
      'keys-x'() { s.keys = false; re(); },
      'dlg-stop'() {},
      'send-phone'() { s.playing = false; say(`Enviado a ${D.link.phone}: aparece «Reproducir aquí»`); },
      pip() { if (a) { s.pip = !s.pip; re(); } else say('Ventana flotante de 480×270, siempre visible'); },
      full() { say('Pantalla completa'); },
      'layout-full'() { NJ.go('reproductor', { structure: 'cine' }); },
      lock() { s.locked = true; s.sheet = null; re(); },
      unlock() { s.locked = false; re(); },
      'ext-on'() { s.ext = true; s.sheet = null; if (s.fg === 'Desactivada') s.fg = '×2'; re(); },
      'ext-off'() { s.ext = false; s.fg = 'Desactivada'; say('Mismo punto, en el reproductor integrado'); },
      noop() {},
    });
    const player = el.querySelector('.nj-a-player');
    if (player) {
      player.addEventListener('dblclick', (e) => { if (!a && e.target.closest('.nj-a-video')) say('Pantalla completa'); });
      player.addEventListener('wheel', (e) => { if (a || !e.target.closest('.nj-a-video')) return; e.preventDefault(); seekTo(s.pos + (e.deltaY < 0 ? 5 : -5)); say(e.deltaY < 0 ? '+5 s' : '−5 s'); }, { passive: false });
      const track = player.querySelector('.nj-a-seek-track');
      if (track && !a) track.addEventListener('mousemove', (e) => { const r = track.getBoundingClientRect(); const f = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)); track.style.setProperty('--hx', f); const bub = track.querySelector('.nj-a-seek-bubble'); if (bub) bub.textContent = `${fmt(f * DUR)} · ${chapterAt(f * DUR)}`; });
    }
    el.addEventListener('keydown', (e) => {
      if (e.target.matches('input, textarea')) return;
      if (s.locked) return;
      const k = e.key;
      if ((e.ctrlKey || e.metaKey) && /^[1-6]$/.test(k)) { e.preventDefault(); setPreset(Number(k) - 1); return; }
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const step = a ? 10 : 5;
      const acts = {
        ' ': () => { s.playing = !s.playing; say(s.playing ? 'Reproduciendo' : 'En pausa'); },
        ArrowLeft: () => { seekTo(s.pos - step); say(`−${step} s`); }, ArrowRight: () => { seekTo(s.pos + step); say(`+${step} s`); },
        ArrowUp: () => { s.vol = Math.min(100, s.vol + 5); say(`Volumen ${s.vol} %`); }, ArrowDown: () => { s.vol = Math.max(0, s.vol - 5); say(`Volumen ${s.vol} %`); },
        Escape: () => { if (s.keys) s.keys = false; else if (s.sheet) s.sheet = null; re(); },
        '?': () => { s.keys = !s.keys; re(); }, '/': () => { s.keys = !s.keys; re(); },
        m: () => { s.muted = !s.muted; say(s.muted ? 'Silenciado' : `Volumen ${s.vol} %`); }, f: () => say('Pantalla completa'), s: () => say('Captura guardada'),
        l: () => { s.loop = !s.loop; say(s.loop ? 'Repetir: activado' : 'Repetir: desactivado'); },
        c: () => { s.sub = s.sub === 'off' ? 'es' : 'off'; say(s.sub === 'off' ? 'Subtítulos: desactivados' : 'Subtítulos: Español'); },
        Enter: () => { if ((s.pos >= 90 && s.pos < 180) || (s.pos >= 1290 && s.pos < 1380)) { seekTo(s.pos < 180 ? 180 : 1380); say('Saltado'); } },
      };
      if (k === 'Enter' && e.target.closest('button')) return;
      const fn = acts[k] || acts[k.toLowerCase && k.toLowerCase()];
      if (fn) { e.preventDefault(); fn(); }
    });
    s.hud = null;
  }

  NJ.registerScreen({
    id: 'reproductor', name: 'Reproductor', group: 'Ver', order: 4, platforms: ['desktop', 'android'], fullBleed: true,
    summary: 'Las hojas se agrupan en seis (Pistas, Mejoras, Imagen, Velocidad y temporizador, Episodios, Más); Mejoras se vuelve un selector visual por familias con niveles, la velocidad y el temporizador pasan a chips y el diseño de controles (incluido un panel lateral fijo) se elige desde el propio reproductor.',
    parity: 'PLY-01, PLY-04 (chip de fuente), PLY-05…PLY-29, PLA-01…PLA-19, PLA-23, PLA-27, PLE-01…PLE-17, PLE-26, PLE-27. Fuera de la maqueta, sin cambios de comportamiento: PLY-02 y PLY-03 (errores y datos móviles), PLY-20 (pantalla final), PLY-22 y PLE-20 (pretraducción), PLA-20…PLA-22 (notificación y reproductor externo de Android), PLA-26, PLE-18/19 (editor completo de estilo y fuentes, se abre con «Más opciones de estilo»), PLY-28 (MPRIS).',
    structures: [
      { id: 'cine', name: 'Cine', desc: 'Controles superpuestos clásicos; en Android, horizontal con transporte central y zonas de gesto.' },
      { id: 'minimal', name: 'Mínimo', desc: 'Una barra baja con el tiempo, la barra de progreso y accesos con icono.' },
      { id: 'panel', name: 'Panel', desc: 'Panel lateral fijo para pistas y mejoras (monitor grande); en Android, reproductor vertical.' },
    ],
    render: renderReproductor,
    mount: mountReproductor,
  });
})();
