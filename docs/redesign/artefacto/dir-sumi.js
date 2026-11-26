/* Dirección D4 · Sumi y hanko (carril que mantiene la identidad). Ver CONTRATO.md.
   Tinta sumi, papel frío, el ensō como trazo de progreso y los sellos hanko como estados de lista. */
(function () {
  const NJ = window.NJ;
  if (!NJ || !NJ.registerDirection) return;
  const h = NJ.h;
  const esc = h.esc;

  /* Kanji de cada estado. 見 ver, 予 previsto (予定), 完 completo (完了), 休 descanso (休止),
     止 detenido (中止), 再 de nuevo (再視聴). 済 (済み, hecho) marca los episodios vistos. */
  const KANJI = { watching: '見', planning: '予', completed: '完', paused: '休', dropped: '止', rewatching: '再' };
  const statusName = (id) => (h.status(id) || {}).name || id;

  /* ---------- trazo de pincel: un aro de ancho variable (presión al inicio, cola seca al final) ---------- */
  const pathCache = {};
  function ensoPath(cx, cy, r, w, a0, sweep, n) {
    const key = [cx, cy, r, w, a0, sweep].join(',');
    if (pathCache[key]) return pathCache[key];
    n = n || 56;
    const out = [], inn = [];
    for (let i = 0; i <= n; i++) {
      const t = i / n, a = ((a0 + sweep * t) * Math.PI) / 180;
      const press = t < 0.1 ? 0.55 + 4.5 * t : 1 - 0.78 * Math.pow((t - 0.1) / 0.9, 1.7);
      const th = w * press, rr = r + Math.sin(t * 9.5) * w * 0.05;
      const ox = Math.cos(a), oy = Math.sin(a);
      out.push([cx + ox * (rr + th / 2), cy + oy * (rr + th / 2)]);
      inn.push([cx + ox * (rr - th / 2), cy + oy * (rr - th / 2)]);
    }
    const f = (p) => p[0].toFixed(2) + ' ' + p[1].toFixed(2);
    return (pathCache[key] = 'M' + out.map(f).join('L') + 'L' + inn.reverse().map(f).join('L') + 'Z');
  }

  /* ---------- marca: kitsune entintado dentro del ensō, con el sello 虹 ---------- */
  function kitsune() {
    return `<path d="M18.5 27 L16 7.5 L29 18.5 Z M45.5 27 L48 7.5 L35 18.5 Z" fill="var(--k-ink)"/>
      <path d="M19.6 23.5 L18.4 12 L26 18.8 Z M44.4 23.5 L45.6 12 L38 18.8 Z" fill="var(--k-red)"/>
      <path d="M17 44 C15.5 30 17 17 32 16 C47 17 48.5 30 47 44 L41.5 44 L41.5 30 L22.5 30 L22.5 44 Z" fill="var(--k-ink)"/>
      <path d="M23 29.5 L41 29.5 L41 37 C41 41.5 37 44 32 44 C27 44 23 41.5 23 37 Z" fill="var(--k-skin)"/>
      <circle cx="28.3" cy="34.4" r="1.5" fill="var(--k-ink)"/><circle cx="35.7" cy="34.4" r="1.5" fill="var(--k-ink)"/>
      <path d="M19 60 C20 52 24.5 48 29 47.5 L35 47.5 C39.5 48 44 52 45 60 Z" fill="var(--k-robe)"/>
      <path d="M22 51.5 A10 10 0 0 1 42 51.5 Z" fill="var(--k-red)"/>
      <path d="M32 51.5 L24.5 45 M32 51.5 L28.2 42.3 M32 51.5 L35.8 42.3 M32 51.5 L39.5 45" stroke="var(--k-rib)" stroke-width=".7" stroke-linecap="round"/>
      <path d="M29.6 44.4 L35.4 47.7 L29.6 51 Z" fill="var(--k-skin)"/>`;
  }
  function hankoNiji(x, y, s) {
    return `<rect x="${x}" y="${y}" width="${s}" height="${s}" rx="${(s * 0.14).toFixed(1)}" fill="var(--k-red)"/><rect x="${x + 1.2}" y="${y + 1.2}" width="${s - 2.4}" height="${s - 2.4}" rx="${(s * 0.1).toFixed(1)}" fill="none" stroke="var(--k-seal-ink)" stroke-width=".6" opacity=".7"/><text x="${x + s / 2}" y="${y + s * 0.77}" font-size="${(s * 0.74).toFixed(1)}" text-anchor="middle" fill="var(--k-seal-ink)" font-family="'Yuji Boku','Zen Kaku Gothic New',serif">虹</text>`;
  }
  function mark(o) {
    o = o || {};
    const enso = `<path d="${ensoPath(32, 32, 28.2, 3.8, -55, 330)}" fill="var(--k-stroke)"/>`;
    const tile = o.tile ? `<rect width="64" height="64" rx="14" fill="var(--k-paper)"/>` : '';
    return `<svg class="${o.cls || 'sm-logo'}" viewBox="0 0 64 64" aria-hidden="true" focusable="false">${tile}${enso}${kitsune()}${hankoNiji(44.5, 45, 13)}</svg>`;
  }
  function linkGlyph() {
    return `<svg class="sm-link-glyph" viewBox="0 0 48 28" aria-hidden="true" focusable="false"><path class="sm-lg-a" d="${ensoPath(14, 14, 10.5, 2.6, -60, 320, 40)}"/><path class="sm-lg-b" d="${ensoPath(34, 14, 10.5, 2.6, 120, 320, 40)}"/></svg>`;
  }
  function nowGlyph() {
    return `<svg class="sm-now-enso" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="${ensoPath(12, 12, 9, 2.4, -50, 325, 36)}"/></svg>`;
  }

  /* ---------- piezas de tarjeta ---------- */
  function seal(id, o) {
    o = o || {};
    if (!id || !KANJI[id]) return '';
    const label = 'Estado: ' + statusName(id);
    const a11y = o.hidden ? 'aria-hidden="true"' : `role="img" aria-label="${esc(label)}" title="${esc(statusName(id))}"`;
    return `<span class="sm-seal${o.round ? ' is-round' : ''}${o.brush ? ' is-brush' : ''}${o.cls ? ' ' + o.cls : ''}" data-status="${esc(id)}" ${a11y}>${KANJI[id]}</span>`;
  }
  function sealTag(id) {
    if (!id || !KANJI[id]) return '';
    return `<span class="sm-tag" data-status="${esc(id)}">${seal(id, { hidden: true, round: true })}<span>${esc(statusName(id))}</span></span>`;
  }
  function line(v, label) {
    const pct = Math.round((v || 0) * 100);
    return `<span class="sm-line" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${pct}" aria-label="${esc(label)}" style="--v:${(v || 0).toFixed(3)}"><i></i></span>`;
  }
  const short = (it) => it.short || it.title;
  const nextEp = (it) => (it.resume ? it.resume.ep : Math.min((it.progress || 0) + 1, it.aired || it.eps || 1));
  const decimal = (n) => String(n).replace('.', ',');
  function contSub(it) {
    if (it.resume) return `Ep. ${it.resume.ep} · quedan ${it.resume.left}`;
    return `Ep. ${nextEp(it)}${it.eps ? ' de ' + it.eps : ''}`;
  }
  function sub(it, o) {
    const s = o.show || {};
    if (o.context === 'continue') return contSub(it);
    if ((s.next || o.context === 'wish') && it.next) return `Ep. ${it.next.ep} en ${it.next.in}`;
    return [it.format, it.year].filter(Boolean).join(' · ');
  }
  function pctOf(it) {
    if (it.resume) return { v: it.resume.pct, label: `Episodio ${it.resume.ep}: ${Math.round(it.resume.pct * 100)} % visto` };
    if (it.eps && it.progress) return { v: it.progress / it.eps, label: `Serie: ${it.progress} de ${it.eps} episodios` };
    return null;
  }
  function playLabel(it) {
    if (it.status === 'completed') return 'Volver a ver';
    if (!it.progress && !it.resume) return 'Empezar Ep. 1';
    return (it.resume ? 'Seguir Ep. ' : 'Ver Ep. ') + nextEp(it);
  }
  function metaLine(it) {
    return [it.format, it.year, it.eps ? it.eps + ' ep.' : null].filter(Boolean).map(esc).join(' · ') + (it.score ? ` · <span class="sm-score">${h.icon('star', { fill: true, size: 15 })}${decimal(it.score)}</span>` : '');
  }
  /* aro del ensō: aguada completa de fondo + arco real de progreso + arco seco */
  function ring(p, o) {
    o = o || {};
    const v = Math.max(0.02, Math.min(1, p.v)) * 100;
    return `<span class="sm-ring${o.cls ? ' ' + o.cls : ''}" style="--p:${v.toFixed(1)}" aria-hidden="true"><svg viewBox="0 0 60 60" focusable="false"><path class="sm-ring-wash" d="${ensoPath(30, 30, 25.5, 5.2, -55, 330)}"/><circle class="sm-enso-arc" cx="30" cy="30" r="25.5" pathLength="100" transform="rotate(-90 30 30)"/><circle class="sm-enso-dry" cx="30" cy="30" r="27.4" pathLength="100" transform="rotate(-87 30 30)"/></svg></span>`;
  }
  const root = (style, it, o) => `data-style="${style}" data-context="${o.context}" data-size="${o.size}" data-status="${esc(it.status || '')}" data-id="${esc(it.id)}"`;
  const playBtn = (it, cls) => `<button type="button" class="nj-iconbtn is-filled sm-play${cls ? ' ' + cls : ''}" data-stop data-sumi-play aria-label="${esc(playLabel(it) + ', ' + short(it))}" title="${esc(playLabel(it))}">${h.icon('play_arrow', { fill: true })}</button>`;
  const sealSize = (o) => (o.size === 's' ? 'is-s' : o.size === 'l' ? 'is-l' : '');

  function hero(style, it, o) {
    const p = pctOf(it);
    const art = style === 'sumi-papel' ? `<span class="sm-mat"><span class="nj-card-art">${h.poster(it)}</span></span>` : `<span class="nj-card-art">${h.poster(it)}${style === 'hanko' ? seal(it.status, { cls: 'sm-corner is-l', brush: true }) : ''}</span>`;
    const play = style === 'enso' && p
      ? `<span class="sm-ringwrap is-hero">${ring(p, { cls: 'is-first' })}${playBtn(it)}</span><span class="sm-hero-cta"><b>${esc(playLabel(it))}</b><span class="nj-meta">${esc(contSub(it))}</span></span>`
      : `<button type="button" class="nj-btn is-filled" data-stop data-sumi-play>${h.icon('play_arrow', { fill: true })}${esc(playLabel(it))}</button>`;
    return `<div class="nj-card sm-card" ${root(style, it, o)}>${art}<div class="sm-hero-body"><div class="sm-hero-title">${esc(it.title)}</div><div class="nj-meta">${metaLine(it)}</div>${it.status ? `<div class="sm-hero-state">${sealTag(it.status)}${p && style !== 'enso' ? `<span class="nj-meta">${esc(p.label)}</span>` : ''}</div>` : ''}${p && style === 'hanko' ? line(p.v, p.label) : ''}<div class="sm-hero-actions">${play}<button type="button" class="nj-btn is-outline sm-open-btn">${h.icon('info')}Ver ficha</button></div></div></div>`;
  }

  /* ---------- tarjeta 1: hanko · póster limpio con sello de estado ---------- */
  function renderHanko(it, o) {
    const s = o.show || {};
    const p = pctOf(it);
    if (o.context === 'hero') return hero('hanko', it, o);
    if (o.context === 'continue') {
      return `<div class="nj-card sm-card" ${root('hanko', it, o)}><button type="button" class="sm-open" aria-label="${esc('Abrir ficha de ' + short(it))}"><span class="nj-card-art">${h.poster(it, { alt: '' })}${seal(it.status, { cls: 'sm-corner is-s', hidden: true })}</span><span class="sm-txt"><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(contSub(it))}</span>${p ? line(p.v, p.label) : ''}</span></button>${playBtn(it)}</div>`;
    }
    if (o.context === 'compact') {
      return `<button type="button" class="nj-card sm-card" ${root('hanko', it, o)}><span class="nj-card-art">${h.poster(it, { alt: '' })}</span><span class="sm-txt"><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(sub(it, o))}</span></span>${s.status !== false ? sealTag(it.status) : ''}</button>`;
    }
    const showSeal = s.status !== false && it.status;
    return `<button type="button" class="nj-card sm-card" ${root('hanko', it, o)}><span class="nj-card-art">${h.poster(it)}${showSeal ? seal(it.status, { cls: 'sm-corner ' + sealSize(o), brush: o.size === 'l' }) : ''}</span>${s.progress && p ? line(p.v, p.label) : ''}<span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(sub(it, o))}</span></button>`;
  }

  /* ---------- tarjeta 2: ensō · el progreso se traza alrededor de reproducir ---------- */
  function renderEnso(it, o) {
    const s = o.show || {};
    const p = pctOf(it);
    const live = p && ['watching', 'rewatching', 'paused'].includes(it.status);
    if (o.context === 'hero') return hero('enso', it, o);
    if (o.context === 'continue') {
      return `<div class="nj-card sm-card" ${root('enso', it, o)}><button type="button" class="sm-open" aria-label="${esc('Abrir ficha de ' + short(it))}"><span class="nj-card-art">${h.poster(it, { alt: '' })}</span><span class="sm-txt"><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(contSub(it))}</span></span></button><span class="sm-ringwrap">${p ? ring(p) : ''}${playBtn(it)}</span></div>`;
    }
    if (o.context === 'compact') {
      return `<button type="button" class="nj-card sm-card" ${root('enso', it, o)}><span class="nj-card-art">${h.poster(it, { alt: '' })}</span><span class="sm-txt"><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(live ? p.label : sub(it, o))}</span></span>${live ? `<span class="sm-ringwrap is-mini">${ring(p)}${h.icon('play_arrow', { fill: true, cls: 'sm-ring-ic' })}</span>` : s.status !== false ? sealTag(it.status) : ''}</button>`;
    }
    const badge = live && (s.progress || o.context !== 'grid') ? `<span class="sm-ringwrap is-badge">${ring(p)}${h.icon('play_arrow', { fill: true, cls: 'sm-ring-ic' })}</span>` : '';
    const st = s.status && it.status ? seal(it.status, { round: true, cls: 'sm-corner is-s' }) : '';
    return `<button type="button" class="nj-card sm-card" ${root('enso', it, o)}${live ? ` aria-description="${esc(p.label)}"` : ''}><span class="nj-card-art">${h.poster(it)}${st}${badge}</span><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(live && o.context !== 'wish' ? contSub(it) : sub(it, o))}</span></button>`;
  }

  /* ---------- tarjeta 3: sumi-papel · marco de papel y título en tinta debajo ---------- */
  function renderPapel(it, o) {
    const s = o.show || {};
    const p = pctOf(it);
    if (o.context === 'hero') return hero('sumi-papel', it, o);
    const mat = (alt) => `<span class="sm-mat"><span class="nj-card-art">${h.poster(it, alt === '' ? { alt: '' } : {})}</span></span>`;
    if (o.context === 'continue') {
      return `<div class="nj-card sm-card" ${root('sumi-papel', it, o)}><button type="button" class="sm-open" aria-label="${esc('Abrir ficha de ' + short(it))}">${mat('')}<span class="sm-txt"><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(contSub(it))}</span>${p ? line(p.v, p.label) : ''}</span></button>${playBtn(it, 'is-ink')}</div>`;
    }
    if (o.context === 'compact') {
      return `<button type="button" class="nj-card sm-card" ${root('sumi-papel', it, o)}>${mat('')}<span class="sm-txt"><span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(sub(it, o))}</span></span>${s.status !== false ? sealTag(it.status) : ''}</button>`;
    }
    const foot = [s.status !== false && it.status ? sealTag(it.status) : '', it.score && o.size !== 's' ? `<span class="sm-score">${h.icon('star', { fill: true, size: 14 })}${decimal(it.score)}</span>` : ''].filter(Boolean).join('');
    return `<button type="button" class="nj-card sm-card" ${root('sumi-papel', it, o)}>${mat()}<span class="nj-card-title">${esc(short(it))}</span><span class="nj-card-sub">${esc(sub(it, o))}</span>${s.progress && p ? line(p.v, p.label) : ''}${foot ? `<span class="sm-foot">${foot}</span>` : ''}</button>`;
  }

  NJ.registerCard({ id: 'hanko', name: 'Hanko', dir: 'sumi', desc: 'Póster limpio con el sello del estado estampado en la esquina: 見 viendo, 予 pendiente, 完 completado, 休 en pausa, 止 abandonado, 再 reviendo.', render: renderHanko });
  NJ.registerCard({ id: 'enso', name: 'Ensō', dir: 'sumi', desc: 'Para continuar: el ensō de la marca rodea el botón de reproducir y su trazo es el avance real del episodio o de la serie.', render: renderEnso });
  NJ.registerCard({ id: 'sumi-papel', name: 'Sumi papel', dir: 'sumi', desc: 'Póster dentro de un marco de papel, título en tinta debajo y sello redondo con el nombre del estado. Más editorial y legible a distancia.', render: renderPapel });

  /* ---------- decorate: marca, sellos, ensō y anclajes ---------- */
  const lastScreen = {}; // plataforma -> pantalla trazada por última vez
  const pickMem = {};
  const watchMem = {};
  const reducedMQ = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : { matches: false };

  function stamp(el) {
    if (!el) return;
    el.classList.remove('is-stamping');
    void el.offsetWidth;
    el.classList.add('is-stamping');
    el.addEventListener('animationend', () => el.classList.remove('is-stamping'), { once: true });
  }
  function ensurePositioned(el) { if (getComputedStyle(el).position === 'static') el.style.position = 'relative'; }

  function decorateBrand(app) {
    const brand = app.querySelector('.nj-topbar .nj-brand');
    if (brand && !brand.querySelector('.sm-mark')) {
      const txt = Array.from(brand.children).find((c) => !c.classList.contains('nj-ic'));
      const m = document.createElement('span');
      m.className = 'sm-mark';
      m.innerHTML = mark({ tile: true });
      brand.insertBefore(m, txt || null);
    }
    if (app.dataset.platform === 'android') {
      /* la marca vive en la appbar superior de cada pantalla, junto al título (no en barras de selección) */
      const bar = app.querySelector('.nj-main .nj-appbar:not(.is-context)');
      const title = bar && bar.querySelector('h1, .nj-h2');
      if (bar && title && title.textContent.trim() && !bar.querySelector('.sm-mark')) title.insertAdjacentHTML('beforebegin', `<span class="sm-mark">${mark({ tile: true })}</span>`);
    }
  }

  /* Ajustes › Apariencia: paletas y temas como sellos; se estampan al elegir */
  const PAL_KANJI = { violeta: '紫', sakura: '桜', matcha: '茶', oceano: '海', luna: '月', mono: '墨' };
  const THEME_KANJI = { claro: '紙', oscuro: '墨', oled: '夜', sistema: '自' };
  function decorateSettings(app, key) {
    const sw = Array.from(app.querySelectorAll('.nj-b-swatch[data-arg]'));
    sw.forEach((b) => {
      const i = b.querySelector('i');
      const k = PAL_KANJI[b.getAttribute('data-arg')];
      if (i && k && !i.querySelector('.sm-pal-k')) i.insertAdjacentHTML('afterbegin', `<span class="sm-pal-k" aria-hidden="true">${k}</span>`);
    });
    const th = Array.from(app.querySelectorAll('.nj-b-theme[data-arg]'));
    th.forEach((b) => {
      const box = b.querySelector('.nj-b-theme-sw');
      const k = THEME_KANJI[b.getAttribute('data-arg')];
      if (box && k && !box.querySelector('.sm-seal')) { ensurePositioned(box); box.insertAdjacentHTML('beforeend', `<span class="sm-seal sm-theme-seal" aria-hidden="true">${k}</span>`); }
    });
    [['pal', sw, (b) => b.querySelector('i')], ['tema', th, (b) => b.querySelector('.sm-theme-seal')]].forEach(([name, list, target]) => {
      if (!list.length) return;
      const on = list.find(isOn);
      const v = on ? on.getAttribute('data-arg') : '';
      const mk = key + ':' + name;
      if (pickMem[mk] !== undefined && pickMem[mk] !== v && on) stamp(target(on));
      pickMem[mk] = v;
    });
  }

  const isOn = (b) => ['aria-checked', 'aria-pressed', 'aria-selected'].some((a) => b.getAttribute(a) === 'true');
  const statusOf = (b) => { const v = b.getAttribute('data-value') || b.getAttribute('data-status') || ''; return KANJI[v] ? v : ''; };

  function decorateStatusPicker(app, key) {
    app.querySelectorAll('[data-region="status-picker"]').forEach((region, ri) => {
      const k = key + ':' + ri;
      const btns = Array.from(region.querySelectorAll('button, [role="radio"], [role="option"]')).filter(statusOf);
      if (!btns.length) return;
      region.classList.add('sm-picker');
      btns.forEach((b) => {
        b.classList.add('sm-pick');
        if (!b.querySelector('.sm-seal')) b.insertAdjacentHTML('afterbegin', seal(statusOf(b), { hidden: true, cls: 'sm-pick-seal' }));
      });
      const current = () => { const on = btns.find(isOn); return on ? statusOf(on) : ''; };
      const now = current();
      if (pickMem[k] !== undefined && pickMem[k] !== now && now) stamp(btns.find(isOn).querySelector('.sm-seal'));
      pickMem[k] = now;
      region.addEventListener('click', (ev) => {
        if (!ev.target.closest('.sm-pick')) return;
        requestAnimationFrame(() => {
          if (!region.isConnected) return;
          const n = current();
          if (n && n !== pickMem[k]) stamp(btns.find(isOn).querySelector('.sm-seal'));
          pickMem[k] = n;
        });
      });
    });
  }

  function epSeal(ep, animate) {
    let s = ep.querySelector(':scope > .sm-ep-seal');
    if (!s) {
      ensurePositioned(ep);
      ep.insertAdjacentHTML('afterbegin', '<span class="sm-ep-seal" role="img" aria-label="Visto" title="Visto">済</span>');
      s = ep.querySelector(':scope > .sm-ep-seal');
    }
    if (animate) stamp(s);
  }
  function decorateEpisodes(app, key) {
    app.querySelectorAll('[data-region="episodes"]').forEach((region, ri) => {
      const k = key + ':' + ri;
      const prev = watchMem[k];
      const seen = new Set();
      region.querySelectorAll('.nj-ep[data-watched="true"]').forEach((ep) => {
        const id = ep.getAttribute('data-ep') || '';
        seen.add(id);
        epSeal(ep, prev && !prev.has(id));
      });
      watchMem[k] = seen;
      if (!window.MutationObserver) return;
      new MutationObserver((muts) => muts.forEach((m) => {
        const ep = m.target;
        if (!ep.classList || !ep.classList.contains('nj-ep')) return;
        const id = ep.getAttribute('data-ep') || '';
        if (ep.getAttribute('data-watched') === 'true') { epSeal(ep, true); watchMem[k].add(id); }
        else { const s = ep.querySelector(':scope > .sm-ep-seal'); if (s) s.remove(); watchMem[k].delete(id); }
      })).observe(region, { attributes: true, attributeFilter: ['data-watched'], subtree: true });
    });
  }

  function decorateAiring(app) {
    app.querySelectorAll('[data-region="airing"] [data-time]').forEach((it) => {
      it.classList.add('sm-air');
      const now = it.getAttribute('data-now') === 'true' || it.hasAttribute('aria-current') || it.classList.contains('is-now');
      if (now && !it.querySelector('.sm-now-enso')) { it.classList.add('sm-now'); it.insertAdjacentHTML('afterbegin', nowGlyph()); }
    });
  }
  function decorateEmpty(app) {
    app.querySelectorAll('[data-region="empty"]').forEach((region) => {
      if (region.querySelector('.sm-empty-ill')) return;
      region.classList.add('sm-empty');
      region.insertAdjacentHTML('afterbegin', mark({ cls: 'sm-empty-ill' }));
      if (getComputedStyle(region).textAlign === 'center') region.querySelector('.sm-empty-ill').style.marginInline = 'auto';
    });
  }
  function decorateLink(app) {
    app.querySelectorAll('[data-region="link"]').forEach((region) => {
      if (region.querySelector('.sm-link-glyph')) return;
      const off = /disconnected|off|error/.test(region.getAttribute('data-state') || '') || /desconectad/i.test(region.textContent || '');
      region.classList.add('sm-link');
      region.classList.toggle('is-off', off);
      const head = region.querySelector('.nj-section-head .nj-h2, .nj-h2, .nj-h3, h2, h3');
      (head || region).insertAdjacentHTML('afterbegin', linkGlyph());
    });
  }

  function decorateTrace(app, ctx) {
    const plat = app.dataset.platform || ctx.platform || 'desktop';
    const scr = app.dataset.screen || ctx.screen || 'x';
    const motion = app.dataset.motion || ctx.motion || 'full';
    const isLab = !app.dataset.screen;
    const first = isLab || lastScreen[plat] !== scr;
    lastScreen[plat] = scr;
    if (!first || motion === 'calm' || reducedMQ.matches) return;
    const scope = app.querySelector('[data-region="tonight"]') || app;
    const rings = Array.from(scope.querySelectorAll('.sm-ring')).slice(0, motion === 'perf' ? 1 : 6);
    if (!rings.length) return;
    rings.forEach((r, i) => { r.style.setProperty('--i', i); r.classList.add('is-tracing'); if (i === 0) r.classList.add('is-first'); });
    const last = rings[rings.length - 1].querySelector('.sm-enso-dry');
    const done = () => rings.forEach((r) => r.classList.remove('is-tracing'));
    if (last) last.addEventListener('animationend', done, { once: true });
    setTimeout(done, 2200);
  }

  function decoratePlay(app) {
    app.addEventListener('click', (ev) => {
      const p = ev.target.closest('[data-sumi-play]');
      if (!p || p.closest('[data-cards-local]')) return;
      if (NJ.screens && NJ.screens.reproductor) NJ.go('reproductor');
    });
  }

  NJ.registerDirection({
    id: 'sumi',
    name: 'Sumi y hanko',
    track: 'identity',
    tagline: 'La identidad de hoy, entintada: violeta Nijiyome, el kitsune dentro del ensō y los estados de lista como sellos.',
    summary: 'Seis paletas en lugar de veinte: Violeta Nijiyome (absorbe Dark Matter), Sakura (Cherry Blossom, Neon Tokyo, Synthwave), Matcha (Zen Garden, Aurora), Océano (Ocean, Midnight, Deep Ocean, Frostbite), Luna de sangre (Blood Moon, Vampire) y Mono. Royal Gold, Solar, Caffeine y Cyberpunk salen: el ámbar choca con el sello 予 de pendiente y el lima es el cliché de negro con un neón.',
    fonts: ['https://fonts.googleapis.com/css2?family=Zen+Kaku+Gothic+New:wght@400;500;700&family=Yuji+Boku&family=M+PLUS+1+Code:wght@400;600&display=swap'],
    palettes: [
      { id: 'violeta', name: 'Violeta Nijiyome', swatch: '#7c4dff' },
      { id: 'sakura', name: 'Sakura', swatch: '#e86a92' },
      { id: 'matcha', name: 'Matcha', swatch: '#5e9e6e' },
      { id: 'oceano', name: 'Océano', swatch: '#0091c2' },
      { id: 'luna', name: 'Luna de sangre', swatch: '#c62828' },
      { id: 'mono', name: 'Mono', swatch: '#808080' },
    ],
    defaultCard: 'hanko',
    notes: {
      thesis: 'Nijiyome ya tiene un mundo: un kitsune entintado dentro de un ensō y un sello 虹. Esta dirección lo toma en serio en vez de reemplazarlo. El fondo es tinta sumi con matiz (no negro plano) o papel frío, el violeta sigue siendo el acento por defecto, y dos objetos del mundo hacen trabajo real: el sello hanko es el estado de la lista y el trazo del ensō es el progreso de lo que estás viendo. Todo lo demás es Material 3 limpio.',
      signature: 'El sello se estampa: al cambiar el estado o marcar un episodio como visto, el sello cae de 1,15 a 1 con un leve desenfoque de tinta que se asienta en 420 ms. Y al abrir Inicio, el ensō de cada "continuar" se traza hasta el punto exacto donde lo dejaste. Un momento por pantalla: el trazo en Inicio, el estampado en la Ficha.',
      motion: 'Curva de salida exponencial cubic-bezier(.16,1,.3,1). Trazo del ensō: 720 ms, escalonado de 70 ms, máximo 6 aros, solo la primera vez que se entra a la pantalla (no al volver a dibujarla). Estampado: 420 ms. Modo calmo y movimiento reducido: el ensō aparece completo y el sello solo hace un fundido de 140 ms, porque confirma la acción. Modo rendimiento: sin desenfoque, sin aguada animada, solo se traza el primer aro.',
      android: 'Navegación inferior, top app bar, hojas inferiores y chips de Material 3 sin cambios; la dirección solo cambia color, tipo, forma y los dos gestos. El selector de estado es una fila de seis sellos de 36 dp dentro de objetivos de 48 dp, con el nombre debajo. El ensō rodea el botón de reproducir de "continuar" y de la ficha. Con color dinámico activo el acento sale del fondo de pantalla, pero la tinta sumi y los colores de los sellos se mantienen para que el estado se lea igual.',
      risks: 'Quien no lee japonés no entiende 見 o 予 a primera vista: el nombre en español va siempre al lado (o en aria-label y tooltip donde no cabe) y el color de estado es estable. Luna de sangre comparte rojo con el sello 止 de abandonado; el kanji los separa. Yuji Boku es una fuente CJK pesada: hay que subsetearla y usarla solo en títulos grandes y sellos grandes; los sellos chicos usan Zen Kaku Gothic New en negrita para que el kanji se lea a 12 px. El violeta del logo (#7c4dff) no llega a 4,5:1 sobre papel claro, así que en modo claro el acento es #5b34d1. Si los sellos se usan como adorno se vuelven stickers: quedan solo para estado de lista, episodios vistos y la marca.',
      compose: 'Ensō: Modifier.drawBehind con drawArc(startAngle = -90f, sweepAngle = 360f * progreso, useCenter = false, style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round)); debajo, la aguada como Path precalculado (mismo generador de ancho variable) con alpha 0,18. El progreso se anima con animateFloatAsState(tween(720, easing = CubicBezierEasing(.16f, 1f, .3f, 1f))) arrancando en 0 solo la primera vez (rememberSaveable con la marca de ya trazado). Sello: composable Seal(status) con Box + Text; Animatable(1.15f).animateTo(1f, tween(420)) aplicado en graphicsLayer { scaleX = s; scaleY = s; rotationZ = -4f } y, solo en gama alta (API 31+ y modo rendimiento apagado), renderEffect = BlurEffect(r, r) con r de 4 px a 0. Modo rendimiento: sin BlurEffect, sin aguada animada y un solo ensō trazado. Movimiento reducido (ANIMATOR_DURATION_SCALE = 0): fundido de 140 ms. Tipos: Zen Kaku Gothic New y Yuji Boku como Font resources en la Typography de MaterialTheme. Paletas: las seis semillas entran al generador actual de tokens.json para light, dark y amoled.',
      paletas: 'Violeta Nijiyome queda primera y absorbe Dark Matter (#5B21B6, mismo tono). Sakura absorbe Cherry Blossom, Neon Tokyo y Synthwave (rosas y magentas que en pantalla se confundían). Matcha absorbe Zen Garden y Aurora (verdes y verde azulado). Océano absorbe Ocean, Midnight, Deep Ocean y Frostbite (cuatro azules). Luna de sangre absorbe Blood Moon y Vampire. Mono se queda como la opción sin color. Royal Gold, Solar y Caffeine salen porque el ámbar es el color del sello 予 y empuja al cliché crema con terracota; Cyberpunk sale porque lima sobre negro es negro con un solo neón.',
    },
    decorate(app, ctx) {
      ctx = ctx || {};
      const key = (app.dataset.platform || ctx.platform || 'desktop') + ':' + (app.dataset.screen || ctx.screen || 'x');
      decorateBrand(app);
      decorateStatusPicker(app, key);
      decorateSettings(app, key);
      decorateEpisodes(app, key);
      decorateAiring(app);
      decorateEmpty(app);
      decorateLink(app);
      decorateTrace(app, ctx);
      decoratePlay(app);
    },
  });

  NJ.sumi = { mark, seal, ensoPath, KANJI };
})();
