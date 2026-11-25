/* Datos de muestra del explorador de rediseño. Títulos y pósters reales (capturas del perfil del dueño);
   nombres de grupos de release, fuentes y cifras de torrents son inventados y se marcan como ejemplo en la UI. */
(function () {
  const P = (slug) => `img/p-${slug}.webp`;
  const titles = [
    { id: 'laidoff', title: 'The Laid-Off Cheat-Granting Mage Enjoys a Second Lease on Life', short: 'Laid-Off Mage', romaji: 'Tsuihou Sareta Cheat Fuyo Majutsushi wa Kimama na Second Life wo Ouka suru', poster: P('laidoff'), backdrop: 'img/frame-laidoff.webp', year: 2026, season: 'Otoño 2026', format: 'TV', eps: 12, aired: 8, score: 7.4, status: 'watching', progress: 1, genres: ['Aventura', 'Comedia', 'Fantasía', 'Recuentos de la vida'], studio: 'Studio Elle', next: { ep: 9, in: '6 d 7 h' }, color: '#d9a441' },
    { id: 'magical', title: 'Magical★Explorer: Eroge no Yuujin Chara ni Tensei shita kedo, Game Chishiki Tsukatte Jiyuu ni Ikiru', short: 'Magical★Explorer', poster: P('magical-explorer'), year: 2026, format: 'TV', eps: 12, aired: 9, score: 6.9, status: 'watching', progress: 8, genres: ['Fantasía', 'Comedia', 'Ecchi'], next: { ep: 10, in: '2 d 4 h' }, color: '#6d8fe0' },
    { id: 'tempal', title: 'Tempal: Item no Chikara', short: 'Tempal', poster: P('tempal'), year: 2026, format: 'ONA', eps: 12, aired: 6, score: 7.1, status: 'watching', progress: 3, resume: { ep: 4, left: '18 min', pct: 0.24 }, genres: ['Acción', 'Aventura', 'Comedia', 'Fantasía'], next: { ep: 7, in: '1 d 2 h' }, color: '#c2412d' },
    { id: 'tsuihou', title: 'Tsuihou Sareta Tensei Juukishi wa Game Chishiki de Musou suru', short: 'Tsuihou Juukishi', poster: P('tsuihou-juukishi'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 6.4, status: 'watching', progress: 1, genres: ['Acción', 'Fantasía', 'Mecha'], color: '#8a6a3f' },
    { id: 'seitokai', title: 'Seitokai ni mo Ana wa Aru!', short: 'Seitokai ni mo Ana wa Aru!', poster: P('seitokai-ana'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 7.0, status: 'watching', progress: 6, genres: ['Comedia', 'Escolar'], color: '#7fa0c4' },
    { id: 'chitose', title: 'Chitose Is in the Ramune Bottle 2nd Cour', short: 'Chitose-kun 2', poster: P('chitose-kun'), year: 2026, format: 'TV', eps: 11, aired: 7, score: 7.8, status: 'watching', progress: 6, genres: ['Comedia', 'Romance'], next: { ep: 8, in: '3 d' }, color: '#e7a0b8' },
    { id: 'goblin', title: 'Tensei shitara Goblin datta kedo, Shitsumon Aru?', short: 'Goblin, ¿preguntas?', poster: P('tensei-goblin'), year: 2026, format: 'ONA', eps: 12, aired: 8, score: 6.4, status: 'planning', progress: 0, genres: ['Acción', 'Aventura', 'Comedia'], color: '#7f9f4c' },
    { id: 'dawang', title: 'Dawang Raoming 3', short: 'Dawang Raoming 3', poster: P('dawang-raoming-3'), year: 2026, format: 'ONA', eps: 16, aired: 9, score: 7.6, status: 'watching', progress: 7, genres: ['Acción', 'Comedia', 'Sobrenatural'], color: '#b6402c' },
    { id: 'shinja', title: 'Shinja Zero no Megami-sama to Hajimeru Isekai Kouryaku', short: 'Shinja Zero', poster: P('shinja-zero'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 6.7, status: 'paused', progress: 4, genres: ['Fantasía', 'Isekai'], color: '#9b7bd0' },
    { id: 'mouse', title: 'Mouse Cursor de Genjitsu wo Sousa suru', short: 'Mouse Cursor', poster: P('mouse-cursor'), year: 2026, format: 'TV Short', eps: 12, aired: 8, score: 6.2, status: 'planning', progress: 0, genres: ['Comedia'], color: '#d8c7a4' },
    { id: 'koori', title: 'Koori no Jouheki', short: 'Koori no Jouheki', poster: P('koori-no-jouheki'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 7.9, status: 'watching', progress: 7, genres: ['Drama', 'Romance', 'Escolar'], color: '#5e8fb8' },
    { id: 'mahoyo', title: 'Mahoutsukai no Yoru', short: 'Mahoyo', poster: P('mahoutsukai-no-yoru'), year: 2026, format: 'Película', eps: 1, aired: 1, score: 8.3, status: 'planning', progress: 0, genres: ['Drama', 'Fantasía', 'Misterio'], color: '#3b5fb8' },
    { id: 'edgerunners', title: 'Cyberpunk: Edgerunners 2', short: 'Edgerunners 2', poster: P('edgerunners-2'), year: 2026, format: 'ONA', eps: 10, aired: 0, score: null, status: 'planning', progress: 0, genres: ['Acción', 'Ciencia ficción'], color: '#e9d43c' },
    { id: 'kikansha', title: 'Kikansha no Mahou wa Tokubetsu desu 2nd Season', short: 'Kikansha no Mahou 2', poster: P('kikansha-no-mahou-2'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 7.2, status: 'completed', progress: 12, genres: ['Acción', 'Fantasía'], color: '#c4623b' },
    { id: 'choujun', title: 'Choujun! Choujou Senpai', short: 'Choujun! Senpai', poster: P('choujun-senpai'), year: 2026, format: 'TV Short', eps: 12, aired: 8, score: 6.8, status: 'dropped', progress: 2, genres: ['Comedia', 'Romance'], color: '#e89cb4' },
    { id: 'kanojo', title: 'Kanojo no Tomodachi', short: 'Kanojo no Tomodachi', poster: P('kanojo-no-tomodachi'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 6.9, status: 'planning', progress: 0, genres: ['Romance'], color: '#d8d2c8' },
    { id: 'horegusuri', title: 'Doumo, Suki na Hito ni Horegusuri wo Iraisareta Majo desu.', short: 'Horegusuri', poster: P('horegusuri'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 7.5, status: 'watching', progress: 5, genres: ['Fantasía', 'Romance'], color: '#7cae8a' },
    { id: 'psyren', title: 'PSYREN', short: 'PSYREN', poster: P('psyren'), year: 2026, format: 'TV', eps: 24, aired: 8, score: 7.3, status: 'watching', progress: 2, genres: ['Acción', 'Misterio', 'Sobrenatural'], color: '#3fd0c8' },
    { id: 'revo', title: 'Kashita Maryoku wa [Revo Barai] de Kyousei Choushuu', short: 'Revo Barai', poster: P('revo-barai'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 6.6, status: 'completed', progress: 12, genres: ['Acción', 'Fantasía'], color: '#5c7ab0' },
    { id: 'aonohako', title: 'Ao no Hako Season 2', short: 'Ao no Hako 2', poster: P('ao-no-hako-2'), year: 2026, format: 'TV', eps: 13, aired: 8, score: 8.4, status: 'watching', progress: 8, genres: ['Deportes', 'Romance'], next: { ep: 9, in: '5 h' }, color: '#e9a24b' },
    { id: 'mezametara', title: 'Mezametara Saikyou Soubi to Uchuusen-mochi datta node', short: 'Mezametara Saikyou', poster: P('mezametara-saikyou'), year: 2026, format: 'TV', eps: 12, aired: 8, score: 6.5, status: 'paused', progress: 3, genres: ['Ciencia ficción', 'Isekai'], color: '#6f7f9a' },
  ];
  // Episodios reales de la temporada (títulos de AniList vistos en la captura de la ficha).
  const laidoffEpisodes = [
    'Everyone Knows What It\'s Like When Nothing Goes Right, but What Do I Do When That Happens? Why, It\'s Obvious! I Run and Scream My Head Off!',
    'The Enhanced Clothes I Was Wearing Were Destroyed by a Devastating Blow, and a Traumatized Girl in Casual Clothes Hit Me with All Her Might.',
    'When I Went to Apologize With a Friend Who Was a Shut-in for Two Years, We Were Unexpectedly Served Apple Bunnies, So I Think She\'s Been Forgiven.',
    'There\'s Something I\'ve Learned from Ending Up Naked Twice… When You\'re Naked, You Get Bitten Twice.',
    'A Girl Who Wants You to Know She Really Can Cook Despite the Lack of Cooking Scenes Draws and Wreathes Her Sword in Flames.',
    'You\'re Persistent, Girl… You Haven\'t Changed at All, and You\'re Powerless to Do Anything Again… But Still, I Am Lily Flambert!',
    'I\'ve Never Been to That Bakery Before, so I\'ll Take This Opportunity to Stuff My Face with Melon Bread and Walk a Dog.',
    'That\'s Opium! Nobody\'s Going to Argue with You, and I\'m Sure You Feel Like Arguing Anyway.',
  ].map((t, i) => ({ n: i + 1, title: t, aired: i < 8, watched: i === 0, ago: ['hace 1 mes', 'hace 1 mes', 'hace 1 mes', 'hace 4 semanas', 'hace 3 semanas', 'hace 2 semanas', 'hace 2 semanas', 'hace 16 h'][i], dur: '24 min', filler: false }));
  for (let n = 9; n <= 12; n++) laidoffEpisodes.push({ n, title: null, aired: false, watched: false, ago: n === 9 ? 'en 6 d 7 h' : 'sin fecha', dur: '24 min' });

  // Fuentes del episodio: atributos reales del selector; grupos y tamaños inventados (ejemplo).
  const sources = [
    { id: 's1', ext: 'Toko', group: 'Kitsune Raws', kind: 'torrent', res: '1080p', codec: 'HEVC', bit: '10-bit', audio: 'Dual', subs: 'Multi', size: '261 MB', seeders: 244, leechers: 0, cached: 'TorBox', reliability: 0.87, recommended: true, langs: 'Subs varios · JA/EN' },
    { id: 's2', ext: 'Toko', group: 'Hanabi Subs', kind: 'torrent', res: '1080p', codec: 'AV1', bit: '8-bit', audio: 'Dual', subs: 'Multi', size: '250 MB', seeders: 61, leechers: 0, cached: 'TorBox', reliability: 0.80, langs: 'Subs FR · JA/EN', web: true },
    { id: 's3', ext: 'Toko', group: 'Yoru Fansub', kind: 'torrent', res: '1080p', codec: 'H.264', bit: '8-bit', audio: 'Dual', subs: 'Multi', size: '1,50 GB', seeders: 56, leechers: 0, cached: 'TorBox', reliability: 0.80, langs: 'Subs varios · JA/EN', web: true },
    { id: 's4', ext: 'Toko', group: 'Nebula', kind: 'web', res: '1080p', codec: 'H.264', bit: '8-bit', audio: 'JA', subs: 'ES', size: 'HLS', reliability: 0.92, langs: 'Sub ES', embed: false },
    { id: 's5', ext: 'Toko', group: 'Mirror B', kind: 'web', res: '720p', codec: 'H.264', bit: '8-bit', audio: 'JA', subs: 'ES', size: 'HLS', reliability: 0.64, langs: 'Sub ES', embed: true },
    { id: 's6', ext: 'Stremio · Torrentio', group: 'Akai Raws', kind: 'torrent', res: '2160p', codec: 'HEVC', bit: '10-bit', audio: 'JA', subs: 'EN', size: '1,9 GB', seeders: 12, leechers: 3, cached: null, reliability: 0.55, langs: 'Sub EN' },
  ];

  const airingToday = [
    { time: '00:00', id: 'magical', ep: 10 }, { time: '09:30', id: 'chitose', ep: 8 }, { time: '11:00', id: 'tempal', ep: 7 },
    { time: '17:30', id: 'koori', ep: 9 }, { time: '21:00', id: 'aonohako', ep: 9, now: true }, { time: '23:30', id: 'psyren', ep: 9 },
  ];

  const M = (slug) => `img/m-${slug}.webp`;
  const manga = [
    { id: 'm1', title: 'Solo Leveling', chapter: 'Cap. 179', total: 200, poster: M('solo-leveling'), progress: 0.9, kind: 'manhwa' },
    { id: 'm2', title: 'Martial Peak', chapter: 'Cap. 3497', total: 3570, poster: M('martial-peak'), progress: 0.98, kind: 'manhua' },
    { id: 'm3', title: 'Martial God Asura', chapter: 'Cap. 85', total: 6100, poster: M('martial-god-asura'), progress: 0.01, kind: 'manhua' },
    { id: 'm4', title: 'Way to Be the Evil Emperor', chapter: 'Cap. 432', total: 480, poster: M('way-to-be-emperor'), progress: 0.9, kind: 'manhua' },
    { id: 'm5', title: 'Revenge of the Baskerville Bloodhound', chapter: 'Cap. 80', total: 120, poster: M('revenge-baskerville'), progress: 0.66, kind: 'manhwa' },
    { id: 'm6', title: 'The Swordmaster\'s Son', chapter: 'Cap. 107', total: 140, poster: M('swordmaster-son'), progress: 0.76, kind: 'manhwa' },
    { id: 'm7', title: 'Rettougan no Tensei Majutsushi', chapter: 'Cap. 91', total: 95, poster: M('rettougan'), progress: 0.95, kind: 'manga' },
    { id: 'm8', title: 'Battle in 5 Seconds After Meeting', chapter: 'Cap. 65', total: 230, poster: M('battle-in-5-seconds'), progress: 0.28, kind: 'manga' },
    { id: 'm9', title: 'Suyo Webtoon-ui Na Kang-Rim', chapter: 'Cap. 122', total: 160, poster: M('suyo-webtoon'), progress: 0.76, kind: 'manhwa' },
  ];

  const enhancements = {
    families: [
      { id: 'anime4k', name: 'Anime4K', modes: ['A', 'B', 'C', 'A+A', 'B+B', 'C+A'], tiers: ['Rápido', 'Equilibrado', 'Calidad'] },
      { id: 'fsr', name: 'FSR', modes: ['FSR 1'], tiers: ['Nitidez baja', 'Media', 'Alta'] },
      { id: 'fsrcnnx', name: 'FSRCNNX', modes: ['8-0-4-1', '16-0-4-1'], tiers: [] },
      { id: 'cas', name: 'CAS', modes: ['Contraste adaptativo'], tiers: [] },
      { id: 'nvscaler', name: 'NVScaler', modes: ['NIS'], tiers: [] },
    ],
    framegen: { name: 'Generación de cuadros', options: ['Desactivada', '×2', '×3', '×4'], engine: 'lsfg-vk' },
  };

  const settingsCategories = [
    { id: 'apariencia', name: 'Apariencia', desc: 'Tema, paleta, diseño de Inicio, tarjetas, animaciones', count: 18 },
    { id: 'reproduccion', name: 'Reproducción', desc: 'Motor, controles, saltos, velocidad, mpv externo', count: 31 },
    { id: 'mejoras', name: 'Mejoras de video', desc: 'Anime4K, FSR, generación de cuadros, decodificación', count: 14 },
    { id: 'subtitulos', name: 'Subtítulos y audio', desc: 'Idiomas, estilo, segunda pista, traducción', count: 26 },
    { id: 'fuentes', name: 'Fuentes de episodios', desc: 'Selección manual/automática, calidad mínima, preferencias', count: 9 },
    { id: 'cuentas', name: 'Listas y cuentas', desc: 'AniList, MyAnimeList, sincronización', count: 8 },
    { id: 'metadatos', name: 'Metadatos y descubrir', desc: 'Orden de fuentes, contenido adulto, filas de Inicio', count: 12 },
    { id: 'link', name: 'Nijiyome Link', desc: 'Emparejar, perfiles de mejora, servidor de extensiones', count: 21 },
    { id: 'transferencias', name: 'Torrents y descargas', desc: 'Carpetas, límites, debrid, programación', count: 34 },
    { id: 'lectores', name: 'Lectores y narrador', desc: 'Manga, novelas, voces TTS', count: 29 },
    { id: 'notificaciones', name: 'Notificaciones', desc: 'Estrenos, recordatorios, actividad', count: 7 },
    { id: 'sistema', name: 'Sistema', desc: 'Bandeja, inicio automático, Discord, barra de título, red y proxy', count: 15, desktop: true },
    { id: 'datos', name: 'Datos y almacenamiento', desc: 'Caché, respaldo, registros, restablecer', count: 11 },
    { id: 'acerca', name: 'Acerca de', desc: 'Versión, actualizaciones, licencias', count: 5 },
  ];

  const statuses = [
    { id: 'watching', name: 'Viendo' }, { id: 'planning', name: 'Pendiente' }, { id: 'completed', name: 'Completado' },
    { id: 'paused', name: 'En pausa' }, { id: 'dropped', name: 'Abandonado' }, { id: 'rewatching', name: 'Reviendo' },
  ];

  const link = { pc: 'octavio-pc', phone: 'moto g51 5G', tablet: 'SM-X810', state: 'connected', profile: 'Anime4K A+A · ×2 cuadros', latency: '14 ms', ram: '1,2 / 2 GB' };

  const transfers = [
    { id: 't1', name: 'Grand Blue Dreaming S02 (E01-E06) 1080p', kind: 'torrent', state: 'downloading', pct: 0.37, down: '8,9 MB/s', up: '210 kB/s', eta: '6 min', peers: 7 },
    { id: 't2', name: 'Ao no Hako S2 · Ep 8', kind: 'hls', state: 'done', pct: 1, size: '412 MB' },
    { id: 't3', name: 'Koori no Jouheki · Ep 7', kind: 'debrid', state: 'queued', pct: 0, size: '1,1 GB' },
  ];

  window.NJ_DATA = { titles, byId: Object.fromEntries(titles.map((t) => [t.id, t])), laidoffEpisodes, sources, airingToday, manga, enhancements, settingsCategories, statuses, link, transfers, heroes: { sbr: 'img/hero-sbr.webp', onepiece: 'img/hero-onepiece.webp', laidoff: 'img/frame-laidoff.webp' } };
})();
