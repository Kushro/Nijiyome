/* Matriz de paridad: cada superficie actual y su lugar en el rediseño. Datos en paridad.json. */
(function () {
  'use strict';
  const NJ = window.NJ;
  if (!NJ || !NJ.registerLab) return;
  const h = NJ.h;
  const esc = h.esc;

  const CHANGES = [
    { id: 'igual', label: 'Igual', desc: 'Queda donde está y como está' },
    { id: 'reubicado', label: 'Reubicado', desc: 'Misma función, otro lugar' },
    { id: 'mejorado', label: 'Mejorado', desc: 'Mismo lugar, mejor control o más visible' },
    { id: 'fusionado', label: 'Fusionado', desc: 'Se une con otra superficie' },
    { id: 'nuevo-acceso', label: 'Nuevo acceso', desc: 'Existía en código sin UI y ahora tiene acceso' },
    { id: 'pendiente', label: 'Pendiente', desc: 'Sin hogar claro: lo decide el dueño' },
  ];
  const SCREENS = {
    inicio: 'Inicio', ficha: 'Ficha', 'fuentes-episodio': 'Fuentes del episodio', reproductor: 'Reproductor',
    explorar: 'Explorar', calendario: 'Calendario', biblioteca: 'Biblioteca', ajustes: 'Ajustes', perfil: 'Perfil',
    transferencias: 'Transferencias', fuentes: 'Fuentes', link: 'Link', manga: 'Manga y novelas', lector: 'Lector',
    bienvenida: 'Bienvenida',
  };
  const PLATFORMS = [{ id: 'desktop', label: 'Escritorio' }, { id: 'android', label: 'Android' }];
  const changeLabel = (id) => (CHANGES.find((c) => c.id === id) || { label: id }).label;
  const fnCount = (list) => list.reduce((n, e) => n + (e.functions || []).length, 0);

  function chipGroup(key, label, options, value) {
    const all = [{ id: '', label: 'Todas' }].concat(options);
    return `<div style="display:grid;gap:6px"><span class="x-note" id="pf-${key}">${esc(label)}</span>
      <div role="group" aria-labelledby="pf-${key}" style="display:flex;flex-wrap:wrap;gap:6px">${all.map((o) => {
        const on = o.id === value;
        return `<button type="button" class="x-btn${on ? ' is-primary' : ''}" data-pf="${key}" data-value="${esc(o.id)}" aria-pressed="${on}" style="padding:5px 10px;font-size:13px">${esc(o.label)}${o.count != null ? ` <span style="opacity:.75;font-weight:500">${o.count}</span>` : ''}</button>`;
      }).join('')}</div></div>`;
  }

  function summary(items, notes) {
    const by = {};
    items.forEach((e) => { by[e.change] = (by[e.change] || 0) + 1; });
    const pend = items.concat(notes).filter((e) => e.change === 'pendiente');
    const sources = {};
    items.forEach((e) => { if (!e.kind) sources[e.source] = (sources[e.source] || 0) + 1; });
    return `<section class="x-panel"><h2>Resumen</h2>
      <dl class="x-kv">
        <dt>Superficies</dt><dd>${items.length} (${Object.entries(sources).sort().map(([s, n]) => `${esc(s.replace('inventario-', 'inventario '))}: ${n}`).join(', ')}, más ${items.filter((e) => e.kind === 'oculta').length} funciones ocultas en código)</dd>
        <dt>Funciones</dt><dd>${fnCount(items)}</dd>
        <dt>Notas transversales</dt><dd>${notes.length}</dd>
      </dl>
      <div style="display:flex;flex-wrap:wrap;gap:6px">${CHANGES.map((c) => `<span class="x-tag${c.id === 'pendiente' ? ' is-accent' : ''}" title="${esc(c.desc)}">${esc(c.label)}: ${by[c.id] || 0}</span>`).join('')}</div>
    </section>
    <section class="x-panel"><h2>${h.icon('flag', { size: 20 })} Riesgos para decidir (${pend.length})</h2>
      ${pend.length ? `<p class="x-note">Estas funciones no tienen un hogar claro en la nueva arquitectura. Ninguna se puede quitar: hay que elegir dónde viven.</p>
      <ul class="x-list" style="max-width:none">${pend.map((e) => `<li><b>${esc(e.id)}</b> ${esc(e.name)}: ${esc(e.risk || 'Sin riesgo descrito')} <button type="button" class="x-btn" data-pf-goid="${esc(e.id)}" style="padding:2px 8px;font-size:12px">Ver fila</button></li>`).join('')}</ul>`
        : '<p class="x-note">No hay funciones pendientes: todas tienen hogar.</p>'}
    </section>`;
  }

  function matches(e, f) {
    if (f.area && e.area !== f.area) return false;
    if (f.change && e.change !== f.change) return false;
    if (f.platform && !(e.platforms || []).includes(f.platform)) return false;
    if (f.q) {
      const hay = [e.id, e.name, e.area, e.pattern, e.risk, (e.newHome || {}).place].concat(e.functions || []).join(' ').toLowerCase();
      if (!f.q.toLowerCase().split(/\s+/).filter(Boolean).every((w) => hay.includes(w))) return false;
    }
    return true;
  }

  function row(e) {
    const nh = e.newHome || {};
    const fns = e.functions || [];
    const scr = SCREENS[nh.screen] ? nh.screen : '';
    return `<tr id="pf-row-${esc(e.id)}">
      <td style="white-space:nowrap"><b>${esc(e.id)}</b><div class="x-note">${esc(e.area)}</div></td>
      <td style="min-width:150px">${esc(e.name)}<div class="x-note">${(e.platforms || []).map((p) => p === 'desktop' ? 'Escritorio' : 'Android').join(' · ')}</div></td>
      <td style="min-width:180px">${fns.length ? `<details><summary style="cursor:pointer">${fns.length} ${fns.length === 1 ? 'función' : 'funciones'}</summary><ul class="x-list" style="margin-top:6px">${fns.map((f) => `<li>${esc(f)}</li>`).join('')}</ul></details>` : '<span class="x-note">Sin funciones propias</span>'}</td>
      <td style="min-width:200px">${scr ? `<button type="button" class="x-btn" data-pf-go="${esc(scr)}" style="padding:3px 8px;font-size:12px">${h.icon('open_in_new', { size: 16 })}${esc(SCREENS[scr])}</button>` : ''}<div style="margin-top:4px">${esc(nh.place || '')}</div></td>
      <td><span class="x-tag${e.change === 'pendiente' ? ' is-accent' : ''}">${esc(changeLabel(e.change))}</span></td>
      <td style="min-width:180px">${e.pattern ? esc(e.pattern) : '<span class="x-note">Sin cambio</span>'}${e.risk ? `<div class="x-note" style="margin-top:4px">${h.icon('warning', { size: 14 })} ${esc(e.risk)}</div>` : ''}</td>
    </tr>`;
  }

  function table(list) {
    if (!list.length) return '<div class="x-empty">Ninguna superficie coincide con estos filtros.</div>';
    return `<div class="x-scroll"><table class="x-table"><thead><tr><th scope="col">ID</th><th scope="col">Nombre</th><th scope="col">Funciones</th><th scope="col">Nuevo hogar</th><th scope="col">Cambio</th><th scope="col">Patrón y riesgo</th></tr></thead><tbody>${list.map(row).join('')}</tbody></table></div>`;
  }

  function render(el) {
    el.innerHTML = `<header style="display:grid;gap:8px"><h1>Paridad</h1><p class="x-lede">Cada superficie de la app actual y dónde vive en el rediseño. La regla: no se pierde ninguna función.</p></header><div class="x-empty" data-pf-body>Cargando la matriz…</div>`;
    fetch('paridad.json', { cache: 'no-cache' })
      .then((r) => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then((data) => { if (!Array.isArray(data)) throw new Error('formato inesperado'); draw(el, data); })
      .catch((err) => {
        const body = el.querySelector('[data-pf-body]');
        if (body) body.innerHTML = `${h.icon('error')} No se pudo cargar paridad.json (${esc(err.message)}). Sirve la carpeta con un servidor local, por ejemplo <code>python3 -m http.server</code>, y recarga.`;
      });
  }

  function draw(el, data) {
    const items = data.filter((e) => e.kind !== 'nota');
    const notes = data.filter((e) => e.kind === 'nota');
    const f = { area: '', change: '', platform: '', q: '' };
    const areas = [...new Set(items.map((e) => e.area))].sort((a, b) => a.localeCompare(b, 'es'));
    const count = (key, val) => items.filter((e) => (key === 'platform' ? (e.platforms || []).includes(val) : e[key] === val)).length;

    el.innerHTML = `<header style="display:grid;gap:8px"><h1>Paridad</h1><p class="x-lede">Cada superficie de la app actual y dónde vive en el rediseño. La regla: no se pierde ninguna función.</p></header>
      ${summary(items, notes)}
      <section class="x-panel" aria-label="Filtros">
        <label style="display:grid;gap:6px"><span class="x-note">Buscar</span>
          <input type="search" data-pf-q placeholder="ID, nombre, función o lugar" style="font:inherit;padding:8px 10px;border-radius:9px;border:1px solid var(--x-line);background:var(--x-panel-2);color:var(--x-text);width:100%;max-width:420px;box-sizing:border-box"></label>
        <div data-pf-chips></div>
      </section>
      <section style="display:grid;gap:10px;min-width:0"><p class="x-note" data-pf-count aria-live="polite"></p><div data-pf-table></div></section>
      ${notes.length ? `<section class="x-panel"><h2>Notas transversales</h2><p class="x-note">Secciones de los inventarios que no son superficies pero traen funciones o reglas.</p>${notes.map((n) => `<details id="pf-note-${esc(n.id)}"><summary style="cursor:pointer"><b>${esc(n.id)}</b> ${esc(n.name)}</summary><div style="display:grid;gap:6px;margin-top:6px">${(n.functions || []).length ? `<ul class="x-list">${n.functions.map((x) => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}${n.pattern ? `<p class="x-note">${esc(n.pattern)}</p>` : ''}${n.risk ? `<p class="x-note">${h.icon('warning', { size: 14 })} ${esc(n.risk)}</p>` : ''}</div></details>`).join('')}</section>` : ''}`;

    const chipsEl = el.querySelector('[data-pf-chips]');
    const tableEl = el.querySelector('[data-pf-table]');
    const countEl = el.querySelector('[data-pf-count]');
    const drawChips = () => {
      chipsEl.innerHTML = `<div style="display:grid;gap:12px">
        ${chipGroup('area', 'Área', areas.map((a) => ({ id: a, label: a, count: count('area', a) })), f.area)}
        ${chipGroup('change', 'Cambio', CHANGES.map((c) => ({ id: c.id, label: c.label, count: count('change', c.id) })), f.change)}
        ${chipGroup('platform', 'Plataforma', PLATFORMS.map((p) => ({ id: p.id, label: p.label, count: count('platform', p.id) })), f.platform)}</div>`;
    };
    const drawTable = () => {
      const list = items.filter((e) => matches(e, f));
      countEl.textContent = `Mostrando ${list.length} de ${items.length} superficies · ${fnCount(list)} funciones`;
      tableEl.innerHTML = table(list);
    };
    drawChips(); drawTable();

    el.querySelector('[data-pf-q]').addEventListener('input', (ev) => { f.q = ev.target.value.trim(); drawTable(); });
    el.addEventListener('click', (ev) => {
      const chip = ev.target.closest('[data-pf]');
      if (chip && el.contains(chip)) { f[chip.dataset.pf] = chip.dataset.value; drawChips(); drawTable(); return; }
      const go = ev.target.closest('[data-pf-go]');
      if (go && el.contains(go)) { NJ.go(go.dataset.pfGo); return; }
      const gid = ev.target.closest('[data-pf-goid]');
      if (gid && el.contains(gid)) {
        f.area = ''; f.change = ''; f.platform = ''; f.q = '';
        el.querySelector('[data-pf-q]').value = '';
        drawChips(); drawTable();
        const tr = document.getElementById('pf-row-' + gid.dataset.pfGoid) || document.getElementById('pf-note-' + gid.dataset.pfGoid);
        if (tr) { tr.scrollIntoView({ block: 'center' }); const d = tr.tagName === 'DETAILS' ? tr : tr.querySelector('details'); if (d) d.open = true; }
      }
    });
  }

  NJ.registerLab({ id: 'paridad', name: 'Paridad', order: 90, render });
})();
