/* Runtime del explorador de rediseño de Nijiyome. Ver CONTRATO.md. */
(function () {
  const D = window.NJ_DATA;
  const store = {
    get(k, d) { try { const v = localStorage.getItem('nj-x-' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('nj-x-' + k, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ } },
  };

  const NJ = (window.NJ = {
    data: D, directions: {}, cards: {}, screens: {}, labs: {}, screenState: {},
    state: Object.assign({ dir: 'tanabata', platform: 'desktop', mode: 'dark', palette: null, motion: 'full', card: null, nav: 'rail', screen: 'inicio', structures: {}, both: false }, store.get('state', {})),
  });

  /* ---------------- ayudantes ---------------- */
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const attrs = (o) => Object.entries(o || {}).map(([k, v]) => (v === true ? ` ${k}` : v === false || v == null ? '' : ` ${k}="${esc(v)}"`)).join('');
  let uid = 0;
  const h = {
    esc, attrs,
    icon(name, o = {}) { return `<span class="nj-ic${o.fill ? ' is-fill' : ''}${o.cls ? ' ' + o.cls : ''}" aria-hidden="true"${o.size ? ` style="font-size:${o.size}px"` : ''}>${esc(name)}</span>`; },
    status(id) { return D.statuses.find((s) => s.id === id) || { id, name: id }; },
    statusChip(id) { return id ? `<span class="nj-status" data-status="${esc(id)}">${esc(h.status(id).name)}</span>` : ''; },
    poster(item, o = {}) { return `<img src="${esc(item.poster)}" alt="${esc(o.alt != null ? o.alt : 'Póster de ' + item.title)}" loading="lazy" decoding="async"${o.w ? ` width="${o.w}"` : ''}>`; },
    progress(v, o = {}) { const pct = Math.round((v || 0) * 100); return `<div class="nj-progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${pct}"${o.label ? ` aria-label="${esc(o.label)}"` : ''} style="--v:${v || 0}"><i></i></div>`; },
    seg(name, options, selected) {
      return `<div class="nj-seg" role="radiogroup" aria-label="${esc(name)}">${options.map((o) => `<button type="button" role="radio" data-value="${esc(o.id)}" aria-checked="${o.id === selected}">${o.icon ? h.icon(o.icon, { size: 18 }) : ''}${esc(o.label)}</button>`).join('')}</div>`;
    },
    chips(name, options, selected = [], o = {}) {
      const sel = new Set([].concat(selected));
      return `<div class="nj-cluster" role="group" aria-label="${esc(name)}"${o.multi ? ' data-multi' : ''}>${options.map((op) => `<button type="button" class="nj-chip" data-value="${esc(op.id)}" aria-pressed="${sel.has(op.id)}">${sel.has(op.id) && o.multi ? h.icon('check', { size: 18 }) : op.icon ? h.icon(op.icon, { size: 18 }) : ''}${esc(op.label)}${op.count != null ? ` <span class="nj-muted">${esc(op.count)}</span>` : ''}</button>`).join('')}</div>`;
    },
    toggle(id, label, on, o = {}) { return `<label class="nj-toggle" for="${esc(id)}"><span>${esc(label)}${o.desc ? `<span class="nj-toggle-desc">${esc(o.desc)}</span>` : ''}</span><input type="checkbox" role="switch" id="${esc(id)}"${on ? ' checked' : ''}></label>`; },
    slider(id, label, o = {}) { return `<div class="nj-slider"><div class="nj-slider-head"><label for="${esc(id)}">${esc(label)}</label><output for="${esc(id)}">${esc(o.value)}${esc(o.unit || '')}</output></div><input type="range" id="${esc(id)}" min="${o.min ?? 0}" max="${o.max ?? 100}" step="${o.step ?? 1}" value="${o.value ?? 0}"></div>`; },
    btn(label, o = {}) { return `<button type="button" class="nj-btn${o.kind ? ' is-' + o.kind : ''}"${attrs(o.attrs)}>${o.icon ? h.icon(o.icon) : ''}${esc(label)}</button>`; },
    iconBtn(icon, label, o = {}) { return `<button type="button" class="nj-iconbtn${o.kind ? ' is-' + o.kind : ''}" aria-label="${esc(label)}" title="${esc(label)}"${attrs(o.attrs)}>${h.icon(icon, { fill: o.fill })}</button>`; },
    ep(n) { return `Ep. ${n}`; },
    uid(p = 'nj') { uid += 1; return `${p}-${uid}`; },
    card(item, opts = {}) {
      const style = NJ.cards[NJ.state.card] || NJ.cards.canon;
      try { return style.render(item, Object.assign({ size: 'm', context: 'row', show: {} }, opts)); } catch (e) { console.error(e); return NJ.cards.canon.render(item, opts); }
    },
  };
  NJ.h = h;

  /* ---------------- registro ---------------- */
  NJ.registerDirection = (def) => { NJ.directions[def.id] = def; (def.fonts || []).forEach(loadFont); refreshSoon(); };
  NJ.registerCard = (def) => { NJ.cards[def.id] = def; refreshSoon(); };
  NJ.registerScreen = (def) => { NJ.screens[def.id] = def; refreshSoon(); };
  NJ.registerLab = (def) => { NJ.labs[def.id] = def; refreshSoon(); };
  var fontsLoaded = new Set();
  function loadFont(url) { if (fontsLoaded.has(url)) return; fontsLoaded.add(url); const l = document.createElement('link'); l.rel = 'stylesheet'; l.href = url; document.head.appendChild(l); }

  /* Canon: Material 3 tal cual, definido aquí para que siempre exista. */
  NJ.registerDirection({
    id: 'canon', name: 'Canon Material 3', track: 'canon', tagline: 'La categoría jugada derecho: Material 3 sin adornos, con la semilla violeta actual.',
    summary: 'La salida estándar. Sirve como vara para medir las otras direcciones y como base si preferís lo familiar.',
    palettes: [{ id: 'violeta', name: 'Violeta Nijiyome', swatch: '#8b5cf6' }],
    defaultCard: 'canon',
    notes: { thesis: 'Material 3 Expressive de libro, como cualquier app nativa de Android.', signature: 'Ninguno propio: forma, color y movimiento de Material.', motion: 'Container transform y shared axis estándar.', android: 'Componentes M3 sin cambios.', risks: 'Se parece a todas las apps de la categoría; la identidad queda en el logo.', compose: 'MaterialTheme con colorScheme dinámico o estático; nada a medida.' },
  });
  NJ.registerCard({
    id: 'canon', name: 'Material', dir: 'canon', desc: 'Póster 2:3 con esquinas M3, estado y progreso superpuestos.',
    render(item, o) {
      const s = o.show || {};
      const sub = o.context === 'continue' ? `${item.resume ? 'Ep. ' + item.resume.ep + ' · quedan ' + item.resume.left : 'Ep. ' + ((item.progress || 0) + 1)}` : `${item.format || ''} · ${item.year || ''}`;
      if (o.context === 'continue' || o.context === 'compact') {
        return `<article class="nj-card" tabindex="0" aria-label="${esc(item.title)}" data-style="canon" data-context="${o.context}" data-size="${o.size}" data-status="${esc(item.status || '')}" data-id="${esc(item.id)}"><div class="nj-card-art">${h.poster(item)}</div><div class="nj-stack" style="gap:4px;min-width:0"><span class="nj-card-title">${esc(item.short || item.title)}</span><span class="nj-card-sub">${esc(sub)}</span>${item.resume ? h.progress(item.resume.pct, { label: 'Progreso del episodio' }) : ''}</div>${o.context === 'continue' ? h.iconBtn('play_arrow', 'Reproducir', { kind: 'filled', fill: true, attrs: { 'data-stop': true } }) : ''}</article>`;
      }
      return `<button type="button" class="nj-card" data-style="canon" data-context="${o.context}" data-size="${o.size}" data-status="${esc(item.status || '')}" data-id="${esc(item.id)}"><div class="nj-card-art">${h.poster(item)}<div class="nj-card-badges">${s.status ? h.statusChip(item.status) : ''}</div>${s.progress && item.eps ? h.progress((item.progress || 0) / item.eps) : ''}</div><span class="nj-card-title">${esc(item.short || item.title)}</span><span class="nj-card-sub">${esc(s.next && item.next ? 'Ep. ' + item.next.ep + ' en ' + item.next.in : sub)}</span></button>`;
    },
  });

  /* ---------------- shell de la app ---------------- */
  const NAV = {
    primary: [
      { id: 'inicio', label: 'Inicio', icon: 'home' }, { id: 'explorar', label: 'Explorar', icon: 'explore' }, { id: 'calendario', label: 'Calendario', icon: 'calendar_month' },
      { id: 'biblioteca', label: 'Biblioteca', icon: 'video_library' }, { id: 'manga', label: 'Manga', icon: 'menu_book' }, { id: 'novelas', label: 'Novelas', icon: 'auto_stories' },
    ],
    utility: [
      { id: 'transferencias', label: 'Transfer.', icon: 'swap_vert' }, { id: 'fuentes', label: 'Fuentes', icon: 'extension' }, { id: 'link', label: 'Link', icon: 'devices' }, { id: 'ajustes', label: 'Ajustes', icon: 'settings' },
    ],
    android: [
      { id: 'inicio', label: 'Inicio', icon: 'home' }, { id: 'explorar', label: 'Explorar', icon: 'explore' }, { id: 'calendario', label: 'Calendario', icon: 'calendar_month' },
      { id: 'biblioteca', label: 'Biblioteca', icon: 'video_library' }, { id: 'tu', label: 'Tú', icon: 'person' },
    ],
  };
  NJ.NAV = NAV;
  const navGroupOf = (screenId) => ({ ficha: 'inicio', reproductor: 'inicio', fuentes: 'fuentes', lector: 'manga', perfil: 'tu', bienvenida: 'inicio' }[screenId] || screenId);

  function railItem(it, cur) { return `<button type="button" class="nj-rail-item" data-go="${it.id}"${it.id === cur ? ' aria-current="page"' : ''}><span class="nj-pill">${h.icon(it.icon, { fill: it.id === cur })}</span><span>${esc(it.label)}</span></button>`; }

  NJ.shell = function (ctx, inner, screen) {
    const cur = navGroupOf(screen.id);
    if (ctx.platform === 'android') {
      const items = NAV.android;
      const andCur = ['manga', 'novelas', 'transferencias', 'fuentes', 'link', 'ajustes', 'perfil', 'tu'].includes(cur) ? 'tu' : cur;
      return `<div class="nj-phone"><div class="nj-statusbar"><span>21:04</span><span class="nj-cluster" style="gap:4px">${h.icon('wifi')}${h.icon('signal_cellular_alt')}${h.icon('battery_5_bar')}</span></div><main class="nj-main">${inner}</main>${screen.fullBleed ? '' : `<nav class="nj-bottombar" aria-label="Navegación principal">${items.map((it) => railItem(it, andCur)).join('')}</nav>`}<div class="nj-gesturebar"></div></div>`;
    }
    const top = `<div class="nj-topbar"><span class="nj-brand">${h.icon('arrow_back', { size: 18 })}<span>Nijiyome</span></span><span class="nj-linkchip" title="Link conectado con moto g51 5G">Link · 2 dispositivos</span>${ctx.nav === 'medio' ? `<span style="margin-left:18px">${h.seg('Medio', [{ id: 'anime', label: 'Anime' }, { id: 'manga', label: 'Manga' }, { id: 'novela', label: 'Novela' }], ['manga', 'novelas', 'lector'].includes(cur) ? 'manga' : 'anime')}</span>` : ''}<button type="button" class="nj-cmd" data-cmd>${h.icon('search')}<span>Buscar o ejecutar un comando</span><span class="nj-kbd">Ctrl K</span></button>${h.iconBtn('notifications', 'Notificaciones (3)')}<span class="nj-winctl" aria-hidden="true"><span>${h.icon('remove', { size: 18 })}</span><span>${h.icon('crop_square', { size: 16 })}</span><span>${h.icon('close', { size: 18 })}</span></span></div>`;
    if (screen.fullBleed) return `<div class="nj-shell is-bleed" data-nav="${ctx.nav}"><main class="nj-main">${inner}</main></div>`;
    const primary = ctx.nav === 'medio' ? NAV.primary.filter((i) => !['manga', 'novelas'].includes(i.id)) : NAV.primary;
    return `<div class="nj-shell" data-nav="${ctx.nav}">${top}<nav class="nj-rail" aria-label="Navegación principal">${primary.map((it) => railItem(it, cur)).join('')}<span class="nj-rail-sep"></span>${NAV.utility.map((it) => railItem(it, cur)).join('')}<button type="button" class="nj-rail-item" data-go="perfil" aria-label="Perfil"><span class="nj-pill"><img src="img/p-psyren.webp" alt="" style="width:30px;height:30px;border-radius:50%;object-fit:cover"></span></button></nav><main class="nj-main">${inner}</main></div>`;
  };

  /* ---------------- marcos ---------------- */
  const SIZES = { desktop: [1440, 900], android: [412, 892] };
  function currentDir() { return NJ.directions[NJ.state.dir] || NJ.directions.canon; }
  function paletteFor(dir) { const ps = dir.palettes || []; return ps.find((p) => p.id === NJ.state.palette) ? NJ.state.palette : (ps[0] && ps[0].id) || 'base'; }
  function cardFor(dir) { return NJ.cards[NJ.state.card] ? NJ.state.card : (dir.defaultCard && NJ.cards[dir.defaultCard] ? dir.defaultCard : 'canon'); }

  NJ.renderFrame = function (host, screenId, platform, o = {}) {
    const screen = NJ.screens[screenId];
    const dir = o.dir ? NJ.directions[o.dir] || currentDir() : currentDir();
    const [w, hgt] = SIZES[platform];
    const structure = (screen && (o.structure || NJ.state.structures[screenId])) || (screen && screen.structures && screen.structures[0] && screen.structures[0].id) || 'base';
    const savedCard = NJ.state.card;
    NJ.state.card = o.card || cardFor(dir);
    const key = screenId + ':' + platform;
    const ctx = { platform, dir: dir.id, structure, mode: NJ.state.mode, motion: NJ.state.motion, card: NJ.state.card, nav: NJ.state.nav, data: D, h, state: (NJ.screenState[key] = NJ.screenState[key] || {}), rerender: () => NJ.renderFrame(host, screenId, platform, o) };
    let inner;
    if (!screen) inner = `<div class="x-empty" style="margin:40px">Esta pantalla todavía no está cargada.</div>`;
    else if (screen.platforms && !screen.platforms.includes(platform)) inner = `<div style="padding:40px" class="nj-meta">Esta pantalla es solo para ${screen.platforms.join(' y ')}.</div>`;
    else { try { inner = screen.render(ctx); } catch (e) { console.error(e); inner = `<div style="padding:40px;color:var(--nj-err)">Error al dibujar ${esc(screenId)}: ${esc(e.message)}</div>`; } }
    const html = screen ? NJ.shell(ctx, inner, screen) : inner;
    host.innerHTML = `<div class="x-scaler" style="width:${w}px;height:${hgt}px"><div class="nj-app" data-dir="${dir.id}" data-platform="${platform}" data-mode="${NJ.state.mode}" data-palette="${paletteFor(dir)}" data-motion="${NJ.state.motion}" data-card="${NJ.state.card}" data-nav="${NJ.state.nav}" data-screen="${esc(screenId)}" data-structure="${esc(structure)}">${html}</div></div>`;
    NJ.state.card = savedCard;
    const app = host.querySelector('.nj-app');
    wireApp(app, ctx);
    if (screen && screen.mount) { try { screen.mount(app.querySelector('.nj-main') || app, ctx); } catch (e) { console.error(e); } }
    if (dir.decorate) { try { dir.decorate(app, ctx); } catch (e) { console.error(e); } }
    fit(host, w, hgt, o.maxW);
    return app;
  };

  function wireApp(app, ctx) {
    app.addEventListener('click', (ev) => {
      const go = ev.target.closest('[data-go]');
      if (go && app.contains(go)) { const id = go.getAttribute('data-go'); const target = ({ tu: 'perfil', novelas: 'manga' })[id] || id; if (NJ.screens[target]) { ev.preventDefault(); NJ.go(target); } return; }
      const card = ev.target.closest('.nj-card[data-id]');
      if (card && !ev.target.closest('[data-stop]') && NJ.screens.ficha && ctx.platform) { /* las pantallas deciden; por defecto, ficha */ if (!card.closest('[data-cards-local]')) { NJ.go('ficha'); return; } }
      if (ev.target.closest('[data-cmd]') && NJ.openPalette) { NJ.openPalette(app, ctx); return; }
      const seg = ev.target.closest('.nj-seg [role="radio"]');
      if (seg && !seg.closest('[data-local]')) { seg.parentElement.querySelectorAll('[role="radio"]').forEach((b) => b.setAttribute('aria-checked', String(b === seg))); }
      const chip = ev.target.closest('.nj-chip');
      if (chip && !chip.closest('[data-local]')) { const multi = chip.parentElement.hasAttribute('data-multi'); if (!multi) chip.parentElement.querySelectorAll('.nj-chip').forEach((c) => c !== chip && c.setAttribute('aria-pressed', 'false')); chip.setAttribute('aria-pressed', String(chip.getAttribute('aria-pressed') !== 'true')); }
      const tab = ev.target.closest('.nj-tab');
      if (tab && !tab.closest('[data-local]')) { tab.parentElement.querySelectorAll('.nj-tab').forEach((t) => t.setAttribute('aria-selected', String(t === tab))); }
    });
    app.addEventListener('input', (ev) => { const r = ev.target; if (r.type === 'range') { const out = app.querySelector(`output[for="${r.id}"]`); if (out) out.textContent = r.value + (out.textContent.replace(/^[\d.,-]+/, '') || ''); } });
  }

  function fit(host, w, hgt, maxW) {
    const sc = host.querySelector('.x-scaler'); if (!sc) return;
    const avail = Math.min(host.parentElement ? host.parentElement.clientWidth : w, maxW || w);
    const s = Math.min(1, avail / w);
    sc.style.transform = `scale(${s})`; host.style.width = w * s + 'px'; host.style.height = hgt * s + 'px';
  }
  window.addEventListener('resize', () => { clearTimeout(fit.t); fit.t = setTimeout(renderPage, 120); });

  NJ.go = function (screenId, o = {}) {
    NJ.state.screen = screenId; if (o.structure) NJ.state.structures[screenId] = o.structure; save();
    if (page() !== 'pantallas') { location.hash = 'pantallas'; } else renderPage();
  };

  /* ---------------- explorador ---------------- */
  const PAGES = [
    { id: 'portada', name: 'Portada', icon: 'flag' }, { id: 'actual', name: 'Cómo está hoy', icon: 'photo_library' },
    { id: 'arquitectura', name: 'Arquitectura', icon: 'account_tree' }, { id: 'direcciones', name: 'Direcciones', icon: 'palette' },
    { id: 'pantallas', name: 'Pantallas', icon: 'devices' }, { id: 'tarjetas', name: 'Tarjetas', icon: 'style' },
    { id: 'patrones', name: 'Patrones', icon: 'touch_app' }, { id: 'movimiento', name: 'Movimiento', icon: 'animation' },
    { id: 'paridad', name: 'Paridad', icon: 'fact_check' }, { id: 'notas', name: 'Implementación', icon: 'integration_instructions' },
  ];
  const page = () => { const id = (location.hash || '').slice(1); return PAGES.find((p) => p.id === id) ? id : 'portada'; };
  function save() { store.set('state', { dir: NJ.state.dir, platform: NJ.state.platform, mode: NJ.state.mode, palette: NJ.state.palette, motion: NJ.state.motion, card: NJ.state.card, nav: NJ.state.nav, screen: NJ.state.screen, structures: NJ.state.structures, both: NJ.state.both }); }

  function xseg(key, label, options, value) {
    return `<div class="x-ctl" data-ctl="${key}"><span id="lbl-${key}">${esc(label)}</span><div class="x-seg" role="group" aria-labelledby="lbl-${key}">${options.map((o) => `<button type="button" data-set="${key}" data-value="${esc(o.id)}" aria-pressed="${o.id === value}"${o.title ? ` title="${esc(o.title)}"` : ''}>${o.swatch ? `<span class="x-sw" style="background:${esc(o.swatch)}"></span>` : ''}${esc(o.label)}</button>`).join('')}</div></div>`;
  }
  function renderBar() {
    const dir = currentDir();
    const dirs = ['tanabata', 'obi', 'transit', 'sumi', 'canon'].filter((id) => NJ.directions[id]).map((id) => ({ id, label: NJ.directions[id].name }));
    const pals = (dir.palettes || []).map((p) => ({ id: p.id, label: p.name, swatch: p.swatch }));
    const cards = Object.values(NJ.cards).map((c) => ({ id: c.id, label: c.name }));
    const bar = document.getElementById('x-bar');
    if (!bar.dataset.init) { bar.dataset.init = '1'; if (window.matchMedia && window.matchMedia('(max-width: 860px)').matches) bar.classList.add('is-collapsed'); }
    const summary = `${dir.name} · ${({ desktop: 'Escritorio', android: 'Android' })[NJ.state.platform]} · ${({ light: 'Claro', dark: 'Oscuro', oled: 'OLED' })[NJ.state.mode]}`;
    bar.innerHTML = `<button type="button" class="x-bar-toggle" data-bar-toggle aria-expanded="${!bar.classList.contains('is-collapsed')}"><span class="nj-ic" aria-hidden="true">tune</span><span>Opciones de la maqueta</span><span class="x-bar-sum">${esc(summary)}</span></button>` + [
      xseg('dir', 'Dirección', dirs, NJ.state.dir),
      xseg('platform', 'Plataforma', [{ id: 'desktop', label: 'Escritorio' }, { id: 'android', label: 'Android' }], NJ.state.platform),
      xseg('mode', 'Tema', [{ id: 'light', label: 'Claro' }, { id: 'dark', label: 'Oscuro' }, { id: 'oled', label: 'OLED' }], NJ.state.mode),
      pals.length > 1 ? xseg('palette', 'Paleta', pals, paletteFor(dir)) : '',
      xseg('card', 'Tarjeta', [{ id: '', label: 'La de la dirección' }].concat(cards), NJ.state.card || ''),
      xseg('motion', 'Movimiento', [{ id: 'full', label: 'Completo' }, { id: 'calm', label: 'Calmo' }, { id: 'perf', label: 'Rendimiento', title: 'Modo para equipos de gama baja' }], NJ.state.motion),
      xseg('nav', 'Navegación', [{ id: 'rail', label: 'Riel' }, { id: 'medio', label: 'Por medio' }, { id: 'comando', label: 'Comandos' }], NJ.state.nav),
    ].join('');
  }
  document.addEventListener('click', (ev) => {
    const t = ev.target.closest('[data-bar-toggle]');
    if (t) { const bar = document.getElementById('x-bar'); bar.classList.toggle('is-collapsed'); t.setAttribute('aria-expanded', String(!bar.classList.contains('is-collapsed'))); return; }
    const b = ev.target.closest('[data-set]'); if (!b) return;
    const k = b.getAttribute('data-set'); let v = b.getAttribute('data-value');
    if (k === 'dir') { NJ.state.palette = null; }
    NJ.state[k] = v || null; if (k === 'card' && !v) NJ.state.card = null;
    save(); renderBar(); renderPage();
  });

  function renderNav() {
    const cur = page();
    document.getElementById('x-nav').innerHTML = PAGES.map((p) => `<a href="#${p.id}"${p.id === cur ? ' aria-current="page"' : ''}>${h.icon(p.icon)}<span>${esc(p.name)}</span></a>`).join('');
  }

  var PAGE_RENDER = {};
  function renderPage() {
    renderNav();
    const id = page(); const el = document.getElementById('x-page');
    const fn = PAGE_RENDER[id];
    if (NJ.labs[id]) { el.innerHTML = ''; NJ.labs[id].render(el); return; }
    el.innerHTML = fn ? fn() : `<div class="x-empty">Sección en preparación.</div>`;
    if (PAGE_RENDER[id + ':mount']) PAGE_RENDER[id + ':mount'](el);
  }
  NJ.renderPage = renderPage;
  var refreshT; function refreshSoon() { clearTimeout(refreshT); refreshT = setTimeout(() => { if (document.getElementById('x-bar')) { renderBar(); renderPage(); } }, 30); }

  /* Pantallas */
  const GROUPS = ['Ver', 'Descubrir', 'Tu colección', 'Leer', 'Fuentes y equipo', 'Configurar'];
  PAGE_RENDER.pantallas = function () {
    const list = Object.values(NJ.screens).sort((a, b) => (GROUPS.indexOf(a.group) - GROUPS.indexOf(b.group)) || ((a.order || 0) - (b.order || 0)));
    if (!list.length) return `<div class="x-empty">Las pantallas se están cargando.</div>`;
    if (!NJ.screens[NJ.state.screen]) NJ.state.screen = list[0].id;
    const s = NJ.screens[NJ.state.screen];
    const groups = {}; list.forEach((x) => (groups[x.group] = groups[x.group] || []).push(x));
    const structs = s.structures || [];
    const curStruct = NJ.state.structures[s.id] || (structs[0] && structs[0].id);
    return `<div class="x-screens"><nav class="x-screenlist" aria-label="Pantallas">${Object.entries(groups).map(([g, xs]) => `<div><h4>${esc(g)}</h4>${xs.map((x) => `<button type="button" data-screen="${x.id}" aria-pressed="${x.id === s.id}"><span>${esc(x.name)}</span><span class="x-count">${(x.structures || []).length > 1 ? (x.structures || []).length + ' alt.' : ''}</span></button>`).join('')}</div>`).join('')}</nav>
      <section class="x-stage"><div class="x-stage-head"><div style="display:grid;gap:4px"><h1 style="font-size:24px">${esc(s.name)}</h1>${s.summary ? `<p class="x-lede">${esc(s.summary)}</p>` : ''}</div>
      <div class="x-seg" role="group" aria-label="Vista"><button type="button" data-both="0" aria-pressed="${!NJ.state.both}">Una plataforma</button><button type="button" data-both="1" aria-pressed="${!!NJ.state.both}">Escritorio y Android juntos</button></div></div>
      ${structs.length ? `<div class="x-structs" role="group" aria-label="Estructuras">${structs.map((st) => `<button type="button" class="x-struct" data-struct="${st.id}" aria-pressed="${st.id === curStruct}"><b>${esc(st.name)}</b><span>${esc(st.desc || '')}</span></button>`).join('')}</div>` : ''}
      <div class="x-frames" id="x-frames"></div>${s.parity ? `<p class="x-note">Paridad: ${esc(s.parity)}</p>` : ''}</section></div>`;
  };
  PAGE_RENDER['pantallas:mount'] = function (el) {
    el.querySelectorAll('[data-screen]').forEach((b) => b.addEventListener('click', () => { NJ.state.screen = b.dataset.screen; save(); renderPage(); }));
    el.querySelectorAll('[data-struct]').forEach((b) => b.addEventListener('click', () => { NJ.state.structures[NJ.state.screen] = b.dataset.struct; save(); renderPage(); }));
    el.querySelectorAll('[data-both]').forEach((b) => b.addEventListener('click', () => { NJ.state.both = b.dataset.both === '1'; save(); renderPage(); }));
    const frames = el.querySelector('#x-frames'); if (!frames) return;
    const plats = NJ.state.both ? ['desktop', 'android'] : [NJ.state.platform];
    const stage = el.querySelector('.x-stage');
    plats.forEach((p) => {
      const wrap = document.createElement('div'); wrap.className = 'x-frame'; wrap.style.flex = p === 'desktop' ? '1 1 0' : '0 0 auto'; wrap.style.minWidth = '0';
      wrap.innerHTML = `<div class="x-frame-cap">${h.icon(p === 'desktop' ? 'desktop_windows' : 'smartphone', { size: 16 })}<span>${p === 'desktop' ? 'Escritorio · 1440 × 900' : 'Android · 412 × 892 dp'}</span></div><div class="x-device ${p === 'android' ? 'is-android' : ''}"></div>`;
      frames.appendChild(wrap);
      const dev = wrap.querySelector('.x-device');
      const maxW = p === 'android' ? (NJ.state.both ? 330 : 412) : (stage.clientWidth - (NJ.state.both ? 330 + 48 : 0));
      NJ.renderFrame(dev, NJ.state.screen, p, { maxW });
    });
  };

  /* Direcciones */
  PAGE_RENDER.direcciones = function () {
    const order = ['tanabata', 'obi', 'transit', 'sumi', 'canon'];
    const ds = order.map((id) => NJ.directions[id]).filter(Boolean);
    const trackName = { free: 'Identidad nueva', identity: 'Mantiene la identidad actual', canon: 'Salida estándar' };
    return `<header style="display:grid;gap:8px"><h1>Direcciones visuales</h1><p class="x-lede">Cada dirección presta a la app tipo, paleta, densidad y un movimiento distintivo. La navegación y los controles son los mismos en todas, así que se pueden comparar pantalla por pantalla. Las tarjetas se pueden cruzar entre direcciones desde la barra superior.</p></header>
    <div class="x-dirgrid">${ds.map((d) => `<article class="x-dir" aria-current="${d.id === NJ.state.dir}"><div class="x-dir-preview" data-preview="${d.id}"></div><div class="x-dir-body"><div class="x-cluster" style="display:flex;gap:8px;flex-wrap:wrap;align-items:center"><h2>${esc(d.name)}</h2><span class="x-tag ${d.track === 'free' ? 'is-accent' : ''}">${esc(trackName[d.track] || '')}</span></div><p>${esc(d.tagline || '')}</p>${d.summary ? `<p class="x-note">${esc(d.summary)}</p>` : ''}<div style="display:flex;gap:6px;flex-wrap:wrap">${(d.palettes || []).map((p) => `<span class="x-tag"><span class="x-sw" style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${esc(p.swatch)}"></span>${esc(p.name)}</span>`).join('')}</div>${d.notes ? `<dl>${[['thesis', 'Idea'], ['signature', 'Movimiento distintivo'], ['motion', 'Animación'], ['android', 'En Android'], ['risks', 'Riesgo honesto'], ['compose', 'En Compose']].filter(([k]) => d.notes[k]).map(([k, l]) => `<dt>${l}</dt><dd>${esc(d.notes[k])}</dd>`).join('')}</dl>` : ''}<div><button type="button" class="x-btn is-primary" data-set="dir" data-value="${d.id}">Usar esta dirección</button></div></div></article>`).join('')}</div>`;
  };
  PAGE_RENDER['direcciones:mount'] = function (el) {
    el.querySelectorAll('[data-preview]').forEach((host) => {
      const id = host.dataset.preview; const box = document.createElement('div'); box.className = 'x-device'; box.style.borderRadius = '0'; box.style.boxShadow = 'none'; host.appendChild(box);
      const saved = NJ.state.dir; NJ.state.dir = id; const savedPal = NJ.state.palette; NJ.state.palette = null;
      NJ.renderFrame(box, 'inicio', 'desktop', { dir: id, maxW: host.clientWidth || 360 });
      NJ.state.dir = saved; NJ.state.palette = savedPal;
      box.querySelectorAll('button,a,input').forEach((b) => b.setAttribute('tabindex', '-1'));
    });
  };

  /* Tarjetas */
  PAGE_RENDER.tarjetas = function () {
    const cs = Object.values(NJ.cards);
    return `<header style="display:grid;gap:8px"><h1>Tarjetas</h1><p class="x-lede">Cada estilo en los cinco contextos donde aparece una tarjeta. Cualquier estilo se puede usar con cualquier dirección desde la barra superior.</p></header>${cs.map((c) => `<section class="x-panel"><div style="display:flex;gap:10px;align-items:baseline;flex-wrap:wrap"><h2>${esc(c.name)}</h2><span class="x-tag">${esc((NJ.directions[c.dir] || {}).name || c.dir)}</span></div>${c.desc ? `<p class="x-note">${esc(c.desc)}</p>` : ''}<div class="x-device" style="border-radius:12px" data-cardlab="${c.id}"></div></section>`).join('')}`;
  };
  PAGE_RENDER['tarjetas:mount'] = function (el) {
    el.querySelectorAll('[data-cardlab]').forEach((host) => {
      const c = NJ.cards[host.dataset.cardlab]; const dir = NJ.directions[c.dir] || currentDir();
      const items = ['laidoff', 'tempal', 'aonohako', 'shinja', 'kikansha', 'choujun'].map((id) => D.byId[id]);
      const saved = NJ.state.card; NJ.state.card = c.id;
      let inner = '';
      try {
        inner = `<div style="padding:24px;display:grid;gap:22px"><div><div class="nj-meta" style="margin-bottom:8px">Fila · tamaño M, con estado y progreso</div><div class="nj-row" data-cards-local>${items.map((it) => h.card(it, { size: 'm', context: 'row', show: { status: true, progress: true, next: true } })).join('')}</div></div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:22px"><div><div class="nj-meta" style="margin-bottom:8px">Continuar</div><div class="nj-stack" data-cards-local>${h.card(D.byId.tempal, { context: 'continue' })}${h.card(D.byId.laidoff, { context: 'continue' })}</div></div>
        <div><div class="nj-meta" style="margin-bottom:8px">Compacta (listas, búsqueda)</div><div class="nj-stack" data-cards-local>${h.card(D.byId.koori, { context: 'compact', show: { status: true } })}${h.card(D.byId.revo, { context: 'compact', show: { status: true } })}</div></div></div>
        <div><div class="nj-meta" style="margin-bottom:8px">Rejilla · tamaño S</div><div class="nj-grid" style="--nj-cell:112px" data-cards-local>${D.titles.slice(6, 14).map((it) => h.card(it, { size: 's', context: 'grid', show: { status: true } })).join('')}</div></div></div>`;
      } catch (e) { inner = `<div style="padding:20px">Error: ${esc(e.message)}</div>`; }
      NJ.state.card = saved;
      host.innerHTML = `<div class="nj-app" data-dir="${dir.id}" data-mode="${NJ.state.mode}" data-palette="${paletteFor(dir)}" data-motion="${NJ.state.motion}" data-card="${c.id}" data-platform="desktop" style="height:auto">${inner}</div>`;
      if (dir.decorate) { try { dir.decorate(host.querySelector('.nj-app'), { platform: 'desktop', screen: 'tarjetas' }); } catch (e) { /* opcional */ } }
    });
  };

  /* Portada, actual, arquitectura y notas se definen en pages.js para no inflar este archivo. */
  NJ.PAGE_RENDER = PAGE_RENDER;

  function boot() {
    /* Solo para pruebas locales: ?screen=ficha&dir=obi&platform=android&mode=light&structure=ruta&card=obi&both=1 */
    try {
      const q = new URLSearchParams(location.search);
      ['screen', 'dir', 'platform', 'mode', 'motion', 'card', 'nav', 'palette'].forEach((k) => { if (q.get(k)) NJ.state[k] = q.get(k); });
      if (q.get('structure') && q.get('screen')) NJ.state.structures[q.get('screen')] = q.get('structure');
      if (q.get('both')) NJ.state.both = q.get('both') === '1';
    } catch (e) { /* sin parámetros */ }
    document.getElementById('x-nav-wrap').hidden = false;
    renderBar(); renderPage();
    window.addEventListener('hashchange', () => { renderPage(); window.scrollTo(0, 0); });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
