/* Dirección Obi (agente D2). Fajas de novela ligera y lomos de estantería como lenguaje de estado.
   Solo registra dirección y tarjetas; decorate() añade la banda de cabecera, el barrido de color y la
   transición compartida tarjeta → ficha. Ver CONTRATO.md. */
(function () {
  if (!window.NJ) return;
  const NJ = window.NJ;
  const D = NJ.data;
  const h = NJ.h;
  const esc = h.esc;
  const STATUS_IDS = new Set(D.statuses.map((s) => s.id));

  /* ---------------- color: texto por luminancia ---------------- */
  const INK = '#121212';
  const PAPER = '#ffffff';
  const hexRgb = (hex) => {
    let x = String(hex || '#888888').replace('#', '');
    if (x.length === 3) x = x.split('').map((c) => c + c).join('');
    return [0, 2, 4].map((i) => parseInt(x.substr(i, 2), 16) || 0);
  };
  const toHex = (rgb) => '#' + rgb.map((c) => Math.max(0, Math.min(255, Math.round(c))).toString(16).padStart(2, '0')).join('');
  const lin = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  const lum = (rgb) => 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2]);
  const ratio = (a, b) => (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
  const L_INK = lum(hexRgb(INK));
  const inkCache = {};
  /* Elige tinta o papel por contraste y, si el par queda por debajo de 5:1, empuja el fondo
     hacia el lado contrario al texto hasta pasar. Así ninguna faja baja de 4.5:1. */
  function inkFor(hex) {
    if (inkCache[hex]) return inkCache[hex];
    let rgb = hexRgb(hex);
    const useInk = ratio(lum(rgb), L_INK) >= ratio(lum(rgb), 1);
    for (let k = 0; k < 14 && ratio(lum(rgb), useInk ? L_INK : 1) < 5; k++) {
      rgb = rgb.map((c) => (useInk ? c + (255 - c) * 0.08 : c * 0.92));
    }
    return (inkCache[hex] = { bg: toHex(rgb), fg: useInk ? INK : PAPER });
  }
  const mix = (a, b, t) => { const x = hexRgb(a); const y = hexRgb(b); return toHex(x.map((c, i) => c * (1 - t) + y[i] * t)); };

  /* Tono de la faja: color de la serie mientras se sigue o está pendiente; color de estado al
     completar; apagada en pausa; neutra si se abandona. */
  function tone(item) {
    const st = item.status;
    if (st === 'completed') return { tone: 'completed', style: '' };
    if (st === 'dropped') return { tone: 'dropped', style: '' };
    const base = item.color || '#8c8c8c';
    const c = inkFor(st === 'paused' ? mix(base, '#8c8c8c', 0.55) : base);
    return { tone: 'series', style: `--obi-bg:${c.bg};--obi-fg:${c.fg}` };
  }

  /* ---------------- copy de la faja ---------------- */
  /* Completa el ítem con NJ.data (las pantallas a veces pasan copias parciales) y adapta manga/novela,
     que traen capítulo y avance 0..1 en lugar de episodios. */
  const MANGA_FAJAS = ['#c4122f', '#2353c4', '#e9a24b', '#1a7442', '#6c4cc0', '#00706f', '#ffd60a', '#e7a0b8'];
  function prep(item) {
    const base = item && D.byId[item.id];
    let it = base && base !== item ? Object.assign({}, base, item) : item;
    if (it && it.chapter && it.eps == null) {
      const hash = String(it.id).split('').reduce((a, c) => a + c.charCodeAt(0), 0);
      it = Object.assign({}, it, { manga: true, status: it.status || 'watching', color: it.color || MANGA_FAJAS[hash % MANGA_FAJAS.length] });
    }
    return it;
  }
  function phrase(item, o) {
    const s = (o && o.show) || {};
    if (item.manga) return { long: `${item.chapter} de ${item.total}`, short: item.chapter };
    const st = item.status;
    const eps = item.eps || 0;
    const prog = item.progress || 0;
    const aired = item.aired;
    if (item.resume && (st === 'watching' || st === 'rewatching')) return { long: `Ep. ${item.resume.ep} · quedan ${item.resume.left}`, short: `Quedan ${item.resume.left}` };
    if (st === 'completed') return { long: `Completado · ${eps} eps`, short: `${eps} de ${eps}` };
    if (st === 'dropped') return { long: `Dejado en el Ep. ${prog}`, short: `Ep. ${prog}` };
    if (st === 'paused') return { long: `En pausa en el Ep. ${prog}`, short: `Ep. ${prog}` };
    if (aired === 0) return { long: `Estreno · ${eps} eps`, short: 'Estreno' };
    if (o && o.context === 'continue') return { long: `Sigue con el Ep. ${prog + 1}`, short: `Ep. ${prog + 1}` };
    if (s.next && item.next) return { long: `Ep. ${item.next.ep} en ${item.next.in}`, short: `Ep. ${item.next.ep} · ${item.next.in}` };
    if (st === 'planning') return { long: `${eps} eps · ${item.format || ''}`, short: `${eps} eps` };
    const behind = aired - prog;
    if (behind === 1) return { long: `Nuevo · Ep. ${aired}`, short: `Nuevo Ep. ${aired}` };
    if (behind > 1) return { long: `${behind} por ver · sigue el Ep. ${prog + 1}`, short: `${behind} por ver` };
    if (item.next) return { long: `Al día · Ep. ${item.next.ep} en ${item.next.in}`, short: 'Al día' };
    return { long: 'Al día', short: 'Al día' };
  }
  function extra(item) {
    if (item.manga) return `${item.kind || 'manga'} · ${Math.round((item.progress || 0) * 100)} % leído`;
    if (item.status === 'planning' || !item.progress) return [item.format, item.year, (item.genres || [])[0]].filter(Boolean).join(' · ');
    return `${item.progress} de ${item.eps} vistos${item.score ? ' · puntaje ' + item.score : ''}`;
  }
  /* Solo "nuevo episodio" y "estreno" llevan la banda diagonal. */
  function novelty(item) {
    const now = (D.airingToday || []).find((a) => a.id === item.id && a.now);
    if (now) return { long: `Estreno Ep. ${now.ep}`, short: 'Estreno' };
    if (item.manga) return null;
    if (item.aired === 0) return { long: 'Estreno', short: 'Estreno' };
    if ((item.status === 'watching' || item.status === 'rewatching') && item.aired - (item.progress || 0) === 1) return { long: `Nuevo Ep. ${item.aired}`, short: 'Nuevo' };
    return null;
  }

  const norm = (o) => Object.assign({ size: 'm', context: 'row' }, o || {}, { show: (o && o.show) || {} });
  const stName = (item) => (item.manga && item.status === 'watching' ? 'Leyendo' : item.status ? h.status(item.status).name : 'Sin lista');
  const titleOf = (item) => item.short || item.title;
  /* Punto de corte natural después de ★ (Magical★Explorer) para no partir la palabra al azar. */
  const tHtml = (item) => esc(titleOf(item)).replace(/★/g, '★<wbr>');
  const subOf = (item) => (item.manga ? [item.kind, item.chapter].filter(Boolean).join(' · ') : [item.format, item.year].filter(Boolean).join(' · '));
  const keyOf = (item, o, style) => `${style || 'obi'}|${item.id}|${o.context}|${o.size}`;
  const open = (style, item, o, label, cls) =>
    `<button type="button" class="nj-card${cls ? ' ' + cls : ''}" data-style="${style}" data-context="${esc(o.context)}" data-size="${esc(o.size)}" data-status="${esc(item.status || '')}" data-id="${esc(item.id)}" lang="es" aria-label="${esc(label)}">`;
  const img = (item, cls) => `<img${cls ? ` class="${cls}"` : ''} src="${esc(item.poster)}" alt="" loading="lazy" decoding="async" width="200" height="300">`;
  const bar = (v) => `<span class="nj-progress" style="--v:${Math.max(0, Math.min(1, v || 0))}"><i></i></span>`;
  const progOf = (item) => (item.manga ? item.progress || 0 : item.resume ? item.resume.pct : item.eps ? (item.progress || 0) / item.eps : 0);

  /* ---------------- tarjeta 1: póster con faja inferior ---------------- */
  function band(item, o, ph, t, cls) {
    const short = o.size === 's' && o.context !== 'hero';
    const withBar = o.show.progress || o.context === 'continue';
    return `<span class="obi-band obi-vt-src${cls ? ' ' + cls : ''}" data-obi-tone="${t.tone}" data-obi-key="${esc(keyOf(item, o))}" style="${t.style}">${withBar ? bar(progOf(item)) : ''}`
      + (o.context === 'hero' ? `<span class="obi-title">${tHtml(item)}</span>` : '')
      + `<span class="obi-st obi-tab">${esc(stName(item))}</span><span class="obi-l1">${t.tone === 'paper' ? '<i class="obi-sw"></i>' : ''}<span class="obi-ph"><span class="obi-cond">${esc(short ? ph.short : ph.long)}</span></span>`
      + (o.context === 'continue' ? `<span class="obi-go">${h.icon('play_arrow', { fill: true, size: 18 })}Reanudar</span>` : '')
      + `</span>${o.context === 'continue' || o.context === 'compact' ? '' : `<span class="obi-l2">${esc(extra(item))}</span>`}</span>`;
  }

  NJ.registerCard({
    id: 'obi', name: 'Obi · faja', dir: 'obi',
    desc: 'Póster con faja inferior en el color de la serie: estado, episodio y la frase del momento. En hover o foco la faja se levanta y deja ver una segunda línea.',
    render(raw, opts) {
      const item = prep(raw);
      const o = norm(opts);
      const ph = phrase(item, o);
      const t = o.context === 'wish' ? { tone: 'paper', style: `--obi-sw:${esc(item.color || '#888')}` } : tone(item);
      const label = `${item.title}. ${stName(item)}. ${ph.long}. ${extra(item)}`;
      if (o.context === 'compact') {
        return open('obi', item, o, label) + `<span class="nj-card-art">${img(item)}</span><span class="nj-card-title">${tHtml(item)}</span><span class="nj-card-sub">${esc([o.show.status ? stName(item) : '', subOf(item)].filter(Boolean).join(' · '))}</span><span class="obi-tag obi-vt-src" data-obi-tone="${t.tone}" data-obi-key="${esc(keyOf(item, o))}" style="${t.style}"><span class="obi-cond">${esc(ph.short)}</span></span></button>`;
      }
      if (o.context === 'continue') {
        return open('obi', item, o, label) + `<span class="nj-card-art">${img(item)}</span><span class="obi-body"><span class="nj-card-title">${tHtml(item)}</span><span class="nj-card-sub">${esc(subOf(item))}${item.eps ? ' · ' + esc(item.eps) + ' eps' : ''}</span></span>${band(item, o, ph, t)}</button>`;
      }
      return open('obi', item, o, label) + `<span class="nj-card-art obi-art">${img(item)}${band(item, o, ph, t)}</span>`
        + (o.context === 'hero' ? `<span class="nj-card-sub">${esc(subOf(item))}${item.studio ? ' · ' + esc(item.studio) : ''}</span>` : `<span class="nj-card-title">${tHtml(item)}</span><span class="nj-card-sub">${esc(subOf(item))}</span>`)
        + '</button>';
    },
  });

  /* ---------------- tarjeta 2: lomo de estantería ---------------- */
  function spineNum(item) {
    if (item.manga) return `<b>${Math.round((item.progress || 0) * 100)}</b><small>%</small>`;
    if (item.status === 'planning' || !item.progress) return `<b>${esc(item.eps || '?')}</b><small>eps</small>`;
    return `<b>${esc(item.progress)}</b><small>/${esc(item.eps)}</small>`;
  }
  function spine(item, o, t, extraCls) {
    const wish = o.context === 'wish';
    const c = inkFor(item.color || '#8c8c8c');
    return `<span class="lomo obi-vt-src${extraCls ? ' ' + extraCls : ''}" data-obi-tone="${wish ? 'paper' : t.tone}" data-obi-key="${esc(keyOf(item, o, 'lomo'))}" style="${wish ? '' : t.style}">`
      + `<span class="lomo-art">${img(item)}</span>`
      + `<span class="lomo-title">${tHtml(item)}</span>`
      + (wish ? `<span class="lomo-obi" style="--obi-bg:${c.bg};--obi-fg:${c.fg}">${spineNum(item)}</span>` : `<span class="lomo-num">${spineNum(item)}</span>`)
      + (o.show.progress && !wish ? bar(progOf(item)) : '')
      + '</span>';
  }
  NJ.registerCard({
    id: 'obi-lomo', name: 'Obi · lomo', dir: 'obi',
    desc: 'Lomo estrecho con título vertical y número grande, para estanterías y listas. En continuar y compacta el lomo se acuesta; en destacada se abre junto a la portada.',
    render(raw, opts) {
      const item = prep(raw);
      const o = norm(opts);
      const ph = phrase(item, o);
      const t = tone(item);
      const label = `${item.title}. ${stName(item)}. ${ph.long}. ${extra(item)}`;
      if (o.context === 'continue' || o.context === 'compact') {
        const flatKey = esc(keyOf(item, o, 'lomo'));
        return open('obi-lomo', item, o, label) + `<span class="lomo is-flat obi-vt-src" data-obi-tone="${t.tone}" data-obi-key="${flatKey}" style="${t.style}"><span class="lomo-art">${img(item)}</span><span class="lomo-mid"><span class="lomo-title">${tHtml(item)}</span><span class="lomo-ph">${esc(o.context === 'compact' && o.show.status && !/^(completed|paused|dropped)$/.test(item.status || '') ? stName(item) + ' · ' + ph.long : ph.long)}</span></span><span class="lomo-num">${spineNum(item)}</span>${o.context === 'continue' ? `<span class="lomo-go">${h.icon('play_arrow', { fill: true })}</span>` : ''}${o.context === 'continue' || o.show.progress ? bar(progOf(item)) : ''}</span></button>`;
      }
      if (o.context === 'hero') {
        return open('obi-lomo', item, o, label) + `<span class="lomo-book">${spine(item, o, t)}<span class="lomo-cover">${img(item)}</span></span><span class="nj-card-sub">${esc(ph.long)}</span></button>`;
      }
      return open('obi-lomo', item, o, label) + spine(item, o, t) + '</button>';
    },
  });

  /* ---------------- tarjeta 3: banda diagonal de novedad ---------------- */
  function ribbon(n, o) {
    if (!n) return '';
    return `<span class="obi-ribbon obi-vt-src"><span>${esc(o.size === 's' && o.context !== 'hero' ? n.short : n.long)}</span></span>`;
  }
  NJ.registerCard({
    id: 'obi-diagonal', name: 'Obi · diagonal', dir: 'obi',
    desc: 'Póster limpio; solo cuando hay episodio nuevo o estreno cruza la esquina una banda diagonal. El resto de las tarjetas no la lleva, así la novedad se ve de lejos.',
    render(raw, opts) {
      const item = prep(raw);
      const o = norm(opts);
      const n = novelty(item);
      const ph = phrase(item, o);
      const label = `${item.title}. ${n ? n.long + '. ' : ''}${stName(item)}. ${ph.long}`;
      const chip = o.show.status && item.status ? `<span class="nj-card-badges">${h.statusChip(item.status)}</span>` : '';
      if (o.context === 'compact') {
        const tail = n ? `<span class="obi-slant obi-vt-src">${esc(n.long)}</span>` : o.show.status ? h.statusChip(item.status) : `<span class="nj-card-sub">${esc(ph.short)}</span>`;
        return open('obi-diagonal', item, o, label) + `<span class="nj-card-art">${img(item)}</span><span class="nj-card-title">${tHtml(item)}</span><span class="nj-card-sub">${esc(ph.long)}</span><span class="obi-tail">${tail}</span></button>`;
      }
      if (o.context === 'continue') {
        return open('obi-diagonal', item, o, label) + `<span class="nj-card-art">${img(item)}</span><span class="obi-body"><span class="nj-card-title">${tHtml(item)}</span><span class="nj-card-sub">${esc(ph.long)}</span>${bar(progOf(item))}</span><span class="obi-play">${h.icon('play_arrow', { fill: true })}</span>${ribbon(n, o)}</button>`;
      }
      return open('obi-diagonal', item, o, label) + `<span class="nj-card-art${n ? '' : ' obi-vt-src'}">${img(item)}${chip}${ribbon(n, o)}${o.show.progress && item.eps && item.progress ? bar(progOf(item)) : ''}</span><span class="nj-card-title">${tHtml(item)}</span><span class="nj-card-sub">${esc(o.show.next && item.next ? ph.long : o.context === 'wish' || o.context === 'hero' ? ph.long : subOf(item))}</span></button>`;
    },
  });

  /* ---------------- decorate: banda de cabecera, barrido, transición compartida ---------------- */
  const mem = new Map();
  let pendingVT = null;
  let replaying = false;
  const reduced = () => { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; } };
  const isOn = (v) => v === 'true' || v === '1' || v === '';

  function sweep(el, from, app) {
    if (!from || app.dataset.motion === 'perf') return;
    el.style.setProperty('--obi-from', from);
    el.classList.remove('is-obi-sweep');
    void el.offsetWidth;
    el.classList.add('is-obi-sweep');
    if (!el._obiEnd) {
      el._obiEnd = (ev) => { if (ev.animationName === 'obi-sweep') el.classList.remove('is-obi-sweep'); };
      el.addEventListener('animationend', el._obiEnd);
    }
  }
  const sig = (el) => (el.dataset.obiTone || '') + (el.style.getPropertyValue('--obi-bg') || '');
  function remember(el, key, app) {
    const prev = mem.get(key);
    const now = { sig: sig(el), bg: getComputedStyle(el).backgroundColor };
    mem.set(key, now);
    if (prev && prev.sig !== now.sig) sweep(el, prev.bg, app);
  }

  function selectedStatus(app) {
    const sp = app.querySelector('[data-region="status-picker"]');
    if (!sp) return null;
    const sel = sp.querySelector('[aria-checked="true"],[aria-pressed="true"],[aria-selected="true"],[aria-current="true"]');
    const v = sel && (sel.getAttribute('data-status') || sel.getAttribute('data-value'));
    return v && STATUS_IDS.has(v) ? v : null;
  }
  function heroItem(hero, app) {
    const idEl = hero.getAttribute('data-id') ? hero : hero.querySelector('[data-id]');
    const base = (idEl && D.byId[idEl.getAttribute('data-id')]) || D.titles[0];
    const st = app.dataset.screen === 'ficha' ? selectedStatus(app) : null;
    return st ? Object.assign({}, base, { status: st }) : base;
  }
  function fillHeadband(band, item) {
    const t = tone(item);
    band.dataset.obiTone = t.tone;
    band.style.removeProperty('--obi-bg');
    band.style.removeProperty('--obi-fg');
    if (t.style) t.style.split(';').forEach((d) => { const i = d.indexOf(':'); if (i > 0) band.style.setProperty(d.slice(0, i), d.slice(i + 1)); });
    const ph = phrase(item, { context: 'hero', show: { next: true } });
    band.innerHTML = `<span class="obi-st">${esc(stName(item))}</span><span class="obi-ph"><span class="obi-cond">${esc(ph.long)}</span></span><span class="obi-hx">${esc(extra(item))}</span>`;
  }
  function headband(hero, app, mk, i) {
    if (hero.querySelector(':scope > .obi-headband')) return;
    const item = heroItem(hero, app);
    const band = document.createElement('div');
    band.className = 'obi-headband';
    band.setAttribute('aria-hidden', 'true');
    fillHeadband(band, item);
    const cs = getComputedStyle(hero);
    if (cs.position === 'static') hero.style.position = 'relative';
    const H = app.dataset.platform === 'android' ? 36 : 40;
    hero.style.paddingBottom = `calc(${cs.paddingBottom} + ${H}px)`;
    hero.setAttribute('data-obi-head', '');
    hero.appendChild(band);
    remember(band, mk + '|hero|' + i + '|' + item.id, app);
    if (pendingVT && i === 0 && app.dataset.screen === 'ficha' && pendingVT.platform === app.dataset.platform && !pendingVT.target) {
      band.style.viewTransitionName = 'obi-shared';
      pendingVT.target = band;
    }
  }

  function onCardClick(ev) {
    if (replaying) return;
    const app = ev.currentTarget;
    const card = ev.target.closest('.nj-card[data-id]');
    if (!card || !app.contains(card) || ev.target.closest('[data-stop]') || card.closest('[data-cards-local]')) return;
    if (typeof document.startViewTransition !== 'function' || !NJ.screens.ficha || location.hash !== '#pantallas') return;
    if (app.dataset.motion !== 'full' || reduced()) return;
    const src = card.querySelector('.obi-vt-src');
    if (!src) return;
    ev.stopPropagation();
    ev.preventDefault();
    src.style.viewTransitionName = 'obi-shared';
    pendingVT = { platform: app.dataset.platform, target: null };
    let ran = false;
    const replay = () => { if (ran) return; ran = true; replaying = true; try { card.click(); } finally { replaying = false; } };
    let vt;
    try { vt = document.startViewTransition(replay); } catch (e) { pendingVT = null; replay(); return; }
    /* Si el navegador no llega a pintar (pestaña oculta, captura), la navegación no espera a la transición. */
    setTimeout(() => { if (!ran) { try { vt.skipTransition(); } catch (e) { /* ya terminó */ } replay(); } }, 400);
    const done = () => { if (pendingVT && pendingVT.target) pendingVT.target.style.viewTransitionName = ''; pendingVT = null; };
    vt.finished.then(done, done);
    if (vt.ready) vt.ready.catch(() => {});
    if (vt.updateCallbackDone) vt.updateCallbackDone.catch(() => {});
  }

  /* Bloque extra solo en la página Tarjetas: contextos que el laboratorio no muestra (deseos,
     destacada, tamaño L) y la prueba del barrido al marcar visto. */
  let demoDone = false;
  function demo(app, cardId) {
    const host = app.firstElementChild;
    if (!host || host.querySelector('.obi-demo')) return;
    const r = NJ.cards[cardId];
    const wrap = document.createElement('div');
    wrap.className = 'obi-demo';
    const more = `<div><div class="nj-meta" style="margin-bottom:8px">Deseos, tamaño L y destacada</div><div class="obi-demo-row" data-cards-local>${r.render(D.byId.goblin, { size: 'm', context: 'wish' })}${r.render(D.byId.edgerunners, { size: 'm', context: 'wish' })}${r.render(D.byId.aonohako, { size: 'l', context: 'row', show: { status: true, progress: true, next: true } })}${r.render(D.byId.laidoff, { size: 'm', context: 'hero', show: { next: true } })}</div></div>`;
    wrap.innerHTML = more + (cardId === 'obi' ? `<div><div class="nj-meta" style="margin-bottom:8px">Cambio de estado: marca el último episodio y la faja se recolorea con un barrido</div><div class="obi-demo-row" data-cards-local data-obi-demo></div><div class="nj-cluster" style="margin-top:12px"><button type="button" class="nj-btn is-filled" data-obi-toggle></button></div></div>` : '');
    host.appendChild(wrap);
    const mk = ['lab', app.dataset.mode, app.dataset.palette].join('|');
    wrap.querySelectorAll('[data-obi-key]').forEach((el) => remember(el, mk + '|more|' + el.dataset.obiKey, app));
    const row = wrap.querySelector('[data-obi-demo]');
    if (!row) return;
    const btn = wrap.querySelector('[data-obi-toggle]');
    const paint = () => {
      const base = D.byId.kikansha;
      const it = Object.assign({}, base, { aired: 12, status: demoDone ? 'completed' : 'watching', progress: demoDone ? 12 : 11 });
      row.innerHTML = NJ.cards.obi.render(it, { size: 'm', context: 'row', show: { status: true, progress: true } }) + NJ.cards['obi-lomo'].render(it, { size: 'm', context: 'row', show: { progress: true } }) + NJ.cards.obi.render(it, { size: 'm', context: 'continue' });
      btn.innerHTML = demoDone ? `${h.icon('undo')}Deshacer` : `${h.icon('check')}Marcar Ep. 12 como visto`;
      row.querySelectorAll('[data-obi-key]').forEach((el) => remember(el, mk + '|demo|' + el.dataset.obiKey, app));
    };
    paint();
    btn.addEventListener('click', () => { demoDone = !demoDone; paint(); });
  }

  function decorate(app, ctx) {
    if (!app) return;
    const lab = ctx && ctx.screen === 'tarjetas';
    const mk = [app.dataset.platform || 'desktop', app.dataset.mode, app.dataset.palette, app.dataset.screen || (lab ? 'lab' : '')].join('|');
    app.querySelectorAll('[data-region="status-picker"]').forEach((sp) => {
      sp.querySelectorAll('button,[role="radio"],[role="option"],[role="menuitemradio"]').forEach((b) => {
        if (b.hasAttribute('data-status')) return;
        const v = b.getAttribute('data-value') || b.value;
        if (v && STATUS_IDS.has(v)) b.setAttribute('data-status', v);
      });
    });
    app.querySelectorAll('[data-region="hero"]').forEach((hero, i) => headband(hero, app, mk, i));
    app.querySelectorAll('[data-region="episodes"]').forEach((reg) => {
      const all = Array.from(reg.querySelectorAll('.nj-ep'));
      const nxt = all.find((e) => e.getAttribute('data-next') === 'true') || all.find((e) => isOn(e.getAttribute('data-aired')) && !isOn(e.getAttribute('data-watched')));
      if (nxt) nxt.setAttribute('data-obi-next', '');
    });
    app.querySelectorAll('.nj-card [data-obi-key]').forEach((el) => remember(el, mk + '|' + el.dataset.obiKey, app));
    if (lab) { if (/^obi/.test(app.dataset.card || '')) demo(app, app.dataset.card); return; }
    app.addEventListener('click', onCardClick, true);
    /* Si el selector de estado solo cambia aria-checked sin redibujar, la banda se recolorea aquí. */
    app.addEventListener('click', (ev) => {
      if (!ev.target.closest('[data-region="status-picker"]')) return;
      setTimeout(() => {
        if (!app.isConnected) return;
        app.querySelectorAll('[data-region="hero"]').forEach((hero, i) => {
          const band = hero.querySelector(':scope > .obi-headband');
          if (!band) return;
          const item = heroItem(hero, app);
          const before = sig(band);
          const from = getComputedStyle(band).backgroundColor;
          fillHeadband(band, item);
          if (sig(band) !== before) { mem.set(mk + '|hero|' + i + '|' + item.id, { sig: sig(band), bg: getComputedStyle(band).backgroundColor }); sweep(band, from, app); }
        });
      }, 0);
    });
  }

  /* ---------------- dirección ---------------- */
  NJ.registerDirection({
    id: 'obi', name: 'Obi', track: 'free',
    tagline: 'Cada serie con su faja: la banda de papel de las novelas ligeras y los Blu-ray japoneses lleva el estado, el episodio y la frase del momento.',
    summary: 'Alternativa preferida por impeccable. Tinta y papel de tienda para la herramienta; color saturado solo en las fajas, que salen del color de portada de cada serie. Las listas pueden verse como lomos alineados en un estante.',
    fonts: ['https://fonts.googleapis.com/css2?family=Dela+Gothic+One&family=M+PLUS+1:wght@400..800&family=M+PLUS+1+Code:wght@400..600&display=swap'],
    palettes: [
      { id: 'tienda', name: 'Tienda', swatch: '#c4122f' },
      { id: 'nocturna', name: 'Estantería nocturna', swatch: '#ffa23a' },
      { id: 'limitada', name: 'Edición limitada', swatch: '#d6b46c' },
      { id: 'mono', name: 'Monocromo con faja', swatch: '#6b6b6b' },
    ],
    defaultCard: 'obi',
    notes: {
      thesis: 'La app como estante de tienda a la noche: cada serie tiene su faja obi, la banda de papel con frase gancho que envuelve la parte baja de la portada. La faja es el lenguaje de estado: color de la serie mientras la sigues o la tienes pendiente, verde al completarla, apagada en pausa, neutra si la dejas. Todo lo demás es una herramienta limpia en tinta y papel. Modo por defecto: oscuro, porque la escena principal es un monitor grande de noche; el claro es papel de tienda y el OLED es negro puro para el sofá.',
      signature: 'La faja inferior de la tarjeta, rotulada en Dela Gothic One con compresión horizontal al 86 % (長体, como se rotulan las fajas reales): estado en etiqueta invertida y la frase del momento (Ep. 9 en 6 d 7 h, quedan 18 min, Nuevo · Ep. 9). El color sale de coverImage.color de AniList y el texto se elige por luminancia, con el fondo ajustado hasta 5:1 si hace falta. Las listas pueden mostrarse como lomos con título vertical y número grande; la novedad (episodio nuevo o estreno) es la única que cruza la esquina en diagonal.',
      motion: 'Un momento orquestado: al abrir una ficha, la faja de la tarjeta se estira y se convierte en la banda de cabecera (transición de contenedor compartido, 420 ms, salida exponencial). Apoyos: en hover o foco la faja se levanta 3 px y muestra una segunda línea (vistos y puntaje); al marcar visto o cambiar estado, el color nuevo entra con un barrido de izquierda a derecha en 520 ms y la frase se revela detrás. Los lomos salen 8 px del estante al pasar. Calmo: sin desplazamientos ni transición compartida, el color cambia directo. Rendimiento: sin barrido, sin transición compartida y sin sombras.',
      android: 'Barra inferior, hojas y diálogos de Material 3 sin cambios de estructura; el tema entra por roles de color, tipo y forma (radios chicos, de papel). La faja mide 28 dp y cada objetivo táctil 48 dp. Sin hover, la segunda línea aparece con pulsación larga y con el foco del D-pad en tablet o TV. El selector de estado es una hoja inferior con fajas de color a lo ancho. Los lomos rinden en la Biblioteca de tablet horizontal: caben más de veinte por fila.',
      risks: 'La faja tapa el cuarto inferior del póster, donde a veces van logo o créditos: baja a 24 px en tamaño S y se levanta al pasar. Dela Gothic One es muy pesada y por debajo de 12 px se empasta, así que nunca baja de ahí. Un color de serie de luminancia media queda cerca de 4.6:1 con ambos textos y el ajuste a 5:1 puede alejarlo del color real de la portada. Sin color de AniList se usa el del estado. Compose no tiene escritura vertical nativa para los lomos. La banda de cabecera suma alto a la cabecera de cada pantalla.',
      compose: 'Faja: Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp).graphicsLayer { translationY = lift.value }.drawWithContent { drawRect(colorNuevo); drawRect(colorAnterior, topLeft = Offset(size.width * barrido.value, 0f), size = Size(size.width * (1f - barrido.value), size.height)); drawContent() }) con un Animatable para el barrido y animateFloatAsState para lift. Texto: Color.luminance() contra tinta y papel. 長体 con TextStyle(textGeometricTransform = TextGeometricTransform(scaleX = 0.86f)). Transición: SharedTransitionLayout alrededor del NavHost y Modifier.sharedBounds(rememberSharedContentState("obi-$id"), animatedVisibilityScope, resizeMode = RemeasureToBounds) en la faja de la tarjeta y en la banda de cabecera de Detalles. Lomos: LazyRow con key y un Layout propio que apila los glifos CJK y rota las corridas latinas. Modo rendimiento: un CompositionLocal LocalObiMotion apaga sharedBounds (queda un fundido), el barrido salta al color final y las sombras pasan a borde de 1 dp.',
    },
    decorate,
  });
})();
