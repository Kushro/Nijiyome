/* Pantallas del agente S3: Transferencias, Fuentes, Link, Manga, Lector y Bienvenida. Ver CONTRATO.md.
   Todo el estado es efímero (ctx.state). Fuentes, tamaños, pares, voces y cifras son de ejemplo. */
(function () {
  'use strict';
  const NJ = window.NJ;
  if (!NJ || !NJ.registerScreen) return;
  const h = NJ.h;
  const D = NJ.data;
  const esc = h.esc;

  /* ---------------- utilidades comunes ---------------- */
  const ic = (n, o) => h.icon(n, o || {});
  const A = (ctx) => ctx.platform === 'android';
  const pid = (ctx, k) => (A(ctx) ? 'a-' : 'd-') + k;
  const clone = (v) => JSON.parse(JSON.stringify(v));
  const pct = (p) => Math.round((p || 0) * 100) + ' %';
  const T = (id) => D.byId[id];
  const reduced = () => { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; } };
  const canMove = (ctx) => ctx.motion === 'full' && !reduced();
  const mm = (min) => String(Math.floor(min / 60) % 24).padStart(2, '0') + ':' + String(min % 60).padStart(2, '0');

  function init(ctx, defs) {
    const s = ctx.state;
    Object.keys(defs).forEach((k) => { if (!(k in s)) s[k] = clone(defs[k]); });
    return s;
  }

  /* Re-dibuja conservando el scroll de las zonas [data-keep] y el foco [data-fk]. */
  function rr(ctx, root) {
    const s = ctx.state;
    s._scroll = {};
    if (root) root.querySelectorAll('[data-keep]').forEach((n) => { s._scroll[n.getAttribute('data-keep')] = n.scrollTop; });
    const a = document.activeElement;
    const f = a && root && root.contains(a) ? a.closest('[data-fk]') : null;
    s._focus = f ? f.getAttribute('data-fk') : null;
    ctx.rerender();
  }
  function restore(el, ctx) {
    const s = ctx.state;
    if (s._scroll) el.querySelectorAll('[data-keep]').forEach((n) => { const v = s._scroll[n.getAttribute('data-keep')]; if (v) n.scrollTop = v; });
    if (s._autofocus) {
      s._autofocus = false;
      const f = el.querySelector('[data-ov] [data-autofocus]') || el.querySelector('[data-ov] .nj-c-sheet-body button, [data-ov] .nj-c-dialog button, [data-ov] input');
      if (f) f.focus({ preventScroll: true });
    } else if (s._focus) {
      const f = el.querySelector('[data-fk="' + (window.CSS && CSS.escape ? CSS.escape(s._focus) : s._focus) + '"]');
      if (f) f.focus({ preventScroll: true });
    }
    s._focus = null;
  }
  /* FLIP: guarda posiciones antes de re-dibujar y anima el desplazamiento después (solo movimiento completo). */
  function flipSave(ctx, root) {
    const s = ctx.state; s._flip = {};
    root.querySelectorAll('[data-flip]').forEach((n) => { s._flip[n.getAttribute('data-flip')] = n.getBoundingClientRect().top; });
  }
  function flipPlay(el, ctx) {
    const s = ctx.state; const prev = s._flip; s._flip = null;
    if (!prev || !canMove(ctx)) return;
    const scale = el.getBoundingClientRect().width / (el.offsetWidth || 1) || 1;
    el.querySelectorAll('[data-flip]').forEach((n) => {
      const was = prev[n.getAttribute('data-flip')];
      if (was == null) return;
      const dy = (was - n.getBoundingClientRect().top) / scale;
      if (Math.abs(dy) < 1) return;
      n.animate([{ transform: `translateY(${dy}px)` }, { transform: 'translateY(0)' }], { duration: 260, easing: 'cubic-bezier(.05,.7,.1,1)' });
    });
  }

  /* Controles envueltos para que la pantalla maneje su estado (el runtime no los toca dentro de [data-local]). */
  const seg = (key, label, opts, sel) => `<div class="nj-c-segw" data-c="seg" data-key="${esc(key)}">${h.seg(label, opts, sel)}</div>`;
  const chips = (key, label, opts, sel, multi) => `<div class="nj-c-chipw" data-c="chip" data-key="${esc(key)}"${multi ? ' data-multi' : ''}>${h.chips(label, opts, sel, { multi })}</div>`;
  const tog = (ctx, key, label, on, desc) => h.toggle(pid(ctx, key), label, on, { desc });
  const sld = (ctx, key, label, o) => h.slider(pid(ctx, key), label, o);
  const btn = (label, c, o = {}) => h.btn(label, { kind: o.kind, icon: o.icon, attrs: Object.assign({ 'data-c': c }, o.attrs || {}) });
  const ibtn = (icon, label, c, o = {}) => h.iconBtn(icon, label, { kind: o.kind, fill: o.fill, attrs: Object.assign({ 'data-c': c }, o.attrs || {}) });
  const pill = (text, tone, icon) => `<span class="nj-badge nj-c-pill" data-tone="${tone || 'neutral'}">${icon ? ic(icon, { size: 15 }) : ''}${esc(text)}</span>`;
  const check = (on, label, c, arg) => `<button type="button" class="nj-c-check" role="checkbox" aria-checked="${!!on}" aria-label="${esc(label)}" data-c="${c}" data-arg="${esc(arg)}">${ic('check', { size: 16 })}</button>`;
  function stepper(key, label, val, o = {}) {
    const shown = o.fmt ? o.fmt(val) : val + (o.unit || '');
    return `<div class="nj-c-stepper" role="group" aria-label="${esc(label)}"><span class="nj-c-stepper-label">${esc(label)}</span><div class="nj-c-stepper-ctl">
      <button type="button" class="nj-iconbtn" data-c="step" data-key="${esc(key)}" data-d="-1" aria-label="Bajar ${esc(label)}"${o.min != null && val <= o.min ? ' disabled' : ''}>${ic('remove')}</button>
      <output aria-live="polite">${esc(shown)}</output>
      <button type="button" class="nj-iconbtn" data-c="step" data-key="${esc(key)}" data-d="1" aria-label="Subir ${esc(label)}"${o.max != null && val >= o.max ? ' disabled' : ''}>${ic('add')}</button></div></div>`;
  }
  function menuHTML(items) {
    return `<div class="nj-menu nj-c-menu" role="menu">${items.map((it) => it === '-' ? '<hr class="nj-divider" role="separator">' :
      `<button type="button" role="menuitem" class="nj-menu-item${it.danger ? ' is-danger' : ''}" data-c="${esc(it.c)}"${it.arg != null ? ` data-arg="${esc(it.arg)}"` : ''}>${ic(it.icon || 'chevron_right', { size: 20 })}<span>${esc(it.label)}</span>${it.kbd ? `<span class="nj-kbd">${esc(it.kbd)}</span>` : ''}</button>`).join('')}</div>`;
  }
  function sheetHTML(ctx, o) {
    const side = !A(ctx) && o.side !== false;
    const tid = pid(ctx, 'ov-' + o.id);
    return `<div class="nj-c-ov ${side ? 'is-side' : 'is-bottom'}" data-ov="${esc(o.id)}">
      <button type="button" class="nj-c-scrim" data-c="close" aria-label="Cerrar" tabindex="-1"></button>
      <section class="nj-sheet${side ? ' is-side' : ''} nj-c-sheet${o.wide ? ' is-wide' : ''}${ctx.state._autofocus ? ' is-opening' : ''}" role="dialog" aria-modal="true" aria-labelledby="${tid}" data-region="sheet">
        <header class="nj-c-sheet-head"><div class="nj-c-grow"><h2 class="nj-h2" id="${tid}">${esc(o.title)}</h2>${o.sub ? `<p class="nj-meta">${o.sub}</p>` : ''}</div>${ibtn('close', 'Cerrar', 'close')}</header>
        <div class="nj-c-sheet-body" data-keep="ov-${esc(o.id)}">${o.body}</div>
        ${o.foot ? `<footer class="nj-c-sheet-foot">${o.foot}</footer>` : ''}
      </section></div>`;
  }
  function dialogHTML(ctx, o) {
    const tid = pid(ctx, 'dlg-' + o.id);
    return `<div class="nj-c-ov is-center" data-ov="${esc(o.id)}"><button type="button" class="nj-c-scrim" data-c="close" aria-label="Cerrar" tabindex="-1"></button>
      <section class="nj-dialog nj-c-dialog" role="alertdialog" aria-modal="true" aria-labelledby="${tid}">${o.icon ? `<span class="nj-c-dialog-ic" data-tone="${o.tone || 'neutral'}">${ic(o.icon)}</span>` : ''}<h2 class="nj-h2" id="${tid}">${esc(o.title)}</h2><div class="nj-c-dialog-body">${o.body}</div><div class="nj-c-dialog-actions">${o.actions}</div></section></div>`;
  }
  function snackHTML(s) {
    if (!s.snack) return '';
    return `<div class="nj-c-snackwrap" role="status" aria-live="polite"><div class="nj-snackbar"><span class="nj-c-grow">${esc(s.snack.text)}</span>${s.snack.act ? btn(s.snack.act, s.snack.c || 'snackAct', { kind: 'text' }) : ''}${ibtn('close', 'Cerrar aviso', 'snackClose')}</div></div>`;
  }
  function snack(ctx, text, act, c, arg) { ctx.state.snack = { text, act, c, arg, id: Date.now() }; }
  function empty(icon, title, text, action) {
    return `<div class="nj-c-empty" data-region="empty">${ic(icon, { size: 32 })}<p class="nj-h3">${esc(title)}</p><p class="nj-meta">${esc(text)}</p>${action || ''}</div>`;
  }
  function openOv(ctx, id, extra) { const s = ctx.state; s.ov = id; s.ovArg = extra == null ? null : extra; s.menu = null; s._autofocus = true; }

  /* Delegación de eventos por pantalla. H: { acción(t, e) }, H.$toggle(key, on), H.$seg(key, v), H.$chip(key, v), H.$live(key, v), H.$key(e) */
  function wire(el, ctx, H) {
    const s = ctx.state;
    const re = () => rr(ctx, el);
    const COMMON = {
      close() { s.ov = null; s.ovArg = null; re(); },
      snackClose() { s.snack = null; re(); },
      snackAct(t) { if (H.$undo) H.$undo(s.snack); s.snack = null; re(); },
      menu(t) { const a = t.getAttribute('data-arg'); s.menu = s.menu === a ? null : a; re(); },
      open(t) { openOv(ctx, t.getAttribute('data-arg')); re(); },
      go(t) { const [scr, st] = (t.getAttribute('data-arg') || '').split(':'); if (NJ.screens[scr]) NJ.go(scr, st ? { structure: st } : {}); },
      seg(t, e) {
        const r = e.target.closest('[role="radio"]'); if (!r) return;
        const k = t.getAttribute('data-key'); const v = r.getAttribute('data-value');
        if (H.$seg && H.$seg(k, v) === false) return;
        s[k] = v; re();
      },
      chip(t, e) {
        const c = e.target.closest('.nj-chip'); if (!c) return;
        const k = t.getAttribute('data-key'); const v = c.getAttribute('data-value');
        if (H.$chip && H.$chip(k, v) === false) return;
        if (t.hasAttribute('data-multi')) { const arr = [].concat(s[k] || []); const i = arr.indexOf(v); if (i >= 0) arr.splice(i, 1); else arr.push(v); s[k] = arr; } else s[k] = s[k] === v && t.hasAttribute('data-optional') ? null : v;
        re();
      },
      step(t) {
        const k = t.getAttribute('data-key'); const d = Number(t.getAttribute('data-d'));
        const lim = (H.$steps && H.$steps[k]) || { step: 1 };
        let v = Number(s[k] || 0) + d * (lim.step || 1);
        if (lim.min != null) v = Math.max(lim.min, v); if (lim.max != null) v = Math.min(lim.max, v);
        if (lim.wrap) v = ((v % lim.wrap) + lim.wrap) % lim.wrap;
        s[k] = v; if (H.$stepped) H.$stepped(k, v); re();
      },
    };
    el.addEventListener('click', (e) => {
      if (s.menu && !e.target.closest('.nj-c-menu, .nj-c-pop, [data-c="menu"]')) { s.menu = null; if (!e.target.closest('[data-c]')) { re(); return; } }
      const t = e.target.closest('[data-c]');
      if (!t || !el.contains(t) || t.disabled) return;
      const k = t.getAttribute('data-c');
      const fn = H[k] || COMMON[k];
      if (fn) fn(t, e);
    });
    el.addEventListener('change', (e) => {
      const n = e.target;
      if (n.type === 'checkbox' && n.id) { const k = n.id.slice(2); s[k] = n.checked; if (H.$toggle) H.$toggle(k, n.checked); }
      if (n.type === 'range' && n.id) { const k = n.id.slice(2); s[k] = Number(n.value); if (H.$range) H.$range(k, Number(n.value)); }
    });
    el.addEventListener('input', (e) => { const n = e.target; if (n.type === 'range' && n.id && H.$live) H.$live(n.id.slice(2), Number(n.value), n); if (n.matches('input[data-model]')) { s[n.getAttribute('data-model')] = n.value; if (H.$text) H.$text(n.getAttribute('data-model'), n.value, n); } });
    el.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        if (s.menu) { s.menu = null; re(); e.stopPropagation(); return; }
        if (s.ov) { s.ov = null; re(); e.stopPropagation(); return; }
      }
      if (H.$key) H.$key(e);
    });
    if (s.snack && !s.snack.armed) {
      s.snack.armed = true; const id = s.snack.id;
      setTimeout(() => { if (el.isConnected && ctx.state.snack && ctx.state.snack.id === id) { ctx.state.snack = null; rr(ctx, el); } }, 7000);
    }
    restore(el, ctx);
  }

  /* Pulsación larga (Android): entra en modo selección. */
  function longPress(el, sel, fn) {
    let t = null; let fired = false;
    el.addEventListener('pointerdown', (e) => { const n = e.target.closest(sel); if (!n || e.button > 0) return; fired = false; t = setTimeout(() => { fired = true; fn(n); }, 480); });
    ['pointerup', 'pointerleave', 'pointercancel', 'pointermove'].forEach((ev) => el.addEventListener(ev, (e) => { if (ev === 'pointermove' && Math.abs(e.movementY) + Math.abs(e.movementX) < 4) return; clearTimeout(t); }));
    el.addEventListener('click', (e) => { if (fired) { e.stopPropagation(); e.preventDefault(); fired = false; } }, true);
  }

  /* Encabezado de pantalla según plataforma. */
  function pageHead(ctx, o) {
    if (A(ctx)) {
      return `<header class="nj-appbar nj-c-appbar">${o.back ? ibtn('arrow_back', 'Atrás', o.back) : ''}<h1 class="nj-h2 nj-c-grow">${esc(o.title)}</h1>${o.actionsA || ''}</header>`;
    }
    return `<header class="nj-c-pagehead"><div class="nj-c-grow"><h1 class="nj-c-title">${esc(o.title)}</h1>${o.meta ? `<p class="nj-meta">${o.meta}</p>` : ''}</div><div class="nj-cluster nj-c-headactions">${o.actions || ''}</div></header>`;
  }

  /* =====================================================================
     1. TRANSFERENCIAS: descargas web, torrents, debrid y biblioteca local juntos
     ===================================================================== */
  const t1 = (D.transfers && D.transfers[0]) || { name: 'Grand Blue Dreaming S02 (E01-E06) 1080p', pct: 0.37, down: '8,9 MB/s', up: '210 kB/s', eta: '6 min', peers: 7 };
  const TX_QUEUE = [
    { id: 'q1', tid: 'aonohako', ep: 9, kind: 'torrent', state: 'downloading', pct: 0.62, size: '1,2 GB', down: '8,9 MB/s', eta: '1 min', src: 'Kitsune Raws', keep: true },
    { id: 'q2', tid: 'koori', ep: 9, kind: 'debrid', state: 'downloading', pct: 0.31, size: '1,1 GB', down: '22 MB/s', eta: '40 s', src: 'Hanabi Subs', keep: true },
    { id: 't1', tid: null, name: t1.name, files: 6, kind: 'torrent', state: 'downloading', pct: t1.pct, size: '40,3 GB', down: t1.down, eta: '46 min', src: 'Lote · 6 episodios', keep: true },
    { id: 'q3', tid: 'tempal', ep: 7, kind: 'web', state: 'queued', pct: 0, size: '412 MB', src: 'Nebula' },
    { id: 'q4', tid: 'psyren', ep: 3, kind: 'torrent', state: 'paused', pct: 0.08, size: '780 MB', src: 'Akai Raws', keep: false },
    { id: 'q5', tid: 'chitose', ep: 8, kind: 'web', state: 'failed', pct: 0.44, size: '350 MB', src: 'Mirror B', err: 'Servicio debrid: alcanzaste el límite de descargas activas.' },
  ];
  const TX_LIB_EXTRA = [
    { id: 't5', tid: 'kikansha', files: 12, kind: 'torrent', state: 'seeding', pct: 1, size: '14,6 GB', src: 'Lote · 12 episodios', keep: true, added: 'hace 3 d' },
    { id: 't6', tid: 'revo', ep: 12, kind: 'torrent', state: 'error', pct: 0.9, size: '1,3 GB', src: 'Yoru Fansub', err: 'No hay espacio libre suficiente: se necesitan 140 MB.', added: 'hace 1 d' },
  ];
  const TX_DONE = [
    { tid: 'aonohako', eps: [{ n: 6, src: 'Kitsune Raws · torrent', size: '1,1 GB' }, { n: 7, src: 'Kitsune Raws · torrent compartiendo', size: '1,1 GB' }, { n: 8, src: 'Nebula · web', size: '412 MB' }] },
    { tid: 'laidoff', eps: [{ n: 1, src: 'Nebula · web', size: '398 MB' }, { n: 2, src: 'Nebula · web', size: '0 B', broken: true }] },
    { tid: 'kikansha', eps: [{ n: '1 a 12', src: 'Torrent completo · compartiendo', size: '14,6 GB', batch: true }] },
  ];
  const TX_MANGA = [
    { mid: 'm1', ch: [{ n: 178, size: '18 MB' }, { n: 179, size: '21 MB' }, { n: 180, size: '…', queued: true }] },
    { mid: 'm5', ch: [{ n: 80, size: '0 B', failed: true }] },
  ];
  const TX_LOCAL = [
    { id: 'l1', file: 'Mahoutsukai no Yoru (2024) [BD 1080p].mkv', tid: 'mahoyo', ep: 1, size: '6,2 GB' },
    { id: 'l2', file: '[Grupo] Kanojo no Tomodachi - 03.mkv', tid: null, ep: 3, size: '640 MB' },
    { id: 'l3', file: 'grabacion_tv_0412.ts', tid: null, ep: null, size: '2,8 GB' },
  ];
  const TX_RULES = [
    { tid: 'aonohako', last: 8, on: true, profile: 'bal' },
    { tid: 'magical', last: 9, on: true, profile: 'best' },
    { tid: 'psyren', last: 2, on: false, profile: 'save' },
  ];
  const TX_HIST = [
    { when: 'Hoy', name: 'Ao no Hako S2 · Ep. 8', tid: 'aonohako', origin: 'Reproducido', kind: 'torrent', meta: 'hace 2 h · 1,1 GB' },
    { when: 'Hoy', name: 'Koori no Jouheki · Ep. 8', tid: 'koori', origin: 'Descargado', kind: 'web', meta: 'hace 5 h · 980 MB' },
    { when: 'Ayer', name: 'Revo Barai · Ep. 11', tid: 'revo', origin: 'Magnet', kind: 'torrent', meta: 'ayer 23:10 · 1,3 GB', removed: true },
    { when: 'Ayer', name: 'Chitose-kun 2 · Ep. 7', tid: 'chitose', origin: 'Falló', kind: 'web', meta: 'ayer 18:02 · Servidor DASH, no se puede descargar', failed: true },
    { when: 'Esta semana', name: 'Kikansha no Mahou 2 · Lote', tid: 'kikansha', origin: 'Añadido', kind: 'torrent', meta: 'lun 21:40 · 14,6 GB' },
  ];
  const TX_FILES = [
    { path: 'Grand Blue S02', name: '[Grupo] Grand Blue S02 - 01 [1080p].mkv', ep: 1, size: '6,4 GB', pct: 1, how: 'name' },
    { path: 'Grand Blue S02', name: '[Grupo] Grand Blue S02 - 02 [1080p].mkv', ep: 2, size: '6,6 GB', pct: 0.8, how: 'name' },
    { path: 'Grand Blue S02', name: '[Grupo] Grand Blue S02 - 03 [1080p].mkv', ep: 3, size: '6,5 GB', pct: 0.35, how: 'name', stream: true },
    { path: 'Grand Blue S02', name: '[Grupo] Grand Blue S02 - 04 [1080p].mkv', ep: 4, size: '6,7 GB', pct: 0, how: 'name' },
    { path: 'Grand Blue S02', name: '[Grupo] Grand Blue S02 - 05 [1080p].mkv', ep: 5, size: '6,5 GB', pct: 0, how: 'name' },
    { path: 'Grand Blue S02', name: '[Grupo] Grand Blue S02 - 06 [1080p].mkv', ep: 6, size: '6,6 GB', pct: 0, how: 'name' },
    { path: 'Grand Blue S02/Extras', name: 'Opening sin créditos.mkv', ep: null, size: '310 MB', pct: 0, how: 'order', skip: true },
    { path: 'Grand Blue S02/Extras', name: 'Ending sin créditos.mkv', ep: null, size: '290 MB', pct: 0, how: 'order', skip: true },
  ];
  const TX_PEERS = [
    { ip: '203.0.113.31', client: 'qBittorrent 5.0', cc: 'DE', down: 2.1, up: 0, pct: 1, flags: 'DIEH' },
    { ip: '198.51.100.7', client: 'Transmission 4.0', cc: 'FR', down: 1.8, up: 0.02, pct: 1, flags: 'DUPXE' },
    { ip: '192.0.2.144', client: 'libtorrent 2.0', cc: 'JP', down: 1.2, up: 0.11, pct: 0.71, flags: 'DUH' },
    { ip: '203.0.113.90', client: 'Deluge 2.1', cc: 'BR', down: 0.9, up: 0.04, pct: 0.66, flags: 'DIX' },
    { ip: '198.51.100.62', client: 'Cliente desconocido', cc: 'US', down: 0.4, up: 0, pct: 0.12, flags: 'DHE' },
    { ip: '192.0.2.18', client: 'qBittorrent 4.6', cc: 'MX', down: 0, up: 0.03, pct: 0.05, flags: 'UPS' },
  ];
  const TX_TRACKERS = [
    { url: 'udp://tracker.example.org:1337/announce', s: 5, l: 2, d: 318, msg: '', st: 'ok' },
    { url: 'udp://open.example.net:6969/announce', s: 4, l: 1, d: 201, msg: '', st: 'ok' },
    { url: 'https://tracker.example.com/announce', s: null, l: null, d: null, msg: 'Actualizando…', st: 'upd' },
    { url: 'udp://old.example.edu:80/announce', s: null, l: null, d: null, msg: 'Tiempo de espera agotado al conectar', st: 'fail' },
  ];
  const FLAG = { D: ['download', 'Bajando de este par'], U: ['upload', 'Subiendo a este par'], I: ['call_received', 'Conexión entrante'], P: ['swap_horiz', 'Conexión uTP'], E: ['lock', 'Conexión cifrada'], X: ['group', 'Encontrado por PEX'], H: ['hub', 'Encontrado por DHT'], S: ['block', 'Bloqueado: no envía nada'] };
  const TX_STATE = { downloading: ['Bajando', 'ok'], queued: ['En cola', 'neutral'], paused: ['En pausa', 'neutral'], failed: ['Falló', 'err'], seeding: ['Compartiendo', 'ok'], error: ['Error', 'err'], starting: ['Iniciando', 'accent'] };
  const TX_KIND = { torrent: ['Torrent', 'hub'], web: ['Web', 'language'], debrid: ['TorBox en caché', 'cloud_done'] };
  const PROFILES = [{ id: 'best', label: 'Mejor calidad' }, { id: 'bal', label: 'Equilibrada ≤1080p' }, { id: 'save', label: 'Ahorro ≤720p' }];

  const txTitle = (it) => (it.tid ? `${T(it.tid).short}${it.ep ? ' · Ep. ' + it.ep : ''}` : it.name);
  const thumb = (tid, w) => (tid && T(tid) ? `<img class="nj-c-thumb" src="${esc(T(tid).poster)}" alt="" width="${w || 40}" height="${Math.round((w || 40) * 1.5)}" loading="lazy" decoding="async">` : `<span class="nj-c-thumb is-empty" aria-hidden="true">${ic('movie', { size: 20 })}</span>`);

  function txAll(s) { return s.queue.concat(s.lib.filter((x) => !s.queue.some((q) => q.id === x.id))); }

  function txRow(ctx, s, it, i, o = {}) {
    const st = TX_STATE[it.state] || ['', 'neutral']; const kd = TX_KIND[it.kind];
    const sel = s.selMode && s.sel.includes(it.id);
    const line = it.state === 'downloading' ? `${it.down} · quedan ${it.eta}` : it.state === 'failed' || it.state === 'error' ? '' : it.state === 'queued' ? 'Espera su turno' : it.state === 'seeding' ? 'Completo' : `${pct(it.pct)} de ${it.size}`;
    const isWeb = it.kind === 'web';
    const pause = it.state === 'paused' || it.state === 'failed' || it.state === 'error' ? (it.state !== 'paused' ? ibtn('refresh', 'Reintentar', 'txResume', { attrs: { 'data-arg': it.id } }) : ibtn('play_arrow', 'Reanudar', 'txResume', { attrs: { 'data-arg': it.id } })) : it.state === 'seeding' ? '' : ibtn('pause', 'Pausar', 'txPause', { attrs: { 'data-arg': it.id } });
    const menu = s.menu === 'row:' + it.id ? menuHTML([
      { label: 'Abrir en el cliente', icon: 'open_in_new', c: 'txOpen', arg: it.id },
      it.tid ? { label: 'Reproducir', icon: 'play_circle', c: 'go', arg: 'ficha' } : null,
      '-',
      { label: 'Mover al principio', icon: 'vertical_align_top', c: 'txTop', arg: it.id, kbd: 'Alt ↑' },
      { label: 'Mover al final', icon: 'vertical_align_bottom', c: 'txBottom', arg: it.id, kbd: 'Alt ↓' },
      !isWeb ? { label: it.keep ? 'No conservar después de ver' : 'Conservar después de ver', icon: it.keep ? 'bookmark_remove' : 'bookmark_add', c: 'txKeep', arg: it.id } : null,
      !A(ctx) ? { label: 'Abrir carpeta', icon: 'folder_open', c: 'txFolder', arg: it.id } : null,
      !isWeb ? (A(ctx) ? { label: 'Compartir enlace magnet', icon: 'share', c: 'txMagnet', arg: it.id } : { label: 'Copiar enlace magnet', icon: 'link', c: 'txMagnet', arg: it.id }) : null,
      '-',
      { label: isWeb ? 'Cancelar descarga…' : 'Borrar…', icon: 'delete', c: 'txDel', arg: it.id, danger: true },
    ].filter(Boolean)) : '';
    return `<li class="nj-c-qrow${sel ? ' is-sel' : ''}${it.state === 'failed' || it.state === 'error' ? ' is-err' : ''}" data-id="${it.id}" data-flip="${it.id}" data-fk="q-${it.id}" tabindex="0"${o.drag ? ' draggable="true"' : ''} aria-label="${esc(txTitle(it))}, ${esc(st[0])}${it.pct ? ', ' + pct(it.pct) : ''}"${s.selMode ? ` aria-selected="${sel}"` : ''}>
      ${s.selMode ? check(sel, 'Seleccionar ' + txTitle(it), 'txSel', it.id) : o.drag ? `<span class="nj-c-grip" title="Arrastra o usa Alt+↑/↓">${ic('drag_indicator', { size: 20 })}</span>` : ''}
      ${thumb(it.tid, 36)}
      <div class="nj-c-grow nj-c-qmain"><div class="nj-c-qtitle">${esc(txTitle(it))}</div>
        <div class="nj-meta nj-c-qmeta">${ic(kd[1], { size: 15 })}<span>${esc(kd[0])} · ${esc(it.src || '')} · ${esc(it.size)}</span>${it.kind !== 'web' && it.keep === false ? '<span>· Se borra al verlo</span>' : ''}</div>
        ${it.err ? `<p class="nj-c-err">${ic('error', { size: 16 })}${esc(it.err)}</p>` : h.progress(it.pct, { label: 'Progreso de ' + txTitle(it) })}
      </div>
      <div class="nj-c-qstat">${pill(st[0], st[1])}<span class="nj-c-num">${esc(line)}</span></div>
      <div class="nj-c-qact nj-c-menuwrap">${s.selMode ? '' : pause + ibtn('more_vert', 'Más acciones', 'menu', { attrs: { 'data-arg': 'row:' + it.id } })}${menu}</div>
    </li>`;
  }

  function txQueue(ctx, s) {
    const q = (s.libQ || '').toLowerCase();
    if (q && ctx.structure !== 'detalle') {
      const hits = s.queue.filter((x) => txTitle(x).toLowerCase().includes(q));
      return `<div class="nj-c-banner" data-tone="info">${ic('filter_alt')}<div class="nj-c-grow"><b>${hits.length} de ${s.queue.length} coinciden con «${esc(s.libQ)}»</b><p class="nj-meta">Para reordenar, borra la búsqueda.</p></div>${btn('Borrar búsqueda', 'libAll', { kind: 'text' })}</div>${hits.length ? `<ol class="nj-c-queue" aria-label="Cola filtrada">${hits.map((it, i) => txRow(ctx, s, it, i, { drag: false })).join('')}</ol>` : ''}`;
    }
    if (!s.queue.length) return empty('download_done', 'Nada en curso', 'Elige Descargar en la ficha de un episodio o en sus fuentes, o añade un torrent.', btn('Añadir torrent', 'open', { kind: 'tonal', icon: 'add', attrs: { 'data-arg': 'add' } }));
    return `<ol class="nj-c-queue" aria-label="Cola de transferencias" data-queue>${s.queue.map((it, i) => txRow(ctx, s, it, i, { drag: !s.selMode })).join('')}<li class="nj-c-insert" aria-hidden="true"></li></ol>`;
  }

  function txBg(ctx, s) {
    if (!A(ctx)) return '';
    if (!s.bgAck) {
      return `<section class="nj-c-panel nj-c-warn" aria-labelledby="${pid(ctx, 'bgw')}"><div class="nj-c-warn-head">${ic('battery_alert')}<h2 class="nj-h3" id="${pid(ctx, 'bgw')}">Las descargas en segundo plano usan tu teléfono</h2></div>
        <p class="nj-meta">Los torrents guardados siguen bajando y compartiendo aunque no estés viendo nada. Eso implica:</p>
        <ul class="nj-c-bullets"><li>Uso de CPU y batería, también con la pantalla apagada.</li><li>Espacio y escrituras en la carpeta de descargas.</li><li>Tráfico de red, que cuenta en datos móviles o con tope.</li><li>Subir a otros pares: tu dirección IP queda visible para ellos.</li><li>Android muestra una notificación fija mientras haya transferencias.</li></ul>
        <p class="nj-meta">Con «Solo Wi-Fi» activado no se baja nada en segundo plano con datos móviles. Puedes desactivarlo cuando quieras.</p>
        <div class="nj-cluster">${btn('Entendido, activar', 'bgAck', { kind: 'filled' })}${btn('Ahora no', 'bgLater', { kind: 'text' })}</div></section>`;
    }
    return `<div class="nj-c-statusrow" role="group" aria-label="Estado de las descargas">${pill('Segundo plano activo', 'ok', 'cloud_sync')}${pill('Esperando Wi-Fi para 1', 'neutral', 'wifi_off')}${btn('Pausado por el sistema · Reanudar', 'txResumeAll', { kind: 'outline', icon: 'play_arrow' })}</div>`;
  }

  function txDebridLine() {
    return `<div class="nj-c-debridline">${ic('cloud_done', { size: 18 })}<span><b>TorBox</b> vinculado · 2 de la cola están en caché y bajan por HTTPS</span></div>`;
  }

  function txDone(ctx, s) {
    const media = s.media;
    const tabs = chips('media', 'Tipo de contenido', [{ id: 'anime', label: 'Anime', count: 3 }, { id: 'manga', label: 'Manga', count: 2 }, { id: 'novela', label: 'Novelas', count: 0 }], media);
    let body = '';
    if (media === 'anime') {
      body = s.done.map((g) => {
        const t = T(g.tid);
        const eps = g.eps.map((e) => `<li class="nj-c-eprow${e.broken ? ' is-err' : ''}" data-fk="e-${g.tid}-${e.n}"><span class="nj-c-epn">${e.batch ? 'Ep. ' + e.n : h.ep(e.n)}</span><span class="nj-meta nj-c-grow">${esc(e.src)} · ${esc(e.size)}</span>${e.broken ? pill('Archivo dañado', 'err', 'broken_image') + btn('Descargar de nuevo', 'txRedl', { kind: 'text', icon: 'refresh', attrs: { 'data-arg': g.tid + ':' + e.n } }) : ibtn('play_arrow', 'Reproducir sin conexión', 'go', { attrs: { 'data-arg': 'reproductor' } })}${!A(ctx) ? ibtn('folder_open', 'Abrir carpeta', 'txFolder') : ''}${ibtn('delete', 'Borrar episodio', 'txDelEp', { attrs: { 'data-arg': g.tid + ':' + e.n } })}</li>`).join('');
        return `<section class="nj-c-group" aria-label="${esc(t.short)}"><div class="nj-c-grouphead">${h.card(t, { context: 'compact', size: 's' })}<span class="nj-meta nj-c-nowrap">${g.eps.length === 1 && g.eps[0].batch ? '12 episodios' : g.eps.length + ' episodios'}</span>${btn('Borrar todos', 'txDelGroup', { kind: 'text', attrs: { 'data-arg': g.tid } })}</div><ul class="nj-c-eplist">${eps}</ul></section>`;
      }).join('') || empty('video_library', 'Sin episodios descargados', 'Los episodios descargados se reproducen aquí sin conexión.', '');
    } else if (media === 'manga') {
      body = TX_MANGA.map((g) => {
        const m = D.manga.find((x) => x.id === g.mid);
        return `<section class="nj-c-group"><div class="nj-c-grouphead"><img class="nj-c-thumb" src="${esc(m.poster)}" alt="" width="36" height="54"><span class="nj-h3 nj-c-grow">${esc(m.title)}</span>${btn('Abrir ficha', 'go', { kind: 'text', attrs: { 'data-arg': 'manga:ficha' } })}${btn('Borrar todos', 'txDelGroup', { kind: 'text', attrs: { 'data-arg': g.mid } })}</div><ul class="nj-c-eplist">${g.ch.map((c) => `<li class="nj-c-eprow${c.failed ? ' is-err' : ''}"><span class="nj-c-epn">Cap. ${c.n}</span><span class="nj-meta nj-c-grow">${c.queued ? 'En cola' : c.failed ? 'Falló la descarga' : 'Descargado · ' + c.size}</span>${c.failed ? btn('Reintentar', 'txRedl', { kind: 'text', icon: 'refresh' }) : c.queued ? ibtn('pause', 'Pausar capítulo', 'txPause') : ibtn('menu_book', 'Leer sin conexión', 'go', { attrs: { 'data-arg': 'lector:paginado' } })}${ibtn('delete', 'Borrar capítulo', 'txDelEp', { attrs: { 'data-arg': g.mid + ':' + c.n } })}</li>`).join('')}</ul></section>`;
      }).join('');
    } else {
      body = empty('auto_stories', 'No hay capítulos de novelas descargados', 'Descarga capítulos desde la ficha de una novela o desde el lector.', btn('Ir a Novelas', 'go', { kind: 'tonal', attrs: { 'data-arg': 'manga:catalogo' } }));
    }
    return `<div class="nj-c-sectionhead"><h2 class="nj-h2">Descargado</h2>${tabs}</div>${body}`;
  }

  function txRules(ctx, s) {
    const sug = ['tempal', 'chitose', 'koori', 'horegusuri'].filter((id) => !s.rules.some((r) => r.tid === id));
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'auto')}"><div class="nj-c-panelhead"><h2 class="nj-h3" id="${pid(ctx, 'auto')}">Auto-descarga</h2>${btn(s.checking ? 'Revisando…' : 'Revisar ahora', 'autoCheck', { kind: 'tonal', icon: 'refresh' })}</div>
      <p class="nj-meta">${A(ctx) ? 'Los episodios nuevos de estas series entran solos a la cola, cada 30 min con Wi-Fi o con Revisar ahora.' : 'Los episodios nuevos de estas series entran solos a la cola al abrir la app y cada 30 min.'}</p>
      ${s.autoResult ? `<p class="nj-c-result" role="status">${ic('task_alt', { size: 18 })}${esc(s.autoResult)}</p>` : ''}
      <ul class="nj-c-rules">${s.rules.map((r, i) => `<li class="nj-c-rule${r.on ? '' : ' is-off'}"><img class="nj-c-thumb" src="${esc(T(r.tid).poster)}" alt="" width="32" height="48" loading="lazy"><div class="nj-c-grow"><div class="nj-c-qtitle">${esc(T(r.tid).short)}</div><div class="nj-meta">Último en cola: Ep. ${r.last}</div>
        <div class="nj-c-profile">${chips('rule' + i, 'Calidad de ' + T(r.tid).short, PROFILES, r.profile)}</div>${s.sim === i ? `<p class="nj-c-result" role="status">${ic('science', { size: 16 })}La próxima revisión encolaría Ep. ${r.last + 1}.</p>` : ''}</div>
        <div class="nj-c-rulectl">${h.toggle(pid(ctx, 'rule' + i), 'Activa', r.on)}<div class="nj-cluster">${btn('Simular', 'autoSim', { kind: 'text', attrs: { 'data-arg': i } })}${ibtn('delete', 'Quitar regla', 'autoDel', { attrs: { 'data-arg': i } })}</div></div></li>`).join('')}</ul>
      ${sug.length ? `<div class="nj-c-sug"><span class="nj-meta">De lo que estás viendo</span><div class="nj-cluster">${sug.map((id) => `<button type="button" class="nj-chip" data-c="autoAdd" data-arg="${id}">${ic('add', { size: 18 })}${esc(T(id).short)}</button>`).join('')}</div></div>` : ''}
    </section>`;
  }

  function txLocal(ctx, s) {
    const un = s.local.filter((l) => !l.tid).length;
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'loc')}"><div class="nj-c-panelhead"><h2 class="nj-h3" id="${pid(ctx, 'loc')}">Biblioteca local</h2>${un ? pill(un + ' sin emparejar', 'warn', 'link_off') : ''}</div>
      <div class="nj-cluster">${btn('Importar videos', 'localImport', { kind: 'tonal', icon: 'video_file' })}${btn('Escanear carpeta', 'localScan', { kind: 'outline', icon: 'folder_open' })}</div>
      <ul class="nj-c-locallist">${s.local.map((l) => `<li class="nj-c-localrow" data-fk="l-${l.id}">${thumb(l.tid, 32)}<div class="nj-c-grow"><div class="nj-c-qtitle">${esc(l.tid ? T(l.tid).short : l.file)}</div><div class="nj-meta nj-c-ellipsis">${l.ep ? 'Ep. ' + l.ep + ' · ' : ''}${esc(l.file)} · ${esc(l.size)}</div></div>${l.tid ? '' : pill('Sin emparejar', 'warn')}${ibtn('link', 'Emparejar con un título', 'open', { attrs: { 'data-arg': 'match:' + l.id } })}${ibtn('delete', 'Quitar de la biblioteca', 'localDel', { attrs: { 'data-arg': l.id } })}</li>`).join('')}</ul>
      ${A(ctx) ? '' : `<div class="nj-c-folders"><div class="nj-c-panelhead"><h3 class="nj-c-label">Carpetas vigiladas</h3>${btn('Añadir carpeta', 'folderAdd', { kind: 'text', icon: 'create_new_folder' })}</div><p class="nj-meta">Los videos nuevos de estas carpetas entran solos a la biblioteca.</p><ul class="nj-c-plainlist">${s.folders.map((f, i) => `<li><span class="nj-c-path">${ic('folder', { size: 18 })}${esc(f)}</span>${ibtn('open_in_new', 'Abrir carpeta', 'txFolder')}${ibtn('close', 'Dejar de vigilar ' + f, 'folderDel', { attrs: { 'data-arg': i } })}</li>`).join('')}</ul></div>`}
    </section>`;
  }

  function txSide(ctx, s) {
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'deb')}"><div class="nj-c-panelhead"><h2 class="nj-h3" id="${pid(ctx, 'deb')}">Debrid</h2>${pill('TorBox vinculado', 'ok', 'cloud_done')}</div>
        <p class="nj-meta">Un torrent que TorBox ya tiene en caché se reproduce al instante por HTTPS, sin bajarlo por P2P.</p>
        ${tog(ctx, 'useDebrid', 'Usar debrid', s.useDebrid, 'Apagado: no se consulta la caché y todo va por P2P.')}
        ${tog(ctx, 'preferCached', 'Priorizar fuentes en caché', s.preferCached && s.useDebrid, 'Suben en la lista de fuentes y Automático puede elegirlas.')}
        ${tog(ctx, 'dlCached', 'Descargar por el servicio si está en caché', s.dlCached && s.useDebrid, 'Baja el episodio por HTTPS con las reglas de descargas web.')}
        ${btn('Administrar servicios debrid', 'goDebrid', { kind: 'text', icon: 'tune' })}</section>
      <section class="nj-c-panel" aria-labelledby="${pid(ctx, 'sto')}"><div class="nj-c-panelhead"><h2 class="nj-h3" id="${pid(ctx, 'sto')}">Almacenamiento</h2><span class="nj-c-num">48,2 GB de 360 GB</span></div>
        ${h.progress(0.134, { label: 'Espacio usado' })}<p class="nj-meta">Web 12,1 GB · Torrents 36,1 GB · Libres 311,8 GB</p>
        ${s.checked ? `<p class="nj-c-result" role="status">${ic('task_alt', { size: 18 })}1 archivo dañado: Laid-Off Mage · Ep. 2. Revisado hace un momento.</p>` : ''}
        <div class="nj-cluster">${btn('Revisar archivos', 'txCheck', { kind: 'outline', icon: 'fact_check' })}${A(ctx) ? '' : btn('Abrir carpeta', 'txFolder', { kind: 'text', icon: 'folder_open' })}</div></section>
      ${A(ctx) ? '' : `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'bg')}"><h2 class="nj-h3" id="${pid(ctx, 'bg')}">Segundo plano</h2>${tog(ctx, 'bgOn', 'Seguir descargando con la app abierta', s.bgOn, 'Los torrents guardados bajan y comparten aunque no estés viendo nada. Tu IP es visible para otros pares.')}</section>`}`;
  }

  function txLimitsPop(ctx, s) {
    return `<div class="nj-c-pop" role="dialog" aria-label="Límites rápidos">
      <div class="nj-c-field"><span class="nj-c-label">Bajada</span>${chips('limDown', 'Límite de bajada', [{ id: '0', label: 'Sin límite' }, { id: '1', label: '1 MB/s' }, { id: '5', label: '5 MB/s' }, { id: '10', label: '10 MB/s' }, { id: '25', label: '25 MB/s' }], s.limDown)}</div>
      <div class="nj-c-field"><span class="nj-c-label">Subida</span>${chips('limUp', 'Límite de subida', [{ id: '0', label: 'Sin límite' }, { id: '128', label: '128 kB/s' }, { id: '512', label: '512 kB/s' }, { id: '1024', label: '1 MB/s' }], s.limUp)}</div>
      ${tog(ctx, 'sched', 'Otros límites por horario', s.sched, 'Por ejemplo, 1 MB/s de día y sin límite de noche. Puede cruzar la medianoche.')}
      ${s.sched ? `<div class="nj-c-pair">${stepper('schedFrom', 'Desde', s.schedFrom, { fmt: mm })}${stepper('schedTo', 'Hasta', s.schedTo, { fmt: mm })}</div><div class="nj-c-field"><span class="nj-c-label">Bajada en ese horario</span>${chips('schedDown', 'Bajada en horario', [{ id: '1', label: '1 MB/s' }, { id: '5', label: '5 MB/s' }, { id: '0', label: 'Sin límite' }], s.schedDown)}</div>` : ''}
      ${btn('Más ajustes de torrents', 'go', { kind: 'text', icon: 'settings', attrs: { 'data-arg': 'ajustes' } })}</div>`;
  }

  function txAddSheet(ctx, s) {
    const src = s.addSrc || ''; const hasSrc = !!(src || s.addFile);
    const kind = s.addFile ? 'file' : /^magnet:\?/i.test(src) ? 'magnet' : /^[a-f0-9]{40}$/i.test(src.trim()) ? 'hash' : src ? 'bad' : 'empty';
    const hint = { empty: 'Pega un enlace magnet o un info-hash de 40 caracteres, o elige un archivo .torrent.', magnet: 'Enlace magnet: los metadatos se piden a los pares.', hash: 'Info-hash: los metadatos se piden a los pares.', bad: 'Esto no es un enlace magnet ni un info-hash válido.', file: 'Archivo .torrent: ya se leyeron sus archivos.' }[kind];
    const ok = kind === 'magnet' || kind === 'hash' || kind === 'file';
    const nSel = s.addFiles.filter(Boolean).length;
    const results = s.addQ && s.addQ.length >= 2 ? D.titles.filter((t) => (t.title + ' ' + t.short).toLowerCase().includes(s.addQ.toLowerCase())).slice(0, 5) : [];
    const body = `<div class="nj-c-drop${s.dropHover ? ' is-hover' : ''}" data-dropzone>${ic('upload_file', { size: 28 })}<div><b>${A(ctx) ? 'Elige un archivo .torrent' : 'Suelta un archivo .torrent aquí'}</b><p class="nj-meta">${A(ctx) ? 'O pega un enlace magnet abajo.' : 'También puedes elegirlo o pegar un enlace magnet.'}</p></div><div class="nj-cluster">${btn('Elegir archivo', 'addPick', { kind: 'tonal', icon: 'attach_file' })}${btn('Pegar', 'addPaste', { kind: 'outline', icon: 'content_paste' })}</div></div>
      ${s.addFile ? `<div class="nj-c-filechip">${ic('description', { size: 18 })}<span class="nj-c-grow nj-c-ellipsis">${esc(s.addFile)}</span>${ibtn('close', 'Quitar el archivo', 'addClearFile')}</div>` : `<label class="nj-c-field"><span class="nj-c-label">Enlace magnet o info-hash</span><span class="nj-field"><input type="text" data-model="addSrc" value="${esc(src)}" placeholder="magnet:?xt=urn:btih:…" spellcheck="false" autocomplete="off" data-autofocus aria-describedby="${pid(ctx, 'addhint')}"></span></label>`}
      <p class="nj-meta${kind === 'bad' ? ' nj-c-errtext' : ''}" id="${pid(ctx, 'addhint')}" data-addhint>${esc(hint)}</p>
      ${ok ? `<div class="nj-c-addstatus" role="status">${kind === 'file' ? ic('task_alt', { size: 18 }) + 'Se leyeron 8 archivos del torrent.' : ic('travel_explore', { size: 18 }) + 'Metadatos recibidos de 6 pares en 4 s.'}</div>
      <fieldset class="nj-c-fieldset"><legend class="nj-c-label">Serie (opcional)</legend><p class="nj-meta">Vincula los archivos a un título para que aparezcan como sus episodios.</p>
        ${s.addSeries ? `<div class="nj-c-filechip">${thumb(s.addSeries, 28)}<span class="nj-c-grow">${esc(T(s.addSeries).short)}</span>${ibtn('close', 'Quitar la serie', 'addSeriesClear')}</div><div class="nj-c-pair">${stepper('addFirst', 'Primer episodio', s.addFirst, { min: 1 })}${stepper('addOffset', 'Desfase', s.addOffset, { fmt: (v) => (v > 0 ? '+' + v : String(v)) })}</div>`
          : `<span class="nj-search">${ic('search')}<input type="search" data-model="addQ" value="${esc(s.addQ || '')}" placeholder="Buscar en AniList" aria-label="Buscar serie en AniList"></span>${results.length ? `<div class="nj-c-results">${results.map((t) => `<button type="button" class="nj-c-result-row" data-c="addSeries" data-arg="${t.id}">${thumb(t.id, 28)}<span class="nj-c-grow">${esc(t.short)}</span><span class="nj-meta">${t.year}</span></button>`).join('')}</div>` : s.addQ && s.addQ.length >= 2 ? '<p class="nj-meta">Ningún título coincide.</p>' : `<div class="nj-cluster">${['laidoff', 'aonohako', 'psyren'].map((id) => `<button type="button" class="nj-chip" data-c="addSeries" data-arg="${id}">${esc(T(id).short)}</button>`).join('')}</div>`}`}
      </fieldset>
      <fieldset class="nj-c-fieldset"><legend class="nj-c-label">Archivos · ${nSel} de ${TX_FILES.length}</legend><div class="nj-cluster">${btn('Todos', 'addAll', { kind: 'text' })}${btn('Ninguno', 'addNone', { kind: 'text' })}${btn('Solo videos de episodios', 'addVideos', { kind: 'text' })}</div>
        <ul class="nj-c-filelist">${TX_FILES.map((f, i) => `<li>${check(s.addFiles[i], f.name, 'addFile', i)}<span class="nj-c-grow nj-c-ellipsis" title="${esc(f.path + '/' + f.name)}">${esc(f.name)}</span>${f.ep ? pill('Ep. ' + (s.addSeries ? f.ep + s.addOffset + s.addFirst - 1 : f.ep), 'ok') : pill('Sin episodio', 'neutral')}<span class="nj-c-num">${esc(f.size)}</span></li>`).join('')}</ul>
        ${nSel ? '<p class="nj-meta">Solo bajan los archivos marcados.</p>' : '<p class="nj-c-errtext">Elige al menos un archivo.</p>'}</fieldset>
      ${tog(ctx, 'addKeep', 'Conservar y descargar todo', s.addKeep, 'Apagado: se comporta como un torrent en streaming y se borra después de verlo.')}` : ''}`;
    const foot = `${btn('Cancelar', 'close', { kind: 'text' })}<button type="button" class="nj-btn is-filled" data-c="addGo"${ok && nSel ? '' : ' disabled'}>${ic('add')}Añadir</button>`;
    return sheetHTML(ctx, { id: 'add', title: 'Añadir torrent', body, foot });
  }

  function txHistSheet(ctx, s) {
    const f = s.histF;
    const rows = TX_HIST.filter((r) => f === 'todo' || (f === 'web' ? r.kind === 'web' : r.kind === 'torrent'));
    const groups = {}; rows.forEach((r) => (groups[r.when] = groups[r.when] || []).push(r));
    const body = `${chips('histF', 'Filtrar historial', [{ id: 'todo', label: 'Todo' }, { id: 'torrent', label: 'Torrents' }, { id: 'web', label: 'Web' }], f)}
      ${Object.keys(groups).length ? Object.entries(groups).map(([g, xs]) => `<h3 class="nj-c-label">${esc(g)}</h3><ul class="nj-c-plainlist">${xs.map((r) => `<li class="nj-c-histrow">${thumb(r.tid, 28)}<div class="nj-c-grow"><div class="nj-c-qtitle">${esc(r.name)}</div><div class="nj-meta">${esc(r.meta)}</div></div>${pill(r.origin, r.failed ? 'err' : 'neutral')}${r.removed ? pill('Quitado', 'warn') + btn('Volver a añadir', 'histReadd', { kind: 'text' }) : r.failed ? btn('Reintentar', 'txRedl', { kind: 'text' }) : ibtn('open_in_new', 'Abrir', 'txOpen', { attrs: { 'data-arg': 't1' } })}</li>`).join('')}</ul>`).join('') : empty('history', 'Sin historial', 'Cada torrent y descarga que reproduzcas o bajes aparece aquí con su fecha.')}`;
    return sheetHTML(ctx, { id: 'hist', title: 'Historial', sub: 'Lo que reprodujiste, bajaste o añadiste. Vaciarlo no toca lo descargado.', body, foot: `<span class="nj-c-grow"></span>${btn('Vaciar historial…', 'open', { kind: 'danger', icon: 'delete_sweep', attrs: { 'data-arg': 'histClear' } })}` });
  }

  function txMatchSheet(ctx, s) {
    const l = s.local.find((x) => x.id === s.ovArg) || s.local[1];
    const q = (s.matchQ != null ? s.matchQ : 'Kanojo').toLowerCase();
    const res = D.titles.filter((t) => (t.title + ' ' + t.short).toLowerCase().includes(q)).slice(0, 6);
    const pick = s.matchPick || l.tid;
    const body = `<p class="nj-meta nj-c-ellipsis">${esc(l.file)}</p><span class="nj-search">${ic('search')}<input type="search" data-model="matchQ" value="${esc(s.matchQ != null ? s.matchQ : 'Kanojo')}" aria-label="Buscar título" data-autofocus></span>
      <div class="nj-c-results" role="radiogroup" aria-label="Resultados">${res.map((t) => `<button type="button" role="radio" aria-checked="${pick === t.id}" class="nj-c-result-row" data-c="matchPick" data-arg="${t.id}">${thumb(t.id, 32)}<span class="nj-c-grow"><span class="nj-c-qtitle">${esc(t.short)}</span><span class="nj-meta"> · ${t.format} · ${t.year} · ${t.eps} eps.</span></span>${pick === t.id ? ic('check_circle', { fill: true }) : ''}</button>`).join('') || '<p class="nj-meta">Ningún título coincide.</p>'}</div>
      ${stepper('matchEp', 'Episodio', s.matchEp, { min: 1 })}`;
    const foot = `${btn('Quitar emparejamiento', 'matchClear', { kind: 'text' })}<span class="nj-c-grow"></span>${btn('Cancelar', 'close', { kind: 'text' })}${btn('Guardar', 'matchSave', { kind: 'filled' })}`;
    return sheetHTML(ctx, { id: 'match', title: 'Emparejar video', body, foot });
  }

  function txDeleteDialog(ctx, s) {
    const ids = s.delIds || []; const items = txAll(s).filter((x) => ids.includes(x.id));
    const web = items.length && items.every((x) => x.kind === 'web');
    if (web) {
      return dialogHTML(ctx, { id: 'del', title: items.length > 1 ? `¿Cancelar ${items.length} descargas?` : '¿Cancelar la descarga?', icon: 'delete', tone: 'err', body: `<p>Se borra lo que ya bajó de ${esc(items.map(txTitle).join(', '))}.</p>`, actions: `${btn('Seguir descargando', 'close', { kind: 'text' })}${btn('Cancelar descarga', 'delGo', { kind: 'danger' })}` });
    }
    return dialogHTML(ctx, { id: 'del', title: items.length > 1 ? `¿Borrar ${items.length} torrents?` : '¿Borrar este torrent?', icon: 'delete', tone: 'err',
      body: `<p>${esc(items.map(txTitle).join(', '))} sale de la lista y deja de bajar y compartir.</p><label class="nj-c-checkrow">${check(s.delFiles, 'Borrar también los archivos descargados', 'delFiles', '')}<span>Borrar también los archivos descargados</span></label>${s.delFiles ? '' : '<p class="nj-meta">Los archivos de video se quedan en la carpeta de descargas y puedes deshacerlo.</p>'}`,
      actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn(items.length > 1 ? `Borrar ${items.length}` : 'Borrar', 'delGo', { kind: 'danger' })}` });
  }

  /* ---- detalle de un torrent ---- */
  function pieces(p) {
    const n = 96; let out = '';
    for (let i = 0; i < n; i++) { const x = i / n; const have = x < p * 0.92 || (x > 0.5 && x < 0.56) || (i % 17 === 3 && x < p + 0.2); const busy = !have && x >= p * 0.92 && x < p * 0.92 + 0.04; out += `<i class="${have ? 'is-have' : busy ? 'is-busy' : ''}"></i>`; }
    return `<div class="nj-c-pieces" role="img" aria-label="Mapa de piezas: ${Math.round(p * 100)} % descargado">${out}</div>`;
  }
  function txDetail(ctx, s, it) {
    const st = TX_STATE[it.state] || ['', 'neutral'];
    const tab = s.dTab;
    const tabs = [['resumen', 'Resumen'], ['archivos', 'Archivos'], ['pares', 'Pares'], ['trackers', 'Trackers']];
    let body = '';
    if (tab === 'resumen') {
      body = `<div class="nj-c-dhero"><div><div class="nj-c-bignum">${pct(it.pct)}</div><p class="nj-meta">${it.state === 'seeding' ? 'Completo · compartiendo' : `15,1 de ${esc(it.size)} · quedan ${esc(it.eta || 'tiempo desconocido')}`}</p></div>${pieces(it.pct)}</div>
        <div class="nj-c-keeprow">${tog(ctx, 'dKeep', 'Conservar después de ver', s.dKeep, s.dKeep ? 'Se queda en la biblioteca.' : 'Se borra al cerrar el reproductor.')}${tog(ctx, 'dSeed', 'Seguir compartiendo al terminar', s.dSeed, A(ctx) ? 'Apagado por defecto en Android: se pausa al completarse.' : 'Apagado por defecto: se pausa al completarse.')}</div>
        <div class="nj-c-statgrid">
          <section><h3 class="nj-c-label">Transferencia</h3><dl class="nj-c-kv"><dt>Bajado</dt><dd>15,1 GB</dd><dt>Subido</dt><dd>312 MB</dd><dt>Bajada</dt><dd>${esc(it.down || '0 B/s')}</dd><dt>Subida</dt><dd>210 kB/s</dd><dt>Piezas</dt><dd>7104 / 19200 × 2,1 MB</dd></dl></section>
          <section><h3 class="nj-c-label">Enjambre</h3><dl class="nj-c-kv"><dt>Semillas</dt><dd>5</dd><dt>Pares</dt><dd>2</dd><dt>Conexiones</dt><dd>7</dd><dt>Proporción</dt><dd>0,02</dd></dl></section>
          <section><h3 class="nj-c-label">Tiempo</h3><dl class="nj-c-kv"><dt>Restante</dt><dd>${esc(it.eta || 'Desconocido')}</dd><dt>Transcurrido</dt><dd>18 min</dd><dt>Añadido</dt><dd>hace 20 min</dd></dl></section>
        </div>
        <div class="nj-c-debridline">${ic('cloud_off', { size: 18 })}<span>TorBox no tiene este torrent en caché: baja por P2P.</span>${btn('Volver a consultar', 'txRecheck', { kind: 'text' })}</div>
        <div class="nj-c-hash"><span class="nj-c-label">Info-hash</span><code>25b66ad360a14ac0512b7a8e76b586955cdfefd2</code>${ibtn('content_copy', 'Copiar info-hash', 'txHash')}</div>
        <details class="nj-c-details"><summary>Detalles del motor</summary><div class="nj-cluster">${[['DHT', 1], ['LSD', 1], ['PEX', 1], ['NAT-PMP/UPnP', 1], ['Reenvío de puerto', 1], ['Persistente', 0], ['Streaming', 1]].map(([n, on]) => pill(n + (on ? ': activo' : ': inactivo'), on ? 'ok' : 'neutral', on ? 'check' : 'remove')).join('')}</div></details>`;
    } else if (tab === 'archivos') {
      const q = (s.fileQ || '').toLowerCase();
      const files = TX_FILES.map((f, i) => Object.assign({ i }, f)).filter((f) => !q || (f.path + f.name).toLowerCase().includes(q));
      const folders = {}; files.forEach((f) => (folders[f.path] = folders[f.path] || []).push(f));
      body = `<div class="nj-c-toolbar"><span class="nj-search nj-c-grow">${ic('search')}<input type="search" data-model="fileQ" value="${esc(s.fileQ || '')}" placeholder="Nombre o carpeta" aria-label="Buscar archivos"></span>${btn('Alinear episodios', 'open', { kind: 'tonal', icon: 'format_list_numbered', attrs: { 'data-arg': 'align' } })}</div>
        <p class="nj-meta">${TX_FILES.length} archivos · ${s.fileSel.filter(Boolean).length} se descargan · 39,7 GB</p>
        ${files.length ? Object.entries(folders).map(([p, fs]) => `<div class="nj-c-folder"><div class="nj-c-folderhead">${ic('folder', { size: 18 })}<span>${esc(p)}</span></div><ul class="nj-c-filelist">${fs.map((f) => `<li class="nj-c-menuwrap${s.fileSel[f.i] ? '' : ' is-skip'}">${check(s.fileSel[f.i], 'Descargar ' + f.name, 'fileSel', f.i)}<button type="button" class="nj-c-filebtn" data-c="menu" data-arg="file:${f.i}"><span class="nj-c-ellipsis">${esc(f.name)}</span><span class="nj-meta">${esc(f.size)} · ${s.fileSel[f.i] ? pct(f.pct) : 'Omitido'}</span>${s.fileSel[f.i] ? h.progress(f.pct, { label: 'Progreso del archivo' }) : ''}</button>${f.stream ? pill('En streaming', 'accent', 'stream') : ''}${f.ep ? pill('Ep. ' + f.ep, 'ok') : pill('Sin episodio', 'neutral')}${s.menu === 'file:' + f.i ? menuHTML([{ label: 'Reproducir este archivo', icon: 'play_arrow', c: 'go', arg: 'reproductor' }, { label: 'Guardar en Descargas', icon: 'save_alt', c: 'fileSave', arg: f.i }]) : ''}</li>`).join('')}</ul></div>`).join('')
          : empty('search_off', 'Ningún archivo coincide', `Nada coincide con «${s.fileQ}».`, btn('Limpiar búsqueda', 'fileClear', { kind: 'text' }))}`;
    } else if (tab === 'pares') {
      const k = s.peerSort; const ps = TX_PEERS.slice().sort((a, b) => (k === 'up' ? b.up - a.up : k === 'pct' ? b.pct - a.pct : b.down - a.down));
      body = `<div class="nj-c-toolbar"><p class="nj-meta nj-c-grow">6 conectados · bajada 6,4 MB/s · subida 200 kB/s</p>${seg('peerSort', 'Ordenar pares', [{ id: 'down', label: 'Bajada' }, { id: 'up', label: 'Subida' }, { id: 'pct', label: 'Progreso' }], k)}</div>
        <table class="nj-c-table"><thead><tr><th scope="col">Dirección</th><th scope="col">Cliente</th><th scope="col">País</th><th scope="col" class="is-num">Bajada</th><th scope="col" class="is-num">Subida</th><th scope="col" class="is-num">Tiene</th><th scope="col">Conexión</th></tr></thead>
        <tbody>${ps.map((p) => `<tr><td><code>${p.ip}</code></td><td>${esc(p.client)}</td><td>${p.cc}</td><td class="is-num">${p.down.toFixed(1).replace('.', ',')} MB/s</td><td class="is-num">${(p.up * 1000).toFixed(0)} kB/s</td><td class="is-num">${pct(p.pct)}</td><td><span class="nj-c-flags">${p.flags.split('').map((f) => `<span class="nj-c-flag" title="${FLAG[f][1]}" aria-label="${FLAG[f][1]}">${ic(FLAG[f][0], { size: 16 })}</span>`).join('')}</span></td></tr>`).join('')}</tbody></table>
        <details class="nj-c-details"><summary>Qué significan los íconos de conexión</summary><ul class="nj-c-legend">${Object.values(FLAG).map(([i, l]) => `<li>${ic(i, { size: 18 })}${esc(l)}</li>`).join('')}</ul></details>`;
    } else {
      const tone = { ok: ['Funciona', 'ok'], upd: ['Actualizando', 'neutral'], fail: ['Falló', 'err'], no: ['Sin contactar', 'neutral'] };
      body = `<div class="nj-c-toolbar"><p class="nj-meta nj-c-grow">4 trackers · 2 funcionan</p>${btn(s.reannounce ? 'Anunciando…' : 'Volver a anunciar', 'reannounce', { kind: 'tonal', icon: 'campaign' })}</div>
        <ul class="nj-c-plainlist nj-c-trackers">${TX_TRACKERS.map((t) => `<li><div class="nj-c-grow"><code class="nj-c-ellipsis">${esc(t.url)}</code><div class="nj-meta">Semillas ${t.s == null ? 'sin dato' : t.s} · Pares ${t.l == null ? 'sin dato' : t.l} · Descargas ${t.d == null ? 'sin dato' : t.d}${t.msg ? ' · ' + esc(t.msg) : ''}</div></div>${pill(tone[t.st][0], tone[t.st][1])}${ibtn('content_copy', 'Copiar URL del tracker', 'txHash')}</li>`).join('')}</ul>
        <p class="nj-meta">Tus propios trackers se anuncian en todos los torrents. Edítalos en Ajustes › Torrents.</p>`;
    }
    const menu = s.menu === 'dmenu' ? menuHTML([{ label: 'Copiar info-hash', icon: 'tag', c: 'txHash' }, A(ctx) ? { label: 'Compartir enlace magnet', icon: 'share', c: 'txMagnet' } : { label: 'Copiar enlace magnet', icon: 'link', c: 'txMagnet' }, !A(ctx) ? { label: 'Abrir carpeta', icon: 'folder_open', c: 'txFolder' } : null, '-', { label: 'Borrar…', icon: 'delete', c: 'txDel', arg: it.id, danger: true }].filter(Boolean)) : '';
    return `<section class="nj-c-detail" aria-label="Detalle de ${esc(txTitle(it))}">
      <header class="nj-c-dhead">${thumb(it.tid, 44)}<div class="nj-c-grow"><h2 class="nj-h2 nj-c-clamp">${esc(txTitle(it))}</h2><div class="nj-cluster">${pill(st[0], st[1])}${it.tid ? '' : `<button type="button" class="nj-chip" data-c="open" data-arg="add">${ic('link', { size: 18 })}Vincular a un título</button>`}</div></div>
        <div class="nj-cluster nj-c-menuwrap">${it.state === 'paused' ? btn('Reanudar', 'txResume', { kind: 'tonal', icon: 'play_arrow', attrs: { 'data-arg': it.id } }) : btn('Pausar', 'txPause', { kind: 'tonal', icon: 'pause', attrs: { 'data-arg': it.id } })}${it.tid ? btn('Reproducir', 'go', { kind: 'filled', icon: 'play_arrow', attrs: { 'data-arg': 'reproductor' } }) : ''}${ibtn('more_vert', 'Más acciones del torrent', 'menu', { attrs: { 'data-arg': 'dmenu' } })}${menu}</div></header>
      <div class="nj-tabs" role="tablist" aria-label="Secciones del torrent">${tabs.map(([id, l]) => `<button type="button" class="nj-tab" role="tab" aria-selected="${tab === id}" data-c="dTab" data-arg="${id}">${l}</button>`).join('')}</div>
      <div class="nj-c-dbody" role="tabpanel" data-keep="dbody">${body}</div></section>`;
  }

  function txAlignSheet(ctx, s) {
    const vids = TX_FILES.filter((f) => true);
    let order = s.alFirst;
    const body = `<p class="nj-meta">Los nombres con número conservan su número más el desfase. Los demás se numeran en orden desde el primer episodio. Puedes corregir cualquiera a mano.</p>
      <div class="nj-c-pair">${stepper('alFirst', 'Primer episodio', s.alFirst, { min: 1 })}${stepper('alOffset', 'Desfase', s.alOffset, { fmt: (v) => (v > 0 ? '+' + v : String(v)) })}</div>
      <ul class="nj-c-filelist nj-c-align">${vids.map((f, i) => { let n; if (s.alOver[i] != null) n = s.alOver[i]; else if (f.how === 'name') n = f.ep + s.alOffset; else n = 6 + s.alOffset + order++; return `<li><div class="nj-c-grow"><div class="nj-c-ellipsis">${esc(f.name)}</div><div class="nj-meta">${s.alOver[i] != null ? 'Corregido a mano' : f.how === 'name' ? 'Número tomado del nombre' : 'Por orden, revísalo'}</div></div>${f.how === 'name' && s.alOver[i] == null ? pill('Ep. ' + n, 'ok') : pill('Ep. ' + n, 'warn')}<div class="nj-c-mini">${ibtn('remove', 'Bajar episodio de ' + f.name, 'alNudge', { attrs: { 'data-arg': i + ':-1:' + n } })}${ibtn('add', 'Subir episodio de ' + f.name, 'alNudge', { attrs: { 'data-arg': i + ':1:' + n } })}</div></li>`; }).join('')}</ul>`;
    const foot = `${btn('Usar los nombres', 'alReset', { kind: 'text' })}<span class="nj-c-grow"></span>${btn('Cancelar', 'close', { kind: 'text' })}${btn('Guardar', 'alSave', { kind: 'filled' })}`;
    return sheetHTML(ctx, { id: 'align', title: 'Alinear episodios', body, foot, wide: true });
  }

  function txLibList(ctx, s) {
    const all = txAll(s);
    const f = s.libFilter; const q = (s.libQ || '').toLowerCase();
    let xs = all.filter((x) => (f === 'todos' || (f === 'bajando' ? x.state === 'downloading' : f === 'compartiendo' ? x.state === 'seeding' : f === 'pausa' ? x.state === 'paused' || x.state === 'queued' : x.state === 'error' || x.state === 'failed')) && (!q || txTitle(x).toLowerCase().includes(q)));
    const key = s.libSort; const dir = s.libDir === 'asc' ? 1 : -1;
    const ord = { downloading: 0, seeding: 1, starting: 2, queued: 3, paused: 4, failed: 5, error: 5 };
    if (key === 'nombre') xs.sort((a, b) => dir * txTitle(a).localeCompare(txTitle(b)));
    else if (key === 'progreso') xs.sort((a, b) => dir * (a.pct - b.pct));
    else if (key === 'estado') xs.sort((a, b) => dir * (ord[a.state] - ord[b.state]));
    else if (dir === 1) xs.reverse();
    return `<div class="nj-c-master" aria-label="Biblioteca de transferencias">
      <div class="nj-c-mastertools"><span class="nj-search">${ic('search')}<input type="search" data-model="libQ" value="${esc(s.libQ || '')}" placeholder="Buscar" aria-label="Buscar en transferencias"></span>
      <div class="nj-c-toolbar nj-c-menuwrap"><button type="button" class="nj-chip" data-c="menu" data-arg="sort" aria-haspopup="menu" aria-expanded="${s.menu === 'sort'}">${ic('sort', { size: 18 })}Orden: ${{ fecha: 'Fecha', nombre: 'Nombre', progreso: 'Progreso', estado: 'Estado' }[key]}</button>${ibtn(s.libDir === 'asc' ? 'arrow_upward' : 'arrow_downward', s.libDir === 'asc' ? 'Ascendente' : 'Descendente', 'libDir')}${s.menu === 'sort' ? menuHTML([['fecha', 'Fecha de añadido'], ['nombre', 'Nombre'], ['progreso', 'Progreso'], ['estado', 'Estado']].map(([id, l]) => ({ label: l, icon: id === key ? 'check' : 'blank', c: 'libSort', arg: id }))) : ''}</div>
      ${chips('libFilter', 'Filtrar por estado', [{ id: 'todos', label: 'Todos', count: all.length }, { id: 'bajando', label: 'Bajando' }, { id: 'compartiendo', label: 'Compartiendo' }, { id: 'pausa', label: 'En pausa' }, { id: 'error', label: 'Con error' }], f)}</div>
      <ul class="nj-c-masterlist" data-keep="master">${xs.map((x) => `<li><button type="button" class="nj-c-masterrow" data-c="dSel" data-arg="${x.id}" aria-current="${s.dSel === x.id}" data-fk="m-${x.id}">${thumb(x.tid, 32)}<span class="nj-c-grow"><span class="nj-c-qtitle nj-c-clamp">${esc(txTitle(x))}</span><span class="nj-meta">${esc(x.size)} · ${pct(x.pct)}</span>${h.progress(x.pct, { label: 'Progreso' })}</span>${pill((TX_STATE[x.state] || [''])[0], (TX_STATE[x.state] || ['', 'neutral'])[1])}</button></li>`).join('') || `<li>${empty('filter_alt_off', 'Nada con ese filtro', 'Prueba con otro estado o borra la búsqueda.', btn('Ver todos', 'libAll', { kind: 'text' }))}</li>`}</ul></div>`;
  }

  NJ.registerScreen({
    id: 'transferencias', name: 'Transferencias', group: 'Fuentes y equipo', order: 1, platforms: ['desktop', 'android'],
    summary: 'Descargas web, cliente de torrents, debrid y biblioteca local pasan de cuatro lugares a una sola cola ordenable con su detalle al lado.',
    parity: 'TOR-01…TOR-27, DL-01…DL-11, DL-15, DL-16 (DL-12/13 viven en la Ficha; DL-14 y TOR-25/26 completos en Ajustes › Torrents y descargas, con atajo desde Límites)',
    structures: [
      { id: 'cola', name: 'Cola y estanterías', desc: 'Cola activa arrastrable arriba, lo descargado por título abajo, paneles de debrid, almacenamiento, auto-descarga y biblioteca local al costado.' },
      { id: 'detalle', name: 'Cliente maestro-detalle', desc: 'Lista filtrable y ordenable a la izquierda, un torrent abierto con Resumen, Archivos, Pares y Trackers a la derecha.' },
    ],
    render(ctx) {
      const s = init(ctx, { queue: TX_QUEUE, lib: TX_LIB_EXTRA, done: TX_DONE, local: TX_LOCAL, rules: TX_RULES, folders: ['~/Videos/Anime', '/media/Almacen/Anime'], media: 'anime', sel: [], selMode: false, anchor: null, ov: null, menu: null, delFiles: true, histF: 'todo', limDown: '0', limUp: '0', sched: false, schedFrom: 480, schedTo: 1380, schedDown: '1', useDebrid: true, preferCached: true, dlCached: true, bgOn: true, bgAck: false, aTab: 'curso', addSrc: '', addFile: null, addSeries: null, addFirst: 1, addOffset: 0, addFiles: TX_FILES.map((f) => !f.skip), addKeep: true, addQ: '', dSel: 't1', dTab: 'resumen', dKeep: true, dSeed: false, fileSel: TX_FILES.map((f) => !f.skip), fileQ: '', peerSort: 'down', libSort: 'fecha', libDir: 'desc', libFilter: 'todos', libQ: '', alFirst: 1, alOffset: 0, alOver: {}, matchEp: 3, checked: false, autoResult: null, sim: null });
      const isA = A(ctx);
      const nSel = s.sel.length;
      const down = s.queue.filter((x) => x.state === 'downloading').length;
      let main = '';
      if (ctx.structure === 'detalle') {
        const it = txAll(s).find((x) => x.id === s.dSel) || txAll(s)[0];
        if (isA) {
          main = `${pageHead(ctx, { title: 'Torrent', back: 'go', actionsA: '' }).replace('data-c="go"', 'data-c="go" data-arg="transferencias:cola"')}<div class="nj-c-body" data-keep="main">${txDetail(ctx, s, it)}</div>`;
        } else {
          main = `${pageHead(ctx, { title: 'Transferencias', meta: `↓ 31,1 MB/s · ↑ 210 kB/s · ${down} bajando`, actions: `<span class="nj-c-menuwrap">${btn('Límites', 'menu', { kind: 'outline', icon: 'speed', attrs: { 'data-arg': 'limits', 'aria-haspopup': 'dialog' } })}${s.menu === 'limits' ? txLimitsPop(ctx, s) : ''}</span>${btn('Añadir torrent', 'open', { kind: 'filled', icon: 'add', attrs: { 'data-arg': 'add' } })}` })}
            <div class="nj-c-md">${txLibList(ctx, s)}<div class="nj-c-mdright" data-keep="detail">${txDetail(ctx, s, it)}</div></div>`;
        }
      } else if (isA) {
        const tab = s.aTab;
        const tabs = `<div class="nj-tabs nj-c-atabs" role="tablist" aria-label="Secciones">${[['curso', 'En curso'], ['hecho', 'Descargado'], ['local', 'Local']].map(([id, l]) => `<button type="button" class="nj-tab" role="tab" aria-selected="${tab === id}" data-c="aTab" data-arg="${id}">${l}</button>`).join('')}</div>`;
        const head = s.selMode
          ? `<header class="nj-appbar nj-c-appbar is-context">${ibtn('close', 'Salir de la selección', 'selExit')}<h1 class="nj-h2 nj-c-grow">${nSel} seleccionados</h1>${ibtn('select_all', 'Seleccionar todo', 'selAll')}${ibtn('pause', 'Pausar seleccionados', 'batchPause')}${ibtn('play_arrow', 'Reanudar seleccionados', 'batchResume')}${ibtn('delete', 'Borrar seleccionados', 'batchDel')}</header>`
          : pageHead(ctx, { title: 'Transferencias', actionsA: `${ibtn('speed', 'Límites rápidos', 'menu', { attrs: { 'data-arg': 'limits' } })}${ibtn('history', 'Historial', 'open', { attrs: { 'data-arg': 'hist' } })}${ibtn('settings', 'Ajustes de torrents y descargas', 'go', { attrs: { 'data-arg': 'ajustes' } })}` });
        let content = '';
        if (tab === 'curso') content = `${txBg(ctx, s)}${txDebridLine()}<div class="nj-c-sectionhead"><h2 class="nj-h3">En curso · ${s.queue.length}</h2><span class="nj-meta">Mantén presionado para seleccionar</span></div>${txQueue(ctx, s)}`;
        else if (tab === 'hecho') content = `<details class="nj-c-panel nj-c-collapse"><summary><span class="nj-h3">Auto-descarga</span><span class="nj-meta">${s.rules.filter((r) => r.on).length} reglas activas</span></summary>${txRules(ctx, s).replace(/^<section class="nj-c-panel"/, '<section class="nj-c-plain"')}</details>${txDone(ctx, s)}${txSide(ctx, s)}`;
        else content = txLocal(ctx, s);
        main = `${head}${s.menu === 'limits' ? `<div class="nj-c-popanchor">${txLimitsPop(ctx, s)}</div>` : ''}${tabs}<div class="nj-c-body" data-keep="main">${content}<div class="nj-c-fabspace"></div></div>${s.selMode || tab !== 'curso' ? '' : `<button type="button" class="nj-c-fab" data-c="open" data-arg="add">${ic('add')}Añadir torrent</button>`}`;
      } else {
        const actions = `<span class="nj-search nj-c-hsearch">${ic('search')}<input type="search" data-model="libQ" value="${esc(s.libQ || '')}" placeholder="Buscar en transferencias" aria-label="Buscar en todas las secciones"></span>
          <span class="nj-c-menuwrap">${btn('Límites', 'menu', { kind: 'outline', icon: 'speed', attrs: { 'data-arg': 'limits', 'aria-haspopup': 'dialog', 'aria-expanded': String(s.menu === 'limits') } })}${s.menu === 'limits' ? txLimitsPop(ctx, s) : ''}</span>
          ${btn('Historial', 'open', { kind: 'outline', icon: 'history', attrs: { 'data-arg': 'hist' } })}
          ${btn('Añadir torrent', 'open', { kind: 'filled', icon: 'add', attrs: { 'data-arg': 'add' } })}`;
        main = `${pageHead(ctx, { title: 'Transferencias', meta: `↓ 31,1 MB/s · ↑ 210 kB/s · ${down} bajando · ${s.limDown === '0' ? 'sin límite' : 'bajada limitada a ' + s.limDown + ' MB/s'}`, actions })}
          <div class="nj-c-split"><div class="nj-c-col" data-keep="main">
            ${txDebridLine()}
            <div class="nj-c-sectionhead"><h2 class="nj-h2">En curso · ${s.queue.length}</h2><span class="nj-meta">Arrastra para cambiar el orden, o Alt+↑/↓ con una fila enfocada</span>${btn(s.selMode ? 'Listo' : 'Seleccionar', 'selToggle', { kind: s.selMode ? 'tonal' : 'text', icon: s.selMode ? 'check' : 'checklist' })}</div>
            ${txQueue(ctx, s)}
            ${txDone(ctx, s)}
            <div class="nj-c-fabspace"></div>
          </div><aside class="nj-c-aside" data-keep="aside" aria-label="Estado y reglas">${txSide(ctx, s)}${txRules(ctx, s)}${txLocal(ctx, s)}</aside></div>`;
      }
      const batch = s.selMode && !isA ? `<div class="nj-c-batch" role="toolbar" aria-label="Acciones de la selección"><span class="nj-c-batchcount">${nSel ? nSel + ' seleccionados' : 'Toca filas para seleccionarlas'}</span>
        ${btn('Pausar', 'batchPause', { kind: 'text', icon: 'pause' })}${btn('Reanudar', 'batchResume', { kind: 'text', icon: 'play_arrow' })}${btn('Conservar', 'batchKeep', { kind: 'text', icon: 'bookmark_add' })}${btn('No conservar', 'batchUnkeep', { kind: 'text', icon: 'bookmark_remove' })}${btn('Al principio', 'batchTop', { kind: 'text', icon: 'vertical_align_top' })}
        <span class="nj-c-batchsep" aria-hidden="true"></span>${btn(nSel ? `Borrar ${nSel}…` : 'Borrar…', 'batchDel', { kind: 'danger', icon: 'delete' })}<span class="nj-c-batchsep" aria-hidden="true"></span>${btn('Todo', 'selAll', { kind: 'text' })}${ibtn('close', 'Salir de la selección', 'selExit')}</div>` : '';
      let ov = '';
      if (s.ov === 'add') ov = txAddSheet(ctx, s);
      else if (s.ov === 'hist') ov = txHistSheet(ctx, s);
      else if (s.ov === 'histClear') ov = dialogHTML(ctx, { id: 'histClear', title: '¿Vaciar el historial?', icon: 'delete_sweep', tone: 'err', body: '<p>Solo se vacía la lista. Los torrents de la biblioteca y los archivos descargados se quedan.</p>', actions: `${btn('Cancelar', 'open', { kind: 'text', attrs: { 'data-arg': 'hist' } })}${btn('Vaciar', 'histClearGo', { kind: 'danger' })}` });
      else if (s.ov === 'del') ov = txDeleteDialog(ctx, s);
      else if (s.ov === 'delGroup') ov = dialogHTML(ctx, { id: 'delGroup', title: 'Borrar todos los episodios', icon: 'delete', tone: 'err', body: `<p>¿Borrar todos los episodios descargados de ${esc((T(s.ovArg) || {}).short || 'este título')}?</p>`, actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Borrar todos', 'delGroupGo', { kind: 'danger' })}` });
      else if (s.ov === 'align') ov = txAlignSheet(ctx, s);
      else if (s.ov === 'match') ov = txMatchSheet(ctx, s);
      else if (s.ov === 'localDel') ov = dialogHTML(ctx, { id: 'localDel', title: 'Quitar de la biblioteca', icon: 'video_file', body: `<p>${esc((s.local.find((l) => l.id === s.ovArg) || {}).file || '')} sale de la biblioteca. ¿Borrar también el archivo?</p>`, actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Conservar el archivo', 'localDelGo', { kind: 'outline', attrs: { 'data-arg': 'keep' } })}${btn('Borrar el archivo', 'localDelGo', { kind: 'danger', attrs: { 'data-arg': 'file' } })}` });
      const dropov = isA ? '' : `<div class="nj-c-dropov" aria-hidden="true">${ic('upload_file', { size: 40 })}<p class="nj-h2">Suelta el .torrent para añadirlo</p></div>`;
      return `<div class="nj-c-root nj-c-tx" data-local data-cards-local>${main}${batch}${ov}${snackHTML(s)}${dropov}</div>`;
    },
    mount(el, ctx) {
      const s = ctx.state; const root = el.querySelector('.nj-c-root'); if (!root) return;
      const re = () => rr(ctx, el);
      const findQ = (id) => s.queue.findIndex((x) => x.id === id);
      const allItems = () => txAll(s);
      const move = (ids, to) => { flipSave(ctx, el); const set = new Set(ids); const block = s.queue.filter((x) => set.has(x.id)); const rest = []; let at = -1; s.queue.forEach((x, i) => { if (i === to) at = rest.length; if (!set.has(x.id)) rest.push(x); }); if (at < 0) at = rest.length; s.queue = rest.slice(0, at).concat(block, rest.slice(at)); };
      const setState = (ids, state) => { allItems().forEach((x) => { if (ids.includes(x.id) && x.state !== 'seeding') x.state = x.state === 'failed' && state === 'downloading' ? 'queued' : state; }); };
      const H = {
        $seg(k) { if (k === 'peerSort') return true; return true; },
        $chip(k, v) { if (/^rule\d+$/.test(k)) { s.rules[Number(k.slice(4))].profile = v; re(); return false; } return true; },
        $toggle(k, on) { if (/^rule\d+$/.test(k)) { s.rules[Number(k.slice(4))].on = on; re(); } else if (['useDebrid', 'sched', 'dKeep'].includes(k)) re(); },
        $steps: { schedFrom: { step: 30, wrap: 1440 }, schedTo: { step: 30, wrap: 1440 }, addFirst: { min: 1, max: 9999 }, addOffset: { min: -9999, max: 9999 }, alFirst: { min: 1, max: 9999 }, alOffset: { min: -9999, max: 9999 }, matchEp: { min: 1, max: 9999 } },
        $text(k, v, n) {
          if (k === 'addSrc') { const hint = el.querySelector('[data-addhint]'); const valid = /^magnet:\?/i.test(v) || /^[a-f0-9]{40}$/i.test(v.trim()); if (valid || !v) { s._focus = 'add-src'; n.setAttribute('data-fk', 'add-src'); re(); } else if (hint) { hint.textContent = 'Esto no es un enlace magnet ni un info-hash válido.'; hint.classList.add('nj-c-errtext'); } }
          if (k === 'addQ' || k === 'matchQ' || k === 'fileQ' || k === 'libQ') { n.setAttribute('data-fk', 'in-' + k); clearTimeout(H._t); H._t = setTimeout(re, 250); }
        },
        $undo() { if (s._undo) { flipSave(ctx, el); s.queue = s._undo; s._undo = null; } },
        selToggle() { s.selMode = !s.selMode; s.sel = []; s.menu = null; re(); },
        selExit() { s.selMode = false; s.sel = []; re(); },
        selAll() { s.sel = s.queue.map((x) => x.id); re(); },
        txSel(t, e) { const id = t.getAttribute('data-arg'); const ids = s.queue.map((x) => x.id); if (e.shiftKey && s.anchor) { const a = ids.indexOf(s.anchor); const b = ids.indexOf(id); ids.slice(Math.min(a, b), Math.max(a, b) + 1).forEach((x) => { if (!s.sel.includes(x)) s.sel.push(x); }); } else { const i = s.sel.indexOf(id); if (i >= 0) s.sel.splice(i, 1); else s.sel.push(id); s.anchor = id; } re(); },
        txPause(t) { setState([t.getAttribute('data-arg')], 'paused'); re(); },
        txResume(t) { setState([t.getAttribute('data-arg')], 'downloading'); allItems().forEach((x) => { if (x.id === t.getAttribute('data-arg')) x.err = null; }); re(); },
        txResumeAll() { setState(s.queue.map((x) => x.id), 'downloading'); snack(ctx, 'Se reanudaron todas las descargas'); re(); },
        batchPause() { setState(s.sel, 'paused'); re(); },
        batchResume() { setState(s.sel, 'downloading'); re(); },
        batchKeep() { allItems().forEach((x) => { if (s.sel.includes(x.id)) x.keep = true; }); snack(ctx, `${s.sel.length} se conservan después de verlos`); re(); },
        batchUnkeep() { allItems().forEach((x) => { if (s.sel.includes(x.id)) x.keep = false; }); snack(ctx, `${s.sel.length} se borran después de verlos`); re(); },
        batchTop() { move(s.sel, 0); re(); },
        batchDel() { if (!s.sel.length) return; s.delIds = s.sel.slice(); openOv(ctx, 'del'); re(); },
        txDel(t) { s.delIds = [t.getAttribute('data-arg')]; openOv(ctx, 'del'); re(); },
        delFiles() { s.delFiles = !s.delFiles; re(); },
        delGo() { const ids = s.delIds || []; const n = ids.length; const files = s.delFiles; s._undo = files ? null : s.queue.slice(); flipSave(ctx, el); s.queue = s.queue.filter((x) => !ids.includes(x.id)); s.lib = s.lib.filter((x) => !ids.includes(x.id)); s.sel = []; s.selMode = false; s.ov = null; if (!s.queue.some((x) => x.id === s.dSel)) s.dSel = (txAll(s)[0] || {}).id; snack(ctx, files ? (n > 1 ? `Se borraron ${n} y sus archivos` : 'Se borró el torrent y sus archivos') : (n > 1 ? `Se quitaron ${n} de la lista` : 'Se quitó de la lista'), files ? null : 'Deshacer'); re(); },
        txTop(t) { move([t.getAttribute('data-arg')], 0); s.menu = null; re(); },
        txBottom(t) { move([t.getAttribute('data-arg')], s.queue.length); s.menu = null; re(); },
        txKeep(t) { const x = allItems().find((y) => y.id === t.getAttribute('data-arg')); if (x) { x.keep = !x.keep; snack(ctx, x.keep ? 'Se conserva después de verlo' : 'Se borra después de verlo'); } s.menu = null; re(); },
        txFolder() { s.menu = null; snack(ctx, 'Se abrió la carpeta en el gestor de archivos'); re(); },
        txMagnet() { s.menu = null; snack(ctx, A(ctx) ? 'Elige una app para compartir el enlace' : 'Enlace magnet copiado'); re(); },
        txHash() { s.menu = null; snack(ctx, 'Copiado al portapapeles'); re(); },
        txOpen(t) { s.dSel = t.getAttribute('data-arg') || 't1'; s.menu = null; s.ov = null; NJ.go('transferencias', { structure: 'detalle' }); },
        txRedl() { snack(ctx, 'Se volvió a poner en cola'); re(); },
        txDelEp(t) { const [g, n] = (t.getAttribute('data-arg') || '').split(':'); const prev = clone(s.done); const grp = s.done.find((x) => x.tid === g); if (grp) { grp.eps = grp.eps.filter((e) => String(e.n) !== n); if (!grp.eps.length) s.done = s.done.filter((x) => x !== grp); } s._undoDone = prev; snack(ctx, 'Episodio borrado', 'Deshacer', 'undoEp'); re(); },
        undoEp() { if (s._undoDone) s.done = s._undoDone; s._undoDone = null; s.snack = null; re(); },
        txDelGroup(t) { openOv(ctx, 'delGroup', t.getAttribute('data-arg')); re(); },
        delGroupGo() { s.done = s.done.filter((g) => g.tid !== s.ovArg); s.ov = null; snack(ctx, 'Se borraron todos los episodios'); re(); },
        txCheck() { s.checked = true; re(); },
        txRecheck() { snack(ctx, 'TorBox todavía no lo tiene en caché'); re(); },
        goDebrid() { if (NJ.screens.fuentes) { const k = 'fuentes:' + ctx.platform; NJ.screenState[k] = NJ.screenState[k] || {}; NJ.screenState[k].tab = 'debrid'; NJ.go('fuentes', { structure: 'pestanas' }); } },
        bgAck() { s.bgAck = true; s.bgOn = true; snack(ctx, 'Descargas en segundo plano activadas'); re(); },
        bgLater() { s.aTab = 'hecho'; re(); },
        aTab(t) { s.aTab = t.getAttribute('data-arg'); re(); },
        autoCheck() { s.autoResult = '1 episodio nuevo en cola: Ao no Hako 2 · Ep. 9.'; re(); },
        autoSim(t) { s.sim = Number(t.getAttribute('data-arg')); re(); },
        autoDel(t) { const i = Number(t.getAttribute('data-arg')); const r = s.rules.splice(i, 1)[0]; s._rule = { r, i }; snack(ctx, `Se quitó la regla de ${T(r.tid).short}`, 'Deshacer', 'undoRule'); re(); },
        undoRule() { if (s._rule) s.rules.splice(s._rule.i, 0, s._rule.r); s._rule = null; s.snack = null; re(); },
        autoAdd(t) { s.rules.push({ tid: t.getAttribute('data-arg'), last: 0, on: true, profile: 'bal' }); re(); },
        localImport() { snack(ctx, 'Se añadieron 2 videos'); re(); },
        localScan() { snack(ctx, 'Carpeta escaneada: 4 videos nuevos'); re(); },
        localDel(t) { openOv(ctx, 'localDel', t.getAttribute('data-arg')); re(); },
        localDelGo(t) { const id = s.ovArg; s.local = s.local.filter((l) => l.id !== id); s.ov = null; snack(ctx, t.getAttribute('data-arg') === 'file' ? 'Video y archivo borrados' : 'Se quitó de la biblioteca; el archivo se queda'); re(); },
        folderAdd() { s.folders.push('~/Descargas/Series'); snack(ctx, 'Carpeta añadida y escaneada'); re(); },
        folderDel(t) { s.folders.splice(Number(t.getAttribute('data-arg')), 1); re(); },
        matchPick(t) { s.matchPick = t.getAttribute('data-arg'); re(); },
        matchSave() { const l = s.local.find((x) => x.id === s.ovArg); if (l && s.matchPick) { l.tid = s.matchPick; l.ep = s.matchEp; } s.ov = null; snack(ctx, 'Video emparejado'); re(); },
        matchClear() { const l = s.local.find((x) => x.id === s.ovArg); if (l) l.tid = null; s.ov = null; snack(ctx, 'Se quitó el emparejamiento'); re(); },
        addPick() { s.addFile = '[Grupo] Grand Blue S02 (E01-E06) [1080p].torrent'; re(); },
        addPaste() { s.addSrc = 'magnet:?xt=urn:btih:25b66ad360a14ac0512b7a8e76b586955cdfefd2&dn=Grand+Blue+S02'; re(); },
        addClearFile() { s.addFile = null; re(); },
        addSeries(t) { s.addSeries = t.getAttribute('data-arg'); re(); },
        addSeriesClear() { s.addSeries = null; re(); },
        addFile(t) { const i = Number(t.getAttribute('data-arg')); s.addFiles[i] = !s.addFiles[i]; re(); },
        addAll() { s.addFiles = TX_FILES.map(() => true); re(); },
        addNone() { s.addFiles = TX_FILES.map(() => false); re(); },
        addVideos() { s.addFiles = TX_FILES.map((f) => !!f.ep); re(); },
        addGo() { flipSave(ctx, el); s.queue.push({ id: 'n' + Date.now(), tid: s.addSeries, name: s.addFile || 'Grand Blue S02 (magnet)', files: 6, ep: null, kind: 'torrent', state: 'starting', pct: 0, size: '39,7 GB', src: 'Lote · ' + s.addFiles.filter(Boolean).length + ' archivos', keep: s.addKeep }); s.ov = null; s.addSrc = ''; s.addFile = null; snack(ctx, 'Torrent añadido al final de la cola', 'Abrir', 'txOpenNew'); re(); },
        txOpenNew() { s.snack = null; NJ.go('transferencias', { structure: 'detalle' }); },
        histReadd() { snack(ctx, 'Se volvió a añadir como streaming'); re(); },
        histClearGo() { s.ov = null; snack(ctx, 'Historial vaciado'); re(); },
        dSel(t) { s.dSel = t.getAttribute('data-arg'); re(); },
        dTab(t) { s.dTab = t.getAttribute('data-arg'); re(); },
        libSort(t) { s.libSort = t.getAttribute('data-arg'); s.menu = null; re(); },
        libDir() { s.libDir = s.libDir === 'asc' ? 'desc' : 'asc'; re(); },
        libAll() { s.libFilter = 'todos'; s.libQ = ''; re(); },
        fileSel(t) { const i = Number(t.getAttribute('data-arg')); s.fileSel[i] = !s.fileSel[i]; re(); },
        fileClear() { s.fileQ = ''; re(); },
        fileSave() { s.menu = null; snack(ctx, 'Guardado en Descargas'); re(); },
        reannounce() { s.reannounce = true; re(); setTimeout(() => { if (ctx.state.reannounce) { ctx.state.reannounce = false; if (el.isConnected) rr(ctx, el); } }, 1500); },
        alNudge(t) { const [i, d, n] = t.getAttribute('data-arg').split(':').map(Number); s.alOver[i] = Math.max(1, n + d); re(); },
        alReset() { s.alOver = {}; s.alFirst = 1; s.alOffset = 0; re(); },
        alSave() { s.ov = null; snack(ctx, 'Episodios alineados'); re(); },
        $key(e) {
          const row = e.target.closest && e.target.closest('.nj-c-qrow');
          if (!row) return;
          const id = row.getAttribute('data-id'); const i = findQ(id); if (i < 0) return;
          if (e.altKey && (e.key === 'ArrowUp' || e.key === 'ArrowDown')) {
            e.preventDefault(); const ids = s.selMode && s.sel.includes(id) ? s.sel.slice() : [id];
            const first = Math.min.apply(null, ids.map(findQ)); const last = Math.max.apply(null, ids.map(findQ));
            const to = e.key === 'ArrowUp' ? Math.max(0, first - 1) : Math.min(s.queue.length, last + 2);
            move(ids, to); s._focus = 'q-' + id; ctx.rerender(); return;
          }
          if (s.selMode && (e.key === ' ' || e.key === 'Enter') && e.target === row) { e.preventDefault(); const k = s.sel.indexOf(id); if (k >= 0) s.sel.splice(k, 1); else s.sel.push(id); s._focus = 'q-' + id; ctx.rerender(); return; }
          if (!s.selMode && e.key === 'Enter' && e.target === row) { s.dSel = id; NJ.go('transferencias', { structure: 'detalle' }); }
          if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && !e.altKey && e.target === row) { e.preventDefault(); const n = e.key === 'ArrowDown' ? row.nextElementSibling : row.previousElementSibling; if (n && n.classList.contains('nj-c-qrow')) n.focus(); }
        },
      };
      H.snackAct = (t) => { const c = s.snack && s.snack.c; if (c && H[c]) H[c](t); else { H.$undo(s.snack); s.snack = null; re(); } };
      wire(el, ctx, H);
      flipPlay(el, ctx);
      /* clic de fila en modo selección */
      el.addEventListener('click', (e) => {
        const row = e.target.closest('.nj-c-qrow'); if (!row || e.target.closest('button, .nj-c-menu')) return;
        if (!s.selMode) { if (!A(ctx) && e.detail === 2) { s.dSel = row.getAttribute('data-id'); NJ.go('transferencias', { structure: 'detalle' }); } else if (A(ctx)) { s.dSel = row.getAttribute('data-id'); NJ.go('transferencias', { structure: 'detalle' }); } return; }
        const id = row.getAttribute('data-id');
        const ids = s.queue.map((x) => x.id);
        if (e.shiftKey && s.anchor) { const a = ids.indexOf(s.anchor); const b = ids.indexOf(id); ids.slice(Math.min(a, b), Math.max(a, b) + 1).forEach((x) => { if (!s.sel.includes(x)) s.sel.push(x); }); } else { const k = s.sel.indexOf(id); if (k >= 0) s.sel.splice(k, 1); else s.sel.push(id); s.anchor = id; }
        re();
      });
      el.addEventListener('click', (e) => { const c = e.target.closest('.nj-card[data-id]'); if (c && el.contains(c)) NJ.go('ficha'); });
      if (A(ctx)) longPress(el, '.nj-c-qrow', (row) => { s.selMode = true; const id = row.getAttribute('data-id'); if (!s.sel.includes(id)) s.sel.push(id); re(); });
      /* reordenar arrastrando con barra de inserción */
      const list = el.querySelector('[data-queue]'); const bar = list && list.querySelector('.nj-c-insert');
      let dragIds = null; let dropAt = -1;
      if (list) {
        list.addEventListener('dragstart', (e) => {
          const row = e.target.closest('.nj-c-qrow'); if (!row) return;
          const id = row.getAttribute('data-id'); dragIds = s.selMode && s.sel.includes(id) ? s.sel.slice() : [id];
          e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/x-nj-row', id); } catch (er) { /* sin datos */ }
          requestAnimationFrame(() => list.querySelectorAll('.nj-c-qrow').forEach((r) => { if (dragIds.includes(r.getAttribute('data-id'))) r.classList.add('is-dragging'); }));
        });
        list.addEventListener('dragover', (e) => {
          if (!dragIds) return; e.preventDefault(); e.dataTransfer.dropEffect = 'move';
          const rows = Array.from(list.querySelectorAll('.nj-c-qrow')); const scale = list.getBoundingClientRect().height / (list.offsetHeight || 1) || 1;
          dropAt = rows.length; let y = 0;
          for (let i = 0; i < rows.length; i++) { const r = rows[i].getBoundingClientRect(); if (e.clientY < r.top + r.height / 2) { dropAt = i; break; } }
          const ref = rows[Math.min(dropAt, rows.length - 1)]; const lr = list.getBoundingClientRect();
          if (ref) { const rr2 = ref.getBoundingClientRect(); y = ((dropAt >= rows.length ? rr2.bottom : rr2.top) - lr.top) / scale; }
          bar.style.transform = `translateY(${y - 1}px)`; bar.classList.add('is-on');
        });
        list.addEventListener('drop', (e) => { if (!dragIds) return; e.preventDefault(); const ids = dragIds; dragIds = null; bar.classList.remove('is-on'); move(ids, dropAt); s._focus = 'q-' + ids[0]; ctx.rerender(); });
        list.addEventListener('dragend', () => { dragIds = null; bar.classList.remove('is-on'); list.querySelectorAll('.is-dragging').forEach((r) => r.classList.remove('is-dragging')); });
      }
      /* soltar un .torrent en cualquier parte (escritorio) */
      if (!A(ctx)) {
        let depth = 0;
        const isFile = (e) => e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');
        root.addEventListener('dragenter', (e) => { if (!isFile(e)) return; depth++; root.classList.add('is-filedrag'); });
        root.addEventListener('dragleave', (e) => { if (!isFile(e)) return; depth = Math.max(0, depth - 1); if (!depth) root.classList.remove('is-filedrag'); });
        root.addEventListener('dragover', (e) => { if (isFile(e)) e.preventDefault(); });
        root.addEventListener('drop', (e) => {
          if (!isFile(e)) return; e.preventDefault(); depth = 0; root.classList.remove('is-filedrag');
          const f = Array.from(e.dataTransfer.files || []).find((x) => /\.torrent$/i.test(x.name));
          if (f) { s.addFile = f.name; openOv(ctx, 'add'); } else snack(ctx, 'Ese archivo no es un .torrent válido.');
          re();
        });
      }
    },
  });

  /* =====================================================================
     2. FUENTES: hub de extensiones .kai, repositorios, Stremio, proveedores, servidores de PC y debrid
     ===================================================================== */
  const FU_EXTS = [
    { id: 'toko', name: 'Toko', ver: '4.6.2', compat: 'partial', kinds: ['Anime'], src: 'streams web y torrents', on: true, desc: 'Extensión oficial de Tatakai. Streams directos por Nebula, varios servidores de anime y búsqueda de torrents.', perms: ['Internet', 'Guarda datos', 'Usa torrents'], domains: ['core.justanime.to', 'justanime.to', 'megaplay.buzz', 'animegg.org', 'animepahe.com', 'animepahe.ru'], domCount: 312, sha: '44d0…4ebd', updated: 'ayer', browser: 12, update: '4.7.0', notes: 'Corrige la búsqueda por temporada y añade dos servidores.', reason: "Usa el módulo de Node 'string_decoder' simulado.", prefs: true },
    { id: 'kumo', name: 'Kumo', ver: '1.3.0', compat: 'full', kinds: ['Manga'], src: 'capítulos', on: true, desc: 'Capítulos de manga y manhwa de varias fuentes, con idiomas por capítulo.', perms: ['Internet'], domains: ['kumo.example.org', 'img.example.org'], domCount: 2, sha: '9a1c…02f7', updated: 'hace 6 d', browser: 0, update: '1.4.0', notes: 'Agrega filtro por grupo de traducción.', prefs: false },
    { id: 'fumi', name: 'Fumi', ver: '0.9.1', compat: 'unsupported', kinds: ['Novela'], src: 'capítulos de texto', on: false, desc: 'Novelas ligeras traducidas por la comunidad.', perms: ['Internet', 'Guarda datos'], domains: ['fumi.example.net'], domCount: 1, sha: '1be0…77aa', updated: 'hace 1 mes', browser: 0, reason: 'Necesita WebSocket, que esta versión todavía no ofrece.', prefs: false },
  ];
  const FU_LOG = ['[ok] search: 24 resultados para «ao no hako» en 820 ms', '[ok] streams: Nebula 1080p HLS', '[warn] catalog: catálogo propio omitido (compatibilidad parcial)', '[err] streams: animepahe.ru respondió 403, se probó el siguiente servidor', '[ok] torrents: 6 resultados'];
  const FU_REPOS = [
    { id: 'r1', name: 'Tatakai oficial', url: 'https://repo.example.org/index.json', n: 14, read: 'hace 2 h' },
    { id: 'r2', name: 'Comunidad', url: 'https://kai.example.net/index.json', n: 31, read: 'hace 3 d', failed: 'El servidor no respondió a tiempo.' },
  ];
  const FU_STORE = [
    { id: 'toko', name: 'Toko', repo: 'Tatakai oficial', ver: '4.7.0', kinds: ['Anime'], desc: 'Streams directos por Nebula, varios servidores y torrents.', perms: ['Internet', 'Guarda datos', 'Usa torrents'], dom: 312, status: 'update' },
    { id: 'kumo', name: 'Kumo', repo: 'Tatakai oficial', ver: '1.4.0', kinds: ['Manga'], desc: 'Manga y manhwa con idiomas por capítulo.', perms: ['Internet'], dom: 2, status: 'update' },
    { id: 'hikari', name: 'Hikari', repo: 'Tatakai oficial', ver: '2.1.0', kinds: ['Anime'], desc: 'Solo torrents: índices públicos con filtros de calidad.', perms: ['Internet', 'Usa torrents'], dom: 5, status: 'new' },
    { id: 'shiori', name: 'Shiori', repo: 'Tatakai oficial', ver: '1.0.4', kinds: ['Novela'], desc: 'Capítulos de novela con traductor y fecha.', perms: ['Internet'], dom: 3, status: 'new' },
    { id: 'nami', name: 'Nami', repo: 'Comunidad', ver: '0.6.0', kinds: ['Anime', 'Manga'], desc: 'Catálogo propio de temporada y capítulos.', perms: ['Internet', 'Guarda datos'], dom: 18, status: 'new', compat: 'partial' },
    { id: 'fumi', name: 'Fumi', repo: 'Comunidad', ver: '0.9.1', kinds: ['Novela'], desc: 'Novelas ligeras traducidas por la comunidad.', perms: ['Internet', 'Guarda datos'], dom: 1, status: 'installed' },
    { id: 'tsuki', name: 'Tsuki', repo: 'Comunidad', ver: '3.2.1', kinds: ['Manga'], desc: 'Webtoons verticales en alta resolución.', perms: ['Internet'], dom: 4, status: 'new' },
    { id: 'kaze', name: 'Kaze', repo: 'Comunidad', ver: '1.8.0', kinds: ['Anime'], desc: 'Servidores espejo con subtítulos en español.', perms: ['Internet'], dom: 9, status: 'new' },
  ];
  const FU_STREMIO = [
    { id: 's1', name: 'Torrentio', ver: '0.0.14', types: 'Series, películas y anime', on: true, desc: 'Busca torrents en varios índices.' },
    { id: 's2', name: 'Catálogo de Cinemeta', ver: '3.0.13', types: 'Catálogo', on: true, desc: 'Metadatos y catálogos de películas y series.', noStreams: true },
  ];
  const FU_PROV = [
    { name: 'Nebula', ext: 'Toko', kind: 'Anime', desc: 'Catálogo propio por temporadas' },
    { name: 'Kumo · Biblioteca', ext: 'Kumo', kind: 'Manga', desc: 'Tendencias y últimas actualizaciones' },
    { name: 'Toko en octavio-pc', ext: 'Servidor de la PC', kind: 'Anime', desc: 'Catálogo servido por tu PC' },
  ];
  const FU_DEBRID = [
    { id: 'torbox', name: 'TorBox', rec: true, linked: true, user: 'octavio', until: 'Premium hasta el 14 mar 2027', cache: true },
    { id: 'rd', name: 'Real-Debrid', rec: false, linked: true, user: 'octavio.rd', until: 'Premium vencido el 2 nov 2026', expired: true, cache: false },
    { id: 'ad', name: 'AllDebrid', rec: false, linked: false, cache: false },
    { id: 'pm', name: 'Premiumize', rec: false, linked: false, cache: true },
  ];
  const COMPAT = { full: ['Completa', 'ok'], partial: ['Parcial', 'warn'], unsupported: ['No compatible', 'err'] };
  const avatar = (name) => `<span class="nj-c-avatar" aria-hidden="true">${esc(name[0])}</span>`;

  function fuDetail(ctx, s, x) {
    const cp = COMPAT[x.compat];
    const dom = s.domAll === x.id ? x.domains : x.domains.slice(0, 6);
    const prefs = x.prefs ? `<section class="nj-c-sub"><h3 class="nj-c-label">Preferencias</h3>
      ${tog(ctx, 'pNebula', 'Probar Nebula primero', s.pNebula, 'Se aplica al instante.')}
      <div class="nj-c-field"><span class="nj-c-label">Audio preferido</span>${chips('pAudio', 'Audio preferido', [{ id: 'ja', label: 'Japonés' }, { id: 'es', label: 'Español' }, { id: 'en', label: 'Inglés' }], s.pAudio)}</div>
      <div class="nj-c-field"><span class="nj-c-label">Servidor preferido</span>${chips('pServer', 'Servidor preferido', [{ id: 'auto', label: 'Automático' }, { id: 'nebula', label: 'Nebula' }, { id: 'mirror', label: 'Mirror B' }, { id: 'pahe', label: 'AnimePahe' }], s.pServer)}</div>
      <div class="nj-c-secret">${ic('key', { size: 18 })}<span class="nj-c-grow">Clave de AniList · ${s.pKey ? 'Guardada en el llavero del sistema' : 'Sin clave'}</span>${s.pKey ? btn('Quitar', 'pKeyDel', { kind: 'text' }) : btn('Añadir clave', 'pKeyAdd', { kind: 'text' })}</div></section>` : '';
    return `<section class="nj-c-extdetail" aria-labelledby="${pid(ctx, 'ext-' + x.id)}">
      <header class="nj-c-dhead">${avatar(x.name)}<div class="nj-c-grow"><h2 class="nj-h2" id="${pid(ctx, 'ext-' + x.id)}">${esc(x.name)}</h2><p class="nj-meta">Versión ${esc(x.ver)} · ${esc(x.kinds.join(', '))} · ${esc(x.src)}</p></div>${pill(cp[0], cp[1], x.compat === 'full' ? 'verified' : x.compat === 'partial' ? 'adjust' : 'block')}</header>
      ${x.update ? `<div class="nj-c-banner" data-tone="info">${ic('system_update_alt')}<div class="nj-c-grow"><b>Versión ${esc(x.update)} disponible</b><p class="nj-meta">${esc(x.notes)}</p></div>${btn(s.updating === x.id ? 'Actualizando…' : 'Actualizar', 'extUpdate', { kind: 'tonal', attrs: { 'data-arg': x.id } })}</div>` : ''}
      ${x.compat !== 'full' ? `<div class="nj-c-banner" data-tone="${x.compat === 'partial' ? 'warn' : 'err'}">${ic(x.compat === 'partial' ? 'adjust' : 'block')}<div class="nj-c-grow"><b>${x.compat === 'partial' ? 'Funcionan la búsqueda y los streams; se omite su catálogo propio.' : 'Necesita funciones que esta versión no tiene, así que no se puede activar.'}</b><p class="nj-meta">Detalle técnico: ${esc(x.reason)}</p></div></div>` : ''}
      <p>${esc(x.desc)}</p>
      <section class="nj-c-sub"><h3 class="nj-c-label">${x.perms.length ? 'Permisos y dominios que declara' : 'Sin permisos especiales'}</h3><div class="nj-cluster">${x.perms.map((p) => pill(p, 'neutral', p === 'Internet' ? 'public' : p === 'Usa torrents' ? 'hub' : 'save')).join('')}</div>
        <div class="nj-cluster nj-c-domains">${dom.map((d) => `<span class="nj-c-domain">${ic('language', { size: 15 })}${esc(d)}</span>`).join('')}${x.domCount > dom.length ? `<button type="button" class="nj-chip" data-c="domAll" data-arg="${x.id}">Ver los ${x.domCount}</button>` : ''}</div>
        ${x.browser ? `<p class="nj-meta">${ic('travel_explore', { size: 16 })} ${x.browser} sitios necesitan un navegador oculto. ${A(ctx) ? '' : 'Se usa el Chromium del sistema.'}</p>` : ''}</section>
      ${prefs}
      <dl class="nj-c-kv nj-c-facts"><dt>Actualizada</dt><dd>${esc(x.updated)}</dd><dt>Fuentes</dt><dd>${esc(x.src)}</dd><dt>SHA-256</dt><dd><code>${esc(x.sha)}</code></dd><dt>Dominios</dt><dd>${x.domCount}</dd></dl>
      <details class="nj-c-details"${s.logOpen ? ' open' : ''}><summary>Registro</summary><pre class="nj-c-log">${FU_LOG.map((l) => `<span data-tone="${l.slice(1, l.indexOf(']'))}">${esc(l)}</span>`).join('\n')}</pre></details>
      <div class="nj-c-dangerzone"><div class="nj-c-grow"><b>Quitar ${esc(x.name)}</b><p class="nj-meta">Se borran la extensión y sus datos de este dispositivo.</p></div>${btn('Quitar…', 'open', { kind: 'danger', icon: 'delete', attrs: { 'data-arg': 'extDel' } })}</div>
    </section>`;
  }

  function fuInstalled(ctx, s) {
    const exts = s.exts; const cur = exts.find((x) => x.id === s.extSel) || exts[0];
    const allOff = exts.every((x) => !x.on);
    const ups = exts.filter((x) => x.update).length;
    const list = `<div class="nj-c-drop is-compact${s.kaiHover ? ' is-hover' : ''}" data-kaidrop>${ic('upload_file', { size: 24 })}<div class="nj-c-grow"><b>${A(ctx) ? 'Instala un paquete .kai' : 'Suelta un .kai aquí'}</b><p class="nj-meta">${A(ctx) ? 'Desde tus archivos o desde un enlace.' : 'O elígelo, o pega su enlace o su ruta.'}</p></div>${btn('Elegir archivo', 'kaiPick', { kind: 'tonal' })}${btn(A(ctx) ? 'Desde URL' : 'URL o ruta', 'open', { kind: 'text', attrs: { 'data-arg': 'url' } })}</div>
      ${allOff ? `<div class="nj-c-banner" data-tone="warn">${ic('extension_off')}<div class="nj-c-grow"><b>Todas las extensiones están desactivadas</b><p class="nj-meta">No se buscan fuentes hasta que actives alguna.</p></div>${btn('Activar todas', 'enableAll', { kind: 'tonal' })}</div>` : ''}
      ${ups ? `<div class="nj-c-banner" data-tone="info">${ic('system_update_alt')}<div class="nj-c-grow"><b>${ups} actualizaciones</b></div>${btn('Ver', 'open', { kind: 'text', attrs: { 'data-arg': 'updates' } })}${btn('Actualizar todas', 'updAll', { kind: 'tonal' })}</div>` : ''}
      ${s.installErr ? `<div class="nj-c-banner" data-tone="err">${ic('error')}<div class="nj-c-grow"><b>No se pudo instalar</b><p class="nj-meta">${esc(s.installErr)}</p></div>${btn('Elegir otro', 'kaiPick', { kind: 'text' })}</div>` : ''}
      <ul class="nj-c-extlist" aria-label="Extensiones instaladas">${exts.map((x) => `<li class="nj-c-extrow${x.id === cur.id && !A(ctx) ? ' is-current' : ''}${x.on ? '' : ' is-off'}"><button type="button" class="nj-c-extbtn" data-c="extSel" data-arg="${x.id}" data-fk="x-${x.id}" aria-current="${x.id === cur.id && !A(ctx)}">${avatar(x.name)}<span class="nj-c-grow"><span class="nj-c-qtitle">${esc(x.name)}</span><span class="nj-meta">${esc(x.ver)} · ${esc(x.kinds.join(', '))}${x.update ? ' · ' + esc(x.update) + ' disponible' : ''}</span></span>${pill(COMPAT[x.compat][0], COMPAT[x.compat][1])}</button><label class="nj-c-switch" title="Usar ${esc(x.name)}"><span class="nj-c-sr">Usar ${esc(x.name)}</span><input type="checkbox" role="switch" data-c="extOn" data-arg="${x.id}"${x.on ? ' checked' : ''}${x.compat === 'unsupported' ? ' disabled' : ''}></label></li>`).join('')}</ul>
      <div class="nj-c-dangerzone"><div class="nj-c-grow"><b>${allOff ? 'Activar todas las extensiones' : 'Desactivar todas las extensiones'}</b><p class="nj-meta">No se desinstala nada y se conservan las preferencias.</p></div>${allOff ? btn('Activar todas', 'enableAll', { kind: 'outline' }) : btn('Desactivar todas…', 'open', { kind: 'danger', icon: 'power_settings_new', attrs: { 'data-arg': 'kill' } })}</div>`;
    if (!exts.length) return empty('extension', 'No hay extensiones instaladas', 'Instala un paquete .kai para empezar a ver.', btn('Explorar la tienda', 'struct', { kind: 'tonal', attrs: { 'data-arg': 'tienda' } }));
    if (A(ctx)) return list;
    return `<div class="nj-c-md2"><div class="nj-c-mdleft">${list}</div><div class="nj-c-mdright" data-keep="extd">${fuDetail(ctx, s, cur)}</div></div>`;
  }

  function fuRepos(ctx, s) {
    return `<div class="nj-c-addrow" data-c="noop"><label class="nj-c-grow nj-c-field"><span class="nj-c-label">URL del repositorio</span><span class="nj-field">${ic('link')}<input type="url" data-model="repoUrl" value="${esc(s.repoUrl || '')}" placeholder="https://…/index.json" spellcheck="false"></span></label>${btn('Añadir', 'repoAdd', { kind: 'tonal', icon: 'add' })}</div>
      ${s.repoErr ? `<p class="nj-c-errtext">${esc(s.repoErr)}</p>` : ''}
      <ul class="nj-c-stack">${s.repos.map((r) => `<li class="nj-c-panel"><div class="nj-c-panelhead"><div class="nj-c-grow"><h3 class="nj-h3">${esc(r.name)}</h3><p class="nj-meta">${r.n} extensiones · leído ${esc(r.read)} · <code>${esc(r.url)}</code></p></div>${ibtn('refresh', 'Actualizar ' + r.name, 'repoRefresh', { attrs: { 'data-arg': r.id } })}${ibtn('delete', 'Quitar ' + r.name, 'open', { attrs: { 'data-arg': 'repoDel:' + r.id } })}</div>
        ${r.failed ? `<p class="nj-c-err">${ic('error', { size: 16 })}Última lectura fallida: ${esc(r.failed)}</p>` : ''}
        ${btn('Ver sus extensiones en la tienda', 'struct', { kind: 'text', icon: 'storefront', attrs: { 'data-arg': 'tienda' } })}</li>`).join('') || `<li>${empty('inventory_2', 'Sin repositorios', 'Los repositorios que añadas aparecen aquí con sus extensiones.')}</li>`}</ul>`;
  }

  function fuStremio(ctx, s) {
    return `<div class="nj-c-addrow"><label class="nj-c-grow nj-c-field"><span class="nj-c-label">URL del manifiesto</span><span class="nj-field">${ic('link')}<input type="url" data-model="stUrl" value="${esc(s.stUrl || '')}" placeholder="https://… o stremio://…" spellcheck="false"></span></label>${btn('Instalar', 'stAdd', { kind: 'tonal', icon: 'add' })}</div>
      <ul class="nj-c-stack">${s.stremio.map((a) => `<li class="nj-c-panel"><div class="nj-c-panelhead">${avatar(a.name)}<div class="nj-c-grow"><h3 class="nj-h3">${esc(a.name)}</h3><p class="nj-meta">Versión ${esc(a.ver)} · ${esc(a.types)}</p></div><label class="nj-c-switch"><span class="nj-c-sr">Usar ${esc(a.name)}</span><input type="checkbox" role="switch" data-c="stOn" data-arg="${a.id}"${a.on ? ' checked' : ''}></label></div>
        <p class="nj-meta">${esc(a.desc)}</p>${a.noStreams ? `<p class="nj-c-err">${ic('info', { size: 16 })}Este addon no ofrece streams: no añade fuentes.</p>` : ''}
        <div class="nj-cluster">${btn('Actualizar', 'stUpd', { kind: 'text', icon: 'refresh' })}${btn('Restablecer coincidencias', 'stReset', { kind: 'text', icon: 'restart_alt' })}<span class="nj-c-grow"></span>${btn('Quitar…', 'open', { kind: 'danger', attrs: { 'data-arg': 'stDel:' + a.id } })}</div></li>`).join('') || `<li>${empty('extension', 'No hay addons de Stremio', 'Pega la URL de un manifiesto para añadir uno.')}</li>`}</ul>`;
  }

  function fuProviders(ctx) {
    return `<p class="nj-meta">Catálogos de las extensiones que tienen fuentes propias, incluidas las del servidor de tu PC.</p>
      <ul class="nj-c-plainlist nj-c-provlist">${FU_PROV.map((p) => `<li><button type="button" class="nj-c-provrow" data-c="provOpen" data-arg="${esc(p.name)}">${avatar(p.name)}<span class="nj-c-grow"><span class="nj-c-qtitle">${esc(p.name)}</span><span class="nj-meta">${esc(p.ext)} · ${esc(p.desc)}</span></span>${pill(p.kind, 'accent')}${ic('chevron_right')}</button></li>`).join('')}</ul>`;
  }

  function fuServers(ctx, s) {
    return `<div class="nj-c-panelhead"><p class="nj-meta nj-c-grow">${A(ctx) ? 'Tu PC puede prestarle sus extensiones a este teléfono. Si se apaga, se usan las locales.' : 'Extensiones que otras PC te prestan por la red local.'}</p>${btn('Configurar servidores', 'go', { kind: 'tonal', icon: 'devices', attrs: { 'data-arg': 'link' } })}</div>
      <ul class="nj-c-stack">
        <li class="nj-c-panel"><div class="nj-c-panelhead">${ic('computer')}<div class="nj-c-grow"><h3 class="nj-h3">octavio-pc</h3><p class="nj-meta">3 extensiones · http://192.168.1.20:8099</p></div>${pill('En línea', 'ok')}${ibtn('refresh', 'Actualizar octavio-pc', 'srvRefresh')}</div>
          <ul class="nj-c-plainlist">${[['Toko', '4.7.0', 'anime', true, true], ['Kumo', '1.3.0', 'manga', true, false], ['Hikari', '2.1.0', 'anime', false, false]].map(([n, v, k, on, newer]) => `<li><span class="nj-c-grow"><span class="nj-c-qtitle">${n}</span><span class="nj-meta"> · v${v} · Tatakai · ${k}</span>${newer && !s.srvUpdated ? '<span class="nj-c-warntext">La PC tiene una versión más nueva que la tuya (4.6.2).</span>' : ''}</span>${pill(on ? 'Activa' : 'Desactivada', on ? 'ok' : 'neutral')}${newer && !s.srvUpdated ? btn('Actualizar desde la PC', 'srvUpd', { kind: 'text' }) : ''}</li>`).join('')}</ul>
          ${s.srvUpdated ? `<p class="nj-c-result" role="status">${ic('task_alt', { size: 18 })}Toko se actualizó a 4.7.0 desde la PC.</p>` : ''}</li>
        <li class="nj-c-panel"><div class="nj-c-panelhead">${ic('laptop')}<div class="nj-c-grow"><h3 class="nj-h3">portatil-sala</h3><p class="nj-meta">http://192.168.1.34:8099</p></div>${pill('Sin conexión', 'err')}</div><p class="nj-c-err">${ic('wifi_off', { size: 16 })}No se pudo conectar. ¿Está encendida y en la misma red?</p>${btn('Reintentar', 'srvRefresh', { kind: 'text', icon: 'refresh' })}</li>
      </ul>`;
  }

  function fuDebrid(ctx, s) {
    const ds = s.debrid;
    return `<p class="nj-meta">Con una cuenta debrid, un torrent que el servicio ya tiene se reproduce al instante por HTTPS. ${A(ctx) ? 'La clave se guarda en el Android Keystore.' : 'La clave se guarda cifrada, con su llave en el llavero del sistema.'} El primero vinculado de la lista es el preferido; arrastra para cambiar el orden.</p>
      <div class="nj-c-panel nj-c-tight">${tog(ctx, 'dUse', 'Usar debrid', s.dUse, 'Apagado: no se consulta la caché y todo va por P2P.')}${tog(ctx, 'dPrefer', 'Priorizar fuentes en caché', s.dPrefer && s.dUse, 'Suben en la lista de fuentes y Automático puede elegirlas.')}${tog(ctx, 'dDown', 'Descargar por el servicio si está en caché', s.dDown && s.dUse, 'Baja por HTTPS con las reglas de descargas web en vez de P2P.')}</div>
      <ol class="nj-c-stack nj-c-debridlist" data-dqueue>${ds.map((d, i) => `<li class="nj-c-panel nj-c-debrid" data-id="${d.id}" data-flip="${d.id}" data-fk="db-${d.id}" tabindex="0" draggable="true" aria-label="${esc(d.name)}, prioridad ${i + 1}">
        <div class="nj-c-panelhead"><span class="nj-c-grip" title="Arrastra o usa Alt+↑/↓">${ic('drag_indicator', { size: 20 })}</span><h3 class="nj-h3">${esc(d.name)}</h3>${d.rec ? pill('Recomendado', 'accent', 'verified') : pill('Experimental · sin probar', 'warn', 'science')}<span class="nj-c-grow"></span>${d.linked ? pill('#' + (ds.filter((x) => x.linked).indexOf(d) + 1), i === 0 ? 'accent' : 'neutral') + pill('Vinculado', 'ok', 'link') : pill('Sin vincular', 'neutral')}</div>
        <p class="nj-meta">${d.cache ? 'Muestra qué fuentes están en caché antes de reproducir.' : 'No consulta la caché: se prueba al reproducir y, si no lo tiene, baja ' + (A(ctx) ? 'en el teléfono' : 'en esta computadora') + '.'}</p>
        ${d.linked ? `<p class="nj-meta">Cuenta ${esc(d.user)} · <span class="${d.expired ? 'nj-c-errtext' : ''}">${esc(d.until)}</span> · Clave guardada</p><div class="nj-cluster">${btn(s.testing === d.id ? 'Probando…' : 'Probar la clave', 'dTest', { kind: 'outline', attrs: { 'data-arg': d.id } })}${i > 0 ? btn('Hacer principal', 'dTop', { kind: 'text', icon: 'vertical_align_top', attrs: { 'data-arg': d.id } }) : ''}<span class="nj-c-grow"></span>${btn('Desvincular…', 'open', { kind: 'danger', attrs: { 'data-arg': 'unlink:' + d.id } })}</div>`
          : `<div class="nj-c-addrow"><label class="nj-c-grow nj-c-field"><span class="nj-c-label">Clave de API</span><span class="nj-field">${ic('key')}<input type="password" data-model="key-${d.id}" autocomplete="off" placeholder="Pega la clave de ${esc(d.name)}"></span></label>${btn('Probar y vincular', 'dLink', { kind: 'tonal', attrs: { 'data-arg': d.id } })}</div>${s.dErr === d.id ? '<p class="nj-c-errtext">El servicio no aceptó esta clave.</p>' : ''}${btn('Obtener una clave en ' + d.name, 'dGetKey', { kind: 'text', icon: 'open_in_new' })}`}
      </li>`).join('')}<li class="nj-c-insert" aria-hidden="true"></li></ol>`;
  }

  function fuStore(ctx, s) {
    const f = s.storeF; const q = (s.storeQ || '').toLowerCase();
    const items = FU_STORE.filter((x) => (f === 'todo' || (f === 'upd' ? x.status === 'update' : f === 'inst' ? x.status !== 'new' : x.kinds.includes(f))) && (!s.storeRepo || x.repo === s.storeRepo) && (!q || (x.name + x.desc).toLowerCase().includes(q)));
    const st = (x) => s.installed.includes(x.id) ? 'installed' : x.status;
    return `<div class="nj-c-storetools"><span class="nj-search">${ic('search')}<input type="search" data-model="storeQ" value="${esc(s.storeQ || '')}" placeholder="Buscar extensiones" aria-label="Buscar extensiones"></span>
        ${chips('storeF', 'Filtrar', [{ id: 'todo', label: 'Todo' }, { id: 'Anime', label: 'Anime' }, { id: 'Manga', label: 'Manga' }, { id: 'Novela', label: 'Novelas' }, { id: 'upd', label: 'Con actualización', count: 2 }, { id: 'inst', label: 'Instaladas' }], f)}
        <div class="nj-c-chipw" data-c="chip" data-key="storeRepo" data-optional>${h.chips('Repositorio', FU_REPOS.map((r) => ({ id: r.name, label: r.name, icon: 'inventory_2' })), s.storeRepo ? [s.storeRepo] : [])}</div></div>
      ${items.length ? `<ul class="nj-c-storegrid" aria-label="Extensiones disponibles">${items.map((x) => { const status = st(x); return `<li class="nj-c-tile"><div class="nj-c-tilehead">${avatar(x.name)}<div class="nj-c-grow"><h3 class="nj-h3">${esc(x.name)}</h3><p class="nj-meta">v${esc(x.ver)} · ${esc(x.repo)}</p></div>${x.compat ? pill('Parcial', 'warn') : ''}</div>
        <p class="nj-c-tiledesc">${esc(x.desc)}</p><div class="nj-cluster">${x.kinds.map((k) => pill(k, 'accent')).join('')}</div>
        <p class="nj-meta nj-c-tileperm">${ic('shield', { size: 16 })}${esc(x.perms.join(', '))} · declara ${x.dom} ${x.dom === 1 ? 'dominio' : 'dominios'}</p>
        <div class="nj-c-tileact">${status === 'installed' ? `${pill('Instalada', 'ok', 'check')}${btn('Abrir', 'storeOpen', { kind: 'text', attrs: { 'data-arg': x.id } })}` : status === 'update' ? btn('Actualizar a ' + x.ver, 'open', { kind: 'tonal', icon: 'system_update_alt', attrs: { 'data-arg': 'perm:' + x.id } }) : btn('Instalar', 'open', { kind: 'filled', icon: 'download', attrs: { 'data-arg': 'perm:' + x.id } })}</div></li>`; }).join('')}</ul>`
        : empty('search_off', 'Ninguna extensión coincide', 'Prueba con otro filtro o quita la búsqueda.', btn('Quitar filtros', 'storeClear', { kind: 'text' }))}`;
  }

  NJ.registerScreen({
    id: 'fuentes', name: 'Fuentes', group: 'Fuentes y equipo', order: 2, platforms: ['desktop', 'android'],
    summary: 'El scroll largo de Extensiones con cinco conceptos se parte en pestañas, suma una tienda con permisos visibles y trae Debrid desde Torrents.',
    parity: 'EXT-01…EXT-15 (EXT-16 en Ajustes › Fuentes de episodios), TOR-21, TOR-22, LNK-12/13/15 resumidos (configuración completa en Link)',
    structures: [
      { id: 'pestanas', name: 'Pestañas', desc: 'Instaladas, Repositorios, Stremio, Proveedores, Servidores en tu PC y Debrid, cada una en su lugar.' },
      { id: 'tienda', name: 'Tienda', desc: 'Rejilla de extensiones de tus repositorios con instalar, actualizar y permisos declarados antes de instalar.' },
    ],
    render(ctx) {
      const s = init(ctx, { tab: 'instaladas', exts: FU_EXTS, extSel: 'toko', repos: FU_REPOS, stremio: FU_STREMIO, debrid: FU_DEBRID, installed: [], storeF: 'todo', storeRepo: null, pNebula: true, pAudio: 'ja', pServer: 'auto', pKey: true, dUse: true, dPrefer: true, dDown: true, updCheck: true });
      const isA = A(ctx);
      const ups = s.exts.filter((x) => x.update).length;
      const TABS = [['instaladas', 'Instaladas', s.exts.length], ['repos', 'Repositorios', s.repos.length], ['stremio', 'Stremio', s.stremio.length], ['prov', 'Proveedores', FU_PROV.length], ['servidores', isA ? 'Extensiones de tu PC' : 'Servidores en tu PC', 2], ['debrid', 'Debrid', s.debrid.filter((d) => d.linked).length]];
      const head = pageHead(ctx, {
        title: 'Fuentes', meta: `${s.exts.length} extensiones · ${s.exts.filter((x) => x.compat === 'partial').length} parcial · ${ups} actualizaciones`,
        actions: `${btn(ups ? `Actualizaciones · ${ups}` : 'Buscar actualizaciones', 'open', { kind: 'outline', icon: 'system_update_alt', attrs: { 'data-arg': 'updates' } })}${btn('Instalar .kai', 'open', { kind: 'filled', icon: 'add', attrs: { 'data-arg': 'url' } })}`,
        actionsA: `<span class="nj-c-badgewrap">${ibtn('system_update_alt', `Actualizaciones (${ups})`, 'open', { attrs: { 'data-arg': 'updates' } })}${ups ? `<span class="nj-c-dotcount" aria-hidden="true">${ups}</span>` : ''}</span>${ibtn('storefront', 'Tienda', 'struct', { attrs: { 'data-arg': ctx.structure === 'tienda' ? 'pestanas' : 'tienda' } })}`,
      });
      let body = '';
      if (ctx.structure === 'tienda') {
        body = `<div class="nj-c-body nj-c-pad" data-keep="main">${isA ? '' : `<div class="nj-c-sectionhead"><h2 class="nj-h2">Tienda</h2><span class="nj-meta">${FU_STORE.length} extensiones de ${FU_REPOS.length} repositorios</span>${btn('Ver instaladas', 'struct', { kind: 'text', icon: 'extension', attrs: { 'data-arg': 'pestanas' } })}</div>`}${fuStore(ctx, s)}</div>`;
      } else {
        const t = s.tab;
        const content = t === 'instaladas' ? fuInstalled(ctx, s) : t === 'repos' ? fuRepos(ctx, s) : t === 'stremio' ? fuStremio(ctx, s) : t === 'prov' ? fuProviders(ctx) : t === 'servidores' ? fuServers(ctx, s) : fuDebrid(ctx, s);
        body = `<div class="nj-tabs nj-c-ftabs" role="tablist" aria-label="Secciones de Fuentes">${TABS.map(([id, l, n]) => `<button type="button" class="nj-tab" role="tab" aria-selected="${t === id}" data-c="tab" data-arg="${id}">${l}<span class="nj-c-tabcount">${n}</span></button>`).join('')}</div>
          <div class="nj-c-body nj-c-pad${s._enter ? ' is-enter' : ''}" role="tabpanel" data-keep="main-${t}">${content}${isA ? '<div class="nj-c-fabspace"></div>' : ''}</div>`;
      }
      let ov = '';
      const ext = s.exts.find((x) => x.id === s.extSel) || s.exts[0];
      if (s.ov === 'url') ov = sheetHTML(ctx, { id: 'url', title: 'Instalar una extensión', body: `<div class="nj-c-drop${s.kaiHover ? ' is-hover' : ''}" data-kaidrop>${ic('upload_file', { size: 28 })}<div><b>${isA ? 'Elige un paquete .kai' : 'Suelta un paquete .kai aquí'}</b><p class="nj-meta">Instala solo extensiones en las que confíes.</p></div>${btn('Elegir archivo', 'kaiPick', { kind: 'tonal', icon: 'attach_file' })}</div>
          <label class="nj-c-field"><span class="nj-c-label">${isA ? 'Enlace' : 'Enlace o ruta'}</span><span class="nj-field">${ic('link')}<input type="text" data-model="kaiUrl" value="${esc(s.kaiUrl || '')}" placeholder="${isA ? 'https://…/extension.kai' : 'https://…/extension.kai o ~/Descargas/extension.kai'}" spellcheck="false" data-autofocus></span></label>
          <p class="nj-meta">Acepta un enlace a un .kai o al manifest.json de al lado${isA ? '' : ', o la ruta de un .kai en este equipo'}.</p>${s.kaiErr ? `<p class="nj-c-errtext">${esc(s.kaiErr)}</p>` : ''}`,
        foot: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Instalar', 'kaiUrlGo', { kind: 'filled' })}` });
      else if (s.ov === 'updates') ov = sheetHTML(ctx, { id: 'updates', title: 'Actualizaciones', body: `${tog(ctx, 'updCheck', 'Buscar actualizaciones al abrir la app', s.updCheck)}<div class="nj-cluster">${btn(s.checkingUpd ? 'Buscando…' : 'Buscar ahora', 'updCheckNow', { kind: 'outline', icon: 'refresh' })}${ups > 1 ? btn('Actualizar todas', 'updAll', { kind: 'tonal' }) : ''}</div>
          ${ups ? `<ul class="nj-c-plainlist">${s.exts.filter((x) => x.update).map((x) => `<li class="nj-c-updrow">${avatar(x.name)}<div class="nj-c-grow"><div class="nj-c-qtitle">${esc(x.name)} · ${esc(x.ver)} → ${esc(x.update)}</div><p class="nj-meta">${esc(x.notes)}</p></div>${btn('Actualizar', 'extUpdate', { kind: 'tonal', attrs: { 'data-arg': x.id } })}</li>`).join('')}</ul>` : empty('task_alt', 'Todo al día', 'Todas las extensiones tienen su última versión.')}` });
      else if (s.ov && s.ov.startsWith('perm:')) {
        const x = FU_STORE.find((y) => y.id === s.ov.slice(5));
        ov = sheetHTML(ctx, { id: s.ov, title: (x.status === 'update' ? 'Actualizar ' : 'Instalar ') + x.name, sub: `v${esc(x.ver)} de ${esc(x.repo)}`, body: `<p>${esc(x.desc)}</p><section class="nj-c-sub"><h3 class="nj-c-label">Pide estos permisos</h3><ul class="nj-c-plainlist">${x.perms.map((p) => `<li>${ic(p === 'Internet' ? 'public' : p === 'Usa torrents' ? 'hub' : 'save')}<span class="nj-c-grow">${esc(p)}</span></li>`).join('')}<li>${ic('language')}<span class="nj-c-grow">Se conecta a ${x.dom} ${x.dom === 1 ? 'dominio declarado' : 'dominios declarados'}</span></li></ul></section>${x.compat ? `<div class="nj-c-banner" data-tone="warn">${ic('adjust')}<div class="nj-c-grow"><b>Compatibilidad parcial</b><p class="nj-meta">Funcionan la búsqueda y los streams; su catálogo propio se omite.</p></div></div>` : ''}`,
          foot: `${btn('Cancelar', 'close', { kind: 'text' })}${btn(x.status === 'update' ? 'Actualizar' : 'Instalar', 'storeInstall', { kind: 'filled', attrs: { 'data-arg': x.id } })}` });
      } else if (s.ov === 'kill') ov = dialogHTML(ctx, { id: 'kill', title: '¿Desactivar todas las extensiones?', icon: 'power_settings_new', tone: 'err', body: '<p>No se desinstala nada y se conservan las preferencias. Mientras estén desactivadas no se buscan fuentes.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Desactivar todas', 'killGo', { kind: 'danger' })}` });
      else if (s.ov === 'extDel') ov = dialogHTML(ctx, { id: 'extDel', title: `¿Quitar ${ext.name}?`, icon: 'delete', tone: 'err', body: '<p>Se borran la extensión y sus datos de este dispositivo.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Quitar', 'extDelGo', { kind: 'danger' })}` });
      else if (s.ov && s.ov.startsWith('repoDel:')) ov = dialogHTML(ctx, { id: 'repoDel', title: '¿Quitar el repositorio?', icon: 'delete', tone: 'err', body: '<p>Las extensiones instaladas se quedan, pero dejan de recibir actualizaciones de aquí.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Quitar', 'repoDelGo', { kind: 'danger' })}` });
      else if (s.ov && s.ov.startsWith('stDel:')) ov = dialogHTML(ctx, { id: 'stDel', title: '¿Quitar el addon?', icon: 'delete', tone: 'err', body: '<p>Deja de aportar fuentes y catálogos.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Quitar', 'stDelGo', { kind: 'danger' })}` });
      else if (s.ov && s.ov.startsWith('unlink:')) { const d = s.debrid.find((x) => x.id === s.ov.slice(7)); ov = dialogHTML(ctx, { id: 'unlink', title: `¿Desvincular ${d.name}?`, icon: 'link_off', tone: 'err', body: '<p>Se borra la clave de este dispositivo. Para volver a usarlo tendrás que pegarla de nuevo.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Desvincular', 'dUnlink', { kind: 'danger', attrs: { 'data-arg': d.id } })}` }); }
      else if (s.ov === 'extA' && isA) ov = sheetHTML(ctx, { id: 'extA', title: ext.name, body: fuDetail(ctx, s, ext).replace(/<header class="nj-c-dhead">[\s\S]*?<\/header>/, `<p class="nj-meta">Versión ${esc(ext.ver)} · ${esc(ext.kinds.join(', '))}</p>`) });
      const fab = isA && ctx.structure !== 'tienda' && s.tab === 'instaladas' ? `<button type="button" class="nj-c-fab" data-c="open" data-arg="url">${ic('add')}Instalar .kai</button>` : '';
      const dropov = isA ? '' : `<div class="nj-c-dropov" aria-hidden="true">${ic('extension', { size: 40 })}<p class="nj-h2">Suelta el .kai para instalarlo</p></div>`;
      return `<div class="nj-c-root nj-c-fu" data-local data-cards-local>${head}${body}${fab}${ov}${snackHTML(s)}${dropov}</div>`;
    },
    mount(el, ctx) {
      const s = ctx.state; const root = el.querySelector('.nj-c-root'); if (!root) return;
      const re = () => rr(ctx, el);
      const H = {
        tab(t) { s.tab = t.getAttribute('data-arg'); s._enter = true; re(); },
        struct(t) { NJ.go('fuentes', { structure: t.getAttribute('data-arg') }); },
        extSel(t) { s.extSel = t.getAttribute('data-arg'); if (A(ctx)) openOv(ctx, 'extA'); re(); },
        extOn(t) { const x = s.exts.find((y) => y.id === t.getAttribute('data-arg')); if (x) { x.on = t.checked; snack(ctx, (x.on ? 'Activada: ' : 'Desactivada: ') + x.name); } re(); },
        $toggle(k) { if (['dUse'].includes(k)) re(); },
        domAll(t) { s.domAll = t.getAttribute('data-arg'); re(); },
        pKeyDel() { s.pKey = false; snack(ctx, 'Clave quitada'); re(); },
        pKeyAdd() { s.pKey = true; snack(ctx, 'Clave guardada'); re(); },
        extUpdate(t) { const x = s.exts.find((y) => y.id === t.getAttribute('data-arg')); if (x) { x.ver = x.update; x.update = null; snack(ctx, `${x.name} se actualizó a ${x.ver}`); } re(); },
        updAll() { const n = s.exts.filter((x) => x.update).length; s.exts.forEach((x) => { if (x.update) { x.ver = x.update; x.update = null; } }); s.ov = null; snack(ctx, `Se actualizaron ${n} extensiones`); re(); },
        updCheckNow() { snack(ctx, 'Se encontraron 0 actualizaciones nuevas'); re(); },
        enableAll() { s.exts.forEach((x) => { if (x.compat !== 'unsupported') x.on = true; }); snack(ctx, 'Se activaron todas las extensiones'); re(); },
        killGo() { s.exts.forEach((x) => { x.on = false; }); s.ov = null; snack(ctx, 'Se desactivaron todas las extensiones', 'Deshacer', 'enableAll'); re(); },
        extDelGo() { const x = s.exts.find((y) => y.id === s.extSel); s._undo = s.exts.slice(); s.exts = s.exts.filter((y) => y !== x); s.extSel = (s.exts[0] || {}).id; s.ov = null; snack(ctx, `Se quitó ${x.name}`); re(); },
        kaiPick() { s.ov = null; s.exts.push({ id: 'shiori', name: 'Shiori', ver: '1.0.4', compat: 'full', kinds: ['Novela'], src: 'capítulos de texto', on: true, desc: 'Capítulos de novela con traductor y fecha.', perms: ['Internet'], domains: ['shiori.example.org'], domCount: 1, sha: '7c2e…a910', updated: 'ahora', browser: 0, prefs: false }); s.installed.push('shiori'); s.extSel = 'shiori'; s.tab = 'instaladas'; snack(ctx, 'Se instaló Shiori 1.0.4'); re(); },
        kaiUrlGo() { const v = (s.kaiUrl || '').trim(); if (!/^https?:\/\//.test(v) && !(!A(ctx) && /\.kai$/i.test(v))) { s.kaiErr = A(ctx) ? 'Escribe un enlace http(s).' : 'Escribe un enlace http(s) o la ruta de un archivo .kai.'; re(); return; } s.kaiErr = null; H.kaiPick(); },
        storeInstall(t) { const id = t.getAttribute('data-arg'); const x = FU_STORE.find((y) => y.id === id); s.installed.push(id); const e = s.exts.find((y) => y.id === id); if (e) { e.ver = x.ver; e.update = null; } s.ov = null; snack(ctx, `${x.name} ${x.ver} instalada`, 'Abrir', 'storeOpenLast'); s._last = id; re(); },
        storeOpenLast() { s.snack = null; if (s.exts.some((x) => x.id === s._last)) s.extSel = s._last; s.tab = 'instaladas'; NJ.go('fuentes', { structure: 'pestanas' }); },
        storeOpen(t) { s.extSel = t.getAttribute('data-arg'); s.tab = 'instaladas'; NJ.go('fuentes', { structure: 'pestanas' }); },
        storeClear() { s.storeF = 'todo'; s.storeRepo = null; s.storeQ = ''; re(); },
        repoAdd() { const v = (s.repoUrl || '').trim(); if (!/^https?:\/\/\S+/.test(v)) { s.repoErr = 'Escribe un enlace http(s) válido.'; re(); return; } s.repoErr = null; s.repos.push({ id: 'r' + Date.now(), name: 'Repositorio nuevo', url: v, n: 6, read: 'ahora' }); s.repoUrl = ''; snack(ctx, 'Repositorio añadido con 6 extensiones'); re(); },
        repoRefresh() { snack(ctx, 'Repositorio leído de nuevo'); re(); },
        repoDelGo() { const id = s.ov.slice(8); s.repos = s.repos.filter((r) => r.id !== id); s.ov = null; snack(ctx, 'Repositorio quitado'); re(); },
        stAdd() { const v = (s.stUrl || '').trim(); if (!/^(https?|stremio):\/\//.test(v)) { snack(ctx, 'Escribe un enlace http(s) o stremio://'); re(); return; } s.stremio.push({ id: 's' + Date.now(), name: 'Addon nuevo', ver: '1.0.0', types: 'Series', on: true, desc: 'Instalado desde un manifiesto.' }); s.stUrl = ''; re(); },
        stOn(t) { const a = s.stremio.find((x) => x.id === t.getAttribute('data-arg')); if (a) a.on = t.checked; },
        stUpd() { snack(ctx, 'El addon ya tiene su última versión'); re(); },
        stReset() { snack(ctx, 'Coincidencias de títulos restablecidas'); re(); },
        stDelGo() { const id = s.ov.slice(6); s.stremio = s.stremio.filter((a) => a.id !== id); s.ov = null; re(); },
        provOpen(t) { snack(ctx, 'Se abre el catálogo de ' + t.getAttribute('data-arg')); re(); },
        srvRefresh() { snack(ctx, 'Servidores revisados'); re(); },
        srvUpd() { s.srvUpdated = true; const x = s.exts.find((y) => y.id === 'toko'); if (x) { x.ver = '4.7.0'; x.update = null; } re(); },
        dTest(t) { snack(ctx, 'TorBox: la clave funciona (octavio)'); re(); },
        dTop(t) { flipSave(ctx, el); const id = t.getAttribute('data-arg'); const d = s.debrid.find((x) => x.id === id); s.debrid = [d].concat(s.debrid.filter((x) => x !== d)); re(); },
        dLink(t) { const id = t.getAttribute('data-arg'); const v = s['key-' + id]; if (!v || v.length < 8) { s.dErr = id; re(); return; } s.dErr = null; const d = s.debrid.find((x) => x.id === id); d.linked = true; d.user = 'octavio'; d.until = 'Premium hasta el 30 ene 2027'; snack(ctx, `${d.name} vinculado como octavio`); re(); },
        dUnlink(t) { const d = s.debrid.find((x) => x.id === t.getAttribute('data-arg')); d.linked = false; s.ov = null; snack(ctx, `${d.name} desvinculado`); re(); },
        dGetKey() { snack(ctx, 'Se abre la página de claves del servicio en el navegador'); re(); },
        $key(e) {
          const row = e.target.closest && e.target.closest('.nj-c-debrid'); if (!row || e.target !== row || !e.altKey) return;
          if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return; e.preventDefault();
          const i = s.debrid.findIndex((x) => x.id === row.getAttribute('data-id')); const j = e.key === 'ArrowUp' ? i - 1 : i + 1; if (j < 0 || j >= s.debrid.length) return;
          flipSave(ctx, el); const a = s.debrid.slice(); const [m] = a.splice(i, 1); a.splice(j, 0, m); s.debrid = a; s._focus = 'db-' + m.id; ctx.rerender();
        },
      };
      wire(el, ctx, H);
      flipPlay(el, ctx);
      s._enter = false;
      /* arrastrar para cambiar la prioridad de debrid */
      const list = el.querySelector('[data-dqueue]');
      if (list) {
        const bar = list.querySelector('.nj-c-insert'); let drag = null; let at = -1;
        list.addEventListener('dragstart', (e) => { const r = e.target.closest('.nj-c-debrid'); if (!r) return; drag = r.getAttribute('data-id'); e.dataTransfer.effectAllowed = 'move'; try { e.dataTransfer.setData('text/x-nj-row', drag); } catch (er) { /* nada */ } r.classList.add('is-dragging'); });
        list.addEventListener('dragover', (e) => { if (!drag) return; e.preventDefault(); const rows = Array.from(list.querySelectorAll('.nj-c-debrid')); const lr = list.getBoundingClientRect(); const sc = lr.height / (list.offsetHeight || 1) || 1; at = rows.length; for (let i = 0; i < rows.length; i++) { const r = rows[i].getBoundingClientRect(); if (e.clientY < r.top + r.height / 2) { at = i; break; } } const ref = rows[Math.min(at, rows.length - 1)].getBoundingClientRect(); const y = ((at >= rows.length ? ref.bottom + 6 : ref.top - 6) - lr.top) / sc; bar.style.transform = `translateY(${y}px)`; bar.classList.add('is-on'); });
        list.addEventListener('drop', (e) => { if (!drag) return; e.preventDefault(); flipSave(ctx, el); const d = s.debrid.find((x) => x.id === drag); const i = s.debrid.indexOf(d); const a = s.debrid.filter((x) => x !== d); a.splice(at > i ? at - 1 : at, 0, d); s.debrid = a; drag = null; ctx.rerender(); });
        list.addEventListener('dragend', () => { drag = null; bar.classList.remove('is-on'); list.querySelectorAll('.is-dragging').forEach((r) => r.classList.remove('is-dragging')); });
      }
      /* soltar un .kai (escritorio) */
      if (!A(ctx)) {
        let depth = 0; const isFile = (e) => e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');
        root.addEventListener('dragenter', (e) => { if (!isFile(e)) return; depth++; root.classList.add('is-filedrag'); });
        root.addEventListener('dragleave', (e) => { if (!isFile(e)) return; depth = Math.max(0, depth - 1); if (!depth) root.classList.remove('is-filedrag'); });
        root.addEventListener('dragover', (e) => { if (isFile(e)) e.preventDefault(); });
        root.addEventListener('drop', (e) => { if (!isFile(e)) return; e.preventDefault(); depth = 0; root.classList.remove('is-filedrag'); const f = Array.from(e.dataTransfer.files || []).find((x) => /\.kai$/i.test(x.name)); if (f) H.kaiPick(); else { s.installErr = 'Elige un archivo con la extensión .kai.'; s.tab = 'instaladas'; re(); } });
      }
    },
  });

  /* =====================================================================
     3. NIJIYOME LINK: servidor en la PC (escritorio) y cliente (Android)
     ===================================================================== */
  const L = D.link || { pc: 'octavio-pc', phone: 'moto g51 5G', tablet: 'SM-X810', latency: '14 ms', ram: '1,2 / 2 GB' };
  const LN_GOALS = [
    { id: 'nitidez', label: 'Nitidez', icon: 'hd', desc: 'Líneas limpias y más detalle', up: 'a4k1440', fg: 'off', sustain: 'ok' },
    { id: 'fluidez', label: 'Fluidez', icon: 'slow_motion_video', desc: 'Movimiento suave en paneos', up: 'fsr', fg: 'x2', sustain: 'ok' },
    { id: 'equilibrio', label: 'Equilibrio', icon: 'tune', desc: 'Un poco de cada uno', up: 'a4k1080', fg: 'x2', sustain: 'warn' },
    { id: 'bateria', label: 'Batería', icon: 'battery_saver', desc: 'Sin procesar: menos datos y consumo', up: 'none', fg: 'off', sustain: 'ok' },
  ];
  const LN_UP = [{ id: 'none', label: 'Sin escalado' }, { id: 'fsr', label: 'FSR' }, { id: 'a4k1080', label: 'Anime4K a 1080p' }, { id: 'a4k1440', label: 'Anime4K a 1440p' }, { id: 'a4k2160', label: 'Anime4K a 2160p', off: 'Tu teléfono no reproduce 2160p' }];
  const LN_FG = [{ id: 'off', label: 'Sin cuadros extra' }, { id: 'x2', label: '×2' }, { id: 'x3', label: '×3', off: 'No se sostiene en tiempo real' }, { id: 'auto', label: 'Automático (60 fps)' }];
  const LN_LOG = ['21:03:58 · session.start · /v1/stream · moto g51 5G · 1 · 42 ms', '21:03:41 · pair.ok · /v1/pair · SM-X810 · 1 · 18 ms', '21:01:12 · ext.list · /ext/v1/list · moto g51 5G · 3 · 9 ms', '20:58:30 · auth.fail · /ext/v1/list · desconocido · 1 · 2 ms · contraseña incorrecta'];

  function qrSVG(seed) {
    let x = seed || 7; const n = 25; let cells = '';
    const finder = (cx, cy) => `<rect x="${cx}" y="${cy}" width="7" height="7" class="nj-c-qrdark"/><rect x="${cx + 1}" y="${cy + 1}" width="5" height="5" class="nj-c-qrlight"/><rect x="${cx + 2}" y="${cy + 2}" width="3" height="3" class="nj-c-qrdark"/>`;
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
      if ((r < 8 && c < 8) || (r < 8 && c > n - 9) || (r > n - 9 && c < 8)) continue;
      x = (x * 1103515245 + 12345) & 0x7fffffff; if (x % 7 < 3) cells += `<rect x="${c}" y="${r}" width="1" height="1"/>`;
    }
    return `<svg class="nj-c-qr" viewBox="-2 -2 29 29" role="img" aria-label="Código QR de ejemplo para emparejar"><rect x="-2" y="-2" width="29" height="29" class="nj-c-qrlight"/><g class="nj-c-qrdark">${cells}</g>${finder(0, 0)}${finder(n - 7, 0)}${finder(0, n - 7)}</svg>`;
  }

  function lnGoalPicker(ctx, s) {
    const cur = LN_GOALS.find((g) => g.up === s.up && g.fg === s.fg);
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'goal')}"><div class="nj-c-panelhead"><h2 class="nj-h3" id="${pid(ctx, 'goal')}">Perfil de mejora</h2>${cur ? '' : pill('Personalizado', 'accent', 'tune')}</div>
      <p class="nj-meta">Qué hace ${A(ctx) ? 'la PC con lo que ves en este teléfono' : 'esta PC con lo que ven tus dispositivos'}. Se mide en tu equipo antes de ofrecerlo.</p>
      <div class="nj-c-goals" role="radiogroup" aria-label="Objetivo de la mejora">${LN_GOALS.map((g) => `<button type="button" role="radio" aria-checked="${cur === g}" class="nj-c-goal" data-c="goal" data-arg="${g.id}">${ic(g.icon, { size: 26, fill: cur === g })}<span class="nj-c-goalname">${g.label}</span><span class="nj-meta">${g.desc}</span>${pill(g.sustain === 'ok' ? 'Sostenido en tiempo real' : 'Puede trabarse', g.sustain === 'ok' ? 'ok' : 'warn')}</button>`).join('')}</div>
      <details class="nj-c-details"${s.techOpen ? ' open' : ''} data-c="techToggle"><summary>Detalle técnico</summary>
        <div class="nj-c-field"><span class="nj-c-label">Escalado</span><div class="nj-cluster" role="radiogroup" aria-label="Escalado">${LN_UP.map((o) => `<button type="button" class="nj-chip" role="radio" aria-checked="${s.up === o.id}" aria-pressed="${s.up === o.id}" data-c="upPick" data-arg="${o.id}"${o.off ? ` disabled title="${esc(o.off)}"` : ''}>${esc(o.label)}</button>`).join('')}</div>${s.up === 'a4k2160' ? '' : '<p class="nj-meta">Anime4K a 2160p no aparece activo: tu teléfono no reproduce 2160p.</p>'}</div>
        <div class="nj-c-field"><span class="nj-c-label">Generación de cuadros (lsfg-vk)</span><div class="nj-cluster" role="radiogroup" aria-label="Generación de cuadros">${LN_FG.map((o) => `<button type="button" class="nj-chip" role="radio" aria-checked="${s.fg === o.id}" aria-pressed="${s.fg === o.id}" data-c="fgPick" data-arg="${o.id}"${o.off ? ` disabled title="${esc(o.off)}"` : ''}>${esc(o.label)}</button>`).join('')}</div></div>
        <p class="nj-c-num">Salida: ${s.up === 'a4k1440' ? '1440p' : '1080p'} · ${s.fg === 'off' ? '23,98' : s.fg === 'x2' ? '47,95' : '60'} fps · HEVC por NVENC</p>
      </details></section>`;
  }

  function lnMap(ctx, s) {
    const devs = [
      { id: 'phone', name: L.phone, icon: 'smartphone', state: s.devState.phone, x: 76, y: 24, sub: s.devState.phone === 'connected' ? 'Viendo Ao no Hako 2 · Ep. 8' : 'Visto hace 3 h' },
      { id: 'tablet', name: L.tablet, icon: 'tablet_android', state: s.devState.tablet, x: 76, y: 78, sub: s.devState.tablet === 'connected' ? 'Conectada' : s.devState.tablet === 'failed' ? 'No responde' : 'Visto hace 3 h' },
    ];
    const pc = { x: 24, y: 50 };
    const lines = devs.map((d) => `<path d="M${pc.x} ${pc.y} C ${(pc.x + d.x) / 2} ${pc.y}, ${(pc.x + d.x) / 2} ${d.y}, ${d.x} ${d.y}" class="nj-c-wire" data-state="${d.state}" vector-effect="non-scaling-stroke"/>`).join('');
    return `<div class="nj-c-map" data-region="link" data-state="${s.linkOn ? 'connected' : 'idle'}" aria-label="Mapa de dispositivos de Link">
      <svg class="nj-c-mapsvg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">${lines}</svg>
      <button type="button" class="nj-c-node is-hub" data-device="pc" data-state="${s.linkOn ? 'connected' : 'idle'}" style="left:${pc.x}%;top:${pc.y}%" data-c="devSel" data-arg="pc" aria-pressed="${s.dev === 'pc'}">${ic('desktop_windows', { size: 28 })}<span class="nj-c-nodename">${esc(L.pc)}</span><span class="nj-meta">${s.linkOn ? 'Esta PC · puerto ' + s.port : 'Link apagado'}</span></button>
      ${devs.map((d) => `<button type="button" class="nj-c-node" data-device="${d.id}" data-state="${d.state}" style="left:${d.x}%;top:${d.y}%" data-c="devSel" data-arg="${d.id}" aria-pressed="${s.dev === d.id}">${ic(d.icon, { size: 24 })}<span class="nj-c-nodename">${esc(d.name)}</span><span class="nj-meta">${esc(d.sub)}</span></button>`).join('')}
      ${s.devState.phone === 'connected' ? `<span class="nj-c-wirelabel" style="left:50%;top:30%">${ic('auto_awesome_motion', { size: 16 })}Anime4K 1440p · ×2 · ${esc(L.latency)}</span>` : ''}
      <button type="button" class="nj-c-node is-ghost" style="left:24%;top:88%" data-c="open" data-arg="pair">${ic('add', { size: 22 })}<span class="nj-c-nodename">Emparejar</span></button>
    </div>`;
  }

  function lnDevicePanel(ctx, s) {
    if (s.dev === 'pc') return `<section class="nj-c-panel"><div class="nj-c-panelhead">${ic('desktop_windows')}<h2 class="nj-h3 nj-c-grow">${esc(L.pc)}</h2>${pill(s.linkOn ? 'Escuchando' : 'Apagado', s.linkOn ? 'ok' : 'neutral')}</div>
      <dl class="nj-c-kv"><dt>Direcciones</dt><dd><code>192.168.1.20</code> · <code>100.64.0.7</code></dd><dt>Puerto</dt><dd>${s.port}</dd><dt>Equipo</dt><dd>RTX 4070 · NVENC · 9 perfiles de mejora</dd><dt>FFmpeg</dt><dd>7.1 encontrado</dd></dl>
      <div class="nj-c-hash"><span class="nj-c-label">Huella</span><code>3F9A 71C2 0B8E D4A6</code></div><p class="nj-meta">Tu teléfono muestra la misma huella antes de emparejar.</p></section>`;
    const d = s.devs[s.dev];
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'dev')}"><div class="nj-c-panelhead">${ic(s.dev === 'phone' ? 'smartphone' : 'tablet_android')}<h2 class="nj-h3 nj-c-grow" id="${pid(ctx, 'dev')}">${esc(d.name)}</h2>${pill(s.devState[s.dev] === 'connected' ? 'Conectado ahora' : 'Visto hace 3 h', s.devState[s.dev] === 'connected' ? 'ok' : 'neutral')}</div>
      <p class="nj-meta">${d.hist} entradas de historial aportadas · emparejado hace ${esc(d.since)}</p>
      <div class="nj-c-field"><span class="nj-c-label">Historial con este dispositivo</span>${seg('hist-' + s.dev, 'Historial', [{ id: 'full', label: 'Completo' }, { id: 'recv', label: 'Solo recibir' }, { id: 'pause', label: 'En pausa' }], d.histMode)}</div>
      <div class="nj-cluster">${d.histMode === 'full' ? btn(s.resync ? 'Enviando todo su historial…' : 'Usar como origen', 'devSource', { kind: 'outline', icon: 'sync_alt' }) : ''}${btn('Renombrar', 'open', { kind: 'text', icon: 'edit', attrs: { 'data-arg': 'rename' } })}${btn('Ver sus registros', 'devLogs', { kind: 'text', icon: 'receipt_long' })}</div>
      <div class="nj-c-dangerzone"><div class="nj-c-grow"><b>Revocar acceso</b><p class="nj-meta">Tendrá que volver a emparejarse.</p></div>${btn('Revocar…', 'open', { kind: 'danger', attrs: { 'data-arg': 'revoke' } })}</div></section>`;
  }

  function lnSessions(ctx, s, bare) {
    if (s.devState.phone !== 'connected') return `<section class="nj-c-panel"><h2 class="nj-h3">Sesiones activas</h2>${empty('cast', 'Ningún dispositivo está viendo ahora', 'Cuando un teléfono reproduzca algo por esta PC, aquí ves su caché y lo que hace la PC.')}</section>`;
    return `<section class="${bare ? 'nj-c-plain' : 'nj-c-panel'}" aria-labelledby="${pid(ctx, 'ses')}">${bare ? '' : `<div class="nj-c-panelhead"><h2 class="nj-h3 nj-c-grow" id="${pid(ctx, 'ses')}">Sesiones activas · 1</h2>${btn('Abrir monitor', 'open', { kind: 'text', icon: 'monitoring', attrs: { 'data-arg': 'monitor' } })}</div>`}
      <div class="nj-c-session"><div class="nj-c-panelhead">${pill(L.phone, 'accent', 'smartphone')}${pill('Procesando', 'ok')}${pill('Torrent', 'neutral', 'hub')}<span class="nj-c-grow"></span>${btn('Detener', 'sesStop', { kind: 'danger', icon: 'stop' })}</div>
        <p class="nj-c-qtitle">Ao no Hako S2 · Ep. 8</p><div class="nj-cluster">${pill('Anime4K 1440p', 'neutral')}${pill('Cuadros ×2', 'neutral')}${pill('NVENC HEVC', 'neutral')}</div>
        <div class="nj-c-cachebar" role="img" aria-label="Posición 12:41 de 23:40; en caché de 11:20 a 14:05"><i class="nj-c-cachewin" style="left:47.6%;width:11.6%"></i><i class="nj-c-cachepos" style="left:53.6%"></i></div>
        <div class="nj-c-metrics"><span>12:41 / 23:40</span><span>En RAM de 11:20 a 14:05</span><span>Caché 612 / 1024 MB</span><span>Adelanto +84 s</span><span>Ritmo 0,4 s/seg.</span></div></div></section>`;
  }

  function lnServer(ctx, s) {
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'srv')}"><h2 class="nj-h3" id="${pid(ctx, 'srv')}">Servidor Link</h2>
      ${tog(ctx, 'linkOn', 'Link en esta PC', s.linkOn, s.linkOn ? 'Escuchando en 192.168.1.20 y 100.64.0.7. Solo red privada, HTTPS y teléfonos emparejados.' : 'Apagado: tus teléfonos no pueden usar esta PC.')}
      ${tog(ctx, 'mdns', 'Anunciar en la red', s.mdns, 'Los teléfonos la encuentran sin escribir la dirección.')}
      <div class="nj-c-field"><span class="nj-c-label">Nombre que ven los teléfonos</span>${chips('pcName', 'Nombre', [{ id: 'host', label: 'octavio-pc (el del equipo)' }, { id: 'sala', label: 'PC de la sala' }, { id: 'estudio', label: 'Estudio' }], s.pcName)}</div>
      <div class="nj-c-field"><span class="nj-c-label">Carpeta de la biblioteca</span><div class="nj-c-pathrow">${s.libFolder ? `<span class="nj-c-path">${ic('folder', { size: 18 })}${esc(s.libFolder)}</span>` : '<span class="nj-meta">Ninguna</span>'}${btn('Elegir carpeta', 'libPick', { kind: 'outline', icon: 'folder_open' })}${s.libFolder ? btn('Quitar', 'libNone', { kind: 'text' }) : ''}</div><p class="nj-meta">Los teléfonos pueden reproducir los videos de esa carpeta (no de sus subcarpetas).</p></div>
      <div class="nj-c-pair">${stepper('port', 'Puerto', s.port, { min: 1024, max: 65535 })}<div class="nj-c-field"><span class="nj-c-label">Caché de reproducción en RAM</span>${seg('ram', 'Caché en RAM por sesión', [{ id: '512', label: '512 MB' }, { id: '1024', label: '1 GB' }, { id: '2048', label: '2 GB' }, { id: '4096', label: '4 GB' }], s.ram)}</div></div>
      ${s.port !== 47321 ? '<p class="nj-meta">Cambiar el puerto reinicia el servidor. Los teléfonos lo detectan solos.</p>' : ''}
      <details class="nj-c-details"><summary>Firewall</summary><p class="nj-meta">En Linux abre los puertos de Link y del servidor de extensiones:</p><div class="nj-c-cmd"><code>sudo ufw allow ${s.port}/tcp &amp;&amp; sudo ufw allow ${s.extPort}/tcp</code>${ibtn('content_copy', 'Copiar comando de ufw', 'copy')}</div><div class="nj-c-cmd"><code>sudo firewall-cmd --add-port=${s.port}/tcp --add-port=${s.extPort}/tcp --permanent</code>${ibtn('content_copy', 'Copiar comando de firewalld', 'copy')}</div><p class="nj-meta">En Windows aparece un botón «Permitir en red privada» que pide permiso de administrador.</p></details>
      <div class="nj-c-dangerzone"><div class="nj-c-grow"><b>Regenerar identidad</b><p class="nj-meta">Cambia la huella. Todos los teléfonos emparejados se olvidan; se guarda una copia.</p></div>${btn('Regenerar…', 'open', { kind: 'danger', attrs: { 'data-arg': 'regen' } })}</div></section>`;
  }

  function lnExtServer(ctx, s) {
    const busy = s.extPort === 8099;
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'exs')}"><h2 class="nj-h3" id="${pid(ctx, 'exs')}">Servidor de extensiones</h2>
      ${tog(ctx, 'extOn', 'Prestar las extensiones de esta PC', s.extOn, 'HTTP simple en la red local. Cada teléfono necesita su propio código.')}
      ${s.extOn ? `${stepper('extPort', 'Puerto', s.extPort, { min: 1024, max: 65535 })}
        ${busy ? `<div class="nj-c-banner" data-tone="err">${ic('error')}<div class="nj-c-grow"><b>El puerto 8099 está en uso por otra app</b><p class="nj-meta">El servidor de extensiones no pudo arrancar.</p></div>${btn('Usar el 8100', 'portTry', { kind: 'tonal' })}</div>` : `<p class="nj-c-result">${ic('task_alt', { size: 18 })}Sirviendo 3 extensiones en el puerto ${s.extPort}.</p>`}
        <div class="nj-cluster">${btn('Nuevo código para un teléfono', 'open', { kind: 'tonal', icon: 'qr_code_2', attrs: { 'data-arg': 'extpair' } })}</div>
        <div class="nj-c-secret">${ic('password', { size: 18 })}<span class="nj-c-grow">${s.viewerPw ? 'Hay una contraseña de visor.' : 'Sin contraseña de visor: cualquiera con un código puede ver la lista.'}</span>${s.viewerPw ? btn('Quitar', 'pwDel', { kind: 'text' }) : btn('Crear contraseña', 'pwAdd', { kind: 'text' })}</div>
        <h3 class="nj-c-label">Dispositivos con acceso</h3><ul class="nj-c-plainlist">${s.extDevs.map((d, i) => `<li>${ic('smartphone', { size: 18 })}<span class="nj-c-grow">${esc(d)}</span>${btn('Revocar…', 'open', { kind: 'text', attrs: { 'data-arg': 'extRevoke:' + i } })}</li>`).join('') || '<li class="nj-meta">Ningún teléfono tiene acceso todavía.</li>'}</ul>
        <details class="nj-c-details"><summary>Solicitudes y errores (${LN_LOG.length})</summary><pre class="nj-c-log">${LN_LOG.map((l) => `<span data-tone="${/fail|error/.test(l) ? 'err' : 'ok'}">${esc(l)}</span>`).join('\n')}</pre></details>` : ''}
    </section>`;
  }

  function lnPaired(ctx, s) {
    return `<section class="nj-c-panel" aria-labelledby="${pid(ctx, 'pdv')}"><div class="nj-c-panelhead"><h2 class="nj-h3 nj-c-grow" id="${pid(ctx, 'pdv')}">Dispositivos emparejados</h2>${btn('Emparejar', 'open', { kind: 'tonal', icon: 'qr_code_2', attrs: { 'data-arg': 'pair' } })}</div>
      <ul class="nj-c-plainlist">${['phone', 'tablet'].map((k) => `<li><button type="button" class="nj-c-provrow" data-c="devSel" data-arg="${k}" aria-pressed="${s.dev === k}">${ic(k === 'phone' ? 'smartphone' : 'tablet_android')}<span class="nj-c-grow"><span class="nj-c-qtitle">${esc(s.devs[k].name)}</span><span class="nj-meta">${s.devState[k] === 'connected' ? 'Conectado ahora' : 'Visto hace 3 h'} · ${s.devs[k].hist} entradas de historial</span></span>${ic('chevron_right')}</button></li>`).join('')}</ul></section>`;
  }

  /* ---- cliente Android ---- */
  function lnClient(ctx, s) {
    if (s.cli === 'none') {
      return `${empty('phonelink_off', 'No hay ninguna PC enlazada', 'En la PC abre Nijiyome › Link › Emparejar y escanea el código.', `<div class="nj-cluster">${btn('Escanear QR', 'open', { kind: 'filled', icon: 'qr_code_scanner', attrs: { 'data-arg': 'scan' } })}${btn('Pegar código', 'open', { kind: 'outline', icon: 'content_paste', attrs: { 'data-arg': 'code' } })}</div>`)}
        <section class="nj-c-panel"><div class="nj-c-panelhead"><h2 class="nj-h3 nj-c-grow">PCs en tu red</h2><span class="nj-c-spin" aria-hidden="true"></span></div><p class="nj-meta">Buscando PCs con Nijiyome abierto en esta red…</p>
          <ul class="nj-c-plainlist"><li>${ic('desktop_windows')}<span class="nj-c-grow"><span class="nj-c-qtitle">${esc(L.pc)}</span><span class="nj-meta"> · 192.168.1.20</span></span>${btn('Emparejar', 'open', { kind: 'tonal', attrs: { 'data-arg': 'trust' } })}</li></ul></section>
        <details class="nj-c-panel nj-c-collapse"><summary><span class="nj-h3">¿Para qué enlazar el teléfono con la PC?</span></summary><ul class="nj-c-bullets"><li>La PC mejora el video (Anime4K, cuadros extra) y te lo envía listo.</li><li>El historial se sincroniza entre los dos.</li><li>Lo que ves en la PC puedes seguirlo aquí, y al revés.</li><li>Gasta la red y la energía de la PC mientras ves.</li><li>Solo funciona en tu red o por un túnel seguro como Tailscale.</li></ul></details>`;
    }
    if (s.cli === 'failed') {
      return `<section class="nj-c-panel nj-c-failed" data-region="link" data-state="failed">${ic('link_off', { size: 32 })}<h2 class="nj-h2">No se pudo conectar con ${esc(L.pc)}</h2><p class="nj-meta">La PC no responde en https://192.168.1.20:47321. Puede estar apagada, con Link desactivado o en otra red.</p>
        <ul class="nj-c-bullets"><li>Revisa que este teléfono esté en el mismo Wi-Fi.</li><li>En la PC, abre Nijiyome y mira que Link esté encendido.</li></ul>
        <div class="nj-cluster">${btn(s.retrying ? 'Reintentando…' : 'Reintentar', 'cliRetry', { kind: 'filled', icon: 'refresh' })}${btn('Olvidar PC…', 'open', { kind: 'danger', attrs: { 'data-arg': 'forget' } })}</div></section>`;
    }
    const conn = `<div class="nj-c-map is-pair" data-region="link" data-state="connected" aria-label="Conexión con la PC"><svg class="nj-c-mapsvg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><path d="M22 50 L 78 50" class="nj-c-wire" data-state="connected" vector-effect="non-scaling-stroke"/></svg>
      <div class="nj-c-node" data-device="phone" data-state="connected" style="left:22%;top:50%">${ic('smartphone', { size: 24 })}<span class="nj-c-nodename">Este teléfono</span></div>
      <div class="nj-c-node is-hub" data-device="pc" data-state="connected" style="left:78%;top:50%">${ic('desktop_windows', { size: 24 })}<span class="nj-c-nodename">${esc(L.pc)}</span></div>
      <span class="nj-c-wirelabel" style="left:50%;top:22%">${esc(L.latency)} · HTTPS</span></div>`;
    return `${s.handoff ? `<section class="nj-c-panel nj-c-handoff" aria-labelledby="${pid(ctx, 'ho')}"><img class="nj-c-thumb" src="${esc(T('aonohako').poster)}" alt="" width="48" height="72"><div class="nj-c-grow"><h2 class="nj-h3" id="${pid(ctx, 'ho')}">La PC te envió Ao no Hako 2</h2><p class="nj-meta">Ep. 8 · desde 12:41 · hace 2 min</p><div class="nj-cluster">${btn('Reproducir aquí', 'go', { kind: 'filled', icon: 'play_arrow', attrs: { 'data-arg': 'reproductor' } })}${btn('Descartar', 'handoffOff', { kind: 'text' })}</div></div></section>` : ''}
      ${conn}
      <section class="nj-c-panel"><div class="nj-c-panelhead">${pill('PC conectada', 'ok', 'check_circle')}<span class="nj-c-grow"></span><span class="nj-meta">Visto hace 1 min</span></div>
        <p class="nj-meta">${esc(L.pc)} · https://192.168.1.20:47321 · Este teléfono se registró como ${esc(L.phone)} (Android)</p>
        <div class="nj-c-hash"><span class="nj-c-label">Huella</span><code>${s.fpFull ? '3F9A 71C2 0B8E D4A6 5512 9E07 C3BB 1F40' : '3F9A 71C2…'}</code>${btn(s.fpFull ? 'Ocultar' : 'Ver completa', 'fpToggle', { kind: 'text' })}</div>
        ${btn('Continuar en la PC', 'cliContinue', { kind: 'tonal', icon: 'cast' })}<p class="nj-meta">Tempal · Ep. 4 en 05:42</p></section>
      <section class="nj-c-panel"><h2 class="nj-h3">Control remoto</h2><p class="nj-c-qtitle">Ao no Hako S2 · Ep. 8</p><p class="nj-meta">${s.rPlay ? 'Reproduciendo' : 'En pausa'} · 12:41 / 23:40</p>
        ${sld(ctx, 'rSeek', 'Posición', { min: 0, max: 1420, value: 761, unit: ' s' })}
        <div class="nj-c-remote">${ibtn('skip_previous', 'Anterior', 'remote', { kind: 'tonal' })}${ibtn(s.rPlay ? 'pause' : 'play_arrow', s.rPlay ? 'Pausar' : 'Reproducir', 'rPlay', { kind: 'filled', fill: true })}${ibtn('skip_next', 'Siguiente', 'remote', { kind: 'tonal' })}</div>
        ${sld(ctx, 'rVol', 'Volumen de la PC', { min: 0, max: 100, value: 70, unit: ' %' })}</section>
      <section class="nj-c-panel"><h2 class="nj-h3">Procesar video en</h2>${seg('procOn', 'Procesar en', [{ id: 'phone', label: 'Teléfono' }, { id: 'pc', label: 'PC' }, { id: 'mix', label: 'Mixto' }], s.procOn)}
        ${tog(ctx, 'histSync', 'Sincronizar el historial con la PC', s.histSync, 'Historial sincronizado hace 4 min')}${btn(s.syncing ? 'Sincronizando…' : 'Sincronizar ahora', 'syncNow', { kind: 'text', icon: 'sync' })}</section>
      ${lnGoalPicker(ctx, s)}
      <section class="nj-c-panel"><h2 class="nj-h3">Mejorar un stream</h2><p class="nj-meta">La PC baja este stream, lo mejora y te lo envía.</p><label class="nj-c-field"><span class="nj-c-label">URL del stream</span><span class="nj-field">${ic('link')}<input type="url" inputmode="url" data-model="streamUrl" value="${esc(s.streamUrl || '')}" placeholder="https://…/master.m3u8" spellcheck="false"></span></label><div class="nj-cluster">${btn('Pegar', 'streamPaste', { kind: 'outline', icon: 'content_paste' })}${btn('Iniciar sesión de mejora', 'streamGo', { kind: 'filled' })}</div>${s.streamErr ? `<p class="nj-c-errtext">${esc(s.streamErr)}</p>` : ''}</section>
      <section class="nj-c-panel"><div class="nj-c-panelhead"><h2 class="nj-h3 nj-c-grow">Extensiones de tu PC</h2>${pill('En línea · 3', 'ok')}</div><p class="nj-meta">Si la PC se apaga, se usan las extensiones de este teléfono.</p><div class="nj-cluster">${btn('Administrar', 'goServers', { kind: 'tonal' })}${btn('Escanear otro código', 'open', { kind: 'text', icon: 'qr_code_scanner', attrs: { 'data-arg': 'scan' } })}</div></section>
      <div class="nj-c-dangerzone"><div class="nj-c-grow"><b>Olvidar esta PC</b><p class="nj-meta">Se borra el certificado y tendrás que emparejar de nuevo.</p></div>${btn('Olvidar…', 'open', { kind: 'danger', attrs: { 'data-arg': 'forget' } })}</div>`;
  }

  NJ.registerScreen({
    id: 'link', name: 'Nijiyome Link', group: 'Fuentes y equipo', order: 3, platforms: ['desktop', 'android'],
    summary: 'Link deja de ser una pila de formularios: un mapa de tus dispositivos, un selector de mejora por objetivo y los dos servidores separados con sus puertos elegibles.',
    parity: 'LNK-01…LNK-20, SET-LNK-01…04, SET-LNKSRV-01…22 (LNK-20 abre Ajustes › Acerca de › Registros)',
    structures: [
      { id: 'mapa', name: 'Mapa de dispositivos', desc: 'Diagrama de la PC con sus teléfonos y tablets; al tocar un nodo se ve y se ajusta ese dispositivo. En Android: conectado, con traspaso y control remoto.' },
      { id: 'panel', name: 'Tarjetas de estado', desc: 'Panel con una tarjeta por servicio y su acción principal. En Android abre en el estado de enlace fallido, con su recuperación.' },
    ],
    render(ctx) {
      const s = init(ctx, { linkOn: true, mdns: true, pcName: 'host', libFolder: '~/Videos/Anime', port: 47321, ram: '1024', extOn: true, extPort: 8099, viewerPw: true, extDevs: [L.phone], dev: 'phone', devState: { phone: 'connected', tablet: 'idle' }, devs: { phone: { name: L.phone, hist: 184, since: '2 meses', histMode: 'full' }, tablet: { name: L.tablet, hist: 37, since: '3 semanas', histMode: 'recv' } }, up: 'a4k1440', fg: 'x2', handoff: true, procOn: 'pc', histSync: true, rPlay: true, techOpen: false, _draw: true });
      if (s.cliFor !== ctx.structure) { s.cliFor = ctx.structure; s.cli = ctx.structure === 'panel' ? 'failed' : 'ok'; }
      const isA = A(ctx);
      let main;
      if (isA) {
        main = `${pageHead(ctx, { title: 'Nijiyome Link', actionsA: ibtn('receipt_long', 'Registros de Link', 'devLogs') })}<div class="nj-c-body nj-c-pad nj-c-stackgap" data-keep="main">${lnClient(ctx, s)}<div class="nj-c-fabspace"></div></div>`;
      } else {
        const head = pageHead(ctx, { title: 'Nijiyome Link', meta: s.linkOn ? `Escuchando en el puerto ${s.port} · ${s.devState.phone === 'connected' ? '1 sesión activa' : 'sin sesiones'} · 2 dispositivos emparejados` : 'Link está apagado en esta PC', actions: `${btn('Abrir monitor', 'open', { kind: 'outline', icon: 'monitoring', attrs: { 'data-arg': 'monitor' } })}${btn('Emparejar dispositivo', 'open', { kind: 'filled', icon: 'qr_code_2', attrs: { 'data-arg': 'pair' } })}` });
        let top;
        if (ctx.structure === 'panel') {
          const card = (icon, title, state, tone, text, action) => `<section class="nj-c-panel nj-c-statcard"><div class="nj-c-panelhead">${ic(icon)}<h2 class="nj-h3 nj-c-grow">${title}</h2>${pill(state, tone)}</div><p class="nj-meta">${text}</p>${action}</section>`;
          top = `<div class="nj-c-cards" data-region="link" data-state="${s.linkOn ? 'connected' : 'idle'}">
            ${card('router', 'Servidor Link', s.linkOn ? 'Escuchando' : 'Apagado', s.linkOn ? 'ok' : 'neutral', `Puerto ${s.port} · 192.168.1.20 y 100.64.0.7 · HTTPS`, btn(s.linkOn ? 'Apagar' : 'Encender', 'linkFlip', { kind: 'text' }))}
            <section class="nj-c-panel nj-c-statcard" data-device="phone" data-state="${s.devState.phone}"><div class="nj-c-panelhead">${ic('smartphone')}<h2 class="nj-h3 nj-c-grow">${esc(L.phone)}</h2>${pill('Conectado', 'ok')}</div><p class="nj-meta">Viendo Ao no Hako 2 · Ep. 8 · ${esc(L.latency)}</p>${btn('Ajustar', 'devSel', { kind: 'text', attrs: { 'data-arg': 'phone' } })}</section>
            <section class="nj-c-panel nj-c-statcard" data-device="tablet" data-state="${s.devState.tablet}"><div class="nj-c-panelhead">${ic('tablet_android')}<h2 class="nj-h3 nj-c-grow">${esc(L.tablet)}</h2>${pill('Inactiva', 'neutral')}</div><p class="nj-meta">Vista hace 3 h · solo recibe historial</p>${btn('Ajustar', 'devSel', { kind: 'text', attrs: { 'data-arg': 'tablet' } })}</section>
            ${card('memory', 'Caché en RAM', '612 / 1024 MB', 'neutral', 'Adelanto de 84 s en la sesión del teléfono.', btn('Abrir monitor', 'open', { kind: 'text', attrs: { 'data-arg': 'monitor' } }))}
            ${card('extension', 'Servidor de extensiones', s.extPort === 8099 ? 'Puerto ocupado' : 'Sirviendo 3', s.extPort === 8099 ? 'err' : 'ok', s.extPort === 8099 ? 'Otra app usa el 8099.' : `Puerto ${s.extPort} · 1 teléfono con acceso`, s.extPort === 8099 ? btn('Usar el 8100', 'portTry', { kind: 'tonal' }) : btn('Nuevo código', 'open', { kind: 'text', attrs: { 'data-arg': 'extpair' } }))}
            ${card('history', 'Historial', 'Sincronizado', 'ok', 'Hace 4 min con el teléfono · 221 entradas en total.', btn('Ver dispositivos', 'devSel', { kind: 'text', attrs: { 'data-arg': 'phone' } }))}
          </div>`;
        } else {
          top = `<div class="nj-c-maprow">${lnMap(ctx, s)}<div class="nj-c-mapside">${lnDevicePanel(ctx, s)}</div></div>`;
        }
        main = `${head}<div class="nj-c-body nj-c-pad" data-keep="main">${top}
          <div class="nj-c-linkgrid"><div class="nj-c-stackgap">${ctx.structure === 'panel' ? lnDevicePanel(ctx, s) + lnPaired(ctx, s) : ''}${lnSessions(ctx, s)}${lnGoalPicker(ctx, s)}</div><div class="nj-c-stackgap">${lnServer(ctx, s)}${lnExtServer(ctx, s)}</div></div></div>`;
      }
      let ov = '';
      if (s.ov === 'pair' || s.ov === 'extpair') {
        const ext = s.ov === 'extpair';
        ov = sheetHTML(ctx, { id: s.ov, title: ext ? 'Código para el servidor de extensiones' : 'Emparejar un dispositivo', body: `<div class="nj-c-qrbox">${qrSVG(ext ? 11 : 7)}<p class="nj-meta">QR de ejemplo</p></div>
          <p>En el teléfono abre Nijiyome › Tú › ${ext ? 'Extensiones de tu PC' : 'Link'} y escanea el código${ext ? ' o pega el enlace' : ''}.</p>
          ${ext ? `<div class="nj-c-cmd"><code>nijiyome://extensionserver/connect?server=192.168.1.20:8100&amp;code=…</code>${ibtn('content_copy', 'Copiar enlace', 'copy')}</div>` : `<div class="nj-c-code" aria-label="Código de emparejamiento">K7QM 2XRP 4D</div><p class="nj-meta">Válido 58 s más, un solo uso.</p><div class="nj-c-hash"><span class="nj-c-label">Huella de esta PC</span><code>3F9A 71C2 0B8E D4A6</code></div>`}`,
          foot: `${btn('Cancelar', 'close', { kind: 'text' })}${btn(ext ? 'Copiar enlace' : 'Copiar texto de emparejamiento', 'copy', { kind: 'tonal', icon: 'content_copy' })}` });
      } else if (s.ov === 'monitor') {
        ov = sheetHTML(ctx, { id: 'monitor', title: 'Monitor de Link y del sistema', sub: `Puerto ${s.port} · 1 sesión activa`, wide: true, body: `<h3 class="nj-c-label">Dispositivos usando esta PC</h3>${lnSessions(ctx, s, true)}
          <h3 class="nj-c-label">Torrents activos · ↓ 31,1 MB/s</h3><ul class="nj-c-plainlist">${TX_QUEUE.slice(0, 3).map((t) => `<li><span class="nj-c-grow"><span class="nj-c-qtitle">${esc(txTitle(t))}</span>${h.progress(t.pct, { label: 'Progreso' })}</span><span class="nj-c-num">${pct(t.pct)} · ${esc(t.down || '')}</span></li>`).join('')}</ul>${btn('Abrir transferencias', 'go', { kind: 'text', icon: 'swap_vert', attrs: { 'data-arg': 'transferencias' } })}
          <h3 class="nj-c-label">Memoria y caché</h3><div class="nj-c-meter"><span>Caché de segmentos de Link</span><span class="nj-c-num">612 / ${s.ram === '4096' ? '4096' : s.ram} MB</span>${h.progress(612 / Number(s.ram), { label: 'Caché de Link' })}</div>
          <div class="nj-c-field"><span class="nj-c-label">Presupuesto por sesión</span>${seg('ram', 'Caché en RAM por sesión', [{ id: '512', label: '512 MB' }, { id: '1024', label: '1 GB' }, { id: '2048', label: '2 GB' }, { id: '4096', label: '4 GB' }], s.ram)}</div>
          <div class="nj-c-meter"><span>RAM de la app</span><span class="nj-c-num">${esc(L.ram)}</span>${h.progress(0.6, { label: 'RAM de la app' })}</div><div class="nj-c-meter"><span>Disco: imágenes 820 MB · torrents 36,1 GB</span><span class="nj-c-num">311,8 GB libres</span>${h.progress(0.134, { label: 'Disco' })}</div>` });
      } else if (s.ov === 'rename') {
        ov = dialogHTML(ctx, { id: 'rename', title: 'Renombrar dispositivo', body: `<label class="nj-c-field"><span class="nj-c-label">Nombre</span><span class="nj-field"><input type="text" data-model="newName" value="${esc(s.newName != null ? s.newName : s.devs[s.dev] ? s.devs[s.dev].name : '')}" data-autofocus></span></label>`, actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Guardar', 'renameGo', { kind: 'filled' })}` });
      } else if (s.ov === 'revoke') ov = dialogHTML(ctx, { id: 'revoke', title: '¿Revocar el acceso?', icon: 'block', tone: 'err', body: `<p>${esc((s.devs[s.dev] || {}).name || '')} deja de poder usar esta PC y tendrá que emparejarse de nuevo.</p>`, actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Revocar', 'revokeGo', { kind: 'danger' })}` });
      else if (s.ov && s.ov.startsWith('extRevoke:')) ov = dialogHTML(ctx, { id: 'extRevoke', title: '¿Quitar el acceso a las extensiones?', icon: 'block', tone: 'err', body: '<p>Ese teléfono vuelve a usar sus extensiones locales.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Quitar acceso', 'extRevokeGo', { kind: 'danger' })}` });
      else if (s.ov === 'regen') ov = dialogHTML(ctx, { id: 'regen', title: '¿Regenerar la identidad de Link?', icon: 'key_off', tone: 'err', body: '<p>Cambia la huella de esta PC. Todos los teléfonos emparejados se olvidan; se guarda una copia de la identidad anterior.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Regenerar', 'regenGo', { kind: 'danger' })}` });
      else if (s.ov === 'forget') ov = dialogHTML(ctx, { id: 'forget', title: '¿Olvidar esta PC?', icon: 'link_off', tone: 'err', body: '<p>Se borra el certificado de este teléfono. Para volver a usarla tendrás que emparejar de nuevo.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Olvidar PC', 'forgetGo', { kind: 'danger' })}` });
      else if (s.ov === 'scan') ov = sheetHTML(ctx, { id: 'scan', title: 'Escanear código', body: `<div class="nj-c-viewfinder" role="img" aria-label="Vista de la cámara">${ic('qr_code_2', { size: 64 })}<span class="nj-c-vfcorner"></span></div><p class="nj-meta">Apunta al código que muestra la PC. Si no das permiso de cámara, pega el código.</p>`, foot: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Pegar código', 'open', { kind: 'outline', attrs: { 'data-arg': 'code' } })}` });
      else if (s.ov === 'code') ov = dialogHTML(ctx, { id: 'code', title: 'Pegar código', body: `<label class="nj-c-field"><span class="nj-c-label">Código de emparejamiento</span><span class="nj-field"><input type="text" class="nj-c-codein" data-model="pairCode" value="${esc(s.pairCode || '')}" maxlength="64" placeholder="10 caracteres o enlace nijiyome-link:v1" autocomplete="off" spellcheck="false" data-autofocus></span></label>${s.codeErr ? `<p class="nj-c-errtext">${esc(s.codeErr)}</p>` : ''}`, actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Pegar del portapapeles', 'codePaste', { kind: 'text' })}${btn('Emparejar', 'codeGo', { kind: 'filled' })}` });
      else if (s.ov === 'trust') ov = dialogHTML(ctx, { id: 'trust', title: `Emparejar con ${L.pc}`, icon: 'verified_user', body: `<p>Revisa que la huella coincida con la que muestra la PC.</p><div class="nj-c-code is-small">3F9A 71C2 0B8E D4A6</div><label class="nj-c-field"><span class="nj-c-label">Código de la PC</span><span class="nj-field"><input type="text" class="nj-c-codein" maxlength="10" value="K7QM2XRP4D" autocomplete="off" spellcheck="false" data-autofocus></span></label>`, actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Confiar y emparejar', 'pairOk', { kind: 'filled' })}` });
      return `<div class="nj-c-root nj-c-ln${canMove(ctx) && s._draw ? ' is-draw' : ''}" data-local data-cards-local>${main}${ov}${snackHTML(s)}</div>`;
    },
    mount(el, ctx) {
      const s = ctx.state; const re = () => rr(ctx, el);
      const H = {
        $toggle(k) { if (['linkOn', 'extOn'].includes(k)) { if (k === 'linkOn') s._draw = s.linkOn; re(); } },
        $seg(k, v) { if (k.startsWith('hist-')) { s.devs[k.slice(5)].histMode = v; re(); return false; } return true; },
        $steps: { port: { min: 1024, max: 65535 }, extPort: { min: 1024, max: 65535 } },
        $stepped(k, v) { if (k === 'port' && v === s.extPort) s.port = v + 1; },
        goal(t) { const g = LN_GOALS.find((x) => x.id === t.getAttribute('data-arg')); s.up = g.up; s.fg = g.fg; snack(ctx, `Perfil: ${g.label}`); re(); },
        upPick(t) { s.up = t.getAttribute('data-arg'); s.techOpen = true; re(); },
        fgPick(t) { s.fg = t.getAttribute('data-arg'); s.techOpen = true; re(); },
        techToggle(t, e) { if (e.target.closest('summary')) s.techOpen = !t.open; },
        devSel(t) { s.dev = t.getAttribute('data-arg'); re(); },
        devSource() { s.resync = true; re(); },
        devLogs() { if (NJ.screens.ajustes) NJ.go('ajustes'); else { snack(ctx, 'Los registros están en Ajustes › Acerca de › Registros'); re(); } },
        renameGo() { if (s.devs[s.dev] && s.newName) s.devs[s.dev].name = s.newName; s.newName = null; s.ov = null; re(); },
        revokeGo() { s.ov = null; snack(ctx, 'Acceso revocado'); s.devState[s.dev] = 'failed'; re(); },
        extRevokeGo() { s.extDevs.splice(Number(s.ov.slice(10)), 1); s.ov = null; re(); },
        regenGo() { s.ov = null; snack(ctx, 'Identidad regenerada. Empareja tus teléfonos de nuevo.'); re(); },
        portTry() { s.extPort = 8100; re(); },
        linkFlip() { s.linkOn = !s.linkOn; re(); },
        libPick() { s.libFolder = '/media/Almacen/Anime'; re(); },
        libNone() { s.libFolder = null; re(); },
        pwDel() { s.viewerPw = false; re(); },
        pwAdd() { s.viewerPw = true; snack(ctx, 'Contraseña de visor guardada'); re(); },
        copy() { snack(ctx, 'Copiado al portapapeles'); re(); },
        sesStop() { s.devState.phone = 'idle'; snack(ctx, 'Sesión detenida'); re(); },
        handoffOff() { s.handoff = false; re(); },
        fpToggle() { s.fpFull = !s.fpFull; re(); },
        cliContinue() { snack(ctx, 'La PC sigue con Tempal · Ep. 4 desde 05:42'); re(); },
        rPlay() { s.rPlay = !s.rPlay; re(); },
        remote() { snack(ctx, 'Enviado a la PC'); re(); },
        syncNow() { snack(ctx, 'Historial sincronizado'); re(); },
        streamPaste() { s.streamUrl = 'https://cdn.example.org/hls/master.m3u8'; re(); },
        streamGo() { if (!/^https?:\/\//.test(s.streamUrl || '')) { s.streamErr = 'Esa URL no es válida. Pega un enlace http(s).'; re(); return; } s.streamErr = null; snack(ctx, 'La PC empezó a mejorar el stream'); re(); },
        goServers() { const k = 'fuentes:' + ctx.platform; NJ.screenState[k] = NJ.screenState[k] || {}; NJ.screenState[k].tab = 'servidores'; NJ.go('fuentes', { structure: 'pestanas' }); },
        cliRetry() { s.retrying = true; re(); setTimeout(() => { if (!el.isConnected) return; s.retrying = false; s.cli = 'ok'; s._draw = true; snack(ctx, `Conectado con ${L.pc}`); rr(ctx, el); }, 1200); },
        forgetGo() { s.cli = 'none'; s.ov = null; re(); },
        codePaste() { s.pairCode = 'K7QM2XRP4D'; re(); },
        codeGo() { const v = (s.pairCode || '').replace(/\s/g, ''); if (!(/^[A-Za-z0-9]{10}$/.test(v) || /^nijiyome-link:v1\?/.test(v))) { s.codeErr = 'Eso no es un enlace nijiyome-link:v1 ni un código de 10 caracteres.'; re(); return; } s.codeErr = null; openOv(ctx, 'trust'); re(); },
        pairOk() { s.ov = null; s.cli = 'ok'; s._draw = true; snack(ctx, `Emparejado con ${L.pc}`); re(); },
      };
      wire(el, ctx, H);
      if (s.ov === 'scan' && A(ctx)) setTimeout(() => { if (el.isConnected && ctx.state.ov === 'scan') { ctx.state.ov = 'trust'; ctx.state._autofocus = true; rr(ctx, el); } }, 1800);
      /* un solo trazo al encender o conectar: la línea se dibuja y se queda */
      if (s._draw) setTimeout(() => { s._draw = false; }, 0);
    },
  });

  /* =====================================================================
     4. MANGA: inicio con filtros que sobreviven al orden, y ficha con capítulos
     ===================================================================== */
  const MG_KIND = { manga: 'Manga', manhwa: 'Manhwa', manhua: 'Manhua' };
  const MG_ORIGIN = { manga: 'jp', manhwa: 'kr', manhua: 'cn' };
  const mgItem = (m) => ({ id: m.id, title: m.title, short: m.title, poster: m.poster, format: MG_KIND[m.kind], year: m.chapter, eps: m.total, progress: parseInt(String(m.chapter).replace(/\D/g, ''), 10) || 0, kind: m.kind });
  const MG_ALL = (D.manga || []).map(mgItem);
  const MG_READ = [{ id: 'watching', label: 'Leyendo' }, { id: 'planning', label: 'Planeo leer' }, { id: 'completed', label: 'Completado' }, { id: 'paused', label: 'En pausa' }, { id: 'dropped', label: 'Abandonado' }, { id: 'rewatching', label: 'Releyendo' }];
  const MG_F = {
    origin: { label: 'Origen', multi: true, opts: [{ id: 'jp', label: 'Japón' }, { id: 'kr', label: 'Corea' }, { id: 'cn', label: 'China' }, { id: 'tw', label: 'Taiwán' }] },
    status: { label: 'Estado', opts: [{ id: 'pub', label: 'En publicación' }, { id: 'fin', label: 'Finalizado' }, { id: 'hiatus', label: 'En pausa' }, { id: 'cancel', label: 'Cancelado' }, { id: 'soon', label: 'Próximamente' }] },
    genre: { label: 'Género', multi: true, opts: ['Acción', 'Aventura', 'Fantasía', 'Romance', 'Comedia', 'Drama', 'Sobrenatural', 'Artes marciales'].map((g) => ({ id: g, label: g })) },
  };
  const MG_SORT = [{ id: 'popular', label: 'Popularidad' }, { id: 'trend', label: 'Tendencia' }, { id: 'score', label: 'Puntuación' }, { id: 'new', label: 'Más nuevos' }, { id: 'title', label: 'Título' }, { id: 'fav', label: 'Favoritos' }];
  const MG_SOURCES = [
    { id: 'kumo', name: 'Kumo', count: 200, langs: 'EN · ES', rel: 0.87 },
    { id: 'toko', name: 'Toko', count: 179, langs: 'EN', rel: 0.72 },
    { id: 'fumi', name: 'Fumi', count: 0, langs: '', rel: 0, miss: 'No encontró el título' },
  ];
  const MG_GROUPS = ['Asura Scans', 'Lectores Nocturnos', 'Reaper Fansub'];
  function mgChapters() { const out = []; for (let n = 200; n >= 171; n--) out.push({ n, title: n === 200 ? 'Epílogo' : n % 10 === 0 ? 'Fin del arco' : '', group: MG_GROUPS[n % 3], lang: n % 3 === 1 ? 'ES' : 'EN', ago: n >= 198 ? 'hace ' + (201 - n) + ' d' : n >= 190 ? 'hace ' + (201 - n) + ' sem.' : 'hace meses', vol: 14 - Math.floor((200 - n) / 10), dl: n === 178 || n === 179 }); return out; }

  function mgFilterChips(ctx, s) {
    const f = s.f; const active = [];
    if (f.origin.length) active.push(['origin', 'Origen: ' + f.origin.map((x) => MG_F.origin.opts.find((o) => o.id === x).label).join(', ')]);
    if (f.status) active.push(['status', 'Estado: ' + MG_F.status.opts.find((o) => o.id === f.status).label]);
    if (f.genre.length) active.push(['genre', 'Género: ' + f.genre.join(', ')]);
    if (f.y0 != null) active.push(['year', `Año: ${f.y0} a ${f.y1}`]);
    const pop = (k) => {
      if (s.menu !== 'f:' + k) return '';
      if (k === 'sort') return `<div class="nj-c-pop" role="dialog" aria-label="Ordenar">${chips('sortPick', 'Ordenar por', MG_SORT.map((o) => (o.id === 'popular' && s.q ? { id: 'popular', label: 'Relevancia' } : o)), s.sort)}<p class="nj-meta">El orden no borra tus filtros.</p></div>`;
      if (k === 'year') return `<div class="nj-c-pop" role="dialog" aria-label="Años">${stepper('y0', 'Desde', f.y0 != null ? f.y0 : 2015, {})}${stepper('y1', 'Hasta', f.y1 != null ? f.y1 : 2026, {})}<div class="nj-cluster">${btn('Aplicar', 'yearApply', { kind: 'tonal' })}${f.y0 != null ? btn('Cualquier año', 'yearClear', { kind: 'text' }) : ''}</div></div>`;
      const d = MG_F[k]; const sel = d.multi ? f[k] : f[k] ? [f[k]] : [];
      return `<div class="nj-c-pop" role="dialog" aria-label="${d.label}"><div class="nj-cluster" role="group" aria-label="${d.label}">${d.opts.map((o) => `<button type="button" class="nj-chip" data-c="fPick" data-arg="${k}:${esc(o.id)}" aria-pressed="${sel.includes(o.id)}">${sel.includes(o.id) ? ic('check', { size: 18 }) : ''}${esc(o.label)}</button>`).join('')}</div></div>`;
    };
    const trig = (k, label, on) => `<span class="nj-c-menuwrap"><button type="button" class="nj-chip${on ? ' is-on' : ''}" data-c="menu" data-arg="f:${k}" aria-haspopup="dialog" aria-expanded="${s.menu === 'f:' + k}" aria-pressed="${!!on}">${label}${ic('arrow_drop_down', { size: 18 })}</button>${pop(k)}</span>`;
    return `<div class="nj-c-filters" role="group" aria-label="Filtros">
      ${trig('origin', 'Origen', f.origin.length)}${trig('status', 'Estado', f.status)}${trig('genre', 'Género', f.genre.length)}${trig('year', 'Año', f.y0 != null)}<span class="nj-c-vsep" aria-hidden="true"></span>${trig('sort', ic('sort', { size: 18 }) + 'Orden: ' + (s.sort === 'popular' && s.q ? 'Relevancia' : MG_SORT.find((o) => o.id === s.sort).label), s.sort !== 'popular')}
      </div>${active.length ? `<div class="nj-c-activef" aria-label="Filtros activos">${active.map(([k, l]) => `<button type="button" class="nj-chip is-input" data-c="fDrop" data-arg="${k}" aria-label="Quitar ${esc(l)}">${esc(l)}${ic('close', { size: 16, cls: 'nj-chip-x' })}</button>`).join('')}${btn('Limpiar filtros', 'fClear', { kind: 'text' })}</div>` : ''}`;
  }

  function mgResults(ctx, s) {
    const f = s.f;
    let xs = MG_ALL.filter((m) => (!f.origin.length || f.origin.includes(MG_ORIGIN[m.kind])) && (!s.q || m.title.toLowerCase().includes(s.q.toLowerCase())));
    if (s.sort === 'title') xs = xs.slice().sort((a, b) => a.title.localeCompare(b.title));
    else if (s.sort === 'new') xs = xs.slice().reverse();
    else if (s.sort === 'score') xs = xs.slice().sort((a, b) => b.progress / b.eps - a.progress / a.eps);
    return `<div class="nj-c-sectionhead"><h2 class="nj-h2">${xs.length} resultados</h2><span class="nj-meta">${s.q ? `para «${esc(s.q)}» · ` : ''}ordenados por ${(s.sort === 'popular' && s.q ? 'relevancia' : MG_SORT.find((o) => o.id === s.sort).label.toLowerCase())}</span></div>
      ${xs.length ? `<div class="nj-grid" data-region="grid" style="--nj-cell:${A(ctx) ? '104px' : '150px'}">${xs.map((m) => h.card(m, { size: A(ctx) ? 's' : 'm', context: 'grid' })).join('')}</div>` : empty('search_off', 'Nada encontrado', 'Prueba con otro título o con menos filtros.', btn('Limpiar filtros', 'fClear', { kind: 'tonal' }))}`;
  }

  function mgFeed(ctx, s) {
    const feat = MG_ALL[0]; const isA = A(ctx);
    const cont = MG_ALL.filter((m) => m.progress / m.eps < 0.99).slice(0, 8);
    const news = s.news.map((id) => MG_ALL.find((m) => m.id === id));
    return `<section class="nj-c-feature" data-region="hero" aria-label="Destacado">
        ${h.card(feat, { size: 'l', context: 'hero' })}
        <div class="nj-c-featuretext"><p class="nj-meta">Destacado de la semana · Manhwa · Finalizado</p><h2 class="nj-c-display">${esc(feat.title)}</h2><p class="nj-c-lede">El cazador más débil de Corea recibe un sistema que solo él puede ver y empieza a subir de nivel cuando nadie más puede.</p>
          <div class="nj-cluster">${btn('Seguir en el cap. 180', 'go', { kind: 'filled', icon: 'menu_book', attrs: { 'data-arg': 'lector:paginado' } })}${btn('Ver ficha', 'mgOpen', { kind: 'tonal', attrs: { 'data-arg': feat.id } })}</div></div>
        ${isA ? '' : `<div class="nj-c-featureside">${MG_ALL.slice(4, 6).map((m) => h.card(m, { size: 'm', context: 'row' })).join('')}</div>`}
      </section>
      <section class="nj-section nj-c-flush" data-region="tonight" data-row-title="Seguir leyendo"><div class="nj-section-head"><h2 class="nj-h2">Seguir leyendo</h2>${btn('Biblioteca', 'go', { kind: 'text', attrs: { 'data-arg': 'biblioteca' } })}</div>
        <div class="nj-row">${cont.map((m) => h.card(m, { size: isA ? 's' : 'm', context: 'row', show: { progress: true } })).join('')}</div></section>
      <section class="nj-section nj-c-flush" aria-labelledby="${pid(ctx, 'news')}"><div class="nj-section-head"><h2 class="nj-h2" id="${pid(ctx, 'news')}">Capítulos nuevos de lo que lees</h2></div>
        ${news.length ? `<ul class="nj-c-newslist">${news.map((m, i) => `<li class="nj-c-newsrow" data-flip="n-${m.id}"><img class="nj-c-thumb" src="${esc(m.poster)}" alt="" width="40" height="60" loading="lazy"><div class="nj-c-grow"><div class="nj-c-qtitle">${esc(m.title)}</div><div class="nj-meta">Nuevo: Cap. ${m.progress + 1} · hace ${i + 2} h</div></div>${btn('Leer', 'go', { kind: 'tonal', attrs: { 'data-arg': 'lector:paginado' } })}${ibtn('close', 'Descartar aviso de ' + m.title, 'newsDrop', { attrs: { 'data-arg': m.id } })}</li>`).join('')}</ul>` : empty('notifications_none', 'Al día', 'No hay capítulos nuevos de lo que sigues.')}</section>
      <section class="nj-section nj-c-flush" data-region="row" data-row-title="Tendencias"><div class="nj-section-head"><h2 class="nj-h2">Tendencias</h2>${btn('Ver todo', 'sortTo', { kind: 'text', attrs: { 'data-arg': 'trend' } })}</div><div class="nj-row">${MG_ALL.slice().reverse().map((m) => h.card(m, { size: isA ? 's' : 'm', context: 'row' })).join('')}</div></section>
      <section class="nj-section nj-c-flush" aria-label="Géneros"><div class="nj-section-head"><h2 class="nj-h2">Géneros</h2></div><div class="nj-cluster">${MG_F.genre.opts.map((g) => `<button type="button" class="nj-chip" data-c="genreGo" data-arg="${esc(g.id)}">${esc(g.label)}</button>`).join('')}</div></section>
      <section class="nj-c-panel nj-c-adult"><div class="nj-c-panelhead">${ic('shield')}<h2 class="nj-h3 nj-c-grow">Zona +18</h2>${s.adult ? btn('Ocultar', 'adultHide', { kind: 'text' }) : ''}</div>${s.adult ? `<p class="nj-meta">Las portadas se difuminan hasta que tocas cada una, según Ajustes › Metadatos.</p>${empty('hide_image', 'Nada en esta zona con tus fuentes', 'Ninguna fuente activa ofrece títulos para adultos.')}` : `<p class="nj-meta">Títulos solo para adultos. Está oculta hasta que confirmes tu edad.</p>${btn('Mostrar zona +18', 'open', { kind: 'tonal', attrs: { 'data-arg': 'adult' } })}`}</section>`;
  }

  function mgStatusPicker(ctx, s) {
    return `<div class="nj-c-statuspick" data-region="status-picker" role="radiogroup" aria-label="Estado en tu lista">${MG_READ.map((o) => `<button type="button" role="radio" aria-checked="${s.status === o.id}" class="nj-chip nj-c-stchip" data-status="${o.id}" data-c="mgStatus" data-arg="${o.id}">${s.status === o.id ? ic('check', { size: 18 }) : ''}${o.label}</button>`).join('')}${s.status ? btn('Quitar de la lista', 'mgStatusDel', { kind: 'text' }) : ''}</div>`;
  }

  function mgChapterList(ctx, s) {
    const q = (s.chQ || '').toLowerCase();
    let chs = mgChapters().filter((c) => (s.chLang === 'all' || c.lang === s.chLang) && (s.chGroup === 'all' || c.group === s.chGroup) && (!q || (String(c.n) + c.title + c.group).toLowerCase().includes(q)));
    if (s.chOrder === 'old') chs = chs.reverse();
    const read = s.read; const next = read + 1;
    const row = (c) => {
      const isRead = c.n <= read; const sel = s.chSel.includes(c.n);
      return `<li class="nj-ep nj-c-chrow${isRead ? ' is-read' : ''}${sel ? ' is-sel' : ''}" data-ep="${c.n}" data-watched="${isRead}" data-next="${c.n === next}" data-aired="true" data-fk="ch-${c.n}">
        ${s.chSelMode ? check(sel, 'Seleccionar capítulo ' + c.n, 'chSel', c.n) : ''}
        <button type="button" class="nj-c-chbtn" data-c="${s.chSelMode ? 'chSel' : 'chOpen'}" data-arg="${c.n}"><span class="nj-c-chname">Cap. ${c.n}${c.title ? ' · ' + esc(c.title) : ''}${c.n === next ? ' ' + pill('Siguiente', 'accent') : ''}</span><span class="nj-meta">${esc(c.group)} · ${c.lang} · ${c.ago}${s.chVol ? '' : ' · Vol. ' + c.vol}</span></button>
        ${c.dl ? `<span class="nj-c-dlmark" title="Descargado">${ic('download_done', { size: 18 })}<span class="nj-c-sr">Descargado</span></span>` : ''}
        ${s.chSelMode ? '' : `<button type="button" class="nj-iconbtn nj-c-readbtn" aria-pressed="${isRead}" aria-label="${isRead ? 'Marcar capítulo ' + c.n + ' como no leído' : 'Marcar hasta el capítulo ' + c.n + ' como leído'}" title="${isRead ? 'Leído' : 'Marcar como leído'}" data-c="chRead" data-arg="${c.n}">${ic(isRead ? 'check_circle' : 'radio_button_unchecked', { fill: isRead })}</button>`}
      </li>`;
    };
    let body;
    if (!chs.length) body = empty('search_off', 'Ningún capítulo coincide', 'Cambia el filtro de idioma, grupo o número.', btn('Quitar filtros', 'chClear', { kind: 'text' }));
    else if (s.chVol) { const vols = {}; chs.forEach((c) => (vols[c.vol] = vols[c.vol] || []).push(c)); body = Object.entries(vols).sort((a, b) => (s.chOrder === 'old' ? a[0] - b[0] : b[0] - a[0])).map(([v, cs]) => `<li class="nj-c-volhead" role="presentation">Volumen ${v}</li>${cs.map(row).join('')}`).join(''); }
    else body = chs.map(row).join('');
    return `<ul class="nj-c-chlist" data-region="episodes" aria-label="Capítulos">${body}</ul>`;
  }

  function mgFicha(ctx, s) {
    const m = MG_ALL.find((x) => x.id === s.mSel) || MG_ALL[0]; const isA = A(ctx);
    const src = MG_SOURCES.find((x) => x.id === s.src);
    const head = `<section class="nj-c-mhero" data-region="hero" aria-labelledby="${pid(ctx, 'mt')}"><img class="nj-c-mcover" src="${esc(m.poster)}" alt="Portada de ${esc(m.title)}" width="${isA ? 120 : 180}" height="${isA ? 180 : 270}">
      <div class="nj-c-mheadtext"><div class="nj-cluster">${pill(m.format, 'accent')}${pill('Finalizado', 'neutral')}${pill(m.kind === 'manhwa' ? 'Corea' : m.kind === 'manhua' ? 'China' : 'Japón', 'neutral')}${pill('2018', 'neutral')}</div>
        <h1 class="nj-c-display" id="${pid(ctx, 'mt')}">${esc(m.title)}</h1>${m.id === 'm1' ? '<p class="nj-meta">Na Honjaman Level Up</p>' : ''}
        <div class="nj-cluster">${btn(s.read ? `Seguir en el cap. ${s.read + 1}` : 'Empezar a leer', 'go', { kind: 'filled', icon: 'menu_book', attrs: { 'data-arg': 'lector:paginado' } })}${stepper('read', 'Leídos', s.read, { min: 0, max: m.eps, fmt: (v) => `${v} / ${m.eps}` })}</div>
        ${mgStatusPicker(ctx, s)}</div></section>`;
    const sources = `<div class="nj-c-srcrow" role="radiogroup" aria-label="Fuente de capítulos">${MG_SOURCES.map((x) => `<button type="button" role="radio" class="nj-c-srcchip" aria-checked="${s.src === x.id}" data-c="srcPick" data-arg="${x.id}"${x.miss ? ' disabled' : ''}><span class="nj-c-srcname">${esc(x.name)}${s.prefSrc === x.id ? ' ' + ic('star', { size: 16, fill: true }) : ''}</span><span class="nj-meta">${x.miss ? esc(x.miss) : `${x.count} caps. · ${x.langs}`}</span>${x.miss ? '' : `<span class="nj-c-relbar" title="Fiabilidad ${pct(x.rel)}: cuántas páginas cargaron bien en tus lecturas" style="--v:${x.rel}"><i></i></span>`}</button>`).join('')}</div>`;
    const tools = `<div class="nj-c-chtools"><span class="nj-search nj-c-grow">${ic('filter_list')}<input type="search" data-model="chQ" value="${esc(s.chQ || '')}" placeholder="Filtrar por número, título o grupo" aria-label="Filtrar capítulos"></span>
      ${seg('chOrder', 'Orden', [{ id: 'new', label: 'Más nuevos' }, { id: 'old', label: 'Más antiguos' }], s.chOrder)}</div>
      <div class="nj-cluster">${chips('chLang', 'Idioma', [{ id: 'all', label: 'Todos los idiomas' }, { id: 'EN', label: 'Inglés' }, { id: 'ES', label: 'Español' }], s.chLang)}
        <span class="nj-c-menuwrap"><button type="button" class="nj-chip" data-c="menu" data-arg="grp" aria-pressed="${s.chGroup !== 'all'}">${ic('group', { size: 18 })}${s.chGroup === 'all' ? 'Grupo' : esc(s.chGroup)}${ic('arrow_drop_down', { size: 18 })}</button>${s.menu === 'grp' ? menuHTML([{ label: 'Todos los grupos', icon: s.chGroup === 'all' ? 'check' : 'blank', c: 'grpPick', arg: 'all' }].concat(MG_GROUPS.map((g) => ({ label: g, icon: s.chGroup === g ? 'check' : 'blank', c: 'grpPick', arg: g })))) : ''}</span>
        <button type="button" class="nj-chip" data-c="chVol" aria-pressed="${s.chVol}">${s.chVol ? ic('check', { size: 18 }) : ''}Agrupar por volumen</button></div>`;
    const chHead = `<div class="nj-c-sectionhead"><h2 class="nj-h2">Capítulos · ${src ? src.count : 0}</h2><span class="nj-c-grow"></span>
      ${s.chSelMode ? '' : `${btn('Seleccionar', 'chSelMode', { kind: 'text', icon: 'checklist' })}<span class="nj-c-menuwrap">${ibtn('more_vert', 'Más acciones de capítulos', 'menu', { attrs: { 'data-arg': 'chmore' } })}${s.menu === 'chmore' ? menuHTML([{ label: 'Actualizar la lista', icon: 'refresh', c: 'chRefresh' }, { label: 'Revisar todas las fuentes', icon: 'travel_explore', c: 'chCheckAll' }, '-', { label: 'Olvidar coincidencias y buscar de nuevo', icon: 'delete_sweep', c: 'chCache', danger: true }]) : ''}</span>`}</div>
      <div class="nj-c-sectionhead is-sub"><span class="nj-c-label">Fuente</span>${btn('Fuentes e idiomas', 'open', { kind: 'text', icon: 'translate', attrs: { 'data-arg': 'srcSheet' } })}</div>${sources}${tools}`;
    const info = `<section class="nj-c-panel"><h2 class="nj-h3">Información</h2><dl class="nj-c-kv"><dt>Autores</dt><dd>Chugong (historia), DUBU (arte)</dd><dt>Capítulos</dt><dd>${m.eps}</dd><dt>Volúmenes</dt><dd>14</dd><dt>Publicación</dt><dd>2018 a 2023</dd><dt>Editorial</dt><dd>D&amp;C Webtoon</dd><dt>Puntuación</dt><dd>86 % en AniList</dd></dl></section>
      <section class="nj-c-panel"><h2 class="nj-h3">Sinopsis</h2><p class="${s.synOpen ? '' : 'nj-c-clamp5'}">Hace diez años se abrieron puertas que conectan nuestro mundo con otro lleno de monstruos. Algunas personas despertaron poderes y se volvieron cazadores. Sung Jinwoo es el más débil de todos, hasta que una mazmorra doble le deja un sistema que solo él puede ver y que lo hace subir de nivel.</p>${btn(s.synOpen ? 'Menos' : 'Más', 'synToggle', { kind: 'text' })}</section>
      <section class="nj-c-panel"><h2 class="nj-h3">Géneros</h2><div class="nj-cluster">${['Acción', 'Aventura', 'Fantasía'].map((g) => `<button type="button" class="nj-chip" data-c="genreGo" data-arg="${g}">${g}</button>`).join('')}</div>
        <h3 class="nj-c-label">Etiquetas</h3><p class="nj-meta">Protagonista con poder oculto 94 % · Mazmorras 91 % · Sistema de niveles 88 %${s.spoil ? ' · Resurrección 61 %' : ''}</p>${btn(s.spoil ? 'Ocultar spoilers' : 'Mostrar etiquetas con spoilers', 'spoilToggle', { kind: 'text' })}</section>
      <section class="nj-c-panel"><h2 class="nj-h3">Adaptación al anime</h2><button type="button" class="nj-c-provrow" data-c="go" data-arg="ficha">${ic('live_tv')}<span class="nj-c-grow"><span class="nj-c-qtitle">Solo Leveling</span><span class="nj-meta">TV · 2 temporadas</span></span>${ic('chevron_right')}</button></section>
      <section class="nj-section nj-c-flush" data-region="row" data-row-title="Recomendaciones"><div class="nj-section-head"><h2 class="nj-h3">Si te gustó</h2></div><div class="nj-row">${MG_ALL.slice(4, 8).map((x) => h.card(x, { size: 's', context: 'row' })).join('')}</div></section>`;
    const chapters = `${chHead}${mgChapterList(ctx, s)}`;
    if (isA) {
      const tab = s.mTab;
      return `${head}<div class="nj-tabs" role="tablist" aria-label="Secciones de la ficha">${[['caps', 'Capítulos'], ['info', 'Información']].map(([id, l]) => `<button type="button" class="nj-tab" role="tab" aria-selected="${tab === id}" data-c="mTab" data-arg="${id}">${l}</button>`).join('')}</div><div class="nj-c-pad nj-c-stackgap">${tab === 'caps' ? chapters : info}</div>`;
    }
    return `${head}<div class="nj-c-fichagrid"><div class="nj-c-stackgap">${chapters}</div><aside class="nj-c-stackgap" aria-label="Información">${info}</aside></div>`;
  }

  NJ.registerScreen({
    id: 'manga', name: 'Manga', group: 'Leer', order: 1, platforms: ['desktop', 'android'],
    summary: 'El inicio de manga separa el orden de los filtros (que ahora sobreviven como chips) y la ficha suma selección por rango, marcar leídos en lote y fuentes legibles.',
    parity: 'MNG-01…MNG-11, NOV-01…NOV-08 (las novelas usan esta misma pantalla con el selector Manga/Novelas; MNG-05 y NOV-03 viven en Biblioteca)',
    structures: [
      { id: 'catalogo', name: 'Inicio de manga', desc: 'Destacado, seguir leyendo, capítulos nuevos, tendencias, géneros y zona +18; con búsqueda y filtros persistentes.' },
      { id: 'ficha', name: 'Ficha de manga', desc: 'Portada y estado de lista, fuente e idioma, capítulos con selección múltiple para descargar o marcar leídos.' },
    ],
    render(ctx) {
      const s = init(ctx, { medium: 'manga', q: '', f: { origin: [], status: null, genre: [], y0: null, y1: null }, sort: 'popular', y0: 2015, y1: 2026, news: ['m1', 'm4', 'm7'], adult: false, mSel: 'm1', status: 'watching', read: 179, src: 'kumo', prefSrc: 'kumo', chQ: '', chOrder: 'new', chLang: 'all', chGroup: 'all', chVol: false, chSel: [], chSelMode: false, mTab: 'caps', langs: ['es', 'en'], synOpen: false });
      const isA = A(ctx);
      let main;
      if (ctx.structure === 'ficha') {
        const nSel = s.chSel.length;
        const headA = s.chSelMode ? `<header class="nj-appbar nj-c-appbar is-context">${ibtn('close', 'Salir de la selección', 'chSelExit')}<h1 class="nj-h2 nj-c-grow">${nSel} seleccionados</h1>${ibtn('select_all', 'Seleccionar todo lo visible', 'chAll')}</header>` : pageHead(ctx, { title: '', back: 'go', actionsA: ibtn('share', 'Compartir', 'share') }).replace('data-c="go"', 'data-c="go" data-arg="manga:catalogo"');
        main = `${isA ? headA : ''}<div class="nj-c-body${isA ? '' : ' nj-c-pad'}" data-keep="main">${isA ? '' : `<nav class="nj-c-crumbs" aria-label="Ruta">${btn('Manga', 'go', { kind: 'text', icon: 'arrow_back', attrs: { 'data-arg': 'manga:catalogo' } })}</nav>`}${mgFicha(ctx, s)}<div class="nj-c-fabspace"></div></div>
          ${s.chSelMode ? `<div class="nj-c-batch" role="toolbar" aria-label="Acciones con capítulos"><span class="nj-c-batchcount">${nSel ? nSel + ' capítulos' : 'Toca capítulos; Mayús para un rango'}</span>${btn('No leídos', 'chUnread', { kind: 'text' })}${btn('Todo lo visible', 'chAll', { kind: 'text' })}<span class="nj-c-batchsep" aria-hidden="true"></span>${btn('Marcar leídos', 'chMarkRead', { kind: 'text', icon: 'done_all' })}${btn('Marcar no leídos', 'chMarkUnread', { kind: 'text', icon: 'remove_done' })}<button type="button" class="nj-btn is-filled" data-c="chDownload"${nSel ? '' : ' disabled'}>${ic('download')}Descargar ${nSel || ''}</button>${isA ? '' : ibtn('close', 'Salir de la selección', 'chSelExit')}</div>` : ''}`;
      } else {
        const active = s.q || s.f.origin.length || s.f.status || s.f.genre.length || s.f.y0 != null || s.sort !== 'popular';
        const novel = s.medium === 'novela';
        main = `${pageHead(ctx, { title: novel ? 'Novelas' : 'Manga', meta: novel ? '' : 'Desde Kumo y Toko · actualizado hace 12 min', actions: `${seg('medium', 'Medio', [{ id: 'manga', label: 'Manga' }, { id: 'novela', label: 'Novelas' }], s.medium)}${ibtn('refresh', 'Actualizar el catálogo', 'mgRefresh')}`, actionsA: ibtn('refresh', 'Actualizar el catálogo', 'mgRefresh') })}
          <div class="nj-c-body nj-c-pad nj-c-stackgap" data-keep="main">
            ${isA ? seg('medium', 'Medio', [{ id: 'manga', label: 'Manga' }, { id: 'novela', label: 'Novelas' }], s.medium) : ''}
            ${novel ? `${empty('auto_stories', 'No tienes una fuente de novelas que funcione', 'Fumi está instalada pero no es compatible con esta versión. Instala otra extensión de novelas para ver el catálogo.', `<div class="nj-cluster">${btn('Buscar en la tienda', 'go', { kind: 'filled', icon: 'storefront', attrs: { 'data-arg': 'fuentes:tienda' } })}${btn('Ver por qué', 'go', { kind: 'text', attrs: { 'data-arg': 'fuentes:pestanas' } })}</div>`)}<p class="nj-meta nj-c-center">Con una fuente activa, Novelas usa este mismo inicio: búsqueda, filtros por estado, origen y orden, seguir leyendo y capítulos nuevos.</p>`
              : `<div class="nj-c-searchrow" data-c="noop"><span class="nj-search nj-c-grow">${ic('search')}<input type="search" data-model="qDraft" value="${esc(s.qDraft != null ? s.qDraft : s.q)}" placeholder="Buscar manga" aria-label="Buscar manga" enterkeyhint="search"></span>${s.q ? btn('Borrar', 'qClear', { kind: 'text' }) : ''}</div>${mgFilterChips(ctx, s)}${active ? mgResults(ctx, s) : mgFeed(ctx, s)}`}
          </div>`;
      }
      let ov = '';
      if (s.ov === 'adult') ov = dialogHTML(ctx, { id: 'adult', title: 'Contenido para adultos', icon: 'shield', body: '<p>Esta zona lista títulos solo para adultos. Confirma que tienes la edad legal para verla.</p>', actions: `${btn('Cancelar', 'close', { kind: 'text' })}${btn('Tengo 18 años o más', 'adultOk', { kind: 'filled' })}` });
      else if (s.ov === 'srcSheet') {
        const LANGS = [['es', 'Español'], ['en', 'Inglés'], ['pt', 'Portugués'], ['fr', 'Francés'], ['de', 'Alemán'], ['it', 'Italiano'], ['ja', 'Japonés'], ['ko', 'Coreano'], ['zh', 'Chino'], ['id', 'Indonesio']];
        const nameOf = (id) => (LANGS.find((l) => l[0] === id) || [id, id])[1];
        ov = sheetHTML(ctx, { id: 'srcSheet', title: 'Fuentes e idiomas', body: `<section class="nj-c-sub"><h3 class="nj-c-label">Idiomas en orden de preferencia</h3><p class="nj-meta">Los capítulos en el primero salen antes. El idioma de la app y el inglés siempre se suman.</p>
          <ol class="nj-c-plainlist nj-c-langorder">${s.langs.map((l, i) => `<li data-flip="lg-${l}"><span class="nj-c-grow">${nameOf(l)}</span>${ibtn('arrow_upward', 'Subir ' + nameOf(l), 'langUp', { attrs: { 'data-arg': i } })}${ibtn('close', 'Quitar ' + nameOf(l), 'langDel', { attrs: { 'data-arg': i } })}</li>`).join('')}</ol>
          <div class="nj-cluster">${LANGS.filter((l) => !s.langs.includes(l[0])).map((l) => `<button type="button" class="nj-chip" data-c="langAdd" data-arg="${l[0]}">${ic('add', { size: 18 })}${l[1]}</button>`).join('')}</div></section>
          <section class="nj-c-sub"><h3 class="nj-c-label">Fuente para este título</h3><div class="nj-c-radiolist" role="radiogroup" aria-label="Fuente preferida">
          <button type="button" role="radio" aria-checked="${!s.prefSrc}" class="nj-c-radiorow" data-c="prefPick" data-arg="">${ic(!s.prefSrc ? 'radio_button_checked' : 'radio_button_unchecked')}<span class="nj-c-grow"><b>Automático</b><span class="nj-meta"> · la mejor por idioma y fiabilidad</span></span></button>
          ${MG_SOURCES.map((x) => `<button type="button" role="radio" aria-checked="${s.prefSrc === x.id}" class="nj-c-radiorow" data-c="prefPick" data-arg="${x.id}"${x.miss ? ' disabled' : ''}>${ic(s.prefSrc === x.id ? 'radio_button_checked' : 'radio_button_unchecked')}<span class="nj-c-grow"><b>${esc(x.name)}</b><span class="nj-meta"> · ${x.miss ? esc(x.miss) : `${x.count} caps. · ${x.langs} · fiable ${pct(x.rel)}`}</span></span></button>`).join('')}</div></section>
          ${btn('Administrar extensiones', 'go', { kind: 'text', icon: 'extension', attrs: { 'data-arg': 'fuentes' } })}` });
      }
      return `<div class="nj-c-root nj-c-mg${s._selIn ? ' is-selin' : ''}" data-local data-cards-local>${main}${ov}${snackHTML(s)}</div>`;
    },
    mount(el, ctx) {
      const s = ctx.state; const re = () => rr(ctx, el);
      const visible = () => { const q = (s.chQ || '').toLowerCase(); return mgChapters().filter((c) => (s.chLang === 'all' || c.lang === s.chLang) && (s.chGroup === 'all' || c.group === s.chGroup) && (!q || (String(c.n) + c.title + c.group).toLowerCase().includes(q))).map((c) => c.n); };
      const H = {
        $steps: { read: { min: 0, max: 200 }, y0: { min: 1950, max: 2027 }, y1: { min: 1950, max: 2027 } },
        $chip(k, v) { if (k === 'sortPick') { s.sort = v; s.menu = null; re(); return false; } return true; },
        $text(k, v, n) { if (k === 'chQ') { n.setAttribute('data-fk', 'in-chQ'); clearTimeout(H._t); H._t = setTimeout(re, 250); } },
        $key(e) { if (e.key === 'Enter' && e.target.matches && e.target.matches('input[data-model="qDraft"]')) { e.preventDefault(); s.q = (s.qDraft || '').trim(); re(); } },
        qClear() { s.q = ''; s.qDraft = ''; re(); },
        fPick(t) { const [k, v] = t.getAttribute('data-arg').split(':'); const d = MG_F[k]; if (d.multi) { const a = s.f[k]; const i = a.indexOf(v); if (i >= 0) a.splice(i, 1); else a.push(v); } else s.f[k] = s.f[k] === v ? null : v; re(); },
        fDrop(t) { const k = t.getAttribute('data-arg'); if (k === 'year') { s.f.y0 = null; s.f.y1 = null; } else s.f[k] = MG_F[k].multi ? [] : null; re(); },
        fClear() { s.f = { origin: [], status: null, genre: [], y0: null, y1: null }; s.q = ''; s.qDraft = ''; s.sort = 'popular'; re(); },
        yearApply() { s.f.y0 = Math.min(s.y0, s.y1); s.f.y1 = Math.max(s.y0, s.y1); s.menu = null; re(); },
        yearClear() { s.f.y0 = null; s.f.y1 = null; s.menu = null; re(); },
        sortTo(t) { s.sort = t.getAttribute('data-arg'); re(); },
        genreGo(t) { const g = t.getAttribute('data-arg'); s.f.genre = [g]; if (ctx.structure !== 'catalogo') NJ.go('manga', { structure: 'catalogo' }); else re(); },
        newsDrop(t) { flipSave(ctx, el); const id = t.getAttribute('data-arg'); s._news = s.news.slice(); s.news = s.news.filter((x) => x !== id); snack(ctx, 'Aviso descartado', 'Deshacer', 'newsUndo'); re(); },
        newsUndo() { if (s._news) s.news = s._news; s.snack = null; re(); },
        adultOk() { s.adult = true; s.ov = null; re(); },
        adultHide() { s.adult = false; re(); },
        mgRefresh() { snack(ctx, 'Catálogo actualizado'); re(); },
        mgOpen(t) { s.mSel = t.getAttribute('data-arg'); NJ.go('manga', { structure: 'ficha' }); },
        mgStatus(t) { s.status = t.getAttribute('data-arg'); snack(ctx, 'Estado guardado y enviado a AniList'); re(); },
        mgStatusDel() { s.status = null; re(); },
        srcPick(t) { s.src = t.getAttribute('data-arg'); s.chLang = 'all'; re(); },
        prefPick(t) { s.prefSrc = t.getAttribute('data-arg') || null; if (s.prefSrc) s.src = s.prefSrc; re(); },
        langUp(t) { const i = Number(t.getAttribute('data-arg')); if (i > 0) { flipSave(ctx, el); const a = s.langs; [a[i - 1], a[i]] = [a[i], a[i - 1]]; } re(); },
        langDel(t) { s.langs.splice(Number(t.getAttribute('data-arg')), 1); re(); },
        langAdd(t) { s.langs.push(t.getAttribute('data-arg')); re(); },
        grpPick(t) { s.chGroup = t.getAttribute('data-arg'); s.menu = null; re(); },
        chVol() { s.chVol = !s.chVol; re(); },
        chClear() { s.chQ = ''; s.chLang = 'all'; s.chGroup = 'all'; re(); },
        chOpen() { NJ.go('lector', { structure: 'paginado' }); },
        chRead(t) { const n = Number(t.getAttribute('data-arg')); s.read = n <= s.read ? n - 1 : n; re(); },
        chSelMode() { s.chSelMode = true; s.chSel = []; s._selIn = true; re(); },
        chSelExit() { s.chSelMode = false; s.chSel = []; re(); },
        chSel(t, e) { const n = Number(t.getAttribute('data-arg')); const vis = visible(); if (e.shiftKey && s.anchor != null) { const a = vis.indexOf(s.anchor); const b = vis.indexOf(n); vis.slice(Math.min(a, b), Math.max(a, b) + 1).forEach((x) => { if (!s.chSel.includes(x)) s.chSel.push(x); }); } else { const i = s.chSel.indexOf(n); if (i >= 0) s.chSel.splice(i, 1); else s.chSel.push(n); s.anchor = n; } re(); },
        chAll() { s.chSel = visible(); re(); },
        chUnread() { s.chSel = visible().filter((n) => n > s.read); re(); },
        chMarkRead() { const mx = Math.max.apply(null, s.chSel.concat([s.read])); s.read = mx; snack(ctx, `Marcados como leídos hasta el cap. ${mx}`); s.chSel = []; s.chSelMode = false; re(); },
        chMarkUnread() { const mn = Math.min.apply(null, s.chSel); if (isFinite(mn)) s.read = Math.min(s.read, mn - 1); snack(ctx, 'Marcados como no leídos'); s.chSel = []; s.chSelMode = false; re(); },
        chDownload() { const n = s.chSel.length; s.chSel = []; s.chSelMode = false; snack(ctx, `${n} capítulos en cola para descargar`, 'Ver descargas', 'goDl'); re(); },
        goDl() { NJ.go('transferencias', { structure: 'cola' }); },
        chRefresh() { s.menu = null; snack(ctx, 'Lista de capítulos actualizada'); re(); },
        chCheckAll() { s.menu = null; snack(ctx, 'Kumo 200 · Toko 179 · Fumi no encontró el título'); re(); },
        chCache() { s.menu = null; snack(ctx, 'Coincidencias olvidadas; se buscó de nuevo en tus fuentes'); re(); },
        mTab(t) { s.mTab = t.getAttribute('data-arg'); re(); },
        synToggle() { s.synOpen = !s.synOpen; re(); },
        spoilToggle() { s.spoil = !s.spoil; re(); },
        share() { snack(ctx, 'Elige una app para compartir'); re(); },
      };
      wire(el, ctx, H);
      flipPlay(el, ctx);
      s._selIn = false;
      el.addEventListener('click', (e) => { const c = e.target.closest('.nj-card[data-id]'); if (!c || !el.contains(c)) return; const id = c.getAttribute('data-id'); if (MG_ALL.some((m) => m.id === id)) { s.mSel = id; NJ.go('manga', { structure: 'ficha' }); } });
      if (A(ctx)) longPress(el, '.nj-c-chrow', (row) => { s.chSelMode = true; s._selIn = true; const n = Number(row.getAttribute('data-ep')); if (!s.chSel.includes(n)) s.chSel.push(n); re(); });
    },
  });

  /* =====================================================================
     5. LECTOR: manga página a página, webtoon en cascada y novela con narrador
     ===================================================================== */
  const RD_PAGES = (D.manga || []).map((m) => m.poster);
  const pageImg = (i, alt) => `<img src="${esc(RD_PAGES[(i - 1) % RD_PAGES.length])}" alt="${esc(alt || 'Página ' + i)}" width="720" height="1080" decoding="async"${i > 2 ? ' loading="lazy"' : ''}>`;
  const RD_TOTAL = 38;
  const RD_ZONES = [
    { id: 'tercios', label: 'Tercios', desc: 'Izquierda, centro y derecha' },
    { id: 'ele', label: 'En L', desc: 'Menú arriba, anterior a la izquierda' },
    { id: 'bordes', label: 'Bordes', desc: 'Franjas finas para pasar, centro amplio' },
    { id: 'off', label: 'Sin zonas', desc: 'Solo deslizar y teclado' },
  ];
  const NOVEL_FONTS = [{ id: 'serif', label: 'Serif', css: 'Georgia, "Times New Roman", serif' }, { id: 'sans', label: 'Sans', css: 'var(--nj-font-ui)' }, { id: 'lora', label: 'Lora', css: 'Lora, Georgia, serif' }, { id: 'dys', label: 'Dislexia', css: 'Verdana, Tahoma, sans-serif' }, { id: 'mono', label: 'Mono', css: 'var(--nj-font-mono)', desktop: true }, { id: 'system', label: 'Sistema', css: 'system-ui, sans-serif' }];
  const NOVEL_TEXT = [
    'La lluvia había dejado el patio lleno de charcos que reflejaban las linternas del templo. Mei se detuvo en el último escalón, con el paraguas cerrado bajo el brazo, y escuchó.',
    'Nadie la había seguido. O eso quería creer. Desde la tarde en el mercado sentía una mirada clavada en la nuca, la misma que la había acompañado tres noches seguidas.',
    '«No deberías estar aquí tan tarde», dijo una voz detrás de las columnas. Era el guardián, un hombre mayor que barría las hojas aunque nunca dejaban de caer.',
    'Ella sonrió sin darse vuelta. Sabía que el guardián conocía los nombres de todos los que pasaban por ese portal, y también los nombres de quienes nunca volvían.',
    'Sacó del bolsillo la carta arrugada. La tinta se había corrido en una esquina, pero la dirección seguía clara: el pabellón norte, a la hora del buey.',
    'Cuando el viento movió las campanas, Mei por fin entendió que la mirada no venía de afuera. Venía de la carta, y la carta la estaba esperando a ella.',
  ];
  const VOICES = {
    system: [['Español (México)', ['Sabina', 'Jorge']], ['Español (España)', ['Lucía']], ['Inglés (EE. UU.)', ['Samantha', 'Alex']]],
    kitten: [['Inglés (EE. UU.)', ['Bella', 'Jasper', 'Luna', 'Bruno', 'Rosie', 'Hugo', 'Kiki', 'Leo']]],
    openai: [['Varios idiomas', ['alloy', 'ash', 'coral', 'echo', 'nova', 'sage']]],
  };
  const ENGINES = [
    { id: 'system', label: 'Voz del sistema', tag: 'Local', state: 'ok', note: 'Usa las voces instaladas en el equipo.' },
    { id: 'kitten', label: 'KittenTTS', tag: 'Local · sin conexión', state: 'ok', note: 'Modelo descargado y verificado (82 MB). Solo lee en inglés.', desktop: true },
    { id: 'openai', label: 'OpenAI', tag: 'Nube · necesita clave', state: 'key', note: 'Falta la clave de API.' },
    { id: 'eleven', label: 'ElevenLabs', tag: 'Nube · necesita clave', state: 'key', note: 'Falta la clave de API.' },
    { id: 'local', label: 'Servidor local', tag: 'Compatible con OpenAI', state: 'setup', note: 'Sin URL de servidor.' },
  ];

  function rdTop(ctx, s, novel) {
    const title = novel ? 'El pabellón del norte' : 'Solo Leveling';
    const sub = novel ? 'Cap. 12 · La carta · Shiori' : `Cap. 180 · Epílogo · ${s.rdSrc === 'toko' ? 'Toko' : 'Kumo'}`;
    const more = s.menu === 'rdmore' ? menuHTML(novel ? [{ label: 'Todos los ajustes del lector', icon: 'settings', c: 'go', arg: 'ajustes' }] : [{ label: 'Cambiar fuente', icon: 'swap_horiz', c: 'open', arg: 'source' }, { label: 'Guardar imagen de la página', icon: 'image', c: 'saveImg' }, { label: 'Recargar páginas', icon: 'refresh', c: 'reload', kbd: 'R' }, '-', { label: 'Todos los ajustes del lector', icon: 'settings', c: 'go', arg: 'ajustes' }]) : '';
    return `<header class="nj-c-rtop" data-chrome>${ibtn('arrow_back', 'Salir del lector', 'go', { attrs: { 'data-arg': novel ? 'manga:catalogo' : 'manga:ficha' } })}<div class="nj-c-grow nj-c-rtitle"><span class="nj-c-qtitle">${title}</span><span class="nj-meta">${sub}</span></div>
      ${ibtn('format_list_bulleted', 'Capítulos', 'open', { attrs: { 'data-arg': 'chapters' } })}${ibtn(novel ? 'text_fields' : 'tune', novel ? 'Texto y diseño' : 'Ajustes del lector', 'open', { attrs: { 'data-arg': 'settings' } })}${A(ctx) ? '' : ibtn(s.full ? 'fullscreen_exit' : 'fullscreen', s.full ? 'Salir de pantalla completa (F)' : 'Pantalla completa (F)', 'fullToggle')}<span class="nj-c-menuwrap">${ibtn('more_vert', 'Más opciones', 'menu', { attrs: { 'data-arg': 'rdmore' } })}${more}</span></header>`;
  }

  function rdZoneOverlay(s) {
    if (s.zones === 'off') return '';
    return `<div class="nj-c-zones${s.zonesShow ? ' is-shown' : ''}" data-layout="${s.zones}" aria-hidden="true"><i data-z="prev"><span>${s.dir === 'rtl' ? 'Siguiente' : 'Anterior'}</span></i><i data-z="menu"><span>Mostrar u ocultar controles</span></i><i data-z="next"><span>${s.dir === 'rtl' ? 'Anterior' : 'Siguiente'}</span></i></div>`;
  }

  function rdMangaSettings(ctx, s) {
    const m = s.rmode; const paged = m === 'paged'; const scroll = !paged; const web = m === 'webtoon';
    const tile = (k, id, icon, label, cur) => `<button type="button" role="radio" aria-checked="${cur === id}" class="nj-c-vtile" data-c="rdSet" data-arg="${k}:${id}">${icon}<span>${label}</span></button>`;
    const fitIcon = (f) => `<svg viewBox="0 0 40 30" class="nj-c-fitsvg" aria-hidden="true"><rect x="1" y="1" width="38" height="28" rx="3" class="nj-c-fitframe"/>${f === 'width' ? '<rect x="4" y="-6" width="32" height="42" class="nj-c-fitpage"/>' : f === 'height' ? '<rect x="12" y="4" width="16" height="22" class="nj-c-fitpage"/>' : f === 'screen' ? '<rect x="9" y="3" width="22" height="24" class="nj-c-fitpage"/>' : '<rect x="14" y="8" width="12" height="15" class="nj-c-fitpage"/>'}</svg>`;
    return `<section class="nj-c-sub"><h3 class="nj-c-label">Modo de lectura</h3><div class="nj-c-vtiles" role="radiogroup" aria-label="Modo de lectura">${tile('rmode', 'paged', ic('auto_stories', { size: 26 }), 'Página a página', m)}${tile('rmode', 'vertical', ic('view_day', { size: 26 }), 'Vertical', m)}${tile('rmode', 'webtoon', ic('splitscreen_bottom', { size: 26 }), 'Webtoon', m)}</div>
        <p class="nj-meta">${paged ? 'Se pasa de página tocando los bordes o deslizando.' : web ? 'Tira continua sin separación, al ancho de la pantalla.' : 'Páginas una debajo de otra, con separación.'}</p></section>
      ${paged ? `<section class="nj-c-sub"><h3 class="nj-c-label">Dirección</h3><div class="nj-c-vtiles is-two" role="radiogroup" aria-label="Dirección">${tile('dir', 'rtl', ic('west', { size: 24 }), 'Derecha a izquierda', s.dir)}${tile('dir', 'ltr', ic('east', { size: 24 }), 'Izquierda a derecha', s.dir)}</div>${A(ctx) ? '' : tog(ctx, 'dbl', 'Doble página', s.dbl, 'Dos páginas lado a lado en pantallas anchas.')}</section>` : ''}
      ${web ? '' : `<section class="nj-c-sub"><h3 class="nj-c-label">Ajuste de la página</h3><div class="nj-c-vtiles is-four" role="radiogroup" aria-label="Ajuste">${[['width', 'Ancho'], ['height', 'Alto'], ['screen', 'Pantalla'], ['natural', 'Natural']].map(([id, l]) => tile('fit', id, fitIcon(id), l, s.fit)).join('')}</div>
        ${sld(ctx, 'maxw', 'Ancho máximo', { min: 0, max: 4000, step: 50, value: s.maxw, unit: s.maxw ? ' dp' : '' })}${m === 'vertical' ? sld(ctx, 'gap', 'Separación entre páginas', { min: 0, max: 64, step: 4, value: s.gap, unit: ' dp' }) : ''}</section>`}
      <section class="nj-c-sub"><h3 class="nj-c-label">Imagen</h3>${tog(ctx, 'crop', 'Recortar bordes', s.crop, 'Quita los márgenes blancos o negros de los escaneos.')}
        <div class="nj-c-field"><span class="nj-c-label">Fondo</span><div class="nj-c-swatches" role="radiogroup" aria-label="Fondo">${[['black', 'Negro'], ['gray', 'Gris'], ['white', 'Blanco'], ['theme', 'Tema']].map(([id, l]) => `<button type="button" role="radio" aria-checked="${s.bg === id}" class="nj-c-swatch" data-sw="${id}" data-c="rdSet" data-arg="bg:${id}"><i aria-hidden="true"></i>${l}</button>`).join('')}</div></div>
        ${tog(ctx, 'sysBright', 'Usar el brillo del sistema', s.sysBright)}${s.sysBright ? '' : sld(ctx, 'bright', 'Brillo', { min: A(ctx) ? 5 : 20, max: 100, step: 5, value: s.bright, unit: ' %' })}</section>
      ${paged ? `<section class="nj-c-sub"><div class="nj-c-panelhead"><h3 class="nj-c-label nj-c-grow">Zonas táctiles</h3>${btn(s.zonesShow ? 'Ocultar zonas' : 'Ver zonas en la página', 'zonesShow', { kind: 'text', icon: 'touch_app' })}</div>
        <div class="nj-c-zonepick" role="radiogroup" aria-label="Distribución de zonas">${RD_ZONES.map((z) => `<button type="button" role="radio" aria-checked="${s.zones === z.id}" class="nj-c-zonetile" data-c="rdSet" data-arg="zones:${z.id}"><span class="nj-c-zonemini" data-layout="${z.id}" aria-hidden="true"><i data-z="prev"></i><i data-z="menu"></i><i data-z="next"></i></span><b>${z.label}</b><span class="nj-meta">${z.desc}</span></button>`).join('')}</div></section>` : ''}
      ${scroll ? `<section class="nj-c-sub"><h3 class="nj-c-label">Desplazamiento</h3>${tog(ctx, 'infinite', 'Seguir con el siguiente capítulo', s.infinite, 'Al llegar al final, el siguiente aparece debajo.')}${sld(ctx, 'auto', 'Velocidad del autodesplazamiento', { min: 0, max: 2000, step: 20, value: s.auto, unit: ' px/s' })}</section>` : ''}
      <section class="nj-c-sub"><h3 class="nj-c-label">Carga</h3>${tog(ctx, 'eager', 'Cargar el capítulo entero', s.eager, 'Apagado: carga las páginas cercanas a la que lees.')}${s.eager ? '' : sld(ctx, 'pre', 'Páginas por adelantado', { min: 1, max: 20, value: s.pre, unit: '' })}</section>
      <section class="nj-c-sub"><h3 class="nj-c-label">Comportamiento</h3>${tog(ctx, 'fullOpen', 'Pantalla completa al abrir', s.fullOpen)}${tog(ctx, 'tapHide', 'Tocar el centro oculta los controles', s.tapHide)}${tog(ctx, 'showProg', 'Mostrar el número de página', s.showProg)}${A(ctx) ? '' : tog(ctx, 'zoomKeys', 'Atajos de zoom', s.zoomKeys, 'Ctrl + rueda y las teclas = y −.')}</section>
      ${btn('Atajos de teclado y todos los ajustes', 'go', { kind: 'text', icon: 'keyboard', attrs: { 'data-arg': 'ajustes' } })}`;
  }

  function rdNovelSettings(ctx, s) {
    const fonts = NOVEL_FONTS.filter((f) => !f.desktop || !A(ctx));
    return `<div class="nj-c-preview" data-preview aria-label="Vista previa" style="${rdNovelVars(s)}"><p>La lluvia había dejado el patio lleno de charcos que reflejaban las linternas del templo.</p><p>Nadie la había seguido. O eso quería creer.</p></div>
      <section class="nj-c-sub"><h3 class="nj-c-label">Tipografía</h3><div class="nj-c-fontchips" role="radiogroup" aria-label="Fuente">${fonts.map((f) => `<button type="button" role="radio" aria-checked="${s.font === f.id}" class="nj-chip" data-c="rdSet" data-arg="font:${f.id}" style="font-family:${f.css}">${f.label}</button>`).join('')}${A(ctx) ? '' : `<button type="button" class="nj-chip" data-c="fontInstalled">${ic('font_download', { size: 18 })}Fuente instalada…</button>`}<button type="button" class="nj-chip" data-c="fontImport">${ic('upload_file', { size: 18 })}Importar TTF u OTF</button></div>
        ${sld(ctx, 'size', 'Tamaño', { min: 10, max: 40, value: s.size, unit: ' sp' })}${sld(ctx, 'lh', 'Interlineado', { min: 10, max: 25, value: s.lh, unit: '' })}${sld(ctx, 'para', 'Espacio entre párrafos', { min: 0, max: 48, step: 2, value: s.para, unit: ' dp' })}${sld(ctx, 'width', 'Ancho máximo (márgenes)', { min: 400, max: 1600, step: 50, value: s.width, unit: ' dp' })}${tog(ctx, 'justify', 'Justificar el texto', s.justify)}</section>
      <section class="nj-c-sub"><h3 class="nj-c-label">Colores</h3><div class="nj-c-swatches" role="radiogroup" aria-label="Colores">${[['theme', 'Tema'], ['sepia', 'Sepia'], ['white', 'Blanco'], ['gray', 'Gris'], ['black', 'Negro']].map(([id, l]) => `<button type="button" role="radio" aria-checked="${s.nbg === id}" class="nj-c-swatch" data-sw="${id}" data-c="rdSet" data-arg="nbg:${id}"><i aria-hidden="true"></i>${l}</button>`).join('')}</div></section>
      <section class="nj-c-sub"><h3 class="nj-c-label">Flujo de lectura</h3>${tog(ctx, 'cont', 'Desplazamiento continuo', s.cont, 'El siguiente capítulo sigue debajo del actual.')}${tog(ctx, 'nprog', 'Barra de progreso', s.nprog)}${tog(ctx, 'fullOpen', 'Pantalla completa al abrir', s.fullOpen)}${tog(ctx, 'awake', 'Mantener la pantalla encendida', s.awake)}</section>
      ${btn('Todos los ajustes del lector', 'go', { kind: 'text', icon: 'settings', attrs: { 'data-arg': 'ajustes' } })}`;
  }
  function rdNovelVars(s) { const f = NOVEL_FONTS.find((x) => x.id === s.font) || NOVEL_FONTS[0]; return `--rd-font:${f.css};--rd-size:${s.size}px;--rd-lh:${(s.lh / 10).toFixed(1)};--rd-para:${s.para}px;--rd-w:${s.width}px;--rd-align:${s.justify ? 'justify' : 'start'}`; }

  function rdVoiceSheet(ctx, s) {
    const engines = ENGINES.filter((e) => !e.desktop || !A(ctx));
    const eng = engines.find((e) => e.id === s.engine) || engines[0];
    const groups = VOICES[eng.id] || [];
    const q = (s.voiceQ || '').toLowerCase();
    const voices = eng.state === 'ok' ? groups.map(([g, vs]) => [g, vs.filter((v) => !q || v.toLowerCase().includes(q))]).filter(([, vs]) => vs.length) : [];
    return sheetHTML(ctx, { id: 'voice', title: 'Voz del narrador', body: `<div class="nj-c-engines" role="radiogroup" aria-label="Motor de voz">${engines.map((e) => `<button type="button" role="radio" aria-checked="${eng.id === e.id}" class="nj-c-engine" data-c="engine" data-arg="${e.id}"><b>${e.label}</b><span class="nj-meta">${e.tag}</span>${e.state === 'ok' ? pill('Lista', 'ok') : pill(e.state === 'key' ? 'Falta la clave' : 'Sin configurar', 'warn')}</button>`).join('')}</div>
      <p class="nj-meta">${esc(eng.note)}</p>
      ${eng.id === 'kitten' ? `<div class="nj-c-banner" data-tone="warn">${ic('translate')}<div class="nj-c-grow"><b>Este capítulo está en español</b><p class="nj-meta">KittenTTS solo lee inglés; sonará con acento y errores.</p></div></div>` : ''}
      ${eng.state === 'ok' ? `<span class="nj-search">${ic('search')}<input type="search" data-model="voiceQ" value="${esc(s.voiceQ || '')}" placeholder="Buscar voz" aria-label="Buscar voz"></span>
        <div class="nj-c-voicelist" role="radiogroup" aria-label="Voces">${voices.map(([g, vs]) => `<h3 class="nj-c-label">${esc(g)}</h3>${vs.map((v) => `<div class="nj-c-voicerow"><button type="button" role="radio" aria-checked="${s.voice === v}" class="nj-c-radiorow" data-c="voicePick" data-arg="${esc(v)}">${ic(s.voice === v ? 'radio_button_checked' : 'radio_button_unchecked')}<span class="nj-c-grow">${esc(v)}</span></button>${ibtn(s.testing === v ? 'stop' : 'play_arrow', s.testing === v ? 'Detener muestra' : 'Escuchar ' + v, 'voiceTest', { attrs: { 'data-arg': v } })}</div>`).join('')}`).join('') || '<p class="nj-meta">Ninguna voz coincide.</p>'}</div>
        ${eng.id === 'system' && !A(ctx) ? sld(ctx, 'pitch', 'Tono', { min: 5, max: 20, value: s.pitch, unit: '' }) : ''}${tog(ctx, 'hl', 'Resaltar el párrafo que se lee', s.hl)}`
        : `<div class="nj-c-banner" data-tone="info">${ic('key')}<div class="nj-c-grow"><b>${eng.label} necesita configuración</b><p class="nj-meta">La clave, el modelo y la URL se guardan en Ajustes › Lectores y narrador.</p></div>${btn('Configurar', 'go', { kind: 'tonal', attrs: { 'data-arg': 'ajustes' } })}</div>`}` });
  }

  function rdNarrator(ctx, s) {
    const st = s.tts; const isA = A(ctx);
    const voiceLabel = `${s.voice} · ${(ENGINES.find((e) => e.id === s.engine) || ENGINES[0]).label}`;
    return `<div class="nj-c-narrator${st === 'idle' ? ' is-idle' : ''}" role="region" aria-label="Narrador">
      ${st === 'idle' ? btn('Leer en voz alta', 'ttsPlay', { kind: 'tonal', icon: 'record_voice_over' }) : `<div class="nj-c-ttsmain">${ibtn(st === 'speaking' ? 'pause' : 'play_arrow', st === 'speaking' ? 'Pausar narración' : 'Reanudar narración', st === 'speaking' ? 'ttsPause' : 'ttsPlay', { kind: 'filled', fill: true })}${ibtn('skip_next', 'Siguiente párrafo', 'ttsNext')}${ibtn('stop', 'Detener narración', 'ttsStop')}</div>`}
      <button type="button" class="nj-chip nj-c-voicechip" data-c="open" data-arg="voice" aria-haspopup="dialog">${ic('voice_selection', { size: 18 })}<span class="nj-c-ellipsis">${esc(voiceLabel)}</span></button>
      ${st === 'idle' ? '' : `<div class="nj-c-ttsopts${isA ? ' is-wrap' : ''}"><span class="nj-c-label">Velocidad</span>${chips('speed', 'Velocidad', [{ id: '0.8', label: '0,8×' }, { id: '1', label: '1×' }, { id: '1.2', label: '1,2×' }, { id: '1.5', label: '1,5×' }], s.speed)}<span class="nj-c-label">Temporizador</span><span class="nj-c-menuwrap"><button type="button" class="nj-chip" data-c="menu" data-arg="timer" aria-pressed="${s.timer !== 'off'}">${ic('bedtime', { size: 18 })}${{ off: 'Apagado', 15: '15 min', 30: '30 min', end: 'Fin del capítulo' }[s.timer]}</button>${s.menu === 'timer' ? menuHTML([['off', 'Apagado'], ['15', '15 min'], ['30', '30 min'], ['end', 'Al terminar el capítulo']].map(([id, l]) => ({ label: l, icon: String(s.timer) === id ? 'check' : 'blank', c: 'timerPick', arg: id }))) : ''}</span></div>`}
    </div>`;
  }

  NJ.registerScreen({
    id: 'lector', name: 'Lector', group: 'Leer', order: 2, platforms: ['desktop', 'android'], fullBleed: true,
    summary: 'Los lectores muestran solo los ajustes del modo elegido, con zonas táctiles visibles y elegibles, y el narrador permite cambiar voz, velocidad y temporizador sin salir.',
    parity: 'MNG-12…MNG-19, NOV-09…NOV-14, NOV-17 y NOV-18 (voz y motor desde el lector), SET-RDR-MANGA y SET-RDR-NOVEL en la hoja; atajos (MNG-20/21) y claves de la nube en Ajustes',
    structures: [
      { id: 'paginado', name: 'Manga página a página', desc: 'Página o doble página, barra inferior con riel de páginas y una hoja de ajustes con zonas táctiles visibles.' },
      { id: 'cascada', name: 'Webtoon en cascada', desc: 'Tira vertical continua con autodesplazamiento a mano y el siguiente capítulo debajo.' },
      { id: 'novela', name: 'Novela con narrador', desc: 'Columna de texto con tipografía en vivo y la barra del narrador con voz, velocidad y temporizador.' },
    ],
    render(ctx) {
      const s = init(ctx, { chrome: true, page: 12, dir: 'rtl', dbl: true, fit: 'height', maxw: 0, gap: 8, crop: false, bg: 'black', sysBright: true, bright: 60, zones: 'tercios', zonesShow: false, infinite: true, auto: 0, autoOn: false, eager: false, pre: 5, fullOpen: true, tapHide: true, showProg: true, zoomKeys: true, full: false, rdSrc: 'kumo', font: 'serif', size: 19, lh: 16, para: 12, width: 720, justify: false, nbg: 'theme', cont: true, nprog: true, awake: true, tts: 'speaking', para0: 1, engine: 'system', voice: 'Sabina', speed: '1', timer: 'off', hl: true, pitch: 10 });
      if (s.modeFor !== ctx.structure) { s.modeFor = ctx.structure; s.rmode = ctx.structure === 'cascada' ? 'webtoon' : 'paged'; s.ov = null; s.chrome = true; }
      const isA = A(ctx); const novel = ctx.structure === 'novela';
      let canvas = '';
      if (novel) {
        canvas = `<div class="nj-c-novel" data-nbg="${s.nbg}" style="${rdNovelVars(s)}" data-keep="novel" data-tap="chrome"><article class="nj-c-ntext" lang="es"><h1 class="nj-c-nchap">Capítulo 12<span>La carta</span></h1>${NOVEL_TEXT.map((p, i) => `<p data-para="${i}" class="${s.hl && s.tts !== 'idle' && s.para0 === i ? 'is-reading' : ''}">${esc(p)}</p>`).join('')}<div class="nj-c-nend"><p class="nj-meta">Fin del capítulo 12</p>${btn('Capítulo 13', 'nextCh', { kind: 'tonal', icon: 'arrow_forward' })}</div></article></div>
          ${s.nprog ? `<div class="nj-c-nprog" role="progressbar" aria-label="Progreso del capítulo" aria-valuenow="34" aria-valuemin="0" aria-valuemax="100"><i style="width:34%"></i></div>` : ''}`;
      } else if (s.rmode === 'paged') {
        const spread = s.dbl && !isA;
        const p2 = s.page + 1;
        const pages = spread ? (s.dir === 'rtl' ? [p2, s.page] : [s.page, p2]) : [s.page];
        canvas = `<div class="nj-c-pagearea" data-bg="${s.bg}" data-fit="${s.fit}" data-tap="zones" style="${s.sysBright ? '' : `--rd-dim:${(1 - s.bright / 100).toFixed(2)}`}"><div class="nj-c-spread${spread ? ' is-double' : ''}${s.crop ? ' is-crop' : ''}" data-flip="spread">${pages.map((p) => `<figure class="nj-c-page">${pageImg(p, 'Página ' + p + ' de ' + RD_TOTAL)}</figure>`).join('')}</div>${rdZoneOverlay(s)}${s.sysBright ? '' : '<i class="nj-c-dim" aria-hidden="true"></i>'}</div>`;
      } else {
        const web = s.rmode === 'webtoon';
        const strip = (from, n) => Array.from({ length: n }, (_, k) => from + k).map((p) => p === 4 ? `<figure class="nj-c-page is-failed" data-region="empty"><div class="nj-c-pagefail">${ic('broken_image', { size: 32 })}<p class="nj-h3">La página 4 no cargó</p><p class="nj-meta">El servidor de Kumo tardó demasiado.</p><div class="nj-cluster">${btn('Reintentar', 'reload', { kind: 'outline', icon: 'refresh' })}${btn('Cambiar fuente', 'open', { kind: 'tonal', attrs: { 'data-arg': 'source' } })}</div></div></figure>` : `<figure class="nj-c-page">${pageImg(p)}</figure>`).join('');
        canvas = `<div class="nj-c-strip${web ? ' is-webtoon' : ''}" data-bg="${s.bg}" data-keep="strip" data-tap="chrome" style="--rd-gap:${web ? 0 : s.gap}px;${s.maxw ? `--rd-maxw:${s.maxw}px` : ''}"><div class="nj-c-stripcol">${strip(1, 6)}${s.infinite ? `<div class="nj-c-chdivider">${ic('menu_book', { size: 18 })}Capítulo 181 · Sigue abajo</div>${strip(7, 3)}` : `<div class="nj-c-chdivider is-end"><p>Fin del capítulo 180</p>${btn('Capítulo 181', 'nextCh', { kind: 'filled' })}</div>`}</div></div>`;
      }
      const bottom = novel ? `<footer class="nj-c-rbot" data-chrome>${ibtn('skip_previous', 'Capítulo anterior', 'prevCh')}<span class="nj-meta nj-c-grow nj-c-center">34 % leído</span>${ibtn('skip_next', 'Capítulo siguiente', 'nextCh')}</footer>`
        : `<footer class="nj-c-rbot" data-chrome>${ibtn('skip_previous', 'Capítulo anterior (P)', 'prevCh')}
          ${s.rmode === 'paged' ? `<div class="nj-c-pageslider${s.dir === 'rtl' ? ' is-rtl' : ''}"><span class="nj-c-num">${s.page}</span><input type="range" min="1" max="${RD_TOTAL}" value="${s.page}" data-c="noop" data-pg aria-label="Página" aria-valuetext="Página ${s.page} de ${RD_TOTAL}"><span class="nj-c-num">${RD_TOTAL}</span></div>` : `<div class="nj-c-autoscroll">${ibtn(s.autoOn ? 'pause' : 'play_arrow', s.autoOn ? 'Detener autodesplazamiento' : 'Autodesplazamiento', 'autoToggle', { kind: s.autoOn ? 'filled' : 'tonal' })}${chips('autoSpd', 'Velocidad', [{ id: '60', label: 'Lenta' }, { id: '160', label: 'Media' }, { id: '320', label: 'Rápida' }], String(s.autoSpd || '160'))}</div>`}
          ${ibtn('view_carousel', 'Páginas', 'open', { attrs: { 'data-arg': 'pages' } })}${ibtn('skip_next', 'Capítulo siguiente (N)', 'nextCh')}</footer>`;
      let ov = '';
      if (s.ov === 'settings') ov = sheetHTML(ctx, { id: 'settings', title: novel ? 'Texto y diseño' : 'Ajustes del lector', sub: novel ? 'Los cambios se ven en vivo.' : 'Solo ves lo que aplica al modo elegido.', body: novel ? rdNovelSettings(ctx, s) : rdMangaSettings(ctx, s) });
      else if (s.ov === 'pages') ov = sheetHTML(ctx, { id: 'pages', title: 'Páginas', sub: `Página ${s.page} de ${RD_TOTAL}`, wide: !isA, body: `<div class="nj-c-pagegrid">${Array.from({ length: 18 }, (_, k) => k + 1).map((p) => `<button type="button" class="nj-c-pthumb" data-c="goPage" data-arg="${p}" aria-current="${p === s.page}">${pageImg(p, '')}<span class="nj-c-num">${p}</span></button>`).join('')}</div>` });
      else if (s.ov === 'chapters') {
        const chs = novel ? [14, 13, 12, 11, 10, 9, 8] : [182, 181, 180, 179, 178, 177, 176, 175];
        const cur = novel ? 12 : 180;
        ov = sheetHTML(ctx, { id: 'chapters', title: 'Capítulos', body: `${chips('chF', 'Mostrar', [{ id: 'all', label: 'Todos' }, { id: 'unread', label: 'Sin leer' }, { id: 'dl', label: 'Descargados' }], s.chF || 'all')}<ul class="nj-c-plainlist nj-c-chsheet">${chs.filter((n) => (s.chF === 'unread' ? n > cur - 1 : s.chF === 'dl' ? n === cur - 1 || n === cur - 2 : true)).map((n) => `<li><button type="button" class="nj-c-radiorow${n === cur ? ' is-current' : ''}" data-c="close" aria-current="${n === cur}"><span class="nj-c-grow">Cap. ${n}${n === cur ? ' · leyendo' : ''}</span><span class="nj-meta">${novel ? 'Shiori · ES' : 'Asura Scans · EN'}</span>${n < cur ? ic('check_circle', { size: 18 }) : ''}${n === cur - 1 || n === cur - 2 ? ic('download_done', { size: 18 }) : ''}</button></li>`).join('')}</ul>` });
      } else if (s.ov === 'source') ov = sheetHTML(ctx, { id: 'source', title: 'Cambiar fuente', sub: 'Abre este capítulo en otra extensión, en la misma página.', body: `<div class="nj-c-radiolist" role="radiogroup" aria-label="Fuente">${MG_SOURCES.map((x) => `<button type="button" role="radio" aria-checked="${s.rdSrc === x.id}" class="nj-c-radiorow" data-c="rdSrc" data-arg="${x.id}"${x.miss || s.rdSrc === x.id ? ' disabled' : ''}>${ic(s.rdSrc === x.id ? 'radio_button_checked' : 'radio_button_unchecked')}<span class="nj-c-grow"><b>${x.name}</b><span class="nj-meta"> · ${x.miss ? esc(x.miss) : `${x.count} caps. · ${x.langs} · fiable ${pct(x.rel)}`}</span></span>${x.id === 'toko' ? pill('Tiene el cap. 180', 'ok') : ''}</button>`).join('')}</div>` });
      else if (s.ov === 'voice') ov = rdVoiceSheet(ctx, s);
      const counter = s.showProg && !novel ? `<span class="nj-c-pagecount" aria-live="polite">${s.rmode === 'paged' ? `${s.page} / ${RD_TOTAL}` : 'Cap. 180'}</span>` : '';
      const st = '';
      return `<div class="nj-c-root nj-c-reader${s.chrome ? '' : ' is-chromeoff'}${novel ? ' is-novel' : ''}" data-rmode="${novel ? 'novel' : s.rmode}" data-local data-cards-local>${st}${canvas}${rdTop(ctx, s, novel)}${novel ? rdNarrator(ctx, s) : ''}${bottom}${counter}${ov}${snackHTML(s)}</div>`;
    },
    mount(el, ctx) {
      const s = ctx.state; const root = el.querySelector('.nj-c-root'); if (!root) return;
      const re = () => rr(ctx, el);
      const total = RD_TOTAL; const step = s.dbl && !A(ctx) ? 2 : 1;
      const chrome = (on) => { s.chrome = on == null ? !s.chrome : on; root.classList.toggle('is-chromeoff', !s.chrome); };
      const turn = (d) => { s.page = Math.max(1, Math.min(total - (step - 1), s.page + d * step)); if (s.tapHide && A(ctx)) s.chrome = false; s._focus = null; re(); };
      const H = {
        rdSet(t) { const [k, v] = t.getAttribute('data-arg').split(':'); s[k] = v; if (k === 'rmode' && v !== 'paged') s.zonesShow = false; re(); },
        $toggle(k) { if (['sysBright', 'eager', 'dbl', 'crop', 'infinite', 'justify', 'nprog', 'hl'].includes(k)) re(); },
        $range(k) { if (['maxw', 'gap', 'bright'].includes(k)) re(); },
        $live(k, v) {
          const map = { size: ['--rd-size', v + 'px'], lh: ['--rd-lh', (v / 10).toFixed(1)], para: ['--rd-para', v + 'px'], width: ['--rd-w', v + 'px'] };
          if (map[k]) el.querySelectorAll('.nj-c-novel, [data-preview]').forEach((n) => n.style.setProperty(map[k][0], map[k][1]));
          if (k === 'bright') el.querySelectorAll('.nj-c-pagearea').forEach((n) => n.style.setProperty('--rd-dim', (1 - v / 100).toFixed(2)));
          if (k === 'pitch') { const o = el.querySelector(`output[for="${pid(ctx, 'pitch')}"]`); if (o) o.textContent = (v / 10).toFixed(1) + '×'; }
          if (k === 'lh') { const o = el.querySelector(`output[for="${pid(ctx, 'lh')}"]`); if (o) o.textContent = (v / 10).toFixed(1) + '×'; }
        },
        zonesShow() { s.zonesShow = !s.zonesShow; if (s.zonesShow && !A(ctx)) s.ov = null; re(); },
        fullToggle() { s.full = !s.full; snack(ctx, s.full ? 'Pantalla completa. Esc o F para salir.' : 'Saliste de pantalla completa'); re(); },
        saveImg() { s.menu = null; snack(ctx, 'Guardada en Imágenes/Nijiyome', 'Abrir carpeta', 'snackClose'); re(); },
        reload() { s.menu = null; snack(ctx, 'Recargando las páginas'); re(); },
        rdSrc(t) { s.rdSrc = t.getAttribute('data-arg'); s.ov = null; snack(ctx, 'Capítulo 180 abierto en Toko, página 12'); re(); },
        goPage(t) { s.page = Number(t.getAttribute('data-arg')); s.ov = null; re(); },
        prevCh() { snack(ctx, 'Capítulo anterior'); re(); },
        nextCh() { snack(ctx, ctx.structure === 'novela' ? 'Este es el último capítulo disponible.' : 'Capítulo 181'); re(); },
        autoToggle() { s.autoOn = !s.autoOn; re(); },
        $chip(k, v) { if (k === 'autoSpd') { s.autoSpd = v; s.autoOn = true; re(); return false; } return true; },
        ttsPlay() { s.tts = 'speaking'; re(); },
        ttsPause() { s.tts = 'paused'; re(); },
        ttsStop() { s.tts = 'idle'; re(); },
        ttsNext() { s.para0 = Math.min(NOVEL_TEXT.length - 1, s.para0 + 1); re(); },
        timerPick(t) { s.timer = t.getAttribute('data-arg'); s.menu = null; if (s.timer !== 'off') snack(ctx, s.timer === 'end' ? 'Se detiene al terminar el capítulo' : `Se detiene en ${s.timer} min`); re(); },
        engine(t) { s.engine = t.getAttribute('data-arg'); const g = VOICES[s.engine]; if (g) s.voice = g[0][1][0]; re(); },
        voicePick(t) { s.voice = t.getAttribute('data-arg'); re(); },
        voiceTest(t) { const v = t.getAttribute('data-arg'); s.testing = s.testing === v ? null : v; re(); },
        fontInstalled() { snack(ctx, 'Se abre la lista de fuentes del sistema, cada una con su letra'); re(); },
        fontImport() { snack(ctx, 'Importada: Literata.ttf'); re(); },
        $key(e) {
          if (s.ov) return;
          const k = e.key.toLowerCase();
          if (ctx.structure !== 'novela' && s.rmode === 'paged' && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) { e.preventDefault(); turn((e.key === 'ArrowRight') === (s.dir === 'rtl') ? -1 : 1); }
          else if (k === 'f' && !A(ctx)) H.fullToggle();
          
        },
      };
      wire(el, ctx, H);
      root.tabIndex = -1;
      root.addEventListener('keydown', (e) => { if (e.target === root && e.key === ' ') { e.preventDefault(); chrome(); } });
      const pg = el.querySelector('[data-pg]');
      if (pg) pg.addEventListener('change', () => { s.page = Number(pg.value); re(); });
      /* toques sobre la página: zonas configurables o mostrar controles */
      el.addEventListener('click', (e) => {
        const area = e.target.closest('[data-tap]'); if (!area || e.target.closest('button, a, input')) return;
        if (area.getAttribute('data-tap') === 'chrome') {
          const p = e.target.closest('[data-para]');
          if (p && s.tts !== 'idle') { s.para0 = Number(p.getAttribute('data-para')); re(); return; }
          if (s.tapHide) chrome(); return;
        }
        const r = area.getBoundingClientRect(); const x = (e.clientX - r.left) / r.width; const y = (e.clientY - r.top) / r.height;
        let z = 'menu';
        if (s.zones === 'tercios') z = x < 1 / 3 ? 'prev' : x > 2 / 3 ? 'next' : 'menu';
        else if (s.zones === 'ele') z = y < 0.25 ? 'menu' : x < 0.35 ? 'prev' : 'next';
        else if (s.zones === 'bordes') z = x < 0.15 ? 'prev' : x > 0.85 ? 'next' : 'menu';
        if (z === 'menu') { if (s.tapHide || s.zones === 'off') chrome(); }
        else turn(z === 'next' ? (s.dir === 'rtl' ? -1 : 1) : (s.dir === 'rtl' ? 1 : -1));
      });
    },
  });

  /* =====================================================================
     6. BIENVENIDA: primer arranque en 4 pasos con elecciones visuales
     ===================================================================== */
  const ON_STEPS = [
    { id: 'look', label: 'Idioma y aspecto', icon: 'palette' },
    { id: 'list', label: 'Tu lista y cómo ves', icon: 'playlist_add_check' },
    { id: 'src', label: 'Fuentes', icon: 'extension' },
    { id: 'link', label: 'Link y sistema', icon: 'devices' },
  ];

  function onThemePreview(ctx, mode, pal) {
    return `<span class="nj-app nj-c-themeprev" data-mode="${mode}" data-dir="${esc(ctx.dir)}" data-palette="${esc(pal)}" data-motion="${esc(ctx.motion)}" aria-hidden="true"><i class="nj-c-tp-rail"></i><i class="nj-c-tp-card"></i><i class="nj-c-tp-card"></i><i class="nj-c-tp-line"></i><i class="nj-c-tp-btn"></i></span>`;
  }

  function onStep(ctx, s) {
    const isA = A(ctx); const dir = NJ.directions[ctx.dir] || {}; const pals = dir.palettes || [];
    const pal = s.pal || (NJ.state.palette && pals.some((p) => p.id === NJ.state.palette) ? NJ.state.palette : (pals[0] || {}).id || 'base');
    const cardRadio = (k, id, title, sub, icon, extra) => `<button type="button" role="radio" aria-checked="${s[k] === id}" class="nj-c-choice" data-c="onSet" data-arg="${k}:${id}">${icon ? ic(icon, { size: 26 }) : ''}<span class="nj-c-grow"><b>${title}</b>${sub ? `<span class="nj-meta">${sub}</span>` : ''}</span>${extra || ''}${s[k] === id ? ic('check_circle', { fill: true, cls: 'nj-c-choicecheck' }) : ''}</button>`;
    if (s.step === 0) {
      return `<h2 class="nj-c-display">Hola. Ajustemos Nijiyome a tu gusto.</h2><p class="nj-c-lede">Todo esto se cambia después en Ajustes.</p>
        <section class="nj-c-sub"><h3 class="nj-c-label">Idioma de la app</h3><div class="nj-c-choices is-two" role="radiogroup" aria-label="Idioma">${cardRadio('lang', 'es', 'Español', 'Latinoamérica', 'translate')}${cardRadio('lang', 'en', 'English', 'United States', 'translate')}</div></section>
        <section class="nj-c-sub"><h3 class="nj-c-label">Tema</h3><div class="nj-c-themes" role="radiogroup" aria-label="Tema">${[['light', 'Claro'], ['dark', 'Oscuro'], ['oled', 'OLED']].map(([id, l]) => `<button type="button" role="radio" aria-checked="${s.mode === id}" class="nj-c-themetile" data-c="onSet" data-arg="mode:${id}">${onThemePreview(ctx, id, pal)}<span>${l}${id === 'oled' ? '<span class="nj-meta"> · negro puro</span>' : ''}</span></button>`).join('')}</div></section>
        ${pals.length > 1 ? `<section class="nj-c-sub"><h3 class="nj-c-label">Paleta</h3><div class="nj-c-palettes" role="radiogroup" aria-label="Paleta">${pals.map((p) => `<button type="button" role="radio" aria-checked="${pal === p.id}" class="nj-c-paltile" data-c="onSet" data-arg="pal:${esc(p.id)}"><i style="background:${esc(p.swatch)}" aria-hidden="true"></i><span>${esc(p.name)}</span></button>`).join('')}</div></section>` : ''}`;
    }
    if (s.step === 1) {
      const acc = (id, name) => { const on = s.accounts.includes(id); return `<div class="nj-c-account${on ? ' is-on' : ''}"><span class="nj-c-avatar" aria-hidden="true">${name[0]}</span><div class="nj-c-grow"><b>${name}</b><p class="nj-meta">${on ? 'Conectado como octavio · 214 títulos traídos' : 'Trae tu lista y guarda el progreso ahí.'}</p></div>${on ? btn('Desconectar', 'onAcc', { kind: 'text', attrs: { 'data-arg': id } }) : btn('Conectar', 'onAcc', { kind: 'tonal', icon: 'login', attrs: { 'data-arg': id } })}</div>`; };
      return `<h2 class="nj-c-display">Tu lista y cómo ves</h2><p class="nj-c-lede">Conecta tu cuenta para traer lo que ya ves. Puedes usar la app sin cuenta.</p>
        <section class="nj-c-sub">${acc('anilist', 'AniList')}${acc('mal', 'MyAnimeList')}${s.accounts.length ? `<div class="nj-c-field"><span class="nj-c-label">Sincronización</span>${seg('sync', 'Sincronización', [{ id: 'both', label: 'En ambos sentidos' }, { id: 'pull', label: 'Solo traer' }, { id: 'push', label: 'Solo enviar' }], s.sync)}</div>` : ''}</section>
        <section class="nj-c-sub"><h3 class="nj-c-label">Cómo prefieres ver</h3>
          <div class="nj-c-field"><span class="nj-c-label">Versión</span>${seg('type', 'Versión preferida', [{ id: 'sub', label: 'Subtitulada' }, { id: 'dub', label: 'Doblada' }], s.type)}</div>
          <div class="nj-c-field"><span class="nj-c-label">Subtítulos</span>${chips('subs', 'Idioma de subtítulos', [{ id: 'es', label: 'Español' }, { id: 'en', label: 'Inglés' }, { id: 'none', label: 'Sin subtítulos' }], s.subs)}</div>
          <div class="nj-c-field"><span class="nj-c-label">Audio</span>${chips('audio', 'Idioma de audio', [{ id: 'ja', label: 'Japonés' }, { id: 'es', label: 'Español latino' }, { id: 'en', label: 'Inglés' }], s.audio)}</div>
          <div class="nj-c-field"><span class="nj-c-label">Calidad mínima</span>${chips('minq', 'Calidad mínima', [{ id: '480', label: '480p' }, { id: '720', label: '720p' }, { id: '1080', label: '1080p' }], s.minq)}</div>
          <div class="nj-c-field"><span class="nj-c-label">Elegir la fuente de cada episodio</span>${seg('pick', 'Selección de fuente', [{ id: 'auto', label: 'Automático' }, { id: 'ask', label: 'Preguntarme' }], s.pick)}</div>
          <div class="nj-c-field"><span class="nj-c-label">Reproductor</span>${seg('engine', 'Motor', isA ? [{ id: 'mpv', label: 'MPV (con mejoras)' }, { id: 'media3', label: 'Media3 (ligero)' }] : [{ id: 'mpv', label: 'Integrado (libmpv)' }, { id: 'ext', label: 'mpv externo' }], s.engine)}</div></section>`;
    }
    if (s.step === 2) {
      const repo = s.repoOn;
      return `<h2 class="nj-c-display">Elige tus primeras fuentes</h2><p class="nj-c-lede">Nijiyome no trae contenido. Las extensiones .kai buscan los episodios y capítulos donde tú elijas.</p>
        <div class="nj-c-choices">${`<div class="nj-c-drop${s.kaiHover ? ' is-hover' : ''}">${ic('upload_file', { size: 28 })}<div class="nj-c-grow"><b>${isA ? 'Instala un .kai' : 'Suelta un .kai aquí'}</b><p class="nj-meta">Desde tus archivos o un enlace.</p></div>${btn('Elegir archivo', 'onKai', { kind: 'tonal' })}</div>`}
          <div class="nj-c-account${repo ? ' is-on' : ''}">${ic('inventory_2', { size: 26 })}<div class="nj-c-grow"><b>Repositorio de Tatakai</b><p class="nj-meta">${repo ? '14 extensiones disponibles. Marca las que quieras.' : 'Lista oficial con actualizaciones automáticas.'}</p></div>${repo ? '' : btn('Añadir', 'onRepo', { kind: 'tonal', icon: 'add' })}</div>
          ${repo ? `<div class="nj-c-chipw">${FU_STORE.filter((x) => x.repo === 'Tatakai oficial').map((x) => `<button type="button" class="nj-chip" data-c="onPick" data-arg="${x.id}" aria-pressed="${s.picks.includes(x.id)}">${s.picks.includes(x.id) ? ic('check', { size: 18 }) : ''}${esc(x.name)} <span class="nj-muted">${esc(x.kinds.join(', '))}</span></button>`).join('')}</div>` : ''}
          <div class="nj-c-account">${ic('move_down', { size: 26 })}<div class="nj-c-grow"><b>Importar de otra instalación</b><p class="nj-meta">${s.imported ? 'Se importaron 3 extensiones, 2 repositorios y sus preferencias.' : isA ? 'Desde un respaldo o desde tu PC con Link.' : 'Desde un archivo de respaldo de Nijiyome.'}</p></div>${s.imported ? pill('Importado', 'ok', 'check') : btn(isA ? 'Desde mi PC' : 'Elegir respaldo', 'onImport', { kind: 'outline' })}</div></div>
        ${isA ? '' : `<section class="nj-c-sub"><h3 class="nj-c-label">Descargas</h3><div class="nj-c-pathrow"><span class="nj-c-path">${ic('folder', { size: 18 })}${esc(s.dlDir)}</span>${btn('Cambiar carpeta', 'onDir', { kind: 'outline', icon: 'folder_open' })}${s.dlDir !== '~/Descargas/Nijiyome (predeterminada)' ? btn('Usar la predeterminada', 'onDirDef', { kind: 'text' }) : ''}</div>
          <div class="nj-c-field"><span class="nj-c-label">Después de ver un episodio, su archivo</span>${seg('after', 'Después de ver', [{ id: 'keep', label: 'Se conserva' }, { id: 'del', label: 'Se borra' }], s.after)}</div></section>`}`;
    }
    return `<h2 class="nj-c-display">Link y sistema</h2><p class="nj-c-lede">${isA ? 'Si tienes Nijiyome en la PC, puede mejorar el video y enviártelo.' : 'Tu PC puede mejorar el video para tu teléfono y prestarle sus extensiones.'} Es opcional.</p>
      <section class="nj-c-sub">${isA
        ? `<div class="nj-c-account${s.linked ? ' is-on' : ''}">${ic('devices', { size: 26 })}<div class="nj-c-grow"><b>${s.linked ? 'Enlazado con octavio-pc' : 'Enlazar con tu PC'}</b><p class="nj-meta">${s.linked ? 'El historial ya se sincroniza.' : 'En la PC abre Link › Emparejar y escanea el código.'}</p></div>${s.linked ? pill('Listo', 'ok', 'check') : btn('Escanear QR', 'onLink', { kind: 'tonal', icon: 'qr_code_scanner' })}</div>
           <div class="nj-c-account${s.notif ? ' is-on' : ''}">${ic('notifications', { size: 26 })}<div class="nj-c-grow"><b>Notificaciones</b><p class="nj-meta">${s.notif ? 'Permitidas: verás el progreso de las descargas y los estrenos.' : 'Para ver el progreso de las descargas, pausarlas y saber cuándo sale un episodio.'}</p></div>${s.notif ? pill('Permitidas', 'ok', 'check') : btn('Permitir', 'onNotif', { kind: 'tonal' })}</div>`
        : `${tog(ctx, 'linkOn', 'Usar esta PC con tu teléfono', s.linkOn, 'Solo en tu red privada, por HTTPS, con teléfonos que emparejes.')}${s.linkOn ? `<div class="nj-c-pairinline">${qrSVG(5)}<div><p>Escanéalo desde Nijiyome en el teléfono: Tú › Link.</p><div class="nj-c-code is-small">K7QM 2XRP 4D</div><p class="nj-meta">QR de ejemplo. También puedes emparejar más tarde desde Link.</p></div></div>` : ''}`}</section>
      ${isA ? '' : `<section class="nj-c-sub"><h3 class="nj-c-label">Sistema</h3>${tog(ctx, 'autostart', 'Abrir al iniciar sesión', s.autostart)}${tog(ctx, 'tray', 'Al cerrar, quedarse en la bandeja', s.tray, 'Las descargas siguen y Link sigue disponible.')}${tog(ctx, 'nativeBar', 'Usar la barra de título del sistema', s.nativeBar, 'Apagado: barra propia con el chip de Link.')}${tog(ctx, 'updates', 'Buscar nuevas versiones al abrir', s.updates)}</section>`}`;
  }

  NJ.registerScreen({
    id: 'bienvenida', name: 'Primer arranque', group: 'Configurar', order: 2, platforms: ['desktop', 'android'], fullBleed: true,
    summary: 'El asistente de 7 a 9 pasos de texto y controles copiados de Ajustes pasa a 4 pasos con elecciones visuales: tema con vista previa, paleta, cuentas, fuentes con importación y Link real.',
    parity: 'SET-ONB (bienvenida, apariencia, reproducción, descargas, listas, Link, permisos, sistema y fin); SET-ACC sync en el paso 2',
    structures: [{ id: 'pasos', name: 'Cuatro pasos', desc: 'Idioma y aspecto, tu lista y cómo ves, fuentes, Link y sistema; con Omitir siempre visible.' }],
    render(ctx) {
      const s = init(ctx, { step: 0, lang: 'es', mode: ctx.mode || 'dark', pal: null, accounts: [], sync: 'both', type: 'sub', subs: 'es', audio: 'ja', minq: '720', pick: 'auto', engine: 'mpv', repoOn: false, picks: [], imported: false, dlDir: '~/Descargas/Nijiyome (predeterminada)', after: 'del', linkOn: true, linked: false, notif: false, autostart: false, tray: true, nativeBar: false, updates: true, done: false });
      const isA = A(ctx); const n = ON_STEPS.length;
      let content;
      if (s.done) {
        const count = s.picks.length + (s.imported ? 3 : 0);
        content = `<div class="nj-c-step${s._dir ? ' is-in-' + s._dir : ''}"><h2 class="nj-c-display">Todo listo</h2><p class="nj-c-lede">${count ? `Tienes ${count} extensiones listas para buscar episodios.` : 'Te falta una cosa: sin una extensión no hay episodios que ver.'}</p>
          <ul class="nj-c-summary">${[['palette', `${s.lang === 'es' ? 'Español' : 'English'} · tema ${{ light: 'claro', dark: 'oscuro', oled: 'OLED' }[s.mode]}`], ['playlist_add_check', s.accounts.length ? 'AniList conectado · ' + { both: 'sincroniza en ambos sentidos', pull: 'solo trae', push: 'solo envía' }[s.sync] : 'Sin cuenta: el progreso queda en este equipo'], ['subtitles', `${s.type === 'sub' ? 'Subtitulada' : 'Doblada'} · mínimo ${s.minq}p · fuente ${s.pick === 'auto' ? 'automática' : 'a elección'}`], ['extension', count ? `${count} extensiones` : 'Ninguna extensión todavía'], ['devices', isA ? (s.linked ? 'Enlazado con octavio-pc' : 'Link: más tarde') : (s.linkOn ? 'Link encendido en esta PC' : 'Link apagado')]].map(([i, t]) => `<li>${ic(i)}<span>${esc(t)}</span></li>`).join('')}</ul>
          <div class="nj-cluster">${count ? btn('Ir al inicio', 'onFinish', { kind: 'filled', icon: 'home' }) : `${btn('Instalar una extensión', 'go', { kind: 'filled', icon: 'extension', attrs: { 'data-arg': 'fuentes:tienda' } })}${btn('Ir al inicio igual', 'onFinish', { kind: 'text' })}`}</div></div>`;
      } else content = `<div class="nj-c-step${s._dir ? ' is-in-' + s._dir : ''}" aria-live="polite">${onStep(ctx, s)}</div>`;
      const steps = `<ol class="nj-c-steps" aria-label="Pasos">${ON_STEPS.map((st, i) => `<li aria-current="${!s.done && i === s.step ? 'step' : 'false'}" class="${i < s.step || s.done ? 'is-done' : ''}"><button type="button" data-c="onJump" data-arg="${i}"${i > s.step && !s.done ? ' disabled' : ''}><span class="nj-c-stepdot">${ic(i < s.step || s.done ? 'check' : st.icon, { size: 18 })}</span><span>${st.label}</span></button></li>`).join('')}</ol>`;
      const foot = s.done ? '' : `<footer class="nj-c-onfoot">${s.step > 0 ? btn('Atrás', 'onBack', { kind: 'text', icon: 'arrow_back' }) : '<span></span>'}<span class="nj-meta">Paso ${s.step + 1} de ${n}</span>${btn(s.step === n - 1 ? 'Terminar' : 'Siguiente', 'onNext', { kind: 'filled', icon: s.step === n - 1 ? 'check' : 'arrow_forward' })}</footer>`;
      const skip = s.done ? '' : btn('Omitir', 'onSkip', { kind: 'text' });
      const brand = `<div class="nj-c-onbrand"><span class="nj-c-onmark" aria-hidden="true">虹</span><span>Nijiyome</span></div>`;
      const body = isA
        ? `<div class="nj-c-onA"><header class="nj-c-onhead">${brand}${skip}</header><div class="nj-c-onprog" role="progressbar" aria-label="Progreso" aria-valuemin="1" aria-valuemax="${n}" aria-valuenow="${s.step + 1}">${ON_STEPS.map((x, i) => `<i class="${i <= s.step || s.done ? 'is-on' : ''}"></i>`).join('')}</div><div class="nj-c-body nj-c-pad" data-keep="on">${content}</div>${foot}</div>`
        : `<div class="nj-c-ondesk"><aside class="nj-c-onside">${brand}${steps}<p class="nj-meta">Todo se puede cambiar después en Ajustes.</p></aside><div class="nj-c-onmain"><div class="nj-c-onskip">${skip}</div><div class="nj-c-body" data-keep="on">${content}</div>${foot}</div></div>`;
      return `<div class="nj-c-root nj-c-on" data-local data-cards-local>${body}${snackHTML(s)}</div>`;
    },
    mount(el, ctx) {
      const s = ctx.state; const re = () => rr(ctx, el);
      const go = (i, d) => { s._dir = canMove(ctx) ? d : null; s.step = i; s._scroll = {}; ctx.rerender(); };
      const H = {
        onSet(t) { const [k, v] = t.getAttribute('data-arg').split(':'); s[k] = v; re(); },
        onAcc(t) { const id = t.getAttribute('data-arg'); const i = s.accounts.indexOf(id); if (i >= 0) s.accounts.splice(i, 1); else { s.accounts.push(id); snack(ctx, (id === 'anilist' ? 'AniList' : 'MyAnimeList') + ' conectado'); } re(); },
        $toggle(k) { if (k === 'linkOn') re(); },
        onKai() { s.picks.push('shiori'); snack(ctx, 'Se instaló Shiori 1.0.4'); re(); },
        onRepo() { s.repoOn = true; s.picks = ['toko']; re(); },
        onPick(t) { const id = t.getAttribute('data-arg'); const i = s.picks.indexOf(id); if (i >= 0) s.picks.splice(i, 1); else s.picks.push(id); re(); },
        onImport() { s.imported = true; re(); },
        onDir() { s.dlDir = '/media/Almacen/Anime'; re(); },
        onDirDef() { s.dlDir = '~/Descargas/Nijiyome (predeterminada)'; re(); },
        onLink() { s.linked = true; snack(ctx, 'Enlazado con octavio-pc'); re(); },
        onNotif() { s.notif = true; re(); },
        onNext() { if (s.step < ON_STEPS.length - 1) go(s.step + 1, 'fwd'); else { s.done = true; s._dir = canMove(ctx) ? 'fwd' : null; ctx.rerender(); } },
        onBack() { if (s.step > 0) go(s.step - 1, 'back'); },
        onJump(t) { const i = Number(t.getAttribute('data-arg')); s.done = false; go(i, i < s.step ? 'back' : 'fwd'); },
        onSkip() { s.done = true; s._dir = null; ctx.rerender(); },
        onFinish() { if (NJ.screens.inicio) NJ.go('inicio'); else { snack(ctx, 'Abriendo el inicio'); re(); } },
      };
      wire(el, ctx, H);
      s._dir = null;
    },
  });
})();
