/* Patrones de interacción, Laboratorio de movimiento y paleta de comandos (agente L1). Ver CONTRATO.md. */
(function () {
  'use strict';
  const NJ = window.NJ; if (!NJ) return;
  const D = NJ.data, h = NJ.h, esc = h.esc;
  const RM = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : { matches: false };
  const EASE = 'cubic-bezier(.2,0,0,1)', EMPH = 'cubic-bezier(.05,.7,.1,1)';

  /* ---------------- infraestructura ---------------- */
  const dirNow = () => NJ.directions[NJ.state.dir] || NJ.directions.canon;
  const palNow = (d) => { const ps = d.palettes || []; return ps.find((p) => p.id === NJ.state.palette) ? NJ.state.palette : (ps[0] && ps[0].id) || 'base'; };
  const cardNow = (d) => (NJ.cards[NJ.state.card] ? NJ.state.card : d.defaultCard && NJ.cards[d.defaultCard] ? d.defaultCard : 'canon');
  function appAttrs(platform, o = {}) {
    const d = dirNow();
    return `class="nj-app lab-app${o.cls ? ' ' + o.cls : ''}" data-dir="${esc(d.id)}" data-platform="${platform}" data-mode="${esc(NJ.state.mode)}" data-palette="${esc(palNow(d))}" data-motion="${esc(o.motion || NJ.state.motion || 'full')}" data-card="${esc(cardNow(d))}" data-nav="${esc(NJ.state.nav)}" data-screen="lab"${o.attrs || ''}`;
  }
  function card(item, opts) { const d = dirNow(); const saved = NJ.state.card; NJ.state.card = cardNow(d); try { return h.card(item, opts); } finally { NJ.state.card = saved; } }
  function decorate(root) {
    const d = dirNow(); if (!d.decorate) return;
    root.querySelectorAll('.lab-app').forEach((app) => { try { d.decorate(app, { platform: app.dataset.platform, dir: d.id, screen: 'lab', mode: NJ.state.mode, motion: app.dataset.motion, data: D, h, state: {} }); } catch (e) { /* decoración opcional */ } });
  }
  function motionOf(el) {
    const app = el && el.closest ? el.closest('.nj-app') : null;
    const m = (app && app.dataset.motion) || NJ.state.motion || 'full';
    return RM.matches && m === 'full' ? 'calm' : m;
  }
  function on(root, type, sel, fn, opts) { root.addEventListener(type, (ev) => { const t = ev.target.closest ? ev.target.closest(sel) : null; if (t && root.contains(t)) fn(ev, t); }, opts); }
  function anim(el, kf, o) { if (!el || !el.animate) return { finished: Promise.resolve(), cancel() {} }; return el.animate(kf, Object.assign({ duration: 200, easing: EASE, fill: 'both' }, o)); }
  const norm = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const ic = (n, o) => h.icon(n, o);
  let seq = 0; const uid = (p) => `lab-${p}-${++seq}`;
  /* Coloca un popover dentro de su contenedor sin salirse (8 px de margen), debajo o encima del ancla. */
  function place(pop, box, ax, ay, aw, ah, o = {}) {
    pop.style.visibility = 'hidden'; pop.style.left = '0px'; pop.style.top = '0px';
    const bw = box.clientWidth, bh = box.clientHeight, pw = pop.offsetWidth, ph = pop.offsetHeight;
    let x = o.alignRight ? ax + aw - pw : ax, y = ay + ah + 4, oy = 'top', ox = o.alignRight ? 'right' : 'left';
    if (y + ph > bh - 8 && ay - ph - 4 >= 8) { y = ay - ph - 4; oy = 'bottom'; }
    if (y + ph > bh - 8) y = Math.max(8, bh - 8 - ph);
    if (x + pw > bw - 8) { x = bw - 8 - pw; ox = 'right'; }
    if (x < 8) { x = 8; ox = 'left'; }
    pop.style.left = x + 'px'; pop.style.top = y + 'px'; pop.style.transformOrigin = `${oy} ${ox}`; pop.style.visibility = '';
  }
  function relRect(el, box) { const a = el.getBoundingClientRect(), b = box.getBoundingClientRect(); const s = b.width / (box.offsetWidth || b.width) || 1; return { x: (a.left - b.left) / s, y: (a.top - b.top) / s, w: a.width / s, h: a.height / s }; }
  function popIn(el) { const m = motionOf(el); if (m === 'perf') return; anim(el, m === 'calm' ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 0, transform: 'scale(.96)' }, { opacity: 1, transform: 'none' }], { duration: 150, fill: 'none' }); }
  /* Pulsación larga (táctil y ratón): 450 ms quieto. Suprime el clic siguiente. */
  function longPress(root, sel, fn) {
    let t = null, sx = 0, sy = 0, fired = false;
    root.addEventListener('pointerdown', (ev) => {
      const el = ev.target.closest(sel); if (!el || !root.contains(el) || ev.button > 0 || ev.target.closest('[data-nolong]')) return;
      fired = false; sx = ev.clientX; sy = ev.clientY; clearTimeout(t);
      el.classList.add('is-pressing');
      t = setTimeout(() => { fired = true; el.classList.remove('is-pressing'); if (navigator.vibrate) { try { navigator.vibrate(12); } catch (e) { /* sin vibración */ } } fn(el, ev); }, 450);
      const stop = () => { clearTimeout(t); el.classList.remove('is-pressing'); };
      const mv = (e) => { if (Math.hypot(e.clientX - sx, e.clientY - sy) > 8) stop(); };
      el.addEventListener('pointermove', mv); el.addEventListener('pointerup', stop, { once: true }); el.addEventListener('pointercancel', stop, { once: true }); el.addEventListener('pointerleave', stop, { once: true });
    });
    root.addEventListener('click', (ev) => { if (fired) { fired = false; ev.stopPropagation(); ev.preventDefault(); } }, true);
    root.addEventListener('contextmenu', (ev) => { if (ev.target.closest(sel) && root.closest('[data-platform="android"]')) ev.preventDefault(); });
  }

  /* ---------------- cola de deshacer (compartida por las demos) ---------------- */
  const UNDO = {};
  function undoHost(key) { return `<div class="lab-snackhost" data-undo-host="${key}" role="status" aria-live="polite"></div>`; }
  function pushUndo(app, key, msg, undo, o = {}) {
    const list = (UNDO[key] = UNDO[key] || []);
    const it = { id: ++seq, msg, undo, done: false, at: Date.now() };
    if (undo) { list.unshift(it); if (list.length > 20) list.pop(); }
    showSnack(app, key, it, o);
    return it;
  }
  function showSnack(app, key, it, o = {}) {
    const host = app && app.querySelector(`[data-undo-host="${key}"]`); if (!host) return;
    const list = UNDO[key] || [];
    const older = list.filter((x) => x !== it && !x.done).length;
    host.innerHTML = `<div class="nj-snackbar lab-snack"><span class="lab-snack-msg">${esc(it.msg)}</span>${it.undo ? `<button type="button" class="nj-btn is-text" data-undo="${it.id}">Deshacer</button>` : ''}${older ? `<button type="button" class="nj-btn is-text lab-snack-hist" data-undo-hist aria-expanded="false">Anteriores (${older})</button>` : ''}${h.iconBtn('close', 'Cerrar aviso', { attrs: { 'data-undo-close': '' } })}${it.undo && motionOf(host) !== 'calm' ? '<i class="lab-snack-life" aria-hidden="true"></i>' : ''}</div>`;
    const sn = host.firstElementChild; const m = motionOf(host);
    if (m === 'full') anim(sn, [{ opacity: 0, transform: 'translateY(16px)' }, { opacity: 1, transform: 'none' }], { duration: 220, fill: 'none' });
    else if (m === 'calm') anim(sn, [{ opacity: 0 }, { opacity: 1 }], { duration: 120, fill: 'none' });
    const life = sn.querySelector('.lab-snack-life'); let lifeA = null;
    if (life) lifeA = anim(life, [{ transform: 'scaleX(1)' }, { transform: 'scaleX(0)' }], { duration: 8000, easing: 'linear' });
    clearTimeout(host._t);
    const arm = () => { clearTimeout(host._t); host._t = setTimeout(() => { if (!host.isConnected) return; host.querySelector('.lab-undo-pop') ? null : collapse(host, key); }, lifeA ? 8000 - (lifeA.currentTime || 0) : 8000); };
    sn.addEventListener('pointerenter', () => { clearTimeout(host._t); if (lifeA) lifeA.pause(); });
    sn.addEventListener('pointerleave', () => { if (lifeA) lifeA.play(); arm(); });
    sn.addEventListener('focusin', () => { clearTimeout(host._t); if (lifeA) lifeA.pause(); });
    arm();
    if (!host._wired) { host._wired = true; wireUndo(host, key, o.onChange); }
    host._onChange = o.onChange || host._onChange;
  }
  function collapse(host, key) {
    const live = (UNDO[key] || []).filter((x) => !x.done).length;
    host.innerHTML = live ? `<button type="button" class="nj-chip lab-undo-chip" data-undo-hist aria-expanded="false">${ic('history', { size: 18 })}Deshacer recientes (${live})</button>` : '';
  }
  function wireUndo(host, key) {
    host.addEventListener('click', (ev) => {
      const b = ev.target.closest('button'); if (!b) return;
      if (b.hasAttribute('data-undo')) {
        const it = (UNDO[key] || []).find((x) => String(x.id) === b.dataset.undo);
        if (it && !it.done) { it.done = true; try { it.undo(); } catch (e) { console.error(e); } if (host._onChange) host._onChange(); }
        const pop = host.querySelector('.lab-undo-pop');
        if (pop) { renderHist(host, key); } else { host.innerHTML = `<div class="nj-snackbar lab-snack"><span class="lab-snack-msg">Deshecho: ${esc(it ? it.msg : '')}</span>${h.iconBtn('close', 'Cerrar aviso', { attrs: { 'data-undo-close': '' } })}</div>`; clearTimeout(host._t); host._t = setTimeout(() => host.isConnected && collapse(host, key), 3000); }
        return;
      }
      if (b.hasAttribute('data-undo-hist')) { const open = host.querySelector('.lab-undo-pop'); if (open) { open.remove(); b.setAttribute('aria-expanded', 'false'); collapse(host, key); } else { clearTimeout(host._t); renderHist(host, key); } return; }
      if (b.hasAttribute('data-undo-close')) { clearTimeout(host._t); collapse(host, key); }
    });
    host.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && host.querySelector('.lab-undo-pop')) { ev.stopPropagation(); collapse(host, key); const c = host.querySelector('[data-undo-hist]'); if (c) c.focus(); } });
  }
  function renderHist(host, key) {
    const list = UNDO[key] || [];
    host.innerHTML = `<div class="lab-undo-pop nj-menu" role="dialog" aria-label="Acciones recientes"><div class="lab-undo-head"><b>Acciones recientes</b><span class="nj-meta">Cada una se deshace por separado</span></div><ul class="lab-undo-list">${list.map((x) => `<li><span>${esc(x.msg)}</span>${x.done ? '<span class="nj-meta">Deshecho</span>' : `<button type="button" class="nj-btn is-text" data-undo="${x.id}">Deshacer</button>`}</li>`).join('')}</ul><button type="button" class="nj-btn is-text" data-undo-hist aria-expanded="true">Cerrar</button></div>`;
    const f = host.querySelector('[data-undo]') || host.querySelector('[data-undo-hist]'); if (f) f.focus();
  }

  /* ---------------- paleta de comandos ---------------- */
  const NAV_ICON = {}; [].concat(NJ.NAV ? NJ.NAV.primary.concat(NJ.NAV.utility) : []).forEach((n) => (NAV_ICON[n.id] = n.icon));
  Object.assign(NAV_ICON, { ficha: 'info', reproductor: 'play_circle', lector: 'menu_book', perfil: 'person', bienvenida: 'waving_hand' });
  const SETTINGS = [
    ['Tamaño de subtítulos', 'Subtítulos y audio › Estilo', 'subtitulos', 'sub-size', 'letra texto grande'],
    ['Contorno y sombra de subtítulos', 'Subtítulos y audio › Estilo', 'subtitulos', 'sub-outline', 'borde'],
    ['Idioma de subtítulos', 'Subtítulos y audio › Idiomas', 'subtitulos', 'sub-lang', 'español ingles'],
    ['Calidad mínima de la fuente', 'Fuentes de episodios › Selección automática', 'fuentes', 'min-quality', '1080p resolucion'],
    ['Preferir debrid en caché', 'Fuentes de episodios › Selección automática', 'fuentes', 'prefer-cache', 'torbox real-debrid'],
    ['Anime4K', 'Mejoras de video › Escalado', 'mejoras', 'anime4k', 'shader upscale'],
    ['Generación de cuadros', 'Mejoras de video › Fluidez', 'mejoras', 'framegen', 'lsfg fps'],
    ['Animaciones', 'Apariencia › Movimiento', 'apariencia', 'motion', 'calmo reducir'],
    ['Modo rendimiento', 'Apariencia › Movimiento', 'apariencia', 'perf-mode', 'gama baja lento'],
    ['Puerto de Nijiyome Link', 'Nijiyome Link › Esta PC', 'link', 'link-port', '47321 red'],
    ['Programación de ancho de banda', 'Torrents y descargas › Velocidad', 'transferencias', 'bw-schedule', 'horario limite'],
    ['Carpeta de descargas', 'Torrents y descargas › Almacenamiento', 'transferencias', 'dl-folder', 'ruta directorio'],
    ['Temporizador de apagado', 'Reproducción › Controles', 'reproduccion', 'sleep-timer', 'dormir'],
    ['Contenido adulto', 'Metadatos y descubrir', 'metadatos', 'adult', '18 nsfw'],
    ['Sincronizar con AniList', 'Listas y cuentas', 'cuentas', 'anilist-sync', 'myanimelist mal'],
    ['Iniciar con el sistema', 'Sistema', 'sistema', 'autostart', 'bandeja arranque', 'desktop'],
  ].map(([label, path, section, key, kw, only]) => ({ label, path, section, key, kw, only }));
  function screenList() {
    const regs = Object.values(NJ.screens || {});
    if (regs.length) return regs.map((s) => ({ id: s.id, name: s.name || s.id }));
    return (NJ.NAV ? NJ.NAV.primary.concat(NJ.NAV.utility) : []).map((n) => ({ id: n.id, name: n.label }));
  }
  function sourcesScreen() { const ids = Object.keys(NJ.screens || {}); return ids.find((i) => /fuente/.test(i) && /ep/.test(i)) || ids.find((i) => /^fuentes?-/.test(i)) || 'fuentes'; }
  function goScreen(id, o) { NJ.go(NJ.screens && NJ.screens[id] ? id : (Object.keys(NJ.screens || {}).length ? 'inicio' : id), o); }
  function nextUnwatched() { const it = D.byId.laidoff; return { it, ep: (it.progress || 0) + 1 }; }
  function paletteItems(plat) {
    const nu = nextUnwatched();
    const go = screenList().map((s) => ({ g: 'go', label: s.name, sub: 'Pantalla', icon: NAV_ICON[s.id] || 'arrow_forward', kw: s.id, run: () => goScreen(s.id) }));
    const titles = D.titles.map((t) => ({ g: 'title', label: t.title, short: t.short, sub: `${h.status(t.status).name} · ${t.format}${t.eps ? ` · Ep. ${t.progress || 0} de ${t.eps}` : ''}`, img: t.poster, kw: (t.romaji || '') + ' ' + (t.short || ''), run: () => { NJ.paletteTarget = { type: 'title', id: t.id }; goScreen('ficha'); } }));
    const cmds = [
      { label: 'Marcar episodio como visto', sub: `${nu.it.short} · Ep. ${nu.ep}`, icon: 'check_circle', kw: 'visto progreso', keep: true, run: (app) => palSnack(app, `Ep. ${nu.ep} de ${nu.it.short} marcado como visto`, true) },
      { label: 'Abrir fuentes del episodio', sub: `${nu.it.short} · Ep. ${nu.ep}`, icon: 'dns', kw: 'source torrent debrid', run: () => { NJ.paletteTarget = { type: 'title', id: nu.it.id, ep: nu.ep }; goScreen(sourcesScreen()); } },
      { label: 'Cambiar tema', sub: 'Claro, oscuro u OLED', icon: 'contrast', kw: 'modo oscuro claro oled', step: 'theme' },
      { label: 'Emparejar dispositivo', sub: 'Nijiyome Link · muestra el código QR', icon: 'devices', kw: 'link qr telefono pc', run: () => { NJ.paletteTarget = { type: 'command', id: 'pair' }; goScreen('link'); } },
      { label: 'Pausar todas las descargas', sub: 'Transferencias', icon: 'pause_circle', kw: 'torrent detener', keep: true, run: (app) => palSnack(app, 'Descargas en pausa', true) },
      { label: 'Continuar Tempal', sub: 'Ep. 4 · quedan 18 min', icon: 'play_arrow', kw: 'reanudar reproducir', run: () => { NJ.paletteTarget = { type: 'title', id: 'tempal', ep: 4 }; goScreen('reproductor'); } },
    ].map((c) => Object.assign({ g: 'cmd' }, c));
    const sets = SETTINGS.filter((s) => !s.only || s.only === plat).map((s) => ({ g: 'set', label: s.label, sub: s.path, icon: 'tune', kw: s.kw, hint: 'Ir a la opción', run: () => { NJ.paletteTarget = { type: 'setting', section: s.section, key: s.key }; try { document.dispatchEvent(new CustomEvent('nj:palette-target', { detail: NJ.paletteTarget })); } catch (e) { /* sin eventos */ } goScreen('ajustes'); } }));
    return { go, title: titles, cmd: cmds, set: sets };
  }
  const GROUPS = [['go', 'Ir a', '/'], ['title', 'Títulos', '@'], ['cmd', 'Comandos', '>'], ['set', 'Ajustes', '#']];
  function rank(it, q) {
    if (!q) return 1;
    const l = norm(it.label), s = norm(it.short), k = norm(it.kw);
    if (l.startsWith(q) || s.startsWith(q)) return 100;
    if ((' ' + l).includes(' ' + q) || (' ' + s).includes(' ' + q)) return 80;
    if (l.includes(q) || s.includes(q)) return 60;
    if (k.includes(q) || norm(it.sub).includes(q)) return 40;
    return 0;
  }
  function hl(text, q) {
    if (!q) return esc(text);
    const i = norm(text).indexOf(q); if (i < 0) return esc(text);
    return esc(text.slice(0, i)) + '<mark class="lab-hl">' + esc(text.slice(i, i + q.length)) + '</mark>' + esc(text.slice(i + q.length));
  }
  function palSnack(app, msg, undoable) {
    let host = app.querySelector(':scope > .lab-palsnack');
    if (!host) { host = document.createElement('div'); host.className = 'lab-palsnack'; host.setAttribute('role', 'status'); host.setAttribute('aria-live', 'polite'); app.appendChild(host); }
    host.innerHTML = `<div class="nj-snackbar"><span>${esc(msg)}</span>${undoable ? '<button type="button" class="nj-btn is-text">Deshacer</button>' : ''}</div>`;
    const b = host.querySelector('button'); if (b) b.addEventListener('click', () => { host.innerHTML = `<div class="nj-snackbar"><span>Deshecho</span></div>`; clearTimeout(host._t); host._t = setTimeout(() => host.remove(), 2500); });
    if (motionOf(app) !== 'perf') anim(host.firstElementChild, [{ opacity: 0 }, { opacity: 1 }], { duration: 150, fill: 'none' });
    clearTimeout(host._t); host._t = setTimeout(() => host.isConnected && host.remove(), 6000);
  }
  function closePal(layer, refocus = true) {
    if (!layer || !layer.isConnected) return;
    const opener = layer._opener; layer.remove();
    if (refocus && opener && opener.isConnected && opener.focus) opener.focus();
  }

  NJ.openPalette = function (appEl, ctx) {
    if (!appEl) return null;
    const plat = (ctx && ctx.platform) || appEl.getAttribute('data-platform') || 'desktop';
    const prev = appEl.querySelector(':scope > .lab-pal'); if (prev) { closePal(prev); return null; }
    const items = paletteItems(plat);
    const id = uid('pal');
    const layer = document.createElement('div');
    layer.className = 'lab-pal' + (plat === 'android' ? ' is-android' : '');
    layer._opener = document.activeElement;
    const chips = GROUPS.map(([g, name, p]) => `<button type="button" class="nj-chip" data-scope="${g}" aria-pressed="false">${esc(name)}${plat === 'android' ? '' : ` <span class="nj-kbd">${p}</span>`}</button>`).join('');
    layer.innerHTML = `<div class="lab-pal-scrim" data-pal-close></div><div class="lab-pal-box" role="dialog" aria-modal="true" aria-label="Buscar o ejecutar un comando">
      <div class="lab-pal-field">${plat === 'android' ? h.iconBtn('arrow_back', 'Cerrar búsqueda', { attrs: { 'data-pal-close': '' } }) : ic('search')}<span class="lab-pal-crumb" hidden></span><input type="text" role="combobox" aria-expanded="true" aria-controls="${id}-list" aria-autocomplete="list" autocomplete="off" spellcheck="false" aria-label="Buscar títulos, pantallas, comandos o ajustes" placeholder="Busca títulos, pantallas, comandos o ajustes">${plat === 'android' ? '' : '<span class="nj-kbd">Esc</span>'}</div>
      <div class="lab-pal-scopes nj-cluster" role="group" aria-label="Buscar solo en">${chips}</div>
      <div class="lab-pal-list" id="${id}-list" role="listbox" aria-label="Resultados"></div>
      ${plat === 'android' ? '' : '<div class="lab-pal-foot"><span><span class="nj-kbd">↑</span><span class="nj-kbd">↓</span> moverse</span><span><span class="nj-kbd">Enter</span> abrir</span><span><span class="nj-kbd">Retroceso</span> volver</span><span><span class="nj-kbd">Ctrl K</span> cerrar</span></div>'}
    </div>`;
    appEl.appendChild(layer);
    const input = layer.querySelector('input'), list = layer.querySelector('.lab-pal-list'), crumb = layer.querySelector('.lab-pal-crumb');
    let flat = [], active = 0, scope = null, step = null;
    function themeStep() {
      const cur = NJ.state.mode;
      return [['light', 'Claro', 'light_mode'], ['dark', 'Oscuro', 'dark_mode'], ['oled', 'OLED', 'contrast']].map(([v, l, i]) => ({ g: 'step', label: 'Tema ' + l.toLowerCase(), sub: v === cur ? 'Activo ahora' : 'Aplicar a toda la app', icon: i, current: v === cur, run: () => { const b = document.querySelector(`#x-bar [data-set="mode"][data-value="${v}"]`); if (b) b.click(); } }));
    }
    function compute() {
      let raw = input.value; let sc = scope;
      const pre = GROUPS.find((g) => raw.startsWith(g[2])); if (pre) { sc = pre[0]; raw = raw.slice(1); }
      const q = norm(raw.trim());
      if (step === 'theme') return [{ g: 'step', name: 'Cambiar tema', items: themeStep().filter((it) => rank(it, q) > 0) }];
      const caps = { go: q ? 6 : 6, title: q ? 6 : 3, cmd: q ? 6 : 4, set: q ? 6 : 3 };
      return GROUPS.filter(([g]) => !sc || sc === g).map(([g, name]) => {
        let arr = items[g].map((it) => ({ it, r: rank(it, q) })).filter((x) => x.r > 0);
        if (!q && g === 'title') arr = arr.filter((x) => x.it.sub.startsWith('Viendo'));
        arr.sort((a, b) => b.r - a.r);
        return { g, name, items: arr.slice(0, sc ? 12 : caps[g]).map((x) => x.it) };
      }).filter((gr) => gr.items.length);
    }
    function paint() {
      const groups = compute(); const q = norm(input.value.replace(/^[/@>#]/, '').trim());
      flat = []; let html = '';
      groups.forEach((gr, gi) => {
        html += `<div role="group" aria-labelledby="${id}-g${gi}"><div class="lab-pal-gh" id="${id}-g${gi}">${esc(gr.name)}</div>`;
        gr.items.forEach((it) => {
          const n = flat.length; flat.push(it);
          const lead = it.img ? `<img src="${esc(it.img)}" alt="" width="28" height="42" loading="lazy">` : ic(it.icon || 'arrow_forward');
          const right = it.step ? ic('chevron_right') : it.current ? ic('check') : it.hint && plat !== 'android' ? `<span class="nj-meta">${esc(it.hint)}</span>` : '';
          html += `<div class="lab-pal-opt" role="option" id="${id}-o${n}" data-n="${n}" aria-selected="${n === active}"><span class="lab-pal-lead">${lead}</span><span class="lab-pal-txt"><span class="lab-pal-l">${hl(it.label, q)}</span><span class="lab-pal-s">${esc(it.sub || '')}</span></span>${right}</div>`;
        });
        html += '</div>';
      });
      if (!flat.length) {
        const qq = input.value.replace(/^[/@>#]/, '').trim();
        flat.push({ label: 'Buscar en el catálogo de tus extensiones', run: () => { NJ.paletteTarget = { type: 'search', q: qq }; goScreen('explorar'); } });
        html = `<div class="lab-pal-empty"><p>Nada coincide con «${esc(qq)}» en tu biblioteca, pantallas ni ajustes.</p><div class="lab-pal-opt" role="option" id="${id}-o0" data-n="0" aria-selected="true"><span class="lab-pal-lead">${ic('travel_explore')}</span><span class="lab-pal-txt"><span class="lab-pal-l">Buscar «${esc(qq)}» en tus extensiones</span><span class="lab-pal-s">Abre Explorar con esta búsqueda</span></span></div></div>`;
      }
      if (active >= flat.length) active = flat.length - 1; if (active < 0) active = 0;
      list.innerHTML = html; setActive(active, false);
      crumb.hidden = !step; crumb.textContent = step ? 'Cambiar tema ›' : '';
      layer.querySelectorAll('[data-scope]').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.scope === scope)));
    }
    function setActive(n, scroll = true) {
      active = n;
      list.querySelectorAll('[role="option"]').forEach((o) => o.setAttribute('aria-selected', String(+o.dataset.n === n)));
      const el = list.querySelector(`[data-n="${n}"]`);
      if (el) { input.setAttribute('aria-activedescendant', el.id); if (scroll) el.scrollIntoView({ block: 'nearest' }); }
    }
    function choose(n) {
      const it = flat[n]; if (!it) return;
      if (it.step) { step = it.step; input.value = ''; active = 0; paint(); input.focus(); return; }
      closePal(layer, !it.keep && false);
      if (it.keep && layer._opener && layer._opener.isConnected) layer._opener.focus();
      try { it.run && it.run(appEl); } catch (e) { console.error(e); }
    }
    input.addEventListener('input', () => { active = 0; paint(); });
    layer.addEventListener('keydown', (ev) => {
      if (ev.key === 'ArrowDown') { ev.preventDefault(); setActive((active + 1) % flat.length); }
      else if (ev.key === 'ArrowUp') { ev.preventDefault(); setActive((active - 1 + flat.length) % flat.length); }
      else if (ev.key === 'Home' && ev.ctrlKey) { ev.preventDefault(); setActive(0); }
      else if (ev.key === 'End' && ev.ctrlKey) { ev.preventDefault(); setActive(flat.length - 1); }
      else if (ev.key === 'Enter') { ev.preventDefault(); choose(active); }
      else if (ev.key === 'Escape') { ev.preventDefault(); ev.stopPropagation(); if (step) { step = null; input.value = ''; paint(); } else closePal(layer); }
      else if (ev.key === 'Backspace' && !input.value && (step || scope)) { ev.preventDefault(); step = null; scope = null; paint(); }
      else if (ev.key === 'Tab') { ev.preventDefault(); const f = [...layer.querySelectorAll('input, [data-scope], button[data-pal-close]')]; const i = f.indexOf(document.activeElement); const nx = f[(i + (ev.shiftKey ? -1 : 1) + f.length) % f.length]; if (nx) nx.focus(); }
    });
    layer.addEventListener('click', (ev) => {
      if (ev.target.closest('[data-pal-close]')) { closePal(layer); return; }
      const sc = ev.target.closest('[data-scope]'); if (sc) { scope = scope === sc.dataset.scope ? null : sc.dataset.scope; step = null; active = 0; paint(); input.focus(); return; }
      const o = ev.target.closest('.lab-pal-opt'); if (o) choose(+o.dataset.n);
    });
    list.addEventListener('pointermove', (ev) => { const o = ev.target.closest('.lab-pal-opt'); if (o && +o.dataset.n !== active) setActive(+o.dataset.n, false); });
    paint(); input.focus({ preventScroll: true });
    const m = motionOf(appEl), box = layer.querySelector('.lab-pal-box');
    if (m === 'full') { anim(layer.querySelector('.lab-pal-scrim'), [{ opacity: 0 }, { opacity: 1 }], { duration: 150, fill: 'none' }); anim(box, plat === 'android' ? [{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }] : [{ opacity: 0, transform: 'translateY(-6px) scale(.985)' }, { opacity: 1, transform: 'none' }], { duration: 200, fill: 'none' }); }
    else if (m === 'calm') anim(box, [{ opacity: 0 }, { opacity: 1 }], { duration: 120, fill: 'none' });
    return layer;
  };
  if (!window.__njLabPaletteKey) {
    window.__njLabPaletteKey = true;
    document.addEventListener('keydown', (ev) => {
      if (!(ev.ctrlKey || ev.metaKey) || ev.altKey || (ev.key || '').toLowerCase() !== 'k') return;
      const ae = document.activeElement;
      const app = (ae && ae.closest && ae.closest('.nj-app')) || document.querySelector('#x-frames .nj-app');
      if (!app) return;
      ev.preventDefault(); NJ.openPalette(app, { platform: app.getAttribute('data-platform') });
    });
  }

  /* ---------------- marco de las demos ---------------- */
  const DEMOS = []; const DEMO = {};
  function defDemo(d) { DEMOS.push(d); DEMO[d.id] = d; }
  const STI = { watching: 'play_circle', planning: 'bookmark', completed: 'check_circle', paused: 'pause_circle', dropped: 'cancel', rewatching: 'replay' };
  const stName = (id) => h.status(id).name;
  function live(app, msg) { const l = app.querySelector('.lab-live'); if (l) { l.textContent = ''; setTimeout(() => (l.textContent = msg), 30); } }
  function paintApp(app) {
    const d = DEMO[app.dataset.demo]; const host = app.querySelector('.lab-host'); if (!d || !host) return;
    const ae = document.activeElement; const fk = ae && app.contains(ae) ? ae.getAttribute('data-fk') : null;
    const scrolls = [...host.querySelectorAll('[data-keep-scroll]')].map((s) => [s.getAttribute('data-keep-scroll'), s.scrollTop, s.scrollLeft]);
    try { host.innerHTML = d.body(app.dataset.platform, app); } catch (e) { console.error(e); host.innerHTML = `<p class="nj-meta" style="padding:16px">Error en la demo: ${esc(e.message)}</p>`; }
    scrolls.forEach(([k, t, l]) => { const s = host.querySelector(`[data-keep-scroll="${k}"]`); if (s) { s.scrollTop = t; s.scrollLeft = l; } });
    host.querySelectorAll('.lab-dragitem, .lab-cardwrap').forEach((w) => { if (!w.querySelector('button:not(.lab-cardmore), a[href]')) { w.setAttribute('tabindex', '0'); w.setAttribute('role', 'button'); } });
    if (d.after) { try { d.after(app, app.dataset.platform); } catch (e) { console.error(e); } }
    if (fk) { const f = host.querySelector(`[data-fk="${CSS.escape(fk)}"]`); if (f) f.focus({ preventScroll: true }); }
  }
  function refresh(id) { document.querySelectorAll(`.lab-app[data-demo="${id}"]`).forEach(paintApp); }
  function frame(d, p) {
    const H = p === 'android' ? d.andH || 600 : d.deskH || 440;
    const inner = `<div class="lab-host"></div>${undoHost(d.id)}<p class="lab-live lab-sr" aria-live="polite"></p>`;
    if (p === 'android') return `<div ${appAttrs('android', { cls: 'lab-phone', attrs: ` data-demo="${d.id}"${d.palHost ? ' data-pal-host' : ''}` })} style="height:${H}px"><div class="nj-statusbar"><span>21:04</span><span class="nj-cluster" style="gap:4px">${ic('wifi')}${ic('signal_cellular_alt')}${ic('battery_5_bar')}</span></div>${inner}<div class="nj-gesturebar"></div></div>`;
    return `<div ${appAttrs('desktop', { cls: 'lab-desk', attrs: ` data-demo="${d.id}"${d.palHost ? ' data-pal-host' : ''}` })} style="height:${H}px">${inner}</div>`;
  }
  function section(d) {
    return `<section class="x-panel lab-demo" id="lab-${d.id}" aria-labelledby="lab-${d.id}-h">
      <div class="lab-demo-head"><h2 id="lab-${d.id}-h">${esc(d.title)}</h2>${d.tag ? `<span class="x-tag">${esc(d.tag)}</span>` : ''}</div>
      <dl class="lab-why"><div><dt>Patrón</dt><dd>${d.pattern}</dd></div><div><dt>Reemplaza</dt><dd>${d.replaces}</dd></div><div><dt>En Compose</dt><dd>${d.compose}</dd></div></dl>
      ${d.sim ? d.sim() : ''}
      <div class="lab-pair">
        <figure class="lab-fig is-desk"><figcaption>${ic('desktop_windows', { size: 16 })}<b>Escritorio</b><span>${d.deskHint || ''}</span></figcaption><div class="lab-scroll">${frame(d, 'desktop')}</div></figure>
        <figure class="lab-fig is-and"><figcaption>${ic('smartphone', { size: 16 })}<b>Android</b><span>${d.andHint || ''}</span></figcaption>${frame(d, 'android')}</figure>
      </div></section>`;
  }
  const kbd = (k) => `<span class="nj-kbd">${esc(k)}</span>`;
  function stTiles(cur, o = {}) {
    return `<div class="lab-sttiles" role="radiogroup" aria-label="${esc(o.label || 'Estado en tu lista')}" data-region="status-picker">${D.statuses.map((s) => {
      const bad = o.invalid ? o.invalid(s.id) : null;
      return `<button type="button" role="radio" class="lab-sttile" data-status="${s.id}" data-st="${s.id}" aria-checked="${s.id === cur}" tabindex="${s.id === cur ? 0 : -1}" data-fk="${o.fk || 'st'}-${s.id}"${bad ? ` aria-disabled="true" title="${esc(bad)}"` : ''}>${ic(STI[s.id], { fill: s.id === cur })}<span>${esc(s.name)}</span>${bad ? `<small>${esc(bad)}</small>` : ''}</button>`;
    }).join('')}</div>`;
  }
  function radioKeys(root, sel, onPick) {
    root.addEventListener('keydown', (ev) => {
      const b = ev.target.closest(sel); if (!b) return;
      const keys = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }; if (!(ev.key in keys)) return;
      const all = [...b.parentElement.querySelectorAll(sel)].filter((x) => x.getAttribute('aria-disabled') !== 'true');
      const i = all.indexOf(b); const nx = all[(i + keys[ev.key] + all.length) % all.length];
      if (nx) { ev.preventDefault(); onPick(nx); }
    });
  }

  /* ---- 1. Multiselección de episodios ---- */
  const EPS = D.laidoffEpisodes.map((e) => ({ n: e.n, title: e.title, aired: e.aired, watched: e.watched, dl: [2, 3, 5].includes(e.n), ago: e.ago, dur: e.dur }));
  const SEL = { desktop: { on: false, keys: new Set(), anchor: null, focus: 1, confirm: false, msg: '' }, android: { on: false, keys: new Set(), anchor: null, focus: 1, confirm: false, msg: '' } };
  const epOf = (n) => EPS.find((e) => e.n === n);
  const airedNs = () => EPS.filter((e) => e.aired).map((e) => e.n);
  function selToggle(st, n, range) {
    const vis = airedNs(); if (!vis.includes(n)) return;
    if (range && st.anchor != null) { const a = vis.indexOf(st.anchor), b = vis.indexOf(n); vis.slice(Math.min(a, b), Math.max(a, b) + 1).forEach((k) => st.keys.add(k)); }
    else { if (!st.keys.delete(n)) st.keys.add(n); st.anchor = n; }
    st.confirm = false;
  }
  function epsAction(app, p, act) {
    const st = SEL[p]; const keys = [...st.keys].sort((a, b) => a - b); const opts = { onChange: () => refresh('eps') };
    if (act === 'mode') { st.on = !st.on; if (!st.on) { st.keys.clear(); st.confirm = false; } }
    else if (act === 'exit') { st.on = false; st.keys.clear(); st.confirm = false; }
    else if (act === 'all') { st.on = true; airedNs().forEach((n) => st.keys.add(n)); st.confirm = false; }
    else if (act === 'range') { if (keys.length > 1) { for (let n = keys[0]; n <= keys[keys.length - 1]; n++) if (epOf(n).aired) st.keys.add(n); } }
    else if (act === 'watch' && keys.length) {
      const target = !keys.every((n) => epOf(n).watched); const prev = keys.map((n) => [n, epOf(n).watched]);
      keys.forEach((n) => (epOf(n).watched = target));
      pushUndo(app, 'eps', `${keys.length} ${keys.length === 1 ? 'episodio marcado' : 'episodios marcados'} como ${target ? 'visto' : 'no visto'}${keys.length === 1 ? '' : 's'}`, () => prev.forEach(([n, w]) => (epOf(n).watched = w)), opts);
    } else if (act === 'dl') {
      const t = keys.filter((n) => !epOf(n).dl); if (!t.length) return; t.forEach((n) => (epOf(n).dl = true));
      pushUndo(app, 'eps', `Descargando ${t.length} ${t.length === 1 ? 'episodio' : 'episodios'}`, () => t.forEach((n) => (epOf(n).dl = false)), opts);
    } else if (act === 'rm') { st.confirm = true; }
    else if (act === 'rm-ok') {
      const t = keys.filter((n) => epOf(n).dl); t.forEach((n) => (epOf(n).dl = false)); st.confirm = false;
      pushUndo(app, 'eps', `${t.length} ${t.length === 1 ? 'descarga quitada' : 'descargas quitadas'}`, () => t.forEach((n) => (epOf(n).dl = true)), opts);
    }
    refresh('eps');
    if (act === 'rm') { const b = app.querySelector('[data-act="rm-ok"]'); if (b) b.focus(); }
  }
  defDemo({
    id: 'eps', title: 'Multiselección de episodios', deskH: 520, andH: 640,
    pattern: `Un modo de selección visible con ancla: clic alterna, ${kbd('Shift')}+clic suma el rango, ${kbd('Ctrl')}+${kbd('A')} toma todo lo emitido, ${kbd('Esc')} sale. La barra de lote flota abajo para que la lista no salte, y cada acción deja un Deshacer en una cola que no pisa la anterior.`,
    replaces: 'Marcar visto episodio por episodio, con un control distinto en cada vista de episodios (icono, ⋮ o nada), y un aviso único que reemplaza al anterior.',
    compose: '<code>SelectionState(keys, anchor)</code> inmutable con <code>toggle(key, visible, range)</code>; <code>Modifier.combinedClickable(onLongClick)</code> en Android; barra con <code>AnimatedVisibility(slideInVertically { it / 4 } + fadeIn())</code>; <code>SnackbarHost</code> con historial propio.',
    deskHint: `Clic en el cuadro o ${kbd('Ctrl')}+clic para empezar · ${kbd('Espacio')} alterna · ${kbd('Enter')} reproduce`,
    andHint: 'Mantén pulsado un episodio · la barra superior pasa a modo selección',
    body(p) {
      const st = SEL[p]; const k = st.keys.size; const sel = [...st.keys];
      const allW = k && sel.every((n) => epOf(n).watched); const nDl = sel.filter((n) => !epOf(n).dl).length; const nRm = sel.filter((n) => epOf(n).dl).length;
      const rows = EPS.map((e) => {
        const s = st.keys.has(e.n);
        return `<div class="lab-ep nj-ep" role="option" data-ep="${e.n}" data-watched="${e.watched}" data-aired="${e.aired}" aria-selected="${s}"${e.aired ? '' : ' aria-disabled="true"'} tabindex="${st.focus === e.n ? 0 : -1}" data-fk="ep-${e.n}">
          <span class="lab-ep-check" data-check aria-hidden="true">${ic(s ? 'check_box' : 'check_box_outline_blank', { fill: s })}</span><span class="lab-ep-num">${e.n}</span>
          <span class="lab-ep-main"><span class="lab-ep-t">${esc(e.title || 'Sin título todavía')}</span><span class="nj-meta">${esc(e.ago)} · ${esc(e.dur)}</span></span>
          <span class="lab-ep-flags">${e.dl ? `<span class="nj-badge">${ic('download_done', { size: 14 })}Descargado</span>` : ''}${e.watched ? `<span class="lab-seen">${ic('visibility', { size: 16 })}Visto</span>` : ''}${!e.aired ? '<span class="nj-meta">Sin emitir</span>' : ''}</span></div>`;
      }).join('');
      const list = `<div class="lab-eplist${st.on ? ' is-selecting' : ''}" role="listbox" aria-multiselectable="true" aria-label="Episodios de Laid-Off Mage" data-region="episodes" data-keep-scroll="eps">${rows}</div>`;
      if (p === 'android') {
        const bar = st.on ? `<div class="nj-appbar lab-ctxbar">${h.iconBtn('close', 'Salir de la selección', { attrs: { 'data-act': 'exit', 'data-fk': 'exit' } })}<span class="nj-h2" role="status">${k} ${k === 1 ? 'seleccionado' : 'seleccionados'}</span>${h.iconBtn('linear_scale', 'Seleccionar el rango entre el primero y el último', { attrs: { 'data-act': 'range' } })}${h.iconBtn('select_all', 'Seleccionar todo lo emitido', { attrs: { 'data-act': 'all' } })}</div>`
          : `<div class="nj-appbar"><span class="nj-h2">Laid-Off Mage</span>${h.iconBtn('checklist', 'Seleccionar episodios', { attrs: { 'data-act': 'mode', 'data-fk': 'mode' } })}</div>`;
        const act = st.on ? `<div class="lab-andbar" role="toolbar" aria-label="Acciones para la selección">
          <button type="button" data-act="watch"${k ? '' : ' disabled'}>${ic(allW ? 'visibility_off' : 'visibility')}<span>${allW ? 'No vistos' : 'Vistos'}</span></button>
          <button type="button" data-act="dl"${nDl ? '' : ' disabled'}>${ic('download')}<span>Descargar${nDl ? ' ' + nDl : ''}</span></button>
          ${st.confirm ? `<button type="button" class="is-danger" data-act="rm-ok">${ic('delete')}<span>Quitar ${nRm}</span></button>` : `<button type="button" data-act="rm"${nRm ? '' : ' disabled'}>${ic('delete')}<span>Quitar</span></button>`}</div>` : '';
        return `<div class="lab-body lab-eps is-and">${bar}<p class="nj-meta lab-hintline">${st.msg ? esc(st.msg) : st.on ? 'Toca para sumar o quitar. El icono de rango completa el tramo.' : 'Mantén pulsado un episodio para seleccionar.'}</p>${list}${act}</div>`;
      }
      const batch = st.on ? `<div class="lab-batch" role="toolbar" aria-label="Acciones para la selección">
        <span class="lab-batch-n" role="status">${k ? `${k} ${k === 1 ? 'seleccionado' : 'seleccionados'}` : `Elige episodios: clic o ${kbd('Shift')}+clic`}</span>
        <button type="button" class="nj-btn is-filled" data-act="watch"${k ? '' : ' disabled'}>${ic(allW ? 'visibility_off' : 'visibility')}Marcar ${k || ''} como ${allW ? 'no vistos' : 'vistos'}</button>
        <button type="button" class="nj-btn is-tonal" data-act="dl"${nDl ? '' : ' disabled'}>${ic('download')}${k && !nDl ? 'Ya descargados' : 'Descargar ' + (nDl || '')}</button>
        ${st.confirm ? `<button type="button" class="nj-btn is-danger" data-act="rm-ok">${ic('delete')}Quitar ${nRm} ${nRm === 1 ? 'descarga' : 'descargas'}</button>` : `<button type="button" class="nj-btn is-outline" data-act="rm"${nRm ? '' : ' disabled title="Ninguno de los elegidos está descargado"'}>${ic('delete')}Quitar</button>`}
        <span class="lab-batch-sep"></span><button type="button" class="nj-btn is-text" data-act="all">Todo (${airedNs().length})</button>${h.iconBtn('close', 'Salir de la selección (Esc)', { attrs: { 'data-act': 'exit' } })}</div>` : '';
      return `<div class="lab-body lab-eps"><div class="lab-eps-head"><div><h3 class="nj-h3">Episodios</h3><span class="nj-meta">8 de 12 emitidos · ${((n) => `${n} ${n === 1 ? 'visto' : 'vistos'}`)(EPS.filter((e) => e.watched).length)}</span></div><span class="nj-meta lab-eps-msg">${esc(st.msg)}</span><button type="button" class="nj-btn is-outline" data-act="mode" aria-pressed="${st.on}" data-fk="mode">${ic('checklist')}${st.on ? 'Terminar selección' : 'Seleccionar'}</button></div>${list}${batch}</div>`;
    },
    bind(app, p) {
      const st = SEL[p];
      on(app, 'click', '[data-act]', (ev, b) => { if (!b.disabled) epsAction(app, p, b.dataset.act); });
      on(app, 'click', '.lab-ep', (ev, r) => {
        const n = +r.dataset.ep; st.focus = n; st.msg = '';
        if (r.getAttribute('aria-disabled') === 'true') { st.msg = `El Ep. ${n} todavía no se emite`; refresh('eps'); return; }
        if (st.on) selToggle(st, n, ev.shiftKey);
        else if (p === 'desktop' && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.target.closest('[data-check]'))) { st.on = true; selToggle(st, n, false); }
        else st.msg = `Reproduciendo Ep. ${n} (en la app abre el reproductor)`;
        refresh('eps');
      });
      app.addEventListener('keydown', (ev) => {
        const r = ev.target.closest('.lab-ep');
        if (ev.key === 'Escape' && st.on) { ev.preventDefault(); epsAction(app, p, 'exit'); return; }
        if (!r) return; const n = +r.dataset.ep; const all = EPS.map((e) => e.n); const i = all.indexOf(n);
        if (ev.key === 'ArrowDown' || ev.key === 'ArrowUp') {
          ev.preventDefault(); const nx = all[Math.max(0, Math.min(all.length - 1, i + (ev.key === 'ArrowDown' ? 1 : -1)))]; st.focus = nx;
          if (ev.shiftKey && epOf(nx).aired) { st.on = true; if (st.anchor == null) st.anchor = n; selToggle(st, nx, true); }
          refresh('eps'); const f = app.querySelector(`[data-fk="ep-${nx}"]`); if (f) f.focus();
        } else if (ev.key === ' ') { ev.preventDefault(); if (!epOf(n).aired) return; st.on = true; selToggle(st, n, ev.shiftKey); refresh('eps'); }
        else if (ev.key === 'Enter') { ev.preventDefault(); st.msg = `Reproduciendo Ep. ${n} (en la app abre el reproductor)`; refresh('eps'); }
        else if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === 'a') { ev.preventDefault(); epsAction(app, p, 'all'); }
      });
      longPress(app, '.lab-ep', (r) => { const n = +r.dataset.ep; if (!epOf(n).aired) return; st.on = true; st.keys.add(n); st.anchor = n; st.focus = n; st.msg = ''; refresh('eps'); });
    },
  });

  /* ---- 2. Arrastrar a una lista o estado ---- */
  const DRAG = { view: 'rail', st: Object.fromEntries(D.titles.map((t) => [t.id, t.status])), lists: { finde: ['tempal', 'aonohako', 'koori', 'mahoyo', 'goblin', 'chitose'], amigos: ['aonohako'], doblaje: [] }, ask: true, pref: 'move', kb: null, pop: null, sheet: null };
  const LISTS = [['finde', 'Para el finde'], ['amigos', 'Con amigos'], ['doblaje', 'Revisar doblaje']];
  const listName = (id) => (LISTS.find((l) => l[0] === id) || [0, id])[1];
  const BOARD = ['watching', 'planning', 'paused', 'completed'];
  const boardItems = () => DRAG.lists.finde.concat(['psyren', 'shinja', 'kikansha']).filter((v, i, a) => a.indexOf(v) === i);
  const itemNow = (id) => Object.assign({}, D.byId[id], { status: DRAG.st[id] });
  function dropReason(id, t) {
    const [k, v] = t.split(':');
    if (k === 'st') { if (DRAG.st[id] === v) return `Ya está en ${stName(v)}`; if (v === 'rewatching' && DRAG.st[id] !== 'completed') return 'Solo se revé algo completado'; return null; }
    if (v === 'finde') return 'Es la lista de origen'; if (DRAG.lists[v].includes(id)) return 'Ya está en esta lista'; return null;
  }
  function applyStatus(app, id, v) {
    const prev = DRAG.st[id]; if (prev === v) return; DRAG.st[id] = v;
    pushUndo(app, 'dnd', `${D.byId[id].short} ahora está en ${stName(v)}`, () => (DRAG.st[id] = prev), { onChange: () => refresh('dnd') });
  }
  function applyList(app, id, L, mode) {
    const prev = JSON.parse(JSON.stringify(DRAG.lists));
    if (!DRAG.lists[L].includes(id)) DRAG.lists[L].push(id);
    if (mode === 'move') DRAG.lists.finde = DRAG.lists.finde.filter((x) => x !== id);
    pushUndo(app, 'dnd', `${D.byId[id].short} ${mode === 'move' ? 'movido' : 'copiado'} a ${listName(L)}`, () => (DRAG.lists = prev), { onChange: () => refresh('dnd') });
  }
  function dropOn(app, p, id, target, mods) {
    const why = dropReason(id, target); DRAG.kb = null;
    if (why) { live(app, `No se puede soltar ahí: ${why}`); refresh('dnd'); return; }
    const [k, v] = target.split(':');
    if (k === 'st') applyStatus(app, id, v);
    else { const mode = mods.ctrl ? 'copy' : mods.shift ? 'move' : DRAG.ask ? null : DRAG.pref; if (!mode) DRAG.pop = { p, id, L: v }; else applyList(app, id, v, mode); }
    refresh('dnd');
  }
  function dragWrap(id, ctx) { const it = itemNow(id); return `<div class="lab-dragitem${DRAG.kb === id ? ' is-picked' : ''}" data-drag="${id}">${card(it, { size: ctx === 'compact' ? 'm' : 's', context: ctx, show: { status: true } }).replace('<button type="button" class="nj-card"', `<button type="button" data-fk="drag-${id}" aria-pressed="${DRAG.kb === id}" aria-label="${esc(it.short)}, ${esc(stName(it.status))}. Enter para tomarlo" class="nj-card"`)}</div>`; }
  defDemo({
    id: 'dnd', title: 'Arrastrar un título a una lista o a un estado', deskH: 520, andH: 640,
    pattern: `Al arrastrar, todos los destinos reaccionan antes de soltar: los válidos se marcan, los que no pueden recibir se atenúan y dicen por qué. En una lista, ${kbd('Ctrl')} copia y ${kbd('Shift')} mueve; sin tecla aparece un popover junto al destino. Con teclado: ${kbd('Enter')} toma el título y ${kbd('Enter')} sobre un destino lo suelta.`,
    replaces: 'El cambio de estado solo por pulsación larga o desde la ficha, sin arrastre ni clic derecho, y sin forma de copiar un título entre listas propias.',
    compose: '<code>Modifier.dragAndDropSource</code>/<code>dragAndDropTarget</code> con un payload sellado y un <code>MutableState</code> de arrastre activo para pintar los destinos; <code>Popup</code> con <code>PopupPositionProvider</code> propio; en Android, <code>ModalBottomSheet</code>.',
    deskHint: `Arrastra un póster al riel o entre columnas · ${kbd('Esc')} cancela`, andHint: 'Mantén pulsado un póster o toca ⋮ · elige el estado en la hoja',
    body(p) {
      if (p === 'android') {
        const sh = DRAG.sheet ? itemNow(DRAG.sheet) : null;
        return `<div class="lab-body lab-dnd is-and"><div class="nj-appbar"><span class="nj-h2">Para el finde</span><span class="nj-meta">${DRAG.lists.finde.length} títulos</span></div><p class="nj-meta lab-hintline">Mantén pulsado un título para moverlo.</p>
          <div class="lab-andgrid" data-cards-local data-keep-scroll="dnd">${DRAG.lists.finde.map((id) => { const it = itemNow(id); return `<div class="lab-cardwrap" data-hold="${id}">${card(it, { size: 's', context: 'grid', show: { status: false } })}<button type="button" class="nj-iconbtn lab-cardmore" data-sheet="${id}" data-nolong aria-label="Mover ${esc(it.short)} a…">${ic('more_vert')}</button></div>`; }).join('')}</div>
          ${sh ? `<div class="lab-sheetwrap"><div class="lab-scrim" data-close-sheet></div><div class="nj-sheet lab-sheet" role="dialog" aria-modal="true" aria-label="Mover ${esc(sh.short)} a…" data-region="sheet">
            <div class="lab-sheet-head"><img src="${esc(sh.poster)}" alt="" width="40" height="60"><div><b>${esc(sh.short)}</b><div>${h.statusChip(sh.status)}</div></div></div>
            <h4 class="lab-h4">Estado</h4>${stTiles(sh.status, { invalid: (s) => (s === 'rewatching' && sh.status !== 'completed' ? 'Solo si está completado' : null), fk: 'dst' })}
            <h4 class="lab-h4">Tus listas</h4><div class="nj-cluster" role="group" aria-label="Listas">${LISTS.map(([l, n]) => { const inL = DRAG.lists[l].includes(sh.id); return `<button type="button" class="nj-chip" data-listchip="${l}" aria-pressed="${inL}" data-fk="lc-${l}">${inL ? ic('check', { size: 18 }) : ''}${esc(n)}</button>`; }).join('')}</div>
            <div class="lab-sheet-foot"><button type="button" class="nj-btn is-filled" data-close-sheet>Listo</button></div></div></div>` : ''}</div>`;
      }
      const picked = DRAG.kb ? itemNow(DRAG.kb) : null;
      const top = `<div class="lab-dnd-top"><div class="nj-seg" role="radiogroup" aria-label="Vista">${[['rail', 'Riel y lista'], ['board', 'Tablero por estado']].map(([v, l]) => `<button type="button" role="radio" data-view="${v}" aria-checked="${DRAG.view === v}">${l}</button>`).join('')}</div>
        <label class="lab-check"><input type="checkbox" data-ask${DRAG.ask ? ' checked' : ''}> Preguntar al soltar en una lista</label></div>
        ${picked ? `<div class="lab-picked" role="status">${ic('pan_tool')}<span><b>${esc(picked.short)}</b> tomado. Elige un destino con ${kbd('Tab')} y ${kbd('Enter')}.</span><button type="button" class="nj-btn is-text" data-cancel-kb>Cancelar</button></div>` : ''}`;
      const dst = (t, label, extra, attrs = '') => { const why = DRAG.kb ? dropReason(DRAG.kb, t) : null; return `<button type="button" class="lab-dst" data-drop="${t}"${DRAG.kb ? ` data-valid="${!why}"` : ''}${why ? ` aria-disabled="true" title="${esc(why)}"` : ''}${attrs}>${label}${extra}</button>`; };
      let main;
      if (DRAG.view === 'board') {
        main = `<div class="lab-board">${BOARD.map((s) => { const its = boardItems().filter((id) => DRAG.st[id] === s); const why = DRAG.kb ? dropReason(DRAG.kb, 'st:' + s) : null; return `<section class="lab-col" data-drop="st:${s}" data-status="${s}"${DRAG.kb ? ` data-valid="${!why}"` : ''} aria-label="${esc(stName(s))}, ${its.length} títulos"><h4 class="lab-h4"><span class="lab-dot"></span>${esc(stName(s))}<span class="nj-meta">${its.length}</span>${DRAG.kb ? `<button type="button" class="nj-btn is-text lab-col-drop" data-drop="st:${s}"${why ? ' disabled' : ''}>Soltar aquí</button>` : ''}</h4><div class="lab-col-list" data-cards-local>${its.map((id) => dragWrap(id, 'compact')).join('') || '<p class="nj-meta">Suelta un título aquí</p>'}</div></section>`; }).join('')}</div>`;
      } else {
        main = `<div class="lab-dnd-grid"><nav class="lab-dnd-rail" aria-label="Destinos"><h4 class="lab-h4">Estados</h4>${D.statuses.map((s) => dst('st:' + s.id, `<span class="lab-dot" data-status="${s.id}"></span><span>${esc(s.name)}</span>`, `<span class="nj-meta">${Object.values(DRAG.st).filter((v) => v === s.id).length}</span>`)).join('')}
          <h4 class="lab-h4">Tus listas</h4>${LISTS.map(([l, n]) => dst('list:' + l, `${ic(l === 'finde' ? 'folder_open' : 'folder', { size: 18 })}<span>${esc(n)}</span>`, `<span class="nj-meta">${l === 'finde' ? 'Origen' : DRAG.lists[l].length}</span>`, l === 'finde' ? ' aria-current="true"' : '')).join('')}</nav>
          <div class="lab-dnd-main"><div class="nj-section-head"><h3 class="nj-h3">Para el finde</h3><span class="nj-meta">${DRAG.lists.finde.length} títulos</span></div><div class="lab-dnd-cards" data-cards-local>${DRAG.lists.finde.map((id) => dragWrap(id, 'grid')).join('') || '<p class="nj-meta" data-region="empty">La lista quedó vacía. Deshaz desde el aviso o arrastra algo de vuelta.</p>'}</div></div></div>`;
      }
      const pop = DRAG.pop && DRAG.pop.p === 'desktop' ? `<div class="lab-pop nj-menu lab-droppop" role="dialog" aria-label="Soltar ${esc(D.byId[DRAG.pop.id].short)} en ${esc(listName(DRAG.pop.L))}"><p><b>${esc(D.byId[DRAG.pop.id].short)}</b> en <b>${esc(listName(DRAG.pop.L))}</b></p><div class="lab-droppop-btns"><button type="button" class="nj-btn is-filled" data-popmode="move" data-fk="pop-move">${ic('drive_file_move')}Mover<small>Sale de Para el finde</small></button><button type="button" class="nj-btn is-tonal" data-popmode="copy">${ic('content_copy')}Copiar<small>Queda en las dos</small></button></div><label class="lab-check"><input type="checkbox" data-remember> No volver a preguntar</label><div class="lab-pop-foot"><span class="nj-meta">${kbd('Ctrl')} copia · ${kbd('Shift')} mueve</span><button type="button" class="nj-btn is-text" data-popcancel>Cancelar</button></div></div>` : '';
      return `<div class="lab-body lab-dnd${DRAG.kb ? ' is-dragging' : ''}">${top}${main}${pop}</div>`;
    },
    after(app, p) {
      const pop = app.querySelector('.lab-droppop');
      if (pop) { const t = app.querySelector(`.lab-dst[data-drop="list:${DRAG.pop.L}"]`) || app; const r = relRect(t, app); place(pop, app, r.x + r.w - 8, r.y, 0, 0); popIn(pop); }
      const sh = app.querySelector('.lab-sheet'); if (sh && !app.contains(document.activeElement)) { const f = sh.querySelector('[aria-checked="true"]'); if (f) f.focus(); }
    },
    bind(app, p) {
      const mods = (ev) => ({ ctrl: ev.ctrlKey || ev.metaKey, shift: ev.shiftKey });
      if (p === 'android') {
        longPress(app, '[data-hold]', (el) => { DRAG.sheet = el.dataset.hold; refresh('dnd'); });
        on(app, 'click', '[data-sheet]', (ev, b) => { DRAG.sheet = b.dataset.sheet; refresh('dnd'); });
        on(app, 'click', '[data-hold]', (ev) => !ev.target.closest('[data-sheet]') && live(app, 'Un toque abre la ficha. Mantén pulsado para mover.'));
        on(app, 'click', '[data-close-sheet]', () => { const id = DRAG.sheet; DRAG.sheet = null; refresh('dnd'); const b = app.querySelector(`[data-sheet="${id}"]`); if (b) b.focus(); });
        on(app, 'click', '.lab-sttile', (ev, b) => { if (b.getAttribute('aria-disabled') === 'true') { live(app, b.title); return; } const id = DRAG.sheet; applyStatus(app, id, b.dataset.st); DRAG.sheet = null; refresh('dnd'); });
        radioKeys(app, '.lab-sttile', (b) => b.focus());
        on(app, 'click', '[data-listchip]', (ev, b) => { const id = DRAG.sheet, L = b.dataset.listchip; const prev = JSON.parse(JSON.stringify(DRAG.lists)); const inL = DRAG.lists[L].includes(id); DRAG.lists[L] = inL ? DRAG.lists[L].filter((x) => x !== id) : DRAG.lists[L].concat(id); if (L === 'finde' && inL) DRAG.sheet = null; pushUndo(app, 'dnd', `${D.byId[id].short} ${inL ? 'quitado de' : 'agregado a'} ${listName(L)}`, () => (DRAG.lists = prev), { onChange: () => refresh('dnd') }); refresh('dnd'); });
        app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && DRAG.sheet) { ev.preventDefault(); DRAG.sheet = null; refresh('dnd'); } });
        return;
      }
      on(app, 'click', '[data-view]', (ev, b) => { DRAG.view = b.dataset.view; DRAG.kb = null; refresh('dnd'); });
      on(app, 'change', '[data-ask]', (ev, b) => { DRAG.ask = b.checked; });
      on(app, 'click', '[data-cancel-kb]', () => { const id = DRAG.kb; DRAG.kb = null; refresh('dnd'); const f = app.querySelector(`[data-fk="drag-${id}"]`); if (f) f.focus(); });
      on(app, 'click', '[data-popmode]', (ev, b) => { const pp = DRAG.pop; DRAG.pop = null; if (app.querySelector('[data-remember]:checked')) { DRAG.ask = false; DRAG.pref = b.dataset.popmode; } applyList(app, pp.id, pp.L, b.dataset.popmode); refresh('dnd'); });
      on(app, 'click', '[data-popcancel]', () => { DRAG.pop = null; refresh('dnd'); });
      on(app, 'click', '.lab-dragitem', (ev, c) => { if (app._dragged) return; const id = c.dataset.drag; DRAG.kb = DRAG.kb === id ? null : id; if (DRAG.kb) live(app, `${D.byId[id].short} tomado. Elige un destino.`); refresh('dnd'); });
      on(app, 'click', '[data-drop]', (ev, b) => { if (b.tagName !== 'BUTTON') return; if (!DRAG.kb) { live(app, 'Arrastra un título aquí, o tómalo con Enter y elige este destino.'); return; } dropOn(app, p, DRAG.kb, b.dataset.drop, mods(ev)); });
      app.addEventListener('keydown', (ev) => { if (ev.key !== 'Escape') return; if (DRAG.pop) { DRAG.pop = null; refresh('dnd'); ev.preventDefault(); } else if (DRAG.kb) { DRAG.kb = null; refresh('dnd'); ev.preventDefault(); } });
      app.addEventListener('pointerdown', (ev) => { if (DRAG.pop && !ev.target.closest('.lab-droppop')) { DRAG.pop = null; refresh('dnd'); } });
      /* arrastre con puntero */
      let d = null;
      const targetAt = (x, y) => { const el = document.elementFromPoint(x, y); return el && el.closest ? el.closest('[data-drop]') : null; };
      app.addEventListener('pointerdown', (ev) => { const w = ev.target.closest('.lab-dragitem'); if (!w || ev.button !== 0) return; d = { id: w.dataset.drag, w, sx: ev.clientX, sy: ev.clientY, on: false, pid: ev.pointerId }; app._dragged = false; });
      app.addEventListener('pointermove', (ev) => {
        if (!d) return;
        if (!d.on) {
          if (Math.hypot(ev.clientX - d.sx, ev.clientY - d.sy) < 6) return;
          d.on = true; app._dragged = true; try { d.w.setPointerCapture(d.pid); } catch (e) { /* sin captura */ }
          const body = app.querySelector('.lab-dnd'); body.classList.add('is-dragging'); d.w.classList.add('is-source');
          body.querySelectorAll('[data-drop]').forEach((t) => t.setAttribute('data-valid', String(!dropReason(d.id, t.dataset.drop))));
          const it = D.byId[d.id]; d.ghost = document.createElement('div'); d.ghost.className = 'lab-ghost'; d.ghost.innerHTML = `<img src="${esc(it.poster)}" alt="" width="36" height="54"><span><b>${esc(it.short)}</b><small></small></span>`; app.appendChild(d.ghost);
        }
        const r = app.getBoundingClientRect(); d.ghost.style.transform = `translate(${ev.clientX - r.left + 12}px, ${ev.clientY - r.top + 8}px)`;
        const t = targetAt(ev.clientX, ev.clientY); app.querySelectorAll('[data-over]').forEach((x) => x !== t && x.removeAttribute('data-over'));
        let lbl = 'Suelta sobre un estado o una lista';
        if (t) { t.setAttribute('data-over', ''); const why = dropReason(d.id, t.dataset.drop); const isList = t.dataset.drop.startsWith('list:'); lbl = why || (isList ? (ev.ctrlKey || ev.metaKey ? 'Copiar' : ev.shiftKey ? 'Mover' : DRAG.ask ? 'Mover o copiar…' : DRAG.pref === 'copy' ? 'Copiar' : 'Mover') : `Pasar a ${stName(t.dataset.drop.slice(3))}`); d.ghost.dataset.bad = String(!!why); }
        d.ghost.querySelector('small').textContent = lbl;
      });
      const end = (ev, ok) => {
        if (!d) return; const dd = d; d = null;
        if (!dd.on) return;
        if (dd.ghost) dd.ghost.remove();
        const t = ok ? targetAt(ev.clientX, ev.clientY) : null;
        if (t) dropOn(app, p, dd.id, t.dataset.drop, mods(ev)); else refresh('dnd');
        setTimeout(() => (app._dragged = false), 0);
      };
      app.addEventListener('pointerup', (ev) => end(ev, true));
      app.addEventListener('pointercancel', (ev) => end(ev, false));
      app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && d && d.on) { end(ev, false); } });
    },
  });

  /* ---- 3. Reordenar con barra de inserción ---- */
  const REO = {
    tab: 'queue',
    queue: [
      { id: 'q1', name: 'Grand Blue Dreaming S02 (E01-E06) 1080p', meta: 'Descargando · 37 % · 8,9 MB/s', icon: 'downloading' },
      { id: 'q2', name: 'Ao no Hako S2 · Ep. 9', meta: 'En cola · HLS', icon: 'schedule' },
      { id: 'q3', name: 'Koori no Jouheki · Ep. 7', meta: 'En cola · debrid', icon: 'schedule' },
      { id: 'q4', name: 'Tempal · Ep. 4', meta: 'En cola · torrent', icon: 'schedule' },
      { id: 'q5', name: 'PSYREN · Ep. 3', meta: 'En cola · torrent', icon: 'schedule' },
      { id: 'q6', name: 'Chitose-kun 2 · Ep. 7', meta: 'En cola · HLS', icon: 'schedule' },
      { id: 'q7', name: 'Dawang Raoming 3 · Ep. 8', meta: 'En cola · torrent', icon: 'schedule' },
      { id: 'q8', name: 'Horegusuri · Ep. 6', meta: 'En pausa', icon: 'pause' },
    ],
    rows: ['Continuar viendo', 'Esta noche', 'Siguiendo', 'Top 10 de la temporada', 'Para ti', 'Porque viste Ao no Hako', 'De tus extensiones', 'Próximos estrenos'].map((n, i) => ({ id: 'r' + i, name: n, meta: i < 2 ? 'Fija arriba en la app actual' : 'Fila de Inicio', icon: 'view_day' })),
  };
  function reoMove(app, from, to, why) {
    const arr = REO[REO.tab]; if (to > from) to -= 1; if (to === from || to < 0 || to >= arr.length) return false;
    const prev = arr.slice(); const [it] = arr.splice(from, 1); arr.splice(to, 0, it);
    const key = REO.tab;
    pushUndo(app, 'reo', `${it.name}: posición ${to + 1} de ${arr.length}`, () => (REO[key] = prev), { onChange: () => refresh('reo') });
    live(app, `${it.name} movido a la posición ${to + 1} de ${arr.length}${why ? '' : ''}`);
    return it;
  }
  function flipRefresh(app) {
    const m = motionOf(app); const before = {};
    if (m === 'full') document.querySelectorAll('.lab-app[data-demo="reo"] .lab-reo-row').forEach((r) => (before[r.closest('.lab-app').dataset.platform + r.dataset.id] = r.getBoundingClientRect().top));
    refresh('reo');
    if (m !== 'full') return;
    document.querySelectorAll('.lab-app[data-demo="reo"] .lab-reo-row').forEach((r) => { const b = before[r.closest('.lab-app').dataset.platform + r.dataset.id]; if (b == null) return; const dy = b - r.getBoundingClientRect().top; if (Math.abs(dy) > 1) anim(r, [{ transform: `translateY(${dy}px)` }, { transform: 'none' }], { duration: 200, fill: 'none' }); });
  }
  defDemo({
    id: 'reo', title: 'Reordenar la cola de descargas o las filas de Inicio', deskH: 440, andH: 600,
    pattern: `El asa arrastra la fila; las demás no se mueven mientras arrastras, solo una barra de 2 px marca dónde caerá. Cerca de los bordes la lista se desplaza sola. Con teclado, ${kbd('Alt')}+${kbd('↑')}/${kbd('↓')} mueve la fila enfocada y se anuncia la nueva posición.`,
    replaces: 'Una cola sin orden manual y un Inicio de más de 15 tipos de fila que no se pueden reordenar ni ocultar.',
    compose: '<code>LazyColumn</code> con <code>Modifier.animateItem()</code> al soltar (no durante); barra con <code>Modifier.drawBehind</code>; auto-scroll con <code>LazyListState.scrollBy</code> en un <code>LaunchedEffect</code> por tiempo; en Android, acciones de TalkBack «Subir» y «Bajar» con <code>semantics { customActions }</code>.',
    deskHint: `Arrastra por ${ic('drag_indicator', { size: 14 })} · ${kbd('Alt')}+${kbd('↑')} ${kbd('↓')} con la fila enfocada`, andHint: 'Arrastra por el asa de la derecha',
    body(p) {
      const arr = REO[REO.tab];
      const rows = arr.map((it, i) => `<li class="lab-reo-row" data-i="${i}" data-id="${it.id}" tabindex="0" data-fk="reo-${it.id}" aria-label="${esc(it.name)}, posición ${i + 1} de ${arr.length}">${p === 'android' ? '' : `<span class="lab-handle" data-handle title="Arrastrar para reordenar" aria-hidden="true">${ic('drag_indicator')}</span>`}<span class="lab-reo-pos" aria-hidden="true">${i + 1}</span>${ic(it.icon, { size: 20, cls: 'lab-reo-ic' })}<span class="lab-reo-main"><b>${esc(it.name)}</b><span class="nj-meta">${esc(it.meta)}</span></span>${p === 'android' ? `<span class="lab-handle" data-handle aria-hidden="true">${ic('drag_handle')}</span>` : ''}</li>`).join('');
      return `<div class="lab-body lab-reo${p === 'android' ? ' is-and' : ''}">${p === 'android' ? '<div class="nj-appbar"><span class="nj-h2">Ordenar</span></div>' : ''}<div class="nj-tabs" role="tablist" aria-label="Qué ordenar">${[['queue', 'Cola de descargas'], ['rows', 'Filas de Inicio']].map(([v, l]) => `<button type="button" class="nj-tab" role="tab" data-tab="${v}" aria-selected="${REO.tab === v}" data-fk="tab-${v}">${l}</button>`).join('')}</div>
        <div class="lab-reo-wrap"><ol class="lab-reo-list" role="list" aria-label="${REO.tab === 'queue' ? 'Cola de descargas' : 'Filas de Inicio'}" data-keep-scroll="reo">${rows}</ol><i class="lab-insbar" hidden></i></div></div>`;
    },
    bind(app) {
      on(app, 'click', '[data-tab]', (ev, b) => { REO.tab = b.dataset.tab; refresh('reo'); });
      app.addEventListener('keydown', (ev) => {
        const r = ev.target.closest('.lab-reo-row'); if (!r) return; const i = +r.dataset.i; const n = REO[REO.tab].length;
        if (ev.altKey && (ev.key === 'ArrowUp' || ev.key === 'ArrowDown')) { ev.preventDefault(); const to = ev.key === 'ArrowUp' ? i - 1 : i + 2; if ((ev.key === 'ArrowUp' && i === 0) || (ev.key === 'ArrowDown' && i === n - 1)) return; reoMove(app, i, to); flipRefresh(app); }
        else if (!ev.altKey && (ev.key === 'ArrowUp' || ev.key === 'ArrowDown')) { ev.preventDefault(); const nx = r.parentElement.children[i + (ev.key === 'ArrowUp' ? -1 : 1)]; if (nx) nx.focus(); }
      });
      let d = null, timer = null, vel = 0, lastT = 0;
      const list = () => app.querySelector('.lab-reo-list');
      function compute(y) {
        const L = list(); const rows = [...L.children]; let to = rows.length;
        for (let k = 0; k < rows.length; k++) { const rr = rows[k].getBoundingClientRect(); if (y < rr.top + rr.height / 2) { to = k; break; } }
        const bar = app.querySelector('.lab-insbar'); const wrap = app.querySelector('.lab-reo-wrap').getBoundingClientRect();
        const ref = rows[Math.min(to, rows.length - 1)].getBoundingClientRect();
        const top = (to < rows.length ? ref.top : ref.bottom) - wrap.top; bar.hidden = false; bar.style.transform = `translateY(${top - 1}px)`;
        d.to = to;
      }
      function tick() { const now = performance.now(); const dt = (now - lastT) / 1000; lastT = now; if (!d || !vel) { clearInterval(timer); timer = null; return; } list().scrollTop += vel * dt; if (d.y != null) compute(d.y); }
      app.addEventListener('pointerdown', (ev) => {
        const hd = ev.target.closest('[data-handle]'); if (!hd || ev.button > 0) return; ev.preventDefault();
        const r = hd.closest('.lab-reo-row'); d = { i: +r.dataset.i, row: r, to: null, y: ev.clientY };
        try { hd.setPointerCapture(ev.pointerId); } catch (e) { /* sin captura */ } r.classList.add('is-lifted'); compute(ev.clientY);
      });
      app.addEventListener('pointermove', (ev) => {
        if (!d) return; d.y = ev.clientY; compute(ev.clientY);
        const lr = list().getBoundingClientRect(); const EDGE = 40, SPEED = 900;
        const up = ev.clientY - lr.top, dn = lr.bottom - ev.clientY;
        vel = up < EDGE ? -SPEED * (1 - Math.max(up, 0) / EDGE) : dn < EDGE ? SPEED * (1 - Math.max(dn, 0) / EDGE) : 0;
        if (vel && !timer) { lastT = performance.now(); timer = setInterval(tick, 16); }
      });
      const end = (ok) => { if (!d) return; const dd = d; d = null; vel = 0; clearInterval(timer); timer = null; const bar = app.querySelector('.lab-insbar'); if (bar) bar.hidden = true; dd.row.classList.remove('is-lifted'); if (ok && dd.to != null) { if (reoMove(app, dd.i, dd.to)) flipRefresh(app); } };
      app.addEventListener('pointerup', () => end(true)); app.addEventListener('pointercancel', () => end(false));
      app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && d) end(false); });
    },
  });

  /* ---- 4. Soltar archivos en la ventana ---- */
  const KINDS = {
    kai: { label: 'Extensión .kai', icon: 'extension', verb: 'Suelta para instalar la extensión', title: 'Instalar la extensión', detail: 'Compatible con Tatakai. Queda activa al instalarla y la puedes revisar o desactivar en Fuentes.', acts: [['install', 'Instalar', 'filled']], done: 'Extensión instalada' },
    torrent: { label: 'Archivo .torrent', icon: 'cloud_download', verb: 'Suelta para agregar el torrent', title: 'Agregar torrent', detail: 'Puedes elegir archivos antes de empezar. Se transmite mientras baja.', acts: [['watch', 'Agregar y ver', 'filled'], ['dl', 'Solo descargar', 'tonal'], ['files', 'Elegir archivos…', 'outline']], done: 'Torrent agregado' },
    magnet: { label: 'Enlace magnet', icon: 'link', verb: 'Suelta para agregar el enlace', title: 'Agregar enlace magnet', detail: 'Los metadatos llegan de los pares; los archivos se muestran en cuanto estén.', acts: [['watch', 'Agregar y ver', 'filled'], ['dl', 'Solo descargar', 'tonal']], done: 'Enlace magnet agregado' },
    video: { label: 'Video local', icon: 'movie', verb: 'Suelta para reproducir', title: 'Reproducir archivo local', detail: 'Parece Laid-Off Mage, Ep. 8. Emparejarlo guarda el progreso en tu lista.', acts: [['pair', 'Reproducir y emparejar', 'filled'], ['play', 'Solo reproducir', 'tonal']], done: 'Reproduciendo el archivo' },
    sub: { label: 'Subtítulo', icon: 'subtitles', verb: 'Suelta para cargar el subtítulo', title: 'Cargar subtítulo', detail: 'No hay nada reproduciéndose. Se guarda para Laid-Off Mage Ep. 8 y se carga al abrirlo.', acts: [['save', 'Guardar para el Ep. 8', 'filled']], done: 'Subtítulo guardado para el Ep. 8' },
    none: { label: 'No compatible', icon: 'block', verb: 'Nijiyome no abre este tipo de archivo', title: 'Este archivo no se puede abrir', detail: 'Acepta .kai, .torrent, enlaces magnet, video (.mkv, .mp4, .webm) y subtítulos (.ass, .srt, .vtt).', acts: [], done: '' },
    any: { label: 'Archivo', icon: 'draft', verb: 'Suelta para ver qué hacer con esto', title: '', detail: '', acts: [] },
  };
  const SIMS = [['kai', 'toko-1.4.0.kai'], ['torrent', 'Grand Blue S02 [1080p].torrent'], ['magnet', 'magnet:?xt=urn:btih:…&dn=Koori no Jouheki 07'], ['video', 'Laid-Off Mage - 08 [1080p].mkv'], ['sub', 'Laid-Off Mage - 08.es.ass'], ['none', 'capturas.zip']];
  function classify(name) {
    const n = String(name || '').trim().toLowerCase();
    if (n.startsWith('magnet:')) return 'magnet';
    const ext = (n.match(/\.([a-z0-9]+)$/) || [])[1];
    if (ext === 'kai') return 'kai'; if (ext === 'torrent') return 'torrent';
    if (['mkv', 'mp4', 'webm', 'avi', 'mov'].includes(ext)) return 'video';
    if (['ass', 'srt', 'vtt', 'ssa'].includes(ext)) return 'sub';
    return 'none';
  }
  const DROP = { desktop: null, android: null };
  function dropSim(kind, name) {
    const id = ++seq;
    DROP.desktop = { phase: 'over', kind, name, id }; DROP.android = { phase: 'result', kind, name, id };
    refresh('drop');
    setTimeout(() => { if (DROP.desktop && DROP.desktop.id === id) { DROP.desktop.phase = 'result'; refresh('drop'); } }, 900);
  }
  defDemo({
    id: 'drop', title: 'Soltar archivos en la ventana', deskH: 440, andH: 600,
    pattern: 'Una sola capa de destino cubre la ventana mientras arrastras: reconoce el tipo por el nombre y dice qué va a pasar antes de soltar. Al soltar ofrece la acción concreta, y lo que no reconoce lo rechaza con la lista de formatos que sí abre.',
    replaces: 'El arrastre externo limitado a Búsqueda por imagen y Extensiones; los .torrent, magnet y videos solo entraban por el sistema o por argumentos.',
    compose: 'Escritorio: <code>Modifier.dragAndDropTarget(shouldStartDragAndDrop = { it.awtTransferable… })</code> sobre la ventana raíz; Android: <code>intent-filter</code> de <code>ACTION_VIEW</code> y <code>ACTION_SEND</code> con los MIME de cada tipo y la misma hoja de acción.',
    deskHint: 'Arrastra un archivo real desde tu explorador, o usa los botones', andHint: 'Llega por Abrir con o Compartir, sin capa de arrastre',
    sim: () => `<div class="lab-sim" role="group" aria-label="Simular un archivo"><span>Simular:</span>${SIMS.map(([k, n]) => `<button type="button" class="x-btn" data-sim="${k}" data-name="${esc(n)}">${k === 'magnet' ? 'Soltar un magnet' : k === 'none' ? 'Soltar un .zip' : k === 'video' ? 'Soltar un video' : k === 'sub' ? 'Soltar un subtítulo' : 'Soltar un .' + k}</button>`).join('')}</div>`,
    body(p) {
      const st = DROP[p]; const K = st ? KINDS[st.kind] : null;
      const bg = `<div class="lab-drop-bg" aria-hidden="true">${(p === 'android' ? ['tempal', 'aonohako', 'koori', 'psyren', 'chitose', 'laidoff'] : ['tempal', 'aonohako', 'koori', 'psyren', 'chitose', 'laidoff', 'magical', 'horegusuri', 'dawang', 'seitokai', 'goblin', 'shinja', 'mahoyo', 'kikansha']).map((id) => `<img src="${esc(D.byId[id].poster)}" alt="" width="90" height="135" loading="lazy">`).join('')}</div>`;
      const result = st && st.phase === 'result' && K ? `<div class="lab-dropdlg${p === 'android' ? ' nj-sheet' : ' nj-dialog'}${st.kind === 'none' ? ' is-error' : ''}" role="dialog" aria-modal="true" aria-labelledby="dd-${p}">
          <div class="lab-dropdlg-head">${ic(K.icon, { size: 28 })}<div><span class="nj-badge">${p === 'android' ? 'Desde Archivos · ' : ''}${esc(K.label)}</span><h3 class="nj-h3" id="dd-${p}">${esc(K.title)}</h3></div></div>
          <p class="lab-mono">${esc(st.name)}</p><p class="nj-meta">${esc(K.detail)}</p>
          <div class="lab-dropdlg-acts">${K.acts.map(([a, l, k]) => `<button type="button" class="nj-btn is-${k}" data-drop-act="${a}" data-fk="dact-${a}">${esc(l)}</button>`).join('')}<button type="button" class="nj-btn is-text" data-drop-close data-fk="dclose">${st.kind === 'none' ? 'Entendido' : 'Cancelar'}</button></div></div>` : '';
      if (p === 'android') return `<div class="lab-body lab-drop is-and"><div class="nj-appbar"><span class="nj-h2">Inicio</span></div>${bg}<p class="nj-meta lab-hintline">Abre un archivo con Nijiyome desde otra app, o usa los botones de arriba.</p>${result ? `<div class="lab-sheetwrap"><div class="lab-scrim" data-drop-close></div>${result}</div>` : ''}</div>`;
      const over = st && st.phase === 'over' ? `<div class="lab-droplayer${st.kind === 'none' ? ' is-bad' : ''}" aria-hidden="true"><div class="lab-droplayer-in">${ic(K.icon, { size: 40 })}<b>${esc(K.verb)}</b>${st.name ? `<span class="lab-mono">${esc(st.name)}</span>` : ''}<span class="nj-badge">${esc(K.label)}</span></div></div>` : '';
      return `<div class="lab-body lab-drop">${bg}<p class="nj-meta lab-hintline">Arrastra aquí un archivo desde tu explorador. Reconoce .kai, .torrent, magnet, video y subtítulos.</p>${over}${result ? `<div class="lab-dlgwrap"><div class="lab-scrim" data-drop-close></div>${result}</div>` : ''}</div>`;
    },
    after(app, p) { const st = DROP[p]; if (st && st.phase === 'result' && !st.focused) { st.focused = true; const f = app.querySelector('[data-drop-act], [data-drop-close]'); if (f) f.focus({ preventScroll: true }); } },
    bind(app, p) {
      on(app, 'click', '[data-drop-close]', () => { DROP[p] = null; refresh('drop'); });
      on(app, 'click', '[data-drop-act]', (ev, b) => { const st = DROP[p]; const K = KINDS[st.kind]; DROP[p] = null; refresh('drop'); pushUndo(app, 'drop', `${K.done}: ${st.name.length > 40 ? st.name.slice(0, 38) + '…' : st.name}`, () => {}, {}); });
      app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && DROP[p]) { ev.preventDefault(); DROP[p] = null; refresh('drop'); } });
      if (p !== 'desktop') return;
      let depth = 0;
      const guess = (dt) => { const it = dt && dt.items && dt.items[0]; const t = it ? it.type : ''; if (t === 'application/x-bittorrent') return 'torrent'; if (/^video\//.test(t)) return 'video'; return 'any'; };
      app.addEventListener('dragenter', (ev) => { ev.preventDefault(); depth++; if (depth === 1) { DROP.desktop = { phase: 'over', kind: guess(ev.dataTransfer), name: '' }; refresh('drop'); } });
      app.addEventListener('dragover', (ev) => { ev.preventDefault(); if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'copy'; });
      app.addEventListener('dragleave', () => { depth = Math.max(0, depth - 1); if (!depth && DROP.desktop && DROP.desktop.phase === 'over') { DROP.desktop = null; refresh('drop'); } });
      app.addEventListener('drop', (ev) => {
        ev.preventDefault(); depth = 0; const dt = ev.dataTransfer;
        const name = (dt.files && dt.files[0] && dt.files[0].name) || (dt.getData('text/uri-list') || '').split('\n')[0] || dt.getData('text/plain') || 'archivo';
        DROP.desktop = { phase: 'result', kind: classify(name), name }; refresh('drop');
      });
    },
  });

  /* ---- 5. Selector de estado de lista ---- */
  const STP = { cur: 'planning', prog: 0, open: false };
  const stpItem = () => D.byId.goblin;
  function stpSet(app, v) {
    const prev = { cur: STP.cur, prog: STP.prog }; if (prev.cur === v) return;
    STP.cur = v; let extra = '';
    if (v === 'completed') { STP.prog = stpItem().eps; extra = `, progreso ${STP.prog} de ${stpItem().eps}`; }
    if (v === 'rewatching') { STP.prog = 0; extra = ', progreso a 0'; }
    pushUndo(app, 'stp', `Goblin: ${stName(v)}${extra}`, () => { STP.cur = prev.cur; STP.prog = prev.prog; }, { onChange: () => refresh('stp') });
    refresh('stp');
  }
  defDemo({
    id: 'stp', title: 'Selector de estado de lista', deskH: 400, andH: 560,
    pattern: 'Los seis estados están a la vista con su color, su icono y su nombre, como un grupo de radio real: un toque o las flechas. El progreso va al lado con un paso a paso, no en otro diálogo.',
    replaces: 'El estado escondido detrás del botón «En lista», con el editor solo por pulsación larga y sin clic derecho.',
    compose: 'Escritorio: <code>SingleChoiceSegmentedButtonRow</code> con seis <code>SegmentedButton</code>; Android: <code>LazyVerticalGrid(GridCells.Fixed(3))</code> de <code>Modifier.selectable(role = Role.RadioButton)</code> dentro de <code>Modifier.selectableGroup()</code>.',
    deskHint: 'Arriba, cómo es hoy; abajo, la propuesta · las flechas cambian el estado', andHint: 'Rejilla de 3 × 2, cada estado de 64 dp de alto',
    body(p) {
      const it = stpItem();
      const stepper = `<div class="lab-stepper" role="group" aria-label="Progreso"><span class="nj-meta">Episodio</span><button type="button" class="nj-iconbtn" data-prog="-1" aria-label="Restar un episodio"${STP.prog <= 0 ? ' disabled' : ''}>${ic('remove')}</button><output aria-live="polite">${STP.prog}</output><button type="button" class="nj-iconbtn" data-prog="1" aria-label="Sumar un episodio"${STP.prog >= it.aired ? ' disabled' : ''}>${ic('add')}</button><span class="nj-meta">de ${it.eps}</span></div>`;
      const head = `<div class="lab-stp-head"><img src="${esc(it.poster)}" alt="" width="48" height="72"><div><b>${esc(it.short)}</b><span class="nj-meta">${esc(it.format)} · ${it.aired} de ${it.eps} emitidos</span></div></div>`;
      if (p === 'android') return `<div class="lab-body lab-stp is-and"><div class="nj-appbar"><span class="nj-h2">Tu lista</span></div><div class="lab-pad">${head}<h4 class="lab-h4">Estado en tu lista</h4>${stTiles(STP.cur, { fk: 'stpa' })}${stepper}<button type="button" class="nj-btn is-text lab-danger-text" data-stp-remove>${ic('delete')}Quitar de la lista</button></div></div>`;
      const seg = `<div class="lab-stseg" role="radiogroup" aria-label="Estado en tu lista" data-region="status-picker">${D.statuses.map((s) => `<button type="button" role="radio" class="lab-stopt" data-status="${s.id}" data-st="${s.id}" aria-checked="${s.id === STP.cur}" tabindex="${s.id === STP.cur ? 0 : -1}" data-fk="stp-${s.id}">${ic(STI[s.id], { size: 18, fill: s.id === STP.cur })}${esc(s.name)}</button>`).join('')}</div>`;
      const old = `<div class="lab-stp-old"><h4 class="lab-h4">Hoy</h4><div class="lab-rel"><button type="button" class="nj-btn is-tonal" data-old aria-haspopup="menu" aria-expanded="${STP.open}" data-fk="old">${ic('bookmark')}En lista${ic('arrow_drop_down')}</button>${STP.open ? `<div class="nj-menu lab-oldmenu" role="menu" aria-label="Estado">${D.statuses.map((s) => `<button type="button" role="menuitemradio" class="nj-menu-item" data-oldst="${s.id}" aria-checked="${s.id === STP.cur}">${s.id === STP.cur ? ic('check', { size: 18 }) : '<span class="lab-ic-sp"></span>'}${esc(s.name)}</button>`).join('')}</div>` : ''}</div><p class="nj-meta">El estado vive en un menú detrás de «En lista»: dos clics y leer una lista.</p></div>`;
      return `<div class="lab-body lab-stp"><div class="lab-cmp">${old}<div class="lab-stp-new">${head}${seg}<div class="lab-stp-row">${stepper}<button type="button" class="nj-btn is-text lab-danger-text" data-stp-remove>${ic('delete')}Quitar de la lista</button></div></div></div></div>`;
    },
    after(app) { const m = app.querySelector('.lab-oldmenu'); if (m && !m.contains(document.activeElement)) { const f = m.querySelector('[aria-checked="true"]'); if (f) f.focus(); } },
    bind(app) {
      on(app, 'click', '[data-st]', (ev, b) => stpSet(app, b.dataset.st));
      radioKeys(app, '.lab-stopt, .lab-sttile', (b) => stpSet(app, b.dataset.st));
      on(app, 'click', '[data-old]', () => { STP.open = !STP.open; refresh('stp'); });
      on(app, 'click', '[data-oldst]', (ev, b) => { STP.open = false; stpSet(app, b.dataset.oldst); const o = app.querySelector('[data-old]'); if (o) o.focus(); });
      on(app, 'click', '[data-prog]', (ev, b) => { const it = stpItem(); STP.prog = Math.max(0, Math.min(it.aired, STP.prog + +b.dataset.prog)); if (STP.prog > 0 && STP.cur === 'planning') { STP.cur = 'watching'; live(app, 'Pasó a Viendo al sumar un episodio'); } refresh('stp'); });
      on(app, 'click', '[data-stp-remove]', () => { const prev = { cur: STP.cur, prog: STP.prog }; STP.cur = null; STP.prog = 0; pushUndo(app, 'stp', 'Goblin quitado de tu lista', () => Object.assign(STP, prev), { onChange: () => refresh('stp') }); refresh('stp'); });
      app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && STP.open) { STP.open = false; refresh('stp'); const o = app.querySelector('[data-old]'); if (o) o.focus(); } });
      app.addEventListener('pointerdown', (ev) => { if (STP.open && !ev.target.closest('.lab-oldmenu, [data-old]')) { STP.open = false; refresh('stp'); } });
    },
  });

  /* ---- 6. Facetas con chips y popover ---- */
  const GENRES = [...new Set(D.titles.flatMap((t) => t.genres || []))].sort((a, b) => a.localeCompare(b, 'es'));
  const FORMATS = [...new Set(D.titles.map((t) => t.format))];
  const FACETS = [
    { id: 'status', name: 'Estado', opts: D.statuses.map((s) => ({ id: s.id, name: s.name })) },
    { id: 'genre', name: 'Género', search: true, opts: GENRES.map((g) => ({ id: g, name: g })) },
    { id: 'format', name: 'Formato', opts: FORMATS.map((f) => ({ id: f, name: f })) },
    { id: 'score', name: 'Puntaje', single: true, opts: [{ id: '0', name: 'Cualquiera' }, { id: '6', name: '6 o más' }, { id: '7', name: '7 o más' }, { id: '8', name: '8 o más' }] },
  ];
  const FAC = { status: new Set(['watching']), genre: new Set(), format: new Set(), score: '0', q: '', open: { desktop: null, android: null }, order: ['status'] };
  const facActive = (f) => (f.single ? FAC[f.id] !== '0' : FAC[f.id].size > 0);
  function facMatch(t, f, val) {
    if (f.id === 'status') return val.has(t.status); if (f.id === 'genre') return (t.genres || []).some((g) => val.has(g));
    if (f.id === 'format') return val.has(t.format); if (f.id === 'score') return (t.score || 0) >= +val; return true;
  }
  function facResults(except) { return D.titles.filter((t) => FACETS.every((f) => f.id === except || !facActive(f) || facMatch(t, f, FAC[f.id]))); }
  function facLabel(f) {
    if (!facActive(f)) return f.name;
    if (f.single) return `${f.name}: ${f.opts.find((o) => o.id === FAC[f.id]).name}`;
    const names = [...FAC[f.id]].map((v) => (f.opts.find((o) => o.id === v) || { name: v }).name);
    return `${f.name}: ${names.slice(0, 2).join(', ')}${names.length > 2 ? ' +' + (names.length - 2) : ''}`;
  }
  function facOptCount(f, o) { const base = facResults(f.id); return f.single ? base.filter((t) => (t.score || 0) >= +o.id).length : base.filter((t) => facMatch(t, f, new Set([o.id]))).length; }
  function facPanel(f, p, n) {
    const q = norm(FAC.q);
    return `${f.search ? `<div class="nj-search lab-facq">${ic('search')}<input type="search" data-fac-q aria-label="Buscar ${f.name.toLowerCase()}" placeholder="Buscar ${f.name.toLowerCase()}" value="${esc(FAC.q)}" data-fk="facq-${p}"></div>` : ''}
      <div class="lab-facopts" role="group" aria-label="${esc(f.name)}">${f.opts.map((o) => { const c = facOptCount(f, o); const chk = f.single ? FAC[f.id] === o.id : FAC[f.id].has(o.id); return `<label class="lab-facopt"${q && !norm(o.name).includes(q) ? ' hidden' : ''}><input type="${f.single ? 'radio' : 'checkbox'}" name="fac-${f.id}-${p}" value="${esc(o.id)}" data-fac-opt="${f.id}" data-fk="fo-${p}-${f.id}-${esc(o.id)}"${chk ? ' checked' : ''}><span>${esc(o.name)}</span><span class="nj-meta">${c}</span></label>`; }).join('')}<p class="nj-meta lab-facnone" hidden>Sin coincidencias.</p></div>
      <div class="lab-pop-foot">${facActive(f) ? `<button type="button" class="nj-btn is-text" data-facet-clear="${f.id}">Limpiar</button>` : '<span></span>'}<button type="button" class="nj-btn is-filled" data-fac-done>Ver ${n} ${n === 1 ? 'título' : 'títulos'}</button></div>`;
  }
  defDemo({
    id: 'fac', title: 'Facetas con chips y popover', deskH: 520, andH: 640,
    pattern: 'Cada chip dice su valor actual, abre un popover con buscador y multiselección, y se limpia con ×. Cada opción muestra cuántos títulos quedarían, y el total cambia en vivo mientras marcas.',
    replaces: 'La hoja de filtros como una lista larga, con Año repetido en chips y en rango, sin conteo de resultados antes de aplicar.',
    compose: '<code>FilterChip</code> con <code>trailingIcon</code> para limpiar + <code>Popup</code> con <code>LazyColumn</code> de <code>Checkbox</code>; los conteos salen de un <code>derivedStateOf</code> sobre la lista filtrada; en Android, <code>ModalBottomSheet</code> con el botón «Ver N títulos».',
    deskHint: 'Abre Género y escribe «fan» · × limpia el filtro', andHint: 'Fila de chips con desplazamiento · la hoja muestra el total',
    body(p) {
      const res = facResults(null); const open = FAC.open[p]; const f = open ? FACETS.find((x) => x.id === open) : null;
      const chips = FACETS.map((x) => `<span class="lab-facet${facActive(x) ? ' is-active' : ''}"><button type="button" class="nj-chip" data-facet="${x.id}" aria-pressed="${facActive(x)}" aria-haspopup="dialog" aria-expanded="${open === x.id}" data-fk="fc-${p}-${x.id}">${esc(facLabel(x))}${ic('arrow_drop_down', { size: 18 })}</button>${facActive(x) ? `<button type="button" class="lab-facet-x" data-facet-clear="${x.id}" aria-label="Quitar filtro ${esc(x.name)}" data-fk="fx-${p}-${x.id}">${ic('close', { size: 16 })}</button>` : ''}</span>`).join('');
      const last = FAC.order.slice().reverse().map((id) => FACETS.find((x) => x.id === id)).find((x) => x && facActive(x));
      const grid = res.length ? `<div class="nj-grid lab-facgrid" data-cards-local>${res.map((t) => card(t, { size: 's', context: 'grid', show: { status: true } })).join('')}</div>` : `<div class="lab-empty" data-region="empty"><p>Ningún título cumple estos filtros.</p>${last ? `<button type="button" class="nj-btn is-tonal" data-facet-clear="${last.id}">Quitar ${esc(facLabel(last))}</button>` : ''}</div>`;
      const count = `<span class="lab-faccount" role="status">${res.length} de ${D.titles.length} títulos</span>`;
      const pop = f ? (p === 'android' ? `<div class="lab-sheetwrap"><div class="lab-scrim" data-fac-done></div><div class="nj-sheet lab-sheet lab-facsheet" role="dialog" aria-modal="true" aria-label="Filtrar por ${esc(f.name)}"><h3 class="nj-h3">${esc(f.name)}</h3>${facPanel(f, p, res.length)}</div></div>` : `<div class="lab-pop nj-menu lab-facpop" role="dialog" aria-label="Filtrar por ${esc(f.name)}">${facPanel(f, p, res.length)}</div>`) : '';
      if (p === 'android') return `<div class="lab-body lab-fac is-and"><div class="nj-appbar"><span class="nj-h2">Biblioteca</span>${count}</div><div class="lab-facbar is-scroll" role="toolbar" aria-label="Filtros" data-keep-scroll="facbar">${chips}</div><div class="lab-facscroll" data-keep-scroll="facg">${grid}</div>${pop}</div>`;
      return `<div class="lab-body lab-fac"><div class="lab-facbar" role="toolbar" aria-label="Filtros de la biblioteca">${chips}${count}</div><div class="lab-facscroll" data-keep-scroll="facg">${grid}</div>${pop}</div>`;
    },
    after(app, p) {
      const pop = app.querySelector('.lab-facpop');
      if (pop) { const chip = app.querySelector(`[data-facet="${FAC.open[p]}"]`); const r = relRect(chip, app); place(pop, app, r.x, r.y, r.w, r.h); }
      const box = pop || app.querySelector('.lab-facsheet');
      if (box && !box.contains(document.activeElement)) { const f = box.querySelector('[data-fac-q], input'); if (f) f.focus({ preventScroll: true }); }
      if (box) facFilterNone(box);
    },
    bind(app, p) {
      on(app, 'click', '[data-facet]', (ev, b) => { FAC.open[p] = FAC.open[p] === b.dataset.facet ? null : b.dataset.facet; FAC.q = ''; refresh('fac'); });
      on(app, 'click', '[data-facet-clear]', (ev, b) => { const f = FACETS.find((x) => x.id === b.dataset.facetClear); if (f.single) FAC[f.id] = '0'; else FAC[f.id] = new Set(); refresh('fac'); const c = app.querySelector(`[data-facet="${f.id}"]`); if (c && !FAC.open[p]) c.focus(); });
      on(app, 'click', '[data-fac-done]', () => { const id = FAC.open[p]; FAC.open[p] = null; refresh('fac'); const c = app.querySelector(`[data-facet="${id}"]`); if (c) c.focus(); });
      on(app, 'change', '[data-fac-opt]', (ev, i) => { const f = FACETS.find((x) => x.id === i.dataset.facOpt); if (f.single) FAC[f.id] = i.value; else { if (i.checked) FAC[f.id].add(i.value); else FAC[f.id].delete(i.value); } FAC.order = FAC.order.filter((x) => x !== f.id).concat(f.id); refresh('fac'); });
      on(app, 'input', '[data-fac-q]', (ev, i) => { FAC.q = i.value; const box = i.closest('.lab-facpop, .lab-facsheet'); const q = norm(i.value); box.querySelectorAll('.lab-facopt').forEach((l) => (l.hidden = !!q && !norm(l.textContent).includes(q))); facFilterNone(box); });
      app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && FAC.open[p]) { ev.preventDefault(); const id = FAC.open[p]; FAC.open[p] = null; refresh('fac'); const c = app.querySelector(`[data-facet="${id}"]`); if (c) c.focus(); } });
      app.addEventListener('pointerdown', (ev) => { if (p === 'desktop' && FAC.open[p] && !ev.target.closest('.lab-facpop, .lab-facet')) { FAC.open[p] = null; refresh('fac'); } });
    },
  });
  function facFilterNone(box) { const none = box.querySelector('.lab-facnone'); if (none) none.hidden = !![...box.querySelectorAll('.lab-facopt')].find((l) => !l.hidden); }

  /* ---- 7. Selector de fuente con reglas ---- */
  const SRC = { auto: true, minRes: '1080p', cache: true, subs: new Set(['Multi', 'ES']), codec: 'any', noEmbed: true, manual: null, rules: false };
  const RES = { '720p': 720, '1080p': 1080, '2160p': 2160 };
  function srcFails(s) {
    const why = [];
    if (RES[s.res] < RES[SRC.minRes]) why.push(`${s.res}, pediste ${SRC.minRes} o más`);
    if (SRC.subs.size && !SRC.subs.has(s.subs)) why.push(`subtítulos ${s.subs}`);
    if (SRC.codec !== 'any' && s.codec !== SRC.codec) why.push(`códec ${s.codec}`);
    if (SRC.noEmbed && s.embed) why.push('reproductor incrustado');
    return why;
  }
  function srcPick() { const ok = D.sources.filter((s) => !srcFails(s).length); ok.sort((a, b) => (SRC.cache ? (b.cached ? 1 : 0) - (a.cached ? 1 : 0) : 0) || b.reliability - a.reliability || RES[b.res] - RES[a.res]); return ok[0] || null; }
  const srcAttrs = (s) => [['res', s.res], ['codec', `${s.codec} ${s.bit}`], ['audio', `Audio ${s.audio}`], ['subs', `Subs ${s.subs}`], ['size', s.size], ['cache', s.cached ? `En caché · ${s.cached}` : s.kind === 'torrent' ? 'Sin caché' : 'Web'], ['seed', s.kind === 'torrent' ? `${s.seeders} semillas` : null], ['rel', `Confiable ${Math.round(s.reliability * 100)} %`]].filter((a) => a[1]);
  function srcRules(p) {
    const chip = (attr, v, l, on2) => `<button type="button" class="nj-chip" data-rule="${attr}" data-v="${esc(v)}" aria-pressed="${on2}" data-fk="rule-${p}-${attr}-${esc(v)}">${on2 ? ic('check', { size: 18 }) : ''}${esc(l)}</button>`;
    return `<div class="lab-rules" role="group" aria-label="Reglas de elección automática">
      ${h.toggle(`src-auto-${p}`, 'Elegir automáticamente con estas reglas', SRC.auto, { desc: 'Si ninguna fuente cumple, se muestra la lista' }).replace('<input', '<input data-src-auto')}
      <div class="lab-rule"><span class="lab-rule-l" id="rl-res-${p}">Calidad mínima</span><div class="nj-seg" role="radiogroup" aria-labelledby="rl-res-${p}">${Object.keys(RES).map((r) => `<button type="button" role="radio" data-rule="minRes" data-v="${r}" aria-checked="${SRC.minRes === r}">${r}</button>`).join('')}</div></div>
      <div class="lab-rule"><span class="lab-rule-l">Debrid</span><div class="nj-cluster">${chip('cache', '1', 'Preferir en caché', SRC.cache)}</div></div>
      <div class="lab-rule"><span class="lab-rule-l">Subtítulos</span><div class="nj-cluster">${['ES', 'EN', 'FR', 'Multi'].map((l) => chip('subs', l, l === 'Multi' ? 'Varios idiomas' : l, SRC.subs.has(l))).join('')}</div></div>
      <div class="lab-rule"><span class="lab-rule-l">Códec</span><div class="nj-cluster">${[['any', 'Cualquiera'], ['HEVC', 'HEVC'], ['AV1', 'AV1'], ['H.264', 'H.264']].map(([v, l]) => chip('codec', v, l, SRC.codec === v)).join('')}</div></div>
      <div class="lab-rule"><span class="lab-rule-l">Reproductor</span><div class="nj-cluster">${chip('noEmbed', '1', 'Evitar incrustados', SRC.noEmbed)}</div></div></div>`;
  }
  defDemo({
    id: 'src', title: 'Selector de fuente y reglas automáticas', tag: 'Grupos, tamaños y cifras de ejemplo', deskH: 600, andH: 660,
    pattern: 'Cada fuente muestra sus atributos como chips, y los que difieren de la elegida se resaltan para comparar de un vistazo. Las reglas de elección automática también son chips: cambiar una mueve la elección al instante y explica por qué las demás no cumplen.',
    replaces: 'Calidad mínima, preferencia de debrid e idiomas como filas sueltas en Ajustes › Reproductor, sin ver qué fuente elegirían en un episodio real.',
    compose: '<code>FlowRow</code> de <code>AssistChip</code> por fuente; reglas como <code>FilterChip</code> y <code>SingleChoiceSegmentedButtonRow</code>; la elección es una función pura <code>pick(sources, rules)</code> en el ViewModel que recalcula un <code>StateFlow</code>.',
    deskHint: 'Cambia la calidad mínima a 2160p o quita «Varios idiomas»', andHint: 'Reglas plegadas arriba, con su resumen',
    body(p) {
      const auto = srcPick(); const chosen = SRC.manual ? D.sources.find((s) => s.id === SRC.manual) : auto;
      const cattrs = chosen ? Object.fromEntries(srcAttrs(chosen)) : {};
      const summary = auto ? `Con estas reglas se reproduce <b>${esc(auto.group)}</b> · ${esc(auto.res)} ${esc(auto.codec)}${auto.cached ? ` · en caché ${esc(auto.cached)}` : ''}${SRC.auto ? ', sin preguntar.' : '. Lo eliges tú al tocar Reproducir.'}` : 'Ninguna fuente cumple todas las reglas: se te muestra la lista para elegir.';
      const rows = D.sources.slice().sort((a, b) => (a === chosen ? -1 : b === chosen ? 1 : srcFails(a).length - srcFails(b).length)).map((s) => {
        const fails = srcFails(s); const isC = s === chosen;
        return `<div class="lab-src" data-src="${s.id}"${isC ? ' data-chosen' : ''}${fails.length ? ' data-fails' : ''}>
          <div class="lab-src-head"><b>${esc(s.group)}</b><span class="nj-meta">${esc(s.ext)} · ${s.kind === 'torrent' ? 'torrent' : 'web'}</span>${isC ? `<span class="nj-badge lab-chosen">${ic(SRC.manual ? 'touch_app' : 'rule', { size: 14 })}${SRC.manual ? 'Elegida a mano' : 'Elegida por tus reglas'}</span>` : ''}</div>
          <div class="lab-src-chips">${srcAttrs(s).map(([k, v]) => `<span class="lab-attr"${!isC && chosen && cattrs[k] !== v ? ' data-diff' : ''}>${esc(v)}</span>`).join('')}</div>
          ${fails.length ? `<p class="lab-src-why">${ic('info', { size: 16 })}No cumple: ${esc(fails.join(' · '))}</p>` : ''}
          <div class="lab-src-acts">${isC ? `<button type="button" class="nj-btn is-filled" data-src-play data-fk="sp-${p}">${ic('play_arrow', { fill: true })}Reproducir</button>${SRC.manual ? '<button type="button" class="nj-btn is-text" data-src-reset>Volver a las reglas</button>' : ''}` : `<button type="button" class="nj-btn is-outline" data-src-use="${s.id}" data-fk="su-${p}-${s.id}">Usar esta</button>`}</div></div>`;
      }).join('');
      if (p === 'android') return `<div class="lab-body lab-srcs is-and"><div class="nj-appbar"><span class="nj-h2">Fuentes · Ep. 2</span></div><div class="lab-andscroll" data-keep-scroll="src"><button type="button" class="lab-disclose" data-src-rules aria-expanded="${SRC.rules}">${ic('rule')}<span><b>Reglas automáticas</b><small>${summary}</small></span>${ic(SRC.rules ? 'expand_less' : 'expand_more')}</button>${SRC.rules ? srcRules(p) : ''}<div class="lab-srclist">${rows}</div></div></div>`;
      return `<div class="lab-body lab-srcs"><aside class="lab-srcrules"><h3 class="nj-h3">Reglas</h3><p class="lab-src-sum" role="status">${summary}</p>${srcRules(p)}</aside><div class="lab-srclist" data-keep-scroll="src"><div class="nj-section-head"><h3 class="nj-h3">Laid-Off Mage · Ep. 2</h3><span class="nj-meta">${D.sources.length} fuentes</span></div>${rows}</div></div>`;
    },
    bind(app) {
      on(app, 'click', '[data-rule]', (ev, b) => {
        const k = b.dataset.rule, v = b.dataset.v;
        if (k === 'minRes') SRC.minRes = v; else if (k === 'cache') SRC.cache = !SRC.cache; else if (k === 'noEmbed') SRC.noEmbed = !SRC.noEmbed;
        else if (k === 'codec') SRC.codec = v; else if (k === 'subs') { if (SRC.subs.has(v)) SRC.subs.delete(v); else SRC.subs.add(v); }
        SRC.manual = null; refresh('src');
      });
      on(app, 'change', '[data-src-auto]', (ev, i) => { SRC.auto = i.checked; refresh('src'); });
      on(app, 'click', '[data-src-rules]', () => { SRC.rules = !SRC.rules; refresh('src'); });
      on(app, 'click', '[data-src-use]', (ev, b) => { SRC.manual = b.dataset.srcUse; refresh('src'); const f = app.querySelector('[data-src-play]'); if (f) f.focus(); });
      on(app, 'click', '[data-src-reset]', () => { SRC.manual = null; refresh('src'); });
      on(app, 'click', '[data-src-play]', () => { const s = SRC.manual ? D.sources.find((x) => x.id === SRC.manual) : srcPick(); pushUndo(app, 'src', `Reproduciendo con ${s ? s.group : 'la fuente elegida'}`, null); });
      radioKeys(app, '.lab-rule [role="radio"]', (b) => { b.click(); const f = app.querySelector(`[data-rule="minRes"][data-v="${b.dataset.v}"]`); if (f) f.focus(); });
    },
  });

  /* ---- 8. Menú contextual unificado ---- */
  const CTX = { desktop: null, android: null };
  const CTX_IDS = ['laidoff', 'tempal', 'aonohako', 'koori', 'psyren', 'chitose'];
  const CTX_ST = Object.fromEntries(CTX_IDS.map((id) => [id, D.byId[id].status]));
  function ctxItems(id) {
    const it = D.byId[id]; const ep = (it.progress || 0) + 1;
    return [['play', 'play_arrow', `Reproducir Ep. ${ep}`], ['watch', 'check_circle', `Marcar Ep. ${ep} como visto`], ['status', 'bookmark', 'Cambiar estado', true], ['list', 'playlist_add', 'Agregar a Para el finde'], ['dl', 'download', 'Descargar episodios'], ['send', 'send_to_mobile', 'Enviar al teléfono'], ['copy', 'link', 'Copiar enlace de AniList'], ['-'], ['remove', 'delete', 'Quitar de la lista', false, true]];
  }
  function ctxRun(app, p, act, id) {
    const it = D.byId[id]; const ep = (it.progress || 0) + 1; const o = { onChange: () => refresh('ctx') };
    if (act === 'play') pushUndo(app, 'ctx', `Reproduciendo ${it.short}, Ep. ${ep}`, null);
    else if (act === 'watch') pushUndo(app, 'ctx', `Ep. ${ep} de ${it.short} marcado como visto`, () => {}, o);
    else if (act === 'list') pushUndo(app, 'ctx', `${it.short} agregado a Para el finde`, () => {}, o);
    else if (act === 'dl') pushUndo(app, 'ctx', `Descargando los episodios emitidos de ${it.short}`, () => {}, o);
    else if (act === 'send') pushUndo(app, 'ctx', `${it.short} enviado a ${D.link.phone}`, null);
    else if (act === 'copy') { const ok = navigator.clipboard && navigator.clipboard.writeText ? navigator.clipboard.writeText('https://anilist.co/search/anime?search=' + encodeURIComponent(it.title)).then(() => true, () => false) : Promise.resolve(false); ok.then((y) => pushUndo(app, 'ctx', y ? 'Enlace de AniList copiado' : 'El navegador no dejó copiar el enlace', null)); }
    else if (act === 'remove') { const prev = CTX_ST[id]; CTX_ST[id] = null; pushUndo(app, 'ctx', `${it.short} quitado de tu lista`, () => (CTX_ST[id] = prev), o); }
    else if (act.startsWith('st:')) { const prev = CTX_ST[id]; CTX_ST[id] = act.slice(3); pushUndo(app, 'ctx', `${it.short} ahora está en ${stName(CTX_ST[id])}`, () => (CTX_ST[id] = prev), o); }
  }
  function ctxMenuHtml(p, st) {
    const it = D.byId[st.id];
    if (st.sub) return `<button type="button" role="menuitem" class="nj-menu-item" data-mi="back" tabindex="-1">${ic('chevron_left')}Cambiar estado</button><div role="separator" class="nj-divider"></div>${D.statuses.map((s) => `<button type="button" role="menuitemradio" class="nj-menu-item" data-mi="st:${s.id}" aria-checked="${CTX_ST[st.id] === s.id}" tabindex="-1"><span class="lab-dot" data-status="${s.id}"></span>${esc(s.name)}${CTX_ST[st.id] === s.id ? ic('check', { size: 18, cls: 'lab-mi-end' }) : ''}</button>`).join('')}`;
    return ctxItems(st.id).map(([a, i, l, sub, danger]) => (a === '-' ? '<div role="separator" class="nj-divider"></div>' : `<button type="button" role="menuitem" class="nj-menu-item${danger ? ' is-danger' : ''}" data-mi="${a}" tabindex="-1"${sub ? ' aria-haspopup="menu"' : ''}${a === 'remove' && !CTX_ST[st.id] ? ' disabled' : ''}>${ic(i)}<span>${esc(l)}</span>${sub ? ic('chevron_right', { cls: 'lab-mi-end' }) : ''}</button>`)).join('') + `<span class="lab-sr">${esc(it.short)}</span>`;
  }
  defDemo({
    id: 'ctx', title: 'Menú contextual unificado', deskH: 420, andH: 600,
    pattern: `El botón ⋯, el clic derecho, ${kbd('Shift')}+${kbd('F10')} y la pulsación larga abren el mismo menú con las mismas acciones. Se mide antes de pintarse, se coloca debajo o encima según quepa, nunca a menos de 8 px del borde, y crece desde la esquina del ancla.`,
    replaces: 'Acciones repartidas entre pulsación larga, un menú «Más» de la ficha y nada en clic derecho; en escritorio no había menú contextual.',
    compose: 'Escritorio: <code>ContextMenuArea</code> con un <code>ContextMenuRepresentation</code> propio y el mismo <code>DropdownMenu</code> para ⋯; Android: <code>Modifier.combinedClickable(onLongClick)</code> que abre una <code>ModalBottomSheet</code> con la misma lista de acciones.',
    deskHint: `Clic derecho sobre un póster, ⋯, o ${kbd('Shift')}+${kbd('F10')} con el foco en él`, andHint: 'Mantén pulsado o toca ⋮ · mismas acciones en una hoja',
    body(p) {
      const st = CTX[p];
      const cards = CTX_IDS.map((id) => { const it = Object.assign({}, D.byId[id], { status: CTX_ST[id] }); return `<div class="lab-cardwrap" data-ctx="${id}">${card(it, { size: p === 'android' ? 's' : 'm', context: 'grid', show: { status: p !== 'android' } })}<button type="button" class="nj-iconbtn lab-cardmore" data-ctxbtn="${id}" data-nolong aria-label="Más acciones para ${esc(it.short)}" aria-haspopup="menu" aria-expanded="${!!(st && st.id === id)}" data-fk="cm-${p}-${id}">${ic('more_vert')}</button></div>`; }).join('');
      let menu = '';
      if (st) {
        const it = D.byId[st.id];
        menu = p === 'android' ? `<div class="lab-sheetwrap"><div class="lab-scrim" data-ctx-close></div><div class="nj-sheet lab-sheet" role="dialog" aria-modal="true" aria-label="Acciones para ${esc(it.short)}"><div class="lab-sheet-head"><img src="${esc(it.poster)}" alt="" width="40" height="60"><div><b>${esc(it.short)}</b><div>${h.statusChip(CTX_ST[st.id])}</div></div></div><div class="lab-sheetmenu" role="menu" aria-label="Acciones para ${esc(it.short)}">${ctxMenuHtml(p, st)}</div></div></div>`
          : `<div class="nj-menu lab-menu" role="menu" aria-label="Acciones para ${esc(it.short)}">${ctxMenuHtml(p, st)}</div>`;
      }
      return `<div class="lab-body lab-ctx${p === 'android' ? ' is-and' : ''}">${p === 'android' ? '<div class="nj-appbar"><span class="nj-h2">Viendo</span></div>' : '<div class="nj-section-head lab-pad-x"><h3 class="nj-h3">Viendo</h3><span class="nj-meta">El menú no se sale aunque abras el del último póster</span></div>'}<div class="${p === 'android' ? 'lab-andgrid' : 'lab-ctxrow'}" data-cards-local data-keep-scroll="ctx">${cards}</div>${menu}</div>`;
    },
    after(app, p) {
      const st = CTX[p]; if (!st) return;
      const m = app.querySelector(p === 'android' ? '.lab-sheetmenu' : '.lab-menu');
      if (p === 'desktop') { if (st.x != null) place(m, app, st.x, st.y, 0, 0); else { const b = app.querySelector(`[data-ctxbtn="${st.id}"]`); const r = relRect(b, app); place(m, app, r.x, r.y, r.w, r.h, { alignRight: true }); } if (!st.shown) { st.shown = true; popIn(m); } }
      const f = m.querySelector('.nj-menu-item:not([disabled])'); if (f && !m.contains(document.activeElement)) f.focus({ preventScroll: true });
    },
    bind(app, p) {
      const open = (id, x, y) => { CTX[p] = { id, x, y, sub: false, back: document.activeElement }; refresh('ctx'); };
      const close = () => { const st = CTX[p]; CTX[p] = null; refresh('ctx'); const b = st && app.querySelector(`[data-ctxbtn="${st.id}"]`); if (b) b.focus(); };
      on(app, 'click', '[data-ctxbtn]', (ev, b) => { if (CTX[p] && CTX[p].id === b.dataset.ctxbtn) close(); else open(b.dataset.ctxbtn); });
      on(app, 'click', '[data-ctx]', (ev) => !ev.target.closest('[data-ctxbtn]') && live(app, p === 'android' ? 'Un toque abre la ficha. Mantén pulsado para ver las acciones.' : 'Un clic abre la ficha. Clic derecho para ver las acciones.'));
      on(app, 'click', '[data-ctx-close]', close);
      if (p === 'desktop') {
        app.addEventListener('contextmenu', (ev) => { if (ev.shiftKey) return; const w = ev.target.closest('[data-ctx]'); if (!w) return; ev.preventDefault(); const r = app.getBoundingClientRect(); open(w.dataset.ctx, ev.clientX - r.left, ev.clientY - r.top); });
        app.addEventListener('keydown', (ev) => { const w = ev.target.closest('[data-ctx]'); if (w && !ev.target.closest('.lab-menu') && ((ev.shiftKey && ev.key === 'F10') || ev.key === 'ContextMenu')) { ev.preventDefault(); const r = relRect(w, app); open(w.dataset.ctx, r.x + 12, r.y + r.h * 0.6); } });
        app.addEventListener('pointerdown', (ev) => { if (CTX[p] && !ev.target.closest('.lab-menu, [data-ctxbtn]')) { CTX[p] = null; refresh('ctx'); } });
      } else longPress(app, '[data-ctx]', (w) => open(w.dataset.ctx));
      on(app, 'click', '[data-mi]', (ev, b) => {
        const st = CTX[p]; const a = b.dataset.mi;
        if (a === 'status') { st.sub = true; refresh('ctx'); return; } if (a === 'back') { st.sub = false; refresh('ctx'); return; }
        const id = st.id; CTX[p] = null; refresh('ctx'); ctxRun(app, p, a, id); const t = app.querySelector(`[data-ctxbtn="${id}"]`); if (t) t.focus();
      });
      app.addEventListener('keydown', (ev) => {
        const m = ev.target.closest('[role="menu"]'); if (!m && !(ev.key === 'Escape' && CTX[p])) return;
        if (ev.key === 'Escape') { ev.preventDefault(); ev.stopPropagation(); close(); return; }
        if (ev.key === 'Tab') { ev.preventDefault(); close(); return; }
        const its = [...m.querySelectorAll('.nj-menu-item:not([disabled])')]; const i = its.indexOf(document.activeElement);
        let n = null; if (ev.key === 'ArrowDown') n = (i + 1) % its.length; else if (ev.key === 'ArrowUp') n = (i - 1 + its.length) % its.length; else if (ev.key === 'Home') n = 0; else if (ev.key === 'End') n = its.length - 1;
        else if (ev.key === 'ArrowRight' && document.activeElement.dataset.mi === 'status') { ev.preventDefault(); document.activeElement.click(); return; }
        else if (ev.key === 'ArrowLeft' && CTX[p] && CTX[p].sub) { ev.preventDefault(); CTX[p].sub = false; refresh('ctx'); return; }
        if (n != null) { ev.preventDefault(); its[n].focus(); }
      });
    },
  });

  /* ---- 9. Ajustes con vista previa en vivo (subtítulos) ---- */
  const SUB0 = { size: 55, outline: 3, shadow: 1, font: 'ui', color: '#ffffff', box: false, pos: 'bottom', margin: 6 };
  const SUB = Object.assign({}, SUB0);
  const SUBFONTS = [['ui', 'Roboto Flex', '"Roboto Flex", system-ui, sans-serif'], ['tight', 'Inter Tight', '"Inter Tight", system-ui, sans-serif'], ['serif', 'Serif del sistema', 'Georgia, "Noto Serif", serif'], ['mono', 'JetBrains Mono', '"JetBrains Mono", monospace']];
  const SUBCOLORS = [['#ffffff', 'Blanco'], ['#ffe14d', 'Amarillo'], ['#9fe3ff', 'Celeste']];
  function subStyle() {
    const u = 'var(--u)'; /* mpv: tamaño en píxeles a una altura de 720 */ const o = SUB.outline, s = SUB.box ? 0 : SUB.shadow; const sh = [];
    if (o > 0) [[1, 0], [-1, 0], [0, 1], [0, -1], [0.7, 0.7], [-0.7, 0.7], [0.7, -0.7], [-0.7, -0.7]].forEach(([x, y]) => sh.push(`calc(${x * o} * ${u}) calc(${y * o} * ${u}) 0 #000`));
    if (s > 0) sh.push(`calc(${s * 1.5} * ${u}) calc(${s * 1.5} * ${u}) calc(${s} * ${u}) rgb(0 0 0 / .85)`);
    const f = SUBFONTS.find((x) => x[0] === SUB.font)[2];
    return `font-size:calc(${SUB.size} * ${u});text-shadow:${sh.join(',') || 'none'};font-family:${f};color:${SUB.color};${SUB.pos === 'top' ? 'top' : 'bottom'}:${SUB.margin}%`;
  }
  function subApply() {
    document.querySelectorAll('.lab-app[data-demo="sub"]').forEach((app) => {
      const l = app.querySelector('.lab-subline'); if (l) { l.setAttribute('style', subStyle()); l.classList.toggle('is-box', SUB.box); }
      [['size', `${SUB.size} px`], ['outline', `${SUB.outline} px`], ['shadow', SUB.box ? 'sin sombra' : `${SUB.shadow} px`], ['margin', `${SUB.margin} %`]].forEach(([k, v]) => { const i = app.querySelector(`[data-sub="${k}"]`); if (i && +i.value !== SUB[k]) i.value = SUB[k]; const out = app.querySelector(`output[data-subout="${k}"]`); if (out) out.textContent = v; });
    });
  }
  function subSlider(p, k, label, min, max, step, unit) { const id = `sub-${p}-${k}`; return `<div class="nj-slider"><div class="nj-slider-head"><label for="${id}">${label}</label><output for="${id}" data-subout="${k}">${SUB[k]} ${unit}</output></div><input type="range" id="${id}" data-sub="${k}" min="${min}" max="${max}" step="${step}" value="${SUB[k]}"${k === 'shadow' && SUB.box ? ' disabled aria-describedby="' + id + '-n"' : ''}>${k === 'shadow' && SUB.box ? `<p class="nj-meta" id="${id}-n">Con la caja de fondo no hay sombra: libmpv pinta la caja con el color de la sombra.</p>` : ''}</div>`; }
  defDemo({
    id: 'sub', title: 'Ajustes con vista previa en vivo', deskH: 520, andH: 680,
    pattern: 'El control y su efecto están juntos: cada ajuste del estilo de subtítulos mueve el cuadro de video al instante, con el mismo cálculo que usará el reproductor. Restablecer deja un Deshacer.',
    replaces: 'Unos 25 controles de estilo dentro de la hoja de Subtítulos sin una vista previa fiel, y un Restablecer que no pedía confirmación ni se podía deshacer.',
    compose: 'El estado del estilo vive en un <code>MutableStateFlow&lt;SubStyle&gt;</code>; la vista previa es un <code>Box</code> con la imagen y un <code>Text</code> que aplica <code>Shadow</code> y <code>drawBehind</code> para el contorno; los cambios se escriben a mpv con <code>sub-font-size</code>, <code>sub-border-size</code> y <code>sub-shadow-offset</code> al soltar.',
    deskHint: 'Mueve Tamaño o Contorno y mira el cuadro · el teléfono va sincronizado', andHint: 'Vista previa arriba, controles abajo',
    body(p) {
      const preview = `<div class="lab-video"><img src="img/frame-laidoff.webp" alt="Cuadro de Laid-Off Mage para previsualizar los subtítulos" width="1280" height="720" loading="lazy"><p class="lab-subline${SUB.box ? ' is-box' : ''}" style='${subStyle()}'>No corro porque quiera.<br>Corro porque me persiguen.</p></div>`;
      const controls = `<div class="lab-subctl">
        ${subSlider(p, 'size', 'Tamaño', 30, 90, 1, 'px')}${subSlider(p, 'outline', 'Contorno', 0, 8, 0.5, 'px')}${subSlider(p, 'shadow', 'Sombra', 0, 6, 0.25, 'px')}
        <div class="lab-ctlrow"><span class="lab-rule-l">Letra</span><div class="nj-cluster" role="radiogroup" aria-label="Letra">${SUBFONTS.map(([v, l, f]) => `<button type="button" class="nj-chip" role="radio" data-subfont="${v}" aria-checked="${SUB.font === v}" aria-pressed="${SUB.font === v}" style='font-family:${f}' data-fk="sf-${p}-${v}">${esc(l)}</button>`).join('')}</div></div>
        <div class="lab-ctlrow"><span class="lab-rule-l">Color</span><div class="nj-cluster" role="radiogroup" aria-label="Color del texto">${SUBCOLORS.map(([v, l]) => `<button type="button" class="nj-chip lab-swatch" role="radio" data-subcolor="${v}" aria-checked="${SUB.color === v}" aria-pressed="${SUB.color === v}" data-fk="sc-${p}-${v}"><span class="lab-sw" style="background:${v}"></span>${l}</button>`).join('')}</div></div>
        ${h.toggle(`sub-${p}-box`, 'Caja de fondo detrás del texto', SUB.box).replace('<input', '<input data-subbox')}
        <div class="lab-ctlrow"><span class="lab-rule-l" id="sub-${p}-pos">Posición</span><div class="nj-seg" role="radiogroup" aria-labelledby="sub-${p}-pos">${[['bottom', 'Abajo'], ['top', 'Arriba']].map(([v, l]) => `<button type="button" role="radio" data-subpos="${v}" aria-checked="${SUB.pos === v}" data-fk="sp-${p}-${v}">${l}</button>`).join('')}</div></div>
        ${subSlider(p, 'margin', 'Distancia al borde', 0, 20, 1, '%')}
        <button type="button" class="nj-btn is-text" data-subreset${JSON.stringify(SUB) === JSON.stringify(SUB0) ? ' disabled' : ''}>${ic('restart_alt')}Restablecer estilo</button></div>`;
      if (p === 'android') return `<div class="lab-body lab-sub is-and"><div class="nj-appbar"><span class="nj-h2">Estilo de subtítulos</span></div>${preview}<div class="lab-andscroll" data-keep-scroll="sub">${controls}</div></div>`;
      return `<div class="lab-body lab-sub"><div class="lab-andscroll" data-keep-scroll="sub">${controls}</div><div class="lab-sub-prev">${preview}<p class="nj-meta">Mismas unidades que mpv: píxeles a una altura de 720, escalados al tamaño del cuadro.</p></div></div>`;
    },
    bind(app) {
      on(app, 'input', '[data-sub]', (ev, i) => { SUB[i.dataset.sub] = +i.value; subApply(); const r = app.closest('.x-page'); if (r) document.querySelectorAll('.lab-app[data-demo="sub"] [data-subreset]').forEach((b) => (b.disabled = false)); });
      on(app, 'click', '[data-subfont]', (ev, b) => { SUB.font = b.dataset.subfont; refresh('sub'); });
      on(app, 'click', '[data-subcolor]', (ev, b) => { SUB.color = b.dataset.subcolor; refresh('sub'); });
      on(app, 'click', '[data-subpos]', (ev, b) => { SUB.pos = b.dataset.subpos; refresh('sub'); });
      on(app, 'change', '[data-subbox]', (ev, i) => { SUB.box = i.checked; refresh('sub'); const f = app.querySelector('[data-subbox]'); if (f) f.focus(); });
      on(app, 'click', '[data-subreset]', () => { const prev = Object.assign({}, SUB); Object.assign(SUB, SUB0); pushUndo(app, 'sub', 'Estilo de subtítulos restablecido', () => Object.assign(SUB, prev), { onChange: () => refresh('sub') }); refresh('sub'); });
      radioKeys(app, '[data-subfont], [data-subcolor], [data-subpos]', (b) => { b.click(); const k = b.getAttribute('data-fk'); const f = k && app.querySelector(`[data-fk="${k}"]`); if (f) f.focus(); });
    },
  });

  /* ---- 10. Pickers en lugar de campos crudos ---- */
  const DAYS = [['L', 'Lunes'], ['M', 'Martes'], ['X', 'Miércoles'], ['J', 'Jueves'], ['V', 'Viernes'], ['S', 'Sábado'], ['D', 'Domingo']];
  const FOLDERS = { home: { usuario: { Anime: { Temporada: {}, Películas: {} }, Descargas: {}, Videos: { Series: {} } } }, mnt: { Discos: { Anime: {}, Respaldo: {} } } };
  const PK = { port: 47321, test: null, folder: ['home', 'usuario', 'Anime'], browse: { desktop: null, android: null }, cells: new Set(), limit: '5', sleep: 'off', custom: 20, focus: '0-1', days: new Set(['0', '1', '2', '3', '4']), from: 1, to: 8 };
  for (let d = 0; d < 5; d++) for (let hh = 1; hh < 8; hh++) PK.cells.add(`${d}-${hh}`);
  const folderAt = (path) => path.reduce((o, k) => (o ? o[k] : null), FOLDERS) || {};
  const two = (n) => String(n).padStart(2, '0');
  function sleepText() {
    const base = 21 * 60 + 4; const mins = { off: 0, 15: 15, 30: 30, 45: 45, 60: 60, other: PK.custom }[PK.sleep];
    if (PK.sleep === 'off') return 'Sin temporizador.'; if (PK.sleep === 'ep') return 'Se detiene al terminar el Ep. 4 de Tempal (quedan 18 min).';
    const t = base + mins; return `Se detiene a las ${two(Math.floor(t / 60) % 24)}:${two(t % 60)} (en ${mins} min).`;
  }
  function schedText() { const n = PK.cells.size; if (!n) return 'Sin franjas: las descargas usan toda la velocidad.'; return PK.limit === 'pause' ? `Las descargas se pausan ${n} h por semana.` : `Se limita a ${PK.limit} MB/s durante ${n} h por semana.`; }
  function portTest(app) {
    PK.test = { state: 'busy' }; refresh('pk');
    setTimeout(() => { const v = PK.port; PK.test = v === 8099 ? { state: 'err', msg: 'En uso: el servidor de extensiones ya escucha en 8099.', fix: 47321 } : v < 1024 ? { state: 'err', msg: 'Los puertos menores a 1024 piden permisos de administrador.', fix: 47321 } : { state: 'ok', msg: `El puerto ${v} está libre en esta PC.` }; refresh('pk'); live(app, PK.test.msg); }, 600);
  }
  function weekGrid(p) {
    if (p === 'android') return `<div class="lab-heat" aria-hidden="true">${DAYS.map(([s], d) => `<div class="lab-heat-row"><span>${s}</span>${Array.from({ length: 24 }, (_, hh) => `<i${PK.cells.has(`${d}-${hh}`) ? ' class="on"' : ''}></i>`).join('')}</div>`).join('')}<div class="lab-heat-row is-hours"><span></span><b>00</b><b>06</b><b>12</b><b>18</b><b>24</b></div></div>`;
    return `<div class="lab-week" role="grid" aria-label="Franjas con límite de velocidad, por día y hora" aria-rowcount="7" aria-colcount="24">
      <div class="lab-week-hours" aria-hidden="true"><span></span>${Array.from({ length: 24 }, (_, hh) => `<span>${hh % 3 ? '' : two(hh)}</span>`).join('')}</div>
      ${DAYS.map(([s, l], d) => `<div class="lab-week-row" role="row"><span role="rowheader" aria-label="${l}">${s}</span>${Array.from({ length: 24 }, (_, hh) => { const k = `${d}-${hh}`; return `<span role="gridcell" class="lab-cell" data-cell="${k}" aria-selected="${PK.cells.has(k)}" aria-label="${l} ${two(hh)}:00" tabindex="${PK.focus === k ? 0 : -1}" data-fk="cell-${k}"></span>`; }).join('')}</div>`).join('')}</div>`;
  }
  defDemo({
    id: 'pk', title: 'Pickers en lugar de campos crudos', deskH: 640, andH: 720,
    pattern: 'Cada dato tiene un selector a su medida: el puerto con paso a paso y «Probar», la carpeta con un explorador, la franja de ancho de banda pintando bloques sobre la semana y el temporizador de apagado con chips que dicen la hora exacta.',
    replaces: 'Campos de texto para puertos (Link 47321, servidor de extensiones 8099, proxy), rutas, horas HH:MM y KiB/s, con Guardar explícito en algunos y guardado automático en otros.',
    compose: 'Puerto: <code>OutlinedTextField</code> numérico con <code>IconButton</code> de paso y una prueba en <code>LaunchedEffect</code>; carpeta: <code>FileKit</code> o <code>JFileChooser</code> en escritorio y <code>ActivityResultContracts.OpenDocumentTree</code> en Android; semana: <code>Canvas</code> con <code>pointerInput { detectDragGestures }</code>; temporizador: <code>FilterChip</code> en <code>FlowRow</code>.',
    deskHint: 'Arrastra sobre la semana para pintar o borrar · flechas y Espacio con el teclado', andHint: 'La semana se edita por franjas, con objetivos de 48 dp',
    body(p) {
      const t = PK.test;
      const port = `<div class="lab-pk"><h3 class="nj-h3"><label for="pk-port-${p}">Puerto de Nijiyome Link</label></h3><div class="lab-pk-row"><div class="lab-stepper is-field"><button type="button" class="nj-iconbtn" data-port="-1" aria-label="Bajar el puerto">${ic('remove')}</button><input type="number" id="pk-port-${p}" data-portin inputmode="numeric" min="1" max="65535" value="${PK.port}" data-fk="port-${p}"><button type="button" class="nj-iconbtn" data-port="1" aria-label="Subir el puerto">${ic('add')}</button></div><button type="button" class="nj-btn is-tonal" data-porttest${t && t.state === 'busy' ? ' disabled' : ''} data-fk="pt-${p}">${ic('network_check')}${t && t.state === 'busy' ? 'Probando…' : 'Probar'}</button></div>
        <div class="nj-cluster">${PK.port !== 47321 ? '<button type="button" class="nj-chip" data-portset="47321">Usar el predeterminado 47321</button>' : '<span class="nj-meta">Predeterminado</span>'}<button type="button" class="nj-chip" data-portset="8099">Probar con 8099</button></div>
        <p class="lab-pk-res${t && t.state === 'err' ? ' is-err' : t && t.state === 'ok' ? ' is-ok' : ''}" role="status">${t && t.msg ? `${ic(t.state === 'ok' ? 'check_circle' : 'error', { size: 18 })}<span>${esc(t.msg)}</span>${t.fix ? ` <button type="button" class="nj-btn is-text" data-portset="${t.fix}">Usar ${t.fix}</button>` : ''}` : ''}</p></div>`;
      const br = PK.browse[p];
      const browser = br ? `<div class="${p === 'android' ? 'lab-sheetwrap' : 'lab-dlgwrap'}"><div class="lab-scrim" data-fcancel></div><div class="${p === 'android' ? 'nj-sheet lab-sheet' : 'nj-dialog lab-fdlg'}" role="dialog" aria-modal="true" aria-label="Elegir carpeta de descargas"><h3 class="nj-h3">${p === 'android' ? 'Selector del sistema (simplificado)' : 'Elegir carpeta'}</h3><p class="lab-mono">/${br.map(esc).join('/')}</p><div class="lab-flist">${br.length ? `<button type="button" class="lab-frow" data-fup data-fk="fup-${p}">${ic('arrow_upward')}Subir un nivel</button>` : ''}${Object.keys(folderAt(br)).map((k) => `<button type="button" class="lab-frow" data-fopen="${esc(k)}" data-fk="fo-${p}-${esc(k)}">${ic('folder')}${esc(k)}${ic('chevron_right', { cls: 'lab-mi-end' })}</button>`).join('') || '<p class="nj-meta">Sin subcarpetas.</p>'}</div><div class="lab-pop-foot"><button type="button" class="nj-btn is-text" data-fcancel>Cancelar</button><button type="button" class="nj-btn is-filled" data-fuse${br.length < 2 ? ' disabled' : ''}>Usar esta carpeta</button></div></div></div>` : '';
      const folder = `<div class="lab-pk"><h3 class="nj-h3">Carpeta de descargas</h3><nav class="lab-crumbs" aria-label="Ruta actual">${PK.folder.map((seg, i) => `<button type="button" class="nj-chip" data-crumb="${i}">${i === 0 ? '/' : ''}${esc(seg)}</button>`).join(ic('chevron_right', { size: 16 }))}</nav><div><button type="button" class="nj-btn is-outline" data-fbrowse data-fk="fb-${p}">${ic('folder_open')}Cambiar carpeta…</button></div><p class="nj-meta">${p === 'android' ? 'En Android abre el selector del sistema y la app guarda el permiso de acceso.' : 'Tocar una parte de la ruta abre el explorador ahí.'}</p></div>`;
      const limits = `<div class="nj-cluster" role="radiogroup" aria-label="Límite durante las franjas">${[['1', '1 MB/s'], ['5', '5 MB/s'], ['10', '10 MB/s'], ['pause', 'Pausar']].map(([v, l]) => `<button type="button" class="nj-chip" role="radio" data-limit="${v}" aria-checked="${PK.limit === v}" aria-pressed="${PK.limit === v}">${l}</button>`).join('')}</div>`;
      const presets = `<div class="nj-cluster" role="group" aria-label="Franjas rápidas"><button type="button" class="nj-chip" data-preset="nights">Noches entre semana (01 a 08)</button><button type="button" class="nj-chip" data-preset="work">Horario laboral (09 a 18)</button><button type="button" class="nj-chip" data-preset="weekend">Fin de semana completo</button><button type="button" class="nj-chip" data-preset="clear">${ic('backspace', { size: 18 })}Borrar todo</button></div>`;
      const andBand = p === 'android' ? `<div class="lab-band"><span class="lab-rule-l">Franja propia</span><div class="nj-cluster" role="group" aria-label="Días">${DAYS.map(([s, l], d) => `<button type="button" class="nj-chip lab-day" data-day="${d}" aria-pressed="${PK.days.has(String(d))}" aria-label="${l}">${s}</button>`).join('')}</div><div class="lab-pk-row">${['from', 'to'].map((k) => `<div class="lab-stepper" role="group" aria-label="${k === 'from' ? 'Desde' : 'Hasta'}"><span class="nj-meta">${k === 'from' ? 'Desde' : 'Hasta'}</span><button type="button" class="nj-iconbtn" data-hour="${k}" data-d="-1" aria-label="${k === 'from' ? 'Desde' : 'Hasta'} una hora antes">${ic('remove')}</button><output>${two(PK[k])}:00</output><button type="button" class="nj-iconbtn" data-hour="${k}" data-d="1" aria-label="${k === 'from' ? 'Desde' : 'Hasta'} una hora después">${ic('add')}</button></div>`).join('')}</div><button type="button" class="nj-btn is-tonal" data-addband${PK.days.size && PK.to > PK.from ? '' : ' disabled'}>${ic('add')}Agregar franja</button></div>` : '';
      const sched = `<div class="lab-pk is-wide"><h3 class="nj-h3">Programación de ancho de banda</h3><div class="lab-ctlrow"><span class="lab-rule-l">Durante las franjas</span>${limits}</div>${presets}${weekGrid(p)}${andBand}<p class="lab-pk-res" role="status">${esc(schedText())}</p></div>`;
      const sleep = `<div class="lab-pk"><h3 class="nj-h3">Temporizador de apagado</h3><div class="nj-cluster" role="radiogroup" aria-label="Temporizador">${[['off', 'Apagado'], ['15', '15 min'], ['30', '30 min'], ['45', '45 min'], ['60', '1 h'], ['ep', 'Al terminar el episodio'], ['other', 'Otro']].map(([v, l]) => `<button type="button" class="nj-chip" role="radio" data-sleep="${v}" aria-checked="${PK.sleep === v}" aria-pressed="${PK.sleep === v}" data-fk="sl-${p}-${v}">${l}</button>`).join('')}</div>${PK.sleep === 'other' ? `<div class="lab-stepper" role="group" aria-label="Minutos"><button type="button" class="nj-iconbtn" data-sleepmin="-5" aria-label="Cinco minutos menos">${ic('remove')}</button><output>${PK.custom} min</output><button type="button" class="nj-iconbtn" data-sleepmin="5" aria-label="Cinco minutos más">${ic('add')}</button></div>` : ''}<p class="lab-pk-res" role="status">${esc(sleepText())}</p></div>`;
      if (p === 'android') return `<div class="lab-body lab-pks is-and"><div class="nj-appbar"><span class="nj-h2">Ajustes</span></div><div class="lab-andscroll" data-keep-scroll="pk">${port}${folder}${sleep}${sched}</div>${browser}</div>`;
      return `<div class="lab-body lab-pks"><div class="lab-andscroll lab-pkgrid" data-keep-scroll="pk">${port}${folder}${sleep}${sched}</div>${browser}</div>`;
    },
    after(app, p) { const br = app.querySelector('.lab-flist'); if (br && !br.closest('[role="dialog"]').contains(document.activeElement)) { const f = br.querySelector('button'); if (f) f.focus({ preventScroll: true }); } },
    bind(app, p) {
      on(app, 'click', '[data-port]', (ev, b) => { PK.port = Math.max(1, Math.min(65535, PK.port + +b.dataset.port)); PK.test = null; refresh('pk'); });
      on(app, 'change', '[data-portin]', (ev, i) => { const v = parseInt(i.value, 10); PK.port = isNaN(v) ? 47321 : Math.max(1, Math.min(65535, v)); PK.test = null; refresh('pk'); });
      on(app, 'keydown', '[data-portin]', (ev, i) => { if (ev.key === 'PageUp' || ev.key === 'PageDown') { ev.preventDefault(); PK.port = Math.max(1, Math.min(65535, PK.port + (ev.key === 'PageUp' ? 10 : -10))); PK.test = null; refresh('pk'); } else if (ev.key === 'Enter') { ev.preventDefault(); i.dispatchEvent(new Event('change', { bubbles: true })); portTest(app); } });
      on(app, 'click', '[data-portset]', (ev, b) => { PK.port = +b.dataset.portset; PK.test = null; refresh('pk'); const f = app.querySelector('[data-portin]'); if (f) f.focus(); });
      on(app, 'click', '[data-porttest]', () => portTest(app));
      on(app, 'click', '[data-fbrowse]', () => { PK.browse[p] = PK.folder.slice(); refresh('pk'); });
      on(app, 'click', '[data-crumb]', (ev, b) => { PK.browse[p] = PK.folder.slice(0, +b.dataset.crumb + 1); refresh('pk'); });
      on(app, 'click', '[data-fopen]', (ev, b) => { PK.browse[p].push(b.dataset.fopen); refresh('pk'); });
      on(app, 'click', '[data-fup]', () => { PK.browse[p].pop(); refresh('pk'); });
      on(app, 'click', '[data-fcancel]', () => { PK.browse[p] = null; refresh('pk'); const f = app.querySelector('[data-fbrowse]'); if (f) f.focus(); });
      on(app, 'click', '[data-fuse]', () => { const prev = PK.folder; PK.folder = PK.browse[p]; PK.browse[p] = null; pushUndo(app, 'pk', `Descargas en /${PK.folder.join('/')}`, () => (PK.folder = prev), { onChange: () => refresh('pk') }); refresh('pk'); const f = app.querySelector('[data-fbrowse]'); if (f) f.focus(); });
      app.addEventListener('keydown', (ev) => { if (ev.key === 'Escape' && PK.browse[p]) { ev.preventDefault(); PK.browse[p] = null; refresh('pk'); } });
      on(app, 'click', '[data-limit]', (ev, b) => { PK.limit = b.dataset.limit; refresh('pk'); });
      on(app, 'click', '[data-sleep]', (ev, b) => { PK.sleep = b.dataset.sleep; refresh('pk'); });
      on(app, 'click', '[data-sleepmin]', (ev, b) => { PK.custom = Math.max(5, Math.min(180, PK.custom + +b.dataset.sleepmin)); refresh('pk'); });
      radioKeys(app, '[data-sleep], [data-limit]', (b) => { b.click(); const k = b.getAttribute('data-fk'); const f = k ? app.querySelector(`[data-fk="${k}"]`) : app.querySelector(`[data-limit="${b.dataset.limit}"]`); if (f) f.focus(); });
      const snapUndo = (msg, prev) => pushUndo(app, 'pk', msg, () => (PK.cells = prev), { onChange: () => refresh('pk') });
      on(app, 'click', '[data-preset]', (ev, b) => {
        const prev = new Set(PK.cells); const k = b.dataset.preset;
        if (k === 'clear') PK.cells.clear();
        else for (let d = 0; d < 7; d++) for (let hh = 0; hh < 24; hh++) { if ((k === 'nights' && d < 5 && hh >= 1 && hh < 8) || (k === 'work' && d < 5 && hh >= 9 && hh < 18) || (k === 'weekend' && d >= 5)) PK.cells.add(`${d}-${hh}`); }
        snapUndo(k === 'clear' ? 'Franjas borradas' : 'Franja agregada', prev); refresh('pk');
      });
      on(app, 'click', '[data-day]', (ev, b) => { const d = b.dataset.day; if (PK.days.has(d)) PK.days.delete(d); else PK.days.add(d); refresh('pk'); });
      on(app, 'click', '[data-hour]', (ev, b) => { const k = b.dataset.hour; PK[k] = Math.max(0, Math.min(24, PK[k] + +b.dataset.d)); refresh('pk'); });
      on(app, 'click', '[data-addband]', () => { const prev = new Set(PK.cells); PK.days.forEach((d) => { for (let hh = PK.from; hh < PK.to; hh++) PK.cells.add(`${d}-${hh}`); }); snapUndo(`Franja de ${two(PK.from)}:00 a ${two(PK.to)}:00 agregada`, prev); refresh('pk'); });
      if (p !== 'desktop') return;
      let paint = null;
      const setCell = (el, v) => { const k = el.dataset.cell; if (v) PK.cells.add(k); else PK.cells.delete(k); el.setAttribute('aria-selected', String(v)); };
      const sync = () => { const r = app.querySelector('.lab-pk.is-wide .lab-pk-res'); if (r) r.textContent = schedText(); };
      app.addEventListener('pointerdown', (ev) => { const c = ev.target.closest('.lab-cell'); if (!c || ev.button > 0) return; ev.preventDefault(); paint = { v: !PK.cells.has(c.dataset.cell), prev: new Set(PK.cells) }; setCell(c, paint.v); try { c.closest('.lab-week').setPointerCapture(ev.pointerId); } catch (e) { /* sin captura */ } sync(); });
      app.addEventListener('pointermove', (ev) => { if (!paint) return; const el = document.elementFromPoint(ev.clientX, ev.clientY); const c = el && el.closest ? el.closest('.lab-cell') : null; if (c && app.contains(c)) { setCell(c, paint.v); sync(); } });
      const stop = () => { if (!paint) return; const pp = paint; paint = null; snapUndo(pp.v ? 'Horas agregadas a la franja' : 'Horas quitadas de la franja', pp.prev); refresh('pk'); };
      app.addEventListener('pointerup', stop); app.addEventListener('pointercancel', stop);
      app.addEventListener('keydown', (ev) => {
        const c = ev.target.closest('.lab-cell'); if (!c) return; let [d, hh] = c.dataset.cell.split('-').map(Number);
        const mv = { ArrowRight: [0, 1], ArrowLeft: [0, -1], ArrowDown: [1, 0], ArrowUp: [-1, 0] }[ev.key];
        if (mv) { ev.preventDefault(); d = Math.max(0, Math.min(6, d + mv[0])); hh = Math.max(0, Math.min(23, hh + mv[1])); }
        else if (ev.key === 'Home') { ev.preventDefault(); hh = 0; } else if (ev.key === 'End') { ev.preventDefault(); hh = 23; }
        else if (ev.key === ' ' || ev.key === 'Enter') { ev.preventDefault(); const prev = new Set(PK.cells); setCell(c, !PK.cells.has(c.dataset.cell)); snapUndo('Franja actualizada', prev); sync(); return; }
        else return;
        PK.focus = `${d}-${hh}`; app.querySelectorAll('.lab-cell[tabindex="0"]').forEach((x) => x.setAttribute('tabindex', '-1')); const n = app.querySelector(`[data-cell="${PK.focus}"]`); n.setAttribute('tabindex', '0'); n.focus();
      });
    },
  });

  /* ---- 11. Paleta de comandos (demo) ---- */
  defDemo({
    id: 'pal', title: 'Paleta de comandos', deskH: 460, andH: 620, palHost: true,
    pattern: `Una sola búsqueda para ir a una pantalla, abrir un título, ejecutar un comando o saltar a la opción exacta de Ajustes. Resultados agrupados, flechas y ${kbd('Enter')}, ${kbd('Esc')} cierra. Prefijos para acotar: ${kbd('/')} pantallas, ${kbd('@')} títulos, ${kbd('>')} comandos, ${kbd('#')} ajustes; en táctil, los mismos filtros son chips.`,
    replaces: 'Una paleta solo de escritorio, escrita a mano en español, cuyos ajustes abrían la sección y no la opción; en Android no había paleta.',
    compose: '<code>Dialog</code> con <code>TextField</code> y <code>LazyColumn</code> agrupada (<code>stickyHeader</code>); <code>onPreviewKeyEvent</code> de la ventana para <code>Ctrl+K</code>; los ajustes se indexan desde el mismo catálogo localizado que Ajustes; en Android, <code>SearchBar</code> expandida a pantalla completa.',
    deskHint: `Toca la barra o pulsa ${kbd('Ctrl')}+${kbd('K')} con el foco aquí · prueba «>tema» o «#sub»`, andHint: 'Pantalla de búsqueda completa con filtros como chips',
    body(p) {
      if (p === 'android') return `<div class="lab-body lab-palhost is-and"><div class="nj-appbar"><span class="nj-h2">Inicio</span>${h.iconBtn('search', 'Buscar o ejecutar un comando', { attrs: { 'data-palopen': '' } })}</div><button type="button" class="nj-search lab-palbar" data-palopen>${ic('search')}<span>Buscar títulos, pantallas o ajustes</span></button><div class="lab-pad"><div class="nj-row" data-cards-local>${['tempal', 'laidoff', 'aonohako'].map((id) => card(D.byId[id], { size: 's', context: 'row', show: { progress: true } })).join('')}</div></div></div>`;
      return `<div class="lab-body lab-palhost"><div class="nj-topbar"><span class="nj-brand"><span>Nijiyome</span></span><button type="button" class="nj-cmd" data-palopen>${ic('search')}<span>Buscar o ejecutar un comando</span><span class="nj-kbd">Ctrl K</span></button></div><div class="lab-pad"><div class="nj-section-head"><h3 class="nj-h3">Continuar viendo</h3></div><div class="nj-row" data-cards-local>${['tempal', 'laidoff', 'aonohako', 'koori', 'psyren'].map((id) => card(D.byId[id], { size: 's', context: 'row', show: { progress: true } })).join('')}</div></div></div>`;
    },
    bind(app, p) { on(app, 'click', '[data-palopen]', () => NJ.openPalette(app, { platform: p })); on(app, 'click', '.nj-card', () => live(app, 'Abre la paleta para probar la búsqueda.')); },
  });

  /* ---------------- página: Patrones ---------------- */
  function mountDemos(el) {
    el.querySelectorAll('.lab-app[data-demo]').forEach((app) => { const d = DEMO[app.dataset.demo]; paintApp(app); try { d.bind(app, app.dataset.platform); } catch (e) { console.error(e); } });
    decorate(el);
  }
  NJ.registerLab({
    id: 'patrones', name: 'Patrones', order: 1,
    render(el) {
      el.innerHTML = `<header class="lab-pagehead"><h1>Patrones de interacción</h1><p class="x-lede">Once patrones que reemplazan menús escondidos y campos de texto por manipulación directa. Todos funcionan: cada uno tiene su versión de escritorio, con ratón y teclado, y la de Android, táctil. Siguen la dirección, el tema y la tarjeta elegidos arriba.</p></header>
        <nav class="lab-index" aria-label="Patrones en esta página">${DEMOS.map((d) => `<button type="button" class="x-btn" data-jump="lab-${d.id}">${esc(d.title.replace(/ \(.+\)$/, ''))}</button>`).join('')}</nav>
        ${DEMOS.map(section).join('')}`;
      el.querySelectorAll('[data-jump]').forEach((b) => b.addEventListener('click', () => { const t = document.getElementById(b.dataset.jump); if (t) { t.scrollIntoView({ behavior: RM.matches ? 'auto' : 'smooth', block: 'start' }); t.setAttribute('tabindex', '-1'); t.focus({ preventScroll: true }); } }));
      el.querySelectorAll('[data-sim]').forEach((b) => b.addEventListener('click', () => dropSim(b.dataset.sim, b.dataset.name)));
      mountDemos(el);
    },
  });

  /* ---------------- Laboratorio de movimiento ---------------- */
  const MV = {}; /* modo local por momento */
  const modeOfStage = (st) => { const m = st.dataset.motion || 'full'; return RM.matches && m === 'full' ? 'calm' : m; };
  const alive = (st, tok) => st.isConnected && st._tok === tok;
  function stopAll(st) { st.getAnimations ? st.getAnimations({ subtree: true }).forEach((a) => a.cancel()) : null; st._tok = (st._tok || 0) + 1; return st._tok; }
  function springRun(st, tok, o, onX) {
    /* Resorte de Compose: masa 1, rigidez k, amortiguación c = 2·ζ·√k. target(t) puede moverse. */
    return new Promise((res) => {
      let x = o.from || 0, v = o.v0 || 0, last = performance.now(); const t0 = last; const c = 2 * o.ratio * Math.sqrt(o.stiff);
      const f = (now) => {
        if (!alive(st, tok)) return res();
        const dt = Math.min(0.05, (now - last) / 1000); last = now; const t = (now - t0) / 1000; const tg = o.target ? o.target(t) : o.to || 0;
        const n = Math.max(1, Math.ceil(dt / 0.004)); const hh = dt / n;
        for (let i = 0; i < n; i++) { const a = -o.stiff * (x - tg) - c * v; v += a * hh; x += v * hh; }
        onX(x);
        if ((!o.until || t > o.until) && Math.abs(x - tg) < 0.02 && Math.abs(v) < 0.05) { onX(tg); return res(); }
        if (t > 12) return res();
        requestAnimationFrame(f);
      };
      requestAnimationFrame(f);
    });
  }
  function curveSvg(c) {
    let d;
    if (c.spring) {
      const k = c.spring[1], z = c.spring[0], cc = 2 * z * Math.sqrt(k); let x = 0, v = 0; const pts = []; const T = Math.min(2.4, 8 / (z * Math.sqrt(k)));
      for (let i = 0; i <= 60; i++) { pts.push([i / 60 * 64, 40 - x * 28 - 6]); for (let s = 0; s < 10; s++) { const hh = T / 600; const a = -k * (x - 1) - cc * v; v += a * hh; x += v * hh; } }
      d = 'M' + pts.map((p) => p.map((n) => n.toFixed(1)).join(',')).join(' L');
    } else if (c.bezier) { const [a, b, e, f] = c.bezier; d = `M0,34 C${a * 64},${34 - b * 28} ${e * 64},${34 - f * 28} 64,6`; }
    else d = 'M0,34 L64,6';
    return `<svg class="lab-curve" viewBox="0 0 64 40" width="64" height="40" aria-hidden="true"><path d="M0,34 H64 M0,6 H64" class="lab-curve-grid"/><path d="${d}" class="lab-curve-line"/></svg>`;
  }
  const P = (id) => D.byId[id].poster;
  const img = (id, cls, w = 60, hh = 90) => `<img src="${esc(P(id))}" alt="" width="${w}" height="${hh}"${cls ? ` class="${cls}"` : ''}>`;

  const MOMENTS = [
    {
      id: 'ficha', name: 'De la tarjeta a la ficha', why: 'El único momento de autor de la navegación: explica que la ficha es esa tarjeta abierta, sin hacer esperar. Sale más rápido de lo que entra.',
      stage: () => `<div class="mv-list"><span class="mv-cap">Viendo</span><div class="mv-posters">${img('tempal')}${img('laidoff', 'mv-hero')}${img('aonohako')}${img('koori')}</div></div>
        <div class="mv-ficha" hidden><div class="mv-ficha-in"><div class="mv-ficha-head">${img('laidoff', '', 54, 81)}<div><b>Laid-Off Mage</b><span>TV · 2026 · 12 episodios</span><span class="mv-pill">${ic('play_arrow', { size: 16 })}Ver Ep. 2</span></div></div><ol class="mv-eps">${D.laidoffEpisodes.slice(0, 3).map((e) => `<li>Ep. ${e.n} · ${esc(e.title)}</li>`).join('')}</ol></div></div>`,
      alts: [
        { id: 'ct', name: 'Transformación de contenedor', desc: 'El póster crece hasta ser la ficha; el contenido aparece cuando ya casi llegó.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '500 ms de entrada · 350 ms de salida', calm: 'Fundido cruzado de 150 ms, sin crecer.', perf: 'La misma transformación en 350 ms, sin sombra ni desenfoque.', compose: 'SharedTransitionLayout { Modifier.sharedBounds(rememberSharedContentState("card-$id"), this) }',
          play: async (st, m, tok) => { const c = st.querySelector('.mv-hero'), f = st.querySelector('.mv-ficha'), inn = f.firstElementChild; const r = relRect(c, st), W = st.clientWidth, H = st.clientHeight; f.hidden = false;
            if (m === 'calm') { await anim(f, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished; await wait(1100); if (!alive(st, tok)) return; await anim(f, [{ opacity: 1 }, { opacity: 0 }], { duration: 120 }).finished; f.hidden = true; return; }
            const dur = m === 'perf' ? 350 : 500; const from = { transform: `translate(${r.x}px,${r.y}px) scale(${r.w / W},${r.h / H})`, borderRadius: '24px' };
            anim(c, [{ opacity: 1 }, { opacity: 0 }], { duration: 80 }); anim(inn, [{ opacity: 0 }, { opacity: 0, offset: 0.4 }, { opacity: 1 }], { duration: dur });
            await anim(f, [from, { transform: 'none', borderRadius: '0px' }], { duration: dur, easing: EMPH }).finished; await wait(1100); if (!alive(st, tok)) return;
            anim(inn, [{ opacity: 1 }, { opacity: 0 }], { duration: dur * 0.4 }); await anim(f, [{ transform: 'none', borderRadius: '0px' }, from], { duration: dur * 0.7, easing: EASE }).finished; f.hidden = true; anim(c, [{ opacity: 0 }, { opacity: 1 }], { duration: 80 }); } },
        { id: 'axis', name: 'Eje compartido', desc: 'La lista se va un poco a la izquierda y la ficha llega desde la derecha: dice «avanzaste un nivel».', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '350 ms · desplazamiento de 30 px', calm: 'Fundido de 150 ms sin desplazamiento.', perf: 'Igual, en 250 ms (es barato: solo opacidad y traslación).', compose: 'AnimatedContent(transitionSpec = { (slideInHorizontally { it / 10 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 10 } + fadeOut()) })',
          play: async (st, m, tok) => { const l = st.querySelector('.mv-list'), f = st.querySelector('.mv-ficha'); f.hidden = false; const dur = m === 'perf' ? 250 : m === 'calm' ? 150 : 350; const dx = m === 'calm' ? 0 : 30;
            anim(l, [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: `translateX(${-dx}px)` }], { duration: dur * 0.45, easing: EASE }); await anim(f, [{ opacity: 0, transform: `translateX(${dx}px)` }, { opacity: 0, offset: 0.3, transform: `translateX(${dx * 0.7}px)` }, { opacity: 1, transform: 'none' }], { duration: dur, easing: EASE }).finished;
            await wait(1100); if (!alive(st, tok)) return; anim(f, [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: `translateX(${dx}px)` }], { duration: dur * 0.6 }); await anim(l, [{ opacity: 0, transform: `translateX(${-dx}px)` }, { opacity: 1, transform: 'none' }], { duration: dur * 0.7, easing: EASE }).finished; f.hidden = true; } },
        { id: 'paper', name: 'Despliegue de papel', desc: 'La ficha se abre desde la tarjeta como una tira de papel: primero a lo ancho, luego hacia abajo.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '500 ms (40 % ancho, 60 % alto)', calm: 'Fundido de 150 ms; el papel no se pliega.', perf: 'Corte directo con fundido de 150 ms: clip-path animado cuesta en GPU débiles.', compose: 'Modifier.graphicsLayer { clip = true; shape = UnfoldShape(progress.value) } con Animatable(0f).animateTo(1f, tween(500, easing = EmphasizedDecelerate))',
          play: async (st, m, tok) => { const c = st.querySelector('.mv-hero'), f = st.querySelector('.mv-ficha'); const r = relRect(c, st), W = st.clientWidth, H = st.clientHeight; f.hidden = false;
            if (m !== 'full') { await anim(f, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished; await wait(1100); if (!alive(st, tok)) return; await anim(f, [{ opacity: 1 }, { opacity: 0 }], { duration: 120 }).finished; f.hidden = true; return; }
            const a = `inset(${r.y}px ${W - r.x - r.w}px ${H - r.y - r.h}px ${r.x}px round 10px)`, b = `inset(${r.y}px 0px ${H - r.y - r.h}px 0px round 0px)`, z = 'inset(0px 0px 0px 0px round 0px)';
            await anim(f, [{ clipPath: a }, { clipPath: b, offset: 0.4 }, { clipPath: z }], { duration: 500, easing: EMPH }).finished; await wait(1100); if (!alive(st, tok)) return;
            await anim(f, [{ clipPath: z }, { clipPath: b, offset: 0.6 }, { clipPath: a }], { duration: 350, easing: EASE }).finished; f.hidden = true; } },
      ],
    },
    {
      id: 'tanzaku', name: 'Balanceo tanzaku', why: 'Las tiras cuelgan de una vara y se mueven como papel. Solo tiene sentido en direcciones que usan el motivo; nunca en bucle infinito.',
      stage: (aid) => aid === 'scroll' ? `<div class="mv-cord"></div><div class="mv-scroller" tabindex="0" aria-label="Fila de tiras, desplázala">${['tempal', 'laidoff', 'aonohako', 'koori', 'psyren', 'chitose', 'horegusuri', 'goblin', 'magical'].map((id) => `<div class="mv-strip">${img(id, '', 44, 130)}</div>`).join('')}</div>` : `<div class="mv-cord"></div><div class="mv-strips">${['tempal', 'laidoff', 'aonohako', 'koori'].map((id) => `<div class="mv-strip">${img(id, '', 44, 130)}</div>`).join('')}</div><p class="mv-note" hidden></p>`,
      alts: [
        { id: 'spring', name: 'Resorte suave', desc: 'Un empujón al tocar o al llegar: las tiras oscilan y se asientan solas en unos dos segundos.', curve: { spring: [0.3, 50], label: 'spring(dampingRatio = 0.3f, stiffness = 50f)' }, dur: 'Unos 2 s hasta asentarse', calm: 'Sin balanceo: la tira se resalta con un fundido de 150 ms.', perf: 'Solo se mueve la tira tocada, no la fila entera.', compose: 'val a = remember { Animatable(0f) }; a.animateTo(0f, spring(0.3f, Spring.StiffnessVeryLow), initialVelocity = 60f); Modifier.graphicsLayer { rotationZ = a.value; transformOrigin = TransformOrigin(0.5f, 0f) }',
          play: async (st, m, tok) => { const ss = [...st.querySelectorAll('.mv-strip')]; if (m === 'calm') { ss.forEach((s) => anim(s, [{ opacity: 0.55 }, { opacity: 1 }], { duration: 150 })); return; } const list = m === 'perf' ? ss.slice(1, 2) : ss;
            await Promise.all(list.map((s, i) => wait(i * 90).then(() => springRun(st, tok, { ratio: 0.3, stiff: 50, from: 0, v0: 70 - i * 8, to: 0 }, (x) => (s.style.transform = `rotate(${x}deg)`))))); } },
        { id: 'wind', name: 'Viento', desc: 'Una ráfaga de cuatro segundos mueve cada tira con su propio desfase y luego se calma. Sirve para la portada de una temporada, no para cada pantalla.', curve: { spring: [0.6, 50], label: 'spring(0.6f, 50f) siguiendo una ráfaga' }, dur: '4 s de ráfaga + asentado', calm: 'Apagado.', perf: 'Apagado: es una animación continua por fotograma.', compose: 'LaunchedEffect(gust) { while (t < 4f) { a.animateTo(noise(t) * envelope(t), spring(0.6f, 50f)); withFrameNanos { … } } }',
          play: async (st, m, tok) => { const note = st.querySelector('.mv-note'); if (m !== 'full') { note.hidden = false; note.textContent = m === 'calm' ? 'En Calmo el viento no corre.' : 'En Rendimiento el viento no corre.'; await anim(note, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished; return; } note.hidden = true;
            const ss = [...st.querySelectorAll('.mv-strip')]; const env = (t) => (t < 0.6 ? t / 0.6 : t < 3 ? 1 : t < 4 ? 4 - t : 0);
            await Promise.all(ss.map((s, i) => springRun(st, tok, { ratio: 0.6, stiff: 50, from: 0, until: 4, target: (t) => env(t) * (4.5 * Math.sin(1.3 * t + i * 0.9) + 2 * Math.sin(3.1 * t + i * 1.7)) }, (x) => (s.style.transform = `rotate(${x}deg)`)))); } },
        { id: 'scroll', name: 'Reacción al scroll', desc: 'Al desplazar la fila, las tiras se inclinan según la velocidad y vuelven al soltar. Prueba también a desplazarla tú.', curve: { spring: [0.5, 200], label: 'spring(0.5f, 200f), inclinación máx. 8°' }, dur: 'Mientras dura el desplazamiento', calm: 'Sin inclinación; el desplazamiento es normal.', perf: 'Sin inclinación.', compose: 'NestedScrollConnection { override fun onPostScroll(consumed, …) { tilt.snapTo((-consumed.x * 0.2f).coerceIn(-8f, 8f)) } } + animateTo(0f, spring(0.5f, 200f))',
          mount: (st) => { const sc = st.querySelector('.mv-scroller'); let lx = sc.scrollLeft, lt = performance.now(), tilt = 0, run = false, tok = 0;
            sc.addEventListener('scroll', () => { const now = performance.now(); const vx = (sc.scrollLeft - lx) / Math.max(1, now - lt); lx = sc.scrollLeft; lt = now; if (modeOfStage(st) !== 'full') return; tilt = Math.max(-8, Math.min(8, -vx * 6)); if (!run) { run = true; tok = st._tok = (st._tok || 0) + 1; const ss = [...sc.querySelectorAll('.mv-strip')]; springRun(st, tok, { ratio: 0.5, stiff: 200, from: 0, target: () => tilt, until: 0.2 }, (x) => ss.forEach((s) => (s.style.transform = `rotate(${x}deg)`))).then(() => (run = false)); } clearTimeout(sc._t); sc._t = setTimeout(() => (tilt = 0), 90); }, { passive: true }); },
          play: async (st) => { const sc = st.querySelector('.mv-scroller'); sc.scrollBy({ left: sc.scrollLeft > 100 ? -260 : 260, behavior: 'smooth' }); } },
      ],
    },
    {
      id: 'inicio', name: 'Entrada de Inicio', why: 'Inicio se abre muchas veces al día: la entrada tiene que ser corta o no existir. Solo la primera vez por sesión, nunca al volver atrás.',
      stage: () => `<div class="mv-home">${['Continuar viendo', 'Esta noche'].map((t, r) => `<div class="mv-hrow"><span class="mv-cap">${t}</span><div class="mv-posters">${(r ? ['aonohako', 'psyren', 'chitose', 'magical', 'koori'] : ['tempal', 'laidoff', 'horegusuri', 'dawang', 'seitokai']).map((id) => img(id, 'mv-p', 46, 69)).join('')}</div></div>`).join('')}</div>`,
      alts: [
        { id: 'cascade', name: 'Cascada', desc: 'Cada póster llega 30 ms después del anterior; el retraso total se corta en 300 ms.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '240 ms por póster · escalonado 30 ms · tope 300 ms', calm: 'Un solo fundido de 150 ms de toda la pantalla.', perf: 'Sin entrada: el contenido aparece con 100 ms de fundido.', compose: 'LazyRow { itemsIndexed { i, it -> Modifier.graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 8.dp.toPx() } } } con delay(min(i * 30, 300))',
          play: async (st, m) => { const ps = [...st.querySelectorAll('.mv-p')]; await skel(st, ps); if (m !== 'full') return fadeAll(st, m); await Promise.all(ps.map((p, i) => anim(p, [{ opacity: 0, transform: 'translateY(8px)' }, { opacity: 1, transform: 'none' }], { duration: 240, delay: Math.min(i * 30, 300) }).finished)); } },
        { id: 'rows', name: 'Revelado por filas', desc: 'Cada fila se descubre de izquierda a derecha, siguiendo la lectura; las filas se escalonan 80 ms.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '350 ms por fila · escalonado 80 ms', calm: 'Fundido de 150 ms.', perf: 'Sin entrada.', compose: 'Modifier.drawWithContent { clipRect(right = size.width * reveal.value) { this@drawWithContent.drawContent() } }',
          play: async (st, m) => { const ps = [...st.querySelectorAll('.mv-p')]; await skel(st, ps); if (m !== 'full') return fadeAll(st, m); await Promise.all([...st.querySelectorAll('.mv-hrow')].map((r, i) => anim(r, [{ clipPath: 'inset(0 100% 0 0)' }, { clipPath: 'inset(0 0% 0 0)' }], { duration: 350, easing: EMPH, delay: i * 80 }).finished)); } },
        { id: 'none', name: 'Sin entrada', desc: 'El esqueleto se reemplaza por el contenido con un fundido corto. Lo más rápido y lo más honesto si los datos ya están en caché.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '150 ms', calm: 'Igual.', perf: 'Igual, en 100 ms.', compose: 'Crossfade(targetState = uiState, animationSpec = tween(150))',
          play: async (st, m) => { const ps = [...st.querySelectorAll('.mv-p')]; await skel(st, ps); return fadeAll(st, m === 'perf' ? 'perf' : 'calm'); } },
      ],
    },
    {
      id: 'visto', name: 'Marcar visto', why: 'Confirma que la acción quedó hecha. Tiene que leerse de reojo y no frenar a quien marca diez episodios seguidos.',
      stage: (aid) => `<div class="mv-ep" data-on="false"><span class="mv-ep-n">2</span><span class="mv-ep-t">${esc(D.laidoffEpisodes[1].title)}</span>${aid === 'stamp' ? `<span class="mv-seal" aria-hidden="true">${ic('check', { size: 18 })}</span>` : ''}${aid === 'band' ? '<i class="mv-band"></i>' : ''}<span class="mv-ep-lbl">Sin ver</span></div>${aid === 'stations' ? `<div class="mv-stations" aria-hidden="true"><i class="mv-line"><b></b></i>${Array.from({ length: 8 }, (_, i) => `<span class="${i < 1 ? 'on' : ''}"></span>`).join('')}</div>` : ''}`,
      alts: [
        { id: 'stamp', name: 'Sello', desc: 'Un sello cae sobre la fila con un resorte corto. Encaja con las direcciones de tinta y hanko.', curve: { spring: [0.6, 1500], label: 'spring(dampingRatio = 0.6f, stiffness = 1500f)' }, dur: 'Unos 250 ms', calm: 'El sello aparece con un fundido de 120 ms, sin escala.', perf: 'Cambia el icono, sin escala ni giro.', compose: 'val s by animateFloatAsState(if (watched) 1f else 1.6f, spring(0.6f, Spring.StiffnessMedium)); Modifier.graphicsLayer { scaleX = s; scaleY = s; rotationZ = -6f }',
          play: async (st, m, tok) => markToggle(st, m, async (on) => { const s = st.querySelector('.mv-seal'); if (!on) { s.getAnimations().forEach((a) => a.cancel()); s.style.opacity = 0; s.style.transform = ''; return; } if (m !== 'full') { if (m === 'calm') await anim(s, [{ opacity: on ? 0 : 1 }, { opacity: on ? 1 : 0 }], { duration: 120 }).finished; return; } s.style.opacity = 1; await springRun(st, tok, { ratio: 0.6, stiff: 1500, from: 1.6, to: 1 }, (x) => (s.style.transform = `scale(${x}) rotate(${-6 - (x - 1) * 14}deg)`)); }) },
        { id: 'stations', name: 'Relleno de estación', desc: 'La línea de progreso avanza hasta la estación nueva, como en un mapa de tren.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '350 ms + 120 ms la estación', calm: 'La estación se enciende con un fundido de 120 ms.', perf: 'Salta al estado final.', compose: 'val p by animateFloatAsState(progress, tween(350, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))); Modifier.drawBehind { drawLine(…, end = Offset(size.width * p, cy)) }',
          play: async (st, m) => markToggle(st, m, async (on) => { const b = st.querySelector('.mv-line b'); const dots = st.querySelectorAll('.mv-stations span'); const a = on ? 0 : 1 / 7, z = on ? 1 / 7 : 0; dots[1].classList.toggle('on', on); if (m === 'full') { await anim(b, [{ transform: `scaleX(${a})` }, { transform: `scaleX(${z})` }], { duration: 350 }).finished; if (on) anim(dots[1], [{ transform: 'scale(.6)' }, { transform: 'none' }], { duration: 120 }); } else if (m === 'calm') anim(dots[1], [{ opacity: 0.3 }, { opacity: 1 }], { duration: 120 }); b.style.transform = `scaleX(${z})`; }) },
        { id: 'band', name: 'Barrido de faja', desc: 'Una faja de color cruza la fila de izquierda a derecha y la deja teñida. Se lee bien en listas densas.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '350 ms', calm: 'La fila se tiñe con un fundido de 120 ms.', perf: 'Se tiñe sin animación.', compose: 'Modifier.drawBehind { drawRect(tint, size = size.copy(width = size.width * sweep.value)) }',
          play: async (st, m) => markToggle(st, m, async (on) => { const b = st.querySelector('.mv-band'); if (m === 'full') await anim(b, [{ transform: `scaleX(${on ? 0 : 1})` }, { transform: `scaleX(${on ? 1 : 0})` }], { duration: on ? 350 : 200, easing: on ? EMPH : EASE }).finished; else if (m === 'calm') await anim(b, [{ opacity: on ? 0 : 1, transform: 'scaleX(1)' }, { opacity: on ? 1 : 0, transform: 'scaleX(1)' }], { duration: 120 }).finished; b.style.transform = `scaleX(${on ? 1 : 0})`; }) },
      ],
    },
    {
      id: 'controles', name: 'Mostrar y ocultar controles del reproductor', why: 'Pasa cientos de veces por sesión, encima del video. Cuanto menos se note, mejor; ocultar es más rápido que mostrar.',
      stage: () => `<div class="mv-player"><img src="img/frame-laidoff.webp" alt="" width="320" height="180"><i class="mv-scrim"></i><div class="mv-top">${ic('arrow_back', { size: 18 })}<span>Laid-Off Mage · Ep. 2</span></div><span class="mv-center">${ic('pause', { size: 28, fill: true })}</span><div class="mv-bottom"><span>08:12</span><i class="mv-seek"><b></b></i><span>23:40</span></div><i class="mv-mini"><b></b></i></div>`,
      alts: [
        { id: 'fade', name: 'Fundido', desc: 'Todo junto aparece y desaparece. Sin desplazamiento que distraiga del video.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '200 ms al mostrar · 150 ms al ocultar', calm: 'Fundido de 120 ms.', perf: 'Igual, y sin el degradado oscuro detrás: barras sólidas.', compose: 'AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(150)))',
          play: async (st, m, tok) => ctlCycle(st, m, tok, async (show, els) => { const d = m === 'calm' ? 120 : show ? 200 : 150; await Promise.all(els.map((e) => anim(e, [{ opacity: show ? 0 : 1 }, { opacity: show ? 1 : 0 }], { duration: d }).finished)); }) },
        { id: 'slide', name: 'Deslizado', desc: 'La barra superior sube, la inferior baja y el botón central se encoge. Indica de dónde vuelven.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '250 ms al mostrar · 200 ms al ocultar', calm: 'Fundido de 120 ms sin desplazamiento.', perf: 'Fundido de 150 ms; sin degradado.', compose: 'AnimatedVisibility(enter = slideInVertically { -it / 3 } + fadeIn(), exit = slideOutVertically { -it / 3 } + fadeOut())',
          play: async (st, m, tok) => ctlCycle(st, m, tok, async (show, els) => { const off = { 'mv-top': 'translateY(-12px)', 'mv-bottom': 'translateY(16px)', 'mv-center': 'scale(.88)', 'mv-scrim': 'none' }; const d = m === 'calm' ? 120 : m === 'perf' ? 150 : show ? 250 : 200; await Promise.all(els.map((e) => { const t = m === 'full' ? off[e.classList[0]] || 'none' : 'none'; return anim(e, show ? [{ opacity: 0, transform: t }, { opacity: 1, transform: 'none' }] : [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: t }], { duration: d, easing: show ? EMPH : EASE }).finished; })); }) },
        { id: 'min', name: 'Mínimo', desc: 'Al ocultar queda una línea de progreso de 2 px; al mostrar, esa línea crece hasta ser la barra completa.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '150 ms', calm: 'Fundido de 120 ms; la línea queda fija.', perf: 'Igual que en Calmo.', compose: 'val h by animateDpAsState(if (visible) 40.dp else 2.dp, tween(150)); la línea mínima siempre compuesta, el resto en AnimatedVisibility',
          play: async (st, m, tok) => ctlCycle(st, m, tok, async (show, els) => { const mini = st.querySelector('.mv-mini'); const bot = st.querySelector('.mv-bottom'); const d = m === 'calm' || m === 'perf' ? 120 : 150; anim(mini, [{ opacity: show ? 1 : 0 }, { opacity: show ? 0 : 1 }], { duration: d }); await Promise.all(els.map((e) => anim(e, e === bot && m === 'full' ? (show ? [{ opacity: 0, transform: 'scaleY(.1)' }, { opacity: 1, transform: 'none' }] : [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'scaleY(.1)' }]) : [{ opacity: show ? 0 : 1 }, { opacity: show ? 1 : 0 }], { duration: d }).finished)); }) },
      ],
    },
    {
      id: 'carga', name: 'Carga', why: 'Buscar fuentes puede tardar segundos. El indicador tiene que decir qué está pasando, no solo que algo se mueve.',
      stage: (aid) => `<div class="mv-srcs">${D.sources.slice(0, 3).map((s) => `<div class="mv-src"><b>${esc(s.group)}</b><span>${esc(s.res)} · ${esc(s.codec)} · ${esc(s.size)}</span></div>`).join('')}</div>${aid === 'shimmer' ? '<i class="mv-shimmer" hidden></i>' : ''}${aid === 'ink' ? '<i class="mv-ink" hidden></i><p class="mv-note" hidden></p>' : ''}${aid === 'count' ? '<div class="mv-count" hidden><span>Buscando fuentes · 0 de 6 extensiones</span><i class="mv-bar"><b></b></i></div>' : ''}`,
      alts: [
        { id: 'shimmer', name: 'Esqueleto con brillo', desc: 'Bloques con la forma real de las filas y un brillo que las recorre mientras carga.', curve: { label: 'linear (brillo) · 150 ms de entrada' }, dur: '1,2 s por pasada, solo mientras carga', calm: 'Esqueleto quieto, sin brillo.', perf: 'Esqueleto quieto, sin brillo.', compose: 'val x by rememberInfiniteTransition().animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing))); Brush.linearGradient(start = Offset(x * w, 0f), …) solo mientras isLoading',
          play: async (st, m, tok) => { const box = st.querySelector('.mv-srcs'), sh = st.querySelector('.mv-shimmer'); box.classList.add('is-skel'); if (m === 'full') { sh.hidden = false; await anim(sh, [{ transform: 'translateX(-100%)' }, { transform: 'translateX(100%)' }], { duration: 1200, easing: 'linear', iterations: 2 }).finished; sh.hidden = true; } else await wait(1600); if (!alive(st, tok)) return; box.classList.remove('is-skel'); if (m !== 'perf') await anim(box, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished; } },
        { id: 'ink', name: 'Tinta que se expande', desc: 'Una gota de tinta late en el centro y, al llegar los datos, se abre en círculo y descubre la lista.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '900 ms por latido · 500 ms al revelar', calm: 'Texto «Cargando fuentes…» y fundido de 150 ms.', perf: 'Texto y corte directo: sin clip-path.', compose: 'Modifier.drawWithContent { clipPath(Path().apply { addOval(Rect(center, radius.value)) }) { this@drawWithContent.drawContent() } }',
          play: async (st, m, tok) => { const box = st.querySelector('.mv-srcs'), ink = st.querySelector('.mv-ink'), note = st.querySelector('.mv-note'); box.style.opacity = 0;
            if (m !== 'full') { note.hidden = false; note.textContent = 'Cargando fuentes…'; await wait(1400); if (!alive(st, tok)) return; note.hidden = true; box.style.opacity = ''; if (m === 'calm') await anim(box, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished; return; }
            ink.hidden = false; await anim(ink, [{ transform: 'scale(0)', opacity: 0.55 }, { transform: 'scale(1)', opacity: 0 }], { duration: 900, easing: EMPH, iterations: 2 }).finished; ink.hidden = true; if (!alive(st, tok)) return; box.style.opacity = '';
            await anim(box, [{ clipPath: 'circle(0% at 50% 50%)' }, { clipPath: 'circle(75% at 50% 50%)' }], { duration: 500, easing: EMPH }).finished; } },
        { id: 'count', name: 'Contador', desc: 'Dice cuántas extensiones respondieron. Es lo más informativo: se ve si una se colgó.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: 'Cada respuesta mueve la barra 200 ms · 150 ms de entrada', calm: 'El número cambia sin mover la barra.', perf: 'Igual que en Calmo.', compose: 'Text("Buscando fuentes · $done de $total") + LinearProgressIndicator(progress = { animatedProgress })',
          play: async (st, m, tok) => { const box = st.querySelector('.mv-srcs'), c = st.querySelector('.mv-count'), t = c.querySelector('span'), b = c.querySelector('b'); box.style.opacity = 0; c.hidden = false; for (let i = 0; i <= 6; i++) { if (!alive(st, tok)) return; t.textContent = `Buscando fuentes · ${i} de 6 extensiones`; const v = i / 6; if (m === 'full') anim(b, [{ transform: `scaleX(${Math.max(0, (i - 1) / 6)})` }, { transform: `scaleX(${v})` }], { duration: 200 }); else b.style.transform = `scaleX(${v})`; await wait(240); } b.style.transform = 'scaleX(1)'; c.hidden = true; box.style.opacity = ''; if (m !== 'perf') await anim(box, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished; } },
      ],
    },
    {
      id: 'estado', name: 'Cambio de estado de lista', why: 'El estado cambia el color del título en toda la app. El movimiento dice qué cambió y hacia dónde.',
      stage: () => `<div class="mv-stage-st" data-cur="planning"><div class="mv-stposter">${img('goblin', '', 70, 105)}<span class="mv-chip nj-status" data-status="planning"><span class="mv-odo-win"><span class="mv-odo"><span>Pendiente</span></span></span></span><i class="mv-ribbon" data-status="planning"></i></div><div class="mv-seg"><i class="mv-ind" data-status="planning"></i>${['planning', 'watching', 'completed'].map((s) => `<span data-s="${s}">${esc(stName(s))}</span>`).join('')}</div></div>`,
      alts: [
        { id: 'ind', name: 'Indicador que se desliza', desc: 'La marca del segmentado viaja al estado nuevo y cambia de color en el camino.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '250 ms', calm: 'El color cambia con un fundido de 120 ms; la marca salta.', perf: 'Salta sin animación.', compose: 'val x by animateDpAsState(segmentOffset(selected), tween(250, easing = EmphasizedDecelerate)); Modifier.offset { IntOffset(x.roundToPx(), 0) }',
          play: async (st, m) => stCycle(st, m, 'ind') },
        { id: 'ribbon', name: 'Cinta de estado', desc: 'Una cinta en el borde del póster se reemplaza con un barrido del color nuevo.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '300 ms', calm: 'Fundido de color de 120 ms.', perf: 'Cambio de color directo.', compose: 'Modifier.drawWithContent { drawContent(); clipRect(right = size.width * wipe.value) { drawRect(newColor, topLeft = ribbonTop) } }',
          play: async (st, m) => stCycle(st, m, 'ribbon') },
        { id: 'odo', name: 'Odómetro', desc: 'El nombre del estado rueda hacia arriba como un contador y el chip cambia de color.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '180 ms', calm: 'El texto cambia con un fundido de 120 ms.', perf: 'Cambio directo.', compose: 'AnimatedContent(status, transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) })',
          play: async (st, m) => stCycle(st, m, 'odo') },
      ],
    },
    {
      id: 'snack', name: 'Snackbar con deshacer', why: 'Cada acción reversible deja un aviso con Deshacer. Lo importante no es cómo entra sino que no se pierda el anterior.',
      stage: () => `<div class="mv-snackstage"><span class="mv-trigger">${ic('visibility', { size: 16 })}Marcar 4 como vistos</span><ol class="mv-eps">${D.laidoffEpisodes.slice(2, 6).map((e) => `<li>Ep. ${e.n} · ${esc(e.title)}</li>`).join('')}</ol><div class="mv-snackhost"></div></div>`,
      alts: [
        { id: 'rise', name: 'Sube desde abajo', desc: 'Entra 16 px desde abajo con una barra de vida que se vacía. Pasar el puntero la detiene.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1) · vida lineal' }, dur: '220 ms al entrar · 150 ms al salir · 8 s de vida (aquí 3 s)', calm: 'Fundido de 150 ms; sin barra animada, el historial guarda la acción.', perf: 'Sin sombra; la barra de vida se mantiene (es una sola capa).', compose: 'SnackbarHost(state) { AnimatedVisibility(enter = slideInVertically { it / 4 } + fadeIn(tween(220))) { Snackbar(…); LinearProgressIndicator(progress = { life.value }) } }',
          play: async (st, m, tok) => snackRun(st, m, tok, 'rise') },
        { id: 'stack', name: 'Apilado', desc: 'Si llega otro aviso, el anterior sube un poco y queda detrás con su propio Deshacer.', curve: { bezier: [0.2, 0, 0, 1], label: 'cubic-bezier(.2,0,0,1)' }, dur: '220 ms · 8 px de separación · máximo 2 a la vista', calm: 'Los dos aparecen con fundido; el de atrás no se desplaza.', perf: 'Solo se ve el último, con «y 1 más» que abre el historial.', compose: 'Column { queue.takeLast(2).forEach { key(it.id) { Snackbar(Modifier.animateEnterExit()…) } } } con un SnackbarQueue propio',
          play: async (st, m, tok) => snackRun(st, m, tok, 'stack') },
        { id: 'origin', name: 'Crece desde la acción', desc: 'El aviso nace del botón que lo provocó, así se entiende de dónde viene.', curve: { bezier: [0.05, 0.7, 0.1, 1], label: 'cubic-bezier(.05,.7,.1,1)' }, dur: '250 ms', calm: 'Fundido de 150 ms en su lugar.', perf: 'Fundido de 150 ms.', compose: 'Modifier.graphicsLayer { transformOrigin = originOf(trigger); scaleX = s.value; scaleY = s.value }',
          play: async (st, m, tok) => snackRun(st, m, tok, 'origin') },
      ],
    },
  ];
  /* ayudantes de los momentos */
  async function skel(st, ps) { ps.forEach((p) => p.classList.add('is-skel')); await wait(450); ps.forEach((p) => p.classList.remove('is-skel')); }
  function fadeAll(st, m) { const home = st.querySelector('.mv-home'); return anim(home, [{ opacity: 0 }, { opacity: 1 }], { duration: m === 'perf' ? 100 : 150 }).finished; }
  async function markToggle(st, m, fx) { const row = st.querySelector('.mv-ep'); const on = row.dataset.on !== 'true'; row.dataset.on = String(on); row.querySelector('.mv-ep-lbl').textContent = on ? 'Visto' : 'Sin ver'; await fx(on); }
  async function ctlCycle(st, m, tok, fx) {
    const els = ['.mv-top', '.mv-bottom', '.mv-center', '.mv-scrim'].map((s) => st.querySelector(s)).filter((e) => e && getComputedStyle(e).display !== 'none');
    await fx(false, els); els.forEach((e) => (e.style.opacity = 0));
    await wait(900); if (!alive(st, tok)) return; els.forEach((e) => (e.style.opacity = '')); await fx(true, els);
  }
  const ST_CYCLE = ['planning', 'watching', 'completed'];
  async function stCycle(st, m, which) {
    const box = st.querySelector('.mv-stage-st'); const cur = box.dataset.cur; const nx = ST_CYCLE[(ST_CYCLE.indexOf(cur) + 1) % 3]; box.dataset.cur = nx;
    const ind = st.querySelector('.mv-ind'), seg = st.querySelector('.mv-seg'), chip = st.querySelector('.mv-chip'), odo = chip.querySelector('.mv-odo'), rib = st.querySelector('.mv-ribbon');
    const segEl = seg.querySelector(`[data-s="${nx}"]`); const fromX = ind.offsetLeft; ind.style.left = segEl.offsetLeft + 'px'; ind.style.width = segEl.offsetWidth + 'px'; ind.dataset.status = nx; seg.querySelectorAll('[data-s]').forEach((s) => s.classList.toggle('on', s.dataset.s === nx));
    const oldName = odo.textContent; chip.dataset.status = nx; rib.dataset.status = nx;
    const jobs = [];
    if (which === 'ind' && m === 'full') jobs.push(anim(ind, [{ transform: `translateX(${fromX - segEl.offsetLeft}px)` }, { transform: 'none' }], { duration: 250, easing: EMPH }).finished);
    if (which === 'ind' && m === 'calm') jobs.push(anim(ind, [{ opacity: 0.2 }, { opacity: 1 }], { duration: 120 }).finished);
    if (which === 'ribbon' && m === 'full') { const nw = document.createElement('i'); nw.className = 'mv-ribbon is-new'; nw.dataset.status = nx; rib.dataset.status = cur; rib.parentElement.appendChild(nw); jobs.push(anim(nw, [{ clipPath: 'inset(0 100% 0 0)' }, { clipPath: 'inset(0 0% 0 0)' }], { duration: 300, easing: EMPH }).finished.then(() => { rib.dataset.status = nx; nw.remove(); })); }
    if (which === 'ribbon' && m === 'calm') jobs.push(anim(rib, [{ opacity: 0.2 }, { opacity: 1 }], { duration: 120 }).finished);
    if (which === 'odo') {
      if (m === 'full') { odo.innerHTML = `<span>${esc(oldName)}</span><span>${esc(stName(nx))}</span>`; jobs.push(anim(odo, [{ transform: 'translateY(0)' }, { transform: 'translateY(-50%)' }], { duration: 180 }).finished.then(() => (odo.innerHTML = `<span>${esc(stName(nx))}</span>`))); }
      else { odo.innerHTML = `<span>${esc(stName(nx))}</span>`; if (m === 'calm') jobs.push(anim(odo, [{ opacity: 0 }, { opacity: 1 }], { duration: 120 }).finished); }
    } else odo.innerHTML = `<span>${esc(stName(nx))}</span>`;
    await Promise.all(jobs);
  }
  async function snackRun(st, m, tok, kind) {
    const host = st.querySelector('.mv-snackhost'); host.innerHTML = '';
    const mk = (msg, n) => { const s = document.createElement('div'); s.className = 'nj-snackbar mv-snack'; s.innerHTML = `<span>${esc(msg)}</span><span class="mv-undo">Deshacer</span>${n ? `<span class="mv-more">y ${n} más</span>` : ''}${m !== 'calm' ? '<i class="lab-snack-life"></i>' : ''}`; host.appendChild(s); return s; };
    const life = (s, ms) => { const l = s.querySelector('.lab-snack-life'); if (l) anim(l, [{ transform: 'scaleX(1)' }, { transform: 'scaleX(0)' }], { duration: ms, easing: 'linear' }); };
    const enter = (s) => (m === 'full' ? anim(s, [{ opacity: 0, transform: 'translateY(16px)' }, { opacity: 1, transform: 'none' }], { duration: 220 }) : m === 'calm' ? anim(s, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }) : anim(s, [{ opacity: 1 }, { opacity: 1 }], { duration: 1 })).finished;
    if (kind === 'origin') {
      const tr = st.querySelector('.mv-trigger'); const s = mk('4 episodios marcados como vistos'); life(s, 3000);
      if (m === 'full') { const a = relRect(tr, host), b = relRect(s, host); s.style.transformOrigin = `${a.x - b.x + a.w / 2}px ${a.y - b.y + a.h / 2}px`; await anim(s, [{ opacity: 0, transform: 'scale(.4)' }, { opacity: 1, transform: 'none' }], { duration: 250, easing: EMPH }).finished; }
      else await anim(s, [{ opacity: 0 }, { opacity: 1 }], { duration: 150 }).finished;
    } else if (kind === 'stack') {
      const s1 = mk('4 episodios marcados como vistos'); life(s1, 3600); await enter(s1); await wait(600); if (!alive(st, tok)) return;
      if (m === 'perf') { s1.remove(); const s2 = mk('Descargando 4 episodios', 1); life(s2, 3000); }
      else { const s2 = mk('Descargando 4 episodios'); life(s2, 3000); s1.classList.add('is-back'); if (m === 'full') anim(s1, [{ transform: 'none', opacity: 1 }, { transform: 'translateY(-8px) scale(.96)', opacity: 0.75 }], { duration: 220 }); else s1.style.opacity = 0.75; await enter(s2); }
    } else { const s = mk('4 episodios marcados como vistos'); life(s, 3000); await enter(s); }
    await wait(3000); if (!alive(st, tok)) return;
    await Promise.all([...host.children].map((s) => anim(s, [{ opacity: s.style.opacity || 1 }, { opacity: 0 }], { duration: m === 'perf' ? 1 : 150 }).finished)); host.innerHTML = '';
  }

  function momentSection(mo) {
    const mode = MV[mo.id] || NJ.state.motion || 'full';
    return `<section class="x-panel lab-moment" id="mv-${mo.id}" aria-labelledby="mv-${mo.id}-h"><div class="lab-demo-head"><h2 id="mv-${mo.id}-h">${esc(mo.name)}</h2>
      <div class="lab-mvmode"><span id="mv-${mo.id}-ml">Ver en</span><div class="x-seg" role="group" aria-labelledby="mv-${mo.id}-ml">${[['full', 'Completo'], ['calm', 'Calmo'], ['perf', 'Rendimiento']].map(([v, l]) => `<button type="button" data-mvmode="${mo.id}:${v}" aria-pressed="${mode === v}">${l}</button>`).join('')}</div></div></div>
      <p class="x-note">${esc(mo.why)}</p>
      <div class="lab-alts">${mo.alts.map((a) => `<article class="lab-alt" aria-labelledby="mv-${mo.id}-${a.id}">
        <div class="lab-alt-head"><h3 id="mv-${mo.id}-${a.id}">${esc(a.name)}</h3><button type="button" class="x-btn is-primary" data-play="${mo.id}:${a.id}" aria-label="Reproducir ${esc(a.name)}"><span class="nj-ic" aria-hidden="true">play_arrow</span>Reproducir</button></div>
        <p class="x-note">${esc(a.desc)}</p>
        <div ${appAttrs('desktop', { cls: 'lab-stage', motion: mode, attrs: ` data-stage="${mo.id}:${a.id}" data-alt="${a.id}"` })}>${mo.stage(a.id)}</div>
        <dl class="lab-spec"><div><dt>Curva</dt><dd class="lab-spec-curve">${curveSvg(a.curve)}<code>${esc(a.curve.label || (a.curve.bezier ? `cubic-bezier(${a.curve.bezier.join(',')})` : 'linear'))}</code></dd></div><div><dt>Duración</dt><dd>${esc(a.dur)}</dd></div><div><dt>En Calmo</dt><dd>${esc(a.calm)}</dd></div><div><dt>En Rendimiento</dt><dd>${esc(a.perf)}</dd></div><div><dt>En Compose</dt><dd><code class="lab-code">${esc(a.compose)}</code></dd></div></dl>
      </article>`).join('')}</div></section>`;
  }
  function perfPanel() {
    return `<section class="x-panel lab-perf" id="mv-perf" aria-labelledby="mv-perf-h"><h2 id="mv-perf-h">Modo rendimiento</h2>
      <p>Tres niveles, elegidos por la app y cambiables en Ajustes › Apariencia › Movimiento. El nivel nunca cambia lo que se puede hacer, solo cuánto se mueve y cuánto cuesta dibujarlo.</p>
      <div class="x-scroll"><table class="x-table lab-scale"><thead><tr><th scope="col"></th><th scope="col">Completo</th><th scope="col">Calmo</th><th scope="col">Rendimiento</th></tr></thead><tbody>
        <tr><th scope="row">Cuándo</th><td>Por defecto en equipos de gama alta y media alta.</td><td>Automático si el sistema pide quitar animaciones; también a mano.</td><td>Automático al detectar un equipo de gama baja; se puede desactivar.</td></tr>
        <tr><th scope="row">Movimiento</th><td>Un momento de autor por pantalla, resortes y transiciones espaciales.</td><td>Sin desplazamientos, balanceos ni parallax. Solo fundidos de 150 ms o menos.</td><td>Transiciones cortas de opacidad y traslación, una animación a la vez.</td></tr>
        <tr><th scope="row">Desenfoque</th><td>Solo donde la dirección lo justifica, horneado a un mapa de bits chico.</td><td>Igual que Completo (no es movimiento).</td><td>Apagado: superficies sólidas.</td></tr>
        <tr><th scope="row">Sombras</th><td>Elevación tonal más sombra en hojas y menús.</td><td>Igual que Completo.</td><td>Solo elevación tonal y un borde de 1 px; ninguna sombra grande.</td></tr>
        <tr><th scope="row">Canvas continuos</th><td>Viento tanzaku, tinta, brillo de esqueleto mientras se ven.</td><td>Apagados.</td><td>Apagados; esqueletos quietos.</td></tr>
        <tr><th scope="row">Varias animaciones a la vez</th><td>Hasta tres, con escalonado tope de 300 ms.</td><td>Una.</td><td>Una; la cascada de Inicio y la reproducción automática del destacado se apagan.</td></tr>
      </tbody></table></div>
      <div class="x-grid-2"><div class="lab-perf-col"><h3>Cómo se detecta en Android</h3><ul class="x-list">
        <li><code>ActivityManager.isLowRamDevice()</code> devuelve <code>true</code>.</li>
        <li>Clase de rendimiento: <code>DevicePerformance.mediaPerformanceClass</code> (androidx.core.performance) menor que <code>Build.VERSION_CODES.S</code>, o sin declarar.</li>
        <li>Refresco de pantalla de 60 Hz o menos (<code>Display.getRefreshRate()</code> ≤ 60): se acortan las duraciones, porque cada cuadro pesa más.</li>
        <li>Calmo: <code>Settings.Global.ANIMATOR_DURATION_SCALE</code> en 0 o la opción del sistema «Quitar animaciones».</li>
        <li>Teléfono de prueba: moto g51 5G. Medir allí antes de fijar los umbrales.</li></ul></div>
        <div class="lab-perf-col"><h3>Cómo se detecta en escritorio</h3><ul class="x-list">
        <li>Skiko sin GPU: <code>SkiaLayer.renderApi</code> en <code>SOFTWARE_FAST</code> o <code>SOFTWARE_COMPAT</code>.</li>
        <li>Renderizado por software del sistema o controlador genérico (llvmpipe, Microsoft Basic Render Driver).</li>
        <li>Si más del 20 % de los cuadros pasan de 16,7 ms durante 5 s, la app sugiere Rendimiento con un aviso; no lo impone.</li>
        <li>Calmo: <code>gsettings get org.gnome.desktop.interface enable-animations</code> en Linux y <code>SPI_GETCLIENTAREAANIMATION</code> en Windows.</li></ul></div></div>
      <h3>Presupuestos</h3>
      <div class="x-scroll"><table class="x-table lab-budget"><thead><tr><th scope="col">Duración</th><th scope="col">Para qué</th><th scope="col">Ejemplos de esta página</th></tr></thead><tbody>
        <tr><td><b>100 ms</b></td><td>Respuesta inmediata a una presión.</td><td>Botón presionado, chip, interruptor, contenido en Rendimiento.</td></tr>
        <tr><td><b>200 ms</b></td><td>Cambio de estado de un control.</td><td>Mostrar controles del reproductor, snackbar, reordenar al soltar.</td></tr>
        <tr><td><b>350 ms</b></td><td>Cambio de disposición, hoja o superposición.</td><td>Eje compartido, marcar visto, revelado por filas.</td></tr>
        <tr><td><b>500 ms</b></td><td>Un momento de autor, uno por pantalla.</td><td>Tarjeta a ficha, despliegue de papel.</td></tr>
      </tbody></table></div>
      <div class="x-scroll"><table class="x-table lab-budget"><thead><tr><th scope="col">Curva o resorte</th><th scope="col">Valor</th><th scope="col">En Compose</th><th scope="col">Uso</th></tr></thead><tbody>
        <tr><td>Salida exponencial</td><td><code>cubic-bezier(.2,0,0,1)</code></td><td><code>CubicBezierEasing(0.2f, 0f, 0f, 1f)</code></td><td>Casi todo: estados, menús, avisos.</td></tr>
        <tr><td>Énfasis</td><td><code>cubic-bezier(.05,.7,.1,1)</code></td><td><code>CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)</code></td><td>Entradas que llegan lejos: ficha, revelados.</td></tr>
        <tr><td>Resorte de interfaz</td><td>ζ 1, rigidez 400</td><td><code>spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)</code></td><td>Hojas, indicadores, arrastre al soltar.</td></tr>
        <tr><td>Sello</td><td>ζ 0,6, rigidez 1500</td><td><code>spring(0.6f, Spring.StiffnessMedium)</code></td><td>Marcar visto con sello.</td></tr>
        <tr><td>Reacción al scroll</td><td>ζ 0,5, rigidez 200</td><td><code>spring(0.5f, Spring.StiffnessLow)</code></td><td>Inclinación de tiras.</td></tr>
        <tr><td>Tanzaku</td><td>ζ 0,3, rigidez 50</td><td><code>spring(0.3f, Spring.StiffnessVeryLow)</code></td><td>Balanceo de tiras, solo en Completo.</td></tr>
      </tbody></table></div>
      <p class="x-note">Reglas que valen en los tres niveles: salir dura el 70 % de entrar; en filas repetidas el hover cambia color, no transformación; en <code>LazyColumn</code> se anima con <code>graphicsLayer</code>, nunca el layout; nada se mueve en bucle más de 5 s sin que el usuario lo pida.</p></section>`;
  }
  NJ.registerLab({
    id: 'movimiento', name: 'Movimiento', order: 2,
    render(el) {
      el.innerHTML = `<header class="lab-pagehead"><h1>Laboratorio de movimiento</h1><p class="x-lede">Ocho momentos de la app, cada uno con dos o tres alternativas que se pueden reproducir. Cada alternativa muestra su curva, su duración y qué hace en los modos Calmo y Rendimiento. Cambia el modo de cada momento con «Ver en», o el de toda la página desde la barra de arriba.</p>${RM.matches ? '<p class="x-note">Tu sistema pide menos movimiento: lo que está en Completo se reproduce como Calmo.</p>' : ''}</header>
        <nav class="lab-index" aria-label="Momentos en esta página">${MOMENTS.map((m) => `<button type="button" class="x-btn" data-jump="mv-${m.id}">${esc(m.name)}</button>`).join('')}<button type="button" class="x-btn" data-jump="mv-perf">Modo rendimiento</button></nav>
        ${MOMENTS.map(momentSection).join('')}${perfPanel()}`;
      el.querySelectorAll('[data-jump]').forEach((b) => b.addEventListener('click', () => { const t = document.getElementById(b.dataset.jump); if (t) { t.scrollIntoView({ behavior: RM.matches ? 'auto' : 'smooth', block: 'start' }); t.setAttribute('tabindex', '-1'); t.focus({ preventScroll: true }); } }));
      el.querySelectorAll('[data-play]').forEach((b) => b.addEventListener('click', () => {
        const [mid, aid] = b.dataset.play.split(':'); const mo = MOMENTS.find((x) => x.id === mid), a = mo.alts.find((x) => x.id === aid);
        const st = el.querySelector(`[data-stage="${mid}:${aid}"]`); const tok = stopAll(st);
        if (!['tanzaku', 'visto', 'estado'].includes(mid) || (mid === 'tanzaku' && aid !== 'scroll')) { st.innerHTML = mo.stage(aid); if (a.mount) a.mount(st); }
        Promise.resolve(a.play(st, modeOfStage(st), tok)).catch((e) => { if (e && e.name !== 'AbortError') console.error(e); });
      }));
      el.querySelectorAll('[data-mvmode]').forEach((b) => b.addEventListener('click', () => {
        const [mid, v] = b.dataset.mvmode.split(':'); MV[mid] = v;
        const sec = el.querySelector(`#mv-${mid}`); sec.querySelectorAll('[data-mvmode]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
        sec.querySelectorAll('.lab-stage').forEach((st) => { stopAll(st); st.dataset.motion = v; });
      }));
      MOMENTS.forEach((mo) => mo.alts.forEach((a) => { if (a.mount) { const st = el.querySelector(`[data-stage="${mo.id}:${a.id}"]`); if (st) a.mount(st); } }));
      decorate(el);
    },
  });
})();
