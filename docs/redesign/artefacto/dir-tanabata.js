/* Dirección Tanabata · noche de deseos (agente D1). Ver CONTRATO.md.
   Tarjetas: tanzaku, tanzaku-sello, tanzaku-vertical, tanzaku-hilo. La física del balanceo vive en decorate(). */
(function () {
  const NJ = window.NJ;
  if (!NJ) return;
  const h = NJ.h;
  const esc = h.esc;

  /* ---------------- textos compartidos ---------------- */
  const stName = (item) => (item.status ? h.status(item.status).name : 'Sin lista');
  const epsText = (item) => {
    if (!item.eps) return '';
    if (item.eps === 1) return item.format || 'Película';
    if (!item.status || item.status === 'planning') return `${item.eps} ep.`;
    return `${item.progress || 0}/${item.eps}`;
  };
  const stLine = (item) => [stName(item), epsText(item)].filter(Boolean).join(' · ');
  const pct = (item) => (item.eps ? Math.max(0, Math.min(1, (item.progress || 0) / item.eps)) : 0);

  /* Línea de deseo: corta, en voz de mano. Solo hechos de los datos. */
  function wishLine(item) {
    if (item.resume) return `Seguir en el ep. ${item.resume.ep}`;
    if (item.next) return `Ep. ${item.next.ep} en ${item.next.in}`;
    if (item.status === 'planning') return item.eps > 1 ? `Empezar · ${item.eps} ep.` : 'Verla una noche';
    if (item.status === 'paused') return `Volver al ep. ${(item.progress || 0) + 1}`;
    if (item.status === 'completed') return 'Cumplido';
    if (item.status === 'watching') return `Ver el ep. ${(item.progress || 0) + 1}`;
    return '';
  }
  function continueSub(item) {
    return item.resume ? `Ep. ${item.resume.ep} · quedan ${item.resume.left}` : `Ep. ${(item.progress || 0) + 1}`;
  }

  function attrsFor(id, item, o, extra) {
    return `class="nj-card tz${extra || ''}" data-style="${id}" data-context="${esc(o.context)}" data-size="${esc(o.size)}" data-status="${esc(item.status || '')}" data-id="${esc(item.id)}"`;
  }
  const thread = (hero) => `<span class="tz-thread" aria-hidden="true"></span>${hero ? '<span class="tz-stub" aria-hidden="true"></span>' : ''}`;
  const inkStyle = (p) => (p > 0 ? ` style="--p:${p.toFixed(3)}"` : '');
  const inkCls = (p) => (p > 0 ? ' has-ink' : '');
  const score = (item, s) => (s.score && item.score ? `<span class="tz-score">${h.icon('star', { fill: true, size: 14 })}${esc(item.score)}</span>` : '');

  /* Contextos compartidos por todas las variantes */
  function renderCompact(id, item, o) {
    const s = o.show || {};
    const sub = `${item.format || ''}${item.year ? ' · ' + item.year : ''}`;
    return `<button type="button" ${attrsFor(id, item, o)}><span class="tz-thumb">${h.poster(item, { alt: '' })}</span><span class="tz-text"><span class="tz-title">${esc(item.short || item.title)}</span><span class="tz-sub">${esc(sub)}${item.eps > 1 && item.status && item.status !== 'planning' ? ' · ' + esc(epsText(item)) : ''}</span></span>${s.status !== false && item.status ? `<span class="tz-tag" data-status="${esc(item.status)}">${esc(stName(item))}</span>` : ''}</button>`;
  }
  function renderContinue(id, item, o, variant) {
    const p = item.resume ? item.resume.pct : pct(item);
    const label = `Continuar ${item.short || item.title}, ${continueSub(item)}`;
    const title = variant === 'vertical'
      ? `<span class="tz-vert"><span class="tz-vtitle">${esc(item.short || item.title)}</span></span><span class="tz-text"><span class="tz-title tz-ttl-alt">${esc(item.short || item.title)}</span><span class="tz-sub">${esc(continueSub(item))}</span><span class="tz-wish">${esc(stName(item))}</span></span>`
      : `<span class="tz-text"><span class="tz-title">${esc(item.short || item.title)}</span><span class="tz-sub">${esc(continueSub(item))}</span><span class="tz-wish">${esc(stName(item))}</span></span>`;
    const ep = item.resume ? item.resume.ep : (item.progress || 0) + 1;
    const play = `<button type="button" class="tz-play" data-stop data-go="reproductor" aria-label="${esc(`Reproducir ${item.short || item.title}, ep. ${ep}`)}">${h.icon('play_arrow', { fill: true })}</button>`;
    return `<article ${attrsFor(id, item, o, ' tz-hangs')} tabindex="0" aria-label="${esc(label)}"><span class="tz-hang">${thread()}<span class="tz-paper tz-ink${inkCls(p)}"${inkStyle(p)}><span class="tz-thumb">${h.poster(item, { alt: '' })}</span>${title}${play}</span></span></article>`;
  }

  /* ---------------- tanzaku: tira alta colgante ---------------- */
  NJ.registerCard({
    id: 'tanzaku', name: 'Tanzaku', dir: 'tanabata',
    desc: 'Tira de washi alta que cuelga de un hilo con nudo. El color del papel es el estado; la tinta baja por la tira con el progreso. Se balancea al pasar, tocar o desplazar.',
    render(item, o) {
      if (o.context === 'compact') return renderCompact('tanzaku', item, o);
      if (o.context === 'continue') return renderContinue('tanzaku', item, o);
      const s = o.show || {};
      const hero = o.context === 'hero';
      const hangs = o.context !== 'grid';
      const p = s.progress ? pct(item) : 0;
      const wish = (o.context === 'wish' || hero || (s.next && item.next)) ? wishLine(item) : '';
      return `<button type="button" ${attrsFor('tanzaku', item, o, hangs ? ' tz-hangs' : '')}><span class="tz-hang">${hangs ? thread(hero) : ''}<span class="tz-paper"><span class="tz-art">${h.poster(item, { alt: '' })}${score(item, s)}</span><span class="tz-text tz-ink${inkCls(p)}"${inkStyle(p)}><span class="tz-title">${esc(item.short || item.title)}</span><span class="tz-sub">${esc(stLine(item))}</span>${wish ? `<span class="tz-wish">${esc(wish)}</span>` : ''}</span></span></span></button>`;
    },
  });

  /* ---------------- tanzaku-sello: póster limpio con tira corta ---------------- */
  NJ.registerCard({
    id: 'tanzaku-sello', name: 'Tanzaku sello', dir: 'tanabata',
    desc: 'Póster limpio; del borde superior cuelga una tira corta con el estado y el episodio. Solo la tira se balancea, el póster queda quieto.',
    render(item, o) {
      if (o.context === 'compact') return renderCompact('tanzaku-sello', item, o);
      if (o.context === 'continue') return renderContinue('tanzaku-sello', item, o);
      const s = o.show || {};
      const p = s.progress ? pct(item) : 0;
      const ep = item.next && (s.next || o.context === 'wish') ? esc(`Ep. ${item.next.ep}`) : item.eps === 1 ? `${h.icon('movie')}<span class="tz-sr">${esc(item.format || 'Película')}</span>` : esc(item.status === 'planning' || !item.status ? `${item.eps} ep` : epsText(item));
      const sub = s.next && item.next ? `Ep. ${item.next.ep} en ${item.next.in}` : o.context === 'wish' || o.context === 'hero' ? wishLine(item) : `${item.format || ''} · ${item.year || ''}`;
      return `<button type="button" ${attrsFor('tanzaku-sello', item, o)}><span class="tz-art">${h.poster(item, { alt: '' })}${score(item, s)}</span><span class="tz-hang tz-strip"><span class="tz-thread" aria-hidden="true"></span><span class="tz-paper tz-ink${inkCls(p)}"${inkStyle(p)}><span class="tz-vlabel">${esc(stName(item))}</span><span class="tz-ep">${ep}</span></span></span><span class="tz-title">${esc(item.short || item.title)}</span><span class="tz-sub${o.context === 'wish' || o.context === 'hero' ? ' is-wish' : ''}">${esc(sub)}</span></button>`;
    },
  });

  /* ---------------- tanzaku-vertical: tira estrecha con título vertical ---------------- */
  NJ.registerCard({
    id: 'tanzaku-vertical', name: 'Tanzaku vertical', dir: 'tanabata',
    desc: 'Tira estrecha con el título en escritura vertical, pensada para filas de deseos. Densa: caben el doble por fila.',
    render(item, o) {
      if (o.context === 'compact') return renderCompact('tanzaku-vertical', item, o);
      if (o.context === 'continue') return renderContinue('tanzaku-vertical', item, o, 'vertical');
      const s = o.show || {};
      const hero = o.context === 'hero';
      const hangs = o.context !== 'grid';
      const p = s.progress || o.context === 'wish' ? pct(item) : 0;
      const second = o.context === 'wish' || hero || (s.next && item.next) ? wishLine(item) || stLine(item) : stLine(item);
      const label = `${item.short || item.title}. ${stLine(item)}${second !== stLine(item) ? '. ' + second : ''}`;
      return `<button type="button" ${attrsFor('tanzaku-vertical', item, o, hangs ? ' tz-hangs' : '')} aria-label="${esc(label)}"><span class="tz-hang">${hangs ? thread(hero) : ''}<span class="tz-paper tz-ink${inkCls(p)}"${inkStyle(p)}><span class="tz-art">${h.poster(item, { alt: '' })}</span><span class="tz-vert" aria-hidden="true"><span class="tz-vtitle">${esc(item.short || item.title)}</span><span class="tz-vsub${second !== stLine(item) ? ' is-wish' : ''}">${esc(second)}</span></span>${second !== stLine(item) ? `<span class="tz-foot" aria-hidden="true">${esc(stName(item))}</span>` : ''}</span></span></button>`;
    },
  });

  /* ---------------- tanzaku-hilo: póster colgado de un nudo de papel ---------------- */
  NJ.registerCard({
    id: 'tanzaku-hilo', name: 'Tanzaku hilo', dir: 'tanabata',
    desc: 'La más liviana: el póster cuelga del hilo con un nudo de papel del color del estado; el progreso es una línea de tinta. Para rejillas grandes y equipos modestos.',
    render(item, o) {
      if (o.context === 'compact') return renderCompact('tanzaku-hilo', item, o);
      if (o.context === 'continue') return renderContinue('tanzaku-hilo', item, o);
      const s = o.show || {};
      const hero = o.context === 'hero';
      const hangs = o.context !== 'grid';
      const p = s.progress ? pct(item) : 0;
      const sub = s.next && item.next ? `Ep. ${item.next.ep} en ${item.next.in}` : o.context === 'wish' || hero ? wishLine(item) || stLine(item) : stLine(item);
      return `<button type="button" ${attrsFor('tanzaku-hilo', item, o, hangs ? ' tz-hangs' : '')}><span class="tz-hang">${hangs ? thread(hero) : ''}<span class="tz-musubi" aria-hidden="true"></span><span class="tz-art">${h.poster(item, { alt: '' })}${score(item, s)}</span></span>${p > 0 ? `<span class="tz-line" aria-hidden="true" style="--p:${p.toFixed(3)}"></span>` : ''}<span class="tz-title">${esc(item.short || item.title)}</span><span class="tz-sub${o.context === 'wish' || hero ? ' is-wish' : ''}">${esc(o.context === 'wish' || hero || (s.next && item.next) ? sub : stLine(item))}</span>${(o.context === 'wish' || hero || (s.next && item.next)) ? `<span class="tz-sub">${esc(stLine(item))}</span>` : ''}</button>`;
    },
  });

  /* ---------------- física del balanceo ---------------- */
  const live = []; // { app, io } para desconectar observadores de marcos ya reemplazados
  const lastScreen = new WeakMap(); // host -> pantalla anterior, para no repetir la cascada en cada re-render
  const reduced = () => { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; } };

  function prune() {
    for (let i = live.length - 1; i >= 0; i--) {
      if (!live[i].app.isConnected) { live[i].io && live[i].io.disconnect(); live.splice(i, 1); }
    }
  }

  function swingEngine(app) {
    const MAX = 0.16; // rad, unos 9°
    const st = new Map(); // .tz-hang -> { a, v, k, c }
    const visible = new Set();
    let raf = 0; let last = 0;
    const motionOn = () => app.isConnected && app.getAttribute('data-motion') === 'full' && !reduced();

    function body(el) {
      let b = st.get(el);
      if (!b) {
        const heavy = el.closest('[data-style="tanzaku-hilo"]') ? 1.8 : el.classList.contains('tz-strip') ? 0.7 : 1;
        const k = 42 / heavy; // rigidez del resorte
        b = { a: 0, v: 0, k, c: 2 * 0.16 * Math.sqrt(k) }; // amortiguado bajo: varias oscilaciones cortas
        st.set(el, b);
      }
      return b;
    }
    function kick(el, dv) {
      if (!motionOn() || !el) return;
      const b = body(el);
      b.v = Math.max(-3.2, Math.min(3.2, b.v + dv));
      b.on = true;
      if (!raf) { last = performance.now(); raf = requestAnimationFrame(tick); }
    }
    function tick(now) {
      raf = 0;
      if (!app.isConnected) return;
      if (document.hidden || !motionOn()) { st.forEach((b, el) => { b.a = 0; b.v = 0; b.on = false; el.style.rotate = ''; }); return; }
      const dt = Math.min(1 / 30, Math.max(0.001, (now - last) / 1000)); last = now;
      let any = false;
      st.forEach((b, el) => {
        if (!b.on) return;
        const acc = -b.k * b.a - b.c * b.v;
        b.v += acc * dt; b.a += b.v * dt;
        if (b.a > MAX) { b.a = MAX; b.v *= -0.4; } else if (b.a < -MAX) { b.a = -MAX; b.v *= -0.4; }
        if (Math.abs(b.a) < 0.0006 && Math.abs(b.v) < 0.004) { b.a = 0; b.v = 0; b.on = false; el.style.rotate = ''; return; }
        el.style.rotate = (b.a * 57.2958).toFixed(2) + 'deg';
        any = true;
      });
      if (any) raf = requestAnimationFrame(tick);
    }

    const hangOf = (t) => { const card = t && t.closest && t.closest('.nj-card.tz'); return card && app.contains(card) ? card.querySelector('.tz-hang') : null; };

    /* Pasar el puntero: empuja desde el lado por el que entra. */
    app.addEventListener('pointerover', (ev) => {
      if (ev.pointerType === 'touch') return;
      const card = ev.target.closest && ev.target.closest('.nj-card.tz');
      if (!card || (ev.relatedTarget && card.contains(ev.relatedTarget))) return;
      const el = card.querySelector('.tz-hang'); if (!el) return;
      const r = card.getBoundingClientRect();
      const mx = ev.movementX || 0;
      const dv = mx ? -mx * 0.06 : (ev.clientX < r.left + r.width / 2 ? -0.9 : 0.9);
      kick(el, Math.max(-1.5, Math.min(1.5, dv)));
    });
    /* Tocar: golpe más fuerte hacia el lado contrario del dedo. */
    app.addEventListener('pointerdown', (ev) => {
      const el = hangOf(ev.target); if (!el) return;
      const r = el.getBoundingClientRect();
      kick(el, ev.clientX < r.left + r.width / 2 ? -1.7 : 1.7);
    });
    /* Foco con teclado o D-pad: un empujón leve, para que en la TV se vea dónde está el foco. */
    app.addEventListener('focusin', (ev) => {
      const el = hangOf(ev.target); if (!el) return;
      if (!ev.target.matches(':focus-visible')) return;
      kick(el, 0.7);
    });
    /* Scroll: horizontal arrastra la tira por inercia; vertical las agita como brisa, según la velocidad. */
    const prev = new WeakMap();
    app.addEventListener('scroll', (ev) => {
      const sc = ev.target; if (!sc || sc.nodeType !== 1 || !motionOn()) return;
      const now = performance.now();
      const p = prev.get(sc) || { x: sc.scrollLeft, y: sc.scrollTop, t: now - 16 };
      const dt = Math.max(8, now - p.t);
      const vx = (sc.scrollLeft - p.x) / dt; const vy = (sc.scrollTop - p.y) / dt; // px/ms
      prev.set(sc, { x: sc.scrollLeft, y: sc.scrollTop, t: now });
      if (Math.abs(vx) < 0.05 && Math.abs(vy) < 0.15) return;
      let i = 0;
      sc.querySelectorAll('.tz-hang').forEach((el) => {
        if (!visible.has(el)) return;
        i += 1;
        const dv = Math.abs(vx) >= 0.05 ? -vx * 0.55 : (i % 2 ? 1 : -1) * Math.min(1.2, Math.abs(vy) * 0.22);
        kick(el, Math.max(-1.3, Math.min(1.3, dv)));
      });
    }, { capture: true, passive: true });

    /* Solo se mueven las que están en pantalla. */
    let io = null;
    if ('IntersectionObserver' in window) {
      io = new IntersectionObserver((entries) => entries.forEach((e) => { if (e.isIntersecting) visible.add(e.target); else { visible.delete(e.target); const b = st.get(e.target); if (b) { b.a = 0; b.v = 0; b.on = false; e.target.style.rotate = ''; } } }), { threshold: 0.01 });
      app.querySelectorAll('.tz-hang').forEach((el) => io.observe(el));
    } else app.querySelectorAll('.tz-hang').forEach((el) => visible.add(el));
    return io;
  }

  /* Varas: cualquier contenedor horizontal con tarjetas colgantes recibe la vara de bambú. */
  function markRods(app) {
    const seen = new Set();
    app.querySelectorAll('.nj-card.tz-hangs').forEach((card) => {
      let par = card.parentElement;
      for (let depth = 0; par && par !== app && depth < 3; depth++, par = par.parentElement) {
        if (seen.has(par)) return;
        const cs = getComputedStyle(par);
        const horizontal = (cs.display.includes('flex') && cs.flexDirection === 'row' && cs.flexWrap === 'nowrap') || par.classList.contains('nj-row');
        if (horizontal) { seen.add(par); par.setAttribute('data-tz-rod', ''); return; }
      }
    });
  }

  /* Esta noche: distingue episodio recién salido, retomar y próximo, leyendo la etiqueta del hueco o los datos. */
  function markKinds(app) {
    app.querySelectorAll('[data-region="tonight"] .nj-card.tz').forEach((card) => {
      const slot = card.parentElement;
      const label = slot && slot.querySelectorAll('.nj-card').length === 1 ? Array.from(slot.children).filter((c) => c !== card).map((c) => c.textContent).join(' ') : '';
      const item = NJ.data.byId[card.getAttribute('data-id')] || {};
      let kind = item.resume ? 'resume' : 'new';
      if (/new_releases|sali[oó]/i.test(label)) kind = 'new';
      else if (/schedule|sale (a las|en)/i.test(label)) kind = 'soon';
      else if (/play_circle|quedaste|retom/i.test(label)) kind = 'resume';
      card.setAttribute('data-tz-kind', kind);
    });
  }

  /* Agenda: el farol marca lo que se emite ahora. */
  function markNow(app) {
    app.querySelectorAll('[data-region="airing"]').forEach((reg) => {
      const items = Array.from(reg.querySelectorAll('[data-time]'));
      if (!items.length) return;
      let now = items.find((el) => el.getAttribute('data-now') === 'true' || (el.hasAttribute('data-now') && el.getAttribute('data-now') !== 'false')) || items.find((el) => el.matches('[aria-current], .is-now'));
      if (!now) {
        const clock = (app.querySelector('.nj-statusbar span') || {}).textContent || '21:04';
        const toMin = (t) => { const m = /(\d{1,2}):(\d{2})/.exec(t || ''); return m ? +m[1] * 60 + +m[2] : null; };
        const cur = toMin(clock) ?? 21 * 60 + 4;
        items.forEach((el) => { const t = toMin(el.getAttribute('data-time')); if (t != null && t <= cur) now = el; });
      }
      if (now) now.setAttribute('data-tz-now', '');
    });
  }

  /* Episodios: el siguiente por ver se marca con el farol. */
  function markNextEp(app) {
    app.querySelectorAll('[data-region="episodes"]').forEach((reg) => {
      const eps = Array.from(reg.querySelectorAll('.nj-ep'));
      const next = eps.find((el) => el.getAttribute('data-next') === 'true') || eps.find((el) => el.getAttribute('data-aired') !== 'false' && el.getAttribute('data-watched') !== 'true');
      if (next) next.setAttribute('data-tz-next', '');
    });
  }

  NJ.registerDirection({
    id: 'tanabata', name: 'Tanabata', track: 'free',
    tagline: 'Lo que quieres ver cuelga como deseos de un bambú; el resto es una herramienta nocturna limpia.',
    summary: 'Suelo índigo de noche, papel washi en los colores tanzaku como colores de estado reales, ámbar de farol solo para el foco y lo que pasa ahora, hilos de bambú como rieles.',
    fonts: [
      'https://fonts.googleapis.com/css2?family=M+PLUS+Rounded+1c:wght@400;500;700;800&display=swap',
      'https://fonts.googleapis.com/css2?family=Yuji+Syuku&display=swap',
    ],
    palettes: [
      { id: 'noche', name: 'Noche índigo', swatch: '#283889' },
      { id: 'festival', name: 'Festival', swatch: '#f27a5e' },
      { id: 'alba', name: 'Alba', swatch: '#e6dcc4' },
      { id: 'bambu', name: 'Bambú', swatch: '#6f9a5a' },
    ],
    defaultCard: 'tanzaku',
    notes: {
      thesis: 'Una app para ver de noche en un monitor grande o desde el sofá. Lo que el usuario quiere ver (Esta noche, Pendientes) cuelga como tanzaku de una vara de bambú; todo lo demás (navegación, ajustes, fuentes, reproductor) es Material de herramienta, con suelo índigo, tipo redondeado y hairlines de bambú. Rechaza el clon de streaming: nada de suelo gris con filas de pósters iguales y un violeta.',
      signature: 'Tarjetas tanzaku colgadas de una vara con hilo y nudo que se balancean con física de resorte amortiguado al pasar el puntero, al tocarlas, al recibir foco de D-pad y al desplazar la fila según la velocidad. El color del papel es el estado (Viendo bermellón, Pendiente azul, Completado verde, En pausa amarillo, Abandonado violeta, Reviendo rosa) y el progreso es tinta que baja por la tira. En Esta noche cada hueco cuelga de la vara con su deseo escrito a pincel debajo.',
      motion: 'Un solo momento orquestado: al entrar en Inicio los tanzaku de Esta noche se cuelgan en cascada (caen 26 px, oscilan y se asientan en 900 ms, 60 ms entre tiras, tope de 8). Después solo hay física reactiva, nunca bucles. Calmo y movimiento reducido: sin balanceo ni cascada, fundido de 140 ms. Rendimiento: sin física, sin sombras, sin cascada.',
      android: 'Navegación inferior Material 3 de cinco destinos, objetivos de 48 dp (chips con área táctil extendida), hojas inferiores con asa de bambú. El balanceo responde al toque y a la velocidad del fling de la fila; en tablet y TV el foco de D-pad empuja la tira y la rodea con el anillo ámbar. En el moto g51 el modo rendimiento queda sugerido por defecto.',
      risks: 'El bermellón de Viendo convive con el rojo de error: el error siempre lleva icono y texto, y su tono es rosado. Esta noche suele ser casi todo Viendo, así que el color solo no separa nada: lo recién salido lleva sello y nudo ámbar, lo próximo un sello de papel y lo que se retoma muestra su tinta. La voz de deseo es pincel (Yuji Syuku): se lee bien en líneas cortas desde 13 px y nunca se usa en títulos, controles ni datos. El título vertical en letras latinas se lee girando la cabeza: la tira vertical repite el título en el nombre accesible y se reserva para filas de deseos. La física en filas largas cuesta batería: solo corre con tiras visibles y se detiene al reposar. El ámbar pierde fuerza si se usa para algo más que foco y ahora.',
      compose: 'Cada tanzaku guarda un Animatable(0f) para el ángulo. Un impulso (hover con Modifier.onPointerEvent(Enter), toque en pointerInput, foco con onFocusChanged, o la velocidad de la fila leída de LazyListState con snapshotFlow) lanza angle.animateTo(0f, spring(dampingRatio = 0.18f, stiffness = Spring.StiffnessLow), initialVelocity = v), y el nodo aplica Modifier.graphicsLayer { rotationZ = angle.value; transformOrigin = TransformOrigin(0.5f, 0f) }, sin recomponer. El papel se pinta con Modifier.drawBehind: drawRect del color de estado, drawRect de la tinta con altura size.height * progreso; papel liso por ahora, y la textura washi real llegará como asset raster (drawImage con alpha baja) cuando haya generación de imágenes, nunca como ruido o vetas imitadas. La vara es un Canvas con drawRoundRect y nodos cada 176 dp. La cascada de Inicio usa LaunchedEffect con delay(i * 60) y Animatable de translationY y rotationZ, una vez por sesión con rememberSaveable. Un CompositionLocal LocalMotionProfile decide: Full crea los Animatable; Calm usa fadeIn(tween(140)); Perf no crea Animatable y deja shadowElevation = 0.',
    },
    decorate(app, ctx) {
      prune();
      const host = app.parentElement && app.parentElement.parentElement;
      const screen = app.getAttribute('data-screen') || (ctx && ctx.screen) || '';
      /* Cascada solo al entrar; un re-render de la misma pantalla no la repite. */
      if (host) { if (lastScreen.get(host) === screen) app.setAttribute('data-tz-settled', ''); lastScreen.set(host, screen); }
      app.querySelectorAll('[data-region="tonight"] .nj-card.tz').forEach((c, i) => c.style.setProperty('--tz-i', String(Math.min(i, 8))));
      markRods(app);
      markKinds(app);
      /* Tarjetas <article> con botones internos: Enter y Espacio abren la ficha como un clic. */
      app.addEventListener('keydown', (ev) => {
        const card = ev.target;
        if (!card.matches || !card.matches('article.nj-card.tz') || (ev.key !== 'Enter' && ev.key !== ' ')) return;
        ev.preventDefault(); card.click();
      });
      markNow(app);
      markNextEp(app);
      const motion = app.getAttribute('data-motion') || (ctx && ctx.motion) || 'full';
      if (motion !== 'full' || reduced()) return;
      const io = swingEngine(app);
      live.push({ app, io });
    },
  });
})();
